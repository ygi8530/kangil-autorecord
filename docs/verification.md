# 검증 기록

## 환경
- 개발 PC: Ubuntu, JDK 17, Android SDK 35, Gradle 8.11.1 (`~/opt`, `~/Android/Sdk`)
- 회사 보안정책으로 PC ↔ 워치 Wi-Fi/핫스팟 연결 불가 → 실기기 검증은 **방법 B**(폰 Bugjaeger로 설치, `docs/device-test-B.md`)로 진행
- PC에서는 Wear OS 5 에뮬레이터(`wear34`, API 34 x86_64)로 일반 Android 동작만 검증

## 에뮬레이터 검증 (2026-10-20, Spike APK 0.1)
※ 에뮬레이터에는 삼성 홈 키 더블 프레스·삼성 Voice Recorder·삼성 동기화가 없음. 아래는 Android 공통 동작만 해당.

| 항목 | 결과 | 근거 |
|---|---|---|
| 투명 Activity → 마이크 FGS 시작 | 성공 | `isForeground=true types=00000080`(microphone) |
| Activity가 떠 있는 시간 | onCreate → finish 약 2ms, 실행 대기시간 121ms | logcat `t=34448 → 34450`, `am start -W WaitTime: 121` |
| 시작 후 화면 | 워치페이스(SysUiActivity)로 즉시 복귀 | `topResumedActivity=...SysUiActivity` |
| 화면 꺼짐(Dozing) 중 녹음 유지 | 유지됨 | 24초 녹음 → 파일 길이 24.000s (ffprobe) |
| 재실행으로 정지 → MediaStore 저장 | 성공 | `/sdcard/Recordings/WatchRecorder/rec_...aac` 194,625 bytes |
| 저장 형식 m4a (녹음 중 ADTS → 저장 시 재인코딩 없이 MP4 리먹스) | 성공 | `rec_20261020_150812.m4a` ffprobe: mp4/aac 16kHz mono 9.86s |
| 녹음 중 force-stop | 임시 파일은 재생 가능(5.95s) **but 복구/게시 안 됨, 사용자 알림 없음** | Spike에 상태 복구 없음 → MVP에서 구현 필요 |

## 삼성 Voice Recorder (Wear OS) 조사
- APKMirror 등록 정보: 최신 1.2.00.49 (2026-03-17). 설명상 선택 권한 "Nearby devices: **Wi-Fi direct transfer**에 사용" → 폰 동기화는 삼성 앱 자체의 Wi-Fi Direct 전송으로 추정(비공개 메커니즘).
- 정적 분석: APKMirror 1.2.00.49 (armeabi-v7a, 서명자 `CN=Samsung Cert, O=Samsung Corporation`, SHA-256 `34df0e7a…0a42`)
  - 패키지: `com.samsung.android.wear.voicerecorder`, targetSdk 36
  - **외부 앱이 쓸 수 있는 녹음 제어 인터페이스 없음**
    - `MainActivity`: exported 런처 (일반 실행만, 문서화된 녹음 시작 extra 없음)
    - `BixbyAppLinkActivity` (`applink://com.samsung.android.wear.voicerecorder`): 권한 `...voicerecorder.Controller` = **signature|privileged(0x3)** → 서드파티 앱 사용 불가
    - `VRQuickButtonService`: 권한 `ACCESS_SYSTEM_KEY_DISPATCHING` → 시스템 키 디스패처 전용
    - Tile/Complication: `BIND_TILE_PROVIDER`/`BIND_COMPLICATION_PROVIDER` 시스템 전용
  - **삼성 앱 자체에 하드웨어 버튼 녹음 기능 존재**: 문자열 `Double press`/`Short press` → "Recording started", `Press & hold` → "Stop and save recording", "You can change to start with a double press instead in Settings." 액션 `quick_button_record`, `quick_button_stop_record`
    → Galaxy Watch Ultra의 Quick(Action) 버튼용으로 보임. **Watch6 홈 키 더블 프레스에서 이 동작을 선택할 수 있는지 실기기 확인 필요 (T0)**. 가능하면 개발 없이 P0+P1 동시 달성.
  - 동기화: `DataLayerListenerService`(path `/c_voicerecorder`) + `WifiP2PConnectionService` → 삼성 앱 내부 DB 기반 전송. 다른 앱 파일 포함 여부는 S2로 확인.
- 실기기 확인: 아래 "실기기 검증" 참고 (T0 = 화면만 열림)

## 실기기 검증 (2026-10-21, 집 Windows PC ↔ 워치 무선 ADB)

### 환경
- PC: Windows 11, Temurin JDK 17.0.20, Android SDK (platforms 35·37, build-tools 34·35·36), adb 1.0.41, Gradle 8.11.1. `gradlew.bat :wear:assembleDebug` 성공 (2m30s)
- 연결: PC는 유선(LG U+ HGW 192.168.123.x, 그 위 공유기 192.168.219.1 이중 NAT), 워치는 Wi-Fi `U+Net61D9_5G`(192.168.219.100). 서로 다른 서브넷이라 mDNS 자동 검색은 안 되지만 PC→워치 방향 직접 접속은 가능 → `adb pair 192.168.219.100:<페어링포트> <코드>` 후 `adb connect 192.168.219.100:<연결포트>` 성공
- 주의: 워치가 폰과 블루투스 연결 중이면 Wi-Fi를 자동으로 끊음 → 무선 디버깅 중에는 워치 Wi-Fi를 유지해야 함

### V1 기기 정보 (`scripts/verify_samsung_recorder.sh`, Windows/Git Bash 대응 수정)
| 항목 | 값 |
|---|---|
| 모델 | SM-R930 (Galaxy Watch6 40mm), product `fresh6bsue` |
| Android / SDK | 16 / 36 (One UI 8 Watch, `ro.build.version.oneui=80000`) |
| 빌드 | BP2A.250325.020.R930XXS2CZF5 |
| 삼성 Voice Recorder | `com.samsung.android.wear.voicerecorder` 1.2.00.49 (versionCode 120049000) → **정적 분석한 APK와 동일 버전**, 위 분석 결론 그대로 적용 |

### V2 후보 패키지·표준 인텐트
- `pm list packages | grep voice|record|sound`: voicerecorder, intellivoiceservice(1.1.37.11), watchface.tickingsound, soundpicker
- `cmd package query-activities -a android.provider.MediaStore.RECORD_SOUND` → **No activities found** (표준 녹음 인텐트 처리 앱 없음)
- 원본 출력: `docs/raw/` (APK 파일은 `.gitignore`로 제외)

### T0 — 홈 키 더블 프레스 = 삼성 음성녹음: **화면만 열림 (녹음 자동 시작 안 함)**
- 설정값: `settings get global setting_home_key_double_press` = `com.samsung.android.wear.voicerecorder/...view.MainActivity` (키 사용자 설정에서 "음성녹음" 선택 시 별도 "녹음 시작" 동작 없이 앱 Activity로 지정됨)
- 2회 시도 logcat (`docs/raw/t0_logcat.txt`):
  ```
  23:43:11.607 I/ActivityTaskManager: START u0 {act=android.intent.action.MAIN flg=0x10200000 cmp=com.samsung.android.wear.voicerecorder/.view.MainActivity} with LAUNCH_SINGLE_TASK from uid 1000
  23:43:18.108 I/ActivityTaskManager: START u0 {act=android.intent.action.MAIN ... cmp=.../.view.MainActivity} ... from uid 1000
  ```
  → 시스템이 extra 없는 MAIN 인텐트로 실행만 함. 이후 AudioRecord/MediaRecorder/마이크 FGS 로그 없음. 사용자 육안 확인도 "녹음 앱 화면만 열림"
- 그 밖의 키 설정: `setting_home_key_long_press` = Bixby, `multi_press_timeout=300`ms, `setting_action_button_press=None/None` (Watch6에는 Ultra의 Quick 버튼 없음)
- **결론: 삼성 앱의 버튼 녹음 기능(Quick 버튼용)은 Watch6 홈 키에 연결되지 않음 → HANDOFF 결정표에 따라 자체 앱 MVP(Phase 2) 진행.** P1(폰 자동 동기화)은 S2 결과로 판단

### T1~T6, S1~S2 (Spike APK 0.1-spike, 실기기)
- 설치: `adb install -r wear-debug.apk` → Success. 권한(마이크·알림)은 사용자가 워치에서 직접 허용(`granted=true, USER_SET`)
- 키 지정: 설정 → 키 사용자 설정 → 두 번 누르기 → Quick Rec → `setting_home_key_double_press=com.yoonki.watchrecorder/...ToggleActivity`
- 로그: `docs/raw/t1-t6_logcat.txt`

| # | 결과 | 근거 |
|---|---|---|
| T1 홈 키 2번 → 시작 | **성공**. 진동, 마이크 표시, 워치페이스 즉시 복귀 (사용자 확인) | `START ... ToggleActivity from uid 1000` → `onCreate 23:47:09.414` → `finish 09.467`(53ms) → `onStartCommand START 09.482` → `recording started 09.814`; `isMicrophoneEnabled: true`; `VibratorManagerService vibrate uid 10138` |
| T2 화면 끈 채 85초 → 홈 키 2번 종료 | **성공**. 녹음 끊김 없음, 저장 진동 (사용자 확인) | start `23:47:36.628`, `Going to sleep 23:47:38` → `Waking up 23:49:03.363`, STOP `23:49:03.979`, `saved size=706370`; 파일 `rec_20261021_234736.m4a` mvhd/mdhd **87.104s** (시작~정지 87.35s) |
| T3 화면 꺼진 상태에서 바로 2번 | **성공** (T1·T2 중 자연 발생) | `Waking up from Dozing reason=WAKE_REASON_POWER_BUTTON 23:47:08.623` → ToggleActivity START `09.284` → 녹음 시작 / 종료도 `23:49:03.363` 깨어남 → `03.944` onCreate → STOP. 첫 누름이 화면을 켜도 더블 프레스가 그대로 인식됨 |
| 반복 토글 | 4회 시작/종료 모두 정상 저장 | 23:47:09~18 (61,243B), 20~22 (16,134B), 25~29 (21,799B), 36~23:49:05 (706,370B). Activity 체류 2~53ms |
| T4 다른 앱(설정) 위에서 | **성공** (시작·종료 모두) | 설정 `StMainSettingsActivity` START `23:50:20` → 홈 키 2번 `Detect press count 2 23:50:33.907` → `recording started 34.220`; 다시 설정 START `23:50:38` → `count 2 23:50:42.525` → STOP → `saved size=67471` |
| T5 시작 직후 재토글 | **2번+2번(진동 직후)은 성공 / 4번 연속은 무반응** | 4연타: `SingleKeyGesture Detect press KEYCODE_POWER count 4 23:50:52.042` → Activity 시작 없음 (시스템이 4회 누르기로 판정, 앱에 전달 안 됨). 2+2: `count 2 23:50:54.619` START → `count 2 23:50:55.674` STOP (1.05s 간격) → `saved size=5267`. 사용자 확인 "진동 오자마자 2번 누르면 종료됨" |
| T6 알림 정지 버튼 | **성공** | `recording started 23:51:17.834` → onCreate 없이 `onStartCommand action=STOP 23:51:21.033` → `saved size=24400` |

#### 키 판정 관련 확인 사항 (설계 반영 필요)
- 시스템 멀티 프레스 판정: `multi_press_timeout=300`ms. 간격이 짧으면 연속 누름이 하나의 N회 누르기로 합쳐짐 → 시작·종료 사이에 진동 후 잠깐 간격이 필요
- SOS: `sos_send_with_key_on=1`(키로 SOS 켜짐). 설정값 `sos_key_press_count_for_sending=3`이 있으나 **사용자 확인상 실제 발동은 홈 키 5회** (설정값은 현재 동작과 무관한 것으로 보임). 4회 연타에서 미발동 확인(해당 시각 SOS/긴급 관련 Activity·서비스 시작 로그 없음). 더블 프레스 2회+2회 사용은 5회에 못 미치지만, 시작 직후 연타가 5회로 합쳐지지 않도록 MVP 디바운스 설계 시 고려
- `finish()` 후 같은 Task에 다시 진입해도 문제 없음: 총 11회 토글 전부 정상 저장, 크래시·ANR 없음 (`AndroidRuntime/FATAL/ANR` 로그 없음)

### S1~S2 — 폰 자동 동기화 (P1 판정)
- 조건: 폰 블루투스 연결 상태(이 동안 워치 Wi-Fi가 끊기므로 ADB는 테스트 전후에만 확인)

| # | 결과 | 근거 |
|---|---|---|
| S1 삼성 음성녹음 앱으로 녹음 (기준선) | **약 20초 이내 폰으로 동기화** (사용자 확인) | 워치 `/sdcard/Recordings/Voice Recorder/음성 002_W_20261022_000122.m4a` (185,292B) 생성 |
| S2 Quick Rec을 삼성 폴더에 저장 | **삼성 앱 목록에 안 보임, 폰으로 동기화 안 됨** (사용자 확인, 1분 이상 대기) | `am start ... --es relpath 'Recordings/Voice Recorder/'` → `recording started 00:06:25.527` → 홈 키 2번 STOP `00:08:25.236` → `saved path=Recordings/Voice Recorder/ size=969493`; 같은 폴더에 `rec_20261022_000625.m4a`, `rec_20261022_000611.m4a` 존재 확인 |

- 첫 S2 시도(23:58)는 무효: Windows PowerShell에서 `adb shell am start ... --es relpath "'Recordings/Voice Recorder/'"`로 넘긴 extra가 앱에 전달되지 않아 기본 폴더(`Recordings/WatchRecorder/`)에 저장됨. `adb shell "<명령 전체를 한 문자열로>"` 형태로 다시 실행해 해결
- **결론: 삼성 Voice Recorder는 폴더 감시가 아니라 자체 DB에 등록한 녹음만 목록·폰 동기화 대상으로 삼음** (정적 분석의 `DataLayerListenerService` + 내부 DB 추정과 일치). 외부 앱 파일을 같은 폴더에 넣는 방식으로는 P1 달성 불가

## 실기기 검증 종합 (2026-10-22)
| 목표 | 판정 |
|---|---|
| P0 홈 키 더블 프레스 시작·종료 | **자체 앱(Quick Rec)으로 달성 가능** — T1~T6 통과 (4연타는 시스템이 4회 누르기로 판정해 무반응, 2+2는 정상) |
| P0 삼성 앱으로 대체 | **불가** — T0: 홈 키 2번은 삼성 앱 화면만 열림 |
| P1 삼성 앱 경유 폰 자동 동기화 | **불가** — S2: 삼성 앱이 외부 파일을 인식·전송하지 않음 |
| P1 대안 | 공개 API로 가능한 방법은 폰 쪽 수신 앱(Wearable Data Layer `ChannelClient`로 파일 전송) 필요 → CLAUDE.md 규칙상 **사용자 동의 필요** |

## 자체 폰 동기화 (2026-10-22, 워치 0.2 + 폰 0.2, Wearable Data Layer)
- 폰: Galaxy S25 Edge SM-S937N, Android 16 (SDK 36). 워치·폰 모두 같은 debug 키로 서명, applicationId `com.yoonki.watchrecorder`
- 폰 logcat (`adb logcat -s WatchRecorder`):
  ```
  01:18:36.375 saved rec_20261022_011825.m4a (28082B) → Recordings/WatchRecorder/
  01:18:36.377 acked rec_20261022_011825.m4a
  01:18:36.377 rec_20261022_011825.m4a already saved, re-acking     ← 같은 파일 재전송 시 중복 저장 없이 ack만
  01:18:55.826 saved rec_20261022_011849.m4a (23955B) → Recordings/WatchRecorder/
  01:19:29.312 saved rec_20261022_011913.m4a (22922B) → Recordings/WatchRecorder/
  ```
- 사용자 확인: 연결 상태 녹음 즉시 수신, **폰 블루투스 끈 상태에서 녹음 → 다시 켜자 자동 수신** (대기열 → 재연결 즉시 전송 동작)
- 미검증: "폰에 저장되면 워치에서 삭제" 토글 (HANDOFF 다음 할 일 1)

## 동기화·삭제 토글 재검증 (2026-10-22 밤, 워치 0.3 → 0.3.1, 폰 0.4.1)
- 폰 0.4 버그: R8이 `android_wear_capabilities`를 지워 워치가 폰을 못 찾음 → 워치 로그 `sync: phone not reachable, 1→2→3 queued`(21:54~21:55). 0.4.1 설치 후 대기열 3건 22:11:58 일괄 수신 (`saved → acked`)
- **"폰에 저장되면 워치에서 삭제" 토글 통과** (워치 logcat):
  ```
  22:11:58 ack rec_20261022_215439.m4a: saved on phone, kept on watch      ← 토글 OFF
  22:12:10 settings: deleteAfterSync=true                                  ← 폰에서 토글 ON → 워치로 전달
  22:12:16 ack rec_20261022_221212.m4a: saved on phone, deleted from watch (rows=1)
  22:12:34 ack rec_20261022_221232.m4a: saved on phone, deleted from watch (rows=1)
  ```
  워치 `ls /sdcard/Recordings/WatchRecorder/`: 221212·221232 없음, 그 이전 파일은 남아 있음
- 발견 1: 재연결 감지(`phone reachable again 22:04:06`) 후 실제 전송은 22:11:57 — JobScheduler 작업이 워치 doze 중 ~8분 지연
- 발견 2: 같은 파일 2회 전송(즉시 job + 15분 주기 job 동시 실행) → 폰이 `already saved, re-acking`으로 중복 저장은 막음
- 0.3.1 수정: 재연결 이벤트에서 job 예약 대신 즉시 전송, `AtomicBoolean`으로 동시 실행 1개 제한
- 0.3.1 확인: 폰 BT 끈 상태에서 녹음(`rec_20261022_221724`, 22:17:24 시작) → BT 켬 → 폰 `22:17:34 saved → acked` 1회만 수신(중복 없음). 녹음 시작부터 10초 이내 도착
