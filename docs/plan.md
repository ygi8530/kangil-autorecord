# Galaxy Watch6 홈 버튼 더블클릭 녹음 토글 — 실행 계획

## Context
- 목표: 홈 키 **더블 프레스 1회 = 녹음 시작, 2회 = 종료·저장**. 녹음 UI 개발이 목적이 아님.
- 최대 의사결정: **P0(더블클릭 토글)와 P1(삼성 Voice Recorder + 폰 자동 동기화 `Recordings/Sounds/Watch/`)를 동시에 만족할 수 있는가.**
- 현 상태: `/home/yoonki/workspace/watch-recorder`는 빈 디렉터리. Ubuntu에 Java, Android SDK, adb 모두 없음. 이전 계획 파일 없음.
- 사전 조사 결과(검증 전 가설):
  - Watch6(One UI Watch) 홈 키 **더블 프레스 → 임의의 설치 앱 실행** 지정 가능(Samsung 공식 지원 문서). 이것이 P0의 유일한 공식 트리거. 키 이벤트 자체를 앱이 가로채는 공개 API는 없음 → "더블클릭 = 우리 앱 Activity 실행"으로 설계.
  - 삼성 워치 Voice Recorder의 녹음 시작/종료용 **공개 Intent/API 문서는 발견되지 않음**. 동기화 방식도 비공개. → 실제 기기에서 exported 컴포넌트를 확인해야 함. 없으면 P0+P1 동시 달성은 공식 방법으로 **불가능**으로 보고.
  - Android 14+: `foregroundServiceType="microphone"`는 while-in-use 제한 → **백그라운드에서 시작 불가, 앱이 포그라운드(Activity 실행 중)일 때 시작해야 함**. 더블 프레스가 Activity를 띄우므로 이 조건은 충족 가능. 즉 "UI 없이 시작"은 불가능하고, "투명 Activity가 순간적으로 떴다 닫히는" 최소 전환이 현실적 하한.

---

## Phase 0 — 개발 환경 (sudo 없이 홈 디렉터리 설치)
1. JDK 17 (Temurin tarball → `~/opt/jdk-17`), Android cmdline-tools → `~/Android/Sdk`
2. `sdkmanager "platform-tools" "platforms;android-34" "build-tools;34.0.0"` (기기 API 확인 후 조정)
3. Gradle 배포판으로 wrapper 생성 (Kotlin + AGP, 단일 `:wear` 모듈)
4. 워치: 개발자 옵션 → ADB 디버깅 + 무선 디버깅 → `adb pair` / `adb connect` (사용자 조작 필요)

## Phase 1 — 실제 기기 검증 (앱 개발 착수 전 게이트)
모든 명령과 결과는 `docs/verification.md`에 기록.

**V1. 기기 정보**: `getprop ro.build.version.release/sdk`, One UI Watch 버전.

**V2. 삼성 Voice Recorder 외부 제어 가능성** (공개·exported 컴포넌트만 대상, 비공개 API/권한 우회 금지)
- `pm list packages | grep -i -E "voice|record"` → 패키지명 확인
- `dumpsys package <pkg>` → exported activity/service/receiver, intent-filter, 요구 permission
- `pm path` → APK pull 후 `aapt2 dump xmltree AndroidManifest.xml`, `shortcuts.xml`/App Actions(Bixby/Assistant capability) 존재 여부
- 표준 Intent 테스트: `am start -a android.provider.MediaStore.RECORD_SOUND`, 런처 Activity 실행 → 녹음이 **자동 시작되는지 / 버튼 탭이 필요한지** 기록
- 판정: 문서화된(또는 명시적으로 exported된 공개) start/stop 인터페이스 존재 여부

**V3. 삼성 동기화 동작**
- 삼성 앱으로 녹음 → 폰 `Recordings/Sounds/Watch/` 도착 확인(시간 측정)
- 실험: 우리 테스트 APK가 **공개 MediaStore API**로 워치의 `Recordings/` 하위(예: `Recordings/Voice Recorder/`)에 파일을 넣었을 때 삼성 앱 목록/폰 동기화에 포함되는지 확인. (포함되면 P1 부분 달성 경로, 안 되면 불가 판정)

**V4. 더블 프레스 트리거 특성** (최소 Spike APK로 측정)
- Spike APK: 투명 Activity 1개 + logcat 타임스탬프. 더블 프레스 대상으로 지정
- 측정: 화면 꺼짐/켜짐/워치페이스/타 앱 상태별 실행 여부, 지연(`ActivityTaskManager: Displayed`), 우리 Activity가 이미 떠 있을 때 재실행(onNewIntent) 동작, 연타 시 중복 실행 빈도
- 투명 Activity에서 FGS(microphone) 시작 → `finish()` 후 워치페이스 복귀 여부, 화면 off 후 녹음 지속 여부

**결정 게이트 (결과를 사용자에게 보고 후 진행)**
| 결과 | 아키텍처 |
|---|---|
| A. 공개 start/stop 인터페이스 존재 | 1순위: 투명 단축 Activity가 삼성 앱에 Intent 전달만 하는 소형 앱 |
| B. 삼성 앱 실행만 가능(탭 필요) | P0+P1 동시 불가로 명시. 대안 제시: 더블 프레스→삼성 앱 직접 지정(개발 불필요, 1탭 필요) vs 자체 앱 |
| C. V3 실험에서 MediaStore 파일이 동기화됨 | 자체 녹음 + 삼성 동기화 경로 재사용 (P0+P1 동시 달성) |
| D. 그 외 | 2순위: 자체 독립 앱 MVP, 폰 자동 동기화 불가 명시 |

예상: 공개 인터페이스가 없을 가능성이 높아 B/D 가능성이 큼 — 이 경우 그대로 보고하고 D로 진행(사용자 확인 후).

## Phase 2 — 자체 앱 MVP (경로 C/D, 가장 간결한 구조)
패키지 예: `com.yoonki.watchrecorder`, Kotlin, minSdk 30(Wear OS 3+), 단일 모듈.

```
wear/src/main/java/.../
  ToggleActivity.kt    // 런처 = 더블프레스 대상. 투명 테마, UI 없음
  PermissionActivity.kt// 최초 1회 RECORD_AUDIO / POST_NOTIFICATIONS 요청만
  RecorderService.kt   // FGS type=microphone, MediaRecorder
  RecorderState.kt     // 상태 머신 + 영속화
  Feedback.kt          // 진동/Toast 패턴
```

**ToggleActivity**: `Theme.Translucent.NoTitleBar`, `excludeFromRecents`, `noHistory`, `launchMode=singleTask`, 빈 `taskAffinity`. `onCreate`/`onNewIntent`에서 권한 확인 → 상태에 따라 Service에 START/STOP 전달 → 즉시 `finish()` (`overridePendingTransition(0,0)`). 권한 없으면 PermissionActivity로.

**상태 머신 (RecorderState)**: `IDLE → STARTING → RECORDING → STOPPING → SAVED → IDLE`
- `SharedPreferences.commit()`으로 상태 + 현재 파일 경로 + 전이 시각 영속화
- STARTING/STOPPING 중 토글 무시, 마지막 전이 후 ~1.5s 이내 토글 무시(디바운스 → 중복 이벤트 처리)
- 재실행 시 정합성 복구: 저장 상태가 RECORDING/STARTING인데 서비스가 실제 동작 중이 아니면(서비스 내 static 플래그/바인딩으로 확인) → 부분 파일을 저장 처리하고 IDLE로 복원, 사용자에게 "이전 녹음이 중단되어 저장됨" 알림
- 실패(마이크 점유, MediaRecorder 예외, FGS 시작 거부) → 리소스 해제, IDLE 복원, 오류 진동 + Toast + 알림

**RecorderService**: `startForeground(..., FOREGROUND_SERVICE_TYPE_MICROPHONE)`, 진행 알림 + Wear `OngoingActivity`(워치페이스에 녹음 중 아이콘 표시 → 상태 확인 수단), 알림 탭/액션 = 정지. 화면 off 대비 `PARTIAL_WAKE_LOCK`(필요성 V4에서 측정).
- 녹음 포맷: **AAC ADTS(.aac)** 채택 — 프로세스가 강제 종료돼도 파일이 재생 가능(MP4는 moov 누락 시 손상). 16kHz/mono/64kbps 내외.
- 녹음 중엔 앱 전용 디렉터리에 기록 → 종료 시 MediaStore Audio `Recordings/WatchRecorder/`(또는 V3에서 동기화가 확인된 경로)로 이동.
- 시스템 마이크 표시(초록 점)·FGS 알림은 그대로 유지(우회 없음).

**피드백**: 시작=짧은 진동 1회, 저장=2회, 오류=긴 진동 + Toast.

**동기화 (D 경로)**: 폰 앱 없이 워치→폰 파일 전송하는 공식 방법은 Wearable Data Layer가 폰 측 앱을 요구하므로 해당 없음. 결과 보고에 "폰 자동 동기화 불가"를 명시하고 수동 수단(`adb pull`, 필요 시 P3에서 워치 공유 시트/클라우드 업로드 검토)만 안내. 폰 앱은 사용자 동의 없이 만들지 않음.

## Phase 3 — P3 (MVP 검증 후, 선택)
Tile로 상태/최근 녹음 표시, 최대 녹음 시간·저장공간 부족 처리 등.

---

## 검증 (End-to-End, ADB)
1. `./gradlew :wear:assembleDebug` → `adb install -r`
2. 워치 설정에서 홈 키 더블 프레스 = 이 앱 지정 (사용자 조작)
3. 시나리오별 `adb logcat -s WatchRecorder ActivityTaskManager` + `dumpsys activity services` 확인:
   - 더블프레스 → 녹음 시작(마이크 표시 등장) → 워치페이스 자동 복귀 → 화면 off 2분 → 더블프레스 → 저장
   - 연타(빠른 더블프레스 2회 연속), STARTING 중 재입력
   - 녹음 중 `adb shell am kill`/`force-stop` → 재실행 시 상태 복구·부분 파일 재생 가능
   - 권한 거부, 다른 앱이 마이크 점유 중
4. `adb pull` 로 저장 파일 재생 확인, 길이·끊김 확인
5. 화면 전환 측정: Activity 표시~finish 시간(ms) 기록, 사용자 눈에 보이는 전환 여부 보고
6. 최종 보고: P0/P1 각각 달성/불가 + 근거(명령 출력)

## 사용자 조작이 필요한 지점
- 워치 개발자 모드·무선 디버깅 활성화 및 페어링 코드 전달
- 워치 홈 키 더블 프레스 대상 지정
- 폰 `Recordings/Sounds/Watch/` 도착 확인 (폰 adb 연결 가능하면 자동화)
