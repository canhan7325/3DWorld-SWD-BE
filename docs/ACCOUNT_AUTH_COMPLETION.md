# Account / Authentication — implementation completion

Snapshot: 24/10/2026. Scope: all BE-FR-ACC tasks and the account-related BE-FR-OPS-05-01 from `3DWorld_SWD_BE_Task_Plan (3).xlsx`.

## Apply before restarting

Run the entire `src/main/resources/db/manual/account-auth-completion.sql` in Supabase SQL Editor **before** starting the updated backend. It adds five attributes to `users` only; there are no new tables or Flyway migrations. It also imports existing verified storefront/contact/bank facts into seller verification state without granting roles/subscriptions or changing financial data.

Flyway is explicitly disabled in application.properties; the local .env is also set to FLYWAY_ENABLED=false. Hibernate stays `validate`. An older FLYWAY_ENABLED=true value no longer enables migrations. There is no need to create or delete flyway_schema_history for startup. Existing tokens from the old implementation require a new login/email link after deployment. This SQL was applied only to a disposable local test database during verification, not Supabase.

| Attribute | Purpose |
| --- | --- |
| auth_version BIGINT | Signed account epoch for access sessions. Logout, reset, status/role changes revoke sessions atomically. Profile edits do not revoke sessions. |
| email_verification_version BIGINT | Versioned one-time verification links, independent of session/reset versions. |
| password_reset_version BIGINT | Versioned one-time reset links. User row lock prevents concurrent replay. |
| email_delivery_state JSONB | Bounded latest record for each email purpose: encrypted pending token, recipient/reference, attempt count/status/retry time/result. |
| seller_verification_state JSONB | Submission/decision, phone/bank snapshot, evidence reference, reviewer/reason/time. |

Email delivery holds at most two current purpose records per user. The pending token is AES-GCM encrypted with a purpose-separated key derived from the server JWT secret and authenticated against its reference. The encrypted copy is cleared after success/use/cancellation/exhaustion. SMTP is at-least-once if a process crashes after sending but before commit; the link itself remains one-time. There is no claim of exactly-once SMTP delivery.

## Task completion matrix

Done below means implemented and verified locally for the existing schema/business scope. It does not mean real SMTP/Cloudinary/SMS/bank providers were contacted during tests.

| Task | Status | APIs / implementation |
| --- | --- | --- |
| BE-FR-ACC-01-01 | Done | POST /api/auth/register; POST /api/auth/verify-email. Normalized email, BCrypt/policy, unique email, fixed CUSTOMER role; purpose/recipient/expiry/one-time verify. |
| BE-FR-ACC-01-02 | Done | POST /api/auth/resend-verification; POST /api/auth/forgot-password. Durable latest email delivery result/retry in users JSONB; cooldown, worker, no token/outbox table. |
| BE-FR-ACC-02-01 | Done | POST /api/auth/login; POST /api/auth/logout; GET /api/auth/me. ACTIVE account, ACCESS-only JWT, signed versioned session, logout-all policy, current role/capability output. |
| BE-FR-ACC-02-02 | Done | POST /api/auth/forgot-password; POST /api/auth/reset-password. Generic response for missing/locked accounts, one-time reset under lock, expiry/tamper/purpose validation and session revocation. |
| BE-FR-ACC-03-01 | Done | GET/PATCH /api/users/me; POST /api/users/me/avatar. Own profile/DOB/phone, contact verification invalidation, Cloudinary Avatar upload validation/storage failure behavior; no role/email mutation from profile. |
| BE-FR-ACC-03-02 | Done | GET/POST /api/users/me/addresses; PATCH/DELETE /api/users/me/addresses/{id}; POST /api/users/me/addresses/{id}/default. Ownership, default serialization/flush, region fields; order snapshots stay independent. Referenced addresses may be rejected by FK instead of destroying history. |
| BE-FR-ACC-03-03 | Done | GET/POST /api/users/me/bank-accounts; PATCH/DELETE /api/users/me/bank-accounts/{id}; POST /api/users/me/bank-accounts/{id}/default. Masked own CRUD, validation, sandbox reference, invalidation after bank/phone changes and audit. |
| BE-FR-ACC-03-04 | Done (manual sandbox) | GET /api/admin/bank-accounts; GET/PATCH /api/admin/bank-accounts/{id}/verification. Evidence/reference/reason, current account fingerprint, no self review, masked evidence read/audit; inactive/stale records cannot be approved. |
| BE-FR-ACC-04-01 | Done (manual sandbox) | GET/POST /api/users/me/seller-verification; GET /api/admin/seller-verifications; GET/PATCH /api/admin/users/{id}/seller-verification. Contact/bank prerequisites, reject/resubmit, stale/self-review guards; verification does not grant unpaid Seller capability. |
| BE-FR-ACC-05-01 | Done for implemented account/resource APIs | Method security + signed session epoch + database role checks on sensitive actions + ownership in service. GET /api/auth/me and /api/users/me expose current capabilities. No membership/assignment functionality is fabricated for future Design/Sharing modules. |
| BE-FR-OPS-05-01 | Done for implemented account/role/sanction scope | GET/POST /api/admin/users; PUT /api/admin/users/{id}/roles/{role}; PATCH /api/admin/users/{id}/status; PATCH /api/admin/users/{id}/phone-verification. Internal Staff/Designer creation, role grants/revocation, sanctions and last-active-Admin guard. No storefront auto-created for Designer, no automatic refunds/deletion. |

The supporting mappings `users`, `roles`, `user_roles`, `user_addresses`, `user_bank_accounts` are updated; `user_bank_accounts.updated_at` is now mapped as well. Sensitive review/decision writes use existing append-only audit_logs.

## Important contracts

- Passwords: at least 8 characters, uppercase/lowercase/digit/special character, maximum 72 UTF-8 bytes. `Admin@123` remains valid.
- Access/email purposes are distinct. Email-purpose tokens cannot authenticate API calls. JWT email tokens are no longer rejected by the obsolete 43-character DTO pattern.
- Session policy is logout-all-devices. Signed jti contains the account epoch plus random session bits. It is compared to users.auth_version, avoiding timestamp precision bugs. Roles changed by the supported Admin service revoke old tokens immediately. Manual SQL role changes must also increment auth_version.
- Internal role grants are not usable before email verification. Public registration accepts no role fields. Only Admin may grant/revoke elevated roles; CUSTOMER base role is retained. SELLER activation requires verified eligibility and a paid/active storefront.
- Admin role mutation/sanction operations serialize with a PostgreSQL transaction advisory lock. The last usable verified ACTIVE Admin cannot be removed/locked/banned, including concurrent self-demotion.
- Bank review request now requires `approved`, `reason`, `evidenceReference`, and `accountFingerprint`. Obtain the current fingerprint from own bank response or Admin verification queue/detail. Number fields remain server-managed; clients cannot post `verificationStatus=VERIFIED`.
- Phone review is explicitly **manual sandbox**, not SMS verification. Admin phone review requires the current phone value plus evidence/reason; self review is rejected. Bank review is likewise manual evidence-based sandbox, not real account-owner verification through a bank provider.
- Changing phone/bank invalidates matching verification and seller publication eligibility. Storefront checks also require an ACTIVE owner with verified contact and ACTIVE SELLER role, so account bans/role revocations stop new catalog purchases.
- Seller review requires the exact `submittedAt` value returned by the pending request. Important edits invalidate submission; resubmit gets a new monotonic submission timestamp. Approval does not create/activate a subscription or grant free Seller role.
- Blocked sellers cannot log in or receive new purchases. Authorized Admin/Print Staff may process existing paid physical orders through the existing status API. Financial/order/case history remains intact and is not deleted/refunded by sanctions.
- Preserve `user_bank_accounts.account_number_token` as a sandbox-only reference. Real provider tokenization is a separate integration task. Seed fixtures may intentionally display full fake numbers; newly created/updated accounts are masked by these APIs.
- Email worker retries per user/purpose under user-row lock. A failed send does not mark an account verified. Queue records survive process restart. No raw token/provider exception payload is logged or returned by account APIs.

## Reviewer examples

Phone manual review:

```json
{
  "phone": "0901234567",
  "approved": true,
  "evidenceReference": "MANUAL-SANDBOX-PHONE-001",
  "reason": "Manual sandbox evidence checked"
}
```

Bank review (replace fingerprint with the response value):

```json
{
  "approved": true,
  "reason": "Manual sandbox bank evidence checked",
  "evidenceReference": "MANUAL-SANDBOX-BANK-001",
  "accountFingerprint": "<64-character fingerprint from current bank response>"
}
```

Seller submission:

```json
{"bankAccountId": 1, "evidenceReference": "SELLER-DECLARATION-001"}
```

Seller reviewer:

```json
{"submittedAt": 1234567890000, "approved": true, "reason": "Current contact and bank evidence checked"}
```

## Verification and boundaries

New opt-in AccountAuthPostgresTest uses an explicitly named local disposable database, mocked EmailService and mocked CloudinaryStorage. It exercises real HTTP validation/filter/method security, persistence/locks, register/verify/reset, retry, purpose separation, one-time/concurrent reset, profile session continuity, cross-user denial, defaults concurrency, bank stale evidence, seller reject/resubmit/approve, last-Admin concurrency, role/session revocation, ban/unban and authorized Staff handling of an existing order. AccountTokenTests cover expiry, purpose/version and ciphertext tamper/reference isolation. Existing avatar image validator tests are retained.

Real SMTP delivery, Cloudinary credentials/network, real SMS/bank verification and production UAT are not asserted by mocked tests. Subscription billing, Google Login, arbitrary project membership/Designer assignments, Wallet/refund and other future tasks are outside this account implementation; do not mark those tasks Done.

The known BE2 PrintRequestMappingTest still asserts the old 10-field entity and is excluded from the general regression command; the default W3dWorldApplicationTests is excluded to avoid using the developer's actual datasource. No BE2 service/controller was changed.

Final verification: 22 targeted tests passed (14 account PostgreSQL scenarios, 4 token tests, 3 avatar validator tests, 1 full application startup/Swagger smoke test). The full application started with schema validation and no flyway_schema_history table; /v3/api-docs returned HTTP 200 and included the new account routes. This confirms local startup, not connectivity or schema state of the live Supabase database.
