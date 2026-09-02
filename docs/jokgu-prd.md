# 족구 점수판 PRD (MVP)

**제품:** Jokguboard  
**날짜:** 2026-09-02  
**범위:** `C:\Jokguboard` 그린필드. `C:\Score`, `C:\PickleBoard`, `C:\sports_scoreboard` 수정 없음.

## 목표

웹 정적 점수판과 Android WebView APK로 족구 경기를 현장에서 집계한다.

## 규칙

- 족구 세트: 15점, 2점 차. 14:14 이후 듀스. 코트 선수 4명. 사이드아웃 시 1→2→3→4 회전.
- Futnet 세트: 11점, 2점 차, 최대 15점. 싱글/더블/트리플. 득점자 서브, 강제 회전 없음.
- 매치: 3세트 2선승만 (`bo3_2`).
- 세트 종료 후 코트 교대 (`teamsSwapped`).

## 포함

- 청/백 점수·세트, 되돌리기, 새 경기, 팀명 설정, ko/en/zh, 도움말 3종
- 0:0 선서브 (`serveChoice` → 첫 득점 `serveAnchor`)
- 팀당 원 최대 4개. 설정에서 종목(jokgu/futnet) 선택. 서브 칸 금색 강조.
- APK 경기 중 화면 L/R +1, 길게 −1
- localStorage 키 `jokgu_scoreboard_state_v1`
- Vercel 정적 (`/play` → `jokgu_scoreboard.html`)

## 제외 (MVP)

- Pro TV sync, 카메라, 팀전, 클럽, 다중 스킨, 음성/TTS
- BLE · wear-remote (이후 `AndroidBleScore` 동일 시그니처 추가 가능)

## 라이선스 · 보안

- 신규 npm 없음. GPL 라이브러리 없음.
- 서체: Google Fonts SIL OFL (Noto Sans KR, Jua). 오프라인 시 시스템 서체 폴백.
- 팀명은 `textContent`만. 점수는 기기 localStorage만.
- Android 권한: INTERNET(웹폰트). 마이크·카메라·BLE 없음.

## 특허 검토가 필요할 수 있는 구성 (비보장)

청구항 비교는 법률 자문이 아니다. 침해 없음을 보장하지 않는다.

- 무선 클리커 L/R로 화면 좌우 득점
- 세트 종료 시 자동 엔드 스왑
- 이후 단계의 사이드아웃 로테이션·BLE 브리지

일반 스포츠 점수 표시·15점 듀스 규칙은 종목 규칙의 관용적 구현이다.
