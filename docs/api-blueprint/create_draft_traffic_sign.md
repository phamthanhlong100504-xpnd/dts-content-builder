# API Blueprint — Tạo Biển Báo Nháp

Tài liệu mô tả **hành vi code hiện tại**, theo source local đã chốt tới commit `225c062`, so với `main` tại `e727ff795ed149da4eb9e77c2d6a22be8f8e03a6`. Ví dụ dùng UUID/thời điểm minh họa; không phải dữ liệu API thật. Không thay đổi code hoặc database.

---

## Part 0 — Classification & Identity

- **API Name**: Create Draft TrafficSign.
- **API Type**: Authenticated API; tạo nội dung dành cho admin, đọc dành cho người dùng đã xác thực.
- **Module**: `content-builder`.
- **Feature**: Reference Content.
- **Description**: Tạo một bản ghi biển báo mới ở trạng thái `DRAFT`. Endpoint tự chọn trạng thái; cùng bộ validation đầu vào cho draft và published.
- **Related Tables**: `traffic_signs`.
- **Related Services**: Identity cấp JWT; Media cung cấp UUID ảnh khi dùng biển báo.
- **Source of Truth**: `TrafficSignController`, `TrafficSignService`, `TrafficSignMapper`, `TrafficSignEntity`, `TrafficSignRepository`.

---

## Part 1 — API Contract

### Endpoint

- **HTTP Method**: `POST`.
- **URL**: `/api/v1/content-builder/traffic-signs/draft`.
- **Success Status**: `201 Created`.
- **Content Type**: `application/json`; body thành công trả trực tiếp DTO/mảng, không có wrapper `data`.

### Headers và quyền truy cập

| Name | Type | Required | Description |
|---|---|---|---|
| `Authorization` | String | Có | `Bearer <JWT>` hợp lệ; phải có `ROLE_ADMIN`. |
| `X-Request-ID` | String | Không | Trace ID; filter tự sinh UUID nếu thiếu/trống và echo header này vào response. |

`JwtAuthenticationFilter` giữ nguyên chuỗi role trong JWT; quyền admin tương ứng `ROLE_ADMIN`. Không gửi `createdBy`, `status` hoặc thời điểm xuất bản để tự chọn danh tính/trạng thái.

Chi tiết xác thực và lỗi chung: [Contract Notes](content_builder_contract_notes.md).

### Request

- **Path Variables**: không có.
- **Query Parameters**: không có.
- **Request Body**: JSON object `CreateTrafficSignRequest`.

| Name | Type | Required | Validation / Business Rules |
|---|---|---|---|
| `sourceId` | Integer | Có | `@NotNull`, >= 1; API không giới hạn tối đa 221. |
| `bankVersion` | String | Có | Không blank, tối đa 64 ký tự; giữ nguyên chuỗi đầu vào. |
| `code` | String | Có | Không blank, tối đa 32 ký tự. |
| `name` | String | Có | Không blank, tối đa 255 ký tự. |
| `category` | String | Có | Một trong `cam`, `nguy_hiem`, `hieu_lenh`, `chi_dan`, `phu`; đúng chữ thường. |
| `categoryName` | String | Có | Không blank, tối đa 100 ký tự. |
| `meaning` | String | Có | Không blank; form không khai báo max length. |
| `quickTip` | String | Có | Không blank; form không khai báo max length. |
| `sourceImage` | String | Có | Không blank, tối đa 255 ký tự; form không kiểm regex tên file hoặc đọc file. |
| `mediaFileId` | UUID | Tùy trạng thái | Có thể null khi DRAFT; service yêu cầu khác null khi PUBLISHED. |
| `applicableLicenses` | Array<String> | Có | Mảng không rỗng, phần tử không blank; API không kiểm whitelist hạng hoặc khử trùng lặp. |

```json
{
  "sourceId": 1,
  "bankVersion": "dts-traffic-signs-v1",
  "code": "P.101",
  "name": "Biển báo minh họa",
  "category": "cam",
  "categoryName": "Biển cấm",
  "meaning": "Nội dung ý nghĩa lấy từ bộ dữ liệu nguồn.",
  "quickTip": "Mẹo ghi nhớ minh họa.",
  "sourceImage": "p101.webp",
  "mediaFileId": null,
  "applicableLicenses": [
    "A1",
    "B"
  ]
}
```
`sourceId` là số nguồn, không phải UUID bản ghi. `mediaFileId` phải là UUID của môi trường đang nạp; API này không upload ảnh hoặc xác minh Media READY.

### Response

| Name | Type | Description |
|---|---|---|
| `id` | UUID | UUID bản ghi do hệ thống sinh; khác sourceId và Media UUID. |
| `sourceId` | Integer | ID dữ liệu nguồn, theo bankVersion. |
| `bankVersion` | String | Map tới cột `content_version`. |
| `code` | String | Mã biển báo. |
| `name` | String | Tên biển báo. |
| `category` | String | Mã nhóm biển báo. |
| `categoryName` | String | Tên nhóm hiển thị. |
| `meaning` | String | Nội dung ý nghĩa. |
| `quickTip` | String | Mẹo ghi nhớ. |
| `sourceImage` | String | Tên/tham chiếu ảnh nguồn; không phải URL ảnh hiển thị. |
| `mediaFileId` | UUID hoặc null | Tham chiếu Media; bắt buộc khi PUBLISHED. |
| `applicableLicenses` | Array<String> | Thông tin hạng bằng trong dữ liệu; endpoint list không tự lọc theo hạng. |
| `status` | String | Trạng thái bản ghi. |
| `publishedAt` | LocalDateTime hoặc null | ISO-8601 không kèm offset; không tự diễn giải là UTC. |

```json
{
  "id": "10000000-0000-4000-8000-000000000001",
  "sourceId": 1,
  "bankVersion": "dts-traffic-signs-v1",
  "code": "P.101",
  "name": "Biển báo minh họa",
  "category": "cam",
  "categoryName": "Biển cấm",
  "meaning": "Nội dung ý nghĩa lấy từ bộ dữ liệu nguồn.",
  "quickTip": "Mẹo ghi nhớ minh họa.",
  "sourceImage": "p101.webp",
  "mediaFileId": null,
  "applicableLicenses": [
    "A1",
    "B"
  ],
  "status": "DRAFT",
  "publishedAt": null
}
```
### Error Codes / HTTP Errors

Response hiện tại **không có trường `code`**; không gán mã `VAL-400`, `AUTH-403` hoặc `RES-404` giả định.

| HTTP | Nhánh xử lý | Ý nghĩa |
|---|---|---|
| 400 | `MethodArgumentNotValidException` | Thiếu/sai ràng buộc Bean Validation trong request. |
| 400 | `BusinessValidationException` | Trùng sourceId/bankVersion, trùng code hoặc thiếu mediaFileId khi published. |
| 403 | `AccessDeniedException` | Không đủ role để gọi thao tác. |
| 500 | Exception không có handler riêng | Lỗi ngoài các nhánh business/validation được xử lý rõ ràng. |

Lỗi tại security filter/ingress có thể khác `ErrorResponse`. Lỗi bind UUID/query, JSON không đọc được, sort không tồn tại hoặc ràng buộc DB không được tự coi là `400/409`: handler tổng quát của source hiện tại có thể trả `500`. Xem [lỗi chung](content_builder_contract_notes.md#part-3--error-contract-and-binding-boundary).

---

## Part 2 — Processing Specification

1. **Controller Layer**: yêu cầu `ROLE_ADMIN`; bind JSON, chạy `@Valid`, lấy UUID người gọi từ `@AuthenticationPrincipal`. Gọi `TrafficSignService.createDraft(request, userId)`.
2. **Service Layer**: kiểm tra dữ liệu bắt buộc và tra trùng `(sourceId, bankVersion)` trên mọi trạng thái chưa soft-delete. Tra thêm code bằng `findByCodeIgnoreCaseAndBankVersionAndDeletedAtIsNull`; khi PUBLISHED, yêu cầu mediaFileId khác null. Map request sang entity, gán `DRAFT`, `createdBy=userId`; giữ publishedAt null.
3. **Entity / Repository Layer**: `@PrePersist` điền createdAt/updatedAt, Hibernate sinh UUID; `save` trong giao dịch service `@Transactional`. Mapper trả `TrafficSignResponse`.
4. **External Interaction**: không gọi Identity/Media qua HTTP trong service này; Identity chỉ là nơi phát JWT trước request.
5. **Validation**: khi validation/business exception xảy ra, transaction không tạo bản ghi thành công. Vi phạm unique index do race hoặc khác biệt chuẩn hóa ở DB đi qua handler generic nếu không có business exception tương ứng.

---

## Part 3 — Data Interaction

| Operation | Table | Condition / Values | Expected Result |
|---|---|---|---|
| SELECT | traffic_signs | source_id = request.sourceId, content_version = request.bankVersion, deleted_at IS NULL | Từ chối sourceId đã dùng, kể cả bản DRAFT. |
| SELECT | traffic_signs | code ignore-case, content_version, deleted_at IS NULL | Kiểm tra code đã tồn tại ở tầng service. |
| INSERT | traffic_signs | Entity trạng thái DRAFT, created_by từ JWT | Một bản ghi mới; không UPDATE bản DRAFT đã có. |

**Ràng buộc V5**: unique `(content_version, source_id)` và `(content_version, lower(btrim(code)))` với `deleted_at IS NULL`. Service không trim code trước khi lưu; DB vẫn áp dụng biểu thức unique đã khai báo.

---

## Part 4 — Operational Notes

- Endpoint này luôn tạo mới. Gọi `/published` với sourceId của bản `/draft` còn tồn tại sẽ bị báo trùng; code hiện tại không có thao tác chuyển draft sang published cho nhóm này.
- Không có idempotency key tại API. Importer cần đối chiếu API/checkpoint trước khi POST lại; không coi retry sau timeout chắc chắn chưa ghi.
- Draft có thể chưa gắn Media, nhưng vẫn phải đủ các trường nội dung còn lại.
- `publishedAt` là LocalDateTime không chứa múi giờ; response không trả các trường createdAt/updatedAt/createdBy dù chúng có trong bảng.
- Dựng schema/import qua [hướng dẫn local](../LOCAL_SETUP.md) và [runbook reference content](../../DEPLOY_REFERENCE_CONTENT.md); tài liệu API không tự thực thi migration.
