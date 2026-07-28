# API Blueprint — Reorder Question Options

Tài liệu thiết kế API Blueprint cho nghiệp vụ Sắp xếp lại Thứ tự Đáp án (Reorder Question Options) trong dịch vụ `content-builder`.
Tuân thủ tiêu chuẩn kiến trúc `/api/{version}/{service}/{object}/` và đặc tả `rules/docx/java/api-blueprint-generator.md`.

---

## Part 0 — Classification & Identity

- **API Name**: Reorder Question Options
- **API Type**: Internal / Public
- **Module**: `content-builder`
- **Feature**: Question Option Management
- **Description**: Sắp xếp lại thứ tự hiển thị (`sort_order`) hàng loạt cho các đáp án của một câu hỏi. Nhận vào danh sách cặp giá trị ID đáp án và số thứ tự mới tương ứng trong cùng một giao dịch (transaction) để đảm bảo tính nhất quán.
- **Related Tables**: `question_options`
- **Related Services**: `user-service`

---

## Part 1 — API Contract

### Endpoint

- **HTTP Method**: `PUT`
- **URL**: `/api/v1/content-builder/questions/{questionId}/options/reorder`
- **Content Type**: `application/json`

### Request

#### Path Variables
| Name | Type | Required | Description | Validation Rules |
|---|---|---|---|---|
| `questionId` | String (UUID) | Yes | Định danh câu hỏi cha | UUIDv4/UUIDv7 |

#### Headers
| Name | Type | Required | Description | Validation Rules |
|---|---|---|---|---|
| `Authorization` | String | Yes | Bearer JWT Token định danh người dùng | Chuẩn RFC 6750 Bearer Token, token hợp lệ, chưa hết hạn |
| `X-Request-ID` | String | No | Trace ID định danh luồng request (Tạm thời client hoặc backend tự sinh UUID do chưa có Gateway) | UUIDv4, tối đa 64 ký tự |

#### Request Body
- Là một mảng JSON các đối tượng chỉ định thứ tự mới (`Array<ReorderItem>`):

| Name | Type | Required | Description | Validation Rules |
|---|---|---|---|---|
| `id` | String (UUID) | Yes | ID của đáp án cần đổi thứ tự | Phải thuộc về `questionId`, chưa bị xóa mềm |
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
- Trả về danh sách toàn bộ các đáp án của câu hỏi sau khi đã được sắp xếp lại thứ tự mới (`Array<OptionDetail>` sắp xếp theo `sort_order ASC`).

---

### Error Codes

| Error Code | HTTP Status | Business Meaning | Client Message |
|---|---|---|---|
| `AUTH-401` | 401 Unauthorized | JWT token không hợp lệ | Authentication required. |
| `AUTH-403` | 403 Forbidden | Không có quyền chỉnh sửa câu hỏi này | You do not have permission to modify this question. |
| `RES-404` | 404 Not Found | Có ID đáp án trong danh sách không tồn tại hoặc không thuộc về câu hỏi này | One or more options not found in this question. |
| `VAL-400` | 400 Bad Request | Payload rỗng hoặc có giá trị `sortOrder` âm | Invalid reorder request payload. |
| `VAL-422` | 422 Unprocessable Entity | Danh sách ID gửi lên bị lặp hoặc có chỉ số `sortOrder` bị trùng lặp | Duplicate IDs or sortOrder indices in request payload. |
| `SYS-500` | 500 Internal Server Error | Lỗi hệ thống | An unexpected internal server error occurred. |

---

## Part 2 — Processing Specification

1. **Controller Layer**:
   - Nhận PUT request tại `/api/v1/content-builder/questions/{questionId}/options/reorder`.
   - Validate danh sách mảng không rỗng, không chứa phần tử null.
   - Trích xuất `userId`, gọi Service Layer: `reorderOptions(questionId, requestList, userId)`.

2. **Service Layer**:
   - Kiểm tra quyền chỉnh sửa câu hỏi của người dùng.
   - Kiểm tra validation trong bộ nhớ:
     - Tập hợp danh sách các `id` trong payload không được có phần tử trùng lặp.
     - Tập hợp các `sortOrder` không được trùng lặp.
   - Khởi tạo giao dịch (@Transactional):
     - Truy vấn tất cả `question_options` thuộc `questionId` (`deleted_at IS NULL`) có `id IN (:payloadIds)`.
     - Nếu số lượng bản ghi tra cứu được trong DB không bằng số lượng ID gửi lên: Ném lỗi `RES-404` (có ID không hợp lệ hoặc đã bị xóa mềm).
     - Lặp qua từng thực thể Option tìm được, cập nhật `sortOrder = item.sortOrder`, `updatedBy = userId`.
     - Thực hiện lưu batch (saveAll) qua Repository Layer.
   - Commit giao dịch.
   - Evict cache `question:detail:{questionId}` khỏi Redis.
   - Truy vấn lại danh sách options đã sắp xếp và trả về DTO.

3. **Repository Layer**:
   - Thực hiện lệnh SELECT IN theo danh sách ID.
   - Thực hiện lệnh UPDATE batch trên bảng `question_options`.

4. **External Interaction**:
   - None.

5. **Validation**:
   - Kiểm tra tính hợp lệ và độc nhất của tập ID và chỉ số sắp xếp.

---

## Part 3 — Data Interaction

- **Operation 1**:
  - **Operation Type**: `SELECT`
  - **Target Table**: `question_options`
  - **Conditions**: `question_id = :questionId AND id IN (:ids) AND deleted_at IS NULL`
  - **Expected Result**: Danh sách các bản ghi tương ứng với ID gửi lên.
- **Operation 2**:
  - **Operation Type**: `UPDATE`
  - **Target Table**: `question_options`
  - **Conditions**: `id = :id` (cho từng đáp án)
  - **Expected Result**: Cập nhật `sort_order`, `updated_by`, `updated_at`.

---

## Part 4 — Operational Notes

- **Transaction Boundary**: Toàn bộ thao tác cập nhật thứ tự phải diễn ra trong 1 giao dịch cô lập để đảm bảo không xảy ra tình trạng thứ tự bị lệch khi có lỗi giữa chừng.
- **Cache Eviction**: Evict cache của key `question:detail:{questionId}`.

- **Tracing**: Truyền dẫn `traceId` qua MDC logging. Do chưa có API Gateway, backend sẽ tự sinh UUID nếu client không truyền header `X-Request-ID`.
