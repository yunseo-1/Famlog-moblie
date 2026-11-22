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
- 미룬 것(S15-2에서 함): 지도 [지도|리스트], 내 주변(위치 동의 API — 백엔드 PR #16 머지됨, 403 LOCATION_CONSENT_REQUIRED), 별칭 장소(PLC-06).

### S15-2 메모 (지도·내 주변·별칭)

- 사용자 결정: 장소 탭은 [지도 | 리스트]이고 처음은 지도. 별칭은 리스트 위 "우리 가족 별칭 N개 ›" 줄 → 별칭 화면. 위치 동의는 앱 안 동의 + 서버 기록(백엔드 PR #16).
- 카카오 지도 SDK `com.kakao.maps.open:android` 2.15.2 (arm64-v8a·armeabi-v7a만, 16KB 정렬 확인). 로그인과 같은 네이티브 키, 카카오 콘솔에서 **카카오맵 사용 설정** 필요.
  키가 비면(CI) 지도 대신 안내 문구. 릴리스는 R8 `optimization.packageScope`가 androidx·kotlin만이라 keep 규칙이 필요 없다.
- 지도(`KakaoMapView`): AndroidView + 화면 수명에 맞춘 resume·pause·finish. 핀 = category/place 알약 비트맵(`setApplyDpScale(false)` — 안 하면 SDK가 한 번 더 키움),
  내 위치 = 파란 점. 카메라 이동이 멈추면 300ms 뒤 화면 영역(sw·ne)으로 목록을 받는다. 지도는 스크롤 밖에 둬서 제스처가 목록에 먹히지 않게 했다.
- **SDK 로그**: 카메라 좌표(`newCenterPoint(lat, lng)`, `getMapPoint`)와 인증 헤더를 logcat에 찍는다 → `MapLogger`로 끈다(Java 플래그는 FamlogApp, 네이티브 스위치는 MapView 생성 전).
  좌표 로그는 0건 확인. 엔진 시작 때 인증 요청 줄 1개(앱 키, APK에 들어 있는 공개 값)는 스위치보다 먼저 찍혀 끌 수 없다.
- 내 주변: [내 주변] → 서버 동의 GET → (미동의·재동의 필요면 약관 창, 서버 currentVersion으로 PUT) → ACCESS_COARSE_LOCATION → 현재 위치 1회(`AndroidLocationSource`, 10초) → nearby 3km.
  404면 버튼 숨김, 403 LOCATION_CONSENT_REQUIRED면 약관 다시, 400(약관 바뀜)이면 새 버전으로 다시 묻는다. 좌표는 메모리에만(`Coordinates.toString` 숨김), 디버그 HTTP 로그는 쿼리를 가린다.
  약관 전문은 S22(요약 + 버전만 표시). 동의 철회 UI는 S20 설정(`ConsentRepository.withdrawLocation` 준비됨).
- 별칭: 가나다 목록, 추가 시트(별칭 30자 + 우리 장소 고르기 + 주소 글 200자), 409면 입력칸 오류, 삭제는 만든 사람·관리자 이상. 장소 고르기 시트(`PlacePickerSheet`)는 일정 폼과 함께 쓴다.
- `FamSegmented`를 디자인 시스템으로 올려 캘린더 [일정|할 일]도 같이 쓴다.
- 실기 확인(2026-11-19, 로컬 서버 main 69dde7b): 지도 타일·핀, 지도 이동 → "화면 안 0곳", 별칭 추가(place_id 1)·중복 409·삭제,
  내 주변: 약관 창(1.0) → consents 새 줄 → 대략적 위치 권한 창 → 송파구청 위치 파란 점·카메라 이동·해주냉면 2.2km, location_access_logs NEARBY 기록(좌표 없음).
- 에뮬레이터 위치 바꾸기: `adb emu geo fix`는 이 이미지(API 37)에서 반영되지 않았다. 대신
  `adb shell appops set com.android.shell android:mock_location allow` 후 `cmd location providers add-test-provider fused` / `set-test-provider-enabled fused true` /
  `set-test-provider-location fused --location 37.5110,127.1015` (getCurrentLocation 대기 중에 한 번 더 보내야 응답한다).
- 남은 확인: 릴리스 빌드에서 지도 실행(서명 S22 전이라 설치 못 함), 실제 폰에서 대략적 위치 정확도.

### S19-1 메모 (채팅 후보 카드·수정 시트)

- 사용자 결정: S19를 S19-1(채팅 카드·Resolved Chip·되돌리기·수정 시트·무시·실시간) → S19-2(제안함 SCR-14 + 원문 보기 ARC-06)로 나눔.
  카드 근거 인용은 앵커 메시지(마지막 근거) 본문을 쓰고, 원문 보기·수정 시트를 열 때만 상세(근거·spans)를 받는다. 제안함은 채팅방 상단 sparkle 아이콘(S19-2).
  장소 후보 시트는 서버가 찾은 곳(최대 3) + 시트 안 카카오 재검색.
- 카드는 채팅 메시지가 아니다(백엔드 결정, `type=CANDIDATE` 메시지 안 씀). `CandidateRepository`가 그룹별 후보를 메모리에 들고
  채팅방에 받아 둔 가장 오래된 seq부터 `status=PENDING,APPROVED`로 받는다. 이전 메시지를 더 받으면 그 범위만, 화면에 다시 돌아오면 다시 받는다.
  `candidate.created`·`candidate.resolved`는 `ChatSync`가 그룹 이벤트를 넘겨 반영한다.
- 채팅방에는 `surface=CARD`이면서 만료 전 PENDING과 APPROVED(칩)만. INBOX(0.5~0.75)는 제안함(S19-2).
- [등록]은 `{version}`만. 422 `CANDIDATE_MISSING_FIELDS`면 시트를 열고 `fieldErrors` 칸에 "AI 추정 · 확인 필요". 409 먼저 처리됨·만료는 서버 문구를 그대로 보이고 상세를 다시 받는다.
  VERSION_CONFLICT는 "그새 내용이 바뀌었어요". 같은 후보는 처리 중 다시 보내지 않는다.
- 시트 값: 일정은 `ScheduleDraft`(가족 전체 → 지금 가족 모두, 참석자 없으면 말한 사람, 별칭 장소에 연결이 없거나 글을 고치면 메모 "장소: …"),
  기념일은 다가오는 그날 매년 종일·가족 전원(음력·미리 알림 v1.1), 할 일은 담당자 1명, 장소는 `{kakaoPlaceId, category, oneLineReview}`. 빈 필수 칸이 있으면 [등록] 비활성.
- 칩 [되돌리기]: 승인한 사람·관리자 이상, 승인 후 5분(15초마다 다시 계산). 다른 사람이 처리한 결과 이벤트에는 시각이 없어 받은 때로 둔다.
  할 일은 상세 화면이 없어 칩에 [보기]가 없다. 되돌려서 지워진 시스템 메시지는 채팅방에서 뺀다(`buildChatRows`). 채팅 탭 미리보기는 마지막 메시지가 그것이면 아직 "삭제된 메시지".
- 원문 보기(같은 방): 상세의 근거 중 가장 앞선 seq로 스크롤(없으면 이전 메시지를 최대 20쪽 받음), spans를 3초 형광 강조(`textHighlight` 토큰, Figma 미정).
- 실기 확인(2026-11-20, 로컬 서버 main 470aba7, 실제 LLM `claude-haiku-5-5`): 그룹 1 AI 분석을 DB에서 켜고(기준점도 last_seq로) 사양서 6.3 예시 4문장을 user 2로 DB에 넣은 뒤
  앱에서 "Ok"를 보내 보조 분석(10분) → 후보 4개(일정 11/21 11:00·할 일 기한 11/22·장소 해주냉면·기념일 12/15), 카드 4종 표시.
  장소 [등록] → "우리 장소에 연결됨" 칩 + 시스템 메시지 → [되돌리기] → UNDONE·시스템 메시지 숨김·기존 장소 유지.
  할 일 [수정] → 담당자 윤서 → 등록(todos 새 줄, suggestion_feedback EDITED {assigneeId}). 기념일 [무시] → 틀린 내용(DISMISSED/WRONG).
  원문 보기 강조. DB로 다른 사람 승인을 흉내 낸 뒤 [등록] → "E2E엄마님이 먼저 등록했어요." + 칩. 일정 [등록] → schedules 11/21 11:00 참석 1·2 → [보기] 일정 상세. 끝나고 AI 분석은 다시 끔.
- adb로 한글을 입력할 수 없어(클립보드 명령도 없음) 위 메시지는 DB에 넣었다. 트리거(`onMessagePosted`)는 서버 전송 경로로 온 메시지만, 규칙 필터는 그 메시지 본문만 보므로
  DB에 넣은 문장은 필터를 거치지 않았고 영어 "Ok"는 걸리지 않아 보조 분석(10분)으로 돌았다.
- AR-1 다시 확인: 임시 계측 테스트(`createAndroidComposeRule<MainActivity>` + `performTextInput`, 커밋 안 함)로 "이번 토요일 11시에 할머니 댁 가자"를 실제 전송 경로로 보냄 →
  01:24:44 전송, 01:25:16 `ai_jobs` RULE 생성(32초: 멈춤 30초 + 5초 주기), 01:25:19 후보(11/21 오전 11:00, 추정 오전·오후·참석자, LLM 2.1초) → 35초 만에 카드. 1분 기준 통과.
  (같은 일정 후보가 APPROVED면 근거만 붙어 새 카드가 안 생기므로, 앞 테스트의 후보 4는 DB에서 UNDONE으로 바꾼 뒤 확인했다.)
- 실기에서 카드는 채팅방에 들어올 때 목록 API로 받은 것만 봤다. 채팅방을 연 채 `candidate.created`로 카드가 바로 붙는 것은 단위 테스트로만 확인.
- 남은 것: S19-2(제안함·원문 보기 ARC-06), 후보 [신고](MOD-01, S20), 기존 일정 수정·할 일 완료 후보(백엔드 다음 단계).

### S19-2 메모 (제안함·원문 보기)

- 사용자 결정: 원문 강조를 위해 백엔드에 `source.candidateId` 추가 요청(→ Famlog-BE PR #23, main `c20a039`). 할 일은 목록 태그 + 수정 시트 [원문 보기].
  제안함 배지는 대기 후보 수, 첫 쪽(50) 뒤에 더 있으면 "N+".
- `ItemSource(messageId, seq, candidateId?)`를 일정·회차·할 일·장소에. "채팅에서 등록" 태그(`ChatSourceTag`, 유형 색)는 캘린더 일정 줄·할 일 줄,
  [원문 보기](`ChatSourceRow`)는 일정 상세·장소 상세·할 일 시트. 누르면 `ChatRoomRoute(groupId, focusSeq, focusCandidateId)`.
  후보 ID가 있으면 상세의 근거 중 그 seq로 가서 spans 강조, 후보를 못 받거나 ID가 없으면 원문 메시지 전체를 강조.
- 등록·무시·되돌리기·수정 시트를 `CandidateActions`로 빼서 채팅방(`CandidateViewModel`)과 제안함(`CandidateInboxViewModel`)이 함께 쓴다.
- 제안함(SCR-14): `status=PENDING` 최신 순 커서(50). 첫 쪽을 다시 받으면 그 범위에서 사라진 대기 후보는 뺀다. 근거 메시지를 폰에 받아 두었으면 인용, 아니면 "원문 보기"만.
  [원문 보기]는 제안함을 닫고 원래 채팅방 back stack 항목의 `savedStateHandle["inboxPickedCandidateId"]`로 넘겨 강조(채팅방을 새로 쌓지 않는다).
  키 이름을 라우트 인자(`focusCandidateId`)와 같게 두면 savedStateHandle이 라우트 인자를 읽어 null 대신 0이 나온다(`/candidates/0` 404 → 전체 강조로 가려짐). 실기에서 찾아 고침.
- 실기 확인(2026-11-20, 로컬 서버 `470aba7` — candidateId 전): 캘린더 11/21 "채팅에서 등록" 태그 → 일정 상세 [원문 보기] → 채팅방 근거 메시지 전체 강조,
  할 일 줄 태그·시트 [원문 보기] → 근거 메시지 강조, 앱바 sparkle 배지 1 → 제안함 카드 → [원문 보기] → 채팅방 복귀·spans 강조.
- 실기 확인(2026-11-20, 로컬 서버 `c20a039`): 할 일 시트 [원문 보기] → `GET /candidates/5` → 근거 spans 강조. 제안함 [원문 보기] → `GET /candidates/8` → 강조.
  일정 6은 근거 후보 4가 UNDONE(AR-1 재확인 때 DB로 바꿈)이라 서버가 candidateId=null → 메시지 전체 강조(의도대로).
- 남은 확인: 장소 candidateId 경로(같은 코드, 실기 데이터 없음), 신뢰도 0.5~0.75(INBOX) 후보가 채팅방엔 없고 제안함에만 보이는 것(단위·UI 테스트만).

### S19-3 메모 (기존 일정 변경·할 일 완료 후보)

- 백엔드 Famlog-BE PR #24(main `ec905d9`): 후보 응답에 `target {entityType, action: CHANGE|COMPLETE, entityId, title, createdBy?, before, after?, choices}`, 승인은 `{version, targetId?}`만.
  반복 일정은 대상에서 빠지고(60일 안 단일 일정만), 대상이 여럿(최대 5)이면 `choices`에서 고른다. 409 `CANDIDATE_TARGET_CHANGED`·403 `GROUP_ROLE_REQUIRED`면 후보는 PENDING 그대로.
- 사용자 결정: 버튼 [바꾸기]·[완료](+[무시], [수정] 없음 — 승인 전 값 고치기는 v1.1), 대상 고르기는 카드 안 라디오 목록, 변경 전·후는 한 줄 화살표(바뀐 부분만, 전은 취소선·후는 굵게).
- `CandidateTarget`·`TargetItem`·`ScheduleTiming`(모델), `toTarget()`(파싱). 읽지 못한 target(모르는 action·대상 없음)이면 후보를 뺀다 — 새 항목 카드로 잘못 보이지 않게.
- 권한: 일정 변경은 `createdBy == 나` 또는 ADMIN·OWNER만 [바꾸기], 아니면 "등록한 사람이나 관리자만 바꿀 수 있어요". 여럿이면 바꿀 수 없는 일정은 비활성. 할 일 완료는 누구나.
  화면 상태에 `CandidateViewer(userId, role)`, 고른 대상은 `CandidateActions.pickState`(후보 ID → entityId).
- 칩: "일정을 바꿨어요 · 엄마 · 보기"(일정 상세) / "할 일을 완료했어요 · 엄마". 되돌리기 안내: "일정을 원래대로 되돌렸어요." / "할 일을 다시 열었어요."
- 실기 확인(2026-11-20, 로컬 서버 `ec905d9`, 실제 LLM, AI 분석은 확인 뒤 다시 끔): 앱 전송 경로로 "자동차 점검 맡겼어"·"할머니 댁 11시 말고 12시로 하자" → 약 35초 뒤
  완료 후보(할 일 2)·변경 후보(일정 6, 11:00 → 12:00) 카드. [바꾸기] → 일정 12:00·칩·시스템 메시지 → [보기] 상세 12:00 → [되돌리기] 11:00 복귀.
  [완료] → 할 일 done_at·칩·시스템 메시지 → [되돌리기] 다시 열림.
- 남은 확인: 대상이 여럿인 카드·권한 없는 안내·409(단위·UI 테스트만), 다른 기기에서 처리한 candidate.resolved 칩(S19-1과 같은 경로).

### S20-1 메모 (AI 분석 안내·동의, SET-01)

- 백엔드 Famlog-BE PR #25(main `781a3b7`): `GET /ai-notice`, `GET/PATCH /groups/{id}/settings`(꺼진 그룹을 켤 때 `aiNoticeVersion` 필수),
  `GET/PATCH /groups/{id}/members/me/preferences`(`aiOptOut`), `PUT /users/me/consents/AI_ANALYSIS`(국외 이전 고지 함께 기록), 그룹 상세 `members[].aiOptOut`(관리자만 값).
  `aiNoticePending` = 켜짐 && 현재 버전 미확인(사람 단위) && 이 그룹 분석 제외 아님 — 서버가 계산. 설정 변경 WebSocket 이벤트 없음.
- 사용자 결정: 동의 내역(SCR-81)·전체 철회는 S20-3·4와 함께, 안내 시트를 버튼 없이 닫으면 앱을 켜 둔 동안 그 그룹은 다시 안 띄움(`GroupSettingsRepository.skipNotice`, 메모리만),
  켜기 전 안내는 구성원 안내와 같은 바텀시트(`AiNoticeSheet`, 모드 ENABLE·CONFIRM·VIEW).
- `feature/settings` 추가: 채팅방 서랍 → [그룹 설정](`GroupSettingsRoute`) — 대화 분석 켜기·끄기(관리자, 켤 때 안내 → [동의하고 켜기]), 내 메시지 분석 제외(모두),
  AI 분석 안내 보기, 구성원별 분석 제외(관리자만, 보기 전용). 채팅방에 들어올 때 `AiNoticePromptViewModel`이 설정을 받아 안내 시트([확인]/[내 메시지 분석 제외]).
- 안내 버전이 그사이 바뀌면(400, field `aiNoticeVersion`·`docVersion`) 새 안내를 받아 다시 보여 준다.
- 실기 확인(2026-11-20, 로컬 서버 `781a3b7`, 메시지 전송 없음 → LLM 호출 없음): 서랍 → 그룹 설정 → 대화 분석 켜기 → 안내 시트 → [동의하고 켜기] → 켜짐·AI_ANALYSIS·OVERSEAS_TRANSFER 1.0 기록.
  미확인 상태(DB에서 내 확인 기록을 철회 처리) → 채팅방 안내 시트 → 닫기 후 다시 들어와도 안 뜸 → 앱 재시작 시 다시 뜸 → [내 메시지 분석 제외] → aiOptOut=true
  → 그룹 설정에서 제외 끄기 → 재시작 → [확인] → 확인 기록. 관리자 구성원 목록이 내 제외 변경을 따라감(실기에서 발견해 고침). 끝에 AI 분석 끔.
- 서버 안내 문구의 거부 방법을 앱 경로("채팅방 메뉴(≡) > 그룹 설정")로 고침 → Famlog-BE PR #26(main `2f40614`), 안내 버전은 1.0 그대로(S22 보관 문구 확정 때 1.1).
- 남은 확인: 다른 구성원 계정에서의 안내 시트(실기는 그룹장 계정으로 미확인 상태를 만들어 확인), 안내 버전이 바뀌는 400 경로(단위 테스트만).

### S20-2a 메모 (신고, MOD-01·SCR-82)

- 백엔드 Famlog-BE PR #27(main `355d99e`): `POST /reports {targetType, targetId, groupId?, reason, detail?}` → 201. 처리 전 같은 대상 재신고는 같은 신고, 처리 결과 알림 없음.
  관리자 조치는 기존 `message.deleted`·`candidate.resolved`(DISMISSED) 이벤트로 온다 → 앱 추가 처리 없음.
- 사용자 결정: S20-2를 신고(2a) → 앱 설정·강제 업데이트(2b)로 나눔, 구성원(USER) 신고는 GRP-05 구성원 관리 화면에서, 선택 업데이트·공지는 앱 시작 대화상자(2b).
- `feature/report`: `ReportViewModel`(대상별 시트 상태·접수 안내) + `ReportSheet`(SCR-82: 사유 라디오, AI 결과에만 "AI 제안이 틀렸어요", 기타는 내용 필수, 500자).
  여는 곳: 채팅방 메시지 길게 누르기 [신고](MESSAGE, 다른 사람이 보낸 메시지만), 사진·동영상 전체 화면 [신고](MEDIA, 다른 사람이 보낸 것만), AI 후보 카드 "AI 제안" 줄 [신고](AI_OUTPUT, 채팅방·제안함).
- 실기 확인(2026-11-20, 로컬 서버 `acce4eb`): 메시지 29 스팸 · AI 후보 12 "AI 제안이 틀렸어요"(확인 동안만 DB로 PENDING) · 사진 9 기타(내용 없으면 필수 안내 → 내용 넣어 접수) → `reports` OPEN 3건,
  "신고가 접수됐어요. 확인 후 조치할게요." 안내. 같은 사진 재신고 → 201, 신고 수 그대로.
- 남은 확인: 관리자 도구 신고 목록 표시(관리자 토큰 필요, DB로 대신 확인), 관리자 조치 뒤 카드·메시지 사라짐(기존 이벤트 경로).

### S20-2b 메모 (앱 설정·강제 업데이트, SCR-00)

- 백엔드 Famlog-BE PR #27: `GET /app/config`(로그인 없음) → `{minSupportedVersion, latestVersion, notice, noticeUpdatedAt, features{nearby, photoLocation, aiAnalysis}}`,
  `X-App-Version`이 최소 지원 버전보다 낮으면 /app/config를 뺀 모든 API가 426. 버전 비교는 자리별 숫자·빠진 자리 0·꼬리 무시(`AppVersions`, 서버와 같은 규칙).
- 시작: `StartResolver`가 앱 설정을 먼저 받는다(실패해도 막지 않음) → 최소 버전 미만이면 `ForceUpdateRoute`. 실행 중 426은 `UpgradeRequiredInterceptor` → `SessionEvent.UpgradeRequired` → 스택 비우고 강제 업데이트.
  강제 업데이트 화면은 [업데이트](Play 스토어)만, 뒤로가기는 앱 종료.
- 사용자 결정: 공지·선택 업데이트는 앱 시작 대화상자. 탭 화면에 처음 왔을 때 알림 권한 안내 뒤에 — 새 공지(`noticeUpdatedAt`이 `AppPrefs`에 저장한 값과 다를 때만) → [확인]하면 저장 →
  최신 버전이 더 높으면 "새 버전이 있어요 [업데이트]/[나중에]"(앱을 켤 때마다 한 번).
- 기능 스위치: `nearby=false`면 장소 지도 [내 주변] 숨김, `aiAnalysis=false`면 그룹 설정에 "AI 분석이 잠시 멈춰 있어요. 설정은 그대로 저장돼요."(스위치는 그대로 바꿀 수 있음). `photoLocation`은 받기만(관련 화면 없음).
- 실기 확인(2026-11-21, 로컬 서버 `acce4eb`, DB `app_versions`·`feature_flags`를 바꿨다가 원래대로 되돌림): 최소 9.0.0 → 시작 시 강제 업데이트 / 실행 중 9.0.0 → 캘린더 탭 요청 426 → 강제 업데이트,
  최신 1.1.0+공지 → 공지 → [확인] → 선택 업데이트 → 재시작하면 공지 없이 선택 업데이트만, `place.nearby`·`ai.analysis` 끔 → [내 주변] 숨김·그룹 설정 안내 → 되돌리면 버튼 복귀.
- 남은 확인: 실제 Play 스토어 이동(아직 스토어 등록 전, S22).

### GRP-05 메모 (구성원 관리·그룹 나가기·삭제)

- 서버 API(S5 그대로): `PATCH /groups/{id}/members/{userId} {role}`(OWNER는 위임, 이전 그룹장은 ADMIN), `DELETE /groups/{id}/members/{userId}`, `POST /groups/{id}/leave`
  (다른 구성원이 있는 그룹장 409 `OWNER_TRANSFER_REQUIRED`, 혼자인 그룹장이면 그룹 삭제 예정), `DELETE /groups/{id}`(그룹장, 30일 뒤 파기). 삭제 예정 계정에 위임하면 400(S20-3).
- 사용자 결정: 채팅방 서랍 구성원 줄 → 구성원 시트, 그룹 나가기·삭제는 그룹 설정 맨 아래, 그룹 삭제 포함(이름 입력 확인).
- `memberActions(me, myRole, target)`: 권한 변경·내보내기는 관리자 이상(그룹장 대상 X), 그룹장 넘기기는 그룹장만, 신고는 누구나, 나 자신은 없음 (사양서 2.2와 같은 기준).
  그룹장 넘기기·내보내기는 확인 대화상자. 처리 뒤 그룹 상세·목록을 다시 받아 서랍·역할이 바로 바뀐다. 구성원 신고(USER)는 S20-2a 신고 시트.
- 나가기·삭제 성공 → `GroupRepository`가 목록에서 빼고 현재 그룹이었으면 비움 → `StartRoute`로 스택을 비우고 다시 고름(다른 그룹 또는 그룹 시작).
- 실기 확인(2026-11-21, 로컬 서버 `acce4eb`): 우리가족에서 엄마 관리자로 → 시트·서랍 태그 갱신, 내보내기 확인 대화상자(취소), 그룹장 넘기기 → 엄마 OWNER·나 ADMIN·엄마 시트는 [신고하기]만
  → 구성원 신고 접수(USER 2) → DB로 역할 원복. 시험용 그룹 E2ELeave(혼자) 나가기 → 경고 → 204 → 우리가족으로, 그룹은 PENDING_DELETION.
  E2EDelete 삭제 → 이름 일부면 요청 안 감 → 이름 맞으면 204 → 우리가족으로.
- 남은 확인: 실제 내보내기(다른 계정이 엄마뿐이라 확인 대화상자까지만, 단위·UI 테스트), 다른 가족이 있는 그룹장의 나가기 안내(단위·UI 테스트). 시험용 그룹 4·5는 PENDING_DELETION으로 남음(30일 뒤 파기).

### S20-3 메모 (설정·개인정보·계정 삭제·복구, SCR-80·81·ACC-01)

- 백엔드 Famlog-BE PR #28(main `acce4eb`): `DELETE /users/me {deleteMedia}` → 204(모든 기기 로그아웃), 그룹장 409 `OWNER_TRANSFER_REQUIRED`(메시지에 그룹 이름),
  유예 중 로그인 403 `ACCOUNT_PENDING_DELETION` → 같은 소셜 토큰으로 `{..., restore:true}`. 웹 `/account-deletion`. 로그아웃 `POST /auth/logout {refreshToken}`(이 기기만).
- 사용자 결정: 설정(SCR-80)은 채팅 탭 앱바 오른쪽 톱니바퀴, 이번 범위는 계정 삭제 중심(알림 SET-02·로그인 기기 AUTH-03·내보내기 S20-4는 다음), 삭제 확인은 안내 화면 + 최종 대화상자.
- 설정: 개인정보·보안, 로그아웃(확인 → 서버 실패해도 기기 정리: 토큰·채팅 캐시 삭제 + `SessionEvent.LoggedOut`), 앱 버전.
  개인정보·보안(SCR-81): 동의 내역(약관·개인정보·위치·AI 분석·국외 이전 순, 위치·AI 분석만 [철회] + 확인), 웹 계정 삭제 안내(브라우저로 서버 루트 `/account-deletion`), 계정 삭제.
- 계정 삭제: 안내 5줄 + "내가 올린 사진·동영상도 지우기" + [계정 삭제] → 최종 확인 → 204면 기기 정리 후 로그인 화면, 409면 서버 문구 + "채팅방 메뉴(≡)에서 가족을 눌러 그룹장을 넘길 수 있어요".
  `deleteMedia`는 false여도 항상 보낸다(기본값으로 두면 직렬화에서 빠져 `{}`가 나가던 것을 테스트로 잡음).
- 로그인 복구: 403이면 서버 문구(복구 기한) + "계정을 복구하고 로그인할까요?" → [복구하고 로그인]은 같은 소셜 토큰(메모리에만)으로 restore=true.
- 구성원 목록에 없는 보낸 사람은 "나간 구성원"(서버가 "탈퇴한 구성원" 닉네임을 주면 그대로).
- 실기 확인(2026-11-21, 로컬 서버 `acce4eb`): 채팅 탭 ⚙ → 설정 → 개인정보·보안(동의 5개, 순서 정렬은 실기에서 보고 고침) → AI 분석 철회 대화상자(취소) → 웹 안내 페이지 크롬으로 열림(앱 경로 문구 일치)
  → 계정 삭제 → 최종 확인 → 409 그룹장 안내, 계정 ACTIVE 그대로 → 로그아웃 확인 대화상자(취소).
- 실제 계정 실기 확인(2026-11-21, 사용자가 시험용 Google 계정으로 로그인): 윤서 로그아웃(204, 이 기기 세션만 폐기) → 시험 계정 가입 → 초대 코드로 우리가족 참여·메시지 1개 → 그룹 나가기(left_at)
  → 그룹 시작 화면 ⚙ → 계정 삭제(사진 지우기) 204 → PENDING_DELETION·활성 세션 0·로그인 화면 → 같은 계정 로그인 403 → 복구 대화상자 → restore 200 → ACTIVE
  → 다시 삭제(사진 지우기 안 함) 204로 정리 → 카카오로 윤서 복귀 → 시험 계정 메시지가 "나간 구성원"으로 보임. 시험 계정(사용자 5)은 PENDING_DELETION(30일 뒤 파기).
- 실기에서 찾아 고친 것: 그룹이 없는 사용자는 설정(계정 삭제)에 닿을 수 없었다 → 그룹 시작 첫 화면에도 ⚙. 복구 대화상자 제목·본문이 같은 문장으로 겹침 → 제목 "계정을 복구할까요?".
  나간 구성원 아바타가 "나"(나 자신으로 읽힘) → "?".
- 남은 확인: "탈퇴한 구성원"(30일 뒤 서버 파기 후).

### S20-4 메모 (내 데이터 내보내기, ACC-02)
- 백엔드 main `da8d1ce`: `POST /users/me/data-exports`(본문 없음, 202, 진행 중이면 같은 요청, 준비된 지 24시간 안이면 429 `RATE_LIMITED`·FAILED는 제한에 안 셈),
  `GET /users/me/data-exports`(최근 5개, 링크 없음), `GET /{id}`(READY일 때만 10분짜리 `downloadUrl`·`fileName`, 다른 사람 것 404). 7일 뒤 EXPIRED.
  준비 알림: 푸시 `DATA_EXPORT_READY`(`exportId`, SYSTEM 채널) + STOMP `/user/queue/events` `data-export.ready`. 만들다 실패하면 알림 없음.
- ZIP은 "내 것"만(백엔드 확인): 내가 보낸 메시지·내가 올린 미디어·내가 만들었거나 참석/담당인 일정·할 일·내가 공유한 장소와 내 표시. 다른 가족 정보 없음.
  예외: 나에게 배정된 일정·할 일을 다른 가족이 만들었으면 그 제목·메모가 들어간다 → 개인정보처리방침(S22)에 적을 것.
- 앱: 개인정보·보안 > [내 데이터 내보내기](`DataExportScreen`). 들어올 때 목록 첫 요청으로 상태 복원(없음·만료 → 요청 / 준비 중 / 준비됨 → 내려받기 / 실패 → 다시 요청).
  준비 중이면 화면이 보이는 동안(STARTED) 10초마다 다시 조회(실패는 알림이 없어서), STOMP 이벤트가 오면 바로 갱신. 링크는 누를 때 새로 받고 화면 상태·로그에 두지 않는다.
  내려받기: Android 10+ 시스템 DownloadManager('다운로드' 폴더, 권한 없음), 8·9는 저장소 권한을 묻지 않으려고 브라우저(사용자 결정).
  푸시 알림을 누르면 `OpenTarget.DataExport` → 시작 분기 뒤 내보내기 화면.
- 실기(2026-11-22, 윤서): 요청 202 → 준비 중 → 홈에 있는 동안 1분 안에 READY·푸시 알림 → 알림 눌러 화면(4.7 kB, 7일 기한) → ZIP 9개 파일 확인(메시지에 보낸 사람 키 없음, 일정·할 일은 createdByMe·participant/assignedToMe만).
- **로컬 한계**: Android 17 에뮬레이터에서 시스템 DownloadManager가 로컬 저장소(`10.0.2.2:8333`)에 붙지 못한다(`failed to connect`, 시스템 다운로드 앱에 로컬 네트워크 권한이 없음).
  운영 S3는 공인 주소라 해당 없음. 실제 내려받기는 S22 스테이징(공인 주소)에서 다시 확인.

### MEDIA_UNAVAILABLE 다시 올리기 (백엔드 S20-5-1, main `a5697a0`)
- 서버 정리 배치가 메시지에 붙지 않은 지 24시간 지난 업로드를 지운다. 그 media id로 보내면 `/user/queue/events` error `MEDIA_UNAVAILABLE`(REST는 409)과 `clientMessageId`가 온다.
  형식 오류(개수·중복·글과 함께)는 그대로 `VALIDATION_FAILED`.
- 앱: `ChatRepository.onUserEvent`가 `MEDIA_UNAVAILABLE`이면 그 메시지의 media id를 비우고, 처음이면 원본으로 다시 올려 같은 clientMessageId로 보낸다(`reuploaded`, 앱을 켜 둔 동안만).
  또 실패하면 FAILED, [다시 보내기]는 업로드부터. 원본은 사진 고를 때 붙잡아 둔 읽기 권한으로 다시 읽는다.
- 실기(2026-11-23): 외가에서 사진을 고르고 바로 홈 → 업로드만 끝나고 전송 보류(media 13) → 행 삭제 → 앱 복귀 시 media 14로 다시 올려 메시지 45 전송.

### AUTH-03 메모 (로그인 기기 관리, SCR-81)
- API: `GET /users/me/sessions`(최근 사용 순, `current`), `DELETE /users/me/sessions/{id}`(204, 그 기기의 다음 호출부터 401, 내 것이 아니면 404).
- 앱: 개인정보·보안 > [로그인 기기](`SessionsScreen`). 이 기기는 맨 위 "이 기기" 표시만(로그아웃은 설정), 다른 기기는 확인 대화상자 뒤 [로그아웃].
  기기 이름이 없으면 "Android 기기"/"iPhone". 마지막 접속은 방금 전·n분 전·n시간 전·날짜. 404면 목록을 새로 받는다. "다른 기기 모두 로그아웃"은 없음(사양·API 없음).
- 새 기기 로그인 알림(NEW_DEVICE_LOGIN)을 누르면 로그인 기기 화면(`OpenTarget.Sessions`).
- 실기(2026-11-23, 윤서): 목록 5개(앱을 다시 깔며 남은 에뮬레이터 세션들) → 세션 8 로그아웃 DELETE 204·DB revoked_at → 목록에서 빠짐. 알림 탭 인텐트로 화면 이동 확인.
  로그아웃당한 기기 A 쪽(다음 호출 401 → 로그인 화면)은 기기 하나라 실기 못 함 → 기존 `TokenAuthenticator`·`SessionEvents` 테스트로 갈음.
- 서버 세션의 `lastUsedAt`은 토큰 재발급 때 갱신되는 것으로 보인다(이 기기도 "n분 전"). 

### SET-02 메모 (알림 설정, SCR-80)
- API(S11에 이미 있음): `GET·PATCH /users/me/notification-settings {previewHidden, quietHoursEnabled, quietStart, quietEnd}`(보낸 값만, 시작=끝 400),
  그룹 알림 끄기 `PATCH /groups/{id}/members/me/preferences {notifMuted}`. 걸러내기·본문 빼기·무음 표시는 서버가 하고 앱은 `silent`만 따른다(S11).
- 앱: 설정 > [알림](`NotificationSettingsScreen`): 미리보기 숨기기, 방해 금지(켜면 시작·끝 시각, Material TimePicker), 그룹 알림(내 그룹 전체 스위치, 사용자 결정), [기기 알림 설정 열기](채널별).
  바꾸면 바로 저장, 실패하면 되돌리고 서버 문구. 시작=끝은 앱에서 막는다. 시각은 하나만 바꿔도 시작·끝을 함께 보낸다.
- 실기(2026-11-23, 윤서): 미리보기·방해 금지·외가 끄기·시작 23:00 → 각 PATCH 200·DB 반영, 끝=시작이면 안내만(요청 없음). 끝나고 DB를 원래 값으로 돌림.
  그룹을 끈 뒤 실제로 푸시가 안 오는지는 다른 구성원이 보내야 해서 못 봄(서버 테스트로 확인된 동작).

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
| 2026-11-19 | S15-2 | 장소 탭 [지도·리스트]: 카카오 지도·화면 영역 핀, 내 주변(위치 약관 동의·대략적 위치), 별칭 장소, FamSegmented | feat(PLC-04) |
| 2026-11-20 | S19-2 | 제안함(SCR-14, 앱바 sparkle·대기 배지), 원문 보기(ARC-06: 일정·할 일·장소 "채팅에서 등록" 태그 → 채팅방 근거 강조), CandidateActions 공통화 | feat(ARC-06) |
| 2026-11-20 | S19-3 | 기존 일정 변경·할 일 완료 후보(ARC-04·06.4): [바꾸기]·[완료], 변경 전→후, 대상 고르기, 일정 수정 권한 안내, 칩·되돌리기 문구 | feat(ARC-05) |
| 2026-11-20 | S20-1 | AI 분석 안내·동의(SET-01): 채팅방 서랍 → 그룹 설정(대화 분석 켜기·끄기, 내 메시지 분석 제외, 구성원별 제외 보기), 켜기 전 안내, 채팅방 안내 시트 | feat(SET-01) |
| 2026-11-20 | S20-2a | 신고(MOD-01·SCR-82): 메시지·사진/동영상·AI 후보 카드 [신고], 사유 선택 시트(AI 결과 전용 사유, 기타 필수 내용) | feat(MOD-01) |
| 2026-11-21 | S20-2b | 앱 설정·강제 업데이트(SCR-00): 시작 시 최소 버전·426 → 강제 업데이트 화면, 공지·선택 업데이트 대화상자, 기능 스위치(내 주변·AI 분석) | feat(SCR-00) |
| 2026-11-21 | GRP-05 | 구성원 관리: 서랍 구성원 시트(권한 바꾸기·그룹장 넘기기·내보내기·신고), 그룹 설정의 그룹 나가기·삭제(이름 확인) | feat(GRP-05) |
| 2026-11-21 | S20-3 | 설정(SCR-80)·개인정보·보안(SCR-81: 동의 내역·철회, 웹 삭제 안내), 로그아웃, 계정 삭제(사진 지우기 선택·그룹장 안내), 로그인 복구, 나간 구성원 표시 | feat(ACC-01) |
| 2026-11-22 | S20-4 | 내 데이터 내보내기(ACC-02): 개인정보·보안 > 요청·준비 중·내려받기(시스템 다운로드/9 이하 브라우저)·다시 요청, 준비 알림(푸시·실시간) | feat(ACC-02) |
| 2026-11-23 | S20-5-1 앱 | 붙일 수 없는 사진·동영상(MEDIA_UNAVAILABLE)이면 media id를 버리고 원본으로 한 번 다시 올려 보내기 | fix(CHAT-05) |
| 2026-11-23 | AUTH-03 앱 | 로그인 기기 관리: 개인정보·보안 > 로그인 기기(이 기기 표시, 다른 기기 원격 로그아웃), 새 기기 로그인 알림 → 화면 | feat(AUTH-03) |
| 2026-11-23 | SET-02 | 알림 설정: 설정 > 알림(미리보기 숨기기, 방해 금지 시간, 그룹별 알림 끄기, 기기 알림 설정 열기) | feat(SET-02) |
| 2026-11-20 | S19-1 | 채팅 후보 카드(근거 인용·추정 배지·AI 제안), Resolved Chip·5분 되돌리기, 수정 시트(SCR-13), 무시 사유, candidate.* 실시간, 원문 강조, 지운 시스템 메시지 숨김 | feat(ARC-05) |
