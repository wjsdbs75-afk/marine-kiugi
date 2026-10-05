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

`android/` 를 고쳤을 때만 `android.yml` 이 APK 를 다시 빌드해 Releases 의 `apk` 에 올린다.

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

- Pages 는 "GitHub Actions" 방식으로 배포한다 (Settings → Pages → Source).
- 안드로이드 빌드는 이 저장소의 Actions 에서만 한다. Gradle 8.10.2 (워크플로의 setup-gradle 이 설치, 저장소에 gradlew 는 없음) + AGP 8.7.3, JDK 17, 외부 라이브러리 없음.
- 공개 배포하는 앱이다. 서명 키는 저장소에 넣지 않고 Actions Secrets (`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`) 에만 둔다. 키 파일이나 비밀번호를 커밋하지 말 것. 키를 바꾸면 기존 앱 위에 덮어 설치가 안 되어 사용자들의 게임 저장이 날아가니 바꾸지 말 것.
- `android.yml` 파일 이름을 바꾸지 말 것 (앱 버전 번호가 실행 횟수라 1부터 다시 시작한다).

## 처음 설정 점검표 (끝난 항목은 지울 것)

저장소에 처음 올린 뒤 한 번씩 확인한다. `gh` 로 할 수 있으면 직접 하고, 권한이 없으면 사용자에게 그 한 가지만 부탁한다.

- [ ] 사용자가 실제 기기에서 확인: 설치, 뒤로 가기, 비행기 모드에서 다시 켜기, 게임을 고쳐 올린 뒤 껐다 켜면 새 버전이 뜨는지.
