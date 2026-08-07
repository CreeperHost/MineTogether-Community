#!/usr/bin/env python3
import json
import os
from pathlib import Path
import sys
from urllib.parse import urlencode
from urllib.request import Request, urlopen


def output(name: str, value: str) -> None:
    output_file = os.environ.get("GITHUB_OUTPUT")
    if output_file:
        with open(output_file, "a", encoding="utf-8") as stream:
            stream.write(f"{name}={value}\n")
    else:
        print(f"{name}={value}")


if len(sys.argv) != 5:
    raise SystemExit("usage: install-modrinth-mod.py PROJECT MINECRAFT LOADER DESTINATION")

project, minecraft, loader, destination = sys.argv[1:]
query = urlencode({
    "game_versions": json.dumps([minecraft]),
    "loaders": json.dumps([loader]),
})
request = Request(
    f"https://api.modrinth.com/v2/project/{project}/version?{query}",
    headers={"User-Agent": "CreeperHost/MineTogether-Community commit-tests"},
)

with urlopen(request, timeout=30) as response:
    versions = json.load(response)

if not versions:
    output("available", "false")
    raise SystemExit(0)

version = versions[0]
files = version.get("files", [])
selected = next((item for item in files if item.get("primary")), files[0] if files else None)
if selected is None:
    raise SystemExit(f"Modrinth returned version {version.get('id')} without any files")

target = Path(destination)
target.parent.mkdir(parents=True, exist_ok=True)
download = Request(selected["url"], headers={"User-Agent": request.headers["User-agent"]})
with urlopen(download, timeout=60) as response, target.open("wb") as stream:
    while chunk := response.read(1024 * 1024):
        stream.write(chunk)

print(f"Installed {project} {version.get('version_number', version.get('id'))} as {target}")
output("available", "true")
output("version", str(version.get("version_number", version.get("id", "unknown"))))
