# Kế Hoạch Thiết Kế API Blueprint — Quản Lý Question & Question Option (Đã phê duyệt)

- **Thời gian phê duyệt**: 28-07-2026 07:06 (local time)
- **Mục tiêu**: Chuẩn hóa URL theo quy tắc `/api/{version}/{service}/{object}/` và sinh 10 tài liệu API Blueprint nhỏ, độc lập cho các nghiệp vụ quản lý Câu hỏi và Đáp án trong dịch vụ `content-builder`.

---

## 1. Cập Nhật Quy Tắc (`rules/global/02_api.md`)

- Cập nhật mục **Versioning** và **Resource Naming** trong file `rules/global/02_api.md` quy định định dạng đường dẫn bắt buộc:
  ```
  /api/{version}/{service}/{object}
  ```

---

## 2. Danh Sách 10 API Blueprint Độc Lập Sẽ Sinh

Các file tài liệu được tách nhỏ từng nghiệp vụ và lưu tại `docs/api-blueprint/`:

| STT | Nghiệp vụ | HTTP Method | REST Endpoint | File Blueprint tương ứng |
|---|---|---|---|---|
| 1 | **Tạo question Nháp** | `POST` | `/api/v1/content-builder/questions/draft` | `docs/api-blueprint/create_draft_question.md` |
| 2 | **Tạo question Xuất bản** | `POST` | `/api/v1/content-builder/questions/published` | `docs/api-blueprint/create_published_question.md` |
| 3 | **Lấy chi tiết question** | `GET` | `/api/v1/content-builder/questions/{id}` | `docs/api-blueprint/get_question_detail.md` |
| 4 | **Lấy danh sách question** | `GET` | `/api/v1/content-builder/questions` | `docs/api-blueprint/list_questions.md` |
| 5 | **Cập nhật question** | `PUT` / `PATCH`| `/api/v1/content-builder/questions/{id}` | `docs/api-blueprint/update_question.md` |
| 6 | **Xóa mềm question** | `DELETE` | `/api/v1/content-builder/questions/{id}` | `docs/api-blueprint/delete_question.md` |
| 7 | **Thêm question-option** | `POST` | `/api/v1/content-builder/questions/{questionId}/options` | `docs/api-blueprint/add_question_option.md` |
| 8 | **Sửa question-option** | `PUT` / `PATCH`| `/api/v1/content-builder/questions/{questionId}/options/{optionId}`| `docs/api-blueprint/update_question_option.md` |
| 9 | **Xóa option** | `DELETE` | `/api/v1/content-builder/questions/{questionId}/options/{optionId}`| `docs/api-blueprint/delete_question_option.md` |
| 10 | **Đổi thứ tự options** | `PUT` | `/api/v1/content-builder/questions/{questionId}/options/reorder` | `docs/api-blueprint/reorder_question_options.md` |

---

## 3. Tiêu Chuẩn Cấu Trúc Blueprint (Tuân thủ `api-blueprint-generator.md`)

Mỗi file trong 10 file trên phải có đủ 5 phần bắt buộc:
1. **Part 0 — Classification & Identity**: API Name, Type, Module, Feature, Description, Related Tables, Related Services.
2. **Part 1 — API Contract**: URL, Method, Request Table, Response Table, Error Codes Table.
3. **Part 2 — Processing Specification**: Các bước xử lý đánh số tuần tự (Controller -> Service -> Repository -> External -> Validation).
4. **Part 3 — Data Interaction**: Thao tác SQL trừu tượng (SELECT, INSERT, UPDATE, soft delete UPDATE).
5. **Part 4 — Operational Notes**: Idempotency, Audit Logging, Tracing (`traceId`), Tenant Isolation, Cache Eviction/Query.
