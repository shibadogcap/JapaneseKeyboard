package com.kazumaproject.markdownhelperkeyboard.ime_service.ui

import android.graphics.drawable.Drawable
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.kazumaproject.core.domain.state.InputMode
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.databinding.FloatingKeyboardLayoutBinding
import com.kazumaproject.markdownhelperkeyboard.databinding.MainLayoutBinding

interface SurfaceApi {
    val kind: ImeKeyboardSurface.Kind
    val suggestionRecyclerView: RecyclerView
    val isTabletSurface: Boolean

    fun currentInputMode(): InputMode

    fun applyConvertingLetter(
        suggestions: List<Candidate>,
        isSpaceKey: Boolean,
        insertString: String,
        suggestionClickNum: Int,
        setSuggestionComposingText: (List<Candidate>, String) -> Unit,
    )

    fun setSideKeyEnterDrawable(drawable: Drawable?)

    fun updateUpperAreaVisibility()

    fun setCandidateTabLayoutVisibility(visible: Boolean)

    fun selectCandidateTab(index: Int)

    companion object {
        fun forMain(mainView: MainLayoutBinding): SurfaceApi = MainSurfaceApi(mainView)
        fun forFloating(binding: FloatingKeyboardLayoutBinding): SurfaceApi =
            FloatingSurfaceApi(binding)
    }
}

private class MainSurfaceApi(
    private val mainView: MainLayoutBinding,
) : SurfaceApi {
    override val kind: ImeKeyboardSurface.Kind = ImeKeyboardSurface.Kind.Main
    override val suggestionRecyclerView: RecyclerView
        get() = mainView.suggestionRecyclerView
    override val isTabletSurface: Boolean
        get() = mainView.tabletView.isVisible

    override fun currentInputMode(): InputMode {
        return if (mainView.tabletView.isVisible) {
            mainView.tabletView.currentInputMode.get()
        } else {
            mainView.keyboardView.currentInputMode.value
        }
    }

    override fun applyConvertingLetter(
        suggestions: List<Candidate>,
        isSpaceKey: Boolean,
        insertString: String,
        suggestionClickNum: Int,
        setSuggestionComposingText: (List<Candidate>, String) -> Unit,
    ) {
        val listIterator = suggestions.listIterator((suggestionClickNum - 1).coerceAtLeast(0))
        when {
            !listIterator.hasPrevious() -> {
                setSuggestionComposingText(suggestions, insertString)
                mainView.suggestionRecyclerView.smoothScrollToPosition(0)
            }
            listIterator.hasNext() -> {
                setSuggestionComposingText(suggestions, insertString)
            }
        }
    }

    override fun setSideKeyEnterDrawable(drawable: Drawable?) {
        if (mainView.tabletView.isVisible) {
            mainView.tabletView.setSideKeyEnterDrawable(drawable)
        } else {
            mainView.keyboardView.setSideKeyEnterDrawable(drawable)
        }
    }

    override fun updateUpperAreaVisibility() {}

    override fun setCandidateTabLayoutVisibility(visible: Boolean) {
        mainView.candidateTabLayout.isVisible = visible
    }

    override fun selectCandidateTab(index: Int) {
        val tab = mainView.candidateTabLayout.getTabAt(index)
        tab?.let { mainView.candidateTabLayout.selectTab(it) }
    }
}

private class FloatingSurfaceApi(
    private val binding: FloatingKeyboardLayoutBinding,
) : SurfaceApi {
    override val kind: ImeKeyboardSurface.Kind = ImeKeyboardSurface.Kind.Floating
    override val suggestionRecyclerView: RecyclerView
        get() = binding.suggestionRecyclerView
    override val isTabletSurface: Boolean = false

    override fun currentInputMode(): InputMode {
        return binding.keyboardViewFloating.currentInputMode.value
    }

    override fun applyConvertingLetter(
        suggestions: List<Candidate>,
        isSpaceKey: Boolean,
        insertString: String,
        suggestionClickNum: Int,
        setSuggestionComposingText: (List<Candidate>, String) -> Unit,
    ) {
        val listIterator = suggestions.listIterator((suggestionClickNum - 1).coerceAtLeast(0))
        when {
            !listIterator.hasPrevious() -> {
                setSuggestionComposingText(suggestions, insertString)
                binding.suggestionRecyclerView.smoothScrollToPosition(0)
            }
            listIterator.hasNext() -> {
                setSuggestionComposingText(suggestions, insertString)
            }
        }
    }

    override fun setSideKeyEnterDrawable(drawable: Drawable?) {
        binding.keyboardViewFloating.setSideKeyEnterDrawable(drawable)
    }

    override fun updateUpperAreaVisibility() {}

    override fun setCandidateTabLayoutVisibility(visible: Boolean) {}

    override fun selectCandidateTab(index: Int) {}
}
