#!/usr/bin/env python3
"""game.html 에 글꼴을 직접 넣는다 (게임에 쓰인 글자만 잘라 woff2 로).

글꼴을 Google Fonts 에서만 받으면, 앱이 오프라인이거나 글꼴이 늦게 오는 동안
기본 글꼴로 그려졌다가 나중에 바뀐다 (앱 로딩 화면 → 게임 로딩 화면이 달라 보이던 원인).
그래서 게임에 쓰인 글자만 담은 글꼴을 'MK Display', 'MK Body' 라는 이름으로 넣어 두고,
여기에 없는 글자만 Google Fonts('Black Han Sans', 'Gothic A1')로 넘어가게 한다.

게임에 새 글자(새 대사, 새 이름 등)를 넣었다면 이것을 다시 돌린다.
돌리지 않아도 새 글자는 Google Fonts 로 보이니 깨지지는 않는다.

필요한 것: pip install fonttools brotli
사용법: python3 tools/embed_fonts.py
"""
import base64
import io
import re
import sys
import urllib.request
from pathlib import Path

from fontTools import subset
from fontTools.ttLib import TTFont

ROOT = Path(__file__).resolve().parent.parent
GAME = ROOT / "game.html"
FONTS = {   # Google Fonts 원본 (SIL Open Font License)
    "display": "https://raw.githubusercontent.com/google/fonts/main/ofl/blackhansans/BlackHanSans-Regular.ttf",
    "body": "https://raw.githubusercontent.com/google/fonts/main/ofl/gothica1/GothicA1-Bold.ttf",
}
START, END = "<style id=\"mkFonts\">", "</style>"


def woff2(url: str, text: str) -> str:
    data = urllib.request.urlopen(url, timeout=60).read()
    font = TTFont(io.BytesIO(data))
    opts = subset.Options()
    opts.flavor = "woff2"
    opts.layout_features = ["*"]
    s = subset.Subsetter(opts)
    s.populate(text=text)
    s.subset(font)
    out = io.BytesIO()
    font.flavor = "woff2"
    font.save(out)
    return base64.b64encode(out.getvalue()).decode("ascii")


def main() -> int:
    game = GAME.read_text(encoding="utf-8")
    # 이미 넣어 둔 글꼴 블록은 빼고 센다 (그림·소리 같은 아주 긴 줄도 뺀다)
    i = game.find(START)
    body = game if i < 0 else game[:i] + game[game.index(END, i) + len(END):]
    lines = [l for l in body.split("\n") if len(l) < 20000]
    chars = set("".join(lines)) | {chr(c) for c in range(0x20, 0x7F)} | set("…·•×÷→←↑↓★☆♥♪―–—“”‘’")
    text = "".join(sorted(c for c in chars if c.isprintable()))
    css = (START
           + "@font-face{font-family:'MK Display';font-weight:400;font-display:block;src:url(data:font/woff2;base64,%s) format('woff2')}"
           % woff2(FONTS["display"], text)
           + "@font-face{font-family:'MK Body';font-weight:100 900;font-display:block;src:url(data:font/woff2;base64,%s) format('woff2')}"
           % woff2(FONTS["body"], text)
           + END)
    if i < 0:
        link = re.search(r"<link rel=\"stylesheet\" href=\"https://fonts\.googleapis\.com[^\n]*\n", game)
        if not link:
            raise SystemExit("game.html 에서 Google Fonts 링크를 찾지 못했습니다.")
        game = game[:link.end()] + css + "\n" + game[link.end():]
    else:
        game = game[:i] + css + game[game.index(END, i) + len(END):]
    GAME.write_text(game, encoding="utf-8")
    print("글자 %d개, 글꼴 블록 %.0f KB" % (len(text), len(css) / 1024))
    return 0


if __name__ == "__main__":
    sys.exit(main())
