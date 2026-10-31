# API Blueprint — Get Simulation Detail

Tài liệu thiết kế API Blueprint cho nghiệp vụ **Lấy chi tiết tình huống mô phỏng lái xe** (Get Simulation Detail) trong dịch vụ `content-builder`.
Tuân thủ tiêu chuẩn kiến trúc `/api/{version}/{service}/{object}/` và quy chuẩn Blueprint của hệ thống DTS.

---

## Part 0 — Classification & Identity

- **API Name**: Get Simulation Detail
- **API Type**: Public / Learner Practice
- **Module**: `content-builder`
- **Feature**: Driving Simulation Practice
- **Description**: Tra cứu và lấy toàn bộ thông tin chi tiết của một tình huống mô phỏng theo UUID hoặc số thứ tự kịch bản (từ `1` đến `120`). Trả về tiêu đề kịch bản, thời lượng video, media ID video và thumbnail, mẹo nhận biết, dấu hiệu nguy hiểm và mốc thời gian chấm điểm (`scoringTimeline`).
- **Related Tables**: `questions`, `question_options`
- **Related Services**: `dts-media` (phục vụ streaming video / thumbnail qua Media ID)

---

## Part 1 — API Contract

### Endpoint

- **HTTP Method**: `GET`
- **URL**: `/api/v1/content-builder/simulations/{idOrNumber}`
- **Content Type**: `application/json`

### Request

#### Path Variables
| Tên tham số | Kiểu dữ liệu | Bắt buộc | Mô tả | Quy tắc kiểm tra (Validation) |
|---|---|---|---|---|
| `idOrNumber` | String | Có | UUID của câu hỏi hoặc số thứ tự kịch bản (1–120) | Định dạng UUIDv4 chuẩn hoặc số nguyên từ `1` đến `120` |

#### Headers
| Tên Header | Kiểu dữ liệu | Bắt buộc | Mô tả |
|---|---|---|---|
| `Accept` | String | Không | `application/json` |
| `Authorization` | String | Không | Bearer JWT Token của học viên (nếu đã đăng nhập) |

#### Request Body
- Không có (None)

---

### Response

- **Success Status**: `200 OK`

#### Response Body
| Tên trường | Kiểu dữ liệu | Mô tả |
|---|---|---|
| `id` | String (UUID) | Định danh duy nhất của câu hỏi trong bảng `questions` |
| `scenarioNumber` | Integer | Số thứ tự tình huống mô phỏng (1 – 120) |
| `title` | String | Tên kịch bản tình huống giao thông |
| `chapterId` | Integer | Số thứ tự chương (1 – 6) |
| `chapterName` | String | Tên chương (ví dụ: *"Giao thông trên đường đô thị"*) |
| `durationSeconds` | Double | Tổng thời lượng video tình huống (giây) |
| `videoMediaId` | String (UUID) | Media ID của video MP4 trên `dts-media` |
| `thumbnailMediaId` | String (UUID) | Media ID của ảnh đại diện WebP trên `dts-media` |
| `mediaFileIds` | Array<String> | Danh sách các Media ID liên quan `[videoMediaId, thumbnailMediaId]` |
| `tip` | String | Mẹo canh thời điểm bấm phím Space |
| `hazardSign` | String | Dấu hiệu nhận biết tình huống nguy hiểm bắt đầu |
| `scoringTimeline` | Object | Mốc thời gian thang điểm chuẩn 5 mốc (`t5_start`, `t4_start`, `t3_start`, `t2_start`, `t1_start`, `t0_end`) |
| `status` | String | Trạng thái của câu hỏi (`PUBLISHED`) |

#### Ví dụ Response Thành công (`200 OK`):
```json
{
  "id": "5a629cca-524c-454c-9a8b-c333aeb09607",
  "scenarioNumber": 1,
  "title": "Người đi bộ sang đường bị khuất sau xe tải",
  "chapterId": 1,
  "chapterName": "Giao thông trên đường đô thị",
  "durationSeconds": 27.0,
  "videoMediaId": "3c18f45d-3981-4a20-91d5-5fb339375f48",
  "thumbnailMediaId": "0cce6c10-def0-4873-a179-046080d84aee",
  "mediaFileIds": [
    "3c18f45d-3981-4a20-91d5-5fb339375f48",
    "0cce6c10-def0-4873-a179-046080d84aee"
  ],
  "tip": "Nhấn space khi quan sát rõ thấy người đi bộ thứ nhất xuất hiện sau xe tải để sang đường",
  "hazardSign": "Người đi bộ sau xe tải sang đường",
  "scoringTimeline": {
    "t5_start": 17.18,
    "t4_start": 17.71,
    "t3_start": 18.25,
    "t2_start": 18.78,
    "t1_start": 19.32,
    "t0_end": 19.85
  },
  "status": "PUBLISHED"
}
```

---

### Error Codes

| Mã lỗi | HTTP Status | Ý nghĩa nghiệp vụ | Thông điệp trả về |
|---|---|---|---|
| `VALIDATION_FAILED` | 400 Bad Request | Tham số đầu vào trống hoặc sai | `Định danh tình huống không được để trống` |
| `RES-404` | 404 Not Found | Không tìm thấy tình huống mô phỏng với ID / số thứ tự tương ứng | `Không tìm thấy câu hỏi mô phỏng với ID / số thứ tự: {idOrNumber}` |
| `SYS-500` | 500 Internal Server Error | Lỗi hệ thống máy chủ | `An unexpected internal server error occurred.` |

---

## Part 2 — Processing Specification

1. **Controller Layer (`SimulationController`)**:
   - Tiếp nhận `GET /api/v1/content-builder/simulations/{idOrNumber}`.
   - Chuyển tiếp tới `SimulationService.getSimulationDetail(idOrNumber)`.
2. **Service Layer (`SimulationService`)**:
   - Kiểm tra định dạng `idOrNumber`:
     - Nếu là chuỗi UUID hợp lệ: Gọi `questionRepository.findByIdAndDeletedAtIsNull(uuid)`.
     - Nếu là chuỗi số: Gọi `questionRepository.findSimulationByScenarioNumber(scenarioNumber)`.
   - Kiểm tra loại câu hỏi: Phải có `type == QuestionType.SIMULATION`. Nếu không thỏa mãn, ném `ResourceNotFoundException`.
   - Trích xuất `metadata` và `explanations` dạng JSONB từ `QuestionEntity`.
   - Ánh xạ sang `SimulationDetailResponse` và trả về client.

