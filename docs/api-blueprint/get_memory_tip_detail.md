# API Blueprint — Chi Tiết Mẹo Ghi Nhớ Đã Xuất Bản

Tài liệu mô tả **hành vi code hiện tại**, theo source local đã chốt tới commit `225c062`, so với `main` tại `e727ff795ed149da4eb9e77c2d6a22be8f8e03a6`. Ví dụ dùng UUID/thời điểm minh họa; không phải dữ liệu API thật. Không thay đổi code hoặc database.

---

## Part 0 — Classification & Identity

- **API Name**: Get Published MemoryTip Detail.
- **API Type**: Authenticated API; tạo nội dung dành cho admin, đọc dành cho người dùng đã xác thực.
- **Module**: `content-builder`.
- **Feature**: Reference Content.
- **Description**: Đọc một mẹo ghi nhớ theo UUID của bản ghi Content Builder; chỉ đọc PUBLISHED chưa soft-delete.
- **Related Tables**: `memory_tips`.
- **Related Services**: Identity cấp JWT; Media cung cấp UUID ảnh khi dùng biển báo.
- **Source of Truth**: `MemoryTipController`, `MemoryTipService`, `MemoryTipMapper`, `MemoryTipEntity`, `MemoryTipRepository`.

---

## Part 1 — API Contract

### Endpoint

- **HTTP Method**: `GET`.
- **URL**: `/api/v1/content-builder/memory-tips/{id}`.
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

| Path Variable | Type | Required | Rule |
|---|---|---|---|
| `id` | UUID | Có | UUID Content Builder; không phải sourceId. |

- **Query Parameters / Body**: không có; endpoint không yêu cầu bankVersion.

```http
GET /api/v1/content-builder/memory-tips/10000000-0000-4000-8000-000000000001
Authorization: Bearer <USER_JWT>
```

### Response

| Name | Type | Description |
|---|---|---|
| `id` | UUID | UUID bản ghi do hệ thống sinh. |
| `sourceId` | Integer | ID nguồn trong một bankVersion. |
| `bankVersion` | String | Map tới `content_version`. |
| `sortOrder` | Integer | Thứ tự hiển thị. |
| `title` | String | Tiêu đề mẹo. |
| `quickRule` | String | Quy tắc ngắn. |
| `explanation` | String | Giải thích chi tiết. |
| `status` | String | Trạng thái bản ghi. |
| `publishedAt` | LocalDateTime hoặc null | ISO-8601 không có offset. |

```json
{
  "id": "10000000-0000-4000-8000-000000000001",
  "sourceId": 1,
  "bankVersion": "dts-2026-600-v1",
  "sortOrder": 1,
  "title": "Mẹo minh họa",
  "quickRule": "Quy tắc ngắn lấy từ bộ dữ liệu nguồn.",
  "explanation": "Giải thích chi tiết lấy từ bộ dữ liệu nguồn.",
  "status": "PUBLISHED",
  "publishedAt": "2026-10-17T09:00:00"
}
```
### Error Codes / HTTP Errors

Response hiện tại **không có trường `code`**; không gán mã `VAL-400`, `AUTH-403` hoặc `RES-404` giả định.

| HTTP | Nhánh xử lý | Ý nghĩa |
|---|---|---|
| 404 | `ResourceNotFoundException` | ID không tồn tại, đã soft-delete hoặc có status khác PUBLISHED. |
| 403 | `AccessDeniedException` | Không đủ role để gọi thao tác. |
| 500 | Exception không có handler riêng | Lỗi ngoài các nhánh business/validation được xử lý rõ ràng. |

Lỗi tại security filter/ingress có thể khác `ErrorResponse`. Lỗi bind UUID/query, JSON không đọc được, sort không tồn tại hoặc ràng buộc DB không được tự coi là `400/409`: handler tổng quát của source hiện tại có thể trả `500`. Xem [lỗi chung](content_builder_contract_notes.md#part-3--error-contract-and-binding-boundary).

---

## Part 2 — Processing Specification

1. **Controller Layer**: yêu cầu `isAuthenticated()`, bind UUID; gọi `MemoryTipService.getPublishedById(id)`.
2. **Service Layer**: truy vấn `findByIdAndDeletedAtIsNull`; không thấy thì ném ResourceNotFoundException. Nếu entity không PUBLISHED cũng ném cùng loại ngoại lệ.
3. **Mapper Layer**: trả `MemoryTipResponse` trực tiếp, không có wrapper và không trả audit fields.
4. **External Interaction**: không phát sinh gọi Media/Identity ở service đọc detail.
5. **Validation Boundary**: UUID sai định dạng không đi vào method; source chưa có handler bind UUID riêng, xem Contract Notes thay vì mặc định ghi 400.

---

## Part 3 — Data Interaction

| Operation | Table | Conditions | Expected Result |
|---|---|---|---|
| SELECT | memory_tips | id = :id AND deleted_at IS NULL; kiểm status PUBLISHED tại service | Một DTO hoặc 404; không ghi DB. |

---

## Part 4 — Operational Notes

- Bản DRAFT/ARCHIVED/HIDDEN không được trả qua endpoint này, kể cả khi người gọi là admin.
- Đây là nội dung dùng chung cho các user đã đăng nhập, không áp dụng kiểm tra chủ sở hữu createdBy.
- Không có endpoint update/delete hoặc publish-existing-draft trong controller mới được mô tả ở đây.
