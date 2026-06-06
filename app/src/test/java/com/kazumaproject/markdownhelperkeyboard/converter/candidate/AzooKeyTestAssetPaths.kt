package com.kazumaproject.markdownhelperkeyboard.converter.candidate

import java.io.File

internal object AzooKeyTestAssetPaths {
    fun projectRoot(): File {
        var dir = File(checkNotNull(System.getProperty("user.dir")))
        while (dir.parentFile != null &&
            !File(dir, "settings.gradle").isFile &&
            !File(dir, "settings.gradle.kts").isFile
        ) {
            dir = checkNotNull(dir.parentFile)
        }
        return dir
    }

    fun loudsDirectory(): File {
        val root = projectRoot()
        return listOf(
            File(root, "app/src/main/assets/louds"),
            File(root, "src/main/assets/louds"),
            File("app/src/main/assets/louds"),
            File("src/main/assets/louds"),
        ).first { it.isDirectory }
    }

    fun emojiDirectory(): File {
        val root = projectRoot()
        return listOf(
            File(root, "app/src/main/assets/azookey/emoji"),
            File(root, "src/main/assets/azookey/emoji"),
            File("app/src/main/assets/azookey/emoji"),
            File("src/main/assets/azookey/emoji"),
        ).first { it.isDirectory }
    }
}