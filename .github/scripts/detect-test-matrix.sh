#!/usr/bin/env bash
set -euo pipefail

property() {
  local name="$1"
  awk -F= -v name="$name" '
    $0 !~ /^[[:space:]]*#/ && $1 == name {
      sub(/^[^=]*=/, "")
      gsub(/^[[:space:]]+|[[:space:]]+$/, "")
      print
      exit
    }
  ' gradle.properties
}

minecraft="$(property minecraft_version)"
loaders="$(property enabled_platforms)"

if [[ -z "$minecraft" ]]; then
  echo "minecraft_version is missing from gradle.properties" >&2
  exit 1
fi

if [[ -z "$loaders" ]]; then
  detected=()
  for loader in fabric forge neoforge; do
    [[ -d "$loader" ]] && detected+=("$loader")
  done
  loaders="$(IFS=,; echo "${detected[*]}")"
fi

if [[ -z "$loaders" ]]; then
  echo "No enabled loader projects were found" >&2
  exit 1
fi

java_version="$(property ci_java_version)"
if [[ -z "$java_version" ]]; then
  java_version="$(grep -RhsE 'options\.release([[:space:]]*=|\.set\()[[:space:]]*[0-9]+' --include='*.gradle' . \
    | grep -Eo '[0-9]+' | head -n 1 || true)"
fi

if [[ -z "$java_version" ]]; then
  case "$minecraft" in
    26.*) java_version=25 ;;
    1.21*) java_version=21 ;;
    1.20.[5-9]*) java_version=21 ;;
    1.18*|1.19*|1.20*) java_version=17 ;;
    *) java_version=8 ;;
  esac
fi

fabric_api="$(property fabric_api_version)"
fabric_api="${fabric_api%%+*}"
[[ -n "$fabric_api" ]] || fabric_api=none

matrix='['
server_matrix='['
separator=''
server_separator=''
IFS=',' read -ra loader_list <<< "$loaders"
for raw_loader in "${loader_list[@]}"; do
  loader="$(echo "$raw_loader" | tr -d '[:space:]')"
  case "$loader" in
    fabric)
      runtime_test=fabric
      regex='.*fabric.*'
      row_fabric_api="$fabric_api"
      loader_version="$(property fabric_loader_version)"
      ;;
    forge)
      runtime_test=lexforge
      loader_version="$(property forge_version)"
      case "$minecraft" in
        1.12.*) regex="${minecraft}-forge${minecraft}-${loader_version}" ;;
        *) regex="forge-${minecraft}-${loader_version}" ;;
      esac
      row_fabric_api=none
      ;;
    neoforge)
      runtime_test=neoforge
      regex='.*neoforge.*'
      row_fabric_api=none
      loader_version="$(property neoforge_version)"
      ;;
    *)
      echo "Unsupported loader in enabled_platforms: $loader" >&2
      exit 1
      ;;
  esac

  if [[ -z "$loader_version" ]]; then
    echo "Loader version is missing for $loader" >&2
    exit 1
  fi

  compatibility_rows=(none)
  case "$minecraft" in
    1.19*|1.20*|1.21*|26.*) compatibility_rows+=(no-chat-reports) ;;
  esac
  for compatibility in "${compatibility_rows[@]}"; do
    matrix+="$separator{\"loader\":\"$loader\",\"loader-version\":\"$loader_version\",\"runtime-test\":\"$runtime_test\",\"regex\":\"$regex\",\"fabric-api\":\"$row_fabric_api\",\"compatibility\":\"$compatibility\"}"
    separator=','
  done
  server_matrix+="$server_separator{\"loader\":\"$loader\",\"loader-version\":\"$loader_version\",\"regex\":\"$regex\",\"fabric-api\":\"$row_fabric_api\"}"
  server_separator=','
done
matrix+=']'
server_matrix+=']'

echo "Detected Minecraft $minecraft, Java $java_version, loaders $loaders"
echo "Runtime matrix: $matrix"
echo "Multiplayer matrix: $server_matrix"

if [[ -n "${GITHUB_OUTPUT:-}" ]]; then
  {
    echo "minecraft=$minecraft"
    echo "java=$java_version"
    echo "runtime-matrix=$matrix"
    echo "multiplayer-matrix=$server_matrix"
  } >> "$GITHUB_OUTPUT"
else
  printf 'minecraft=%s\njava=%s\nruntime-matrix=%s\nmultiplayer-matrix=%s\n' "$minecraft" "$java_version" "$matrix" "$server_matrix"
fi
