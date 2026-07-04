#!/usr/bin/env bash
# Initialize/update AzooKey clones and optionally refresh Android dictionary assets.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
CONVERTER="$ROOT/clones/AzooKeyKanaKanjiConverter"
APP="$ROOT/clones/azooKey"

echo "==> Updating AzooKeyKanaKanjiConverter submodules"
cd "$CONVERTER"
git submodule update --init --recursive

echo "==> Updating azooKey submodules"
cd "$APP"
git submodule update --init --recursive

DICT_LOUDS="$CONVERTER/Sources/KanaKanjiConverterModuleWithDefaultDictionary/azooKey_dictionary_storage/Dictionary/louds"
DICT_ROOT="$CONVERTER/Sources/KanaKanjiConverterModuleWithDefaultDictionary/azooKey_dictionary_storage/Dictionary"
EMOJI_ALL="$CONVERTER/Sources/KanaKanjiConverterModuleWithDefaultDictionary/azooKey_emoji_dictionary_storage/EmojiDictionary/emoji_all_E17.0.txt"
EMOJI_DICT="$CONVERTER/Sources/KanaKanjiConverterModuleWithDefaultDictionary/azooKey_emoji_dictionary_storage/EmojiDictionary/emoji_dict_E17.0.txt"

if [[ "${SYNC_ASSETS:-0}" == "1" ]]; then
  echo "==> Copying LOUDS assets"
  ./gradlew -p "$ROOT/app" copyAzooKeyLoudsAssets \
    -PazooKeyLoudsSourceDir="$DICT_LOUDS"

  echo "==> Copying connection-cost assets"
  ./gradlew -p "$ROOT/app" copyAzooKeyConnectionAssets \
    -PazooKeyDictionarySourceDir="$DICT_ROOT"

  if [[ -f "$EMOJI_ALL" ]]; then
    mkdir -p "$ROOT/app/src/main/assets/azookey/emoji"
    cp "$EMOJI_ALL" "$ROOT/app/src/main/assets/azookey/emoji/"
    cp "$EMOJI_DICT" "$ROOT/app/src/main/assets/azookey/emoji/" 2>/dev/null || true
  fi

  echo "==> Verifying assets"
  ./gradlew -p "$ROOT/app" verifyAzooKeyLoudsAssets generateAzooKeyLoudsManifest
fi

cat > "$ROOT/clones/VERSIONS.txt" <<EOF
AzooKeyKanaKanjiConverter=$(cd "$CONVERTER" && git rev-parse --short HEAD)
azooKey=$(cd "$APP" && git rev-parse --short HEAD)
dictionary_storage=$(cd "$CONVERTER/Sources/KanaKanjiConverterModuleWithDefaultDictionary/azooKey_dictionary_storage" 2>/dev/null && git rev-parse --short HEAD || echo missing)
emoji_storage=$(cd "$CONVERTER/Sources/KanaKanjiConverterModuleWithDefaultDictionary/azooKey_emoji_dictionary_storage" 2>/dev/null && git rev-parse --short HEAD || echo missing)
EOF

echo "Wrote $ROOT/clones/VERSIONS.txt"
cat "$ROOT/clones/VERSIONS.txt"
