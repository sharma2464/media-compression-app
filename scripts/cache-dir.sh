#!/usr/bin/env bash
# Shared artifact paths (gitignored under repo via .cache/).
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
export CACHE_ROOT="${MEDIA_COMPRESSION_TMP:-$ROOT/.cache}"
export PREVIEW_CAPTURES="$CACHE_ROOT/preview-captures"
export DEBUG_SESSIONS="$CACHE_ROOT/debug-sessions"

mkdir -p "$PREVIEW_CAPTURES" "$DEBUG_SESSIONS"
