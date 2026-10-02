import hashlib
import json
import re
import sys
from pathlib import Path

BANK_VERSION = "dts-2026-600-v1"

LICENSES = (
    "A1", "A", "B1",
    "B", "C1", "C", "D1", "D2", "D",
    "BE", "C1E", "CE", "D1E", "D2E", "DE",
)

A1_CRITICAL = [
    19, 20, 21, 22, 24, 26, 27, 28, 30, 47,
    48, 52, 53, 63, 64, 65, 68, 70, 71, 72,
]

B1_CRITICAL = [
    19, 20, 21, 22, 24, 26, 27, 28, 30, 47,
    48, 52, 53, 63, 64, 65, 68, 70, 71, 72,
    73, 74, 87, 89, 90, 91, 92, 215, 254, 255,
]


def require(condition: bool, message: str) -> None:
    if not condition:
        raise ValueError(message)


def save_json(path: Path, value: object) -> None:
    path.write_text(
        json.dumps(value, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )


def main(root: Path) -> None:
    source = root / "questions.source.json"
    image_dir = root / "images"

    questions = json.loads(source.read_text(encoding="utf-8"))
    require(isinstance(questions, list), "Nguồn phải là một mảng câu hỏi.")
    require(len(questions) == 600, "Nguồn phải có đúng 600 câu.")

    by_id = {question["id"]: question for question in questions}
    require(
        len(by_id) == 600 and set(by_id) == set(range(1, 601)),
        "ID câu hỏi phải duy nhất và đủ từ 1 đến 600.",
    )
    require(
        sum(question["is_critical"] is True for question in questions) == 60,
        "Bộ nguồn phải có đúng 60 câu gắn cờ điểm liệt.",
    )
    require(
        {question["chapter_id"] for question in questions} == set(range(1, 7)),
        "Chương phải thuộc phạm vi 1 đến 6.",
    )

    for question in questions:
        question_id = question["id"]
        option_ids = [option["id"] for option in question["options"]]

        require(
            question["image"] is None,
            f"Câu {question_id}: file nguồn đã có image, cần kiểm tra riêng.",
        )
        require(
            len(option_ids) == len(set(option_ids))
            and question["correct_option"] in option_ids,
            f"Câu {question_id}: ID phương án hoặc đáp án đúng không hợp lệ.",
        )

        assigned = question["applicable_licenses"]
        require(
            bool(assigned)
            and len(assigned) == len(set(assigned))
            and set(assigned) <= set(LICENSES),
            f"Câu {question_id}: danh sách hạng không hợp lệ.",
        )

    require(image_dir.is_dir(), "Không tìm thấy thư mục images.")
    image_files = list(image_dir.iterdir())
    require(len(image_files) == 318, "Bộ ảnh đã kiểm tra có đúng 318 file.")

    for path in image_files:
        require(
            path.is_file() and re.fullmatch(r"[1-9]\d*\.webp", path.name),
            f"Tên ảnh không hợp lệ: {path.name}",
        )

    images = {int(path.stem): path for path in image_files}
    require(
        len(images) == 318 and set(images) <= set(by_id),
        "Ảnh trùng ID hoặc không có câu hỏi tương ứng.",
    )

    manifest = []
    for question_id, path in sorted(images.items()):
        content = path.read_bytes()
        require(
            content[:4] == b"RIFF" and content[8:12] == b"WEBP",
            f"Ảnh {path.name} không phải WebP hợp lệ ở mức định dạng.",
        )
        manifest.append({
            "questionId": question_id,
            "path": f"images/{question_id}.webp",
            "sizeBytes": len(content),
            "sha256": hashlib.sha256(content).hexdigest(),
        })

    critical_by_license = {}

    for license_class in LICENSES:
        applicable = {
            question["id"]
            for question in questions
            if license_class in question["applicable_licenses"]
        }
        globally_flagged = {
            question_id
            for question_id in applicable
            if by_id[question_id]["is_critical"] is True
        }

        if license_class in ("A1", "A"):
            selected = set(A1_CRITICAL)
            expected_questions, expected_critical = 250, 20
        elif license_class == "B1":
            selected = set(B1_CRITICAL)
            expected_questions, expected_critical = 300, 30
        else:
            selected = globally_flagged
            expected_questions, expected_critical = 600, 60

        require(
            len(applicable) == expected_questions,
            f"Hạng {license_class}: sai số câu áp dụng.",
        )
        require(
            selected <= globally_flagged
            and len(selected) == expected_critical,
            f"Hạng {license_class}: sai danh sách câu điểm liệt.",
        )

        critical_by_license[license_class] = sorted(selected)
        print(
            f"{license_class}: {len(applicable)} câu; "
            f"{len(selected)} câu điểm liệt theo hạng"
        )

    prepared_questions = [
        {
            **question,
            "image": (
                f"images/{question['id']}.webp"
                if question["id"] in images
                else None
            ),
        }
        for question in sorted(questions, key=lambda item: item["id"])
    ]

    save_json(root / "questions.json", prepared_questions)
    save_json(
        root / "critical-by-license.json",
        {
            "bankVersion": BANK_VERSION,
            "criticalQuestionIdsByLicense": critical_by_license,
        },
    )
    save_json(
        root / "image-manifest.json",
        {
            "bankVersion": BANK_VERSION,
            "images": manifest,
        },
    )

    print("Đã tạo questions.json, critical-by-license.json, image-manifest.json.")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit(
            "Cách chạy: python tools/prepare_question_bank_2026.py "
            "data/question-banks/2026/600"
        )
    main(Path(sys.argv[1]).resolve())