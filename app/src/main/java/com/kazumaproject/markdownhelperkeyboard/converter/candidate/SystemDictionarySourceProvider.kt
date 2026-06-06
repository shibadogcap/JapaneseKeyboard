package com.kazumaproject.markdownhelperkeyboard.converter.candidate

/**
 * system 辞書 lane の供給元。AzooKey 本家の lattice 出力に相当する候補集合を返す。
 */
interface SystemDictionarySourceProvider {
    val lastBunsetsuResult: BunsetsuCandidateResult?
    suspend fun provide(request: CandidateRequest): CandidateSources
}

class EngineSystemDictionarySourceProvider(
    private val convertNormal: suspend (request: CandidateRequest) -> SystemCandidateSourceResult,
    private val convertOriginal: suspend (request: CandidateRequest) -> SystemCandidateSourceResult,
    private val convertWithoutPrediction: suspend (request: CandidateRequest) -> SystemCandidateSourceResult,
) : SystemDictionarySourceProvider {
    private var _lastBunsetsuResult: BunsetsuCandidateResult? = null
    override val lastBunsetsuResult: BunsetsuCandidateResult?
        get() = _lastBunsetsuResult

    /**
     * lattice / LOUDS が system 候補を返すとき、文節メタデータだけ engine から取得する。
     * system lane への候補混入を防ぐ。
     */
    suspend fun warmBunsetsuMetadataOnly(request: CandidateRequest) {
        if (!request.useBunsetsu || request.mode == CandidateRequestMode.EnglishKana) return
        provide(request)
    }

    override suspend fun provide(request: CandidateRequest): CandidateSources {
        val result = when (request.mode) {
            CandidateRequestMode.Normal -> convertNormal(request)
            CandidateRequestMode.Original -> convertOriginal(request)
            CandidateRequestMode.WithoutPrediction -> convertWithoutPrediction(request)
            CandidateRequestMode.EnglishKana -> SystemCandidateSourceResult(emptyList())
        }
        _lastBunsetsuResult = result.bunsetsuResult
        return CandidateSources(system = result.candidates)
    }
}

/**
 * LOUDS を system 主とし、[KanaKanjiEngine] は文節メタデータ取得のみに使う。
 */
class LoudsPrimarySystemDictionarySourceProvider(
    private val louds: AzooKeyLoudsDictionaryCandidateSourceProvider,
    private val engine: EngineSystemDictionarySourceProvider,
) : SystemDictionarySourceProvider {
    override val lastBunsetsuResult: BunsetsuCandidateResult?
        get() = engine.lastBunsetsuResult

    override suspend fun provide(request: CandidateRequest): CandidateSources {
        if (request.useBunsetsu && request.mode != CandidateRequestMode.EnglishKana) {
            engine.warmBunsetsuMetadataOnly(request)
        }
        return louds.provide(request)
    }
}