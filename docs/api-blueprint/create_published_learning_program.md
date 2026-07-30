# Tạo Chương Trình Học Xuất Bản (Create Published Learning Program)

## Part 0 — Classification & Identity

- **API Name**: Create Published Learning Program
- **API Type**: Internal / Public
- **Module**: `content-builder`
- **Feature**: Learning Program Management
- **Description**: Tạo mới bản ghi chương trình học với trạng thái lập tức chính thức là `PUBLISHED`. Yêu cầu kiểm tra nghiệp vụ khắt khe hơn: chương trình phải được truyền kèm danh sách `chapterBlocks` (cấu trúc cây chương/bài học) để đảm bảo chương trình xuất bản có nội dung thực tế, không rỗng.
- **Related Tables**: `learning_programs`, `chapter_blocks`
- **Related Services**: `user-service`

---

## Part 1 — API Contract

### Endpoint

- **HTTP Method**: `POST`
- **URL**: `/api/v1/content-builder/learning-programs/published`
- **Content Type**: `application/json`

### Request

#### Headers & Request Body
*(Giống hoàn toàn với API Create Draft Learning Program về header. Request Body bổ sung thêm mảng `chapterBlocks` bắt buộc)*:

#### Request Body
| Name | Type | Required | Description | Validation Rules |
|---|---|---|---|---|
| `title` | String | Yes | Tên chương trình học | Không được để trống, 1 đến 255 ký tự |
| `code` | String | No | Mã chương trình | Tối đa 100 ký tự, duy nhất |
| `description` | String | No | Mô tả chi tiết | TEXT |
| `metadata` | Object | No | Dữ liệu mở rộng | Chuẩn JSONB, mặc định `{}` |
| `chapterBlocks` | Array<Object> | Yes | Danh sách các khối chương/bài học khởi tạo kèm theo | Mảng phải có tối thiểu 1 phần tử, tối đa 500. Tuân thủ validation rule của ChapterBlock Item bên dưới |

**Cấu trúc từng đối tượng trong mảng `chapterBlocks` (ChapterBlock Item):**
| Name | Type | Required | Description | Validation Rules |
|---|---|---|---|---|
| `parentId` | String (UUID) | No | ID node cha (NULL nếu node gốc) | Chuẩn UUID, tham chiếu node khác trong cùng mảng hoặc đã tồn tại |
| `chapterId` | String (UUID) | No | ID chapter gắn vào node (NULL nếu container) | Chuẩn UUID, tham chiếu bảng `chapters` |
| `title` | String | Yes | Tiêu đề node | Không được để trống, tối đa 255 ký tự |
| `sortOrder` | Integer | No | Thứ tự hiển thị | Số nguyên >= 0, mặc định bằng chỉ số index |
| `metadata` | Object | No | Dữ liệu mở rộng | Chuẩn JSONB, mặc định `{}` |

---

### Response

- **Success Status**: `201 Created`
- **Response Body**: *(Giống với API Create Draft Learning Program, trường `status` trả về `"PUBLISHED"`. Bổ sung trường `chapterBlocks` chứa danh sách chi tiết các khối chương vừa được tạo kèm)*.

---

### Error Codes

| Error Code | HTTP Status | Business Meaning | Client Message |
|---|---|---|---|
| `AUTH-401` | 401 Unauthorized | Thiếu hoặc JWT token không hợp lệ | Authentication required or token invalid. |
| `AUTH-403` | 403 Forbidden | Người dùng không có quyền xuất bản chương trình | You do not have permission to publish learning programs. |
| `VAL-400` | 400 Bad Request | Payload vi phạm định dạng | Invalid request payload. |
| `VAL-409` | 409 Conflict | Mã chương trình (`code`) đã tồn tại | Learning program with this code already exists. |
| `VAL-422` | 422 Unprocessable Entity | Vi phạm quy tắc xuất bản (VD: mảng `chapterBlocks` rỗng, `chapterId` tham chiếu chapter không tồn tại) | Cannot publish learning program without valid chapter blocks. |
| `SYS-500` | 500 Internal Server Error | Lỗi hệ thống nội bộ | An unexpected internal server error occurred. |

---

## Part 2 — Processing Specification

1. **Controller Layer**:
   - Nhận HTTP Request POST tại endpoint `/api/v1/content-builder/learning-programs/published`.
   - Trích xuất `userId` và gọi Service Layer phương thức `createLearningProgram(request, userId, "PUBLISHED")`.

2. **Service Layer**:
   - Kiểm tra quyền hạn xuất bản nội dung (`learning-programs:update` permission).
   - Kiểm tra tính duy nhất của `code` (nếu có).
   - **Strict Business Validation cho trạng thái PUBLISHED**:
     - Mảng `chapterBlocks` phải được cung cấp và có tối thiểu 1 phần tử.
     - Nếu `chapterId` được cung cấp trong bất kỳ phần tử nào, kiểm tra sự tồn tại của chapter trong bảng `chapters` (`deleted_at IS NULL`).
   - Khởi tạo giao dịch (@Transactional), lưu bảng `learning_programs` với `status = "PUBLISHED"`.
   - Batch insert các bản ghi `chapter_blocks` với `status = "PUBLISHED"`, `learning_program_id = learningProgram.id`.
   - Commit giao dịch và trả về Response DTO.

3. **Repository Layer**:
   - Thực hiện INSERT vào `learning_programs` và `chapter_blocks`.

4. **External Interaction**:
   - None.

5. **Validation**:
   - Yêu cầu kiểm tra tính đầy đủ của cấu trúc chương khắt khe hơn bản DRAFT.

---

## Part 3 — Data Interaction
- Tương tự như API Create Draft Learning Program, trạng thái lưu vào DB là `'PUBLISHED'`.
- Bổ sung INSERT batch vào bảng `chapter_blocks`.

## Part 4 — Operational Notes
- **Audit Logging**: Ghi audit log quan trọng về việc xuất bản chương trình trực tiếp lên hệ thống.
- **Metrics**: Metric `content_builder.learning_program.created` với tag `status=published`.
