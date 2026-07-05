# AzooKey 変換候補ランキングアルゴリズム仕様書

最終更新: 2026-06-13

参照元:
- `clones/azooKey/Keyboard/Display/InputManager.swift` — `setResult()`, `getConvertRequestOptions()`
- `clones/AzooKeyKanaKanjiConverter/Sources/KanaKanjiConverterModule/ConverterAPI/KanaKanjiConverter.swift` — `processResult()`
- `clones/AzooKeyKanaKanjiConverter/Sources/KanaKanjiConverterModule/ConverterAPI/ConversionResult.swift` — `ConversionResult`
- `clones/AzooKeyKanaKanjiConverter/Sources/KanaKanjiConverterModule/ConverterAPI/Candidate.swift` — `Candidate`, `ComposingCount`
- `clones/AzooKeyKanaKanjiConverter/Sources/KanaKanjiConverterModule/ConversionAlgorithms/Kana2Kanji.swift` — `processClauseCandidate()`
- `clones/azooKey/Keyboard/Display/LiveConversionManager.swift`

## 1. 概要

`processResult(inputData:result:options:)` は、ラティス（`LatticeNode`）から変換候補を生成し、ランキング・重複除去・昇格処理を行って最終的な `ConversionResult` を組み立てる関数である。この関数は `KanaKanjiConverter.requestCandidates()` の内部で呼ばれ、azooKey の変換候補表示を完全に決定する。

`ConversionResult` は以下の4つの配列を持つ:

```swift
public struct ConversionResult: Sendable {
    public var mainResults: [Candidate]        // 変換候補欄にこのままの順で並べることのできる候補
    public var predictionResults: [Candidate]  // 入力中の予測変換候補（日本語）
    public var englishPredictionResults: [Candidate] = []  // 入力中の英語予測変換候補
    public var firstClauseResults: [Candidate] // 最初の文節候補
}
```

mainResults の中には「全体文候補（best-5）」「予測変換候補（top-3、mix時のみ）」「英単語候補（mix時のみ）」「トップレベル追加候補」「ユーザショートカット」「第一文節候補」「単語候補」が全て連結される。

### 全体的な処理フロー

```text
入力: ComposingText
  │
  ▼
convertToLattice(inputData, N_best=10)
  │
  ▼
processResult()
  ├─ (1) 文節候補(CandidateData[]) を取得 ───→ 全体文候補生成 ───→ best-5選択
  ├─ (2) ユーザショートカット抽出
  ├─ (3) 予測変換候補生成 (top-3)
  ├─ (4) 英単語予測候補生成
  ├─ (5) トップレベル追加候補生成
  ├─ (6) 混合 → 重複除去 → top-5
  ├─ (7) 完全一致読み昇格 (3位以内保証)
  ├─ (8) 第一文節候補追加
  ├─ (9) 単語候補 (wordCandidates) 追加
  └─ (10) actions適用 & テンプレート展開
  │
  ▼
出力: ConversionResult(mainResults, predictionResults, englishPredictionResults, firstClauseResults)
```

## 2. リクエストオプション

`InputManager.getConvertRequestOptions()` で生成される `ConvertRequestOptions` の主要パラメータ:

| パラメータ | 値 | 説明 |
|-----------|-----|------|
| `N_best` | `10` | ラティスのビーム幅。最終的な候補数とは異なる |
| `requireJapanesePrediction` | `.autoMix` | 日本語予測変換: 有効 + 混合 |
| `requireEnglishPrediction` | `.autoMix` | 英語予測変換: 有効 + 混合 |
| `fullWidthRomanCandidate` | `true` | 全角英数字候補を追加 |
| `halfWidthKanaCandidate` | `true` | 半角カナ候補を追加 |
| `zenzaiMode` | 設定依存 | Zenzai（ニューラル変換）が有効か |
| `learningType` | 設定依存 | 学習モード |
| `specialCandidateProviders` | カレンダー, カンマ区切り数値, メール, 時刻表現, Unicode, バージョン(, 英文タイポグラフィ) | 特殊変換プロバイダ |

### requireJapanesePrediction / requireEnglishPrediction の振る舞い

- `.autoMix`: 予測変換を**生成し、mainResults に混合する**。
- `.manualMix`: 予測変換を生成するが、mainResults には混合せず、`predictionResults` / `englishPredictionResults` として分離する。
- `.disabled`: 予測変換を生成しない。

## 3. ConversionResult の 4 つのレーン

### 3.1 mainResults

UI の変換候補欄（`ResultModel.results`）にそのまま表示される候補のリスト。以下の要素がこの順で連結される:

```
mainResults = fullCandidates (best-5 混合, 最大5件)
            + firstClauseCandidates (重複除去後, 最大5件)
            + wordCandidates (重複除去後, 無制限)
```

### 3.2 predictionResults

入力中の予測変換候補（日本語）。`requireJapanesePrediction` が `.autoMix` または `.manualMix` の場合に生成される。`.autoMix` の場合、上位3件が mainResults にも混合される。

### 3.3 englishPredictionResults

入力中の英語予測変換候補。`requireEnglishPrediction` が `.autoMix` または `.manualMix` の場合に生成される。`.autoMix` の場合、全件が mainResults にも混合される。

### 3.4 firstClauseResults

最初の文節のみの変換候補。mainResults とは別に保持され、UI の「文節区切り」表示などに使われる。

## 4. 全体文候補 (Whole Sentence Candidates)

### 4.1 生成

```swift
let clauseResult = result.result.getCandidateData()  // CandidateData[] (文節区切りの全候補)
let wholeSentenceUniqueCandidates = getUniqueCandidate(
    clauseResult.lazy.map { converter.processClauseCandidate($0) }
)
```

`processClauseCandidate` は各文節の連接コスト（意味連接 MMValue）を考慮して、1つの `Candidate`（全文）にまとめる:

```swift
func processClauseCandidate(_ data: CandidateData) -> Candidate {
    let mmValue = data.clauses.reduce { $0 + dicdataStore.getMMValue($1.clause.mid) }
    let text = data.clauses.map { $0.clause.text }.joined()
    let value = data.clauses.last!.value + mmValue.value  // 最終文節の値 + MM連接値
    // ...
    return Candidate(text: text, value: value, ...)
}
```

### 4.2 最良文節データの保存

予測変換生成のために、値が最大の文節データが `bestCandidateDataForPrediction` として保存される:

```swift
bestCandidateDataForPrediction = zip(clauseResult, clauseResultCandidates)
    .max { $0.1.value < $1.1.value }!.0
```

### 4.3 best-5 選択

Zenzai の有無で戦略が変わる:

**Zenzai 無効時:**
```swift
bestFiveSentenceCandidates = wholeSentenceUniqueCandidates
    .min(count: 5, sortedBy: { $0.value > $1.value })
```
単純に value 降順で上位5件。

**Zenzai 有効時:**
```swift
var first5 = Array(wholeSentenceUniqueCandidates.prefix(5))
let values = first5.map(\.value).sorted(by: >)
for (i, v) in zip(first5.indices, values) {
    first5[i].value = v  // Zenzai 出力順を維持しつつ value は元の降順に再設定
}
bestFiveSentenceCandidates = first5
```
Zenzai によって並び替えられた候補から上位5件をそのまま採用する。ただし `value` プロパティは元の降順値で上書きする（後続の重複除去ロジックと互換性を保つため）。

## 5. ユーザショートカット

入力全体の読み（`inputData.convertTarget` をカタカナに変換したもの）に完全一致するユーザショートカットエントリを辞書から取得する:

```swift
let ruby = inputData.convertTarget.toKatakana()
let dicdata = converter.dicdataStore.getPerfectMatchedUserShortcutsDicdata(ruby: ruby, ...)
userShortcutsCandidates = dicdata.map { Candidate(text: $0.word, value: $0.value(), ...) }
```

## 6. 予測変換候補 (Prediction Results)

### 6.1 生成

`bestCandidateDataForPrediction`（値最大の文節データ）から、最大2回の文節取り出しと、入力全体を使った予測を行う:

```swift
// 最終文節から順に分解して予測
// 1回目: lastPart = 最終文節の ClauseData, prepart = それより前の文節
// 2回目: prepart からさらに1文節を lastPart にマージして再予測
// 最終: 全文節をマージした fullClause で予測
```

各ステップで `converter.getPredictionCandidates(..., N_best: 5)` を呼び、LOUDS 辞書からの接続候補を取得する。各候補には連接コスト（CC値 + MM値）が加算される。

### 6.2 Stable Prediction Cache

```swift
stablePredictionCandidates = self.stablePredictionCandidates(...)  // キャッシュから
```

前回の確定情報に基づく「安定した予測候補」がキャッシュされている場合、それを取得する。

### 6.3 最終的な predictionResults

```swift
predictionResults = mergeStableCandidates(
    stableCandidates: stablePredictionCandidates,
    otherCandidates: candidates,  // 通常の予測候補 (top-3 by value)
    limit: 3
)
```

`mergeStableCandidates` のロジック:
1. `stableCandidates` を重複除去（`getUniqueCandidate`）
2. stable の件数が `limit` (3) 以上 → stable のみ返す
3. stable 不足分を `otherCandidates`（stable と重複しないもの）から value 降順で補充

### 6.4 autoMix 時の流用

`requireJapanesePrediction.shouldMix == true`（`.autoMix`）の場合、`predictionResults` 全体が `bestThreePredictionCandidates` として mainResults の混合に使われる。false（`.manualMix`）の場合は `[]` となる。

## 7. 英語予測変換候補 (English Prediction Results)

### 7.1 生成

`getForeignPredictionCandidate(inputData:language:)` で生成される:

- 入力が ASCII 文字のみの場合、`UITextChecker`（Apple のスペルチェッカー）を使って英語補完候補を取得
- 基本値: `-5 + penalty`（`penalty` のデフォルトは `-5` なので、最初の候補は `-10`）
- その後 `-10 / count` ずつ value が減少
- 未変換の入力文字自体も `value: -5` で候補に含める

### 7.2 autoMix 時の流用

`requireEnglishPrediction.shouldMix == true`（`.autoMix`）の場合、`englishPredictionResults` が `foreignCandidates` として mainResults の混合に使われる。

## 8. 追加候補 (Additional & Top-Level)

### 8.1 getAdditionalCandidate

入力文字列から生成するフォールバック候補:

| 候補 | value | 条件 |
|------|-------|------|
| カタカナ (例: 「シカイ」) | `-14 * katakanaScore` | 常に生成 |
| ひらがな (例: 「しかい」) | `-14.5` | 常に生成 |
| 大文字 (例: 「シカイ」→ 意味なし, 英字入力時有用) | `-14.6` | 常に生成 |
| 全角英数字 | `-14.7` | `fullWidthRomanCandidate == true` |
| 半角カナ (例: 「ｼｶｲ」) | `-15` | `halfWidthKanaCandidate == true` |

`katakanaScore` はカタカナ文字の出現パターンに基づく補正値（小さいほどカタカナ語らしい）:

| 文字 | スコア倍率 |
|------|-----------|
| プヴペィフ | `×0.5` |
| ュピポ | `×0.6` |
| パォグーム | `×0.7` |

例: 「シカイ」には該当文字がないので `score = 1.0`、value = `-14.0`。

### 8.2 getTopLevelAdditionalCandidate

`englishCandidateInRoman2KanaInput` が有効かつ入力が ASCII のみの場合、英語予測候補を `penalty: -10` で生成する。

## 9. 混合 (Mixing) — 最重要ランキングステップ

### 9.1 混合プール

```swift
let mixedCandidates = getUniqueCandidate(
    bestFiveSentenceCandidates          // 全体文上位5件
        .chained(bestThreePredictionCandidates)  // 予測上位3件 (autoMix時のみ有効)
        .chained(foreignCandidates)              // 英単語候補 (autoMix時のみ有効)
        .chained(topLevelAdditionalCandidates)   // トップレベル追加候補
        .chained(userShortcutsCandidates)        // ユーザショートカット
)
```

`chained` は遅延結合（新しい配列を確保しない）。`getUniqueCandidate` で全体を重複除去する。

### 9.2 最終 top-5 決定

**autoMix 時:**
```swift
fullCandidates = mergeStableCandidates(
    stableCandidates: stablePredictionCandidates,  // 安定予測キャッシュ
    otherCandidates: mixedCandidates,
    limit: 5
)
```

**非 autoMix 時:**
```swift
fullCandidates = mixedCandidates.min(count: 5, sortedBy: { $0.value > $1.value })
```

### 9.3 優先順位の実質的な決定

`getUniqueCandidate` がテキストキーで重複除去を行うため、chain の**先に現れた候補が優先されるわけではない**。同一テキストの場合、value が高い（または同じ value なら rubyCount が長い）方が残る。

結果として、各ソース間の競合は value 値で解決される:

| ソース | 典型的な value レンジ | 優先度 |
|--------|---------------------|--------|
| 全体文候補 (best-5) | `-5 〜 -20`（高い） | 高い |
| 予測変換候補 (top-3) | `-10 〜 -30` | 中 |
| 英単語候補 | `-10 〜 -20` | 中 |
| トップレベル追加候補 | `-10`（英予測） | 中 |
| ユーザショートカット | ユーザ定義値 | 可変 |
| 安定予測キャッシュ | 前回確定由来 | 高い |

## 10. 完全一致読み昇格 (Exact-Reading Promotion)

入力の読みに完全一致する候補が先頭3位以内に存在しない場合、3位以内に昇格させる:

```swift
// 判定: 各候補の data の ruby 連結が input のカタカナ変換と一致するか
let checkRuby: (Candidate) -> Bool = {
    $0.data.reduce(into: "") { $0 += $1.ruby } == inputData.convertTarget.toKatakana()
}

if !result.prefix(3).contains(where: checkRuby) {
    // 1. 4位以降に存在する場合 → 2位に昇格
    if let candidateIndex = result.dropFirst(3).firstIndex(where: checkRuby) {
        let candidate = result.remove(at: candidateIndex)
        result.insert(candidate, at: min(result.endIndex, 2))
    }
    // 2. bestFiveSentenceCandidates にあればそれを2位に挿入
    else if let candidate = bestFiveSentenceCandidates.first(where: checkRuby) {
        result.insert(candidate, at: min(result.endIndex, 2))
    }
    // 3. wholeSentenceUniqueCandidates にあればそれを2位に挿入
    else if let candidate = wholeSentenceUniqueCandidates.first(where: checkRuby) {
        result.insert(candidate, at: min(result.endIndex, 2))
    }
}
```

**重要:** `insert(at: min(endIndex, 2))` のため、結果が2件未満の場合は末尾に追加される。通常は5件あるので、2位（0-indexed: 2、つまり3番目）に挿入される。

この昇格により、最も基本的な変換結果（例: 入力「しかい」→ 候補「司会」）が常に表示上位に来ることが保証される。

### 10.1 なぜ「3位以内」か

日本語入力では、ユーザが期待する基本的な変換結果が上位に出ないと UX が大幅に低下する。Zenzai のニューラル変換が予想外の候補を上位に出す場合でも、最低1つの「正しい読みの候補」を上位3位以内に保証するためのセーフガードである。

## 11. 第一文節候補 (First Clause Results)

### 11.1 生成

```swift
let uniqueFirstClauseCandidates = getUniqueCandidate(clauseResult.lazy.map { candidateData in
    let first = candidateData.clauses.first!
    let count = max(0, first.clause.dataEndIndex)
    return Candidate(
        text: first.clause.text,
        value: first.value,
        composingCount: first.clause.ranges.reduce(into: .inputCount(0)) { $0 = .composite($0, $1.count) },
        lastMid: first.clause.mid,
        data: Array(candidateData.data[0...count])
    )
})
```

### 11.2 ソート

```swift
firstClauseResults = uniqueFirstClauseCandidates.min(count: 5) {
    if $0.rubyCount == $1.rubyCount {
        $0.value > $1.value       // 同じ長さなら value 降順
    } else {
        $0.rubyCount > $1.rubyCount  // 長い読みほど優先 (長い文節ほど先に)
    }
}
```

### 11.3 重複除去 (対 fullCandidates)

```swift
let firstClauseCandidates = getUniqueCandidate(
    uniqueFirstClauseCandidates,
    seenCandidates: seenCandidate  // fullCandidates の text セット
).min(count: 5) { ... }
```

既に fullCandidates に含まれているテキストを持つ第一文節候補は除外される。

## 12. 単語候補 (Word Candidates)

### 12.1 生成元

```swift
// ラティスの最初の位置 (inputIndex=0) の全ノード
let dicCandidates: [Candidate] = result.lattice[index: .bothIndex(inputIndex: 0, surfaceIndex: 0)]
    .map { Candidate(text: $0.data.word, value: $0.data.value(), composingCount: $0.range.count, ...) }

// 追加候補 (カタカナ, ひらがな, 大文字, 全角英数字, 半角カナ)
let additionalCandidates = getAdditionalCandidate(inputData, options: options)
```

### 12.2 ソート

```swift
var candidates = getUniqueCandidate(dicCandidates + additionalCandidates, seenCandidates: seenCandidate)
    .sorted {
        let count0 = $0.rubyCount
        let count1 = $1.rubyCount
        return count0 == count1 ? $0.value > $1.value : count0 > count1
    }
```

1. 読みの長さ（rubyCount）降順（長い単語ほど先）
2. 同じ長さなら value 降順

### 12.3 特殊候補の挿入

```swift
let wiseCandidates = getUniqueCandidate(
    getSpecialCandidate(inputData, options: options),  // カレンダー, メール, Unicodeなど
    seenCandidates: seenCandidate
)
candidates.insert(contentsOf: wiseCandidates, at: min(5, candidates.endIndex))
```

特殊候補（カレンダー変換、メールアドレス、Unicode 入力、バージョン情報など）は単語候補の先頭（5件目以内）に割り込む。

## 13. 重複除去 (Deduplication)

`getUniqueCandidate` 関数は以下のロジックで重複を除去する:

```swift
private func getUniqueCandidate(_ candidates: some Sequence<Candidate>, seenCandidates: Set<String> = []) -> [Candidate] {
    var result = [Candidate]()
    var textIndex = [String: Int]()  // text → result index

    for candidate in candidates where !candidate.text.isEmpty && !seenCandidates.contains(candidate.text) {
        if let index = textIndex[candidate.text] {
            // 既存: value が高い方、または同値なら rubyCount が長い方を残す
            if result[index].value < candidate.value || result[index].rubyCount < candidate.rubyCount {
                result[index] = candidate
            }
        } else {
            textIndex[candidate.text] = result.endIndex
            result.append(candidate)
        }
    }
    return result
}
```

### キーポイント

- **キーは `candidate.text`（表示テキスト）**。例えば「司会」「司会」は同一と判定される。
- 同一テキストの場合、**value が高い方が優先**される。
- value が同一の場合は **rubyCount が長い方（より多くの読みをカバーする方）が優先**。
- `seenCandidates` に含まれるテキストはスキップされる（後続のフェーズで上位フェーズの候補と重複しないようにするため）。
- 空文字の候補は無視される。

### 各フェーズでの seenCandidates の更新

```text
全文候補 (top-5) → seenCandidates に追加
予測候補 (top-3) → 重複除去時に全文候補と競合
第一文節候補 → seenCandidates (全文候補+第一文節候補) に対して重複除去
単語候補 → seenCandidates (全文候補+第一文節候補+単語候補) に対して重複除去
特殊候補 → seenCandidates (全文候補+第一文節候補+単語候補) に対して重複除去
```

## 14. Zenzai の影響

### 14.1 ラティス生成時の Zenzai

zenzai が有効な場合、`convertToLattice` は `converter.all_zenzai()` を呼び、ニューラル言語モデルによるスコアリングを反映したラティスを生成する。これにより `clauseResult` の候補の順序と値が変化する。

### 14.2 best-5 選択の変更

Zenzai 無効時は `wholeSentenceUniqueCandidates` を value 降順でソートして上位5件を取るが、Zenzai 有効時は Zenzai の出力順（≒ニューラルモデルが評価した確信度順）で上位5件を取る。

```swift
// Zenzai 有効: 出力順を維持
var first5 = Array(wholeSentenceUniqueCandidates.prefix(5))
let values = first5.map(\.value).sorted(by: >)
for (i, v) in zip(first5.indices, values) {
    first5[i].value = v  // value だけ降順に再設定
}
```

### 14.3 推論回数制限

`inferenceLimit` パラメータにより Zenzai の推論回数が制限される:

| effort | inferenceLimit | モデル |
|--------|---------------|--------|
| high | 3 | zenz-v3.1-small (Q5_K_M) |
| medium | 1 | zenz-v3.1-small (Q5_K_M) |
| low | 2 | zenz-v3.1-xsmall (Q5_K_M) |

### 14.4 完全一致クエリ時の特別動作

`options.requestQuery == .完全一致` の場合、ランキングが簡略化される:

```swift
let merged = getUniqueCandidate(wholeSentenceUniqueCandidates + userShortcutsCandidates)
if zenzaiMode.enabled {
    return ConversionResult(mainResults: merged, ...)  // Zenzai の出力順
} else {
    return ConversionResult(mainResults: merged.sorted(by: { $0.value > $1.value }), ...)
}
```

予測変換、第一文節候補、単語候補、完全一致読み昇格はすべてスキップされる。

## 15. 半角・異体字ハンドリング

### 15.1 半角カナ候補

`halfWidthKanaCandidate == true` の場合、`getAdditionalCandidate()` が半角カナ候補を生成する:

```swift
let word = string.applyingTransform(.fullwidthToHalfwidth, reverse: false) ?? ""
let halfWidthKatakana = Candidate(text: word, value: -15, ...)
```

この候補は `wordCandidates` に追加され、seenCandidates で重複除去される。value が `-15` と低いため、通常は単語候補の後半に表示される。

### 15.2 全角英数字候補

`fullWidthRomanCandidate == true` の場合、同様に全角英数字候補が生成される:

```swift
let word = string.applyingTransform(.fullwidthToHalfwidth, reverse: true) ?? ""
let fullWidthLetter = Candidate(text: word, value: -14.7, ...)
```

### 15.3 ひらがな/カタカナ/大文字

常に生成される:

| 候補 | value | 備考 |
|------|-------|------|
| カタカナ | `-14 × katakanaScore` | カタカナ語らしさで補正 |
| ひらがな | `-14.5` | 無変換入力と同じテキスト |
| 大文字 | `-14.6` | 英字入力時のみ有用 |

## 16. 完全な混合ダイアグラム

```text
                        ┌──────────────────────────┐
                        │   clauseResult           │
                        │   (CandidateData[])       │
                        └──────────┬───────────────┘
                                   │
                    ┌──────────────┼──────────────┐
                    ▼              ▼              ▼
         ┌──────────────────┐ ┌──────────┐ ┌──────────────┐
         │processClause     │ │best-value│ │first clause  │
         │Candidate (全文)  │ │(予測用)  │ │extraction    │
         └───────┬──────────┘ └────┬─────┘ └──────┬───────┘
                 ▼                 ▼               ▼
         ┌──────────────┐  ┌──────────────┐ ┌──────────────┐
         │wholeSentence  │  │prediction    │ │firstClause   │
         │unique(重複無) │  │generation    │ │unique + sort │
         └───────┬──────┘  └──────┬───────┘ └──────┬───────┘
                 │                │                 │
         Zenzai? │                ▼                 │
          Yes ───┤        ┌──────────────┐          │
          No ────┤        │stableCache   │          │
                 ▼        │merge(limit:3)│          │
         ┌──────────────┐ └──────┬───────┘          │
         │best-5        │        │                   │
         │(value / order)│        │                   │
         └───────┬──────┘        │                   │
                 │                │                   │
                 ▼                ▼                   │
         ┌────────────────────────────────────┐       │
         │  MIX (getUniqueCandidate)          │       │
         │  best5 + prediction + foreign      │       │
         │  + topLevelAdditional + shortcuts  │       │
         └──────────────┬─────────────────────┘       │
                        │                              │
                        ▼                              │
         ┌──────────────────────────────┐              │
         │ mergeStable(limit:5) / top-5 │              │
         │ = fullCandidates             │              │
         └──────────────┬───────────────┘              │
                        │                              │
                        ▼                              │
         ┌──────────────────────────────┐              │
         │ 完全一致読み昇格             │              │
         │ (checkRuby: 先頭3位以内)     │              │
         └──────────────┬───────────────┘              │
                        │                              │
                        ▼                              ▼
         ┌─────────────────────────────────────────────────┐
         │  result = fullCandidates                       │
         │         + firstClauseCandidates (seen排除,top-5)│
         │         + wordCandidates (seen排除, sort特殊)   │
         └──────────────────────┬──────────────────────────┘
                                ▼
         ┌──────────────────────────────────┐
         │  actions適用 + template展開      │
         │  (括弧類にカーソル移動アクション) │
         └──────────────────────┬───────────┘
                                ▼
         ┌──────────────────────────────────┐
         │  ConversionResult(mainResults,   │
         │    predictionResults,            │
         │    englishPredictionResults,     │
         │    firstClauseResults)           │
         └──────────────────────────────────┘
```

## 17. 具体例: 入力「しかい」

### 17.1 前提

- 入力: `しかい`（ComposingText, 4文字）
- LOUDS 辞書 + N-gram + cb（連接コスト）
- Zenzai 無効
- `requireJapanesePrediction = .autoMix`
- `requireEnglishPrediction = .autoMix`

### 17.2 期待される全体文候補 (wholeSentenceUniqueCandidates の上位)

| 順位 | テキスト | 読み | value (例) | 備考 |
|------|---------|------|-----------|------|
| 1 | 司会 | シカイ | -5.2 | N-gram 高スコア |
| 2 | 死海 | シカイ | -7.8 | 固有名詞 |
| 3 | 市外 | シガイ | -8.5 | 読みが異なる |
| 4 | 鹿威し | シシオドシ | -12.3 | 読みが長い |
| 5 | 四回 | ヨンカイ | -13.0 | 読みが異なる |

### 17.3 best-5 選択

上記上位5件が `bestFiveSentenceCandidates` となる。

### 17.4 予測変換候補

bestCandidate が「司会」の場合、その最終文節から予測:

- 「司会者」(-10.5)
- 「司会進行」(-12.3)
- 「司会業」(-14.0)

### 17.5 混合 (MIX)

```text
mixedCandidates (重複除去後):
  1. 司会 (-5.2)          ← best-5
  2. 死海 (-7.8)          ← best-5
  3. 市外 (-8.5)          ← best-5
  4. 司会者 (-10.5)       ← 予測
  5. 司会進行 (-12.3)     ← 予測
  6. 鹿威し (-12.3)       ← best-5
  7. 四回 (-13.0)         ← best-5
  8. 司会業 (-14.0)       ← 予測
  9. シカイ (-14.0)       ← 追加(カタカナ)
  10. しかい (-14.5)      ← 追加(ひらがな)
```

`mergeStableCandidates(limit: 5)` → **fullCandidates (上位5件)**:

| 順位 | テキスト | value |
|------|---------|-------|
| 1 | 司会 | -5.2 |
| 2 | 死海 | -7.8 |
| 3 | 市外 | -8.5 |
| 4 | 司会者 | -10.5 |
| 5 | 司会進行 | -12.3 |

### 17.6 完全一致読み昇格

`checkRuby`（各候補の ruby 連結が `シカイ` と一致するか）:

- 「司会」→ `シカイ` ✅ → 先頭3位以内に存在 → 昇格不要
- 「死海」→ `シカイ` ✅ → 同様

最初から「司会」が1位なので昇格は発生しない。

### 17.7 第一文節候補

- 「司会」(-5.2)
- 「死海」(-7.8)
- 「市外」(-8.5)
- 「鹿威し」(-12.3)
- 「四回」(-13.0)

seenCandidates != fullCandidates のテキストなので、全て重複として除外される（既に fullCandidates に含まれている）。→ 0件。

### 17.8 単語候補

ラティスの先頭位置から取得した単語:

| テキスト | value | rubyCount |
|---------|-------|-----------|
| 死 (-6.0) | -6.0 | 2 |
| 司 (-8.0) | -8.0 | 2 |
| 市 (-9.0) | -9.0 | 2 |
| 四 (-10.0) | -10.0 | 2 |
| 詩 (-12.0) | -12.0 | 2 |
| シカイ (-14.0) | -14.0 | 4 ← 追加(カタカナ)
| しかい (-14.5) | -14.5 | 4 ← 追加(ひらがな)
| ｼｶｲ (-15.0) | -15.0 | 4 ← 追加(半角カナ)

sort: rubyCount 降順 → value 降順:

| テキスト | value | rubyCount |
|---------|-------|-----------|
| シカイ | -14.0 | 4 |
| しかい | -14.5 | 4 |
| ｼｶｲ | -15.0 | 4 |
| 死 | -6.0 | 2 |
| 司 | -8.0 | 2 |
| 市 | -9.0 | 2 |
| 四 | -10.0 | 2 |
| 詩 | -12.0 | 2 |

特殊候補（カレンダー等）は min(5, 8)=5 の位置に挿入。

### 17.9 最終 mainResults

```text
mainResults = fullCandidates (5件)
            + firstClauseCandidates (0件、重複)
            + wordCandidates (8件)

= [
  0: 司会 (-5.2)
  1: 死海 (-7.8)
  2: 市外 (-8.5)
  3: 司会者 (-10.5)
  4: 司会進行 (-12.3)
  5: シカイ (-14.0)       ← word (rubyCount=4)
  6: しかい (-14.5)       ← word (rubyCount=4)
  7: ｼｶｲ (-15.0)         ← word (rubyCount=4)
  8: 死 (-6.0)             ← word (rubyCount=2)
  9: 司 (-8.0)             ← word (rubyCount=2)
 10: 市 (-9.0)             ← word (rubyCount=2)
 11: 四 (-10.0)            ← word (rubyCount=2)
 12: 詩 (-12.0)            ← word (rubyCount=2)
]
```

## 18. スロット割当まとめ

| カテゴリ | 最大件数 | mainResults 内の位置 | ソート順 |
|----------|---------|---------------------|---------|
| 全体文候補 (best-5) | 5 | 先頭 (0-4) | Zenzai 出力順 / value 降順 |
| 予測変換候補 (autoMix) | 3 | 全体文候補と混合 | value 降順 |
| 英単語候補 (autoMix) | 無制限 | 全体文候補と混合 | value 降順 |
| トップレベル追加 | 無制限 | 全体文候補と混合 | value 降順 |
| ユーザショートカット | 無制限 | 全体文候補と混合 | value 降順 |
| 完全一致読み昇格 | 1 | 2位 (0-indexed) | 強制挿入 |
| 第一文節候補 | 5 | fullCandidates の直後 | rubyCount 降順→value 降順 |
| 単語候補 (辞書) | 無制限 | 第一文節候補の直後 | rubyCount 降順→value 降順 |
| 単語候補 (追加) | 最大5種 | 単語候補内 | rubyCount 降順→value 降順 |
| 特殊候補 (カレンダー等) | 無制限 | 単語候補の 5 番目以降に挿入 | ラティス辞書価格順 |

### UI 表示上の注意

`InputManager.setResult()` では:

```swift
model.setResults(results.mainResults)
model.resetSupplementaryCandidates()
```

`ResultModel` は `results`（mainResults）が空でない限り `results` を表示する。`predictionResults` と `englishPredictionResults` は別レーンとして保持され、`ResultModel.displayState` が `.results` または `.predictions` で切り替わる。

確定後予測（`requestPostCompositionPredictionCandidates`）の結果は別途 `model.setPredictionResults()` で設定される。

## 19. 値 (PValue) の基準

`PValue` は iOS では `Float16`、他プラットフォームでは `Float32`。

| 値 | 意味 | 典型例 |
|----|------|--------|
| `0` 〜 `-5` | 非常に高い（学習語など） | 学習による昇格 |
| `-5` 〜 `-10` | 高い（一般的な変換候補） | 「司会」「死海」 |
| `-10` 〜 `-15` | 中程度 | 予測候補、カタカナ |
| `-15` 〜 `-20` | 低い（フォールバック） | 半角カナ、特殊変換 |
| `< -20` | 非常に低い | 関連性の低い候補 |
| `-18` | Enter 時の未確定文字列 | `enter()` 内のデフォルト値 |

`DicdataStore.threshold = -17` が学習データの閾値として定義されている。

## 20. 実装上の注意点

### 20.1 空入力時の動作

```swift
if inputData.convertTarget.isEmpty {
    return ConversionResult(mainResults: [], predictionResults: [], ...)
}
```

### 20.2 ラティスが空の場合

```swift
if clauseResult.isEmpty {
    let candidates = getUniqueCandidate(getAdditionalCandidate(...))
    return ConversionResult(mainResults: candidates, firstClauseResults: candidates)
}
```

全文候補が1つも得られなかった場合、追加候補（カタカナ・ひらがな等）のみを返す。

### 20.3 template 展開

各候補の `parseTemplate()` は最終段階で呼ばれる:

```swift
result.mutatingForEach { item in
    item.withActions(self.getAppropriateActions(item))  // 括弧類にカーソル移動アクション
    item.parseTemplate()  // <date> などのテンプレートを展開
}
```

`getAppropriateActions` は括弧類（`「」`, `（）` など）に `moveCursor(-1)` アクションを付与する。これにより、括弧を入力した際にカーソルが括弧内に移動する。

### 20.4 確定後予測 (PostCompositionPrediction)

`processResult` とは独立して、確定後に `requestPostCompositionPredictionCandidates()` が呼ばれる:

```swift
// InputManager 内
@MainActor func updatePostCompositionPredictionCandidates(candidate: Candidate) {
    let results = kanaKanjiConverter.requestPostCompositionPredictionCandidates(
        leftSideCandidate: candidate, options: options
    )
    // 絵文字 denylist フィルタリング
    // → predictionManager の状態更新
    // → ResultModel.setPredictionResults() で UI 更新
}
```

この結果は `ResultModel.predictionResults` に格納され、mainResults とは独立した表示状態（`DisplayState.predictions`）で表示される。

---

## 付録A: コード対応表

| 処理 | ファイル | 関数/行 |
|------|---------|--------|
| requestCandidates エントリ | `KanaKanjiConverter.swift` | `requestCandidates()` L1123 |
| ラティス変換 | `KanaKanjiConverter.swift` | `convertToLattice()` L1022 |
| processResult (全ランキング) | `KanaKanjiConverter.swift` | `processResult()` L778 |
| 全体文候補生成 | `KanaKanjiConverter.swift` | L788-802 |
| processClauseCandidate | `Kana2Kanji.swift` | `processClauseCandidate()` L27 |
| ユーザショートカット | `KanaKanjiConverter.swift` | L804-823 |
| 予測変換生成 | `KanaKanjiConverter.swift` | `getPredictionCandidate()` L551 |
| 安定予測キャッシュ統合 | `KanaKanjiConverter.swift` | `mergeStableCandidates()` L182 |
| 英語予測 | `KanaKanjiConverter.swift` | `getForeignPredictionCandidate()` L468 |
| 追加候補 | `KanaKanjiConverter.swift` | `getAdditionalCandidate()` L695 |
| トップレベル追加 | `KanaKanjiConverter.swift` | `getTopLevelAdditionalCandidate()` L664 |
| 混合 + top-5 | `KanaKanjiConverter.swift` | L894-910 |
| 完全一致読み昇格 | `KanaKanjiConverter.swift` | L980-992 |
| 第一文節候補 | `KanaKanjiConverter.swift` | L913-931, L934-944 |
| 単語候補 | `KanaKanjiConverter.swift` | L947-975 |
| 重複除去 | `KanaKanjiConverter.swift` | `getUniqueCandidate()` L425 |
| actions 適用・template展開 | `KanaKanjiConverter.swift` | L997-1012 |
| ConvertRequestOptions | `ConvertRequestOptions.swift` | L56-80 |
| Zenzai 設定 | `InputManager.swift` | `getConvertRequestOptions()` L135-152 |
| 結果反映 (UI) | `InputManager.swift` | `setResult()` L994-1024 |
| LiveConversionManager | `LiveConversionManager.swift` | L1-155 |

## 付録B: 用語集

| 用語 | 説明 |
|------|------|
| Lattice | 入力文字列に対するすべての可能な分割・変換パスを保持するグラフ構造 |
| CandidateData | 文節（clause）の集合。各文節はテキスト、値、範囲を持つ |
| clauseResult | `result.result.getCandidateData()` で得られる `[CandidateData]` |
| PValue | 候補の評価値。`Float16`/`Float32`。高いほど良い |
| rubyCount | 候補の読み（ルビ）の文字数 |
| getUniqueCandidate | テキストキーによる重複除去 + 高 value 選択 |
| mergeStableCandidates | 安定予測キャッシュを優先的に上限まで埋める統合関数 |
| chained | 遅延結合。新しい配列を確保しない |
| Zenzai | ニューラルかな漢字変換システム。azooKey 独自の Transformer ベース変換 |
| seenCandidates | 上位フェーズで既に採用されたテキストの集合。下位フェーズでの重複を防止 |
