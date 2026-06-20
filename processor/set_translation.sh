#!/bin/bash
set -e

# Ensure we know where the project root is
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

LATEST_OUT_FOLDER=$(find "$PROJECT_ROOT/processor/out" -mindepth 2 -maxdepth 2 -printf "%T@ %p\n" 2>/dev/null | sort -n | tail -1 | cut -d' ' -f2-)

if [ -z "$LATEST_OUT_FOLDER" ]; then
    echo "No output folder found"
    exit 1
fi

cd "$PROJECT_ROOT"
./gradlew :processor:run -Plaunch='com.github.lauroschuck.quickcard4ankidroid.processor.PosTranslationSetter' --args="$1 $LATEST_OUT_FOLDER $2 '$3'"
