package com.kazumaproject.markdownhelperkeyboard.converter.lattice

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyTestAssetPaths
import java.io.File
import org.junit.Assume.assumeTrue
import org.junit.Test

class AzooKeyConnectionCostDebugTest {
    @Test
    fun printNumberToYenConnectionCosts() {
        assumeTrue(
            File(AzooKeyTestAssetPaths.projectRoot(), "app/src/main/assets/azookey/cb/1285.binary").isFile,
        )
        val store = AzooKeyConnectionCostStore.fromDirectory(
            File(AzooKeyTestAssetPaths.projectRoot(), "app/src/main/assets/azookey"),
        ) ?: return
        val numberRcid = 1295
        listOf(11, 1298, 1300, 1285).forEach { lcid ->
            println("1295 -> $lcid = ${store.getConnectionCost(numberRcid, lcid)}")
        }
    }
}
