# 족구 점수판 PRD

**제품:** Jokguboard  
**정리일:** 2026-09-02  
**현재 설치 APK:** `app-debug-0902_1746-outdisc.apk`  
**웹:** https://jokguboard.vercel.app  
**저장소:** https://github.com/golee75git/Jokguboard.git  

**범위:** `C:\Jokguboard`만. `C:\Score`, `C:\PickleBoard`, `C:\sports_scoreboard` 수정 없음.

날짜별 기록: [jokgu-changelog.md](jokgu-changelog.md)  
APK 버전: [jokgu-apk.md](jokgu-apk.md)  
구현 계획: [jokgu-plan.md](jokgu-plan.md)  
작업 로그: `수정기록.txt`

## 목표

웹 정적 점수판과 Android WebView APK로 족구·Futnet 경기를 현장에서 집계한다.

## 규칙 (현재)

### 족구 (`jokgu`)

- 코트 선수 4명. 한 세트 15점, 2점 차. 14:14부터 듀스.
- 랠리 득점: 서브 팀·리시브 팀 모두 +1 가능.
- 사이드아웃: 리시브 팀이 득점하면 서브권이 넘어간다. 직전 서브 팀만 다음 번호(1→2→3→4). 서브 팀이 연속 득점하면 번호 유지.
- 상대가 먼저 득점하면 그 팀 1번부터 서브.
- 원 숫자 1·2·3·4는 슬롯에 고정. 서브 표시는 금색 배경과 더 큰 원·숫자.
- 원 위치: 바깥쪽 세로. 왼쪽 팀 아래가 1번, 오른쪽 팀 위가 1번.
- 경기는 3세트 2선승만 (`bo3_2`).

### Futnet (`futnet`)

- 한 세트 11점, 2점 차, 최대 15점(15:14 종료).
- 항상 3세트 2선승.
- 싱글 1인 / 더블 2인 / 트리플 3인(기본 더블).
- 방금 득점한 팀이 서브. 강제 회전 없음.
- 더블·트리플: 원 탭으로 서브 선수 표시. 싱글: ①만, 그 팀 점수 짝수 R / 홀수 L(0점은 R).

### 공통

- 0:0에서 선서브 선택(팀명·원·화면 좌우).
- 세트 종료 후 코트 교대(`teamsSwapped`).
- 종목 또는 Futnet 유형 변경 시 확인 후 현재 점수 초기화.

## 포함 (현재)

- 청/백 점수·세트, 되돌리기, 새 경기(보드 취소·확인 배너), 설정, ko/en/zh, 도움말 3종
- 설정: 종목, Futnet 유형, 언어, 글꼴, 보드 모습, 점수 읽기
- 심플 모드(선서브 후). 심플에서는 새 경기 숨김
- 하단 메뉴: 되돌리기 · 심플 · 설정 · 도움말
- APK 경기 중 화면 L/R +1, 길게 −1. 가운데 칸·새 경기는 L/R에서 제외
- localStorage 키 `jokgu_scoreboard_state_v1`
- Vercel 정적 (`/` 랜딩, `/play` → `jokgu_scoreboard.html`)
- TTS: 득점·서브·세트·경기. Android `AndroidTTS`, 웹 `speechSynthesis`. MP3 팩·마이크 없음
- BLE: 기존 Score 시계와 같은 GATT 패킷 v2. Android 12+ 연결·advertise 권한
- 보드 모습: 자체 look + Score 스킨 CSS를 족구 DOM에 연결. 탁구장 PNG·카메라·liverec 없음

## 제외 · 나중

- Play 스토어 release 서명 APK (현재는 debug)
- Pro TV sync, 카메라, 팀전, 클럽
- 대회 설정 UI, 라이브 중 핸디 변경
- Score 허브 링크는 Score 워크스페이스에서만 (이 저장소 아님)

## 라이선스 · 보안

- 신규 npm 없음. GPL 라이브러리 없음.
- 서체 SIL OFL 1.1: Noto Sans KR, Jua, Do Hyeon, Orbitron (Google Fonts CSS만). 오프라인 시 시스템 서체 폴백.
- AndroidX Core/AppCompat/WebKit, Kotlin, AGP — Apache-2.0.
- 팀명 `textContent`. 점수는 기기 localStorage만. 사용자 입력 HTML 삽입 없음.
- 카메라·마이크 권한 없음. BLE는 페어링된 기기 notify.

## Play Store 참고

디버그 APK는 스토어 제출용이 아니다. 등록 시 release 서명·개인정보 문구·BLE 권한 고지가 별도로 필요하다.

## 특허 검토가 필요할 수 있는 구성 (비보장)

청구항 비교는 법률 자문이 아니다. 침해 없음을 보장하지 않는다.

- 화면 좌우 점수 BLE GATT notify
- APK L/R 클릭·길게 −1
- 세트 종료 시 자동 엔드 스왑
- 점수 TTS 문구 타이밍
- 3D 스킨(원근·카드 기울기)
- 한 화면에서 종목 전환 시 점수 초기화 + 선수 원 개수 변경
- 사이드아웃 회전 vs 득점자 서브

일반 점수 표시·15점/11점 듀스·확인 배너는 종목 규칙·관용 UI이다.
