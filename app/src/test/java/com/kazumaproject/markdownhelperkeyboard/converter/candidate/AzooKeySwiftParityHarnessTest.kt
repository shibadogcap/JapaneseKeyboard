package com.kazumaproject.markdownhelperkeyboard.converter.candidate

import java.io.File
import java.util.concurrent.TimeUnit
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * macOS + Swift CLI が利用可能な環境で clones 参照出力を生成する（Phase 0b）。
 */
class AzooKeySwiftParityHarnessTest {
    @Test
    fun swiftParityHarnessProducesJsonOnMacOs() {
        assumeTrue("Requires macOS", System.getProperty("os.name").contains("Mac", ignoreCase = true))
        val root = AzooKeyTestAssetPaths.projectRoot()
        val script = File(root, "scripts/azookey-swift-parity.sh")
        assumeTrue("Requires parity script", script.isFile)
        assumeTrue("Requires AzooKey clone", File(root, "clones/AzooKeyKanaKanjiConverter/Package.swift").isFile)

        val output = File(root, "build/azookey-swift-parity-output.json")
        output.parentFile?.mkdirs()

        val process = ProcessBuilder("bash", script.absolutePath)
            .directory(root)
            .redirectErrorStream(true)
            .start()
        assumeTrue(
            "Swift harness timed out or failed",
            process.waitFor(10, TimeUnit.MINUTES) && process.exitValue() == 0,
        )
        assumeTrue("Expected Swift parity JSON output", output.isFile && output.length() > 0)
    }
}
