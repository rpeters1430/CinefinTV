#!/usr/bin/env python3
from pathlib import Path
import struct

EXPECTED = {
    "launcher-icon.png": (1024, 1024),
    "launcher-icon-round.png": (1024, 1024),
    "tv-banner.png": (1536, 864),
}


def read_png_size(path: Path) -> tuple[int, int]:
    with path.open("rb") as f:
        signature = f.read(8)
        if signature != b"\x89PNG\r\n\x1a\n":
            raise ValueError("not a PNG file")

        length = struct.unpack(">I", f.read(4))[0]
        chunk_type = f.read(4)
        if chunk_type != b"IHDR" or length != 13:
            raise ValueError("invalid PNG IHDR chunk")

        width, height = struct.unpack(">II", f.read(8))
        return width, height


base = Path("app/assets/branding")
failed = False

for name, expected in EXPECTED.items():
    path = base / name
    if not path.exists():
        print(f"MISSING: {path}")
        failed = True
        continue

    try:
        actual = read_png_size(path)
    except Exception as exc:
        print(f"INVALID: {path} ({exc})")
        failed = True
        continue

    if actual != expected:
        print(f"INVALID: {path} is {actual[0]}x{actual[1]} (expected {expected[0]}x{expected[1]})")
        failed = True
    else:
        print(f"OK: {path} is {actual[0]}x{actual[1]}")

raise SystemExit(1 if failed else 0)
