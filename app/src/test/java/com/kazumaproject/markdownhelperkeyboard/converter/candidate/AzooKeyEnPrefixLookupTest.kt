package com.kazumaproject.markdownhelperkeyboard.converter.candidate

import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class AzooKeyEnPrefixLookupTest {
    @Test
    fun enPrefixIncludesYenEntry() = runTest {
        assumeTrue(AzooKeyTestAssetPaths.loudsDirectory().isDirectory)
        val registry = loaderFor(AzooKeyTestAssetPaths.loudsDirectory())
            .loadLoudsDictionaryRegistry()
            ?: error("registry missing")

        val entries = registry.commonPrefixEntries("エン")
            .map { it.surface to it.reading }
            .distinct()
        println("エン prefix entries: $entries")
        assertTrue(
            "Expected 円 among エン prefix entries, got: $entries",
            entries.any { it.first == "円" },
        )
    }

    private fun loaderFor(loudsDirectory: File): AzooKeyDictionaryShardLoader {
        return AzooKeyDictionaryShardLoader(
            readBytes = { path -> File(path).takeIf { it.isFile }?.readBytes() },
            loudsDirectory = loudsDirectory.absolutePath,
        )
    }
}
