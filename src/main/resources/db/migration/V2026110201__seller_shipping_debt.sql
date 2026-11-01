-- Existing marketplace schema is required. Reuse wallets and audit_logs; no new business tables.
ALTER TABLE wallets ADD COLUMN IF NOT EXISTS return_shipping_debt NUMERIC(18,2) NOT NULL DEFAULT 0;
ALTER TABLE wallets ADD COLUMN IF NOT EXISTS shipping_debt_since TIMESTAMPTZ;
UPDATE wallets SET shipping_debt_since=coalesce(updated_at,now())
WHERE return_shipping_debt>0 AND shipping_debt_since IS NULL;
ALTER TABLE wallets ADD CONSTRAINT ck_shipping_debt_nonnegative CHECK (return_shipping_debt>=0);
CREATE INDEX IF NOT EXISTS ix_wallets_outstanding_shipping_debt ON wallets(shipping_debt_since) WHERE return_shipping_debt>0;
