package com.kazumaproject.markdownhelperkeyboard.ime_service.candidate

import com.kazumaproject.markdownhelperkeyboard.converter.api.AzooKeyRoman2KanaTransducer
import com.kazumaproject.markdownhelperkeyboard.converter.api.ComposingText
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.BunsetsuCandidateResult
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CandidateRequestMode
import com.kazumaproject.markdownhelperkeyboard.ime_service.romaji_kana.RomajiComposingSnapshot

/**
 * [IMEService] から候補リクエストと [ComposingText] セッション同期を切り出す（Phase 2/3）。
 */
class ImeSuggestionOrchestrator(
    private val coordinator: ImeCandidateCoordinator,
) {
    fun syncComposingSession(
        displayInput: String,
        useQwertyRoman2Kana: Boolean,
        roman2Kana: AzooKeyRoman2KanaTransducer,
        romajiSnapshot: RomajiComposingSnapshot?,
        qwertyRomajiRawInput: String?,
        zenkakuRomaji: Boolean,
    ) {
        coordinator.composingTextSession.configure(roman2Kana)
        if (!useQwertyRoman2Kana) {
            if (displayInput.isEmpty()) {
                coordinator.composingTextSession.reset()
            } else {
                coordinator.composingTextSession.applyDirectInput(displayInput)
            }
            return
        }
        if (displayInput.isEmpty()) {
            coordinator.composingTextSession.reset()
            return
        }
        when {
            !qwertyRomajiRawInput.isNullOrEmpty() -> {
                coordinator.composingTextSession.rebuildFromQwertyRawInput(
                    rawInput = qwertyRomajiRawInput,
                    zenkakuRomaji = zenkakuRomaji,
                    displayInput = displayInput,
                )
            }
            romajiSnapshot != null && romajiSnapshot.displayText == displayInput -> {
                coordinator.composingTextSession.applyPhysicalKeyboardSnapshot(
                    snapshot = romajiSnapshot,
                    displayInput = displayInput,
                )
            }
            else -> coordinator.composingTextSession.applyDirectInput(displayInput)
        }
    }

    fun composingTextForRequest(
        displayInput: String,
        useQwertyRoman2Kana: Boolean,
        roman2Kana: AzooKeyRoman2KanaTransducer,
        romajiSnapshot: RomajiComposingSnapshot?,
    ): ComposingText {
        coordinator.composingTextSession.configure(roman2Kana)
        return coordinator.composingTextSession.resolveForCandidateRequest(
            displayInput = displayInput,
            useQwertySession = useQwertyRoman2Kana,
            fallbackSnapshot = romajiSnapshot,
        )
    }

    /**
     * 候補リクエスト前に必ず [syncComposingSession] してから [ComposingText] を返す。
     * [IMEService] 以外から呼ぶ場合もこの API を使うこと。
     */
    fun composingTextForCandidateRequest(
        displayInput: String,
        useQwertyRoman2Kana: Boolean,
        roman2Kana: AzooKeyRoman2KanaTransducer,
        romajiSnapshot: RomajiComposingSnapshot?,
        qwertyRomajiRawInput: String?,
        zenkakuRomaji: Boolean,
    ): ComposingText {
        syncComposingSession(
            displayInput = displayInput,
            useQwertyRoman2Kana = useQwertyRoman2Kana,
            roman2Kana = roman2Kana,
            romajiSnapshot = romajiSnapshot,
            qwertyRomajiRawInput = qwertyRomajiRawInput,
            zenkakuRomaji = zenkakuRomaji,
        )
        return composingTextForRequest(
            displayInput = displayInput,
            useQwertyRoman2Kana = useQwertyRoman2Kana,
            roman2Kana = roman2Kana,
            romajiSnapshot = romajiSnapshot,
        )
    }

    suspend fun requestSuggestionResult(
        insertString: String,
        mode: CandidateRequestMode,
        preferences: ImeCandidatePreferences,
        zenz: ImeCandidateZenzContext?,
        composingText: ComposingText,
        roman2Kana: AzooKeyRoman2KanaTransducer,
        onBunsetsuMerged: suspend (
            input: String,
            candidates: List<Candidate>,
            bunsetsu: BunsetsuCandidateResult?,
        ) -> Unit,
    ): ImeCandidateSuggestResult = suggest(
        insertString = insertString,
        mode = mode,
        preferences = preferences,
        zenz = zenz,
        composingText = composingText,
        roman2Kana = roman2Kana,
        onBunsetsuMerged = onBunsetsuMerged,
    )

    suspend fun suggestionList(
        insertString: String,
        mode: CandidateRequestMode,
        preferences: ImeCandidatePreferences,
        zenz: ImeCandidateZenzContext?,
        composingText: ComposingText,
        roman2Kana: AzooKeyRoman2KanaTransducer,
        onBunsetsuMerged: suspend (
            input: String,
            candidates: List<Candidate>,
            bunsetsu: BunsetsuCandidateResult?,
        ) -> Unit,
    ): List<Candidate> {
        return requestSuggestionResult(
            insertString = insertString,
            mode = mode,
            preferences = preferences,
            zenz = zenz,
            composingText = composingText,
            roman2Kana = roman2Kana,
            onBunsetsuMerged = onBunsetsuMerged,
        ).candidates
    }

    suspend fun suggestionListWithoutPrediction(
        insertString: String,
        preferences: ImeCandidatePreferences,
        composingText: ComposingText,
        roman2Kana: AzooKeyRoman2KanaTransducer,
        onBunsetsuMerged: suspend (
            input: String,
            candidates: List<Candidate>,
            bunsetsu: BunsetsuCandidateResult?,
        ) -> Unit,
    ): List<Candidate> {
        return suggest(
            insertString = insertString,
            mode = CandidateRequestMode.WithoutPrediction,
            preferences = preferences,
            zenz = null,
            composingText = composingText,
            roman2Kana = roman2Kana,
            onBunsetsuMerged = onBunsetsuMerged,
        ).candidates
    }

    fun suggestionListEnglishKana(insertString: String): List<Candidate> {
        return coordinator.suggestEnglishKana(insertString)
    }

    suspend fun suggest(
        insertString: String,
        mode: CandidateRequestMode,
        preferences: ImeCandidatePreferences,
        zenz: ImeCandidateZenzContext?,
        composingText: ComposingText,
        roman2Kana: AzooKeyRoman2KanaTransducer,
        onBunsetsuMerged: suspend (
            input: String,
            candidates: List<Candidate>,
            bunsetsu: BunsetsuCandidateResult?,
        ) -> Unit,
    ): ImeCandidateSuggestResult {
        val result = coordinator.suggest(
            input = insertString,
            mode = mode,
            preferences = preferences,
            zenz = zenz,
            composingTextOverride = composingText,
            roman2Kana = roman2Kana,
        )
        onBunsetsuMerged(insertString, result.candidates, result.bunsetsuResult)
        return result
    }
}