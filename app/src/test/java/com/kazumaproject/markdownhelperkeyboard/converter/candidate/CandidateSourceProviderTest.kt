package com.kazumaproject.markdownhelperkeyboard.converter.candidate

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlinx.coroutines.test.runTest

class CandidateSourceProviderTest {
    @Test
    fun compositeProviderMergesSourcesInProviderOrder() {
        val provider = CompositeCandidateSourceProvider(
            listOf(
                CandidateSourceProvider {
                    CandidateSources(
                        memory = listOf(candidate("学習1", CandidateType.LEARNED_HISTORY, score = 10)),
                        system = listOf(candidate("変換1", CandidateType.NBEST, score = 20))
                    )
                },
                CandidateSourceProvider {
                    CandidateSources(
                        memory = listOf(candidate("学習2", CandidateType.LEARNED_HISTORY, score = 30)),
                        userDictionary = listOf(candidate("辞書", CandidateType.USER_DICTIONARY, score = 40))
                    )
                }
            )
        )

        val sources = provider.provide(baseRequest())

        assertEquals(listOf("学習1", "学習2"), sources.memory.map { it.string })
        assertEquals(listOf("辞書", "変換1", "学習1", "学習2"), sources.mainCandidates.map { it.string })
    }

    @Test
    fun converterCanUseSourceProviderDirectly() {
        val result = AzooKeyStyleConverter.convert(
            request = baseRequest(input = "u3042", specialCandidateProviders = listOf(UnicodeSpecialCandidateProvider)),
            sourceProvider = CandidateSourceProvider {
                CandidateSources(system = listOf(candidate("変換", CandidateType.NBEST, score = 10)))
            }
        )

        assertEquals(listOf("変換", "あ"), result.mainResults.map { it.string })
    }

    @Test
    fun compositeSuspendProviderMergesSourcesInProviderOrder() = runTest {
        val provider = CompositeSuspendCandidateSourceProvider(
            listOf(
                SuspendCandidateSourceProvider {
                    CandidateSources(
                        userTemplate = listOf(candidate("定型文", CandidateType.USER_DICTIONARY, score = 50)),
                        system = listOf(candidate("変換", CandidateType.NBEST, score = 10))
                    )
                },
                SuspendCandidateSourceProvider {
                    CandidateSources(
                        romaji = listOf(candidate("azookey", CandidateType.HIRAGANA, score = 5)),
                        special = listOf(candidate("既存特殊", CandidateType.SPECIAL, score = 1))
                    )
                }
            )
        )

        val sources = provider.provide(baseRequest())

        assertEquals(listOf("定型文", "変換", "azookey"), sources.mainCandidates.map { it.string })
        assertEquals(listOf("既存特殊"), sources.special.map { it.string })
    }

    @Test
    fun converterCanUseSuspendSourceProviderDirectly() = runTest {
        val result = AzooKeyStyleConverter.convert(
            request = baseRequest(input = "0930", specialCandidateProviders = listOf(TimeExpressionSpecialCandidateProvider)),
            sourceProvider = SuspendCandidateSourceProvider {
                CandidateSources(system = listOf(candidate("午前九時半", CandidateType.NBEST, score = 10)))
            }
        )

        assertEquals(listOf("午前九時半", "09:30"), result.mainResults.map { it.string })
    }

    private fun baseRequest(
        input: String = "あずーきー",
        specialCandidateProviders: List<SpecialCandidateProvider> = emptyList(),
    ): CandidateRequest {
        return CandidateRequest(
            input = input,
            mode = CandidateRequestMode.Normal,
            nBest = 10,
            useUserDictionary = true,
            useUserTemplate = true,
            useRomajiCandidates = true,
            useBunsetsu = false,
            useOmissionSearch = false,
            japanesePredictionMode = AzooKeyStylePredictionMode.AutoMix,
            englishPredictionMode = AzooKeyStylePredictionMode.Disabled,
            learningType = AzooKeyStyleLearningType.OnlyOutput,
            typoCorrectionMode = AzooKeyStyleTypoCorrectionMode.Automatic,
            specialCandidateProviders = specialCandidateProviders,
        )
    }

    private fun candidate(
        string: String,
        type: Byte,
        score: Int,
    ): Candidate {
        return Candidate(
            string = string,
            type = type,
            length = string.length.toUByte(),
            score = score
        )
    }
}
