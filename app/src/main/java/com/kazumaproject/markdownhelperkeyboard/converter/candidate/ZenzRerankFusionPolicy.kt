package com.kazumaproject.markdownhelperkeyboard.converter.candidate

object ZenzRerankFusionPolicy {
    private const val DEFAULT_BASE_WEIGHT = 0.7f
    private const val DEFAULT_ZENZ_WEIGHT = 0.3f

    fun rerank(
        candidates: List<Candidate>,
        rawZenzScores: List<Float>,
        baseWeight: Float = DEFAULT_BASE_WEIGHT,
        zenzWeight: Float = DEFAULT_ZENZ_WEIGHT,
    ): List<Candidate>? {
        if (candidates.isEmpty()) {
            return emptyList()
        }
        if (candidates.size != rawZenzScores.size || rawZenzScores.none { it.isFinite() }) {
            return null
        }

        val baseNorm = minMaxNormalize(candidates.map { it.value })
        val zenzNorm = minMaxNormalizeFinite(rawZenzScores)

        return candidates.mapIndexed { index, candidate ->
            val rawScore = rawZenzScores[index]
            ZenzRerankFusionEntry(
                originalPosition = index,
                candidate = candidate,
                rawZenzScore = rawScore,
                fusedScore = baseWeight * baseNorm[index] + zenzWeight * zenzNorm[index],
            )
        }.sortedWith(
            compareByDescending<ZenzRerankFusionEntry> { it.fusedScore }
                .thenByDescending {
                    if (it.rawZenzScore.isFinite()) it.rawZenzScore else Float.NEGATIVE_INFINITY
                }
                .thenByDescending { it.candidate.value }
                .thenBy { it.originalPosition }
        ).map { it.candidate }
    }

    private fun minMaxNormalize(values: List<Float>): List<Float> {
        if (values.isEmpty()) return emptyList()
        val minValue = values.minOrNull() ?: return List(values.size) { 1.0f }
        val maxValue = values.maxOrNull() ?: return List(values.size) { 1.0f }
        val span = maxValue - minValue
        if (span <= 1e-6f) return List(values.size) { 1.0f }
        return values.map { (it - minValue) / span }
    }

    private fun minMaxNormalizeFinite(values: List<Float>): List<Float> {
        if (values.isEmpty()) return emptyList()

        val result = MutableList(values.size) { 0.0f }
        val finiteValues = values.withIndex().filter { it.value.isFinite() }
        if (finiteValues.isEmpty()) return result

        val minValue = finiteValues.minOf { it.value }
        val maxValue = finiteValues.maxOf { it.value }
        val span = maxValue - minValue

        if (span <= 1e-6f) {
            finiteValues.forEach { result[it.index] = 1.0f }
            return result
        }

        finiteValues.forEach {
            result[it.index] = (it.value - minValue) / span
        }
        return result
    }
}

private data class ZenzRerankFusionEntry(
    val originalPosition: Int,
    val candidate: Candidate,
    val rawZenzScore: Float,
    val fusedScore: Float,
)
