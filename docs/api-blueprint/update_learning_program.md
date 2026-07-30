# Cập Nhật Chương Trình Học (Update Learning Program)

## Part 0 — Classification & Identity

- **API Name**: Update Learning Program
- **API Type**: Internal / Public
- **Module**: `content-builder`
- **Feature**: Learning Program Management
- **Description**: Cập nhật thông tin của một chương trình học đã tồn tại. Chỉ cập nhật thông tin riêng của chương trình (tiêu đề, mã, mô tả, metadata, trạng thái). Việc cập nhật cấu trúc cây chương/bài học được thực hiện qua các API chuyên biệt của Chapter Block.
- **Related Tables**: `learning_programs`, `chapter_blocks`
- **Related Services**: `user-service`

---

## Part 1 — API Contract

### Endpoint

- **HTTP Method**: `PUT`
- **URL**: `/api/v1/content-builder/learning-programs/{id}`
- **Content Type**: `application/json`

### Request

#### Path Variables
| Name | Type | Required | Description | Validation Rules |
|---|---|---|---|---|
| `id` | String (UUID) | Yes | Định danh chương trình cần sửa | UUIDv4/UUIDv7 |

#### Request Body
| Name | Type | Required | Description | Validation Rules |
|---|---|---|---|---|
| `title` | String | Yes | Tên chương trình | Không được để trống, 1 đến 255 ký tự |
| `code` | String | No | Mã chương trình | Tối đa 100 ký tự, duy nhất (không tính chính nó và bản ghi đã xóa mềm) |
| `description` | String | No | Mô tả chi tiết | TEXT |
| `status` | String | Yes | Trạng thái mới | `DRAFT`, `PUBLISHED`, `ARCHIVED`, `HIDDEN` |
| `metadata` | Object | No | Dữ liệu mở rộng | Chuẩn JSONB |

---

### Response

- **Success Status**: `200 OK`

#### Response Body
- Trả về chi tiết đối tượng LearningProgram sau khi cập nhật thành công (giống Response Body của API Get Learning Program Detail).

---

### Error Codes

| Error Code | HTTP Status | Business Meaning | Client Message |
|---|---|---|---|
| `AUTH-401` | 401 Unauthorized | JWT token không hợp lệ | Authentication required. |
| `AUTH-403` | 403 Forbidden | Không có quyền cập nhật chương trình này | You do not have permission to update this learning program. |
| `RES-404` | 404 Not Found | Chương trình không tồn tại hoặc đã bị xóa mềm | Learning program not found with ID. |
| `VAL-400` | 400 Bad Request | Payload không hợp lệ | Invalid request payload. |
| `VAL-409` | 409 Conflict | Mã chương trình (`code`) đã tồn tại trong bản ghi khác | Learning program with this code already exists. |
| `VAL-422` | 422 Unprocessable Entity | Vi phạm quy tắc nghiệp vụ khi chuyển sang `PUBLISHED` (VD: chưa có khối chương nào trong bảng `chapter_blocks`) | Cannot change status to PUBLISHED: Learning program lacks chapter blocks. |
| `SYS-500` | 500 Internal Server Error | Lỗi hệ thống | An unexpected internal server error occurred. |

---

## Part 2 — Processing Specification

1. **Controller Layer**:
   - Nhận PUT request tại `/api/v1/content-builder/learning-programs/{id}`.
   - Validate payload đầu vào, trích xuất `userId`.
   - Gọi Service Layer: `updateLearningProgram(id, request, userId)`.

2. **Service Layer**:
   - Truy vấn bản ghi LearningProgram từ DB theo `id` (`deleted_at IS NULL`). Nếu không thấy ném lỗi `RES-404`.
   - Kiểm tra quyền: Người dùng phải là người tạo (`createdBy == userId`) hoặc có role Admin/Editor (`learning-programs:update` permission).
   - Nếu `code` thay đổi: Kiểm tra tính duy nhất của `code` mới (loại trừ bản ghi hiện tại).
   - Nếu `request.status == "PUBLISHED"` và trạng thái hiện tại là `DRAFT`:
     - Kiểm tra trong bảng `chapter_blocks` xem chương trình này đã có ít nhất 1 khối chương (`learning_program_id = id AND deleted_at IS NULL`) hay chưa. Nếu chưa có, ném ngoại lệ vi phạm nghiệp vụ (`VAL-422`).
   - Cập nhật các trường thông tin từ request vào thực thể LearningProgram.
   - Gán `updatedBy = userId` (trường `updatedAt` được tự động cập nhật bởi Trigger DB).
   - Lưu xuống DB qua Repository Layer.
   - Evict cache liên quan (nếu có) và trả về DTO.

3. **Repository Layer**:
   - Thực hiện lệnh UPDATE trên bảng `learning_programs`.

4. **External Interaction**:
   - None.

5. **Validation**:
   - Kiểm tra tính duy nhất của `code` và các ràng buộc chuyển trạng thái hợp lệ.

---

## Part 3 — Data Interaction

- **Operation 1**:
  - **Operation Type**: `SELECT`
  - **Target Table**: `learning_programs`
  - **Conditions**: `id = :id AND deleted_at IS NULL`
  - **Expected Result**: 1 dòng dữ liệu.
- **Operation 2** (Nếu code thay đổi):
  - **Operation Type**: `SELECT`
  - **Target Table**: `learning_programs`
  - **Conditions**: `code = :newCode AND id != :id AND deleted_at IS NULL`
  - **Expected Result**: Phải trả về 0 dòng.
- **Operation 3** (Khi chuyển sang PUBLISHED):
  - **Operation Type**: `SELECT`
  - **Target Table**: `chapter_blocks`
  - **Conditions**: `learning_program_id = :id AND deleted_at IS NULL`
  - **Expected Result**: Kiểm tra COUNT >= 1.
- **Operation 4**:
  - **Operation Type**: `UPDATE`
  - **Target Table**: `learning_programs`
  - **Conditions**: `id = :id`
  - **Expected Result**: Cập nhật `title`, `code`, `description`, `status`, `metadata`, `updated_by`, `updated_at`.

---

## Part 4 — Operational Notes

- **Audit Logging**: Ghi log sự kiện cập nhật chương trình.
- **Cache Eviction**: Xóa cache Redis của key `learning-program:detail:{id}`.
