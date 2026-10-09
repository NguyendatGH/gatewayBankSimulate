CREATE SEQUENCE merchant_number_seq START WITH 1;
CREATE SEQUENCE terminal_number_seq START WITH 1;
CREATE SEQUENCE trade_number_seq START WITH 1;

CREATE TABLE merchants (
    id UUID PRIMARY KEY,
    mer_no VARCHAR(32) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    status VARCHAR(16) NOT NULL CHECK (status IN ('ACTIVE', 'INACTIVE')),
    webhook_url VARCHAR(2048),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    external_reference VARCHAR(128),
    settlement_bank_bin VARCHAR(6),
    settlement_account_number VARCHAR(30),
    settlement_account_name VARCHAR(120),
    CONSTRAINT ck_merchants_settlement_bin
        CHECK (settlement_bank_bin IS NULL OR settlement_bank_bin ~ '^[0-9]{6}$'),
    CONSTRAINT ck_merchants_settlement_account
        CHECK (settlement_account_number IS NULL OR settlement_account_number ~ '^[0-9]{6,30}$'),
    CONSTRAINT ck_merchants_settlement_complete
        CHECK ((settlement_bank_bin IS NULL) = (settlement_account_number IS NULL))
);
CREATE UNIQUE INDEX uk_merchants_external_reference ON merchants(external_reference)
    WHERE external_reference IS NOT NULL;

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
    status VARCHAR(16) NOT NULL CHECK (status IN ('ACTIVE', 'INACTIVE')),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    three_ds_supported BOOLEAN NOT NULL DEFAULT FALSE,
    bank_bin VARCHAR(6),
    CONSTRAINT ck_acquirers_bank_bin CHECK (bank_bin IS NULL OR bank_bin ~ '^[0-9]{6}$')
);
CREATE UNIQUE INDEX uk_acquirers_bank_bin ON acquirers(bank_bin) WHERE bank_bin IS NOT NULL;

CREATE TABLE acquirer_payment_methods (
    acquirer_id UUID NOT NULL REFERENCES acquirers(id) ON DELETE CASCADE,
    payment_method VARCHAR(32) NOT NULL CHECK (payment_method IN ('CARD', 'PAYNOW', 'GOOGLE_PAY', 'APPLE_PAY', 'QR')),
    PRIMARY KEY (acquirer_id, payment_method)
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
    payment_method VARCHAR(32) NOT NULL CHECK (payment_method IN ('CARD', 'PAYNOW', 'GOOGLE_PAY', 'APPLE_PAY', 'QR')),
    acquirer_id UUID NOT NULL REFERENCES acquirers(id),
    priority INTEGER NOT NULL CHECK (priority >= 1),
    enabled BOOLEAN NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    UNIQUE (routing_profile_id, payment_method, priority),
    CONSTRAINT routing_rules_profile_method_acquirer_unique
        UNIQUE (routing_profile_id, payment_method, acquirer_id)
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
    updated_at TIMESTAMPTZ NOT NULL,
    acquirer_id UUID REFERENCES acquirers(id)
);

CREATE TABLE terminal_payment_methods (
    terminal_id UUID NOT NULL REFERENCES terminals(id) ON DELETE CASCADE,
    payment_method VARCHAR(32) NOT NULL CHECK (payment_method IN ('CARD', 'PAYNOW', 'GOOGLE_PAY', 'APPLE_PAY', 'QR')),
    enabled BOOLEAN NOT NULL,
    PRIMARY KEY (terminal_id, payment_method)
);

CREATE TABLE issuer_accounts (
    id UUID PRIMARY KEY,
    account_no VARCHAR(64) NOT NULL UNIQUE,
    currency VARCHAR(3) NOT NULL,
    balance BIGINT NOT NULL CHECK (balance >= 0),
    available_balance BIGINT NOT NULL CHECK (available_balance >= 0),
    hold_balance BIGINT NOT NULL CHECK (hold_balance >= 0),
    status VARCHAR(16) NOT NULL CHECK (status IN ('ACTIVE', 'BLOCKED', 'CLOSED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (available_balance + hold_balance <= balance)
);

CREATE TABLE issuer_cards (
    id UUID PRIMARY KEY,
    account_id UUID NOT NULL REFERENCES issuer_accounts(id),
    pan_token_or_test_pan VARCHAR(32) NOT NULL UNIQUE,
    expiry_month INTEGER NOT NULL CHECK (expiry_month BETWEEN 1 AND 12),
    expiry_year INTEGER NOT NULL,
    cvv_test_value VARCHAR(8),
    status VARCHAR(16) NOT NULL CHECK (status IN ('ACTIVE', 'BLOCKED', 'EXPIRED')),
    three_ds_mode VARCHAR(24) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE payment_transactions (
    id UUID PRIMARY KEY,
    provider_payment_id VARCHAR(96) NOT NULL UNIQUE,
    merchant_id UUID NOT NULL REFERENCES merchants(id),
    terminal_id UUID NOT NULL REFERENCES terminals(id),
    order_code VARCHAR(128) NOT NULL,
    amount BIGINT NOT NULL CHECK (amount > 0),
    currency VARCHAR(3) NOT NULL,
    payment_method VARCHAR(32) NOT NULL,
    three_ds_requested BOOLEAN NOT NULL DEFAULT FALSE,
    three_ds_result VARCHAR(32),
    status VARCHAR(32) NOT NULL,
    selected_acquirer_id UUID REFERENCES acquirers(id),
    return_url TEXT,
    cancel_url TEXT,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    amount_paid BIGINT NOT NULL DEFAULT 0,
    transaction_ref VARCHAR(160),
    paid_at TIMESTAMPTZ,
    request_payload JSONB,
    merchant_webhook_url TEXT,
    settlement_bank_bin VARCHAR(6),
    settlement_account_number VARCHAR(30),
    issuer_card_id UUID REFERENCES issuer_cards(id),
    UNIQUE (merchant_id, terminal_id, order_code)
);

CREATE TABLE payment_attempts (
    id UUID PRIMARY KEY,
    payment_id UUID NOT NULL REFERENCES payment_transactions(id) ON DELETE CASCADE,
    attempt_no INTEGER NOT NULL CHECK (attempt_no >= 1),
    acquirer_id UUID NOT NULL REFERENCES acquirers(id),
    state VARCHAR(32) NOT NULL,
    request_sent_at TIMESTAMPTZ,
    response_received_at TIMESTAMPTZ,
    result_code VARCHAR(96),
    result_message TEXT,
    downstream_reference VARCHAR(160),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (payment_id, attempt_no)
);

CREATE TABLE transaction_events (
    id UUID PRIMARY KEY,
    payment_id UUID NOT NULL REFERENCES payment_transactions(id) ON DELETE CASCADE,
    from_state VARCHAR(32),
    to_state VARCHAR(32) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    event_data JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX transaction_events_payment_created_idx ON transaction_events(payment_id, created_at);

CREATE TABLE webhook_deliveries (
    id UUID PRIMARY KEY,
    event_id VARCHAR(160) NOT NULL UNIQUE,
    payment_id UUID NOT NULL REFERENCES payment_transactions(id) ON DELETE CASCADE,
    target_url TEXT NOT NULL,
    status VARCHAR(16) NOT NULL CHECK (status IN ('PENDING', 'DELIVERED', 'RETRY_WAIT', 'DEAD')),
    attempt_count INTEGER NOT NULL DEFAULT 0,
    last_http_status INTEGER,
    last_error TEXT,
    next_retry_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    delivered_at TIMESTAMPTZ,
    payload JSONB NOT NULL
);
CREATE INDEX webhook_deliveries_due_idx ON webhook_deliveries(status, next_retry_at);

CREATE TABLE issuer_holds (
    id UUID PRIMARY KEY,
    payment_id UUID NOT NULL UNIQUE REFERENCES payment_transactions(id),
    account_id UUID NOT NULL REFERENCES issuer_accounts(id),
    amount BIGINT NOT NULL CHECK (amount > 0),
    status VARCHAR(16) NOT NULL CHECK (status IN ('ACTIVE', 'CAPTURED', 'RELEASED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    released_at TIMESTAMPTZ,
    captured_at TIMESTAMPTZ
);

CREATE TABLE refund_transactions (
    id UUID PRIMARY KEY,
    provider_refund_id VARCHAR(96) NOT NULL UNIQUE,
    payment_id UUID REFERENCES payment_transactions(id),
    merchant_id UUID NOT NULL REFERENCES merchants(id),
    merchant_reference VARCHAR(160) NOT NULL,
    amount BIGINT NOT NULL CHECK (amount > 0),
    status VARCHAR(16) NOT NULL CHECK (status IN ('REQUESTED', 'PROCESSING', 'SUCCEEDED', 'FAILED', 'UNKNOWN')),
    idempotency_key VARCHAR(160) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    to_bank_bin VARCHAR(6),
    to_account_number VARCHAR(34),
    description VARCHAR(255),
    bank_reference VARCHAR(96),
    failure_code VARCHAR(64),
    failure_reason VARCHAR(255),
    UNIQUE (merchant_id, idempotency_key)
);
CREATE INDEX refund_transactions_payment_idx ON refund_transactions(payment_id) WHERE payment_id IS NOT NULL;
CREATE INDEX refund_transactions_status_idx ON refund_transactions(status);

CREATE TABLE sandbox_ledger_entries (
    id UUID PRIMARY KEY,
    payment_id UUID NOT NULL REFERENCES payment_transactions(id),
    phase VARCHAR(16) NOT NULL CHECK (phase IN ('CAPTURE', 'SETTLEMENT')),
    account_key VARCHAR(160) NOT NULL,
    amount BIGINT NOT NULL CHECK (amount <> 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (payment_id, phase, account_key)
);

CREATE TABLE sandbox_merchant_accounts (
    merchant_id UUID NOT NULL REFERENCES merchants(id),
    bank_bin VARCHAR(6) NOT NULL,
    account_number VARCHAR(30) NOT NULL,
    balance BIGINT NOT NULL DEFAULT 0 CHECK (balance >= 0),
    PRIMARY KEY (merchant_id, bank_bin, account_number)
);

CREATE TABLE gateway_transactions (
    id UUID PRIMARY KEY,
    gw_txn_id VARCHAR(64) NOT NULL UNIQUE,
    merchant_id UUID NOT NULL REFERENCES merchants(id),
    terminal_id UUID NOT NULL REFERENCES terminals(id),
    order_code VARCHAR(128) NOT NULL,
    amount BIGINT NOT NULL CHECK (amount > 0),
    currency VARCHAR(3) NOT NULL,
    payment_method VARCHAR(32) NOT NULL,
    bank_code VARCHAR(64) NOT NULL,
    bank_ref VARCHAR(96),
    auth_code VARCHAR(64),
    result_code VARCHAR(4),
    status VARCHAR(20) NOT NULL CHECK (status IN ('CREATED', 'PENDING_AUTH', 'SUCCESS', 'FAILED', 'EXPIRED')),
    card_brand VARCHAR(24),
    card_masked VARCHAR(24),
    failure_reason VARCHAR(255),
    paid_at TIMESTAMPTZ,
    merchant_return_url VARCHAR(2048) NOT NULL,
    merchant_webhook_url VARCHAR(2048),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    three_ds_preference VARCHAR(24),
    result_message VARCHAR(255),
    UNIQUE (merchant_id, terminal_id, order_code)
);
CREATE UNIQUE INDEX gateway_transactions_bank_ref_key
    ON gateway_transactions(bank_code, bank_ref) WHERE bank_ref IS NOT NULL;
CREATE INDEX gateway_transactions_due_idx ON gateway_transactions(status, expires_at);

CREATE TABLE gateway_transaction_events (
    id UUID PRIMARY KEY,
    transaction_id UUID NOT NULL REFERENCES gateway_transactions(id) ON DELETE CASCADE,
    from_status VARCHAR(20),
    to_status VARCHAR(20) NOT NULL,
    source VARCHAR(16) NOT NULL CHECK (source IN ('MERCHANT', 'GATEWAY', 'BANK', 'JOB')),
    detail VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX gateway_transaction_events_txn_idx ON gateway_transaction_events(transaction_id, created_at);

INSERT INTO issuer_accounts (id, account_no, currency, balance, available_balance, hold_balance, status)
VALUES ('00000000-0000-4000-8000-000000000001', 'SANDBOX_BUYER', 'VND', 100000000, 100000000, 0, 'ACTIVE');

INSERT INTO issuer_cards (id, account_id, pan_token_or_test_pan, expiry_month, expiry_year,
                          cvv_test_value, status, three_ds_mode)
VALUES ('00000000-0000-4000-8000-000000000101', '00000000-0000-4000-8000-000000000001',
        '4000000000001000', 12, 2030, '123', 'ACTIVE', 'CHALLENGE');
