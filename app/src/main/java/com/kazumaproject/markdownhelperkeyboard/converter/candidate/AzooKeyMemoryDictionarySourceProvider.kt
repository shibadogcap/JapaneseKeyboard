package com.kazumaproject.markdownhelperkeyboard.converter.candidate

/**
 * AzooKey 本家の memory identifier 相当。学習語を prediction lane ではなく辞書 lane（system へ合流）で扱う。
 */
class AzooKeyMemoryDictionarySourceProvider(
    private val prefixMatchThreshold: Int,
    private val limit: Int,
    private val search: suspend (reading: String, limit: Int) -> List<AzooKeyDictionaryEntry>,
) : SuspendCandidateSourceProvider {
    override suspend fun provide(request: CandidateRequest): CandidateSources {
        if (!request.shouldReadMemoryDictionary) {
            return CandidateSources()
        }
        if (request.input.length <= prefixMatchThreshold) {
            return CandidateSources()
        }
        val candidates = search(request.input, limit)
            .map { entry ->
                entry.toCandidate(
                    type = CandidateType.NBEST,
                    connectionIdResolver = AzooKeyDictionaryConnectionIdResolver(),
                )
            }
        return CandidateSources(memory = candidates)
    }
}