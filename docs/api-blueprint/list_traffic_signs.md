# API Blueprint — Danh Sách Biển Báo Đã Xuất Bản

Tài liệu mô tả **hành vi code hiện tại**, theo source local đã chốt tới commit `225c062`, so với `main` tại `e727ff795ed149da4eb9e77c2d6a22be8f8e03a6`. Ví dụ dùng UUID/thời điểm minh họa; không phải dữ liệu API thật. Không thay đổi code hoặc database.

---

## Part 0 — Classification & Identity

- **API Name**: List Published Traffic Signs.
- **API Type**: Authenticated API; tạo nội dung dành cho admin, đọc dành cho người dùng đã xác thực.
- **Module**: `content-builder`.
- **Feature**: Reference Content.
- **Description**: Liệt kê biển báo PUBLISHED, chưa soft-delete, trong đúng bankVersion; hỗ trợ tìm kiếm và phân trang.
- **Related Tables**: `traffic_signs`.
- **Related Services**: Identity cấp JWT; Media cung cấp UUID ảnh khi dùng biển báo.
- **Source of Truth**: `TrafficSignController`, `TrafficSignService`, `TrafficSignMapper`, `TrafficSignEntity`, `TrafficSignRepository`.

---

## Part 1 — API Contract

### Endpoint

- **HTTP Method**: `GET`.
- **URL**: `/api/v1/content-builder/traffic-signs`.
- **Success Status**: `200 OK`.
- **Content Type**: `application/json`; body thành công trả trực tiếp DTO/mảng, không có wrapper `data`.

### Headers và quyền truy cập

| Name | Type | Required | Description |
|---|---|---|---|
| `Authorization` | String | Có | `Bearer <JWT>` hợp lệ; người dùng đã xác thực. |
| `X-Request-ID` | String | Không | Trace ID; filter tự sinh UUID nếu thiếu/trống và echo header này vào response. |

`JwtAuthenticationFilter` giữ nguyên chuỗi role trong JWT; quyền admin tương ứng `ROLE_ADMIN`. Không gửi `createdBy`, `status` hoặc thời điểm xuất bản để tự chọn danh tính/trạng thái.

Chi tiết xác thực và lỗi chung: [Contract Notes](content_builder_contract_notes.md).

### Request

- **Path Variables / Body**: không có.

| Query Parameter | Type | Required | Rule |
|---|---|---|---|
| `bankVersion` | String | Có | Không blank; so sánh đúng chuỗi với content_version. |
| `category` | String | Không | Nếu không blank: trim rồi so sánh chính xác, phân biệt chữ hoa/thường. Giá trị lạ cho danh sách rỗng; không có validate enum ở tham số này. |
| `keyword` | String | Không | Trim, lower-case; LIKE trên code hoặc name. |
| `page` | Integer | Không | Chỉ số trang từ 0; frontend gửi giá trị >= 0. |
| `size` | Integer | Không | Mặc định 24; frontend gửi số dương. |
| `sort` | String | Không | Mặc định `sourceId,asc`; dùng tên property Java, không dùng tên cột SQL. |

```http
GET /api/v1/content-builder/traffic-signs?bankVersion=dts-traffic-signs-v1&page=0&size=24&sort=sourceId,asc
Authorization: Bearer <USER_JWT>
```

`page`/`size` được Spring Data Pageable resolve; controller không khai báo một giới hạn nghiệp vụ `size<=100` hoặc sort whitelist riêng. Không hứa mọi page/size sai đều trả 400.

### Response

| Name | Type | Description |
|---|---|---|
| `content` | Array<Object> | TrafficSignResponse. |
| `pageNumber` | Integer | Trang sau khi Pageable resolve, từ 0. |
| `pageSize` | Integer | Kích thước trang sau khi resolve. |
| `totalElements` | Long | Tổng bản ghi khớp filter. |
| `totalPages` | Integer | Tổng số trang. |
| `last` | Boolean | Có phải trang cuối. |

**Mỗi phần tử content:**

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
  "content": [
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
      "mediaFileId": "30000000-0000-4000-8000-000000000001",
      "applicableLicenses": [
        "A1",
        "B"
      ],
      "status": "PUBLISHED",
      "publishedAt": "2026-10-17T09:00:00"
    }
  ],
  "pageNumber": 0,
  "pageSize": 24,
  "totalElements": 1,
  "totalPages": 1,
  "last": true
}
```
### Error Codes / HTTP Errors

Response hiện tại **không có trường `code`**; không gán mã `VAL-400`, `AUTH-403` hoặc `RES-404` giả định.

| HTTP | Nhánh xử lý | Ý nghĩa |
|---|---|---|
| 400 | `BusinessValidationException` | bankVersion rỗng/blank khi đã bind vào method. |
| 403 | `AccessDeniedException` | Không đủ role để gọi thao tác. |
| 500 | Exception không có handler riêng | Lỗi ngoài các nhánh business/validation được xử lý rõ ràng. |

Lỗi tại security filter/ingress có thể khác `ErrorResponse`. Lỗi bind UUID/query, JSON không đọc được, sort không tồn tại hoặc ràng buộc DB không được tự coi là `400/409`: handler tổng quát của source hiện tại có thể trả `500`. Xem [lỗi chung](content_builder_contract_notes.md#part-3--error-contract-and-binding-boundary).

---

## Part 2 — Processing Specification

1. **Controller Layer**: yêu cầu `isAuthenticated()`, bind filter/Pageable; gọi `TrafficSignService.listPublished`.
2. **Service Layer**: `@Transactional(readOnly=true)`; từ chối bankVersion blank. Tạo JPA Specification với `bankVersion`, `status=PUBLISHED`, `deletedAt IS NULL`.
3. **Filter / Repository Layer**: thêm category đã trim nếu có; keyword được ghép `%keyword%` cho code/name; repository `findAll(specification, pageable)` trả Page; map từng entity sang response rồi `PageResponse.of`.
4. **External Interaction**: không gọi Media hoặc các service khác; response giữ Media UUID nếu có.
5. **Empty Result**: không có bankVersion/dữ liệu khớp thì trả 200 với content rỗng, không trả 404. Trang vượt phạm vi cũng có thể rỗng theo kết quả Page.

---

## Part 3 — Data Interaction

| Operation | Table | Conditions | Expected Result |
|---|---|---|---|
| SELECT + count phục vụ Page | traffic_signs | content_version = bankVersion AND status = PUBLISHED AND deleted_at IS NULL; thêm filter tùy chọn, sort/page từ Pageable | Page các bản ghi khớp điều kiện; không ghi DB. |

---

## Part 4 — Operational Notes

- Frontend dùng `pageNumber`, không suy ra response có trường `number`, `page` hoặc wrapper `data`.
- Không thêm filter status từ client để lấy DRAFT/HIDDEN; service cố định PUBLISHED.
- `%` và `_` trong keyword vẫn có ý nghĩa LIKE vì code không escape chúng.
- Endpoint không nhận filter `licenseClass`; `applicableLicenses` chỉ là trường dữ liệu trả về. API không tự lấy hạng bằng từ Identity hoặc chọn biển theo hạng.
- FE dùng `mediaFileId` với luồng đọc Media hiện có; `sourceImage` không phải URL MinIO và service không tự resolve ảnh.
- Response không đặt Cache-Control riêng trong controller này; không mô tả Redis cache hoặc metric chưa được triển khai.
