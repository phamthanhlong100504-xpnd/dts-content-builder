# API Blueprint — Soft Delete Question

Tài liệu thiết kế API Blueprint cho nghiệp vụ Xóa Mềm Câu hỏi (Soft Delete Question) trong dịch vụ `content-builder`.
Tuân thủ tiêu chuẩn kiến trúc `/api/{version}/{service}/{object}/` và đặc tả `rules/docx/java/api-blueprint-generator.md`.

---

## Part 0 — Classification & Identity

- **API Name**: Soft Delete Question
- **API Type**: Internal / Public
- **Module**: `content-builder`
- **Feature**: Question Management
- **Description**: Thực hiện xóa mềm một câu hỏi khỏi hệ thống bằng cách gán `deleted_at = CURRENT_TIMESTAMP`. **Quy tắc nghiệp vụ Cascade**: Tự động tiến hành xóa mềm toàn bộ danh sách lựa chọn/đáp án (`question_options`) trực thuộc câu hỏi này trong cùng một giao dịch.
- **Related Tables**: `questions`, `question_options`, `question_blocks`
- **Related Services**: `user-service`

---

## Part 1 — API Contract

### Endpoint

- **HTTP Method**: `DELETE`
- **URL**: `/api/v1/content-builder/questions/{id}`
- **Content Type**: None

### Request

#### Path Variables
| Name | Type | Required | Description | Validation Rules |
|---|---|---|---|---|
| `id` | String (UUID) | Yes | Định danh câu hỏi cần xóa | UUIDv4/UUIDv7 |

#### Headers
| Name | Type | Required | Description | Validation Rules |
|---|---|---|---|---|
| `Authorization` | String | Yes | Bearer JWT Token định danh người dùng | Chuẩn RFC 6750 Bearer Token, token hợp lệ, chưa hết hạn |
| `X-Request-ID` | String | No | Trace ID định danh luồng request (Tạm thời client hoặc backend tự sinh UUID do chưa có Gateway) | UUIDv4, tối đa 64 ký tự |

---

### Response

- **Success Status**: `204 No Content` (hoặc `200 OK` kèm thông báo thành công)
- **Response Body**: Empty (đối với 204) hoặc Object JSON xác nhận xóa thành công.

---

### Error Codes

| Error Code | HTTP Status | Business Meaning | Client Message |
|---|---|---|---|
| `AUTH-401` | 401 Unauthorized | JWT token không hợp lệ | Authentication required. |
| `AUTH-403` | 403 Forbidden | Không có quyền xóa câu hỏi này | You do not have permission to delete this question. |
| `RES-404` | 404 Not Found | Câu hỏi không tồn tại hoặc đã bị xóa mềm từ trước | Question not found. |
| `VAL-409` | 409 Conflict | Câu hỏi đang được khóa hoặc sử dụng trong đề thi chính thức không cho phép xóa | Question cannot be deleted because it is being used in an active exam session. |
| `SYS-500` | 500 Internal Server Error | Lỗi hệ thống | An unexpected internal server error occurred. |

---

## Part 2 — Processing Specification

1. **Controller Layer**:
   - Nhận DELETE request tại `/api/v1/content-builder/questions/{id}`.
   - Validate UUID `id`, trích xuất `userId`.
   - Gọi Service Layer: `deleteQuestion(id, userId)`.

2. **Service Layer**:
   - Truy vấn bản ghi Question theo `id` (`deleted_at IS NULL`). Nếu không thấy ném lỗi `RES-404`.
   - Kiểm tra quyền xóa (`questions:delete` permission hoặc chủ sở hữu).
   - Kiểm tra trạng thái: Nếu `status == PUBLISHED`, ném lỗi `VAL-409` (Không được phép xóa câu hỏi đã xuất bản, phải dùng chức năng Deprecate).
   - Kiểm tra ràng buộc cục bộ: Kiểm tra bảng `question_blocks` (`existsByQuestionIdAndDeletedAtIsNull`). Nếu câu hỏi đang nằm trong một chương học, ném lỗi xung đột `VAL-409`.
   - Khởi tạo giao dịch (@Transactional):
     - Gán `question.deletedAt = CURRENT_TIMESTAMP`, `question.updatedBy = userId`.
     - Thực hiện câu lệnh cập nhật batch trên bảng `question_options`: Gán `deleted_at = CURRENT_TIMESTAMP`, `updated_by = userId` cho tất cả bản ghi có `question_id = id` và `deleted_at IS NULL`.
   - Commit giao dịch.
   - Evict cache liên quan.

3. **Repository Layer**:
   - Thực hiện lệnh UPDATE soft delete trên `questions`.
   - Thực hiện lệnh UPDATE soft delete trên `question_options`.

4. **External Interaction**:
   - Gửi sự kiện `QuestionDeletedEvent` lên Kafka (nếu hệ thống sử dụng event-driven để thống kê số lượng hoặc dọn dẹp tài nguyên bên ngoài).

5. **Validation**:
   - Kiểm tra tính hợp lệ của ID và trạng thái khóa của câu hỏi.

---

## Part 3 — Data Interaction

- **Operation 1**:
  - **Operation Type**: `UPDATE`
  - **Target Table**: `questions`
  - **Conditions**: `id = :id AND deleted_at IS NULL`
  - **Expected Result**: Gán `deleted_at = CURRENT_TIMESTAMP`, `updated_by = :userId`.
- **Operation 2**:
  - **Operation Type**: `UPDATE`
  - **Target Table**: `question_options`
  - **Conditions**: `question_id = :id AND deleted_at IS NULL`
  - **Expected Result**: Gán `deleted_at = CURRENT_TIMESTAMP`, `updated_by = :userId` cho toàn bộ options con.

---

## Part 4 — Operational Notes

- **Idempotency**: Việc gọi nhiều lần API soft delete với cùng 1 ID sau lần thành công đầu tiên sẽ trả về `404 Not Found` (do `deleted_at` đã có giá trị).
- **Audit Logging**: Ghi log sự kiện xóa mềm câu hỏi kèm danh sách số lượng options đã bị xóa theo.
- **Cache Eviction**: Evict key `question:detail:{id}` khỏi Redis.

- **Tracing**: Truyền dẫn `traceId` qua MDC logging. Do chưa có API Gateway, backend sẽ tự sinh UUID nếu client không truyền header `X-Request-ID`.
