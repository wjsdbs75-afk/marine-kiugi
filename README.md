# 마린 키우기

강화복 해병 서바이벌 게임. 게임 본체는 웹 페이지 한 장이고, 안드로이드 앱은 그 페이지를 화면 가득 띄우는 껍데기입니다.

| | 주소 |
|---|---|
| 게임 (브라우저) | https://wjsdbs75-afk.github.io/marine-kiugi/ |
| 안드로이드 APK | https://github.com/wjsdbs75-afk/marine-kiugi/releases/download/apk/marine-kiugi.apk |
| 지금 배포된 버전 확인 | https://wjsdbs75-afk.github.io/marine-kiugi/version.json |
| 원본 아티팩트 | https://claude.ai/artifact/Vmor47U3WvMdi9XY2TsKkA |

## 어떻게 굴러가나

```
game.html 수정 ──┬─▶ Claude 아티팩트에 게시
                 └─▶ 이 저장소 main 에 push ─▶ GitHub Pages 자동 배포 (1~2분)
                                                    │
                          앱을 껐다 켜면 ◀──────────┘  새 버전이 뜸
```

- **`game.html`** 이 유일한 원본입니다. 아티팩트에 올리는 파일과 똑같습니다.
- 앱은 켤 때마다 `version.json` 을 보고, 배포가 바뀌었으면 게임 페이지를 새로 받아 기기에 저장한 뒤 띄웁니다. 그래서 **게임만 고쳤을 때는 APK 를 다시 받을 필요가 없습니다.**
- 인터넷이 안 되거나 받다가 끊기면 마지막으로 받아 둔 버전으로 실행됩니다.
- "껐다 켠다"는 최근 앱 목록에서 밀어서 닫거나, 로비에서 뒤로 가기로 끝낸 뒤 다시 여는 것입니다. 홈 버튼으로 나갔다 돌아오면 하던 화면이 그대로 이어집니다.
- 게임 저장은 기기 안(앱 데이터)에 남습니다. 앱을 지우면 저장도 지워집니다.

## 처음 한 번만 할 일

1. 저장소 **Settings → Pages → Build and deployment → Source** 를 **GitHub Actions** 로 바꿉니다.
2. **Actions** 탭에서 "게임 배포 (GitHub Pages)" 가 초록색으로 끝났는지 확인합니다. (1번보다 먼저 실행돼서 빨간색이면 **Re-run jobs** 를 누르세요.)
3. 서명 키를 Secrets 에 넣습니다 (아래 "서명 키" 참고). 그다음 Actions 탭에서 "안드로이드 APK 빌드" 를 **Run workflow** 로 한 번 실행합니다.
4. 빌드가 끝나면 휴대폰 브라우저로 위 APK 주소를 열어 설치합니다. ("출처를 알 수 없는 앱" 허용이 필요합니다.)

## 폴더

| 경로 | 내용 |
|---|---|
| `game.html` | 게임 본체 (아티팩트 원본, 뼈대 없이 본문만) |
| `tools/build_site.py` | `game.html` 을 Pages 용 `index.html` 로 감싸고 `version.json` 을 만듦 |
| `tools/from_artifact.py` | 아티팩트에서 읽어 온 HTML 을 `game.html` 로 되돌림 |
| `android/` | 껍데기 앱 (WebView 하나, 외부 라이브러리 없음) |
| `.github/workflows/pages.yml` | `game.html` 이 바뀌면 Pages 배포 |
| `.github/workflows/android.yml` | `android/` 가 바뀌면 APK 빌드 → Releases 의 `apk` 에 올림 |

내 컴퓨터에서 미리 보기: `python3 tools/build_site.py && python3 -m http.server -d _site` 후 http://localhost:8000

## 껍데기 앱 메모

- 뒤로 가기 버튼 → 페이지의 `window.__mkBack()` 호출. `true` 면 게임이 처리한 것이고, `false` 면 앱을 끝냅니다.
- 상태 바·내비게이션 바 높이는 CSS 변수 `--safe-top` / `--safe-bottom` 으로 페이지에 넘겨줍니다.
- 세로 고정, 화면 꺼짐 방지, 게임 밖 링크는 브라우저로 엽니다.
- 하단 내비게이션 바(뒤로·홈·최근)는 숨겨서 게임이 화면 맨 아래까지 덮습니다. 아래에서 쓸어 올리면 잠깐 나타납니다. 상태 바는 그대로입니다.
- 여는 주소는 `android/gradle.properties` 의 `gameUrl` 입니다. **이 주소가 바뀌면 기기의 게임 저장이 새 주소로 넘어가지 않습니다.**

### 서명 키

공개하는 앱이라 서명 키는 저장소에 넣지 않습니다. 키 파일과 비밀번호는 저장소 **Settings → Secrets and variables → Actions** 에 두 개의 Secret 으로 넣어 둡니다.

| Secret 이름 | 값 |
|---|---|
| `KEYSTORE_BASE64` | 키 저장소 파일을 base64 로 바꾼 글자 |
| `KEYSTORE_PASSWORD` | 키 저장소 비밀번호 |

- 두 값이 있으면 그 키로 서명해서 Releases 의 `apk` 에 올립니다.
- 없으면 시험용 키로만 빌드하고 릴리스에는 올리지 않습니다 (Actions 실행 화면의 Artifacts 에서만 받을 수 있음).
- **키 파일과 비밀번호는 따로 안전하게 보관하세요.** 잃어버리면 같은 앱으로 업데이트를 낼 수 없고, 사용자는 앱을 지우고 새로 깔아야 합니다(게임 저장이 사라짐). 남에게 새면 그 사람이 "진짜인 척하는 업데이트"를 만들 수 있습니다.
