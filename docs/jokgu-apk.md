# APK 버전별 수정사항

**제품:** Jokguboard  
**정리일:** 2026-09-09  
**현재 설치:** `app-debug-0910_0029-discscore.apk`  
**다운로드:** https://jokguboard.vercel.app/downloads/app-debug-0910_0029-discscore.apk  

랜딩(`/`)과 도움말 3종 상단에 현재 파일명을 표시한다. 모두 debug 빌드이며 Play 스토어 제출용이 아니다.

날짜별 서술은 [jokgu-changelog.md](jokgu-changelog.md). 상세는 `수정기록.txt`.

파일명: `app-debug-MMDD_HHMM-설명.apk` (KST).

| APK | 시각 | 한 줄 | Git |
|-----|------|------|-----|
| `app-debug-0902_1334-jokgu-mvp.apk` | 13:34 | 족구 MVP 웹·APK | `6ae4208` |
| `app-debug-0902_1357-board-look.apk` | 13:57 | 청/백 카드 보드 | `3ac7f97` |
| `app-debug-0902_1408-look-pack.apk` | 14:08 | 모습 4종 | `3cda465` |
| `app-debug-0902_1430-tts-ble.apk` | 14:30 | TTS·BLE·심플·OFL | `6abe834` |
| `app-debug-0902_1449-card-depth.apk` | 14:49 | 사이버·링 원근 | `987f1b3` |
| `app-debug-0902_1508-rotate.apk` | 15:08 | 원 1~4·사이드아웃 | `bedc6fd` |
| `app-debug-0902_1519-skins.apk` | 15:19 | Score 스킨 CSS 연결 | `fda5065` |
| `app-debug-0902_1550-dock.apk` | 15:50 | 하단 메뉴·설정 언어 | `70866ce` |
| `app-debug-0902_1600-mid.apk` | 16:00 | 가운데 새 경기 | `d3da119` |
| `app-debug-0902_1618-sport.apk` | 16:18 | 족구/Futnet 전환 | `3604bd9` |
| `app-debug-0902_1631-fresh.apk` | 16:31 | 새 경기 보드 확인 | `42da9ed` |
| `app-debug-0902_1636-freshhit.apk` | 16:36 | 0:0 새 경기 클릭·금색 버튼 | `9ae5d9c` |
| `app-debug-0902_1646-midz.apk` | 16:46 | 가운데 칸을 점수 위에 | `6c40172` |
| `app-debug-0902_1712-cyberhit.apk` | 17:12 | 사이버·링 새 경기 클릭 | `039928b` |
| `app-debug-0902_1738-netcol.apk` | 17:38 | 숫자 고정·사이드아웃 1→2→3→4 | `d2ff148` |
| `app-debug-0902_1746-outdisc.apk` | 17:46 | 원 바깥·크게, 서브 원 더 큼 | `ca92738` |
| `app-debug-0908_2010-hidpad.apk` | 20:10 | APK 위·중·아래 패드·외부 BT 음량 | `4ea331b` |
| `app-debug-0909_1701-courtdisc.apk` | 17:01 | 점수 위·아래 원 2×2, 선서브 1번 | `11b6201` |
| `app-debug-0909_2234-padgesture.apk` | 22:34 | 위·가운데·아래 리모컨 제스처 인식(실기기 확인) | `252a4e7` |
| `app-debug-0909_2256-winflow.apk` | 22:56 | 세트 5초 자동 닫힘·경기 승 새 경기 | `a2d20fb` |
| `app-debug-0909_2331-padwin.apk` | 23:31 | 패드 고정+세트 안내 중에도 득점 | `775a49a` |
| `app-debug-0909_2357-nameserve.apk` | 23:57 | 팀명 세트쪽·터치수정·새경기 선서브 | `d405b89` |
| `app-debug-0910_0029-discscore.apk` | 00:29 | 원 간격·점수 칸 맞춤 | `5bcba58` |

## 현재 버전에서 쓰는 것

`app-debug-0910_0029-discscore.apk`가 위 표를 모두 이은 최신이다.

- 족구 랠리 득점, 사이드아웃 시 직전 서브 팀만 1→2→3→4
- Futnet 11점 캡 15, 득점자 서브
- 원 점수 위·아래 2×2(화면 왼쪽 3·4/2·1, 오른쪽 1·2/4·3), 위·아래 같은 세로줄로 점수 가운데에 모임, 숫자 고정, 선서브 금색은 1번, 서브 원 더 큼
- 큰 점수는 남은 칸을 채움 (`JK_SCORE_FILL`)
- 팀명은 세트 쪽(사이버는 위 가운데), 글자 2배, 터치하면 이름 수정. 선서브는 원·화면 좌우·패드 위·아래. 새 경기는 취소 또는 선서브 팀 선택
- 새 경기 보드 확인, 사이버·링에서도 클릭. 세트 안내 창은 5초 후 자동 닫힘(안내 중에도 패드 득점). 경기 승 창은 확인+새 경기
- 패드 Kotlin은 `2234-padgesture`와 동일(`MainActivity.kt` 미수정)
- 심플, TTS, BLE, 스킨, 하단 메뉴
- APK 전용 가운데·아래 버튼 패드 위치 맞추기(실측 결과 이 리모컨은 좌표 클릭이 아니라 제스처를
  보냄: 가운데·아래는 눌림·뗌 사이 경과시간·이동거리, 위는 좌표 없는 단독 시스템 신호로 인식),
  외부 BT 음량 좌우 득점, 갤럭시 탭 S9+(SM-X816N) 실기기 확인 완료

이전 APK는 `downloads/`에 보관한다. 설치 안내는 최신 파일명만 가리킨다.
