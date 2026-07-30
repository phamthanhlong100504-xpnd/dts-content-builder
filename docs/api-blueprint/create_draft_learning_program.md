# Tạo Chương Trình Học Nháp (Create Draft Learning Program)

## Part 0 — Classification & Identity

- **API Name**: Create Draft Learning Program
- **API Type**: Internal / Public
- **Module**: `content-builder`
- **Feature**: Learning Program Management
- **Description**: Tạo mới bản ghi chương trình học vào hệ thống với trạng thái mặc định là `DRAFT`. Chương trình ở trạng thái nháp có thể được chỉnh sửa tự do, thêm cấu trúc chương/bài học trước khi xuất bản chính thức.
- **Related Tables**: `learning_programs`
- **Related Services**: `user-service` (xác thực người tạo)

---

## Part 1 — API Contract

### Endpoint

- **HTTP Method**: `POST`
- **URL**: `/api/v1/content-builder/learning-programs/draft`
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
| `title` | String | Yes | Tên chương trình học | Không được để trống hoặc chứa toàn khoảng trắng, độ dài từ 1 đến 255 ký tự |
| `code` | String | No | Mã chương trình dùng để import/export hoặc tích hợp hệ thống ngoài | Tối đa 100 ký tự, nếu có phải duy nhất trong hệ thống (unique, không tính bản ghi đã bị xóa mềm) |
| `description` | String | No | Mô tả chi tiết chương trình học | Không giới hạn độ dài (TEXT) |
| `metadata` | Object | No | Dữ liệu mở rộng (tag, danh mục, cấu hình,...) | Chuẩn JSONB hợp lệ, mặc định `{}` |

---

### Response

- **Success Status**: `201 Created`

#### Response Body
| Name | Type | Description |
|---|---|---|
| `id` | String (UUID) | Định danh duy nhất của chương trình vừa tạo |
| `title` | String | Tên chương trình |
| `code` | String | Mã chương trình (NULL nếu không có) |
| `description` | String | Mô tả chương trình (NULL nếu không có) |
| `status` | String | Trạng thái (Luôn trả về `DRAFT`) |
| `metadata` | Object | Dữ liệu mở rộng |
| `createdBy` | String (UUID) | ID người tạo |
| `createdAt` | String (ISO-8601) | Thời điểm tạo bản ghi (UTC) |

---

### Error Codes

| Error Code | HTTP Status | Business Meaning | Client Message |
|---|---|---|---|
| `AUTH-401` | 401 Unauthorized | Thiếu hoặc JWT token không hợp lệ | Authentication required or token invalid. |
| `AUTH-403` | 403 Forbidden | Người dùng không có quyền tạo chương trình học | You do not have permission to create learning programs. |
| `VAL-400` | 400 Bad Request | Payload gửi lên vi phạm định dạng hoặc thiếu trường bắt buộc | Invalid request payload or missing required fields. |
| `VAL-409` | 409 Conflict | Mã chương trình (`code`) đã tồn tại trong hệ thống (bị trùng lặp) | Learning program with this code already exists. |
| `SYS-500` | 500 Internal Server Error | Lỗi hệ thống nội bộ hoặc lỗi thao tác cơ sở dữ liệu | An unexpected internal server error occurred. |

---

## Part 2 — Processing Specification

1. **Controller Layer**:
   - Nhận HTTP Request POST tại endpoint `/api/v1/content-builder/learning-programs/draft`.
   - Thực hiện kiểm tra định dạng dữ liệu đầu vào (Input Validation) đối với Request Body dựa trên annotations (`@NotBlank`, `@Size`, `@Valid`).
   - Trích xuất thông tin người dùng (`userId`) từ Security Context / JWT Token.
   - Gọi xuống Service Layer phương thức `createLearningProgram(request, userId, "DRAFT")`.

2. **Service Layer**:
   - Kiểm tra quyền hạn (Permission Validation): Xác minh `userId` có quyền tạo nội dung tài liệu (`learning-programs:create` permission).
   - Kiểm tra tính duy nhất của `code`: Nếu `code` được cung cấp, truy vấn bảng `learning_programs` kiểm tra `code = :code AND deleted_at IS NULL`. Nếu đã tồn tại, ném lỗi `VAL-409`.
   - Khởi tạo giao dịch cơ sở dữ liệu (`@Transactional`).
   - Tạo thực thể LearningProgram mới, gán `id = gen_random_uuid()`, `status = "DRAFT"`, `createdBy = userId`, `createdAt = CURRENT_TIMESTAMP`.
   - Thực hiện lưu LearningProgram xuống DB qua Repository Layer.
   - Cam kết giao dịch (Commit Transaction).
   - Chuyển đổi thực thể sang đối tượng Response DTO và trả về cho Controller.

3. **Repository Layer**:
   - Thực hiện lệnh SELECT kiểm tra trùng `code` (nếu có).
   - Thực hiện lệnh INSERT vào bảng `learning_programs`.

4. **External Interaction**:
   - None.

5. **Validation**:
   - **Request Validation**: Kiểm tra trường bắt buộc (`title`), độ dài tối đa, `code` tối đa 100 ký tự.
   - **Business Validation**: Kiểm tra tính duy nhất của `code`.
   - **Permission Validation**: Kiểm tra role/permission của user.

---

## Part 3 — Data Interaction

- **Operation 1** (Nếu có `code`):
  - **Operation Type**: `SELECT`
  - **Target Table**: `learning_programs`
  - **Conditions**: `code = :code AND deleted_at IS NULL`
  - **Expected Result**: Phải trả về 0 dòng (code chưa tồn tại).
- **Operation 2**:
  - **Operation Type**: `INSERT`
  - **Target Table**: `learning_programs`
  - **Conditions**: None
  - **Expected Result**: Tạo 1 bản ghi mới với `status = 'DRAFT'`.

---

## Part 4 — Operational Notes

- **Idempotency**: Hỗ trợ idempotency nếu client gửi kèm header `Idempotency-Key`.
- **Tenant Isolation**: Áp dụng cô lập theo tenant nếu hệ thống bật multi-tenancy.
- **Retry Strategy**: Client có thể tự động retry tối đa 3 lần nếu gặp mã lỗi `SYS-503` hoặc lỗi network time-out.
- **Audit Logging**: Ghi log sự kiện tạo chương trình nháp kèm `learningProgramId`, `userId`, `traceId`.
- **Monitoring & Metrics**: Đếm metric `content_builder.learning_program.created` với tag `status=draft`.
- **Tracing**: Truyền dẫn `traceId` qua MDC logging. Do chưa có API Gateway, backend sẽ tự sinh UUID nếu client không truyền header `X-Request-ID`.
