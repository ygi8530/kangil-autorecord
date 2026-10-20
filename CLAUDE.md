# watch-recorder

Galaxy Watch6 홈 키 **더블 프레스 = 녹음 시작 / 다시 더블 프레스 = 종료·저장** 을 구현하는 Wear OS 앱.
녹음 UI 앱이 목적이 아니라 "버튼 조작 없는 빠른 시작·종료"가 핵심.

## 작업 규칙
- **모든 응답·진행 로그·보고는 한국어로.** (사용자 요청)
- 새 세션은 먼저 `docs/HANDOFF.md` 를 읽고 이어서 진행.
- 기술적으로 불가능한 것은 가능한 것처럼 보고하지 않는다. 검증 결과는 근거(명령 출력)와 함께 `docs/verification.md` 에 기록.
- 삼성 비공개 API 사용, 마이크 표시·권한·알림 우회 금지.
- 사용자 동의 없이 스마트폰용 앱을 만들지 않는다.
- 우선순위: P0 더블클릭 시작·종료 > P1 삼성 Voice Recorder·폰 자동 동기화 유지 > P2 안정성·예외 처리 > P3 편의 기능.

## 구조
- `wear/` 단일 모듈, Kotlin, 외부 라이브러리 없음 (framework API만), minSdk 33 / targetSdk 35
  - `ToggleActivity` — 더블 프레스 대상. 투명 테마, 토글 후 즉시 `finish()`
  - `RecorderService` — `foregroundServiceType=microphone`. 녹음 중엔 AAC ADTS(강제종료에도 재생 가능), 저장 시 재인코딩 없이 `.m4a` 로 리먹스 → MediaStore `Recordings/WatchRecorder/`
- `docs/plan.md` 승인된 계획, `docs/verification.md` 검증 기록, `docs/device-test-B.md` 실기기 수동 테스트 안내
- `scripts/verify_samsung_recorder.sh` 삼성 녹음 앱 정보 수집 (bash, 읽기 전용)

## 빌드·설치
```
./gradlew :wear:assembleDebug          # Windows: gradlew.bat :wear:assembleDebug
adb install -r wear/build/outputs/apk/debug/wear-debug.apk
adb logcat -s WatchRecorder ActivityTaskManager
```
`local.properties`(gitignore)에 `sdk.dir=` 필요. JDK 17.
