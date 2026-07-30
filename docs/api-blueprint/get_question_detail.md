# API Blueprint — Get Question Detail

Tài liệu thiết kế API Blueprint cho nghiệp vụ Lấy Chi tiết Câu hỏi (Get Question Detail) trong dịch vụ `content-builder`.
Tuân thủ tiêu chuẩn kiến trúc `/api/{version}/{service}/{object}/` và đặc tả `rules/docx/java/api-blueprint-generator.md`.

---

## Part 0 — Classification & Identity

- **API Name**: Get Question Detail
- **API Type**: Internal / Public
- **Module**: `content-builder`
- **Feature**: Question Management
- **Description**: Tra cứu và lấy thông tin chi tiết của một câu hỏi theo ID. Hỗ trợ tham số truy vấn `includeOptions=true` để đính kèm toàn bộ danh sách lựa chọn/đáp án liên quan (chưa bị xóa mềm).
- **Related Tables**: `questions`, `question_options`
- **Related Services**: None

---

## Part 1 — API Contract

### Endpoint

- **HTTP Method**: `GET`
- **URL**: `/api/v1/content-builder/questions/{id}`
- **Content Type**: `application/json`

### Request

#### Path Variables
| Name | Type | Required | Description | Validation Rules |
|---|---|---|---|---|
| `id` | String (UUID) | Yes | Định danh câu hỏi | Định dạng UUIDv4/UUIDv7 |

#### Query Parameters
| Name | Type | Required | Description | Validation Rules |
|---|---|---|---|---|
| `includeOptions` | Boolean | No | Cờ yêu cầu lấy kèm danh sách đáp án (`question_options`) | `true` hoặc `false`, mặc định là `false` |

#### Headers
| Name | Type | Required | Description | Validation Rules |
|---|---|---|---|---|
| `Authorization` | String | Yes | Bearer JWT Token định danh người dùng | Chuẩn RFC 6750 Bearer Token, token hợp lệ, chưa hết hạn |
| `X-Request-ID` | String | No | Trace ID định danh luồng request (Tạm thời client hoặc backend tự sinh UUID do chưa có Gateway) | UUIDv4, tối đa 64 ký tự |

#### Request Body
- None

---

### Response

- **Success Status**: `200 OK`

#### Response Body
| Name | Type | Description |
|---|---|---|
| `id` | String (UUID) | Định danh câu hỏi |
| `type` | String | Loại câu hỏi |
| `content` | String | Nội dung câu hỏi |
| `explanations` | Object/Array | Lời giải thích |
| `mediaFileIds` | Array<String> | Danh sách UUID media |
| `attachments` | Object/Array | File đính kèm |
| `references` | Object/Array | Tham khảo |
| `status` | String | Trạng thái (`DRAFT`, `PUBLISHED`, `ARCHIVED`, `HIDDEN`) |
| `metadata` | Object | Dữ liệu mở rộng |
| `createdBy` | String (UUID) | ID người tạo |
| `createdAt` | String (ISO-8601)| Thời điểm tạo |
| `updatedBy` | String (UUID) | ID người cập nhật gần nhất |
| `updatedAt` | String (ISO-8601)| Thời điểm cập nhật gần nhất |
| `options` | Array<Object> | Danh sách các đáp án (Chỉ có giá trị nếu `includeOptions = true`, sắp xếp theo `sort_order ASC`) |

---

### Error Codes

| Error Code | HTTP Status | Business Meaning | Client Message |
|---|---|---|---|
| `AUTH-401` | 401 Unauthorized | Thiếu hoặc JWT token không hợp lệ | Authentication required. |
| `AUTH-403` | 403 Forbidden | Người dùng không có quyền xem câu hỏi này | You do not have permission to view this question. |
| `RES-404` | 404 Not Found | Câu hỏi không tồn tại hoặc đã bị xóa mềm (`deleted_at IS NOT NULL`) | Question not found with the specified ID. |
| `SYS-500` | 500 Internal Server Error | Lỗi hệ thống | An unexpected internal server error occurred. |

---

## Part 2 — Processing Specification

1. **Controller Layer**:
   - Nhận GET request tại `/api/v1/content-builder/questions/{id}`.
   - Validate tham số path `id` đúng định dạng UUID.
   - Gọi Service Layer: `getQuestionById(id, includeOptions)`.

2. **Service Layer**:
   - Kiểm tra quyền đọc (`questions:read` permission).
   - Gọi Repository truy vấn bản ghi Question theo `id` với điều kiện `deleted_at IS NULL`.
   - Nếu không tìm thấy, ném ngoại lệ `ResourceNotFoundException` (trả lỗi `RES-404`).
   - Nếu `includeOptions == true`:
     - Gọi Repository truy vấn danh sách `question_options` theo `question_id = id` và `deleted_at IS NULL`, sắp xếp theo `sort_order ASC, created_at ASC`.
     - Đính kèm danh sách options vào đối tượng DTO kết quả.
   - Trả về Response DTO.

3. **Repository Layer**:
   - Thực hiện lệnh SELECT trên bảng `questions`.
   - Thực hiện lệnh SELECT trên bảng `question_options` (nếu cần).

4. **External Interaction**:
   - None.

5. **Validation**:
   - Kiểm tra UUID hợp lệ.

---

## Part 3 — Data Interaction

- **Operation 1**:
  - **Operation Type**: `SELECT`
  - **Target Table**: `questions`
  - **Conditions**: `id = :id AND deleted_at IS NULL`
  - **Expected Result**: Trả về 0 hoặc 1 dòng dữ liệu.
- **Operation 2** (Khi `includeOptions=true`):
  - **Operation Type**: `SELECT`
  - **Target Table**: `question_options`
  - **Conditions**: `question_id = :id AND deleted_at IS NULL ORDER BY sort_order ASC, created_at ASC`
  - **Expected Result**: Trả về danh sách $0 \dots N$ đáp án.

---

## Part 4 — Operational Notes

- **Caching**: Có thể sử dụng Redis cache với key `question:detail:{id}` (đặc biệt đối với các câu hỏi PUBLISHED có lưu lượng truy cập lớn). Khi update hoặc soft-delete cần tiến hành evict cache.
- **Monitoring**: Theo dõi latency truy vấn chi tiết câu hỏi.

- **Tracing**: Truyền dẫn `traceId` qua MDC logging. Do chưa có API Gateway, backend sẽ tự sinh UUID nếu client không truyền header `X-Request-ID`.
