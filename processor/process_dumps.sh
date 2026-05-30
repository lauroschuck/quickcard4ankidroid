#!/bin/bash
set -e

# Support --force flag
FORCE=false
if [[ "$1" == "--force" || "$1" == "-f" ]]; then
    FORCE=true
    shift
fi

# Require version, threads and mirrors as arguments
if [ "$#" -lt 3 ]; then
    echo "Usage: $0 [--force] <version> <threads> <mirror_url_1> [mirror_url_2 ...]"
    exit 1
fi

VERSION=$1
THREADS=$2
shift 2

# Support mirrors as subsequent arguments
MIRRORS=""
while (( "$#" )); do
    if [ -n "$MIRRORS" ]; then
        MIRRORS="$MIRRORS,$1"
    else
        MIRRORS="$1"
    fi
    shift
done

# Configuration: Map of language code to dump URL
declare -A DUMPS
DUMPS=(
    ["en"]="https://kaikki.org/dictionary/raw-wiktextract-data.jsonl.gz"
    ["zh"]="https://kaikki.org/dictionary/downloads/zh/zh-extract.jsonl.gz"
    ["cs"]="https://kaikki.org/dictionary/downloads/cs/cs-extract.jsonl.gz"
    ["nl"]="https://kaikki.org/dictionary/downloads/nl/nl-extract.jsonl.gz"
    ["fr"]="https://kaikki.org/dictionary/downloads/fr/fr-extract.jsonl.gz"
    ["de"]="https://kaikki.org/dictionary/downloads/de/de-extract.jsonl.gz"
    ["el"]="https://kaikki.org/dictionary/downloads/el/el-extract.jsonl.gz"
    ["id"]="https://kaikki.org/dictionary/downloads/id/id-extract.jsonl.gz"
    ["it"]="https://kaikki.org/dictionary/downloads/it/it-extract.jsonl.gz"
    ["ja"]="https://kaikki.org/dictionary/downloads/ja/ja-extract.jsonl.gz"
    ["ko"]="https://kaikki.org/dictionary/downloads/ko/ko-extract.jsonl.gz"
    ["ku"]="https://kaikki.org/dictionary/downloads/ku/ku-extract.jsonl.gz"
    ["ms"]="https://kaikki.org/dictionary/downloads/ms/ms-extract.jsonl.gz"
    ["pl"]="https://kaikki.org/dictionary/downloads/pl/pl-extract.jsonl.gz"
    ["pt"]="https://kaikki.org/dictionary/downloads/pt/pt-extract.jsonl.gz"
    ["ru"]="https://kaikki.org/dictionary/downloads/ru/ru-extract.jsonl.gz"
    ["es"]="https://kaikki.org/dictionary/downloads/es/es-extract.jsonl.gz"
    ["th"]="https://kaikki.org/dictionary/downloads/th/th-extract.jsonl.gz"
    ["tr"]="https://kaikki.org/dictionary/downloads/tr/tr-extract.jsonl.gz"
    ["vi"]="https://kaikki.org/dictionary/downloads/vi/vi-extract.jsonl.gz"
)

LEARNING_LANGS="af,am,ar,az,be,bg,bn,bs,ca,cs,cy,da,de,el,en,es,et,eu,fa,fi,fr,fy,ga,gd,gl,gn,gu,ha,he,hi,hr,hu,hy,id,ig,is,it,ja,ka,km,kn,ko,ky,lb,ln,lo,lt,lv,mk,ml,mn,mr,ms,mt,my,nb,ne,nl,no,or,pa,pl,pt,ro,ru,sk,sl,so,sq,sr,sv,sw,ta,te,tg,th,tl,tr,uk,ur,uz,vi,zh,zu"

# Ensure we know where the project root is
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
BASE_DOWNLOAD_DIR="$PROJECT_ROOT/processor/downloads"

# Ensure base download directory exists
mkdir -p "$BASE_DOWNLOAD_DIR"

NEW_DUMPS_COUNT=0

echo "Step 1: Downloading and decompressing new dumps..."
for LANG_CODE in "${!DUMPS[@]}"; do
    URL="${DUMPS[$LANG_CODE]}"
    FILENAME=$(basename "$URL")

    echo "Checking $LANG_CODE dump at $URL..."

    # Get Last-Modified header. -L follows redirects, -I gets headers only.
    LAST_MOD=$(curl -sIL "$URL" | grep -i '^last-modified:' | tail -n 1 | cut -d' ' -f2- | tr -d '\r')

    if [ -z "$LAST_MOD" ]; then
        echo "Warning: Could not get Last-Modified for $LANG_CODE."
        exit 1
    else
        TIMESTAMP=$(date -d "$LAST_MOD" +"%Y%m%d-%H%M%S" 2>/dev/null || date -jf "%a, %d %b %Y %T %Z" "$LAST_MOD" +"%Y%m%d-%H%M%S" 2>/dev/null)
        if [ -z "$TIMESTAMP" ]; then
             echo "Warning: Failed to parse date '$LAST_MOD'."
             exit 1
        fi
    fi

    LANG_DIR="$BASE_DOWNLOAD_DIR/$LANG_CODE/$TIMESTAMP"
    mkdir -p "$LANG_DIR"

    GZ_FILE="$LANG_DIR/$FILENAME"
    JSONL_FILE="${GZ_FILE%.gz}"

    # If the decompressed file exists, we consider this version done
    if [ -f "$JSONL_FILE" ]; then
        echo "$LANG_CODE dump ($TIMESTAMP) already exists and is decompressed. Skipping."
        continue
    fi

    NEW_DUMPS_COUNT=$((NEW_DUMPS_COUNT + 1))

    # Attempt download/resume. curl -C - will automatically handle existing partial files.
    echo "Downloading/Resuming $LANG_CODE dump..."
    curl -L -C - --output "$GZ_FILE" "$URL"

    # Decompress using a temp file in the SAME directory to avoid "No space left on device"
    # if /tmp is a small memory partition.
    echo "Decompressing $LANG_CODE dump..."
    TMP_FILE=$(mktemp --tmpdir="$LANG_DIR" "kaikki_${LANG_CODE}_XXXXXX.jsonl.tmp")

    # Ensure cleanup of temp file if interrupted
    trap 'rm -f "$TMP_FILE"' EXIT

    gunzip -c "$GZ_FILE" > "$TMP_FILE"
    mv "$TMP_FILE" "$JSONL_FILE"
    trap - EXIT
done

if [ "$FORCE" = false ] && [ "$NEW_DUMPS_COUNT" -eq 0 ]; then
    echo ""
    echo "No new dumps found. All latest versions are already processed. Aborting."
    exit 2
fi

echo ""
echo "Step 2: Finding latest dumps..."
PROCESSOR_ARGS="$VERSION $LEARNING_LANGS $THREADS \"$MIRRORS\""

for LANG_CODE in "${!DUMPS[@]}"; do
    # The directory name is our timestamp: YYYYMMDD-HHMMSS
    LATEST_DIR_PATH=$(ls -d "$BASE_DOWNLOAD_DIR/$LANG_CODE"/*/ 2>/dev/null | sort -r | head -n 1)

    if [ -n "$LATEST_DIR_PATH" ]; then
        DUMP_FILE=$(ls "$LATEST_DIR_PATH"*.jsonl 2>/dev/null | head -n 1)
        if [ -n "$DUMP_FILE" ]; then
            # Extract timestamp from directory path
            TIMESTAMP=$(basename "$LATEST_DIR_PATH")
            PROCESSOR_ARGS="$PROCESSOR_ARGS $LANG_CODE:$TIMESTAMP:$DUMP_FILE"
        else
            echo "Warning: No .jsonl file found in $LATEST_DIR_PATH"
            exit 1
        fi
    else
        echo "Error: No dump directory found for $LANG_CODE"
        exit 1
    fi
done

echo ""
echo "Step 3: Running KaikkiProcessor..."
cd "$PROJECT_ROOT"
./gradlew :processor:run --args="$PROCESSOR_ARGS"

echo ""
echo "All dumps processed. Metadata and databases are available in processor/out/"
