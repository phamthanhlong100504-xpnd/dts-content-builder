# Cập Nhật Khối Chương (Update Chapter Block)

## Part 0 — Classification & Identity

- **API Name**: Update Chapter Block
- **API Type**: Internal / Public
- **Module**: `content-builder`
- **Feature**: Chapter Block Management
- **Description**: Cập nhật thông tin chi tiết của một node trong cấu trúc cây chương (tiêu đề, trạng thái, node cha, chapter liên kết, metadata...).
- **Related Tables**: `chapter_blocks`, `learning_programs`, `chapters`
- **Related Services**: `user-service`

---

## Part 1 — API Contract

### Endpoint

- **HTTP Method**: `PUT`
- **URL**: `/api/v1/content-builder/learning-programs/{learningProgramId}/chapter-blocks/{blockId}`
- **Content Type**: `application/json`

### Request

#### Path Variables
| Name | Type | Required | Description | Validation Rules |
|---|---|---|---|---|
| `learningProgramId` | String (UUID) | Yes | Định danh chương trình học cha | UUIDv4/UUIDv7 |
| `blockId` | String (UUID) | Yes | Định danh khối chương cần sửa | UUIDv4/UUIDv7 |

#### Request Body
| Name | Type | Required | Description | Validation Rules |
|---|---|---|---|---|
| `parentId` | String (UUID) | No | ID node cha mới (NULL nếu di chuyển về root) | Chuẩn UUID, phải tồn tại trong cùng chương trình học, không được trỏ về chính nó hoặc tạo vòng lặp |
| `chapterId` | String (UUID) | No | ID chapter gắn vào node (NULL nếu node là container) | Chuẩn UUID, phải tồn tại trong bảng `chapters` |
| `title` | String | Yes | Tiêu đề node | Không được để trống, tối đa 255 ký tự |
| `sortOrder` | Integer | Yes | Thứ tự hiển thị | Số nguyên >= 0 |
| `status` | String | Yes | Trạng thái | `DRAFT`, `PUBLISHED`, `ARCHIVED`, `HIDDEN` |
| `metadata` | Object | No | Dữ liệu mở rộng | Chuẩn JSONB |

---

### Response

- **Success Status**: `200 OK`
- **Response Body**: Trả về chi tiết đối tượng ChapterBlock sau khi cập nhật thành công (giống Response Body của API Add Chapter Block).

---

### Error Codes

| Error Code | HTTP Status | Business Meaning | Client Message |
|---|---|---|---|
| `AUTH-401` | 401 Unauthorized | JWT token không hợp lệ | Authentication required. |
| `AUTH-403` | 403 Forbidden | Không có quyền sửa khối chương | You do not have permission to modify this chapter block. |
| `RES-404` | 404 Not Found | Khối chương hoặc chương trình học cha không tồn tại | Chapter block not found in the specified learning program. |
| `VAL-400` | 400 Bad Request | Payload không hợp lệ | Invalid request payload. |
| `VAL-422` | 422 Unprocessable Entity | Vi phạm quy tắc nghiệp vụ (VD: `parentId` trỏ về chính node đó hoặc tạo vòng lặp trong cây, `chapterId` không tồn tại) | Invalid parent reference: circular dependency detected or chapter not found. |
| `SYS-500` | 500 Internal Server Error | Lỗi hệ thống | An unexpected internal server error occurred. |

---

## Part 2 — Processing Specification

1. **Controller Layer**:
   - Nhận PUT request tại `/api/v1/content-builder/learning-programs/{learningProgramId}/chapter-blocks/{blockId}`.
   - Validate UUID và payload, trích xuất `userId`.
   - Gọi Service Layer: `updateChapterBlock(learningProgramId, blockId, request, userId)`.

2. **Service Layer**:
   - Truy vấn bản ghi ChapterBlock theo `id = blockId` và `learning_program_id = learningProgramId` (`deleted_at IS NULL`). Nếu không thấy ném lỗi `RES-404`.
   - Kiểm tra quyền hạn của người dùng (`learning-programs:update` permission).
   - Kiểm tra nghiệp vụ:
     - Nếu `parentId` thay đổi: Kiểm tra `parentId != blockId` (không trỏ về chính nó). Duyệt cây lên trên để đảm bảo không tạo vòng lặp (circular dependency).
     - Nếu `chapterId` được cung cấp: Kiểm tra sự tồn tại của chapter trong bảng `chapters`.
   - Cập nhật thông tin vào thực thể ChapterBlock, gán `updatedBy = userId`.
   - Lưu xuống DB qua Repository Layer.
   - Evict cache `learning-program:detail:{learningProgramId}`.
   - Trả về DTO.

3. **Repository Layer**:
   - Thực hiện lệnh UPDATE trên bảng `chapter_blocks`.

4. **External Interaction**:
   - None.

5. **Validation**:
   - Kiểm tra tính nhất quán giữa `learningProgramId` trong URL và `learning_program_id` trong DB.
   - Kiểm tra không tạo vòng lặp trong cây phân cấp.

---

## Part 3 — Data Interaction

- **Operation 1**:
  - **Operation Type**: `SELECT`
  - **Target Table**: `chapter_blocks`
  - **Conditions**: `id = :blockId AND learning_program_id = :learningProgramId AND deleted_at IS NULL`
  - **Expected Result**: 1 dòng dữ liệu khối chương.
- **Operation 2** (Nếu parentId thay đổi):
  - **Operation Type**: `SELECT`
  - **Target Table**: `chapter_blocks`
  - **Conditions**: Duyệt cây từ `parentId` lên trên qua `parent_id` để kiểm tra vòng lặp
  - **Expected Result**: Không có node nào có `id = blockId` trong chuỗi cha.
- **Operation 3**:
  - **Operation Type**: `UPDATE`
  - **Target Table**: `chapter_blocks`
  - **Conditions**: `id = :blockId`
  - **Expected Result**: Cập nhật `parent_id`, `chapter_id`, `title`, `sort_order`, `status`, `metadata`, `updated_by`, `updated_at`.

---

## Part 4 — Operational Notes

- **Cache Eviction**: Xóa cache Redis của `learning-program:detail:{learningProgramId}`.
