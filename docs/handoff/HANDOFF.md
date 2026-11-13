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
- [ ] 패키지 골격: `core/{model,network,database,datastore,designsystem,common}`, `feature/*` 빈 화면
- [ ] Hilt, Compose Material 3, edge-to-edge, 예측형 뒤로가기
- [ ] `core/designsystem`: `docs/design/compose-theme/FamTheme.kt` + `design-tokens.json` 반영, 기본 `ui/theme/` 제거
- [ ] 하단 탭 5개(채팅·캘린더·장소·앨범·이야기), 앱 바 "우리 가족 ▾" 자리
- [ ] Retrofit + 인증 인터셉터(401 시 refresh 1회 재시도), Keystore 암호화 DataStore 토큰 저장
- [ ] GitHub Actions: `./gradlew assembleDebug testDebugUnitTest`
- [ ] **기술 검증 스파이크**: Photo Picker로 고른 사진의 EXIF 촬영 시각·GPS가 읽히는지, `ACCESS_MEDIA_LOCATION` 필요 여부 → 결과를 여기에 기록

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
| 2026-11-12 | - | CLAUDE.md 패키지 목록을 사양서 13.3에 맞춤, HANDOFF를 모바일 범위·사양서 v2.0 기준으로 정리 | - |
