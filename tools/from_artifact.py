#!/usr/bin/env python3
"""아티팩트에서 읽어 온 전체 HTML 에서 뼈대를 벗겨 game.html 로 저장한다.

아티팩트가 저장소보다 앞서 있을 때(다른 곳에서 아티팩트만 고친 경우) 맞추는 용도.
Artifact 도구의 read 가 저장해 준 .html 파일 경로를 넘긴다.

사용법: python3 tools/from_artifact.py <읽어 온 파일.html>
"""
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent


def main() -> int:
    if len(sys.argv) != 2:
        print(__doc__, file=sys.stderr)
        return 2
    data = Path(sys.argv[1]).read_bytes()
    start = data.find(b"<body>")
    tail = b"</body></html>"
    if not data.lstrip().lower().startswith(b"<!doctype") or start < 0 or not data.rstrip().endswith(tail):
        print("오류: 아티팩트 뼈대(<!doctype …><body> … </body></html>)가 보이지 않습니다. 이미 본문만 있는 파일일 수 있어요.", file=sys.stderr)
        return 1
    body = data[start + len(b"<body>"):data.rstrip().rfind(tail)].strip(b"\n") + b"\n"
    (ROOT / "game.html").write_bytes(body)
    print("game.html 갱신: %.1f MB" % (len(body) / 1e6))
    return 0


if __name__ == "__main__":
    sys.exit(main())
