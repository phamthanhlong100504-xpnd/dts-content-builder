# API Blueprint — Delete Question Option

Tài liệu thiết kế API Blueprint cho nghiệp vụ Xóa Mềm Đáp án (Delete Question Option) trong dịch vụ `content-builder`.
Tuân thủ tiêu chuẩn kiến trúc `/api/{version}/{service}/{object}/` và đặc tả `rules/docx/java/api-blueprint-generator.md`.

---

## Part 0 — Classification & Identity

- **API Name**: Delete Question Option
- **API Type**: Internal / Public
- **Module**: `content-builder`
- **Feature**: Question Option Management
- **Description**: Thực hiện xóa mềm một lựa chọn/đáp án khỏi câu hỏi bằng cách gán `deleted_at = CURRENT_TIMESTAMP`. Nếu câu hỏi đang ở trạng thái `PUBLISHED` và việc xóa đáp án này khiến câu hỏi bị vi phạm quy tắc (VD: câu trắc nghiệm không còn đáp án đúng nào, hoặc dưới 2 đáp án), hệ thống sẽ ném lỗi ngăn chặn hoặc tự động chuyển trạng thái câu hỏi về `DRAFT`.
- **Related Tables**: `question_options`, `questions`
- **Related Services**: `user-service`

---

## Part 1 — API Contract

### Endpoint

- **HTTP Method**: `DELETE`
- **URL**: `/api/v1/content-builder/questions/{questionId}/options/{optionId}`
- **Content Type**: None

### Request

#### Path Variables
| Name | Type | Required | Description | Validation Rules |
|---|---|---|---|---|
| `questionId` | String (UUID) | Yes | Định danh câu hỏi cha | UUIDv4/UUIDv7 |
| `optionId` | String (UUID) | Yes | Định danh đáp án cần xóa | UUIDv4/UUIDv7 |

#### Headers
| Name | Type | Required | Description | Validation Rules |
|---|---|---|---|---|
| `Authorization` | String | Yes | Bearer JWT Token định danh người dùng | Chuẩn RFC 6750 Bearer Token, token hợp lệ, chưa hết hạn |
| `X-Request-ID` | String | No | Trace ID định danh luồng request (Tạm thời client hoặc backend tự sinh UUID do chưa có Gateway) | UUIDv4, tối đa 64 ký tự |

---

### Response

- **Success Status**: `204 No Content`

---

### Error Codes

| Error Code | HTTP Status | Business Meaning | Client Message |
|---|---|---|---|
| `AUTH-401` | 401 Unauthorized | JWT token không hợp lệ | Authentication required. |
| `AUTH-403` | 403 Forbidden | Không có quyền xóa đáp án | You do not have permission to delete this option. |
| `RES-404` | 404 Not Found | Đáp án không tồn tại hoặc đã bị xóa mềm | Option not found. |
| `VAL-422` | 422 Unprocessable Entity | Vi phạm quy tắc xuất bản của câu hỏi cha (VD: Xóa đáp án đúng duy nhất của câu hỏi đang `PUBLISHED`) | Cannot delete option: Published question would lack a correct answer. |
| `SYS-500` | 500 Internal Server Error | Lỗi hệ thống | An unexpected internal server error occurred. |

---

## Part 2 — Processing Specification

1. **Controller Layer**:
   - Nhận DELETE request tại `/api/v1/content-builder/questions/{questionId}/options/{optionId}`.
   - Validate UUIDs, trích xuất `userId`.
   - Gọi Service Layer: `deleteOption(questionId, optionId, userId)`.

2. **Service Layer**:
   - Truy vấn bản ghi QuestionOption theo `id` và `question_id` (`deleted_at IS NULL`). Nếu không thấy ném `RES-404`.
   - Kiểm tra quyền xóa của người dùng (`questions:delete` permission).
   - Kiểm tra câu hỏi cha: Nếu câu hỏi đang có `status == "PUBLISHED"`:
     - Kiểm tra nếu đáp án bị xóa là đáp án đúng duy nhất, hoặc làm cho số lượng đáp án còn lại < 2: Ném lỗi `VAL-422` yêu cầu chuyển câu hỏi về DRAFT trước khi xóa, hoặc tự động cảnh báo.
   - Khởi tạo giao dịch (@Transactional): Gán `option.deletedAt = CURRENT_TIMESTAMP`, `option.updatedBy = userId`.
   - Lưu xuống DB qua Repository Layer.
   - Evict cache `question:detail:{questionId}`.

3. **Repository Layer**:
   - Thực hiện UPDATE soft delete trên bảng `question_options`.

4. **External Interaction**:
   - None.

5. **Validation**:
   - Kiểm tra ràng buộc số lượng đáp án hợp lệ cho câu hỏi `PUBLISHED`.

---

## Part 3 — Data Interaction

- **Operation 1**:
  - **Operation Type**: `UPDATE`
  - **Target Table**: `question_options`
  - **Conditions**: `id = :optionId AND question_id = :questionId AND deleted_at IS NULL`
  - **Expected Result**: Gán `deleted_at = CURRENT_TIMESTAMP`, `updated_by = :userId`.

---

## Part 4 — Operational Notes

- **Idempotency**: Gọi DELETE nhiều lần cho cùng 1 đáp án đã bị soft delete sẽ trả về `404 Not Found`.
- **Cache Eviction**: Evict key `question:detail:{questionId}` khỏi Redis.

- **Tracing**: Truyền dẫn `traceId` qua MDC logging. Do chưa có API Gateway, backend sẽ tự sinh UUID nếu client không truyền header `X-Request-ID`.
