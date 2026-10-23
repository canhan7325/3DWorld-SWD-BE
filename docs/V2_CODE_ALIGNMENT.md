# V2 code alignment on main

This change targets the existing BE1 functionality and the already implemented BE2 entities. It does not create a schema, run migrations, update Supabase or introduce APIs for unimplemented v2 modules. Use the reviewed fresh 48-table v2 SQL first; Hibernate remains in `validate` mode.

## BE1 changes

- Existing entities now map v2 fields for users, addresses, products/media/variants, cart items, orders/items, shipments, transactions/attempts and audit logs.
- JSONB uses Hibernate `@JdbcTypeCode(SqlTypes.JSON)`. Existing String accessors/DTO fields keep their Java type: Hibernate stores raw JSON rather than a JSON-encoded quoted string. Audit metadata preserves existing valid JSON and wraps legacy free-form strings in a `detail` object.
- Profile supports date of birth; address input/output supports province, district and ward. Google ID is mapped only: Google Login is not implemented. Phone changes invalidate phone and active bank verification with an audit record.
- User row locks serialize cart mutations and address/bank default changes. Default flags are cleared/flushed before the selected default is set to respect the existing partial unique indexes.
- Catalog is public; eligible storefronts are filtered before pagination, stable sorting includes ID, and nullable search parameters are cast to String for PostgreSQL.
- Cart saves the initial acknowledged price. Responses include `previousUnitPrice` and `priceChanged`; price changes are not silently accepted during checkout. `POST /api/cart/items/{itemId}/acknowledge-price` accepts the current price. Existing cart rows whose price is NULL require acknowledgement too.
- Checkout requires `shippingAddressId` and `idempotencyKey`. It snapshots an owned address from the database. The legacy `shippingAddressSnapshot` request field is retained for compatibility but ignored. A repeated key returns the same seller child orders; a different payload with that key is rejected. Variants lock in ascending ID order across sellers.
- New order responses include order code, checkout group, reservation expiry/state, address and policy snapshots. Each order stores expiry, commission/refund/auto-confirm values. Configuration defaults preserve current behavior: shipping fee and commission default to zero, refund 72h and auto-confirm 24h. Configure these before using the workflow; changes affect new orders only.
- Expiry jobs lock/recheck orders before releasing variants, with persisted reservation state guarding repeated release. Seller fulfillment only allows PAID -> PROCESSING; it consumes reserved stock once. Seller cannot mark an unpaid order PAID or mark delivery via the generic status API.
- Verified physical payment locks the order and moves a valid reserved order to PAID. Late/expired/cancelled/mismatched payment remains financially SUCCESS but sets `fulfillment_status=RECONCILIATION_REQUIRED`; it does not resurrect the order or consume another reservation. Automatic refund of such exceptions is not implemented.
- Payment purpose/type/source now matches SQL CHECK constraints: TOPUP/WALLET, RENTAL/DIRECT_TO_DESIGNER, POD with its print_request source and optional quote reference. Wallet ownership is checked. Each new top-up requires `idempotencyKey`; retries reuse it. The obligation key is hashed to fit VARCHAR(100). Attempts store app ID/time, expiry, order URL, provider event JSON and a dedicated provider transaction ID. Reconciliation and callbacks lock before status updates. Unknown create outcomes retain their reference for querying rather than issuing another charge automatically.
- Direct `POST /api/payments/{transactionId}/refund` now returns HTTP 409 instead of issuing a refund from a caller-provided amount. The v2 refund request/reviewer/settlement workflow is not implemented; the provider adapter is retained for that future integration.
- Shipping requires PROCESSING + consumed reservation. The seller must process the paid order before creating a shipment. Order locks serialize shipment creation/events. Repeated same-state delivery is a no-op; invalid regressions are rejected. State changes append order history and shipping actions write actor/reference/time audit records. Delivery never sets received/settled facts.

### Request changes

Checkout example:

```json
{
  "cartItemIds": [1, 2],
  "shippingAddressId": 1,
  "idempotencyKey": "checkout-unique-client-key"
}
```

Top-up example (existing endpoint):

```json
{
  "sourceType": "WALLET",
  "sourceId": 1,
  "stage": "TOPUP",
  "amount": 50000,
  "description": "Wallet top-up",
  "idempotencyKey": "topup-unique-client-key"
}
```

A verified top-up still requires the future wallet ledger handler; do not mark WAL tasks complete just because a payment succeeded.

## BE2 handoff: entity-only changes

`PrintRequest` now maps:

- JSONB `shipping_address_snapshot` (String accessor preserved).
- `cancellation_reason`, `cancelled_at`.
- `current_file_version`, `current_quote_id`, `current_spec_version`.
- `submitted_at`, `handled_at`, `handled_by_user_id`.
- `copyright_confirmed`, `preview_confirmed` (default false).

Model3D, ModelVersion, ModelFile, PrintMaterial and PrintRequestItem have no added/changed columns in the reviewed v2 schema; their implementation is unchanged. No BE2 service, DTO, controller, validation or tests were edited.

BE2 still needs to implement/map new modules as its tasks reach them:

1. print_request_file_versions / print_request_spec_versions: immutable history, increasing per-request versions and current pointers.
2. PrintQuote / PrintQuoteItem: file/spec version snapshots, accepted quote pointer, re-quote rules.
3. print_request_status_history: append-only transitions with actor/reason/event reference.
4. print_request_technical_reviews: reviewer authorization and technical review data.
5. POD submit/review/cancel and confirmation gates using the new fields; lock file/spec/quote after deposit.
6. Quote/deposit/remaining/production integration and fulfillment handler with payment. Current generic payment code does not activate POD production or rental entitlements.
7. Update `PrintRequestMappingTest.requestMapsOnlyTheApprovedTenColumns`: it intentionally asserts the old 10-field entity and now fails because v2 adds 10 fields and JSONB. Keep the service acceptance tests and extend the column/type assertions for v2.
8. Update opt-in PostgreSQL test schemas/fixtures that create the old print_requests layout. Existing model-only schema definitions may also include the old shared users mapping.

## Still outside existing implemented scope (BE1)

The new packaging/reviews/refund tables are not automatically features: no packaging engine, review APIs, refund approval/settlement workflow, wallet exactly-once ledger handler or admin config workflow is introduced here. The new entity fields do not make those tasks Done. Shipping fee is currently a configurable flat snapshot, not a package/carrier quote. Account capabilities still need extensions when new modules are implemented.

## Validation

- Compiles and all test sources compile.
- Opt-in V2PersistenceIntegrationTest validates every currently scanned entity against the local 48-table PostgreSQL database and exercises checkout replay, address JSON snapshot independence, price acknowledgement, repeated expiry release, physical payment -> processing/consume, eligible-storefront pagination and JSONB audit storage.
- Existing unit/controller tests were run. The old BE2 PrintRequestMappingTest has the documented expected mismatch and was left unchanged; other non-database tests pass after preserving existing JSON audit payload formatting.
- W3dWorldApplicationTests (the default context test) was excluded to avoid loading the developer's real database/environment. Opt-in existing PostgreSQL suites were not enabled or rewritten.
- No real payment, Cloudinary upload or Supabase schema/data update was performed.
