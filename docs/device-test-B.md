# 실기기 테스트 가이드 (방법 B: PC 없이 스마트폰으로 설치)

회사 PC는 사용하지 않습니다. **개인 스마트폰 + 워치**만 사용합니다.
(이 방식이 회사 보안 정책에 문제가 없는지는 직접 확인해 주세요.)

## 0. 준비물
- 스마트폰에 **Bugjaeger** 앱 설치 (Play 스토어, 개발자: Roman Sisik). 폰에서 워치로 ADB 명령을 보내는 앱입니다.
- 테스트 APK: `wear-debug.apk` (회사 PC → 폰으로 옮기는 방법은 이메일/메신저 등 회사 정책에 맞는 방법 사용)

## 1. 워치 ↔ 폰 연결 (폰 핫스팟)
1. 폰에서 **모바일 핫스팟** 켜기
2. 워치: 설정 → 연결 → Wi-Fi → 폰 핫스팟에 연결
3. 워치: 설정 → 워치 정보 → 소프트웨어 정보 → **소프트웨어 버전** 연속 탭 → 개발자 모드 켜짐
4. 워치: 설정 → 개발자 옵션 → **ADB 디버깅** 켜기, **Wi-Fi를 통한 디버깅** 켜기
5. 워치: Wi-Fi를 통한 디버깅 → **새 기기 페어링** → IP:포트와 6자리 코드 표시됨
6. 폰 Bugjaeger: 우측 상단 메뉴 → **Pair device** → 위 IP:포트와 코드 입력
7. Bugjaeger: 연결 아이콘 → **Wi-Fi를 통한 디버깅 화면의 IP:포트**(페어링 포트와 다름)로 연결

## 2. APK 설치
Bugjaeger의 **Packages** 탭 → `+`(Install) → `wear-debug.apk` 선택.
워치 앱 목록에 **Quick Rec** 아이콘이 생기면 성공.

## 3. 첫 실행 (권한)
워치에서 Quick Rec 실행 → **마이크 허용**, **알림 허용**.
권한 허용 후 화면이 바로 닫히는 것은 정상입니다.

## 4. 홈 키 더블 프레스 지정
워치: 설정 → 유용한 기능(고급 기능) → **키 사용자 설정** → **두 번 누르기** → 앱 열기 → **Quick Rec**

## 4-1. ★ 가장 먼저: T0 (삼성 앱 자체 기능 확인, APK 설치 전에도 가능)
삼성 음성녹음 앱 내부에 "버튼 두 번 누르기 → 녹음 시작 / 길게 누르기 → 저장" 기능이 있음을 확인했습니다 (원래 Galaxy Watch Ultra의 Quick 버튼용으로 추정).
1. 워치: 설정 → 유용한 기능 → 키 사용자 설정 → **두 번 누르기** 목록에서 **음성녹음**을 고를 때, "녹음 시작" 같은 **별도 동작 항목**이 나오는지
2. 워치: 음성녹음 앱 → 설정(⋮ 또는 아래로 스크롤) 에 **버튼/빠른 녹음/두 번 누르기** 관련 항목이 있는지
3. 두 번 누르기를 **음성녹음**으로 지정 후 워치페이스에서 홈 키 2번 → **바로 녹음이 시작되는지 / 앱 화면만 열리는지**
4. Bugjaeger Shell (읽기 전용 명령):
```
dumpsys package com.samsung.android.wear.voicerecorder | grep -m2 -E "versionName|enabled="
settings list system | grep -i -E "double|quick|key"
settings list secure | grep -i -E "double|quick|key"
settings list global | grep -i -E "double|quick|key"
```
→ 3번에서 바로 녹음이 시작되면 개발 없이 목표 달성 가능성이 큽니다. 결과를 꼭 먼저 알려 주세요.

## 5. 테스트 항목 (각 항목의 결과를 메모)

| # | 동작 | 확인할 것 |
|---|---|---|
| T1 | 워치페이스 상태에서 홈 키 2번 | 진동 1회? 마이크 사용 표시(초록 점) 나타남? 워치페이스로 자동 복귀? 화면 전환이 눈에 보였는지(깜빡임 정도) |
| T2 | 녹음 중 손목 내려 화면 끄고 1분 대기 → 홈 키 2번 | 진동 2회(저장)? |
| T3 | 화면이 꺼진 상태에서 바로 홈 키 2번 | 녹음이 시작되는지, 아니면 화면만 켜지는지 |
| T4 | 다른 앱(예: 설정)을 연 상태에서 홈 키 2번 | 시작/종료 동작 여부 |
| T5 | 녹음 시작 직후 1초 안에 다시 홈 키 2번 | 바로 종료되는지, 오류 나는지 |
| T6 | 녹음 중 알림 패널 확인 | "녹음 중" 알림과 정지 버튼이 보이는지 |

## 6. 결과 수집 (Bugjaeger의 Shell 탭에서 실행)

**로그** (테스트 직후):
```
logcat -d -s WatchRecorder ActivityTaskManager | tail -n 200
```

**저장된 파일 목록**:
```
ls -la /sdcard/Recordings/WatchRecorder/
```

**기기 정보**:
```
getprop ro.build.version.release; getprop ro.build.version.sdk; getprop ro.build.display.id
```

**삼성 녹음 앱 정보** (패키지 이름 확인):
```
pm list packages | grep -i -E "voice|record"
```

출력은 Bugjaeger에서 복사/공유해서 채팅에 붙여 주세요.

## 7. 동기화 실험 (P1 판정용)

**S1.** 삼성 음성녹음 앱으로 10초 녹음 → 폰 `내장 저장공간/Recordings/Sounds/Watch/` 에 파일이 생기는지, 몇 초 걸리는지 확인 (기준선)

**S2.** Bugjaeger Shell에서 아래 명령으로 Quick Rec이 **삼성 녹음 앱 폴더**에 저장하게 한 뒤, 홈 키 2번으로 종료:
```
am start -n com.yoonki.watchrecorder/.ToggleActivity --es relpath "Recordings/Voice Recorder/"
```
→ 다시 홈 키 2번(또는 같은 명령)으로 종료.
그다음 (1) 워치의 삼성 녹음 앱 목록에 이 파일이 보이는지, (2) 폰 `Recordings/Sounds/Watch/` 로 동기화되는지 확인.

(삼성 앱의 실제 저장 폴더가 다르면 S1 후 `ls /sdcard/Recordings/` 결과를 알려 주세요. 폴더명을 맞춰 다시 안내드립니다.)
