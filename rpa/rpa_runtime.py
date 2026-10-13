"""프로젝트 환경 설정, DB 연결, 항목별 실행 기록을 공통으로 처리합니다."""
import os
import ssl
from pathlib import Path
from urllib.parse import urlsplit, parse_qs, unquote
from collection_run import CollectionRun

ROOT = Path(__file__).resolve().parent


def load_env():
    # 프로세스 환경 > 프로젝트 .env > rpa/.env 순서입니다.
    for path in (ROOT.parent / '.env', ROOT / '.env'):
        if not path.is_file():
            continue
        for raw in path.read_text(encoding='utf-8-sig').splitlines():
            line = raw.strip()
            if line.startswith('export '):
                line = line[7:].strip()
            if not line or line.startswith('#') or '=' not in line:
                continue
            key, value = line.split('=', 1)
            key, value = key.strip(), value.strip()
            if len(value) >= 2 and value[0] == value[-1] and value[0] in "\"'":
                value = value[1:-1]
            if key:
                os.environ.setdefault(key, value)


def db_config():
    load_env()
    url = os.getenv('DB_URL', '')
    parsed = urlsplit(url.removeprefix('jdbc:')) if url else None
    if parsed and parsed.scheme != 'mysql':
        raise ValueError('DB_URL은 jdbc:mysql 주소여야 합니다.')
    def setting(key, fallback):
        return os.getenv(key) or fallback
    config = dict(host=setting('ZIPAI_DB_HOST', parsed.hostname if parsed else 'localhost'),
                  port=int(setting('ZIPAI_DB_PORT', parsed.port or 3306 if parsed else 3306)),
                  database=setting('ZIPAI_DB_NAME', unquote(parsed.path.lstrip('/')) if parsed else 'zipai'),
                  user=setting('ZIPAI_DB_USER', os.getenv('DB_USERNAME', 'zipai')),
                  password=setting('ZIPAI_DB_PASSWORD', os.getenv('DB_PASSWORD', '')),
                  charset='utf8mb4', autocommit=False)
    query = parse_qs(parsed.query) if parsed else {}
    mode = setting('ZIPAI_DB_SSL_MODE', query.get('sslMode', [''])[0]).upper()
    remote = config['host'] not in ('localhost', '127.0.0.1', '::1')
    verify = remote or mode in ('VERIFY_IDENTITY', 'VERIFY_CA')
    if verify:
        ca = os.getenv('ZIPAI_DB_SSL_CA')
        if ca:
            p = Path(ca)
            if not p.is_absolute():
                p = ROOT.parent / p
            if not p.is_file():
                raise ValueError('ZIPAI_DB_SSL_CA 인증서 파일을 찾을 수 없습니다.')
            ca = str(p)
        else:
            try:
                import certifi
                ca = certifi.where()
            except ImportError:
                ca = ssl.get_default_verify_paths().cafile
        if not ca:
            raise ValueError('certifi를 설치하거나 ZIPAI_DB_SSL_CA를 지정하세요.')
        config.update(ssl_ca=ca, ssl_verify_cert=True, ssl_verify_identity=True, ssl_disabled=False)
    elif mode == 'REQUIRED':
        config['ssl_disabled'] = False
    return config


def start_run(category, trigger='unknown', operation='crawl'):
    load_env()
    name = 'FINANCE_IMPORT_TOKEN' if category == 'finance' else 'PROPERTY_IMPORT_TOKEN'
    run = CollectionRun(ROOT, os.getenv('ZIPAI_BASE_URL', 'http://localhost:8080'),
                        os.getenv(name, ''), category, trigger, operation)
    run.emit()
    return run


def execute(run, action):
    try:
        code = action() or 0
        if code not in (0, 3):
            run.payload['errors'] = max(1, run.payload['errors'])
        run.finished('partial' if code == 3 or (code == 0 and run.partial) else 'completed' if code == 0 else 'failed')
        return code
    except KeyboardInterrupt:
        run.finished('cancelled')
        raise
    except BaseException:
        run.payload['errors'] = max(1, run.payload['errors'])
        run.finished('failed')
        raise
