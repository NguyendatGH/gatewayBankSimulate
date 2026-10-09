-- Mỗi terminal có thể có tài khoản nhận tiền riêng (BTC mở nhiều kênh: mỗi kênh một ngân hàng + tài khoản ở ngân hàng đó).
-- NULL = dùng tài khoản settlement của merchant như trước.
ALTER TABLE terminals ADD COLUMN settlement_bank_bin VARCHAR(6);
ALTER TABLE terminals ADD COLUMN settlement_account_number VARCHAR(30);
ALTER TABLE terminals ADD COLUMN settlement_account_name VARCHAR(120);
ALTER TABLE terminals ADD CONSTRAINT ck_terminals_settlement_bin
    CHECK (settlement_bank_bin IS NULL OR settlement_bank_bin ~ '^[0-9]{6}$');
ALTER TABLE terminals ADD CONSTRAINT ck_terminals_settlement_pair
    CHECK ((settlement_bank_bin IS NULL) = (settlement_account_number IS NULL));
