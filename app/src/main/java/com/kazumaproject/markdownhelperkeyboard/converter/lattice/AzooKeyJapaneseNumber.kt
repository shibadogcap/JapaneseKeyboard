package com.kazumaproject.markdownhelperkeyboard.converter.lattice

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyCid
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyDictionaryEntry
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyDictionarySourceKind
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyMid

private enum class JapaneseNumber(val isNumber: Boolean, val isNotNumber: Boolean, val toRoman: String, val maxDigit: Int?, val toKanji: String) {
    いち(true, false, "1", null, "一"),
    に(true, false, "2", null, "二"),
    さん(true, false, "3", null, "三"),
    よん(true, false, "4", null, "四"),
    ご(true, false, "5", null, "五"),
    ろく(true, false, "6", null, "六"),
    なな(true, false, "7", null, "七"),
    はち(true, false, "8", null, "八"),
    きゅう(true, false, "9", null, "九"),
    れい(true, false, "0", null, "〇"),
    じゅう(false, true, "", null, "十"),
    ひゃく(false, true, "", null, "百"),
    せん(false, true, "", null, "千"),
    まん(false, true, "", 2, "万"),
    おく(false, true, "", 3, "億"),
    ちょう(false, true, "", 4, "兆"),
    おわり(true, true, "", 1, ""),
    エラー(false, true, "", null, "")
}

private enum class NumberChar(val character: Char) {
    Zero('0'), One('1'), Two('2'), Three('3'), Four('4'), Five('5'), Six('6'), Seven('7'), Eight('8'), Nine('9')
}

object AzooKeyJapaneseNumber {

    private fun parseLiteral(input: String): List<JapaneseNumber> {
        val tokens = mutableListOf<JapaneseNumber>()
        var i = 0
        val len = input.length

        fun peek(offset: Int = 0): Char? {
            val idx = i + offset
            return if (idx < len) input[idx] else null
        }

        while (i < len) {
            val u0 = input[i]
            when (u0) {
                'イ' -> {
                    val u1 = peek(1)
                    if (u1 == 'チ' || u1 == 'ッ') {
                        tokens.add(JapaneseNumber.いち)
                        i += 2
                    } else {
                        tokens.add(JapaneseNumber.エラー)
                        return tokens
                    }
                }
                'オ' -> {
                    val u1 = peek(1)
                    if (u1 == 'ク') {
                        tokens.add(JapaneseNumber.おく)
                        i += 2
                    } else {
                        tokens.add(JapaneseNumber.エラー)
                        return tokens
                    }
                }
                'キ' -> {
                    val u1 = peek(1)
                    val u2 = peek(2)
                    if (u1 == 'ュ' && u2 == 'ウ') {
                        tokens.add(JapaneseNumber.きゅう)
                        i += 3
                    } else {
                        tokens.add(JapaneseNumber.エラー)
                        return tokens
                    }
                }
                'ク' -> {
                    tokens.add(JapaneseNumber.きゅう)
                    i += 1
                }
                'ゴ' -> {
                    tokens.add(JapaneseNumber.ご)
                    i += 1
                }
                'サ' -> {
                    val u1 = peek(1)
                    if (u1 == 'ン') {
                        tokens.add(JapaneseNumber.さん)
                        i += 2
                    } else {
                        tokens.add(JapaneseNumber.エラー)
                        return tokens
                    }
                }
                'シ' -> {
                    val u1 = peek(1)
                    if (u1 == 'チ') {
                        tokens.add(JapaneseNumber.なな)
                        i += 2
                    } else {
                        tokens.add(JapaneseNumber.よん)
                        i += 1
                    }
                }
                'ジ' -> {
                    val u1 = peek(1)
                    val u2 = peek(2)
                    if (u1 == 'ュ' && (u2 == 'ウ' || u2 == 'ッ')) {
                        tokens.add(JapaneseNumber.じゅう)
                        i += 3
                    } else {
                        tokens.add(JapaneseNumber.エラー)
                        return tokens
                    }
                }
                'セ' -> {
                    val u1 = peek(1)
                    if (u1 == 'ン') {
                        tokens.add(JapaneseNumber.せん)
                        i += 2
                    } else {
                        tokens.add(JapaneseNumber.エラー)
                        return tokens
                    }
                }
                'ゼ' -> {
                    val u1 = peek(1)
                    if (u1 == 'ロ') {
                        tokens.add(JapaneseNumber.れい)
                        i += 2
                    } else if (u1 == 'ン') {
                        tokens.add(JapaneseNumber.せん)
                        i += 2
                    } else {
                        tokens.add(JapaneseNumber.エラー)
                        return tokens
                    }
                }
                'チ' -> {
                    val u1 = peek(1)
                    val u2 = peek(2)
                    if (u1 == 'ョ' && u2 == 'ウ') {
                        tokens.add(JapaneseNumber.ちょう)
                        i += 3
                    } else {
                        tokens.add(JapaneseNumber.エラー)
                        return tokens
                    }
                }
                'ナ' -> {
                    val u1 = peek(1)
                    if (u1 == 'ナ') {
                        tokens.add(JapaneseNumber.なな)
                        i += 2
                    } else {
                        tokens.add(JapaneseNumber.エラー)
                        return tokens
                    }
                }
                'ニ' -> {
                    tokens.add(JapaneseNumber.に)
                    i += 1
                }
                'ハ' -> {
                    val u1 = peek(1)
                    if (u1 == 'チ' || u1 == 'ッ') {
                        tokens.add(JapaneseNumber.はち)
                        i += 2
                    } else {
                        tokens.add(JapaneseNumber.エラー)
                        return tokens
                    }
                }
                'ヒ', 'ビ', 'ピ' -> {
                    val u1 = peek(1)
                    val u2 = peek(2)
                    if (u1 == 'ャ' && u2 == 'ク') {
                        tokens.add(JapaneseNumber.ひゃく)
                        i += 3
                    } else {
                        tokens.add(JapaneseNumber.エラー)
                        return tokens
                    }
                }
                'マ' -> {
                    val u1 = peek(1)
                    if (u1 == 'ン') {
                        tokens.add(JapaneseNumber.まん)
                        i += 2
                    } else if (u1 == 'ル') {
                        tokens.add(JapaneseNumber.れい)
                        i += 2
                    } else {
                        tokens.add(JapaneseNumber.エラー)
                        return tokens
                    }
                }
                'ヨ' -> {
                    val u1 = peek(1)
                    if (u1 == 'ン') {
                        tokens.add(JapaneseNumber.よん)
                        i += 2
                    } else {
                        tokens.add(JapaneseNumber.エラー)
                        return tokens
                    }
                }
                'レ' -> {
                    val u1 = peek(1)
                    if (u1 == 'イ') {
                        tokens.add(JapaneseNumber.れい)
                        i += 2
                    } else {
                        tokens.add(JapaneseNumber.エラー)
                        return tokens
                    }
                }
                'ロ' -> {
                    val u1 = peek(1)
                    if (u1 == 'ク' || u1 == 'ッ') {
                        tokens.add(JapaneseNumber.ろく)
                        i += 2
                    } else {
                        tokens.add(JapaneseNumber.エラー)
                        return tokens
                    }
                }
                else -> {
                    tokens.add(JapaneseNumber.エラー)
                    return tokens
                }
            }
        }
        tokens.add(JapaneseNumber.おわり)
        return tokens
    }

    private fun parseTokens(tokens: List<JapaneseNumber>): List<List<NumberChar>> {
        var maxDigits: Int? = null
        var result = mutableListOf<List<NumberChar>>()
        var stack = mutableListOf(NumberChar.Zero, NumberChar.Zero, NumberChar.Zero, NumberChar.Zero)
        var curnum: NumberChar? = null

        for (token in tokens) {
            when (token) {
                JapaneseNumber.いち -> {
                    if (curnum != null) return emptyList()
                    curnum = NumberChar.One
                }
                JapaneseNumber.に -> {
                    if (curnum != null) return emptyList()
                    curnum = NumberChar.Two
                }
                JapaneseNumber.さん -> {
                    if (curnum != null) return emptyList()
                    curnum = NumberChar.Three
                }
                JapaneseNumber.よん -> {
                    if (curnum != null) return emptyList()
                    curnum = NumberChar.Four
                }
                JapaneseNumber.ご -> {
                    if (curnum != null) return emptyList()
                    curnum = NumberChar.Five
                }
                JapaneseNumber.ろく -> {
                    if (curnum != null) return emptyList()
                    curnum = NumberChar.Six
                }
                JapaneseNumber.なな -> {
                    if (curnum != null) return emptyList()
                    curnum = NumberChar.Seven
                }
                JapaneseNumber.はち -> {
                    if (curnum != null) return emptyList()
                    curnum = NumberChar.Eight
                }
                JapaneseNumber.きゅう -> {
                    if (curnum != null) return emptyList()
                    curnum = NumberChar.Nine
                }
                JapaneseNumber.れい -> {
                    if (curnum != null) return emptyList()
                    curnum = NumberChar.Zero
                }
                JapaneseNumber.じゅう -> {
                    stack[2] = curnum ?: NumberChar.One
                    curnum = null
                }
                JapaneseNumber.ひゃく -> {
                    stack[1] = curnum ?: NumberChar.One
                    curnum = null
                }
                JapaneseNumber.せん -> {
                    stack[0] = curnum ?: NumberChar.One
                    curnum = null
                }
                JapaneseNumber.おわり, JapaneseNumber.まん, JapaneseNumber.おく, JapaneseNumber.ちょう -> {
                    stack[3] = curnum ?: NumberChar.Zero
                    val targetDigit = token.maxDigit ?: 1
                    if (maxDigits != null) {
                        if (maxDigits <= targetDigit) {
                            return emptyList()
                        }
                        result[maxDigits - targetDigit] = stack.toList()
                    } else {
                        maxDigits = targetDigit
                        result = MutableList(maxDigits) { listOf(NumberChar.Zero, NumberChar.Zero, NumberChar.Zero, NumberChar.Zero) }
                        result[0] = stack.toList()
                    }
                    curnum = null
                    stack = mutableListOf(NumberChar.Zero, NumberChar.Zero, NumberChar.Zero, NumberChar.Zero)
                }
                JapaneseNumber.エラー -> {}
            }
        }
        return result
    }

    fun getJapaneseNumberDicdata(head: String): List<AzooKeyDictionaryEntry> {
        val tokens = parseLiteral(head)
        if (tokens.isEmpty() || tokens.any { it == JapaneseNumber.エラー }) {
            return emptyList()
        }

        val kanji = tokens.map { it.toKanji }.joinToString("")

        val roman: String
        if (tokens.all { it.isNumber }) {
            roman = tokens.map { it.toRoman }.joinToString("")
        } else if (tokens.all { it.isNotNumber }) {
            return emptyList()
        } else {
            val parseResult = parseTokens(tokens)
            if (parseResult.isEmpty()) {
                return emptyList()
            }
            val chars = mutableListOf<Char>()
            for (stack in parseResult) {
                if (chars.isEmpty()) {
                    if (stack[0] != NumberChar.Zero) {
                        chars.addAll(listOf(stack[0].character, stack[1].character, stack[2].character, stack[3].character))
                    } else if (stack[1] != NumberChar.Zero) {
                        chars.addAll(listOf(stack[1].character, stack[2].character, stack[3].character))
                    } else if (stack[2] != NumberChar.Zero) {
                        chars.addAll(listOf(stack[2].character, stack[3].character))
                    } else if (stack[3] != NumberChar.Zero) {
                        chars.add(stack[3].character)
                    } else {
                        return emptyList()
                    }
                } else {
                    chars.addAll(listOf(stack[0].character, stack[1].character, stack[2].character, stack[3].character))
                }
            }
            roman = String(chars.toCharArray())
        }

        val headLen = head.length.toFloat()
        val romanLen = roman.length.toFloat()

        return listOf(
            AzooKeyDictionaryEntry(
                surface = kanji,
                reading = head,
                leftId = AzooKeyCid.NUMBER,
                rightId = AzooKeyCid.NUMBER,
                mid = AzooKeyMid.NUMBER,
                wordCost = (17f - headLen / 3f).toInt(),
                value = -17f + headLen / 3f,
                sourceKind = AzooKeyDictionarySourceKind.System
            ),
            AzooKeyDictionaryEntry(
                surface = roman,
                reading = head,
                leftId = AzooKeyCid.NUMBER,
                rightId = AzooKeyCid.NUMBER,
                mid = AzooKeyMid.NUMBER,
                wordCost = (16f - 4f / romanLen).toInt(),
                value = -16f + 4f / romanLen,
                sourceKind = AzooKeyDictionarySourceKind.System
            )
        )
    }
}
