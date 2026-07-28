# API Blueprint — Create Draft Question

Tài liệu thiết kế API Blueprint cho nghiệp vụ Tạo Câu hỏi Nháp (Create Draft Question) trong dịch vụ `content-builder`.
Tuân thủ tiêu chuẩn kiến trúc `/api/{version}/{service}/{object}/` và đặc tả `rules/docx/java/api-blueprint-generator.md`.

---

## Part 0 — Classification & Identity

- **API Name**: Create Draft Question
- **API Type**: Internal / Public
- **Module**: `content-builder`
- **Feature**: Question Management
- **Description**: Tạo mới bản ghi câu hỏi vào hệ thống với trạng thái mặc định là `DRAFT`. Hỗ trợ truyền kèm danh sách lựa chọn (`options`) trong Request Body để khởi tạo đồng thời câu hỏi và đáp án trong cùng một giao dịch (transaction).
- **Related Tables**: `questions`, `question_options`
- **Related Services**: `user-service` (xác thực người tạo), `media-service` (tham chiếu ID tệp đa phương tiện)

---

## Part 1 — API Contract

### Endpoint

- **HTTP Method**: `POST`
- **URL**: `/api/v1/content-builder/questions/draft`
- **Content Type**: `application/json`

### Request

#### Path Variables
- None

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
| `type` | String | Yes | Loại câu hỏi | Phải thuộc danh sách: `SINGLE_CHOICE`, `MULTIPLE_CHOICE`, `TRUE_FALSE`, `FILL_BLANK`, `ORDERING`, `MATCHING` |
| `content` | String | Yes | Nội dung câu hỏi (Văn bản thuần hoặc Markdown/HTML) | Không được để trống, độ dài từ 1 đến 50,000 ký tự |
| `explanations` | Object/Array | No | Cấu hình lời giải thích nhúng dạng JSON | Chuẩn JSONB hợp lệ |
| `mediaFileIds` | Array<String> | No | Danh sách UUID của các file media từ Media Service | Mảng các string chuẩn định dạng UUIDv4, tối đa 20 phần tử |
| `attachments` | Object/Array | No | Cấu hình file đính kèm dạng JSON | Chuẩn JSONB hợp lệ |
| `references` | Object/Array | No | Cấu hình nguồn tham khảo dạng JSON | Chuẩn JSONB hợp lệ |
| `metadata` | Object | No | Dữ liệu mở rộng (độ khó, từ khóa, tag,...) | Chuẩn JSONB hợp lệ, mặc định `{}` |
| `options` | Array<Object> | No | Danh sách các đáp án khởi tạo kèm theo | Mảng tối đa 50 phần tử. Nếu có truyền, tuân thủ validation rule của Option Item bên dưới |

**Cấu trúc từng đối tượng trong mảng `options` (Option Item):**
| Name | Type | Required | Description | Validation Rules |
|---|---|---|---|---|
| `content` | String | Yes | Nội dung chi tiết của lựa chọn đáp án | Không được để trống, tối đa 10,000 ký tự |
| `sortOrder` | Integer | No | Thứ tự hiển thị của đáp án | Số nguyên >= 0, mặc định bằng chỉ số index trong mảng |
| `isCorrect` | Boolean | Yes | Đánh dấu đây là đáp án đúng | `true` hoặc `false` |
| `metadata` | Object | No | Dữ liệu mở rộng riêng cho đáp án | Chuẩn JSONB hợp lệ, mặc định `{}` |

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
| `mediaFileIds` | Array<String> | Danh sách UUID media đính kèm |
| `attachments` | Object/Array | Danh sách file đính kèm |
| `references` | Object/Array | Danh sách tham khảo |
| `status` | String | Trạng thái (Luôn trả về `DRAFT`) |
| `metadata` | Object | Dữ liệu mở rộng |
| `createdBy` | String (UUID) | ID người tạo |
| `createdAt` | String (ISO-8601)| Thời điểm tạo bản ghi (UTC) |
| `options` | Array<Object> | Danh sách chi tiết các đáp án vừa được tạo kèm (đầy đủ `id`, `sortOrder`, `isCorrect`, `status="DRAFT"`) |

---

### Error Codes

| Error Code | HTTP Status | Business Meaning | Client Message |
|---|---|---|---|
| `AUTH-401` | 401 Unauthorized | Thiếu hoặc JWT token không hợp lệ | Authentication required or token invalid. |
| `AUTH-403` | 403 Forbidden | Người dùng không có quyền tạo câu hỏi | You do not have permission to create questions. |
| `VAL-400` | 400 Bad Request | Payload gửi lên vi phạm định dạng hoặc thiếu trường bắt buộc | Invalid request payload or missing required fields. |
| `VAL-422` | 422 Unprocessable Entity | Vi phạm quy tắc nghiệp vụ (VD: câu hỏi `SINGLE_CHOICE` nhưng mảng `options` có > 1 đáp án `isCorrect=true`, hoặc không có đáp án đúng nào) | Business validation failed for question options structure. |
| `SYS-500` | 500 Internal Server Error | Lỗi hệ thống nội bộ hoặc lỗi thao tác cơ sở dữ liệu | An unexpected internal server error occurred. |

---

## Part 2 — Processing Specification

1. **Controller Layer**:
   - Nhận HTTP Request POST tại endpoint `/api/v1/content-builder/questions/draft`.
   - Thực hiện kiểm tra định dạng dữ liệu đầu vào (Input Validation) đối với Request Body dựa trên annotations (`@NotNull`, `@NotBlank`, `@Size`, `@Valid`).
   - Trích xuất thông tin người dùng (`userId`) từ Security Context / JWT Token.
   - Gọi xuống Service Layer phương thức `createQuestion(request, userId, "DRAFT")`.

2. **Service Layer**:
   - Kiểm tra quyền hạn (Permission Validation): Xác minh `userId` có quyền tạo nội dung tài liệu.
   - Kiểm tra quy tắc nghiệp vụ (Business Validation):
     - Nếu loại câu hỏi là `SINGLE_CHOICE` và mảng `options` được cung cấp, kiểm tra số lượng đáp án có `isCorrect = true` không được vượt quá 1.
     - Nếu loại câu hỏi là `TRUE_FALSE` và có mảng `options`, số lượng phần tử tối đa là 2.
   - Khởi tạo giao dịch cơ sở dữ liệu (`@Transactional`).
   - Tạo thực thể Question mới, gán `id = UUIDv7/gen_random_uuid()`, `status = "DRAFT"`, `createdBy = userId`, `createdAt = CURRENT_TIMESTAMP`.
   - Thực hiện lưu Question xuống DB qua Repository Layer.
   - Nếu mảng `options` không rỗng:
     - Lặp qua từng Option Item, khởi tạo thực thể QuestionOption tương ứng, gán `questionId = question.id`, `status = "DRAFT"`, `createdBy = userId`.
     - Lưu danh sách QuestionOption xuống DB qua Repository Layer.
   - Cam kết giao dịch (Commit Transaction).
   - Chuyển đổi thực thể sang đối tượng Response DTO và trả về cho Controller.

3. **Repository Layer**:
   - Thực hiện lệnh INSERT vào bảng `questions`.
   - Thực hiện lệnh INSERT batch vào bảng `question_options` (nếu có options).

4. **External Interaction**:
   - None (Kiểm tra sự tồn tại của `mediaFileIds` tại Media Service có thể thực hiện bất đồng bộ hoặc bỏ qua ở phase DRAFT).

5. **Validation**:
   - **Request Validation**: Kiểm tra các trường bắt buộc (`type`, `content`), định dạng UUID của `mediaFileIds`.
   - **Business Validation**: Kiểm tra tính logic của danh sách đáp án `options` ứng với từng `type`.
   - **Permission Validation**: Kiểm tra role/permission của user.

---

## Part 3 — Data Interaction

- **Operation 1**:
  - **Operation Type**: `INSERT`
  - **Target Table**: `questions`
  - **Conditions**: None
  - **Expected Result**: Tạo 1 bản ghi mới với `status = 'DRAFT'`.
- **Operation 2** (Nếu có mảng `options`):
  - **Operation Type**: `INSERT`
  - **Target Table**: `question_options`
  - **Conditions**: None
  - **Expected Result**: Tạo $N$ bản ghi mới tương ứng, tham chiếu tới `question_id` vừa tạo.

---

## Part 4 — Operational Notes

- **Idempotency**: Hỗ trợ idempotency nếu client gửi kèm header `Idempotency-Key`.
- **Tenant Isolation**: Áp dụng cô lập theo tenant nếu hệ thống bật multi-tenancy (tham chiếu tenant qua `createdBy` hoặc trường tenant_id chung).
- **Retry Strategy**: Client có thể tự động retry tối đa 3 lần nếu gặp mã lỗi `SYS-503` hoặc lỗi network time-out.
- **Audit Logging**: Ghi log sự kiện tạo câu hỏi nháp kèm `questionId`, `userId`, `traceId`.
- **Monitoring & Metrics**: Đếm metric `content_builder.question.created` với tag `status=draft`.
- **Tracing**: Truyền dẫn `traceId` qua MDC logging. Do chưa có API Gateway, backend sẽ tự sinh UUID nếu client không truyền header `X-Request-ID`.
