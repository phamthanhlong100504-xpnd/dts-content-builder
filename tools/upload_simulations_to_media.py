#!/usr/bin/env python3
"""Upload Vietnamese Driving License Simulation assets (videos & thumbnails) to dts-media.

Features:
- Chapter-by-chapter upload (e.g. --chapter 1 to upload Chapter 1 only).
- Resumable checkpointing (stores ready media IDs in media-map.json).
- Direct MinIO upload via presigned URL with host rewriting for remote VPS.
- Polling for worker verification (status == READY).
- Automatic handling of both SIMULATION_VIDEO (.mp4) and SIMULATION_THUMBNAIL (.webp).
"""

from __future__ import annotations
import argparse
import base64
import json
import os
from pathlib import Path
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8")
if hasattr(sys.stderr, "reconfigure"):
    sys.stderr.reconfigure(encoding="utf-8")

REPO_ROOT = Path(__file__).resolve().parent.parent
DATA_DIR = REPO_ROOT / "data" / "simulations" / "dts-simulation-120-video-v1"
INVENTORY_FILE = DATA_DIR / "asset-inventory.json"
CHECKPOINT_FILE = DATA_DIR / "media-map.json"

CHAPTER_RANGES = {
    1: (1, 29),
    2: (30, 43),
    3: (44, 63),
    4: (64, 73),
    5: (74, 90),
    6: (91, 120),
}


def make_jwt_token(sub_uuid: str = "0190ce1a-0000-7000-8000-000000000000") -> str:
    """Generate unsigned JWT token accepted by internal media-service."""
    header = base64.urlsafe_b64encode(b'{"alg":"none"}').decode().rstrip("=")
    payload = base64.urlsafe_b64encode(json.dumps({"sub": sub_uuid}).encode()).decode().rstrip("=")
    return f"{header}.{payload}.signature"


def load_checkpoint(file_path: Path) -> dict:
    if file_path.exists():
        try:
            with open(file_path, "r", encoding="utf-8") as f:
                return json.load(f)
        except Exception as e:
            print(f"[WARN] Không đọc được checkpoint cũ ({e}), tạo mới.")
    return {
        "version": "1.0",
        "createdAt": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
        "media": {},  # key: "ch1/001_video" or "ch1/001_thumb" -> media object
    }


def save_checkpoint(file_path: Path, data: dict):
    file_path.parent.mkdir(parents=True, exist_ok=True)
    temp_path = file_path.with_name(file_path.name + ".tmp")
    with open(temp_path, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
    temp_path.replace(file_path)


def upload_single_file(
    media_api: str,
    vps_ip: str,
    token: str,
    file_path: Path,
    mime_type: str,
    target_type: str,
    visibility: str = "PUBLIC",
    timeout_ready: int = 60,
) -> dict:
    """Execute 4-step upload flow and wait until status == READY."""
    file_size = file_path.stat().st_size
    file_name = file_path.name

    # Step 1: Initialize
    init_url = f"{media_api}/api/v1/media/uploads/initialize"
    init_body = json.dumps({
        "fileName": file_name,
        "sizeBytes": file_size,
        "mimeType": mime_type,
        "visibility": visibility,
        "targetType": target_type,
    }).encode("utf-8")

    req = urllib.request.Request(
        init_url,
        data=init_body,
        headers={
            "Content-Type": "application/json",
            "Authorization": f"Bearer {token}",
        },
        method="POST",
    )

    try:
        with urllib.request.urlopen(req, timeout=20) as resp:
            init_res = json.loads(resp.read().decode())
    except urllib.error.HTTPError as e:
        err_msg = e.read().decode(errors="replace")
        raise RuntimeError(f"Initialize failed HTTP {e.code}: {err_msg}") from e

    session_id = init_res["sessionId"]
    media_id = init_res["mediaId"]
    presigned_url = init_res["presignedUrl"]

    # Remap localhost MinIO URL to VPS IP if needed
    if "localhost" in presigned_url or "127.0.0.1" in presigned_url:
        presigned_url = presigned_url.replace("http://localhost:9000", f"http://{vps_ip}:9000")
        presigned_url = presigned_url.replace("http://127.0.0.1:9000", f"http://{vps_ip}:9000")

    # Step 2: Direct PUT to MinIO
    with open(file_path, "rb") as f:
        binary_data = f.read()

    put_req = urllib.request.Request(
        presigned_url,
        data=binary_data,
        headers={
            "Content-Type": mime_type,
            "Content-Length": str(file_size),
        },
        method="PUT",
    )

    try:
        with urllib.request.urlopen(put_req, timeout=120) as resp:
            if resp.status not in (200, 201, 204):
                raise RuntimeError(f"PUT MinIO returned HTTP {resp.status}")
    except urllib.error.HTTPError as e:
        err_msg = e.read().decode(errors="replace")
        raise RuntimeError(f"PUT MinIO failed HTTP {e.code}: {err_msg}") from e

    # Step 3: Confirm upload session
    confirm_url = f"{media_api}/api/v1/media/uploads/{session_id}/confirm"
    confirm_req = urllib.request.Request(
        confirm_url,
        data=b"{}",
        headers={
            "Content-Type": "application/json",
            "Authorization": f"Bearer {token}",
        },
        method="POST",
    )

    try:
        with urllib.request.urlopen(confirm_req, timeout=20) as resp:
            if resp.status not in (200, 201, 202, 204):
                raise RuntimeError(f"Confirm returned HTTP {resp.status}")
    except urllib.error.HTTPError as e:
        err_msg = e.read().decode(errors="replace")
        raise RuntimeError(f"Confirm failed HTTP {e.code}: {err_msg}") from e

    # Step 4: Polling until READY
    poll_url = f"{media_api}/api/v1/media/{media_id}"
    start_time = time.monotonic()

    while time.monotonic() - start_time < timeout_ready:
        get_req = urllib.request.Request(
            poll_url,
            headers={"Authorization": f"Bearer {token}"},
        )
        try:
            with urllib.request.urlopen(get_req, timeout=10) as resp:
                media_info = json.loads(resp.read().decode())
                status = media_info.get("status")
                if status == "READY":
                    return media_info
                elif status == "FAILED":
                    raise RuntimeError(f"Media {media_id} transitioned to FAILED status")
        except urllib.error.HTTPError as e:
            # Service may return 404/500 temporarily while processing
            pass
        time.sleep(2)

    raise TimeoutError(f"Media {media_id} did not become READY within {timeout_ready}s")


def main():
    parser = argparse.ArgumentParser(description="Upload driving license simulation videos/thumbnails to dts-media.")
    parser.add_argument("--chapter", type=int, choices=range(1, 7), help="Chỉ upload theo chương (1-6).")
    parser.add_argument("--all", action="store_true", help="Upload toàn bộ 6 chương (120 tình huống).")
    parser.add_argument("--limit", type=int, default=0, help="Giới hạn số tình huống upload (0 = không giới hạn).")
    parser.add_argument("--media-api", default=os.environ.get("DTS_MEDIA_API", "http://103.20.96.85:8080"), help="Media Service API URL")
    parser.add_argument("--vps-ip", default="103.20.96.85", help="VPS IP for MinIO rewriting")
    parser.add_argument("--visibility", default="PUBLIC", choices=["PUBLIC", "PRIVATE"], help="Upload visibility")
    parser.add_argument("--dry-run", action="store_true", help="Chỉ kiểm tra danh sách file cần upload, không gửi request.")

    args = parser.parse_args()

    if not args.chapter and not args.all:
        parser.print_help()
        print("\n[ERROR] Bạn cần chỉ định --chapter <1-6> (khuyến nghị từng chương) hoặc --all!")
        sys.exit(1)

    if not INVENTORY_FILE.exists():
        print(f"[ERROR] Không tìm thấy {INVENTORY_FILE}. Hãy chạy audit trước.")
        sys.exit(1)

    with open(INVENTORY_FILE, "r", encoding="utf-8") as f:
        inventory_data = json.load(f)

    all_assets = inventory_data.get("assets", [])
    print(f"Loaded {len(all_assets)} assets from inventory.")

    checkpoint_data = load_checkpoint(CHECKPOINT_FILE)
    ready_map = checkpoint_data.get("media", {})

    # Determine scenario range
    if args.chapter:
        start_id, end_id = CHAPTER_RANGES[args.chapter]
        selected_scenarios = list(range(start_id, end_id + 1))
        chapter_label = f"CHƯƠNG {args.chapter} (Tình huống {start_id:03d} -> {end_id:03d})"
    else:
        selected_scenarios = list(range(1, 121))
        chapter_label = "TOÀN BỘ 120 TÌNH HUỐNG (CHƯƠNG 1 -> 6)"

    # Filter assets for selected scenarios
    selected_assets = [a for a in all_assets if a["scenarioId"] in selected_scenarios]
    
    # Group by scenarioId
    scenarios_todo = {}
    for a in selected_assets:
        scenarios_todo.setdefault(a["scenarioId"], {})[a["type"]] = a

    print("=" * 65)
    print(f"MỤC TIÊU UPLOAD: {chapter_label}")
    print(f"Tổng số tình huống cần xử lý: {len(scenarios_todo)}")
    print(f"Đã hoàn thành trước đó (READY): {len([sid for sid in scenarios_todo if f'{sid:03d}_video' in ready_map and f'{sid:03d}_thumb' in ready_map])}")
    print("=" * 65)

    if args.dry_run:
        print("[DRY-RUN] Dừng lại do cờ --dry-run được bật.")
        return

    token = os.environ.get("DTS_MEDIA_TOKEN") or make_jwt_token()
    media_api = args.media_api.rstrip("/")

    processed_count = 0
    success_count = 0

    for sid in sorted(scenarios_todo.keys()):
        if args.limit > 0 and processed_count >= args.limit:
            print(f"\n[INFO] Đã đạt giới hạn --limit {args.limit}. Dừng.")
            break

        scenario_files = scenarios_todo[sid]
        video_asset = scenario_files.get("video")
        thumb_asset = scenario_files.get("thumbnail")

        video_key = f"{sid:03d}_video"
        thumb_key = f"{sid:03d}_thumb"

        # Check if already done
        video_done = video_key in ready_map and ready_map[video_key].get("status") == "READY"
        thumb_done = thumb_key in ready_map and ready_map[thumb_key].get("status") == "READY"

        if video_done and thumb_done:
            print(f"[SKIP] Tình huống {sid:03d}: Cả Video và Thumbnail đã READY trên media-service.")
            continue

        processed_count += 1
        print(f"\n>>> [{processed_count}] Bắt đầu xử lý Tình huống #{sid:03d}:")

        # 1. Upload Video
        if not video_done and video_asset:
            local_path = REPO_ROOT / video_asset["localPath"]
            size_mb = video_asset["sizeBytes"] / 1024 / 1024
            print(f"  [1/2] Đang tải VIDEO: {local_path.name} ({size_mb:.2f} MB)...", end=" ", flush=True)
            try:
                res_video = upload_single_file(
                    media_api=media_api,
                    vps_ip=args.vps_ip,
                    token=token,
                    file_path=local_path,
                    mime_type="video/mp4",
                    target_type="SIMULATION_VIDEO",
                    visibility=args.visibility,
                )
                ready_map[video_key] = {
                    "scenarioId": sid,
                    "type": "video",
                    "mediaId": res_video["mediaId"],
                    "status": res_video["status"],
                    "url": res_video.get("url"),
                    "originalFilename": res_video.get("originalFilename"),
                    "sizeBytes": res_video.get("sizeBytes"),
                    "sha256": video_asset["sha256"],
                }
                checkpoint_data["media"] = ready_map
                save_checkpoint(CHECKPOINT_FILE, checkpoint_data)
                print(f"DONE -> Media ID: {res_video['mediaId']} (READY)")
            except Exception as e:
                print(f"LỖI: {e}")
                print(f"[DỪNG] Gặp lỗi ở tình huống {sid:03d}. Đã lưu checkpoint các file trước đó.")
                sys.exit(1)

        # 2. Upload Thumbnail
        if not thumb_done and thumb_asset:
            local_path = REPO_ROOT / thumb_asset["localPath"]
            size_kb = thumb_asset["sizeBytes"] / 1024
            print(f"  [2/2] Đang tải THUMBNAIL: {local_path.name} ({size_kb:.1f} KB)...", end=" ", flush=True)
            try:
                res_thumb = upload_single_file(
                    media_api=media_api,
                    vps_ip=args.vps_ip,
                    token=token,
                    file_path=local_path,
                    mime_type="image/webp",
                    target_type="SIMULATION_THUMBNAIL",
                    visibility=args.visibility,
                )
                ready_map[thumb_key] = {
                    "scenarioId": sid,
                    "type": "thumbnail",
                    "mediaId": res_thumb["mediaId"],
                    "status": res_thumb["status"],
                    "url": res_thumb.get("url"),
                    "originalFilename": res_thumb.get("originalFilename"),
                    "sizeBytes": res_thumb.get("sizeBytes"),
                    "sha256": thumb_asset["sha256"],
                }
                checkpoint_data["media"] = ready_map
                save_checkpoint(CHECKPOINT_FILE, checkpoint_data)
                print(f"DONE -> Media ID: {res_thumb['mediaId']} (READY)")
            except Exception as e:
                print(f"LỖI: {e}")
                print(f"[DỪNG] Gặp lỗi ở thumbnail tình huống {sid:03d}. Đã lưu checkpoint.")
                sys.exit(1)

        success_count += 1

    print("\n" + "=" * 65)
    print(f"HOÀN THÀNH ĐỢT UPLOAD {chapter_label}!")
    print(f"Số tình huống hoàn thành lượt này: {success_count}")
    print(f"Checkpoint đã ghi tại: {CHECKPOINT_FILE}")
    print("=" * 65)


if __name__ == "__main__":
    main()
