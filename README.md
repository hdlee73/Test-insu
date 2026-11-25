# 증권노트 (Test-insu)

보험증권(PDF·사진·카메라 스캔)을 휴대폰 안에서 읽어 아래 항목을 추출하고, 암호화해 저장한 뒤 선택한 증권을 **docx·xlsx**로 내보내는 안드로이드 앱입니다.

| 항목 | 저장 형식 |
|---|---|
| 보험계약자 / 피보험자 | 법인: 법인명 + 사업자등록번호 10자리, 개인: 성명 + 주민등록번호 13자리 ('-' 없이) |
| 보험가입금액, 보험료 | 원 단위 정수 (담보가 여러 개면 가입금액은 합계, 담보별 목록도 함께 저장) |
| 납입주기 | 월납·3개월납·6개월납·연납·일시납 |
| 보험계약기간 | 시작일·종료일 `YYYY-MM-DD` |

## 화면 구성 (확정 시안)

- **홈(시안 B)**: 그라데이션 머리말에 PDF·사진·스캔 버튼, 저장/검수 대기/30일 내 만기 요약, 최근 증권
- **검수(시안 C)**: 위쪽 원본 미리보기(확대 가능), 아래쪽 추출 필드. 칸을 누르면 원본에서 그 값을 찾은 위치를 주황 상자로 표시. 확인이 필요한 칸은 주황 테두리
- **내 증권(시안 D 방식)**: 길게 눌러 여러 건 선택 → docx/xlsx 일괄 내보내기, 검색
- 왼쪽에서 열리는 서랍 메뉴(Modal Navigation Drawer), 상세·수정, 설정·보안, 정보·사용 방법
- 뒤로가기: 서랍 닫기 → 이전 화면 → 홈에서는 두 번 눌러 종료. 저장하지 않은 검수·수정 내용이 있으면 확인 창

## 구조

MVVM + Jetpack Compose(Material 3), 수동 DI(`AppContainer`), Version Catalog(`gradle/libs.versions.toml`).

```
app/src/main/java/io/github/hdlee73/insu/
 ├ domain/   모델, 파서(PolicyParser·RowBuilder·Normalizers·IdValidators), PolicyForm 검증, Masking  ← 순수 Kotlin
 ├ data/     db(Room+SQLCipher), source(PDF·OCR), export(docx·xlsx 작성기), settings, update(GitHub Releases)
 ├ core/     AppContainer, CrashLogger
 └ ui/       theme, home, list, importer, review, detail, settings, about, InsuNavHost
```

- **텍스트 확보**: PDF는 PdfBox-Android로 글자층을 좌표와 함께 먼저 추출하고, 글자층이 없으면 PdfRenderer로 렌더링해 **ML Kit 한국어 OCR**(온디바이스). 사진은 긴 변 2000px로 줄여 OCR
- **파싱**: 좌표로 같은 줄을 묶어(표 양식 대응) 라벨 앵커 + 정규식으로 값을 찾고, 사업자번호 검증식·생년월일 검사·종료일>시작일 검사. 법인등록번호·전화번호·증권번호는 등록번호로 오인하지 않도록 제외
- **내보내기**: Apache POI 대신 최소 OOXML 작성기를 직접 구현(용량·속도). xlsx는 증권 1건 = 1행(+담보 시트), docx는 증권 1건 = 1쪽 표

## 개인정보 보호

- DB 파일 전체를 **SQLCipher**로 암호화, DB 암호는 **Android Keystore** AES-GCM 키로 보호
- 원본 PDF·사진 사본은 읽은 직후 삭제, 클라우드 백업·기기 이전에서 제외(`allowBackup=false`, data extraction rules)
- 목록·상세에서 등록번호는 가려서 표시, 전체 보기는 생체/화면잠금 확인 후 10초
- 화면 캡처·최근 앱 미리보기 차단(FLAG_SECURE, 설정에서 끌 수 있음)
- 인터넷은 업데이트 확인(api.github.com)에만 사용, 증권 내용은 외부로 전송하지 않음
- 크래시 로그(`files/logs`)는 기록 전에 주민번호·사업자번호 형태의 숫자를 지움

## Debug APK 빌드 (로컬 테스트용)

준비물: JDK 17, Android SDK(Platform 35). Android Studio를 쓰면 SDK가 함께 설치됩니다.

```bash
git clone https://github.com/hdlee73/Test-insu.git
cd Test-insu
echo "sdk.dir=$HOME/Android/Sdk" > local.properties   # Windows: sdk.dir=C\:\\Users\\<이름>\\AppData\\Local\\Android\\Sdk
./gradlew testDebugUnitTest      # 파서·내보내기 단위 테스트
./gradlew assembleDebug          # 결과: app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

GitHub에 푸시하면 **Android Debug Build** 워크플로가 테스트와 Debug APK 빌드를 실행하고, 실행 결과의 Artifacts에서 `insunote-debug-apk`를 내려받을 수 있습니다.

## 릴리스 (보류)

릴리스 빌드·GitHub Release 배포는 별도 지시가 있을 때 워크플로를 추가합니다. 서명 키는 저장소에 두지 않고 GitHub Secrets로만 전달합니다.

| Secret | 용도 |
|---|---|
| `INSU_KEYSTORE_BASE64` | keystore 파일(base64) → 워크플로에서 파일로 복원 후 `INSU_KEYSTORE_PATH`로 전달 |
| `INSU_KEYSTORE_PASSWORD`, `INSU_KEY_ALIAS`, `INSU_KEY_PASSWORD` | 서명 정보 |

`.gitignore`가 `*.jks`, `*.keystore`, `local.properties`, `keystore.properties` 등을 막고 있습니다.

## 만든이

이현덕 with Claude · hdlee73@gmail.com · https://hdlee73.github.io/Home/
