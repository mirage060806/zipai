"""Run local tests/build with existing .env.local; never print secrets."""
import os
import subprocess
import sys
from pathlib import Path
from dotenv import dotenv_values

root = Path(__file__).resolve().parent
path = root / '.env.local'
if not path.is_file():
    raise SystemExit('.env.local 파일이 없습니다.')
settings = dotenv_values(path, encoding='utf-8-sig', interpolate=False)
missing = [key for key in ('DB_URL', 'DB_USERNAME', 'DB_PASSWORD') if not settings.get(key)]
if missing:
    raise SystemExit('설정 누락: ' + ', '.join(missing))
env = os.environ.copy()
env.update({key: value for key, value in settings.items() if value is not None})
command = ['cmd.exe', '/c', 'gradlew.bat'] if os.name == 'nt' else ['./gradlew']
print('.env.local을 적용해 전체 테스트와 빌드를 실행합니다.')
result = subprocess.run(command + ['test', 'bootJar', '--no-daemon'], cwd=root, env=env)
sys.exit(result.returncode)
