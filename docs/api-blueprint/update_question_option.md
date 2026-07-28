# API Blueprint — Update Question Option

Tài liệu thiết kế API Blueprint cho nghiệp vụ Cập nhật Đáp án (Update Question Option) trong dịch vụ `content-builder`.
Tuân thủ tiêu chuẩn kiến trúc `/api/{version}/{service}/{object}/` và đặc tả `rules/docx/java/api-blueprint-generator.md`.

---

## Part 0 — Classification & Identity

- **API Name**: Update Question Option
- **API Type**: Internal / Public
- **Module**: `content-builder`
- **Feature**: Question Option Management
- **Description**: Cập nhật thông tin chi tiết của một lựa chọn/đáp án trong câu hỏi (nội dung, cờ đúng/sai, thứ tự, trạng thái...).
- **Related Tables**: `question_options`, `questions`
- **Related Services**: `user-service`

---

## Part 1 — API Contract

### Endpoint

- **HTTP Method**: `PUT`
- **URL**: `/api/v1/content-builder/questions/{questionId}/options/{optionId}`
- **Content Type**: `application/json`

### Request

#### Path Variables
| Name | Type | Required | Description | Validation Rules |
|---|---|---|---|---|
| `questionId` | String (UUID) | Yes | Định danh câu hỏi cha | UUIDv4/UUIDv7 |
| `optionId` | String (UUID) | Yes | Định danh đáp án cần sửa | UUIDv4/UUIDv7 |

#### Headers
| Name | Type | Required | Description | Validation Rules |
|---|---|---|---|---|
| `Authorization` | String | Yes | Bearer JWT Token định danh người dùng | Chuẩn RFC 6750 Bearer Token, token hợp lệ, chưa hết hạn |
| `X-Request-ID` | String | No | Trace ID định danh luồng request (Tạm thời client hoặc backend tự sinh UUID do chưa có Gateway) | UUIDv4, tối đa 64 ký tự |

#### Request Body
| Name | Type | Required | Description | Validation Rules |
|---|---|---|---|---|
| `content` | String | Yes | Nội dung đáp án | Không được để trống, tối đa 10,000 ký tự |
| `sortOrder` | Integer | Yes | Thứ tự hiển thị | Số nguyên >= 0 |
| `isCorrect` | Boolean | Yes | Đánh dấu đúng/sai | `true` hoặc `false` |
| `status` | String | Yes | Trạng thái | `DRAFT`, `PUBLISHED`, `ARCHIVED`, `HIDDEN` |
| `metadata` | Object | No | Dữ liệu mở rộng | Chuẩn JSONB |

---

### Response

- **Success Status**: `200 OK`

#### Response Body
| Name | Type | Description |
|---|---|---|
| `id` | String (UUID) | Định danh duy nhất của đáp án |
| `questionId` | String (UUID) | ID câu hỏi cha |
| `content` | String | Nội dung đáp án sau cập nhật |
| `sortOrder` | Integer | Thứ tự hiển thị |
| `isCorrect` | Boolean | Cờ đúng/sai |
| `status` | String | Trạng thái |
| `metadata` | Object | Dữ liệu mở rộng |
| `updatedBy` | String (UUID) | ID người sửa |
| `updatedAt` | String (ISO-8601)| Thời điểm sửa |

---

### Error Codes

| Error Code | HTTP Status | Business Meaning | Client Message |
|---|---|---|---|
| `AUTH-401` | 401 Unauthorized | JWT token không hợp lệ | Authentication required. |
| `AUTH-403` | 403 Forbidden | Không có quyền sửa đáp án | You do not have permission to modify this option. |
| `RES-404` | 404 Not Found | Đáp án hoặc câu hỏi cha không tồn tại | Option not found in the specified question. |
| `VAL-400` | 400 Bad Request | Payload không hợp lệ | Invalid request payload. |
| `VAL-422` | 422 Unprocessable Entity | Vi phạm quy tắc nghiệp vụ (VD: Chuyển đáp án từ sai thành đúng `isCorrect=true` trong câu hỏi `SINGLE_CHOICE` đang có 1 đáp án đúng khác) | Cannot change option to correct: Single choice question already has another correct answer. |
| `SYS-500` | 500 Internal Server Error | Lỗi hệ thống | An unexpected internal server error occurred. |

---

## Part 2 — Processing Specification

1. **Controller Layer**:
   - Nhận PUT request tại `/api/v1/content-builder/questions/{questionId}/options/{optionId}`.
   - Validate UUID và payload, trích xuất `userId`.
   - Gọi Service Layer: `updateOption(questionId, optionId, request, userId)`.

2. **Service Layer**:
   - Truy vấn bản ghi QuestionOption theo `id = optionId` và `question_id = questionId` (`deleted_at IS NULL`). Nếu không thấy ném lỗi `RES-404`.
   - Kiểm tra quyền hạn của người dùng.
   - Kiểm tra nghiệp vụ: Nếu thay đổi `isCorrect` từ `false` sang `true` trên câu hỏi `SINGLE_CHOICE`, cần xác minh không có đáp án nào khác đang là `true`.
   - Cập nhật thông tin vào thực thể QuestionOption, gán `updatedBy = userId`.
   - Lưu xuống DB qua Repository Layer.
   - Evict cache `question:detail:{questionId}`.
   - Trả về DTO.

3. **Repository Layer**:
   - Thực hiện lệnh UPDATE trên bảng `question_options`.

4. **External Interaction**:
   - None.

5. **Validation**:
   - Kiểm tra tính nhất quán giữa `questionId` trong URL và `question_id` trong DB.

---

## Part 3 — Data Interaction

- **Operation 1**:
  - **Operation Type**: `SELECT`
  - **Target Table**: `question_options` (có thể JOIN `questions` để lấy type)
  - **Conditions**: `id = :optionId AND question_id = :questionId AND deleted_at IS NULL`
  - **Expected Result**: 1 dòng dữ liệu đáp án.
- **Operation 2**:
  - **Operation Type**: `UPDATE`
  - **Target Table**: `question_options`
  - **Conditions**: `id = :optionId`
  - **Expected Result**: Cập nhật nội dung, `is_correct`, `sort_order`, `status`, `updated_by`, `updated_at`.

---

## Part 4 — Operational Notes

- **Cache Eviction**: Xóa cache Redis của `question:detail:{questionId}`.

- **Tracing**: Truyền dẫn `traceId` qua MDC logging. Do chưa có API Gateway, backend sẽ tự sinh UUID nếu client không truyền header `X-Request-ID`.
