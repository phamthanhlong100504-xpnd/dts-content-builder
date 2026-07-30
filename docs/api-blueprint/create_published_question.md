# API Blueprint — Create Published Question

Tài liệu thiết kế API Blueprint cho nghiệp vụ Tạo Câu hỏi Xuất bản (Create Published Question) trong dịch vụ `content-builder`.
Tuân thủ tiêu chuẩn kiến trúc `/api/{version}/{service}/{object}/` và đặc tả `rules/docx/java/api-blueprint-generator.md`.

---

## Part 0 — Classification & Identity

- **API Name**: Create Published Question
- **API Type**: Internal / Public
- **Module**: `content-builder`
- **Feature**: Question Management
- **Description**: Tạo mới bản ghi câu hỏi với trạng thái lập tức chính thức là `PUBLISHED`. Yêu cầu kiểm tra nghiệp vụ khắt khe hơn (phải có ít nhất 2 đáp án đối với câu hỏi trắc nghiệm và ít nhất 1 đáp án đúng) trong cùng một giao dịch.
- **Related Tables**: `questions`, `question_options`
- **Related Services**: `user-service`, `media-service`

---

## Part 1 — API Contract

### Endpoint

- **HTTP Method**: `POST`
- **URL**: `/api/v1/content-builder/questions/published`
- **Content Type**: `application/json`

### Request

#### Path Variables
- None

#### Query Parameters
- None

#### Headers
| Name | Type | Required | Description | Validation Rules |
|---|---|---|---|---|
| `Authorization` | String | Yes | Bearer JWT Token định danh người dùng | Chuẩn RFC 6750 Bearer Token, token hợp lệ |
| `X-Request-ID` | String | No | Trace ID định danh luồng request (Tạm thời client hoặc backend tự sinh UUID do chưa có Gateway) | UUIDv4, tối đa 64 ký tự |

#### Request Body
| Name | Type | Required | Description | Validation Rules |
|---|---|---|---|---|
| `type` | String | Yes | Loại câu hỏi | Phải thuộc danh sách: `SINGLE_CHOICE`, `MULTIPLE_CHOICE`, `TRUE_FALSE`, `FILL_BLANK`, `ORDERING`, `MATCHING` |
| `content` | String | Yes | Nội dung câu hỏi | Không được để trống, độ dài từ 1 đến 50,000 ký tự |
| `explanations` | Object/Array | No | Lời giải thích | Chuẩn JSONB hợp lệ |
| `mediaFileIds` | Array<String> | No | Danh sách UUID của các file media | Mảng string UUIDv4, tối đa 20 phần tử |
| `attachments` | Object/Array | No | File đính kèm | Chuẩn JSONB hợp lệ |
| `references` | Object/Array | No | Nguồn tham khảo | Chuẩn JSONB hợp lệ |
| `metadata` | Object | No | Dữ liệu mở rộng | Chuẩn JSONB hợp lệ, mặc định `{}` |
| `options` | Array<Object> | Yes | Danh sách các đáp án khởi tạo kèm theo | BẮT BUỘC có tối thiểu 2 lựa chọn đáp án hợp lệ |

**Cấu trúc từng đối tượng trong mảng `options` (Option Item):**
| Name | Type | Required | Description | Validation Rules |
|---|---|---|---|---|
| `content` | String | Yes | Nội dung chi tiết đáp án | Không được để trống, tối đa 10,000 ký tự |
| `sortOrder` | Integer | No | Thứ tự hiển thị | Số nguyên >= 0 |
| `isCorrect` | Boolean | Yes | Đánh dấu đúng/sai | `true` hoặc `false` |
| `metadata` | Object | No | Dữ liệu mở rộng | Chuẩn JSONB hợp lệ |

---

### Response

- **Success Status**: `201 Created`

#### Response Body
| Name | Type | Description |
|---|---|---|
| `id` | String (UUID) | Định danh duy nhất của câu hỏi vừa tạo |
| `type` | String | Loại câu hỏi |
| `content` | String | Nội dung câu hỏi |
| `explanations` | Object/Array | Lời giải thích |
| `mediaFileIds` | Array<String> | Danh sách UUID media |
| `attachments` | Object/Array | Danh sách file đính kèm |
| `references` | Object/Array | Danh sách tham khảo |
| `status` | String | Trạng thái (Luôn trả về `PUBLISHED`) |
| `metadata` | Object | Dữ liệu mở rộng |
| `createdBy` | String (UUID) | ID người tạo |
| `createdAt` | String (ISO-8601)| Thời điểm tạo |
| `options` | Array<Object> | Danh sách chi tiết các đáp án (`status="PUBLISHED"`) |

---

### Error Codes

| Error Code | HTTP Status | Business Meaning | Client Message |
|---|---|---|---|
| `AUTH-401` | 401 Unauthorized | Thiếu hoặc JWT token không hợp lệ | Authentication required or token invalid. |
| `AUTH-403` | 403 Forbidden | Người dùng không có quyền xuất bản câu hỏi | You do not have permission to publish questions. |
| `VAL-400` | 400 Bad Request | Payload vi phạm định dạng | Invalid request payload. |
| `VAL-422` | 422 Unprocessable Entity | Vi phạm quy tắc xuất bản (VD: Câu hỏi `SINGLE_CHOICE` hoặc `MULTIPLE_CHOICE` nhưng mảng `options` có dưới 2 đáp án, hoặc không có đáp án nào `isCorrect=true`) | Cannot publish question without valid options and at least one correct answer. |
| `SYS-500` | 500 Internal Server Error | Lỗi hệ thống nội bộ | An unexpected internal server error occurred. |

---

## Part 2 — Processing Specification

1. **Controller Layer**:
   - Nhận HTTP Request POST tại endpoint `/api/v1/content-builder/questions/published`.
   - Validate payload đầu vào.
   - Trích xuất `userId` và gọi Service Layer phương thức `createQuestion(request, userId, "PUBLISHED")`.

2. **Service Layer**:
   - Kiểm tra quyền hạn xuất bản nội dung (`questions:update` permission).
   - **Strict Business Validation cho trạng thái PUBLISHED**:
     - Đối với `SINGLE_CHOICE`, `MULTIPLE_CHOICE`: Mảng `options` phải có tối thiểu 2 phần tử. Phải có ít nhất 1 đáp án đúng (`isCorrect = true`). Riêng `SINGLE_CHOICE` chỉ được chính xác 1 đáp án đúng.
     - Đối với `TRUE_FALSE`: Phải có chính xác 2 đáp án (Đúng và Sai), trong đó có 1 đáp án đúng.
   - Khởi tạo giao dịch (@Transactional), lưu bảng `questions` với `status = "PUBLISHED"`.
   - Batch insert các bản ghi `question_options` với `status = "PUBLISHED"`.
   - Commit giao dịch và trả về Response DTO.

3. **Repository Layer**:
   - Thực hiện INSERT vào `questions` và `question_options`.

4. **External Interaction**:
   - Gọi đồng bộ hoặc bất đồng bộ tới `media-service` để xác minh tất cả `mediaFileIds` đều tồn tại và ở trạng thái hợp lệ.

5. **Validation**:
   - Yêu cầu kiểm tra tính đầy đủ của đáp án khắt khe hơn bản DRAFT.

---

## Part 3 — Data Interaction

- **Operation 1**:
  - **Operation Type**: `INSERT`
  - **Target Table**: `questions`
  - **Conditions**: None
  - **Expected Result**: Tạo 1 bản ghi mới với `status = 'PUBLISHED'`.
- **Operation 2**:
  - **Operation Type**: `INSERT`
  - **Target Table**: `question_options`
  - **Conditions**: None
  - **Expected Result**: Tạo $N$ bản ghi đáp án với `status = 'PUBLISHED'`.

---

## Part 4 — Operational Notes

- **Audit Logging**: Ghi audit log quan trọng về việc xuất bản câu hỏi trực tiếp lên hệ thống.
- **Metrics**: Metric `content_builder.question.created` với tag `status=published`.
- **Tracing**: Truyền dẫn `traceId` qua MDC logging. Do chưa có API Gateway, backend sẽ tự sinh UUID nếu client không truyền header `X-Request-ID`.
