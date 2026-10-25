# BE2 V2 API Registry

Source scan on `feature/printing` HEAD `fc3ade5`: 36 Model/Printing Controller mappings, including uncommitted Staff Review/Quote code. Source mapping does not prove generated OpenAPI or live HTTP. Existing routes were preserved; this workflow refactor adds no route and adds `workflow` to the customer Print Request Detail response.

## Customer POD

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/api/print/materials/getAll` | Material catalog |
| GET | `/api/print/materials/getId/{materialId}` | Material detail |
| POST | `/api/print/requests/create` | Multipart Draft |
| GET | `/api/print/requests/getAll` | Owner list |
| GET | `/api/print/requests/getId/{printRequestId}` | Owner Detail; now includes backend `workflow` |
| GET | `/api/print/requests/getId/{printRequestId}/file` | Current file download |
| POST | `/api/print/requests/getId/{printRequestId}/file/correct` | File correction |
| GET | `/api/print/requests/getId/{printRequestId}/file/versions` | File history |
| GET | `/api/print/requests/getId/{printRequestId}/file/versions/{version}/download` | Historical file download |
| PATCH | `/api/print/requests/getId/{printRequestId}/specifications` | Spec correction |
| GET | `/api/print/requests/getId/{printRequestId}/specifications/versions` | Spec history |
| POST | `/api/print/requests/getId/{printRequestId}/preview/confirm` | Current pair preview confirmation |
| POST | `/api/print/requests/getId/{printRequestId}/submit` | Initial/corrected submit |
| POST | `/api/print/requests/getId/{printRequestId}/cancel` | Ordinary self-service cancellation |
| GET | `/api/print/requests/getId/{printRequestId}/status-history` | Owner status history |
| GET | `/api/print/requests/getId/{printRequestId}/quotes` | Quote history |
| POST | `/api/print/requests/getId/{printRequestId}/quotes/{quoteId}/accept` | Accept current valid quote |
| POST | `/api/print/requests/getId/{printRequestId}/quotes/{quoteId}/request-revision` | Open post-quote revision cycle |

All customer routes are under `PrintRequestController` except Material catalog. Customer identity comes from JWT; path/request input cannot choose owner.

## Staff/Admin POD

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/api/staff/print/requests/{printRequestId}` | Review current File/Spec/settings |
| GET | `/api/staff/print/requests/{printRequestId}/file/download` | Request-bound private download |
| POST | `/api/staff/print/requests/{printRequestId}/request-correction` | Version-bound correction note |
| POST | `/api/staff/print/requests/{printRequestId}/approve-review` | Approve exact current pair |
| POST | `/api/staff/print/requests/{printRequestId}/quotes` | Issue current reviewed quote |
| GET | `/api/staff/print/requests/{printRequestId}/quotes` | Staff quote history |

These are `StaffPrintRequestController` mappings requiring PRINT_STAFF or ADMIN. There is no BE2 payment callback or public production activation endpoint.

## Admin Material and Model

Admin Material: `GET /api/admin/print/materials/getAll`; `POST /api/admin/print/materials/create`; `PATCH /api/admin/print/materials/update/{materialId}`; `PATCH /api/admin/print/materials/updateStatus/{materialId}`.

Model: `POST /api/models/uploads`; `GET /api/models/my`; `GET /api/models/getId/{modelId}`; `GET /api/models/getId/{modelId}/versions`; `GET /api/models/{modelId}/versions/{versionId}`; `POST /api/models/{modelId}/versions/save`; `POST /api/models/{modelId}/versions/{versionId}/restore`; `GET /api/models/{modelId}/files/{fileId}/download`.

Evidence: relevant Model/Printing unit/MVC regression passed, 310 tests with 8 skips. Generated Swagger/OpenAPI and live HTTP are NOT VERIFIED. PostgreSQL v2 and BE1 provider integration are NOT VERIFIED.
