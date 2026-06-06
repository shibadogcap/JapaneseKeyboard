package com.kazumaproject.markdownhelperkeyboard.converter.candidate

fun interface CandidateSourceProvider {
    fun provide(request: CandidateRequest): CandidateSources
}

fun interface SuspendCandidateSourceProvider {
    suspend fun provide(request: CandidateRequest): CandidateSources
}

class CompositeCandidateSourceProvider(
    private val providers: List<CandidateSourceProvider>,
) : CandidateSourceProvider {
    override fun provide(request: CandidateRequest): CandidateSources {
        return providers.fold(CandidateSources()) { sources, provider ->
            sources.merge(provider.provide(request))
        }
    }
}

class CompositeSuspendCandidateSourceProvider(
    private val providers: List<SuspendCandidateSourceProvider>,
) : SuspendCandidateSourceProvider {
    override suspend fun provide(request: CandidateRequest): CandidateSources {
        var sources = CandidateSources()
        providers.forEach { provider ->
            sources = sources.merge(provider.provide(request))
        }
        return sources
    }
}
