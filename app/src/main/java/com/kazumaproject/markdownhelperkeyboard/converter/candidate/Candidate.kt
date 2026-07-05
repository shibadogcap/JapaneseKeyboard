package com.kazumaproject.markdownhelperkeyboard.converter.candidate

import com.kazumaproject.core.domain.extensions.toHiragana
import com.kazumaproject.markdownhelperkeyboard.converter.api.ComposingCount
import com.kazumaproject.markdownhelperkeyboard.converter.lattice.AzooKeyConnectionCostStore
import com.kazumaproject.markdownhelperkeyboard.converter.lattice.AzooKeyDicdataStoreUtils
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.random.Random

/**
 * @see 1:NBest 2:Part of letters 3:Hirakana 4:Katakana 5:Combine part of letter 6. Single Kanji
 **/
data class Candidate(
    val string: String,
    val type: Byte,
    val length: UByte,
    val score: Int,
    val value: AzooKeyPValue = -score.toFloat(),
    val yomi: String? = null,
    val leftId: Short? = null,
    val rightId: Short? = null,
    val isLearningTarget: Boolean = true,
    val data: List<AzooKeyDictionaryEntry> = emptyList(),
    val lastMid: Int = AzooKeyConnectionCostStore.MID_GENERAL,
    val composingCount: ComposingCount = ComposingCount.InputCount(0),
    val rubyCount: Int = -1,
    val actions: List<CandidateCompleteAction> = emptyList(),
) {
    /** azooKey互換: dataの各reading長の合計 */
    val effectiveRubyCount: Int
        get() = if (rubyCount >= 0) rubyCount else data.sumOf { it.reading.length }
}

fun Candidate.adjustCandidate(): Candidate {
    val entries = this.data
    if (entries.isEmpty()) return this
    val last = entries.last()
    if (last.reading.length < 2) {
        val rubyHira = last.reading.toHiragana()
        val newElement = last.copy(
            surface = rubyHira,
        )
        val newEntries = entries.dropLast(1) + newElement
        val text = entries.dropLast(1).joinToString("") { it.surface } + rubyHira
        return this.copy(
            string = text,
            data = newEntries,
            yomi = newEntries.joinToString("") { it.reading }
        )
    }
    return this
}

fun makePrefixClauseCandidate(data: List<AzooKeyDictionaryEntry>): Candidate {
    val text = StringBuilder()
    var composingCount = 0
    var lastRcid = 0
    var lastMid = AzooKeyConnectionCostStore.MID_GENERAL
    val candidateData = mutableListOf<AzooKeyDictionaryEntry>()
    for (item in data) {
        val lcid = item.leftId ?: 0
        if (AzooKeyDicdataStoreUtils.isClause(lastRcid, lcid)) {
            break
        }
        text.append(item.surface)
        composingCount += item.reading.length
        lastRcid = item.rightId ?: 0
        if (item.mid != AzooKeyConnectionCostStore.MID_GENERAL && AzooKeyDicdataStoreUtils.includeMMValueCalculation(item.leftId, item.rightId)) {
            lastMid = item.mid
        }
        candidateData.add(item)
    }
    return Candidate(
        string = text.toString(),
        type = 1.toByte(),
        length = composingCount.toUByte(),
        score = -5,
        value = -5f,
        yomi = candidateData.joinToString("") { it.reading },
        data = candidateData,
        lastMid = lastMid
    )
}

// Template regex patterns (matches azooKey's Swift implementation)
private val DATE_EXPRESSION = Regex("""<date format="(.*?)" type="(.*?)" language="(.*?)" delta="(.*?)" deltaunit="(.*?)">""")
private val RANDOM_EXPRESSION = Regex("""<random type="(.*?)" value="(.*?)">""")

/**
 * テンプレート（<date ...>, <random ...>）を展開し、展開後は isLearningTarget = false にする
 */
fun Candidate.parseTemplate(): Candidate {
    if (!string.contains('<')) return this

    var newText = string

    // Process date templates
    var dateMatch = DATE_EXPRESSION.find(newText)
    while (dateMatch != null) {
        val template = dateMatch.value
        val format = dateMatch.groupValues[1]
        val type = dateMatch.groupValues[2]
        val language = dateMatch.groupValues[3]
        val delta = dateMatch.groupValues[4].toIntOrNull() ?: 0
        val deltaUnit = dateMatch.groupValues[5].toIntOrNull() ?: 1

        val preview = previewDateTemplate(format, type, language, delta, deltaUnit)
        newText = newText.replaceFirst(template, preview)
        dateMatch = DATE_EXPRESSION.find(newText)
    }

    // Process random templates
    var randomMatch = RANDOM_EXPRESSION.find(newText)
    while (randomMatch != null) {
        val template = randomMatch.value
        val type = randomMatch.groupValues[1]
        val value = randomMatch.groupValues[2]

        val preview = previewRandomTemplate(type, value)
        newText = newText.replaceFirst(template, preview)
        randomMatch = RANDOM_EXPRESSION.find(newText)
    }

    return if (newText != string) {
        this.copy(string = newText, isLearningTarget = false)
    } else {
        this
    }
}

private fun previewDateTemplate(format: String, type: String, language: String, delta: Int, deltaUnit: Int): String {
    val locale = when (language) {
        "ja_JP" -> Locale.JAPANESE
        "en_US" -> Locale.ENGLISH
        else -> Locale.JAPANESE
    }

    @Suppress("DEPRECATION")
    val calendar = Calendar.getInstance(locale)
    calendar.add(Calendar.SECOND, delta * deltaUnit)

    val sdf = try {
        SimpleDateFormat(format, locale)
    } catch (e: IllegalArgumentException) {
        SimpleDateFormat("yyyy/MM/dd", locale)
    }

    return try {
        sdf.format(calendar.time)
    } catch (e: Exception) {
        format // fallback to format string on error
    }
}

private fun previewRandomTemplate(type: String, value: String): String {
    return when (type) {
        "int" -> {
            val parts = value.split(",").mapNotNull { it.trim().toIntOrNull() }
            if (parts.size >= 2) {
                Random.nextInt(parts[0], parts[1] + 1).toString()
            } else {
                "0"
            }
        }
        "double" -> {
            val parts = value.split(",").mapNotNull { it.trim().toDoubleOrNull() }
            if (parts.size >= 2) {
                String.format("%.1f", Random.nextDouble(parts[0], parts[1]))
            } else {
                "0.0"
            }
        }
        "string" -> {
            val parts = value.split(",").map { it.trim() }
            parts.ifEmpty { listOf("") }.random()
        }
        else -> value
    }
}

// ── Half-width Kana / Full-width Roman variant generation (azooKey parity) ──

/** 全角カタカナ→半角カナ マッピング */
private val KATAKANA_TO_HALF_WIDTH_MAP: Map<Char, Char> = run {
    val map = mutableMapOf<Char, Char>()
    // 全角カタカナ U+30A1..U+30F6 → 半角カナ U+FF67..U+FF9D
    val fullWidthStart = '\u30A1' // ァ
    val halfWidthStart = '\uFF67'  // ｧ
    for (i in 0..86) {
        map[(fullWidthStart + i)] = (halfWidthStart + i)
    }
    // 全角「ー」(U+30FC) → 半角「ｰ」(U+FF70)
    map['\u30FC'] = '\uFF70'
    // 全角「。」(U+3002) → 半角「｡」(U+FF61)
    map['。'] = '｡'
    // 全角「、」(U+3001) → 半角「､」(U+FF64)
    map['、'] = '､'
    // 全角「・」(U+30FB) → 半角「･」(U+FF65)
    map['・'] = '･'
    // 全角「「」(U+300C) → 半角「｢」(U+FF62)
    map['「'] = '｢'
    // 全角「」」(U+300D) → 半角「｣」(U+FF63)
    map['」'] = '｣'
    // 全角「゛」(U+309B) → 半角「ﾞ」(U+FF9E)
    map['\u309B'] = '\uFF9E'
    // 全角「゜」(U+309C) → 半角「ﾟ」(U+FF9F)
    map['\u309C'] = '\uFF9F'
    map
}

/** 半角英数字→全角英数字 マッピング */
private val HALF_TO_FULL_WIDTH_ALPHANUM_MAP: Map<Char, Char> = run {
    val map = mutableMapOf<Char, Char>()
    for (c in '!'..'~') {
        map[c] = (c.code + 0xFEE0).toChar()
    }
    map[' '] = '\u3000' // 半角スペース→全角スペース
    map
}

/**
 * 文字列を半角カナに変換（azooKey `halfWidthKanaCandidate` 相当）
 */
fun String.toHalfWidthKana(): String = HalfWidthKatakanaConverter.convertFullWidthKatakana(this)

/**
 * 文字列を全角英数字に変換（azooKey `fullWidthRomanCandidate` 相当）
 */
fun String.toFullWidthRoman(): String = this.map { HALF_TO_FULL_WIDTH_ALPHANUM_MAP[it] ?: it }.joinToString("")

/**
 * 全角カタカナかどうか（半角カナ変換の対象判定）
 */
private fun String.containsFullWidthKatakana(): Boolean = this.any { it in KATAKANA_TO_HALF_WIDTH_MAP }

/**
 * 半角英数字かどうか（全角英数字変換の対象判定）
 */
private fun String.containsHalfWidthAlphanum(): Boolean = this.any { it in '!'..'~' || it == ' ' }


