# 족구 점수판 구현 계획 (MVP)

**날짜:** 2026-09-02

## 접근

`voice_scoreboard.html` 전체 복사를 하지 않는다. 스킨 CSS 구간(NEON~ARENA3D, liverec·PNG 제외)만 족구 DOM 선택자로 연결한다 (`JK_SCORE_SKIN_CSS`).

참고한 패턴(자체 재작성): `applyDelta`, `historyStack`/`undo`, `persistState`, APK L/R 길게 −1.

## 파일

| 경로 | 구분 |
|------|------|
| `jokgu_scoreboard.html` | 신규 |
| `index.html`, `help.html`, `help_en.html`, `help_zh.html` | 신규 |
| `serve.ps1`, `vercel.json` | 신규 |
| `android/` | 신규 WebView (`com.jokgu.scoreboard`) |
| `downloads/*.apk` | 신규 빌드 산출물 |
| `docs/jokgu-prd.md`, `docs/jokgu-plan.md` | 신규 |
| `수정기록.txt` | 신규 |
| `C:\Score` 등 | 재사용 없음 · 수정 없음 |

## 마커

`JK_CORE`, `JK_DELTA`, `JK_APK_LR`, `JK_I18N`, `JK_DISK_GLYPH`, `JK_OFL_FONTS`, `JK_SERVE`, `JK_LOOK_PACK`, `JK_SKIN_REMAP`, `JK_SIMPLE`, `JK_TTS`, `JK_BLE_PUSH`, `JK_BLE_SPIKE`, `JK_FRAME_SVG`, `JK_BOARD_DEPTH`, `JK_ROTATE`, `JK_SPORT_RULES`, `JK_SCORE_SKIN_CSS`, `JK_SCORE_SKIN_BRIDGE`, `JK_DOCK`, `JK_MID_PANEL`

## 검증

- 족구: 14:13 미종료, 15:13 승, 14:14→16:14, 3세트 2선승
- Futnet: 11:9 승, 10:10·11:10 계속, 12:10 승, 14:14 계속, 15:14 종료
- undo, 새 경기, ko/en/zh, help 3종
- `serve.ps1` · APK L/R
- 심플, OFL 글꼴 4종, TTS, BLE 패킷 v2

## 배포

GitHub: `https://github.com/golee75git/Jokguboard.git`  
Vercel 정적, 빌드 스텝 없음. APK 이름 `app-debug-MMDD_HHMM-설명.apk`.
