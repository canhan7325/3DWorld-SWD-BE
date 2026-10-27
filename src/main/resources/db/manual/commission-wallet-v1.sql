-- Apply once in Supabase SQL Editor. No table is created.
ALTER TABLE public.wallets
  ADD COLUMN IF NOT EXISTS return_shipping_debt NUMERIC(18,2) NOT NULL DEFAULT 0;
ALTER TABLE public.wallets
  DROP CONSTRAINT IF EXISTS ck_wallet_nonnegative;
ALTER TABLE public.wallets
  ADD CONSTRAINT ck_wallet_nonnegative CHECK (
    pending_balance >= 0 AND available_balance >= 0 AND held_balance >= 0
    AND return_shipping_debt >= 0);
-- Admin values are stored in existing platform_configs. Existing fee snapshots remain unchanged.
INSERT INTO public.platform_configs(config_key,config_value)
VALUES ('COMMISSION_RATE','0.05'),('STOREFRONT_MONTHLY_FEE','99000')
ON CONFLICT (config_key) DO NOTHING;
