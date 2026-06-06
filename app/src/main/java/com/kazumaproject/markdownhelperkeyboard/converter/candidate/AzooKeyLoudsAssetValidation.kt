package com.kazumaproject.markdownhelperkeyboard.converter.candidate

data class AzooKeyLoudsAssetValidationResult(
    val hasCharIdMap: Boolean,
    val completeIdentifiers: Set<String>,
    val identifiersMissingLouds: Set<String>,
    val identifiersMissingLoudsChars2: Set<String>,
    val identifiersMissingShard: Set<String>,
) {
    val isValid: Boolean
        get() = hasCharIdMap &&
                identifiersMissingLouds.isEmpty() &&
                identifiersMissingLoudsChars2.isEmpty() &&
                identifiersMissingShard.isEmpty()
}

object AzooKeyLoudsAssetValidation {
    fun validate(fileNames: Iterable<String>): AzooKeyLoudsAssetValidationResult {
        val fileNameList = fileNames.toList()
        val loudsIdentifiers = mutableSetOf<String>()
        val loudsChars2Identifiers = mutableSetOf<String>()
        val shardIdentifiers = mutableSetOf<String>()

        fileNameList.forEach { fileName ->
            when {
                fileName.endsWith(".louds") -> {
                    AzooKeyLoudsAssetManifest.identifierFromFileNameSuffix(
                        fileName = fileName,
                        suffix = ".louds",
                    )?.let(loudsIdentifiers::add)
                }

                fileName.endsWith(".loudschars2") -> {
                    AzooKeyLoudsAssetManifest.identifierFromFileNameSuffix(
                        fileName = fileName,
                        suffix = ".loudschars2",
                    )?.let(loudsChars2Identifiers::add)
                }

                fileName.endsWith(".loudstxt3") -> {
                    identifierFromShardFileName(fileName)?.let(shardIdentifiers::add)
                }
            }
        }

        val knownIdentifiers = loudsIdentifiers + loudsChars2Identifiers + shardIdentifiers
        val completeIdentifiers = loudsIdentifiers
            .intersect(loudsChars2Identifiers)
            .intersect(shardIdentifiers)
            .toSortedSet()

        return AzooKeyLoudsAssetValidationResult(
            hasCharIdMap = "charID.chid" in fileNameList,
            completeIdentifiers = completeIdentifiers,
            identifiersMissingLouds = (knownIdentifiers - loudsIdentifiers).toSortedSet(),
            identifiersMissingLoudsChars2 = (knownIdentifiers - loudsChars2Identifiers).toSortedSet(),
            identifiersMissingShard = (knownIdentifiers - shardIdentifiers).toSortedSet(),
        )
    }

    private fun identifierFromShardFileName(fileName: String): String? {
        val stem = fileName.removeSuffix(".loudstxt3")
        val identifierStem = stem.dropLastWhile { it.isDigit() }
        if (identifierStem.length == stem.length) {
            return null
        }
        return AzooKeyDictionaryShardName.identifierFromFileStem(identifierStem)
    }
}
