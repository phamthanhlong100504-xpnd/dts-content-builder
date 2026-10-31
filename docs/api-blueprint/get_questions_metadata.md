# API Blueprint — Lấy Metadata Câu Hỏi Nội Bộ

Tài liệu mô tả **hành vi code hiện tại**, theo source local đã chốt tới commit `225c062`, so với `main` tại `e727ff795ed149da4eb9e77c2d6a22be8f8e03a6`. Ví dụ dùng UUID/thời điểm minh họa; không phải dữ liệu API thật. Không thay đổi code hoặc database.

---

## Part 0 — Classification & Identity

- **API Name**: Get Questions Metadata.
- **API Type**: Internal, chỉ dành cho consumer backend được cấp quyền.
- **Module**: `content-builder`.
- **Feature**: Internal Examination Source.
- **Description**: Đọc metadata của câu hỏi qua source CHAPTER hoặc LEARNING_PROGRAM, chỉ theo các đường liên kết published hợp lệ. Cập nhật file blueprint cũ theo chốt auth và schema mới.
- **Related Tables**: `chapters`, `learning_programs`, `chapter_blocks`, `question_blocks`, `questions`, `question_options`.
- **Related Services**: Examination là consumer chính; Media cung cấp tham chiếu ảnh.
- **Source of Truth**: `InternalQuestionController`, `ExamQuestionReadService`, `ExamSourceAccess`, `ChapterRepository`, `InternalQuestionMetadataResponse`.

---

## Part 1 — API Contract

### Endpoint

- **HTTP Method**: `GET`.
- **URL**: `/api/v1/content-builder/internal/questions/metadata`.
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

- **Path Variables / Body**: không có.

| Query Parameter | Type | Required | Rule |
|---|---|---|---|
| `contentId` | UUID | Có | UUID chapter hoặc learning program. |
| `contentType` | String | Có | Trim, upper-case rồi nhận CHAPTER hoặc LEARNING_PROGRAM. |
| `licenseClass` | String | Không | Trim/upper-case; null hoặc blank nghĩa là không lọc hạng. Nếu có phải thuộc `A1`, `A`, `B1`, `B`, `C1`, `C`, `D1`, `D2`, `D`, `BE`, `C1E`, `CE`, `D1E`, `D2E`, `DE`. |

```http
GET /api/v1/content-builder/internal/questions/metadata?contentId=40000000-0000-4000-8000-000000000001&contentType=CHAPTER&licenseClass=A1
X-DTS-Exam-Source-Token: <EXAMINATION_SERVICE_TOKEN>
```

### Response

Trả **mảng trực tiếp**; câu không thuộc license được bỏ khỏi kết quả.

| Name | Type | Description |
|---|---|---|
| `id` | UUID | UUID câu hỏi. |
| `optionIds` | Array<UUID> | ID các lựa chọn, đã sắp theo sortOrder null-last rồi UUID. |
| `chapterIds` | Array<UUID> | Các chapter chứa liên kết hợp lệ tới câu trong source đang chọn. |
| `isCritical` | Boolean | Điểm liệt theo licenseClass; nếu bỏ license thì dùng metadata.isCritical. |
| `status` | String | PUBLISHED cho các câu được trả thành công. |
| `questionType` | String | QuestionType của câu; không dùng trường type tại metadata. |
| `mediaFileIds` | Array<String UUID> | Tham chiếu Media, mảng rỗng nếu không có ảnh. |
| `contentVersion` | String | Revision `sha256:<64 hex>` do backend tính, không phải bankVersion. |

```json
[
  {
    "id": "20000000-0000-4000-8000-000000000001",
    "optionIds": [
      "21000000-0000-4000-8000-000000000001",
      "21000000-0000-4000-8000-000000000002"
    ],
    "chapterIds": [
      "40000000-0000-4000-8000-000000000001"
    ],
    "isCritical": false,
    "status": "PUBLISHED",
    "questionType": "SINGLE_CHOICE",
    "mediaFileIds": [],
    "contentVersion": "sha256:0000000000000000000000000000000000000000000000000000000000000000"
  }
]
```

Hash toàn số 0 trong ví dụ chỉ thể hiện định dạng; không phải revision thực của dữ liệu. Response thành công có `Cache-Control: no-store`.

### Error Codes / HTTP Errors

Response hiện tại **không có trường `code`**; không gán mã `VAL-400`, `AUTH-403` hoặc `RES-404` giả định.

| HTTP | Nhánh xử lý | Ý nghĩa |
|---|---|---|
| 400 | `BusinessValidationException` | contentType/license không hợp lệ, source parent chưa PUBLISHED, source câu hỏi/option/phân loại hạng không hợp lệ. |
| 404 | `ResourceNotFoundException` | Chapter hoặc learning program không tồn tại/chưa soft-delete không tìm thấy. |
| 403 | `AccessDeniedException` | Không đủ quyền admin/service token đọc nguồn. |
| 500 | Exception không có handler riêng | Lỗi ngoài các nhánh business/validation được xử lý rõ ràng. |

Lỗi tại security filter/ingress có thể khác `ErrorResponse`. Lỗi bind UUID/query, JSON không đọc được, sort không tồn tại hoặc ràng buộc DB không được tự coi là `400/409`: handler tổng quát của source hiện tại có thể trả `500`. Xem [lỗi chung](content_builder_contract_notes.md#part-3--error-contract-and-binding-boundary).

---

## Part 2 — Processing Specification

1. **Controller Layer**: bind query/header; `ExamSourceAccess.requireAccess` kiểm quyền; gọi `ExamQuestionReadService.getMetadata` và trả ResponseEntity no-store.
2. **Resolve Source**: với CHAPTER, parent phải tồn tại, chưa xóa mềm, PUBLISHED. Với LEARNING_PROGRAM, program cũng phải tồn tại/PUBLISHED; đọc chapter_blocks của program, chỉ nhận các đường từ node lên root đều PUBLISHED/chưa xóa và không có cycle/missing parent, rồi chỉ giữ chapter PUBLISHED/chưa xóa. Chapter được distinct theo thứ tự sortOrder null-last rồi UUID của link.
3. **Resolve Question Links**: đọc question_blocks của các chapter; đường từ link lên root phải toàn PUBLISHED, cùng chapter, không có cycle/missing parent. Sắp theo thứ tự chapter, sortOrder link null-last rồi UUID link. Gom trùng questionId và hợp nhất chapterIds theo thứ tự gặp.
4. **Read Questions / Options**: bỏ câu không tồn tại/soft-delete/khác PUBLISHED; nếu có license thì chỉ giữ câu thuộc applicableLicenses. Kiểm chuẩn source, sắp options, tính critical và revision; build DTO metadata.
5. **Empty Result**: source tồn tại/PUBLISHED nhưng không có link/câu phù hợp thì trả `[]`; không đồng nghĩa source ID không tồn tại.

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

| Operation | Tables | Conditions / Purpose | Expected Result |
|---|---|---|---|
| SELECT | chapters / learning_programs | Đọc parent theo id và deleted_at IS NULL; kiểm status tại service. | Parent hợp lệ hoặc lỗi. |
| SELECT | chapter_blocks | Khi chọn program: learning_program_id, deleted_at IS NULL. | Các link dùng để kiểm đường published và thu chapter. |
| SELECT | question_blocks | chapter_id IN (...), deleted_at IS NULL. | Nguồn quan hệ question/chapter. |
| SELECT | questions / question_options | Lấy batch theo ID, chưa soft-delete; kiểm trạng thái/phân loại/option tại service. | Metadata đã validate; không UPDATE/INSERT. |

Transaction của service là read-only, isolation REPEATABLE_READ.

---

## Part 4 — Operational Notes

- Giữ các trường cũ id/optionIds, bổ sung chapterIds/isCritical/status/questionType/mediaFileIds/contentVersion.
- Controller hiện gọi ExamQuestionReadService, không còn gọi overload legacy trong QuestionService; không áp quy tắc legacy hardcode bank 2026 để mô tả endpoint này.
- Metadata không trả option content/isCorrect hoặc explanations; dùng batch có cấp quyền cho consumer backend.
- Không gọi từ FE học viên và không coi X-Service-Name là thông tin xác thực.
