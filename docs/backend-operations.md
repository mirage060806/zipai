# 백엔드 운영 안내

## 환경변수

- `PORT`: 서버 포트, 기본값 `4173`
- `HOST`: 바인딩 주소, 로컬 기본값 `127.0.0.1`
- `COOKIE_SECURE`: HTTPS 운영 환경에서는 반드시 `true`
- `ZIPAI_DB_PATH`: SQLite 파일의 절대 또는 실행 디렉터리 기준 경로
- `ZIPAI_BACKUP_DIR`: DB 백업 파일 저장 경로
- `PUBLIC_DATA_SERVICE_KEY`: 공공데이터포털 일반 인증키

외부 접속을 허용하는 운영 환경은 `HOST=0.0.0.0`으로 설정하고, Node 서버 앞에 HTTPS를 종료하는 리버스 프록시를 둡니다. 이때 `COOKIE_SECURE=true`를 적용합니다.

## 실행과 검사

```text
node server.js
node scripts/backend-integration-test.js
```

통합 테스트는 임시 DB를 사용하므로 운영 DB를 변경하지 않습니다.

## SQLite 백업

```text
node scripts/backup-database.js
```

스크립트는 WAL 체크포인트 후 타임스탬프가 포함된 별도 SQLite 파일을 생성합니다. 운영에서는 스케줄러로 정기 실행하고, 백업 디렉터리를 서버와 다른 저장소에 추가 복제해야 합니다. 복구 훈련과 보관 기간도 별도로 정합니다.

## 적용된 보호

- 서버 DB 세션과 `HttpOnly`, `SameSite=Lax` 쿠키
- 역할 기반 관리자 API 권한 검사
- 변경 API의 브라우저 `Origin` 검사
- 인증 및 일반 API 요청 횟수 제한
- 입력 길이와 허용 상태값 검사
- 보안 응답 헤더
- `.env`, SQLite, 백엔드 소스와 내부 자료의 정적 파일 노출 차단

다중 서버 운영 시 메모리 기반 요청 제한을 Redis 등의 공유 저장소 기반으로 교체해야 합니다. 개인정보 보관·파기 정책, 중앙 로그 수집, 침해 모니터링과 실제 HTTPS 인증서 구성은 배포 환경에서 별도로 완료합니다.
