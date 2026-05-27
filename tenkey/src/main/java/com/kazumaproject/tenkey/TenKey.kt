package com.kazumaproject.tenkey

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Build
import android.text.SpannableString
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import android.text.style.ReplacementSpan
import android.util.AttributeSet
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.PopupWindow
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.AppCompatImageButton
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.view.setPadding
import androidx.core.widget.ImageViewCompat
import com.google.android.material.textview.MaterialTextView
import com.kazumaproject.core.domain.extensions.hide
import com.kazumaproject.core.domain.extensions.layoutXPosition
import com.kazumaproject.core.domain.extensions.layoutYPosition
import com.kazumaproject.core.domain.extensions.setBorder
import com.kazumaproject.core.domain.extensions.setDrawableAlpha
import com.kazumaproject.core.domain.extensions.setDrawableSolidColor
import com.kazumaproject.core.domain.key.Key
import com.kazumaproject.core.domain.key.KeyInfo
import com.kazumaproject.core.domain.key.KeyMap
import com.kazumaproject.core.domain.key.KeyRect
import com.kazumaproject.core.domain.listener.FlickListener
import com.kazumaproject.core.domain.listener.LongPressListener
import com.kazumaproject.core.domain.state.GestureType
import com.kazumaproject.core.domain.state.InputMode
import com.kazumaproject.core.domain.state.InputMode.ModeEnglish.next
import com.kazumaproject.core.domain.state.PressedKey
import com.kazumaproject.core.data.popup.PopupViewStyle
import com.kazumaproject.core.ui.effect.Blur
import com.kazumaproject.core.ui.input_mode_witch.InputModeSwitch
import com.kazumaproject.core.ui.key_window.KeyWindowLayout
import com.kazumaproject.tenkey.databinding.KeyboardLayoutBinding
import com.kazumaproject.tenkey.databinding.PopupLayoutActiveBinding
import com.kazumaproject.tenkey.databinding.PopupLayoutActiveMaterialBinding
import com.kazumaproject.tenkey.databinding.PopupLayoutActiveMaterialLightBinding
import com.kazumaproject.tenkey.databinding.PopupLayoutBinding
import com.kazumaproject.tenkey.databinding.PopupLayoutMaterialBinding
import com.kazumaproject.tenkey.databinding.PopupLayoutMaterialLightBinding
import com.kazumaproject.tenkey.extensions.setPopUpWindowBottom
import com.kazumaproject.tenkey.extensions.setPopUpWindowCenter
import com.kazumaproject.tenkey.extensions.setPopUpWindowFlickBottom
import com.kazumaproject.tenkey.extensions.setPopUpWindowFlickLeft
import com.kazumaproject.tenkey.extensions.setPopUpWindowFlickRight
import com.kazumaproject.tenkey.extensions.setPopUpWindowFlickTop
import com.kazumaproject.tenkey.extensions.setPopUpWindowLeft
import com.kazumaproject.tenkey.extensions.setPopUpWindowRight
import com.kazumaproject.tenkey.extensions.setPopUpWindowTop
import com.kazumaproject.tenkey.extensions.setTenKeyTextEnglish
import com.kazumaproject.tenkey.extensions.setTenKeyTextJapanese
import com.kazumaproject.tenkey.extensions.setTenKeyTextJapaneseWithFlickGuide
import com.kazumaproject.tenkey.extensions.setTenKeyTextNumber
import com.kazumaproject.tenkey.extensions.setTenKeyTextWhenTapEnglish
import com.kazumaproject.tenkey.extensions.setTenKeyTextWhenTapJapanese
import com.kazumaproject.tenkey.extensions.setTenKeyTextWhenTapNumber
import com.kazumaproject.tenkey.extensions.setTextFlickBottomEnglish
import com.kazumaproject.tenkey.extensions.setTextFlickBottomJapanese
import com.kazumaproject.tenkey.extensions.setTextFlickBottomNumber
import com.kazumaproject.tenkey.extensions.setTextFlickLeftEnglish
import com.kazumaproject.tenkey.extensions.setTextFlickLeftJapanese
import com.kazumaproject.tenkey.extensions.setTextFlickLeftNumber
import com.kazumaproject.tenkey.extensions.setTextFlickRightEnglish
import com.kazumaproject.tenkey.extensions.setTextFlickRightJapanese
import com.kazumaproject.tenkey.extensions.setTextFlickRightNumber
import com.kazumaproject.tenkey.extensions.setTextFlickTopEnglish
import com.kazumaproject.tenkey.extensions.setTextFlickTopJapanese
import com.kazumaproject.tenkey.extensions.setTextFlickTopNumber
import com.kazumaproject.tenkey.extensions.setTextTapEnglish
import com.kazumaproject.tenkey.extensions.setTextTapJapanese
import com.kazumaproject.tenkey.extensions.setTextTapNumber
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

@SuppressLint("ClickableViewAccessibility")
class TenKey(context: Context, attributeSet: AttributeSet) :
    ConstraintLayout(context, attributeSet), View.OnTouchListener {

    private data class BaseMargins(
        val start: Int,
        val top: Int,
        val end: Int,
        val bottom: Int
    )

    // ViewBinding for the main keyboard layout
    private val binding: KeyboardLayoutBinding

    // KeyMap to decide which character to send on tap/flick
    private var keyMap: KeyMap

    // For handling long-press detection
    private var longPressJob: Job? = null
    private var isLongPressed = false
    private var touchSequenceActive = false

    // Track which key is currently pressed
    private lateinit var pressedKey: PressedKey

    // External listeners
    private var flickListener: FlickListener? = null
    private var longPressListener: LongPressListener? = null
    private var inputModeChangedListener: ((InputMode) -> Unit)? = null

    private var flickSensitivity: Int = 100
    private var longPressTimeout: Long = ViewConfiguration.getLongPressTimeout().toLong()

    private var keySizeDelta = 0

    private var isLanguageIconEnabled = true

    /** ← REPLACED AtomicReference with StateFlow **/
    private val _currentInputMode = MutableStateFlow<InputMode>(InputMode.ModeJapanese)
    val currentInputMode: StateFlow<InputMode> = _currentInputMode

    // Popups: active (center) and directional
    private lateinit var popupWindowActive: PopupWindow
    private lateinit var bubbleViewActive: KeyWindowLayout
    private lateinit var popTextActive: MaterialTextView
    private var activePopupButtonId: Int = View.NO_ID
    private var activePopupGestureType: GestureType = GestureType.Null
    private var activePopupText: CharSequence = ""
    private lateinit var screenshotPopupView: KeyWindowLayout
    private lateinit var screenshotPopupText: MaterialTextView
    private lateinit var screenshotPopupWindow: PopupWindow
    private var popupWindowAnchorProvider: (() -> View?)? = null

    private lateinit var popupWindowLeft: PopupWindow
    private lateinit var bubbleViewLeft: KeyWindowLayout
    private lateinit var popTextLeft: MaterialTextView

    private lateinit var popupWindowTop: PopupWindow
    private lateinit var bubbleViewTop: KeyWindowLayout
    private lateinit var popTextTop: MaterialTextView

    private lateinit var popupWindowRight: PopupWindow
    private lateinit var bubbleViewRight: KeyWindowLayout
    private lateinit var popTextRight: MaterialTextView

    private lateinit var popupWindowBottom: PopupWindow
    private lateinit var bubbleViewBottom: KeyWindowLayout
    private lateinit var popTextBottom: MaterialTextView

    private lateinit var popupWindowCenter: PopupWindow
    private lateinit var bubbleViewCenter: KeyWindowLayout
    private lateinit var popTextCenter: MaterialTextView

    private var isFlickGuideEnabled: Boolean = false
    private var popupViewStyle = PopupViewStyle(100, 28f)

    private val cachedArrowRightDrawable: Drawable? by lazy {
        ContextCompat.getDrawable(
            context,
            com.kazumaproject.core.R.drawable.baseline_arrow_right_24
        )
    }

    private val cachedArrowLeftDrawable: Drawable? by lazy {
        ContextCompat.getDrawable(
            context,
            com.kazumaproject.core.R.drawable.baseline_arrow_left_24
        )
    }

    private val cachedSymbolDrawable: Drawable? by lazy {
        ContextCompat.getDrawable(
            context,
            com.kazumaproject.core.R.drawable.baseline_emoji_emotions_24
        )
    }

    private val cachedArrowRightAltDrawable: Drawable? by lazy {
        ContextCompat.getDrawable(
            context,
            com.kazumaproject.core.R.drawable.baseline_arrow_right_alt_24
        )
    }

    private val cachedUndoDrawable: Drawable? by lazy {
        ContextCompat.getDrawable(
            context,
            com.kazumaproject.core.R.drawable.undo_24px
        )
    }

    private val cachedBackSpaceDrawable: Drawable? by lazy {
        ContextCompat.getDrawable(
            context,
            com.kazumaproject.core.R.drawable.baseline_backspace_24
        )
    }

    private val cachedSpaceDrawable: Drawable? by lazy {
        ContextCompat.getDrawable(
            context,
            com.kazumaproject.core.R.drawable.baseline_space_bar_24
        )
    }

    private val cachedNumberSmallDrawable: Drawable? by lazy {
        ContextCompat.getDrawable(
            context,
            com.kazumaproject.core.R.drawable.number_small
        )
    }

    private val cachedHenkanDrawable: Drawable? by lazy {
        ContextCompat.getDrawable(context, com.kazumaproject.core.R.drawable.baseline_autorenew_24)
    }

    private val cachedKanaDrawable: Drawable? by lazy {
        ContextCompat.getDrawable(context, com.kazumaproject.core.R.drawable.kana_small)
    }

    private val cachedOpenBracketDrawable: Drawable? by lazy {
        ContextCompat.getDrawable(
            context,
            com.kazumaproject.core.R.drawable.open_bracket
        )
    }

    private val cachedLanguageDrawable: Drawable? by lazy {
        ContextCompat.getDrawable(
            context,
            com.kazumaproject.core.R.drawable.language_24dp
        )
    }

    private val cachedContentCopyDrawable: Drawable? by lazy {
        ContextCompat.getDrawable(
            context,
            com.kazumaproject.core.R.drawable.content_copy_24dp
        )
    }

    private val cachedContentCutDrawable: Drawable? by lazy {
        ContextCompat.getDrawable(
            context,
            com.kazumaproject.core.R.drawable.content_cut_24dp
        )
    }

    private val cachedContentShareDrawable: Drawable? by lazy {
        ContextCompat.getDrawable(
            context,
            com.kazumaproject.core.R.drawable.baseline_share_24
        )
    }

    private val cachedContentSelectDrawable: Drawable? by lazy {
        ContextCompat.getDrawable(
            context,
            com.kazumaproject.core.R.drawable.text_select_start_24dp
        )
    }

    private val cachedEnglishDrawable: Drawable? by lazy {
        ContextCompat.getDrawable(
            context, com.kazumaproject.core.R.drawable.english_small
        )
    }


    // Map each Key enum to its corresponding View (Button/ImageButton/Switch)
    private var listKeys: Map<Key, Any>

    private var isCursorMode = false
    private val baseKeyMargins: MutableMap<Int, BaseMargins> = mutableMapOf()

    // Theme Variables (Initialized with defaults)
    private var themeMode: String = "default"
    private var isNightMode: Boolean = false
    private var isDynamicColorEnabled: Boolean = false
    private var customBgColor: Int = Color.WHITE
    private var customKeyColor: Int = Color.LTGRAY
    private var customSpecialKeyColor: Int = Color.GRAY
    private var customKeyTextColor: Int = Color.BLACK
    private var customSpecialKeyTextColor: Int = Color.BLACK
    private var liquidGlassEnable: Boolean = false
    private var liquidGlassKeyAlphaEnable: Int = 255
    private var customBorderEnable: Boolean = false
    private var customBorderColor: Int = Color.BLACK

    /** ← NEW: scope tied to this view; cancel it on detach **/
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    init {
        // Inflate the keyboard layout with ViewBinding (root is <merge>, so attachToParent = true)
        val inflater = LayoutInflater.from(context)
        binding = KeyboardLayoutBinding.inflate(inflater, this)
        clipChildren = false
        clipToPadding = false
        val screenshotPopupBinding = PopupLayoutActiveBinding.inflate(inflater, null, false)
        screenshotPopupView = screenshotPopupBinding.bubbleLayoutActive
        screenshotPopupText = screenshotPopupBinding.popupTextActive
        screenshotPopupView.visibility = View.GONE
        screenshotPopupWindow = PopupWindow(
            screenshotPopupView,
            LayoutParams.WRAP_CONTENT,
            LayoutParams.WRAP_CONTENT,
            false
        ).apply {
            isTouchable = false
            isFocusable = false
            isClippingEnabled = false
            elevation = 8f
            animationStyle = 0
            enterTransition = null
            exitTransition = null
        }
        // Initialize keyMap
        keyMap = KeyMap()

        // Build the map from Key enum to actual View references
        listKeys = mapOf(
            Key.KeyA to binding.key1,
            Key.KeyKA to binding.key2,
            Key.KeySA to binding.key3,
            Key.KeyTA to binding.key4,
            Key.KeyNA to binding.key5,
            Key.KeyHA to binding.key6,
            Key.KeyMA to binding.key7,
            Key.KeyYA to binding.key8,
            Key.KeyRA to binding.key9,
            Key.KeyWA to binding.key11,
            Key.KeyKutouten to binding.key12,
            Key.KeyDakutenSmall to binding.keySmallLetter,
            Key.SideKeyPreviousChar to binding.keyReturn,
            Key.SideKeyCursorLeft to binding.keySoftLeft,
            Key.SideKeyCursorRight to binding.keyMoveCursorRight,
            Key.SideKeySymbol to binding.sideKeySymbol,
            Key.SideKeyInputMode to binding.keySwitchKeyMode,
            Key.SideKeyDelete to binding.keyDelete,
            Key.SideKeySpace to binding.keySpace,
            Key.SideKeyEnter to binding.keyEnter
        )
        captureBaseKeyMargins()

        // Make all key views non-focusable so touches go directly to onTouch
        setViewsNotFocusable()

        // Initially display Japanese text on main keys
        setJapaneseTextFor(binding.key12)

        // Set default drawable for small/dakuten key
        setBackgroundSmallLetterKey()

        // Attach this view as its own touch listener
        this.setOnTouchListener(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            this.focusable = View.NOT_FOCUSABLE
        } else {
            this.isFocusable = false
        }

        scope.launch {
            currentInputMode.collect { inputMode ->
                Log.d("TenKey", "currentInputMode: $inputMode")
                // Whenever inputMode changes, update all keys and switch UI
                handleCurrentInputModeSwitch(inputMode)
                binding.keySwitchKeyMode.setInputMode(inputMode, false)
                applyModeSwitchCustomization()
            }
        }
    }

    private fun setPopupViewTheme(
        isDynamicColorsEnable: Boolean,
        isDarkMode: Boolean,
        inflater: LayoutInflater
    ) {
        if (isDynamicColorsEnable) {
            if (isDarkMode) {
                val activeBinding =
                    PopupLayoutActiveMaterialBinding.inflate(inflater, null, false)
                popupWindowActive = PopupWindow(
                    activeBinding.root,
                    LayoutParams.WRAP_CONTENT,
                    LayoutParams.WRAP_CONTENT,
                    false
                )
                bubbleViewActive = activeBinding.bubbleLayoutActive
                popTextActive = activeBinding.popupTextActive
                val leftBinding = PopupLayoutMaterialBinding.inflate(inflater, null, false)
                popupWindowLeft = PopupWindow(
                    leftBinding.root,
                    LayoutParams.WRAP_CONTENT,
                    LayoutParams.WRAP_CONTENT,
                    false
                )
                bubbleViewLeft = leftBinding.bubbleLayout
                popTextLeft = leftBinding.popupText
            } else {
                val activeBinding =
                    PopupLayoutActiveMaterialLightBinding.inflate(inflater, null, false)
                popupWindowActive = PopupWindow(
                    activeBinding.root,
                    LayoutParams.WRAP_CONTENT,
                    LayoutParams.WRAP_CONTENT,
                    false
                )
                bubbleViewActive = activeBinding.bubbleLayoutActive
                popTextActive = activeBinding.popupTextActive
                val leftBinding =
                    PopupLayoutMaterialLightBinding.inflate(inflater, null, false)
                popupWindowLeft = PopupWindow(
                    leftBinding.root,
                    LayoutParams.WRAP_CONTENT,
                    LayoutParams.WRAP_CONTENT,
                    false
                )
                bubbleViewLeft = leftBinding.bubbleLayout
                popTextLeft = leftBinding.popupText
            }
        } else {
            val activeBinding = PopupLayoutActiveBinding.inflate(inflater, null, false)
            popupWindowActive = PopupWindow(
                activeBinding.root,
                LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT,
                false
            )
            bubbleViewActive = activeBinding.bubbleLayoutActive
            popTextActive = activeBinding.popupTextActive

            val leftBinding = PopupLayoutBinding.inflate(inflater, null, false)
            popupWindowLeft = PopupWindow(
                leftBinding.root,
                LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT,
                false
            )
            bubbleViewLeft = leftBinding.bubbleLayout
            popTextLeft = leftBinding.popupText
        }

        // --- Top popup ---
        if (isDynamicColorsEnable) {
            if (isDarkMode) {
                val topBinding = PopupLayoutMaterialBinding.inflate(inflater, null, false)
                popupWindowTop = PopupWindow(
                    topBinding.root,
                    LayoutParams.WRAP_CONTENT,
                    LayoutParams.WRAP_CONTENT,
                    false
                )
                bubbleViewTop = topBinding.bubbleLayout
                popTextTop = topBinding.popupText
            } else {
                val topBinding =
                    PopupLayoutMaterialLightBinding.inflate(inflater, null, false)
                popupWindowTop = PopupWindow(
                    topBinding.root,
                    LayoutParams.WRAP_CONTENT,
                    LayoutParams.WRAP_CONTENT,
                    false
                )
                bubbleViewTop = topBinding.bubbleLayout
                popTextTop = topBinding.popupText
            }
        } else {
            val topBinding = PopupLayoutBinding.inflate(inflater, null, false)
            popupWindowTop = PopupWindow(
                topBinding.root, LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, false
            )
            bubbleViewTop = topBinding.bubbleLayout
            popTextTop = topBinding.popupText
        }

        // --- Right popup ---
        if (isDynamicColorsEnable) {
            if (isDarkMode) {
                val rightBinding = PopupLayoutMaterialBinding.inflate(inflater, null, false)
                popupWindowRight = PopupWindow(
                    rightBinding.root,
                    LayoutParams.WRAP_CONTENT,
                    LayoutParams.WRAP_CONTENT,
                    false
                )
                bubbleViewRight = rightBinding.bubbleLayout
                popTextRight = rightBinding.popupText
            } else {
                val rightBinding =
                    PopupLayoutMaterialLightBinding.inflate(inflater, null, false)
                popupWindowRight = PopupWindow(
                    rightBinding.root,
                    LayoutParams.WRAP_CONTENT,
                    LayoutParams.WRAP_CONTENT,
                    false
                )
                bubbleViewRight = rightBinding.bubbleLayout
                popTextRight = rightBinding.popupText
            }
        } else {
            val rightBinding = PopupLayoutBinding.inflate(inflater, null, false)
            popupWindowRight = PopupWindow(
                rightBinding.root,
                LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT,
                false
            )
            bubbleViewRight = rightBinding.bubbleLayout
            popTextRight = rightBinding.popupText
        }

        // --- Bottom popup ---
        if (isDynamicColorsEnable) {
            if (isDarkMode) {
                val bottomBinding =
                    PopupLayoutMaterialBinding.inflate(inflater, null, false)
                popupWindowBottom = PopupWindow(
                    bottomBinding.root,
                    LayoutParams.WRAP_CONTENT,
                    LayoutParams.WRAP_CONTENT,
                    false
                )
                bubbleViewBottom = bottomBinding.bubbleLayout
                popTextBottom = bottomBinding.popupText
            } else {
                val bottomBinding =
                    PopupLayoutMaterialLightBinding.inflate(inflater, null, false)
                popupWindowBottom = PopupWindow(
                    bottomBinding.root,
                    LayoutParams.WRAP_CONTENT,
                    LayoutParams.WRAP_CONTENT,
                    false
                )
                bubbleViewBottom = bottomBinding.bubbleLayout
                popTextBottom = bottomBinding.popupText
            }
        } else {
            val bottomBinding = PopupLayoutBinding.inflate(inflater, null, false)
            popupWindowBottom = PopupWindow(
                bottomBinding.root,
                LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT,
                false
            )
            bubbleViewBottom = bottomBinding.bubbleLayout
            popTextBottom = bottomBinding.popupText
        }

        // --- Center popup (for long‐press + flick previews) ---
        if (isDynamicColorsEnable) {
            if (isDarkMode) {
                val centerBinding =
                    PopupLayoutMaterialBinding.inflate(inflater, null, false)
                popupWindowCenter = PopupWindow(
                    centerBinding.root,
                    LayoutParams.WRAP_CONTENT,
                    LayoutParams.WRAP_CONTENT,
                    false
                )
                bubbleViewCenter = centerBinding.bubbleLayout
                popTextCenter = centerBinding.popupText
            } else {
                val centerBinding =
                    PopupLayoutMaterialLightBinding.inflate(inflater, null, false)
                popupWindowCenter = PopupWindow(
                    centerBinding.root,
                    LayoutParams.WRAP_CONTENT,
                    LayoutParams.WRAP_CONTENT,
                    false
                )
                bubbleViewCenter = centerBinding.bubbleLayout
                popTextCenter = centerBinding.popupText
            }
        } else {
            val centerBinding = PopupLayoutBinding.inflate(inflater, null, false)
            popupWindowCenter = PopupWindow(
                centerBinding.root,
                LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT,
                false
            )
            bubbleViewCenter = centerBinding.bubbleLayout
            popTextCenter = centerBinding.popupText
        }
        applyPopupTextSize()
        applyTypefaceToPopups()
        applyScreenshotPopupAppearance()
        disablePopupAnimations()
    }

    fun applyPopupViewStyle(style: PopupViewStyle) {
        popupViewStyle = PopupViewStyle(
            sizeScalePercent = style.sizeScalePercent.coerceIn(50, 200),
            textSizeSp = style.textSizeSp.coerceIn(8f, 48f)
        )
        applyPopupTextSize()
    }

    private fun applyPopupTextSize() {
        if (!::popTextActive.isInitialized) return
        listOf(
            popTextActive,
            popTextLeft,
            popTextTop,
            popTextRight,
            popTextBottom,
            popTextCenter
        ).forEach { textView ->
            textView.setTextSize(
                TypedValue.COMPLEX_UNIT_SP,
                popupViewStyle.textSizeSp.coerceIn(8f, 48f)
            )
        }
    }

    private fun disablePopupAnimations() {
        if (::popupWindowActive.isInitialized) popupWindowActive.animationStyle = 0
        if (::popupWindowLeft.isInitialized) popupWindowLeft.animationStyle = 0
        if (::popupWindowTop.isInitialized) popupWindowTop.animationStyle = 0
        if (::popupWindowRight.isInitialized) popupWindowRight.animationStyle = 0
        if (::popupWindowBottom.isInitialized) popupWindowBottom.animationStyle = 0
        if (::popupWindowCenter.isInitialized) popupWindowCenter.animationStyle = 0
        if (::screenshotPopupWindow.isInitialized) screenshotPopupWindow.animationStyle = 0
    }

    fun setPopupWindowAnchorProvider(provider: (() -> View?)?) {
        popupWindowAnchorProvider = provider
    }

    fun setLanguageEnableKeyState(state: Boolean) {
        this.isLanguageIconEnabled = state
    }

    /**
     * Sets the text size for the main keys (key1 to key12).
     * @param size The new text size in sp.
     */
    fun setKeyLetterSize(size: Float) {
        binding.apply {
            val keyButtons = listOf(
                key1, key2, key3, key4, key5, key6,
                key7, key8, key9, key11, key12
            )
            keyButtons.forEach { button ->
                button.textSize = size
            }
        }
    }

    /**
     * Sets key width/height scaling by adjusting the margins of each key view.
     * 100 keeps the current layout, a smaller value shrinks keys, and a larger value expands
     * them until the gaps between keys are minimized.
     */
    fun setKeySizeScale(widthScalePercent: Int, heightScalePercent: Int) {
        scalableKeyViews().forEach { keyView ->
            val params = keyView.layoutParams as? LayoutParams ?: return@forEach
            val baseMargins = baseKeyMargins[keyView.id] ?: return@forEach

            params.marginStart = scaleMargin(baseMargins.start, widthScalePercent)
            params.topMargin = scaleMargin(baseMargins.top, heightScalePercent)
            params.marginEnd = scaleMargin(baseMargins.end, widthScalePercent)
            params.bottomMargin = scaleMargin(baseMargins.bottom, heightScalePercent)
            keyView.layoutParams = params
        }
        requestLayout()
    }

    /**
     * Sets the padding delta to keySize Delta.
     * @param delta The delta value from preference.
     */
    fun setKeyLetterSizeDelta(delta: Int) {
        this.keySizeDelta = delta
        handleCurrentInputModeSwitch(currentInputMode.value)
    }

    private fun captureBaseKeyMargins() {
        scalableKeyViews().forEach { keyView ->
            val params = keyView.layoutParams as? LayoutParams ?: return@forEach
            baseKeyMargins[keyView.id] = BaseMargins(
                start = params.marginStart,
                top = params.topMargin,
                end = params.marginEnd,
                bottom = params.bottomMargin
            )
        }
    }

    private fun scalableKeyViews(): List<View> {
        return listKeys.values.filterIsInstance<View>()
    }

    private fun scaleMargin(baseMargin: Int, scalePercent: Int): Int {
        val clampedScale = scalePercent.coerceIn(60, 140)
        val scaledMargin = when {
            clampedScale >= 100 -> {
                val reduceRatio = (clampedScale - 100) / 40f
                baseMargin * (1f - reduceRatio)
            }

            else -> {
                val expandRatio = (100 - clampedScale) / 40f
                baseMargin * (1f + (expandRatio * 1.5f))
            }
        }

        return scaledMargin.roundToInt().coerceAtLeast(0)
    }

    private fun setMaterialYouTheme(
        isDarkMode: Boolean,
        isDynamicColorEnable: Boolean
    ) {
        if (!isDynamicColorEnable) {
            val tint = ColorStateList.valueOf(
                ContextCompat.getColor(context, com.kazumaproject.core.R.color.black)
            )
            androidx.core.widget.TextViewCompat.setCompoundDrawableTintList(binding.keyEnter, tint)
            return
        }
        binding.apply {
            val centerRes = if (isDarkMode)
                com.kazumaproject.core.R.drawable.ten_keys_center_bg_material
            else
                com.kazumaproject.core.R.drawable.ten_keys_center_bg_material_light

            val sideRes = if (isDarkMode)
                com.kazumaproject.core.R.drawable.ten_keys_side_bg_material
            else
                com.kazumaproject.core.R.drawable.ten_keys_side_bg_material_light

            val roundRes = if (isDarkMode)
                com.kazumaproject.core.R.drawable.round_key_bg_material
            else
                com.kazumaproject.core.R.drawable.round_key_bg_material_light
            // 中央キー
            listOf(
                key1, key2, key3, key4, key5, key6,
                key7, key8, key9, keySmallLetter, key11, key12
            ).forEach { btn ->
                // getDrawable→mutate でインスタンスを複製
                btn.background = ContextCompat
                    .getDrawable(context, centerRes)
                    ?.mutate()
                if (liquidGlassEnable) {
                    btn.setDrawableAlpha(liquidGlassKeyAlphaEnable)
                }
            }

            // サイドキー
            listOf(
                keyReturn, keySoftLeft, sideKeySymbol,
                keyDelete, keyMoveCursorRight, keySpace,

                ).forEach { btn ->
                btn.background = ContextCompat
                    .getDrawable(context, sideRes)
                    ?.mutate()
                if (liquidGlassEnable) {
                    btn.setDrawableAlpha(liquidGlassKeyAlphaEnable)
                }
            }

            keyEnter.background = insetEnterBackground(
                ContextCompat.getDrawable(context, roundRes)
            )

            keyEnter.setDrawableAlpha(liquidGlassKeyAlphaEnable)

            keySwitchKeyMode.background = ContextCompat
                .getDrawable(context, roundRes)

            keySwitchKeyMode.setDrawableAlpha(liquidGlassKeyAlphaEnable)
        }
    }

    /**
     * 動的な色指定によるニューモーフィズムテーマの適用
     * @param targetColor 適用したいメインカラー (例: Color.parseColor("#E0E5EC"))
     */
    fun setDynamicNeumorphismTheme(targetColor: Int) {
        val density = context.resources.displayMetrics.density
        val radius = 8f * density // 角丸の半径 (8dp)

        // 文字色を背景の明るさに応じて自動決定（白 または 黒）
        val textColor =
            if (androidx.core.graphics.ColorUtils.calculateLuminance(targetColor) > 0.5) {
                ContextCompat.getColor(context, com.kazumaproject.core.R.color.black)
            } else {
                ContextCompat.getColor(context, com.kazumaproject.core.R.color.white)
            }
        val textTint = ColorStateList.valueOf(textColor)

        // 背景色をセット（キーと同化させるため）
        this.setBackgroundColor(targetColor)

        binding.apply {
            // 中央キー、サイドキー、Enterキーなど全てに適用
            // （サイドキーだけ色を変えたい場合は別の引数を渡して getDynamicNeumorphDrawable を呼ぶ）
            val commonDrawable = getDynamicNeumorphDrawable(targetColor, radius)

            // 各ボタンに適用 (Drawableはmutateしないと状態が共有されてバグる可能性があるが、
            // 今回は都度生成関数を呼ぶか、定数ならmutateする。
            // ここではループ内で「同じ設定」でいいなら共通インスタンスの `constantState.newDrawable()` を使うとメモリ効率が良い)

            val keys = listOf(
                key1, key2, key3, key4, key5, key6,
                key7, key8, key9, keySmallLetter, key11, key12,
                keyReturn, keySoftLeft, sideKeySymbol,
                keyDelete, keyMoveCursorRight, keySpace, keyEnter, keySwitchKeyMode
            )

            keys.forEach { view ->
                // 新しいDrawableインスタンスを生成してセット
                // (全て同じ色なら同じDrawableインスタンスを使い回しても良いが、サイズが違うとstretchされるため注意)
                view.background = getDynamicNeumorphDrawable(targetColor, radius)

                if (view is MaterialTextView || view is AppCompatButton) {
                    (view as? MaterialTextView)?.setTextColor(textColor)
                    (view as? AppCompatButton)?.setTextColor(textColor)
                }
                if (view is AppCompatImageButton) {
                    ImageViewCompat.setImageTintList(view, textTint)
                }
            }
        }
    }

    private var hasCustomEnterIcon = false
    private var hasCustomSpaceIcon = false
    private var hasCustomLeftIcon = false
    private var hasCustomRightIcon = false

    fun setCustomIcons(
        enterPath: String,
        spacePath: String,
        leftArrowPath: String,
        rightArrowPath: String,
        modeSwitchPath: String = "",
        undoPath: String = "",
        emojiPath: String = "",
        deletePath: String = "",
        customTextModeSwitch: String = "",
        customTextUndo: String = "",
        customTextEmoji: String = "",
        customTextDelete: String = "",
        customTextEnter: String = "",
        customTextSpace: String = "",
        customTextSymbol: String = "",
        customText123: String = ""
    ) {
        val enterDrawable = loadCustomIcon(enterPath)
        customEnterDrawable = enterDrawable?.let { fitDrawable(it) }
        hasCustomEnterIcon = customEnterDrawable != null || customTextEnter.isNotEmpty()

        val spaceDrawable = loadCustomIcon(spacePath)
        customSpaceDrawable = spaceDrawable?.let { fitDrawable(it) }
        hasCustomSpaceIcon = customSpaceDrawable != null || customTextSpace.isNotEmpty()

        val leftDrawable = loadCustomIcon(leftArrowPath)
        if (leftDrawable != null) {
            customLeftDrawable = fitDrawable(leftDrawable)
            hasCustomLeftIcon = true
        } else {
            customLeftDrawable = null
            hasCustomLeftIcon = false
        }
        setCursorKeyIconOrText(binding.keySoftLeft, customLeftDrawable, "◀")

        val rightDrawable = loadCustomIcon(rightArrowPath)
        if (rightDrawable != null) {
            customRightDrawable = fitDrawable(rightDrawable)
            hasCustomRightIcon = true
        } else {
            customRightDrawable = null
            hasCustomRightIcon = false
        }
        setCursorKeyIconOrText(binding.keyMoveCursorRight, customRightDrawable, "▶")
        normalizeSpecialKeys()

        customModeSwitchDrawable = loadCustomIcon(modeSwitchPath)
        customUndoDrawable = loadCustomIcon(undoPath)
        customEmojiDrawable = loadCustomIcon(emojiPath)
        customDeleteDrawable = loadCustomIcon(deletePath)

        customTextModeSwitchStr = customTextModeSwitch
        customTextUndoStr = customTextUndo
        customTextEmojiStr = customTextEmoji
        customTextDeleteStr = customTextDelete

        customTextEnterStr = customTextEnter
        customTextSpaceStr = customTextSpace
        customTextSymbolStr = customTextSymbol
        customText123Str = customText123

        applySpecialKeyCustomizations()
    }

    private fun loadCustomIcon(path: String): Drawable? {
        if (path.isBlank()) return null
        val file = java.io.File(path)
        if (!file.exists()) return null
        return try {
            if (path.endsWith(".svg", ignoreCase = true)) {
                file.inputStream().use { inputStream ->
                    val svg = com.caverock.androidsvg.SVG.getFromInputStream(inputStream)
                    val picture = svg.renderToPicture()
                    android.graphics.drawable.PictureDrawable(picture)
                }
            } else {
                val bitmap = android.graphics.BitmapFactory.decodeFile(path)
                if (bitmap != null) {
                    android.graphics.drawable.BitmapDrawable(resources, bitmap)
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * テーマ設定を一括で適用するメイン関数
     * メンバ変数に値を保存してからテーマを適用します。
     * @param currentNightMode res.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK の値
     */
    fun applyKeyboardTheme(
        themeMode: String,
        currentNightMode: Int,
        isDynamicColorEnabled: Boolean,
        customBgColor: Int,
        customKeyColor: Int,
        customSpecialKeyColor: Int,
        customEnterKeyColor: Int,
        customKeyTextColor: Int,
        customSpecialKeyTextColor: Int,
        customEnterKeyTextColor: Int,
        liquidGlassEnable: Boolean,
        customBorderEnable: Boolean,
        customBorderColor: Int,
        liquidGlassKeyAlphaEnable: Int,
        borderWidth: Int
    ) {
        // メンバ変数に代入
        this.themeMode = themeMode

        // Int型の currentNightMode から Boolean型の isNightMode を判定
        this.isNightMode = (currentNightMode == Configuration.UI_MODE_NIGHT_YES)

        this.isDynamicColorEnabled = isDynamicColorEnabled
        this.customBgColor = customBgColor
        this.customKeyColor = customKeyColor
        this.customSpecialKeyColor = customSpecialKeyColor
        this.customKeyTextColor = customKeyTextColor
        this.customSpecialKeyTextColor = customSpecialKeyTextColor

        this.liquidGlassEnable = liquidGlassEnable

        this.customBorderEnable = customBorderEnable
        this.customBorderColor = customBorderColor
        this.liquidGlassKeyAlphaEnable = liquidGlassKeyAlphaEnable

        val inflater = LayoutInflater.from(context)

        when (this.themeMode) {
            "default" -> {
                setPopupViewTheme(
                    isDynamicColorsEnable = isDynamicColorEnabled,
                    isDarkMode = isNightMode,
                    inflater = inflater
                )
                setBackgroundColor(Color.TRANSPARENT)
                setMaterialYouTheme(this.isNightMode, true)
            }


            "custom" -> {
                val activeBinding = PopupLayoutActiveBinding.inflate(inflater, null, false)
                popupWindowActive = PopupWindow(
                    activeBinding.root,
                    LayoutParams.WRAP_CONTENT,
                    LayoutParams.WRAP_CONTENT,
                    false
                )
                bubbleViewActive = activeBinding.bubbleLayoutActive
                popTextActive = activeBinding.popupTextActive
                val activeColor = manipulateColor(customSpecialKeyColor, 1.2f)
                bubbleViewActive.setBubbleColor(activeColor)
                popTextActive.setTextColor(customSpecialKeyTextColor)

                val leftBinding = PopupLayoutBinding.inflate(inflater, null, false)
                popupWindowLeft = PopupWindow(
                    leftBinding.root,
                    LayoutParams.WRAP_CONTENT,
                    LayoutParams.WRAP_CONTENT,
                    false
                )
                bubbleViewLeft = leftBinding.bubbleLayout
                popTextLeft = leftBinding.popupText
                bubbleViewLeft.setBubbleColor(customSpecialKeyColor)
                popTextLeft.setTextColor(customSpecialKeyTextColor)

                // --- Top popup ---
                val topBinding = PopupLayoutMaterialBinding.inflate(inflater, null, false)
                popupWindowTop = PopupWindow(
                    topBinding.root,
                    LayoutParams.WRAP_CONTENT,
                    LayoutParams.WRAP_CONTENT,
                    false
                )
                bubbleViewTop = topBinding.bubbleLayout
                popTextTop = topBinding.popupText
                bubbleViewTop.setBubbleColor(customSpecialKeyColor)
                popTextTop.setTextColor(customSpecialKeyTextColor)

                // --- Right popup ---
                val rightBinding = PopupLayoutMaterialBinding.inflate(inflater, null, false)
                popupWindowRight = PopupWindow(
                    rightBinding.root,
                    LayoutParams.WRAP_CONTENT,
                    LayoutParams.WRAP_CONTENT,
                    false
                )
                bubbleViewRight = rightBinding.bubbleLayout
                popTextRight = rightBinding.popupText
                bubbleViewRight.setBubbleColor(customSpecialKeyColor)
                popTextRight.setTextColor(customSpecialKeyTextColor)

                // --- Bottom popup ---
                val bottomBinding = PopupLayoutMaterialBinding.inflate(inflater, null, false)
                popupWindowBottom = PopupWindow(
                    bottomBinding.root, LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, false
                )
                bubbleViewBottom = bottomBinding.bubbleLayout
                popTextBottom = bottomBinding.popupText
                bubbleViewBottom.setBubbleColor(customSpecialKeyColor)
                popTextBottom.setTextColor(customSpecialKeyTextColor)

                // --- Center popup (for long‐press + flick previews) ---
                val centerBinding = PopupLayoutMaterialBinding.inflate(inflater, null, false)
                popupWindowCenter = PopupWindow(
                    centerBinding.root, LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, false
                )
                bubbleViewCenter = centerBinding.bubbleLayout
                popTextCenter = centerBinding.popupText
                bubbleViewCenter.setBubbleColor(customSpecialKeyColor)
                popTextCenter.setTextColor(customSpecialKeyTextColor)

                setFullCustomNeumorphismTheme(
                    backgroundColor = customBgColor,
                    normalKeyColor = customKeyColor,
                    specialKeyColor = customSpecialKeyColor,
                    enterKeyColor = customEnterKeyColor,
                    normalKeyTextColor = customKeyTextColor,
                    specialKeyTextColor = customSpecialKeyTextColor,
                    enterKeyTextColor = customEnterKeyTextColor,
                    borderWidth = borderWidth
                )
            }

            else -> {
                setPopupViewTheme(
                    isDynamicColorsEnable = isDynamicColorEnabled,
                    isDarkMode = isNightMode,
                    inflater = inflater
                )
                setBackgroundColor(Color.TRANSPARENT)
                setMaterialYouTheme(this.isNightMode, true)
            }
        }
        applyScreenshotPopupAppearance()
        disablePopupAnimations()
    }

    private fun applyScreenshotPopupAppearance() {
        if (!::popTextActive.isInitialized) return
        val (bubbleColor, strokeColor, textColor) = when {
            themeMode == "custom" -> Triple(
                customBgColor,
                manipulateColor(customBgColor, if (isNightMode) 1.35f else 0.86f),
                customKeyTextColor
            )
            isNightMode -> Triple(
                ContextCompat.getColor(context, com.kazumaproject.core.R.color.keyboard_bg),
                ContextCompat.getColor(context, com.kazumaproject.core.R.color.keyboard_boarder_color),
                ContextCompat.getColor(context, com.kazumaproject.core.R.color.keyboard_icon_color)
            )
            else -> Triple(
                ContextCompat.getColor(context, com.kazumaproject.core.R.color.keyboard_bg),
                ContextCompat.getColor(context, com.kazumaproject.core.R.color.keyboard_boarder_color),
                ContextCompat.getColor(context, com.kazumaproject.core.R.color.keyboard_icon_color)
            )
        }
        listOf(
            bubbleViewActive,
            bubbleViewLeft,
            bubbleViewTop,
            bubbleViewRight,
            bubbleViewBottom,
            bubbleViewCenter,
            screenshotPopupView
        ).forEach { bubble ->
            bubble.setBubbleColor(bubbleColor)
            bubble.setStrokeColor(strokeColor)
            bubble.setStrokeWidth(context.resources.displayMetrics.density)
            bubble.setCornersRadius(28f * context.resources.displayMetrics.density)
        }
        listOf(
            popTextActive,
            popTextLeft,
            popTextTop,
            popTextRight,
            popTextBottom,
            popTextCenter,
            screenshotPopupText
        ).forEach { textView ->
            textView.setTextColor(textColor)
            textView.includeFontPadding = false
            textView.gravity = Gravity.CENTER
        }
    }

    /**
     * 詳細な色指定によるニューモーフィズムテーマの適用（拡張版）
     *
     * @param backgroundColor View全体の背景色
     * @param normalKeyColor 「通常キー」の背景色 (追加)
     * @param specialKeyColor 「特殊キー（Deleteなど）」の背景色
     * @param enterKeyColor 「確定キー」の背景色
     * @param normalKeyTextColor 通常キーの文字・アイコン色
     * @param specialKeyTextColor 特殊キーの文字・アイコン色
     * @param enterKeyTextColor 確定キーの文字・アイコン色
     */
    fun setFullCustomNeumorphismTheme(
        backgroundColor: Int,
        normalKeyColor: Int,
        specialKeyColor: Int,
        enterKeyColor: Int,
        normalKeyTextColor: Int,
        specialKeyTextColor: Int,
        enterKeyTextColor: Int,
        borderWidth: Int
    ) {
        val density = context.resources.displayMetrics.density
        val radius = 8f * density // 角丸の半径 (8dp)
        val enterRadius = 1000f

        // 1. 全体の背景色を設定
        if (liquidGlassEnable) {
            this.setBackgroundColor(ColorUtils.setAlphaComponent(backgroundColor, 0))
        } else {
            this.setBackgroundColor(backgroundColor)
        }

        binding.apply {
            // --- キーの分類リスト定義 ---
            val normalKeys = listOf(
                key1, key2, key3, key4, key5, key6,
                key7, key8, key9, key11, key12, keySmallLetter
            )

            val specialKeys = listOf(
                keyReturn, keySoftLeft, sideKeySymbol,
                keyDelete, keyMoveCursorRight, keySpace,
                keySwitchKeyMode
            )

            val enterKeys = listOf(
                keyEnter
            )

            // --- 色の適用処理 ---

            // 2. 通常キーへの適用 (normalKeyColorを使用)
            val normalDrawableState =
                getDynamicNeumorphDrawable(normalKeyColor, radius).constantState

            val normalColorStateList = ColorStateList.valueOf(normalKeyTextColor)

            normalKeys.forEach { view ->
                if (customBorderEnable) {
                    view.setDrawableSolidColor(customKeyColor)
                    view.setBorder(customBorderColor, borderWidth)
                } else {
                    view.background = normalDrawableState?.newDrawable()?.mutate()
                }

                if (view is MaterialTextView) view.setTextColor(normalColorStateList)
                if (view is AppCompatButton) view.setTextColor(normalColorStateList)

                if (view is AppCompatImageButton) {
                    ImageViewCompat.setImageTintList(view, normalColorStateList)
                }

                view.setDrawableAlpha(liquidGlassKeyAlphaEnable)
            }

            // 3. 特殊キーへの適用 (specialKeyColorを使用)
            val specialDrawableState =
                getDynamicNeumorphDrawable(specialKeyColor, radius).constantState

            val specialColorStateList = ColorStateList.valueOf(specialKeyTextColor)

            specialKeys.forEach { view ->
                if (customBorderEnable) {
                    view.setDrawableSolidColor(customSpecialKeyColor)
                    view.setBorder(customBorderColor, borderWidth)
                } else {
                    view.background = specialDrawableState?.newDrawable()?.mutate()
                }
                if (view is AppCompatButton) {
                    view.setTextColor(specialColorStateList)
                } else if (view is AppCompatImageButton) {
                    ImageViewCompat.setImageTintList(view, specialColorStateList)
                }
                view.setDrawableAlpha(liquidGlassKeyAlphaEnable)
            }

            // 4. 確定キーへの適用 (enterKeyColorを使用)
            val enterDrawableState =
                getDynamicNeumorphDrawable(enterKeyColor, enterRadius).constantState

            val enterColorStateList = ColorStateList.valueOf(enterKeyTextColor)

            enterKeys.forEach { view ->
                if (customBorderEnable) {
                    view.setDrawableSolidColor(enterKeyColor)
                    view.setBorder(customBorderColor, borderWidth)
                } else {
                    view.background = insetEnterBackground(enterDrawableState?.newDrawable()?.mutate())
                }
                if (view is MaterialTextView) view.setTextColor(enterColorStateList)
                if (view is AppCompatButton) view.setTextColor(enterColorStateList)
                if (view is AppCompatImageButton) {
                    ImageViewCompat.setImageTintList(view, enterColorStateList)
                }
                view.setDrawableAlpha(liquidGlassKeyAlphaEnable)
            }
        }
    }

    /**
     * 指定された色(baseColor)を元に、ニューモーフィズムのDrawableを動的に生成する
     * @param baseColor キーのメインカラー
     * @param radius キーの角丸の半径 (px)
     */
    private fun getDynamicNeumorphDrawable(baseColor: Int, radius: Float): Drawable {
        // 1. 色の計算
        // ハイライト色: ベース色に白(#FFFFFF)を50%混ぜる（または明るくする）
        val highlightColor = manipulateColor(baseColor, 1.2f) // 輝度を上げる簡易版
        // シャドウ色: ベース色に黒(#000000)を混ぜて暗くする
        val shadowColor = manipulateColor(baseColor, 0.8f)    // 輝度を下げる簡易版

        // 2. ピクセル単位のオフセット量（4dpなどをpxに変換）
        val density = context.resources.displayMetrics.density
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
        }

        // LayerDrawableで重ねる (下から順に描画される)
        val idleLayer = LayerDrawable(arrayOf(shadowDrawable, highlightDrawable, surfaceDrawable))

        // インセット（余白）を設定して位置をずらす
        // setLayerInset(index, left, top, right, bottom)

        // 影: 左と上を空けて、右下に表示させる
        idleLayer.setLayerInset(0, offset, offset, 0, 0)

        // ハイライト: 右と下を空けて、左上に表示させる
        idleLayer.setLayerInset(1, 0, 0, offset, offset)

        // メイン面: 全体に少し余白を入れて中央に配置（影が見えるようにする）
        idleLayer.setLayerInset(2, padding, padding, padding, padding)


        // --- B. 押下状態 (Pressed) の作成 ---

        // 押したときは凹む表現（影を消して少し暗くする、あるいは内側の影を擬似的に表現）
        val pressedDrawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            // ベース色より少し暗くすることで「押し込まれた」感を出す
            setColor(manipulateColor(baseColor, 0.95f))
        }
        // Pressed状態はサイズを変えないため、IdleのSurfaceと同じ位置に合わせるためのInsetが必要ならLayerDrawableにする
        val pressedLayer = LayerDrawable(arrayOf(pressedDrawable))
        pressedLayer.setLayerInset(0, padding, padding, padding, padding)


        // --- C. StateListDrawable (Selector) にまとめる ---
        val stateListDrawable = android.graphics.drawable.StateListDrawable()

        // 押された時
        stateListDrawable.addState(
            intArrayOf(android.R.attr.state_pressed),
            pressedLayer
        )
        // 無効な時 (必要であれば)
        stateListDrawable.addState(
            intArrayOf(-android.R.attr.state_enabled),
            pressedLayer // 簡易的にPressedと同じ、あるいは透明度を下げるなど
        )
        // 通常時
        stateListDrawable.addState(
            intArrayOf(),
            idleLayer
        )

        return stateListDrawable
    }

    private fun insetEnterBackground(drawable: Drawable?): Drawable? {
        if (drawable == null) return null
        val verticalInset = (6 * context.resources.displayMetrics.density).toInt()
        return InsetDrawable(drawable, 0, verticalInset, 0, verticalInset)
    }

    /**
     * 詳細な色指定によるニューモーフィズムテーマの適用
     *
     * @param backgroundColor 全体の背景色および「通常キー」の背景色
     * @param specialKeyColor 「特殊キー（Enter, Deleteなど）」の背景色
     * @param normalKeyTextColor 通常キーの文字・アイコン色
     * @param specialKeyTextColor 特殊キーの文字・アイコン色
     */
    fun setCustomNeumorphismTheme(
        backgroundColor: Int,
        specialKeyColor: Int,
        normalKeyTextColor: Int,
        specialKeyTextColor: Int
    ) {
        val density = context.resources.displayMetrics.density
        val radius = 8f * density // 角丸の半径 (8dp)

        // 1. 全体の背景色を設定
        this.setBackgroundColor(backgroundColor)

        binding.apply {
            // --- キーの分類リスト定義 ---
            val normalKeys = listOf(
                key1, key2, key3, key4, key5, key6,
                key7, key8, key9, key11, key12, keySmallLetter
            )

            val specialKeys = listOf(
                keyReturn, keySoftLeft, sideKeySymbol,
                keyDelete, keyMoveCursorRight, keySpace, keyEnter,
                keySwitchKeyMode
            )

            // --- 色の適用処理 ---

            // 2. 通常キーへの適用
            val normalDrawableState =
                getDynamicNeumorphDrawable(backgroundColor, radius).constantState

            // ★修正: 単色のColorStateListを作成して強制適用する
            val normalColorStateList = ColorStateList.valueOf(normalKeyTextColor)

            normalKeys.forEach { view ->
                view.background = normalDrawableState?.newDrawable()?.mutate()

                // テキストカラーの適用 (ColorStateListを使う)
                if (view is MaterialTextView) view.setTextColor(normalColorStateList)
                if (view is AppCompatButton) view.setTextColor(normalColorStateList)

                // アイコンTintの適用
                if (view is AppCompatImageButton) {
                    ImageViewCompat.setImageTintList(view, normalColorStateList)
                }
            }

            // 3. 特殊キーへの適用
            val specialDrawableState =
                getDynamicNeumorphDrawable(specialKeyColor, radius).constantState

            // ★修正: 単色のColorStateListを作成して強制適用する
            val specialColorStateList = ColorStateList.valueOf(specialKeyTextColor)

            specialKeys.forEach { view ->
                view.background = specialDrawableState?.newDrawable()?.mutate()

                // テキストカラーの適用 (ColorStateListを使う)
                if (view is MaterialTextView) view.setTextColor(specialColorStateList)
                if (view is AppCompatButton) view.setTextColor(specialColorStateList)

                // アイコンTintの適用
                if (view is AppCompatImageButton) {
                    ImageViewCompat.setImageTintList(view, specialColorStateList)
                }
            }
        }
    }

    /**
     * 色の明るさを調整するヘルパー関数
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

    fun setCurrentMode(inputMode: InputMode) {
        Log.d("setCurrentMode", "$inputMode")
        _currentInputMode.update { inputMode }
    }

    /** Allow setting an external FlickListener **/
    fun setOnFlickListener(flickListener: FlickListener) {
        this.flickListener = flickListener
    }

    /** Allow setting an external LongPressListener **/
    fun setOnLongPressListener(longPressListener: LongPressListener) {
        this.longPressListener = longPressListener
    }

    fun setOnInputModeChangedListener(listener: (InputMode) -> Unit) {
        this.inputModeChangedListener = listener
    }

    /** Padding setters for side keys (symbol, cursors, delete, enter, previous char) **/
    fun setPaddingToSideKeySymbol(paddingSize: Int) {
        binding.sideKeySymbol.setPadding(paddingSize)
    }

    fun setTextToMoveCursorMode(cursorMode: Boolean) {
        if (cursorMode) {
            setKeysCursorMoveMode()
        } else {
            handleCurrentInputModeSwitch(currentInputMode.value)
        }
        this.isCursorMode = cursorMode
    }

    fun setTextToAllButtonsFromSelectMode(isSelecMode: Boolean) {
        if (isSelecMode) {
            setKeysTextsInSelectMode()
        } else {
            handleCurrentInputModeSwitch(currentInputMode.value)
        }
    }

    /** Clean up references when view is detached **/
    private fun release() {
        flickListener = null
        longPressListener = null
        inputModeChangedListener = null
        longPressJob?.cancel()
        longPressJob = null
        touchSequenceActive = false
        hideAllPopWindow()
        isCursorMode = false
        // ← CANCEL the observing coroutine when the view is detached
        //scope.coroutineContext.cancelChildren()
    }

    fun cancelTenKeyScope() {
        scope.coroutineContext.cancelChildren()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        Log.d("TenKey: onDetachedFromWindow", "called")
        release()
    }

    /** Intercept all touch events so we can handle them manually in onTouch **/
    override fun onInterceptTouchEvent(event: MotionEvent?): Boolean {
        return true
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouch(view: View?, event: MotionEvent?): Boolean {
        if (view != null && event != null) {
            if (view.visibility != View.VISIBLE) {
                return false
            }
            when (event.action and MotionEvent.ACTION_MASK) {
                MotionEvent.ACTION_DOWN -> {
                    touchSequenceActive = true
                    val key = pressedKeyByMotionEvent(event, 0)
                    flickListener?.onFlick(GestureType.Down, key, null)

                    pressedKey = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        PressedKey(
                            key = key,
                            pointer = 0,
                            initialX = event.getRawX(event.actionIndex),
                            initialY = event.getRawY(event.actionIndex),
                        )
                    } else {
                        PressedKey(
                            key = key,
                            pointer = 0,
                            initialX = event.getX(event.actionIndex),
                            initialY = event.getY(event.actionIndex),
                        )
                    }

                    Log.d("TenKey: ACTION_DOWN", "called ${pressedKey.key}")

                    if (isCursorMode) {
                        return true
                    }

                    setKeyPressed()
                    showInitialFlickGuideIfNeeded()
                    longPressJob = CoroutineScope(Dispatchers.Main).launch {
                        delay(longPressTimeout)
                        if (touchSequenceActive && pressedKey.key != Key.NotSelected) {
                            longPressListener?.onLongPress(pressedKey.key)
                            isLongPressed = true
                            onLongPressed()
                        }
                    }
                    return false
                }

                MotionEvent.ACTION_UP -> {
                    resetLongPressAction()
                    touchSequenceActive = false
                    hideAllPopWindow()
                    if (isCursorMode) {
                        val viewToRelease: View? = when (pressedKey.key) {
                            Key.SideKeySpace -> binding.keySpace
                            else -> null
                        }
                        viewToRelease?.let { key ->
                            key.isPressed = false
                            flickListener?.onFlick(
                                gestureType = GestureType.Tap,
                                key = pressedKey.key,
                                char = null
                            )
                        }
                        handleCurrentInputModeSwitch(currentInputMode.value)
                        isCursorMode = false
                        pressedKey = pressedKey.copy(key = Key.NotSelected)
                        return false
                    }

                    if (pressedKey.pointer == event.getPointerId(event.actionIndex)) {
                        val gestureType = getGestureType(event)
                        if (isSideKey(pressedKey.key)) {
                            flickListener?.onFlick(
                                gestureType = gestureType,
                                key = pressedKey.key,
                                char = null
                            )
                            if (pressedKey.key == Key.SideKeyInputMode && gestureType == GestureType.Tap) {
                                handleClickInputModeSwitch()
                            }
                            resetAllKeys()
                            hideAllPopWindow()
                            restorePressedKeyContent()
                            pressedKey = pressedKey.copy(key = Key.NotSelected)
                            return false
                        }
                        // ← READING the state flow's current value:
                        val keyInfo = currentInputMode.value
                            .next(keyMap = keyMap, key = pressedKey.key, isTablet = false)

                        Log.d("TenKey: ACTION_UP in pointer", "called $keyInfo $pressedKey")

                        if (keyInfo == KeyInfo.Null) {
                            flickListener?.onFlick(
                                gestureType = gestureType, key = pressedKey.key, char = null
                            )
                            if (pressedKey.key == Key.SideKeyInputMode) {
                                handleClickInputModeSwitch()
                            }
                        } else if (keyInfo is KeyInfo.KeyTapFlickInfo) {
                            when (gestureType) {
                                GestureType.Null -> {}
                                GestureType.Down -> {}
                                GestureType.Tap -> flickListener?.onFlick(
                                    gestureType = gestureType,
                                    key = pressedKey.key,
                                    char = keyInfo.tap
                                )

                                GestureType.FlickLeft -> flickListener?.onFlick(
                                    gestureType = gestureType,
                                    key = pressedKey.key,
                                    char = keyInfo.flickLeft
                                )

                                GestureType.FlickTop -> flickListener?.onFlick(
                                    gestureType = gestureType,
                                    key = pressedKey.key,
                                    char = keyInfo.flickTop
                                )

                                GestureType.FlickRight -> flickListener?.onFlick(
                                    gestureType = gestureType,
                                    key = pressedKey.key,
                                    char = keyInfo.flickRight
                                )

                                GestureType.FlickBottom -> flickListener?.onFlick(
                                    gestureType = gestureType,
                                    key = pressedKey.key,
                                    char = keyInfo.flickBottom
                                )
                            }
                        }
                    }
                    Log.d("TenKey: ACTION_UP out", "called $pressedKey")
                    resetAllKeys()
                    hideAllPopWindow()
                    val button = getButtonFromKey(pressedKey.key)
                    button?.let {
                        if (it is AppCompatButton) {
                            restoreKeyContent(it)
                        }
                    }
                    pressedKey = pressedKey.copy(key = Key.NotSelected)
                    return false
                }

                MotionEvent.ACTION_MOVE -> {
                    if (isCursorMode) {
                        // sensitivity threshold in pixels
                        val threshold = 16f

                        // 1) get the tracked pointer index
                        val pointer = pressedKey.pointer

                        // 2) read its current raw X–Y
                        val (currentX, currentY) = getRawCoordinates(event, pointer)

                        // 3) compute delta since last origin
                        val dx = currentX - pressedKey.initialX
                        val dy = currentY - pressedKey.initialY

                        // 4) only handle if movement exceeds threshold and one axis dominates
                        if (abs(dx) > abs(dy) && abs(dx) > threshold) {
                            // horizontal move
                            if (dx < 0f) {
                                flickListener?.onFlick(GestureType.Tap, Key.SideKeyCursorLeft, null)
                            } else {
                                flickListener?.onFlick(
                                    GestureType.Tap,
                                    Key.SideKeyCursorRight,
                                    null
                                )
                            }
                            // reset origin to avoid repeated triggers
                            pressedKey = pressedKey.copy(initialX = currentX, initialY = currentY)
                        } else if (abs(dy) > abs(dx) && abs(dy) > threshold) {
                            // vertical move
                            if (dy < 0f) {
                                flickListener?.onFlick(
                                    GestureType.FlickTop,
                                    Key.SideKeyCursorLeft,
                                    null
                                )
                            } else {
                                flickListener?.onFlick(
                                    GestureType.FlickBottom,
                                    Key.SideKeyCursorRight,
                                    null
                                )
                            }
                            // reset origin
                            pressedKey = pressedKey.copy(initialX = currentX, initialY = currentY)
                        }

                        return true
                    }

                    val gestureType = if (event.pointerCount == 1) {
                        getGestureType(event, 0)
                    } else {
                        getGestureType(event, pressedKey.pointer)
                    }
                    if (isSideKey(pressedKey.key)) {
                        (getButtonFromKey(pressedKey.key) as? View)?.isPressed = true
                        hideAllPopWindow()
                        return true
                    }
                    when (gestureType) {
                        GestureType.Null -> {}
                        GestureType.Down -> {}
                        GestureType.Tap -> setTapInActionMove()
                        GestureType.FlickLeft, GestureType.FlickTop, GestureType.FlickRight, GestureType.FlickBottom -> setFlickInActionMove(
                            gestureType
                        )
                    }
                    return false
                }

                MotionEvent.ACTION_POINTER_DOWN -> {
                    touchSequenceActive = true
                    if (isLongPressed) {
                        hideAllPopWindow()
                        Blur.removeBlurEffect(this)
                    }
                    hideAllPopWindow()
                    longPressJob?.cancel()
                    if (isCursorMode) {
                        return true
                    }
                    Log.d(
                        "TenKey: ACTION_POINTER_DOWN",
                        "called $pressedKey ${binding.keySmallLetter.text == "🌐"}"
                    )
                    if (isSideKey(pressedKey.key) ||
                        (pressedKey.key == Key.KeyDakutenSmall && binding.keySmallLetter.text == "🌐")
                    ) {
                        return true
                    }
                    if (event.pointerCount == 2) {
                        isLongPressed = false
                        val pointer = event.getPointerId(event.actionIndex)
                        val key = pressedKeyByMotionEvent(event, pointer)
                        val gestureType2 = getGestureType(
                            event, if (pointer == 0) 1 else 0
                        )
                        if (pressedKey.key == Key.KeyDakutenSmall && currentInputMode.value == InputMode.ModeNumber) {
                            restoreSmallLetterKey()
                        }
                        val keyInfo = currentInputMode.value
                            .next(keyMap = keyMap, key = pressedKey.key, isTablet = false)
                        if (keyInfo == KeyInfo.Null) {
                            flickListener?.onFlick(
                                gestureType = gestureType2, key = pressedKey.key, char = null
                            )
                        } else if (keyInfo is KeyInfo.KeyTapFlickInfo) {
                            when (gestureType2) {
                                GestureType.Null -> {}
                                GestureType.Down -> {}
                                GestureType.Tap -> {
                                    flickListener?.onFlick(
                                        gestureType = gestureType2,
                                        key = pressedKey.key,
                                        char = keyInfo.tap
                                    )
                                    val button = getButtonFromKey(pressedKey.key)
                                    button?.let {
                                        if (it is AppCompatButton) {
                                            restoreKeyContent(it)
                                        }
                                    }
                                }

                                GestureType.FlickLeft, GestureType.FlickTop, GestureType.FlickRight, GestureType.FlickBottom -> {
                                    setFlickActionPointerDown(keyInfo, gestureType2)
                                }
                            }
                        }
                        pressedKey = pressedKey.copy(
                            key = key, pointer = pointer, initialX = if (pointer == 0) {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                    event.getRawX(0)
                                } else {
                                    event.getX(0)
                                }
                            } else {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                    event.getRawX(1)
                                } else {
                                    event.getX(1)
                                }
                            }, initialY = if (pointer == 0) {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                    event.getRawY(0)
                                } else {
                                    event.getY(0)
                                }
                            } else {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                    event.getRawY(1)
                                } else {
                                    event.getY(1)
                                }
                            }
                        )
                        setKeyPressed()
                        showInitialFlickGuideIfNeeded()
                        longPressJob = CoroutineScope(Dispatchers.Main).launch {
                            delay(longPressTimeout)
                            if (touchSequenceActive && pressedKey.key != Key.NotSelected) {
                                longPressListener?.onLongPress(pressedKey.key)
                                isLongPressed = true
                                onLongPressed()
                            }
                        }
                    }
                    return false
                }

                MotionEvent.ACTION_POINTER_UP -> {
                    if (event.pointerCount == 2) {
                        if (pressedKey.pointer == event.getPointerId(event.actionIndex)) {
                            resetLongPressAction()
                            if (isCursorMode) {
                                touchSequenceActive = false
                                hideAllPopWindow()
                                return true
                            }
                            val gestureType = getGestureType(
                                event, event.getPointerId(event.actionIndex)
                            )
                            val keyInfo = currentInputMode.value
                                .next(keyMap = keyMap, key = pressedKey.key, isTablet = false)

                            Log.d("TenKey: ACTION_POINTER_UP", "called [${pressedKey.key}]")
                            if (isSideKey(pressedKey.key)) {
                                flickListener?.onFlick(
                                    gestureType = gestureType,
                                    key = pressedKey.key,
                                    char = null
                                )
                                if (pressedKey.key == Key.SideKeyInputMode && gestureType == GestureType.Tap) {
                                    handleClickInputModeSwitch()
                                }
                                (getButtonFromKey(pressedKey.key) as? AppCompatButton)?.let {
                                    it.isPressed = false
                                    restoreKeyContent(it)
                                }
                                pressedKey = pressedKey.copy(key = Key.NotSelected)
                                touchSequenceActive = false
                                hideAllPopWindow()
                                return false
                            }
                            if (keyInfo == KeyInfo.Null) {
                                flickListener?.onFlick(
                                    gestureType = gestureType, key = pressedKey.key, char = null
                                )
                                if (pressedKey.key == Key.SideKeyInputMode) {
                                    handleClickInputModeSwitch()
                                }
                            } else if (keyInfo is KeyInfo.KeyTapFlickInfo) {
                                when (gestureType) {
                                    GestureType.Null -> {}
                                    GestureType.Down -> {}
                                    GestureType.Tap -> {
                                        flickListener?.onFlick(
                                            gestureType = gestureType,
                                            key = pressedKey.key,
                                            char = keyInfo.tap
                                        )
                                    }

                                    GestureType.FlickLeft -> flickListener?.onFlick(
                                        gestureType = gestureType,
                                        key = pressedKey.key,
                                        char = keyInfo.flickLeft
                                    )

                                    GestureType.FlickTop -> flickListener?.onFlick(
                                        gestureType = gestureType,
                                        key = pressedKey.key,
                                        char = keyInfo.flickTop
                                    )

                                    GestureType.FlickRight -> flickListener?.onFlick(
                                        gestureType = gestureType,
                                        key = pressedKey.key,
                                        char = keyInfo.flickRight
                                    )

                                    GestureType.FlickBottom -> flickListener?.onFlick(
                                        gestureType = gestureType,
                                        key = pressedKey.key,
                                        char = keyInfo.flickBottom
                                    )
                                }
                            }
                            val button = getButtonFromKey(pressedKey.key)
                            button?.let {
                                if (it is AppCompatButton) {
                                    it.isPressed = false
                                    restoreKeyContent(it)
                                }
                            }
                            pressedKey = pressedKey.copy(key = Key.NotSelected)
                            touchSequenceActive = false
                            hideAllPopWindow()
                        }
                        return false
                    }
                    return false
                }

                MotionEvent.ACTION_CANCEL -> {
                    touchSequenceActive = false
                    resetLongPressAction()
                    resetAllKeys()
                    hideAllPopWindow()
                    restorePressedKeyContent()
                    pressedKey = pressedKey.copy(key = Key.NotSelected)
                    return false
                }

                else -> return false
            }
        }
        return false
    }

    /** Handle orientation changes by re‐applying text on all keys **/
    override fun onConfigurationChanged(newConfig: Configuration?) {
        super.onConfigurationChanged(newConfig)
        newConfig?.apply {
            if (orientation == Configuration.ORIENTATION_PORTRAIT || orientation == Configuration.ORIENTATION_LANDSCAPE) {
                setTextToAllButtons()
            }
        }
    }

    fun setFlickSensitivityValue(sensitivity: Int) {
        flickSensitivity = sensitivity
    }

    fun setLongPressTimeout(timeoutMillis: Long) {
        longPressTimeout = timeoutMillis.coerceIn(100L, 2000L)
    }

    private fun setTextToAllButtons() {
        setJapaneseTextFor(binding.key1)
        setJapaneseTextFor(binding.key2)
        setJapaneseTextFor(binding.key3)
        setJapaneseTextFor(binding.key4)
        setJapaneseTextFor(binding.key5)
        setJapaneseTextFor(binding.key6)
        setJapaneseTextFor(binding.key7)
        setJapaneseTextFor(binding.key8)
        setJapaneseTextFor(binding.key9)
        setJapaneseTextFor(binding.key11)
    }

    /** Determine which Key enum corresponds to the touch coordinates **/
    private fun pressedKeyByMotionEvent(event: MotionEvent, pointer: Int): Key {
        val (x, y) = getRawCoordinates(event, pointer)

        val keyRects = listOf(
            KeyRect(
                Key.SideKeyPreviousChar,
                binding.keyReturn.layoutXPosition(),
                binding.keyReturn.layoutYPosition(),
                binding.keyReturn.layoutXPosition() + binding.keyReturn.width,
                binding.keyReturn.layoutYPosition() + binding.keyReturn.height
            ), KeyRect(
                Key.KeyA,
                binding.key1.layoutXPosition(),
                binding.key1.layoutYPosition(),
                binding.key1.layoutXPosition() + binding.key1.width,
                binding.key1.layoutYPosition() + binding.key1.height
            ), KeyRect(
                Key.KeyKA,
                binding.key2.layoutXPosition(),
                binding.key2.layoutYPosition(),
                binding.key2.layoutXPosition() + binding.key2.width,
                binding.key2.layoutYPosition() + binding.key2.height
            ), KeyRect(
                Key.KeySA,
                binding.key3.layoutXPosition(),
                binding.key3.layoutYPosition(),
                binding.key3.layoutXPosition() + binding.key3.width,
                binding.key3.layoutYPosition() + binding.key3.height
            ), KeyRect(
                Key.SideKeyDelete,
                binding.keyDelete.layoutXPosition(),
                binding.keyDelete.layoutYPosition(),
                binding.keyDelete.layoutXPosition() + binding.keyDelete.width,
                binding.keyDelete.layoutYPosition() + binding.keyDelete.height
            ), KeyRect(
                Key.SideKeyCursorLeft,
                binding.keySoftLeft.layoutXPosition(),
                binding.keySoftLeft.layoutYPosition(),
                binding.keySoftLeft.layoutXPosition() + binding.keySoftLeft.width,
                binding.keySoftLeft.layoutYPosition() + binding.keySoftLeft.height
            ), KeyRect(
                Key.KeyTA,
                binding.key4.layoutXPosition(),
                binding.key4.layoutYPosition(),
                binding.key4.layoutXPosition() + binding.key4.width,
                binding.key4.layoutYPosition() + binding.key4.height
            ), KeyRect(
                Key.KeyNA,
                binding.key5.layoutXPosition(),
                binding.key5.layoutYPosition(),
                binding.key5.layoutXPosition() + binding.key5.width,
                binding.key5.layoutYPosition() + binding.key5.height
            ), KeyRect(
                Key.KeyHA,
                binding.key6.layoutXPosition(),
                binding.key6.layoutYPosition(),
                binding.key6.layoutXPosition() + binding.key6.width,
                binding.key6.layoutYPosition() + binding.key6.height
            ), KeyRect(
                Key.SideKeyCursorRight,
                binding.keyMoveCursorRight.layoutXPosition(),
                binding.keyMoveCursorRight.layoutYPosition(),
                binding.keyMoveCursorRight.layoutXPosition() + binding.keyMoveCursorRight.width,
                binding.keyMoveCursorRight.layoutYPosition() + binding.keyMoveCursorRight.height
            ), KeyRect(
                Key.SideKeySymbol,
                binding.sideKeySymbol.layoutXPosition(),
                binding.sideKeySymbol.layoutYPosition(),
                binding.sideKeySymbol.layoutXPosition() + binding.sideKeySymbol.width,
                binding.sideKeySymbol.layoutYPosition() + binding.sideKeySymbol.height
            ), KeyRect(
                Key.KeyMA,
                binding.key7.layoutXPosition(),
                binding.key7.layoutYPosition(),
                binding.key7.layoutXPosition() + binding.key7.width,
                binding.key7.layoutYPosition() + binding.key7.height
            ), KeyRect(
                Key.KeyYA,
                binding.key8.layoutXPosition(),
                binding.key8.layoutYPosition(),
                binding.key8.layoutXPosition() + binding.key8.width,
                binding.key8.layoutYPosition() + binding.key8.height
            ), KeyRect(
                Key.KeyRA,
                binding.key9.layoutXPosition(),
                binding.key9.layoutYPosition(),
                binding.key9.layoutXPosition() + binding.key9.width,
                binding.key9.layoutYPosition() + binding.key9.height
            ), KeyRect(
                Key.SideKeySpace,
                binding.keySpace.layoutXPosition(),
                binding.keySpace.layoutYPosition(),
                binding.keySpace.layoutXPosition() + binding.keySpace.width,
                binding.keySpace.layoutYPosition() + binding.keySpace.height
            ), KeyRect(
                Key.SideKeyInputMode,
                binding.keySwitchKeyMode.layoutXPosition(),
                binding.keySwitchKeyMode.layoutYPosition(),
                binding.keySwitchKeyMode.layoutXPosition() + binding.keySwitchKeyMode.width,
                binding.keySwitchKeyMode.layoutYPosition() + binding.keySwitchKeyMode.height
            ), KeyRect(
                Key.KeyDakutenSmall,
                binding.keySmallLetter.layoutXPosition(),
                binding.keySmallLetter.layoutYPosition(),
                binding.keySmallLetter.layoutXPosition() + binding.keySmallLetter.width,
                binding.keySmallLetter.layoutYPosition() + binding.keySmallLetter.height
            ), KeyRect(
                Key.KeyWA,
                binding.key11.layoutXPosition(),
                binding.key11.layoutYPosition(),
                binding.key11.layoutXPosition() + binding.key11.width,
                binding.key11.layoutYPosition() + binding.key11.height
            ), KeyRect(
                Key.KeyKutouten,
                binding.key12.layoutXPosition(),
                binding.key12.layoutYPosition(),
                binding.key12.layoutXPosition() + binding.key12.width,
                binding.key12.layoutYPosition() + binding.key12.height
            ), KeyRect(
                Key.SideKeyEnter,
                binding.keyEnter.layoutXPosition(),
                binding.keyEnter.layoutYPosition(),
                binding.keyEnter.layoutXPosition() + binding.keyEnter.width,
                binding.keyEnter.layoutYPosition() + binding.keyEnter.height
            )
        )

        // If the touch falls inside any key's rectangle, return that enum
        keyRects.forEach { rect ->
            if (x >= rect.left && x <= rect.right && y >= rect.top && y <= rect.bottom) {
                return rect.key
            }
        }

        return Key.NotSelected
    }

    /**
     * フリックガイド表示のオン/オフを切り替える
     */
    fun setFlickGuideEnabled(enabled: Boolean) {
        isFlickGuideEnabled = enabled
        // 現在のモードに合わせてキー表示を再描画
        handleCurrentInputModeSwitch(currentInputMode.value)
    }

    private fun setJapaneseTextFor(button: AppCompatButton) {
        if (isFlickGuideEnabled) {
            button.setTenKeyTextJapaneseWithFlickGuide(
                button.id,
                delta = keySizeDelta,
                modeTheme = themeMode,
                colorTextInt = customKeyTextColor
            )
        } else {
            button.setTenKeyTextJapanese(
                button.id,
                delta = keySizeDelta,
                modeTheme = themeMode,
                colorTextInt = customKeyTextColor
            )
        }
    }

    /** Get absolute coordinates for the given pointer **/
    private fun getRawCoordinates(event: MotionEvent, pointer: Int): Pair<Float, Float> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            event.getRawX(pointer) to event.getRawY(pointer)
        } else {
            val location = IntArray(2)
            this.getLocationOnScreen(location)
            (event.getX(pointer) + location[0]) to (event.getY(pointer) + location[1])
        }
    }

    /** Determine whether the movement is a tap or a flick in a direction **/
    private fun getGestureType(event: MotionEvent, pointer: Int = 0): GestureType {
        val finalX = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            event.getRawX(pointer)
        } else {
            event.getX(pointer)
        }
        val finalY = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            event.getRawY(pointer)
        } else {
            event.getY(pointer)
        }
        val distanceX = finalX - pressedKey.initialX
        val distanceY = finalY - pressedKey.initialY
        val distSq = distanceX * distanceX + distanceY * distanceY

        val density = resources.displayMetrics.scaledDensity
        val mappedSensitivity = (100 - flickSensitivity) / 10.0f
        val flickSensitivityInDip = mappedSensitivity * 1.5f * density
        val thresholdPx = (20.0f * density + flickSensitivityInDip).coerceAtLeast(1.0f)
        val thresholdPxSq = thresholdPx * thresholdPx

        return if (distSq < thresholdPxSq) {
            GestureType.Tap
        } else {
            if (abs(distanceX) > abs(distanceY)) {
                if (distanceX < 0) GestureType.FlickLeft else GestureType.FlickRight
            } else {
                if (distanceY < 0) GestureType.FlickTop else GestureType.FlickBottom
            }
        }
    }

    /** Visually indicate which key is pressed **/
    private fun setKeyPressed() {
        listKeys.forEach { (keyEnum, viewObj) ->
            when (viewObj) {
                is InputModeSwitch -> viewObj.isPressed = (keyEnum == pressedKey.key)
                is AppCompatButton -> viewObj.isPressed = (keyEnum == pressedKey.key)
                is AppCompatImageButton -> viewObj.isPressed = (keyEnum == pressedKey.key)
            }
        }
    }

    private fun isSideKey(key: Key): Boolean {
        return key == Key.SideKeyPreviousChar ||
            key == Key.SideKeySymbol ||
            key == Key.SideKeyInputMode ||
            key == Key.SideKeyDelete ||
            key == Key.SideKeySpace ||
            key == Key.SideKeyEnter ||
            key == Key.SideKeyCursorLeft ||
            key == Key.SideKeyCursorRight
    }

    private fun useScreenshotStyleFlickPopup(): Boolean = true

    private fun showInitialFlickGuideIfNeeded() {
        if (!useScreenshotStyleFlickPopup()) return
        if (isSideKey(pressedKey.key)) return
        if (!::popTextActive.isInitialized || !::popupWindowActive.isInitialized) return
        val button = getButtonFromKey(pressedKey.key) as? AppCompatButton ?: return
        button.isPressed = true
        showActiveFlickPopup(button, GestureType.Tap)
    }

    private fun setFiveDirectionPopupTexts(button: AppCompatButton) {
        when (currentInputMode.value) {
            InputMode.ModeJapanese -> {
                popTextCenter.setTextTapJapanese(button.id)
                popTextTop.setTextFlickTopJapanese(button.id)
                popTextLeft.setTextFlickLeftJapanese(button.id)
                popTextBottom.setTextFlickBottomJapanese(button.id)
                popTextRight.setTextFlickRightJapanese(button.id)
            }

            InputMode.ModeEnglish -> {
                popTextCenter.setTextTapEnglish(button.id)
                popTextTop.setTextFlickTopEnglish(button.id)
                popTextLeft.setTextFlickLeftEnglish(button.id)
                popTextBottom.setTextFlickBottomEnglish(button.id)
                popTextRight.setTextFlickRightEnglish(button.id)
            }

            InputMode.ModeNumber -> {
                popTextCenter.setTextTapNumber(button.id)
                popTextTop.setTextFlickTopNumber(button.id)
                popTextLeft.setTextFlickLeftNumber(button.id)
                popTextBottom.setTextFlickBottomNumber(button.id)
                popTextRight.setTextFlickRightNumber(button.id)
            }
        }
    }

    private fun setActivePopupTextForGesture(button: AppCompatButton, gestureType: GestureType) {
        when (gestureType) {
            GestureType.Tap -> when (currentInputMode.value) {
                InputMode.ModeJapanese -> popTextActive.setTextTapJapanese(button.id)
                InputMode.ModeEnglish -> popTextActive.setTextTapEnglish(button.id)
                InputMode.ModeNumber -> popTextActive.setTextTapNumber(button.id)
            }

            GestureType.FlickLeft -> when (currentInputMode.value) {
                InputMode.ModeJapanese -> popTextActive.setTextFlickLeftJapanese(button.id)
                InputMode.ModeEnglish -> popTextActive.setTextFlickLeftEnglish(button.id)
                InputMode.ModeNumber -> popTextActive.setTextFlickLeftNumber(button.id)
            }

            GestureType.FlickTop -> when (currentInputMode.value) {
                InputMode.ModeJapanese -> popTextActive.setTextFlickTopJapanese(button.id)
                InputMode.ModeEnglish -> popTextActive.setTextFlickTopEnglish(button.id)
                InputMode.ModeNumber -> popTextActive.setTextFlickTopNumber(button.id)
            }

            GestureType.FlickRight -> when (currentInputMode.value) {
                InputMode.ModeJapanese -> popTextActive.setTextFlickRightJapanese(button.id)
                InputMode.ModeEnglish -> popTextActive.setTextFlickRightEnglish(button.id)
                InputMode.ModeNumber -> popTextActive.setTextFlickRightNumber(button.id)
            }

            GestureType.FlickBottom -> when (currentInputMode.value) {
                InputMode.ModeJapanese -> popTextActive.setTextFlickBottomJapanese(button.id)
                InputMode.ModeEnglish -> popTextActive.setTextFlickBottomEnglish(button.id)
                InputMode.ModeNumber -> popTextActive.setTextFlickBottomNumber(button.id)
            }

            GestureType.Down,
            GestureType.Null -> popTextActive.text = ""
        }
    }

    private fun fiveDirectionPopupText(button: AppCompatButton): CharSequence {
        setFiveDirectionPopupTexts(button)
        val center = popTextCenter.text
        val top = popTextTop.text
        val left = popTextLeft.text
        val right = popTextRight.text
        val bottom = popTextBottom.text
        var centerStart = -1
        var topStart = -1
        var leftStart = -1
        var rightStart = -1
        var bottomStart = -1
        val text = buildString {
            topStart = length
            append(top)
            append('\n')
            if (left.isNotEmpty()) append(left).append(' ')
            leftStart = if (left.isNotEmpty()) top.length + 1 else -1
            centerStart = length
            append(center)
            if (right.isNotEmpty()) {
                append(' ')
                rightStart = length
                append(right)
            }
            append('\n')
            bottomStart = length
            append(bottom)
        }
        return SpannableString(text).apply {
            fun setRelative(start: Int, part: CharSequence, scale: Float) {
                if (start < 0 || part.isEmpty()) return
                setSpan(
                    RelativeSizeSpan(scale),
                    start,
                    (start + part.length).coerceAtMost(length),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
            setRelative(topStart, top, 0.78f)
            setRelative(leftStart, left, 0.78f)
            setRelative(rightStart, right, 0.78f)
            setRelative(bottomStart, bottom, 0.78f)
            setRelative(centerStart, center, 1.35f)
        }
    }

    private fun showActiveFlickPopup(button: AppCompatButton, gestureType: GestureType) {
        var usingGuide = gestureType == GestureType.Tap && useScreenshotStyleFlickPopup()
        var nextText = if (usingGuide) {
            fiveDirectionPopupText(button)
        } else {
            setActivePopupTextForGesture(button, gestureType)
            popTextActive.text
        }
        if (nextText.isEmpty() && useScreenshotStyleFlickPopup()) {
            nextText = fiveDirectionPopupText(button)
            usingGuide = true
        }
        if (nextText.isEmpty()) {
            hideAllPopWindow()
            return
        }
        val stateGestureType = if (usingGuide) GestureType.Tap else gestureType
        val isSameAnchorShowing = if (useScreenshotStyleFlickPopup()) {
            screenshotPopupWindow.isShowing && activePopupButtonId == button.id
        } else {
            popupWindowActive.isShowing && activePopupButtonId == button.id
        }
        if (
            isSameAnchorShowing &&
            activePopupGestureType == stateGestureType &&
            activePopupText.toString() == nextText.toString()
        ) {
            return
        }
        if (!isSameAnchorShowing) {
            hideAllPopWindow()
        }
        activePopupButtonId = button.id
        activePopupGestureType = stateGestureType
        activePopupText = nextText
        val targetTextView = if (useScreenshotStyleFlickPopup()) screenshotPopupText else popTextActive
        targetTextView.text = nextText
        if (usingGuide) {
            targetTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            targetTextView.setLineSpacing(0f, 0.86f)
            targetTextView.maxLines = 3
        } else {
            targetTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 30f)
            targetTextView.setLineSpacing(0f, 1f)
            targetTextView.maxLines = 1
        }
        if (targetTextView.text.isEmpty()) return
        val popupScalePercent = if (useScreenshotStyleFlickPopup()) {
            popupViewStyle.sizeScalePercent.coerceAtLeast(120)
        } else if (usingGuide) {
            popupViewStyle.sizeScalePercent.coerceAtLeast(120)
        } else {
            popupViewStyle.sizeScalePercent
        }
        if (useScreenshotStyleFlickPopup()) {
            showScreenshotFlickPopup(button, popupScalePercent)
        } else if (isSameAnchorShowing) {
            val popupWidth = button.width.scaledPopupSizeForActive(popupScalePercent)
            val popupHeight = button.height.scaledPopupSizeForActive(popupScalePercent)
            popupWindowActive.update(
                button,
                -((popupWidth - button.width) / 2),
                -popupHeight - button.height,
                popupWidth,
                popupHeight
            )
        } else {
            popupWindowActive.setPopUpWindowTop(context, bubbleViewActive, button, popupScalePercent)
        }
    }

    private fun showScreenshotFlickPopup(button: AppCompatButton, popupScalePercent: Int) {
        if (!touchSequenceActive) return
        val popupWidth = button.width.scaledPopupSizeForActive(popupScalePercent)
        val popupHeight = button.height.scaledPopupSizeForActive(popupScalePercent)
        val windowAnchor = popupWindowAnchorProvider?.invoke() ?: button
        if (!isPopupAnchorReady(button, windowAnchor)) return

        val location = getLocationRelativeToWindowAnchor(button, windowAnchor)
        val rawX = location[0] + (button.width - popupWidth) / 2
        val rawY = location[1] - popupHeight - (6f * resources.displayMetrics.density).roundToInt()
        val maxX = (windowAnchor.width - popupWidth).coerceAtLeast(0)
        val popupX = rawX.coerceIn(0, maxX)
        val popupY = rawY.coerceAtLeast(0)

        screenshotPopupView.visibility = View.VISIBLE
        screenshotPopupWindow.width = popupWidth
        screenshotPopupWindow.height = popupHeight
        if (screenshotPopupWindow.isShowing) {
            screenshotPopupWindow.update(popupX, popupY, popupWidth, popupHeight)
        } else {
            screenshotPopupWindow.showAtLocation(windowAnchor, Gravity.NO_GRAVITY, popupX, popupY)
        }
    }

    private fun getLocationRelativeToWindowAnchor(keyAnchor: View, windowAnchor: View): IntArray {
        if (keyAnchor === windowAnchor) {
            return IntArray(2).also { keyAnchor.getLocationInWindow(it) }
        }

        val keyLocation = IntArray(2)
        val windowLocation = IntArray(2)
        keyAnchor.getLocationOnScreen(keyLocation)
        windowAnchor.getLocationOnScreen(windowLocation)
        return intArrayOf(
            keyLocation[0] - windowLocation[0],
            keyLocation[1] - windowLocation[1]
        )
    }

    private fun isPopupAnchorReady(keyAnchor: View, windowAnchor: View?): Boolean {
        if (!keyAnchor.isAttachedToWindow) return false
        if (windowAnchor == null) return false
        if (!windowAnchor.isAttachedToWindow) return false
        return windowAnchor.windowToken != null
    }

    private fun Int.scaledPopupSizeForActive(sizeScalePercent: Int): Int {
        return (this * sizeScalePercent.coerceIn(50, 200) / 100f).roundToInt()
    }

    private fun restorePressedKeyContent() {
        (getButtonFromKey(pressedKey.key) as? AppCompatButton)?.let { restoreKeyContent(it) }
    }

    /** Cancel ongoing long‐press visuals and job **/
    private fun resetLongPressAction() {
        if (isLongPressed) {
            hideAllPopWindow()
            Blur.removeBlurEffect(this)
        }
        longPressJob?.cancel()
        isLongPressed = false
    }

    /** Un–highlight all keys **/
    private fun resetAllKeys() {
        listKeys.values.forEach { viewObj ->
            when (viewObj) {
                is InputModeSwitch -> viewObj.isPressed = false
                is AppCompatButton -> viewObj.isPressed = false
                is AppCompatImageButton -> viewObj.isPressed = false
            }
        }
    }

    /** Return the underlying view object (Button/ImageButton/Switch) for a given Key **/
    private fun getButtonFromKey(key: Key): Any? {
        return listKeys[key]
    }

    /** Called when a long‐press is detected; show all related popups **/
    private fun onLongPressed() {
        if (isSideKey(pressedKey.key)) {
            hideAllPopWindow()
            return
        }
        if (useScreenshotStyleFlickPopup()) {
            val activeButton = getButtonFromKey(pressedKey.key) as? AppCompatButton ?: return
            activeButton.isPressed = true
            showActiveFlickPopup(activeButton, GestureType.Tap)
            return
        }
        val button = getButtonFromKey(pressedKey.key)
        button?.let {
            if (it is AppCompatButton) {
                if (it == binding.sideKeySymbol) return

                when (currentInputMode.value) {
                    InputMode.ModeJapanese -> {
                        popTextTop.setTextFlickTopJapanese(it.id)
                        popTextLeft.setTextFlickLeftJapanese(it.id)
                        popTextBottom.setTextFlickBottomJapanese(it.id)
                        popTextRight.setTextFlickRightJapanese(it.id)
                        popTextActive.setTextTapJapanese(it.id)
                    }

                    InputMode.ModeEnglish -> {
                        popTextTop.setTextFlickTopEnglish(it.id)
                        popTextLeft.setTextFlickLeftEnglish(it.id)
                        popTextBottom.setTextFlickBottomEnglish(it.id)
                        popTextRight.setTextFlickRightEnglish(it.id)
                        popTextActive.setTextTapEnglish(it.id)
                    }

                    InputMode.ModeNumber -> {
                        popTextTop.setTextFlickTopNumber(it.id)
                        popTextLeft.setTextFlickLeftNumber(it.id)
                        popTextBottom.setTextFlickBottomNumber(it.id)
                        popTextRight.setTextFlickRightNumber(it.id)
                        popTextActive.setTextTapNumber(it.id)
                    }
                }
                popupWindowTop.setPopUpWindowTop(context, bubbleViewTop, it, popupViewStyle.sizeScalePercent)
                popupWindowLeft.setPopUpWindowLeft(context, bubbleViewLeft, it, popupViewStyle.sizeScalePercent)
                if (popTextBottom.text.isNotEmpty()) {
                    popupWindowBottom.setPopUpWindowBottom(context, bubbleViewBottom, it, popupViewStyle.sizeScalePercent)
                }
                if (popTextRight.text.isNotEmpty()) {
                    popupWindowRight.setPopUpWindowRight(context, bubbleViewRight, it, popupViewStyle.sizeScalePercent)
                }
                popupWindowActive.setPopUpWindowCenter(context, bubbleViewActive, it, popupViewStyle.sizeScalePercent)
            }

            if (it is AppCompatButton) {
                if (currentInputMode.value == InputMode.ModeNumber && it == binding.keySmallLetter) {
                    popTextTop.setTextFlickTopNumber(it.id)
                    popTextLeft.setTextFlickLeftNumber(it.id)
                    popTextBottom.setTextFlickBottomNumber(it.id)
                    popTextRight.setTextFlickRightNumber(it.id)
                    popupWindowTop.setPopUpWindowTop(context, bubbleViewTop, it, popupViewStyle.sizeScalePercent)
                    popupWindowLeft.setPopUpWindowLeft(context, bubbleViewLeft, it, popupViewStyle.sizeScalePercent)
                    popupWindowBottom.setPopUpWindowBottom(context, bubbleViewBottom, it, popupViewStyle.sizeScalePercent)
                    popupWindowRight.setPopUpWindowRight(context, bubbleViewRight, it, popupViewStyle.sizeScalePercent)
                    popupWindowActive.setPopUpWindowCenter(context, bubbleViewActive, it, popupViewStyle.sizeScalePercent)
                }
            }
        }
    }

    /** Hide every popup bubble **/
    private fun hideAllPopWindow() {
        activePopupButtonId = View.NO_ID
        activePopupGestureType = GestureType.Null
        activePopupText = ""
        if (::screenshotPopupView.isInitialized) {
            screenshotPopupView.visibility = View.GONE
            screenshotPopupText.text = ""
        }
        if (::screenshotPopupWindow.isInitialized) screenshotPopupWindow.hide()
        popupWindowActive.hide()
        popupWindowLeft.hide()
        popupWindowTop.hide()
        popupWindowRight.hide()
        popupWindowBottom.hide()
        popupWindowCenter.hide()
    }

    /** Called during a “tap” gesture in an ongoing move event **/
    private fun setTapInActionMove() {
        val keepFlickGuide = useScreenshotStyleFlickPopup() && screenshotPopupWindow.isShowing
        if (!isLongPressed && !keepFlickGuide) hideAllPopWindow()
        val button = getButtonFromKey(pressedKey.key)
        button?.let {
            if (it is AppCompatButton) {
                if (it == binding.sideKeySymbol) return
                it.isPressed = true
                when (currentInputMode.value) {
                    InputMode.ModeJapanese -> {
                        it.setTenKeyTextWhenTapJapanese(it.id)
                        if (isLongPressed) popTextActive.setTextTapJapanese(it.id)
                    }

                    InputMode.ModeEnglish -> {
                        it.setTenKeyTextWhenTapEnglish(it.id)
                        if (isLongPressed) popTextActive.setTextTapEnglish(it.id)
                    }

                    InputMode.ModeNumber -> {
                        it.setTenKeyTextWhenTapNumber(it.id)
                        if (isLongPressed) popTextActive.setTextTapNumber(it.id)
                    }
                }

                if (isLongPressed || keepFlickGuide) {
                    showActiveFlickPopup(it, GestureType.Tap)
                }
            }
            if (it is AppCompatButton && currentInputMode.value == InputMode.ModeNumber && it == binding.keySmallLetter) {
                it.isPressed = true
                it.text = "("
                if (isLongPressed) popTextActive.setTextTapNumber(it.id)
                if (isLongPressed || keepFlickGuide) {
                    showActiveFlickPopup(it, GestureType.Tap)
                }
            }
        }
    }

    /** Called during a “flick” gesture in an ongoing move event **/
    private fun setFlickInActionMove(gestureType: GestureType) {
        longPressJob?.cancel()
        val button = getButtonFromKey(pressedKey.key)
        button?.let {
            if (it is AppCompatButton) {
                if (it == binding.sideKeySymbol) return
                it.isPressed = true
                if (useScreenshotStyleFlickPopup()) {
                    showActiveFlickPopup(it, gestureType)
                    return
                }
                if (!isLongPressed) it.text = ""
                when (gestureType) {
                    GestureType.FlickLeft -> {
                        when (currentInputMode.value) {
                            InputMode.ModeJapanese -> {
                                popTextActive.setTextFlickLeftJapanese(it.id)
                                if (isLongPressed) popTextCenter.setTextTapJapanese(it.id)
                            }

                            InputMode.ModeEnglish -> {
                                popTextActive.setTextFlickLeftEnglish(it.id)
                                if (isLongPressed) popTextCenter.setTextTapEnglish(it.id)
                            }

                            InputMode.ModeNumber -> {
                                popTextActive.setTextFlickLeftNumber(it.id)
                                if (isLongPressed) popTextCenter.setTextTapNumber(it.id)
                            }
                        }
                        if (isLongPressed) {
                            popupWindowCenter.setPopUpWindowCenter(context, bubbleViewCenter, it, popupViewStyle.sizeScalePercent)
                            popupWindowActive.setPopUpWindowLeft(context, bubbleViewActive, it, popupViewStyle.sizeScalePercent)
                        } else {
                            popupWindowActive.setPopUpWindowFlickLeft(context, bubbleViewActive, it, popupViewStyle.sizeScalePercent)
                        }
                    }

                    GestureType.FlickTop -> {
                        when (currentInputMode.value) {
                            InputMode.ModeJapanese -> {
                                popTextActive.setTextFlickTopJapanese(it.id)
                                if (isLongPressed) popTextCenter.setTextTapJapanese(it.id)
                            }

                            InputMode.ModeEnglish -> {
                                popTextActive.setTextFlickTopEnglish(it.id)
                                if (isLongPressed) popTextCenter.setTextTapEnglish(it.id)
                            }

                            InputMode.ModeNumber -> {
                                popTextActive.setTextFlickTopNumber(it.id)
                                if (isLongPressed) popTextCenter.setTextTapNumber(it.id)
                            }
                        }
                        if (isLongPressed) {
                            popupWindowCenter.setPopUpWindowCenter(context, bubbleViewCenter, it, popupViewStyle.sizeScalePercent)
                            popupWindowActive.setPopUpWindowTop(context, bubbleViewActive, it, popupViewStyle.sizeScalePercent)
                        } else {
                            popupWindowActive.setPopUpWindowFlickTop(context, bubbleViewActive, it, popupViewStyle.sizeScalePercent)
                        }
                    }

                    GestureType.FlickRight -> {
                        when (currentInputMode.value) {
                            InputMode.ModeJapanese -> {
                                popTextActive.setTextFlickRightJapanese(it.id)
                                if (isLongPressed) popTextCenter.setTextTapJapanese(it.id)
                            }

                            InputMode.ModeEnglish -> {
                                popTextActive.setTextFlickRightEnglish(it.id)
                                if (isLongPressed) popTextCenter.setTextTapEnglish(it.id)
                            }

                            InputMode.ModeNumber -> {
                                popTextActive.setTextFlickRightNumber(it.id)
                                if (isLongPressed) popTextCenter.setTextTapNumber(it.id)
                            }
                        }
                        if (isLongPressed) {
                            if (popTextActive.text.isNotEmpty()) {
                                popupWindowActive.setPopUpWindowRight(context, bubbleViewActive, it, popupViewStyle.sizeScalePercent)
                                popupWindowCenter.setPopUpWindowCenter(
                                    context, bubbleViewCenter, it, popupViewStyle.sizeScalePercent
                                )
                            }
                        } else {
                            if (popTextActive.text.isNotEmpty()) {
                                popupWindowActive.setPopUpWindowFlickRight(
                                    context, bubbleViewActive, it, popupViewStyle.sizeScalePercent
                                )
                            }
                        }
                    }

                    GestureType.FlickBottom -> {
                        when (currentInputMode.value) {
                            InputMode.ModeJapanese -> {
                                popTextActive.setTextFlickBottomJapanese(it.id)
                                if (isLongPressed) popTextCenter.setTextTapJapanese(it.id)
                            }

                            InputMode.ModeEnglish -> {
                                popTextActive.setTextFlickBottomEnglish(it.id)
                                if (isLongPressed) popTextCenter.setTextTapEnglish(it.id)
                            }

                            InputMode.ModeNumber -> {
                                popTextActive.setTextFlickBottomNumber(it.id)
                                if (isLongPressed) popTextCenter.setTextTapNumber(it.id)
                            }
                        }
                        if (isLongPressed) {
                            if (popTextActive.text.isNotEmpty()) {
                                popupWindowActive.setPopUpWindowBottom(
                                    context, bubbleViewActive, it, popupViewStyle.sizeScalePercent
                                )
                                popupWindowCenter.setPopUpWindowCenter(
                                    context, bubbleViewCenter, it, popupViewStyle.sizeScalePercent
                                )
                            }
                        } else {
                            if (popTextActive.text.isNotEmpty()) {
                                popupWindowActive.setPopUpWindowFlickBottom(
                                    context, bubbleViewActive, it, popupViewStyle.sizeScalePercent
                                )
                            }
                        }
                    }

                    else -> {}
                }
            }
            if (it is AppCompatButton && currentInputMode.value == InputMode.ModeNumber && it == binding.keySmallLetter) {
                it.isPressed = true
                if (!isLongPressed) it.text = ""
                when (gestureType) {
                    GestureType.FlickLeft -> {
                        popTextActive.setTextFlickLeftNumber(it.id)
                        if (isLongPressed) popTextCenter.setTextTapNumber(it.id)
                        if (isLongPressed) {
                            popupWindowCenter.setPopUpWindowCenter(context, bubbleViewCenter, it, popupViewStyle.sizeScalePercent)
                            popupWindowActive.setPopUpWindowLeft(context, bubbleViewActive, it, popupViewStyle.sizeScalePercent)
                        } else {
                            popupWindowActive.setPopUpWindowFlickLeft(context, bubbleViewActive, it, popupViewStyle.sizeScalePercent)
                        }
                    }

                    GestureType.FlickTop -> {
                        popTextActive.setTextFlickTopNumber(it.id)
                        if (isLongPressed) popTextCenter.setTextTapNumber(it.id)
                        if (isLongPressed) {
                            popupWindowCenter.setPopUpWindowCenter(context, bubbleViewCenter, it, popupViewStyle.sizeScalePercent)
                            popupWindowActive.setPopUpWindowTop(context, bubbleViewActive, it, popupViewStyle.sizeScalePercent)
                        } else {
                            popupWindowActive.setPopUpWindowFlickTop(context, bubbleViewActive, it, popupViewStyle.sizeScalePercent)
                        }
                    }

                    GestureType.FlickRight -> {
                        popTextActive.setTextFlickRightNumber(it.id)
                        if (isLongPressed) popTextCenter.setTextTapNumber(it.id)
                        if (isLongPressed) {
                            popupWindowCenter.setPopUpWindowCenter(context, bubbleViewCenter, it, popupViewStyle.sizeScalePercent)
                            popupWindowActive.setPopUpWindowRight(context, bubbleViewActive, it, popupViewStyle.sizeScalePercent)
                        } else {
                            popupWindowActive.setPopUpWindowFlickRight(
                                context, bubbleViewActive, it, popupViewStyle.sizeScalePercent
                            )
                        }
                    }

                    GestureType.FlickBottom -> {
                        popTextActive.setTextFlickBottomNumber(it.id)
                        if (isLongPressed) popTextCenter.setTextTapNumber(it.id)
                        if (isLongPressed) {
                            popupWindowCenter.setPopUpWindowCenter(context, bubbleViewCenter, it, popupViewStyle.sizeScalePercent)
                            popupWindowActive.setPopUpWindowBottom(context, bubbleViewActive, it, popupViewStyle.sizeScalePercent)
                        } else {
                            popupWindowActive.setPopUpWindowFlickBottom(
                                context, bubbleViewActive, it, popupViewStyle.sizeScalePercent
                            )
                        }
                    }

                    else -> {}
                }
            }
        }
    }

    /** Handle flick action when second finger goes down **/
    private fun setFlickActionPointerDown(keyInfo: KeyInfo, gestureType: GestureType) {
        if (keyInfo is KeyInfo.KeyTapFlickInfo) {
            val charToSend = when (gestureType) {
                GestureType.Tap -> keyInfo.tap
                GestureType.FlickLeft -> keyInfo.flickLeft
                GestureType.FlickTop -> keyInfo.flickTop
                GestureType.FlickRight -> keyInfo.flickRight
                GestureType.FlickBottom -> keyInfo.flickBottom
                else -> null
            }
            flickListener?.onFlick(
                gestureType = gestureType, key = pressedKey.key, char = charToSend
            )
            val button = getButtonFromKey(pressedKey.key)
            button?.let {
                if (it is AppCompatButton) {
                    restoreKeyContent(it)
                }
            }
        }
    }

    /** Set default drawable for the small/dakuten key **/
    @Suppress("UNUSED_PARAMETER")
    fun setBackgroundSmallLetterKey(
        drawable: Drawable? = cachedLanguageDrawable
    ) {
        binding.keySmallLetter.text = when (currentInputMode.value) {
            InputMode.ModeJapanese -> "ﾞ ﾟ"
            InputMode.ModeEnglish -> "a/A"
            InputMode.ModeNumber -> "()"
        }
        prepareSpecialKeyButton(binding.keySmallLetter)
    }

    /** Set default drawable for the small/dakuten key **/
    @Suppress("UNUSED_PARAMETER")
    fun setBackgroundSmallLetterKey(
        isLanguageEnable: Boolean,
        isEnglish: Boolean
    ) {
        binding.keySmallLetter.text = when (currentInputMode.value) {
            InputMode.ModeJapanese -> "ﾞ ﾟ"
            InputMode.ModeEnglish -> "a/A"
            InputMode.ModeNumber -> "()"
        }
        prepareSpecialKeyButton(binding.keySmallLetter)
    }

    /** Set custom drawable on the Enter key **/
    fun setSideKeyEnterDrawable(drawable: Drawable?) {
        if (hasCustomEnterIcon || customTextEnterStr.isNotEmpty()) return
        prepareSpecialKeyButton(binding.keyEnter)
        setCenteredIcon(binding.keyEnter, drawable)
    }

    /** Retrieve current Enter key drawable **/
    fun getCurrentEnterKeyDrawable(): Drawable? {
        return binding.keyEnter.compoundDrawables[0]
    }

    /** Set custom drawable on the Space key **/
    @Suppress("UNUSED_PARAMETER")
    fun setSideKeySpaceDrawable(drawable: Drawable?) {
        prepareSpecialKeyButton(binding.keySpace)
        if (customTextSpaceStr.isNotEmpty()) {
            binding.keySpace.setCompoundDrawables(null, null, null, null)
            binding.keySpace.text = customTextSpaceStr
            customTypeface?.let { binding.keySpace.typeface = it }
            return
        }
        val defaultDrawable = if (
            drawable != null &&
            cachedHenkanDrawable?.constantState != null &&
            drawable.constantState == cachedHenkanDrawable?.constantState
        ) {
            cachedHenkanDrawable
        } else {
            drawable ?: cachedSpaceDrawable
        }
        if (customSpaceDrawable != null) {
            setCenteredIcon(binding.keySpace, customSpaceDrawable)
        } else {
            setCenteredIcon(binding.keySpace, defaultDrawable, tintAsDefaultIcon = true)
        }
        customTypeface?.let { binding.keySpace.typeface = it }
    }

    /** Enable/disable the “previous character” key **/
    fun setSideKeyPreviousState(state: Boolean) {
        binding.keyReturn.isEnabled = state
    }

    /** Enable/disable the “previous character” key **/
    fun setSideKeyPreviousDrawable(drawable: Drawable?) {
        setButtonImageOrText(binding.keyReturn, drawable ?: cachedUndoDrawable, customUndoDrawable, customTextUndoStr)
    }

    /** Cycle through input modes when the switch key is clicked **/
    private fun handleClickInputModeSwitch() {
        // ← READ from StateFlow.value:
        val newInputMode = when (currentInputMode.value) {
            InputMode.ModeJapanese -> InputMode.ModeEnglish
            InputMode.ModeEnglish -> InputMode.ModeNumber
            InputMode.ModeNumber -> InputMode.ModeJapanese
        }
        // ← WRITE to MutableStateFlow:
        _currentInputMode.update { newInputMode }
        inputModeChangedListener?.invoke(newInputMode)
        // We don’t need to manually call setKeysInXXX or setInputMode(...) here,
        // because our collector in init { … } already calls `handleCurrentInputModeSwitch(...)`
        // and `binding.keySwitchKeyMode.setInputMode(...)`.
    }

    /** Sync UI to a specified input mode (called from collector) **/
    private fun handleCurrentInputModeSwitch(inputMode: InputMode) {
        when (inputMode) {
            InputMode.ModeJapanese -> setKeysInJapaneseText()
            InputMode.ModeEnglish -> setKeysInEnglishText()
            InputMode.ModeNumber -> setKeysInNumberText()
        }
    }

    private fun setKeysCursorMoveMode() {
        binding.apply {
            key1.text = ""
            key2.text = ""
            key3.text = ""
            key4.text = ""
            key5.text = ""
            key6.text = ""
            key7.text = ""
            key8.text = ""
            key9.text = ""
            key11.text = ""
            key12.text = ""
            keySmallLetter.text = ""
            keySmallLetter.setCompoundDrawablesWithIntrinsicBounds(null, null, null, null)
            setButtonImageOrText(keyReturn, cachedUndoDrawable, customUndoDrawable, customTextUndoStr)
            sideKeySymbol.text = ""
            sideKeySymbol.setCompoundDrawables(null, null, null, null)
            keyMoveCursorRight.text = ""
            keyMoveCursorRight.setCompoundDrawables(null, null, null, null)
            keySoftLeft.text = ""
            keySoftLeft.setCompoundDrawables(null, null, null, null)
            keyDelete.text = ""
            keyDelete.setCompoundDrawables(null, null, null, null)
        }
    }

    /** Populate all main keys with Japanese labels **/
    private fun setKeysTextsInSelectMode() {
        val copyIcon = cachedContentCopyDrawable
        copyIcon?.apply {
            setBounds(
                0,
                0,
                intrinsicWidth,
                intrinsicHeight
            )
        }

        val cutIcon = cachedContentCutDrawable

        cutIcon?.apply {
            setBounds(
                0,
                0,
                intrinsicWidth,
                intrinsicHeight
            )
        }

        val shareIcon = cachedContentShareDrawable
        shareIcon?.apply {
            setBounds(
                0,
                0,
                intrinsicWidth,
                intrinsicHeight
            )
        }

        val selectAllIcon = cachedContentSelectDrawable
        selectAllIcon?.apply {
            setBounds(
                0,
                0,
                intrinsicWidth,
                intrinsicHeight
            )
        }

        binding.apply {
            key1.apply {
                text = "コピー"
                textSize = 12f
                setCompoundDrawables(copyIcon, null, null, null)
            }
            key2.text = ""
            key3.apply {
                text = "切り取り"
                textSize = 12f
                setCompoundDrawables(cutIcon, null, null, null)
            }
            key4.text = ""
            key5.text = ""
            key6.text = ""
            key7.apply {
                text = "全て選択"
                textSize = 12f
                setCompoundDrawables(selectAllIcon, null, null, null)
            }
            key8.text = ""
            key9.apply {
                text = "共有"
                textSize = 12f
                setCompoundDrawables(shareIcon, null, null, null)
            }

            keyEnter.visibility = View.INVISIBLE
            keySwitchKeyMode.visibility = View.INVISIBLE
            key11.visibility = View.INVISIBLE
            key12.visibility = View.INVISIBLE
            keySmallLetter.visibility = View.INVISIBLE

            setButtonImageOrText(keyReturn, cachedUndoDrawable, customUndoDrawable, customTextUndoStr)
            sideKeySymbol.text = ""
            sideKeySymbol.setCompoundDrawables(null, null, null, null)
            keySpace.apply {
                text = "⟲"
                setCompoundDrawablesWithIntrinsicBounds(null, null, null, null)
            }
            setCursorKeyIconOrText(keyMoveCursorRight, customRightDrawable.takeIf { hasCustomRightIcon }, "▶")
            setCursorKeyIconOrText(keySoftLeft, customLeftDrawable.takeIf { hasCustomLeftIcon }, "◀")
            setButtonImageOrText(keyDelete, cachedBackSpaceDrawable, customDeleteDrawable, customTextDeleteStr)
            normalizeSpecialKeys()
        }
    }

    /** Populate all main keys with Japanese labels **/
    private fun setKeysInJapaneseText() {
        binding.apply {
            key1.apply {
                setJapaneseTextFor(key1)
                setCompoundDrawables(null, null, null, null)
            }
            setJapaneseTextFor(key2)
            key3.apply {
                setJapaneseTextFor(key3)
                setCompoundDrawables(null, null, null, null)
            }
            setJapaneseTextFor(key4)
            setJapaneseTextFor(key5)
            setJapaneseTextFor(key6)
            key7.apply {
                setJapaneseTextFor(key7)
                setCompoundDrawables(null, null, null, null)
            }
            setJapaneseTextFor(key8)
            key9.apply {
                setJapaneseTextFor(key9)
                setCompoundDrawables(null, null, null, null)
            }
            setJapaneseTextFor(key11)
            setJapaneseTextFor(key12)
            keySmallLetter.text = "ﾞ ﾟ"
            keySmallLetter.setCompoundDrawablesWithIntrinsicBounds(null, null, null, null)
            resetFromSelectMode(binding)
            setCursorKeyIconOrText(keyMoveCursorRight, customRightDrawable.takeIf { hasCustomRightIcon }, "▶")
            setCursorKeyIconOrText(keySoftLeft, customLeftDrawable.takeIf { hasCustomLeftIcon }, "◀")
            setButtonImageOrText(keyDelete, cachedBackSpaceDrawable, customDeleteDrawable, customTextDeleteStr)
            normalizeSpecialKeys()
        }
    }

    /** Populate all main keys with English labels **/
    private fun setKeysInEnglishText() {
        binding.apply {
            key1.apply {
                setTenKeyTextEnglish(
                    key1.id,
                    delta = keySizeDelta,
                    modeTheme = themeMode,
                    colorTextInt = customKeyTextColor
                )
                setCompoundDrawables(null, null, null, null)
            }
            key2.apply {
                setTenKeyTextEnglish(
                    key2.id,
                    delta = keySizeDelta,
                    modeTheme = themeMode,
                    colorTextInt = customKeyTextColor
                )
                setCompoundDrawables(null, null, null, null)
            }
            key3.setTenKeyTextEnglish(
                key3.id, delta = keySizeDelta, modeTheme = themeMode,
                colorTextInt = customKeyTextColor
            )
            key4.setTenKeyTextEnglish(
                key4.id, delta = keySizeDelta, modeTheme = themeMode,
                colorTextInt = customKeyTextColor
            )
            key5.setTenKeyTextEnglish(
                key5.id, delta = keySizeDelta, modeTheme = themeMode,
                colorTextInt = customKeyTextColor
            )
            key6.setTenKeyTextEnglish(
                key6.id, delta = keySizeDelta, modeTheme = themeMode,
                colorTextInt = customKeyTextColor
            )
            key7.apply {
                setTenKeyTextEnglish(
                    key7.id, delta = keySizeDelta, modeTheme = themeMode,
                    colorTextInt = customKeyTextColor
                )
                setCompoundDrawables(null, null, null, null)
            }
            key8.setTenKeyTextEnglish(
                key8.id, delta = keySizeDelta, modeTheme = themeMode,
                colorTextInt = customKeyTextColor
            )
            key9.apply {
                setTenKeyTextEnglish(
                    key9.id, delta = keySizeDelta, modeTheme = themeMode,
                    colorTextInt = customKeyTextColor
                )
                setCompoundDrawables(null, null, null, null)
            }
            key11.setTenKeyTextEnglish(
                key11.id, delta = keySizeDelta, modeTheme = themeMode,
                colorTextInt = customKeyTextColor
            )
            key12.setTenKeyTextEnglish(
                key12.id, delta = keySizeDelta, modeTheme = themeMode,
                colorTextInt = customKeyTextColor
            )
            resetFromSelectMode(binding)
            setCursorKeyIconOrText(keyMoveCursorRight, customRightDrawable.takeIf { hasCustomRightIcon }, "▶")
            setCursorKeyIconOrText(keySoftLeft, customLeftDrawable.takeIf { hasCustomLeftIcon }, "◀")
            keySmallLetter.text = "a/A"
            keySmallLetter.setCompoundDrawablesWithIntrinsicBounds(null, null, null, null)
            setButtonImageOrText(keyDelete, cachedBackSpaceDrawable, customDeleteDrawable, customTextDeleteStr)
            normalizeSpecialKeys()
        }
    }

    /** Populate all main keys with Number labels **/
    private fun setKeysInNumberText() {
        binding.apply {
            key1.apply {
                setTenKeyTextNumber(
                    key1.id, delta = keySizeDelta, modeTheme = themeMode,
                    colorTextInt = customKeyTextColor
                )
                setCompoundDrawables(null, null, null, null)
            }
            key2.setTenKeyTextNumber(
                key2.id, delta = keySizeDelta, modeTheme = themeMode,
                colorTextInt = customKeyTextColor
            )
            key3.apply {
                setTenKeyTextNumber(
                    key3.id, delta = keySizeDelta, modeTheme = themeMode,
                    colorTextInt = customKeyTextColor
                )
                setCompoundDrawables(null, null, null, null)
            }
            key4.setTenKeyTextNumber(
                key4.id, delta = keySizeDelta, modeTheme = themeMode,
                colorTextInt = customKeyTextColor
            )
            key5.setTenKeyTextNumber(
                key5.id, delta = keySizeDelta, modeTheme = themeMode,
                colorTextInt = customKeyTextColor
            )
            key6.setTenKeyTextNumber(
                key6.id, delta = keySizeDelta, modeTheme = themeMode,
                colorTextInt = customKeyTextColor
            )
            key7.apply {
                setTenKeyTextNumber(
                    key7.id, delta = keySizeDelta, modeTheme = themeMode,
                    colorTextInt = customKeyTextColor
                )
                setCompoundDrawables(null, null, null, null)
            }
            key8.setTenKeyTextNumber(
                key8.id, delta = keySizeDelta, modeTheme = themeMode,
                colorTextInt = customKeyTextColor
            )
            key9.apply {
                setTenKeyTextNumber(
                    key9.id, delta = keySizeDelta, modeTheme = themeMode,
                    colorTextInt = customKeyTextColor
                )
                setCompoundDrawables(null, null, null, null)
            }
            key11.setTenKeyTextNumber(
                key11.id, delta = keySizeDelta, modeTheme = themeMode,
                colorTextInt = customKeyTextColor
            )
            key12.setTenKeyTextNumber(
                key12.id, delta = keySizeDelta, modeTheme = themeMode,
                colorTextInt = customKeyTextColor
            )

            resetFromSelectMode(binding)
            setCursorKeyIconOrText(keyMoveCursorRight, customRightDrawable.takeIf { hasCustomRightIcon }, "▶")
            setCursorKeyIconOrText(keySoftLeft, customLeftDrawable.takeIf { hasCustomLeftIcon }, "◀")
            keySmallLetter.text = "()[]"
            keySmallLetter.setCompoundDrawablesWithIntrinsicBounds(null, null, null, null)
            setButtonImageOrText(keyDelete, cachedBackSpaceDrawable, customDeleteDrawable, customTextDeleteStr)
            normalizeSpecialKeys()
        }
    }

    private fun resetFromSelectMode(binding: KeyboardLayoutBinding) {
        binding.apply {
            keyReturn.apply {
                visibility = View.VISIBLE
                setButtonImageOrText(this, cachedUndoDrawable, customUndoDrawable, customTextUndoStr)
            }
            sideKeySymbol.apply {
                visibility = View.VISIBLE
                setButtonImageOrText(this, cachedSymbolDrawable, customEmojiDrawable, customTextEmojiStr)
            }
            keySpace.apply {
                visibility = View.VISIBLE
                setSideKeySpaceDrawable(null)
            }
            keyEnter.visibility = View.VISIBLE
            keySwitchKeyMode.visibility = View.VISIBLE
            key11.visibility = View.VISIBLE
            key12.visibility = View.VISIBLE
            keySmallLetter.visibility = View.VISIBLE
            normalizeSpecialKeys()
        }
    }

    /** Mark all key Views as non‐focusable so touches go directly to onTouch **/
    private fun setViewsNotFocusable() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            binding.key1.focusable = View.NOT_FOCUSABLE
            binding.key2.focusable = View.NOT_FOCUSABLE
            binding.key3.focusable = View.NOT_FOCUSABLE
            binding.key4.focusable = View.NOT_FOCUSABLE
            binding.key5.focusable = View.NOT_FOCUSABLE
            binding.key6.focusable = View.NOT_FOCUSABLE
            binding.key7.focusable = View.NOT_FOCUSABLE
            binding.key8.focusable = View.NOT_FOCUSABLE
            binding.key9.focusable = View.NOT_FOCUSABLE
            binding.key11.focusable = View.NOT_FOCUSABLE
            binding.key12.focusable = View.NOT_FOCUSABLE
            binding.keySmallLetter.focusable = View.NOT_FOCUSABLE

            binding.keyReturn.focusable = View.NOT_FOCUSABLE
            binding.keySoftLeft.focusable = View.NOT_FOCUSABLE
            binding.sideKeySymbol.focusable = View.NOT_FOCUSABLE
            binding.keySwitchKeyMode.focusable = View.NOT_FOCUSABLE
            binding.keyDelete.focusable = View.NOT_FOCUSABLE
            binding.keyMoveCursorRight.focusable = View.NOT_FOCUSABLE
            binding.keySpace.focusable = View.NOT_FOCUSABLE
            binding.keyEnter.focusable = View.NOT_FOCUSABLE
        } else {
            binding.key1.isFocusable = false
            binding.key2.isFocusable = false
            binding.key3.isFocusable = false
            binding.key4.isFocusable = false
            binding.key5.isFocusable = false
            binding.key6.isFocusable = false
            binding.key7.isFocusable = false
            binding.key8.isFocusable = false
            binding.key9.isFocusable = false
            binding.key11.isFocusable = false
            binding.key12.isFocusable = false
            binding.keySmallLetter.isFocusable = false

            binding.keyReturn.isFocusable = false
            binding.keySoftLeft.isFocusable = false
            binding.sideKeySymbol.isFocusable = false
            binding.keySwitchKeyMode.isFocusable = false
            binding.keyDelete.isFocusable = false
            binding.keyMoveCursorRight.isFocusable = false
            binding.keySpace.isFocusable = false
            binding.keyEnter.isFocusable = false
        }
    }

    private var customEnterDrawable: Drawable? = null
    private var customSpaceDrawable: Drawable? = null
    private var customLeftDrawable: Drawable? = null
    private var customRightDrawable: Drawable? = null

    private var customModeSwitchDrawable: Drawable? = null
    private var customUndoDrawable: Drawable? = null
    private var customEmojiDrawable: Drawable? = null
    private var customDeleteDrawable: Drawable? = null

    private var customTextModeSwitchStr: String = ""
    private var customTextUndoStr: String = ""
    private var customTextEmojiStr: String = ""
    private var customTextDeleteStr: String = ""

    private var customTextEnterStr: String = ""
    private var customTextSpaceStr: String = ""
    private var customTextSymbolStr: String = ""
    private var customText123Str: String = ""

    private val specialKeyTextSizeSp = 18f
    private val specialKeyAutoSizeMinSp = 7
    private val specialKeyIconSizeDp = 24

    private fun fitDrawable(drawable: Drawable?, targetDp: Int = 24): Drawable? {
        if (drawable == null) return null
        val density = resources.displayMetrics.density
        val targetPx = (targetDp * density).toInt()
        val originalWidth = drawable.intrinsicWidth
        val originalHeight = drawable.intrinsicHeight
        val width: Int
        val height: Int
        if (originalWidth > 0 && originalHeight > 0) {
            val ratio = originalWidth.toFloat() / originalHeight.toFloat()
            if (ratio > 1f) {
                width = targetPx
                height = (targetPx / ratio).toInt()
            } else {
                width = (targetPx * ratio).toInt()
                height = targetPx
            }
        } else {
            width = targetPx
            height = targetPx
        }
        drawable.setBounds(0, 0, width, height)
        return drawable
    }

    private fun tintDefaultSpecialKeyDrawable(drawable: Drawable?): Drawable? {
        if (drawable == null) return null
        val wrapped = DrawableCompat.wrap(drawable.mutate())
        val tintColor = if (themeMode == "custom") {
            customSpecialKeyTextColor
        } else {
            ContextCompat.getColor(context, com.kazumaproject.core.R.color.keyboard_icon_color)
        }
        DrawableCompat.setTint(wrapped, tintColor)
        return wrapped
    }

    private class CenteredDrawableSpan(
        private val drawable: Drawable
    ) : ReplacementSpan() {
        override fun getSize(
            paint: Paint,
            text: CharSequence?,
            start: Int,
            end: Int,
            fm: Paint.FontMetricsInt?
        ): Int {
            val rect = drawable.bounds
            fm?.let {
                val fontMetrics = paint.fontMetricsInt
                val fontHeight = fontMetrics.descent - fontMetrics.ascent
                val drawableHeight = rect.height()
                val centerY = fontMetrics.ascent + fontHeight / 2
                it.ascent = centerY - drawableHeight / 2
                it.descent = centerY + drawableHeight / 2
                it.top = it.ascent
                it.bottom = it.descent
            }
            return rect.width()
        }

        override fun draw(
            canvas: Canvas,
            text: CharSequence?,
            start: Int,
            end: Int,
            x: Float,
            top: Int,
            y: Int,
            bottom: Int,
            paint: Paint
        ) {
            val rect = drawable.bounds
            val transY = top + (bottom - top - rect.height()) / 2
            canvas.save()
            canvas.translate(x, transY.toFloat())
            drawable.draw(canvas)
            canvas.restore()
        }
    }

    private fun setCenteredIcon(
        button: AppCompatButton,
        drawable: Drawable?,
        tintAsDefaultIcon: Boolean = false
    ) {
        val iconDrawable = if (tintAsDefaultIcon) tintDefaultSpecialKeyDrawable(drawable) else drawable
        val fittedDrawable = fitDrawable(iconDrawable, specialKeyIconSizeDp)
        button.setCompoundDrawables(null, null, null, null)
        if (fittedDrawable == null) {
            button.text = ""
            return
        }
        val text = SpannableString(" ")
        text.setSpan(
            CenteredDrawableSpan(fittedDrawable),
            0,
            text.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        button.text = text
    }

    private fun setCursorKeyIconOrText(
        button: AppCompatButton,
        customDrawable: Drawable?,
        fallbackText: String
    ) {
        prepareSpecialKeyButton(button)
        if (customDrawable != null) {
            setCenteredIcon(button, customDrawable)
        } else {
            button.setCompoundDrawables(null, null, null, null)
            button.text = fallbackText
            customTypeface?.let { button.typeface = it }
        }
    }

    private fun restoreSmallLetterKey() {
        binding.keySmallLetter.setCompoundDrawables(null, null, null, null)
        binding.keySmallLetter.text = when (currentInputMode.value) {
            InputMode.ModeJapanese -> "ﾞ ﾟ"
            InputMode.ModeEnglish -> "a/A"
            InputMode.ModeNumber -> "()[]"
        }
        prepareSpecialKeyButton(binding.keySmallLetter)
    }

    private fun restoreKeyContent(button: AppCompatButton) {
        when (button) {
            binding.keySpace -> setSideKeySpaceDrawable(null)
            binding.keySmallLetter -> restoreSmallLetterKey()
            binding.keySoftLeft -> setCursorKeyIconOrText(
                button,
                customLeftDrawable.takeIf { hasCustomLeftIcon },
                "◀"
            )
            binding.keyMoveCursorRight -> setCursorKeyIconOrText(
                button,
                customRightDrawable.takeIf { hasCustomRightIcon },
                "▶"
            )
            binding.keyReturn -> setButtonImageOrText(
                button,
                cachedUndoDrawable,
                customUndoDrawable,
                customTextUndoStr
            )
            binding.sideKeySymbol -> setButtonImageOrText(
                button,
                cachedSymbolDrawable,
                customEmojiDrawable,
                customTextSymbolStr.ifEmpty { customTextEmojiStr }
            )
            binding.keyDelete -> setButtonImageOrText(
                button,
                cachedBackSpaceDrawable,
                customDeleteDrawable,
                customTextDeleteStr
            )
            binding.keyEnter -> setButtonImageOrText(
                button,
                cachedArrowRightAltDrawable,
                customEnterDrawable,
                customTextEnterStr
            )
            binding.keySwitchKeyMode -> {
                binding.keySwitchKeyMode.setInputMode(currentInputMode.value, false)
                applyModeSwitchCustomization()
            }
            else -> {
                when (currentInputMode.value) {
                    InputMode.ModeJapanese -> setJapaneseTextFor(button)
                    InputMode.ModeEnglish -> button.setTenKeyTextEnglish(
                        button.id,
                        delta = keySizeDelta,
                        modeTheme = themeMode,
                        colorTextInt = customKeyTextColor
                    )
                    InputMode.ModeNumber -> button.setTenKeyTextNumber(
                        button.id,
                        delta = keySizeDelta,
                        modeTheme = themeMode,
                        colorTextInt = customKeyTextColor
                    )
                }
            }
        }
        customTypeface?.let { button.typeface = it }
    }

    private fun prepareSpecialKeyButton(
        button: AppCompatButton,
        autoSizeText: Boolean = false
    ) {
        button.gravity = Gravity.CENTER
        button.includeFontPadding = false
        button.minWidth = 0
        button.minHeight = 0
        button.minimumWidth = 0
        button.minimumHeight = 0
        button.setPadding(0, 0, 0, 0)
        button.compoundDrawablePadding = 0
        button.isSingleLine = true
        button.maxLines = 1
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, specialKeyTextSizeSp)
        if (autoSizeText) {
            androidx.core.widget.TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
                button,
                specialKeyAutoSizeMinSp,
                specialKeyTextSizeSp.toInt(),
                1,
                TypedValue.COMPLEX_UNIT_SP
            )
        } else {
            androidx.core.widget.TextViewCompat.setAutoSizeTextTypeWithDefaults(
                button,
                androidx.core.widget.TextViewCompat.AUTO_SIZE_TEXT_TYPE_NONE
            )
        }
    }

    private fun normalizeSpecialKeys() {
        binding.apply {
            listOf(
                keyReturn,
                keySoftLeft,
                sideKeySymbol,
                keyDelete,
                keyMoveCursorRight,
                keySpace,
                keyEnter,
                keySmallLetter
            ).forEach { prepareSpecialKeyButton(it, autoSizeText = it != keySmallLetter && it.text.length > 3) }
        }
    }

    private fun applyModeSwitchCustomization() {
        val customText = customText123Str.ifEmpty { customTextModeSwitchStr }
        if (customText.isEmpty() && customModeSwitchDrawable == null) {
            binding.keySwitchKeyMode.setInputMode(currentInputMode.value, false)
        } else {
            setButtonImageOrText(
                binding.keySwitchKeyMode,
                cachedLanguageDrawable,
                customModeSwitchDrawable,
                customText
            )
        }
        binding.keySwitchKeyMode.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        customTypeface?.let { binding.keySwitchKeyMode.typeface = it }
    }

    private fun setButtonImageOrText(
        button: AppCompatButton,
        defaultDrawable: Drawable?,
        customDrawable: Drawable?,
        customText: String
    ) {
        if (customText.isNotEmpty()) {
            prepareSpecialKeyButton(button, autoSizeText = customText.length > 3)
            button.text = customText
            button.setCompoundDrawables(null, null, null, null)
        } else {
            prepareSpecialKeyButton(button)
            if (customDrawable != null) {
                button.text = ""
                setCenteredIcon(button, customDrawable)
            } else {
                if (defaultDrawable != null) {
                    button.text = ""
                    setCenteredIcon(button, defaultDrawable, tintAsDefaultIcon = true)
                } else {
                    button.text = if (button == binding.keyReturn) "↶" else ""
                    button.setCompoundDrawables(null, null, null, null)
                }
            }
        }
    }

    fun applySpecialKeyCustomizations() {
        applyModeSwitchCustomization()
        setButtonImageOrText(
            binding.keyReturn,
            cachedUndoDrawable,
            customUndoDrawable,
            customTextUndoStr
        )
        setButtonImageOrText(
            binding.sideKeySymbol,
            cachedSymbolDrawable,
            customEmojiDrawable,
            customTextSymbolStr.ifEmpty { customTextEmojiStr }
        )
        setButtonImageOrText(
            binding.keyDelete,
            cachedBackSpaceDrawable,
            customDeleteDrawable,
            customTextDeleteStr
        )
        setSideKeySpaceDrawable(null)
        setButtonImageOrText(
            binding.keyEnter,
            cachedArrowRightAltDrawable,
            customEnterDrawable,
            customTextEnterStr
        )
        normalizeSpecialKeys()
        customTypeface?.let { setCustomTypeface(it) }
    }

    private var customTypeface: android.graphics.Typeface? = null

    private fun applyTypefaceToPopups() {
        val type = customTypeface ?: return
        if (::popTextActive.isInitialized) popTextActive.typeface = type
        if (::popTextLeft.isInitialized) popTextLeft.typeface = type
        if (::popTextTop.isInitialized) popTextTop.typeface = type
        if (::popTextRight.isInitialized) popTextRight.typeface = type
        if (::popTextBottom.isInitialized) popTextBottom.typeface = type
        if (::popTextCenter.isInitialized) popTextCenter.typeface = type
    }

    fun setCustomTypeface(typeface: android.graphics.Typeface?) {
        this.customTypeface = typeface
        val views = listOf(
            binding.key1, binding.key2, binding.key3, binding.key4,
            binding.key5, binding.key6, binding.key7, binding.key8,
            binding.key9, binding.key11, binding.key12, binding.keySmallLetter,
            binding.keySoftLeft, binding.keyMoveCursorRight, binding.sideKeySymbol, binding.keySpace,
            binding.keyEnter, binding.keySwitchKeyMode, binding.keyReturn, binding.keyDelete
        )
        views.forEach { view ->
            when (view) {
                is androidx.appcompat.widget.AppCompatButton -> {
                    view.setTypeface(typeface)
                }
                is com.google.android.material.textview.MaterialTextView -> {
                    view.setTypeface(typeface)
                }
            }
        }
        applyTypefaceToPopups()
    }
}
