# Thêm Khối Câu Hỏi Vào Chương Học (Add Question Block)

## Part 0 — Classification & Identity

- **API Name**: Add Question Block
- **API Type**: Internal / Public
- **Module**: `content-builder`
- **Feature**: Question Block Management
- **Description**: Thêm một node mới vào cấu trúc cây câu hỏi của một chương học. Node có thể là container (nhóm câu hỏi) hoặc leaf node (gắn với một question cụ thể thông qua `questionId`). Nếu không truyền `sortOrder`, hệ thống tự động tính toán gán vào vị trí cuối cùng trong danh sách các node cùng cấp (cùng `parentId`).
- **Related Tables**: `chapters`, `question_blocks`, `questions`
- **Related Services**: `user-service`

---

## Part 1 — API Contract

### Endpoint

- **HTTP Method**: `POST`
- **URL**: `/api/v1/content-builder/chapters/{chapterId}/question-blocks`
- **Content Type**: `application/json`

### Request

#### Path Variables
| Name | Type | Required | Description | Validation Rules |
|---|---|---|---|---|
| `chapterId` | String (UUID) | Yes | Định danh chương học cha | UUIDv4/UUIDv7 |

#### Query Parameters
- None

#### Headers
| Name | Type | Required | Description | Validation Rules |
|---|---|---|---|---|
| `Authorization` | String | Yes | Bearer JWT Token định danh người dùng | Chuẩn RFC 6750 Bearer Token, token hợp lệ, chưa hết hạn |
| `X-Request-ID` | String | No | Trace ID định danh luồng request | UUIDv4, tối đa 64 ký tự |

#### Request Body
| Name | Type | Required | Description | Validation Rules |
|---|---|---|---|---|
| `parentId` | String (UUID) | No | ID node cha trong cây phân cấp (NULL nếu là node gốc) | Chuẩn UUID, phải tồn tại trong bảng `question_blocks` cùng `chapterId` và chưa bị xóa mềm |
| `questionId` | String (UUID) | No | ID question được gắn vào node này (NULL nếu node chỉ là container nhóm) | Chuẩn UUID, phải tồn tại trong bảng `questions` và chưa bị xóa mềm |
| `title` | String | Yes | Tiêu đề hiển thị của node | Không được để trống, tối đa 255 ký tự |
| `sortOrder` | Integer | No | Thứ tự hiển thị trong cùng cấp | Số nguyên >= 0. Nếu bỏ trống, tự động gán bằng `MAX(sort_order) + 1` |
| `status` | String | No | Trạng thái của node | `DRAFT`, `PUBLISHED`, `ARCHIVED`, `HIDDEN`. Mặc định: `DRAFT` |
| `metadata` | Object | No | Dữ liệu mở rộng | Chuẩn JSONB hợp lệ, mặc định `{}` |

---

### Response

- **Success Status**: `201 Created`

#### Response Body
| Name | Type | Description |
|---|---|---|
| `id` | String (UUID) | Định danh duy nhất của node vừa tạo |
| `chapterId` | String (UUID) | ID chương học cha |
| `parentId` | String (UUID) | ID node cha (NULL nếu node gốc) |
| `questionId` | String (UUID) | ID câu hỏi được gắn (NULL nếu container) |
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
| `AUTH-403` | 403 Forbidden | Không có quyền thêm khối câu hỏi | You do not have permission to modify this chapter. |
| `RES-404` | 404 Not Found | Chương học cha không tồn tại hoặc đã bị xóa mềm | Parent chapter not found. |
| `VAL-400` | 400 Bad Request | Payload không hợp lệ | Invalid request payload. |
| `VAL-422` | 422 Unprocessable Entity | Vi phạm quy tắc nghiệp vụ | Invalid parent block or question reference. |
| `SYS-500` | 500 Internal Server Error | Lỗi hệ thống | An unexpected internal server error occurred. |

---

## Part 2 — Processing Specification

1. **Controller Layer**:
   - Nhận POST request tại `/api/v1/content-builder/chapters/{chapterId}/question-blocks`.
   - Validate Path Variable `chapterId` và Request Body.
   - Trích xuất `userId`, gọi Service Layer: `addQuestionBlock(chapterId, request, userId)`.

2. **Service Layer**:
   - Truy vấn kiểm tra sự tồn tại của Chapter theo `chapterId` (`deleted_at IS NULL`). Nếu không thấy ném lỗi `RES-404`.
   - Kiểm tra quyền chỉnh sửa chương học này (`chapters:update` permission).
   - Kiểm tra quy tắc nghiệp vụ:
     - Nếu `parentId` được cung cấp: Kiểm tra sự tồn tại trong `question_blocks` với `chapter_id = chapterId AND deleted_at IS NULL`.
     - Nếu `questionId` được cung cấp: Kiểm tra sự tồn tại của câu hỏi trong bảng `questions` với `deleted_at IS NULL`.
   - Nếu `sortOrder` là null: SELECT MAX(sort_order) từ `question_blocks` theo `chapter_id = chapterId AND parent_id = parentId`.
   - Khởi tạo thực thể QuestionBlock mới.
   - Lưu xuống DB qua Repository Layer.
   - Evict cache chi tiết chương học (`chapter:detail:{chapterId}`) khỏi Redis.
   - Trả về Response DTO.

3. **Repository Layer**:
   - Các thao tác SELECT kiểm tra tồn tại.
   - Thực hiện lệnh INSERT vào bảng `question_blocks`.

4. **External Interaction**:
   - None.
