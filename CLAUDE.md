# kangil-autorecord (Quick Rec)

Galaxy Watch6 홈 키 **더블 프레스 = 녹음 시작 / 다시 더블 프레스 = 종료·저장**, 저장된 녹음은 폰 앱으로 자동 전송.
녹음 UI 앱이 목적이 아니라 "버튼 조작 없는 빠른 시작·종료"가 핵심.

## 작업 규칙
- **모든 응답·진행 로그·보고는 한국어로.** (사용자 요청)
- 새 세션은 먼저 `docs/HANDOFF.md` 를 읽고 이어서 진행. 사용법·설치는 `README.md`.
- 기술적으로 불가능한 것은 가능한 것처럼 보고하지 않는다. 검증 결과는 근거(명령 출력)와 함께 `docs/verification.md` 에 기록.
- 삼성 비공개 API 사용, 마이크 표시·권한·알림 우회 금지.
- 폰 앱(`mobile/`)은 사용자 동의로 추가됨(2026-10-22). 다른 기기용 앱은 사용자 동의 없이 만들지 않는다.
- 우선순위: P0 더블클릭 시작·종료 > P1 폰 자동 동기화 > P2 안정성·예외 처리 > P3 편의 기능.

## 구조
- `wear/` 워치 앱 (minSdk 33 / targetSdk 35). 라이브러리는 `play-services-wearable`만
  - `ToggleActivity`(더블 프레스 대상, 투명·즉시 finish) → `RecorderService`(FGS microphone, ADTS 녹음 → m4a 리먹스 → MediaStore)
  - `SyncQueue` / `SyncJobService` / `WatchListenerService` — 폰 전송 대기열·재시도·ack 처리
- `mobile/` 폰 앱 (minSdk 29, Jetpack Compose). `PhoneListenerService` 수신·ack, `Library` 보관함, `ui/` iOS 스타일 화면
- 두 앱의 `applicationId`·서명이 같아야 Data Layer 통신 가능. 프로토콜은 양쪽 `SyncProtocol.kt` (동일하게 유지)
- `docs/` 계획·검증·인수인계, `dist/` 설치용 APK(같은 debug 키로 서명된 짝)

## 빌드·설치
```
./gradlew :wear:assembleDebug :mobile:assembleDebug   # Windows: gradlew.bat ...
adb -s <워치> install -r wear/build/outputs/apk/debug/wear-debug.apk
adb -s <폰>   install -r mobile/build/outputs/apk/debug/mobile-debug.apk
adb logcat -s WatchRecorder
```
`local.properties`(gitignore)에 `sdk.dir=` 필요. JDK 17. dist/ 갱신 시 두 APK를 같은 빌드에서 함께 복사.
