# Lấy Metadata Câu Hỏi Nội Bộ (Get Questions Metadata)

Tài liệu thiết kế API Blueprint cho nghiệp vụ lấy danh sách ID câu hỏi và ID các đáp án (metadata) dành riêng cho các service khác gọi nội bộ (như Examination Service gọi để xáo trộn đáp án khi sinh đề thi).
Tuân thủ tiêu chuẩn kiến trúc `/api/{version}/{service}/{object}/` và đặc tả `rules/docx/java/api-blueprint-generator.md`.

---

## Part 0 — Classification & Identity

- **API Name**: Get Questions Metadata (Internal)
- **API Type**: Internal
- **Module**: `content-builder`
- **Feature**: Question Management
- **Description**: Trả về danh sách metadata (bao gồm `id` câu hỏi và mảng `optionIds` của các đáp án) dựa trên cấu trúc của một content cụ thể (ví dụ: một `CHAPTER` hoặc một `LEARNING_PROGRAM`).
- **Related Tables**: `questions`, `question_options`, `chapter_blocks`
- **Related Services**: Examination Service (Caller)

---

## Part 1 — API Contract

### Endpoint

- **HTTP Method**: `GET`
- **URL**: `/api/v1/content-builder/internal/questions/metadata`
- **Content Type**: `application/json`

### Request

#### Path Variables
- None

#### Query Parameters
| Name | Type | Required | Description | Validation Rules |
|---|---|---|---|---|
| `contentId` | String (UUID) | Yes | Định danh nội dung cha (Chương hoặc Khóa học) | UUIDv4/UUIDv7 |
| `contentType` | String | Yes | Loại nội dung cha | Chỉ chấp nhận: `CHAPTER`, `LEARNING_PROGRAM` |

#### Headers
| Name | Type | Required | Description | Validation Rules |
|---|---|---|---|---|
| `X-Request-ID` | String | No | Trace ID định danh luồng request | UUIDv4, tối đa 64 ký tự |
| `X-Service-Name` | String | Yes | Tên của service gọi đến (vd: `examination`) | Dùng cho mục đích audit log |

*(Lưu ý: Do đây là Internal API, không cần JWT token người dùng, hệ thống bảo mật bằng Network policy / Gateway chặn ngoài)*

---

### Response

- **Success Status**: `200 OK`

#### Response Body
Trả về mảng JSON chứa metadata của từng câu hỏi:

| Name | Type | Description |
|---|---|---|
| `id` | String (UUID) | ID của câu hỏi |
| `optionIds` | Array<String(UUID)> | Mảng danh sách các ID đáp án thuộc về câu hỏi này |

**Ví dụ:**
```json
[
  {
    "id": "q1-uuid",
    "optionIds": ["opt1-uuid", "opt2-uuid", "opt3-uuid", "opt4-uuid"]
  },
  {
    "id": "q2-uuid",
    "optionIds": ["opt5-uuid", "opt6-uuid", "opt7-uuid"]
  }
]
```

---

### Error Codes

| Error Code | HTTP Status | Business Meaning | Client Message |
|---|---|---|---|
| `VAL-400` | 400 Bad Request | Tham số không hợp lệ (sai type) | Invalid request parameters. |
| `RES-404` | 404 Not Found | Không tìm thấy content theo ID | Content not found. |
| `SYS-500` | 500 Internal Server Error | Lỗi hệ thống | An unexpected internal server error occurred. |

---

## Part 2 — Processing Specification

1. **Controller Layer**:
   - Nhận GET request tại `/api/v1/content-builder/internal/questions/metadata`.
   - Validate `contentType` và `contentId`.
   - Gọi Service Layer: `getQuestionsMetadataForExam(contentId, contentType)`.

2. **Service Layer**:
   - Tùy thuộc vào `contentType`:
     - Nếu là `CHAPTER`: Truy vấn danh sách `question_id` từ bảng `question_blocks` nơi `chapter_id = contentId AND question_id IS NOT NULL AND deleted_at IS NULL`.
     - Nếu là `LEARNING_PROGRAM`:
       - Bước 1: Tìm danh sách `chapter_id` từ bảng `chapter_blocks` nơi `learning_program_id = contentId AND deleted_at IS NULL`.
       - Bước 2: Truy vấn danh sách `question_id` từ bảng `question_blocks` nơi `chapter_id IN (danh sách trên) AND question_id IS NOT NULL AND deleted_at IS NULL`.
   - Thu thập tập hợp các `question_id` thu được.
   - Truy vấn bảng `question_options` để lấy danh sách các options (với điều kiện `deleted_at IS NULL`) gom nhóm theo `question_id`.
   - Tạo mảng DTO kết quả. Trả về mảng rỗng `[]` nếu không có câu hỏi nào.

3. **Repository Layer**:
   - Dùng Custom Query để join `chapter_blocks`, `questions` và `question_options` để tối ưu số lần gọi database (hoặc dùng Hibernate fetch).

4. **External Interaction**:
   - None.

5. **Validation**:
   - Kiểm tra `contentType` hợp lệ.

---

## Part 3 — Data Interaction

- **Operation 1**:
  - **Operation Type**: `SELECT`
  - **Target Table**: `chapter_blocks` JOIN `question_options`
  - **Conditions**: Lọc theo `learning_program_id` hoặc `chapter_id` phụ thuộc vào `contentType`, và `deleted_at IS NULL`.
  - **Expected Result**: Danh sách câu hỏi kèm các ID lựa chọn.

---

## Part 4 — Operational Notes

- **Performance**: API này được gọi thường xuyên mỗi khi có 1 lượt thi mới được khởi tạo bên `examination`. Do đó, cần tối ưu câu query tránh N+1.
- **Security**: Endpoint `/**/internal/**` phải được cấu hình chặn trên Spring Cloud Gateway không cho public từ ngoài Internet.
