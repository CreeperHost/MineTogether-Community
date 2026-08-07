#!/usr/bin/env bash
set -euo pipefail

log=run/logs/latest.log
[[ -f "$log" ]] || { echo "Minecraft did not produce $log" >&2; exit 1; }
failure_pattern='NoClassDefFoundError|ClassNotFoundException|ExceptionInInitializerError|Caught exception from|The game crashed|A fatal error has been detected'
if grep -Ein "$failure_pattern" "$log"; then
  echo "A fatal loading signature was found in the runtime log" >&2
  exit 1
fi

while IFS=: read -r line _; do
  [[ -n "$line" ]] || continue
  start=$((line > 40 ? line - 40 : 1)); end=$((line + 40))
  if ! sed -n "${start},${end}p" "$log" | grep -Eq 'META-INF/versions/9/module-info\.class.*headlessmc-lwjgl\.jar|Zip file headlessmc-lwjgl\.jar failed to read properly'; then
    echo "Unexpected Forge LoaderException at $log:$line" >&2
    exit 1
  fi
done < <(grep -n 'LoaderException' "$log" || true)
if ! grep -Eiq 'minetogethercommunity|MineTogether Community' "$log"; then
  echo "The runtime log does not show MineTogether loading" >&2
  exit 1
fi
echo "Forge 1.7.10 runtime log passed"
