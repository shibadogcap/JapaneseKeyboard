package com.kazumaproject.markdownhelperkeyboard.converter.core

import com.kazumaproject.core.domain.extensions.hiraganaToKatakana
import com.kazumaproject.markdownhelperkeyboard.converter.api.AzooKeyRoman2KanaTransducer
import com.kazumaproject.markdownhelperkeyboard.converter.api.ComposingCount
import com.kazumaproject.markdownhelperkeyboard.converter.api.ComposingText
import com.kazumaproject.markdownhelperkeyboard.converter.api.InputStyle
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyCid
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyPValue
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CandidateType
import com.kazumaproject.markdownhelperkeyboard.converter.lattice.AzooKeyConnectionCostStore
import com.kazumaproject.markdownhelperkeyboard.converter.lattice.AzooKeyGraphRegisteredNode
import com.kazumaproject.markdownhelperkeyboard.converter.lattice.AzooKeyLattice
import com.kazumaproject.markdownhelperkeyboard.converter.lattice.AzooKeyLatticeDualIndexMap
import com.kazumaproject.markdownhelperkeyboard.converter.lattice.AzooKeyLatticeIndex
import com.kazumaproject.markdownhelperkeyboard.converter.lattice.AzooKeyLatticeNodeArray
import com.kazumaproject.markdownhelperkeyboard.converter.lattice.AzooKeyMutableLatticeNode
import com.kazumaproject.markdownhelperkeyboard.converter.lattice.AzooKeyDicdataStoreUtils
import com.kazumaproject.markdownhelperkeyboard.converter.lattice.CandidateData
import com.kazumaproject.markdownhelperkeyboard.converter.lattice.ClauseDataUnit
import com.kazumaproject.markdownhelperkeyboard.converter.lattice.getGraphCandidateData

/**
 * AzooKey [Kana2Kanji](https://github.com/azooKey/AzooKeyKanaKanjiConverter) 相当。
 */
class AzooKeyKana2Kanji(
    val dicdataStore: AzooKeyDicdataFacade,
) {
    fun processClauseCandidate(data: CandidateData): Candidate {
        var mmValue = 0f
        var prevMid = AzooKeyConnectionCostStore.MID_GENERAL
        for ((clause, _) in data.clauses) {
            mmValue += dicdataStore.getMMValue(prevMid, clause.mid)
            prevMid = clause.mid
        }
        val text = data.clauses.joinToString("") { it.first.text }
        if (data.clauses.isEmpty()) {
            return Candidate(
                string = text,
                type = CandidateType.NBEST,
                length = text.length.toUByte(),
                score = 0,
                value = 0f,
                yomi = "",
            )
        }
        val value = data.clauses.last().second + mmValue
        val composingCount = data.clauses.fold(ComposingCount.InputCount(0) as ComposingCount) { acc, (clause, _) ->
            clause.ranges.fold(acc) { inner, (from, to) ->
                ComposingCount.Composite(inner, ComposingCount.SurfaceCount(to - from))
            }
        }.let { count ->
            if (count is ComposingCount.InputCount && count.count == 0) {
                val rubyLength = data.data.sumOf { it.reading.length }
                if (rubyLength > 0) {
                    ComposingCount.SurfaceCount(rubyLength)
                } else {
                    count
                }
            } else {
                count
            }
        }
        return Candidate(
            string = text,
            type = CandidateType.NBEST,
            length = text.length.toUByte(),
            score = value.toInt(),
            value = value,
            yomi = data.data.joinToString("") { it.reading },
            leftId = data.data.firstOrNull()?.leftId?.toShort(),
            rightId = data.data.lastOrNull()?.rightId?.toShort(),
            data = data.data,
            lastMid = data.clauses.last().first.mid,
            rubyCount = data.data.sumOf { it.reading.length },
            composingCount = composingCount,
        )
    }

    suspend fun kana2latticeAll(
        inputData: ComposingText,
        nBest: Int,
        needTypoCorrection: Boolean,
        useMemory: Boolean,
        preprocessedLattice: AzooKeyLattice? = null,
    ): Pair<AzooKeyMutableLatticeNode, AzooKeyLattice> {
        val result = AzooKeyMutableLatticeNode.createResultNode()
        val inputCount = inputData.input.size
        val surfaceCount = inputData.convertTarget.length
        val indexMap = AzooKeyLatticeDualIndexMap(inputData)
        val latticeIndices = indexMap.indices(inputCount = inputCount, surfaceCount = surfaceCount)
        val lattice = preprocessedLattice ?: dicdataStore.buildLattice(
            composingText = inputData,
            needTypoCorrection = needTypoCorrection,
            useMemory = useMemory,
        )

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
                    updateResultNode(node, result)
                } else {
                    updateNextNodes(node, lattice[nextIndex], nBest)
                }
            }
        }
        return result to lattice
    }

    suspend fun buildLatticeWithIncrementalCache(
        inputData: ComposingText,
        incrementalCacheInfo: Pair<ComposingText, AzooKeyLattice>,
        useMemory: Boolean,
        needTypoCorrection: Boolean = false,
    ): AzooKeyLattice {
        return dicdataStore.buildLatticeWithIncrementalCache(
            inputData = inputData,
            inputCount = inputData.input.size,
            surfaceCount = inputData.convertTarget.length,
            incrementalCacheInfo = incrementalCacheInfo,
            needTypoCorrection = needTypoCorrection,
            useMemory = useMemory,
        )
    }

    fun kana2latticeNoChange(
        previousResult: Pair<ComposingText, AzooKeyLattice>,
    ): Pair<AzooKeyMutableLatticeNode, AzooKeyLattice> {
        val inputCount = previousResult.first.input.size
        val surfaceCount = previousResult.first.convertTarget.length
        val result = AzooKeyMutableLatticeNode.createResultNode()
        previousResult.second.forEach { node ->
            val end = node.range.endIndex
            val isTerminal = when (end) {
                is AzooKeyLatticeIndex.Input -> end.value == inputCount
                is AzooKeyLatticeIndex.Surface -> end.value == surfaceCount
            }
            if (!isTerminal) return@forEach
            if (node.prevs.isEmpty()) return@forEach
            if (dicdataStore.shouldBeRemoved(node.entry)) return@forEach
            updateResultNode(node, result)
        }
        return result to previousResult.second
    }

    suspend fun kana2latticeAfterComplete(
        inputData: ComposingText,
        completedData: Candidate,
        nBest: Int,
        previousResult: Pair<ComposingText, AzooKeyLattice>,
        useMemory: Boolean,
        needTypoCorrection: Boolean,
    ): Pair<AzooKeyMutableLatticeNode, AzooKeyLattice> {
        val inputCount = inputData.input.size
        val surfaceCount = inputData.convertTarget.length
        val convertedInputCount = previousResult.first.input.size - inputCount
        val convertedSurfaceCount = previousResult.first.convertTarget.length - surfaceCount
        val start = AzooKeyGraphRegisteredNode.fromLastCandidate(completedData)
        val indexMap = AzooKeyLatticeDualIndexMap(inputData)
        val latticeIndices = indexMap.indices(inputCount = inputCount, surfaceCount = surfaceCount)
        val lattice = previousResult.second.suffix(inputCount = inputCount, surfaceCount = surfaceCount)

        for ((isHead, nodeArray) in lattice.indexedNodes(latticeIndices)) {
            val prevs = if (isHead) listOf(start) else emptyList()
            for (node in nodeArray) {
                node.prevs.clear()
                node.prevs.addAll(prevs)
                node.range = node.range.offseted(
                    inputOffset = -convertedInputCount,
                    surfaceOffset = -convertedSurfaceCount,
                )
            }
        }

        val result = AzooKeyMutableLatticeNode.createResultNode()
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
                if (nextIndex.inputIndex == inputCount || nextIndex.surfaceIndex == surfaceCount) {
                    updateResultNode(node, result)
                } else {
                    updateNextNodes(node, lattice[nextIndex], nBest)
                }
            }
        }
        return result to lattice
    }

    suspend fun kana2latticeChanged(
        inputData: ComposingText,
        nBest: Int,
        counts: com.kazumaproject.markdownhelperkeyboard.converter.api.DifferenceSuffix,
        previousResult: Pair<ComposingText, AzooKeyLattice>,
        needTypoCorrection: Boolean,
        useMemory: Boolean,
    ): Pair<AzooKeyMutableLatticeNode, AzooKeyLattice> {
        val inputCount = inputData.input.size
        val surfaceCount = inputData.convertTarget.length
        val commonInputCount = previousResult.first.input.size - counts.deletedInput
        val commonSurfaceCount = previousResult.first.convertTarget.length - counts.deletedSurface
        val indexMap = AzooKeyLatticeDualIndexMap(inputData)
        val latticeIndices = indexMap.indices(inputCount = inputCount, surfaceCount = surfaceCount)
        var lattice = previousResult.second.prefix(inputCount = commonInputCount, surfaceCount = commonSurfaceCount)

        fun isTerminal(node: AzooKeyMutableLatticeNode): Boolean {
            return when (val end = node.range.endIndex) {
                is AzooKeyLatticeIndex.Input -> end.value == inputCount
                is AzooKeyLatticeIndex.Surface -> end.value == surfaceCount
            }
        }

        var terminalNodes = AzooKeyLattice(
            inputCount = inputCount,
            surfaceCount = surfaceCount,
            rawNodes = lattice.mapIndexedArrays { array ->
                (array.inputIndexedNodes + array.surfaceIndexedNodes).filter(::isTerminal)
            },
        )

        if (!(counts.addedInput == 0 && counts.addedSurface == 0)) {
            val rawNodes = latticeIndices.map { index ->
                val inputRange = index.inputIndex?.let { iIndex ->
                    if (maxOf(commonInputCount, iIndex) < inputCount) {
                        AzooKeyDicdataFacade.InputRange(
                            startIndex = iIndex,
                            endIndexRange = maxOf(commonInputCount, iIndex) until inputCount,
                        )
                    } else {
                        null
                    }
                }
                val surfaceRange = index.surfaceIndex?.let { sIndex ->
                    if (maxOf(commonSurfaceCount, sIndex) < surfaceCount) {
                        AzooKeyDicdataFacade.SurfaceRange(
                            startIndex = sIndex,
                            endIndexRange = maxOf(commonSurfaceCount, sIndex) until surfaceCount,
                        )
                    } else {
                        null
                    }
                }
                dicdataStore.lookupDicdata(
                    composingText = inputData,
                    inputRange = inputRange,
                    surfaceRange = surfaceRange,
                    needTypoCorrection = needTypoCorrection,
                    useMemory = useMemory,
                )
            }
            val addedNodes = AzooKeyLattice(
                inputCount = inputCount,
                surfaceCount = surfaceCount,
                rawNodes = rawNodes,
            )
            for (node in lattice) {
                if (node.prevs.isEmpty()) continue
                if (dicdataStore.shouldBeRemoved(node.entry)) continue
                val nextIndex = indexMap.dualIndex(node.range.endIndex)
                if (nextIndex.surfaceIndex != surfaceCount) {
                    updateNextNodes(node, addedNodes[nextIndex], nBest)
                }
            }
            lattice.merge(addedNodes)
            terminalNodes.merge(addedNodes)
        }

        val result = AzooKeyMutableLatticeNode.createResultNode()
        for (surfaceIndex in 0 until surfaceCount) {
            val isHead = surfaceIndex == 0
            for (node in terminalNodes.surfaceBucket(surfaceIndex)) {
                processChangedTerminalNode(
                    node = node,
                    isHead = isHead,
                    result = result,
                    terminalNodes = terminalNodes,
                    indexMap = indexMap,
                    surfaceCount = surfaceCount,
                    nBest = nBest,
                )
            }
        }
        for (inputIndex in 0 until inputCount) {
            val isHead = surfaceCount == 0 && inputIndex == 0
            for (node in terminalNodes.inputBucket(inputIndex)) {
                processChangedTerminalNode(
                    node = node,
                    isHead = isHead,
                    result = result,
                    terminalNodes = terminalNodes,
                    indexMap = indexMap,
                    surfaceCount = surfaceCount,
                    nBest = nBest,
                )
            }
        }
        return result to lattice
    }

    private fun processChangedTerminalNode(
        node: AzooKeyMutableLatticeNode,
        isHead: Boolean,
        result: AzooKeyMutableLatticeNode,
        terminalNodes: AzooKeyLattice,
        indexMap: AzooKeyLatticeDualIndexMap,
        surfaceCount: Int,
        nBest: Int,
    ) {
        if (node.prevs.isEmpty()) return
        if (dicdataStore.shouldBeRemoved(node.entry)) return
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
            updateResultNode(node, result)
        } else {
            updateNextNodes(node, terminalNodes[nextIndex], nBest)
        }
    }

    private fun updateResultNode(node: AzooKeyMutableLatticeNode, resultNode: AzooKeyMutableLatticeNode) {
        node.values.forEachIndexed { index, value ->
            resultNode.prevs.add(node.getRegisteredNode(index, value))
        }
    }

    private fun updateNextNodes(
        node: AzooKeyMutableLatticeNode,
        nextNodes: AzooKeyLatticeNodeArray,
        nBest: Int,
    ) {
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
                val lastIndex = nextNode.prevs.indexOfLast { it.totalValue >= newValue } + 1
                if (lastIndex >= nBest) return@forEachIndexed
                val newNode = node.getRegisteredNode(index, newValue)
                if (nextNode.prevs.size >= nBest) {
                    nextNode.prevs.removeAt(nextNode.prevs.lastIndex)
                }
                nextNode.prevs.add(lastIndex, newNode)
            }
        }
    }

    fun getCandidateDataFromResult(resultNode: AzooKeyMutableLatticeNode): List<CandidateData> {
        return resultNode.prevs.map { it.getGraphCandidateData() }
    }

    /**
     * AzooKey [Kana2Kanji.getPredictionCandidates](https://github.com/azooKey/AzooKeyKanaKanjiConverter) 相当。
     */
    suspend fun getPredictionCandidates(
        composingText: ComposingText,
        prepart: CandidateData,
        lastClause: ClauseDataUnit,
        nBest: Int,
        useMemory: Boolean,
        roman2Kana: AzooKeyRoman2KanaTransducer = AzooKeyRoman2KanaTransducer.Identity,
    ): List<Candidate> {
        val lastRuby = lastClause.ranges.joinToString("") { range ->
            val from = range.first
            val to = range.second
            composingText.convertTarget
                .substring(from.coerceAtMost(composingText.convertTarget.length), to.coerceAtMost(composingText.convertTarget.length))
                .hiraganaToKatakana()
        }
        if (lastRuby.isEmpty()) return emptyList()

        val prestring = prepart.clauses.joinToString("") { it.first.text }
        var accumulated = ""
        var count = 0
        while (accumulated != prestring && count < prepart.data.size) {
            accumulated += prepart.data[count].surface
            count++
        }
        val datas = prepart.data.take(count)

        val lastCandidate = if (prepart.clauses.isEmpty()) {
            Candidate(
                string = "",
                type = CandidateType.NBEST,
                length = 0u,
                score = 0,
                value = 0f,
                yomi = "",
                lastMid = AzooKeyConnectionCostStore.MID_GENERAL,
            )
        } else {
            processClauseCandidate(prepart)
        }

        val lastRcid = lastCandidate.data.lastOrNull()?.rightId ?: AzooKeyCid.BOS
        val nextLcid = prepart.clauses.lastOrNull()?.first?.nextLcid ?: AzooKeyCid.BOS
        val lastMid = lastCandidate.lastMid
        val lastRubyCount = lastRuby.length
        val ignoreCcValue = dicdataStore.getConnectionCost(lastRcid, nextLcid)
        val composingCount = ComposingCount.Composite(
            lastCandidate.composingCount,
            ComposingCount.SurfaceCount(lastRubyCount),
        )

        val inputStyle = composingText.input.lastOrNull()?.inputStyle ?: InputStyle.Direct
        val dicdata = when (inputStyle) {
            InputStyle.Roman2Kana -> {
                val roman = lastRuby.takeLastWhile { it.isLetter() && it.code < 128 }
                if (roman.isNotEmpty()) {
                    val ruby = lastRuby.dropLast(roman.length)
                    if (ruby.isEmpty()) {
                        emptyList()
                    } else {
                        val possibleNexts = roman2Kana.possibleNexts(roman.lowercase())
                        possibleNexts.flatMap { next ->
                            dicdataStore.getPredictionLOUDSDicdata(
                                key = ruby + next,
                                useMemory = useMemory,
                                includeExactMatch = true,
                            )
                        }
                    }
                } else {
                    dicdataStore.getPredictionLOUDSDicdata(
                        key = lastRuby,
                        useMemory = useMemory,
                        includeExactMatch = false,
                    )
                }
            }
            else -> dicdataStore.getPredictionLOUDSDicdata(
                key = lastRuby,
                useMemory = useMemory,
                includeExactMatch = false,
            )
        }

        val result = mutableListOf<Candidate>()
        val ccLatter = dicdataStore.getCCLatter(lastRcid)
        for (data in dicdata) {
            val includeMm = AzooKeyDicdataStoreUtils.includeMMValueCalculation(data.leftId, data.rightId)
            val mmValue = if (includeMm) dicdataStore.getMMValue(lastMid, data.mid) else 0f
            val ccValue = ccLatter.getOrNull(data.leftId ?: AzooKeyCid.PROPER_NOUN)
                ?: dicdataStore.getConnectionCost(lastRcid, data.leftId ?: AzooKeyCid.PROPER_NOUN)
            val penalty = -(data.reading.length - lastRuby.length).toFloat()
            val wValue = data.value
            val newValue = lastCandidate.value + mmValue + ccValue + wValue + penalty - ignoreCcValue
            val lastIndex = result.indexOfLast { it.value >= newValue } + 1
            if (lastIndex >= nBest) continue
            val nodeData = datas + data
            val candidate = Candidate(
                string = lastCandidate.string + data.surface,
                type = CandidateType.NBEST,
                length = composingText.convertTarget.length.toUByte(),
                score = newValue.toInt(),
                value = newValue,
                yomi = nodeData.joinToString("") { it.reading },
                data = nodeData,
                lastMid = if (includeMm) data.mid else lastMid,
                rubyCount = nodeData.sumOf { it.reading.length },
                composingCount = composingCount,
            )
            if (result.size >= nBest) {
                result.removeAt(result.lastIndex)
            }
            result.add(lastIndex, candidate)
        }
        return result
    }
}
