# azooKey風変換・Gboard風UX ロードマップ

## ゴール

オフライン優先で動くAndroid向け日本語キーボードを作る。変換の考え方はazooKeyに寄せ、日常の操作感と見た目はGboardに近づける。

このプロジェクトではローカルのプライバシー保証を守る。パスワード欄や `EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING` を指定するエディタでは、学習、確定後予測、クリップボード履歴、Zenz/Zenzaiの個人化候補などに入力内容を渡さない。

## 長期方針

### 変換

- 変換処理を `IMEService` に埋め込まれた分岐ではなく、request/response形式のパイプラインとして扱う。
- azooKeyに近いリクエストモデルへ寄せる。
  - composing text
  - 日本語予測モード
  - 英語予測モード
  - 学習ポリシー
  - ユーザー辞書ポリシー
  - 特殊候補プロバイダ
  - Zenz/Zenzaiのrerank/generationモード
  - privacy/session metadata
- 候補ソースは最後のmergeまで分離して扱う。
  - システム辞書
  - ユーザー辞書
  - 学習メモリ
  - 確定後のつながり予測
  - Zenz/Zenzai候補
  - emoji/symbol/date/numberなどの特殊候補
  - Bitwarden/Android Inline Autofill候補
- 候補ソースごとのテストと、最終的な候補ミックス方針のテストを増やす。
- 目標は「AzooKeyのKotlin実装」を内蔵すること。Swift実装を参照し、API境界、provider順、候補分離、prediction/rerankの契約をKotlin domain modelとして再現する。
- 辞書は最終的にAzooKey互換に寄せる。
  - 単語、読み、品詞ID、スコア、連接コスト、特殊候補、絵文字/記号候補をsource adapterから扱えるようにする。
  - 既存辞書を段階的に互換schemaへ写像し、fixture testでAzooKey本家と候補順を比較できるようにする。
  - system/user/learned/emoji/symbol dictionaryを別sourceとして保持し、最後のmixerで統合する。
  - emojiはAzooKey本家のTextReplacer用TSVと通常Dicdata辞書の両方を扱い、入力中候補、確定後予測、特殊候補laneの出し分けを本家に寄せる。
- 確定後のつながり予測を強化する。
  - 語から語への遷移を学習する。
  - recency/frequencyをスコアに使う。
  - 助詞・語尾・定型フレーズのフォールバックを維持する。
  - 句読点、パスワード欄、直接入力モードでは文脈フィルタをかける。
  - AzooKeyのpost-composition predictionに近く、emoji、prediction、zero-hintを別ソースとして扱う。
  - 将来的にはZenz/Zenzai rerankの前に軽量n-gram風のスコアリングを入れる。
- 学習メモリはAzooKey本家のように候補後処理ではなく辞書sourceとして扱う。
  - private sessionでは読み書きともに止める。
  - 通常sessionでは短期メモリと永続メモリを分け、最終的にLOUDS-backed memory sourceへ寄せる。
  - 確定候補の `ruby` / `word` / `lcid` / `rcid` / `mid` / `value` を保持し、次回変換のlattice内で自然に競合させる。
- 候補順位は既存実装のlegacy `score` ではなく、AzooKey本家の `PValue` 相当を優先する。
  - `Candidate.score` は既存UI/互換用に残す。
  - `Candidate.value` を辞書、学習、絵文字、Zenz rerank、確定後予測の共通順位軸にする。
  - 本家辞書のFloat32 valueは丸めず保持し、必要な互換境界だけ `wordCost` / `score` へ落とす。
- ライブ変換はUIの表示都合ではなく、変換request policyとして扱う。
  - composing中、候補選択中、変換中、private session、Zenz/Zenzai有効時の条件をdomain policyへ集約する。
  - 差分入力でも候補laneと学習laneの扱いが通常変換とずれないようにする。
- Zenz/ZenzaiはAzooKey互換性のために必要な変換段として扱う。
  - 通常候補のrerankだけでなく、candidate evaluationの `pass` / `fixRequired` / `wholeResult` 相当をdomain modelで扱う。
  - 個人文脈や右文脈を使う経路はprivacy gateの後ろに置く。
  - experimental predictive inputは通常の辞書予測、zero-hint、emoji予測と競合しないようにlaneを分ける。

### クロスプラットフォーム構造

- 変換/sessionロジックはできるだけKotlin-firstの純粋なモジュールに置く。
- Android UIはJetpack/View/Compose-friendlyなadapter層に閉じ込める。
- 将来Swift UIでも同じ概念を共有できるように、domain modelをAndroid非依存にする。
  - candidate request
  - candidate response
  - candidate lane
  - privacy policy
  - learned transition
  - keyboard action result
- 純粋な変換部品ではAndroid framework型を避ける。`EditorInfo`、`InputConnection`、View、Autofill型は境界で変換する。

### UX

- Gboardの基本的なメンタルモデルに寄せる。
  - コンパクトなtoolbar/candidate strip
  - suggestion/toolsの予測可能な切り替え
  - パスワード欄ではAutofillと直接入力を優先
  - 安定したQWERTY形状、キー間隔、popup、return/action key
- 見た目の挙動を候補生成内部に直接結びつけない。
- Material/custom themeと将来のGboard風デフォルトが共存できるよう、themeまわりをモジュール化する。

### プライバシー

- 以下を単一のsession privacy gateで扱う。
  - パスワード系input type
  - `EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING`
  - private/incognito mode
- private sessionでは次を無効化する。
  - 学習書き込み
  - 学習候補読み取り
  - 確定後のつながり予測
  - クリップボード履歴の永続化/preview
  - Zenz/Zenzai個人化パス
- Inline Autofillはパスワード欄でも表示してよい。これはキーボード学習ではなく、editor/autofill surfaceとして扱う。

## 中期計画

### 候補パイプライン

1. 完全な `CandidateService` 抽出の前に、小さな純Kotlin部品を導入する。
2. raw numeric type checkを減らし、候補type/laneを名前つきcontractにする。
3. ranking/mixing ruleを `IMEService` から外へ出す。
4. 現在の候補生成呼び出しをrequest/response modelで包む。
5. 次のテストを増やす。
   - source ordering
   - learned candidate promotion
   - duplicate handling
   - privacy suppression
   - Zenz rerank merge behavior

### つながり予測

1. 確定後予測の組み立てを `IMEService` から抽出する。
2. 学習済み遷移候補とfallback particleを別入力として扱う。
3. simple sortingからn-gram風transition scoringへ進化できるscoring policyを追加する。
4. private sessionでは遷移学習の読み書きをしない。
5. 後で通常変換候補と同じranker/mixerへ流す。

### Zenzと学習

1. 学習メモリを独立したlaneとして扱う。
2. Zenz rerankで通常候補を改善しつつ、学習候補を消さない。
3. Zenz処理はsession privacy gateの後ろに置く。
4. conflict policyを明文化する。
   - 学習済みexact matchは見える位置に残す。
   - Zenzはsystem candidatesの順序改善に使う。
   - generated Zenz candidatesはmarkして件数制限する。
   - typed textと基本のかな/カタカナfallbackは残す。
5. AzooKey本家 `LearningType` と `ConvertRequestOptions.zenzaiMode` に近いruntime policyをKotlin側へ作る。
6. 長期的には学習メモリを `user` / `memory` identifier相当の辞書sourceとして扱い、system辞書やユーザー辞書と同じlattice上で評価する。

### ライブ変換

1. ライブ変換の発火条件を `IMEService` の散在分岐から純Kotlin policyへ移す。
2. 通常変換と同じ `CandidateRequest` / `CandidateSources` / `AzooKeyStyleCandidateService` を通す。
3. composing確定前の表示候補、確定後予測、Zenz/Zenzai評価の優先順位を固定する。
4. private session、パスワード欄、直接入力、候補選択中はライブ変換を抑制する。
5. AzooKey本家のincremental conversionに近づけるため、後で入力差分と前回候補のcacheを導入する。

### Gboard風Surface

1. まずtoolbar/candidate stripの安定化を優先する。
2. candidate stripが予測可能になってからQWERTY/tenkeyの見た目を詰める。
3. 調整対象:
   - key shape/spacing
   - popup size/position
   - toolbar icon behavior
   - candidate chip spacing
   - Inline Autofill chip size
4. よく使うeditorでscreenshot/manual QA観点を残す。
   - 通常テキスト
   - 検索欄
   - 複数行入力
   - パスワード欄
   - Bitwarden Inline Autofill

## 短期計画

現在の短期トラック:

1. パスワード欄をprivate sessionとして扱い、学習対象から外す。
2. Bitwarden Inline Autofill候補がtoolbar展開をロックしないようにする。
3. 候補ranking ruleを `IMEService` から抽出する。
4. 候補type/lane helperを導入する。
5. rankingとprivacy behaviorのテストを増やす。
6. 将来の `CandidateService` に互換なrequest objectで候補生成を包み始める。
7. 確定後のつながり予測を純Kotlin componentへ抽出する。
8. azooKey風の `ConversionResult` / request option modelを導入し、Live Zenz候補mixへ接続する。
9. 通常候補生成をazooKey風mixerへ通し、既存の候補順を保ちながらsource分離へ寄せる。
10. `CandidateRequest` を導入し、現在の `getSuggestionList*` 経路を将来の `CandidateService` へ移せる形にする。
11. `CandidateSources` を導入し、候補ソースの束ね方を明示する。
12. `IMEService` 内でも `CandidateSources` を明示的に作ってからmixerへ渡し、`CandidateService` 抽出の境界を作る。
13. `CandidateAssembler` を導入し、AzooKey風 `ConversionResult` への組み立てを `IMEService` 外へ出す。
14. `SpecialCandidateProvider` を導入し、Unicode、日時、メール、数値、絵文字などをAzooKey風の特殊候補laneとして扱う。
15. 確定後予測をAzooKeyのpost-composition prediction風にし、emoji/prediction/zero-hintの候補配分を純Kotlin componentで固定する。
16. Zenz rerankの辞書スコア/LMスコア融合を純Kotlin policyへ切り出し、AzooKeyのcandidate evaluationに寄せる準備をする。
17. AzooKey本家のemoji TextReplacer assetを同梱し、入力中prefix検索と確定後完全一致検索を既存emoji fallbackより優先する。
18. 学習・ライブ変換・Zenzaiの有効/無効をまとめるruntime policyを追加し、private sessionで同時に抑制されることをテストする。
19. 本家 `LearningMemory` の構造をKotlinへ写像し、まずは既存学習履歴をAzooKey互換entryとして読み出す。
20. 本家 `zenzaiMode` / `experimentalZenzaiPredictiveInput` 相当のcandidate laneを作り、Zenz生成候補と辞書候補の競合ルールを固定する。

## 現在の進捗

完了:

- パスワード欄はno-personalized-learning editorと同じprivate-session behaviorを適用する。
- Bitwarden/Android Inline Autofill候補はtoolbar toggleを毎回強制リセットしない。
- `AzooKeyStyleCandidateRanker` を最初の候補ranking componentとして導入した。
- `AzooKeyStyleCandidateMixer` と `AzooKeyStyleConvertRequestOptions` でazooKey風の `autoMix` / `manualMix` / `disabled` を表現できるようにした。
- Live Zenz結果は `IMEService` 内の直接連結ではなく、azooKey風mixer経由でmergeされる。
- 通常/Original/WithoutPrediction候補は `mixSuggestionSourcesAzooKeyStyle` を共有し、学習候補を日本語予測laneとして扱う。
- `CandidateRequest` でinput、candidate mode、prediction mode、learning policy、source toggle、privacy stateをsnapshot化した。
- private/suppressed sessionではrequest model上で学習候補読み取りを止める。
- `CandidateSources` でlearned/userTemplate/userDictionary/system/romaji/english/special/firstClauseを分離して束ねるようにした。
- `CandidateSources.systemPrediction` を追加し、LOUDS prefix検索のような辞書由来予測を通常system候補と別レーンで扱えるようにした。
- `getSuggestionListOriginal` / `getSuggestionList` / `getSuggestionListWithoutPrediction` は、候補リストを直接mixerへ渡すのではなく `CandidateSources` を作ってからmixする。
- `CandidateAssembler` で `CandidateRequest` と `CandidateSources` から `AzooKeyStyleConversionResult` を作る境界を追加した。
- 確定後予測は助詞fallbackが候補欄を埋めすぎないよう制限する。
- learned-history promotionとduplicate handlingのranking testを追加した。
- `CandidateType` / `CandidateLane` を導入し、raw candidate type numberを少しずつ名前つきcontractへ置換する準備ができた。
- `SpecialCandidateProvider` を追加し、AzooKeyの `Unicode` provider相当として `u3042` / `U+1F600` のような入力をUnicode文字候補へ変換できるようにした。
- AzooKeyの `CommaSeparatedNumber` provider相当として、`1000` や `-1234567.89` から桁区切り数値候補を出せるようにした。
- AzooKeyの `Typography` provider相当として、ASCII英数字から太字、イタリック、スクリプト、Fraktur、Double Struck、Sans、Monospaceなどの装飾文字候補を出せるようにした。
- AzooKeyの `TimeExpression` provider相当として、`930` / `0930` のようなASCII数字入力から `9:30` / `09:30` 形式の時刻候補を出せるようにした。
- AzooKeyの `Calendar` provider相当として、`2024ねん` から `令和6年`、`れいわがんねん` から `2019年` のような和暦/西暦候補を出せるようにした。
- AzooKeyの `EmailAddress` provider相当として、`sumire@` / `sumire@out` から主要ドメインつきメールアドレス候補を出せるようにした。
- `CandidateRequest` / `AzooKeyStyleConvertRequestOptions` に `specialCandidateProviders` を持たせ、AzooKey本家と同じく特殊候補provider一覧を差し替え可能にした。
- 通常/Original/WithoutPrediction候補生成では `DefaultSpecialCandidateProviders` の結果を `CandidateSources.special` に流し、mixer/assembler経由で候補欄へ出せるようにした。
- `AzooKeyStyleConverter` facadeを追加し、`IMEService` ではなくKotlin converter domain側で特殊候補provider注入、source assembly、conversion result化を行う入口を作った。
- `CandidateSourceProvider` / `CompositeCandidateSourceProvider` を追加し、system/userDictionary/learned/template/romaji/emojiなどのsource取得をAzooKey互換エンジン側へ移す契約を作った。
- `SuspendCandidateSourceProvider` / `CompositeSuspendCandidateSourceProvider` を追加し、Room repositoryやKanaKanji engineのような非同期sourceもAzooKey互換facadeへ直接流せるようにした。
- `IMEService` のNormal/Original/WithoutPrediction候補合流点を `convertSuggestionSourcesAzooKeyStyle` に寄せ、`AzooKeyStyleConverter` の非同期provider入口をアプリ実装から使い始めた。
- `CandidateSourceAdapters` を追加し、learned/user dictionary/user template/romaji候補の読み込み条件、prefix閾値、Candidate化、score順整列をconverter domain側のprovider adapterへ移した。
- `IMEService` のNormal/Original/WithoutPrediction候補取得では `collectAuxiliarySuggestionSources` を使い、学習・ユーザー辞書・定型文・ローマ字候補を `CompositeSuspendCandidateSourceProvider` から取得するようにした。
- `SystemKanaKanjiCandidateSourceProvider` を追加し、Normal/Original/WithoutPredictionごとのKanaKanji engine呼び出しと `BunsetsuCandidateResult` の保持をsource providerとして扱えるようにした。
- `AzooKeyStyleCandidateService` を追加し、auxiliary source、system source、special provider注入、conversion result化、文節結果の返却をconverter domain側でまとめる入口を作った。
- `AzooKeyStyleCandidateServiceFactory` を追加し、learned/user dictionary/user template/romaji provider生成をconverter domain側へ移した。`IMEService` はAndroid repository呼び出しlambdaと閾値だけを渡す。
- `SystemKanaKanjiEngineSourceFactory` を追加し、Normal/Original/WithoutPredictionごとのKanaKanji engine呼び出しを `IMEService` からconverter domain側へ移した。`IMEService` はengine/repository/config/log callbackを渡すだけに近づいた。
- `SymbolSpecialCandidateProvider` を追加し、`きごう` / `やじるし` / `かっこ` などから基本記号候補をsource分離して出せるようにした。
- `CandidateAssembler` が `request.nBest` を尊重するようにし、special candidateとfirstClause resultは別枠として残ることをテストした。
- `IMEService` の候補本線は `AzooKeyStyleCandidateService.convert()` を呼び、NG word filter、候補順override、表示更新だけに近い形へさらに寄せた。
- `CandidatePostProcessor` を追加し、NG word filter、重複除去、候補順overrideをconverter domain側のpost-process境界へ移した。
- Normal/Original/WithoutPredictionの候補本線は `postProcessSuggestionCandidates` を呼ぶだけになり、NGワード一覧が空のときは空Regexで候補を落とさないようにした。
- `AzooKeyDictionaryEntry` を追加し、AzooKey本家の `DicdataElement(word, ruby, lcid, rcid, mid, value, metadata)` に近い辞書entry modelをKotlin側に作った。
- `CandidateSourceRecord` は既存の `text` / `reading` / `score` 互換を保ったまま、必要なら `AzooKeyDictionaryEntry` を載せられるようにした。
- learned/user dictionary/user template sourceは、候補化前にAzooKey互換entryへ写像し始めた。ユーザー辞書は本家AncoSessionのdynamic user dictionaryと同じく固有名詞CID・一般MIDを初期値にする。
- system dictionary向けの互換entry mapperと、left/right IDが欠けているentryを補完する `AzooKeyDictionaryConnectionIdResolver` を追加した。
- `AzooKeyDictionaryEntry.toCandidate()` は `yomi` / `leftId` / `rightId` を保持するようにした。辞書entryから候補へ戻しても連接情報を失わない。
- `TokenEntryConverted` をAzooKey互換entryへ写像するmapperを追加した。既存engine内部のtoken resultを互換entry境界へ接続する準備ができた。
- `SystemUserDictionaryCandidateSourceProvider` を追加し、system user dictionary由来entryを候補sourceとして扱える入口を作った。
- `SystemUserDictionaryDao` / `SystemUserDictionaryRepository` にprefix検索を追加した。Hilt境界へ接続するときにsystem user dictionary source providerへそのまま渡せる。
- `AzooKeyStyleCandidateServiceFactory` にsystem user dictionary sourceを接続した。`IMEService` は `SystemUserDictionaryRepository.searchByReadingPrefix` の結果をAzooKey互換entryへ写像して渡す。
- legacy `CandidateSourceRecord` から候補化するときも `AzooKeyDictionaryConnectionIdResolver` を通し、left/right IDが欠けている候補をAzooKey互換の固有名詞CIDへ補完するようにした。
- `AzooKeyDictionaryNodeMapper` を追加し、AzooKey互換entryからGraphBuilder用 `Node` を作れるようにした。
- GraphBuilderの通常system辞書経路で `TokenEntryConverted` -> `AzooKeyDictionaryEntry` -> `Node` の境界を通すようにした。既存engine内部のtoken resultをAzooKey互換entryへ接続し始めた。
- user dictionaryとsystem user dictionaryが同じsurfaceを出す場合、post-processでは先に来たユーザー辞書候補を残すことをテストで固定した。
- GraphBuilder内に `tokenToAzooKeyNode` helperを追加し、token辞書由来のNode生成をAzooKey互換entry経由に集約した。
- system user dictionaryの通常検索、typo correction、omission search経路を `AzooKeyDictionaryEntry` -> `Node` へ寄せた。
- system dictionaryのtypo correction、omission search経路も `AzooKeyDictionaryEntry` -> `Node` へ寄せた。
- wiki/web/person/neologdの外部辞書経路も同じNode mapperを通すようにした。
- GraphBuilder内のユーザー辞書・学習辞書経路も `AzooKeyDictionaryEntry` -> `Node` へ寄せた。ユーザー辞書は既存 `PosMapper` のcontext IDを保持し、学習辞書は既存graph互換のfallback CID policyを明示した。
- `AzooKeyDictionaryConnectionIdPolicies` を追加し、default補完とlearned graph互換補完をpolicyとして分けた。
- `EmojiDictionaryCandidateSourceProvider` / `SymbolDictionaryCandidateSourceProvider` を追加し、AzooKey互換entry由来の絵文字・記号候補をspecial laneへ流せるようにした。
- `AzooKeyStyleCandidateServiceFactory` にemoji dictionary sourceとsymbol dictionary sourceを接続した。`IMEService` では既存 `KanaKanjiEngine` の内蔵emoji/symbol辞書検索をAzooKey互換entryへ写像して渡す。
- `AzooKeyDictionaryEntryIndex` を追加し、AzooKey互換entryを読み完全一致・読みprefix・source kindで検索できる純Kotlin indexを作った。
- `AzooKeyDictionaryShardName` を追加し、本家 `DictionaryBuilder.escapedIdentifier` と同じUTF-16 hex chunk形式で辞書shard名を作れるようにした。
- `AzooKeyLoudstxt3BinaryParser` を追加し、本家 `Loudstxt3Builder.makeBinary` / `LOUDS.parseBinary` に対応するbinary `.loudstxt3` payload/fileを `AzooKeyDictionaryEntry` へ変換できるようにした。
- `AzooKeyDictionaryShardLoader` を追加し、`louds/[3042]0.loudstxt3` のようなtext/binary shardを読み、entry listや `AzooKeyDictionaryEntryIndex` へ変換できるようにした。現在は `charID.chid`、`.louds`、`.loudschars2` から `AzooKeyLoudsDictionaryLookup` を組み立てる入口も持つ。
- `AndroidAssetAzooKeyDictionaryShardLoader` を追加し、Android `AssetManager` からAzooKey辞書shardを読むfactoryを用意した。
- `AzooKeyCharIdMap` を追加し、本家 `charID.chid` 形式を読み、読み文字列をAzooKey char ID列へ変換できるようにした。
- `AzooKeyLoudsTrie` を追加し、本家 `.louds` / `.loudschars2` binaryから完全一致node indexとprefix descendant node indexを検索できるようにした。0-bit selectはword単位prefix countで高速化済み。
- `AzooKeyLoudsDictionaryLookup` を追加し、node indexを本家同様 `shard = nodeIndex >> 11` / `local = nodeIndex & 2047` に分解して `.loudstxt3` shardの該当slotを読む検索facadeを作った。
- `AzooKeyLoudsDictionaryCandidateSourceProvider` を追加し、LOUDS辞書lookup由来のentryをsystem candidate sourceとして候補生成に接続できる形にした。`AzooKeyStyleCandidateServiceFactory` から複数lookupをauxiliary sourceへ合成できる入口も追加済み。
- LOUDS辞書sourceは、完全一致entryを `system`、prefix descendant entryを `systemPrediction` へ分離するようにした。`CandidateAssembler` は日本語予測laneで `learned + systemPrediction` を使うため、確定変換候補と予測候補を混ぜすぎずにAzooKey風の候補配分へ近づけられる。
- `AzooKeyLoudsDictionaryRegistry` を追加し、ひらがな入力をカタカナへ正規化したうえで、読みの先頭文字から該当identifierのLOUDS lookupへルーティングできるようにした。
- `AzooKeyLoudsIdentifierManifest` を追加し、`louds/identifiers.txt` から利用可能identifier一覧を読み、registry生成に使えるようにした。
- `AzooKeyLoudsAssetManifest` を追加し、`identifiers.txt` が無い場合でもasset内の `.louds` / `.loudschars2` ペアからidentifierを自動発見できるようにした。
- `AzooKeyLoudsAssetValidation` を追加し、asset file listから `charID.chid`、`.louds`、`.loudschars2`、`.loudstxt3` shardの欠落を検出できるようにした。
- `AzooKeyDictionaryShardLoader` はescaped filenameに加えて、本家テストfixtureのraw filename（例: `シ.louds`, `シ0.loudstxt3`）もfallbackで読めるようにした。
- `IMEService.createCandidateServiceFactory` にAndroid asset由来のLOUDS dictionary registryをoptional sourceとして接続した。`identifiers.txt` があればそれを使い、無ければasset file listからidentifierを自動発見する。assetが無い場合はnullになり、既存挙動を維持する。registryは `by lazy` で一度だけ読む。
- `:app:generateAzooKeyLoudsManifest` Gradle taskを追加し、配置済みLOUDS assetまたは `-PazooKeyLoudsDir=/path/to/louds` の外部ディレクトリから `identifiers.txt` を生成できるようにした。辞書本体のコピーは行わない。
- `:app:verifyAzooKeyLoudsAssets` Gradle taskを追加し、配置済みLOUDS assetまたは `-PazooKeyLoudsDir=/path/to/louds` の外部ディレクトリに対して最低限のファイル欠落を検出できるようにした。
- `:app:copyAzooKeyLoudsAssets` Gradle taskを追加し、`-PazooKeyLoudsSourceDir=/path/to/louds` からAndroid asset用LOUDSファイルをコピーできるようにした。`identifiers.txt` はコピーせず、コピー後にmanifest生成taskで作る。
- `:app:prepareAzooKeyLoudsAssets` Gradle taskを追加し、コピー、最低限の欠落検証、`identifiers.txt` 生成を1コマンドで実行できるようにした。
- AzooKey本家 `azooKey_dictionary_storage` submoduleを初期化し、`Dictionary/louds` の本辞書LOUDS assetを `app/src/main/assets/louds/` へ同梱した。160 identifier、約23MB。
- ローカルに本家 `DictionaryMock` がある場合、`シカイ` / `しかい` から `司会` / `視界` / `死界` が取得できることをKotlin側のスキップ可能fixture testで確認する。さらに `AzooKeyStyleCandidateService` のLOUDS sourceとして通しても最終候補に主要候補が出ることを確認する。
- `AzooKeyBundledLoudsAssetTest` を追加し、repoに同梱した本辞書assetのmanifest読み込みと、`AzooKeyStyleCandidateService` 経由の代表候補生成をテストするようにした。
- 実データ同梱状態で `:app:assembleFullStandardDebug` が成功することを確認した。出力は `app/build/outputs/apk/fullStandard/debug/app-full-standard-debug.apk`。
- `AzooKeyDictionaryImportSource` を追加し、複数identifier/shard specから互換entry list/indexを構築できるようにした。
- `AzooKeyEmojiDictionaryTextParser` / `AzooKeyEmojiDictionarySearch` を追加し、本家 `TextReplacer` のemoji TSV形式(base, queries, variations)をKotlin側で読めるようにした。variation emojiはmetadataで区別する。
- AzooKey本家 `azooKey_emoji_dictionary_storage` submodule由来の `emoji_all_E17.0.txt` を `app/src/main/assets/azookey/emoji/` へ同梱した。約232KB。
- AzooKey本家 `azooKey_emoji_dictionary_storage` submodule由来の `emoji_dict_E17.0.txt` を `app/src/main/assets/azookey/emoji/` へ同梱した。約356KB。
- `AndroidAssetAzooKeyEmojiDictionaryLoader` を追加し、Android `AssetManager` からemoji TextReplacer辞書を読むfactoryを用意した。
- `AzooKeyEmojiDicdataTextParser` を追加し、本家 `emoji_dict_E17.0.txt` の `ruby, word, lcid, rcid, mid, value` 行をemoji sourceとして読めるようにした。rubyは入力検索用にひらがなへ正規化する。
- `AzooKeyEmojiDictionarySearch` はTextReplacer TSVとDicdata emoji辞書を合成できるようにした。入力中prefix検索ではword cost優先でDicdata候補を上位に出し、確定後予測ではTextReplacerの完全一致queryを使う。
- `IMEService` の入力中emoji dictionary sourceと確定後emoji prediction sourceは、同梱emoji辞書を優先し、assetが無い場合だけ既存 `KanaKanjiEngine` emoji検索へfallbackするようにした。
- `:app:copyAzooKeyEmojiDictionaryAssets` Gradle taskを拡張し、本家 `emoji_all_E17.0.txt` と任意の `emoji_dict_E17.0.txt` をAndroid assetへコピーできるようにした。
- `AzooKeyBundledEmojiDictionaryAssetTest` を更新し、同梱emoji TextReplacer辞書とDicdata辞書で入力中prefix検索、確定後検索、Dicdata候補優先を確認するようにした。
- `EmojiDictionaryCandidateSourceProvider` 経由でもDicdata word cost順が保たれることをテストした。
- LOUDS本辞書とemoji TextReplacer辞書を同梱した状態で `:app:assembleFullStandardDebug` が成功することを確認した。出力は `app/build/outputs/apk/fullStandard/debug/app-full-standard-debug.apk`。
- `AzooKeyDicdataElementTextParser` を追加し、本家 `.loudstxt3` のentry行に対応する `ruby, word, lcid, rcid, mid, value, adjust?` 形式を `AzooKeyDictionaryEntry` へ変換できるようにした。AzooKey辞書取り込みの最初の入口。
- `KanaKanjiEngine.searchEmojiDictionaryEntries` / `searchSymbolDictionaryEntries` を追加し、既存LOUDS/token辞書からemoji/symbol候補をAzooKey互換entryとして取り出せるようにした。
- `PostCommitPredictionComposer` をAzooKeyの `requestPostCompositionPredictionCandidates` に近づけ、emoji候補最大3件、prediction候補、zero-hint候補、助詞最大3件の配分を扱えるようにした。
- `PostCommitEmojiDictionaryProvider` を追加し、確定後テキストからemoji辞書entryを検索して `PostCommitPredictionComposer` のemoji candidateへ渡せる境界を作った。本家TextReplacer同様、確定後予測ではvariation emojiを除外する。
- `IMEService.schedulePostCommitPrediction` から `PostCommitEmojiDictionaryProvider` を呼び、既存emoji辞書由来の候補を確定後予測candidateへ混ぜ始めた。
- `ZenzRerankFusionPolicy` を追加し、Zenz raw scoreと辞書スコアの融合rerankを `IMEService` から純Kotlin componentへ切り出した。
- `ZenzaiCandidateEvaluationResult` をconverter domainへ追加し、Zenzaiの `pass` / `fixRequired` / `wholeResult` 相当をIME service modelから分離した。
- `AzooKeyRuntimeConversionPolicy` を追加し、学習メモリ、Zenzai、experimental predictive input、ライブ変換の有効/無効をprivate/suppressed sessionでまとめて抑制できる純Kotlin境界を作った。
- `CandidateRequest` はruntime policyを通してeffective learning type、effective Zenzai mode、Zenzai predictive input、live conversion状態を返すようにした。
- `CandidateRequest` / `AzooKeyStyleConvertRequestOptions` に `versionString` metadataを追加した。本家 `VersionSpecialCandidateProvider` 相当で利用する。
- `IMEService.buildCandidateRequest` と `shouldStartLiveConversion` はruntime policyへ接続済み。現時点では挙動変化を抑えるため、ライブ変換の候補選択中/変換中抑制はpolicy単体に固定し、IME側の詳細置換は次段階で行う。
- `IMEService` のZenz候補生成、Zenzai candidate evaluation、Zenz rerank plan入口はruntime policyの個人化gateを通すようにした。private/suppressed sessionではZenz系の文脈利用経路へ入らない。
- `VersionSpecialCandidateProvider` を追加し、本家同様 `ばーじょん` / `バージョン` 入力でアプリのversion stringを特殊候補として出せるようにした。`IMEService.buildCandidateRequest` から `BuildConfig.VERSION_NAME` を渡す。
- `Candidate` に `isLearningTarget` を追加し、本家同様Version候補を学習対象外にした。IMEのタップ学習処理もこのflagを見て学習を抑止する。
- `AzooKeyCandidateLearningPolicy` を追加し、タップ候補を学習するかどうかの判定をIME serviceから純Kotlin componentへ切り出した。private mode、先頭候補学習設定、候補側の `isLearningTarget` をunit testで固定している。
- `AzooKeyPostCommitPredictionPolicy` と `AzooKeyTransitionLearningPolicy` を追加し、確定後予測を出す条件、確定語同士のつながり学習を書き込む条件を純Kotlin componentへ切り出した。private/suppressed/non-Japanese/終端句点、候補側の `isLearningTarget` をunit testで固定している。
- `PostCommitPredictionService` を追加し、学習済みつながり候補、emoji辞書候補、zero-hint fallbackの合成をIME serviceから候補domainへ移した。AzooKey本家と同じくemoji最大3件を先に入れ、残り枠を予測候補とzero-hintへ配分する。
- `LearnedTransitionCandidateMapper` を追加し、既存Room学習entryから候補domainの `Candidate` へ変換する境界を作った。将来AzooKey互換のlearning memory形式へ差し替える場合も、IME service側の変更を抑えられる。
- `emoji_dict_E17.0.txt` には `🎵️` などの記号的に使われるentryも含まれるため、同梱emoji Dicdataから該当候補が取得できることをfixtureで確認した。
- `CandidateService` / `DefaultCandidateService` / `AzooKeyDictionaryAssetProvider` / `ImeCandidateEnvironment` を導入し、Hilt `CandidateModule` で注入できるようにした。
- `IMEService` の候補変換・post-process は `CandidateService` 経由に寄せ、`createCandidateServiceFactory()` を削除した。
- `AzooKeyBundledLoudsGoldenTest` で同梱本辞書の `しかい` 先頭5候補の順序回帰を固定した（DictionaryMock とは期待値が異なる）。
- 作業引き継ぎ用の詳細は [azookey-candidate-service-boundary.md](azookey-candidate-service-boundary.md) を参照。
- `ImeCandidateCoordinator` / `ImeCandidatePreferences` / `ImeCandidateRequestFactory` を導入し、`getSuggestionList*` / `EnglishKana` を coordinator 経由にした。
- `PostCommitPredictionFacade` を Hilt 注入化。確定後予測に LOUDS prefix 遷移候補をマージ開始。
- `CandidateRequest` に runtime session フラグ（候補選択中・変換中・直接入力）を追加し、ライブ変換 policy と接続した。
- 学習語を AzooKey 本家どおり **memory 辞書 lane** へ移行。`AzooKeyMemoryDictionarySourceProvider` / lattice `searchMemory`、`CandidateAssembler` は private 時に memory を gate、予測 lane は `systemPrediction` のみ。
- `ConversionSession` を本実装化。composing 追跡・確定語（yomi 保持）・Zenz rerank LRU・incremental 判定を `ImeCandidateCoordinator.conversionSession` で保持。確定後予測は coordinator 経由で `recordCommit` する。
- lattice **incremental cache**: 読み prefix 拡張時に LOUDS node を差分構築（`AzooKeyLoudsBackedDicdataStore.buildLatticeNodesIncremental` → `AzooKeyLatticeConverter` → `ImeCandidateEnvironment.latticeIncrementalState`）。
- **`ComposingText` Roman2Kana + セッション**: `ImeComposingTextSession` が QWERTY/物理キーボード入力ごとに `ComposingText` を更新。候補時は `ConversionSession.liveComposingText` にも記録。`ImeSuggestionOrchestrator` が IME から同期。
- **lattice-first / engine 整理**: `AzooKeyLatticePrimary` / `AzooKeyLoudsPrimary` では system 候補を engine から返さず、文節メタのみ `warmBunsetsuMetadataOnly`。
- **`ImeSuggestionOrchestrator`**: `IMEService` の `requestSuggestionResult` / composing 同期の第一段階抽出（[ime-service-refactoring-design.md](ime-service-refactoring-design.md) Phase 2/3）。
- **DictionaryMock top-N**: `AzooKeyDictionaryMockTopNComparisonTest` で lattice primary と LOUDS-only の先頭3件（司会・視界・死界）を本家 fixture と比較。
- **Room → memory.louds**: 学習確定のたびに `AzooKeyLearningMemoryRepository` が `persistSessionAndRoomEntries` で `memory.louds` / `memory0.loudstxt3` を書き出す。

## 重要ファイル

- `docs/azookey-candidate-service-boundary.md`
  - CandidateService / Coordinator / PostCommit の境界と次タスクのハンドオフメモ。
- `app/src/main/java/.../ime_service/candidate/ImeCandidateCoordinator.kt`
  - IME からの候補取得 orchestration。`ConversionSession` を保持。
- `app/src/main/java/.../converter/api/ConversionSession.kt`
  - AzooKey `ConversionSessionState` 相当（composing / 確定 / Zenz cache）。
- `app/src/main/java/.../ime_service/candidate/PostCommitPredictionFacade.kt`
  - 確定後予測（学習 + LOUDS + emoji）。
- `app/src/main/java/com/kazumaproject/markdownhelperkeyboard/converter/candidate/CandidateService.kt`
  - Hilt 注入可能な候補変換境界（`convert` / `postProcess`）。
- `app/src/main/java/com/kazumaproject/markdownhelperkeyboard/converter/candidate/DefaultCandidateService.kt`
  - `CandidateService` 実装。Factory 生成と repository / LOUDS / emoji asset 接続を所有。
- `app/src/main/java/com/kazumaproject/markdownhelperkeyboard/converter/candidate/AzooKeyDictionaryAssetProvider.kt`
  - APK 内 `louds/` / `azookey/emoji/` の lazy 読み込み。
- `app/src/main/java/com/kazumaproject/markdownhelperkeyboard/ime_service/di/CandidateModule.kt`
  - `CandidateService` の Hilt bind。
- `app/src/main/java/com/kazumaproject/markdownhelperkeyboard/ime_service/IMEService.kt`
  - まだ主要なorchestration point。候補本線は `CandidateService` + `buildImeCandidateEnvironment`。
  - privacy stateは `onStartInputView` で適用される。
  - Inline Autofill responseはcandidate adapterを更新する。
- `app/src/main/java/com/kazumaproject/markdownhelperkeyboard/converter/candidate/AzooKeyStyleCandidateRanker.kt`
  - 最初の純Kotlin ranking component。
- `app/src/main/java/com/kazumaproject/markdownhelperkeyboard/converter/candidate/AzooKeyStyleCandidateMixer.kt`
  - azooKey風のsource-aware candidate mixing stage。
- `app/src/main/java/com/kazumaproject/markdownhelperkeyboard/converter/candidate/AzooKeyStyleConversionModels.kt`
  - request optionと分離されたconversion resultのKotlin model。
- `app/src/main/java/com/kazumaproject/markdownhelperkeyboard/converter/candidate/CandidateRequest.kt`
  - 将来の `CandidateService` 向けの純Kotlin request snapshot。
- `app/src/main/java/com/kazumaproject/markdownhelperkeyboard/converter/candidate/CandidateSources.kt`
  - 候補ソースを明示的に束ねる純Kotlin model。
  - `systemPrediction` はLOUDS prefix検索や将来の辞書予測を通常system候補から分けて保持する。
- `app/src/main/java/com/kazumaproject/markdownhelperkeyboard/converter/candidate/CandidateAssembler.kt`
  - `CandidateRequest` と `CandidateSources` をAzooKey風のconversion resultへ組み立てる純Kotlin component。
- `app/src/main/java/com/kazumaproject/markdownhelperkeyboard/converter/candidate/AzooKeyStyleConverter.kt`
  - AzooKey Kotlin実装化のfacade。特殊候補provider注入とconversion result化をKotlin domain側へ寄せる入口。
- `app/src/main/java/com/kazumaproject/markdownhelperkeyboard/converter/candidate/AzooKeyStyleCandidateService.kt`
  - AzooKey互換変換サービスの入口。source取得、source merge、converter呼び出し、文節結果返却をまとめる。
- `app/src/main/java/com/kazumaproject/markdownhelperkeyboard/converter/candidate/AzooKeyStyleCandidateServiceFactory.kt`
  - auxiliary provider生成のfactory。learned/user dictionary/system user dictionary/user template/emoji dictionary/symbol dictionary/romaji/LOUDS dictionary source構成をconverter domain側で所有する。
- `app/src/main/java/com/kazumaproject/markdownhelperkeyboard/converter/candidate/SystemKanaKanjiEngineSourceFactory.kt`
  - system KanaKanji engine provider生成のfactory。Normal/Original/WithoutPredictionのengine呼び分けをconverter domain側で所有する。
- `app/src/main/java/com/kazumaproject/markdownhelperkeyboard/converter/candidate/CandidatePostProcessor.kt`
  - NG word filter、重複除去、候補順overrideをまとめるpost-process component。`IMEService` の表示直前処理を薄くする。
- `app/src/main/java/com/kazumaproject/markdownhelperkeyboard/converter/candidate/AzooKeyDictionaryEntry.kt`
  - AzooKey互換辞書entryのKotlin model。surface、reading、lcid/rcid、mid、word cost、source kind、metadataを保持する。mapper、connection ID resolver、Node mapperもここに置いている。
- `app/src/main/java/com/kazumaproject/markdownhelperkeyboard/converter/candidate/AzooKeyDictionaryEntryIndex.kt`
  - AzooKey互換entryの読み検索index。source kind filter、prefix検索、重複除去、word cost順を担う。
- `app/src/main/java/com/kazumaproject/markdownhelperkeyboard/converter/candidate/AzooKeyDictionaryShardName.kt`
  - AzooKey本家と同じ辞書shard filename escapeを担う。`.loudstxt3` / `.louds` / `.loudschars2` のfilename生成、escaped/raw filename stemからのidentifier復元もここへ集約している。
- `app/src/main/java/com/kazumaproject/markdownhelperkeyboard/converter/candidate/AzooKeyLoudstxt3BinaryParser.kt`
  - binary `.loudstxt3` file/payloadを互換entryへ展開するparser。
- `app/src/main/java/com/kazumaproject/markdownhelperkeyboard/converter/candidate/AzooKeyDictionaryShardLoader.kt`
  - text/binary `.loudstxt3` shard、`charID.chid`、`.louds`、`.loudschars2` を読み、互換entry/index/LOUDS lookupへ変換するloader。
- `app/src/main/java/com/kazumaproject/markdownhelperkeyboard/converter/candidate/AndroidAssetAzooKeyDictionaryShardLoader.kt`
  - Android assetからAzooKey辞書shardを読むためのfactory。`identifiers` から `AzooKeyLoudsDictionaryRegistry` を作る入口も持つ。`identifiers.txt` が無い場合はasset一覧から自動発見する。
- `app/src/main/java/com/kazumaproject/markdownhelperkeyboard/converter/candidate/AzooKeyLoudsAssetManifest.kt`
  - `.louds` / `.loudschars2` の揃ったasset filenameから利用可能identifierを発見し、安定順のmanifest textへ変換するhelper。
- `app/src/main/java/com/kazumaproject/markdownhelperkeyboard/converter/candidate/AzooKeyLoudsAssetValidation.kt`
  - `charID.chid`、`.louds`、`.loudschars2`、`.loudstxt3` shardの欠落をidentifier単位で検出するhelper。
- `app/src/main/java/com/kazumaproject/markdownhelperkeyboard/converter/candidate/AzooKeyCharIdMap.kt`
  - 本家 `charID.chid` のchar-to-ID map。読み文字列をLOUDS検索用のID列へ変換する。
- `app/src/main/java/com/kazumaproject/markdownhelperkeyboard/converter/candidate/AzooKeyLoudsTrie.kt`
  - 本家 `.louds` / `.loudschars2` binaryのKotlin reader。完全一致node index検索とprefix descendant検索を担う。word単位の0-count indexでselectを高速化している。
- `app/src/main/java/com/kazumaproject/markdownhelperkeyboard/converter/candidate/AzooKeyLoudsDictionaryLookup.kt`
  - LOUDS node indexから `.loudstxt3` shard/local slotを引き、該当entryだけを読む検索facade。
- `app/src/main/java/com/kazumaproject/markdownhelperkeyboard/converter/candidate/AzooKeyLoudsDictionaryRegistry.kt`
  - 読みの先頭文字からidentifierを選び、必要なlookupをlazy loadするregistry。ひらがな入力はカタカナへ正規化する。
- `app/src/main/java/com/kazumaproject/markdownhelperkeyboard/converter/candidate/AzooKeyLoudsIdentifierManifest.kt`
  - `louds/identifiers.txt` をparseする小さなmanifest parser。空白、タブ、カンマ、コメントを許す。
- `app/src/main/java/com/kazumaproject/markdownhelperkeyboard/converter/candidate/AzooKeyDictionaryImportSource.kt`
  - 複数identifier/shardをまとめて互換entry list/indexへ変換するimport source。
- `app/src/main/java/com/kazumaproject/markdownhelperkeyboard/converter/candidate/AzooKeyEmojiDictionaryTextParser.kt`
  - AzooKey本家のemoji TSV形式を互換entryへ変換するparser。variation emojiは `EmojiVariation` metadataとして保持する。
- `app/src/main/java/com/kazumaproject/markdownhelperkeyboard/converter/candidate/AzooKeyEmojiDicdataTextParser.kt`
  - AzooKey本家の `emoji_dict_E17.0.txt` を互換entryへ変換するparser。読みをひらがなへ正規化し、入力中prefix検索へ接続する。
- `app/src/main/java/com/kazumaproject/markdownhelperkeyboard/converter/candidate/AzooKeyEmojiDictionarySearch.kt`
  - TextReplacer TSVとDicdata emoji辞書をまとめたemoji辞書検索facade。入力中prefix検索と確定後完全一致検索を分ける。
- `app/src/main/java/com/kazumaproject/markdownhelperkeyboard/converter/candidate/AndroidAssetAzooKeyEmojiDictionaryLoader.kt`
  - Android assetからAzooKey emoji TextReplacer辞書を読むfactory。assetが無い場合はnullを返し、既存辞書fallbackを維持する。
- `app/src/main/java/com/kazumaproject/markdownhelperkeyboard/converter/candidate/AzooKeyDicdataElementTextParser.kt`
  - AzooKey本家 `DicdataElement` のテキスト行を互換entryへ変換するparser。LOUDS/asset取り込みの前段。
- `app/src/main/java/com/kazumaproject/markdownhelperkeyboard/converter/graph/GraphBuilder.kt`
  - 既存lattice graph生成の中心。通常system辞書経路からAzooKey互換entry境界へ寄せ始めている。
- `app/src/main/java/com/kazumaproject/markdownhelperkeyboard/converter/engine/KanaKanjiEngine.kt`
  - 既存辞書engine。emoji/symbol辞書候補をAzooKey互換entryとして返す `searchEmojiDictionaryEntries` / `searchSymbolDictionaryEntries` を追加した。
- `app/src/main/java/com/kazumaproject/markdownhelperkeyboard/system_user_dictionary/database/SystemUserDictionaryDao.kt`
  - system user dictionaryのprefix検索を追加した。互換source providerへつなぐ入口。
- `app/src/main/java/com/kazumaproject/markdownhelperkeyboard/converter/candidate/CandidateSourceProvider.kt`
  - source取得の契約。同期/非同期providerを合成して `CandidateSources` を作れる。
- `app/src/main/java/com/kazumaproject/markdownhelperkeyboard/converter/candidate/CandidateSourceAdapters.kt`
  - learned/user dictionary/system user dictionary/user template/emoji dictionary/symbol dictionary/romaji/LOUDS dictionaryのsource adapter。Android repositoryや辞書entry検索の結果をCandidate化する。
- `app/src/main/java/com/kazumaproject/markdownhelperkeyboard/converter/candidate/PostCommitPredictionComposer.kt`
  - 確定後予測を組み立てる純Kotlin component。AzooKey風にemoji/prediction/zero-hintの候補配分を扱う。
- `app/src/main/java/com/kazumaproject/markdownhelperkeyboard/converter/candidate/PostCommitEmojiDictionaryProvider.kt`
  - 確定後予測向けにemoji dictionary entryをCandidate化するadapter。今後IME本体の確定後予測検索へ接続する。
- `app/src/main/java/com/kazumaproject/markdownhelperkeyboard/converter/candidate/ZenzRerankFusionPolicy.kt`
  - Zenz/Zenzai候補評価の前段として、辞書スコアとZenz raw scoreを融合する純Kotlin policy。
- `app/src/main/java/com/kazumaproject/markdownhelperkeyboard/converter/candidate/ZenzaiCandidateEvaluationResult.kt`
  - AzooKey本家の `CandidateEvaluationResult` に対応するZenzai評価結果のdomain model。
- `app/src/main/java/com/kazumaproject/markdownhelperkeyboard/converter/candidate/AzooKeyRuntimeConversionPolicy.kt`
  - 学習、Zenzai、Zenzai predictive input、ライブ変換のruntime gate。private/suppressed sessionで個人化経路をまとめて止める。
- `app/src/main/java/com/kazumaproject/markdownhelperkeyboard/converter/candidate/CandidateType.kt`
  - candidate type numberとlaneの名前つきregistry。
- `app/src/main/java/com/kazumaproject/markdownhelperkeyboard/converter/candidate/SpecialCandidateProvider.kt`
  - AzooKey風の特殊候補provider registry。現在はCalendar、EmailAddress、Symbol、Unicode、Version、時刻、桁区切り数値、Typography providerを持つ。
- `app/src/test/java/com/kazumaproject/markdownhelperkeyboard/converter/candidate/`
  - ranking、mixing、request privacy、source bundle、assembler、post-commit predictionのテスト。
- `docs/ime-service-refactoring-design.md`
  - service分解の広い設計メモ。今後こちらも日本語のまま更新する。

## 次の現状タスク

1. `CandidateService` / `ImeCandidateCoordinator` / `PostCommitPredictionFacade` は導入済み。次は `ImePreferencesSnapshot` と `buildImeCandidatePreferences` の統合、辞書二系統の重複テスト。詳細は [azookey-candidate-service-boundary.md](azookey-candidate-service-boundary.md)。
2. 辞書をAzooKey互換へ寄せるためのschema/source adapterを作る。互換entry model、connection ID resolver、learned/user/template/system/system-user/token resultの初期写像、system user sourceのfactory接続、token辞書由来のGraphBuilder接続、ユーザー辞書・学習辞書のGraphBuilder接続、emoji/symbol source provider、factory接続、既存engine内蔵emoji/symbol辞書source接続、AzooKey DicdataElement行parser、binary `.loudstxt3` parser、Android asset shard loader、`.charID` parser、`.louds` / `.loudschars2` reader、LOUDS lookup、LOUDS dictionary source providerは完了。
   - system user dictionaryがengine辞書とauxiliary sourceの両方から来る場合の重複・優先順位をAzooKey本家fixtureに近いテストへ拡張する。
   - AzooKey本家 `.louds` / `.loudschars2` / `.charID` / `.loudstxt3` のassetをAndroidで読めるloaderへ落とし込む。基本reader/lookup/registry/manifest/factory/IME接続、manifest自動発見、完全一致/予測レーン分離、本辞書asset同梱、debug APKビルドは実装済み。次は本家と同じ読みで同じ候補順が得られることを継続差分テストする。
   - 実assetは `app/src/main/assets/louds/` に同梱済み。`identifiers.txt` は160 identifierを列挙している。未配置の場合も `.louds` / `.loudschars2` のペアから自動発見するため、初期取り込み時はmanifest無しでも起動できる。
   - 辞書本体の再導入は `./gradlew :app:prepareAzooKeyLoudsAssets -PazooKeyLoudsSourceDir=/path/to/louds` でコピー、最低限の欠落確認、manifest生成までまとめて行える。個別には `copyAzooKeyLoudsAssets` / `verifyAzooKeyLoudsAssets` / `generateAzooKeyLoudsManifest` を使う。次は本家辞書全体での候補差分テストと、full/lite別asset同梱方針を決める。
   - AzooKey本家のemoji TSV assetは `app/src/main/assets/azookey/emoji/emoji_all_E17.0.txt` に同梱済み。`AzooKeyEmojiDictionaryTextParser` / `AzooKeyEmojiDictionarySearch` / `AndroidAssetAzooKeyEmojiDictionaryLoader` 経由でIMEに接続済み。
- AzooKey本家 `emoji_dict_E17.0.txt` もDicdata sourceとして同梱・接続済み。次は本家fixtureとの差分を増やし、TextReplacer候補、通常emoji候補、特殊候補providerの重複順をさらに調整する。
- `AzooKeyPValue` を導入し、辞書entry、候補、学習遷移、Zenz rerank、確定後予測、emoji/symbol/user/system sourceの候補順をPValue基準へ移行した。既存実装と異なる場合はAzooKey風の「valueが大きい候補を優先」を採用する。
- LOUDS binary `.loudstxt3` のFloat32 value、Dicdata textのvalue/adjust、emoji Dicdata valueを保持し、`wordCost` は互換用の整数値として扱う。
- emoji入力中候補では本家Dicdata候補をTextReplacer fallbackより優先し、その中ではPValue順で並べる。重複surfaceがある場合もDicdata側を残す。
   - symbolも本家asset形式に寄せる場合は、emoji parserと同じく `AzooKeyDictionaryEntryIndex` へ落とし込むparserを追加する。
   - AzooKey本家fixtureと同じ読みから同じ主要候補が出ることをテストする。
3. `SpecialCandidateProvider` にAzooKey相当のproviderを追加する。
   - `EmailAddress`: 入力中のメール断片から保存済み/推定メール候補を出す。
   - `Calendar`: 実装済み。次は既存エンジン側の日付候補との重複順序を調整する。
   - `EmailAddress`: 実装済み。次はAzooKey本家の `isEnglishSentence` 判定との完全一致を確認する。
   - `TimeExpression`: 実装済み。次は既存エンジン側の日付・時刻候補との重複順序を調整する。
   - `CommaSeparatedNumber`: 実装済み。次は全角数字や既存辞書候補との重複順序を調整する。
   - `Typography`: 実装済み。次はAzooKeyと同じ発火条件、候補数、表示順を実機で確認する。
   - `Version`: 実装済み。`ばーじょん` / `バージョン` から `BuildConfig.VERSION_NAME` 由来のversion stringを出す。本家同様、候補は学習対象外。
   - `Symbol`: 基本記号provider、辞書由来sourceの入口、既存engine内蔵symbol辞書接続、emoji Dicdata由来の記号系fixtureは実装済み。次は本家assetとの差分fixtureを増やす。
   - `Emoji`: 辞書由来sourceの入口、既存engine内蔵emoji辞書接続、確定後予測用adapter、IME確定後予測ルート接続、AzooKey TSV parser、emoji TextReplacer asset同梱、`emoji_dict_E17.0.txt` Dicdata取り込み、Android asset loader、IME優先接続は実装済み。次は本家fixtureとの差分確認。
4. `CandidateAssembler` にAzooKeyの `processResult` に近い上位候補数制御、firstClause、special candidateの扱いを段階的に移す。`nBest` とspecial/firstClause別枠の初期対応は完了。
5. `CandidateSources` を候補取得関数の戻り値に近づけ、source取得、filter、order適用の分離を維持する。初期分離は完了。
6. private sessionでZenz/Zenzai personalization pathに入らないことをテストしやすい境界にする。AzooKey本家 `LearningType` / `zenzaiMode` / `experimentalZenzaiPredictiveInput` 相当をまとめるruntime policyと、IME側Zenz入口の個人化gate接続は完了。タップ候補学習判定は `AzooKeyCandidateLearningPolicy` に分離済み。次は `performZenzRequest` / `performZenzaiRequest` 内部のleft context取得もpolicy引数で明示する。
7. 確定後つながり予測は `PostCommitPredictionFacade` 経由。LOUDS prefix 遷移の初期マージは完了。次は本家 `getPredictionCandidates` / `getZeroHintPredictionCandidates` の条件合わせと zero-hint 辞書探索。
7. 確定後予測へ絵文字辞書sourceを接続済み。次は確定候補の読み/元DicdataElementを保持し、本家TextReplacerの `data.word` 検索にさらに近づける。
8. 確定後予測のscoring policyを追加し、frequency/recencyを入れられる形にする。学習済み遷移はAzooKey本家 `LearningMemory` の `PValue(-1 - 4/rubyLength - 3*d^3)` 形へ写像済みなので、次は永続カウント/recencyをその入力へ渡す。
9. Zenzaiのcandidate evaluation結果を `pass` / `fixRequired` / `wholeResult` 相当のdomain modelへ寄せる。domain model化は完了、次はalternative constraintsとcandidate生成方針を接続する。
10. 学習履歴をAzooKey互換entryとしてsource化する。まず既存 `LearnRepository` の読み出しを `AzooKeyDictionaryEntry` 化し、次に永続memory LOUDSへの移行を検討する。
11. ライブ変換の発火条件と候補更新条件をdomain policyへ移し、通常変換と同じ候補serviceを通るようにする。最初のpolicyと `shouldStartLiveConversion` 接続は完了。次は候補選択中/変換中/直接入力の状態をIME側からpolicyへ渡す。

## 今後のエージェント向けメモ

- worktreeには既存の未コミット変更がある可能性が高い。無関係な変更は戻さない。
- `IMEService.kt` は強く結合しているため、変更は小さく、テスト可能にする。
- 大きな移動の前に、純Kotlin helperとfocused testを増やす。
- raw candidate type numberはまだ多い。触る挙動に必要な範囲でだけ名前を増やす。
- 検索は `rg`、手編集は `apply_patch`、検証は触った範囲に一番狭いGradle taskを使う。
