package com.kazumaproject.markdownhelperkeyboard.converter.lattice

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyDictionaryConnectionIdResolver
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyDictionaryEntry
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyPValue
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CandidateType

/**
 * AzooKey lattice 上の Viterbi 風デコード（CID 連接 + entry value）。
 * 品詞連接 cb バイナリ未同梱時は [DEFAULT_CONNECTION_COST] を使う。
 */
class AzooKeyLatticeDecoder(
    private val connectionCost: (formerRightId: Int, latterLeftId: Int) -> AzooKeyPValue = { _, _ ->
        DEFAULT_CONNECTION_COST
    },
    private val morphologicalCost: (formerMid: Int, latterMid: Int) -> AzooKeyPValue = { _, _ ->
        0f
    },
    private val connectionIdResolver: AzooKeyDictionaryConnectionIdResolver =
        AzooKeyDictionaryConnectionIdResolver(),
    private val beamWidth: Int = DEFAULT_BEAM_WIDTH,
) {
    data class Path(
        val entries: List<AzooKeyDictionaryEntry>,
        val endIndex: Int,
        val totalValue: AzooKeyPValue,
        val lastRightId: Int,
        val lastMid: Int = BOS_MID,
    )

    fun decode(
        inputLength: Int,
        nodes: List<AzooKeyLatticeNode>,
        nBest: Int,
    ): List<Candidate> {
        if (inputLength <= 0 || nBest <= 0) return emptyList()
        val dp = Array(inputLength + 1) { mutableListOf<Path>() }
        dp[0] += Path(
            entries = emptyList(),
            endIndex = 0,
            totalValue = 0f,
            lastRightId = BOS_RIGHT_ID,
            lastMid = BOS_MID,
        )

        nodes
            .sortedWith(compareBy({ it.endIndex }, { it.startIndex }))
            .forEach { node ->
                val startPaths = dp[node.startIndex]
                if (startPaths.isEmpty()) return@forEach
                val resolved = connectionIdResolver.resolve(node.entry)
                val extensions = startPaths.map { path ->
                    val linkCost = connectionCost(path.lastRightId, resolved.leftId ?: DEFAULT_LEFT_ID)
                    val mmCost = morphologicalCost(path.lastMid, resolved.mid)
                    Path(
                        entries = path.entries + resolved,
                        endIndex = node.endIndex,
                        totalValue = path.totalValue + linkCost + mmCost + resolved.value,
                        lastRightId = resolved.rightId ?: DEFAULT_RIGHT_ID,
                        lastMid = resolved.mid,
                    )
                }
                val merged = (dp[node.endIndex] + extensions)
                    .sortedByDescending { it.totalValue }
                    .take(beamWidth)
                dp[node.endIndex].clear()
                dp[node.endIndex].addAll(merged)
            }

        return dp[inputLength]
            .filter { path -> path.entries.isNotEmpty() }
            .distinctBy { path -> path.entries.joinToString(separator = "\u0000") { "${it.reading}\t${it.surface}" } }
            .take(nBest)
            .map { path -> path.toCandidate() }
    }

    private fun Path.toCandidate(): Candidate {
        val surface = entries.joinToString("") { it.surface }
        val reading = entries.joinToString("") { it.reading }
        val last = entries.last()
        val displayValue = entries.sumOf { it.value.toDouble() }.toFloat()
        return Candidate(
            string = surface,
            type = CandidateType.NBEST,
            length = reading.length.toUByte(),
            score = totalValue.toInt(),
            value = displayValue,
            yomi = reading,
            leftId = entries.first().leftId?.toShort(),
            rightId = last.rightId?.toShort(),
        )
    }

    companion object {
        const val DEFAULT_BEAM_WIDTH: Int = 24
        const val DEFAULT_CONNECTION_COST: Float = -25f
        const val BOS_RIGHT_ID: Int = 0
        const val BOS_MID: Int = AzooKeyConnectionCostStore.MID_GENERAL
        const val DEFAULT_LEFT_ID: Int = 1285
        const val DEFAULT_RIGHT_ID: Int = 1285
    }
}