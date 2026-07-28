# API Blueprint — Add Question Option

Tài liệu thiết kế API Blueprint cho nghiệp vụ Thêm Đáp án vào Câu hỏi (Add Question Option) trong dịch vụ `content-builder`.
Tuân thủ tiêu chuẩn kiến trúc `/api/{version}/{service}/{object}/` và đặc tả `rules/docx/java/api-blueprint-generator.md`.

---

## Part 0 — Classification & Identity

- **API Name**: Add Question Option
- **API Type**: Internal / Public
- **Module**: `content-builder`
- **Feature**: Question Option Management
- **Description**: Thêm một lựa chọn/đáp án mới vào một câu hỏi đã tồn tại. Nếu không truyền `sortOrder`, hệ thống tự động tính toán gán vào vị trí cuối cùng trong danh sách đáp án hiện tại.
- **Related Tables**: `questions`, `question_options`
- **Related Services**: `user-service`

---

## Part 1 — API Contract

### Endpoint

- **HTTP Method**: `POST`
- **URL**: `/api/v1/content-builder/questions/{questionId}/options`
- **Content Type**: `application/json`

### Request

#### Path Variables
| Name | Type | Required | Description | Validation Rules |
|---|---|---|---|---|
| `questionId` | String (UUID) | Yes | Định danh câu hỏi cha | UUIDv4/UUIDv7 |

#### Query Parameters
- None

#### Headers
| Name | Type | Required | Description | Validation Rules |
|---|---|---|---|---|
| `Authorization` | String | Yes | Bearer JWT Token định danh người dùng | Chuẩn RFC 6750 Bearer Token, token hợp lệ, chưa hết hạn |
| `X-Request-ID` | String | No | Trace ID định danh luồng request (Tạm thời client hoặc backend tự sinh UUID do chưa có Gateway) | UUIDv4, tối đa 64 ký tự |

#### Request Body
| Name | Type | Required | Description | Validation Rules |
|---|---|---|---|---|
| `content` | String | Yes | Nội dung chi tiết của lựa chọn đáp án | Không được để trống, tối đa 10,000 ký tự |
| `sortOrder` | Integer | No | Thứ tự hiển thị trong câu hỏi | Số nguyên >= 0. Nếu bỏ trống, tự động gán bằng `MAX(sort_order) + 1` |
| `isCorrect` | Boolean | Yes | Đánh dấu đây là đáp án đúng | `true` hoặc `false` |
| `status` | String | No | Trạng thái của đáp án | `DRAFT`, `PUBLISHED`, `ARCHIVED`, `HIDDEN`. Mặc định: `DRAFT` |
| `metadata` | Object | No | Dữ liệu mở rộng | Chuẩn JSONB hợp lệ, mặc định `{}` |

---

### Response

- **Success Status**: `201 Created`

#### Response Body
| Name | Type | Description |
|---|---|---|
| `id` | String (UUID) | Định danh duy nhất của đáp án vừa tạo |
| `questionId` | String (UUID) | ID câu hỏi cha |
| `content` | String | Nội dung đáp án |
| `sortOrder` | Integer | Thứ tự hiển thị thực tế đã lưu |
| `isCorrect` | Boolean | Cờ đúng/sai |
| `status` | String | Trạng thái |
| `metadata` | Object | Dữ liệu mở rộng |
| `createdBy` | String (UUID) | ID người tạo |
| `createdAt` | String (ISO-8601)| Thời điểm tạo |

---

### Error Codes

| Error Code | HTTP Status | Business Meaning | Client Message |
|---|---|---|---|
| `AUTH-401` | 401 Unauthorized | JWT token không hợp lệ | Authentication required. |
| `AUTH-403` | 403 Forbidden | Không có quyền thêm đáp án | You do not have permission to modify this question. |
| `RES-404` | 404 Not Found | Câu hỏi cha không tồn tại hoặc đã bị xóa mềm | Parent question not found. |
| `VAL-400` | 400 Bad Request | Payload không hợp lệ | Invalid request payload. |
| `VAL-422` | 422 Unprocessable Entity | Vi phạm quy tắc nghiệp vụ (VD: Thêm đáp án đúng `isCorrect=true` vào câu hỏi `SINGLE_CHOICE` mà trước đó đã có 1 đáp án đúng khác đang tồn tại) | Cannot add correct option: Single choice question already has a correct answer. |
| `SYS-500` | 500 Internal Server Error | Lỗi hệ thống | An unexpected internal server error occurred. |

---

## Part 2 — Processing Specification

1. **Controller Layer**:
   - Nhận POST request tại `/api/v1/content-builder/questions/{questionId}/options`.
   - Validate Path Variable `questionId` và Request Body.
   - Trích xuất `userId`, gọi Service Layer: `addOption(questionId, request, userId)`.

2. **Service Layer**:
   - Truy vấn kiểm tra sự tồn tại của Question theo `questionId` (`deleted_at IS NULL`). Nếu không thấy ném lỗi `RES-404`.
   - Kiểm tra quyền chỉnh sửa câu hỏi này.
   - Kiểm tra quy tắc nghiệp vụ:
     - Nếu `isCorrect == true` và `question.type == "SINGLE_CHOICE"`: Đếm số đáp án đúng hiện có trong DB của câu hỏi này (`is_correct = true AND deleted_at IS NULL`). Nếu `count >= 1`, ném lỗi vi phạm nghiệp vụ (`VAL-422`) hoặc yêu cầu client sửa đáp án cũ trước.
   - Nếu `sortOrder` là null: Thực hiện câu lệnh SELECT MAX(sort_order) từ `question_options` theo `question_id`, gán `sortOrder = max + 1` (mặc định 0 nếu chưa có đáp án nào).
   - Khởi tạo thực thể QuestionOption mới, gán `id = gen_random_uuid()`, `questionId = questionId`, `createdBy = userId`, `createdAt = CURRENT_TIMESTAMP`.
   - Lưu xuống DB qua Repository Layer.
   - Evict cache chi tiết câu hỏi (`question:detail:{questionId}`) khỏi Redis.
   - Trả về Response DTO.

3. **Repository Layer**:
   - Thực hiện lệnh SELECT kiểm tra câu hỏi cha.
   - Thực hiện lệnh SELECT MAX(sort_order).
   - Thực hiện lệnh INSERT vào bảng `question_options`.

4. **External Interaction**:
   - None.

5. **Validation**:
   - Kiểm tra quy tắc số lượng đáp án đúng tối đa theo `question.type`.

---

## Part 3 — Data Interaction

- **Operation 1**:
  - **Operation Type**: `SELECT`
  - **Target Table**: `questions`
  - **Conditions**: `id = :questionId AND deleted_at IS NULL`
  - **Expected Result**: 1 dòng dữ liệu câu hỏi cha.
- **Operation 2**:
  - **Operation Type**: `SELECT`
  - **Target Table**: `question_options`
  - **Conditions**: `question_id = :questionId AND deleted_at IS NULL` (Để tính max sort_order hoặc kiểm tra is_correct)
  - **Expected Result**: Giá trị max sort_order hoặc số lượng đáp án đúng.
- **Operation 3**:
  - **Operation Type**: `INSERT`
  - **Target Table**: `question_options`
  - **Conditions**: None
  - **Expected Result**: Tạo 1 bản ghi đáp án mới.

---

## Part 4 — Operational Notes

- **Cache Eviction**: Xóa cache của key `question:detail:{questionId}` để lần tra cứu câu hỏi tiếp theo có phản ánh đúng danh sách đáp án mới.
- **Audit Logging**: Ghi log thêm đáp án.

- **Tracing**: Truyền dẫn `traceId` qua MDC logging. Do chưa có API Gateway, backend sẽ tự sinh UUID nếu client không truyền header `X-Request-ID`.
