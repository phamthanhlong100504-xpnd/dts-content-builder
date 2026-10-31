#!/usr/bin/env python3
"""Import simulation scenarios and scoring options directly into Content Builder database.

Idempotent:
- Checks if chapter already exists by title or metadata.
- Checks if question already exists by metadata->>'scenarioNumber'.
- Updates or inserts cleanly.
- Reuses existing tables: chapters, questions, question_options, question_blocks.
"""

from __future__ import annotations
import argparse
from datetime import datetime, timezone
import json
import os
from pathlib import Path
import sys
import uuid
import pg8000.native

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8")
if hasattr(sys.stderr, "reconfigure"):
    sys.stderr.reconfigure(encoding="utf-8")

REPO_ROOT = Path(__file__).resolve().parent.parent
DATA_DIR = REPO_ROOT / "data" / "simulations" / "dts-simulation-120-video-v1"
SCENARIOS_FILE = DATA_DIR / "simulation-scenarios.json"
MEDIA_MAP_FILE = DATA_DIR / "media-map.json"

CHAPTER_INFO = {
    1: {"title": "Chương 1: Giao thông trên đường đô thị", "range": (1, 29)},
    2: {"title": "Chương 2: Giao thông trên đường nông thôn", "range": (30, 43)},
    3: {"title": "Chương 3: Giao thông trên đường cao tốc", "range": (44, 63)},
    4: {"title": "Chương 4: Giao thông trên đường núi", "range": (64, 73)},
    5: {"title": "Chương 5: Giao thông trên đường quốc lộ", "range": (74, 90)},
    6: {"title": "Chương 6: Tình huống va chạm thực tế", "range": (91, 120)},
}

ADMIN_USER_ID = "0190ce1a-0000-7000-8000-000000000000"


def get_db_connection():
    db_host = os.environ.get("DB_HOST", "103.20.96.85")
    db_port = int(os.environ.get("DB_PORT", "5434"))
    db_name = os.environ.get("DB_NAME", "content-builder")
    db_user = os.environ.get("DB_USER", "postgres")
    db_pass = os.environ.get("DB_PASSWORD", "K59Ptl*100504")

    return pg8000.native.Connection(
        user=db_user,
        password=db_pass,
        host=db_host,
        port=db_port,
        database=db_name,
    )


def ensure_chapter(conn, ch_num: int, ch_title: str, dry_run: bool = False) -> str:
    """Find existing chapter by title or create new."""
    rows = conn.run(
        "SELECT id FROM chapters WHERE title = :title AND deleted_at IS NULL LIMIT 1",
        title=ch_title,
    )
    if rows:
        return str(rows[0][0])

    ch_id = str(uuid.uuid4())
    print(f"  [+] Tạo Chapter mới: {ch_title} (ID: {ch_id})")
    if not dry_run:
        meta_json = json.dumps({
            "chapterNumber": ch_num,
            "type": "SIMULATION",
            "scenarioRange": list(CHAPTER_INFO[ch_num]["range"]),
        }, ensure_ascii=False)
        conn.run(
            """
            INSERT INTO chapters (id, title, status, metadata, created_by, created_at, updated_at)
            VALUES (:id, :title, 'PUBLISHED', :metadata, :created_by, :now, :now)
            """,
            id=ch_id,
            title=ch_title,
            metadata=meta_json,
            created_by=ADMIN_USER_ID,
            now=datetime.now(timezone.utc),
        )
    return ch_id


def import_scenario(conn, scenario: dict, media_map: dict, chapter_id: str, dry_run: bool = False):
    sid = scenario["id"]
    scenario_num = scenario["scenario_number"]
    title = scenario["title"]
    tip = scenario.get("tip", "")
    hazard_sign = scenario.get("hazard_sign", "")
    timeline = scenario["scoring_timeline"]
    duration = float(scenario["duration_seconds"])

    # 1. Media IDs from media_map
    video_key = f"{sid:03d}_video"
    thumb_key = f"{sid:03d}_thumb"

    if video_key not in media_map or thumb_key not in media_map:
        raise ValueError(f"Tình huống {sid:03d} chưa có media READY trong media-map.json!")

    video_media_id = media_map[video_key]["mediaId"]
    thumb_media_id = media_map[thumb_key]["mediaId"]
    media_file_ids = json.dumps([video_media_id, thumb_media_id])

    # 2. Check if question already exists
    existing = conn.run(
        """
        SELECT id FROM questions 
        WHERE metadata->>'scenarioNumber' = :snum 
          AND deleted_at IS NULL 
        LIMIT 1
        """,
        snum=str(scenario_num),
    )

    explanations_json = json.dumps({
        "tip": tip,
        "hazardSign": hazard_sign,
    }, ensure_ascii=False)

    metadata_json = json.dumps({
        "scenarioNumber": scenario_num,
        "chapterId": scenario["chapter_id"],
        "chapterName": scenario["chapter_name"],
        "durationSeconds": duration,
        "scoringTimeline": timeline,
        "bankVersion": "2026-simulation-v1",
    }, ensure_ascii=False)

    now = datetime.now(timezone.utc)

    if existing:
        q_id = str(existing[0][0])
        print(f"  [*] Cập nhật Tình huống #{sid:03d} (ID: {q_id})...", end=" ")
        if not dry_run:
            conn.run(
                """
                UPDATE questions 
                SET content = :content,
                    explanations = :explanations,
                    media_file_ids = :media_file_ids,
                    metadata = :metadata,
                    updated_at = :now
                WHERE id = :id
                """,
                id=q_id,
                content=title,
                explanations=explanations_json,
                media_file_ids=media_file_ids,
                metadata=metadata_json,
                now=now,
            )
            # Remove old options before re-creating
            conn.run("DELETE FROM question_options WHERE question_id = :qid", qid=q_id)
    else:
        q_id = str(uuid.uuid4())
        print(f"  [+] Thêm mới Tình huống #{sid:03d} (ID: {q_id})...", end=" ")
        if not dry_run:
            conn.run(
                """
                INSERT INTO questions (
                    id, type, content, explanations, media_file_ids, 
                    status, metadata, created_by, created_at, updated_at
                ) VALUES (
                    :id, 'SIMULATION', :content, :explanations, :media_file_ids,
                    'PUBLISHED', :metadata, :created_by, :now, :now
                )
                """,
                id=q_id,
                content=title,
                explanations=explanations_json,
                media_file_ids=media_file_ids,
                metadata=metadata_json,
                created_by=ADMIN_USER_ID,
                now=now,
            )

    # 3. Insert 5 score bands into question_options
    score_windows = [
        (5, float(timeline["t5_start"]), float(timeline["t4_start"])),
        (4, float(timeline["t4_start"]), float(timeline["t3_start"])),
        (3, float(timeline["t3_start"]), float(timeline["t2_start"])),
        (2, float(timeline["t2_start"]), float(timeline["t1_start"])),
        (1, float(timeline["t1_start"]), float(timeline["t0_end"])),
    ]

    if not dry_run:
        for idx, (score, start_sec, end_sec) in enumerate(score_windows, start=1):
            opt_id = str(uuid.uuid4())
            opt_meta = json.dumps({
                "score": score,
                "startSec": start_sec,
                "endSec": end_sec,
            })
            conn.run(
                """
                INSERT INTO question_options (
                    id, question_id, content, sort_order, is_correct,
                    status, metadata, created_by, created_at, updated_at
                ) VALUES (
                    :id, :qid, :content, :sort_order, TRUE,
                    'PUBLISHED', :metadata, :created_by, :now, :now
                )
                """,
                id=opt_id,
                qid=q_id,
                content=f"Thời điểm đạt {score} điểm ({start_sec:.1f}s - {end_sec:.1f}s)",
                sort_order=idx,
                metadata=opt_meta,
                created_by=ADMIN_USER_ID,
                now=now,
            )

    # 4. Link to question_blocks (in chapter)
    if not dry_run:
        existing_block = conn.run(
            "SELECT id FROM question_blocks WHERE chapter_id = :chid AND question_id = :qid LIMIT 1",
            chid=chapter_id,
            qid=q_id,
        )
        if not existing_block:
            block_id = str(uuid.uuid4())
            conn.run(
                """
                INSERT INTO question_blocks (
                    id, chapter_id, question_id, title, sort_order,
                    status, metadata, created_by, created_at, updated_at
                ) VALUES (
                    :id, :chid, :qid, :title, :sort_order,
                    'PUBLISHED', :metadata, :created_by, :now, :now
                )
                """,
                id=block_id,
                chid=chapter_id,
                qid=q_id,
                title=title,
                sort_order=scenario_num,
                metadata=json.dumps({"scenarioNumber": scenario_num}),
                created_by=ADMIN_USER_ID,
                now=now,
            )

    print("DONE (5 options + 1 block)")


def main():
    parser = argparse.ArgumentParser(description="Import simulation scenarios into Content Builder DB.")
    parser.add_argument("--chapter", type=int, choices=range(1, 7), help="Chỉ import theo chương (1-6).")
    parser.add_argument("--all", action="store_true", help="Import toàn bộ 6 chương (120 tình huống).")
    parser.add_argument("--dry-run", action="store_true", help="Chạy thử không ghi vào DB.")
    args = parser.parse_args()

    if not args.chapter and not args.all:
        parser.print_help()
        print("\n[ERROR] Cần chỉ định --chapter <1-6> hoặc --all!")
        sys.exit(1)

    if not SCENARIOS_FILE.exists() or not MEDIA_MAP_FILE.exists():
        print("[ERROR] Thiếu file dữ liệu scenarios hoặc media-map.json!")
        sys.exit(1)

    with open(SCENARIOS_FILE, "r", encoding="utf-8") as f:
        scenarios = json.load(f)

    with open(MEDIA_MAP_FILE, "r", encoding="utf-8") as f:
        media_map_data = json.load(f)
        media_map = media_map_data.get("media", {})

    print(f"Đã nạp {len(scenarios)} scenarios và {len(media_map)} assets trong media-map.")

    # Target chapters
    target_chapters = [args.chapter] if args.chapter else list(range(1, 7))

    conn = get_db_connection()
    print("Kết nối thành công tới Content Builder Database!\n" + "=" * 65)

    try:
        for ch_num in target_chapters:
            ch_info = CHAPTER_INFO[ch_num]
            ch_start, ch_end = ch_info["range"]
            ch_title = ch_info["title"]
            print(f"\n>>> XỬ LÝ {ch_title} (Tình huống {ch_start:03d} -> {ch_end:03d}):")

            ch_id = ensure_chapter(conn, ch_num, ch_title, dry_run=args.dry_run)

            ch_scenarios = [s for s in scenarios if ch_start <= s["scenario_number"] <= ch_end]
            for s in ch_scenarios:
                import_scenario(conn, s, media_map, ch_id, dry_run=args.dry_run)

        print("\n" + "=" * 65)
        print("HOÀN TẤT IMPORT VÀO DATABASE THÀNH CÔNG!")
        print("=" * 65)

    finally:
        conn.close()


if __name__ == "__main__":
    main()

