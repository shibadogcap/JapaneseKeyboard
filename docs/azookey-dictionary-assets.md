# AzooKey辞書asset配置メモ

## 目的

AzooKey本家の辞書形式をAndroidアプリ内蔵assetとして読み、Kotlin側の `AzooKeyLoudsDictionaryRegistry` から候補生成へ接続する。

## 現在の同梱状態

`app/src/main/assets/louds/` には、AzooKey本家 `azooKey_dictionary_storage` submodule由来のLOUDS辞書を同梱している。

- source: `/private/tmp/AzooKeyKanaKanjiConverter/Sources/KanaKanjiConverterModuleWithDefaultDictionary/azooKey_dictionary_storage/Dictionary/louds`
- identifiers: 160件
- asset size: 約23MB
- debug APK: `app/build/outputs/apk/fullStandard/debug/app-full-standard-debug.apk`

`app/src/main/assets/azookey/emoji/` には、AzooKey本家 `azooKey_emoji_dictionary_storage` submodule由来のemoji TextReplacer辞書を同梱している。

- source: `/private/tmp/AzooKeyKanaKanjiConverter/Sources/KanaKanjiConverterModuleWithDefaultDictionary/azooKey_emoji_dictionary_storage/EmojiDictionary/emoji_all_E17.0.txt`
- asset: `app/src/main/assets/azookey/emoji/emoji_all_E17.0.txt`
- asset size: 約232KB
- format: `base<TAB>queries_csv<TAB>variations_csv`

同じディレクトリには、AzooKey本家の通常Dicdata emoji辞書も同梱している。

- source: `/private/tmp/AzooKeyKanaKanjiConverter/Sources/KanaKanjiConverterModuleWithDefaultDictionary/azooKey_emoji_dictionary_storage/EmojiDictionary/emoji_dict_E17.0.txt`
- asset: `app/src/main/assets/azookey/emoji/emoji_dict_E17.0.txt`
- asset size: 約356KB
- format: `ruby<TAB>word<TAB>lcid<TAB>rcid<TAB>mid<TAB>value`

本家clone側で辞書ディレクトリが空の場合は、AzooKeyKanaKanjiConverterで次を実行してsubmoduleを初期化する。

```bash
git submodule update --init --recursive
```

## 配置

標準配置は `app/src/main/assets/louds/`。

必須:

- `charID.chid`
  - 本家 `charID.chid` と同じ、文字ID順に文字を並べたUTF-8テキスト。

推奨:

- `identifiers.txt`
  - 利用可能な辞書identifierの一覧。
  - 空白、タブ、カンマ区切りに対応。
  - `#` 以降はコメント。
  - 未配置の場合は `.louds` / `.loudschars2` のペアからidentifierを自動発見する。

identifierごとに必要:

- `<identifier>.louds` または `<escaped>.louds`
- `<identifier>.loudschars2` または `<escaped>.loudschars2`
- `<identifier><shard>.loudstxt3` または `<escaped><shard>.loudstxt3`

`escaped` は AzooKey本家 `DictionaryBuilder.escapedIdentifier` と同じUTF-16 hex chunk形式。例:

- `あ` -> `[3042]`
- `シ` -> `[30B7]`
- `user` / `memory` / `user_shortcuts` はraw名のまま。

現在のloaderは本家テストfixture互換のraw filenameもfallbackで読むため、`シ.louds` や `シ0.loudstxt3` も読める。

## identifiers.txt例

```text
# system dictionary identifiers
ア イ ウ エ オ
カ キ ク ケ コ
サ シ ス セ ソ
```

## アプリ接続

`AzooKeyDictionaryAssetProvider`（Hilt `@Singleton`）が `AndroidAssetAzooKeyDictionaryShardLoader.createRegistryFromManifest` をlazyに呼ぶ。`DefaultCandidateService` と確定後 emoji 検索が共有する。assetが無い場合は `null` になり、既存の変換挙動は変わらない。

assetが存在する場合:

1. `identifiers.txt` を読む。無い場合はasset file listから `.louds` / `.loudschars2` の揃ったidentifierを自動発見する。
2. 入力読みをカタカナへ正規化する。
3. 先頭文字に対応するidentifierのLOUDS lookupをlazy loadする。
4. 完全一致entryを `system`、prefix descendant entryを `systemPrediction` として `AzooKeyStyleCandidateServiceFactory` のLOUDS dictionary sourceに合成する。

`identifiers.txt` を明示配置した場合はその内容を優先する。テストや部分assetでは、使いたいidentifierだけに絞れるため明示manifestが便利。

emoji辞書は `AzooKeyDictionaryAssetProvider.emojiDictionarySearch` 経由で読む（内部は `AndroidAssetAzooKeyEmojiDictionaryLoader`）。TextReplacer assetが無い場合は `null` になり、既存の `KanaKanjiEngine.searchEmojiDictionaryEntries` fallbackを使う。Dicdata assetだけが無い場合はTextReplacerのみで動く。

assetが存在する場合:

1. `azookey/emoji/emoji_all_E17.0.txt` を読む。
2. `AzooKeyEmojiDictionaryTextParser` でbase emoji、query、variation emojiを `AzooKeyDictionaryEntry` へ展開する。
3. `azookey/emoji/emoji_dict_E17.0.txt` があれば読み、`AzooKeyEmojiDicdataTextParser` で通常Dicdata emoji entryへ展開する。rubyは入力検索用にひらがなへ正規化する。
4. 入力中の絵文字候補は `searchInputPrefix` を使い、TextReplacer queryとDicdata rubyのprefixから検索する。候補順はAzooKey本家の `PValue` 相当を優先し、同じsurfaceがTextReplacer fallbackとDicdataの両方にある場合はDicdata側を残す。
5. 確定後予測の絵文字候補は `searchPostCommit` を使い、AzooKey本家TextReplacerに近く確定語の完全一致queryから検索する。
6. 確定後予測では本家同様、variation emojiを除外してbase emojiを優先する。

`emoji_dict_E17.0.txt` には `🎵️` などの記号的に使われるentryも含まれる。現在はsource kindを `Emoji` として読み、emoji dictionary sourceから特殊候補laneへ流す。将来的にsymbol laneとの完全分離が必要になった場合は、surfaceやCIDにもとづいて `Symbol` sourceへ再分類する。

## 検証

## スコア形式

Kotlin側の候補順位はAzooKey本家に合わせて `PValue` 相当の `Float` を主軸にする。

- `AzooKeyDictionaryEntry.value`: 本家Dicdata/LOUDS由来のFloat32 valueを保持する。
- `Candidate.value`: ranker、source adapter、Zenz rerank、確定後予測で使う共通順位値。
- `wordCost` / `Candidate.score`: 既存UI、既存graph、古いテスト互換のために残す整数値。
- 学習候補: 既存学習scoreは `LearningMemory` 風の `-1 - 4/rubyLength - 3*d^3` へ写像して候補順へ使う。
- emoji Dicdata: TextReplacer fallbackより先に扱い、Dicdata内は `value` 降順で並べる。

ローカルに本家 `DictionaryMock` がある場合、以下のスキップ可能テストが動く。

- `AzooKeyDictionaryMockFixtureTest`
  - `シカイ` / `しかい` から `司会` / `視界` / `死界` が取得できることを確認する。
  - `AzooKeyStyleCandidateService` のLOUDS sourceとして通した場合も、最終候補に同じ主要候補が上がることを確認する。
- `AzooKeyBundledLoudsAssetTest`
  - 同梱済み `app/src/main/assets/louds` のmanifestが読めることを確認する。
  - 同梱済みLOUDS辞書を `AzooKeyStyleCandidateService` へ通し、代表候補が最終候補に出ることを確認する。
- `AzooKeyBundledLoudsGoldenTest`
  - 同梱本辞書で `しかい` の先頭5候補順を回帰固定する（`司会`, `視界`, `歯科医`, `市会`, `士会`）。本家 DictionaryMock とは異なる。
  - 代表読み `にほん` は先頭3固定 + 4–5位は `ニホン`/`にほん` の部分集合（asset バージョン定数付き）。
- `AzooKeyBundledEmojiDictionaryOrderingTest`
  - 同梱 emoji で Dicdata の value 降順と TextReplacer より Dicdata 優先を確認する。
  - `AzooKeyStyleCandidateService` 経由で `えが` パイプラインに emoji が載ること・Dicdata lane 順を確認する。
- `ImeSuggestionOrchestratorTest` / `ImeZenzContextBuilderTest` / `EditorGatewayTest` / `HardwareKeyboardCoordinatorTest` / `InputActionDispatcherTest`
- `AzooKeyLoudsEngineDuplicateSurfaceTest`
  - LOUDS のみ・engine 空の経路で main 候補に surface 重複がないことを確認する。
- `AzooKeyBundledEmojiDictionaryAssetTest`
  - 同梱済み `app/src/main/assets/azookey/emoji/emoji_all_E17.0.txt` と `emoji_dict_E17.0.txt` が読めることを確認する。
  - 入力中prefix検索、確定後検索、Dicdata由来候補の優先を確認する。

通常確認:

```bash
./gradlew :app:testFullStandardDebugUnitTest --tests 'com.kazumaproject.markdownhelperkeyboard.converter.candidate.*'
```

APKビルド確認:

```bash
./gradlew :app:assembleFullStandardDebug
```

## manifest生成

assetを配置したあと、明示manifestを生成する場合は次を実行する。

```bash
./gradlew :app:generateAzooKeyLoudsManifest
```

別ディレクトリを入力にする場合:

```bash
./gradlew :app:generateAzooKeyLoudsManifest -PazooKeyLoudsDir=/path/to/louds
```

このtaskは `.louds` と `.loudschars2` が揃っているidentifierだけを `identifiers.txt` に出力する。辞書本体のコピーは行わない。

## assetコピー

本家側で出力したLOUDS辞書をAndroid assetへコピーする場合:

```bash
./gradlew :app:copyAzooKeyLoudsAssets -PazooKeyLoudsSourceDir=/path/to/louds
```

コピー先を変える場合:

```bash
./gradlew :app:copyAzooKeyLoudsAssets \
  -PazooKeyLoudsSourceDir=/path/to/louds \
  -PazooKeyLoudsDestDir=/path/to/destination
```

このtaskは `charID.chid`、`.louds`、`.loudschars2`、`.loudstxt3` だけをコピーする。`identifiers.txt` はコピーせず、必要ならコピー後に `generateAzooKeyLoudsManifest` で生成する。

コピー、検証、manifest生成をまとめて行う場合:

```bash
./gradlew :app:prepareAzooKeyLoudsAssets -PazooKeyLoudsSourceDir=/path/to/louds
```

出力先を変える場合:

```bash
./gradlew :app:prepareAzooKeyLoudsAssets \
  -PazooKeyLoudsSourceDir=/path/to/louds \
  -PazooKeyLoudsDestDir=/path/to/destination
```

emoji TextReplacer辞書をAndroid assetへコピーする場合:

```bash
./gradlew :app:copyAzooKeyEmojiDictionaryAssets \
  -PazooKeyEmojiDictionarySourceFile=/path/to/EmojiDictionary/emoji_all_E17.0.txt \
  -PazooKeyEmojiDicdataDictionarySourceFile=/path/to/EmojiDictionary/emoji_dict_E17.0.txt
```

コピー先を変える場合:

```bash
./gradlew :app:copyAzooKeyEmojiDictionaryAssets \
  -PazooKeyEmojiDictionarySourceFile=/path/to/EmojiDictionary/emoji_all_E17.0.txt \
  -PazooKeyEmojiDicdataDictionarySourceFile=/path/to/EmojiDictionary/emoji_dict_E17.0.txt \
  -PazooKeyEmojiDictionaryDestDir=/path/to/assets/azookey/emoji
```

`emoji_dict_E17.0.txt` を省略した場合は、TextReplacer辞書だけをコピーする。

## asset検証

asset配置後は次で最低限のファイルセットを検証できる。

```bash
./gradlew :app:verifyAzooKeyLoudsAssets
```

別ディレクトリを入力にする場合:

```bash
./gradlew :app:verifyAzooKeyLoudsAssets -PazooKeyLoudsDir=/path/to/louds
```

このtaskは次を確認する。

- `charID.chid` が存在する。
- identifierごとに `.louds` が存在する。
- identifierごとに `.loudschars2` が存在する。
- identifierごとに少なくとも1つ `.loudstxt3` shardが存在する。

内容の候補差分までは見ない。候補内容は `AzooKeyDictionaryMockFixtureTest` のようなfixture testで確認する。

## 次の作業

- 本家辞書全体を同梱した状態で、代表読みの候補順がAzooKey本家とどこまで一致するか差分テストを増やす（`しかい` / `にほん` は済。他読みは未）。
- asset sizeとAPK sizeを見ながら、full/lite/flavorごとの同梱方針を決める。
- 本家assetから複数identifierのfixtureを作り、代表読みの候補差分テストを増やす。
- emoji TextReplacer辞書と `emoji_dict_E17.0.txt` は同梱済み。次は本家fixtureとの差分を増やし、TextReplacer候補、通常emoji候補、特殊候補providerの重複順をさらに調整する。
- symbolの本家assetも `AzooKeyDictionaryEntryIndex` へ流し、特殊候補と通常候補の重複順を調整する。
