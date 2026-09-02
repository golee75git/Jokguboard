# 족구 점수판 구현 계획

**정리일:** 2026-09-02  
**현재 설치 APK:** `app-debug-0902_1746-outdisc.apk`

PRD: [jokgu-prd.md](jokgu-prd.md)  
날짜별: [jokgu-changelog.md](jokgu-changelog.md)  
APK: [jokgu-apk.md](jokgu-apk.md)

## 접근

`voice_scoreboard.html` 전체 복사를 하지 않는다. 스킨 CSS 구간(NEON~ARENA3D, liverec·PNG 제외)만 족구 DOM 선택자로 연결한다 (`JK_SCORE_SKIN_CSS`).

참고한 패턴(자체 재작성): `applyDelta`, `historyStack`/`undo`, `persistState`, APK L/R 길게 −1.

탁구 규칙·Score/PickleBoard/sports_scoreboard 폴더는 수정하지 않는다.

## 상태 (2026-09-02)

1단계 MVP와 이후 현장 수정이 한 저장소에 있다. 현재 보드가 기준이다.

| 항목 | 상태 |
|------|------|
| 웹 정적 + `/play` + Vercel | 완료 |
| Android WebView `com.jokgu.scoreboard` | 완료 |
| 족구 15점 듀스 · 3세트 2선승 | 완료 |
| 족구 4인 · 랠리 득점 · 사이드아웃 1→2→3→4 | 완료 |
| Futnet 11점 캡 15 · 득점자 서브 | 완료 |
| 원 바깥 세로 · 왼쪽 아래 1 · 오른쪽 위 1 | 완료 |
| 원·숫자 확대, 서브 원 더 큼·금색, 숫자 고정 | 완료 |
| 새 경기 보드 확인, 사이버·링 클릭 | 완료 |
| 심플 · OFL 4종 · TTS · BLE | 완료 |
| 스킨 CSS 연결 (PNG·카메라 없음) | 완료 |
| Play 스토어 release APK | 미함 (debug만) |
| 대회 설정 UI · 라이브 핸디 | 나중 |

## 파일

| 경로 | 구분 |
|------|------|
| `jokgu_scoreboard.html` | 메인 보드 (단일 HTML) |
| `index.html`, `help.html`, `help_en.html`, `help_zh.html` | 랜딩·도움말 |
| `serve.ps1`, `vercel.json` | 로컬 미리보기·정적 라우트 |
| `android/` | WebView (`com.jokgu.scoreboard`) |
| `downloads/*.apk` | 디버그 APK 산출물 |
| `scripts/check-win-rules.js` | 승패·서브 인덱스 회귀 |
| `docs/jokgu-prd.md`, `docs/jokgu-plan.md` | 요구·계획 |
| `docs/jokgu-changelog.md`, `docs/jokgu-apk.md` | 날짜별·APK별 정리 |
| `수정기록.txt` | 작업 로그 |
| `LICENSE-FONTS.txt` | OFL 서체 목록 |
| `C:\Score` 등 | 재사용 없음 · 수정 없음 |

## 마커

`JK_CORE`, `JK_DELTA`, `JK_APK_LR`, `JK_I18N`, `JK_DISK_GLYPH`, `JK_NET_COL`, `JK_OFL_FONTS`, `JK_SERVE`, `JK_LOOK_PACK`, `JK_SKIN_REMAP`, `JK_SIMPLE`, `JK_TTS`, `JK_BLE_PUSH`, `JK_BLE_SPIKE`, `JK_FRAME_SVG`, `JK_BOARD_DEPTH`, `JK_ROTATE`, `JK_SPORT_RULES`, `JK_FRESH_BANNER`, `JK_FRESH_HIT`, `JK_SCORE_SKIN_CSS`, `JK_SCORE_SKIN_BRIDGE`, `JK_DOCK`, `JK_MID_PANEL`

문제 시 해당 마커 블록만 되돌린다.

## 검증

- 족구: 14:13 미종료, 15:13 승, 14:14→16:14, 3세트 2선승
- Futnet: 11:9 승, 10:10·11:10 계속, 12:10 승, 14:14 계속, 15:14 종료
- 사이드아웃 시 직전 서브 팀 인덱스 1→2→3→4 순환 (`scripts/check-win-rules.js`)
- undo, 새 경기(0:0에서도 클릭, 보드 확인 배너), ko/en/zh, help 3종
- `serve.ps1` · APK L/R · 사이버·링에서 새 경기 클릭
- 심플, OFL 글꼴 4종, TTS, BLE 패킷 v2

## 배포

GitHub: `https://github.com/golee75git/Jokguboard.git`  
Vercel 정적, 빌드 스텝 없음. 사이트 https://jokguboard.vercel.app  
APK 이름 `app-debug-MMDD_HHMM-설명.apk`. 랜딩·도움말 상단에 현재 파일명 표시.
