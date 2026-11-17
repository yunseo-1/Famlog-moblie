# FamLog 인수인계 문서 (모바일 저장소)

- 작성일: 2026-11-12 (사양서 v2.0 기준으로 갱신)
- 인계: 기획·설계 (Claude 채팅) → 구현 (Claude Code)
- 담당자: 김윤서 (기획·백엔드·안드로이드, 1인 개발)
- 이 저장소(`Famlog-moblie`)는 Android 앱 전용이다. 서버·워커·인프라 진행은 백엔드 저장소(`Famlog-BE`)에서 기록한다.

---

## 1. 지금까지 만든 것

| 산출물 | 위치 | 상태 |
|---|---|---|
| 프로젝트 허브 | Notion `FamLog (팸로그) - 가족 라이프 플랫폼` (`40dfc8bd-3e5e-81e7-a350-d047fb58d744`) | 완료 |
| **통합 사양서 v2.0** (개발 기준) | Notion `416fc8bd-3e5e-8128-aab8-dc6d43bbd7f2`, 원본 사본 `Famlog-docs/docs/spec/`, 이 저장소 사본 `docs/spec/` | 완료 |
| 기획서 | Notion `40dfc8bd-3e5e-811c-a214-ca77147cf511` | 배경·방향 문서 |
| 기능명세서 | Notion `40dfc8bd-3e5e-8190-b3bb-dbcadd1477ab` | 사양서로 대체됨 (참고용) |
| ERD 초안 | Notion `40dfc8bd-3e5e-81f6-b5e9-e8e2a5e9c7dc` | 컬럼 상세 참고용, 일부 구버전 (아래 4장) |
| 경쟁 서비스 분석 (카나나) | Notion `40dfc8bd-3e5e-8108-8869-dcf1b0f0cbe4` | 완료 |
| Figma 와이어프레임 + 디자인 패턴 | https://www.figma.com/design/1nECE5iK8TBDRSydCasO1j | 02 채팅방 화면만 미완 (아래 3장) |
| Figma 화면 지도 (로컬) | `docs/design/figma-map.md` | 완료 |
| 디자인 토큰 / Compose 테마 초안 | `docs/design/design-tokens.json`, `docs/design/compose-theme/` | 완료 |
| 화면 캡처 | `docs/design/screenshots/` | 2026-11-11~12 시점 스냅숏 |

## 2. 확정된 결정 (Decision Log)

| 날짜 | 결정 | 이유 |
|---|---|---|
| 11/03 | DB는 PostgreSQL 하나 (+PostGIS +pgvector) | 위치 검색·벡터 검색을 DB 하나로 |
| 11/03 | 채팅과 AI 처리는 큐(Redis Streams)로 분리 | AI 장애가 채팅에 영향 없게 |
| 11/11 | 서비스명 **FamLog (팸로그)** | 'Famory'는 Play에 같은 이름·같은 컨셉 앱이 있음 |
| 11/11 | Android 전용, Google Play 출시 목표 | 개인 계정 → 비공개 테스트 12명·14일 필요 |
| 11/11 | **1:1 채팅 없음**, 가족 그룹당 단체방 1개 | 데이터 범위 = 그룹 범위로 단순화 |
| 11/11 | 여러 그룹 소속 시 **앱 바에서 현재 그룹 전환** (채팅 탭만 전체 목록) | 친가·외가 데이터가 섞이지 않게 |
| 11/12 | 월간 스토리 우선 (주간은 추후) | 요구사항 |
| 11/12 | YouTube: v1.1은 저장+공유 시트, v1.2에 API 직접 업로드 | 감사 전 API 업로드 영상은 비공개 고정 |
| 11/12 | **AI 자동 등록은 후보 테이블 분리** (`registration_candidates`) | "AI가 임의로 등록하지 않는다" 요구사항을 구조로 보장 |
| 11/12 | 로그인: 카카오·Google 소셜 로그인 (제안, 오픈 이슈) | 부모님 세대 편의, 비밀번호 미보관 |
| 11/12 | 레포 3개 분리: 문서(`Famlog-docs`) · 백엔드(`Famlog-BE`) · 모바일(`Famlog-moblie`) | 사양서 v2.0 13.3 |
| 11/12 | 앱 패키지·applicationId `com.famlog`, minSdk 26 / targetSdk 37 | Play는 `com.example` 불가, targetSdk 36 이상 요구 |
| 11/12 | 앱은 `:app` 단일 모듈 + `core/`·`feature/` 패키지 분리 (Gradle 모듈 분리는 나중에) | 1인 개발 초기 단순화 |

## 3. Figma 상태

- 파일 키: `1nECE5iK8TBDRSydCasO1j` / 페이지: `🎨 Design Patterns`(0:1), `📱 Wireframes`(2:2)
- 완료: 화면 01, 03~07, 컴포넌트 12종, 토큰(변수), 디자인 가이드(패턴 A~H)
- **미완: 02 채팅방 화면 재구성** — Figma MCP 호출 한도(Starter)로 중단됨.
  현재 02(`6:114`)는 구버전 메시지 내용이다. 카드 컴포넌트는 최신 구조로 바뀌어 있다.
  만들어야 할 화면은 `figma-map.md` 5장 "02 계열 목표 구조"에 적어 두었다.
  → 앱 구현은 **목표 구조 문서 + 컴포넌트 기준**으로 진행하면 된다. Figma 반영은 한도가 풀리면 따로.
- 04~06 캡처는 앱 바가 "장소/앨범/이야기"로 찍혀 있지만, 실제 Figma는 **"우리 가족 ▾"(그룹 전환)**으로 바뀌어 있다.

## 4. 알려진 불일치 (구현 시 이렇게 처리)

| 위치 | 내용 | 처리 |
|---|---|---|
| ERD `users` | email, password_hash 컬럼 | 쓰지 않는다. `social_accounts` 사용 (사양서 10.2) |
| ERD `group_members` | family_role, ai_opt_out 없음 | 사양서 10.2대로 추가 |
| ERD `archive_items.status` | SUGGESTED/DISMISSED 상태 | 쓰지 않는다. 후보는 `registration_candidates` |
| ERD `messages.type` | ARCHIVE_SUGGESTION | `CANDIDATE` 타입으로 바꾸고 payload에 candidateId (사양서 10.2) |
| ERD `album_videos` | 앨범 전용 | 사양서 10.1 `videos`로 일반화 |
| ERD `stories.period_type` | WEEKLY 기본처럼 보임 | MONTHLY 기본 |
| `suggestion_feedback` | archive_item_id 기준 | `candidate_id` 기준으로 기록 (승인/수정/무시 모두) |
| 기능명세서 | 구 ID 체계(SUM-01 등) | 사양서 04장 ID만 사용 |
| 사양서 S6 프롬프트 | targetSdk 36 | 이 저장소는 37 (사양서 8.1 기준 "36 이상" 충족) |
| 사양서 13.3 | `core/*`, `feature/*`를 Gradle 모듈로 그림 | 지금은 `:app` 안의 패키지 (CLAUDE.md 5장) |

## 5. MVP 범위와 일정 (사양서 17장)

포함: 소셜 로그인·만 14세 이상 확인·기기 관리·계정 삭제·데이터 내보내기·동의 관리 / 그룹 생성·초대·전환·역할 /
단체 채팅(사진·동영상·읽음·답장·공감·푸시)·신고 / 구성원별·전체 일정, 반복, 공휴일, 할 일 /
장소 공유·지역별·구성원별 목록·별칭·지도 / ⭐ 채팅 기반 자동 등록(일정·기념일·할 일·장소)

| 주차 | 기간 | 목표 | 단계(사양서 20.4) | 모바일 단계 |
|---|---|---|---|---|
| W1 | 11/16~11/22 | 개발 환경, 서버·앱·워커 골격, CI, Photo Picker EXIF 검증 | S0~S3, S6 | S6 |
| W2~3 | 11/23~12/06 | 계정·그룹 | S4, S5, S7 | S7 |
| W4~5 | 12/07~12/20 | 채팅·미디어·푸시 | S8~S11 | S10, S11 |
| W6 | 12/21~12/27 | 일정·할 일·공휴일 | S12, S13 | S13 |
| W7 | 12/28~01/03 | 장소·카카오 연동 | S14, S15 | S15 |
| W8~9 | 01/04~01/17 | ⭐ 자동 등록, 평가 데이터셋 | S16~S19 | S19 |
| W10 | 01/18~01/24 | 설정·개인정보 기능·보안 테스트·정책 문서 | S20, S21 | S20 |
| W11~12 | 01/25~02/07 | 비공개 테스트 14일 (12명+), 버그 수정 | S22 | S22 |
| W13~14 | 02/08~02/21 | **v1.0 출시** | - | - |

## 6. 다음 작업: W1 체크리스트 (모바일, 사양서 20.5 S6)

선행 (다른 저장소):
- [x] S0 문서 레포 정리 (`Famlog-docs`), 사양서 사본 동기화
- [ ] S1~S3 백엔드 인프라·서버 골격·보안 골격 → `Famlog-BE`에서 진행

이 저장소:
- [x] 저장소 정리: `CLAUDE.md`·`.mcp.json`·`docs/` 루트 배치, 패키지 `com.famlog`, minSdk 26 / targetSdk 37
- [x] 사양서 v2.0 사본 → `docs/spec/`, `/feature` 명령
- [x] 패키지 골격: `core/{model,network,datastore,designsystem,common}`, `feature/{auth,chat,calendar,place,album,story}` (database는 S10, group·settings는 S7·S20)
- [x] Hilt, Compose Material 3, edge-to-edge, 예측형 뒤로가기(`enableOnBackInvokedCallback`)
- [x] `core/designsystem`: `FamTheme.kt` + Material 3 색·글꼴 연결, Noto Sans KR 3종, 기본 `ui/theme/` 제거
- [x] 하단 탭 5개(채팅·캘린더·장소·앨범·이야기), 앱 바 "우리 가족 ▾" 자리, 앨범·이야기 "곧 만나요"
- [x] Retrofit + 인증 인터셉터 + `TokenAuthenticator`(재발급 동기화 1회), Keystore 암호화 DataStore 토큰 저장
- [x] GitHub Actions: `./gradlew :app:assembleDebug :app:testDebugUnitTest` (첫 PR에서 CI 통과 확인 필요)
- [ ] **기술 검증 스파이크**: 디버그 앱의 "FamLog EXIF" 아이콘으로 실기기에서 확인 → 아래 표에 기록

### EXIF 검증 결과 (실기기에서 채울 것)

| 기기 / Android | Photo Picker 그대로 읽기 (촬영 시각 / GPS) | `ACCESS_MEDIA_LOCATION` 허용 후 | `setRequireOriginal` | 결론 |
|---|---|---|---|---|
| (에뮬레이터 API 37) | 화면 실행만 확인, 사진 없음 | - | - | - |
| | | | | |

판단 기준: Photo Picker 사진에서 GPS를 읽을 수 없다면 ALB-02(여행 앨범)는 서버 워커가 업로드 원본에서 GPS를 읽는 방식(사양서 8.6, S9)으로 간다.

### S6 메모 (S7에서 이어서)

- 앱 시작 화면은 지금 채팅 탭 고정. S7에서 토큰 유무·`lastGroupId`로 로그인 / 그룹 시작(SCR-02) / 마지막 그룹을 고른다.
- 로그인 화면은 자리만 있다 (`feature/auth/LoginScreen.kt`). 세션 만료 이벤트가 오면 이 화면으로 간다.
- 백엔드 인수인계(S0~S5) 중 S7에서 반영할 것: 로그인 응답 404 `USER_NOT_REGISTERED` → 약관·닉네임·출생연월 화면 후 `/auth/{provider}/signup`,
  Google `setServerClientId`는 백엔드 `infra/.env`의 웹 클라이언트 ID, 그룹 응답의 `familyRole`이 비어 있으면 SCR-03 안내.
- 릴리스 서버 주소는 자리 값(`https://api.famlog.invalid/`). S22에서 교체.
- 폰트 라이선스: `app/src/main/assets/licenses/OFL-NotoSansKR.txt`. 앱 안 오픈소스 라이선스 화면은 S20·S22에서.
- 문서 레포의 `docs/api/openapi.yaml`이 아직 비어 있다. S7은 `Famlog-BE`의 `AuthDtos`·`GroupDtos` 코드를 계약으로 썼다. S8 전에 생성해 커밋할 것.

### S7 메모 (로그인·그룹)

- 시작 분기 `core/data/StartResolver`: 토큰·사용자 ID 없음 → 로그인 / 그룹 없음 → SCR-02 / 현재 그룹에서 내 `familyRole` 없음 → SCR-03 / 그 외 → 채팅 탭. 서버에 닿지 못하면 [다시 시도].
- GET /users/me가 없어 로그인 응답의 `user.id`·`lastGroupId`를 `SessionStore`(DataStore)에 저장한다. 현재 그룹은 전환 시 서버 PUT 성공 후 로컬에도 저장(GRP-03 재실행 유지).
- 소셜 키: `local.properties`에 `FAMLOG_KAKAO_NATIVE_APP_KEY`, `FAMLOG_GOOGLE_WEB_CLIENT_ID`. 콘솔 등록은 **디버그 키**(키 해시 `WXsVqa7rwrbdsirdJT2GSnj0+so=`, SHA-1 `59:7B:…:FA:CA`)만 했다. 릴리스·Play 앱 서명 키는 S22에서 추가 등록.
- `googleid`는 1.1.1 고정: 1.2.x는 Kotlin 2.4로 빌드돼 현재 Kotlin 2.2.10에서 읽지 못한다. Kotlin을 올릴 때 같이 올린다.
- 그룹이 1개여도 앱 바 ▾를 보인다(figma-map 2장은 1개면 숨김). 시트에 [가족 초대]·[새 그룹]·[코드 참여]가 있어 다른 진입점이 생기기 전까지 유지.
- 초대 공유는 **코드만** 글로 보낸다. 서버 링크 도메인(`famlog.app`)을 아직 소유하지 않아 링크를 넣지 않았다. App Links는 도메인 확정 후.
- 미룬 것: SCR-04 알림 권한(S11), 프로필·그룹 사진(S9, 지금은 첫 글자 아바타), 약관 전문(S22, 지금은 "출시 전 제공" 안내), 로그아웃·기기 관리(S20), GRP-05 구성원 관리 화면.
- **Android 17(API 37) 로컬 네트워크 보호**: 앱이 사설 IP(`10.0.2.2`, `192.168.x.x`)에 붙으려면 `ACCESS_LOCAL_NETWORK`(런타임 권한) 필요.
  없으면 연결이 15초 시간 초과로 끝난다. 디버그 빌드에만 선언하고 시작 시 요청한다(`src/debug/.../BuildTypeSetup`). 릴리스에는 없음.
- 실제 카카오·Google 로그인 → 가입 → 그룹 만들기/참여 → 역할 → 전환 흐름은 본인 계정으로 에뮬레이터·실기기 확인 필요 (아래 체크리스트).

### S10-1 메모 (텍스트 채팅)

- 범위: SCR-10 채팅 탭, SCR-11 채팅방, SCR-12 서랍(구성원·가족 초대만), CHAT-01~04·06. 사진·동영상(CHAT-05)은 **S10-2**에서.
- 실시간: `core/network/ChatSocket`(OkHttp 웹소켓 위 STOMP 1.2 직접 구현, 라이브러리 없음). 연결 확인은 웹소켓 ping 15초, STOMP 하트비트는 `0,0`.
  `core/data/ChatSync`가 앱이 화면에 보일 때만(ProcessLifecycleOwner) 연결하고, 끊기면 1·2·4…30초 간격 + 네트워크 복구 시 바로 다시 연결한다.
- 토큰 만료 ERROR 프레임 → `groups/me` 한 번 호출해 `TokenAuthenticator`가 재발급하게 한 뒤 다시 연결 (재발급 경로를 따로 만들지 않음).
- 캐시: Room `famlog.db`의 `messages` 한 표. 키는 `clientMessageId`(없으면 `id:{서버 id}`)라 서버 응답이 보내기 전 줄을 덮어쓴다. 스키마가 바뀌면 지우고 다시 받는다(마이그레이션 없음).
  로그아웃(AUTH_INVALID)·다른 계정 로그인 시 비운다. `allowBackup=false`라 백업에 들어가지 않는다.
- **사양서 20장 S10과 다른 점**: 텍스트 재전송은 WorkManager가 아니라 앱 안 대기열(Room의 SENDING 줄 → 연결되면 같은 clientMessageId로 다시 보냄, 250ms 간격).
  서버가 메시지 전송을 STOMP로만 받기 때문. 앱을 완전히 닫으면 다음 실행 때 보낸다. WorkManager는 S10-2 업로드 재시도에 쓴다. (2026-11-17 사용자 확인)
- 오래 안 들어온 방: afterSeq 5페이지(250개)를 넘게 밀려 있으면 캐시를 버리고 최신 50개부터 다시 받는다. 캐시가 없는 방의 남의 메시지는 저장하지 않고 채팅 탭 요약만 바꾼다(방에 들어갈 때 최신 페이지).
- 보낸 사람 이름 = 호칭(`familyTitle`) → 없으면 닉네임 (`Member.displayName`). 역할(엄마 등)로 대신 보이는 규칙은 아직 없음.
- 자리만 만든 것: [신고](MOD-01 API는 S20, 지금은 "곧 제공돼요"), `type=CANDIDATE` 카드 자리(S13), VOICE·PLACE·POLL 등은 "지원하지 않는 메시지".
- 뺀 것: 채팅 탭 이야기 배너(v1.1)·검색(CHAT-09), 서랍의 사진·링크 모아보기·공지·그룹 설정, 메시지 복사.

## 7. 오픈 이슈

사양서 19장이 기준이다. 모바일에 직접 영향이 있는 것:

- [ ] 로그인 방식 확정 (소셜만 권장) — W2 전
- [ ] 위치기반서비스 신고 여부 — 결정 전까지 위치 기능은 플래그로 끔 (W10 전)
- [ ] Play 카테고리 (Communication 권장) — W10 전
- [ ] 비공개 테스터 12명 명단 — W10 전

## 8. 진행 현황 (Claude Code가 갱신)

| 날짜 | 기능 ID | 내용 | 커밋 |
|---|---|---|---|
| 2026-11-12 | - | 통합 사양서 v2.0 사본(`docs/spec/`), CLAUDE.md 사양서 v2.0 기준으로 갱신, `/feature` 명령 추가 | docs(spec) |
| 2026-11-12 | - | CLAUDE.md 패키지 목록을 사양서 13.3에 맞춤, HANDOFF를 모바일 범위·사양서 v2.0 기준으로 정리 | docs |
| 2026-11-15 | S6 | 앱 골격: Hilt·내비게이션·하단 탭, FamTheme, 토큰 저장·재발급, 디버그 EXIF 검증 화면, CI | chore(S6) |
| 2026-11-17 | S7 | 카카오·Google 로그인·가입(SCR-01), 그룹 시작(SCR-02), 내 가족 역할(SCR-03), 그룹 전환 시트(SCR-70), 시작 분기 | feat(AUTH-01) |
| 2026-11-18 | S10-1 | 채팅 탭(SCR-10)·채팅방(SCR-11)·서랍(SCR-12), STOMP 실시간, Room 캐시, 오프라인 재전송, 읽음·답장·공감·삭제 | feat(CHAT-02) |
