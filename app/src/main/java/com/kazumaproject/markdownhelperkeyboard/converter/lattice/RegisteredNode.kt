package com.kazumaproject.markdownhelperkeyboard.converter.lattice

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyCid
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyDictionaryEntry
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyDictionarySourceKind
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyMid
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyPValue
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate

sealed class RegisteredNode {
    abstract val entry: AzooKeyDictionaryEntry
    abstract val prev: RegisteredNode?
    abstract val totalValue: AzooKeyPValue
    abstract val startIndex: Int
    abstract val endIndex: Int

    object BOS : RegisteredNode() {
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
        override val prev: RegisteredNode? = null
        override val totalValue: AzooKeyPValue = 0f
        override val startIndex: Int = 0
        override val endIndex: Int = 0
    }

    data class Node(
        override val entry: AzooKeyDictionaryEntry,
        override val prev: RegisteredNode?,
        override val totalValue: AzooKeyPValue,
        override val startIndex: Int,
        override val endIndex: Int,
    ) : RegisteredNode()

    companion object {
        fun fromLastCandidate(candidate: Candidate): RegisteredNode {
            val lastRcid = candidate.data.lastOrNull()?.rightId ?: AzooKeyCid.BOS
            val lastMid = candidate.lastMid
            return Node(
                entry = AzooKeyDictionaryEntry(
                    surface = "",
                    reading = "",
                    leftId = AzooKeyCid.BOS,
                    rightId = lastRcid,
                    mid = lastMid,
                    wordCost = 0,
                    value = 0f,
                    sourceKind = AzooKeyDictionarySourceKind.System,
                ),
                prev = null,
                totalValue = 0f,
                startIndex = 0,
                endIndex = 0,
            )
        }
    }
}

fun RegisteredNode.getEntries(): List<AzooKeyDictionaryEntry> {
    val entries = mutableListOf<AzooKeyDictionaryEntry>()
    var cursor: RegisteredNode? = this
    while (cursor != null && cursor !is RegisteredNode.BOS) {
        if (cursor is RegisteredNode.Node) {
            if (cursor.entry.surface.isNotEmpty()) {
                entries.add(cursor.entry)
            }
        }
        cursor = cursor.prev
    }
    entries.reverse()
    return entries
}

/**
 * [azooKey RegisteredNode.getCandidateData()](https://github.com/azooKey/AzooKeyKanaKanjiConverter)
 * 相当。再帰ではなく、チェーンを収集→前方向に一度だけ処理する非再帰実装。
 *
 * @return 文節単位の区切り情報を持った変換候補データ。
 */
fun RegisteredNode.getCandidateData(): CandidateData {
    // 1) チェーンを収集（BOSまで）しつつ、非空ワード数を数える
    val chain = mutableListOf<RegisteredNode>()
    var nonEmptyCount = 0
    var cursor: RegisteredNode? = this
    while (cursor != null && cursor !is RegisteredNode.BOS) {
        chain.add(cursor)
        if (cursor.entry.surface.isNotEmpty()) nonEmptyCount++
        cursor = cursor.prev
    }
    // BOSノードを追加
    if (cursor is RegisteredNode.BOS) {
        chain.add(cursor)
    }
    // 逆順にして起点→現在の順番にする
    chain.reverse()
    // BOS の次からが実データ
    val head = chain[0]  // BOS

    val clauses: MutableList<Pair<ClauseDataUnit, Float>> = mutableListOf()
    val data: MutableList<AzooKeyDictionaryEntry> = mutableListOf()

    var unit = ClauseDataUnit()
    unit.mid = head.entry.mid
    unit.ranges.add(Pair(head.startIndex, head.endIndex))
    clauses.add(Pair(unit, 0f))
    var lastClause = unit
    var lastClauseIndex = 0

    // 4) 前方向に一度だけ処理
    for (i in 1 until chain.size) {
        val node = chain[i] as? RegisteredNode.Node ?: continue
        // 空語はスキップ（azooKey 準拠）
        if (node.entry.surface.isEmpty()) continue

        val prevNode = chain[i - 1]
        val prevRcid = prevNode.entry.rightId ?: AzooKeyCid.PROPER_NOUN
        val currLcid = node.entry.leftId ?: AzooKeyCid.PROPER_NOUN

        if (lastClause.text.isEmpty() || !AzooKeyDicdataStoreUtils.isClause(prevRcid, currLcid)) {
            // 文節継続
            lastClause.text += node.entry.surface
            lastClause.reading += node.entry.reading
            lastClause.ranges.add(Pair(node.startIndex, node.endIndex))
            if ((lastClause.mid == 500 && node.entry.mid != 500) ||
                AzooKeyDicdataStoreUtils.includeMMValueCalculation(node.entry.leftId, node.entry.rightId)
            ) {
                lastClause.mid = node.entry.mid
            }
            data.add(node.entry)
            lastClause.dataEndIndex = data.size - 1
            clauses[lastClauseIndex] = Pair(lastClause, node.totalValue)
        } else {
            // 文節境界
            var newUnit = ClauseDataUnit()
            newUnit.text = node.entry.surface
            newUnit.reading = node.entry.reading
            newUnit.ranges.add(Pair(node.startIndex, node.endIndex))
            if (AzooKeyDicdataStoreUtils.includeMMValueCalculation(node.entry.leftId, node.entry.rightId)) {
                newUnit.mid = node.entry.mid
            }
            lastClause.nextLcid = node.entry.leftId ?: AzooKeyCid.EOS
            clauses[lastClauseIndex] = Pair(lastClause, (chain[i - 1] as? RegisteredNode.Node)?.totalValue ?: 0f)
            data.add(node.entry)
            newUnit.dataEndIndex = data.size - 1
            clauses.add(Pair(newUnit, node.totalValue))
            lastClause = newUnit
            lastClauseIndex = clauses.size - 1
        }
    }
    return CandidateData(clauses = clauses, data = data)
}

/**
 * azooKey [CandidateData](https://github.com/azooKey/AzooKeyKanaKanjiConverter) 相当。
 * 文節区切り情報を持った変換候補データ。
 */
data class CandidateData(
    val clauses: List<Pair<ClauseDataUnit, Float>>,
    val data: List<AzooKeyDictionaryEntry>,
)
