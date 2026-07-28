# API Blueprint — Question Option Management & Reordering

Tài liệu thiết kế API Blueprint cho các nghiệp vụ quản lý Danh sách Lựa chọn/Đáp án (Question Option) và Sắp xếp thứ tự trong dịch vụ `content-builder`.
Tuân thủ tiêu chuẩn kiến trúc `/api/{version}/{service}/{object}/` và đặc tả `rules/docx/java/api-blueprint-generator.md`.

---

# 1. Thêm Đáp Án Vào Câu Hỏi (Add Question Option)

## Part 0 — Classification & Identity

- **API Name**: Add Question Option
- **API Type**: Internal / Public
- **Module**: `content-builder`
- **Feature**: Question Option Management
- **Description**: Thêm một lựa chọn/đáp án mới vào một câu hỏi đã tồn tại. Nếu không truyền `sortOrder`, hệ thống tự động tính toán gán vào vị trí cuối cùng trong danh sách đáp án hiện tại.
- **Related Tables**: `questions`, `question_options`
- **Related Services**: `user-service`

---

## Part 1 — API Contract

### Endpoint

- **HTTP Method**: `POST`
- **URL**: `/api/v1/content-builder/questions/{questionId}/options`
- **Content Type**: `application/json`

### Request

#### Path Variables
| Name | Type | Required | Description | Validation Rules |
|---|---|---|---|---|
| `questionId` | String (UUID) | Yes | Định danh câu hỏi cha | UUIDv4/UUIDv7 |

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
| `content` | String | Yes | Nội dung chi tiết của lựa chọn đáp án | Không được để trống, tối đa 10,000 ký tự |
| `sortOrder` | Integer | No | Thứ tự hiển thị trong câu hỏi | Số nguyên >= 0. Nếu bỏ trống, tự động gán bằng `MAX(sort_order) + 1` |
| `isCorrect` | Boolean | Yes | Đánh dấu đây là đáp án đúng | `true` hoặc `false` |
| `status` | String | No | Trạng thái của đáp án | `DRAFT`, `PUBLISHED`, `ARCHIVED`, `HIDDEN`. Mặc định: `DRAFT` |
| `metadata` | Object | No | Dữ liệu mở rộng | Chuẩn JSONB hợp lệ, mặc định `{}` |

---

### Response

- **Success Status**: `201 Created`

#### Response Body
| Name | Type | Description |
|---|---|---|
| `id` | String (UUID) | Định danh duy nhất của đáp án vừa tạo |
| `questionId` | String (UUID) | ID câu hỏi cha |
| `content` | String | Nội dung đáp án |
| `sortOrder` | Integer | Thứ tự hiển thị thực tế đã lưu |
| `isCorrect` | Boolean | Cờ đúng/sai |
| `status` | String | Trạng thái |
| `metadata` | Object | Dữ liệu mở rộng |
| `createdBy` | String (UUID) | ID người tạo |
| `createdAt` | String (ISO-8601)| Thời điểm tạo |

---

### Error Codes

| Error Code | HTTP Status | Business Meaning | Client Message |
|---|---|---|---|
| `AUTH-401` | 401 Unauthorized | JWT token không hợp lệ | Authentication required. |
| `AUTH-403` | 403 Forbidden | Không có quyền thêm đáp án | You do not have permission to modify this question. |
| `RES-404` | 404 Not Found | Câu hỏi cha không tồn tại hoặc đã bị xóa mềm | Parent question not found. |
| `VAL-400` | 400 Bad Request | Payload không hợp lệ | Invalid request payload. |
| `VAL-422` | 422 Unprocessable Entity | Vi phạm quy tắc nghiệp vụ (VD: Thêm đáp án đúng `isCorrect=true` vào câu hỏi `SINGLE_CHOICE` mà trước đó đã có 1 đáp án đúng khác đang tồn tại) | Cannot add correct option: Single choice question already has a correct answer. |
| `SYS-500` | 500 Internal Server Error | Lỗi hệ thống | An unexpected internal server error occurred. |

---

## Part 2 — Processing Specification

1. **Controller Layer**:
   - Nhận POST request tại `/api/v1/content-builder/questions/{questionId}/options`.
   - Validate Path Variable `questionId` và Request Body.
   - Trích xuất `userId`, gọi Service Layer: `addOption(questionId, request, userId)`.

2. **Service Layer**:
   - Truy vấn kiểm tra sự tồn tại của Question theo `questionId` (`deleted_at IS NULL`). Nếu không thấy ném lỗi `RES-404`.
   - Kiểm tra quyền chỉnh sửa câu hỏi này.
   - Kiểm tra quy tắc nghiệp vụ:
     - Nếu `isCorrect == true` và `question.type == "SINGLE_CHOICE"`: Đếm số đáp án đúng hiện có trong DB của câu hỏi này (`is_correct = true AND deleted_at IS NULL`). Nếu `count >= 1`, ném lỗi vi phạm nghiệp vụ (`VAL-422`) hoặc yêu cầu client sửa đáp án cũ trước.
   - Nếu `sortOrder` là null: Thực hiện câu lệnh SELECT MAX(sort_order) từ `question_options` theo `question_id`, gán `sortOrder = max + 1` (mặc định 0 nếu chưa có đáp án nào).
   - Khởi tạo thực thể QuestionOption mới, gán `id = gen_random_uuid()`, `questionId = questionId`, `createdBy = userId`, `createdAt = CURRENT_TIMESTAMP`.
   - Lưu xuống DB qua Repository Layer.
   - Evict cache chi tiết câu hỏi (`question:detail:{questionId}`) khỏi Redis.
   - Trả về Response DTO.

3. **Repository Layer**:
   - Thực hiện lệnh SELECT kiểm tra câu hỏi cha.
   - Thực hiện lệnh SELECT MAX(sort_order).
   - Thực hiện lệnh INSERT vào bảng `question_options`.

4. **External Interaction**:
   - None.

5. **Validation**:
   - Kiểm tra quy tắc số lượng đáp án đúng tối đa theo `question.type`.

---

## Part 3 — Data Interaction

- **Operation 1**:
  - **Operation Type**: `SELECT`
  - **Target Table**: `questions`
  - **Conditions**: `id = :questionId AND deleted_at IS NULL`
  - **Expected Result**: 1 dòng dữ liệu câu hỏi cha.
- **Operation 2**:
  - **Operation Type**: `SELECT`
  - **Target Table**: `question_options`
  - **Conditions**: `question_id = :questionId AND deleted_at IS NULL` (Để tính max sort_order hoặc kiểm tra is_correct)
  - **Expected Result**: Giá trị max sort_order hoặc số lượng đáp án đúng.
- **Operation 3**:
  - **Operation Type**: `INSERT`
  - **Target Table**: `question_options`
  - **Conditions**: None
  - **Expected Result**: Tạo 1 bản ghi đáp án mới.

---

## Part 4 — Operational Notes

- **Cache Eviction**: Xóa cache của key `question:detail:{questionId}` để lần tra cứu câu hỏi tiếp theo có phản ánh đúng danh sách đáp án mới.
- **Audit Logging**: Ghi log thêm đáp án.

---
---

# 2. Cập Nhật Đáp Án (Update Question Option)

## Part 0 — Classification & Identity

- **API Name**: Update Question Option
- **API Type**: Internal / Public
- **Module**: `content-builder`
- **Feature**: Question Option Management
- **Description**: Cập nhật thông tin chi tiết của một lựa chọn/đáp án trong câu hỏi (nội dung, cờ đúng/sai, thứ tự, trạng thái...).
- **Related Tables**: `question_options`, `questions`
- **Related Services**: `user-service`

---

## Part 1 — API Contract

### Endpoint

- **HTTP Method**: `PUT`
- **URL**: `/api/v1/content-builder/questions/{questionId}/options/{optionId}`
- **Content Type**: `application/json`

### Request

#### Path Variables
| Name | Type | Required | Description | Validation Rules |
|---|---|---|---|---|
| `questionId` | String (UUID) | Yes | Định danh câu hỏi cha | UUIDv4/UUIDv7 |
| `optionId` | String (UUID) | Yes | Định danh đáp án cần sửa | UUIDv4/UUIDv7 |

#### Request Body
| Name | Type | Required | Description | Validation Rules |
|---|---|---|---|---|
| `content` | String | Yes | Nội dung đáp án | Không được để trống, tối đa 10,000 ký tự |
| `sortOrder` | Integer | Yes | Thứ tự hiển thị | Số nguyên >= 0 |
| `isCorrect` | Boolean | Yes | Đánh dấu đúng/sai | `true` hoặc `false` |
| `status` | String | Yes | Trạng thái | `DRAFT`, `PUBLISHED`, `ARCHIVED`, `HIDDEN` |
| `metadata` | Object | No | Dữ liệu mở rộng | Chuẩn JSONB |

---

### Response

- **Success Status**: `200 OK`
- **Response Body**: Trả về chi tiết đối tượng Option sau khi cập nhật thành công (giống Response Body của API Add Question Option).

---

### Error Codes

| Error Code | HTTP Status | Business Meaning | Client Message |
|---|---|---|---|
| `AUTH-401` | 401 Unauthorized | JWT token không hợp lệ | Authentication required. |
| `AUTH-403` | 403 Forbidden | Không có quyền sửa đáp án | You do not have permission to modify this option. |
| `RES-404` | 404 Not Found | Đáp án hoặc câu hỏi cha không tồn tại | Option not found in the specified question. |
| `VAL-400` | 400 Bad Request | Payload không hợp lệ | Invalid request payload. |
| `VAL-422` | 422 Unprocessable Entity | Vi phạm quy tắc nghiệp vụ (VD: Chuyển đáp án từ sai thành đúng `isCorrect=true` trong câu hỏi `SINGLE_CHOICE` đang có 1 đáp án đúng khác) | Cannot change option to correct: Single choice question already has another correct answer. |
| `SYS-500` | 500 Internal Server Error | Lỗi hệ thống | An unexpected internal server error occurred. |

---

## Part 2 — Processing Specification

1. **Controller Layer**:
   - Nhận PUT request tại `/api/v1/content-builder/questions/{questionId}/options/{optionId}`.
   - Validate UUID và payload, trích xuất `userId`.
   - Gọi Service Layer: `updateOption(questionId, optionId, request, userId)`.

2. **Service Layer**:
   - Truy vấn bản ghi QuestionOption theo `id = optionId` và `question_id = questionId` (`deleted_at IS NULL`). Nếu không thấy ném lỗi `RES-404`.
   - Kiểm tra quyền hạn của người dùng.
   - Kiểm tra nghiệp vụ: Nếu thay đổi `isCorrect` từ `false` sang `true` trên câu hỏi `SINGLE_CHOICE`, cần xác minh không có đáp án nào khác đang là `true`.
   - Cập nhật thông tin vào thực thể QuestionOption, gán `updatedBy = userId`.
   - Lưu xuống DB qua Repository Layer.
   - Evict cache `question:detail:{questionId}`.
   - Trả về DTO.

3. **Repository Layer**:
   - Thực hiện lệnh UPDATE trên bảng `question_options`.

4. **External Interaction**:
   - None.

5. **Validation**:
   - Kiểm tra tính nhất quán giữa `questionId` trong URL và `question_id` trong DB.

---

## Part 3 — Data Interaction

- **Operation 1**:
  - **Operation Type**: `SELECT`
  - **Target Table**: `question_options` (có thể JOIN `questions` để lấy type)
  - **Conditions**: `id = :optionId AND question_id = :questionId AND deleted_at IS NULL`
  - **Expected Result**: 1 dòng dữ liệu đáp án.
- **Operation 2**:
  - **Operation Type**: `UPDATE`
  - **Target Table**: `question_options`
  - **Conditions**: `id = :optionId`
  - **Expected Result**: Cập nhật nội dung, `is_correct`, `sort_order`, `status`, `updated_by`, `updated_at`.

---

## Part 4 — Operational Notes

- **Cache Eviction**: Xóa cache Redis của `question:detail:{questionId}`.

---
---

# 3. Xóa Đáp Án (Delete Question Option)

## Part 0 — Classification & Identity

- **API Name**: Delete Question Option
- **API Type**: Internal / Public
- **Module**: `content-builder`
- **Feature**: Question Option Management
- **Description**: Thực hiện xóa mềm một lựa chọn/đáp án khỏi câu hỏi bằng cách gán `deleted_at = CURRENT_TIMESTAMP`. Nếu câu hỏi đang ở trạng thái `PUBLISHED` và việc xóa đáp án này khiến câu hỏi bị vi phạm quy tắc (VD: câu trắc nghiệm không còn đáp án đúng nào, hoặc dưới 2 đáp án), hệ thống sẽ ném lỗi ngăn chặn hoặc tự động chuyển trạng thái câu hỏi về `DRAFT`.
- **Related Tables**: `question_options`, `questions`
- **Related Services**: `user-service`

---

## Part 1 — API Contract

### Endpoint

- **HTTP Method**: `DELETE`
- **URL**: `/api/v1/content-builder/questions/{questionId}/options/{optionId}`
- **Content Type**: None

### Request

#### Path Variables
| Name | Type | Required | Description | Validation Rules |
|---|---|---|---|---|
| `questionId` | String (UUID) | Yes | Định danh câu hỏi cha | UUIDv4/UUIDv7 |
| `optionId` | String (UUID) | Yes | Định danh đáp án cần xóa | UUIDv4/UUIDv7 |

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
| `AUTH-403` | 403 Forbidden | Không có quyền xóa đáp án | You do not have permission to delete this option. |
| `RES-404` | 404 Not Found | Đáp án không tồn tại hoặc đã bị xóa mềm | Option not found. |
| `VAL-422` | 422 Unprocessable Entity | Vi phạm quy tắc xuất bản của câu hỏi cha (VD: Xóa đáp án đúng duy nhất của câu hỏi đang `PUBLISHED`) | Cannot delete option: Published question would lack a correct answer. |
| `SYS-500` | 500 Internal Server Error | Lỗi hệ thống | An unexpected internal server error occurred. |

---

## Part 2 — Processing Specification

1. **Controller Layer**:
   - Nhận DELETE request tại `/api/v1/content-builder/questions/{questionId}/options/{optionId}`.
   - Validate UUIDs, trích xuất `userId`.
   - Gọi Service Layer: `deleteOption(questionId, optionId, userId)`.

2. **Service Layer**:
   - Truy vấn bản ghi QuestionOption theo `id` và `question_id` (`deleted_at IS NULL`). Nếu không thấy ném `RES-404`.
   - Kiểm tra quyền xóa của người dùng.
   - Kiểm tra câu hỏi cha: Nếu câu hỏi đang có `status == "PUBLISHED"`:
     - Kiểm tra nếu đáp án bị xóa là đáp án đúng duy nhất, hoặc làm cho số lượng đáp án còn lại < 2: Ném lỗi `VAL-422` yêu cầu chuyển câu hỏi về DRAFT trước khi xóa, hoặc tự động cảnh báo.
   - Khởi tạo giao dịch (@Transactional): Gán `option.deletedAt = CURRENT_TIMESTAMP`, `option.updatedBy = userId`.
   - Lưu xuống DB qua Repository Layer.
   - Evict cache `question:detail:{questionId}`.

3. **Repository Layer**:
   - Thực hiện UPDATE soft delete trên bảng `question_options`.

4. **External Interaction**:
   - None.

5. **Validation**:
   - Kiểm tra ràng buộc số lượng đáp án hợp lệ cho câu hỏi `PUBLISHED`.

---

## Part 3 — Data Interaction

- **Operation 1**:
  - **Operation Type**: `UPDATE`
  - **Target Table**: `question_options`
  - **Conditions**: `id = :optionId AND question_id = :questionId AND deleted_at IS NULL`
  - **Expected Result**: Gán `deleted_at = CURRENT_TIMESTAMP`, `updated_by = :userId`.

---

## Part 4 — Operational Notes

- **Idempotency**: Gọi DELETE nhiều lần cho cùng 1 đáp án đã bị soft delete sẽ trả về `404 Not Found`.
- **Cache Eviction**: Evict key `question:detail:{questionId}` khỏi Redis.

---
---

# 4. Đổi Thứ Tự Đáp Án (Reorder Question Options)

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
