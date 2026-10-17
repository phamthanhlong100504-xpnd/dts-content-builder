# Chạy Content Builder từ source của nhóm

Tài liệu này mô tả source đã rà soát ngày 17/10/2026. Java và các migration V2–V5 được giữ nguyên. Source không chứa bản sao dữ liệu cá nhân hay trạng thái database đang chạy trên máy lập trình.

## Thành phần cần có

- JDK 21; dùng Gradle Wrapper `gradlew.bat` trên Windows hoặc `./gradlew` trên Linux. Không cần cài Gradle riêng.
- PostgreSQL; môi trường local DTS dùng cổng host 5435 cho Content Builder, database `content-builder`.
- Identity để đăng nhập lấy JWT; Gateway cổng 8888 để giao diện gọi API; Media và storage của Media để hiển thị ảnh.
- Python 3 để nạp bộ dữ liệu từ `data/` bằng các công cụ trong `tools/`.
- Examination cần source đọc ngân hàng câu hỏi nội bộ. Hai service phải dùng cùng JWT root secret của môi trường đó.

`docker-compose.prod.yml` chỉ khởi chạy ứng dụng Content Builder. Nó không tạo PostgreSQL, MinIO, Media, Identity hoặc tự import nội dung. Build Java thành công không đồng nghĩa database đã có nội dung.

## Checkout đúng code

Nhóm dùng commit/nhánh đã tích hợp đủ bộ 600 câu, internal exam source và reference content; checkout `main` cũ sẽ chưa có các phần này. Sau khi PR được merge, thành viên mới có thể clone repo và checkout `main` mới.

`.agents` là submodule tài liệu đã có trong lịch sử Git; không xóa gitlink hoặc `.gitmodules` trong đợt bàn giao này. Submodule không phải dependency của Gradle build hiện tại.

## Database có sẵn và database trống là hai trường hợp khác nhau

**Máy đã có database đang sử dụng:** không chạy lại bootstrap V2–V5 và không import lại tùy tiện. Giữ dữ liệu, migration history và cấu hình đã hoạt động. Rà soát schema/history trước mọi nâng cấp.

**Máy mới, database trống:** tạo đúng database Content Builder bằng quy trình của nhóm. Dùng tài khoản PostgreSQL của môi trường mới, không dùng thông tin đăng nhập mặc định ghi trong source.

File `src/main/resources/application.yaml` hiện đặt `spring.flyway.enabled=false` và `hibernate.ddl-auto=update`. Vì vậy đặt SQL trong thư mục migration chưa làm SQL tự chạy. Chế độ khởi chạy bên dưới tắt Hibernate DDL để schema chỉ được tạo bởi lệnh SQL rõ ràng.

V3 dùng `trigger_set_updated_at()` trước khi V4 định nghĩa hàm. File tiền đề `docs/sql/00__bootstrap_trigger_set_updated_at.sql` cung cấp đúng thân hàm từ V4, kiểm tra tên database và từ chối database `public` đã có bảng. Các migration V2–V5 không bị sửa.

Trên Windows PowerShell, từ thư mục repo, khi đã có `psql.exe` và cấu hình `PGHOST`, `PGPORT`, `PGDATABASE`, `PGUSER` cùng cơ chế cấp mật khẩu PostgreSQL của nhóm:

```powershell
# Chỉ dành cho database Content Builder MỚI, TRỐNG.
# Ví dụ local; thay bằng thông tin database mới đã được cấp.
$env:PGHOST = 'localhost'
$env:PGPORT = '5435'
$env:PGDATABASE = 'content-builder'
$env:PGUSER = 'postgres'

psql.exe -X -v ON_ERROR_STOP=1 --single-transaction `
  -v "expected_db=$env:PGDATABASE" `
  -f docs/sql/00__bootstrap_trigger_set_updated_at.sql `
  -f src/main/resources/db/migration/V2__init_question_schema.sql `
  -f src/main/resources/db/migration/V3__init_chapter_schema.sql `
  -f src/main/resources/db/migration/V4__init_learning_program_schema.sql `
  -f src/main/resources/db/migration/V5__add_reference_content_tables.sql
if ($LASTEXITCODE -ne 0) { throw 'Bootstrap failed; stop and review the SQL error.' }
```

Nếu dùng client `psql` trong container, có thể sao chép riêng các file SQL vào một thư mục tạm của container PostgreSQL đã chọn và chạy cùng một lệnh `psql`, giữ nguyên thứ tự, `--single-transaction`, `ON_ERROR_STOP` và `expected_db`. Không đổi sang container/database của service khác.

Bootstrap này tạo schema bằng các file checked-in nhưng **không tạo Flyway history**. Không bật Flyway tự động trên database đã bootstrap theo cách này. Muốn chuyển cách quản lý migration phải có kế hoạch baseline/nâng cấp riêng. V5 không phải script chạy lặp trên database đã có hai bảng reference content.

Các bảng sau bootstrap: `questions`, `question_options`, `chapters`, `question_blocks`, `learning_programs`, `chapter_blocks`, `traffic_signs`, `memory_tips`. Việc SQL chạy thành công trên database mới vẫn phải được xác nhận tại máy nhận; đợt rà soát Git không chạy database.

## Khởi chạy ứng dụng trên máy mới

Mở PowerShell riêng. Đặt biến trong phiên này; không ghi bí mật vào file Git. JWT secret phải lấy đúng từ cấu hình được nhóm cấp, không tự tạo một secret khác với Identity/Examination.

```powershell
Set-Location 'D:\DTS\dts-content-builder'
$env:SPRING_DATASOURCE_URL = 'jdbc:postgresql://localhost:5435/content-builder'
$env:SPRING_DATASOURCE_USERNAME = 'postgres'
$env:SPRING_FLYWAY_ENABLED = 'false'
$env:SPRING_JPA_HIBERNATE_DDL_AUTO = 'none'
$env:EUREKA_CLIENT_ENABLED = 'false'

# Nhập bí mật ẩn; chỉ chuyển thành chuỗi trong môi trường tiến trình con.
function Read-ProcessSecret([string]$Prompt) {
    $value = Read-Host $Prompt -AsSecureString
    $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($value)
    try { [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer) }
    finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer) }
}
$env:SPRING_DATASOURCE_PASSWORD = Read-ProcessSecret 'PostgreSQL password'
$env:JWT_SECRET = Read-ProcessSecret 'Shared Base64 JWT root secret of this environment'
try {
    .\gradlew.bat bootRun
} finally {
    Remove-Item Env:SPRING_DATASOURCE_PASSWORD -ErrorAction SilentlyContinue
    Remove-Item Env:JWT_SECRET -ErrorAction SilentlyContinue
}
```

`JWT_SECRET` ở đây trực tiếp override Spring property `jwt.secret`. Cấu hình hiện có còn tham chiếu `JWT_ACCESS_SECRET`; không tự thay giá trị đang dùng giữa các service. Ứng dụng nghe cổng 8082. Trên máy đang hoạt động, giữ cách khởi chạy đã được xác nhận; không cần khởi động lại chỉ để commit code.

Gateway chạy Java trực tiếp trên Windows cần `CONTENT_BUILDER_SERVICE_URL=http://localhost:8082`. Examination dùng `CONTENT_BUILDER_URL=http://localhost:8082` cho client riêng; đây là hai tên biến của hai repo khác nhau. Cấu hình các service còn lại theo hướng dẫn tổng thể DTS.

## Nạp nội dung để giao diện có dữ liệu

Source có 600 câu, 6 chương, 318 ảnh câu hỏi, 221 biển báo/221 ảnh và 12 mẹo. Chúng là dữ liệu nguồn, chưa phải các hàng trong database hoặc object trong MinIO của máy mới.

Thứ tự nạp:

1. Media của môi trường mới có schema/policy phù hợp và storage sử dụng được. Bộ biển báo cần policy `TRAFFIC_SIGN` ở repo Media.
2. Upload 318 ảnh câu hỏi bằng `tools/upload_question_images_2026.py` để lấy Media UUID của môi trường mới.
3. Publish 600 câu bằng `tools/publish_questions_2026.py`, rồi 6 chương bằng `tools/publish_chapters_2026.py`.
4. Upload 221 ảnh biển báo bằng `tools/upload_traffic_sign_images.py`.
5. Publish biển báo bằng `tools/publish_traffic_signs_2026.py` với map ảnh vừa tạo.
6. Publish 12 mẹo bằng `tools/publish_memory_tips_2026.py`.

Đọc `CONTENT_BUILDER_SERVICE_GUIDE.md` cho bộ 600 câu và `DEPLOY_REFERENCE_CONTENT.md` cho biển báo/mẹo. Các publisher yêu cầu tài khoản và quyền phù hợp; công cụ upload yêu cầu token Media.

Map/checkpoint là trạng thái riêng theo môi trường: giữ ở ngoài repo, lưu lại để chạy tiếp khi bị ngắt. Không xóa checkpoint để nhập lại từ đầu, không chép Media UUID từ máy khác và không commit JWT, database dump hoặc MinIO object state. Không chạy lại `prepare_question_bank_2026.py` trên bản release đã chốt vì công cụ đó tái tạo JSON.

Khi code đã push, thành viên khác vẫn cần hoàn thành cấu hình, schema, upload và publish ở môi trường của họ. Riêng repo Content Builder không tái tạo toàn bộ trạng thái Identity, Media, Practice, Examination, Progress và Result của máy hiện tại.
