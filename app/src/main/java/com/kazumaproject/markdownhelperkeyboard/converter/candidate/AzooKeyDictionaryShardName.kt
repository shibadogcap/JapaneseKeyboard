package com.kazumaproject.markdownhelperkeyboard.converter.candidate

object AzooKeyDictionaryShardName {
    private val rawIdentifiers = setOf("user", "memory", "user_shortcuts")

    fun escapedIdentifier(identifier: String): String {
        if (identifier in rawIdentifiers) {
            return identifier
        }
        val chunks = mutableListOf<String>()
        for (char in identifier) {
            if (Character.isHighSurrogate(char) || Character.isLowSurrogate(char)) {
                chunks += char.code.toFourDigitHex()
            } else {
                chunks += char.code.toFourDigitHex()
            }
        }
        return chunks.joinToString(separator = "_", prefix = "[", postfix = "]")
    }

    fun identifierFromFileStem(fileStem: String): String? {
        return if (fileStem.startsWith("[") && fileStem.endsWith("]")) {
            unescapedIdentifier(fileStem)
        } else {
            fileStem.takeIf { it.isNotEmpty() }
        }
    }

    fun unescapedIdentifier(identifier: String): String? {
        if (identifier in rawIdentifiers) {
            return identifier
        }
        if (!identifier.startsWith("[") || !identifier.endsWith("]")) {
            return null
        }
        val body = identifier.removePrefix("[").removeSuffix("]")
        if (body.isEmpty()) {
            return ""
        }
        return body
            .split("_")
            .map { chunk ->
                chunk.toIntOrNull(radix = 16)?.toChar() ?: return null
            }
            .joinToString(separator = "")
    }

    fun loudstxt3FileName(identifier: String, shardIndex: Int): String {
        return "${escapedIdentifier(identifier)}$shardIndex.loudstxt3"
    }

    fun rawLoudstxt3FileName(identifier: String, shardIndex: Int): String {
        return "$identifier$shardIndex.loudstxt3"
    }

    fun loudsFileName(identifier: String): String {
        return "${escapedIdentifier(identifier)}.louds"
    }

    fun rawLoudsFileName(identifier: String): String {
        return "$identifier.louds"
    }

    fun loudsChars2FileName(identifier: String): String {
        return "${escapedIdentifier(identifier)}.loudschars2"
    }

    fun rawLoudsChars2FileName(identifier: String): String {
        return "$identifier.loudschars2"
    }

    private fun Int.toFourDigitHex(): String {
        return toString(radix = 16).uppercase().padStart(4, '0')
    }
}
