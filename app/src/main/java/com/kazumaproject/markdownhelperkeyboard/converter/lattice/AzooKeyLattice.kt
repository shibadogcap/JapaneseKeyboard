package com.kazumaproject.markdownhelperkeyboard.converter.lattice

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyCid
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyDictionaryEntry
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyMid
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyDictionarySourceKind
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyPValue

class AzooKeyMutableLatticeNode(
    val entry: AzooKeyDictionaryEntry,
    var range: AzooKeyLatticeRange,
) {
    val prevs: MutableList<AzooKeyGraphRegisteredNode> = mutableListOf()
    var values: MutableList<AzooKeyPValue> = mutableListOf(entry.value)

    fun getRegisteredNode(index: Int, value: AzooKeyPValue): AzooKeyGraphRegisteredNode {
        return AzooKeyGraphRegisteredNode.Node(
            entry = entry,
            prev = prevs[index],
            totalValue = value,
            range = range,
        )
    }

    companion object {
        fun createResultNode(): AzooKeyMutableLatticeNode = AzooKeyMutableLatticeNode(
            entry = AzooKeyDictionaryEntry(
                surface = "",
                reading = "",
                leftId = AzooKeyCid.EOS,
                rightId = AzooKeyCid.EOS,
                mid = AzooKeyMid.GENERAL,
                wordCost = 0,
                value = 0f,
                sourceKind = AzooKeyDictionarySourceKind.System,
            ),
            range = AzooKeyLatticeRange.zero,
        )

        @Deprecated("Use createResultNode(); shared EOS node must not accumulate conversion state")
        val eosNode: AzooKeyMutableLatticeNode get() = createResultNode()
    }
}

class AzooKeyLatticeNodeArray(
    val inputIndexedNodes: List<AzooKeyMutableLatticeNode>,
    val surfaceIndexedNodes: List<AzooKeyMutableLatticeNode>,
) : Iterable<AzooKeyMutableLatticeNode> {
    override fun iterator(): Iterator<AzooKeyMutableLatticeNode> {
        return (inputIndexedNodes + surfaceIndexedNodes).iterator()
    }
}

class AzooKeyLattice private constructor(
    private val inputIndexedNodes: MutableList<MutableList<AzooKeyMutableLatticeNode>>,
    private val surfaceIndexedNodes: MutableList<MutableList<AzooKeyMutableLatticeNode>>,
) : Iterable<AzooKeyMutableLatticeNode> {
    constructor() : this(mutableListOf(), mutableListOf())

    constructor(
        inputCount: Int,
        surfaceCount: Int,
        rawNodes: List<List<AzooKeyMutableLatticeNode>>,
    ) : this(
        inputIndexedNodes = MutableList(inputCount) { mutableListOf() },
        surfaceIndexedNodes = MutableList(surfaceCount) { mutableListOf() },
    ) {
        rawNodes.forEach { nodes ->
            val first = nodes.firstOrNull() ?: return@forEach
            when (val start = first.range.startIndex) {
                is AzooKeyLatticeIndex.Input -> {
                    if (start.value < inputIndexedNodes.size) {
                        inputIndexedNodes[start.value].addAll(nodes)
                    }
                }
                is AzooKeyLatticeIndex.Surface -> {
                    if (start.value < surfaceIndexedNodes.size) {
                        surfaceIndexedNodes[start.value].addAll(nodes)
                    }
                }
            }
        }
    }

    operator fun get(index: AzooKeyLatticeDualIndexMap.DualIndex): AzooKeyLatticeNodeArray {
        val inputNodes = index.inputIndex?.let { inputIndexedNodes.getOrNull(it) }.orEmpty()
        val surfaceNodes = index.surfaceIndex?.let { surfaceIndexedNodes.getOrNull(it) }.orEmpty()
        return AzooKeyLatticeNodeArray(inputNodes, surfaceNodes)
    }

    fun indexedNodes(
        indices: List<AzooKeyLatticeDualIndexMap.DualIndex>,
    ): Sequence<Pair<Boolean, AzooKeyLatticeNodeArray>> {
        return indices.asSequence().map { index ->
            val isHead = index.inputIndex == 0 && index.surfaceIndex == 0
            isHead to this[index]
        }
    }

    fun prefix(inputCount: Int, surfaceCount: Int): AzooKeyLattice {
        fun filterNodes(nodes: List<AzooKeyMutableLatticeNode>): List<AzooKeyMutableLatticeNode> {
            return nodes.filter { node ->
                when (val end = node.range.endIndex) {
                    is AzooKeyLatticeIndex.Input -> end.value <= inputCount
                    is AzooKeyLatticeIndex.Surface -> end.value <= surfaceCount
                }
            }
        }
        return AzooKeyLattice(
            inputIndexedNodes = inputIndexedNodes.take(inputCount).map { filterNodes(it).toMutableList() }.toMutableList(),
            surfaceIndexedNodes = surfaceIndexedNodes.take(surfaceCount).map { filterNodes(it).toMutableList() }.toMutableList(),
        )
    }

    fun suffix(inputCount: Int, surfaceCount: Int): AzooKeyLattice {
        return AzooKeyLattice(
            inputIndexedNodes = inputIndexedNodes.takeLast(inputCount).map { it.toMutableList() }.toMutableList(),
            surfaceIndexedNodes = surfaceIndexedNodes.takeLast(surfaceCount).map { it.toMutableList() }.toMutableList(),
        )
    }

    fun merge(other: AzooKeyLattice) {
        other.inputIndexedNodes.forEachIndexed { index, nodeArray ->
            if (index < inputIndexedNodes.size) {
                inputIndexedNodes[index].addAll(nodeArray)
            } else {
                inputIndexedNodes.add(nodeArray.toMutableList())
            }
        }
        if (inputIndexedNodes.size < other.inputIndexedNodes.size) {
            inputIndexedNodes.addAll(
                other.inputIndexedNodes.drop(inputIndexedNodes.size).map { it.toMutableList() },
            )
        }
        other.surfaceIndexedNodes.forEachIndexed { index, nodeArray ->
            if (index < surfaceIndexedNodes.size) {
                surfaceIndexedNodes[index].addAll(nodeArray)
            } else {
                surfaceIndexedNodes.add(nodeArray.toMutableList())
            }
        }
        if (surfaceIndexedNodes.size < other.surfaceIndexedNodes.size) {
            surfaceIndexedNodes.addAll(
                other.surfaceIndexedNodes.drop(surfaceIndexedNodes.size).map { it.toMutableList() },
            )
        }
    }

    fun resetNodeStates() {
        fun reset(nodes: List<AzooKeyMutableLatticeNode>) {
            nodes.forEach { node ->
                node.prevs.clear()
                node.values.clear()
                if (node.range.startIndex.isZero) {
                    node.prevs.add(AzooKeyGraphRegisteredNode.BOS)
                }
            }
        }
        inputIndexedNodes.forEach(::reset)
        surfaceIndexedNodes.forEach(::reset)
    }

    fun mapIndexedArrays(
        transform: (AzooKeyLatticeNodeArray) -> List<AzooKeyMutableLatticeNode>,
    ): List<List<AzooKeyMutableLatticeNode>> {
        val result = mutableListOf<List<AzooKeyMutableLatticeNode>>()
        surfaceIndexedNodes.forEach { nodes ->
            if (nodes.isNotEmpty()) {
                result.add(transform(AzooKeyLatticeNodeArray(emptyList(), nodes)))
            }
        }
        inputIndexedNodes.forEach { nodes ->
            if (nodes.isNotEmpty()) {
                result.add(transform(AzooKeyLatticeNodeArray(nodes, emptyList())))
            }
        }
        return result
    }

    fun surfaceBucket(index: Int): List<AzooKeyMutableLatticeNode> =
        surfaceIndexedNodes.getOrNull(index).orEmpty()

    fun inputBucket(index: Int): List<AzooKeyMutableLatticeNode> =
        inputIndexedNodes.getOrNull(index).orEmpty()

    fun indexedNodeArrays(): List<AzooKeyLatticeNodeArray> {
        return buildList {
            surfaceIndexedNodes.forEach { nodes ->
                if (nodes.isNotEmpty()) add(AzooKeyLatticeNodeArray(emptyList(), nodes))
            }
            inputIndexedNodes.forEach { nodes ->
                if (nodes.isNotEmpty()) add(AzooKeyLatticeNodeArray(nodes, emptyList()))
            }
        }
    }

    override fun iterator(): Iterator<AzooKeyMutableLatticeNode> {
        return sequence {
            surfaceIndexedNodes.forEach { yieldAll(it) }
            inputIndexedNodes.forEach { yieldAll(it) }
        }.iterator()
    }
}
