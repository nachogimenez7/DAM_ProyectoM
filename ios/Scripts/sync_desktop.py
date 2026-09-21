#!/usr/bin/env python3
"""Update the existing desktop copy, retaining signing, Xcode settings and local package layout.
Never edits Android; creates a dated backup of every existing file it will overwrite.
"""
import datetime
import json
import plistlib
import shutil
import subprocess
from pathlib import Path

IOS = Path(__file__).resolve().parents[1]
DEST = Path.home() / "Desktop/TraidoresIOS"
project = Path("TraidoresIOS.xcodeproj/project.pbxproj")
if not (DEST / project).is_file():
    raise SystemExit("Desktop Xcode project missing; refusing to create a different checkout implicitly.")


def load_project(path):
    return json.loads(subprocess.check_output(["plutil", "-convert", "json", "-o", "-", str(path)]))


def encode(value, depth=0):
    tab = "\t" * depth
    if isinstance(value, dict):
        return "{\n" + "".join(f'{tab}\t{json.dumps(k)} = {encode(v, depth + 1)};\n' for k, v in value.items()) + tab + "}"
    if isinstance(value, list):
        return "(" + ", ".join(encode(v, depth) for v in value) + ")"
    return str(value) if isinstance(value, int) else json.dumps(value, ensure_ascii=False)


canonical = load_project(IOS / "TraidoresIOS" / project)
local = load_project(DEST / project)
objects = local["objects"]
# The generated IDs are stable. Add only missing source references/build files;
# preserve user project settings, scheme, Info.plist, configurations and entitlements.
for key, value in canonical["objects"].items():
    if key not in objects:
        objects[key] = value
    elif value["isa"] == "PBXSourcesBuildPhase":
        objects[key]["files"] = list(dict.fromkeys(objects[key]["files"] + value["files"]))
    elif value["isa"] == "PBXGroup" and "children" in value:
        objects[key]["children"] = list(dict.fromkeys(objects[key]["children"] + value["children"]))
for value in objects.values():
    if value["isa"] == "XCLocalSwiftPackageReference" and "TraidoresCore" in value.get("relativePath", ""):
        value["relativePath"] = "Packages/TraidoresCore"

copies = []
for folder in ["App", "DesignSystem", "Features", "Platform", "Resources"]:
    root = IOS / "TraidoresIOS/TraidoresIOS" / folder
    for src in root.rglob("*"):
        if src.is_file() and src.name != ".DS_Store":
            copies.append((src, DEST / "TraidoresIOS" / src.relative_to(root.parent)))
for src in (IOS / "Packages/TraidoresCore").rglob("*"):
    relative = src.relative_to(IOS / "Packages")
    if src.is_file() and not any(p in {".build", ".swiftpm", ".DS_Store"} for p in relative.parts):
        copies.append((src, DEST / "Packages" / relative))
copies.append((IOS / "docs/PRUEBA_PARTIDA_CLASICA.md", DEST / "PRUEBA_PARTIDA_CLASICA.md"))
backup = Path.home() / "Library/Application Support/TraidoresIOS/Backups" / datetime.datetime.now().strftime("%Y%m%d-%H%M%S-%f")
for target in [dst for _, dst in copies] + [DEST / project]:
    if target.exists():
        stored = backup / target.relative_to(DEST)
        stored.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(target, stored)
for src, dst in copies:
    dst.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(src, dst)
(DEST / project).write_text("// !$*UTF8*$!\n" + encode(local) + "\n")
subprocess.run(["plutil", "-lint", str(DEST / project)], check=True)
# Assert exactly the settings used by the current desktop target remain intact.
updated = load_project(DEST / project)
for key, obj in local["objects"].items():
    if obj["isa"] == "XCBuildConfiguration":
        assert updated["objects"][key]["buildSettings"] == obj["buildSettings"]
print(f"Updated {len(copies)} files; backup: {backup}")
