package com.kazumaproject.markdownhelperkeyboard.converter.core

import com.kazumaproject.core.domain.extensions.hiraganaToKatakana
import com.kazumaproject.markdownhelperkeyboard.converter.api.ComposingText
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.converter.lattice.AzooKeyLattice

/** AzooKey [Kana2Kanji.PrefixConstraint](https://github.com/azooKey/AzooKeyKanaKanjiConverter) 相当。 */
data class AzooKeyPrefixConstraint(
    val constraint: ByteArray = byteArrayOf(),
    val hasEos: Boolean = false,
    val ignoreMemoryAndUserDictionary: Boolean = false,
) {
    val isEmpty: Boolean
        get() = constraint.isEmpty() && !hasEos

    companion object {
        const val ALIGNMENT_SEPARATOR = "\uEE08"

        fun normalized(
            constraintBytes: ByteArray,
            defaultHasEos: Boolean,
            ignoreMemoryAndUserDictionary: Boolean,
        ): AzooKeyPrefixConstraint {
            val separator = ALIGNMENT_SEPARATOR.toByteArray(Charsets.UTF_8)
            val index = constraintBytes.indexOfSlice(separator)
            return if (index >= 0) {
                AzooKeyPrefixConstraint(
                    constraint = constraintBytes.copyOfRange(0, index),
                    hasEos = true,
                    ignoreMemoryAndUserDictionary = ignoreMemoryAndUserDictionary,
                )
            } else {
                AzooKeyPrefixConstraint(
                    constraint = constraintBytes,
                    hasEos = defaultHasEos,
                    ignoreMemoryAndUserDictionary = ignoreMemoryAndUserDictionary,
                )
            }
        }

        private fun ByteArray.indexOfSlice(slice: ByteArray): Int {
            if (slice.isEmpty() || size < slice.size) return -1
            for (index in 0..size - slice.size) {
                if (slice.indices.all { this[index + it] == slice[it] }) return index
            }
            return -1
        }
    }
}

data class AzooKeyZenzaiCache(
    val inputData: ComposingText,
    val prefixConstraint: AzooKeyPrefixConstraint = AzooKeyPrefixConstraint(),
    val satisfyingCandidate: Candidate? = null,
    val lattice: com.kazumaproject.markdownhelperkeyboard.converter.lattice.AzooKeyLattice? = null,
) {
    fun getNewConstraint(newInputData: ComposingText): AzooKeyPrefixConstraint {
        val satisfying = satisfyingCandidate
        if (satisfying != null) {
            var current = newInputData.convertTarget.hiraganaToKatakana()
            val bytes = mutableListOf<Byte>()
            for (entry in satisfying.data) {
                if (!current.startsWith(entry.reading)) break
                bytes.addAll(entry.surface.toByteArray(Charsets.UTF_8).toList())
                current = current.drop(entry.reading.length)
            }
            return AzooKeyPrefixConstraint(bytes.toByteArray())
        }
        return if (newInputData.convertTarget.startsWith(inputData.convertTarget)) {
            AzooKeyPrefixConstraint(prefixConstraint.constraint.copyOf())
        } else {
            AzooKeyPrefixConstraint()
        }
    }
}
