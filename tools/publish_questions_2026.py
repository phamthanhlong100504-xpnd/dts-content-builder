"""Import the validated 2026 question bank into Content Builder."""

import argparse
import json
import os
import urllib.error
import urllib.parse
import urllib.request
import uuid
from pathlib import Path
from typing import Dict, Optional

from import_question_bank_2026 import (
    BANK_VERSION,
    inspect,
    preview_question_request,
    read_json,
    require,
)


BASE_URL = ""

# Giữ kết nối trực tiếp tới service, không phụ thuộc proxy của máy chạy.
HTTP_OPENER = urllib.request.build_opener(
    urllib.request.ProxyHandler({})
)


def normalize_base_url(value: str) -> str:
    url = (value or "").strip().rstrip("/")

    require(
        bool(url),
        (
            "Thiếu Content Builder URL. "
            "Dùng --base-url hoặc biến môi trường "
            "DTS_CONTENT_BUILDER_URL."
        ),
    )

    require(
        url.startswith(("http://", "https://")),
        "Content Builder URL phải bắt đầu bằng http:// hoặc https://.",
    )

    return url


def configure_base_url(cli_value: Optional[str]) -> None:
    global BASE_URL

    configured = (
        cli_value
        or os.environ.get("DTS_CONTENT_BUILDER_URL", "")
    )

    BASE_URL = normalize_base_url(configured)


def api(
    method: str,
    path: str,
    token: str,
    payload: Optional[dict] = None,
):
    require(
        bool(BASE_URL),
        "Content Builder URL chưa được cấu hình.",
    )

    body = (
        json.dumps(
            payload,
            ensure_ascii=False,
        ).encode("utf-8")
        if payload
        else None
    )

    request = urllib.request.Request(
        BASE_URL + path,
        data=body,
        method=method,
        headers={
            "Authorization": "Bearer " + token,
            "Accept": "application/json",
            "Content-Type": "application/json; charset=utf-8",
        },
    )

    try:
        with HTTP_OPENER.open(
            request,
            timeout=30,
        ) as response:
            return json.load(response)

    except urllib.error.HTTPError as error:
        detail = error.read(500).decode(
            "utf-8",
            errors="replace",
        )

        raise RuntimeError(
            f"API {method} {path}: "
            f"HTTP {error.code}: {detail}"
        ) from error

    except urllib.error.URLError as error:
        raise RuntimeError(
            f"Không kết nối được Content Builder "
            f"tại {BASE_URL}: {error.reason}"
        ) from error


def existing_questions(
    token: str,
) -> Dict[int, dict]:
    found = {}
    page_number = 0

    while True:
        page = api(
            "GET",
            (
                "/api/v1/content-builder/questions"
                f"?page={page_number}&size=100"
            ),
            token,
        )

        require(
            isinstance(page.get("content"), list),
            "Trang câu hỏi API không hợp lệ.",
        )

        for question in page["content"]:
            metadata = question.get("metadata") or {}

            if metadata.get("bankVersion") != BANK_VERSION:
                continue

            source_id = metadata.get("sourceQuestionId")

            require(
                type(source_id) is int
                and 1 <= source_id <= 600,
                (
                    "Câu đã nhập có "
                    "sourceQuestionId không hợp lệ."
                ),
            )

            require(
                source_id not in found,
                (
                    "Content Builder có câu trùng "
                    f"ID gốc {source_id}."
                ),
            )

            found[source_id] = question

        if page.get("last") is True:
            return found

        page_number += 1

        require(
            page_number < 100,
            "Phân trang API không kết thúc.",
        )


def validate_existing(
    question: dict,
    expected: dict,
    token: str,
) -> str:
    question_id = question["id"]

    detail = api(
        "GET",
        (
            "/api/v1/content-builder/questions/"
            f"{urllib.parse.quote(question_id)}"
            "?includeOptions=true"
        ),
        token,
    )

    source_id = expected["metadata"]["sourceQuestionId"]

    for field in (
        "type",
        "content",
        "explanations",
        "mediaFileIds",
        "metadata",
    ):
        require(
            detail.get(field) == expected[field],
            (
                f"Câu {source_id} đã tồn tại nhưng "
                f"trường {field} khác dữ liệu gốc."
            ),
        )

    require(
        detail.get("status") == "PUBLISHED",
        (
            f"Câu {source_id} đã tồn tại "
            "nhưng chưa PUBLISHED."
        ),
    )

    options = detail.get("options") or []

    require(
        len(options) == len(expected["options"]),
        (
            f"Câu {source_id} đã tồn tại "
            "nhưng số phương án khác."
        ),
    )

    actual_options = sorted(
        (
            (
                option["sortOrder"],
                option["content"],
                option["isCorrect"],
                option.get("metadata"),
            )
            for option in options
        ),
        key=lambda item: item[0],
    )

    expected_options = sorted(
        (
            (
                option["sortOrder"],
                option["content"],
                option["isCorrect"],
                option.get("metadata"),
            )
            for option in expected["options"]
        ),
        key=lambda item: item[0],
    )

    require(
        actual_options == expected_options,
        (
            f"Câu {source_id} đã tồn tại nhưng "
            "phương án khác dữ liệu gốc."
        ),
    )

    return question_id


def write_checkpoint(
    path: Path,
    ids: Dict[int, str],
) -> None:
    document = {
        "bankVersion": BANK_VERSION,
        "contentBuilderUrl": BASE_URL,
        "questionUuidBySourceId": {
            str(source_id): ids[source_id]
            for source_id in sorted(ids)
        },
    }

    path.parent.mkdir(
        parents=True,
        exist_ok=True,
    )

    temporary = path.with_name(
        path.name + ".tmp"
    )

    temporary.write_text(
        json.dumps(
            document,
            ensure_ascii=False,
            indent=2,
        )
        + "\n",
        encoding="utf-8",
    )

    temporary.replace(path)


def main() -> None:
    parser = argparse.ArgumentParser(
        description=(
            "Nhập bộ 600 câu hỏi 2026 "
            "vào Content Builder."
        )
    )

    parser.add_argument(
        "--base-url",
        help=(
            "URL Content Builder, ví dụ "
            "https://content-builder.example.com. "
            "Nếu không truyền, dùng biến môi trường "
            "DTS_CONTENT_BUILDER_URL."
        ),
    )

    parser.add_argument(
        "--root",
        type=Path,
        default=(
            Path(__file__).resolve().parent.parent
            / "data/question-banks/2026/600"
        ),
        help="Thư mục chứa bộ dữ liệu 600 câu.",
    )

    parser.add_argument(
        "--media-map",
        type=Path,
        required=True,
        help=(
            "File mapping questionId -> media UUID "
            "đã được Media Service tạo."
        ),
    )

    parser.add_argument(
        "--checkpoint",
        type=Path,
        default=None,
        help=(
            "File lưu mapping sourceQuestionId -> "
            "Content Builder question UUID. "
            "Mặc định lưu cạnh media map."
        ),
    )

    parser.add_argument(
        "--apply",
        action="store_true",
        help="Cho phép POST câu hỏi vào Content Builder.",
    )

    parser.add_argument(
        "--limit",
        type=int,
        default=600,
        help="Giới hạn số câu mới tạo trong một lượt.",
    )

    args = parser.parse_args()

    require(
        1 <= args.limit <= 600,
        "--limit phải từ 1 đến 600.",
    )

    configure_base_url(args.base_url)

    # Kiểm tra bộ câu hỏi và SHA-256 của toàn bộ ảnh
    # trước khi gọi API.
    inspect(args.root)

    media_map = read_json(args.media_map)

    require(
        media_map.get("bankVersion") == BANK_VERSION,
        "Phiên bản Media map không khớp.",
    )

    require(
        not media_map.get("pendingByQuestionId"),
        "Media map còn ảnh đang chờ xác minh.",
    )

    media = media_map.get("mediaByQuestionId")

    images = read_json(
        args.root / "image-manifest.json"
    )["images"]

    expected_image_ids = {
        str(entry["questionId"])
        for entry in images
    }

    require(
        isinstance(media, dict)
        and set(media) == expected_image_ids,
        "Media map không khớp đúng 318 câu có ảnh.",
    )

    for source_id, media_id in media.items():
        try:
            uuid.UUID(media_id)

        except (TypeError, ValueError) as error:
            raise ValueError(
                (
                    f"Media UUID của câu {source_id} "
                    "không hợp lệ."
                )
            ) from error

    token = os.environ.get(
        "DTS_IMPORT_TOKEN",
        "",
    )

    require(
        token,
        "Thiếu biến môi trường DTS_IMPORT_TOKEN.",
    )

    questions = read_json(
        args.root / "questions.json"
    )

    rules = read_json(
        args.root / "critical-by-license.json"
    )["criticalQuestionIdsByLicense"]

    payloads = {}

    for question in questions:
        source_id = question["id"]

        payload = preview_question_request(
            question,
            rules,
        )

        payload["mediaFileIds"] = (
            [media[str(source_id)]]
            if question["image"]
            else []
        )

        payloads[source_id] = payload

    found = existing_questions(token)

    require(
        set(found) <= set(payloads),
        "Có câu thuộc phiên bản này ngoài bộ 600.",
    )

    ids = {}

    for source_id, question in sorted(
        found.items()
    ):
        ids[source_id] = validate_existing(
            question,
            payloads[source_id],
            token,
        )

    print(
        f"API {BASE_URL}: "
        f"{len(found)} câu đã có và khớp nguồn; "
        f"{600 - len(found)} câu chưa nhập."
    )

    if not args.apply:
        print(
            "CHECK OK: chỉ đọc API và dữ liệu; "
            "chưa POST."
        )
        return

    checkpoint = (
        args.checkpoint
        if args.checkpoint is not None
        else (
            args.media_map.parent
            / "content-builder-question-map.json"
        )
    )

    write_checkpoint(
        checkpoint,
        ids,
    )

    created = 0

    for source_id in sorted(payloads):
        if source_id in ids:
            continue

        if created >= args.limit:
            break

        result = api(
            "POST",
            "/api/v1/content-builder/questions/published",
            token,
            payloads[source_id],
        )

        require(
            result.get("metadata")
            == payloads[source_id]["metadata"],
            (
                f"Câu {source_id}: "
                "phản hồi POST không khớp metadata."
            ),
        )

        question_uuid = result.get("id")

        uuid.UUID(question_uuid)

        ids[source_id] = question_uuid

        write_checkpoint(
            checkpoint,
            ids,
        )

        created += 1

        print(
            f"Đã nhập câu {source_id}/600"
        )

    print(
        f"Hoàn tất lượt: tạo {created} câu; "
        f"tổng {len(ids)}/600. "
        f"Map: {checkpoint}"
    )


if __name__ == "__main__":
    main()