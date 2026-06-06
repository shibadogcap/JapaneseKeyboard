package com.kazumaproject.markdownhelperkeyboard.converter.candidate

import java.io.File

/**
 * Room 学習 DB → AzooKey memory ディレクトリ（`memory.louds` / `memory0.loudstxt3` 等）への書き出し。
 *
 * **Single-writer:** 呼び出し元は [AzooKeyLearningMemoryStore] の mutex 経由で直列化すること。
 * 差分 shard 書き込みは `.tmp` へ書いてから rename する（途中失敗時は旧 shard を残す）。
 */
object AzooKeyLearningMemoryPersistence {
    const val ENTRIES_PER_SHARD: Int = 1 shl AzooKeyLoudsDictionaryLookup.DefaultShardShift

    data class PersistDeltaResult(
        val export: AzooKeyLoudsBinaryBuilder.ExportResult,
        val appendedShardCount: Int,
    )

    fun persist(
        directory: File,
        entries: List<AzooKeyDictionaryEntry>,
        charIdMap: AzooKeyCharIdMap,
    ): Boolean {
        if (entries.isEmpty()) {
            directory.mkdirs()
            return false
        }
        val trie = AzooKeyTemporalLearningMemoryTrie.fromEntries(entries, charIdMap)
        return persistTrie(directory, trie)
    }

    fun persistTrie(
        directory: File,
        trie: AzooKeyTemporalLearningMemoryTrie,
    ): Boolean {
        if (trie.allEntries().isEmpty()) {
            directory.mkdirs()
            return false
        }
        deleteMemoryShards(directory)
        val export = AzooKeyLoudsBinaryBuilder.exportFromTrie(trie)
        writeLoudsExport(directory, export)
        writeSequentialShardsAtomic(directory, export.nodeRubyGroups, startShardIndex = 0)
        return true
    }

    /**
     * 新規 ruby group 分だけ shard を追記し、LOUDS 本体は差し替える。
     * 既存 group の **内容更新**（upsert）や **同一読みの別表記**（homograph）は [persistTrie] で全 shard を書き直すこと。
     */
    fun persistTrieDelta(
        directory: File,
        trie: AzooKeyTemporalLearningMemoryTrie,
        previousRubyGroupCount: Int,
        export: AzooKeyLoudsBinaryBuilder.ExportResult,
    ): PersistDeltaResult {
        if (trie.allEntries().isEmpty()) {
            return PersistDeltaResult(export = export, appendedShardCount = 0)
        }
        writeLoudsExport(directory, export)
        val newGroups = export.nodeRubyGroups.drop(previousRubyGroupCount.coerceAtLeast(0))
        if (newGroups.isEmpty()) {
            return PersistDeltaResult(export = export, appendedShardCount = 0)
        }
        val startShardIndex = shardCount(directory)
        val appended = writeSequentialShardsAtomic(
            directory = directory,
            groups = newGroups,
            startShardIndex = startShardIndex,
        )
        return PersistDeltaResult(export = export, appendedShardCount = appended)
    }

    fun shardCount(directory: File): Int {
        var index = 0
        while (loudstxt3ShardFile(directory, index).isFile) {
            index++
        }
        return index
    }

    fun deleteMemoryShards(directory: File) {
        var index = 0
        while (true) {
            val file = loudstxt3ShardFile(directory, index)
            if (!file.isFile) break
            file.delete()
            File(file.parentFile, "${file.name}.tmp").takeIf { it.isFile }?.delete()
            index++
        }
    }

    /** LOUDS 本体・chars2・全 shard を削除（永続化失敗後のリカバリ用）。 */
    fun deleteAllPersistedFiles(directory: File) {
        deleteMemoryShards(directory)
        listOf(
            AzooKeyDictionaryShardName.rawLoudsFileName(AzooKeyLoudsBinaryBuilder.MEMORY_IDENTIFIER),
            AzooKeyDictionaryShardName.rawLoudsChars2FileName(AzooKeyLoudsBinaryBuilder.MEMORY_IDENTIFIER),
        ).forEach { name ->
            val file = File(directory, name)
            file.takeIf { it.isFile }?.delete()
            File(directory, "${name}.tmp").takeIf { it.isFile }?.delete()
        }
    }

    private fun writeSequentialShardsAtomic(
        directory: File,
        groups: List<AzooKeyLoudstxt3BinaryBuilder.RubyGroup>,
        startShardIndex: Int,
    ): Int {
        if (groups.isEmpty()) return 0
        var shardIndex = startShardIndex
        var offset = 0
        var shardsWritten = 0
        while (offset < groups.size) {
            val end = minOf(offset + ENTRIES_PER_SHARD, groups.size)
            writeShardFileAtomic(
                loudstxt3ShardFile(directory, shardIndex),
                groups.subList(offset, end),
            )
            shardIndex++
            shardsWritten++
            offset = end
        }
        return shardsWritten
    }

    private fun writeShardFileAtomic(
        target: File,
        groups: List<AzooKeyLoudstxt3BinaryBuilder.RubyGroup>,
    ) {
        target.parentFile?.mkdirs()
        val temp = File(target.parentFile, "${target.name}.tmp")
        AzooKeyLoudstxt3BinaryBuilder.writeFile(temp, groups)
        if (target.isFile && !target.delete()) {
            temp.delete()
            error("Failed to replace shard ${target.name}")
        }
        if (!temp.renameTo(target)) {
            temp.copyTo(target, overwrite = true)
            temp.delete()
        }
    }

    private fun writeLoudsExport(directory: File, export: AzooKeyLoudsBinaryBuilder.ExportResult) {
        directory.mkdirs()
        writeBytesAtomic(
            File(directory, AzooKeyDictionaryShardName.rawLoudsFileName(AzooKeyLoudsBinaryBuilder.MEMORY_IDENTIFIER)),
            AzooKeyLoudsBinaryBuilder.makeLoudsBytes(export.bits),
        )
        writeBytesAtomic(
            File(
                directory,
                AzooKeyDictionaryShardName.rawLoudsChars2FileName(AzooKeyLoudsBinaryBuilder.MEMORY_IDENTIFIER),
            ),
            export.nodes2Characters,
        )
    }

    private fun writeBytesAtomic(target: File, bytes: ByteArray) {
        target.parentFile?.mkdirs()
        val temp = File(target.parentFile, "${target.name}.tmp")
        temp.writeBytes(bytes)
        if (target.isFile && !target.delete()) {
            temp.delete()
            error("Failed to replace ${target.name}")
        }
        if (!temp.renameTo(target)) {
            temp.copyTo(target, overwrite = true)
            temp.delete()
        }
    }

    private fun loudstxt3ShardFile(directory: File, shardIndex: Int): File {
        return File(
            directory,
            AzooKeyDictionaryShardName.rawLoudstxt3FileName(
                AzooKeyLoudsBinaryBuilder.MEMORY_IDENTIFIER,
                shardIndex,
            ),
        )
    }

    fun hasPersistedFiles(directory: File): Boolean {
        return File(directory, AzooKeyDictionaryShardName.rawLoudsFileName(AzooKeyLoudsBinaryBuilder.MEMORY_IDENTIFIER))
            .isFile &&
            File(
                directory,
                AzooKeyDictionaryShardName.rawLoudstxt3FileName(AzooKeyLoudsBinaryBuilder.MEMORY_IDENTIFIER, 0),
            ).isFile
    }
}