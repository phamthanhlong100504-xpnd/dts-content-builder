# API Blueprint — Question Management

Tài liệu thiết kế API Blueprint cho các nghiệp vụ quản lý Câu hỏi (Question) trong dịch vụ `content-builder`.
Tuân thủ tiêu chuẩn kiến trúc `/api/{version}/{service}/{object}/` và đặc tả `rules/docx/java/api-blueprint-generator.md`.

---

# 1. Tạo Câu Hỏi Nháp (Create Draft Question)

## Part 0 — Classification & Identity

- **API Name**: Create Draft Question
- **API Type**: Internal / Public
- **Module**: `content-builder`
- **Feature**: Question Management
- **Description**: Tạo mới bản ghi câu hỏi vào hệ thống với trạng thái mặc định là `DRAFT`. Hỗ trợ truyền kèm danh sách lựa chọn (`options`) trong Request Body để khởi tạo đồng thời câu hỏi và đáp án trong cùng một giao dịch (transaction).
- **Related Tables**: `questions`, `question_options`
- **Related Services**: `user-service` (xác thực người tạo), `media-service` (tham chiếu ID tệp đa phương tiện)

---

## Part 1 — API Contract

### Endpoint

- **HTTP Method**: `POST`
- **URL**: `/api/v1/content-builder/questions/draft`
- **Content Type**: `application/json`

### Request

#### Path Variables
- None

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
| `type` | String | Yes | Loại câu hỏi | Phải thuộc danh sách: `SINGLE_CHOICE`, `MULTIPLE_CHOICE`, `TRUE_FALSE`, `FILL_BLANK`, `ORDERING`, `MATCHING` |
| `content` | String | Yes | Nội dung câu hỏi (Văn bản thuần hoặc Markdown/HTML) | Không được để trống, độ dài từ 1 đến 50,000 ký tự |
| `explanations` | Object/Array | No | Cấu hình lời giải thích nhúng dạng JSON | Chuẩn JSONB hợp lệ |
| `mediaFileIds` | Array<String> | No | Danh sách UUID của các file media từ Media Service | Mảng các string chuẩn định dạng UUIDv4, tối đa 20 phần tử |
| `attachments` | Object/Array | No | Cấu hình file đính kèm dạng JSON | Chuẩn JSONB hợp lệ |
| `references` | Object/Array | No | Cấu hình nguồn tham khảo dạng JSON | Chuẩn JSONB hợp lệ |
| `metadata` | Object | No | Dữ liệu mở rộng (độ khó, từ khóa, tag,...) | Chuẩn JSONB hợp lệ, mặc định `{}` |
| `options` | Array<Object> | No | Danh sách các đáp án khởi tạo kèm theo | Mảng tối đa 50 phần tử. Nếu có truyền, tuân thủ validation rule của Option Item bên dưới |

**Cấu trúc từng đối tượng trong mảng `options` (Option Item):**
| Name | Type | Required | Description | Validation Rules |
|---|---|---|---|---|
| `content` | String | Yes | Nội dung chi tiết của lựa chọn đáp án | Không được để trống, tối đa 10,000 ký tự |
| `sortOrder` | Integer | No | Thứ tự hiển thị của đáp án | Số nguyên >= 0, mặc định bằng chỉ số index trong mảng |
| `isCorrect` | Boolean | Yes | Đánh dấu đây là đáp án đúng | `true` hoặc `false` |
| `metadata` | Object | No | Dữ liệu mở rộng riêng cho đáp án | Chuẩn JSONB hợp lệ, mặc định `{}` |

---

### Response

- **Success Status**: `201 Created`

#### Response Body
| Name | Type | Description |
|---|---|---|
| `id` | String (UUID) | Định danh duy nhất của câu hỏi vừa tạo |
| `type` | String | Loại câu hỏi |
| `content` | String | Nội dung câu hỏi |
| `explanations` | Object/Array | Lời giải thích |
| `mediaFileIds` | Array<String> | Danh sách UUID media đính kèm |
| `attachments` | Object/Array | Danh sách file đính kèm |
| `references` | Object/Array | Danh sách tham khảo |
| `status` | String | Trạng thái (Luôn trả về `DRAFT`) |
| `metadata` | Object | Dữ liệu mở rộng |
| `createdBy` | String (UUID) | ID người tạo |
| `createdAt` | String (ISO-8601)| Thời điểm tạo bản ghi (UTC) |
| `options` | Array<Object> | Danh sách chi tiết các đáp án vừa được tạo kèm (đầy đủ `id`, `sortOrder`, `isCorrect`, `status="DRAFT"`) |

---

### Error Codes

| Error Code | HTTP Status | Business Meaning | Client Message |
|---|---|---|---|
| `AUTH-401` | 401 Unauthorized | Thiếu hoặc JWT token không hợp lệ | Authentication required or token invalid. |
| `AUTH-403` | 403 Forbidden | Người dùng không có quyền tạo câu hỏi | You do not have permission to create questions. |
| `VAL-400` | 400 Bad Request | Payload gửi lên vi phạm định dạng hoặc thiếu trường bắt buộc | Invalid request payload or missing required fields. |
| `VAL-422` | 422 Unprocessable Entity | Vi phạm quy tắc nghiệp vụ (VD: câu hỏi `SINGLE_CHOICE` nhưng mảng `options` có > 1 đáp án `isCorrect=true`, hoặc không có đáp án đúng nào) | Business validation failed for question options structure. |
| `SYS-500` | 500 Internal Server Error | Lỗi hệ thống nội bộ hoặc lỗi thao tác cơ sở dữ liệu | An unexpected internal server error occurred. |

---

## Part 2 — Processing Specification

1. **Controller Layer**:
   - Nhận HTTP Request POST tại endpoint `/api/v1/content-builder/questions/draft`.
   - Thực hiện kiểm tra định dạng dữ liệu đầu vào (Input Validation) đối với Request Body dựa trên annotations (`@NotNull`, `@NotBlank`, `@Size`, `@Valid`).
   - Trích xuất thông tin người dùng (`userId`) từ Security Context / JWT Token.
   - Gọi xuống Service Layer phương thức `createQuestion(request, userId, "DRAFT")`.

2. **Service Layer**:
   - Kiểm tra quyền hạn (Permission Validation): Xác minh `userId` có quyền tạo nội dung tài liệu.
   - Kiểm tra quy tắc nghiệp vụ (Business Validation):
     - Nếu loại câu hỏi là `SINGLE_CHOICE` và mảng `options` được cung cấp, kiểm tra số lượng đáp án có `isCorrect = true` không được vượt quá 1.
     - Nếu loại câu hỏi là `TRUE_FALSE` và có mảng `options`, số lượng phần tử tối đa là 2.
   - Khởi tạo giao dịch cơ sở dữ liệu (`@Transactional`).
   - Tạo thực thể Question mới, gán `id = UUIDv7/gen_random_uuid()`, `status = "DRAFT"`, `createdBy = userId`, `createdAt = CURRENT_TIMESTAMP`.
   - Thực hiện lưu Question xuống DB qua Repository Layer.
   - Nếu mảng `options` không rỗng:
     - Lặp qua từng Option Item, khởi tạo thực thể QuestionOption tương ứng, gán `questionId = question.id`, `status = "DRAFT"`, `createdBy = userId`.
     - Lưu danh sách QuestionOption xuống DB qua Repository Layer.
   - Cam kết giao dịch (Commit Transaction).
   - Chuyển đổi thực thể sang đối tượng Response DTO và trả về cho Controller.

3. **Repository Layer**:
   - Thực hiện lệnh INSERT vào bảng `questions`.
   - Thực hiện lệnh INSERT batch vào bảng `question_options` (nếu có options).

4. **External Interaction**:
   - None (Kiểm tra sự tồn tại của `mediaFileIds` tại Media Service có thể thực hiện bất đồng bộ hoặc bỏ qua ở phase DRAFT).

5. **Validation**:
   - **Request Validation**: Kiểm tra các trường bắt buộc (`type`, `content`), định dạng UUID của `mediaFileIds`.
   - **Business Validation**: Kiểm tra tính logic của danh sách đáp án `options` ứng với từng `type`.
   - **Permission Validation**: Kiểm tra role/permission của user.

---

## Part 3 — Data Interaction

- **Operation 1**:
  - **Operation Type**: `INSERT`
  - **Target Table**: `questions`
  - **Conditions**: None
  - **Expected Result**: Tạo 1 bản ghi mới với `status = 'DRAFT'`.
- **Operation 2** (Nếu có mảng `options`):
  - **Operation Type**: `INSERT`
  - **Target Table**: `question_options`
  - **Conditions**: None
  - **Expected Result**: Tạo $N$ bản ghi mới tương ứng, tham chiếu tới `question_id` vừa tạo.

---

## Part 4 — Operational Notes

- **Idempotency**: Hỗ trợ idempotency nếu client gửi kèm header `Idempotency-Key`.
- **Tenant Isolation**: Áp dụng cô lập theo tenant nếu hệ thống bật multi-tenancy (tham chiếu tenant qua `createdBy` hoặc trường tenant_id chung).
- **Retry Strategy**: Client có thể tự động retry tối đa 3 lần nếu gặp mã lỗi `SYS-503` hoặc lỗi network time-out.
- **Audit Logging**: Ghi log sự kiện tạo câu hỏi nháp kèm `questionId`, `userId`, `traceId`.
- **Monitoring & Metrics**: Đếm metric `content_builder.question.created` với tag `status=draft`.
- **Tracing**: Truyền dẫn `traceId` qua MDC logging. Do chưa có API Gateway, backend sẽ tự sinh UUID nếu client không truyền header `X-Request-ID`.

---
---

# 2. Tạo Câu Hỏi Xuất Bản (Create Published Question)

## Part 0 — Classification & Identity

- **API Name**: Create Published Question
- **API Type**: Internal / Public
- **Module**: `content-builder`
- **Feature**: Question Management
- **Description**: Tạo mới bản ghi câu hỏi với trạng thái lập tức chính thức là `PUBLISHED`. Yêu cầu kiểm tra nghiệp vụ khắt khe hơn (phải có ít nhất 2 đáp án đối với câu hỏi trắc nghiệm và ít nhất 1 đáp án đúng) trong cùng một giao dịch.
- **Related Tables**: `questions`, `question_options`
- **Related Services**: `user-service`, `media-service`

---

## Part 1 — API Contract

### Endpoint

- **HTTP Method**: `POST`
- **URL**: `/api/v1/content-builder/questions/published`
- **Content Type**: `application/json`

### Request

#### Headers & Request Body
*(Giống hoàn toàn với API Create Draft Question, ngoại trừ việc mảng `options` có ràng buộc nghiệp vụ bắt buộc phải đầy đủ đáp án tùy theo loại câu hỏi)*.

---

### Response

- **Success Status**: `201 Created`
- **Response Body**: *(Giống với API Create Draft Question, trường `status` trả về `"PUBLISHED"`)*.

---

### Error Codes

| Error Code | HTTP Status | Business Meaning | Client Message |
|---|---|---|---|
| `AUTH-401` | 401 Unauthorized | Thiếu hoặc JWT token không hợp lệ | Authentication required or token invalid. |
| `AUTH-403` | 403 Forbidden | Người dùng không có quyền xuất bản câu hỏi | You do not have permission to publish questions. |
| `VAL-400` | 400 Bad Request | Payload vi phạm định dạng | Invalid request payload. |
| `VAL-422` | 422 Unprocessable Entity | Vi phạm quy tắc xuất bản (VD: Câu hỏi `SINGLE_CHOICE` hoặc `MULTIPLE_CHOICE` nhưng mảng `options` có dưới 2 đáp án, hoặc không có đáp án nào `isCorrect=true`) | Cannot publish question without valid options and at least one correct answer. |
| `SYS-500` | 500 Internal Server Error | Lỗi hệ thống nội bộ | An unexpected internal server error occurred. |

---

## Part 2 — Processing Specification

1. **Controller Layer**:
   - Nhận HTTP Request POST tại endpoint `/api/v1/content-builder/questions/published`.
   - Trích xuất `userId` và gọi Service Layer phương thức `createQuestion(request, userId, "PUBLISHED")`.

2. **Service Layer**:
   - Kiểm tra quyền hạn xuất bản nội dung (`PUBLISH_QUESTION` permission).
   - **Strict Business Validation cho trạng thái PUBLISHED**:
     - Đối với `SINGLE_CHOICE`, `MULTIPLE_CHOICE`: Mảng `options` phải có tối thiểu 2 phần tử. Phải có ít nhất 1 đáp án đúng (`isCorrect = true`). Riêng `SINGLE_CHOICE` chỉ được chính xác 1 đáp án đúng.
     - Đối với `TRUE_FALSE`: Phải có chính xác 2 đáp án (Đúng và Sai), trong đó có 1 đáp án đúng.
   - Khởi tạo giao dịch (@Transactional), lưu bảng `questions` với `status = "PUBLISHED"`.
   - Batch insert các bản ghi `question_options` với `status = "PUBLISHED"`.
   - Commit giao dịch và trả về Response DTO.

3. **Repository Layer**:
   - Thực hiện INSERT vào `questions` và `question_options`.

4. **External Interaction**:
   - Gọi đồng bộ hoặc bất đồng bộ tới `media-service` để xác minh tất cả `mediaFileIds` đều tồn tại và ở trạng thái hợp lệ.

5. **Validation**:
   - Yêu cầu kiểm tra tính đầy đủ của đáp án khắt khe hơn bản DRAFT.

---

## Part 3 — Data Interaction
- Tương tự như API Create Draft Question, trạng thái lưu vào DB là `'PUBLISHED'`.

## Part 4 — Operational Notes
- **Audit Logging**: Ghi audit log quan trọng về việc xuất bản câu hỏi trực tiếp lên hệ thống.
- **Metrics**: Metric `content_builder.question.created` với tag `status=published`.

---
---

# 3. Lấy Chi Tiết Câu Hỏi (Get Question Detail)

## Part 0 — Classification & Identity

- **API Name**: Get Question Detail
- **API Type**: Internal / Public
- **Module**: `content-builder`
- **Feature**: Question Management
- **Description**: Tra cứu và lấy thông tin chi tiết của một câu hỏi theo ID. Hỗ trợ tham số truy vấn `includeOptions=true` để đính kèm toàn bộ danh sách lựa chọn/đáp án liên quan (chưa bị xóa mềm).
- **Related Tables**: `questions`, `question_options`
- **Related Services**: None

---

## Part 1 — API Contract

### Endpoint

- **HTTP Method**: `GET`
- **URL**: `/api/v1/content-builder/questions/{id}`
- **Content Type**: `application/json`

### Request

#### Path Variables
| Name | Type | Required | Description | Validation Rules |
|---|---|---|---|---|
| `id` | String (UUID) | Yes | Định danh câu hỏi | Định dạng UUIDv4/UUIDv7 |

#### Query Parameters
| Name | Type | Required | Description | Validation Rules |
|---|---|---|---|---|
| `includeOptions` | Boolean | No | Cờ yêu cầu lấy kèm danh sách đáp án (`question_options`) | `true` hoặc `false`, mặc định là `false` |

#### Headers
| Name | Type | Required | Description | Validation Rules |
|---|---|---|---|---|
| `Authorization` | String | Yes | Bearer JWT Token | Hợp lệ |

#### Request Body
- None

---

### Response

- **Success Status**: `200 OK`

#### Response Body
| Name | Type | Description |
|---|---|---|
| `id` | String (UUID) | Định danh câu hỏi |
| `type` | String | Loại câu hỏi |
| `content` | String | Nội dung câu hỏi |
| `explanations` | Object/Array | Lời giải thích |
| `mediaFileIds` | Array<String> | Danh sách UUID media |
| `attachments` | Object/Array | File đính kèm |
| `references` | Object/Array | Tham khảo |
| `status` | String | Trạng thái (`DRAFT`, `PUBLISHED`, `ARCHIVED`, `HIDDEN`) |
| `metadata` | Object | Dữ liệu mở rộng |
| `createdBy` | String (UUID) | ID người tạo |
| `createdAt` | String (ISO-8601)| Thời điểm tạo |
| `updatedBy` | String (UUID) | ID người cập nhật gần nhất |
| `updatedAt` | String (ISO-8601)| Thời điểm cập nhật gần nhất |
| `options` | Array<Object> | Danh sách các đáp án (Chỉ có giá trị nếu `includeOptions = true`, sắp xếp theo `sort_order ASC`) |

---

### Error Codes

| Error Code | HTTP Status | Business Meaning | Client Message |
|---|---|---|---|
| `AUTH-401` | 401 Unauthorized | Thiếu hoặc JWT token không hợp lệ | Authentication required. |
| `RES-404` | 404 Not Found | Câu hỏi không tồn tại hoặc đã bị xóa mềm (`deleted_at IS NOT NULL`) | Question not found with the specified ID. |
| `SYS-500` | 500 Internal Server Error | Lỗi hệ thống | An unexpected internal server error occurred. |

---

## Part 2 — Processing Specification

1. **Controller Layer**:
   - Nhận GET request tại `/api/v1/content-builder/questions/{id}`.
   - Validate tham số path `id` đúng định dạng UUID.
   - Gọi Service Layer: `getQuestionById(id, includeOptions)`.

2. **Service Layer**:
   - Gọi Repository truy vấn bản ghi Question theo `id` với điều kiện `deleted_at IS NULL`.
   - Nếu không tìm thấy, ném ngoại lệ `ResourceNotFoundException` (trả lỗi `RES-404`).
   - Nếu `includeOptions == true`:
     - Gọi Repository truy vấn danh sách `question_options` theo `question_id = id` và `deleted_at IS NULL`, sắp xếp theo `sort_order ASC, created_at ASC`.
     - Đính kèm danh sách options vào đối tượng DTO kết quả.
   - Trả về Response DTO.

3. **Repository Layer**:
   - Thực hiện lệnh SELECT trên bảng `questions`.
   - Thực hiện lệnh SELECT trên bảng `question_options` (nếu cần).

4. **External Interaction**:
   - None.

5. **Validation**:
   - Kiểm tra UUID hợp lệ.

---

## Part 3 — Data Interaction

- **Operation 1**:
  - **Operation Type**: `SELECT`
  - **Target Table**: `questions`
  - **Conditions**: `id = :id AND deleted_at IS NULL`
  - **Expected Result**: Trả về 0 hoặc 1 dòng dữ liệu.
- **Operation 2** (Khi `includeOptions=true`):
  - **Operation Type**: `SELECT`
  - **Target Table**: `question_options`
  - **Conditions**: `question_id = :id AND deleted_at IS NULL ORDER BY sort_order ASC, created_at ASC`
  - **Expected Result**: Trả về danh sách $0 \dots N$ đáp án.

---

## Part 4 — Operational Notes

- **Caching**: Có thể sử dụng Redis cache với key `question:detail:{id}` (đặc biệt đối với các câu hỏi PUBLISHED có lưu lượng truy cập lớn). Khi update hoặc soft-delete cần tiến hành evict cache.
- **Monitoring**: Theo dõi latency truy vấn chi tiết câu hỏi.

---
---

# 4. Lấy Danh Sách Câu Hỏi (List Questions)

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
| `Authorization` | String | Yes | Bearer JWT Token | Hợp lệ |

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
| `VAL-400` | 400 Bad Request | Tham số phân trang hoặc sắp xếp không hợp lệ | Invalid pagination or filtering parameters. |
| `SYS-500` | 500 Internal Server Error | Lỗi hệ thống | An unexpected internal server error occurred. |

---

## Part 2 — Processing Specification

1. **Controller Layer**:
   - Nhận GET request tại `/api/v1/content-builder/questions`.
   - Tạo đối tượng `Pageable` từ `page`, `size`, `sort`.
   - Gọi Service Layer: `listQuestions(filterCriteria, pageable)`.

2. **Service Layer**:
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

---
---

# 5. Cập Nhật Câu Hỏi (Update Question)

## Part 0 — Classification & Identity

- **API Name**: Update Question
- **API Type**: Internal / Public
- **Module**: `content-builder`
- **Feature**: Question Management
- **Description**: Cập nhật thông tin của một câu hỏi đã tồn tại. Chỉ cập nhật thông tin riêng của câu hỏi (nội dung, metadata, trạng thái, media...). Việc cập nhật danh sách đáp án được thực hiện qua các API chuyên biệt của Question Option.
- **Related Tables**: `questions`
- **Related Services**: `user-service`, `media-service`

---

## Part 1 — API Contract

### Endpoint

- **HTTP Method**: `PUT`
- **URL**: `/api/v1/content-builder/questions/{id}`
- **Content Type**: `application/json`

### Request

#### Path Variables
| Name | Type | Required | Description | Validation Rules |
|---|---|---|---|---|
| `id` | String (UUID) | Yes | Định danh câu hỏi cần sửa | UUIDv4/UUIDv7 |

#### Request Body
| Name | Type | Required | Description | Validation Rules |
|---|---|---|---|---|
| `type` | String | Yes | Loại câu hỏi | `SINGLE_CHOICE`, `MULTIPLE_CHOICE`,... |
| `content` | String | Yes | Nội dung câu hỏi | 1 đến 50,000 ký tự |
| `explanations` | Object/Array | No | Lời giải thích | Chuẩn JSONB |
| `mediaFileIds` | Array<String> | No | Danh sách UUID media | Chuẩn UUID |
| `attachments` | Object/Array | No | File đính kèm | Chuẩn JSONB |
| `references` | Object/Array | No | Tài liệu tham khảo | Chuẩn JSONB |
| `status` | String | Yes | Trạng thái mới | `DRAFT`, `PUBLISHED`, `ARCHIVED`, `HIDDEN` |
| `metadata` | Object | No | Dữ liệu mở rộng | Chuẩn JSONB |

---

### Response

- **Success Status**: `200 OK`

#### Response Body
- Trả về chi tiết đối tượng Question sau khi cập nhật thành công (giống Response Body của API Get Question Detail).

---

### Error Codes

| Error Code | HTTP Status | Business Meaning | Client Message |
|---|---|---|---|
| `AUTH-401` | 401 Unauthorized | JWT token không hợp lệ | Authentication required. |
| `AUTH-403` | 403 Forbidden | Không có quyền cập nhật câu hỏi này | You do not have permission to update this question. |
| `RES-404` | 404 Not Found | Câu hỏi không tồn tại hoặc đã bị xóa mềm | Question not found with ID. |
| `VAL-400` | 400 Bad Request | Payload không hợp lệ | Invalid request payload. |
| `VAL-422` | 422 Unprocessable Entity | Vi phạm quy tắc nghiệp vụ khi chuyển sang `PUBLISHED` (VD: chưa có đủ đáp án hợp lệ trong DB) | Cannot change status to PUBLISHED: Question lacks valid options. |
| `SYS-500` | 500 Internal Server Error | Lỗi hệ thống | An unexpected internal server error occurred. |

---

## Part 2 — Processing Specification

1. **Controller Layer**:
   - Nhận PUT request tại `/api/v1/content-builder/questions/{id}`.
   - Validate payload đầu vào, trích xuất `userId`.
   - Gọi Service Layer: `updateQuestion(id, request, userId)`.

2. **Service Layer**:
   - Truy vấn bản ghi Question từ DB theo `id` (`deleted_at IS NULL`). Nếu không thấy ném lỗi `RES-404`.
   - Kiểm tra quyền: Người dùng phải là người tạo (`createdBy == userId`) hoặc có role Admin/Editor (`EDIT_ANY_QUESTION` permission).
   - Nếu `request.status == "PUBLISHED"` và trạng thái hiện tại là `DRAFT`:
     - Kiểm tra trong bảng `question_options` xem câu hỏi này đã có đủ số lượng đáp án tối thiểu và có đáp án đúng (`isCorrect = true`) hay chưa. Nếu chưa đủ, ném ngoại lệ vi phạm nghiệp vụ (`VAL-422`).
   - Cập nhật các trường thông tin từ request vào thực thể Question.
   - Gán `updatedBy = userId` (trường `updatedAt` được tự động cập nhật bởi Trigger DB và JPA `@LastModifiedDate`).
   - Lưu xuống DB qua Repository Layer.
   - Evict cache liên quan (nếu có) và trả về DTO.

3. **Repository Layer**:
   - Thực hiện lệnh UPDATE trên bảng `questions`.

4. **External Interaction**:
   - None.

5. **Validation**:
   - Kiểm tra các ràng buộc chuyển trạng thái hợp lệ.

---

## Part 3 — Data Interaction

- **Operation 1**:
  - **Operation Type**: `SELECT`
  - **Target Table**: `questions`
  - **Conditions**: `id = :id AND deleted_at IS NULL`
  - **Expected Result**: 1 dòng dữ liệu.
- **Operation 2**:
  - **Operation Type**: `UPDATE`
  - **Target Table**: `questions`
  - **Conditions**: `id = :id`
  - **Expected Result**: Cập nhật các cột nội dung, `status`, `updated_by`, `updated_at`.

---

## Part 4 — Operational Notes

- **Audit Logging**: Ghi log sự kiện cập nhật câu hỏi.
- **Cache Eviction**: Xóa cache Redis của key `question:detail:{id}`.

---
---

# 6. Xóa Mềm Câu Hỏi (Soft Delete Question)

## Part 0 — Classification & Identity

- **API Name**: Soft Delete Question
- **API Type**: Internal / Public
- **Module**: `content-builder`
- **Feature**: Question Management
- **Description**: Thực hiện xóa mềm một câu hỏi khỏi hệ thống bằng cách gán `deleted_at = CURRENT_TIMESTAMP`. **Quy tắc nghiệp vụ Cascade**: Tự động tiến hành xóa mềm toàn bộ danh sách lựa chọn/đáp án (`question_options`) trực thuộc câu hỏi này trong cùng một giao dịch.
- **Related Tables**: `questions`, `question_options`, `question_blocks`
- **Related Services**: `user-service`

---

## Part 1 — API Contract

### Endpoint

- **HTTP Method**: `DELETE`
- **URL**: `/api/v1/content-builder/questions/{id}`
- **Content Type**: None

### Request

#### Path Variables
| Name | Type | Required | Description | Validation Rules |
|---|---|---|---|---|
| `id` | String (UUID) | Yes | Định danh câu hỏi cần xóa | UUIDv4/UUIDv7 |

#### Headers
| Name | Type | Required | Description | Validation Rules |
|---|---|---|---|---|
| `Authorization` | String | Yes | Bearer JWT Token | Hợp lệ |

---

### Response

- **Success Status**: `204 No Content` (hoặc `200 OK` kèm thông báo thành công)
- **Response Body**: Empty (đối với 204) hoặc Object JSON xác nhận xóa thành công.

---

### Error Codes

| Error Code | HTTP Status | Business Meaning | Client Message |
|---|---|---|---|
| `AUTH-401` | 401 Unauthorized | JWT token không hợp lệ | Authentication required. |
| `AUTH-403` | 403 Forbidden | Không có quyền xóa câu hỏi này | You do not have permission to delete this question. |
| `RES-404` | 404 Not Found | Câu hỏi không tồn tại hoặc đã bị xóa mềm từ trước | Question not found. |
| `VAL-409` | 409 Conflict | Câu hỏi đang được khóa hoặc sử dụng trong đề thi chính thức không cho phép xóa | Question cannot be deleted because it is being used in an active exam session. |
| `SYS-500` | 500 Internal Server Error | Lỗi hệ thống | An unexpected internal server error occurred. |

---

## Part 2 — Processing Specification

1. **Controller Layer**:
   - Nhận DELETE request tại `/api/v1/content-builder/questions/{id}`.
   - Validate UUID `id`, trích xuất `userId`.
   - Gọi Service Layer: `deleteQuestion(id, userId)`.

2. **Service Layer**:
   - Truy vấn bản ghi Question theo `id` (`deleted_at IS NULL`). Nếu không thấy ném lỗi `RES-404`.
   - Kiểm tra quyền xóa (`DELETE_QUESTION` permission hoặc chủ sở hữu).
   - Kiểm tra ràng buộc nghiệp vụ: Nếu câu hỏi đang được gắn trong một chương trình học/đề thi đang hoạt động (có thể kiểm tra qua `question_blocks` hoặc liên kết đề thi), ném lỗi xung đột `VAL-409`.
   - Khởi tạo giao dịch (@Transactional):
     - Gán `question.deletedAt = CURRENT_TIMESTAMP`, `question.updatedBy = userId`.
     - Thực hiện câu lệnh cập nhật batch trên bảng `question_options`: Gán `deleted_at = CURRENT_TIMESTAMP`, `updated_by = userId` cho tất cả bản ghi có `question_id = id` và `deleted_at IS NULL`.
     - Thực hiện cập nhật tương tự cho các tham chiếu trong `question_blocks` (nếu cần gỡ bỏ khỏi cây cấu trúc chương).
   - Commit giao dịch.
   - Evict cache liên quan.

3. **Repository Layer**:
   - Thực hiện lệnh UPDATE soft delete trên `questions`.
   - Thực hiện lệnh UPDATE soft delete trên `question_options`.

4. **External Interaction**:
   - Gửi sự kiện `QuestionDeletedEvent` lên Kafka (nếu hệ thống sử dụng event-driven để thống kê số lượng hoặc dọn dẹp tài nguyên bên ngoài).

5. **Validation**:
   - Kiểm tra tính hợp lệ của ID và trạng thái khóa của câu hỏi.

---

## Part 3 — Data Interaction

- **Operation 1**:
  - **Operation Type**: `UPDATE`
  - **Target Table**: `questions`
  - **Conditions**: `id = :id AND deleted_at IS NULL`
  - **Expected Result**: Gán `deleted_at = CURRENT_TIMESTAMP`, `updated_by = :userId`.
- **Operation 2**:
  - **Operation Type**: `UPDATE`
  - **Target Table**: `question_options`
  - **Conditions**: `question_id = :id AND deleted_at IS NULL`
  - **Expected Result**: Gán `deleted_at = CURRENT_TIMESTAMP`, `updated_by = :userId` cho toàn bộ options con.

---

## Part 4 — Operational Notes

- **Idempotency**: Việc gọi nhiều lần API soft delete với cùng 1 ID sau lần thành công đầu tiên sẽ trả về `404 Not Found` (do `deleted_at` đã có giá trị).
- **Audit Logging**: Ghi log sự kiện xóa mềm câu hỏi kèm danh sách số lượng options đã bị xóa theo.
- **Cache Eviction**: Evict key `question:detail:{id}` khỏi Redis.
