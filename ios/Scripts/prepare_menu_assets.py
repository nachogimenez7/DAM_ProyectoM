#!/usr/bin/env python3
"""Copy/convert the selected Android assets into iOS only. Requires Pillow."""
import hashlib
import json
import shutil
from pathlib import Path
from PIL import Image

IOS = Path(__file__).resolve().parents[1]
ROOT = IOS.parent
RES = ROOT / "app/src/main/res"
TARGET = IOS / "TraidoresIOS/TraidoresIOS/Resources"
CATALOG = TARGET / "Assets.xcassets"
SOURCES = {
    **{name: f"drawable/{name}.webp" for name in [
        "rol_aldeano_gaucho", "rol_asesino_gaucho", "rol_medico_gaucho", "rol_detective_gaucho",
        "mapa_pampa_vertical_dia", "mapa_pampa_vertical_noche",
    ]},
    "fondo_menu": "drawable/fondo_menu.webp",
    "logo_traidores_clean": "drawable/logo_traidores_clean.webp",
    "bandido_menu_medallion": "drawable-nodpi/bandido_menu_medallion.png",
    "modo_juego_local_pampa_v3": "drawable/modo_juego_local_pampa_v3.png",
    "modo_jugar_online": "drawable/modo_jugar_online.webp",
}


def write_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n")


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


manifest = []
for name, source in SOURCES.items():
    src = RES / source
    dst = CATALOG / f"{name}.imageset" / f"{name}.png"
    dst.parent.mkdir(parents=True, exist_ok=True)
    if src.suffix == ".png":
        shutil.copyfile(src, dst)
    else:
        with Image.open(src) as im:
            im.save(dst, "PNG", optimize=True)
    write_json(dst.parent / "Contents.json", {
        "images": [{"filename": dst.name, "idiom": "universal"}],
        "info": {"author": "xcode", "version": 1},
    })
    manifest.append({"source": str(src.relative_to(ROOT)), "destination": str(dst.relative_to(IOS)),
                     "sourceSHA256": digest(src), "destinationSHA256": digest(dst),
                     "operation": "copy" if src.suffix == ".png" else "WebP to PNG; no resize"})

for source in ["font/bree_serif.ttf", "raw/menu_music.mp3"]:
    src = RES / source
    dst = TARGET / src.name
    shutil.copyfile(src, dst)
    manifest.append({"source": str(src.relative_to(ROOT)), "destination": str(dst.relative_to(IOS)),
                     "sourceSHA256": digest(src), "destinationSHA256": digest(dst), "operation": "copy"})

write_json(CATALOG / "Contents.json", {"info": {"author": "xcode", "version": 1}})
write_json(CATALOG / "LaunchBackground.colorset/Contents.json", {
    "colors": [{"idiom": "universal", "color": {"color-space": "srgb", "components": {
        "red": "0.035", "green": "0.035", "blue": "0.035", "alpha": "1.000"}}}],
    "info": {"author": "xcode", "version": 1},
})
write_json(IOS / "docs/ASSET_MANIFEST.json", manifest)
print(f"Prepared {len(manifest)} assets; Android sources left untouched.")
