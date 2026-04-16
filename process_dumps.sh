#!/bin/bash
set -e

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

LEARNING_LANGS="af,am,ar,az,be,bg,bn,bs,ca,cs,cy,da,de,el,en,es,et,eu,fa,fi,fr,fy,ga,gd,gl,gn,gu,ha,he,hi,hr,hu,hy,id,ig,is,it,iw,ja,ka,km,kn,ko,ky,lb,ln,lo,lt,lv,mk,ml,mn,mr,ms,mt,my,nb,ne,nl,no,or,pa,pl,pt,ro,ru,sk,sl,so,sq,sr,sv,sw,ta,te,tg,th,tl,tr,uk,ur,uz,vi,zh,zu"
THREADS=4
BASE_DOWNLOAD_DIR="$(pwd)/processor/downloads"

# Ensure base download directory exists
mkdir -p "$BASE_DOWNLOAD_DIR"

echo "Step 1: Downloading and decompressing new dumps..."
for LANG_CODE in "${!DUMPS[@]}"; do
    URL="${DUMPS[$LANG_CODE]}"
    FILENAME=$(basename "$URL")

    echo "Checking $LANG_CODE dump at $URL..."

    # Get Last-Modified header. -L follows redirects, -I gets headers only.
    LAST_MOD=$(curl -sIL "$URL" | grep -i '^last-modified:' | tail -n 1 | cut -d' ' -f2- | tr -d '\r')

    if [ -z "$LAST_MOD" ]; then
        echo "Warning: Could not get Last-Modified for $LANG_CODE. Using current date for directory."
        TIMESTAMP=$(date +"%Y%m%d-%H%M%S")
    else
        TIMESTAMP=$(date -d "$LAST_MOD" +"%Y%m%d-%H%M%S" 2>/dev/null || date -jf "%a, %d %b %Y %T %Z" "$LAST_MOD" +"%Y%m%d-%H%M%S" 2>/dev/null)
        if [ -z "$TIMESTAMP" ]; then
             echo "Warning: Failed to parse date '$LAST_MOD'. Using current date."
             TIMESTAMP=$(date +"%Y%m%d-%H%M%S")
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

    # Reset trap
    trap - EXIT
done

echo ""
echo "Step 2: Finding latest dumps and running KaikkiProcessor..."
PROCESSOR_ARGS="$LEARNING_LANGS $THREADS"

# Iterate again to find the latest directory for each language
for LANG_CODE in "${!DUMPS[@]}"; do
    LATEST_DIR=$(ls -d "$BASE_DOWNLOAD_DIR/$LANG_CODE"/*/ 2>/dev/null | sort -r | head -n 1)

    if [ -n "$LATEST_DIR" ]; then
        DUMP_FILE=$(ls "$LATEST_DIR"*.jsonl 2>/dev/null | head -n 1)
        if [ -n "$DUMP_FILE" ]; then
            PROCESSOR_ARGS="$PROCESSOR_ARGS $LANG_CODE:$DUMP_FILE"
        else
            echo "Warning: No .jsonl file found in $LATEST_DIR"
        fi
    else
        echo "Error: No dump directory found for $LANG_CODE"
    fi
done

echo ""
echo "Step 3: Run KaikkiProcessor..."
./gradlew :processor:run --args="$PROCESSOR_ARGS"

echo ""
echo "All dumps processed. Databases are available in processor/out/"
