package com.kazumaproject.markdownhelperkeyboard.ime_service.adapters

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.text.SpannableString
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Gravity
import android.widget.ImageView
import android.widget.FrameLayout
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.util.AttributeSet
import android.util.Size
import android.view.SurfaceView
import android.view.ViewTreeObserver
import android.widget.inline.InlineContentView
import android.view.inputmethod.InlineSuggestion
import androidx.appcompat.widget.AppCompatImageButton
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.AsyncListDiffer
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.DynamicColors
import com.google.android.material.textview.MaterialTextView
import com.kazumaproject.core.domain.extensions.isAllFullWidthNumericSymbol
import com.kazumaproject.core.domain.extensions.isAllHalfWidthNumericSymbol
import com.kazumaproject.core.domain.extensions.isDarkThemeOn
import com.kazumaproject.core.domain.extensions.setDrawableSolidColor
import com.kazumaproject.core.domain.state.TenKeyQWERTYMode
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.QWERTY_GLIDE_CANDIDATE_TYPE
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.CustomKeyboardLayout
import com.kazumaproject.markdownhelperkeyboard.gemma.GemmaTranslationManager
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.correctReading
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.debugPrintCodePoints
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import timber.log.Timber

internal class CandidateItemColorState {
    var backgroundColor: Int? = null
        private set
    var pressedBackgroundColor: Int? = null
        private set

    fun setBackgroundColor(color: Int): Boolean {
        if (backgroundColor == color) return false
        backgroundColor = color
        return true
    }

    fun setPressedBackgroundColor(color: Int): Boolean {
        if (pressedBackgroundColor == color) return false
        pressedBackgroundColor = color
        return true
    }

    fun setColors(backgroundColor: Int, pressedBackgroundColor: Int): Boolean {
        if (
            this.backgroundColor == backgroundColor &&
            this.pressedBackgroundColor == pressedBackgroundColor
        ) {
            return false
        }
        this.backgroundColor = backgroundColor
        this.pressedBackgroundColor = pressedBackgroundColor
        return true
    }
}

internal data class CandidateYomiPresentation(
    val isVisible: Boolean,
    val text: String,
    val textSize: Float
)

internal fun resolveCandidateYomiPresentation(
    showCandidateYomiForLiveConversion: Boolean,
    isFirstCandidate: Boolean,
    suggestion: Candidate,
    candidateTextSize: Float
): CandidateYomiPresentation {
    val yomi = suggestion.yomi
    val shouldShowYomi =
        showCandidateYomiForLiveConversion &&
            isFirstCandidate &&
            !yomi.isNullOrBlank() &&
            yomi != suggestion.string
    return CandidateYomiPresentation(
        isVisible = shouldShowYomi,
        text = if (shouldShowYomi) yomi.orEmpty() else "",
        textSize = candidateTextSize * 0.72f
    )
}

class SuggestionAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        const val VIEW_TYPE_EMPTY = 0
        const val VIEW_TYPE_SUGGESTION = 1
        const val VIEW_TYPE_CUSTOM_LAYOUT_PICKER = 2
        const val VIEW_TYPE_GEMMA_ACTION = 3
        const val VIEW_TYPE_INLINE_SUGGESTION = 4
    }

    enum class HelperIcon {
        UNDO, REDO, RECONVERT, PASTE
    }

    // Listeners for clicks
    private var onItemClickListener: ((Candidate, Int) -> Unit)? = null
    private var onItemLongClickListener: ((Candidate, Int) -> Unit)? = null
    private var onItemHelperIconClickListener: ((HelperIcon) -> Unit)? = null
    private var onItemHelperIconLongClickListener: ((HelperIcon) -> Unit)? = null
    private var onCustomLayoutItemClickListener: ((Int) -> Unit)? = null
    private var onShowSoftKeyboardClick: (() -> Unit)? = null

    private val adapterScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    var onListUpdated: (() -> Unit)? = null
    var onClipboardUpdated: (() -> Unit)? = null

    // Holds the preview content for the empty state.
    private var clipboardText: String = ""
    private var clipboardBitmap: Bitmap? = null // ★追加: Bitmapを保持するフィールド
    private var undoText: String = ""
    private var redoText: String = ""
    private var isReconvertEnabled: Boolean = false

    // Internal flags to track enable/disable state
    private var isUndoEnabled: Boolean = false
    private var isRedoEnabled: Boolean = false
    private var isPasteEnabled: Boolean = true
    private var isClipboardDescriptionShow: Boolean = true

    private var currentMode: TenKeyQWERTYMode = TenKeyQWERTYMode.Default
    private var customLayouts: List<CustomKeyboardLayout> = emptyList()

    private var showCustomTab: Boolean = true

    private var incognitoIconDrawable: android.graphics.drawable.Drawable? = null
    private var customTypeface: android.graphics.Typeface? = null

    private var inlineSuggestions: List<InlineSuggestion> = emptyList()

    private val inlineCount: Int
        get() = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            inlineSuggestions.size
        } else {
            0
        }

    fun setInlineSuggestions(suggestions: List<InlineSuggestion>) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            if (this.inlineSuggestions == suggestions) return
            val previousCount = inlineCount
            this.inlineSuggestions = suggestions
            val newCount = inlineCount
            when {
                previousCount == 0 && newCount > 0 -> notifyItemRangeInserted(0, newCount)
                previousCount > 0 && newCount == 0 -> notifyItemRangeRemoved(0, previousCount)
                previousCount == newCount -> notifyItemRangeChanged(0, newCount)
                else -> {
                    notifyItemRangeRemoved(0, previousCount)
                    notifyItemRangeInserted(0, newCount)
                }
            }
            onListUpdated?.invoke()
        }
    }

    fun hasInlineSuggestions(): Boolean {
        return inlineSuggestions.isNotEmpty()
    }

    fun setCustomTypeface(typeface: android.graphics.Typeface?) {
        this.customTypeface = typeface
        notifyItemRangeChanged(0, itemCount)
    }

    private var candidateTextSize: Float = 14f
    private var candidateTextColor: Int? = null
    private var showCandidateYomiForLiveConversion: Boolean = false
    private val candidateItemColorState = CandidateItemColorState()

    private var candidateEmptyDrawableColor: Int? = null
    private var candidateEmptyDrawableTextColor: Int? = null

    fun setOnItemClickListener(onItemClick: (Candidate, Int) -> Unit) {
        this.onItemClickListener = onItemClick
    }

    fun setOnItemLongClickListener(onItemLongClick: (Candidate, Int) -> Unit) {
        this.onItemLongClickListener = onItemLongClick
    }

    fun setOnItemHelperIconClickListener(onItemHelperIconClickListener: (HelperIcon) -> Unit) {
        this.onItemHelperIconClickListener = onItemHelperIconClickListener
    }

    fun setOnItemHelperIconLongClickListener(onItemHelperIconLongClickListener: (HelperIcon) -> Unit) {
        this.onItemHelperIconLongClickListener = onItemHelperIconLongClickListener
    }

    fun setOnCustomLayoutItemClickListener(listener: (Int) -> Unit) {
        this.onCustomLayoutItemClickListener = listener
    }

    fun setOnPhysicalKeyboardListener(listener: () -> Unit) {
        this.onShowSoftKeyboardClick = listener
    }

    fun release() {
        onItemClickListener = null
        onItemLongClickListener = null
        onItemHelperIconClickListener = null
        onItemHelperIconLongClickListener = null
        onCustomLayoutItemClickListener = null
        onShowSoftKeyboardClick = null
        onListUpdated = null
        onClipboardUpdated = null
        incognitoIconDrawable = null
        adapterScope.cancel()
    }

    /**
     * ★新しい関数: シークレットモードのアイコンを設定します。
     * Drawableがnullでなければアイコンを表示し、nullなら非表示にします。
     */
    fun setIncognitoIcon(drawable: android.graphics.drawable.Drawable?) {
        this.incognitoIconDrawable = drawable
        if (suggestions.isEmpty()) {
            notifyItemChanged(0)
        }
    }

    fun setUndoEnabled(enabled: Boolean) {
        isUndoEnabled = enabled
        if (suggestions.isEmpty()) {
            notifyItemChanged(0)
        }
    }

    fun setPasteEnabled(enabled: Boolean) {
        isPasteEnabled = enabled
        if (suggestions.isEmpty()) {
            notifyItemChanged(0)
        }
    }

    fun setRedoEnabled(enabled: Boolean) {
        isRedoEnabled = enabled
        if (suggestions.isEmpty()) {
            notifyItemChanged(0)
        }
    }

    fun setReconvertEnabled(enabled: Boolean) {
        isReconvertEnabled = enabled
        if (suggestions.isEmpty()) {
            notifyItemChanged(0)
        }
    }

    fun setClipboardDescriptionTextVisibility(visibility: Boolean) {
        isClipboardDescriptionShow = visibility
        if (suggestions.isEmpty()) {
            notifyItemChanged(0)
        }
    }

    /**
     * テキストのクリップボードプレビューを設定します。
     * このとき、画像のプレビューはクリアされます。
     */
    fun setClipboardPreview(text: String) {
        clipboardText = text
        clipboardBitmap = null // ★追加: テキスト設定時に画像はクリア
        if (suggestions.isEmpty()) {
            notifyItemChanged(0)
        }
        onClipboardUpdated?.invoke()
    }

    /**
     * ★新しい関数: 画像のクリップボードプレビューを設定します。
     * このとき、テキストのプレビューはクリアされます。
     */
    fun setClipboardImagePreview(bitmap: Bitmap?) {
        clipboardBitmap = bitmap
        clipboardText = "" // 画像設定時にテキストはクリア
        if (suggestions.isEmpty()) {
            notifyItemChanged(0)
        }
        onClipboardUpdated?.invoke()
    }

    fun hasClipboardPreview(): Boolean {
        return clipboardText.isNotEmpty() || clipboardBitmap != null
    }



    fun setUndoPreviewText(text: String) {
        undoText = text
        if (suggestions.isEmpty()) {
            notifyItemChanged(0)
        }
    }

    fun setRedoPreviewText(text: String) {
        redoText = text
        if (suggestions.isEmpty()) {
            notifyItemChanged(0)
        }
    }

    fun updateState(mode: TenKeyQWERTYMode, layouts: List<CustomKeyboardLayout>) {
        val needsFullRefresh = (currentMode != mode) || (customLayouts != layouts)
        currentMode = mode
        customLayouts = layouts
        if (needsFullRefresh) {
            // Custom layout tabs are backed by a different item list from candidate suggestions.
            // Layout add/remove/reorder can change item count and view types at the same time, so
            // targeted notifyItemChanged calls are unsafe here. Keep this boundary explicit so the
            // tab list can be moved to a stableId-based DiffUtil/ListAdapter model later.
            notifyDataSetChanged()
        }
    }

    fun updateCustomTabVisibility(visibility: Boolean) {
        if (showCustomTab == visibility) return
        showCustomTab = visibility
        if (currentMode is TenKeyQWERTYMode.Custom) {
            notifyDataSetChanged()
        }
    }

    private val diffCallback = object : DiffUtil.ItemCallback<Candidate>() {
        override fun areItemsTheSame(oldItem: Candidate, newItem: Candidate): Boolean {
            return oldItem.string == newItem.string
        }

        override fun areContentsTheSame(oldItem: Candidate, newItem: Candidate): Boolean {
            return oldItem == newItem
        }
    }
    private val differ = AsyncListDiffer(this, diffCallback)

    var suggestions: List<Candidate>
        get() = differ.currentList
        set(value) {
            // submitListの第2引数にコールバックを渡す
            differ.submitList(value) {
                onListUpdated?.invoke()
            }
        }

    private var highlightedPosition: Int = RecyclerView.NO_POSITION

    inner class SuggestionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val text: MaterialTextView = itemView.findViewById(R.id.suggestion_item_text_view)
        val yomiText: MaterialTextView = itemView.findViewById(R.id.suggestion_item_yomi_text_view)
        val typeText: MaterialTextView = itemView.findViewById(R.id.suggestion_item_type_text_view)
    }

    inner class GemmaActionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val badgeText: MaterialTextView = itemView.findViewById(R.id.suggestion_gemma_action_badge)
        val actionText: MaterialTextView = itemView.findViewById(R.id.suggestion_gemma_action_text)
    }

    inner class EmptyViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val undoIconParent: ConstraintLayout? = itemView.findViewById(R.id.undo_icon_parent)
        val undoIcon: MaterialTextView? = itemView.findViewById(R.id.undo_icon)
        val redoIconParent: ConstraintLayout? = itemView.findViewById(R.id.redo_icon_parent)
        val redoIcon: MaterialTextView? = itemView.findViewById(R.id.redo_icon)
        val reconvertIconParent: ConstraintLayout? = itemView.findViewById(R.id.reconvert_icon_parent)
        val reconvertIcon: MaterialTextView? = itemView.findViewById(R.id.reconvert_icon)
        val reconvertImageView: ImageView? = itemView.findViewById(R.id.reconvert_image_view)
        val pasteIconParent: ConstraintLayout? = itemView.findViewById(R.id.paste_icon_patent)
        val pasteIcon: ImageView? = itemView.findViewById(R.id.paste_icon)
        val clipboardPreviewText: MaterialTextView? =
            itemView.findViewById(R.id.clipboard_text_preview)
        val clipboardPreviewTextDescription: MaterialTextView? =
            itemView.findViewById(R.id.clipboard_preview_text_description)
        val incognitoIcon: AppCompatImageButton? = itemView.findViewById(R.id.incognito_icon)
    }

    inner class CustomLayoutViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val nameTextView: MaterialTextView = itemView.findViewById(R.id.custom_layout_name)
    }

    inner class InlineSuggestionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val container: FrameLayout = (itemView as? InlineSuggestionClipContainer)?.getContainer() ?: (itemView as FrameLayout)
        var currentSuggestion: InlineSuggestion? = null
    }

    override fun getItemViewType(position: Int): Int {
        val inlineSize = inlineCount
        if (position < inlineSize) {
            return VIEW_TYPE_INLINE_SUGGESTION
        }
        val adjustedPosition = position - inlineSize

        return if (suggestions.isNotEmpty()) {
            if (suggestions[adjustedPosition].isSelectedTextGemmaActionCandidate()) {
                VIEW_TYPE_GEMMA_ACTION
            } else {
                VIEW_TYPE_SUGGESTION
            }
        } else {
            if (currentMode is TenKeyQWERTYMode.Custom && customLayouts.isNotEmpty() && showCustomTab) {
                VIEW_TYPE_CUSTOM_LAYOUT_PICKER
            } else {
                VIEW_TYPE_EMPTY
            }
        }
    }

    override fun getItemCount(): Int {
        val baseCount = if (suggestions.isNotEmpty()) {
            suggestions.size
        } else if (inlineCount > 0) {
            0
        } else {
            if (currentMode is TenKeyQWERTYMode.Custom && customLayouts.isNotEmpty()) {
                customLayouts.size
            } else {
                1
            }
        }
        return inlineCount + baseCount
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val isDynamicColorEnable = DynamicColors.isDynamicColorAvailable()
        return when (viewType) {
            VIEW_TYPE_EMPTY -> {
                val emptyView = LayoutInflater.from(parent.context)
                    .inflate(R.layout.suggestion_empty_layout, parent, false)
                EmptyViewHolder(emptyView)
            }

            VIEW_TYPE_CUSTOM_LAYOUT_PICKER -> {
                val customView = LayoutInflater.from(parent.context)
                    .inflate(R.layout.suggestion_custom_layout_item, parent, false)
                CustomLayoutViewHolder(customView)
            }

            VIEW_TYPE_SUGGESTION -> {
                val itemView = LayoutInflater.from(parent.context)
                    .inflate(R.layout.suggestion_item, parent, false)
                itemView.setBackgroundResource(
                    if (isDynamicColorEnable) com.kazumaproject.core.R.drawable.recyclerview_item_bg_material else com.kazumaproject.core.R.drawable.recyclerview_item_bg
                )
                SuggestionViewHolder(itemView)
            }

            VIEW_TYPE_GEMMA_ACTION -> {
                val itemView = LayoutInflater.from(parent.context)
                    .inflate(R.layout.suggestion_gemma_action_item, parent, false)
                itemView.setBackgroundResource(
                    if (isDynamicColorEnable) com.kazumaproject.core.R.drawable.recyclerview_item_bg_material else com.kazumaproject.core.R.drawable.recyclerview_item_bg
                )
                GemmaActionViewHolder(itemView)
            }

            VIEW_TYPE_INLINE_SUGGESTION -> {
                val container = InlineSuggestionClipContainer(parent.context).apply {
                    val density = resources.displayMetrics.density
                    val margin2px = (2 * density).toInt()
                    layoutParams = ViewGroup.MarginLayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        (36 * density).toInt()
                    ).apply {
                        topMargin = margin2px
                        bottomMargin = margin2px
                    }
                    minimumWidth = parent.context.dpToPxInt(100f)
                    setBackgroundResource(android.R.color.transparent)
                }
                InlineSuggestionViewHolder(container)
            }

            else -> throw IllegalArgumentException("Unknown view type: $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder.itemViewType) {
            VIEW_TYPE_INLINE_SUGGESTION -> onBindInlineSuggestionViewHolder(
                holder as InlineSuggestionViewHolder, position
            )
            VIEW_TYPE_EMPTY -> onBindEmptyViewHolder(holder as EmptyViewHolder)
            VIEW_TYPE_SUGGESTION -> onBindSuggestionViewHolder(
                holder as SuggestionViewHolder, position - inlineCount
            )
            VIEW_TYPE_GEMMA_ACTION -> onBindGemmaActionViewHolder(
                holder as GemmaActionViewHolder, position - inlineCount
            )

            VIEW_TYPE_CUSTOM_LAYOUT_PICKER -> onBindCustomLayoutViewHolder(
                holder as CustomLayoutViewHolder, position - inlineCount
            )
        }
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        super.onViewRecycled(holder)
        if (holder is InlineSuggestionViewHolder) {
            holder.currentSuggestion = null
            holder.container.removeAllViews()
        }
    }

    private fun onBindInlineSuggestionViewHolder(holder: InlineSuggestionViewHolder, position: Int) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            holder.itemView.post {
                try {
                    if (position >= 0 && position < inlineSuggestions.size) {
                        val suggestion = inlineSuggestions[position]
                        holder.currentSuggestion = suggestion
                        val clipContainer = holder.itemView as? InlineSuggestionClipContainer
                        val targetContainer = clipContainer?.getContainer() ?: holder.container
                        targetContainer.removeAllViews()

                        val isOnlyInline = suggestions.isEmpty() && inlineCount == 1
                        val density = holder.itemView.context.resources.displayMetrics.density

                        clipContainer?.maxWidthLimit = if (isOnlyInline) (160 * density).toInt() else -1

                        holder.itemView.layoutParams = (holder.itemView.layoutParams as? ViewGroup.MarginLayoutParams)?.apply {
                            width = if (isOnlyInline) ViewGroup.LayoutParams.MATCH_PARENT else ViewGroup.LayoutParams.WRAP_CONTENT
                            rightMargin = 0
                        }

                        if (isOnlyInline) {
                            holder.itemView.setBackgroundResource(android.R.color.transparent)
                        } else {
                            holder.itemView.setBackgroundResource(android.R.color.transparent)
                        }

                        val targetLp = targetContainer.layoutParams as? FrameLayout.LayoutParams ?: FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.MATCH_PARENT
                        )

                        if (isOnlyInline) {
                            targetLp.width = FrameLayout.LayoutParams.WRAP_CONTENT
                            targetLp.height = (36 * density).toInt()
                            targetLp.gravity = Gravity.CENTER
                            targetContainer.layoutParams = targetLp

                            targetContainer.setBackgroundResource(android.R.color.transparent)
                            targetContainer.setPadding(0, 0, 0, 0)
                        } else {
                            targetLp.width = FrameLayout.LayoutParams.MATCH_PARENT
                            targetLp.height = FrameLayout.LayoutParams.MATCH_PARENT
                            targetLp.gravity = Gravity.NO_GRAVITY
                            targetContainer.layoutParams = targetLp
                            targetContainer.setBackgroundResource(android.R.color.transparent)
                            targetContainer.setPadding(0, 0, 0, 0)
                        }

                        val widthPx = if (isOnlyInline) (160 * density).toInt() else ViewGroup.LayoutParams.WRAP_CONTENT
                        val heightPx = (36 * density).toInt()
                        val size = Size(
                            widthPx,
                            heightPx
                        )

                        Timber.d("Inflating inline suggestion position=$position size=${size.width}x${size.height}")
                        suggestion.inflate(holder.itemView.context, size, holder.itemView.context.mainExecutor) { view ->
                            holder.itemView.post {
                                if (holder.currentSuggestion === suggestion) {
                                    try {
                                        if (view != null) {
                                            if (view.parent != null) {
                                                (view.parent as? ViewGroup)?.removeView(view)
                                            }
                                            targetContainer.removeAllViews()
                                            targetContainer.addView(
                                                view,
                                                FrameLayout.LayoutParams(
                                                    FrameLayout.LayoutParams.WRAP_CONTENT,
                                                    FrameLayout.LayoutParams.MATCH_PARENT,
                                                    Gravity.CENTER
                                                )
                                            )
                                        } else {
                                            Timber.w("Inline suggestion inflated null view position=$position")
                                        }
                                    } catch (e: Exception) {
                                        Timber.e(e, "Error adding inline suggestion view in post")
                                    }
                                } else {
                                    Timber.d("Discarded inflated view for position=$position because suggestion has changed")
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Error during onBindInlineSuggestionViewHolder")
                }
            }
        }
    }

    private fun onBindEmptyViewHolder(holder: EmptyViewHolder) {
        val isDynamicColorEnable = DynamicColors.isDynamicColorAvailable()
        Timber.d("SuggestionAdapter onBindEmptyViewHolder: $clipboardText $isPasteEnabled")
        holder.apply {
            undoIcon?.typeface = customTypeface
            redoIcon?.typeface = customTypeface
            reconvertIcon?.typeface = customTypeface
            clipboardPreviewText?.typeface = customTypeface
            clipboardPreviewTextDescription?.typeface = customTypeface

            incognitoIcon?.apply {
                if (incognitoIconDrawable != null) {
                    visibility = View.VISIBLE
                    setImageDrawable(incognitoIconDrawable)
                } else {
                    visibility = View.GONE
                }
            }

            undoIcon?.apply {
                isVisible = isUndoEnabled
                isFocusable = false
                Timber.d("undo text: $undoText")
                debugPrintCodePoints(undoText)
                text = undoText
            }
            redoIcon?.apply {
                isVisible = isRedoEnabled
                isFocusable = false
                text = redoText
            }
            reconvertIcon?.apply {
                isVisible = isReconvertEnabled
                isFocusable = false
            }
            pasteIconParent?.apply {
                isEnabled = isPasteEnabled
                visibility = if (isPasteEnabled) View.VISIBLE else View.INVISIBLE
                isFocusable = false
                val hasLeadingHelper =
                    incognitoIconDrawable != null || isUndoEnabled || isRedoEnabled || isReconvertEnabled
                (layoutParams as? ConstraintLayout.LayoutParams)?.let { params ->
                    if (hasLeadingHelper) {
                        params.startToStart = ConstraintLayout.LayoutParams.UNSET
                        params.startToEnd = R.id.reconvert_icon_parent
                        params.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
                        params.horizontalBias = 1f
                        params.marginStart = context.dpToPxInt(16f)
                        params.marginEnd = 0
                    } else {
                        params.startToEnd = ConstraintLayout.LayoutParams.UNSET
                        params.startToStart = ConstraintLayout.LayoutParams.PARENT_ID
                        params.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
                        params.horizontalBias = 0.5f
                        params.marginStart = 0
                        params.marginEnd = context.dpToPxInt(40f)
                    }
                    layoutParams = params
                }

                candidateEmptyDrawableColor?.let {
                    this.setDrawableSolidColor(it)
                }
            }

            // ★修正: 画像プレビューのロジック
            pasteIcon?.apply {
                if (clipboardBitmap != null) {
                    // Bitmapがあればそれを設定
                    setImageBitmap(clipboardBitmap)
                    scaleType = ImageView.ScaleType.CENTER_CROP
                } else {
                    // なければデフォルトのアイコンを設定
                    setImageResource(com.kazumaproject.core.R.drawable.content_paste_24px)
                    scaleType = ImageView.ScaleType.CENTER_INSIDE
                    candidateEmptyDrawableTextColor?.let {
                        setColorFilter(it)
                    }
                }
            }

            // テキストプレビューは、画像がない場合にのみ表示
            clipboardPreviewText?.text = if (clipboardBitmap == null) clipboardText else ""
            candidateEmptyDrawableTextColor?.let {
                clipboardPreviewText?.setTextColor(it)
            }
            candidateEmptyDrawableTextColor?.let { color ->
                reconvertIcon?.setTextColor(color)
                reconvertImageView?.setColorFilter(color)
            }

            undoIconParent?.apply {
                if (isDynamicColorEnable) {
                    if (this.context.isDarkThemeOn()) {
                        setBackgroundResource(
                            com.kazumaproject.core.R.drawable.ten_keys_side_bg_material
                        )
                    } else {
                        setBackgroundResource(
                            com.kazumaproject.core.R.drawable.ten_keys_side_bg_material_light
                        )
                    }
                }
                isVisible = isUndoEnabled
                setOnClickListener {
                    onItemHelperIconClickListener?.invoke(HelperIcon.UNDO)
                }
                setOnLongClickListener {
                    onItemHelperIconLongClickListener?.invoke(HelperIcon.UNDO)
                    true
                }
            }

            redoIconParent?.apply {
                if (isDynamicColorEnable) {
                    if (this.context.isDarkThemeOn()) {
                        setBackgroundResource(
                            com.kazumaproject.core.R.drawable.ten_keys_side_bg_material
                        )
                    } else {
                        setBackgroundResource(
                            com.kazumaproject.core.R.drawable.ten_keys_side_bg_material_light
                        )
                    }
                }
                isVisible = isRedoEnabled
                setOnClickListener {
                    onItemHelperIconClickListener?.invoke(HelperIcon.REDO)
                }
                setOnLongClickListener {
                    onItemHelperIconLongClickListener?.invoke(HelperIcon.REDO)
                    true
                }
            }

            reconvertIconParent?.apply {
                if (isDynamicColorEnable) {
                    if (this.context.isDarkThemeOn()) {
                        setBackgroundResource(
                            com.kazumaproject.core.R.drawable.ten_keys_side_bg_material
                        )
                    } else {
                        setBackgroundResource(
                            com.kazumaproject.core.R.drawable.ten_keys_side_bg_material_light
                        )
                    }
                }
                isVisible = isReconvertEnabled
                setOnClickListener {
                    onItemHelperIconClickListener?.invoke(HelperIcon.RECONVERT)
                }
                setOnLongClickListener {
                    false
                }
            }

            // テキスト用の説明は、画像がない場合にのみ表示
            clipboardPreviewTextDescription?.isVisible =
                isPasteEnabled && clipboardBitmap == null && isClipboardDescriptionShow

            pasteIconParent?.apply {
                setOnClickListener {
                    onItemHelperIconClickListener?.invoke(HelperIcon.PASTE)
                }
                setOnLongClickListener {
                    onItemHelperIconLongClickListener?.invoke(HelperIcon.PASTE)
                    true
                }
            }
        }
    }

    fun setCandidateTextSize(size: Float) {
        if (candidateTextSize == size) return
        candidateTextSize = size
        notifyItemRangeChanged(0, itemCount)
    }

    fun setShowCandidateYomiForLiveConversion(enabled: Boolean) {
        if (showCandidateYomiForLiveConversion == enabled) return
        showCandidateYomiForLiveConversion = enabled
        notifyItemRangeChanged(0, itemCount)
    }

    fun setCandidateTextColor(color: Int) {
        if (candidateTextColor == color) return
        candidateTextColor = color
        // 全アイテムを更新して色を反映させる
        notifyItemRangeChanged(0, itemCount)
    }

    fun setCandidateItemBackgroundColor(color: Int) {
        if (!candidateItemColorState.setBackgroundColor(color)) return
        notifyItemRangeChanged(0, itemCount)
    }

    fun setCandidateItemPressedBackgroundColor(color: Int) {
        if (!candidateItemColorState.setPressedBackgroundColor(color)) return
        notifyItemRangeChanged(0, itemCount)
    }

    fun setCandidateItemColors(backgroundColor: Int, pressedColor: Int) {
        if (!candidateItemColorState.setColors(backgroundColor, pressedColor)) return
        notifyItemRangeChanged(0, itemCount)
    }

    fun setCandidateEmptyDrawableColor(color: Int) {
        if (candidateEmptyDrawableColor == color) return
        candidateEmptyDrawableColor = color
        // 全アイテムを更新して色を反映させる
        notifyItemRangeChanged(0, itemCount)
    }

    fun setCandidateEmptyDrawableTextColor(color: Int) {
        if (candidateEmptyDrawableTextColor == color) return
        candidateEmptyDrawableTextColor = color
        // 全アイテムを更新して色を反映させる
        notifyItemRangeChanged(0, itemCount)
    }

    private fun onBindSuggestionViewHolder(holder: SuggestionViewHolder, position: Int) {
        applyCandidateItemBackground(holder.itemView)
        val suggestion = suggestions[position]
        val paddingLength = when {
            position == 0 -> 4
            suggestion.string.length == 1 -> 4
            suggestion.string.length == 2 -> 2
            else -> 1
        }
        val readingCorrectionString =
            if (suggestion.type == (15).toByte()) suggestion.string.correctReading() else Pair(
                "", ""
            )
        holder.text.text = if (suggestion.type == (15).toByte()) {
            readingCorrectionString.first.padStart(readingCorrectionString.first.length + paddingLength)
                .plus(" ".repeat(paddingLength))
        } else {
            suggestion.string.padStart(suggestion.string.length + paddingLength)
                .plus(" ".repeat(paddingLength))
        }

        holder.text.typeface = customTypeface
        holder.yomiText.typeface = customTypeface
        holder.typeText.typeface = customTypeface
        holder.text.textSize = candidateTextSize
        val yomiPresentation = resolveCandidateYomiPresentation(
            showCandidateYomiForLiveConversion = showCandidateYomiForLiveConversion,
            isFirstCandidate = position == 0,
            suggestion = suggestion,
            candidateTextSize = candidateTextSize
        )
        holder.yomiText.isVisible = yomiPresentation.isVisible
        holder.yomiText.text = yomiPresentation.text
        holder.yomiText.textSize = yomiPresentation.textSize
        holder.yomiText.translationX = if (yomiPresentation.isVisible) {
            holder.text.paint.measureText(" ".repeat(paddingLength))
        } else {
            0f
        }

        candidateTextColor?.let { color ->
            holder.text.setTextColor(color)
            // 必要であれば typeText（[半]などの補足テキスト）にも同じ色、またはその色の薄い版などを適用
            holder.typeText.setTextColor(color)
            holder.yomiText.setTextColor(color)
        }

        holder.typeText.text = when (suggestion.type) {
            (1).toByte() -> ""
            /** 予測 **/
            (9).toByte() -> ""
            (5).toByte() -> "[部]"
            (7).toByte() -> ""
            /** 最長 **/
            (10).toByte() -> ""
            /** 絵文字 **/
            (11).toByte() -> "  "
            /** 顔文字 **/
            (12).toByte() -> "  "
            /** 記号 **/
            (13).toByte() -> {
                when {
                    suggestion.string.isAllHalfWidthNumericSymbol() -> "[半]"
                    suggestion.string.isAllFullWidthNumericSymbol() -> "[全]"
                    else -> "  "
                }
            }
            /** 日付 **/
            (14).toByte() -> "[日付]"
            /** 修正 **/
            (15).toByte() -> {
                val spannable = SpannableString("[読] ${readingCorrectionString.second}")
                spannable.setSpan(
                    RelativeSizeSpan(1.25f), 4, spannable.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                spannable
            }
            /** ことわざ **/
            (16).toByte() -> ""
            /** 数 漢字混じり **/
            (17).toByte() -> ""
            /** 数 カンマあり**/
            (18).toByte() -> ""
            /** 数 **/
            (19).toByte() -> ""
            /** 学習 **/
            (20).toByte() -> ""
            /** 記号 **/
            (21).toByte() -> when {
                suggestion.string.isAllHalfWidthNumericSymbol() -> "[半]"
                suggestion.string.isAllFullWidthNumericSymbol() -> "[全]"
                else -> "  "
            }
            /** 全角数字 **/
            (22).toByte() -> "[全]"
            /** Mozc UT Names **/
            (23).toByte() -> ""
            /** Mozc UT Places **/
            (24).toByte() -> ""
            /** Mozc UT Wiki **/
            (25).toByte() -> ""
            /** Mozc UT Neologd **/
            (26).toByte() -> ""
            /** Mozc UT Web **/
            (27).toByte() -> ""
            (28).toByte() -> ""
            /** 英語 **/
            (29).toByte() -> ""
            /** 全角 **/
            (30).toByte() -> "[全]"
            /** 半角 **/
            (31).toByte() -> "[半]"
            /** 漢数字 **/
            (32).toByte() -> ""
            /** Zenz **/
            (33).toByte() -> "[AI]"
            (34).toByte() -> "[履歴]"
            /** Typo Correction QWERTY **/
            (35).toByte() -> "[修正]"

            (36).toByte() -> ""
            (37).toByte() -> "[AI]"
            (38).toByte() -> ""
            (39).toByte() -> ""
            (40).toByte() -> "[AI]"
            QWERTY_GLIDE_CANDIDATE_TYPE -> ""
            GemmaTranslationManager.TRANSLATED_CANDIDATE_TYPE.toByte() -> "[訳]"
            GemmaTranslationManager.PROMPT_RESULT_CANDIDATE_TYPE.toByte() -> "[AI]"
            GemmaTranslationManager.SELECTION_TRANSLATE_ACTION_CANDIDATE_TYPE.toByte() -> "[訳]"
            GemmaTranslationManager.SELECTION_PROMPT_ACTION_CANDIDATE_TYPE.toByte() -> "[AI]"
            else -> ""
        }
        holder.itemView.isPressed = position == highlightedPosition
        holder.itemView.setOnClickListener {
            onItemClickListener?.invoke(suggestion, position)
        }
        holder.itemView.setOnLongClickListener {
            onItemLongClickListener?.invoke(suggestion, position)
            true
        }
    }

    private fun onBindGemmaActionViewHolder(holder: GemmaActionViewHolder, position: Int) {
        applyCandidateItemBackground(holder.itemView)
        val suggestion = suggestions[position]
        holder.actionText.typeface = customTypeface
        holder.badgeText.typeface = customTypeface
        holder.actionText.text = suggestion.string
        holder.actionText.textSize = candidateTextSize
        holder.badgeText.text = when (suggestion.type) {
            GemmaTranslationManager.SELECTION_TRANSLATE_ACTION_CANDIDATE_TYPE.toByte() -> "訳"
            GemmaTranslationManager.SELECTION_PROMPT_ACTION_CANDIDATE_TYPE.toByte() -> "AI"
            else -> ""
        }

        candidateTextColor?.let { color ->
            holder.actionText.setTextColor(color)
            holder.badgeText.setTextColor(color)
        }
        candidateItemColorState.backgroundColor?.let { color ->
            holder.badgeText.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = holder.itemView.context.dpToPxInt(10f).toFloat()
                setColor(color)
            }
        }

        holder.itemView.isPressed = position == highlightedPosition
        holder.itemView.setOnClickListener {
            onItemClickListener?.invoke(suggestion, position)
        }
        holder.itemView.setOnLongClickListener {
            onItemLongClickListener?.invoke(suggestion, position)
            true
        }
    }

    private fun onBindCustomLayoutViewHolder(holder: CustomLayoutViewHolder, position: Int) {
        val layoutItem = customLayouts[position]
        holder.nameTextView.typeface = customTypeface
        holder.nameTextView.text = layoutItem.name
        holder.itemView.setOnClickListener {
            onCustomLayoutItemClickListener?.invoke(position)
        }
    }

    private fun applyCandidateItemBackground(itemView: View) {
        val backgroundColor = candidateItemColorState.backgroundColor
        val pressedColor = candidateItemColorState.pressedBackgroundColor
        if (backgroundColor == null && pressedColor == null) {
            itemView.setBackgroundResource(defaultCandidateItemBackgroundRes())
            return
        }

        itemView.background = StateListDrawable().apply {
            addState(
                intArrayOf(android.R.attr.state_pressed),
                createCandidateItemDrawable(
                    pressedColor ?: ContextCompat.getColor(
                        itemView.context,
                        com.kazumaproject.core.R.color.qwety_key_bg_color
                    ),
                    itemView.context.resources.displayMetrics.density
                )
            )
            addState(
                intArrayOf(),
                createCandidateItemDrawable(
                    backgroundColor ?: Color.TRANSPARENT,
                    itemView.context.resources.displayMetrics.density
                )
            )
        }
    }

    private fun defaultCandidateItemBackgroundRes(): Int {
        return if (DynamicColors.isDynamicColorAvailable()) {
            com.kazumaproject.core.R.drawable.recyclerview_item_bg_material
        } else {
            com.kazumaproject.core.R.drawable.recyclerview_item_bg
        }
    }

    private fun createCandidateItemDrawable(color: Int, density: Float): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = 16f * density
        }
    }

    fun updateHighlightPosition(newPosition: Int) {
        val previous = highlightedPosition
        highlightedPosition = newPosition
        if (previous != RecyclerView.NO_POSITION) {
            notifyItemChanged(previous)
        }
        notifyItemChanged(highlightedPosition)
    }

    private fun Candidate.isSelectedTextGemmaActionCandidate(): Boolean {
        return type == GemmaTranslationManager.SELECTION_TRANSLATE_ACTION_CANDIDATE_TYPE.toByte() ||
            type == GemmaTranslationManager.SELECTION_PROMPT_ACTION_CANDIDATE_TYPE.toByte()
    }

    private fun android.content.Context.dpToPxInt(dp: Float): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            resources.displayMetrics
        ).toInt()
    }

}

class InlineSuggestionClipContainer @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {
    private val mOnDrawListener = ViewTreeObserver.OnDrawListener {
        clipDescendantInlineContentViews()
    }
    private val mParentBounds = Rect()
    private val mContentBounds = Rect()
    private val mContentContainer: FrameLayout

    var maxWidthLimit: Int = -1

    init {
        val mBackgroundView = SurfaceView(context)
        mBackgroundView.setZOrderOnTop(true)
        mBackgroundView.holder.setFormat(PixelFormat.TRANSPARENT)
        addView(mBackgroundView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        mContentContainer = FrameLayout(context)
        addView(mContentContainer, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        if (maxWidthLimit > 0) {
            val childWidthSpec = MeasureSpec.makeMeasureSpec(maxWidthLimit, MeasureSpec.AT_MOST)
            if (childCount >= 2) {
                val backgroundView = getChildAt(0)
                val contentContainer = getChildAt(1)

                contentContainer.measure(childWidthSpec, heightMeasureSpec)

                val bgWidthSpec = MeasureSpec.makeMeasureSpec(contentContainer.measuredWidth, MeasureSpec.EXACTLY)
                val bgHeightSpec = MeasureSpec.makeMeasureSpec(contentContainer.measuredHeight, MeasureSpec.EXACTLY)
                backgroundView.measure(bgWidthSpec, bgHeightSpec)

                val widthMode = MeasureSpec.getMode(widthMeasureSpec)
                val widthSize = MeasureSpec.getSize(widthMeasureSpec)
                    val finalWidth = if (widthMode == MeasureSpec.EXACTLY) {
                        widthSize
                    } else {
                        contentContainer.measuredWidth
                    }

                    setMeasuredDimension(finalWidth, contentContainer.measuredHeight)
                return
            }
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        viewTreeObserver.addOnDrawListener(mOnDrawListener)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        viewTreeObserver.removeOnDrawListener(mOnDrawListener)
    }

    private fun clipDescendantInlineContentViews() {
        mParentBounds.set(0, 0, width, height)
        clipDescendantInlineContentViews(this)
    }

    private fun clipDescendantInlineContentViews(root: View?) {
        if (root == null) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (root is InlineContentView) {
                mContentBounds.set(mParentBounds)
                offsetRectIntoDescendantCoords(root, mContentBounds)
                root.clipBounds = mContentBounds
                return
            }
        }
        if (root is ViewGroup) {
            val childCount = root.childCount
            for (i in 0 until childCount) {
                val child = root.getChildAt(i)
                clipDescendantInlineContentViews(child)
            }
        }
    }

    fun getContainer(): FrameLayout {
        return mContentContainer
    }
}
