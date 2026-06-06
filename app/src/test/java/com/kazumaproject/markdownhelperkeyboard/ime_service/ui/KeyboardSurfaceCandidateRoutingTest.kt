package com.kazumaproject.markdownhelperkeyboard.ime_service.ui

import com.kazumaproject.core.data.floating_candidate.CandidateItem
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardSurfaceCandidateRoutingTest {
    private val coordinator = KeyboardSurfaceCoordinator()

    @Test
    fun routesToFloatingBarWhenPhysicalKeyboardEnabled() {
        var floatingUpdated = false
        var mainUpdated = false
        val result = coordinator.routeCandidateDisplay(
            request = coordinator.buildCandidateSurfaceRequest(
                physicalKeyboardEnableReplayFirst = true,
                suppressSuggestions = false,
            ),
            candidates = listOf(sampleCandidate("東京")),
            host = recordingHost(
                onFloating = { floatingUpdated = true },
                onMain = { mainUpdated = true },
            ),
        )
        assertTrue(result.routedToFloating)
        assertFalse(result.updatedMainSuggestions)
        assertTrue(floatingUpdated)
        assertFalse(mainUpdated)
    }

    @Test
    fun routesToMainBarWhenPhysicalKeyboardDisabled() {
        var floatingUpdated = false
        var mainUpdated = false
        val result = coordinator.routeCandidateDisplay(
            request = coordinator.buildCandidateSurfaceRequest(
                physicalKeyboardEnableReplayFirst = false,
                suppressSuggestions = false,
            ),
            candidates = listOf(sampleCandidate("大阪")),
            host = recordingHost(
                onFloating = { floatingUpdated = true },
                onMain = { mainUpdated = true },
            ),
        )
        assertFalse(result.routedToFloating)
        assertTrue(result.updatedMainSuggestions)
        assertFalse(floatingUpdated)
        assertTrue(mainUpdated)
    }

    @Test
    fun suppressSuggestionsSkipsBothSurfaces() {
        val result = coordinator.routeCandidateDisplay(
            request = coordinator.buildCandidateSurfaceRequest(
                physicalKeyboardEnableReplayFirst = true,
                suppressSuggestions = true,
            ),
            candidates = listOf(sampleCandidate("名古屋")),
            host = recordingHost(onFloating = {}, onMain = {}),
        )
        assertFalse(result.routedToFloating)
        assertFalse(result.updatedMainSuggestions)
    }

    private fun sampleCandidate(word: String) = Candidate(
        string = word,
        type = 1,
        length = word.length.toUByte(),
        score = 0,
    )

    private fun recordingHost(
        onFloating: () -> Unit,
        onMain: () -> Unit,
    ) = object : KeyboardSurfaceCoordinator.CandidateDisplayHost {
        override fun updateMainSuggestionAdapters(candidates: List<Candidate>) {
            onMain()
        }

        override fun updateFloatingCandidateBar(items: List<CandidateItem>) {
            onFloating()
        }
    }
}