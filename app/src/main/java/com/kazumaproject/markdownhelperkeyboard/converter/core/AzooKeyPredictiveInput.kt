package com.kazumaproject.markdownhelperkeyboard.converter.core

import com.kazumaproject.core.domain.extensions.hiraganaToKatakana
import com.kazumaproject.core.domain.extensions.katakanaToHiragana
import com.kazumaproject.markdownhelperkeyboard.converter.api.AzooKeyRoman2KanaTransducer
import com.kazumaproject.markdownhelperkeyboard.converter.api.ComposingCount
import com.kazumaproject.markdownhelperkeyboard.converter.api.ComposingText
import com.kazumaproject.markdownhelperkeyboard.converter.api.InputStyle
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyStyleZenzaiMode
import com.kazumaproject.markdownhelperkeyboard.converter.api.prefixToCursorPosition
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyCid
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyDictionarySourceKind
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyDictionaryEntry
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyMid
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CandidateType
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.applyAppropriateActions
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.parseTemplate

data class PredictiveInputSource(
    val baseConvertTarget: String,
    val possibleNexts: List<String>,
    val droppedSuffixCount: Int,
)

object AzooKeyPredictiveInputResolver {
    fun resolve(
        composingText: ComposingText,
        roman2Kana: AzooKeyRoman2KanaTransducer = AzooKeyRoman2KanaTransducer.Identity,
    ): PredictiveInputSource {
        val lastStyle = composingText.input.lastOrNull()?.inputStyle ?: InputStyle.Direct
        if (lastStyle != InputStyle.Roman2Kana) {
            return PredictiveInputSource(
                baseConvertTarget = composingText.convertTarget,
                possibleNexts = emptyList(),
                droppedSuffixCount = 0,
            )
        }
        val romanSuffix = composingText.convertTarget.takeLastWhile { it.isAsciiLetter() }
        if (romanSuffix.isEmpty()) {
            return PredictiveInputSource(
                baseConvertTarget = composingText.convertTarget,
                possibleNexts = emptyList(),
                droppedSuffixCount = 0,
            )
        }
        val possibleNexts = roman2Kana.possibleNexts(romanSuffix)
        if (possibleNexts.isEmpty()) {
            return PredictiveInputSource(
                baseConvertTarget = composingText.convertTarget,
                possibleNexts = emptyList(),
                droppedSuffixCount = 0,
            )
        }
        val base = composingText.convertTarget.dropLast(romanSuffix.length)
        return PredictiveInputSource(
            baseConvertTarget = base,
            possibleNexts = possibleNexts,
            droppedSuffixCount = romanSuffix.length,
        )
    }

    private fun Char.isAsciiLetter(): Boolean = this in 'a'..'z' || this in 'A'..'Z'
}

data class StablePredictionCandidateCacheEntry(
    val originalConvertTarget: String,
    val suffixCount: Int,
    val candidates: List<Candidate>,
) {
    fun compatibleCandidates(
        currentConvertTarget: String,
        baseConvertTarget: String,
        possibleNexts: List<String>,
    ): List<Candidate> {
        val droppedSuffixCount = suffixCount.coerceAtLeast(0).coerceAtMost(originalConvertTarget.length)
        val cachedBase = originalConvertTarget.dropLast(droppedSuffixCount)
        if (!baseConvertTarget.startsWith(cachedBase)) return emptyList()

        val compatiblePrefixes = if (possibleNexts.isEmpty()) {
            listOf(currentConvertTarget.hiraganaToKatakana())
        } else {
            possibleNexts.map { (baseConvertTarget + it).hiraganaToKatakana() }
        }
        val currentRuby = currentConvertTarget.hiraganaToKatakana()
        return candidates.mapNotNull { candidate ->
            val candidateRuby = if (candidate.data.isEmpty()) {
                candidate.string.hiraganaToKatakana()
            } else {
                candidate.data.joinToString("") { it.reading }
            }
            if (candidate.string.isEmpty() || candidateRuby == currentRuby) return@mapNotNull null
            if (!compatiblePrefixes.any { prefix -> candidateRuby.startsWith(prefix) }) return@mapNotNull null
            candidate.copy(
                length = currentConvertTarget.length.toUByte(),
                composingCount = ComposingCount.SurfaceCount(currentConvertTarget.length),
            )
        }
    }
}

data class PredictiveInputCacheContext(
    val leftSideContext: String,
    val inputStyle: InputStyle,
    val zenzaiMode: AzooKeyStyleZenzaiMode,
    val zenzProfile: String = "",
    val zenzTopic: String = "",
    val zenzStyle: String = "",
    val zenzPreference: String = "",
    val zenzRightSideContext: String = "",
)

data class PredictiveInputCacheEntry(
    val context: PredictiveInputCacheContext,
    val originalConvertTarget: String,
    val suffixCount: Int,
    val predictedText: String,
) {
    fun remainingPrediction(currentConvertTarget: String, count: Int): String? {
        if (count <= 0) return null
        val droppedSuffixCount = suffixCount.coerceAtLeast(0).coerceAtMost(originalConvertTarget.length)
        val baseConvertTarget = originalConvertTarget.dropLast(droppedSuffixCount)
        if (!currentConvertTarget.startsWith(baseConvertTarget)) return null

        val consumedInsertText = currentConvertTarget.drop(baseConvertTarget.length)
        val predictedInsertText = if (context.inputStyle == InputStyle.Roman2Kana) {
            predictedText.katakanaToHiragana()
        } else {
            predictedText
        }
        if (!predictedInsertText.startsWith(consumedInsertText)) return null
        val consumedCount = consumedInsertText.length
        if (consumedCount >= predictedText.length) return null
        return predictedText.drop(consumedCount).take(count)
    }
}

/** AzooKey [InputManager.enter](https://github.com/azooKey/azooKey) の raw kana 確定候補。 */
object AzooKeyEnterFallbackCandidate {
    fun build(
        composingText: ComposingText,
        roman2Kana: AzooKeyRoman2KanaTransducer = AzooKeyRoman2KanaTransducer.Identity,
    ): Candidate {
        val prefix = composingText.prefixToCursorPosition(roman2Kana)
        val reading = prefix.convertTarget.hiraganaToKatakana()
        val entry = AzooKeyDictionaryEntry(
            surface = prefix.convertTarget,
            reading = reading,
            leftId = AzooKeyCid.PROPER_NOUN,
            rightId = AzooKeyCid.PROPER_NOUN,
            mid = AzooKeyMid.GENERAL,
            wordCost = 18,
            value = -18f,
            sourceKind = AzooKeyDictionarySourceKind.System,
        )
        return Candidate(
            string = prefix.convertTarget,
            type = CandidateType.NBEST,
            length = prefix.convertTarget.length.toUByte(),
            score = -18,
            value = -18f,
            yomi = reading,
            composingCount = ComposingCount.InputCount(prefix.input.size),
            lastMid = AzooKeyMid.GENERAL,
            data = listOf(entry),
        ).applyAppropriateActions().parseTemplate()
    }
}
