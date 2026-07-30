# Lấy Cây Cấu Trúc Khối Chương (Get Chapter Block Tree)

## Part 0 — Classification & Identity

- **API Name**: Get Chapter Block Tree
- **API Type**: Internal / Public
- **Module**: `content-builder`
- **Feature**: Chapter Block Management
- **Description**: Lấy toàn bộ cấu trúc cây phân cấp của các khối chương thuộc một chương trình học. Trả về danh sách phẳng (flat list) hoặc cấu trúc lồng nhau (nested tree) tùy thuộc tham số `format`. Mặc định trả về danh sách phẳng với thông tin `parentId` để client tự xây dựng cây.
- **Related Tables**: `chapter_blocks`, `learning_programs`
- **Related Services**: None

---

## Part 1 — API Contract

### Endpoint

- **HTTP Method**: `GET`
- **URL**: `/api/v1/content-builder/learning-programs/{learningProgramId}/chapter-blocks`
- **Content Type**: `application/json`

### Request

#### Path Variables
| Name | Type | Required | Description | Validation Rules |
|---|---|---|---|---|
| `learningProgramId` | String (UUID) | Yes | Định danh chương trình học | UUIDv4/UUIDv7 |

#### Query Parameters
| Name | Type | Required | Description | Validation Rules |
|---|---|---|---|---|
| `format` | String | No | Định dạng dữ liệu trả về | `flat` (mặc định) hoặc `tree`. `flat` trả danh sách phẳng, `tree` trả cấu trúc lồng nhau |

#### Headers
| Name | Type | Required | Description | Validation Rules |
|---|---|---|---|---|
| `Authorization` | String | Yes | Bearer JWT Token | Hợp lệ |

#### Request Body
- None

---

### Response

- **Success Status**: `200 OK`

#### Response Body (format=flat)
- Trả về mảng phẳng `Array<ChapterBlockDetail>` sắp xếp theo `sort_order ASC`:

| Name | Type | Description |
|---|---|---|
| `id` | String (UUID) | Định danh node |
| `learningProgramId` | String (UUID) | ID chương trình học |
| `parentId` | String (UUID) | ID node cha (NULL nếu node gốc) |
| `chapterId` | String (UUID) | ID chapter gắn kèm (NULL nếu container) |
| `title` | String | Tiêu đề node |
| `sortOrder` | Integer | Thứ tự hiển thị |
| `status` | String | Trạng thái |
| `metadata` | Object | Dữ liệu mở rộng |
| `createdBy` | String (UUID) | ID người tạo |
| `createdAt` | String (ISO-8601) | Thời điểm tạo |
| `updatedBy` | String (UUID) | ID người cập nhật |
| `updatedAt` | String (ISO-8601) | Thời điểm cập nhật |

#### Response Body (format=tree)
- Trả về mảng các node gốc `Array<ChapterBlockTreeNode>`, mỗi node có thêm trường `children` chứa danh sách các node con lồng nhau:

| Name | Type | Description |
|---|---|---|
| *(Các trường giống format flat)* | | |
| `children` | Array<ChapterBlockTreeNode> | Danh sách các node con lồng nhau, sắp xếp theo `sort_order ASC` |

---

### Error Codes

| Error Code | HTTP Status | Business Meaning | Client Message |
|---|---|---|---|
| `AUTH-401` | 401 Unauthorized | JWT token không hợp lệ | Authentication required. |
| `AUTH-403` | 403 Forbidden | Người dùng không có quyền xem cấu trúc chương trình này | You do not have permission to view chapter blocks. |
| `RES-404` | 404 Not Found | Chương trình học không tồn tại hoặc đã bị xóa mềm | Learning program not found. |
| `SYS-500` | 500 Internal Server Error | Lỗi hệ thống | An unexpected internal server error occurred. |

---

## Part 2 — Processing Specification

1. **Controller Layer**:
   - Nhận GET request tại `/api/v1/content-builder/learning-programs/{learningProgramId}/chapter-blocks`.
   - Validate tham số path `learningProgramId` và query parameter `format`.
   - Gọi Service Layer: `getChapterBlockTree(learningProgramId, format)`.

2. **Service Layer**:
   - Kiểm tra quyền đọc (`learning-programs:read` permission).
   - Kiểm tra sự tồn tại của LearningProgram (`deleted_at IS NULL`). Nếu không tìm thấy, ném `RES-404`.
   - Truy vấn toàn bộ `chapter_blocks` thuộc `learningProgramId` (`deleted_at IS NULL`), sắp xếp theo `sort_order ASC, created_at ASC`.
   - Nếu `format == "tree"`:
     - Xây dựng cấu trúc cây từ danh sách phẳng bằng cách nhóm theo `parentId`.
     - Trả về danh sách các node gốc (`parentId IS NULL`) với `children` lồng nhau.
   - Nếu `format == "flat"` (mặc định):
     - Trả về danh sách phẳng.

3. **Repository Layer**:
   - Thực hiện lệnh SELECT trên bảng `chapter_blocks`.

4. **External Interaction**:
   - None.

5. **Validation**:
   - Kiểm tra `format` phải là `flat` hoặc `tree`.

---

## Part 3 — Data Interaction

- **Operation 1**:
  - **Operation Type**: `SELECT`
  - **Target Table**: `learning_programs`
  - **Conditions**: `id = :learningProgramId AND deleted_at IS NULL`
  - **Expected Result**: 1 dòng dữ liệu.
- **Operation 2**:
  - **Operation Type**: `SELECT`
  - **Target Table**: `chapter_blocks`
  - **Conditions**: `learning_program_id = :learningProgramId AND deleted_at IS NULL ORDER BY sort_order ASC, created_at ASC`
  - **Expected Result**: Danh sách $0 \dots N$ node khối chương.

---

## Part 4 — Operational Notes

- **Caching**: Có thể sử dụng Redis cache với key `learning-program:chapter-blocks:{learningProgramId}`.
- **Performance**: Truy vấn toàn bộ node một lần và xây dựng cây trong bộ nhớ (thay vì truy vấn đệ quy nhiều lần).
- **Monitoring**: Theo dõi latency và số lượng node trung bình trên mỗi chương trình học.
