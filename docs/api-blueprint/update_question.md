# API Blueprint — Update Question

Tài liệu thiết kế API Blueprint cho nghiệp vụ Cập nhật Câu hỏi (Update Question) trong dịch vụ `content-builder`.
Tuân thủ tiêu chuẩn kiến trúc `/api/{version}/{service}/{object}/` và đặc tả `rules/docx/java/api-blueprint-generator.md`.

---

## Part 0 — Classification & Identity

- **API Name**: Update Question
- **API Type**: Internal / Public
- **Module**: `content-builder`
- **Feature**: Question Management
- **Description**: Cập nhật thông tin của một câu hỏi đã tồn tại. Chỉ cập nhật thông tin riêng của câu hỏi (nội dung, metadata, trạng thái, media...). Việc cập nhật danh sách đáp án được thực hiện qua các API chuyên biệt của Question Option.
- **Related Tables**: `questions`
- **Related Services**: `user-service`, `media-service`

---

## Part 1 — API Contract

### Endpoint

- **HTTP Method**: `PUT`
- **URL**: `/api/v1/content-builder/questions/{id}`
- **Content Type**: `application/json`

### Request

#### Path Variables
| Name | Type | Required | Description | Validation Rules |
|---|---|---|---|---|
| `id` | String (UUID) | Yes | Định danh câu hỏi cần sửa | UUIDv4/UUIDv7 |

#### Headers
| Name | Type | Required | Description | Validation Rules |
|---|---|---|---|---|
| `Authorization` | String | Yes | Bearer JWT Token định danh người dùng | Chuẩn RFC 6750 Bearer Token, token hợp lệ, chưa hết hạn |
| `X-Request-ID` | String | No | Trace ID định danh luồng request (Tạm thời client hoặc backend tự sinh UUID do chưa có Gateway) | UUIDv4, tối đa 64 ký tự |

#### Request Body
| Name | Type | Required | Description | Validation Rules |
|---|---|---|---|---|
| `type` | String | Yes | Loại câu hỏi | `SINGLE_CHOICE`, `MULTIPLE_CHOICE`,... |
| `content` | String | Yes | Nội dung câu hỏi | 1 đến 50,000 ký tự |
| `explanations` | Object/Array | No | Lời giải thích | Chuẩn JSONB |
| `mediaFileIds` | Array<String> | No | Danh sách UUID media | Chuẩn UUID |
| `attachments` | Object/Array | No | File đính kèm | Chuẩn JSONB |
| `references` | Object/Array | No | Tài liệu tham khảo | Chuẩn JSONB |
| `status` | String | Yes | Trạng thái mới | `DRAFT`, `PUBLISHED`, `ARCHIVED`, `HIDDEN` |
| `metadata` | Object | No | Dữ liệu mở rộng | Chuẩn JSONB |

---

### Response

- **Success Status**: `200 OK`

#### Response Body
- Trả về chi tiết đối tượng Question sau khi cập nhật thành công (giống Response Body của API Get Question Detail).

---

### Error Codes

| Error Code | HTTP Status | Business Meaning | Client Message |
|---|---|---|---|
| `AUTH-401` | 401 Unauthorized | JWT token không hợp lệ | Authentication required. |
| `AUTH-403` | 403 Forbidden | Không có quyền cập nhật câu hỏi này | You do not have permission to update this question. |
| `RES-404` | 404 Not Found | Câu hỏi không tồn tại hoặc đã bị xóa mềm | Question not found with ID. |
| `VAL-400` | 400 Bad Request | Payload không hợp lệ | Invalid request payload. |
| `VAL-422` | 422 Unprocessable Entity | Vi phạm quy tắc nghiệp vụ khi chuyển sang `PUBLISHED` (VD: chưa có đủ đáp án hợp lệ trong DB) | Cannot change status to PUBLISHED: Question lacks valid options. |
| `SYS-500` | 500 Internal Server Error | Lỗi hệ thống | An unexpected internal server error occurred. |

---

## Part 2 — Processing Specification

1. **Controller Layer**:
   - Nhận PUT request tại `/api/v1/content-builder/questions/{id}`.
   - Validate payload đầu vào, trích xuất `userId`.
   - Gọi Service Layer: `updateQuestion(id, request, userId)`.

2. **Service Layer**:
   - Truy vấn bản ghi Question từ DB theo `id` (`deleted_at IS NULL`). Nếu không thấy ném lỗi `RES-404`.
   - Kiểm tra quyền: Người dùng phải là người tạo (`createdBy == userId`) hoặc có role Admin/Editor (`questions:update` permission).
   - Nếu `request.status == "PUBLISHED"` và trạng thái hiện tại là `DRAFT`:
     - Kiểm tra trong bảng `question_options` xem câu hỏi này đã có đủ số lượng đáp án tối thiểu và có đáp án đúng (`isCorrect = true`) hay chưa. Nếu chưa đủ, ném ngoại lệ vi phạm nghiệp vụ (`VAL-422`).
   - Cập nhật các trường thông tin từ request vào thực thể Question.
   - Gán `updatedBy = userId` (trường `updatedAt` được tự động cập nhật bởi Trigger DB và JPA `@LastModifiedDate`).
   - Lưu xuống DB qua Repository Layer.
   - Evict cache liên quan (nếu có) và trả về DTO.

3. **Repository Layer**:
   - Thực hiện lệnh UPDATE trên bảng `questions`.

4. **External Interaction**:
   - None.

5. **Validation**:
   - Kiểm tra các ràng buộc chuyển trạng thái hợp lệ.

---

## Part 3 — Data Interaction

- **Operation 1**:
  - **Operation Type**: `SELECT`
  - **Target Table**: `questions`
  - **Conditions**: `id = :id AND deleted_at IS NULL`
  - **Expected Result**: 1 dòng dữ liệu.
- **Operation 2**:
  - **Operation Type**: `UPDATE`
  - **Target Table**: `questions`
  - **Conditions**: `id = :id`
  - **Expected Result**: Cập nhật các cột nội dung, `status`, `updated_by`, `updated_at`.

---

## Part 4 — Operational Notes

- **Audit Logging**: Ghi log sự kiện cập nhật câu hỏi.
- **Cache Eviction**: Xóa cache Redis của key `question:detail:{id}`.

- **Tracing**: Truyền dẫn `traceId` qua MDC logging. Do chưa có API Gateway, backend sẽ tự sinh UUID nếu client không truyền header `X-Request-ID`.
