# Lấy Chi Tiết Chủ Đề (Get Chapter Detail)

## Part 0 — Classification & Identity

- **API Name**: Get Chapter Detail
- **API Type**: Internal / Public
- **Module**: `content-builder`
- **Feature**: Chapter Management
- **Description**: Tra cứu và lấy thông tin chi tiết của một chủ đề theo ID. Hỗ trợ tham số truy vấn `includeQuestionBlocks=true` để đính kèm toàn bộ cấu trúc cây các khối câu hỏi liên quan (chưa bị xóa mềm).
- **Related Tables**: `chapters`, `question_blocks`
- **Related Services**: None

---

## Part 1 — API Contract

### Endpoint

- **HTTP Method**: `GET`
- **URL**: `/api/v1/content-builder/chapters/{id}`
- **Content Type**: `application/json`

### Request

#### Path Variables
| Name | Type | Required | Description | Validation Rules |
|---|---|---|---|---|
| `id` | String (UUID) | Yes | Định danh chủ đề | Định dạng UUIDv4/UUIDv7 |

#### Query Parameters
| Name | Type | Required | Description | Validation Rules |
|---|---|---|---|---|
| `includeQuestionBlocks` | Boolean | No | Cờ yêu cầu lấy kèm cấu trúc cây khối câu hỏi (`question_blocks`) | `true` hoặc `false`, mặc định là `false` |

#### Headers
| Name | Type | Required | Description | Validation Rules |
|---|---|---|---|---|
| `Authorization` | String | Yes | Bearer JWT Token | Hợp lệ |

#### Request Body
- None

---

### Response

- **Success Status**: `200 OK`

#### Response Body
| Name | Type | Description |
|---|---|---|
| `id` | String (UUID) | Định danh chủ đề |
| `title` | String | Tên chủ đề |
| `status` | String | Trạng thái (`DRAFT`, `PUBLISHED`, `ARCHIVED`, `HIDDEN`) |
| `metadata` | Object | Dữ liệu mở rộng |
| `createdBy` | String (UUID) | ID người tạo |
| `createdAt` | String (ISO-8601) | Thời điểm tạo |
| `updatedBy` | String (UUID) | ID người cập nhật gần nhất |
| `updatedAt` | String (ISO-8601) | Thời điểm cập nhật gần nhất |
| `questionBlocks` | Array<Object> | Cấu trúc cây các khối câu hỏi (Chỉ có giá trị nếu `includeQuestionBlocks = true`, sắp xếp theo `sort_order ASC`) |

---

### Error Codes

| Error Code | HTTP Status | Business Meaning | Client Message |
|---|---|---|---|
| `AUTH-401` | 401 Unauthorized | Thiếu hoặc JWT token không hợp lệ | Authentication required. |
| `AUTH-403` | 403 Forbidden | Người dùng không có quyền xem chủ đề này | You do not have permission to view this chapter. |
| `RES-404` | 404 Not Found | Chủ đề không tồn tại hoặc đã bị xóa mềm (`deleted_at IS NOT NULL`) | Chapter not found with the specified ID. |
| `SYS-500` | 500 Internal Server Error | Lỗi hệ thống | An unexpected internal server error occurred. |

---

## Part 2 — Processing Specification

1. **Controller Layer**:
   - Nhận GET request tại `/api/v1/content-builder/chapters/{id}`.
   - Validate tham số path `id` đúng định dạng UUID.
   - Gọi Service Layer: `getChapterById(id, includeQuestionBlocks)`.

2. **Service Layer**:
   - Kiểm tra quyền đọc (`chapters:read` permission).
   - Gọi Repository truy vấn bản ghi Chapter theo `id` với điều kiện `deleted_at IS NULL`.
   - Nếu không tìm thấy, ném ngoại lệ `ResourceNotFoundException` (trả lỗi `RES-404`).
   - Nếu `includeQuestionBlocks == true`:
     - Gọi Repository truy vấn danh sách `question_blocks` theo `chapter_id = id` và `deleted_at IS NULL`, sắp xếp theo `sort_order ASC, created_at ASC`.
     - Đính kèm danh sách questionBlocks vào đối tượng DTO kết quả.
   - Trả về Response DTO.

3. **Repository Layer**:
   - Thực hiện lệnh SELECT trên bảng `chapters`.
   - Thực hiện lệnh SELECT trên bảng `question_blocks` (nếu cần).

4. **External Interaction**:
   - None.

5. **Validation**:
   - Kiểm tra UUID hợp lệ.

---

## Part 3 — Data Interaction

- **Operation 1**:
  - **Operation Type**: `SELECT`
  - **Target Table**: `chapters`
  - **Conditions**: `id = :id AND deleted_at IS NULL`
  - **Expected Result**: Trả về 0 hoặc 1 dòng dữ liệu.
- **Operation 2** (Khi `includeQuestionBlocks=true`):
  - **Operation Type**: `SELECT`
  - **Target Table**: `question_blocks`
  - **Conditions**: `chapter_id = :id AND deleted_at IS NULL ORDER BY sort_order ASC, created_at ASC`
  - **Expected Result**: Trả về danh sách $0 \dots N$ khối câu hỏi.

---

## Part 4 — Operational Notes

- **Caching**: Có thể sử dụng Redis cache với key `chapter:detail:{id}` (đặc biệt đối với các chủ đề PUBLISHED có lưu lượng truy cập lớn). Khi update hoặc soft-delete cần tiến hành evict cache.
- **Monitoring**: Theo dõi latency truy vấn chi tiết chủ đề.
