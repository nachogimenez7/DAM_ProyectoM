#!/usr/bin/env python3
"""Bring Android's emote sounds into iOS GameAudio and record them in the manifest.

MP3 files are copied as is. The Ogg Vorbis ones, which AVAudioPlayer cannot decode,
are converted to AAC (.m4a) with ffmpeg at their current, already levelled loudness.
Only the emote entries of docs/ASSET_MANIFEST.json are replaced; the rest keep their order.
"""
import hashlib
import json
import shutil
import subprocess
from pathlib import Path

IOS = Path(__file__).resolve().parents[1]
ROOT = IOS.parent
RAW = ROOT / "app/src/main/res/raw"
TARGET = IOS / "TraidoresIOS/TraidoresIOS/Resources/GameAudio"
MANIFEST = IOS / "docs/ASSET_MANIFEST.json"
FFMPEG = Path.home() / ".local/bin/ffmpeg"


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


entries = []
for src in sorted(RAW.glob("sfx_emote_*")):
    if src.suffix == ".mp3":
        dst = TARGET / src.name
        shutil.copyfile(src, dst)
        operation = "copy"
    else:
        dst = TARGET / (src.stem + ".m4a")
        subprocess.run([str(FFMPEG), "-loglevel", "error", "-y", "-i", str(src),
                        "-c:a", "aac", "-b:a", "128k", "-map_metadata", "-1", str(dst)], check=True)
        operation = "ffmpeg Ogg Vorbis -> AAC 128 kbps (AVAudioPlayer has no Ogg decoder)"
    entries.append({
        "source": str(src.relative_to(ROOT)),
        "destination": str(dst.relative_to(IOS)),
        "sourceSHA256": digest(src),
        "destinationSHA256": digest(dst),
        "operation": operation,
    })

manifest = json.loads(MANIFEST.read_text())
destinations = {entry["destination"] for entry in entries}
kept = [entry for entry in manifest if entry.get("destination") not in destinations]
MANIFEST.write_text(json.dumps(kept + entries, indent=2, ensure_ascii=False) + "\n")
print(f"Prepared {len(entries)} emote sounds in {TARGET.relative_to(IOS)}.")
