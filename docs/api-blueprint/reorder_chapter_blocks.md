# Đổi Thứ Tự Khối Chương (Reorder Chapter Blocks)

## Part 0 — Classification & Identity

- **API Name**: Reorder Chapter Blocks
- **API Type**: Internal / Public
- **Module**: `content-builder`
- **Feature**: Chapter Block Management
- **Description**: Sắp xếp lại thứ tự hiển thị (`sort_order`) hàng loạt cho các node khối chương cùng cấp trong một chương trình học. Nhận vào danh sách cặp giá trị ID node và số thứ tự mới tương ứng trong cùng một giao dịch (transaction) để đảm bảo tính nhất quán.
- **Related Tables**: `chapter_blocks`
- **Related Services**: `user-service`

---

## Part 1 — API Contract

### Endpoint

- **HTTP Method**: `PUT`
- **URL**: `/api/v1/content-builder/learning-programs/{learningProgramId}/chapter-blocks/reorder`
- **Content Type**: `application/json`

### Request

#### Path Variables
| Name | Type | Required | Description | Validation Rules |
|---|---|---|---|---|
| `learningProgramId` | String (UUID) | Yes | Định danh chương trình học cha | UUIDv4/UUIDv7 |

#### Headers
| Name | Type | Required | Description | Validation Rules |
|---|---|---|---|---|
| `Authorization` | String | Yes | Bearer JWT Token định danh người dùng | Chuẩn RFC 6750 Bearer Token, token hợp lệ, chưa hết hạn |
| `X-Request-ID` | String | No | Trace ID định danh luồng request (Tạm thời client hoặc backend tự sinh UUID do chưa có Gateway) | UUIDv4, tối đa 64 ký tự |

#### Request Body
- Là một mảng JSON các đối tượng chỉ định thứ tự mới (`Array<ReorderItem>`):

| Name | Type | Required | Description | Validation Rules |
|---|---|---|---|---|
| `id` | String (UUID) | Yes | ID của khối chương cần đổi thứ tự | Phải thuộc về `learningProgramId`, chưa bị xóa mềm |
| `sortOrder` | Integer | Yes | Thứ tự hiển thị mới | Số nguyên >= 0, không được trùng lặp trong cùng mảng |

**Ví dụ Request Body:**
```json
[
  { "id": "d3b07384-d113-4c4e-9c81-bcc31f31a231", "sortOrder": 0 },
  { "id": "e4c18495-e224-5d5f-0d92-cdd42g42b342", "sortOrder": 1 },
  { "id": "f5d29506-f335-6e6g-1e03-dee53h53c453", "sortOrder": 2 }
]
```

---

### Response

- **Success Status**: `200 OK`

#### Response Body
- Trả về danh sách toàn bộ các khối chương đã được sắp xếp lại thứ tự mới (`Array<ChapterBlockDetail>` sắp xếp theo `sort_order ASC`).

---

### Error Codes

| Error Code | HTTP Status | Business Meaning | Client Message |
|---|---|---|---|
| `AUTH-401` | 401 Unauthorized | JWT token không hợp lệ | Authentication required. |
| `AUTH-403` | 403 Forbidden | Không có quyền chỉnh sửa chương trình học này | You do not have permission to modify this learning program. |
| `RES-404` | 404 Not Found | Có ID khối chương trong danh sách không tồn tại hoặc không thuộc về chương trình học này | One or more chapter blocks not found in this learning program. |
| `VAL-400` | 400 Bad Request | Payload rỗng hoặc có giá trị `sortOrder` âm | Invalid reorder request payload. |
| `VAL-422` | 422 Unprocessable Entity | Danh sách ID gửi lên bị lặp, có chỉ số `sortOrder` bị trùng lặp, hoặc các khối chương không cùng một cấp (không cùng `parent_id`) | Invalid payload: Duplicate IDs, duplicate sortOrders, or blocks are not siblings. |
| `SYS-500` | 500 Internal Server Error | Lỗi hệ thống | An unexpected internal server error occurred. |

---

## Part 2 — Processing Specification

1. **Controller Layer**:
   - Nhận PUT request tại `/api/v1/content-builder/learning-programs/{learningProgramId}/chapter-blocks/reorder`.
   - Validate danh sách mảng không rỗng, không chứa phần tử null.
   - Trích xuất `userId`, gọi Service Layer: `reorderChapterBlocks(learningProgramId, requestList, userId)`.

2. **Service Layer**:
   - Kiểm tra quyền chỉnh sửa chương trình học của người dùng (`learning-programs:update` permission).
   - Kiểm tra validation trong bộ nhớ:
     - Tập hợp danh sách các `id` trong payload không được có phần tử trùng lặp.
     - Tập hợp các `sortOrder` không được trùng lặp.
   - Khởi tạo giao dịch (@Transactional):
     - Truy vấn tất cả `chapter_blocks` thuộc `learningProgramId` (`deleted_at IS NULL`) có `id IN (:payloadIds)`.
     - Nếu số lượng bản ghi tra cứu được trong DB không bằng số lượng ID gửi lên: Ném lỗi `RES-404`.
     - Kiểm tra tất cả các bản ghi tìm được phải có cùng `parent_id` (hoặc cùng là NULL) để đảm bảo chúng là các node cùng cấp. Nếu vi phạm, ném lỗi `VAL-422`.
     - Lặp qua từng thực thể ChapterBlock tìm được, cập nhật `sortOrder = item.sortOrder`, `updatedBy = userId`.
     - Thực hiện lưu batch (saveAll) qua Repository Layer.
   - Commit giao dịch.
   - Evict cache `learning-program:detail:{learningProgramId}` khỏi Redis.
   - Truy vấn lại danh sách chapter_blocks đã sắp xếp và trả về DTO.

3. **Repository Layer**:
   - Thực hiện lệnh SELECT IN theo danh sách ID.
   - Thực hiện lệnh UPDATE batch trên bảng `chapter_blocks`.

4. **External Interaction**:
   - None.

5. **Validation**:
   - Kiểm tra tính hợp lệ và độc nhất của tập ID và chỉ số sắp xếp.

---

## Part 3 — Data Interaction

- **Operation 1**:
  - **Operation Type**: `SELECT`
  - **Target Table**: `chapter_blocks`
  - **Conditions**: `learning_program_id = :learningProgramId AND id IN (:ids) AND deleted_at IS NULL`
  - **Expected Result**: Danh sách các bản ghi tương ứng với ID gửi lên.
- **Operation 2**:
  - **Operation Type**: `UPDATE`
  - **Target Table**: `chapter_blocks`
  - **Conditions**: `id = :id` (cho từng khối chương)
  - **Expected Result**: Cập nhật `sort_order`, `updated_by`, `updated_at`.

---

## Part 4 — Operational Notes

- **Transaction Boundary**: Toàn bộ thao tác cập nhật thứ tự phải diễn ra trong 1 giao dịch cô lập để đảm bảo không xảy ra tình trạng thứ tự bị lệch khi có lỗi giữa chừng.
- **Cache Eviction**: Evict cache của key `learning-program:detail:{learningProgramId}`.
- **Tracing**: Truyền dẫn `traceId` qua MDC logging.
