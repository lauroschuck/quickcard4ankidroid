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
# Use absolute path for the download directory to prevent issues with Gradle's working directory
DOWNLOAD_DIR="$(pwd)/kaikki_downloads"

# Ensure download directory exists
mkdir -p "$DOWNLOAD_DIR"

echo "Step 1: Downloading all dumps..."
for LANG_CODE in "${!DUMPS[@]}"; do
    URL="${DUMPS[$LANG_CODE]}"
    FILENAME=$(basename "$URL")
    echo "Downloading $LANG_CODE dump..."
    # -L follows redirects, -C - resumes download
    curl -L -C - --output "$DOWNLOAD_DIR/$FILENAME" "$URL"
done

echo ""
echo "Step 2: Decompressing all dumps..."
for LANG_CODE in "${!DUMPS[@]}"; do
    URL="${DUMPS[$LANG_CODE]}"
    FILENAME=$(basename "$URL")
    DUMP_GZ="$DOWNLOAD_DIR/$FILENAME"
    echo "Decompressing $DUMP_GZ..."
    # -f overwrites, -k keeps the original .gz
    gunzip -f -k "$DUMP_GZ"
done

echo ""
echo "Step 3: Running KaikkiProcessor for all dumps..."
# Build the arguments string starting with learning languages and thread count
PROCESSOR_ARGS="$LEARNING_LANGS $THREADS"

for LANG_CODE in "${!DUMPS[@]}"; do
    URL="${DUMPS[$LANG_CODE]}"
    FILENAME=$(basename "$URL")
    # Determine the decompressed filename (stripping .gz)
    DUMP_FILE="$DOWNLOAD_DIR/${FILENAME%.gz}"
    # Append the language code and file path pair
    PROCESSOR_ARGS="$PROCESSOR_ARGS $LANG_CODE:$DUMP_FILE"
done

# Run the processor once with all accumulated pairs
./gradlew :processor:run --args="$PROCESSOR_ARGS"

echo ""
echo "All dumps processed. Databases are available in processor/out/"
