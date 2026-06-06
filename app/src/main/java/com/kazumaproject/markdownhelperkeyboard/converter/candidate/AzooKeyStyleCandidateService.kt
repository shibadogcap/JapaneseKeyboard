package com.kazumaproject.markdownhelperkeyboard.converter.candidate

data class AzooKeyStyleCandidateServiceResult(
    val conversionResult: AzooKeyStyleConversionResult,
    val sources: CandidateSources,
    val bunsetsuResult: BunsetsuCandidateResult?,
)

class AzooKeyStyleCandidateService(
    private val auxiliarySourceProvider: SuspendCandidateSourceProvider,
    private val systemSourceProvider: SystemDictionarySourceProvider,
) {
    suspend fun convert(request: CandidateRequest): AzooKeyStyleCandidateServiceResult {
        val sources = auxiliarySourceProvider.provide(request)
            .merge(systemSourceProvider.provide(request))
        return AzooKeyStyleCandidateServiceResult(
            conversionResult = AzooKeyStyleConverter.convert(
                request = request,
                sources = sources,
            ),
            sources = sources,
            bunsetsuResult = systemSourceProvider.lastBunsetsuResult,
        )
    }
}
