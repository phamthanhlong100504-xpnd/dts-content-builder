# API Blueprint — Đọc Chi Tiết Nhiều Câu Hỏi Nội Bộ

Tài liệu mô tả **hành vi code hiện tại**, theo source local đã chốt tới commit `225c062`, so với `main` tại `e727ff795ed149da4eb9e77c2d6a22be8f8e03a6`. Ví dụ dùng UUID/thời điểm minh họa; không phải dữ liệu API thật. Không thay đổi code hoặc database.

---

## Part 0 — Classification & Identity

- **API Name**: Get Internal Questions Batch.
- **API Type**: Internal, chỉ dành cho consumer backend được cấp quyền.
- **Module**: `content-builder`.
- **Feature**: Internal Examination Source.
- **Description**: Đọc đầy đủ nội dung/options/đáp án/giải thích của tập question UUID để consumer backend đóng băng source. Giữ thứ tự request; xử lý fail toàn request nếu một câu không hợp lệ.
- **Related Tables**: `questions`, `question_options`.
- **Related Services**: Examination là consumer chính; Media cung cấp tham chiếu ảnh.
- **Source of Truth**: `InternalQuestionController`, `ExamQuestionReadService`, `ExamSourceAccess`, `InternalQuestionDetailResponse`, `InternalQuestionOptionResponse`.

---

## Part 1 — API Contract

### Endpoint

- **HTTP Method**: `POST`.
- **URL**: `/api/v1/content-builder/internal/questions/batch`.
- **Success Status**: `200 OK`.
- **Content Type**: `application/json`; body thành công trả trực tiếp DTO/mảng, không có wrapper `data`.

### Headers và quyền truy cập

| Name | Type | Required | Description |
|---|---|---|---|
| `Authorization` | String | Một trong hai cơ chế | `Bearer <JWT>` hợp lệ có đồng thời `ROLE_ADMIN` và `PERM_questions:read`. |
| `X-DTS-Exam-Source-Token` | String | Một trong hai cơ chế | Service JWT ngắn hạn do Examination phát; có thể dùng thay admin JWT. |
| `X-Request-ID` | String | Không | Trace ID được filter tiếp nhận hoặc tự sinh; code không kiểm UUID/giới hạn 64 ký tự. |

`ExamSourceAccess.requireAccess` kiểm quyền trước khi gọi service. `permitAll` ở URL internal chỉ cho request đi tới chốt này, không mở dữ liệu vô danh. `X-Service-Name` không được code dùng để cấp quyền.

Chi tiết xác thực và lỗi chung: [Contract Notes](content_builder_contract_notes.md).

### Request

- **Path Variables**: không có.

| Query Parameter | Type | Required | Rule |
|---|---|---|---|
| `licenseClass` | String | Không | Trim/upper-case; null/blank là không lọc hạng; nếu có phải thuộc `A1`, `A`, `B1`, `B`, `C1`, `C`, `D1`, `D2`, `D`, `BE`, `C1E`, `CE`, `D1E`, `D2E`, `DE`. |

**Body là mảng UUID trực tiếp**, không phải object `{ "questionIds": [...] }`.

| Body | Type | Rules |
|---|---|---|
| root | Array<UUID> | 0–1000 phần tử, unique, không chứa null. Mảng rỗng trả []. |

```http
POST /api/v1/content-builder/internal/questions/batch?licenseClass=A1
Content-Type: application/json
X-DTS-Exam-Source-Token: <EXAMINATION_SERVICE_TOKEN>
```

```json
[
  "20000000-0000-4000-8000-000000000001"
]
```

### Response

Mảng DTO trực tiếp, cùng thứ tự UUID request khi thành công.

| Name | Type | Description |
|---|---|---|
| `id` | UUID | UUID câu hỏi. |
| `content` | String | Nội dung câu hỏi. |
| `type` | String | QuestionType; ở batch tên trường là type. |
| `isCritical` | Boolean | Điểm liệt theo cùng license đã dùng lúc đọc metadata. |
| `mediaFileIds` | Array<String UUID> | Tham chiếu Media, không phải URL hoặc path local. |
| `options` | Array<Object> | Danh sách option có cả isCorrect; chỉ consumer backend được cấp quyền nhận. |
| `status` | String | PUBLISHED. |
| `contentVersion` | String | Revision phải khớp metadata nếu source chưa thay đổi và dùng cùng license. |
| `explanations` | Object hoặc null | Map giải thích gốc từ QuestionEntity; không tạo lời giải mới. |

**Option:**

| Name | Type | Description |
|---|---|---|
| `id` | UUID | UUID option. |
| `content` | String | Nội dung option, không blank. |
| `sortOrder` | Integer hoặc null | Thứ tự nguồn; null được xếp sau các giá trị có số. |
| `isCorrect` | Boolean | Đáp án đúng/sai của option. |

```json
[
  {
    "id": "20000000-0000-4000-8000-000000000001",
    "content": "Nội dung câu hỏi minh họa.",
    "type": "SINGLE_CHOICE",
    "isCritical": false,
    "mediaFileIds": [],
    "options": [
      {
        "id": "21000000-0000-4000-8000-000000000001",
        "content": "Lựa chọn minh họa thứ nhất.",
        "sortOrder": 1,
        "isCorrect": true
      },
      {
        "id": "21000000-0000-4000-8000-000000000002",
        "content": "Lựa chọn minh họa thứ hai.",
        "sortOrder": 2,
        "isCorrect": false
      }
    ],
    "status": "PUBLISHED",
    "contentVersion": "sha256:0000000000000000000000000000000000000000000000000000000000000000",
    "explanations": {
      "text": "Giải thích minh họa từ dữ liệu nguồn."
    }
  }
]
```

Hash trong ví dụ chỉ minh họa định dạng. `Cache-Control: no-store` được đặt trên response thành công.

### Error Codes / HTTP Errors

Response hiện tại **không có trường `code`**; không gán mã `VAL-400`, `AUTH-403` hoặc `RES-404` giả định.

| HTTP | Nhánh xử lý | Ý nghĩa |
|---|---|---|
| 400 | `BusinessValidationException` | Mảng có null/trùng ID/>1000; license không hợp lệ; câu không PUBLISHED/không thuộc license; nội dung/options/media/phân loại hạng không hợp lệ. |
| 404 | `ResourceNotFoundException` | Bất kỳ questionId không tồn tại hoặc đã soft-delete. |
| 403 | `AccessDeniedException` | Không đủ quyền admin/service token đọc nguồn. |
| 500 | Exception không có handler riêng | Lỗi ngoài các nhánh business/validation được xử lý rõ ràng. |

Lỗi tại security filter/ingress có thể khác `ErrorResponse`. Lỗi bind UUID/query, JSON không đọc được, sort không tồn tại hoặc ràng buộc DB không được tự coi là `400/409`: handler tổng quát của source hiện tại có thể trả `500`. Xem [lỗi chung](content_builder_contract_notes.md#part-3--error-contract-and-binding-boundary).

---

## Part 2 — Processing Specification

1. **Controller Layer**: bind array/header/query; kiểm ExamSourceAccess; gọi `getBatch(questionIds, licenseClass)`.
2. **Request Validation**: normalize license; kiểm null entries, tối đa 1000, uniqueness. Mảng rỗng được trả rỗng ngay. Body JSON bị thiếu/không đọc được là lỗi binding riêng, không mặc định coi là business 400.
3. **Read Source**: lấy questions chưa soft-delete và options chưa soft-delete theo tập UUID. Duyệt theo đúng thứ tự array request.
4. **Per-question Validation**: không có câu: 404; có nhưng không PUBLISHED: 400; ngoài license: 400, không bỏ qua rồi trả thiếu. Kiểm toàn bộ source, sắp options, tính critical/revision.
5. **Response**: nội dung gốc, options có isCorrect, status, revision và explanations. Nếu một câu lỗi thì không có response thành công một phần. Service chỉ đọc, không tạo record kết quả hoặc ghi lịch sử.

### Điều kiện chất lượng source dùng chung

- Câu hỏi phải PUBLISHED, chưa soft-delete, có type và content không blank.
- Toàn bộ options chưa soft-delete được kiểm tra: ít nhất 2, ID khác null/không trùng, đúng questionId, PUBLISHED, nội dung không blank, isCorrect khác null, sortOrder nếu có phải >= 0. Có một option DRAFT chưa soft-delete cũng làm source không hợp lệ; service không âm thầm bỏ option đó.
- Ít nhất một đáp án đúng; SINGLE_CHOICE đúng chính xác một; TRUE_FALSE đúng chính xác một trong đúng hai options. Code áp dụng yêu cầu tối thiểu 2 options chung cho các type hiện có.
- mediaFileIds chỉ chứa chuỗi UUID. Nếu metadata.sourceImagePath không blank thì phải có ít nhất một Media UUID. Service không gọi Media để xác nhận UUID đã READY.
- Khi chọn license: applicableLicenses/criticalLicenses phải là list các hạng hợp lệ; criticalLicenses là tập con của applicableLicenses. Thiếu/sai dữ liệu gây BusinessValidationException, không tự suy luận từ boolean isCritical.

### Quy ước revision

`contentVersion` băm snapshot gồm id, type, content, status, mediaFileIds, options đã sắp thứ tự, explanations, metadata, licenseClass và isCritical. Map keys được serialize có thứ tự; kết quả là `sha256:` nối SHA256 hex. Không đưa chapterIds hoặc audit timestamp thành trường revision riêng; việc đổi liên kết chương không được coi là đổi nội dung chỉ bằng hash này.

Consumer dùng cùng license ở metadata và batch, đối chiếu revision trước khi đóng băng nội dung đề. Hai HTTP request riêng không nằm trong cùng transaction; nếu source đổi giữa hai lần đọc, revision có thể khác. Không tự dựng revision tại FE hoặc thay bằng bankVersion.

---

## Part 3 — Data Interaction

| Operation | Table | Conditions | Expected Result |
|---|---|---|---|
| SELECT | questions | id IN request array AND deleted_at IS NULL | Map entity để duyệt theo thứ tự request. |
| SELECT | question_options | question_id IN request array AND deleted_at IS NULL | Options đầy đủ để kiểm chuẩn; không lọc bỏ DRAFT trước validation. |

Service dùng read-only REPEATABLE_READ. Endpoint batch không yêu cầu chapterId, không tự xác nhận câu đang thuộc catalog đã đọc trước đó; consumer phải chọn đúng ID và kiểm revision từ metadata/bank.

---

## Part 4 — Operational Notes

- Response chứa đáp án đúng và giải thích; không proxy thẳng tới FE học viên đang làm bài. Examination áp quy tắc phát hành kết quả của nó ở repo tương ứng.
- Dùng cùng license với lúc chọn nguồn. Nếu bỏ license, critical lấy boolean metadata.isCritical thay vì criticalLicenses của hạng, nên kết quả có thể khác.
- status/contentVersion/explanations là các trường bổ sung so với DTO trên main; các trường cũ vẫn được giữ.
- Service không gọi AI để tạo giải thích, không truy cập filesystem ảnh, không upload Media và không tăng tiến độ người học.
