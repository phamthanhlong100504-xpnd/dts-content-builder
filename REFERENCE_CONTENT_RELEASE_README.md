# DTS Reference Content 2026

Bộ dữ liệu gồm **221 biển báo/221 ảnh WebP và 12 mẹo ghi nhớ**. Bộ 600 câu và 6 chương đã thuộc nhánh `feature/2026-license-question-bank-media`.

## Đọc tài liệu

- `docs/LOCAL_SETUP.md`: cấu hình local, database mới, khởi chạy và thứ tự nạp dữ liệu.
- `CONTENT_BUILDER_SERVICE_GUIDE.md`: ngân hàng 600 câu, upload ảnh, publish câu/chương.
- `DEPLOY_REFERENCE_CONTENT.md`: upload ảnh biển báo, publish biển báo/mẹo.

## Thành phần cần commit cùng chức năng

- Java của `TrafficSign` và `MemoryTip` trong cấu trúc package hiện có.
- `src/main/resources/db/migration/V5__add_reference_content_tables.sql` nguyên trạng.
- `data/question-banks/2026/traffic_signs/` và `memory_tips/`.
- Ba công cụ `upload_traffic_sign_images.py`, `publish_traffic_signs_2026.py`, `publish_memory_tips_2026.py` trong `tools/`.

Media cần policy `TRAFFIC_SIGN` ở repo riêng. Không có Media UUID cố định dùng chung giữa các máy. Source Git không chứa database/MinIO của máy lập trình.

Tài liệu này thay các đường dẫn cũ tới file không có trong checkout; không thay đổi Java, các API, dữ liệu nguồn hoặc migration V2–V5. Việc dựng database mới và nạp nội dung là bước riêng do người vận hành thực hiện, không tự chạy khi commit/push.
