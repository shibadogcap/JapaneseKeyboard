package com.kazumaproject.markdownhelperkeyboard.converter.lattice

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyCid
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyDictionaryEntry
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CandidateType

data class ClauseDataUnit(
    var text: String = "",
    var reading: String = "",
    var mid: Int = 501,
    var nextLcid: Int = AzooKeyCid.EOS,
    /** 文節を構成する LatticeRange。azooKey `LatticeNode.range` 相当（startIndex, endIndex のペア）。 */
    val ranges: MutableList<Pair<Int, Int>> = mutableListOf(),
    /** この文節を構成する最後のエントリの [CandidateData.data] 上のインデックス。 */
    var dataEndIndex: Int = -1,
)

object AzooKeyDicdataStoreUtils {
    private val BOS_EOS_wordIDs = setOf(AzooKeyCid.BOS, AzooKeyCid.EOS)
    private val PREPOSITION_wordIDs = setOf(1315, 6, 557, 558, 559, 560)
    private val INPOSITION_wordIDs = buildSet {
        addAll(561 until 868)
        addAll(1283 until 1297)
        addAll(1306 until 1310)
        addAll(11 until 53)
        addAll(555 until 557)
        addAll(1281 until 1283)
        addAll(listOf(1314, 3, 2, 4, 5, 1, 9))
    }

    val wordTypes: ByteArray = ByteArray(1320) { cid ->
        judgeWordType(cid)
    }

    private fun judgeWordType(cid: Int): Byte {
        if (BOS_EOS_wordIDs.contains(cid)) return 3 // BOS/EOS
        if (PREPOSITION_wordIDs.contains(cid)) return 0 // 前置
        if (INPOSITION_wordIDs.contains(cid)) return 1 // 内容
        return 2 // 後置
    }

    fun isClause(formerRcid: Int, latterLcid: Int): Boolean {
        val latterWordType = wordTypes.getOrNull(latterLcid)?.toInt() ?: 2
        if (latterWordType == 3) return false
        val formerWordType = wordTypes.getOrNull(formerRcid)?.toInt() ?: 2
        if (formerWordType == 3) return false

        if (latterWordType == 0) {
            return formerWordType != 0
        }
        if (latterWordType == 1) {
            return formerWordType != 0
        }
        return false
    }

    /**
     * [DicdataStore.includeMMValueCalculation](https://github.com/azooKey/AzooKeyKanaKanjiConverter)
     * 相当。lcid / rcid を見て、当該エントリが形態素連接コスト（MM値）の計算対象かを返す。
     */
    fun includeMMValueCalculation(leftId: Int?, rightId: Int?): Boolean {
        val lcid = leftId ?: AzooKeyCid.PROPER_NOUN
        val rcid = rightId ?: AzooKeyCid.PROPER_NOUN
        // 非自立動詞（lcid/rcid 895〜1280）
        if (lcid in 895..1280 || rcid in 895..1280) return true
        // 非自立名詞（lcid/rcid 1297〜1305）
        if (lcid in 1297..1305 || rcid in 1297..1305) return true
        // 内容語
        val lcidType = wordTypes.getOrNull(lcid)?.toInt() ?: 2
        val rcidType = wordTypes.getOrNull(rcid)?.toInt() ?: 2
        return lcidType == 1 || rcidType == 1
    }

    /** 予測変換で終端になれない品詞ID（rcid ベース） */
    val predictionUsable: BooleanArray = BooleanArray(1320) { rcid ->
        getPredictionUsable(rcid)
    }

    private fun getPredictionUsable(rcid: Int): Boolean {
        // 連用タ接続
        if (rcid in setOf(33, 34, 50, 86, 87, 88, 103, 127, 128, 144, 397, 398, 408, 426, 427, 450, 457, 480, 687, 688, 703, 704, 727, 742, 750, 758, 766, 786, 787, 798, 810, 811, 829, 830, 831, 893, 973, 974, 975, 976, 977, 1007, 1008, 1009, 1010, 1063, 1182, 1183, 1184, 1185, 1186, 1187, 1188, 1189, 1190, 1191, 1192, 1193, 1194, 1240, 1241, 1242, 1243, 1268, 1269, 1270, 1271)) return false
        // 仮定縮約
        if (rcid in setOf(15, 16, 17, 18, 41, 42, 59, 60, 61, 62, 63, 64, 94, 95, 109, 110, 111, 112, 135, 136, 379, 380, 381, 382, 402, 412, 413, 442, 443, 471, 472, 562, 572, 582, 591, 598, 618, 627, 677, 678, 693, 694, 709, 710, 722, 730, 737, 745, 753, 761, 770, 771, 791, 869, 878, 885, 896, 906, 917, 918, 932, 948, 949, 950, 951, 952, 987, 988, 989, 990, 1017, 1018, 1033, 1034, 1035, 1036, 1058, 1078, 1079, 1080, 1081, 1082, 1083, 1084, 1085, 1086, 1087, 1088, 1089, 1090, 1212, 1213, 1214, 1215)) return false
        // 未然形
        if (rcid in setOf(372, 406, 418, 419, 431, 437, 438, 455, 462, 463, 464, 495, 496, 504, 533, 534, 540, 551, 567, 577, 587, 595, 606, 614, 622, 630, 641, 647, 653, 659, 665, 672, 683, 684, 699, 700, 715, 716, 725, 733, 740, 748, 756, 764, 780, 781, 794, 806, 807, 823, 824, 825, 837, 842, 847, 852, 859, 865, 873, 881, 890, 901, 911, 925, 935, 963, 964, 965, 966, 967, 999, 1000, 1001, 1002, 1023, 1024, 1045, 1046, 1047, 1048, 1061, 1143, 1144, 1145, 1146, 1147, 1148, 1149, 1150, 1151, 1152, 1153, 1154, 1155, 1224, 1225, 1226, 1227, 1260, 1261, 1262, 1263, 1278)) return false
        // 未然特殊
        if (rcid in setOf(420, 421, 631, 782, 783, 795, 891, 936, 1156, 1157, 1158, 1159, 1160, 1161, 1162, 1163, 1164, 1165, 1166, 1167, 1168, 1228, 1229, 1230, 1231)) return false
        // 未然ウ接続
        if (rcid in setOf(25, 26, 46, 74, 75, 76, 99, 119, 120, 140, 389, 390, 405, 416, 417, 447, 476, 493, 494, 566, 576, 585, 594, 603, 621, 629, 671, 681, 682, 697, 698, 713, 714, 724, 732, 739, 747, 755, 763, 778, 779, 793, 804, 805, 820, 821, 822, 872, 880, 889, 900, 910, 923, 924, 934, 958, 959, 960, 961, 962, 995, 996, 997, 998, 1021, 1022, 1041, 1042, 1043, 1044, 1060, 1130, 1131, 1132, 1133, 1134, 1135, 1136, 1137, 1138, 1139, 1140, 1141, 1142, 1220, 1221, 1222, 1223, 1256, 1257, 1258, 1259)) return false
        return true
    }
}

fun Candidate.getClauses(): List<ClauseDataUnit> {
    val entries = this.data
    if (entries.isEmpty()) return emptyList()

    val clauses = mutableListOf<ClauseDataUnit>()
    val firstEntry = entries[0]
    val firstMid = if (AzooKeyDicdataStoreUtils.includeMMValueCalculation(firstEntry.leftId, firstEntry.rightId)) {
        firstEntry.mid
    } else {
        AzooKeyConnectionCostStore.MID_GENERAL
    }
    var currentClause = ClauseDataUnit(
        text = firstEntry.surface,
        reading = firstEntry.reading,
        mid = firstMid,
    )

    for (i in 1 until entries.size) {
        val prev = entries[i - 1]
        val curr = entries[i]
        
        val prevRcid = prev.rightId ?: AzooKeyCid.PROPER_NOUN
        val currLcid = curr.leftId ?: AzooKeyCid.PROPER_NOUN

        if (AzooKeyDicdataStoreUtils.isClause(prevRcid, currLcid)) {
            currentClause.nextLcid = currLcid
            clauses.add(currentClause)
            val newMid = if (AzooKeyDicdataStoreUtils.includeMMValueCalculation(curr.leftId, curr.rightId)) {
                curr.mid
            } else {
                AzooKeyConnectionCostStore.MID_GENERAL
            }
            currentClause = ClauseDataUnit(
                text = curr.surface,
                reading = curr.reading,
                mid = newMid,
            )
        } else {
            currentClause.text += curr.surface
            currentClause.reading += curr.reading
            val midGeneral = AzooKeyConnectionCostStore.MID_GENERAL
            if ((currentClause.mid == midGeneral && curr.mid != midGeneral) ||
                AzooKeyDicdataStoreUtils.includeMMValueCalculation(curr.leftId, curr.rightId)
            ) {
                currentClause.mid = curr.mid
            }
        }
    }
    clauses.add(currentClause)
    return clauses
}

fun Candidate.makeFirstClauseCandidate(): Candidate {
    val entries = this.data
    if (entries.isEmpty()) return this

    val firstClauseEntries = mutableListOf<AzooKeyDictionaryEntry>()
    var text = entries[0].surface
    var reading = entries[0].reading
    var lastRcid = entries[0].rightId ?: AzooKeyCid.PROPER_NOUN
    firstClauseEntries.add(entries[0])

    for (i in 1 until entries.size) {
        val item = entries[i]
        val currLcid = item.leftId ?: AzooKeyCid.PROPER_NOUN
        if (AzooKeyDicdataStoreUtils.isClause(lastRcid, currLcid)) {
            break
        }
        text += item.surface
        reading += item.reading
        lastRcid = item.rightId ?: AzooKeyCid.PROPER_NOUN
        firstClauseEntries.add(item)
    }

    return Candidate(
        string = text,
        type = CandidateType.PART_OF_LETTERS,
        length = reading.length.toUByte(),
        score = this.score,
        value = this.value,
        yomi = reading,
        leftId = entries.first().leftId?.toShort(),
        rightId = lastRcid.toShort(),
        data = firstClauseEntries,
    )
}
