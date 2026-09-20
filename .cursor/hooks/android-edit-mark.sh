#!/usr/bin/env bash
# Agent Write edits: mark Android sources dirty; install runs on stop.
set -euo pipefail
source "$(dirname "$0")/android-install-lib.sh"
input=$(cat)
path=$(hook_file_path "$input")
[[ -n "$path" ]] || exit 0
if is_android_source_path "$path"; then
  mark_touched
fi
exit 0
