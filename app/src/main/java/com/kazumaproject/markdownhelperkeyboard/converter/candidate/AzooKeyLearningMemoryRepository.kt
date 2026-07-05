package com.kazumaproject.markdownhelperkeyboard.converter.candidate

import com.kazumaproject.markdownhelperkeyboard.learning.database.LearnEntity
import com.kazumaproject.markdownhelperkeyboard.repository.LearnRepository
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

import kotlinx.coroutines.CoroutineScope

/**
 * Room + LOUDS memory の二層（AzooKey LearningManager 相当）。
 * 検索は LOUDS 優先、未構築時は Room から LOUDS を生成する。
 */
@Singleton
class AzooKeyLearningMemoryRepository @Inject constructor(
    private val learnRepository: LearnRepository,
    private val learningMemoryStore: AzooKeyLearningMemoryStore,
) {
    suspend fun prefixSearch(reading: String, limit: Int): List<AzooKeyDictionaryEntry> {
        if (reading.isBlank() || limit <= 0) return emptyList()
        return learningMemoryStore.prefixSearch(
            reading = reading,
            limit = limit,
            seedEntries = { loadAllEntriesFromRoom() },
        )
    }

    suspend fun commitWord(
        reading: String,
        surface: String,
        leftId: Short? = null,
        rightId: Short? = null,
        initialScore: Short = 3000,
    ) {
        val entity = LearnEntity(
            input = reading,
            out = surface,
            score = initialScore,
            leftId = leftId,
            rightId = rightId,
        )
        val entry = AzooKeyLearningMemoryValue.entryFromLearnEntity(entity)
        learningMemoryStore.memorizeSession(entry)
        try {
            learningMemoryStore.commitPersistedEntry(entry) {
                learnRepository.upsertLearnedData(entity)
            }
        } catch (_: Exception) {
            try {
                rebuildLoudsFromRoom()
            } catch (rebuildError: Exception) {
                // Ignore
            }
        }
    }

    suspend fun commitFromCandidate(
        candidate: Candidate,
        learningType: AzooKeyStyleLearningType,
    ) {
        if (!learningType.shouldUpdate) return
        val reading = candidate.yomi?.takeIf { it.isNotBlank() } ?: return
        commitWord(
            reading = reading,
            surface = candidate.string,
            leftId = candidate.leftId,
            rightId = candidate.rightId,
        )
    }

    /** 候補タップ確定時の読み→表記学習（Room + セッション LOUDS）。 */
    suspend fun commitTappedCandidate(
        reading: String,
        candidate: Candidate,
        position: Int,
    ) {
        if (reading.isBlank() || !candidate.isLearningTarget) return
        val score = tappedCandidateScore(candidate, position)

        // 1) 構成要素 (形態素) の個別学習 (unigram)
        for (item in candidate.data) {
            commitWord(
                reading = item.reading,
                surface = item.surface,
                leftId = item.leftId?.toShort(),
                rightId = item.rightId?.toShort(),
                initialScore = score,
            )
        }

        // 2) 文節境界での bigram 学習（AzooKey LearningManager 相当）
        val data = candidate.data
        for (i in 0 until data.size - 1) {
            val prevRcid = data[i].rightId ?: continue
            val nextLcid = data[i + 1].leftId ?: continue
            if (com.kazumaproject.markdownhelperkeyboard.converter.lattice.AzooKeyDicdataStoreUtils.isClause(
                    prevRcid,
                    nextLcid,
                )
            ) {
                commitWord(
                    reading = data[i].reading + data[i + 1].reading,
                    surface = data[i].surface + data[i + 1].surface,
                    initialScore = 3000,
                )
            }
        }

        // 3) フレーズ全体の学習
        if (data.isNotEmpty()) {
            commitWord(
                reading = data.joinToString(separator = "") { it.reading },
                surface = candidate.string,
                leftId = data.first().leftId?.toShort(),
                rightId = data.last().rightId?.toShort(),
                initialScore = score,
            )
        } else {
            commitWord(
                reading = reading,
                surface = candidate.string,
                leftId = candidate.leftId,
                rightId = candidate.rightId,
                initialScore = score,
            )
        }
    }

    /** 確定語同士のつながり学習（AzooKeyスタイル：隣接文節結合unigram学習）。 */
    suspend fun commitTransition(
        previousCandidate: Candidate,
        currentCandidate: Candidate,
    ) {
        if (!previousCandidate.isLearningTarget || !currentCandidate.isLearningTarget) return
        val prevLast = previousCandidate.data.lastOrNull()
        val currFirst = currentCandidate.data.firstOrNull()
        val prevReading = prevLast?.reading
            ?: previousCandidate.yomi?.takeIf { it.isNotBlank() }
            ?: return
        val currReading = currFirst?.reading
            ?: currentCandidate.yomi?.takeIf { it.isNotBlank() }
            ?: return
        val prevSurface = prevLast?.surface ?: previousCandidate.string
        val currSurface = currFirst?.surface ?: currentCandidate.string

        commitWord(
            reading = prevReading + currReading,
            surface = prevSurface + currSurface,
            initialScore = 3000,
        )
    }

    private fun tappedCandidateScore(candidate: Candidate, position: Int): Short {
        return ((candidate.score - 500 * position).coerceAtLeast(0)).toShort()
    }

    suspend fun rebuildLoudsFromRoom() {
        val entries = loadAllEntriesFromRoom()
        AzooKeyLearningMemoryPersistence.deleteAllPersistedFiles(learningMemoryStore.directory)
        if (entries.isEmpty()) {
            learningMemoryStore.invalidateLoudsCache()
            return
        }
        learningMemoryStore.invalidateLoudsCache()
        learningMemoryStore.persistSessionAndRoomEntries(entries)
    }

    private suspend fun loadAllEntriesFromRoom(): List<AzooKeyDictionaryEntry> {
        val today = AzooKeyLearningMemoryDecay.todayEpochDay()
        return AzooKeyLearningMemoryDecay.trimToMaxCount(
            learnRepository.allSuspend()
                .mapNotNull { entity ->
                    val decayed = AzooKeyLearningMemoryDecay.applyDecayToScore(
                        score = entity.score.toInt(),
                        lastUpdatedDay = today,
                        lastUsedDay = today,
                    ) ?: return@mapNotNull null
                    AzooKeyLearningMemoryValue.entryFromLearnEntity(
                        entity.copy(score = decayed.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()),
                    )
                },
        )
    }
}