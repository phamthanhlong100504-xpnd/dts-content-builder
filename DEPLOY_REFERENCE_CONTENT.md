# Bàn giao dữ liệu biển báo và mẹo ghi nhớ

Phạm vi: **221 biển báo, 221 ảnh WebP và 12 mẹo**. Bộ 600 câu/318 ảnh và 6 chương dùng `CONTENT_BUILDER_SERVICE_GUIDE.md`; khởi chạy local và bootstrap database mới dùng `docs/LOCAL_SETUP.md`.

## File phải có trong checkout

- `src/main/resources/db/migration/V5__add_reference_content_tables.sql` và đầy đủ Entity/Repository/Service/Mapper/Form/Response/Controller của `TrafficSign`, `MemoryTip`.
- `data/question-banks/2026/traffic_signs/traffic-signs.json`, `sign-manifest.json`, `images/*.webp`.
- `data/question-banks/2026/memory_tips/memory-tips.json`.
- `tools/upload_traffic_sign_images.py`, `tools/publish_traffic_signs_2026.py`, `tools/publish_memory_tips_2026.py`.

Không cần `validate_reference_content_2026.py` hoặc `REFERENCE_CONTENT_SHA256SUMS.txt`: hai file này không có trong snapshot local được bàn giao. Các lệnh dưới dùng công cụ thực tế có trong source. Không lấy các checksum cũ của tài liệu trước làm bằng chứng cho file migration hiện tại.

## Schema và Media

Content Builder hiện tắt Flyway. Máy mới cần bootstrap có kiểm soát theo `docs/LOCAL_SETUP.md`, gồm file tiền đề trigger và V2→V5. Máy đã có bảng không chạy lại bootstrap/V5 tùy tiện. Không sửa các migration đã được thực thi. Đợt rà soát Git không chạy SQL, không xác nhận migration history của database Content Builder hiện tại.

Repo `dts-media` cần schema/policy `TRAFFIC_SIGN` phù hợp. Migration của Media và endpoint/bucket MinIO được đối chiếu khi bàn giao riêng repo Media. Không mặc định image Java Content Builder chứa `tools/` hoặc `data/` để tự import. Hãy chạy importer từ máy có checkout đầy đủ và Python 3.

## Xác minh dữ liệu nguồn, chưa ghi API

Từ gốc repo:

```bash
python3 tools/upload_traffic_sign_images.py
python3 tools/publish_memory_tips_2026.py --bank-version dts-2026-600-v1
```

Trên Windows đổi `python3` thành Python launcher của máy, ví dụ `py -3`. Hai lệnh không có `--upload`, `--apply` hoặc `--check-api` ở trên chỉ kiểm tra offline. Đọc đầu ra: 221 ảnh và 12 mẹo. Bộ ảnh câu hỏi có manifest SHA256 riêng trong thư mục `600`.

## Cấp URL, token và checkpoint riêng

Ví dụ Bash cho môi trường mới; thay host thật. Publisher mẹo yêu cầu URL có port rõ ràng, vì vậy HTTPS mặc định vẫn ghi `:443`:

```bash
export DTS_MEDIA_API_URL='https://media.example.com:443'
export DTS_CONTENT_BUILDER_URL='https://content-builder.example.com:443'
export DTS_IMPORT_DIR="$HOME/dts-import-2026"
mkdir -p "$DTS_IMPORT_DIR"
chmod 700 "$DTS_IMPORT_DIR"

read -r -s -p 'Media JWT: ' DTS_MEDIA_TOKEN; echo
export DTS_MEDIA_TOKEN
read -r -s -p 'Content Builder ADMIN JWT: ' DTS_IMPORT_TOKEN; echo
export DTS_IMPORT_TOKEN

export DTS_SIGN_MEDIA_MAP="$DTS_IMPORT_DIR/traffic-sign-media-map.json"
export DTS_SIGN_MAP="$DTS_IMPORT_DIR/content-builder-traffic-sign-map.json"
export DTS_TIP_MAP="$DTS_IMPORT_DIR/content-builder-memory-tips-map.json"
```

Token lấy qua Identity của môi trường đích. Không commit chúng. Không chép map của máy đã có nội dung sang môi trường mới. Mỗi map thuộc đúng môi trường và lần import của nó; giữ lại để tiếp tục nếu bị ngắt.

## Upload 221 ảnh và publish 221 biển báo

Các lệnh có `--upload`/`--apply` bên dưới **ghi dữ liệu**. Chỉ dùng khi đã chọn đúng môi trường mới và hoàn tất schema/storage. Người vận hành tự chạy, không liên quan tới bước commit Git.

```bash
# Upload một ảnh trước, xác nhận READY và presigned PUT truy cập được.
python3 tools/upload_traffic_sign_images.py \
  --media-api "$DTS_MEDIA_API_URL" --map-file "$DTS_SIGN_MEDIA_MAP" \
  --limit 1 --upload

# Tiếp tục tới đủ 221; công cụ dùng checkpoint để bỏ qua ảnh đã hoàn thành.
python3 tools/upload_traffic_sign_images.py \
  --media-api "$DTS_MEDIA_API_URL" --map-file "$DTS_SIGN_MEDIA_MAP" \
  --limit 221 --upload

# Đối chiếu source và map đủ 221 ảnh, không ghi API.
python3 tools/publish_traffic_signs_2026.py --media-map "$DTS_SIGN_MEDIA_MAP"

# Đối chiếu API read-only trước khi ghi.
python3 tools/publish_traffic_signs_2026.py \
  --media-map "$DTS_SIGN_MEDIA_MAP" --base-url "$DTS_CONTENT_BUILDER_URL" \
  --checkpoint "$DTS_SIGN_MAP" --allow-remote --check-api

python3 tools/publish_traffic_signs_2026.py \
  --media-map "$DTS_SIGN_MEDIA_MAP" --base-url "$DTS_CONTENT_BUILDER_URL" \
  --checkpoint "$DTS_SIGN_MAP" --allow-remote --limit 1 --apply

python3 tools/publish_traffic_signs_2026.py \
  --media-map "$DTS_SIGN_MEDIA_MAP" --base-url "$DTS_CONTENT_BUILDER_URL" \
  --checkpoint "$DTS_SIGN_MAP" --allow-remote --limit 221 --apply

python3 tools/publish_traffic_signs_2026.py \
  --media-map "$DTS_SIGN_MEDIA_MAP" --base-url "$DTS_CONTENT_BUILDER_URL" \
  --checkpoint "$DTS_SIGN_MAP" --allow-remote --check-api
```

Nếu cấu hình storage trả URL `localhost` không truy cập được từ máy import, sửa cấu hình môi trường theo cách quản trị Media trước; không tạo thêm media ID để thử liên tục. Dừng khi có pending upload hoặc source/checkpoint/API không khớp.

## Publish 12 mẹo

```bash
python3 tools/publish_memory_tips_2026.py \
  --bank-version dts-2026-600-v1 --base-url "$DTS_CONTENT_BUILDER_URL" \
  --checkpoint "$DTS_TIP_MAP" --allow-remote --check-api

python3 tools/publish_memory_tips_2026.py \
  --bank-version dts-2026-600-v1 --base-url "$DTS_CONTENT_BUILDER_URL" \
  --checkpoint "$DTS_TIP_MAP" --allow-remote --limit 1 --apply

python3 tools/publish_memory_tips_2026.py \
  --bank-version dts-2026-600-v1 --base-url "$DTS_CONTENT_BUILDER_URL" \
  --checkpoint "$DTS_TIP_MAP" --allow-remote --limit 12 --apply

python3 tools/publish_memory_tips_2026.py \
  --bank-version dts-2026-600-v1 --base-url "$DTS_CONTENT_BUILDER_URL" \
  --checkpoint "$DTS_TIP_MAP" --allow-remote --check-api
```

Mong đợi sau khi nạp thành công: 221 biển báo PUBLISHED có Media READY, 12 mẹo PUBLISHED; danh mục biển báo gồm `cam=59`, `nguy_hiem=83`, `hieu_lenh=41`, `chi_dan=25`, `phu=13`. Đây là tiêu chí bàn giao dữ liệu, không phải kết quả chạy lại đã được xác nhận trong đợt rà soát Git.

Không import lại trên database đang phục vụ người dùng khi chỉ cần push source. Sau khi dữ liệu đã nạp, ứng dụng dùng các hàng trong PostgreSQL và UUID Media; importer không phải tiến trình runtime cần chạy liên tục.
