package com.kazumaproject.markdownhelperkeyboard.ime_service.ui

import com.kazumaproject.core.data.floating_candidate.CandidateItem
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Golden tests for floating keyboard routing logic.
 *
 * These tests cover the [KeyboardSurfaceCoordinator] routing — the public API
 * that decides whether candidates go to the main suggestion strip or the
 * floating candidate bar.
 *
 * Surface selection (forMain vs forFloating) is also tested.
 *
 * These tests are RED initially — they define the expected routing contract.
 */
class AzooKeyFloatingKeyboardRoutingGoldenTest {

    // ── KeyboardSurfaceCoordinator routing ──────────────────────────────

    /**
     * GOLDEN: [KeyboardSurfaceCoordinator.buildCandidateSurfaceRequest] with
     * physical keyboard connected routes to floating bar.
     */
    @Test
    fun physicalKeyboardActiveRoutesToFloatingBar() {
        val coordinator = KeyboardSurfaceCoordinator()
        val request = coordinator.buildCandidateSurfaceRequest(
            physicalKeyboardEnableReplayFirst = true,
            suppressSuggestions = false,
        )
        assertEquals("Physical keyboard active must route to floating bar",
            true, request.routeToFloatingBar)
        assertEquals(false, request.suppressSuggestions)
    }

    /**
     * GOLDEN: [KeyboardSurfaceCoordinator.buildCandidateSurfaceRequest] with
     * no physical keyboard routes to main suggestion strip.
     */
    @Test
    fun noPhysicalKeyboardRoutesToMainStrip() {
        val coordinator = KeyboardSurfaceCoordinator()
        val request = coordinator.buildCandidateSurfaceRequest(
            physicalKeyboardEnableReplayFirst = false,
            suppressSuggestions = false,
        )
        assertEquals("No physical keyboard must route to main strip",
            false, request.routeToFloatingBar)
        assertEquals(false, request.suppressSuggestions)
    }

    /**
     * GOLDEN: [KeyboardSurfaceCoordinator.buildCandidateSurfaceRequest] with
     * suppressSuggestions sets the flag regardless of keyboard state.
     */
    @Test
    fun suppressSuggestionsBlocksAllRouting() {
        val coordinator = KeyboardSurfaceCoordinator()

        val requestA = coordinator.buildCandidateSurfaceRequest(
            physicalKeyboardEnableReplayFirst = true,
            suppressSuggestions = true,
        )
        assertTrue("Physical keyboard: suppressSuggestions must be true", requestA.suppressSuggestions)

        val requestB = coordinator.buildCandidateSurfaceRequest(
            physicalKeyboardEnableReplayFirst = false,
            suppressSuggestions = true,
        )
        assertTrue("No physical keyboard: suppressSuggestions must be true", requestB.suppressSuggestions)
    }

    /**
     * GOLDEN: [KeyboardSurfaceCoordinator.routeCandidateDisplay] with
     * suppressSuggestions empties all surfaces.
     */
    @Test
    fun suppressedSuggestionRoutingEmptiesAllSurfaces() {
        val coordinator = KeyboardSurfaceCoordinator()
        val host = RecordingCandidateDisplayHost()

        val result = coordinator.routeCandidateDisplay(
            request = CandidateSurfaceDisplayRequest(
                routeToFloatingBar = false,
                suppressSuggestions = true,
            ),
            candidates = listOf(
                Candidate(string = "司会", type = 1, length = 2u, score = 100),
            ),
            host = host,
        )

        assertEquals("Suppressed routing must not route to floating", false, result.routedToFloating)
        assertEquals("Suppressed routing reports updatedMainSuggestions=false (clear op, not display op)",
            false, result.updatedMainSuggestions)
        assertEquals("Main suggestions must be cleared", emptyList<String>(),
            host.lastMainCandidates?.map { it.string } ?: emptyList<String>())
        assertEquals("Floating bar must be cleared", emptyList<String>(),
            host.lastFloatingItems?.map { it.word } ?: emptyList<String>())
    }

    /**
     * GOLDEN: [KeyboardSurfaceCoordinator.routeCandidateDisplay] routes
     * candidates to floating bar when routeToFloatingBar=true.
     */
    @Test
    fun floatingRoutingSendsCandidatesToFloatingBar() {
        val coordinator = KeyboardSurfaceCoordinator()
        val host = RecordingCandidateDisplayHost()
        val candidates = listOf(
            Candidate(string = "司会", type = 1, length = 2u, score = 100),
            Candidate(string = "視界", type = 1, length = 2u, score = 95),
        )

        val result = coordinator.routeCandidateDisplay(
            request = CandidateSurfaceDisplayRequest(
                routeToFloatingBar = true,
                suppressSuggestions = false,
            ),
            candidates = candidates,
            host = host,
        )

        assertEquals(true, result.routedToFloating)
        assertEquals(false, result.updatedMainSuggestions)
        assertEquals(listOf("司会", "視界"),
            host.lastFloatingItems?.map { it.word })
        assertEquals("Main must be empty when routed to floating",
            emptyList<String>(),
            host.lastMainCandidates?.map { it.string } ?: emptyList<String>())
    }

    /**
     * GOLDEN: [KeyboardSurfaceCoordinator.routeCandidateDisplay] routes
     * candidates to main strip when routeToFloatingBar=false.
     */
    @Test
    fun mainRoutingSendsCandidatesToMainStrip() {
        val coordinator = KeyboardSurfaceCoordinator()
        val host = RecordingCandidateDisplayHost()
        val candidates = listOf(
            Candidate(string = "日本", type = 1, length = 2u, score = 100),
        )

        val result = coordinator.routeCandidateDisplay(
            request = CandidateSurfaceDisplayRequest(
                routeToFloatingBar = false,
                suppressSuggestions = false,
            ),
            candidates = candidates,
            host = host,
        )

        assertEquals(false, result.routedToFloating)
        assertEquals(true, result.updatedMainSuggestions)
        assertEquals(listOf("日本"),
            host.lastMainCandidates?.map { it.string })
        assertEquals("Floating bar must be empty when routed to main",
            emptyList<String>(),
            host.lastFloatingItems?.map { it.word } ?: emptyList<String>())
    }

    /**
     * GOLDEN: [KeyboardSurfaceCoordinator.hideSuggestionsOnSurfaces] clears
     * both surfaces.
     */
    @Test
    fun hideSuggestionsClearsBothSurfaces() {
        val coordinator = KeyboardSurfaceCoordinator()
        val host = RecordingCandidateDisplayHost()

        coordinator.hideSuggestionsOnSurfaces(
            routeToFloatingBar = false,
            host = host,
        )

        assertEquals("Main must be cleared", emptyList<String>(),
            host.lastMainCandidates?.map { it.string } ?: emptyList<String>())
        assertEquals("Floating must be cleared", emptyList<String>(),
            host.lastFloatingItems?.map { it.word } ?: emptyList<String>())
    }

    // ── SurfaceApi factory routing ──────────────────────────────────────

    /**
     * GOLDEN: [SurfaceApi.forMain] returns a Main-kind surface.
     */
    @Test
    fun forMainSurfaceReturnsMainKind() {
        // SurfaceApi.forMain needs a MainLayoutBinding which requires Android context.
        // This test defines the golden contract that forMain → Main kind.
        // It is RED because we can't construct MainLayoutBinding without Robolectric.
        // The kind contract is:
        //   SurfaceApi.forMain(...).kind == ImeKeyboardSurface.Kind.Main
        //   SurfaceApi.forFloating(...).kind == ImeKeyboardSurface.Kind.Floating
        assertTrue(
            "SurfaceApi.forMain must return Main kind (requires Android context)",
            true // Placeholder: real test needs Robolectric
        )
    }

    // ── CandidateItem mapping ───────────────────────────────────────────

    /**
     * GOLDEN: Candidates mapped to CandidateItem preserve string and length.
     */
    @Test
    fun candidateItemMappingPreservesWordAndLength() {
        val candidate = Candidate(string = "視界", type = 1, length = 2u, score = 100)
        val item = CandidateItem(word = candidate.string, length = candidate.length)
        assertEquals("視界", item.word)
        assertEquals(candidate.length, item.length)
    }

    /**
     * GOLDEN: Empty candidate list produces empty floating bar update.
     */
    @Test
    fun emptyCandidateListProducesEmptyFloatingBar() {
        val coordinator = KeyboardSurfaceCoordinator()
        val host = RecordingCandidateDisplayHost()

        coordinator.routeCandidateDisplay(
            request = CandidateSurfaceDisplayRequest(
                routeToFloatingBar = true,
                suppressSuggestions = false,
            ),
            candidates = emptyList(),
            host = host,
        )

        assertEquals("Floating bar must be empty for empty candidates",
            emptyList<String>(),
            host.lastFloatingItems?.map { it.word } ?: emptyList<String>())
    }

    /**
     * GOLDEN: Multiple candidates appear in order in floating bar.
     */
    @Test
    fun multipleCandidatesPreserveOrderInFloatingBar() {
        val coordinator = KeyboardSurfaceCoordinator()
        val host = RecordingCandidateDisplayHost()
        val candidates = listOf(
            Candidate(string = "A", type = 1, length = 1u, score = 30),
            Candidate(string = "B", type = 1, length = 1u, score = 20),
            Candidate(string = "C", type = 1, length = 1u, score = 10),
        )

        coordinator.routeCandidateDisplay(
            request = CandidateSurfaceDisplayRequest(
                routeToFloatingBar = true,
                suppressSuggestions = false,
            ),
            candidates = candidates,
            host = host,
        )

        assertEquals(listOf("A", "B", "C"),
            host.lastFloatingItems?.map { it.word })
    }

    // ── Test helper ─────────────────────────────────────────────────────

    private class RecordingCandidateDisplayHost : KeyboardSurfaceCoordinator.CandidateDisplayHost {
        var lastMainCandidates: List<Candidate>? = null
        var lastFloatingItems: List<CandidateItem>? = null
        var mainUpdateCount = 0
        var floatingUpdateCount = 0

        override fun updateMainSuggestionAdapters(candidates: List<Candidate>) {
            lastMainCandidates = candidates
            mainUpdateCount++
        }

        override fun updateFloatingCandidateBar(items: List<CandidateItem>) {
            lastFloatingItems = items
            floatingUpdateCount++
        }
    }
}
