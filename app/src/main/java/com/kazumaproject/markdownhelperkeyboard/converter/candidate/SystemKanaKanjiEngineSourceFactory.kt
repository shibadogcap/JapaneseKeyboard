package com.kazumaproject.markdownhelperkeyboard.converter.candidate

import com.kazumaproject.markdownhelperkeyboard.converter.engine.KanaKanjiEngine
import com.kazumaproject.markdownhelperkeyboard.repository.LearnRepository
import com.kazumaproject.markdownhelperkeyboard.repository.UserDictionaryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class SystemKanaKanjiEngineSourceConfig(
    val mozcUtPersonName: Boolean?,
    val mozcUTPlaces: Boolean?,
    val mozcUTWiki: Boolean?,
    val mozcUTNeologd: Boolean?,
    val mozcUTWeb: Boolean?,
    val enableTypoCorrectionJapaneseFlick: Boolean,
    val enableTypoCorrectionQwertyEnglish: Boolean,
    val typoCorrectionOffsetScore: Int,
    val omissionSearchOffsetScore: Int,
)

class SystemKanaKanjiEngineSourceFactory(
    private val kanaKanjiEngine: KanaKanjiEngine,
    private val userDictionaryRepository: UserDictionaryRepository,
    private val learnRepository: LearnRepository?,
    private val config: SystemKanaKanjiEngineSourceConfig,
    private val onNormalBunsetsuResult: (request: CandidateRequest, result: BunsetsuCandidateResult) -> Unit = { _, _ -> },
) {
    fun create(): EngineSystemDictionarySourceProvider {
        return EngineSystemDictionarySourceProvider(
            convertOriginal = { request -> convertOriginal(request) },
            convertNormal = { request -> convertNormal(request) },
            convertWithoutPrediction = { request -> convertWithoutPrediction(request) },
        )
    }

    private suspend fun convertOriginal(request: CandidateRequest): SystemCandidateSourceResult {
        return withContext(Dispatchers.Default) {
            if (request.useBunsetsu) {
                val result = kanaKanjiEngine.getCandidatesOriginalWithBunsetsu(
                    input = request.input,
                    n = request.effectiveSearchNBest,
                    mozcUtPersonName = config.mozcUtPersonName,
                    mozcUTPlaces = config.mozcUTPlaces,
                    mozcUTWiki = config.mozcUTWiki,
                    mozcUTNeologd = config.mozcUTNeologd,
                    mozcUTWeb = config.mozcUTWeb,
                    userDictionaryRepository = userDictionaryRepository,
                    learnRepository = learnRepository,
                    isOmissionSearchEnable = request.useOmissionSearch,
                    enableTypoCorrectionJapaneseFlick = config.enableTypoCorrectionJapaneseFlick,
                    enableTypoCorrectionQwertyEnglish = config.enableTypoCorrectionQwertyEnglish,
                    typoCorrectionOffsetScore = config.typoCorrectionOffsetScore,
                    omissionSearchOffsetScore = config.omissionSearchOffsetScore,
                )
                SystemCandidateSourceResult(result.candidates, result)
            } else {
                SystemCandidateSourceResult(
                    kanaKanjiEngine.getCandidatesOriginal(
                        input = request.input,
                        n = request.effectiveSearchNBest,
                        mozcUtPersonName = config.mozcUtPersonName,
                        mozcUTPlaces = config.mozcUTPlaces,
                        mozcUTWiki = config.mozcUTWiki,
                        mozcUTNeologd = config.mozcUTNeologd,
                        mozcUTWeb = config.mozcUTWeb,
                        userDictionaryRepository = userDictionaryRepository,
                        learnRepository = learnRepository,
                        isOmissionSearchEnable = request.useOmissionSearch,
                        enableTypoCorrectionJapaneseFlick = config.enableTypoCorrectionJapaneseFlick,
                        enableTypoCorrectionQwertyEnglish = config.enableTypoCorrectionQwertyEnglish,
                        typoCorrectionOffsetScore = config.typoCorrectionOffsetScore,
                        omissionSearchOffsetScore = config.omissionSearchOffsetScore,
                    )
                )
            }
        }
    }

    private suspend fun convertNormal(request: CandidateRequest): SystemCandidateSourceResult {
        return withContext(Dispatchers.Default) {
            if (request.useBunsetsu) {
                val result = kanaKanjiEngine.getCandidatesWithBunsetsuSeparation(
                    input = request.input,
                    n = request.effectiveSearchNBest,
                    mozcUtPersonName = config.mozcUtPersonName,
                    mozcUTPlaces = config.mozcUTPlaces,
                    mozcUTWiki = config.mozcUTWiki,
                    mozcUTNeologd = config.mozcUTNeologd,
                    mozcUTWeb = config.mozcUTWeb,
                    userDictionaryRepository = userDictionaryRepository,
                    learnRepository = learnRepository,
                    isOmissionSearchEnable = request.useOmissionSearch,
                    enableTypoCorrectionJapaneseFlick = config.enableTypoCorrectionJapaneseFlick,
                    enableTypoCorrectionQwertyEnglish = config.enableTypoCorrectionQwertyEnglish,
                    typoCorrectionOffsetScore = config.typoCorrectionOffsetScore,
                    omissionSearchOffsetScore = config.omissionSearchOffsetScore,
                )
                onNormalBunsetsuResult(request, result)
                SystemCandidateSourceResult(result.candidates, result)
            } else {
                SystemCandidateSourceResult(
                    kanaKanjiEngine.getCandidates(
                        input = request.input,
                        n = request.effectiveSearchNBest,
                        mozcUtPersonName = config.mozcUtPersonName,
                        mozcUTPlaces = config.mozcUTPlaces,
                        mozcUTWiki = config.mozcUTWiki,
                        mozcUTNeologd = config.mozcUTNeologd,
                        mozcUTWeb = config.mozcUTWeb,
                        userDictionaryRepository = userDictionaryRepository,
                        learnRepository = learnRepository,
                        isOmissionSearchEnable = request.useOmissionSearch,
                        enableTypoCorrectionJapaneseFlick = config.enableTypoCorrectionJapaneseFlick,
                        enableTypoCorrectionQwertyEnglish = config.enableTypoCorrectionQwertyEnglish,
                        typoCorrectionOffsetScore = config.typoCorrectionOffsetScore,
                        omissionSearchOffsetScore = config.omissionSearchOffsetScore,
                    )
                )
            }
        }
    }

    private suspend fun convertWithoutPrediction(request: CandidateRequest): SystemCandidateSourceResult {
        return withContext(Dispatchers.Default) {
            if (request.useBunsetsu) {
                val result = kanaKanjiEngine.getCandidatesWithoutPredictionWithBunsetsu(
                    input = request.input,
                    n = request.effectiveSearchNBest,
                    mozcUtPersonName = config.mozcUtPersonName,
                    mozcUTPlaces = config.mozcUTPlaces,
                    mozcUTWiki = config.mozcUTWiki,
                    mozcUTNeologd = config.mozcUTNeologd,
                    mozcUTWeb = config.mozcUTWeb,
                    userDictionaryRepository = userDictionaryRepository,
                    learnRepository = learnRepository,
                    typoCorrectionOffsetScore = config.typoCorrectionOffsetScore,
                    omissionSearchOffsetScore = config.omissionSearchOffsetScore,
                )
                SystemCandidateSourceResult(result.candidates, result)
            } else {
                SystemCandidateSourceResult(
                    kanaKanjiEngine.getCandidatesWithoutPrediction(
                        input = request.input,
                        n = request.effectiveSearchNBest,
                        mozcUtPersonName = config.mozcUtPersonName,
                        mozcUTPlaces = config.mozcUTPlaces,
                        mozcUTWiki = config.mozcUTWiki,
                        mozcUTNeologd = config.mozcUTNeologd,
                        mozcUTWeb = config.mozcUTWeb,
                        userDictionaryRepository = userDictionaryRepository,
                        learnRepository = learnRepository,
                        typoCorrectionOffsetScore = config.typoCorrectionOffsetScore,
                        omissionSearchOffsetScore = config.omissionSearchOffsetScore,
                    )
                )
            }
        }
    }
}
