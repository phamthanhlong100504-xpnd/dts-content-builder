# Xóa Mềm Chủ Đề (Soft Delete Chapter)

## Part 0 — Classification & Identity

- **API Name**: Soft Delete Chapter
- **API Type**: Internal / Public
- **Module**: `content-builder`
- **Feature**: Chapter Management
- **Description**: Thực hiện xóa mềm một chủ đề khỏi hệ thống bằng cách gán `deleted_at = CURRENT_TIMESTAMP`. **Chỉ cho phép xóa chủ đề ở trạng thái `DRAFT`**. Chủ đề đã `PUBLISHED`, `ARCHIVED` hoặc `HIDDEN` không thể xóa — phải chuyển về `DRAFT` trước hoặc sử dụng chức năng Archive. **Quy tắc nghiệp vụ Cascade**: Tự động tiến hành xóa mềm toàn bộ danh sách khối câu hỏi (`question_blocks`) trực thuộc chủ đề này trong cùng một giao dịch.
- **Related Tables**: `chapters`, `question_blocks`
- **Related Services**: `user-service`

---

## Part 1 — API Contract

### Endpoint

- **HTTP Method**: `DELETE`
- **URL**: `/api/v1/content-builder/chapters/{id}`
- **Content Type**: None

### Request

#### Path Variables
| Name | Type | Required | Description | Validation Rules |
|---|---|---|---|---|
| `id` | String (UUID) | Yes | Định danh chủ đề cần xóa | UUIDv4/UUIDv7 |

#### Headers
| Name | Type | Required | Description | Validation Rules |
|---|---|---|---|---|
| `Authorization` | String | Yes | Bearer JWT Token | Hợp lệ |

---

### Response

- **Success Status**: `204 No Content` (hoặc `200 OK` kèm thông báo thành công)
- **Response Body**: Empty (đối với 204) hoặc Object JSON xác nhận xóa thành công.

---

### Error Codes

| Error Code | HTTP Status | Business Meaning | Client Message |
|---|---|---|---|
| `AUTH-401` | 401 Unauthorized | JWT token không hợp lệ | Authentication required. |
| `AUTH-403` | 403 Forbidden | Không có quyền xóa chủ đề này | You do not have permission to delete this chapter. |
| `RES-404` | 404 Not Found | Chủ đề không tồn tại hoặc đã bị xóa mềm từ trước | Chapter not found. |
| `VAL-422` | 422 Unprocessable Entity | Chủ đề không ở trạng thái `DRAFT`, không thể xóa. | Cannot delete chapter: Only DRAFT chapters can be deleted. |
| `VAL-409` | 409 Conflict | Chủ đề đang được sử dụng/gắn trong một khối chương (`chapter_blocks`) đang hoạt động. | Chapter cannot be deleted because it is linked to a chapter block. |
| `SYS-500` | 500 Internal Server Error | Lỗi hệ thống | An unexpected internal server error occurred. |

---

## Part 2 — Processing Specification

1. **Controller Layer**:
   - Nhận DELETE request tại `/api/v1/content-builder/chapters/{id}`.
   - Validate UUID `id`, trích xuất `userId`.
   - Gọi Service Layer: `deleteChapter(id, userId)`.

2. **Service Layer**:
   - Truy vấn bản ghi Chapter theo `id` (`deleted_at IS NULL`). Nếu không thấy ném lỗi `RES-404`.
   - Kiểm tra quyền xóa (`chapters:delete` permission hoặc chủ sở hữu).
   - **Status Validation**: Kiểm tra `chapter.status == "DRAFT"`. Nếu trạng thái không phải `DRAFT`, ném lỗi `VAL-422`.
   - Kiểm tra ràng buộc nghiệp vụ: Nếu chủ đề đang được gắn trong bất kỳ `chapter_blocks` nào đang hoạt động (`chapter_id = id AND deleted_at IS NULL`), ném lỗi xung đột `VAL-409`. (Dù là DRAFT nhưng nếu đã lỡ gắn vào chương trình học thì phải gỡ ra trước khi xóa).
   - Khởi tạo giao dịch (@Transactional):
     - Gán `chapter.deletedAt = CURRENT_TIMESTAMP`, `chapter.updatedBy = userId`.
     - Thực hiện câu lệnh cập nhật batch trên bảng `question_blocks`: Gán `deleted_at = CURRENT_TIMESTAMP`, `updated_by = userId` cho tất cả bản ghi có `chapter_id = id` và `deleted_at IS NULL`.
   - Commit giao dịch.
   - Evict cache liên quan.

3. **Repository Layer**:
   - Thực hiện lệnh UPDATE soft delete trên `chapters`.
   - Thực hiện lệnh UPDATE soft delete trên `question_blocks`.

4. **External Interaction**:
   - None.

5. **Validation**:
   - Kiểm tra tính hợp lệ của ID.
   - Kiểm tra trạng thái phải là `DRAFT`.
   - Kiểm tra ràng buộc tham chiếu từ `chapter_blocks`.

---

## Part 3 — Data Interaction

- **Operation 1**:
  - **Operation Type**: `SELECT`
  - **Target Table**: `chapters`
  - **Conditions**: `id = :id AND deleted_at IS NULL`
  - **Expected Result**: 1 dòng dữ liệu, kiểm tra `status = 'DRAFT'`.
- **Operation 2**:
  - **Operation Type**: `SELECT`
  - **Target Table**: `chapter_blocks`
  - **Conditions**: `chapter_id = :id AND deleted_at IS NULL`
  - **Expected Result**: Kiểm tra COUNT = 0 (không có tham chiếu).
- **Operation 3**:
  - **Operation Type**: `UPDATE`
  - **Target Table**: `chapters`
  - **Conditions**: `id = :id AND deleted_at IS NULL`
  - **Expected Result**: Gán `deleted_at = CURRENT_TIMESTAMP`, `updated_by = :userId`.
- **Operation 4**:
  - **Operation Type**: `UPDATE`
  - **Target Table**: `question_blocks`
  - **Conditions**: `chapter_id = :id AND deleted_at IS NULL`
  - **Expected Result**: Gán `deleted_at = CURRENT_TIMESTAMP`, `updated_by = :userId` cho toàn bộ khối câu hỏi con.

---

## Part 4 — Operational Notes

- **Idempotency**: Việc gọi nhiều lần API soft delete với cùng 1 ID sau lần thành công đầu tiên sẽ trả về `404 Not Found` (do `deleted_at` đã có giá trị).
- **Audit Logging**: Ghi log sự kiện xóa mềm chủ đề kèm danh sách số lượng question_blocks đã bị xóa theo.
- **Cache Eviction**: Evict key `chapter:detail:{id}` khỏi Redis.
