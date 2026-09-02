# 족구 점수판 구현 계획 (MVP)

**날짜:** 2026-09-02

## 접근

`voice_scoreboard.html` 전체 복사를 하지 않는다. 단일 HTML로 족구 코어만 작성한다.

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

`JK_CORE`, `JK_DELTA`, `JK_APK_LR`, `JK_I18N`, `JK_DISK_GLYPH`, `JK_OFL_FONTS`, `JK_SERVE`

## 검증

- 14:13 미종료, 15:13 승, 14:14→16:14, 2세트 선승
- undo, 새 경기, ko/en/zh, help 3종
- `serve.ps1` · APK L/R

## 배포

GitHub: `https://github.com/golee75git/Jokguboard.git`  
Vercel 정적, 빌드 스텝 없음. APK 이름 `app-debug-MMDD_HHMM-설명.apk`.
