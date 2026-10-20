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
- 실기기 확인: 방법 B 진행 후 기록

## 실기기 검증 (방법 B)
- 대기 중 (`docs/device-test-B.md`의 T1~T6, S1~S2)
