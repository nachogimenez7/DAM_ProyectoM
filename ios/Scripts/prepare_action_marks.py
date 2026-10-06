#!/usr/bin/env python3
"""Bring Android's card action marks and special role scenes into the iOS asset catalog.

WebP is converted to PNG with macOS `sips`, keeping size and transparency. Only the
action mark entries of docs/ASSET_MANIFEST.json are replaced; the rest keep their order.
"""
import hashlib
import json
import subprocess
from pathlib import Path

IOS = Path(__file__).resolve().parents[1]
ROOT = IOS.parent
SOURCE = ROOT / "app/src/main/res/drawable-nodpi"
CATALOG = IOS / "TraidoresIOS/TraidoresIOS/Resources/Assets.xcassets"
MANIFEST = IOS / "docs/ASSET_MANIFEST.json"


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


# The Payador, Oráculo and Bufón announcements.
SCENES = ["payador_contrapunto_scene", "oracle_return_scene", "jester_victory_scene", "jester_horn_illustrated"]

entries = []
sources = sorted(SOURCE.glob("action_mark_*.webp")) + [
    next(ROOT.glob(f"app/src/main/res/drawable*/{name}.webp")) for name in SCENES]
for src in sources:
    folder = CATALOG / f"{src.stem}.imageset"
    folder.mkdir(parents=True, exist_ok=True)
    dst = folder / f"{src.stem}.png"
    subprocess.run(["sips", "-s", "format", "png", str(src), "--out", str(dst)],
                   check=True, capture_output=True)
    (folder / "Contents.json").write_text(json.dumps({
        "images": [{"filename": dst.name, "idiom": "universal"}],
        "info": {"author": "xcode", "version": 1},
    }, indent=2) + "\n")
    entries.append({
        "source": str(src.relative_to(ROOT)),
        "destination": str(dst.relative_to(IOS)),
        "sourceSHA256": digest(src),
        "destinationSHA256": digest(dst),
        "operation": "sips WebP to PNG; no resize",
    })

manifest = json.loads(MANIFEST.read_text())
destinations = {entry["destination"] for entry in entries}
kept = [entry for entry in manifest if entry.get("destination") not in destinations]
MANIFEST.write_text(json.dumps(kept + entries, indent=2, ensure_ascii=False) + "\n")
print(f"Prepared {len(entries)} action marks and scenes in the asset catalog.")
