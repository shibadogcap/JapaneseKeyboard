package com.kazumaproject.markdownhelperkeyboard.converter.lattice

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyCid
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyDictionaryEntry
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyDictionarySourceKind
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyMid
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyPValue
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate

sealed class AzooKeyGraphRegisteredNode {
    abstract val entry: AzooKeyDictionaryEntry
    abstract val prev: AzooKeyGraphRegisteredNode?
    abstract val totalValue: AzooKeyPValue
    abstract val range: AzooKeyLatticeRange

    data class Node(
        override val entry: AzooKeyDictionaryEntry,
        override val prev: AzooKeyGraphRegisteredNode?,
        override val totalValue: AzooKeyPValue,
        override val range: AzooKeyLatticeRange,
    ) : AzooKeyGraphRegisteredNode()

    object BOS : AzooKeyGraphRegisteredNode() {
        override val entry = AzooKeyDictionaryEntry(
            surface = "",
            reading = "",
            leftId = AzooKeyCid.BOS,
            rightId = AzooKeyCid.BOS,
            mid = AzooKeyMid.GENERAL,
            wordCost = 0,
            value = 0f,
            sourceKind = AzooKeyDictionarySourceKind.System,
        )
        override val prev: AzooKeyGraphRegisteredNode? = null
        override val totalValue: AzooKeyPValue = 0f
        override val range: AzooKeyLatticeRange = AzooKeyLatticeRange.zero
    }

    companion object {
        fun fromLastCandidate(candidate: Candidate): AzooKeyGraphRegisteredNode {
            val lastRcid = candidate.data.lastOrNull()?.rightId ?: AzooKeyCid.BOS
            return Node(
                entry = AzooKeyDictionaryEntry(
                    surface = "",
                    reading = "",
                    leftId = AzooKeyCid.BOS,
                    rightId = lastRcid,
                    mid = candidate.lastMid,
                    wordCost = 0,
                    value = 0f,
                    sourceKind = AzooKeyDictionarySourceKind.System,
                ),
                prev = null,
                totalValue = 0f,
                range = AzooKeyLatticeRange.zero,
            )
        }
    }
}

fun AzooKeyGraphRegisteredNode.getGraphCandidateData(): CandidateData {
    val chain = mutableListOf<AzooKeyGraphRegisteredNode>()
    var cursor: AzooKeyGraphRegisteredNode? = this
    while (cursor != null) {
        chain.add(cursor)
        cursor = cursor.prev
    }
    chain.reverse()

    val head = chain.first()
    val clauses = mutableListOf<Pair<ClauseDataUnit, Float>>()
    val data = mutableListOf<AzooKeyDictionaryEntry>()

    var unit = ClauseDataUnit()
    unit.mid = head.entry.mid
    unit.ranges.add(rangeToPair(head.range))
    clauses.add(unit to 0f)
    var lastClause = unit
    var lastClauseIndex = 0

    for (i in 1 until chain.size) {
        val node = chain[i]
        if (node.entry.surface.isEmpty()) continue

        val prevNode = chain[i - 1]
        val prevRcid = prevNode.entry.rightId ?: AzooKeyCid.PROPER_NOUN
        val currLcid = node.entry.leftId ?: AzooKeyCid.PROPER_NOUN

        if (lastClause.text.isEmpty() || !AzooKeyDicdataStoreUtils.isClause(prevRcid, currLcid)) {
            lastClause.text += node.entry.surface
            lastClause.reading += node.entry.reading
            lastClause.ranges.add(rangeToPair(node.range))
            if ((lastClause.mid == AzooKeyConnectionCostStore.MID_GENERAL && node.entry.mid != AzooKeyConnectionCostStore.MID_GENERAL) ||
                AzooKeyDicdataStoreUtils.includeMMValueCalculation(node.entry.leftId, node.entry.rightId)
            ) {
                lastClause.mid = node.entry.mid
            }
            data.add(node.entry)
            lastClause.dataEndIndex = data.size - 1
            clauses[lastClauseIndex] = lastClause to node.totalValue
        } else {
            val newUnit = ClauseDataUnit(
                text = node.entry.surface,
                reading = node.entry.reading,
                mid = if (AzooKeyDicdataStoreUtils.includeMMValueCalculation(node.entry.leftId, node.entry.rightId)) {
                    node.entry.mid
                } else {
                    AzooKeyConnectionCostStore.MID_GENERAL
                },
            ).also {
                it.ranges.add(rangeToPair(node.range))
            }
            lastClause.nextLcid = node.entry.leftId ?: AzooKeyCid.EOS
            clauses[lastClauseIndex] = lastClause to ((prevNode as? AzooKeyGraphRegisteredNode.Node)?.totalValue ?: 0f)
            data.add(node.entry)
            newUnit.dataEndIndex = data.size - 1
            clauses.add(newUnit to node.totalValue)
            lastClause = newUnit
            lastClauseIndex = clauses.size - 1
        }
    }
    return CandidateData(clauses = clauses, data = data)
}

private fun rangeToPair(range: AzooKeyLatticeRange): Pair<Int, Int> {
    return when (range) {
        is AzooKeyLatticeRange.Surface -> range.from to range.to
        is AzooKeyLatticeRange.Input -> range.from to range.to
    }
}
