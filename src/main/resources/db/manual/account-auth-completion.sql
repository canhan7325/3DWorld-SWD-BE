-- Apply once to the v2 database before starting this account/auth implementation.
-- Adds attributes only; creates no table, no Flyway migration and preserves all data.
BEGIN;
ALTER TABLE public.users
    ADD COLUMN IF NOT EXISTS auth_version BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS email_verification_version BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS password_reset_version BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS email_delivery_state JSONB NOT NULL DEFAULT '{}'::jsonb,
    ADD COLUMN IF NOT EXISTS seller_verification_state JSONB NOT NULL DEFAULT '{}'::jsonb;
-- Existing access/email tokens must be replaced after deployment. Login/request a new link.
-- auth_version is independent of profile.updated_at; ordinary profile edits do not log users out.

-- Preserve previously verified storefront owners when adopting the new structured user state.
-- Only import existing email+phone+active VERIFIED bank facts; no new Seller role or subscription is granted.
UPDATE public.users u
SET seller_verification_state=jsonb_build_object(
    'status','VERIFIED','phoneSnapshot',u.phone,'bankAccountId',b.bank_account_id,
    'evidenceReference','IMPORTED_EXISTING_STOREFRONT',
    'submittedAt',floor(extract(epoch FROM now())*1000)::bigint,
    'reviewedAt',floor(extract(epoch FROM now())*1000)::bigint,
    'reviewerId',NULL,'reason','Imported existing verified storefront and contact/bank facts')
FROM public.user_bank_accounts b
WHERE b.user_id=u.user_id AND b.is_active AND b.is_default AND b.verification_status='VERIFIED'
  AND u.email_verified AND u.phone_verified AND u.phone IS NOT NULL
  AND u.seller_verification_state='{}'::jsonb
  AND EXISTS (SELECT 1 FROM public.storefronts s
              WHERE s.owner_user_id=u.user_id AND s.seller_verification_status='VERIFIED');

COMMIT;
