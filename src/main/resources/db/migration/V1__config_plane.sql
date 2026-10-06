CREATE SEQUENCE merchant_number_seq START WITH 1;
CREATE SEQUENCE terminal_number_seq START WITH 1;

CREATE TABLE merchants (
    id UUID PRIMARY KEY,
    mer_no VARCHAR(32) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    status VARCHAR(16) NOT NULL CHECK (status IN ('ACTIVE', 'INACTIVE')),
    webhook_url VARCHAR(2048),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE merchant_credentials (
    id UUID PRIMARY KEY,
    merchant_id UUID NOT NULL REFERENCES merchants(id),
    credential_version INTEGER NOT NULL CHECK (credential_version >= 1),
    secret_ciphertext TEXT NOT NULL,
    status VARCHAR(16) NOT NULL CHECK (status IN ('ACTIVE', 'REVOKED')),
    created_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ
);
CREATE UNIQUE INDEX one_active_merchant_credential ON merchant_credentials(merchant_id) WHERE status = 'ACTIVE';

CREATE TABLE acquirers (
    id UUID PRIMARY KEY,
    code VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    type VARCHAR(32) NOT NULL CHECK (type IN ('CARD_ACQUIRER', 'QR_PROVIDER')),
    status VARCHAR(16) NOT NULL CHECK (status IN ('ACTIVE', 'INACTIVE')),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE acquirer_merchant_configs (
    id UUID PRIMARY KEY,
    acquirer_id UUID NOT NULL REFERENCES acquirers(id),
    merchant_id UUID NOT NULL REFERENCES merchants(id),
    acquirer_mid VARCHAR(255),
    acquirer_tid VARCHAR(255),
    status VARCHAR(16) NOT NULL CHECK (status IN ('ACTIVE', 'INACTIVE')),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    UNIQUE (acquirer_id, merchant_id),
    UNIQUE (acquirer_id, acquirer_mid),
    UNIQUE (acquirer_id, acquirer_tid)
);

CREATE TABLE routing_profiles (
    id UUID PRIMARY KEY,
    code VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    status VARCHAR(16) NOT NULL CHECK (status IN ('ACTIVE', 'INACTIVE')),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE routing_rules (
    id UUID PRIMARY KEY,
    routing_profile_id UUID NOT NULL REFERENCES routing_profiles(id),
    payment_method VARCHAR(32) NOT NULL CHECK (payment_method IN ('CARD', 'PAYNOW', 'GOOGLE_PAY', 'APPLE_PAY')),
    acquirer_id UUID NOT NULL REFERENCES acquirers(id),
    priority INTEGER NOT NULL CHECK (priority >= 1),
    enabled BOOLEAN NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    UNIQUE (routing_profile_id, payment_method, priority)
);

CREATE TABLE terminals (
    id UUID PRIMARY KEY,
    terminal_id VARCHAR(32) NOT NULL UNIQUE,
    merchant_id UUID NOT NULL REFERENCES merchants(id),
    name VARCHAR(255) NOT NULL,
    channel VARCHAR(32) NOT NULL CHECK (channel IN ('WEB', 'MOBILE_APP', 'API', 'POS')),
    currency VARCHAR(3) NOT NULL CHECK (currency ~ '^[A-Z]{3}$'),
    three_ds_policy VARCHAR(16) CHECK (three_ds_policy IN ('REQUIRED', 'OPTIONAL', 'DISABLED')),
    routing_profile_id UUID REFERENCES routing_profiles(id),
    status VARCHAR(16) NOT NULL CHECK (status IN ('ACTIVE', 'INACTIVE')),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE terminal_payment_methods (
    terminal_id UUID NOT NULL REFERENCES terminals(id) ON DELETE CASCADE,
    payment_method VARCHAR(32) NOT NULL CHECK (payment_method IN ('CARD', 'PAYNOW', 'GOOGLE_PAY', 'APPLE_PAY')),
    enabled BOOLEAN NOT NULL,
    PRIMARY KEY (terminal_id, payment_method)
);
