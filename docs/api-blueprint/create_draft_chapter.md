# Tạo Chủ Đề Nháp (Create Draft Chapter)

## Part 0 — Classification & Identity

- **API Name**: Create Draft Chapter
- **API Type**: Internal / Public
- **Module**: `content-builder`
- **Feature**: Chapter Management
- **Description**: Tạo mới bản ghi chủ đề/bài học vào hệ thống với trạng thái mặc định là `DRAFT`. Chủ đề ở trạng thái nháp có thể được chỉnh sửa tự do trước khi xuất bản chính thức.
- **Related Tables**: `chapters`
- **Related Services**: `user-service` (xác thực người tạo)

---

## Part 1 — API Contract

### Endpoint

- **HTTP Method**: `POST`
- **URL**: `/api/v1/content-builder/chapters/draft`
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
| `title` | String | Yes | Tên chủ đề/bài học | Không được để trống hoặc chứa toàn khoảng trắng, độ dài từ 1 đến 255 ký tự |
| `metadata` | Object | No | Dữ liệu mở rộng (tag, cấu hình hiển thị,...) | Chuẩn JSONB hợp lệ, mặc định `{}` |

---

### Response

- **Success Status**: `201 Created`

#### Response Body
| Name | Type | Description |
|---|---|---|
| `id` | String (UUID) | Định danh duy nhất của chủ đề vừa tạo |
| `title` | String | Tên chủ đề |
| `status` | String | Trạng thái (Luôn trả về `DRAFT`) |
| `metadata` | Object | Dữ liệu mở rộng |
| `createdBy` | String (UUID) | ID người tạo |
| `createdAt` | String (ISO-8601) | Thời điểm tạo bản ghi (UTC) |

---

### Error Codes

| Error Code | HTTP Status | Business Meaning | Client Message |
|---|---|---|---|
| `AUTH-401` | 401 Unauthorized | Thiếu hoặc JWT token không hợp lệ | Authentication required or token invalid. |
| `AUTH-403` | 403 Forbidden | Người dùng không có quyền tạo chủ đề | You do not have permission to create chapters. |
| `VAL-400` | 400 Bad Request | Payload gửi lên vi phạm định dạng hoặc thiếu trường bắt buộc | Invalid request payload or missing required fields. |
| `SYS-500` | 500 Internal Server Error | Lỗi hệ thống nội bộ hoặc lỗi thao tác cơ sở dữ liệu | An unexpected internal server error occurred. |

---

## Part 2 — Processing Specification

1. **Controller Layer**:
   - Nhận HTTP Request POST tại endpoint `/api/v1/content-builder/chapters/draft`.
   - Thực hiện kiểm tra định dạng dữ liệu đầu vào (Input Validation) đối với Request Body dựa trên annotations (`@NotBlank`, `@Size`, `@Valid`).
   - Trích xuất thông tin người dùng (`userId`) từ Security Context / JWT Token.
   - Gọi xuống Service Layer phương thức `createChapter(request, userId, "DRAFT")`.

2. **Service Layer**:
   - Kiểm tra quyền hạn (Permission Validation): Xác minh `userId` có quyền tạo nội dung tài liệu (`chapters:create` permission).
   - Khởi tạo giao dịch cơ sở dữ liệu (`@Transactional`).
   - Tạo thực thể Chapter mới, gán `id = gen_random_uuid()`, `status = "DRAFT"`, `createdBy = userId`, `createdAt = CURRENT_TIMESTAMP`.
   - Thực hiện lưu Chapter xuống DB qua Repository Layer.
   - Cam kết giao dịch (Commit Transaction).
   - Chuyển đổi thực thể sang đối tượng Response DTO và trả về cho Controller.

3. **Repository Layer**:
   - Thực hiện lệnh INSERT vào bảng `chapters`.

4. **External Interaction**:
   - None.

5. **Validation**:
   - **Request Validation**: Kiểm tra trường bắt buộc (`title`), độ dài tối đa 255 ký tự, không được rỗng hoặc toàn khoảng trắng.
   - **Permission Validation**: Kiểm tra role/permission của user.

---

## Part 3 — Data Interaction

- **Operation 1**:
  - **Operation Type**: `INSERT`
  - **Target Table**: `chapters`
  - **Conditions**: None
  - **Expected Result**: Tạo 1 bản ghi mới với `status = 'DRAFT'`.

---

## Part 4 — Operational Notes

- **Idempotency**: Hỗ trợ idempotency nếu client gửi kèm header `Idempotency-Key`.
- **Tenant Isolation**: Áp dụng cô lập theo tenant nếu hệ thống bật multi-tenancy.
- **Retry Strategy**: Client có thể tự động retry tối đa 3 lần nếu gặp mã lỗi `SYS-503` hoặc lỗi network time-out.
- **Audit Logging**: Ghi log sự kiện tạo chủ đề nháp kèm `chapterId`, `userId`, `traceId`.
- **Monitoring & Metrics**: Đếm metric `content_builder.chapter.created` với tag `status=draft`.
- **Tracing**: Truyền dẫn `traceId` qua MDC logging. Do chưa có API Gateway, backend sẽ tự sinh UUID nếu client không truyền header `X-Request-ID`.
