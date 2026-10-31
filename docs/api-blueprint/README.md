# Content Builder — API Blueprint cho phần bổ sung so với main

Đọc từ đây để hiểu phần code được đưa từ local lên nhánh `feature/2026-license-question-bank-media`. Mốc source local đã commit: `225c062`; main dùng để đối chiếu: `e727ff795ed149da4eb9e77c2d6a22be8f8e03a6`.

Các file chi tiết giữ mẫu **Part 0 — Classification & Identity → Part 1 — API Contract → Part 2 — Processing Specification → Part 3 — Data Interaction → Part 4 — Operational Notes** của repo. Nội dung mô tả Controller/Form/Service/Mapper/Repository và DTO thực tế, không tạo thêm chức năng từ mô tả.

## Đọc theo nhu cầu

1. Đọc [Contract Notes](content_builder_contract_notes.md) để hiểu quyền, response/error shape, ID/version và các giới hạn đang có.
2. Backend Examination đọc nhóm internal source. FE học viên không dùng các response chứa đáp án nội bộ.
3. FE/admin đọc nhóm biển báo và mẹo; lưu ý phân trang, UUID/Media UUID và các trường filter thực sự được controller nhận.
4. Thành viên dựng môi trường đọc [LOCAL_SETUP](../LOCAL_SETUP.md), [hướng dẫn 600 câu](../../CONTENT_BUILDER_SERVICE_GUIDE.md) và [runbook reference content](../../DEPLOY_REFERENCE_CONTENT.md).

## 1. Nguồn câu hỏi nội bộ cho Examination

| Method | Endpoint | Tài liệu | Thay đổi so với main |
|---|---|---|---|
| GET | `/api/v1/content-builder/internal/questions/bank` | [Đọc bank theo hạng](get_internal_question_bank.md) | API mới: catalog 6 chương và metadata theo bankVersion/license |
| GET | `/api/v1/content-builder/internal/questions/metadata` | [Metadata câu hỏi](get_questions_metadata.md) | API có sẵn, cập nhật auth, license, published path, DTO/revision và service xử lý |
| POST | `/api/v1/content-builder/internal/questions/batch` | [Batch chi tiết nguồn](get_internal_questions_batch.md) | API có sẵn, cập nhật auth, license, kiểm chuẩn source, thứ tự, status/revision/explanations |

Nhóm này dùng `InternalQuestionController → ExamSourceAccess → ExamQuestionReadService` cùng các repository nội dung. `ChapterRepository.findPublishedByBankVersion` cung cấp catalog. DTO cũ được giữ các trường ban đầu và bổ sung dữ liệu snapshot; file metadata blueprint cũ được cập nhật ngay tại tên cũ để không để hai contract trái nhau.

Luồng tích hợp:

1. Examination đọc bank hoặc metadata với đúng hạng bằng và quyền backend.
2. Chọn question UUID theo nghiệp vụ của Examination.
3. Đọc batch bằng cùng license, đối chiếu contentVersion và lưu snapshot trong luồng bên Examination.
4. Không chuyển isCorrect/explanations cho học viên trước thời điểm quy tắc thi cho phép. Việc phát hành kết quả thuộc repo Examination.

## 2. Biển báo giao thông

| Method | Endpoint | Quyền | Tài liệu |
|---|---|---|---|
| POST | `/api/v1/content-builder/traffic-signs/draft` | ROLE_ADMIN | [Tạo nháp](create_draft_traffic_sign.md) |
| POST | `/api/v1/content-builder/traffic-signs/published` | ROLE_ADMIN | [Tạo đã xuất bản](create_published_traffic_sign.md) |
| GET | `/api/v1/content-builder/traffic-signs` | Đã xác thực | [Danh sách published](list_traffic_signs.md) |
| GET | `/api/v1/content-builder/traffic-signs/{id}` | Đã xác thực | [Chi tiết published](get_traffic_sign_detail.md) |

Các class mới: TrafficSignController, CreateTrafficSignRequest, TrafficSignResponse, TrafficSignService, TrafficSignMapper, TrafficSignEntity, TrafficSignRepository. Migration V5 tạo bảng `traffic_signs` và unique index. Bộ nguồn đi cùng gồm 221 biển/221 WebP và importer upload/publish, nhưng giới hạn form API không bị đồng nhất với số lượng của bộ import.

## 3. Mẹo ghi nhớ

| Method | Endpoint | Quyền | Tài liệu |
|---|---|---|---|
| POST | `/api/v1/content-builder/memory-tips/draft` | ROLE_ADMIN | [Tạo nháp](create_draft_memory_tip.md) |
| POST | `/api/v1/content-builder/memory-tips/published` | ROLE_ADMIN | [Tạo đã xuất bản](create_published_memory_tip.md) |
| GET | `/api/v1/content-builder/memory-tips` | Đã xác thực | [Danh sách published](list_memory_tips.md) |
| GET | `/api/v1/content-builder/memory-tips/{id}` | Đã xác thực | [Chi tiết published](get_memory_tip_detail.md) |

Các class mới: MemoryTipController, CreateMemoryTipRequest, MemoryTipResponse, MemoryTipService, MemoryTipMapper, MemoryTipEntity, MemoryTipRepository. V5 cũng tạo bảng `memory_tips`, unique source và thứ tự theo phiên bản. Bộ nguồn đang bàn giao có 12 mẹo.

## 4. Những thay đổi source khác cần hiểu

### QuestionService legacy và các API đã có

Commit feature `79a35fac` đã có trước đợt commit local bổ sung bộ 600 câu/ảnh/import tools và hai helper overload nhận license trong QuestionService. Main chưa có phần đó.

Tuy nhiên controller internal hiện tại đã chuyển sang **ExamQuestionReadService**. Vì vậy helper `getQuestionsMetadataForExam(..., licenseClass)` / `getQuestionsBatchForLicense` trong QuestionService không phải contract mới đang được ba endpoint internal gọi. Không dùng cách fallback/hardcode bank ở helper legacy để thay thế mô tả thuật toán strict của service mới.

Các URL public `/questions`, `/chapters`, `/learning-programs`, options/blocks đã có trước và không được thêm endpoint CRUD mới bởi đợt bổ sung này. Blueprint lịch sử của các URL đó được giữ lại. Trường hợp một tài liệu cũ mô tả định hướng chưa có trong code, ưu tiên code và ghi nhận giới hạn trong Contract Notes thay vì âm thầm coi đã triển khai.

### Import, ảnh và database

| Thành phần | Vai trò | Tài liệu cần đọc |
|---|---|---|
| `data/question-banks/2026/600/`, `tools/*question*2026.py`, `publish_chapters_2026.py` | Bộ 600 câu/318 ảnh/6 chương và nạp dữ liệu qua API đã có | [Service guide](../../CONTENT_BUILDER_SERVICE_GUIDE.md) |
| `data/question-banks/2026/traffic_signs/`, `memory_tips/` | 221 biển/ảnh và 12 mẹo nguồn | [Reference runbook](../../DEPLOY_REFERENCE_CONTENT.md) |
| `upload_traffic_sign_images.py`, `publish_traffic_signs_2026.py`, `publish_memory_tips_2026.py` | Upload Media, publish reference content và checkpoint theo môi trường | [Reference runbook](../../DEPLOY_REFERENCE_CONTENT.md) |
| `V5__add_reference_content_tables.sql` | Schema hai bảng reference content, không chứa toàn bộ dữ liệu nguồn | [Local setup](../LOCAL_SETUP.md) |
| `docs/sql/00__bootstrap_trigger_set_updated_at.sql` | Tiền đề dành riêng database mới, trống trước V2–V5 | [Local setup](../LOCAL_SETUP.md) |

Không có endpoint `/import` mới được tạo bởi các Python tools. Chúng là client bootstrap dùng API hiện có; build/push Java không tự upload ảnh hoặc publish dữ liệu. Không commit token/checkpoint/Media UUID riêng theo máy để giả định môi trường khác sẽ chạy được.

## 5. Phạm vi bản tài liệu này

- 11 endpoint được mô tả: 9 mới so với main, 2 endpoint cũ được cập nhật contract.
- 13 file `.md` trong đợt tài liệu: 10 blueprint endpoint mới, cập nhật `get_questions_metadata.md`, thêm Contract Notes và chỉ mục này.
- Giữ nguyên Java, SQL migration, JSON/WebP, cấu hình và các blueprint lịch sử khác. Không chạy migration hoặc sửa nghiệp vụ trong bước viết tài liệu.
- Chưa có thao tác push/merge khi tạo các tài liệu này. Tiếp tục commit tài liệu trên cùng nhánh feature đang dùng rồi kiểm tra trước push.
