package com.kazumaproject.markdownhelperkeyboard.converter.candidate

import com.kazumaproject.markdownhelperkeyboard.converter.zenz.ZenzModelOutputPolicy

sealed class ZenzaiCandidateEvaluationResult {
    data object Error : ZenzaiCandidateEvaluationResult()

    data class Pass(
        val score: Float,
        val alternativeConstraints: List<AlternativeConstraint> = emptyList(),
    ) : ZenzaiCandidateEvaluationResult()

    data class FixRequired(
        val prefix: String,
    ) : ZenzaiCandidateEvaluationResult()

    data class WholeResult(
        val result: String,
    ) : ZenzaiCandidateEvaluationResult()

    data class AlternativeConstraint(
        val probabilityRatio: Float,
        val prefix: String,
    )

    companion object {
        fun parse(raw: String?): ZenzaiCandidateEvaluationResult {
            return when {
                raw == null -> Error
                raw.startsWith("PASS:") -> parsePass(raw.removePrefix("PASS:"))
                raw.startsWith("FIX:") -> parseFix(raw.removePrefix("FIX:"))
                raw.startsWith("WHOLE:") -> parseWhole(raw.removePrefix("WHOLE:"))
                else -> Error
            }
        }

        private fun parsePass(body: String): Pass {
            val segments = body.split('|')
            val score = segments.firstOrNull()?.toFloatOrNull() ?: 0f
            val constraints = segments.drop(1).mapNotNull { segment ->
                if (!segment.startsWith("ALT:")) return@mapNotNull null
                val fields = segment.removePrefix("ALT:").split(':', limit = 2)
                if (fields.size < 2) return@mapNotNull null
                val prefix = fields[1]
                if (!ZenzModelOutputPolicy.isValidConstraintText(prefix)) return@mapNotNull null
                AlternativeConstraint(
                    probabilityRatio = fields[0].toFloatOrNull() ?: 0f,
                    prefix = prefix,
                )
            }
            return Pass(score = score, alternativeConstraints = constraints)
        }

        private fun parseFix(prefix: String): ZenzaiCandidateEvaluationResult {
            return if (ZenzModelOutputPolicy.isValidConstraintText(prefix)) {
                FixRequired(prefix)
            } else {
                Error
            }
        }

        private fun parseWhole(result: String): ZenzaiCandidateEvaluationResult {
            return if (ZenzModelOutputPolicy.isValidConstraintText(result)) {
                WholeResult(result)
            } else {
                Error
            }
        }
    }
}
