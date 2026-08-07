#!/usr/bin/env bash
set -euo pipefail

compatibility="${1:-none}"
log=run/logs/latest.log

if [[ ! -f "$log" ]]; then
  echo "Minecraft did not produce $log" >&2
  exit 1
fi

failure_pattern='Mixin apply failed|MixinApplyError|InvalidMixinException|InjectionError|Critical injection failure|Overwrite conflict|@Overwrite conflict|Failed to apply mixin|ModLoadingException|LoadingFailedException|ExceptionInInitializerError'
if grep -Ein "$failure_pattern" "$log"; then
  echo "A fatal loading or mixin failure was found in the runtime log" >&2
  exit 1
fi

if ! grep -Eiq 'minetogethercommunity|MineTogether Community' "$log"; then
  echo "The runtime log does not show MineTogether loading" >&2
  exit 1
fi

if [[ "$compatibility" == "no-chat-reports" ]]; then
  if ! grep -Eiq 'no[ _-]?chat[ _-]?reports|No Chat Reports' "$log"; then
    echo "The runtime log does not show No Chat Reports loading" >&2
    exit 1
  fi

  if ! grep -Eiq 'MixinChatScreen.*nochatreports\.mixins\.json' "$log"; then
    echo "The runtime log does not show No Chat Reports applying its ChatScreen mixin" >&2
    exit 1
  fi
fi

echo "Runtime log passed for compatibility row: $compatibility"
