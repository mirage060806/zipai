# Render Docker 빌드 Gradle 다운로드 오류

## 결론

배포 실패 지점은 애플리케이션 소스 컴파일이 아니라 Gradle Wrapper가 Gradle 배포본을 내려받는 단계입니다. `gradle-wrapper.properties`의 연결 제한 시간을 60초로 늘리고, 재시도를 3회로 설정했습니다.

## 관측한 오류

Render 빌드 로그에서 다음 순서로 실패했습니다.

1. Docker 이미지와 빌드 컨텍스트 준비는 완료되었습니다.
2. `RUN ./gradlew clean bootJar --no-daemon`에서 Wrapper가 `gradle-9.7.1-bin.zip` 다운로드를 시작했습니다.
3. 약 40% 다운로드 후 `java.net.SocketException: Connection reset`이 발생했습니다.
4. Wrapper 설정이 `retries=0`, `networkTimeout=10000`이므로 재시도 없이 종료 코드 1로 빌드가 끝났습니다.

따라서 `Dockerfile:10`은 실패가 드러난 위치이며, 직접 원인은 Render 빌드 컨테이너와 Gradle 배포 서버 사이의 일시적 네트워크 연결 종료입니다. Java 코드, 의존성 선언 또는 Dockerfile 명령 자체의 오류라는 근거는 로그에 없습니다.

## 적용한 해결

`gradle/wrapper/gradle-wrapper.properties`를 다음처럼 변경했습니다.

| 항목 | 기존 | 변경 | 목적 |
| --- | ---: | ---: | --- |
| `networkTimeout` | 10,000 ms | 60,000 ms | 느린 CI 네트워크에서도 연결·읽기 대기 |
| `retries` | 0 | 3 | 연결 초기화 시 자동 재시도 |
| `retryBackOffMs` | 500 ms | 1,000 ms | 재시도 사이에 짧은 대기 |

이 변경은 Wrapper 배포본 다운로드에만 적용되며, 애플리케이션 런타임 동작에는 영향을 주지 않습니다.

## 실행 및 검증 결과

로컬에서 다음 명령을 실행했습니다.

```powershell
.\gradlew.bat clean bootJar --no-daemon
```

결과는 **`BUILD SUCCESSFUL`** 이었고, 21초 동안 `clean`, `compileJava`, `processResources`, `classes`, `resolveMainClassName`, `bootJar` 작업이 완료되었습니다. 생성된 산출물은 `build/libs/zipai-0.0.1-SNAPSHOT.jar`(약 34.8 MB)입니다.

이 작업 환경에는 Docker CLI가 설치되어 있지 않아 Render와 같은 컨테이너 빌드는 직접 실행할 수 없습니다.

Render에서는 변경사항을 푸시한 뒤 **Manual Deploy → Deploy latest commit**(또는 새 커밋 푸시)로 다시 빌드합니다. 일시적 네트워크 문제여도 이제 최대 3회 재시도하므로 동일 유형의 실패 가능성이 낮아집니다.

## 재발 시 점검

재시도 후에도 실패하면 Render 배포 로그의 실패 대상 URL과 HTTP/네트워크 오류를 확인합니다.

- `services.gradle.org`만 반복 실패: Render 빌드 네트워크 또는 Gradle 배포 서버 상태를 우선 확인합니다.
- Gradle 배포본 다운로드는 성공하고 Maven 의존성 다운로드가 실패: Maven Central 연결 문제이므로 해당 로그를 기준으로 별도 조치합니다.
- 컴파일 오류가 이어서 발생: 그 시점의 Java 컴파일 로그가 새로운 실제 원인이므로 분리해 해결합니다.
