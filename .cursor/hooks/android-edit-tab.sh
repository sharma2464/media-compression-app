#!/usr/bin/env bash
# Tab inline edits: build and install immediately.
set -euo pipefail
source "$(dirname "$0")/android-install-lib.sh"
input=$(cat)
path=$(hook_file_path "$input")
[[ -n "$path" ]] || exit 0
is_android_source_path "$path" || exit 0
mark_touched
run_install_debug
exit 0
