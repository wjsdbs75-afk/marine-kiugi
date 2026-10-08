# 마린 키우기 — 작업 안내 (Claude 용)

이 게임은 **두 곳에 동시에** 올라가 있다. 게임을 고칠 때는 항상 둘 다 갱신한다. 한쪽만 올리지 말 것.

**커밋과 푸시는 전부 Claude 가 한다.** 사용자는 개발자가 아니다. git 작업이나 파일 올리기를 사용자에게 시키지 말고, `main` 에 직접 커밋·푸시한다 (브랜치나 PR 을 만들지 않는다). 푸시가 권한 문제로 막히면 "이 세션에 저장소가 쓰기 권한으로 연결돼 있지 않다"고 바로 알린다.

- Claude 아티팩트: https://claude.ai/artifact/Vmor47U3WvMdi9XY2TsKkA
- 이 저장소 `main` → GitHub Pages: https://wjsdbs75-afk.github.io/marine-kiugi/ (안드로이드 앱이 여는 주소)

## 자동 동기화 (아티팩트 → 저장소)

사용자는 주로 저장소가 연결되지 않은 대화(Cowork)에서 게임을 고친다. 그 대화에서는 아티팩트만 갱신할 수 있으므로, **"마린 키우기 자동 배포"** 라는 예약 작업(루틴)이 이 저장소를 붙인 채 실행되어 아티팩트의 최신본을 `game.html` 로 가져와 `main` 에 푸시한다. 그러면 Pages 가 배포하고, 앱은 껐다 켤 때 새 버전을 받는다.

- 저장소 없는 대화에서 게임을 고쳤다면: 아티팩트를 게시한 뒤 그 예약 작업을 바로 실행시킨다 (`fire_trigger`). 사용자가 따로 할 일은 없다.
- 저장소가 연결된 대화라면: 아래 "게임을 고칠 때 순서"대로 직접 커밋·푸시한다.
- 어느 쪽이든 아티팩트와 저장소의 `game.html` 은 같은 내용이어야 한다. 다르면 아티팩트가 기준이다.

## 원본은 `game.html` 하나

`game.html` 은 아티팩트에 게시하는 파일 그대로다. `<!doctype>`, `<html>`, `<head>`, `<body>` 없이 본문만 들어 있다 (아티팩트는 게시할 때 뼈대를 씌우고, Pages 용 뼈대는 `tools/build_site.py` 가 씌운다). 4MB 남짓이고 base64 자원 줄(`BGM_B64`, `SHOT_B64`, `ATLAS_URL`, `MEDIC_URL` 등)은 한 줄이 수십 KB~2MB 라 아주 길다. 통째로 읽지 말고 필요한 줄만 읽고, 고칠 때는 Edit 로 해당 부분만 바꾼다.

## 게임을 고칠 때 순서

1. **어긋났는지 확인.** Artifact `read` 로 아티팩트를 읽고, 저장된 파일을 `python3 tools/from_artifact.py <파일>` 로 풀어 `git diff --stat game.html` 을 본다. 차이가 있으면 아티팩트가 앞선 것이니 그 상태에서 시작한다.
2. `game.html` 을 고친다.
3. 파일 안의 `VERSION`, `VERSION_DATE`, `VERSION_NOTE` 를 올린다 (화면 구석과 설정 탭에 표시된다).
4. **확인.** `python3 tools/build_site.py /tmp/site` 로 만든 `index.html` 을 브라우저(Playwright)로 열어 콘솔 오류가 없는지, `window.__mkBack` 이 함수인지 본다.
5. **아티팩트 게시.** Artifact `publish`, `url` 은 위 아티팩트 주소, `file_path` 는 `game.html`.
6. **저장소 반영.** `game.html` 을 커밋해서 `main` 에 push. `pages.yml` 이 1~2분 안에 배포한다. `version.json` 의 `version` 이 새 값이면 반영된 것이다.
7. 사용자에게 "앱을 완전히 껐다 켜면 새 버전이 뜬다"고 알려 준다. APK 를 다시 빌드할 필요는 없다.

앱이 게임을 확인하고 받는 동안 띄우는 화면은 앱 안에 든 `loading.html` 이다 (게임 안의 로딩 화면 `#boot` 과 같은 모양이라 넘어갈 때 티가 안 난다). 원본은 `tools/loading_src.html`, `python3 tools/build_loading.py` 가 글꼴을 잘라 넣어 `android/app/src/main/assets/` 와 `ios/MarineKiugi/` 에 쓴다. 게임의 로딩 화면 모양이나 작전 팁(`TIPS`), 헬멧 그림(`#bootHelm`)을 바꾸면 이것도 다시 만들어 커밋한다 (앱 빌드가 다시 돈다). 받는 동안 앱은 막대를 60% 까지만 채우고 그 값을 넘겨준다 (안드로이드 `MKShell.bootFrac()`, 아이폰 `window.MKBoot.frac`). 게임의 `#boot` 가 거기서 이어 채운다. 작전 팁은 두 화면 모두 시계(3.2초)에 맞춰 바뀐다.

글꼴은 Google Fonts 에만 기대지 않고 `game.html` 안의 `<style id="mkFonts">` 에 게임에 쓰인 글자만 잘라 넣어 둔다 ('MK Display', 'MK Body'). 새 글자(대사·이름 등)를 넣었으면 `python3 tools/embed_fonts.py` 를 돌린다 (안 돌려도 빠진 글자만 Google Fonts 로 보인다).

`android/` 를 고쳤을 때만 `android.yml` 이 APK 를 다시 빌드해 Releases 의 `apk` 에 올린다. `ios/` 를 고쳤을 때만 `ios.yml` 이 아이폰 앱을 빌드해 TestFlight 에 올린다 (게임만 고쳤을 때는 둘 다 필요 없다).

## 앱과 페이지 사이의 약속 (깨면 앱이 망가진다)

| 약속 | 내용 |
|---|---|
| `window.__mkBack()` | 안드로이드 뒤로 가기 때 앱이 부른다. 처리했으면 `true`, 닫을 것이 없으면 `false`(앱 종료). 앱은 이 함수가 있어야 "게임이 제대로 떴다"고 판단한다. |
| `--safe-top`, `--safe-bottom` | 앱이 `<html>` 에 넣어 주는 CSS 변수(px). 게임의 `#safeProbe` 가 읽어 UI 를 민다. 값이 바뀌면 `resize` 이벤트가 온다. |
| `window.MKShell` | 앱이 주입하는 객체 (`safeTop()`, `safeBottom()`). `build_site.py` 가 만든 머리말 스크립트가 쓴다. 브라우저에는 없다. |
| `version.json` | 배포마다 내용이 달라져야 한다 (`commit` 값). 앱은 켤 때 이 파일을 보고, 지난번과 다르면 `index.html` 을 통째로 새로 받아 기기에 저장한다. |
| `index.html` | 앱은 받은 파일이 `</html>` 로 끝나야 온전하다고 본다 (`build_site.py` 가 그렇게 만든다). 앱은 이 사본을 Pages 주소 그대로 띄우므로, 게임에 파일을 따로 두지 말고 지금처럼 한 장에 다 넣을 것 (따로 둔 파일은 오프라인에서 안 뜬다). |
| 저장 | `localStorage` 의 `marine-kiugi-v1`. 페이지 주소(origin)에 묶여 있으니 `android/gradle.properties` 의 `gameUrl` 을 바꾸지 말 것. |

## 그 밖에

- **외부 유료 도구(Creative Claw 이미지·음성 생성, 배경 지우기 등)를 쓸 때는 반드시 사용자에게 알린다.** 쓰기 전에 무엇을 몇 장 만들지, 쓴 뒤에는 사용한 크레딧과 남은 크레딧을 보고한다.
- **이미지는 한 시트에 몰아서 만든다.** 몬스터·아이콘·아이템처럼 작은 에셋이나 여러 시안은 한 장에 격자로 여러 개를 그리게 한 뒤 잘라 쓴다 (장마다 따로 생성하지 않는다). 크레딧을 크게 아낀다. 한 장에 담기 어려운 큰 그림만 따로 만든다.

- Pages 는 "GitHub Actions" 방식으로 배포한다 (Settings → Pages → Source).
- 안드로이드 빌드는 이 저장소의 Actions 에서만 한다. Gradle 8.10.2 (워크플로의 setup-gradle 이 설치, 저장소에 gradlew 는 없음) + AGP 8.7.3, JDK 17, 외부 라이브러리 없음.
- 공개 배포하는 앱이다. 서명 키는 저장소에 넣지 않고 Actions Secrets (`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`) 에만 둔다. 키 파일이나 비밀번호를 커밋하지 말 것. 키를 바꾸면 기존 앱 위에 덮어 설치가 안 되어 사용자들의 게임 저장이 날아가니 바꾸지 말 것.
- `android.yml`, `ios.yml` 파일 이름을 바꾸지 말 것 (앱 버전·빌드 번호가 실행 횟수라 1부터 다시 시작한다).
- 아이폰 앱(`ios/`)은 WKWebView 하나. 저장 사본을 `loadHTMLString(baseURL: Pages 주소)` 로 띄워 origin 을 맞춘다. 번들 ID `io.github.wjsdbs75afk.marinekiugi` 와 Info.plist 의 `GameURL` 을 바꾸지 말 것. 서명은 Secrets 의 App Store Connect API 키(`APPSTORE_*`, `APPLE_TEAM_ID`)로 Xcode 가 자동으로 한다. 키를 커밋하지 말 것.

## 출시 전에 뺄 것 (테스트 기능)

사용자가 출시를 이야기하거나 출시가 가까워 보이면 **먼저 이 목록을 상기시킨다** (사용자가 그렇게 해 달라고 했다).

- 스테이지 바로 가기: 상점·강화 탭을 번갈아 10번 누르면 열리는 창 (`game.html` 의 `devOv`, `devTabTap`, `openDev`, `devUnlock`). 잠긴 스테이지도 출격된다.
- 분대 슬롯 구매·광고 슬롯이 결제·광고 없이 바로 지급된다 ("테스트: 결제 없이 지급").
- 보석 상점·분대 슬롯 결제가 실제 결제 없이 지급된다 (상점 안내문 "지금은 테스트 버전이라…").
- 광고가 5초짜리 가짜 화면이다 (`테스트 광고`). 실제 광고 SDK 를 붙여야 한다.
- 설정의 저장 코드(8자리)는 임시 기능이다. 계정 연동으로 바꿀지 정한다.
- 헬스테이지 주간 랭킹의 다른 플레이어 기록은 가상 데이터다 (`hellBoard`, 주마다 같은 씨앗으로 1,500명). 서버(예: Firebase)에 기록을 올리고 받아 오는 실제 랭킹으로 바꿔야 한다. 랭킹 보상(보석 최대 2만 개)도 지금은 버튼만 누르면 지급된다 (`rankClaim`, "테스트: 바로 지급").

## 처음 설정 점검표 (끝난 항목은 지울 것)

저장소에 처음 올린 뒤 한 번씩 확인한다. `gh` 로 할 수 있으면 직접 하고, 권한이 없으면 사용자에게 그 한 가지만 부탁한다.

- [ ] 사용자가 실제 기기에서 확인: 설치, 뒤로 가기, 비행기 모드에서 다시 켜기, 게임을 고쳐 올린 뒤 껐다 켜면 새 버전이 뜨는지.
