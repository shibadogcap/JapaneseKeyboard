package com.kazumaproject.markdownhelperkeyboard.converter.zenz

/** Swift [ExperimentalTypoCorrectionConfig](https://github.com/azooKey/AzooKeyKanaKanjiConverter) 相当。 */
data class AzooKeyExperimentalTypoCorrectionConfig(
    val beamSize: Int = 32,
    val topK: Int = 64,
    val nBest: Int = 5,
    val maxSteps: Int? = null,
    val alpha: Float = 2.0f,
    val beta: Float = 3.0f,
    val gamma: Float = 2.0f,
) {
    init {
        require(beamSize >= 1)
        require(topK >= 1)
        require(nBest >= 1)
    }
}

/** Swift [ZenzaiTypoCandidate](https://github.com/azooKey/AzooKeyKanaKanjiConverter) 相当。 */
data class AzooKeyZenzaiTypoCandidate(
    val correctedInput: String,
    val convertedText: String,
    val score: Float,
    val lmScore: Float,
    val channelCost: Float,
    val prominence: Float,
)

class AzooKeyZenzaiTypoGenerationCache {
    var prompt: String = ""
    var promptTokenIds: IntArray = IntArray(0)
    var vocabSize: Int = 0
    val nextLogProbCache: MutableMap<List<Int>, FloatArray> = mutableMapOf()
    val encodeCache: MutableMap<String, IntArray> = mutableMapOf()
    val tokenCharCache: MutableMap<Int, Char?> = mutableMapOf()

    fun invalidateAll() {
        prompt = ""
        promptTokenIds = IntArray(0)
        vocabSize = 0
        nextLogProbCache.clear()
        encodeCache.clear()
        tokenCharCache.clear()
    }
}

enum class AzooKeyZenzaiTypoKeyTopologyId {
    MacOsStandardQwerty,
    IOsStandardQwerty,
    IOsStandardFlickTenkey,
}

class AzooKeyZenzaiTypoKeyTopology private constructor(
    val id: AzooKeyZenzaiTypoKeyTopologyId,
    private val neighborDistancesByCharacter: Map<Char, Map<Char, Float>>,
) {
    fun neighborDistances(around: Char): Map<Char, Float> =
        neighborDistancesByCharacter[around].orEmpty()

    companion object {
        val MacOsStandardQwerty = buildCoordinateTopology(
            id = AzooKeyZenzaiTypoKeyTopologyId.MacOsStandardQwerty,
            coordinates = mapOf(
                '1' to (-1.0f to 0f), '2' to (0.25f to 0f), '3' to (1.25f to 0f), '4' to (2.25f to 0f),
                '5' to (3.25f to 0f), '6' to (4.25f to 0f), '7' to (5.25f to 0f), '8' to (6.25f to 0f),
                '9' to (7.25f to 0f), '0' to (8.25f to 0f), '-' to (9.25f to 0f), '^' to (10.25f to 0f),
                'q' to (0.00f to 1f), 'w' to (1.00f to 1f), 'e' to (2.00f to 1f), 'r' to (3.00f to 1f),
                't' to (4.00f to 1f), 'y' to (5.00f to 1f), 'u' to (6.00f to 1f), 'i' to (7.00f to 1f),
                'o' to (8.00f to 1f), 'p' to (9.00f to 1f), '@' to (10.00f to 1f), '[' to (11.00f to 1f),
                'a' to (0.25f to 2f), 's' to (1.25f to 2f), 'd' to (2.25f to 2f), 'f' to (3.25f to 2f),
                'g' to (4.25f to 2f), 'h' to (5.25f to 2f), 'j' to (6.25f to 2f), 'k' to (7.25f to 2f),
                'l' to (8.25f to 2f), ';' to (9.25f to 2f), ']' to (10.25f to 2f),
                'z' to (0.80f to 3f), 'x' to (1.80f to 3f), 'c' to (2.80f to 3f), 'v' to (3.80f to 3f),
                'b' to (4.80f to 3f), 'n' to (5.80f to 3f), 'm' to (6.80f to 3f), ',' to (7.80f to 3f),
                '.' to (8.80f to 3f), '/' to (9.80f to 3f), '_' to (10.80f to 3f),
            ),
        )

        val IOsStandardQwerty = buildCoordinateTopology(
            id = AzooKeyZenzaiTypoKeyTopologyId.IOsStandardQwerty,
            coordinates = mapOf(
                'q' to (0.00f to 1.0f), 'w' to (1.00f to 1.0f), 'e' to (2.00f to 1.0f), 'r' to (3.00f to 1.0f),
                't' to (4.00f to 1.0f), 'y' to (5.00f to 1.0f), 'u' to (6.00f to 1.0f), 'i' to (7.00f to 1.0f),
                'o' to (8.00f to 1.0f), 'p' to (9.00f to 1.0f),
                'a' to (0.50f to 2.5f), 's' to (1.50f to 2.5f), 'd' to (2.50f to 2.5f), 'f' to (3.50f to 2.5f),
                'g' to (4.50f to 2.5f), 'h' to (5.50f to 2.5f), 'j' to (6.50f to 2.5f), 'k' to (7.50f to 2.5f),
                'l' to (8.50f to 2.5f),
                'z' to (1.50f to 4.0f), 'x' to (2.50f to 4.0f), 'c' to (3.50f to 4.0f), 'v' to (4.50f to 4.0f),
                'b' to (5.50f to 4.0f), 'n' to (6.50f to 4.0f), 'm' to (7.50f to 4.0f),
            ),
        )

        val IOsStandardFlickTenkey = buildTenkeyTopology(
            groups = listOf(
                "アイウエオ", "カキクケコ", "ガギグゲゴ", "サシスセソ", "ザジズゼゾ",
                "タチツテト", "ダヂヅデド", "ナニヌネノ", "ハヒフヘホ", "バビブベボ",
                "パピプペポ", "マミムメモ", "ヤユヨ", "ャュョ", "ラリルレロ", "ワヲンー",
            ),
        )

        private fun buildCoordinateTopology(
            id: AzooKeyZenzaiTypoKeyTopologyId,
            coordinates: Map<Char, Pair<Float, Float>>,
            neighborMaxDistance: Float = 1.65f,
        ): AzooKeyZenzaiTypoKeyTopology {
            val distances = mutableMapOf<Char, MutableMap<Char, Float>>()
            for ((source, sourcePoint) in coordinates) {
                for ((target, targetPoint) in coordinates) {
                    if (source == target) continue
                    val dx = sourcePoint.first - targetPoint.first
                    val dy = sourcePoint.second - targetPoint.second
                    val distance = kotlin.math.sqrt(dx * dx + dy * dy)
                    if (distance <= neighborMaxDistance) {
                        distances.getOrPut(source) { mutableMapOf() }[target] = distance
                    }
                }
            }
            return AzooKeyZenzaiTypoKeyTopology(id, distances)
        }

        private fun buildTenkeyTopology(groups: List<String>): AzooKeyZenzaiTypoKeyTopology {
            val distances = mutableMapOf<Char, MutableMap<Char, Float>>()
            for (group in groups) {
                val chars = group.toList()
                for (source in chars) {
                    for (target in chars) {
                        if (source == target) continue
                        distances.getOrPut(source) { mutableMapOf() }[target] = 1.0f
                    }
                }
            }
            return AzooKeyZenzaiTypoKeyTopology(
                AzooKeyZenzaiTypoKeyTopologyId.IOsStandardFlickTenkey,
                distances,
            )
        }
    }
}

internal fun typoCorrectionPromptPrefix(leftSideContext: String): String {
    val inputTag = "\uEE00"
    val contextTag = "\uEE02"
    return if (leftSideContext.isEmpty()) {
        inputTag
    } else {
        contextTag + leftSideContext + inputTag
    }
}
