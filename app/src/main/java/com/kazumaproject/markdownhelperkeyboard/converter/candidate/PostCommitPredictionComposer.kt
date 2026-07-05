package com.kazumaproject.markdownhelperkeyboard.converter.candidate

object PostCommitPredictionComposer {
    private val DEFAULT_PARTICLE_SET = setOf("は", "が", "の", "に", "を", "も", "で", "と", "へ", "から", "まで", "より")

    fun compose(
        committedText: String,
        learnedTransitions: List<Candidate>,
        emojiCandidates: List<Candidate> = emptyList(),
        zeroHintCandidates: List<Candidate> = emptyList(),
        limit: Int = 10,
        emojiLimit: Int = 3,
        particleLimit: Int = 3,
    ): List<Candidate> {
        if (committedText.isBlank() || limit <= 0) {
            return emptyList()
        }

        val zeroHints = zeroHintCandidates
            .asSequence()
            .filterParticleCandidates(particleBudget = particleLimit.coerceAtLeast(0))
            .toList()

        val results = mutableListOf<Candidate>()
        val seen = linkedSetOf<String>()

        emojiCandidates
            .cleanForPostCommit(committedText = committedText)
            .takeLast(emojiLimit.coerceAtLeast(0))
            .forEachUnique(results = results, seen = seen, limit = limit)

        val remainingAfterEmoji = limit - results.size
        if (remainingAfterEmoji <= 0) {
            return results
        }

        val predictionsCount = maxOf(
            remainingAfterEmoji / 2,
            remainingAfterEmoji - zeroHints.cleanForPostCommit(committedText, seen).count()
        )
        learnedTransitions
            .cleanForPostCommit(committedText = committedText, seen = seen)
            .sortedByDescending { it.value }
            .take(predictionsCount.coerceAtLeast(0))
            .forEachUnique(results = results, seen = seen, limit = limit)

        zeroHints
            .cleanForPostCommit(committedText = committedText, seen = seen)
            .sortedByDescending { it.value }
            .forEachUnique(results = results, seen = seen, limit = limit)

        return results
    }

    private fun Candidate.isParticleCandidate(): Boolean {
        return string in DEFAULT_PARTICLE_SET
    }

    private fun Sequence<Candidate>.filterParticleCandidates(
        particleBudget: Int,
    ): Sequence<Candidate> {
        var particleCount = 0
        return filter { candidate ->
            if (!candidate.isParticleCandidate()) {
                true
            } else if (particleCount < particleBudget) {
                particleCount += 1
                true
            } else {
                false
            }
        }
    }

    private fun Iterable<Candidate>.cleanForPostCommit(
        committedText: String,
        seen: Set<String> = emptySet(),
    ): List<Candidate> {
        return asSequence()
            .filter { it.string.isNotBlank() && it.string != committedText && it.string !in seen }
            .distinctBy { it.string }
            .toList()
    }

    private fun List<Candidate>.forEachUnique(
        results: MutableList<Candidate>,
        seen: MutableSet<String>,
        limit: Int,
    ) {
        for (candidate in this) {
            if (results.size >= limit) {
                return
            }
            if (seen.add(candidate.string)) {
                results.add(candidate)
            }
        }
    }
}
