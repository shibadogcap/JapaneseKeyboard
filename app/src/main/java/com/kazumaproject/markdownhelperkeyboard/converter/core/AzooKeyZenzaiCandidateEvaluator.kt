package com.kazumaproject.markdownhelperkeyboard.converter.core

import com.kazumaproject.core.domain.extensions.hiraganaToKatakana
import com.kazumaproject.markdownhelperkeyboard.converter.api.ComposingText
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.ZenzaiCandidateEvaluationResult
import com.kazumaproject.markdownhelperkeyboard.converter.zenz.ZenzEnginePort

internal object AzooKeyZenzaiCandidateEvaluator {
    private const val ALIGNMENT_SEPARATOR = "\uEE08"

    suspend fun evaluate(
        zenzEngine: ZenzEnginePort,
        profile: String,
        leftContext: String,
        inputData: ComposingText,
        candidate: Candidate,
        prefixConstraint: AzooKeyPrefixConstraint,
        requestRichCandidates: Boolean,
    ): ZenzaiCandidateEvaluationResult {
        if (profile.isBlank()) return ZenzaiCandidateEvaluationResult.Error
        val cursorPosition = if (inputData.isAtEndIndex) null else inputData.convertTargetCursorPosition
        val inputKatakana = inputWithAlignmentSeparator(
            inputData.convertTarget.hiraganaToKatakana(),
            cursorPosition,
        )
        val candidateForEval = if (cursorPosition != null &&
            cursorPosition in 0 until inputData.convertTarget.length
        ) {
            candidate.string + ALIGNMENT_SEPARATOR
        } else {
            candidate.string
        }
        @Suppress("UNUSED_PARAMETER")
        val unusedRich = requestRichCandidates
        @Suppress("UNUSED_PARAMETER")
        val unusedConstraint = prefixConstraint
        val raw = zenzEngine.candidateEvaluate(
            profile = profile,
            leftContext = leftContext,
            inputKatakana = inputKatakana,
            candidate = candidateForEval,
        )
        return ZenzaiCandidateEvaluationResult.parse(raw)
    }

    private fun inputWithAlignmentSeparator(input: String, cursorPosition: Int?): String {
        if (cursorPosition == null || cursorPosition !in 0 until input.length) {
            return input
        }
        return input.substring(0, cursorPosition) + ALIGNMENT_SEPARATOR + input.substring(cursorPosition)
    }
}
