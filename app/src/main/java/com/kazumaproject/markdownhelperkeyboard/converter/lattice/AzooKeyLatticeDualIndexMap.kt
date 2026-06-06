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
                val surface = inputIndexToSurfaceIndexMap[latticeIndex.index]
                if (surface != null) {
                    DualIndex.BothIndex(inputIdx = latticeIndex.index, surfaceIdx = surface)
                } else {
                    DualIndex.InputIndex(latticeIndex.index)
                }
            }
            is AzooKeyLatticeIndex.Surface -> {
                val input = inputIndexToSurfaceIndexMap.entries
                    .firstOrNull { it.value == latticeIndex.index }
                    ?.key
                if (input != null) {
                    DualIndex.BothIndex(inputIdx = input, surfaceIdx = latticeIndex.index)
                } else {
                    DualIndex.SurfaceIndex(latticeIndex.index)
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

sealed class AzooKeyLatticeIndex {
    data class Input(val index: Int) : AzooKeyLatticeIndex()
    data class Surface(val index: Int) : AzooKeyLatticeIndex()

    val isZero: Boolean
        get() = when (this) {
            is Input -> index == 0
            is Surface -> index == 0
        }
}