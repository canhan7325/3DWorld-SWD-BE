# BE2 V2 Implementation Tracking

Baseline: `feature/printing` at `fc3ade5`. This turn continued on existing uncommitted Staff Review and Quote/Production changes. The separate updated workbook is authoritative for row-level tracking; original Task IDs and acceptance text were preserved. No code was staged or committed.

| Task ID | Prior status | Current status | Source and test evidence | Remaining acceptance / next step |
| --- | --- | --- | --- | --- |
| BE-FR-POD-01-01 | In-progress | In-progress | Draft, File/Spec V1, edit, preview, submit and derived Detail workflow in source; scoped tests pass | PostgreSQL v2 and end-to-end quote/payment gates. |
| BE-FR-POD-02-01 | In-progress | In-progress | Staff review and version-bound correction/resubmit in source | Live DB verification and Quote-version integration acceptance. |
| BE-FR-POD-03-01 | In-progress | In-progress | Quote issue with immutable line snapshots, fixed first deadline and 100% upfront totals in source | Isolated PostgreSQL mapping/FK and runtime HTTP/OpenAPI. |
| BE-FR-POD-03-02 | In-progress | In-progress | Current quote acceptance, deadline checks, supersession, submitted-revision cap and scheduled overdue scan in source | PostgreSQL multi-instance row-lock/race verification. |
| BE-FR-POD-04-01 | In-progress | In-progress | Accepted Quote and internal queue service present | BE1 verified success handoff, transaction reference/amount authenticity, idempotent live queue activation. |
| BE-FR-POD-06-01 | In-progress | In-progress | Customer lock/cancel guards and queue lock in source | BE1 payment failure/expiry handling and production runtime verification. |
| BE-V2-POD-FILE-01 | In-progress | In-progress | Immutable File versions and preview invalidation | Isolated PostgreSQL FK/rollback/concurrency. |
| BE-V2-POD-SPEC-01 | In-progress | In-progress | Immutable Spec versions and preview invalidation | Isolated PostgreSQL JSONB/FK/rollback. |
| BE-V2-POD-STATE-01 | In-progress | In-progress | Shared edit/cancel guards, Detail workflow, status history | Verify status vocabulary and history triggers in deployed v2 schema. |
| BE-V2-POD-REVIEW-01 | In-progress | In-progress | JSONB Technical Review decisions tied to exact File/Spec pair | Handler assignment and PostgreSQL review persistence. |
| BE-V2-POD-QUOTE-01 | In-progress | In-progress | Quote issue/accept/revise source paths and fixed deadline | Full runtime/DB acceptance and BE1 payment binding. |
| BE-V2-POD-TEST-01 | In-progress | In-progress | `mvn.cmd -o -DskipTests test-compile` passed; Model/Printing regression: 313 tests, 0 failures/errors, 8 skipped | Disposable 48-table PostgreSQL suite, generated OpenAPI and live HTTP. |

The new policy delta is recorded separately from each task's original acceptance cell: full upfront (`deposit_amount = total_amount`, `remaining_amount = 0`), no remaining-payment charge, fixed first-quote 24-hour window, and three **completed** post-quote revision submissions. The original acceptance text is not silently rewritten.

## Changes in this turn

Added `PrintRequestWorkflowResponse`, `PrintRequestWorkflowPolicy` and `PrintRequestWorkflowService`; extended `PrintRequestResponse` additively. `PrintRequestController.getById` now attaches the owner-scoped backend workflow. File, Spec and State services reuse the common edit/cancel guard. `PrintRequestQuoteService.countPostQuoteRevisions` now counts only unique completed resubmission events, and its immediate revision response/audit no longer consumes a slot before submission. Existing Printing MVC/service tests were extended. Earlier uncommitted Staff Review/Quote/Production files were preserved.

## Evidence levels

| Level | Result |
| --- | --- |
| Source implementation | Workflow read model and guards implemented; Quote and internal Production source paths present. |
| Java compilation | PASS (`test-compile`). |
| Scoped tests | PASS (313 run, 0 failures, 0 errors, 8 skipped). |
| PostgreSQL v2 | NOT VERIFIED: local executable SQL has 36 tables; metadata exports/approved executable 48-table fixture unavailable in this checkout. |
| Generated OpenAPI | NOT VERIFIED. Controller mappings only. |
| Live HTTP / provider | NOT VERIFIED. No shared Supabase or provider called. |

BE1 integration requirement: accept a current quote before initiating a `FULL` POD charge; disallow new `REMAINING`/partial `DEPOSIT` charges; expose authenticated verified success/failure/expiry and transaction-to-request/quote/amount evidence to BE2; retain `PENDING`/`UNKNOWN` for reconciliation; ensure late success after cancellation never reactivates production. No BE1 source was changed.
