#!/usr/bin/env python3
"""Copy the Android match audio into the iOS GameAudio folder and update the manifest.

Only copies (no re-encoding). Entries for these files are replaced in
docs/ASSET_MANIFEST.json; every other entry is kept as is.
"""
import hashlib
import json
import shutil
from pathlib import Path

IOS = Path(__file__).resolve().parents[1]
ROOT = IOS.parent
RAW = ROOT / "app/src/main/res/raw"
TARGET = IOS / "TraidoresIOS/TraidoresIOS/Resources/GameAudio"
MANIFEST = IOS / "docs/ASSET_MANIFEST.json"

# Android name -> iOS name. night_phase_music.mpeg is MPEG-1 Layer III; the .mp3
# extension lets AVAudioPlayer pick the right decoder.
FILES = {
    "day_music_pampa.mp3": "day_music_pampa.mp3",
    "day_music_greece.mp3": "day_music_greece.mp3",
    "day_music_medieval.mp3": "day_music_medieval.mp3",
    "night_phase_music.mpeg": "night_phase_music.mp3",
    "victory_music_town.mp3": "victory_music_town.mp3",
    "victory_music_traitors.mp3": "victory_music_traitors.mp3",
    "sfx_card_deal.mp3": "sfx_card_deal.mp3",
    "sfx_night_fall.mp3": "sfx_night_fall.mp3",
    "sfx_dawn.mp3": "sfx_dawn.mp3",
    "sfx_death_elevenlabs.mp3": "sfx_death_elevenlabs.mp3",
    "sfx_elimination.mp3": "sfx_elimination.mp3",
    "sfx_expulsion.mp3": "sfx_expulsion.mp3",
    "sfx_no_death.wav": "sfx_no_death.wav",
    "sfx_vote_cast.mp3": "sfx_vote_cast.mp3",
    "sfx_tie_break.mp3": "sfx_tie_break.mp3",
}


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


TARGET.mkdir(parents=True, exist_ok=True)
manifest = json.loads(MANIFEST.read_text())
destinations = set()
entries = []
for source_name, target_name in FILES.items():
    src = RAW / source_name
    dst = TARGET / target_name
    shutil.copyfile(src, dst)
    destination = str(dst.relative_to(IOS))
    destinations.add(destination)
    entries.append({
        "source": str(src.relative_to(ROOT)),
        "destination": destination,
        "sourceSHA256": digest(src),
        "destinationSHA256": digest(dst),
        "operation": "copy" if source_name == target_name else "copy; renamed extension only",
    })

manifest = [entry for entry in manifest if entry.get("destination") not in destinations] + entries
MANIFEST.write_text(json.dumps(manifest, indent=2, ensure_ascii=False) + "\n")
print(f"Copied {len(entries)} audio files to {TARGET.relative_to(IOS)}; Android sources left untouched.")
