#!/usr/bin/env bash
set -euo pipefail

root=build/ci-runtime/forge
mods="$root/mods"
probe="$root/probe"

[[ -d "$mods" ]] || { echo "Missing Forge runtime mods directory: $mods" >&2; exit 1; }
[[ -d "$probe" ]] || { echo "Missing Forge CI probe directory: $probe" >&2; exit 1; }

production_jar="$(find "$mods" -maxdepth 1 -type f -iname '*minetogether*.jar' ! -iname '*thin*' ! -iname '*sources*' ! -iname '*ci-probe*' -print -quit)"
probe_jar="$(find "$probe" -maxdepth 1 -type f -iname '*ci-probe*.jar' -print -quit)"
[[ -n "$production_jar" ]] || { echo "Missing reobfuscated MineTogether production jar" >&2; exit 1; }
[[ -n "$probe_jar" ]] || { echo "Missing reobfuscated Forge 1.12.2 CI probe jar" >&2; exit 1; }

unzip -tqq "$production_jar"
unzip -tqq "$probe_jar"
unzip -p "$production_jar" mcmod.info >/dev/null
unzip -p "$probe_jar" mcmod.info >/dev/null

if unzip -Z1 "$production_jar" | grep -Eq '(^|/)(fabric\.mod\.json|mods\.toml|neoforge\.mods\.toml)$'; then
  echo "Production jar contains modern loader metadata" >&2
  exit 1
fi
if unzip -Z1 "$production_jar" | grep -q '^net/creeperhost/minetogethercommunity/ci/'; then
  echo "Production jar contains CI-only probe classes" >&2
  exit 1
fi
if ! unzip -Z1 "$probe_jar" | grep -q '^net/creeperhost/minetogethercommunity/ci/MineTogetherCiProbe.class$'; then
  echo "CI probe jar does not contain the runtime probe" >&2
  exit 1
fi

echo "Verified forge runtime: $(basename "$production_jar") + $(basename "$probe_jar")"
