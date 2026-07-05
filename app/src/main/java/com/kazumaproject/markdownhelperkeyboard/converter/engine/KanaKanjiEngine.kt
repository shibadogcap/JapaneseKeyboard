package com.kazumaproject.markdownhelperkeyboard.converter.engine

import android.content.Context
import android.os.Build
import androidx.core.text.isDigitsOnly
import com.kazumaproject.Louds.LOUDS
import com.kazumaproject.Louds.with_term_id.LOUDSWithTermId
import com.kazumaproject.convertFullWidthToHalfWidth
import com.kazumaproject.core.domain.extensions.toHankakuAlphabet
import com.kazumaproject.core.domain.extensions.toHankakuKatakana
import com.kazumaproject.data.emoji.Emoji
import com.kazumaproject.data.emoticon.Emoticon
import com.kazumaproject.data.symbol.Symbol
import com.kazumaproject.dictionary.TokenArray
import com.kazumaproject.domain.categorizeEmoji
import com.kazumaproject.domain.sortByEmojiCategory
import com.kazumaproject.domain.toEmoticonCategory
import com.kazumaproject.domain.toSymbolCategory
import com.kazumaproject.hiraToKata
import com.kazumaproject.markdownhelperkeyboard.converter.bitset.SuccinctBitVector
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyDictionaryAssetProvider
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyDictionaryConnectionIdResolver
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyDictionaryEntry
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyDictionarySourceKind
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyMid
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyLoudsDictionarySearcher
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.BunsetsuCandidateResult
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CandidateType
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.DictionaryBinaryReader
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.DictionaryCategory
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.DictionaryCategoryLoadState
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.DictionaryCompatibilityValidator
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.DictionaryFileKey
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.DictionaryFileRole
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.DictionaryFileSpecs
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.DictionaryOverrideStore
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.DictionaryOverrideValidator
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.DictionarySourceResolver
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.addCommasToNumber
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.containsDigit
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.containsFullWidthNumber
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.convertFullWidthAlnumToHalfWidth
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.convertFullWidthNumbersToHalfWidth
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.convertToKanjiNotation
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.createValueBasedSymbolCandidates
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.groupAndReplaceJapaneseForNumber
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.isAllEnglishLetters
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.isAllFullWidthAscii
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.isAllHalfWidthAscii
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.replaceJapaneseCharactersForEnglish
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.toFullWidth
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.toKanji
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.toNumber
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.toNumberExponent
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.toSubscriptDigits
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.toSuperscriptDigits
import com.kazumaproject.markdownhelperkeyboard.repository.LearnRepository
import com.kazumaproject.markdownhelperkeyboard.repository.UserDictionaryRepository
import com.kazumaproject.markdownhelperkeyboard.user_dictionary.database.UserWord
import com.kazumaproject.markdownhelperkeyboard.learning.model.LearnResult
import com.kazumaproject.toFullWidthDigitsEfficient
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import timber.log.Timber
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.ObjectInputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class KanaKanjiEngine {

    // ── emoji / emoticon / symbol（記号キーボード用に維持） ────────────────

    private lateinit var emojiYomiTrie: LOUDSWithTermId
    private lateinit var emojiTangoTrie: LOUDS
    private lateinit var emojiTokenArray: TokenArray

    private lateinit var emojiSuccinctBitVectorLBSYomi: SuccinctBitVector
    private lateinit var emojiSuccinctBitVectorIsLeafYomi: SuccinctBitVector
    private lateinit var emojiSuccinctBitVectorTokenArray: SuccinctBitVector
    private lateinit var emojiSuccinctBitVectorTangoLBS: SuccinctBitVector

    private lateinit var emoticonYomiTrie: LOUDSWithTermId
    private lateinit var emoticonTangoTrie: LOUDS
    private lateinit var emoticonTokenArray: TokenArray

    private lateinit var emoticonSuccinctBitVectorLBSYomi: SuccinctBitVector
    private lateinit var emoticonSuccinctBitVectorIsLeafYomi: SuccinctBitVector
    private lateinit var emoticonSuccinctBitVectorTokenArray: SuccinctBitVector
    private lateinit var emoticonSuccinctBitVectorTangoLBS: SuccinctBitVector

    private lateinit var symbolYomiTrie: LOUDSWithTermId
    private lateinit var symbolTangoTrie: LOUDS
    private lateinit var symbolTokenArray: TokenArray

    private lateinit var symbolSuccinctBitVectorLBSYomi: SuccinctBitVector
    private lateinit var symbolSuccinctBitVectorIsLeafYomi: SuccinctBitVector
    private lateinit var symbolSuccinctBitVectorTokenArray: SuccinctBitVector
    private lateinit var symbolSuccinctBitVectorTangoLBS: SuccinctBitVector

    // ── system user dictionary（ユーザー辞書、mozc非依存） ──────────────────

    private var systemUserYomiTrie: LOUDSWithTermId? = null
    private var systemUserTangoTrie: LOUDS? = null
    private var systemUserTokenArray: TokenArray? = null
    private var systemUserSuccinctBitVectorLBSYomi: SuccinctBitVector? = null
    private var systemUserSuccinctBitVectorIsLeaf: SuccinctBitVector? = null
    private var systemUserSuccinctBitVectorTokenArray: SuccinctBitVector? = null
    private var systemUserSuccinctBitVectorLBSTango: SuccinctBitVector? = null

    // ── English engine ──────────────────────────────────────────────────────

    private lateinit var englishEngine: EnglishEngine

    private var dictionaryBinaryReader: DictionaryBinaryReader? = null

    // ── LOUDS dictionary asset provider ─────────────────────────────────────

    private var dictionaryAssetProvider: AzooKeyDictionaryAssetProvider? = null

    private val loudsSearcher: AzooKeyLoudsDictionarySearcher?
        get() = dictionaryAssetProvider?.loudsDictionaryRegistry

    private val connectionIdResolver: AzooKeyDictionaryConnectionIdResolver
        get() = AzooKeyDictionaryConnectionIdResolver()

    // ── Dictionary source resolver (override state) ─────────────────────────

    private var dictionarySourceResolver: DictionarySourceResolver? = null

    // ── setter methods (used by DI) ───────────────────────────────────────

    fun assignEnglishEngine(engine: EnglishEngine) {
        this.englishEngine = engine
    }

    fun assignDictionaryAssetProvider(provider: AzooKeyDictionaryAssetProvider) {
        this.dictionaryAssetProvider = provider
    }

    fun assignDictionarySourceResolver(resolver: DictionarySourceResolver) {
        this.dictionarySourceResolver = resolver
    }

    // ── assign methods (called by DI) ───────────────────────────────────

    fun assignEmojiDictionary(
        tangoTrie: LOUDS, yomiTrie: LOUDSWithTermId, tokenArray: TokenArray,
        succinctBitVectorLBSYomi: SuccinctBitVector, succinctBitVectorIsLeafYomi: SuccinctBitVector,
        succinctBitVectorTokenArray: SuccinctBitVector, succinctBitVectorTangoLBS: SuccinctBitVector,
    ) {
        this.emojiTangoTrie = tangoTrie
        this.emojiYomiTrie = yomiTrie
        this.emojiTokenArray = tokenArray
        this.emojiSuccinctBitVectorLBSYomi = succinctBitVectorLBSYomi
        this.emojiSuccinctBitVectorIsLeafYomi = succinctBitVectorIsLeafYomi
        this.emojiSuccinctBitVectorTokenArray = succinctBitVectorTokenArray
        this.emojiSuccinctBitVectorTangoLBS = succinctBitVectorTangoLBS
    }

    fun assignEmoticonDictionary(
        tangoTrie: LOUDS, yomiTrie: LOUDSWithTermId, tokenArray: TokenArray,
        succinctBitVectorLBSYomi: SuccinctBitVector, succinctBitVectorIsLeafYomi: SuccinctBitVector,
        succinctBitVectorTokenArray: SuccinctBitVector, succinctBitVectorTangoLBS: SuccinctBitVector,
    ) {
        this.emoticonTangoTrie = tangoTrie
        this.emoticonYomiTrie = yomiTrie
        this.emoticonTokenArray = tokenArray
        this.emoticonSuccinctBitVectorLBSYomi = succinctBitVectorLBSYomi
        this.emoticonSuccinctBitVectorIsLeafYomi = succinctBitVectorIsLeafYomi
        this.emoticonSuccinctBitVectorTokenArray = succinctBitVectorTokenArray
        this.emoticonSuccinctBitVectorTangoLBS = succinctBitVectorTangoLBS
    }

    fun assignSymbolDictionary(
        tangoTrie: LOUDS, yomiTrie: LOUDSWithTermId, tokenArray: TokenArray,
        succinctBitVectorLBSYomi: SuccinctBitVector, succinctBitVectorIsLeafYomi: SuccinctBitVector,
        succinctBitVectorTokenArray: SuccinctBitVector, succinctBitVectorTangoLBS: SuccinctBitVector,
    ) {
        this.symbolTangoTrie = tangoTrie
        this.symbolYomiTrie = yomiTrie
        this.symbolTokenArray = tokenArray
        this.symbolSuccinctBitVectorLBSYomi = succinctBitVectorLBSYomi
        this.symbolSuccinctBitVectorIsLeafYomi = succinctBitVectorIsLeafYomi
        this.symbolSuccinctBitVectorTokenArray = succinctBitVectorTokenArray
        this.symbolSuccinctBitVectorTangoLBS = succinctBitVectorTangoLBS
    }

    fun loadSystemUserDictionaryFromFiles(context: Context) {
        if (isSystemUserDictionaryInitialized()) return
        try {
            val dir = context.filesDir
            val yomiFile = File(dir, "system_user_dictionary/yomi.dat")
            val tangoFile = File(dir, "system_user_dictionary/tango.dat")
            val tokenFile = File(dir, "system_user_dictionary/token.dat")
            if (!yomiFile.exists() || !tangoFile.exists() || !tokenFile.exists()) return

            ObjectInputStream(FileInputStream(yomiFile)).use { ois ->
                this.systemUserYomiTrie = ois.readObject() as? LOUDSWithTermId
            }
            ObjectInputStream(FileInputStream(tangoFile)).use { ois ->
                this.systemUserTangoTrie = ois.readObject() as? LOUDS
            }
            ObjectInputStream(FileInputStream(tokenFile)).use { ois ->
                this.systemUserTokenArray = ois.readObject() as? TokenArray
            }

            if (systemUserYomiTrie != null && systemUserTokenArray != null) {
                this.systemUserSuccinctBitVectorLBSYomi = SuccinctBitVector(systemUserYomiTrie!!.LBS)
                this.systemUserSuccinctBitVectorIsLeaf = SuccinctBitVector(systemUserYomiTrie!!.isLeaf)
                this.systemUserSuccinctBitVectorTokenArray = SuccinctBitVector(systemUserTokenArray!!.bitvector)
                this.systemUserSuccinctBitVectorLBSTango = SuccinctBitVector(systemUserTangoTrie?.LBS ?: java.util.BitSet())
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed to load system user dictionary from files")
            releaseSystemUserDictionary()
        }
    }

    fun releaseSystemUserDictionary() {
        systemUserYomiTrie = null
        systemUserTangoTrie = null
        systemUserTokenArray = null
        systemUserSuccinctBitVectorLBSYomi = null
        systemUserSuccinctBitVectorIsLeaf = null
        systemUserSuccinctBitVectorTokenArray = null
        systemUserSuccinctBitVectorLBSTango = null
    }

    fun isSystemUserDictionaryInitialized(): Boolean = systemUserYomiTrie != null

    fun setDictionaryBinaryReader(reader: DictionaryBinaryReader) {
        this.dictionaryBinaryReader = reader
    }

    fun applyDictionaryOverrideState(context: Context) {
        val resolver = dictionarySourceResolver ?: return
        val states = DictionaryCategory.entries.associateWith { category ->
            resolver.resolveCategoryLoadState(category)
        }

        // Release mozc-specific dictionaries when their category has User overrides
        // Note: LOUDS-based dictionaries (SYSTEM, SINGLE_KANJI, READING_CORRECTION, etc.)
        // are always loaded from APK assets via AzooKeyDictionaryAssetProvider.
        val mozcCategories = mapOf(
            DictionaryCategory.PERSON_NAME to { releasePersonNamesDictionary() },
            DictionaryCategory.PLACES to { releasePlacesDictionary() },
            DictionaryCategory.WIKI to { releaseWikiDictionary() },
            DictionaryCategory.NEOLOGD to { releaseNeologdDictionary() },
            DictionaryCategory.WEB to { releaseWebDictionary() },
        )
        mozcCategories.forEach { (category, releaseFn) ->
            if (states[category] == DictionaryCategoryLoadState.User) {
                releaseFn()
            }
        }
    }

    fun releasePersonNamesDictionary() {}
    fun releasePlacesDictionary() {}
    fun releaseWikiDictionary() {}
    fun releaseNeologdDictionary() {}
    fun releaseWebDictionary() {}


    fun getCandidatesEnglishKana(input: String): List<Candidate> {
        val inputToEnglish = input.replaceJapaneseCharactersForEnglish()
        val inputToNumbers = input.groupAndReplaceJapaneseForNumber()
        val directJapaneseNumber = input.toNumber()
        val numberUnitCandidates = createCandidatesForJapaneseNumberWithUnit(input)
        val preferredNumberCandidate = when {
            numberUnitCandidates.isNotEmpty() -> numberUnitCandidates.first().string
            directJapaneseNumber != null -> directJapaneseNumber.second
            else -> inputToNumbers
        }
        val listJapaneseCandidates = listOf(
            Candidate(
                string = input, type = (1).toByte(), length = input.length.toUByte(), score = 3000
            ), Candidate(
                string = input.hiraToKata(),
                type = (1).toByte(),
                length = input.length.toUByte(),
                score = 3000
            ), Candidate(
                string = input.toHankakuKatakana(),
                type = (31).toByte(),
                length = input.length.toUByte(),
                score = 3000
            ), Candidate(
                string = inputToEnglish,
                type = (1).toByte(),
                length = input.length.toUByte(),
                score = 3000
            ), Candidate(
                string = inputToEnglish.replaceFirstChar { it.uppercaseChar() },
                type = (1).toByte(),
                length = input.length.toUByte(),
                score = 3000
            ), Candidate(
                string = inputToEnglish.uppercase(),
                type = (1).toByte(),
                length = input.length.toUByte(),
                score = 3000
            ), Candidate(
                string = preferredNumberCandidate,
                type = (1).toByte(),
                length = input.length.toUByte(),
                score = 3000
            )
        )

        val digitCandidates = when {
            directJapaneseNumber != null -> createDigitCandidates(
                directJapaneseNumber.second,
                input.length.toUByte()
            )

            numberUnitCandidates.isNotEmpty() -> emptyList()
            else -> createDigitCandidates(inputToNumbers, input.length.toUByte())
        }

        val englishDeferred = if (input.isAllEnglishLetters()) {
            if (::englishEngine.isInitialized) {
                englishEngine.getCandidates(input)
            } else {
                emptyList()
            }
        } else if (input.isAllFullWidthAscii()) {
            if (::englishEngine.isInitialized) {
                englishEngine.getCandidates(input.toHankakuAlphabet())
            } else {
                emptyList()
            }
        } else {
            emptyList()
        }

        val englishZenkaku = if (input.isAllHalfWidthAscii()) {
            val fullWidthInput = input.toFullWidth()
            listOf(
                Candidate(
                    string = fullWidthInput.lowercase(),
                    type = (30).toByte(),
                    length = input.length.toUByte(),
                    score = 30000
                ), Candidate(
                    string = fullWidthInput.uppercase(),
                    type = (30).toByte(),
                    length = input.length.toUByte(),
                    score = 30000
                )
            )
        } else {
            emptyList()
        }

        val numbersConverted =
            digitCandidates + numberUnitCandidates + (englishDeferred + englishZenkaku).sortedBy { it.score }
        val temporalCandidates = createTemporalDictionaryCandidates(input)

        return listJapaneseCandidates + numbersConverted + temporalCandidates
    }

    private fun createTemporalDictionaryCandidates(input: String): List<Candidate> = when (input) {
        "きょう" -> {
            val today = Calendar.getInstance()
            createCandidatesForDate(today, input)
        }

        "きのう" -> {
            val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
            createCandidatesForDate(yesterday, input)
        }

        "あした" -> {
            val tomorrow = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }
            createCandidatesForDate(tomorrow, input)
        }

        "いま" -> {
            val now = Calendar.getInstance()
            createCandidatesForTime(now, input)
        }

        "ことし" -> createCandidatesForRelativeYear(input, yearOffset = 0)
        "きょねん" -> createCandidatesForRelativeYear(input, yearOffset = -1)
        "らいねん" -> createCandidatesForRelativeYear(input, yearOffset = 1)
        else -> emptyList()
    }

    private fun createCandidatesForRelativeYear(input: String, yearOffset: Int): List<Candidate> {
        val calendar = Calendar.getInstance().apply { add(Calendar.YEAR, yearOffset) }
        val year = calendar.get(Calendar.YEAR)
        val reiwaYear = year - 2018
        val zodiac = listOf(
            "子",
            "丑",
            "寅",
            "卯",
            "辰",
            "巳",
            "午",
            "未",
            "申",
            "酉",
            "戌",
            "亥"
        )[Math.floorMod(year - 4, 12)]
        val length = input.length.toUByte()

        val baseCandidates = mutableListOf(
            Candidate(
                string = "${year}年",
                type = 14,
                length = length,
                score = 7000,
                leftId = 1851,
                rightId = 1851
            ),
            Candidate(
                string = "${zodiac}年",
                type = 14,
                length = length,
                score = 7002,
                leftId = 1851,
                rightId = 1851
            )
        )

        if (reiwaYear > 0) {
            baseCandidates += Candidate(
                string = "令和${reiwaYear}年",
                type = 14,
                length = length,
                score = 7001,
                leftId = 1851,
                rightId = 1851
            )
            baseCandidates += Candidate(
                string = "R${reiwaYear}",
                type = 14,
                length = length,
                score = 7003,
                leftId = 1851,
                rightId = 1851
            )
        }

        return baseCandidates
    }

    private fun createDigitCandidates(inputDigits: String, inputLength: UByte): List<Candidate> {
        val halfWidthDigits = inputDigits.convertFullWidthNumbersToHalfWidth()
        val fullWidthDigits = halfWidthDigits.toFullWidthDigitsEfficient()

        val fullWidth = Candidate(
            string = fullWidthDigits,
            type = 22,
            length = inputLength,
            score = 8000,
            leftId = 2040,
            rightId = 2040
        )
        val halfWidth = Candidate(
            string = halfWidthDigits.convertFullWidthToHalfWidth(),
            type = 31,
            length = inputLength,
            score = 8000,
            leftId = 2040,
            rightId = 2040
        )
        val timeConversion = createCandidatesForTime(halfWidthDigits)
        val dateConversion = createCandidatesForDateInDigit(halfWidthDigits)

        val numberValue = halfWidthDigits.toLongOrNull()
        val numberCandidates = if (numberValue != null) {
            buildList {
                add(
                    Candidate(
                        string = numberValue.toKanji(),
                        type = 17,
                        score = 2000,
                        length = inputLength,
                        leftId = 2040,
                        rightId = 2040
                    )
                )
                add(
                    Candidate(
                        string = halfWidthDigits.addCommasToNumber(),
                        type = 19,
                        score = 8001,
                        length = inputLength,
                        leftId = 2040,
                        rightId = 2040
                    )
                )
                add(
                    Candidate(
                        string = halfWidthDigits,
                        type = 18,
                        score = 8002,
                        length = inputLength,
                        leftId = 2040,
                        rightId = 2040
                    )
                )
                add(
                    Candidate(
                        string = numberValue.convertToKanjiNotation(),
                        type = 23,
                        score = 7900,
                        length = inputLength,
                        leftId = 2040,
                        rightId = 2040
                    )
                )
            }
        } else {
            emptyList()
        }

        return listOf(fullWidth, halfWidth) + timeConversion + dateConversion + numberCandidates
    }

    private fun createCandidatesForJapaneseNumberWithUnit(input: String): List<Candidate> {
        val unitMappings = listOf(
            "にん" to "人",
            "えん" to "円",
            "ぷん" to "分",
            "ふん" to "分",
            "じ" to "時"
        )

        for ((readingSuffix, unit) in unitMappings) {
            if (!input.endsWith(readingSuffix) || input.length <= readingSuffix.length) continue

            val number = input.removeSuffix(readingSuffix).toNumber() ?: continue
            val isTimeLike = unit == "時" || unit == "分"
            val connectionId = if (isTimeLike) 1851.toShort() else 2040.toShort()

            return listOf(
                Candidate(
                    string = "${number.second}$unit",
                    type = if (isTimeLike) 30 else 18,
                    length = input.length.toUByte(),
                    score = 8000,
                    leftId = connectionId,
                    rightId = connectionId
                ),
                Candidate(
                    string = "${number.first}$unit",
                    type = if (isTimeLike) 30 else 22,
                    length = input.length.toUByte(),
                    score = 8001,
                    leftId = connectionId,
                    rightId = connectionId
                )
            )
        }

        return emptyList()
    }


    fun getSymbolEmojiCandidates(): List<Emoji> = emojiTokenArray.getNodeIds().map { nodeId ->
        emojiTangoTrie.getLetterShortArray(nodeId, emojiSuccinctBitVectorTangoLBS)
    }.distinct().map { symbol ->
        Emoji(
            symbol = symbol, category = categorizeEmoji(symbol)
        )
    }.sortByEmojiCategory()

    private fun deferredPredictionEmojiSymbols(
        input: String,
        yomiTrie: LOUDSWithTermId,
        succinctBitVector: SuccinctBitVector,
    ): List<String> {
        val results = mutableListOf<String>()
        for (i in 1..input.length) {
            val prefix = input.substring(0, i)
            if (yomiTrie.getNodeIndex(prefix, succinctBitVector) >= 0) {
                results.add(prefix)
            }
        }
        return results
    }

    fun searchEmojiDictionaryEntries(input: String, limit: Int): List<AzooKeyDictionaryEntry> {
        if (input.isBlank() || limit <= 0) {
            return emptyList()
        }
        val readings = deferredPredictionEmojiSymbols(
            input = input,
            yomiTrie = emojiYomiTrie,
            succinctBitVector = emojiSuccinctBitVectorLBSYomi,
        )
        return deferredFromDictionarySymbols(
            input = input,
            commonPrefixListString = readings,
            yomiTrie = emojiYomiTrie,
            tokenArray = emojiTokenArray,
            tangoTrie = emojiTangoTrie,
            succinctBitVectorLBSYomi = emojiSuccinctBitVectorLBSYomi,
            succinctBitVectorIsLeafYomi = emojiSuccinctBitVectorIsLeafYomi,
            succinctBitVectorTokenArray = emojiSuccinctBitVectorTokenArray,
            succinctBitVectorTangoLBS = emojiSuccinctBitVectorTangoLBS,
            type = 11,
        ).take(limit).map { candidate ->
            AzooKeyDictionaryEntry(
                surface = candidate.string,
                reading = candidate.yomi ?: input,
                leftId = candidate.leftId?.toInt(),
                rightId = candidate.rightId?.toInt(),
                mid = AzooKeyMid.EMOJI,
                wordCost = candidate.score,
                sourceKind = AzooKeyDictionarySourceKind.Emoji,
            )
        }
    }

    fun searchSymbolDictionaryEntries(input: String, limit: Int): List<AzooKeyDictionaryEntry> {
        if (input.isBlank() || limit <= 0) {
            return emptyList()
        }
        val readings = deferredPredictionEmojiSymbols(
            input = input,
            yomiTrie = symbolYomiTrie,
            succinctBitVector = symbolSuccinctBitVectorLBSYomi,
        )
        val candidates = deferredFromDictionarySymbols(
            input = input,
            commonPrefixListString = readings,
            yomiTrie = symbolYomiTrie,
            tokenArray = symbolTokenArray,
            tangoTrie = symbolTangoTrie,
            succinctBitVectorLBSYomi = symbolSuccinctBitVectorLBSYomi,
            succinctBitVectorIsLeafYomi = symbolSuccinctBitVectorIsLeafYomi,
            succinctBitVectorTokenArray = symbolSuccinctBitVectorTokenArray,
            succinctBitVectorTangoLBS = symbolSuccinctBitVectorTangoLBS,
            type = 13,
        ).let { result ->
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                result.filterNot { it.string.containsHentaigana() }
            } else {
                result
            }
        }
        return candidates.take(limit).map { candidate ->
            AzooKeyDictionaryEntry(
                surface = candidate.string,
                reading = candidate.yomi ?: input,
                leftId = candidate.leftId?.toInt(),
                rightId = candidate.rightId?.toInt(),
                mid = AzooKeyMid.GENERAL,
                wordCost = candidate.score,
                sourceKind = AzooKeyDictionarySourceKind.Symbol,
            )
        }
    }

    fun getSymbolEmoticonCandidates(): List<Emoticon> = emoticonTokenArray.getNodeIds().map {
        emoticonTangoTrie.getLetterShortArray(
            it, emoticonSuccinctBitVectorTangoLBS
        )
    }.distinct().map { symbol ->
        Emoticon(
            symbol = symbol, category = symbol.toEmoticonCategory()
        )
    }

    fun getSymbolCandidates(): List<Symbol> {
        // Generate the initial list of symbols
        val initialSymbols = symbolTokenArray.getNodeIds().map {
            if (it >= 0) {
                // Corrected: Restored the original 'symbolTangoTrie' variable name
                symbolTangoTrie.getLetterShortArray(
                    it, symbolSuccinctBitVectorTangoLBS
                )
            } else {
                ""
            }
        }.distinct().filterNot { it.isBlank() }

        val filteredSymbols = if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            initialSymbols.filterNot { it.containsHentaigana() }
        } else {
            initialSymbols
        }

        return filteredSymbols.map { symbol ->
            Symbol(
                symbol = symbol, category = symbol.toSymbolCategory()
            )
        }
    }

    /**
     * 文字列に変体仮名が含まれているかを確認する拡張関数
     * 変体仮名のUnicode範囲: U+1B000..U+1B0FF
     */
    private fun String.containsHentaigana(): Boolean {
        return this.codePoints().anyMatch { codePoint ->
            codePoint in 0x1B000..0x1B0FF
        }
    }

    private fun createCandidatesForDate(
        calendar: Calendar, input: String
    ): List<Candidate> {
        val formatter1 = SimpleDateFormat("M/d", Locale.getDefault())
        val formatter2 = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault())
        val formatter3 = SimpleDateFormat("M月d日(EEE)", Locale.getDefault())
        val formatterReiwa =
            "令和${calendar.get(Calendar.YEAR) - 2018}年${calendar.get(Calendar.MONTH) + 1}月${
                calendar.get(Calendar.DAY_OF_MONTH)
            }日"
        val formatterR06 = "R${calendar.get(Calendar.YEAR) - 2018}/${
            String.format(
                Locale.getDefault(), "%02d", calendar.get(Calendar.MONTH) + 1
            )
        }/${String.format(Locale.getDefault(), "%02d", calendar.get(Calendar.DAY_OF_MONTH))}"
        val dayOfWeek = SimpleDateFormat("EEEE", Locale.getDefault()).format(calendar.time)

        return listOf(
            Candidate(
                string = formatter1.format(calendar.time),  // M/d format
                type = 14,
                length = input.length.toUByte(),
                score = 7000,
                leftId = 1851,
                rightId = 1851
            ), Candidate(
                string = formatter2.format(calendar.time),  // yyyy/MM/dd format
                type = 14,
                length = input.length.toUByte(),
                score = 7000,
                leftId = 1851,
                rightId = 1851
            ), Candidate(
                string = formatter3.format(calendar.time),  // M月d日(曜日) format
                type = 14,
                length = input.length.toUByte(),
                score = 7000,
                leftId = 1851,
                rightId = 1851
            ), Candidate(
                string = formatterReiwa,  // 令和 format
                type = 14,
                length = input.length.toUByte(),
                score = 7000,
                leftId = 1851,
                rightId = 1851
            ), Candidate(
                string = formatterR06,  // Rxx/MM/dd format
                type = 14,
                length = input.length.toUByte(),
                score = 7000,
                leftId = 1851,
                rightId = 1851
            ), Candidate(
                string = dayOfWeek,  // 曜日 format
                type = 14,
                length = input.length.toUByte(),
                score = 7000,
                leftId = 1851,
                rightId = 1851
            )
        )
    }

    /**
     * 4桁の数字を時刻の候補に変換する。
     *
     * @param input "0000"から"2959"までの4桁の数字文字列。
     * @return 時刻の候補リスト。条件に合わない場合は空のリストを返す。
     */
    private fun createCandidatesForTime(input: String): List<Candidate> {
        // 入力が4桁の数字でない場合は早期リターン
        if (!input.matches(Regex("""\d{4}"""))) {
            return emptyList()
        }

        val number = input.toInt()

        // 全体の数値が 0 から 2959 の範囲内かチェック
        if (number !in 0..2959) {
            return emptyList()
        }

        // 下2桁（分）が 0 から 59 の範囲内かチェック
        val minutes = number % 100
        if (minutes > 59) { // Redundant 'minutes < 0' check removed
            return emptyList()
        }

        // 時間と分を2桁の文字列として取り出す
        val hoursStr = input.substring(0, 2)
        val minutesStr = input.substring(2, 4)

        val length = input.length.toUByte()

        // 2つのフォーマットの候補を作成
        val candidate1 = Candidate(
            string = "$hoursStr:$minutesStr",
            type = 30,
            length = length,
            score = 8000,
            leftId = 1851,
            rightId = 1851
        )

        val candidate2 = Candidate(
            string = "${hoursStr}時${minutesStr}分",
            type = 30,
            length = length,
            score = 8000,
            leftId = 1851,
            rightId = 1851
        )

        return listOf(candidate1, candidate2)
    }

    /**
     * 3桁または4桁の数字を月日の候補に変換する。
     *
     * @param input "101"から"1231"のような3桁または4桁の数字文字列。
     * @return 月日の候補リスト。条件に合わない場合は空のリストを返す。
     */
    private fun createCandidatesForDateInDigit(input: String): List<Candidate> {
        // 入力が3桁または4桁の数字でない場合は早期リターン
        if (!input.matches(Regex("""\d{3,4}"""))) {
            return emptyList()
        }

        // 最後の2桁を「日」、それより前を「月」として分割
        val dayStr = input.substring(input.length - 2)
        val monthStr = input.substring(0, input.length - 2)

        val month = monthStr.toInt()
        val day = dayStr.toInt()

        // 月が1から12の範囲内かチェック
        if (month !in 1..12) {
            return emptyList()
        }

        // 日が1から31の範囲内かチェック（簡略版）
        // ※より厳密にする場合は、月ごとの日数（30日、31日、閏年など）を考慮する必要があります。
        if (day !in 1..31) {
            return emptyList()
        }

        // 候補の文字列を作成（例: 5月12日）
        // .toInt()で変換しているため、"05"のような先頭のゼロは自動的に除去されます。
        val dateString = "${month}月${day}日"

        val length = input.length.toUByte()

        // 候補を作成
        val candidate = Candidate(
            string = dateString, type = 40, // 時刻(30)とは別のタイプ番号を割り当て（例: 40）
            length = length, score = 8000, leftId = 1851, // 必要に応じて日付用のIDに変更
            rightId = 1851  // 必要に応じて日付用のIDに変更
        )

        return listOf(candidate)
    }

    private fun createCandidatesForEra(year: Int, input: String): List<Candidate> {
        // 元号名、開始年、終了年（null は現在まで）
        data class Era(val name: String, val start: Int, val end: Int?)

        val eras = listOf(
            Era("令和", 2019, null),    // 令和は継続中
            Era("平成", 1989, 2019),    // 平成は1989～2019
            Era("昭和", 1926, 1989),    // 昭和は1926～1989
            Era("大正", 1912, 1926),    // 大正は1912～1926
            Era("明治", 1868, 1912)     // 明治は1868～1912
        )

        val length = input.length.toUByte()

        fun formatEra(eraName: String, eraYear: Int) =
            eraName + if (eraYear == 1) "元年" else "${eraYear}年"

        return eras.filter { (_, start, end) ->
            year >= start && (end == null || year <= end)
        }.map { (name, start, _) ->
            val eraYear = year - start + 1
            Candidate(
                string = formatEra(name, eraYear),
                type = 30,
                length = length,
                score = 70000,
                leftId = 1851,
                rightId = 1851
            )
        }
    }

    private fun createCandidatesForTime(cal: Calendar, input: String): List<Candidate> {
        val hour24 = cal.get(Calendar.HOUR_OF_DAY)
        val minute = cal.get(Calendar.MINUTE)
        val minutePadded = minute.toString().padStart(2, '0')

        // 12時間表記と午前/午後
        val meridiem = if (hour24 < 12) "午前" else "午後"
        val hour12 = when {
            hour24 == 0 -> 12
            hour24 > 12 -> hour24 - 12
            else -> hour24
        }

        return listOf(
            // 例: "14時5分"
            Candidate(
                string = "${hour24}時${minute}分",
                type = 14,
                length = input.length.toUByte(),
                score = 7000,
                leftId = 1851,
                rightId = 1851
            ),
            // 例: "14:05"
            Candidate(
                string = "${
                    hour24.toString().padStart(2, '0')
                }:$minutePadded",
                type = 14,
                length = input.length.toUByte(),
                score = 7001,
                leftId = 1851,
                rightId = 1851
            ),
            // 例: "午後2時5分"
            Candidate(
                string = "$meridiem${hour12}時${minute}分",
                type = 14,
                length = input.length.toUByte(),
                score = 7003,
                leftId = 1851,
                rightId = 1851
            ),
            Candidate(
                string = "${hour12}時${minute}分",
                type = 14,
                length = input.length.toUByte(),
                score = 7004,
                leftId = 1851,
                rightId = 1851
            ),
            Candidate(
                string = "${
                    hour12.toString().padStart(2, '0')
                }:$minutePadded",
                type = 14,
                length = input.length.toUByte(),
                score = 7002,
                leftId = 1851,
                rightId = 1851
            ),
        )
    }

    private fun deferredFromDictionarySymbols(
        input: String,
        commonPrefixListString: List<String>,
        yomiTrie: LOUDSWithTermId,
        tokenArray: TokenArray,
        tangoTrie: LOUDS,
        succinctBitVectorLBSYomi: SuccinctBitVector,
        succinctBitVectorIsLeafYomi: SuccinctBitVector,
        succinctBitVectorTokenArray: SuccinctBitVector,
        succinctBitVectorTangoLBS: SuccinctBitVector,
        type: Byte
    ): List<Candidate> {
        return commonPrefixListString.flatMap { yomi ->
            if (input.length > yomi.length) {
                return@flatMap emptyList<Candidate>()
            }
            val termIdArray = yomiTrie.getTermIdShortArray(
                yomiTrie.getNodeIndex(yomi, succinctBitVectorLBSYomi), succinctBitVectorIsLeafYomi
            )
            tokenArray.getListDictionaryByYomiTermIdShortArray(
                termIdArray, succinctBitVectorTokenArray
            ).map { entry ->
                val penalty = when (type.toInt()) {
                    13 -> 3500
                    11, 12 -> 2000
                    else -> 0
                }
                Candidate(
                    string = when (entry.nodeId) {
                        -2 -> yomi
                        -1 -> yomi.hiraToKata()
                        else -> tangoTrie.getLetterShortArray(
                            entry.nodeId, succinctBitVectorTangoLBS
                        )
                    },
                    type = type,
                    length = yomi.length.toUByte(),
                    score = entry.wordCost.toInt() + penalty + if (yomi.length == input.length) 0
                    else 1500 * (yomi.length - input.length),
                    yomi = yomi,
                    leftId = tokenArray.leftIds[entry.posTableIndex.toInt()],
                    rightId = tokenArray.rightIds[entry.posTableIndex.toInt()]
                )
            }
        }.sortedBy { it.score }.take(60)
    }

    private fun addHalfWidthCandidates(
        originalData: Pair<List<Candidate>, List<Int>>
    ): Pair<List<Candidate>, List<Int>> {

        val originalList = originalData.first
        val intList = originalData.second

        // flatMap を使ってリストを変換・拡張します
        val newList = originalList.flatMap { candidate ->
            // 1. string に全角数字が含まれているかチェック
            if (candidate.string.containsFullWidthNumber()) {

                // 2. 全角数字を半角に変換
                val newString = candidate.string.convertFullWidthNumbersToHalfWidth()

                // 3. 元の候補のコピーを作成し、string だけを新しい文字列に差し替え
                val newCandidate = candidate.copy(
                    string = newString, type = 31, score = candidate.score + 6000
                    // type, length, score など他のプロパティはそのままコピーされます
                )

                // 4. 元の候補 (例: １８時) と、
                //    新しい候補 (例: 18時) の両方をリストとして返す
                listOf(candidate, newCandidate)

            } else {
                // 5. 全角数字を含まない場合は、元の候補だけをリストとして返す
                listOf(candidate)
            }
        }.distinct() // 最後にリスト全体の重複を除去します

        // 6. 新しく作成した候補リストと、元の Int リストで Pair を再構築して返す
        return Pair(newList, intList)
    }

    private fun addHalfWidthCandidates(
        originalData: BunsetsuCandidateResult
    ): BunsetsuCandidateResult {
        val newList = addHalfWidthCandidates(originalData.candidates)
        return BunsetsuCandidateResult(
            candidates = newList,
            splitPatterns = originalData.splitPatterns,
            splitPatternByCandidateString = originalData.splitPatternByCandidateString
        )
    }

    /**
     * Candidate リストを処理し、
     * string に全角数字が含まれる場合、それを半角数字に変換した
     * 新しい Candidate をリストに追加します。
     *
     * @param originalList 元の List<Candidate>
     * @return 元の候補と、半角変換された候補の両方を含む新しい List
     */
    private fun addHalfWidthCandidates(
        originalList: List<Candidate>
    ): List<Candidate> {

        // flatMap を使ってリストを変換・拡張します
        val newList = originalList.flatMap { candidate ->
            // 1. string に全角数字が含まれているかチェック
            if (candidate.string.containsFullWidthNumber()) {

                // 2. 全角数字を半角に変換
                val newString = candidate.string.convertFullWidthAlnumToHalfWidth()

                // 3. 元の候補のコピーを作成し、string だけを新しい文字列に差し替え
                val newCandidate = candidate.copy(
                    string = newString, type = 31, score = candidate.score + 6000
                    // type, length, score など他のプロパティはそのままコピーされます
                )

                // 4. 元の候補 (例: １８時) と、
                //    新しい候補 (例: 18時) の両方をリストとして返す
                listOf(candidate, newCandidate)

            } else {
                // 5. 全角数字を含まない場合は、元の候補だけをリストとして返す
                listOf(candidate)
            }
        }.distinct() // 最後にリスト全体の重複を除去します

        // 6. 新しく作成した候補リストを返す
        return newList
    }

}
