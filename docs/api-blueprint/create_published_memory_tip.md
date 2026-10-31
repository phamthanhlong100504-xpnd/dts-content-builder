# API Blueprint — Tạo Mẹo Ghi Nhớ Xuất Bản

Tài liệu mô tả **hành vi code hiện tại**, theo source local đã chốt tới commit `225c062`, so với `main` tại `e727ff795ed149da4eb9e77c2d6a22be8f8e03a6`. Ví dụ dùng UUID/thời điểm minh họa; không phải dữ liệu API thật. Không thay đổi code hoặc database.

---

## Part 0 — Classification & Identity

- **API Name**: Create Published MemoryTip.
- **API Type**: Authenticated API; tạo nội dung dành cho admin, đọc dành cho người dùng đã xác thực.
- **Module**: `content-builder`.
- **Feature**: Reference Content.
- **Description**: Tạo một bản ghi mẹo ghi nhớ mới ở trạng thái `PUBLISHED`. Endpoint tự chọn trạng thái; cùng bộ validation đầu vào cho draft và published.
- **Related Tables**: `memory_tips`.
- **Related Services**: Identity cấp JWT; Media cung cấp UUID ảnh khi dùng biển báo.
- **Source of Truth**: `MemoryTipController`, `MemoryTipService`, `MemoryTipMapper`, `MemoryTipEntity`, `MemoryTipRepository`.

---

## Part 1 — API Contract

### Endpoint

- **HTTP Method**: `POST`.
- **URL**: `/api/v1/content-builder/memory-tips/published`.
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
- **Request Body**: JSON object `CreateMemoryTipRequest`.

| Name | Type | Required | Validation / Business Rules |
|---|---|---|---|
| `sourceId` | Integer | Có | `@NotNull`, >= 1; API không giới hạn tối đa 12. |
| `bankVersion` | String | Có | Không blank, tối đa 64 ký tự. |
| `sortOrder` | Integer | Có | `@NotNull`, >= 1; unique trong bankVersion theo V5. |
| `title` | String | Có | Không blank, tối đa 255 ký tự. |
| `quickRule` | String | Có | Không blank; form không khai báo max length. |
| `explanation` | String | Có | Không blank; form không khai báo max length. |

```json
{
  "sourceId": 1,
  "bankVersion": "dts-2026-600-v1",
  "sortOrder": 1,
  "title": "Mẹo minh họa",
  "quickRule": "Quy tắc ngắn lấy từ bộ dữ liệu nguồn.",
  "explanation": "Giải thích chi tiết lấy từ bộ dữ liệu nguồn."
}
```
`sourceId` là số nguồn, không phải UUID bản ghi. Mọi trường nội dung đều cần đủ ngay cả ở bản DRAFT.

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
| 400 | `MethodArgumentNotValidException` | Thiếu/sai ràng buộc Bean Validation trong request. |
| 400 | `BusinessValidationException` | Trùng sourceId/bankVersion, thiếu dữ liệu bắt buộc hoặc sortOrder không dương. |
| 403 | `AccessDeniedException` | Không đủ role để gọi thao tác. |
| 500 | Exception không có handler riêng | Lỗi ngoài các nhánh business/validation được xử lý rõ ràng. |

Lỗi tại security filter/ingress có thể khác `ErrorResponse`. Lỗi bind UUID/query, JSON không đọc được, sort không tồn tại hoặc ràng buộc DB không được tự coi là `400/409`: handler tổng quát của source hiện tại có thể trả `500`. Xem [lỗi chung](content_builder_contract_notes.md#part-3--error-contract-and-binding-boundary).

---

## Part 2 — Processing Specification

1. **Controller Layer**: yêu cầu `ROLE_ADMIN`; bind JSON, chạy `@Valid`, lấy UUID người gọi từ `@AuthenticationPrincipal`. Gọi `MemoryTipService.createPublished(request, userId)`.
2. **Service Layer**: kiểm tra dữ liệu bắt buộc và tra trùng `(sourceId, bankVersion)` trên mọi trạng thái chưa soft-delete. Map request sang entity, gán `PUBLISHED`, `createdBy=userId`; gán publishedAt bằng `LocalDateTime.now()`.
3. **Entity / Repository Layer**: `@PrePersist` điền createdAt/updatedAt, Hibernate sinh UUID; `save` trong giao dịch service `@Transactional`. Mapper trả `MemoryTipResponse`.
4. **External Interaction**: không gọi Identity/Media qua HTTP trong service này; Identity chỉ là nơi phát JWT trước request.
5. **Validation**: khi validation/business exception xảy ra, transaction không tạo bản ghi thành công. Vi phạm unique index do race hoặc khác biệt chuẩn hóa ở DB đi qua handler generic nếu không có business exception tương ứng.

---

## Part 3 — Data Interaction

| Operation | Table | Condition / Values | Expected Result |
|---|---|---|---|
| SELECT | memory_tips | source_id = request.sourceId, content_version = request.bankVersion, deleted_at IS NULL | Từ chối sourceId đã dùng, kể cả bản DRAFT. |
| INSERT | memory_tips | Entity trạng thái PUBLISHED, created_by từ JWT | Một bản ghi mới; không UPDATE bản DRAFT đã có. |

**Ràng buộc V5**: unique `(content_version, source_id)` và `(content_version, sort_order)` với `deleted_at IS NULL`. Service chỉ kiểm trước sourceId; trùng sortOrder có thể phát sinh lỗi DB và HTTP 500, không phải contract 409.

---

## Part 4 — Operational Notes

- Endpoint này luôn tạo mới. Gọi `/published` với sourceId của bản `/draft` còn tồn tại sẽ bị báo trùng; code hiện tại không có thao tác chuyển draft sang published cho nhóm này.
- Không có idempotency key tại API. Importer cần đối chiếu API/checkpoint trước khi POST lại; không coi retry sau timeout chắc chắn chưa ghi.
- `publishedAt` là LocalDateTime không chứa múi giờ; response không trả các trường createdAt/updatedAt/createdBy dù chúng có trong bảng.
- Dựng schema/import qua [hướng dẫn local](../LOCAL_SETUP.md) và [runbook reference content](../../DEPLOY_REFERENCE_CONTENT.md); tài liệu API không tự thực thi migration.
