# API Blueprint — Đọc Ngân Hàng Câu Hỏi Theo Hạng Bằng

Tài liệu mô tả **hành vi code hiện tại**, theo source local đã chốt tới commit `225c062`, so với `main` tại `e727ff795ed149da4eb9e77c2d6a22be8f8e03a6`. Ví dụ dùng UUID/thời điểm minh họa; không phải dữ liệu API thật. Không thay đổi code hoặc database.

---

## Part 0 — Classification & Identity

- **API Name**: Get Licensed Question Bank.
- **API Type**: Internal, chỉ dành cho consumer backend được cấp quyền.
- **Module**: `content-builder`.
- **Feature**: Internal Examination Source.
- **Description**: Đọc catalog sáu chương và metadata ngân hàng theo bankVersion/hạng bằng, dùng cho Examination chọn nguồn sinh đề.
- **Related Tables**: `chapters`, `question_blocks`, `questions`, `question_options`.
- **Related Services**: Examination là consumer chính; Media cung cấp tham chiếu ảnh.
- **Source of Truth**: `InternalQuestionController`, `ExamQuestionReadService`, `ExamSourceAccess`, `ChapterRepository`, `InternalQuestionBankResponse`.

---

## Part 1 — API Contract

### Endpoint

- **HTTP Method**: `GET`.
- **URL**: `/api/v1/content-builder/internal/questions/bank`.
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
| `bankVersion` | String | Có | Regex `[A-Za-z0-9._:-]{1,128}`; không trim tự động. |
| `licenseClass` | String | Có | Không blank sau normalize; trim/upper-case; một trong `A1`, `A`, `B1`, `B`, `C1`, `C`, `D1`, `D2`, `D`, `BE`, `C1E`, `CE`, `D1E`, `D2E`, `DE`. |

```http
GET /api/v1/content-builder/internal/questions/bank?bankVersion=dts-2026-600-v1&licenseClass=A1
X-DTS-Exam-Source-Token: <EXAMINATION_SERVICE_TOKEN>
```

### Response

| Name | Type | Description |
|---|---|---|
| `bankVersion` | String | Phiên bản đã chọn. |
| `licenseClass` | String | Hạng đã normalize. |
| `chapters` | Array<Object> | Catalog đúng 6 chương, sort sourceChapterId tăng dần. |
| `questions` | Array<Object> | Metadata câu hỏi, unique theo id; không phân trang. |

**Phần tử chapters:**

| Name | Type | Description |
|---|---|---|
| `id` | UUID | UUID chapter Content Builder. |
| `sourceChapterId` | Integer | Số chương nguồn 1–6, không trùng. |
| `bankVersion` | String | Phiên bản khớp request và chapter metadata. |

**Phần tử questions:**

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

Ví dụ cấu trúc: mảng questions được minh họa một phần tử, không phải số lượng thực của bộ dữ liệu 2026.

```json
{
  "bankVersion": "dts-2026-600-v1",
  "licenseClass": "A1",
  "chapters": [
    {
      "id": "40000000-0000-4000-8000-000000000001",
      "sourceChapterId": 1,
      "bankVersion": "dts-2026-600-v1"
    },
    {
      "id": "40000000-0000-4000-8000-000000000002",
      "sourceChapterId": 2,
      "bankVersion": "dts-2026-600-v1"
    },
    {
      "id": "40000000-0000-4000-8000-000000000003",
      "sourceChapterId": 3,
      "bankVersion": "dts-2026-600-v1"
    },
    {
      "id": "40000000-0000-4000-8000-000000000004",
      "sourceChapterId": 4,
      "bankVersion": "dts-2026-600-v1"
    },
    {
      "id": "40000000-0000-4000-8000-000000000005",
      "sourceChapterId": 5,
      "bankVersion": "dts-2026-600-v1"
    },
    {
      "id": "40000000-0000-4000-8000-000000000006",
      "sourceChapterId": 6,
      "bankVersion": "dts-2026-600-v1"
    }
  ],
  "questions": [
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
}
```

`Cache-Control: no-store`; hash trong ví dụ là placeholder định dạng, không phải giá trị để consumer sử dụng.

### Error Codes / HTTP Errors

Response hiện tại **không có trường `code`**; không gán mã `VAL-400`, `AUTH-403` hoặc `RES-404` giả định.

| HTTP | Nhánh xử lý | Ý nghĩa |
|---|---|---|
| 400 | `BusinessValidationException` | Thiếu/blank/không hợp lệ license hoặc bankVersion; catalog không đúng 6 chương hợp lệ; metadata trùng câu không nhất quán; source không đạt điều kiện. |
| 404 | `ResourceNotFoundException` | Không tìm thấy chapter PUBLISHED/chưa xóa thuộc bankVersion đã chọn. |
| 403 | `AccessDeniedException` | Không đủ quyền admin/service token đọc nguồn. |
| 500 | Exception không có handler riêng | Lỗi ngoài các nhánh business/validation được xử lý rõ ràng. |

Lỗi tại security filter/ingress có thể khác `ErrorResponse`. Lỗi bind UUID/query, JSON không đọc được, sort không tồn tại hoặc ràng buộc DB không được tự coi là `400/409`: handler tổng quát của source hiện tại có thể trả `500`. Xem [lỗi chung](content_builder_contract_notes.md#part-3--error-contract-and-binding-boundary).

---

## Part 2 — Processing Specification

1. **Controller Layer**: kiểm quyền qua ExamSourceAccess; gọi `getBank(bankVersion, licenseClass)` và trả no-store.
2. **Catalog Query**: ChapterRepository.findPublishedByBankVersion dùng `deleted_at IS NULL`, `status='PUBLISHED'`, `metadata ->> 'bankVersion' = :bankVersion`, ORDER BY id. Không có chapter: 404; có nhưng số lượng khác 6: 400.
3. **Catalog Integrity**: mỗi chapter có UUID, đúng trạng thái/phiên bản; metadata.sourceChapterId là Number biểu diễn số nguyên hoặc chuỗi 1–6; các số 1–6 và UUID không trùng. Sắp catalog theo sourceChapterId.
4. **Licensed Read**: với mỗi chapter, gọi `getMetadata(chapterId, CHAPTER, license)` bằng cùng source snapshot. Áp dụng toàn bộ điều kiện published path, license, options/media/revision của metadata.
5. **Deduplication**: giữ thứ tự câu lần đầu gặp. Nếu cùng questionId xuất hiện nhiều chương, optionIds/isCritical/status/questionType/mediaFileIds/contentVersion phải giống nhau; khác thì lỗi 400. Nếu giống, hợp nhất chapterIds mà không sửa danh sách metadata nguồn.
6. **Result**: trả catalog và tập metadata distinct. Transaction read-only REPEATABLE_READ bao trùm lần đọc bank; không sinh đề, chấm điểm hoặc tạo attempt.

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

| Operation | Tables | Conditions | Expected Result |
|---|---|---|---|
| SELECT | chapters | PUBLISHED, chưa xóa, metadata.bankVersion khớp. | Catalog 6 chương. |
| SELECT | question_blocks / questions / question_options | Điều kiện của getMetadata cho từng chapter và cùng license. | Metadata nguồn đã kiểm chuẩn; không ghi DB. |

---

## Part 4 — Operational Notes

- Code bắt buộc đúng sáu chương nhưng không hardcode tổng số câu trả về là 600/250; số câu phụ thuộc dữ liệu published và license. Chương không có câu cho hạng đó vẫn nằm trong catalog.
- Với bộ 2026 đã import đúng, dữ liệu/fixture có B: 600 câu, 60 điểm liệt; A1: 250 câu, 20 điểm liệt. Đây là tính chất bộ dữ liệu đang có, không phải giới hạn tổng quát của endpoint.
- DTO chapter không chứa title. Không tự giả định response có tên chương hoặc tên bài thi.
- Đây là API nguồn dùng chung cho backend; không có phân trang, chọn quota, random đề hoặc dữ liệu người học.
