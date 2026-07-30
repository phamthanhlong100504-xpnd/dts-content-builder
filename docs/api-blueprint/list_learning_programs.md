# Lấy Danh Sách Chương Trình Học (List Learning Programs)

## Part 0 — Classification & Identity

- **API Name**: List Learning Programs
- **API Type**: Internal / Public
- **Module**: `content-builder`
- **Feature**: Learning Program Management
- **Description**: Tra cứu và lấy danh sách chương trình học theo cơ chế phân trang (Pagination), hỗ trợ lọc theo từ khóa (title), mã chương trình (code), trạng thái, người tạo.
- **Related Tables**: `learning_programs`
- **Related Services**: None

---

## Part 1 — API Contract

### Endpoint

- **HTTP Method**: `GET`
- **URL**: `/api/v1/content-builder/learning-programs`
- **Content Type**: `application/json`

### Request

#### Path Variables
- None

#### Query Parameters
| Name | Type | Required | Description | Validation Rules |
|---|---|---|---|---|
| `page` | Integer | No | Số trang truy vấn (0-indexed) | >= 0, mặc định: `0` |
| `size` | Integer | No | Kích thước trang | Từ 1 đến 100, mặc định: `20` |
| `sort` | String | No | Tiêu chí sắp xếp | VD: `createdAt,desc` hoặc `title,asc`. Mặc định: `createdAt,desc` |
| `keyword` | String | No | Từ khóa tìm kiếm tương đối trong tiêu đề (`title ILIKE %keyword%`) | Tối đa 100 ký tự |
| `code` | String | No | Lọc theo mã chương trình (khớp chính xác) | Tối đa 100 ký tự |
| `status` | String | No | Lọc theo trạng thái | `DRAFT`, `PUBLISHED`, `ARCHIVED`, `HIDDEN` |
| `createdBy` | String (UUID) | No | Lọc theo ID tài khoản người tạo | Định dạng UUID |

#### Headers
| Name | Type | Required | Description | Validation Rules |
|---|---|---|---|---|
| `Authorization` | String | Yes | Bearer JWT Token | Hợp lệ |

---

### Response

- **Success Status**: `200 OK`

#### Response Body
| Name | Type | Description |
|---|---|---|
| `content` | Array<Object> | Danh sách tóm tắt các chương trình trong trang hiện tại |
| `pageNumber` | Integer | Chỉ số trang hiện tại |
| `pageSize` | Integer | Số lượng bản ghi tối đa trên mỗi trang |
| `totalElements` | Long | Tổng số lượng bản ghi thỏa mãn điều kiện lọc |
| `totalPages` | Integer | Tổng số trang |
| `last` | Boolean | Cờ đánh dấu đây có phải là trang cuối cùng hay không |

*(Mỗi phần tử trong mảng `content` trả về các thông tin chung: `id`, `title`, `code`, `description`, `status`, `createdBy`, `createdAt`, `updatedAt`)*.

---

### Error Codes

| Error Code | HTTP Status | Business Meaning | Client Message |
|---|---|---|---|
| `AUTH-401` | 401 Unauthorized | JWT token không hợp lệ | Authentication required. |
| `AUTH-403` | 403 Forbidden | Người dùng không có quyền xem danh sách chương trình học | You do not have permission to view learning programs. |
| `VAL-400` | 400 Bad Request | Tham số phân trang hoặc sắp xếp không hợp lệ | Invalid pagination or filtering parameters. |
| `SYS-500` | 500 Internal Server Error | Lỗi hệ thống | An unexpected internal server error occurred. |

---

## Part 2 — Processing Specification

1. **Controller Layer**:
   - Nhận GET request tại `/api/v1/content-builder/learning-programs`.
   - Tạo đối tượng `Pageable` từ `page`, `size`, `sort`.
   - Gọi Service Layer: `listLearningPrograms(filterCriteria, pageable)`.

2. **Service Layer**:
   - Kiểm tra quyền đọc (`learning-programs:read` permission).
   - Xây dựng Specification lọc theo:
     - `deleted_at IS NULL` (luôn luôn bắt buộc).
     - `title ILIKE %:keyword%` (nếu có `keyword`).
     - `code = :code` (nếu có `code`).
     - `status = :status` (nếu có `status`).
     - `created_by = :createdBy` (nếu có `createdBy`).
   - Gọi Repository truy vấn phân trang.
   - Chuyển đổi sang đối tượng Page DTO và trả về.

3. **Repository Layer**:
   - Thực hiện lệnh SELECT COUNT(*) và SELECT phân trang trên bảng `learning_programs`.

4. **External Interaction**:
   - None.

5. **Validation**:
   - Kiểm tra `page >= 0`, `size <= 100`.

---

## Part 3 — Data Interaction

- **Operation 1**:
  - **Operation Type**: `SELECT`
  - **Target Table**: `learning_programs`
  - **Conditions**: `deleted_at IS NULL` kết hợp các điều kiện lọc động, `ORDER BY :sort LIMIT :size OFFSET :offset`
  - **Expected Result**: Trang dữ liệu chương trình và tổng số bản ghi.

---

## Part 4 — Operational Notes

- **Performance**: Đảm bảo các cột được lọc thường xuyên (`status`, `code`, `created_by`) đã được tạo Index trên bảng `learning_programs`. Index `uq_learning_programs_code` hỗ trợ tra cứu theo `code`.
- **Tenant Isolation**: Bổ sung điều kiện lọc theo tenant nếu bật multi-tenancy.
