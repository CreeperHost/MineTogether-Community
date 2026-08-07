#!/usr/bin/env bash
set -euo pipefail

mods=build/ci-runtime/forge/mods
probe=build/ci-runtime/forge/probe
[[ -d "$mods" ]] || { echo "Missing staged Forge mods directory" >&2; exit 1; }
[[ -d "$probe" ]] || { echo "Missing staged Forge probe directory" >&2; exit 1; }
production_jar="$(find "$mods" -maxdepth 1 -type f -iname '*minetogether*.jar' ! -iname '*sources*' ! -iname '*dev*' -print -quit)"
probe_jar="$(find "$probe" -maxdepth 1 -type f -iname '*ci-probe*.jar' -print -quit)"
[[ -n "$production_jar" ]] || { echo "Missing MineTogether production jar" >&2; exit 1; }
[[ -n "$probe_jar" ]] || { echo "Missing multiplayer CI probe jar" >&2; exit 1; }
unzip -tqq "$production_jar"
unzip -tqq "$probe_jar"
unzip -p "$production_jar" mcmod.info >/dev/null
unzip -p "$probe_jar" mcmod.info >/dev/null
if unzip -l "$probe_jar" | grep -Eq 'net/creeperhost/minetogethercommunity/(MineTogether|proxy|chat/MineTogetherChat)\.class'; then
  echo "CI probe jar unexpectedly contains production classes" >&2
  exit 1
fi
echo "Verified Forge 1.7.10 runtime: $(basename "$production_jar") + $(basename "$probe_jar")"

service_root=build/ci-runtime/connect-service
service_jar="$(find "$service_root" -maxdepth 1 -type f -name '*.jar' -print -quit)"
[[ -n "$service_jar" ]] || { echo "Missing local Connect service jar" >&2; exit 1; }
unzip -tqq "$service_jar"
echo "Verified local Connect service: $(basename "$service_jar")"
