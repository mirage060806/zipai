"""PC .env의 수집 토큰 두 개를 SSH로 EC2에 동기화하고 적용 상태를 확인합니다."""
import argparse
import json
import os
import re
import shlex
import shutil
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent
TOKEN_KEYS = ('PROPERTY_IMPORT_TOKEN', 'FINANCE_IMPORT_TOKEN')

# 토큰은 명령행이나 임시 업로드 파일에 넣지 않고 SSH 표준입력으로 보냅니다.
REMOTE_CODE = r'''
import fcntl
import json
import os
import re
import subprocess
import sys
import tempfile
from pathlib import Path

KEYS = ('PROPERTY_IMPORT_TOKEN', 'FINANCE_IMPORT_TOKEN')


def merge_tokens(text, tokens):
    lines = text.splitlines(keepends=True)
    remaining = []
    pattern = re.compile(r'^\s*(?:export\s+)?(PROPERTY_IMPORT_TOKEN|FINANCE_IMPORT_TOKEN)\s*=')
    for line in lines:
        if not pattern.match(line):
            remaining.append(line)
    original = ''.join(remaining)
    if original and not original.endswith(('\n', '\r')):
        original += '\n'
    return original + ''.join(key + '=' + tokens[key] + '\n' for key in KEYS)


def atomic_write(path, text):
    fd, temp = tempfile.mkstemp(prefix='.zipai-env-', dir=str(path.parent))
    try:
        os.fchmod(fd, 0o600)
        with os.fdopen(fd, 'w', encoding='utf-8', newline='') as stream:
            stream.write(text)
            stream.flush()
            os.fsync(stream.fileno())
        os.replace(temp, path)
    finally:
        if os.path.exists(temp):
            os.unlink(temp)


def container_tokens():
    result = subprocess.run(['sudo', '-n', 'docker', 'inspect', 'zipai'],
                            stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
    if result.returncode:
        raise RuntimeError('현재 zipai 컨테이너와 sudo 권한을 확인하세요.')
    data = json.loads(result.stdout)[0]
    if not data['State']['Running']:
        raise RuntimeError('현재 zipai 컨테이너가 실행 중인지 확인하세요.')
    values = {}
    for line in data['Config'].get('Env') or []:
        key, sep, value = line.partition('=')
        if sep and key in KEYS:
            values[key] = value
    return values


def check_api(tokens):
    # 호스트에 포트 80으로 공개된 기존 앱의 조회 전용 인증 API를 검사합니다.
    import urllib.request
    for key, category, header in [('PROPERTY_IMPORT_TOKEN','market','X-Property-Import-Token'),
                                   ('FINANCE_IMPORT_TOKEN','finance','X-Finance-Import-Token')]:
        request = urllib.request.Request('http://127.0.0.1/api/collection-runs/check?category=' + category,
                                         headers={header:tokens[key]})
        with urllib.request.urlopen(request, timeout=15) as response:
            body = json.loads(response.read().decode('utf-8'))
            if response.status != 200 or body.get('success') is not True:
                raise RuntimeError('조회 전용 토큰 인증 확인 실패')
        print(key + ': 서버 인증 200 확인', flush=True)


def main():
    tokens = json.load(sys.stdin)
    if set(tokens) != set(KEYS) or any(not isinstance(tokens[k],str) or not tokens[k]
                                      or any(c.isspace() for c in tokens[k]) or '\x00' in tokens[k] for k in KEYS):
        raise RuntimeError('PC 토큰 설정의 형식을 확인하세요.')
    config = Path.home() / 'zipai-config'
    env_file = config / 'zipai.env'
    deploy = Path.home() / 'zipai-integrated' / 'deploy-ec2.sh'
    if not env_file.is_file() or env_file.is_symlink():
        raise RuntimeError('기존 ~/zipai-config/zipai.env 파일을 확인하세요.')
    if not deploy.is_file():
        raise RuntimeError('기존 ~/zipai-integrated/deploy-ec2.sh 파일을 확인하세요.')
    os.chmod(config, 0o700)
    with (config / '.token-sync.lock').open('a') as lock:
        os.chmod(config / '.token-sync.lock', 0o600)
        try:
            fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError:
            raise RuntimeError('다른 토큰 동기화가 실행 중입니다. 완료 후 다시 실행하세요.')
        # 컨테이너 조회가 불가능한 상태에서는 환경 파일도 변경하지 않습니다.
        current = container_tokens()
        original = env_file.read_text(encoding='utf-8-sig')
        updated = merge_tokens(original, tokens)
        if updated != original:
            atomic_write(env_file, updated)
            print('EC2 환경 파일: 두 토큰 저장 완료 / 다른 설정 유지', flush=True)
        else:
            os.chmod(env_file, 0o600)
            print('EC2 환경 파일: 두 토큰 일치', flush=True)
        if any(current.get(key) != tokens[key] for key in KEYS):
            print('실행 컨테이너 토큰 적용을 위해 기존 배포 스크립트를 실행합니다. 잠시 기다려 주세요.', flush=True)
            # 기존 배포의 사진 마운트·메모리·롤백 설정을 그대로 사용합니다.
            # 배포 로그에는 다른 비밀 설정이 있을 수 있어 PC로 출력하지 않습니다.
            result = subprocess.run(['bash', str(deploy)], cwd=str(deploy.parent),
                                    stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
            if result.returncode:
                raise RuntimeError('EC2 배포 실패. 환경 파일은 저장됐습니다. EC2에서 기존 배포를 점검하세요.')
        else:
            print('실행 컨테이너: 두 토큰 일치 / 재배포 생략', flush=True)
        applied = container_tokens()
        if any(applied.get(key) != tokens[key] for key in KEYS):
            raise RuntimeError('배포 후 토큰 불일치: deploy-ec2.sh가 zipai.env를 읽는지 확인하세요.')
        check_api(tokens)
        print('동기화·컨테이너 적용·두 토큰 인증 확인 완료', flush=True)


if __name__ == '__main__':
    try:
        main()
    except RuntimeError as error:
        print('동기화 실패: ' + str(error), flush=True)
        sys.exit(1)
    except Exception:
        # 환경 값, HTTP 헤더, Docker inspect 내용은 오류에서도 노출하지 않습니다.
        print('동기화 실패: SSH/sudo/배포 상태와 환경 파일을 확인하세요. 토큰 값은 출력하지 않습니다.', flush=True)
        sys.exit(1)
'''


def read_tokens(path):
    try:
        from dotenv import dotenv_values
    except ImportError:
        raise RuntimeError('python-dotenv 설치 필요: python -m pip install python-dotenv')
    if not path.is_file():
        raise RuntimeError('프로젝트 .env 파일을 찾을 수 없습니다.')
    values = dotenv_values(path, encoding='utf-8-sig', interpolate=False)
    tokens = {}
    for key in TOKEN_KEYS:
        value = values.get(key)
        if not value or any(char.isspace() for char in value) or '\x00' in value:
            raise RuntimeError(key + ': .env에 값이 없거나 공백/줄바꿈이 있습니다.')
        tokens[key] = value
    return tokens


def main():
    parser = argparse.ArgumentParser(description='PC .env → EC2 수집 토큰 자동 적용')
    parser.add_argument('--host', default='3.34.125.63')
    parser.add_argument('--key', type=Path, default=Path('C:/yoonfolder/aws-keys/zipai-key.pem'))
    parser.add_argument('--env-file', type=Path, default=ROOT / '.env')
    parser.add_argument('--check', action='store_true', help='PC 파일만 점검합니다. EC2에 접속하거나 변경하지 않습니다.')
    args = parser.parse_args()
    tokens = read_tokens(args.env_file)
    for key in TOKEN_KEYS:
        print(key + ': PC .env에서 읽기 완료 (값 비공개)', flush=True)
    if args.check:
        print('PC 설정 확인 완료. EC2 변경 없음.')
        return 0
    if not re.fullmatch(r'[A-Za-z0-9][A-Za-z0-9.-]*', args.host):
        raise RuntimeError('EC2 호스트 형식을 확인하세요.')
    if not args.key.is_file():
        raise RuntimeError('SSH 키 파일 경로를 확인하세요. --key 옵션으로 지정할 수 있습니다.')
    ssh = shutil.which('ssh')
    if not ssh:
        raise RuntimeError('ssh를 찾지 못했습니다. Git Bash에서 실행하세요.')
    command = [ssh, '-i', str(args.key.resolve()), '-o', 'BatchMode=yes',
               '-o', 'ConnectTimeout=10', 'ubuntu@' + args.host,
               'python3 -c ' + shlex.quote(REMOTE_CODE)]
    print('EC2로 두 토큰을 동기화합니다. 기존 DB·SNS 설정을 유지합니다.', flush=True)
    result = subprocess.run(command, input=json.dumps(tokens, ensure_ascii=False).encode('utf-8'))
    if result.returncode:
        print('실패: SSH 접속/키/호스트 등록과 EC2 배포 상태를 확인하세요.', flush=True)
    return result.returncode


if __name__ == '__main__':
    for stream in (sys.stdout,sys.stderr):
        if hasattr(stream,'reconfigure'):
            stream.reconfigure(encoding='utf-8',errors='replace')
    try:
        raise SystemExit(main())
    except Exception as error:
        # 토큰을 포함하는 예외는 출력하지 않고, 고정된 입력 검증 설명만 사용합니다.
        print('실행 실패: ' + str(error) if isinstance(error,RuntimeError) else '실행 실패: 파일과 SSH 설정을 확인하세요.')
        raise SystemExit(1)
