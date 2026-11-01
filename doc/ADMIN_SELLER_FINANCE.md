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
