
"""Upload 221 DTS traffic sign images through Media Service."""

import argparse
import base64
import hashlib
import json
import os
import re
import time
import uuid
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.parse import urlsplit
from urllib.request import Request, build_opener, ProxyHandler

BANK_VERSION = "dts-traffic-signs-v1"

DEFAULT_ROOT = (
    Path(__file__).resolve().parent.parent
    / "data/question-banks/2026/traffic_signs"
)

HTTP = build_opener(ProxyHandler({}))


def require(condition, message):
    if not condition:
        raise ValueError(message)


def read_json(path):
    return json.loads(path.read_text(encoding="utf-8-sig"))


def save_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_name(path.name + ".tmp")
    temporary.write_text(
        json.dumps(data, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    os.replace(temporary, path)


def validate_source(root):
    signs = read_json(root / "traffic-signs.json")
    manifest = read_json(root / "sign-manifest.json")
    image_dir = root / "images"

    require(isinstance(signs, list), "Signs must be a JSON array.")
    require(len(signs) == 221, "Expected exactly 221 signs.")
    require(isinstance(manifest, dict), "Invalid manifest.")
    require(image_dir.is_dir(), "Missing images directory.")

    expected_ids = set(range(1, 222))

    ids = [sign.get("id") for sign in signs]

    require(
        all(type(value) is int for value in ids)
        and len(set(ids)) == 221
        and set(ids) == expected_ids,
        "Sign IDs must be unique from 1 to 221.",
    )

    require(
        set(manifest) == {str(i) for i in expected_ids},
        "Manifest IDs do not match 1..221.",
    )

    categories = {
        "cam", "nguy_hiem", "hieu_lenh", "chi_dan", "phu"
    }

    entries = []
    names = set()

    for sign in sorted(signs, key=lambda s: s["id"]):
        sign_id = sign["id"]
        filename = sign.get("image")

        require(
            sign.get("category") in categories,
            f"Sign {sign_id}: invalid category.",
        )

        require(
            isinstance(filename, str)
            and re.fullmatch(r"[A-Za-z0-9_-]+\.webp", filename),
            f"Sign {sign_id}: invalid filename.",
        )

        require(
            manifest[str(sign_id)] == filename,
            f"Sign {sign_id}: manifest mismatch.",
        )

        require(
            filename not in names,
            f"Sign {sign_id}: duplicate filename.",
        )

        names.add(filename)

        path = image_dir / filename

        require(
            path.is_file(),
            f"Sign {sign_id}: image not found.",
        )

        content = path.read_bytes()

        require(
            12 <= len(content) <= 104857600
            and content[:4] == b"RIFF"
            and content[8:12] == b"WEBP",
            f"Sign {sign_id}: invalid WebP file.",
        )

        entries.append({
            "id": sign_id,
            "filename": filename,
            "path": path,
            "size": len(content),
            "sha256": hashlib.sha256(content).hexdigest(),
        })

    actual_names = {
        path.name for path in image_dir.glob("*.webp")
        if path.is_file()
    }

    require(
        actual_names == names,
        "Image directory does not match manifest.",
    )

    return entries


def load_checkpoint(path, entries):
    if path.exists():
        data = read_json(path)
    else:
        data = {
            "bankVersion": BANK_VERSION,
            "mediaBySignId": {},
            "pendingBySignId": {},
            "sha256BySignId": {},
        }

    require(
        data.get("bankVersion") == BANK_VERSION,
        "Checkpoint bank version mismatch.",
    )

    completed = data.get("mediaBySignId")
    pending = data.get("pendingBySignId")
    digests = data.get("sha256BySignId")

    require(
        isinstance(completed, dict)
        and isinstance(pending, dict)
        and isinstance(digests, dict),
        "Invalid checkpoint structure.",
    )

    by_id = {str(item["id"]): item for item in entries}

    require(
        set(completed) <= set(by_id)
        and set(pending) <= set(by_id)
        and set(digests) <= set(by_id),
        "Checkpoint contains unknown sign IDs.",
    )

    require(
        not (set(completed) & set(pending)),
        "Checkpoint contains conflicting upload states.",
    )

    for sign_id, media_id in completed.items():
        uuid.UUID(media_id)
        require(
            digests.get(sign_id) == by_id[sign_id]["sha256"],
            f"Sign {sign_id}: source changed after upload.",
        )

    for sign_id, attempt in pending.items():
        require(
            isinstance(attempt, dict),
            f"Sign {sign_id}: invalid pending upload.",
        )
        uuid.UUID(attempt["mediaId"])
        uuid.UUID(attempt["sessionId"])
        require(
            attempt.get("sha256") == by_id[sign_id]["sha256"],
            f"Sign {sign_id}: pending source changed.",
        )

    return data


def media_api(base_url, method, path, token=None, payload=None):
    headers = {"Accept": "application/json"}

    if token:
        headers["Authorization"] = "Bearer " + token

    data = None

    if payload is not None:
        data = json.dumps(payload).encode("utf-8")
        headers["Content-Type"] = "application/json"
    elif method == "POST":
        data = b""

    request = Request(
        base_url + path,
        data=data,
        headers=headers,
        method=method,
    )

    try:
        with HTTP.open(request, timeout=30) as response:
            return json.load(response)
    except HTTPError as error:
        raise RuntimeError(
            f"Media {method} {path}: HTTP {error.code}"
        ) from error
    except URLError as error:
        raise RuntimeError(
            f"Cannot connect to Media: {error.reason}"
        ) from error


def media_detail_if_ready(base_url, media_id, token):
    path = "/api/v1/media/" + media_id

    try:
        detail = media_api(base_url, "GET", path, token)
    except RuntimeError as error:
        # Existing Media API may return HTTP 500 while
        # the file has not reached READY.
        if str(error).endswith("HTTP 500"):
            return None
        raise

    require(isinstance(detail, dict), "Invalid Media detail.")

    status = detail.get("status")

    if status == "READY":
        return detail

    if status in ("UPLOADING", "PENDING", "PROCESSING"):
        return None

    raise RuntimeError(
        f"Media {media_id}: unexpected status {status}"
    )


def verify_ready(detail, entry):
    require(
        detail.get("status") == "READY"
        and detail.get("originalFilename") == entry["filename"]
        and detail.get("sizeBytes") == entry["size"]
        and detail.get("mimeType") == "image/webp",
        f"Sign {entry['id']}: Media differs from source.",
    )


def token_expiration(token):
    try:
        payload = token.split(".")[1]
        payload += "=" * (-len(payload) % 4)
        claims = json.loads(base64.urlsafe_b64decode(payload))
        return int(claims["exp"])
    except (IndexError, KeyError, ValueError, TypeError) as error:
        raise ValueError("Invalid JWT expiration.") from error


def upload_one(base_url, token, entry, checkpoint, checkpoint_path):
    sign_id = str(entry["id"])

    initialized = media_api(
        base_url,
        "POST",
        "/api/v1/media/uploads/initialize",
        token,
        {
            "fileName": entry["filename"],
            "sizeBytes": entry["size"],
            "mimeType": "image/webp",
            "visibility": "PUBLIC",
            "targetType": "TRAFFIC_SIGN",
        },
    )

    media_id = initialized["mediaId"]
    session_id = initialized["sessionId"]
    presigned_url = initialized["presignedUrl"]

    uuid.UUID(media_id)
    uuid.UUID(session_id)

    checkpoint["pendingBySignId"][sign_id] = {
        "mediaId": media_id,
        "sessionId": session_id,
        "filename": entry["filename"],
        "sha256": entry["sha256"],
    }

    save_json(checkpoint_path, checkpoint)

    parsed = urlsplit(presigned_url)

    require(
        parsed.scheme in ("http", "https")
        and bool(parsed.hostname),
        f"Sign {sign_id}: invalid presigned URL.",
    )

    content = entry["path"].read_bytes()

    require(
        hashlib.sha256(content).hexdigest() == entry["sha256"],
        f"Sign {sign_id}: image changed during upload.",
    )

    put_request = Request(
        presigned_url,
        data=content,
        headers={"Content-Type": "image/webp"},
        method="PUT",
    )

    with HTTP.open(put_request, timeout=60) as response:
        require(
            response.status in (200, 204),
            f"Sign {sign_id}: storage PUT failed.",
        )

    confirmed = media_api(
        base_url,
        "POST",
        f"/api/v1/media/uploads/{session_id}/confirm",
        token,
    )

    require(
        confirmed.get("mediaId") == media_id,
        f"Sign {sign_id}: confirmation mismatch.",
    )

    for _ in range(30):
        detail = media_detail_if_ready(base_url, media_id, token)

        if detail is not None:
            verify_ready(detail, entry)
            return media_id

        time.sleep(1)

    raise RuntimeError(
        f"Sign {sign_id}: Media not READY. "
        f"Pending mediaId={media_id}, sessionId={session_id}. "
        "Keep checkpoint and inspect Media/Kafka."
    )


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=DEFAULT_ROOT)
    parser.add_argument("--media-api")
    parser.add_argument("--map-file", type=Path)
    parser.add_argument("--upload", action="store_true")
    parser.add_argument("--limit", type=int, default=1)
    args = parser.parse_args()

    entries = validate_source(args.root.resolve())

    print("BANK_VERSION=" + BANK_VERSION)
    print(f"SIGNS_VALIDATED={len(entries)}")
    print(f"WEBP_VALIDATED={len(entries)}")
    print(f"FIRST_IMAGE={entries[0]['filename']}")
    print(f"LAST_IMAGE={entries[-1]['filename']}")

    if not args.upload:
        print("MODE=DRY_RUN")
        print("MEDIA_REQUESTS=0")
        print("CHECKPOINT_WRITES=0")
        print("STEP_4Z_F1=PASS")
        return

    require(
        1 <= args.limit <= 221,
        "--limit must be between 1 and 221.",
    )

    require(args.map_file is not None, "--map-file is required.")

    base_url = (
        args.media_api
        or os.environ.get("DTS_MEDIA_API_URL", "")
    ).strip().rstrip("/")

    require(
        base_url.startswith(("http://", "https://")),
        "Media API URL is required.",
    )

    token = os.environ.get("DTS_MEDIA_TOKEN", "")
    require(bool(token), "DTS_MEDIA_TOKEN is required.")

    expires_at = token_expiration(token)

    checkpoint_path = args.map_file.resolve()
    checkpoint = load_checkpoint(checkpoint_path, entries)

    completed = checkpoint["mediaBySignId"]
    pending = checkpoint["pendingBySignId"]
    digests = checkpoint["sha256BySignId"]

    uploaded = 0

    for entry in entries:
        sign_id = str(entry["id"])

        if sign_id in completed:
            detail = media_detail_if_ready(
                base_url, completed[sign_id], token
            )
            require(
                detail is not None,
                f"Sign {sign_id}: checkpoint Media not READY.",
            )
            verify_ready(detail, entry)
            continue

        if sign_id in pending:
            media_id = pending[sign_id]["mediaId"]
            detail = media_detail_if_ready(base_url, media_id, token)

            require(
                detail is not None,
                f"Sign {sign_id}: upload still pending. "
                "Inspect Media before retrying.",
            )

            verify_ready(detail, entry)

            completed[sign_id] = media_id
            digests[sign_id] = entry["sha256"]
            del pending[sign_id]

            save_json(checkpoint_path, checkpoint)
            print(f"RECOVERED={sign_id}")
            continue

        if uploaded >= args.limit:
            break

        require(
            expires_at > time.time() + 60,
            "JWT is expiring; obtain a new token.",
        )

        print(f"UPLOADING={sign_id}:{entry['filename']}", flush=True)

        media_id = upload_one(
            base_url, token, entry, checkpoint, checkpoint_path
        )

        completed[sign_id] = media_id
        digests[sign_id] = entry["sha256"]
        del pending[sign_id]

        save_json(checkpoint_path, checkpoint)

        uploaded += 1
        print(f"READY={sign_id}:{media_id}", flush=True)

    print(f"NEW_UPLOADS={uploaded}")
    print(f"TOTAL_READY_IN_MAP={len(completed)}/221")
    print(f"PENDING={len(pending)}")
    print(f"CHECKPOINT={checkpoint_path}")


if __name__ == "__main__":
    main()
