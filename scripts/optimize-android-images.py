#!/usr/bin/env python3
"""Convierte PNG alcanzables del APK a WebP lossless cuando realmente reduce tamaño."""

from __future__ import annotations

import argparse
import io
import re
from pathlib import Path

from PIL import Image, ImageChops


def reachable_drawables(mapping_file: Path) -> set[str]:
    report = mapping_file.read_text(encoding="utf-8")
    return set(
        re.findall(r"@[^:]+:drawable/([^ ]+) : reachable=true", report)
    )


def encode_lossless(image: Image.Image) -> bytes:
    output = io.BytesIO()
    image.save(output, "WEBP", lossless=True, method=6, exact=True)
    return output.getvalue()


def pixels_match(original: Image.Image, encoded: bytes) -> bool:
    restored = Image.open(io.BytesIO(encoded))
    mode = "RGBA" if "A" in original.getbands() else "RGB"
    return ImageChops.difference(
        original.convert(mode),
        restored.convert(mode),
    ).getbbox(alpha_only=False) is None


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--resources",
        type=Path,
        default=Path("app/src/main/res"),
    )
    parser.add_argument(
        "--mapping",
        type=Path,
        default=Path("app/build/outputs/mapping/release/resources.txt"),
    )
    parser.add_argument("--minimum-bytes", type=int, default=50_000)
    parser.add_argument("--minimum-saving-percent", type=float, default=10.0)
    parser.add_argument("--apply", action="store_true")
    args = parser.parse_args()

    reachable = reachable_drawables(args.mapping)
    before_total = 0
    after_total = 0
    converted = 0

    candidates = sorted(
        args.resources.rglob("*.png"),
        key=lambda path: path.stat().st_size,
        reverse=True,
    )
    for source in candidates:
        if source.name.endswith(".9.png") or source.parent.name.startswith("mipmap-"):
            continue
        if source.stem not in reachable or source.stat().st_size < args.minimum_bytes:
            continue
        destination = source.with_suffix(".webp")
        if destination.exists():
            raise RuntimeError(f"Ya existe el destino: {destination}")

        with Image.open(source) as original:
            encoded = encode_lossless(original)
            if not pixels_match(original, encoded):
                raise RuntimeError(f"La conversión no fue lossless: {source}")

        before = source.stat().st_size
        after = len(encoded)
        saving_percent = 100.0 * (before - after) / before
        if saving_percent < args.minimum_saving_percent:
            continue

        print(f"{source}: {before} -> {after} bytes (-{saving_percent:.1f}%)")
        before_total += before
        after_total += after
        converted += 1
        if args.apply:
            destination.write_bytes(encoded)
            source.unlink()

    action = "convertidos" if args.apply else "convertibles"
    print(
        f"{converted} archivos {action}; ahorro: "
        f"{before_total - after_total} bytes"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
