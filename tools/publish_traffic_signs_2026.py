
import argparse
import hashlib
import json
import os
import re
import uuid
from collections import Counter
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.parse import urlencode, urlsplit
from urllib.request import ProxyHandler, Request, build_opener

BANK_VERSION = "dts-traffic-signs-v1"
EXPECTED_COUNT = 221
ALLOWED_CATEGORIES = {"cam", "nguy_hiem", "hieu_lenh", "chi_dan", "phu"}
ROOT = Path(__file__).resolve().parent.parent / "data/question-banks/2026/traffic_signs"
API_PATH = "/api/v1/content-builder/traffic-signs"
HTTP = build_opener(ProxyHandler({}))
PAYLOAD_FIELDS = (
    "sourceId", "bankVersion", "code", "name", "category",
    "categoryName", "meaning", "quickTip", "sourceImage",
    "mediaFileId", "applicableLicenses",
)


def require(ok, message):
    if not ok:
        raise ValueError(message)


def read_json(path):
    require(path.is_file(), f"Missing file: {path}")
    with path.open("r", encoding="utf-8-sig") as file:
        return json.load(file)


def sha256_json(value):
    canonical = json.dumps(value, sort_keys=True, ensure_ascii=False, separators=(",", ":"))
    return hashlib.sha256(canonical.encode("utf-8")).hexdigest()


def nonempty(value, max_length=None):
    return (isinstance(value, str) and bool(value.strip())
            and (max_length is None or len(value) <= max_length))


def load_payloads(root, media_map_path):
    signs = read_json(root / "traffic-signs.json")
    manifest = read_json(root / "sign-manifest.json")
    media_map = read_json(media_map_path)

    require(isinstance(signs, list) and len(signs) == EXPECTED_COUNT,
            "traffic-signs.json must contain exactly 221 items")
    require(isinstance(manifest, dict), "sign-manifest.json must be an object")
    require(isinstance(media_map, dict), "Invalid Media map")
    require(media_map.get("bankVersion") == BANK_VERSION,
            "Media map bankVersion mismatch")

    expected_keys = {str(i) for i in range(1, EXPECTED_COUNT + 1)}
    require(set(manifest) == expected_keys, "Manifest IDs must be 1..221")
    ids_to_media = media_map.get("mediaBySignId")
    ids_to_hash = media_map.get("sha256BySignId")
    pending = media_map.get("pendingBySignId")
    require(isinstance(ids_to_media, dict) and set(ids_to_media) == expected_keys,
            "Media mapping must have exactly 221 IDs")
    require(isinstance(ids_to_hash, dict) and set(ids_to_hash) == expected_keys,
            "Media SHA256 mapping must have exactly 221 IDs")
    require(isinstance(pending, dict) and not pending,
            "Media map has pending uploads; stop and investigate")

    image_dir = root / "images"
    require(image_dir.is_dir(), "Traffic sign images directory not found")

    payloads = {}
    image_names = set()
    code_keys = set()
    media_uuids = set()
    categories = Counter()

    for sign in signs:
        require(isinstance(sign, dict), "Sign must be a JSON object")
        source_id = sign.get("id")
        require(type(source_id) is int and 1 <= source_id <= EXPECTED_COUNT,
                f"Invalid sign source ID: {source_id!r}")
        require(source_id not in payloads, f"Duplicate sign ID: {source_id}")
        key = str(source_id)

        fields = (
            ("code", 32), ("name", 255), ("category_name", 100),
            ("meaning", None), ("quick_tip", None), ("image", 255),
        )
        for field, max_len in fields:
            require(nonempty(sign.get(field), max_len),
                    f"Sign {source_id}: invalid {field}")

        code = sign["code"]
        code_key = code.strip().casefold()
        require(code_key not in code_keys, f"Duplicate sign code: {code}")
        code_keys.add(code_key)

        category = sign.get("category")
        require(category in ALLOWED_CATEGORIES,
                f"Sign {source_id}: invalid category: {category}")
        categories[category] += 1

        filename = sign["image"]
        require(re.fullmatch(r"[A-Za-z0-9_-]+\.webp", filename) is not None,
                f"Sign {source_id}: invalid image filename")
        require(manifest[key] == filename,
                f"Sign {source_id}: image does not match manifest")
        require(filename not in image_names,
                f"Sign {source_id}: duplicate filename")
        image_names.add(filename)
        image_path = image_dir / filename
        require(image_path.is_file(), f"Sign {source_id}: missing WebP image")
        image_bytes = image_path.read_bytes()
        require(len(image_bytes) >= 12 and image_bytes[:4] == b"RIFF"
                and image_bytes[8:12] == b"WEBP",
                f"Sign {source_id}: image is not WebP")
        require(hashlib.sha256(image_bytes).hexdigest() == ids_to_hash[key],
                f"Sign {source_id}: image SHA256 differs from Media map")

        media_id = str(uuid.UUID(ids_to_media[key]))
        require(media_id not in media_uuids,
                f"Sign {source_id}: duplicate Media UUID")
        media_uuids.add(media_id)

        licenses = sign.get("applicable_licenses")
        require(isinstance(licenses, list) and bool(licenses),
                f"Sign {source_id}: applicable licenses missing")
        require(all(nonempty(value) for value in licenses)
                and len(set(licenses)) == len(licenses),
                f"Sign {source_id}: invalid or duplicate applicable licenses")

        payloads[source_id] = {
            "sourceId": source_id,
            "bankVersion": BANK_VERSION,
            "code": code,
            "name": sign["name"],
            "category": category,
            "categoryName": sign["category_name"],
            "meaning": sign["meaning"],
            "quickTip": sign["quick_tip"],
            "sourceImage": filename,
            "mediaFileId": media_id,
            "applicableLicenses": licenses,
        }

    require(set(payloads) == set(range(1, EXPECTED_COUNT + 1)),
            "Sign IDs must be 1..221")
    actual_webps = {path.name for path in image_dir.glob("*.webp") if path.is_file()}
    require(actual_webps == image_names,
            "Image directory WebP files do not match the 221 signs")

    payload_hash = sha256_json({str(k): payloads[k] for k in sorted(payloads)})
    media_hash = sha256_json({str(k): ids_to_media[str(k)] for k in sorted(payloads)})
    return payloads, categories, payload_hash, media_hash


def normalize_base_url(value, allow_remote):
    url = (value or "").strip().rstrip("/")
    parsed = urlsplit(url)
    require(parsed.scheme in ("http", "https") and parsed.hostname,
            "Missing/invalid Content Builder URL")
    require(not parsed.username and not parsed.password and not parsed.query
            and not parsed.fragment and parsed.path in ("", "/"),
            "Content Builder URL must be only scheme://host:port")
    is_local = parsed.hostname in ("127.0.0.1", "localhost", "::1")
    require(is_local or allow_remote,
            "Non-local Content Builder blocked; use --allow-remote explicitly")
    require(parsed.scheme == "https" or is_local,
            "Remote Content Builder must use HTTPS")
    return url


def api(method, base_url, path, token, payload=None):
    body = (json.dumps(payload, ensure_ascii=False).encode("utf-8")
            if payload is not None else None)
    request = Request(
        base_url + path,
        data=body,
        method=method,
        headers={
            "Authorization": "Bearer " + token,
            "Accept": "application/json",
            "Content-Type": "application/json; charset=utf-8",
        },
    )
    try:
        with HTTP.open(request, timeout=30) as response:
            result = json.load(response)
            require(isinstance(result, dict), f"Invalid API response for {path}")
            return result
    except HTTPError as error:
        info = error.read(400).decode("utf-8", errors="replace")
        raise RuntimeError(f"API {method} {path}: HTTP {error.code}: {info}") from error
    except URLError as error:
        raise RuntimeError(f"Cannot connect to Content Builder: {error.reason}") from error


def validate_response(sign, expected):
    source_id = expected["sourceId"]
    require(sign.get("status") == "PUBLISHED",
            f"Sign {source_id}: API status is not PUBLISHED")
    for field in PAYLOAD_FIELDS:
        actual = sign.get(field)
        wanted = expected[field]
        if field == "mediaFileId" and actual is not None:
            actual = str(uuid.UUID(str(actual)))
        require(actual == wanted,
                f"Sign {source_id}: existing/API field {field} differs from source")
    return str(uuid.UUID(str(sign["id"])))


def get_existing(base_url, token, payloads):
    found = {}
    page_number = 0
    while True:
        query = urlencode({
            "bankVersion": BANK_VERSION, "page": page_number,
            "size": 100, "sort": "sourceId,asc",
        })
        page = api("GET", base_url, f"{API_PATH}?{query}", token)
        items = page.get("content")
        require(isinstance(items, list) and type(page.get("totalElements")) is int
                and type(page.get("last")) is bool,
                "Content Builder pagination response is invalid")
        for item in items:
            require(isinstance(item, dict), "Invalid traffic sign in API response")
            source_id = item.get("sourceId")
            require(type(source_id) is int and source_id in payloads,
                    "Content Builder returned source ID outside 1..221")
            require(source_id not in found, f"Duplicate published sign {source_id}")
            found[source_id] = validate_response(item, payloads[source_id])
        if page["last"]:
            require(len(found) == page["totalElements"],
                    "API page count does not match totalElements")
            return found
        page_number += 1
        require(page_number < 100, "API pagination did not terminate")


def load_checkpoint(path, base_url, payload_hash, media_hash, payloads):
    if not path.is_file():
        return {}
    doc = read_json(path)
    require(isinstance(doc, dict), "Invalid publish checkpoint")
    for field, expected in (
        ("bankVersion", BANK_VERSION),
        ("contentBuilderUrl", base_url),
        ("payloadSha256", payload_hash),
        ("mediaMapSha256", media_hash),
    ):
        require(doc.get(field) == expected,
                f"Checkpoint {field} mismatch; stop for review")
    ids = doc.get("trafficSignUuidBySourceId")
    require(isinstance(ids, dict), "Invalid checkpoint UUID map")
    result = {}
    for source_id, cb_id in ids.items():
        require(source_id.isdecimal() and int(source_id) in payloads,
                f"Unknown source ID in checkpoint: {source_id}")
        result[int(source_id)] = str(uuid.UUID(str(cb_id)))
    require(len(set(result.values())) == len(result),
            "Duplicate Content Builder UUID in checkpoint")
    return result


def write_checkpoint(path, base_url, payload_hash, media_hash, ids):
    document = {
        "bankVersion": BANK_VERSION,
        "contentBuilderUrl": base_url,
        "payloadSha256": payload_hash,
        "mediaMapSha256": media_hash,
        "trafficSignUuidBySourceId": {
            str(source_id): ids[source_id] for source_id in sorted(ids)
        },
    }
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_name(path.name + ".tmp")
    temporary.write_text(json.dumps(document, ensure_ascii=False, indent=2) + "\n",
                         encoding="utf-8")
    os.replace(temporary, path)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=ROOT)
    parser.add_argument("--media-map", type=Path, required=True)
    parser.add_argument("--checkpoint", type=Path)
    parser.add_argument("--base-url", default=None)
    parser.add_argument("--check-api", action="store_true",
                        help="Read and verify published signs; never POST or write files")
    parser.add_argument("--apply", action="store_true",
                        help="Allow POST published signs; saves an atomic checkpoint")
    parser.add_argument("--limit", type=int, default=1,
                        help="Maximum new signs to publish in this run (default: 1)")
    parser.add_argument("--allow-remote", action="store_true",
                        help="Explicitly allow non-local HTTPS Content Builder URL")
    args = parser.parse_args()
    require(not (args.check_api and args.apply), "Choose --check-api OR --apply")
    require(1 <= args.limit <= EXPECTED_COUNT, "--limit must be 1..221")

    payloads, categories, payload_hash, media_hash = load_payloads(
        args.root.resolve(), args.media_map.resolve())
    print("BANK_VERSION=" + BANK_VERSION)
    print("SOURCE_VALIDATED=221")
    print("MEDIA_UUIDS_VALIDATED=221")
    print("IMAGE_SHA256_VALIDATED=221")
    print("CATEGORIES=" + ",".join(f"{key}:{categories[key]}" for key in sorted(categories)))
    print("FIRST_SIGN=1:" + payloads[1]["code"])
    print("LAST_SIGN=221:" + payloads[221]["code"])
    print("FIRST_PUBLISH_PAYLOAD=" + json.dumps(payloads[1], ensure_ascii=False))

    if not (args.check_api or args.apply):
        print("MODE=OFFLINE_DRY_RUN")
        print("HTTP_REQUESTS=0")
        print("CHECKPOINT_WRITES=0")
        print("STEP_4Z_G4A=PASS")
        return

    token = os.environ.get("DTS_IMPORT_TOKEN", "").strip()
    require(bool(token), "Missing DTS_IMPORT_TOKEN")
    base_url = normalize_base_url(
        args.base_url or os.environ.get("DTS_CONTENT_BUILDER_URL", ""),
        args.allow_remote)
    checkpoint = (args.checkpoint.resolve() if args.checkpoint
                  else args.media_map.resolve().parent / "content-builder-traffic-sign-map.json")
    saved = load_checkpoint(checkpoint, base_url, payload_hash, media_hash, payloads)
    found = get_existing(base_url, token, payloads)

    for source_id, cb_id in saved.items():
        require(found.get(source_id) == cb_id,
                f"Checkpoint sign {source_id} not found/mismatched in Content Builder")

    print(f"CONTENT_BUILDER_URL={base_url}")
    print(f"EXISTING_MATCHED={len(found)}")
    print(f"MISSING={EXPECTED_COUNT - len(found)}")
    print(f"CHECKPOINT_EXISTING={len(saved)}")

    if args.check_api:
        print("MODE=API_READ_ONLY")
        print("POST_REQUESTS=0")
        print("CHECKPOINT_WRITES=0")
        print("STEP_4Z_G4B=PASS")
        return

    ids = dict(found)
    write_checkpoint(checkpoint, base_url, payload_hash, media_hash, ids)
    created = 0
    for source_id in sorted(payloads):
        if source_id in ids:
            continue
        if created >= args.limit:
            break
        sign = api("POST", base_url, API_PATH + "/published",
                   token, payloads[source_id])
        cb_id = validate_response(sign, payloads[source_id])
        require(cb_id not in ids.values(),
                f"Sign {source_id}: duplicate returned Content Builder UUID")
        ids[source_id] = cb_id
        write_checkpoint(checkpoint, base_url, payload_hash, media_hash, ids)
        created += 1
        print(f"PUBLISHED={source_id}:{cb_id}", flush=True)

    print(f"NEW_PUBLISHED={created}")
    print(f"TOTAL_PUBLISHED={len(ids)}/221")
    print(f"CHECKPOINT={checkpoint}")
    print("STEP_4Z_G4C=PASS")


if __name__ == "__main__":
    main()
