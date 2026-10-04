#!/usr/bin/env python3
"""Audit and equalise the loudness of the short game sounds.

  scripts/audio_levels.py audit      # print LUFS / peak for every sound
  scripts/audio_levels.py normalize  # bring every short sound to TARGET_LUFS

Music tracks are only audited. `sfx_card_deal` is skipped on purpose: the role
dealing screens play it at hand-tuned volumes (0.28-0.5) on top of its own level.
The app code must play these files at relative volume 1.0, otherwise the
per-sound multipliers undo the equalisation.

Needs ffmpeg (FFMPEG env var, PATH, or ~/.local/bin/ffmpeg).
"""
import os
import re
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
RAW = ROOT / "app/src/main/res/raw"
IOS_DIRS = [
    ROOT / "ios/TraidoresIOS/TraidoresIOS/Resources",
    ROOT / "ios/TraidoresIOS/TraidoresIOS/Resources/GameAudio",
]

TARGET_LUFS = -20.0   # integrated loudness every short sound is brought to
PEAK_LIMIT_DB = -2.0  # sample-peak ceiling, leaves room for the lossy re-encode
MUSIC_PREFIXES = ("day_music", "menu_music", "night_phase_music", "victory_music")
SKIP = {"sfx_card_deal.mp3"}


def ffmpeg_bin():
    found = os.environ.get("FFMPEG") or shutil.which("ffmpeg")
    if not found and (Path.home() / ".local/bin/ffmpeg").exists():
        found = str(Path.home() / ".local/bin/ffmpeg")
    if not found:
        sys.exit("ffmpeg not found")
    return found


FFMPEG = ffmpeg_bin()


def measure(path):
    out = subprocess.run(
        [FFMPEG, "-hide_banner", "-nostats", "-i", str(path), "-af", "ebur128=peak=true", "-f", "null", "-"],
        capture_output=True, text=True,
    ).stderr
    summary = out[out.rfind("Summary:"):]
    lufs = float(re.search(r"\n\s+I:\s+(-?[\d.]+) LUFS", summary).group(1))
    peak = float(re.search(r"Peak:\s+(-?[\d.]+) dBFS", summary).group(1))
    return lufs, peak


def sounds():
    return sorted(p for p in RAW.iterdir() if p.suffix in {".mp3", ".wav", ".ogg", ".mpeg"})


def is_music(path):
    return path.name.startswith(MUSIC_PREFIXES)


def probe(path):
    info = subprocess.run([FFMPEG, "-hide_banner", "-i", str(path)], capture_output=True, text=True).stderr
    rate = int(re.search(r"(\d+) Hz", info).group(1))
    channels = 1 if "mono" in info else 2
    return rate, channels


def encode_args(path, rate, channels):
    if path.suffix == ".wav":
        return ["-c:a", "pcm_s16le"]
    if path.suffix == ".ogg":
        return ["-c:a", "libvorbis", "-q:a", "5"]
    return ["-c:a", "libmp3lame", "-b:a", "192k" if channels == 2 else "128k"]


def normalize(path):
    lufs, _ = measure(path)
    gain = TARGET_LUFS - lufs
    rate, channels = probe(path)
    limit = 10 ** (PEAK_LIMIT_DB / 20)
    filt = f"volume={gain:.2f}dB,alimiter=limit={limit:.4f}:attack=2:release=40:level=disabled"
    with tempfile.TemporaryDirectory() as tmp:
        out = Path(tmp) / path.name
        subprocess.run(
            [FFMPEG, "-y", "-hide_banner", "-loglevel", "error", "-i", str(path), "-vn", "-map_metadata", "-1",
             "-af", filt, "-ar", str(rate), "-ac", str(channels), *encode_args(path, rate, channels), str(out)],
            check=True,
        )
        shutil.copyfile(out, path)
    return gain


def sync_ios(path):
    for folder in IOS_DIRS:
        target = folder / path.name
        if target.exists():
            shutil.copyfile(path, target)
            print(f"  -> iOS {target.relative_to(ROOT)}")


def main():
    mode = sys.argv[1] if len(sys.argv) > 1 else "audit"
    print(f"{'file':44s} {'LUFS':>7s} {'peak dB':>8s}")
    for path in sounds():
        if mode == "normalize" and not is_music(path) and path.name not in SKIP:
            gain = normalize(path)
            lufs, peak = measure(path)
            print(f"{path.name:44s} {lufs:7.1f} {peak:8.1f}   (gain {gain:+.1f} dB)")
            sync_ios(path)
        else:
            lufs, peak = measure(path)
            tag = "music" if is_music(path) else ("skipped" if path.name in SKIP else "")
            print(f"{path.name:44s} {lufs:7.1f} {peak:8.1f}   {tag}")


if __name__ == "__main__":
    main()
