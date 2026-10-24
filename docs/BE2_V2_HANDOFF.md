# Bàn giao BE2 — Khang: schema v2 và kế hoạch implementation

Ngày đối chiếu: **24/10/2026**. Nguồn: task plan `(3)`, code workspace `main` (gồm thay đổi chưa commit), ERD v2 và schema PostgreSQL local 48 bảng đã validate.

Excel đi kèm: [3DWorld_SWD_BE_Task_Plan_v2_2026-10-24.xlsx](3DWorld_SWD_BE_Task_Plan_v2_2026-10-24.xlsx). File này là tài liệu bàn giao, không phải đã sửa các service BE2 hoặc đã deploy DB.

## 1. Quy ước trạng thái và ownership

- **Done**: implementation đáp ứng scope ghi rõ, có source/test evidence. Không đồng nghĩa đã UAT production/provider.
- **In-progress**: mọi task chưa đủ acceptance, bao gồm chưa có code/chờ schema/policy. Excel có cột “Mức triển khai” để tránh nhầm.
- DB task mapping Done không làm task nghiệp vụ Done. Riêng PrintRequest đã thêm field nhưng test mapping cũ chưa cập nhật.
- Giữ owner chức năng gốc từ file (3). Sheet DB48 đề xuất một owner Entity/Repository mỗi bảng. Những đổi ownership so với file cũ phải được nhóm review, không tự chia microservice.
- Đề xuất BE1 sở hữu transactions/transaction_attempts/platform_configs/audit; BE2 sở hữu wallets/wallet_ledgers/seller_earnings và Model/AI/Rental/POD/Notifications. Task BE1 top-up gọi BE2 ledger handler; không tự sửa bảng tài chính BE2.

## 2. Code đã thay đổi liên quan BE2

Chỉ sửa **Entity PrintRequest** phía BE2; không sửa service/controller/DTO/validation/test BE2. Các Model3D/ModelVersion/ModelFile/PrintMaterial/PrintRequestItem không có thay đổi cột trong v2 đã rà.

| Field | Kiểu Java / SQL | BE2 cần xử lý |
|---|---|---|
| shipping_address_snapshot | String + @JdbcTypeCode(JSON) / JSONB | Nội dung phải JSON hợp lệ; không ghi plain text. Java String accessor vẫn giữ để tránh phá DTO cũ. |
| cancellation_reason, cancelled_at | String / Instant | Cancel đúng lifecycle, reason/actor, không cancel đổi ý sau production lock. |
| current_file_version | Integer | Là version number theo request, không phải file_version_id. Phải tạo file-version record trước khi set pointer. |
| current_spec_version | Integer | Là version number theo request; không set ID row hoặc dùng version của request khác. |
| current_quote_id | Integer | Quote phải thuộc cùng print_request; composite FK enforce. |
| submitted_at | Instant | Chỉ set khi submit hợp lệ; không đồng nghĩa đã accepted quote/deposit. |
| handled_at, handled_by_user_id | Instant / Integer | Staff/Admin authorization; reviewer/assignment reference và audit. |
| copyright_confirmed | boolean default false | Ghi declaration user, không xem như bằng chứng bản quyền pháp lý. |
| preview_confirmed | boolean default false | User đã xác nhận preview đúng file/spec version theo contract; không tự set true khi upload. |

AuditLog là shared Entity của BE1 đã đổi metadata sang JSONB và thêm actor_role/old_value/new_value. Constructor giữ nguyên JSON hợp lệ (kể cả decimal formatting) và bọc legacy text thành {"detail":...}. BE2 nên truyền JSON cấu trúc, tuyệt đối không log secret/full bank number.

## 3. Bảng BE2 phụ trách (đề xuất v2)

| Bảng | Feature | Entity hiện tại |
|---|---|---|
| `ai_requests` | AI Generation | Chưa có |
| `model_files` | Model Library & Versions | ModelFile |
| `model_versions` | Model Library & Versions | ModelVersion |
| `models_3d` | Model Library & Versions | Model3D |
| `notifications` | Governance & Operations | Chưa có |
| `print_materials` | Print On Demand | PrintMaterial |
| `print_quote_items` | Print On Demand | Chưa có |
| `print_quotes` | Print On Demand | Chưa có |
| `print_request_file_versions` | Print On Demand | Chưa có |
| `print_request_items` | Print On Demand | PrintRequestItem |
| `print_request_spec_versions` | Print On Demand | Chưa có |
| `print_request_status_history` | Print On Demand | Chưa có |
| `print_request_technical_reviews` | Print On Demand | Chưa có |
| `print_requests` | Print On Demand | PrintRequest |
| `rental_contracts` | Rental | Chưa có |
| `rental_offers` | Rental | Chưa có |
| `rental_plans` | Rental | Chưa có |
| `seller_earnings` | Wallet & Earnings | Chưa có |
| `wallet_ledgers` | Wallet & Earnings | Chưa có |
| `wallets` | Wallet & Earnings | Chưa có |

BE1 sở hữu bảng referenced không có nghĩa BE2 được bỏ FK/ownership check. Cross-owner mutation cần service interface/event contract. Bảng mới chưa có Entity không được tự đánh Done.

## 4. API BE2 đang có thật trên Swagger

| Method | Path | Scope / trạng thái |
|---|---|---|
| GET | `/api/models/{modelId}/files/{fileId}/download` | BE-FR-LIB-02-01, BE-FR-LIB-02-02, BE-FR-VIW-01-01 |
| GET | `/api/models/my` | BE-FR-LIB-01-01 |
| GET | `/api/models/getId/{modelId}` | BE-FR-LIB-01-01, BE-FR-VIW-01-02 |
| POST | `/api/models/uploads` | BE-FR-LIB-02-01, BE-FR-LIB-02-02 |
| GET | `/api/models/getId/{modelId}/versions` | BE-FR-LIB-03-01 |
| GET | `/api/models/{modelId}/versions/{versionId}` | BE-FR-LIB-03-01, BE-FR-VIW-01-01, BE-FR-VIW-01-02 |
| POST | `/api/models/{modelId}/versions/{versionId}/restore` | BE-FR-LIB-03-01 |
| POST | `/api/models/{modelId}/versions/save` | BE-FR-LIB-03-01, BE-FR-EDT-05-01, BE-FR-EDT-05-02 |
| GET | `/api/admin/print/materials/getAll` | BE-V2-POD-MAT-01 |
| POST | `/api/admin/print/materials/create` | BE-V2-POD-MAT-01 |
| PATCH | `/api/admin/print/materials/update/{materialId}` | BE-V2-POD-MAT-01 |
| PATCH | `/api/admin/print/materials/updateStatus/{materialId}` | BE-V2-POD-MAT-01 |
| GET | `/api/print/materials/getAll` | BE-FR-POD-01-01, BE-V2-POD-MAT-01 |
| GET | `/api/print/materials/getId/{materialId}` | BE-FR-POD-01-01, BE-V2-POD-MAT-01 |
| POST | `/api/print/requests/create` | BE-FR-POD-01-01, BE-V2-POD-FILE-01 |
| GET | `/api/print/requests/getAll` | BE-FR-POD-01-01 |
| GET | `/api/print/requests/getId/{printRequestId}` | BE-FR-POD-01-01 |
| GET | `/api/print/requests/getId/{printRequestId}/file` | BE-FR-POD-01-01, BE-V2-POD-FILE-01 |

POD create hiện nhận **multipart file + materialId + rightsDeclarationAccepted**, tạo draft qua ModelUploadService. Chưa có submit/spec edit/review/quote/production/remaining endpoints. Không ghi URL API dự kiến thành URL đã có trên Swagger.

## 5. Những task BE2 có thể giữ Done theo scope đã kiểm tra

- **BE-FR-LIB-02-01 — File validation/storage/metadata và permission APIs**: Upload file validation/storage/hash/metadata/size + own authorized download; unit/controller tests có trong repo. Giới hạn: Local private storage; không đồng nhất Cloudinary avatar với storage file 3D.
- **BE-FR-LIB-03-01 — Immutable version/save/restore và reference guards**: Immutable new versions, head lock/stale-base conflict, restore thành version mới, history và safe metadata APIs. Giới hạn: Core own Model versions đã code/test; không bao gồm POD-specific file/spec version workflow.
- **BE-V2-POD-MAT-01 — Admin material catalog và compatible technology/unit**: AdminPrintMaterialService + PrintMaterialService + controller/unit tests hiện có.

Những task tổng có phần đã code nhưng còn thiếu vẫn ở In-progress; ví dụ library CRUD chưa đủ rename/delete/visibility, file cleanup chưa có durable orphan worker, POD mới draft.

## 6. Thứ tự BE2 cần làm tiếp

### P0 — Sửa test/schema contract trước khi mở workflow

1. Cập nhật `PrintRequestMappingTest.requestMapsOnlyTheApprovedTenColumns`: v2 có 20 field, address JSONB. Test cũ hiện fail vì cố định 10 cột; không xóa validation chỉ để pass.
2. Cập nhật fixture DDL của các opt-in Printing/Model PostgreSQL tests, kể cả bảng users dùng chung có avatar/date_of_birth/google_id. Không dùng ddl-auto=create/update để che lỗi mapping.
3. Thêm repository/entity cho 4 bảng POD v2: file versions, spec versions, status history, technical reviews. Giữ UNIQUE(request,version), FK same-request và append-only history.

### P1 — Hoàn thiện POD file/spec/submit/review/quote

4. `BE-V2-POD-FILE-01`: upload/correction -> new immutable file version, checksum/size/type validation, rollback cleanup, current_file_version theo request lock.
5. `BE-V2-POD-SPEC-01`: material/technology/spec JSON validation, new spec version, đồng bộ current_spec_version; file/spec đổi phải invalid old quote.
6. `BE-V2-POD-STATE-01`: rights/preview flags + submit/cancel + status history, server timestamp, actor/reason; tạo draft không tự submit/verify.
7. `BE-V2-POD-REVIEW-01`: Staff technical review/handler; evidence/review_data JSON, reviewer permission và audit.
8. `BE-V2-POD-QUOTE-01` + FR-POD-03: quote/quote items snapshot file/spec version, current_quote_id đúng request, expire/accept guard, total=deposit+remaining.

### P2 — Chốt integration và money handler

9. `BE-V2-WAL-HANDLER-01`: nếu theo phân công bảng đề xuất, BE2 implement verified topup credit handler; BE1 callback gọi bằng transaction ID. Lock wallet, unique ledger key, replay trả outcome cũ. Không nhầm AI credits với VND.
10. POD deposit success mới lock/queue đúng quote/file/spec; remaining chỉ sau production. Callback/redirect FE không tự cấp quyền/queue; không ghi transaction SUCCESS từ POD service.
11. POD chỉ dispatch khi đủ remaining (remaining=0 xử lý không tạo payment 0). Gọi BE1 Shipment service cho print_request source; Physical shipment route hiện tại chưa hỗ trợ POD.
12. Wallet/earning/refund correction chỉ dựa verified source và idempotency. Seller_earnings hiện physical-only; chốt Rental/Digital settlement schema/policy trước khi thêm release handlers.

### P3 — Notifications, Rental và AI

13. Notification dedupe theo (recipient_user_id,dedupe_key), source_event_id/expiry; deep link vẫn check permission. conversation_id chưa là chat FK.
14. Rental activation/access theo verified payment, start/end/flags snapshot; request-time expiry guard, stop-new-rentals không revoke hợp đồng cũ. Không biến rental_offers thành Digital SALE.
15. AI provider/ingest/history theo tasks AIG; reserve/consume/restore credits cần ledger/persistence được chốt, không chỉ update balance rồi coi exactly-once.

## 7. Contract cần thống nhất với BE1

| Boundary | BE1 | BE2 | Invariant |
|---|---|---|---|
| Payment -> Wallet | Verify provider, lock obligation/attempt, emit/call transaction ID | Ledger credit handler dưới wallet lock | Replay một effect; pending/unknown không credit |
| Payment -> POD | Verify amount/reference/stage/quote snapshot | Lock request, đúng accepted file/spec, queue/remaining gate | Không tin redirect; callback cũ không queue quote mới |
| POD -> Shipment | Shipment source XOR physical_order/print_request, event/audit | Assert fully paid + production complete | Delivered không auto received/revenue release |
| Payment -> Rental | Verify financial outcome | Entitlement/start/end/access/expiry | Payment success chưa đủ nếu entitlement thất bại; reconciliation rõ |
| Refund -> Wallet/Earning | Request/review/verified refund orchestration | Atomic reversal bằng compensating ledger entries | Không update/delete ledger; refund <= paid-unrefunded |
| Events -> Notification | Business event ID, actor/resource/time | Authorized recipient/deep link + dedupe | Duplicate event không gửi/trạng thái trùng |

## 8. Schema v2 chưa đủ cho các chức năng này

- Design nội bộ: chưa có request/assignment/quote/revision/delivery schema.
- Chat: chưa conversations/participants/messages; notification field không thay chat.
- Digital SALE/current ownership/provenance/access grants: chưa đủ tables. Model owner column đơn lẻ không là history/entitlement.
- Project sharing VIEW/EDIT membership: chưa có persistence.
- AI-credit operation ledger: chưa có normalized ledger/reservation history.
- Production runs/defects/workload/repair-reprint: chỉ production_status/history không đủ cho full workload/remediation.
- Seller earnings cho Rental/Digital: bảng hiện FK Physical order NOT NULL. Không gắn rental vào physical_order giả.
- Review v2 đang Physical-only. POD/Rental/Design reviews cần source design riêng.

Các task này vẫn In-progress với “Chờ mở rộng schema/policy”, không tạo bảng ngoài 48 bảng mà không thống nhất nhóm.

## 9. Thay đổi BE1 mà BE2/FE cần biết

- Checkout cần `shippingAddressId` owned + `idempotencyKey`; `shippingAddressSnapshot` client còn nhận nhưng bị bỏ qua, snapshot từ DB.
- Cart trả `previousUnitPrice` / `priceChanged`; `POST /api/cart/items/{itemId}/acknowledge-price` trước checkout khi giá đổi.
- Order có reservation state/expiry và policy snapshots; Seller chỉ PAID -> PROCESSING; khi processing consume stock.
- Payment `sourceType=WALLET` cần idempotencyKey cho topup mới. Dedicated provider_transaction_id thay evidence_key.
- `POST /api/payments/{transactionId}/refund` hiện trả 409 cho tới khi approved refund/settlement workflow có thật.
- Physical shipping yêu cầu PROCESSING + CONSUMED. Không dùng route này để giả shipping POD.
- Google Login chưa có chỉ vì Entity users.google_id đã được thêm.

## 10. Checklist nghiệm thu BE2

- [ ] Mapping/unit/controller suites cập nhật v2 và pass; mapping test cũ không bị bỏ lặng lẽ.
- [ ] Local PostgreSQL fixtures đúng v2, JSONB insert/read và composite FK tested.
- [ ] Upload hỏng/storage fail/rollback không để URL/file version giả.
- [ ] File/spec versions immutable, current pointers và quote snapshots cùng request.
- [ ] Submit/deposit/production/remaining/shipping gates đúng, no premature access/queue.
- [ ] Permission matrix Customer/Staff/Admin/Designer/owner/renter có direct API tests.
- [ ] Duplicate/out-of-order/unknown events không double credit/queue/release.
- [ ] Provider sandbox UAT riêng; báo rõ demo/local storage khác production.

## 11. Toàn bộ task BE2 chưa Done trong Excel

| Task | Chức năng | Mức triển khai | Còn phải làm |
|---|---|---|---|
| BE-FR-LIB-01-01 | Project/model library CRUD và source ownership | Một phần | Chưa đủ update/delete/visibility CRUD, quyền commercial/current owner theo schema và chặn tài nguyên đang được tham chiếu. |
| BE-FR-LIB-02-02 | Secure object/file transfer and orphan cleanup | Một phần | Chưa có durable orphan reconciliation scanner/retry khi cleanup thất bại; cập nhật fixtures PostgreSQL shared users theo v2. |
| BE-FR-VIW-01-01 | Preview metadata và authorized file access contract | Một phần | Preview/viewable representation contract và entitlement cho renter/shared project chưa hoàn chỉnh. |
| BE-FR-VIW-01-02 | Preview representation và units/capabilities metadata | Một phần | Units/dimensions/render capability/preview conversion chưa có đầy đủ. |
| BE-FR-AIG-03-01 | Credit policy/read/admin grant và demo quota | Chưa có code | Chưa có API/service nghiệp vụ tương ứng; triển khai theo acceptance v2 và dependency. |
| BE-FR-AIG-03-02 | Credit operation ledger reserve/consume/restore | Chưa có code | Có balance và credits_used nhưng không có credit operation ledger/reservation idempotency; phải chốt persistence trước triển khai. |
| BE-FR-OPS-02-01 | Business notification event/read/deep link permissions | Chưa có code | Chưa có API/service nghiệp vụ tương ứng; triển khai theo acceptance v2 và dependency. |
| BE-FR-OPS-02-02 | Notification worker/deduplication và delivery log | Chưa có code | Chưa có API/service nghiệp vụ tương ứng; triển khai theo acceptance v2 và dependency. |
| BE-FR-PAY-03-01 | Wallet spend atomic và COD method guard | Chưa có code | Chưa có API/service nghiệp vụ tương ứng; triển khai theo acceptance v2 và dependency. |
| BE-FR-WAL-02-01 | Seller earning Pending và commission snapshot | Chưa có code | Chưa có API/service nghiệp vụ tương ứng; triển khai theo acceptance v2 và dependency. |
| BE-FR-LIB-04-01 | Project VIEW/EDIT grant/revoke authorization | Chưa có code | 48 bảng chưa có project membership/access grants VIEW/EDIT; ownership users không thay thế membership. |
| BE-FR-EDT-04-01 | Editor source access/import metadata contract | Chưa có code | Chưa có API/service nghiệp vụ tương ứng; triển khai theo acceptance v2 và dependency. |
| BE-FR-EDT-05-01 | Persist scene state + model files và new version transaction | Một phần | Persist editable scene state cùng file-role contract; không coi save SOURCE_MODEL là đã đủ Editor scene. |
| BE-FR-EDT-05-02 | Editor export/version conflict contract | Một phần | Export format/render pipeline và scene export contract chưa đủ. |
| BE-FR-AIG-01-01 | Text-to-3D request và async provider submit | Chưa có code | Chưa có API/service nghiệp vụ tương ứng; triển khai theo acceptance v2 và dependency. |
| BE-FR-AIG-02-01 | Image input validation/private storage và async submit | Chưa có code | Chưa có API/service nghiệp vụ tương ứng; triển khai theo acceptance v2 và dependency. |
| BE-FR-AIG-04-01 | Normalize result/status, ingest output và save Model idempotent | Chưa có code | Chưa có API/service nghiệp vụ tương ứng; triển khai theo acceptance v2 và dependency. |
| BE-FR-AIG-04-02 | AI output ingest/storage + result-to-model transaction | Chưa có code | Chưa có API/service nghiệp vụ tương ứng; triển khai theo acceptance v2 và dependency. |
| BE-FR-AIG-05-01 | Generation history/regenerate lineage và fresh charge | Chưa có code | Chưa có API/service nghiệp vụ tương ứng; triển khai theo acceptance v2 và dependency. |
| BE-FR-AIG-06-01 | Provider adapter, timeout/unknown và demo scenarios | Chưa có code | Chưa có API/service nghiệp vụ tương ứng; triển khai theo acceptance v2 và dependency. |
| BE-FR-AIG-06-02 | AI polling/webhook/timeout reconciliation worker | Chưa có code | Chưa có API/service nghiệp vụ tương ứng; triển khai theo acceptance v2 và dependency. |
| BE-FR-PAY-04-01 | Attempt history/retry và reconciliation | Chưa có code | Chưa có API/service nghiệp vụ tương ứng; triển khai theo acceptance v2 và dependency. |
| BE-FR-WAL-03-01 | Atomic financial finalization/commission/release | Chưa có code | seller_earnings hiện chỉ physical_order_id NOT NULL UNIQUE; thu nhập Digital/Rental cần persistence riêng hoặc schema mở rộng. |
| BE-FR-WAL-03-02 | Financial deadline/release scheduler | Chưa có code | Scheduler Digital/Rental earnings bị giới hạn seller_earnings physical-only; cần chốt revenue/settlement policy. |
| BE-FR-WAL-04-01 | Payout request và Available hold atomic | Chưa có code | Chưa có API/service nghiệp vụ tương ứng; triển khai theo acceptance v2 và dependency. |
| BE-FR-WAL-05-01 | Admin payout decision và verified transfer result | Chưa có code | Chưa có API/service nghiệp vụ tương ứng; triển khai theo acceptance v2 và dependency. |
| BE-FR-WAL-05-02 | Payout transfer adapter/demo reconciliation | Chưa có code | Chưa có API/service nghiệp vụ tương ứng; triển khai theo acceptance v2 và dependency. |
| BE-FR-POD-01-01 | Print request/source/version và compatible settings | Một phần | Chưa submit/edit spec đầy đủ, v2 file/spec version records/current pointers, rights/preview flags và quote gates. |
| BE-FR-POD-02-01 | Staff file review/correction/version transition | Chưa có code | Chưa có API/service nghiệp vụ tương ứng; triển khai theo acceptance v2 và dependency. |
| BE-FR-POD-03-01 | Quote snapshot và material/labor/deposit/remaining validation | Chưa có code | Chưa có API/service nghiệp vụ tương ứng; triển khai theo acceptance v2 và dependency. |
| BE-FR-POD-03-02 | Quote validity and obsolete-version guard | Chưa có code | Chưa có API/service nghiệp vụ tương ứng; triển khai theo acceptance v2 và dependency. |
| BE-FR-POD-04-01 | Accepted quote/order/deposit + atomic production lock | Chưa có code | Chưa có API/service nghiệp vụ tương ứng; triển khai theo acceptance v2 và dependency. |
| BE-FR-POD-05-01 | Production runs/post-processing/defect metadata APIs | Chưa có code | Có production_status từng item và status history; chưa có print_runs/defects/preview assets/workload normalized. |
| BE-FR-POD-05-02 | Workload/ETA theo print technology | Chưa có code | Chưa có schema workload/capacity/technology queues để tính ETA đáng tin cậy. |
| BE-FR-POD-06-01 | Remaining payment gate, shipment và service completion | Chưa có code | Chưa có API/service nghiệp vụ tương ứng; triển khai theo acceptance v2 và dependency. |
| BE-FR-POD-07-01 | Manufacturing defect/remedy/full refund policy service | Chưa có code | refund_requests hỗ trợ evidence/refund; repair/reprint/replacement/remedy lifecycle chưa có bảng chuyên biệt. |
| BE-FR-POD-07-02 | POD defect evidence + remediation transaction | Chưa có code | Thiếu normalized remediation/run references; không nhét toàn bộ vào audit_logs. |
| BE-FR-OPS-03-01 | Role-scoped operational/finance dashboard queries | Chưa có code | Chưa có API/service nghiệp vụ tương ứng; triển khai theo acceptance v2 và dependency. |
| BE-FR-DES-01-01 | Internal Design request/review/assignment authorization | Chờ mở rộng schema | Schema 48 bảng chưa có design_requests/assignments/quotes/revisions/deliveries. Không thể đánh Done chỉ bằng users/models/transactions. |
| BE-FR-DES-02-01 | Design quote acceptance/deposit và work gate | Chờ mở rộng schema | Schema 48 bảng chưa có design_requests/assignments/quotes/revisions/deliveries. Không thể đánh Done chỉ bằng users/models/transactions. |
| BE-FR-DES-03-01 | Preview/revision/allowance/approval + Additional Quote | Chờ mở rộng schema | Schema 48 bảng chưa có design_requests/assignments/quotes/revisions/deliveries. Không thể đánh Done chỉ bằng users/models/transactions. |
| BE-FR-DES-03-02 | Additional Design Quote linkage guard | Chờ mở rộng schema | Schema 48 bảng chưa có design_requests/assignments/quotes/revisions/deliveries. Không thể đánh Done chỉ bằng users/models/transactions. |
| BE-FR-DES-04-01 | Remaining paid final access + ownership handover transaction | Chờ mở rộng schema | Schema 48 bảng chưa có design_requests/assignments/quotes/revisions/deliveries. Không thể đánh Done chỉ bằng users/models/transactions. |
| BE-FR-CHT-01-01 | Context conversation participant/message APIs (Optional) | Chờ mở rộng schema | Optional: không có conversations/participants/messages; notifications.conversation_id không có FK và không phải hệ thống chat. |
| BE-FR-DIG-01-01 | Current-owner listing và SALE/RENTAL active exclusion | Chờ mở rộng schema | Schema 48 bảng chưa có Digital SALE offer/order/ownership chain/entitlements. rental_offers chỉ phục vụ Rental; chưa đủ SALE mode. |
| BE-FR-DIG-02-01 | Digital checkout reservation/concurrent payment protection | Chờ mở rộng schema | Schema 48 bảng chưa có Digital SALE offer/order/ownership chain/entitlements. rental_offers chỉ phục vụ Rental; chưa đủ SALE mode. |
| BE-FR-DIG-02-02 | Exclusive sale concurrency/reservation mechanism | Chờ mở rộng schema | Schema 48 bảng chưa có Digital SALE offer/order/ownership chain/entitlements. rental_offers chỉ phục vụ Rental; chưa đủ SALE mode. |
| BE-FR-DIG-03-01 | Ownership transfer + access grant exactly once | Chờ mở rộng schema | Schema 48 bảng chưa có Digital SALE offer/order/ownership chain/entitlements. rental_offers chỉ phục vụ Rental; chưa đủ SALE mode. |
| BE-FR-DIG-03-02 | Ownership entitlement retry/compensation worker | Chờ mở rộng schema | Schema 48 bảng chưa có Digital SALE offer/order/ownership chain/entitlements. rental_offers chỉ phục vụ Rental; chưa đủ SALE mode. |
| BE-FR-DIG-04-01 | Provenance/resale eligibility và digital completion | Chờ mở rộng schema | Schema 48 bảng chưa có Digital SALE offer/order/ownership chain/entitlements. rental_offers chỉ phục vụ Rental; chưa đủ SALE mode. |
| BE-FR-DIG-05-01 | Exceptional digital case/ownership compensation guard | Chờ mở rộng schema | Schema 48 bảng chưa có Digital SALE offer/order/ownership chain/entitlements. rental_offers chỉ phục vụ Rental; chưa đủ SALE mode. |
| BE-FR-REN-01-01 | Rental payment/contract/entitlement activation | Chưa có code | Chưa có API/service nghiệp vụ tương ứng; triển khai theo acceptance v2 và dependency. |
| BE-FR-REN-02-01 | Access deadline guard và rental completion hold | Chưa có code | Rental contract/expiry có bảng, entitlement granularity và income completion cần chốt; seller_earnings physical-only. |
| BE-FR-REN-02-02 | Rental expiry/release job và request-time access guard | Chưa có code | Rental contract/expiry có bảng, entitlement granularity và income completion cần chốt; seller_earnings physical-only. |
| BE-FR-REN-03-01 | Stop rental/current contracts và commercial mode transition | Chưa có code | Chưa có API/service nghiệp vụ tương ứng; triển khai theo acceptance v2 và dependency. |
| BE-V2-WAL-HANDLER-01 | Verified topup -> available ledger handler (BE2 owner) | Chưa có code | Financial obligation lock và wallet lock; unique ledger idempotency; no credit fail/unknown; trả existing result. |
| BE-V2-POD-FILE-01 | POD file version records/current pointer | Một phần | Thêm entity/repository/service v2 và current_file_version; không chỉ map thêm thuộc tính. |
| BE-V2-POD-SPEC-01 | POD specification versions/current pointer | Chưa có code | Specification JSON object hợp lệ, material compatibility, version/request unique, immutable khi locked/deposit. |
| BE-V2-POD-REVIEW-01 | Technical review và handler assignment | Chưa có code | Staff/Admin có quyền; review_data object; handled_by/time; không thay đổi locked file/spec sau deposit. |
| BE-V2-POD-STATE-01 | POD submit/cancel/history và confirmation flags | Một phần | Service/DTO/controller workflow chưa dùng những field này; quyền và transition cần triển khai. |
| BE-V2-POD-QUOTE-01 | Quote current pointer + immutable file/spec snapshot | Chưa có code | Quote snapshot đúng request/version; old quote không accept sau spec/file đổi; total=deposit+remaining; expiry explicit. |
| BE-V2-POD-TEST-01 | Update v2 entity mapping tests và PostgreSQL fixtures | Một phần | Chỉnh test/fixtures BE2; rerun printing/model PG suites trước đánh Done. |
| BE-V2-NOTIF-01 | Notification dedupe/expiry/source event | Chưa có code | Dedupe theo recipient+key; idempotent worker; scoped read/readAt; expire không leak deep link; conversation_id chưa có chat. |

Không thay đổi source BE2 trong lần lập tài liệu này. Hãy cập nhật Excel và file bàn giao sau mỗi PR/merge/UAT; không tự giữ Done khi API/acceptance đã thay đổi.
