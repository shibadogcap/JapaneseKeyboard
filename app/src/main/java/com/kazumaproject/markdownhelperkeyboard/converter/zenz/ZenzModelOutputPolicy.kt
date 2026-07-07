package com.kazumaproject.markdownhelperkeyboard.converter.zenz

/**
 * Validates Zenz model outputs at the model boundary (AzooKey does not post-filter engine candidates).
 */
internal object ZenzModelOutputPolicy {
    fun isValidConstraintText(text: String): Boolean {
        if (text.isEmpty()) {
            return false
        }
        return text.none { isHangul(it) }
    }

    fun isValidGeneratedReading(reading: String): Boolean {
        if (reading.isEmpty()) {
            return false
        }
        return reading.none { isHangul(it) }
    }

    fun sanitizeGeneratedReading(reading: String): String? {
        val normalized = reading.trim()
        return normalized.takeIf { isValidGeneratedReading(it) }
    }

    private fun isHangul(ch: Char): Boolean {
        val code = ch.code
        return code in 0x1100..0x11FF ||
            code in 0x3130..0x318F ||
            code in 0xAC00..0xD7AF
    }
}
