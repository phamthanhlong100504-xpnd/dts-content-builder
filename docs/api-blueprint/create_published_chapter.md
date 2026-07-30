# Tạo Chủ Đề Xuất Bản (Create Published Chapter)

## Part 0 — Classification & Identity

- **API Name**: Create Published Chapter
- **API Type**: Internal / Public
- **Module**: `content-builder`
- **Feature**: Chapter Management
- **Description**: Tạo mới bản ghi chủ đề với trạng thái lập tức chính thức là `PUBLISHED`. Yêu cầu kiểm tra nghiệp vụ khắt khe hơn: chủ đề phải được truyền kèm danh sách `questionBlocks` (cấu trúc câu hỏi) để đảm bảo chủ đề xuất bản có nội dung thực tế.
- **Related Tables**: `chapters`, `question_blocks`
- **Related Services**: `user-service`

---

## Part 1 — API Contract

### Endpoint

- **HTTP Method**: `POST`
- **URL**: `/api/v1/content-builder/chapters/published`
- **Content Type**: `application/json`

### Request

#### Headers & Request Body
*(Giống hoàn toàn với API Create Draft Chapter về header. Request Body bổ sung thêm mảng `questionBlocks` bắt buộc)*:

#### Request Body
| Name | Type | Required | Description | Validation Rules |
|---|---|---|---|---|
| `title` | String | Yes | Tên chủ đề/bài học | Không được để trống, 1 đến 255 ký tự |
| `metadata` | Object | No | Dữ liệu mở rộng | Chuẩn JSONB hợp lệ, mặc định `{}` |
| `questionBlocks` | Array<Object> | Yes | Danh sách các khối câu hỏi khởi tạo kèm theo | Mảng phải có tối thiểu 1 phần tử, tối đa 200. Tuân thủ validation rule của QuestionBlock Item bên dưới |

**Cấu trúc từng đối tượng trong mảng `questionBlocks` (QuestionBlock Item):**
| Name | Type | Required | Description | Validation Rules |
|---|---|---|---|---|
| `parentId` | String (UUID) | No | ID node cha trong cây (NULL nếu node gốc) | Chuẩn UUID, tham chiếu node khác trong cùng mảng hoặc đã tồn tại trong DB |
| `questionId` | String (UUID) | No | ID câu hỏi gắn vào node (NULL nếu node là container) | Chuẩn UUID, tham chiếu bảng `questions` |
| `title` | String | Yes | Tiêu đề nhóm câu hỏi | Không được để trống, tối đa 255 ký tự |
| `sortOrder` | Integer | No | Thứ tự hiển thị | Số nguyên >= 0, mặc định bằng chỉ số index |
| `metadata` | Object | No | Dữ liệu mở rộng | Chuẩn JSONB hợp lệ, mặc định `{}` |

---

### Response

- **Success Status**: `201 Created`
- **Response Body**: *(Giống với API Create Draft Chapter, trường `status` trả về `"PUBLISHED"`. Bổ sung trường `questionBlocks` chứa danh sách chi tiết các khối câu hỏi vừa được tạo kèm)*.

---

### Error Codes

| Error Code | HTTP Status | Business Meaning | Client Message |
|---|---|---|---|
| `AUTH-401` | 401 Unauthorized | Thiếu hoặc JWT token không hợp lệ | Authentication required or token invalid. |
| `AUTH-403` | 403 Forbidden | Người dùng không có quyền xuất bản chủ đề | You do not have permission to publish chapters. |
| `VAL-400` | 400 Bad Request | Payload vi phạm định dạng | Invalid request payload. |
| `VAL-422` | 422 Unprocessable Entity | Vi phạm quy tắc xuất bản (VD: mảng `questionBlocks` rỗng hoặc không được cung cấp, `questionId` tham chiếu câu hỏi không tồn tại) | Cannot publish chapter without valid question blocks. |
| `SYS-500` | 500 Internal Server Error | Lỗi hệ thống nội bộ | An unexpected internal server error occurred. |

---

## Part 2 — Processing Specification

1. **Controller Layer**:
   - Nhận HTTP Request POST tại endpoint `/api/v1/content-builder/chapters/published`.
   - Trích xuất `userId` và gọi Service Layer phương thức `createChapter(request, userId, "PUBLISHED")`.

2. **Service Layer**:
   - Kiểm tra quyền hạn xuất bản nội dung (`chapters:update` permission).
   - **Strict Business Validation cho trạng thái PUBLISHED**:
     - Mảng `questionBlocks` phải được cung cấp và có tối thiểu 1 phần tử.
     - Nếu `questionId` được cung cấp trong bất kỳ phần tử nào, kiểm tra sự tồn tại của câu hỏi trong bảng `questions` (`deleted_at IS NULL`).
   - Khởi tạo giao dịch (@Transactional), lưu bảng `chapters` với `status = "PUBLISHED"`.
   - Batch insert các bản ghi `question_blocks` với `status = "PUBLISHED"`, `chapter_id = chapter.id`.
   - Commit giao dịch và trả về Response DTO.

3. **Repository Layer**:
   - Thực hiện INSERT vào `chapters` và `question_blocks`.

4. **External Interaction**:
   - None.

5. **Validation**:
   - Yêu cầu kiểm tra tính đầy đủ của cấu trúc câu hỏi khắt khe hơn bản DRAFT.

---

## Part 3 — Data Interaction
- Tương tự như API Create Draft Chapter, trạng thái lưu vào DB là `'PUBLISHED'`.
- Bổ sung INSERT batch vào bảng `question_blocks`.

## Part 4 — Operational Notes
- **Audit Logging**: Ghi audit log quan trọng về việc xuất bản chủ đề trực tiếp lên hệ thống.
- **Metrics**: Metric `content_builder.chapter.created` với tag `status=published`.
