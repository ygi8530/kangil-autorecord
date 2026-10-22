# 개발 인수인계 (2026-10-22, 집 Windows PC)

## 0. 새 세션 시작 프롬프트 (복사해서 사용)
```
kangil-autorecord(Quick Rec) 프로젝트를 이어서 진행한다. CLAUDE.md, README.md, docs/HANDOFF.md를 먼저 읽고 현재 상태를 3줄로 요약해줘.
그다음 빌드 환경(JDK 17, Android SDK, adb)을 확인하고, 워치·폰 무선 ADB 연결을 단계별로 안내해줘.
HANDOFF의 '다음 할 일' 순서대로 진행하고 검증 결과는 docs/verification.md에 기록해. 모든 응답은 한국어로.
```

## 1. 현재 상태
- **P0 홈 키 더블 프레스 녹음 시작·종료: 완료** (워치 앱 `wear/`, 실기기 T1~T6 통과)
- **P1 폰 자동 동기화: 자체 폰 앱(`mobile/`)으로 구현·동작 확인**
  - 삼성 Voice Recorder 경유는 불가 확정 (T0: 홈 키 2번 = 삼성 앱 화면만 열림 / S2: 외부 파일 인식·동기화 안 함)
  - Wearable Data Layer로 워치→폰 전송, 끊김 시 대기열 보관 후 재연결 즉시 전송, 폰 저장·크기 확인 후 ack
- 폰 앱 0.4: iOS 스타일(kangil-train 앱과 같은 inset grouped 디자인) 보관함 — 날짜별/월별/즐겨찾기, 검색, 인라인 재생(속도·10초 이동), 이름·메모·공유·열기·삭제, 오늘 녹음 강조, 당겨서 새로고침, 여러 개 선택(길게 누르기·전체 선택 → 일괄 즐겨찾기·공유·삭제), 설정(워치에서 삭제 토글), 빨강~주황 앱 아이콘(어두운/나무 테마에서 잘 보이게)
- 버전: 워치 0.3.1 (versionCode 4), 폰 0.4.1 (versionCode 5). 설치본: `dist/` (R8 축소 release 빌드, debug 키 서명)
- 수정 이력: 폰 0.3 삭제 시 크래시 — `createDeleteRequest`에 generic `content://media/external/...` URI를 넘겨 `Invalid Uri`. VOLUME_NAME으로 볼륨별 URI(`external_primary`)를 만들도록 수정, 삭제 실패는 Toast로 처리
- 폰 0.4 전송 불가 버그: R8 리소스 축소가 `android_wear_capabilities`를 미사용으로 지워 폰이 `watchrecorder_phone` capability를 광고하지 않음 → 워치가 폰을 못 찾고 대기열에만 쌓음. `tools:keep`은 values 파일에선 무시됨 → `res/raw/keep.xml`로 옮겨 해결(0.4.1). release 빌드 후 `aapt2 dump resources ... | grep android_wear_capabilities`로 확인할 것
- 워치 0.3.1: 재연결 시 즉시 전송(doze 중 job 지연 회피), 동시 sync 1개 제한(중복 전송 제거)
- 전송 경로: BT가 꺼져도 워치 Wi-Fi + 폰 인터넷이면 Data Layer가 클라우드 경유로 전송함(2026-10-22 확인). 진짜 오프라인→재연결 즉시 전송은 미검증(워치 Wi-Fi도 끄고 테스트 필요)

## 2. 구조
```
wear/   (Wear OS, minSdk 33, Kotlin, play-services-wearable만 사용)
  ToggleActivity       더블 프레스 대상. 투명 테마, START/STOP 전달 후 즉시 finish()
  RecorderService      FGS(microphone). AAC ADTS로 녹음 → 저장 시 m4a 리먹스 → MediaStore Recordings/WatchRecorder/
                       → SyncQueue 등록 + SyncJobService 예약. 시작 시 남은 .aac(강제종료 잔여) 복구
  SyncQueue            SharedPreferences 대기열(id, uri, name, size, sentAt) + deleteAfterSync 설정
  SyncJobService       JobScheduler. 폰(capability watchrecorder_phone) 찾아 ChannelClient로 전송.
                       sentAt 후 5분 내 ack 없으면 재전송. 대기열이 있으면 15분 주기 persisted job 유지
  WatchListenerService CAPABILITY_CHANGED(폰 재연결 → 즉시 sync), MESSAGE_RECEIVED(/watchrecorder/ack → 대기열 제거,
                       설정 켜져 있으면 워치 파일 삭제), DATA_CHANGED(/watchrecorder/settings)
  SyncProtocol         경로·키 상수 (mobile과 동일하게 유지)

mobile/ (폰, minSdk 29, Jetpack Compose + Material3 + play-services-wearable)
  PhoneListenerService CHANNEL_EVENT 수신 → MediaStore Recordings/WatchRecorder/ 저장(IS_PENDING) → 크기 검증 → ack.
                       이미 받은 id면 저장 없이 ack만 (ReceivedStore)
  Library              MediaStore 조회 + 메타데이터(제목·메모·즐겨찾기, 파일명 키) + 삭제(소유 아니면 createDeleteRequest)
  WatchLink            연결 상태(NodeClient), deleteAfterSync를 DataItem으로 게시(워치 재연결 시 자동 전달)
  ui/                  Theme(iOS 팔레트), Grouped(섹션·행·세그먼트), LibraryScreen(목록·선택 모드), SettingsScreen, Player, Format
  res/                 ic_launcher(adaptive: 그라데이션 배경 + 흰 마이크, wear와 동일)
```

### 동기화 프로토콜 (`SyncProtocol.kt`, 양쪽 동일)
| 방향 | 종류 | 경로 | 내용 |
|---|---|---|---|
| 워치→폰 | Channel | `/watchrecorder/file/<id>/<size>/<urlencoded name>` | m4a 바이트. 워치는 출력 스트림만 닫고(half-close) 채널은 폰이 닫음 |
| 폰→워치 | Message | `/watchrecorder/ack` | payload = id. 폰 저장·크기 확인 후에만 전송 |
| 폰→워치 | DataItem | `/watchrecorder/settings` | `deleteAfterSync: Boolean` |
| 폰 capability | `wear.xml` | `watchrecorder_phone` | 워치가 전송 대상 노드를 찾는 기준 |

**Data Layer 필수 조건:** 두 앱의 `applicationId`(`com.yoonki.watchrecorder`)와 **서명 키가 같아야** 함. 지금은 이 PC의 debug 키로 서명 → 다른 PC에서 한쪽만 빌드하면 통신 안 됨. 배포용이면 release keystore를 만들어 두 모듈에 같은 signingConfig 적용 필요.

## 3. 검증 기록 요약 (`docs/verification.md`)
- T0 삼성 앱: 화면만 열림 / T1~T6 Quick Rec 통과 / S1 삼성 앱 20초 내 동기화 / S2 삼성 폴더 경유 불가
- 자체 동기화(2026-10-22 01:18~01:19, 폰 logcat): 연결 상태 녹음 3건 `saved ... → acked`, 중복 수신 시 `already saved, re-acking` 확인. 사용자 확인: 블루투스 끈 상태 녹음 → 재연결 후 자동 수신
- 원본 로그(`docs/raw/`)는 기기 전체 로그라 개인 정보가 섞일 수 있어 저장소에 올리지 않음 (.gitignore)

## 4. 다음 할 일
1. ~~"워치에서 삭제" 토글~~ **검증 완료 (2026-10-22, verification.md)**. 참고로 원래 내용: — 폰 설정에서 켜고 녹음 → 폰 수신 후 워치 `/sdcard/Recordings/WatchRecorder/`에서 사라지는지, 워치 logcat `ack ...: deleted from watch` 확인
2. 폰 앱 0.4 실사용 피드백 반영 (0.4 화면은 폰이 잠겨 있어 캡처 검증 못 함 — 실행·크래시 없음만 확인)
3. 장시간 녹음(30분~1시간, 20~30MB) 전송 안정성·소요 시간 측정. 필요하면 진행률 표시
4. 워치 쪽 대기열 상태 노출(예: 알림/타일에 "폰 전송 대기 N개")
5. 배포용 release 서명 통일 (위 2절)
6. P3 후보: 워치 타일(상태·최근 녹음), 최대 녹음 시간·저장공간 부족 처리, 폰 알림(새 녹음 도착)

## 5. 개발 환경 메모 (Windows)
- `local.properties`: `sdk.dir=C\:\\Users\\<사용자>\\AppData\\Local\\Android\\Sdk`, JDK 17(`JAVA_HOME`)
- 빌드: `gradlew.bat :wear:assembleDebug :mobile:assembleDebug`
- 워치·폰 무선 ADB: README 1-3, 1-4절. 워치는 폰 블루투스 연결 중 Wi-Fi를 끊으므로 **워치 ADB 작업 중엔 폰 블루투스 OFF**, 전송 테스트 땐 ON (이때 워치 ADB 끊김 → 폰 logcat으로 확인)
- PowerShell에서 공백 있는 extra: `adb shell "am start ... --es relpath 'A B/'"`처럼 명령 전체를 한 문자열로
- PowerShell의 `>`는 바이너리를 깨뜨림 → 스크린샷은 Git Bash `adb exec-out screencap -p > x.png`
- 로그: 워치 `adb logcat -s WatchRecorder`, 폰 `adb logcat -s WatchRecorder`
- 이전 검증용 저장소: `github.com/ygi8530/watch-recorder` (Spike·검증 이력)
