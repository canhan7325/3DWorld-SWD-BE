# BE2 Entity and Database Schema Alignment

## Scope and Baseline

- SQL source: `C:\Users\Admin\Downloads\3DWorld_36Tables_Fresh_Supabase_Fixed.sql` (SHA-256 `24256DE01F5721EC19317277C4619F3CC80EDAD9F44B5B9986B5410941FAEF37`). The file is readable outside the repository; no older DDL was substituted.
- SQL declares 36 tables in `public`. `application.properties` sets `hibernate.default_schema=public` and `ddl-auto=validate`. No database connection or live schema validation was attempted.
- Branch `feature/printing`, HEAD `d9adcfa1c34eec618a6e2e238d5a3366d41b6ca3`. Existing staged/unstaged/untracked work was preserved. There are 23 `@Entity` classes mapped to 23 distinct tables and 13 tables without an Entity.
- Inventory: `User`, `Role`, `UserRole`, `UserAddress`, `UserBankAccount`, `Storefront`, `Model3D`, `ModelVersion`, `ModelFile`, `Product`, `ProductMedia`, `ProductVariant`, `Cart`, `CartItem`, `PhysicalOrder`, `PhysicalOrderItem`, `Shipment`, `PrintMaterial`, `PrintRequest`, `PrintRequestItem`, `PaymentTransaction`, `PaymentAttempt`, `AuditLog`. `UserRoleId` is `@Embeddable`, not an Entity. Corresponding repositories exist for these 23 Entities. Relevant isolated tests exist under `features/model` and `features/printing`.

Baseline worktree: four `common/**/.gitkeep` deletions were already staged. `SecurityConfig.java`, `Model3DRepository.java`, `ModelServiceValidation.java`, `LocalModelStorageServiceTest.java` and `ModelUploadControllerTest.java` were already modified. The Model Save controller/service/DTO and two Save tests, CORS tests, prior BE2 audit outputs, and the Save-verified workbook were already untracked. None of these were reverted, staged or committed. This audit added only the `AuditLog.java` edit, this report and the schema-audited tracker workbook.

Repository inventory: `UserRepository`, `RoleRepository`, `UserRoleRepository`, `UserAddressRepository`, `UserBankAccountRepository`, `StorefrontRepository`, `Model3DRepository`, `ModelVersionRepository`, `ModelFileRepository`, `ProductRepository`, `ProductMediaRepository`, `ProductVariantRepository`, `CartRepository`, `CartItemRepository`, `PhysicalOrderRepository`, `PhysicalOrderItemRepository`, `ShipmentRepository`, `PrintMaterialRepository`, `PrintRequestRepository`, `PrintRequestItemRepository`, `PaymentTransactionRepository`, `PaymentAttemptRepository`, `AuditLogRepository`. Relevant existing suites include Model Upload, Save, Version History, Download, Library and local storage tests, plus Print Material management, Print Request Create/Read, file service and mapping tests.

## Entity Comparison Matrix

`Matched` counts mapped SQL columns. A missing Entity is expected for an unimplemented feature. `PARTIAL` means columns map but Java annotations/defaults or runtime behavior do not fully reflect SQL. DB-only FK, UNIQUE and CHECK constraints are not counted as missing Java annotations.

| Database table | Java Entity | Matched columns | Missing columns | Extra columns | Type mismatch | Constraint risk | Result |
|---|---|---:|---|---|---|---|---|
| users | User | 11/11 | none | none | none | Existing BE1 `@OneToMany`; no SQL type gap found | MATCH |
| roles | Role | 5/5 | none | none | none | `createdAt` has no Java initializer; null insert may bypass SQL default | PARTIAL |
| user_roles | UserRole | 6/6 | none | none | none | Java default `ACTIVE` versus SQL `PENDING`; existing BE1 relationships | PARTIAL |
| user_addresses | UserAddress | 8/8 | none | none | none | DB partial unique default-address index | MATCH |
| user_bank_accounts | UserBankAccount | 13/14 | `updated_at` | none | none | Four Java length defaults exceed SQL limits | MISMATCH |
| storefronts | Storefront | 6/9 | `description`, `verified_at`, `created_at` | none | none | Two Java length defaults exceed SQL; status lacks Java default | MISMATCH |
| storefront_subscriptions | none | 0/8 | Entity absent | none | n/a | Feature unimplemented | NOT_IMPLEMENTED |
| models_3d | Model3D | 10/10 | none | none | none | Enum pair CHECK; visibility and timestamps supplied by Java | MATCH |
| model_versions | ModelVersion | 9/9 | none | none | none | DB owns AI uniqueness, same-model parent FK and version uniqueness | MATCH |
| model_files | ModelFile | 10/10 | none | none | none | Partial unique index gives **at most** one primary file | MATCH |
| ai_requests | none | 0/15 | Entity absent | none | n/a | Feature unimplemented | NOT_IMPLEMENTED |
| products | Product | 12/12 | none | none | none | Defaults and lengths compatible | MATCH |
| product_media | ProductMedia | 6/6 | none | none | none | Defaults and lengths compatible | MATCH |
| product_variants | ProductVariant | 8/8 | none | none | none | Price/stock CHECK constraints remain DB-enforced | MATCH |
| carts | Cart | 4/4 | none | none | none | Unique user is DB-enforced and annotated | MATCH |
| cart_items | CartItem | 6/6 | none | none | none | Unique cart/variant and positive quantity DB-enforced | MATCH |
| physical_orders | PhysicalOrder | 15/15 | none | none | none | `order_status` Java length 255 versus SQL 30; UUID checkout group matches | PARTIAL |
| physical_order_items | PhysicalOrderItem | 8/8 | none | none | none | `sku_snapshot` Java length 255 versus SQL 100 | PARTIAL |
| shipments | Shipment | 9/9 | none | none | none | `status` Java length 255 versus SQL 30; parent XOR DB-enforced | PARTIAL |
| rental_offers | none | 0/9 | Entity absent | none | n/a | Feature unimplemented | NOT_IMPLEMENTED |
| rental_plans | none | 0/10 | Entity absent | none | n/a | Feature unimplemented | NOT_IMPLEMENTED |
| rental_contracts | none | 0/17 | Entity absent | none | n/a | Feature unimplemented | NOT_IMPLEMENTED |
| print_materials | PrintMaterial | 6/6 | none | none | none | `NUMERIC(18,2)` maps to `BigDecimal(18,2)` | MATCH |
| print_requests | PrintRequest | 10/10 | none | none | none | No revision/current-state columns in SQL | MATCH |
| print_request_items | PrintRequestItem | 14/14 | none | none | none | `NUMERIC(12,2)` dimensions, source XOR, quantity/infill CHECKs | MATCH |
| print_quotes | none | 0/12 | Entity absent | none | n/a | Feature unimplemented | NOT_IMPLEMENTED |
| print_quote_items | none | 0/10 | Entity absent | none | n/a | Feature unimplemented | NOT_IMPLEMENTED |
| transactions | PaymentTransaction | 19/25 | Six listed below | none | none | Length/precision gaps; Payment source values violate SQL CHECKs | MISMATCH |
| transaction_attempts | PaymentAttempt | 13/20 | Seven listed below | none | none | Length/default gaps; retry number always 1 despite unique pair | MISMATCH |
| wallets | none | 0/7 | Entity absent | none | n/a | Feature unimplemented | NOT_IMPLEMENTED |
| wallet_ledgers | none | 0/10 | Entity absent | none | n/a | Feature unimplemented; append-only DB trigger | NOT_IMPLEMENTED |
| seller_earnings | none | 0/15 | Entity absent | none | n/a | Feature unimplemented | NOT_IMPLEMENTED |
| reports | none | 0/13 | Entity absent | none | n/a | Feature unimplemented | NOT_IMPLEMENTED |
| notifications | none | 0/10 | Entity absent | none | n/a | Feature unimplemented | NOT_IMPLEMENTED |
| audit_logs | AuditLog | 8/8 | none | none | none | SQL 60/40 lengths corrected; append-only DB trigger | MATCH |
| platform_configs | none | 0/5 | Entity absent | none | n/a | Feature unimplemented | NOT_IMPLEMENTED |

All mapped integer FKs have compatible `Integer` types, `BIGINT` file size maps to `Long`, monetary/dimension `NUMERIC` maps to `BigDecimal`, and `TIMESTAMPTZ` maps to `Instant`. Java-generated timestamps are compatible insertion values but may differ from DB `now()` clock/time precision. No BE2 JPA relationship mapping exists. Existing BE1 `User`/`UserRole` relationship mapping remains unchanged.

## Exact Mismatches and Risks

1. `AuditLog.java:27,30` (BE2, corrected): SQL `audit_logs.action_type VARCHAR(60)` and `entity_type VARCHAR(40)` previously used implicit Java length 255. Added `length=60` and `length=40`; no field or API changed.
2. `UserBankAccount.java:28,40,43,52,58` (BE1, approval required): SQL `bank_code VARCHAR(30)`, `account_number_masked VARCHAR(50)`, `verification_status VARCHAR(30)`, `environment VARCHAR(20)` are implicit 255 in Java. SQL `updated_at TIMESTAMPTZ` has no field. This can allow overlength values through Java and omits an audit timestamp.
3. `Storefront.java:31-43` (BE1, approval required): SQL `description TEXT`, `verified_at TIMESTAMPTZ`, `created_at TIMESTAMPTZ` are unmapped. `status` and `seller_verification_status` are SQL `VARCHAR(30)` versus Java implicit 255. SQL default `INACTIVE`/`PENDING` is not represented by Java initialization; inserts with explicit null could fail.
4. `PhysicalOrder.java:32`, `PhysicalOrderItem.java:23`, `Shipment.java:37` (BE1, approval required): SQL `order_status VARCHAR(30)`, `sku_snapshot VARCHAR(100)`, `status VARCHAR(30)` respectively use implicit Java length 255. `PhysicalOrder.checkout_group_id UUID` correctly maps to `UUID`.
5. `PaymentTransaction.java:19-47` (BE1, approval required): SQL `original_transaction_id`, `payee_user_id`, `payee_bank_account_id`, `refund_reason`, `requested_by_user_id`, `reviewed_by_user_id` are unmapped. `transaction_type(20)`, `purpose(35)`, `operation_key(100)`, `payment_stage(30)`, `settlement_mode(30)`, `currency(3)`, `environment(20)`, `status(30)` have implicit Java length 255; `amount NUMERIC(18,2)` lacks precision/scale annotations. Java `BigDecimal` remains a compatible SQL type.
6. `PaymentTransaction.java:49-75` and `PaymentServiceImpl.java:172-180` (BE1, approval required): `request.sourceType()` is persisted as `purpose`; Java accepts `RENTAL_CONTRACT`, `PRINT_REQUEST`, `PRINT_QUOTE`, `WALLET`, while SQL permits `RENTAL`, `POD` and limited other purpose values. `PAYMENT` with `WALLET` or quote-only source also conflicts with `ck_tx_type_sources`; rental PAYMENT uses `GATEWAY` while SQL requires `DIRECT_TO_DESIGNER`. These are verified source-to-DDL conflicts, not merely annotation differences.
7. `PaymentAttempt.java:18-45` (BE1, approval required): SQL `bank_destination_snapshot`, `review_reason`, `verified_by_user_id`, `cod_collected_at`, `cod_settled_at`, `hold_created_at`, `reviewed_at` are unmapped. `payment_method(30)`, `provider_name(100)`, `attempt_status(30)` use implicit Java 255; `evidence_key TEXT` is mapped as a default-length string. Java provider default `ZALOPAY` differs from SQL `SANDBOX`. Constructor always uses attempt number 1; `PaymentServiceImpl.java:43-58` creates another attempt for an existing operation and reuses the same external reference, conflicting with SQL unique `(transaction_id, attempt_number)` and `(provider_name, external_reference)`.
8. `UserRole.java:33` (BE1, approval required): SQL default `approval_status='PENDING'`; Java initializes `ACTIVE`. This may be intentional immediate granting, but the effective insert behavior differs. `Role.java:34` leaves SQL NOT NULL `created_at` uninitialized if a Role is persisted through JPA.

No BE1/common source file was changed. Task Plan ownership labels BE-DB-28/29 as BE2 while implementation and this task's boundary place Payment in BE1; ownership needs reconciliation before any correction.

## Model and Printing Contracts

- Model Save: `ModelVersionSaveServiceImpl` locks an owned active Model, loads a base version of that Model, rejects stale bases, writes `EDITOR_SAVE` with null `ai_request_id`, increments version number and adds one primary `SOURCE_MODEL` file. SQL permits `EDITOR_SAVE`; composite FK `fk_model_versions_parent_same_model` and unique `(model_id,version_number)` provide DB protection. `uq_model_primary_file` enforces at most one primary per version, not existence of one. No ModelVersion append-only trigger exists; immutability currently depends on application behavior.
- Model file roles exactly match SQL's five allowed values. `storage_key` remains internal and is not exposed by Save/History/Download DTOs. Model Save needs no new table.
- Print Create Draft validates positive quantity, infill 0..100, positive `NUMERIC(12,2)` dimensions, active compatible material, owned Model Version and a usable primary file. It persists `model_version_id` and leaves `uploaded_file_key` null, satisfying `ck_pod_source_xor`; status `DRAFT` and production status `PENDING` match SQL. File-based Create first uploads a Model Version, then uses that version. These are source checks; PostgreSQL constraints have not been exercised.
- Latest 36-table SQL has neither `print_request_file_versions` nor `print_request_spec_versions`, and `print_requests` lacks revision/current-state pointers. The Task Plan Extra sheet proposes them, but no approved storage is present. Update Draft, immutable specification/file history, staff revision and quote linkage to a particular revision remain blocked; no speculative Entity or migration was created.

## Verification and Gate

- `mvn.cmd -o -DskipTests test-compile`: exit 0, BUILD SUCCESS after the AuditLog correction.
- `mvn.cmd -o '-Dtest=Model*Test,LocalModelStorageServiceTest,Print*Test,AdminPrint*Test' test`: exit 0, 247 tests, 0 failures, 0 errors, 1 skip (host cannot create test symlinks). This includes Model Upload, Save History, Version History, Secure Download, Print Material and Print Request Create/Read tests.
- No shared Supabase access, Flyway, migration, full integration suite, Hibernate validation against PostgreSQL, commit or push.
- `MODEL_ENTITY_MAPPING = MATCH`; `PRINTING_ENTITY_MAPPING = MATCH`; `SHARED_DEPENDENCY_MAPPING = MISMATCH`; `POSTGRESQL_INTEGRATION = PENDING`.

The updated tracker keeps BE-DB-08/09/10/23/24/25 and the discovered BE1 dependency rows `in_process`. None was marked `done` from isolated tests alone.
