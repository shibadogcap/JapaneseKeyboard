# AzooKey 互換変換 API（Kotlin）

最終更新: 2026-06-05

参照: [AzooKeyKanaKanjiConverter](https://github.com/azooKey/AzooKeyKanaKanjiConverter) / [azooKey](https://github.com/azooKey/azooKey)

## 目標

変換を **API の状態** として扱う。IME / UI は文字列を直接 engine に渡さず、本家と同型の型でリクエストする。

```kotlin
val result = kanaKanjiConverter.requestCandidates(
    input = ComposingText.fromConvertTarget("しかい"),
    options = ConvertRequestOptions(...),
    runtime = ConvertRuntimeContext(...),
    environment = imeEnvironment,
)
```

## 公開 API（`converter.api`）

| 本家 (Swift) | Kotlin |
|--------------|--------|
| `ComposingText` | `ComposingText` |
| `ConvertRequestOptions` | `ConvertRequestOptions` |
| `ConversionResult` | `ConversionResult` (= `AzooKeyStyleConversionResult`) |
| `KanaKanjiConverter.requestCandidates` | `KanaKanjiConverter.requestCandidates` |
| `KanaKanjiConverter.requestPostCompositionPredictionCandidates` | `KanaKanjiConverter.requestPostCompositionPredictionCandidates` |
| — | `KanaKanjiConverter.requestEnglishKanaCandidates` |
| — | `ConvertRuntimeContext` |
| `ComposingText.insert` / `delete` / `moveCursor` | `ComposingTextEditor.kt`（Direct + Roman2Kana 末尾 1 文字 append） |
| `ConversionSessionState` | `ConversionSession`（composing / 確定語 / Zenz LRU） |

実装: `DefaultKanaKanjiConverter`（Hilt `@Singleton`）

内部ブリッジ: `CandidateRequestBridge` → `CandidateRequest` / `CandidateService`

## IME 接続

```
onStartInput
  └─ ImePreferencesSnapshot.from(...) → cachedPreferences

IMEService.buildImeCandidatePreferences()
  └─ ImeCandidatePreferencesBuilder(snapshot, runtime)

ImeCandidateCoordinator.suggest()
  └─ KanaKanjiConverter.requestCandidatesPostProcessed()
```

確定後:

```
schedulePostCommitPrediction(Candidate with yomi)
  └─ PostCommitPredictionFacade.predict()
       ├─ Room 学習遷移
       ├─ LOUDS prefix（確定語の **yomi** → 本家 totalRuby）
       ├─ LOUDS zero-hint（yomi があるときのみ）
       └─ emoji（surface + yomi）
```

## 本家との差分（意図的 / 暫定）

| 項目 | 状態 |
|------|------|
| Lattice + DicdataStore | **本線**: `AzooKeyLatticePrimary`（LOUDS + memory + cb + mm + typo） |
| 学習 memory | lattice 内競合。確定書き込みは `AzooKeyLearningMemoryRepository` |
| 予測 lane | `systemPrediction` のみ。学習語は `memory` lane（private 時は `CandidateAssembler` が gate） |
| `ComposingText` Roman2Kana | `AzooKeyRoman2KanaTransducer` + `insertRoman2KanaAtCursor` |
| `ConversionSession` | `recordConversion` / `recordCommit` / Zenz rerank LRU / lattice node cache |
| Zenzai ALT | Kotlin 側パース済み。native は将来 `PASS\|ALT:...` 拡張可 |
| DictionaryMock fixture | `AzooKeyDictionaryMockTopNComparisonTest`（lattice / LOUDS top-3） |

互換優先のため段階的に本家へ寄せる。**破壊的変更可**。

## テスト

```bash
./gradlew :app:testFullStandardDebugUnitTest \
  --tests 'com.kazumaproject.markdownhelperkeyboard.converter.api.*' \
  --tests 'com.kazumaproject.markdownhelperkeyboard.converter.candidate.*' \
  --tests 'com.kazumaproject.markdownhelperkeyboard.converter.lattice.*' \
  --tests 'com.kazumaproject.markdownhelperkeyboard.ime_service.candidate.*' \
  --tests 'com.kazumaproject.markdownhelperkeyboard.converter.zenz.*'
```

## 辞書・学習・Zenz

| 領域 | Kotlin 境界 |
|------|-------------|
| 学習 memory | `AzooKeyLearningMemoryRepository` / lattice `searchMemory` |
| LearningType | `OnlyOutput` = 変換中読み取りのみ。書き込みは確定経路 |
| PValue | `Candidate.value` / `AzooKeyLearningMemoryValue` |
| Zenz | `ZenzConversionService` + `ImeCandidateCoordinator` |
| Zenzai | `ZenzaiAlternativeConstraintPolicy`（`ALT:ratio:prefix`） |

## 次の作業（優先順）

1. ~~**QWERTY Roman2Kana**~~ → **済**: `ImeComposingTextSession` がキー入力ごとに `ComposingText` を保持。`ImeSuggestionOrchestrator` が `processInputString` / 候補リクエストで同期。
2. ~~**lattice-first で engine system 候補の二重化を抑止**~~ → **済**: `LatticePrimary` / `LoudsPrimary` は `EngineSystemDictionarySourceProvider.warmBunsetsuMetadataOnly`（文節のみ engine）。
3. ~~**IMEService 候補経路の第一段階抽出**~~ → **接続済**: `ImeSuggestionOrchestrator` + `ImeZenzContextBuilder`（`composingTextForCandidateRequest` で composing 同期）。Zenz 左文脈解決は IME 内。
4. ~~**post-commit 次語条件の本家精密化**~~ → **済**: LOUDS 遷移は yomi、zero-hint は yomi gate、`shouldUseLearnedTransitions`
5. ~~**学習 LOUDS の差分更新**~~ → **済**: 確定ごと `appendPersistedEntries`（Room 全件読み出しなし + loudstxt3 shard 追記）
6. ~~**ComposingText のキー単位差分更新**~~ → **済**: `ImeComposingTextSession` + `appendRoman2KanaCharAtEnd`（`かkai` 誤分割を防止）

関連: [azookey-candidate-service-boundary.md](azookey-candidate-service-boundary.md), [azookey-gboard-roadmap.md](azookey-gboard-roadmap.md)