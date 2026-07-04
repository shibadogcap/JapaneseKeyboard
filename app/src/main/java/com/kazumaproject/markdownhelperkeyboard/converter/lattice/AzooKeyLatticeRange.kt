package com.kazumaproject.markdownhelperkeyboard.converter.lattice

import com.kazumaproject.markdownhelperkeyboard.converter.api.ComposingCount

sealed class AzooKeyLatticeIndex {
    abstract val isZero: Boolean

    data class Surface(val value: Int) : AzooKeyLatticeIndex() {
        override val isZero: Boolean get() = value == 0
    }

    data class Input(val value: Int) : AzooKeyLatticeIndex() {
        override val isZero: Boolean get() = value == 0
    }
}

sealed class AzooKeyLatticeRange {
    abstract val startIndex: AzooKeyLatticeIndex
    abstract val endIndex: AzooKeyLatticeIndex

    abstract fun offseted(inputOffset: Int, surfaceOffset: Int): AzooKeyLatticeRange

    data class Surface(val from: Int, val to: Int) : AzooKeyLatticeRange() {
        override val startIndex: AzooKeyLatticeIndex get() = AzooKeyLatticeIndex.Surface(from)
        override val endIndex: AzooKeyLatticeIndex get() = AzooKeyLatticeIndex.Surface(to)
        override fun offseted(inputOffset: Int, surfaceOffset: Int): AzooKeyLatticeRange =
            Surface(from + surfaceOffset, to + surfaceOffset)

        val count: ComposingCount get() = ComposingCount.SurfaceCount(to - from)
    }

    data class Input(val from: Int, val to: Int) : AzooKeyLatticeRange() {
        override val startIndex: AzooKeyLatticeIndex get() = AzooKeyLatticeIndex.Input(from)
        override val endIndex: AzooKeyLatticeIndex get() = AzooKeyLatticeIndex.Input(to)
        override fun offseted(inputOffset: Int, surfaceOffset: Int): AzooKeyLatticeRange =
            Input(from + inputOffset, to + inputOffset)

        val count: ComposingCount get() = ComposingCount.InputCount(to - from)
    }

    companion object {
        val zero: AzooKeyLatticeRange = Input(0, 0)
    }
}
