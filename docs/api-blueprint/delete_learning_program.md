# Xóa Mềm Chương Trình Học (Soft Delete Learning Program)

## Part 0 — Classification & Identity

- **API Name**: Soft Delete Learning Program
- **API Type**: Internal / Public
- **Module**: `content-builder`
- **Feature**: Learning Program Management
- **Description**: Thực hiện xóa mềm một chương trình học khỏi hệ thống bằng cách gán `deleted_at = CURRENT_TIMESTAMP`. **Chỉ cho phép xóa chương trình ở trạng thái `DRAFT`**. Chương trình đã `PUBLISHED`, `ARCHIVED` hoặc `HIDDEN` không thể xóa — admin phải chuyển về `DRAFT` trước hoặc sử dụng chức năng Archive. **Quy tắc nghiệp vụ Cascade**: Tự động tiến hành xóa mềm toàn bộ danh sách khối chương (`chapter_blocks`) trực thuộc chương trình này trong cùng một giao dịch.
- **Related Tables**: `learning_programs`, `chapter_blocks`
- **Related Services**: `user-service`

---

## Part 1 — API Contract

### Endpoint

- **HTTP Method**: `DELETE`
- **URL**: `/api/v1/content-builder/learning-programs/{id}`
- **Content Type**: None

### Request

#### Path Variables
| Name | Type | Required | Description | Validation Rules |
|---|---|---|---|---|
| `id` | String (UUID) | Yes | Định danh chương trình cần xóa | UUIDv4/UUIDv7 |

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
| `AUTH-403` | 403 Forbidden | Không có quyền xóa chương trình này | You do not have permission to delete this learning program. |
| `RES-404` | 404 Not Found | Chương trình không tồn tại hoặc đã bị xóa mềm từ trước | Learning program not found. |
| `VAL-422` | 422 Unprocessable Entity | Chương trình không ở trạng thái `DRAFT`, không thể xóa. Phải chuyển về DRAFT hoặc sử dụng Archive | Cannot delete learning program: Only DRAFT programs can be deleted. Archive it or change status to DRAFT first. |
| `SYS-500` | 500 Internal Server Error | Lỗi hệ thống | An unexpected internal server error occurred. |

---

## Part 2 — Processing Specification

1. **Controller Layer**:
   - Nhận DELETE request tại `/api/v1/content-builder/learning-programs/{id}`.
   - Validate UUID `id`, trích xuất `userId`.
   - Gọi Service Layer: `deleteLearningProgram(id, userId)`.

2. **Service Layer**:
   - Truy vấn bản ghi LearningProgram theo `id` (`deleted_at IS NULL`). Nếu không thấy ném lỗi `RES-404`.
   - Kiểm tra quyền xóa (`learning-programs:delete` permission hoặc chủ sở hữu).
   - **Status Validation**: Kiểm tra `learningProgram.status == "DRAFT"`. Nếu trạng thái không phải `DRAFT` (tức là `PUBLISHED`, `ARCHIVED` hoặc `HIDDEN`), ném lỗi `VAL-422`. Chương trình đã xuất bản hoặc lưu trữ không được phép xóa để đảm bảo an toàn dữ liệu cho các dịch vụ bên ngoài (exam-service, practice-service) đang tham chiếu.
   - Khởi tạo giao dịch (@Transactional):
     - Gán `learningProgram.deletedAt = CURRENT_TIMESTAMP`, `learningProgram.updatedBy = userId`.
     - Thực hiện câu lệnh cập nhật batch trên bảng `chapter_blocks`: Gán `deleted_at = CURRENT_TIMESTAMP`, `updated_by = userId` cho tất cả bản ghi có `learning_program_id = id` và `deleted_at IS NULL`.
   - Commit giao dịch.
   - Evict cache liên quan.

3. **Repository Layer**:
   - Thực hiện lệnh UPDATE soft delete trên `learning_programs`.
   - Thực hiện lệnh UPDATE soft delete trên `chapter_blocks`.

4. **External Interaction**:
   - Gửi sự kiện `LearningProgramDeletedEvent` lên Kafka (optional, dùng cho mục đích thông báo/thống kê). Không cần gọi REST đồng bộ tới service ngoài vì chỉ cho phép xóa trạng thái `DRAFT` — chưa có service nào tham chiếu.

5. **Validation**:
   - Kiểm tra tính hợp lệ của ID.
   - Kiểm tra trạng thái phải là `DRAFT`.

---

## Part 3 — Data Interaction

- **Operation 1**:
  - **Operation Type**: `SELECT`
  - **Target Table**: `learning_programs`
  - **Conditions**: `id = :id AND deleted_at IS NULL`
  - **Expected Result**: 1 dòng dữ liệu, kiểm tra `status = 'DRAFT'`.
- **Operation 2**:
  - **Operation Type**: `UPDATE`
  - **Target Table**: `learning_programs`
  - **Conditions**: `id = :id AND deleted_at IS NULL`
  - **Expected Result**: Gán `deleted_at = CURRENT_TIMESTAMP`, `updated_by = :userId`.
- **Operation 3**:
  - **Operation Type**: `UPDATE`
  - **Target Table**: `chapter_blocks`
  - **Conditions**: `learning_program_id = :id AND deleted_at IS NULL`
  - **Expected Result**: Gán `deleted_at = CURRENT_TIMESTAMP`, `updated_by = :userId` cho toàn bộ khối chương con.

---

## Part 4 — Operational Notes

- **Idempotency**: Việc gọi nhiều lần API soft delete với cùng 1 ID sau lần thành công đầu tiên sẽ trả về `404 Not Found` (do `deleted_at` đã có giá trị).
- **Audit Logging**: Ghi log sự kiện xóa mềm chương trình kèm danh sách số lượng chapter_blocks đã bị xóa theo.
- **Cache Eviction**: Evict key `learning-program:detail:{id}` khỏi Redis.
- **Status Lifecycle**: Chương trình đã PUBLISHED không thể xóa — admin chuyển sang ARCHIVED để ngừng sử dụng, hoặc HIDDEN để tạm ẩn. Dữ liệu lịch sử luôn được giữ lại cho báo cáo và audit. Các dịch vụ bên ngoài (exam, practice) chỉ cần filter `status = PUBLISHED` khi tạo phiên mới.
