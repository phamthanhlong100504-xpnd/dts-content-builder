# Xóa Khối Chương (Delete Chapter Block)

## Part 0 — Classification & Identity

- **API Name**: Delete Chapter Block
- **API Type**: Internal / Public
- **Module**: `content-builder`
- **Feature**: Chapter Block Management
- **Description**: Thực hiện xóa mềm một node khối chương khỏi cấu trúc cây bằng cách gán `deleted_at = CURRENT_TIMESTAMP`. **Quy tắc nghiệp vụ Cascade**: Tự động xóa mềm toàn bộ các node con (children) của node bị xóa trong cùng một giao dịch, đệ quy toàn bộ cây con.
- **Related Tables**: `chapter_blocks`, `learning_programs`
- **Related Services**: `user-service`

---

## Part 1 — API Contract

### Endpoint

- **HTTP Method**: `DELETE`
- **URL**: `/api/v1/content-builder/learning-programs/{learningProgramId}/chapter-blocks/{blockId}`
- **Content Type**: None

### Request

#### Path Variables
| Name | Type | Required | Description | Validation Rules |
|---|---|---|---|---|
| `learningProgramId` | String (UUID) | Yes | Định danh chương trình học cha | UUIDv4/UUIDv7 |
| `blockId` | String (UUID) | Yes | Định danh khối chương cần xóa | UUIDv4/UUIDv7 |

#### Headers
| Name | Type | Required | Description | Validation Rules |
|---|---|---|---|---|
| `Authorization` | String | Yes | Bearer JWT Token định danh người dùng | Chuẩn RFC 6750 Bearer Token, token hợp lệ, chưa hết hạn |
| `X-Request-ID` | String | No | Trace ID định danh luồng request (Tạm thời client hoặc backend tự sinh UUID do chưa có Gateway) | UUIDv4, tối đa 64 ký tự |

---

### Response

- **Success Status**: `204 No Content`

---

### Error Codes

| Error Code | HTTP Status | Business Meaning | Client Message |
|---|---|---|---|
| `AUTH-401` | 401 Unauthorized | JWT token không hợp lệ | Authentication required. |
| `AUTH-403` | 403 Forbidden | Không có quyền xóa khối chương | You do not have permission to delete this chapter block. |
| `RES-404` | 404 Not Found | Khối chương không tồn tại hoặc đã bị xóa mềm | Chapter block not found. |
| `SYS-500` | 500 Internal Server Error | Lỗi hệ thống | An unexpected internal server error occurred. |

---

## Part 2 — Processing Specification

1. **Controller Layer**:
   - Nhận DELETE request tại `/api/v1/content-builder/learning-programs/{learningProgramId}/chapter-blocks/{blockId}`.
   - Validate UUIDs, trích xuất `userId`.
   - Gọi Service Layer: `deleteChapterBlock(learningProgramId, blockId, userId)`.

2. **Service Layer**:
   - Truy vấn bản ghi ChapterBlock theo `id = blockId` và `learning_program_id = learningProgramId` (`deleted_at IS NULL`). Nếu không thấy ném `RES-404`.
   - Kiểm tra quyền xóa của người dùng (`learning-programs:update` permission).
   - Khởi tạo giao dịch (@Transactional):
     - Gán `block.deletedAt = CURRENT_TIMESTAMP`, `block.updatedBy = userId`.
     - **Cascade soft delete**: Thu thập tất cả các node con đệ quy (theo `parent_id`). Thực hiện cập nhật batch gán `deleted_at = CURRENT_TIMESTAMP`, `updated_by = userId` cho toàn bộ node con trong cây.
   - Commit giao dịch.
   - Evict cache `learning-program:detail:{learningProgramId}`.

3. **Repository Layer**:
   - Thực hiện UPDATE soft delete trên bảng `chapter_blocks` cho node gốc và toàn bộ node con.

4. **External Interaction**:
   - None.

5. **Validation**:
   - Kiểm tra tính hợp lệ của ID và sự thuộc về chương trình học.

---

## Part 3 — Data Interaction

- **Operation 1**:
  - **Operation Type**: `SELECT`
  - **Target Table**: `chapter_blocks`
  - **Conditions**: `id = :blockId AND learning_program_id = :learningProgramId AND deleted_at IS NULL`
  - **Expected Result**: 1 dòng dữ liệu.
- **Operation 2**:
  - **Operation Type**: `SELECT`
  - **Target Table**: `chapter_blocks`
  - **Conditions**: Truy vấn đệ quy (CTE) tất cả node con theo `parent_id`, bắt đầu từ `blockId`
  - **Expected Result**: Danh sách tất cả node con trong cây.
- **Operation 3**:
  - **Operation Type**: `UPDATE`
  - **Target Table**: `chapter_blocks`
  - **Conditions**: `id IN (:blockId, :childIds) AND deleted_at IS NULL`
  - **Expected Result**: Gán `deleted_at = CURRENT_TIMESTAMP`, `updated_by = :userId` cho toàn bộ node.

---

## Part 4 — Operational Notes

- **Idempotency**: Gọi DELETE nhiều lần cho cùng 1 node đã bị soft delete sẽ trả về `404 Not Found`.
- **Cache Eviction**: Evict key `learning-program:detail:{learningProgramId}` khỏi Redis.
- **Tracing**: Truyền dẫn `traceId` qua MDC logging.
