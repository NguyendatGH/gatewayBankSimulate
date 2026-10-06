ALTER TABLE routing_rules DROP CONSTRAINT IF EXISTS routing_rules_payment_method_check;
ALTER TABLE routing_rules
    ADD CONSTRAINT routing_rules_payment_method_check
    CHECK (payment_method IN ('CARD', 'PAYNOW', 'GOOGLE_PAY', 'APPLE_PAY', 'QR'));

ALTER TABLE terminal_payment_methods DROP CONSTRAINT IF EXISTS terminal_payment_methods_payment_method_check;
ALTER TABLE terminal_payment_methods
    ADD CONSTRAINT terminal_payment_methods_payment_method_check
    CHECK (payment_method IN ('CARD', 'PAYNOW', 'GOOGLE_PAY', 'APPLE_PAY', 'QR'));
