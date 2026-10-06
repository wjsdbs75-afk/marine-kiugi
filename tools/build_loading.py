#!/usr/bin/env python3
"""앱이 게임 페이지를 확인하고 받는 동안 띄우는 화면(loading.html)을 만든다.

tools/loading_src.html 에 글꼴(필요한 글자만 잘라 woff2)과 게임의 작전 팁을 채워
안드로이드(android/app/src/main/assets/)와 아이폰(ios/MarineKiugi/) 두 곳에 쓴다.
게임 안의 로딩 화면 모양이나 팁을 바꿨다면 이것을 다시 돌려 앱을 새로 빌드한다.

필요한 것: pip install fonttools brotli
사용법: python3 tools/build_loading.py
"""
import base64
import io
import json
import re
import sys
import urllib.request
from pathlib import Path

from fontTools import subset
from fontTools.ttLib import TTFont

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "tools" / "loading_src.html"
OUTS = [ROOT / "android/app/src/main/assets/loading.html", ROOT / "ios/MarineKiugi/loading.html"]
FONTS = {   # Google Fonts 원본 (SIL Open Font License)
    "display": "https://raw.githubusercontent.com/google/fonts/main/ofl/blackhansans/BlackHanSans-Regular.ttf",
    "body": "https://raw.githubusercontent.com/google/fonts/main/ofl/gothica1/GothicA1-Bold.ttf",
}
# 앱(MainActivity.java, GameViewController.swift)이 mkLoad 로 넘기는 글자들
APP_TEXTS = ["보급품 확인 중", "새 버전을 받는 중", "게임을 받는 중", "강하 준비 중"]


def tips() -> list:
    game = (ROOT / "game.html").read_text(encoding="utf-8")
    m = re.search(r"const TIPS = \[(.*?)\];", game, re.S)
    if not m:
        raise SystemExit("game.html 에서 TIPS 를 찾지 못했습니다.")
    return re.findall(r"'([^']*)'", m.group(1))


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
    src = SRC.read_text(encoding="utf-8")
    tip_list = tips()
    chars = set(re.sub(r"<[^>]*>", "", src)) | set("".join(tip_list)) | set("".join(APP_TEXTS))
    chars |= set("0123456789%…·.,!?- ")
    text = "".join(sorted(c for c in chars if c.isprintable()))
    page = (src.replace("{{FONT_DISPLAY}}", woff2(FONTS["display"], text))
               .replace("{{FONT_BODY}}", woff2(FONTS["body"], text))
               .replace("{{TIPS}}", json.dumps(tip_list, ensure_ascii=False)))
    for out in OUTS:
        out.parent.mkdir(parents=True, exist_ok=True)
        out.write_text(page, encoding="utf-8")
        print("만든 곳: %s (%.0f KB)" % (out.relative_to(ROOT), out.stat().st_size / 1024))
    return 0


if __name__ == "__main__":
    sys.exit(main())
