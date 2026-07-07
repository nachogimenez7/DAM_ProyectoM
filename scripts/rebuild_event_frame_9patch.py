"""Reconstruye los marcos ui_frame_event_* como 9-patch correctos.

Problema original: el arte (~917x634px) flotaba en un lienzo cuadrado 1026x1026
con 61-69dp de transparencia horneada arriba/abajo, y las guias 9-patch eran
genericas (no coincidian ni con el arte ni con el hueco interior). Resultado:
el panel oscuro asomaba fuera del marco y el texto pisaba el borde.

Este script:
1. Recorta cada PNG al bounding box real del arte (elimina margenes horneados).
2. Mide el hueco interior real y lo usa como padding marks (contenido).
3. Coloca las bandas de estiramiento en tramos lisos del borde (por archivo,
   esquivando laureles/sellos/medallones centrales y esquinas ornamentadas).
4. Genera previews del 9-patch SIMULADO a dos tamanos, con un rectangulo
   magenta imitando el panel oscuro interior, para validar visualmente.

Uso:  python scripts/rebuild_event_frame_9patch.py [--apply]
      Sin --apply solo genera build/event-frame-9patch/ (archivos + previews).
      Con --apply ademas sobrescribe app/src/main/res/drawable-xxhdpi/.
"""

import sys
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
SOURCE_DIR = ROOT / "app" / "src" / "main" / "res" / "drawable-xxhdpi"
OUT_DIR = ROOT / "build" / "event-frame-9patch"
ALPHA_MIN = 24
MARK = (0, 0, 0, 255)

# Bandas de estiramiento como fracciones del arte recortado (start, end).
# Elegidas para caer en tramos lisos: lejos del ornamento central (~0.40-0.60)
# y de las esquinas ornamentadas (~<0.25 / >0.75).
FRAMES = {
    "ui_frame_event_grecia.9.png": {
        "bands_x": [(0.26, 0.34), (0.66, 0.74)],
        "bands_y": [(0.42, 0.50), (0.70, 0.78)],
    },
    "ui_frame_event_pampa.9.png": {
        "bands_x": [(0.26, 0.34), (0.66, 0.74)],
        "bands_y": [(0.40, 0.46), (0.60, 0.66)],
    },
    "ui_frame_event_medieval.9.png": {
        "bands_x": [(0.30, 0.40), (0.60, 0.70)],
        "bands_y": [(0.32, 0.40), (0.58, 0.66)],
    },
}

PREVIEW_SIZES = [(920, 560), (920, 1080)]
PREVIEW_BG = (70, 70, 78, 255)
PREVIEW_INNER = (255, 0, 255, 255)
PREVIEW_OVERLAP_PX = 30  # ~10dp: cuanto se mete el panel oscuro bajo el marco


def art_bbox(img):
    px = img.load()
    w, h = img.size
    min_x = min_y = None
    max_x = max_y = None
    for y in range(h):
        for x in range(w):
            if px[x, y][3] > ALPHA_MIN:
                if min_x is None or x < min_x:
                    min_x = x
                if max_x is None or x > max_x:
                    max_x = x
                if min_y is None:
                    min_y = y
                max_y = y
    return (min_x, min_y, max_x + 1, max_y + 1)


def hole_rect(img):
    px = img.load()
    w, h = img.size
    cx, cy = w // 2, h // 2

    def walk(dx, dy):
        x, y = cx, cy
        while 0 <= x < w and 0 <= y < h and px[x, y][3] <= ALPHA_MIN:
            x += dx
            y += dy
        return x, y

    left = walk(-1, 0)[0] + 1
    right = walk(1, 0)[0]
    top = walk(0, -1)[1] + 1
    bottom = walk(0, 1)[1]
    return (left, top, right, bottom)


def segments(length, bands_frac):
    bands = sorted((int(length * a), int(length * b)) for a, b in bands_frac)
    result = []
    cursor = 0
    for start, end in bands:
        if start > cursor:
            result.append((cursor, start, False))
        result.append((start, end, True))
        cursor = end
    if cursor < length:
        result.append((cursor, length, False))
    return result


def build_nine_patch(art, bands_x, bands_y, hole):
    w, h = art.size
    out = Image.new("RGBA", (w + 2, h + 2), (0, 0, 0, 0))
    out.paste(art, (1, 1))
    px = out.load()
    for start, end, stretch in segments(w, bands_x):
        if stretch:
            for x in range(start, end):
                px[x + 1, 0] = MARK
    for start, end, stretch in segments(h, bands_y):
        if stretch:
            for y in range(start, end):
                px[0, y + 1] = MARK
    hl, ht, hr, hb = hole
    for x in range(hl, hr):
        px[x + 1, h + 1] = MARK
    for y in range(ht, hb):
        px[w + 1, y + 1] = MARK
    return out


def dest_map(length, bands_frac, target):
    segs = segments(length, bands_frac)
    fixed = sum(end - start for start, end, s in segs if not s)
    stretch = sum(end - start for start, end, s in segs if s)
    scale = max(target - fixed, 0) / stretch if stretch else 1.0
    mapping = []
    cursor = 0.0
    for start, end, is_stretch in segs:
        size = (end - start) * (scale if is_stretch else 1.0)
        mapping.append((start, end, cursor, cursor + size))
        cursor += size
    return mapping


def map_coord(mapping, value):
    for s0, s1, d0, d1 in mapping:
        if s0 <= value <= s1:
            if s1 == s0:
                return d0
            return d0 + (value - s0) / (s1 - s0) * (d1 - d0)
    return mapping[-1][3]


def render_simulated(art, bands_x, bands_y, hole, target):
    tw, th = target
    mx = dest_map(art.size[0], bands_x, tw)
    my = dest_map(art.size[1], bands_y, th)

    canvas = Image.new("RGBA", (tw, th), PREVIEW_BG)

    hl, ht, hr, hb = hole
    inner = (
        int(map_coord(mx, hl)) - PREVIEW_OVERLAP_PX,
        int(map_coord(my, ht)) - PREVIEW_OVERLAP_PX,
        int(map_coord(mx, hr)) + PREVIEW_OVERLAP_PX,
        int(map_coord(my, hb)) + PREVIEW_OVERLAP_PX,
    )
    inner_img = Image.new("RGBA", (inner[2] - inner[0], inner[3] - inner[1]), PREVIEW_INNER)
    canvas.paste(inner_img, (inner[0], inner[1]))

    for sx0, sx1, dx0, dx1 in mx:
        for sy0, sy1, dy0, dy1 in my:
            src = art.crop((sx0, sy0, sx1, sy1))
            dw = max(int(round(dx1)) - int(round(dx0)), 1)
            dh = max(int(round(dy1)) - int(round(dy0)), 1)
            tile = src.resize((dw, dh), Image.BILINEAR)
            canvas.alpha_composite(tile, (int(round(dx0)), int(round(dy0))))
    return canvas


def main():
    apply_changes = "--apply" in sys.argv
    OUT_DIR.mkdir(parents=True, exist_ok=True)

    for name, config in FRAMES.items():
        source = Image.open(SOURCE_DIR / name).convert("RGBA")
        w, h = source.size
        content = source.crop((1, 1, w - 1, h - 1))

        bbox = art_bbox(content)
        art = content.crop(bbox)
        hole = hole_rect(art)

        nine = build_nine_patch(art, config["bands_x"], config["bands_y"], hole)
        out_path = OUT_DIR / name
        nine.save(out_path)

        aw, ah = art.size
        print(f"{name}: art {aw}x{ah}px  hole L{hole[0]} T{hole[1]} "
              f"R{aw - hole[2]} B{ah - hole[3]} (px desde el borde del arte)")

        for i, size in enumerate(PREVIEW_SIZES):
            preview = render_simulated(art, config["bands_x"], config["bands_y"], hole, size)
            preview.convert("RGB").save(
                OUT_DIR / name.replace(".9.png", f"_preview_{size[0]}x{size[1]}.png")
            )

        if apply_changes:
            nine.save(SOURCE_DIR / name)
            print(f"  -> aplicado sobre {SOURCE_DIR / name}")

    print(f"\nSalida en {OUT_DIR}")
    if not apply_changes:
        print("(sin --apply: los drawables del juego NO fueron modificados)")


if __name__ == "__main__":
    main()
