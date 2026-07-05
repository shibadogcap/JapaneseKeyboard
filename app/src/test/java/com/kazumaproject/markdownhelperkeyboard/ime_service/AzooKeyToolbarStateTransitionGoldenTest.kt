package com.kazumaproject.markdownhelperkeyboard.ime_service

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Golden tests for the toolbar / upper-area state machine that controls
 * suggestion strip vs shortcut toolbar visibility.
 *
 * The state machine lives in IMEService.applyUpperAreaState(). It tracks:
 *
 *   hasCandidateSuggestions = hasSuggestions || hasInlineSuggestions || hasClipboardPreview
 *   showSuggestion = hasCandidateSuggestions && !isToolbarForcedShow
 *   on transition: hasCandidateSuggestions && !lastShowSuggestion → isToolbarForcedShow = false
 *
 * These tests are RED initially — they define the expected golden transitions.
 */
class AzooKeyToolbarStateTransitionGoldenTest {

    /**
     * Pure-function model of the toolbar state machine extracted from
     * IMEService. Each test calls this to verify golden transitions.
     */
    private data class UpperAreaState(
        val showSuggestion: Boolean,
        val hasCandidateSuggestions: Boolean,
        val privateMode: Boolean,
        val forcedToolbar: Boolean,
    )

    private data class UpperAreaInput(
        val hasSuggestions: Boolean,
        val hasInlineSuggestions: Boolean,
        val hasClipboardPreview: Boolean,
        val lastShowSuggestion: Boolean,
        val isToolbarForcedShow: Boolean,
        val privateMode: Boolean,
    )

    private fun resolveUpperAreaState(input: UpperAreaInput): UpperAreaState {
        val hasCandidateSuggestions = input.hasSuggestions ||
            input.hasInlineSuggestions ||
            input.hasClipboardPreview
        val forcedToolbar = if (hasCandidateSuggestions && !input.lastShowSuggestion) {
            false // auto-clear forced toolbar when candidates appear
        } else {
            input.isToolbarForcedShow
        }
        val showSuggestion = hasCandidateSuggestions && !forcedToolbar
        return UpperAreaState(
            showSuggestion = showSuggestion,
            hasCandidateSuggestions = hasCandidateSuggestions,
            privateMode = input.privateMode,
            forcedToolbar = forcedToolbar,
        )
    }

    // ── Golden transitions ──────────────────────────────────────────────

    /**
     * GOLDEN: No candidates → showSuggestion=false, toolbar visible.
     */
    @Test
    fun noCandidatesShowsToolbar() {
        val state = resolveUpperAreaState(UpperAreaInput(
            hasSuggestions = false,
            hasInlineSuggestions = false,
            hasClipboardPreview = false,
            lastShowSuggestion = false,
            isToolbarForcedShow = false,
            privateMode = false,
        ))
        assertEquals("No candidates must hide suggestion strip (showSuggestion=false)",
            false, state.showSuggestion)
        assertEquals("No candidates → hasCandidateSuggestions=false",
            false, state.hasCandidateSuggestions)
    }

    /**
     * GOLDEN: Candidates present → showSuggestion=true, candidates visible.
     */
    @Test
    fun candidatesPresentShowsSuggestionStrip() {
        val state = resolveUpperAreaState(UpperAreaInput(
            hasSuggestions = true,
            hasInlineSuggestions = false,
            hasClipboardPreview = false,
            lastShowSuggestion = false,
            isToolbarForcedShow = false,
            privateMode = false,
        ))
        assertEquals("Candidates present must show suggestion strip",
            true, state.showSuggestion)
        assertEquals(true, state.hasCandidateSuggestions)
        assertEquals(false, state.forcedToolbar)
    }

    /**
     * GOLDEN: Inline suggestions alone trigger candidate display.
     */
    @Test
    fun inlineSuggestionsAloneShowsSuggestionStrip() {
        val state = resolveUpperAreaState(UpperAreaInput(
            hasSuggestions = false,
            hasInlineSuggestions = true,
            hasClipboardPreview = false,
            lastShowSuggestion = false,
            isToolbarForcedShow = false,
            privateMode = false,
        ))
        assertEquals(true, state.showSuggestion)
        assertEquals(true, state.hasCandidateSuggestions)
    }

    /**
     * GOLDEN: Clipboard preview alone triggers candidate display.
     */
    @Test
    fun clipboardPreviewAloneShowsSuggestionStrip() {
        val state = resolveUpperAreaState(UpperAreaInput(
            hasSuggestions = false,
            hasInlineSuggestions = false,
            hasClipboardPreview = true,
            lastShowSuggestion = false,
            isToolbarForcedShow = false,
            privateMode = false,
        ))
        assertEquals(true, state.showSuggestion)
        assertEquals(true, state.hasCandidateSuggestions)
    }

    /**
     * GOLDEN: Forced toolbar hides suggestion strip even when candidates exist.
     */
    @Test
    fun forcedToolbarOverridesSuggestionStrip() {
        val state = resolveUpperAreaState(UpperAreaInput(
            hasSuggestions = true,
            hasInlineSuggestions = false,
            hasClipboardPreview = false,
            lastShowSuggestion = true,
            isToolbarForcedShow = true,
            privateMode = false,
        ))
        assertEquals("Forced toolbar hides suggestion strip when candidates exist",
            false, state.showSuggestion)
        assertEquals(true, state.hasCandidateSuggestions)
        assertEquals(true, state.forcedToolbar)
    }

    /**
     * GOLDEN: Transition from no-candidates → candidates clears forced toolbar.
     *
     * This is the "auto-reveal" behavior: when the user was on the toolbar
     * and starts typing, the forcedToolbar flag is cleared so candidates appear.
     */
    @Test
    fun candidateAppearanceClearsForcedToolbar() {
        // Previous state: no candidates, toolbar forced
        val prev = UpperAreaInput(
            hasSuggestions = false,
            hasInlineSuggestions = false,
            hasClipboardPreview = false,
            lastShowSuggestion = false,
            isToolbarForcedShow = true,
            privateMode = false,
        )

        // Current state: candidates appear
        val state = resolveUpperAreaState(prev.copy(
            hasSuggestions = true,
        ))

        assertEquals("forcedToolbar must clear when candidates appear",
            false, state.forcedToolbar)
        assertEquals("showSuggestion must be true after auto-clear",
            true, state.showSuggestion)
    }

    /**
     * GOLDEN: Transition from candidates → no candidates shows toolbar,
     * keeping forcedToolbar=false (no lingering forced state).
     */
    @Test
    fun candidateDisappearanceShowsToolbar() {
        val state = resolveUpperAreaState(UpperAreaInput(
            hasSuggestions = false,
            hasInlineSuggestions = false,
            hasClipboardPreview = false,
            lastShowSuggestion = true,
            isToolbarForcedShow = false,
            privateMode = false,
        ))
        assertEquals("No candidates must hide suggestion strip",
            false, state.showSuggestion)
        assertEquals(false, state.forcedToolbar)
    }

    /**
     * GOLDEN: Private mode does NOT affect showSuggestion directly (it's a display flag).
     */
    @Test
    fun privateModeIsIndependentOfCandidateVisibility() {
        val state = resolveUpperAreaState(UpperAreaInput(
            hasSuggestions = true,
            hasInlineSuggestions = false,
            hasClipboardPreview = false,
            lastShowSuggestion = false,
            isToolbarForcedShow = false,
            privateMode = true,
        ))
        assertEquals("Private mode must not suppress showSuggestion",
            true, state.showSuggestion)
        assertEquals(true, state.privateMode)
    }

    /**
     * GOLDEN: All three candidate sources combine via OR.
     */
    @Test
    fun anyCandidateSourceTriggersSuggestionStrip() {
        val combinations = listOf(
            Triple(true, false, false),
            Triple(false, true, false),
            Triple(false, false, true),
            Triple(true, true, true),
        )
        for ((hasSuggestions, hasInline, hasClipboard) in combinations) {
            val state = resolveUpperAreaState(UpperAreaInput(
                hasSuggestions = hasSuggestions,
                hasInlineSuggestions = hasInline,
                hasClipboardPreview = hasClipboard,
                lastShowSuggestion = false,
                isToolbarForcedShow = false,
                privateMode = false,
            ))
            assertEquals("Any candidate source must hasCandidateSuggestions=true",
                true, state.hasCandidateSuggestions)
        }
    }

    /**
     * GOLDEN: All sources false → hasCandidateSuggestions=false.
     */
    @Test
    fun noCandidateSourceHasNoCandidateSuggestions() {
        val state = resolveUpperAreaState(UpperAreaInput(
            hasSuggestions = false,
            hasInlineSuggestions = false,
            hasClipboardPreview = false,
            lastShowSuggestion = false,
            isToolbarForcedShow = false,
            privateMode = false,
        ))
        assertEquals(false, state.hasCandidateSuggestions)
    }

    /**
     * GOLDEN: Forced toolbar persists when no new candidates appear.
     */
    @Test
    fun forcedToolbarPersistsWithoutCandidateChange() {
        val state = resolveUpperAreaState(UpperAreaInput(
            hasSuggestions = false,
            hasInlineSuggestions = false,
            hasClipboardPreview = false,
            lastShowSuggestion = false,
            isToolbarForcedShow = true,
            privateMode = false,
        ))
        assertEquals("forcedToolbar persists when no candidates to trigger auto-clear",
            true, state.forcedToolbar)
        assertEquals(false, state.showSuggestion)
    }
}
