package com.kazumaproject.markdownhelperkeyboard.converter.candidate

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * セッション trie + 永続 LOUDS memory の統合ストア（AzooKey LearningManager 相当）。
 * Room upsert → session trie → disk LOUDS は [commitMutex] で直列化する。
 *
 * 永続化に失敗した場合はディスク上の memory ファイルを削除しインメモリキャッシュを無効化する。
 * 呼び出し元（[AzooKeyLearningMemoryRepository] 等）で Room からの full rebuild を行うこと。
 */
@Singleton
class AzooKeyLearningMemoryStore @Inject constructor(
    @ApplicationContext context: Context,
    private val assetProvider: AzooKeyDictionaryAssetProvider,
) {
    private val memoryDirectory = File(context.filesDir, "azookey-learning-memory")
    private val sessionTrie = AzooKeyTemporalLearningMemoryTrie()
    private var diskTrie = AzooKeyTemporalLearningMemoryTrie()
    private val loadMutex = Mutex()
    /** Room 確定・session trie・LOUDS ファイル IO の単一ライター。 */
    private val commitMutex = Mutex()

    @Volatile
    private var loudsSearcher: AzooKeyLoudsDictionarySearcher? = null

    @Volatile
    private var persistedRubyGroupCount: Int = 0

    val directory: File
        get() = memoryDirectory

    suspend fun ensureLoudsLoaded(
        seedEntries: suspend () -> List<AzooKeyDictionaryEntry>,
    ): AzooKeyLoudsDictionarySearcher? {
        loudsSearcher?.let { return it }
        return loadMutex.withLock {
            loudsSearcher?.let { return@withLock it }
            val charIdMap = assetProvider.charIdMap ?: return@withLock null
            if (!AzooKeyLearningMemoryPersistence.hasPersistedFiles(memoryDirectory)) {
                val entries = seedEntries()
                if (entries.isNotEmpty()) {
                    commitMutex.withLock {
                        persistFull(entries, charIdMap)
                    }
                }
            } else {
                commitMutex.withLock {
                    if (diskTrie.allEntries().isEmpty()) {
                        val entries = seedEntries()
                        if (entries.isNotEmpty()) {
                            diskTrie = AzooKeyTemporalLearningMemoryTrie.fromEntries(entries, charIdMap)
                            persistedRubyGroupCount = AzooKeyLoudsBinaryBuilder
                                .exportFromTrie(diskTrie)
                                .nodeRubyGroups
                                .size
                        }
                    }
                }
            }
            buildSearcher(charIdMap)?.also { loudsSearcher = it }
        }
    }

    /**
     * Room 書き込みと session/disk trie・LOUDS 永続化を同一ロックで行う。
     * [beforePersist] で Room upsert などを実行する。
     */
    suspend fun commitPersistedEntry(
        entry: AzooKeyDictionaryEntry,
        beforePersist: suspend () -> Unit = {},
    ) {
        val charIdMap = assetProvider.charIdMap ?: return
        commitMutex.withLock {
            beforePersist()
            memorizeSessionLocked(entry, charIdMap)
            persistDiskEntriesLocked(listOf(entry), charIdMap)
        }
    }

    suspend fun prefixSearch(
        reading: String,
        limit: Int,
        seedEntries: suspend () -> List<AzooKeyDictionaryEntry>,
        maxPrefixDepth: Int = 4,
    ): List<AzooKeyDictionaryEntry> {
        val charIdMap = assetProvider.charIdMap ?: return emptyList()
        val charIds = charIdMap.encode(reading) ?: return emptyList()
        ensureLoudsLoaded(seedEntries)
        val session = synchronized(sessionTrie) {
            sessionTrie.prefixMatch(charIds.map { it.toByte() })
        }
        val louds = loudsSearcher
            ?.prefixEntries(reading, maxDepth = maxPrefixDepth, maxCount = limit)
            ?: emptyList()
        return (session + louds)
            .distinctBy { it.reading to it.surface }
            .sortedByDescending { it.value }
            .take(limit)
    }

    suspend fun persistSessionAndRoomEntries(
        entries: List<AzooKeyDictionaryEntry>,
    ) {
        val charIdMap = assetProvider.charIdMap ?: return
        commitMutex.withLock {
            persistFull(entries, charIdMap)
        }
    }

    fun invalidateLoudsCache() {
        loudsSearcher = null
        persistedRubyGroupCount = 0
        diskTrie = AzooKeyTemporalLearningMemoryTrie()
    }

    fun memorizeSession(entry: AzooKeyDictionaryEntry) {
        val charIdMap = assetProvider.charIdMap ?: return
        memorizeSessionLocked(entry, charIdMap)
    }

    private fun memorizeSessionLocked(entry: AzooKeyDictionaryEntry, charIdMap: AzooKeyCharIdMap) {
        val charIds = charIdMap.encode(entry.reading)?.map { it.toByte() } ?: return
        synchronized(sessionTrie) {
            sessionTrie.memorize(entry, charIds)
        }
    }

    private fun persistDiskEntriesLocked(
        entries: List<AzooKeyDictionaryEntry>,
        charIdMap: AzooKeyCharIdMap,
    ) {
        if (entries.isEmpty()) return
        var anyUpsert = false
        entries.forEach { entry ->
            val charIds = charIdMap.encode(entry.reading)?.map { it.toByte() } ?: return@forEach
            if (!diskTrie.memorize(entry, charIds)) {
                anyUpsert = true
            }
        }
        val export = AzooKeyLoudsBinaryBuilder.exportFromTrie(diskTrie)
        try {
            if (!AzooKeyLearningMemoryPersistence.hasPersistedFiles(memoryDirectory)) {
                diskTrie = AzooKeyTemporalLearningMemoryTrie.fromEntries(
                    entries = sessionTrie.allEntries() + diskTrie.allEntries(),
                    charIdMap = charIdMap,
                )
                AzooKeyLearningMemoryPersistence.persistTrie(memoryDirectory, diskTrie)
                persistedRubyGroupCount = AzooKeyLoudsBinaryBuilder
                    .exportFromTrie(diskTrie)
                    .nodeRubyGroups
                    .size
            } else if (anyUpsert || export.nodeRubyGroups.size <= persistedRubyGroupCount) {
                // upsert または同一読みの別表記（homograph）: group 数は変わらず shard 内容だけ変わる
                AzooKeyLearningMemoryPersistence.persistTrie(memoryDirectory, diskTrie)
                persistedRubyGroupCount = export.nodeRubyGroups.size
            } else {
                AzooKeyLearningMemoryPersistence.persistTrieDelta(
                    directory = memoryDirectory,
                    trie = diskTrie,
                    previousRubyGroupCount = persistedRubyGroupCount,
                    export = export,
                )
                persistedRubyGroupCount = export.nodeRubyGroups.size
            }
            loudsSearcher = buildSearcher(charIdMap)
        } catch (e: Exception) {
            AzooKeyLearningMemoryPersistence.deleteAllPersistedFiles(memoryDirectory)
            invalidateLoudsCache()
            throw e
        }
    }

    private fun persistFull(entries: List<AzooKeyDictionaryEntry>, charIdMap: AzooKeyCharIdMap) {
        val merged = AzooKeyTemporalLearningMemoryTrie.fromEntries(
            entries = sessionTrie.allEntries() + entries,
            charIdMap = charIdMap,
        )
        diskTrie = merged
        AzooKeyLearningMemoryPersistence.persistTrie(memoryDirectory, diskTrie)
        persistedRubyGroupCount = AzooKeyLoudsBinaryBuilder.exportFromTrie(diskTrie).nodeRubyGroups.size
        loudsSearcher = buildSearcher(charIdMap)
    }

    /**
     * [commitMutex] を保持したまま LOUDS をロードする（[prefixSearch] 用）。
     * 呼び出し元は [commitMutex] を取得済みであること。
     */
    private suspend fun ensureLoudsLoadedWhileLocked(
        seedEntries: suspend () -> List<AzooKeyDictionaryEntry>,
        charIdMap: AzooKeyCharIdMap,
    ) {
        if (loudsSearcher != null) return
        if (!AzooKeyLearningMemoryPersistence.hasPersistedFiles(memoryDirectory)) {
            val entries = seedEntries()
            if (entries.isNotEmpty()) {
                persistFull(entries, charIdMap)
            }
        } else if (diskTrie.allEntries().isEmpty()) {
            val entries = seedEntries()
            if (entries.isNotEmpty()) {
                diskTrie = AzooKeyTemporalLearningMemoryTrie.fromEntries(entries, charIdMap)
                persistedRubyGroupCount = AzooKeyLoudsBinaryBuilder
                    .exportFromTrie(diskTrie)
                    .nodeRubyGroups
                    .size
            }
        }
        buildSearcher(charIdMap)?.let { loudsSearcher = it }
    }

    private fun buildSearcher(charIdMap: AzooKeyCharIdMap): AzooKeyLoudsDictionarySearcher? {
        if (!AzooKeyLearningMemoryPersistence.hasPersistedFiles(memoryDirectory)) {
            return null
        }
        val loader = AzooKeyDictionaryShardLoader(
            readBytes = { path -> File(path).takeIf { it.isFile }?.readBytes() },
            loudsDirectory = memoryDirectory.absolutePath,
        )
        val loudsTrie = loader.loadLoudsTrie(AzooKeyLoudsBinaryBuilder.MEMORY_IDENTIFIER)
            ?: return null
        return AzooKeyLoudsDictionaryLookup(
            identifier = AzooKeyLoudsBinaryBuilder.MEMORY_IDENTIFIER,
            charIdMap = charIdMap,
            loudsTrie = loudsTrie,
            shardLoader = loader,
            sourceKind = AzooKeyDictionarySourceKind.Memory,
        )
    }
}