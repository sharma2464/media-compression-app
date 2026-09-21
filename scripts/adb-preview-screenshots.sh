#!/usr/bin/env bash
# Capture compare-preview screenshots during an active compress session (device must show progress UI).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/screenshots/preview-captures/$(date +%Y%m%d-%H%M%S)"
mkdir -p "$OUT"
PKG=com.sharma2464.mediacompression

# Open expanded progress if minimized bar visible (~bottom card)
adb shell input tap 540 2050 || true
sleep 1.5

for i in $(seq -w 1 15); do
  adb exec-out screencap -p > "$OUT/step_${i}.png"
  if [[ "$i" == "04" || "$i" == "09" ]]; then
    adb shell input swipe 540 900 200 900 30
    sleep 0.4
    adb exec-out screencap -p > "$OUT/step_${i}_wipe_left.png"
    adb shell input swipe 540 900 900 900 30
    sleep 0.4
    adb exec-out screencap -p > "$OUT/step_${i}_wipe_right.png"
  fi
  sleep 3
done

adb shell run-as "$PKG" cat files/debug-d66e0a.ndjson 2>/dev/null > "$OUT/debug.ndjson" || true
echo "Saved $(ls -1 "$OUT"/*.png 2>/dev/null | wc -l) PNGs under $OUT"
