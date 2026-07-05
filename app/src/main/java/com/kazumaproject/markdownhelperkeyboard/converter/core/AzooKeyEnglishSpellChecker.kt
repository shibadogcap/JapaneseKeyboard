package com.kazumaproject.markdownhelperkeyboard.converter.core

import android.content.Context
import android.view.textservice.SentenceSuggestionsInfo
import android.view.textservice.SpellCheckerSession
import android.view.textservice.SuggestionsInfo
import android.view.textservice.TextInfo
import android.view.textservice.TextServicesManager
import com.kazumaproject.markdownhelperkeyboard.converter.api.ComposingText
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyCid
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyDictionarySourceKind
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyMid
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CandidateType
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * Android 版 UITextChecker 相当。AzooKey [getForeignPredictionCandidate] と同じ value 減衰。
 */
@Singleton
class AzooKeyEnglishSpellChecker @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    suspend fun getForeignPredictionCandidates(
        inputData: ComposingText,
        language: String = "en-US",
        penalty: Float = -5f,
    ): List<Candidate> {
        if (language != "en-US") return emptyList()
        val ruby = inputData.convertTarget.filter { it.isLetter() }
        if (ruby.isEmpty() || !ruby.all { it.code in 65..90 || it.code in 97..122 }) {
            return emptyList()
        }
        val completions = fetchCompletions(ruby, language)
        if (completions.isEmpty()) return emptyList()

        val result = mutableListOf<Candidate>()
        val composingLength = ruby.length.toUByte()
        result += buildCandidate(ruby, ruby.uppercase(), penalty, composingLength)

        var value = -5f + penalty
        val delta = -10f / completions.size.coerceAtLeast(1)
        for (word in completions) {
            result += buildCandidate(word, word.uppercase(), value, composingLength)
            value += delta
        }
        return result
    }

    private fun buildCandidate(
        surface: String,
        reading: String,
        value: Float,
        length: UByte,
    ): Candidate {
        return Candidate(
            string = surface,
            type = CandidateType.NBEST,
            length = length,
            score = value.toInt(),
            value = value,
            yomi = reading,
            data = listOf(
                com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyDictionaryEntry(
                    surface = surface,
                    reading = reading,
                    leftId = AzooKeyCid.PROPER_NOUN,
                    rightId = AzooKeyCid.PROPER_NOUN,
                    mid = AzooKeyMid.GENERAL,
                    wordCost = (-value).toInt(),
                    value = value,
                    sourceKind = AzooKeyDictionarySourceKind.System,
                ),
            ),
        )
    }

    private suspend fun fetchCompletions(word: String, languageTag: String): List<String> {
        val manager = context.getSystemService(Context.TEXT_SERVICES_MANAGER_SERVICE) as? TextServicesManager
            ?: return emptyList()
        val locale = Locale.forLanguageTag(languageTag.replace('_', '-'))
        return suspendCoroutine { continuation ->
            var session: SpellCheckerSession? = null
            session = manager.newSpellCheckerSession(null, locale, object : SpellCheckerSession.SpellCheckerSessionListener {
                override fun onGetSuggestions(results: Array<out SuggestionsInfo>?) {
                    session?.close()
                    val words = results?.flatMap { info ->
                        if (info.suggestionsAttributes and SuggestionsInfo.RESULT_ATTR_IN_THE_DICTIONARY != 0) {
                            emptyList()
                        } else {
                            (0 until info.suggestionsCount)
                                .mapNotNull { index -> info.getSuggestionAt(index)?.takeIf { it.isNotBlank() } }
                        }
                    }.orEmpty().distinct()
                    continuation.resume(words)
                }

                override fun onGetSentenceSuggestions(results: Array<out SentenceSuggestionsInfo>?) {
                    session?.close()
                    continuation.resume(emptyList())
                }
            }, true)
            if (session == null) {
                continuation.resume(emptyList())
            } else {
                session.getSuggestions(TextInfo(word), 5)
            }
        }
    }
}
