# Marketplace structure refactor

Scope: catalog, cart, storefront, order, shipping, payment, refund, wallet. Keep the project's existing `com.project.w_3d_world.features` root; do not copy another project's namespace or rename unrelated modules.

## Completed

- Concrete business services split into interface in `service` and implementation in `service.impl`: SellerProductService, StorefrontService, AdminStorefrontService, OrderPaymentService, ShippingQuoteService, ShippingPackageService, CodPaymentService, PlatformConfigService.
- Existing catalog/cart/order/payment/shipment/refund/wallet interfaces retained. Controllers and callers inject service interfaces. Direct implementation construction is limited to unit tests; test configuration imports the implementation when registering a concrete bean.
- Request and response records separated into `dto.request` and `dto.response`, one public record per file. All 73 existing DTO records retained, including constructor overloads, validation, mapping helpers and JSON components. Generic `MarketplacePage` remains under `dto` because both response families share it. Admin storefront plan constants stay in their response helper.
- `ShipmentStatus` moved to `shipping.enums` with exact enum values preserved. Order/payment workers moved to `scheduler`; demo shipping provider implementation moved to `service.impl`.
- Explicit imports for the touched marketplace feature packages; implementation methods use `@Override`.
- Java imports in dependent code/tests mechanically updated. No endpoint path, JSON field, status value, database mapping, Flyway migration or financial policy changed by this refactor.

## Existing integration boundaries retained

Provider clients, signature/encryption helpers and configuration records do not require business-service interface/impl wrappers. These include ZaloPayClient, ShippingQuoteSigner, RefundBankEncryption and provider properties. PaymentSourceResolver remains a payment-internal query resolver, not a public business-service implementation.

This change addresses package layout, service abstraction and DTO separation. Existing SQL in several legacy business implementations, generic Map responses in admin finance, and cross-feature repository access are **not** claimed to satisfy every architectural rule in the reference document. Those require separate repository/typed-response refactors with dedicated validation. No placeholder exception/repository classes or empty packages were added.

Printing, AI, model/studio, auth and user internals are not reorganized. References to moved public DTOs may be updated mechanically where needed.

## Validation

`mvn -o -Daether.syncContext.named.factory=noop clean ... test` compiles main and all test sources from scratch and runs the selected marketplace regression suite. The suite covers review authorization, subscription activation without upfront deposit, seller reserve/debt, earning release/reconciliation, refund, seller handover, controller security, signed shipping quote serialization, packing and payment redirects/contracts. Clean-build result: **61 tests passed, 0 failures/errors, 0 skipped**. DTO inventory: 73 records before and after, no record type lost or added. PostgreSQL integration tests and live application startup are not run by this structural-only verification. No live DB migration or data changes.

Deploy from a **clean** build: moved classes must not remain as stale beans in `target/classes`.


## Custom repository interfaces

Separated the seven existing JDBC repositories into interface + `repository.impl` implementation:

- CatalogReadRepository
- ProductReviewRepository
- AdminStorefrontQueryRepository
- RefundReceiptRepository
- AdminDashboardRepository
- EarningReleaseStatusRepository
- WalletHistoryRepository

Service/controller code injects the existing repository interface name. Implementations retain the exact SQL, row mapping and persistence behavior and are the only beans annotated `@Repository`. JPA repositories remain `interface extends JpaRepository`; no artificial JpaRepository inheritance or new entity mapping was added. Tests that directly construct JDBC implementations now construct `*RepositoryImpl`; interface mocks remain unchanged. Explicit Spring test registration uses the concrete implementation. Admin storefront sort validation is accessed through the repository interface instead of a static implementation dependency.
