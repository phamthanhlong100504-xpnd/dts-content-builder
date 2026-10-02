"""Upload the validated 2026 question bank images to DTS Media."""

import argparse
import base64
import hashlib
import json
import os
import time
import uuid
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.parse import urlsplit
from urllib.request import Request, urlopen


BANK_VERSION = "dts-2026-600-v1"

MEDIA_API = ""

DEFAULT_ROOT = (
    Path(__file__).resolve().parent.parent
    / "data/question-banks/2026/600"
)


class ApiError(RuntimeError):
    def __init__(self, endpoint, status):
        self.status = status
        super().__init__(
            f"Media API {endpoint}: HTTP {status}"
        )


def require(condition, message):
    if not condition:
        raise ValueError(message)


def normalize_media_api(value):
    url = (value or "").strip().rstrip("/")

    require(
        bool(url),
        (
            "Thiếu Media API URL. "
            "Dùng --media-api hoặc biến môi trường "
            "DTS_MEDIA_API_URL."
        ),
    )

    require(
        url.startswith(("http://", "https://")),
        "Media API URL phải bắt đầu bằng http:// hoặc https://.",
    )

    return url


def configure_media_api(cli_value):
    global MEDIA_API

    configured = (
        cli_value
        or os.environ.get("DTS_MEDIA_API_URL", "")
    )

    MEDIA_API = normalize_media_api(
        configured
    )


def read_json(path):
    return json.loads(
        path.read_text(
            encoding="utf-8-sig"
        )
    )


def api(
    method,
    path,
    token=None,
    body=None,
):
    require(
        bool(MEDIA_API),
        "Media API URL chưa được cấu hình.",
    )

    headers = {}

    if token:
        headers["Authorization"] = (
            "Bearer " + token
        )

    data = None

    if body is not None:
        headers["Content-Type"] = (
            "application/json"
        )
        data = json.dumps(
            body
        ).encode("utf-8")

    elif method == "POST":
        data = b""

    try:
        request = Request(
            MEDIA_API + path,
            data=data,
            headers=headers,
            method=method,
        )

        with urlopen(
            request,
            timeout=20,
        ) as response:
            return json.load(response)

    except HTTPError as error:
        raise ApiError(
            path,
            error.code,
        ) from error

    except URLError as error:
        raise RuntimeError(
            (
                "Không kết nối được Media API tại "
                f"{MEDIA_API}: {error.reason}"
            )
        ) from error


def token_expiration(token):
    try:
        payload = token.split(".")[1]

        padding = "=" * (
            -len(payload) % 4
        )

        claims = json.loads(
            base64.urlsafe_b64decode(
                payload + padding
            )
        )

        return int(
            claims["exp"]
        )

    except (
        ValueError,
        IndexError,
        KeyError,
        TypeError,
    ) as error:
        raise ValueError(
            (
                "JWT không có thời hạn exp "
                "hợp lệ."
            )
        ) from error


def manifest_entries(root):
    manifest = read_json(
        root / "image-manifest.json"
    )

    if (
        manifest.get("bankVersion")
        != BANK_VERSION
    ):
        raise ValueError(
            (
                "Sai bankVersion trong "
                "image-manifest.json"
            )
        )

    entries = manifest.get("images")

    if (
        not isinstance(entries, list)
        or len(entries) != 318
    ):
        raise ValueError(
            "Manifest phải có đúng 318 ảnh"
        )

    ids = [
        entry["questionId"]
        for entry in entries
    ]

    if ids != sorted(set(ids)):
        raise ValueError(
            (
                "questionId ảnh không duy nhất "
                "hoặc chưa được sắp xếp"
            )
        )

    checked = []

    for entry in entries:
        question_id = entry["questionId"]

        expected_path = (
            f"images/{question_id}.webp"
        )

        if entry["path"] != expected_path:
            raise ValueError(
                (
                    "Sai đường dẫn ảnh câu "
                    f"{question_id}"
                )
            )

        path = root / expected_path
        content = path.read_bytes()

        if (
            content[:4] != b"RIFF"
            or content[8:12] != b"WEBP"
            or len(content)
            != entry["sizeBytes"]
            or hashlib.sha256(
                content
            ).hexdigest()
            != entry["sha256"]
        ):
            raise ValueError(
                (
                    f"Ảnh câu {question_id} "
                    "không khớp manifest"
                )
            )

        checked.append(
            (
                question_id,
                content,
            )
        )

    return checked


def read_map(
    path,
    valid_ids,
):
    if path.exists():
        mapping = read_json(path)

    else:
        mapping = {
            "bankVersion": BANK_VERSION,
            "mediaByQuestionId": {},
            "pendingByQuestionId": {},
        }

    if (
        mapping.get("bankVersion")
        != BANK_VERSION
    ):
        raise ValueError(
            (
                "Sai bankVersion trong "
                "file ánh xạ media"
            )
        )

    by_id = mapping.get(
        "mediaByQuestionId"
    )

    if not isinstance(
        by_id,
        dict,
    ):
        raise ValueError(
            (
                "File ánh xạ thiếu "
                "mediaByQuestionId"
            )
        )

    for question_id, media_id in (
        by_id.items()
    ):
        if (
            not question_id.isdigit()
            or int(question_id)
            not in valid_ids
        ):
            raise ValueError(
                (
                    "ID câu hỏi không thuộc "
                    f"manifest: {question_id}"
                )
            )

        uuid.UUID(media_id)

    pending = mapping.setdefault(
        "pendingByQuestionId",
        {},
    )

    if not isinstance(
        pending,
        dict,
    ):
        raise ValueError(
            (
                "pendingByQuestionId "
                "không hợp lệ"
            )
        )

    for question_id, attempt in (
        pending.items()
    ):
        if (
            not question_id.isdigit()
            or int(question_id)
            not in valid_ids
            or question_id in by_id
            or not isinstance(
                attempt,
                dict,
            )
        ):
            raise ValueError(
                (
                    "Upload dở của câu "
                    f"{question_id} "
                    "không hợp lệ"
                )
            )

        uuid.UUID(
            attempt["mediaId"]
        )

        uuid.UUID(
            attempt["sessionId"]
        )

    return mapping


def save_map(
    path,
    mapping,
):
    path.parent.mkdir(
        parents=True,
        exist_ok=True,
    )

    temporary = path.with_name(
        path.name + ".tmp"
    )

    temporary.write_text(
        json.dumps(
            mapping,
            ensure_ascii=False,
            indent=2,
        )
        + "\n",
        encoding="utf-8",
    )

    os.replace(
        temporary,
        path,
    )


def detail_if_ready(
    media_id,
):
    try:
        return api(
            "GET",
            "/api/v1/media/" + media_id,
        )

    except ApiError as error:
        # Media hiện trả HTTP 500
        # khi ảnh chưa ở trạng thái READY.
        if error.status == 500:
            return None

        raise


def validate_detail(
    detail,
    question_id,
    expected_size,
):
    if (
        detail.get("status")
        != "READY"
        or detail.get(
            "originalFilename"
        )
        != f"{question_id}.webp"
        or detail.get(
            "sizeBytes"
        )
        != expected_size
        or detail.get(
            "mimeType"
        )
        != "image/webp"
    ):
        raise ValueError(
            (
                f"Media của câu {question_id} "
                "khác manifest hoặc chưa READY"
            )
        )


def validate_presigned_url(
    url,
    question_id,
):
    parsed = urlsplit(url)

    if (
        parsed.scheme
        not in ("http", "https")
        or not parsed.hostname
    ):
        raise ValueError(
            (
                f"Câu {question_id}: "
                "presigned URL không hợp lệ"
            )
        )


def upload_one(
    question_id,
    content,
    token,
    mapping,
    map_file,
):
    initial = api(
        "POST",
        "/api/v1/media/uploads/initialize",
        token,
        {
            "fileName": (
                f"{question_id}.webp"
            ),
            "sizeBytes": len(content),
            "mimeType": "image/webp",
            "visibility": "PUBLIC",
            "targetType": "QUESTION",
        },
    )

    media_id = initial["mediaId"]
    session_id = initial["sessionId"]

    uuid.UUID(media_id)
    uuid.UUID(session_id)

    # Lưu cả lượt đang dở để lần chạy sau
    # không tự tạo thêm một Media ID
    # cho cùng ảnh.
    mapping[
        "pendingByQuestionId"
    ][str(question_id)] = {
        "mediaId": media_id,
        "sessionId": session_id,
    }

    save_map(
        map_file,
        mapping,
    )

    url = initial["presignedUrl"]

    validate_presigned_url(
        url,
        question_id,
    )

    try:
        request = Request(
            url,
            data=content,
            method="PUT",
            headers={
                "Content-Type": (
                    "image/webp"
                ),
            },
        )

        with urlopen(
            request,
            timeout=30,
        ) as response:
            if response.status not in (
                200,
                204,
            ):
                raise RuntimeError(
                    (
                        "Object storage PUT "
                        f"câu {question_id}: "
                        f"HTTP {response.status}"
                    )
                )

    except HTTPError as error:
        raise RuntimeError(
            (
                "Object storage PUT "
                f"câu {question_id}: "
                f"HTTP {error.code}"
            )
        ) from error

    except URLError as error:
        raise RuntimeError(
            (
                "Không upload được ảnh câu "
                f"{question_id} qua presigned URL: "
                f"{error.reason}"
            )
        ) from error

    confirmed = api(
        "POST",
        (
            "/api/v1/media/uploads/"
            f"{session_id}/confirm"
        ),
        token,
    )

    if (
        confirmed.get("mediaId")
        != media_id
    ):
        raise RuntimeError(
            (
                "Media ID xác nhận câu "
                f"{question_id} không khớp"
            )
        )

    for _ in range(30):
        detail = detail_if_ready(
            media_id
        )

        if detail is not None:
            validate_detail(
                detail,
                question_id,
                len(content),
            )

            return media_id

        time.sleep(1)

    raise RuntimeError(
        (
            f"Ảnh câu {question_id} chưa READY "
            "sau 30 giây. "
            "Kiểm tra log Media/Kafka. "
            f"Media ID: {media_id}"
        )
    )


def main():
    parser = argparse.ArgumentParser(
        description=__doc__
    )

    parser.add_argument(
        "--media-api",
        help=(
            "URL DTS Media API. "
            "Nếu không truyền, sử dụng biến "
            "DTS_MEDIA_API_URL."
        ),
    )

    parser.add_argument(
        "--root",
        type=Path,
        default=DEFAULT_ROOT,
        help=(
            "Thư mục chứa image-manifest.json "
            "và folder images."
        ),
    )

    parser.add_argument(
        "--map-file",
        type=Path,
        default=None,
        help=(
            "File lưu mapping "
            "questionId -> media UUID. "
            "Nếu không truyền, sử dụng biến "
            "DTS_MEDIA_MAP_FILE."
        ),
    )

    parser.add_argument(
        "--limit",
        type=int,
        default=None,
        help=(
            "Số ảnh MỚI tối đa "
            "trong lượt này"
        ),
    )

    parser.add_argument(
        "--upload",
        action="store_true",
        required=True,
        help=(
            "Xác nhận cho phép upload "
            "ảnh lên Media Service."
        ),
    )

    args = parser.parse_args()

    configure_media_api(
        args.media_api
    )

    if args.map_file is None:
        configured_map = (
            os.environ.get(
                "DTS_MEDIA_MAP_FILE",
                "",
            )
            .strip()
        )

        require(
            bool(configured_map),
            (
                "Thiếu file media map. "
                "Dùng --map-file hoặc biến "
                "DTS_MEDIA_MAP_FILE."
            ),
        )

        args.map_file = Path(
            configured_map
        )

    if (
        args.limit is not None
        and args.limit < 1
    ):
        parser.error(
            "--limit phải lớn hơn 0"
        )

    entries = manifest_entries(
        args.root
    )

    valid_ids = {
        question_id
        for question_id, _ in entries
    }

    mapping = read_map(
        args.map_file,
        valid_ids,
    )

    token = os.environ.get(
        "DTS_MEDIA_TOKEN",
        "",
    )

    if not token:
        raise ValueError(
            (
                "Thiếu biến môi trường "
                "DTS_MEDIA_TOKEN"
            )
        )

    expiration = token_expiration(
        token
    )

    uploaded = 0

    by_id = mapping[
        "mediaByQuestionId"
    ]

    for question_id, content in entries:
        existing_id = by_id.get(
            str(question_id)
        )

        if existing_id:
            detail = detail_if_ready(
                existing_id
            )

            if detail is None:
                raise RuntimeError(
                    (
                        f"Ảnh câu {question_id} "
                        "có trong map nhưng "
                        "Media chưa READY"
                    )
                )

            validate_detail(
                detail,
                question_id,
                len(content),
            )

            continue

        pending = mapping[
            "pendingByQuestionId"
        ].get(
            str(question_id)
        )

        if pending:
            detail = detail_if_ready(
                pending["mediaId"]
            )

            if detail is None:
                raise RuntimeError(
                    (
                        f"Câu {question_id} "
                        "có upload dở: "
                        f"mediaId="
                        f"{pending['mediaId']}, "
                        f"sessionId="
                        f"{pending['sessionId']}. "
                        "Kiểm tra Media trước "
                        "khi chạy tiếp."
                    )
                )

            validate_detail(
                detail,
                question_id,
                len(content),
            )

            by_id[
                str(question_id)
            ] = pending["mediaId"]

            del mapping[
                "pendingByQuestionId"
            ][str(question_id)]

            save_map(
                args.map_file,
                mapping,
            )

            print(
                (
                    "Đã khôi phục ảnh READY "
                    f"của câu {question_id}"
                )
            )

            continue

        if (
            args.limit is not None
            and uploaded >= args.limit
        ):
            break

        if (
            expiration
            < time.time() + 60
        ):
            raise RuntimeError(
                (
                    "Token sắp hết hạn. "
                    "Lấy token Identity mới "
                    "rồi chạy lại; "
                    "ảnh đã hoàn tất vẫn nằm "
                    "trong map."
                )
            )

        print(
            (
                f"Đang tải câu "
                f"{question_id}..."
            ),
            flush=True,
        )

        media_id = upload_one(
            question_id,
            content,
            token,
            mapping,
            args.map_file,
        )

        by_id[
            str(question_id)
        ] = media_id

        del mapping[
            "pendingByQuestionId"
        ][str(question_id)]

        save_map(
            args.map_file,
            mapping,
        )

        uploaded += 1

        print(
            (
                f"READY "
                f"{question_id}.webp "
                f"→ {media_id}"
            ),
            flush=True,
        )

    print(
        (
            f"Hoàn tất lượt này: "
            f"{uploaded} ảnh mới; "
            f"{len(by_id)}/318 "
            "ảnh trong map."
        )
    )


if __name__ == "__main__":
    main()