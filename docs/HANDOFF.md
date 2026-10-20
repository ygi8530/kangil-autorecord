# 인수인계 (2026-10-20, 회사 Ubuntu → 집 Windows)

## 0. Windows 새 세션 시작 프롬프트 (복사해서 사용)
```
watch-recorder 프로젝트를 이어서 진행한다. CLAUDE.md와 docs/HANDOFF.md를 먼저 읽고 현재 상태를 3줄로 요약해줘.
그다음 이 Windows PC에서 빌드 환경(JDK 17, Android SDK, adb)을 확인·준비하고,
같은 Wi-Fi의 Galaxy Watch6에 ADB 무선 연결하는 방법을 단계별로 안내해줘.
연결되면 HANDOFF의 '다음 할 일' 순서대로 실기기 검증을 진행하고 결과는 docs/verification.md에 기록해.
모든 응답은 한국어로.
```

## 1. 한 줄 요약
검증용 Spike 앱은 완성·에뮬레이터 검증 완료. **실제 Galaxy Watch6 검증은 아직 0건.** 다음 할 일은 실기기에서 T0 → T1~T6 → S1~S2 검증 후 아키텍처 결정.

## 2. 지금까지 확인된 사실

### 삼성 Voice Recorder (Wear OS) 정적 분석 — 상세: `docs/verification.md`
- 패키지 `com.samsung.android.wear.voicerecorder` (1.2.00.49, Samsung 서명 확인)
- **외부 앱이 녹음을 시작·종료시킬 공개 인터페이스 없음**
  - Bixby 경로는 signature|privileged 권한으로 잠김, 버튼 서비스는 `ACCESS_SYSTEM_KEY_DISPATCHING` 시스템 전용
  - → "우리 앱이 삼성 앱을 원격 조종" 방식(계획의 1순위)은 불가
- **삼성 앱 자체에 하드웨어 버튼 녹음 기능 있음** ("Double press/Short press → Recording started", "Press & hold → Stop and save recording")
  - Galaxy Watch Ultra Quick 버튼용으로 추정. **Watch6 홈 키 더블 프레스에서도 동작하는지가 최대 미확인 사항 (T0)**
  - 동작하면 개발 없이 P0+P1 동시 달성 가능 (단, 종료가 '길게 누르기'일 수 있음)
- 폰 동기화는 삼성 앱 내부 DB 기반 Data Layer + Wi-Fi Direct (비공개). 우리 앱 파일이 포함되는지는 S2로 확인

### Spike 앱 에뮬레이터 결과 (Wear OS 5 에뮬레이터)
- 투명 Activity → 마이크 FGS 시작 성공, Activity 체류 ~2ms, 워치페이스로 즉시 복귀
- 화면 꺼짐(Dozing) 중 녹음 유지 (24s → 24.000s)
- 저장: `.m4a` (녹음 중 ADTS → 저장 시 리먹스). 사용자가 이 방식 유지로 결정함
- **미구현**: 녹음 중 강제 종료 시 임시 파일(`files/*.aac`) 복구·게시·알림 없음 → MVP 상태 머신에서 구현

## 3. 결정 대기 (실기기 결과에 따라)
| T0 결과 | 진행 |
|---|---|
| 홈 키 2번으로 삼성 앱이 **바로 녹음 시작** | 삼성 기능 사용 (개발 최소화). 종료 방식·동기화 확인 후 사용자에게 보고 |
| 삼성 앱 **화면만 열림** | 자체 앱 MVP 진행 (계획 Phase 2). S2 결과로 동기화 가능 여부 판단, 불가면 "폰 자동 동기화 불가" 명시 |

## 4. 다음 할 일 (순서)
1. **T0** — 워치 설정 → 유용한 기능 → 키 사용자 설정 → 두 번 누르기 = 음성녹음 → 홈 키 2번 결과 확인 (설치 불필요)
2. Windows PC ↔ 워치 ADB 연결 (아래 5절) 후 `scripts/verify_samsung_recorder.sh` 실행 (Git Bash), 결과를 verification.md에 기록
3. Spike APK 설치 → `docs/device-test-B.md` 의 T1~T6, S1~S2 (PC 연결되면 Claude가 직접 adb로 측정)
4. 결과를 사용자에게 보고하고 아키텍처 결정 → MVP 구현 (`docs/plan.md` Phase 2: 상태 머신 IDLE→STARTING→RECORDING→STOPPING→SAVED→IDLE, 디바운스, 강제종료 복구, 진동 피드백, OngoingActivity 표시)

## 5. Windows 환경 준비
- **Android Studio** 설치 (JDK 17·SDK·adb 포함). `local.properties` 에 `sdk.dir=C\:\\Users\\<사용자>\\AppData\\Local\\Android\\Sdk`
- 빌드: `gradlew.bat :wear:assembleDebug`
- 워치 연결 (PC와 워치가 같은 Wi-Fi):
  1. 워치 설정 → 워치 정보 → 소프트웨어 정보 → 소프트웨어 버전 7회 탭 → 개발자 모드
  2. 개발자 옵션 → ADB 디버깅 ON, Wi-Fi를 통한 디버깅 ON
  3. 새 기기 페어링 → `adb pair <IP:페어링포트>` (6자리 코드 입력)
  4. `adb connect <IP:연결포트>` → `adb devices` 로 확인
- Galaxy Watch6는 USB 데이터 단자가 없음 → Wi-Fi ADB만 가능

## 6. 참고
- 회사 Ubuntu PC는 보안정책으로 워치 연결 불가였음 (그래서 Windows로 이동)
- 삼성 APK 원본은 저작권 문제로 저장소에 포함하지 않음 (APKMirror에서 재다운로드 가능)
- 사용자 선호: 한국어, 간결한 단계별 안내
