#!/usr/bin/env bash
set -euo pipefail

root=build/ci-runtime
if [[ ! -d "$root" ]]; then
  echo "Missing staged runtime directory: $root" >&2
  exit 1
fi

found=0
while IFS= read -r -d '' loader_dir; do
  loader="$(basename "$loader_dir")"
  mods_dir="$loader_dir/mods"
  probe_dir="$loader_dir/probe"
  [[ -d "$mods_dir" ]] || { echo "Missing mods directory for $loader" >&2; exit 1; }
  [[ -d "$probe_dir" ]] || { echo "Missing CI probe directory for $loader" >&2; exit 1; }

  production_jar="$(find "$mods_dir" -maxdepth 1 -type f -iname '*minetogether*.jar' ! -iname '*sources*' ! -iname '*dev*' -print -quit)"
  [[ -n "$production_jar" ]] || { echo "Missing MineTogether production jar for $loader" >&2; exit 1; }
  probe_jar="$(find "$probe_dir" -maxdepth 1 -type f -iname '*ci-probe*.jar' -print -quit)"
  [[ -n "$probe_jar" ]] || { echo "Missing multiplayer CI probe jar for $loader" >&2; exit 1; }

  unzip -tqq "$production_jar"
  unzip -tqq "$probe_jar"
  case "$loader" in
    fabric)
      unzip -p "$production_jar" fabric.mod.json >/dev/null
      unzip -p "$probe_jar" fabric.mod.json >/dev/null
      ;;
    forge)
      if unzip -Z1 "$production_jar" | grep -qx 'META-INF/mods.toml'; then
        unzip -p "$production_jar" META-INF/mods.toml >/dev/null
      else
        unzip -p "$production_jar" mcmod.info >/dev/null
      fi
      if unzip -Z1 "$probe_jar" | grep -qx 'META-INF/mods.toml'; then
        unzip -p "$probe_jar" META-INF/mods.toml >/dev/null
      else
        unzip -p "$probe_jar" mcmod.info >/dev/null
      fi
      ;;
    neoforge)
      unzip -p "$production_jar" META-INF/neoforge.mods.toml >/dev/null
      unzip -p "$probe_jar" META-INF/neoforge.mods.toml >/dev/null
      ;;
  esac

  echo "Verified $loader runtime: $(basename "$production_jar") + $(basename "$probe_jar")"
  found=$((found + 1))
done < <(find "$root" -mindepth 1 -maxdepth 1 -type d -print0)

if [[ $found -eq 0 ]]; then
  echo "No loader runtimes were staged" >&2
  exit 1
fi
