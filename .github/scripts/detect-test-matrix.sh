#!/usr/bin/env bash
set -euo pipefail

property() {
  local name="$1"
  awk -F= -v name="$name" '$0 !~ /^[[:space:]]*#/ && $1 == name { sub(/^[^=]*=/, ""); gsub(/^[[:space:]]+|[[:space:]]+$/, ""); print; exit }' gradle.properties
}

minecraft="$(property minecraft_version)"
forge="$(property forge_version)"
[[ -n "$minecraft" ]] || { echo "minecraft_version is missing from gradle.properties" >&2; exit 1; }
[[ -n "$forge" ]] || { echo "forge_version is missing from gradle.properties" >&2; exit 1; }

java=8
runtime_matrix="[{\"loader\":\"forge\",\"loader-version\":\"$forge\",\"runtime-test\":\"lexforge\",\"regex\":\".*Forge.*\",\"fabric-api\":\"none\",\"compatibility\":\"none\"}]"
multiplayer_matrix="[{\"loader\":\"forge\",\"loader-version\":\"$forge\",\"regex\":\".*Forge.*\",\"fabric-api\":\"none\"}]"

echo "Detected Minecraft $minecraft, Java $java, loader forge"
echo "Runtime matrix: $runtime_matrix"
echo "Multiplayer matrix: $multiplayer_matrix"
if [[ -n "${GITHUB_OUTPUT:-}" ]]; then
  {
    echo "minecraft=$minecraft"
    echo "java=$java"
    echo "runtime-matrix=$runtime_matrix"
    echo "multiplayer-matrix=$multiplayer_matrix"
  } >> "$GITHUB_OUTPUT"
else
  printf 'minecraft=%s\njava=%s\nruntime-matrix=%s\nmultiplayer-matrix=%s\n' "$minecraft" "$java" "$runtime_matrix" "$multiplayer_matrix"
fi
