package com.kazumaproject.markdownhelperkeyboard.converter.api

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.BunsetsuCandidateResult
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CandidateType
import com.kazumaproject.markdownhelperkeyboard.converter.lattice.AzooKeyLatticeIncrementalState

/**
 * AzooKey [ConversionSessionState](https://github.com/azooKey/AzooKeyKanaKanjiConverter) 相当。
 * composing 中の前回入力・文節結果・確定語・Zenz rerank キャッシュを 1 セッションに集約する。
 */
data class ConversionSession(
    val sessionId: String = DEFAULT_SESSION_ID,
    var previousComposingText: ComposingText? = null,
    /** IME がキー入力ごとに更新する現在の composing（Roman2Kana セグメント含む） */
    var liveComposingText: ComposingText? = null,
    var lastConvertTarget: String? = null,
    var completedCandidate: Candidate? = null,
    var lastCommittedCandidate: Candidate? = null,
    var lastBunsetsuResult: BunsetsuCandidateResult? = null,
    val latticeIncrementalState: AzooKeyLatticeIncrementalState = AzooKeyLatticeIncrementalState(),
) {
    private val zenzRerankCache = object : LinkedHashMap<String, List<Candidate>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<Candidate>>): Boolean {
            return size > ZENZ_RERANK_CACHE_LIMIT
        }
    }

    fun recordConversion(
        input: ComposingText,
        bunsetsuResult: BunsetsuCandidateResult? = null,
    ) {
        previousComposingText = input
        lastConvertTarget = input.convertTarget
        lastBunsetsuResult = bunsetsuResult
        completedCandidate = null
    }

    /** 前回変換読みの prefix 拡張なら incremental conversion の再利用候補（本家 cache 準備）。 */
    fun isIncrementalExtension(newInput: ComposingText): Boolean {
        val previous = lastConvertTarget ?: return false
        val next = newInput.convertTarget
        return next.startsWith(previous) && next.length > previous.length
    }

    fun recordCommit(
        surface: String,
        tapped: Candidate? = null,
        fallbackReading: String? = null,
    ): Candidate {
        val reading = tapped?.yomi?.takeIf { it.isNotBlank() }
            ?: fallbackReading?.takeIf { it.isNotBlank() }
        val committed = Candidate(
            string = surface,
            type = tapped?.type ?: CandidateType.NBEST,
            length = surface.length.toUByte(),
            score = tapped?.score ?: 0,
            value = tapped?.value ?: 0f,
            yomi = reading,
            leftId = tapped?.leftId,
            rightId = tapped?.rightId,
        )
        completedCandidate = committed
        lastCommittedCandidate = committed
        stopComposition(keepCommitted = true)
        return committed
    }

    fun stopComposition(keepCommitted: Boolean = false) {
        previousComposingText = null
        liveComposingText = null
        lastConvertTarget = null
        lastBunsetsuResult = null
        latticeIncrementalState.clear()
        if (!keepCommitted) {
            completedCandidate = null
        }
    }

    fun getZenzRerank(cacheKey: String): List<Candidate>? {
        synchronized(zenzRerankCache) {
            return zenzRerankCache[cacheKey]
        }
    }

    fun putZenzRerank(cacheKey: String, candidates: List<Candidate>) {
        synchronized(zenzRerankCache) {
            zenzRerankCache[cacheKey] = candidates
        }
    }

    fun clearZenzRerankCache() {
        synchronized(zenzRerankCache) {
            zenzRerankCache.clear()
        }
    }

    fun reset() {
        stopComposition(keepCommitted = false)
        lastCommittedCandidate = null
        clearZenzRerankCache()
    }

    companion object {
        const val DEFAULT_SESSION_ID: String = "default"
        private const val ZENZ_RERANK_CACHE_LIMIT = 64
    }
}