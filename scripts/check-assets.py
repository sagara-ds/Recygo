"""Catch missing Figma asset references before packaging the native app."""
from pathlib import Path
import json
import re

ROOT = Path(__file__).resolve().parents[1]
assets = ROOT / "app/src/main/assets/figma"
manifest = json.loads((ROOT / "docs/figma-assets.json").read_text())
failures = []
for entry in manifest:
    file = assets / entry["file"]
    if not file.is_file() or not file.stat().st_size:
        failures.append("Missing/empty exported file: " + entry["file"])
for file in (ROOT / "app/src/main/java").rglob("*.kt"):
    source = file.read_text()
    for screen, name in re.findall(r'"(\d+:\d+)"\s*,\s*"(img\w+)"', source):
        prefix = screen.replace(":", "_") + "_" + name
        if not any((assets / (prefix + suffix)).is_file() for suffix in (".svg", ".png")):
            failures.append(file.name + ": " + prefix)
    # Includes names selected dynamically from a fixed list.
    for name in set(re.findall(r'"(img\w+)"', source)):
        if not any(assets.glob("*_" + name + ".*")):
            failures.append(file.name + ": unknown asset " + name)
if failures:
    raise SystemExit("\n".join(sorted(set(failures))))
print("PASS: exported manifest files and native asset references exist.")
