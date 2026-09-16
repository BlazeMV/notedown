#!/usr/bin/env bash
set -euo pipefail
MODS="${NOTEDOWN_MODS_DIR:-$HOME/Library/Application Support/PrismLauncher/instances/FO 26.2/minecraft/mods}"
JAR=$(ls build/libs/notedown-*.jar | grep -v sources | head -1)
rm -f "$MODS"/notedown-*.jar
cp "$JAR" "$MODS/" && echo "installed $(basename "$JAR") -> $MODS"
