#!/usr/bin/env bash
# Shared helpers for post-edit installDebug hooks.

PROJECT_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
TOUCHED="$PROJECT_ROOT/.cursor/hooks/.android-touched"
LOG="$PROJECT_ROOT/.cursor/hooks/install-debug.log"
LOCK="$PROJECT_ROOT/.cursor/hooks/.install-debug.lock"

is_android_source_path() {
  local path="$1"
  path="${path#"$PROJECT_ROOT"/}"
  path="${path#./}"
  case "$path" in
    app/src/*|app/build.gradle.kts|build.gradle.kts|gradle/*|settings.gradle.kts) return 0 ;;
    *) return 1 ;;
  esac
}

hook_file_path() {
  local input="$1"
  if command -v jq >/dev/null 2>&1; then
    echo "$input" | jq -r '.file_path // .path // .file // empty' 2>/dev/null || true
    return
  fi
  python3 -c 'import sys,json; d=json.load(sys.stdin); print(d.get("file_path") or d.get("path") or d.get("file") or "")' <<<"$input" 2>/dev/null || true
}

mark_touched() {
  mkdir -p "$(dirname "$TOUCHED")"
  date -Iseconds >"$TOUCHED"
}

run_install_debug() {
  (
    flock -n 9 || exit 0
    cd "$PROJECT_ROOT"
    echo "=== installDebug $(date -Iseconds) ===" >>"$LOG"
    ./gradlew installDebug >>"$LOG" 2>&1
  ) 9>"$LOCK"
}
