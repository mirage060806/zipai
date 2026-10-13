#!/usr/bin/env python3
"""공식 주거금융 페이지의 정책별 핵심 영역 변경을 감지해 ZipAI에 전달한다."""

import argparse
import hashlib
import html
import json
import os
import re
import sys
import urllib.error
import urllib.request
from datetime import datetime, timezone
from html.parser import HTMLParser
from pathlib import Path

import requests
from rpa_runtime import load_env, start_run, execute
from bs4 import BeautifulSoup
from urllib.parse import urljoin, urlsplit
ACTIVE_HISTORY = None

ROOT = Path(__file__).resolve().parent
PROJECT_ROOT = ROOT.parent
DEFAULT_SOURCES = ROOT / "finance_policy_sources.json"
DEFAULT_OUTPUT = ROOT / "data" / "processed" / "finance_policy_snapshots.json"


def load_dotenv():
    load_env()


class TextExtractor(HTMLParser):
    def __init__(self):
        super().__init__()
        self.parts = []
        self.hidden = 0

    def handle_starttag(self, tag, attrs):
        if tag in {"script", "style", "noscript"}:
            self.hidden += 1

    def handle_endtag(self, tag):
        if tag in {"script", "style", "noscript"} and self.hidden:
            self.hidden -= 1

    def handle_data(self, data):
        if not self.hidden:
            self.parts.append(data)


def page_text(payload, charset):
    extractor = TextExtractor()
    extractor.feed(payload.decode(charset or "utf-8", errors="replace"))
    return re.sub(r"\s+", " ", html.unescape(" ".join(extractor.parts))).strip()


def fetch(source, timeout):
    source_url = source["sourceUrl"].strip()
    response = requests.get(
        source_url,
        timeout=timeout,
        headers={"User-Agent": "ZipAI-Study/1.0 (official-policy-change-checker)"},
    )
    response.raise_for_status()
    if source.get("resolveDetail", urlsplit(source_url).path in ("", "/")):
        soup = BeautifulSoup(response.content, "html.parser")
        keyword = re.sub(r"\s+", "", source["keyword"])
        links = set()
        for link in soup.select("a[href]"):
            if keyword not in re.sub(r"\s+", "", link.get_text(" ", strip=True)):
                continue
            target = urljoin(response.url, link["href"].strip())
            parsed = urlsplit(target)
            if parsed.scheme == "https" and parsed.netloc == urlsplit(response.url).netloc and parsed.path not in ("", "/"):
                links.add(target.split("#", 1)[0])
        if len(links) != 1:
            raise RuntimeError("정책 상세 링크가 없거나 여러 개입니다. finance_policy_sources.json에 상세 URL을 지정하세요.")
        response = requests.get(links.pop(), timeout=timeout, headers={"User-Agent":"ZipAI-Study/1.0"})
        response.raise_for_status()
    content_type = response.headers.get("Content-Type", "").split(";", 1)[0].strip().lower()
    if content_type not in {"text/html", "application/xhtml+xml", ""}:
        raise RuntimeError(f"지원하지 않는 응답 형식: {content_type}")
    charset = response.encoding
    if not charset or charset.lower() == "iso-8859-1":
        charset = response.apparent_encoding or "utf-8"
    soup = BeautifulSoup(response.content.decode(charset, errors="replace"), "html.parser")
    for node in soup.select("script, style, noscript, header, footer, nav"):
        node.decompose()
    selector = source.get("contentSelector")
    content = soup.select_one(selector or "main, article, #content, #contents")
    if selector and content is None:
        raise RuntimeError("정책 본문 영역을 찾지 못했습니다. 본문 구조를 확인하세요.")
    title_selector = source.get("titleSelector")
    title = soup.select_one(title_selector) if title_selector else None
    if title_selector and title is None:
        raise RuntimeError("정책 제목 영역을 찾지 못했습니다. 본문 구조를 확인하세요.")
    body_text = page_text(str(content if content is not None else soup).encode("utf-8"), "utf-8")
    title_text = title.get_text(" ", strip=True) if title is not None else ""
    text = re.sub(r"\s+", " ", (title_text + " " + body_text)).strip()
    if source.get("requiredBodyKeywords"):
        compact_body = re.sub(r"\s+", "", body_text)
        if not all(re.sub(r"\s+", "", word) in compact_body for word in source["requiredBodyKeywords"]):
            raise RuntimeError("정책 본문에 필수 설명 항목이 없습니다. 차단/오류 페이지 또는 본문 변경을 확인하세요.")

    keyword = re.sub(r"\s+", "", source["keyword"])
    compact = re.sub(r"\s+", "", text)
    compact_index = compact.find(keyword)
    if compact_index < 0:
        raise RuntimeError(f"정책 키워드를 찾지 못했습니다: {source['keyword']}")

    # 공백 제거 문자열의 위치를 원문 위치에 근사 변환한다.
    seen = 0
    original_index = 0
    for original_index, char in enumerate(text):
        if not char.isspace():
            if seen >= compact_index:
                break
            seen += 1
    start = max(0, original_index - 1800)
    end = min(len(text), original_index + len(source["keyword"]) + 3600)
    excerpt = text[start:end].strip()
    normalized = re.sub(r"\s+", " ", text if selector else excerpt)
    return {
        "policyName": source["policyName"],
        "sourceName": source["sourceName"],
        "sourceUrl": response.url,
        "sourceHash": hashlib.sha256(normalized.encode("utf-8")).hexdigest(),
        "snapshotExcerpt": normalized[:6000],
        "checkedAt": datetime.now(timezone.utc).isoformat(),
    }


def upload(base_url, token, snapshots, timeout):
    data = json.dumps(snapshots, ensure_ascii=False).encode("utf-8")
    request = urllib.request.Request(
        base_url.rstrip("/") + "/api/finance/policy-updates/import",
        data=data,
        method="POST",
        headers={
            "Content-Type": "application/json; charset=utf-8",
            "X-Finance-Import-Token": token,
        },
    )
    with urllib.request.urlopen(request, timeout=timeout) as response:
        return response.status, json.loads(response.read().decode("utf-8"))


def _main(args):
    load_dotenv()
    base_url = os.getenv("ZIPAI_BASE_URL", "http://localhost:8080")
    token = os.getenv("FINANCE_IMPORT_TOKEN", "")
    timeout = float(os.getenv("FINANCE_CRAWL_TIMEOUT_SECONDS", "20"))
    sources = json.loads(Path(args.sources).read_text(encoding="utf-8-sig"))
    if not isinstance(sources, list) or not sources or any(not all(s.get(k) for k in ("policyName", "sourceName", "sourceUrl", "keyword")) for s in sources):
        raise ValueError("금융 출처의 필수 항목을 확인하세요.")
    print(f"backend={base_url}")
    print(f"sources={len(sources)}")
    print(f"finance_import_token={'SET' if token else 'MISSING'}")
    if args.check:
        if not token:
            print("필수 환경변수 누락: FINANCE_IMPORT_TOKEN", file=sys.stderr)
            return 2
        try:
            request = urllib.request.Request(base_url.rstrip('/') + '/api/collection-runs/check?category=finance',
                                             headers={'X-Finance-Import-Token':token})
            with urllib.request.urlopen(request, timeout=timeout) as response:
                status, result = response.status, json.loads(response.read().decode('utf-8'))
        except urllib.error.HTTPError as error:
            print(f"import_token_status={error.code}", file=sys.stderr)
            return 1
        except urllib.error.URLError as error:
            print(f"backend_check_failed={error}", file=sys.stderr)
            return 1
        print(f"import_token_status={status}")
        print("check_result=" + json.dumps(result, ensure_ascii=False))
        return 0

    if ACTIVE_HISTORY:
        ACTIVE_HISTORY.emit(stage="collect")
    snapshots = []
    failures = []
    for index, source in enumerate(sources, 1):
        try:
            snapshot = fetch(source, timeout)
            snapshots.append(snapshot)
            print(f"source={index}/{len(sources)} policy={source['policyName']} result=OK")
        except (OSError, ValueError, RuntimeError, requests.RequestException) as error:
            failures.append({"policyName": source.get("policyName"), "error": str(error)})
            print(f"source={index}/{len(sources)} policy={source.get('policyName')} result=FAILED error={error}")

    if ACTIVE_HISTORY:
        ACTIVE_HISTORY.partial = bool(failures)
        ACTIVE_HISTORY.emit(collected=len(snapshots), errors=len(failures))
    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps({"items": snapshots, "failures": failures}, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"saved={output}")
    if not snapshots:
        print("수집에 성공한 공식 페이지가 없습니다.", file=sys.stderr)
        return 1
    if args.no_upload:
        print(f"run_summary collected={len(snapshots)} failed={len(failures)} action=save_only")
        return 3 if failures else 0
    if not token:
        print("필수 환경변수 누락: FINANCE_IMPORT_TOKEN", file=sys.stderr)
        return 2
    if ACTIVE_HISTORY:
        ACTIVE_HISTORY.emit(stage="upload")
    try:
        status, result = upload(base_url, token, snapshots, timeout)
    except urllib.error.HTTPError as error:
        print(f"upload_status={error.code} body={error.read().decode('utf-8', errors='replace')}", file=sys.stderr)
        return 1
    except urllib.error.URLError as error:
        print(f"upload_failed={error}", file=sys.stderr)
        return 1
    print(f"upload_status={status}")
    print("import_result=" + json.dumps(result, ensure_ascii=False))
    print(f"run_summary collected={len(snapshots)} failed={len(failures)} result={'PARTIAL' if failures else 'SUCCESS'}")
    return 3 if failures else 0


def main():
    global ACTIVE_HISTORY
    load_env()
    parser = argparse.ArgumentParser()
    parser.add_argument('--sources', default=str(DEFAULT_SOURCES))
    parser.add_argument('--output', default=str(DEFAULT_OUTPUT))
    parser.add_argument('--no-upload', action='store_true')
    parser.add_argument('--check', action='store_true', help='서버 연결과 토큰을 확인합니다. 수집·반영 이력을 생성하지 않습니다.')
    parser.add_argument('--trigger', choices=['manual','scheduled','unknown'], default='unknown')
    args = parser.parse_args()
    if args.check:
        return _main(args)
    ACTIVE_HISTORY = start_run('finance', args.trigger)
    try:
        return execute(ACTIVE_HISTORY, lambda: _main(args))
    finally:
        ACTIVE_HISTORY = None

if __name__ == "__main__":
    raise SystemExit(main())
