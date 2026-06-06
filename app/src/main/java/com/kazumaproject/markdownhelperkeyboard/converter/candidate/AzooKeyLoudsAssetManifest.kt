package com.kazumaproject.markdownhelperkeyboard.converter.candidate

object AzooKeyLoudsAssetManifest {
    fun discoverIdentifiers(fileNames: Iterable<String>): Set<String> {
        val loudsIdentifiers = mutableSetOf<String>()
        val loudsChars2Identifiers = mutableSetOf<String>()
        fileNames.forEach { fileName ->
            when {
                fileName.endsWith(".louds") -> {
                    val identifier = identifierFromFileNameSuffix(
                        fileName = fileName,
                        suffix = ".louds",
                    )
                    if (identifier != null) {
                        loudsIdentifiers += identifier
                    }
                }

                fileName.endsWith(".loudschars2") -> {
                    val identifier = identifierFromFileNameSuffix(
                        fileName = fileName,
                        suffix = ".loudschars2",
                    )
                    if (identifier != null) {
                        loudsChars2Identifiers += identifier
                    }
                }
            }
        }
        return loudsIdentifiers
            .intersect(loudsChars2Identifiers)
            .toSortedSet()
    }

    fun toManifestText(identifiers: Iterable<String>): String {
        return identifiers
            .toSortedSet()
            .joinToString(separator = "\n", postfix = "\n")
    }

    fun identifierFromFileNameSuffix(
        fileName: String,
        suffix: String,
    ): String? {
        val stem = fileName.removeSuffix(suffix)
        return AzooKeyDictionaryShardName.identifierFromFileStem(stem)
    }
}
