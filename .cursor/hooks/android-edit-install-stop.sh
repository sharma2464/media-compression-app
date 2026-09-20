#!/usr/bin/env bash
# End of agent turn: install if Android sources were edited.
set -euo pipefail
source "$(dirname "$0")/android-install-lib.sh"
[[ -f "$TOUCHED" ]] || exit 0
rm -f "$TOUCHED"
run_install_debug
exit 0
