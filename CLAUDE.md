# CLAUDE.md — FamLog (팸로그)

> Claude Code가 이 저장소에서 작업할 때 가장 먼저 읽는 파일이다.
> 응답과 커밋 메시지, 문서는 **한국어**로 작성한다. 코드 식별자는 영어.

## 1. 프로젝트 한 줄 요약

가족 전용 단체 채팅 + 구성원별 일정 + 맛집·장소 + 앨범을 하나로 묶고,
**채팅에서 일정·기념일·할 일·장소를 AI가 인식해 "등록 후보"를 만들어 주는 Android 가족 앱**.
Google Play 출시가 목표다 (v1.0 목표: 2027년 2월 중순).

## 2. 문서 우선순위 (충돌 시 위가 이긴다)

1. **통합 사양서 v2.0** — 개발 기준 문서. 기능 ID, API, 데이터, 보안, 인수 기준이 모두 여기 있다.
   - 로컬 사본: **`docs/spec/`** (장별 Markdown 21개). 색인·"기능 ID → 읽을 곳" 표는 `docs/spec/README.md`
   - 이 폴더는 사본이다. **여기서 직접 고치지 않는다.** 원본은 문서 레포(`yunseo-1/Famlog`)의 `docs/spec/`이고,
     사양 변경은 Notion(page id `416fc8bd-3e5e-8128-aab8-dc6d43bbd7f2`) → 문서 레포 → `scripts/sync-spec.sh` 순서로 반영한다.
   - 사양과 다르게 구현해야 하거나 사양이 애매하면 **구현 전에 질문**한다. 추측해서 채우지 않는다.
   - 경로 읽는 법: 사양서의 `famlog-mobile/` = 이 저장소. 사양서 13.3은 `core/*`, `feature/*`를 Gradle 모듈로 그렸지만, 지금은 `:app` 안의 **패키지**로 나눈다(5장). `../docs/...` 경로는 문서 레포(`yunseo-1/Famlog`)를 뜻한다.
2. **Figma 화면 지도** — `docs/design/figma-map.md` (노드 ID, 화면 구조, 컴포넌트, 토큰)
3. **ERD 초안** (Notion page id: `40dfc8bd-3e5e-81f6-b5e9-e8e2a5e9c7dc`) — 참고용. 사양서 10장(데이터 구조)이 우선.
4. 인수인계 문서 — `docs/handoff/HANDOFF.md` (결정 로그, 알려진 불일치, 다음 작업)

## 3. 절대 규칙

- **AI는 임의로 등록하지 않는다.** 채팅 인식 결과는 `registration_candidates`(PENDING)에만 저장하고,
  실제 일정·할 일·장소는 `POST /api/v1/candidates/{id}/approve`에서만 만든다. (사양서 06)
  절차: 탐지 → 등록 후보 생성 → 근거 메시지 표시 → 사용자 확인/수정 → 승인 후 등록.
- **모든 그룹 데이터 접근은 `GroupAccessChecker`를 거친다.** 그룹 경로에 비구성원이 요청하면 403,
  다른 그룹 리소스 ID로 직접 요청하면 404(존재 여부 숨김). 테스트 필수. (사양서 09 §9.3)
- **1:1 채팅은 없다.** 가족 그룹당 단체 채팅방 1개 (`chat_rooms.group_id` UNIQUE).
- **현재 가족 그룹**: 캘린더·장소·앨범·이야기 탭은 현재 그룹 데이터만. 채팅 탭만 전체 그룹 목록.
- **날짜 계산·구성원 매칭·장소 확정은 서버 코드가 한다.** LLM은 원문 표현만 추출한다.
- 시간은 DB에 UTC로 저장, API 응답은 `+09:00` ISO-8601. 날짜 해석 기준은 근거 메시지 **발송 시각**(Asia/Seoul).
- 메시지 본문·토큰·좌표·출생연도월 등 개인정보를 로그에 남기지 않는다. LLM 프롬프트·응답 원문도 저장하지 않는다. (사양서 08, 09)
- AI 결과(후보 카드·스토리·영상)에는 "AI 제안/AI가 쓴 초안" 표시와 앱 안 [신고]를 둔다. (사양서 07 §7.2)
- 그룹 AI 분석이 꺼졌거나 개인이 분석 제외한 메시지는 LLM으로 보내지 않는다. (사양서 06 AR-10)
- 스키마 변경은 **Flyway 마이그레이션 파일**로만. 직접 DDL 금지.
- 새 라이브러리 추가 전에 이유를 적고 사용자에게 확인받는다.
- 앱 UI는 `docs/design/` 의 토큰·컴포넌트 이름을 따른다. 임의 색상(hex) 하드코딩 금지 → `FamColors.*`.

## 4. 기술 스택 (사양서 11장)

| 영역 | 선택 |
|---|---|
| 앱 | Kotlin, Jetpack Compose, MVVM + Hilt, Retrofit/OkHttp, STOMP, Room, Coil, Kakao Map SDK, Photo Picker, FCM. minSdk 26 / **targetSdk 37** (compileSdk 37) |
| 서버 | Java 21, Spring Boot 3, Spring Security(JWT), WebSocket(STOMP), JPA + QueryDSL, Flyway |
| 워커 | Python 3.12, FastAPI, Redis Streams consumer (AI 인식·미디어·영상) |
| 데이터 | PostgreSQL 16 + PostGIS + pgvector, Redis 7, S3(로컬은 MinIO) |
| 인프라 | Docker Compose (MVP), GitHub Actions |

## 5. 저장소 구조

**이 저장소(`Famlog-moblie`)는 Android 앱 전용이다.** 서버·워커·인프라는 백엔드 저장소(`yunseo-1/Famlog-BE`)에 있다.
저장소 루트가 Android Studio 프로젝트이고, Gradle 모듈은 `:app` 하나다.
API는 문서 레포의 `docs/api/openapi.yaml` 계약을 따르고, 계약에 없는 필드를 만들어 쓰지 않는다.

```
Famlog/
├ app/                 Android 앱 (단일 모듈, 패키지 com.famlog)
│  └ src/
│     ├ main/java/com/famlog/
│     │  ├ FamlogApp.kt, MainActivity.kt   Hilt 진입점
│     │  ├ navigation/    시작 분기(StartRoute), 하단 탭 5개, 그룹 전환 시트, 세션 만료 시 로그인 이동
│     │  ├ core/model/    순수 Kotlin 모델 (AuthTokens, ApiError, ErrorCodes, ApiResult, Group·Member)
│     │  ├ core/common/   디스패처·앱 범위 CoroutineScope·Clock(서울)·기기 이름·네트워크 복구 알림
│     │  ├ core/datastore/  TokenStore(Keystore AES-GCM 암호화), SessionStore(사용자 ID·현재 그룹), DeviceIdProvider
│     │  ├ core/data/     Repository(Auth, Group, Chat, PushToken, Schedule, Todo), ChatSync(실시간 연결·재연결), MediaUploader, StartResolver, AgePolicy — Android 의존성 없음
│     │  ├ core/database/ Room 로컬 캐시(채팅 메시지). 서버가 원본이라 스키마가 바뀌면 지우고 다시 받는다
│     │  ├ core/media/    사진·동영상 업로드 작업(WorkManager), Photo Picker 파일 검사·읽기
│     │  ├ core/push/     FCM 수신 서비스, 알림 채널·표시(PushNotifier)
│     │  ├ core/network/  Retrofit·OkHttp, AuthInterceptor, TokenAuthenticator(재발급 1회 동기화), SessionEvents, ChatSocket(STOMP 직접 구현)
│     │  ├ core/designsystem/  theme/FamTheme.kt(토큰), component/(FamAppBar, FamBottomNav, …)
│     │  └ feature/{auth,group,chat,calendar,place,album,story}/  화면 (settings는 S20에서 추가)
│     ├ debug/          로컬 서버 평문 허용, 요청 로그(BASIC), EXIF 검증 화면 — 릴리스에 포함 안 됨
│     └ release/        빌드 타입별 네트워크 모듈 (로그 없음)
├ gradle/, build.gradle.kts, settings.gradle.kts   (버전은 gradle/libs.versions.toml)
├ .github/workflows/android.yml  PR·main push마다 assembleDebug + testDebugUnitTest
├ CLAUDE.md, .mcp.json  이 파일, MCP 서버 설정 (Figma, Notion)
└ docs/                spec/(사양서 사본), design/, handoff/  (adr/는 필요 시 추가)
```

- 앱은 당분간 `:app` 단일 모듈 안에서 `core/`, `feature/`를 **패키지**로 나눈다.
  빌드 시간이 문제가 되면 그때 Gradle 모듈로 분리한다.
- `core/model`과 도메인·데이터 계층에는 Android 의존성(`Context`, `android.*`)을 넣지 않는다 (iOS 확장 대비, 사양서 13 §13.3).
- 토큰은 Android Keystore로 암호화한 DataStore에 저장한다. 사진은 Photo Picker만, 위치는 포그라운드·대략적 위치 우선 (사양서 08 §8.6, 09 §9.5).
- targetSdk는 Google Play 요구(API 36 이상)를 만족해야 한다. 현재 설정은 37.
- 소셜 로그인 키는 `local.properties`의 `FAMLOG_KAKAO_NATIVE_APP_KEY`, `FAMLOG_GOOGLE_WEB_CLIENT_ID`(백엔드 `GOOGLE_CLIENT_ID`와 같은 웹 클라이언트) → BuildConfig. git에 올리지 않고, 비어 있으면 버튼이 "설정 필요"를 띄운다(CI).
- 서버 주소는 `BuildConfig.API_BASE_URL`. 디버그는 에뮬레이터에서 로컬 서버(`http://10.0.2.2:8080/api/v1/`)로 간다.
- 401 처리: `AUTH_TOKEN_EXPIRED`면 `TokenAuthenticator`가 재발급 후 1회 재시도, `AUTH_INVALID`·재발급 실패면 토큰 삭제 후 로그인 화면.
  재발급을 겹쳐 보내면 서버가 재사용으로 보고 모든 기기를 로그아웃시키므로, 재발급 경로를 따로 만들지 않는다.
- 화면은 `FamTheme.colors`/`FamTheme.type`/`FamDimens`를 쓴다. Material 3 컴포넌트도 `FamLogTheme`이 토큰 색으로 연결해 둔다.

## 6. 작업 방식

- **기능 ID 하나씩** 작업한다 (예: `SCH-02`). 시작할 때 **`/feature SCH-02`** 명령을 쓴다.
  사양서 04 기능 요구사항 → 10 데이터 → 11 API → 18 인수 기준(자동 등록은 06 §6.5) 순서로 같은 ID를 읽는다.
- 요청 순서와 단계별 프롬프트는 사양서 `docs/spec/20-vibe-coding.md`에 있다.
- 순서: (백엔드 저장소에서 서버 API + 테스트) → 이 저장소에서 앱 화면 → 인수 기준 확인 → 커밋.
- 화면 작업 시: 먼저 `docs/design/figma-map.md`와 `docs/design/screenshots/`를 본다.
  Figma MCP는 **Starter 요금제라 호출 한도가 작다.** 로컬 문서로 부족할 때만 특정 노드 ID 하나에 대해
  `get_design_context` / `get_screenshot`을 호출한다. 파일 전체를 훑는 호출은 하지 않는다.
- 커밋 메시지: `feat(SCH-02): 구성원별 일정 조회 API` 형식.
- 작업이 끝나면 `docs/handoff/HANDOFF.md`의 "진행 현황"을 갱신한다.

## 7. 자주 쓰는 명령

```bash
# 앱 (저장소 루트에서)
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest            # 단위 테스트 (CI와 같음)
./gradlew :app:connectedDebugAndroidTest    # Compose UI 테스트 (에뮬레이터·기기 필요)
./gradlew :app:assembleRelease              # R8 릴리스 빌드 확인 (서명은 S22)

# 로컬 서버 (백엔드 저장소에서): ./gradlew bootRun → http://localhost:8080/v3/api-docs
```
