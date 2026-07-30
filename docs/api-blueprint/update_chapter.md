# Cập Nhật Chủ Đề (Update Chapter)

## Part 0 — Classification & Identity

- **API Name**: Update Chapter
- **API Type**: Internal / Public
- **Module**: `content-builder`
- **Feature**: Chapter Management
- **Description**: Cập nhật thông tin của một chủ đề đã tồn tại. Chỉ cập nhật thông tin riêng của chủ đề (tiêu đề, metadata, trạng thái). Việc cập nhật cấu trúc khối câu hỏi được thực hiện qua các API chuyên biệt của Question Block.
- **Related Tables**: `chapters`, `question_blocks`
- **Related Services**: `user-service`

---

## Part 1 — API Contract

### Endpoint

- **HTTP Method**: `PUT`
- **URL**: `/api/v1/content-builder/chapters/{id}`
- **Content Type**: `application/json`

### Request

#### Path Variables
| Name | Type | Required | Description | Validation Rules |
|---|---|---|---|---|
| `id` | String (UUID) | Yes | Định danh chủ đề cần sửa | UUIDv4/UUIDv7 |

#### Request Body
| Name | Type | Required | Description | Validation Rules |
|---|---|---|---|---|
| `title` | String | Yes | Tên chủ đề | Không được để trống, 1 đến 255 ký tự |
| `status` | String | Yes | Trạng thái mới | `DRAFT`, `PUBLISHED`, `ARCHIVED`, `HIDDEN` |
| `metadata` | Object | No | Dữ liệu mở rộng | Chuẩn JSONB hợp lệ |

---

### Response

- **Success Status**: `200 OK`

#### Response Body
- Trả về chi tiết đối tượng Chapter sau khi cập nhật thành công (giống Response Body của API Get Chapter Detail).

---

### Error Codes

| Error Code | HTTP Status | Business Meaning | Client Message |
|---|---|---|---|
| `AUTH-401` | 401 Unauthorized | JWT token không hợp lệ | Authentication required. |
| `AUTH-403` | 403 Forbidden | Không có quyền cập nhật chủ đề này | You do not have permission to update this chapter. |
| `RES-404` | 404 Not Found | Chủ đề không tồn tại hoặc đã bị xóa mềm | Chapter not found with ID. |
| `VAL-400` | 400 Bad Request | Payload không hợp lệ | Invalid request payload. |
| `VAL-422` | 422 Unprocessable Entity | Vi phạm quy tắc nghiệp vụ khi chuyển sang `PUBLISHED` (VD: chưa có khối câu hỏi nào trong bảng `question_blocks`) | Cannot change status to PUBLISHED: Chapter lacks question blocks. |
| `SYS-500` | 500 Internal Server Error | Lỗi hệ thống | An unexpected internal server error occurred. |

---

## Part 2 — Processing Specification

1. **Controller Layer**:
   - Nhận PUT request tại `/api/v1/content-builder/chapters/{id}`.
   - Validate payload đầu vào, trích xuất `userId`.
   - Gọi Service Layer: `updateChapter(id, request, userId)`.

2. **Service Layer**:
   - Truy vấn bản ghi Chapter từ DB theo `id` (`deleted_at IS NULL`). Nếu không thấy ném lỗi `RES-404`.
   - Kiểm tra quyền: Người dùng phải là người tạo (`createdBy == userId`) hoặc có role Admin/Editor (`chapters:update` permission).
   - Nếu `request.status == "PUBLISHED"` và trạng thái hiện tại là `DRAFT`:
     - Kiểm tra trong bảng `question_blocks` xem chủ đề này đã có ít nhất 1 khối câu hỏi (`chapter_id = id AND deleted_at IS NULL`) hay chưa. Nếu chưa có, ném ngoại lệ vi phạm nghiệp vụ (`VAL-422`).
   - Cập nhật các trường thông tin từ request vào thực thể Chapter.
   - Gán `updatedBy = userId` (trường `updatedAt` được tự động cập nhật bởi Trigger DB).
   - Lưu xuống DB qua Repository Layer.
   - Evict cache liên quan (nếu có) và trả về DTO.

3. **Repository Layer**:
   - Thực hiện lệnh UPDATE trên bảng `chapters`.

4. **External Interaction**:
   - None.

5. **Validation**:
   - Kiểm tra các ràng buộc chuyển trạng thái hợp lệ.

---

## Part 3 — Data Interaction

- **Operation 1**:
  - **Operation Type**: `SELECT`
  - **Target Table**: `chapters`
  - **Conditions**: `id = :id AND deleted_at IS NULL`
  - **Expected Result**: 1 dòng dữ liệu.
- **Operation 2** (Khi chuyển sang PUBLISHED):
  - **Operation Type**: `SELECT`
  - **Target Table**: `question_blocks`
  - **Conditions**: `chapter_id = :id AND deleted_at IS NULL`
  - **Expected Result**: Kiểm tra COUNT >= 1.
- **Operation 3**:
  - **Operation Type**: `UPDATE`
  - **Target Table**: `chapters`
  - **Conditions**: `id = :id`
  - **Expected Result**: Cập nhật `title`, `status`, `metadata`, `updated_by`, `updated_at`.

---

## Part 4 — Operational Notes

- **Audit Logging**: Ghi log sự kiện cập nhật chủ đề.
- **Cache Eviction**: Xóa cache Redis của key `chapter:detail:{id}`.
