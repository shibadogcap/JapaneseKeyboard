package com.kazumaproject.markdownhelperkeyboard.converter.lattice

import com.kazumaproject.markdownhelperkeyboard.converter.api.ComposingText
import com.kazumaproject.markdownhelperkeyboard.converter.api.ComposingTextIndexMapper

/**
 * AzooKey [LatticeDualIndexMap](https://github.com/azooKey/AzooKeyKanaKanjiConverter) 相当。
 */
class AzooKeyLatticeDualIndexMap(
    composingText: ComposingText,
) {
    private val inputIndexToSurfaceIndexMap: Map<Int, Int> =
        ComposingTextIndexMapper.inputIndexToSurfaceIndexMap(composingText)

    sealed class DualIndex {
        data class InputIndex(val index: Int) : DualIndex()
        data class SurfaceIndex(val index: Int) : DualIndex()
        data class BothIndex(val inputIdx: Int, val surfaceIdx: Int) : DualIndex()

        val inputIndex: Int?
            get() = when (this) {
                is InputIndex -> index
                is BothIndex -> inputIdx
                is SurfaceIndex -> null
            }

        val surfaceIndex: Int?
            get() = when (this) {
                is SurfaceIndex -> index
                is BothIndex -> surfaceIdx
                is InputIndex -> null
            }
    }

    fun dualIndex(latticeIndex: AzooKeyLatticeIndex): DualIndex {
        return when (latticeIndex) {
            is AzooKeyLatticeIndex.Input -> {
                val surface = inputIndexToSurfaceIndexMap[latticeIndex.value]
                if (surface != null) {
                    DualIndex.BothIndex(inputIdx = latticeIndex.value, surfaceIdx = surface)
                } else {
                    DualIndex.InputIndex(latticeIndex.value)
                }
            }
            is AzooKeyLatticeIndex.Surface -> {
                val input = inputIndexToSurfaceIndexMap.entries
                    .firstOrNull { it.value == latticeIndex.value }
                    ?.key
                if (input != null) {
                    DualIndex.BothIndex(inputIdx = input, surfaceIdx = latticeIndex.value)
                } else {
                    DualIndex.SurfaceIndex(latticeIndex.value)
                }
            }
        }
    }

    fun indices(inputCount: Int, surfaceCount: Int): List<DualIndex> {
        val result = mutableListOf<DualIndex>()
        var surfacePointer = 0
        for (inputIndex in 0 until inputCount) {
            val surfaceIndex = inputIndexToSurfaceIndexMap[inputIndex]
            if (surfaceIndex != null) {
                for (j in minOf(surfacePointer, surfaceIndex) until surfaceIndex) {
                    result += DualIndex.SurfaceIndex(j)
                }
                if (surfacePointer <= surfaceIndex && surfaceIndex < surfaceCount) {
                    result += DualIndex.BothIndex(inputIdx = inputIndex, surfaceIdx = surfaceIndex)
                } else {
                    result += DualIndex.InputIndex(inputIndex)
                }
                surfacePointer = surfaceIndex + 1
            } else {
                result += DualIndex.InputIndex(inputIndex)
            }
        }
        for (j in minOf(surfaceCount, surfacePointer) until surfaceCount) {
            result += DualIndex.SurfaceIndex(j)
        }
        return result
    }
}
