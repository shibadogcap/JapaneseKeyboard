#!/usr/bin/env bash
# AzooKey Swift 参照ハーネス: clones CLI で parity fixture を実行し JSON 出力する。
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
CLONE="$ROOT/clones/AzooKeyKanaKanjiConverter"
FIXTURE="${1:-$ROOT/app/src/test/resources/azookey_parity_fixtures.json}"
OUT="${2:-$ROOT/build/azookey-swift-parity-output.json}"

if [[ "$(uname -s)" != "Darwin" ]]; then
  echo "Swift parity harness requires macOS" >&2
  exit 2
fi

if [[ ! -d "$CLONE" ]]; then
  echo "Missing clone at $CLONE" >&2
  exit 2
fi

mkdir -p "$(dirname "$OUT")"

cd "$CLONE"

# EvaluateCommand 互換 JSON を生成
python3 - <<'PY' "$FIXTURE" "$ROOT/build/azookey-swift-eval-input.json"
import json, sys
fixture_path, out_path = sys.argv[1:3]
with open(fixture_path, encoding="utf-8") as f:
    fixtures = json.load(f)
items = []
for fx in fixtures:
    if "input" not in fx:
        continue
    answers = []
    if "top5" in fx:
        answers = fx["top5"]
    elif "top1" in fx:
        answers = [fx["top1"]]
    items.append({"query": fx["input"], "answer": answers, "tag": [fx.get("id", "")]})
with open(out_path, "w", encoding="utf-8") as f:
    json.dump(items, f, ensure_ascii=False, indent=2)
PY

swift run -c release CliTool evaluate "$ROOT/build/azookey-swift-eval-input.json" \
  --output "$OUT" \
  --stable \
  --config_n_best 10 \
  --prediction_mode disabled \
  --top_n 5

echo "Wrote $OUT"
