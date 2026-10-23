# Print Request revision schema proposal

Status: PROPOSED, NOT APPROVED. No migration or live database change has been made.

Source alignment: `BE-FR-POD-01-01`, `BE-DB-24`, `BE-DB-25`, and `Extra` rows 11-13 of `3DWorld_SWD_BE_Task_Plan (1).xlsx`. The full SRS and DDL files were not available locally; this proposal uses the task-plan text and the previously supplied DDL excerpts. Confirm all existing foreign keys and quote references against the official DDL before authoring a migration.

## Decision to approve

One Print Request selects one Model Version at a time and may have multiple configuration Items. Current Create stores `model_version_id` on each Item; it now rejects mixed Model Version IDs for new Requests without removing that column. Existing mixed-version Requests, if any, require an explicit remediation decision during migration. Do not rewrite them silently.

## Proposed structures

1. Add nullable `current_file_version INTEGER` and `current_spec_version INTEGER` to `print_requests`. They are server-controlled version numbers, not client input. Keep them nullable during backfill, then define the required state for new Requests. Do not add unrelated workflow columns from Extra row 11 in this change.
2. Add `print_request_file_versions` with `print_request_file_version_id` identity PK, `print_request_id` FK, positive `version_number`, `model_version_id` FK, `model_file_id` FK, safe file name/format/size/hash snapshots, `created_by_user_id` FK and `created_at TIMESTAMPTZ NOT NULL`. Enforce unique `(print_request_id, version_number)`. Store no public file URL or raw upload key. File versions are append-only.
3. Add `print_request_spec_versions` with `print_request_spec_version_id` identity PK, `print_request_id` FK, positive `version_number`, `file_version_number`, `snapshot_schema_version`, `customer_notes_snapshot`, `specification_data JSONB NOT NULL`, `created_by_user_id` FK and `created_at TIMESTAMPTZ NOT NULL`. Enforce unique `(print_request_id, version_number)` and a composite reference `(print_request_id, file_version_number)` to the file-version unique key. Specification versions are append-only.
4. Add composite foreign keys from each current pointer on `print_requests` to the matching `(print_request_id, version_number)` child key. Make them deferrable or insert the parent with null pointers, insert version rows, then set pointers in the same transaction. Neither pointer may reference another Request.

The `specification_data` contract must be versioned and contain the complete ordered configuration Item set: stable `print_request_item_id` where present, selected `model_version_id`, Material ID/name/Technology/unit snapshots, color, quality, infill, dimensions, quantity, notes, source/file-version identity, and explicit nulls. JSONB is the smallest extension compatible with the Extra sheet's `specification_data`; it requires application-level schema validation and immutable-row enforcement. A normalized revision-item table would provide stronger relational constraints, but adds another table and migration surface. Approve the representation before migration.

## Current state and concurrency

- Initial Create inserts Request, Items, file version 1, specification version 1 and rights audit in one transaction; it sets both current pointers to 1 before commit.
- A configuration-only Update appends a new specification version and keeps the current file version. A source change appends both file and specification versions and records a new rights declaration in that transaction.
- Update locks the owner-scoped Request row, verifies `status = DRAFT` and `locked_at IS NULL`, and compares the client `expectedRevisionNumber` with the durable `current_spec_version`. A mismatch returns 409. Version numbers are assigned by the server; the unique keys provide a final collision guard.
- Identical full specifications do not append a revision or audit event. A submitted/locked Request cannot be changed by Customer Update.
- `GET /getId/{id}` resolves the current specification through `current_spec_version`. A future history read uses `(print_request_id, version_number)` and owner authorization. Neither operation exposes internal cost or storage keys.

## Existing Items and Quote references

Do not update or delete old `print_request_items` rows that may be referenced by `print_quote_items.print_request_item_id`. A future Update can append new Item rows for the new current specification and record their IDs in its immutable snapshot; it must not treat all Items with the same `print_request_id` as current. Adapt current reads only with the approved revision contract. Confirm Quote's exact FK and whether Quote needs an explicit revision ID before implementing Staff Quote or Submit.

## Migration and verification gate

1. In an isolated PostgreSQL environment, inventory existing Requests and quoted Item references. Detect mixed `model_version_id` Requests and null/malformed source or Material references; report them for an explicit business decision.
2. For each existing Request, build only a baseline revision from its **current persisted** Items and source metadata. Preserve the existing Item IDs, accepted Quotes and audit events. Do not claim to reconstruct older specifications that were never stored.
3. Set the current pointers after baseline rows exist. Validate owner linkage, pointer FKs, uniqueness, snapshot fidelity and no cross-Request references before tightening nullability for new Requests.
4. Test rollback of Request/Item/revision/audit writes, concurrent expected-revision updates, Material deactivation locking, FK RESTRICT behavior and Quote references against isolated PostgreSQL. Do not use shared Supabase for this gate.

`POST /api/print/requests/submit/{printRequestId}` remains unimplemented until Update/history and Submit state transitions are approved. Submit must lock the owner-scoped Request, verify current file/spec pointers and selected source/material eligibility, then transition atomically; no Staff Review, Quote or Payment endpoint is part of this proposal.
