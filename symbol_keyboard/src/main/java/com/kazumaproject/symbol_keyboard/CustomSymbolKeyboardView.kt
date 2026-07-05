package com.kazumaproject.symbol_keyboard

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.util.TypedValue
import android.view.GestureDetector
import android.view.MotionEvent
import android.text.Spannable
import android.text.SpannableString
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.tabs.TabLayout
import com.kazumaproject.core.data.clicked_symbol.SymbolMode
import com.kazumaproject.core.data.clipboard.ClipboardItem
import com.kazumaproject.data.clicked_symbol.ClickedSymbol
import com.kazumaproject.data.emoji.Emoji
import com.kazumaproject.data.emoji.EmojiCategory
import com.kazumaproject.data.emoticon.Emoticon
import com.kazumaproject.data.emoticon.EmoticonCategory
import com.kazumaproject.data.symbol.Symbol
import com.kazumaproject.data.symbol.SymbolCategory
import com.kazumaproject.domain.EmojiSkinToneSupport
import com.kazumaproject.listeners.ClipboardHistoryToggleListener
import com.kazumaproject.listeners.ClipboardItemLongClickListener
import com.kazumaproject.listeners.DeleteButtonSymbolViewClickListener
import com.kazumaproject.listeners.DeleteButtonSymbolViewLongClickListener
import com.kazumaproject.listeners.ImageItemClickListener
import com.kazumaproject.listeners.ReturnToTenKeyButtonClickListener
import com.kazumaproject.listeners.SymbolRecyclerViewItemClickListener
import com.kazumaproject.listeners.SymbolRecyclerViewItemLongClickListener
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@SuppressLint("ClickableViewAccessibility")
class CustomSymbolKeyboardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr) {

    private val categoryTab: TabLayout
    private val modeTab: TabLayout
    private val recycler: RecyclerView
    private val symbolAdapter = SymbolAdapter()
    private val clipboardAdapter = ClipboardAdapter()
    private val gridLM = GridLayoutManager(context, 3, RecyclerView.HORIZONTAL, false)

    // View References for functional keys
    private val returnButton: TextView
    private val deleteButton: ShapeableImageView

    // Theme Colors (Default values)
    private var themeBackgroundColor: Int = Color.WHITE
    private var themeIconColor: Int = Color.GRAY
    private var themeSelectedIconColor: Int = Color.BLUE
    private var themeKeyBackgroundColor: Int = Color.LTGRAY
    private var liquidGlassEnable: Boolean = false

    // Flag to check if custom theme is applied
    private var isCustomThemeApplied = false

    private var emojiMap: Map<EmojiCategory, List<Emoji>> = emptyMap()
    private var emoticonMap: Map<EmoticonCategory, List<String>> = emptyMap()
    private var symbolMap: Map<SymbolCategory, List<String>> = emptyMap()

    private var historyEmojiList: MutableList<String> = mutableListOf()
    private var historyEmoticonList: MutableList<String> = mutableListOf()
    private var historySymbolList: MutableList<String> = mutableListOf()

    private var symbolsHistory: List<ClickedSymbol> = emptyList()
    private var clipBoardItems: List<ClipboardItem> = emptyList()
    private var currentMode: SymbolMode = SymbolMode.EMOJI

    private var pagingJob: Job? = null
    private var lifecycleOwner: LifecycleOwner? = null
    private var skinTonePopup: PopupWindow? = null
    private var defaultEmojiSkinTone: String = EmojiSkinToneSupport.DEFAULT_SKIN_TONE

    private var returnListener: ReturnToTenKeyButtonClickListener? = null
    private var deleteClickListener: DeleteButtonSymbolViewClickListener? = null
    private var deleteLongListener: DeleteButtonSymbolViewLongClickListener? = null
    private var itemClickListener: SymbolRecyclerViewItemClickListener? = null
    private var itemLongClickListener: SymbolRecyclerViewItemLongClickListener? = null
    private var imageItemClickListener: ImageItemClickListener? = null
    private var clipboardItemClickListener: ((ClipboardItem) -> Unit)? = null
    private var clipboardItemLongClickListener: ClipboardItemLongClickListener? = null
    private var clipboardHistoryToggleListener: ClipboardHistoryToggleListener? = null
    private var defaultEmojiSkinToneChangeListener: ((String) -> Unit)? = null
    private var isClipboardHistoryEnabled: Boolean = false
    private var onDeleteFingerUpListener: (() -> Unit)? = null

    // Emoji Kitchen 関連のメンバ変数
    private var emojiKitchenPreviewContainer: ConstraintLayout? = null
    private var emojiKitchenPreviewImage: android.widget.ImageView? = null
    private var emojiKitchenPreviewLabel: TextView? = null
    private var emojiKitchenResetButton: TextView? = null

    private var firstKitchenEmoji: com.kazumaproject.symbol_keyboard.emoji_kitchen.EmojiKitchenManager.EmojiItem? = null
    private var secondKitchenEmoji: com.kazumaproject.symbol_keyboard.emoji_kitchen.EmojiKitchenManager.EmojiItem? = null
    private var generatedStickerBitmap: android.graphics.Bitmap? = null

    private var emojiKitchenRecyclerView: RecyclerView? = null
    private val emojiKitchenAdapter = EmojiKitchenStickerAdapter()
    private var emojiKitchenLoadJob: Job? = null

    // クリップボード関連のUIコンポーネント
    private var clipboardControlLayout: LinearLayout? = null
    private var clipboardSearchView: androidx.appcompat.widget.SearchView? = null
    private var clipboardClearAllButton: ShapeableImageView? = null

    private var clipboardClearAllListener: (() -> Unit)? = null
    private var clipboardSearchListener: ((String) -> Unit)? = null

    private var emojiControlLayout: LinearLayout? = null
    private var emojiSearchView: androidx.appcompat.widget.SearchView? = null
    private var emojiSearchListener: ((String) -> Unit)? = null
    private var emojiSearchResults: List<String>? = null
    private var symbolPanelSearchFocusListener: ((Boolean) -> Unit)? = null

    private enum class ActiveSearchTarget {
        CLIPBOARD,
        EMOJI,
    }

    private var activeSearchTarget: ActiveSearchTarget? = null

    init {
        inflate(context, R.layout.symbol_keyboard_main_layout, this)

        categoryTab = findViewById(R.id.category_tab_layout)
        modeTab = findViewById(R.id.mode_tab_layout)
        recycler = findViewById(R.id.symbol_candidate_recycler_view)
        returnButton = findViewById(R.id.return_jp_keyboard_button)
        val returnText = SpannableString("あa")
        returnText.setSpan(
            StyleSpan(android.graphics.Typeface.BOLD),
            0,
            1,
            Spannable.SPAN_INCLUSIVE_INCLUSIVE
        )
        returnText.setSpan(
            StyleSpan(android.graphics.Typeface.NORMAL),
            1,
            2,
            Spannable.SPAN_INCLUSIVE_INCLUSIVE
        )
        returnText.setSpan(
            RelativeSizeSpan(1.4f),
            1,
            2,
            Spannable.SPAN_INCLUSIVE_INCLUSIVE
        )
        returnButton.text = returnText
        returnButton.gravity = android.view.Gravity.CENTER

        deleteButton = findViewById(R.id.symbol_keyboard_delete_key)

        emojiKitchenRecyclerView = findViewById(R.id.emoji_kitchen_recycler_view)
        emojiKitchenRecyclerView?.apply {
            layoutManager = androidx.recyclerview.widget.LinearLayoutManager(context, RecyclerView.HORIZONTAL, false)
            adapter = emojiKitchenAdapter
        }

        emojiKitchenPreviewContainer = findViewById(R.id.emoji_kitchen_preview_container)
        emojiKitchenPreviewImage = findViewById(R.id.emoji_kitchen_preview_image)
        emojiKitchenPreviewLabel = findViewById(R.id.emoji_kitchen_preview_label)
        emojiKitchenResetButton = findViewById(R.id.emoji_kitchen_reset_button)

        emojiKitchenResetButton?.setOnClickListener {
            resetEmojiKitchenState()
        }

        clipboardControlLayout = findViewById(R.id.clipboard_control_layout)
        clipboardSearchView = findViewById(R.id.clipboard_search_view)
        clipboardClearAllButton = findViewById(R.id.clipboard_clear_all_button)

        emojiControlLayout = findViewById(R.id.emoji_control_layout)
        emojiSearchView = findViewById(R.id.emoji_search_view)

        clipboardClearAllButton?.setOnClickListener {
            clipboardClearAllListener?.invoke()
        }

        clipboardSearchView?.setOnQueryTextListener(object : androidx.appcompat.widget.SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                return false
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                clipboardSearchListener?.invoke(newText.orEmpty())
                return true
            }
        })
        setupSymbolSearchView(clipboardSearchView, ActiveSearchTarget.CLIPBOARD)

        emojiSearchView?.setOnQueryTextListener(object : androidx.appcompat.widget.SearchView.OnQueryTextListener {
            override fun onQueryTextChange(newText: String?): Boolean {
                val query = newText.orEmpty()
                emojiSearchListener?.invoke(query)
                if (query.isBlank()) {
                    emojiSearchResults = null
                    if (currentMode == SymbolMode.EMOJI) {
                        categoryTab.visibility = View.VISIBLE
                        updateSymbolsForCategory(categoryTab.selectedTabPosition)
                    }
                }
                return true
            }

            override fun onQueryTextSubmit(query: String?): Boolean = false
        })
        setupSymbolSearchView(emojiSearchView, ActiveSearchTarget.EMOJI)

        emojiKitchenPreviewImage?.setOnClickListener {
            val bitmap = generatedStickerBitmap
            if (bitmap != null) {
                imageItemClickListener?.onImageClick(bitmap)
                resetEmojiKitchenState()
            }
        }

        // Initialize default colors
        themeIconColor =
            ContextCompat.getColor(context, com.kazumaproject.core.R.color.keyboard_icon_color)
        themeSelectedIconColor =
            ContextCompat.getColor(context, com.kazumaproject.core.R.color.enter_key_bg)
        themeKeyBackgroundColor =
            ContextCompat.getColor(context, com.kazumaproject.core.R.color.keyboard_bg)

        recycler.apply {
            layoutManager = gridLM
            adapter = symbolAdapter
            itemAnimator = null
        }

        symbolAdapter.setOnItemClickListener { str ->
            if (currentMode == SymbolMode.EMOJI_KITCHEN) {
                handleEmojiKitchenClick(str)
            } else {
                itemClickListener?.onClick(ClickedSymbol(mode = currentMode, symbol = str))
                if (currentMode == SymbolMode.EMOJI) {
                    tryToTriggerGboardEmojiKitchen(str)
                }
            }
        }

        clipboardAdapter.setOnItemClickListener { item ->
            clipboardItemClickListener?.invoke(item)
        }

        clipboardAdapter.setOnItemActionListener { item, action ->
            clipboardItemLongClickListener?.onAction(item, action)
        }

        symbolAdapter.setOnItemLongClickListener { str, pos, anchor ->
            if (
                currentMode == SymbolMode.EMOJI &&
                !isHistoryCategorySelected() &&
                EmojiSkinToneSupport.hasSkinToneVariants(str)
            ) {
                showSkinTonePopup(str, anchor)
                return@setOnItemLongClickListener
            }

            val historyList = when (currentMode) {
                SymbolMode.EMOJI -> historyEmojiList
                SymbolMode.EMOTICON -> historyEmoticonList
                SymbolMode.SYMBOL -> historySymbolList
                else -> emptyList()
            }
            if (historyList.isNotEmpty()
                && categoryTab.selectedTabPosition == 0
                && pos in historyList.indices
            ) {
                itemLongClickListener?.onLongClick(
                    ClickedSymbol(mode = currentMode, symbol = str),
                    position = pos
                )
                when (currentMode) {
                    SymbolMode.EMOJI -> historyEmojiList =
                        historyEmojiList.toMutableList().apply { removeAt(pos) }

                    SymbolMode.EMOTICON -> historyEmoticonList =
                        historyEmoticonList.toMutableList().apply { removeAt(pos) }

                    SymbolMode.SYMBOL -> historySymbolList =
                        historySymbolList.toMutableList().apply { removeAt(pos) }

                    else -> {}
                }
                updateSymbolsForCategory(0)
            }
        }

        returnButton.setOnClickListener {
            returnListener?.onClick()
        }

        deleteButton.apply {
            val handler = Handler(Looper.getMainLooper())
            var isLongPressed = false

            val longPressRunnable = Runnable {
                isLongPressed = true
                deleteLongListener?.onLongClickListener()
            }

            setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        isLongPressed = false
                        handler.postDelayed(
                            longPressRunnable,
                            ViewConfiguration.getLongPressTimeout().toLong()
                        )
                        true
                    }

                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        handler.removeCallbacks(longPressRunnable)
                        if (isLongPressed) {
                            onDeleteFingerUpListener?.invoke()
                        } else {
                            deleteClickListener?.onClick()
                        }
                        true
                    }

                    else -> false
                }
            }
            setOnClickListener(null)
        }

        modeTab.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                currentMode = SymbolMode.entries[tab?.position ?: 0]
                buildCategoryTabs()
                categoryTab.getTabAt(0)?.select()
                customTypeface?.let { applyTypefaceToTabLayout(modeTab, it) }
                reapplyTabThemesIfNeeded()
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {
                customTypeface?.let { applyTypefaceToTabLayout(modeTab, it) }
                reapplyTabThemesIfNeeded()
            }
            override fun onTabReselected(tab: TabLayout.Tab?) {
                customTypeface?.let { applyTypefaceToTabLayout(modeTab, it) }
            }
        })

        categoryTab.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                updateSymbolsForCategory(tab?.position ?: 0)
                customTypeface?.let { applyTypefaceToTabLayout(categoryTab, it) }
                reapplyTabThemesIfNeeded()
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {
                customTypeface?.let { applyTypefaceToTabLayout(categoryTab, it) }
                reapplyTabThemesIfNeeded()
            }
            override fun onTabReselected(tab: TabLayout.Tab?) {
                customTypeface?.let { applyTypefaceToTabLayout(categoryTab, it) }
            }
        })
    }

    /**
     * 動的にテーマカラーを適用するメソッド
     */
    fun setKeyboardTheme(
        @ColorInt backgroundColor: Int,
        @ColorInt iconColor: Int,
        @ColorInt selectedIconColor: Int,
        @ColorInt keyBackgroundColor: Int,
        liquidGlassEnable: Boolean,
    ) {
        this.themeBackgroundColor = backgroundColor
        this.themeIconColor = iconColor
        this.themeSelectedIconColor = selectedIconColor
        this.themeKeyBackgroundColor = keyBackgroundColor
        this.isCustomThemeApplied = true
        this.liquidGlassEnable = liquidGlassEnable

        // 1. 全体の背景色
        if (liquidGlassEnable) {
            this.setBackgroundColor(ColorUtils.setAlphaComponent(backgroundColor, 0))
        } else {
            this.setBackgroundColor(backgroundColor)
        }

        // 2. ColorStateList の作成（選択中を濃く、非選択を薄く）
        val states = arrayOf(
            intArrayOf(android.R.attr.state_selected),
            intArrayOf(),
        )
        val colors = intArrayOf(
            selectedIconColor,
            iconColor,
        )
        val tabColorStateList = ColorStateList(states, colors)
        val effectiveKeyColor = ensureKeyContrast(backgroundColor, keyBackgroundColor)
        val bgTintList = ColorStateList.valueOf(backgroundColor)
        val tabButtonRadius = dpToPx(8).toFloat()
        val auxiliaryButtonRadius = dpToPx(8).toFloat()

        // 3. Category Tab の全体設定
        categoryTab.backgroundTintList = bgTintList

        categoryTab.tabIconTint = tabColorStateList
        categoryTab.setTabTextColors(iconColor, selectedIconColor)
        categoryTab.setSelectedTabIndicatorColor(Color.TRANSPARENT)
        categoryTab.tabRippleColor = null // リップル削除

        // ★重要: ニューモーフィズムの影が切れないようにクリッピングを無効化
        disableClipping(categoryTab)

        // ★重要: タブの生成完了を待ってから背景を適用 (postを使用)
        categoryTab.post {
            applyThemeToTabs(categoryTab, effectiveKeyColor, tabButtonRadius)
        }

        // 4. Mode Tab (Bottom Bar) の全体設定
        modeTab.backgroundTintList = bgTintList
        modeTab.tabIconTint = tabColorStateList
        modeTab.setSelectedTabIndicatorColor(Color.TRANSPARENT)
        modeTab.tabRippleColor = null

        // ★重要: クリッピング無効化と遅延適用
        disableClipping(modeTab)
        modeTab.post {
            applyThemeToTabs(modeTab, effectiveKeyColor, tabButtonRadius)
        }

        // 5. 機能キー (Return/Delete) のニューモーフィズム設定
        val keyRadius = dpToPx(25).toFloat()
        returnButton.background = getTabNeumorphDrawable(effectiveKeyColor, keyRadius)
        deleteButton.background = getTabNeumorphDrawable(effectiveKeyColor, keyRadius)

        val p = dpToPx(8)
        returnButton.setPadding(p, p, p, p)
        deleteButton.setPadding(p, p, p, p)

        returnButton.setTextColor(iconColor)
        deleteButton.setColorFilter(iconColor, PorterDuff.Mode.SRC_IN)

        applyThemeToAuxiliaryControls(
            keyBackgroundColor = effectiveKeyColor,
            iconColor = iconColor,
            cornerRadius = auxiliaryButtonRadius,
        )

        if (currentMode == SymbolMode.CLIPBOARD) {
            buildCategoryTabs()
        }

        symbolAdapter.setThemeColors(
            textColor = selectedIconColor,
            highlightColor = selectedIconColor
        )
    }

    @ColorInt
    private fun ensureKeyContrast(@ColorInt panelColor: Int, @ColorInt keyColor: Int): Int {
        if (colorDistance(panelColor, keyColor) >= 40f) {
            return keyColor
        }
        return if (ColorUtils.calculateLuminance(panelColor) > 0.5) {
            manipulateColor(panelColor, 0.82f)
        } else {
            manipulateColor(panelColor, 1.18f)
        }
    }

    private fun colorDistance(@ColorInt left: Int, @ColorInt right: Int): Float {
        val lr = Color.red(left)
        val lg = Color.green(left)
        val lb = Color.blue(left)
        val rr = Color.red(right)
        val rg = Color.green(right)
        val rb = Color.blue(right)
        val dr = (lr - rr).toFloat()
        val dg = (lg - rg).toFloat()
        val db = (lb - rb).toFloat()
        return kotlin.math.sqrt(dr * dr + dg * dg + db * db)
    }

    private fun reapplyTabThemesIfNeeded() {
        if (!isCustomThemeApplied) return
        val effectiveKeyColor = ensureKeyContrast(themeBackgroundColor, themeKeyBackgroundColor)
        val tabButtonRadius = dpToPx(8).toFloat()
        applyThemeToTabs(categoryTab, effectiveKeyColor, tabButtonRadius)
        applyThemeToTabs(modeTab, effectiveKeyColor, tabButtonRadius)
    }

    private fun applyThemeToAuxiliaryControls(
        @ColorInt keyBackgroundColor: Int,
        @ColorInt iconColor: Int,
        cornerRadius: Float,
    ) {
        val buttonBackground = getTabNeumorphDrawable(keyBackgroundColor, cornerRadius)
        val hintColor = ColorUtils.setAlphaComponent(iconColor, 160)

        clipboardClearAllButton?.background = buttonBackground
        clipboardClearAllButton?.setColorFilter(iconColor, PorterDuff.Mode.SRC_IN)

        clipboardSearchView?.background = buttonBackground
        clipboardSearchView?.findViewById<android.widget.EditText>(
            androidx.appcompat.R.id.search_src_text
        )?.apply {
            setTextColor(iconColor)
            setHintTextColor(hintColor)
        }
        clipboardSearchView?.findViewById<android.widget.ImageView>(
            androidx.appcompat.R.id.search_mag_icon
        )?.setColorFilter(iconColor, PorterDuff.Mode.SRC_IN)
        clipboardSearchView?.findViewById<android.widget.ImageView>(
            androidx.appcompat.R.id.search_close_btn
        )?.setColorFilter(iconColor, PorterDuff.Mode.SRC_IN)

        emojiSearchView?.background = buttonBackground
        emojiSearchView?.findViewById<android.widget.EditText>(
            androidx.appcompat.R.id.search_src_text
        )?.apply {
            setTextColor(iconColor)
            setHintTextColor(hintColor)
        }
        emojiSearchView?.findViewById<android.widget.ImageView>(
            androidx.appcompat.R.id.search_mag_icon
        )?.setColorFilter(iconColor, PorterDuff.Mode.SRC_IN)
        emojiSearchView?.findViewById<android.widget.ImageView>(
            androidx.appcompat.R.id.search_close_btn
        )?.setColorFilter(iconColor, PorterDuff.Mode.SRC_IN)

        emojiKitchenResetButton?.background = buttonBackground
        emojiKitchenResetButton?.setTextColor(iconColor)
        emojiKitchenPreviewLabel?.setTextColor(iconColor)
    }

    /**
     * TabLayoutとその内部のSlidingTabStripのクリッピングを無効にする
     * これにより、領域外の「影」が描画されるようになります
     */
    private fun disableClipping(tabLayout: TabLayout) {
        tabLayout.clipChildren = false
        tabLayout.clipToPadding = false
        val slidingTabStrip = tabLayout.getChildAt(0) as? ViewGroup
        slidingTabStrip?.clipChildren = false
        slidingTabStrip?.clipToPadding = false
    }

    /**
     * TabLayout内のすべてのタブViewに対して、ニューモーフィズム背景とマージンを適用する
     */
    private fun applyThemeToTabs(
        tabLayout: TabLayout,
        @ColorInt buttonColor: Int,
        cornerRadius: Float,
    ) {
        val slidingTabStrip = tabLayout.getChildAt(0) as? ViewGroup ?: return

        for (i in 0 until slidingTabStrip.childCount) {
            val tabView = slidingTabStrip.getChildAt(i)

            // マージンを設定 (影のスペースを確保するため 4dp 程度確保)
            val params = tabView.layoutParams as? ViewGroup.MarginLayoutParams
            if (params != null) {
                val m = dpToPx(6) // 影(4dp) + 余白(2dp) で余裕を持たせる
                params.setMargins(m, m, m, m)
                tabView.layoutParams = params
            }

            // キー背景色でボタン面を描画（パネル背景色だと影が見えなくなる）
            tabView.backgroundTintList = null
            tabView.background = getTabNeumorphDrawable(
                baseColor = buttonColor,
                radius = cornerRadius,
                selectedAccentColor = if (isCustomThemeApplied) themeSelectedIconColor else null,
            )

            // パディング調整 (Drawable内のpaddingとは別に、Viewのコンテンツ位置調整)
            // TenKeyのロジックではDrawable自体がpaddingを持つため、View自体のpaddingは少なめでOK
            val p = dpToPx(4)
            tabView.setPadding(p, p, p, p)

            // 再描画要求
            tabView.invalidate()
        }
        tabLayout.requestLayout()
    }

    /**
     * TenKeyの getDynamicNeumorphDrawable と同等の実装
     */
    private fun getTabNeumorphDrawable(
        @ColorInt baseColor: Int,
        radius: Float,
        @ColorInt selectedAccentColor: Int? = null,
    ): Drawable {
        // 1. 色の計算 (TenKeyと同じ係数を使用)
        // ハイライト色: 明るくする
        val highlightColor = manipulateColor(baseColor, 1.25f)
        // シャドウ色: 暗くする
        val shadowColor = manipulateColor(baseColor, 0.75f)
        // 押下時の色: ベースより少し暗く
        val pressedColor = manipulateColor(baseColor, 0.92f)

        // 2. オフセット量とパディング (TenKeyの設定に合わせる)
        val density = resources.displayMetrics.density
        val offset = (4 * density).toInt() // 影のずれ幅
        val padding = (2 * density).toInt() // メイン面の縮小幅

        // --- A. 通常状態 (Idle) の作成 ---

        // レイヤー0: 暗い影 (右下に配置)
        val shadowDrawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            setColor(shadowColor)
        }

        // レイヤー1: 明るいハイライト (左上に配置)
        val highlightDrawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            setColor(highlightColor)
        }

        // レイヤー2: メインの面
        val surfaceDrawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            setColor(baseColor)
            val strokeColor = if (ColorUtils.calculateLuminance(baseColor) > 0.5) {
                manipulateColor(baseColor, 0.78f)
            } else {
                manipulateColor(baseColor, 1.22f)
            }
            setStroke((1 * density).toInt().coerceAtLeast(1), strokeColor)
        }

        // LayerDrawableで重ねる (下から順に描画)
        val idleLayer = LayerDrawable(arrayOf(shadowDrawable, highlightDrawable, surfaceDrawable))

        // インセット設定 (TenKeyと一致させる)
        // 影: 左と上を空けて、右下に表示
        idleLayer.setLayerInset(0, offset, offset, 0, 0)
        // ハイライト: 右と下を空けて、左上に表示
        idleLayer.setLayerInset(1, 0, 0, offset, offset)
        // メイン面: 全体にpaddingを入れて中央に配置
        idleLayer.setLayerInset(2, padding, padding, padding, padding)


        // --- B. 押下・選択状態 (Pressed / Selected) の作成 ---

        val selectedSurfaceDrawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            if (selectedAccentColor != null) {
                setColor(manipulateColor(baseColor, 0.88f))
                setStroke((2 * density).toInt().coerceAtLeast(2), selectedAccentColor)
            } else {
                setColor(pressedColor)
            }
        }

        // サイズが変わらないようにLayerDrawableにして同じInsetを与える
        val pressedLayer = LayerDrawable(arrayOf(selectedSurfaceDrawable))
        pressedLayer.setLayerInset(0, padding, padding, padding, padding)


        // --- C. StateListDrawable (Selector) にまとめる ---
        val stateListDrawable = StateListDrawable()

        // 選択中 (TabLayout用)
        stateListDrawable.addState(
            intArrayOf(android.R.attr.state_selected),
            pressedLayer
        )
        // 押下中 (ボタン用)
        stateListDrawable.addState(
            intArrayOf(android.R.attr.state_pressed),
            pressedLayer
        )
        // 通常時
        stateListDrawable.addState(
            intArrayOf(),
            idleLayer
        )

        return stateListDrawable
    }

    fun setOnDeleteButtonFingerUpListener(listener: () -> Unit) {
        onDeleteFingerUpListener = listener
    }

    private val gestureDetector = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent) = true

            private val SWIPE_DISTANCE_THRESHOLD = 30f
            private val SWIPE_VELOCITY_THRESHOLD = 100

            override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
                if (e1 == null) return false
                val dx = e2.x - e1.x
                val dy = e2.y - e1.y

                if (kotlin.math.abs(dx) > SWIPE_DISTANCE_THRESHOLD
                    && kotlin.math.abs(vx) > SWIPE_VELOCITY_THRESHOLD
                    && kotlin.math.abs(dx) > kotlin.math.abs(dy)
                ) {
                    if (dx > 0) selectPreviousCategory()
                    else selectNextCategory()
                    return true
                }
                return false
            }
        }
    )

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        val loc = IntArray(2).also { recycler.getLocationOnScreen(it) }
        val y = ev.rawY
        if (y >= loc[1] && y <= loc[1] + recycler.height) {
            gestureDetector.onTouchEvent(ev)
        }
        return super.dispatchTouchEvent(ev)
    }

    fun setLifecycleOwner(owner: LifecycleOwner) {
        lifecycleOwner = owner
    }

    fun setOnReturnToTenKeyButtonClickListener(l: ReturnToTenKeyButtonClickListener) {
        returnListener = l
    }

    fun setOnDeleteButtonSymbolViewClickListener(l: DeleteButtonSymbolViewClickListener) {
        deleteClickListener = l
    }

    fun setOnDeleteButtonSymbolViewLongClickListener(l: DeleteButtonSymbolViewLongClickListener) {
        deleteLongListener = l
    }

    fun setOnSymbolRecyclerViewItemClickListener(l: SymbolRecyclerViewItemClickListener) {
        itemClickListener = l
    }

    fun setOnSymbolRecyclerViewItemLongClickListener(l: SymbolRecyclerViewItemLongClickListener) {
        itemLongClickListener = l
    }

    fun setOnImageItemClickListener(l: ImageItemClickListener) {
        imageItemClickListener = l
    }

    fun setOnClipboardItemClickListener(l: (ClipboardItem) -> Unit) {
        clipboardItemClickListener = l
    }

    fun setOnClipboardItemLongClickListener(l: ClipboardItemLongClickListener) {
        clipboardItemLongClickListener = l
    }

    fun setOnEmojiSearchListener(listener: (String) -> Unit) {
        emojiSearchListener = listener
    }

    fun setOnSymbolPanelSearchFocusListener(listener: (Boolean) -> Unit) {
        symbolPanelSearchFocusListener = listener
    }

    fun isSymbolPanelSearchActive(): Boolean = activeSearchTarget != null

    fun appendActiveSearchText(text: String) {
        if (text.isEmpty()) return
        val searchView = activeSearchView() ?: return
        val updated = searchView.query?.toString().orEmpty() + text
        searchView.setQuery(updated, true)
    }

    fun deleteActiveSearchChar(): Boolean {
        val searchView = activeSearchView() ?: return false
        val current = searchView.query?.toString().orEmpty()
        if (current.isEmpty()) return false
        searchView.setQuery(current.dropLast(1), true)
        return true
    }

    fun clearSymbolPanelSearchFocus() {
        activeSearchTarget = null
        clipboardSearchView?.clearFocus()
        emojiSearchView?.clearFocus()
        symbolPanelSearchFocusListener?.invoke(false)
    }

    private fun activeSearchView(): androidx.appcompat.widget.SearchView? {
        return when (activeSearchTarget) {
            ActiveSearchTarget.CLIPBOARD -> clipboardSearchView
            ActiveSearchTarget.EMOJI -> emojiSearchView
            null -> null
        }
    }

    private fun setupSymbolSearchView(
        searchView: androidx.appcompat.widget.SearchView?,
        target: ActiveSearchTarget,
    ) {
        val editText = searchView?.findViewById<android.widget.EditText>(
            androidx.appcompat.R.id.search_src_text,
        ) ?: return
        editText.showSoftInputOnFocus = false
        editText.isFocusableInTouchMode = true
        val activate = {
            activeSearchTarget = target
            editText.requestFocus()
            symbolPanelSearchFocusListener?.invoke(true)
        }
        searchView.setOnClickListener { activate() }
        editText.setOnClickListener { activate() }
        editText.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                activate()
            } else if (activeSearchTarget == target) {
                activeSearchTarget = null
                symbolPanelSearchFocusListener?.invoke(false)
            }
        }
    }

    fun displayEmojiSearchResults(results: List<String>) {
        emojiSearchResults = results
        if (currentMode != SymbolMode.EMOJI) return
        val hasQuery = !emojiSearchView?.query.isNullOrBlank()
        categoryTab.visibility = if (hasQuery) View.GONE else View.VISIBLE
        refreshEmojiGrid()
    }

    private fun refreshEmojiGrid() {
        if (currentMode != SymbolMode.EMOJI) return
        updateSymbolsForCategory(categoryTab.selectedTabPosition)
    }

    fun setOnClipboardControlListener(onClearAll: () -> Unit, onSearch: (String) -> Unit) {
        this.clipboardClearAllListener = onClearAll
        this.clipboardSearchListener = onSearch
    }

    fun updateClipboardItems(newItems: List<ClipboardItem>) {
        this.clipBoardItems = newItems
        if (currentMode == SymbolMode.CLIPBOARD) {
            updateSymbolsForCategory(categoryTab.selectedTabPosition)
        }
    }

    fun setClipboardHistoryEnabled(isEnabled: Boolean) {
        this.isClipboardHistoryEnabled = isEnabled
    }

    fun setOnClipboardHistoryToggleListener(l: ClipboardHistoryToggleListener) {
        this.clipboardHistoryToggleListener = l
    }

    fun setOnDefaultEmojiSkinToneChangeListener(l: (String) -> Unit) {
        defaultEmojiSkinToneChangeListener = l
    }

    fun setDefaultEmojiSkinTone(skinTone: String) {
        defaultEmojiSkinTone =
            if (EmojiSkinToneSupport.isSupportedSkinToneValue(skinTone)) {
                skinTone
            } else {
                EmojiSkinToneSupport.DEFAULT_SKIN_TONE
            }
        if (currentMode == SymbolMode.EMOJI && !isHistoryCategorySelected()) {
            updateSymbolsForCategory(categoryTab.selectedTabPosition)
        }
    }

    private fun handleEmojiKitchenClick(char: String) {
        val context = context ?: return
        val manager = com.kazumaproject.symbol_keyboard.emoji_kitchen.EmojiKitchenManager
        val emojis = manager.getSupportedEmojis(context)
        val selected = emojis.find { emoji -> emoji.char == char || emoji.codepoint == charToCodepoint(char) } ?: return

        if (firstKitchenEmoji == null) {
            firstKitchenEmoji = selected
            buildCategoryTabs()
            updateSymbolsForCategory(0)
        } else if (secondKitchenEmoji == null) {
            secondKitchenEmoji = selected
            showEmojiKitchenPreview()
        }
    }

    private fun charToCodepoint(char: String): String {
        if (char.isEmpty()) return ""
        val codePoint = char.codePointAt(0)
        return Integer.toHexString(codePoint).lowercase()
    }

    private fun showEmojiKitchenPreview() {
        val context = context ?: return
        val first = firstKitchenEmoji ?: return
        val second = secondKitchenEmoji ?: return

        recycler.visibility = View.GONE
        emojiKitchenPreviewContainer?.visibility = View.VISIBLE

        val url = com.kazumaproject.symbol_keyboard.emoji_kitchen.EmojiKitchenManager.getStickerUrl(
            context, first.codepoint, second.codepoint
        )

        if (url != null) {
            emojiKitchenPreviewLabel?.text = "読み込み中..."
            lifecycleOwner?.let { owner ->
                owner.lifecycleScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                    val bitmap = com.kazumaproject.symbol_keyboard.emoji_kitchen.EmojiKitchenManager.downloadSticker(url)
                    if (bitmap != null) {
                        generatedStickerBitmap = bitmap
                        emojiKitchenPreviewImage?.setImageBitmap(bitmap)
                        emojiKitchenPreviewLabel?.text = "合成完了！タップして送信"
                    } else {
                        emojiKitchenPreviewLabel?.text = "ダウンロード失敗"
                    }
                }
            }
        } else {
            emojiKitchenPreviewLabel?.text = "組み合わせがありません"
        }
    }

    private fun resetEmojiKitchenState() {
        firstKitchenEmoji = null
        secondKitchenEmoji = null
        generatedStickerBitmap = null
        emojiKitchenPreviewImage?.setImageBitmap(null)
        emojiKitchenPreviewContainer?.visibility = View.GONE
        recycler.visibility = View.VISIBLE

        if (currentMode == SymbolMode.EMOJI_KITCHEN) {
            buildCategoryTabs()
            updateSymbolsForCategory(0)
        }
    }

    fun setSymbolLists(
        emojiList: List<Emoji>,
        emoticons: List<Emoticon>,
        symbols: List<Symbol>,
        clipBoardItems: List<ClipboardItem>,
        symbolsHistory: List<ClickedSymbol>,
        symbolMode: SymbolMode = SymbolMode.EMOJI,
        defaultEmojiSkinTone: String = EmojiSkinToneSupport.DEFAULT_SKIN_TONE
    ) {
        this.symbolsHistory = symbolsHistory
        this.clipBoardItems = clipBoardItems
        this.defaultEmojiSkinTone =
            if (EmojiSkinToneSupport.isSupportedSkinToneValue(defaultEmojiSkinTone)) {
                defaultEmojiSkinTone
            } else {
                EmojiSkinToneSupport.DEFAULT_SKIN_TONE
            }

        historyEmojiList = symbolsHistory
            .filter { it.mode == SymbolMode.EMOJI }
            .map { it.symbol }
            .toMutableList()
        historyEmoticonList = symbolsHistory
            .filter { it.mode == SymbolMode.EMOTICON }
            .map { it.symbol }
            .toMutableList()
        historySymbolList = symbolsHistory
            .filter { it.mode == SymbolMode.SYMBOL }
            .map { it.symbol }
            .toMutableList()

        this.emoticonMap = emoticons
            .groupBy { it.category }
            .mapValues { entry -> entry.value.map { it.symbol } }
        this.symbolMap = symbols
            .groupBy { it.category }
            .mapValues { entry -> entry.value.map { it.symbol } }
        this.emojiMap = emojiList
            .groupBy { it.category }
            .toSortedMap(categoryOrder)

        currentMode = symbolMode
        buildModeTabs()
        buildCategoryTabs()
        modeTab.getTabAt(symbolMode.ordinal)?.select()
        categoryTab.getTabAt(0)?.select()
        updateSymbolsForCategory(0)
    }

    private fun buildModeTabs() {
        modeTab.removeAllTabs()
        listOf(
            com.kazumaproject.core.R.drawable.mood_24px,
            com.kazumaproject.core.R.drawable.emoticon_24px,
            com.kazumaproject.core.R.drawable.star_24px,
            com.kazumaproject.core.R.drawable.clip_board,
        ).forEach { res ->
            modeTab.addTab(modeTab.newTab().setIcon(res))
        }

        // ★ テーマ適用フラグが立っている場合、タブ再構築後にテーマを適用
        if (isCustomThemeApplied) {
            modeTab.post {
                val effectiveKeyColor = ensureKeyContrast(themeBackgroundColor, themeKeyBackgroundColor)
                applyThemeToTabs(modeTab, effectiveKeyColor, dpToPx(8).toFloat())
            }
        }
        customTypeface?.let { applyTypefaceToTabLayout(modeTab, it) }
    }

    private fun buildCategoryTabs() {
        categoryTab.removeAllTabs()
        val historyIcon = com.kazumaproject.core.R.drawable.history_24dp

        val normalColor = themeIconColor
        val selectedColor = themeSelectedIconColor

        categoryTab.setTabTextColors(normalColor, selectedColor)
        categoryTab.setSelectedTabIndicatorColor(if (isCustomThemeApplied) Color.TRANSPARENT else selectedColor)

        val states = arrayOf(
            intArrayOf(android.R.attr.state_selected),
            intArrayOf(),
        )
        val colors = intArrayOf(
            selectedColor,
            normalColor,
        )
        val tabColorStateList = ColorStateList(states, colors)
        categoryTab.tabIconTint = tabColorStateList

        when (currentMode) {
            SymbolMode.EMOJI -> {
                if (historyEmojiList.isNotEmpty()) {
                    categoryTab.addTab(categoryTab.newTab().setIcon(historyIcon))
                }
                emojiMap.keys.forEach { cat ->
                    categoryTab.addTab(
                        categoryTab.newTab().setIcon(
                            categoryIconRes[cat] ?: com.kazumaproject.core.R.drawable.logo_key
                        )
                    )
                }
            }

            SymbolMode.EMOTICON -> {
                if (historyEmoticonList.isNotEmpty()) {
                    categoryTab.addTab(categoryTab.newTab().setIcon(historyIcon))
                }
                val orderedKeys = EmoticonCategory.entries
                orderedKeys.forEach { category ->
                    if (emoticonMap.containsKey(category)) {
                        val tabText = when (category) {
                            EmoticonCategory.SMILE -> "笑顔"
                            EmoticonCategory.SWEAT -> "焦っている顔"
                            EmoticonCategory.SURPRISE -> "驚いている顔"
                            EmoticonCategory.SADNESS -> "泣いている顔"
                            EmoticonCategory.DISPLEASURE -> "不満げな顔"
                            EmoticonCategory.UNKNOWN -> "その他"
                        }
                        categoryTab.addTab(categoryTab.newTab().setText(tabText))
                    }
                }
            }

            SymbolMode.SYMBOL -> {
                if (historySymbolList.isNotEmpty()) {
                    categoryTab.addTab(categoryTab.newTab().setIcon(historyIcon))
                }
                val orderedKeys = SymbolCategory.entries
                orderedKeys.forEach { category ->
                    if (symbolMap.containsKey(category)) {
                        val tabText = when (category) {
                            SymbolCategory.BRACKETS_AND_QUOTES -> "括弧と引用符"
                            SymbolCategory.PUNCTUATION_AND_DIACRITICS -> "区切り文字と発音区別符号"
                            SymbolCategory.Hankaku -> "半角"
                            SymbolCategory.GENERAL -> "全般"
                            SymbolCategory.ARROWS -> "矢印"
                            SymbolCategory.MATH_AND_UNITS -> "数学と単位"
                            SymbolCategory.GEOMETRIC_SHAPES -> "図形"
                            SymbolCategory.ALPHABET_LATIN -> "ラテン文字"
                            SymbolCategory.ALPHABET_GREEK -> "ギリシャ文字"
                            SymbolCategory.ALPHABET_CYRILLIC -> "キリル文字"
                            SymbolCategory.BOX_DRAWING -> "罫線"
                            SymbolCategory.PICTOGRAPHS_AND_ICONS -> "アイコン"
                            SymbolCategory.ROMAN_NUMERALS -> "ローマ数字"
                            SymbolCategory.ENCLOSED_CHARACTERS -> "囲み文字"
                            SymbolCategory.PHONETIC_SYMBOLS -> "発音記号"
                            SymbolCategory.JAPANESE_KANA_AND_VARIANTS -> "日本語仮名・特殊文字"
                            SymbolCategory.CJK_AND_RADICALS -> "CJK・部首"
                            SymbolCategory.CONTROL_CHARACTERS -> "制御文字"
                        }
                        categoryTab.addTab(categoryTab.newTab().setText(tabText))
                    }
                }
            }

            SymbolMode.CLIPBOARD -> {
                val tab = categoryTab.newTab().setCustomView(R.layout.custom_tab_clipboard)
                categoryTab.addTab(tab)
                tab.customView?.let { customView ->
                    customView.findViewById<TextView>(R.id.clipboard_tab_text)
                        .setTextColor(selectedColor)
                }
            }

            SymbolMode.EMOJI_KITCHEN -> {
                val tabText = if (firstKitchenEmoji == null) "1つ目を選択" else "2つ目を選択"
                categoryTab.addTab(categoryTab.newTab().setText(tabText))
            }
        }

        // ★ テーマ適用フラグが立っている場合、タブ再構築後にテーマを適用
        if (isCustomThemeApplied) {
            categoryTab.post {
                val effectiveKeyColor = ensureKeyContrast(themeBackgroundColor, themeKeyBackgroundColor)
                applyThemeToTabs(categoryTab, effectiveKeyColor, dpToPx(8).toFloat())
            }
        }
        customTypeface?.let { applyTypefaceToTabLayout(categoryTab, it) }
    }

    private fun buildClipboardListItems(items: List<ClipboardItem>): List<ClipboardListItem> {
        val pinned = items.filter { it.isPinned() }
        val unpinned = items.filterNot { it.isPinned() }
        return buildList {
            if (pinned.isNotEmpty()) {
                add(ClipboardListItem.Header(resources.getString(R.string.symbol_clipboard_section_pinned)))
                addAll(pinned.map { ClipboardListItem.Content(it) })
            }
            if (unpinned.isNotEmpty()) {
                add(ClipboardListItem.Header(resources.getString(R.string.symbol_clipboard_section_unpinned)))
                addAll(unpinned.map { ClipboardListItem.Content(it) })
            }
        }
    }

    private fun ClipboardItem.isPinned(): Boolean {
        return when (this) {
            is ClipboardItem.Image -> isPinned
            is ClipboardItem.Text -> isPinned
            ClipboardItem.Empty -> false
        }
    }

    private fun updateSymbolsForCategory(index: Int) {
        when (currentMode) {
            SymbolMode.CLIPBOARD -> {
                clipboardControlLayout?.visibility = View.VISIBLE
                emojiControlLayout?.visibility = View.GONE
                emojiSearchView?.setQuery("", false)
                categoryTab.visibility = View.GONE
            }
            SymbolMode.EMOJI -> {
                clipboardControlLayout?.visibility = View.GONE
                clipboardSearchView?.setQuery("", false)
                emojiControlLayout?.visibility = View.VISIBLE
                val hasQuery = !emojiSearchView?.query.isNullOrBlank()
                categoryTab.visibility = if (hasQuery) View.GONE else View.VISIBLE
            }
            else -> {
                clearSymbolPanelSearchFocus()
                clipboardControlLayout?.visibility = View.GONE
                emojiControlLayout?.visibility = View.GONE
                clipboardSearchView?.setQuery("", false)
                emojiSearchView?.setQuery("", false)
                emojiSearchResults = null
                categoryTab.visibility = View.VISIBLE
            }
        }

        skinTonePopup?.dismiss()
        pagingJob?.cancel()
        lifecycleOwner?.let { owner ->
            pagingJob = owner.lifecycleScope.launch {
                when (currentMode) {
                    SymbolMode.CLIPBOARD -> {
                        recycler.adapter = clipboardAdapter
                        gridLM.spanCount =
                            if (resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) 2 else 4
                        gridLM.orientation = RecyclerView.VERTICAL
                        gridLM.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                            override fun getSpanSize(position: Int): Int {
                                return if (clipboardAdapter.isHeader(position)) gridLM.spanCount else 1
                            }
                        }
                        val clipboardListItems = buildClipboardListItems(clipBoardItems)
                        recycler.scrollToPosition(0)
                        Pager(
                            config = PagingConfig(pageSize = 20, enablePlaceholders = false),
                            pagingSourceFactory = { ClipboardPagingSource(clipboardListItems) }
                        ).flow.collectLatest { clipboardAdapter.submitData(it) }
                    }

                    else -> {
                        recycler.adapter = symbolAdapter
                        gridLM.spanSizeLookup = GridLayoutManager.DefaultSpanSizeLookup()
                        val listForPaging = when (currentMode) {
                            SymbolMode.EMOJI -> {
                                val searchResults = emojiSearchResults
                                if (!searchResults.isNullOrEmpty()) {
                                    searchResults.map { symbol ->
                                        EmojiSkinToneSupport.withSkinTone(
                                            symbol,
                                            this@CustomSymbolKeyboardView.defaultEmojiSkinTone,
                                        )
                                    }
                                } else if (!emojiSearchView?.query.isNullOrBlank()) {
                                    emptyList()
                                } else {
                                    val hasHistory = historyEmojiList.isNotEmpty()
                                    if (hasHistory && index == 0) historyEmojiList
                                    else {
                                        val adj = index - if (hasHistory) 1 else 0
                                        emojiMap.keys.elementAtOrNull(adj)
                                            ?.let {
                                                emojiMap[it]?.map { e ->
                                                    EmojiSkinToneSupport.withSkinTone(
                                                        e.symbol,
                                                        this@CustomSymbolKeyboardView.defaultEmojiSkinTone
                                                    )
                                                }
                                            } ?: emptyList()
                                    }
                                }
                            }

                            SymbolMode.EMOTICON -> {
                                val hasHistory = historyEmoticonList.isNotEmpty()
                                if (hasHistory && index == 0) historyEmoticonList
                                else {
                                    val adj = index - if (hasHistory) 1 else 0
                                    val orderedKeys = EmoticonCategory.entries
                                        .filter { emoticonMap.containsKey(it) }
                                    orderedKeys.elementAtOrNull(adj)?.let { emoticonMap[it] }
                                        ?: emptyList()
                                }
                            }

                            SymbolMode.SYMBOL -> {
                                val hasHistory = historySymbolList.isNotEmpty()
                                if (hasHistory && index == 0) historySymbolList
                                else {
                                    val adj = index - if (hasHistory) 1 else 0
                                    val orderedKeys = SymbolCategory.entries
                                        .filter { symbolMap.containsKey(it) }
                                    orderedKeys.elementAtOrNull(adj)?.let { symbolMap[it] }
                                        ?: emptyList()
                                }
                            }

                            SymbolMode.EMOJI_KITCHEN -> {
                                val emojis = com.kazumaproject.symbol_keyboard.emoji_kitchen.EmojiKitchenManager.getSupportedEmojis(context)
                                if (firstKitchenEmoji == null) {
                                    emojis.map { emoji -> emoji.char }
                                } else {
                                    com.kazumaproject.symbol_keyboard.emoji_kitchen.EmojiKitchenManager.getCombinableEmojis(context, firstKitchenEmoji!!.codepoint).map { emoji -> emoji.char }
                                }
                            }

                            else -> emptyList()
                        }

                        when (currentMode) {
                            SymbolMode.EMOJI,
                            SymbolMode.EMOJI_KITCHEN -> symbolAdapter.setItemMargins(2, 1, context)
                            SymbolMode.EMOTICON -> symbolAdapter.setItemMargins(6, 5, context)
                            SymbolMode.SYMBOL -> symbolAdapter.setItemMargins(8, 5, context)
                            else -> symbolAdapter.setItemMargins(3, 2, context)
                        }

                        symbolAdapter.showSkinToneIndicators =
                            currentMode == SymbolMode.EMOJI && !isHistoryCategorySelected()

                        symbolAdapter.symbolTextSize = when (currentMode) {
                            SymbolMode.EMOJI -> {
                                if (resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) 31f else 27f
                            }

                            SymbolMode.EMOJI_KITCHEN -> {
                                if (resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) 31f else 27f
                            }

                            SymbolMode.EMOTICON -> 14f
                            SymbolMode.SYMBOL -> 13f
                            SymbolMode.CLIPBOARD -> 16f
                        }

                        gridLM.spanCount = when (currentMode) {
                            SymbolMode.EMOJI,
                            SymbolMode.EMOJI_KITCHEN -> adaptiveGridSpan(targetCellDp = 43, min = 8, max = 14)
                            SymbolMode.EMOTICON -> adaptiveGridSpan(targetCellDp = 100, min = 3, max = 6)
                            SymbolMode.SYMBOL -> adaptiveGridSpan(targetCellDp = 58, min = 6, max = 10)
                            else -> adaptiveGridSpan(targetCellDp = 64, min = 5, max = 9)
                        }
                        gridLM.orientation = RecyclerView.VERTICAL
                        recycler.scrollToPosition(0)

                        Pager(
                            config = PagingConfig(pageSize = 100, enablePlaceholders = false),
                            pagingSourceFactory = { SymbolPagingSource(listForPaging) }
                        ).flow.collectLatest { symbolAdapter.submitData(it) }
                    }
                }
            }
        }
    }

    private fun showSkinTonePopup(symbol: String, anchor: View) {
        val variants = EmojiSkinToneSupport.skinToneVariants(symbol)
        if (variants.isEmpty() || !anchor.isAttachedToWindow) return

        skinTonePopup?.dismiss()

        val content = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dpToPx(6), dpToPx(5), dpToPx(6), dpToPx(5))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(12).toFloat()
                setColor(themeKeyBackgroundColor)
            }
        }

        variants.forEach { variant ->
            content.addView(
                TextView(context).apply {
                    text = variant
                    textSize =
                        if (resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) 30f else 26f
                    gravity = android.view.Gravity.CENTER
                    includeFontPadding = false
                    setTextColor(themeIconColor)
                    layoutParams = LinearLayout.LayoutParams(dpToPx(44), dpToPx(44))
                    setOnClickListener {
                        skinTonePopup?.dismiss()
                        updateDefaultEmojiSkinTone(
                            EmojiSkinToneSupport.skinToneValueFromEmoji(variant)
                        )
                        itemClickListener?.onClick(
                            ClickedSymbol(mode = SymbolMode.EMOJI, symbol = variant)
                        )
                    }
                }
            )
        }

        content.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )

        skinTonePopup = PopupWindow(
            content,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            false
        ).apply {
            isOutsideTouchable = true
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            elevation = dpToPx(8).toFloat()
            showAsDropDown(
                anchor,
                (anchor.width - content.measuredWidth) / 2,
                -anchor.height - content.measuredHeight
            )
        }
    }

    private fun updateDefaultEmojiSkinTone(skinTone: String) {
        val supportedSkinTone =
            if (EmojiSkinToneSupport.isSupportedSkinToneValue(skinTone)) {
                skinTone
            } else {
                EmojiSkinToneSupport.DEFAULT_SKIN_TONE
            }
        if (defaultEmojiSkinTone == supportedSkinTone) return

        defaultEmojiSkinTone = supportedSkinTone
        defaultEmojiSkinToneChangeListener?.invoke(supportedSkinTone)
        if (currentMode == SymbolMode.EMOJI && !isHistoryCategorySelected()) {
            updateSymbolsForCategory(categoryTab.selectedTabPosition)
        }
    }

    private fun isHistoryCategorySelected(): Boolean {
        return when (currentMode) {
            SymbolMode.EMOJI -> historyEmojiList.isNotEmpty() && categoryTab.selectedTabPosition == 0
            SymbolMode.EMOTICON -> historyEmoticonList.isNotEmpty() && categoryTab.selectedTabPosition == 0
            SymbolMode.SYMBOL -> historySymbolList.isNotEmpty() && categoryTab.selectedTabPosition == 0
            SymbolMode.CLIPBOARD -> false
            SymbolMode.EMOJI_KITCHEN -> false
        }
    }

    private fun selectPreviousCategory() {
        val i = categoryTab.selectedTabPosition
        if (i > 0) categoryTab.getTabAt(i - 1)?.select()
    }

    private fun selectNextCategory() {
        val i = categoryTab.selectedTabPosition
        val last = categoryTab.tabCount - 1
        if (i < last) categoryTab.getTabAt(i + 1)?.select()
    }

    /**
     * TenKeyと同じ色計算ロジック
     * @param color 元の色
     * @param factor 1.0より大＝明るく、1.0より小＝暗く
     */
    private fun manipulateColor(color: Int, factor: Float): Int {
        val a = Color.alpha(color)
        val r = (Color.red(color) * factor).toInt().coerceIn(0, 255)
        val g = (Color.green(color) * factor).toInt().coerceIn(0, 255)
        val b = (Color.blue(color) * factor).toInt().coerceIn(0, 255)
        return Color.argb(a, r, g, b)
    }

    fun release() {
        skinTonePopup?.dismiss()
        skinTonePopup = null
        pagingJob?.cancel()
        pagingJob = null
        lifecycleOwner = null
        returnListener = null
        deleteClickListener = null
        deleteLongListener = null
        itemClickListener = null
        itemLongClickListener = null
        imageItemClickListener = null
        clipboardItemClickListener = null
        clipboardItemLongClickListener = null
        clipboardHistoryToggleListener = null
        defaultEmojiSkinToneChangeListener = null
    }

    private fun dpToPx(dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp.toFloat(),
            resources.displayMetrics
        ).toInt()
    }

    private fun adaptiveGridSpan(targetCellDp: Int, min: Int, max: Int): Int {
        val availableWidthPx = recycler.width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels
        val availableWidthDp = availableWidthPx / resources.displayMetrics.density
        return (availableWidthDp / targetCellDp).toInt().coerceIn(min, max)
    }

    private val categoryIconRes = mapOf(
        EmojiCategory.EMOTICONS to com.kazumaproject.core.R.drawable.mood_24px,
        EmojiCategory.GESTURES to com.kazumaproject.core.R.drawable.thumb_up_24dp,
        EmojiCategory.PEOPLE_BODY to com.kazumaproject.core.R.drawable.person_24dp,
        EmojiCategory.ANIMALS_NATURE to com.kazumaproject.core.R.drawable.pets_24dp,
        EmojiCategory.FOOD_DRINK to com.kazumaproject.core.R.drawable.fastfood_24dp,
        EmojiCategory.TRAVEL_PLACES to com.kazumaproject.core.R.drawable.travel_explore_24dp,
        EmojiCategory.ACTIVITIES to com.kazumaproject.core.R.drawable.celebration_24dp,
        EmojiCategory.OBJECTS to com.kazumaproject.core.R.drawable.lightbulb_24dp,
        EmojiCategory.SYMBOLS to com.kazumaproject.core.R.drawable.emoji_symbols,
        EmojiCategory.FLAGS to com.kazumaproject.core.R.drawable.flag_24dp,
        EmojiCategory.UNKNOWN to com.kazumaproject.core.R.drawable.question_mark_24dp
    )

    private val categoryOrder = Comparator<EmojiCategory> { a, b ->
        listOf(
            EmojiCategory.EMOTICONS,
            EmojiCategory.GESTURES,
            EmojiCategory.PEOPLE_BODY,
            EmojiCategory.ANIMALS_NATURE,
            EmojiCategory.FOOD_DRINK,
            EmojiCategory.TRAVEL_PLACES,
            EmojiCategory.ACTIVITIES,
            EmojiCategory.OBJECTS,
            EmojiCategory.SYMBOLS,
            EmojiCategory.FLAGS,
            EmojiCategory.UNKNOWN
        ).indexOf(a).compareTo(
            listOf(
                EmojiCategory.EMOTICONS,
                EmojiCategory.GESTURES,
                EmojiCategory.PEOPLE_BODY,
                EmojiCategory.ANIMALS_NATURE,
                EmojiCategory.FOOD_DRINK,
                EmojiCategory.TRAVEL_PLACES,
                EmojiCategory.ACTIVITIES,
                EmojiCategory.OBJECTS,
                EmojiCategory.SYMBOLS,
                EmojiCategory.FLAGS,
                EmojiCategory.UNKNOWN
            ).indexOf(b)
        )
    }

    fun switchToClipboardMode(selectCategoryIndex: Int = 0) {
        // Modeを切り替え
        currentMode = SymbolMode.CLIPBOARD

        // ModeTab / CategoryTab を作り直す（CLIPBOARD用タブを生成するため）
        buildModeTabs()
        buildCategoryTabs()

        // UI上の選択状態も同期
        modeTab.getTabAt(SymbolMode.CLIPBOARD.ordinal)?.select()

        // CLIPBOARDのカテゴリは基本1つなので 0 を選択
        categoryTab.getTabAt(selectCategoryIndex.coerceIn(0, categoryTab.tabCount - 1))?.select()
    }

    fun switchToSymbolMode(mode: SymbolMode, selectCategoryIndex: Int = 0) {
        currentMode = mode
        buildModeTabs()
        buildCategoryTabs()
        modeTab.getTabAt(mode.ordinal)?.select()
        categoryTab.getTabAt(selectCategoryIndex.coerceIn(0, categoryTab.tabCount - 1))?.select()
    }

    inner class EmojiKitchenStickerAdapter : RecyclerView.Adapter<EmojiKitchenStickerAdapter.StickerViewHolder>() {
        private var items: List<android.graphics.Bitmap> = emptyList()

        fun submitList(newItems: List<android.graphics.Bitmap>) {
            items = newItems
            notifyDataSetChanged()
        }

        inner class StickerViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val imageView: ShapeableImageView = view as ShapeableImageView
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StickerViewHolder {
            val imageView = ShapeableImageView(parent.context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    dpToPx(64),
                    dpToPx(64)
                )
                val p = dpToPx(4)
                setPadding(p, p, p, p)
                scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
                val shapeAppearanceModel = com.google.android.material.shape.ShapeAppearanceModel.builder()
                    .setAllCornerSizes(dpToPx(8).toFloat())
                    .build()
                setShapeAppearanceModel(shapeAppearanceModel)
                background = getTabNeumorphDrawable(themeKeyBackgroundColor, dpToPx(8).toFloat())
            }
            return StickerViewHolder(imageView)
        }

        override fun onBindViewHolder(holder: StickerViewHolder, position: Int) {
            val bitmap = items[position]
            holder.imageView.setImageBitmap(bitmap)
            holder.imageView.setOnClickListener {
                imageItemClickListener?.onImageClick(bitmap)
                emojiKitchenRecyclerView?.visibility = View.GONE
            }
        }

        override fun getItemCount() = items.size
    }

    private fun tryToTriggerGboardEmojiKitchen(char: String) {
        emojiKitchenRecyclerView?.visibility = View.GONE
    }

    private var customTypeface: android.graphics.Typeface? = null

    fun setCustomTypeface(typeface: android.graphics.Typeface?) {
        this.customTypeface = typeface
        
        returnButton.typeface = typeface
        symbolAdapter.setCustomTypeface(typeface)
        clipboardAdapter.setCustomTypeface(typeface)
        applyTypefaceToSearchView(typeface)
        
        emojiKitchenPreviewLabel?.typeface = typeface
        emojiKitchenResetButton?.typeface = typeface
        
        applyTypefaceToTabLayout(categoryTab, typeface)
        applyTypefaceToTabLayout(modeTab, typeface)
    }

    private fun applyTypefaceToSearchView(typeface: android.graphics.Typeface?) {
        val searchView = clipboardSearchView ?: return
        fun applyToChildren(view: View) {
            if (view is TextView) {
                view.typeface = typeface
            }
            if (view is ViewGroup) {
                for (i in 0 until view.childCount) {
                    applyToChildren(view.getChildAt(i))
                }
            }
        }
        searchView.post { applyToChildren(searchView) }
    }

    private fun applyTypefaceToTabLayout(tabLayout: TabLayout, typeface: android.graphics.Typeface?) {
        fun applyToChildren(view: View) {
            if (view is TextView) {
                view.typeface = typeface
                view.includeFontPadding = false
            }
            if (view is ViewGroup) {
                for (i in 0 until view.childCount) {
                    applyToChildren(view.getChildAt(i))
                }
            }
        }
        tabLayout.post {
            val slidingTabStrip = tabLayout.getChildAt(0) as? ViewGroup ?: return@post
            for (i in 0 until slidingTabStrip.childCount) {
                applyToChildren(slidingTabStrip.getChildAt(i))
            }
        }
        tabLayout.postDelayed({
            val slidingTabStrip = tabLayout.getChildAt(0) as? ViewGroup ?: return@postDelayed
            for (i in 0 until slidingTabStrip.childCount) {
                applyToChildren(slidingTabStrip.getChildAt(i))
            }
        }, 32L)
    }

}
