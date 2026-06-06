package com.kazumaproject.markdownhelperkeyboard.converter.lattice

import android.content.res.AssetManager
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyPValue

/**
 * 品詞 ID 連接コスト（cb）と意味連接（mm）の lazy ロード。
 */
class AzooKeyConnectionCostStore private constructor(
    private val readCbLine: (Int) -> ByteArray?,
    morphologicalValues: FloatArray?,
) {
    private val ccParsed = BooleanArray(AzooKeyConnectionCostBinaryParser.CID_COUNT)
    private val ccLines = HashMap<Int, FloatArray>()
    private val mmValues: FloatArray? = morphologicalValues

    fun getConnectionCost(formerRightId: Int, latterLeftId: Int): AzooKeyPValue {
        if (formerRightId !in 0 until AzooKeyConnectionCostBinaryParser.CID_COUNT) {
            return AzooKeyConnectionCostBinaryParser.DEFAULT_UNKNOWN_COST
        }
        if (!ccParsed[formerRightId]) {
            loadConnectionLine(formerRightId)
        }
        return ccLines[formerRightId]?.getOrNull(latterLeftId)
            ?: AzooKeyConnectionCostBinaryParser.DEFAULT_UNKNOWN_COST
    }

    fun getMorphologicalCost(formerMid: Int, latterMid: Int): AzooKeyPValue {
        val matrix = mmValues ?: return 0f
        if (formerMid == MID_GENERAL || latterMid == MID_GENERAL) {
            return 0f
        }
        val index = formerMid * AzooKeyConnectionCostBinaryParser.MID_COUNT + latterMid
        return matrix.getOrNull(index) ?: 0f
    }

    private fun loadConnectionLine(former: Int) {
        val bytes = readCbLine(former)
        val line = if (bytes != null) {
            AzooKeyConnectionCostBinaryParser.parseConnectionLine(bytes)
        } else {
            FloatArray(AzooKeyConnectionCostBinaryParser.CID_COUNT) {
                AzooKeyConnectionCostBinaryParser.DEFAULT_UNKNOWN_COST
            }
        }
        ccLines[former] = line
        ccParsed[former] = true
    }

    companion object {
        const val ASSET_CB_DIRECTORY: String = "azookey/cb"
        const val ASSET_MM_FILE: String = "azookey/mm.binary"
        const val MID_GENERAL: Int = 500

        fun fromDirectory(root: java.io.File): AzooKeyConnectionCostStore? {
            val cbDir = java.io.File(root, "cb")
            val mmFile = java.io.File(root, "mm.binary")
            if (!cbDir.isDirectory && !mmFile.isFile) {
                return null
            }
            val morphological = mmFile.takeIf { it.isFile }?.readBytes()?.let {
                AzooKeyConnectionCostBinaryParser.parseMorphologicalMatrix(it)
            }
            return AzooKeyConnectionCostStore(
                readCbLine = { former ->
                    java.io.File(cbDir, "$former.binary").takeIf { it.isFile }?.readBytes()
                },
                morphologicalValues = morphological,
            )
        }

        fun fromAssets(assets: AssetManager): AzooKeyConnectionCostStore? {
            val mmBytes = runCatching {
                assets.open(ASSET_MM_FILE).use { it.readBytes() }
            }.getOrNull()
            val hasCb = runCatching {
                assets.list(ASSET_CB_DIRECTORY)?.isNotEmpty() == true
            }.getOrDefault(false)
            if (mmBytes == null && !hasCb) {
                return null
            }
            val morphological = mmBytes?.let {
                AzooKeyConnectionCostBinaryParser.parseMorphologicalMatrix(it)
            }
            return AzooKeyConnectionCostStore(
                readCbLine = { former ->
                    runCatching {
                        assets.open("$ASSET_CB_DIRECTORY/$former.binary").use { it.readBytes() }
                    }.getOrNull()
                },
                morphologicalValues = morphological,
            )
        }
    }
}