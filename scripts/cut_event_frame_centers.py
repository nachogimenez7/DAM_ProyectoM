from collections import deque
from pathlib import Path

from PIL import Image


ROOT = Path(__file__).resolve().parents[1]
SOURCE_DIR = ROOT / "app" / "src" / "main" / "res" / "drawable-xxhdpi"
OUT_DIR = ROOT / "build" / "event-frame-cutouts"
PREVIEW_DIR = OUT_DIR / "previews-magenta"

MAGENTA = (255, 0, 255, 255)
TRANSPARENT = (0, 0, 0, 0)

# Nine-patch PNGs use the outer 1 px border for stretch/content markers.
NINE_PATCH_BORDER = 1

FRAMES = {
    "ui_frame_event_grecia.9.png": {
        "threshold": 70,
        "fill_bbox": False,
    },
    "ui_frame_event_pampa.9.png": {
        "threshold": 35,
        "fill_bbox": False,
        "clip_rect": None,
        "core_clear_rect": (0.18, 0.32, 0.84, 0.68),
    },
    "ui_frame_event_medieval.9.png": {
        "threshold": 110,
        "fill_bbox": False,
        "clip_rect": (0.15, 0.27, 0.85, 0.72),
        "core_clear_rect": None,
    },
}


def luminance(pixel: tuple[int, int, int, int]) -> float:
    r, g, b, _ = pixel
    return 0.2126 * r + 0.7152 * g + 0.0722 * b


def rect_from_ratio(
    size: tuple[int, int],
    rect: tuple[float, float, float, float] | None,
) -> tuple[int, int, int, int] | None:
    if rect is None:
        return None
    width, height = size
    left, top, right, bottom = rect
    return (
        int(width * left),
        int(height * top),
        int(width * right),
        int(height * bottom),
    )


def flood_dark_center(
    image: Image.Image,
    threshold: int,
    fill_bbox: bool,
    clip_rect: tuple[float, float, float, float] | None = None,
    core_clear_rect: tuple[float, float, float, float] | None = None,
) -> tuple[Image.Image, int]:
    rgba = image.convert("RGBA")
    pixels = rgba.load()
    width, height = rgba.size

    min_x = NINE_PATCH_BORDER
    min_y = NINE_PATCH_BORDER
    max_x = width - NINE_PATCH_BORDER - 1
    max_y = height - NINE_PATCH_BORDER - 1
    clip = rect_from_ratio((width, height), clip_rect)
    if clip is not None:
        clip_left, clip_top, clip_right, clip_bottom = clip
        min_x = max(min_x, clip_left)
        min_y = max(min_y, clip_top)
        max_x = min(max_x, clip_right)
        max_y = min(max_y, clip_bottom)
    seed = (width // 2, height // 2)

    queue: deque[tuple[int, int]] = deque([seed])
    visited = set()
    cut = []

    while queue:
        x, y = queue.popleft()
        if (x, y) in visited:
            continue
        visited.add((x, y))
        if x < min_x or x > max_x or y < min_y or y > max_y:
            continue

        pixel = pixels[x, y]
        if pixel[3] == 0 or luminance(pixel) > threshold:
            continue

        cut.append((x, y))
        queue.append((x + 1, y))
        queue.append((x - 1, y))
        queue.append((x, y + 1))
        queue.append((x, y - 1))

    if fill_bbox and cut:
        left = min(x for x, _ in cut)
        right = max(x for x, _ in cut)
        top = min(y for _, y in cut)
        bottom = max(y for _, y in cut)
        cut = [
            (x, y)
            for y in range(top, bottom + 1)
            for x in range(left, right + 1)
            if min_x <= x <= max_x and min_y <= y <= max_y
        ]

    core = rect_from_ratio((width, height), core_clear_rect)
    if core is not None:
        left, top, right, bottom = core
        cut.extend(
            (x, y)
            for y in range(max(top, NINE_PATCH_BORDER), min(bottom, height - NINE_PATCH_BORDER))
            for x in range(max(left, NINE_PATCH_BORDER), min(right, width - NINE_PATCH_BORDER))
        )

    cut = list(set(cut))
    for x, y in cut:
        pixels[x, y] = TRANSPARENT

    return rgba, len(cut)


def save_magenta_preview(image: Image.Image, destination: Path) -> None:
    background = Image.new("RGBA", image.size, MAGENTA)
    background.alpha_composite(image)
    background.save(destination)


def main() -> None:
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    PREVIEW_DIR.mkdir(parents=True, exist_ok=True)

    for filename, config in FRAMES.items():
        source = SOURCE_DIR / filename
        output = OUT_DIR / filename
        preview = PREVIEW_DIR / filename.replace(".9.png", "_preview_magenta.png")

        cutout, pixels_cut = flood_dark_center(
            Image.open(source),
            threshold=config["threshold"],
            fill_bbox=config["fill_bbox"],
            clip_rect=config.get("clip_rect"),
            core_clear_rect=config.get("core_clear_rect"),
        )
        cutout.save(output)
        save_magenta_preview(cutout, preview)
        print(
            f"{filename}: threshold={config['threshold']} "
            f"fill_bbox={config['fill_bbox']} transparent_pixels={pixels_cut} "
            f"-> {output}"
        )


if __name__ == "__main__":
    main()
