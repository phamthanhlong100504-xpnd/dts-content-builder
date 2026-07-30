# API Blueprint — List Questions

Tài liệu thiết kế API Blueprint cho nghiệp vụ Tra cứu và Lấy Danh sách Câu hỏi (List Questions) trong dịch vụ `content-builder`.
Tuân thủ tiêu chuẩn kiến trúc `/api/{version}/{service}/{object}/` và đặc tả `rules/docx/java/api-blueprint-generator.md`.

---

## Part 0 — Classification & Identity

- **API Name**: List Questions
- **API Type**: Internal / Public
- **Module**: `content-builder`
- **Feature**: Question Management
- **Description**: Tra cứu và lấy danh sách câu hỏi theo cơ chế phân trang (Pagination), hỗ trợ lọc theo từ khóa (content), loại câu hỏi, trạng thái, người tạo hoặc chương/chủ đề bài học liên kết.
- **Related Tables**: `questions`, `question_blocks`
- **Related Services**: None

---

## Part 1 — API Contract

### Endpoint

- **HTTP Method**: `GET`
- **URL**: `/api/v1/content-builder/questions`
- **Content Type**: `application/json`

### Request

#### Path Variables
- None

#### Query Parameters
| Name | Type | Required | Description | Validation Rules |
|---|---|---|---|---|
| `page` | Integer | No | Số trang truy vấn (0-indexed) | >= 0, mặc định: `0` |
| `size` | Integer | No | Kích thước trang | Từ 1 đến 100, mặc định: `20` |
| `sort` | String | No | Tiêu chí sắp xếp | VD: `createdAt,desc` hoặc `type,asc`. Mặc định: `createdAt,desc` |
| `keyword` | String | No | Từ khóa tìm kiếm tương đối trong nội dung câu hỏi (`content ILIKE %keyword%`) | Tối đa 100 ký tự |
| `type` | String | No | Lọc theo loại câu hỏi | `SINGLE_CHOICE`, `MULTIPLE_CHOICE`,... |
| `status` | String | No | Lọc theo trạng thái | `DRAFT`, `PUBLISHED`, `ARCHIVED`, `HIDDEN` |
| `createdBy` | String (UUID) | No | Lọc theo ID tài khoản người tạo | Định dạng UUID |
| `chapterId` | String (UUID) | No | Lọc danh sách câu hỏi được gắn trong 1 chapter cụ thể | Định dạng UUID |

#### Headers
| Name | Type | Required | Description | Validation Rules |
|---|---|---|---|---|
| `Authorization` | String | Yes | Bearer JWT Token định danh người dùng | Chuẩn RFC 6750 Bearer Token, token hợp lệ, chưa hết hạn |
| `X-Request-ID` | String | No | Trace ID định danh luồng request (Tạm thời client hoặc backend tự sinh UUID do chưa có Gateway) | UUIDv4, tối đa 64 ký tự |

---

### Response

- **Success Status**: `200 OK`

#### Response Body
| Name | Type | Description |
|---|---|---|
| `content` | Array<Object> | Danh sách tóm tắt các câu hỏi trong trang hiện tại |
| `pageNumber` | Integer | Chỉ số trang hiện tại |
| `pageSize` | Integer | Số lượng bản ghi tối đa trên mỗi trang |
| `totalElements` | Long | Tổng số lượng bản ghi thỏa mãn điều kiện lọc |
| `totalPages` | Integer | Tổng số trang |
| `last` | Boolean | Cờ đánh dấu đây có phải là trang cuối cùng hay không |

*(Mỗi phần tử trong mảng `content` trả về các thông tin chung: `id`, `type`, `content`, `status`, `createdBy`, `createdAt`, `updatedAt`)*.

---

### Error Codes

| Error Code | HTTP Status | Business Meaning | Client Message |
|---|---|---|---|
| `AUTH-401` | 401 Unauthorized | JWT token không hợp lệ | Authentication required. |
| `AUTH-403` | 403 Forbidden | Người dùng không có quyền xem danh sách câu hỏi | You do not have permission to view questions. |
| `VAL-400` | 400 Bad Request | Tham số phân trang hoặc sắp xếp không hợp lệ | Invalid pagination or filtering parameters. |
| `SYS-500` | 500 Internal Server Error | Lỗi hệ thống | An unexpected internal server error occurred. |

---

## Part 2 — Processing Specification

1. **Controller Layer**:
   - Nhận GET request tại `/api/v1/content-builder/questions`.
   - Tạo đối tượng `Pageable` từ `page`, `size`, `sort`.
   - Gọi Service Layer: `listQuestions(filterCriteria, pageable)`.

2. **Service Layer**:
   - Kiểm tra quyền đọc (`questions:read` permission).
   - Xây dựng Specification (JPA Criteria / QueryDSL) lọc theo:
     - `deleted_at IS NULL` (luôn luôn bắt buộc).
     - `content ILIKE %:keyword%` (nếu có `keyword`).
     - `type = :type` (nếu có `type`).
     - `status = :status` (nếu có `status`).
     - `created_by = :createdBy` (nếu có `createdBy`).
     - Nếu có tham số `chapterId`: Thực hiện JOIN hoặc IN query qua bảng `question_blocks` nơi `chapter_id = :chapterId AND deleted_at IS NULL AND question_id IS NOT NULL`.
   - Gọi Repository truy vấn phân trang.
   - Chuyển đổi sang đối tượng Page DTO và trả về.

3. **Repository Layer**:
   - Thực hiện lệnh SELECT COUNT(*) và SELECT phân trang trên bảng `questions` (có thể JOIN `question_blocks`).

4. **External Interaction**:
   - None.

5. **Validation**:
   - Kiểm tra `page >= 0`, `size <= 100`.

---

## Part 3 — Data Interaction

- **Operation 1**:
  - **Operation Type**: `SELECT`
  - **Target Table**: `questions` (hoặc JOIN `question_blocks`)
  - **Conditions**: `deleted_at IS NULL` kết hợp các điều kiện lọc động, `ORDER BY :sort LIMIT :size OFFSET :offset`
  - **Expected Result**: Trang dữ liệu câu hỏi và tổng số bản ghi.

---

## Part 4 — Operational Notes

- **Performance**: Đảm bảo các cột được lọc thường xuyên (`status`, `type`, `created_by`) đã được tạo Index trên bảng `questions`.
- **Tenant Isolation**: Bổ sung điều kiện lọc theo tenant nếu bật multi-tenancy.

- **Tracing**: Truyền dẫn `traceId` qua MDC logging. Do chưa có API Gateway, backend sẽ tự sinh UUID nếu client không truyền header `X-Request-ID`.
