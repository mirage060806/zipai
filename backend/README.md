# ZipAI backend workspace

이 디렉터리는 경량 Node.js API와 Python AI/OCR 보조 서비스를 관리합니다.

- 루트의 `*-api.js`, `auth.js`, `database.js`: 현재 `server.js`가 직접 사용하는 Node.js API
- `node/`: 기존 API를 계층형 구조로 점진적으로 분리하기 위한 확장 영역
- `ai-service/`: OCR 및 라이프스타일 추천을 담당할 독립 Python 서비스 골격

운영용 Spring Boot 애플리케이션은 프로젝트 표준에 따라 `src/main` 아래에 위치합니다.

