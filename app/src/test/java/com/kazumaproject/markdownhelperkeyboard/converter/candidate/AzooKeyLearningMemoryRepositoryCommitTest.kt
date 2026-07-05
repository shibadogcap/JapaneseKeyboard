package com.kazumaproject.markdownhelperkeyboard.converter.candidate

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.markdownhelperkeyboard.learning.database.LearnDao
import com.kazumaproject.markdownhelperkeyboard.learning.database.LearnEntity
import com.kazumaproject.markdownhelperkeyboard.learning.model.LearnResult
import com.kazumaproject.markdownhelperkeyboard.repository.LearnRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AzooKeyLearningMemoryRepositoryCommitTest {
    @Test
    fun commitTappedCandidateUpsertsRoom() = runTest {
        val entries = mutableListOf<LearnEntity>()
        val repository = repositoryWithEntries(entries)

        repository.commitTappedCandidate(
            reading = "きょう",
            candidate = Candidate(
                string = "今日",
                type = CandidateType.NBEST,
                length = 2.toUByte(),
                score = 1200,
                leftId = 1,
                rightId = 2,
            ),
            position = 1,
        )

        assertEquals(1, entries.size)
        assertEquals("きょう", entries.single().input)
        assertEquals("今日", entries.single().out)
        assertEquals(700.toShort(), entries.single().score)
        assertEquals(1.toShort(), entries.single().leftId)
        assertEquals(2.toShort(), entries.single().rightId)
    }

    @Test
    fun commitTransitionUsesDefaultScore() = runTest {
        val entries = mutableListOf<LearnEntity>()
        val repository = repositoryWithEntries(entries)

        repository.commitTransition(
            previousCandidate = Candidate(
                string = "東京",
                yomi = "とうきょう",
                type = CandidateType.NBEST,
                length = 2.toUByte(),
                score = 1000,
            ),
            currentCandidate = Candidate(
                string = "駅",
                yomi = "えき",
                type = CandidateType.NBEST,
                length = 1.toUByte(),
                score = 1000,
            ),
        )

        assertEquals("とうきょうえき", entries.single().input)
        assertEquals("東京駅", entries.single().out)
        assertEquals(3000.toShort(), entries.single().score)
    }

    @Test
    fun commitFromCandidateRespectsLearningTypeGate() = runTest {
        val entries = mutableListOf<LearnEntity>()
        val repository = repositoryWithEntries(entries)

        repository.commitFromCandidate(
            candidate = Candidate(
                string = "今日",
                yomi = "きょう",
                type = CandidateType.NBEST,
                length = 2.toUByte(),
                score = 1000,
            ),
            learningType = AzooKeyStyleLearningType.OnlyOutput,
        )
        assertEquals(0, entries.size)

        repository.commitFromCandidate(
            candidate = Candidate(
                string = "今日",
                yomi = "きょう",
                type = CandidateType.NBEST,
                length = 2.toUByte(),
                score = 1000,
            ),
            learningType = AzooKeyStyleLearningType.InputAndOutput,
        )
        assertEquals("きょう", entries.single().input)
        assertEquals("今日", entries.single().out)
    }

    @Test
    fun commitTappedCandidateExportsMemoryLoudsWhenCharIdAvailable() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val assetProvider = AzooKeyDictionaryAssetProvider(context)
        assumeTrue("charID.chid required for memory export", assetProvider.charIdMap != null)

        val entries = mutableListOf<LearnEntity>()
        val learnRepository = LearnRepository(InMemoryLearnDao(entries))
        val store = AzooKeyLearningMemoryStore(context, assetProvider)
        val repository = AzooKeyLearningMemoryRepository(learnRepository, store)

        repository.commitTappedCandidate(
            reading = "しかい",
            candidate = Candidate(
                string = "司会",
                type = CandidateType.NBEST,
                length = 2.toUByte(),
                score = 1200,
                yomi = "しかい",
            ),
            position = 0,
        )

        assertTrue(
            AzooKeyLearningMemoryPersistence.hasPersistedFiles(store.directory),
        )
        val lookup = AzooKeyDictionaryShardLoader(
            readBytes = { path -> File(path).takeIf { it.isFile }?.readBytes() },
            loudsDirectory = store.directory.absolutePath,
        ).loadLoudsDictionaryLookup("memory")
        assumeTrue("memory LOUDS lookup should load", lookup != null)
        assertTrue(lookup!!.exactEntries("しかい").any { it.surface == "司会" })
    }

    @Test
    fun commitTappedCandidateTwiceUpsertsLoudsValue() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val assetProvider = AzooKeyDictionaryAssetProvider(context)
        assumeTrue("charID.chid required for memory export", assetProvider.charIdMap != null)

        val entries = mutableListOf<LearnEntity>()
        val learnRepository = LearnRepository(InMemoryLearnDao(entries))
        val store = AzooKeyLearningMemoryStore(context, assetProvider)
        val repository = AzooKeyLearningMemoryRepository(learnRepository, store)

        val candidate = Candidate(
            string = "司会",
            type = CandidateType.NBEST,
            length = 2.toUByte(),
            score = 1200,
            yomi = "しかい",
        )
        repository.commitTappedCandidate(reading = "しかい", candidate = candidate, position = 0)
        val lookupLoader = AzooKeyDictionaryShardLoader(
            readBytes = { path -> File(path).takeIf { it.isFile }?.readBytes() },
            loudsDirectory = store.directory.absolutePath,
        )
        val lookupAfterFirst = lookupLoader.loadLoudsDictionaryLookup("memory")
        assumeTrue("memory LOUDS lookup should load", lookupAfterFirst != null)
        val costAfterFirst = lookupAfterFirst!!.exactEntries("しかい")
            .first { it.surface == "司会" }
            .wordCost

        repository.commitTappedCandidate(reading = "しかい", candidate = candidate, position = 0)

        assertEquals(1, entries.size)
        val lookupAfterSecond = lookupLoader.loadLoudsDictionaryLookup("memory")
        assumeTrue("memory LOUDS lookup should load", lookupAfterSecond != null)
        val costAfterSecond = lookupAfterSecond!!.exactEntries("しかい")
            .first { it.surface == "司会" }
            .wordCost
        assertNotEquals(costAfterFirst, costAfterSecond)
        assertEquals(0, costAfterSecond)
    }

    @Test
    fun concurrentCommitTransitionsDoNotCorruptMemory() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val assetProvider = AzooKeyDictionaryAssetProvider(context)
        assumeTrue("charID.chid required", assetProvider.charIdMap != null)

        val entries = mutableListOf<LearnEntity>()
        val learnRepository = LearnRepository(InMemoryLearnDao(entries))
        val store = AzooKeyLearningMemoryStore(context, assetProvider)
        val repository = AzooKeyLearningMemoryRepository(learnRepository, store)
        val surfaces = listOf(
            "あい", "うえ", "おか", "きく", "けこ", "さし", "すせ", "そた",
            "ちつ", "てと", "なに", "ぬね", "のは", "ひふ", "へほ", "まみ",
        )
        kotlinx.coroutines.coroutineScope {
            val jobs = surfaces.map { surface ->
                launch {
                    repository.commitTransition(
                        previousCandidate = Candidate(
                            string = "日本",
                            yomi = "にほん",
                            type = CandidateType.NBEST,
                            length = 2.toUByte(),
                            score = 1000,
                        ),
                        currentCandidate = Candidate(
                            string = surface,
                            yomi = surface,
                            type = CandidateType.NBEST,
                            length = surface.length.toUByte(),
                            score = 1000,
                        ),
                    )
                }
            }
            jobs.forEach { it.join() }
        }
        assertEquals(16, entries.size)
        val lookup = AzooKeyDictionaryShardLoader(
            readBytes = { path -> File(path).takeIf { it.isFile }?.readBytes() },
            loudsDirectory = store.directory.absolutePath,
        ).loadLoudsDictionaryLookup("memory")
        assumeTrue("memory LOUDS lookup should load", lookup != null)
        val transitionSurfaces = lookup!!.prefixEntries("にほん", maxDepth = 10, maxCount = 100).map { it.surface }.toSet()
        assertEquals(surfaces.map { "日本$it" }.toSet(), transitionSurfaces)
    }

    @Test
    fun commitHomographSurfacesBothAppearOnDisk() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val assetProvider = AzooKeyDictionaryAssetProvider(context)
        assumeTrue("charID.chid required", assetProvider.charIdMap != null)

        val entries = mutableListOf<LearnEntity>()
        val learnRepository = LearnRepository(InMemoryLearnDao(entries))
        val store = AzooKeyLearningMemoryStore(context, assetProvider)
        val repository = AzooKeyLearningMemoryRepository(learnRepository, store)
        repository.commitTappedCandidate(
            reading = "しかい",
            candidate = Candidate(
                string = "司会",
                type = CandidateType.NBEST,
                length = 2.toUByte(),
                score = 1200,
                yomi = "しかい",
            ),
            position = 0,
        )
        repository.commitTappedCandidate(
            reading = "しかい",
            candidate = Candidate(
                string = "試会",
                type = CandidateType.NBEST,
                length = 2.toUByte(),
                score = 1100,
                yomi = "しかい",
            ),
            position = 0,
        )
        assertEquals(2, entries.size)
        val lookup = AzooKeyDictionaryShardLoader(
            readBytes = { path -> File(path).takeIf { it.isFile }?.readBytes() },
            loudsDirectory = store.directory.absolutePath,
        ).loadLoudsDictionaryLookup("memory")
        assumeTrue("memory LOUDS lookup should load", lookup != null)
        val surfaces = lookup!!.exactEntries("しかい").map { it.surface }.toSet()
        assertTrue(surfaces.containsAll(setOf("司会", "試会")))
    }

    @Test
    fun commitTappedCandidateIgnoresBlankReading() = runTest {
        val entries = mutableListOf<LearnEntity>()
        val repository = repositoryWithEntries(entries)

        repository.commitTappedCandidate(
            reading = " ",
            candidate = Candidate(
                string = "今日",
                type = CandidateType.NBEST,
                length = 2.toUByte(),
                score = 1000,
            ),
            position = 0,
        )

        assertEquals(0, entries.size)
    }

    private fun repositoryWithEntries(
        entries: MutableList<LearnEntity>,
    ): AzooKeyLearningMemoryRepository {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val learnRepository = LearnRepository(InMemoryLearnDao(entries))
        val store = AzooKeyLearningMemoryStore(context, AzooKeyDictionaryAssetProvider(context))
        return AzooKeyLearningMemoryRepository(learnRepository, store)
    }

    private class InMemoryLearnDao(
        private val entries: MutableList<LearnEntity>,
    ) : LearnDao {
        override suspend fun insert(learnData: LearnEntity) {
            entries.add(learnData.copy(id = entries.size + 1))
        }

        override suspend fun findByInput(input: String): List<LearnResult>? =
            entries.filter { it.input == input }.map { LearnResult(it.out, it.score.toInt()) }

        override suspend fun findByInputAndOutput(input: String, output: String): LearnEntity? =
            entries.firstOrNull { it.input == input && it.out == output }

        override suspend fun existsDuplicateForUpdate(
            input: String,
            output: String,
            excludeId: Int,
        ): Boolean = entries.any { it.input == input && it.out == output && it.id != excludeId }

        override fun all(): Flow<List<LearnEntity>> = flowOf(entries.toList())

        override suspend fun getAllSuspend(): List<LearnEntity> = entries.toList()

        override suspend fun insertAll(learnDataList: List<LearnEntity>) {
            learnDataList.forEach { insert(it) }
        }

        override suspend fun predictiveSearchByInput(prefix: String, limit: Int): List<LearnEntity> =
            entries.filter { it.input.startsWith(prefix) }.take(limit)

        override suspend fun findCommonPrefixes(searchTerm: String): List<LearnEntity> =
            entries.filter { searchTerm.startsWith(it.input) }

        override suspend fun updateLearnedData(learnData: LearnEntity) {
            val index = entries.indexOfFirst { it.id == learnData.id }
            if (index >= 0) entries[index] = learnData
        }

        override suspend fun delete(learnData: LearnEntity) {
            entries.removeAll { it.id == learnData.id }
        }

        override suspend fun deleteAll() {
            entries.clear()
        }

        override suspend fun deleteByInput(input: String): Int {
            val before = entries.size
            entries.removeAll { it.input == input }
            return before - entries.size
        }

        override suspend fun deleteByInputAndOutput(input: String, output: String): Int {
            val before = entries.size
            entries.removeAll { it.input == input && it.out == output }
            return before - entries.size
        }
    }
}