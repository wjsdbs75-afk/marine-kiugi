#!/usr/bin/env python3
"""game.html(아티팩트에 올리는 원본)을 GitHub Pages용 완성 페이지로 감싼다.

아티팩트는 게시할 때 Claude 쪽에서 <html>/<head>/<body> 뼈대를 씌워 주기 때문에
game.html 에는 본문만 들어 있다. Pages 에는 뼈대가 없으므로 여기서 직접 씌운다.

사용법: python3 tools/build_site.py [출력 폴더=_site]
"""
import json
import os
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "game.html"

# 아티팩트 뼈대와 같은 기본 스타일. 다른 점은 하나: 뼈대는 노치/시스템 바만큼 화면을 줄이지만,
# 여기서는 줄이지 않고 --safe-top/--safe-bottom 으로 넘겨서 게임이 직접 UI 를 밀게 한다.
# (게임의 #safeProbe 가 이 두 값을 읽는다.)
HEAD = """<!doctype html>
<html lang="ko">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no,viewport-fit=cover">
<meta name="theme-color" content="#05070f">
<title>{title}</title>
<link rel="icon" href="data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='14 14 80 80'%3E%3Crect x='14' y='14' width='80' height='80' rx='18' fill='%231c2740'/%3E%3Cg fill='%23ffc21f' stroke='%230a0f1c' stroke-width='2.5' stroke-linejoin='round'%3E%3Cpath d='M34 46 54 32 74 46V54L54 40 34 54Z'/%3E%3Cpath d='M34 58 54 44 74 58V66L54 52 34 66Z'/%3E%3Cpath d='M34 70 54 56 74 70V78L54 64 34 78Z'/%3E%3C/g%3E%3C/svg%3E">
<style>:root{{touch-action:manipulation;box-sizing:border-box;--safe-top:env(safe-area-inset-top,0px);--safe-bottom:env(safe-area-inset-bottom,0px)}}body{{margin:0;padding:0;font:14px -apple-system,BlinkMacSystemFont,sans-serif;background:#05070f;color:#eaf0ff}}img{{max-width:100%}}[hidden]:not([hidden=until-found i]){{display:none!important}}</style>
<script>
// 안드로이드 껍데기 앱(android/)이 상태 바·내비게이션 바 높이를 알려 준다. 브라우저에서는 MKShell 이 없어 그냥 지나간다.
(function () {{
  var s = window.MKShell; if (!s) return;
  try {{
    var r = document.documentElement.style;
    r.setProperty('--safe-top', s.safeTop() + 'px');
    r.setProperty('--safe-bottom', s.safeBottom() + 'px');
  }} catch (e) {{}}
}})();
</script>
</head>
<body>
"""
TAIL = "\n</body>\n</html>\n"


def main() -> int:
    out = Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT / "_site"
    game = SRC.read_text(encoding="utf-8")

    low = game[:2000].lower()
    if "<!doctype" in low or "<html" in low or "<body" in low:
        print("오류: game.html 에는 뼈대(<!doctype>, <html>, <body>) 없이 본문만 있어야 합니다.", file=sys.stderr)
        return 1

    m = re.search(r"<title>(.*?)</title>", game, re.S)
    title = m.group(1).strip() if m else "마린 키우기"

    def const(name: str) -> str:
        mm = re.search(r"\b%s\s*=\s*'([^']*)'" % name, game)
        return mm.group(1) if mm else ""

    out.mkdir(parents=True, exist_ok=True)
    (out / "index.html").write_text(HEAD.format(title=title) + game + TAIL, encoding="utf-8")
    info = {
        "size": (out / "index.html").stat().st_size,   # 앱이 받는 동안 막대를 채우는 데 쓴다
        "version": const("VERSION"),
        "date": const("VERSION_DATE"),
        "note": const("VERSION_NOTE"),
        "commit": os.environ.get("GITHUB_SHA", ""),
    }
    (out / "version.json").write_text(json.dumps(info, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    (out / ".nojekyll").write_text("", encoding="utf-8")
    print("만든 곳: %s  (%s, %.1f MB)" % (out, info["version"] or "버전 표기 없음", (out / "index.html").stat().st_size / 1e6))
    return 0


if __name__ == "__main__":
    sys.exit(main())
