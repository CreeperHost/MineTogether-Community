#!/usr/bin/env bash
set -euo pipefail

loader="${1:?loader is required}"
minecraft="${2:?Minecraft version is required}"
loader_version="${3:?loader version is required}"
java_version="${4:?Java version is required}"
launch_regex="${5:?HeadlessMC launch regex is required}"
hmc_jar="${6:?HeadlessMC launcher jar is required}"

repo="$(pwd)"
hmc_jar="$(realpath "$hmc_jar")"
work="$repo/build/ci-multiplayer/$loader"
manager="$work/manager"
shared_mc="${MINETOGETHER_CI_MINECRAFT_CACHE:-$work/minecraft}"
results="$work/results"
vanilla_port="${MINETOGETHER_CI_VANILLA_PORT:-25571}"
modded_port="${MINETOGETHER_CI_MODDED_PORT:-25572}"
chat_port="${MINETOGETHER_CI_CHAT_PORT:-26667}"
connect_proxy_port="${MINETOGETHER_CI_CONNECT_PROXY_PORT:-27777}"
connect_control_port="${MINETOGETHER_CI_CONNECT_CONTROL_PORT:-27778}"
connect_node_file="$work/connect-service/connect-nodes.json"
scenarios=",${MINETOGETHER_CI_SCENARIOS:-1,2,3,4,5,6,7,8,9,10},"
reuse_installs="${MINETOGETHER_CI_REUSE_INSTALLS:-false}"
process_groups=()
LAST_PID=""

cleanup() {
  local pgid
  for pgid in "${process_groups[@]}"; do
    kill -TERM -- "-$pgid" 2>/dev/null || true
  done
}
trap cleanup EXIT

fail() {
  echo "::error::$*" >&2
  exit 1
}

write_hmc_config() {
  local directory="$1"
  local game="$2"
  local username="$3"
  local uuid="$4"
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

hmc() {
  local directory="$1"
  shift
  (cd "$directory" && java -jar "$hmc_jar" --command "$@")
}

start_group() {
  local directory="$1"
  local log="$2"
  shift 2
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

remember_group() {
  local pgid="$1"
  process_groups+=("$pgid")
}

stop_group() {
  local pgid="$1"
  if kill -0 -- "-$pgid" 2>/dev/null; then
    kill -TERM -- "-$pgid" 2>/dev/null || true
    for _ in {1..30}; do
      if ! kill -0 -- "-$pgid" 2>/dev/null; then
        wait "$pgid" 2>/dev/null || true
        return 0
      fi
      sleep 1
    done
    kill -KILL -- "-$pgid" 2>/dev/null || true
  fi
  wait "$pgid" 2>/dev/null || true
}

await_group_exit() {
  local pgid="$1"
  local timeout="${2:-30}"
  for ((second = 0; second < timeout; second++)); do
    if ! kill -0 -- "-$pgid" 2>/dev/null; then
      wait "$pgid" 2>/dev/null || true
      return 0
    fi
    sleep 1
  done
  stop_group "$pgid"
}

wait_for_log() {
  local log="$1"
  local pattern="$2"
  local pgid="$3"
  local timeout="${4:-180}"
  for ((second = 0; second < timeout; second++)); do
    grep -Eq "$pattern" "$log" 2>/dev/null && return 0
    kill -0 -- "-$pgid" 2>/dev/null || fail "Process group $pgid exited before '$pattern' appeared in $log"
    sleep 1
  done
  fail "Timed out waiting for '$pattern' in $log"
}

wait_for_marker() {
  local marker="$1"
  local pgid="$2"
  local timeout="${3:-360}"
  for ((second = 0; second < timeout; second++)); do
    [[ -f "$results/$marker" ]] && return 0
    [[ -f "$results/failure" ]] && fail "CI probe reported failure: $(cat "$results/failure.txt" 2>/dev/null || true)"
    kill -0 -- "-$pgid" 2>/dev/null || fail "Client process group $pgid exited before marker '$marker'"
    sleep 1
  done
  fail "Timed out waiting for CI marker '$marker'"
}

server_directory() {
  local name="$1"
  local directory
  directory="$(find "$manager/HeadlessMC/servers" -type d -name "$name" -print -quit)"
  [[ -n "$directory" && -d "$directory" ]] || fail "HeadlessMC did not create server directory named $name"
  printf '%s\n' "$directory"
}

configure_server() {
  local directory="$1"
  local port="$2"
  mkdir -p "$directory/config"
  cat > "$directory/server.properties" <<EOF
online-mode=false
server-ip=127.0.0.1
server-port=$port
motd=MineTogether CI
view-distance=3
simulation-distance=3
max-players=4
spawn-protection=0
EOF
  cat > "$directory/config/minetogethercommunity.json" <<'EOF'
{
  "dedicatedServerConnect": "off",
  "debugMode": true
}
EOF
}

start_server() {
  local name="$1"
  local log="$2"
  start_group "$manager" "$log" java -jar "$hmc_jar" --command server launch "$name" --jvm '"-Xms512m -Xmx1024m"'
  remember_group "$LAST_PID"
  wait_for_log "$log" 'Done \([^)]*\)!|Done \(' "$LAST_PID" 240
}

create_client() {
  local name="$1"
  local username="$2"
  local uuid="$3"
  local modded="$4"
  local directory="$work/clients/$name"
  local game="$directory/game"
  write_hmc_config "$directory" "$game" "$username" "$uuid"
  mkdir -p "$game/mods"
  cat > "$game/options.txt" <<'EOF'
onboardAccessibility:false
renderDistance:2
simulationDistance:5
graphicsMode:0
maxFps:10
EOF
  if [[ "$modded" == true ]]; then
    mkdir -p "$game/local/minetogether"
    cat > "$game/local/minetogether/minetogethercommunity.json" <<'EOF'
{
  "connectPackPrompted": true,
  "connectPackBypass": true
}
EOF
    cp "$repo/build/ci-runtime/$loader/mods/"*.jar "$game/mods/"
    cp "$repo/build/ci-runtime/$loader/probe/"*.jar "$game/mods/"
  fi
  printf '%s\n' "$directory"
}

start_client() {
  local directory="$1"
  local name="$2"
  local version="$3"
  local port="$4"
  local role="${5:-}"
  local expected="${6:-1}"
  local peer="${7:-CiSender}"
  local connect_uuid="${8:-}"
  local connect_username="${9:-$name}"
  local connect_server_token="${10:-server-0001}"
  local role_timeout=3600
  local log="$work/logs/$name.log"
  local jvm="-Xms384m -Xmx1024m"
  local environment=(env)
  local command=(launch "$version")
  local game_args=()
  if [[ -n "$role" ]]; then
    [[ "$role" == connect-host ]] && role_timeout=12000
    environment+=(
      "MINETOGETHER_CI_ROLE=$role"
      "MINETOGETHER_CI_TIMEOUT_TICKS=$role_timeout"
      "MINETOGETHER_CI_EXPECTED_PLAYERS=$expected"
      "MINETOGETHER_CI_PEER_NAME=$peer"
      "MINETOGETHER_CI_RESULTS=$results"
      "MINETOGETHER_CI_SERVER_ADDRESS=127.0.0.1:$port"
    )
    if [[ "$role" == chat-* || "$role" == ctcp-* ]]; then
      environment+=("MINETOGETHER_CI_CHAT_PORT=$chat_port")
    fi
    if [[ "$role" == connect-* && -n "$connect_uuid" ]]; then
      jvm="-Xms384m -Xmx1536m"
      environment+=(
        "MINETOGETHER_CI_CHAT_PORT=$chat_port"
        "MINETOGETHER_CI_CONNECT_UUID=$connect_uuid"
        "MINETOGETHER_CI_CONNECT_USERNAME=$connect_username"
        "MINETOGETHER_CI_CONNECT_SERVER_TOKEN=$connect_server_token"
        "MINETOGETHER_CI_CONNECT_HOST_HASH=$connect_host_hash"
      )
      jvm+=" -Dconnect.mesh.hosts=$connect_node_file -Dconnect.node=ci-local"
    fi
  fi
  if [[ -z "$role" ]]; then
    game_args+=(--game-args "--quickPlayMultiplayer=127.0.0.1:$port")
  fi
  [[ "$version" == "$launch_regex" ]] && command+=( -regex )

  start_group "$directory" "$log" "${environment[@]}" java -jar "$hmc_jar" --command "${command[@]}" --jvm "\"$jvm\"" "${game_args[@]}"
  remember_group "$LAST_PID"
}

reset_results() {
  rm -rf "$results"
  mkdir -p "$results"
}

run_scenario() {
  [[ "$scenarios" == *",$1,"* ]]
}

assert_clean_logs() {
  local bad='Mixin apply failed|Mixin transformation of .* failed|InvalidMixinException|NoClassDefFoundError|ClassNotFoundException|ExceptionInInitializerError|Connection refused|Failed to connect to the server|Connection Lost|Internal Exception|The game crashed|A fatal error has been detected'
  local logs=()
  mapfile -d '' logs < <(find "$work/clients" "$work/logs" -type f -name '*.log' -print0)
  [[ ${#logs[@]} -gt 0 ]] || fail "No multiplayer logs were produced"
  local matches
  matches="$(grep -Ein "$bad" "${logs[@]}" | grep -vE 'dev[./]ftb[./]mods[./]ftbquests[./]client[./]FTBQuestsNetClient' || true)"
  if run_scenario 9; then
    matches="$(printf '%s\n' "$matches" | grep -vF 'MineTogether connection lost. Your world is no longer shared to friends.' || true)"
  fi
  if [[ -n "$matches" ]]; then
    printf '%s\n' "$matches"
    fail "A fatal runtime signature was found in multiplayer logs"
  fi
}

if [[ "$reuse_installs" == true ]]; then
  rm -rf "$work/logs" "$work/clients" "$results"
else
  rm -rf "$work"
fi
mkdir -p "$manager" "$shared_mc" "$work/logs" "$work/clients" "$results"
write_hmc_config "$manager" "$manager/game" CiManager 00000000-0000-0000-0000-000000000001

echo "Installing Minecraft $minecraft and $loader $loader_version"
if [[ ! -f "$shared_mc/versions/$minecraft/$minecraft.json" ]]; then
  hmc "$manager" download "$minecraft"
fi
if ! find "$shared_mc/versions" -mindepth 1 -maxdepth 1 -type d -iname "*$loader*" -print -quit | grep -q .; then
  hmc "$manager" "$loader" "$minecraft" --uid "$loader_version" --java "$java_version"
fi
if ! find "$manager/HeadlessMC/servers" -type d -name vanilla-ci -print -quit 2>/dev/null | grep -q .; then
  hmc "$manager" server add vanilla "$minecraft" vanilla-ci
fi
if ! find "$manager/HeadlessMC/servers" -type d -name modded-ci -print -quit 2>/dev/null | grep -q .; then
  hmc "$manager" server add "$loader" "$minecraft" modded-ci "$loader_version"
fi
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
ctcp_sender="$(create_client ctcp-sender CiCtcpSend 00000000-0000-0000-0000-000000000110 true)"
ctcp_receiver="$(create_client ctcp-receiver CiCtcpRecv 00000000-0000-0000-0000-000000000120 true)"
singleplayer="$(create_client singleplayer CiSingleplayer 00000000-0000-0000-0000-000000000080 true)"
connect_ui="$(create_client connect-ui CiConnectUi 00000000-0000-0000-0000-000000000081 true)"
connect_unavailable="$(create_client connect-unavailable CiConnectOffline 00000000-0000-0000-0000-000000000082 true)"
late_sender="$(create_client late-sender CiLateSend 00000000-0000-0000-0000-000000000090 true)"
late_receiver="$(create_client late-receiver CiLateRecv 00000000-0000-0000-0000-000000000100 true)"
connect_host_uuid="10000000-0000-4000-8000-000000000001"
connect_friend_uuid="20000000-0000-4000-8000-000000000002"
connect_extra_uuid="30000000-0000-4000-8000-000000000003"
connect_outsider_uuid="40000000-0000-4000-8000-000000000004"
connect_host_hash="$(printf '%s' "$connect_host_uuid" | sha256sum | awk '{print toupper($1)}')"
connect_host="$(create_client connect-host CiConnectHost "$connect_host_uuid" true)"
connect_friend="$(create_client connect-friend CiConnectFriend "$connect_friend_uuid" true)"
connect_extra="$(create_client connect-extra CiConnectExtra "$connect_extra_uuid" true)"
connect_outsider="$(create_client connect-outsider CiConnectOutsider "$connect_outsider_uuid" true)"

if run_scenario 5; then
  echo "Scenario 5/10: offline modded client creates and enters a fresh singleplayer world"
  reset_results
  start_client "$singleplayer" singleplayer "$launch_regex" 0 singleplayer 1
  singleplayer_pid="$LAST_PID"
  wait_for_marker singleplayer-success "$singleplayer_pid" 480
  await_group_exit "$singleplayer_pid" 60
  assert_clean_logs "$work/logs/singleplayer.log"
fi

if run_scenario 8; then
  echo "Scenario 8/10: unavailable Connect discovery leaves Multiplayer usable and backs off cleanly"
  reset_results
  start_client "$connect_unavailable" connect-unavailable "$launch_regex" 0 connect-unavailable 1
  connect_unavailable_pid="$LAST_PID"
  wait_for_marker connect-unavailable-backoff "$connect_unavailable_pid" 240
  wait_for_marker connect-unavailable-success "$connect_unavailable_pid" 30
  await_group_exit "$connect_unavailable_pid" 60
  assert_clean_logs "$work/logs/connect-unavailable.log"
fi

if run_scenario 9; then
  echo "Scenario 9/10: local Connect publish, discovery, relay admission, rejection, limits, and lifecycle"
  reset_results
  connect_service_jar="$(find "$repo/build/ci-runtime/connect-service" -maxdepth 1 -type f -name '*.jar' -print -quit)"
  [[ -n "$connect_service_jar" ]] || fail "Staged local MTConnect service jar was not found"

  start_group "$work" "$work/logs/connect-service.log" java -jar "$connect_service_jar" \
    --proxy-port "$connect_proxy_port" --control-port "$connect_control_port" \
    --work-dir "$work/connect-service"
  connect_service_pid="$LAST_PID"
  remember_group "$connect_service_pid"
  wait_for_log "$work/logs/connect-service.log" '^READY ' "$connect_service_pid" 60

  connect_control="http://127.0.0.1:$connect_control_port"
  curl -fsS -X POST "$connect_control/fixture/friend?left=$connect_host_uuid&right=$connect_friend_uuid" >/dev/null
  curl -fsS -X POST "$connect_control/fixture/friend?left=$connect_host_uuid&right=$connect_extra_uuid" >/dev/null
  curl -fsS -X POST "$connect_control/fixture/limit?user=$connect_host_uuid&max=2" >/dev/null

  start_client "$connect_host" connect-host "$launch_regex" 0 connect-host 1 CiConnectFriend \
    "$connect_host_uuid" CiConnectHost
  connect_host_pid="$LAST_PID"
  wait_for_marker connect-host-published "$connect_host_pid" 480
  curl -fsS "$connect_control/state" | grep -q '"serverToken":"server-0001"' \
    || fail "Local Connect service did not register the initial host"

  start_client "$connect_outsider" connect-outsider "$launch_regex" 0 connect-outsider 1 CiConnectHost \
    "$connect_outsider_uuid" CiConnectOutsider server-0001
  connect_outsider_pid="$LAST_PID"
  wait_for_marker connect-outsider-rejected "$connect_outsider_pid" 180
  await_group_exit "$connect_outsider_pid" 60

  start_client "$connect_friend" connect-friend "$launch_regex" 0 connect-friend 2 CiConnectHost \
    "$connect_friend_uuid" CiConnectFriend server-0001
  connect_friend_pid="$LAST_PID"
  wait_for_marker connect-friend-listing "$connect_friend_pid" 240
  wait_for_marker connect-friend-joined "$connect_friend_pid" 360
  wait_for_marker connect-host-friend-visible "$connect_host_pid" 60

  start_client "$connect_extra" connect-extra "$launch_regex" 0 connect-extra 1 CiConnectHost \
    "$connect_extra_uuid" CiConnectExtra server-0001
  connect_extra_pid="$LAST_PID"
  wait_for_marker connect-extra-rejected "$connect_extra_pid" 180
  await_group_exit "$connect_extra_pid" 60
  curl -fsS "$connect_control/events" | grep -q '"type":"REJECTED_NOT_FRIEND"' \
    || fail "Local Connect service did not audit the non-friend rejection"
  curl -fsS "$connect_control/events" | grep -q '"type":"REJECTED_FULL"' \
    || fail "Local Connect service did not audit the full-server rejection"

  touch "$results/connect-friend-release"
  wait_for_marker connect-friend-success "$connect_friend_pid" 60
  await_group_exit "$connect_friend_pid" 60

  touch "$results/connect-host-close-request"
  wait_for_marker connect-host-closed "$connect_host_pid" 120
  curl -fsS "$connect_control/state" | grep -q '"hosts":\[\]' \
    || fail "Closing through the Connect UI left a host registration behind"

  touch "$results/connect-host-republish-request"
  wait_for_marker connect-host-republished "$connect_host_pid" 180
  curl -fsS "$connect_control/state" | grep -q '"serverToken":"server-0002"' \
    || fail "Republishing did not create exactly one fresh host registration"

  touch "$results/connect-host-fault-request"
  curl -fsS -X POST "$connect_control/fault/drop-host?user=$connect_host_uuid" | grep -q '"dropped":true' \
    || fail "Could not inject the local Connect proxy-loss fault"
  wait_for_marker connect-host-fault-observed "$connect_host_pid" 120
  curl -fsS "$connect_control/state" | grep -q '"hosts":\[\]' \
    || fail "Proxy loss left a host registration behind"

  touch "$results/connect-host-final-republish-request"
  wait_for_marker connect-host-final-republished "$connect_host_pid" 180
  curl -fsS "$connect_control/state" | grep -q '"serverToken":"server-0003"' \
    || fail "Host could not republish after proxy loss"
  [[ "$(curl -fsS "$connect_control/state" | grep -o '"serverToken"' | wc -l)" -eq 1 ]] \
    || fail "Republish produced duplicate host registrations"

  touch "$results/connect-host-stop-request"
  wait_for_marker connect-host-success "$connect_host_pid" 60
  await_group_exit "$connect_host_pid" 60
  curl -fsS -X POST "$connect_control/shutdown" >/dev/null || true
  stop_group "$connect_service_pid"

  if grep -E 'sessions\.minetogether\.io|dist\.creeper\.host/MineTogether/nodes|api\.creeper\.host/.*/profile' \
      "$work/logs/connect-"*.log; then
    fail "A local Connect scenario attempted to use a production service"
  fi
fi

if run_scenario 7; then
  echo "Scenario 7/10: singleplayer pause menu opens translated MineTogether Connect controls"
  reset_results
  start_client "$connect_ui" connect-ui "$launch_regex" 0 connect-ui 1
  connect_ui_pid="$LAST_PID"
  wait_for_marker connect-ui-settings-ready "$connect_ui_pid" 480
  wait_for_marker connect-ui-success "$connect_ui_pid" 30
  await_group_exit "$connect_ui_pid" 60
  assert_clean_logs "$work/logs/connect-ui.log"
fi

if run_scenario 1; then
  echo "Scenario 1/10: offline modded client joins a vanilla server and safely attempts an emote"
  reset_results
  start_server vanilla-ci "$work/logs/vanilla-server.log"
  vanilla_server_pid="$LAST_PID"
  start_client "$modded_connect" modded-connect "$launch_regex" "$vanilla_port" connect 1
  connect_pid="$LAST_PID"
  wait_for_marker connect-success "$connect_pid"
  await_group_exit "$connect_pid"
  stop_group "$vanilla_server_pid"
fi

if run_scenario 2 || run_scenario 3 || run_scenario 4 || run_scenario 6; then
  start_server modded-ci "$work/logs/modded-server.log"
  modded_server_pid="$LAST_PID"
fi

if run_scenario 2; then
  echo "Scenario 2/10: vanilla and modded clients stay connected while the modded client emits an emote"
  reset_results
  start_client "$vanilla_client" vanilla-client "$minecraft" "$modded_port"
  vanilla_pid="$LAST_PID"
  wait_for_log "$work/logs/modded-server.log" 'CiVanilla.*joined the game' "$modded_server_pid" 240
  start_client "$mixed_sender" mixed-sender "$launch_regex" "$modded_port" mixed-sender 2
  mixed_pid="$LAST_PID"
  wait_for_marker mixed-sender-success "$mixed_pid"
  kill -0 -- "-$vanilla_pid" 2>/dev/null || fail "Vanilla client exited after the modded client sent an emote"
  grep -Eq 'CiVanilla.*joined the game' "$work/logs/modded-server.log" || fail "Vanilla client never joined the modded server"
  grep -Eq 'CiMixed.*joined the game' "$work/logs/modded-server.log" || fail "Modded client never joined beside the vanilla client"
  if grep -Eq 'CiVanilla (lost connection|left the game)' "$work/logs/modded-server.log"; then
    fail "Vanilla client disconnected while the modded peer emitted an emote"
  fi
  await_group_exit "$mixed_pid"
  stop_group "$vanilla_pid"
fi

if run_scenario 3; then
  echo "Scenario 3/10: two modded clients relay and apply emote start/stop packets"
  reset_results
  start_client "$receiver" receiver "$launch_regex" "$modded_port" receiver 2 CiSender
  receiver_pid="$LAST_PID"
  start_client "$sender" sender "$launch_regex" "$modded_port" sender 2 CiReceiver
  sender_pid="$LAST_PID"
  wait_for_marker receiver-remote-start "$receiver_pid"
  wait_for_marker receiver-remote-stop "$receiver_pid"
  wait_for_marker receiver-success "$receiver_pid"
  wait_for_marker sender-success "$sender_pid"
  await_group_exit "$receiver_pid"
  await_group_exit "$sender_pid"
fi

if run_scenario 6; then
  echo "Scenario 6/10: a late peer receives persistent emote state and disconnect cleanup removes its stale state"
  reset_results
  start_client "$late_sender" late-sender "$launch_regex" "$modded_port" late-sender 1 CiLateRecv
  late_sender_pid="$LAST_PID"
  wait_for_marker late-sender-emote-started "$late_sender_pid"
  start_client "$late_receiver" late-receiver "$launch_regex" "$modded_port" late-receiver 2 CiLateSend
  late_receiver_pid="$LAST_PID"
  wait_for_marker late-receiver-observed-existing "$late_receiver_pid"
  wait_for_marker late-receiver-disconnect-requested "$late_receiver_pid"
  wait_for_marker late-sender-disconnect-clean "$late_sender_pid"
  wait_for_marker late-sender-success "$late_sender_pid"
  stop_group "$late_receiver_pid"
  await_group_exit "$late_sender_pid" 60
  assert_clean_logs "$work/logs/late-sender.log" "$work/logs/late-receiver.log" "$work/logs/modded-server.log"
fi

if run_scenario 4; then
  echo "Scenario 4/10: two offline clients use mocked API discovery and relay MineTogether chat over local IRC"
  reset_results
  start_group "$work" "$work/logs/mock-irc.log" python3 -u "$repo/.github/scripts/mock-irc-server.py" --host 127.0.0.1 --port "$chat_port" --channel '#minetogether-ci'
  mock_irc_pid="$LAST_PID"
  remember_group "$mock_irc_pid"
  wait_for_log "$work/logs/mock-irc.log" '^READY ' "$mock_irc_pid" 30
  start_client "$chat_receiver" chat-receiver "$launch_regex" "$modded_port" chat-receiver 2 CiChatSend
  chat_receiver_pid="$LAST_PID"
  start_client "$chat_sender" chat-sender "$launch_regex" "$modded_port" chat-sender 2 CiChatRecv
  chat_sender_pid="$LAST_PID"
  wait_for_marker chat-message-sent "$chat_sender_pid"
  wait_for_marker chat-message-received "$chat_receiver_pid"
  wait_for_marker chat-sender-success "$chat_sender_pid"
  wait_for_marker chat-receiver-success "$chat_receiver_pid"
  await_group_exit "$chat_sender_pid"
  await_group_exit "$chat_receiver_pid"
  stop_group "$mock_irc_pid"
fi

if run_scenario 2 || run_scenario 3 || run_scenario 4 || run_scenario 6; then
  stop_group "$modded_server_pid"
fi

if run_scenario 10; then
  echo "Scenario 10/10: two MineTogether clients relay emotes over CTCP on a vanilla server"
  reset_results
  start_server vanilla-ci "$work/logs/vanilla-ctcp-server.log"
  vanilla_ctcp_server_pid="$LAST_PID"
  start_group "$work" "$work/logs/mock-irc-ctcp.log" python3 -u "$repo/.github/scripts/mock-irc-server.py" --host 127.0.0.1 --port "$chat_port" --channel '#minetogether-ci'
  mock_irc_ctcp_pid="$LAST_PID"
  remember_group "$mock_irc_ctcp_pid"
  wait_for_log "$work/logs/mock-irc-ctcp.log" '^READY ' "$mock_irc_ctcp_pid" 30
  start_client "$ctcp_receiver" ctcp-receiver "$launch_regex" "$vanilla_port" ctcp-receiver 2 CiCtcpSend
  ctcp_receiver_pid="$LAST_PID"
  start_client "$ctcp_sender" ctcp-sender "$launch_regex" "$vanilla_port" ctcp-sender 2 CiCtcpRecv
  ctcp_sender_pid="$LAST_PID"
  wait_for_marker ctcp-emote-start-sent "$ctcp_sender_pid"
  wait_for_marker ctcp-receiver-remote-start "$ctcp_receiver_pid"
  wait_for_marker ctcp-emote-stop-sent "$ctcp_sender_pid"
  wait_for_marker ctcp-receiver-remote-stop "$ctcp_receiver_pid"
  wait_for_marker ctcp-sender-success "$ctcp_sender_pid"
  wait_for_marker ctcp-receiver-success "$ctcp_receiver_pid"
  await_group_exit "$ctcp_sender_pid"
  await_group_exit "$ctcp_receiver_pid"
  stop_group "$mock_irc_ctcp_pid"
  stop_group "$vanilla_ctcp_server_pid"
fi

assert_clean_logs
echo "All $loader singleplayer, offline connect, local chat, multiplayer compatibility, emote relay, and cleanup scenarios passed."
