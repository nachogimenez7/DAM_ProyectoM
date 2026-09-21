#!/usr/bin/env python3
"""Capture a read-only Kotlin reference for Swift equivalence tests."""
import hashlib
import json
import re
from pathlib import Path

IOS = Path(__file__).resolve().parents[1]
ANDROID = IOS.parent / "app/src/main/java/com/traidores/juego"
catalog = (ANDROID / "RoleCatalog.kt").read_text()
models = (ANDROID / "GameModels.kt").read_text()
keys = dict(re.findall(r'const val (\w+) = "(\w+)"', catalog))
maps = dict(re.findall(r'(MEDIEVAL|GREECE|PAMPA)\("(\w+)",', catalog))
roles = []
for block in re.findall(r'^        RoleDefinition\(\n(.*?)^        \)', catalog, re.M | re.S):
    match = re.match(r'\s*(\w+),\s*(GameRules\.\w+|"Neutral"),\s*"[^"\n]*",\s*(\d+)(?:,\s*RoleMap\.(\w+))?', block)
    if not match:
        raise ValueError("RoleDefinition format changed; review exporter before updating fixtures")
    key, team, minimum, exclusive = match.groups()
    team = {"GameRules.TOWN_WINNER": "Pueblo", "GameRules.TRAITOR_WINNER": "Traidores", '"Neutral"': "Neutral"}[team]
    roles.append({"key": keys[key], "team": team, "minimumPlayers": int(minimum), "exclusiveMap": maps.get(exclusive)})
phase_block = re.search(r'enum class GamePhase : Serializable \{([^}]+)\}', models).group(1)
phases = re.findall(r'\b[A-Z][A-Z_]+\b', phase_block)
assert len(roles) == 11 and len(phases) == 14 and len(maps) == 3
fixture = {
    "sourceSHA256": {name: hashlib.sha256((ANDROID / name).read_bytes()).hexdigest()
                     for name in ["RoleCatalog.kt", "GameModels.kt"]},
    "maps": list(maps.values()), "phases": phases, "roles": roles,
}
destination = IOS / "Packages/TraidoresCore/Tests/TraidoresCoreTests/Fixtures/android-catalog.json"
destination.parent.mkdir(parents=True, exist_ok=True)
destination.write_text(json.dumps(fixture, indent=2, ensure_ascii=False) + "\n")
print("Exported Kotlin catalog fixture without modifying Android.")
