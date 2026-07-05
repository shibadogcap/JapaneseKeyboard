package com.kazumaproject.markdownhelperkeyboard.ime_service.ui

import android.graphics.drawable.Drawable
import com.kazumaproject.core.data.floating_candidate.CandidateItem
import com.kazumaproject.core.domain.state.InputMode
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate

import com.kazumaproject.markdownhelperkeyboard.databinding.FloatingKeyboardLayoutBinding
import com.kazumaproject.markdownhelperkeyboard.databinding.MainLayoutBinding
import com.kazumaproject.tabletkey.TabletKeyboardView
import com.kazumaproject.tenkey.TenKey
import com.kazumaproject.tenkey.extensions.isHiragana
import com.kazumaproject.tenkey.extensions.isLatinAlphabet

/**
 * 通常 / フローティング / タブレット表面の UI 差分を吸収する（IME Phase 4a/4b）。
 * tap/flick 本体は [TapFlickInputBridge]、候補表示振り分けは [routeCandidateDisplay]。
 */
class KeyboardSurfaceCoordinator {

    interface CandidateDisplayHost {
        fun updateMainSuggestionAdapters(candidates: List<Candidate>)
        fun updateFloatingCandidateBar(items: List<CandidateItem>)
    }

    fun routeCandidateDisplay(
        request: CandidateSurfaceDisplayRequest,
        candidates: List<Candidate>,
        host: CandidateDisplayHost,
    ): CandidateSurfaceDisplayResult {
        if (request.suppressSuggestions) {
            return CandidateSurfaceDisplayResult(
                routedToFloating = false,
                updatedMainSuggestions = false,
            )
        }
        return if (request.routeToFloatingBar) {
            host.updateFloatingCandidateBar(
                candidates.map { CandidateItem(word = it.string, length = it.length) },
            )
            CandidateSurfaceDisplayResult(routedToFloating = true, updatedMainSuggestions = false)
        } else {
            host.updateMainSuggestionAdapters(candidates)
            CandidateSurfaceDisplayResult(routedToFloating = false, updatedMainSuggestions = true)
        }
    }

    fun buildCandidateSurfaceRequest(
        physicalKeyboardEnableReplayFirst: Boolean?,
        suppressSuggestions: Boolean,
    ): CandidateSurfaceDisplayRequest {
        return CandidateSurfaceDisplayRequest(
            routeToFloatingBar = physicalKeyboardEnableReplayFirst == true,
            suppressSuggestions = suppressSuggestions,
        )
    }

    fun hideSuggestionsOnSurfaces(
        routeToFloatingBar: Boolean,
        host: CandidateDisplayHost,
    ) {
        if (routeToFloatingBar) {
            host.updateFloatingCandidateBar(emptyList())
        } else {
            host.updateMainSuggestionAdapters(emptyList())
        }
    }

    data class HenkanDrawableSet(
        val returnDrawable: Drawable?,
        val henkanDrawable: Drawable?,
        val spaceDrawable: Drawable?,
        val kanaDrawable: Drawable?,
        val englishDrawable: Drawable?,
        val numberDrawable: Drawable?,
        val tenkeyShowImeButton: Boolean,
    )

    fun updateHenkanOnMain(
        mainView: MainLayoutBinding,
        insertString: String,
        currentInputMode: InputMode,
        drawables: HenkanDrawableSet,
        useTabletGojuonSurface: Boolean,
    ) {
        if (useTabletGojuonSurface) {
            applyTabletHenkanSideKeys(
                target = TabletHenkanSideKeyTarget(mainView.tabletView),
                currentInputMode = currentInputMode,
                drawables = drawables,
            )
        } else {
            applyHenkanSideKeys(
                target = TenKeyHenkanSideKeyTarget(mainView.keyboardView),
                insertString = insertString,
                currentInputMode = currentInputMode,
                drawables = drawables,
            )
        }
    }

    /** 単体 TenKey 表面向け（テスト・直接呼び出し用）。 */
    fun updateHenkanOnTenKey(
        tenKey: TenKey,
        insertString: String,
        currentInputMode: InputMode,
        drawables: HenkanDrawableSet,
    ) {
        applyHenkanSideKeys(
            target = TenKeyHenkanSideKeyTarget(tenKey),
            insertString = insertString,
            currentInputMode = currentInputMode,
            drawables = drawables,
        )
    }

    fun updateHenkanOnFloating(
        floatingBinding: FloatingKeyboardLayoutBinding,
        insertString: String,
        currentInputMode: InputMode,
        drawables: HenkanDrawableSet,
    ) {
        applyHenkanSideKeys(
            target = TenKeyHenkanSideKeyTarget(floatingBinding.keyboardViewFloating),
            insertString = insertString,
            currentInputMode = currentInputMode,
            drawables = drawables,
        )
    }

    fun updateHenkanBoth(
        mainView: MainLayoutBinding,
        floatingBinding: FloatingKeyboardLayoutBinding?,
        insertString: String,
        currentInputMode: InputMode,
        drawables: HenkanDrawableSet,
        useTabletGojuonSurface: Boolean,
    ) {
        updateHenkanOnMain(
            mainView = mainView,
            insertString = insertString,
            currentInputMode = currentInputMode,
            drawables = drawables,
            useTabletGojuonSurface = useTabletGojuonSurface,
        )
        floatingBinding?.let {
            updateHenkanOnFloating(
                floatingBinding = it,
                insertString = insertString,
                currentInputMode = currentInputMode,
                drawables = drawables,
            )
        }
    }

    private interface HenkanSideKeyTarget {
        fun setSideKeyEnterDrawable(drawable: Drawable?)
        fun setSideKeySpaceDrawable(drawable: Drawable?)
        fun setBackgroundSmallLetterKey(drawable: Drawable?)
        fun setBackgroundSmallLetterKey(isLanguageEnable: Boolean, isEnglish: Boolean)
    }

    private class TenKeyHenkanSideKeyTarget(
        private val tenKey: TenKey,
    ) : HenkanSideKeyTarget {
        override fun setSideKeyEnterDrawable(drawable: Drawable?) = tenKey.setSideKeyEnterDrawable(drawable)
        override fun setSideKeySpaceDrawable(drawable: Drawable?) = tenKey.setSideKeySpaceDrawable(drawable)
        override fun setBackgroundSmallLetterKey(drawable: Drawable?) =
            tenKey.setBackgroundSmallLetterKey(drawable)
        override fun setBackgroundSmallLetterKey(isLanguageEnable: Boolean, isEnglish: Boolean) =
            tenKey.setBackgroundSmallLetterKey(isLanguageEnable, isEnglish)
    }

    private class TabletHenkanSideKeyTarget(
        private val tablet: TabletKeyboardView,
    ) : HenkanSideKeyTarget {
        override fun setSideKeyEnterDrawable(drawable: Drawable?) = tablet.setSideKeyEnterDrawable(drawable)
        override fun setSideKeySpaceDrawable(drawable: Drawable?) = tablet.setSideKeySpaceDrawable(drawable)
        override fun setBackgroundSmallLetterKey(drawable: Drawable?) = Unit
        override fun setBackgroundSmallLetterKey(isLanguageEnable: Boolean, isEnglish: Boolean) = Unit
    }

    private fun applyHenkanSideKeys(
        target: HenkanSideKeyTarget,
        insertString: String,
        currentInputMode: InputMode,
        drawables: HenkanDrawableSet,
    ) {
        target.setSideKeyEnterDrawable(drawables.returnDrawable)
        when (currentInputMode) {
            InputMode.ModeJapanese -> applyJapaneseHenkanSideKeys(target, insertString, drawables)
            InputMode.ModeEnglish -> applyEnglishHenkanSideKeys(target, insertString, drawables)
            InputMode.ModeNumber -> applyNumberHenkanSideKeys(target, drawables)
        }
    }

    private fun applyJapaneseHenkanSideKeys(
        target: HenkanSideKeyTarget,
        insertString: String,
        drawables: HenkanDrawableSet,
    ) {
        if (insertString.isNotEmpty() && insertString.last().isHiragana()) {
            target.setBackgroundSmallLetterKey(drawables.kanaDrawable)
        } else {
            target.setBackgroundSmallLetterKey(
                isLanguageEnable = drawables.tenkeyShowImeButton,
                isEnglish = false,
            )
        }
        target.setSideKeySpaceDrawable(drawables.henkanDrawable)
    }

    private fun applyEnglishHenkanSideKeys(
        target: HenkanSideKeyTarget,
        insertString: String,
        drawables: HenkanDrawableSet,
    ) {
        if (insertString.isNotEmpty() && insertString.last().isLatinAlphabet()) {
            target.setBackgroundSmallLetterKey(drawables.englishDrawable)
        } else {
            target.setBackgroundSmallLetterKey(
                isLanguageEnable = drawables.tenkeyShowImeButton,
                isEnglish = true,
            )
        }
        target.setSideKeySpaceDrawable(drawables.spaceDrawable)
    }

    private fun applyNumberHenkanSideKeys(
        target: HenkanSideKeyTarget,
        drawables: HenkanDrawableSet,
    ) {
        target.setBackgroundSmallLetterKey(drawables.numberDrawable)
        target.setSideKeySpaceDrawable(drawables.spaceDrawable)
    }

    /** タブレット五十字は変換中でも小文字キー背景を切り替えない（従来 [updateUIinHenkan] 互換）。 */
    private fun applyTabletHenkanSideKeys(
        target: HenkanSideKeyTarget,
        currentInputMode: InputMode,
        drawables: HenkanDrawableSet,
    ) {
        target.setSideKeyEnterDrawable(drawables.returnDrawable)
        when (currentInputMode) {
            InputMode.ModeJapanese -> target.setSideKeySpaceDrawable(drawables.henkanDrawable)
            InputMode.ModeEnglish,
            InputMode.ModeNumber,
            -> target.setSideKeySpaceDrawable(drawables.spaceDrawable)
        }
    }
}