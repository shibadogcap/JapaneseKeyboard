# AzooKey 変換 API 境界（現行）

最終更新: 2026-07-04

## 概要

Android 版の変換パイプラインは AzooKey 本家と同様、単一の `KanaKanjiConverter` API を中心に構成される。

```text
IMEService
  └─ ImeSuggestionOrchestrator
        └─ ImeCandidateCoordinator
              └─ KanaKanjiConverter (DefaultKanaKanjiConverter)
                    └─ AzooKeyKanaKanjiConverterEngine
                          ├─ AzooKeyKana2Kanji (lattice / Viterbi)
                          └─ AzooKeyDicdataFacade (LOUDS + cb/mm)
```

旧来の `CandidateService` → `CandidateAssembler` → 複数 `CandidateSourceProvider` 構成は削除済み。

## ConversionResult レーン

| レーン | 用途 |
|--------|------|
| `mainResults` | 変換候補欄・ライブ変換の本体 |
| `predictionResults` | 日本語予測（manualMix 時は分離） |
| `englishPredictionResults` | 英語予測 |
| `firstClauseResults` | 第一文節候補 |
| `supplementaryCandidates` | 絵文字・記号などライブ変換対象外 |

IME 表示は `displayCandidates()`（main + supplementary）を使用し、ライブ変換は `liveConversionCandidates()`（main のみ）を使用する。

## セッション

- IME 側: `ConversionSession`（composing / commit / Zenz cache）
- Engine 側: `AzooKeyKanaKanjiConverterEngine.sessions`（lattice incremental state）

`KanaKanjiConverter.stopComposition()` で両者を同期する。確定後は `keepCompletedData=true` で文節 warm-start 用 `completedData` を保持する。

## 参照 clone

```bash
./scripts/sync-azookey-clones.sh          # submodule 初期化
SYNC_ASSETS=1 ./scripts/sync-azookey-clones.sh  # assets 同期
```

- エンジン: `clones/AzooKeyKanaKanjiConverter`
- UI 参照: `clones/azooKey`
- バージョン: `clones/VERSIONS.txt`

## Parity テスト

- Kotlin golden: `AzooKeyParityGoldenTest`, `AzooKeySwiftKotlinParityTest`
- Swift 参照 (macOS): `scripts/azookey-swift-parity.sh`
- Fixtures: `app/src/test/resources/azookey_parity_fixtures.json`
