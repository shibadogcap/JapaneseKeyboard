package com.kazumaproject.markdownhelperkeyboard.converter.candidate

object AzooKeyLoudsIdentifierManifest {
    const val DefaultFileName = "identifiers.txt"

    fun parse(text: String): Set<String> {
        return text
            .lineSequence()
            .flatMap { line ->
                val trimmedLine = line.trim()
                val content = if (trimmedLine == "#") {
                    trimmedLine
                } else {
                    line.substringBefore('#')
                }
                content
                    .splitToSequence(',', '\t', ' ')
            }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()
    }
}
