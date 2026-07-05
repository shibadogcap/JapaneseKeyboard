package com.kazumaproject.markdownhelperkeyboard.converter.candidate

interface SpecialCandidateProvider {
    fun provide(request: CandidateRequest): List<Candidate>
}

object UnicodeSpecialCandidateProvider : SpecialCandidateProvider {
    override fun provide(request: CandidateRequest): List<Candidate> {
        val input = request.input.trim()
        val hex = when {
            input.startsWith("u+") || input.startsWith("U+") -> input.drop(2)
            input.startsWith("u") || input.startsWith("U") -> input.drop(1)
            else -> return emptyList()
        }
        if (hex.isEmpty() || hex.any { !it.isDigit() && it.lowercaseChar() !in 'a'..'f' }) {
            return emptyList()
        }

        val codePoint = hex.toIntOrNull(radix = 16) ?: return emptyList()
        if (!Character.isValidCodePoint(codePoint)) {
            return emptyList()
        }

        return listOf(
            Candidate(
                string = String(Character.toChars(codePoint)),
                type = CandidateType.UNICODE_SPECIAL,
                length = request.input.length.toUByte(),
                score = -10,
                yomi = request.input,
            )
        )
    }
}

object VersionSpecialCandidateProvider : SpecialCandidateProvider {
    override fun provide(request: CandidateRequest): List<Candidate> {
        val versionString = request.versionString ?: return emptyList()
        if (request.input.toKatakana() != "バージョン") {
            return emptyList()
        }
        return listOf(
            Candidate(
                string = versionString,
                type = CandidateType.VERSION_SPECIAL,
                length = request.input.length.toUByte(),
                score = -30,
                yomi = request.input,
                leftId = AzooKeyCid.PROPER_NOUN.toShort(),
                rightId = AzooKeyCid.PROPER_NOUN.toShort(),
                isLearningTarget = false,
            )
        )
    }

    private fun String.toKatakana(): String {
        return map { char ->
            if (char in 'ぁ'..'ゖ') {
                (char.code + HIRAGANA_TO_KATAKANA_OFFSET).toChar()
            } else {
                char
            }
        }.joinToString(separator = "")
    }

    private const val HIRAGANA_TO_KATAKANA_OFFSET = 0x60
}

object CommaSeparatedNumberSpecialCandidateProvider : SpecialCandidateProvider {
    override fun provide(request: CandidateRequest): List<Candidate> {
        val input = request.input
        if (input.isEmpty()) {
            return emptyList()
        }

        val negative = input.first() == '-'
        val unsignedInput = if (negative) input.drop(1) else input
        val parts = unsignedInput.split(".")
        if (parts.size > 2 || parts.any { part -> part.isEmpty() || part.any { !it.isAsciiDigit() } }) {
            return emptyList()
        }

        val integerPart = parts.first()
        if (integerPart.length <= 3) {
            return emptyList()
        }

        val groupedInteger = integerPart
            .reversed()
            .chunked(3)
            .joinToString(",")
            .reversed()
        val result = buildString {
            if (negative) append('-')
            append(groupedInteger)
            if (parts.size == 2) {
                append('.')
                append(parts[1])
            }
        }

        return listOf(
            Candidate(
                string = result,
                type = CandidateType.COMMA_SEPARATED_NUMBER_SPECIAL,
                length = request.input.length.toUByte(),
                score = -10,
                yomi = request.input,
            )
        )
    }

    private fun Char.isAsciiDigit(): Boolean = this in '0'..'9'
}

object TypographySpecialCandidateProvider : SpecialCandidateProvider {
    override fun provide(request: CandidateRequest): List<Candidate> {
        val input = request.input
        if (input.isEmpty() || input.any { !it.isAsciiLetterOrDigit() }) {
            return emptyList()
        }

        val alphabetOnly = input.all { it.isAsciiLetter() }
        val variants = buildList {
            add(input.transformTypography(upperOffset = 119743, lowerOffset = 119737, digitOffset = 120734))
            if (alphabetOnly) {
                add(
                    input.transformTypography(
                        upperOffset = 119795,
                        lowerOffset = 119789,
                        lowerSpecials = mapOf('h' to "ℎ")
                    )
                )
                add(input.transformTypography(upperOffset = 119847, lowerOffset = 119841))
                add(
                    input.transformTypography(
                        upperOffset = 119899,
                        lowerOffset = 119893,
                        upperSpecials = mapOf(
                            'B' to "ℬ",
                            'E' to "ℰ",
                            'F' to "ℱ",
                            'H' to "ℋ",
                            'I' to "ℐ",
                            'L' to "ℒ",
                            'M' to "ℳ",
                            'R' to "ℛ",
                        ),
                        lowerSpecials = mapOf(
                            'e' to "ℯ",
                            'g' to "ℊ",
                            'o' to "ℴ",
                        )
                    )
                )
                add(input.transformTypography(upperOffset = 119951, lowerOffset = 119945))
                add(
                    input.transformTypography(
                        upperOffset = 120003,
                        lowerOffset = 119997,
                        upperSpecials = mapOf(
                            'C' to "ℭ",
                            'H' to "ℌ",
                            'I' to "ℑ",
                            'R' to "ℜ",
                            'Z' to "ℨ",
                        )
                    )
                )
            }
            add(
                input.transformTypography(
                    upperOffset = 120055,
                    lowerOffset = 120049,
                    digitOffset = 120744,
                    upperSpecials = mapOf(
                        'C' to "ℂ",
                        'H' to "ℍ",
                        'N' to "ℕ",
                        'P' to "ℙ",
                        'Q' to "ℚ",
                        'R' to "ℝ",
                        'Z' to "ℤ",
                    )
                )
            )
            if (alphabetOnly) {
                add(input.transformTypography(upperOffset = 120107, lowerOffset = 120101))
            }
            add(input.transformTypography(upperOffset = 120159, lowerOffset = 120153, digitOffset = 120754))
            add(input.transformTypography(upperOffset = 120211, lowerOffset = 120205, digitOffset = 120764))
            if (alphabetOnly) {
                add(input.transformTypography(upperOffset = 120263, lowerOffset = 120257))
                add(input.transformTypography(upperOffset = 120315, lowerOffset = 120309))
            }
            add(input.transformTypography(upperOffset = 120367, lowerOffset = 120361, digitOffset = 120774))
        }.distinct()

        return variants.map { variant ->
            Candidate(
                string = variant,
                type = CandidateType.TYPOGRAPHY_SPECIAL,
                length = request.input.length.toUByte(),
                score = -15,
                yomi = request.input,
            )
        }
    }

    private fun String.transformTypography(
        upperOffset: Int,
        lowerOffset: Int,
        digitOffset: Int? = null,
        upperSpecials: Map<Char, String> = emptyMap(),
        lowerSpecials: Map<Char, String> = emptyMap(),
    ): String {
        return buildString {
            for (char in this@transformTypography) {
                append(
                    when {
                        char in upperSpecials -> upperSpecials.getValue(char)
                        char in lowerSpecials -> lowerSpecials.getValue(char)
                        char.isAsciiUppercase() -> codePointString(char.code + upperOffset)
                        char.isAsciiLowercase() -> codePointString(char.code + lowerOffset)
                        char.isAsciiDigit() && digitOffset != null -> codePointString(char.code + digitOffset)
                        else -> char.toString()
                    }
                )
            }
        }
    }

    private fun codePointString(codePoint: Int): String {
        return String(Character.toChars(codePoint))
    }

    private fun Char.isAsciiLetterOrDigit(): Boolean = isAsciiLetter() || isAsciiDigit()

    private fun Char.isAsciiLetter(): Boolean = isAsciiUppercase() || isAsciiLowercase()

    private fun Char.isAsciiUppercase(): Boolean = this in 'A'..'Z'

    private fun Char.isAsciiLowercase(): Boolean = this in 'a'..'z'

    private fun Char.isAsciiDigit(): Boolean = this in '0'..'9'
}

object TimeExpressionSpecialCandidateProvider : SpecialCandidateProvider {
    override fun provide(request: CandidateRequest): List<Candidate> {
        val input = request.input
        if (input.length !in 3..4 || input.any { !it.isAsciiDigit() }) {
            return emptyList()
        }

        val hour = if (input.length == 3) {
            input.take(1).toIntOrNull() ?: return emptyList()
        } else {
            input.take(2).toIntOrNull() ?: return emptyList()
        }
        val minute = input.takeLast(2).toIntOrNull() ?: return emptyList()
        val hourRange = if (input.length == 3) 0..9 else 0..24
        if (hour !in hourRange || minute !in 0..59) {
            return emptyList()
        }

        val result = if (input.length == 3) {
            "$hour:${minute.toString().padStart(2, '0')}"
        } else {
            "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"
        }

        return listOf(
            Candidate(
                string = result,
                type = CandidateType.TIME_EXPRESSION_SPECIAL,
                length = request.input.length.toUByte(),
                score = -10,
                yomi = request.input,
            )
        )
    }

    private fun Char.isAsciiDigit(): Boolean = this in '0'..'9'
}

object CalendarSpecialCandidateProvider : SpecialCandidateProvider {
    override fun provide(request: CandidateRequest): List<Candidate> {
        val input = request.input.toKatakana()
        return (toWareki(input) + toSeireki(input)).mapIndexed { index, result ->
            Candidate(
                string = result,
                type = CandidateType.CALENDAR_SPECIAL,
                length = request.input.length.toUByte(),
                score = -18 - index,
                yomi = request.input,
            )
        }
    }

    private fun toWareki(input: String): List<String> {
        val seireki = input.take(4).toIntOrNull() ?: return emptyList()
        if (!input.endsWith("ネン")) {
            return emptyList()
        }

        return when {
            seireki == 1989 -> listOf("平成元年", "昭和64年")
            seireki == 2019 -> listOf("令和元年", "平成31年")
            seireki == 1926 -> listOf("昭和元年", "大正15年")
            seireki == 1912 -> listOf("大正元年", "明治45年")
            seireki == 1868 -> listOf("明治元年", "慶應4年")
            seireki in 1990..2018 -> listOf("平成${seireki - 1988}年")
            seireki in 1927..1988 -> listOf("昭和${seireki - 1925}年")
            seireki in 1869..1911 -> listOf("明治${seireki - 1867}年")
            seireki in 1913..1925 -> listOf("大正${seireki - 1911}年")
            seireki >= 2020 -> listOf("令和${seireki - 2018}年")
            else -> emptyList()
        }
    }

    private fun toSeireki(input: String): List<String> {
        val ganNen = when (input) {
            "メイジガンネン" -> "1868年"
            "タイショウガンネン" -> "1912年"
            "ショウワガンネン" -> "1926年"
            "ヘイセイガンネン" -> "1989年"
            "レイワガンネン" -> "2019年"
            else -> null
        }
        if (ganNen != null) {
            return listOf(ganNen)
        }

        if (!input.endsWith("ネン")) {
            return emptyList()
        }
        val eraAndYear = input.dropLast(2)
        val result = when {
            eraAndYear.startsWith("ショウワ") ->
                eraAndYear.drop(4).toIntOrNull()?.let { it + 1925 }
            eraAndYear.startsWith("ヘイセイ") ->
                eraAndYear.drop(4).toIntOrNull()?.let { it + 1988 }
            eraAndYear.startsWith("レイワ") ->
                eraAndYear.drop(3).toIntOrNull()?.let { it + 2018 }
            eraAndYear.startsWith("メイジ") ->
                eraAndYear.drop(3).toIntOrNull()?.let { it + 1867 }
            eraAndYear.startsWith("タイショウ") ->
                eraAndYear.drop(5).toIntOrNull()?.let { it + 1911 }
            else -> null
        }

        return result?.let { listOf("${it}年") }.orEmpty()
    }

    private fun String.toKatakana(): String {
        return map { char ->
            if (char in 'ぁ'..'ゖ') {
                (char.code + HIRAGANA_TO_KATAKANA_OFFSET).toChar()
            } else {
                char
            }
        }.joinToString(separator = "")
    }

    private const val HIRAGANA_TO_KATAKANA_OFFSET = 0x60
}

object EmailAddressSpecialCandidateProvider : SpecialCandidateProvider {
    private val domains = listOf(
        "@gmail.com",
        "@icloud.com",
        "@yahoo.co.jp",
        "@au.com",
        "@docomo.ne.jp",
        "@excite.co.jp",
        "@ezweb.ne.jp",
        "@googlemail.com",
        "@hotmail.co.jp",
        "@hotmail.com",
        "@i.softbank.jp",
        "@live.jp",
        "@me.com",
        "@mineo.jp",
        "@nifty.com",
        "@outlook.com",
        "@outlook.jp",
        "@softbank.ne.jp",
        "@yahoo.ne.jp",
        "@ybb.ne.jp",
        "@ymobile.ne.jp"
    )

    override fun provide(request: CandidateRequest): List<Candidate> {
        val input = request.input
        val atIndex = input.lastIndexOf('@')
        if (atIndex < 0) {
            return emptyList()
        }

        val id = input.take(atIndex)
        val domainPrefix = input.drop(atIndex + 1)
        if (id.isNotEmpty() && !id.isEnglishSentence()) {
            return emptyList()
        }

        val baseScore = if (id.isEmpty()) -20 else -13
        return domains.mapIndexedNotNull { index, domain ->
            if (!domain.startsWith("@$domainPrefix")) {
                null
            } else {
                Candidate(
                    string = id + domain,
                    type = CandidateType.EMAIL_ADDRESS_SPECIAL,
                    length = request.input.length.toUByte(),
                    score = baseScore - index,
                    yomi = request.input,
                )
            }
        }
    }

    private fun String.isEnglishSentence(): Boolean {
        return all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it in EMAIL_ID_SYMBOLS }
    }

    private val EMAIL_ID_SYMBOLS = setOf('.', '_', '+', '-')
}

object SymbolSpecialCandidateProvider : SpecialCandidateProvider {
    private val symbolGroups: Map<String, List<String>> = mapOf(
        "きごう" to listOf("♪", "☆", "★", "○", "●", "◎", "◇", "◆", "□", "■", "△", "▲", "▽", "▼"),
        "やじるし" to listOf("→", "←", "↑", "↓", "↔", "↕", "⇒", "⇐", "⇔", "↗", "↘", "↙", "↖"),
        "かっこ" to listOf("「」", "『』", "（）", "()", "[]", "【】", "《》", "〈〉", "［］"),
        "まる" to listOf("○", "●", "◎", "◯", "〇", "◉", "◌"),
        "ほし" to listOf("☆", "★", "✩", "✭", "✮", "✯", "✰"),
    )

    override fun provide(request: CandidateRequest): List<Candidate> {
        val symbols = symbolGroups[request.input] ?: return emptyList()
        return symbols.mapIndexed { index, symbol ->
            Candidate(
                string = symbol,
                type = CandidateType.SYMBOL_SPECIAL,
                length = request.input.length.toUByte(),
                score = -30 - index,
                yomi = request.input,
            )
        }
    }
}

object HalfWidthKatakanaSpecialCandidateProvider : SpecialCandidateProvider {
    private val FULL_TO_HALF_KATAKANA = mapOf<Char, String>(
        'ア' to "ｱ", 'イ' to "ｲ", 'ウ' to "ｳ", 'エ' to "ｴ", 'オ' to "ｵ",
        'カ' to "ｶ", 'キ' to "ｷ", 'ク' to "ｸ", 'ケ' to "ｹ", 'コ' to "ｺ",
        'サ' to "ｻ", 'シ' to "ｼ", 'ス' to "ｽ", 'セ' to "ｾ", 'ソ' to "ｿ",
        'タ' to "ﾀ", 'チ' to "ﾁ", 'ツ' to "ﾂ", 'テ' to "ﾃ", 'ト' to "ﾄ",
        'ナ' to "ﾅ", 'ニ' to "ﾆ", 'ヌ' to "ﾇ", 'ネ' to "ﾈ", 'ノ' to "ﾉ",
        'ハ' to "ﾊ", 'ヒ' to "ﾋ", 'フ' to "ﾌ", 'ヘ' to "ﾍ", 'ホ' to "ﾎ",
        'マ' to "ﾏ", 'ミ' to "ﾐ", 'ム' to "ﾑ", 'メ' to "ﾒ", 'モ' to "ﾓ",
        'ヤ' to "ﾔ", 'ユ' to "ﾕ", 'ヨ' to "ﾖ",
        'ラ' to "ﾗ", 'リ' to "ﾘ", 'ル' to "ﾙ", 'レ' to "ﾚ", 'ロ' to "ﾛ",
        'ワ' to "ﾜ", 'ヲ' to "ｦ", 'ン' to "ﾝ",
        'ガ' to "ｶﾞ", 'ギ' to "ｷﾞ", 'グ' to "ｸﾞ", 'ゲ' to "ｹﾞ", 'ゴ' to "ｺﾞ",
        'ザ' to "ｻﾞ", 'ジ' to "ｼﾞ", 'ズ' to "ｽﾞ", 'ゼ' to "ｾﾞ", 'ゾ' to "ｿﾞ",
        'ダ' to "ﾀﾞ", 'ヂ' to "ﾁﾞ", 'ヅ' to "ﾂﾞ", 'デ' to "ﾃﾞ", 'ド' to "ﾄﾞ",
        'バ' to "ﾊﾞ", 'ビ' to "ﾋﾞ", 'ブ' to "ﾌﾞ", 'ベ' to "ﾍﾞ", 'ボ' to "ﾎﾞ",
        'パ' to "ﾊﾟ", 'ピ' to "ﾋﾟ", 'プ' to "ﾌﾟ", 'ペ' to "ﾍﾟ", 'ポ' to "ﾎﾟ",
        'ッ' to "ｯ", 'ャ' to "ｬ", 'ュ' to "ｭ", 'ョ' to "ｮ",
        'ァ' to "ｧ", 'ィ' to "ｨ", 'ゥ' to "ｩ", 'ェ' to "ｪ", 'ォ' to "ｫ",
        'ー' to "ｰ",
    )

    override fun provide(request: CandidateRequest): List<Candidate> {
        val input = request.input
        if (input.isEmpty() || !input.all { it in FULL_TO_HALF_KATAKANA.keys }) {
            return emptyList()
        }
        val halfWidth = input.map { FULL_TO_HALF_KATAKANA[it] ?: it.toString() }.joinToString("")
        if (halfWidth == input) return emptyList()
        return listOf(
            Candidate(
                string = halfWidth,
                type = CandidateType.HALF_WIDTH_KATAKANA_SPECIAL,
                length = request.input.length.toUByte(),
                score = -12,
                yomi = request.input,
            )
        )
    }
}

object DefaultSpecialCandidateProviders {
    val providers: List<SpecialCandidateProvider> = listOf(
        CalendarSpecialCandidateProvider,
        EmailAddressSpecialCandidateProvider,
        SymbolSpecialCandidateProvider,
        UnicodeSpecialCandidateProvider,
        VersionSpecialCandidateProvider,
        TimeExpressionSpecialCandidateProvider,
        CommaSeparatedNumberSpecialCandidateProvider,
        TypographySpecialCandidateProvider,
        HalfWidthKatakanaSpecialCandidateProvider
    )

    fun provide(request: CandidateRequest): List<Candidate> {
        return request.specialCandidateProviders.flatMap { it.provide(request) }
    }
}
