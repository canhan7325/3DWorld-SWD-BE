# Marketplace feature package conventions

Use the existing project root `com.project.w_3d_world.features`.

```text
features/<feature>/
  controller/       HTTP, validation, endpoint authorization, service interface calls
  dto/request/      One request record per file, validation annotations
  dto/response/     One response record per file, explicit JSON fields
  entity/           JPA mappings and entity state helpers
  enums/            Business enums, exact persisted values
  repository/       JPA repository interfaces and custom query contracts
  repository/impl/  JDBC/custom query implementations
  service/          Public business interfaces
  service/impl/     Transactional implementations
  scheduler/        Scheduled jobs, calling service interfaces
```

Add infrastructure/config/mapper/exception packages only when there is real code to put there. Provider clients, signing helpers and pure utilities do not need artificial service interface wrappers. Shared response containers can stay at `dto` if multiple response types use them.

Controller and feature integrations inject service interfaces, never implementations. Keep `@Transactional` on implementations and preserve read/write boundaries. Constructor injection with final dependencies, explicit imports, and `@Override` on interface implementations are the defaults. Do not write fully qualified class names in ordinary declarations when an import is possible. Unit tests may construct implementations with mocks.

Structural refactors must preserve endpoint paths, JSON components, validation constraints, status values, table/column mapping and business behavior. Update imports in dependent modules mechanically; do not reorganize a teammate's feature. Use clean compile/test builds after moves to remove stale compiled classes. Record changes and remaining integration limitations in MODULE_INTEGRATION_STATUS.md.

Custom query repositories use an interface plus an `@Repository` implementation in `repository.impl`. Keep JPA repositories as interfaces extending JpaRepository; Spring Data supplies their implementation. Do not make JDBC interfaces extend JpaRepository without an entity/aggregate mapping just for naming consistency.
