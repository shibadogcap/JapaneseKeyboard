package com.kazumaproject.markdownhelperkeyboard.converter.core

import com.kazumaproject.core.domain.extensions.hiraganaToKatakana
import com.kazumaproject.markdownhelperkeyboard.converter.api.ComposingText
import com.kazumaproject.markdownhelperkeyboard.converter.api.ConvertRequestOptions
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.ZenzaiCandidateEvaluationResult
import com.kazumaproject.markdownhelperkeyboard.converter.zenz.ZenzEnginePort
import com.kazumaproject.markdownhelperkeyboard.converter.zenz.ZenzPromptContext
import com.kazumaproject.markdownhelperkeyboard.converter.zenz.toZenzPromptContext

internal object AzooKeyZenzaiCandidateEvaluator {
    private const val ALIGNMENT_SEPARATOR = "\uEE08"

    suspend fun evaluate(
        zenzEngine: ZenzEnginePort,
        prompt: ZenzPromptContext,
        inputData: ComposingText,
        candidate: Candidate,
        prefixConstraint: AzooKeyPrefixConstraint,
        requestRichCandidates: Boolean,
    ): ZenzaiCandidateEvaluationResult {
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
        val unusedConstraint = prefixConstraint
        val raw = zenzEngine.candidateEvaluate(
            prompt = prompt,
            inputKatakana = inputKatakana,
            candidate = candidateForEval,
            requestRichCandidates = requestRichCandidates,
        )
        return ZenzaiCandidateEvaluationResult.parse(raw)
    }

    suspend fun evaluate(
        zenzEngine: ZenzEnginePort,
        options: ConvertRequestOptions,
        leftContext: String,
        inputData: ComposingText,
        candidate: Candidate,
        prefixConstraint: AzooKeyPrefixConstraint,
        requestRichCandidates: Boolean,
    ): ZenzaiCandidateEvaluationResult {
        return evaluate(
            zenzEngine = zenzEngine,
            prompt = options.toZenzPromptContext(leftContext),
            inputData = inputData,
            candidate = candidate,
            prefixConstraint = prefixConstraint,
            requestRichCandidates = requestRichCandidates,
        )
    }

    private fun inputWithAlignmentSeparator(input: String, cursorPosition: Int?): String {
        if (cursorPosition == null || cursorPosition !in 0 until input.length) {
            return input
        }
        return input.substring(0, cursorPosition) + ALIGNMENT_SEPARATOR + input.substring(cursorPosition)
    }
}
