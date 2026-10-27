# DTS — Bàn giao dữ liệu mô phỏng 120 tình huống

## 1. Phạm vi

Bộ dữ liệu gồm **120 tình huống / 6 chương**, **15 bộ đề (10 tình huống/đề)**, **120 video MP4** và **120 ảnh đại diện WebP**. Tài liệu này hướng dẫn **kiểm tra và nạp media** qua `dts-media` vào MinIO; chưa triển khai API nghiệp vụ, chấm điểm hay giao diện mô phỏng.

**Mã nguồn quản lý:**

| Repository | Tệp | Vai trò |
| --- | --- | --- |
| `dts-content-builder` | `data/simulations/dts-simulation-120-video-v1/simulation-scenarios.json` | Nội dung, chương, đường dẫn, thời lượng, các mốc thời gian |
| `dts-content-builder` | `data/simulations/dts-simulation-120-video-v1/simulation-exam-sets.json` | 15 bộ đề và phân bổ chương |
| `dts-content-builder` | `tools/dts_simulation_data.py` | Kiểm tra JSON/assets, nạp và xác minh media |
| `dts-media` | `src/main/resources/db/migration/V6__add_simulation_media_policies.sql` | Flyway migration bổ sung chính sách upload video/ảnh |

Hai JSON thuộc **bộ nội dung V1 đã chốt**: script đối chiếu SHA-256 cố định. Không tự chỉnh sửa JSON hoặc các mốc chấm điểm khi chưa thống nhất phiên bản dữ liệu mới.

## 2. Đóng gói dữ liệu media bàn giao

Đính kèm **riêng** thư mục tài nguyên (không đưa video dung lượng lớn vào Git):

```text
<ASSET_ROOT>/
└── simulations/
    ├── ch1/    # 001–029
    ├── ch2/    # 030–043
    ├── ch3/    # 044–063
    ├── ch4/    # 064–073
    ├── ch5/    # 074–090
    └── ch6/    # 091–120
```

Mỗi tình huống có hai tệp đúng tên: `NNN.mp4` và `NNN_thumb.webp` (ví dụ: `ch1/001.mp4`, `ch1/001_thumb.webp`). Đường dẫn khai báo trong JSON là `simulations/chN/NNN.mp4` / `simulations/chN/NNN_thumb.webp`.

**Chuẩn tệp bắt buộc:** video phải là **container MP4 thật** (không chỉ đuôi `.mp4`), codec **H.264**, phát/tua được trên trình duyệt; ảnh phải là **WebP thật**. Nếu nguồn là MPEG-TS giả đuôi `.mp4`, đóng gói lại trên **bản sao** bằng FFmpeg, ví dụ:

```bash
ffmpeg -i input_mpegts.mp4 -map 0:v:0 -map '0:a?' -c copy -movflags +faststart output.mp4
```

Sau đó kiểm tra `ffprobe` và phát thử. Không ghi đè hoặc phát hành bộ media chưa được audit. Khi chuyển bộ tài nguyên sang môi trường khác, giữ nguyên cấu trúc/tên và tự chạy lại audit để lập SHA-256 của bản được bàn giao.

## 3. Yêu cầu môi trường

- Python 3 (script dùng thư viện chuẩn), `ffprobe`/FFmpeg trên `PATH`.
- `dts-media` kết nối PostgreSQL, MinIO và Kafka/worker xác minh upload; Identity Service cấp JWT được phép gọi Media API.
- Trước khi chạy Media Service phiên bản mới: **sao lưu database**, áp dụng migration V6 **qua Flyway** (không chỉnh bảng trực tiếp). V6 thêm `SIMULATION_VIDEO` (`video/mp4`, tối đa 2 GiB) và `SIMULATION_THUMBNAIL` (`image/webp`, tối đa 20 MiB), đều `PRIVATE`.
- Chuẩn bị bucket MinIO theo cấu hình của Media Service; xác thực truy cập bucket và kiểm tra Media API trả URL đã ký. Cấu hình endpoint/credential theo môi trường; **không đưa mật khẩu, JWT hoặc signed URL vào repo, log bàn giao**.
- **Giới hạn của công cụ hiện tại:** lệnh `upload`/`verify-media` chỉ chấp nhận Media API qua `http://localhost`/`127.0.0.1` và presigned MinIO URL trỏ về máy local. Khi triển khai ở máy khác/VPS, chạy công cụ tại máy có cổng dịch vụ tương ứng được đưa ra localhost (hoặc xây dựng/cấp duyệt công cụ triển khai riêng); **không trỏ script hiện tại trực tiếp đến public URL**.

## 4. Trình tự kiểm tra và nạp dữ liệu

Thực hiện từ thư mục gốc repo `dts-content-builder`. Các biến viết trong dấu `<...>` phải thay bằng đường dẫn thực tế; **`<ASSET_ROOT>` là thư mục chứa thư mục con `simulations/`**. Ví dụ dùng `python` (Windows có thể dùng `py -3`). Đặt inventory và checkpoint trong một thư mục trạng thái **ngoài Git repo**; mỗi môi trường có trạng thái riêng.

**Bước 1 — Kiểm tra bộ JSON và tệp nguồn:**

```bash
python tools/dts_simulation_data.py validate-json --repo .
python tools/dts_simulation_data.py audit-assets --repo . --assets-root "<ASSET_ROOT>" --inventory "<STATE_DIR>/asset-inventory.json" --require-ffprobe
```

Kết quả mong đợi: `JSON_STRUCTURE=PASS`, `JSON_SHA256=PASS`, `ASSET_AUDIT=PASS; VIDEOS=120; THUMBNAILS=120; FFMPEG_PROBED=120/120`. Công cụ đã cập nhật sẽ **từ chối container MPEG-TS đội lốt MP4**. Không nạp nếu bất kỳ kiểm tra nào thất bại.

**Bước 2 — Đăng nhập Identity Service** bằng tài khoản có quyền quản trị media và nạp access token vào biến môi trường `DTS_MEDIA_TOKEN` **trong tiến trình shell đang thao tác**, không lưu token vào file hoặc Git. API đã kiểm thử: `POST /api/v1/auth/login` với `username`, `password`, `deviceInfo`; lấy `data.accessToken`. Ví dụ PowerShell (điều chỉnh endpoint Identity theo môi trường):

```powershell
$user = Read-Host "DTS username"
$secure = Read-Host "DTS password" -AsSecureString
$ptr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
try {
    $body = @{username=$user; password=[Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr); deviceInfo="Simulation Media Import"} | ConvertTo-Json
    $r = Invoke-RestMethod -Method Post -Uri "http://127.0.0.1:8081/api/v1/auth/login" -ContentType "application/json" -Body ([Text.Encoding]::UTF8.GetBytes($body))
    if (-not $r.data.accessToken) { throw "LOGIN_FAILED" }
    $env:DTS_MEDIA_TOKEN = $r.data.accessToken
} finally {
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr)
    Remove-Variable body, r, secure -ErrorAction SilentlyContinue
}
```

Làm mới token khi hết hạn. Kiểm tra Media Service có thể trả URL đã ký để đọc các media READY.

**Bước 3 — Xem trước và upload thử một tình huống:**

```bash
python tools/dts_simulation_data.py upload --repo . --inventory "<STATE_DIR>/asset-inventory.json" --checkpoint "<STATE_DIR>/media-map.json" --media-api http://127.0.0.1:8080 --scenario 1
python tools/dts_simulation_data.py upload --repo . --inventory "<STATE_DIR>/asset-inventory.json" --checkpoint "<STATE_DIR>/media-map.json" --media-api http://127.0.0.1:8080 --scenario 1 --execute
python tools/dts_simulation_data.py verify-media --repo . --inventory "<STATE_DIR>/asset-inventory.json" --checkpoint "<STATE_DIR>/media-map.json" --media-api http://127.0.0.1:8080 --scenario 1 --range-check --full-sha-check
```

Lệnh `upload` **không có `--execute` chỉ là dry-run**. Với `--execute`, công cụ gọi `initialize`, upload lên presigned MinIO PUT, `confirm`, rồi đợi trạng thái `READY`. **Kiểm tra phát, tua video và thumbnail trong trình duyệt** trước khi nạp hàng loạt.

**Bước 4 — Upload theo đợt, kiểm tra toàn bộ:**

```bash
python tools/dts_simulation_data.py upload --repo . --inventory "<STATE_DIR>/asset-inventory.json" --checkpoint "<STATE_DIR>/media-map.json" --media-api http://127.0.0.1:8080 --limit 80 --execute
# Lặp lại lệnh trên, cấp JWT mới khi cần, cho tới khi TOTAL_READY=240/240.
python tools/dts_simulation_data.py verify-media --repo . --inventory "<STATE_DIR>/asset-inventory.json" --checkpoint "<STATE_DIR>/media-map.json" --media-api http://127.0.0.1:8080 --range-check --full-sha-check
```

Điều kiện hoàn tất: `UPLOAD_FINISHED; ... TOTAL_READY=240/240; PENDING=0` và `MEDIA_VERIFY=PASS; READY=240/240; HTTP_RANGE_206=120/120; REMOTE_SHA256_VERIFIED=240/240`.

## 5. Mapping, cập nhật và xử lý lỗi

- File `<STATE_DIR>/media-map.json` lưu ánh xạ theo khóa `001/video`, `001/thumbnail`, ... với `mediaId`, `objectKey`, SHA-256. **Media ID thuộc môi trường đã upload**, không sử dụng ID từ máy khác và không đưa checkpoint local vào Git.
- `asset-inventory.json` chứa **đường dẫn tuyệt đối của máy thực hiện audit**; mỗi thành viên/môi trường phải tự sinh inventory mới. Khi nội dung tệp thay đổi, tạo inventory/checkpoint phiên bản mới; không sử dụng hash/checkpoint cũ cho tệp mới.
- Media đã `READY` có cùng SHA-256 được script bỏ qua. Nếu còn `PENDING`, kiểm tra Kafka/worker và session tương ứng trước khi tiếp tục; **không xóa checkpoint, chạy đồng thời nhiều tiến trình upload hoặc tạo bản ghi trùng**.
- Thay video bằng bản MP4 mới phải tạo media object/mapping phiên bản mới, chỉ tái sử dụng thumbnail nếu SHA-256 không đổi. Không tự xóa media cũ trước khi xác nhận các hệ thống phụ thuộc và kế hoạch dọn dẹp.
- Để nội dung có thể truy xuất qua API ứng dụng, backend cần thiết kế/triển khai **lưu trữ mapping mediaId với tình huống**, migration và hợp đồng API riêng. Tool nạp media **chưa đồng nghĩa đã import dữ liệu nghiệp vụ vào database Content Builder/Examination**; frontend không đọc JSON local, checkpoint hoặc hard-code dữ liệu ở runtime.

## 6. Điều kiện nghiệm thu còn lại

Kết quả audit và SHA-256 xác nhận dữ liệu lưu trữ, **không thay thế** kiểm thử CORS/giải mã video trên giao diện thực, độ chính xác vị trí chấm điểm theo từng cảnh, phân quyền truy cập, hiệu năng và quyền sử dụng/phân phối nội dung. Các mốc `scoring_timeline` mới được kiểm tra **thứ tự toán học**, chưa đối chiếu hình ảnh thực. `passing_score=35` có trong JSON nguồn nhưng **chưa được phép dùng để quyết định đạt/trượt trên sản phẩm khi chưa duyệt nghiệp vụ**.

**Trạng thái đối chứng local khi lập tài liệu:** 120 video MP4 mới và 120 ảnh WebP đã đạt `READY=240/240`, Range `206=120/120`, SHA-256 tải từ MinIO `240/240`; video mẫu 001 phát/tua thành công trong Chrome. Kết quả này là chứng cứ **trên môi trường đã thử**, không tự động xác nhận mọi máy hoặc cấu hình triển khai khác.
