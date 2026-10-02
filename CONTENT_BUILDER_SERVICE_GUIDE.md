
## 1. Mục đích

Tài liệu này mô tả:

- phạm vi nghiệp vụ hiện tại của `dts-content-builder` liên quan bộ câu hỏi GPLX 2026;
- các thay đổi backend hỗ trợ lọc theo hạng GPLX;
- cấu trúc dữ liệu bank 2026;
- luồng media;
- bộ công cụ import/publish portable dùng được trên local, staging hoặc VPS;
- thứ tự cần thực hiện để production hoạt động tương đương môi trường local đã kiểm thử.

## 2. Vai trò của Content Builder

Luồng hiện tại:

```text
Question Bank 2026
    ↓
Content Builder
    ├── nội dung câu hỏi
    ├── đáp án
    ├── explanations
    ├── applicableLicenses
    ├── criticalLicenses
    ├── bankVersion
    └── mediaFileIds
          ↓
Practice Service
          ↓
Frontend
```

Content Builder là nguồn nội dung đã publish.

Practice sử dụng dữ liệu đó để phục vụ:

- ôn tập theo chương;
- luyện tập;
- thi thử;
- câu điểm liệt theo hạng;
- lịch sử và kết quả bài làm;
- hiển thị media thông qua `mediaFileId`.


## 3. Bank 2026 hiện tại

Phiên bản:

```text
dts-2026-600-v1
```

Cấu trúc:

```text
data/question-banks/2026/600/
├── questions.source.json
├── questions.json
├── critical-by-license.json
├── image-manifest.json
└── images/
```

Các đặc điểm đã kiểm tra:

```text
600 câu
6 chương
60 câu gắn cờ critical tổng
318 ảnh WebP
15 hạng GPLX
```

Tên ảnh:

```text
<questionId>.webp
```

Ví dụ:

```text
227.webp
301.webp
600.webp
```

## 4. Các hạng GPLX được hỗ trợ

```text
A1
A
B1
B
C1
C
D1
D2
D
BE
C1E
CE
D1E
D2E
DE
```

Metadata câu hỏi dùng:

```json
{
  "bankVersion": "dts-2026-600-v1",
  "sourceQuestionId": 1,
  "chapterId": 1,
  "applicableLicenses": ["A1", "A"],
  "isCritical": true,
  "criticalLicenses": ["A1"],
  "sourceImagePath": "images/..."
}
```

Ý nghĩa:

- `applicableLicenses`: câu áp dụng cho những hạng nào.
- `criticalLicenses`: câu là điểm liệt đối với những hạng nào.
- `isCritical`: cờ critical tổng của nguồn.
- `bankVersion`: xác định đúng phiên bản bank.


## 5. Thay đổi backend hiện tại

### 5.1. `InternalQuestionController`

File:

```text
src/main/java/com/dts/content_builder/api/controller/InternalQuestionController.java
```

API metadata hỗ trợ thêm:

```text
licenseClass
```

Ví dụ:

```text
GET /internal/questions/metadata
    ?contentId=...
    &contentType=...
    &licenseClass=A1
```

API batch cũng hỗ trợ:

```text
POST /internal/questions/batch?licenseClass=A1
```

---

### 5.2. `QuestionService` – lọc metadata theo hạng

Service hỗ trợ:

```java
getQuestionsMetadataForExam(
    UUID contentId,
    String contentType,
    String licenseClass
)
```

Quy tắc:

1. Nếu không truyền `licenseClass`, giữ hành vi cũ.
2. Nếu có hạng, normalize uppercase.
3. Chỉ chấp nhận 15 hạng hợp lệ.
4. Chỉ lấy câu:
   - `PUBLISHED`;
   - có metadata;
   - `bankVersion = dts-2026-600-v1`;
   - có `applicableLicenses`;
   - hạng yêu cầu nằm trong `applicableLicenses`.
5. Loại trùng question ID.

---

### 5.3. `QuestionService` – batch detail theo hạng

Service hỗ trợ:

```java
getQuestionsBatchForLicense(
    List<UUID> questionIds,
    String licenseClass
)
```

Nếu có `licenseClass`:

- xác thực hạng;
- kiểm tra bank 2026;
- kiểm tra `applicableLicenses`;
- kiểm tra `criticalLicenses`;
- từ chối câu không áp dụng cho hạng;
- tính `isCritical` theo:

```text
criticalLicenses.contains(licenseClass)
```

Điều này cho phép một câu là điểm liệt với một hạng nhưng không bắt buộc là điểm liệt với mọi hạng.


## 6. Media contract

Câu hỏi có ảnh sử dụng:

```text
mediaFileIds
```

Không lưu URL MinIO/object storage cố định trong nội dung câu hỏi.

Luồng:

```text
images/<questionId>.webp
    ↓
Media Service
    ↓
media UUID
    ↓
Content Builder mediaFileIds
    ↓
Practice mediaFileId
    ↓
Frontend
```

## 7. Bộ công cụ triển khai

Folder:

```text
tools/
```

Các file:

```text
prepare_question_bank_2026.py
import_question_bank_2026.py
upload_question_images_2026.py
publish_questions_2026.py
publish_chapters_2026.py
```

## 8. `prepare_question_bank_2026.py`

Mục đích:

- kiểm tra đủ 600 câu;
- kiểm tra ID 1–600;
- kiểm tra 6 chương;
- kiểm tra 60 câu critical tổng;
- kiểm tra `applicable_licenses`;
- kiểm tra đúng 318 ảnh;
- kiểm tra định dạng WebP;
- tính/kiểm tra SHA-256;
- chuẩn hóa manifest.

Script này làm việc với dữ liệu file và không phụ thuộc endpoint local.


## 9. `import_question_bank_2026.py`

Mục đích hiện tại:

- validation/preview kế hoạch import;
- kiểm tra cấu trúc CreateQuestionRequest;
- kiểm tra bankVersion;
- kiểm tra metadata;
- kiểm tra media requirement.

Script này không phải tool trực tiếp tạo toàn bộ dữ liệu production.

Tool trực tiếp thực hiện upload/publish là ba script ở các phần tiếp theo.

## 10. `upload_question_images_2026.py`

Script này đã được chuyển thành portable.

Không hard-code:

```text
127.0.0.1
localhost
D:/DTS/_local
```

### Cấu hình

Media API:

```text
--media-api
```

hoặc:

```text
DTS_MEDIA_API_URL
```

Token:

```text
DTS_MEDIA_TOKEN
```

Map file:

```text
--map-file
```

hoặc:

```text
DTS_MEDIA_MAP_FILE
```

### Luồng

```text
image-manifest.json
    ↓
xác thực 318 ảnh + SHA-256
    ↓
POST /api/v1/media/uploads/initialize
    ↓
nhận mediaId + sessionId + presignedUrl
    ↓
PUT ảnh vào presignedUrl
    ↓
POST confirm
    ↓
chờ Media status READY
    ↓
ghi questionId -> mediaId vào map
```

Script hỗ trợ resume:

```text
pendingByQuestionId
```

để tránh tạo media UUID mới nếu một lượt upload bị gián đoạn.

### Presigned URL

Script không giả định MinIO chạy ở:

```text
localhost:9000
```

Presigned URL chỉ cần là HTTP/HTTPS hợp lệ và truy cập được từ máy chạy script.

Do đó có thể dùng:

- MinIO trên VPS;
- S3-compatible storage;
- reverse proxy;
- domain HTTPS;
- storage endpoint riêng.


## 11. `publish_questions_2026.py`

### Cấu hình

Content Builder URL:

```text
--base-url
```

hoặc:

```text
DTS_CONTENT_BUILDER_URL
```

Token:

```text
DTS_IMPORT_TOKEN
```

Media map:

```text
--media-map
```

Checkpoint tùy chọn:

```text
--checkpoint
```

### Luồng

```text
questions.json
+
critical-by-license.json
+
question-media-map.json
    ↓
kiểm tra dữ liệu
    ↓
đọc các câu đã tồn tại trên Content Builder
    ↓
validate câu đã tồn tại
    ↓
POST câu mới nếu dùng --apply
    ↓
publish question
    ↓
ghi sourceQuestionId -> Content Builder UUID
```

Mặc định nếu không dùng:

```text
--apply
```

script chỉ kiểm tra dữ liệu và API, không POST câu mới.

---

## 12. `publish_chapters_2026.py`

### Cấu hình

Content Builder URL:

```text
--base-url
```

hoặc:

```text
DTS_CONTENT_BUILDER_URL
```

Token:

```text
DTS_IMPORT_TOKEN
```

Question map:

```text
--question-map
```

Checkpoint tùy chọn:

```text
--checkpoint
```

### Dữ liệu chương

```text
Chương 1: 180 câu
Chương 2: 25 câu
Chương 3: 58 câu
Chương 4: 37 câu
Chương 5: 185 câu
Chương 6: 115 câu
```

Tổng:

```text
600 câu
```

Script kiểm tra Content Builder đã có đủ 600 câu và UUID trong map khớp database trước khi tạo chapter.

Mặc định không có `--apply` thì chỉ kiểm tra.

## 13. Thứ tự triển khai

### Bước 1 – Có source code và bank data

Cần có:

```text
data/question-banks/2026/600/
tools/
```

### Bước 2 – Validate bank

Ví dụ:

```bash
python tools/prepare_question_bank_2026.py   data/question-banks/2026/600
```

Mục tiêu:

```text
600 câu hợp lệ
318 ảnh hợp lệ
manifest hợp lệ
license metadata hợp lệ
```

### Bước 3 – Cấu hình Media production

Ví dụ Linux/VPS:

```bash
export DTS_MEDIA_API_URL="https://media.example.com"
export DTS_MEDIA_TOKEN="<JWT>"
export DTS_MEDIA_MAP_FILE="/opt/dts/import/question-media-map.json"
```

### Bước 4 – Upload media

Có thể test với giới hạn nhỏ trước:

```bash
python tools/upload_question_images_2026.py   --upload   --limit 1
```

Sau khi kiểm tra thành công:

```bash
python tools/upload_question_images_2026.py   --upload
```

Kết quả cần có:

```text
318/318 ảnh trong map
```

Map cuối cùng phải có:

```json
{
  "bankVersion": "dts-2026-600-v1",
  "mediaByQuestionId": {
    "227": "...uuid...",
    "228": "...uuid..."
  },
  "pendingByQuestionId": {}
}
```

### Bước 5 – Cấu hình Content Builder production

```bash
export DTS_CONTENT_BUILDER_URL="https://content-builder.example.com"
export DTS_IMPORT_TOKEN="<JWT>"
```

### Bước 6 – Dry-run publish questions

```bash
python tools/publish_questions_2026.py   --media-map /opt/dts/import/question-media-map.json
```

Không có `--apply` nghĩa là chưa tạo câu mới.

Nếu validation thành công mới thực hiện bước tiếp.


### Bước 7 – Publish 600 questions

```bash
python tools/publish_questions_2026.py   --media-map /opt/dts/import/question-media-map.json   --checkpoint /opt/dts/import/content-builder-question-map.json   --apply
```

Kết quả cuối cùng:

```text
600/600 câu
```

Checkpoint:

```text
content-builder-question-map.json
```

### Bước 8 – Dry-run chapters

```bash
python tools/publish_chapters_2026.py   --question-map /opt/dts/import/content-builder-question-map.json
```

Nếu kiểm tra thành công mới tạo chapter.

### Bước 9 – Publish 6 chapters

```bash
python tools/publish_chapters_2026.py   --question-map /opt/dts/import/content-builder-question-map.json   --checkpoint /opt/dts/import/content-builder-chapter-map.json   --apply
```

Kết quả:

```text
6/6 chương
```

## 14. Token và secret

Không commit:

```text
DTS_MEDIA_TOKEN
DTS_IMPORT_TOKEN
password
secret
access key
presigned URL
```

## 15. Map/checkpoint production

Các file map/checkpoint là dữ liệu phát sinh theo môi trường:

```text
question-media-map.json
content-builder-question-map.json
content-builder-chapter-map.json
```

## 16. Smoke test Content Builder

### Metadata không truyền hạng

Kỳ vọng:

```text
giữ hành vi cũ
```

### Với `licenseClass=A1`

Kỳ vọng:

```text
chỉ trả câu có A1 trong applicableLicenses
```

### Batch với `licenseClass=A1`

Kỳ vọng:

- câu thuộc A1;
- `isCritical` đúng theo `criticalLicenses` của A1.

### Hạng không hợp lệ

Ví dụ:

```text
XYZ
```

Kỳ vọng:

```text
BusinessValidationException
```

### Media

Chọn một câu có ảnh:

```text
Content Builder mediaFileIds
    ↓
Media API tìm thấy UUID
    ↓
media READY
    ↓
object tồn tại
```

---

## 17. Smoke test xuyên service

Sau khi Content Builder production hoàn chỉnh:

```text
Content Builder
    ↓
Practice
    ↓
Frontend
```

Kiểm tra:

1. chọn hạng GPLX;
2. kiểm tra thống kê câu;
3. ôn theo chương;
4. kiểm tra ảnh;
5. tạo bài thi/luyện tập;
6. kiểm tra câu đúng hạng;
7. kiểm tra câu điểm liệt;
8. kiểm tra ảnh trong bài;
9. nộp bài;
10. xem kết quả;
11. xem lịch sử;
12. kiểm tra ảnh trong review.

---

## 18. Kết luận

Luồng production hoàn chỉnh:

```text
Bank data
    ↓
validate
    ↓
upload 318 ảnh
    ↓
publish 600 questions
    ↓
publish 6 chapters
    ↓
Content Builder
    ↓
Practice
    ↓
Frontend
```

Sau khi import/publish hoàn tất, các script không cần chạy thường xuyên. Chúng là công cụ bootstrap/migration dữ liệu, không phải dependency runtime của Content Builder.
