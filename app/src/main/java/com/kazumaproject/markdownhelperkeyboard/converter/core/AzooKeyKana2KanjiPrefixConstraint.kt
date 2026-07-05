package com.kazumaproject.markdownhelperkeyboard.converter.core

import com.kazumaproject.markdownhelperkeyboard.converter.api.ComposingText
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyCid
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyDictionaryMetadata
import com.kazumaproject.markdownhelperkeyboard.converter.lattice.AzooKeyGraphRegisteredNode
import com.kazumaproject.markdownhelperkeyboard.converter.lattice.AzooKeyLattice
import com.kazumaproject.markdownhelperkeyboard.converter.lattice.AzooKeyLatticeDualIndexMap
import com.kazumaproject.markdownhelperkeyboard.converter.lattice.AzooKeyLatticeNodeArray
import com.kazumaproject.markdownhelperkeyboard.converter.lattice.AzooKeyMutableLatticeNode
import com.kazumaproject.markdownhelperkeyboard.converter.lattice.AzooKeyLatticeIndex
import com.kazumaproject.markdownhelperkeyboard.converter.lattice.AzooKeyLatticeRange

internal suspend fun AzooKeyKana2Kanji.kana2latticeAllWithPrefixConstraint(
    inputData: ComposingText,
    nBest: Int,
    constraint: AzooKeyPrefixConstraint,
    preprocessedLattice: AzooKeyLattice? = null,
    useMemory: Boolean,
): Pair<AzooKeyMutableLatticeNode, AzooKeyLattice> {
    val result = AzooKeyMutableLatticeNode.createResultNode()
    val inputCount = inputData.input.size
    val surfaceCount = inputData.convertTarget.length
    val indexMap = AzooKeyLatticeDualIndexMap(inputData)
    val latticeIndices = indexMap.indices(inputCount = inputCount, surfaceCount = surfaceCount)
    val lattice = preprocessedLattice ?: dicdataStore.buildLattice(
        composingText = inputData,
        needTypoCorrection = false,
        useMemory = useMemory,
    )

    val constraintBytes = constraint.constraint
    for ((isHead, nodeArray) in lattice.indexedNodes(latticeIndices)) {
        for (node in nodeArray) {
            if (node.prevs.isEmpty()) continue
            if (dicdataStore.shouldBeRemoved(node.entry)) continue
            val wValue = node.entry.value
            node.values = if (isHead) {
                node.prevs.map { prev ->
                    prev.totalValue + wValue + dicdataStore.getConnectionCost(
                        prev.entry.rightId ?: AzooKeyCid.BOS,
                        node.entry.leftId ?: AzooKeyCid.PROPER_NOUN,
                    )
                }.toMutableList()
            } else {
                node.prevs.map { prev -> prev.totalValue + wValue }.toMutableList()
            }
            val nextIndex = indexMap.dualIndex(node.range.endIndex)
            if (nextIndex.surfaceIndex == surfaceCount) {
                registerTerminalWithConstraint(node, result, constraint, constraintBytes)
            } else {
                updateNextNodesWithConstraint(
                    node = node,
                    nextNodes = lattice[nextIndex],
                    nBest = nBest,
                    constraint = constraint,
                    constraintBytes = constraintBytes,
                )
            }
        }
    }
    return result to lattice
}

private fun AzooKeyKana2Kanji.registerTerminalWithConstraint(
    node: AzooKeyMutableLatticeNode,
    result: AzooKeyMutableLatticeNode,
    constraint: AzooKeyPrefixConstraint,
    constraintBytes: ByteArray,
) {
    val cLen = constraintBytes.size
    for (index in node.prevs.indices) {
        if (!shouldBypassConstraint(node.entry, constraint)) {
            val (matched, total) = computeMatchedAndTotalLength(
                prev = node.prevs[index],
                currentWord = node.entry.surface,
                constraintBytes = constraintBytes,
            )
            val ok = if (constraint.hasEos) matched == cLen && total == cLen else matched == cLen
            if (!ok) continue
        }
        result.prevs.add(node.getRegisteredNode(index, node.values[index]))
    }
}

private fun AzooKeyKana2Kanji.updateNextNodesWithConstraint(
    node: AzooKeyMutableLatticeNode,
    nextNodes: AzooKeyLatticeNodeArray,
    nBest: Int,
    constraint: AzooKeyPrefixConstraint,
    constraintBytes: ByteArray,
) {
    val cLen = constraintBytes.size
    val mtPerPrev = node.prevs.indices.map { index ->
        computeMatchedAndTotalLength(node.prevs[index], node.entry.surface, constraintBytes)
    }
    val ccLatter = dicdataStore.getCCLatter(node.entry.rightId ?: AzooKeyCid.PROPER_NOUN)
    for (nextNode in nextNodes) {
        if (dicdataStore.shouldBeRemoved(nextNode.entry)) continue
        val ccValue = ccLatter.getOrNull(nextNode.entry.leftId ?: AzooKeyCid.PROPER_NOUN)
            ?: dicdataStore.getConnectionCost(
                node.entry.rightId ?: AzooKeyCid.PROPER_NOUN,
                nextNode.entry.leftId ?: AzooKeyCid.PROPER_NOUN,
            )
        node.values.forEachIndexed { index, value ->
            val newValue = ccValue + value
            if (!shouldBypassConstraint(nextNode.entry, constraint)) {
                val (matchedPrev, totalPrev) = mtPerPrev[index]
                if (matchedPrev != minOf(totalPrev, cLen)) return@forEachIndexed
                val (matchedExt, mismatch) = extendMatched(matchedPrev, nextNode.entry.surface, constraintBytes)
                if (mismatch) return@forEachIndexed
                val newTotal = totalPrev + nextNode.entry.surface.toByteArray(Charsets.UTF_8).size
                val ok = if (constraint.hasEos) {
                    matchedExt == newTotal && newTotal < cLen
                } else {
                    matchedExt == cLen || (newTotal <= cLen && matchedExt == newTotal)
                }
                if (!ok) return@forEachIndexed
            }
            val lastIndex = nextNode.prevs.indexOfLast { it.totalValue >= newValue } + 1
            if (lastIndex >= nBest) return@forEachIndexed
            if (nextNode.prevs.size >= nBest) {
                nextNode.prevs.removeAt(nextNode.prevs.lastIndex)
            }
            nextNode.prevs.add(lastIndex, node.getRegisteredNode(index, newValue))
        }
    }
}

private fun shouldBypassConstraint(
    entry: com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyDictionaryEntry,
    constraint: AzooKeyPrefixConstraint,
): Boolean {
    if (constraint.ignoreMemoryAndUserDictionary) return false
    return entry.metadata.contains(AzooKeyDictionaryMetadata.Learned) ||
        entry.metadata.contains(AzooKeyDictionaryMetadata.FromUserDictionary) ||
        entry.sourceKind == com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyDictionarySourceKind.Memory ||
        entry.sourceKind == com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyDictionarySourceKind.User
}

private fun computeMatchedAndTotalLength(
    prev: AzooKeyGraphRegisteredNode,
    currentWord: String,
    constraintBytes: ByteArray,
): Pair<Int, Int> {
    val words = mutableListOf<String>()
    var cursor: AzooKeyGraphRegisteredNode? = prev
    while (cursor != null) {
        if (cursor.entry.surface.isNotEmpty()) words.add(cursor.entry.surface)
        cursor = cursor.prev
    }
    words.reverse()
    words.add(currentWord)
    val allBytes = words.joinToString("") { it }.toByteArray(Charsets.UTF_8)
    var ci = 0
    outer@ for (word in words) {
        if (ci >= constraintBytes.size) break
        for (byte in word.toByteArray(Charsets.UTF_8)) {
            if (ci >= constraintBytes.size) break@outer
            if (byte != constraintBytes[ci]) break@outer
            ci++
        }
    }
    return ci to allBytes.size
}

private fun extendMatched(
    matched: Int,
    nextWord: String,
    constraintBytes: ByteArray,
): Pair<Int, Boolean> {
    var ci = matched
    if (ci >= constraintBytes.size) return ci to false
    for (byte in nextWord.toByteArray(Charsets.UTF_8)) {
        if (ci >= constraintBytes.size) return ci to false
        if (byte != constraintBytes[ci]) return ci to true
        ci++
    }
    return ci to false
}

internal fun AzooKeyKana2Kanji.candidateSatisfies(candidate: com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate, constraint: AzooKeyPrefixConstraint): Boolean {
    val bytes = candidate.string.toByteArray(Charsets.UTF_8)
    return if (constraint.hasEos) {
        bytes.contentEquals(constraint.constraint)
    } else {
        bytes.size >= constraint.constraint.size &&
            constraint.constraint.indices.all { bytes[it] == constraint.constraint[it] }
    }
}

internal fun AzooKeyKana2Kanji.zenzaiLatticeInputData(inputData: ComposingText): ComposingText {
    return if (inputData.isAtEndIndex) inputData else inputData.prefixToCursorPosition()
}

internal fun AzooKeyKana2Kanji.zenzaiInputCursorPosition(inputData: ComposingText): Int? {
    return if (inputData.isAtEndIndex) null else inputData.convertTargetCursorPosition
}

internal fun ComposingText.prefixToCursorPosition(): ComposingText {
    val cursor = convertTargetCursorPosition.coerceIn(0, convertTarget.length)
    return copy(
        convertTarget = convertTarget.take(cursor),
        convertTargetCursorPosition = cursor,
        input = input.take(cursor.coerceAtMost(input.size)),
    )
}
