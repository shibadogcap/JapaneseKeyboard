package com.kazumaproject.markdownhelperkeyboard.converter.candidate

/**
 * Kotlin-side facade for the azooKey-compatible conversion boundary.
 *
 * Source fetching can still live in platform code, but special providers,
 * source assembly, and result shaping are owned here so the IME can gradually
 * move toward a clean embeddable conversion engine.
 */
object AzooKeyStyleConverter {
    fun convert(
        request: CandidateRequest,
        sourceProvider: CandidateSourceProvider,
    ): AzooKeyStyleConversionResult {
        return convert(
            request = request,
            sources = sourceProvider.provide(request)
        )
    }

    suspend fun convert(
        request: CandidateRequest,
        sourceProvider: SuspendCandidateSourceProvider,
    ): AzooKeyStyleConversionResult {
        return convert(
            request = request,
            sources = sourceProvider.provide(request)
        )
    }

    fun convert(
        request: CandidateRequest,
        sources: CandidateSources,
    ): AzooKeyStyleConversionResult {
        val specialCandidates = request.specialCandidateProviders.flatMap { it.provide(request) }
        val mergedSources = sources.copy(
            special = sources.special + specialCandidates
        )

        return CandidateAssembler.assemble(
            request = request,
            sources = mergedSources
        )
    }
}
