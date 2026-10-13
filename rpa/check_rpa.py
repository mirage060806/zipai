"""수집·DB 변경 없이 폴더, 의존성, 설정의 존재 여부만 점검합니다."""
import importlib.util
import json
import os
from pathlib import Path
from rpa_runtime import ROOT, load_env, db_config

load_env()
failed = False
for name in ['requests','bs4','pypdf','mysql.connector','certifi']:
    try:
        present = importlib.util.find_spec(name) is not None
    except ModuleNotFoundError:
        present = False
    print(name + ': ' + ('설치됨' if present else '설치 필요'))
    failed |= not present
for name in ['PROPERTY_IMPORT_TOKEN','FINANCE_IMPORT_TOKEN']:
    print(name + ': ' + ('설정됨' if os.getenv(name) else '설정 필요 (수집 실행 이력 전송용)'))
    failed |= not bool(os.getenv(name))
print('ZIPAI_BASE_URL: ' + ('설정됨' if os.getenv('ZIPAI_BASE_URL') else '기본 localhost:8080 사용'))
try:
    config = db_config()
    print('DB 설정: 읽기 성공 / 인증서·호스트 검증 ' + ('사용' if config.get('ssl_verify_identity') else '로컬 연결'))
    print('DB 비밀번호: ' + ('설정됨' if config['password'] else '없음'))
except Exception as error:
    print('DB 설정: ' + str(error))
    failed = True
try:
    sources=json.loads((ROOT/'finance_policy_sources.json').read_text(encoding='utf-8-sig'))
    if not sources or any(not all(source.get(key) for key in ('policyName','sourceName','sourceUrl','keyword')) for source in sources):
        raise ValueError('출처 필수 항목 누락')
    print('금융 출처: ' + str(len(sources)) + '개 / 형식 정상')
except Exception:
    print('금융 출처: 파일/JSON 확인 필요')
    failed=True
print('이 점검은 실제 DB 연결이나 서버 인증 성공을 보장하지 않습니다.')
raise SystemExit(1 if failed else 0)
