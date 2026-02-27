# homeset

`homeset`은 Paper 1.21 서버에서 플레이어의 집 좌표를 저장/이동/삭제/공유할 수 있도록 만든 Kotlin 기반 플러그인입니다.

## 명령어 사용방법과 설명

플러그인이 활성화되면 아래 명령어를 사용할 수 있습니다.

- `/sethome [이름] [share|personal]`
  - 현재 위치를 집으로 저장하거나 기존 집 좌표를 갱신합니다.
  - 이름을 생략하면 `default`를 사용합니다.
  - `share`를 사용하면 공유 집으로 등록, `personal`은 개인 집으로 저장합니다. (기본값: `personal`)
  - 권한: `homeset.sethome` (기본 허용)
  - 공유 저장 권한: `homeset.share` (기본 OP)

- `/home [이름]`
  - 저장된 집으로 텔레포트합니다.
  - 이름을 생략하면 `default`를 사용합니다.
  - 개인 집이 우선이며, 개인 집이 없으면 공유 집에서 찾습니다.
  - 권한: `homeset.use` (기본 허용)

- `/home <이름> [registered|unregistered]`
  - 본인 집의 공유 상태를 변경합니다.
  - `registered`는 공유 등록, `unregistered`는 공유 해제입니다. (기본값: `unregistered`)
  - 권한: `homeset.share` (기본 OP)

- `/delhome [이름]`
  - 저장된 집 정보를 삭제합니다.
  - 이름을 생략하면 `default`를 삭제합니다.
  - 권한: `homeset.delhome` (기본 허용)

- `/homes [share|personal]`
  - 저장된 집 목록을 확인합니다.
  - `personal`은 본인 집 목록, `share`는 공유 집 목록을 표시합니다. (기본값: `personal`)
  - 권한: `homeset.use` (기본 허용)

- `/homesetreload`
  - 플러그인 설정(`config.yml`)을 다시 불러옵니다.
  - 권한: `homeset.reload` (기본 OP)

## `config.yml` 파일 위치

- `plugins/homeset/config.yml`

## 참고

- 데이터베이스 파일은 플러그인 데이터 폴더 아래 `homes.db`로 생성됩니다.
- 서버 기동 시 데이터 폴더 생성, DB 연결, 명령어 등록 중 하나라도 실패하면 플러그인이 비활성화됩니다.
