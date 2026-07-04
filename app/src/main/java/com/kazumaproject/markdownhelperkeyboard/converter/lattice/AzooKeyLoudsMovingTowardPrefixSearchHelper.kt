package com.kazumaproject.markdownhelperkeyboard.converter.lattice

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyLoudsTrie

/**
 * AzooKey [LOUDS.MovingTowardPrefixSearchHelper](https://github.com/azooKey/AzooKeyKanaKanjiConverter) の Kotlin port。
 */
class AzooKeyLoudsMovingTowardPrefixSearchHelper(
    private val louds: AzooKeyLoudsTrie,
) {
    private val indices = mutableListOf<Pair<Int, Int>>()
    private val stack = ArrayDeque<Pair<Int, Int>>()

    fun indicesInDepth(depth: IntRange): List<Int> =
        indices.filter { (d, _) -> d in depth }.map { it.second }

    fun update(target: List<Int>): UpdateResult {
        var updated = false
        var availableMaxIndex = 0
        target.forEachIndexed { index, charId ->
            val stacked = stack.getOrNull(index)
            when {
                stacked != null && stacked.second == charId -> {
                    availableMaxIndex = index
                }
                stacked != null && stacked.second != charId -> {
                    while (stack.size > index) {
                        stack.removeLast()
                    }
                }
            }
            val parentNode = stack.lastOrNull()?.first ?: ROOT_NODE_INDEX
            val child = louds.searchCharNodeIndex(parentNode, charId) ?: return@forEachIndexed
            indices += index to child
            updated = true
            availableMaxIndex = index
            if (stack.size == index) {
                stack.addLast(child to charId)
            } else {
                stack[index] = child to charId
            }
        }
        return UpdateResult(updated = updated, availableMaxIndex = availableMaxIndex)
    }

    data class UpdateResult(
        val updated: Boolean,
        val availableMaxIndex: Int,
    )

    companion object {
        private const val ROOT_NODE_INDEX = 1
    }
}
