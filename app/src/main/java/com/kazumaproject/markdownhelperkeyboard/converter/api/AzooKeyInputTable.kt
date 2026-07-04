package com.kazumaproject.markdownhelperkeyboard.converter.api

import com.kazumaproject.core.domain.extensions.hiraganaToKatakana

/**
 * AzooKey [InputTable](https://github.com/azooKey/AzooKeyKanaKanjiConverter) 相当。
 * 逐次 `apply` により途中入力（`いっt` 等）を Swift と同じセマンティクスで扱う。
 */
class AzooKeyInputTable private constructor(
    private val trieRoot: TrieNode,
    private val maxKeyCount: Int,
    private val possibleNextsByPrefix: Map<String, List<String>>,
) {
    fun possibleNexts(romanPrefix: String): List<String> =
        possibleNextsByPrefix[romanPrefix.lowercase()].orEmpty()

    /** Swift `InputTable.apply(to:added:)` — 戻り値は deletedCount。 */
    fun apply(buffer: MutableList<Char>, added: Char): Int =
        applyInternal(buffer, InputTableKeyElement.Character(added.lowercaseChar()))

    internal fun applyInternal(buffer: MutableList<Char>, added: InputTableKeyElement): Int {
        val match = matchGreedy(buffer, added) ?: run {
            when (added) {
                is InputTableKeyElement.Character -> {
                    buffer.add(added.char)
                }
                InputTableKeyElement.CompositionSeparator -> Unit
                InputTableKeyElement.Any1 -> Unit
            }
            return 0
        }
        val deleteCount = (match.matchedDepth - 1).coerceAtLeast(0)
        if (deleteCount > 0) {
            repeat(deleteCount) {
                if (buffer.isNotEmpty()) buffer.removeAt(buffer.lastIndex)
            }
        }
        if (match.output.isNotEmpty()) {
            buffer.addAll(match.output)
        }
        return deleteCount
    }

    /** 全文字列を逐次 apply した結果（完成形 convertTarget）。 */
    fun convert(romaji: String): String {
        val buffer = mutableListOf<Char>()
        romaji.lowercase().forEach { apply(buffer, it) }
        return buffer.joinToString("")
    }

    /** カスタムローマ字表（ユーザー編集）から InputTable を構築。 */
    fun toRomajiMap(): Map<String, Pair<String, Int>> {
        return buildRomajiMapFromTrie()
    }

    private fun buildRomajiMapFromTrie(): Map<String, Pair<String, Int>> {
        val result = linkedMapOf<String, Pair<String, Int>>()
        collectMappings(trieRoot, emptyList(), result)
        return result
    }

    private fun collectMappings(
        node: TrieNode,
        path: List<InputTableKeyElement>,
        out: MutableMap<String, Pair<String, Int>>,
    ) {
        if (node.output != null && path.isNotEmpty()) {
            val output = node.output ?: return
            val key = path.filterIsInstance<InputTableKeyElement.Character>()
                .joinToString("") { it.char.toString() }
            if (key.isNotEmpty()) {
                out[key] = output.joinToString("") to path.size
            }
        }
        node.charChildren.forEach { (char, child) ->
            collectMappings(child, path + InputTableKeyElement.Character(char), out)
        }
    }

    private data class GreedyMatch(
        val output: List<Char>,
        val matchedDepth: Int,
    )

    private fun matchGreedy(buffer: List<Char>, added: InputTableKeyElement): GreedyMatch? {
        data class Frame(
            val node: TrieNode,
            val resolvedAny1: Char?,
            val depth: Int,
            val any1Count: Int,
            val keyExactCount: Int,
        )

        var best: GreedyMatch? = null
        var bestDepth = -1
        var bestAny1 = Int.MAX_VALUE
        var bestKeyExact = -1

        fun consider(node: TrieNode, resolvedAny1: Char?, depth: Int, any1Count: Int, keyExactCount: Int) {
            if (node.output != null) {
                val output = node.resolveOutput(resolvedAny1)
                if (output != null) {
                    val better = depth > bestDepth ||
                        (depth == bestDepth && any1Count < bestAny1) ||
                        (depth == bestDepth && any1Count == bestAny1 && keyExactCount > bestKeyExact)
                    if (better) {
                        best = GreedyMatch(output, depth)
                        bestDepth = depth
                        bestAny1 = any1Count
                        bestKeyExact = keyExactCount
                    }
                }
            }
        }

        fun pieceAt(depth: Int): InputTableKeyElement? = when {
            depth == 0 -> added
            else -> {
                val index = buffer.size - depth
                if (index !in buffer.indices) null
                else InputTableKeyElement.Character(buffer[index].lowercaseChar())
            }
        }

        val stack = ArrayDeque<Frame>()
        stack.add(Frame(trieRoot, null, 0, 0, 0))
        while (stack.isNotEmpty()) {
            val top = stack.removeLast()
            consider(top.node, top.resolvedAny1, top.depth, top.any1Count, top.keyExactCount)
            if (top.depth >= maxKeyCount) continue

            when (val piece = pieceAt(top.depth)) {
                null -> continue
                is InputTableKeyElement.Character -> {
                    top.node.charChildren[piece.char]?.let { child ->
                        stack.add(
                            Frame(
                                node = child,
                                resolvedAny1 = top.resolvedAny1,
                                depth = top.depth + 1,
                                any1Count = top.any1Count,
                                keyExactCount = top.keyExactCount + 1,
                            ),
                        )
                    }
                    top.node.any1Child?.let { child ->
                        stack.add(
                            Frame(
                                node = child,
                                resolvedAny1 = piece.char,
                                depth = top.depth + 1,
                                any1Count = top.any1Count + 1,
                                keyExactCount = top.keyExactCount,
                            ),
                        )
                    }
                }
                InputTableKeyElement.CompositionSeparator -> {
                    top.node.separatorChild?.let { child ->
                        stack.add(
                            Frame(
                                node = child,
                                resolvedAny1 = top.resolvedAny1,
                                depth = top.depth + 1,
                                any1Count = top.any1Count,
                                keyExactCount = top.keyExactCount + 1,
                            ),
                        )
                    }
                }
                InputTableKeyElement.Any1 -> {
                    top.node.any1Child?.let { child ->
                        stack.add(
                            Frame(
                                node = child,
                                resolvedAny1 = top.resolvedAny1,
                                depth = top.depth + 1,
                                any1Count = top.any1Count + 1,
                                keyExactCount = top.keyExactCount,
                            ),
                        )
                    }
                }
            }
        }
        return best
    }

    private class TrieNode {
        var output: List<Char>? = null
        val charChildren: MutableMap<Char, TrieNode> = mutableMapOf()
        var separatorChild: TrieNode? = null
        var any1Child: TrieNode? = null

        fun resolveOutput(resolvedAny1: Char?): List<Char>? {
            val raw = output ?: return null
            return raw.map { ch ->
                if (ch == ANY1_PLACEHOLDER) resolvedAny1 ?: return null
                else ch
            }
        }

        fun add(reversedKey: List<InputTableKeyElement>, value: List<Char>) {
            if (reversedKey.isEmpty()) {
                output = value
                return
            }
            val head = reversedKey.first()
            val rest = reversedKey.drop(1)
            when (head) {
                is InputTableKeyElement.Character -> {
                    val next = charChildren.getOrPut(head.char) { TrieNode() }
                    next.add(rest, value)
                }
                InputTableKeyElement.CompositionSeparator -> {
                    val next = separatorChild ?: TrieNode().also { separatorChild = it }
                    next.add(rest, value)
                }
                InputTableKeyElement.Any1 -> {
                    val next = any1Child ?: TrieNode().also { any1Child = it }
                    next.add(rest, value)
                }
            }
        }
    }

    internal sealed class InputTableKeyElement {
        data class Character(val char: Char) : InputTableKeyElement()
        data object CompositionSeparator : InputTableKeyElement()
        data object Any1 : InputTableKeyElement()
    }

    companion object {
        private const val ANY1_PLACEHOLDER = '\u0000'

        val Empty: AzooKeyInputTable = fromStringMap(emptyMap(), includeAzooKeyEndRules = false)

        val Default: AzooKeyInputTable = fromStringMap(
            map = AzooKeyDefaultRoman2KanaData.stringMap,
            includeAzooKeyEndRules = true,
        )

        fun fromRomajiMap(map: Map<String, Pair<String, Int>>): AzooKeyInputTable {
            val stringMap = map.mapValues { (_, pair) -> pair.first }
            return fromStringMap(stringMap, includeAzooKeyEndRules = false)
        }

        fun fromStringMap(
            map: Map<String, String>,
            includeAzooKeyEndRules: Boolean,
        ): AzooKeyInputTable {
            val root = TrieNode()
            var maxKey = 0
            for ((key, value) in map) {
                val elements = key.lowercase().map { InputTableKeyElement.Character(it) }
                maxKey = maxOf(maxKey, elements.size)
                root.add(elements.reversed(), value.toList())
            }
            if (includeAzooKeyEndRules) {
                // Swift: [.piece(.character("n")), .piece(.compositionSeparator)]: [.character("ん")]
                root.add(
                    listOf(
                        InputTableKeyElement.Character('n'),
                        InputTableKeyElement.CompositionSeparator,
                    ).reversed(),
                    listOf('ん'),
                )
                // Swift: [.piece(.character("n")), .any1]: [.character("ん"), .any1]
                root.add(
                    listOf(
                        InputTableKeyElement.Character('n'),
                        InputTableKeyElement.Any1,
                    ).reversed(),
                    listOf('ん', ANY1_PLACEHOLDER),
                )
                maxKey = maxOf(maxKey, 2)
            }
            val possibleNexts = buildPossibleNexts(map)
            return AzooKeyInputTable(root, maxKey, possibleNexts)
        }

        private fun buildPossibleNexts(map: Map<String, String>): Map<String, List<String>> {
            if (map.isEmpty()) return emptyMap()
            val results = mutableMapOf<String, MutableList<String>>()
            for ((key, value) in map) {
                val katakana = value.hiraganaToKatakana()
                for (prefixCount in 1 until key.length) {
                    val prefix = key.substring(0, prefixCount).lowercase()
                    results.getOrPut(prefix) { mutableListOf() }.add(katakana)
                }
            }
            return results
        }
    }
}
