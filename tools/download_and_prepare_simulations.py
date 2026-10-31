#!/usr/bin/env python3
"""DTS Simulation Data Download and Preparation Tool.
Downloads 120 videos from lucasdzio/Oto_oanh-videos, extracts answers from kamedev02/ontapmophong,
organizes them into chapter folders (ch1..ch6), and generates WebP thumbnails.
"""
from __future__ import annotations

import concurrent.futures
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import time
import urllib.request

try:
    import imageio_ffmpeg
    DEFAULT_FFMPEG = imageio_ffmpeg.get_ffmpeg_exe()
except Exception:
    DEFAULT_FFMPEG = shutil.which("ffmpeg") or "ffmpeg"

try:
    from PIL import Image
    HAS_PIL = True
except ImportError:
    HAS_PIL = False

CHAPTER_RANGES = {
    1: (1, 29),
    2: (30, 43),
    3: (44, 63),
    4: (64, 73),
    5: (74, 90),
    6: (91, 120)
}

def get_chapter(scenario_id: int) -> int:
    for ch, (start, end) in CHAPTER_RANGES.items():
        if start <= scenario_id <= end:
            return ch
    raise ValueError(f"Scenario ID ngoài khoảng 1..120: {scenario_id}")

def download_url(url: str, dest: Path, max_retries: int = 3) -> bool:
    dest.parent.mkdir(parents=True, exist_ok=True)
    if dest.exists() and dest.stat().st_size > 1000:
        return True
    
    tmp_path = dest.with_name(dest.name + ".tmp")
    req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64)"})
    
    for attempt in range(1, max_retries + 1):
        try:
            with urllib.request.urlopen(req, timeout=45) as resp, open(tmp_path, "wb") as out:
                shutil.copyfileobj(resp, out)
            if tmp_path.stat().st_size > 1000:
                os.replace(tmp_path, dest)
                return True
            else:
                if tmp_path.exists():
                    tmp_path.unlink()
        except Exception as e:
            if attempt == max_retries:
                print(f"[ERROR] Thất bại tải {url}: {e}", file=sys.stderr)
                if tmp_path.exists():
                    tmp_path.unlink()
                return False
            time.sleep(1.5 * attempt)
    return False

def make_thumbnail(ffmpeg_exe: str, video_path: Path, thumb_path: Path, timestamp: float = 2.0) -> bool:
    thumb_path.parent.mkdir(parents=True, exist_ok=True)
    if thumb_path.exists() and thumb_path.stat().st_size > 500:
        with open(thumb_path, "rb") as f:
            h = f.read(12)
            if h[:4] == b"RIFF" and h[8:12] == b"WEBP":
                return True

    # Thử xuất trực tiếp sang WebP qua ffmpeg
    cmd = [
        ffmpeg_exe, "-y",
        "-ss", str(timestamp),
        "-i", str(video_path),
        "-vframes", "1",
        "-c:v", "libwebp",
        "-quality", "85",
        str(thumb_path)
    ]
    p = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    if p.returncode == 0 and thumb_path.exists() and thumb_path.stat().st_size > 500:
        with open(thumb_path, "rb") as f:
            h = f.read(12)
            if h[:4] == b"RIFF" and h[8:12] == b"WEBP":
                return True

    # Fallback: trích xuất PNG rồi dùng Pillow lưu sang WebP
    if HAS_PIL:
        temp_png = thumb_path.with_suffix(".temp.png")
        cmd_png = [
            ffmpeg_exe, "-y",
            "-ss", str(timestamp),
            "-i", str(video_path),
            "-vframes", "1",
            str(temp_png)
        ]
        p_png = subprocess.run(cmd_png, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        if p_png.returncode == 0 and temp_png.exists():
            with Image.open(temp_png) as img:
                img.save(thumb_path, "WEBP", quality=85)
            temp_png.unlink()
            return True

    return False

def download_video_task(scenario_id: int, base_dir: Path, ffmpeg_exe: str) -> dict:
    ch = get_chapter(scenario_id)
    video_name = f"{scenario_id:03d}.mp4"
    thumb_name = f"{scenario_id:03d}_thumb.webp"
    
    ch_dir = base_dir / "simulations" / f"ch{ch}"
    video_path = ch_dir / video_name
    thumb_path = ch_dir / thumb_name
    
    url = f"https://github.com/lucasdzio/Oto_oanh-videos/releases/download/v1/th{scenario_id:03d}.mp4"
    
    downloaded = download_url(url, video_path)
    if not downloaded:
        return {"id": scenario_id, "status": "FAIL_DOWNLOAD", "video": str(video_path)}
    
    thumb_ok = make_thumbnail(ffmpeg_exe, video_path, thumb_path, timestamp=2.0)
    if not thumb_ok:
        return {"id": scenario_id, "status": "FAIL_THUMBNAIL", "video": str(video_path)}
        
    return {
        "id": scenario_id,
        "chapter": ch,
        "status": "OK",
        "video_path": str(video_path),
        "video_size": video_path.stat().st_size,
        "thumb_path": str(thumb_path),
        "thumb_size": thumb_path.stat().st_size
    }

def main():
    target_dir_str = sys.argv[1] if len(sys.argv) > 1 else r"C:\Users\LongBeo\Desktop\dts-simulation-assets"
    target_dir = Path(target_dir_str).resolve()
    print(f"=== BẮT ĐẦU CHUẨN BỊ TÀI NGUYÊN MÔ PHỎNG 120 TÌNH HUỐNG ===")
    print(f"Thư mục lưu trữ: {target_dir}")
    print(f"Sử dụng FFmpeg: {DEFAULT_FFMPEG}")
    
    target_dir.mkdir(parents=True, exist_ok=True)
    
    # 1. Tải bộ dữ liệu đáp án từ kamedev02/ontapmophong
    print("\n--- Bước 1: Tải file đáp án và kịch bản từ kamedev02/ontapmophong ---")
    answers_url = "https://raw.githubusercontent.com/kamedev02/ontapmophong/main/assets/data.json"
    answers_dest = target_dir / "simulation_answers_kamedev.json"
    if download_url(answers_url, answers_dest):
        print(f"[OK] Đã tải đáp án gốc: {answers_dest} ({answers_dest.stat().st_size} bytes)")
    else:
        print("[WARN] Không tải được data.json từ kamedev02")

    # 2. Đồng bộ file scenarios và exam-sets chuẩn của DTS
    repo_scenarios = Path("data/simulations/dts-simulation-120-video-v1/simulation-scenarios.json")
    repo_sets = Path("data/simulations/dts-simulation-120-video-v1/simulation-exam-sets.json")
    if repo_scenarios.exists():
        shutil.copyfile(repo_scenarios, target_dir / "simulation-scenarios.json")
        print(f"[OK] Đã sao chép chuẩn hóa DTS: {target_dir / 'simulation-scenarios.json'}")
    if repo_sets.exists():
        shutil.copyfile(repo_sets, target_dir / "simulation-exam-sets.json")
        print(f"[OK] Đã sao chép 15 bộ đề DTS: {target_dir / 'simulation-exam-sets.json'}")

    # 3. Tải 120 video và tạo 120 thumbnail song song (6 luồng)
    print("\n--- Bước 2: Tải 120 video và tạo 120 thumbnail WebP (chia 6 chương) ---")
    start_time = time.time()
    results = []
    
    with concurrent.futures.ThreadPoolExecutor(max_workers=6) as executor:
        futures = {
            executor.submit(download_video_task, i, target_dir, DEFAULT_FFMPEG): i
            for i in range(1, 121)
        }
        for future in concurrent.futures.as_completed(futures):
            res = future.result()
            results.append(res)
            if res["status"] == "OK":
                print(f"  [DONE {len(results):03d}/120] Câu {res['id']:03d} -> Chương {res['chapter']} | Video: {res['video_size']/1024/1024:.2f}MB | Thumb: {res['thumb_size']/1024:.1f}KB")
            else:
                print(f"  [ERROR] Câu {res['id']:03d}: {res['status']}")

    elapsed = time.time() - start_time
    ok_count = sum(1 for r in results if r["status"] == "OK")
    print(f"\n--- KẾT QUẢ TẢI & CHUẨN BỊ ---")
    print(f"Tổng số tình huống hoàn tất: {ok_count}/120")
    print(f"Thời gian thực hiện: {elapsed:.1f} giây")

    # 4. Kiểm tra tổng kết cấu trúc thư mục
    print("\nThống kê các chương tại máy:")
    sim_dir = target_dir / "simulations"
    for ch in range(1, 7):
        ch_path = sim_dir / f"ch{ch}"
        mp4s = list(ch_path.glob("*.mp4"))
        webps = list(ch_path.glob("*.webp"))
        print(f"  Chương {ch} ({ch_path.name}): {len(mp4s)} videos, {len(webps)} thumbnails")

if __name__ == "__main__":
    main()
