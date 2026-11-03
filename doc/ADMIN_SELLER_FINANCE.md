# Admin seller finance API

Backend implementation for the revenue cards, commission table, storefront subscription table and shop wallet/debt table. All dashboard endpoints require `ADMIN` via `@PreAuthorize`; customer/seller requests receive 403. Existing APIs remain available.

## Deployment

Run a clean build (`mvn clean package`) so obsolete compiled migration resources cannot be bundled. Flyway is enabled by default; `FLYWAY_ENABLED=false` explicitly opts out. On the existing marketplace schema without Flyway history, baseline version 0 adopts existing tables and migration `V2026110201__seller_shipping_debt.sql` adds/reuses `wallets.return_shipping_debt` and adds `wallets.shipping_debt_since`. Existing debt values are preserved. The migration does not create business tables; Flyway creates its own schema history table. This is an upgrade of the existing schema, not a fresh database bootstrap.

Migration has been tested on an isolated local PostgreSQL schema, not applied to the application's real database in this task.

## Balance and debt rules

- 100,000 VND is a minimum retained **within available balance**, not an extra deposit credited on top of it. `held_balance` is reserved for payout requests.
- Actual outbound + return fees are assessed once per order using the existing staff return-shipping/refund approval flow. Fees debit available balance down to zero (including the retained 100,000 VND); the shortfall accumulates in `return_shipping_debt`.
- Outstanding debt is positive in DB. Dashboard also supplies `signed_shipping_debt` as a negative display amount.
- `shipping_debt_since` records the start of the current unpaid debt episode. More charges do not reset it; fully repaying debt clears it. For historical debt, migration uses the wallet update time as an approximate start.
- Verified top-ups, eligible seller revenue releases, and released rejected-payout funds settle debt first. Revenue remains pending until receipt/refund-window conditions are satisfied. Offset entries are in `wallet_ledgers`; assessment and settlement evidence is in `audit_logs`.
- Debt blocks withdrawals. Available balance after withdrawal must retain 100,000 VND. A suspended shop also cannot request/complete payout.
- Debt alone no longer blocks handover/shipment of existing orders, so continued sales can repay it. Admin suspension prevents new listings/trading through storefront eligibility and excludes the shop from public purchasable catalog/checkout. Existing order obligations and top-ups remain available.

## Endpoints

Base: `/api/admin/dashboard`.

| Method/path | Purpose |
|---|---|
| `GET /metrics?from=2026-11-01&to=2026-11-30` | Realized revenue, provisional commission, subscription income, current total debt |
| `GET /commissions?from=...&to=...&shopId=2&page=0&size=20` | Order/shop/value/rate/commission/seller entitlement/debt offset/credited amount/status |
| `GET /subscriptions?from=...&to=...&shopId=2&userId=9&page=0&size=20` | Paid subscription history, user/shop, cycle, amount, activation/expiry |
| `GET /shops/2` | Lifetime subscription income, realized commission, current debt, latest 100 subscription payments |
| `GET /wallets?debtOnly=true&lockedOnly=true&inactiveDays=30&page=0&size=20` | Shop wallets, retained reserve, debt age, last order date, withdrawal lock and suspension candidate |
| `GET /shops/2/shipping-debt` | Actual two-way fee assessments, initial debit/debt, FIFO repayment allocation and settlement ledger history |
| `POST /shops/2/suspend-for-debt` | Admin manually suspends a shop with overdue debt and no recent non-cancelled order |
| `POST /shops/2/restore-after-debt` | Admin restores a debt-suspended shop after debt clears; expired subscriptions remain inactive |

Date range is inclusive, interpreted in Asia/Ho_Chi_Minh. To filter by month, pass the first and last calendar dates. Metrics: realized commission uses `released_at`, rental income uses `paid_at`, provisional commission uses earning creation date. Commission rows use earning creation date. Debt metrics/wallets are current stock balances, independent of the date range. An order with an active or paid refund does not contribute realized/provisional revenue. Provisional commission includes funded pending/held earnings, not uncollected COD cash.

Rows use `PENDING_REFUND_WINDOW`, `RECOGNIZED`, `REFUNDED`. `refund_under_review` distinguishes an active request. `seller_net_amount` is entitlement before debt offsets; `credited_to_shop` is the earning's released amount minus its recorded debt offset.

The current subscription model stores one 30-day storefront package, without a historical package-name/yearly-cycle snapshot. API reports `STOREFRONT_SUBSCRIPTION` and `30_DAYS`; it does not invent monthly/yearly packages. Subscription history beyond the latest 100 is available through the paginated `/subscriptions` endpoint.

Suspension request:

```json
{"inactiveDays":30,"reason":"Shipping debt overdue and no recent sales"}
```

Restoration request:

```json
{"reason":"Shipping debt cleared"}
```

Both debt age and non-cancelled-order inactivity must exceed `inactiveDays` (1–3650). No automatic ban. Admin decisions are audited. Suspension blocks trading via `storefronts.status=SUSPENDED`, not the user's login/account. Repayment allocation in debt details is FIFO over recorded assessments; legacy charges lacking assessment evidence cannot be reconstructed, and wallet debt remains authoritative.

## Validation

Targeted tests cover repeated return fees exceeding the reserve, debt offset on release/top-up and duplicate callbacks, overdue/inactive suspension and restoration, seller handover, controller authorization, Flyway adoption/re-run and dashboard SQL on temporary PostgreSQL fixtures.

## Automatic recognition of commission

The independent wallet scheduler runs every `app.wallet.earning-release-scan-ms` (default 60000 ms). It processes each order through a separate transactional service call. Expired reservation cancellation cannot skip the wallet scan; a failed earning is logged with its order ID, does not roll back other orders, and is retried on the next scan.

A pending earning is released only when the order is `RECEIVED`, receipt time is present, platform receipt metadata and full successful payment/settled COD receipts exist, the refund deadline has passed, and no refund transaction is pending, processing or successful. A missing/stale earning eligibility timestamp is synchronized with the order deadline. If the order deadline is missing, receipt time plus the order's snapshotted refund window is used (72 hours for legacy rows without a snapshot).

The release atomically moves pending seller funds, offsets shipping debt, writes ledgers, changes the earning to `RELEASED` and records `released_at`. The UI label remains `RECOGNIZED`. Repeated scans cannot release a completed earning twice. Dashboard realized commission is filtered by the actual `released_at`, so overdue earnings recovered today appear in today's recognition period, not backdated to the order date. No schema migration is needed for this scheduler correction.

## Seller wallet history and earning troubleshooting

Seller-only (controller and service role checks, identity from JWT):

- `GET /api/users/me/payouts?page=0&size=20` (alias `/api/users/me/wallet/payouts`): newest first, stable ordering by creation time and transaction ID. Returns `transactionId`, `amount`, `status`, `bankAccountId`, `bankName`, `accountNumberMasked`, `accountHolder`, `transferReference`, `createdAt`, `reason`. Deleted/missing bank records do not hide the payout. Bank labels reflect the current linked bank record, not a historical snapshot. The latest stored reason is returned; old payouts that never persisted a reason remain null. New payout requests now persist their reason in the existing transaction field.
- `GET /api/users/me/wallet/ledgers?page=0&size=20`: returns `ledgerId`, `entryType`, `balanceBucket`, `signedAmount`, `balanceAfter`, `transactionId`, `earningId`, `orderId`, `createdAt`. A missing wallet returns an empty list without creating a wallet. Size 1–100. Continue pages to fetch complete history. No full bank account token/number is exposed.

Revenue release entry type in the existing DB is **`SELLER_REVENUE_RELEASE`**, not `SELLER_REVENUE_RELEASED`. It has two rows: negative PENDING and positive AVAILABLE. Render both with their respective buckets, rather than treating both rows as separate external income/expense. `PAYOUT_HOLD` increases HELD; `PAYOUT_COMPLETED` decreases HELD; return shipping decreases AVAILABLE. Shipping assessment entries without an earning link can have null `orderId` in the ledger response; order-linked fee evidence remains in the Admin debt details API.

Admin-only diagnosis and retry:

- `GET /api/admin/finance/orders/{orderId}/earning`: current earning/order state, platform receipts, effective refund deadline, released timestamp, `eligibleForRelease`, `recognitionStatus` and `blockers`.
- `POST /api/admin/finance/orders/{orderId}/earning/reconcile`: retries the existing transactional release logic, then reads the committed state. If financially blocked, returns NOT_RECOGNIZED and blockers; no forced recognition or fabricated receipt. Transaction failures propagate as errors; they are not reported as success.

Typical blockers: `ORDER_NOT_RECEIVED`, `PLATFORM_RECEIPT_NOT_RECONCILED` (including unremitted COD), `FUNDS_RECEIPT_NOT_POSTED`, `REFUND_WINDOW_NOT_EXPIRED`, `REFUND_IN_PROGRESS_OR_PAID`, `INSUFFICIENT_PENDING_BALANCE`, `EARNING_NOT_CREATED`. Missing or stale data must be reconciled using the corresponding payment/order workflow; elapsed 72 hours alone does not override these checks. Dashboard date filters for realized income use `released_at` (recognition day), not order date.

These endpoints reuse existing tables and require no migration. They allow diagnosis against the deployed instance; local tests do not establish why a particular production order is pending.
