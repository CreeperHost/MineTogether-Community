#!/usr/bin/env bash
# The shared entry point for local Docker/act runs and the manual GitHub workflow.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/../.."

selection="${1:-all}"
authenticated="${CI_AUTHENTICATED:-true}"
case "$selection" in
  all) loaders=(neoforge fabric) ;;
  neoforge|fabric) loaders=("$selection") ;;
  *) echo 'Usage: bash common/tests/run-tests.sh [all|neoforge|fabric]' >&2; exit 2 ;;
esac
case "$authenticated" in
  true|false) ;;
  *) echo 'CI_AUTHENTICATED must be true or false.' >&2; exit 2 ;;
esac

export LIBGL_ALWAYS_SOFTWARE=true SDL_VIDEODRIVER=x11 ALSOFT_DRIVERS=null
export MT_CI_DEFER_REPORTS=true
mkdir -p build/test-suite
: > build/test-suite/phases.tsv
rm -f build/test-suite/discord-sent.txt build/test-suite/results.txt
for loader in "${loaders[@]}"; do
  for kind in dedicated-server title-screen; do
    # Never summarize a previous run when compilation or launch fails before preparation.
    rm -f "build/$kind/$loader/results.json" "build/$kind/$loader/success.txt" \
      "build/$kind/$loader/failure.txt" "build/$kind/$loader/discord-sent.txt" \
      "build/$kind/$loader/title-screen.png" "build/$kind/$loader/friends-screen.png" \
      "build/$kind/$loader/chat-screen.png" "build/$kind/$loader/failure.png"
  done
done
failures=()
run_phase() {
  local name="$1" log="$2"
  shift 2
  mkdir -p "$(dirname "$log")"
  echo "Running $name"
  # Keep going after failure, while preserving it in the final suite exit status.
  if "$@" 2>&1 | tee "$log"; then
    echo "PASS: $name"
    printf '%s\ttrue\n' "$name" >> build/test-suite/phases.tsv
  else
    failures+=("$name")
    echo "FAIL: $name (continuing with remaining tests)" >&2
    printf '%s\tfalse\n' "$name" >> build/test-suite/phases.tsv
  fi
}

check_results() {
  local output="$1"
  test -f "$output/success.txt" || return 1
  test ! -f "$output/failure.txt" || return 1
  test -s "$output/results.json" || return 1
}

server_tests() {
  local loader="$1"
  bash ./gradlew ":$loader:ciServerUnitTest" ":$loader:runCiDedicatedServer" \
    --init-script common/tests/server-tests.gradle --console=plain --continue || return $?
  check_results "build/dedicated-server/$loader"
}

client_tests() {
  local loader="$1" output="build/title-screen/$1"
  # Leave time for device-code approval, then world creation and friends-screen checks.
  timeout --signal=TERM --kill-after=15s 20m xvfb-run -a -s '-screen 0 1280x720x24' \
    bash ./gradlew ":$loader:ciReportingTest" ":$loader:runCiTitleScreenshot" \
      --init-script common/tests/client-tests.gradle --console=plain --continue \
      "-PciAuthenticated=$authenticated" || return $?
  check_results "$output" || return $?
  test -s "$output/title-screen.png" || return 1
  if [[ "$authenticated" == true ]]; then
    test -s "$output/friends-screen.png" || return 1
    test -s "$output/chat-screen.png" || return 1
  fi
}

probe_tasks=()
for loader in "${loaders[@]}"; do probe_tasks+=(":$loader:ciProbeClasses"); done
run_phase 'Shared unit tests and probe compilation' build/test-suite/support.log \
  bash ./gradlew :common:ciConnectServiceTest "${probe_tasks[@]}" \
    --init-script common/tests/test-support.gradle --console=plain --continue

for loader in "${loaders[@]}"; do
  run_phase "$loader dedicated server" "build/dedicated-server/$loader/launch.log" server_tests "$loader"
  run_phase "$loader client" "build/title-screen/$loader/launch.log" client_tests "$loader"
done

if bash ./gradlew :common:ciSuiteReport --init-script common/tests/reporting.gradle \
    --console=plain "-PciLoaders=$selection" 2>&1 | tee build/test-suite/report.log; then
  echo 'Combined report completed.'
else
  failures+=('Combined report')
fi

if ((${#failures[@]})); then
  printf 'Failed test group: %s\n' "${failures[@]}" >&2
  exit 1
fi
echo 'All requested test groups passed.'
