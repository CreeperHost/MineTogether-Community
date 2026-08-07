#!/usr/bin/env bash
set -euo pipefail

loader="${1:?loader is required}"
minecraft="${2:?Minecraft version is required}"
loader_version="${3:?loader version is required}"
forge_uid="${loader_version%%-*}"
java_version="${4:?Java version is required}"
launch_regex="${5:?HeadlessMC launch regex is required}"
hmc_jar="${6:?HeadlessMC launcher jar is required}"
[[ "$loader" == forge ]] || { echo "::error::Forge is the only loader on the 1.7.10 branch" >&2; exit 1; }

repo="$(pwd)"
hmc_jar="$(realpath "$hmc_jar")"
work="$repo/build/ci-multiplayer/$loader"
manager="$work/manager"
shared_mc="${MINETOGETHER_CI_MINECRAFT_CACHE:-$work/minecraft}"
results="$work/results"
vanilla_port="${MINETOGETHER_CI_VANILLA_PORT:-25571}"
modded_port="${MINETOGETHER_CI_MODDED_PORT:-25572}"
chat_port="${MINETOGETHER_CI_CHAT_PORT:-26667}"
scenarios=",${MINETOGETHER_CI_SCENARIOS:-1,2,3,4},"
reuse_installs="${MINETOGETHER_CI_REUSE_INSTALLS:-false}"
process_groups=()
LAST_PID=""

cleanup() {
  local pgid
  for pgid in "${process_groups[@]}"; do kill -TERM -- "-$pgid" 2>/dev/null || true; done
}
trap cleanup EXIT
fail() { echo "::error::$*" >&2; exit 1; }

write_hmc_config() {
  local directory="$1" game="$2" username="$3" uuid="$4"
  mkdir -p "$directory/HeadlessMC" "$game"
  cat > "$directory/HeadlessMC/config.properties" <<EOF
hmc.mcdir=$shared_mc
hmc.gamedir=$game
hmc.offline=true
hmc.offline.username=$username
hmc.offline.uuid=$uuid
hmc.no.auto.config=true
hmc.java.versions=$(command -v java)
hmc.store.accounts=false
EOF
}

hmc() { local directory="$1"; shift; (cd "$directory" && java -jar "$hmc_jar" --command "$@"); }

start_group() {
  local directory="$1" log="$2"; shift 2
  mkdir -p "$(dirname "$log")"
  (cd "$directory" && exec setsid "$@") > "$log" 2>&1 &
  LAST_PID=$!
  for _ in {1..50}; do
    kill -0 -- "-$LAST_PID" 2>/dev/null && return 0
    kill -0 "$LAST_PID" 2>/dev/null || fail "Process $LAST_PID exited before its process group was established; see $log"
    sleep 0.1
  done
  fail "Process group $LAST_PID was not established; see $log"
}

remember_group() { process_groups+=("$1"); }

stop_group() {
  local pgid="$1"
  if kill -0 -- "-$pgid" 2>/dev/null; then
    kill -TERM -- "-$pgid" 2>/dev/null || true
    for _ in {1..30}; do
      if ! kill -0 -- "-$pgid" 2>/dev/null; then wait "$pgid" 2>/dev/null || true; return 0; fi
      sleep 1
    done
    kill -KILL -- "-$pgid" 2>/dev/null || true
  fi
  wait "$pgid" 2>/dev/null || true
}

await_group_exit() {
  local pgid="$1" timeout="${2:-30}"
  for ((second=0; second<timeout; second++)); do
    if ! kill -0 -- "-$pgid" 2>/dev/null; then wait "$pgid" 2>/dev/null || true; return 0; fi
    sleep 1
  done
  stop_group "$pgid"
}

wait_for_log() {
  local log="$1" pattern="$2" pgid="$3" timeout="${4:-180}" watch_pgid="${5:-}"
  for ((second=0; second<timeout; second++)); do
    grep -Eq "$pattern" "$log" 2>/dev/null && return 0
    kill -0 -- "-$pgid" 2>/dev/null || fail "Process group $pgid exited before '$pattern' appeared in $log"
    if [[ -n "$watch_pgid" ]]; then
      kill -0 -- "-$watch_pgid" 2>/dev/null || fail "Client process group $watch_pgid exited before '$pattern' appeared in $log"
    fi
    sleep 1
  done
  fail "Timed out waiting for '$pattern' in $log"
}

wait_for_marker() {
  local marker="$1" pgid="$2" timeout="${3:-360}"
  for ((second=0; second<timeout; second++)); do
    [[ -f "$results/$marker" ]] && return 0
    [[ -f "$results/failure" ]] && fail "CI probe reported failure: $(cat "$results/failure.txt" 2>/dev/null || true)"
    kill -0 -- "-$pgid" 2>/dev/null || fail "Client process group $pgid exited before marker '$marker'"
    sleep 1
  done
  fail "Timed out waiting for CI marker '$marker'"
}

server_directory() {
  local name="$1" directory
  directory="$(find "$manager/HeadlessMC/servers" -type d -name "$name" -print -quit)"
  [[ -n "$directory" && -d "$directory" ]] || fail "HeadlessMC did not create server directory named $name"
  printf '%s\n' "$directory"
}

configure_server() {
  local directory="$1" port="$2"
  mkdir -p "$directory/config"
  cat > "$directory/server.properties" <<EOF
online-mode=false
server-ip=127.0.0.1
server-port=$port
motd=MineTogether CI
view-distance=3
max-players=4
spawn-protection=0
EOF
  cat > "$directory/config/minetogethercommunity.json" <<'EOF'
{"dedicatedServerConnect":"off","debugMode":true}
EOF
}

start_server() {
  local name="$1" log="$2"
  start_group "$manager" "$log" java -jar "$hmc_jar" --command server launch "$name" --jvm '"-Xms512m -Xmx1024m"'
  remember_group "$LAST_PID"
  wait_for_log "$log" 'Done \([^)]*\)!|Done \(' "$LAST_PID" 300
}

create_client() {
  local name="$1" username="$2" uuid="$3" modded="$4"
  local directory="$work/clients/$name" game="$work/clients/$name/game"
  write_hmc_config "$directory" "$game" "$username" "$uuid"
  mkdir -p "$game/mods"
  if [[ "$modded" == true ]]; then
    cp "$repo/build/ci-runtime/$loader/mods/"*.jar "$game/mods/"
    cp "$repo/build/ci-runtime/$loader/probe/"*.jar "$game/mods/"
  fi
  printf '%s\n' "$directory"
}

start_client() {
  local directory="$1" name="$2" version="$3" port="$4" role="${5:-}" expected="${6:-1}" peer="${7:-CiSender}"
  local log="$work/logs/$name.log" jvm="-Xms384m -Xmx1024m"
  local environment=(env) command=(launch "$version") game_args=()
  if [[ -n "$role" ]]; then
    environment+=("MINETOGETHER_CI_ROLE=$role" "MINETOGETHER_CI_EXPECTED_PLAYERS=$expected" "MINETOGETHER_CI_PEER_NAME=$peer" "MINETOGETHER_CI_RESULTS=$results" "MINETOGETHER_CI_SERVER_ADDRESS=127.0.0.1:$port")
    [[ "$role" == chat-* ]] && environment+=("MINETOGETHER_CI_CHAT_PORT=$chat_port")
  else
    game_args+=(--game-args "\"--server 127.0.0.1 --port $port\"")
  fi
  [[ "$version" == "$launch_regex" ]] && command+=(-regex)
  start_group "$directory" "$log" "${environment[@]}" java -jar "$hmc_jar" --command "${command[@]}" --jvm "\"$jvm\"" "${game_args[@]}"
  remember_group "$LAST_PID"
}

reset_results() { rm -rf "$results"; mkdir -p "$results"; }
run_scenario() { [[ "$scenarios" == *",$1,"* ]]; }

if [[ "$scenarios" == ",2," ]]; then
  echo "::notice::Scenario 2 is unsupported on Forge 1.7.10: its server handshake waits ten hours for an FML response and then rejects a vanilla client before any mod can participate."
  exit 0
fi

assert_clean_logs() {
  local bad='NoClassDefFoundError|ClassNotFoundException|ExceptionInInitializerError|Caught exception from|Connection refused|Failed to connect to the server|Connection Lost|The game crashed|A fatal error has been detected'
  local logs=()
  mapfile -d '' logs < <(find "$work" -type f -name '*.log' -print0)
  [[ ${#logs[@]} -gt 0 ]] || fail "No multiplayer logs were produced"
  if grep -Ein "$bad" "${logs[@]}"; then fail "A fatal runtime signature was found in multiplayer logs"; fi

  local internal_errors
  internal_errors="$(grep -Hin 'Internal Exception' "${logs[@]}" || true)"
  if [[ -n "$internal_errors" ]] && echo "$internal_errors" | grep -Ev 'Ci(Connect|Vanilla|Mixed|Sender|Receiver|ChatSend|ChatRecv) lost connection:.*Internal Exception: java\.io\.IOException: Connection reset by peer'; then
    fail "An unexpected internal connection error was found in multiplayer logs"
  fi

  # Forge 1.7.10's ASM 5 scanner reports HMC 2.10's Java 9 module descriptor twice,
  # then deliberately ignores that injected headless helper. Keep all other loader errors fatal.
  local match file line start end unexpected=false
  while IFS= read -r match; do
    [[ -n "$match" ]] || continue
    file="${match%%:*}"
    line="${match#*:}"; line="${line%%:*}"
    start=$((line > 40 ? line - 40 : 1)); end=$((line + 40))
    if ! sed -n "${start},${end}p" "$file" | grep -Eq 'META-INF/versions/9/module-info\.class.*headlessmc-lwjgl\.jar|Zip file headlessmc-lwjgl\.jar failed to read properly'; then
      echo "$match" >&2
      unexpected=true
    fi
  done < <(grep -Hn 'LoaderException' "${logs[@]}" || true)
  [[ "$unexpected" == false ]] || fail "An unexpected Forge loader error was found in multiplayer logs"
}

if [[ "$reuse_installs" == true ]]; then rm -rf "$work/logs" "$work/clients" "$results"; else rm -rf "$work"; fi
mkdir -p "$manager" "$shared_mc" "$work/logs" "$work/clients" "$results"
write_hmc_config "$manager" "$manager/game" CiManager 00000000-0000-0000-0000-000000000001

echo "Installing Minecraft $minecraft and Forge $loader_version"
[[ -f "$shared_mc/versions/$minecraft/$minecraft.json" ]] || hmc "$manager" download "$minecraft"
if ! find "$shared_mc/versions" -mindepth 1 -maxdepth 1 -type d -iname '*forge*' -print -quit | grep -q .; then
  hmc "$manager" forge "$minecraft" --uid "$forge_uid" --java "$java_version"
fi
if ! find "$manager/HeadlessMC/servers" -type d -name vanilla-ci -print -quit 2>/dev/null | grep -q .; then hmc "$manager" server add vanilla "$minecraft" vanilla-ci; fi
if ! find "$manager/HeadlessMC/servers" -type d -name modded-ci -print -quit 2>/dev/null | grep -q .; then hmc "$manager" server add forge "$minecraft" modded-ci "$forge_uid"; fi
hmc "$manager" server eula vanilla-ci accept
hmc "$manager" server eula modded-ci accept

vanilla_server="$(server_directory vanilla-ci)"
modded_server="$(server_directory modded-ci)"
configure_server "$vanilla_server" "$vanilla_port"
configure_server "$modded_server" "$modded_port"
mkdir -p "$modded_server/mods"
cp "$repo/build/ci-runtime/$loader/mods/"*.jar "$modded_server/mods/"

modded_connect="$(create_client modded-connect CiConnect 00000000-0000-0000-0000-000000000010 true)"
vanilla_client="$(create_client vanilla-client CiVanilla 00000000-0000-0000-0000-000000000020 false)"
mixed_sender="$(create_client mixed-sender CiMixed 00000000-0000-0000-0000-000000000030 true)"
sender="$(create_client sender CiSender 00000000-0000-0000-0000-000000000040 true)"
receiver="$(create_client receiver CiReceiver 00000000-0000-0000-0000-000000000050 true)"
chat_sender="$(create_client chat-sender CiChatSend 00000000-0000-0000-0000-000000000060 true)"
chat_receiver="$(create_client chat-receiver CiChatRecv 00000000-0000-0000-0000-000000000070 true)"

if run_scenario 1; then
  echo "Scenario 1/4: offline modded client joins a vanilla server and safely attempts an emote"
  reset_results; start_server vanilla-ci "$work/logs/vanilla-server.log"; vanilla_server_pid="$LAST_PID"
  start_client "$modded_connect" modded-connect "$launch_regex" "$vanilla_port" connect 1; connect_pid="$LAST_PID"
  wait_for_marker connect-success "$connect_pid"; await_group_exit "$connect_pid"; stop_group "$vanilla_server_pid"
fi

if run_scenario 3 || run_scenario 4; then start_server modded-ci "$work/logs/modded-server.log"; modded_server_pid="$LAST_PID"; fi

if run_scenario 2; then
  echo "::notice::Scenario 2 is unsupported on Forge 1.7.10: its server handshake waits ten hours for an FML response and then rejects a vanilla client before any mod can participate."
fi

if run_scenario 3; then
  echo "Scenario 3/4: two modded clients relay and apply emote start/stop packets"
  reset_results; start_client "$receiver" receiver "$launch_regex" "$modded_port" receiver 2 CiSender; receiver_pid="$LAST_PID"
  start_client "$sender" sender "$launch_regex" "$modded_port" sender 2 CiReceiver; sender_pid="$LAST_PID"
  wait_for_marker receiver-remote-start "$receiver_pid"; wait_for_marker receiver-remote-stop "$receiver_pid"
  wait_for_marker sender-success "$sender_pid"; wait_for_marker receiver-success "$receiver_pid"
  await_group_exit "$receiver_pid"; await_group_exit "$sender_pid"
fi

if run_scenario 4; then
  echo "Scenario 4/4: two offline clients use mocked API discovery and relay MineTogether chat over local IRC"
  reset_results; start_group "$work" "$work/logs/mock-irc.log" python3 -u "$repo/.github/scripts/mock-irc-server.py" --host 127.0.0.1 --port "$chat_port" --channel '#minetogether-ci'; mock_irc_pid="$LAST_PID"
  remember_group "$mock_irc_pid"; wait_for_log "$work/logs/mock-irc.log" '^READY ' "$mock_irc_pid" 30
  start_client "$chat_receiver" chat-receiver "$launch_regex" "$modded_port" chat-receiver 2 CiChatSend; chat_receiver_pid="$LAST_PID"
  start_client "$chat_sender" chat-sender "$launch_regex" "$modded_port" chat-sender 2 CiChatRecv; chat_sender_pid="$LAST_PID"
  wait_for_marker chat-message-sent "$chat_sender_pid"; wait_for_marker chat-message-received "$chat_receiver_pid"
  wait_for_marker chat-sender-success "$chat_sender_pid"; wait_for_marker chat-receiver-success "$chat_receiver_pid"
  await_group_exit "$chat_sender_pid"; await_group_exit "$chat_receiver_pid"; stop_group "$mock_irc_pid"
fi

if run_scenario 3 || run_scenario 4; then stop_group "$modded_server_pid"; fi
assert_clean_logs
echo "All selected technically supported Forge 1.7.10 offline connect, local chat, and emote relay scenarios passed."
