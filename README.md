# homeset 1.3

`homeset`은 Paper 26.2 서버에서 플레이어의 홈을 저장, 이동, 삭제 및 공유할 수 있는 Kotlin 플러그인입니다. 빌드와 실행에는 Java 25 이상이 필요합니다.

## 주요 기능

- 개인 홈과 서버 공용 공유 홈을 SQLite에 저장합니다.
- `/homes` GUI에서 홈 이동, 삭제, 공유 홈 관리 및 페이지 이동을 지원합니다.
- 개인 홈의 침대 아이콘을 클릭해 Minecraft의 16가지 침대 색상 중 하나로 변경할 수 있습니다.
- 피해 후 재사용 대기 시간(eg. pvp)과 이동 시 취소되는 텔레포트 지연 시간을 설정할 수 있습니다.
- 플레이어의 클라이언트 언어에 따라 영어 또는 한국어 메시지와 GUI를 자동으로 표시합니다.

## 명령어

- `/sethome [이름] [share|personal]`
  - 현재 위치에 홈을 저장하거나 기존 홈을 갱신합니다.
  - 이름과 유형의 기본값은 각각 `default`, `personal`입니다.
  - 홈 이름은 영문자, 숫자, `_`, `-`로 구성된 1~32자여야 합니다.
  - `share`는 공유 홈 관리 권한이 필요합니다.

- `/home [이름]`
  - 개인 홈을 먼저 찾고, 없으면 같은 이름의 공유 홈으로 이동합니다.
  - 이름을 생략하면 `default`를 사용합니다.

- `/home <이름> <share|unshare>`
  - 본인 홈의 공유 상태를 변경합니다. 공유 홈 관리 권한이 필요합니다.

- `/delhome [이름]`
  - 본인 홈을 삭제합니다. 이름을 생략하면 `default`를 사용합니다.

- `/homes [share|personal]`
  - 기본적으로 개인 홈 관리 GUI를 엽니다.
  - `share`는 공유 홈 GUI, `personal`은 개인 홈 GUI를 엽니다.
  - 공유 홈 관리자는 공유 홈의 위치를 현재 위치로 갱신하거나 삭제할 수 있습니다.

- `/homelist`
  - 개인 홈과 공유 홈 이름을 채팅에 각각 출력합니다.

- `/homesetreload`
  - `config.yml`을 다시 불러옵니다. 기본적으로 OP 전용입니다.

## 권한

| 권한 | 기본값 | 용도 |
| --- | --- | --- |
| `homeset.use` | 모든 플레이어 | `/home`, `/homes`, `/homelist` |
| `homeset.sethome` | 모든 플레이어 | `/sethome` |
| `homeset.delhome` | 모든 플레이어 | `/delhome` |
| `homeset.share` | OP | 공유 홈 생성 및 관리 |
| `homeset.reload` | OP | `/homesetreload` |

## 설정

설정 파일은 `plugins/homeset/config.yml`에 생성됩니다.

| 설정 | 기본값 | 설명 |
| --- | ---: | --- |
| `settings.home_damage_cooldown_seconds` | `5` | 피해를 받은 뒤 `/home` 사용을 막는 시간(초). `0`이면 비활성화 |
| `settings.home_teleport_delay_seconds` | `3` | 홈 이동 전 대기 시간(초). 블록 단위로 움직이면 취소되며 `0`이면 즉시 이동 |
| `settings.max_homes_per_player` | `3` | 플레이어당 개인 홈 최대 개수. 공유 홈은 제외되며 `0`이면 제한 없음 |
| `settings.shared_home_manage_permission` | `homeset.share` | 공유 홈 생성 및 관리에 필요한 권한. 기본값은 OP 여부로 판정 |

## 다국어 설정

기본 설정에는 영어 `messages`, `gui`와 한국어 `messages_ko`, `gui_ko`가 포함됩니다. 플레이어의 클라이언트 언어가 자동으로 선택되며 해당 번역이 없으면 영어를 사용합니다. 다른 언어는 `messages_<언어 코드>`와 `gui_<언어 코드>` 섹션을 추가해 지원할 수 있습니다.

## 데이터

- 홈은 플러그인 데이터 폴더의 `homes.db`에 저장됩니다.
- 기존 데이터베이스에는 공유 여부와 아이콘 색상 컬럼이 자동으로 추가됩니다.
- 데이터 폴더 생성, 데이터베이스 초기화 또는 명령어 등록에 실패하면 플러그인이 비활성화됩니다.
