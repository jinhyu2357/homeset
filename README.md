# homeset

`homeset`은 Paper 1.21 서버에서 플레이어의 집 좌표를 저장/이동/삭제할 수 있도록 만든 Kotlin 기반 플러그인입니다.

## 요구 사항

- Java 21
- Gradle Wrapper (`./gradlew`)
- Paper 서버 1.21.x

## 빌드 및 실행

### 1) 플러그인 빌드

```bash
./gradlew clean build
```

- Shadow JAR(`shadowJar`)가 기본 산출물로 생성됩니다.
- 생성된 JAR는 일반적으로 `build/libs/homeset-1.0.jar` 경로에 만들어집니다.

### 2) 로컬 Paper 서버 실행(개발용)

```bash
./gradlew runServer
```

- `run-paper` 플러그인을 통해 Minecraft 1.21 서버를 실행합니다.
- 빌드된 플러그인 JAR가 자동으로 로드됩니다.

## 명령어 사용방법과 설명

플러그인이 활성화되면 아래 명령어를 사용할 수 있습니다.

- `/sethome <이름>`
  - 현재 위치를 `<이름>`으로 저장하거나 기존 집 좌표를 갱신합니다.
  - 권한: `homeset.sethome` (기본 허용)

- `/home <이름>`
  - 저장된 `<이름>`의 집으로 텔레포트합니다.
  - 권한: `homeset.use` (기본 허용)

- `/delhome <이름>`
  - 저장된 `<이름>`의 집 정보를 삭제합니다.
  - 권한: `homeset.delhome` (기본 허용)

- `/homes`
  - 자신이 저장한 집 목록을 확인합니다.
  - 권한: `homeset.use` (기본 허용)

## `config.yml` 파일 설명

이 프로젝트의 메인 클래스는 `saveDefaultConfig()`를 호출하므로, 플러그인 데이터 폴더에 `config.yml`을 둘 수 있습니다.
현재 코드 기준으로는 `config.yml` 값을 직접 읽는 로직이 없어서 **필수 설정값은 없습니다**.

일반적으로는 다음 위치에 파일이 생성/배치됩니다.

- `plugins/homeset/config.yml`

예시 템플릿:

```yaml
# homeset 설정 파일
# 현재 버전(1.0)에서는 코드에서 직접 읽는 항목이 없습니다.
# 이후 버전에서 최대 홈 개수, 쿨다운, 메시지 포맷 등을 확장할 때 사용하세요.
```

## 프로젝트 구조(요약)

- `src/main/kotlin/org/example/jinhhyu/homeset/Homeset.kt`
  - 플러그인 활성화/비활성화, DB 초기화, 명령어 등록 처리
- `src/main/resources/paper-plugin.yml`
  - 플러그인 메타데이터(이름/버전/메인 클래스)와 권한 정의

## 참고

- 데이터베이스 파일은 플러그인 데이터 폴더 아래 `homes.db`로 생성됩니다.
- 서버 기동 시 데이터 폴더 생성, DB 연결, 명령어 등록 중 하나라도 실패하면 플러그인이 비활성화됩니다.
