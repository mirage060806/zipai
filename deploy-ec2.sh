#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
env_file="$HOME/zipai-config/zipai.env"
upload_dir="$HOME/zipai-data/uploads"
community_dir="$HOME/zipai-data/static-uploads"
mkdir -p "$upload_dir" "$community_dir"
chmod 700 "$HOME/zipai-data"
test -f "$env_file" || { echo "환경변수 파일 없음: $env_file"; exit 1; }
for key in DB_URL DB_USERNAME DB_PASSWORD NAVER_MAPS_CLIENT_ID NAVER_MAPS_CLIENT_SECRET; do
  if ! awk -v key="$key" 'index($0,key "=")==1 && length(substr($0,length(key)+2))>0 {found=1} END {exit !found}' "$env_file"; then
    echo "필수 설정 누락: $key"; exit 1
  fi
done
# 빌드 실패 시 현재 실행 중인 앱은 유지한다.
sudo docker build -t zipai:latest .
backup="zipai-backup-$(date +%Y%m%d%H%M%S)"
had_old=false
if sudo docker container inspect zipai >/dev/null 2>&1; then
  sudo docker stop zipai
  # 정지 후 복사하여 복사 중 새 업로드가 발생하지 않게 한다.
  if sudo docker cp zipai:/app/uploads/. "$upload_dir/"; then
    echo '기존 업로드 파일 보존 완료'
  else
    echo '사진 복사 실패. 기존 서버를 다시 시작하고 배포를 중단합니다.'
    sudo docker start zipai
    exit 1
  fi
  # docker cp는 정지된 컨테이너에서도 동작한다.
  static_backup=$(mktemp -d)
  if ! sudo docker cp zipai:/app/static/. "$static_backup/"; then
    sudo rm -rf "$static_backup"
    echo '커뮤니티 사진 확인 실패. 기존 서버를 다시 시작합니다.'
    sudo docker start zipai
    exit 1
  fi
  if [ -d "$static_backup/uploads" ]; then
    if ! sudo cp -a "$static_backup/uploads/." "$community_dir/"; then
      sudo rm -rf "$static_backup"
      sudo docker start zipai
      exit 1
    fi
  fi
  sudo rm -rf "$static_backup"
  sudo docker rename zipai "$backup"
  had_old=true
fi
rollback() {
  echo '새 컨테이너 실행/기동 확인 실패. 이전 컨테이너를 복구합니다.'
  sudo docker logs --tail 60 zipai 2>/dev/null || true
  sudo docker rm -f zipai >/dev/null 2>&1 || true
  if "$had_old"; then
    sudo docker rename "$backup" zipai
    sudo docker start zipai
  fi
}
trap rollback ERR
sudo docker run -d --name zipai --restart unless-stopped \
  --env-file "$env_file" \
  -e JAVA_TOOL_OPTIONS="-Xms128m -Xmx512m" \
  --mount "type=bind,source=$upload_dir,target=/app/uploads" \
  --mount "type=bind,source=$community_dir,target=/app/static/uploads" \
  -p 80:8080 zipai:latest
ready=false
for attempt in $(seq 1 60); do
  if sudo docker exec zipai curl -fsS http://localhost:8080/api/auth/social-providers >/dev/null 2>&1; then
    ready=true; break
  fi
  sleep 2
done
if ! "$ready"; then false; fi
trap - ERR
sudo docker exec zipai python3 -c 'import numpy, pandas, sklearn; print("Python ML 패키지 확인 완료")'
sudo docker inspect zipai --format '{{range .Mounts}}{{.Source}} -> {{.Destination}}{{println}}{{end}}'
sudo docker ps --filter name=zipai
sudo docker exec zipai curl -fsS http://localhost:8080/api/auth/social-providers
echo
echo '서버 기동 확인 완료. 브라우저에서 실제 로그인과 AI 추천 결과를 확인하세요.'
if "$had_old"; then echo "이전 컨테이너 보관: $backup (중지 상태)"; fi
