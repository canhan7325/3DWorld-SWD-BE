# BE1 to BE2 POD Payment Contract

This is the required integration contract, not an active callback. BE1 owns provider verification, transaction and attempt status; BE2 owns the accepted Print Quote and Print Request production transition. No BE1 source or shared database was changed for this document.

## Current boundary

`PaymentServiceImpl.callback` and `reconcilePending` verify ZaloPay responses, mark `PaymentAttempt` and `PaymentTransaction` successful, then call the Physical Order handler for non-POD transactions. Before the BE1 change, that handler ignored POD, and `PaymentSourceResolver` accepted `DEPOSIT`/`REMAINING` without checking the current Quote/pair. BE1 now admits only POD `FULL` for the current accepted Quote with matching File/Spec snapshots and `deposit_amount = total_amount`, `remaining_amount = 0`. Request/Quote aliases share one canonical Quote operation key. Any different existing POD Payment transaction for the same request and payer blocks another charge until reconciliation, regardless of its status; retrying the same Quote/key remains possible. A verified POD `SUCCESS` is financially persisted with `fulfillment_status=RECONCILIATION_REQUIRED` until BE2 has a safe verified-outcome consumer. `PrintRequestProductionService.activateAfterVerifiedPayment` remains disconnected; primitive values alone are not trusted payment evidence.

## Proposed authenticated handoff

BE1 should dispatch a server-internal verified outcome after locking and persisting the transaction/attempt, never from FE redirect data. The following fields are required; names describe the interface to agree, not a new database table or public endpoint.

| Field | Source / rule |
| --- | --- |
| `transactionId` | Persisted `transactions.transaction_id`; stable idempotency anchor. |
| `printRequestId`, `quoteId` | Persisted `transactions.print_request_id` and `print_quote_id`; both must match the current accepted Quote/request. |
| `expectedAmount`, `verifiedPaidAmount` | Quote `total_amount` and BE1 verified transaction amount; compare as currency amounts, both equal 100% total. |
| `outcome` | BE1 verified `SUCCESS`, `FAILED`, or confirmed session `EXPIRED`; `PENDING`/`UNKNOWN` are nonterminal, not failure. |
| `paymentStage`, `purpose` | `FULL` and `POD` only for new POD charges. `DEPOSIT`/`REMAINING` requests are rejected by BE1; no zero-value remaining transaction. |
| `attemptId`, `attemptExpiresAt` | Persisted `transaction_attempts.attempt_id` and `expires_at` when an attempt exists. This deadline is separate from Quote's 24-hour acceptance window. |
| `providerName`, `providerReference` | Existing attempt `provider_name` and `external_reference` or `provider_transaction_id` when present; do not require a value the provider has not supplied. |
| `idempotencyIdentifier`, `eventTime` | Transaction `operation_key` or authenticated BE1 event identifier, plus persisted verified/completed time. Retries use the same transaction/event identity. |

Proposed internal method: `PodPaymentOutcomeHandler.handleVerified(PodPaymentOutcome outcome)`, called only by BE1 after its locked transaction/attempt records have a verified terminal outcome. The outcome should carry the fields above plus `payerUserId`. BE2 must read the authoritative transaction/attempt again under the agreed lock and verify current accepted Quote, full amount, source reference and cancellation state. This is a proposed method contract, not a registered bean or public endpoint.

BE1 and BE2 must agree on durable delivery before wiring the method. A queue failure must not roll back a provider-confirmed financial `SUCCESS`. Without an approved outbox/retry mechanism (or equivalent durable reconciliation worker) a synchronous callback into BE2 is unsafe: the BE2 transaction may throw after BE1 has verified payment. On an undeliverable outcome, keep `fulfillment_status=RECONCILIATION_REQUIRED`; replay by the same transaction/event ID after the integration exists. BE2 must return an explicit applied/reconciliation result, not throw for late success on a cancelled request. No outbox table or migration was created in this task.

## Outcome rules

- Verified `SUCCESS`: request lock, current accepted Quote and exact File/Spec snapshot, full paid amount, correct payer/request/quote references; transition once to `IN_QUEUE`, set `locked_at`, queue items, append one status-history event keyed by transaction ID. A retry returns the existing queue result without a second event or charge.
- Verified `FAILED`: cancel only when BE1 establishes a terminal failure and no verified success/unresolved attempt exists. Preserve transaction/attempt history; do not queue.
- Verified `EXPIRED`: use the real persisted attempt/provider expiry and reconciliation result, not the Quote deadline. Cancel only when no verified success or unknown outcome remains.
- `PENDING`/`UNKNOWN`: leave request/payment reconcilable; do not cancel or queue.
- Late verified success after cancellation: leave request cancelled, mark financial reconciliation required in BE1 and route to approved refund/reconciliation workflow. Never reactivate queue automatically.

## Queue persistence and safety

BE2 source can persist `print_requests.status=IN_QUEUE`, `locked_at`, item `production_status=QUEUED` and an append-only status-history source event such as `POD_PAYMENT_VERIFIED:<requestId>:tx:<transactionId>` in one transaction. This is a potential durable queue/lock fact. The existing local executable SQL has 36 tables and cannot prove the deployed 48-table UNIQUE/history/FK constraints. BE1 has not wired a verified POD outcome or fulfillment result to BE2, so payment-to-queue activation is **BLOCKED**, not production-ready.

BE1 Payment API for new POD obligations: `POST /api/payments/zalopay` with `sourceType=PRINT_REQUEST` and that request ID, or `sourceType=PRINT_QUOTE` and the current Quote ID; `stage=FULL`; `amount` omitted because BE1 reads the accepted Quote total. Both aliases resolve to the same persisted Quote/operation key. `POST /api/payments/zalopay/callback` remains provider-facing MAC-verified; it does not accept frontend payment-success claims. A provider create result that is not verified as accepted is returned as `UNKNOWN` and left for provider query/reconciliation rather than marked `FAILED` by default.

The BE1 handoff must also expose whether an accepted Quote's payment is pending, unknown, failed or verified, plus attempt expiry, before BE2 can fill `workflow.paymentExpiresAt` and accurately disable repeated `pay` actions. Quote acceptance at T-1 minute remains valid after the 24-hour quote deadline while its separate payment session continues.

## Verification and unresolved provider policy

BE1 Payment unit tests cover current-Quote validation SQL, `FULL`-only POD stage, canonical alias idempotency, legacy-obligation blocking, MAC/amount-gated callback behavior, provider-query success/unknown/failure, and unchanged Physical/Rental/Wallet creation behavior. The scoped Payment/Model/Printing run passed 328 tests (0 failures/errors, 8 skipped). These are mock/unit/MVC checks; no provider call, live payment, or disposable 48-table PostgreSQL test was performed.

`PaymentServiceImpl` currently initializes a ZaloPay attempt with `expires_at = now + 900 seconds`. That existing value is not evidence of the provider's authoritative session expiry and must not be used to cancel a POD request until BE1 confirms the provider contract and verifies the terminal outcome. The existing query branch treats `return_code=-1` as definitive failure; provider code semantics were not independently verified during this task. Any unrecognized POD create response is therefore retained as `UNKNOWN` for provider query rather than treated as failed. Physical/Rental/Wallet response handling remains unchanged.
