"""Publish six chapters for the validated 2026 bank to Content Builder."""

import argparse
import contextlib
import io
import json
import os
import uuid
from collections import defaultdict
from pathlib import Path

from import_question_bank_2026 import (
    BANK_VERSION,
    inspect,
    read_json,
    require,
)

import publish_questions_2026 as question_publisher


CHAPTER_NAMES = {
    1: "Quy định chung và quy tắc giao thông đường bộ",
    2: (
        "Văn hóa giao thông, đạo đức người lái xe, "
        "kỹ năng PCCC và cứu hộ, cứu nạn"
    ),
    3: "Kỹ thuật lái xe",
    4: "Cấu tạo và sửa chữa",
    5: "Báo hiệu đường bộ",
    6: (
        "Giải thế sa hình và kỹ năng xử lý "
        "tình huống giao thông"
    ),
}

CHAPTER_COUNTS = {
    1: 180,
    2: 25,
    3: 58,
    4: 37,
    5: 185,
    6: 115,
}

CHAPTER_PATH = "/api/v1/content-builder/chapters"


def existing_chapters(token):
    result = {}
    page_number = 0

    while True:
        page = question_publisher.api(
            "GET",
            f"{CHAPTER_PATH}?page={page_number}&size=20",
            token,
        )

        require(
            isinstance(page.get("content"), list),
            "Danh sách chương API không hợp lệ.",
        )

        for chapter in page["content"]:
            metadata = chapter.get("metadata") or {}

            if metadata.get("bankVersion") != BANK_VERSION:
                continue

            source_id = metadata.get("sourceChapterId")

            require(
                type(source_id) is int
                and source_id in CHAPTER_NAMES,
                (
                    "Có chương thuộc bộ 2026 mang "
                    "sourceChapterId không hợp lệ."
                ),
            )

            require(
                source_id not in result,
                (
                    "Content Builder đã có chương trùng "
                    f"sourceChapterId {source_id}."
                ),
            )

            result[source_id] = chapter

        if page.get("last") is True:
            return result

        page_number += 1

        require(
            page_number < 100,
            "Phân trang chương không kết thúc.",
        )


def check_chapter(detail, expected):
    source_id = expected["metadata"]["sourceChapterId"]

    require(
        detail.get("title") == expected["title"],
        f"Chương {source_id}: sai tên.",
    )

    require(
        detail.get("status") == "PUBLISHED",
        f"Chương {source_id}: chưa PUBLISHED.",
    )

    require(
        detail.get("metadata") == expected["metadata"],
        f"Chương {source_id}: sai metadata.",
    )

    blocks = detail.get("questionBlocks") or []

    require(
        len(blocks) == len(expected["questionBlocks"]),
        (
            f"Chương {source_id}: "
            "thiếu hoặc thừa questionBlocks."
        ),
    )

    actual = sorted(
        blocks,
        key=lambda item: item["sortOrder"],
    )

    planned = expected["questionBlocks"]

    for index, (block, wanted) in enumerate(
        zip(actual, planned)
    ):
        for field in (
            "questionId",
            "title",
            "sortOrder",
            "metadata",
        ):
            require(
                block.get(field) == wanted[field],
                (
                    f"Chương {source_id}: "
                    f"block thứ {index} sai {field}."
                ),
            )

        require(
            block.get("status") == "PUBLISHED",
            (
                f"Chương {source_id}: "
                f"block thứ {index} chưa PUBLISHED."
            ),
        )

    chapter_uuid = detail.get("id")

    uuid.UUID(chapter_uuid)

    return chapter_uuid


def write_checkpoint(path, ids):
    document = {
        "bankVersion": BANK_VERSION,
        "contentBuilderUrl": question_publisher.BASE_URL,
        "chapterUuidBySourceId": {
            str(key): ids[key]
            for key in sorted(ids)
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


def main():
    parser = argparse.ArgumentParser(
        description=(
            "Tạo sáu chương của bộ câu hỏi 2026 "
            "trên Content Builder."
        )
    )

    parser.add_argument(
        "--base-url",
        help=(
            "URL Content Builder. "
            "Nếu không truyền, sử dụng biến môi trường "
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
        "--question-map",
        type=Path,
        required=True,
        help=(
            "File mapping sourceQuestionId -> "
            "Content Builder question UUID."
        ),
    )

    parser.add_argument(
        "--checkpoint",
        type=Path,
        default=None,
        help=(
            "File lưu mapping sourceChapterId -> "
            "Content Builder chapter UUID. "
            "Mặc định lưu cạnh question map."
        ),
    )

    parser.add_argument(
        "--apply",
        action="store_true",
        help="Cho phép POST tạo chương.",
    )

    parser.add_argument(
        "--limit",
        type=int,
        default=6,
        help="Giới hạn số chương mới tạo trong một lượt.",
    )

    args = parser.parse_args()

    require(
        1 <= args.limit <= 6,
        "--limit phải từ 1 đến 6.",
    )

    # Dùng cùng cơ chế cấu hình URL với publish_questions_2026.py.
    question_publisher.configure_base_url(
        args.base_url
    )

    with contextlib.redirect_stdout(
        io.StringIO()
    ):
        inspect(args.root)

    print(
        "SOURCE CHECK OK: "
        "600 câu, 318 ảnh, 15 hạng."
    )

    mapping = read_json(
        args.question_map
    )

    require(
        mapping.get("bankVersion")
        == BANK_VERSION,
        "Map UUID câu hỏi không đúng bankVersion.",
    )

    require(
        mapping.get("contentBuilderUrl")
        == question_publisher.BASE_URL,
        (
            "Map UUID câu hỏi thuộc Content Builder "
            "khác với URL đang cấu hình."
        ),
    )

    ids = mapping.get(
        "questionUuidBySourceId"
    )

    require(
        isinstance(ids, dict)
        and set(ids)
        == {
            str(i)
            for i in range(1, 601)
        },
        (
            "Map phải chứa đủ và chỉ chứa "
            "600 sourceQuestionId."
        ),
    )

    for source_id, question_uuid in ids.items():
        try:
            uuid.UUID(question_uuid)

        except (TypeError, ValueError) as error:
            raise ValueError(
                (
                    f"UUID câu {source_id} "
                    "không hợp lệ."
                )
            ) from error

    grouped = defaultdict(list)

    for question in read_json(
        args.root / "questions.json"
    ):
        grouped[
            question["chapter_id"]
        ].append(
            question["id"]
        )

    require(
        {
            key: len(value)
            for key, value in grouped.items()
        }
        == CHAPTER_COUNTS,
        (
            "Số câu của sáu chương không khớp "
            "bộ 600 đã kiểm tra."
        ),
    )

    plans = {}

    for chapter_id in sorted(
        CHAPTER_NAMES
    ):
        blocks = []

        for index, source_id in enumerate(
            sorted(grouped[chapter_id])
        ):
            blocks.append({
                "questionId": ids[str(source_id)],
                "title": f"Câu {source_id}",
                "sortOrder": index,
                "metadata": {
                    "bankVersion": BANK_VERSION,
                    "sourceQuestionId": source_id,
                },
            })

        plans[chapter_id] = {
            "title": CHAPTER_NAMES[chapter_id],
            "metadata": {
                "bankVersion": BANK_VERSION,
                "sourceChapterId": chapter_id,
            },
            "questionBlocks": blocks,
        }

    token = os.environ.get(
        "DTS_IMPORT_TOKEN",
        "",
    )

    require(
        token,
        "Thiếu biến môi trường DTS_IMPORT_TOKEN.",
    )

    questions = (
        question_publisher.existing_questions(
            token
        )
    )

    require(
        len(questions) == 600,
        "Content Builder chưa có đủ 600 câu.",
    )

    for source_id in range(1, 601):
        require(
            questions[source_id]["id"]
            == ids[str(source_id)],
            (
                f"Map UUID của câu {source_id} "
                "không khớp database."
            ),
        )

    found = existing_chapters(
        token
    )

    chapter_ids = {}

    for chapter_id, chapter in sorted(
        found.items()
    ):
        detail = question_publisher.api(
            "GET",
            f"{CHAPTER_PATH}/{chapter['id']}",
            token,
        )

        chapter_ids[chapter_id] = (
            check_chapter(
                detail,
                plans[chapter_id],
            )
        )

    print(
        f"API {question_publisher.BASE_URL}: "
        f"{len(found)} chương đã khớp; "
        f"{6 - len(found)} chương chưa nhập."
    )

    if not args.apply:
        print(
            "CHECK OK: chưa POST chương."
        )
        return

    checkpoint = (
        args.checkpoint
        if args.checkpoint is not None
        else (
            args.question_map.parent
            / "content-builder-chapter-map.json"
        )
    )

    write_checkpoint(
        checkpoint,
        chapter_ids,
    )

    created = 0

    for chapter_id in sorted(plans):
        if chapter_id in chapter_ids:
            continue

        if created >= args.limit:
            break

        response = question_publisher.api(
            "POST",
            f"{CHAPTER_PATH}/published",
            token,
            plans[chapter_id],
        )

        chapter_ids[chapter_id] = (
            check_chapter(
                response,
                plans[chapter_id],
            )
        )

        write_checkpoint(
            checkpoint,
            chapter_ids,
        )

        created += 1

        print(
            f"Đã tạo chương {chapter_id}: "
            f"{len(plans[chapter_id]['questionBlocks'])} câu"
        )

    print(
        f"Hoàn tất lượt: tạo {created} chương; "
        f"tổng {len(chapter_ids)}/6. "
        f"Map: {checkpoint}"
    )


if __name__ == "__main__":
    main()