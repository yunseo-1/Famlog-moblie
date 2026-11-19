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
  **S22 할 일**: 같은 릴리스·Play 앱 서명 키의 SHA-1을 Google Cloud 콘솔(프로젝트 `affable-curve-514603-j7`) → 사용자 인증 정보 →
  `Android key (auto created by Firebase)`의 Android 앱 제한에도 추가한다. 빠뜨리면 출시판에서 FCM 토큰 발급이 막혀 푸시가 오지 않는다 (S11 메모).
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

### S10-2 메모 (사진·동영상)

- 보내기: 입력창 [+] → Photo Picker(권한 없음, 30개) → `core/media/ContentMediaInspector`가 서버와 같은 기준(사진 jpeg·png·webp·heic·heif 30MB, 동영상 mp4·mov 500MB·5분)으로 거르고 뺀 개수를 알린다.
  사진은 한 묶음(IMAGE), 동영상은 하나씩(VIDEO) 메시지를 만든다(서버 규칙). Photo Picker 읽기 권한은 `takePersistableUriPermission`으로 유지한다.
- 업로드: `MediaUploadWorker`(WorkManager, 네트워크 있을 때, 30초부터 지수 백오프, 6번 실패하면 FAILED) → `core/data/MediaUploader`.
  시도마다 남은 파일만 새 URL 발급 → 응답 headers 그대로 PUT(인증·로그 없는 `@UploadClient`) → complete → 파일별 mediaId를 Room에 남긴다.
  다 올리면 `ChatRepository.sendIfReady`가 STOMP로 보낸다. 연결이 없으면 대기열에 남아 연결될 때 보낸다 (업로드는 백그라운드에서도, 전송은 앱이 보일 때).
- 보기: Coil 3이 앱 OkHttp(인증·재발급)로 `GET /media/{id}/content?variant=`를 부르고 302를 따라간다. 다른 호스트로 넘어갈 때 OkHttp가 Authorization을 뗀다.
  격자는 THUMB_S, 한 장은 THUMB_M, 전체 화면은 THUMB_L. UPLOADED는 "처리 중", `media.updated`가 오면 바뀐다.
- 동영상 재생: 기본 `VideoView`(사용자 결정). 인증 헤더를 못 넣어서 302의 저장소 주소를 먼저 받아 넘긴다(`MediaUrls.resolve`, 로그 없음).
- Room 버전 2 (mediaJson·localMediaJson 추가). 캐시라 버전이 바뀌면 지운다 → 업데이트 전에 보내지 못한 메시지도 사라진다.
- **로컬에서 업로드하려면 백엔드 `infra/.env`에 `S3_PUBLIC_ENDPOINT=http://10.0.2.2:8333`** 를 넣고 서버를 다시 켠다. 비어 있으면 업로드 URL이 localhost라 에뮬레이터에서 실패한다.
- 에뮬레이터 실기 확인(2026-11-18): 사진 3장 묶음·동영상 업로드 → 처리 중 → 썸네일, 전체 화면 보기, VideoView 재생, 저장된 원본에 GPS 없음.
  **백엔드에 넘길 것**: 에뮬레이터 카메라 동영상(오디오 AMR-NB)은 워커의 메타데이터 제거 단계(`ffmpeg -c copy -f mp4`)가 실패해 `NOT_A_VIDEO`로 거부된다.
  mp4에 못 넣는 오디오는 AAC로 바꾸는 처리가 필요 (`Famlog-BE/worker/app/media/videos.py`). → 백엔드 PR #11에서 수정됨
- 미룬 것: 원본 저장(내려받기), 서랍 사진 모아보기(앨범 단계), 동영상 업로드 중 미리보기 프레임(로컬은 재생 아이콘만), 업로드 진행 알림(포그라운드 서비스).

### S11 메모 (푸시 알림)

- 계약: 백엔드 HANDOFF "푸시 규칙 (S11, 앱 S11 참고)". 서버는 data 메시지만(우선순위 HIGH) 보내고, 채널·묶음·문구·표시 여부는 앱이 정한다.
- Firebase: 프로젝트 `affable-curve-514603-j7`, `app/google-services.json`은 git에 올린다(사용자 결정, 비밀값 아님). 서비스 계정 키는 백엔드 `infra/.env`의 경로만.
  - `google-services.json`의 API 키는 앱에 들어가는 공개 값이라 GitHub Secret scanning 경고는 Won't fix로 닫는다.
    대신 키 제한을 건다(2026-11-18 적용): Android 앱 `com.famlog` + 디버그 SHA-1, API는 Firebase Installations·FCM Registration만.
    적용 뒤 앱 데이터를 지우고 새로 발급받아 `device_tokens` 등록까지 확인했다. 릴리스·Play 앱 서명 SHA-1 추가는 S22.
- 토큰: `PushTokenRepository`가 앱이 처음 보일 때(프로세스당 1번)·로그인/가입 직후·`onNewToken` 때 `PUT /devices/token`. 로그아웃 때는 서버가 지우므로 부르지 않는다.
- 수신: `FamlogMessagingService` → `PushReceiver`(로그아웃 상태·보고 있는 채팅방·내가 보낸 것은 버림) → `PushNotifier`.
  채널 4개(`famlog.chat`·`schedule`·`ai`·`system`). 채팅은 그룹마다 알림 1개(tag `chat:{groupId}`, MessagingStyle로 쌓음) + 요약 1개. `silent`면 소리·진동 없음.
  본문이 없으면(미리보기 숨김) "새 메시지", 사진·동영상은 "사진 N장"·"동영상".
- 보고 있는 채팅방: `ChatPresence`(채팅방 화면 resume/pause). 들어가면 그 그룹 알림을 지운다.
- 알림 탭: `MainActivity` → `PendingChatOpen` → 시작 분기를 마치고 탭 화면에 오면 `ChatRoomRoute`(바닥은 채팅 탭). 로그아웃하면 버린다.
- 권한(SCR-04, Figma 없음): 탭 화면에 처음 왔을 때 안내 시트 1번 → [알림 받기]면 시스템 창. 거절해도 다시 묻지 않는다(`AppPrefs`, 설정 화면은 S20).
- 실기 확인(2026-11-18, 에뮬레이터 API 37 + 백엔드 세션이 user 2로 발송): 입장 시스템 메시지, 텍스트 쌓임, 사진 2장, 미리보기 숨김, 방해 금지 무음,
  채팅방 보는 중 미표시, 앱 꺼진 상태에서 알림 탭 → 채팅방 이동. FCM 토큰 거절 없음.
- 함께 고친 것: 디버그 요청 로그가 302 뒤 저장소 Presigned URL(서명 포함)을 남기던 문제 → 쿼리를 가린다. 여러 메시지가 한꺼번에 붙으면 맨 아래로 안 내려가던 문제.
- 함께 고친 S10 버그: 앱이 꺼진 동안 온 메시지를 열면 읽은 메시지에 "1"이 남았다. 채팅방 `refresh`와 재연결 `syncCached`가 같은 페이지를 받는 동안
  `read.updated`가 먼저 반영되고, 늦게 끝난 쪽이 예전 unreadCount로 덮어썼다. → 이미 받은 메시지는 더 작은 안 읽은 수를 지킨다(`ChatRepository.store`).
  고치기 전에 잘못 저장된 캐시는 그대로 남는다(출시 전이라 개발 기기만 해당, 앱 데이터를 지우면 사라짐).
- 미룬 것: 알림 설정 화면(S20, 서버 API는 있음), 일정·AI 알림(S12~), 알림에서 바로 답장, 새 기기 로그인 알림 → 기기 관리 화면 이동(SCR-80).

### S13-1 메모 (캘린더·일정)

- 계약: 백엔드 HANDOFF "일정·할 일 규칙 (S12, 앱 S13 참고)". 일정은 로컬 캐시 없이 화면에 들어올 때마다 받는다(`ScheduleRepository`, 공휴일만 해마다 메모리 캐시).
  등록·수정·삭제가 끝나면 `ScheduleRepository.changes`로 캘린더·상세가 다시 받는다.
- 캘린더 탭(SCR-20): [일정 | 할 일](할 일은 S13-2), [전체] + 구성원 칩(여러 명, 고른 사람이 참여한 일정의 합집합 — 앱에서 거른다),
  월 격자(일요일 시작, 4~6주, 공휴일 빨간 날, 구성원 색 점) + 고른 날 일정, [목록]은 그 달의 날짜별 일정. 색 점 옆에 늘 이름을 함께 보인다.
  구성원 색은 `calendar_color`, 없으면 구성원 순서대로 팔레트.
- 등록·수정(SCR-21, Figma 없음 → 후보 수정 시트 필드 패턴): 시작을 옮기면 길이를 지킨 채 끝도 옮긴다, 참여자 기본은 나,
  반복은 안 함/매주/매월/매년 + 종료일(선택, 사용자 결정). 다른 곳에서 만든 간격·횟수는 종류를 안 바꾸면 그대로 다시 보낸다.
  알림은 최대 3개, 종일은 전날 오후 8시만. 시간·참여자를 바꾸면 0.4초 뒤 겹침 조회 → "아빠는 이 시간에 '…' 일정이 있어요"(막지 않음).
  수정 409 `VERSION_CONFLICT`면 안내하고 최신 내용을 다시 불러온다.
- 상세: 수정·삭제는 등록한 사람 또는 관리자 이상(서버와 같은 기준). 반복 일정 삭제는 "모든 회차가 삭제돼요" 확인.
- 리마인드 푸시 `SCHEDULE_REMINDER` → 일정 채널, "제목 / 그룹 · 오늘 오후 5:10"(미리보기 숨김이면 제목 대신 "일정 알림"). 누르면 캘린더 탭 → 일정 상세.
  알림 탭 대상은 `PendingOpen`(채팅방·일정)으로 묶었다.
- 실기 확인(2026-11-18, 에뮬레이터 + 로컬 서버 main): 등록(매주, 참여자 2명) → DB 저장·회차 점, 겹침 안내, 12월 공휴일(25일·1/1) 빨간 날,
  10분 전 알림 17:00:09 수신 → 탭하면 상세, 삭제 후 캘린더로 복귀.
- 알게 된 점: 쿼리의 `+09:00`은 Retrofit이 `%2B09%3A00`으로 인코딩한다(테스트로 고정). Material 3 TimePicker 다이얼 색은 기본 테마 색 그대로다(Figma 없음).
- 미룬 것: 장소 연결(S14·15 → S15-1에서 함), "채팅에서 등록" 태그(S19), 주 보기·이번 회차만 수정·음력(v1.1), 할 일(S13-2).

### S13-2 메모 (할 일)

- 캘린더 탭 [할 일]: [전체] + 구성원 칩(담당자로 거름), "할 일 N"(기한 이른 순, 기한 없음은 뒤) → "완료한 일"(최근 완료 순) — 서버 순서 그대로.
  기한이 지난 안 한 일은 "…까지 · 지남"(status/danger). 100개씩, 끝에 가까워지면 다음 커서.
- 완료 체크는 구성원 누구나, 바로 화면에 반영하고 실패하면 되돌린다. 새로 완료하면 항상 `announce:true`(사용자 결정: 기본 켬)
  → 채팅방 시스템 메시지 "○○님이 '…' 할 일을 완료했어요." 되돌리기는 `announce`를 보내지 않는다.
- 추가·수정 바텀시트: 제목(100자), 담당자(기본 나, 같은 사람을 다시 누르면 없음), 기한(없음/날짜). 수정·삭제는 등록한 사람 또는 관리자 이상, 아니면 읽기만.
  PATCH는 보낸 키만 바뀌고 null은 지우기라 `JsonObject`로 직접 만든다(`TodoApi.toPatch`·`donePatch`).
- 409 `VERSION_CONFLICT`면 안내하고 목록을 다시 받는다.
- 실기 확인(2026-11-18): 추가 → DB 저장, 완료 → `done_at`·채팅 시스템 메시지(seq 21, payload `TODO_DONE`) 표시.
- 완료 시스템 메시지가 닉네임("1324님이")을 쓰던 문제 → 백엔드 PR #14에서 호칭 우선으로 수정됨(입장·퇴장 포함, 새 메시지부터 적용).
- 미룬 것: 장보기 목록(TODO-02, v1.1), 할 일 기한 알림(서버 없음).

### S15-1 메모 (장소: 검색·공유·목록·상세)

- 사용자 결정: S15를 S15-1(검색·공유·목록·상세·채팅 카드·일정 연결) → S15-2(카카오 지도·내 주변·별칭)로 나눔.
  리스트는 지역·공유한 사람·카테고리 필터를 한 화면에서 함께 건다(세그먼트 전환 대신). 카카오 지도 SDK(`com.kakao.maps.open:android` 2.15.2)는 승인됨, S15-2에서 추가.
- 장소 탭(SCR-31): "전체 지역 ▾" → 시·도/시·군·구 시트(개수 배지, 세종처럼 시·군·구 없는 곳은 "○○ 전체"), 공유한 사람 칩·카테고리 칩(여러 개),
  "지역 · N곳" 헤더(배지 값), 50개씩. 배지는 지역 필터를 받지 않아 지역만 바꿀 때는 다시 받지 않는다.
- 검색·공유(SCR-32): 입력 멈춤 400ms 또는 키보드 [검색], 15개씩 다음 쪽. 이미 공유한 곳은 "공유됨" → 상세.
  시트: 카테고리(서버 추천값이 기본)·별점(같은 별 다시 누르면 지움)·한줄평 100자·채팅방에도 알리기(기본 켬). 409/400(검색 1시간 지남)이면 안내하고 다시 검색.
- 상세: 다녀옴·가고 싶어요(바로 반영, 실패하면 되돌림, 누른 사람 이름), 주소·전화, 카카오맵에서 보기(placeUrl)·전화 걸기, 수정(카테고리·별점·한줄평, null은 지우기)·삭제는 공유한 사람·관리자 이상.
  404면 "삭제된 장소예요."(채팅 카드는 남으므로).
- 채팅 장소 카드: 서버 payload를 Room `payloadJson`에 보관(DB 버전 3, 지우고 다시 받음). 카드는 공유한 순간 값, 탭하면 상세. 채팅 탭 미리보기 "장소: ○○".
- 일정 연결: 일정 폼에 "장소" 칸(우리 그룹 장소에서 고르기·빼기). PATCH가 값 전체라 수정 때 기존 placeId를 다시 보낸다(테스트·실기로 확인). 상세에 장소 줄 → 장소 상세.
- 실기 확인(2026-11-19, 에뮬레이터 + 로컬 서버 main 3ebee79): 해주냉면 카드·상세·다녀옴(DB place_marks), 실제 카카오 검색 "CGV" → 공유(places id 2, 채팅 seq 24 PLACE),
  수정(ETC·별점 지움 → DB null), "서울 > 송파구 + 윤서" 1곳 / "+ E2E엄마" 0곳, 일정에 장소 연결 → 제목만 고쳐도 place_id 유지, 장소 삭제 → 일정 place_id null·카드 탭 시 "삭제된 장소".
- 남은 확인: 인수 기준(아빠 송파 2·강남 1)은 공유자가 한 명뿐이라 단위·UI 테스트로만 확인. "공유됨" 표시는 adb로 한글 입력이 안 돼 실기 미확인(단위 테스트로 확인).
- 미룬 것(S15-2): 지도 [지도|리스트], 내 주변(위치 동의 API — 백엔드 PR #16 머지됨, 403 LOCATION_CONSENT_REQUIRED), 별칭 장소(PLC-06).

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
| 2026-11-18 | S10-2 | 사진·동영상 보내기(Photo Picker 30개, WorkManager 업로드), 묶음·썸네일 표시, 전체 화면 보기·동영상 재생 | feat(CHAT-05) |
| 2026-11-18 | S11 | FCM 푸시: 토큰 등록, 알림 채널 4개·그룹 묶음·방해 금지 무음·미리보기 숨김, 알림 탭 → 채팅방, 알림 권한 안내(SCR-04), 읽음 표시 덮어쓰기 버그 수정 | feat(NOTI-01) |
| 2026-11-18 | S13-1 | 캘린더 탭(월·목록, 구성원 필터, 공휴일), 일정 등록·수정·상세·삭제, 반복·알림·겹침 안내, 일정 리마인드 푸시 | feat(SCH-01) |
| 2026-11-18 | S13-2 | 캘린더 탭 [할 일]: 목록·담당자 필터·완료 체크(채팅방 알림)·추가·수정·삭제 | feat(TODO-01) |
| 2026-11-19 | S15-1 | 장소 탭 목록(지역·공유한 사람·카테고리 필터), 검색·공유, 상세(다녀옴·가고 싶어요·수정·삭제), 채팅 장소 카드, 일정 장소 연결 | feat(PLC-01) |
