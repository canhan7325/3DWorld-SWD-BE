# BE2 POD Business Rules V2

The approved policy is one fixed 24-hour Buyer response window per Print Request, at most three confirmed post-quote revision submissions, and 100% upfront payment. It supersedes older partial-deposit and remaining-balance assumptions. This file records the policy; source evidence and remaining gaps are distinguished below.

## Quote and revision

- First issued quote: `deadline = firstQuote.issued_at + 24 hours`. `PrintRequestQuoteService` persists `issued_at`/`expires_at`; later quotes inherit the first deadline under the request row lock. No quote means no deadline. At the boundary, `now.isBefore(deadline)` is required for acceptance/revision.
- A post-quote revision begins with a version-bound request and audit/Technical Review event. The prior unaccepted quote becomes `SUPERSEDED`. File and Spec can both change in that cycle. Only `POD_POST_QUOTE_REVISION:<requestId>:<n>` on successful resubmission consumes one of three slots; `REVISION_START` does not. The event is durable in append-only status history and identified by a unique source-event ID. Draft edits before the first quote do not count.
- Replacement quote needs a new current File/Spec review and inherits the original deadline. Historical quotes and File/Spec versions remain. `PrintRequestReviewPolicy` rejects approval of a changed pair.
- Customer can accept only the current `ISSUED` quote for the current reviewed pair before the original deadline. Acceptance freezes ordinary edits and cancellation while payment is in progress. Passing the quote deadline after acceptance does not cancel that accepted request.
- When the unaccepted deadline passes, request-time Quote actions or `PrintQuoteExpirationWorker` call `expireIfOverdue`. The worker scans overdue candidates every 60 seconds by default (`app.print.quote-expiration-scan-ms`) and processes each request in a separate locked transaction. Repeated scans do not add another cancellation/history event. Accepting or revising at the deadline is rejected, even if the worker has not run.
- An accepted Quote remains in `QUOTE_ACCEPTED` after the Quote deadline while its separate payment session continues. The worker excludes it and the locked service rechecks for an accepted Quote before cancellation.

## Payment and production

- New Quote amounts are `deposit_amount = total_amount` and `remaining_amount = 0.00`. No BE2 remaining-payment transaction is created. Quote amount is snapshotted in Quote/Quote Item records.
- BE1 owns provider creation, verification, failed/expired attempts and financial reconciliation. BE1 `PaymentSourceResolver` currently still allows POD `DEPOSIT` and `REMAINING` stages as well as `FULL`; BE1 must restrict new POD payments to the accepted current quote and 100% `FULL`, disallow zero remaining charges, and supply an authenticated verified-outcome contract. BE2 did not modify it.
- BE1 payment attempts have their own persisted `expires_at`; the quote deadline must not substitute for it. `PENDING`/`UNKNOWN` must not cancel the request. Verified failure/expiry should cancel only after BE1 proves no verified success. Late success after cancellation requires reconciliation, not automatic reactivation.
- `PrintRequestProductionService` contains a request-locked queue operation with amount/snapshot checks and an idempotent `IN_QUEUE` result. It currently accepts supplied transaction facts and has no BE1 verified-outcome caller; therefore live production activation is **BLOCKED** until the BE1 contract is implemented and tested. No controller exposes this method as a payment callback.

## Policy deltas and evidence

The Task Plan's older acceptance language mentions a deposit/remaining calculation and later remaining-balance stage. The new policy changes that to full upfront payment and no second charge. The original acceptance cells are preserved in the workbook; the policy delta is recorded separately. The local `3DWorld_db_V2.sql` has 36 `CREATE TABLE` statements and cannot verify the deployed 48-table contract. Java mapping/unit/MVC checks do not establish PostgreSQL JSONB, FK, trigger or row-lock behavior.
