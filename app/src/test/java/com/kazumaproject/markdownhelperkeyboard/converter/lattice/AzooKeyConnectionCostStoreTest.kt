package com.kazumaproject.markdownhelperkeyboard.converter.lattice

import java.io.File
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class AzooKeyConnectionCostStoreTest {
    @Test
    fun bundledConnectionCostsDifferFromUnknownDefault() {
        val cbFile = bundledCbFile(1285) ?: return
        val line = AzooKeyConnectionCostBinaryParser.parseConnectionLine(cbFile.readBytes())
        val cost = line[1285]
        assertNotEquals(
            AzooKeyConnectionCostBinaryParser.DEFAULT_UNKNOWN_COST,
            cost,
        )
    }

    @Test
    fun storeLoadsBundledCbFromDirectory() {
        val root = bundledAzookeyDirectory() ?: return
        val store = AzooKeyConnectionCostStore.fromDirectory(root) ?: return
        val cost = store.getConnectionCost(1285, 1285)
        assertNotEquals(AzooKeyConnectionCostBinaryParser.DEFAULT_UNKNOWN_COST, cost)
    }

    private fun bundledAzookeyDirectory(): File? {
        return listOf(
            File("app/src/main/assets/azookey"),
            File("src/main/assets/azookey"),
        ).firstOrNull { File(it, "cb").isDirectory }
            ?.also { assumeTrue("Skip when cb assets are not copied", File(it, "cb/1285.binary").isFile) }
    }

    private fun bundledCbFile(former: Int): File? {
        val file = listOf(
            File("app/src/main/assets/azookey/cb/$former.binary"),
            File("src/main/assets/azookey/cb/$former.binary"),
        ).firstOrNull { it.isFile } ?: return null
        assumeTrue("Skip when cb assets are not copied: ./gradlew copyAzooKeyConnectionAssets -PazooKeyDictionarySourceDir=...", file.isFile)
        return file
    }
}