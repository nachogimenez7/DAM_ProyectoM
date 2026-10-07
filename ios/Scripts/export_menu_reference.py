#!/usr/bin/env python3
"""Mechanically port Android's read-only menu content and missing role artwork."""
import json
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
IOS = ROOT / "ios/TraidoresIOS/TraidoresIOS"
RES = ROOT / "app/src/main/res"
source = (ROOT / "app/src/main/java/com/traidores/juego/RoleCatalog.kt").read_text()
keys = dict(re.findall(r'const val (\w+) = "(\w+)"', source))
definitions = {}
for block in re.findall(r'^        RoleDefinition\(\n(.*?)^        \)', source, re.M | re.S):
    match = re.match(r'\s*(\w+),\s*(GameRules\.\w+|"Neutral"),\s*"([^"\n]*)",\s*(\d+)', block)
    assert match, block
    key, team, function, minimum = match.groups()
    definitions[key] = dict(function=function, minimum=int(minimum), team={
        "GameRules.TOWN_WINNER": "Pueblo", "GameRules.TRAITOR_WINNER": "Traidores", '"Neutral"': "Neutral"
    }[team])
advice = dict(re.findall(r'(\w+) to\s*"([^"\n]*)"', source.split('private val adviceByKey =')[1].split('fun definition')[0]))
stories = source.split('private val stories =')[1]
names = source.split('private fun displayName')[1].split('private fun gameName')[0]
maps = []
for kotlin_map, wire, suffix, short in [
    ("MEDIEVAL", "medieval", "medieval", "Feudo"),
    ("GREECE", "grecia", "griego", "Grecia"),
    ("PAMPA", "pampa", "gaucho", "Pampa"),
]:
    info = re.search(r'RoleMap\.' + kotlin_map + r' -> MapInfo\(\s*"([^"]*)",\s*"([^"]*)",\s*"([^"]*)",\s*displayName\((\w+), map\),\s*"([^"]*)"', source)
    assert info
    title, era, description, exclusive, asset = info.groups()
    story_block = re.search(r'RoleMap\.' + kotlin_map + r' to mapOf\((.*?)\n        \)', stories, re.S).group(1)
    name_block = re.search(r'RoleMap\.' + kotlin_map + r' -> when \(key\) \{(.*?)\n            \}', names, re.S).group(1)
    display_names = dict(re.findall(r'(\w+) -> "([^"]*)"', name_block))
    roles = []
    for key, story in re.findall(r'(\w+) to "([^"\n]*)"', story_block):
        image_key = "detective" if key == "POLICIA" else keys[key]
        roles.append(dict(id=keys[key], title=display_names[key], story=story,
                          image=f"rol_{image_key}_{suffix}", advice=advice[key], **definitions[key]))
    assert len(roles) == 9
    maps.append(dict(id=wire, title=title, shortTitle=short, era=era, description=description,
                     image=asset, exclusive=display_names[exclusive], roles=roles))
android = '{http://schemas.android.com/apk/res/android}'
help_sections = []
for element in ET.parse(RES / "layout/activity_ayuda.xml").iter():
    identifier = element.get(android + 'id', '')
    if identifier.startswith('@+id/helpTitle'):
        help_sections.append(dict(id=identifier.removeprefix('@+id/helpTitle'), title=element.get(android + 'text'), body=''))
    elif identifier.startswith('@+id/helpBody') and element.get(android + 'text'):
        section = next(item for item in help_sections if item['id'] == identifier.removeprefix('@+id/helpBody'))
        section['body'] = element.get(android + 'text').replace('\\n', '\n')
strings = {e.get('name'): ''.join(e.itertext()) for e in ET.parse(RES / "values/strings.xml").getroot()}
tutorial = [dict(id=key, title=strings[f'tutorial_{key}_title'], body=strings[f'tutorial_{key}_body'],
                 hint=strings[f'tutorial_{key}_hint']) for key in ['role', 'night', 'debate', 'vote']]
assert len(help_sections) == 10 and all(s['body'] for s in help_sections[:-1])
profile_source = (ROOT / 'app/src/main/java/com/traidores/juego/ProfileCustomizationCatalog.kt').read_text()
achievement_constants = dict(re.findall(r'const val (ACH_\w+) = "([^"]+)"', profile_source))
achievements = []
for block in re.findall(r'ProfileAchievement\(\s*id = (ACH_\w+),(.*?)\n        \)', profile_source, re.S):
    constant, fields = block
    values = dict(re.findall(r'(name|shortName|description) = "([^"]*)"', fields))
    rarity = re.search(r'rarity = AchievementRarity\.(\w+)', fields).group(1)
    achievements.append(dict(id=achievement_constants[constant], title=values['name'],
        shortTitle=values['shortName'], description=values['description'], rarity=rarity))
emote_source = (ROOT / 'app/src/main/java/com/traidores/juego/EmoteCatalog.kt').read_text().split('val defaultLoadoutIds')[0]
emotes = []
for block in re.findall(r'        emote\((.*?)\n        \)', emote_source, re.S):
    values = dict(re.findall(r'(id|label|description|themeLabel) = "([^"]*)"', block))
    asset = re.search(r'imageRes = R.drawable.(\w+)', block).group(1)
    tone = re.search(r'toneHex = "(#[0-9A-Fa-f]{6})"', block).group(1)
    category = re.search(r'category = EmoteCategory\.(\w+)', block)
    emotes.append(dict(id=values['id'], title=values['label'], description=values['description'],
        theme=values['themeLabel'], image=asset, tone=tone, category=category.group(1) if category else 'CLASSIC',
        premium='isPremium = true' in block, animated='isAnimated = true' in block))
assert len(achievements) == 10 and len(emotes) == 20
payload = json.dumps(dict(maps=maps, help=help_sections, tutorial=tutorial,
    achievements=achievements, emotes=emotes), ensure_ascii=False, indent=2)
swift = '''// Generated by ios/scripts/export_menu_reference.py from Android menu sources.
import Foundation

struct GuideRole: Codable, Identifiable, Sendable {
    let id, title, story, image, advice, function, team: String
    let minimum: Int
}
struct GuideMap: Codable, Identifiable, Sendable {
    let id, title, shortTitle, era, description, image, exclusive: String
    let roles: [GuideRole]
}
struct HelpSectionContent: Codable, Identifiable, Sendable {
    let id, title, body: String
}
struct TutorialContent: Codable, Identifiable, Sendable {
    let id, title, body, hint: String
}
struct ProfileAchievementContent: Codable, Identifiable, Sendable {
    let id, title, shortTitle, description, rarity: String
}
struct ProfileEmoteContent: Codable, Identifiable, Sendable {
    let id, title, description, theme, image, category, tone: String
    let premium, animated: Bool
}
enum AndroidMenuReference {
    struct Content: Codable, Sendable {
        let maps: [GuideMap]
        let help: [HelpSectionContent]
        let tutorial: [TutorialContent]
        let achievements: [ProfileAchievementContent]
        let emotes: [ProfileEmoteContent]
    }
    static let content = try! JSONDecoder().decode(Content.self, from: Data(json.utf8))
    private static let json = #"""
''' + payload + '\n"""#\n}\n'
(IOS / 'Features/Menu/AndroidMenuReference.swift').write_text(swift)
if '--content-only' in sys.argv:
    sys.exit(0)  # Regenerate text content without re-converting artwork (no Pillow needed).
from PIL import Image
imported = []
for role in [role for m in maps for role in m['roles']]:
    name = role['image']
    destination = IOS / f'Resources/Assets.xcassets/{name}.imageset'
    if destination.exists():
        continue  # Existing/user-adjusted artwork is never overwritten.
    source_image = RES / f'drawable/{name}.webp'
    assert source_image.exists(), source_image
    destination.mkdir()
    with Image.open(source_image) as image:
        image.save(destination / f'{name}.png', optimize=True)
    (destination / 'Contents.json').write_text(json.dumps(dict(
        images=[dict(filename=f'{name}.png', idiom='universal')], info=dict(author='xcode', version=1)), indent=2) + '\n')
    imported.append(name)
print(f'Exported {len(maps)} maps, 27 role entries, 10 help sections, 4 tutorial pages; imported {len(imported)} missing images.')
profile_images = list((RES / 'drawable-nodpi').glob('profile_*.webp')) + list((RES / 'drawable-nodpi').glob('reaction_*.*'))
for source_image in sorted(profile_images):
    name = source_image.stem
    destination = IOS / f'Resources/Assets.xcassets/{name}.imageset'
    if destination.exists():
        continue
    destination.mkdir()
    with Image.open(source_image) as image:
        image.save(destination / f'{name}.png', optimize=True)
    (destination / 'Contents.json').write_text(json.dumps(dict(
        images=[dict(filename=f'{name}.png', idiom='universal')], info=dict(author='xcode', version=1)), indent=2) + '\n')
    print(f'Imported profile artwork: {name}')
