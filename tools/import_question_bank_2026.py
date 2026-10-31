"""Kiểm tra kế hoạch nhập bộ câu hỏi 2026; chưa gọi API hoặc ghi database."""

import argparse
import hashlib
import json
from collections import Counter
from pathlib import Path

BANK_VERSION = "dts-2026-600-v1"

LICENSES = (
    "A1", "A", "B1", "B", "C1", "C", "D1", "D2", "D",
    "BE", "C1E", "CE", "D1E", "D2E", "DE",
)

A1_CRITICAL = [
    19, 20, 21, 22, 24, 26, 27, 28, 30, 47,
    48, 52, 53, 63, 64, 65, 68, 70, 71, 72,
]

B1_CRITICAL = A1_CRITICAL + [
    73, 74, 87, 89, 90, 91, 92, 215, 254, 255,
]


def require(condition: bool, message: str) -> None:
    if not condition:
        raise ValueError(message)


def read_json(path: Path):
    return json.loads(path.read_text(encoding="utf-8-sig"))


def preview_question_request(question: dict, rules: dict) -> dict:
    """Xem trước cấu trúc CreateQuestionRequest; không gửi request."""
    question_id = question["id"]

    return {
        "type": "SINGLE_CHOICE",
        "content": question["question"],
        "explanations": (
            {"text": question["explanation"]}
            if question["explanation"]
            else None
        ),
        # Câu có ảnh phải chờ Media trả mediaId trước khi nhập thật.
        "mediaFileIds": None if question["image"] else [],
        "metadata": {
            "bankVersion": BANK_VERSION,
            "sourceQuestionId": question_id,
            "chapterId": question["chapter_id"],
            "applicableLicenses": question["applicable_licenses"],
            "isCritical": question["is_critical"],
            "criticalLicenses": [
                license_class
                for license_class in LICENSES
                if question_id in rules[license_class]
            ],
            "sourceImagePath": question["image"],
        },
        "options": [
            {
                "content": option["text"],
                "sortOrder": position,
                "isCorrect": (
                    option["id"] == question["correct_option"]
                ),
                "metadata": {
                    "sourceOptionId": option["id"],
                },
            }
            for position, option in enumerate(question["options"])
        ],
    }


def inspect(root: Path) -> None:
    source = read_json(root / "questions.source.json")
    questions = read_json(root / "questions.json")
    critical_file = read_json(root / "critical-by-license.json")
    image_file = read_json(root / "image-manifest.json")

    require(
        isinstance(source, list) and isinstance(questions, list),
        "Câu hỏi phải là mảng JSON.",
    )
    require(
        len(source) == len(questions) == 600,
        "Phải có đúng 600 câu.",
    )

    original = {question["id"]: question for question in source}
    by_id = {question["id"]: question for question in questions}
    expected_ids = set(range(1, 601))

    require(
        len(original) == len(by_id) == 600,
        "Có ID câu hỏi trùng.",
    )
    require(
        set(original) == set(by_id) == expected_ids,
        "ID câu hỏi phải đủ từ 1 đến 600.",
    )
    require(
        sum(question["is_critical"] is True for question in questions) == 60,
        "Bộ 600 câu phải có đúng 60 cờ điểm liệt.",
    )

    require(
        critical_file["bankVersion"]
        == image_file["bankVersion"]
        == BANK_VERSION,
        "Phiên bản hai manifest không khớp.",
    )

    rules = critical_file["criticalQuestionIdsByLicense"]

    require(
        set(rules) == set(LICENSES),
        "Danh sách 15 hạng không khớp.",
    )
    require(
        rules["A1"] == rules["A"] == A1_CRITICAL,
        "Sai danh sách 20 câu điểm liệt A1/A.",
    )
    require(
        rules["B1"] == B1_CRITICAL,
        "Sai danh sách 30 câu điểm liệt B1.",
    )

    for question_id in range(1, 601):
        question = by_id[question_id]
        old = original[question_id]

        require(
            old["image"] is None,
            f"Câu {question_id}: nguồn đã chứa đường dẫn ảnh.",
        )

        old_content = {
            key: value
            for key, value in old.items()
            if key != "image"
        }
        new_content = {
            key: value
            for key, value in question.items()
            if key != "image"
        }

        require(
            old_content == new_content,
            f"Câu {question_id}: nội dung khác JSON gốc.",
        )
        require(
            isinstance(question["question"], str)
            and 0 < len(question["question"]) <= 50000,
            f"Câu {question_id}: nội dung không hợp lệ.",
        )

        options = question["options"]
        require(
            2 <= len(options) <= 50,
            f"Câu {question_id}: số phương án không hợp lệ.",
        )

        option_ids = [option["id"] for option in options]
        require(
            len(set(option_ids)) == len(options)
            and question["correct_option"] in option_ids,
            f"Câu {question_id}: đáp án đúng hoặc ID phương án sai.",
        )
        require(
            all(
                isinstance(option["text"], str)
                and 0 < len(option["text"]) <= 10000
                for option in options
            ),
            f"Câu {question_id}: nội dung phương án không hợp lệ.",
        )

        applicable = question["applicable_licenses"]
        require(
            applicable
            and len(applicable) == len(set(applicable))
            and set(applicable) <= set(LICENSES),
            f"Câu {question_id}: hạng áp dụng không hợp lệ.",
        )

    per_class = {}

    for license_class in LICENSES:
        applicable = [
            question
            for question in questions
            if license_class in question["applicable_licenses"]
        ]
        globally_flagged = {
            question["id"]
            for question in applicable
            if question["is_critical"] is True
        }
        selected = rules[license_class]

        if license_class in ("A1", "A"):
            expected_total, expected_critical = 250, 20
        elif license_class == "B1":
            expected_total, expected_critical = 300, 30
        else:
            expected_total, expected_critical = 600, 60

        require(
            len(applicable) == expected_total,
            f"Hạng {license_class}: sai tổng số câu.",
        )
        require(
            selected == sorted(set(selected))
            and set(selected) <= globally_flagged
            and len(selected) == expected_critical,
            f"Hạng {license_class}: sai câu điểm liệt.",
        )

        if license_class not in ("A1", "A", "B1"):
            require(
                selected == sorted(globally_flagged),
                f"Hạng {license_class}: thiếu câu điểm liệt.",
            )

        chapter_counts = dict(
            sorted(
                Counter(
                    question["chapter_id"]
                    for question in applicable
                ).items()
            )
        )
        per_class[license_class] = (
            len(applicable),
            expected_critical,
            chapter_counts,
        )

    entries = image_file["images"]

    require(
        isinstance(entries, list) and len(entries) == 318,
        "Manifest phải có đúng 318 ảnh.",
    )

    image_ids = [entry["questionId"] for entry in entries]

    require(
        image_ids == sorted(set(image_ids)),
        "Manifest ảnh có ID trùng hoặc chưa sắp xếp.",
    )

    image_dir = root / "images"
    require(image_dir.is_dir(), "Không tìm thấy thư mục images.")

    disk_files = {
        path.name
        for path in image_dir.iterdir()
        if path.is_file()
    }

    require(
        disk_files == {
            f"{question_id}.webp"
            for question_id in image_ids
        },
        "File trong thư mục images không khớp manifest.",
    )

    for entry in entries:
        question_id = entry["questionId"]
        relative_path = f"images/{question_id}.webp"

        require(
            entry["path"] == relative_path
            and by_id[question_id]["image"] == relative_path,
            f"Câu {question_id}: đường dẫn ảnh không khớp.",
        )

        content = (root / relative_path).read_bytes()

        require(
            content[:4] == b"RIFF"
            and content[8:12] == b"WEBP"
            and len(content) == entry["sizeBytes"]
            and hashlib.sha256(content).hexdigest() == entry["sha256"],
            f"Ảnh câu {question_id}: sai định dạng, dung lượng hoặc SHA-256.",
        )

    for question_id in expected_ids - set(image_ids):
        require(
            by_id[question_id]["image"] is None,
            f"Câu {question_id}: có ảnh ngoài manifest.",
        )

    requests = [
        preview_question_request(by_id[question_id], rules)
        for question_id in range(1, 601)
    ]

    require(
        len(requests) == 600
        and all(
            sum(option["isCorrect"] for option in request["options"]) == 1
            for request in requests
        ),
        "Ánh xạ request hoặc đáp án đúng thất bại.",
    )

    print(
        f"DRY RUN OK — {BANK_VERSION}: "
        "600 câu, 318 ảnh, 6 chương, 15 hạng."
    )
    print("Chưa gọi API; chưa ghi Media/Content Builder/Practice.")

    for license_class, (
        total,
        critical_count,
        chapter_counts,
    ) in per_class.items():
        print(
            f"{license_class:4} | {total:3} câu | "
            f"{critical_count:2} điểm liệt | "
            f"chương {chapter_counts}"
        )

    missing_explanations = [
        question["id"]
        for question in questions
        if not (question.get("explanation") or "").strip()
    ]
    print("Câu thiếu giải thích:", missing_explanations)

    sample = requests[36]  # Câu ID 37
    sample_result = {
        "metadata": sample["metadata"],
        "correctSourceOptionId": next(
            option["metadata"]["sourceOptionId"]
            for option in sample["options"]
            if option["isCorrect"]
        ),
        "mediaFileIds": (
            "CHỜ_MEDIA_ID"
            if sample["mediaFileIds"] is None
            else []
        ),
    }
    print(
        "Request mẫu câu 37:",
        json.dumps(sample_result, ensure_ascii=False),
    )


if __name__ == "__main__":
    parser = argparse.ArgumentParser(
        description="Chỉ kiểm tra kế hoạch nhập bộ câu hỏi 2026."
    )
    parser.add_argument(
        "--dry-run",
        action="store_true",
        required=True,
    )
    parser.add_argument(
        "--root",
        type=Path,
        default=(
            Path(__file__).resolve().parent.parent
            / "data/question-banks/2026/600"
        ),
    )

    args = parser.parse_args()
    inspect(args.root)