#!/usr/bin/env bash
# Swift testMustCases 相当: 1文字ずつ direct 入力して Kotlin golden と突き合わせる JSON を生成する。
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
CLONE="$ROOT/clones/AzooKeyKanaKanjiConverter"
EVAL_DIR="$ROOT/scripts/swift-sequential-eval"
INPUT="${1:-$ROOT/build/azookey-sequential-eval-input.json}"
OUT="${2:-$ROOT/build/azookey-swift-sequential-output.json}"

if [[ "$(uname -s)" != "Darwin" ]]; then
  echo "Swift sequential parity requires macOS" >&2
  exit 2
fi

if [[ ! -d "$CLONE" ]]; then
  echo "Missing clone at $CLONE" >&2
  exit 2
fi

mkdir -p "$(dirname "$INPUT")" "$(dirname "$OUT")"

python3 - <<PY "$INPUT"
import json, sys
out = sys.argv[1]
items = [
    {"query": "にほん", "typo": False, "roman2kana": False},
    {"query": "しかい", "typo": False, "roman2kana": False},
    {"query": "2000えん", "typo": False, "roman2kana": False},
    {"query": "2000エン", "typo": False, "roman2kana": False},
    {"query": "けいさん", "typo": False, "roman2kana": False},
    {"query": "つかっている", "typo": False, "roman2kana": False},
    {"query": "しんだどうぶつ", "typo": False, "roman2kana": False},
    {"query": "azooKeyをつかう", "typo": False, "roman2kana": False},
    {"query": "じどうAIそうじゅう。", "typo": False, "roman2kana": False},
    {"query": "1234567890123456789012", "typo": False, "roman2kana": False},
    {"query": "tukatteiru", "typo": False, "roman2kana": True},
    {"query": "sindadoubutu", "typo": False, "roman2kana": True},
    {"query": "keisann", "typo": False, "roman2kana": True},
    {"query": "たいかくせい", "typo": True, "roman2kana": False},
    {"query": "きみのことかすき", "typo": True, "roman2kana": False},
    {"query": "おへんとうをもつていく", "typo": True, "roman2kana": False},
]
json.dump(items, open(out, "w"), ensure_ascii=False, indent=2)
PY

cd "$EVAL_DIR"
swift run -c release swift-sequential-eval "$INPUT" > "$OUT"
echo "Wrote $OUT"
