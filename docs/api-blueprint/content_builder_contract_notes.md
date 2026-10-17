# API Blueprint — Contract Notes cho phần bổ sung Content Builder

Tài liệu dùng chung cho các blueprint nguồn thi, biển báo và mẹo ghi nhớ. Mô tả code local đã commit tới `225c062`, so với main `e727ff795ed149da4eb9e77c2d6a22be8f8e03a6`. Không giả định tính năng hoặc cơ chế bảo mật chỉ được nhắc như định hướng trong tài liệu cũ đã được triển khai.

---

## Part 0 — Classification & Identity

- **Module**: `content-builder`.
- **Scope**: auth/header, schema response, nguồn dữ liệu, lỗi và ranh giới xử lý của 11 API trong [chỉ mục](README.md).
- **Source of Truth**: `SecurityConfig`, `JwtAuthenticationFilter`, `ExamSourceAccess`, `MdcTraceIdFilter`, `GlobalExceptionHandler`, các Controller/Form/Response/Service/Mapper/Repository liên quan và migration V5.
- **Mục đích**: các thành viên FE/backend hiểu cùng contract đang tồn tại; tài liệu không yêu cầu sửa logic đã chạy.

## Part 1 — Request, Response và Authentication

### URL và header

- Prefix của controller: `/api/v1/content-builder`.
- Content Builder local mặc định nghe cổng `8082`. Khi dùng Gateway, cấu hình route ở repo Gateway; không mặc định API nội bộ được phép gọi từ trình duyệt qua Gateway.
- `Content-Type: application/json` cho POST có body.
- `X-Request-ID` tùy chọn. `MdcTraceIdFilter` nhận chuỗi không blank hoặc tự sinh UUID, ghi MDC traceId và echo response header. Code không kiểm header này phải là UUID hoặc dài tối đa 64 ký tự.
- Không gửi `userId` hoặc `createdBy` trong JSON để chọn người tạo; controller lấy UUID từ AuthenticationPrincipal.

### Hai nhóm quyền khác nhau

| Nhóm API | Điều kiện tại code |
|---|---|
| Tạo biển báo/mẹo draft hoặc published | JWT người dùng hợp lệ, có authority `ROLE_ADMIN` qua `hasRole('ADMIN')` |
| Danh sách/detail biển báo/mẹo | JWT người dùng hợp lệ; `isAuthenticated()`; chỉ trả PUBLISHED/chưa xóa |
| Internal bank/metadata/batch | `ExamSourceAccess.requireAccess`: admin có đồng thời `ROLE_ADMIN` và `PERM_questions:read`, **hoặc** service token đúng chuẩn ở header `X-DTS-Exam-Source-Token` |

`JwtAuthenticationFilter` giữ nguyên chuỗi trong claim `roles`; các phần tử claim `permissions` được thêm tiền tố `PERM_`. Chỉ có role admin chưa đủ để đọc nguồn đáp án nội bộ theo nhánh admin. Một JWT học viên thông thường không tự cấp quyền đọc nguồn này.

`SecurityConfig` cho URL internal đi qua bằng `permitAll` để service token được controller xác thực riêng. Điều này không có nghĩa bank/metadata/batch công khai: `ExamSourceAccess` vẫn từ chối request không có một trong hai điều kiện trên. Header `X-Service-Name` không được dùng làm credential trong code hiện tại.

### Service JWT của Examination

Consumer backend dùng client phát token hiện có; FE không tự phát token này. Cấu hình `jwt.secret` của môi trường phải khớp với cấu hình phía Examination để hai bên dẫn xuất cùng service key.

Các kiểm tra nhìn thấy trong `ExamSourceAccess`:

- Chữ ký HS256 bằng service key dẫn xuất HMAC-SHA256, context `dts:content-builder:exam-source:v1`; không dùng nguyên user JWT làm service token.
- `iss=dts-examination`, `sub=exam-source-reader`, `aud` có đúng một giá trị `dts-content-builder`, `purpose=EXAM_SOURCE_READ`.
- Header `typ` phải khớp giá trị cố định của client/server. Không chép token thật hoặc secret vào tài liệu để minh họa.
- Có iat, exp, jti không blank; thời hạn dương và không quá 60 giây; cho phép clock skew 5 giây theo parser và kiểm tra thời gian.
- Token không blank, chiều dài không quá 4096 ký tự.
- Root key Base64 sau decode phải có ít nhất 32 byte.

### Body thành công và kiểu dữ liệu

| Loại endpoint | Body thành công |
|---|---|
| Internal metadata/batch | Array DTO trực tiếp |
| Internal bank | Object có bankVersion, licenseClass, chapters, questions |
| Reference create/detail | Object TrafficSignResponse hoặc MemoryTipResponse trực tiếp |
| Reference list | PageResponse: content, pageNumber, pageSize, totalElements, totalPages, last |

Không thêm wrapper `data`, `success`, `code` hoặc `message` vào mô tả response thành công khi DTO không có. `publishedAt` là `LocalDateTime`, thường serialize ISO-8601 không có offset; không tự gọi đó là thời gian UTC.

`page` là query index từ 0, khác tên response `pageNumber`. `Pageable` dùng tên property Java như `sourceId` hoặc `sortOrder` cho sort, không dùng `source_id` hoặc `sort_order`. Controller reference mới không khai báo sort whitelist/max-size nghiệp vụ riêng. Frontend gửi page không âm và size dương; không suy ra resolver sẽ trả 400 cho mọi giá trị sai.

---

## Part 2 — Business Processing Boundaries

### Ba tầng ID và hai loại version

| Trường | Ý nghĩa |
|---|---|
| `id` | UUID bản ghi trong Content Builder |
| `sourceId` / `sourceChapterId` | ID/số thứ tự từ bộ nguồn; không dùng thay UUID path |
| `mediaFileId` / `mediaFileIds` | UUID của Media trong môi trường hiện tại; không phải UUID bản ghi reference content |
| `bankVersion` | Phiên bản bộ dữ liệu; reference entity map vào cột content_version, chapter/question giữ trong metadata |
| `contentVersion` của câu hỏi nội bộ | Revision SHA256 của snapshot nguồn theo license; không phải bankVersion hoặc cột content_version của biển báo/mẹo |
| `sourceImage` | Trường API map vào image_filename của traffic_signs; không phải URL ảnh được ký |

### Hạng bằng và source nội bộ

Whitelist ở ExamQuestionReadService: `A1`, `A`, `B1`, `B`, `C1`, `C`, `D1`, `D2`, `D`, `BE`, `C1E`, `CE`, `D1E`, `D2E`, `DE`. Đây là whitelist của code đang bàn giao, không phải tài liệu diễn giải pháp luật về hạng bằng.

Bank bắt buộc licenseClass; metadata/batch cho phép bỏ hoặc blank. Nếu bỏ, service không lọc license và critical lấy boolean metadata.isCritical. Nếu có license, dùng metadata.applicableLicenses/criticalLicenses và kiểm critical là tập con applicable. Consumer thi nên dùng cùng hạng trong bank/metadata/batch để so revision.

Đường dẫn học liệu phải published mới có thể đưa câu vào metadata. Batch đọc trực tiếp question UUID và không tự xác nhận nó còn thuộc chapter/program đã chọn; consumer dùng metadata/revision để kiểm soát nguồn mình đã chọn.

### Reference content

- Draft và published dùng cùng form bắt buộc nội dung. Draft không phải payload tùy ý thiếu title/meaning/quickRule.
- POST `/draft` và POST `/published` tạo **hai loại bản ghi mới**, không phải quy trình POST draft rồi POST published để cập nhật cùng bản ghi. SourceId đã tồn tại ở DRAFT vẫn chiếm unique key của bankVersion.
- Biển PUBLISHED cần mediaFileId khác null. Service không gọi Media để kiểm READY hoặc nội dung ảnh; importer đảm nhận luồng upload/đối chiếu trước khi publish.
- API list/detail chỉ đọc PUBLISHED/chưa soft-delete; admin cũng không dùng endpoint đọc này để xem draft.
- applicableLicenses là trường trả về của biển báo; list controller không nhận licenseClass và không tự chọn hạng từ hồ sơ Identity.
- Importer có ràng buộc bộ nguồn/checkpoint chặt hơn một số form API: 221 biển, 12 mẹo, manifest ảnh, môi trường/UUID. Không đưa các giới hạn riêng của importer thành validation API nếu form/service không làm.

---

## Part 3 — Error Contract and Binding Boundary

### Body do GlobalExceptionHandler tạo

```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "Published traffic sign requires mediaFileId.",
  "traceId": "50000000-0000-4000-8000-000000000001"
}
```

Response này không có `code`. Ví dụ message/traceId minh họa nhánh business, không yêu cầu client so sánh nguyên văn mọi message.

| Exception được handler nêu rõ | HTTP | error |
|---|---:|---|
| BusinessValidationException | 400 | Bad Request |
| IllegalArgumentException | 400 | Bad Request |
| MethodArgumentNotValidException | 400 | Validation Failed |
| ResourceNotFoundException | 404 | Not Found |
| AccessDeniedException tại MVC/method | 403 | Forbidden |
| Exception còn lại | 500 | Internal Server Error |

Bean Validation message được ghép từ field + default message. Handler generic hiện ghép tên loại exception và message vào response; tài liệu này ghi nhận hành vi, không sửa exception handler.

### Không hứa sai về 400/401/409

1. Missing query parameter, type mismatch khi bind UUID/query và JSON không đọc được chưa có handler riêng trong source. Chúng có thể đi vào handler generic 500; không ghi mặc định là VAL-400 chỉ vì input sai.
2. DataIntegrityViolationException/vi phạm unique ở DB chưa được map thành 409. Ví dụ trùng sortOrder của mẹo chỉ bị chặn bởi V5, hoặc race giữa check trùng và insert, có thể trả 500. Trùng sourceId/code được service phát hiện trước thì là business 400.
3. SecurityConfig không cấu hình custom AuthenticationEntryPoint cho từng trường hợp. JwtAuthenticationFilter bắt lỗi token rồi tiếp tục với trạng thái chưa xác thực. Response từ security filter/ingress không được bảo đảm có cùng ErrorResponse hoặc mã 401 cố định. Client xử lý lỗi xác thực/quyền 401/403 phù hợp với đường gọi; không phụ thuộc vào một trường code giả định.
4. Với internal request đã bind hợp lệ nhưng không vượt ExamSourceAccess, controller ném AccessDeniedException và handler mô tả nhánh 403.

Đây là ghi nhận contract đang có; không bổ sung validation, endpoint, status mapping hoặc thay đổi nghiệp vụ trong đợt viết tài liệu.

---

## Part 4 — Data, Transaction và Operational Notes

- Internal source: `@Transactional(readOnly=true, isolation=REPEATABLE_READ)`; ba response thành công đặt Cache-Control no-store. Không có cập nhật question/progress/result khi đọc.
- Reference create: giao dịch service ghi đúng một entity; UUID/audit timestamp từ Entity/Mapper, không lấy từ request body.
- Reference list/detail: read-only, không gọi service ngoài ở tầng service đọc.
- V5 tạo bảng memory_tips/traffic_signs và unique index có điều kiện deleted_at IS NULL. File migration không tự chạy vì được nhắc trong blueprint. Đọc [LOCAL_SETUP](../LOCAL_SETUP.md) về schema; máy đã có dữ liệu không chạy bootstrap database trống.
- Dữ liệu Git/ảnh/checkpoint/importer: [CONTENT_BUILDER_SERVICE_GUIDE](../../CONTENT_BUILDER_SERVICE_GUIDE.md), [DEPLOY_REFERENCE_CONTENT](../../DEPLOY_REFERENCE_CONTENT.md), [REFERENCE_CONTENT_RELEASE_README](../../REFERENCE_CONTENT_RELEASE_README.md).
- Không có Redis cache, audit-event bus, metric mới hoặc Media HTTP validation được tự thêm bởi tài liệu này. Không dùng các đề xuất trong blueprint lịch sử để khẳng định chúng đã chạy.
