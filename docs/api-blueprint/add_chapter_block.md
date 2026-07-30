# Thêm Khối Chương Vào Chương Trình Học (Add Chapter Block)

## Part 0 — Classification & Identity

- **API Name**: Add Chapter Block
- **API Type**: Internal / Public
- **Module**: `content-builder`
- **Feature**: Chapter Block Management
- **Description**: Thêm một node mới vào cấu trúc cây chương của một chương trình học. Node có thể là container (nhóm chương/chủ đề) hoặc leaf node (gắn với một chapter cụ thể thông qua `chapterId`). Nếu không truyền `sortOrder`, hệ thống tự động tính toán gán vào vị trí cuối cùng trong danh sách các node cùng cấp (cùng `parentId`).
- **Related Tables**: `learning_programs`, `chapter_blocks`, `chapters`
- **Related Services**: `user-service`

---

## Part 1 — API Contract

### Endpoint

- **HTTP Method**: `POST`
- **URL**: `/api/v1/content-builder/learning-programs/{learningProgramId}/chapter-blocks`
- **Content Type**: `application/json`

### Request

#### Path Variables
| Name | Type | Required | Description | Validation Rules |
|---|---|---|---|---|
| `learningProgramId` | String (UUID) | Yes | Định danh chương trình học cha | UUIDv4/UUIDv7 |

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
| `parentId` | String (UUID) | No | ID node cha trong cây phân cấp (NULL nếu là node gốc) | Chuẩn UUID, phải tồn tại trong bảng `chapter_blocks` cùng `learningProgramId` và chưa bị xóa mềm |
| `chapterId` | String (UUID) | No | ID chapter được gắn vào node này (NULL nếu node chỉ là container nhóm) | Chuẩn UUID, phải tồn tại trong bảng `chapters` và chưa bị xóa mềm |
| `title` | String | Yes | Tiêu đề hiển thị của node | Không được để trống, tối đa 255 ký tự |
| `sortOrder` | Integer | No | Thứ tự hiển thị trong cùng cấp | Số nguyên >= 0. Nếu bỏ trống, tự động gán bằng `MAX(sort_order) + 1` trong các node cùng `parentId` |
| `status` | String | No | Trạng thái của node | `DRAFT`, `PUBLISHED`, `ARCHIVED`, `HIDDEN`. Mặc định: `DRAFT` |
| `metadata` | Object | No | Dữ liệu mở rộng | Chuẩn JSONB hợp lệ, mặc định `{}` |

---

### Response

- **Success Status**: `201 Created`

#### Response Body
| Name | Type | Description |
|---|---|---|
| `id` | String (UUID) | Định danh duy nhất của node vừa tạo |
| `learningProgramId` | String (UUID) | ID chương trình học cha |
| `parentId` | String (UUID) | ID node cha (NULL nếu node gốc) |
| `chapterId` | String (UUID) | ID chapter được gắn (NULL nếu container) |
| `title` | String | Tiêu đề node |
| `sortOrder` | Integer | Thứ tự hiển thị thực tế đã lưu |
| `status` | String | Trạng thái |
| `metadata` | Object | Dữ liệu mở rộng |
| `createdBy` | String (UUID) | ID người tạo |
| `createdAt` | String (ISO-8601) | Thời điểm tạo |

---

### Error Codes

| Error Code | HTTP Status | Business Meaning | Client Message |
|---|---|---|---|
| `AUTH-401` | 401 Unauthorized | JWT token không hợp lệ | Authentication required. |
| `AUTH-403` | 403 Forbidden | Không có quyền thêm khối chương | You do not have permission to modify this learning program. |
| `RES-404` | 404 Not Found | Chương trình học cha không tồn tại hoặc đã bị xóa mềm | Parent learning program not found. |
| `VAL-400` | 400 Bad Request | Payload không hợp lệ | Invalid request payload. |
| `VAL-422` | 422 Unprocessable Entity | Vi phạm quy tắc nghiệp vụ (VD: `parentId` không tồn tại trong cùng chương trình học, `chapterId` tham chiếu chapter không tồn tại hoặc đã bị xóa) | Invalid parent block or chapter reference. |
| `SYS-500` | 500 Internal Server Error | Lỗi hệ thống | An unexpected internal server error occurred. |

---

## Part 2 — Processing Specification

1. **Controller Layer**:
   - Nhận POST request tại `/api/v1/content-builder/learning-programs/{learningProgramId}/chapter-blocks`.
   - Validate Path Variable `learningProgramId` và Request Body.
   - Trích xuất `userId`, gọi Service Layer: `addChapterBlock(learningProgramId, request, userId)`.

2. **Service Layer**:
   - Truy vấn kiểm tra sự tồn tại của LearningProgram theo `learningProgramId` (`deleted_at IS NULL`). Nếu không thấy ném lỗi `RES-404`.
   - Kiểm tra quyền chỉnh sửa chương trình học này (`learning-programs:update` permission).
   - Kiểm tra quy tắc nghiệp vụ:
     - Nếu `parentId` được cung cấp: Kiểm tra sự tồn tại của node cha trong bảng `chapter_blocks` với `id = parentId AND learning_program_id = learningProgramId AND deleted_at IS NULL`. Nếu không thấy, ném lỗi `VAL-422`.
     - Nếu `chapterId` được cung cấp: Kiểm tra sự tồn tại của chapter trong bảng `chapters` với `id = chapterId AND deleted_at IS NULL`. Nếu không thấy, ném lỗi `VAL-422`.
   - Nếu `sortOrder` là null: Thực hiện câu lệnh SELECT MAX(sort_order) từ `chapter_blocks` theo `learning_program_id = learningProgramId AND parent_id = parentId` (hoặc `parent_id IS NULL` nếu `parentId` là null), gán `sortOrder = max + 1` (mặc định 0 nếu chưa có node nào).
   - Khởi tạo thực thể ChapterBlock mới, gán `id = gen_random_uuid()`, `learningProgramId`, `createdBy = userId`, `createdAt = CURRENT_TIMESTAMP`.
   - Lưu xuống DB qua Repository Layer.
   - Evict cache chi tiết chương trình học (`learning-program:detail:{learningProgramId}`) khỏi Redis.
   - Trả về Response DTO.

3. **Repository Layer**:
   - Thực hiện lệnh SELECT kiểm tra chương trình học cha.
   - Thực hiện lệnh SELECT kiểm tra node cha (nếu có parentId).
   - Thực hiện lệnh SELECT kiểm tra chapter (nếu có chapterId).
   - Thực hiện lệnh SELECT MAX(sort_order).
   - Thực hiện lệnh INSERT vào bảng `chapter_blocks`.

4. **External Interaction**:
   - None.

5. **Validation**:
   - Kiểm tra tính hợp lệ của tham chiếu `parentId` và `chapterId`.

---

## Part 3 — Data Interaction

- **Operation 1**:
  - **Operation Type**: `SELECT`
  - **Target Table**: `learning_programs`
  - **Conditions**: `id = :learningProgramId AND deleted_at IS NULL`
  - **Expected Result**: 1 dòng dữ liệu chương trình học cha.
- **Operation 2** (Nếu có parentId):
  - **Operation Type**: `SELECT`
  - **Target Table**: `chapter_blocks`
  - **Conditions**: `id = :parentId AND learning_program_id = :learningProgramId AND deleted_at IS NULL`
  - **Expected Result**: 1 dòng dữ liệu node cha.
- **Operation 3** (Nếu có chapterId):
  - **Operation Type**: `SELECT`
  - **Target Table**: `chapters`
  - **Conditions**: `id = :chapterId AND deleted_at IS NULL`
  - **Expected Result**: 1 dòng dữ liệu chapter.
- **Operation 4**:
  - **Operation Type**: `SELECT`
  - **Target Table**: `chapter_blocks`
  - **Conditions**: `learning_program_id = :learningProgramId AND parent_id = :parentId AND deleted_at IS NULL` (Để tính max sort_order)
  - **Expected Result**: Giá trị max sort_order.
- **Operation 5**:
  - **Operation Type**: `INSERT`
  - **Target Table**: `chapter_blocks`
  - **Conditions**: None
  - **Expected Result**: Tạo 1 bản ghi node mới.

---

## Part 4 — Operational Notes

- **Cache Eviction**: Xóa cache của key `learning-program:detail:{learningProgramId}` để lần tra cứu tiếp theo phản ánh đúng cấu trúc cây mới.
- **Audit Logging**: Ghi log thêm khối chương.
- **Tracing**: Truyền dẫn `traceId` qua MDC logging.
