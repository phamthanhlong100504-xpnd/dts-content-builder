# API Blueprint — Evaluate Simulation Response

Tài liệu thiết kế API Blueprint cho nghiệp vụ **Chấm điểm và Đánh giá phản xạ tình huống mô phỏng** (Evaluate Simulation Response) trong dịch vụ `content-builder`.
Tuân thủ tiêu chuẩn kiến trúc `/api/{version}/{service}/{object}/` và quy chuẩn Blueprint của hệ thống DTS.

---

## Part 0 — Classification & Identity

- **API Name**: Evaluate Simulation Response
- **API Type**: Public / Learner Practice & Examination
- **Module**: `content-builder`
- **Feature**: Driving Simulation Scoring & Evaluation
- **Description**: Nhận thời điểm học viên nhấn phím Space (tính theo giây `selectedSecond` từ đầu video), đối chiếu với mốc thời gian chấm điểm chuẩn của tình huống (`scoringTimeline`) và trả về:
  1. **Điểm số**: từ `0` đến `5` điểm.
  2. **Phân loại kết quả**: `PERFECT` (5đ), `GOOD` (4đ), `ACCEPTABLE` (3đ), `LATE` (2đ), `VERY_LATE` (1đ), `PRESSED_TOO_EARLY` (0đ), `PRESSED_TOO_LATE` (0đ).
  3. **Mã lỗi cụ thể**: `SIMULATION_PRESSED_TOO_EARLY` (khi nhấn quá sớm trước khi tình huống xuất hiện) hoặc `SIMULATION_PRESSED_TOO_LATE` (khi nhấn quá muộn sau khi tai nạn đã xảy ra).
  4. **Lời nhận xét / phản hồi sư phạm**: Hướng dẫn học viên rút kinh nghiệm quan sát.
  5. **Dữ liệu mốc điểm đầy đủ**: Giúp Frontend hiển thị trực quan các dải màu điểm số trên thanh video player.
- **Related Tables**: `questions`, `question_options`

---

## Part 1 — API Contract

### Endpoint

- **HTTP Method**: `POST`
- **URL**: `/api/v1/content-builder/simulations/{idOrNumber}/evaluate`
- **Content Type**: `application/json`

### Request

#### Path Variables
| Tên tham số | Kiểu dữ liệu | Bắt buộc | Mô tả |
|---|---|---|---|
| `idOrNumber` | String | Có | UUID câu hỏi hoặc số thứ tự kịch bản (1–120) |

#### Request Body
| Tên trường | Kiểu dữ liệu | Bắt buộc | Mô tả | Validation Rules |
|---|---|---|---|---|
| `selectedSecond` | Double | Có | Thời điểm học viên bấm phím Space (giây) tính từ đầu video | `@NotNull`, `@DecimalMin("0.0")` |

#### Ví dụ Request Body:
```json
{
  "selectedSecond": 17.5
}
```

---

### Response

- **Success Status**: `200 OK`

#### Response Body
| Tên trường | Kiểu dữ liệu | Mô tả |
|---|---|---|
| `questionId` | String (UUID) | UUID câu hỏi trong database |
| `scenarioNumber` | Integer | Số thứ tự tình huống (1 – 120) |
| `title` | String | Tên kịch bản tình huống |
| `selectedSecond` | Double | Thời điểm học viên đã bấm phím Space |
| `score` | Integer | Điểm số đạt được: từ `0` đến `5` điểm |
| `evaluationResult` | String | Kết quả đánh giá:<br>• `PERFECT` (5 điểm)<br>• `GOOD` (4 điểm)<br>• `ACCEPTABLE` (3 điểm)<br>• `LATE` (2 điểm)<br>• `VERY_LATE` (1 điểm)<br>• `PRESSED_TOO_EARLY` (0 điểm - bấm quá sớm)<br>• `PRESSED_TOO_LATE` (0 điểm - bấm quá muộn) |
| `errorCode` | String | Mã lỗi chi tiết nếu vi phạm (`SIMULATION_PRESSED_TOO_EARLY`, `SIMULATION_PRESSED_TOO_LATE` hoặc `null` nếu đạt điểm) |
| `message` | String | Nhận xét chi tiết kèm thông số thời gian |
| `tip` | String | Mẹo canh thời điểm bấm phím Space |
| `hazardSign` | String | Dấu hiệu nhận biết tình huống nguy hiểm bắt đầu |
| `scoringTimeline` | Object | Mốc thời gian thang điểm chuẩn để FE vẽ thanh phổ điểm |

---

### Các kịch bản chấm điểm thực tế

#### Kịch bản 1: Bấm quá sớm (0 điểm)
```json
{
  "questionId": "5a629cca-524c-454c-9a8b-c333aeb09607",
  "scenarioNumber": 1,
  "title": "Người đi bộ sang đường bị khuất sau xe tải",
  "selectedSecond": 15.2,
  "score": 0,
  "evaluationResult": "PRESSED_TOO_EARLY",
  "errorCode": "SIMULATION_PRESSED_TOO_EARLY",
  "message": "Bạn đã bấm quá sớm tại giây 15.20s (Tình huống nguy hiểm chưa xuất hiện, mốc 5 điểm bắt đầu lúc 17.18s). Điểm đạt được: 0 điểm.",
  "tip": "Nhấn space khi quan sát rõ thấy người đi bộ thứ nhất xuất hiện sau xe tải để sang đường",
  "hazardSign": "Người đi bộ sau xe tải sang đường",
  "scoringTimeline": {
    "t5_start": 17.18,
    "t4_start": 17.71,
    "t3_start": 18.25,
    "t2_start": 18.78,
    "t1_start": 19.32,
    "t0_end": 19.85
  }
}
```

#### Kịch bản 2: Bấm thời điểm vàng (Đạt 5 điểm tối đa)
```json
{
  "questionId": "5a629cca-524c-454c-9a8b-c333aeb09607",
  "scenarioNumber": 1,
  "title": "Người đi bộ sang đường bị khuất sau xe tải",
  "selectedSecond": 17.5,
  "score": 5,
  "evaluationResult": "PERFECT",
  "errorCode": null,
  "message": "Xuất sắc! Bạn đã bấm tại giây 17.50s - thời điểm vàng đạt điểm tối đa: 5/5 điểm (Khung 5 điểm: 17.18s - 17.71s).",
  "tip": "Nhấn space khi quan sát rõ thấy người đi bộ thứ nhất xuất hiện sau xe tải để sang đường",
  "hazardSign": "Người đi bộ sau xe tải sang đường",
  "scoringTimeline": {
    "t5_start": 17.18,
    "t4_start": 17.71,
    "t3_start": 18.25,
    "t2_start": 18.78,
    "t1_start": 19.32,
    "t0_end": 19.85
  }
}
```

#### Kịch bản 3: Bấm quá muộn sau khi tai nạn đã xảy ra (0 điểm)
```json
{
  "questionId": "5a629cca-524c-454c-9a8b-c333aeb09607",
  "scenarioNumber": 1,
  "title": "Người đi bộ sang đường bị khuất sau xe tải",
  "selectedSecond": 21.0,
  "score": 0,
  "evaluationResult": "PRESSED_TOO_LATE",
  "errorCode": "SIMULATION_PRESSED_TOO_LATE",
  "message": "Bạn đã bấm quá muộn tại giây 21.00s (Tai nạn/va chạm đã xảy ra sau mốc 19.85s). Điểm đạt được: 0 điểm.",
  "tip": "Nhấn space khi quan sát rõ thấy người đi bộ thứ nhất xuất hiện sau xe tải để sang đường",
  "hazardSign": "Người đi bộ sau xe tải sang đường",
  "scoringTimeline": {
    "t5_start": 17.18,
    "t4_start": 17.71,
    "t3_start": 18.25,
    "t2_start": 18.78,
    "t1_start": 19.32,
    "t0_end": 19.85
  }
}
```

---

## Part 2 — Processing Specification

```
Học viên gửi request (selectedSecond)
      │
      ▼
SimulationController.evaluateSimulation
      │
      ▼
SimulationService.findSimulationQuestion
      │  (Tìm theo UUID hoặc scenarioNumber 1-120)
      ▼
Trích xuất mốc điểm từ metadata.scoringTimeline:
  t5_start, t4_start, t3_start, t2_start, t1_start, t0_end
      │
      ├─► selectedSecond < t5_start   --> 0 điểm (PRESSED_TOO_EARLY)
      ├─► selectedSecond < t4_start   --> 5 điểm (PERFECT)
      ├─► selectedSecond < t3_start   --> 4 điểm (GOOD)
      ├─► selectedSecond < t2_start   --> 3 điểm (ACCEPTABLE)
      ├─► selectedSecond < t1_start   --> 2 điểm (LATE)
      ├─► selectedSecond <= t0_end    --> 1 điểm (VERY_LATE)
      └─► selectedSecond > t0_end     --> 0 điểm (PRESSED_TOO_LATE)
      │
      ▼
Đóng gói SimulationEvaluationResponse trả về cho Client
```

