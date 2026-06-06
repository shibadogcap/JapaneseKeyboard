# AzooKey 候補サービス境界（CandidateService）

最終更新: 2026-06-05（AzooKey 本家寄せ — lattice primary / preferences / memory）

## 目的

AzooKey 互換の候補生成を `IMEService` から切り出し、Hilt で注入できる境界を用意する。別エージェントはこのファイルと [azookey-gboard-roadmap.md](azookey-gboard-roadmap.md) を読んで着手する。

## 完了したこと

### Phase 0 — 回帰テスト

| テスト | 内容 |
|--------|------|
| `AzooKeyBundledLoudsGoldenTest` | 同梱 LOUDS で `しかい` → 先頭5件固定（本辞書） |
| `AzooKeyLoudsEngineDuplicateSurfaceTest` | LOUDS のみ経路で surface 重複なし |
| `AzooKeyLatticePrimaryGoldenTest` | lattice + cb で LOUDS primary と同じ top-5 |
| `AzooKeyDualPathSurfaceDedupTest` | 移行用 DualPath の surface dedup |

### Phase 1 — CandidateService + Coordinator

| 型 | 役割 |
|----|------|
| `CandidateService` / `DefaultCandidateService` | convert + postProcess（Hilt `@Singleton`） |
| `AzooKeyDictionaryAssetProvider` | LOUDS / emoji / cb 連接コストの lazy 読み込み |
| `ImeCandidatePreferences` | セッション設定の snapshot |
| `ImeCandidatePreferencesBuilder` | `ImePreferencesSnapshot` + ランタイム状態 → preferences |
| `ImeCandidateRequestFactory` | Request / environment 組み立て |
| `ImeCandidateCoordinator` | `getSuggestionList*` 相当の orchestration |
| `PostCommitPredictionFacade` | 確定後予測（学習 + LOUDS + zero-hint + emoji） |
| `CandidateModule` | Hilt bind |

`CandidateRequest` に `isCandidateSelectionActive` / `isConverting` / `isDirectInputMode` を追加し、`AzooKeyRuntimeConversionPolicy`（ライブ変換抑制など）へ接続済み。

`IMEService` は `candidateCoordinator.suggest(...)` と `postCommitPredictionFacade.predict(...)` を呼ぶ。辞書候補は `KanaKanjiConverter` → `CandidateService` 経路。

### 候補パイプライン — Lattice primary（本家 `convertToLattice` 近似）

> **注:** ここでの「Lattice primary」は AzooKey 候補変換フェーズの話。**IME リファクタ Phase 4a**（`KeyboardSurfaceCoordinator` / henkan UI）とは無関係。IME 側の進捗ラベルは [ime-service-refactoring-design.md](ime-service-refactoring-design.md) を参照。

| 項目 | 状態 |
|------|------|
| デフォルト方針 | LOUDS + `azookey/cb` あり → **`AzooKeyLatticePrimary`**（Viterbi + 連接コスト + mm） |
| `AzooKeyLoudsBackedDicdataStore` | prefix / exact / typo / memory を lattice node 化 |
| `AzooKeyLatticeDecoder` | cb + mm スコア、beam search |
| `LatticePrimarySystemDictionarySourceProvider` | system 主経路、文節のみ engine |
| memory 二重供給防止 | lattice primary 時は auxiliary memory off、engine の `learnRepository` も off |
| engine 学習 promotion | lattice 時は Room 学習を engine 内に渡さない（memory は LOUDS trie のみ） |

### 学習（AzooKey LearningManager 近似）

| 項目 | 状態 |
|------|------|
| 読み取り | `LearningType.OnlyOutput` + `shouldReadMemoryDictionary` → lattice / LOUDS memory |
| 書き込み | `AzooKeyLearningMemoryRepository.commitTappedCandidate` / `commitTransition`（Room + session trie） |
| 予測 lane | `CandidateAssembler` は **systemPrediction のみ**。学習語は `CandidateSources.memory` → main / lattice |
| `ConversionSession` | composing・確定語・Zenz rerank LRU・**lattice incremental cache** |

### Phase 2 — 確定後予測

- 学習遷移 + LOUDS prefix 遷移（**yomi** で LOUDS、本家 `totalRuby` 相当）
- zero-hint: 確定候補の **yomi** のみ（`shouldSearchZeroHint`）
- emoji: surface + reading の両方で TextReplacer / Dicdata 検索

### Zenz / Zenzai

- `ZenzConversionService` + `ImeCandidateCoordinator`（生成 / rerank / Zenzai / live mix）
- `PASS:score\|ALT:ratio:prefix` パース + `ZenzaiAlternativeConstraintPolicy`

## アーキテクチャ（現在）

```text
onStartInput → ImePreferencesSnapshot → applyImePreferences / cachedPreferences
       │
       ▼
IMEService.buildImeCandidatePreferences()
  └─ ImeCandidatePreferencesBuilder(snapshot, ImeCandidateRuntimeSession)
       │
       ▼
ImeCandidateCoordinator.suggest()
  └─ KanaKanjiConverter.requestCandidatesPostProcessed()
        └─ DefaultCandidateService
              ├─ resolveSystemDictionarySourcePolicy()
              │     └─ AzooKeyLatticePrimary (LOUDS+cb 同梱時)
              ├─ LatticePrimarySystemDictionarySourceProvider
              │     └─ AzooKeyLatticeConverter + AzooKeyLoudsBackedDicdataStore
              └─ AzooKeyStyleCandidateServiceFactory (memory auxiliary: lattice 時 off)
       │
       ▼
CandidateAssembler → AzooKeyStyleCandidateMixer (systemPrediction のみ予測 lane)

確定後:
  PostCommitPredictionFacade
    ├─ LearnRepository 遷移
    ├─ LOUDS prefix（確定 yomi）
    ├─ LOUDS zero-hint（yomi gate）
    └─ emoji（surface + yomi）
```

## テスト

```bash
./gradlew :app:testFullStandardDebugUnitTest \
  --tests 'com.kazumaproject.markdownhelperkeyboard.converter.candidate.*' \
  --tests 'com.kazumaproject.markdownhelperkeyboard.converter.lattice.*' \
  --tests 'com.kazumaproject.markdownhelperkeyboard.ime_service.candidate.*' \
  --tests 'com.kazumaproject.markdownhelperkeyboard.converter.zenz.*'
```

## 実機常用前チェック

最終確認: 2026-06-05 / ADB device `0005324AH001339` / debug APK `app-full-standard-debug.apk`

### ADB導線

```bash
./gradlew :app:assembleFullStandardDebug
adb install -r app/build/outputs/apk/fullStandard/debug/app-full-standard-debug.apk
adb shell ime list -s
adb shell ime enable com.shibadogcap.himawarikeyboard/com.kazumaproject.markdownhelperkeyboard.ime_service.IMEService
adb shell ime set com.shibadogcap.himawarikeyboard/com.kazumaproject.markdownhelperkeyboard.ime_service.IMEService
adb logcat -d -v time | rg -i 'FATAL EXCEPTION|AndroidRuntime|com.kazumaproject|markdownhelper|IllegalStateException|RuntimeException|InflateException|NavController|Fragment|crash|Exception'
```

### 実測結果

| 項目 | 状態 | メモ |
|------|------|------|
| 主要AzooKey変換系unit test | PASS | `converter.api` / `converter.candidate` / `converter.lattice` / `ime_service.candidate` / `converter.zenz` |
| 入力UI周辺unit test | PASS | `ime_service.input` / `ime_service.ui` / `ime_service.editor` |
| debug APK build | PASS | `:app:assembleFullStandardDebug` |
| ADB install | PASS | `adb install -r ...` が `Success` |
| IME enable/set | PASS | `ime enable` は既に有効、`ime set` 成功。2026-06-05の修正版APKでも再set済み |
| 現在IME確認 | PASS | `mSelectedMethodId` / `mCurImeId` が `com.shibadogcap.himawarikeyboard/...IMEService` |
| 設定画面起動 | PASS | `LaunchableMainActivity` 起動、対象アプリ由来のfatalなし |
| IME service起動 | PASS | `ActivityManager` が `IMEService` process startを記録 |
| TenKey入力イベント | PASS（クラッシュなし） | `TenKey: ACTION_UP` が複数記録。入力内容の視覚確認は別途 |
| logcat fatal確認 | PASS | 対象アプリ/SystemUI/RemoteInput由来の `FATAL EXCEPTION` / `AndroidRuntime` なし |
| QWERTY入力 | 修正済み / 要再確認 | 英語モードでも英数カナ候補ルートを明示的に呼ぶ。API候補プールを最低24件へ拡張。実機でroman2kana、space、enter、backspaceを手動確認する |
| 候補タブ展開 | 未確認 | toolbar/candidate stripの展開・復帰を手動確認する |
| Floating入力 | 未確認 | 通常TenKey同等の入力、候補表示、候補クリックを確認する |
| Password/Bitwarden | 修正済み / 要再確認 | password欄で個人化抑制は維持。Inline Autofill requestをHeliBoard/AOSP寄せで3spec + 最大6件へ変更。inline chip表示時にIME高さを増やさない。adapterはinline chipを差分更新し、inflateサイズ/null viewログを出す |
| 物理キーボード | 修正済み / 要再確認 | 起動時replay既定値を「物理KBなし」に固定。外部・非virtual・alphabeticデバイスのみ物理KB扱い。仮想/タッチ由来イベントからのtrue emitを抑制 |
| Zenz/live実機 | 修正済み / 要再確認 | 候補更新完了を空候補でも通知し、入力変更時はZenz待機を解除。live変換はAPI第一候補を最新入力ごとに適用し、tail/変換中/候補選択中に抑制 |
| つながり予測 | 修正済み / 要再確認 | 確定前の読みをpost-commitへ渡す。IMEは `KanaKanjiConverter.requestPostCompositionPredictionCandidates` 経由で取得する。人工助詞fallbackと通常fallback readingsは通常経路から削除し、学習履歴/システム予測/zero-hint/emoji suffix のtypeを分離した |
| 入力レイテンシ | 修正済み / 要再確認 | 通常入力の固定delayを除去。候補request/applyに `ImeLatency` debug logを追加 |
| 途中編集 | 修正済み / 要再確認 | composing中カーソル移動ではhead/tailを保持し、tail込みの `ComposingText` cursor位置を候補APIへ渡す。tailありではlive変換を抑制し、候補APIはカーソル前prefixで通常候補を出す |
| 通知直接返信 | 修正済み / 要再確認 | `com.android.systemui` RemoteInputではInline Suggestions request/responseとIME高さ拡張を抑制 |
| AI/履歴候補デザイン | 修正済み / 要再確認 | AI/Gemma action候補を通常候補と同じ40dpチップ体系へ寄せた |
| 候補数 / 部分列候補 | 修正済み / 要再確認 | `nBest` を表示件数上限として扱わず、入力長に応じて探索幅を最低24/48/64、最大80へ拡張。AzooKey `processResult` 型の「全文top5 + 予測少量 + special + firstClause + word候補」へ変更。LOUDS/lattice nodeから部分列word候補を通常候補列へ流す |
| ポップアップ形状 | 修正済み / 要再確認 | TenKey / custom flick / 設定preview の丸形状を角丸四角へ統一 |

### 次に触るべき残差

1. 実機で TenKey/QWERTY の高速連打・連続delete・候補表示・確定後予測を確認し、候補が全文top5で止まらず firstClause / word 候補まで出ること、`ImeLatency` が極端に大きい箇所がないことを確認する。
2. 予測タブと履歴タブを確認し、zero-hint助詞・LOUDS予測・emoji suffix が `LEARNED_HISTORY` として表示されないことを確認する。
3. live変換が通常欄でAPI第一候補を追従すること、途中編集やtailありでは暴れず通常候補だけ更新されることを確認する。
4. Bitwarden / Android Inline Autofill のチップ表示・クリックを確認し、表示できてもクリックできない場合は `SuggestionAdapter` の inline inflateログと autofill host 境界を追う。
5. 通知直接返信で SystemUI が落ちないことを確認し、必要なら `SystemUI RemoteInput` セッションでは候補バーもさらに軽量化する。
6. 候補タブ visibility と toolbar展開を実機で確認し、固着する場合は `KeyboardSurfaceCoordinator.routeCandidateDisplay` と既存toolbar状態の同期を直す。
7. Floating の `handleTap` / `handleFlick` / `handleLongPress` ペアを `TapFlickInputBridge` / surface APIへ寄せ、通常UIとの差分を小さくする。

## まだ IMEService に残っているもの

- キー入力 / composing 表示 / `applyFirstSuggestion` / `applyLiveConversionIfNeeded`
- `buildImeCandidateZenzContext()` — Zenz 左文脈解決（`ImeZenzContextBuilder` は preferences 組み立て済み）
- `EditorGateway.connectionProvider` 用の `currentInputConnection` 参照 1 箇所のみ
- **IME Phase 4b 残差:** `handleTap` / `handleFlick` / `handleLongPress` の Floating ペア、QWERTY enter/space、候補タブ visibility（`handleTapAndFlick*` サイドキー本体は `TapFlickInputBridge` 済）
- **IME Phase 5 残差:** 物理 KB 時の View host 実装（ロジックは `PhysicalKeyboardUiEffectHandler` 済）

## 次の作業（優先順）

1. ~~**QWERTY Roman2Kana + ComposingText セッション**~~ → **済**: `ImeComposingTextSession` / `ConversionSession.liveComposingText` / `ImeSuggestionOrchestrator.syncComposingSession`
2. ~~**lattice-primary 時の engine 重複**~~ → **済**: system 候補は lattice/LOUDS のみ。engine は `warmBunsetsuMetadataOnly`
3. ~~**ImeSuggestionOrchestrator**~~ → **済**: `IMEService` から候補リクエスト・composing 同期を委譲（Phase 2/3 第一段）
4. ~~**post-commit**~~ → **済**: yomi ベース LOUDS 遷移 / zero-hint gate / 学習遷移 prefs / converter API 経由化 / 学習なし時の辞書API fallback
5. ~~**学習 LOUDS 差分更新**~~ → **済**: `appendPersistedEntries` + `persistTrieDelta`
6. **ImePreferencesSnapshot 残差分** — `buildImeCandidatePreferences` の `zenzaiEnabled` を snapshot 化済み。Gboard UX / 一部 runtime `var` は残す
7. ~~**ImeSuggestionOrchestrator 拡張**~~ → **済**: `suggestionList*` / `ImeZenzContextBuilder` / composing 同期
8. ~~**ImeCandidatePresentationCoordinator**~~ → **部分完了（IME Phase 2）**: 表示パイプライン + 文節 merge 状態 + tail フィルタ（候補タブ切替・一部 live 変換は IME 残存）
9. ~~**InputActionDispatcher**~~ → **済（Phase 6 スケルトン）**: `onKeyDown` モード振り分け
10. ~~**EditorGateway**~~ → **済（Phase 3）**: 装飾 composing 含む IC 集約。直参照 1 箇所
11. ~~**KeyboardSurfaceCoordinator**~~ → **部分完了（IME Phase 4a/4b）**: henkan UI + 候補表示振り分け + `TapFlickInputBridge`（tap/flick 文字・long press は残）
12. ~~**HardwareKeyboardCoordinator**~~ → **部分完了（IME Phase 5）**: 接続副作用 + floating 候補追従 + `physicalKeyboardEnable` 解釈・emit + `PhysicalKeyboardUiEffectHandler`（collect UI ポリシー）
13. ~~**KeyboardModeController / InputActionDispatcher 拡張**~~ → **部分完了（IME Phase 6）**: session モード + TenKey ジェスチャ dispatch

## 重要ファイル

| パス | 説明 |
|------|------|
| `ime_service/candidate/ImeCandidatePreferencesBuilder.kt` | snapshot → preferences |
| `converter/lattice/AzooKeyLatticeConverter.kt` | lattice n-best |
| `converter/candidate/LatticePrimarySystemDictionarySourceProvider.kt` | system 主経路 |
| `converter/candidate/AzooKeyLearningMemoryRepository.kt` | 学習読み書き |
| `converter/candidate/SystemDictionarySourcePolicy.kt` | 自動 policy 解決 |
| `ime_service/candidate/PostCommitPredictionFacade.kt` | 確定後予測 |

## 注意

- 同梱 LOUDS の `しかい` 先頭候補は DictionaryMock と異なる（`AzooKeyBundledLoudsGoldenTest`）。
- `DualPath` は明示 policy のみ（移行・回帰用）。
