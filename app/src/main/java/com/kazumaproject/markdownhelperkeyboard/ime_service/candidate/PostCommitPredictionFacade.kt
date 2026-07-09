package com.kazumaproject.markdownhelperkeyboard.ime_service.candidate

import com.kazumaproject.core.domain.extensions.hiraganaToKatakana
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyDictionaryAssetProvider
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyLearningMemoryRepository
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyPostCommitLoudsPredictor
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CandidateType
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.PostCommitEmojiDictionaryProvider
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.PostCommitPredictionService
import com.kazumaproject.markdownhelperkeyboard.converter.core.AzooKeyDicdataFacadeFactory
import com.kazumaproject.markdownhelperkeyboard.converter.engine.KanaKanjiEngine
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PostCommitPredictionFacade @Inject constructor(
    learningMemoryRepository: AzooKeyLearningMemoryRepository,
    azooKeyDictionaryAssets: AzooKeyDictionaryAssetProvider,
    dicdataFacadeFactory: AzooKeyDicdataFacadeFactory,
    kanaKanjiEngine: KanaKanjiEngine,
) {
    private val dicdataFacade = dicdataFacadeFactory.create { reading, limit ->
        learningMemoryRepository.prefixSearch(reading, limit)
    }

    private val service = PostCommitPredictionService(
        searchLearnedTransitions = { transitionReading, limit ->
            learningMemoryRepository.prefixSearch(
                reading = transitionReading.hiraganaToKatakana(),
                limit = limit,
            ).map { entry ->
                Candidate(
                    string = entry.surface,
                    type = CandidateType.LEARNED_HISTORY,
                    length = entry.surface.length.toUByte(),
                    score = entry.value.toInt(),
                    value = entry.value,
                    yomi = entry.reading,
                    leftId = entry.leftId?.toShort(),
                    rightId = entry.rightId?.toShort(),
                    data = listOf(entry),
                    lastMid = entry.mid,
                )
            }
        },
        searchLoudsTransitions = { transitionReading, limit, leftSideCandidate ->
            AzooKeyPostCommitLoudsPredictor.predictTransitions(
                leftSideCandidate = leftSideCandidate,
                facade = dicdataFacade,
                useMemory = true,
                limit = limit,
            )
        },
        searchZeroHintCandidates = { leftSideCandidate, limit ->
            AzooKeyPostCommitLoudsPredictor.predictZeroHint(
                leftSideCandidate = leftSideCandidate,
                facade = dicdataFacade,
                useMemory = true,
                limit = limit,
            )
        },
        emojiProvider = PostCommitEmojiDictionaryProvider(
            limit = 8,
            textReplacer = azooKeyDictionaryAssets.textReplacer,
            fallbackSearch = { surface, reading, limit ->
                azooKeyDictionaryAssets.emojiDictionarySearch?.searchPostCommit(
                    committedText = surface,
                    limit = limit,
                    committedReading = reading,
                )
                    ?: kanaKanjiEngine.searchEmojiDictionaryEntries(surface, limit)
            },
        ),
    )

    suspend fun predict(
        leftSideCandidate: Candidate,
        useLearnedTransitions: Boolean,
    ): List<Candidate> {
        return service.predict(
            leftSideCandidate = leftSideCandidate,
            useLearnedTransitions = useLearnedTransitions,
        )
    }
}
