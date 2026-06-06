package com.kazumaproject.markdownhelperkeyboard.converter.candidate

/**
 * AzooKey [TemporalLearningMemoryTrie](https://github.com/azooKey/AzooKeyKanaKanjiConverter) 相当。
 */
class AzooKeyTemporalLearningMemoryTrie {
    private data class Node(
        val dataIndices: MutableList<Int> = mutableListOf(),
        val children: MutableMap<Byte, Int> = mutableMapOf(),
    )

    private val nodes = mutableListOf(Node())
    private val entries = mutableListOf<AzooKeyDictionaryEntry>()

    /**
     * @return `true` if a new (reading, surface) pair was inserted; `false` if an existing entry was updated.
     */
    fun memorize(entry: AzooKeyDictionaryEntry, charIds: List<Byte>): Boolean {
        if (charIds.isEmpty()) return false
        var nodeIndex = 0
        for (charId in charIds) {
            nodeIndex = nodes[nodeIndex].children.getOrPut(charId) {
                nodes.size.also { nodes += Node() }
            }
        }
        val existingIndex = nodes[nodeIndex].dataIndices.firstOrNull { index ->
            sameEntry(entries[index], entry)
        }
        if (existingIndex != null) {
            entries[existingIndex] = entry
            return false
        }
        val dataIndex = entries.size
        entries += entry
        nodes[nodeIndex].dataIndices += dataIndex
        return true
    }

    fun prefixMatch(charIds: List<Byte>): List<AzooKeyDictionaryEntry> {
        var nodeIndex = 0
        for (charId in charIds) {
            nodeIndex = nodes[nodeIndex].children[charId] ?: return emptyList()
        }
        val result = mutableListOf<AzooKeyDictionaryEntry>()
        val queue = ArrayDeque<Int>()
        nodes[nodeIndex].children.values.forEach { queue += it }
        result += nodes[nodeIndex].dataIndices.map { entries[it] }
        while (queue.isNotEmpty()) {
            val index = queue.removeFirst()
            result += nodes[index].dataIndices.map { entries[it] }
            queue += nodes[index].children.values
        }
        return result.distinctBy { it.reading to it.surface }
    }

    fun perfectMatch(charIds: List<Byte>): List<AzooKeyDictionaryEntry> {
        var nodeIndex = 0
        for (charId in charIds) {
            nodeIndex = nodes[nodeIndex].children[charId] ?: return emptyList()
        }
        return nodes[nodeIndex].dataIndices.map { entries[it] }
    }

    fun allEntries(): List<AzooKeyDictionaryEntry> = entries.toList()

    val nodeCount: Int
        get() = nodes.size

    fun rootChildrenSorted(): List<Pair<Byte, Int>> {
        return nodes[0].children.entries.sortedBy { it.key }.map { it.key to it.value }
    }

    fun childrenSorted(nodeIndex: Int): List<Pair<Byte, Int>> {
        return nodes[nodeIndex].children.entries.sortedBy { it.key }.map { it.key to it.value }
    }

    fun rubyGroupAtNode(nodeIndex: Int): AzooKeyLoudstxt3BinaryBuilder.RubyGroup {
        val nodeEntries = nodes[nodeIndex].dataIndices.map { entries[it] }
        if (nodeEntries.isEmpty()) {
            return AzooKeyLoudstxt3BinaryBuilder.RubyGroup(ruby = "", rows = emptyList())
        }
        val ruby = nodeEntries.first().reading
        return AzooKeyLoudstxt3BinaryBuilder.RubyGroup(
            ruby = ruby,
            rows = nodeEntries.map { entry ->
                AzooKeyLoudstxt3BinaryBuilder.Row(
                    word = entry.surface,
                    leftId = entry.leftId ?: AzooKeyCid.GENERAL_NOUN,
                    rightId = entry.rightId ?: AzooKeyCid.GENERAL_NOUN,
                    mid = entry.mid,
                    value = entry.value,
                )
            },
        )
    }

    companion object {
        fun fromEntries(
            entries: List<AzooKeyDictionaryEntry>,
            charIdMap: AzooKeyCharIdMap,
        ): AzooKeyTemporalLearningMemoryTrie {
            val trie = AzooKeyTemporalLearningMemoryTrie()
            entries.forEach { entry ->
                val charIds = charIdMap.encode(entry.reading)?.map { it.toByte() } ?: return@forEach
                trie.memorize(entry, charIds)
            }
            return trie
        }

        private fun sameEntry(left: AzooKeyDictionaryEntry, right: AzooKeyDictionaryEntry): Boolean {
            return left.surface == right.surface &&
                left.reading == right.reading &&
                left.leftId == right.leftId &&
                left.rightId == right.rightId
        }
    }
}