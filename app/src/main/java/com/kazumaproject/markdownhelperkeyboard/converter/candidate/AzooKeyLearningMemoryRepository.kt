package com.kazumaproject.markdownhelperkeyboard.converter.candidate

import com.kazumaproject.markdownhelperkeyboard.learning.database.LearnEntity
import com.kazumaproject.markdownhelperkeyboard.repository.LearnRepository
import javax.inject.Inject
import javax.inject.Singleton

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
        try {
            learningMemoryStore.commitPersistedEntry(entry) {
                learnRepository.upsertLearnedData(entity)
            }
        } catch (_: Exception) {
            rebuildLoudsFromRoom()
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
        if (reading.isBlank()) return
        commitWord(
            reading = reading,
            surface = candidate.string,
            leftId = candidate.leftId,
            rightId = candidate.rightId,
            initialScore = tappedCandidateScore(candidate, position),
        )
    }

    /** 確定語同士のつながり学習（AzooKeyスタイル：隣接文節結合unigram学習）。 */
    suspend fun commitTransition(
        previousCandidate: Candidate,
        currentCandidate: Candidate,
    ) {
        val prevReading = previousCandidate.yomi
        val currReading = currentCandidate.yomi
        if (prevReading.isNullOrBlank() || currReading.isNullOrBlank()) return
        commitWord(
            reading = prevReading + currReading,
            surface = previousCandidate.string + currentCandidate.string,
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
        return learnRepository.allSuspend()
            .map(AzooKeyLearningMemoryValue::entryFromLearnEntity)
    }
}