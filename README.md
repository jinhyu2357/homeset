# homeset 26.2

`homeset`은 Paper 26.2 서버에서 플레이어의 집 좌표를 저장/이동/삭제/공유할 수 있도록 만든 Kotlin 기반 플러그인입니다. 빌드와 실행에는 Java 25 이상이 필요합니다.

## 명령어 사용방법과 설명

- `/sethome [name] [share|personal]`
  - 현재 위치를 집으로 저장하거나 기존 집 좌표를 갱신합니다.
  - 이름을 생략하면 `default`를 사용합니다.
  - `share`를 사용하면 공유 집으로 등록(op전용), `personal`은 개인 집으로 저장합니다. (기본값: `personal`)

- `/home [name]`
  - 저장된 집으로 텔레포트합니다.
  - 이름을 생략하면 `default`를 사용합니다.
  - 개인 집이 우선이며, 개인 집이 없으면 공유 집에서 찾습니다.

- `/home <이름> [share|unshare]`(op전용)
  - 본인 집의 공유 상태를 변경합니다.
  - `share`는 공유, `unshare`는 공유 해제입니다. (기본값: `null`)

- `/delhome [name]`
  - 저장된 집 정보를 삭제합니다.
  - 이름을 생략하면 `default`를 삭제합니다.

- `/homes [share|personal]`
  - `/homes` 또는 `/homes personal`은 개인 집 관리 GUI를 엽니다.
  - GUI에서 집 좌표를 확인하고 텔레포트하거나 삭제할 수 있습니다.
  - GUI의 버튼 또는 `/homes share`로 별도의 공유 집 페이지를 엽니다. 공유 집 관리 권한이 있는 관리자는 흰색 침대 버튼으로 좌표를 갱신하거나 용암 양동이 버튼으로 공유 집을 삭제할 수 있습니다.

- `/homesetreload`(op전용)
  - `config.yml`을 다시 불러옵니다.

## `config.yml` 파일 위치

- `plugins/homeset/config.yml`

## 참고

- 데이터베이스 파일은 플러그인 데이터 폴더 아래 `homes.db`로 생성됩니다.
- 서버 기동 시 데이터 폴더 생성, DB 연결, 명령어 등록 중 하나라도 실패하면 플러그인이 비활성화됩니다.
