package com.kazumaproject.markdownhelperkeyboard.converter.candidate

import com.kazumaproject.dictionary.models.TokenEntryConverted
import com.kazumaproject.graph.Node

/**
 * Kotlin-side counterpart of azooKey's DicdataElement.
 *
 * The current app dictionaries do not all expose azooKey-compatible connection
 * IDs yet, so lcid/rcid are nullable at this boundary. Resolvers can fill them
 * before the full lattice engine consumes the entry.
 */
data class AzooKeyDictionaryEntry(
    val surface: String,
    val reading: String,
    val leftId: Int?,
    val rightId: Int?,
    val mid: Int,
    val wordCost: Int,
    val value: AzooKeyPValue = wordCost.toFloat(),
    val sourceKind: AzooKeyDictionarySourceKind,
    val legacyPosIndex: Int? = null,
    val metadata: Set<AzooKeyDictionaryMetadata> = emptySet(),
) {
    fun toCandidate(
        type: Byte,
        connectionIdResolver: AzooKeyDictionaryConnectionIdResolver? = null,
    ): Candidate {
        val resolved = connectionIdResolver?.resolve(this) ?: this
        return Candidate(
            string = resolved.surface,
            type = type,
            length = resolved.reading.length.toUByte(),
            score = resolved.wordCost,
            value = resolved.value,
            yomi = resolved.reading,
            leftId = resolved.leftId?.toShort(),
            rightId = resolved.rightId?.toShort(),
        )
    }
}

enum class AzooKeyDictionarySourceKind {
    System,
    SystemUser,
    User,
    Learned,
    /** AzooKey LOUDS memory identifier 相当（学習辞書） */
    Memory,
    Template,
    Emoji,
    Symbol,
    Special,
}

enum class AzooKeyDictionaryMetadata {
    Learned,
    FromUserDictionary,
    EmojiVariation,
    EmojiTextReplacer,
    EmojiDicdata,
}

object AzooKeyCid {
    const val BOS = 0
    const val SYMBOL = 5
    const val GENERAL_NOUN = 1285
    const val PROPER_NOUN = 1288
    const val NUMBER = 1295
    const val EOS = 1316
}

object AzooKeyMid {
    const val GENERAL = 501
    const val NUMBER = 452
    const val EMOJI = 501
}

object AzooKeyDictionaryEntryMapper {
    fun systemDictionary(
        surface: String,
        reading: String,
        wordCost: Int,
        leftId: Int,
        rightId: Int,
        mid: Int,
    ): AzooKeyDictionaryEntry {
        return AzooKeyDictionaryEntry(
            surface = surface,
            reading = reading,
            leftId = leftId,
            rightId = rightId,
            mid = mid,
            wordCost = wordCost,
            sourceKind = AzooKeyDictionarySourceKind.System,
        )
    }

    fun tokenEntryConverted(
        reading: String,
        tokenEntry: TokenEntryConverted,
    ): AzooKeyDictionaryEntry {
        return systemDictionary(
            surface = tokenEntry.tango,
            reading = reading.take(tokenEntry.yomiLength.toInt()),
            wordCost = tokenEntry.wordCost.toInt(),
            leftId = tokenEntry.leftId.toInt(),
            rightId = tokenEntry.rightId.toInt(),
            mid = AzooKeyMid.GENERAL,
        )
    }

    fun learned(
        surface: String,
        reading: String,
        score: Int,
        leftId: Int?,
        rightId: Int?,
    ): AzooKeyDictionaryEntry = memory(
        surface = surface,
        reading = reading,
        leftId = leftId,
        rightId = rightId,
        legacyScore = score,
        readingLength = reading.length,
    )

    fun memory(
        surface: String,
        reading: String,
        leftId: Int?,
        rightId: Int?,
        legacyScore: Int,
        readingLength: Int,
        occurrenceCount: Int = 1,
    ): AzooKeyDictionaryEntry {
        val value = AzooKeyLearningMemoryValue.pValue(
            readingLength = readingLength,
            occurrenceCount = occurrenceCount,
        )
        return AzooKeyDictionaryEntry(
            surface = surface,
            reading = reading,
            leftId = leftId,
            rightId = rightId,
            mid = AzooKeyMid.GENERAL,
            wordCost = legacyScore,
            value = value,
            sourceKind = AzooKeyDictionarySourceKind.Memory,
            metadata = setOf(AzooKeyDictionaryMetadata.Learned),
        )
    }

    fun userDictionaryWithContextId(
        surface: String,
        reading: String,
        score: Int,
        contextId: Int,
        legacyPosIndex: Int?,
    ): AzooKeyDictionaryEntry {
        return AzooKeyDictionaryEntry(
            surface = surface,
            reading = reading,
            leftId = contextId,
            rightId = contextId,
            mid = AzooKeyMid.GENERAL,
            wordCost = score,
            sourceKind = AzooKeyDictionarySourceKind.User,
            legacyPosIndex = legacyPosIndex,
            metadata = setOf(AzooKeyDictionaryMetadata.FromUserDictionary),
        )
    }

    fun userDictionary(
        surface: String,
        reading: String,
        score: Int,
        legacyPosIndex: Int?,
    ): AzooKeyDictionaryEntry {
        return AzooKeyDictionaryEntry(
            surface = surface,
            reading = reading,
            leftId = AzooKeyCid.PROPER_NOUN,
            rightId = AzooKeyCid.PROPER_NOUN,
            mid = AzooKeyMid.GENERAL,
            wordCost = score,
            sourceKind = AzooKeyDictionarySourceKind.User,
            legacyPosIndex = legacyPosIndex,
            metadata = setOf(AzooKeyDictionaryMetadata.FromUserDictionary),
        )
    }

    fun systemUserDictionary(
        surface: String,
        reading: String,
        score: Int,
        leftId: Int,
        rightId: Int,
    ): AzooKeyDictionaryEntry {
        return AzooKeyDictionaryEntry(
            surface = surface,
            reading = reading,
            leftId = leftId,
            rightId = rightId,
            mid = AzooKeyMid.GENERAL,
            wordCost = score,
            sourceKind = AzooKeyDictionarySourceKind.SystemUser,
            metadata = setOf(AzooKeyDictionaryMetadata.FromUserDictionary),
        )
    }

    fun template(
        surface: String,
        reading: String,
        score: Int,
    ): AzooKeyDictionaryEntry {
        return AzooKeyDictionaryEntry(
            surface = surface,
            reading = reading,
            leftId = AzooKeyCid.PROPER_NOUN,
            rightId = AzooKeyCid.PROPER_NOUN,
            mid = AzooKeyMid.GENERAL,
            wordCost = score,
            sourceKind = AzooKeyDictionarySourceKind.Template,
        )
    }

    fun emoji(
        surface: String,
        reading: String,
        score: Int = -3,
        metadata: Set<AzooKeyDictionaryMetadata> = emptySet(),
    ): AzooKeyDictionaryEntry {
        return AzooKeyDictionaryEntry(
            surface = surface,
            reading = reading,
            leftId = AzooKeyCid.SYMBOL,
            rightId = AzooKeyCid.SYMBOL,
            mid = AzooKeyMid.EMOJI,
            wordCost = score,
            sourceKind = AzooKeyDictionarySourceKind.Emoji,
            metadata = metadata,
        )
    }

    fun symbol(
        surface: String,
        reading: String,
        score: Int = -3,
    ): AzooKeyDictionaryEntry {
        return AzooKeyDictionaryEntry(
            surface = surface,
            reading = reading,
            leftId = AzooKeyCid.SYMBOL,
            rightId = AzooKeyCid.SYMBOL,
            mid = AzooKeyMid.GENERAL,
            wordCost = score,
            sourceKind = AzooKeyDictionarySourceKind.Symbol,
        )
    }
}

object AzooKeyDictionaryNodeMapper {
    fun toNode(
        entry: AzooKeyDictionaryEntry,
        startPosition: Int,
        scoreOffset: Int = 0,
        connectionIdResolver: AzooKeyDictionaryConnectionIdResolver = AzooKeyDictionaryConnectionIdResolver(),
    ): Node {
        val resolved = connectionIdResolver.resolve(entry)
        val score = resolved.wordCost + scoreOffset
        return Node(
            l = resolved.leftId?.toShort() ?: AzooKeyCid.PROPER_NOUN.toShort(),
            r = resolved.rightId?.toShort() ?: AzooKeyCid.PROPER_NOUN.toShort(),
            score = score,
            f = score,
            g = score,
            tango = resolved.surface,
            yomiUsed = resolved.reading,
            len = resolved.reading.length.toShort(),
            sPos = startPosition,
        )
    }
}

object AzooKeyDictionaryConnectionIdPolicies {
    private const val LEGACY_LEARNED_GRAPH_CONTEXT_ID = 1851

    val Default = AzooKeyDictionaryConnectionIdResolver()
    val LearnedGraph = AzooKeyDictionaryConnectionIdResolver(
        defaultLeftId = LEGACY_LEARNED_GRAPH_CONTEXT_ID,
        defaultRightId = LEGACY_LEARNED_GRAPH_CONTEXT_ID,
    )
}

class AzooKeyDictionaryConnectionIdResolver(
    private val defaultLeftId: Int = AzooKeyCid.PROPER_NOUN,
    private val defaultRightId: Int = AzooKeyCid.PROPER_NOUN,
) {
    fun resolve(entry: AzooKeyDictionaryEntry): AzooKeyDictionaryEntry {
        if (entry.leftId != null && entry.rightId != null) {
            return entry
        }
        return entry.copy(
            leftId = entry.leftId ?: defaultLeftId,
            rightId = entry.rightId ?: defaultRightId,
        )
    }
}
