package com.kazumaproject.markdownhelperkeyboard.ime_service.candidate

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyDictionaryAssetProvider
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CandidateType
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.LearnedTransitionCandidateMapper
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.LearnedTransitionRecord
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.PostCommitEmojiDictionaryProvider
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.PostCommitPredictionService
import com.kazumaproject.markdownhelperkeyboard.converter.engine.KanaKanjiEngine
import com.kazumaproject.markdownhelperkeyboard.repository.LearnRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PostCommitPredictionFacade @Inject constructor(
    learnRepository: LearnRepository,
    azooKeyDictionaryAssets: AzooKeyDictionaryAssetProvider,
    kanaKanjiEngine: KanaKanjiEngine,
) {
    private val service = PostCommitPredictionService(
        searchLearnedTransitions = { input, limit ->
            learnRepository.predictiveSearchByInput(input, limit).map {
                LearnedTransitionCandidateMapper.toCandidate(
                    LearnedTransitionRecord(
                        input = it.input,
                        output = it.out,
                        score = it.score.toInt(),
                        leftId = it.leftId,
                        rightId = it.rightId,
                    )
                )
            }
        },
        searchLoudsTransitions = { transitionReading, limit ->
            azooKeyDictionaryAssets.loudsDictionaryRegistry
                ?.prefixEntries(
                    reading = transitionReading,
                    maxDepth = 4,
                    maxCount = limit,
                )
                ?.map { entry -> entry.toCandidate(type = CandidateType.POST_COMMIT_PREDICTION) }
                ?: emptyList()
        },
        searchZeroHintCandidates = { leftSideCandidate, limit ->
            val reading = leftSideCandidate.yomi?.takeIf { it.isNotBlank() }
                ?: leftSideCandidate.string
            azooKeyDictionaryAssets.loudsDictionaryRegistry
                ?.prefixEntries(
                    reading = reading,
                    maxDepth = 4,
                    maxCount = limit,
                )
                ?.map { entry -> entry.toCandidate(type = CandidateType.ZERO_HINT_PREDICTION) }
                ?: emptyList()
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
