
import argparse
import hashlib
import json
import os
import uuid
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.parse import urlencode, urlsplit
from urllib.request import ProxyHandler, Request, build_opener

DEFAULT_BANK_VERSION = "dts-2026-600-v1"
EXPECTED_COUNT = 12
ROOT = Path(__file__).resolve().parent.parent / "data/question-banks/2026/memory_tips"
API_PATH = "/api/v1/content-builder/memory-tips"
HTTP = build_opener(ProxyHandler({}))
PAYLOAD_FIELDS = (
    "sourceId", "bankVersion", "sortOrder", "title", "quickRule", "explanation",
)
SOURCE_FIELDS = {"id", "order", "title", "quick_rule", "explanation"}


def require(condition, message):
    if not condition:
        raise ValueError(message)


def read_json(path):
    require(path.is_file(), f"Missing file: {path}")
    with path.open("r", encoding="utf-8-sig") as handle:
        return json.load(handle)


def json_sha256(value):
    canonical = json.dumps(value, sort_keys=True, ensure_ascii=False, separators=(",", ":"))
    return hashlib.sha256(canonical.encode("utf-8")).hexdigest()


def nonblank(value, maximum=None):
    return (isinstance(value, str) and bool(value.strip())
            and (maximum is None or len(value) <= maximum))


def load_payloads(source_root, bank_version):
    require(nonblank(bank_version, 64), "Invalid bank version (max 64 characters)")
    raw = read_json(source_root / "memory-tips.json")
    require(isinstance(raw, list) and len(raw) == EXPECTED_COUNT,
            "memory-tips.json must contain exactly 12 objects")

    payloads = {}
    orders = set()
    titles = set()
    for tip in raw:
        require(isinstance(tip, dict) and set(tip) == SOURCE_FIELDS,
                "Each tip must contain exactly id, order, title, quick_rule, explanation")
        source_id = tip["id"]
        order = tip["order"]
        require(type(source_id) is int and 1 <= source_id <= EXPECTED_COUNT,
                f"Invalid source ID {source_id!r}")
        require(source_id not in payloads, f"Duplicate source ID {source_id}")
        require(type(order) is int and 1 <= order <= EXPECTED_COUNT,
                f"Tip {source_id}: invalid sort order {order!r}")
        require(order not in orders, f"Duplicate sort order {order}")
        orders.add(order)
        for field, maximum in (("title", 255), ("quick_rule", None),
                               ("explanation", None)):
            require(nonblank(tip[field], maximum),
                    f"Tip {source_id}: invalid {field}")
        title_key = tip["title"].strip().casefold()
        require(title_key not in titles, f"Duplicate memory tip title: {tip['title']}")
        titles.add(title_key)
        payloads[source_id] = {
            "sourceId": source_id,
            "bankVersion": bank_version,
            "sortOrder": order,
            "title": tip["title"],
            "quickRule": tip["quick_rule"],
            "explanation": tip["explanation"],
        }

    expected = set(range(1, EXPECTED_COUNT + 1))
    require(set(payloads) == expected, "Source IDs must cover 1..12")
    require(orders == expected, "Sort orders must cover 1..12")
    # Preserve the source ordering as a contractual display ordering.
    require(all(payloads[i]["sortOrder"] == i for i in expected),
            "Unexpected sort order: source IDs and display positions must match")
    payload_hash = json_sha256({str(i): payloads[i] for i in sorted(payloads)})
    source_hash = json_sha256(raw)
    return payloads, payload_hash, source_hash


def normalize_base_url(value, allow_remote):
    base_url = (value or "").strip().rstrip("/")
    parsed = urlsplit(base_url)
    require(parsed.scheme in ("http", "https") and parsed.hostname,
            "Missing or invalid Content Builder URL")
    require(not parsed.username and not parsed.password and not parsed.query
            and not parsed.fragment and parsed.path in ("", "/"),
            "Content Builder URL must be scheme://host:port only")
    require(parsed.port is not None, "Content Builder URL must include a port")
    is_local = parsed.hostname in ("127.0.0.1", "localhost", "::1")
    require(is_local or allow_remote,
            "Remote Content Builder is blocked; --allow-remote is required")
    require(parsed.scheme == "https" or is_local,
            "Non-local Content Builder must use HTTPS")
    return base_url


def api(method, base_url, path, token, body=None):
    data = None if body is None else json.dumps(body, ensure_ascii=False).encode("utf-8")
    request = Request(
        base_url + path, data=data, method=method,
        headers={
            "Authorization": "Bearer " + token,
            "Accept": "application/json",
            "Content-Type": "application/json; charset=utf-8",
        },
    )
    try:
        with HTTP.open(request, timeout=30) as response:
            # json.load decodes UTF-8 as JSON regardless of a missing HTTP charset.
            result = json.load(response)
            require(isinstance(result, dict), f"Invalid JSON object: {path}")
            return result
    except HTTPError as error:
        detail = error.read(300).decode("utf-8", errors="replace")
        raise RuntimeError(f"API {method} {path}: HTTP {error.code}: {detail}") from error
    except URLError as error:
        raise RuntimeError(f"Cannot connect to Content Builder: {error.reason}") from error


def validate_api_tip(record, payload):
    source_id = payload["sourceId"]
    require(isinstance(record, dict), f"Tip {source_id}: malformed API item")
    require(record.get("status") == "PUBLISHED",
            f"Tip {source_id}: API status must be PUBLISHED")
    for field in PAYLOAD_FIELDS:
        require(record.get(field) == payload[field],
                f"Tip {source_id}: API field {field} differs from source")
    require(nonblank(record.get("publishedAt")),
            f"Tip {source_id}: publishedAt is missing")
    try:
        return str(uuid.UUID(str(record["id"])))
    except (KeyError, TypeError, ValueError, AttributeError) as error:
        raise ValueError(f"Tip {source_id}: invalid Content Builder UUID") from error


def get_existing(base_url, token, payloads, bank_version):
    existing = {}
    page_number = 0
    while True:
        query = urlencode({
            "bankVersion": bank_version,
            "page": page_number,
            "size": 100,
            "sort": "sortOrder,asc",
        })
        result = api("GET", base_url, f"{API_PATH}?{query}", token)
        items = result.get("content")
        require(isinstance(items, list) and type(result.get("totalElements")) is int
                and type(result.get("last")) is bool,
                "Memory tips list API has an unexpected pagination structure")
        for record in items:
            require(isinstance(record, dict), "Memory tip API list returned non-object")
            source_id = record.get("sourceId")
            require(type(source_id) is int and source_id in payloads,
                    f"Unexpected memory tip source ID: {source_id}")
            require(source_id not in existing,
                    f"Duplicate API published source ID: {source_id}")
            existing[source_id] = validate_api_tip(record, payloads[source_id])
        if result["last"]:
            require(len(existing) == result["totalElements"],
                    "API totalElements differs from retrieved item count")
            return existing
        page_number += 1
        require(page_number < 20, "Memory tip API pagination did not terminate")


def load_checkpoint(path, base_url, bank_version, payload_hash, source_hash, payloads):
    if not path.is_file():
        return {}
    document = read_json(path)
    require(isinstance(document, dict), "Invalid memory tips checkpoint JSON")
    for field, expected in (
        ("bankVersion", bank_version),
        ("contentBuilderUrl", base_url),
        ("payloadSha256", payload_hash),
        ("sourceSha256", source_hash),
    ):
        require(document.get(field) == expected,
                f"Checkpoint {field} mismatch: stop and investigate")
    mapping = document.get("memoryTipUuidBySourceId")
    require(isinstance(mapping, dict), "Invalid memoryTipUuidBySourceId mapping")
    ids = {}
    for source_id, cb_uuid in mapping.items():
        require(isinstance(source_id, str) and source_id.isdecimal()
                and str(int(source_id)) == source_id and int(source_id) in payloads,
                f"Unknown source ID in memory tips checkpoint: {source_id!r}")
        ids[int(source_id)] = str(uuid.UUID(str(cb_uuid)))
    require(len(set(ids.values())) == len(ids),
            "Memory tips checkpoint contains duplicate Content Builder UUIDs")
    return ids


def save_checkpoint(path, base_url, bank_version, payload_hash, source_hash, ids):
    doc = {
        "bankVersion": bank_version,
        "contentBuilderUrl": base_url,
        "payloadSha256": payload_hash,
        "sourceSha256": source_hash,
        "memoryTipUuidBySourceId": {str(i): ids[i] for i in sorted(ids)},
    }
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_name(path.name + ".tmp")
    with temporary.open("w", encoding="utf-8", newline="\n") as handle:
        json.dump(doc, handle, ensure_ascii=False, indent=2)
        handle.write("\n")
        handle.flush()
        os.fsync(handle.fileno())
    os.replace(temporary, path)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=ROOT,
                        help="Directory containing memory-tips.json")
    parser.add_argument("--bank-version", default=DEFAULT_BANK_VERSION,
                        help="Version for the 12 tips (default: dts-2026-600-v1)")
    parser.add_argument("--checkpoint", type=Path,
                        help="Explicit checkpoint path; required for --apply")
    parser.add_argument("--base-url",
                        help="Content Builder base URL, such as http://127.0.0.1:8082")
    parser.add_argument("--check-api", action="store_true",
                        help="Read-only API comparison; no POST or checkpoint writes")
    parser.add_argument("--apply", action="store_true",
                        help="POST missing published tips (requires token and checkpoint)")
    parser.add_argument("--limit", type=int, default=1,
                        help="Maximum new tips per apply (default: 1)")
    parser.add_argument("--allow-remote", action="store_true",
                        help="Explicit opt-in for non-local HTTPS Content Builder")
    args = parser.parse_args()

    require(not (args.check_api and args.apply), "Choose --check-api OR --apply")
    require(1 <= args.limit <= EXPECTED_COUNT, "--limit must be 1..12")
    payloads, payload_hash, source_hash = load_payloads(
        args.root.resolve(), args.bank_version)

    print("BANK_VERSION=" + args.bank_version)
    print("SOURCE_VALIDATED=12")
    print("SORT_ORDER_VALIDATED=1..12")
    print("FIRST_MEMORY_TIP=1:" + payloads[1]["title"])
    print("LAST_MEMORY_TIP=12:" + payloads[12]["title"])
    print("FIRST_PUBLISH_PAYLOAD=" + json.dumps(payloads[1], ensure_ascii=False))

    if not (args.check_api or args.apply):
        print("MODE=OFFLINE_DRY_RUN")
        print("HTTP_REQUESTS=0")
        print("CHECKPOINT_WRITES=0")
        print("STEP_4Z_H2=PASS")
        return

    token = os.environ.get("DTS_IMPORT_TOKEN", "").strip()
    require(bool(token), "Missing DTS_IMPORT_TOKEN")
    base_url = normalize_base_url(
        args.base_url or os.environ.get("DTS_CONTENT_BUILDER_URL", ""),
        args.allow_remote,
    )
    require(not args.apply or args.checkpoint is not None,
            "--apply requires an explicit --checkpoint path")
    checkpoint = args.checkpoint.resolve() if args.checkpoint else None
    saved = (load_checkpoint(checkpoint, base_url, args.bank_version,
                             payload_hash, source_hash, payloads)
             if checkpoint is not None else {})
    found = get_existing(base_url, token, payloads, args.bank_version)
    for source_id, cb_uuid in saved.items():
        require(found.get(source_id) == cb_uuid,
                f"Checkpoint tip {source_id} does not match published API")

    print("CONTENT_BUILDER_URL=" + base_url)
    print(f"EXISTING_MATCHED={len(found)}")
    print(f"MISSING={EXPECTED_COUNT - len(found)}")
    print(f"CHECKPOINT_EXISTING={len(saved)}")

    if args.check_api:
        print("MODE=API_READ_ONLY")
        print("POST_REQUESTS=0")
        print("CHECKPOINT_WRITES=0")
        print("STEP_4Z_H3=PASS")
        return

    ids = dict(found)
    save_checkpoint(checkpoint, base_url, args.bank_version,
                    payload_hash, source_hash, ids)
    newly_created = 0
    for source_id in sorted(payloads):
        if source_id in ids:
            continue
        if newly_created >= args.limit:
            break
        response = api("POST", base_url, API_PATH + "/published",
                       token, payloads[source_id])
        cb_uuid = validate_api_tip(response, payloads[source_id])
        require(cb_uuid not in ids.values(),
                f"Duplicate Content Builder UUID returned for tip {source_id}")
        ids[source_id] = cb_uuid
        save_checkpoint(checkpoint, base_url, args.bank_version,
                        payload_hash, source_hash, ids)
        newly_created += 1
        print(f"PUBLISHED={source_id}:{cb_uuid}", flush=True)

    print(f"NEW_PUBLISHED={newly_created}")
    print(f"TOTAL_PUBLISHED={len(ids)}/{EXPECTED_COUNT}")
    print(f"CHECKPOINT={checkpoint}")
    print("STEP_4Z_H4=PASS")


if __name__ == "__main__":
    try:
        main()
    except (ValueError, OSError, json.JSONDecodeError, RuntimeError) as error:
        print(f"ERROR={error}", file=__import__("sys").stderr)
        raise SystemExit(1) from None
