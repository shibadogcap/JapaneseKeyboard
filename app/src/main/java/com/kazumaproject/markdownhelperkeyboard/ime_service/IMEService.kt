package com.kazumaproject.markdownhelperkeyboard.ime_service

import android.annotation.SuppressLint
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.hardware.input.InputManager
import android.inputmethodservice.InputMethodService
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CombinedVibration
import android.os.Handler
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.text.Spannable
import android.text.SpannableString
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.UnderlineSpan
import android.view.Gravity
import android.view.InputDevice
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.Window
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.inputmethod.CompletionInfo
import android.view.inputmethod.CorrectionInfo
import android.view.inputmethod.CursorAnchorInfo
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedText
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputContentInfo
import android.view.inputmethod.InputMethodInfo
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.PopupWindow
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.ColorInt
import androidx.appcompat.view.ContextThemeWrapper
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.toColorInt
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.inputmethod.InputConnectionCompat
import androidx.core.view.inputmethod.InputContentInfoCompat
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.DynamicColorsOptions
import com.google.android.material.tabs.TabLayout
import com.kazumaproject.android.flexbox.FlexDirection
import com.kazumaproject.android.flexbox.FlexboxLayoutManager
import com.kazumaproject.android.flexbox.JustifyContent
import com.kazumaproject.core.data.clicked_symbol.SymbolMode
import com.kazumaproject.core.data.clipboard.ClipboardItem
import com.kazumaproject.core.data.floating_candidate.CandidateItem
import com.kazumaproject.core.data.popup.FlickPopupViewStyleSet
import com.kazumaproject.core.data.popup.PopupViewStyle
import com.kazumaproject.core.data.popup.QwertyPopupViewStyleSet
import com.kazumaproject.core.domain.extensions.dpToPx
import com.kazumaproject.core.domain.extensions.getThemeColorOrFallback
import com.kazumaproject.core.domain.extensions.isLightColor
import com.kazumaproject.core.domain.extensions.hiraganaToKatakana
import com.kazumaproject.core.domain.extensions.isAsciiDigitForRomajiQwerty
import com.kazumaproject.core.domain.extensions.isAsciiSymbolForRomajiQwerty
import com.kazumaproject.core.domain.extensions.kanjiCount
import com.kazumaproject.core.domain.extensions.setDrawableAlpha
import com.kazumaproject.core.domain.extensions.setDrawableSolidColor
import com.kazumaproject.core.domain.extensions.setLayerTypeSolidColor
import com.kazumaproject.core.domain.extensions.toHankakuAlphabet
import com.kazumaproject.core.domain.extensions.toHankakuKatakana
import com.kazumaproject.core.domain.extensions.toHankakuKigou
import com.kazumaproject.core.domain.extensions.toHiragana
import com.kazumaproject.core.domain.extensions.toRomajiQwertyOutputChar
import com.kazumaproject.core.domain.extensions.toZenkaku
import com.kazumaproject.core.domain.extensions.toZenkakuAlphabet
import com.kazumaproject.core.domain.extensions.toZenkakuKatakana
import com.kazumaproject.core.domain.key.Key
import com.kazumaproject.core.domain.listener.FlickListener
import com.kazumaproject.core.domain.listener.LongPressListener
import com.kazumaproject.core.domain.listener.QWERTYKeyListener
import com.kazumaproject.core.domain.physical_keyboard.FloatingCandidateTailResolver
import com.kazumaproject.core.domain.physical_keyboard.KanaDakutenComposer
import com.kazumaproject.core.domain.physical_keyboard.PhysicalKanaMapper
import com.kazumaproject.core.domain.physical_keyboard.PhysicalKeyboardInputMode
import com.kazumaproject.core.domain.qwerty.QWERTYKey
import com.kazumaproject.core.domain.state.GestureType
import com.kazumaproject.core.domain.state.InputMode
import com.kazumaproject.core.domain.state.TenKeyQWERTYMode
import com.kazumaproject.core.domain.window.getScreenHeight
import com.kazumaproject.custom_keyboard.data.FlickDirection
import com.kazumaproject.custom_keyboard.data.KeyAction
import com.kazumaproject.custom_keyboard.data.KeyActionMapper
import com.kazumaproject.custom_keyboard.data.KeyboardInputMode
import com.kazumaproject.custom_keyboard.data.KeyboardLayout
import com.kazumaproject.custom_keyboard.data.KeyboardLayoutUsageMode
import com.kazumaproject.custom_keyboard.layout.KeyboardDefaultLayouts
import com.kazumaproject.custom_keyboard.layout.KeyboardDefaultLayouts.DeleteKeyFlickSettings
import com.kazumaproject.custom_keyboard.view.FlickKeyboardView
import com.kazumaproject.data.clicked_symbol.ClickedSymbol
import com.kazumaproject.data.emoji.Emoji
import com.kazumaproject.data.emoticon.Emoticon
import com.kazumaproject.data.symbol.Symbol
import com.kazumaproject.domain.EmojiSkinToneSupport
import com.kazumaproject.listeners.ClipboardHistoryToggleListener
import com.kazumaproject.listeners.ClipboardItemAction
import com.kazumaproject.listeners.DeleteButtonSymbolViewClickListener
import com.kazumaproject.listeners.DeleteButtonSymbolViewLongClickListener
import com.kazumaproject.listeners.ReturnToTenKeyButtonClickListener
import com.kazumaproject.listeners.SymbolRecyclerViewItemClickListener
import com.kazumaproject.listeners.SymbolRecyclerViewItemLongClickListener
import com.kazumaproject.markdownhelperkeyboard.BuildConfig
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.clipboard_history.database.ClipboardHistoryItem
import com.kazumaproject.markdownhelperkeyboard.clipboard_history.database.ItemType
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyCandidateLearningPolicy
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyCandidateLearningPolicyInput
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyPostCommitPredictionPolicy
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyPostCommitPredictionPolicyInput
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyStyleCandidateRanker
import com.kazumaproject.markdownhelperkeyboard.converter.api.CandidateRequestBridge
import com.kazumaproject.markdownhelperkeyboard.ime_service.candidate.ImeCandidateCoordinator
import com.kazumaproject.markdownhelperkeyboard.ime_service.candidate.ImeCandidatePresentationCoordinator
import com.kazumaproject.markdownhelperkeyboard.ime_service.candidate.ImeSuggestionOrchestrator
import com.kazumaproject.markdownhelperkeyboard.ime_service.candidate.ImeZenzContextBuilder
import com.kazumaproject.markdownhelperkeyboard.ime_service.editor.EditorGateway
import com.kazumaproject.markdownhelperkeyboard.ime_service.input.HardwareKeyboardCoordinator
import com.kazumaproject.markdownhelperkeyboard.ime_service.input.InputActionDispatcher
import com.kazumaproject.markdownhelperkeyboard.ime_service.input.PhysicalKeyboardUiEffectHandler
import com.kazumaproject.markdownhelperkeyboard.ime_service.ui.FloatingImeKeyboardSurface
import com.kazumaproject.markdownhelperkeyboard.ime_service.ui.KeyboardSurfaceCoordinator
import com.kazumaproject.markdownhelperkeyboard.ime_service.ui.MainImeKeyboardSurface
import com.kazumaproject.markdownhelperkeyboard.ime_service.ui.TapFlickInputBridge
import com.kazumaproject.markdownhelperkeyboard.ime_service.ui.TapFlickSurfaceActionsFactory
import com.kazumaproject.markdownhelperkeyboard.ime_service.input.InputModeKeyHandlers
import com.kazumaproject.markdownhelperkeyboard.ime_service.state.ImeSessionState
import com.kazumaproject.markdownhelperkeyboard.ime_service.candidate.ImeCandidatePreferences
import com.kazumaproject.markdownhelperkeyboard.ime_service.candidate.ImeCandidatePreferencesBuilder
import com.kazumaproject.markdownhelperkeyboard.ime_service.candidate.ImeCandidateRuntimeSession
import com.kazumaproject.markdownhelperkeyboard.ime_service.candidate.ImeCandidateRequestFactory
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyStyleLearningType
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyLiveConversionMode

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyStyleConvertRequestOptions
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyStylePredictionMode
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyRuntimeConversionPolicy
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyStyleZenzaiMode
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.adjustCandidate

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CandidateRequest
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CandidateRequestMode
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CandidateRequestPrivacy
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CandidateType
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CandidateLane


import com.kazumaproject.markdownhelperkeyboard.converter.candidate.QWERTY_GLIDE_CANDIDATE_TYPE
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyTransitionLearningPolicy
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyTransitionLearningPolicyInput
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.ZenzCandidate
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.ZenzaiCandidateEvaluationResult
import com.kazumaproject.markdownhelperkeyboard.converter.engine.EnglishEngine
import com.kazumaproject.markdownhelperkeyboard.converter.engine.KanaKanjiEngine
import com.kazumaproject.markdownhelperkeyboard.converter.glide.QwertyGlidePrebuiltDictionaryLoader
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.CustomKeyboardLayout
import com.kazumaproject.markdownhelperkeyboard.databinding.FloatingKeyboardLayoutBinding
import com.kazumaproject.markdownhelperkeyboard.databinding.MainLayoutBinding
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.DictionaryCategory
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.DictionaryBinaryReader
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.DictionaryOverrideStore
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.DictionarySourceResolver
import com.kazumaproject.markdownhelperkeyboard.gemma.GemmaTranslationManager
import com.kazumaproject.markdownhelperkeyboard.gemma.database.GemmaPromptTemplate
import com.kazumaproject.markdownhelperkeyboard.ime_service.adapters.FloatingCandidateListAdapter
import com.kazumaproject.markdownhelperkeyboard.ime_service.adapters.GridSpacingItemDecoration
import com.kazumaproject.markdownhelperkeyboard.ime_service.adapters.ShortcutAdapter
import com.kazumaproject.markdownhelperkeyboard.ime_service.adapters.SuggestionAdapter
import com.kazumaproject.markdownhelperkeyboard.ime_service.clipboard.ClipboardUtil
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.correctReading
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.getCurrentInputTypeForIME2
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.getEnterKeyIndexSumire
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.getLastCharacterAsString
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.getQWERTYReturnTextInEn
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.getQWERTYReturnTextInJp
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.isAllEnglishLetters
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.isAllHiraganaWithSymbols
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.isPassword
import com.kazumaproject.markdownhelperkeyboard.ime_service.floating_view.BubbleTextView
import com.kazumaproject.markdownhelperkeyboard.ime_service.floating_view.FloatingDockListener
import com.kazumaproject.markdownhelperkeyboard.ime_service.floating_view.FloatingDockView
import com.kazumaproject.markdownhelperkeyboard.ime_service.models.CandidateShowFlag
import com.kazumaproject.markdownhelperkeyboard.ime_service.models.SymbolKeyboardState
import com.kazumaproject.markdownhelperkeyboard.ime_service.romaji_kana.RomajiKanaConverter
import com.kazumaproject.markdownhelperkeyboard.ime_service.state.CandidateTab
import com.kazumaproject.markdownhelperkeyboard.ime_service.state.InputTypeForIME
import com.kazumaproject.markdownhelperkeyboard.ime_service.state.KeyboardType
import com.kazumaproject.markdownhelperkeyboard.learning.database.LearnEntity
import com.kazumaproject.markdownhelperkeyboard.learning.multiple.LearnMultiple
import com.kazumaproject.markdownhelperkeyboard.ng_word.database.NgWord
import com.kazumaproject.markdownhelperkeyboard.physical_keyboard.shortcut.PhysicalKeyboardShortcutAction
import com.kazumaproject.markdownhelperkeyboard.physical_keyboard.shortcut.PhysicalKeyboardShortcutContext
import com.kazumaproject.markdownhelperkeyboard.physical_keyboard.shortcut.PhysicalShortcutMatcher
import com.kazumaproject.markdownhelperkeyboard.physical_keyboard.shortcut.database.PhysicalKeyboardShortcutItem
import com.kazumaproject.markdownhelperkeyboard.repository.CandidateOrderOverrideRepository
import com.kazumaproject.markdownhelperkeyboard.repository.ClickedSymbolRepository
import com.kazumaproject.markdownhelperkeyboard.repository.ClipboardHistoryRepository
import com.kazumaproject.markdownhelperkeyboard.repository.DeleteKeyFlickDeleteTargetRepository
import com.kazumaproject.markdownhelperkeyboard.repository.GemmaPromptTemplateRepository
import com.kazumaproject.markdownhelperkeyboard.repository.KeyboardRepository
import com.kazumaproject.markdownhelperkeyboard.repository.LearnRepository
import com.kazumaproject.markdownhelperkeyboard.repository.NgWordRepository
import com.kazumaproject.markdownhelperkeyboard.repository.PhysicalKeyboardShortcutRepository
import com.kazumaproject.markdownhelperkeyboard.repository.RomajiMapRepository
import com.kazumaproject.markdownhelperkeyboard.repository.ShortcutRepository
import com.kazumaproject.markdownhelperkeyboard.repository.SystemUserDictionaryRepository
import com.kazumaproject.markdownhelperkeyboard.repository.UserDictionaryRepository
import com.kazumaproject.markdownhelperkeyboard.repository.UserTemplateRepository
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import com.kazumaproject.markdownhelperkeyboard.setting_activity.MainActivity
import com.kazumaproject.markdownhelperkeyboard.setting_activity.circular_slot.CircularSlotActionApplier
import com.kazumaproject.markdownhelperkeyboard.short_cut.ShortcutType
import com.kazumaproject.markdownhelperkeyboard.sumire_special_key.SumireSpecialKeyActionDisplayMetadata
import com.kazumaproject.markdownhelperkeyboard.sumire_special_key.SumireSpecialKeyActionDisplayOverrideApplier
import com.kazumaproject.markdownhelperkeyboard.sumire_special_key.SumireSpecialKeyActionResolver
import com.kazumaproject.markdownhelperkeyboard.sumire_special_key.SumireSpecialKeyPlacementOverrideApplier
import com.kazumaproject.markdownhelperkeyboard.sumire_special_key.SumireSpecialKeyRepository
import com.kazumaproject.markdownhelperkeyboard.sumire_special_key.database.SumireSpecialKeyActionOverrideEntity
import com.kazumaproject.markdownhelperkeyboard.sumire_special_key.database.SumireSpecialKeyPlacementOverrideEntity
import com.kazumaproject.markdownhelperkeyboard.variant.AppVariantConfig
import com.kazumaproject.qwerty_keyboard.ui.QWERTYKeyboardView
import com.kazumaproject.symbol_keyboard.CustomSymbolKeyboardView
import com.kazumaproject.tenkey.TenKey
import com.kazumaproject.tenkey.extensions.getDakutenFlickLeft
import com.kazumaproject.tenkey.extensions.getDakutenFlickRight
import com.kazumaproject.tenkey.extensions.getDakutenFlickTop
import com.kazumaproject.tenkey.extensions.getDakutenSmallChar
import com.kazumaproject.tenkey.extensions.getNextInputChar
import com.kazumaproject.tenkey.extensions.getNextReturnInputChar
import com.kazumaproject.tenkey.extensions.isHiragana
import com.kazumaproject.tenkey.extensions.isLatinAlphabet
import com.kazumaproject.tenkey.extensions.toggleDakutenWithSeion
import com.kazumaproject.tenkey.extensions.toggleHandakutenWithSeion
import com.kazumaproject.zenz.ZenzEngine
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.BreakIterator
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import java.util.regex.Pattern
import javax.inject.Inject
import com.google.android.material.R as MaterialR

@AndroidEntryPoint
class IMEService : InputMethodService(), LifecycleOwner, InputConnection,
    ClipboardHistoryToggleListener, InputManager.InputDeviceListener {

    private sealed class CandidateLongPressAction {
        object HideWord : CandidateLongPressAction()
        object Translate : CandidateLongPressAction()
        data class CustomPrompt(
            val template: GemmaPromptTemplate
        ) : CandidateLongPressAction()

        object Close : CandidateLongPressAction()
    }

    private enum class SuggestionProgressReason {
        CandidateTranslation,
        VoiceInput,
        QwertyGlideDecode
    }

    private data class BunsetsuSegmentState(
        val reading: String,
        val displayText: String,
        val candidates: List<Candidate> = emptyList(),
        val selectedIndex: Int = 0
    )

    private data class BunsetsuConversionSession(
        val rawInput: String,
        val conversionInput: String,
        val segments: List<BunsetsuSegmentState>,
        val tailText: String = "",
        val focusedIndex: Int = 0,
        val splitPatterns: List<List<Int>> = emptyList(),
        val activeSplitPatternIndex: Int = 0
    )

    private data class ReconversionEntry(
        val committedText: String,
        val reading: String
    )

    private data class BunsetsuReconversionDraft(
        val originalReading: String,
        val committedText: String = ""
    )

    private sealed class SelectedTextGemmaAction {
        object Translate : SelectedTextGemmaAction()
        data class CustomPrompt(val template: GemmaPromptTemplate) : SelectedTextGemmaAction()
    }

    private data class SelectedTextGemmaSession(
        val selectedText: String,
        val actions: List<SelectedTextGemmaAction>
    )

    @Inject
    lateinit var learnMultiple: LearnMultiple

    @Inject
    lateinit var appPreference: AppPreference

    @Inject
    lateinit var inputMethodManager: InputMethodManager

    @Inject
    lateinit var kanaKanjiEngine: KanaKanjiEngine

    @Inject
    lateinit var azooKeyDictionaryAssetProvider: com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyDictionaryAssetProvider

    @Inject
    lateinit var dictionarySourceResolver: DictionarySourceResolver

    @Inject
    lateinit var dictionaryOverrideStore: DictionaryOverrideStore

    @Inject
    lateinit var dictionaryBinaryReader: DictionaryBinaryReader

    @Inject
    lateinit var englishEngine: EnglishEngine

    @Inject
    lateinit var learnRepository: LearnRepository

    @Inject
    lateinit var zenzConversionService: com.kazumaproject.markdownhelperkeyboard.converter.zenz.ZenzConversionService

    @Inject
    lateinit var learningMemoryRepository: com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyLearningMemoryRepository

    @Inject
    lateinit var userDictionaryRepository: UserDictionaryRepository

    @Inject
    lateinit var systemUserDictionaryRepository: SystemUserDictionaryRepository

    @Inject
    lateinit var userTemplateRepository: UserTemplateRepository

    @Inject
    lateinit var candidateOrderOverrideRepository: CandidateOrderOverrideRepository

    @Inject
    lateinit var clickedSymbolRepository: ClickedSymbolRepository

    @Inject
    lateinit var deleteKeyFlickDeleteTargetRepository: DeleteKeyFlickDeleteTargetRepository

    @Inject
    lateinit var clipboardHistoryRepository: ClipboardHistoryRepository

    @Inject
    lateinit var keyboardRepository: KeyboardRepository

    @Inject
    lateinit var romajiMapRepository: RomajiMapRepository

    @Inject
    lateinit var ngWordRepository: NgWordRepository

    @Inject
    lateinit var shortCurRepository: ShortcutRepository

    @Inject
    lateinit var physicalKeyboardShortcutRepository: PhysicalKeyboardShortcutRepository

    @Inject
    lateinit var clipboardUtil: ClipboardUtil

    @Inject
    lateinit var gemmaTranslationManager: GemmaTranslationManager

    @Inject
    lateinit var gemmaPromptTemplateRepository: GemmaPromptTemplateRepository

    @Inject
    lateinit var sumireSpecialKeyRepository: SumireSpecialKeyRepository

    @Inject
    lateinit var candidateCoordinator: ImeCandidateCoordinator

    private val liveConversionManager = com.kazumaproject.markdownhelperkeyboard.ime_service.candidate.LiveConversionManager(enabled = false)

    private val suggestionOrchestrator: ImeSuggestionOrchestrator by lazy {
        ImeSuggestionOrchestrator(candidateCoordinator)
    }

    private val editorGateway: EditorGateway by lazy {
        EditorGateway(
            connectionProvider = { currentInputConnection },
            onComposingChange = {
                suppressSelectionCleanupForInternalPreEditMove()
                cancelCandidateTranslationIfComposingChanges(it)
            },
            onPreEditMutation = {
                suppressSelectionCleanupForInternalPreEditMove()
                cancelCandidateTranslationIfPreEditMutates()
            },
            onCommit = {
                suppressSelectionCleanupForInternalPreEditMove()
                clearFunctionKeyConversionSource()
                cancelCandidateTranslationIfPreEditMutates()
            },
            onFinishComposing = {
                suppressSelectionCleanupForInternalPreEditMove()
                clearFunctionKeyConversionSource()
                cancelCandidateTranslationIfPreEditMutates()
            },
        )
    }

    private val inputActionDispatcher = InputActionDispatcher()

    private val physicalKeyboardUiEffectHandler = PhysicalKeyboardUiEffectHandler()

    private val hardwareKeyboardCoordinator: HardwareKeyboardCoordinator by lazy {
        HardwareKeyboardCoordinator(inputManager)
    }

    private val keyboardSurfaceCoordinator = KeyboardSurfaceCoordinator()

    private val candidateSurfaceHost = object : KeyboardSurfaceCoordinator.CandidateDisplayHost {
        override fun updateMainSuggestionAdapters(candidates: List<Candidate>) {
            suggestionAdapter?.suggestions = candidates
            suggestionAdapterFull?.suggestions = candidates
        }

        override fun updateFloatingCandidateBar(items: List<CandidateItem>) {
            updateSuggestionsForFloatingCandidate(items)
        }
    }

    private val candidatePresentationCoordinator: ImeCandidatePresentationCoordinator by lazy {
        ImeCandidatePresentationCoordinator(candidatePresentationHost)
    }

    private val candidatePresentationHost = object : ImeCandidatePresentationCoordinator.Host {
        override fun bunsetsuSeparationEnabled(): Boolean = bunsetsuSeparation == true

        override fun currentBunsetsuPositionList(): List<Int>? = bunsetsuPositionList

        override fun applyBunsetsuUiState(state: ImeCandidatePresentationCoordinator.BunsetsuUiState) {
            bunsetsuSplitPatterns = state.splitPatterns
            bunsetsuPositionList = state.positionList
        }

        override fun stringInTailLength(): Int = stringInTail.get().length

        override fun shouldApplyCandidateResult(insertString: String, requestToken: Long): Boolean =
            this@IMEService.shouldApplyCandidateResult(insertString, requestToken)

        override suspend fun updateDisplayedCandidates(
            insertString: String,
            candidates: List<Candidate>,
        ) {
            this@IMEService.updateDisplayedCandidates(insertString, candidates)
        }

        override suspend fun applyLiveConversion(
            insertString: String,
            candidates: List<Candidate>,
            firstClauseResults: List<Candidate>
        ) {
            this@IMEService.applyLiveConversionIfNeeded(insertString, candidates, firstClauseResults)
        }

        override fun updateBunsetsuSpaceKeyIfNeeded(
            mainView: MainLayoutBinding,
            candidates: List<Candidate>,
            insertString: String,
        ) {
            this@IMEService.updateBunsetsuSpaceKeyIfNeeded(mainView, candidates, insertString)
        }

        override fun notifyAsyncZenzIfNeeded(
            insertString: String,
            result: com.kazumaproject.markdownhelperkeyboard.ime_service.candidate.ImeCandidateSuggestResult,
        ) {
            if (result.emitAsyncZenzGeneration || result.emitAsyncZenzai) {
                scope.launch {
                    val fullReading = inputString.value + stringInTail.get()
                    val cursorPosition = inputString.value.length
                    _zenzRequest.emit(ZenzRequestParams(fullReading, cursorPosition))
                }
            }
        }

        override fun maybeLaunchZenzRerank(
            requestToken: Long,
            insertString: String,
            baseCandidates: List<Candidate>,
            plan: com.kazumaproject.markdownhelperkeyboard.ime_service.candidate.ImeCandidateZenzRerankPlan,
            mainView: MainLayoutBinding,
        ) {
            this@IMEService.maybeLaunchZenzRerank(requestToken, insertString, baseCandidates, plan, mainView)
        }
    }

    private val physicalKeyboardPresenceHost =
        object : HardwareKeyboardCoordinator.PhysicalKeyboardPresenceHost {
            override fun clearZenzContextCache() = this@IMEService.clearZenzContextCache()

            override fun dismissFloatingDock() {
                floatingDockWindow?.dismiss()
            }

            override fun dismissFloatingModeSwitch() {
                floatingModeSwitchWindow?.dismiss()
            }

            override fun dismissFloatingCandidate() {
                floatingCandidateWindow?.dismiss()
            }

            override fun setHasHardwareKeyboardConnected(connected: Boolean) {
                hasHardwareKeyboardConnected = connected
            }

            override fun schedulePhysicalKeyboardEnableEmit(enabled: Boolean) {
                hardwareKeyboardCoordinator.schedulePhysicalKeyboardEnableEmit(
                    enabled = enabled,
                    scope = scope,
                    emit = { _physicalKeyboardEnable.emit(it) },
                )
            }

            override fun setKeyboardFloatingMode(floating: Boolean) {
                isKeyboardFloatingMode = floating
            }
        }

    /** 画面上 QWERTY のローマ字生入力（`convertQWERTYZenkaku` 前）。ComposingText セッション用 */
    private var lastQwertyRomajiRawInput: String? = null

    private var zenzEngine: ZenzEngine? = null

    private var shortcutAdapter: ShortcutAdapter? = null

    private var romajiConverter: RomajiKanaConverter? = null
    private var azooKeyRoman2KanaTransducer: com.kazumaproject.markdownhelperkeyboard.converter.api.AzooKeyRoman2KanaTransducer =
        com.kazumaproject.markdownhelperkeyboard.converter.api.AzooKeyRoman2KanaTransducer.default()

    private lateinit var clipboardManager: ClipboardManager

    private var isClipboardHistoryFeatureEnabled: Boolean = false
    private val clipboardMutex = Mutex()
    private var isCustomKeyboardTwoWordsOutputEnable: Boolean? = false
    private var tenkeyQWERTYSwitchNumber: Boolean? = false
    private var tabletTenkeyQwertySwitchEnglish: Boolean = false
    private var tenkeyQKeymapGuide: Boolean? = false
    private var flickKeymapGuidePreference: Boolean? = false
    private var flickGuideTextSizeSpPreference: Int? = 9
    private var flickGuideMaxCharactersPreference: Int? = 1

    private var floatingCandidateWindow: PopupWindow? = null
    private lateinit var floatingCandidateView: View
    private lateinit var listAdapter: FloatingCandidateListAdapter

    private var floatingDockWindow: PopupWindow? = null
    private lateinit var floatingDockView: FloatingDockView

    private var floatingModeSwitchWindow: PopupWindow? = null
    private lateinit var floatingModeSwitchView: BubbleTextView

    private var floatingKeyboardView: PopupWindow? = null
    private var floatingKeyboardBinding: FloatingKeyboardLayoutBinding? = null

    /**
     * Floating QWERTY view に対して configureQwertyView() を実行済みかどうかを示すフラグ。
     *
     * Floating QWERTY の listener bind / 各 preference 適用は 1 回だけ行えば十分なため、
     * syncFloatingKeyboardContentForMode() からの再呼び出しによる listener 多重登録や
     * QWERTY 内部状態の不要な上書きを防ぐ。floatingKeyboardBinding が再生成された場合は
     * actionInDestroy 等でこのフラグを false に戻すこと。
     */
    private var isFloatingQwertyConfigured: Boolean = false
    private var keyboardBackgroundPlayer: ExoPlayer? = null
    private var floatingKeyboardBackgroundPlayer: ExoPlayer? = null
    private var floatingKeyboardBackgroundVideoConfig: KeyboardBackgroundVideoConfig? = null
    private var isKeyboardFloatingMode: Boolean? = false
    private var isKeyboardRounded: Boolean? = false
    private var keyboardCornerRadiusDp: Int = 32
    private var keyboardCornerTopLeft: Boolean = true
    private var keyboardCornerTopRight: Boolean = true
    private var keyboardCornerBottomLeft: Boolean = true
    private var keyboardCornerBottomRight: Boolean = true
    private var bunsetsuSeparation: Boolean? = false
    private var bunsetsuCursorMove: Boolean? = false
    private var reconversionEnabledPreference: Boolean = false
    private var bunsetsuPositionList: List<Int>? = emptyList()
    private var bunsetsuSplitPatterns: List<List<Int>> = emptyList()
    private var bunsetsuConversionSession: BunsetsuConversionSession? = null
    private var pendingReconversionEntry: ReconversionEntry? = null
    private var bunsetsuReconversionDraft: BunsetsuReconversionDraft? = null
    private var preserveBunsetsuReconversionDraftOnNextProcessInput = false
    private var isRestoringReconversionInput = false
    private var isToolbarForcedShow: Boolean = false
    private var lastShowSuggestion: Boolean = false
    private val clipboardPreviewFreshDurationMs = 45_000L
    private var clipboardPreviewFingerprint: String? = null
    private var clipboardPreviewShownAtMs: Long = 0L

    private data class KeyboardBackgroundVideoConfig(
        val uriString: String,
        val quality: String
    )

    private var henkanPressedWithBunsetsuDetect: Boolean = false
    private var conversionKeySwipePreference: Boolean? = false

    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false

    private var enableGemmaTranslationPreference: Boolean? = false

    private var cachedPrimaryClipContent: ClipboardItem? = null

    /**
     * クリップボードの内容が変更されたときに呼び出されるリスナー。
     */
    private val clipboardListener = ClipboardManager.OnPrimaryClipChangedListener {
        ioScope.launch {
            clipboardMutex.withLock {
                // 1. 現在クリップボードにあるアイテムを取得 (ClipboardItem.Text or Image)
                val newItem = clipboardUtil.getPrimaryClipContent()
                cachedPrimaryClipContent = newItem
                if (newItem is ClipboardItem.Empty) return@withLock
                if (isPrivateMode) return@withLock
                if (clipboardUtil.isPrimaryClipSensitive()) return@withLock
                markClipboardPreviewFresh(newItem)
                withContext(Dispatchers.Main) {
                    updateClipboardPreview()
                    mainLayoutBinding?.let { updateUpperAreaVisibility(it) }
                }

                // 2. DBに保存されている最新のメタデータを取得
                val lastSavedItem = clipboardHistoryRepository.getLatestItem()

                // 3. 重複チェック
                // 最新の実データを取得して比較 (テキストのみ。画像はパス比較などで代用検討)
                val isDuplicate = if (lastSavedItem == null) {
                    false
                } else {
                    when {
                        newItem is ClipboardItem.Text && lastSavedItem.itemType == ItemType.TEXT -> {
                            // DBのpreviewではなくファイルの実体と、現在のクリップボードを比較
                            val lastFullText = clipboardHistoryRepository.getFullText(lastSavedItem)
                            newItem.text == lastFullText
                        }

                        else -> false // 画像の厳密な比較はコストが高いため、一旦 false
                    }
                }

                // 4. 重複していなければ保存
                if (!isDuplicate) {
                    if (isClipboardHistoryFeatureEnabled) {
                        Timber.d("Saving new clipboard item to file and DB.")
                        // ここで Repository の新メソッドを呼ぶ (ファイル保存 + DB挿入)
                        clipboardHistoryRepository.insertFromClipboard(newItem)
                        cleanupExpiredClipboardItemsIfNeededNow()
                    }
                }
            }
        }
    }

    private var suggestionAdapter: SuggestionAdapter? = null
    private var suggestionAdapterFull: SuggestionAdapter? = null

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val ioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @Volatile
    private var lastAppliedDictionaryOverrideRevision: Long = Long.MIN_VALUE

    @Volatile
    private var dictionaryOverrideApplyJob: Job? = null

    private var cachedEmoji: List<Emoji>? = null
    private var cachedEmoticons: List<Emoticon>? = null
    private var cachedSymbols: List<Symbol>? = null
    private var cachedClickedSymbolHistory: List<ClickedSymbol>? = null
    private var currentClipboardItems: List<ClipboardItem> = emptyList()
    private var sumireSpecialKeyActionOverrides: List<SumireSpecialKeyActionOverrideEntity> =
        emptyList()
    private var sumireSpecialKeyPlacementOverrides: List<SumireSpecialKeyPlacementOverrideEntity> =
        emptyList()

    private var deleteLongPressJob: Job? = null
    private var rightLongPressJob: Job? = null
    private var leftLongPressJob: Job? = null
    private var candidateTranslationJob: Job? = null
    private var selectedTextGemmaActionJob: Job? = null
    private var postCommitPredictionJob: Job? = null
    private var manualProcessInputStringJob: Job? = null
    private var isPostCommitPredictionActive = false
    private val customGemmaPromptActionLimit = 5
    private val candidateTranslationRequestId = AtomicLong(0L)
    private var candidateTranslationContextSnapshot: String? = null
    private val selectedTextGemmaActionMenuRequestId = AtomicLong(0L)
    private val selectedTextGemmaActionRequestId = AtomicLong(0L)
    private val postCommitPredictionRequestId = AtomicLong(0L)
    private var selectedTextGemmaSession: SelectedTextGemmaSession? = null

    private var mainLayoutBinding: MainLayoutBinding? = null
    private val suggestionProgressReasons = mutableSetOf<SuggestionProgressReason>()
    private var isInputViewActive: Boolean = false
    private val _inputString = MutableStateFlow("")
    private val inputString = _inputString.asStateFlow()
    private var stringInTail = AtomicReference("")
    private var functionKeyConversionSource: String? = null
    private var suppressedSelectionCleanupCount = 0
    private var preservePreEditOnNextSelectionUpdate: String? = null
    private var suppressSelectionCleanupUntilMillis: Long = 0L
    private var isPromotingTail = false
    private var lastCommittedCandidate: com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate? = null
    private val _dakutenPressed = MutableStateFlow(false)
    private val _suggestionFlag = MutableSharedFlow<CandidateShowFlag>(replay = 0)
    private val suggestionFlag = _suggestionFlag.asSharedFlow()
    private val _suggestionViewStatus = MutableStateFlow(true)
    private val suggestionViewStatus = _suggestionViewStatus.asStateFlow()
    private val _keyboardSymbolViewState = MutableStateFlow(SymbolKeyboardState())
    private val keyboardSymbolViewState: StateFlow<SymbolKeyboardState> =
        _keyboardSymbolViewState.asStateFlow()
    private val clipboardSearchQuery = MutableStateFlow("")
    private val emojiSearchQuery = MutableStateFlow("")
    private val _tenKeyQWERTYMode = MutableStateFlow<TenKeyQWERTYMode>(TenKeyQWERTYMode.Default)
    private val qwertyMode = _tenKeyQWERTYMode.asStateFlow()
    private val _physicalKeyboardEnable = MutableSharedFlow<Boolean>(replay = 1)
    private val physicalKeyboardEnable: SharedFlow<Boolean> = _physicalKeyboardEnable

    private var currentInputType: InputTypeForIME = InputTypeForIME.Text
    private val lastFlickConvertedNextHiragana = AtomicBoolean(false)
    private val isContinuousTapInputEnabled = AtomicBoolean(false)
    private val englishSpaceKeyPressed = AtomicBoolean(false)
    private var suggestionClickNum = 0
    private val isHenkan = AtomicBoolean(false)
    private val onLeftKeyLongPressUp = AtomicBoolean(false)
    private val onRightKeyLongPressUp = AtomicBoolean(false)
    private val onDeleteLongPressUp = AtomicBoolean(false)
    private var onKeyboardSwitchLongPressUp = false
    private val deleteKeyLongKeyPressed = AtomicBoolean(false)
    private val rightCursorKeyLongKeyPressed = AtomicBoolean(false)
    private val leftCursorKeyLongKeyPressed = AtomicBoolean(false)
    private var isFlickOnlyMode: Boolean? = false
    private var isOmissionSearchEnable: Boolean? = false
    private var delayTime: Int? = 1000
    private var isLearnDictionaryMode: Boolean? = false
    private var isUserDictionaryEnable: Boolean? = false
    private var isUserTemplateEnable: Boolean? = false
    private var hankakuPreference: Boolean? = false
    private var customDirectModeSpaceHankakuPreference: Boolean = true
    private var isLiveConversionEnable: Boolean? = false
    private var liveConversionStartLength: Int = 1
    private var showLiveConversionCandidateYomi: Boolean = false
    private var liveConversionAutomaticCompletionStrength: String = "weak"
    private var nBest: Int? = 4
    private var flickSensitivityPreferenceValue: Int? = 100
    private var longPressTimeoutPreferenceValue: Int? = 300
    private var tenkeyShowIMEButtonPreference: Boolean? = true
    private var qwertyShowIMEButtonPreference: Boolean? = true
    private var qwertyShowEmojiButtonPreference: Boolean? = false
    private var defaultEmojiSkinTonePreference: String = EmojiSkinToneSupport.DEFAULT_SKIN_TONE
    private var qwertyEnableFlickUpPreference: Boolean? = false
    private var qwertyEnableFlickDownPreference: Boolean? = false
    private var qwertyNumberKeyFlickUpChars: Map<String, String> = emptyMap()
    private var qwertyNumberKeyFlickDownChars: Map<String, String> = emptyMap()
    private var qwertyEnableZenkakuSpacePreference: Boolean? = false
    private var qwertyRomajiHankakuNumberPreference: Boolean? = false
    private var qwertyRomajiHankakuSymbolPreference: Boolean? = false
    private var qwertyShowPopupWindowPreference: Boolean? = true
    private var qwertyGlideInputPreference: Boolean = false
    private var qwertyGlideCommitPreviousCandidateOnNewGlidePreference: Boolean = false
    private var qwertyGlideInsertSpaceAfterCommittingPreviousCandidatePreference: Boolean = false
    private var qwertyShowCursorButtonsPreference: Boolean? = false
    private var qwertyShowNumberButtonsPreference: Boolean? = false
    private var qwertyShowSwitchRomajiEnglishPreference: Boolean? = false
    private var qwertyShowKutoutenButtonsPreference: Boolean? = false
    private var qwertyShowKeymapSymbolsPreference: Boolean? = false
    private var qwertyRomajiShiftConversionPreference: Boolean? = false
    private var showCandidateInPasswordPreference: Boolean? = true
    private var tabletGojuonLayoutPreference: Boolean? = true
    private var isVibration: Boolean? = true
    private var vibrationTimingStr: String? = "both"
    private var isKeySoundEnabled: Boolean? = false
    private var keySoundVolumePercent: Int? = 0
    private var mozcUTPersonName: Boolean? = false
    private var mozcUTPlaces: Boolean? = false
    private var mozcUTWiki: Boolean? = false
    private var mozcUTNeologd: Boolean? = false
    private var mozcUTWeb: Boolean? = false
    private var switchQWERTYPassword: Boolean? = false
    private var landscapeForceQwertyPreference: Boolean? = false
    private var landscapeForceQwertyRomajiPreference: Boolean? = false
    private var clipboardPreviewVisibility: Boolean? = true
    private var clipboardPreviewTapToDelete: Boolean? = false
    private var isDeleteLeftFlickPreference: Boolean? = true
    private var currentKeyTypeface: android.graphics.Typeface? = null
    private var currentCandidateTypeface: android.graphics.Typeface? = null
    private var isDeleteUpFlickPreference: Boolean? = false
    private var isDeleteDownFlickPreference: Boolean? = false

    @Volatile
    private var deleteKeyFlickTargetChars: Set<Char> = DEFAULT_DELETE_KEY_FLICK_TARGETS
    private var tenkeyHeightPreferenceValue: Int? = 280
    private var tenkeyWidthPreferenceValue: Int? = 100
    private var qwertyHeightPreferenceValue: Int? = 280
    private var qwertyWidthPreferenceValue: Int? = 100
    private var candidateViewHeightPreferenceValue: Int? = 110
    private var candidateViewHeightEmptyPreferenceValue: Int? = 110
    private var tenkeyPositionPreferenceValue: Boolean? = true
    private var tenkeyBottomMarginPreferenceValue: Int? = 0
    private var qwertyPositionPreferenceValue: Boolean? = true
    private var qwertyBottomMarginPreferenceValue: Int? = 0

    private var tenkeyHeightLandScapePreferenceValue: Int? = 220
    private var tenkeyWidthLandScapePreferenceValue: Int? = 100
    private var qwertyHeightLandScapePreferenceValue: Int? = 220
    private var qwertyWidthLandScapePreferenceValue: Int? = 100
    private var candidateViewLandScapeHeightPreferenceValue: Int? = 110
    private var candidateViewLandScapeHeightEmptyPreferenceValue: Int? = 110
    private var tenkeyLandScapePositionPreferenceValue: Boolean? = true
    private var tenkeyLandScapeBottomMarginPreferenceValue: Int? = 0
    private var qwertyLandScapePositionPreferenceValue: Boolean? = true
    private var qwertyLandScapeBottomMarginPreferenceValue: Int? = 0

    private var tenkeyStartMarginPreferenceValue: Int? = 0
    private var tenkeyEndMarginPreferenceValue: Int? = 0
    private var qwertyStartMarginPreferenceValue: Int? = 0
    private var qwertyEndMarginPreferenceValue: Int? = 0

    private var tenkeyLandScapeStartMarginPreferenceValue: Int? = 0
    private var tenkeyLandScapeEndMarginPreferenceValue: Int? = 0
    private var qwertyLandScapeStartMarginPreferenceValue: Int? = 0
    private var qwertyLandScapeEndMarginPreferenceValue: Int? = 0

    private var enableShowLastShownKeyboardInRestart: Boolean? = false
    private var lastSavedKeyboardPosition: Int? = 0

    private var zenzEnableStatePreference: Boolean? = false
    private var zenzaiEnableStatePreference: Boolean? = false
    private var zenzProfilePreference: String? = ""
    private var zenzEnableLongPressConversionPreference: Boolean? = false
    private var zenzRerankPreference: Boolean? = false

    private var qwertyKeyVerticalMargin: Float? = 5.0f
    private var qwertyKeyHorizontalGap: Float? = 2.0f
    private var qwertyKeyIndentLarge: Float? = 23.0f
    private var qwertyKeyIndentSmall: Float? = 9.0f
    private var qwertyKeySideMargin: Float? = 4.0f
    private var qwertyKeyTextSize: Float? = 18.0f
    private var qwertySpecialKeyTextSize: Float? = 12.0f
    private var qwertySpecialKeyIconSize: Float? = 18.0f

    private var keyboardThemeMode: String? = "default"
    private var customThemeBgColor: Int? = Color.WHITE
    private var customThemeKeyColor: Int? = Color.LTGRAY
    private var customThemeSpecialKeyColor: Int? = Color.GRAY
    private var customThemeKeyTextColor: Int? = Color.BLACK
    private var customThemeSpecialKeyTextColor: Int? = Color.BLACK
    private var customThemeCandidateTextColor: Int? = Color.BLACK
    private var customThemeCandidateItemBgColor: Int? = Color.TRANSPARENT
    private var customThemeCandidateItemPressedBgColor: Int? = Color.WHITE
    private var customThemeShortcutIconColor: Int? = Color.BLACK
    private var customThemeEnterKeyColor: Int? = Color.BLUE
    private var customThemeEnterKeyTextColor: Int? = Color.WHITE
    private var customThemePopupBgColor: Int? = Color.WHITE
    private var customThemePopupTextColor: Int? = Color.BLACK

    private var liquidGlassThemePreference: Boolean? = false
    private var liquidGlassBlurRadiousPreference: Int? = 220
    private var liquidGlassKeyBlurRadiousPreference: Int? = 255

    private var customKeyBorderEnablePreference: Boolean? = false
    private var customKeyBorderEnableColor: Int? = Color.BLACK

    private var customComposingTextPreference: Boolean? = false

    private var inputCompositionBackgroundColor: Int? = "#440099CC".toColorInt()
    private var inputCompositionAfterBackgroundColor: Int? = "#770099CC".toColorInt()
    private var inputCompositionTextColor: Int? = Color.WHITE

    private var inputConversionBackgroundColor: Int? = "#55FF8800".toColorInt()
    private var inputConversionTextColor: Int? = Color.WHITE

    private var enableTypoCorrectionJapaneseFlickKeyboardPreference: Boolean? = false
    private var enableTypoCorrectionQwertyEnglishKeyboardPreference: Boolean? = false

    private var cachedPreferences: ImePreferencesSnapshot? = null
    private var cachedRuntimeConversionPolicy: AzooKeyRuntimeConversionPolicy? = null
    private var cachedRuntimeConversionPolicyInput: String? = null

    private var customIconEnterPath: String = ""
    private var customIconSpacePath: String = ""
    private var customIconArrowLeftPath: String = ""
    private var customIconArrowRightPath: String = ""
    private var customFontPath: String = ""

    @Deprecated(
        message = "Use the new input key type management system instead. This field is kept only for backward compatibility."
    )
    private var sumireInputKeyType: String? = "flick-default"
    private var sumireInputKeyLayoutType: String? = "toggle"
    private var sumireInputStyle: String? = "default"
    private var candidateColumns: String? = "1"
    private var candidateColumnsLandscape: String? = "1"
    private var candidateViewHeight: String? = "2"
    private var candidateTabVisibility: Boolean? = false
    private var symbolKeyboardFirstItem: SymbolMode? = SymbolMode.EMOJI
    private var userDictionaryPrefixMatchNumber: Int? = 2
    private var isTablet: Boolean? = false
    private var isNgWordEnable: Boolean? = false
    private var deleteKeyHighLight: Boolean? = true
    private var customKeyboardSuggestionPreference: Boolean? = true
    private var zenzDebounceTimePreference: Int? = 300
    private var zenzMaximumLetterSizePreference: Int? = 32
    private var zenzMaximumContextSizePreference: Int? = 512
    private var zenzMaximumThreadSizePreference: Int? = 4

    private var sumireEnglishQwertyPreference: Boolean? = false
    private var conversionCandidatesRomajiEnablePreference: Boolean? = false

    private var enableZenzRightContextPreference: Boolean? = false

    private var learnFirstCandidateDictionaryPreference: Boolean? = false
    private var enablePredictionSearchLearnDictionaryPreference: Boolean? = false
    private var learnPredictionPreference: Int? = 2
    private var circularFlickWindowScale: Float? = 1.0f
    private var circularFlickDirectionCount: Int? = 4

    private var customKeyBorderWidth: Int? = 1

    private var keyBorderEnable: Boolean? = false
    private var keyCornerRadiusDp: Int? = 8
    private var keyPopupStyle: String? = "default"

    private var qwertySwitchNumberKeyWithoutNumberPreference: Boolean? = false
    private var qwertyGlideInputCoordinator: QwertyGlideInputCoordinator? = null
    private var suppressNextQwertyGlideSuggestionRefresh: Boolean = false
    private var currentQwertyGlideCompositionText: String? = null

    private var customRomajiZenkakuConversionEnablePreference: Boolean? = true

    private var omissionSearchOffsetScorePreference: Int? = 1900
    private var enableTypoCorrectionJapaneseFlickKeyboardOffsetScorePreference: Int? = 3000

    private val _ngWordsList = MutableStateFlow<List<NgWord>>(emptyList())
    private val ngWordsList: StateFlow<List<NgWord>> = _ngWordsList
    private var cachedNgWordsStringList: List<String> = emptyList()
    private val _ngPattern = MutableStateFlow("".toRegex())
    private val ngPattern: StateFlow<Regex> = _ngPattern
    private var isPrivateMode = false

    private val _keyboardFloatingMode = MutableStateFlow(false)
    private val keyboardFloatingMode = _keyboardFloatingMode.asStateFlow()

    private var keyboardContainer: FrameLayout? = null

    private var isSpaceKeyLongPressed = false
    private val _selectMode = MutableStateFlow(false)
    private val selectMode: StateFlow<Boolean> = _selectMode

    private val _cursorMoveMode = MutableStateFlow(false)
    private val cursorMoveMode: StateFlow<Boolean> = _cursorMoveMode
    private var hasConvertedKatakana = false

    private val deletedBuffer = EditHistoryBuffer()
    private var activeDeleteHistoryBatch: DeleteHistoryBatch? = null

    private var keyboardOrder: List<KeyboardType> = emptyList()
    private var candidateTabOrder: List<CandidateTab> = emptyList()

    private var customLayouts: List<CustomKeyboardLayout> = emptyList()
    private var currentCustomKeyboardStableId: String? = null
    private var customKeyboardRenderJob: Job? = null
    private var numberKeyboardRenderJob: Job? = null

    private var currentNightMode: Int = 0

    private lateinit var lifecycleRegistry: LifecycleRegistry

    private lateinit var inputManager: InputManager

    private val cachedSpaceDrawable: Drawable? by lazy {
        ContextCompat.getDrawable(
            applicationContext, com.kazumaproject.core.R.drawable.baseline_space_bar_24
        )
    }
    private val cachedLogoDrawable: Drawable? by lazy {
        ContextCompat.getDrawable(
            applicationContext, com.kazumaproject.core.R.drawable.language_24dp
        )
    }
    private val cachedKanaDrawable: Drawable? by lazy {
        ContextCompat.getDrawable(applicationContext, com.kazumaproject.core.R.drawable.kana_small)
    }
    private val cachedHenkanDrawable: Drawable? by lazy {
        ContextCompat.getDrawable(applicationContext, com.kazumaproject.core.R.drawable.baseline_autorenew_24)
    }

    private val cachedNumberDrawable: Drawable? by lazy {
        ContextCompat.getDrawable(
            applicationContext, com.kazumaproject.core.R.drawable.number_small
        )
    }

    private val cachedArrowDropDownDrawable: Drawable? by lazy {
        ContextCompat.getDrawable(
            applicationContext, com.kazumaproject.core.R.drawable.outline_arrow_drop_down_24
        )
    }

    private val cachedArrowDropUpDrawable: Drawable? by lazy {
        ContextCompat.getDrawable(
            applicationContext, com.kazumaproject.core.R.drawable.outline_arrow_drop_up_24
        )
    }

    private val cachedArrowRightDrawable: Drawable? by lazy {
        ContextCompat.getDrawable(
            applicationContext, com.kazumaproject.core.R.drawable.baseline_arrow_right_alt_24
        )
    }

    private val cachedReturnDrawable: Drawable? by lazy {
        ContextCompat.getDrawable(
            applicationContext, com.kazumaproject.core.R.drawable.baseline_keyboard_return_24
        )
    }

    private val cachedTabDrawable: Drawable? by lazy {
        ContextCompat.getDrawable(
            applicationContext, com.kazumaproject.core.R.drawable.keyboard_tab_24px
        )
    }

    private val cachedCheckDrawable: Drawable? by lazy {
        ContextCompat.getDrawable(
            applicationContext, com.kazumaproject.core.R.drawable.baseline_check_24
        )
    }

    private val cachedSearchDrawable: Drawable? by lazy {
        ContextCompat.getDrawable(
            applicationContext, com.kazumaproject.core.R.drawable.baseline_search_24
        )
    }

    private val cachedEnglishDrawable: Drawable? by lazy {
        ContextCompat.getDrawable(
            applicationContext, com.kazumaproject.core.R.drawable.english_small
        )
    }

    companion object {
        private const val LONG_DELAY_TIME = 64L
        private const val DEFAULT_DELAY_MS = 1000L
        private const val PAGE_SIZE: Int = 5
        private const val ZENZ_RERANK_TOP_K = 4
        private const val ZENZ_RERANK_ALPHA = 0.7f
        private const val ZENZ_RERANK_BETA = 0.3f
        private const val ZENZ_LEFT_CONTEXT_MAX = 20
        private const val CANDIDATE_REFRESH_DEBOUNCE_MS = 12L
        private val DEFAULT_DELETE_KEY_FLICK_TARGETS =
            DeleteKeyFlickDeleteTargetRepository.DEFAULT_TARGET_SYMBOLS.toSet()
        private val ALWAYS_DELETE_KEY_FLICK_BOUNDARIES = setOf(' ', '　', '\n')

        private val passwordTypes = setOf(
            InputTypeForIME.TextWebPassword,
            InputTypeForIME.TextPassword,
            InputTypeForIME.NumberPassword,
            InputTypeForIME.TextVisiblePassword,
        )

        private val passwordTypesWithOutNumber = setOf(
            InputTypeForIME.TextWebPassword,
            InputTypeForIME.TextPassword,
            InputTypeForIME.TextVisiblePassword,
        )

        private val numberTypes = setOf(
            InputTypeForIME.Number,
            InputTypeForIME.NumberDecimal,
            InputTypeForIME.NumberPassword,
            InputTypeForIME.NumberSigned,
            InputTypeForIME.Phone,
            InputTypeForIME.Date,
            InputTypeForIME.Datetime,
            InputTypeForIME.Time,
        )

    }

    private var currentPage: Int = 0
    private var currentHighlightIndex: Int = RecyclerView.NO_POSITION
    private var fullSuggestionsList: List<CandidateItem> = emptyList()

    private var initialCursorDetectInFloatingCandidateView = false
    private var initialCursorXPosition: Int = 0

    private var physicalKeyboardFloatingXPosition = 200
    private var physicalKeyboardFloatingYPosition = 150

    private var dismissJob: Job? = null

    private var currentCustomKeyboardPosition = 0

    private var hasHardwareKeyboardConnected: Boolean? = false
    private var currentEnterKeyIndex: Int = 0 // 0:改行, 1:確定,
    private var currentDakutenKeyIndex: Int = 0 // 0:^_^, 1:゛゜
    private var currentSpaceKeyIndex: Int = 0 // 0: Space, 1: Convert
    private var currentKatakanaKeyIndex: Int = 0 // 0: SiwtchToNumber, 1: Katakana
    private val currentInputModeForSession: InputMode
        get() = inputActionDispatcher.keyboardMode.sessionMode
    private var currentQwertyRomajiModeForSession: Boolean = true

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var systemBottomInset = 0

    private var suppressSuggestions: Boolean = false
    private var isSystemUiRemoteInputSession: Boolean = false
    private var lastUpperAreaState: UpperAreaState? = null

    private var countToggleKatakana = 0

    private var hardKeyboardShiftPressd = false
    private var physicalKeyboardInputMode: PhysicalKeyboardInputMode =
        PhysicalKeyboardInputMode.ROMAJI
    private var physicalKeyboardShortcuts: List<PhysicalKeyboardShortcutItem> = emptyList()

    private var isDefaultRomajiHenkanMap = false

    private var bunsetusMultipleDetect = false

    private val _zenzCandidates = MutableStateFlow<List<ZenzCandidate>>(emptyList())
    private val zenzCandidates: StateFlow<List<ZenzCandidate>> = _zenzCandidates
    private var lastCandidate: String? = ""

    data class ZenzRequestParams(
        val insertReading: String,
        val cursorPosition: Int,
    )

    private val _zenzRequest = MutableSharedFlow<ZenzRequestParams>(
        extraBufferCapacity = 0
    )

    private val zenzRequest = _zenzRequest

    private val lastLocalUpdatedInput = MutableStateFlow("")

    private var addUserDictionaryPopup: PopupWindow? = null

    private var filteredCandidateList: List<Candidate>? = emptyList()
    private var zenzRerankJob: Job? = null
    private var zenzRerankRequestToken: Long = 0L
    private var zenzContextCacheInput: String? = null
    private var zenzContextCacheHardwareKeyboard: Boolean? = null
    private var zenzContextCacheLeftContext: String? = null
    private var zenzContextCache: com.kazumaproject.markdownhelperkeyboard.ime_service.candidate.ImeCandidateZenzContext? = null
    private var cachedCandidateLeftContext: String = ""
    private var symbolPanelSearchFocused = false

    private var previousTenKeyQWERTYMode: TenKeyQWERTYMode? = null

    private var currentKeyboardOrder = 0

    private data class UpperAreaState(
        val showSuggestion: Boolean,
        val hasCandidateSuggestions: Boolean,
        val privateMode: Boolean,
        val forcedToolbar: Boolean,
    )

    private data class ImeItem(
        val id: String,                 // imeId (InputMethodInfo.getId())
        val packageName: String,
        val settingsActivity: String?,   // 例: "com.example.ime.SettingsActivity"
        val label: CharSequence
    )

    private sealed class RowItem {
        data class Internal(val type: KeyboardType, val title: String) : RowItem()
        data class External(val ime: ImeItem) : RowItem()
    }

    private enum class DeleteDirection {
        BeforeCursor,
        AfterCursor
    }

    private sealed interface EditHistoryEntry {
        val previewText: String

        data class DeleteCommittedText(
            val deletedText: String,
            val direction: DeleteDirection = DeleteDirection.BeforeCursor
        ) : EditHistoryEntry {
            override val previewText: String = deletedText
        }

        data class CompositionChange(
            val beforeInput: String,
            val beforeTail: String,
            val afterInput: String,
            val afterTail: String,
            override val previewText: String
        ) : EditHistoryEntry

        data class ReplaceCommittedText(
            val beforeText: String,
            val afterText: String
        ) : EditHistoryEntry {
            override val previewText: String = beforeText
        }

    }

    private class EditHistoryBuffer {
        private val undoStack = ArrayDeque<EditHistoryEntry>()
        private val redoStack = ArrayDeque<EditHistoryEntry>()

        fun push(entry: EditHistoryEntry) {
            undoStack.addLast(entry)
            redoStack.clear()
        }

        fun popUndo(): EditHistoryEntry? {
            return if (undoStack.isEmpty()) null else undoStack.removeLast()
        }

        fun popRedo(): EditHistoryEntry? {
            return if (redoStack.isEmpty()) null else redoStack.removeLast()
        }

        fun pushRedo(entry: EditHistoryEntry) {
            redoStack.addLast(entry)
        }

        fun pushUndoFromRedo(entry: EditHistoryEntry) {
            undoStack.addLast(entry)
        }

        fun clear() {
            undoStack.clear()
            redoStack.clear()
        }

        fun isEmpty(): Boolean = undoStack.isEmpty() && redoStack.isEmpty()

        fun isNotEmpty(): Boolean = !isEmpty()

        fun hasUndoHistory(): Boolean = undoStack.isNotEmpty()

        fun hasRedoHistory(): Boolean = redoStack.isNotEmpty()

        fun peekUndoPreviewText(): String = undoStack.peekLast()?.previewText.orEmpty()

        fun peekRedoPreviewText(): String = redoStack.peekLast()?.previewText.orEmpty()
    }

    private data class DeleteHistoryBatch(
        val initialInput: String,
        val initialTail: String,
        val deletesCommittedText: Boolean,
        val deletedText: StringBuilder = StringBuilder()
    )

    // 設定値を保持するためのデータクラス
    private data class KeyboardSizePreferences(
        val heightPref: Int,
        val widthPref: Int,
        val bottomMargin: Int,
        val positionIsEnd: Boolean, // true: End, false: Start
        val candidateEmptyHeight: Int,
        val qwertyHeightPref: Int,
        val qwertyWidthPref: Int,
        val qwertyBottomMargin: Int,
        val qwertyPositionIsEnd: Boolean,
        val keyboardMarginStart: Int,
        val keyboardMarginEnd: Int,
        val qwertyMarginStart: Int,
        val qwertyMarginEnd: Int,
    )

    private data class KeyboardSurface(
        val rootView: View,
        val keyboardView: TenKey?,
        val tabletView: View?,
        val qwertyView: QWERTYKeyboardView?,
        val customLayout: FlickKeyboardView?,
        val suggestionRecyclerView: RecyclerView?,
        val symbolKeyboard: CustomSymbolKeyboardView?
    )

    override fun onCreate() {
        super.onCreate()
        Timber.d("onCreate")
        lifecycleRegistry = LifecycleRegistry(this)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        hasHardwareKeyboardConnected = false
        scope.launch { _physicalKeyboardEnable.emit(false) }

        if (AppVariantConfig.hasZenz) {
            zenzEngine = providesZenzEngine(this)
        }
        if (AppVariantConfig.hasGemma) {
            scope.launch {
                gemmaTranslationManager.initializeIfEnabled(forceReload = false)
            }
        }
        observeDeleteKeyFlickTargets()
        observeSumireSpecialKeyOverrides()

        suggestionAdapter = SuggestionAdapter().apply {
            onListUpdated = {
                if (isKeyboardFloatingMode != true) {
                    mainLayoutBinding?.apply {
                        setMainSuggestionColumn(this)
                        suggestionRecyclerView.scrollToPosition(0)
                        updateUpperAreaVisibility(this)
                    }
                }
            }
            onClipboardUpdated = {
                mainLayoutBinding?.let { updateUpperAreaVisibility(it) }
            }
        }
        suggestionAdapterFull = SuggestionAdapter()
        shortcutAdapter = ShortcutAdapter().apply {
            submitList(
                listOf(
                    ShortcutType.SETTINGS,
                    ShortcutType.EMOJI,
                    ShortcutType.TEMPLATE,
                    ShortcutType.COPY,
                    ShortcutType.PASTE,
                    ShortcutType.KEYBOARD_PICKER,
                    ShortcutType.CLIP_BOARD
                )
            )
        }
        currentNightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        clipboardManager =
            applicationContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboardManager.addPrimaryClipChangedListener(clipboardListener)
        isClipboardHistoryFeatureEnabled = appPreference.clipboard_history_enable ?: false
        cleanupExpiredClipboardItemsIfNeeded()

        inputManager = getSystemService(Context.INPUT_SERVICE) as InputManager
        inputManager.registerInputDeviceListener(this, null)
        floatingCandidateView = layoutInflater.inflate(R.layout.floating_candidate_layout, null)
        listAdapter = FloatingCandidateListAdapter(
            pageSize = PAGE_SIZE,
        )
        listAdapter.onSuggestionClicked = { suggestion: CandidateItem ->
            commitAndClearInput(suggestion.word)
        }
        listAdapter.onPagerClicked = {
            goToNextPageForFloatingCandidate()
        }
        ioScope.launch {
            val initialCustomLayouts = keyboardRepository.getLayoutsNotFlowEnsuringStableIds()
            withContext(Dispatchers.Main) {
                customLayouts = initialCustomLayouts
                currentCustomKeyboardStableId = customLayouts
                    .getOrNull(currentCustomKeyboardPosition)
                    ?.stableId
                    ?.takeIf { it.isNotBlank() }
            }
            shortCurRepository.initDefaultShortcutsIfNeeded()
            physicalKeyboardShortcutRepository.ensureDefaultShortcuts()
        }

        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        setSuggestionProgressVisible(
                            reason = SuggestionProgressReason.VoiceInput,
                            visible = true
                        )
                    }

                    override fun onBeginningOfSpeech() {

                    }

                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {
                        setSuggestionProgressVisible(
                            reason = SuggestionProgressReason.VoiceInput,
                            visible = false
                        )
                    }

                    override fun onError(error: Int) {
                        isListening = false
                        setSuggestionProgressVisible(
                            reason = SuggestionProgressReason.VoiceInput,
                            visible = false
                        )
                    }

                    override fun onResults(results: Bundle?) {
                        isListening = false
                        val matches =
                            results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val text = matches?.firstOrNull() ?: return
                        _inputString.update { text }
                        setSuggestionProgressVisible(
                            reason = SuggestionProgressReason.VoiceInput,
                            visible = false
                        )
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val matches =
                            partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val text = matches?.firstOrNull() ?: return
                        _inputString.update { text }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }
        }
    }

    private fun observeDeleteKeyFlickTargets() {
        ioScope.launch {
            deleteKeyFlickDeleteTargetRepository.ensureDefaultTargets()
            deleteKeyFlickDeleteTargetRepository.observeAll().collect { targets ->
                deleteKeyFlickTargetChars = targets.mapNotNull { target ->
                    target.symbol.singleOrNull()
                }.toSet()
            }
        }
    }

    private fun observeSumireSpecialKeyOverrides() {
        ioScope.launch {
            combine(
                sumireSpecialKeyRepository.observeAllPlacementOverrides(),
                sumireSpecialKeyRepository.observeAllActionOverrides()
            ) { placementOverrides, actionOverrides ->
                placementOverrides to actionOverrides
            }.collect { (placementOverrides, actionOverrides) ->
                sumireSpecialKeyPlacementOverrides = placementOverrides
                sumireSpecialKeyActionOverrides = actionOverrides
                withContext(Dispatchers.Main.immediate) {
                    if (qwertyMode.value == TenKeyQWERTYMode.Sumire) {
                        refreshActiveSumireLayoutIfNeeded()
                    } else {
                        renderCurrentKeyboardStateOnActiveSurface()
                    }
                }
            }
        }
    }

    override fun onCreateInputView(): View? {
        Timber.d("onCreateInputView")
        // もしコンテナがすでに存在している場合、システムが再追加できるように
        // 古い親から切り離す。
        keyboardContainer?.let {
            (it.parent as? ViewGroup)?.removeView(it)
        }

        // もしコンテナがまだ一度も作成されていない場合（初回起動時）のみ、
        // 作成とセットアップを行う。
        if (keyboardContainer == null) {
            isTablet = resources.getBoolean(com.kazumaproject.core.R.bool.isTablet)
            keyboardContainer = FrameLayout(this)

            // コンテナの内部にキーボードのUIをセットアップする
            setupKeyboardView()
            // 初回のみ実行したい他のセットアップ処理

            mainLayoutBinding?.let { mainView ->
                if (lifecycle.currentState == Lifecycle.State.CREATED) {
                    startScope(mainView)
                } else {
                    scope.coroutineContext.cancelChildren()
                    startScope(mainView)
                }
            }
        } else {
            setupKeyboardView()
            scope.coroutineContext.cancelChildren()
            mainLayoutBinding?.let { mainView ->
                startScope(mainView)
            }
        }
        return keyboardContainer
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        Timber.d("onStartInput: ${Build.MANUFACTURER}")
        Timber.d("onUpdate onStartInput called $restarting ${attribute?.imeOptions}")
        isSystemUiRemoteInputSession = isSystemUiRemoteInput(attribute)
        isTablet = resources.getBoolean(com.kazumaproject.core.R.bool.isTablet)
        resetAllFlags()
        physicalKeyboardFloatingXPosition = 200
        physicalKeyboardFloatingYPosition = 150
        _suggestionViewStatus.update { true }
        val preferences = ImePreferencesSnapshot.from(
            appPreference = appPreference,
            dictionarySourceResolver = dictionarySourceResolver,
            customThemeCandidateItemPressedBgColorDefault = ContextCompat.getColor(
                this,
                com.kazumaproject.core.R.color.qwety_key_bg_color
            )
        )
        applyImePreferences(preferences)
        initializeMozcDictionaries(preferences)
        suggestionAdapter?.updateCustomTabVisibility(preferences.customKeyboardSuggestionPreference)
        syncZenzLeftContextFromEditor()
    }

    private fun applyImePreferences(preferences: ImePreferencesSnapshot) {
        this.cachedPreferences = preferences
        cachedRuntimeConversionPolicy = null
        cachedRuntimeConversionPolicyInput = null
        val deleteKeyFlickPreferencesChanged =
            isDeleteLeftFlickPreference != preferences.isDeleteLeftFlickPreference ||
                    isDeleteUpFlickPreference != preferences.isDeleteUpFlickPreference ||
                    isDeleteDownFlickPreference != preferences.isDeleteDownFlickPreference
        val qwertyGlidePreferenceChanged =
            qwertyGlideInputPreference != preferences.qwertyGlideInputPreference

        keyboardOrder = preferences.keyboardOrder
        candidateTabOrder = preferences.candidateTabOrder
        mozcUTPersonName = preferences.mozcUTPersonName
        mozcUTPlaces = preferences.mozcUTPlaces
        mozcUTWiki = preferences.mozcUTWiki
        mozcUTNeologd = preferences.mozcUTNeologd
        mozcUTWeb = preferences.mozcUTWeb
        isFlickOnlyMode = preferences.isFlickOnlyMode
        isOmissionSearchEnable = preferences.isOmissionSearchEnable
        delayTime = preferences.delayTime
        isLearnDictionaryMode = preferences.isLearnDictionaryMode
        isUserDictionaryEnable = preferences.isUserDictionaryEnable
        isUserTemplateEnable = preferences.isUserTemplateEnable
        hankakuPreference = preferences.hankakuPreference
        customDirectModeSpaceHankakuPreference =
            preferences.customDirectModeSpaceHankakuPreference
        isLiveConversionEnable = preferences.isLiveConversionEnable
        liveConversionManager.enabled = preferences.isLiveConversionEnable ?: false
        liveConversionStartLength = preferences.liveConversionStartLength.coerceIn(1, 10)
        showLiveConversionCandidateYomi = preferences.showLiveConversionCandidateYomi
        liveConversionAutomaticCompletionStrength = preferences.liveConversionAutomaticCompletionStrength
        val shouldShowLiveConversionCandidateYomi =
            preferences.isLiveConversionEnable && preferences.showLiveConversionCandidateYomi
        listOfNotNull(suggestionAdapter, suggestionAdapterFull).forEach { adapter ->
            adapter.setShowCandidateYomiForLiveConversion(shouldShowLiveConversionCandidateYomi)
        }
        nBest = preferences.nBest
        flickSensitivityPreferenceValue = preferences.flickSensitivityPreferenceValue
        longPressTimeoutPreferenceValue = preferences.longPressTimeoutPreferenceValue
        qwertyShowIMEButtonPreference = preferences.qwertyShowIMEButtonPreference
        qwertyShowEmojiButtonPreference = preferences.qwertyShowEmojiButtonPreference
        tenkeyShowIMEButtonPreference = preferences.tenkeyShowIMEButtonPreference
        qwertyShowCursorButtonsPreference = preferences.qwertyShowCursorButtonsPreference
        qwertyShowNumberButtonsPreference = preferences.qwertyShowNumberButtonsPreference
        qwertyShowSwitchRomajiEnglishPreference =
            preferences.qwertyShowSwitchRomajiEnglishPreference
        qwertyGlideInputPreference = preferences.qwertyGlideInputPreference
        qwertyGlideCommitPreviousCandidateOnNewGlidePreference =
            preferences.qwertyGlideCommitPreviousCandidateOnNewGlidePreference
        qwertyGlideInsertSpaceAfterCommittingPreviousCandidatePreference =
            preferences.qwertyGlideInsertSpaceAfterCommittingPreviousCandidatePreference
        if (qwertyGlidePreferenceChanged) {
            qwertyGlideInputCoordinator?.cancelPending()
            englishEngine.invalidateQwertyGlideCache()
            currentQwertyGlideCompositionText = null
        }
        englishEngine.configureQwertyGlideDecoder(
            enabled = preferences.qwertyGlideInputPreference,
            canUseBundledPrebuiltIndex = !dictionarySourceResolver.shouldUseOverrideCategory(
                DictionaryCategory.ENGLISH
            ),
            prebuiltDictionaryLoader = QwertyGlidePrebuiltDictionaryLoader(dictionarySourceResolver),
        )
        qwertyShowPopupWindowPreference = preferences.qwertyShowPopupWindowPreference
        qwertyEnableFlickUpPreference = preferences.qwertyEnableFlickUpPreference
        qwertyEnableFlickDownPreference = preferences.qwertyEnableFlickDownPreference
        qwertyNumberKeyFlickUpChars = preferences.qwertyNumberKeyFlickUpChars
        qwertyNumberKeyFlickDownChars = preferences.qwertyNumberKeyFlickDownChars
        qwertyEnableZenkakuSpacePreference = preferences.qwertyEnableZenkakuSpacePreference
        qwertyRomajiHankakuNumberPreference = preferences.qwertyRomajiHankakuNumberPreference
        qwertyRomajiHankakuSymbolPreference = preferences.qwertyRomajiHankakuSymbolPreference
        qwertyShowKutoutenButtonsPreference = preferences.qwertyShowKutoutenButtonsPreference
        showCandidateInPasswordPreference = preferences.showCandidateInPasswordPreference
        qwertyShowKeymapSymbolsPreference = preferences.qwertyShowKeymapSymbolsPreference
        qwertyRomajiShiftConversionPreference = preferences.qwertyRomajiShiftConversionPreference
        tabletGojuonLayoutPreference = preferences.tabletGojuonLayoutPreference
        isNgWordEnable = preferences.isNgWordEnable
        deleteKeyHighLight = preferences.deleteKeyHighLight
        customKeyboardSuggestionPreference = preferences.customKeyboardSuggestionPreference
        userDictionaryPrefixMatchNumber = preferences.userDictionaryPrefixMatchNumber
        isVibration = preferences.isVibration
        vibrationTimingStr = preferences.vibrationTimingStr
        isKeySoundEnabled = preferences.isKeySoundEnabled
        keySoundVolumePercent = preferences.keySoundVolumePercent
        sumireInputKeyType = preferences.sumireInputKeyType
        sumireInputKeyLayoutType = preferences.sumireInputKeyLayoutType
        sumireInputStyle = preferences.sumireInputStyle
        candidateColumns = preferences.candidateColumns
        candidateColumnsLandscape = preferences.candidateColumnsLandscape
        candidateTabVisibility = preferences.candidateTabVisibility
        symbolKeyboardFirstItem = preferences.symbolKeyboardFirstItem
        defaultEmojiSkinTonePreference = preferences.defaultEmojiSkinTone
        isCustomKeyboardTwoWordsOutputEnable = preferences.isCustomKeyboardTwoWordsOutputEnable
        tenkeyQWERTYSwitchNumber = preferences.tenkeyQWERTYSwitchNumber
        tabletTenkeyQwertySwitchEnglish = preferences.tabletTenkeyQwertySwitchEnglish
        tenkeyQKeymapGuide = preferences.tenkeyQKeymapGuide
        flickKeymapGuidePreference = preferences.flickKeymapGuide
        flickGuideTextSizeSpPreference = preferences.flickGuideTextSizeSp
        flickGuideMaxCharactersPreference = preferences.flickGuideMaxCharacters
        isKeyboardFloatingMode = preferences.isKeyboardFloatingMode
        isKeyboardRounded = preferences.isKeyboardRounded
        keyboardCornerRadiusDp = preferences.keyboardCornerRadiusDp.coerceIn(0, 64)
        keyboardCornerTopLeft = preferences.keyboardCornerTopLeft
        keyboardCornerTopRight = preferences.keyboardCornerTopRight
        keyboardCornerBottomLeft = preferences.keyboardCornerBottomLeft
        keyboardCornerBottomRight = preferences.keyboardCornerBottomRight
        bunsetsuSeparation = preferences.bunsetsuSeparation
        bunsetsuCursorMove = preferences.bunsetsuCursorMove
        reconversionEnabledPreference = preferences.reconversionEnabled
        conversionKeySwipePreference = preferences.conversionKeySwipePreference
        physicalKeyboardInputMode =
            PhysicalKeyboardInputMode.fromPreferenceValue(preferences.physicalKeyboardInputMode)
        _keyboardFloatingMode.update { preferences.isKeyboardFloatingMode }
        switchQWERTYPassword = preferences.switchQWERTYPassword
        landscapeForceQwertyPreference = preferences.landscapeForceQwertyPreference
        landscapeForceQwertyRomajiPreference = preferences.landscapeForceQwertyRomajiPreference
        isDeleteLeftFlickPreference = preferences.isDeleteLeftFlickPreference
        isDeleteUpFlickPreference = preferences.isDeleteUpFlickPreference
        isDeleteDownFlickPreference = preferences.isDeleteDownFlickPreference
        if (deleteKeyFlickPreferencesChanged) {
            refreshDeleteKeyFlickPreferenceLayouts()
        }
        zenzDebounceTimePreference = preferences.zenzDebounceTimePreference
        zenzMaximumLetterSizePreference = preferences.zenzMaximumLetterSizePreference
        zenzMaximumContextSizePreference = preferences.zenzMaximumContextSizePreference
        zenzMaximumThreadSizePreference = preferences.zenzMaximumThreadSizePreference
        clipboardPreviewVisibility = preferences.clipboardPreviewVisibility
        clipboardPreviewTapToDelete = preferences.clipboardPreviewTapToDelete
        tenkeyHeightPreferenceValue = preferences.tenkeyHeightPreferenceValue
        tenkeyWidthPreferenceValue = preferences.tenkeyWidthPreferenceValue
        qwertyHeightPreferenceValue = preferences.qwertyHeightPreferenceValue
        qwertyWidthPreferenceValue = preferences.qwertyWidthPreferenceValue
        candidateViewHeightPreferenceValue = preferences.candidateViewHeightPreferenceValue
        candidateViewHeightEmptyPreferenceValue =
            preferences.candidateViewHeightEmptyPreferenceValue
        tenkeyPositionPreferenceValue = preferences.tenkeyPositionPreferenceValue
        tenkeyBottomMarginPreferenceValue = preferences.tenkeyBottomMarginPreferenceValue
        qwertyPositionPreferenceValue = preferences.qwertyPositionPreferenceValue
        qwertyBottomMarginPreferenceValue = preferences.qwertyBottomMarginPreferenceValue
        tenkeyStartMarginPreferenceValue = preferences.tenkeyStartMarginPreferenceValue
        tenkeyEndMarginPreferenceValue = preferences.tenkeyEndMarginPreferenceValue
        qwertyStartMarginPreferenceValue = preferences.qwertyStartMarginPreferenceValue
        qwertyEndMarginPreferenceValue = preferences.qwertyEndMarginPreferenceValue
        tenkeyLandScapeStartMarginPreferenceValue =
            preferences.tenkeyLandscapeStartMarginPreferenceValue
        tenkeyLandScapeEndMarginPreferenceValue =
            preferences.tenkeyLandscapeEndMarginPreferenceValue
        qwertyLandScapeStartMarginPreferenceValue =
            preferences.qwertyLandscapeStartMarginPreferenceValue
        qwertyLandScapeEndMarginPreferenceValue =
            preferences.qwertyLandscapeEndMarginPreferenceValue
        enableShowLastShownKeyboardInRestart =
            preferences.enableShowLastShownKeyboardInRestart
        lastSavedKeyboardPosition = preferences.lastSavedKeyboardPosition
        if (preferences.enableShowLastShownKeyboardInRestart) {
            currentKeyboardOrder =
                normalizeKeyboardOrderIndex(preferences.lastSavedKeyboardPosition)
            if (currentKeyboardOrder != preferences.lastSavedKeyboardPosition) {
                lastSavedKeyboardPosition = currentKeyboardOrder
                appPreference.save_last_used_keyboard_position_preference = currentKeyboardOrder
            }
        } else {
            currentKeyboardOrder = 0
        }
        tenkeyHeightLandScapePreferenceValue = preferences.tenkeyHeightLandscapePreferenceValue
        tenkeyWidthLandScapePreferenceValue = preferences.tenkeyWidthLandscapePreferenceValue
        qwertyHeightLandScapePreferenceValue = preferences.qwertyHeightLandscapePreferenceValue
        qwertyWidthLandScapePreferenceValue = preferences.qwertyWidthLandscapePreferenceValue
        candidateViewLandScapeHeightPreferenceValue =
            preferences.candidateViewLandscapeHeightPreferenceValue
        candidateViewLandScapeHeightEmptyPreferenceValue =
            preferences.candidateViewLandscapeHeightEmptyPreferenceValue
        tenkeyLandScapePositionPreferenceValue =
            preferences.tenkeyLandscapePositionPreferenceValue
        tenkeyLandScapeBottomMarginPreferenceValue =
            preferences.tenkeyLandscapeBottomMarginPreferenceValue
        qwertyLandScapePositionPreferenceValue =
            preferences.qwertyLandscapePositionPreferenceValue
        qwertyLandScapeBottomMarginPreferenceValue =
            preferences.qwertyLandscapeBottomMarginPreferenceValue
        zenzEnableStatePreference = preferences.zenzEnableStatePreference
        zenzaiEnableStatePreference = preferences.zenzaiEnableStatePreference
        zenzProfilePreference = preferences.zenzProfilePreference
        zenzEnableLongPressConversionPreference =
            preferences.zenzEnableLongPressConversionPreference
        zenzRerankPreference = preferences.zenzRerankPreference
        qwertyKeyVerticalMargin = preferences.qwertyKeyVerticalMargin
        qwertyKeyHorizontalGap = preferences.qwertyKeyHorizontalGap
        qwertyKeyIndentLarge = preferences.qwertyKeyIndentLarge
        qwertyKeyIndentSmall = preferences.qwertyKeyIndentSmall
        qwertyKeySideMargin = preferences.qwertyKeySideMargin
        qwertyKeyTextSize = preferences.qwertyKeyTextSize
        qwertySpecialKeyTextSize = preferences.qwertySpecialKeyTextSize
        qwertySpecialKeyIconSize = preferences.qwertySpecialKeyIconSize
        keyboardThemeMode = preferences.keyboardThemeMode
        customThemeBgColor = preferences.customThemeBgColor
        customThemeKeyColor = preferences.customThemeKeyColor
        customThemeSpecialKeyColor = preferences.customThemeSpecialKeyColor
        customThemeKeyTextColor = preferences.customThemeKeyTextColor
        customThemeSpecialKeyTextColor = preferences.customThemeSpecialKeyTextColor
        customThemeCandidateTextColor = preferences.customThemeCandidateTextColor
        customThemeCandidateItemBgColor = preferences.customThemeCandidateItemBgColor
        customThemeCandidateItemPressedBgColor = preferences.customThemeCandidateItemPressedBgColor
        customThemeShortcutIconColor = preferences.customThemeShortcutIconColor
        customThemeEnterKeyColor = preferences.customThemeEnterKeyColor
        customThemeEnterKeyTextColor = preferences.customThemeEnterKeyTextColor
        customThemePopupBgColor = preferences.customThemePopupBgColor
        customThemePopupTextColor = preferences.customThemePopupTextColor
        liquidGlassThemePreference = preferences.liquidGlassThemePreference
        liquidGlassBlurRadiousPreference = preferences.liquidGlassBlurRadiousPreference
        liquidGlassKeyBlurRadiousPreference = preferences.liquidGlassKeyBlurRadiousPreference
        customKeyBorderEnablePreference = preferences.customKeyBorderEnablePreference
        customKeyBorderEnableColor = preferences.customKeyBorderEnableColor
        customComposingTextPreference = preferences.customComposingTextPreference
        inputCompositionBackgroundColor = preferences.inputCompositionBackgroundColor
        inputCompositionTextColor = preferences.inputCompositionTextColor
        inputConversionBackgroundColor = preferences.inputConversionBackgroundColor
        inputConversionTextColor = preferences.inputConversionTextColor
        inputCompositionAfterBackgroundColor =
            manipulateColor(preferences.inputCompositionBackgroundColor, 1.2f)
        sumireEnglishQwertyPreference = preferences.sumireEnglishQwertyPreference
        conversionCandidatesRomajiEnablePreference =
            preferences.conversionCandidatesRomajiEnablePreference
        enableZenzRightContextPreference = preferences.enableZenzRightContextPreference
        learnFirstCandidateDictionaryPreference =
            preferences.learnFirstCandidateDictionaryPreference
        enablePredictionSearchLearnDictionaryPreference =
            preferences.enablePredictionSearchLearnDictionaryPreference
        learnPredictionPreference = preferences.learnPredictionPreference
        circularFlickWindowScale = preferences.circularFlickWindowScale
        circularFlickDirectionCount = preferences.circularFlickDirectionCount
        customKeyBorderWidth = preferences.customKeyBorderWidth
        keyBorderEnable = preferences.keyBorderEnable
        keyCornerRadiusDp = preferences.keyCornerRadiusDp
        keyPopupStyle = preferences.keyPopupStyle
        qwertySwitchNumberKeyWithoutNumberPreference =
            preferences.qwertySwitchNumberKeyWithoutNumberPreference
        customRomajiZenkakuConversionEnablePreference =
            preferences.customRomajiZenkakuConversionEnablePreference
        omissionSearchOffsetScorePreference = preferences.omissionSearchOffsetScorePreference
        enableTypoCorrectionJapaneseFlickKeyboardOffsetScorePreference =
            preferences.enableTypoCorrectionJapaneseFlickKeyboardOffsetScorePreference
        enableTypoCorrectionJapaneseFlickKeyboardPreference =
            preferences.enableTypoCorrectionJapaneseFlickKeyboardPreference
        enableTypoCorrectionQwertyEnglishKeyboardPreference =
            preferences.enableTypoCorrectionQwertyEnglishKeyboardPreference

        enableGemmaTranslationPreference = preferences.enableGemmaTranslationPreference
        updateQwertyGlideInputModeOnActiveSurface()
        refreshReconversionUi()

        customIconEnterPath = preferences.customIconEnterPath
        customIconSpacePath = preferences.customIconSpacePath
        customIconArrowLeftPath = preferences.customIconArrowLeftPath
        customIconArrowRightPath = preferences.customIconArrowRightPath
        customFontPath = ""

        val customTypeface = if (customFontPath.isNotEmpty() && java.io.File(customFontPath).exists()) {
            runCatching { android.graphics.Typeface.createFromFile(customFontPath) }.getOrNull()
        } else {
            null
        }

        val customKeyTypeface = if (preferences.customFontKeyPath.isNotEmpty() && java.io.File(preferences.customFontKeyPath).exists()) {
            runCatching { android.graphics.Typeface.createFromFile(preferences.customFontKeyPath) }.getOrNull()
        } else {
            customTypeface
        }

        val customCandidateTypeface = if (preferences.customFontCandidatePath.isNotEmpty() && java.io.File(preferences.customFontCandidatePath).exists()) {
            runCatching { android.graphics.Typeface.createFromFile(preferences.customFontCandidatePath) }.getOrNull()
        } else {
            customTypeface
        }
        currentKeyTypeface = customKeyTypeface
        currentCandidateTypeface = customCandidateTypeface

        refreshCustomIcons()

        applyCurrentTypefacesToViews()
    }

    private fun getCustomEnterTextAndIcon(attribute: EditorInfo?): Pair<String, String> {
        val prefs = cachedPreferences ?: return Pair("", "")
        val defaultText = prefs.customTextEnter
        val defaultPath = prefs.customIconEnterPath
        if (attribute == null) {
            return Pair(defaultText, defaultPath)
        }
        val action = attribute.imeOptions and EditorInfo.IME_MASK_ACTION
        val (text, path) = when (action) {
            EditorInfo.IME_ACTION_GO -> Pair(prefs.customTextEnterGo, prefs.customIconEnterGoPath)
            EditorInfo.IME_ACTION_SEARCH -> Pair(prefs.customTextEnterSearch, prefs.customIconEnterSearchPath)
            EditorInfo.IME_ACTION_SEND -> Pair(prefs.customTextEnterSend, prefs.customIconEnterSendPath)
            EditorInfo.IME_ACTION_NEXT -> Pair(prefs.customTextEnterNext, prefs.customIconEnterNextPath)
            EditorInfo.IME_ACTION_DONE -> Pair(prefs.customTextEnterDone, prefs.customIconEnterDonePath)
            EditorInfo.IME_ACTION_PREVIOUS -> Pair(prefs.customTextEnterPrevious, prefs.customIconEnterPreviousPath)
            EditorInfo.IME_ACTION_NONE -> Pair(prefs.customTextEnterAccess, prefs.customIconEnterAccessPath)
            else -> Pair(defaultText, defaultPath)
        }
        if (text.isEmpty() && path.isEmpty()) {
            return Pair(defaultText, defaultPath)
        }
        return Pair(text, path)
    }

    private fun refreshCustomIcons(attribute: EditorInfo? = currentInputEditorInfo) {
        val prefs = cachedPreferences ?: return
        val (resolvedEnterText, resolvedEnterPath) = getCustomEnterTextAndIcon(attribute)

        mainLayoutBinding?.keyboardView?.setCustomIcons(
            enterPath = resolvedEnterPath,
            spacePath = prefs.customIconSpacePath,
            leftArrowPath = prefs.customIconArrowLeftPath,
            rightArrowPath = prefs.customIconArrowRightPath,
            modeSwitchPath = prefs.customIconModeSwitchPath,
            undoPath = prefs.customIconUndoPath,
            emojiPath = prefs.customIconEmojiPath,
            deletePath = prefs.customIconDeletePath,
            customTextModeSwitch = prefs.customTextModeSwitch,
            customTextUndo = prefs.customTextUndo,
            customTextEmoji = prefs.customTextEmoji,
            customTextDelete = prefs.customTextDelete,
            customTextEnter = resolvedEnterText,
            customTextSpace = prefs.customTextSpace,
            customTextSymbol = prefs.customTextSymbol,
            customText123 = prefs.customText123,
            convertPath = prefs.customIconConvertPath,
            customTextConvert = prefs.customTextConvert
        )
        mainLayoutBinding?.qwertyView?.setCustomIcons(
            enterPath = resolvedEnterPath,
            spacePath = prefs.customIconSpacePath,
            leftArrowPath = prefs.customIconArrowLeftPath,
            rightArrowPath = prefs.customIconArrowRightPath,
            modeSwitchPath = prefs.customIconModeSwitchPath,
            undoPath = prefs.customIconUndoPath,
            emojiPath = prefs.customIconEmojiPath,
            deletePath = prefs.customIconDeletePath,
            customTextModeSwitch = prefs.customTextModeSwitch,
            customTextUndo = prefs.customTextUndo,
            customTextEmoji = prefs.customTextEmoji,
            customTextDelete = prefs.customTextDelete,
            customTextEnter = resolvedEnterText,
            customTextSpace = prefs.customTextSpace,
            customTextSymbol = prefs.customTextSymbol,
            customText123 = prefs.customText123,
            shiftOffPath = prefs.customIconShiftOffPath,
            shiftOnPath = prefs.customIconShiftOnPath,
            shiftLockPath = prefs.customIconShiftLockPath,
            convertPath = prefs.customIconConvertPath,
            customTextConvert = prefs.customTextConvert
        )
        floatingKeyboardBinding?.qwertyViewFloating?.setCustomIcons(
            enterPath = resolvedEnterPath,
            spacePath = prefs.customIconSpacePath,
            leftArrowPath = prefs.customIconArrowLeftPath,
            rightArrowPath = prefs.customIconArrowRightPath,
            modeSwitchPath = prefs.customIconModeSwitchPath,
            undoPath = prefs.customIconUndoPath,
            emojiPath = prefs.customIconEmojiPath,
            deletePath = prefs.customIconDeletePath,
            customTextModeSwitch = prefs.customTextModeSwitch,
            customTextUndo = prefs.customTextUndo,
            customTextEmoji = prefs.customTextEmoji,
            customTextDelete = prefs.customTextDelete,
            customTextEnter = resolvedEnterText,
            customTextSpace = prefs.customTextSpace,
            customTextSymbol = prefs.customTextSymbol,
            customText123 = prefs.customText123,
            shiftOffPath = prefs.customIconShiftOffPath,
            shiftOnPath = prefs.customIconShiftOnPath,
            shiftLockPath = prefs.customIconShiftLockPath,
            convertPath = prefs.customIconConvertPath,
            customTextConvert = prefs.customTextConvert
        )
        floatingKeyboardBinding?.keyboardViewFloating?.setCustomIcons(
            enterPath = resolvedEnterPath,
            spacePath = prefs.customIconSpacePath,
            leftArrowPath = prefs.customIconArrowLeftPath,
            rightArrowPath = prefs.customIconArrowRightPath,
            modeSwitchPath = prefs.customIconModeSwitchPath,
            undoPath = prefs.customIconUndoPath,
            emojiPath = prefs.customIconEmojiPath,
            deletePath = prefs.customIconDeletePath,
            customTextModeSwitch = prefs.customTextModeSwitch,
            customTextUndo = prefs.customTextUndo,
            customTextEmoji = prefs.customTextEmoji,
            customTextDelete = prefs.customTextDelete,
            customTextEnter = resolvedEnterText,
            customTextSpace = prefs.customTextSpace,
            customTextSymbol = prefs.customTextSymbol,
            customText123 = prefs.customText123,
            convertPath = prefs.customIconConvertPath,
            customTextConvert = prefs.customTextConvert
        )
    }

    private fun applyCurrentTypefacesToViews() {
        mainLayoutBinding?.keyboardView?.setCustomTypeface(currentKeyTypeface)
        mainLayoutBinding?.qwertyView?.setCustomTypeface(currentKeyTypeface)
        mainLayoutBinding?.customLayoutDefault?.setCustomTypeface(currentKeyTypeface)
        mainLayoutBinding?.keyboardSymbolView?.setCustomTypeface(currentCandidateTypeface)
        floatingKeyboardBinding?.keyboardViewFloating?.setCustomTypeface(currentKeyTypeface)
        floatingKeyboardBinding?.qwertyViewFloating?.setCustomTypeface(currentKeyTypeface)
        floatingKeyboardBinding?.customLayoutFloating?.setCustomTypeface(currentKeyTypeface)
        suggestionAdapter?.setCustomTypeface(currentCandidateTypeface)
        suggestionAdapterFull?.setCustomTypeface(currentCandidateTypeface)
    }

    private fun initializeMozcDictionaries(@Suppress("UNUSED_PARAMETER") preferences: ImePreferencesSnapshot) {
        applyDictionaryOverrideRevisionIfNeeded()
        if (!kanaKanjiEngine.isSystemUserDictionaryInitialized()) {
            runCatching {
                kanaKanjiEngine.loadSystemUserDictionaryFromFiles(applicationContext)
            }
        }
    }

    private fun applyDictionaryOverrideRevisionIfNeeded() {
        val currentRevision = dictionaryOverrideStore.currentRevision
        if (currentRevision == lastAppliedDictionaryOverrideRevision) return
        if (dictionaryOverrideApplyJob?.isActive == true) return

        dictionaryOverrideApplyJob = ioScope.launch {
            val revisionToApply = dictionaryOverrideStore.currentRevision
            val success = runCatching {
                kanaKanjiEngine.applyDictionaryOverrideState(applicationContext)
                englishEngine.reloadDictionariesFromCurrentSources(
                    reader = dictionaryBinaryReader,
                    qwertyGlideInputEnabled = qwertyGlideInputPreference,
                    qwertyGlidePrebuiltDictionaryLoader = QwertyGlidePrebuiltDictionaryLoader(
                        dictionarySourceResolver
                    ),
                    canUseBundledPrebuiltIndex = !dictionarySourceResolver.shouldUseOverrideCategory(
                        DictionaryCategory.ENGLISH
                    ),
                )
            }.onFailure {
                Timber.w(it, "Failed to apply dictionary override revision $revisionToApply")
            }.isSuccess

            if (success) {
                lastAppliedDictionaryOverrideRevision = revisionToApply
            }
        }
    }

    private fun loadKeyboardBackgroundBitmap(): Bitmap? {
        val uriString = appPreference.keyboard_background_image_uri
        if (uriString.isBlank()) return null
        val uri = runCatching { uriString.toUri() }.getOrNull() ?: return null
        return runCatching {
            contentResolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input)
            }
        }.onFailure {
            Timber.w(it, "Failed to load keyboard background image: $uriString")
        }.getOrNull()
    }

    private fun clearKeyboardBackgroundImage(imageView: ImageView) {
        imageView.setImageDrawable(null)
        imageView.background = null
        imageView.isVisible = false
    }

    private fun applyKeyboardBackgroundImageToViewIfNeeded(imageView: ImageView): Boolean {
        val bitmap = loadKeyboardBackgroundBitmap()
        if (bitmap == null) {
            clearKeyboardBackgroundImage(imageView)
            return false
        }

        val displayMode = appPreference.keyboard_background_image_display_mode
        when (displayMode) {
            "center_crop" -> {
                imageView.background = null
                imageView.scaleType = ImageView.ScaleType.CENTER_CROP
                imageView.setImageBitmap(bitmap)
            }


            else -> {
                imageView.background = null
                imageView.scaleType = ImageView.ScaleType.FIT_CENTER
                imageView.setImageBitmap(bitmap)
            }
        }
        imageView.isVisible = true
        return true
    }

    private fun applyKeyboardBackgroundImageIfNeeded(mainView: MainLayoutBinding): Boolean {
        return applyKeyboardBackgroundImageToViewIfNeeded(mainView.keyboardBackgroundImage)
    }

    private fun applyFloatingKeyboardBackgroundImageIfNeeded(
        floatingView: FloatingKeyboardLayoutBinding
    ): Boolean {
        return applyKeyboardBackgroundImageToViewIfNeeded(floatingView.floatingKeyboardBackgroundImage)
    }

    private fun resolveVideoQualityMaxSize(quality: String): Pair<Int, Int> {
        return when (quality) {
            "low" -> 640 to 360
            "medium" -> 1280 to 720
            else -> Int.MAX_VALUE to Int.MAX_VALUE
        }
    }

    private fun releaseKeyboardBackgroundVideoPlayer() {
        mainLayoutBinding?.keyboardBackgroundVideo?.player = null
        mainLayoutBinding?.keyboardBackgroundVideo?.isVisible = false
        keyboardBackgroundPlayer?.release()
        keyboardBackgroundPlayer = null
    }

    @androidx.annotation.OptIn(UnstableApi::class)
    private fun applyKeyboardBackgroundVideoToViewIfNeeded(
        playerView: PlayerView,
        releasePlayer: () -> Unit,
        onPlayerCreated: (ExoPlayer) -> Unit,
        surfaceName: String
    ): Boolean {
        playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
        val uriString = appPreference.keyboard_background_video_uri
        if (uriString.isBlank()) {
            releasePlayer()
            playerView.isVisible = false
            return false
        }

        val uri = runCatching { uriString.toUri() }.getOrNull()
        if (uri == null) {
            releasePlayer()
            playerView.isVisible = false
            return false
        }

        val (maxWidth, maxHeight) = resolveVideoQualityMaxSize(appPreference.keyboard_background_video_quality)
        return runCatching {
            releasePlayer()
            val player = ExoPlayer.Builder(this).build().apply {
                repeatMode = Player.REPEAT_MODE_ALL
                volume = 0f
                playWhenReady = true
                videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
                trackSelectionParameters = trackSelectionParameters
                    .buildUpon()
                    .setMaxVideoSize(maxWidth, maxHeight)
                    .build()
                setMediaItem(MediaItem.fromUri(uri))
                prepare()
            }
            playerView.player = player
            playerView.isVisible = true
            onPlayerCreated(player)
            true
        }.onFailure {
            Timber.w(it, "Failed to play $surfaceName keyboard background video: $uriString")
            releasePlayer()
            playerView.isVisible = false
        }.getOrDefault(false)
    }

    @androidx.annotation.OptIn(UnstableApi::class)
    private fun applyKeyboardBackgroundVideoIfNeeded(mainView: MainLayoutBinding): Boolean {
        return applyKeyboardBackgroundVideoToViewIfNeeded(
            playerView = mainView.keyboardBackgroundVideo,
            releasePlayer = ::releaseKeyboardBackgroundVideoPlayer,
            onPlayerCreated = { keyboardBackgroundPlayer = it },
            surfaceName = "main"
        )
    }

    private fun releaseFloatingKeyboardBackgroundVideoPlayer() {
        floatingKeyboardBinding?.floatingKeyboardBackgroundVideo?.player = null
        floatingKeyboardBinding?.floatingKeyboardBackgroundVideo?.isVisible = false
        floatingKeyboardBackgroundPlayer?.release()
        floatingKeyboardBackgroundPlayer = null
        floatingKeyboardBackgroundVideoConfig = null
    }

    @androidx.annotation.OptIn(UnstableApi::class)
    private fun applyFloatingKeyboardBackgroundVideoIfNeeded(
        floatingView: FloatingKeyboardLayoutBinding
    ): Boolean {
        val playerView = floatingView.floatingKeyboardBackgroundVideo
        playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
        playerView.setKeepContentOnPlayerReset(true)

        val uriString = appPreference.keyboard_background_video_uri
        if (uriString.isBlank()) {
            releaseFloatingKeyboardBackgroundVideoPlayer()
            playerView.isVisible = false
            return false
        }

        val uri = runCatching { uriString.toUri() }.getOrNull()
        if (uri == null) {
            releaseFloatingKeyboardBackgroundVideoPlayer()
            playerView.isVisible = false
            return false
        }

        val quality = appPreference.keyboard_background_video_quality
        val videoConfig = KeyboardBackgroundVideoConfig(
            uriString = uriString,
            quality = quality
        )
        floatingKeyboardBackgroundPlayer?.takeIf {
            floatingKeyboardBackgroundVideoConfig == videoConfig
        }?.let { existingPlayer ->
            playerView.player = existingPlayer
            playerView.isVisible = true
            return true
        }

        val (maxWidth, maxHeight) = resolveVideoQualityMaxSize(quality)
        return runCatching {
            releaseFloatingKeyboardBackgroundVideoPlayer()
            val player = ExoPlayer.Builder(this).build().apply {
                repeatMode = Player.REPEAT_MODE_ALL
                volume = 0f
                playWhenReady = true
                videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
                trackSelectionParameters = trackSelectionParameters
                    .buildUpon()
                    .setMaxVideoSize(maxWidth, maxHeight)
                    .build()
                setMediaItem(MediaItem.fromUri(uri))
                prepare()
            }
            playerView.player = player
            playerView.isVisible = true
            floatingKeyboardBackgroundPlayer = player
            floatingKeyboardBackgroundVideoConfig = videoConfig
            true
        }.onFailure {
            Timber.w(it, "Failed to play floating keyboard background video: $uriString")
            releaseFloatingKeyboardBackgroundVideoPlayer()
            playerView.isVisible = false
        }.getOrDefault(false)
    }

    private fun clearNormalKeyboardBackgroundForFloatingMode(mainView: MainLayoutBinding) {
        releaseKeyboardBackgroundVideoPlayer()
        clearKeyboardBackgroundImage(mainView.keyboardBackgroundImage)
    }

    private fun applyKeyboardBackgroundIfNeeded(
        mainView: MainLayoutBinding,
        skipForFloatingMode: Boolean = true
    ) {
        if (skipForFloatingMode && isKeyboardFloatingMode == true) {
            clearNormalKeyboardBackgroundForFloatingMode(mainView)
            return
        }
        val isBackgroundVideoApplied = applyKeyboardBackgroundVideoIfNeeded(mainView)
        if (isBackgroundVideoApplied) {
            applyKeyboardContainerTransparencyForVideo(mainView, enabled = true)
            clearKeyboardBackgroundImage(mainView.keyboardBackgroundImage)
        } else {
            applyKeyboardBackgroundImageIfNeeded(mainView)
        }
    }

    private fun applyFloatingKeyboardBackgroundIfNeeded(
        floatingView: FloatingKeyboardLayoutBinding
    ) {
        applyFloatingKeyboardRoundedClipping(floatingView)
        updateFloatingKeyboardBackgroundBounds(floatingView)
        val isBackgroundVideoApplied = applyFloatingKeyboardBackgroundVideoIfNeeded(floatingView)
        if (isBackgroundVideoApplied) {
            clearKeyboardBackgroundImage(floatingView.floatingKeyboardBackgroundImage)
            applyFloatingKeyboardContainerTransparencyForBackgroundMedia(
                floatingView,
                enabled = true
            )
        } else {
            val isBackgroundImageApplied =
                applyFloatingKeyboardBackgroundImageIfNeeded(floatingView)
            applyFloatingKeyboardContainerTransparencyForBackgroundMedia(
                floatingView,
                enabled = isBackgroundImageApplied
            )
        }
    }

    private fun applyFloatingKeyboardRoundedClipping(floatingView: FloatingKeyboardLayoutBinding) {
        val clipDrawable = createKeyboardBackgroundDrawable(
            color = Color.TRANSPARENT,
            radiusDp = 16,
            topLeft = true,
            topRight = true,
            bottomRight = true,
            bottomLeft = true
        )
        floatingView.root.clipToOutline = false
        floatingView.floatingKeyboardBackgroundContainer.background = clipDrawable
        floatingView.floatingKeyboardBackgroundContainer.outlineProvider =
            ViewOutlineProvider.BACKGROUND
        floatingView.floatingKeyboardBackgroundContainer.clipToOutline = true
    }

    private fun bindSuggestionAdaptersForFloatingMode(
        mainView: MainLayoutBinding,
        isFloatingMode: Boolean
    ) {
        val floatingView = floatingKeyboardBinding
        if (isFloatingMode) {
            floatingView?.suggestionRecyclerView?.adapter = suggestionAdapter
            floatingView?.candidatesRowView?.adapter = suggestionAdapterFull
            mainView.suggestionRecyclerView.adapter = null
            mainView.candidatesRowView.adapter = null
        } else {
            mainView.suggestionRecyclerView.adapter = suggestionAdapter
            mainView.candidatesRowView.adapter = suggestionAdapterFull
            floatingView?.suggestionRecyclerView?.adapter = null
            floatingView?.candidatesRowView?.adapter = null
        }
    }

    private fun updateFloatingKeyboardBackgroundBounds(
        floatingView: FloatingKeyboardLayoutBinding,
        fallbackKeyboardHeightPx: Int? = null
    ) {
        fun applyHeight(height: Int) {
            if (height <= 0) return
            val params = floatingView.floatingKeyboardBackgroundContainer.layoutParams
            if (params.height != height) {
                params.height = height
                floatingView.floatingKeyboardBackgroundContainer.layoutParams = params
            }
        }

        val fallbackHeight = fallbackKeyboardHeightPx?.let { keyboardHeight ->
            val chromeHeightPx = (106 * resources.displayMetrics.density).toInt()
            keyboardHeight + chromeHeightPx
        }
        applyHeight(floatingView.floatingKeyboardContent.height.takeIf { it > 0 } ?: fallbackHeight
        ?: 0)
        floatingView.root.post {
            applyHeight(floatingView.floatingKeyboardContent.height)
        }
    }

    private fun updateFloatingFullCandidatesHeight(
        floatingView: FloatingKeyboardLayoutBinding,
        heightPx: Int
    ) {
        (floatingView.candidatesRowView.layoutParams as? ConstraintLayout.LayoutParams)
            ?.let { params ->
                if (params.height != heightPx) {
                    params.height = heightPx
                    floatingView.candidatesRowView.layoutParams = params
                }
            }
    }

    private fun applyKeyboardContainerTransparencyForVideo(
        mainView: MainLayoutBinding,
        enabled: Boolean
    ) {
        if (!enabled) return
        // Keep the original rounded drawable and just make it transparent.
        mainView.root.setDrawableAlpha(0)
        mainView.suggestionViewParent.setDrawableAlpha(0)
        mainView.candidateTabLayout.setDrawableAlpha(0)
        mainView.shortcutToolbarRecyclerview.setBackgroundColor(Color.TRANSPARENT)
    }

    private fun applyFloatingKeyboardContainerTransparencyForBackgroundMedia(
        floatingView: FloatingKeyboardLayoutBinding,
        enabled: Boolean
    ) {
        if (enabled) {
            floatingView.suggestionViewParent.background = null
            floatingView.candidatesRowView.setBackgroundColor(Color.TRANSPARENT)
        } else {
            applyFloatingKeyboardContainerBackgrounds(floatingView)
        }
    }

    private fun createKeyboardBackgroundDrawable(
        @ColorInt color: Int,
        radiusDp: Int,
        topLeft: Boolean,
        topRight: Boolean,
        bottomRight: Boolean,
        bottomLeft: Boolean
    ): GradientDrawable {
        val radiusPx = radiusDp * resources.displayMetrics.density
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(color)
            cornerRadii = floatArrayOf(
                if (topLeft) radiusPx else 0f,
                if (topLeft) radiusPx else 0f,
                if (topRight) radiusPx else 0f,
                if (topRight) radiusPx else 0f,
                if (bottomRight) radiusPx else 0f,
                if (bottomRight) radiusPx else 0f,
                if (bottomLeft) radiusPx else 0f,
                if (bottomLeft) radiusPx else 0f
            )
        }
    }

    private fun applyKeyboardContainerBackgrounds(mainView: MainLayoutBinding) {
        val isDynamic = DynamicColors.isDynamicColorAvailable()
        if (isKeyboardRounded == true) {
            val fallbackColor = getColor(com.kazumaproject.core.R.color.keyboard_bg)
            val defaultColor = if (isDynamic) {
                mainView.root.context.getThemeColorOrFallback(
                    attrRes = MaterialR.attr.colorSurfaceContainer,
                    fallbackColor = fallbackColor
                )
            } else {
                fallbackColor
            }
            val customColor = customThemeBgColor ?: Color.WHITE
            val backgroundColor = when (keyboardThemeMode) {
                "custom" -> customColor
                else -> defaultColor
            }
            mainView.root.background = createKeyboardBackgroundDrawable(
                color = backgroundColor,
                radiusDp = keyboardCornerRadiusDp,
                topLeft = keyboardCornerTopLeft,
                topRight = keyboardCornerTopRight,
                bottomRight = keyboardCornerBottomRight,
                bottomLeft = keyboardCornerBottomLeft
            )
            mainView.suggestionViewParent.background = createKeyboardBackgroundDrawable(
                color = backgroundColor,
                radiusDp = keyboardCornerRadiusDp,
                topLeft = keyboardCornerTopLeft,
                topRight = keyboardCornerTopRight,
                bottomRight = keyboardCornerBottomRight,
                bottomLeft = keyboardCornerBottomLeft
            )
            mainView.candidateTabLayout.background = createKeyboardBackgroundDrawable(
                color = backgroundColor,
                radiusDp = keyboardCornerRadiusDp,
                topLeft = keyboardCornerTopLeft,
                topRight = keyboardCornerTopRight,
                bottomRight = keyboardCornerBottomRight,
                bottomLeft = keyboardCornerBottomLeft
            )
            return
        }

        when (keyboardThemeMode) {
            "default" -> {
                if (isDynamic) {
                    mainView.root.setBackgroundResource(com.kazumaproject.core.R.drawable.square_corners_bg_material_root)
                    mainView.suggestionViewParent.setBackgroundResource(com.kazumaproject.core.R.drawable.square_corners_bg_material_root)
                    mainView.candidateTabLayout.setBackgroundResource(com.kazumaproject.core.R.drawable.square_corners_bg_material_root)
                } else {
                    mainView.root.setBackgroundResource(com.kazumaproject.core.R.drawable.square_corners_bg_root)
                    mainView.suggestionViewParent.setBackgroundResource(com.kazumaproject.core.R.drawable.square_corners_bg_root)
                    mainView.candidateTabLayout.setBackgroundResource(com.kazumaproject.core.R.drawable.square_corners_bg_root)
                }
            }

            "custom" -> {
                mainView.root.setBackgroundResource(com.kazumaproject.core.R.drawable.square_corners_bg_root)
                mainView.suggestionViewParent.setBackgroundResource(com.kazumaproject.core.R.drawable.square_corners_bg_root)
                mainView.candidateTabLayout.setBackgroundResource(com.kazumaproject.core.R.drawable.square_corners_bg_root)

                mainView.root.setDrawableSolidColor(customThemeBgColor ?: Color.WHITE)
                mainView.suggestionViewParent.setDrawableSolidColor(
                    customThemeBgColor ?: Color.WHITE
                )
                mainView.candidateTabLayout.setDrawableSolidColor(
                    customThemeBgColor ?: Color.WHITE
                )
            }

            else -> {
                if (isDynamic) {
                    mainView.root.setBackgroundResource(com.kazumaproject.core.R.drawable.square_corners_bg_material_root)
                    mainView.suggestionViewParent.setBackgroundResource(com.kazumaproject.core.R.drawable.square_corners_bg_material_root)
                    mainView.candidateTabLayout.setBackgroundResource(com.kazumaproject.core.R.drawable.square_corners_bg_material_root)
                } else {
                    mainView.root.setBackgroundResource(com.kazumaproject.core.R.drawable.square_corners_bg_root)
                    mainView.suggestionViewParent.setBackgroundResource(com.kazumaproject.core.R.drawable.square_corners_bg_root)
                    mainView.candidateTabLayout.setBackgroundResource(com.kazumaproject.core.R.drawable.square_corners_bg_root)
                }
            }
        }
        applyThemeToFloatingDockView()
        applyThemeToFloatingCandidateListAdapter()
        applyThemeToSymbolKeyboard()
    }

    private fun resolveSymbolKeyboardKeyColor(@ColorInt panelColor: Int, @ColorInt keyColor: Int): Int {
        val dr = Color.red(panelColor) - Color.red(keyColor)
        val dg = Color.green(panelColor) - Color.green(keyColor)
        val db = Color.blue(panelColor) - Color.blue(keyColor)
        val distance = kotlin.math.sqrt(
            (dr * dr + dg * dg + db * db).toFloat(),
        )
        if (distance >= 40f) {
            return keyColor
        }
        return if (ColorUtils.calculateLuminance(panelColor) > 0.5) {
            manipulateColor(panelColor, 0.82f)
        } else {
            manipulateColor(panelColor, 1.18f)
        }
    }

    private fun applyFloatingKeyboardContainerBackgrounds(
        floatingView: FloatingKeyboardLayoutBinding
    ) {
        val isDynamic = DynamicColors.isDynamicColorAvailable()
        when (keyboardThemeMode) {
            "default" -> {
                if (isDynamic) {
                    floatingView.root.setBackgroundResource(com.kazumaproject.core.R.drawable.keyboard_root_material_floating)
                    floatingView.suggestionViewParent.setBackgroundResource(com.kazumaproject.core.R.drawable.keyboard_root_material_floating)
                    floatingView.suggestionVisibility.setBackgroundResource(com.kazumaproject.core.R.drawable.recyclerview_size_button_bg_material)
                } else {
                    floatingView.suggestionViewParent.background = null
                }
            }

            "custom" -> {
                floatingView.root.setBackgroundResource(com.kazumaproject.core.R.drawable.keyboard_root_material_floating)
                floatingView.suggestionViewParent.setBackgroundResource(com.kazumaproject.core.R.drawable.keyboard_root_material_floating)
                floatingView.suggestionVisibility.setBackgroundResource(com.kazumaproject.core.R.drawable.recyclerview_size_button_bg_material)

                floatingView.root.setDrawableSolidColor(customThemeBgColor ?: Color.WHITE)
                floatingView.suggestionViewParent.setDrawableSolidColor(
                    customThemeBgColor ?: Color.WHITE
                )
                floatingView.suggestionVisibility.setDrawableSolidColor(
                    customThemeSpecialKeyColor ?: Color.GRAY
                )
            }

            else -> {
                if (isDynamic) {
                    floatingView.root.setBackgroundResource(com.kazumaproject.core.R.drawable.keyboard_root_material_floating)
                    floatingView.suggestionViewParent.setBackgroundResource(com.kazumaproject.core.R.drawable.keyboard_root_material_floating)
                    floatingView.suggestionVisibility.setBackgroundResource(com.kazumaproject.core.R.drawable.recyclerview_size_button_bg_material)
                } else {
                    floatingView.suggestionViewParent.background = null
                }
            }
        }
    }

    override fun onStartInputView(editorInfo: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(editorInfo, restarting)
        isSystemUiRemoteInputSession = isSystemUiRemoteInput(editorInfo)
        isInputViewActive = true
        keyboardSelectionPopupWindow?.dismiss()
        addUserDictionaryPopup?.dismiss()
        _keyboardSymbolViewState.update { SymbolKeyboardState() }
        _selectMode.update { false }
        cachedPrimaryClipContent = null
        _cursorMoveMode.update { false }
        hardwareKeyboardCoordinator.refreshPresence(physicalKeyboardPresenceListener)
        val hasPhysicalKeyboard = hasHardwareKeyboardConnected == true
        zenzEngine?.setRuntimeConfig(
            nCtx = zenzMaximumContextSizePreference ?: 512,
            nThreads = zenzMaximumThreadSizePreference ?: 4
        )
        suggestionAdapter?.suggestions = emptyList()
        suggestionAdapter?.setInlineSuggestions(emptyList())
        suggestionAdapterFull?.setInlineSuggestions(emptyList())
        suggestionAdapter?.setCandidateTextSize(appPreference.candidate_letter_size ?: 14.0f)
        suggestionAdapterFull?.setCandidateTextSize(appPreference.candidate_letter_size ?: 14.0f)
        suggestionClickNum = 0
        setCurrentInputType(editorInfo)
        suggestionAdapter?.setClipboardDescriptionTextVisibility(
            !(clipboardPreviewTapToDelete ?: false)
        )
        val isNumberInputType = currentInputType in numberTypes
        if (!isNumberInputType && qwertyMode.value == TenKeyQWERTYMode.Sumire) {
            mainLayoutBinding?.let { mainView ->
                Timber.d("TenKeyQWERTYMode.Sumire: ${currentInputModeForSession} ${switchQWERTYPassword}")
                when (currentInputModeForSession) {
                    InputMode.ModeJapanese -> {
                        customKeyboardMode = KeyboardInputMode.HIRAGANA
                        updateKeyboardLayout()
                    }

                    InputMode.ModeEnglish -> {
                        if (switchQWERTYPassword == true) {
                            if (currentInputType in passwordTypesWithOutNumber) {
                                updateQwertyOnActiveSurface { resetQWERTYKeyboard() }
                                _tenKeyQWERTYMode.update { TenKeyQWERTYMode.TenKeyQWERTY }
                            } else {
                                if (currentInputType !in numberTypes) {
                                    customKeyboardMode = KeyboardInputMode.ENGLISH
                                    createNewKeyboardLayoutForSumire()
                                }
                            }
                        } else {
                            customKeyboardMode = KeyboardInputMode.ENGLISH
                            createNewKeyboardLayoutForSumire()
                        }
                    }

                    InputMode.ModeNumber -> {
                        customKeyboardMode = KeyboardInputMode.SYMBOLS
                        createNewKeyboardLayoutForSumire()
                    }
                }
            }
        }

        updateClipboardPreview()

        applyPrivacyStateForEditor(editorInfo)

        suppressSuggestions = if (showCandidateInPasswordPreference == true) {
            currentInputType.isPassword()
        } else {
            false
        }

        if (currentInputType in passwordTypesWithOutNumber) {
            if (switchQWERTYPassword == true) {
                Timber.d("current input type in OnStartView passwordTypesWithOutNumber: [$currentInputType] [$restarting] [${currentInputModeForSession}] [${qwertyMode.value}]")
                setCurrentInputModeForSession(InputMode.ModeEnglish)
                setCurrentQwertyRomajiModeForSession(false)
                setQwertySwitchNumberLayoutKeyVisibilityOnActiveSurface(true)
                _tenKeyQWERTYMode.update { TenKeyQWERTYMode.TenKeyQWERTY }
                updateQwertyOnActiveSurface {
                    resetQWERTYKeyboard(currentInputType.getQWERTYReturnTextInEn())
                }
                renderCurrentKeyboardStateOnActiveSurface()
            } else {
                Timber.d("current input type in OnStartView passwordTypesWithOutNumber else: [$currentInputType] [$restarting]")
                if (isTabletGojuonSurface()) {
                    mainLayoutBinding?.tabletView?.currentInputMode?.set(InputMode.ModeEnglish)
                } else {
                    setCurrentInputModeForSession(InputMode.ModeEnglish)
                }
            }
        } else if (isNumberInputType) {
            Timber.d("current input type in OnStartView number: [$currentInputType] [$restarting]")
            showNumberKeyboardForCurrentInputType()
        } else {
            Timber.d("current input type in OnStartView not password: [$currentInputType] [$restarting]")
            resetKeyboard()
        }

        if (isKeyboardFloatingMode == true) {
            val isPortrait =
                resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
            val screenWidth = resources.displayMetrics.widthPixels

            val widthPref = if (isPortrait) {
                tenkeyWidthPreferenceValue ?: 100
            } else {
                tenkeyWidthLandScapePreferenceValue ?: 100
            }

            val widthPx = when {
                widthPref == 100 -> {
                    ViewGroup.LayoutParams.MATCH_PARENT
                }

                else -> {
                    (screenWidth * (widthPref / 100f)).toInt()
                }
            }
            changeFloatingKeyboardSize(
                newWidthDp = widthPx
            )

            floatingKeyboardBinding?.let { floatingKeyboardLayoutBinding ->
                val isNight = currentNightMode == Configuration.UI_MODE_NIGHT_YES
                val isDark = keyboardThemeMode == "dark" || (keyboardThemeMode == "default" && isNight)
                val isCustom = keyboardThemeMode == "custom"

                val resolvedBgColor = customThemeBgColor ?: Color.WHITE
                val resolvedKeyColor = customThemeKeyColor ?: Color.WHITE
                val resolvedSpecialKeyColor = customThemeSpecialKeyColor ?: Color.GRAY
                val resolvedEnterKeyColor = customThemeEnterKeyColor ?: Color.BLUE
                val resolvedPopupBgColor = customThemePopupBgColor ?: Color.WHITE

                val resolvedKeyTextColor = if (isCustom) {
                    customThemeKeyTextColor ?: if (resolvedKeyColor.isLightColor()) Color.BLACK else Color.WHITE
                } else {
                    if (isDark) Color.WHITE else Color.BLACK
                }

                val resolvedSpecialKeyTextColor = if (isCustom) {
                    customThemeSpecialKeyTextColor ?: if (resolvedSpecialKeyColor.isLightColor()) Color.BLACK else Color.WHITE
                } else {
                    if (isDark) Color.WHITE else Color.BLACK
                }

                val resolvedEnterKeyTextColor = if (isCustom) {
                    customThemeEnterKeyTextColor ?: if (resolvedEnterKeyColor.isLightColor()) Color.BLACK else Color.WHITE
                } else {
                    if (isDark) Color.WHITE else Color.BLACK
                }

                val resolvedPopupTextColor = if (isCustom) {
                    customThemePopupTextColor ?: if (resolvedPopupBgColor.isLightColor()) Color.BLACK else Color.WHITE
                } else {
                    if (isDark) Color.WHITE else Color.BLACK
                }

                floatingKeyboardLayoutBinding.keyboardViewFloating.applyKeyboardTheme(
                    themeMode = keyboardThemeMode ?: "default",
                    currentNightMode = currentNightMode,
                    isDynamicColorEnabled = DynamicColors.isDynamicColorAvailable(),
                    customBgColor = resolvedBgColor,
                    customKeyColor = resolvedKeyColor,
                    customSpecialKeyColor = resolvedSpecialKeyColor,
                    customEnterKeyColor = resolvedEnterKeyColor,
                    customKeyTextColor = resolvedKeyTextColor,
                    customSpecialKeyTextColor = resolvedSpecialKeyTextColor,
                    customEnterKeyTextColor = resolvedEnterKeyTextColor,
                    customPopupBgColor = resolvedPopupBgColor,
                    customPopupTextColor = resolvedPopupTextColor,
                    liquidGlassEnable = liquidGlassThemePreference ?: false,
                    customBorderEnable = customKeyBorderEnablePreference ?: false,
                    customBorderColor = customKeyBorderEnableColor ?: Color.BLACK,
                    liquidGlassKeyAlphaEnable = liquidGlassKeyBlurRadiousPreference ?: 255,
                    borderWidth = customKeyBorderWidth ?: 1,
                    keyBorderEnable = keyBorderEnable ?: false,
                    keyCornerRadiusDp = keyCornerRadiusDp ?: 8,
                    keyPopupStyle = keyPopupStyle ?: "default"
                )
                floatingKeyboardLayoutBinding.keyboardViewFloating.setPopupWindowAnchorProvider {
                    floatingKeyboardLayoutBinding.root
                }
                floatingKeyboardLayoutBinding.keyboardViewFloating.setLongPressTimeout(
                    (longPressTimeoutPreferenceValue ?: 300).toLong()
                )
                floatingKeyboardLayoutBinding.keyboardViewFloating.applyPopupViewStyle(
                    currentTenKeyPopupViewStyle()
                )
                floatingKeyboardLayoutBinding.keyboardViewFloating.apply {
                    setOnFlickListener(object : FlickListener {
                        override fun onFlick(gestureType: GestureType, key: Key, char: Char?) {
                            val insertString = inputString.value
                            val sb = StringBuilder()
                            val suggestionList = suggestionAdapter?.suggestions ?: emptyList()
                            when (gestureType) {
                                GestureType.Null -> {

                                }

                                GestureType.Down -> {
                                    handleKeyPressFeedback(getKeySoundType(key))
                                }

                                GestureType.Tap -> {
                                    handleTapAndFlickFloating(
                                        key = key,
                                        char = char,
                                        insertString = insertString,
                                        sb = sb,
                                        isFlick = false,
                                        gestureType = gestureType,
                                        suggestions = suggestionList,
                                        floatingKeyboardLayoutBinding = floatingKeyboardLayoutBinding
                                    )
                                }


                                else -> {
                                    handleTapAndFlickFloating(
                                        key = key,
                                        char = char,
                                        insertString = insertString,
                                        sb = sb,
                                        isFlick = true,
                                        gestureType = gestureType,
                                        suggestions = suggestionList,
                                        floatingKeyboardLayoutBinding = floatingKeyboardLayoutBinding
                                    )
                                }
                            }
                        }
                    })
                    setOnLongPressListener(object : LongPressListener {
                        override fun onLongPress(key: Key) {
                            handleLongPressFloating(key)
                            Timber.d("Long Press: $key")
                        }
                    })
                }
                floatingKeyboardLayoutBinding.suggestionRecyclerView.adapter = suggestionAdapter
                floatingKeyboardLayoutBinding.candidatesRowView.adapter = suggestionAdapterFull
                mainLayoutBinding?.suggestionRecyclerView?.adapter = null
                mainLayoutBinding?.candidatesRowView?.adapter = null
            }
        }
        mainLayoutBinding?.let { mainView ->
            if (!hasPhysicalKeyboard) {
                if (isKeyboardFloatingMode == true) {
                    applyFloatingModeState(true)
                } else {
                    setKeyboardSizeSwitchKeyboard(mainView)
                    applyFloatingModeState(false)
                }
            } else {
                checkForPhysicalKeyboard(true)
            }
            mainView.apply {
                applyKeyboardContainerBackgrounds(mainView)

                if (liquidGlassThemePreference == true) {
                    mainView.root.setDrawableAlpha(liquidGlassBlurRadiousPreference ?: 220)
                    mainView.suggestionViewParent.setDrawableAlpha(0)
                    mainView.candidateTabLayout.setDrawableAlpha(0)
                }

                mainView.root.outlineProvider = ViewOutlineProvider.BACKGROUND
                mainView.root.clipToOutline = isKeyboardRounded == true

                applyKeyboardBackgroundIfNeeded(mainView)
                if (isKeyboardFloatingMode == true) {
                    floatingKeyboardBinding?.let { applyFloatingKeyboardBackgroundIfNeeded(it) }
                }

                updateUpperAreaVisibility(mainView)
                suggestionVisibility.isVisible = false
                keyboardView.setFlickSensitivityValue(flickSensitivityPreferenceValue ?: 100)
                keyboardView.setLongPressTimeout((longPressTimeoutPreferenceValue ?: 300).toLong())
                keyboardView.applyPopupViewStyle(currentTenKeyPopupViewStyle())
                val defaultLetterSize = when (currentInputModeForSession) {
                    InputMode.ModeJapanese -> 17f
                    InputMode.ModeEnglish -> 12f
                    InputMode.ModeNumber -> 16f
                    else -> 17f
                }
                val keyLetterSizeDelta = (appPreference.key_letter_size ?: 0.0f)
                    .coerceIn(12f - defaultLetterSize, 40f - defaultLetterSize)
                keyboardView.setKeyLetterSize((defaultLetterSize + keyLetterSizeDelta).coerceIn(12f, 40f))
                keyboardView.setKeyLetterSizeDelta(keyLetterSizeDelta.toInt())
                keyboardView.setKeySizeScale(
                    appPreference.tenkey_key_width_scale_percent ?: 100,
                    appPreference.tenkey_key_height_scale_percent ?: 100
                )
                keyboardView.setLanguageEnableKeyState(tenkeyShowIMEButtonPreference ?: true)
                if (tenkeyShowIMEButtonPreference == true) {
                    keyboardView.setBackgroundSmallLetterKey(cachedLogoDrawable)
                } else {
                    keyboardView.setBackgroundSmallLetterKey(cachedKanaDrawable)
                }

                keyboardView.setFlickGuideEnabled(tenkeyQKeymapGuide ?: false)

                setTabsToTabLayout(mainView)

                refreshSuggestionProgressVisibility()

                tabletView.setFlickSensitivityValue(flickSensitivityPreferenceValue ?: 100)
                tabletView.setLongPressTimeout((longPressTimeoutPreferenceValue ?: 300).toLong())
                customLayoutDefault.setFlickSensitivityValue(flickSensitivityPreferenceValue ?: 100)
                customLayoutDefault.setLongPressTimeout(
                    (longPressTimeoutPreferenceValue ?: 300).toLong()
                )
                customLayoutDefault.setFlickGuideEnabled(flickKeymapGuidePreference ?: false)
                customLayoutDefault.setFlickGuideTextSizeSp(
                    (flickGuideTextSizeSpPreference ?: 9).coerceIn(6, 16).toFloat()
                )
                customLayoutDefault.setFlickGuideMaxCodePoints(
                    (flickGuideMaxCharactersPreference ?: 1).coerceIn(1, 4)
                )
                qwertyView.setLongPressTimeout((longPressTimeoutPreferenceValue ?: 300).toLong())
                qwertyView.applyPopupViewStyleSet(currentQwertyPopupViewStyleSet())
                qwertyView.setSpecialKeyVisibility(
                    showCursors = qwertyShowCursorButtonsPreference ?: false,
                    showSwitchKey = qwertyShowIMEButtonPreference ?: true,
                    showKutouten = qwertyShowKutoutenButtonsPreference ?: false,
                    showEmojiKey = qwertyShowEmojiButtonPreference ?: false
                )
                qwertyView.setRomajiEnglishSwitchKeyTextWithStyle(true)
                qwertyView.updateSymbolKeymapState(qwertyShowKeymapSymbolsPreference ?: false)
                qwertyView.updateNumberKeyState(qwertyShowNumberButtonsPreference ?: false)
                qwertyView.setPopUpViewState(qwertyShowPopupWindowPreference ?: true)
                qwertyView.setFlickUpDetectionEnabled(qwertyEnableFlickUpPreference ?: false)
                qwertyView.setFlickDownDetectionEnabled(qwertyEnableFlickDownPreference ?: false)
                qwertyView.setNumberKeyFlickUpChars(qwertyNumberKeyFlickUpChars)
                qwertyView.setNumberKeyFlickDownChars(qwertyNumberKeyFlickDownChars)
                qwertyView.setNumberSwitchKeyTextStyle(
                    excludeNumber = qwertySwitchNumberKeyWithoutNumberPreference ?: false
                )
                qwertyView.setSwitchNumberLayoutKeyVisibility(false)
                qwertyView.setDeleteLeftFlickEnabled(isDeleteLeftFlickPreference ?: true)
                qwertyView.setDeleteUpFlickEnabled(isDeleteUpFlickPreference ?: false)
                qwertyView.setDeleteDownFlickEnabled(isDeleteDownFlickPreference ?: false)
                qwertyView.setKeyMargins(
                    verticalDp = qwertyKeyVerticalMargin ?: 5.0f,
                    horizontalGapDp = qwertyKeyHorizontalGap ?: 2.0f,
                    indentLargeDp = qwertyKeyIndentLarge ?: 23.0f,
                    indentSmallDp = qwertyKeyIndentSmall ?: 9.0f,
                    sideMarginDp = qwertyKeySideMargin ?: 4.0f,
                    textSizeSp = qwertyKeyTextSize ?: 18.0f,
                    specialTextSizeSp = qwertySpecialKeyTextSize ?: 12.0f,
                    specialIconSizeDp = qwertySpecialKeyIconSize ?: 18.0f
                )
                if (isKeyboardFloatingMode == true) {
                    suggestionRecyclerView.adapter = null
                    candidatesRowView.adapter = null
                } else {
                    suggestionRecyclerView.adapter = suggestionAdapter
                    candidatesRowView.adapter = suggestionAdapterFull
                }
                candidateTabLayout.visibility = View.INVISIBLE
                updateUpperAreaVisibility(mainView)
                val currentKeyboardType = keyboardOrder.getOrNull(currentKeyboardOrder)
                if (shouldSwitchTenkeyEnglishToQwerty() && currentInputModeForSession == InputMode.ModeEnglish && currentKeyboardType == KeyboardType.TENKEY) {
                    _tenKeyQWERTYMode.update { TenKeyQWERTYMode.TenKeyQWERTY }
                    setQwertySwitchNumberLayoutKeyVisibilityOnActiveSurface(true)
                    setCurrentQwertyRomajiModeForSession(false)
                    qwertyView.resetQWERTYKeyboard(currentInputType.getQWERTYReturnTextInEn())
                    setKeyboardSizeSwitchKeyboard(mainView)
                }
            }
            setMainSuggestionColumn(mainView)
        }
        mainLayoutBinding?.let { updateUpperAreaVisibility(it) }
        if (hasPhysicalKeyboard) {
            floatingCandidateWindow?.let {
                if (it.isShowing) it.dismiss()
            }
            floatingCandidateWindow = null
            initialCursorDetectInFloatingCandidateView = false
            initialCursorXPosition = 0
            physicalKeyboardFloatingXPosition = 0
            physicalKeyboardFloatingYPosition = 0

            ensureFloatingCandidateWindowInitialized()
            floatingDockView.setText("あ")
        }
    }

    private fun ensureFloatingCandidateWindowInitialized() {
        if (floatingCandidateWindow != null) return

        Timber.d("Initializing floatingCandidateWindow")
        val popupContentView = layoutInflater.inflate(R.layout.floating_candidate_layout, null)
        val recyclerView =
            popupContentView.findViewById<RecyclerView>(R.id.floating_candidate_recycler_view)
        recyclerView.adapter = listAdapter
        recyclerView.layoutManager = LinearLayoutManager(this)
        floatingCandidateWindow = PopupWindow(
            popupContentView,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT
        ).apply {
            isOutsideTouchable = false
            isClippingEnabled = false
            animationStyle = 0
        }
        applyThemeToFloatingCandidateListAdapter()

        if (floatingDockWindow == null && ::floatingDockView.isInitialized) {
            floatingDockWindow = PopupWindow(
                floatingDockView,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT
            ).apply {
                animationStyle = 0
            }
        }

        if (floatingModeSwitchWindow == null && ::floatingModeSwitchView.isInitialized) {
            floatingModeSwitchWindow = PopupWindow(
                floatingModeSwitchView,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT
            ).apply {
                isTouchable = false
                isClippingEnabled = false
                animationStyle = 0
            }
        }
    }

    override fun onFinishInput() {
        super.onFinishInput()
        Timber.d("onUpdate onFinishInput")
        finishComposingText()
        resetAllFlags()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        Timber.d("onUpdate onFinishInputView")
        finishComposingText()
        resetAllFlags()
        persistCurrentCustomKeyboardInputModeIfEnabled()
        isInputViewActive = false
        qwertyGlideInputCoordinator?.cancelPending()
        cachedPrimaryClipContent = null
        releaseKeyboardBackgroundVideoPlayer()
        releaseFloatingKeyboardBackgroundVideoPlayer()
        stopVoiceInput()
        _keyboardSymbolViewState.update { SymbolKeyboardState() }
        floatingCandidateWindow?.let {
            if (it.isShowing) it.dismiss()
        }
        floatingCandidateWindow = null
        floatingDockWindow?.dismiss()
        floatingModeSwitchWindow?.dismiss()
        floatingKeyboardView?.dismiss()
    }

    override fun onDestroy() {
        Timber.d("onUpdate onDestroy")
        isInputViewActive = false
        releaseKeyboardBackgroundVideoPlayer()
        releaseFloatingKeyboardBackgroundVideoPlayer()
        super.onDestroy()
        mainLayoutBinding?.apply {
            keyboardView.cancelTenKeyScope()
            keyboardSymbolView.release()
        }
        floatingKeyboardBinding?.apply {
            keyboardViewFloating.cancelTenKeyScope()
            floatingSymbolKeyboard.release()
        }
        zenzEngine = null
        qwertyGlideInputCoordinator?.cancelPending()
        englishEngine.cancelQwertyGlideWarmup()
        suggestionAdapter?.release()
        suggestionAdapter = null
        shortcutAdapter = null
        suggestionAdapterFull = null
        dismissJob = null
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        clearSymbols()
        floatingCandidateWindow = null
        floatingDockWindow = null
        floatingKeyboardView = null
        floatingModeSwitchWindow = null
        keyboardSelectionPopupWindow = null
        hasHardwareKeyboardConnected = null
        clipboardManager.removePrimaryClipChangedListener(clipboardListener)
        filteredCandidateList = null
        if (mozcUTPersonName == true) kanaKanjiEngine.releasePersonNamesDictionary()
        if (mozcUTPlaces == true) kanaKanjiEngine.releasePlacesDictionary()
        if (mozcUTWiki == true) kanaKanjiEngine.releaseWikiDictionary()
        if (mozcUTNeologd == true) kanaKanjiEngine.releaseNeologdDictionary()
        if (mozcUTWeb == true) kanaKanjiEngine.releaseWebDictionary()
        if (kanaKanjiEngine.isSystemUserDictionaryInitialized()) kanaKanjiEngine.releaseSystemUserDictionary()
        isFlickOnlyMode = null
        isOmissionSearchEnable = null
        delayTime = null
        isLearnDictionaryMode = null
        isUserDictionaryEnable = null
        isUserTemplateEnable = null
        hankakuPreference = null
        customDirectModeSpaceHankakuPreference = true
        isLiveConversionEnable = null
        liveConversionManager.enabled = false
        nBest = null
        lastCandidate = null
        flickSensitivityPreferenceValue = null
        longPressTimeoutPreferenceValue = null
        qwertyShowIMEButtonPreference = null
        qwertyShowEmojiButtonPreference = null
        defaultEmojiSkinTonePreference = EmojiSkinToneSupport.DEFAULT_SKIN_TONE
        tenkeyShowIMEButtonPreference = null
        qwertyShowCursorButtonsPreference = null
        qwertyShowNumberButtonsPreference = null
        qwertyShowSwitchRomajiEnglishPreference = null
        qwertyGlideInputPreference = false
        qwertyRomajiShiftConversionPreference = null
        qwertyShowPopupWindowPreference = null
        qwertyEnableFlickUpPreference = null
        qwertyEnableFlickDownPreference = null
        qwertyNumberKeyFlickUpChars = emptyMap()
        qwertyNumberKeyFlickDownChars = emptyMap()
        qwertyEnableZenkakuSpacePreference = null
        qwertyRomajiHankakuNumberPreference = null
        qwertyRomajiHankakuSymbolPreference = null
        switchQWERTYPassword = null
        landscapeForceQwertyPreference = null
        landscapeForceQwertyRomajiPreference = null
        clipboardPreviewVisibility = null
        clipboardPreviewTapToDelete = null
        isDeleteLeftFlickPreference = null
        isDeleteUpFlickPreference = null
        isDeleteDownFlickPreference = null
        qwertyShowKutoutenButtonsPreference = null
        qwertyShowKeymapSymbolsPreference = null
        showCandidateInPasswordPreference = null
        tabletGojuonLayoutPreference = null
        isVibration = null
        isKeySoundEnabled = null
        keySoundVolumePercent = null
        tenkeyHeightPreferenceValue = null
        tenkeyWidthPreferenceValue = null
        qwertyHeightPreferenceValue = null
        candidateViewHeightPreferenceValue = null
        candidateViewHeightEmptyPreferenceValue = null
        qwertyWidthPreferenceValue = null
        tenkeyPositionPreferenceValue = null
        tenkeyBottomMarginPreferenceValue = null
        qwertyPositionPreferenceValue = null
        qwertyBottomMarginPreferenceValue = null

        tenkeyStartMarginPreferenceValue = null
        tenkeyEndMarginPreferenceValue = null
        qwertyStartMarginPreferenceValue = null
        qwertyEndMarginPreferenceValue = null

        tenkeyLandScapeStartMarginPreferenceValue = null
        tenkeyLandScapeEndMarginPreferenceValue = null
        qwertyLandScapeStartMarginPreferenceValue = null
        qwertyLandScapeEndMarginPreferenceValue = null

        enableShowLastShownKeyboardInRestart = null
        lastSavedKeyboardPosition = null

        tenkeyHeightLandScapePreferenceValue = null
        tenkeyWidthLandScapePreferenceValue = null
        qwertyHeightLandScapePreferenceValue = null
        candidateViewLandScapeHeightPreferenceValue = null
        candidateViewLandScapeHeightEmptyPreferenceValue = null
        qwertyWidthLandScapePreferenceValue = null
        tenkeyLandScapePositionPreferenceValue = null
        tenkeyLandScapeBottomMarginPreferenceValue = null
        qwertyLandScapePositionPreferenceValue = null
        qwertyLandScapeBottomMarginPreferenceValue = null

        zenzEnableStatePreference = null
        zenzaiEnableStatePreference = null
        zenzProfilePreference = null
        zenzEnableLongPressConversionPreference = null
        zenzRerankPreference = null

        qwertyKeyVerticalMargin = null
        qwertyKeyHorizontalGap = null
        qwertyKeyIndentLarge = null
        qwertyKeyIndentSmall = null
        qwertyKeySideMargin = null
        qwertyKeyTextSize = null
        qwertySpecialKeyTextSize = null
        qwertySpecialKeyIconSize = null

        keyboardThemeMode = null
        customThemeBgColor = null
        customThemeKeyColor = null
        customThemeSpecialKeyColor = null
        customThemeKeyTextColor = null
        customThemeSpecialKeyTextColor = null
        customThemeCandidateTextColor = null
        customThemeCandidateItemBgColor = null
        customThemeCandidateItemPressedBgColor = null
        customThemeShortcutIconColor = null
        customThemePopupBgColor = null
        customThemePopupTextColor = null

        vibrationTimingStr = null
        mozcUTPersonName = null
        romajiConverter = null
        mozcUTPlaces = null
        mozcUTWiki = null
        mozcUTNeologd = null
        mozcUTWeb = null
        sumireInputKeyType = null
        sumireInputKeyLayoutType = null
        sumireInputStyle = null
        candidateColumns = null
        candidateColumnsLandscape = null
        candidateViewHeight = null
        candidateTabVisibility = null
        isTablet = null
        isNgWordEnable = null
        deleteKeyHighLight = null
        customKeyboardSuggestionPreference = null
        flickKeymapGuidePreference = null
        flickGuideTextSizeSpPreference = null
        flickGuideMaxCharactersPreference = null
        zenzDebounceTimePreference = null
        zenzMaximumLetterSizePreference = null
        zenzMaximumContextSizePreference = null
        zenzMaximumThreadSizePreference = null
        symbolKeyboardFirstItem = null
        userDictionaryPrefixMatchNumber = null
        isCustomKeyboardTwoWordsOutputEnable = null
        tenkeyQWERTYSwitchNumber = null
        tabletTenkeyQwertySwitchEnglish = false
        tenkeyQKeymapGuide = null
        isKeyboardFloatingMode = null
        isKeyboardRounded = null
        bunsetsuSeparation = null
        bunsetsuCursorMove = null
        reconversionEnabledPreference = false
        conversionKeySwipePreference = null
        bunsetsuPositionList = null
        bunsetsuSplitPatterns = emptyList()
        bunsetsuConversionSession = null
        pendingReconversionEntry = null
        bunsetsuReconversionDraft = null
        preserveBunsetsuReconversionDraftOnNextProcessInput = false
        isRestoringReconversionInput = false

        enableGemmaTranslationPreference = null

        liquidGlassThemePreference = null
        liquidGlassBlurRadiousPreference = null
        liquidGlassKeyBlurRadiousPreference = null
        customKeyBorderEnablePreference = null
        customKeyBorderEnableColor = null

        customComposingTextPreference = null
        inputCompositionBackgroundColor = null
        inputCompositionTextColor = null
        inputCompositionAfterBackgroundColor = null
        inputConversionBackgroundColor = null
        inputConversionTextColor = null

        previousTenKeyQWERTYMode = null

        sumireEnglishQwertyPreference = null
        conversionCandidatesRomajiEnablePreference = null
        enableZenzRightContextPreference = null
        learnFirstCandidateDictionaryPreference = null
        enablePredictionSearchLearnDictionaryPreference = null
        learnPredictionPreference = null
        circularFlickWindowScale = null
        customKeyBorderWidth = null
        qwertySwitchNumberKeyWithoutNumberPreference = null
        customRomajiZenkakuConversionEnablePreference = null
        omissionSearchOffsetScorePreference = null
        enableTypoCorrectionJapaneseFlickKeyboardOffsetScorePreference = null

        enableTypoCorrectionJapaneseFlickKeyboardPreference = null
        enableTypoCorrectionQwertyEnglishKeyboardPreference = null

        inputManager.unregisterInputDeviceListener(this)
        actionInDestroy()
        speechRecognizer?.destroy()
        speechRecognizer = null
        System.gc()
        dismissFloatingDock()
    }

    override fun onComputeInsets(outInsets: Insets?) {
        super.onComputeInsets(outInsets)
        if (isKeyboardFloatingMode == true) {
            val inputHeight = window.window?.decorView?.height ?: 0
            outInsets?.contentTopInsets = inputHeight
            outInsets?.visibleTopInsets = inputHeight
            outInsets?.touchableInsets = Insets.TOUCHABLE_INSETS_CONTENT
        }
    }

    override fun onConfigureWindow(win: Window?, isFullscreen: Boolean, isCandidatesOnly: Boolean) {
        super.onConfigureWindow(win, isFullscreen, isCandidatesOnly)
        // Android 12 (API 31) 以上の場合
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        ) {
            if (liquidGlassThemePreference == true &&
                isKeyboardFloatingMode != true &&
                hasHardwareKeyboardConnected != true
            ) {
                // 背景のアプリに対してブラーをかける
                win?.setBackgroundBlurRadius(50)
            } else {
                win?.setBackgroundBlurRadius(0)
            }
        }
    }

    override fun onUpdateCursorAnchorInfo(cursorAnchorInfo: CursorAnchorInfo?) {
        super.onUpdateCursorAnchorInfo(cursorAnchorInfo)
        if (isSystemUiRemoteInputSession) return
        if (physicalKeyboardEnable.replayCache.firstOrNull() == true) {
            ensureFloatingCandidateWindowInitialized()
        }
        Timber.d("onUpdateCursorAnchorInfo start: [${cursorAnchorInfo == null}] [${floatingCandidateWindow == null}]")
        val insertString = inputString.value
        if (!hardwareKeyboardCoordinator.shouldProcessFloatingCandidateAnchor(
                cursorAnchorInfo = cursorAnchorInfo,
                floatingCandidateWindow = floatingCandidateWindow,
                composingInputNonEmpty = insertString.isNotEmpty(),
            )
        ) {
            isPostCommitPredictionActive = false
            floatingCandidateWindow?.dismiss()
            initialCursorDetectInFloatingCandidateView = false
            initialCursorXPosition = 0
            return
        }
        val density = resources.displayMetrics.density
        val offsetPx = (8 * density).toInt()

        // Measure popupWindow height
        val popupView = floatingCandidateWindow?.contentView
        if (popupView != null) {
            popupView.measure(
                View.MeasureSpec.makeMeasureSpec(resources.displayMetrics.widthPixels, View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec(resources.displayMetrics.heightPixels, View.MeasureSpec.AT_MOST)
            )
        }
        val popupHeight = popupView?.measuredHeight ?: 0
        val screenHeight = resources.displayMetrics.heightPixels

        val anchor = hardwareKeyboardCoordinator.resolveFloatingCandidateAnchor(
            cursorAnchorInfo = cursorAnchorInfo!!,
            initialCursorDetectInFloatingCandidateView = initialCursorDetectInFloatingCandidateView,
            initialCursorXPosition = initialCursorXPosition,
            yOffset = offsetPx,
            screenHeight = screenHeight,
            popupWindowHeight = popupHeight,
        )
        val decorView = this@IMEService.window.window?.decorView
        val location = IntArray(2)
        decorView?.getLocationOnScreen(location)
        val decorX = location[0]
        val decorY = location[1]

        val targetX = anchor.x - decorX
        val targetY = anchor.y - decorY

        physicalKeyboardFloatingXPosition = targetX
        physicalKeyboardFloatingYPosition = targetY
        initialCursorXPosition = anchor.initialCursorXPosition
        initialCursorDetectInFloatingCandidateView = anchor.markInitialCursorDetect
        floatingCandidateWindow?.let { window ->
            val adjustedAnchor = anchor.copy(
                x = targetX,
                y = targetY
            )
            hardwareKeyboardCoordinator.applyFloatingCandidateAnchor(
                update = adjustedAnchor,
                window = window,
                host = floatingCandidateWindowHost,
            )
        }
    }

    private val floatingCandidateWindowHost =
        HardwareKeyboardCoordinator.FloatingCandidateWindowHost { x, y, window ->
            if (isSystemUiRemoteInputSession) return@FloatingCandidateWindowHost
            Timber.d("onUpdateCursorAnchorInfo window debug: [$x] [$y] [${window.isShowing}]")
            if (window.isShowing) {
                window.update(x, y, -1, -1)
            } else {
                val anchor = mainLayoutBinding?.let { resolveShowListPopupAnchor(it) }
                    ?: this@IMEService.window.window?.decorView
                showPopupWindowSafely(
                    popupWindow = window,
                    anchorView = anchor,
                    gravity = Gravity.NO_GRAVITY,
                    x = x,
                    y = y,
                    source = "onUpdateCursorAnchorInfo",
                )
            }
        }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        when (newConfig.orientation) {
            Configuration.ORIENTATION_PORTRAIT -> {
                finishComposingText()
                setComposingText("", 0)
                Timber.d("onConfigurationChanged: ORIENTATION_PORTRAIT")
            }

            Configuration.ORIENTATION_LANDSCAPE -> {
                finishComposingText()
                setComposingText("", 0)
                Timber.d("onConfigurationChanged: ORIENTATION_LANDSCAPE")
            }

            Configuration.ORIENTATION_UNDEFINED -> {
                finishComposingText()
                setComposingText("", 0)
                Timber.d("onConfigurationChanged: ORIENTATION_UNDEFINED")
            }

            else -> {
                finishComposingText()
                setComposingText("", 0)
                Timber.d("onConfigurationChanged: else")
            }
        }

        val newNightMode = newConfig.uiMode and Configuration.UI_MODE_NIGHT_MASK

        if (newNightMode != currentNightMode) {
            setupKeyboardView()
            currentNightMode = newNightMode
        }

        refreshKeyboardForCurrentOrientation()

    }

    override fun onEvaluateInputViewShown(): Boolean {
        super.onEvaluateInputViewShown()
        return true
    }

    /**
     * FloatingDockViewを非表示にします。
     */
    private fun dismissFloatingDock() {
        if (floatingDockWindow?.isShowing == true) {
            floatingDockWindow?.dismiss()
            floatingDockWindow = null
        }
    }

    private fun canShowPopupWindow(anchorView: View?): Boolean {
        if (!isInputViewActive) return false
        if (anchorView == null) return false
        if (!anchorView.isAttachedToWindow) return false
        if (anchorView.windowToken == null) return false
        return true
    }

    private fun resolveShowListPopupAnchor(mainView: MainLayoutBinding): View? {
        if (isKeyboardFloatingMode != true) {
            return requireActiveKeyboardSurface()?.rootView ?: mainView.root
        }

        val mainRoot = mainLayoutBinding?.root
        if (mainRoot?.isAttachedToWindow == true && mainRoot.windowToken != null) {
            return mainRoot
        }

        val decorView = window.window?.decorView
        if (decorView?.isAttachedToWindow == true && decorView.windowToken != null) {
            return decorView
        }

        return null
    }

    private fun showPopupWindowSafely(
        popupWindow: PopupWindow,
        anchorView: View?,
        gravity: Int,
        x: Int,
        y: Int,
        source: String
    ): Boolean {
        if (!canShowPopupWindow(anchorView)) {
            Timber.w("$source: Skip showAtLocation because anchor is not attached.")
            return false
        }
        return runCatching {
            popupWindow.showAtLocation(anchorView, gravity, x, y)
            true
        }.onFailure { throwable ->
            Timber.w(throwable, "$source: showAtLocation failed")
        }.getOrDefault(false)
    }

    private fun updateComposingText(text: String) {
        editorGateway.setComposingText(text, 1)
    }

    private fun commitRecognizedText(text: String) {
        editorGateway.commitRecognizedText(text)
    }

    private fun startVoiceInput(
        mainView: MainLayoutBinding
    ) {
        Timber.d("startVoiceInput: [$isListening] [$speechRecognizer]")
        setSuggestionProgressVisible(
            reason = SuggestionProgressReason.VoiceInput,
            visible = false
        )
        if (isListening) return
        if (speechRecognizer == null) return

        val languageValue: String = when {
            qwertyMode.value == TenKeyQWERTYMode.TenKeyQWERTY -> {
                "en-CA"
            }

            qwertyMode.value == TenKeyQWERTYMode.TenKeyQWERTYRomaji -> {
                if (currentQwertyRomajiModeForSession) {
                    "ja-JP"
                } else {
                    "en-CA"
                }
            }

            else -> {
                when (currentTenkeyInputMode(mainView)) {
                    InputMode.ModeJapanese -> "ja-JP"
                    InputMode.ModeEnglish -> "en-CA"
                    InputMode.ModeNumber -> "ja-JP"
                }
            }
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            // 日本語固定にしたければ "ja-JP"
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageValue)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }

        try {
            speechRecognizer?.startListening(intent)
            isListening = true
        } catch (e: SecurityException) {
            // RECORD_AUDIO が許可されていないなど
            isListening = false
        }
    }

    private fun stopVoiceInput() {
        if (!isListening) return
        try {
            speechRecognizer?.stopListening()
        } catch (_: Exception) {
        } finally {
            isListening = false
        }
    }

    /**
     * Dynamically changes the size of the floating keyboard view.
     * Pass null for a dimension you don't want to change.
     *
     * @param newWidthDp The desired new width in DP, or null to keep the current width.
     */
    private fun changeFloatingKeyboardSize(newWidthDp: Int? = null) {
        val popupWindow = floatingKeyboardView ?: return

        val newWidthPx = newWidthDp ?: popupWindow.width

        val savedX = appPreference.keyboard_floating_position_x
        val savedY = appPreference.keyboard_floating_position_y

        popupWindow.update(
            savedX, savedY, newWidthPx, ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private fun setupKeyboardView() {
        Timber.d("setupKeyboardView: Called")
        val isDynamicColorsEnable = DynamicColors.isDynamicColorAvailable()
        val ctx = when (keyboardThemeMode) {
            "default" -> {
                if (isDynamicColorsEnable) {
                    val seedColor = appPreference.seedColor

                    if (seedColor == 0x00000000) {
                        DynamicColors.wrapContextIfAvailable(this, R.style.Theme_MarkdownKeyboard)
                    } else {
                        val baseThemedContext =
                            ContextThemeWrapper(this, R.style.Theme_MarkdownKeyboard)
                        val options =
                            DynamicColorsOptions.Builder().setContentBasedSource(seedColor).build()
                        DynamicColors.wrapContextIfAvailable(baseThemedContext, options)
                    }
                } else {
                    ContextThemeWrapper(this, R.style.Theme_MarkdownKeyboard)
                }
            }

            "custom" -> {
                ContextThemeWrapper(this, R.style.Theme_MarkdownKeyboard)
            }

            else -> {
                if (isDynamicColorsEnable) {
                    val seedColor = appPreference.seedColor

                    if (seedColor == 0x00000000) {
                        DynamicColors.wrapContextIfAvailable(this, R.style.Theme_MarkdownKeyboard)
                    } else {
                        val baseThemedContext =
                            ContextThemeWrapper(this, R.style.Theme_MarkdownKeyboard)
                        val options =
                            DynamicColorsOptions.Builder().setContentBasedSource(seedColor).build()
                        DynamicColors.wrapContextIfAvailable(baseThemedContext, options)
                    }
                } else {
                    ContextThemeWrapper(this, R.style.Theme_MarkdownKeyboard)
                }
            }
        }

        floatingDockView = FloatingDockView(ctx).apply {
            setText("あ")
            setOnFloatingDockListener(object : FloatingDockListener {
                override fun onDockClick() {
                    Timber.d("setOnFloatingDockListener: Dockがクリックされました")
                }

                override fun onIconClick() {
                    Timber.d("setOnFloatingDockListener: Iconがクリックされました")
                    scope.launch {
                        _physicalKeyboardEnable.emit(false)
                    }
                    floatingDockWindow?.dismiss()
                    floatingModeSwitchWindow?.dismiss()
                }
            })
        }

        floatingModeSwitchView = BubbleTextView(ctx).apply {
            text = "あ"
        }

        mainLayoutBinding = MainLayoutBinding.inflate(LayoutInflater.from(ctx))

        releaseFloatingKeyboardBackgroundVideoPlayer()
        floatingKeyboardBinding = FloatingKeyboardLayoutBinding.inflate(LayoutInflater.from(ctx))
        // floatingKeyboardBinding を作り直したので configureQwertyView guard をリセット。
        isFloatingQwertyConfigured = false

        floatingKeyboardBinding?.let { floatingKeyboardLayoutBinding ->
            setFloatingKeyboardListeners(floatingKeyboardLayoutBinding = floatingKeyboardLayoutBinding)
            val heightPref = tenkeyHeightPreferenceValue ?: 280
            val widthPref = tenkeyWidthPreferenceValue ?: 100
            val keyboardMarginBottomPref = appPreference.keyboard_vertical_margin_bottom ?: 0

            val density = resources.displayMetrics.density
            val screenWidth = resources.displayMetrics.widthPixels
            val isPortrait =
                resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
            val keyboardMarginBottom = (keyboardMarginBottomPref * density).toInt()
            val heightPx = when {
                keyboardSymbolViewState.value.isShown -> {
                    val height = if (isPortrait) 320 else 220
                    (height * density).toInt()
                }

                else -> {
                    val clampedHeight = heightPref.coerceIn(180, 420)
                    (clampedHeight * density).toInt()
                }
            }

            Timber.d("setKeyboardSize: $heightPx $keyboardMarginBottom")

            val widthPx = when {
                widthPref == 100 || keyboardSymbolViewState.value.isShown -> {
                    ViewGroup.LayoutParams.MATCH_PARENT
                }

                else -> {
                    (screenWidth * (widthPref / 100f)).toInt()
                }
            }
            if (floatingKeyboardView == null) {
                floatingKeyboardView = PopupWindow(
                    floatingKeyboardLayoutBinding.root,
                    widthPx,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
            } else {
                floatingKeyboardView?.dismiss()
                floatingKeyboardView = null

                floatingKeyboardView = PopupWindow(
                    floatingKeyboardLayoutBinding.root,
                    widthPx,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
            }
            updateFloatingKeyboardBackgroundBounds(floatingKeyboardLayoutBinding, heightPx)
            updateFloatingFullCandidatesHeight(floatingKeyboardLayoutBinding, heightPx)
        }

        keyboardContainer?.let { container ->
            container.removeAllViews()
            mainLayoutBinding?.root?.let { newRootView ->
                container.addView(newRootView)
                mainLayoutBinding?.let { mainView ->
                    mainView.toolbarToggleButton.setOnClickListener {
                        isToolbarForcedShow = !isToolbarForcedShow
                        updateUpperAreaVisibility(mainView)
                    }
                    when (keyboardThemeMode) {
                        "default" -> {
                            if (isDynamicColorsEnable) {
                                mainView.apply {
                                    root.setBackgroundResource(com.kazumaproject.core.R.drawable.keyboard_root_material)
                                    suggestionViewParent.setBackgroundResource(com.kazumaproject.core.R.drawable.keyboard_root_material)
                                    suggestionVisibility.setBackgroundResource(com.kazumaproject.core.R.drawable.recyclerview_size_button_bg_material)
                                    candidateTabLayout.setBackgroundResource(com.kazumaproject.core.R.drawable.keyboard_root_material)
                                }
                                floatingKeyboardBinding?.apply {
                                    root.setBackgroundResource(com.kazumaproject.core.R.drawable.keyboard_root_material_floating)
                                    suggestionViewParent.setBackgroundResource(com.kazumaproject.core.R.drawable.keyboard_root_material_floating)
                                    suggestionVisibility.setBackgroundResource(com.kazumaproject.core.R.drawable.recyclerview_size_button_bg_material)
                                }
                            }
                        }

                        "custom" -> {
                            mainView.apply {
                                root.setBackgroundResource(com.kazumaproject.core.R.drawable.keyboard_root_material)
                                suggestionViewParent.setBackgroundResource(com.kazumaproject.core.R.drawable.keyboard_root_material)
                                suggestionVisibility.setBackgroundResource(com.kazumaproject.core.R.drawable.recyclerview_size_button_bg_material)
                                candidateTabLayout.setBackgroundResource(com.kazumaproject.core.R.drawable.keyboard_root_material)
                                applyThemeToSuggestionAdapter()

                                root.setDrawableSolidColor(customThemeBgColor ?: Color.WHITE)
                                suggestionViewParent.setDrawableSolidColor(
                                    customThemeBgColor ?: Color.WHITE
                                )
                                suggestionVisibility.setDrawableSolidColor(
                                    customThemeSpecialKeyColor ?: Color.GRAY
                                )
                                candidateTabLayout.setLayerTypeSolidColor(
                                    customThemeBgColor ?: Color.WHITE
                                )

                                suggestionVisibility.setColorFilter(
                                    customThemeKeyTextColor ?: Color.BLACK
                                )
                            }
                            floatingKeyboardBinding?.apply {
                                root.setBackgroundResource(com.kazumaproject.core.R.drawable.keyboard_root_material_floating)
                                suggestionViewParent.setBackgroundResource(com.kazumaproject.core.R.drawable.keyboard_root_material_floating)
                                suggestionVisibility.setBackgroundResource(com.kazumaproject.core.R.drawable.recyclerview_size_button_bg_material)

                                root.setDrawableSolidColor(customThemeBgColor ?: Color.WHITE)
                                suggestionViewParent.setDrawableSolidColor(
                                    customThemeBgColor ?: Color.WHITE
                                )
                                suggestionVisibility.setDrawableSolidColor(
                                    customThemeSpecialKeyColor ?: Color.GRAY
                                )
                            }
                        }

                        else -> {
                            if (isDynamicColorsEnable) {
                                mainView.apply {
                                    root.setBackgroundResource(com.kazumaproject.core.R.drawable.keyboard_root_material)
                                    suggestionViewParent.setBackgroundResource(com.kazumaproject.core.R.drawable.keyboard_root_material)
                                    suggestionVisibility.setBackgroundResource(com.kazumaproject.core.R.drawable.recyclerview_size_button_bg_material)
                                    candidateTabLayout.setBackgroundResource(com.kazumaproject.core.R.drawable.keyboard_root_material)
                                }
                                floatingKeyboardBinding?.apply {
                                    root.setBackgroundResource(com.kazumaproject.core.R.drawable.keyboard_root_material_floating)
                                    suggestionViewParent.setBackgroundResource(com.kazumaproject.core.R.drawable.keyboard_root_material_floating)
                                    suggestionVisibility.setBackgroundResource(com.kazumaproject.core.R.drawable.recyclerview_size_button_bg_material)
                                }
                            }
                        }
                    }
                    mainView.root.outlineProvider = ViewOutlineProvider.BACKGROUND
                    mainView.root.clipToOutline = isKeyboardRounded == true
                    applyKeyboardBackgroundIfNeeded(mainView)
                    if (isKeyboardFloatingMode == true) {
                        floatingKeyboardBinding?.let { applyFloatingKeyboardBackgroundIfNeeded(it) }
                    }
                    ViewCompat.setOnApplyWindowInsetsListener(mainView.root) { _, windowInsets ->
                        val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
                        if (systemBottomInset != insets.bottom) {
                            systemBottomInset = insets.bottom
                            mainView.root.post {
                                setKeyboardSizeSwitchKeyboard(mainView)
                            }
                        }
                        windowInsets
                    }
                    setCandidateTabLayout(mainView)
                    setupCustomKeyboardListeners(mainView)
                    setSuggestionRecyclerView(
                        mainView, FlexboxLayoutManager(applicationContext).apply {
                            flexDirection = FlexDirection.ROW
                            justifyContent = JustifyContent.FLEX_START
                        })
                    setShortCutAdapter(mainView)
                    setSymbolKeyboard(mainView)
                    setQWERTYKeyboard(mainView)
                    if (isTablet == true) {
                        setTabletKeyListeners(mainView)
                    }
                    setTenKeyListeners(mainView)
                    setKeyboardSizeSwitchKeyboard(mainView)
                    applyCurrentTypefacesToViews()
                    updateClipboardPreview()
                    mainView.suggestionRecyclerView.isVisible = suggestionViewStatus.value
                    updateUpperAreaVisibility(mainView)
                    applyThemeToSuggestionAdapter()
                    applyThemeToSymbolKeyboard()
                }
            }
        }
    }

    override fun onUpdateSelection(
        oldSelStart: Int,
        oldSelEnd: Int,
        newSelStart: Int,
        newSelEnd: Int,
        candidatesStart: Int,
        candidatesEnd: Int
    ) {
        if (isPromotingTail) {
            timber.log.Timber.d("onUpdateSelection: ignored during promoting tail")
            return
        }
        super.onUpdateSelection(
            oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd
        )

        if (
            SystemClock.uptimeMillis() < suppressSelectionCleanupUntilMillis &&
            (inputString.value.isNotEmpty() || stringInTail.get().isNotEmpty())
        ) {
            Timber.d("onUpdateSelection ignored for internal preedit cursor move: [${inputString.value}] [${stringInTail.get()}]")
            refreshReconversionUi()
            return
        }

        if (suppressedSelectionCleanupCount > 0) {
            suppressedSelectionCleanupCount -= 1
            Timber.d("onUpdateSelection suppressed: [${inputString.value}] [${stringInTail.get()}]")
            return
        }

        if (candidatesStart == -1 && candidatesEnd == -1) {
            if (_inputString.value.isNotEmpty() || stringInTail.get().isNotEmpty()) {
                Timber.d("onUpdateSelection: composing text cleared externally. Resetting inputString and tail.")
                finishComposingText()
                resetAllFlags()
            }
            syncZenzLeftContextFromEditor()
        }

        // Check if composing text is active
        if (candidatesStart != -1 && candidatesEnd != -1) {
            // User moved cursor inside composing text.
            if (newSelStart == newSelEnd && newSelStart >= candidatesStart && newSelStart <= candidatesEnd) {
                if (newSelStart < candidatesEnd) {
                    val fullComposing = inputString.value + stringInTail.get()
                    val composingRangeLength = candidatesEnd - candidatesStart

                    if (composingRangeLength != fullComposing.length) {
                        if (isHenkan.get()) {
                            Timber.d("onUpdateSelection: composing length mismatch during conversion, committing henkan text.")
                            commitCurrentHenkanForNewInput()
                        } else {
                            Timber.d("onUpdateSelection: composing length mismatch while editing preedit, preserving current head/tail.")
                            suppressSelectionCleanupForInternalPreEditMove()
                            refreshCandidateForCurrentPreedit()
                        }
                        return
                    }

                    val visualOffset = newSelStart - candidatesStart
                    if (visualOffset >= 0 && visualOffset <= fullComposing.length && visualOffset != inputString.value.length) {
                        val newHead = fullComposing.substring(0, visualOffset)
                        val newTail = fullComposing.substring(visualOffset)
                        Timber.d("User moved cursor in composing text manually: newHead=$newHead, newTail=$newTail")
                        stringInTail.set(newTail)
                        _inputString.update { newHead }
                        suppressSelectionCleanupForInternalPreEditMove()
                        refreshCandidateForCurrentPreedit()
                    }
                }
            } else {
                Timber.d("onUpdateSelection: user moved cursor outside composing region. Finishing and resetting.")
                finishComposingText()
                resetAllFlags()
            }
            return
        }

        val selectedText = if (newSelStart != newSelEnd) {
            editorGateway.getSelectedText(0)?.toString().orEmpty()
        } else {
            ""
        }
        if (selectedText.isNotEmpty()) {
            if (selectedTextGemmaSession?.selectedText != null &&
                selectedTextGemmaSession?.selectedText != selectedText
            ) {
                clearSelectedTextGemmaSession(clearSuggestions = true)
            }
            if (AppVariantConfig.hasGemma &&
                appPreference.enable_gemma_translation_preference &&
                gemmaTranslationManager.isTranslationAvailable()
            ) {
                showSelectedTextGemmaActions(selectedText)
            } else {
                clearSelectedTextGemmaSession(clearSuggestions = true)
            }
            return
        } else if (selectedTextGemmaSession != null) {
            clearSelectedTextGemmaSession(clearSuggestions = true)
        }

        val preservedPreEdit = preservePreEditOnNextSelectionUpdate
        if (preservedPreEdit != null) {
            preservePreEditOnNextSelectionUpdate = null
            if (_inputString.value == preservedPreEdit && preservedPreEdit.isNotEmpty()) {
                Timber.d("onUpdateSelection preserve preedit after cancel: $preservedPreEdit")
                refreshReconversionUi()
                return
            }
        }

        Timber.d("onUpdateSelection end called: [${inputString.value}] [${stringInTail.get()}] [${bunsetusMultipleDetect}]")
        if (stringInTail.get().isEmpty()) {
            bunsetusMultipleDetect = false
        }


        updateClipboardPreview()

        val tail = stringInTail.get()
        val hasTail = tail.isNotEmpty()
        val caretTop = newSelStart == 0 && newSelEnd == 0

        Timber.d("onUpdateSelection tail: $tail")

        when {

            hasTail || _inputString.value.isNotEmpty() -> {
                Timber.d("onUpdateSelection preserves preedit after caret move: head=[${_inputString.value}] tail=[$tail] caretTop=$caretTop")
                suppressSelectionCleanupForInternalPreEditMove()
                refreshCandidateForCurrentPreedit()
            }
        }
        refreshReconversionUi()
    }

    private fun refreshCandidateForCurrentPreedit() {
        val head = inputString.value
        if (head.isBlank() && stringInTail.get().isEmpty()) return
        invalidateLiveConversionAfterInternalCursorMove()
        beginZenzRerankRequest()
        lastCandidate = null
        if (head.isNotEmpty()) {
            val spannable = createSpannableWithTail(head)
            val preEditBackground = if (customComposingTextPreference == true) {
                inputCompositionBackgroundColor
                    ?: getColor(com.kazumaproject.core.R.color.char_in_edit_color)
            } else {
                getColor(com.kazumaproject.core.R.color.char_in_edit_color)
            }
            val afterEditBackground = if (customComposingTextPreference == true) {
                inputCompositionAfterBackgroundColor
                    ?: getColor(com.kazumaproject.core.R.color.blue)
            } else {
                getColor(com.kazumaproject.core.R.color.blue)
            }
            setComposingTextPreEdit(
                inputString = head,
                spannableString = spannable,
                backgroundColor = preEditBackground,
                textColor = if (customComposingTextPreference == true) inputCompositionTextColor else null,
            )
            setComposingTextAfterEdit(
                inputString = head,
                spannableString = spannable,
                backgroundColor = afterEditBackground,
                textColor = if (customComposingTextPreference == true) inputCompositionTextColor else null,
            )
        }
        scope.launch {
            _suggestionFlag.emit(CandidateShowFlag.Updating)
        }
    }

    private fun suppressSelectionCleanupForInternalPreEditMove() {
        if (suppressedSelectionCleanupCount < 2) {
            suppressedSelectionCleanupCount = 2
        }
        suppressSelectionCleanupUntilMillis = SystemClock.uptimeMillis() + 800L
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        mainLayoutBinding?.let { mainView ->
            event?.let { e ->
                logPhysicalKeyEventForDebug(keyCode, e)
                val insertString = inputString.value
                val suggestions = listAdapter.currentList
                if (handlePhysicalKeyboardShortcut(
                        keyCode,
                        e,
                        mainView,
                        insertString,
                        suggestions
                    )
                ) {
                    return true
                }
            }

            val handled = inputActionDispatcher.dispatchOnKeyDown(
                keyCode = keyCode,
                event = event,
                mainView = mainView,
                handlers = InputModeKeyHandlers(
                    onJapaneseKeyDown = { code, ev, view -> handleJapaneseKeyDown(code, ev, view) },
                    onEnglishKeyDown = { code, ev, view -> handleEnglishKeyDown(code, ev, view) },
                    onNumberKeyDown = { code, ev, view -> handleNumberKeyDown(code, ev, view) },
                ),
            )
            return handled
        }
        return super.onKeyDown(keyCode, event)
    }

// ---------------------------------------------------------------------------------
// 1. メインのモード別ハンドラ
// ---------------------------------------------------------------------------------

    /**
     * 日本語入力モード時のキーダウン処理
     */
    private fun handleJapaneseKeyDown(
        keyCode: Int, event: KeyEvent?, mainView: MainLayoutBinding
    ): Boolean {
        val e = event ?: return super.onKeyDown(keyCode, event)
        val insertString = inputString.value
        val suggestions = listAdapter.currentList

        if (!isFunctionKeyConversionKeyCode(keyCode)) {
            clearFunctionKeyConversionSource()
        }

        handleBunsetsuPhysicalNavigation(keyCode, e)?.let {
            return it
        }

        if (isHenkan.get() && (keyCode == KeyEvent.KEYCODE_DEL ||
                    keyCode == KeyEvent.KEYCODE_FORWARD_DEL ||
                    keyCode == KeyEvent.KEYCODE_ESCAPE)
        ) {
            return handleJapaneseDeleteFloating(keyCode, e, insertString)
        }

        if (e.isCtrlPressed) {
            return handleJapaneseCtrlPressed(keyCode, e, mainView, insertString)
        }

        return when (keyCode) {
            KeyEvent.KEYCODE_F6, KeyEvent.KEYCODE_F7, KeyEvent.KEYCODE_F8, KeyEvent.KEYCODE_F9, KeyEvent.KEYCODE_F10 -> handleConversionKeyFloating(
                keyCode,
                insertString
            )

            KeyEvent.KEYCODE_ZENKAKU_HANKAKU -> toggleJapaneseEnglishMode(mainView)
            KeyEvent.KEYCODE_HENKAN -> switchToJapaneseFromPhysicalKey(
                mainView,
                insertString,
                suggestions
            )

            KeyEvent.KEYCODE_MUHENKAN -> switchToEnglishModeFloating(mainView)
            KeyEvent.KEYCODE_KATAKANA_HIRAGANA,
            KeyEvent.KEYCODE_KANA -> switchToHiraganaMode(mainView)

            KeyEvent.KEYCODE_EISU -> switchToEnglishModeFloating(mainView)

            KeyEvent.KEYCODE_DEL, KeyEvent.KEYCODE_FORWARD_DEL -> handleJapaneseDeleteFloating(
                keyCode,
                e,
                insertString
            )

            KeyEvent.KEYCODE_SPACE -> handleJapaneseSpaceFloating(
                mainView, insertString, suggestions
            )

            KeyEvent.KEYCODE_DPAD_LEFT -> handleJapaneseDpadLeft(insertString)
            KeyEvent.KEYCODE_DPAD_RIGHT -> handleJapaneseDpadRight(insertString)
            KeyEvent.KEYCODE_DPAD_UP -> handleJapaneseDpadUp(mainView, insertString, suggestions)
            KeyEvent.KEYCODE_DPAD_DOWN -> handleJapaneseDpadDown(
                mainView, insertString, suggestions
            )

            KeyEvent.KEYCODE_ENTER -> handleJapaneseEnterFloating(
                mainView, insertString, suggestions
            )

            KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> super.onKeyDown(keyCode, e)

            in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z, in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9, KeyEvent.KEYCODE_MINUS, KeyEvent.KEYCODE_EQUALS, KeyEvent.KEYCODE_LEFT_BRACKET, KeyEvent.KEYCODE_RIGHT_BRACKET, KeyEvent.KEYCODE_BACKSLASH, KeyEvent.KEYCODE_SEMICOLON, KeyEvent.KEYCODE_APOSTROPHE, KeyEvent.KEYCODE_COMMA, KeyEvent.KEYCODE_PERIOD, KeyEvent.KEYCODE_SLASH, KeyEvent.KEYCODE_GRAVE, KeyEvent.KEYCODE_AT, KeyEvent.KEYCODE_YEN, KeyEvent.KEYCODE_RO, KeyEvent.KEYCODE_NUMPAD_DIVIDE, KeyEvent.KEYCODE_NUMPAD_MULTIPLY, KeyEvent.KEYCODE_NUMPAD_SUBTRACT, KeyEvent.KEYCODE_NUMPAD_ADD, KeyEvent.KEYCODE_NUMPAD_DOT -> {
                handleJapaneseCharacterKeyFloating(
                    keyCode, e, insertString
                )
            }

            else -> super.onKeyDown(keyCode, e)
        }
    }

    /**
     * 英語入力モード時のキーダウン処理
     */
    private fun handleEnglishKeyDown(
        keyCode: Int, event: KeyEvent?, mainView: MainLayoutBinding // mainViewの実際の型に置き換えてください
    ): Boolean {
        event?.let { e ->
            if (e.isCtrlPressed) {
                if (keyCode == KeyEvent.KEYCODE_SPACE) {
                    // 英語モード時のCtrl+Spaceは日本語モードへ
                    customKeyboardMode = when (customKeyboardMode) {
                        KeyboardInputMode.HIRAGANA -> KeyboardInputMode.ENGLISH
                        KeyboardInputMode.ENGLISH -> KeyboardInputMode.HIRAGANA
                        KeyboardInputMode.SYMBOLS -> KeyboardInputMode.HIRAGANA
                    }
                    updateKeyboardLayout()

                    val inputMode = when (customKeyboardMode) {
                        KeyboardInputMode.HIRAGANA -> InputMode.ModeJapanese
                        KeyboardInputMode.ENGLISH -> InputMode.ModeEnglish
                        KeyboardInputMode.SYMBOLS -> InputMode.ModeJapanese
                    }
                    floatingDockView.setText("あ")
                    setCurrentInputModeForSession(inputMode)
                    showFloatingModeSwitchView("あ")
                    return true
                }
            }
            when (keyCode) {
                KeyEvent.KEYCODE_ZENKAKU_HANKAKU -> return toggleJapaneseEnglishMode(mainView)
                KeyEvent.KEYCODE_HENKAN -> {
                    // 変換キーで日本語モードへ
                    return switchToHiraganaMode(mainView)
                }

                KeyEvent.KEYCODE_KATAKANA_HIRAGANA,
                KeyEvent.KEYCODE_KANA -> return switchToHiraganaMode(mainView)

                KeyEvent.KEYCODE_MUHENKAN,
                KeyEvent.KEYCODE_EISU -> return switchToEnglishModeFloating(mainView)

                else -> return super.onKeyDown(keyCode, event)
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    /**
     * 数字入力モード時のキーダウン処理
     */
    private fun handleNumberKeyDown(
        keyCode: Int, event: KeyEvent?, mainView: MainLayoutBinding // mainViewの実際の型に置き換えてください
    ): Boolean {
        event?.let { e ->
            if (e.isCtrlPressed) {
                if (keyCode == KeyEvent.KEYCODE_SPACE) {
                    // 数字モード時のCtrl+Spaceは日本語モードへ (日本語モードのロジックと同じ)
                    return cycleInputMode(mainView)
                }
            }
            when (keyCode) {
                KeyEvent.KEYCODE_ZENKAKU_HANKAKU -> return toggleJapaneseEnglishMode(mainView)
                KeyEvent.KEYCODE_HENKAN -> {
                    // 変換キーで日本語モードへ
                    return switchToHiraganaMode(mainView)
                }

                KeyEvent.KEYCODE_KATAKANA_HIRAGANA,
                KeyEvent.KEYCODE_KANA -> return switchToHiraganaMode(mainView)

                KeyEvent.KEYCODE_MUHENKAN,
                KeyEvent.KEYCODE_EISU -> return switchToEnglishModeFloating(mainView)

                else -> return super.onKeyDown(keyCode, event)
            }
        }
        return super.onKeyDown(keyCode, event)
    }


// ---------------------------------------------------------------------------------
// 2. 日本語入力のヘルパー関数
// ---------------------------------------------------------------------------------

    private fun handleBunsetsuPhysicalNavigation(keyCode: Int, event: KeyEvent): Boolean? {
        if (!isBunsetsuCursorMoveSessionActive()) return null
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                if (event.isShiftPressed) {
                    switchBunsetsuSplitPattern(delta = -1)
                } else {
                    moveFocusedBunsetsuSegment(delta = -1)
                }
            }

            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (event.isShiftPressed) {
                    switchBunsetsuSplitPattern(delta = 1)
                } else {
                    moveFocusedBunsetsuSegment(delta = 1)
                }
            }

            else -> null
        }
    }

    private fun currentPhysicalShortcutContext(): PhysicalKeyboardShortcutContext {
        return when {
            isBunsetsuCursorMoveSessionActive() -> PhysicalKeyboardShortcutContext.BUNSETSU_CONVERSION
            isHenkan.get() -> PhysicalKeyboardShortcutContext.CONVERSION
            inputString.value.isNotEmpty() -> PhysicalKeyboardShortcutContext.COMPOSITION
            else -> PhysicalKeyboardShortcutContext.ANY
        }
    }

    private fun isFunctionKeyConversionKeyCode(keyCode: Int): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_F6,
            KeyEvent.KEYCODE_F7,
            KeyEvent.KEYCODE_F8,
            KeyEvent.KEYCODE_F9,
            KeyEvent.KEYCODE_F10 -> true

            else -> false
        }
    }

    private fun clearFunctionKeyConversionSource() {
        functionKeyConversionSource = null
    }

    private fun getFunctionKeyConversionSource(insertString: String): String {
        val currentSource = functionKeyConversionSource
        if (currentSource != null) {
            return currentSource
        }
        functionKeyConversionSource = insertString
        return insertString
    }

    private fun handlePhysicalKeyboardShortcut(
        keyCode: Int,
        event: KeyEvent,
        mainView: MainLayoutBinding,
        insertString: String,
        suggestions: List<CandidateItem>
    ): Boolean {
        val shortcut = PhysicalShortcutMatcher.match(
            shortcuts = physicalKeyboardShortcuts,
            currentContext = currentPhysicalShortcutContext(),
            keyCode = keyCode,
            event = event
        ) ?: physicalKeyboardShortcuts.firstOrNull {
            it.enabled &&
                    it.actionId == PhysicalKeyboardShortcutAction.CYCLE_INPUT_MODE.id &&
                    it.keyCode == keyCode &&
                    it.ctrl == event.isCtrlPressed &&
                    it.shift == event.isShiftPressed &&
                    it.alt == event.isAltPressed &&
                    it.meta == event.isMetaPressed &&
                    (it.scanCode == null || it.scanCode == event.scanCode)
        } ?: return false
        val action = PhysicalKeyboardShortcutAction.fromId(shortcut.actionId) ?: return false
        return executePhysicalKeyboardShortcutAction(action, mainView, insertString, suggestions)
    }

    private fun executePhysicalKeyboardShortcutAction(
        action: PhysicalKeyboardShortcutAction,
        mainView: MainLayoutBinding,
        insertString: String,
        suggestions: List<CandidateItem>
    ): Boolean {
        return when (action) {
            PhysicalKeyboardShortcutAction.COPY -> {
                copyAction()
                true
            }

            PhysicalKeyboardShortcutAction.PASTE -> {
                pasteAction()
                true
            }

            PhysicalKeyboardShortcutAction.CUT -> {
                cutAction()
                true
            }

            PhysicalKeyboardShortcutAction.SELECT_ALL -> {
                selectAllText()
                true
            }

            PhysicalKeyboardShortcutAction.SWITCH_TO_JAPANESE -> switchToJapaneseFromPhysicalKey(
                mainView,
                insertString,
                suggestions
            )

            PhysicalKeyboardShortcutAction.SWITCH_TO_ENGLISH -> switchToEnglishModeFloating(mainView)
            PhysicalKeyboardShortcutAction.CYCLE_INPUT_MODE -> toggleJapaneseEnglishMode(mainView)
            PhysicalKeyboardShortcutAction.CONVERT -> handleJapaneseSpaceFloating(
                mainView,
                insertString,
                suggestions
            )

            PhysicalKeyboardShortcutAction.CONVERT_NEXT -> {
                if (cycleFocusedBunsetsuCandidate(delta = 1)) true else {
                    floatingCandidateNextItem(insertString)
                    true
                }
            }

            PhysicalKeyboardShortcutAction.CONVERT_PREV -> {
                if (cycleFocusedBunsetsuCandidate(delta = -1)) true else {
                    floatingCandidatePreviousItem(insertString)
                    true
                }
            }

            PhysicalKeyboardShortcutAction.COMMIT -> {
                if (isBunsetsuCursorMoveSessionActive()) {
                    commitBunsetsuConversionSession()
                } else {
                    handleJapaneseEnterFloating(mainView, insertString, suggestions)
                }
                true
            }

            PhysicalKeyboardShortcutAction.CANCEL -> {
                if (isBunsetsuCursorMoveSessionActive()) {
                    restoreRawInputFromBunsetsuSession()
                } else if (isHenkan.get()) {
                    cancelFloatingCandidateConversion(insertString)
                }
                true
            }

            PhysicalKeyboardShortcutAction.SEGMENT_FOCUS_LEFT -> moveFocusedBunsetsuSegment(delta = -1)
            PhysicalKeyboardShortcutAction.SEGMENT_FOCUS_RIGHT -> moveFocusedBunsetsuSegment(delta = 1)
            PhysicalKeyboardShortcutAction.SEGMENT_WIDTH_SHRINK -> switchBunsetsuSplitPattern(delta = -1)
            PhysicalKeyboardShortcutAction.SEGMENT_WIDTH_EXPAND -> switchBunsetsuSplitPattern(delta = 1)
            PhysicalKeyboardShortcutAction.CONVERT_TO_HIRAGANA -> handleConversionKeyFloating(
                KeyEvent.KEYCODE_F6,
                insertString
            )

            PhysicalKeyboardShortcutAction.CONVERT_TO_FULL_KATAKANA -> handleConversionKeyFloating(
                KeyEvent.KEYCODE_F7,
                insertString
            )

            PhysicalKeyboardShortcutAction.CONVERT_TO_HALF_WIDTH -> handleConversionKeyFloating(
                KeyEvent.KEYCODE_F8,
                insertString
            )

            PhysicalKeyboardShortcutAction.CONVERT_TO_FULL_ALPHANUMERIC -> handleConversionKeyFloating(
                KeyEvent.KEYCODE_F9,
                insertString
            )

            PhysicalKeyboardShortcutAction.CONVERT_TO_HALF_ALPHANUMERIC -> handleConversionKeyFloating(
                KeyEvent.KEYCODE_F10,
                insertString
            )
        }
    }

    private fun handleJapaneseShiftPressed(
        keyCode: Int, event: KeyEvent, insertString: String
    ): Boolean {
        if (hasPhysicalTextShortcutModifier(event)) return super.onKeyDown(keyCode, event)
        if (event.isShiftPressed && isBunsetsuCursorMoveSessionActive()) {
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT -> return switchBunsetsuSplitPattern(delta = -1)
                KeyEvent.KEYCODE_DPAD_RIGHT -> return switchBunsetsuSplitPattern(delta = 1)
            }
        }
        hardKeyboardShiftPressd = true
        val unicode = event.getUnicodeChar(event.metaState)
        if (unicode != 0) {
            val text = unicode.toChar().toString()
            val sb = StringBuilder()
            if (insertString.isNotEmpty()) {
                sb.append(insertString).append(text)
                _inputString.update { sb.toString() }
            } else {
                _inputString.update { text }
            }
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun handleJapaneseCtrlPressed(
        keyCode: Int, event: KeyEvent, mainView: MainLayoutBinding, // mainViewの実際の型に置き換えてください
        insertString: String
    ): Boolean {
        if (keyCode == KeyEvent.KEYCODE_SPACE) {
            return cycleInputMode(mainView)
        }
        if (insertString.isNotEmpty()) return true
        return super.onKeyDown(keyCode, event)
    }

    /**
     * F6-F10の文字種変換処理
     */
    private fun handleConversionKeyFloating(keyCode: Int, insertString: String): Boolean {
        if (insertString.isEmpty()) {
            clearFunctionKeyConversionSource()
            return true // 元のロジックでは何もせず true を返していた
        }

        val sourceString = getFunctionKeyConversionSource(insertString)

        Timber.d("onKeyDown: F-Key $keyCode Pressed $sourceString")

        val resultString = when (keyCode) {
            KeyEvent.KEYCODE_F6, KeyEvent.KEYCODE_F7, KeyEvent.KEYCODE_F8 -> {
                if (sourceString.isAllEnglishLetters()) {
                    romajiConverter?.let { converter ->
                        val romajiResult = if (isDefaultRomajiHenkanMap) {
                            converter.convertCustomLayout(sourceString.lowercase())
                        } else {
                            converter.convert(sourceString.lowercase())
                        }
                        when (keyCode) {
                            KeyEvent.KEYCODE_F6 -> romajiResult.toHiragana()
                            KeyEvent.KEYCODE_F7 -> romajiResult.toZenkakuKatakana()
                            KeyEvent.KEYCODE_F8 -> romajiResult.toHankakuKatakana()
                            else -> sourceString // ありえない
                        }
                    } ?: sourceString
                } else {
                    when (keyCode) {
                        KeyEvent.KEYCODE_F6 -> sourceString.toHiragana()
                        KeyEvent.KEYCODE_F7 -> sourceString.toZenkakuKatakana()
                        KeyEvent.KEYCODE_F8 -> sourceString.toHankakuKatakana()
                        else -> sourceString // ありえない
                    }
                }
            }

            KeyEvent.KEYCODE_F9 -> {
                if (sourceString.isAllEnglishLetters()) {
                    sourceString.lowercase().toZenkakuAlphabet()
                } else {
                    romajiConverter?.hiraganaToRomaji(sourceString.toHiragana())
                        ?.toZenkakuAlphabet() ?: sourceString
                }
            }

            KeyEvent.KEYCODE_F10 -> {
                if (sourceString.isAllEnglishLetters()) {
                    sourceString.lowercase().toHankakuAlphabet()
                } else {
                    romajiConverter?.hiraganaToRomaji(sourceString.toHiragana())
                        ?.toHankakuAlphabet() ?: sourceString
                }
            }

            else -> sourceString // 来ないはず
        }

        _inputString.update { resultString }
        return true
    }

    private fun handleJapaneseDeleteFloating(
        keyCode: Int, event: KeyEvent?, insertString: String
    ): Boolean {
        if (insertString.isEmpty()) {
            return super.onKeyDown(keyCode, event)
        }
        if (isBunsetsuCursorMoveSessionActive()) {
            restoreRawInputFromBunsetsuSession()
            listAdapter.updateHighlightPosition(RecyclerView.NO_POSITION)
            currentHighlightIndex = RecyclerView.NO_POSITION
            return true
        }

        deleteStringCommon(insertString)
        resetFlagsDeleteKey()
        event?.let { e ->
            romajiConverter?.handleDelete(e)
        }
        if (cachedPreferences?.zenzaiEnableStatePreference == true &&
            shouldUseQwertyRoman2KanaComposing()
        ) {
            scope.launch {
                val remaining = inputString.value
                if (remaining.isNotEmpty()) {
                    requestExperimentalTypoCorrectionAfterBackspace(remaining)
                }
            }
        }
        return true
    }

    private suspend fun requestExperimentalTypoCorrectionAfterBackspace(insertString: String) {
        val composingText = composingTextForCandidateRequest(insertString)
        val inputStyle = composingText.input.lastOrNull()?.inputStyle
            ?: com.kazumaproject.markdownhelperkeyboard.converter.api.InputStyle.Direct
        if (inputStyle != com.kazumaproject.markdownhelperkeyboard.converter.api.InputStyle.Roman2Kana) {
            return
        }
        val preferences = buildImeCandidatePreferences()
        val typoCandidates = suggestionOrchestrator.requestExperimentalTypoCorrection(
            composingText = composingText,
            preferences = preferences,
            inputStyle = inputStyle,
            roman2Kana = azooKeyRoman2KanaTransducer,
        )
        val best = typoCandidates.firstOrNull() ?: return
        if (best.correctedInput.isBlank() || best.correctedInput == insertString) return
        _inputString.update { best.correctedInput }
        syncComposingTextSession(best.correctedInput)
        val mainView = mainLayoutBinding ?: return
        setSuggestionOnView(best.correctedInput, mainView)
    }

    private fun cancelFloatingCandidateConversion(insertString: String) {
        preservePreEditOnNextSelectionUpdate = insertString
        isHenkan.set(false)
        henkanPressedWithBunsetsuDetect = false
        stringInTail.set("")
        currentHighlightIndex = RecyclerView.NO_POSITION
        suggestionClickNum = 0
        isFirstClickHasStringTail = false
        listAdapter.updateHighlightPosition(RecyclerView.NO_POSITION)
        suggestionAdapter?.updateHighlightPosition(RecyclerView.NO_POSITION)
        suggestionAdapter?.suggestions = emptyList()
        suggestionAdapterFull?.suggestions = emptyList()
        updateSuggestionsForFloatingCandidate(emptyList())
        val spannableString = SpannableString(insertString)
        setComposingTextAfterEdit(
            inputString = insertString,
            spannableString = spannableString,
            backgroundColor = if (customComposingTextPreference == true) {
                inputCompositionAfterBackgroundColor
                    ?: getColor(com.kazumaproject.core.R.color.blue)
            } else {
                getColor(com.kazumaproject.core.R.color.blue)
            },
            textColor = if (customComposingTextPreference == true) {
                inputCompositionTextColor
            } else {
                null
            }
        )
    }

    private fun handleJapaneseSpaceFloating(
        mainView: MainLayoutBinding, // mainViewの実際の型に置き換えてください
        insertString: String, suggestions: List<CandidateItem> // suggestionsの実際の型に置き換えてください
    ): Boolean {
        if (cycleFocusedBunsetsuCandidate(delta = 1)) {
            return true
        }

        when (currentInputModeForSession) {
            InputMode.ModeJapanese -> {
                if (insertString.isNotEmpty()) {
                    val normalizedInsertString = if (isDefaultRomajiHenkanMap) {
                        romajiConverter?.flushZenkaku(insertString)?.first
                    } else {
                        romajiConverter?.flush(insertString)?.first
                    } ?: insertString

                    isHenkan.set(true)
                    Timber.d("KEYCODE_SPACE is pressed: $normalizedInsertString $stringInTail")
                    _inputString.update { normalizedInsertString }

                    if (shouldUseBunsetsuCursorMoveSession()) {
                        scope.launch {
                            val activated = activateBunsetsuConversionSession(
                                input = normalizedInsertString,
                                mainView = mainView
                            )
                            if (!activated) {
                                floatingCandidateNextItem(normalizedInsertString)
                            }
                        }
                    } else {
                        floatingCandidateNextItem(normalizedInsertString)
                    }
                } else {
                    if (stringInTail.get().isNotEmpty()) return true
                    val isFlick = hankakuPreference ?: false
                    setSpaceKeyActionEnglishAndNumberEmpty(isFlick)
                }
            }

            else -> {
                handleSpaceKeyClick(false, insertString, suggestions.map {
                    Candidate(
                        string = it.word,
                        type = (1).toByte(),
                        length = insertString.length.toUByte(),
                        score = 0
                    )
                }, mainView)
            }
        }
        return true
    }

    private fun handleJapaneseDpadLeft(insertString: String): Boolean {
        if (moveFocusedBunsetsuSegment(delta = -1)) {
            return true
        }
        if (isHenkan.get()) {
            floatingCandidatePreviousItem(insertString)
            return true
        } else {
            handleLeftKeyPress(
                GestureType.Tap, insertString
            )
            romajiConverter?.clear()
        }
        return true
    }

    private fun handleJapaneseDpadRight(insertString: String): Boolean {
        if (moveFocusedBunsetsuSegment(delta = 1)) {
            return true
        }
        if (isHenkan.get()) {
            Timber.d("KEYCODE_DPAD_RIGHT: called")
            floatingCandidateNextItem(insertString)
            return true
        } else {
            actionInRightKeyPressed(
                GestureType.Tap, insertString
            )
            romajiConverter?.clear()
        }
        return true
    }

    private fun handleJapaneseDpadUp(
        mainView: MainLayoutBinding, // mainViewの実際の型に置き換えてください
        insertString: String, suggestions: List<CandidateItem> // suggestionsの実際の型に置き換えてください
    ): Boolean {
        if (cycleFocusedBunsetsuCandidate(delta = -1)) {
            return true
        }
        if (insertString.isNotEmpty()) {
            if (isHenkan.get()) {
                floatingCandidatePreviousItem(insertString)
                return true
            } else {
                // 非変換時はSpaceキーと同一のロジック
                return handleJapaneseSpaceFloating(mainView, insertString, suggestions)
            }
        }
        // insertStringが空の場合、元のロジックではフォールスルーしていた
        return super.onKeyDown(KeyEvent.KEYCODE_DPAD_UP, null)
    }

    private fun handleJapaneseDpadDown(
        mainView: MainLayoutBinding, // mainViewの実際の型に置き換えてください
        insertString: String, suggestions: List<CandidateItem> // suggestionsの実際の型に置き換えてください
    ): Boolean {
        if (cycleFocusedBunsetsuCandidate(delta = 1)) {
            return true
        }
        if (insertString.isNotEmpty()) {
            if (isHenkan.get()) {
                floatingCandidateNextItem(insertString)
                return true
            } else {
                // 非変換時はSpaceキーと同一のロジック
                return handleJapaneseSpaceFloating(mainView, insertString, suggestions)
            }
        }
        // insertStringが空の場合、元のロジックではフォールスルーしていた
        return super.onKeyDown(KeyEvent.KEYCODE_DPAD_DOWN, null)
    }

    private fun handleJapaneseEnterFloating(
        mainView: MainLayoutBinding, insertString: String, suggestions: List<CandidateItem>
    ): Boolean {
        if (commitBunsetsuConversionSession()) {
            romajiConverter?.clear()
            return true
        }
        if (insertString.isNotEmpty()) {
            if (isHenkan.get()) {
                floatingCandidateEnterPressed()
                romajiConverter?.clear()
                return true
            } else {
                handleNonEmptyInputEnterKey(suggestions.map {
                    Candidate(
                        string = it.word,
                        type = (1).toByte(),
                        length = insertString.length.toUByte(),
                        score = 0
                    )
                }, mainView, insertString)
            }
        } else {
            handleEmptyInputEnterKey(mainView)
        }
        romajiConverter?.clear()
        return true
    }

    private fun handleJapaneseCharacterKeyFloating(
        keyCode: Int, event: KeyEvent?, insertString: String
    ): Boolean {
        event?.let { e ->
            if (isDevicePhysicalKeyboard(e.device)) {
                if (physicalKeyboardEnable.replayCache.firstOrNull() != true) {
                    hardwareKeyboardCoordinator.schedulePhysicalKeyboardEnableEmit(
                        enabled = true,
                        scope = scope,
                        emit = { _physicalKeyboardEnable.emit(it) },
                    )
                }
                isKeyboardFloatingMode = false
            }
            val sb = StringBuilder() // ここで宣言

            if (isBunsetsuCursorMoveSessionActive()) {
                clearBunsetsuConversionSession()
                isHenkan.set(false)
                henkanPressedWithBunsetsuDetect = false
                suggestionAdapter?.updateHighlightPosition(RecyclerView.NO_POSITION)
                if (physicalKeyboardEnable.replayCache.isNotEmpty() &&
                    physicalKeyboardEnable.replayCache.first()
                ) {
                    updateSuggestionsForFloatingCandidate(emptyList())
                    currentHighlightIndex = RecyclerView.NO_POSITION
                }
            }

            if (hasPhysicalTextShortcutModifier(e)) {
                return super.onKeyDown(keyCode, e)
            }

            if (isHenkan.get()) {
                listAdapter.selectHighlightedItem()
                if (physicalKeyboardInputMode == PhysicalKeyboardInputMode.KANA) {
                    PhysicalKanaMapper.resolve(
                        keyCode = keyCode,
                        isShift = e.isShiftPressed
                    )?.let { kana ->
                        _inputString.update { KanaDakutenComposer.append("", kana) }
                    }
                } else {
                    handlePhysicalRomajiOrUnicodeKey(keyCode, e)?.let { romajiResult ->
                        Timber.d("KeyEvent Key Henkan: $e\n$insertString\n${romajiResult.first}")
                        _inputString.update {
                            romajiResult.first
                        }
                    }
                }
                return true
            }
            if (physicalKeyboardInputMode == PhysicalKeyboardInputMode.KANA) {
                val kana = PhysicalKanaMapper.resolve(
                    keyCode = keyCode,
                    isShift = e.isShiftPressed
                )
                kana?.let {
                    _inputString.update { current ->
                        KanaDakutenComposer.append(current, it)
                    }
                    return true
                }
                return super.onKeyDown(keyCode, e)
            }

            val letterConverted = handlePhysicalRomajiOrUnicodeKey(keyCode, e)
                ?: return super.onKeyDown(keyCode, e)
            Timber.d("onKeyDown: $letterConverted")
            lastQwertyRomajiRawInput = null
            if (insertString.isNotEmpty()) {
                sb.append(
                    insertString.dropLast((letterConverted.second))
                ).append(letterConverted.first)
                _inputString.update {
                    sb.toString()
                }
            } else {
                _inputString.update {
                    letterConverted.first
                }
            }
            return true
        }
        return super.onKeyDown(keyCode, null)
    }

    private fun hasPhysicalTextShortcutModifier(event: KeyEvent): Boolean {
        return event.isCtrlPressed || event.isAltPressed || event.isMetaPressed
    }

    private fun handlePhysicalRomajiOrUnicodeKey(
        keyCode: Int,
        event: KeyEvent
    ): Pair<String, Int>? {
        val unicode = event.getUnicodeChar(event.metaState)
        if (unicode == 0) return null

        val isEnglish = currentInputModeForSession == InputMode.ModeEnglish
        return if (keyCode in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z) {
            if (isEnglish) {
                romajiConverter?.handleKeyEvent(event)
            } else if (isDefaultRomajiHenkanMap) {
                romajiConverter?.handleKeyEventZenkaku(event)
            } else {
                romajiConverter?.handleKeyEvent(event)
            }
        } else {
            if (isEnglish) {
                romajiConverter?.handleUnicodeChar(unicode)
            } else if (isDefaultRomajiHenkanMap) {
                val mappedChar = unicode.toChar().toRomajiQwertyOutputChar()
                romajiConverter?.handleUnicodeChar(mappedChar.code)
            } else {
                romajiConverter?.handleUnicodeChar(unicode)
            }
        }
    }


// ---------------------------------------------------------------------------------
// 3. 共通ヘルパー関数（モード切替など）
// ---------------------------------------------------------------------------------

    private fun logPhysicalKeyEventForDebug(keyCode: Int, event: KeyEvent) {
        if (!BuildConfig.DEBUG) return
        Timber.d(
            "PhysicalKeyEvent keyCode=%d keyName=%s scanCode=%d unicodeChar=%d metaState=%d shift=%b alt=%b ctrl=%b meta=%b inputMode=%s",
            keyCode,
            KeyEvent.keyCodeToString(keyCode),
            event.scanCode,
            event.unicodeChar,
            event.metaState,
            event.isShiftPressed,
            event.isAltPressed,
            event.isCtrlPressed,
            event.isMetaPressed,
            physicalKeyboardInputMode
        )
    }

    private fun switchToJapaneseFromPhysicalKey(
        mainView: MainLayoutBinding,
        insertString: String,
        suggestions: List<CandidateItem>
    ): Boolean {
        if (currentInputModeForSession == InputMode.ModeJapanese && insertString.isNotEmpty()) {
            return handleJapaneseSpaceFloating(mainView, insertString, suggestions)
        }
        return switchToHiraganaMode(mainView)
    }

    /**
     * Ctrl+Space押下時の入力モードサイクル（日→英→数→日）
     */
    private fun cycleInputMode(mainView: MainLayoutBinding): Boolean {
        customKeyboardMode = when (customKeyboardMode) {
            KeyboardInputMode.HIRAGANA -> KeyboardInputMode.ENGLISH
            KeyboardInputMode.ENGLISH -> KeyboardInputMode.SYMBOLS
            KeyboardInputMode.SYMBOLS -> KeyboardInputMode.HIRAGANA
        }
        updateKeyboardLayout()

        val inputMode = when (customKeyboardMode) {
            KeyboardInputMode.HIRAGANA -> InputMode.ModeJapanese
            KeyboardInputMode.ENGLISH -> InputMode.ModeEnglish
            KeyboardInputMode.SYMBOLS -> InputMode.ModeNumber
        }
        val showInputModeText = when (inputMode) {
            InputMode.ModeJapanese -> "あ"
            InputMode.ModeEnglish -> "A"
            InputMode.ModeNumber -> "A"
        }
        Timber.d("e.isCtrlPressed Space: $inputMode $showInputModeText")
        floatingDockView.setText(showInputModeText)
        setCurrentInputModeForSession(inputMode)

        showFloatingModeSwitchView(showInputModeText)
        finishComposingText()
        _inputString.update { "" }
        return true
    }

    /**
     * ひらがなモード（日本語入力）へ切り替える
     */
    private fun switchToHiraganaMode(mainView: MainLayoutBinding): Boolean {
        customKeyboardMode = KeyboardInputMode.HIRAGANA
        updateKeyboardLayout()
        val inputMode = InputMode.ModeJapanese
        val showInputModeText = "あ"
        Timber.d("switchToHiraganaMode: $inputMode $showInputModeText")
        floatingDockView.setText(showInputModeText)
        setCurrentInputModeForSession(inputMode)
        showFloatingModeSwitchView(showInputModeText)
        finishComposingText()
        _inputString.update { "" }
        return true
    }

    private fun toggleJapaneseEnglishMode(mainView: MainLayoutBinding): Boolean {
        return when (currentInputModeForSession) {
            InputMode.ModeEnglish -> switchToHiraganaMode(mainView)
            else -> switchToEnglishModeFloating(mainView)
        }
    }

    /**
     * 英語モードへ切り替える (無変換キー用)
     */
    private fun switchToEnglishModeFloating(mainView: MainLayoutBinding): Boolean {
        customKeyboardMode = KeyboardInputMode.ENGLISH
        updateKeyboardLayout()
        val inputMode = InputMode.ModeEnglish
        val showInputModeText = "A"
        Timber.d("switchToEnglishMode (MUHENKAN): $inputMode $showInputModeText")
        floatingDockView.setText(showInputModeText)
        setCurrentInputModeForSession(inputMode)
        showFloatingModeSwitchView(showInputModeText)
        finishComposingText()
        _inputString.update { "" }
        return true
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_ENTER -> {
                Timber.d("onKeyUp KEYCODE_ENTER: ${inputString.value} ${isHenkan.get()}")
                if (isHenkan.get()) {
                    if (inputString.value.isNotEmpty()) {
                        return true
                    }
                    isHenkan.set(false)
                    henkanPressedWithBunsetsuDetect = false
                }
                return super.onKeyUp(keyCode, event)
            }
        }
        return super.onKeyUp(keyCode, event)
    }

    private fun showFloatingModeSwitchView(showInputModeText: String) {
        // 以前のdismiss処理がスケジュールされていればキャンセルする
        dismissJob?.cancel()

        floatingModeSwitchView.text = showInputModeText
        floatingModeSwitchWindow?.dismiss()
        val modeSwitchPopupWindow = floatingModeSwitchWindow
        modeSwitchPopupWindow?.let { switchWindow ->
            switchWindow.isTouchable = false
            if (switchWindow.isShowing) {
                switchWindow.update(
                    physicalKeyboardFloatingXPosition, physicalKeyboardFloatingYPosition, -1, -1
                )
            } else {
                showPopupWindowSafely(
                    popupWindow = switchWindow,
                    anchorView = window.window?.decorView,
                    gravity = Gravity.NO_GRAVITY,
                    x = physicalKeyboardFloatingXPosition,
                    y = physicalKeyboardFloatingYPosition,
                    source = "showFloatingModeSwitchView"
                )
            }
            // 新しいコルーチンを開始し、そのJobを保存する
            dismissJob = scope.launch {
                delay(1500)
                switchWindow.dismiss()
            }
        }
    }

    private fun floatingCandidateNextItem(insertString: String) {
        Timber.d("floatingCandidateNextItem called. Current highlight: $currentHighlightIndex ${stringInTail.get()}")
        if (listAdapter.currentList.isEmpty()) return

        val suggestionCount = listAdapter.currentList.size.coerceAtMost(PAGE_SIZE)
        if (suggestionCount == 0) return

        // 次のページが存在するかどうかを確認
        val maxPage = (fullSuggestionsList.size - 1) / PAGE_SIZE
        val hasNextPage = currentPage < maxPage

        // ハイライトがページの最後尾 かつ 次のページが存在する場合
        if (currentHighlightIndex == suggestionCount - 1 && hasNextPage) {
            goToNextPageForFloatingCandidate() // 次のページに移動
            scope.launch {
                delay(64)
                Timber.d("floatingCandidateNextItem hasNextPage: ${listAdapter.getHighlightedItem()}")
                displayComposingTextInHardwareKeyboardConnected(insertString)
            }
        } else if (currentHighlightIndex == suggestionCount - 1 && !hasNextPage) {
            currentPage = -1
            scope.launch {
                delay(64)
                Timber.d("floatingCandidateNextItem hasNextPage: ${listAdapter.getHighlightedItem()}")
                displayComposingTextInHardwareKeyboardConnected(insertString)
            }
        } else {
            // 上記以外の場合は、現在のページ内でハイライトをループさせる
            currentHighlightIndex = if (currentHighlightIndex == RecyclerView.NO_POSITION) {
                0
            } else {
                (currentHighlightIndex + 1) % suggestionCount
            }
            listAdapter.updateHighlightPosition(currentHighlightIndex)
            displayComposingTextInHardwareKeyboardConnected(insertString)
            Timber.d("floatingCandidateNextItem: ${listAdapter.getHighlightedItem()} ${inputString.value} $stringInTail")
        }
    }

    private fun floatingCandidatePreviousItem(insertString: String) {
        if (listAdapter.currentList.isEmpty()) return
        val suggestionCount = listAdapter.currentList.size.coerceAtMost(PAGE_SIZE)
        if (suggestionCount == 0) return
        val hasPreviousPage = currentPage > 0

        if (currentHighlightIndex <= 0 && hasPreviousPage) {
            goToPreviousPageForFloatingCandidate()
            scope.launch {
                delay(64)
                displayComposingTextInHardwareKeyboardConnected(insertString = insertString)
                Timber.d("floatingCandidatePreviousItem hasPreviousPage: ${listAdapter.getHighlightedItem()}")
            }
        } else {
            currentHighlightIndex = if (currentHighlightIndex <= 0) {
                suggestionCount - 1
            } else {
                currentHighlightIndex - 1
            }
            listAdapter.updateHighlightPosition(currentHighlightIndex)
            displayComposingTextInHardwareKeyboardConnected(insertString = insertString)
            Timber.d("floatingCandidatePreviousItem: ${listAdapter.getHighlightedItem()}")
        }
    }

    private fun displayComposingTextInHardwareKeyboardConnected(
        insertString: String
    ) {
        val selectedSuggestion = listAdapter.currentList.getOrNull(currentHighlightIndex) ?: return
        val tail = FloatingCandidateTailResolver.resolveTail(
            originalInput = insertString,
            selectedCandidateLength = selectedSuggestion.length.toInt()
        )
        stringInTail.set(tail)
        Timber.d("displayComposingTextInHardwareKeyboardConnected: ${selectedSuggestion.word} ${selectedSuggestion.length} $insertString $tail ${insertString.length} ${selectedSuggestion.length.toInt()}")
        val spannableString = SpannableString(selectedSuggestion.word + tail)
        setComposingTextAfterEdit(
            inputString = selectedSuggestion.word,
            spannableString = spannableString,
            backgroundColor = if (customComposingTextPreference == true) {
                inputCompositionAfterBackgroundColor ?: getColor(
                    com.kazumaproject.core.R.color.blue
                )
            } else {
                getColor(
                    com.kazumaproject.core.R.color.blue
                )
            },
            textColor = if (customComposingTextPreference == true) {
                inputCompositionTextColor
            } else {
                null
            }
        )
    }

    private fun floatingCandidateEnterPressed() {
        val selectedSuggestion = listAdapter.getHighlightedItem()
        if (selectedSuggestion != null) {
            val subString = stringInTail.get()
            if (subString.isNotEmpty()) {
                commitText(selectedSuggestion.word, 1)
                updateSuggestionsForFloatingCandidate(emptyList())
                _inputString.update { subString }
                listAdapter.updateHighlightPosition(-1)
                currentHighlightIndex = -1
                scope.launch {
                    delay(64)
                    floatingCandidateNextItem(insertString = subString)
                }
            } else {
                commitText(selectedSuggestion.word, 1)
                updateSuggestionsForFloatingCandidate(emptyList())
            }
        }
    }

    /**
     * フローティングモードの状態に応じて、キーボードの表示/非表示やレイアウトを適用します。
     * @param isFloatingMode フローティングモードが有効かどうかのフラグ
     */
    private fun applyFloatingModeState(isFloatingMode: Boolean) {
        val mainView = mainLayoutBinding ?: return
        if (physicalKeyboardEnable.replayCache.isNotEmpty() && physicalKeyboardEnable.replayCache.first()) {
            floatingKeyboardView?.dismiss()
            releaseFloatingKeyboardBackgroundVideoPlayer()
            // 物理キーボード接続中は Floating UI を出さないため、isKeyboardFloatingMode は
            // 必ず false として扱う。これを忘れると、Floating ON 状態で物理キーボードが
            // 接続されたまま applyFloatingModeState が呼ばれた場合に isKeyboardFloatingMode が
            // 古い値 (true) のまま残り、getActiveKeyboardSurface() が見えていない floating 側を
            // 返してしまう。
            this.isKeyboardFloatingMode = false
            return
        }
        this.isKeyboardFloatingMode = isFloatingMode
        if (isFloatingMode) {
            (mainView.root.layoutParams as? FrameLayout.LayoutParams)?.let { params ->
                params.width = ViewGroup.LayoutParams.MATCH_PARENT
                params.height = getScreenHeight(this@IMEService)
                mainView.root.layoutParams = params
            }
            mainView.root.alpha = 0f
            bindSuggestionAdaptersForFloatingMode(mainView, isFloatingMode = true)
            clearNormalKeyboardBackgroundForFloatingMode(mainView)
            floatingKeyboardBinding?.let { floatingView ->
                applyFloatingKeyboardBackgroundIfNeeded(floatingView)
            }
            floatingKeyboardView?.apply {
                Timber.d("applyFloatingModeState: isFloatingMode=$isFloatingMode ${this.isShowing}")
                if (!this.isShowing) {
                    val anchorView = window.window?.decorView
                    val savedX = appPreference.keyboard_floating_position_x
                    val savedY = appPreference.keyboard_floating_position_y
                    val shown = if (savedX != -1 && savedY != -1) {
                        showPopupWindowSafely(
                            popupWindow = this,
                            anchorView = anchorView,
                            gravity = Gravity.NO_GRAVITY,
                            x = savedX,
                            y = savedY,
                            source = "applyFloatingModeState(saved)"
                        )
                    } else {
                        showPopupWindowSafely(
                            popupWindow = this,
                            anchorView = anchorView,
                            gravity = Gravity.BOTTOM or Gravity.END,
                            x = 0,
                            y = 0,
                            source = "applyFloatingModeState(default)"
                        )
                    }
                    if (!shown) {
                        Timber.w("Could not show floating keyboard, window token is not available yet.")
                    }
                }
            }
            floatingKeyboardBinding?.let { floatingView ->
                applyFloatingKeyboardBackgroundIfNeeded(floatingView)
            }
            syncFloatingKeyboardContentForMode(qwertyMode.value)
            // Floating mode ON に切り替えた直後は、通常 QWERTY が持っている
            // QWERTYMode / Shift / CapsLock / Romaji 等の現在状態を Floating 側に反映する。
            // syncFloatingKeyboardContentForMode は ensureFloatingQwertyConfigured のみを行い、
            // 内部状態のミラーはここで明示的に実施する (sync 内でミラーすると、ユーザーが
            // Floating 側で操作した内部状態を意図せず main の古い状態で上書きしてしまうため)。
            floatingKeyboardBinding?.let { floatingView ->
                if (qwertyMode.value == TenKeyQWERTYMode.TenKeyQWERTY ||
                    qwertyMode.value == TenKeyQWERTYMode.TenKeyQWERTYRomaji
                ) {
                    mirrorMainQwertyStateToFloating(mainView, floatingView)
                }
            }
            renderCurrentKeyboardStateOnActiveSurface()
            updateFloatingKeyboardSizeForMode(qwertyMode.value)
        } else {
            // Floating mode OFF に戻す際は、Floating 中にユーザーが操作した
            // QWERTY 内部状態を通常 QWERTY 側へ引き継ぐ。
            floatingKeyboardBinding?.let { floatingView ->
                if (qwertyMode.value == TenKeyQWERTYMode.TenKeyQWERTY ||
                    qwertyMode.value == TenKeyQWERTYMode.TenKeyQWERTYRomaji
                ) {
                    mirrorFloatingQwertyStateToMain(mainView, floatingView)
                }
            }
            bindSuggestionAdaptersForFloatingMode(mainView, isFloatingMode = false)
            mainView.root.isVisible = true
            mainView.root.alpha = 1f
            floatingKeyboardView?.dismiss()
            releaseFloatingKeyboardBackgroundVideoPlayer()
            setKeyboardSizeSwitchKeyboard(mainView)
            applyKeyboardBackgroundIfNeeded(mainView, skipForFloatingMode = false)
            // isKeyboardFloatingMode は既に false にセット済みなので、
            // renderCurrentKeyboardStateOnActiveSurface() は main surface に対して動作する。
            // renderKeyboardMode 単体ではなく、本関数を呼ぶことで:
            //   - main surface の visibility (TenKey/QWERTY/Custom) を再評価
            //   - mainView.keyboardView.currentInputMode を currentInputModeForSession と同期
            //     (Floating ON 中に setCurrentInputModeForSession で更新された値は floating 側
            //      にしか反映されておらず、main の TenKey が古い InputMode を保持している
            //      可能性があるため、ここで明示的に再同期する)
            //   - customLayoutDefault の dynamic key を最新状態で再描画
            //   - mainView.qwertyView の romaji / 切替キー可視状態を最新状態で再描画
            // が一括で行われ、Floating ON -> OFF 復帰時に main 側表示が古い値で残るのを防ぐ。
            renderCurrentKeyboardStateOnActiveSurface()
        }
    }

    private fun getNormalKeyboardSurface(): KeyboardSurface? {
        val mainView = mainLayoutBinding ?: return null
        return KeyboardSurface(
            rootView = mainView.root,
            keyboardView = mainView.keyboardView,
            tabletView = mainView.tabletView,
            qwertyView = mainView.qwertyView,
            customLayout = mainView.customLayoutDefault,
            suggestionRecyclerView = mainView.suggestionRecyclerView,
            symbolKeyboard = mainView.keyboardSymbolView
        )
    }

    private fun getFloatingKeyboardSurface(): KeyboardSurface? {
        val floatingView = floatingKeyboardBinding ?: return null
        return KeyboardSurface(
            rootView = floatingView.root,
            keyboardView = floatingView.keyboardViewFloating,
            tabletView = null,
            qwertyView = floatingView.qwertyViewFloating,
            customLayout = floatingView.customLayoutFloating,
            suggestionRecyclerView = floatingView.suggestionRecyclerView,
            symbolKeyboard = floatingView.floatingSymbolKeyboard
        )
    }

    private fun getActiveKeyboardSurface(): KeyboardSurface? {
        return if (isKeyboardFloatingMode == true) {
            getFloatingKeyboardSurface()
        } else {
            getNormalKeyboardSurface()
        }
    }

    private fun requireActiveKeyboardSurface(): KeyboardSurface? {
        return getActiveKeyboardSurface()
    }

    private fun hideKeyboardViews(surface: KeyboardSurface) {
        surface.keyboardView?.isVisible = false
        surface.tabletView?.isVisible = false
        surface.qwertyView?.isVisible = false
        surface.customLayout?.isVisible = false
    }

    private fun renderKeyboardMode(
        surface: KeyboardSurface,
        mode: TenKeyQWERTYMode,
        isFloating: Boolean
    ) {
        hideKeyboardViews(surface)
        if (hasHardwareKeyboardConnected == true) {
            return
        }
        when (mode) {
            TenKeyQWERTYMode.Default -> {
                if (!isFloating && isTabletGojuonSurface()) {
                    surface.tabletView?.isVisible = true
                } else {
                    surface.keyboardView?.isVisible = true
                }
            }

            TenKeyQWERTYMode.TenKeyQWERTY -> {
                surface.qwertyView?.setRomajiEnglishSwitchKeyVisibility(false)
                surface.qwertyView?.isVisible = true
            }

            TenKeyQWERTYMode.TenKeyQWERTYRomaji -> {
                surface.qwertyView?.setRomajiEnglishSwitchKeyVisibility(
                    qwertyShowSwitchRomajiEnglishPreference == true
                )
                surface.qwertyView?.isVisible = true
            }

            TenKeyQWERTYMode.Custom,
            TenKeyQWERTYMode.Sumire,
            TenKeyQWERTYMode.Number -> {
                surface.customLayout?.isVisible = true
            }
        }
    }

    private fun renderCurrentKeyboardSurface() {
        val surface = getActiveKeyboardSurface() ?: return
        renderKeyboardMode(
            surface = surface,
            mode = qwertyMode.value,
            isFloating = isKeyboardFloatingMode == true
        )
        updateQwertyGlideInputModeOnActiveSurface()
    }

    private fun updateDynamicKeyOnActiveSurface(
        keyId: String,
        stateIndex: Int
    ) {
        getActiveKeyboardSurface()
            ?.customLayout
            ?.updateDynamicKey(
                keyId = keyId,
                stateIndex = stateIndex
            )
    }

    private fun isTabletGojuonSurface(): Boolean {
        return TenkeyEnglishQwertySwitchResolver.isTabletGojuonSurface(
            isTablet = isTablet,
            tabletGojuonLayoutPreference = tabletGojuonLayoutPreference
        )
    }

    private fun isTabletTenkeySurface(): Boolean {
        return TenkeyEnglishQwertySwitchResolver.isTabletTenkeySurface(
            isTablet = isTablet,
            tabletGojuonLayoutPreference = tabletGojuonLayoutPreference
        )
    }

    private fun currentTenkeyInputMode(mainView: MainLayoutBinding): InputMode {
        return inputActionDispatcher.keyboardMode.readTenkeyInputMode(
            mainView = mainView,
            useTabletGojuonSurface = isTabletGojuonSurface(),
        )
    }

    /** IME メインスレッド専用。候補リクエスト組み立て時に一括読み取りする。 */
    private fun currentImeSessionSnapshot(): ImeSessionState = ImeSessionState.fromImeService(
        inputString = inputString.value,
        stringInTail = stringInTail.get(),
        isHenkan = isHenkan,
        hasConvertedKatakana = hasConvertedKatakana,
        sessionInputMode = currentInputModeForSession,
        selectModeActive = selectMode.value,
        cursorMoveModeActive = cursorMoveMode.value,
        suppressSuggestions = suppressSuggestions,
    )

    private fun shouldSwitchTenkeyEnglishToQwerty(): Boolean {
        return TenkeyEnglishQwertySwitchResolver.shouldSwitchEnglishToQwerty(
            isTablet = isTablet,
            tabletGojuonLayoutPreference = tabletGojuonLayoutPreference,
            tabletTenkeyQwertySwitchEnglish = tabletTenkeyQwertySwitchEnglish,
            tenkeyQwertySwitchEnglish = tenkeyQWERTYSwitchNumber == true
        )
    }

    private fun switchTenkeyEnglishToQwertyIfNeeded(
        inputMode: InputMode,
        mainView: MainLayoutBinding,
        insertString: String = inputString.value
    ): Boolean {
        if (inputMode != InputMode.ModeEnglish) return false
        if (!shouldSwitchTenkeyEnglishToQwerty()) return false

        _tenKeyQWERTYMode.update { TenKeyQWERTYMode.TenKeyQWERTY }
        inputActionDispatcher.keyboardMode.setSessionMode(InputMode.ModeEnglish)
        setCurrentQwertyRomajiModeForSession(false)
        setQwertySwitchNumberLayoutKeyVisibilityOnActiveSurface(true)
        updateQwertyOnActiveSurface {
            resetQWERTYKeyboard(currentInputType.getQWERTYReturnTextInEn())
        }
        renderCurrentKeyboardStateOnActiveSurface()
        if (insertString.isEmpty()) {
            setKeyboardSizeSwitchKeyboard(mainView)
        } else {
            setKeyboardHeightWithAdditional(mainView)
        }
        previousTenKeyQWERTYMode = TenKeyQWERTYMode.Default
        return true
    }

    private fun renderDynamicKeysOnActiveSurface() {
        val customLayout = getActiveKeyboardSurface()?.customLayout ?: return
        customLayout.updateDynamicKey(
            keyId = "enter_key",
            stateIndex = currentEnterKeyIndex
        )
        customLayout.updateDynamicKey(
            keyId = "dakuten_toggle_key",
            stateIndex = currentDakutenKeyIndex
        )
        customLayout.updateDynamicKey(
            keyId = "space_convert_key",
            stateIndex = currentSpaceKeyIndex
        )
        customLayout.updateDynamicKey(
            keyId = "katakana_toggle_key",
            stateIndex = currentKatakanaKeyIndex
        )
    }

    private fun setInputModeOnActiveSurface(inputMode: InputMode) {
        if (isKeyboardFloatingMode == true) {
            floatingKeyboardBinding?.keyboardViewFloating?.setCurrentMode(inputMode)
            return
        }
        mainLayoutBinding?.let { mainView ->
            if (isTabletGojuonSurface()) {
                mainView.tabletView.currentInputMode.set(inputMode)
                mainView.tabletView.setInputModeSwitchState()
            } else {
                mainView.keyboardView.setCurrentMode(inputMode)
            }
        }
    }

    private fun setCurrentInputModeForSession(inputMode: InputMode) {
        inputActionDispatcher.keyboardMode.setSessionMode(inputMode)
        setInputModeOnActiveSurface(inputMode)
    }

    private fun resizeTenkeySurfaceAfterQwertyNumberKey(
        mainView: MainLayoutBinding,
        insertString: String
    ) {
        if (insertString.isEmpty()) {
            setKeyboardSizeSwitchKeyboard(mainView)
        } else {
            setKeyboardHeightWithAdditional(mainView)
        }
    }

    private fun returnDefaultQwertyToTenkeyInputMode(
        inputMode: InputMode,
        mainView: MainLayoutBinding,
        insertString: String
    ) {
        _tenKeyQWERTYMode.update { TenKeyQWERTYMode.Default }
        setCurrentInputModeForSession(inputMode)
        resizeTenkeySurfaceAfterQwertyNumberKey(mainView, insertString)
    }

    private fun returnDefaultQwertyFromNumberKey(
        mainView: MainLayoutBinding,
        insertString: String
    ) {
        val targetInputMode = if (qwertySwitchNumberKeyWithoutNumberPreference == true) {
            InputMode.ModeJapanese
        } else {
            InputMode.ModeNumber
        }
        returnDefaultQwertyToTenkeyInputMode(
            inputMode = targetInputMode,
            mainView = mainView,
            insertString = insertString
        )
    }

    private fun setQwertyRomajiModeOnActiveSurface(enabled: Boolean) {
        val finalEnabled = if (currentInputModeForSession == InputMode.ModeEnglish) false else enabled
        getActiveKeyboardSurface()
            ?.qwertyView
            ?.setRomajiMode(finalEnabled)
    }

    private fun setCurrentQwertyRomajiModeForSession(enabled: Boolean) {
        currentQwertyRomajiModeForSession = enabled
        setQwertyRomajiModeOnActiveSurface(enabled)
        updateQwertyGlideInputModeOnActiveSurface()
    }

    private fun setQwertyRomajiSwitchTextOnActiveSurface(isJapanese: Boolean) {
        getActiveKeyboardSurface()
            ?.qwertyView
            ?.setRomajiEnglishSwitchKeyTextWithStyle(isJapanese)
    }

    private fun setQwertySwitchNumberLayoutKeyVisibilityOnActiveSurface(isVisible: Boolean) {
        getActiveKeyboardSurface()
            ?.qwertyView
            ?.setSwitchNumberLayoutKeyVisibility(isVisible)
    }

    private fun setQwertyKey123VisibilityOnActiveSurface(isVisible: Boolean) {
        val shouldShowKey = isVisible && !(
            (qwertyShowNumberButtonsPreference ?: false) &&
            (qwertyShowKeymapSymbolsPreference ?: false)
        )
        getActiveKeyboardSurface()
            ?.qwertyView
            ?.setKey123Visibility(shouldShowKey)
    }

    private fun renderQwertyStateOnActiveSurface() {
        val qwertyView = getActiveKeyboardSurface()?.qwertyView ?: return
        val finalRomajiMode = if (currentInputModeForSession == InputMode.ModeEnglish) false else currentQwertyRomajiModeForSession
        qwertyView.setRomajiMode(finalRomajiMode)
        qwertyView.setRomajiEnglishSwitchKeyTextWithStyle(
            currentInputModeForSession == InputMode.ModeJapanese
        )
        qwertyView.setRomajiEnglishSwitchKeyVisibility(
            qwertyMode.value == TenKeyQWERTYMode.TenKeyQWERTYRomaji &&
                    qwertyShowSwitchRomajiEnglishPreference == true
        )
        setQwertySwitchNumberLayoutKeyVisibilityOnActiveSurface(true)
        setQwertyKey123VisibilityOnActiveSurface(true)
        updateQwertyGlideInputModeOnActiveSurface()
    }

    private fun calculateQwertyGlideInputMode(): Boolean {
        val surface = getActiveKeyboardSurface()
        return QwertyGlideInputModeResolver.resolve(
            qwertyGlideInputPreference = qwertyGlideInputPreference,
            isQwertySurfaceActive = surface?.qwertyView?.isVisible == true,
            currentQwertyRomajiModeForSession = currentQwertyRomajiModeForSession
        )
    }

    private fun updateQwertyGlideInputModeOnActiveSurface() {
        getActiveKeyboardSurface()
            ?.qwertyView
            ?.setQwertyGlideInputMode(calculateQwertyGlideInputMode())
    }

    private fun updateQwertyOnActiveSurface(block: QWERTYKeyboardView.() -> Unit) {
        getActiveKeyboardSurface()
            ?.qwertyView
            ?.block()
    }

    private fun renderCurrentKeyboardStateOnActiveSurface() {
        renderCurrentKeyboardSurface()
        setInputModeOnActiveSurface(currentInputModeForSession)
        renderDynamicKeysOnActiveSurface()
        renderQwertyStateOnActiveSurface()
    }

    private fun refreshActiveSumireLayoutIfNeeded() {
        if (qwertyMode.value != TenKeyQWERTYMode.Sumire) return
        val surface = getActiveKeyboardSurface() ?: return
        val customLayout = surface.customLayout ?: return
        setSumireLayoutTo(customLayout)
        renderDynamicKeysOnActiveSurface()
    }

    private fun defaultInputModeFor(inputType: InputTypeForIME): InputMode {
        return when (inputType) {
            InputTypeForIME.TextEmailAddress,
            InputTypeForIME.TextEditTextInWebView,
            InputTypeForIME.TextPostalAddress,
            InputTypeForIME.TextWebEmailAddress,
            InputTypeForIME.TextPassword,
            InputTypeForIME.TextVisiblePassword,
            InputTypeForIME.TextWebPassword -> InputMode.ModeEnglish

            InputTypeForIME.Number,
            InputTypeForIME.NumberDecimal,
            InputTypeForIME.NumberPassword,
            InputTypeForIME.NumberSigned,
            InputTypeForIME.Phone,
            InputTypeForIME.Date,
            InputTypeForIME.Datetime,
            InputTypeForIME.Time -> InputMode.ModeNumber

            else -> InputMode.ModeJapanese
        }
    }

    private fun createSumireKeyboardLayout(): KeyboardLayout {
        val dynamicStates = mapOf(
            "enter_key" to currentEnterKeyIndex,
            "dakuten_toggle_key" to currentDakutenKeyIndex,
            "katakana_toggle_key" to currentKatakanaKeyIndex,
            "space_convert_key" to currentSpaceKeyIndex,
        )
        val layoutType = sumireInputKeyLayoutType ?: "toggle"
        return applyCircularSlotActionSettings(
            KeyboardDefaultLayouts.createFinalLayout(
                mode = customKeyboardMode,
                dynamicKeyStates = dynamicStates,
                inputLayoutType = layoutType,
                inputStyle = sumireInputStyle ?: "default",
                deleteKeyFlickSettings = currentDeleteKeyFlickSettings()
            ),
            customKeyboardMode
        ).let { baseLayout ->
            SumireSpecialKeyActionDisplayOverrideApplier.apply(
                layout = baseLayout,
                layoutType = layoutType,
                inputMode = customKeyboardMode.name,
                overrides = sumireSpecialKeyActionOverrides,
                displayMetadata = sumireSpecialKeyActionDisplayMetadata()
            )
        }.let { layout ->
            SumireSpecialKeyPlacementOverrideApplier.apply(
                layout = layout,
                layoutType = layoutType,
                inputMode = customKeyboardMode.name,
                overrides = sumireSpecialKeyPlacementOverrides
            )
        }
    }

    private fun sumireSpecialKeyActionDisplayMetadata(): List<SumireSpecialKeyActionDisplayMetadata> {
        return KeyActionMapper.getDisplayActions(this).map {
            SumireSpecialKeyActionDisplayMetadata(
                action = it.action,
                displayName = it.displayName,
                iconResId = it.iconResId
            )
        }
    }

    private fun currentDeleteKeyFlickSettings(): DeleteKeyFlickSettings {
        return DeleteKeyFlickSettings(
            left = isDeleteLeftFlickPreference ?: true,
            up = isDeleteUpFlickPreference ?: false,
            down = isDeleteDownFlickPreference ?: false
        )
    }

    private fun applyDeleteKeyFlickPreferences(layout: KeyboardLayout): KeyboardLayout {
        return KeyboardDefaultLayouts.applyDeleteKeyFlickSettings(
            layout = layout,
            deleteKeyFlickSettings = currentDeleteKeyFlickSettings()
        )
    }

    private fun setKeyboardWithDeleteKeyFlickPreferences(
        flickView: FlickKeyboardView,
        layout: KeyboardLayout
    ) {
        flickView.clearSumireSpecialKeyActionResolver()
        flickView.setKeyboard(applyDeleteKeyFlickPreferences(layout))
    }

    private fun syncCustomKeyboardToggleKeyIcons(flickView: FlickKeyboardView) {
        flickView.updateKeyIconByAction(
            KeyAction.SwitchDirectMode,
            if (isCustomLayoutDirectMode) {
                com.kazumaproject.core.R.drawable.language_japanese_kana_right_24px
            } else {
                com.kazumaproject.core.R.drawable.language_japanese_kana_left_24px
            }
        )
        flickView.updateKeyIconByAction(
            KeyAction.SwitchRomajiEnglish,
            if (isCustomLayoutRomajiMode) {
                com.kazumaproject.core.R.drawable.language_japanese_kana_left_bold_24px
            } else {
                com.kazumaproject.core.R.drawable.language_japanese_kana_right_bold_24px
            }
        )
        flickView.updateKeyIconByAction(
            KeyAction.ShiftKey,
            if (isCustomLayoutShiftPressed) {
                com.kazumaproject.core.R.drawable.shift_fill_24px
            } else {
                com.kazumaproject.core.R.drawable.shift_24px
            }
        )
        flickView.updateKeyIconByAction(
            KeyAction.CapLockKey,
            if (isCustomLayoutCapLock) {
                com.kazumaproject.core.R.drawable.caps_lock
            } else {
                com.kazumaproject.core.R.drawable.caps_lock_outline
            }
        )
    }

    private fun syncCustomKeyboardToggleKeyIconsOnAvailableSurfaces() {
        getNormalKeyboardSurface()
            ?.customLayout
            ?.let(::syncCustomKeyboardToggleKeyIcons)
        getFloatingKeyboardSurface()
            ?.customLayout
            ?.let(::syncCustomKeyboardToggleKeyIcons)
    }

    private fun setSumireLayoutTo(flickView: FlickKeyboardView) {
        val layoutType = sumireInputKeyLayoutType ?: "toggle"
        flickView.setSumireSpecialKeyActionResolver(
            resolver = SumireSpecialKeyActionResolver(sumireSpecialKeyActionOverrides)::resolve,
            layoutType = layoutType,
            inputMode = customKeyboardMode.name
        )
        flickView.setKeyboard(createSumireKeyboardLayout())
    }

    private fun setNumberLayoutTo(flickView: FlickKeyboardView) {
        val numberCustomLayout = numberUsageCustomKeyboardLayoutOrNull()
        if (numberCustomLayout != null) {
            setKeyboardWithDeleteKeyFlickPreferences(
                flickView,
                KeyboardDefaultLayouts.createNumberLayout(currentDeleteKeyFlickSettings())
            )
            setNumberCustomLayoutTo(flickView, numberCustomLayout)
            return
        }
        numberKeyboardRenderJob?.cancel()
        setKeyboardWithDeleteKeyFlickPreferences(
            flickView,
            KeyboardDefaultLayouts.createNumberLayout(currentDeleteKeyFlickSettings())
        )
    }

    private fun numberUsageCustomKeyboardLayoutOrNull(): CustomKeyboardLayout? {
        return customLayouts.firstOrNull { layout ->
            layout.usageMode == KeyboardLayoutUsageMode.Number
        }
    }

    private fun setNumberCustomLayoutTo(
        flickView: FlickKeyboardView,
        layout: CustomKeyboardLayout
    ) {
        numberKeyboardRenderJob = scope.launch(Dispatchers.IO) {
            val id = layout.layoutId
            val expectedStableId = layout.stableId
            val dbLayout = runCatching { keyboardRepository.getFullLayout(id).first() }
                .getOrElse {
                    Timber.w(
                        it,
                        "setNumberCustomLayoutTo: layout disappeared id=$id stableId=$expectedStableId"
                    )
                    return@launch
                }
            val finalLayout = keyboardRepository.convertLayout(dbLayout)
            withContext(Dispatchers.Main) {
                val currentNumberLayout = numberUsageCustomKeyboardLayoutOrNull()
                val stillNumberLayout = qwertyMode.value == TenKeyQWERTYMode.Number &&
                        currentNumberLayout?.layoutId == id &&
                        (expectedStableId.isBlank() || currentNumberLayout.stableId == expectedStableId)
                if (!stillNumberLayout) {
                    Timber.d("setNumberCustomLayoutTo: skip stale render id=$id stableId=$expectedStableId")
                    return@withContext
                }
                setKeyboardWithDeleteKeyFlickPreferences(flickView, finalLayout)
            }
        }
    }

    private fun showNumberKeyboardForCurrentInputType() {
        customKeyboardMode = KeyboardInputMode.SYMBOLS
        setCurrentInputModeForSession(InputMode.ModeNumber)
        _tenKeyQWERTYMode.update { TenKeyQWERTYMode.Number }
        suggestionAdapter?.updateState(TenKeyQWERTYMode.Number, emptyList())
        mainLayoutBinding?.apply {
            hideAllKeyboards()
            customLayoutDefault.isVisible = true
            qwertyView.setRomajiEnglishSwitchKeyVisibility(false)
            setNumberLayoutTo(customLayoutDefault)
            updateUpperAreaVisibility(this)
        }
        syncFloatingKeyboardContentForMode(TenKeyQWERTYMode.Number)
        renderCurrentKeyboardStateOnActiveSurface()
        updateFloatingKeyboardSizeForMode(TenKeyQWERTYMode.Number)
    }

    private fun setCurrentCustomLayoutTo(flickView: FlickKeyboardView) {
        val layout = selectedCustomKeyboardLayoutOrNull() ?: return
        scope.launch(Dispatchers.IO) {
            val id = layout.layoutId
            val expectedStableId = layout.stableId
            val dbLayout = runCatching { keyboardRepository.getFullLayout(id).first() }
                .getOrElse {
                    Timber.w(
                        it,
                        "setCurrentCustomLayoutTo: layout disappeared id=$id stableId=$expectedStableId"
                    )
                    return@launch
                }
            val finalLayout = keyboardRepository.convertLayout(dbLayout)
            isCustomLayoutRomajiMode = resolveInitialCustomKeyboardRomajiMode(
                layoutId = id,
                stableId = expectedStableId,
                defaultValue = finalLayout.isRomaji
            )
            isCustomLayoutDirectMode = resolveInitialCustomKeyboardDirectMode(
                layoutId = id,
                stableId = expectedStableId,
                defaultValue = finalLayout.isDirectMode
            )
            isCustomLayoutShiftPressed = false
            isCustomLayoutCapLock = false
            withContext(Dispatchers.Main) {
                if (!isCurrentCustomKeyboardSelection(layoutId = id, stableId = expectedStableId)) {
                    return@withContext
                }
                setKeyboardWithDeleteKeyFlickPreferences(flickView, finalLayout)
                syncCustomKeyboardToggleKeyIcons(flickView)
            }
        }
    }

    private fun setCustomLayoutOnActiveSurface(layout: KeyboardLayout) {
        getActiveKeyboardSurface()
            ?.customLayout
            ?.let { flickView -> setKeyboardWithDeleteKeyFlickPreferences(flickView, layout) }
    }

    private fun setCustomLayoutOnAvailableSurfaces(layout: KeyboardLayout) {
        getNormalKeyboardSurface()
            ?.customLayout
            ?.let { flickView -> setKeyboardWithDeleteKeyFlickPreferences(flickView, layout) }
        getFloatingKeyboardSurface()
            ?.customLayout
            ?.let { flickView -> setKeyboardWithDeleteKeyFlickPreferences(flickView, layout) }
    }

    private fun refreshDeleteKeyFlickPreferenceLayouts() {
        val customLayout = getActiveKeyboardSurface()?.customLayout ?: return
        when (qwertyMode.value) {
            TenKeyQWERTYMode.Sumire -> setSumireLayoutTo(customLayout)
            TenKeyQWERTYMode.Custom -> setCurrentCustomLayoutTo(customLayout)
            TenKeyQWERTYMode.Number -> setNumberLayoutTo(customLayout)
            else -> Unit
        }
        syncFloatingKeyboardContentForMode(qwertyMode.value)
    }

    private fun syncFloatingKeyboardContentForMode(mode: TenKeyQWERTYMode) {
        if (isKeyboardFloatingMode != true) return
        val mainView = mainLayoutBinding ?: return
        val floatingView = floatingKeyboardBinding ?: return
        when (mode) {
            TenKeyQWERTYMode.Default -> {
                configureFloatingTenKeyView(floatingView)
            }

            TenKeyQWERTYMode.TenKeyQWERTY -> {
                // listener bind / preference 適用は最初の 1 回だけにする。
                // ここで resetQWERTYKeyboard() を呼ぶと Floating 側 QWERTY の
                // qwertyMode / Shift / CapsLock / Romaji 等の内部状態が破壊されるため呼ばない。
                // 状態同期は toggle ON/OFF 時 (applyFloatingModeState) に行う。
                ensureFloatingQwertyConfigured(floatingView.qwertyViewFloating, mainView)
            }

            TenKeyQWERTYMode.TenKeyQWERTYRomaji -> {
                // setRomajiKeyboard() は内部状態を初期化してしまうため呼ばない。
                // 状態同期は toggle ON/OFF 時 (applyFloatingModeState) に行う。
                ensureFloatingQwertyConfigured(floatingView.qwertyViewFloating, mainView)
            }

            TenKeyQWERTYMode.Sumire -> {
                configureFlickKeyboardView(
                    floatingView.customLayoutFloating,
                    mainView,
                    isFloatingView = true
                )
                setSumireLayoutTo(floatingView.customLayoutFloating)
            }

            TenKeyQWERTYMode.Custom -> {
                configureFlickKeyboardView(
                    floatingView.customLayoutFloating,
                    mainView,
                    isFloatingView = true
                )
                setCurrentCustomLayoutTo(floatingView.customLayoutFloating)
            }

            TenKeyQWERTYMode.Number -> {
                configureFlickKeyboardView(
                    floatingView.customLayoutFloating,
                    mainView,
                    isFloatingView = true
                )
                setNumberLayoutTo(floatingView.customLayoutFloating)
            }
        }
    }

    /**
     * Floating 側 QWERTY view の listener bind / preference 適用を 1 回だけ行うための guard。
     *
     * 同じ View に対して configureQwertyView() を何度も呼ぶと listener の上書き再登録や
     * setSwitchNumberLayoutKeyVisibility(false) などの副作用が発生するため、
     * 初回のみ configure する。Floating mode が toggle されたり keyboard view が
     * 再生成された際は [isFloatingQwertyConfigured] を false にリセットすること。
     */
    private fun ensureFloatingQwertyConfigured(
        qwertyView: QWERTYKeyboardView,
        mainView: MainLayoutBinding,
    ) {
        if (isFloatingQwertyConfigured) return
        configureQwertyView(qwertyView, mainView)
        isFloatingQwertyConfigured = true
    }

    /**
     * 通常 QWERTY (mainView.qwertyView) の現在の表示状態を Floating 側 QWERTY に
     * 非破壊的にコピーする。
     *
     * resetQWERTYKeyboard() / setRomajiKeyboard() のような状態初期化を行わず、
     * snapshotUiState / renderUiState を経由して以下の状態のみを反映する:
     * - QWERTYMode (Default / Number / Symbol)
     * - Shift / CapsLock 状態
     * - Romaji / English モード
     * - Return / Space キーの label
     * - Romaji / English 切替キーの可視状態
     */
    private fun mirrorMainQwertyStateToFloating(
        mainView: MainLayoutBinding,
        floatingView: FloatingKeyboardLayoutBinding,
    ) {
        val state = mainView.qwertyView.snapshotUiState()
        floatingView.qwertyViewFloating.renderUiState(state)
    }

    /**
     * Floating QWERTY (floatingView.qwertyViewFloating) の現在の表示状態を
     * 通常 QWERTY 側へ非破壊的にコピーする。
     *
     * Floating mode を OFF に戻す際に呼ぶことで、ユーザーが Floating 中に変更した
     * Shift / CapsLock / Number / Symbol / Romaji 等の内部状態を引き継ぐ。
     *
     * ただし enterKeyText / spaceKeyText は InputType に応じて showKeyboard() が常に
     * mainView.qwertyView 側に正しい値をセットするのが source of truth であり、
     * Floating 側はその値を mirrorMainQwertyStateToFloating 経由でしか受け取らない。
     * Floating ON 中に showKeyboard(QWERTY/ROMAJI) が走ると mainView.qwertyView だけ
     * resetQWERTYKeyboard / setRomajiKeyboard で更新され、Floating 側の text label は
     * 古い (場合によっては空) 値のままになる可能性がある。その状態のまま OFF に戻す際に
     * Floating 側の text label を main へ書き戻すと、せっかく main 側に正しく載っていた
     * Return / Space ラベルを破壊してしまう。
     *
     * そのため text label は main の現在値を維持し、ユーザーが Floating ON 中に
     * 操作した QWERTYMode / Shift / CapsLock / Romaji / Romaji-English 切替キー可視状態
     * のみを Floating 側から引き継ぐ。
     */
    private fun mirrorFloatingQwertyStateToMain(
        mainView: MainLayoutBinding,
        floatingView: FloatingKeyboardLayoutBinding,
    ) {
        val floatingState = floatingView.qwertyViewFloating.snapshotUiState()
        val mainState = mainView.qwertyView.snapshotUiState()
        val merged = floatingState.copy(
            enterKeyText = mainState.enterKeyText,
            spaceKeyText = mainState.spaceKeyText
        )
        mainView.qwertyView.renderUiState(merged)
    }

    private fun configureFloatingTenKeyView(
        floatingKeyboardLayoutBinding: FloatingKeyboardLayoutBinding
    ) {
        val isNight = currentNightMode == Configuration.UI_MODE_NIGHT_YES
        val isDark = keyboardThemeMode == "dark" || (keyboardThemeMode == "default" && isNight)
        val isCustom = keyboardThemeMode == "custom"

        val resolvedBgColor = customThemeBgColor ?: Color.WHITE
        val resolvedKeyColor = customThemeKeyColor ?: Color.WHITE
        val resolvedSpecialKeyColor = customThemeSpecialKeyColor ?: Color.GRAY
        val resolvedEnterKeyColor = customThemeEnterKeyColor ?: Color.BLUE
        val resolvedPopupBgColor = customThemePopupBgColor ?: Color.WHITE

        val resolvedKeyTextColor = if (isCustom) {
            customThemeKeyTextColor ?: if (resolvedKeyColor.isLightColor()) Color.BLACK else Color.WHITE
        } else {
            if (isDark) Color.WHITE else Color.BLACK
        }

        val resolvedSpecialKeyTextColor = if (isCustom) {
            customThemeSpecialKeyTextColor ?: if (resolvedSpecialKeyColor.isLightColor()) Color.BLACK else Color.WHITE
        } else {
            if (isDark) Color.WHITE else Color.BLACK
        }

        val resolvedEnterKeyTextColor = if (isCustom) {
            customThemeEnterKeyTextColor ?: if (resolvedEnterKeyColor.isLightColor()) Color.BLACK else Color.WHITE
        } else {
            if (isDark) Color.WHITE else Color.BLACK
        }

        val resolvedPopupTextColor = if (isCustom) {
            customThemePopupTextColor ?: if (resolvedPopupBgColor.isLightColor()) Color.BLACK else Color.WHITE
        } else {
            if (isDark) Color.WHITE else Color.BLACK
        }

        floatingKeyboardLayoutBinding.keyboardViewFloating.applyKeyboardTheme(
            themeMode = keyboardThemeMode ?: "default",
            currentNightMode = currentNightMode,
            isDynamicColorEnabled = DynamicColors.isDynamicColorAvailable(),
            customBgColor = resolvedBgColor,
            customKeyColor = resolvedKeyColor,
            customSpecialKeyColor = resolvedSpecialKeyColor,
            customEnterKeyColor = resolvedEnterKeyColor,
            customKeyTextColor = resolvedKeyTextColor,
            customSpecialKeyTextColor = resolvedSpecialKeyTextColor,
            customEnterKeyTextColor = resolvedEnterKeyTextColor,
            customPopupBgColor = resolvedPopupBgColor,
            customPopupTextColor = resolvedPopupTextColor,
            liquidGlassEnable = liquidGlassThemePreference ?: false,
            customBorderEnable = customKeyBorderEnablePreference ?: false,
            customBorderColor = customKeyBorderEnableColor ?: Color.BLACK,
            liquidGlassKeyAlphaEnable = liquidGlassKeyBlurRadiousPreference ?: 255,
            borderWidth = customKeyBorderWidth ?: 1,
            keyBorderEnable = keyBorderEnable ?: false,
            keyCornerRadiusDp = keyCornerRadiusDp ?: 8,
            keyPopupStyle = keyPopupStyle ?: "default"
        )
        floatingKeyboardLayoutBinding.keyboardViewFloating.setPopupWindowAnchorProvider {
            floatingKeyboardLayoutBinding.root
        }
        floatingKeyboardLayoutBinding.keyboardViewFloating.setLongPressTimeout(
            (longPressTimeoutPreferenceValue ?: 300).toLong()
        )
        floatingKeyboardLayoutBinding.keyboardViewFloating.applyPopupViewStyle(
            currentTenKeyPopupViewStyle()
        )
        floatingKeyboardLayoutBinding.keyboardViewFloating.setFlickSensitivityValue(
            flickSensitivityPreferenceValue ?: 100
        )
        floatingKeyboardLayoutBinding.keyboardViewFloating.setFlickGuideEnabled(
            tenkeyQKeymapGuide ?: false
        )
        floatingKeyboardLayoutBinding.keyboardViewFloating.apply {
            setOnFlickListener(object : FlickListener {
                override fun onFlick(gestureType: GestureType, key: Key, char: Char?) {
                    val insertString = inputString.value
                    val sb = StringBuilder()
                    val suggestionList = suggestionAdapter?.suggestions ?: emptyList()
                    when (gestureType) {
                        GestureType.Null -> Unit
                        GestureType.Down -> handleKeyPressFeedback(getKeySoundType(key))
                        GestureType.Tap -> handleTapAndFlickFloating(
                            key = key,
                            char = char,
                            insertString = insertString,
                            sb = sb,
                            isFlick = false,
                            gestureType = gestureType,
                            suggestions = suggestionList,
                            floatingKeyboardLayoutBinding = floatingKeyboardLayoutBinding
                        )

                        else -> handleTapAndFlickFloating(
                            key = key,
                            char = char,
                            insertString = insertString,
                            sb = sb,
                            isFlick = true,
                            gestureType = gestureType,
                            suggestions = suggestionList,
                            floatingKeyboardLayoutBinding = floatingKeyboardLayoutBinding
                        )
                    }
                }
            })
            setOnLongPressListener(object : LongPressListener {
                override fun onLongPress(key: Key) {
                    handleLongPressFloating(key)
                    Timber.d("Long Press: $key")
                }
            })
        }
    }

    private fun updateFloatingKeyboardSizeForMode(mode: TenKeyQWERTYMode) {
        if (isKeyboardFloatingMode != true) return
        val floatingView = floatingKeyboardBinding ?: return
        val popupWindow = floatingKeyboardView ?: return
        val prefs = getKeyboardSizePreferences()
        val density = resources.displayMetrics.density
        val screenWidth = resources.displayMetrics.widthPixels
        val usesQwertySize =
            mode == TenKeyQWERTYMode.TenKeyQWERTY || mode == TenKeyQWERTYMode.TenKeyQWERTYRomaji
        val heightPref = if (usesQwertySize) prefs.qwertyHeightPref else prefs.heightPref
        val widthPref = if (usesQwertySize) prefs.qwertyWidthPref else prefs.widthPref
        val heightPx = (heightPref.coerceIn(60, 420) * density).toInt()
        val isSymbolOpen = keyboardSymbolViewState.value.isShown
        val widthPx = if (isSymbolOpen) {
            ViewGroup.LayoutParams.MATCH_PARENT
        } else if (widthPref == 100) {
            ViewGroup.LayoutParams.MATCH_PARENT
        } else {
            (screenWidth * (widthPref / 100f)).toInt()
        }
        (floatingView.floatingKeyboardContainer.layoutParams as? ConstraintLayout.LayoutParams)
            ?.let { params ->
                params.height = heightPx
                floatingView.floatingKeyboardContainer.layoutParams = params
            }
        updateFloatingFullCandidatesHeight(floatingView, heightPx)
        updateFloatingKeyboardBackgroundBounds(floatingView, heightPx)
        val savedX = appPreference.keyboard_floating_position_x
        val savedY = appPreference.keyboard_floating_position_y
        popupWindow.update(savedX, savedY, widthPx, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    private fun setTenKeyListeners(
        mainView: MainLayoutBinding
    ) {
        mainView.keyboardView.apply {
            val isNight = currentNightMode == Configuration.UI_MODE_NIGHT_YES
            val isDark = keyboardThemeMode == "dark" || (keyboardThemeMode == "default" && isNight)
            val isCustom = keyboardThemeMode == "custom"

            val resolvedBgColor = customThemeBgColor ?: Color.WHITE
            val resolvedKeyColor = customThemeKeyColor ?: Color.WHITE
            val resolvedSpecialKeyColor = customThemeSpecialKeyColor ?: Color.GRAY
            val resolvedEnterKeyColor = customThemeEnterKeyColor ?: Color.BLUE
            val resolvedPopupBgColor = customThemePopupBgColor ?: Color.WHITE

            val resolvedKeyTextColor = if (isCustom) {
                customThemeKeyTextColor ?: if (resolvedKeyColor.isLightColor()) Color.BLACK else Color.WHITE
            } else {
                if (isDark) Color.WHITE else Color.BLACK
            }

            val resolvedSpecialKeyTextColor = if (isCustom) {
                customThemeSpecialKeyTextColor ?: if (resolvedSpecialKeyColor.isLightColor()) Color.BLACK else Color.WHITE
            } else {
                if (isDark) Color.WHITE else Color.BLACK
            }

            val resolvedEnterKeyTextColor = if (isCustom) {
                customThemeEnterKeyTextColor ?: if (resolvedEnterKeyColor.isLightColor()) Color.BLACK else Color.WHITE
            } else {
                if (isDark) Color.WHITE else Color.BLACK
            }

            val resolvedPopupTextColor = if (isCustom) {
                customThemePopupTextColor ?: if (resolvedPopupBgColor.isLightColor()) Color.BLACK else Color.WHITE
            } else {
                if (isDark) Color.WHITE else Color.BLACK
            }

            applyKeyboardTheme(
                themeMode = keyboardThemeMode ?: "default",
                currentNightMode = currentNightMode,
                isDynamicColorEnabled = DynamicColors.isDynamicColorAvailable(),
                customBgColor = resolvedBgColor,
                customKeyColor = resolvedKeyColor,
                customSpecialKeyColor = resolvedSpecialKeyColor,
                customEnterKeyColor = resolvedEnterKeyColor,
                customKeyTextColor = resolvedKeyTextColor,
                customSpecialKeyTextColor = resolvedSpecialKeyTextColor,
                customEnterKeyTextColor = resolvedEnterKeyTextColor,
                customPopupBgColor = resolvedPopupBgColor,
                customPopupTextColor = resolvedPopupTextColor,
                liquidGlassEnable = liquidGlassThemePreference ?: false,
                customBorderEnable = customKeyBorderEnablePreference ?: false,
                customBorderColor = customKeyBorderEnableColor ?: Color.BLACK,
                liquidGlassKeyAlphaEnable = liquidGlassKeyBlurRadiousPreference ?: 255,
                borderWidth = customKeyBorderWidth ?: 1,
                keyBorderEnable = keyBorderEnable ?: false,
                keyCornerRadiusDp = keyCornerRadiusDp ?: 8,
                keyPopupStyle = keyPopupStyle ?: "default"
            )
            setPopupWindowAnchorProvider { mainView.root }
            setOnFlickListener(object : FlickListener {
                override fun onFlick(gestureType: GestureType, key: Key, char: Char?) {
                    Timber.d("Flick: $char $key $gestureType")
                    val insertString = inputString.value
                    val sb = StringBuilder()
                    val suggestionList = suggestionAdapter?.suggestions ?: emptyList()
                    when (gestureType) {
                        GestureType.Null -> {

                        }

                        GestureType.Down -> {
                            handleKeyPressFeedback(getKeySoundType(key))
                        }

                        GestureType.Tap -> {
                            handleTapAndFlick(
                                key = key,
                                char = char,
                                insertString = insertString,
                                sb = sb,
                                isFlick = false,
                                gestureType = gestureType,
                                suggestions = suggestionList,
                                mainView = mainView
                            )
                        }


                        else -> {
                            handleTapAndFlick(
                                key = key,
                                char = char,
                                insertString = insertString,
                                sb = sb,
                                isFlick = true,
                                gestureType = gestureType,
                                suggestions = suggestionList,
                                mainView = mainView
                            )
                        }
                    }
                }
            })
            setOnLongPressListener(object : LongPressListener {
                override fun onLongPress(key: Key) {
                    handleLongPress(key)
                    Timber.d("Long Press: $key")
                }
            })
            setOnInputModeChangedListener { inputMode ->
                handleTenKeyInputModeChanged(inputMode, mainView)
            }
        }
    }

    private fun handleTenKeyInputModeChanged(
        inputMode: InputMode,
        mainView: MainLayoutBinding
    ) {
        inputActionDispatcher.keyboardMode.setSessionMode(inputMode)
        if (switchTenkeyEnglishToQwertyIfNeeded(inputMode, mainView)) return
        setTenkeyIconsInHenkan(inputString.value, mainView)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setFloatingKeyboardListeners(
        floatingKeyboardLayoutBinding: FloatingKeyboardLayoutBinding
    ) {
        floatingKeyboardLayoutBinding.apply {
            dragHandle.setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        val location = IntArray(2)
                        floatingKeyboardView?.contentView?.getLocationOnScreen(location)
                        initialX = location[0]
                        initialY = location[1]
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        true
                    }

                    MotionEvent.ACTION_MOVE -> {
                        val newX = initialX + (event.rawX - initialTouchX)
                        val newY = initialY + (event.rawY - initialTouchY)
                        appPreference.keyboard_floating_position_x = newX.toInt()
                        appPreference.keyboard_floating_position_y = newY.toInt()
                        Timber.d("dragHandle.setOnTouchListene: $newX $newY")
                        floatingKeyboardView?.update(newX.toInt(), newY.toInt(), -1, -1)
                        true
                    }

                    else -> false
                }
            }
            floatingHideKeyboardBtn.setOnClickListener {
                requestHideSelf(InputMethodManager.HIDE_NOT_ALWAYS)
            }
        }
    }

    private fun updateSuggestionsForFloatingCandidate(
        suggestions: List<CandidateItem>,
        highlightedAbsoluteIndex: Int? = null
    ) {
        Timber.d("updateSuggestionsForFloatingCandidate: $suggestions")
        if (suggestions.isNotEmpty() && physicalKeyboardEnable.replayCache.firstOrNull() == true) {
            ensureFloatingCandidateWindowInitialized()
            requestCursorUpdates(InputConnection.CURSOR_UPDATE_IMMEDIATE or InputConnection.CURSOR_UPDATE_MONITOR)
        }
        fullSuggestionsList = suggestions
        highlightedAbsoluteIndex?.let { absoluteIndex ->
            if (suggestions.isNotEmpty() && absoluteIndex != RecyclerView.NO_POSITION) {
                val safeIndex = absoluteIndex.coerceIn(0, suggestions.lastIndex)
                currentPage = safeIndex / PAGE_SIZE
                currentHighlightIndex = safeIndex % PAGE_SIZE
            } else {
                currentPage = 0
                currentHighlightIndex = RecyclerView.NO_POSITION
            }
        } ?: run {
            currentPage = 0
        }
        displayCurrentPage()
    }

    private fun displayCurrentPage() {
        if (fullSuggestionsList.isEmpty()) {
            Timber.d("onUpdateCursorAnchorInfo displayCurrentPage empty called")
            listAdapter.submitList(emptyList())
            floatingCandidateWindow?.dismiss()
            return
        }

        val startIndex = currentPage * PAGE_SIZE
        val endIndex = (startIndex + PAGE_SIZE).coerceAtMost(fullSuggestionsList.size)
        val suggestionsForPage = fullSuggestionsList.subList(startIndex, endIndex)

        val itemsToShow = mutableListOf<CandidateItem>()
        itemsToShow.addAll(suggestionsForPage)

        val totalPages = (fullSuggestionsList.size + PAGE_SIZE - 1) / PAGE_SIZE
        if (totalPages > 1) {
            val pagerLabel = "▶ (${currentPage + 1}/$totalPages)"
            itemsToShow.add(CandidateItem(word = pagerLabel, length = (1).toUByte()))
        }
        listAdapter.submitList(itemsToShow) {
            listAdapter.updateHighlightPosition(currentHighlightIndex)
            Timber.d("floatingCandidateNextItem (after update): ${listAdapter.getHighlightedItem()} [$itemsToShow]")
        }
    }

    private fun setTabletKeyListeners(
        mainView: MainLayoutBinding
    ) {
        mainView.tabletView.apply {
            val isNight = currentNightMode == Configuration.UI_MODE_NIGHT_YES
            val isDark = keyboardThemeMode == "dark" || (keyboardThemeMode == "default" && isNight)
            val isCustom = keyboardThemeMode == "custom"

            val resolvedBgColor = customThemeBgColor ?: Color.WHITE
            val resolvedKeyColor = customThemeKeyColor ?: Color.WHITE
            val resolvedSpecialKeyColor = customThemeSpecialKeyColor ?: Color.GRAY
            val resolvedEnterKeyColor = customThemeEnterKeyColor ?: Color.BLUE

            val resolvedKeyTextColor = if (isCustom) {
                customThemeKeyTextColor ?: if (resolvedKeyColor.isLightColor()) Color.BLACK else Color.WHITE
            } else {
                if (isDark) Color.WHITE else Color.BLACK
            }

            val resolvedSpecialKeyTextColor = if (isCustom) {
                customThemeSpecialKeyTextColor ?: if (resolvedSpecialKeyColor.isLightColor()) Color.BLACK else Color.WHITE
            } else {
                if (isDark) Color.WHITE else Color.BLACK
            }

            val resolvedEnterKeyTextColor = if (isCustom) {
                customThemeEnterKeyTextColor ?: if (resolvedEnterKeyColor.isLightColor()) Color.BLACK else Color.WHITE
            } else {
                if (isDark) Color.WHITE else Color.BLACK
            }

            applyKeyboardTheme(
                themeMode = keyboardThemeMode ?: "default",
                currentNightMode = currentNightMode,
                isDynamicColorEnabled = DynamicColors.isDynamicColorAvailable(),
                customBgColor = resolvedBgColor,
                customKeyColor = resolvedKeyColor,
                customSpecialKeyColor = resolvedSpecialKeyColor,
                customEnterKeyColor = resolvedEnterKeyColor,
                customKeyTextColor = resolvedKeyTextColor,
                customSpecialKeyTextColor = resolvedSpecialKeyTextColor,
                customEnterKeyTextColor = resolvedEnterKeyTextColor,
                liquidGlassEnable = liquidGlassThemePreference ?: false,
                customBorderEnable = customKeyBorderEnablePreference ?: false,
                customBorderColor = customKeyBorderEnableColor ?: Color.BLACK,
                liquidGlassKeyAlphaEnable = liquidGlassKeyBlurRadiousPreference ?: 255,
                borderWidth = customKeyBorderWidth ?: 1,
                keyBorderEnable = keyBorderEnable ?: false,
                keyCornerRadiusDp = keyCornerRadiusDp ?: 8,
                keyPopupStyle = keyPopupStyle ?: "default"
            )
            setOnFlickListener(object : FlickListener {
                override fun onFlick(gestureType: GestureType, key: Key, char: Char?) {
                    Timber.d("Flick: $char $key $gestureType")
                    val insertString = inputString.value
                    val sb = StringBuilder()
                    val suggestionList = suggestionAdapter?.suggestions ?: emptyList()
                    when (gestureType) {
                        GestureType.Null -> {

                        }

                        GestureType.Down -> {
                            handleKeyPressFeedback(getKeySoundType(key))
                        }

                        GestureType.Tap -> {
                            handleTapAndFlick(
                                key = key,
                                char = char,
                                insertString = insertString,
                                sb = sb,
                                isFlick = false,
                                gestureType = gestureType,
                                suggestions = suggestionList,
                                mainView = mainView
                            )
                        }

                        else -> {
                            handleTapAndFlick(
                                key = key,
                                char = char,
                                insertString = insertString,
                                sb = sb,
                                isFlick = true,
                                gestureType = gestureType,
                                suggestions = suggestionList,
                                mainView = mainView
                            )
                        }
                    }
                }

            })
            setOnLongPressListener(object : LongPressListener {
                override fun onLongPress(key: Key) {
                    handleLongPress(key)
                }
            })
            setOnInputModeChangedListener { inputMode ->
                handleTenKeyInputModeChanged(inputMode, mainView)
            }
        }
    }

    private fun handleTapAndFlick(
        key: Key,
        char: Char?,
        insertString: String,
        sb: StringBuilder,
        isFlick: Boolean,
        gestureType: GestureType,
        suggestions: List<Candidate>,
        mainView: MainLayoutBinding,
    ) {
        dispatchTapAndFlickGesture(
            key = key,
            char = char,
            insertString = insertString,
            sb = sb,
            isFlick = isFlick,
            gestureType = gestureType,
            suggestions = suggestions,
            surfaceActions = mainTapFlickSurfaceActions(mainView, suggestions),
        )
    }

    private fun handleTapAndFlickFloating(
        key: Key,
        char: Char?,
        insertString: String,
        sb: StringBuilder,
        isFlick: Boolean,
        gestureType: GestureType,
        suggestions: List<Candidate>,
        floatingKeyboardLayoutBinding: FloatingKeyboardLayoutBinding,
    ) {
        dispatchTapAndFlickGesture(
            key = key,
            char = char,
            insertString = insertString,
            sb = sb,
            isFlick = isFlick,
            gestureType = gestureType,
            suggestions = suggestions,
            surfaceActions = floatingTapFlickSurfaceActions(
                floatingKeyboardLayoutBinding,
                suggestions,
            ),
        )
    }

    private fun dispatchTapAndFlickGesture(
        key: Key,
        char: Char?,
        insertString: String,
        sb: StringBuilder,
        isFlick: Boolean,
        gestureType: GestureType,
        suggestions: List<Candidate>,
        surfaceActions: TapFlickInputBridge.TapFlickSurfaceActions,
    ) {
        inputActionDispatcher.dispatchTenKeyGesture(
            request = TapFlickInputBridge.TapFlickDispatchRequest(
                key = key,
                char = char,
                insertString = insertString,
                isFlick = isFlick,
                gestureType = gestureType,
                suggestions = suggestions,
            ),
            sb = sb,
            session = tapFlickSessionHooks(),
            surfaceActions = surfaceActions,
        )
    }

    private fun tapFlickSessionHooks(): TapFlickInputBridge.TapFlickSessionHooks =
        object : TapFlickInputBridge.TapFlickSessionHooks {
            override val vibrationTimingStr: String? get() = this@IMEService.vibrationTimingStr
            override fun vibrate() = this@IMEService.vibrate()
            override val selectModeActive: Boolean get() = selectMode.value
            override val deletedBufferNotEmpty: Boolean get() = deletedBuffer.isNotEmpty()
            override fun clearDeletedBuffer() = this@IMEService.clearDeletedBuffer()
            override fun clearDeletedBufferWithoutResetLayout() =
                this@IMEService.clearDeletedBufferWithoutResetLayout()
            override fun refreshEditHistoryUi() = this@IMEService.refreshEditHistoryUi()
            override val leftCursorLongPressed: Boolean get() = leftCursorKeyLongKeyPressed.get()
            override val rightCursorLongPressed: Boolean get() = rightCursorKeyLongKeyPressed.get()
            override val deleteKeyLongPressed: Boolean get() = deleteKeyLongKeyPressed.get()
            override val isHenkanActive: Boolean get() = isHenkan.get()
            override val cursorMoveModeActive: Boolean get() = cursorMoveMode.value
            override fun exitCursorMoveMode() {
                _cursorMoveMode.update { false }
            }
            override val isSpaceKeyLongPressed: Boolean
                get() = this@IMEService.isSpaceKeyLongPressed
            override fun setSpaceKeyLongPressed(value: Boolean) {
                this@IMEService.isSpaceKeyLongPressed = value
            }
            override val hankakuPreference: Boolean? get() = this@IMEService.hankakuPreference
            override val isDeleteLeftFlickPreference: Boolean?
                get() = this@IMEService.isDeleteLeftFlickPreference
            override val isDeleteUpFlickPreference: Boolean?
                get() = this@IMEService.isDeleteUpFlickPreference
            override val isDeleteDownFlickPreference: Boolean?
                get() = this@IMEService.isDeleteDownFlickPreference
            override fun onLeftKeyLongPressReleased() {
                onLeftKeyLongPressUp.set(true)
                leftCursorKeyLongKeyPressed.set(false)
                leftLongPressJob?.cancel()
                leftLongPressJob = null
            }
            override fun onRightKeyLongPressReleased() {
                onRightKeyLongPressUp.set(true)
                rightCursorKeyLongKeyPressed.set(false)
                rightLongPressJob?.cancel()
                rightLongPressJob = null
            }
            override fun stopDeleteLongPress() = this@IMEService.stopDeleteLongPress()
            override fun toggleSymbolKeyboard() {
                _keyboardSymbolViewState.value = SymbolKeyboardState(
                    isShown = !_keyboardSymbolViewState.value.isShown,
                )
            }
            override fun finishComposingAndClearTail() {
                stringInTail.set("")
                finishComposingText()
                setComposingText("", 0)
            }
            override fun performCopy() = copyAction()
            override fun performCut() = cutAction()
            override fun performSelectAll() = selectAllText()
            override fun performShareSelectedText() {
                val selectedText = getSelectedText(0)
                if (!selectedText.isNullOrEmpty()) {
                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, selectedText.toString())
                    }
                    val chooser: Intent =
                        Intent.createChooser(sendIntent, "Share text via").apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                    startActivity(chooser)
                    clearSelection()
                }
            }
        }

    private val tapFlickSharedHost = object : TapFlickSurfaceActionsFactory.SharedHost {
        override fun handleDeleteKeyInHenkan(suggestions: List<Candidate>, insertString: String) =
            this@IMEService.handleDeleteKeyInHenkan(suggestions, insertString)
        override fun handleLeftCursor(gestureType: GestureType, insertString: String) =
            this@IMEService.handleLeftCursor(gestureType, insertString)
        override fun actionInRightKeyPressed(gestureType: GestureType, insertString: String) =
            this@IMEService.actionInRightKeyPressed(gestureType, insertString)
        override fun handleDeleteKeyTap(insertString: String, suggestions: List<Candidate>) =
            this@IMEService.handleDeleteKeyTap(insertString, suggestions)
        override fun deleteWordOrSymbolsBeforeCursor(insertString: String) =
            this@IMEService.deleteWordOrSymbolsBeforeCursor(insertString)
        override fun deleteWordOrSymbolsAfterCursor(insertString: String) =
            this@IMEService.deleteWordOrSymbolsAfterCursor(insertString)
        override fun undoLastHistoryEntry() = this@IMEService.undoLastHistoryEntry()
        override fun setNextReturnInputCharacter(insertString: String) =
            this@IMEService.setNextReturnInputCharacter(insertString)
    }

    private fun mainTapFlickSurfaceActions(
        mainView: MainLayoutBinding,
        suggestions: List<Candidate>,
    ): TapFlickInputBridge.TapFlickSurfaceActions {
        return TapFlickSurfaceActionsFactory.create(
            shared = tapFlickSharedHost,
            bindings = TapFlickSurfaceActionsFactory.Bindings(
                surface = MainImeKeyboardSurface { currentTenkeyInputMode(mainView) },
                onNonEmptyEnter = { insertString, list ->
                    handleNonEmptyInputEnterKey(list, mainView, insertString)
                },
                onEmptyEnter = { handleEmptyInputEnterKey(mainView) },
                onDakutenSmall = { sb, isFlick, char, insertString, gestureType ->
                    handleDakutenSmallLetterKey(sb, isFlick, char, insertString, mainView, gestureType)
                },
                moveFocusedBunsetsu = { delta -> moveFocusedBunsetsuSegment(delta = delta) },
                onJapaneseModeSpaceKey = { list, insertString ->
                    handleJapaneseModeSpaceKey(mainView, list, insertString)
                },
                setTenkeyIconsInHenkan = { insertString ->
                    setTenkeyIconsInHenkan(insertString, mainView)
                },
                cycleFocusedBunsetsuCandidate = { delta -> cycleFocusedBunsetsuCandidate(delta = delta) },
                onSpaceKeyClick = { isHankaku, insertString, list ->
                    handleSpaceKeyClick(isHankaku, insertString, list, mainView)
                },
                onFlick = { char, insertString, sb -> handleFlick(char, insertString, sb, mainView) },
                onTap = { char, insertString, sb -> handleTap(char, insertString, sb, mainView) },
            ),
        )
    }

    private fun floatingTapFlickSurfaceActions(
        floatingKeyboardLayoutBinding: FloatingKeyboardLayoutBinding,
        suggestions: List<Candidate>,
    ): TapFlickInputBridge.TapFlickSurfaceActions {
        val floating = floatingKeyboardLayoutBinding
        return TapFlickSurfaceActionsFactory.create(
            shared = tapFlickSharedHost,
            bindings = TapFlickSurfaceActionsFactory.Bindings(
                surface = FloatingImeKeyboardSurface {
                    floating.keyboardViewFloating.currentInputMode.value
                },
                onNonEmptyEnter = { insertString, list ->
                    handleNonEmptyInputEnterKeyFloating(list, floating, insertString)
                },
                onEmptyEnter = { handleEmptyInputEnterKeyFloating(floating) },
                onDakutenSmall = { sb, isFlick, char, insertString, gestureType ->
                    handleDakutenSmallLetterKeyFloating(
                        sb, isFlick, char, insertString, floating, gestureType,
                    )
                },
                moveFocusedBunsetsu = { delta -> moveFocusedBunsetsuSegment(delta, floating) },
                onJapaneseModeSpaceKey = { list, insertString ->
                    handleJapaneseModeSpaceKeyFloating(floating, list, insertString)
                },
                setTenkeyIconsInHenkan = { insertString ->
                    setTenkeyIconsInHenkanFloating(insertString, floating)
                },
                cycleFocusedBunsetsuCandidate = { delta ->
                    cycleFocusedBunsetsuCandidate(delta, floating)
                },
                onSpaceKeyClick = { isHankaku, insertString, list ->
                    handleSpaceKeyClickFloating(isHankaku, insertString, list, floating)
                },
                onFlick = { char, insertString, sb ->
                    handleFlickFloating(char, insertString, sb, floating)
                },
                onTap = { char, insertString, sb ->
                    handleTapFloating(char, insertString, sb, floating)
                },
            ),
        )
    }

    private fun handleFlick(
        char: Char?, insertString: String, sb: StringBuilder, _mainView: MainLayoutBinding
    ) {
        if (isHenkan.get()) {
            commitCurrentHenkanForNewInput()
            char?.let {
                sendCharFlick(
                    charToSend = it, insertString = "", sb = sb
                )
            }
            isContinuousTapInputEnabled.set(true)
            lastFlickConvertedNextHiragana.set(true)
        } else {
            char?.let {
                sendCharFlick(
                    charToSend = it, insertString = insertString, sb = sb
                )
            }
            isContinuousTapInputEnabled.set(true)
            lastFlickConvertedNextHiragana.set(true)
        }
    }

    private fun handleFlickFloating(
        char: Char?,
        insertString: String,
        sb: StringBuilder,
        _floatingKeyboardLayoutBinding: FloatingKeyboardLayoutBinding
    ) {
        if (isHenkan.get()) {
            commitCurrentHenkanForNewInput()
            char?.let {
                sendCharFlick(
                    charToSend = it, insertString = "", sb = sb
                )
            }
            isContinuousTapInputEnabled.set(true)
            lastFlickConvertedNextHiragana.set(true)
        } else {
            char?.let {
                sendCharFlick(
                    charToSend = it, insertString = insertString, sb = sb
                )
            }
            isContinuousTapInputEnabled.set(true)
            lastFlickConvertedNextHiragana.set(true)
        }
    }

    private fun handleTap(
        char: Char?, insertString: String, sb: StringBuilder, _mainView: MainLayoutBinding
    ) {
        if (isHenkan.get()) {
            commitCurrentHenkanForNewInput()
            char?.let {
                sendCharTap(
                    charToSend = it, insertString = "", sb = sb
                )
            }
        } else {
            char?.let {
                sendCharTap(
                    charToSend = it, insertString = insertString, sb = sb
                )
            }
        }
    }

    private fun Char.toRomajiQwertyOutputChar(): Char {
        return toRomajiQwertyOutputChar(
            useHankakuNumber = qwertyRomajiHankakuNumberPreference == true,
            useHankakuSymbol = qwertyRomajiHankakuSymbolPreference == true
        )
    }

    private fun Char.shouldApplyRomajiQwertyWidthPreference(): Boolean {
        return isLowerCase() ||
                isAsciiDigitForRomajiQwerty() ||
                isAsciiSymbolForRomajiQwerty()
    }

    private fun Char.shouldUseRomajiQwertyOutputCharAfterShift(): Boolean {
        return !hardKeyboardShiftPressd || shouldApplyRomajiQwertyWidthPreference()
    }

    private fun handleTapFloating(
        char: Char?,
        insertString: String,
        sb: StringBuilder,
        _floatingKeyboardLayoutBinding: FloatingKeyboardLayoutBinding
    ) {
        if (isHenkan.get()) {
            commitCurrentHenkanForNewInput()
            char?.let {
                sendCharTap(
                    charToSend = it, insertString = "", sb = sb
                )
            }
        } else {
            char?.let {
                sendCharTap(
                    charToSend = it, insertString = insertString, sb = sb
                )
            }
        }
    }

    private fun handleLongPress(
        key: Key
    ) {
        when (key) {
            Key.NotSelected -> {}
            Key.SideKeyEnter -> {}
            Key.KeyDakutenSmall -> {
                if (tenkeyShowIMEButtonPreference == true) {
                    showListPopup()
                }
            }

            Key.SideKeyCursorLeft -> {
                handleLeftLongPress()
                leftCursorKeyLongKeyPressed.set(true)
                if (selectMode.value) {
                    clearDeletedBufferWithoutResetLayout()
                } else {
                    clearDeletedBuffer()
                }
                refreshEditHistoryUi()
            }

            Key.SideKeyCursorRight -> {
                handleRightLongPress()
                rightCursorKeyLongKeyPressed.set(true)
                if (selectMode.value) {
                    clearDeletedBufferWithoutResetLayout()
                } else {
                    clearDeletedBuffer()
                }
                refreshEditHistoryUi()
            }

            Key.SideKeyDelete -> {
                handleDeleteLongPress()
            }

            Key.SideKeyInputMode -> {}
            Key.SideKeyPreviousChar -> {}
            Key.SideKeySpace -> {
                handleSpaceLongAction()
            }

            Key.SideKeySymbol -> {}
            else -> {}
        }
    }

    private fun handleLongPressFloating(
        key: Key
    ) {
        when (key) {
            Key.NotSelected -> {}
            Key.SideKeyEnter -> {}
            Key.KeyDakutenSmall -> {
                showListPopup()
            }

            Key.SideKeyCursorLeft -> {
                handleLeftLongPress()
                leftCursorKeyLongKeyPressed.set(true)
                if (selectMode.value) {
                    clearDeletedBufferWithoutResetLayout()
                } else {
                    clearDeletedBuffer()
                }
                refreshEditHistoryUi()
            }

            Key.SideKeyCursorRight -> {
                handleRightLongPress()
                rightCursorKeyLongKeyPressed.set(true)
                if (selectMode.value) {
                    clearDeletedBufferWithoutResetLayout()
                } else {
                    clearDeletedBuffer()
                }
                refreshEditHistoryUi()
            }

            Key.SideKeyDelete -> {
                handleDeleteLongPress()
            }

            Key.SideKeyInputMode -> {}
            Key.SideKeyPreviousChar -> {}
            Key.SideKeySpace -> {
                handleSpaceLongActionFloating()
            }

            Key.SideKeySymbol -> {}
            else -> {}
        }
    }

    private var keyboardSelectionPopupWindow: PopupWindow? = null

    private fun shouldShowCandidateLongPressActions(): Boolean {
        return isNgWordEnable == true || gemmaTranslationManager.isTranslationAvailable()
    }

    private fun showCandidateLongPressActions(
        insertString: String, candidate: Candidate, candidatePosition: Int
    ) {
        ioScope.launch {
            val enabledPromptTemplates = if (gemmaTranslationManager.isTranslationAvailable()) {
                gemmaPromptTemplateRepository.getEnabledTemplates(customGemmaPromptActionLimit)
            } else {
                emptyList()
            }

            withContext(Dispatchers.Main) {
                showCandidateLongPressActionsPopup(
                    insertString = insertString,
                    candidate = candidate,
                    candidatePosition = candidatePosition,
                    promptTemplates = enabledPromptTemplates
                )
            }
        }
    }

    private fun showCandidateLongPressActionsPopup(
        insertString: String,
        candidate: Candidate,
        candidatePosition: Int,
        promptTemplates: List<GemmaPromptTemplate>
    ) {
        mainLayoutBinding?.let { mainView ->
            val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
            val popupView = inflater.inflate(R.layout.popup_list_layout, mainView.root, false)
            val listView = popupView.findViewById<ListView>(R.id.popup_listview)

            listView.choiceMode = ListView.CHOICE_MODE_SINGLE

            val actions = buildList {
                if (isNgWordEnable == true) {
                    add(CandidateLongPressAction.HideWord)
                }
                if (gemmaTranslationManager.isTranslationAvailable()) {
                    add(CandidateLongPressAction.Translate)
                    promptTemplates.forEach { template ->
                        add(CandidateLongPressAction.CustomPrompt(template))
                    }
                }
                add(CandidateLongPressAction.Close)
            }

            val items = actions.map { action ->
                when (action) {
                    CandidateLongPressAction.HideWord -> "この単語を非表示"
                    CandidateLongPressAction.Translate -> getString(R.string.candidate_action_translate)
                    is CandidateLongPressAction.CustomPrompt -> action.template.title
                    CandidateLongPressAction.Close -> getString(R.string.candidate_action_close)
                }
            }

            val adapter = ArrayAdapter(this, R.layout.list_item_layout, items)
            listView.adapter = adapter

            keyboardSelectionPopupWindow = PopupWindow(
                popupView,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                true
            )
            listView.setOnItemClickListener { _, _, position, _ ->
                Timber.d("candidate long click: $candidate $candidatePosition")
                val selectedAction = actions.getOrNull(position)
                when (selectedAction) {
                    CandidateLongPressAction.HideWord -> {
                        ioScope.launch {
                            val exist = ngWordRepository.exists(
                                yomi = insertString, tango = candidate.string
                            )
                            if (!exist) {
                                ngWordRepository.addNgWord(
                                    yomi = insertString, tango = candidate.string
                                )
                                withContext(Dispatchers.Main) {
                                    _suggestionFlag.emit(CandidateShowFlag.Updating)
                                }
                            }
                        }
                    }

                    CandidateLongPressAction.Translate -> translateCandidateInPlace(
                        candidate = candidate,
                        candidatePosition = candidatePosition
                    )

                    is CandidateLongPressAction.CustomPrompt -> executeCustomGemmaPromptInPlace(
                        template = selectedAction.template,
                        candidate = candidate,
                        candidatePosition = candidatePosition
                    )

                    CandidateLongPressAction.Close, null -> Unit
                }
                keyboardSelectionPopupWindow?.dismiss()
            }

            keyboardSelectionPopupWindow?.let { popupWindow ->
                showPopupWindowSafely(
                    popupWindow = popupWindow,
                    anchorView = mainView.suggestionRecyclerView,
                    gravity = Gravity.TOP,
                    x = 0,
                    y = 0,
                    source = "registerNGWord"
                )
            }
        }
    }

    private fun showSelectedTextGemmaActions(selectedText: String) {
        if (!gemmaTranslationManager.isTranslationAvailable()) {
            clearSelectedTextGemmaSession(clearSuggestions = true)
            return
        }
        if (selectedTextGemmaSession?.selectedText == selectedText &&
            suggestionAdapter?.suggestions.orEmpty().any { isSelectedTextGemmaActionCandidate(it) }
        ) {
            return
        }

        val requestId = selectedTextGemmaActionMenuRequestId.incrementAndGet()
        ioScope.launch {
            val templates = gemmaPromptTemplateRepository.getEnabledTemplates(
                customGemmaPromptActionLimit
            )
            withContext(Dispatchers.Main) {
                if (selectedTextGemmaActionMenuRequestId.get() != requestId) return@withContext
                val currentSelection =
                    editorGateway.getSelectedText(0)?.toString().orEmpty()
                if (currentSelection != selectedText) return@withContext

                val actions = buildList {
                    add(SelectedTextGemmaAction.Translate)
                    templates.forEach { template ->
                        add(SelectedTextGemmaAction.CustomPrompt(template))
                    }
                }
                if (actions.isEmpty()) {
                    clearSelectedTextGemmaSession(clearSuggestions = true)
                    return@withContext
                }

                selectedTextGemmaSession = SelectedTextGemmaSession(
                    selectedText = selectedText,
                    actions = actions
                )
                val candidates = buildSelectedTextGemmaActionCandidates(
                    selectedText = selectedText,
                    actions = actions
                )
                suggestionAdapter?.suggestions = candidates
                suggestionAdapterFull?.suggestions = candidates
                suggestionAdapter?.updateHighlightPosition(RecyclerView.NO_POSITION)
                suggestionAdapterFull?.updateHighlightPosition(RecyclerView.NO_POSITION)
            }
        }
    }

    private fun buildSelectedTextGemmaActionCandidates(
        selectedText: String,
        actions: List<SelectedTextGemmaAction>
    ): List<Candidate> {
        val candidateLength = selectedText.length
            .coerceIn(0, UByte.MAX_VALUE.toInt())
            .toUByte()
        return actions.mapIndexed { index, action ->
            when (action) {
                SelectedTextGemmaAction.Translate -> Candidate(
                    string = getString(R.string.candidate_action_translate),
                    type = GemmaTranslationManager.SELECTION_TRANSLATE_ACTION_CANDIDATE_TYPE.toByte(),
                    length = candidateLength,
                    score = Int.MAX_VALUE - index
                )

                is SelectedTextGemmaAction.CustomPrompt -> Candidate(
                    string = action.template.title,
                    type = GemmaTranslationManager.SELECTION_PROMPT_ACTION_CANDIDATE_TYPE.toByte(),
                    length = candidateLength,
                    score = Int.MAX_VALUE - index,
                    yomi = action.template.id.toString()
                )
            }
        }
    }

    private fun clearSelectedTextGemmaSession(clearSuggestions: Boolean) {
        selectedTextGemmaActionMenuRequestId.incrementAndGet()
        cancelActiveSelectedTextGemmaAction()
        selectedTextGemmaSession = null
        if (!clearSuggestions) return
        suggestionAdapter?.suggestions = emptyList()
        suggestionAdapterFull?.suggestions = emptyList()
        suggestionAdapter?.updateHighlightPosition(RecyclerView.NO_POSITION)
        suggestionAdapterFull?.updateHighlightPosition(RecyclerView.NO_POSITION)
    }

    private fun handleSelectedTextGemmaActionClick(position: Int): Boolean {
        val session = selectedTextGemmaSession ?: return false
        val action = session.actions.getOrNull(position) ?: return false
        when (action) {
            SelectedTextGemmaAction.Translate -> executeSelectedTextGemmaAction(
                actionLabel = getString(R.string.candidate_action_translate),
                sourceText = session.selectedText,
                emptyResultMessage = getString(R.string.candidate_translation_empty),
                failureMessage = getString(R.string.candidate_translation_failed)
            ) { sourceText ->
                gemmaTranslationManager.translate(sourceText)
            }

            is SelectedTextGemmaAction.CustomPrompt -> executeSelectedTextGemmaAction(
                actionLabel = action.template.title,
                sourceText = session.selectedText,
                emptyResultMessage = getString(R.string.candidate_gemma_prompt_empty),
                failureMessage = getString(
                    R.string.candidate_gemma_prompt_failed,
                    action.template.title
                )
            ) { sourceText ->
                gemmaTranslationManager.runCustomPrompt(
                    text = sourceText,
                    promptTitle = action.template.title,
                    promptBody = action.template.prompt
                )
            }
        }
        return true
    }

    private fun executeSelectedTextGemmaAction(
        actionLabel: String,
        sourceText: String,
        emptyResultMessage: String,
        failureMessage: String,
        transform: suspend (String) -> String
    ) {
        cancelActiveCandidateTranslation()
        cancelActiveSelectedTextGemmaAction()
        val requestId = selectedTextGemmaActionRequestId.incrementAndGet()
        setCandidateTranslationProgressVisible(true)
        showToastMessage(
            if (actionLabel == getString(R.string.candidate_action_translate)) {
                getString(R.string.candidate_translation_in_progress)
            } else {
                getString(R.string.candidate_gemma_prompt_in_progress, actionLabel)
            }
        )
        selectedTextGemmaActionJob = ioScope.launch {
            runCatching {
                val transformedText = transform(sourceText)
                transformedText.takeIf { it.isNotBlank() }
                    ?: throw IllegalStateException(emptyResultMessage)
            }.onSuccess { transformedText ->
                withContext(Dispatchers.Main) {
                    if (!isSelectedTextGemmaActionRequestCurrent(requestId)) return@withContext
                    finishSelectedTextGemmaAction(requestId)
                    replaceSelectedTextWithGemmaResult(
                        originalText = sourceText,
                        transformedText = transformedText
                    )
                }
            }.onFailure { error ->
                Timber.e(error, "Selected text Gemma action failed.")
                withContext(Dispatchers.Main) {
                    if (!isSelectedTextGemmaActionRequestCurrent(requestId)) return@withContext
                    finishSelectedTextGemmaAction(requestId)
                    if (error is CancellationException) return@withContext
                    showToastMessage(resolveThrowableMessage(error, failureMessage))
                }
            }
        }
    }

    private fun replaceSelectedTextWithGemmaResult(
        originalText: String,
        transformedText: String
    ) {
        if (editorGateway.connection() == null) {
            showToastMessage(getString(R.string.candidate_translation_cancelled_context_changed))
            clearSelectedTextGemmaSession(clearSuggestions = true)
            return
        }
        val currentSelectedText = editorGateway.getSelectedText(0)?.toString().orEmpty()
        if (currentSelectedText != originalText) {
            showToastMessage(getString(R.string.candidate_translation_cancelled_context_changed))
            clearSelectedTextGemmaSession(clearSuggestions = true)
            return
        }
        if (transformedText == originalText) {
            clearSelectedTextGemmaSession(clearSuggestions = true)
            return
        }

        beginBatchEdit()
        try {
            commitText(transformedText, 1)
        } finally {
            endBatchEdit()
        }
        pushEditHistoryEntry(
            EditHistoryEntry.ReplaceCommittedText(
                beforeText = originalText,
                afterText = transformedText
            )
        )
        clearSelectedTextGemmaSession(clearSuggestions = true)
    }

    private fun isSelectedTextGemmaActionCandidate(candidate: Candidate): Boolean {
        return candidate.type == GemmaTranslationManager.SELECTION_TRANSLATE_ACTION_CANDIDATE_TYPE.toByte() ||
                candidate.type == GemmaTranslationManager.SELECTION_PROMPT_ACTION_CANDIDATE_TYPE.toByte()
    }

    private fun isSelectedTextGemmaActionRequestCurrent(requestId: Long): Boolean {
        return selectedTextGemmaActionRequestId.get() == requestId
    }

    private fun finishSelectedTextGemmaAction(requestId: Long) {
        if (!isSelectedTextGemmaActionRequestCurrent(requestId)) return
        selectedTextGemmaActionJob = null
        setCandidateTranslationProgressVisible(false)
    }

    private fun cancelActiveSelectedTextGemmaAction() {
        val currentJob = selectedTextGemmaActionJob
        if (currentJob?.isActive != true) return
        selectedTextGemmaActionRequestId.incrementAndGet()
        selectedTextGemmaActionJob = null
        setCandidateTranslationProgressVisible(false)
        gemmaTranslationManager.cancelActiveTranslation()
        currentJob.cancel(CancellationException("Selected text Gemma action cancelled."))
    }

    private fun translateCandidateInPlace(candidate: Candidate, candidatePosition: Int) {
        executeGemmaCandidateAction(
            candidate = candidate,
            candidatePosition = candidatePosition,
            progressMessage = getString(R.string.candidate_translation_in_progress),
            emptyResultMessage = getString(R.string.candidate_translation_empty),
            failureMessage = getString(R.string.candidate_translation_failed),
            resultCandidateType = GemmaTranslationManager.TRANSLATED_CANDIDATE_TYPE
        ) { sourceText ->
            gemmaTranslationManager.translate(sourceText)
        }
    }

    private fun executeCustomGemmaPromptInPlace(
        template: GemmaPromptTemplate,
        candidate: Candidate,
        candidatePosition: Int
    ) {
        executeGemmaCandidateAction(
            candidate = candidate,
            candidatePosition = candidatePosition,
            progressMessage = getString(
                R.string.candidate_gemma_prompt_in_progress,
                template.title
            ),
            emptyResultMessage = getString(R.string.candidate_gemma_prompt_empty),
            failureMessage = getString(R.string.candidate_gemma_prompt_failed, template.title),
            resultCandidateType = GemmaTranslationManager.PROMPT_RESULT_CANDIDATE_TYPE
        ) { sourceText ->
            gemmaTranslationManager.runCustomPrompt(
                text = sourceText,
                promptTitle = template.title,
                promptBody = template.prompt
            )
        }
    }

    private fun executeGemmaCandidateAction(
        candidate: Candidate,
        candidatePosition: Int,
        progressMessage: String,
        emptyResultMessage: String,
        failureMessage: String,
        resultCandidateType: Int,
        transform: suspend (String) -> String
    ) {
        cancelActiveCandidateTranslation()
        cancelActiveSelectedTextGemmaAction()
        val sourceText = displayTextFromCandidate(candidate)
        val expectedPreEditSnapshot = resolveCurrentPreEditText()
        val requestId = candidateTranslationRequestId.incrementAndGet()
        candidateTranslationContextSnapshot = expectedPreEditSnapshot
        setCandidateTranslationProgressVisible(true)
        showToastMessage(progressMessage)
        candidateTranslationJob = ioScope.launch {
            runCatching {
                val transformedText = transform(sourceText)
                transformedText.takeIf { it.isNotBlank() }
                    ?: throw IllegalStateException(emptyResultMessage)
            }.onSuccess { transformedText ->
                withContext(Dispatchers.Main) {
                    if (!isCandidateTranslationRequestCurrent(requestId)) return@withContext
                    if (resolveCurrentPreEditText() != expectedPreEditSnapshot) {
                        finishCandidateTranslation(requestId)
                        showToastMessage(getString(R.string.candidate_translation_cancelled_context_changed))
                        return@withContext
                    }
                    finishCandidateTranslation(requestId)
                    replaceCandidateWithGemmaResult(
                        originalCandidate = candidate,
                        candidatePosition = candidatePosition,
                        transformedText = transformedText,
                        resultCandidateType = resultCandidateType
                    )
                }
            }.onFailure { error ->
                Timber.e(error, "Gemma candidate action failed.")
                withContext(Dispatchers.Main) {
                    if (!isCandidateTranslationRequestCurrent(requestId)) return@withContext
                    finishCandidateTranslation(requestId)
                    if (error is CancellationException) return@withContext
                    showToastMessage(resolveThrowableMessage(error, failureMessage))
                }
            }
        }
    }

    private fun resolveCurrentPreEditText(): String {
        bunsetsuConversionSession?.let { session ->
            return session.segments.joinToString(separator = "") { it.displayText } + session.tailText
        }

        if (isHenkan.get()) {
            val suggestions = suggestionAdapter?.suggestions.orEmpty()
            if (suggestions.isNotEmpty()) {
                val selectedIndex = if (suggestionClickNum <= 0) {
                    0
                } else {
                    (suggestionClickNum - 1).coerceAtMost(suggestions.lastIndex)
                }
                return getCandidateCommitString(suggestions[selectedIndex]) + stringInTail.get()
            }
        }

        return inputString.value + stringInTail.get()
    }

    private fun isCandidateTranslationRequestCurrent(requestId: Long): Boolean {
        return candidateTranslationRequestId.get() == requestId
    }

    private fun setCandidateTranslationProgressVisible(isVisible: Boolean) {
        setSuggestionProgressVisible(
            reason = SuggestionProgressReason.CandidateTranslation,
            visible = isVisible
        )
    }

    private fun setSuggestionProgressVisible(
        reason: SuggestionProgressReason,
        visible: Boolean
    ) {
        if (visible) {
            suggestionProgressReasons += reason
        } else {
            suggestionProgressReasons -= reason
        }
        refreshSuggestionProgressVisibility()
    }

    private fun refreshSuggestionProgressVisibility() {
        mainLayoutBinding?.suggestionProgressbar?.isVisible =
            suggestionProgressReasons.isNotEmpty()
    }

    private fun finishCandidateTranslation(requestId: Long) {
        if (!isCandidateTranslationRequestCurrent(requestId)) return
        candidateTranslationJob = null
        candidateTranslationContextSnapshot = null
        setCandidateTranslationProgressVisible(false)
    }

    private fun cancelActiveCandidateTranslation() {
        val currentJob = candidateTranslationJob
        val hasActiveTranslation =
            currentJob?.isActive == true || candidateTranslationContextSnapshot != null
        if (!hasActiveTranslation) return
        candidateTranslationRequestId.incrementAndGet()
        candidateTranslationJob = null
        candidateTranslationContextSnapshot = null
        setCandidateTranslationProgressVisible(false)
        gemmaTranslationManager.cancelActiveTranslation()
        currentJob?.cancel(CancellationException("Candidate translation cancelled."))
    }

    private fun cancelCandidateTranslationIfComposingChanges(nextText: CharSequence?) {
        val snapshot = candidateTranslationContextSnapshot ?: return
        val nextValue = nextText?.toString().orEmpty()
        if (nextValue == snapshot) return
        cancelActiveCandidateTranslation()
    }

    private fun cancelCandidateTranslationIfPreEditMutates() {
        if (candidateTranslationContextSnapshot == null) return
        cancelActiveCandidateTranslation()
    }

    private fun replaceCandidateWithGemmaResult(
        originalCandidate: Candidate,
        candidatePosition: Int,
        transformedText: String,
        resultCandidateType: Int
    ) {
        val updatedCandidate = originalCandidate.copy(
            string = transformedText,
            type = resultCandidateType.toByte()
        )

        suggestionAdapter?.let { adapter ->
            adapter.suggestions = replaceCandidateInList(
                currentList = adapter.suggestions,
                originalCandidate = originalCandidate,
                candidatePosition = candidatePosition,
                translatedCandidate = updatedCandidate
            )
        }

        suggestionAdapterFull?.let { adapter ->
            adapter.suggestions = replaceCandidateInList(
                currentList = adapter.suggestions,
                originalCandidate = originalCandidate,
                candidatePosition = candidatePosition,
                translatedCandidate = updatedCandidate
            )
        }

        reflectGemmaResultInPreEdit(
            originalCandidate = originalCandidate,
            translatedCandidate = updatedCandidate,
            candidatePosition = candidatePosition
        )
    }

    private fun replaceCandidateInList(
        currentList: List<Candidate>,
        originalCandidate: Candidate,
        candidatePosition: Int,
        translatedCandidate: Candidate
    ): List<Candidate> {
        if (candidatePosition !in currentList.indices) return currentList
        if (currentList[candidatePosition] != originalCandidate) return currentList
        return currentList.toMutableList().apply {
            this[candidatePosition] = translatedCandidate
        }
    }

    private fun reflectGemmaResultInPreEdit(
        originalCandidate: Candidate,
        translatedCandidate: Candidate,
        candidatePosition: Int
    ) {
        val safePosition = candidatePosition.coerceAtLeast(0)
        suggestionClickNum = safePosition + 1
        suggestionAdapter?.updateHighlightPosition(safePosition)
        suggestionAdapterFull?.updateHighlightPosition(safePosition)

        val mainView = mainLayoutBinding
        val session = bunsetsuConversionSession
        if (mainView != null &&
            session != null &&
            isBunsetsuCursorMoveSessionActive() &&
            session.segments.isNotEmpty()
        ) {
            val focusedIndex = session.focusedIndex.coerceIn(0, session.segments.lastIndex)
            val targetSegment = session.segments[focusedIndex]
            val updatedCandidates = replaceCandidateInList(
                currentList = targetSegment.candidates,
                originalCandidate = originalCandidate,
                candidatePosition = candidatePosition,
                translatedCandidate = translatedCandidate
            )
            val selectedIndex = safePosition.coerceAtMost(
                (updatedCandidates.lastIndex).coerceAtLeast(0)
            )
            val updatedSegments = session.segments.toMutableList()
            updatedSegments[focusedIndex] = targetSegment.copy(
                displayText = translatedCandidate.string,
                candidates = updatedCandidates,
                selectedIndex = selectedIndex
            )
            bunsetsuConversionSession = session.copy(segments = updatedSegments)
            renderBunsetsuConversionSession(mainView, floatingKeyboardBinding)
            return
        }

        applyComposingText(
            text = translatedCandidate.string + stringInTail.get(),
            highlightLength = translatedCandidate.string.length,
            backgroundColor = if (customComposingTextPreference == true) {
                inputConversionBackgroundColor
                    ?: getColor(com.kazumaproject.core.R.color.orange)
            } else {
                getColor(com.kazumaproject.core.R.color.orange)
            },
            textColor = if (customComposingTextPreference == true) {
                inputConversionTextColor
            } else {
                null
            }
        )
    }

    private fun showToastMessage(message: String) {
        scope.launch(Dispatchers.Main) {
            Toast.makeText(this@IMEService, message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun resolveThrowableMessage(error: Throwable, fallbackMessage: String): String {
        val localized = error.localizedMessage?.trim().orEmpty()
        if (localized.isNotEmpty()) return localized

        val message = error.message?.trim().orEmpty()
        if (message.isNotEmpty()) return message

        val className = error.javaClass.simpleName.trim()
        return if (className.isNotEmpty()) {
            "$fallbackMessage ($className)"
        } else {
            fallbackMessage
        }
    }

    private fun showListPopup() {
        onKeyboardSwitchLongPressUp = true
        if (inputString.value.isNotEmpty()) return

        mainLayoutBinding?.let { mainView ->
            val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
            val popupView = inflater.inflate(R.layout.popup_list_layout, mainView.root, false)

            when (keyboardThemeMode) {
                "custom" -> popupView.setDrawableSolidColor(customThemeKeyColor ?: Color.WHITE)
            }

            val listView = popupView.findViewById<ListView>(R.id.popup_listview)
            listView.choiceMode = ListView.CHOICE_MODE_SINGLE

            // --- 1) 行データを構築（内部→外部の順） ---
            val internalOrder = appPreference.keyboard_order
            val internalRows: List<RowItem.Internal> = internalOrder.map { type ->
                val title = when (type) {
                    KeyboardType.TENKEY -> "日本語 - かな"
                    KeyboardType.SUMIRE -> "スミレ入力"
                    KeyboardType.QWERTY -> "英語"
                    KeyboardType.ROMAJI -> "ローマ字入力"
                    KeyboardType.CUSTOM -> "カスタム"
                }
                RowItem.Internal(type = type, title = title)
            }

            val externalRows: List<RowItem.External> = listEnabledImeItems()
                // 任意: 音声入力など除外したい場合は filter を足す
                // .filter { it.packageName != "com.google.android.tts" }
                .map { RowItem.External(it) }

            val rows: List<RowItem> = internalRows + externalRows
            val internalCount = internalRows.size

            Timber.d("Popup rows size=${rows.size}, internalCount=$internalCount")
            Timber.d("get all IME list: [${listEnabledImeItems()}]")

            // --- 2) 2行表示アダプタ ---
            val adapter = object : ArrayAdapter<RowItem>(
                this@IMEService,
                R.layout.list_item_keyboard_switch_popup,
                android.R.id.text1,
                rows
            ) {
                override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                    val view = super.getView(position, convertView, parent)
                    val text1 = view.findViewById<TextView>(android.R.id.text1)

                    when (val item = getItem(position)!!) {
                        is RowItem.Internal -> {
                            text1.text = item.title
                        }

                        is RowItem.External -> {
                            text1.text = item.ime.label
                        }
                    }

                    // --- custom テーマ対応（選択色・文字色） ---
                    if (keyboardThemeMode == "custom") {
                        val baseBg = customThemeKeyColor ?: Color.WHITE
                        val baseText = customThemeKeyTextColor ?: Color.BLACK

                        val listParent = parent as ListView
                        val checked = listParent.isItemChecked(position)

                        if (checked) {
                            val highlightColor =
                                manipulateColor(customThemeSpecialKeyColor ?: Color.LTGRAY, 1.2f)
                            view.setBackgroundColor(highlightColor)
                            text1.setTextColor(customThemeSpecialKeyTextColor ?: baseText)
                        } else {
                            view.setBackgroundColor(baseBg)
                            text1.setTextColor(baseText)
                        }
                    }

                    return view
                }
            }

            listView.adapter = adapter
            limitListViewVisibleItems(listView, maxVisible = 5)

            // --- 3) PopupWindow ---
            keyboardSelectionPopupWindow = PopupWindow(
                popupView,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                true
            )

            // 既存の「内部キーボードの選択状態」を復元（範囲チェック必須）
            if (currentKeyboardOrder in 0 until internalCount) {
                listView.setItemChecked(currentKeyboardOrder, true)
            }

            // --- 4) クリック処理（内部→今まで通り / 外部→IME切替） ---
            listView.setOnItemClickListener { _, _, position, _ ->
                onKeyboardSwitchLongPressUp = false
                keyboardSelectionPopupWindow?.dismiss()

                when (val row = rows[position]) {
                    is RowItem.Internal -> {
                        // 既存挙動：内部キーボード切替
                        currentKeyboardOrder = position
                        if (enableShowLastShownKeyboardInRestart == true) {
                            appPreference.save_last_used_keyboard_position_preference = position
                        }

                        val nextType = row.type
                        when (nextType) {
                            KeyboardType.TENKEY -> {
                                setCurrentInputModeForSession(InputMode.ModeJapanese)
                            }

                            KeyboardType.SUMIRE -> {
                                setCurrentInputModeForSession(InputMode.ModeJapanese)
                            }

                            KeyboardType.ROMAJI -> {
                                setCurrentInputModeForSession(InputMode.ModeJapanese)
                                setQwertySwitchNumberLayoutKeyVisibilityOnActiveSurface(false)
                            }

                            KeyboardType.QWERTY -> {
                                setCurrentInputModeForSession(InputMode.ModeEnglish)
                                setQwertySwitchNumberLayoutKeyVisibilityOnActiveSurface(false)
                            }

                            KeyboardType.CUSTOM -> { /* 任意 */
                            }
                        }

                        showKeyboard(nextType)
                        setKeyboardSizeSwitchKeyboard(mainView)
                    }

                    is RowItem.External -> {
                        // 外部IMEへ切替（API 28+ 推奨、失敗時はピッカーへ）
                        val imeId = row.ime.id
                        runCatching {
                            if (Build.VERSION.SDK_INT >= 28) {
                                switchInputMethod(imeId)
                            } else {
                                showKeyboardPicker()
                            }
                        }.onFailure {
                            showKeyboardPicker()
                        }
                    }
                }
            }

            keyboardSelectionPopupWindow?.setOnDismissListener {
                onKeyboardSwitchLongPressUp = false
            }

            keyboardSelectionPopupWindow?.let { popupWindow ->
                showPopupWindowSafely(
                    popupWindow = popupWindow,
                    anchorView = resolveShowListPopupAnchor(mainView),
                    gravity = Gravity.CENTER,
                    x = 0,
                    y = 0,
                    source = "showListPopup"
                )
            }
        }
    }

    private fun limitListViewVisibleItems(listView: ListView, maxVisible: Int) {
        val adapter = listView.adapter ?: return
        val visibleCount = minOf(maxVisible, adapter.count)
        if (visibleCount <= 0) return

        var totalHeight = 0

        // 各行を実測して合算（simple_list_item_2 等でもOK）
        for (i in 0 until visibleCount) {
            val itemView = adapter.getView(i, null, listView)

            // 幅が未確定でも高さはだいたい測れる。より厳密にしたいなら widthSpec を調整。
            itemView.measure(
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            )
            totalHeight += itemView.measuredHeight
        }

        val divider = listView.dividerHeight
        totalHeight += divider * (visibleCount - 1)
        totalHeight += listView.paddingTop + listView.paddingBottom

        listView.layoutParams = listView.layoutParams.apply {
            height = totalHeight
        }

        // スクロールバーを出したい場合（任意）
        listView.isVerticalScrollBarEnabled = true
    }

    private fun InputMethodService.listEnabledImeItems(): List<ImeItem> {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val pm = packageManager

        return imm.enabledInputMethodList
            .asSequence()
            // 自分自身を除外（任意）
            .filter { it.packageName != packageName }
            .map { imi: InputMethodInfo ->
                ImeItem(
                    id = imi.id,
                    packageName = imi.packageName,
                    settingsActivity = imi.settingsActivity, // null のIMEもある
                    label = imi.loadLabel(pm)
                )
            }
            .sortedBy { it.label.toString() }
            .toList()
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


    private fun showUserTemplateListPopup() {
        onKeyboardSwitchLongPressUp = true
        if (inputString.value.isNotEmpty()) return

        mainLayoutBinding?.let { mainView ->
            ioScope.launch {
                val templates = userTemplateRepository.allTemplatesSuspend()
                val templateNames = templates.map { it.word }

                withContext(Dispatchers.Main) {
                    val inflater =
                        getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
                    val popupView =
                        inflater.inflate(R.layout.popup_list_layout, mainView.root, false)
                    val listView = popupView.findViewById<ListView>(R.id.popup_listview)

                    listView.choiceMode = ListView.CHOICE_MODE_SINGLE

                    val adapter = ArrayAdapter(
                        this@IMEService, R.layout.list_item_layout, templateNames
                    )
                    listView.adapter = adapter

                    keyboardSelectionPopupWindow = PopupWindow(
                        popupView,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        true // Focusable
                    )

                    listView.setOnItemClickListener { _, _, position, _ ->
                        val selectedTemplate = templates[position]
                        val textToCommit = selectedTemplate.word
                        commitText(textToCommit, 1)

                        keyboardSelectionPopupWindow?.dismiss()
                    }

                    keyboardSelectionPopupWindow?.setOnDismissListener {
                        onKeyboardSwitchLongPressUp = false
                    }

                    keyboardSelectionPopupWindow?.showAsDropDown(mainView.shortcutToolbarRecyclerview)
                }
            }
        }
    }

    private fun showCurrentDateListPopup() {
        onKeyboardSwitchLongPressUp = true
        if (inputString.value.isNotEmpty()) return
        val calendar = Calendar.getInstance()
        mainLayoutBinding?.let { mainView ->
            ioScope.launch {
                val currentDates = createDateStrings(calendar)

                withContext(Dispatchers.Main) {
                    val inflater =
                        getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
                    val popupView =
                        inflater.inflate(R.layout.popup_list_layout, mainView.root, false)
                    val listView = popupView.findViewById<ListView>(R.id.popup_listview)

                    listView.choiceMode = ListView.CHOICE_MODE_SINGLE

                    val adapter = ArrayAdapter(
                        this@IMEService, R.layout.list_item_layout, currentDates
                    )
                    listView.adapter = adapter

                    keyboardSelectionPopupWindow = PopupWindow(
                        popupView,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        true // Focusable
                    )

                    listView.setOnItemClickListener { _, _, position, _ ->
                        val selectedDates = currentDates[position]
                        commitText(selectedDates, 1)

                        keyboardSelectionPopupWindow?.dismiss()
                    }

                    keyboardSelectionPopupWindow?.setOnDismissListener {
                        onKeyboardSwitchLongPressUp = false
                    }

                    keyboardSelectionPopupWindow?.showAsDropDown(mainView.shortcutToolbarRecyclerview)
                }
            }
        }
    }

    private fun createDateStrings(calendar: Calendar): List<String> {
        val formatter1 = SimpleDateFormat("M/d", Locale.getDefault())
        val formatter2 = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault())
        val formatter3 = SimpleDateFormat("M月d日(EEE)", Locale.getDefault())
        val formatterReiwa =
            "令和${calendar.get(Calendar.YEAR) - 2018}年${calendar.get(Calendar.MONTH) + 1}月${
                calendar.get(Calendar.DAY_OF_MONTH)
            }日"
        val formatterR06 = "R${calendar.get(Calendar.YEAR) - 2018}/${
            String.format(
                Locale.getDefault(), "%02d", calendar.get(Calendar.MONTH) + 1
            )
        }/${String.format(Locale.getDefault(), "%02d", calendar.get(Calendar.DAY_OF_MONTH))}"
        val dayOfWeek = SimpleDateFormat("EEEE", Locale.getDefault()).format(calendar.time)

        // Candidateオブジェクトを作成せず、文字列を直接リストにして返す
        return listOf(
            formatter1.format(calendar.time),  // M/d
            formatter2.format(calendar.time),  // yyyy/MM/dd
            formatter3.format(calendar.time),  // M月d日(EEE)
            formatterReiwa,                    // 令和n年M月d日
            formatterR06,                      // Rxx/MM/dd
            dayOfWeek                          // EEEE (曜日)
        )
    }

    private fun handleDeleteLongPress() {
        if (isHenkan.get()) {
            cancelHenkanByLongPressDeleteKey()
            hasConvertedKatakana = isLiveConversionEnable == true
            bunsetusMultipleDetect = false
        } else {
            onDeleteLongPressUp.set(true)
            deleteLongPress()
            _dakutenPressed.value = false
            englishSpaceKeyPressed.set(false)
            deleteKeyLongKeyPressed.set(true)
        }
    }

    private fun handleSpaceLongAction() {
        Timber.d("SideKeySpace LongPress: ${cursorMoveMode.value} $isSpaceKeyLongPressed")
        if (switchBunsetsuSplitPattern()) {
            isSpaceKeyLongPressed = true
            return
        }
        val insertString = inputString.value
        if (insertString.isNotEmpty()) {
            if (zenzEnableLongPressConversionPreference == true) {
                scope.launch {
                    filteredCandidateList = suggestionAdapter?.suggestions
                    val candidates = performZenzRequest(insertString)
                    _zenzCandidates.update { candidates }
                }
                isSpaceKeyLongPressed = true
            } else {
                if (conversionKeySwipePreference == true) {
                    if (!isHenkan.get()) {
                        _cursorMoveMode.update { true }
                        isSpaceKeyLongPressed = true
                    }
                } else {
                    mainLayoutBinding?.let {
                        if (currentInputModeForSession == InputMode.ModeJapanese) {
                            if (isHenkan.get()) return
                            isSpaceKeyLongPressed = true
                            if (hasConvertedKatakana) {
                                if (isLiveConversionEnable == true) {
                                    applyFirstSuggestion(
                                        Candidate(
                                            string = insertString.hiraganaToKatakana(),
                                            type = (3).toByte(),
                                            length = insertString.length.toUByte(),
                                            score = 4000
                                        )
                                    )
                                } else {
                                    applyFirstSuggestion(
                                        Candidate(
                                            string = insertString,
                                            type = (3).toByte(),
                                            length = insertString.length.toUByte(),
                                            score = 4000
                                        )
                                    )
                                }
                            } else {
                                if (isLiveConversionEnable == true) {
                                    applyFirstSuggestion(
                                        Candidate(
                                            string = insertString,
                                            type = (3).toByte(),
                                            length = insertString.length.toUByte(),
                                            score = 4000
                                        )
                                    )
                                } else {
                                    applyFirstSuggestion(
                                        Candidate(
                                            string = insertString.hiraganaToKatakana(),
                                            type = (3).toByte(),
                                            length = insertString.length.toUByte(),
                                            score = 4000
                                        )
                                    )
                                }
                            }
                            hasConvertedKatakana = !hasConvertedKatakana
                        }
                    }
                }
            }

        } else {
            _cursorMoveMode.update { true }
            isSpaceKeyLongPressed = true
        }
        Timber.d("SideKeySpace LongPress after: ${cursorMoveMode.value} $isSpaceKeyLongPressed")
    }

    private fun handleSpaceLongActionSumire() {
        Timber.d("SideKeySpace LongPress: ${cursorMoveMode.value} $isSpaceKeyLongPressed")
        if (switchBunsetsuSplitPattern()) {
            isSpaceKeyLongPressed = true
            return
        }
        val insertString = inputString.value
        if (insertString.isNotEmpty()) {
            mainLayoutBinding?.let {
                if (currentInputModeForSession == InputMode.ModeJapanese) {
                    if (isHenkan.get()) return
                    isSpaceKeyLongPressed = true
                    if (hasConvertedKatakana) {
                        if (isLiveConversionEnable == true) {
                            applyFirstSuggestion(
                                Candidate(
                                    string = insertString.hiraganaToKatakana(),
                                    type = (3).toByte(),
                                    length = insertString.length.toUByte(),
                                    score = 4000
                                )
                            )
                        } else {
                            applyFirstSuggestion(
                                Candidate(
                                    string = insertString,
                                    type = (3).toByte(),
                                    length = insertString.length.toUByte(),
                                    score = 4000
                                )
                            )
                        }
                    } else {
                        if (isLiveConversionEnable == true) {
                            applyFirstSuggestion(
                                Candidate(
                                    string = insertString,
                                    type = (3).toByte(),
                                    length = insertString.length.toUByte(),
                                    score = 4000
                                )
                            )
                        } else {
                            applyFirstSuggestion(
                                Candidate(
                                    string = insertString.hiraganaToKatakana(),
                                    type = (3).toByte(),
                                    length = insertString.length.toUByte(),
                                    score = 4000
                                )
                            )
                        }
                    }
                    hasConvertedKatakana = !hasConvertedKatakana
                }
            }
        } else if (insertString.isEmpty() && stringInTail.get().isEmpty()) {
            _cursorMoveMode.update { true }
            isSpaceKeyLongPressed = true
        }
        Timber.d("SideKeySpace LongPress after: ${cursorMoveMode.value} $isSpaceKeyLongPressed")
    }

    private fun handleSpaceLongActionFloating() {
        Timber.d("SideKeySpace LongPress Floting: ${cursorMoveMode.value} $isSpaceKeyLongPressed")
        if (switchBunsetsuSplitPattern(floatingKeyboardLayoutBinding = floatingKeyboardBinding)) {
            isSpaceKeyLongPressed = true
            return
        }
        val insertString = inputString.value
        if (insertString.isNotEmpty()) {
            floatingKeyboardBinding?.let {
                if (it.keyboardViewFloating.currentInputMode.value == InputMode.ModeJapanese) {
                    if (isHenkan.get()) return
                    isSpaceKeyLongPressed = true
                    if (hasConvertedKatakana) {
                        if (isLiveConversionEnable == true) {
                            applyFirstSuggestion(
                                Candidate(
                                    string = insertString.hiraganaToKatakana(),
                                    type = (3).toByte(),
                                    length = insertString.length.toUByte(),
                                    score = 4000
                                )
                            )
                        } else {
                            applyFirstSuggestion(
                                Candidate(
                                    string = insertString,
                                    type = (3).toByte(),
                                    length = insertString.length.toUByte(),
                                    score = 4000
                                )
                            )
                        }
                    } else {
                        if (isLiveConversionEnable == true) {
                            applyFirstSuggestion(
                                Candidate(
                                    string = insertString,
                                    type = (3).toByte(),
                                    length = insertString.length.toUByte(),
                                    score = 4000
                                )
                            )
                        } else {
                            applyFirstSuggestion(
                                Candidate(
                                    string = insertString.hiraganaToKatakana(),
                                    type = (3).toByte(),
                                    length = insertString.length.toUByte(),
                                    score = 4000
                                )
                            )
                        }
                    }
                    hasConvertedKatakana = !hasConvertedKatakana
                }
            }
        } else if (insertString.isEmpty() && stringInTail.get().isEmpty()) {
            _cursorMoveMode.update { true }
            isSpaceKeyLongPressed = true
        }
        Timber.d("SideKeySpace LongPress Floating after: ${cursorMoveMode.value} $isSpaceKeyLongPressed")
    }

    /**
     * 全てのキーボードビューを確実に非表示にする
     */
    private fun hideAllKeyboards() {
        mainLayoutBinding?.apply {
            keyboardView.isVisible = false
            qwertyView.isVisible = false
            tabletView.isVisible = false
            customLayoutDefault.isVisible = false
            keyboardSymbolView.isVisible = false
            candidatesRowView.isVisible = false
        }
    }

    /**
     * 指定されたキーボードを表示するための統一された関数
     */
    private fun showKeyboard(type: KeyboardType) {
        if (currentInputType in numberTypes) {
            showNumberKeyboardForCurrentInputType()
            return
        }
        hideAllKeyboards()
        val resolvedType = resolveKeyboardTypeForCurrentOrientation(type)
        Timber.d("showKeyboard called: requested=$type resolved=$resolvedType")
        mainLayoutBinding?.apply {
            when (resolvedType) {
                KeyboardType.TENKEY -> {
                    if (qwertyMode.value != TenKeyQWERTYMode.Number) {
                        if (isTabletGojuonSurface()) {
                            tabletView.isVisible = true
                            tabletView.resetLayout()
                            keyboardView.isVisible = false
                        } else {
                            keyboardView.isVisible = true
                            tabletView.isVisible = false
                        }
                        _tenKeyQWERTYMode.update { TenKeyQWERTYMode.Default }
                    } else {
                        customKeyboardMode = KeyboardInputMode.HIRAGANA
                        customLayoutDefault.isVisible = true
                        setCurrentInputModeForSession(InputMode.ModeNumber)
                        setNumberLayoutTo(customLayoutDefault)
                        qwertyView.isVisible = false
                        keyboardView.isVisible = false
                    }
                }

                KeyboardType.QWERTY -> {
                    if (qwertyMode.value != TenKeyQWERTYMode.Number) {
                        qwertyView.isVisible = true
                        keyboardView.isVisible = false
                        customLayoutDefault.isVisible = false
                        _tenKeyQWERTYMode.update { TenKeyQWERTYMode.TenKeyQWERTY }
                        setCurrentInputModeForSession(InputMode.ModeEnglish)
                        setCurrentQwertyRomajiModeForSession(false)
                        val qwertyEnterKeyText = currentInputType.getQWERTYReturnTextInEn()
                        qwertyView.resetQWERTYKeyboard(qwertyEnterKeyText)
                    } else {
                        customKeyboardMode = KeyboardInputMode.HIRAGANA
                        customLayoutDefault.isVisible = true
                        setCurrentInputModeForSession(InputMode.ModeNumber)
                        setNumberLayoutTo(customLayoutDefault)
                        qwertyView.isVisible = false
                        keyboardView.isVisible = false
                    }
                }

                KeyboardType.ROMAJI -> {
                    if (qwertyMode.value != TenKeyQWERTYMode.Number) {
                        qwertyView.isVisible = true
                        keyboardView.isVisible = false
                        customLayoutDefault.isVisible = false
                        _tenKeyQWERTYMode.update { TenKeyQWERTYMode.TenKeyQWERTYRomaji }
                        setCurrentInputModeForSession(InputMode.ModeJapanese)
                        setCurrentQwertyRomajiModeForSession(true)
                        val qwertyEnterKeyText = currentInputType.getQWERTYReturnTextInJp()
                        qwertyView.setRomajiKeyboard(
                            qwertyEnterKeyText
                        )
                        qwertyView.setRomajiEnglishSwitchKeyVisibility(true)
                    } else {
                        customKeyboardMode = KeyboardInputMode.HIRAGANA
                        customLayoutDefault.isVisible = true
                        setCurrentInputModeForSession(InputMode.ModeNumber)
                        setNumberLayoutTo(customLayoutDefault)
                        qwertyView.isVisible = false
                        keyboardView.isVisible = false
                    }
                }

                KeyboardType.SUMIRE -> {
                    Timber.d("showKeyboard keyboard: $currentInputModeForSession [$customKeyboardMode]")
                    if (sumireEnglishQwertyPreference == true && customKeyboardMode == KeyboardInputMode.ENGLISH) {
                        if (qwertyMode.value != TenKeyQWERTYMode.Number) {
                            _tenKeyQWERTYMode.update { TenKeyQWERTYMode.TenKeyQWERTY }
                            setQwertySwitchNumberLayoutKeyVisibilityOnActiveSurface(true)
                            setCurrentQwertyRomajiModeForSession(false)
                            setKeyboardSizeSwitchKeyboard(this)
                            previousTenKeyQWERTYMode = TenKeyQWERTYMode.Sumire
                            qwertyView.isVisible = true
                            customLayoutDefault.isVisible = false
                            keyboardView.isVisible = false
                        } else {
                            customLayoutDefault.isVisible = true
                            setNumberLayoutTo(customLayoutDefault)
                            qwertyView.isVisible = false
                            keyboardView.isVisible = false

                            customKeyboardMode = when (currentInputModeForSession) {
                                InputMode.ModeJapanese -> {
                                    KeyboardInputMode.HIRAGANA
                                }

                                InputMode.ModeEnglish -> {
                                    KeyboardInputMode.ENGLISH
                                }

                                InputMode.ModeNumber -> {
                                    KeyboardInputMode.SYMBOLS
                                }
                            }
                        }
                    } else {
                        customLayoutDefault.isVisible = true
                        if (qwertyMode.value != TenKeyQWERTYMode.Number) {
                            currentEnterKeyIndex = currentInputType.getEnterKeyIndexSumire()
                            _tenKeyQWERTYMode.update { TenKeyQWERTYMode.Sumire }
                            setSumireLayoutTo(customLayoutDefault)
                        } else {
                            setNumberLayoutTo(customLayoutDefault)
                        }
                        qwertyView.isVisible = false
                        keyboardView.isVisible = false

                        customKeyboardMode = when (currentInputModeForSession) {
                            InputMode.ModeJapanese -> {
                                KeyboardInputMode.HIRAGANA
                            }

                            InputMode.ModeEnglish -> {
                                KeyboardInputMode.ENGLISH
                            }

                            InputMode.ModeNumber -> {
                                KeyboardInputMode.SYMBOLS
                            }
                        }
                    }
                }

                KeyboardType.CUSTOM -> {
                    Timber.d("updateKeyboardLayout CUSTOM: $isFlickOnlyMode $sumireInputKeyType")
                    if (!selectInitialCustomKeyboardTab()) {
                        fallbackFromCustomKeyboardIfNeeded()
                        return@apply
                    }
                    if (qwertyMode.value != TenKeyQWERTYMode.Number) {
                        _tenKeyQWERTYMode.update { TenKeyQWERTYMode.Custom }
                    } else {
                        setNumberLayoutTo(customLayoutDefault)
                        //_tenKeyQWERTYMode.update { TenKeyQWERTYMode.Number }
                    }
                    customLayoutDefault.isVisible = true
                    setCurrentInputModeForSession(InputMode.ModeJapanese)
                    qwertyView.isVisible = false
                    keyboardView.isVisible = false
                }
            }
            updateUpperAreaVisibility(this)
            syncFloatingKeyboardContentForMode(qwertyMode.value)
            renderCurrentKeyboardStateOnActiveSurface()
            updateFloatingKeyboardSizeForMode(qwertyMode.value)
        }
    }

    private fun updateKeyboardLayout() {
        Timber.d("updateKeyboardLayout: ${qwertyMode.value} $currentEnterKeyIndex")
        when (qwertyMode.value) {
            TenKeyQWERTYMode.Custom -> {}

            TenKeyQWERTYMode.Default -> {}
            TenKeyQWERTYMode.TenKeyQWERTY -> {}
            TenKeyQWERTYMode.TenKeyQWERTYRomaji -> {}
            TenKeyQWERTYMode.Sumire -> {
                Timber.d("updateKeyboardLayout: $isFlickOnlyMode $sumireInputKeyType")
                renderDynamicKeysOnActiveSurface()
            }

            TenKeyQWERTYMode.Number -> {

            }

        }
        syncFloatingKeyboardContentForMode(qwertyMode.value)
        renderCurrentKeyboardStateOnActiveSurface()
        enforcePhysicalKeyboardUiState()
    }

    private fun applyCircularSlotActionSettings(
        layout: KeyboardLayout,
        mode: KeyboardInputMode
    ): KeyboardLayout {
        return CircularSlotActionApplier.apply(
            layout = layout,
            mode = mode,
            settings = appPreference.getCircularSlotActionSettings()
        )
    }

    private fun createNewKeyboardLayoutForSumire() {
        Timber.d("updateKeyboardLayout: ${qwertyMode.value} $currentEnterKeyIndex")
        when (qwertyMode.value) {
            TenKeyQWERTYMode.Custom -> {
                when (customKeyboardMode) {
                    KeyboardInputMode.HIRAGANA -> {
                        mainLayoutBinding?.let { mainView ->
                            if (!selectInitialCustomKeyboardTab()) {
                                fallbackFromCustomKeyboardIfNeeded()
                                return@let
                            }
                            mainView.customLayoutDefault.isVisible = true
                            setCurrentInputModeForSession(InputMode.ModeJapanese)
                            mainView.qwertyView.isVisible = false
                            mainView.keyboardView.isVisible = false
                        }
                    }

                    KeyboardInputMode.ENGLISH -> {
                        val insertString = inputString.value
                        _tenKeyQWERTYMode.update { TenKeyQWERTYMode.TenKeyQWERTY }
                        mainLayoutBinding?.let { mainView ->
                            setQwertySwitchNumberLayoutKeyVisibilityOnActiveSurface(true)
                            setCurrentQwertyRomajiModeForSession(false)
                            if (insertString.isEmpty()) {
                                setKeyboardSizeSwitchKeyboard(mainView)
                            } else {
                                setKeyboardHeightWithAdditional(mainView)
                            }
                            previousTenKeyQWERTYMode = TenKeyQWERTYMode.Custom
                        }
                    }

                    KeyboardInputMode.SYMBOLS -> {
                        setCustomLayoutOnActiveSurface(
                            KeyboardDefaultLayouts.createNumberLayout(currentDeleteKeyFlickSettings())
                        )
                    }

                }
            }

            TenKeyQWERTYMode.Default -> {}
            TenKeyQWERTYMode.TenKeyQWERTY -> {}
            TenKeyQWERTYMode.TenKeyQWERTYRomaji -> {}
            TenKeyQWERTYMode.Sumire -> {
                when (customKeyboardMode) {
                    KeyboardInputMode.HIRAGANA -> {
                        Timber.d("updateKeyboardLayout: $isFlickOnlyMode $sumireInputKeyType")
                        getActiveKeyboardSurface()?.customLayout?.let(::setSumireLayoutTo)
                    }

                    KeyboardInputMode.ENGLISH -> {
                        if (sumireEnglishQwertyPreference == true) {
                            val insertString = inputString.value
                            _tenKeyQWERTYMode.update { TenKeyQWERTYMode.TenKeyQWERTY }
                            mainLayoutBinding?.let { mainView ->
                                setQwertySwitchNumberLayoutKeyVisibilityOnActiveSurface(true)
                                setCurrentQwertyRomajiModeForSession(false)
                                updateQwertyOnActiveSurface { setDefaultView() }
                                if (insertString.isEmpty()) {
                                    setKeyboardSizeSwitchKeyboard(mainView)
                                } else {
                                    setKeyboardHeightWithAdditional(mainView)
                                }
                                previousTenKeyQWERTYMode = TenKeyQWERTYMode.Sumire
                            }
                        } else {
                            Timber.d("updateKeyboardLayout: $isFlickOnlyMode $sumireInputKeyType")
                            getActiveKeyboardSurface()?.customLayout?.let(::setSumireLayoutTo)
                        }
                    }

                    KeyboardInputMode.SYMBOLS -> {
                        Timber.d("updateKeyboardLayout: $isFlickOnlyMode $sumireInputKeyType")
                        getActiveKeyboardSurface()?.customLayout?.let(::setSumireLayoutTo)
                    }
                }
            }

            TenKeyQWERTYMode.Number -> {

            }

        }
        syncFloatingKeyboardContentForMode(qwertyMode.value)
    }

    private var isCustomLayoutRomajiMode = false
    private var isCustomLayoutDirectMode = false
    private var isCustomLayoutShiftPressed = false
    private var isCustomLayoutCapLock = false

    private fun customKeyboardInputModePersistenceKey(layoutId: Long, stableId: String): String {
        return stableId.takeIf { it.isNotBlank() } ?: layoutId.toString()
    }

    private fun resolveInitialCustomKeyboardDirectMode(
        layoutId: Long,
        stableId: String,
        defaultValue: Boolean
    ): Boolean {
        if (appPreference.remember_custom_keyboard_input_mode_preference != true) {
            return defaultValue
        }
        val key = customKeyboardInputModePersistenceKey(layoutId, stableId)
        return appPreference.getCustomKeyboardLastDirectMode(key) ?: defaultValue
    }

    private fun resolveInitialCustomKeyboardRomajiMode(
        layoutId: Long,
        stableId: String,
        defaultValue: Boolean
    ): Boolean {
        if (appPreference.remember_custom_keyboard_input_mode_preference != true) {
            return defaultValue
        }
        val key = customKeyboardInputModePersistenceKey(layoutId, stableId)
        return appPreference.getCustomKeyboardLastRomajiMode(key) ?: defaultValue
    }

    private fun persistCurrentCustomKeyboardInputModeIfEnabled() {
        if (appPreference.remember_custom_keyboard_input_mode_preference != true) {
            return
        }
        if (qwertyMode.value != TenKeyQWERTYMode.Custom) {
            return
        }
        val layout = selectedCustomKeyboardLayoutOrNull() ?: return
        val key = customKeyboardInputModePersistenceKey(
            layoutId = layout.layoutId,
            stableId = layout.stableId
        )
        appPreference.saveCustomKeyboardLastDirectMode(key, isCustomLayoutDirectMode)
        appPreference.saveCustomKeyboardLastRomajiMode(key, isCustomLayoutRomajiMode)
    }

    private fun selectedCustomKeyboardLayoutOrNull(): CustomKeyboardLayout? {
        currentCustomKeyboardStableId
            ?.let { stableId -> resolveCustomKeyboardIndexByStableId(customLayouts, stableId) }
            ?.let { index ->
                currentCustomKeyboardPosition = index
                return customLayouts[index]
            }

        return customLayouts.getOrNull(currentCustomKeyboardPosition)?.also { layout ->
            currentCustomKeyboardStableId = layout.stableId.takeIf { it.isNotBlank() }
        }
    }

    private fun isCurrentCustomKeyboardSelection(layoutId: Long, stableId: String): Boolean {
        val selected = selectedCustomKeyboardLayoutOrNull() ?: return false
        if (stableId.isNotBlank()) {
            return selected.stableId == stableId
        }
        return selected.layoutId == layoutId
    }

    private fun currentCustomKeyboardStableIdCandidate(): String? {
        currentCustomKeyboardStableId
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }
        customLayouts
            .getOrNull(currentCustomKeyboardPosition)
            ?.stableId
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }
        return appPreference.last_used_custom_keyboard_stable_id
            ?.takeIf { it.isNotBlank() }
    }

    private fun selectInitialCustomKeyboardTab(): Boolean {
        Timber.d("selectInitialCustomKeyboardTab")
        val initialSelection = resolveInitialCustomKeyboardSelection(
            layouts = customLayouts,
            rememberLast = appPreference.remember_last_custom_keyboard_preference == true,
            savedStableId = appPreference.last_used_custom_keyboard_stable_id
        ) ?: run {
            clearCurrentCustomKeyboardSelection()
            return false
        }
        selectCustomKeyboardTab(
            index = initialSelection.index,
            reason = initialSelection.reason
        )
        return true
    }

    private fun selectCustomKeyboardTab(
        index: Int,
        reason: CustomKeyboardSelectionReason
    ) {
        val layout = customLayouts.getOrNull(index) ?: run {
            Timber.d("selectCustomKeyboardTab: invalid index=$index, size=${customLayouts.size}, reason=$reason")
            return
        }
        currentCustomKeyboardPosition = index
        currentCustomKeyboardStableId = layout.stableId.takeIf { it.isNotBlank() }
        if (shouldPersistCustomKeyboardSelection(
                layout = layout,
                rememberLast = appPreference.remember_last_custom_keyboard_preference == true,
                reason = reason
            )
        ) {
            appPreference.last_used_custom_keyboard_stable_id = layout.stableId
        }
        renderCustomKeyboardLayout(layout)
    }

    private fun renderCustomKeyboardLayout(layout: CustomKeyboardLayout) {
        customKeyboardRenderJob?.cancel()
        customKeyboardRenderJob = scope.launch(Dispatchers.IO) {
            val id = layout.layoutId
            val expectedStableId = layout.stableId
            val dbLayout = runCatching { keyboardRepository.getFullLayout(id).first() }
                .getOrElse {
                    Timber.w(
                        it,
                        "renderCustomKeyboardLayout: layout disappeared id=$id stableId=$expectedStableId"
                    )
                    return@launch
                }
            Timber.d("renderCustomKeyboardLayout: $id $dbLayout")
            val finalLayout = keyboardRepository.convertLayout(dbLayout)
            Timber.d("renderCustomKeyboardLayout: ${dbLayout.isRomaji} ${finalLayout.isRomaji}")
            isCustomLayoutRomajiMode = resolveInitialCustomKeyboardRomajiMode(
                layoutId = id,
                stableId = expectedStableId,
                defaultValue = finalLayout.isRomaji
            )
            isCustomLayoutDirectMode = resolveInitialCustomKeyboardDirectMode(
                layoutId = id,
                stableId = expectedStableId,
                defaultValue = finalLayout.isDirectMode
            )
            isCustomLayoutShiftPressed = false
            isCustomLayoutCapLock = false
            withContext(Dispatchers.Main) {
                if (!isCurrentCustomKeyboardSelection(layoutId = id, stableId = expectedStableId)) {
                    Timber.d("renderCustomKeyboardLayout: skip stale render id=$id stableId=$expectedStableId")
                    return@withContext
                }
                setCustomLayoutOnAvailableSurfaces(finalLayout)
                syncCustomKeyboardToggleKeyIconsOnAvailableSurfaces()
            }
        }
    }

    private fun clearCurrentCustomKeyboardSelection() {
        currentCustomKeyboardPosition = 0
        currentCustomKeyboardStableId = null
        customKeyboardRenderJob?.cancel()
        customKeyboardRenderJob = null
    }

    private fun fallbackFromCustomKeyboardIfNeeded() {
        val fallbackType = keyboardOrder.firstOrNull { it != KeyboardType.CUSTOM }
            ?: KeyboardType.TENKEY
        Timber.d("fallbackFromCustomKeyboardIfNeeded: fallbackType=$fallbackType")
        suggestionAdapter?.updateState(TenKeyQWERTYMode.Default, emptyList())
        showKeyboard(fallbackType)
    }

    private fun onCustomKeyboardLayoutsChanged(newLayouts: List<CustomKeyboardLayout>) {
        val selectedStableId = currentCustomKeyboardStableIdCandidate()
        val previousIndex = currentCustomKeyboardPosition
        val selection = resolveCustomKeyboardSelectionAfterLayoutsChanged(
            layouts = newLayouts,
            selectedStableId = selectedStableId,
            previousIndex = previousIndex
        )

        customLayouts = newLayouts

        if (selection == null) {
            clearCurrentCustomKeyboardSelection()
            if (appPreference.remember_last_custom_keyboard_preference == true) {
                appPreference.last_used_custom_keyboard_stable_id = ""
            }
            if (qwertyMode.value == TenKeyQWERTYMode.Custom) {
                suggestionAdapter?.updateState(TenKeyQWERTYMode.Custom, emptyList())
                fallbackFromCustomKeyboardIfNeeded()
            } else if (qwertyMode.value == TenKeyQWERTYMode.Number && currentInputType in numberTypes) {
                showNumberKeyboardForCurrentInputType()
            }
            return
        }

        currentCustomKeyboardPosition = selection.index
        currentCustomKeyboardStableId = selection.stableId.takeIf { it.isNotBlank() }

        val selectedLayout = customLayouts.getOrNull(selection.index) ?: run {
            clearCurrentCustomKeyboardSelection()
            fallbackFromCustomKeyboardIfNeeded()
            return
        }

        if (shouldPersistCustomKeyboardSelection(
                layout = selectedLayout,
                rememberLast = appPreference.remember_last_custom_keyboard_preference == true,
                reason = selection.reason
            )
        ) {
            appPreference.last_used_custom_keyboard_stable_id = selectedLayout.stableId
        }

        if (qwertyMode.value == TenKeyQWERTYMode.Custom) {
            suggestionAdapter?.updateState(TenKeyQWERTYMode.Custom, customLayouts)
            renderCustomKeyboardLayout(selectedLayout)
            syncFloatingKeyboardContentForMode(qwertyMode.value)
            renderCurrentKeyboardStateOnActiveSurface()
            updateFloatingKeyboardSizeForMode(qwertyMode.value)
        } else if (qwertyMode.value == TenKeyQWERTYMode.Number && currentInputType in numberTypes) {
            showNumberKeyboardForCurrentInputType()
        }
    }

    private fun moveToCustomKeyboardByStableId(stableId: String) {
        val targetIndex = resolveCustomKeyboardIndexByStableId(customLayouts, stableId) ?: run {
            Timber.d("moveToCustomKeyboardByStableId: target not found stableId=$stableId")
            return
        }
        selectCustomKeyboardTab(
            index = targetIndex,
            reason = CustomKeyboardSelectionReason.MoveToStableId
        )
    }

    /**
     * 濁点モードを切り替えてキーボードを更新するメソッドの例
     */
    private fun setSumireKeyboardDakutenKey() {
        // 0と1を交互に切り替える
        currentDakutenKeyIndex = 1
        updateDynamicKeyOnActiveSurface("dakuten_toggle_key", currentDakutenKeyIndex)
    }

    private fun setSumireKeyboardEnterKey(index: Int) {
        currentEnterKeyIndex = index
        updateDynamicKeyOnActiveSurface("enter_key", currentEnterKeyIndex)
    }

    private fun setSumireKeyboardSpaceKey(index: Int) {
        currentSpaceKeyIndex = index
        updateDynamicKeyOnActiveSurface("space_convert_key", currentSpaceKeyIndex)
    }

    private fun setSumireKeyboardSwitchNumberAndKatakanaKey(index: Int) {
        currentKatakanaKeyIndex = index
        updateDynamicKeyOnActiveSurface("katakana_toggle_key", currentKatakanaKeyIndex)
    }

    private fun resetSumireKeyboardDakutenMode() {
        currentDakutenKeyIndex = 0
        currentEnterKeyIndex = currentInputType.getEnterKeyIndexSumire()
        currentSpaceKeyIndex = 0
        Timber.d("resetSumireKeyboardDakutenMode called: $currentEnterKeyIndex")
        renderDynamicKeysOnActiveSurface()
    }

    private fun showKeyboardPicker() {
        val inputMethodManager =
            getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        inputMethodManager.showInputMethodPicker()
    }

    private fun launchSettingsActivity(navigationRequest: String) {
        // Create the Intent to launch your MainActivity
        val intent = Intent(this, MainActivity::class.java)

        // Add the flag required to start an Activity from a Service
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        // Add the specific request as an extra
        intent.putExtra("openSettingActivity", navigationRequest)

        // Start the activity
        startActivity(intent)
    }

    // ▼▼▼ ADD THIS VARIABLE ▼▼▼
    private var customKeyboardMode = KeyboardInputMode.HIRAGANA

    private fun clearDeleteBufferWithView() {
        appPreference.undo_enable_preference?.let {
            if (it && deletedBuffer.isNotEmpty()) {
                clearDeletedBufferWithoutResetLayout()
                refreshEditHistoryUi()
            }
        }
    }

    private fun setupCustomKeyboardListeners(mainView: MainLayoutBinding) {
        configureFlickKeyboardView(mainView.customLayoutDefault, mainView, isFloatingView = false)
    }

    private fun currentTenKeyPopupViewStyle(): PopupViewStyle {
        return PopupViewStyle(
            sizeScalePercent = appPreference.tenkey_popup_size_scale_percent ?: 100,
            textSizeSp = appPreference.tenkey_popup_text_size_sp ?: 28.0f
        )
    }

    private fun currentQwertyPopupViewStyleSet(): QwertyPopupViewStyleSet {
        return QwertyPopupViewStyleSet(
            keyPreview = PopupViewStyle(
                sizeScalePercent = appPreference.qwerty_key_preview_popup_size_scale_percent ?: 100,
                textSizeSp = appPreference.qwerty_key_preview_popup_text_size_sp ?: 28.0f
            ),
            variation = PopupViewStyle(
                sizeScalePercent = appPreference.qwerty_variation_popup_size_scale_percent ?: 100,
                textSizeSp = appPreference.qwerty_variation_popup_text_size_sp ?: 28.0f
            )
        )
    }

    private fun currentFlickPopupViewStyleSet(): FlickPopupViewStyleSet {
        return FlickPopupViewStyleSet(
            directional = PopupViewStyle(
                sizeScalePercent = appPreference.flick_directional_popup_size_scale_percent ?: 100,
                textSizeSp = appPreference.flick_directional_popup_text_size_sp ?: 28.0f
            ),
            cross = PopupViewStyle(
                sizeScalePercent = appPreference.flick_cross_popup_size_scale_percent ?: 100,
                textSizeSp = appPreference.flick_cross_popup_text_size_sp ?: 18.0f
            ),
            standard = PopupViewStyle(
                sizeScalePercent = appPreference.flick_standard_popup_size_scale_percent ?: 100,
                textSizeSp = appPreference.flick_standard_popup_text_size_sp ?: 19.0f
            ),
            tfbi = PopupViewStyle(
                sizeScalePercent = appPreference.flick_tfbi_popup_size_scale_percent ?: 100,
                textSizeSp = appPreference.flick_tfbi_popup_text_size_sp ?: 20.0f
            )
        )
    }

    private fun configureFlickKeyboardView(
        flickView: FlickKeyboardView,
        mainView: MainLayoutBinding,
        isFloatingView: Boolean
    ) {
        if (isFloatingView) {
            Timber.d("Configuring floating FlickKeyboardView mirror surface")
            // Floating ON のときだけ、popup の window anchor を IME decorView (or floating root)
            // に切り替える。Floating の PopupWindow は floating の root に attach されており、
            // 各 key (anchor view) は popup から見て別 window 扱いになるため、座標計算を
            // window anchor 基準に補正する必要がある。
            flickView.setPopupWindowAnchorProvider {
                window.window?.decorView ?: floatingKeyboardBinding?.root
            }
        } else {
            // Floating OFF のときは PR 前と同じ挙動を維持する。
            // anchor provider を null にしておくことで、各 controller の resolveWindowAnchor が
            // keyAnchor 自身を window anchor としてフォールバックし、
            // popupWindow.showAtLocation も従来通り keyAnchor の window へ表示される。
            // これにより Floating OFF の通常 popup 表示位置 / 表示先が PR 前と同等になる。
            flickView.setPopupWindowAnchorProvider(null)
        }
        val isNight = currentNightMode == Configuration.UI_MODE_NIGHT_YES
        val isDark = keyboardThemeMode == "dark" || (keyboardThemeMode == "default" && isNight)
        val isCustom = keyboardThemeMode == "custom"

        val resolvedBgColor = customThemeBgColor ?: Color.WHITE
        val resolvedKeyColor = customThemeKeyColor ?: Color.WHITE
        val resolvedSpecialKeyColor = customThemeSpecialKeyColor ?: Color.GRAY
        val resolvedEnterKeyColor = customThemeEnterKeyColor ?: Color.BLUE
        val resolvedPopupBgColor = customThemePopupBgColor ?: Color.WHITE

        val resolvedKeyTextColor = if (isCustom) {
            customThemeKeyTextColor ?: if (resolvedKeyColor.isLightColor()) Color.BLACK else Color.WHITE
        } else {
            if (isDark) Color.WHITE else Color.BLACK
        }

        val resolvedSpecialKeyTextColor = if (isCustom) {
            customThemeSpecialKeyTextColor ?: if (resolvedSpecialKeyColor.isLightColor()) Color.BLACK else Color.WHITE
        } else {
            if (isDark) Color.WHITE else Color.BLACK
        }

        val resolvedEnterKeyTextColor = if (isCustom) {
            customThemeEnterKeyTextColor ?: if (resolvedEnterKeyColor.isLightColor()) Color.BLACK else Color.WHITE
        } else {
            if (isDark) Color.WHITE else Color.BLACK
        }

        val resolvedPopupTextColor = if (isCustom) {
            customThemePopupTextColor ?: if (resolvedPopupBgColor.isLightColor()) Color.BLACK else Color.WHITE
        } else {
            if (isDark) Color.WHITE else Color.BLACK
        }

        flickView.applyKeyboardTheme(
            themeMode = keyboardThemeMode ?: "default",
            currentNightMode = currentNightMode,
            isDynamicColorEnabled = DynamicColors.isDynamicColorAvailable(),
            customBgColor = resolvedBgColor,
            customKeyColor = resolvedKeyColor,
            customSpecialKeyColor = resolvedSpecialKeyColor,
            customEnterKeyColor = resolvedEnterKeyColor,
            customKeyTextColor = resolvedKeyTextColor,
            customSpecialKeyTextColor = resolvedSpecialKeyTextColor,
            customEnterKeyTextColor = resolvedEnterKeyTextColor,
            customPopupBgColor = resolvedPopupBgColor,
            customPopupTextColor = resolvedPopupTextColor,
            liquidGlassEnable = liquidGlassThemePreference ?: false,
            customBorderEnable = customKeyBorderEnablePreference ?: false,
            customBorderColor = customKeyBorderEnableColor ?: Color.BLACK,
            liquidGlassKeyAlphaEnable = liquidGlassKeyBlurRadiousPreference ?: 255,
            borderWidth = customKeyBorderWidth ?: 1,
            keyBorderEnable = keyBorderEnable ?: false,
            keyCornerRadiusDp = keyCornerRadiusDp ?: 8,
            keyPopupStyle = keyPopupStyle ?: "default"
        )

        flickView.setAngleAndRange(
            appPreference.getCircularFlickRanges(),
            circularFlickWindowScale ?: 1.0f
        )
        flickView.setCircularFlickOptions(
            directionCount = circularFlickDirectionCount
                ?: appPreference.circularFlickDirectionCount
        )

        flickView.applyKeySizing(
            keyWidthScalePercent = appPreference.flick_key_width_scale_percent ?: 160,
            keyHeightScalePercent = appPreference.flick_key_height_scale_percent ?: 160,
            iconScalePercent = appPreference.flick_key_icon_scale_percent ?: 80,
            textSizeSp = appPreference.flick_key_text_size_sp ?: 16.0f,
            specialKeyTextSizeSp = appPreference.flick_special_key_text_size_sp ?: 16.0f
        )
        flickView.applyPopupViewStyleSet(currentFlickPopupViewStyleSet())
        flickView.setFlickGuideEnabled(flickKeymapGuidePreference ?: false)
        flickView.setFlickGuideTextSizeSp(
            (flickGuideTextSizeSpPreference ?: 9).coerceIn(6, 16).toFloat()
        )
        flickView.setFlickGuideMaxCodePoints(
            (flickGuideMaxCharactersPreference ?: 1).coerceIn(1, 4)
        )

        flickView.setOnKeyboardActionListener(object :
            com.kazumaproject.custom_keyboard.view.FlickKeyboardView.OnKeyboardActionListener {

            override fun onPress(action: KeyAction) {
                if (action == KeyAction.DoNothing) return
                handleKeyPressFeedback(getKeySoundType(action))
            }

            override fun onActionLongPress(action: KeyAction) {
                if (action != KeyAction.DoNothing) {
                    vibrate()
                    clearDeleteBufferWithView()
                }
                Timber.d("onActionLongPress: $action")
                when (action) {
                    KeyAction.DoNothing -> Unit
                    KeyAction.Backspace -> {}
                    KeyAction.ChangeInputMode -> {
                        // 現在のモードに応じて次のモードを決定
                        customKeyboardMode = when (customKeyboardMode) {
                            KeyboardInputMode.HIRAGANA -> KeyboardInputMode.ENGLISH
                            KeyboardInputMode.ENGLISH -> KeyboardInputMode.SYMBOLS
                            KeyboardInputMode.SYMBOLS -> KeyboardInputMode.HIRAGANA
                        }
                        updateKeyboardLayout()
                    }

                    KeyAction.Convert, KeyAction.Space -> {
                        val insertString = inputString.value
                        if (switchBunsetsuSplitPattern()) {
                            isSpaceKeyLongPressed = true
                            return
                        }
                        if (insertString.isEmpty()) {
                            flickView.setCursorMode(true)
                        } else {
                            if (zenzEnableLongPressConversionPreference == true) {
                                scope.launch {
                                    filteredCandidateList = suggestionAdapter?.suggestions
                                    val candidates = performZenzRequest(insertString)
                                    _zenzCandidates.update { candidates }
                                }
                            } else {
                                if (conversionKeySwipePreference == true) {
                                    if (!isHenkan.get()) {
                                        flickView.setCursorMode(true)
                                    }
                                } else {
                                    handleSpaceLongActionSumire()
                                }
                            }
                        }
                    }

                    KeyAction.Copy -> {

                    }

                    KeyAction.Delete -> {
                        handleDeleteLongPress()
                    }

                    KeyAction.NewLine, KeyAction.Enter, KeyAction.Confirm -> {
                        val insertString = inputString.value
                        val suggestions = suggestionAdapter?.suggestions ?: emptyList()
                        if (insertString.isNotEmpty()) {
                            handleNonEmptyInputEnterKey(suggestions, mainView, insertString)
                        } else {
                            handleEmptyInputEnterKey(mainView)
                        }
                    }

                    is KeyAction.InputText -> {
                        if (action.text == "^_^") {
                            val insertString = keyboardCompositionTextForEditing()
                            Timber.d("InputText: emoji: $insertString")
                            if (insertString.isNotEmpty()) {
                                val sb = StringBuilder()
                                val c = insertString.last()
                                c.getDakutenFlickTop()?.let { dakutenChar ->
                                    setStringBuilderForConvertStringInHiragana(
                                        dakutenChar, sb, insertString
                                    )
                                }
                            } else if (!isSymbolPanelSearchRoutingActive()) {
                                _keyboardSymbolViewState.value = SymbolKeyboardState(
                                    isShown = !_keyboardSymbolViewState.value.isShown
                                )
                                stringInTail.set("")
                                finishComposingText()
                                setComposingText("", 0)
                            }
                        }
                    }

                    KeyAction.MoveCursorLeft -> {
                        val insertString = inputString.value
                        val suggestions = suggestionAdapter?.suggestions ?: emptyList()
                        cancelLeftLongPress()
                        cancelRightLongPress()
                        handleLeftLongPress()
                        leftCursorKeyLongKeyPressed.set(true)
                        if (selectMode.value) {
                            clearDeletedBufferWithoutResetLayout()
                        } else {
                            if (moveFocusedBunsetsuSegment(delta = -1)) {
                            } else if (isHenkan.get()) {
                                handleDeleteKeyInHenkan(suggestions, insertString)
                            } else {
                                clearDeletedBuffer()
                            }
                        }
                        refreshEditHistoryUi()
                    }

                    KeyAction.MoveCursorRight -> {
                        val insertString = inputString.value
                        val suggestions = suggestionAdapter?.suggestions ?: emptyList()
                        cancelLeftLongPress()
                        cancelRightLongPress()
                        handleRightLongPress()
                        rightCursorKeyLongKeyPressed.set(true)
                        if (selectMode.value) {
                            clearDeletedBufferWithoutResetLayout()
                        } else {
                            if (moveFocusedBunsetsuSegment(delta = 1)) {
                            } else if (isHenkan.get()) {
                                handleJapaneseModeSpaceKey(
                                    mainView, suggestions, insertString
                                )
                            } else {
                                clearDeletedBuffer()
                            }
                        }
                        refreshEditHistoryUi()
                    }

                    KeyAction.Paste -> {}
                    KeyAction.SelectAll -> {}
                    KeyAction.SelectLeft -> {}
                    KeyAction.SelectRight -> {}
                    KeyAction.ShowEmojiKeyboard -> {}

                    KeyAction.SwitchToNextIme -> {
                        showListPopup()
                    }

                    KeyAction.ToggleCase -> {}
                    KeyAction.ToggleDakuten -> {}
                    KeyAction.ToggleDakutenOnly -> {}
                    KeyAction.ToggleHandakutenOnly -> {}
                    KeyAction.SwitchToEnglishLayout -> {
                        customKeyboardMode = KeyboardInputMode.ENGLISH
                        createNewKeyboardLayoutForSumire()
                        val inputMode = InputMode.ModeEnglish
                        setCurrentInputModeForSession(inputMode)
                    }

                    KeyAction.SwitchToKanaLayout -> {
                        customKeyboardMode = KeyboardInputMode.HIRAGANA
                        createNewKeyboardLayoutForSumire()
                        val inputMode = InputMode.ModeJapanese
                        setCurrentInputModeForSession(inputMode)
                    }

                    KeyAction.SwitchToNumberLayout -> {
                        customKeyboardMode = KeyboardInputMode.SYMBOLS
                        createNewKeyboardLayoutForSumire()
                        val inputMode = InputMode.ModeNumber
                        setCurrentInputModeForSession(inputMode)
                    }

                    KeyAction.ShiftKey -> {}
                    KeyAction.MoveCustomKeyboardTab -> {}
                    is KeyAction.MoveToCustomKeyboard -> {}
                    KeyAction.ToggleKatakana -> {}
                    KeyAction.DeleteUntilSymbol -> {}
                    KeyAction.MoveCursorDown -> {
                        if (cycleFocusedBunsetsuCandidate(delta = 1)) {
                            refreshEditHistoryUi()
                        }
                    }

                    KeyAction.MoveCursorUp -> {
                        if (cycleFocusedBunsetsuCandidate(delta = -1)) {
                            refreshEditHistoryUi()
                        }
                    }

                    KeyAction.Cancel -> {}
                    KeyAction.VoiceInput -> {}
                    is KeyAction.Text -> Unit
                    KeyAction.DeleteAfterCursorUntilSymbol -> {}
                    KeyAction.UndoLastDelete -> {}
                    KeyAction.SwitchRomajiEnglish -> {}
                    KeyAction.ForceNewLine -> {
                        val insertString = inputString.value
                        val suggestions = suggestionAdapter?.suggestions ?: emptyList()
                        if (insertString.isEmpty()) {
                            forceNewLine(mainView)
                        } else {
                            handleNonEmptyInputEnterKey(suggestions, mainView, insertString)
                        }
                    }

                    KeyAction.SwitchDirectMode -> {}
                    KeyAction.CapLockKey -> {}
                    KeyAction.ForceHalfWidthSpace -> {}
                    KeyAction.ForceFullWidthSpace -> {}
                }
            }

            override fun onActionUpAfterLongPress(action: KeyAction) {
                Timber.d("onActionUpAfterLongPress: $action")
                when (action) {
                    KeyAction.DoNothing -> Unit
                    KeyAction.Backspace -> {}
                    KeyAction.ChangeInputMode -> {}
                    KeyAction.Confirm -> {}
                    KeyAction.Convert, KeyAction.Space -> {
                        isSpaceKeyLongPressed = false
                    }

                    KeyAction.Copy -> {}
                    KeyAction.Delete -> {
                        stopDeleteLongPress()
                    }

                    KeyAction.Enter -> {}
                    is KeyAction.InputText -> {}
                    KeyAction.MoveCursorLeft -> {
                        cancelLeftLongPress()
                        cancelRightLongPress()
                    }

                    KeyAction.MoveCursorRight -> {
                        cancelLeftLongPress()
                        cancelRightLongPress()
                    }

                    KeyAction.NewLine -> {}
                    KeyAction.Paste -> {}
                    KeyAction.SelectAll -> {}
                    KeyAction.SelectLeft -> {}
                    KeyAction.SelectRight -> {}
                    KeyAction.ShowEmojiKeyboard -> {}
                    KeyAction.SwitchToNextIme -> {}
                    KeyAction.ToggleCase -> {}
                    KeyAction.ToggleDakuten -> {}
                    KeyAction.ToggleDakutenOnly -> {}
                    KeyAction.ToggleHandakutenOnly -> {}
                    KeyAction.SwitchToEnglishLayout -> {}
                    KeyAction.SwitchToKanaLayout -> {}
                    KeyAction.SwitchToNumberLayout -> {}
                    KeyAction.ShiftKey -> {}
                    KeyAction.MoveCustomKeyboardTab -> {}
                    is KeyAction.MoveToCustomKeyboard -> {}
                    KeyAction.ToggleKatakana -> {}
                    KeyAction.DeleteUntilSymbol -> {}
                    KeyAction.MoveCursorDown -> {}
                    KeyAction.MoveCursorUp -> {}
                    KeyAction.Cancel -> {
                        stopDeleteLongPress()
                        cancelLeftLongPress()
                        cancelRightLongPress()
                    }

                    KeyAction.VoiceInput -> {}
                    is KeyAction.Text -> Unit
                    KeyAction.DeleteAfterCursorUntilSymbol -> {}
                    KeyAction.UndoLastDelete -> {}
                    KeyAction.ForceNewLine -> {}
                    KeyAction.SwitchDirectMode -> {}
                    KeyAction.SwitchRomajiEnglish -> {}
                    KeyAction.CapLockKey -> {}
                    KeyAction.ForceHalfWidthSpace -> {
                        handleForceHalfWidthSpaceOrConvert(
                            mainView,
                            floatingKeyboardBinding.takeIf { isFloatingView })
                    }

                    KeyAction.ForceFullWidthSpace -> {
                        handleForceFullWidthSpaceOrConvert(
                            mainView,
                            floatingKeyboardBinding.takeIf { isFloatingView })
                    }
                }
            }

            override fun onFlickDirectionChanged(direction: FlickDirection) {
                vibrate()
                Timber.d("onFlickDirectionChanged: $direction")
            }

            override fun onFlickActionLongPress(action: KeyAction) {
                Timber.d("onFlickActionLongPress: $action")
                if (action != KeyAction.DoNothing) vibrate()
                when (action) {
                    KeyAction.DoNothing -> Unit
                    KeyAction.Backspace -> {}
                    KeyAction.ChangeInputMode -> {}
                    KeyAction.Confirm -> {}
                    KeyAction.Convert -> {
                        if (switchBunsetsuSplitPattern()) {
                            isSpaceKeyLongPressed = true
                            return
                        }
                        if (zenzEnableLongPressConversionPreference == true) {
                            val insertString = inputString.value
                            scope.launch {
                                filteredCandidateList = suggestionAdapter?.suggestions
                                val candidates = performZenzRequest(insertString)
                                _zenzCandidates.update { candidates }
                            }
                        } else {
                            if (conversionKeySwipePreference == true) {
                                if (!isHenkan.get()) {
                                    flickView.setCursorMode(true)
                                }
                            } else {
                                handleSpaceLongActionSumire()
                            }
                        }
                    }

                    KeyAction.Copy -> {
                        val selectedText = getSelectedText(0)
                        if (!selectedText.isNullOrEmpty()) {
                            copySelectedTextToClipboard(selectedText)
                        }
                    }

                    KeyAction.Delete -> {
                        handleDeleteLongPress()
                    }

                    KeyAction.Enter -> {}
                    is KeyAction.InputText -> {}
                    KeyAction.MoveCursorLeft -> {
                        cancelLeftLongPress()
                        cancelRightLongPress()
                        handleLeftLongPress()
                        leftCursorKeyLongKeyPressed.set(true)
                        if (selectMode.value) {
                            clearDeletedBufferWithoutResetLayout()
                        } else {
                            clearDeletedBuffer()
                        }
                        refreshEditHistoryUi()
                    }

                    KeyAction.MoveCursorRight -> {
                        cancelLeftLongPress()
                        cancelRightLongPress()
                        handleRightLongPress()
                        rightCursorKeyLongKeyPressed.set(true)
                        if (selectMode.value) {
                            clearDeletedBufferWithoutResetLayout()
                        } else {
                            clearDeletedBuffer()
                        }
                        refreshEditHistoryUi()
                    }

                    KeyAction.NewLine -> {}
                    KeyAction.Paste -> {
                        pasteAction()
                    }

                    KeyAction.SelectAll -> {
                        selectAllText()
                    }

                    KeyAction.SelectLeft -> {}
                    KeyAction.SelectRight -> {}
                    KeyAction.ShowEmojiKeyboard -> {}
                    KeyAction.Space -> {
                        if (switchBunsetsuSplitPattern()) {
                            isSpaceKeyLongPressed = true
                            return
                        }
                        flickView.setCursorMode(true)
                    }

                    KeyAction.SwitchToNextIme -> {
                        showListPopup()
                    }

                    KeyAction.ToggleCase -> {
                        dakutenSmallActionForSumire()
                    }

                    KeyAction.ToggleDakuten -> {
                        dakutenSmallActionForSumire()
                    }

                    KeyAction.ToggleDakutenOnly -> {}

                    KeyAction.ToggleHandakutenOnly -> {}

                    KeyAction.SwitchToEnglishLayout -> {}
                    KeyAction.SwitchToKanaLayout -> {}
                    KeyAction.SwitchToNumberLayout -> {}
                    KeyAction.ShiftKey -> {}
                    KeyAction.MoveCustomKeyboardTab -> {}
                    is KeyAction.MoveToCustomKeyboard -> {}
                    KeyAction.ToggleKatakana -> {}
                    KeyAction.DeleteUntilSymbol -> {}
                    KeyAction.MoveCursorDown -> {

                    }

                    KeyAction.MoveCursorUp -> {}
                    KeyAction.Cancel -> {}
                    KeyAction.VoiceInput -> {}
                    is KeyAction.Text -> Unit
                    KeyAction.DeleteAfterCursorUntilSymbol -> {}
                    KeyAction.UndoLastDelete -> {}
                    KeyAction.ForceNewLine -> {}
                    KeyAction.SwitchDirectMode -> {}
                    KeyAction.SwitchRomajiEnglish -> {}
                    KeyAction.CapLockKey -> {}
                    KeyAction.ForceHalfWidthSpace -> {}
                    KeyAction.ForceFullWidthSpace -> {}
                }
            }

            override fun onFlickActionUpAfterLongPress(action: KeyAction, isFlick: Boolean) {
                if (action != KeyAction.DoNothing) vibrate()
                Timber.d("onFlickActionUpAfterLongPress: $action $isFlick")
                when (action) {
                    KeyAction.DoNothing -> Unit
                    KeyAction.Backspace -> {}
                    KeyAction.ChangeInputMode -> {}
                    KeyAction.Confirm -> {}
                    KeyAction.Copy -> {}
                    KeyAction.Delete -> {
                        stopDeleteLongPress()
                    }

                    KeyAction.Enter -> {}
                    is KeyAction.InputText -> {
                        when (action.text) {
                            "ひらがな小文字" -> {
                                val insertString = keyboardCompositionTextForEditing()
                                if (insertString.isEmpty()) return
                                val sb = StringBuilder()
                                val c = insertString.last()
                                c.getDakutenFlickTop()?.let { dakutenChar ->
                                    setStringBuilderForConvertStringInHiragana(
                                        dakutenChar, sb, insertString
                                    )
                                }
                            }

                            "濁点" -> {
                                val insertString = keyboardCompositionTextForEditing()
                                if (insertString.isEmpty()) return
                                val sb = StringBuilder()
                                val c = insertString.last()
                                c.getDakutenFlickLeft()?.let { dakutenChar ->
                                    setStringBuilderForConvertStringInHiragana(
                                        dakutenChar, sb, insertString
                                    )
                                }
                            }

                            "半濁点" -> {
                                val insertString = keyboardCompositionTextForEditing()
                                if (insertString.isEmpty()) return
                                val sb = StringBuilder()
                                val c = insertString.last()
                                c.getDakutenFlickRight()?.let { dakutenChar ->
                                    setStringBuilderForConvertStringInHiragana(
                                        dakutenChar, sb, insertString
                                    )
                                }
                            }

                        }
                    }

                    KeyAction.MoveCursorLeft -> {
                        cancelLeftLongPress()
                        cancelRightLongPress()
                    }

                    KeyAction.MoveCursorRight -> {
                        cancelLeftLongPress()
                        cancelRightLongPress()
                    }

                    KeyAction.NewLine -> {}
                    KeyAction.Paste -> {}
                    KeyAction.SelectAll -> {}
                    KeyAction.SelectLeft -> {}
                    KeyAction.SelectRight -> {}
                    KeyAction.ShowEmojiKeyboard -> {}
                    KeyAction.Convert, KeyAction.Space -> {
                        flickView.setCursorMode(false)
                        isSpaceKeyLongPressed = false
                        if (inputString.value.isEmpty()) {
                            val isHankaku = hankakuPreference == true
                            val insertString = inputString.value
                            val suggestions = suggestionAdapter?.suggestions ?: emptyList()
                            if (isHankaku) {
                                if (isFlick) {
                                    handleSpaceKeyClick(false, insertString, suggestions, mainView)
                                } else {
                                    handleSpaceKeyClick(true, insertString, suggestions, mainView)
                                }
                            } else {
                                if (isFlick) {
                                    handleSpaceKeyClick(true, insertString, suggestions, mainView)
                                } else {
                                    handleSpaceKeyClick(false, insertString, suggestions, mainView)
                                }
                            }
                        }
                    }

                    KeyAction.SwitchToNextIme -> {}
                    KeyAction.ToggleCase -> {
                        dakutenSmallActionForSumire()
                    }

                    KeyAction.ToggleDakuten -> {
                        dakutenSmallActionForSumire()
                    }

                    KeyAction.ToggleDakutenOnly -> {
                        toggleDakutenOnlyForCustomKeyboard()
                    }

                    KeyAction.ToggleHandakutenOnly -> {
                        toggleHandakutenOnlyForCustomKeyboard()
                    }

                    KeyAction.SwitchToEnglishLayout -> {
                        customKeyboardMode = KeyboardInputMode.ENGLISH
                        createNewKeyboardLayoutForSumire()
                        val inputMode = InputMode.ModeEnglish
                        setCurrentInputModeForSession(inputMode)
                    }

                    KeyAction.SwitchToKanaLayout -> {
                        customKeyboardMode = KeyboardInputMode.HIRAGANA
                        createNewKeyboardLayoutForSumire()
                        val inputMode = InputMode.ModeJapanese
                        setCurrentInputModeForSession(inputMode)
                    }

                    KeyAction.SwitchToNumberLayout -> {
                        customKeyboardMode = KeyboardInputMode.SYMBOLS
                        createNewKeyboardLayoutForSumire()
                        val inputMode = InputMode.ModeNumber
                        setCurrentInputModeForSession(inputMode)
                    }

                    KeyAction.ShiftKey -> {
                        isCustomLayoutShiftPressed = !isCustomLayoutShiftPressed

                        Handler(mainLooper).post {
                            getActiveKeyboardSurface()?.customLayout?.updateKeyIconByAction(
                                KeyAction.ShiftKey,
                                if (isCustomLayoutShiftPressed) com.kazumaproject.core.R.drawable.shift_fill_24px
                                else com.kazumaproject.core.R.drawable.shift_24px
                            )
                        }
                    }

                    KeyAction.MoveCustomKeyboardTab -> {
                        scope.launch {
                            if (customLayouts.isNotEmpty()) {
                                val position =
                                    (currentCustomKeyboardPosition + 1) % customLayouts.size
                                selectCustomKeyboardTab(
                                    index = position,
                                    reason = CustomKeyboardSelectionReason.UserNextTab
                                )
                            }
                        }
                    }

                    is KeyAction.MoveToCustomKeyboard -> {
                        moveToCustomKeyboardByStableId(action.stableId)
                    }

                    KeyAction.ToggleKatakana -> {
                        when (countToggleKatakana) {
                            0 -> {
                                _inputString.update {
                                    it.hiraganaToKatakana()
                                }
                                countToggleKatakana++
                            }

                            1 -> {
                                _inputString.update {
                                    it.toHankakuKatakana()
                                }
                                countToggleKatakana++
                            }

                            2 -> {
                                _inputString.update {
                                    it.toHiragana()
                                }
                                countToggleKatakana = 0
                            }
                        }
                    }

                    KeyAction.DeleteUntilSymbol -> {
                        if (isDeleteLeftFlickPreference == true) {
                            val insertString = inputString.value
                            deleteWordOrSymbolsBeforeCursor(insertString)
                        }
                        stopDeleteLongPress()
                    }

                    KeyAction.DeleteAfterCursorUntilSymbol -> {
                        if (isDeleteUpFlickPreference == true) {
                            val insertString = inputString.value
                            deleteWordOrSymbolsAfterCursor(insertString)
                        }
                        stopDeleteLongPress()
                    }

                    KeyAction.UndoLastDelete -> {
                        if (isDeleteDownFlickPreference == true) {
                            undoLastHistoryEntry()
                        }
                        stopDeleteLongPress()
                    }

                    KeyAction.MoveCursorDown -> {
                        cancelLeftLongPress()
                        cancelRightLongPress()
                        val insertString = inputString.value
                        if (cycleFocusedBunsetsuCandidate(delta = 1)) {
                        } else if (insertString.isEmpty() && stringInTail.get().isEmpty()) {
                            sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_DOWN)
                        }
                    }

                    KeyAction.MoveCursorUp -> {
                        cancelLeftLongPress()
                        cancelRightLongPress()
                        val insertString = inputString.value
                        if (cycleFocusedBunsetsuCandidate(delta = -1)) {
                        } else if (insertString.isEmpty() && stringInTail.get().isEmpty()) {
                            sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_UP)
                        }
                    }

                    KeyAction.Cancel -> {
                        stopDeleteLongPress()
                        cancelLeftLongPress()
                        cancelRightLongPress()
                    }

                    KeyAction.VoiceInput -> {}
                    is KeyAction.Text -> Unit
                    KeyAction.ForceNewLine -> {
                        val insertString = inputString.value
                        val suggestions = suggestionAdapter?.suggestions ?: emptyList()
                        if (insertString.isEmpty()) {
                            forceNewLine(mainView)
                        } else {
                            handleNonEmptyInputEnterKey(suggestions, mainView, insertString)
                        }
                    }

                    KeyAction.SwitchDirectMode -> {
                        isCustomLayoutDirectMode = !isCustomLayoutDirectMode
                        persistCurrentCustomKeyboardInputModeIfEnabled()

                        Handler(mainLooper).post {
                            getActiveKeyboardSurface()?.customLayout?.updateKeyIconByAction(
                                KeyAction.SwitchDirectMode,
                                if (isCustomLayoutDirectMode) com.kazumaproject.core.R.drawable.language_japanese_kana_right_24px
                                else com.kazumaproject.core.R.drawable.language_japanese_kana_left_24px
                            )
                        }
                    }

                    KeyAction.SwitchRomajiEnglish -> {
                        isCustomLayoutRomajiMode = !isCustomLayoutRomajiMode
                        persistCurrentCustomKeyboardInputModeIfEnabled()
                        Handler(mainLooper).post {
                            getActiveKeyboardSurface()?.customLayout?.updateKeyIconByAction(
                                KeyAction.SwitchRomajiEnglish,
                                if (isCustomLayoutRomajiMode) com.kazumaproject.core.R.drawable.language_japanese_kana_left_bold_24px
                                else com.kazumaproject.core.R.drawable.language_japanese_kana_right_bold_24px
                            )
                        }
                    }

                    KeyAction.CapLockKey -> {
                        isCustomLayoutCapLock = !isCustomLayoutCapLock

                        Handler(mainLooper).post {
                            getActiveKeyboardSurface()?.customLayout?.updateKeyIconByAction(
                                KeyAction.CapLockKey,
                                if (isCustomLayoutCapLock) com.kazumaproject.core.R.drawable.caps_lock
                                else com.kazumaproject.core.R.drawable.caps_lock_outline
                            )
                        }
                    }

                    KeyAction.ForceHalfWidthSpace -> {
                        handleForceHalfWidthSpaceOrConvert(
                            mainView,
                            floatingKeyboardBinding.takeIf { isFloatingView })
                    }

                    KeyAction.ForceFullWidthSpace -> {
                        handleForceFullWidthSpaceOrConvert(
                            mainView,
                            floatingKeyboardBinding.takeIf { isFloatingView })
                    }
                }
            }

            override fun onAction(action: KeyAction, isFlick: Boolean) {
                if (action != KeyAction.DoNothing) vibrate()

                Timber.d("onAction: $action $isFlick")
                if (!shouldPreserveDeleteHistoryForAction(action)) {
                    clearDeleteBufferWithView()
                }
                when (action) {
                    KeyAction.DoNothing -> Unit
                    is KeyAction.Text -> {
                        val text = action.text
                        Timber.d("onAction Text: [$text] [${qwertyMode.value}] [$isDefaultRomajiHenkanMap]")
                        when (qwertyMode.value) {
                            TenKeyQWERTYMode.Custom -> {
                                if (text.isEmpty()) return
                                if (isCustomLayoutDirectMode) {
                                    val output = applyCustomLayoutShiftAndCapLock(text)
                                    finishComposingText()
                                    setComposingText("", 0)
                                    commitText(output, 1)
                                    if (isCustomLayoutShiftPressed) {
                                        isCustomLayoutShiftPressed = false
                                    }
                                    return
                                }
                                if (text.length == 1) {
                                    if (isCustomLayoutRomajiMode) {
                                        val insertString = composingPrefixForKeyboardAppend()
                                        val sb = StringBuilder()
                                        sb.append(insertString).append(text)
                                        romajiConverter?.let { converter ->
                                            if (isDefaultRomajiHenkanMap) {
                                                if (!isCustomLayoutShiftPressed && !isCustomLayoutCapLock) {
                                                    val converted = converter.convertCustomLayout(
                                                        sb.toString()
                                                    )
                                                    if (!routeSymbolPanelSearchFullText(converted)) {
                                                        _inputString.update { converted }
                                                    } else {
                                                        lastQwertyRomajiRawInput = sb.toString()
                                                    }
                                                } else {
                                                    val converted = applyCustomLayoutShiftAndCapLock(
                                                        sb.toString()
                                                    )
                                                    if (!routeSymbolPanelSearchFullText(converted)) {
                                                        _inputString.update { converted }
                                                    } else {
                                                        lastQwertyRomajiRawInput = sb.toString()
                                                    }
                                                }

                                            } else {
                                                if (customRomajiZenkakuConversionEnablePreference == true) {
                                                    val raw = sb.toString()
                                                    updateInputStringFromQwertyRomajiBuffer(
                                                        raw,
                                                        applyCustomLayoutShiftAndCapLock(
                                                            converter.convertQWERTYZenkaku(raw),
                                                        ),
                                                    )
                                                } else {
                                                    val converted = applyCustomLayoutShiftAndCapLock(
                                                        converter.convert(
                                                            sb.toString()
                                                        )
                                                    )
                                                    if (!routeSymbolPanelSearchFullText(converted)) {
                                                        _inputString.update { converted }
                                                    } else {
                                                        lastQwertyRomajiRawInput = sb.toString()
                                                    }
                                                }
                                            }
                                        }
                                    } else {
                                        handleOnKeyForSumire(
                                            applyCustomLayoutShiftAndCapLock(text),
                                            mainView,
                                            isFlick
                                        )
                                    }

                                    if (isCustomLayoutShiftPressed) {
                                        isCustomLayoutShiftPressed = false
                                    }

                                } else {
                                    if (isCustomKeyboardTwoWordsOutputEnable == true) {
                                        finishComposingText()
                                        setComposingText("", 0)
                                        commitText(text, 1)
                                    } else {
                                        if (isCustomLayoutRomajiMode) {
                                            val insertString = composingPrefixForKeyboardAppend()
                                            val sb = StringBuilder()
                                            sb.append(insertString).append(text)
                                            romajiConverter?.let { converter ->
                                                if (isDefaultRomajiHenkanMap) {
                                                    val converted = converter.convertCustomLayout(sb.toString())
                                                    if (!routeSymbolPanelSearchFullText(converted)) {
                                                        _inputString.update { converted }
                                                    } else {
                                                        lastQwertyRomajiRawInput = sb.toString()
                                                    }
                                                } else {
                                                    if (customRomajiZenkakuConversionEnablePreference == true) {
                                                        val raw = sb.toString()
                                                        updateInputStringFromQwertyRomajiBuffer(
                                                            raw,
                                                            converter.convertQWERTYZenkaku(raw),
                                                        )
                                                    } else {
                                                        val converted = converter.convert(sb.toString())
                                                        if (!routeSymbolPanelSearchFullText(converted)) {
                                                            _inputString.update { converted }
                                                        } else {
                                                            lastQwertyRomajiRawInput = sb.toString()
                                                        }
                                                    }
                                                }
                                            }
                                        } else {
                                            val insertString = composingPrefixForKeyboardAppend()
                                            val sb = StringBuilder()
                                            sb.append(insertString).append(text)
                                            val combined = sb.toString()
                                            if (!routeSymbolPanelSearchFullText(combined)) {
                                                _inputString.update { combined }
                                            }
                                        }
                                    }
                                }
                            }

                            TenKeyQWERTYMode.Sumire -> {
                                Timber.d("TenKeyQWERTYMode.Sumire: $text $isFlick")
                                handleOnKeyForSumire(text, mainView, isFlick)
                            }

                            TenKeyQWERTYMode.Number -> {
                                handleOnKeyForSumire(text, mainView, isFlick)
                            }

                            else -> {}
                        }
                    }

                    is KeyAction.InputText -> {
                        when (action.text) {
                            "^_^" -> {
                                val insertString = keyboardCompositionTextForEditing()
                                Timber.d("InputText: emoji: $insertString")
                                if (insertString.isNotEmpty()) {
                                    val sb = StringBuilder()
                                    val c = insertString.last()
                                    c.getDakutenFlickTop()?.let { dakutenChar ->
                                        setStringBuilderForConvertStringInHiragana(
                                            dakutenChar, sb, insertString
                                        )
                                    }
                                } else if (!isSymbolPanelSearchRoutingActive()) {
                                    _keyboardSymbolViewState.value = SymbolKeyboardState(
                                        isShown = !_keyboardSymbolViewState.value.isShown
                                    )
                                    stringInTail.set("")
                                    finishComposingText()
                                    setComposingText("", 0)
                                }
                            }

                            ":", "-" -> {
                                val insertString = inputString.value
                                val sb = StringBuilder()
                                sb.append(insertString).append(action.text)
                                _inputString.update {
                                    sb.toString()
                                }
                            }

                            "ひらがな小文字" -> {
                                val insertString = keyboardCompositionTextForEditing()
                                if (insertString.isEmpty()) return
                                val sb = StringBuilder()
                                val c = insertString.last()
                                c.getDakutenFlickTop()?.let { dakutenChar ->
                                    setStringBuilderForConvertStringInHiragana(
                                        dakutenChar, sb, insertString
                                    )
                                }
                            }

                            "濁点" -> {
                                val insertString = keyboardCompositionTextForEditing()
                                if (insertString.isEmpty()) return
                                val sb = StringBuilder()
                                val c = insertString.last()
                                c.getDakutenFlickLeft()?.let { dakutenChar ->
                                    setStringBuilderForConvertStringInHiragana(
                                        dakutenChar, sb, insertString
                                    )
                                }
                            }

                            "半濁点" -> {
                                val insertString = keyboardCompositionTextForEditing()
                                if (insertString.isEmpty()) return
                                val sb = StringBuilder()
                                val c = insertString.last()
                                c.getDakutenFlickRight()?.let { dakutenChar ->
                                    setStringBuilderForConvertStringInHiragana(
                                        dakutenChar, sb, insertString
                                    )
                                }
                            }

                        }
                    }

                    KeyAction.SwitchToNextIme -> {
                        if (!onKeyboardSwitchLongPressUp) {
                            switchNextKeyboard()
                            _inputString.update { "" }
                            finishComposingText()
                            setComposingText("", 0)
                        }
                    }

                    KeyAction.ChangeInputMode -> {
                        // 現在のモードに応じて次のモードを決定
                        customKeyboardMode = when (customKeyboardMode) {
                            KeyboardInputMode.HIRAGANA -> KeyboardInputMode.ENGLISH
                            KeyboardInputMode.ENGLISH -> KeyboardInputMode.SYMBOLS
                            KeyboardInputMode.SYMBOLS -> KeyboardInputMode.HIRAGANA
                        }
                        createNewKeyboardLayoutForSumire()

                        val inputMode = when (customKeyboardMode) {
                            KeyboardInputMode.HIRAGANA -> InputMode.ModeJapanese
                            KeyboardInputMode.ENGLISH -> InputMode.ModeEnglish
                            KeyboardInputMode.SYMBOLS -> InputMode.ModeNumber
                        }
                        if (isTabletGojuonSurface()) {
                            mainView.tabletView.currentInputMode.set(inputMode)
                        }
                        setCurrentInputModeForSession(inputMode)
                    }

                    KeyAction.Delete -> {
                        val insertString = inputString.value
                        val suggestions = suggestionAdapter?.suggestions ?: emptyList()
                        handleDeleteKeyTap(insertString, suggestions)
                        stopDeleteLongPress()
                    }

                    KeyAction.ForceNewLine -> {
                        val insertString = inputString.value
                        val suggestions = suggestionAdapter?.suggestions ?: emptyList()
                        if (insertString.isEmpty()) {
                            forceNewLine(mainView)
                        } else {
                            handleNonEmptyInputEnterKey(suggestions, mainView, insertString)
                        }
                    }

                    KeyAction.NewLine, KeyAction.Enter, KeyAction.Confirm -> {
                        val insertString = inputString.value
                        val suggestions = suggestionAdapter?.suggestions ?: emptyList()
                        if (insertString.isNotEmpty()) {
                            handleNonEmptyInputEnterKey(suggestions, mainView, insertString)
                        } else {
                            handleEmptyInputEnterKey(mainView)
                        }
                    }

                    KeyAction.Convert, KeyAction.Space -> {
                        val insertString = inputString.value
                        val suggestions = suggestionAdapter?.suggestions ?: emptyList()
                        if (cursorMoveMode.value) {
                            _cursorMoveMode.update { false }
                        } else {
                            if (!isSpaceKeyLongPressed) {
                                if (isFlick && cycleFocusedBunsetsuCandidate(delta = -1)) {
                                } else {
                                    val isHankaku = hankakuPreference == true
                                    if (isHankaku) {
                                        if (isFlick) {
                                            handleSpaceKeyClick(
                                                false, insertString, suggestions, mainView
                                            )
                                        } else {
                                            handleSpaceKeyClick(
                                                true, insertString, suggestions, mainView
                                            )
                                        }
                                    } else {
                                        if (isFlick) {
                                            handleSpaceKeyClick(
                                                true, insertString, suggestions, mainView
                                            )
                                        } else {
                                            handleSpaceKeyClick(
                                                false, insertString, suggestions, mainView
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        isSpaceKeyLongPressed = false
                    }

                    KeyAction.MoveCursorLeft -> {
                        val insertString = inputString.value
                        val suggestions = suggestionAdapter?.suggestions ?: emptyList()
                        if (!leftCursorKeyLongKeyPressed.get()) {
                            if (moveFocusedBunsetsuSegment(delta = -1)) {
                            } else if (isHenkan.get()) {
                                handleDeleteKeyInHenkan(suggestions, insertString)
                            } else {
                                handleLeftCursor(GestureType.Tap, insertString)
                            }
                        }
                        cancelRightLongPress()
                        cancelLeftLongPress()
                    }

                    KeyAction.MoveCursorRight -> {
                        val insertString = inputString.value
                        val suggestions = suggestionAdapter?.suggestions ?: emptyList()
                        if (!rightCursorKeyLongKeyPressed.get()) {
                            if (moveFocusedBunsetsuSegment(delta = 1)) {
                            } else if (isHenkan.get()) {
                                handleJapaneseModeSpaceKey(
                                    mainView, suggestions, insertString
                                )
                            } else {
                                actionInRightKeyPressed(GestureType.Tap, insertString)
                            }
                        }
                        cancelRightLongPress()
                        cancelLeftLongPress()
                    }

                    KeyAction.Backspace -> {}
                    KeyAction.Copy -> {
                        val selectedText = getSelectedText(0)
                        if (!selectedText.isNullOrEmpty()) {
                            copySelectedTextToClipboard(selectedText)
                        }
                    }

                    KeyAction.Paste -> {
                        pasteAction()
                    }

                    KeyAction.SelectAll -> {
                        selectAllText()
                    }

                    KeyAction.SelectLeft -> {}
                    KeyAction.SelectRight -> {}
                    KeyAction.ShowEmojiKeyboard -> {
                        toggleEmojiKeyboard()
                    }

                    KeyAction.ToggleCase -> {
                        dakutenSmallActionForSumire()
                    }

                    KeyAction.ToggleDakuten -> {
                        dakutenSmallActionForSumire()
                    }

                    KeyAction.ToggleDakutenOnly -> {
                        toggleDakutenOnlyForCustomKeyboard()
                    }

                    KeyAction.ToggleHandakutenOnly -> {
                        toggleHandakutenOnlyForCustomKeyboard()
                    }

                    KeyAction.SwitchToEnglishLayout -> {
                        customKeyboardMode = KeyboardInputMode.ENGLISH
                        createNewKeyboardLayoutForSumire()
                        val inputMode = InputMode.ModeEnglish
                        if (isTabletGojuonSurface()) {
                            mainView.tabletView.currentInputMode.set(inputMode)
                        }
                        setCurrentInputModeForSession(inputMode)
                    }

                    KeyAction.SwitchToKanaLayout -> {
                        customKeyboardMode = KeyboardInputMode.HIRAGANA
                        createNewKeyboardLayoutForSumire()
                        val inputMode = InputMode.ModeJapanese
                        if (isTabletGojuonSurface()) {
                            mainView.tabletView.currentInputMode.set(inputMode)
                        }
                        setCurrentInputModeForSession(inputMode)
                    }

                    KeyAction.SwitchToNumberLayout -> {
                        customKeyboardMode = KeyboardInputMode.SYMBOLS
                        createNewKeyboardLayoutForSumire()
                        val inputMode = InputMode.ModeNumber
                        setCurrentInputModeForSession(inputMode)
                    }

                    KeyAction.ShiftKey -> {
                        isCustomLayoutShiftPressed = !isCustomLayoutShiftPressed

                        Handler(mainLooper).post {
                            getActiveKeyboardSurface()?.customLayout?.updateKeyIconByAction(
                                KeyAction.ShiftKey,
                                if (isCustomLayoutShiftPressed) com.kazumaproject.core.R.drawable.shift_fill_24px
                                else com.kazumaproject.core.R.drawable.shift_24px
                            )
                        }
                    }

                    KeyAction.SwitchDirectMode -> {
                        isCustomLayoutDirectMode = !isCustomLayoutDirectMode
                        persistCurrentCustomKeyboardInputModeIfEnabled()

                        Handler(mainLooper).post {
                            getActiveKeyboardSurface()?.customLayout?.updateKeyIconByAction(
                                KeyAction.SwitchDirectMode,
                                if (isCustomLayoutDirectMode) com.kazumaproject.core.R.drawable.language_japanese_kana_right_24px
                                else com.kazumaproject.core.R.drawable.language_japanese_kana_left_24px
                            )
                        }
                    }

                    KeyAction.CapLockKey -> {
                        isCustomLayoutCapLock = !isCustomLayoutCapLock

                        Handler(mainLooper).post {
                            getActiveKeyboardSurface()?.customLayout?.updateKeyIconByAction(
                                KeyAction.CapLockKey,
                                if (isCustomLayoutCapLock) com.kazumaproject.core.R.drawable.caps_lock
                                else com.kazumaproject.core.R.drawable.caps_lock_outline
                            )
                        }
                    }

                    KeyAction.SwitchRomajiEnglish -> {
                        isCustomLayoutRomajiMode = !isCustomLayoutRomajiMode
                        persistCurrentCustomKeyboardInputModeIfEnabled()
                        Handler(mainLooper).post {
                            getActiveKeyboardSurface()?.customLayout?.updateKeyIconByAction(
                                KeyAction.SwitchRomajiEnglish,
                                if (isCustomLayoutRomajiMode) com.kazumaproject.core.R.drawable.language_japanese_kana_left_bold_24px
                                else com.kazumaproject.core.R.drawable.language_japanese_kana_right_bold_24px
                            )
                        }
                    }

                    KeyAction.MoveCustomKeyboardTab -> {
                        scope.launch {
                            if (customLayouts.isNotEmpty()) {
                                val position =
                                    (currentCustomKeyboardPosition + 1) % customLayouts.size
                                selectCustomKeyboardTab(
                                    index = position,
                                    reason = CustomKeyboardSelectionReason.UserNextTab
                                )
                            }
                        }
                    }

                    is KeyAction.MoveToCustomKeyboard -> {
                        moveToCustomKeyboardByStableId(action.stableId)
                    }

                    KeyAction.ToggleKatakana -> {
                        when (countToggleKatakana) {
                            0 -> {
                                _inputString.update {
                                    it.hiraganaToKatakana()
                                }
                                countToggleKatakana++
                            }

                            1 -> {
                                _inputString.update {
                                    it.toHankakuKatakana()
                                }
                                countToggleKatakana++
                            }

                            2 -> {
                                _inputString.update {
                                    it.toHiragana()
                                }
                                countToggleKatakana = 0
                            }
                        }
                    }

                    KeyAction.DeleteUntilSymbol -> {
                        if (isDeleteLeftFlickPreference == true) {
                            val insertString = inputString.value
                            deleteWordOrSymbolsBeforeCursor(insertString)
                        }
                    }

                    KeyAction.DeleteAfterCursorUntilSymbol -> {
                        if (isDeleteUpFlickPreference == true) {
                            val insertString = inputString.value
                            deleteWordOrSymbolsAfterCursor(insertString)
                        }
                    }

                    KeyAction.UndoLastDelete -> {
                        if (isDeleteDownFlickPreference == true) {
                            undoLastHistoryEntry()
                        }
                    }

                    KeyAction.MoveCursorDown -> {
                        val insertString = inputString.value
                        if (cycleFocusedBunsetsuCandidate(delta = 1)) {
                        } else if (insertString.isEmpty() && stringInTail.get().isEmpty()) {
                            sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_DOWN)
                        }
                    }

                    KeyAction.MoveCursorUp -> {
                        val insertString = inputString.value
                        if (cycleFocusedBunsetsuCandidate(delta = -1)) {
                        } else if (insertString.isEmpty() && stringInTail.get().isEmpty()) {
                            sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_UP)
                        }
                    }

                    KeyAction.Cancel -> {}
                    KeyAction.VoiceInput -> {
                        startVoiceInput(mainView)
                    }

                    KeyAction.ForceHalfWidthSpace -> {
                        handleForceHalfWidthSpaceOrConvert(
                            mainView,
                            floatingKeyboardBinding.takeIf { isFloatingView })
                    }

                    KeyAction.ForceFullWidthSpace -> {
                        handleForceFullWidthSpaceOrConvert(
                            mainView,
                            floatingKeyboardBinding.takeIf { isFloatingView })
                    }
                }
            }
        })
    }

    private fun applyCustomLayoutShiftAndCapLock(text: String): String {
        if (!isCustomLayoutShiftPressed && !isCustomLayoutCapLock) {
            return text
        }
        if (isCustomLayoutShiftPressed) {
            Handler(mainLooper).post {
                getActiveKeyboardSurface()?.customLayout?.updateKeyIconByAction(
                    KeyAction.ShiftKey,
                    com.kazumaproject.core.R.drawable.shift_24px
                )
            }
        }
        return text.map { char ->
            if (char in 'a'..'z' || char in 'A'..'Z') {
                char.uppercaseChar()
            } else {
                char
            }
        }.joinToString("")
    }

    private fun handleOnKeyForSumire(
        text: String, mainView: MainLayoutBinding, isFlick: Boolean
    ) {
        val insertString = inputString.value
        val sb = StringBuilder()
        if (text.isNotEmpty()) {
            if (text.length == 1) {
                text.first().let {
                    if (isFlickOnlyMode == true) {
                        handleFlick(char = it, insertString, sb, mainView)
                    } else {
                        if (isFlick) {
                            handleFlick(char = it, insertString, sb, mainView)
                        } else {
                            handleTap(char = it, insertString, sb, mainView)
                        }
                    }
                }
            } else {
                sb.append(composingPrefixForKeyboardAppend()).append(text)
                val combined = sb.toString()
                if (!routeSymbolPanelSearchFullText(combined)) {
                    _inputString.update { combined }
                }
            }
        }

    }

    private fun cancelLeftLongPress() {
        onLeftKeyLongPressUp.set(true)
        leftCursorKeyLongKeyPressed.set(false)
        leftLongPressJob?.cancel()
        leftLongPressJob = null
    }

    private fun cancelRightLongPress() {
        onRightKeyLongPressUp.set(true)
        rightCursorKeyLongKeyPressed.set(false)
        rightLongPressJob?.cancel()
        rightLongPressJob = null
    }

    private fun copyAction() {
        val selectedText = getSelectedText(0)
        if (!selectedText.isNullOrEmpty()) {
            copySelectedTextToClipboard(selectedText)
        }
    }

    private fun copySelectedTextToClipboard(selectedText: CharSequence) {
        val text = selectedText.toString()
        val isSensitive = currentInputType.isPassword()
        clipboardUtil.setClipBoard(text, isSensitive = isSensitive)
        markClipboardPreviewFresh(ClipboardItem.Text(id = 0, text = text))
        suggestionAdapter?.apply {
            if (clipboardPreviewVisibility == true) {
                setPasteEnabled(true)
                setClipboardPreview(if (isSensitive) getSensitiveClipboardPreviewText() else text)
                appPreference.last_pasted_clipboard_text_preference = ""
            } else {
                setPasteEnabled(false)
            }
        }
    }

    /**
     * クリップボードからの貼り付けアクション。テキストと画像の両方に対応。
     */
    private fun pasteAction() {
        when (val item = clipboardUtil.getPrimaryClipContent()) {
            is ClipboardItem.Image -> {
                commitBitmap(item.bitmap)
            }

            is ClipboardItem.Text -> {
                if (item.text.isNotEmpty()) {
                    commitClipboardTextWithoutConversion(item.text)
                    appPreference.last_pasted_clipboard_text_preference = item.text
                    clearClipboardPreviewState()
                }
            }

            is ClipboardItem.Empty -> {
                // Do nothing
            }
        }
        clearDeletedBufferWithoutResetLayout()
        refreshEditHistoryUi()
    }

    private fun commitClipboardTextWithoutConversion(text: String) {
        suppressSelectionCleanupForInternalPreEditMove()
        beginBatchEdit()
        try {
            _inputString.update { "" }
            stringInTail.set("")
            setComposingText("", 0)
            finishComposingText()
            commitText(text, 1)
        } finally {
            endBatchEdit()
        }
        suggestionClickNum = 0
        isHenkan.set(false)
        henkanPressedWithBunsetsuDetect = false
        clearBunsetsuConversionSession()
        clearSuggestionStateAfterCommit()
    }

    private fun cutAction() {
        val selectedText = getSelectedText(0)
        if (!selectedText.isNullOrEmpty()) {
            copySelectedTextToClipboard(selectedText)
            sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
        }
    }

    private fun showOrHideKeyboard() {
        if (isInputViewShown) {
            requestHideSelf(0)
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                requestShowSelf(0)
            } else {
                val token = window?.window?.attributes?.token
                val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInputFromInputMethod(
                    token, InputMethodManager.SHOW_IMPLICIT
                )
            }
        }
    }

    private fun pasteImageAction(bitmap: Bitmap) {
        commitBitmap(bitmap)
        clearDeletedBufferWithoutResetLayout()
        refreshEditHistoryUi()
    }

    private fun pasteClipboardHistoryItem(item: ClipboardItem) {
        scope.launch {
            val fullContent = if (item.clipboardId() > 0) {
                withContext(Dispatchers.IO) {
                    clipboardHistoryRepository.getFullContentById(item.clipboardId())
                }
            } else {
                item
            }
            pasteClipboardItemContent(fullContent)
        }
    }

    private fun pasteClipboardItemContent(item: ClipboardItem) {
        when (item) {
            is ClipboardItem.Image -> {
                commitBitmap(item.bitmap)
                _keyboardSymbolViewState.update { SymbolKeyboardState() }
            }

            is ClipboardItem.Text -> {
                if (item.text.isNotEmpty()) {
                    commitClipboardTextWithoutConversion(item.text)
                    appPreference.last_pasted_clipboard_text_preference = item.text
                    clearClipboardPreviewState()
                    _keyboardSymbolViewState.update { SymbolKeyboardState() }
                }
            }

            ClipboardItem.Empty -> Unit
        }
        clearDeletedBufferWithoutResetLayout()
        refreshEditHistoryUi()
    }

    private fun handleClipboardHistoryItemAction(item: ClipboardItem, action: ClipboardItemAction) {
        vibrate()
        when (action) {
            ClipboardItemAction.PASTE -> pasteClipboardHistoryItem(item)
            ClipboardItemAction.PIN -> updateClipboardHistoryPin(item, isPinned = true)
            ClipboardItemAction.UNPIN -> updateClipboardHistoryPin(item, isPinned = false)
            ClipboardItemAction.DELETE -> deleteClipboardHistoryItem(item)
        }
    }

    private fun updateClipboardHistoryPin(item: ClipboardItem, isPinned: Boolean) {
        val id = item.clipboardId()
        if (id <= 0) return
        ioScope.launch {
            clipboardHistoryRepository.setPinned(id, isPinned)
            if (!isPinned) {
                cleanupExpiredClipboardItemsIfNeededNow()
            }
        }
    }

    private fun deleteClipboardHistoryItem(item: ClipboardItem) {
        val id = item.clipboardId()
        if (id <= 0) return
        ioScope.launch {
            clipboardHistoryRepository.deleteById(id)
        }
    }

    private fun ClipboardItem.clipboardId(): Long {
        return when (this) {
            is ClipboardItem.Image -> id
            is ClipboardItem.Text -> id
            ClipboardItem.Empty -> 0L
        }
    }

    private fun cleanupExpiredClipboardItemsIfNeeded() {
        if (!isClipboardUnpinnedAutoDeleteEnabled()) return
        ioScope.launch {
            cleanupExpiredClipboardItemsIfNeededNow()
        }
    }

    private suspend fun cleanupExpiredClipboardItemsIfNeededNow() {
        if (!isClipboardUnpinnedAutoDeleteEnabled()) return
        clipboardHistoryRepository.deleteExpiredUnpinnedItems(clipboardUnpinnedRetentionHours())
    }

    private fun filterClipboardHistoryListByRetention(
        historyList: List<ClipboardHistoryItem>
    ): List<ClipboardHistoryItem> {
        if (!isClipboardUnpinnedAutoDeleteEnabled()) return historyList
        val threshold = System.currentTimeMillis() -
                clipboardUnpinnedRetentionHours() * 60L * 60L * 1000L
        return historyList.filter { item ->
            item.isPinned || item.timestamp >= threshold
        }
    }

    private fun isClipboardUnpinnedAutoDeleteEnabled(): Boolean {
        return appPreference.clipboard_delete_unpinned_after_hours_preference
    }

    private fun clipboardUnpinnedRetentionHours(): Int {
        return appPreference.clipboard_unpinned_retention_hours_preference.coerceIn(1, 72)
    }

    /**
     * Bitmapを入力先アプリに送信します。
     * この関数を呼び出す前に、FileProviderが正しく設定されている必要があります。
     *
     * @param bitmap 送信するBitmapオブジェクト。
     */
    private fun commitBitmap(bitmap: Bitmap) {
        // ▼▼▼ ログ追加 ▼▼▼
        Timber.d("commitBitmap: 開始")

        // APIレベルが低い場合は何もせずに終了
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) {
            Timber.w("このAPIレベルではcommitContentはサポートされていません。")
            return
        }

        // ▼▼▼ ログ追加 ▼▼▼
        // InputConnectionとEditorInfoが有効か確認
        val inputConnection = editorGateway.connection()
        val editorInfo = currentInputEditorInfo
        if (inputConnection == null || editorInfo == null) {
            Timber.e("commitBitmap: InputConnectionまたはEditorInfoがnullです。処理を中断します。")
            return
        }

        // ▼▼▼ ログ追加 ▼▼▼
        // ターゲットエディタがサポートするMIMEタイプをログに出力
        val supportedMimeTypes = editorInfo.contentMimeTypes ?: emptyArray()
        if (supportedMimeTypes.isEmpty()) {
            Timber.w("commitBitmap: ターゲットエディタはどのMIMEタイプもサポートしていません。")
        } else {
            Timber.d("commitBitmap: ターゲットエディタがサポートするMIMEタイプ: ${supportedMimeTypes.joinToString()}")
        }

        // ▼▼▼ ログ追加 ▼▼▼
        // "image/png"をサポートしているか確認
        val isPngSupported = supportedMimeTypes.any { mimeType ->
            ClipDescription.compareMimeTypes(mimeType, "image/png")
        }
        if (!isPngSupported) {
            Timber.w("commitBitmap: ターゲットエディタは 'image/png' をサポートしていません。")
            // ここで処理を中断するか、別の形式（例: "image/jpeg"）を試すか判断できます
        }

        // 1. Bitmapをキャッシュディレクトリ内のファイルに保存
        val cachePath = File(cacheDir, "images")
        cachePath.mkdirs() // ディレクトリが存在することを確認
        val imageFile = File(cachePath, "clipboard_image.png")
        try {
            FileOutputStream(imageFile).use { outputStream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
            }
            // ▼▼▼ ログ追加 ▼▼▼
            Timber.d("commitBitmap: Bitmapをファイルに保存しました: ${imageFile.absolutePath}")
        } catch (e: IOException) {
            Timber.e(e, "Bitmapのファイルへの保存に失敗しました")
            return
        }

        // 2. FileProviderを使用してContent URIを取得
        val contentUri: Uri
        try {
            val authority = "${applicationContext.packageName}.fileprovider"
            contentUri = FileProvider.getUriForFile(this, authority, imageFile)
            // ▼▼▼ ログ追加 ▼▼▼
            Timber.d("commitBitmap: Content URIを取得しました: $contentUri")
        } catch (e: IllegalArgumentException) {
            Timber.e(
                e, "FileProviderが正しく設定されていません。AndroidManifest.xmlを確認してください。"
            )
            return
        }

        // 3. InputContentInfoCompatを作成
        val mimeType = "image/png"
        val description = ClipDescription("Image from keyboard", arrayOf(mimeType))

        // ★★★ 修正点 ★★★
        // linkUri（3番目の引数）にはnullを渡します。
        // この引数はhttp/httpsのウェブURIを要求するため、content:// URIを渡すとクラッシュします。
        val inputContentInfo = InputContentInfoCompat(
            contentUri, description, null // linkUriはnullにする
        )

        // 4. 読み取り権限をターゲットアプリに付与
        val flags = InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION

        // ▼▼▼ ログ追加 ▼▼▼
        Timber.d("commitBitmap: commitContentを呼び出します...")

        // 5. コンテンツをコミット (EditorGateway 経由)
        val didCommit = editorGateway.commitContentCompat(
            inputContentInfo,
            flags,
            null,
            editorInfo,
        )

        // ▼▼▼ ログ追加 ▼▼▼
        if (didCommit) {
            Timber.d("commitBitmap: コンテンツのコミットに成功しました。")
        } else {
            // このログは元のコードにもありますが、ここに来た場合の直前のログが重要になります
            Timber.e("commitBitmap: コンテンツのコミットに失敗しました。エディタが画像の挿入をサポートしていない可能性があります。")
            commitBitmapViaClipboard(contentUri)
        }
    }

    /**
     * クリップボード経由でBitmapを貼り付けます。
     * commitContentが失敗した場合のフォールバックとして使用します。
     *
     * @param contentUri 貼り付ける画像のContent URI
     */
    private fun commitBitmapViaClipboard(contentUri: Uri) {
        Timber.d("commitBitmapViaClipboard: 開始")
        try {
            clipboardUtil.setClipBoardUri(contentUri, label = "Image")

            // 2. ターゲットアプリに読み取り権限を一時的に付与
            // (FileProviderのgrantUriPermissions属性がtrueなら不要な場合もあるが、明示的に行うのが安全)
            grantUriPermission(
                currentInputEditorInfo.packageName,
                contentUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            Timber.d("commitBitmapViaClipboard: クリップボードにコピー完了")

            // 3. 「貼り付け」コマンドを実行
            val didPaste = editorGateway.performContextMenuAction(android.R.id.paste)
            if (didPaste) {
                Timber.d("commitBitmapViaClipboard: 貼り付けコマンドの実行に成功")
            } else {
                Timber.w("commitBitmapViaClipboard: 貼り付けコマンドの実行に失敗")
                // ここでユーザーに「クリップボードにコピーしました。手動で貼り付けてください」と通知するのも良い
            }

        } catch (e: Exception) {
            Timber.e(e, "commitBitmapViaClipboard: 処理中に例外が発生")
        }
    }

    /**
     * ★新しい関数: クリップボードのプレビューUIを更新します。
     * 画像とテキストの両方を判定して、正しくプレビューの状態を設定します。
     */
    private fun clipboardPreviewFingerprint(item: ClipboardItem): String? {
        return when (item) {
            is ClipboardItem.Text -> "text:${item.text.hashCode()}:${item.text.length}"
            is ClipboardItem.Image -> "image:${item.bitmap.width}x${item.bitmap.height}:${item.bitmap.byteCount}"
            ClipboardItem.Empty -> null
        }
    }

    private fun markClipboardPreviewFresh(item: ClipboardItem) {
        clipboardPreviewFingerprint = clipboardPreviewFingerprint(item)
        clipboardPreviewShownAtMs = SystemClock.elapsedRealtime()
        Handler(mainLooper).postDelayed({
            updateClipboardPreview()
        }, clipboardPreviewFreshDurationMs + 250L)
    }

    private fun clearClipboardPreviewState() {
        clipboardPreviewFingerprint = null
        clipboardPreviewShownAtMs = 0L
        listOfNotNull(suggestionAdapter, suggestionAdapterFull).forEach { adapter ->
            adapter.setClipboardPreview("")
            adapter.setClipboardImagePreview(null)
            adapter.setPasteEnabled(false)
        }
        mainLayoutBinding?.let { updateUpperAreaVisibility(it) }
    }

    private fun shouldShowClipboardPreview(item: ClipboardItem): Boolean {
        if (clipboardPreviewVisibility != true) return false
        if (clipboardPreviewTapToDelete == true) return false
        if (deletedBuffer.isNotEmpty()) return false
        val fingerprint = clipboardPreviewFingerprint(item) ?: return false
        if (clipboardPreviewFingerprint != fingerprint) return false
        val elapsed = SystemClock.elapsedRealtime() - clipboardPreviewShownAtMs
        if (elapsed !in 0..clipboardPreviewFreshDurationMs) return false
        if (item is ClipboardItem.Text &&
            appPreference.last_pasted_clipboard_text_preference == item.text
        ) {
            return false
        }
        return true
    }

    private fun updateClipboardPreview() {
        if (clipboardPreviewVisibility != true) return
        Timber.d("SuggestionAdapter Clipboard: updateClipboardPreview")
        val item = cachedPrimaryClipContent ?: run {
            val fetched = clipboardUtil.getPrimaryClipContent()
            cachedPrimaryClipContent = fetched
            fetched
        }
        if (!shouldShowClipboardPreview(item)) {
            clearClipboardPreviewState()
            return
        }
        listOfNotNull(suggestionAdapter, suggestionAdapterFull).forEach { adapter ->
            when (item) {
                is ClipboardItem.Image -> {
                    adapter.setPasteEnabled(true)
                    if (clipboardUtil.isPrimaryClipSensitive()) {
                        adapter.setClipboardPreview(getSensitiveClipboardPreviewText())
                    } else {
                        adapter.setClipboardImagePreview(item.bitmap)
                    }
                }

                is ClipboardItem.Text -> {
                    adapter.setPasteEnabled(true)
                    adapter.setClipboardPreview(getClipboardPreviewText(item.text))
                }

                is ClipboardItem.Empty -> {
                    clearClipboardPreviewState()
                }
            }
        }
    }

    private fun getClipboardPreviewText(text: String): String {
        return if (clipboardUtil.isPrimaryClipSensitive()) {
            getSensitiveClipboardPreviewText()
        } else {
            text
        }
    }

    private fun getSensitiveClipboardPreviewText(text: CharSequence? = null): String = "********"

    private fun dakutenSmallActionForSumire() {
        val insertString = keyboardCompositionTextForEditing()
        val sb = StringBuilder()
        if (insertString.isNotEmpty()) {
            if (insertString.last().isLatinAlphabet()) {
                smallConversionEnglish(sb, insertString)
            } else if (insertString.last().isHiragana()) {
                dakutenSmallLetter(
                    sb, insertString, GestureType.Tap
                )
            }
        }
    }


    private fun smallConversionEnglish(
        sb: StringBuilder, insertString: String,
    ) {
        _dakutenPressed.value = true
        englishSpaceKeyPressed.set(false)

        if (insertString.isNotEmpty()) {
            val insertPosition = insertString.last()
            insertPosition.let { c ->
                if (!c.isHiragana()) {
                    c.getDakutenSmallChar()?.let { dakutenChar ->
                        setStringBuilderForConvertStringInHiragana(dakutenChar, sb, insertString)
                    }
                }
            }
        }
    }

    private fun resetKeyboard() {
        Timber.d("resetKeyboard called for showKeyboard")
        if (keyboardOrder.isEmpty()) return
        if (enableShowLastShownKeyboardInRestart == true) {
            val resolvedIndex = normalizeKeyboardOrderIndex(lastSavedKeyboardPosition ?: 0)
            if (resolvedIndex != (lastSavedKeyboardPosition ?: 0)) {
                lastSavedKeyboardPosition = resolvedIndex
                appPreference.save_last_used_keyboard_position_preference = resolvedIndex
            }
            currentKeyboardOrder = resolvedIndex
            showKeyboard(keyboardOrder[resolvedIndex])
        } else {
            currentKeyboardOrder = 0
            showKeyboard(keyboardOrder[0])
        }
    }

    private fun isLandscapeOrientation(): Boolean {
        return resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    }

    private fun resolveKeyboardTypeForCurrentOrientation(requestedType: KeyboardType): KeyboardType {
        if (landscapeForceQwertyPreference != true || !isLandscapeOrientation()) {
            return requestedType
        }
        return if (landscapeForceQwertyRomajiPreference == true) {
            KeyboardType.ROMAJI
        } else {
            KeyboardType.QWERTY
        }
    }

    private fun refreshKeyboardForCurrentOrientation() {
        val mainView = mainLayoutBinding ?: return
        if (keyboardOrder.isEmpty()) return
        val requestedType = keyboardOrder.getOrNull(currentKeyboardOrder)
            ?: keyboardOrder.getOrNull(lastSavedKeyboardPosition ?: -1)
            ?: keyboardOrder.firstOrNull()
            ?: return
        showKeyboard(requestedType)
        setKeyboardSizeSwitchKeyboard(mainView)
    }

    private fun normalizeKeyboardOrderIndex(index: Int): Int {
        if (keyboardOrder.isEmpty()) return 0
        return index.takeIf { it in keyboardOrder.indices } ?: 0
    }

    private fun handleLeftCursor(gestureType: GestureType, insertString: String) {
        if (selectMode.value) {
            extendOrShrinkLeftOneChar()
        } else {
            handleLeftKeyPress(gestureType, insertString)
        }
        onLeftKeyLongPressUp.set(true)
    }

    /**
     * テキスト中の「offset」位置から見て、一つ前のグラフェムクラスタ開始位置を返す。
     * 文字列先頭または取得失敗時は 0 を返す。
     */
    private fun previousGraphemeOffset(text: String, offset: Int): Int {
        if (offset <= 0) return 0
        val it = BreakIterator.getCharacterInstance()
        it.setText(text)
        val pos = it.preceding(offset)
        return if (pos == BreakIterator.DONE) 0 else pos
    }

    /**
     * テキスト中の「offset」位置から見て、一つ次のグラフェムクラスタ開始位置を返す。
     * 文字列末尾または取得失敗時は text.length を返す。
     */
    private fun nextGraphemeOffset(text: String, offset: Int): Int {
        if (offset >= text.length) return text.length
        val it = BreakIterator.getCharacterInstance()
        it.setText(text)
        val pos = it.following(offset)
        return if (pos == BreakIterator.DONE) text.length else pos
    }

////////////////////////////////////////////////////////////////////////////////
// ─────────────────────────────────────────────────────────────────────────────
//    extendOrShrinkLeftOneChar / extendOrShrinkSelectionRight の修正版
//    （グラフェムクラスタ単位で選択範囲を伸縮）
// ─────────────────────────────────────────────────────────────────────────────
////////////////////////////////////////////////////////////////////////////////

    /** 選択開始時の固定端（アンカー）。-1 は「未設定」を示す */
    private var anchorPos = -1

    /**
     * Shift + ← 相当：左へ「拡張グラフェムクラスタ」1つ分だけ伸ばす／縮める
     */
    private fun extendOrShrinkLeftOneChar() {
        val extracted = getExtractedText(ExtractedTextRequest(), 0) ?: return
        val textStr = extracted.text?.toString() ?: return
        val selStart = extracted.selectionStart
        val selEnd = extracted.selectionEnd

        // 0) まだ選択がない（キャレットのみ）
        if (selStart == selEnd) {
            // キャレットが先頭なら何もしない
            if (selStart == 0) return

            // アンカーを現在位置に固定
            anchorPos = selStart

            // 前のグラフェムクラスタ開始位置を取得して選択開始
            val newStart = previousGraphemeOffset(textStr, selStart)

            beginBatchEdit()
            finishComposingText()
            setSelection(newStart, selEnd)
            endBatchEdit()
            return
        }

        // 1) すでに選択がある
        val cursorOnLeft = (anchorPos == selEnd)   // カーソルが選択範囲の左端にあるか
        val cursorOnRight = (anchorPos == selStart) // カーソルが選択範囲の右端にあるか

        when {
            // 1-A: カーソルが左端 → さらに左へ1文字（グラフェム）分伸ばす
            cursorOnLeft -> {
                if (selStart == 0) return
                val newStart = previousGraphemeOffset(textStr, selStart)
                beginBatchEdit()
                finishComposingText()
                setSelection(newStart, selEnd)
                endBatchEdit()
            }

            // 1-B: カーソルが右端 → 右端を1文字（グラフェム）分縮める
            cursorOnRight -> {
                val newEnd = previousGraphemeOffset(textStr, selEnd)
                if (newEnd <= selStart) {
                    // 選択範囲がなくなるのでキャレットのみの状態に戻す
                    beginBatchEdit()
                    finishComposingText()
                    setSelection(selStart, selStart)
                    endBatchEdit()
                    anchorPos = -1
                } else {
                    beginBatchEdit()
                    finishComposingText()
                    setSelection(selStart, newEnd)
                    endBatchEdit()
                }
            }

            else -> {
                // 状態不整合ならアンカーをリセット
                anchorPos = -1
            }
        }
    }

    /**
     * Shift + → 相当：右へ「拡張グラフェムクラスタ」1つ分だけ伸ばす／縮める
     */
    private fun extendOrShrinkSelectionRight() {
        val extracted = getExtractedText(ExtractedTextRequest(), 0) ?: return
        val textStr = extracted.text?.toString() ?: return
        val selStart = extracted.selectionStart
        val selEnd = extracted.selectionEnd
        val textLen = textStr.length

        // 0) まだ選択がない（キャレットのみ）
        if (selStart == selEnd) {
            anchorPos = selStart
            if (selEnd < textLen) {
                val newEnd = nextGraphemeOffset(textStr, selEnd)
                beginBatchEdit()
                finishComposingText()
                setSelection(selStart, newEnd)
                endBatchEdit()
            }
            return
        }

        // 1) すでに選択がある
        val cursorIsOnRight = (anchorPos == selStart)
        if (cursorIsOnRight) {
            // 1-A: カーソルが右端 → さらに右へ1文字（グラフェム）分伸ばす
            if (selEnd < textLen) {
                val newEnd = nextGraphemeOffset(textStr, selEnd)
                beginBatchEdit()
                finishComposingText()
                setSelection(anchorPos, newEnd)
                endBatchEdit()
            }
        } else {
            // 1-B: カーソルが左端 → 左端を1文字（グラフェム）分縮める
            val newStart = nextGraphemeOffset(textStr, selStart)
            if (newStart >= selEnd) {
                // 選択範囲がなくなるのでキャレットのみの状態に戻す
                beginBatchEdit()
                finishComposingText()
                setSelection(selEnd, selEnd)
                endBatchEdit()
                anchorPos = -1
            } else {
                beginBatchEdit()
                finishComposingText()
                setSelection(newStart, selEnd)
                endBatchEdit()
            }
        }
    }

    /**
     * 入力フィールドの全文を全選択する
     */
    private fun selectAllText() {
        if (inputString.value.isNotEmpty()) return
        val request = ExtractedTextRequest()
        // 必要に応じて request.flags を設定（デフォルトで OK）
        val extracted: ExtractedText? = getExtractedText(request, 0)
        val fullText: CharSequence = extracted?.text ?: return
        // 3. テキスト長を取得
        val textLen = fullText.length
        if (textLen == 0) return
        // 4. 選択開始：先頭(0) → 選択終了：全文長
        // ※ beginBatchEdit() / endBatchEdit() で一連の編集をまとめると滑らか
        beginBatchEdit()
        finishComposingText() // もし変換中の文字列があれば確定しておく
        setSelection(0, textLen)
        endBatchEdit()
    }

    private fun clearSelection() {
        beginBatchEdit()
        finishComposingText()
        editorGateway.collapseSelectionToCursor()
        endBatchEdit()
    }

    private fun shouldPreserveDeleteHistoryForAction(action: KeyAction): Boolean {
        return when (action) {
            KeyAction.Delete,
            KeyAction.DeleteUntilSymbol,
            KeyAction.DeleteAfterCursorUntilSymbol,
            KeyAction.UndoLastDelete,
            KeyAction.DoNothing -> true

            else -> false
        }
    }

    private fun cancelHenkanByLongPressDeleteKey() {
        val insertString = inputString.value
        val selectedSuggestion = suggestionAdapter?.suggestions?.getOrNull(suggestionClickNum)

        deleteKeyLongKeyPressed.set(true)
        suggestionAdapter?.updateHighlightPosition(RecyclerView.NO_POSITION)
        suggestionClickNum = 0
        isFirstClickHasStringTail = false
        isContinuousTapInputEnabled.set(true)
        lastFlickConvertedNextHiragana.set(true)
        isHenkan.set(false)
        henkanPressedWithBunsetsuDetect = false
        clearBunsetsuConversionSession()

        val spannableString = if (insertString.length == selectedSuggestion?.length?.toInt()) {
            SpannableString(insertString + stringInTail)
        } else {
            stringInTail.set("")
            SpannableString(insertString)
        }
        setComposingTextAfterEdit(
            inputString = insertString,
            spannableString = spannableString,
            backgroundColor = if (customComposingTextPreference == true) {
                inputCompositionAfterBackgroundColor
                    ?: getColor(com.kazumaproject.core.R.color.blue)
            } else {
                getColor(com.kazumaproject.core.R.color.blue)
            },
            textColor = if (customComposingTextPreference == true) {
                inputCompositionTextColor
            } else {
                null
            }
        )
        mainLayoutBinding?.suggestionRecyclerView?.apply {
            scrollToPosition(0)
        }
    }

    @OptIn(FlowPreview::class)
    private fun startScope(mainView: MainLayoutBinding) = scope.launch {
        launch {
            var prevFlag: CandidateShowFlag? = null
            suggestionFlag.collectLatest { currentFlag ->
                val insertString = inputString.value
                Timber.d("suggestionFlag CandidateShowFlag.Idle: [$insertString] [$stringInTail] [$prevFlag] [$currentFlag]")
                if (prevFlag == CandidateShowFlag.Idle && currentFlag == CandidateShowFlag.Updating) {
                    when {
                        physicalKeyboardEnable.replayCache.isEmpty() &&
                                isKeyboardFloatingMode == true || (physicalKeyboardEnable.replayCache.isNotEmpty() &&
                                !physicalKeyboardEnable.replayCache.first()) && isKeyboardFloatingMode == true -> {
                            floatingKeyboardBinding?.let { floatingKeyboardLayoutBinding ->
                                animateSuggestionImageViewVisibility(
                                    floatingKeyboardLayoutBinding.suggestionVisibility, true
                                )
                            }
                        }

                        physicalKeyboardEnable.replayCache.isEmpty() && (mainView.keyboardView.isVisible ||
                                mainView.tabletView.isVisible || mainView.qwertyView.isVisible ||
                                mainView.customLayoutDefault.isVisible) -> {
                            if (!suppressSuggestions) {
                                animateSuggestionImageViewVisibility(
                                    mainView.suggestionVisibility, true
                                )
                            }
                            if (!isSystemUiRemoteInputSession) {
                                setKeyboardHeightWithAdditional(mainView)
                            }
                        }

                        (physicalKeyboardEnable.replayCache.isNotEmpty() && !physicalKeyboardEnable.replayCache.first()) &&
                                (mainView.keyboardView.isVisible || mainView.tabletView.isVisible ||
                                        mainView.qwertyView.isVisible || mainView.customLayoutDefault.isVisible) -> {
                            animateSuggestionImageViewVisibility(
                                mainView.suggestionVisibility, true
                            )
                            if (!isSystemUiRemoteInputSession) {
                                setKeyboardHeightWithAdditional(mainView)
                            }
                        }

                    }
                    if (isKeyboardFloatingMode == true) {
                        floatingKeyboardBinding?.let { floatingKeyboard ->
                            updateUIinHenkanFloating(floatingKeyboard, insertString)
                        }
                    } else {
                        updateUIinHenkan(mainView, insertString)
                    }
                    setSumireKeyboardSwitchNumberAndKatakanaKey(1)
                    if (qwertyMode.value == TenKeyQWERTYMode.TenKeyQWERTYRomaji && currentInputModeForSession == InputMode.ModeJapanese) {
                        updateQwertyOnActiveSurface {
                            setSpaceKeyText("変換")
                            setReturnKeyText("確定")
                        }
                    } else if ((qwertyMode.value == TenKeyQWERTYMode.TenKeyQWERTY && currentInputModeForSession == InputMode.ModeEnglish) || qwertyMode.value == TenKeyQWERTYMode.TenKeyQWERTYRomaji && currentInputModeForSession == InputMode.ModeEnglish) {
                        updateQwertyOnActiveSurface { setReturnKeyText("done") }
                    }
                    if (mainView.customLayoutDefault.isVisible) {
                        setSumireKeyboardDakutenKey()
                        setSumireKeyboardEnterKey(5)
                        when (currentInputModeForSession) {
                            InputMode.ModeJapanese -> {
                                setSumireKeyboardSpaceKey(1)
                            }

                            else -> {}
                        }
                    }
                    if (candidateTabVisibility == true) {
                        mainView.candidateTabLayout.isVisible = true
                    }
                    updateUpperAreaVisibility(mainView)
                }
                when (currentFlag) {
                    CandidateShowFlag.Idle -> {
                        suggestionAdapter?.suggestions = emptyList()
                        if (stringInTail.get().isEmpty()) {
                            if (isKeyboardFloatingMode == true) {
                                if (!suppressSuggestions) {
                                    floatingKeyboardBinding?.let { floatingKeyboardLayoutBinding ->
                                        animateSuggestionImageViewVisibility(
                                            floatingKeyboardLayoutBinding.suggestionVisibility,
                                            false
                                        )
                                    }
                                }
                            } else {
                                if (mainView.suggestionVisibility.isVisible) {
                                    animateSuggestionImageViewVisibility(
                                        mainView.suggestionVisibility, false
                                    )
                                }
                            }
                            if (mainView.customLayoutDefault.isVisible) {
                                resetSumireKeyboardDakutenMode()
                            }
                            if (qwertyMode.value == TenKeyQWERTYMode.TenKeyQWERTYRomaji && currentInputModeForSession == InputMode.ModeJapanese) {
                                updateQwertyOnActiveSurface {
                                    setSpaceKeyText("空白")
                                    val qwertyEnterKeyText =
                                        currentInputType.getQWERTYReturnTextInJp()
                                    setReturnKeyText(qwertyEnterKeyText)
                                }
                            } else if ((qwertyMode.value == TenKeyQWERTYMode.TenKeyQWERTY && currentInputModeForSession == InputMode.ModeEnglish) || qwertyMode.value == TenKeyQWERTYMode.TenKeyQWERTYRomaji && currentInputModeForSession == InputMode.ModeEnglish) {
                                val qwertyEnterKeyText = currentInputType.getQWERTYReturnTextInEn()
                                updateQwertyOnActiveSurface { setReturnKeyText(qwertyEnterKeyText) }
                            }
                            setKeyboardHeightDefault(mainView)
                            setSumireKeyboardSwitchNumberAndKatakanaKey(0)
                            countToggleKatakana = 0
                            mainView.candidateTabLayout.isVisible = false
                            val tab = mainView.candidateTabLayout.getTabAt(0)
                            tab?.let { mainView.candidateTabLayout.selectTab(it) }
                            updateUpperAreaVisibility(mainView)
                        }
                        updateClipboardPreview()
                        updateUpperAreaVisibility(mainView)
                    }

                    CandidateShowFlag.Updating -> {
                        delay(CANDIDATE_REFRESH_DEBOUNCE_MS)
                        setSuggestionOnView(inputString.value, mainView)
                    }
                }
                prevFlag = currentFlag
            }
        }

        launch {
            suggestionViewStatus.collectLatest { isVisible ->
                Timber.d("suggestionViewStatus: $isVisible")
                updateSuggestionViewVisibility(mainView, isVisible)
            }
        }

        launch {
            keyboardSymbolViewState.collectLatest { isSymbolKeyboardShow ->
                Timber.d("keyboardSymbolViewState: $isSymbolKeyboardShow")
                setKeyboardSizeSwitchKeyboard(mainView)
                if (isKeyboardFloatingMode == true) {
                    floatingKeyboardBinding?.let { floatingKeyboardLayoutBinding ->
                        setSymbolsFloating(floatingKeyboardLayoutBinding)
                        if (isSymbolKeyboardShow.isShown) {
                            hideKeyboardViews(getFloatingKeyboardSurface() ?: return@let)
                            floatingKeyboardLayoutBinding.floatingSymbolKeyboard.isVisible = true
                        } else {
                            floatingKeyboardLayoutBinding.floatingSymbolKeyboard.isVisible = false
                            renderCurrentKeyboardStateOnActiveSurface()
                        }
                        updateFloatingKeyboardSizeForMode(qwertyMode.value)
                    }
                } else {
                    setKeyboardSizeForHeightSymbol(mainView, isSymbolKeyboardShow.isShown)
                }
                mainView.apply {
                    if (isSymbolKeyboardShow.isShown) {
                        shortcutToolbarRecyclerview.isVisible = false
                    } else {
                        updateUpperAreaVisibility(mainView)
                    }
                    if (isSymbolKeyboardShow.isShown) {
                        applySymbolPanelKeyboardVisibility(
                            mainView = mainView,
                            keepMainKeyboardVisible = symbolPanelSearchFocused,
                        )
                        animateViewVisibility(keyboardSymbolView, true)
                        suggestionRecyclerView.isVisible = false
                        if (isSymbolKeyboardShow.mode == SymbolMode.CLIPBOARD) {
                            setSymbolsClipboard(mainView = mainView)
                        } else {
                            setSymbols(mainView)
                        }
                    } else {
                        symbolPanelSearchFocused = false
                        mainLayoutBinding?.keyboardSymbolView?.clearSymbolPanelSearchFocus()
                        floatingKeyboardBinding?.floatingSymbolKeyboard?.clearSymbolPanelSearchFocus()
                        if (isTabletGojuonSurface()) {
                            when {
                                tabletView.isInvisible -> {
                                    tabletView.isVisible = true
                                }

                                qwertyView.isInvisible -> {
                                    qwertyView.isVisible = true
                                }

                                customLayoutDefault.isInvisible -> {
                                    customLayoutDefault.isVisible = true
                                }
                            }
                        } else {
                            when {
                                keyboardView.isInvisible -> {
                                    keyboardView.isVisible = true
                                }

                                qwertyView.isInvisible -> {
                                    qwertyView.isVisible = true
                                }

                                customLayoutDefault.isInvisible -> {
                                    customLayoutDefault.isVisible = true
                                }
                            }
                        }
                        animateViewVisibility(keyboardSymbolView, false)
                        updateUpperAreaVisibility(mainView)
                        if (customLayoutDefault.isInvisible) customLayoutDefault.visibility =
                            View.VISIBLE
                    }
                }
            }
        }

        launch {
            selectMode.collectLatest { selectMode ->
                mainView.keyboardView.setTextToAllButtonsFromSelectMode(selectMode)
            }
        }

        launch {
            cursorMoveMode.collect { isCursorMoveMode ->
                mainView.keyboardView.setTextToMoveCursorMode(isCursorMoveMode)
                floatingKeyboardBinding?.keyboardViewFloating?.setTextToMoveCursorMode(
                    isCursorMoveMode
                )
            }
        }

        launch {
            qwertyMode.collectLatest {
                Timber.d("qwertyMode value: $it")
                when (it) {
                    TenKeyQWERTYMode.Default -> {
                        suggestionAdapter?.updateState(
                            TenKeyQWERTYMode.Default, emptyList()
                        )
                    }

                    TenKeyQWERTYMode.TenKeyQWERTY -> {
                        suggestionAdapter?.updateState(
                            TenKeyQWERTYMode.TenKeyQWERTY, emptyList()
                        )
                    }

                    TenKeyQWERTYMode.TenKeyQWERTYRomaji -> {
                        suggestionAdapter?.updateState(
                            TenKeyQWERTYMode.TenKeyQWERTY, emptyList()
                        )
                    }

                    TenKeyQWERTYMode.Custom -> {
                        if (customLayouts.isEmpty()) {
                            clearCurrentCustomKeyboardSelection()
                            fallbackFromCustomKeyboardIfNeeded()
                            return@collectLatest
                        } else {
                            if (selectedCustomKeyboardLayoutOrNull() == null) {
                                currentCustomKeyboardPosition = 0
                                currentCustomKeyboardStableId =
                                    customLayouts.first().stableId.takeIf { stableId -> stableId.isNotBlank() }
                            }
                            suggestionAdapter?.updateState(
                                TenKeyQWERTYMode.Custom, customLayouts
                            )
                        }
                    }

                    TenKeyQWERTYMode.Sumire -> {
                        suggestionAdapter?.updateState(
                            TenKeyQWERTYMode.Sumire, emptyList()
                        )
                    }

                    TenKeyQWERTYMode.Number -> {
                        suggestionAdapter?.updateState(
                            TenKeyQWERTYMode.Number, emptyList()
                        )
                    }
                }
                syncFloatingKeyboardContentForMode(it)
                renderCurrentKeyboardStateOnActiveSurface()
                updateFloatingKeyboardSizeForMode(it)
            }
        }

        launch {
            emojiSearchQuery
                .debounce(200)
                .collectLatest { query ->
                    val results = if (query.isBlank()) {
                        emptyList()
                    } else {
                        withContext(Dispatchers.Default) {
                            azooKeyDictionaryAssetProvider.emojiDictionarySearch
                                ?.searchInputPrefix(query, limit = 80)
                                ?.map { entry -> entry.surface }
                                ?.distinct()
                                .orEmpty()
                        }
                    }
                    mainLayoutBinding?.keyboardSymbolView?.displayEmojiSearchResults(results)
                    floatingKeyboardBinding?.floatingSymbolKeyboard?.displayEmojiSearchResults(results)
                }
        }

        launch {
            var clipboardJob: Job? = null
            clipboardSearchQuery.collectLatest { query ->
                clipboardJob?.cancel()
                clipboardJob = launch {
                    val flow = if (query.isEmpty()) {
                        clipboardHistoryRepository.allHistory
                    } else {
                        clipboardHistoryRepository.searchHistory(query)
                    }
                    flow.collectLatest { historyList ->
                        cleanupExpiredClipboardItemsIfNeeded()
                        val visibleHistoryList = filterClipboardHistoryListByRetention(historyList)
                        // 1. DBモデル(軽量メタデータ)のリストからUIモデルのリストに変換する
                        //    CursorWindowクラッシュを避けるため、ここでは実データ(全文/Bitmap)を読み込まず
                        //    プレビュー用のテキストを保持させる、または ID のみの器を作る。
                        val uiItems = visibleHistoryList.map { entity ->
                            when (entity.itemType) {
                                ItemType.TEXT -> {
                                    // 一覧表示には DB の preview を使用する
                                    ClipboardItem.Text(
                                        id = entity.id,
                                        text = entity.preview,
                                        isPinned = entity.isPinned
                                    )
                                }

                                ItemType.IMAGE -> {
                                    // 画像の場合、一覧では Bitmap は null (または読み込み専用の器) にする
                                    // ※ 必要に応じて placeholder 用の空 Bitmap を渡すか、
                                    //    UI 側 (CustomSymbolKeyboardView) で path からロードするように変更します。
                                    val content = clipboardHistoryRepository.getThumbnail(entity)
                                    if (content is ClipboardItem.Image) {
                                        content // 正しい Bitmap が入った ClipboardItem.Image
                                    } else {
                                        ClipboardItem.Text(
                                            id = entity.id,
                                            text = "[画像の読み込み失敗]",
                                            isPinned = entity.isPinned
                                        )
                                    }
                                }
                            }
                        }

                        // 2. 最新のリストをクラスのプロパティにキャッシュする
                        currentClipboardItems = uiItems

                        // 3. CustomSymbolKeyboardViewの表示を更新する
                        mainView.keyboardSymbolView.updateClipboardItems(uiItems)
                        floatingKeyboardBinding?.floatingSymbolKeyboard?.updateClipboardItems(uiItems)
                    }
                }
            }
        }

        launch {
            romajiMapRepository.getActiveMap().map { entity ->
                entity?.let {
                    Pair(it.mapData, it.isDeletable)
                } ?: Pair(romajiMapRepository.getDefaultMapData(), false)
            }.distinctUntilChanged().collectLatest { (activeMapData, isDeletable) ->
                val converterMap = if (!isDeletable) {
                    activeMapData.mapKeys { (key, _) -> key.toZenkaku() }
                } else {
                    activeMapData
                }
                isDefaultRomajiHenkanMap = !isDeletable
                romajiConverter = RomajiKanaConverter(converterMap)
                azooKeyRoman2KanaTransducer = if (isDeletable) {
                    com.kazumaproject.markdownhelperkeyboard.converter.api.AzooKeyRoman2KanaTransducer
                        .fromMap(converterMap)
                } else {
                    com.kazumaproject.markdownhelperkeyboard.converter.api.AzooKeyRoman2KanaTransducer.default()
                }
            }
        }

        launch {
            keyboardRepository.getLayouts().distinctUntilChanged().collectLatest { layouts ->
                val normalizedLayouts = if (layouts.any { it.stableId.isBlank() }) {
                    keyboardRepository.ensureStableIds()
                    keyboardRepository.getLayoutsNotFlow()
                } else {
                    layouts
                }
                onCustomKeyboardLayoutsChanged(normalizedLayouts)
            }
        }

        launch {
            ngWordRepository.getAllNgWordsFlow().collectLatest { ngWords ->
                val distinctNgWords = ngWords.distinct()
                _ngWordsList.value = distinctNgWords
                cachedNgWordsStringList = distinctNgWords.map { it.tango }
                _ngPattern.value = distinctNgWords.joinToString("|") { Pattern.quote(it.tango) }.toRegex()
            }
        }

        launch {
            keyboardFloatingMode.collectLatest { isFloatingMode ->
                Timber.d("keyboardFloatingMode state changed: $isFloatingMode")
                applyFloatingModeState(isFloatingMode)
            }
        }

        launch {
            physicalKeyboardEnable.distinctUntilChanged().collect { isPhysicalKeyboardEnable ->
                Timber.d("physicalKeyboardEnable: $isPhysicalKeyboardEnable")
                val effect = physicalKeyboardUiEffectHandler.buildUiEffect(
                    physicalKeyboardEnabled = isPhysicalKeyboardEnable,
                    sessionInputMode = currentInputModeForSession,
                )
                physicalKeyboardUiEffectHandler.applyUiEffect(
                    effect,
                    physicalKeyboardUiHost(mainView),
                )
                Timber.d("isPhysicalKeyboardEnable: $isPhysicalKeyboardEnable")
            }
        }

        launch {
            shortCurRepository.enabledShortcutsFlow.collectLatest {
                val shortcuts = it.ifEmpty {
                    listOf(
                        ShortcutType.SETTINGS,
                        ShortcutType.EMOJI,
                        ShortcutType.TEMPLATE,
                        ShortcutType.COPY,
                        ShortcutType.PASTE,
                        ShortcutType.KEYBOARD_PICKER,
                        ShortcutType.CLIP_BOARD
                    )
                }
                shortcutAdapter?.submitList(shortcuts) {
                    mainLayoutBinding?.let { mainView ->
                        distributeShortcutToolbarItems(mainView)
                        updateUpperAreaVisibility(mainView)
                    }
                }
            }
        }

        launch {
            physicalKeyboardShortcutRepository.getEnabled().collectLatest {
                physicalKeyboardShortcuts = it
            }
        }

        launch {
            zenzRequest
                .debounce((zenzDebounceTimePreference ?: 300).toLong())
                .collectLatest { params ->
                    val insertReading = params.insertReading
                    val cursorPosition = params.cursorPosition
                    val zenz = buildImeCandidateZenzContext(insertReading)
                    val preferences = buildImeCandidatePreferences()
                    val policy = currentRuntimeConversionPolicy(insertReading)
                    if (policy.shouldUseZenzai) {
                        _zenzCandidates.update { emptyList() }
                        return@collectLatest
                    }
                    val currentFullInput = inputString.value + stringInTail.get()
                    val zenzCandidates = run {
                        val generated = candidateCoordinator.generateLiveZenzCandidates(
                            input = insertReading,
                            zenz = zenz,
                            preferences = preferences,
                            cursorPosition = cursorPosition,
                        )
                        lastLocalUpdatedInput.first { completedInput ->
                            completedInput == insertReading || currentFullInput != insertReading
                        }
                        if (currentFullInput == insertReading) generated else emptyList()
                    }
                    _zenzCandidates.update { zenzCandidates }
                }
        }

        launch {
            zenzCandidates
                .buffer(kotlinx.coroutines.channels.Channel.CONFLATED)
                .collectLatest { resultFromZenz ->
                    val insertString = inputString.value
                    if (insertString.isNotEmpty()) {
                        if (resultFromZenz.isNotEmpty() &&
                            resultFromZenz.first().originalString == insertString
                        ) {
                            val suggestions = filteredCandidateList ?: emptyList()
                            val mergedCandidates = candidateCoordinator.mergeLiveZenzCandidates(
                                insertReading = insertString,
                                dictionaryCandidates = suggestions,
                                zenzCandidates = resultFromZenz,
                            )
                            if (mergedCandidates != null) {
                                suggestionAdapter?.suggestions =
                                    com.kazumaproject.markdownhelperkeyboard.converter.core.AzooKeyJapaneseConversionText
                                        .filterDisplayedCandidates(mergedCandidates)
                            }
                        } else {
                            if (inputString.value.isEmpty()) {
                                if (!isPostCommitPredictionActive) {
                                    suggestionAdapter?.suggestions = emptyList()
                                    suggestionAdapterFull?.suggestions = emptyList()
                                }
                            }
                        }
                    } else {
                        if (!isPostCommitPredictionActive) {
                            suggestionAdapter?.suggestions = emptyList()
                            suggestionAdapterFull?.suggestions = emptyList()
                        }
                    }
                }
        }

        launch {
            var lastString = ""
            inputString.collectLatest { string ->
                lastString = string
                processInputString(string, mainView)
            }
        }
    }


    private fun beginZenzRerankRequest(): Long {
        zenzRerankJob?.cancel()
        zenzRerankJob = null
        zenzRerankRequestToken += 1L
        return zenzRerankRequestToken
    }

    private fun clearZenzContextCache() {
        zenzContextCacheInput = null
        zenzContextCacheHardwareKeyboard = null
        zenzContextCacheLeftContext = null
        zenzContextCache = null
    }

    private fun getActiveSymbolPanelSearchQuery(): String {
        return mainLayoutBinding?.keyboardSymbolView?.getActiveSearchQuery()
            ?: floatingKeyboardBinding?.floatingSymbolKeyboard?.getActiveSearchQuery()
            ?: ""
    }

    private fun composingPrefixForKeyboardAppend(): String {
        if (!isSymbolPanelSearchRoutingActive()) {
            return inputString.value
        }
        return lastQwertyRomajiRawInput ?: getActiveSymbolPanelSearchQuery()
    }

    private fun keyboardCompositionTextForEditing(): String {
        if (!isSymbolPanelSearchRoutingActive()) {
            return inputString.value
        }
        return getActiveSymbolPanelSearchQuery()
    }

    private fun isSymbolPanelSearchRoutingActive(): Boolean {
        if (!keyboardSymbolViewState.value.isShown) return false
        if (symbolPanelSearchFocused) return true
        return mainLayoutBinding?.keyboardSymbolView?.isSymbolPanelSearchActive() == true ||
            floatingKeyboardBinding?.floatingSymbolKeyboard?.isSymbolPanelSearchActive() == true
    }

    private fun routeSymbolPanelSearchText(text: String): Boolean {
        if (!isSymbolPanelSearchRoutingActive() || text.isEmpty()) return false
        mainLayoutBinding?.keyboardSymbolView?.appendActiveSearchText(text)
        floatingKeyboardBinding?.floatingSymbolKeyboard?.appendActiveSearchText(text)
        return true
    }

    private fun routeSymbolPanelSearchFullText(text: String): Boolean {
        if (!isSymbolPanelSearchRoutingActive() || text.isEmpty()) return false
        mainLayoutBinding?.keyboardSymbolView?.setActiveSearchText(text)
        floatingKeyboardBinding?.floatingSymbolKeyboard?.setActiveSearchText(text)
        return true
    }

    private fun routeSymbolPanelSearchDelete(): Boolean {
        if (!isSymbolPanelSearchRoutingActive()) return false
        val deletedFromMain = mainLayoutBinding?.keyboardSymbolView?.deleteActiveSearchChar() == true
        val deletedFromFloating =
            floatingKeyboardBinding?.floatingSymbolKeyboard?.deleteActiveSearchChar() == true
        if (deletedFromMain || deletedFromFloating) {
            lastQwertyRomajiRawInput = getActiveSymbolPanelSearchQuery().ifEmpty { null }
        }
        return deletedFromMain || deletedFromFloating
    }

    private fun setSymbolPanelSearchFocused(active: Boolean) {
        if (symbolPanelSearchFocused == active) return
        symbolPanelSearchFocused = active
        if (active) {
            lastQwertyRomajiRawInput = null
        }
        mainLayoutBinding?.let { mainView ->
            if (keyboardSymbolViewState.value.isShown) {
                if (active) {
                    updateKeyboardLayout(mainView, isSymbolOverride = true)
                } else {
                    applySymbolPanelKeyboardVisibility(mainView, keepMainKeyboardVisible = false)
                    updateKeyboardLayout(mainView, isSymbolOverride = true)
                }
            }
        }
    }

    private fun applySymbolPanelKeyboardVisibility(
        mainView: MainLayoutBinding,
        keepMainKeyboardVisible: Boolean,
    ) {
        val surface = getNormalKeyboardSurface() ?: return
        if (keepMainKeyboardVisible) {
            renderKeyboardMode(surface, qwertyMode.value, isFloating = false)
        } else {
            hideKeyboardViews(surface)
        }
    }

    private fun applySymbolPanelWithKeyboardSplitLayout(
        mainView: MainLayoutBinding,
        keyboardHeightPx: Int,
        width: Int,
        gravity: Int,
    ) {
        val toolbarHeight = dpToPx(40)
        val horizontal = gravity and Gravity.HORIZONTAL_GRAVITY_MASK
        val symbolHeight = (keyboardHeightPx * 0.58f).toInt().coerceAtLeast(dpToPx(200))

        (mainView.keyboardSymbolView.layoutParams as? FrameLayout.LayoutParams)?.let { params ->
            params.height = symbolHeight
            params.width = width
            params.topMargin = toolbarHeight
            params.bottomMargin = 0
            params.gravity = Gravity.TOP or horizontal
            mainView.keyboardSymbolView.layoutParams = params
        }

        applySymbolPanelKeyboardVisibility(mainView, keepMainKeyboardVisible = true)
        val surface = getNormalKeyboardSurface() ?: return
        val keyboardView = when (qwertyMode.value) {
            TenKeyQWERTYMode.Default -> {
                if (isTabletGojuonSurface()) surface.tabletView else surface.keyboardView
            }
            TenKeyQWERTYMode.TenKeyQWERTY,
            TenKeyQWERTYMode.TenKeyQWERTYRomaji,
            -> surface.qwertyView
            TenKeyQWERTYMode.Custom,
            TenKeyQWERTYMode.Sumire,
            TenKeyQWERTYMode.Number,
            -> surface.customLayout
        }
        keyboardView?.let { view ->
            (view.layoutParams as? FrameLayout.LayoutParams)?.let { params ->
                params.height = keyboardHeightPx
                params.topMargin = 0
                params.bottomMargin = 0
                params.gravity = Gravity.BOTTOM or horizontal
                view.layoutParams = params
            }
            view.isVisible = true
        }
    }

    private fun currentZenzConversionConfig(
        snapshot: ImePreferencesSnapshot? = cachedPreferences,
    ): com.kazumaproject.markdownhelperkeyboard.converter.zenz.ZenzConversionConfig {
        return com.kazumaproject.markdownhelperkeyboard.converter.zenz.ZenzConversionConfig(
            profile = snapshot?.zenzProfilePreference ?: (zenzProfilePreference ?: ""),
            maxTokens = snapshot?.zenzMaximumLetterSizePreference ?: (zenzMaximumLetterSizePreference ?: 32),
            rerankEnabled = snapshot?.zenzRerankPreference ?: (zenzRerankPreference == true),
            useRightContextFallback = snapshot?.enableZenzRightContextPreference
                ?: (enableZenzRightContextPreference == true),
            hasHardwareKeyboard = hasHardwareKeyboardConnected == true,
        )
    }

    private suspend fun resolveZenzLeftContext(insertString: String): String {
        return getZenzLeftContext(insertString, cachedPreferences)
    }

    private fun syncZenzLeftContextFromEditor() {
        val left = truncateZenzLeftContext(getLeftContext(inputLength = 0))
        cachedCandidateLeftContext = left
        candidateCoordinator.updateLeftSideContext(left)
        Timber.d("syncZenzLeftContextFromEditor: synced memory leftSideContext to [$left]")
    }

    private suspend fun getZenzLeftContext(
        insertString: String,
        snapshot: ImePreferencesSnapshot? = cachedPreferences,
    ): String {
        return try {
            truncateZenzLeftContext(getLeftContext(inputLength = 0))
        } catch (e: Exception) {
            Timber.e(e, "Error getZenzLeftContext")
            ""
        }
    }

    /** AzooKey v3: left context は最大 20 文字（改行以降は切り捨て）。 */
    private fun truncateZenzLeftContext(raw: String): String {
        val withoutNewline = raw.substringBefore('\n').substringBefore('\r')
        return if (withoutNewline.length <= ZENZ_LEFT_CONTEXT_MAX) {
            withoutNewline
        } else {
            withoutNewline.takeLast(ZENZ_LEFT_CONTEXT_MAX)
        }
    }

    private suspend fun updateDisplayedCandidates(
        insertString: String,
        candidates: List<Candidate>,
    ) {
        if (!shouldApplyCandidateResult(insertString)) {
            if (inputString.value != insertString) {
                lastLocalUpdatedInput.emit(insertString)
            }
            return
        }
        // supplementaryCandidates は main と分離済み。ライブ変換は mainResults のみ参照する。
        val displayCandidates = com.kazumaproject.markdownhelperkeyboard.converter.core.AzooKeyJapaneseConversionText
            .filterDisplayedCandidates(candidates)
        val request = keyboardSurfaceCoordinator.buildCandidateSurfaceRequest(
            physicalKeyboardEnableReplayFirst = physicalKeyboardEnable.replayCache.firstOrNull() == true &&
                hasHardwareKeyboardConnected == true,
            suppressSuggestions = suppressSuggestions,
        )
        keyboardSurfaceCoordinator.routeCandidateDisplay(
            request = request,
            candidates = displayCandidates,
            host = candidateSurfaceHost,
        )

        if (currentRuntimeConversionPolicy(insertString).allowsPersonalizedConversion) {
            filteredCandidateList = displayCandidates
        }
        if (isZenzEnabledForSession()) {
            lastLocalUpdatedInput.emit(insertString)
        }
    }


    private fun isZenzEnabledForSession(): Boolean {
        return cachedPreferences?.zenzEnableStatePreference ?: (zenzEnableStatePreference == true)
    }

    private fun physicalKeyboardUiHost(
        mainView: MainLayoutBinding,
    ): PhysicalKeyboardUiEffectHandler.PhysicalKeyboardUiHost =
        object : PhysicalKeyboardUiEffectHandler.PhysicalKeyboardUiHost {
            override fun setWindowAnimations(animations: Int) {
                window.window?.setWindowAnimations(animations)
            }

            override fun clearWindowBackgroundBlur() {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    window.window?.setBackgroundBlurRadius(0)
                }
            }

            override fun setDockInputModeLabel(label: String) {
                floatingDockView.setText(label)
            }

            override fun dismissFloatingKeyboard() {
                floatingKeyboardView?.dismiss()
            }

            override fun releaseFloatingKeyboardBackgroundVideo() {
                releaseFloatingKeyboardBackgroundVideoPlayer()
            }

            override fun expandMainRootToScreenHeight() {
                mainView.keyboardView.visibility = View.GONE
                mainView.tabletView.visibility = View.GONE
                mainView.qwertyView.visibility = View.GONE
                mainView.customLayoutDefault.visibility = View.GONE
                mainView.candidatesRowView.visibility = View.GONE
                mainView.keyboardSymbolView.visibility = View.GONE
                mainView.candidateTabLayout.visibility = View.GONE

                (mainView.root.layoutParams as? FrameLayout.LayoutParams)?.let { params ->
                    params.width = ViewGroup.LayoutParams.MATCH_PARENT
                    params.height = ViewGroup.LayoutParams.WRAP_CONTENT
                    mainView.root.layoutParams = params
                }
                updateKeyboardLayout(mainView)
            }

            override fun setMainRootAlpha(alpha: Float) {
                mainView.root.alpha = alpha
            }

            override fun requestCursorUpdates(flags: Int) {
                if (!isSystemUiRemoteInputSession) {
                    this@IMEService.requestCursorUpdates(flags)
                }
            }

            override fun showFloatingDockIfNeeded() {
                this@IMEService.applyThemeToFloatingDockView()
                mainLayoutBinding?.let { mainView ->
                    val parent = floatingDockView.parent
                    if (parent != mainView.root) {
                        (parent as? ViewGroup)?.removeView(floatingDockView)
                        val heightPx = (80 * resources.displayMetrics.density).toInt()
                        val params = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            heightPx,
                            Gravity.BOTTOM
                        )
                        mainView.root.addView(floatingDockView, params)
                    }
                    floatingDockView.visibility = View.VISIBLE
                    mainView.root.requestLayout()
                }
            }

            override fun resetCandidateHighlight() {
                listAdapter.updateHighlightPosition(-1)
                currentHighlightIndex = -1
            }

            override fun resetHenkanFlags() {
                isHenkan.set(false)
                henkanPressedWithBunsetsuDetect = false
            }

            override fun resizeKeyboardForPhysicalKeyboardDisconnect() {
                setKeyboardSizeForHeightPhysicalKeyboard(mainView)
            }

            override fun dismissFloatingCandidateWindow() {
                floatingCandidateWindow?.dismiss()
            }

            override fun dismissFloatingDockWindow() {
                mainLayoutBinding?.let { mainView ->
                    mainView.root.removeView(floatingDockView)
                }
                floatingDockWindow?.dismiss()
            }
        }

    private fun shouldApplyCandidateResult(
        requestInput: String,
        requestToken: Long = zenzRerankRequestToken,
    ): Boolean {
        return !suppressSuggestions &&
                requestInput.isNotEmpty() &&
                inputString.value == requestInput &&
                requestToken == zenzRerankRequestToken
    }

    private fun applyPrivacyStateForEditor(editorInfo: EditorInfo?) {
        val privateModeRequested = editorInfo?.let { info ->
            currentInputType.isPassword() ||
                    (info.imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING) != 0 ||
                    info.imeOptions == 318767106
        } ?: currentInputType.isPassword()

        isPrivateMode = privateModeRequested
        if (privateModeRequested) {
            suggestionAdapter?.setIncognitoIcon(
                ContextCompat.getDrawable(this, com.kazumaproject.core.R.drawable.incognito)
            )
        } else {
            suggestionAdapter?.setIncognitoIcon(null)
        }
    }

    private fun isSystemUiRemoteInput(editorInfo: EditorInfo?): Boolean {
        if (editorInfo?.packageName != "com.android.systemui") return false
        val action = editorInfo.imeOptions and EditorInfo.IME_MASK_ACTION
        return action == EditorInfo.IME_ACTION_SEND ||
            action == EditorInfo.IME_ACTION_DONE ||
            action == EditorInfo.IME_ACTION_UNSPECIFIED ||
            action == EditorInfo.IME_ACTION_NONE
    }

    private fun clearSuggestionStateAfterCommit() {
        if (!isPostCommitPredictionActive) {
            suggestionAdapter?.suggestions = emptyList()
            suggestionAdapterFull?.suggestions = emptyList()
            filteredCandidateList = emptyList()
        }
        if (physicalKeyboardEnable.replayCache.isNotEmpty() && physicalKeyboardEnable.replayCache.first()) {
            updateSuggestionsForFloatingCandidate(emptyList())
            listAdapter.updateHighlightPosition(-1)
            currentHighlightIndex = -1
        }
        scope.launch {
            _suggestionFlag.emit(CandidateShowFlag.Idle)
        }
        refreshReconversionUi()
        mainLayoutBinding?.let { updateUpperAreaVisibility(it) }
    }

    private fun invalidatePostCommitPrediction() {
        isPostCommitPredictionActive = false
        postCommitPredictionRequestId.incrementAndGet()
        postCommitPredictionJob?.cancel()
        postCommitPredictionJob = null
    }

    private fun launchLearningMemoryCommit(block: suspend () -> Unit) {
        ioScope.launch {
            try {
                block()
            } catch (e: Exception) {
                Timber.e(e, "Learning memory commit failed")
                try {
                    learningMemoryRepository.rebuildLoudsFromRoom()
                } catch (rebuildError: Exception) {
                    Timber.e(rebuildError, "Learning memory rebuild from Room failed")
                }
            }
        }
    }

    private fun learnTransitionFromLastCommittedWord(
        committedText: String,
        candidate: Candidate? = null,
    ) {
        val previous = lastCommittedCandidate
        val learnMode = cachedPreferences?.isLearnDictionaryMode ?: (isLearnDictionaryMode == true)
        if (previous != null && candidate != null &&
            AzooKeyTransitionLearningPolicy.shouldLearnTransition(
                AzooKeyTransitionLearningPolicyInput(
                    previousCommittedText = previous.string,
                    committedText = committedText,
                    isLearnDictionaryMode = learnMode,
                    isPrivateMode = isPrivateMode,
                    candidate = candidate,
                )
            )
        ) {
            launchLearningMemoryCommit {
                learningMemoryRepository.commitTransition(
                    previousCandidate = previous,
                    currentCandidate = candidate,
                )
            }
        }
        if (candidate != null) {
            lastCommittedCandidate = candidate
        }
    }

    private fun schedulePostCommitPrediction(committedCandidate: Candidate) {
        val committedText = committedCandidate.string
        if (!AzooKeyPostCommitPredictionPolicy.shouldRequestPrediction(
                AzooKeyPostCommitPredictionPolicyInput(
                    committedText = committedText,
                    isPrivateMode = isPrivateMode,
                    suppressSuggestions = suppressSuggestions,
                    isJapaneseMode = currentInputModeForSession == InputMode.ModeJapanese,
                )
            )
        ) {
            return
        }

        isPostCommitPredictionActive = true
        val token = postCommitPredictionRequestId.incrementAndGet()
        postCommitPredictionJob?.cancel()
        postCommitPredictionJob = scope.launch {
            val candidates = withContext(Dispatchers.IO) {
                val useLearned = AzooKeyPostCommitPredictionPolicy.shouldUseLearnedTransitionsWithFallback(
                    isLearnDictionaryMode = cachedPreferences?.isLearnDictionaryMode
                        ?: isLearnDictionaryMode,
                    predictionLearnSearchEnabled = cachedPreferences?.enablePredictionSearchLearnDictionaryPreference
                        ?: enablePredictionSearchLearnDictionaryPreference,
                )
                candidateCoordinator.predictPostCommitCandidates(
                    leftSideCandidate = committedCandidate,
                    useLearnedTransitions = useLearned,
                )
            }
            if (token != postCommitPredictionRequestId.get()) return@launch
            if (inputString.value.isNotEmpty() || stringInTail.get().isNotEmpty() || isHenkan.get()) {
                isPostCommitPredictionActive = false
                return@launch
            }
            if (candidates.isNotEmpty()) {
                val filtered = com.kazumaproject.markdownhelperkeyboard.converter.core.AzooKeyJapaneseConversionText
                    .filterDisplayedCandidates(candidates)
                suggestionAdapter?.suggestions = filtered
                suggestionAdapterFull?.suggestions = filtered
                filteredCandidateList = filtered
                mainLayoutBinding?.let { updateUpperAreaVisibility(it) }
            } else {
                isPostCommitPredictionActive = false
            }
        }
    }

    private fun updateBunsetsuSpaceKeyIfNeeded(
        mainView: MainLayoutBinding,
        candidates: List<Candidate>,
        insertString: String
    ) {
        if (bunsetsuSeparation == true) {
            bunsetsuPositionList?.let {
                if (bunsetusMultipleDetect && it.isNotEmpty()) {
                    handleJapaneseModeSpaceKeyWithBunsetsu(
                        mainView, candidates, insertString
                    )
                }
            }
        }
    }

    private fun maybeLaunchZenzRerank(
        requestToken: Long,
        insertString: String,
        baseCandidates: List<Candidate>,
        plan: com.kazumaproject.markdownhelperkeyboard.ime_service.candidate.ImeCandidateZenzRerankPlan,
        mainView: MainLayoutBinding,
    ) {
        zenzRerankJob = scope.launch {
            val zenz = buildImeCandidateZenzContext(insertString)
            val reranked = try {
                candidateCoordinator.rerankWithZenz(
                    input = insertString,
                    baseCandidates = baseCandidates,
                    plan = plan,
                    zenz = zenz,
                    preferences = buildImeCandidatePreferences(),
                    cursorPosition = inputString.value.length,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Error rerankCandidatesWithZenz")
                null
            } ?: return@launch

            if (requestToken != zenzRerankRequestToken || reranked == baseCandidates) {
                return@launch
            }

            val prioritized = candidateCoordinator.prioritizeReranked(reranked)
            updateDisplayedCandidates(insertString, prioritized)
            updateBunsetsuSpaceKeyIfNeeded(mainView, prioritized, insertString)
        }
    }

    private fun commonPrefixLength(a: String, b: String): Int {
        val n = minOf(a.length, b.length)
        var i = 0
        while (i < n && a[i] == b[i]) i++
        return i
    }

    private fun String.duplicateCharCount(): Int {
        val counts = this.groupingBy { it }.eachCount()
        return counts.values.sumOf { (it - 1).coerceAtLeast(0) } // 2回目以降の総数
    }

    private fun ZenzCandidate.rank(prefix: String): Int {
        val prefixScore = commonPrefixLength(this.string, prefix) * 10
        val kanjiScore = this.string.kanjiCount() * 3

        // ★ここを強めに：重複が多い候補は大きく減点
        val duplicatePenalty = this.string.duplicateCharCount() * 50

        val typeBonus = if (this.type == (40).toByte()) 2 else 0

        return prefixScore + kanjiScore + typeBonus - duplicatePenalty
    }

    private fun getKeyboardSizePreferences(): KeyboardSizePreferences {
        val isPortrait = resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
        return if (isPortrait) {
            KeyboardSizePreferences(
                heightPref = tenkeyHeightPreferenceValue ?: 280,
                widthPref = tenkeyWidthPreferenceValue ?: 100,
                bottomMargin = tenkeyBottomMarginPreferenceValue ?: 0,
                positionIsEnd = tenkeyPositionPreferenceValue ?: true,
                candidateEmptyHeight = candidateViewHeightEmptyPreferenceValue ?: 110,
                qwertyHeightPref = qwertyHeightPreferenceValue ?: 280,
                qwertyWidthPref = qwertyWidthPreferenceValue ?: 100,
                qwertyBottomMargin = qwertyBottomMarginPreferenceValue ?: 0,
                qwertyPositionIsEnd = qwertyPositionPreferenceValue ?: true,
                keyboardMarginStart = tenkeyStartMarginPreferenceValue ?: 0,
                keyboardMarginEnd = tenkeyEndMarginPreferenceValue ?: 0,
                qwertyMarginStart = qwertyStartMarginPreferenceValue ?: 0,
                qwertyMarginEnd = qwertyEndMarginPreferenceValue ?: 0
            )
        } else {
            KeyboardSizePreferences(
                heightPref = tenkeyHeightLandScapePreferenceValue ?: 220,
                widthPref = tenkeyWidthLandScapePreferenceValue ?: 100,
                bottomMargin = tenkeyLandScapeBottomMarginPreferenceValue ?: 0,
                positionIsEnd = tenkeyLandScapePositionPreferenceValue ?: true,
                candidateEmptyHeight = candidateViewLandScapeHeightEmptyPreferenceValue ?: 110,
                qwertyHeightPref = qwertyHeightLandScapePreferenceValue ?: 220,
                qwertyWidthPref = qwertyWidthLandScapePreferenceValue ?: 100,
                qwertyBottomMargin = qwertyLandScapeBottomMarginPreferenceValue ?: 0,
                qwertyPositionIsEnd = qwertyLandScapePositionPreferenceValue ?: true,
                keyboardMarginStart = tenkeyLandScapeStartMarginPreferenceValue ?: 0,
                keyboardMarginEnd = tenkeyLandScapeEndMarginPreferenceValue ?: 0,
                qwertyMarginStart = qwertyLandScapeStartMarginPreferenceValue ?: 0,
                qwertyMarginEnd = qwertyLandScapeEndMarginPreferenceValue ?: 0
            )
        }
    }

    /**
     * キーボードのレイアウトサイズを計算し、ビューに適用する統合関数
     * @param mainView バインディングオブジェクト
     * @param isSymbolOverride シンボルキーボード状態を強制的に上書きする場合にtrue/falseを指定
     * @param isFloating フローティングモードの場合にtrue
     * @param addCandidateTabHeight 候補タブの高さを追加する場合にtrue
     */
    private fun updateKeyboardLayout(
        mainView: MainLayoutBinding,
        isSymbolOverride: Boolean? = null,
        isFloating: Boolean = false,
        addCandidateTabHeight: Boolean = false
    ) {
        // 1. 設定値の読み込み
        val prefs = getKeyboardSizePreferences()
        val isPortrait = resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
        val density = resources.displayMetrics.density
        val screenWidth = resources.displayMetrics.widthPixels
        val isSymbol = isSymbolOverride ?: keyboardSymbolViewState.value.isShown
        val symbolWithInputKeyboard = isSymbol && symbolPanelSearchFocused

        // 2. ピクセル値の計算
        val heightPx = when {
            qwertyMode.value == TenKeyQWERTYMode.TenKeyQWERTY || qwertyMode.value == TenKeyQWERTYMode.TenKeyQWERTYRomaji -> {
                val clampedHeight = if (isPortrait) {
                    prefs.qwertyHeightPref.coerceIn(100, 420)
                } else if (isFloating) {
                    prefs.qwertyHeightPref
                } else {
                    prefs.qwertyHeightPref.coerceIn(100, 420)
                }
                (clampedHeight * density).toInt()
            }

            else -> {
                val clampedHeight = if (isPortrait) {
                    prefs.heightPref.coerceIn(100, 420)
                } else if (isFloating) {
                    prefs.heightPref
                } else {
                    prefs.heightPref.coerceIn(100, 420)
                }
                (clampedHeight * density).toInt()
            }
        }

        val widthPx = when {
            prefs.widthPref == 100 -> ViewGroup.LayoutParams.MATCH_PARENT
            else -> (screenWidth * (prefs.widthPref / 100f)).toInt()
        }

        val qwertyWidthPx = when {
            prefs.qwertyWidthPref == 100 -> ViewGroup.LayoutParams.MATCH_PARENT
            else -> (screenWidth * (prefs.qwertyWidthPref / 100f)).toInt()
        }

        // 3. 最終的な高さ、幅、Gravity、マージンの決定
        val toolbarHeight = dpToPx(40)
        val candidateTabHeight = if (
            !isPortrait &&
            candidateTabVisibility == true &&
            mainView.candidateTabLayout.isVisible
        ) {
            mainView.candidateTabLayout.height.takeIf { it > 0 } ?: dpToPx(36)
        } else {
            0
        }
        val baseKeyboardHeight = heightPx + toolbarHeight + candidateTabHeight

        val finalKeyboardHeight = when {
            symbolWithInputKeyboard -> toolbarHeight + (heightPx * 1.58f).toInt().coerceAtLeast(dpToPx(200)) + systemBottomInset
            else -> baseKeyboardHeight + systemBottomInset
        }

        val finalKeyboardWidth =
            if (isSymbol) {
                ViewGroup.LayoutParams.MATCH_PARENT
            } else if (qwertyMode.value == TenKeyQWERTYMode.TenKeyQWERTY || qwertyMode.value == TenKeyQWERTYMode.TenKeyQWERTYRomaji) {
                qwertyWidthPx
            } else {
                widthPx
            }

        val finalStartMargin =
            if (isSymbol) {
                0
            } else if (qwertyMode.value == TenKeyQWERTYMode.TenKeyQWERTY || qwertyMode.value == TenKeyQWERTYMode.TenKeyQWERTYRomaji) {
                dpToPx(prefs.qwertyMarginStart)
            } else {
                dpToPx(prefs.keyboardMarginStart)
            }

        val finalEndMargin =
            if (isSymbol) {
                0
            } else if (qwertyMode.value == TenKeyQWERTYMode.TenKeyQWERTY || qwertyMode.value == TenKeyQWERTYMode.TenKeyQWERTYRomaji) {
                dpToPx(prefs.qwertyMarginEnd)
            } else {
                dpToPx(prefs.keyboardMarginEnd)
            }

        val finalBottomMargin =
            if (qwertyMode.value == TenKeyQWERTYMode.TenKeyQWERTY || qwertyMode.value == TenKeyQWERTYMode.TenKeyQWERTYRomaji) {
                prefs.qwertyBottomMargin
            } else {
                prefs.bottomMargin
            }

        val positionIsEnd =
            if (qwertyMode.value == TenKeyQWERTYMode.TenKeyQWERTY || qwertyMode.value == TenKeyQWERTYMode.TenKeyQWERTYRomaji) {
                prefs.qwertyPositionIsEnd
            } else {
                prefs.positionIsEnd
            }
        val gravity =
            if (positionIsEnd) (Gravity.BOTTOM or Gravity.END) else (Gravity.BOTTOM or Gravity.START)

        // 4. レイアウトパラメータの適用
        applyKeyboardLayoutParameters(
            mainView = mainView,
            heightPx = heightPx,
            finalKeyboardHeight = finalKeyboardHeight,
            finalKeyboardWidth = finalKeyboardWidth,
            gravity = gravity,
            finalBottomMargin = finalBottomMargin,
            finalStartMargin = finalStartMargin,
            finalEndMargin = finalEndMargin,
            isPortrait = isPortrait,
        )

        (mainView.keyboardSymbolView.layoutParams as? FrameLayout.LayoutParams)?.let { param ->
            param.height = heightPx
            param.width = finalKeyboardWidth
            param.topMargin = if (isPortrait) toolbarHeight else 0
            param.bottomMargin = if (isPortrait) 0 else finalBottomMargin
            param.gravity = if (isPortrait) {
                Gravity.TOP or (gravity and Gravity.HORIZONTAL_GRAVITY_MASK)
            } else {
                gravity
            }
            mainView.keyboardSymbolView.layoutParams = param
        }

        if (symbolWithInputKeyboard) {
            applySymbolPanelWithKeyboardSplitLayout(
                mainView = mainView,
                keyboardHeightPx = heightPx,
                width = finalKeyboardWidth,
                gravity = gravity,
            )
        }

        if (isTabletGojuonSurface()) {
            (mainView.tabletView.layoutParams as? FrameLayout.LayoutParams)?.let { param ->
                param.height = heightPx
                mainView.tabletView.layoutParams = param
            }
        }

        // 5. 個別処理
        if (addCandidateTabHeight) {
            val params = mainView.suggestionVisibility.layoutParams as ConstraintLayout.LayoutParams
            params.bottomToBottom = ConstraintLayout.LayoutParams.UNSET
            mainView.suggestionVisibility.layoutParams = params
        }
        updateUpperAreaVisibility(mainView)
        enforcePhysicalKeyboardUiState()
    }

    private fun updateUpperAreaVisibility(mainView: MainLayoutBinding) {
        if (hasHardwareKeyboardConnected == true) {
            mainView.suggestionViewParent.visibility = View.GONE
            return
        }
        val hasSuggestions = suggestionAdapter?.suggestions?.isNotEmpty() == true
        val hasInlineSuggestions = suggestionAdapter?.hasInlineSuggestions() == true
        val hasClipboardPreview = suggestionAdapter?.hasClipboardPreview() == true
        val hasCandidateSuggestions = hasSuggestions || hasInlineSuggestions || hasClipboardPreview

        if (hasCandidateSuggestions && !lastShowSuggestion) {
            isToolbarForcedShow = false
        }
        lastShowSuggestion = hasCandidateSuggestions
        val showSuggestion = hasCandidateSuggestions && !isToolbarForcedShow
        val upperAreaState = UpperAreaState(
            showSuggestion = showSuggestion,
            hasCandidateSuggestions = hasCandidateSuggestions,
            privateMode = isPrivateMode,
            forcedToolbar = isToolbarForcedShow,
        )
        val stateChanged = lastUpperAreaState != upperAreaState
        lastUpperAreaState = upperAreaState

        fun applyUpperAreaState() {
            mainView.suggestionViewParent.visibility = View.VISIBLE
            mainView.toolbarToggleButton.visibility = View.VISIBLE
            mainView.upperAreaContentContainer.visibility = View.VISIBLE
            mainView.suggestionViewParent.clearAnimation()
            mainView.shortcutToolbarRecyclerview.clearAnimation()
            mainView.suggestionRecyclerView.clearAnimation()
            mainView.toolbarToggleButton.clearAnimation()
            mainView.suggestionViewParent.alpha = 1f
            mainView.shortcutToolbarRecyclerview.alpha = 1f
            mainView.suggestionRecyclerView.alpha = 1f
            mainView.toolbarToggleButton.alpha = 1f
            mainView.suggestionViewParent.elevation = 0f
            mainView.suggestionViewParent.translationZ = 0f
            mainView.suggestionViewParent.bringToFront()
            mainView.upperAreaContentContainer.bringToFront()
            mainView.toolbarToggleButton.bringToFront()

            if (showSuggestion) {
                mainView.suggestionRecyclerView.visibility = View.VISIBLE
                mainView.shortcutToolbarRecyclerview.visibility = View.GONE
                mainView.toolbarToggleButton.setImageResource(com.kazumaproject.core.R.drawable.baseline_arrow_left_24)
            } else {
                mainView.suggestionRecyclerView.visibility = View.GONE
                mainView.shortcutToolbarRecyclerview.visibility = View.VISIBLE
                if (isPrivateMode) {
                    mainView.toolbarToggleButton.setImageResource(com.kazumaproject.core.R.drawable.incognito)
                } else {
                    mainView.toolbarToggleButton.setImageResource(com.kazumaproject.core.R.drawable.baseline_menu_24)
                }
            }
        }

        applyUpperAreaState()

        val targetHeight = dpToPx(40)
        if (mainView.suggestionViewParent.layoutParams.height != targetHeight) {
            mainView.suggestionViewParent.layoutParams = mainView.suggestionViewParent.layoutParams.apply {
                height = targetHeight
            }
        }
        (mainView.suggestionViewParent.layoutParams as? FrameLayout.LayoutParams)?.let { params ->
            val horizontalGravity = params.gravity and Gravity.HORIZONTAL_GRAVITY_MASK
            val toolbarHorizontalGravity = horizontalGravity.takeIf { it != 0 } ?: Gravity.START
            params.gravity = Gravity.TOP or toolbarHorizontalGravity
            params.topMargin = 0
            params.bottomMargin = 0
            mainView.suggestionViewParent.layoutParams = params
        }

        if (stateChanged) {
            mainView.root.post {
                applyUpperAreaState()
            }
        }
    }

    /**
     * 計算されたレイアウトパラメータを各ビューに適用するヘルパー関数
     */
    private fun applyKeyboardLayoutParameters(
        mainView: MainLayoutBinding,
        heightPx: Int,
        finalKeyboardHeight: Int,
        finalKeyboardWidth: Int,
        gravity: Int,
        finalBottomMargin: Int,
        finalStartMargin: Int,
        finalEndMargin: Int,
        isPortrait: Boolean = resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT,
    ) {
        if (hasHardwareKeyboardConnected == true) {
            val wrapContent = ViewGroup.LayoutParams.WRAP_CONTENT
            (mainView.root.layoutParams as? FrameLayout.LayoutParams)?.let { params ->
                params.height = wrapContent
                params.width = ViewGroup.LayoutParams.MATCH_PARENT
                params.bottomMargin = 0
                params.leftMargin = 0
                params.rightMargin = 0
                params.gravity = Gravity.BOTTOM
                mainView.root.layoutParams = params
            }
            mainView.root.minimumHeight = 0
            keyboardContainer?.let { container ->
                container.minimumHeight = 0
                (container.layoutParams as? ViewGroup.LayoutParams)?.let { params ->
                    params.height = wrapContent
                    params.width = ViewGroup.LayoutParams.MATCH_PARENT
                    container.layoutParams = params
                }
            }
            return
        }
        val toolbarHeight = dpToPx(40)
        val horizontalGravity = gravity and Gravity.HORIZONTAL_GRAVITY_MASK
        val toolbarHorizontalGravity = horizontalGravity.takeIf { it != 0 } ?: Gravity.START
        val toolbarGravity = Gravity.TOP or toolbarHorizontalGravity
        val keyboardViews = listOf(
            mainView.keyboardView,
            mainView.tabletView,
            mainView.customLayoutDefault,
            mainView.qwertyView,
            mainView.candidatesRowView,
            mainView.keyboardSymbolView,
        )
        (mainView.suggestionViewParent.layoutParams as? FrameLayout.LayoutParams)?.let { params ->
            params.height = toolbarHeight
            params.topMargin = 0
            params.bottomMargin = 0
            params.gravity = toolbarGravity
            mainView.suggestionViewParent.layoutParams = params
        }
        keyboardViews.forEach { view ->
            (view.layoutParams as? FrameLayout.LayoutParams)?.let { params ->
                params.height = heightPx
                if (isPortrait) {
                    params.topMargin = toolbarHeight
                    params.bottomMargin = 0
                    params.gravity = Gravity.TOP or horizontalGravity
                } else {
                    params.topMargin = 0
                    params.bottomMargin = finalBottomMargin
                    params.gravity = gravity
                }
                view.layoutParams = params
            }
        }
        (mainView.candidateTabLayout.layoutParams as? FrameLayout.LayoutParams)?.let { params ->
            if (!isPortrait && candidateTabVisibility == true && mainView.candidateTabLayout.isVisible) {
                params.height = dpToPx(36)
                params.topMargin = 0
                params.bottomMargin = heightPx + finalBottomMargin
                params.gravity = gravity
            } else {
                params.bottomMargin = 0
            }
            mainView.candidateTabLayout.layoutParams = params
        }

        (mainView.root.layoutParams as? FrameLayout.LayoutParams)?.let { params ->
            params.height = finalKeyboardHeight
            params.width = finalKeyboardWidth
            params.bottomMargin = finalBottomMargin
            params.leftMargin = finalStartMargin
            params.rightMargin = finalEndMargin
            params.gravity = gravity
            mainView.root.layoutParams = params
        } ?: run {
            mainView.root.layoutParams = FrameLayout.LayoutParams(
                finalKeyboardWidth,
                finalKeyboardHeight,
                gravity
            ).apply {
                bottomMargin = finalBottomMargin
                leftMargin = finalStartMargin
                rightMargin = finalEndMargin
            }
        }
        mainView.root.minimumHeight = finalKeyboardHeight
        keyboardContainer?.let { container ->
            container.minimumHeight = finalKeyboardHeight
            container.layoutParams = (container.layoutParams ?: ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                finalKeyboardHeight
            )).apply {
                width = ViewGroup.LayoutParams.MATCH_PARENT
                height = finalKeyboardHeight
            }
        }

        mainView.root.setPadding(0, 0, 0, systemBottomInset)
        enforcePhysicalKeyboardUiState()
    }

    private fun setKeyboardHeightWithAdditionalOriginal(mainView: MainLayoutBinding) {
        Timber.d("Keyboard Height: setKeyboardHeightWithAdditional called")
        if (currentInputType.isPassword()) return
        val isPortrait = resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
        val screenWidth = resources.displayMetrics.widthPixels
        val density = resources.displayMetrics.density

        val heightPref = if (isPortrait) {
            tenkeyHeightPreferenceValue ?: 280
        } else {
            tenkeyHeightLandScapePreferenceValue ?: 220
        }
        val widthPref = if (isPortrait) {
            tenkeyWidthPreferenceValue ?: 100
        } else {
            tenkeyWidthLandScapePreferenceValue ?: 100
        }
        val keyboardBottomMargin = if (isPortrait) {
            tenkeyBottomMarginPreferenceValue ?: 0
        } else {
            tenkeyLandScapeBottomMarginPreferenceValue ?: 0
        }

        val positionPref = if (isPortrait) {
            tenkeyPositionPreferenceValue ?: true
        } else {
            tenkeyLandScapePositionPreferenceValue ?: true
        }
        val qwertyHeightPref = if (isPortrait) {
            qwertyHeightPreferenceValue ?: 280
        } else {
            qwertyHeightLandScapePreferenceValue ?: 220
        }
        val qwertyWidthPref = if (isPortrait) {
            qwertyWidthPreferenceValue ?: 100
        } else {
            qwertyWidthLandScapePreferenceValue ?: 100
        }
        val qwertyPositionPref = if (isPortrait) {
            qwertyPositionPreferenceValue ?: true
        } else {
            qwertyLandScapePositionPreferenceValue ?: true
        }
        val qwertyKeyboardMarginBottomPref = if (isPortrait) {
            qwertyBottomMarginPreferenceValue ?: 0
        } else {
            qwertyLandScapeBottomMarginPreferenceValue ?: 0
        }

        val heightPx = when {
            keyboardSymbolViewState.value.isShown -> {
                val clampedHeight = if (qwertyMode.value == TenKeyQWERTYMode.TenKeyQWERTY || qwertyMode.value == TenKeyQWERTYMode.TenKeyQWERTYRomaji) {
                    qwertyHeightPref.coerceIn(60, 420)
                } else {
                    heightPref.coerceIn(60, 420)
                }
                (clampedHeight * density).toInt()
            }

            qwertyMode.value == TenKeyQWERTYMode.TenKeyQWERTY || qwertyMode.value == TenKeyQWERTYMode.TenKeyQWERTYRomaji -> {
                val clampedHeight = qwertyHeightPref.coerceIn(60, 420)
                (clampedHeight * density).toInt()
            }

            else -> {
                val clampedHeight = heightPref.coerceIn(60, 420)
                (clampedHeight * density).toInt()
            }
        }

        val widthPx = when {
            widthPref == 100 -> {
                ViewGroup.LayoutParams.MATCH_PARENT
            }

            else -> {
                (screenWidth * (widthPref / 100f)).toInt()
            }
        }

        val qwertyWidthPx = when {
            qwertyWidthPref == 100 -> {
                ViewGroup.LayoutParams.MATCH_PARENT
            }

            else -> {
                (screenWidth * (qwertyWidthPref / 100f)).toInt()
            }
        }

        val suggestionHeightInDp = if (isPortrait) {
            candidateViewHeightPreferenceValue ?: 110
        } else {
            candidateViewLandScapeHeightPreferenceValue ?: 110
        }

        val keyboardHeight = if (isPortrait) {
            if (keyboardSymbolViewState.value.isShown) heightPx + applicationContext.dpToPx(50) else heightPx + applicationContext.dpToPx(
                suggestionHeightInDp
            )
        } else {
            if (keyboardSymbolViewState.value.isShown) heightPx else heightPx + applicationContext.dpToPx(
                suggestionHeightInDp
            )
        }

        val finalKeyboardHeight = if (candidateTabVisibility == true) {
            keyboardHeight + mainView.candidateTabLayout.height
        } else {
            keyboardHeight
        } + systemBottomInset

        val finalKeyboardWidth =
            if (qwertyMode.value == TenKeyQWERTYMode.TenKeyQWERTY || qwertyMode.value == TenKeyQWERTYMode.TenKeyQWERTYRomaji) {
                qwertyWidthPx
            } else {
                widthPx
            }

        val gravity =
            if (qwertyMode.value == TenKeyQWERTYMode.TenKeyQWERTY || qwertyMode.value == TenKeyQWERTYMode.TenKeyQWERTYRomaji) {
                if (qwertyPositionPref) {
                    Gravity.BOTTOM or Gravity.END
                } else {
                    Gravity.BOTTOM or Gravity.START
                }
            } else {
                if (positionPref) {
                    Gravity.BOTTOM or Gravity.END
                } else {
                    Gravity.BOTTOM or Gravity.START
                }
            }

        val finalBottomMargin =
            if (qwertyMode.value == TenKeyQWERTYMode.TenKeyQWERTY || qwertyMode.value == TenKeyQWERTYMode.TenKeyQWERTYRomaji) {
                qwertyKeyboardMarginBottomPref
            } else {
                keyboardBottomMargin
            }

        listOf(
            mainView.suggestionViewParent,
            mainView.keyboardView,
            mainView.customLayoutDefault,
            mainView.qwertyView,
            mainView.keyboardSymbolView
        ).forEach { view ->
            (view.layoutParams as? FrameLayout.LayoutParams)?.let { params ->
                if (view != mainView.suggestionViewParent) params.height = heightPx
                else params.bottomMargin = heightPx
                params.gravity = gravity
                view.layoutParams = params
            }
        }

        (mainView.root.layoutParams as? FrameLayout.LayoutParams)?.let { params ->
            params.height = finalKeyboardHeight
            params.width = finalKeyboardWidth
            params.bottomMargin = finalBottomMargin
            mainView.root.layoutParams = params
        }

        // Adjust suggestion view constraints since it's no longer attached to the parent bottom
        val params = mainView.suggestionVisibility.layoutParams as ConstraintLayout.LayoutParams
        params.bottomToBottom = ConstraintLayout.LayoutParams.UNSET
        mainView.suggestionVisibility.layoutParams = params

        mainView.root.setPadding(0, 0, 0, systemBottomInset)
    }

    private fun setKeyboardSizeForHeightPhysicalKeyboard(mainView: MainLayoutBinding) {
        Timber.d("Keyboard Height: setKeyboardSizeForHeight called $hasHardwareKeyboardConnected")
        updateKeyboardLayout(mainView)
    }

    private fun setKeyboardSizeForHeightSymbol(mainView: MainLayoutBinding, isSymbol: Boolean) {
        Timber.d("Keyboard Height: setKeyboardSizeForHeightSymbol called")
        updateKeyboardLayout(mainView, isSymbolOverride = isSymbol)
    }

    private fun setKeyboardSizeForHeightForFloatingMode(mainView: MainLayoutBinding) {
        Timber.d("Keyboard Height: setKeyboardSizeForHeightForFloatingMode called")
        updateKeyboardLayout(mainView, isFloating = true)
    }

    private fun setKeyboardHeightWithAdditional(mainView: MainLayoutBinding) {
        Timber.d("Keyboard Height: setKeyboardHeightWithAdditional called")
        updateKeyboardLayout(mainView)
    }

    private fun setKeyboardHeightDefault(mainView: MainLayoutBinding) {
        Timber.d("Keyboard Height: setKeyboardHeightDefault called")
        if (isKeyboardFloatingMode == true) return
        updateKeyboardLayout(mainView)
    }

    private fun setKeyboardSizeSwitchKeyboard(mainView: MainLayoutBinding) {
        Timber.d("Keyboard Height: setKeyboardSizeSwitchKeyboard called")
        if (isKeyboardFloatingMode == true) return
        updateKeyboardLayout(mainView)
    }

    private fun updateSuggestionViewVisibility(
        mainView: MainLayoutBinding, isVisible: Boolean
    ) {
        if (isKeyboardFloatingMode == true) {
            floatingKeyboardBinding?.let { floatingKeyboardLayoutBinding ->
                val activeFloatingKeyboardView = when (qwertyMode.value) {
                    TenKeyQWERTYMode.TenKeyQWERTY,
                    TenKeyQWERTYMode.TenKeyQWERTYRomaji -> floatingKeyboardLayoutBinding.qwertyViewFloating

                    TenKeyQWERTYMode.Custom,
                    TenKeyQWERTYMode.Sumire,
                    TenKeyQWERTYMode.Number -> floatingKeyboardLayoutBinding.customLayoutFloating

                    TenKeyQWERTYMode.Default -> floatingKeyboardLayoutBinding.keyboardViewFloating
                }
                animateViewVisibility(floatingKeyboardLayoutBinding.candidatesRowView, !isVisible)
                activeFloatingKeyboardView.isVisible = isVisible
                hideFirstRowCandidatesInFullScreenFloating(floatingKeyboardLayoutBinding)
                floatingKeyboardLayoutBinding.candidatesRowView.scrollToPosition(0)
                if (isVisible) {
                    if (activeFloatingKeyboardView.isInvisible) {
                        animateViewVisibility(
                            activeFloatingKeyboardView,
                            isVisible = true,
                            true
                        )
                    }
                } else {
                    activeFloatingKeyboardView.visibility = View.INVISIBLE
                }
                floatingKeyboardLayoutBinding.suggestionVisibility.apply {
                    this.setImageDrawable(if (isVisible) cachedArrowDropDownDrawable else cachedArrowDropUpDrawable)
                }
            }
        }
        animateViewVisibility(mainView.candidatesRowView, !isVisible)
        mainView.candidatesRowView.scrollToPosition(0)
        hideFirstRowCandidatesInFullScreen(mainView)
        if (isVisible) {
            mainLayoutBinding?.apply {
                when {
                    customLayoutDefault.isInvisible -> {
                        animateViewVisibility(
                            customLayoutDefault, isVisible = true, true
                        )
                    }

                    keyboardView.isInvisible -> {
                        animateViewVisibility(
                            keyboardView, isVisible = true, true
                        )
                    }

                    qwertyView.isInvisible -> {
                        animateViewVisibility(
                            qwertyView, isVisible = true, true
                        )
                    }

                    tabletView.isInvisible -> {
                        animateViewVisibility(
                            tabletView, isVisible = true, true
                        )
                    }
                }
            }
        } else {
            mainLayoutBinding?.apply {
                when {
                    keyboardView.isVisible -> keyboardView.visibility = View.INVISIBLE
                    qwertyView.isVisible -> qwertyView.visibility = View.INVISIBLE
                    customLayoutDefault.isVisible -> customLayoutDefault.visibility = View.INVISIBLE
                    tabletView.isVisible -> tabletView.visibility = View.INVISIBLE
                }
            }
        }
        mainView.suggestionVisibility.apply {
            this.setImageDrawable(if (isVisible) cachedArrowDropDownDrawable else cachedArrowDropUpDrawable)
        }
        updateUpperAreaVisibility(mainView)
    }

    private fun animateViewVisibility(
        mainView: View, isVisible: Boolean, withAnimation: Boolean = true
    ) {
        if (hasHardwareKeyboardConnected == true) {
            val isKeyboardComponent = mainLayoutBinding?.let { mainViewBinding ->
                mainView == mainViewBinding.keyboardView ||
                mainView == mainViewBinding.tabletView ||
                mainView == mainViewBinding.customLayoutDefault ||
                mainView == mainViewBinding.qwertyView ||
                mainView == mainViewBinding.candidatesRowView ||
                mainView == mainViewBinding.keyboardSymbolView
            } ?: false
            if (isKeyboardComponent) {
                mainView.visibility = View.GONE
                return
            }
        }
        mainView.animate().cancel()

        if (isVisible) {
            mainView.visibility = View.VISIBLE

            if (withAnimation) {
                mainView.translationY = mainView.height.toFloat() // Start from hidden position
                mainView.animate().translationY(0f) // Animate to visible position
                    .setDuration(150).setInterpolator(AccelerateDecelerateInterpolator()).start()
            } else {
                mainView.translationY = 0f
            }
        } else {
            if (withAnimation) {
                mainView.translationY = 0f
                mainView.animate().translationY(mainView.height.toFloat()).setDuration(200)
                    .setInterpolator(AccelerateDecelerateInterpolator()).withEndAction {
                        mainView.visibility = View.GONE
                    }.start()
            } else {
                mainView.visibility = View.GONE
            }
        }
    }

    private fun enforcePhysicalKeyboardUiState() {
        if (hasHardwareKeyboardConnected == true) {
            mainLayoutBinding?.let { mainView ->
                mainView.suggestionViewParent.visibility = View.GONE
                mainView.keyboardView.visibility = View.GONE
                mainView.tabletView.visibility = View.GONE
                mainView.customLayoutDefault.visibility = View.GONE
                mainView.qwertyView.visibility = View.GONE
                mainView.candidatesRowView.visibility = View.GONE
                mainView.keyboardSymbolView.visibility = View.GONE

                mainView.root.minimumHeight = 0
                (mainView.root.layoutParams as? FrameLayout.LayoutParams)?.let { params ->
                    params.height = ViewGroup.LayoutParams.WRAP_CONTENT
                    params.width = ViewGroup.LayoutParams.MATCH_PARENT
                    params.bottomMargin = 0
                    params.leftMargin = 0
                    params.rightMargin = 0
                    mainView.root.layoutParams = params
                }

                keyboardContainer?.let { container ->
                    container.minimumHeight = 0
                    (container.layoutParams as? ViewGroup.LayoutParams)?.let { params ->
                        params.height = ViewGroup.LayoutParams.WRAP_CONTENT
                        params.width = ViewGroup.LayoutParams.MATCH_PARENT
                        container.layoutParams = params
                    }
                }
            }
        }
    }

    private fun applyThemeToFloatingDockView() {
        if (!::floatingDockView.isInitialized) return
        val isDynamic = DynamicColors.isDynamicColorAvailable()
        val fallbackColor = ContextCompat.getColor(this, com.kazumaproject.core.R.color.keyboard_bg)
        val defaultColor = if (isDynamic) {
            getThemeColorOrFallback(
                attrRes = MaterialR.attr.colorSurfaceContainer,
                fallbackColor = fallbackColor
            )
        } else {
            fallbackColor
        }
        val customColor = customThemeBgColor ?: Color.WHITE
        val bgColor = when (keyboardThemeMode) {
            "custom" -> customColor
            "dark" -> Color.parseColor("#121212")
            "light" -> Color.parseColor("#F5F5F5")
            else -> defaultColor
        }

        val fallbackTextColor = ContextCompat.getColor(this, com.kazumaproject.core.R.color.keyboard_icon_color)
        val defaultTextColor = if (isDynamic) {
            getThemeColorOrFallback(
                attrRes = MaterialR.attr.colorOnSurface,
                fallbackColor = fallbackTextColor
            )
        } else {
            val isNight = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            if (isNight) Color.WHITE else Color.BLACK
        }
        val textColor = when (keyboardThemeMode) {
            "custom" -> customThemeKeyTextColor ?: defaultTextColor
            "dark" -> Color.WHITE
            "light" -> Color.BLACK
            else -> defaultTextColor
        }

        val defaultIconBgColor = if (isDynamic) {
            getThemeColorOrFallback(
                attrRes = MaterialR.attr.colorSurfaceContainerHigh,
                fallbackColor = ContextCompat.getColor(this, com.kazumaproject.core.R.color.enter_key_bg)
            )
        } else {
            val isNight = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            if (isNight) Color.parseColor("#303030") else Color.parseColor("#E0E0E0")
        }
        val iconBgColor = when (keyboardThemeMode) {
            "custom" -> customThemeSpecialKeyColor ?: defaultIconBgColor
            "dark" -> Color.parseColor("#303030")
            "light" -> Color.parseColor("#E0E0E0")
            else -> defaultIconBgColor
        }

        floatingDockView.applyThemeColors(
            backgroundColor = bgColor,
            textColor = textColor,
            iconBackgroundColor = iconBgColor
        )
    }

    private fun applyThemeToSuggestionAdapter() {
        val adapter = suggestionAdapter ?: return
        val adapterFull = suggestionAdapterFull

        val isNight = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

        val candidateTextColorVal: Int
        val candidateItemBgColorVal: Int
        val candidateItemPressedBgColorVal: Int
        val emptyDrawableColorVal: Int
        val emptyDrawableTextColorVal: Int

        when (keyboardThemeMode) {
            "custom" -> {
                val isCustomDark = !(customThemeBgColor ?: Color.WHITE).isLightColor()
                candidateTextColorVal = customThemeCandidateTextColor ?: (if (isCustomDark) Color.WHITE else Color.BLACK)
                candidateItemBgColorVal = customThemeCandidateItemBgColor ?: Color.TRANSPARENT
                candidateItemPressedBgColorVal = customThemeCandidateItemPressedBgColor
                    ?: ContextCompat.getColor(this, com.kazumaproject.core.R.color.qwety_key_bg_color)
                emptyDrawableColorVal = customThemeSpecialKeyColor ?: (if (isCustomDark) Color.parseColor("#303030") else Color.parseColor("#E0E0E0"))
                emptyDrawableTextColorVal = customThemeSpecialKeyTextColor ?: (if (isCustomDark) Color.WHITE else Color.BLACK)
            }
            "dark" -> {
                candidateTextColorVal = Color.WHITE
                candidateItemBgColorVal = Color.TRANSPARENT
                candidateItemPressedBgColorVal = Color.DKGRAY
                emptyDrawableColorVal = Color.parseColor("#303030")
                emptyDrawableTextColorVal = Color.WHITE
            }
            "light" -> {
                candidateTextColorVal = Color.BLACK
                candidateItemBgColorVal = Color.TRANSPARENT
                candidateItemPressedBgColorVal = Color.LTGRAY
                emptyDrawableColorVal = Color.parseColor("#E0E0E0")
                emptyDrawableTextColorVal = Color.BLACK
            }
            else -> {
                val isDynamic = DynamicColors.isDynamicColorAvailable()
                if (isDynamic) {
                    val fallbackTextColor = ContextCompat.getColor(this, com.kazumaproject.core.R.color.keyboard_icon_color)
                    val defaultTextColor = getThemeColorOrFallback(
                        attrRes = MaterialR.attr.colorOnSurface,
                        fallbackColor = fallbackTextColor
                    )
                    val defaultIconBgColor = getThemeColorOrFallback(
                        attrRes = MaterialR.attr.colorSurfaceContainerHigh,
                        fallbackColor = ContextCompat.getColor(this, com.kazumaproject.core.R.color.enter_key_bg)
                    )
                    candidateTextColorVal = defaultTextColor
                    candidateItemBgColorVal = Color.TRANSPARENT
                    candidateItemPressedBgColorVal = getThemeColorOrFallback(
                        attrRes = MaterialR.attr.colorSurfaceContainerLow,
                        fallbackColor = Color.LTGRAY
                    )
                    emptyDrawableColorVal = defaultIconBgColor
                    emptyDrawableTextColorVal = defaultTextColor
                } else {
                    if (isNight) {
                        candidateTextColorVal = Color.WHITE
                        candidateItemBgColorVal = Color.TRANSPARENT
                        candidateItemPressedBgColorVal = Color.DKGRAY
                        emptyDrawableColorVal = Color.parseColor("#303030")
                        emptyDrawableTextColorVal = Color.WHITE
                    } else {
                        candidateTextColorVal = Color.BLACK
                        candidateItemBgColorVal = Color.TRANSPARENT
                        candidateItemPressedBgColorVal = Color.LTGRAY
                        emptyDrawableColorVal = Color.parseColor("#E0E0E0")
                        emptyDrawableTextColorVal = Color.BLACK
                    }
                }
            }
        }

        listOfNotNull(adapter, adapterFull).forEach { ad ->
            ad.setCandidateTextColor(candidateTextColorVal)
            ad.setCandidateItemColors(candidateItemBgColorVal, candidateItemPressedBgColorVal)
            ad.setCandidateEmptyDrawableColor(emptyDrawableColorVal)
            ad.setCandidateEmptyDrawableTextColor(emptyDrawableTextColorVal)
        }
    }

    private fun applyThemeToFloatingCandidateListAdapter() {
        if (!::listAdapter.isInitialized) return
        when (keyboardThemeMode) {
            "custom" -> {
                listAdapter.itemTextColor = customThemeCandidateTextColor ?: Color.BLACK
                listAdapter.itemBackground = customThemeCandidateItemBgColor ?: Color.TRANSPARENT
                listAdapter.itemPressedBackground = customThemeCandidateItemPressedBgColor
                    ?: ContextCompat.getColor(
                        this@IMEService,
                        com.kazumaproject.core.R.color.qwety_key_bg_color
                    )
            }
            "dark" -> {
                listAdapter.itemTextColor = Color.WHITE
                listAdapter.itemBackground = Color.TRANSPARENT
                listAdapter.itemPressedBackground = Color.DKGRAY
            }
            "light" -> {
                listAdapter.itemTextColor = Color.BLACK
                listAdapter.itemBackground = Color.TRANSPARENT
                listAdapter.itemPressedBackground = Color.LTGRAY
            }
            else -> {
                val isNight = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
                if (isNight) {
                    listAdapter.itemTextColor = Color.WHITE
                    listAdapter.itemBackground = Color.TRANSPARENT
                    listAdapter.itemPressedBackground = Color.DKGRAY
                } else {
                    listAdapter.itemTextColor = Color.BLACK
                    listAdapter.itemBackground = Color.TRANSPARENT
                    listAdapter.itemPressedBackground = Color.LTGRAY
                }
            }
        }

        floatingCandidateWindow?.contentView?.let { contentView ->
            val bgContainer = contentView as? ViewGroup
            val bgColor = when (keyboardThemeMode) {
                "custom" -> customThemeBgColor ?: Color.parseColor("#303030")
                "dark" -> Color.parseColor("#121212")
                "light" -> Color.parseColor("#F5F5F5")
                else -> {
                    val isNight = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
                    if (isNight) Color.parseColor("#121212") else Color.parseColor("#F5F5F5")
                }
            }
            bgContainer?.setBackgroundColor(bgColor)
        }
        applyThemeToSuggestionAdapter()
    }

    /**
     * Applies the current theme colors to the symbol keyboard (emoji/symbol/clipboard view)
     * for ALL theme modes (custom, dark, light, default/dynamic color).
     */
    private fun applyThemeToSymbolKeyboard() {
        val symbolViews = listOfNotNull(
            mainLayoutBinding?.keyboardSymbolView,
            floatingKeyboardBinding?.floatingSymbolKeyboard,
        )
        if (symbolViews.isEmpty()) return
        val isNight = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val isDynamic = DynamicColors.isDynamicColorAvailable()

        val defaultKeyBg = if (isDynamic) {
            getThemeColorOrFallback(
                attrRes = MaterialR.attr.colorSurfaceContainerHigh,
                fallbackColor = ContextCompat.getColor(this, com.kazumaproject.core.R.color.keyboard_bg)
            )
        } else {
            if (isNight) Color.parseColor("#303030") else Color.parseColor("#E0E0E0")
        }
        val defaultBg = if (isDynamic) {
            getThemeColorOrFallback(
                attrRes = MaterialR.attr.colorSurface,
                fallbackColor = ContextCompat.getColor(this, com.kazumaproject.core.R.color.keyboard_bg)
            )
        } else {
            if (isNight) Color.parseColor("#1C1F23") else Color.parseColor("#E0E5EC")
        }

        // Resolve backgrounds per theme mode
        var (bgColor, keyBgColor) = when (keyboardThemeMode) {
            "custom" -> Pair(
                customThemeBgColor ?: Color.WHITE,
                customThemeKeyColor ?: Color.WHITE
            )
            "dark" -> Pair(
                Color.parseColor("#1C1F23"),
                Color.parseColor("#303030")
            )
            "light" -> Pair(
                Color.parseColor("#E0E5EC"),
                Color.parseColor("#E0E0E0")
            )
            else -> Pair(defaultBg, defaultKeyBg)
        }
        keyBgColor = resolveSymbolKeyboardKeyColor(bgColor, keyBgColor)

        // 選択タブは濃いアイコン、非選択はやや薄いアイコン
        val selectedIconColor = when (keyboardThemeMode) {
            "custom" -> customThemeKeyTextColor
                ?: if (keyBgColor.isLightColor()) Color.BLACK else Color.WHITE
            else -> if (keyBgColor.isLightColor()) Color.BLACK else Color.WHITE
        }
        val iconColor = ColorUtils.setAlphaComponent(selectedIconColor, 200)

        symbolViews.forEach { symbolView ->
            symbolView.setKeyboardTheme(
                backgroundColor = bgColor,
                iconColor = iconColor,
                selectedIconColor = selectedIconColor,
                keyBackgroundColor = keyBgColor,
                liquidGlassEnable = liquidGlassThemePreference ?: false
            )
        }
    }

    /**
     * Applies the current theme's icon color to the shortcut toolbar adapter
     * for ALL theme modes (custom, dark, light, default/dynamic color).
     */
    private fun applyShortcutAdapterIconColor() {
        val adapter = shortcutAdapter ?: return
        val isNight = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val isDynamic = DynamicColors.isDynamicColorAvailable()

        val defaultBg = if (isDynamic) {
            getThemeColorOrFallback(
                attrRes = MaterialR.attr.colorSurface,
                fallbackColor = ContextCompat.getColor(this, com.kazumaproject.core.R.color.keyboard_bg)
            )
        } else {
            if (isNight) Color.parseColor("#1C1F23") else Color.parseColor("#E0E5EC")
        }

        // Resolve toolbar background per theme mode
        val bgColor = when (keyboardThemeMode) {
            "custom" -> customThemeBgColor ?: Color.WHITE
            "dark" -> Color.parseColor("#1C1F23")
            "light" -> Color.parseColor("#E0E5EC")
            else -> defaultBg
        }

        // Unified: derive icon color from background, with custom override support
        val iconColor = if (keyboardThemeMode == "custom") {
            customThemeShortcutIconColor ?: (if (bgColor.isLightColor()) Color.BLACK else Color.WHITE)
        } else {
            if (bgColor.isLightColor()) Color.BLACK else Color.WHITE
        }
        adapter.setIconColor(iconColor)
    }

    private fun animateSuggestionImageViewVisibility(
        mainView: View, isVisible: Boolean
    ) {
        mainView.post {
            mainView.pivotX = mainView.width / 2f
            mainView.pivotY = mainView.height / 2f

            if (isVisible) {
                mainView.visibility = View.VISIBLE
                mainView.scaleX = 0f
                mainView.scaleY = 0f

                mainView.animate().scaleX(1f).scaleY(1f).setDuration(200)
                    .setInterpolator(AccelerateDecelerateInterpolator()).withEndAction {
                        mainView.scaleX = 1f
                        mainView.scaleY = 1f
                    }.start()
            } else {
                mainView.visibility = View.VISIBLE
                mainView.scaleX = 1f
                mainView.scaleY = 1f

                mainView.animate().scaleX(0f).scaleY(0f).setDuration(200)
                    .setInterpolator(AccelerateDecelerateInterpolator()).withEndAction {
                        mainView.visibility = View.GONE
                        mainView.scaleX = 1f
                        mainView.scaleY = 1f
                    }.start()
            }
        }
    }

    private fun hideFirstRowCandidatesInFullScreen(
        mainView: MainLayoutBinding
    ) {
        mainView.candidatesRowView.post {
            if (!mainView.candidatesRowView.canScrollVertically(-1)) {
                val flexboxManager =
                    mainView.candidatesRowView.layoutManager as? FlexboxLayoutManager ?: return@post
                val flexLines = flexboxManager.flexLines

                if (flexLines.isNotEmpty()) {
                    val firstRowHeight = flexLines[0].crossSize
                    mainView.candidatesRowView.scrollBy(0, firstRowHeight)
                }
            }
        }
    }

    private fun hideFirstRowCandidatesInFullScreenFloating(
        floatingKeyboardLayoutBinding: FloatingKeyboardLayoutBinding
    ) {
        floatingKeyboardLayoutBinding.candidatesRowView.post {
            if (!floatingKeyboardLayoutBinding.candidatesRowView.canScrollVertically(-1)) {
                val flexboxManager =
                    floatingKeyboardLayoutBinding.candidatesRowView.layoutManager as? FlexboxLayoutManager
                        ?: return@post
                val flexLines = flexboxManager.flexLines

                if (flexLines.isNotEmpty()) {
                    val firstRowHeight = flexLines[0].crossSize
                    floatingKeyboardLayoutBinding.candidatesRowView.scrollBy(0, firstRowHeight)
                }
            }
        }
    }

    private fun launchProcessInputStringLatest(
        string: String,
        mainView: MainLayoutBinding,
    ) {
        manualProcessInputStringJob?.cancel()
        manualProcessInputStringJob = scope.launch {
            processInputString(string, mainView)
        }
    }

    private suspend fun processInputString(
        string: String, mainView: MainLayoutBinding,
    ) {
        if (isSymbolPanelSearchRoutingActive()) {
            if (string.isNotEmpty()) {
                _inputString.update { "" }
            }
            return
        }
        syncComposingTextSession(string)
        if (string.isNotEmpty()) {
            invalidatePostCommitPrediction()
            hasConvertedKatakana = false
            if (isRestoringReconversionInput) {
                isRestoringReconversionInput = false
            } else {
                clearPendingReconversionEntry()
            }
            if (preserveBunsetsuReconversionDraftOnNextProcessInput) {
                preserveBunsetsuReconversionDraftOnNextProcessInput = false
            } else {
                clearBunsetsuReconversionDraft()
            }
            if (suppressSuggestions) {
                setComposingText(string, 1)
                refreshReconversionUi()
                return
            }
            if (suppressNextQwertyGlideSuggestionRefresh) {
                suppressNextQwertyGlideSuggestionRefresh = false
                setComposingTextAfterEdit(
                    inputString = string,
                    spannableString = createSpannableWithTail(string),
                    backgroundColor = if (customComposingTextPreference == true) {
                        inputCompositionAfterBackgroundColor
                            ?: getColor(com.kazumaproject.core.R.color.blue)
                    } else {
                        getColor(com.kazumaproject.core.R.color.blue)
                    },
                    textColor = if (customComposingTextPreference == true) {
                        inputCompositionTextColor
                    } else {
                        null
                    }
                )
                refreshReconversionUi()
                return
            }
            if (deleteKeyLongKeyPressed.get()) {
                setComposingTextAfterEdit(
                    inputString = string,
                    spannableString = createSpannableWithTail(string),
                    backgroundColor = if (customComposingTextPreference == true) {
                        inputCompositionAfterBackgroundColor
                            ?: getColor(com.kazumaproject.core.R.color.blue)
                    } else {
                        getColor(com.kazumaproject.core.R.color.blue)
                    },
                    textColor = if (customComposingTextPreference == true) {
                        inputCompositionTextColor
                    } else {
                        null
                    }
                )
                refreshReconversionUi()
                return
            }
            if (qwertyMode.value == TenKeyQWERTYMode.TenKeyQWERTY) {
                handleTenKeyQwertyInput(string)
            } else {
                handleDefaultInput(string)
            }
        } else {
            lastQwertyRomajiRawInput = null
            if (stringInTail.get().isNotEmpty()) {
                setComposingText(stringInTail.get(), 1)
                onLeftKeyLongPressUp.set(true)
                onDeleteLongPressUp.set(true)
            } else {
                setDrawableToEnterKeyCorrespondingToImeOptions(mainView)
                if (isKeyboardFloatingMode == true) {
                    floatingKeyboardBinding?.let { floatingKeyboardLayoutBinding ->
                        setDrawableToEnterKeyCorrespondingToImeOptionsFloating(
                            floatingKeyboardLayoutBinding
                        )
                    }
                }
                onLeftKeyLongPressUp.set(true)
                onRightKeyLongPressUp.set(true)
                onDeleteLongPressUp.set(true)
                liveConversionManager.stopComposition()
            }
            _zenzCandidates.update { emptyList() }
            hasConvertedKatakana = false
            filteredCandidateList = emptyList()
            resetInputString()
            lastCandidate = ""
            hardKeyboardShiftPressd = false
            initialCursorDetectInFloatingCandidateView = false
            initialCursorXPosition = 0
            if (physicalKeyboardEnable.replayCache.isNotEmpty() && physicalKeyboardEnable.replayCache.first()) {
                updateSuggestionsForFloatingCandidate(emptyList())
                listAdapter.updateHighlightPosition(-1)
                currentHighlightIndex = -1
            }
            if (isKeyboardFloatingMode == true) {
                floatingKeyboardBinding?.keyboardViewFloating?.apply {
                    setSideKeySpaceDrawable(
                        cachedSpaceDrawable
                    )
                    when (currentInputMode.value) {
                        InputMode.ModeEnglish -> {
                            setBackgroundSmallLetterKey(
                                isLanguageEnable = tenkeyShowIMEButtonPreference ?: true,
                                isEnglish = true
                            )
                        }

                        InputMode.ModeJapanese -> {
                            setBackgroundSmallLetterKey(
                                isLanguageEnable = tenkeyShowIMEButtonPreference ?: true,
                                isEnglish = false
                            )
                        }

                        InputMode.ModeNumber -> {
                            setBackgroundSmallLetterKey(
                                cachedNumberDrawable
                            )
                        }
                    }
                }
            }
            if (isTabletGojuonSurface()) {
                mainView.tabletView.apply {
                    setSideKeySpaceDrawable(
                        cachedSpaceDrawable
                    )
                }
            } else {
                mainView.keyboardView.apply {
                    setSideKeySpaceDrawable(
                        cachedSpaceDrawable
                    )
                    when (currentInputMode.value) {
                        InputMode.ModeEnglish -> {
                            setBackgroundSmallLetterKey(
                                isLanguageEnable = tenkeyShowIMEButtonPreference ?: true,
                                isEnglish = true
                            )
                        }

                        InputMode.ModeJapanese -> {
                            setBackgroundSmallLetterKey(
                                isLanguageEnable = tenkeyShowIMEButtonPreference ?: true,
                                isEnglish = false
                            )
                        }

                        InputMode.ModeNumber -> {
                            setBackgroundSmallLetterKey(
                                cachedNumberDrawable
                            )
                        }
                    }
                }
            }

        }
        refreshReconversionUi()
    }

    /**
     * TenKeyQWERTYモードの入力処理を担当します。
     */
    private suspend fun handleTenKeyQwertyInput(string: String) {
        val spannable = createSpannableWithTail(string)
        _suggestionFlag.emit(CandidateShowFlag.Updating)
        if (!shouldStartLiveConversion(string)) {
            setComposingTextPreEdit(
                inputString = string,
                spannableString = spannable,
                backgroundColor = if (customComposingTextPreference == true) {
                    inputCompositionBackgroundColor
                        ?: getColor(com.kazumaproject.core.R.color.char_in_edit_color)
                } else {
                    getColor(com.kazumaproject.core.R.color.char_in_edit_color)
                },
                textColor = if (customComposingTextPreference == true) inputCompositionTextColor else null
            )
        }
        // ライブ変換の有効・無効に関わらず、同期的に入力テキストを即座に描画します。
        setComposingTextAfterEdit(
            inputString = string,
            spannableString = spannable,
            backgroundColor = if (customComposingTextPreference == true) {
                inputCompositionAfterBackgroundColor
                    ?: getColor(com.kazumaproject.core.R.color.blue)
            } else {
                getColor(com.kazumaproject.core.R.color.blue)
            },
            textColor = if (customComposingTextPreference == true) {
                inputCompositionTextColor
            } else {
                null
            }
        )
    }

    /**
     * TenKeyQWERTY以外のモードの入力処理を担当します。
     */
    private suspend fun handleDefaultInput(string: String) {
        val spannable = createSpannableWithTail(string)
        if (!shouldStartLiveConversion(string)) {
            setComposingTextPreEdit(
                inputString = string,
                spannableString = spannable,
                backgroundColor = if (customComposingTextPreference == true) {
                    inputCompositionBackgroundColor
                        ?: getColor(com.kazumaproject.core.R.color.char_in_edit_color)
                } else {
                    getColor(com.kazumaproject.core.R.color.char_in_edit_color)
                },
                textColor = if (customComposingTextPreference == true) inputCompositionTextColor else null
            )
        }
        _suggestionFlag.emit(CandidateShowFlag.Updating)
        if (inputString.value != string) {
            return
        }

        val shouldCommitOriginalText =
            inputString.value.isNotEmpty() && !isHenkan.get() && !onDeleteLongPressUp.get() && !englishSpaceKeyPressed.get() && !deleteKeyLongKeyPressed.get() && !hasConvertedKatakana

        if (shouldCommitOriginalText) {
            isContinuousTapInputEnabled.set(true)
            lastFlickConvertedNextHiragana.set(true)
            setComposingTextAfterEdit(
                inputString = string,
                spannableString = spannable,
                backgroundColor = if (customComposingTextPreference == true) {
                    inputCompositionAfterBackgroundColor
                        ?: getColor(com.kazumaproject.core.R.color.blue)
                } else {
                    getColor(com.kazumaproject.core.R.color.blue)
                },
                textColor = if (customComposingTextPreference == true) {
                    inputCompositionTextColor
                } else {
                    null
                }
            )
        }
    }

    /**
     * サジェスト候補リストの先頭にある文字列を取得し、編集後のテキストとして設定します。
     * このロジックは複数箇所で使われるため、関数として抽出しました。
     */
    private fun getCandidateCommitString(candidate: Candidate): String {
        return if (candidate.type == (15).toByte()) {
            candidate.string.correctReading().first
        } else {
            candidate.string
        }
    }

    private fun shouldDirectInsertCharacter(char: Char): Boolean {
        return char == '\n' || char == ' ' || char == '　' || char == '\t'
    }

    private fun candidateReadingLength(candidate: Candidate): Int {
        val fromData = candidate.data.sumOf { it.reading.length }
        return if (fromData > 0) fromData else candidate.rubyCount
    }

    private fun candidateMatchesInsertString(candidate: Candidate, insertString: String): Boolean {
        return candidateReadingLength(candidate) == insertString.length
    }

    /**
     * AzooKey [InputManager.moveCursor] 相当。
     * カーソル移動時は preedit を head/tail に分割し、ライブ変換は一時停止して候補を再取得する。
     */
    private fun invalidateLiveConversionAfterInternalCursorMove() {
        if (isLiveConversionEnable != true) return
        liveConversionManager.setLastUsedCandidate(null)
        lastCandidate = null
    }

    /**
     * AzooKey [InputManager.input] / [KeyboardActionManager] 相当。
     * ライブ変換中に記号・括弧などを直接入力する前に、変換中テキストを確定する。
     */
    private fun commitLiveConversionBeforeDirectInsert(insertString: String) {
        if (isLiveConversionEnable != true || insertString.isEmpty()) return
        val tail = stringInTail.get()
        val candidate = liveConversionManager.lastUsedCandidate
        val commitString = when {
            candidate != null && candidateMatchesInsertString(candidate, insertString) -> {
                applyCandidateCompleteActions(candidate)
                getCandidateCommitString(candidate)
            }
            !lastCandidate.isNullOrEmpty() && lastCandidate != insertString -> lastCandidate!!
            else -> insertString
        }
        beginBatchEdit()
        try {
            setComposingText("", 0)
            finishComposingText()
            commitText(commitString + tail, 1)
        } finally {
            endBatchEdit()
        }
        _inputString.update { "" }
        stringInTail.set("")
        lastQwertyRomajiRawInput = null
        liveConversionManager.stopComposition()
        candidateCoordinator.resetConversionSession()
    }

    private fun insertDirectText(text: String) {
        if (text.isEmpty()) return
        val insertString = inputString.value
        if (!isHenkan.get() && insertString.isNotEmpty()) {
            commitLiveConversionBeforeDirectInsert(insertString)
        } else {
            beginBatchEdit()
            try {
                setComposingText("", 0)
                finishComposingText()
            } finally {
                endBatchEdit()
            }
            _inputString.update { "" }
            stringInTail.set("")
            lastQwertyRomajiRawInput = null
            liveConversionManager.stopComposition()
            candidateCoordinator.resetConversionSession()
        }
        commitText(text, 1)
        clearSuggestionStateAfterCommit()
        resetFlagsEnterKeyNotHenkan()
    }

    private fun insertDirectCharacterFromKeyboard(charToSend: Char) {
        insertDirectText(charToSend.toString())
    }

    private fun shouldStartLiveConversion(input: String): Boolean {
        if (input.length < liveConversionStartLength) return false
        if (stringInTail.get().isNotEmpty()) return false
        if (isHenkan.get()) return false
        if (suggestionClickNum > 0) return false
        return currentRuntimeConversionPolicy(input).shouldUseLiveConversion
    }

    /**
     * 変換キー押下時のライブ変換候補選択（ライブ変換 OFF 時のみ使用）。
     * 読み長が入力全体と一致する候補のみ採用する。
     */
    private fun selectLiveConversionCandidate(
        insertString: String,
        candidates: List<Candidate>,
    ): Candidate? {
        return candidates.firstOrNull { candidate ->
            val lane = CandidateType.laneOf(candidate)
            if (lane == CandidateLane.Special || lane == CandidateLane.Prediction) {
                return@firstOrNull false
            }
            candidate.data.sumOf { it.reading.length } == insertString.length
        }
    }

    private fun currentRuntimeConversionPolicy(input: String): AzooKeyRuntimeConversionPolicy {
        if (cachedRuntimeConversionPolicyInput == input) {
            return cachedRuntimeConversionPolicy!!
        }
        val policy = CandidateRequestBridge.toCandidateRequest(
            composingText = composingTextForCandidateRequest(input),
            options = ImeCandidateRequestFactory.buildConvertRequestOptions(
                preferences = buildImeCandidatePreferences(),
                mode = CandidateRequestMode.Normal,
            ),
            runtime = ImeCandidateRequestFactory.buildRuntimeContext(buildImeCandidatePreferences()),
            mode = CandidateRequestMode.Normal,
        ).runtimeConversionPolicy
        cachedRuntimeConversionPolicyInput = input
        cachedRuntimeConversionPolicy = policy
        return policy
    }

    private fun currentAzooKeyLearningType(): AzooKeyStyleLearningType {
        val snapshot = cachedPreferences ?: return AzooKeyStyleLearningType.Nothing
        return ImeCandidatePreferencesBuilder.learningTypeFromSnapshot(snapshot)
    }

    private fun currentAzooKeyZenzaiMode(): AzooKeyStyleZenzaiMode {
        val enabled = cachedPreferences?.zenzaiEnableStatePreference
            ?: (zenzaiEnableStatePreference == true)
        return if (enabled) {
            AzooKeyStyleZenzaiMode.On
        } else {
            AzooKeyStyleZenzaiMode.Off
        }
    }

    private fun legacyAzooKeyLearningType(): AzooKeyStyleLearningType {
        return if (enablePredictionSearchLearnDictionaryPreference == true) {
            AzooKeyStyleLearningType.OnlyOutput
        } else {
            AzooKeyStyleLearningType.Nothing
        }
    }

    private fun currentAzooKeyLiveConversionMode(): AzooKeyLiveConversionMode {
        return if (isLiveConversionEnable == true) {
            AzooKeyLiveConversionMode.Enabled
        } else {
            AzooKeyLiveConversionMode.Disabled
        }
    }

    private fun currentCandidateRequestPrivacy(): CandidateRequestPrivacy {
        return CandidateRequestPrivacy(
            isPrivateMode = isPrivateMode,
            suppressSuggestions = suppressSuggestions,
        )
    }

    private fun shouldUseZenzCandidateGeneration(input: String): Boolean {
        return isZenzEnabledForSession() &&
            hasHardwareKeyboardConnected != true &&
            currentRuntimeConversionPolicy(input).allowsPersonalizedConversion
    }

    private fun shouldUseZenzaiCandidateEvaluation(input: String): Boolean {
        val policy = currentRuntimeConversionPolicy(input)
        return isZenzEnabledForSession() &&
            zenzRerankPreference == true &&
            policy.shouldUseZenzai &&
            policy.allowsPersonalizedConversion
    }

    private fun applyFirstSuggestion(
        candidate: Candidate
    ) {
        applyLiveConversionDisplay(getCandidateCommitString(candidate))
    }

    /**
     * AzooKey [DisplayedTextManager.updateComposingText] に準拠し、ライブ変換の表示のみ更新する。
     * [_inputString] は [convertTarget] のまま維持し、短い候補で入力が切り詰められないようにする。
     */
    private fun applyLiveConversionDisplay(liveText: String) {
        beginBatchEdit()
        lastCandidate = liveText
        val newSpannable = createSpannableWithTail(liveText)
        setComposingTextAfterEdit(
            inputString = liveText,
            spannableString = newSpannable,
            backgroundColor = if (customComposingTextPreference == true) {
                inputCompositionAfterBackgroundColor
                    ?: getColor(com.kazumaproject.core.R.color.blue)
            } else {
                getColor(com.kazumaproject.core.R.color.blue)
            },
            textColor = if (customComposingTextPreference == true) {
                inputCompositionTextColor
            } else {
                null
            }
        )
        endBatchEdit()
    }

    /**
     * 末尾文字列を結合したSpannableStringを生成します。
     */
    private fun createSpannableWithTail(text: String): SpannableString {
        return SpannableString(text + stringInTail.get())
    }

    private fun shouldUseBunsetsuCursorMoveSession(): Boolean {
        return bunsetsuSeparation == true && bunsetsuCursorMove == true
    }

    private fun isBunsetsuCursorMoveSessionActive(): Boolean {
        return shouldUseBunsetsuCursorMoveSession() &&
                isHenkan.get() &&
                bunsetsuConversionSession != null
    }

    private fun sanitizeSplitPositions(
        input: String,
        splitPositions: List<Int>,
    ): List<Int> = ImeCandidatePresentationCoordinator.sanitizeSplitPositions(input, splitPositions)

    private fun normalizeBunsetsuSplitPatterns(
        input: String,
        splitPatterns: List<List<Int>>
    ): List<List<Int>> {
        val initialPattern = sanitizeSplitPositions(input, bunsetsuPositionList.orEmpty())
        val normalizedPatterns = splitPatterns
            .map { sanitizeSplitPositions(input, it) }
            .distinct()

        return buildList {
            add(initialPattern)
            normalizedPatterns.forEach { pattern ->
                if (pattern != initialPattern) {
                    add(pattern)
                }
            }
        }
    }

    private fun buildBunsetsuSegments(
        input: String,
        splitPositions: List<Int>
    ): List<BunsetsuSegmentState> {
        val sanitizedSplitPositions = sanitizeSplitPositions(input, splitPositions)

        val boundaries = buildList {
            add(0)
            addAll(sanitizedSplitPositions)
            add(input.length)
        }.distinct()

        return boundaries.zipWithNext()
            .mapNotNull { (start, end) ->
                input.substring(start, end)
                    .takeIf { it.isNotEmpty() }
                    ?.let { reading ->
                        BunsetsuSegmentState(
                            reading = reading,
                            displayText = reading
                        )
                    }
            }
    }

    private fun displayTextFromCandidate(candidate: Candidate): String {
        return if (candidate.type == (15).toByte()) {
            candidate.string.correctReading().first
        } else {
            candidate.string
        }
    }

    private suspend fun loadCandidatesForBunsetsuSegment(
        session: BunsetsuConversionSession,
        segmentIndex: Int,
        mainView: MainLayoutBinding
    ): BunsetsuConversionSession {
        if (segmentIndex !in session.segments.indices) return session

        val targetSegment = session.segments[segmentIndex]
        if (targetSegment.candidates.isNotEmpty()) return session

        val previousPositions = bunsetsuPositionList
        val previousSplitPatterns = bunsetsuSplitPatterns
        val targetReadingLength = targetSegment.reading.length
        val candidates = try {
            getSuggestionList(targetSegment.reading, mainView).filter {
                it.length.toInt() == targetReadingLength
            }
        } finally {
            bunsetsuPositionList = previousPositions
            bunsetsuSplitPatterns = previousSplitPatterns
        }

        val displayText = candidates.firstOrNull()?.let(::displayTextFromCandidate)
            ?: targetSegment.reading

        val updatedSegments = session.segments.toMutableList()
        updatedSegments[segmentIndex] = targetSegment.copy(
            candidates = candidates,
            displayText = displayText,
            selectedIndex = 0
        )
        return session.copy(segments = updatedSegments)
    }

    private suspend fun activateBunsetsuConversionSession(
        input: String,
        mainView: MainLayoutBinding,
        floatingKeyboardLayoutBinding: FloatingKeyboardLayoutBinding? = null
    ): Boolean {
        if (!shouldUseBunsetsuCursorMoveSession()) return false

        val tailText = stringInTail.get()
        val splitPatterns = normalizeBunsetsuSplitPatterns(input, bunsetsuSplitPatterns)
        val initialSplitPositions = splitPatterns.firstOrNull().orEmpty()
        val initialSegments = buildBunsetsuSegments(input, initialSplitPositions)
        if (initialSegments.isEmpty()) {
            clearBunsetsuConversionSession()
            return false
        }

        val initialSession = BunsetsuConversionSession(
            rawInput = input + tailText,
            conversionInput = input,
            segments = initialSegments,
            tailText = tailText,
            focusedIndex = 0,
            splitPatterns = splitPatterns,
            activeSplitPatternIndex = 0
        )

        isHenkan.set(true)
        henkanPressedWithBunsetsuDetect = true
        bunsetusMultipleDetect = true
        stringInTail.set("")
        suggestionClickNum = 0
        currentHighlightIndex = RecyclerView.NO_POSITION
        bunsetsuPositionList = initialSplitPositions
        bunsetsuSplitPatterns = splitPatterns
        bunsetsuConversionSession = loadCandidatesForBunsetsuSegment(
            initialSession,
            segmentIndex = 0,
            mainView = mainView
        )
        renderBunsetsuConversionSession(mainView, floatingKeyboardLayoutBinding)
        return true
    }

    private fun buildBunsetsuSegmentRanges(
        segments: List<BunsetsuSegmentState>
    ): List<IntRange> {
        var start = 0
        return segments.map { segment ->
            val endExclusive = start + segment.reading.length
            val range = start until endExclusive
            start = endExclusive
            range
        }
    }

    private fun overlapLength(first: IntRange, second: IntRange): Int {
        val start = maxOf(first.first, second.first)
        val endExclusive = minOf(first.last + 1, second.last + 1)
        return (endExclusive - start).coerceAtLeast(0)
    }

    private fun findFocusedSegmentIndexForSplitPattern(
        currentSegments: List<BunsetsuSegmentState>,
        currentFocusedIndex: Int,
        nextSegments: List<BunsetsuSegmentState>
    ): Int {
        if (currentSegments.size == 1 && nextSegments.size > 1) {
            return 0
        }

        val currentRanges = buildBunsetsuSegmentRanges(currentSegments)
        val currentRange = currentRanges.getOrNull(currentFocusedIndex) ?: return 0
        val nextRanges = buildBunsetsuSegmentRanges(nextSegments)

        return nextRanges.indices.maxWithOrNull(
            compareBy<Int> { index ->
                overlapLength(currentRange, nextRanges[index])
            }.thenByDescending { index ->
                -kotlin.math.abs(nextRanges[index].first - currentRange.first)
            }
        ) ?: 0
    }

    private fun switchBunsetsuSplitPattern(
        delta: Int = 1,
        floatingKeyboardLayoutBinding: FloatingKeyboardLayoutBinding? = null
    ): Boolean {
        if (!isBunsetsuCursorMoveSessionActive()) return false
        val mainView = mainLayoutBinding ?: return false
        val session = bunsetsuConversionSession ?: return false
        if (session.splitPatterns.size <= 1) return false

        scope.launch {
            val nextPatternIndex =
                ((session.activeSplitPatternIndex + delta) % session.splitPatterns.size + session.splitPatterns.size) % session.splitPatterns.size
            val nextSplitPositions = session.splitPatterns[nextPatternIndex]
            val rebuiltSegments = buildBunsetsuSegments(
                input = session.conversionInput,
                splitPositions = nextSplitPositions
            )
            if (rebuiltSegments.isEmpty()) {
                return@launch
            }

            val nextFocusedIndex = findFocusedSegmentIndexForSplitPattern(
                currentSegments = session.segments,
                currentFocusedIndex = session.focusedIndex,
                nextSegments = rebuiltSegments
            )

            val switchedSession = session.copy(
                segments = rebuiltSegments,
                focusedIndex = nextFocusedIndex,
                activeSplitPatternIndex = nextPatternIndex
            )
            bunsetsuPositionList = nextSplitPositions
            bunsetsuSplitPatterns = session.splitPatterns
            bunsetsuConversionSession = loadCandidatesForBunsetsuSegment(
                switchedSession,
                segmentIndex = nextFocusedIndex,
                mainView = mainView
            )
            renderBunsetsuConversionSession(mainView, floatingKeyboardLayoutBinding)
        }
        return true
    }

    private fun updateSuggestionViewsForBunsetsuSegment(
        segment: BunsetsuSegmentState,
        mainView: MainLayoutBinding,
        floatingKeyboardLayoutBinding: FloatingKeyboardLayoutBinding?
    ) {
        suggestionAdapter?.suggestions = segment.candidates
        suggestionAdapterFull?.suggestions = segment.candidates

        val highlightIndex = if (segment.candidates.isEmpty()) {
            RecyclerView.NO_POSITION
        } else {
            segment.selectedIndex.coerceIn(0, segment.candidates.lastIndex)
        }
        suggestionAdapter?.updateHighlightPosition(highlightIndex)

        if (highlightIndex != RecyclerView.NO_POSITION) {
            mainView.suggestionRecyclerView.smoothScrollToPosition(highlightIndex)
            floatingKeyboardLayoutBinding?.suggestionRecyclerView?.smoothScrollToPosition(
                highlightIndex
            )
        }

        if (hardwareKeyboardCoordinator.shouldRouteCandidatesToFloatingBar(
                physicalKeyboardEnable.replayCache.firstOrNull(),
            )
        ) {
            updateSuggestionsForFloatingCandidate(segment.candidates.map {
                CandidateItem(
                    word = displayTextFromCandidate(it),
                    length = it.length
                )
            }, highlightedAbsoluteIndex = highlightIndex)
        }
    }

    private fun renderBunsetsuConversionSession(
        mainView: MainLayoutBinding,
        floatingKeyboardLayoutBinding: FloatingKeyboardLayoutBinding? = null
    ) {
        val session = bunsetsuConversionSession ?: return
        val focusedIndex = session.focusedIndex.coerceIn(0, session.segments.lastIndex)
        val segments = session.segments
        val convertedText = segments.joinToString(separator = "") { it.displayText }
        val text = convertedText + session.tailText
        val highlightStart = segments
            .take(focusedIndex)
            .sumOf { it.displayText.length }
        val focusedSegment = segments[focusedIndex]
        val highlightEnd = highlightStart + focusedSegment.displayText.length

        updateSuggestionViewsForBunsetsuSegment(
            segment = focusedSegment,
            mainView = mainView,
            floatingKeyboardLayoutBinding = floatingKeyboardLayoutBinding
        )

        applyBunsetsuComposingText(
            text = text,
            segments = segments,
            tailText = session.tailText,
            highlightStart = highlightStart,
            highlightEnd = highlightEnd,
            backgroundColor = if (customComposingTextPreference == true) {
                inputConversionBackgroundColor
                    ?: getColor(com.kazumaproject.core.R.color.orange)
            } else {
                getColor(com.kazumaproject.core.R.color.orange)
            },
            textColor = if (customComposingTextPreference == true) {
                inputConversionTextColor
            } else {
                null
            }
        )
        updateHenkanUi(mainView, floatingKeyboardLayoutBinding, session.rawInput)
    }

    private fun applyBunsetsuComposingText(
        text: String,
        segments: List<BunsetsuSegmentState>,
        tailText: String,
        highlightStart: Int,
        highlightEnd: Int,
        @ColorInt backgroundColor: Int,
        @ColorInt textColor: Int? = null
    ) {
        val spannableString = SpannableString(text)
        val safeStart = highlightStart.coerceIn(0, text.length)
        val safeEnd = highlightEnd.coerceIn(safeStart, text.length)
        val spanFlag = Spannable.SPAN_EXCLUSIVE_EXCLUSIVE or Spannable.SPAN_COMPOSING

        spannableString.apply {
            setSpan(
                BackgroundColorSpan(backgroundColor),
                safeStart,
                safeEnd,
                spanFlag
            )

            textColor?.let { color ->
                setSpan(
                    ForegroundColorSpan(color),
                    safeStart,
                    safeEnd,
                    spanFlag
                )
            }

            var segmentStart = 0
            segments.forEach { segment ->
                val segmentEnd = (segmentStart + segment.displayText.length).coerceAtMost(length)
                if (segmentEnd > segmentStart) {
                    setSpan(
                        UnderlineSpan(),
                        segmentStart,
                        segmentEnd,
                        spanFlag
                    )
                }
                segmentStart = segmentEnd
            }

            if (tailText.isNotEmpty()) {
                val tailStart = (text.length - tailText.length).coerceAtLeast(0)
                if (tailStart < length) {
                    setSpan(
                        UnderlineSpan(),
                        tailStart,
                        length,
                        spanFlag
                    )
                }
            }
        }

        setComposingText(spannableString, 1)
    }

    private fun clearBunsetsuConversionSession() {
        bunsetsuConversionSession = null
        bunsetusMultipleDetect = false
    }

    private fun restoreRawInputFromBunsetsuSession() {
        val session = bunsetsuConversionSession ?: return
        val rawInput = session.rawInput
        val shouldForceRefresh = inputString.value == rawInput
        clearBunsetsuConversionSession()
        val spannableString = SpannableString(rawInput)
        setComposingTextAfterEdit(
            inputString = rawInput,
            spannableString = spannableString,
            backgroundColor = if (customComposingTextPreference == true) {
                inputCompositionAfterBackgroundColor
                    ?: getColor(com.kazumaproject.core.R.color.blue)
            } else {
                getColor(com.kazumaproject.core.R.color.blue)
            },
            textColor = if (customComposingTextPreference == true) {
                inputCompositionTextColor
            } else {
                null
            }
        )
        suggestionAdapter?.suggestions = emptyList()
        suggestionAdapterFull?.suggestions = emptyList()
        suggestionAdapter?.updateHighlightPosition(RecyclerView.NO_POSITION)
        if (physicalKeyboardEnable.replayCache.isNotEmpty() &&
            physicalKeyboardEnable.replayCache.first()
        ) {
            updateSuggestionsForFloatingCandidate(emptyList())
            currentHighlightIndex = RecyclerView.NO_POSITION
        }
        _inputString.update { rawInput }
        if (shouldForceRefresh) {
            mainLayoutBinding?.let { mainView ->
                launchProcessInputStringLatest(rawInput, mainView)
            }
        }
    }

    private fun exitBunsetsuCursorMoveSessionToRawInput(): String {
        val rawInput = bunsetsuConversionSession?.rawInput ?: inputString.value
        restoreRawInputFromBunsetsuSession()
        isHenkan.set(false)
        henkanPressedWithBunsetsuDetect = false
        suggestionClickNum = 0
        suggestionAdapter?.updateHighlightPosition(RecyclerView.NO_POSITION)
        if (physicalKeyboardEnable.replayCache.isNotEmpty() &&
            physicalKeyboardEnable.replayCache.first()
        ) {
            currentHighlightIndex = RecyclerView.NO_POSITION
        }
        isFirstClickHasStringTail = false
        return rawInput
    }

    private fun moveFocusedBunsetsuSegment(
        delta: Int,
        floatingKeyboardLayoutBinding: FloatingKeyboardLayoutBinding? = null
    ): Boolean {
        if (!isBunsetsuCursorMoveSessionActive()) return false
        val mainView = mainLayoutBinding ?: return false
        val session = bunsetsuConversionSession ?: return false
        val nextIndex = (session.focusedIndex + delta).coerceIn(0, session.segments.lastIndex)
        if (nextIndex == session.focusedIndex) return true

        scope.launch {
            val movedSession = session.copy(focusedIndex = nextIndex)
            bunsetsuConversionSession = loadCandidatesForBunsetsuSegment(
                movedSession,
                segmentIndex = nextIndex,
                mainView = mainView
            )
            renderBunsetsuConversionSession(mainView, floatingKeyboardLayoutBinding)
        }
        return true
    }

    private fun cycleFocusedBunsetsuCandidate(
        delta: Int,
        floatingKeyboardLayoutBinding: FloatingKeyboardLayoutBinding? = null
    ): Boolean {
        if (!isBunsetsuCursorMoveSessionActive()) return false
        val mainView = mainLayoutBinding ?: return false
        val session = bunsetsuConversionSession ?: return false

        scope.launch {
            val loadedSession = loadCandidatesForBunsetsuSegment(
                session,
                segmentIndex = session.focusedIndex,
                mainView = mainView
            )
            val segment = loadedSession.segments[loadedSession.focusedIndex]
            if (segment.candidates.isEmpty()) {
                bunsetsuConversionSession = loadedSession
                renderBunsetsuConversionSession(mainView, floatingKeyboardLayoutBinding)
                return@launch
            }

            val candidateCount = segment.candidates.size
            val nextIndex =
                ((segment.selectedIndex + delta) % candidateCount + candidateCount) % candidateCount
            val updatedSegment = segment.copy(
                selectedIndex = nextIndex,
                displayText = displayTextFromCandidate(segment.candidates[nextIndex])
            )
            val updatedSegments = loadedSession.segments.toMutableList()
            updatedSegments[loadedSession.focusedIndex] = updatedSegment
            bunsetsuConversionSession = loadedSession.copy(segments = updatedSegments)
            renderBunsetsuConversionSession(mainView, floatingKeyboardLayoutBinding)
        }
        return true
    }

    private fun commitBunsetsuConversionSession(): Boolean {
        val session = bunsetsuConversionSession ?: return false
        val commitString = session.segments.joinToString(separator = "") { it.displayText }
        val tailText = session.tailText
        if (tailText.isEmpty()) {
            finalizeBunsetsuReconversion(
                originalReading = session.rawInput,
                committedText = commitString
            )
        }
        beginBatchEdit()
        try {
            setComposingText("", 0)
            finishComposingText()
            commitText(commitString, 1)
            if (tailText.isNotEmpty()) {
                val spannableString = SpannableString(tailText)
                setComposingTextAfterEdit(
                    inputString = tailText,
                    spannableString = spannableString,
                    backgroundColor = if (customComposingTextPreference == true) {
                        inputCompositionAfterBackgroundColor
                            ?: getColor(com.kazumaproject.core.R.color.blue)
                    } else {
                        getColor(com.kazumaproject.core.R.color.blue)
                    },
                    textColor = if (customComposingTextPreference == true) {
                        inputCompositionTextColor
                    } else {
                        null
                    }
                )
            }
        } finally {
            endBatchEdit()
        }
        isHenkan.set(false)
        henkanPressedWithBunsetsuDetect = false
        suggestionClickNum = 0
        englishSpaceKeyPressed.set(false)
        onDeleteLongPressUp.set(false)
        _dakutenPressed.value = false
        lastFlickConvertedNextHiragana.set(true)
        isContinuousTapInputEnabled.set(true)
        suggestionAdapter?.suggestions = emptyList()
        suggestionAdapterFull?.suggestions = emptyList()
        suggestionAdapter?.updateHighlightPosition(RecyclerView.NO_POSITION)
        if (physicalKeyboardEnable.replayCache.isNotEmpty() &&
            physicalKeyboardEnable.replayCache.first()
        ) {
            updateSuggestionsForFloatingCandidate(emptyList())
            currentHighlightIndex = RecyclerView.NO_POSITION
        }
        isFirstClickHasStringTail = false
        stringInTail.set("")
        _inputString.update { tailText }
        clearBunsetsuConversionSession()
        return true
    }

    private suspend fun resetInputString() {
        Timber.d("resetInputString detect: $bunsetusMultipleDetect [${stringInTail.get()}]")
        val henkanActive = isHenkan.get()
        val tailIsEmpty = stringInTail.get().isEmpty()
        val shouldCommitIdle = !bunsetusMultipleDetect || tailIsEmpty
        if (!henkanActive && shouldCommitIdle) {
            _suggestionFlag.emit(CandidateShowFlag.Idle)
        }
    }

    private fun setCurrentInputType(attribute: EditorInfo?) {
        attribute?.apply {
            currentInputType = getCurrentInputTypeForIME2(this)
            inputActionDispatcher.keyboardMode.setSessionMode(defaultInputModeFor(currentInputType))
            Timber.d("setCurrentInputType: $currentInputType $inputType ${attribute.hintText} ${attribute.actionId} ${attribute.fieldName} ${attribute.inputType} ")
            if (isTabletGojuonSurface()) {
                mainLayoutBinding?.tabletView?.apply {
                    when (currentInputType) {
                        InputTypeForIME.Text,
                        InputTypeForIME.TextAutoComplete,
                        InputTypeForIME.TextAutoCorrect,
                        InputTypeForIME.TextCapCharacters,
                        InputTypeForIME.TextCapSentences,
                        InputTypeForIME.TextCapWords,
                        InputTypeForIME.TextFilter,
                        InputTypeForIME.TextNoSuggestion,
                        InputTypeForIME.TextPersonName,
                        InputTypeForIME.TextPhonetic,
                        InputTypeForIME.TextWebEditText,
                        InputTypeForIME.TextUri,
                            -> {
                            currentInputMode.set(InputMode.ModeJapanese)
                            setInputModeSwitchState()
                            setSideKeyPreviousState(true)
                            this.setSideKeyEnterDrawable(
                                cachedArrowRightDrawable
                            )
                        }

                        InputTypeForIME.TextSend -> {
                            currentInputMode.set(InputMode.ModeJapanese)
                            setInputModeSwitchState()
                            setSideKeyPreviousState(true)
                            this.setSideKeyEnterDrawable(
                                cachedArrowRightDrawable
                            )
                        }

                        InputTypeForIME.TextMultiLine,
                        InputTypeForIME.TextImeMultiLine,
                        InputTypeForIME.TextShortMessage,
                        InputTypeForIME.TextLongMessage,
                            -> {
                            currentInputMode.set(InputMode.ModeJapanese)
                            setInputModeSwitchState()
                            setSideKeyPreviousState(true)
                            this.setSideKeyEnterDrawable(
                                cachedReturnDrawable
                            )
                        }

                        InputTypeForIME.TextEmailAddress, InputTypeForIME.TextEmailSubject, InputTypeForIME.TextNextLine -> {
                            currentInputMode.set(InputMode.ModeJapanese)
                            setInputModeSwitchState()
                            setSideKeyPreviousState(true)
                            this.setSideKeyEnterDrawable(
                                cachedTabDrawable
                            )
                        }

                        InputTypeForIME.TextDone -> {
                            currentInputMode.set(InputMode.ModeJapanese)
                            setInputModeSwitchState()
                            setSideKeyPreviousState(true)
                            this.setSideKeyEnterDrawable(
                                cachedCheckDrawable
                            )
                        }

                        InputTypeForIME.TextWebSearchView, InputTypeForIME.TextWebSearchViewFireFox, InputTypeForIME.TextSearchView -> {
                            currentInputMode.set(InputMode.ModeJapanese)
                            setInputModeSwitchState()
                            setSideKeyPreviousState(true)
                            this.setSideKeyEnterDrawable(
                                cachedSearchDrawable
                            )
                        }

                        InputTypeForIME.TextEditTextInWebView,
                        InputTypeForIME.TextPostalAddress,
                        InputTypeForIME.TextWebEmailAddress,
                        InputTypeForIME.TextPassword,
                        InputTypeForIME.TextVisiblePassword,
                        InputTypeForIME.TextWebPassword,
                            -> {
                            currentInputMode.set(InputMode.ModeEnglish)
                            setInputModeSwitchState()
                            setSideKeyPreviousState(true)
                            this.setSideKeyEnterDrawable(
                                cachedArrowRightDrawable
                            )
                        }

                        InputTypeForIME.None, InputTypeForIME.TextNotCursorUpdate -> {
                            currentInputMode.set(InputMode.ModeJapanese)
                            setInputModeSwitchState()
                            setSideKeyPreviousState(true)
                            this.setSideKeyEnterDrawable(
                                cachedArrowRightDrawable
                            )
                        }

                        InputTypeForIME.Number,
                        InputTypeForIME.NumberDecimal,
                        InputTypeForIME.NumberPassword,
                        InputTypeForIME.NumberSigned,
                        InputTypeForIME.Phone,
                        InputTypeForIME.Date,
                        InputTypeForIME.Datetime,
                        InputTypeForIME.Time,
                            -> {
                            currentInputMode.set(InputMode.ModeNumber)
                            setInputModeSwitchState()
                            setSideKeyPreviousState(false)
                            mainLayoutBinding?.let {
                                setDrawableToEnterKeyCorrespondingToImeOptions(it)
                            }
                        }

                    }
                }
            } else {
                mainLayoutBinding?.keyboardView?.apply {
                    when (currentInputType) {
                        InputTypeForIME.Text,
                        InputTypeForIME.TextAutoComplete,
                        InputTypeForIME.TextAutoCorrect,
                        InputTypeForIME.TextCapCharacters,
                        InputTypeForIME.TextCapSentences,
                        InputTypeForIME.TextCapWords,
                        InputTypeForIME.TextFilter,
                        InputTypeForIME.TextNoSuggestion,
                        InputTypeForIME.TextPersonName,
                        InputTypeForIME.TextPhonetic,
                        InputTypeForIME.TextWebEditText,
                        InputTypeForIME.TextUri,
                            -> {
                            setCurrentMode(InputMode.ModeJapanese)
                            setSideKeyPreviousState(true)
                            this.setSideKeyEnterDrawable(
                                cachedArrowRightDrawable
                            )
                            setFirstKeyboardType()
                        }

                        InputTypeForIME.TextMultiLine,
                        InputTypeForIME.TextImeMultiLine,
                        InputTypeForIME.TextShortMessage,
                        InputTypeForIME.TextLongMessage,
                            -> {
                            setCurrentMode(InputMode.ModeJapanese)
                            setSideKeyPreviousState(true)
                            this.setSideKeyEnterDrawable(
                                cachedReturnDrawable
                            )
                            setFirstKeyboardType()
                        }

                        InputTypeForIME.TextEmailSubject, InputTypeForIME.TextNextLine -> {
                            setCurrentMode(InputMode.ModeJapanese)
                            setSideKeyPreviousState(true)
                            this.setSideKeyEnterDrawable(
                                cachedTabDrawable
                            )
                            setFirstKeyboardType()
                        }

                        InputTypeForIME.TextDone -> {
                            setCurrentMode(InputMode.ModeJapanese)
                            setSideKeyPreviousState(true)
                            this.setSideKeyEnterDrawable(
                                cachedCheckDrawable
                            )
                            setFirstKeyboardType()
                        }

                        InputTypeForIME.TextSend -> {
                            setCurrentMode(InputMode.ModeJapanese)
                            setSideKeyPreviousState(true)
                            this.setSideKeyEnterDrawable(
                                cachedArrowRightDrawable
                            )
                            setFirstKeyboardType()
                        }

                        InputTypeForIME.TextWebSearchView, InputTypeForIME.TextWebSearchViewFireFox, InputTypeForIME.TextSearchView -> {
                            setCurrentMode(InputMode.ModeJapanese)
                            setSideKeyPreviousState(true)
                            this.setSideKeyEnterDrawable(
                                cachedSearchDrawable
                            )
                            setFirstKeyboardType()
                        }

                        InputTypeForIME.TextEmailAddress,
                        InputTypeForIME.TextEditTextInWebView,
                        InputTypeForIME.TextPostalAddress,
                        InputTypeForIME.TextWebEmailAddress,
                        InputTypeForIME.TextPassword,
                        InputTypeForIME.TextVisiblePassword,
                        InputTypeForIME.TextWebPassword,
                            -> {
                            setCurrentMode(InputMode.ModeEnglish)
                            setSideKeyPreviousState(true)
                            this.setSideKeyEnterDrawable(
                                cachedArrowRightDrawable
                            )
                            setFirstKeyboardType()
                        }

                        InputTypeForIME.None, InputTypeForIME.TextNotCursorUpdate -> {
                            setCurrentMode(InputMode.ModeJapanese)
                            setSideKeyPreviousState(true)
                            this.setSideKeyEnterDrawable(
                                cachedArrowRightDrawable
                            )
                            setFirstKeyboardType()
                        }

                        InputTypeForIME.Number,
                        InputTypeForIME.NumberDecimal,
                        InputTypeForIME.NumberPassword,
                        InputTypeForIME.NumberSigned,
                        InputTypeForIME.Phone,
                        InputTypeForIME.Date,
                        InputTypeForIME.Datetime,
                        InputTypeForIME.Time,
                            -> {
                            _tenKeyQWERTYMode.update { TenKeyQWERTYMode.Number }
                        }

                    }
                }
            }

            if (isKeyboardFloatingMode == true) {
                floatingKeyboardBinding?.keyboardViewFloating?.apply {
                    when (currentInputType) {
                        InputTypeForIME.Text,
                        InputTypeForIME.TextAutoComplete,
                        InputTypeForIME.TextAutoCorrect,
                        InputTypeForIME.TextCapCharacters,
                        InputTypeForIME.TextCapSentences,
                        InputTypeForIME.TextCapWords,
                        InputTypeForIME.TextFilter,
                        InputTypeForIME.TextNoSuggestion,
                        InputTypeForIME.TextPersonName,
                        InputTypeForIME.TextPhonetic,
                        InputTypeForIME.TextWebEditText,
                        InputTypeForIME.TextUri,
                            -> {
                            setCurrentMode(InputMode.ModeJapanese)
                            setSideKeyPreviousState(true)
                            this.setSideKeyEnterDrawable(
                                cachedArrowRightDrawable
                            )
                        }

                        InputTypeForIME.TextMultiLine,
                        InputTypeForIME.TextImeMultiLine,
                        InputTypeForIME.TextShortMessage,
                        InputTypeForIME.TextLongMessage,
                            -> {
                            setCurrentMode(InputMode.ModeJapanese)
                            setSideKeyPreviousState(true)
                            this.setSideKeyEnterDrawable(
                                cachedReturnDrawable
                            )
                        }

                        InputTypeForIME.TextEmailSubject, InputTypeForIME.TextNextLine -> {
                            setCurrentMode(InputMode.ModeJapanese)
                            setSideKeyPreviousState(true)
                            this.setSideKeyEnterDrawable(
                                cachedTabDrawable
                            )
                        }

                        InputTypeForIME.TextDone -> {
                            setCurrentMode(InputMode.ModeJapanese)
                            setSideKeyPreviousState(true)
                            this.setSideKeyEnterDrawable(
                                cachedCheckDrawable
                            )
                        }

                        InputTypeForIME.TextSend -> {
                            setCurrentMode(InputMode.ModeJapanese)
                            setSideKeyPreviousState(true)
                            this.setSideKeyEnterDrawable(
                                cachedArrowRightDrawable
                            )
                        }

                        InputTypeForIME.TextWebSearchView, InputTypeForIME.TextWebSearchViewFireFox, InputTypeForIME.TextSearchView -> {
                            setCurrentMode(InputMode.ModeJapanese)
                            setSideKeyPreviousState(true)
                            this.setSideKeyEnterDrawable(
                                cachedSearchDrawable
                            )
                        }

                        InputTypeForIME.TextEmailAddress,
                        InputTypeForIME.TextEditTextInWebView,
                        InputTypeForIME.TextPostalAddress,
                        InputTypeForIME.TextWebEmailAddress,
                        InputTypeForIME.TextPassword,
                        InputTypeForIME.TextVisiblePassword,
                        InputTypeForIME.TextWebPassword,
                            -> {
                            setCurrentMode(InputMode.ModeEnglish)
                            setSideKeyPreviousState(true)
                            this.setSideKeyEnterDrawable(
                                cachedArrowRightDrawable
                            )
                        }

                        InputTypeForIME.None, InputTypeForIME.TextNotCursorUpdate -> {
                            setCurrentMode(InputMode.ModeJapanese)
                            setSideKeyPreviousState(true)
                            this.setSideKeyEnterDrawable(
                                cachedArrowRightDrawable
                            )
                        }

                        InputTypeForIME.Number,
                        InputTypeForIME.NumberDecimal,
                        InputTypeForIME.NumberPassword,
                        InputTypeForIME.NumberSigned,
                        InputTypeForIME.Phone,
                        InputTypeForIME.Date,
                        InputTypeForIME.Datetime,
                        InputTypeForIME.Time,
                            -> {
                            setCurrentMode(InputMode.ModeNumber)
                            setSideKeyPreviousState(true)
                            this.setSideKeyEnterDrawable(
                                cachedArrowRightDrawable
                            )
                        }

                    }
                }
            }
        }
        refreshCustomIcons(attribute)
    }

    private fun setFirstKeyboardType() {
        if (keyboardOrder.isNotEmpty()) {
            val firstItem =
                if (keyboardOrder.first() == KeyboardType.CUSTOM && customLayouts.isEmpty()) {
                    keyboardOrder.firstOrNull { it != KeyboardType.CUSTOM } ?: KeyboardType.TENKEY
                } else {
                    keyboardOrder.first()
                }
            when (firstItem) {
                KeyboardType.TENKEY -> _tenKeyQWERTYMode.update { TenKeyQWERTYMode.Default }
                KeyboardType.SUMIRE -> _tenKeyQWERTYMode.update { TenKeyQWERTYMode.Sumire }
                KeyboardType.QWERTY -> _tenKeyQWERTYMode.update { TenKeyQWERTYMode.TenKeyQWERTY }
                KeyboardType.ROMAJI -> _tenKeyQWERTYMode.update { TenKeyQWERTYMode.TenKeyQWERTYRomaji }
                KeyboardType.CUSTOM -> _tenKeyQWERTYMode.update { TenKeyQWERTYMode.Custom }
            }
        }
    }

    private fun setTabsToTabLayout(
        mainView: MainLayoutBinding
    ) {
        mainView.candidateTabLayout.removeAllTabs()

        candidateTabOrder.forEach { tabType ->
            val tab = mainView.candidateTabLayout.newTab()
            tab.text = getCandidateTabDisplayName(tabType)
            mainView.candidateTabLayout.addTab(tab)
        }
    }

    private fun getCandidateTabDisplayName(candidateTab: CandidateTab): String {
        return when (candidateTab) {
            CandidateTab.PREDICTION -> "予測"
            CandidateTab.CONVERSION -> "変換"
            CandidateTab.EISUKANA -> "英数カナ"
        }
    }

    private fun setCandidateTabLayout(
        mainView: MainLayoutBinding
    ) {
        mainView.candidateTabLayout.apply {
            when (keyboardThemeMode) {
                "custom" -> {
                    setSelectedTabIndicatorColor(customThemeSpecialKeyTextColor ?: Color.BLACK)
                    setTabTextColors(
                        customThemeKeyTextColor ?: Color.BLACK,
                        customThemeSpecialKeyTextColor ?: Color.BLACK
                    )
                }

                else -> {}
            }
            addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
                override fun onTabSelected(tab: TabLayout.Tab?) {
                    tab?.let { t ->
                        if (t.position > candidateTabOrder.size - 1) return
                        when (candidateTabOrder[t.position]) {
                            CandidateTab.PREDICTION -> {
                                val input = inputString.value
                                if (input.isNotEmpty()) {
                                    ioScope.launch {
                                        setCandidates(input, mainView)
                                        withContext(Dispatchers.Main) {
                                            hideFirstRowCandidatesInFullScreen(mainView)
                                        }
                                    }
                                }
                            }

                            CandidateTab.CONVERSION -> {
                                val input = inputString.value
                                if (input.isNotEmpty()) {
                                    ioScope.launch {
                                        setCandidatesWithoutPrediction(input, mainView)
                                        withContext(Dispatchers.Main) {
                                            hideFirstRowCandidatesInFullScreen(mainView)
                                        }
                                    }
                                }
                            }

                            CandidateTab.EISUKANA -> {
                                val input = inputString.value
                                if (input.isNotEmpty()) {
                                    ioScope.launch {
                                        setCandidatesEnglishKana(input)
                                        withContext(Dispatchers.Main) {
                                            hideFirstRowCandidatesInFullScreen(mainView)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                override fun onTabUnselected(tab: TabLayout.Tab?) {

                }

                override fun onTabReselected(tab: TabLayout.Tab?) {

                }

            })
        }
    }

    private fun setSuggestionRecyclerView(
        mainView: MainLayoutBinding, flexboxLayoutManagerRow: FlexboxLayoutManager
    ) {
        suggestionAdapter?.let { adapter ->
            adapter.setOnItemClickListener { candidate, position ->
                val insertString = inputString.value
                val currentInputMode: InputMode = currentTenkeyInputMode(mainView)
                vibrate()
                setCandidateClick(
                    candidate = candidate,
                    insertString = insertString,
                    currentInputMode = currentInputMode,
                    position = position
                )
            }
            adapter.setOnItemLongClickListener { candidate, i ->
                Timber.d("Candidate long tap: $candidate $i")
                if (isSelectedTextGemmaActionCandidate(candidate)) return@setOnItemLongClickListener
                val insertString = inputString.value
                if (shouldShowCandidateLongPressActions()) {
                    showCandidateLongPressActions(
                        insertString = insertString, candidate = candidate, candidatePosition = i
                    )
                }
            }

            adapter.setOnPhysicalKeyboardListener {
                mainView.apply {
                    if (keyboardView.isVisible || customLayoutDefault.isVisible || qwertyView.isVisible || tabletView.isVisible) {
                        hideAllKeyboards()
                        val heightPx = dpToPx(40f)
                        val widthPx = ViewGroup.LayoutParams.MATCH_PARENT
                        (mainView.suggestionViewParent.layoutParams as? FrameLayout.LayoutParams)?.let { params ->
                            params.bottomMargin = heightPx
                            mainView.suggestionViewParent.layoutParams = params
                        }
                        (mainView.root.layoutParams as? FrameLayout.LayoutParams)?.let { params ->
                            params.width = widthPx
                            mainView.root.layoutParams = params
                        }
                    } else {
                        if (keyboardOrder.isEmpty()) return@apply
                        showKeyboard(keyboardOrder[0])
                        setKeyboardSizeSwitchKeyboard(mainView)
                    }
                }
            }

            adapter.setOnItemHelperIconClickListener { helperIcon ->
                when (helperIcon) {
                    SuggestionAdapter.HelperIcon.UNDO -> {
                        if (!isEditHistoryEnabled()) return@setOnItemHelperIconClickListener
                        undoLastHistoryEntry()
                    }

                    SuggestionAdapter.HelperIcon.REDO -> {
                        if (!isEditHistoryEnabled()) return@setOnItemHelperIconClickListener
                        redoLastHistoryEntry()
                    }

                    SuggestionAdapter.HelperIcon.RECONVERT -> {
                        performPendingReconversion()
                    }

                    SuggestionAdapter.HelperIcon.PASTE -> {
                        Timber.d("SuggestionAdapter.HelperIcon.PASTE: clicked")
                        vibrate()
                        pasteAction()
                        if (clipboardPreviewTapToDelete == true) {
                            //clipboardUtil.clearClipboard()
                            adapter.apply {
                                setClipboardPreview("")
                                setPasteEnabled(false)
                            }
                        }
                    }
                }
            }
            adapter.setOnItemHelperIconLongClickListener { helperIcon ->
                when (helperIcon) {
                    SuggestionAdapter.HelperIcon.UNDO -> {
                        if (!isEditHistoryEnabled()) return@setOnItemHelperIconLongClickListener
                        undoAllHistoryEntries()
                    }

                    SuggestionAdapter.HelperIcon.REDO -> {
                        if (!isEditHistoryEnabled()) return@setOnItemHelperIconLongClickListener
                        redoAllHistoryEntries()
                    }

                    SuggestionAdapter.HelperIcon.RECONVERT -> Unit

                    SuggestionAdapter.HelperIcon.PASTE -> {
                        clipboardUtil.clearClipboard()
                        adapter.apply {
                            setClipboardPreview("")
                            setPasteEnabled(false)
                        }
                    }
                }
            }
            adapter.setOnCustomLayoutItemClickListener { position ->
                selectCustomKeyboardTab(
                    index = position,
                    reason = CustomKeyboardSelectionReason.UserTabClick
                )
            }
        }
        suggestionAdapterFull?.let { adapter ->
            adapter.setOnItemClickListener { candidate, position ->
                val insertString = inputString.value
                val currentInputMode: InputMode = currentTenkeyInputMode(mainView)
                vibrate()
                setCandidateClick(
                    candidate = candidate,
                    insertString = insertString,
                    currentInputMode = currentInputMode,
                    position = position
                )
            }
            adapter.setOnItemLongClickListener { candidate, i ->
                Timber.d("Candidate long tap: $candidate $i")
                if (isSelectedTextGemmaActionCandidate(candidate)) return@setOnItemLongClickListener
                val insertString = inputString.value
                if (shouldShowCandidateLongPressActions()) {
                    showCandidateLongPressActions(
                        insertString = insertString, candidate = candidate, candidatePosition = i
                    )
                }
            }
            adapter.setOnItemHelperIconClickListener { helperIcon ->
                when (helperIcon) {
                    SuggestionAdapter.HelperIcon.UNDO -> {
                        if (!isEditHistoryEnabled()) return@setOnItemHelperIconClickListener
                        undoLastHistoryEntry()
                    }

                    SuggestionAdapter.HelperIcon.REDO -> {
                        if (!isEditHistoryEnabled()) return@setOnItemHelperIconClickListener
                        redoLastHistoryEntry()
                    }

                    SuggestionAdapter.HelperIcon.RECONVERT -> {
                        performPendingReconversion()
                    }

                    SuggestionAdapter.HelperIcon.PASTE -> {
                        vibrate()
                        pasteAction()
                    }
                }
            }
            adapter.setOnItemHelperIconLongClickListener { helperIcon ->
                when (helperIcon) {
                    SuggestionAdapter.HelperIcon.UNDO -> {
                        if (!isEditHistoryEnabled()) return@setOnItemHelperIconLongClickListener
                        undoAllHistoryEntries()
                    }

                    SuggestionAdapter.HelperIcon.REDO -> {
                        if (!isEditHistoryEnabled()) return@setOnItemHelperIconLongClickListener
                        redoAllHistoryEntries()
                    }

                    SuggestionAdapter.HelperIcon.RECONVERT -> Unit

                    SuggestionAdapter.HelperIcon.PASTE -> {
                        clipboardUtil.clearClipboard()
                        adapter.apply {
                            setClipboardPreview("")
                            setPasteEnabled(false)
                        }
                    }
                }
            }
        }
        mainView.suggestionRecyclerView.apply {
            itemAnimator = null
            isFocusable = false
        }

        mainView.candidatesRowView.apply {
            itemAnimator = null
            isFocusable = false
        }
        suggestionAdapter.apply {
            mainView.candidatesRowView.layoutManager = flexboxLayoutManagerRow
            floatingKeyboardBinding?.let { floatingKeyboardLayoutBinding ->
                floatingKeyboardLayoutBinding.suggestionRecyclerView.let { sRecyclerView ->
                    sRecyclerView.layoutManager = FlexboxLayoutManager(applicationContext).apply {
                        flexDirection = FlexDirection.COLUMN
                    }
                    sRecyclerView.itemAnimator = null
                    sRecyclerView.isFocusable = false
                }
                floatingKeyboardLayoutBinding.candidatesRowView.let { fullCandidateView ->
                    fullCandidateView.itemAnimator = null
                    fullCandidateView.isFocusable = false
                    fullCandidateView.layoutManager =
                        FlexboxLayoutManager(applicationContext).apply {
                            flexDirection = FlexDirection.ROW
                            justifyContent = JustifyContent.FLEX_START
                        }
                }
            }
        }
        mainView.suggestionVisibility.setOnClickListener {
            _suggestionViewStatus.update { !it }
        }
        floatingKeyboardBinding?.suggestionVisibility?.setOnClickListener {
            _suggestionViewStatus.update { !it }
        }
    }

    private fun setMainSuggestionColumn(
        mainView: MainLayoutBinding
    ) {
        val isPortrait = resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT

        val columnNum = if (isPortrait) {
            candidateColumns ?: "1"
        } else {
            candidateColumnsLandscape ?: "1"
        }

        val adapter = mainView.suggestionRecyclerView.adapter
        mainView.suggestionRecyclerView.adapter = null

        if (mainView.suggestionRecyclerView.itemDecorationCount > 0) {
            mainView.suggestionRecyclerView.removeItemDecorationAt(0)
        }

        if (shouldUseSelectedTextGemmaActionLayout()) {
            mainView.suggestionRecyclerView.layoutManager =
                LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
            mainView.suggestionRecyclerView.adapter = adapter
            return
        }

        when (columnNum) {
            "1" -> {
                mainView.suggestionRecyclerView.layoutManager =
                    LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
            }

            "2", "3" -> {
                val spanCount = columnNum.toInt()
                val gridLayoutManager = GridLayoutManager(
                    this@IMEService, spanCount, GridLayoutManager.HORIZONTAL, false
                )

                gridLayoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                    override fun getSpanSize(position: Int): Int {
                        return when (adapter?.getItemViewType(position)) {
                            // If the item is the empty view or the custom layout picker,
                            // make it span all columns.
                            SuggestionAdapter.VIEW_TYPE_EMPTY, SuggestionAdapter.VIEW_TYPE_CUSTOM_LAYOUT_PICKER -> spanCount
                            SuggestionAdapter.VIEW_TYPE_INLINE_SUGGESTION -> spanCount
                            // Otherwise (for regular suggestions), make it span just one column.
                            else -> 1
                        }
                    }
                }

                val spacingInPixels =
                    resources.getDimensionPixelSize(com.kazumaproject.core.R.dimen.grid_spacing)

                mainView.suggestionRecyclerView.layoutManager = gridLayoutManager
                mainView.suggestionRecyclerView.addItemDecoration(
                    GridSpacingItemDecoration(
                        spanCount, spacingInPixels, true
                    )
                )
            }
        }
        mainView.suggestionRecyclerView.adapter = adapter
    }

    private fun shouldUseSelectedTextGemmaActionLayout(): Boolean {
        val suggestions = suggestionAdapter?.suggestions.orEmpty()
        return suggestions.isNotEmpty() && suggestions.all { isSelectedTextGemmaActionCandidate(it) }
    }

    private fun setShortCutAdapter(
        mainView: MainLayoutBinding
    ) {
        mainView.shortcutToolbarRecyclerview.apply {
            layoutManager =
                LinearLayoutManager(this@IMEService, LinearLayoutManager.HORIZONTAL, false)
            adapter = shortcutAdapter
        }
        mainView.upperAreaContentContainer.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            distributeShortcutToolbarItems(mainView)
        }
        distributeShortcutToolbarItems(mainView)
        applyShortcutAdapterIconColor()
        shortcutAdapter?.onItemClicked = { type ->
            when (type) {
                ShortcutType.SETTINGS -> {
                    launchSettingsActivity("setting_fragment_request")
                }

                ShortcutType.EMOJI -> {
                    vibrate()
                    _keyboardSymbolViewState.value = SymbolKeyboardState(
                        isShown = !_keyboardSymbolViewState.value.isShown
                    )
                }

                ShortcutType.TEMPLATE -> {
                    showUserTemplateListPopup()
                }

                ShortcutType.KEYBOARD_PICKER -> {
                    showKeyboardPicker()
                }

                ShortcutType.SELECT_ALL -> {
                    selectAllText()
                }

                ShortcutType.COPY -> {
                    copyAction()
                }

                ShortcutType.PASTE -> {
                    pasteAction()
                }

                ShortcutType.DATE_PICKER -> {
                    showCurrentDateListPopup()
                }

                ShortcutType.VOICE_INPUT -> {
                    startVoiceInput(mainView)
                }

                ShortcutType.CLIP_BOARD -> {
                    vibrate()
                    val isClipboardPanelShown =
                        _keyboardSymbolViewState.value.isShown &&
                            _keyboardSymbolViewState.value.mode == SymbolMode.CLIPBOARD
                    _keyboardSymbolViewState.value = SymbolKeyboardState(
                        isShown = !isClipboardPanelShown,
                        mode = SymbolMode.CLIPBOARD
                    )
                }
            }
        }
    }

    private fun distributeShortcutToolbarItems(mainView: MainLayoutBinding) {
        mainView.upperAreaContentContainer.post {
            val count = shortcutAdapter?.currentList?.size?.takeIf { it > 0 }
                ?: shortcutAdapter?.itemCount?.takeIf { it > 0 }
                ?: return@post
            val width = mainView.upperAreaContentContainer.width
            if (width <= 0) return@post
            val itemWidth = (width / count).coerceAtLeast(1)
            val usedWidth = itemWidth * count
            val sidePadding = ((width - usedWidth) / 2).coerceAtLeast(0)
            // Distribute any rounding remainder to the end padding so items fill the width exactly
            val endPadding = (width - usedWidth - sidePadding).coerceAtLeast(0)
            mainView.shortcutToolbarRecyclerview.setPadding(sidePadding, 0, endPadding, 0)
            mainView.shortcutToolbarRecyclerview.clipToPadding = true
            shortcutAdapter?.setItemWidth(itemWidth)
        }
    }

    private fun setSymbolKeyboard(
        mainView: MainLayoutBinding
    ) {
        mainView.keyboardSymbolView.apply {
            setLifecycleOwner(this@IMEService)
            setOnReturnToTenKeyButtonClickListener(object : ReturnToTenKeyButtonClickListener {
                override fun onClick() {
                    vibrate()
                    _keyboardSymbolViewState.value = SymbolKeyboardState(
                        isShown = !_keyboardSymbolViewState.value.isShown
                    )
                    finishComposingText()
                    setComposingText("", 0)
                }
            })
            setOnDeleteButtonSymbolViewClickListener(object : DeleteButtonSymbolViewClickListener {
                override fun onClick() {
                    if (!deleteKeyLongKeyPressed.get()) {
                        vibrate()
                        if (routeSymbolPanelSearchDelete()) return
                        sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
                    }
                    stopDeleteLongPress()
                }
            })

            setOnDeleteButtonFingerUpListener {
                stopDeleteLongPress()
            }
            setOnDeleteButtonSymbolViewLongClickListener(object :
                DeleteButtonSymbolViewLongClickListener {
                override fun onLongClickListener() {
                    onDeleteLongPressUp.set(true)
                    deleteLongPress()
                    _dakutenPressed.value = false
                    englishSpaceKeyPressed.set(false)
                    deleteKeyLongKeyPressed.set(true)
                }
            })
            /** ここで絵文字を追加 **/
            setOnSymbolRecyclerViewItemClickListener(object : SymbolRecyclerViewItemClickListener {
                override fun onClick(symbol: ClickedSymbol) {
                    vibrate()
                    insertDirectText(symbol.symbol)
                    CoroutineScope(Dispatchers.IO).launch {
                        clickedSymbolRepository.insert(
                            mode = symbol.mode, symbol = symbol.symbol
                        )
                    }
                }
            })
            setOnSymbolRecyclerViewItemLongClickListener(object :
                SymbolRecyclerViewItemLongClickListener {
                override fun onLongClick(symbol: ClickedSymbol, position: Int) {
                    vibrate()
                    CoroutineScope(Dispatchers.IO).launch {
                        clickedSymbolRepository.delete(
                            mode = symbol.mode, symbol = symbol.symbol
                        )
                    }
                }
            })
            setOnImageItemClickListener { bitmap -> pasteImageAction(bitmap) }
            setOnClipboardItemClickListener { item ->
                vibrate()
                pasteClipboardHistoryItem(item)
            }
            setOnClipboardItemLongClickListener { item, action ->
                handleClipboardHistoryItemAction(item, action)
            }
            setOnClipboardControlListener(
                onClearAll = {
                    vibrate()
                    CoroutineScope(Dispatchers.IO).launch {
                        clipboardHistoryRepository.deleteUnpinnedAll()
                    }
                },
                onSearch = { query ->
                    clipboardSearchQuery.value = query
                }
            )
            setOnEmojiSearchListener { query ->
                emojiSearchQuery.value = query
            }
            setOnSymbolPanelSearchFocusListener { active ->
                setSymbolPanelSearchFocused(active)
            }
            setClipboardHistoryEnabled(isClipboardHistoryFeatureEnabled)
            setOnClipboardHistoryToggleListener(this@IMEService)
            setDefaultEmojiSkinTone(defaultEmojiSkinTonePreference)
            setOnDefaultEmojiSkinToneChangeListener { skinTone ->
                defaultEmojiSkinTonePreference = skinTone
                appPreference.default_emoji_skin_tone_preference = skinTone
                floatingKeyboardBinding?.floatingSymbolKeyboard?.setDefaultEmojiSkinTone(skinTone)
            }
        }

        floatingKeyboardBinding?.floatingSymbolKeyboard?.apply {
            setLifecycleOwner(this@IMEService)
            setOnReturnToTenKeyButtonClickListener(object : ReturnToTenKeyButtonClickListener {
                override fun onClick() {
                    vibrate()
                    _keyboardSymbolViewState.value = SymbolKeyboardState(
                        isShown = !_keyboardSymbolViewState.value.isShown
                    )
                    finishComposingText()
                    setComposingText("", 0)
                }
            })
            setOnDeleteButtonSymbolViewClickListener(object : DeleteButtonSymbolViewClickListener {
                override fun onClick() {
                    if (!deleteKeyLongKeyPressed.get()) {
                        vibrate()
                        if (routeSymbolPanelSearchDelete()) return
                        sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
                    }
                    stopDeleteLongPress()
                }
            })

            setOnDeleteButtonFingerUpListener {
                stopDeleteLongPress()
            }
            setOnDeleteButtonSymbolViewLongClickListener(object :
                DeleteButtonSymbolViewLongClickListener {
                override fun onLongClickListener() {
                    onDeleteLongPressUp.set(true)
                    deleteLongPress()
                    _dakutenPressed.value = false
                    englishSpaceKeyPressed.set(false)
                    deleteKeyLongKeyPressed.set(true)
                }
            })
            /** ここで絵文字を追加 **/
            setOnSymbolRecyclerViewItemClickListener(object : SymbolRecyclerViewItemClickListener {
                override fun onClick(symbol: ClickedSymbol) {
                    vibrate()
                    insertDirectText(symbol.symbol)
                    CoroutineScope(Dispatchers.IO).launch {
                        clickedSymbolRepository.insert(
                            mode = symbol.mode, symbol = symbol.symbol
                        )
                    }
                }
            })
            setOnSymbolRecyclerViewItemLongClickListener(object :
                SymbolRecyclerViewItemLongClickListener {
                override fun onLongClick(symbol: ClickedSymbol, position: Int) {
                    vibrate()
                    CoroutineScope(Dispatchers.IO).launch {
                        clickedSymbolRepository.delete(
                            mode = symbol.mode, symbol = symbol.symbol
                        )
                    }
                }
            })
            setOnImageItemClickListener { bitmap -> pasteImageAction(bitmap) }
            setOnClipboardItemClickListener { item ->
                vibrate()
                pasteClipboardHistoryItem(item)
            }
            setOnClipboardItemLongClickListener { item, action ->
                handleClipboardHistoryItemAction(item, action)
            }
            setOnClipboardControlListener(
                onClearAll = {
                    vibrate()
                    CoroutineScope(Dispatchers.IO).launch {
                        clipboardHistoryRepository.deleteUnpinnedAll()
                    }
                },
                onSearch = { query ->
                    clipboardSearchQuery.value = query
                }
            )
            setOnEmojiSearchListener { query ->
                emojiSearchQuery.value = query
            }
            setOnSymbolPanelSearchFocusListener { active ->
                setSymbolPanelSearchFocused(active)
            }
            setClipboardHistoryEnabled(isClipboardHistoryFeatureEnabled)
            setOnClipboardHistoryToggleListener(this@IMEService)
            setDefaultEmojiSkinTone(defaultEmojiSkinTonePreference)
            setOnDefaultEmojiSkinToneChangeListener { skinTone ->
                defaultEmojiSkinTonePreference = skinTone
                appPreference.default_emoji_skin_tone_preference = skinTone
                mainView.keyboardSymbolView.setDefaultEmojiSkinTone(skinTone)
            }
        }
    }

    private fun setQWERTYKeyboard(
        mainView: MainLayoutBinding
    ) {
        configureQwertyView(mainView.qwertyView, mainView)
    }

    private fun configureQwertyView(
        qwertyView: QWERTYKeyboardView,
        mainView: MainLayoutBinding,
    ) {
        qwertyView.apply {
            val isNight = currentNightMode == Configuration.UI_MODE_NIGHT_YES
            val isDark = keyboardThemeMode == "dark" || (keyboardThemeMode == "default" && isNight)
            val isCustom = keyboardThemeMode == "custom"

            val resolvedBgColor = customThemeBgColor ?: Color.WHITE
            val resolvedKeyColor = customThemeKeyColor ?: Color.WHITE
            val resolvedSpecialKeyColor = customThemeSpecialKeyColor ?: Color.GRAY
            val resolvedEnterKeyColor = customThemeEnterKeyColor ?: Color.BLUE
            val resolvedPopupBgColor = customThemePopupBgColor ?: Color.WHITE

            val resolvedKeyTextColor = if (isCustom) {
                customThemeKeyTextColor ?: if (resolvedKeyColor.isLightColor()) Color.BLACK else Color.WHITE
            } else {
                if (isDark) Color.WHITE else Color.BLACK
            }

            val resolvedSpecialKeyTextColor = if (isCustom) {
                customThemeSpecialKeyTextColor ?: if (resolvedSpecialKeyColor.isLightColor()) Color.BLACK else Color.WHITE
            } else {
                if (isDark) Color.WHITE else Color.BLACK
            }

            val resolvedEnterKeyTextColor = if (isCustom) {
                customThemeEnterKeyTextColor ?: if (resolvedEnterKeyColor.isLightColor()) Color.BLACK else Color.WHITE
            } else {
                if (isDark) Color.WHITE else Color.BLACK
            }

            val resolvedPopupTextColor = if (isCustom) {
                customThemePopupTextColor ?: if (resolvedPopupBgColor.isLightColor()) Color.BLACK else Color.WHITE
            } else {
                if (isDark) Color.WHITE else Color.BLACK
            }

            applyKeyboardTheme(
                themeMode = keyboardThemeMode ?: "default",
                currentNightMode = currentNightMode,
                isDynamicColorEnabled = DynamicColors.isDynamicColorAvailable(),
                customBgColor = resolvedBgColor,
                customKeyColor = resolvedKeyColor,
                customSpecialKeyColor = resolvedSpecialKeyColor,
                customEnterKeyColor = resolvedEnterKeyColor,
                customKeyTextColor = resolvedKeyTextColor,
                customSpecialKeyTextColor = resolvedSpecialKeyTextColor,
                customEnterKeyTextColor = resolvedEnterKeyTextColor,
                customPopupBgColor = resolvedPopupBgColor,
                customPopupTextColor = resolvedPopupTextColor,
                liquidGlassEnable = liquidGlassThemePreference ?: false,
                customBorderEnable = customKeyBorderEnablePreference ?: false,
                customBorderColor = customKeyBorderEnableColor ?: Color.BLACK,
                liquidGlassKeyAlphaEnable = liquidGlassKeyBlurRadiousPreference ?: 255,
                borderWidth = customKeyBorderWidth ?: 1,
                keyBorderEnable = keyBorderEnable ?: false,
                keyCornerRadiusDp = keyCornerRadiusDp ?: 8,
                keyPopupStyle = keyPopupStyle ?: "default"
            )
            setLongPressTimeout((longPressTimeoutPreferenceValue ?: 300).toLong())
            applyPopupViewStyleSet(currentQwertyPopupViewStyleSet())
            setSpecialKeyVisibility(
                showCursors = qwertyShowCursorButtonsPreference ?: false,
                showSwitchKey = qwertyShowIMEButtonPreference ?: true,
                showKutouten = qwertyShowKutoutenButtonsPreference ?: false,
                showEmojiKey = qwertyShowEmojiButtonPreference ?: false
            )
            setRomajiEnglishSwitchKeyTextWithStyle(true)
            updateSymbolKeymapState(qwertyShowKeymapSymbolsPreference ?: false)
            updateNumberKeyState(qwertyShowNumberButtonsPreference ?: false)
            setPopUpViewState(qwertyShowPopupWindowPreference ?: true)
            setFlickUpDetectionEnabled(qwertyEnableFlickUpPreference ?: false)
            setFlickDownDetectionEnabled(qwertyEnableFlickDownPreference ?: false)
            setNumberKeyFlickUpChars(qwertyNumberKeyFlickUpChars)
            setNumberKeyFlickDownChars(qwertyNumberKeyFlickDownChars)
            setNumberSwitchKeyTextStyle(
                excludeNumber = qwertySwitchNumberKeyWithoutNumberPreference ?: false
            )
            setSwitchNumberLayoutKeyVisibility(false)
            setDeleteLeftFlickEnabled(isDeleteLeftFlickPreference ?: true)
            setDeleteUpFlickEnabled(isDeleteUpFlickPreference ?: false)
            setDeleteDownFlickEnabled(isDeleteDownFlickPreference ?: false)
            val glideCoordinator = qwertyGlideInputCoordinator
                ?: QwertyGlideInputCoordinator(
                    scope = scope,
                    candidateProvider = englishEngine,
                    previousTextProvider = { getPreviousTextForQwertyGlide() },
                    onPreviewCandidates = { candidates ->
                        showQwertyGlideCandidates(candidates, applyFirstCandidate = false)
                    },
                    onFinalCandidates = { candidates ->
                        showQwertyGlideCandidates(candidates, applyFirstCandidate = true)
                    },
                    onGlideStarted = {
                        commitPreviousQwertyGlideCandidateOnNewGlideIfNeeded()
                    },
                    onCancel = {},
                    onProcessingChanged = { isProcessing ->
                        setSuggestionProgressVisible(
                            reason = SuggestionProgressReason.QwertyGlideDecode,
                            visible = isProcessing
                        )
                    }
                ).also { qwertyGlideInputCoordinator = it }
            setQwertyGlideInputListener(glideCoordinator)
            setQwertyGlideInputMode(calculateQwertyGlideInputMode())
            setKeyMargins(
                verticalDp = qwertyKeyVerticalMargin ?: 5.0f,
                horizontalGapDp = qwertyKeyHorizontalGap ?: 2.0f,
                indentLargeDp = qwertyKeyIndentLarge ?: 23.0f,
                indentSmallDp = qwertyKeyIndentSmall ?: 9.0f,
                sideMarginDp = qwertyKeySideMargin ?: 4.0f,
                textSizeSp = qwertyKeyTextSize ?: 18.0f,
                specialTextSizeSp = qwertySpecialKeyTextSize ?: 12.0f,
                specialIconSizeDp = qwertySpecialKeyIconSize ?: 18.0f
            )

            setOnQWERTYKeyListener(object : QWERTYKeyListener {
                override fun onPressedQWERTYKey(qwertyKey: QWERTYKey) {
                    Timber.d("Pressed Key: $qwertyKey")
                    handleKeyPressFeedback(getKeySoundType(qwertyKey))
                    deleteLongPressJob?.cancel()
                }

                override fun onReleasedQWERTYKey(
                    qwertyKey: QWERTYKey, tap: Char?, variations: List<Char>?
                ) {
                    Timber.d("onReleasedQWERTYKey: $qwertyKey")
                    when (vibrationTimingStr) {
                        "both" -> {
                            vibrate()
                        }

                        "press" -> {

                        }

                        "release" -> {

                        }
                    }
                    val insertString = inputString.value
                    val sb = StringBuilder()
                    val suggestionList = suggestionAdapter?.suggestions ?: emptyList()
                    if (qwertyKey != QWERTYKey.QWERTYKeyDelete) {
                        clearDeletedBuffer()
                        refreshEditHistoryUi()
                    }
                    when (qwertyKey) {
                        QWERTYKey.QWERTYKeyNotSelect -> {}
                        QWERTYKey.QWERTYKeyShift -> {
                            hardKeyboardShiftPressd = true
                        }

                        QWERTYKey.QWERTYKeyDelete -> {
                            if (!deleteKeyLongKeyPressed.get()) {
                                handleDeleteKeyTap(insertString, suggestionList)
                            }
                            stopDeleteLongPress()
                            if (isDefaultRomajiHenkanMap) {
                                hardKeyboardShiftPressd = false
                            }
                        }

                        QWERTYKey.QWERTYKeySwitchDefaultLayout -> {
                            if (!onKeyboardSwitchLongPressUp) {
                                switchNextKeyboard()
                                _inputString.update { "" }
                                finishComposingText()
                                setComposingText("", 0)
                                setQwertySwitchNumberLayoutKeyVisibilityOnActiveSurface(false)
                            }
                        }

                        QWERTYKey.QWERTYKeySwitchMode -> {

                        }

                        QWERTYKey.QWERTYKeyEmoji -> {
                            toggleEmojiKeyboard()
                        }

                        QWERTYKey.QWERTYKeySpace -> {
                            Timber.d("onReleasedQWERTYKey: QWERTYKeySpace $isSpaceKeyLongPressed")
                            if (!isSpaceKeyLongPressed) {
                                handleSpaceKeyClickInQWERTY(insertString, mainView, suggestionList)
                            }
                            isSpaceKeyLongPressed = false
                        }

                        QWERTYKey.QWERTYKeyReturn -> {
                            if (insertString.isNotEmpty()) {
                                handleNonEmptyInputEnterKey(suggestionList, mainView, insertString)
                            } else {
                                handleEmptyInputEnterKey(mainView)
                            }
                        }

                        QWERTYKey.QWERTYKeyCursorLeft -> {
                            Timber.d("QWERTYKey.QWERTYKeyCursorLeft")
                            if (!leftCursorKeyLongKeyPressed.get()) {
                                if (moveFocusedBunsetsuSegment(delta = -1)) {
                                    // handled by bunsetsu cursor move session
                                } else if (isHenkan.get()) {
                                    val suggestions = suggestionAdapter?.suggestions ?: emptyList()
                                    handleDeleteKeyInHenkan(suggestions, insertString)
                                } else {
                                    handleLeftCursor(GestureType.Tap, insertString)
                                }
                            }
                            onLeftKeyLongPressUp.set(true)
                            leftCursorKeyLongKeyPressed.set(false)
                            leftLongPressJob?.cancel()
                            leftLongPressJob = null
                            isSpaceKeyLongPressed = false
                        }

                        QWERTYKey.QWERTYKeyCursorRight -> {
                            Timber.d("QWERTYKey.QWERTYKeyCursorRight")
                            if (!rightCursorKeyLongKeyPressed.get()) {
                                if (moveFocusedBunsetsuSegment(delta = 1)) {
                                    // handled by bunsetsu cursor move session
                                } else if (isHenkan.get()) {
                                    val suggestions = suggestionAdapter?.suggestions ?: emptyList()
                                    handleJapaneseModeSpaceKey(
                                        mainView, suggestions, insertString
                                    )
                                } else {
                                    actionInRightKeyPressed(GestureType.Tap, insertString)
                                }
                            }
                            onRightKeyLongPressUp.set(true)
                            rightCursorKeyLongKeyPressed.set(false)
                            rightLongPressJob?.cancel()
                            rightLongPressJob = null
                            isSpaceKeyLongPressed = false
                        }

                        QWERTYKey.QWERTYKeyCursorUp -> {
                            if (!leftCursorKeyLongKeyPressed.get()) {
                                handleLeftCursor(GestureType.FlickTop, insertString)
                            }
                            onLeftKeyLongPressUp.set(true)
                            leftCursorKeyLongKeyPressed.set(false)
                            leftLongPressJob?.cancel()
                            leftLongPressJob = null
                            isSpaceKeyLongPressed = false
                        }

                        QWERTYKey.QWERTYKeyCursorDown -> {
                            if (!leftCursorKeyLongKeyPressed.get()) {
                                handleLeftCursor(GestureType.FlickBottom, insertString)
                            }
                            onLeftKeyLongPressUp.set(true)
                            leftCursorKeyLongKeyPressed.set(false)
                            leftLongPressJob?.cancel()
                            leftLongPressJob = null
                            isSpaceKeyLongPressed = false
                        }

                        QWERTYKey.QWERTYKeySwitchRomajiEnglish -> {
                            val romajiMode = currentQwertyRomajiModeForSession
                            setCurrentQwertyRomajiModeForSession(!romajiMode)
                            if (currentInputModeForSession == InputMode.ModeJapanese) {
                                setCurrentInputModeForSession(InputMode.ModeEnglish)
                                setQwertyRomajiSwitchTextOnActiveSurface(false)
                            } else {
                                setCurrentInputModeForSession(InputMode.ModeJapanese)
                                setQwertyRomajiSwitchTextOnActiveSurface(true)
                            }
                        }

                        QWERTYKey.QWERTYKeySwitchNumberKey -> {
                            if (previousTenKeyQWERTYMode == null) {
                                returnDefaultQwertyFromNumberKey(mainView, insertString)
                            } else {
                                previousTenKeyQWERTYMode?.let {
                                    when (it) {
                                        TenKeyQWERTYMode.Default -> {
                                            returnDefaultQwertyFromNumberKey(mainView, insertString)
                                        }

                                        TenKeyQWERTYMode.Sumire -> {
                                            if (qwertySwitchNumberKeyWithoutNumberPreference == true) {
                                                customKeyboardMode = KeyboardInputMode.HIRAGANA
                                                _tenKeyQWERTYMode.update { TenKeyQWERTYMode.Sumire }
                                                setCurrentInputModeForSession(InputMode.ModeJapanese)
                                                createNewKeyboardLayoutForSumire()
                                                if (insertString.isEmpty()) {
                                                    setKeyboardSizeSwitchKeyboard(mainView)
                                                } else {
                                                    setKeyboardHeightWithAdditional(mainView)
                                                }
                                            } else {
                                                customKeyboardMode = KeyboardInputMode.SYMBOLS
                                                _tenKeyQWERTYMode.update { TenKeyQWERTYMode.Sumire }
                                                createNewKeyboardLayoutForSumire()
                                                if (insertString.isEmpty()) {
                                                    setKeyboardSizeSwitchKeyboard(mainView)
                                                } else {
                                                    setKeyboardHeightWithAdditional(mainView)
                                                }
                                            }
                                        }

                                        TenKeyQWERTYMode.Custom -> {
                                            customKeyboardMode = KeyboardInputMode.HIRAGANA
                                            _tenKeyQWERTYMode.update { TenKeyQWERTYMode.Custom }
                                            createNewKeyboardLayoutForSumire()
                                            if (insertString.isEmpty()) {
                                                setKeyboardSizeSwitchKeyboard(mainView)
                                            } else {
                                                setKeyboardHeightWithAdditional(mainView)
                                            }
                                        }

                                        else -> {
                                            returnDefaultQwertyFromNumberKey(mainView, insertString)
                                        }
                                    }
                                }
                            }
                        }

                        else -> {
                            val effectiveInsertString = if (isBunsetsuCursorMoveSessionActive()) {
                                exitBunsetsuCursorMoveSessionToRawInput()
                            } else {
                                insertString
                            }
                            val inputForAppend = if (isHenkan.get()) {
                                commitCurrentHenkanForNewInput()
                                ""
                            } else if (isSymbolPanelSearchRoutingActive()) {
                                lastQwertyRomajiRawInput.orEmpty()
                            } else {
                                effectiveInsertString
                            }
                            if (currentInputModeForSession == InputMode.ModeJapanese) {
                                if (inputForAppend.isNotEmpty()) {
                                    Timber.d("QWERTY romaji not empty: $hardKeyboardShiftPressd $qwertyRomajiShiftConversionPreference")
                                    if (qwertyRomajiShiftConversionPreference == true) {
                                        if (hardKeyboardShiftPressd) {
                                            Timber.d("QWERTY romaji hardKeyboardShiftPressd: $tap")
                                            tap?.let { c ->
                                                val charToAppend =
                                                    if (isDefaultRomajiHenkanMap &&
                                                        c.shouldApplyRomajiQwertyWidthPreference()
                                                    ) {
                                                        c.toRomajiQwertyOutputChar()
                                                    } else {
                                                        c
                                                    }
                                                Timber.d("QWERTY romaji : $charToAppend")
                                                sb.append(inputForAppend)
                                                    .append(charToAppend)
                                                romajiConverter?.let { converter ->
                                                    val raw = sb.toString()
                                                    updateInputStringFromQwertyRomajiBuffer(
                                                        raw,
                                                        converter.convertQWERTYZenkaku(raw),
                                                    )
                                                }
                                            }
                                        } else {
                                            tap?.let { c ->
                                                val charToAppend = if (isDefaultRomajiHenkanMap) {
                                                    c.toRomajiQwertyOutputChar()
                                                } else {
                                                    c
                                                }
                                                Timber.d("QWERTY romaji : $charToAppend")
                                                sb.append(inputForAppend)
                                                    .append(charToAppend)
                                                romajiConverter?.let { converter ->
                                                    val raw = sb.toString()
                                                    updateInputStringFromQwertyRomajiBuffer(
                                                        raw,
                                                        converter.convertQWERTYZenkaku(raw),
                                                    )
                                                }
                                            }
                                        }
                                    } else {
                                        if (hardKeyboardShiftPressd) {
                                            Timber.d("QWERTY romaji hardKeyboardShiftPressd: $tap")
                                            handleTap(tap, inputForAppend, sb, mainView)
                                        } else {
                                            tap?.let { c ->
                                                val charToAppend = if (isDefaultRomajiHenkanMap) {
                                                    c.toRomajiQwertyOutputChar()
                                                } else {
                                                    c
                                                }
                                                Timber.d("QWERTY romaji : $charToAppend")
                                                sb.append(inputForAppend)
                                                    .append(charToAppend)
                                                romajiConverter?.let { converter ->
                                                    val raw = sb.toString()
                                                    updateInputStringFromQwertyRomajiBuffer(
                                                        raw,
                                                        converter.convertQWERTYZenkaku(raw),
                                                    )
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    tap?.let { c ->
                                        romajiConverter?.let { converter ->
                                            val charToAppend =
                                                if (isDefaultRomajiHenkanMap &&
                                                    c.shouldUseRomajiQwertyOutputCharAfterShift()
                                                ) {
                                                    c.toRomajiQwertyOutputChar()
                                                } else {
                                                    c
                                                }
                                            Timber.d("QWERTY romaji 2: $charToAppend")
                                            val raw = charToAppend.toString()
                                            updateInputStringFromQwertyRomajiBuffer(
                                                raw,
                                                converter.convertQWERTYZenkaku(raw),
                                            )
                                        }
                                    }
                                }
                            } else {
                                handleTap(tap, inputForAppend, sb, mainView)
                            }
                            isContinuousTapInputEnabled.set(true)
                            lastFlickConvertedNextHiragana.set(true)
                        }
                    }
                }

                override fun onLongPressQWERTYKey(qwertyKey: QWERTYKey) {
                    when (qwertyKey) {
                        QWERTYKey.QWERTYKeyDelete -> {
                            if (isHenkan.get()) {
                                cancelHenkanByLongPressDeleteKey()
                            } else {
                                onDeleteLongPressUp.set(true)
                                deleteLongPress()
                                _dakutenPressed.value = false
                                englishSpaceKeyPressed.set(false)
                                deleteKeyLongKeyPressed.set(true)
                            }
                        }

                        QWERTYKey.QWERTYKeySwitchDefaultLayout -> {
                            showListPopup()
                        }

                        QWERTYKey.QWERTYKeySpace -> {
                            val insertString = inputString.value
                            if (switchBunsetsuSplitPattern()) {
                                isSpaceKeyLongPressed = true
                                return
                            }
                            if (insertString.isEmpty() || !currentQwertyRomajiModeForSession) {
                                setCursorMode(true)
                                isSpaceKeyLongPressed = true
                            } else {
                                if (zenzEnableLongPressConversionPreference == true) {
                                    scope.launch {
                                        filteredCandidateList = suggestionAdapter?.suggestions
                                        val candidates = performZenzRequest(insertString)
                                        _zenzCandidates.update { candidates }
                                    }
                                    isSpaceKeyLongPressed = true
                                } else {
                                    if (conversionKeySwipePreference == true) {
                                        if (!isHenkan.get()) {
                                            setCursorMode(true)
                                            isSpaceKeyLongPressed = true
                                        }
                                    } else {
                                        setCursorMode(true)
                                        isSpaceKeyLongPressed = true
                                    }
                                }
                            }
                        }

                        QWERTYKey.QWERTYKeyCursorRight -> {
                            handleRightLongPress()
                            rightCursorKeyLongKeyPressed.set(true)
                            if (selectMode.value) {
                                clearDeletedBufferWithoutResetLayout()
                            } else {
                                clearDeletedBuffer()
                            }
                            refreshEditHistoryUi()
                        }

                        QWERTYKey.QWERTYKeyCursorLeft -> {
                            handleLeftLongPress()
                            leftCursorKeyLongKeyPressed.set(true)
                            if (selectMode.value) {
                                clearDeletedBufferWithoutResetLayout()
                            } else {
                                clearDeletedBuffer()
                            }
                            refreshEditHistoryUi()
                        }

                        else -> {

                        }
                    }
                }

                override fun onFlickUPQWERTYKey(
                    qwertyKey: QWERTYKey, tap: Char?, variations: List<Char>?
                ) {
                    Timber.d("onFlickUPQWERTYKey: $qwertyKey, $tap, $variations")
                    when (vibrationTimingStr) {
                        "both" -> {
                            vibrate()
                        }

                        "press" -> {

                        }

                        "release" -> {

                        }
                    }
                    val insertString = inputString.value
                    val sb = StringBuilder()

                    if (qwertyKey != QWERTYKey.QWERTYKeyDelete) {
                        clearDeletedBuffer()
                        refreshEditHistoryUi()
                    }

                    variations?.let { variation ->
                        if (variation.isNotEmpty()) {
                            if (switchQWERTYPassword == true) {
                                if (currentInputType in passwordTypesWithOutNumber) {
                                    handleTap(
                                        variation.first().toHankakuKigou(),
                                        insertString,
                                        sb,
                                        mainView
                                    )
                                } else {
                                    handleTap(variation.first(), insertString, sb, mainView)
                                }
                            } else {
                                handleTap(variation.first(), insertString, sb, mainView)
                            }
                        }
                    }
                }

                override fun onFlickDownQWERTYKey(
                    qwertyKey: QWERTYKey,
                    character: Char
                ) {
                    Timber.d("onFlickDownQWERTYKey: $qwertyKey, $character")
                    when (vibrationTimingStr) {
                        "both" -> {
                            vibrate()
                        }

                        "press" -> {}

                        "release" -> {}
                    }
                    if (qwertyKey != QWERTYKey.QWERTYKeyDelete) {
                        clearDeletedBuffer()
                        refreshEditHistoryUi()
                    }
                    handleTap(character, inputString.value, StringBuilder(), mainView)
                }

                override fun onFlickDirectionQWERTYKey(
                    qwertyKey: QWERTYKey,
                    direction: String
                ) {
                    Timber.d("onFlickDirectionQWERTYKey: $qwertyKey, $direction")
                    vibrate()
                    val keyCode = when (direction) {
                        "UP" -> android.view.KeyEvent.KEYCODE_DPAD_UP
                        "DOWN" -> android.view.KeyEvent.KEYCODE_DPAD_DOWN
                        "LEFT" -> android.view.KeyEvent.KEYCODE_DPAD_LEFT
                        "RIGHT" -> android.view.KeyEvent.KEYCODE_DPAD_RIGHT
                        else -> return
                    }
                    sendDownUpKeyEvents(keyCode)
                }
            })

            setOnDeleteLeftFlickListener {
                val insertString = inputString.value
                Timber.d("setOnDeleteLeftFlickListener called: [$insertString]")
                deleteWordOrSymbolsBeforeCursor(insertString)
            }

            setOnDeleteUpFlickListener {
                if (isDeleteUpFlickPreference != true) return@setOnDeleteUpFlickListener
                val insertString = inputString.value
                Timber.d("setOnDeleteUpFlickListener called: [$insertString]")
                deleteWordOrSymbolsAfterCursor(insertString)
            }

            setOnDeleteDownFlickListener {
                if (isDeleteDownFlickPreference != true) return@setOnDeleteDownFlickListener
                Timber.d("setOnDeleteDownFlickListener called")
                undoLastHistoryEntry()
            }
        }
    }

    private fun getPreviousTextForQwertyGlide(): String {
        return editorGateway.getTextBeforeCursor(64, 0)?.toString().orEmpty()
    }

    private fun showQwertyGlideCandidates(
        candidates: List<Candidate>,
        applyFirstCandidate: Boolean
    ) {
        if (candidates.isEmpty()) return
        if (currentQwertyRomajiModeForSession) return
        suggestionClickNum = 0
        suggestionAdapter?.updateHighlightPosition(RecyclerView.NO_POSITION)
        suggestionAdapter?.suggestions = candidates
        suggestionAdapterFull?.suggestions = candidates
        if (physicalKeyboardEnable.replayCache.firstOrNull() == true) {
            updateSuggestionsForFloatingCandidate(
                candidates.map { CandidateItem(word = it.string, length = it.length) }
            )
        }
        if (applyFirstCandidate) {
            clearDeletedBuffer()
            refreshEditHistoryUi()
            val first = candidates.first()
            currentQwertyGlideCompositionText = if (
                first.type == QWERTY_GLIDE_CANDIDATE_TYPE
            ) {
                first.string
            } else {
                null
            }
            suppressNextQwertyGlideSuggestionRefresh = true
            _inputString.update { first.string }
            val spannable = createSpannableWithTail(first.string)
            setComposingTextAfterEdit(
                inputString = first.string,
                spannableString = spannable,
                backgroundColor = if (customComposingTextPreference == true) {
                    inputCompositionAfterBackgroundColor
                        ?: getColor(com.kazumaproject.core.R.color.blue)
                } else {
                    getColor(com.kazumaproject.core.R.color.blue)
                },
                textColor = if (customComposingTextPreference == true) {
                    inputCompositionTextColor
                } else {
                    null
                }
            )
        }
    }

    private fun commitPreviousQwertyGlideCandidateOnNewGlideIfNeeded() {
        val firstCandidate = suggestionAdapter?.suggestions?.firstOrNull()
        val decision = QwertyGlideCommitPolicy.resolvePreviousGlideCommitDecision(
            qwertyGlideInputPreference = qwertyGlideInputPreference,
            qwertyGlideCommitPreviousCandidateOnNewGlidePreference =
                qwertyGlideCommitPreviousCandidateOnNewGlidePreference,
            qwertyGlideInsertSpaceAfterCommittingPreviousCandidatePreference =
                qwertyGlideInsertSpaceAfterCommittingPreviousCandidatePreference,
            inputString = inputString.value,
            stringInTail = stringInTail.get(),
            currentQwertyRomajiModeForSession = currentQwertyRomajiModeForSession,
            firstCandidate = firstCandidate,
            currentQwertyGlideCompositionText = currentQwertyGlideCompositionText
        )
        if (decision !is QwertyGlidePreviousCandidateCommitDecision.Commit) return

        beginBatchEdit()
        try {
            setComposingText("", 0)
            finishComposingText()
            commitText(decision.commitText, 1)
        } finally {
            endBatchEdit()
        }
        stringInTail.set("")
        currentQwertyGlideCompositionText = null
        resetFlagsSuggestionClick()
    }

    private suspend fun setSymbols(mainView: MainLayoutBinding) {
        coroutineScope {
            if (cachedEmoji == null || cachedEmoticons == null || cachedSymbols == null) {
                val emojiDeferred =
                    async(Dispatchers.Default) { kanaKanjiEngine.getSymbolEmojiCandidates() }
                val emoticonDeferred =
                    async(Dispatchers.Default) { kanaKanjiEngine.getSymbolEmoticonCandidates() }
                val symbolDeferred =
                    async(Dispatchers.Default) { kanaKanjiEngine.getSymbolCandidates() }
                cachedEmoji = emojiDeferred.await()
                cachedEmoticons = emoticonDeferred.await()
                cachedSymbols = symbolDeferred.await()
            }
            val historyDeferred = async(Dispatchers.Default) { clickedSymbolRepository.getAll() }
            cachedClickedSymbolHistory =
                historyDeferred.await().sortedByDescending { it.timestamp }.distinctBy { it.symbol }
        }
        Timber.d("setSymbols: ${cachedEmoji?.size}")
        mainView.keyboardSymbolView.setSymbolLists(
            emojiList = cachedEmoji ?: emptyList(),
            emoticons = cachedEmoticons ?: emptyList(),
            symbols = cachedSymbols ?: emptyList(),
            clipBoardItems = currentClipboardItems,
            symbolsHistory = cachedClickedSymbolHistory ?: emptyList(),
            symbolMode = symbolKeyboardFirstItem ?: SymbolMode.EMOJI,
            defaultEmojiSkinTone = defaultEmojiSkinTonePreference

        )
    }

    private suspend fun setSymbolsClipboard(mainView: MainLayoutBinding) {
        coroutineScope {
            if (cachedEmoji == null || cachedEmoticons == null || cachedSymbols == null) {
                val emojiDeferred =
                    async(Dispatchers.Default) { kanaKanjiEngine.getSymbolEmojiCandidates() }
                val emoticonDeferred =
                    async(Dispatchers.Default) { kanaKanjiEngine.getSymbolEmoticonCandidates() }
                val symbolDeferred =
                    async(Dispatchers.Default) { kanaKanjiEngine.getSymbolCandidates() }
                cachedEmoji = emojiDeferred.await()
                cachedEmoticons = emoticonDeferred.await()
                cachedSymbols = symbolDeferred.await()
            }
            val historyDeferred = async(Dispatchers.Default) { clickedSymbolRepository.getAll() }
            cachedClickedSymbolHistory =
                historyDeferred.await().sortedByDescending { it.timestamp }.distinctBy { it.symbol }
        }
        Timber.d("setSymbols: ${cachedEmoji?.size}")
        mainView.keyboardSymbolView.setSymbolLists(
            emojiList = cachedEmoji ?: emptyList(),
            emoticons = cachedEmoticons ?: emptyList(),
            symbols = cachedSymbols ?: emptyList(),
            clipBoardItems = currentClipboardItems,
            symbolsHistory = cachedClickedSymbolHistory ?: emptyList(),
            symbolMode = SymbolMode.CLIPBOARD,
            defaultEmojiSkinTone = defaultEmojiSkinTonePreference

        )
    }

    private suspend fun setSymbolsFloating(floatingKeyboardLayoutBinding: FloatingKeyboardLayoutBinding) {
        coroutineScope {
            if (cachedEmoji == null || cachedEmoticons == null || cachedSymbols == null) {
                val emojiDeferred =
                    async(Dispatchers.Default) { kanaKanjiEngine.getSymbolEmojiCandidates() }
                val emoticonDeferred =
                    async(Dispatchers.Default) { kanaKanjiEngine.getSymbolEmoticonCandidates() }
                val symbolDeferred =
                    async(Dispatchers.Default) { kanaKanjiEngine.getSymbolCandidates() }
                cachedEmoji = emojiDeferred.await()
                cachedEmoticons = emoticonDeferred.await()
                cachedSymbols = symbolDeferred.await()
            }
            val historyDeferred = async(Dispatchers.Default) { clickedSymbolRepository.getAll() }
            cachedClickedSymbolHistory =
                historyDeferred.await().sortedByDescending { it.timestamp }.distinctBy { it.symbol }
        }
        floatingKeyboardLayoutBinding.floatingSymbolKeyboard.setSymbolLists(
            emojiList = cachedEmoji ?: emptyList(),
            emoticons = cachedEmoticons ?: emptyList(),
            symbols = cachedSymbols ?: emptyList(),
            clipBoardItems = currentClipboardItems,
            symbolsHistory = cachedClickedSymbolHistory ?: emptyList(),
            symbolMode = symbolKeyboardFirstItem ?: SymbolMode.EMOJI,
            defaultEmojiSkinTone = defaultEmojiSkinTonePreference

        )
    }

    private fun clearSymbols() {
        cachedEmoji = null
        cachedEmoticons = null
        cachedSymbols = null
        cachedClickedSymbolHistory = null
    }

    private fun setCandidateClick(
        candidate: Candidate, insertString: String, currentInputMode: InputMode, position: Int
    ) {
        Timber.d("setCandidateClick: $candidate")
        if (isSelectedTextGemmaActionCandidate(candidate) && handleSelectedTextGemmaActionClick(
                position
            )
        ) {
            return
        }
        if (handleBunsetsuCandidateClick(candidate, currentInputMode, position)) {
            applyCandidateCompleteActions(candidate)
            restoreKeyboardFromFullSuggestionViewIfNeeded()
            return
        }
        if (insertString.isEmpty() && isPostCommitPredictionActive) {
            vibrate()
            learnTransitionFromLastCommittedWord(candidate.string, candidate)
            commitText(candidate.string, 1)
            schedulePostCommitPrediction(candidate)
            resetFlagsSuggestionClick()
            return
        }
        if (insertString.isNotEmpty()) {
            isHenkan.set(false)
            henkanPressedWithBunsetsuDetect = false
            val qwertyGlideDecision = QwertyGlideCommitPolicy.resolveTapCommitDecision(
                candidate = candidate,
                insertString = insertString
            )
            if (qwertyGlideDecision is QwertyGlideTapCommitDecision.CommitQwertyGlideCandidate) {
                commitQwertyGlideCandidate(candidate)
                return
            }
            processCandidate(
                candidate = candidate,
                insertString = insertString,
                currentInputMode = currentInputMode,
                position = position
            )
            if (handlePromotedTailAfterCandidateCommit()) {
                return
            }
            applyCandidateCompleteActions(candidate)
        }
        resetFlagsSuggestionClick()
    }

    private fun handleBunsetsuCandidateClick(
        candidate: Candidate,
        currentInputMode: InputMode,
        position: Int
    ): Boolean {
        val session = bunsetsuConversionSession ?: return false
        if (!isBunsetsuCursorMoveSessionActive()) return false
        val mainView = mainLayoutBinding ?: return false

        val focusedIndex = session.focusedIndex.coerceIn(0, session.segments.lastIndex)
        val targetSegment = session.segments[focusedIndex]
        val candidateDisplayText = displayTextFromCandidate(candidate)
        val selectedIndex = targetSegment.candidates.indexOfFirst { it == candidate }

        if (shouldLearnTappedCandidate(currentInputMode, position, candidate)) {
            launchLearningMemoryCommit {
                learningMemoryRepository.commitTappedCandidate(
                    reading = targetSegment.reading,
                    candidate = candidate,
                    position = position,
                )
            }
        }

        val updatedSegments = session.segments.toMutableList()
        updatedSegments[focusedIndex] = targetSegment.copy(
            displayText = candidateDisplayText,
            selectedIndex = if (selectedIndex >= 0) selectedIndex else targetSegment.selectedIndex
        )
        bunsetsuConversionSession = session.copy(segments = updatedSegments)
        commitBunsetsuConversionUntilFocusedSegment(
            mainView = mainView,
            session = session.copy(segments = updatedSegments)
        )
        return true
    }

    private fun commitBunsetsuConversionUntilFocusedSegment(
        mainView: MainLayoutBinding,
        session: BunsetsuConversionSession
    ): Boolean {
        if (session.segments.isEmpty()) return false

        val focusedIndex = session.focusedIndex.coerceIn(0, session.segments.lastIndex)
        val committedText = session.segments
            .take(focusedIndex + 1)
            .joinToString(separator = "") { it.displayText }
        val remainingSegmentInput = session.segments
            .drop(focusedIndex + 1)
            .joinToString(separator = "") { it.reading }
        val sessionTailText = session.tailText
        val nextInput = if (remainingSegmentInput.isNotEmpty()) {
            remainingSegmentInput
        } else {
            sessionTailText
        }
        val nextTailText = if (remainingSegmentInput.isNotEmpty()) {
            sessionTailText
        } else {
            ""
        }

        if (nextInput.isEmpty()) {
            finalizeBunsetsuReconversion(
                originalReading = session.rawInput,
                committedText = committedText
            )
        } else {
            appendBunsetsuReconversionDraft(
                originalReading = session.rawInput,
                committedText = committedText
            )
            preserveBunsetsuReconversionDraftOnNextProcessInput = true
        }

        beginBatchEdit()
        try {
            setComposingText("", 0)
            finishComposingText()
            if (committedText.isNotEmpty()) {
                commitText(committedText, 1)
            }

            if (nextInput.isNotEmpty()) {
                stringInTail.set(nextTailText)
                val spannableString = SpannableString(nextInput + nextTailText)
                setComposingTextAfterEdit(
                    inputString = nextInput,
                    spannableString = spannableString,
                    backgroundColor = if (customComposingTextPreference == true) {
                        inputCompositionAfterBackgroundColor
                            ?: getColor(com.kazumaproject.core.R.color.blue)
                    } else {
                        getColor(com.kazumaproject.core.R.color.blue)
                    },
                    textColor = if (customComposingTextPreference == true) {
                        inputCompositionTextColor
                    } else {
                        null
                    }
                )
            }
        } finally {
            endBatchEdit()
        }

        isHenkan.set(false)
        henkanPressedWithBunsetsuDetect = false
        suggestionClickNum = 0
        englishSpaceKeyPressed.set(false)
        onDeleteLongPressUp.set(false)
        _dakutenPressed.value = false
        lastFlickConvertedNextHiragana.set(true)
        isContinuousTapInputEnabled.set(true)
        suggestionAdapter?.suggestions = emptyList()
        suggestionAdapterFull?.suggestions = emptyList()
        suggestionAdapter?.updateHighlightPosition(RecyclerView.NO_POSITION)
        if (physicalKeyboardEnable.replayCache.isNotEmpty() &&
            physicalKeyboardEnable.replayCache.first()
        ) {
            updateSuggestionsForFloatingCandidate(emptyList())
            currentHighlightIndex = RecyclerView.NO_POSITION
        }
        isFirstClickHasStringTail = false
        _inputString.update { nextInput }
        clearBunsetsuConversionSession()
        learnMultiple.stop()

        if (nextInput.isNotEmpty()) {
            launchProcessInputStringLatest(nextInput, mainView)
        } else {
            stringInTail.set("")
        }
        return true
    }


    private fun isEditHistoryEnabled(): Boolean {
        return appPreference.undo_enable_preference == true
    }

    private fun isDeleteHistoryRecordingEnabled(): Boolean {
        return appPreference.undo_enable_preference == true ||
                appPreference.delete_key_down_flick_preference == true
    }

    private fun isDeleteKeyDownFlickUndoEnabled(): Boolean {
        return appPreference.delete_key_down_flick_preference == true
    }

    private fun updateSideKeyPreviousDrawableForHistory() {
        val drawableRes = if (deletedBuffer.hasUndoHistory()) {
            com.kazumaproject.core.R.drawable.baseline_delete_24
        } else {
            com.kazumaproject.core.R.drawable.undo_24px
        }
        mainLayoutBinding?.keyboardView?.setSideKeyPreviousDrawable(
            ContextCompat.getDrawable(this, drawableRes)
        )
    }

    private fun refreshEditHistoryUi() {
        val hasUndoHistory = isEditHistoryEnabled() && deletedBuffer.hasUndoHistory()
        val hasRedoHistory = isEditHistoryEnabled() && deletedBuffer.hasRedoHistory()
        val undoLabel =
            if (hasUndoHistory) getString(com.kazumaproject.core.R.string.undo_action_label) else ""
        val redoLabel =
            if (hasRedoHistory) getString(com.kazumaproject.core.R.string.redo_action_label) else ""
        listOfNotNull(suggestionAdapter, suggestionAdapterFull).forEach { adapter ->
            adapter.setUndoPreviewText(undoLabel)
            adapter.setUndoEnabled(hasUndoHistory)
            adapter.setRedoPreviewText(redoLabel)
            adapter.setRedoEnabled(hasRedoHistory)
        }
        updateSideKeyPreviousDrawableForHistory()
        if (!hasUndoHistory && !hasRedoHistory) {
            updateClipboardPreview()
        }
        refreshReconversionUi()
    }

    private fun canPerformPendingReconversion(entry: ReconversionEntry): Boolean {
        if (entry.committedText.isEmpty() || entry.reading.isEmpty()) return false
        val textBeforeCursor = editorGateway
            .getTextBeforeCursor(entry.committedText.length, 0)
            ?.toString()
            .orEmpty()
        return textBeforeCursor.endsWith(entry.committedText)
    }

    private fun shouldShowReconversionButton(): Boolean {
        if (!reconversionEnabledPreference) return false
        if (inputString.value.isNotEmpty() || stringInTail.get().isNotEmpty()) return false
        if (isHenkan.get()) return false
        val entry = pendingReconversionEntry ?: return false
        return canPerformPendingReconversion(entry)
    }

    private fun refreshReconversionUi() {
        val isEnabled = shouldShowReconversionButton()
        listOfNotNull(suggestionAdapter, suggestionAdapterFull).forEach { adapter ->
            adapter.setReconvertEnabled(isEnabled)
        }
    }

    private fun clearPendingReconversionEntry() {
        pendingReconversionEntry = null
        refreshReconversionUi()
    }

    private fun clearBunsetsuReconversionDraft() {
        bunsetsuReconversionDraft = null
        preserveBunsetsuReconversionDraftOnNextProcessInput = false
    }

    private fun rememberCommittedTextForReconversion(
        reading: String,
        committedText: String
    ) {
        if (reading.isEmpty() || committedText.isEmpty()) return
        val draft = bunsetsuReconversionDraft
        pendingReconversionEntry = if (draft != null) {
            ReconversionEntry(
                committedText = draft.committedText + committedText,
                reading = draft.originalReading
            )
        } else {
            ReconversionEntry(
                committedText = committedText,
                reading = reading
            )
        }
        clearBunsetsuReconversionDraft()
        refreshReconversionUi()
    }

    private fun appendBunsetsuReconversionDraft(
        originalReading: String,
        committedText: String
    ) {
        if (originalReading.isEmpty() || committedText.isEmpty()) return
        val existing = bunsetsuReconversionDraft
        bunsetsuReconversionDraft = if (existing != null) {
            existing.copy(committedText = existing.committedText + committedText)
        } else {
            BunsetsuReconversionDraft(
                originalReading = originalReading,
                committedText = committedText
            )
        }
    }

    private fun finalizeBunsetsuReconversion(
        originalReading: String,
        committedText: String
    ) {
        if (committedText.isEmpty()) return
        val draft = bunsetsuReconversionDraft
        val entry = if (draft != null) {
            ReconversionEntry(
                committedText = draft.committedText + committedText,
                reading = draft.originalReading
            )
        } else {
            ReconversionEntry(
                committedText = committedText,
                reading = originalReading
            )
        }
        pendingReconversionEntry = entry
        clearBunsetsuReconversionDraft()
        refreshReconversionUi()
    }

    private fun restoreReadingToPreEdit(
        reading: String,
        mainView: MainLayoutBinding
    ) {
        if (reading.isEmpty()) return
        resetHistoryInteractionFlags()
        stringInTail.set("")
        _inputString.update { reading }
        val spannable = createSpannableWithTail(reading)
        setComposingTextPreEdit(
            inputString = reading,
            spannableString = spannable,
            backgroundColor = if (customComposingTextPreference == true) {
                inputCompositionBackgroundColor
                    ?: getColor(com.kazumaproject.core.R.color.char_in_edit_color)
            } else {
                getColor(com.kazumaproject.core.R.color.char_in_edit_color)
            },
            textColor = if (customComposingTextPreference == true) {
                inputCompositionTextColor
            } else {
                null
            }
        )
        isRestoringReconversionInput = true
        launchProcessInputStringLatest(reading, mainView)
    }

    private fun performPendingReconversion() {
        val entry = pendingReconversionEntry ?: return
        val mainView = mainLayoutBinding ?: return
        if (!canPerformPendingReconversion(entry)) {
            clearPendingReconversionEntry()
            return
        }

        var restored = false
        suppressedSelectionCleanupCount += 1
        beginBatchEdit()
        try {
            if (!deleteCommittedTextBeforeCursor(entry.committedText)) {
                return
            }
            restoreReadingToPreEdit(entry.reading, mainView)
            restored = true
        } finally {
            endBatchEdit()
        }

        if (restored) {
            clearPendingReconversionEntry()
        }
    }

    /**
     * 削除バッファをまるごとクリアしたいときに呼ぶ
     */
    private fun clearDeletedBuffer() {
        if (!isDeleteHistoryRecordingEnabled()) return
        deletedBuffer.clear()
        activeDeleteHistoryBatch = null
        updateSideKeyPreviousDrawableForHistory()
    }

    private fun clearDeletedBufferWithoutResetLayout() {
        if (!isDeleteHistoryRecordingEnabled()) return
        deletedBuffer.clear()
        activeDeleteHistoryBatch = null
    }

    private fun pushEditHistoryEntry(entry: EditHistoryEntry) {
        if (!isDeleteHistoryRecordingEnabled()) return
        deletedBuffer.push(entry)
        refreshEditHistoryUi()
    }

    private fun captureDeletedTextFromConnection(inputConnection: InputConnection?): String {
        val connection = inputConnection ?: return ""
        val selectedText = connection.getSelectedText(0)?.toString().orEmpty()
        if (selectedText.isNotEmpty()) {
            return selectedText
        }
        return getLastCharacterAsString(connection)
    }

    private fun removedSuffixFromComposition(beforeInput: String, afterInput: String): String {
        return if (beforeInput.startsWith(afterInput)) {
            beforeInput.substring(afterInput.length)
        } else {
            beforeInput
        }
    }

    private fun createCompositionHistoryEntry(
        beforeInput: String,
        beforeTail: String,
        afterInput: String,
        afterTail: String,
        previewText: String = removedSuffixFromComposition(beforeInput, afterInput)
    ): EditHistoryEntry.CompositionChange? {
        if (beforeInput == afterInput && beforeTail == afterTail) return null
        val normalizedPreview = previewText.ifEmpty {
            (beforeInput + beforeTail).ifEmpty { afterInput + afterTail }
        }
        return EditHistoryEntry.CompositionChange(
            beforeInput = beforeInput,
            beforeTail = beforeTail,
            afterInput = afterInput,
            afterTail = afterTail,
            previewText = normalizedPreview
        )
    }

    private fun resetHistoryInteractionFlags() {
        isHenkan.set(false)
        henkanPressedWithBunsetsuDetect = false
        suggestionClickNum = 0
        englishSpaceKeyPressed.set(false)
        onDeleteLongPressUp.set(false)
        _dakutenPressed.value = false
        lastFlickConvertedNextHiragana.set(true)
        isContinuousTapInputEnabled.set(true)
        suggestionAdapter?.updateHighlightPosition(RecyclerView.NO_POSITION)
        isFirstClickHasStringTail = false
        clearBunsetsuConversionSession()
        learnMultiple.stop()
    }

    private fun restoreCompositionState(input: String, tail: String) {
        beginBatchEdit()
        try {
            _inputString.update { input }
            stringInTail.set(tail)
            resetHistoryInteractionFlags()
            if (input.isEmpty() && tail.isEmpty()) {
                setComposingText("", 0)
                finishComposingText()
            } else {
                val spannableString = SpannableString(input + tail)
                setComposingTextAfterEdit(
                    inputString = input,
                    spannableString = spannableString,
                    backgroundColor = if (customComposingTextPreference == true) {
                        inputCompositionAfterBackgroundColor
                            ?: getColor(com.kazumaproject.core.R.color.blue)
                    } else {
                        getColor(com.kazumaproject.core.R.color.blue)
                    },
                    textColor = if (customComposingTextPreference == true) {
                        inputCompositionTextColor
                    } else {
                        null
                    }
                )
            }
        } finally {
            endBatchEdit()
        }
    }

    private fun deleteCommittedTextBeforeCursor(text: String): Boolean {
        if (editorGateway.connection() == null) return false
        if (text.isEmpty()) return false
        val textBeforeCursor = editorGateway.getTextBeforeCursor(text.length, 0)?.toString() ?: ""
        if (!textBeforeCursor.endsWith(text)) return false
        return editorGateway.deleteSurroundingText(text.length, 0)
    }

    private fun deleteCommittedTextAfterCursor(text: String): Boolean {
        if (editorGateway.connection() == null) return false
        if (text.isEmpty()) return false
        val textAfterCursor = editorGateway.getTextAfterCursor(text.length, 0)?.toString() ?: ""
        if (!textAfterCursor.startsWith(text)) return false
        return editorGateway.deleteSurroundingText(0, text.length)
    }

    private fun performUndo(entry: EditHistoryEntry): Boolean {
        return when (entry) {
            is EditHistoryEntry.DeleteCommittedText -> {
                when (entry.direction) {
                    DeleteDirection.BeforeCursor -> {
                        commitText(entry.deletedText, 1)
                    }

                    DeleteDirection.AfterCursor -> {
                        editorGateway.commitText(entry.deletedText, 0)
                    }
                }
            }

            is EditHistoryEntry.ReplaceCommittedText -> {
                if (!deleteCommittedTextBeforeCursor(entry.afterText)) {
                    false
                } else {
                    commitText(entry.beforeText, 1)
                }
            }

            is EditHistoryEntry.CompositionChange -> {
                restoreCompositionState(entry.beforeInput, entry.beforeTail)
                true
            }
        }
    }

    private fun performRedo(entry: EditHistoryEntry): Boolean {
        return when (entry) {
            is EditHistoryEntry.DeleteCommittedText -> {
                when (entry.direction) {
                    DeleteDirection.BeforeCursor -> {
                        deleteCommittedTextBeforeCursor(entry.deletedText)
                    }

                    DeleteDirection.AfterCursor -> {
                        deleteCommittedTextAfterCursor(entry.deletedText)
                    }
                }
            }

            is EditHistoryEntry.ReplaceCommittedText -> {
                if (!deleteCommittedTextBeforeCursor(entry.beforeText)) {
                    false
                } else {
                    commitText(entry.afterText, 1)
                }
            }

            is EditHistoryEntry.CompositionChange -> {
                restoreCompositionState(entry.afterInput, entry.afterTail)
                true
            }
        }
    }

    private fun undoLastHistoryEntry() {
        val entry = deletedBuffer.popUndo() ?: return
        if (performUndo(entry)) {
            deletedBuffer.pushRedo(entry)
        } else {
            deletedBuffer.pushUndoFromRedo(entry)
        }
        refreshEditHistoryUi()
    }

    private fun redoLastHistoryEntry() {
        val entry = deletedBuffer.popRedo() ?: return
        if (performRedo(entry)) {
            deletedBuffer.pushUndoFromRedo(entry)
        } else {
            deletedBuffer.pushRedo(entry)
        }
        refreshEditHistoryUi()
    }

    private fun undoAllHistoryEntries() {
        while (deletedBuffer.hasUndoHistory()) {
            val entry = deletedBuffer.popUndo() ?: break
            if (performUndo(entry)) {
                deletedBuffer.pushRedo(entry)
            } else {
                deletedBuffer.pushUndoFromRedo(entry)
                break
            }
        }
        refreshEditHistoryUi()
    }

    private fun redoAllHistoryEntries() {
        while (deletedBuffer.hasRedoHistory()) {
            val entry = deletedBuffer.popRedo() ?: break
            if (performRedo(entry)) {
                deletedBuffer.pushUndoFromRedo(entry)
            } else {
                deletedBuffer.pushRedo(entry)
                break
            }
        }
        refreshEditHistoryUi()
    }

    private fun handleExactLengthMatch(
        insertString: String,
        candidateString: String,
        candidate: Candidate,
        currentInputMode: InputMode,
        position: Int
    ) {
        if (!learnMultiple.enabled()) {
            learnMultiple.start()
            learnMultiple.setInput(insertString)
            learnMultiple.setWordToStringBuilder(candidateString)
            upsertLearnDictionaryWhenTapCandidate(
                currentInputMode = currentInputMode,
                insertString = insertString,
                candidate = candidate,
                position = position
            )
        } else {
            learnMultiple.setInput(learnMultiple.getInput() + insertString)
            learnMultiple.setWordToStringBuilder(candidateString)
            upsertLearnDictionaryMultipleTapCandidate(
                currentInputMode = currentInputMode,
                input = learnMultiple.getInput(),
                output = learnMultiple.getInputAndStringBuilder().second,
                candidate = candidate,
                insertString = insertString,
                position = position
            )
        }
        if (stringInTail.get().isNullOrEmpty()) {
            learnMultiple.stop()
        }
    }

    private fun commitAndClearInput(candidateString: String) {
        val reading = inputString.value
        if (reading.isNotEmpty() && stringInTail.get().isEmpty()) {
            rememberCommittedTextForReconversion(
                reading = reading,
                committedText = candidateString
            )
        }
        val committedCandidate = candidateCoordinator.committedCandidateForPostCommit(
            surface = candidateString,
            fallbackReading = reading.takeIf {
                it.isNotBlank() && stringInTail.get().isEmpty()
            },
        )
        learnTransitionFromLastCommittedWord(candidateString, committedCandidate)

        _inputString.update { "" }
        commitText(candidateString, 1)
        schedulePostCommitPrediction(committedCandidate)
    }

    private fun resolveCurrentHenkanCommitText(): String {
        bunsetsuConversionSession?.let { session ->
            val convertedText = session.segments.joinToString(separator = "") { it.displayText }
            return convertedText + session.tailText
        }

        val suggestions = suggestionAdapter?.suggestions.orEmpty()
        if (suggestions.isNotEmpty()) {
            val selectedIndex = if (suggestionClickNum <= 0) {
                0
            } else {
                (suggestionClickNum - 1).coerceAtMost(suggestions.lastIndex)
            }
            return getCandidateCommitString(suggestions[selectedIndex]) + stringInTail.get()
        }

        return inputString.value + stringInTail.get()
    }

    private fun commitCurrentHenkanForNewInput() {
        if (!isHenkan.get()) return

        val currentHenkanText = resolveCurrentHenkanCommitText()
        suppressedSelectionCleanupCount += 1

        beginBatchEdit()
        try {
            setComposingText("", 0)
            finishComposingText()
            if (currentHenkanText.isNotEmpty()) {
                commitText(currentHenkanText, 1)
            }
        } finally {
            endBatchEdit()
        }

        resetFlagsEnterKeyNotHenkan()
    }

    private fun shouldLearnTappedCandidate(
        currentInputMode: InputMode,
        position: Int,
        candidate: Candidate,
    ): Boolean {
        return AzooKeyCandidateLearningPolicy.shouldLearnTappedCandidate(
            AzooKeyCandidateLearningPolicyInput(
                isJapaneseMode = currentInputMode == InputMode.ModeJapanese,
                isLearnDictionaryMode = isLearnDictionaryMode == true,
                isPrivateMode = isPrivateMode,
                position = position,
                learnFirstCandidate = learnFirstCandidateDictionaryPreference == true,
                candidate = candidate,
            )
        )
    }

    private fun handlePartialOrExcessLength(
        insertString: String,
        candidate: Candidate,
        currentInputMode: InputMode,
        position: Int
    ) {
        val candidateLength = candidate.length.toInt()
        val candidateString = candidate.string
        if (insertString.length > candidateLength) {
            val tail = insertString.substring(candidateLength)
            if (shouldLearnTappedCandidate(currentInputMode, position, candidate)) {
                launchLearningMemoryCommit {
                    learningMemoryRepository.commitTappedCandidate(
                        reading = insertString.substring(0, candidateLength),
                        candidate = candidate,
                        position = position,
                    )
                }
            }
            commitPartialCandidateAndPromoteTail(candidateString, tail)
            return
        }
        commitAndClearInput(candidateString)
    }

    private fun commitPartialCandidateAndPromoteTail(candidateString: String, tail: String) {
        isPromotingTail = true
        suppressSelectionCleanupForInternalPreEditMove()
        beginBatchEdit()
        try {
            setComposingText("", 0)
            finishComposingText()
            commitText(candidateString, 1)
            if (tail.isNotEmpty()) {
                isHenkan.set(false)
                henkanPressedWithBunsetsuDetect = false
                suggestionClickNum = 0
                englishSpaceKeyPressed.set(false)
                onDeleteLongPressUp.set(false)
                _dakutenPressed.value = false
                lastFlickConvertedNextHiragana.set(true)
                isContinuousTapInputEnabled.set(true)
                restoreKeyboardFromFullSuggestionViewIfNeeded()
                suggestionAdapter?.updateHighlightPosition(androidx.recyclerview.widget.RecyclerView.NO_POSITION)
                suggestionAdapterFull?.updateHighlightPosition(androidx.recyclerview.widget.RecyclerView.NO_POSITION)
                isFirstClickHasStringTail = false
                clearBunsetsuConversionSession()
                if (isLiveConversionEnable == true) {
                    liveConversionManager.updateAfterFirstClauseCompletion()
                }

                _inputString.update { tail }
                stringInTail.set("")
                applyComposingText(
                    text = tail,
                    highlightLength = tail.length,
                    backgroundColor = if (customComposingTextPreference == true) {
                        inputConversionBackgroundColor
                            ?: getColor(com.kazumaproject.core.R.color.orange)
                    } else {
                        getColor(com.kazumaproject.core.R.color.orange)
                    },
                    textColor = if (customComposingTextPreference == true) {
                        inputConversionTextColor
                    } else {
                        null
                    }
                )
                promotedTailAfterCandidateCommit = true
            }
        } finally {
            endBatchEdit()
            isPromotingTail = false
        }
    }

    private fun upsertLearnDictionaryWhenTapCandidate(
        currentInputMode: InputMode, insertString: String, candidate: Candidate, position: Int
    ) {
        if (shouldLearnTappedCandidate(currentInputMode, position, candidate)) {
            launchLearningMemoryCommit {
                learningMemoryRepository.commitTappedCandidate(
                    reading = insertString,
                    candidate = candidate,
                    position = position,
                )
            }
        }
        // 2) 共通の後処理（入力クリア＋コミット）
        learnTransitionFromLastCommittedWord(candidate.string, candidate)
        val tail = stringInTail.get()
        if (tail.isNotEmpty()) {
            commitPartialCandidateAndPromoteTail(candidate.string, tail)
        } else {
            if (insertString.isNotEmpty() && stringInTail.get().isEmpty()) {
                rememberCommittedTextForReconversion(
                    reading = insertString,
                    committedText = candidate.string
                )
            }
            _inputString.update { "" }
            lastQwertyRomajiRawInput = null
            candidateCoordinator.resetConversionSession()
            commitText(candidate.string, 1)
            schedulePostCommitPrediction(candidate)
        }
    }

    private fun commitQwertyGlideCandidate(candidate: Candidate) {
        beginBatchEdit()
        try {
            setComposingText("", 0)
            finishComposingText()
            commitText(candidate.string, 1)
        } finally {
            endBatchEdit()
        }
        stringInTail.set("")
        currentQwertyGlideCompositionText = null
        resetFlagsSuggestionClick()
    }

    private fun processCandidate(
        candidate: Candidate, insertString: String, currentInputMode: InputMode, position: Int
    ) {
        Timber.d("processCandidate ${candidate.type.toInt()} ${insertString.length == candidate.length.toInt()}")
        val qwertyGlideDecision = QwertyGlideCommitPolicy.resolveTapCommitDecision(
            candidate = candidate,
            insertString = insertString
        )
        if (qwertyGlideDecision is QwertyGlideTapCommitDecision.CommitQwertyGlideCandidate) {
            commitQwertyGlideCandidate(candidate)
            return
        }
        when (candidate.type.toInt()) {
            15 -> {
                val readingCorrection = candidate.string.correctReading()
                commitAndClearInput(readingCorrection.first)
            }

            9,
            11,
            12,
            13,
            14,
            28,
            30,
            GemmaTranslationManager.TRANSLATED_CANDIDATE_TYPE,
            GemmaTranslationManager.PROMPT_RESULT_CANDIDATE_TYPE -> {
                commitAndClearInput(candidate.string)
            }

            else -> {
                if (insertString.length == candidate.length.toInt()) {
                    handleExactLengthMatch(
                        insertString = insertString,
                        candidateString = candidate.string,
                        candidate = candidate,
                        currentInputMode = currentInputMode,
                        position = position
                    )
                } else {
                    handlePartialOrExcessLength(
                        insertString = insertString,
                        candidate = candidate,
                        currentInputMode = currentInputMode,
                        position = position
                    )
                }
            }
        }
    }


    private fun upsertLearnDictionaryMultipleTapCandidate(
        currentInputMode: InputMode,
        input: String,
        output: String,
        candidate: Candidate,
        insertString: String,
        position: Int
    ) {
        if (shouldLearnTappedCandidate(currentInputMode, position, candidate)) {
            launchLearningMemoryCommit {
                learningMemoryRepository.commitTappedCandidate(
                    reading = insertString,
                    candidate = candidate,
                    position = position,
                )
            }
        }
        // 共通後処理
        learnTransitionFromLastCommittedWord(candidate.string, candidate)
        val tail = stringInTail.get()
        if (tail.isNotEmpty()) {
            commitPartialCandidateAndPromoteTail(candidate.string, tail)
        } else {
            if (insertString.isNotEmpty() && stringInTail.get().isEmpty()) {
                rememberCommittedTextForReconversion(
                    reading = insertString,
                    committedText = candidate.string
                )
            }
            _inputString.update { "" }
            commitText(candidate.string, 1)
            schedulePostCommitPrediction(candidate)
        }
    }

    private fun resetAllFlags() {
        Timber.d("onUpdate resetAllFlags called")
        liveConversionManager.stopComposition()
        customKeyboardRenderJob?.cancel()
        customKeyboardRenderJob = null
        clearFunctionKeyConversionSource()
        _inputString.update { "" }
        _tenKeyQWERTYMode.update { TenKeyQWERTYMode.Default }
        suggestionAdapter?.suggestions = emptyList()
        stringInTail.set("")
        suggestionClickNum = 0
        currentCustomKeyboardPosition = 0
        currentCustomKeyboardStableId = null
        filteredCandidateList = emptyList()
        isHenkan.set(false)
        henkanPressedWithBunsetsuDetect = false
        isContinuousTapInputEnabled.set(false)
        leftCursorKeyLongKeyPressed.set(false)
        rightCursorKeyLongKeyPressed.set(false)
        _dakutenPressed.value = false
        englishSpaceKeyPressed.set(false)
        lastFlickConvertedNextHiragana.set(false)
        onDeleteLongPressUp.set(false)
        isSpaceKeyLongPressed = false
        onKeyboardSwitchLongPressUp = false
        suggestionAdapter?.updateHighlightPosition(RecyclerView.NO_POSITION)
        isFirstClickHasStringTail = false
        resetKeyboard()
        lastCandidate = ""
        _keyboardSymbolViewState.update { SymbolKeyboardState() }
        learnMultiple.stop()
        stopDeleteLongPress()
        clearDeletedBuffer()
        refreshEditHistoryUi()
        _selectMode.update { false }
        hasConvertedKatakana = false
        romajiConverter?.clear()
        hardKeyboardShiftPressd = false
        resetSumireKeyboardDakutenMode()
        initialCursorDetectInFloatingCandidateView = false
        initialCursorXPosition = 0
        countToggleKatakana = 0
        currentEnterKeyIndex = 0
        currentSpaceKeyIndex = 0
        currentKatakanaKeyIndex = 0
        currentDakutenKeyIndex = 0
        clearPendingReconversionEntry()
        clearBunsetsuReconversionDraft()
        bunsetsuPositionList = emptyList()
        bunsetsuSplitPatterns = emptyList()
        clearBunsetsuConversionSession()
        henkanPressedWithBunsetsuDetect = false
        bunsetusMultipleDetect = false
    }

    private fun actionInDestroy() {
        mainLayoutBinding?.suggestionRecyclerView?.apply {
            layoutManager = null
            adapter = null
        }
        releaseFloatingKeyboardBackgroundVideoPlayer()
        mainLayoutBinding = null
        floatingKeyboardBinding = null
        isFloatingQwertyConfigured = false
        closeConnection()
        scope.cancel()
        ioScope.cancel()
    }

    private fun resetFlagsSuggestionClick() {
        isHenkan.set(false)
        henkanPressedWithBunsetsuDetect = false
        suggestionClickNum = 0
        englishSpaceKeyPressed.set(false)
        onDeleteLongPressUp.set(false)
        _dakutenPressed.value = false
        lastFlickConvertedNextHiragana.set(true)
        isContinuousTapInputEnabled.set(true)
        restoreKeyboardFromFullSuggestionViewIfNeeded()
        suggestionAdapter?.updateHighlightPosition(RecyclerView.NO_POSITION)
        suggestionAdapterFull?.updateHighlightPosition(RecyclerView.NO_POSITION)
        isFirstClickHasStringTail = false
        clearBunsetsuConversionSession()
        if (stringInTail.get().isEmpty()) {
            clearSuggestionStateAfterCommit()
        }
        liveConversionManager.stopComposition()
        _inputString.update { "" }
        refreshReconversionUi()
    }

    private fun restoreKeyboardFromFullSuggestionViewIfNeeded() {
        _suggestionViewStatus.update { true }
        mainLayoutBinding?.let { mainView ->
            mainView.root.post {
                updateSuggestionViewVisibility(mainView, true)
            }
        }
    }

    private fun resetFlagsEnterKey() {
        isHenkan.set(false)
        henkanPressedWithBunsetsuDetect = false
        suggestionClickNum = 0
        englishSpaceKeyPressed.set(false)
        onDeleteLongPressUp.set(false)
        _dakutenPressed.value = false
        lastFlickConvertedNextHiragana.set(true)
        isContinuousTapInputEnabled.set(true)
        suggestionAdapter?.updateHighlightPosition(RecyclerView.NO_POSITION)
        isFirstClickHasStringTail = false
        clearBunsetsuConversionSession()
        _inputString.update { "" }
        refreshReconversionUi()
    }

    private fun resetFlagsEnterKeyNotHenkan() {
        isHenkan.set(false)
        henkanPressedWithBunsetsuDetect = false
        suggestionClickNum = 0
        englishSpaceKeyPressed.set(false)
        onDeleteLongPressUp.set(false)
        _dakutenPressed.value = false
        lastFlickConvertedNextHiragana.set(true)
        isContinuousTapInputEnabled.set(true)
        _inputString.update { "" }
        stringInTail.set("")
        suggestionAdapter?.updateHighlightPosition(RecyclerView.NO_POSITION)
        isFirstClickHasStringTail = false
        clearBunsetsuConversionSession()
        learnMultiple.stop()
        refreshReconversionUi()
    }

    private fun resetFlagsKeySpace() {
        onDeleteLongPressUp.set(false)
        _dakutenPressed.value = false
        isContinuousTapInputEnabled.set(false)
        lastFlickConvertedNextHiragana.set(false)
        englishSpaceKeyPressed.set(false)
    }

    private fun resetFlagsDeleteKey() {
        suggestionClickNum = 0
        _dakutenPressed.value = false
        englishSpaceKeyPressed.set(false)
        onDeleteLongPressUp.set(false)
        isHenkan.set(false)
        henkanPressedWithBunsetsuDetect = false
        lastFlickConvertedNextHiragana.set(true)
        isContinuousTapInputEnabled.set(true)
        suggestionAdapter?.updateHighlightPosition(RecyclerView.NO_POSITION)
        isFirstClickHasStringTail = false
        clearBunsetsuConversionSession()
    }

    /**
     * 編集前（PreEdit）のテキスト装飾を設定する
     * * @param backgroundColor 背景色 (Color Int)
     * @param textColor テキスト色 (Color Int, nullの場合は適用しない)
     */
    private fun setComposingTextPreEdit(
        inputString: String,
        spannableString: SpannableString,
        @ColorInt backgroundColor: Int,
        @ColorInt textColor: Int? = null,
    ) {
        Timber.d("launchInputString: setComposingTextPreEdit $spannableString")
        editorGateway.setComposingTextPreEdit(
            inputString = inputString,
            spannableString = spannableString,
            tailLength = stringInTail.get().length,
            backgroundColor = backgroundColor,
            textColor = textColor,
        )
    }

    /**
     * 編集後（AfterEdit）のテキスト装飾を設定する
     */
    private fun setComposingTextAfterEdit(
        inputString: String,
        spannableString: SpannableString,
        @ColorInt backgroundColor: Int,
        @ColorInt textColor: Int? = null,
    ) {
        editorGateway.setComposingTextAfterEdit(
            inputString = inputString,
            spannableString = spannableString,
            tailLength = stringInTail.get().length,
            backgroundColor = backgroundColor,
            textColor = textColor,
        )
    }

    private fun setEnterKeyAction(
        suggestions: List<Candidate>, currentInputMode: InputMode, insertString: String
    ) {
        Timber.d("setEnterKeyAction: $insertString ${stringInTail.get()} $bunsetsuPositionList $henkanPressedWithBunsetsuDetect")
        promotedTailAfterCandidateCommit = false
        val index = (suggestionClickNum - 1).coerceAtLeast(0)
        val nextSuggestion = suggestions[index]
        processCandidate(
            candidate = nextSuggestion,
            insertString = insertString,
            currentInputMode = currentInputMode,
            position = index
        )
        if (handlePromotedTailAfterCandidateCommit()) {
            return
        }
        clearSuggestionStateAfterCommit()
        resetFlagsEnterKey()
    }

    private fun handlePromotedTailAfterCandidateCommit(): Boolean {
        if (!promotedTailAfterCandidateCommit) return false
        promotedTailAfterCandidateCommit = false
        isHenkan.set(false)
        henkanPressedWithBunsetsuDetect = false
        suggestionClickNum = 0
        suggestionAdapter?.updateHighlightPosition(RecyclerView.NO_POSITION)
        suggestionAdapterFull?.updateHighlightPosition(RecyclerView.NO_POSITION)
        isFirstClickHasStringTail = false
        clearBunsetsuConversionSession()
        scope.launch {
            _suggestionFlag.emit(CandidateShowFlag.Updating)
        }
        mainLayoutBinding?.let { updateUpperAreaVisibility(it) }
        refreshReconversionUi()
        return true
    }

    private fun setTenkeyIconsInHenkan(insertString: String, mainView: MainLayoutBinding) {
        if (isTabletGojuonSurface()) {
            mainView.tabletView.apply {
                when (currentInputMode.get()) {
                    is InputMode.ModeJapanese -> {
                        setSideKeySpaceDrawable(
                            cachedSpaceDrawable
                        )
                        setSideKeyPreviousState(true)
                    }

                    is InputMode.ModeEnglish -> {
                        setSideKeySpaceDrawable(
                            cachedSpaceDrawable
                        )
                        setSideKeyPreviousState(false)
                    }

                    is InputMode.ModeNumber -> {
                        setSideKeyPreviousState(true)
                    }
                }
            }
        } else {
            mainView.keyboardView.apply {
                when (currentInputMode.value) {
                    is InputMode.ModeJapanese -> {
                        setSideKeySpaceDrawable(
                            cachedSpaceDrawable
                        )
                        setSideKeyPreviousState(true)
                        if (insertString.isNotEmpty()) {
                            if (insertString.isNotEmpty() && insertString.last()
                                    .isLatinAlphabet()
                            ) {
                                setBackgroundSmallLetterKey(
                                    cachedEnglishDrawable
                                )
                            } else {
                                setBackgroundSmallLetterKey(
                                    isLanguageEnable = tenkeyShowIMEButtonPreference ?: true,
                                    isEnglish = false
                                )
                            }
                        } else {
                            setBackgroundSmallLetterKey(
                                isLanguageEnable = tenkeyShowIMEButtonPreference ?: true,
                                isEnglish = false
                            )
                        }
                    }

                    is InputMode.ModeEnglish -> {
                        setSideKeySpaceDrawable(
                            cachedSpaceDrawable
                        )
                        setBackgroundSmallLetterKey(
                            cachedNumberDrawable
                        )
                        setSideKeyPreviousState(false)
                    }

                    is InputMode.ModeNumber -> {
                        setSideKeyPreviousState(true)
                        if (insertString.isNotEmpty()) {
                            setSideKeySpaceDrawable(
                                cachedHenkanDrawable
                            )
                            if (insertString.last().isHiragana()) {
                                setBackgroundSmallLetterKey(
                                    cachedKanaDrawable
                                )
                            } else {
                                setBackgroundSmallLetterKey(
                                    isLanguageEnable = tenkeyShowIMEButtonPreference ?: true,
                                    isEnglish = false
                                )
                            }
                        } else {
                            setSideKeySpaceDrawable(
                                cachedSpaceDrawable
                            )
                            setBackgroundSmallLetterKey(
                                isLanguageEnable = tenkeyShowIMEButtonPreference ?: true,
                                isEnglish = false
                            )
                        }
                    }
                }
            }
        }
    }

    private fun setTenkeyIconsInHenkanFloating(
        insertString: String, floatingKeyboardLayoutBinding: FloatingKeyboardLayoutBinding
    ) {
        floatingKeyboardLayoutBinding.keyboardViewFloating.apply {
            Timber.d("setTenkeyIconsInHenkanFloating: ${currentInputMode.value}")
            when (currentInputMode.value) {
                is InputMode.ModeJapanese -> {
                    setSideKeySpaceDrawable(
                        cachedSpaceDrawable
                    )
                    setSideKeyPreviousState(true)
                    if (insertString.isNotEmpty()) {
                        if (insertString.isNotEmpty() && insertString.last().isLatinAlphabet()) {
                            setBackgroundSmallLetterKey(
                                cachedEnglishDrawable
                            )
                        } else {
                            setBackgroundSmallLetterKey(
                                isLanguageEnable = tenkeyShowIMEButtonPreference ?: true,
                                isEnglish = false
                            )
                        }
                    } else {
                        setBackgroundSmallLetterKey(
                            isLanguageEnable = tenkeyShowIMEButtonPreference ?: true,
                            isEnglish = false
                        )
                    }
                }

                is InputMode.ModeEnglish -> {
                    setSideKeySpaceDrawable(
                        cachedSpaceDrawable
                    )
                    setBackgroundSmallLetterKey(
                        cachedNumberDrawable
                    )
                    setSideKeyPreviousState(false)
                }

                is InputMode.ModeNumber -> {
                    setSideKeyPreviousState(true)
                    if (insertString.isNotEmpty()) {
                        setSideKeySpaceDrawable(
                            cachedHenkanDrawable
                        )
                        if (insertString.last().isHiragana()) {
                            setBackgroundSmallLetterKey(
                                cachedKanaDrawable
                            )
                        } else {
                            setBackgroundSmallLetterKey(
                                isLanguageEnable = tenkeyShowIMEButtonPreference ?: true,
                                isEnglish = false
                            )
                        }
                    } else {
                        setSideKeySpaceDrawable(
                            cachedSpaceDrawable
                        )
                        setBackgroundSmallLetterKey(
                            isLanguageEnable = tenkeyShowIMEButtonPreference ?: true,
                            isEnglish = false
                        )
                    }
                }
            }
        }
    }

    private fun henkanDrawableSet(): KeyboardSurfaceCoordinator.HenkanDrawableSet {
        return KeyboardSurfaceCoordinator.HenkanDrawableSet(
            returnDrawable = cachedReturnDrawable,
            henkanDrawable = cachedHenkanDrawable,
            spaceDrawable = cachedSpaceDrawable,
            kanaDrawable = cachedKanaDrawable,
            englishDrawable = cachedEnglishDrawable,
            numberDrawable = cachedNumberDrawable,
            tenkeyShowImeButton = tenkeyShowIMEButtonPreference ?: true,
        )
    }

    private fun floatingKeyboardSurfaceInputMode(
        floatingBinding: FloatingKeyboardLayoutBinding,
    ): InputMode {
        return floatingBinding.keyboardViewFloating.currentInputMode.value
    }

    private fun updateHenkanUi(
        mainView: MainLayoutBinding,
        floatingBinding: FloatingKeyboardLayoutBinding?,
        insertString: String,
    ) {
        val drawables = henkanDrawableSet()
        keyboardSurfaceCoordinator.updateHenkanOnMain(
            mainView = mainView,
            insertString = insertString,
            currentInputMode = currentTenkeyInputMode(mainView),
            drawables = drawables,
            useTabletGojuonSurface = isTabletGojuonSurface(),
        )
        floatingBinding?.let {
            keyboardSurfaceCoordinator.updateHenkanOnFloating(
                floatingBinding = it,
                insertString = insertString,
                currentInputMode = floatingKeyboardSurfaceInputMode(it),
                drawables = drawables,
            )
        }
    }

    private fun updateUIinHenkan(mainView: MainLayoutBinding, insertString: String) {
        updateHenkanUi(mainView, floatingKeyboardBinding, insertString)
    }

    private fun updateUIinHenkanFloating(
        floatingKeyboardLayoutBinding: FloatingKeyboardLayoutBinding,
        insertString: String,
    ) {
        mainLayoutBinding?.let { mainView ->
            updateHenkanUi(mainView, floatingKeyboardLayoutBinding, insertString)
        } ?: keyboardSurfaceCoordinator.updateHenkanOnFloating(
            floatingBinding = floatingKeyboardLayoutBinding,
            insertString = insertString,
            currentInputMode = floatingKeyboardSurfaceInputMode(floatingKeyboardLayoutBinding),
            drawables = henkanDrawableSet(),
        )
    }

    private suspend fun setSuggestionOnView(
        inputString: String, mainView: MainLayoutBinding
    ) {
        Timber.d("setSuggestionOnView: tabPosition first: $inputString $suggestionClickNum")
        if (inputString.isEmpty() || suggestionClickNum > 0) return
        val tabPosition = mainView.candidateTabLayout.selectedTabPosition
        Timber.d("setSuggestionOnView: tabPosition: $tabPosition $bunsetsuPositionList")
        if (candidateTabVisibility == true) {
            if (candidateTabOrder.isNotEmpty() && tabPosition < candidateTabOrder.size) {
                when (candidateTabOrder[tabPosition]) {
                    CandidateTab.PREDICTION -> {
                        setCandidates(inputString, mainView)
                    }

                    CandidateTab.CONVERSION -> {
                        setCandidatesWithoutPrediction(inputString, mainView)
                    }

                    CandidateTab.EISUKANA -> {
                        setCandidatesEnglishKana(inputString)
                    }
                }
            } else {
                setCandidates(inputString, mainView)
            }
        } else if (currentInputModeForSession == InputMode.ModeEnglish) {
            setCandidatesEnglishDefault(inputString, mainView)
        } else {
            setCandidatesOriginal(inputString, mainView)
        }
        Timber.d("setSuggestionOnView auto: $inputString $stringInTail $tabPosition $bunsetsuPositionList ${isHenkan.get()} $henkanPressedWithBunsetsuDetect $bunsetusMultipleDetect")
        updateUpperAreaVisibility(mainView)
    }

    private suspend fun setCandidates(
        insertString: String, mainView: MainLayoutBinding
    ) {
        val requestToken = beginZenzRerankRequest()
        emitAsyncZenzRequestsIfNeeded(insertString)
        val result = requestSuggestionResult(insertString, CandidateRequestMode.Normal)
        applySuggestionResultToView(
            insertString = insertString,
            mainView = mainView,
            result = result,
            requestToken = requestToken,
            notifyAsyncZenz = false,
        )
    }

    private suspend fun setCandidatesOriginal(
        insertString: String, mainView: MainLayoutBinding
    ) {
        val requestToken = beginZenzRerankRequest()
        emitAsyncZenzRequestsIfNeeded(insertString)
        val result = requestSuggestionResult(insertString, CandidateRequestMode.Original)
        applySuggestionResultToView(
            insertString = insertString,
            mainView = mainView,
            result = result,
            requestToken = requestToken,
            notifyAsyncZenz = false,
        )
    }

    private suspend fun setCandidatesWithoutPrediction(
        insertString: String, mainView: MainLayoutBinding
    ) {
        val requestToken = beginZenzRerankRequest()
        val candidates = getSuggestionListWithoutPrediction(insertString)
        applySuggestionResultToView(
            insertString = insertString,
            mainView = mainView,
            result = com.kazumaproject.markdownhelperkeyboard.ime_service.candidate.ImeCandidateSuggestResult(
                candidates = candidates,
                bunsetsuResult = null,
            ),
            requestToken = requestToken,
            notifyAsyncZenz = false,
        )
    }

    private suspend fun setCandidatesEnglishKana(
        insertString: String,
    ) {
        val requestToken = beginZenzRerankRequest()
        val candidates = getSuggestionListEnglishKana(insertString)
        mainLayoutBinding?.let { mainView ->
            applySuggestionResultToView(
                insertString = insertString,
                mainView = mainView,
                result = com.kazumaproject.markdownhelperkeyboard.ime_service.candidate.ImeCandidateSuggestResult(
                    candidates = candidates,
                    bunsetsuResult = null,
                ),
                requestToken = requestToken,
                notifyAsyncZenz = false,
            )
        }
    }

    private suspend fun setCandidatesEnglishDefault(
        insertString: String,
        mainView: MainLayoutBinding,
    ) {
        val requestToken = beginZenzRerankRequest()
        val englishKana = getSuggestionListEnglishKana(insertString)
        // 英字入力では Zenz / リッチ候補の変換パイプラインを走らせず、英語候補のみ表示する。
        val merged = englishKana.distinctBy { candidate -> candidate.string to candidate.type }
        applySuggestionResultToView(
            insertString = insertString,
            mainView = mainView,
            result = com.kazumaproject.markdownhelperkeyboard.ime_service.candidate.ImeCandidateSuggestResult(
                candidates = merged,
                bunsetsuResult = null,
            ),
            requestToken = requestToken,
            notifyAsyncZenz = false,
        )
    }


    private fun buildImeCandidatePreferences(): ImeCandidatePreferences {
        val snapshot = cachedPreferences
        if (snapshot == null) {
            return legacyImeCandidatePreferencesFallback()
        }
        val ngWords =
            if (snapshot.isNgWordEnable) cachedNgWordsStringList else emptyList()
        val insertLength = inputString.value.length
        val leftContext = cachedCandidateLeftContext.ifEmpty {
            truncateZenzLeftContext(getLeftContext(inputLength = 0))
        }
        val rightContext = if (snapshot.enableZenzRightContextPreference) {
            getRightContext(inputLength = insertLength).take(
                com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyConversionDefaults.ZENZ_RIGHT_CONTEXT_MAX,
            )
        } else {
            ""
        }
        return ImeCandidatePreferencesBuilder.build(
            snapshot = snapshot,
            runtime = currentImeCandidateRuntimeSession(ngWords),
            appPreference = appPreference,
            ngWords = ngWords,
            ngWordPattern = ngPattern.value,
            romanize = { input -> romajiConverter?.hiraganaToRomaji(input) },
            toHankakuAlphabet = { input -> input.toHankakuAlphabet() },
            zenzaiEnabled = snapshot.zenzaiEnableStatePreference,
            zenzProfile = snapshot.zenzProfilePreference ?: (zenzProfilePreference ?: ""),
            zenzLeftSideContext = leftContext,
            zenzRightSideContext = rightContext,
            zenzModelIdentity = com.kazumaproject.markdownhelperkeyboard.converter.zenz.ZenzModelIdentity.currentModelPath,
        )
    }

    private fun currentImeCandidateRuntimeSession(
        ngWords: List<String>,
    ): ImeCandidateRuntimeSession {
        val session = currentImeSessionSnapshot()
        return ImeCandidateRuntimeSession(
            isPrivateMode = isPrivateMode,
            suppressSuggestions = session.suppressSuggestions,
            isCandidateSelectionActive = session.selectModeActive || suggestionClickNum > 0,
            isConverting = session.isHenkan,
            isDirectInputMode = false,
            qwertyMode = qwertyMode.value,
            currentQwertyRomajiMode = currentQwertyRomajiModeForSession,
            onNormalBunsetsuResult = { request, result ->
                Timber.d(
                    "handleJapaneseModeSpaceKeyWithBunsetsu: ${result.primarySplitPositions} ${isHenkan.get()} $ngWords ${request.input} ${result.splitPatterns}"
                )
            },
        )
    }

    /** [cachedPreferences] 未適用時の最小フォールバック（通常は onStartInput 後に到達しない） */
    private fun legacyImeCandidatePreferencesFallback(): ImeCandidatePreferences {
        return ImeCandidatePreferences(
            nBest = (nBest ?: 4).coerceAtLeast(24),
            useUserDictionary = isUserDictionaryEnable == true,
            useUserTemplate = isUserTemplateEnable == true,
            useRomajiCandidates = conversionCandidatesRomajiEnablePreference == true,
            useBunsetsu = bunsetsuSeparation == true,
            useOmissionSearch = isOmissionSearchEnable ?: false,
            learningType = legacyAzooKeyLearningType(),
            zenzaiMode = if (zenzaiEnableStatePreference == true) {
                AzooKeyStyleZenzaiMode.On
            } else {
                AzooKeyStyleZenzaiMode.Off
            },
            liveConversionMode = if (isLiveConversionEnable == true) {
                AzooKeyLiveConversionMode.Enabled
            } else {
                AzooKeyLiveConversionMode.Disabled
            },
            privacy = currentCandidateRequestPrivacy(),
            versionString = "JapaneseKeyboard Version ${BuildConfig.VERSION_NAME}",
            learnedPrefixMatchThreshold = (learnPredictionPreference ?: 2) - 1,
            userDictionaryPrefixMatchThreshold = (userDictionaryPrefixMatchNumber ?: 2) - 1,
            isLearnDictionaryMode = isLearnDictionaryMode == true,
            romanize = { input -> romajiConverter?.hiraganaToRomaji(input) },
            toHankakuAlphabet = { input -> input.toHankakuAlphabet() },
            onNormalBunsetsuResult = { _, _ -> },
            isNgWordFilterEnabled = isNgWordEnable == true,
            ngWords = emptyList(),
            ngWordPattern = ngPattern.value,
            isOrderOverrideEnabled = false,
            isCandidateSelectionActive = selectMode.value || suggestionClickNum > 0,
            isConverting = isHenkan.get(),
            isDirectInputMode = false,
            experimentalZenzaiPredictiveInput = zenzaiEnableStatePreference == true &&
                appPreference.experimental_zenzai_predictive_input_preference,
            zenzLeftSideContext = truncateZenzLeftContext(getLeftContext(inputLength = 0)),
            zenzModelIdentity = com.kazumaproject.markdownhelperkeyboard.converter.zenz.ZenzModelIdentity.currentModelPath,
        )
    }

    private fun shouldUseQwertyRoman2KanaComposing(): Boolean {
        if (romajiConverter == null) return false
        if (currentInputModeForSession != InputMode.ModeJapanese) return false
        return when (qwertyMode.value) {
            TenKeyQWERTYMode.TenKeyQWERTYRomaji -> currentQwertyRomajiModeForSession
            TenKeyQWERTYMode.Default,
            TenKeyQWERTYMode.Sumire -> hasHardwareKeyboardConnected == true
            TenKeyQWERTYMode.TenKeyQWERTY,
            TenKeyQWERTYMode.Custom,
            TenKeyQWERTYMode.Number -> false
        }
    }

    private fun syncComposingTextSession(displayInput: String) {
        suggestionOrchestrator.syncComposingSession(
            displayInput = displayInput,
            useQwertyRoman2Kana = shouldUseQwertyRoman2KanaComposing(),
            roman2Kana = azooKeyRoman2KanaTransducer,
            romajiSnapshot = romajiConverter?.composingSnapshot(),
            qwertyRomajiRawInput = lastQwertyRomajiRawInput,
            zenkakuRomaji = isDefaultRomajiHenkanMap,
        )
    }

    private fun composingTextForCandidateRequest(displayInput: String): com.kazumaproject.markdownhelperkeyboard.converter.api.ComposingText {
        val composingText = suggestionOrchestrator.composingTextForCandidateRequest(
            displayInput = displayInput,
            useQwertyRoman2Kana = shouldUseQwertyRoman2KanaComposing(),
            roman2Kana = azooKeyRoman2KanaTransducer,
            romajiSnapshot = romajiConverter?.composingSnapshot(),
            qwertyRomajiRawInput = lastQwertyRomajiRawInput,
            zenkakuRomaji = isDefaultRomajiHenkanMap,
        )
        val tail = stringInTail.get()
        if (tail.isEmpty()) {
            return composingText
        }
        val fullDisplayInput = displayInput + tail
        if (fullDisplayInput == composingText.convertTarget) {
            return composingText.copy(
                convertTargetCursorPosition = displayInput.length.coerceIn(0, fullDisplayInput.length),
            )
        }
        return com.kazumaproject.markdownhelperkeyboard.converter.api.ComposingText
            .fromConvertTarget(fullDisplayInput)
            .copy(
                convertTargetCursorPosition = displayInput.length.coerceIn(0, fullDisplayInput.length),
            )
    }

    private fun updateInputStringFromQwertyRomajiBuffer(
        rawBuffer: String,
        converted: String,
    ) {
        lastQwertyRomajiRawInput = rawBuffer
        if (routeSymbolPanelSearchFullText(converted)) return
        _inputString.update { converted }
    }

    private suspend fun requestSuggestionResult(
        insertString: String,
        mode: CandidateRequestMode,
    ): com.kazumaproject.markdownhelperkeyboard.ime_service.candidate.ImeCandidateSuggestResult {
        val startedAt = SystemClock.elapsedRealtime()
        syncZenzLeftContextFromEditor()
        val composingText = composingTextForCandidateRequest(insertString)
        val result = suggestionOrchestrator.requestSuggestionResult(
            insertString = insertString,
            mode = mode,
            preferences = buildImeCandidatePreferences(),
            zenz = buildImeCandidateZenzContext(insertString),
            composingText = composingText,
            roman2Kana = azooKeyRoman2KanaTransducer,
            onBunsetsuMerged = candidatePresentationCoordinator::mergeBunsetsuAfterCandidateRequest,
        )
        Timber.d(
            "ImeLatency candidate_request input_len=${insertString.length} mode=$mode candidates=${result.candidates.size} elapsed_ms=${SystemClock.elapsedRealtime() - startedAt}"
        )
        return result
    }

    private suspend fun buildImeCandidateZenzContext(
        insertString: String,
    ): com.kazumaproject.markdownhelperkeyboard.ime_service.candidate.ImeCandidateZenzContext {
        val hardwareKeyboard = hasHardwareKeyboardConnected == true
        val leftContext = cachedCandidateLeftContext.ifEmpty {
            truncateZenzLeftContext(getLeftContext(inputLength = 0))
        }
        zenzContextCacheInput?.let { cachedInput ->
            if (cachedInput == insertString &&
                zenzContextCacheHardwareKeyboard == hardwareKeyboard &&
                zenzContextCacheLeftContext == leftContext
            ) {
                zenzContextCache?.let { return it }
            }
        }
        val snapshot = cachedPreferences
        val built = ImeZenzContextBuilder.build(
            snapshot = snapshot,
            policy = currentRuntimeConversionPolicy(insertString),
            config = currentZenzConversionConfig(snapshot),
            leftContext = leftContext,
            hasHardwareKeyboard = hardwareKeyboard,
            fallbackZenzEnabled = zenzEnableStatePreference == true,
            fallbackZenzRerankEnabled = zenzRerankPreference == true,
            fallbackNBest = nBest ?: 4,
        )
        zenzContextCacheInput = insertString
        zenzContextCacheHardwareKeyboard = hardwareKeyboard
        zenzContextCacheLeftContext = leftContext
        zenzContextCache = built
        return built
    }

    private suspend fun emitAsyncZenzRequestsIfNeeded(insertString: String) {
        val fullReading = inputString.value + stringInTail.get()
        val cursorPosition = inputString.value.length
        val zenz = buildImeCandidateZenzContext(fullReading)
        val policy = currentRuntimeConversionPolicy(fullReading)
        if (zenz.shouldEmitAsyncGeneration(fullReading, policy) ||
            zenz.shouldEmitAsyncZenzai(fullReading, policy)
        ) {
            scope.launch {
                _zenzRequest.emit(ZenzRequestParams(fullReading, cursorPosition))
            }
        }
    }

    private suspend fun applySuggestionResultToView(
        insertString: String,
        mainView: MainLayoutBinding,
        result: com.kazumaproject.markdownhelperkeyboard.ime_service.candidate.ImeCandidateSuggestResult,
        requestToken: Long,
        notifyAsyncZenz: Boolean = true,
    ) {
        val startedAt = SystemClock.elapsedRealtime()
        Timber.d("setCandidates called: $bunsetusMultipleDetect $bunsetsuPositionList i:[$insertString] s:[$stringInTail]")
        candidatePresentationCoordinator.applySuggestionResultToView(
            insertString = insertString,
            mainView = mainView,
            result = result,
            requestToken = requestToken,
            notifyAsyncZenz = notifyAsyncZenz,
        )
        Timber.d(
            "ImeLatency candidate_apply input_len=${insertString.length} token=$requestToken elapsed_ms=${SystemClock.elapsedRealtime() - startedAt}"
        )
    }

    private suspend fun applyLiveConversionIfNeeded(
        insertString: String,
        displayedCandidates: List<Candidate>,
        firstClauseResults: List<Candidate> = emptyList(),
    ) {
        if (shouldStartLiveConversion(insertString)) {
            if (!shouldApplyCandidateResult(insertString)) {
                return
            }
            val liveText = liveConversionManager.updateWithNewResults(
                composingText = composingTextForCandidateRequest(insertString),
                candidates = displayedCandidates,
                firstClauseResults = firstClauseResults,
                convertTargetCursorPosition = insertString.length,
                convertTarget = insertString
            )

            val threshold = getAutomaticCompletionStrengthThreshold(liveConversionAutomaticCompletionStrength)
            val autoCompletedClause = liveConversionManager.candidateForCompleteFirstClause(threshold)
            val mainView = mainLayoutBinding
            if (autoCompletedClause != null && mainView != null && stringInTail.get().isEmpty()) {
                val firstClauseReadingLength = autoCompletedClause.data.sumOf { it.reading.length }
                    .coerceAtMost(insertString.length)
                val remainingKana = insertString.drop(firstClauseReadingLength)
                candidateCoordinator.setCompletedData(autoCompletedClause)

                if (shouldUseQwertyRoman2KanaComposing()) {
                    val composingText = composingTextForCandidateRequest(insertString)
                    val indexMap = composingText.inputIndexToSurfaceIndexMap(azooKeyRoman2KanaTransducer)
                    val inputIndex = indexMap.entries.firstOrNull { it.value == firstClauseReadingLength }?.key
                        ?: firstClauseReadingLength
                    val raw = lastQwertyRomajiRawInput ?: ""
                    lastQwertyRomajiRawInput = raw.drop(inputIndex)
                    if (remainingKana.isNotEmpty()) {
                        candidateCoordinator.composingTextSession.rebuildFromQwertyRawInput(
                            rawInput = lastQwertyRomajiRawInput.orEmpty(),
                            zenkakuRomaji = isDefaultRomajiHenkanMap,
                            displayInput = remainingKana,
                        )
                    }
                } else if (remainingKana.isNotEmpty()) {
                    candidateCoordinator.composingTextSession.applyDirectInput(remainingKana)
                }

                commitPartialCandidateAndPromoteTail(autoCompletedClause.string, remainingKana)
                if (remainingKana.isEmpty()) {
                    resetAllFlags()
                }
                return
            }

            if (liveText.isNotEmpty() &&
                liveText != lastCandidate &&
                !com.kazumaproject.markdownhelperkeyboard.converter.core.AzooKeyJapaneseConversionText
                    .containsHangul(liveText)
            ) {
                applyLiveConversionDisplay(liveText)
            }
            isContinuousTapInputEnabled.set(true)
            lastFlickConvertedNextHiragana.set(true)
            hasConvertedKatakana = liveText.isNotEmpty() && liveText != insertString
        }
    }

    private fun getAutomaticCompletionStrengthThreshold(strength: String): Int {
        return when (strength) {
            "disabled" -> Int.MAX_VALUE
            "weak" -> 16
            "normal" -> 13
            "strong" -> 10
            "ultrastrong" -> 6
            else -> 16
        }
    }

    private suspend fun getSuggestionListOriginal(
        insertString: String,
        @Suppress("UNUSED_PARAMETER") mainView: MainLayoutBinding,
    ): List<Candidate> {
        return suggestionListForMode(insertString, CandidateRequestMode.Original)
    }

    private suspend fun getSuggestionList(
        insertString: String,
        @Suppress("UNUSED_PARAMETER") mainView: MainLayoutBinding,
    ): List<Candidate> {
        return suggestionListForMode(insertString, CandidateRequestMode.Normal)
    }

    private suspend fun suggestionListForMode(
        insertString: String,
        mode: CandidateRequestMode,
    ): List<Candidate> {
        return suggestionOrchestrator.suggestionList(
            insertString = insertString,
            mode = mode,
            preferences = buildImeCandidatePreferences(),
            zenz = buildImeCandidateZenzContext(insertString),
            composingText = composingTextForCandidateRequest(insertString),
            roman2Kana = azooKeyRoman2KanaTransducer,
            onBunsetsuMerged = candidatePresentationCoordinator::mergeBunsetsuAfterCandidateRequest,
        )
    }

    private suspend fun performZenzRequest(insertString: String): List<ZenzCandidate> {
        return candidateCoordinator.generateLiveZenzCandidates(
            input = insertString,
            zenz = buildImeCandidateZenzContext(insertString),
            preferences = buildImeCandidatePreferences(),
        )
    }

    private fun getLeftContext(inputLength: Int): String {
        val lengthToGetTextBeforeCursor = (128 + inputLength).coerceAtMost(256)
        val charSequence = editorGateway.getTextBeforeCursor(lengthToGetTextBeforeCursor, 0)
            ?: return ""
        val text = charSequence.toString()

        Timber.d("getLeftContext: inputLength [$inputLength] text: [$text]")
        // 改行記号 '\n' があれば、それより後ろの部分だけを返す。
        // 改行がない場合は、テキスト全体が返されます。
        return text.substringAfterLast('\n')
    }

    private fun getRightContext(inputLength: Int): String {
        val lengthToGetTextAfterCursor = (128 + inputLength).coerceAtMost(256)
        val charSequence = editorGateway.getTextAfterCursor(lengthToGetTextAfterCursor, 0)
            ?: return ""
        val text = charSequence.toString()

        Timber.d("getRightContext: inputLength [$inputLength] text: [$text]")

        return text.substringBefore('\n')
    }

    private suspend fun getSuggestionListWithoutPrediction(
        insertString: String,
    ): List<Candidate> {
        return suggestionOrchestrator.suggestionListWithoutPrediction(
            insertString = insertString,
            preferences = buildImeCandidatePreferences(),
            composingText = composingTextForCandidateRequest(insertString),
            roman2Kana = azooKeyRoman2KanaTransducer,
            onBunsetsuMerged = candidatePresentationCoordinator::mergeBunsetsuAfterCandidateRequest,
        )
    }

    private fun getSuggestionListEnglishKana(insertString: String): List<Candidate> {
        return suggestionOrchestrator.suggestionListEnglishKana(insertString)
    }

    /**
     * カーソル前の文字に応じて、単語または記号1つを削除します。
     * - カーソル直前の文字が指定記号の場合：その記号を1つだけ削除します。
     * - カーソル直前の文字がそれ以外の場合：その単語を末尾まで削除します。
     */
    private fun deleteWordOrSymbolsBeforeCursor(insertString: String) {
        if (editorGateway.connection() == null) return
        if (isHenkan.get()) return
        if (stringInTail.get().isNotEmpty()) return

        if (insertString.isNotEmpty()) {
            _inputString.update { "" }
            setComposingText("", 0)
            finishComposingText()
        } else {
            val textBeforeCursor = editorGateway.getTextBeforeCursor(100, 0)?.toString() ?: ""
            if (textBeforeCursor.isEmpty()) return

            val charsToDelete = deleteKeyFlickTargetChars + ALWAYS_DELETE_KEY_FLICK_BOUNDARIES

            var deleteCount = 0

            // カーソル直前の1文字が指定記号かどうかをチェック
            if (textBeforeCursor.last() in charsToDelete) {
                // 記号の場合、1文字だけ削除する
                deleteCount = 1
            } else {
                // 記号でない場合、空白まで遡って単語の長さを数える
                for (char in textBeforeCursor.reversed()) {
                    if (char.isWhitespace() || char in charsToDelete) {
                        // 単語の区切り（空白または記号）が見つかったら停止
                        break
                    }
                    deleteCount++
                }
            }

            if (deleteCount > 0) {
                val deletedText = textBeforeCursor.takeLast(deleteCount)
                editorGateway.deleteSurroundingText(deleteCount, 0)
                if (deletedText.isNotEmpty()) {
                    pushEditHistoryEntry(EditHistoryEntry.DeleteCommittedText(deletedText))
                }
            }
        }
    }

    /**
     * カーソル後の文字に応じて、単語または記号1つを削除します。
     * - カーソル直後の文字が指定記号の場合：その記号を1つだけ削除します。
     * - カーソル直後の文字がそれ以外の場合：その単語を末尾まで削除します。
     * - PreEdit / stringInTail がある場合は committed text を消さず、stringInTail を削除します。
     */
    private fun deleteWordOrSymbolsAfterCursor(insertString: String) {
        if (editorGateway.connection() == null) return
        if (isHenkan.get()) return
        if (insertString.isNotEmpty()) {
            return
        }

        val textAfterCursor = editorGateway.getTextAfterCursor(100, 0)?.toString() ?: ""
        if (textAfterCursor.isEmpty()) return

        val charsToDelete = deleteKeyFlickTargetChars + ALWAYS_DELETE_KEY_FLICK_BOUNDARIES

        var deleteCount = 0
        if (textAfterCursor.first() in charsToDelete) {
            deleteCount = 1
        } else {
            for (char in textAfterCursor) {
                if (char.isWhitespace() || char in charsToDelete) break
                deleteCount++
            }
        }

        if (deleteCount > 0) {
            val deletedText = textAfterCursor.take(deleteCount)
            editorGateway.deleteSurroundingText(0, deleteCount)
            if (deletedText.isNotEmpty()) {
                pushEditHistoryEntry(
                    EditHistoryEntry.DeleteCommittedText(
                        deletedText = deletedText,
                        direction = DeleteDirection.AfterCursor
                    )
                )
            }
        }
    }

    private fun deleteLongPress() {
        if (deleteLongPressJob?.isActive == true) return
        activeDeleteHistoryBatch = DeleteHistoryBatch(
            initialInput = inputString.value,
            initialTail = stringInTail.get(),
            deletesCommittedText = inputString.value.isEmpty()
        )
        deleteLongPressJob = scope.launch {
            while (isActive && deleteKeyLongKeyPressed.get()) {
                val current = inputString.value
                val tailIsEmpty = stringInTail.get().isEmpty()

                if (current.isEmpty()) {
                    if (tailIsEmpty) {
                        // Performance optimization: Skip captureDeletedTextFromConnection during long press deletion
                        // to avoid blocking SQLite/IPC queries in the rapid loop.
                        deleteLastGraphemeOrSelection()
                    } else {
                        break
                    }
                } else {
                    val newString = current.dropLast(1)
                    _inputString.update { newString }
                    if (newString.isEmpty() && tailIsEmpty) {
                        setComposingText("", 0)
                    }
                }

                delay(LONG_DELAY_TIME)
            }
            // （連続タップ入力解除など）
            enableContinuousTapInput()

            val flag = if (inputString.value.isEmpty()) CandidateShowFlag.Idle
            else CandidateShowFlag.Updating
            _suggestionFlag.emit(flag)
        }
        deleteLongPressJob?.invokeOnCompletion {
            if (selectMode.value || !isEditHistoryEnabled()) {
                activeDeleteHistoryBatch = null
                return@invokeOnCompletion
            }
            val batch = activeDeleteHistoryBatch
            activeDeleteHistoryBatch = null
            batch?.let {
                if (it.deletesCommittedText) {
                    val deletedText = it.deletedText.toString()
                    if (deletedText.isNotEmpty()) {
                        pushEditHistoryEntry(EditHistoryEntry.DeleteCommittedText(deletedText))
                    }
                }
            }
        }
    }

    private fun stopDeleteLongPress() {
        deleteKeyLongKeyPressed.set(false)
        onDeleteLongPressUp.set(true)
        deleteLongPressJob?.cancel()
        deleteLongPressJob = null
    }

    private fun enableContinuousTapInput() {
        isContinuousTapInputEnabled.set(true)
        lastFlickConvertedNextHiragana.set(true)
    }

    private fun setEnterKeyPress() {
        Timber.d("setEnterKeyPress: $currentInputType")
        when (currentInputType) {
            InputTypeForIME.TextMultiLine,
            InputTypeForIME.TextImeMultiLine,
            InputTypeForIME.TextShortMessage,
            InputTypeForIME.TextLongMessage,
                -> {
                commitText("\n", 1)
            }

            InputTypeForIME.None,
            InputTypeForIME.Text,
            InputTypeForIME.TextAutoComplete,
            InputTypeForIME.TextAutoCorrect,
            InputTypeForIME.TextCapCharacters,
            InputTypeForIME.TextCapSentences,
            InputTypeForIME.TextCapWords,
            InputTypeForIME.TextEmailSubject,
            InputTypeForIME.TextFilter,
            InputTypeForIME.TextNoSuggestion,
            InputTypeForIME.TextPersonName,
            InputTypeForIME.TextPhonetic,
            InputTypeForIME.TextWebEditText,
            InputTypeForIME.TextUri,
            InputTypeForIME.TextPostalAddress,
            InputTypeForIME.TextEmailAddress,
            InputTypeForIME.TextWebEmailAddress,
            InputTypeForIME.TextPassword,
            InputTypeForIME.TextVisiblePassword,
            InputTypeForIME.TextWebPassword,
            InputTypeForIME.TextNotCursorUpdate,
            InputTypeForIME.TextEditTextInWebView,
            InputTypeForIME.TextSend
                -> {
                Timber.d("Enter key: called 3\n")
                sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
            }

            InputTypeForIME.TextNextLine -> {
                performEditorAction(EditorInfo.IME_ACTION_NEXT)
            }

            InputTypeForIME.TextDone -> {
                performEditorAction(EditorInfo.IME_ACTION_DONE)
            }

            InputTypeForIME.Number,
            InputTypeForIME.NumberDecimal,
            InputTypeForIME.NumberPassword,
            InputTypeForIME.NumberSigned,
            InputTypeForIME.Phone,
            InputTypeForIME.Date,
            InputTypeForIME.Datetime,
            InputTypeForIME.Time,
                -> {
                performEditorAction(EditorInfo.IME_ACTION_DONE)
            }

            InputTypeForIME.TextWebSearchView, InputTypeForIME.TextWebSearchViewFireFox, InputTypeForIME.TextSearchView -> {
                Timber.d(
                    "enter key search: ${EditorInfo.IME_ACTION_SEARCH}" + "\n${currentInputEditorInfo.inputType}" + "\n${currentInputEditorInfo.imeOptions}" + "\n${currentInputEditorInfo.actionId}" + "\n${currentInputEditorInfo.privateImeOptions}"
                )
                performEditorAction(EditorInfo.IME_ACTION_SEARCH)
            }

        }
    }

    private fun handleDeleteKeyTap(insertString: String, suggestions: List<Candidate>) {
        if (routeSymbolPanelSearchDelete()) return
        when {
            insertString.isNotEmpty() -> {
                if (isHenkan.get()) {
                    if (isBunsetsuCursorMoveSessionActive()) {
                        restoreRawInputFromBunsetsuSession()
                        hasConvertedKatakana = isLiveConversionEnable == true
                        resetFlagsDeleteKey()
                    } else if (deleteKeyHighLight == true) {
                        handleDeleteKeyInHenkan(suggestions, insertString)
                    } else {
                        cancelHenkanByLongPressDeleteKey()
                        hasConvertedKatakana = isLiveConversionEnable == true
                    }
                } else {
                    deleteStringCommon(insertString)
                    resetFlagsDeleteKey()
                }
            }

            else -> {
                if (stringInTail.get().isNotEmpty()) return
                if (!selectMode.value) {
                    val beforeChar = captureDeletedTextFromConnection(editorGateway.connection())
                    if (beforeChar.isNotEmpty()) {
                        if (isEditHistoryEnabled()) {
                            Timber.d("delete: $beforeChar")
                            pushEditHistoryEntry(EditHistoryEntry.DeleteCommittedText(beforeChar))
                        }
                    }
                }
                deleteLastGraphemeOrSelection()
            }
        }
    }

    private fun handleSpaceKeyClick(
        isFlick: Boolean,
        insertString: String,
        suggestions: List<Candidate>,
        mainView: MainLayoutBinding
    ) {
        if (cycleFocusedBunsetsuCandidate(delta = 1)) {
            resetFlagsKeySpace()
            return
        }

        if (insertString.isNotBlank()) {
            mainView.apply {
                if (isTabletGojuonSurface()) {
                    tabletView.let { tabletKey ->
                        when (tabletKey.currentInputMode.get()) {
                            InputMode.ModeJapanese -> if (suggestions.isNotEmpty()) handleJapaneseModeSpaceKey(
                                this, suggestions, insertString
                            )

                            else -> setSpaceKeyActionEnglishAndNumberNotEmpty(insertString)
                        }
                    }
                } else {
                    keyboardView.let { tenkey ->
                        when (tenkey.currentInputMode.value) {
                            InputMode.ModeJapanese -> if (suggestions.isNotEmpty()) {
                                if (bunsetsuSeparation == true) {
                                    handleJapaneseModeSpaceKeyWithBunsetsu(
                                        this, suggestions, insertString
                                    )
                                } else {
                                    handleJapaneseModeSpaceKey(
                                        this, suggestions, insertString
                                    )
                                }
                            }

                            else -> setSpaceKeyActionEnglishAndNumberNotEmpty(insertString)
                        }
                    }
                }
            }
        } else {
            if (stringInTail.get().isNotEmpty()) return
            setSpaceKeyActionEnglishAndNumberEmpty(isFlick)
        }
        resetFlagsKeySpace()
    }

    private fun handleSpaceKeyClickFloating(
        isFlick: Boolean,
        insertString: String,
        suggestions: List<Candidate>,
        floatingKeyboardLayoutBinding: FloatingKeyboardLayoutBinding
    ) {
        if (cycleFocusedBunsetsuCandidate(delta = 1, floatingKeyboardLayoutBinding)) {
            resetFlagsKeySpace()
            return
        }

        if (insertString.isNotBlank()) {
            floatingKeyboardLayoutBinding.keyboardViewFloating.let { tenkey ->
                when (tenkey.currentInputMode.value) {
                    InputMode.ModeJapanese -> {
                        if (suggestions.isNotEmpty()) {
                            if (bunsetsuSeparation == true) {
                                handleJapaneseModeSpaceKeyWithBunsetsuFloating(
                                    floatingKeyboardLayoutBinding, suggestions, insertString
                                )
                            } else {
                                handleJapaneseModeSpaceKeyFloating(
                                    floatingKeyboardLayoutBinding, suggestions, insertString
                                )
                            }
                        }
                    }

                    else -> setSpaceKeyActionEnglishAndNumberNotEmpty(insertString)
                }
            }
        } else {
            if (stringInTail.get().isNotEmpty()) return
            setSpaceKeyActionEnglishAndNumberEmpty(isFlick)
        }
        resetFlagsKeySpace()
    }

    private fun handleSpaceKeyClickInQWERTY(
        insertString: String, mainView: MainLayoutBinding, suggestions: List<Candidate>
    ) {
        if (cycleFocusedBunsetsuCandidate(delta = 1)) {
            resetFlagsKeySpace()
            return
        }

        if (insertString.isNotBlank()) {
            mainView.apply {
                when (currentInputModeForSession) {
                    InputMode.ModeJapanese -> {
                        val insertStringEndWithN = if (isDefaultRomajiHenkanMap) {
                            romajiConverter?.flushZenkaku(insertString)?.first
                        } else {
                            romajiConverter?.flush(insertString)?.first
                        }
                        if (insertStringEndWithN == null) {
                            _inputString.update { insertString }
                            if (suggestions.isNotEmpty()) {
                                if (bunsetsuSeparation == true) {
                                    handleJapaneseModeSpaceKeyWithBunsetsu(
                                        this, suggestions, insertString
                                    )
                                } else {
                                    handleJapaneseModeSpaceKey(
                                        this, suggestions, insertString
                                    )
                                }
                            }
                        } else {
                            _inputString.update { insertStringEndWithN }
                            scope.launch {
                                delay(64)
                                val newSuggestionList =
                                    suggestionAdapter?.suggestions ?: emptyList()
                                if (newSuggestionList.isNotEmpty()) {
                                    if (bunsetsuSeparation == true) {
                                        handleJapaneseModeSpaceKeyWithBunsetsu(
                                            mainView, newSuggestionList, insertString
                                        )
                                    } else {
                                        handleJapaneseModeSpaceKey(
                                            mainView, newSuggestionList, insertString
                                        )
                                    }
                                }
                            }
                        }
                    }

                    else -> setSpaceKeyActionEnglishAndNumberNotEmpty(insertString)
                }
            }
        } else {
            if (stringInTail.get().isNotEmpty()) return
            val romajiMode = currentQwertyRomajiModeForSession
            Timber.d("handleSpaceKeyClickInQWERTY: $romajiMode")
            if (romajiMode && qwertyEnableZenkakuSpacePreference == true) {
                handleSpaceKeyClick(false, insertString, suggestions, mainView)
            } else {
                setSpaceKeyActionEnglishAndNumberNotEmpty(insertString)
            }
        }
        resetFlagsKeySpace()
    }

    private fun handleForceHalfWidthSpaceOrConvert(
        mainView: MainLayoutBinding,
        floatingKeyboardLayoutBinding: FloatingKeyboardLayoutBinding? = null
    ) {
        handleForceSpaceOrConvert(
            space = " ",
            mainView = mainView,
            floatingKeyboardLayoutBinding = floatingKeyboardLayoutBinding
        )
    }

    private fun handleForceFullWidthSpaceOrConvert(
        mainView: MainLayoutBinding,
        floatingKeyboardLayoutBinding: FloatingKeyboardLayoutBinding? = null
    ) {
        handleForceSpaceOrConvert(
            space = "　",
            mainView = mainView,
            floatingKeyboardLayoutBinding = floatingKeyboardLayoutBinding
        )
    }

    private fun handleForceSpaceOrConvert(
        space: String,
        mainView: MainLayoutBinding,
        floatingKeyboardLayoutBinding: FloatingKeyboardLayoutBinding?
    ) {
        val insertString = inputString.value
        var suggestions = suggestionAdapter?.suggestions ?: emptyList()

        if (insertString.isNotEmpty() && !isHenkan.get()) {
            val filtered = suggestions.filter { candidate ->
                val lane = CandidateType.laneOf(candidate)
                lane != CandidateLane.Prediction && lane != CandidateLane.Special
            }
            if (filtered.isNotEmpty()) {
                suggestions = filtered
                suggestionAdapter?.suggestions = filtered
                suggestionAdapterFull?.suggestions = filtered
            }
        }

        if (floatingKeyboardLayoutBinding != null) {
            if (cycleFocusedBunsetsuCandidate(delta = 1, floatingKeyboardLayoutBinding)) {
                resetFlagsKeySpace()
                return
            }
        } else if (cycleFocusedBunsetsuCandidate(delta = 1)) {
            resetFlagsKeySpace()
            return
        }

        if (insertString.isNotEmpty()) {
            if (floatingKeyboardLayoutBinding != null) {
                floatingKeyboardLayoutBinding.keyboardViewFloating.let { tenkey ->
                    when (tenkey.currentInputMode.value) {
                        InputMode.ModeJapanese -> {
                            if (suggestions.isNotEmpty()) {
                                if (bunsetsuSeparation == true) {
                                    handleJapaneseModeSpaceKeyWithBunsetsuFloating(
                                        floatingKeyboardLayoutBinding, suggestions, insertString
                                    )
                                } else {
                                    handleJapaneseModeSpaceKeyFloating(
                                        floatingKeyboardLayoutBinding, suggestions, insertString
                                    )
                                }
                            }
                        }

                        else -> setSpaceKeyActionEnglishAndNumberNotEmpty(insertString)
                    }
                }
            } else if (isTabletGojuonSurface()) {
                mainView.tabletView.let { tabletKey ->
                    when (tabletKey.currentInputMode.get()) {
                        InputMode.ModeJapanese -> {
                            if (suggestions.isNotEmpty()) {
                                handleJapaneseModeSpaceKey(mainView, suggestions, insertString)
                            }
                        }

                        else -> setSpaceKeyActionEnglishAndNumberNotEmpty(insertString)
                    }
                }
            } else {
                mainView.keyboardView.let { tenkey ->
                    when (tenkey.currentInputMode.value) {
                        InputMode.ModeJapanese -> {
                            if (suggestions.isNotEmpty()) {
                                if (bunsetsuSeparation == true) {
                                    handleJapaneseModeSpaceKeyWithBunsetsu(
                                        mainView, suggestions, insertString
                                    )
                                } else {
                                    handleJapaneseModeSpaceKey(mainView, suggestions, insertString)
                                }
                            }
                        }

                        else -> setSpaceKeyActionEnglishAndNumberNotEmpty(insertString)
                    }
                }
            }
        } else {
            if (stringInTail.get().isNotEmpty()) return
            commitText(space, 1)
            _inputString.update { "" }
            if (isHenkan.get()) {
                suggestionAdapter?.suggestions = emptyList()
                isHenkan.set(false)
                henkanPressedWithBunsetsuDetect = false
                suggestionClickNum = 0
                suggestionAdapter?.updateHighlightPosition(-1)
            }
        }
        resetFlagsKeySpace()
    }


    private fun handleJapaneseModeSpaceKey(
        mainView: MainLayoutBinding, suggestions: List<Candidate>, insertString: String
    ) {
        if (suggestions.isEmpty()) return
        if (cycleFocusedBunsetsuCandidate(delta = 1)) {
            return
        }

        prepareHenkanCandidateCycle()
        isHenkan.set(true)
        suggestionClickNum += 1
        suggestionClickNum = suggestionClickNum.coerceAtMost(suggestions.size + 1)
        mainView.suggestionRecyclerView.apply {
            smoothScrollToPosition(
                (suggestionClickNum - 1 + 2).coerceAtLeast(0).coerceAtMost(suggestions.size - 1)
            )
            suggestionAdapter?.updateHighlightPosition((suggestionClickNum - 1).coerceAtLeast(0))
        }
        setConvertLetterInJapaneseFromButton(suggestions, true, mainView, insertString)
    }

    private fun handleJapaneseModeSpaceKeyWithBunsetsu(
        mainView: MainLayoutBinding, suggestions: List<Candidate>, insertString: String
    ) {
        if (suggestions.isEmpty()) return
        if (shouldUseBunsetsuCursorMoveSession()) {
            scope.launch {
                val activated = activateBunsetsuConversionSession(
                    input = insertString,
                    mainView = mainView
                )
                if (!activated) {
                    handleJapaneseModeSpaceKey(mainView, suggestions, insertString)
                }
            }
            return
        }

        val position = bunsetsuPositionList?.firstOrNull()

        if (position != null && stringInTail.get().isEmpty()) {
            // 区切り位置がある場合：文字列を分割する
            val head = insertString.substring(0, position)
            val tail = insertString.substring(position)

            _inputString.update { head }
            stringInTail.set(tail)
            Timber.d(
                "handleJapaneseModeSpaceKeyWithBunsetsu called: $bunsetsuPositionList | head: $head, tail: $tail $stringInTail"
            )
            isHenkan.set(true)
            henkanPressedWithBunsetsuDetect = true
            bunsetsuPositionList?.let {
                if (it.size > 1) {
                    bunsetusMultipleDetect = true
                }
            }
        } else {
            prepareHenkanCandidateCycle()
            isHenkan.set(true)
            suggestionClickNum += 1
            suggestionClickNum = suggestionClickNum.coerceAtMost(suggestions.size + 1)
            mainView.suggestionRecyclerView.apply {
                smoothScrollToPosition(
                    (suggestionClickNum - 1 + 2).coerceAtLeast(0).coerceAtMost(suggestions.size - 1)
                )
                suggestionAdapter?.updateHighlightPosition((suggestionClickNum - 1).coerceAtLeast(0))
            }
            setConvertLetterInJapaneseFromButton(suggestions, true, mainView, insertString)
            Timber.d(
                "handleJapaneseModeSpaceKeyWithBunsetsu called: No split position. Full string to tail: $insertString"
            )
        }
    }

    private fun handleJapaneseModeSpaceKeyWithBunsetsuFloating(
        floatingKeyboardLayoutBinding: FloatingKeyboardLayoutBinding,
        suggestions: List<Candidate>,
        insertString: String
    ) {
        if (suggestions.isEmpty()) return
        if (shouldUseBunsetsuCursorMoveSession()) {
            val mainView = mainLayoutBinding ?: return
            scope.launch {
                val activated = activateBunsetsuConversionSession(
                    input = insertString,
                    mainView = mainView,
                    floatingKeyboardLayoutBinding = floatingKeyboardLayoutBinding
                )
                if (!activated) {
                    handleJapaneseModeSpaceKeyFloating(
                        floatingKeyboardLayoutBinding,
                        suggestions,
                        insertString
                    )
                }
            }
            return
        }

        val position = bunsetsuPositionList?.firstOrNull()

        if (position != null && stringInTail.get().isEmpty()) {
            // 区切り位置がある場合：文字列を分割する
            val head = insertString.substring(0, position)
            val tail = insertString.substring(position)

            _inputString.update { head }
            stringInTail.set(tail)
            Timber.d(
                "handleJapaneseModeSpaceKeyWithBunsetsu called: $bunsetsuPositionList | head: $head, tail: $tail $stringInTail"
            )
            isHenkan.set(true)
            henkanPressedWithBunsetsuDetect = true
        } else {
            prepareHenkanCandidateCycle()
            isHenkan.set(true)
            suggestionClickNum += 1
            suggestionClickNum = suggestionClickNum.coerceAtMost(suggestions.size + 1)
            floatingKeyboardLayoutBinding.suggestionRecyclerView.apply {
                smoothScrollToPosition(
                    (suggestionClickNum - 1 + 2).coerceAtLeast(0).coerceAtMost(suggestions.size - 1)
                )
                suggestionAdapter?.updateHighlightPosition((suggestionClickNum - 1).coerceAtLeast(0))
            }
            setConvertLetterInJapaneseFromButtonFloating(
                suggestions,
                true,
                floatingKeyboardLayoutBinding = floatingKeyboardLayoutBinding,
                insertString
            )
            Timber.d(
                "handleJapaneseModeSpaceKeyWithBunsetsu called: No split position. Full string to tail: $insertString"
            )
        }
    }

    private fun handleJapaneseModeSpaceKeyFloating(
        floatingKeyboardLayoutBinding: FloatingKeyboardLayoutBinding,
        suggestions: List<Candidate>,
        insertString: String
    ) {
        if (suggestions.isEmpty()) return
        if (cycleFocusedBunsetsuCandidate(delta = 1, floatingKeyboardLayoutBinding)) {
            return
        }

        prepareHenkanCandidateCycle()
        isHenkan.set(true)
        suggestionClickNum += 1
        suggestionClickNum = suggestionClickNum.coerceAtMost(suggestions.size + 1)
        floatingKeyboardLayoutBinding.suggestionRecyclerView.apply {
            smoothScrollToPosition(
                (suggestionClickNum - 1 + 2).coerceAtLeast(0).coerceAtMost(suggestions.size - 1)
            )
            suggestionAdapter?.updateHighlightPosition((suggestionClickNum - 1).coerceAtLeast(0))
        }
        setConvertLetterInJapaneseFromButtonFloating(
            suggestions, true, floatingKeyboardLayoutBinding, insertString
        )
    }

    private fun handleNonEmptyInputEnterKey(
        suggestions: List<Candidate>, mainView: MainLayoutBinding, insertString: String
    ) {
        if (commitBunsetsuConversionSession()) {
            return
        }
        if (isTabletGojuonSurface()) {
            mainView.tabletView.apply {
                when (val inputMode = currentInputMode.get()) {
                    InputMode.ModeJapanese -> {
                        if (isHenkan.get()) {
                            handleHenkanModeEnterKey(suggestions, inputMode, insertString)
                        } else {
                            commitEnterKeyForJapaneseInput(insertString)
                        }
                    }

                    else -> {
                        finishInputEnterKey()
                        setCursorLeftAfterCommitPair(insertString)
                    }
                }
            }
        } else {
            mainView.keyboardView.apply {
                when (val inputMode = currentInputMode.value) {
                    InputMode.ModeJapanese -> {
                        if (isHenkan.get()) {
                            handleHenkanModeEnterKey(suggestions, inputMode, insertString)
                        } else {
                            commitEnterKeyForJapaneseInput(insertString)
                        }
                    }

                    else -> {
                        finishInputEnterKey()
                        setCursorLeftAfterCommitPair(insertString)
                    }
                }
            }
        }
    }

    /**
     * AzooKey [InputManager.enter](https://github.com/azooKey/azooKey) 相当。
     * ライブ変換 ON 時は [LiveConversionManager.lastUsedCandidate] で確定する。
     */
    private fun commitEnterKeyForJapaneseInput(insertString: String) {
        val tail = stringInTail.get()
        if (isLiveConversionEnable == true && insertString.isNotEmpty()) {
            val candidate = liveConversionManager.lastUsedCandidate
            val canUseCandidate = candidate != null && candidateMatchesInsertString(candidate, insertString)
            val commitString = when {
                canUseCandidate -> getCandidateCommitString(candidate!!)
                !lastCandidate.isNullOrEmpty() && lastCandidate != insertString -> lastCandidate!!
                else -> insertString
            }
            if (canUseCandidate) {
                applyCandidateCompleteActions(candidate!!)
            }
            if (tail.isNotEmpty()) {
                commitPartialCandidateAndPromoteTail(commitString, tail)
                if (canUseCandidate) {
                    liveConversionManager.updateAfterFirstClauseCompletion()
                } else {
                    liveConversionManager.stopComposition()
                }
                candidateCoordinator.resetConversionSession()
                clearSuggestionStateAfterCommit()
                resetFlagsEnterKeyNotHenkan()
                return
            }
            if (canUseCandidate || commitString != insertString) {
                beginBatchEdit()
                try {
                    setComposingText("", 0)
                    finishComposingText()
                    commitText(commitString, 1)
                } finally {
                    endBatchEdit()
                }
                _inputString.update { "" }
                lastQwertyRomajiRawInput = null
                candidateCoordinator.resetConversionSession()
                liveConversionManager.stopComposition()
                clearSuggestionStateAfterCommit()
                resetFlagsEnterKeyNotHenkan()
                return
            }
        }
        finishInputEnterKey()
        setCursorLeftAfterCommitPair(insertString + tail)
    }

    private fun handleNonEmptyInputEnterKeyFloating(
        suggestions: List<Candidate>,
        floatingKeyboardLayoutBinding: FloatingKeyboardLayoutBinding,
        insertString: String
    ) {
        if (commitBunsetsuConversionSession()) {
            return
        }
        floatingKeyboardLayoutBinding.keyboardViewFloating.apply {
            when (val inputMode = currentInputMode.value) {
                InputMode.ModeJapanese -> {
                    if (isHenkan.get()) {
                        handleHenkanModeEnterKey(suggestions, inputMode, insertString)
                    } else {
                        commitEnterKeyForJapaneseInput(insertString)
                    }
                }

                else -> {
                    finishInputEnterKey()
                    setCursorLeftAfterCommitPair(insertString)
                }
            }
        }
    }

    private fun applyCandidateCompleteActions(candidate: Candidate) {
        val moveActions = candidate.actions.filterIsInstance<
            com.kazumaproject.markdownhelperkeyboard.converter.candidate.CandidateCompleteAction.MoveCursor,
            >()
        if (moveActions.isNotEmpty()) {
            moveActions.forEach { action ->
                repeat(kotlin.math.abs(action.offset)) {
                    if (action.offset < 0) {
                        moveCursorLeftBySelection()
                    }
                }
            }
            return
        }
        setCursorLeftAfterCommitPair(candidate.string)
    }

    private fun setCursorLeftAfterCommitPair(insertString: String) {
        if (appPreference.cursor_move_after_commit_target_pairs_preference.contains(insertString)) {
            moveCursorLeftBySelection()
        }
    }

    private fun moveCursorLeftBySelection() {
        if (editorGateway.connection() == null) return

        beginBatchEdit()
        try {
            val req = ExtractedTextRequest()
            req.token = 0
            req.flags = 0
            val extractedText = getExtractedText(req, 0)

            if (extractedText != null) {
                val start = extractedText.selectionStart
                if (start > 0) {
                    setSelection(start - 1, start - 1)
                }
            } else {
                sendDpadLeftIfPossible()
            }
        } catch (e: Exception) {
            Timber.e(e)
        } finally {
            endBatchEdit()
        }
    }

    private fun handleLeftCursorMoveAction() {
        Timber.d("handleLeftCursorMoveAction: called")
        sendDpadLeftIfPossible()
    }

    private fun handleRightCursorMoveAction() {
        sendDpadRightIfPossible()
    }

    private fun handleDeleteKeyInHenkan(suggestions: List<Candidate>, insertString: String) {
        suggestionClickNum -= 1
        mainLayoutBinding?.let { mainView ->
            mainView.suggestionRecyclerView.apply {
                smoothScrollToPosition(
                    if (suggestionClickNum == 1) 1 else (suggestionClickNum - 1).coerceAtLeast(
                        0
                    )
                )
                suggestionAdapter?.updateHighlightPosition(
                    if (suggestionClickNum == 1) 1 else (suggestionClickNum - 1).coerceAtLeast(
                        0
                    )
                )
            }
            setConvertLetterInJapaneseFromButton(suggestions, false, mainView, insertString)
        }
    }

    private fun handleHenkanModeEnterKey(
        suggestions: List<Candidate>, currentInputMode: InputMode, insertString: String
    ) {
        if (suggestionClickNum !in suggestions.indices) {
            suggestionClickNum = 0
        }
        setEnterKeyAction(suggestions, currentInputMode, insertString)
    }

    private fun handleEmptyInputEnterKey(mainView: MainLayoutBinding) {
        if (stringInTail.get().isNotEmpty()) {
            finishComposingText()
            setComposingText("", 0)
            stringInTail.set("")
        } else {
            setEnterKeyPress()
            isHenkan.set(false)
            henkanPressedWithBunsetsuDetect = false
            suggestionClickNum = 0
            suggestionAdapter?.updateHighlightPosition(RecyclerView.NO_POSITION)
            isFirstClickHasStringTail = false
        }
        if (candidateTabVisibility == true) {
            mainView.candidateTabLayout.isVisible = false
            val tab = mainView.candidateTabLayout.getTabAt(0)
            tab?.let { mainView.candidateTabLayout.selectTab(it) }
        }
        updateUpperAreaVisibility(mainView)
        setDrawableToEnterKeyCorrespondingToImeOptions(mainView)
    }

    private fun forceNewLine(mainView: MainLayoutBinding) {
        if (stringInTail.get().isNotEmpty()) {
            finishComposingText()
            setComposingText("", 0)
            stringInTail.set("")
        } else {
            commitText("\n", 1)
            isHenkan.set(false)
            henkanPressedWithBunsetsuDetect = false
            suggestionClickNum = 0
            suggestionAdapter?.updateHighlightPosition(RecyclerView.NO_POSITION)
            isFirstClickHasStringTail = false
        }
        if (candidateTabVisibility == true) {
            mainView.candidateTabLayout.isVisible = false
            val tab = mainView.candidateTabLayout.getTabAt(0)
            tab?.let { mainView.candidateTabLayout.selectTab(it) }
        }
        updateUpperAreaVisibility(mainView)
        setDrawableToEnterKeyCorrespondingToImeOptions(mainView)
    }

    private fun handleEmptyInputEnterKeyFloating(floatingKeyboardLayoutBinding: FloatingKeyboardLayoutBinding) {
        if (stringInTail.get().isNotEmpty()) {
            finishComposingText()
            setComposingText("", 0)
            stringInTail.set("")
        } else {
            setEnterKeyPress()
            isHenkan.set(false)
            henkanPressedWithBunsetsuDetect = false
            suggestionClickNum = 0
            suggestionAdapter?.updateHighlightPosition(RecyclerView.NO_POSITION)
            isFirstClickHasStringTail = false
        }
        setDrawableToEnterKeyCorrespondingToImeOptionsFloating(floatingKeyboardLayoutBinding)
    }

    private fun setDrawableToEnterKeyCorrespondingToImeOptions(mainView: MainLayoutBinding) {
        val currentDrawable = when (currentInputType) {
            InputTypeForIME.TextWebSearchView, InputTypeForIME.TextWebSearchViewFireFox, InputTypeForIME.TextSearchView -> {
                cachedSearchDrawable
            }

            InputTypeForIME.TextMultiLine, InputTypeForIME.TextImeMultiLine, InputTypeForIME.TextShortMessage, InputTypeForIME.TextLongMessage -> {
                cachedReturnDrawable
            }

            InputTypeForIME.TextEmailAddress, InputTypeForIME.TextEmailSubject, InputTypeForIME.TextNextLine -> {
                cachedTabDrawable
            }

            InputTypeForIME.TextDone -> {
                cachedCheckDrawable
            }

            InputTypeForIME.TextSend -> {
                cachedArrowRightDrawable
            }

            else -> {
                cachedArrowRightDrawable
            }
        }
        if (isTabletGojuonSurface()) {
            mainView.tabletView.setSideKeyEnterDrawable(currentDrawable)
        } else {
            mainView.keyboardView.setSideKeyEnterDrawable(currentDrawable)
        }
    }

    private fun setDrawableToEnterKeyCorrespondingToImeOptionsFloating(floatingKeyboardLayoutBinding: FloatingKeyboardLayoutBinding) {
        val currentDrawable = when (currentInputType) {
            InputTypeForIME.TextWebSearchView, InputTypeForIME.TextWebSearchViewFireFox, InputTypeForIME.TextSearchView -> {
                cachedSearchDrawable
            }

            InputTypeForIME.TextMultiLine, InputTypeForIME.TextImeMultiLine, InputTypeForIME.TextShortMessage, InputTypeForIME.TextLongMessage -> {
                cachedReturnDrawable
            }

            InputTypeForIME.TextEmailAddress, InputTypeForIME.TextEmailSubject, InputTypeForIME.TextNextLine -> {
                cachedTabDrawable
            }

            InputTypeForIME.TextDone -> {
                cachedCheckDrawable
            }

            InputTypeForIME.TextSend -> {
                cachedArrowRightDrawable
            }

            else -> {
                cachedArrowRightDrawable
            }
        }
        floatingKeyboardLayoutBinding.keyboardViewFloating.setSideKeyEnterDrawable(currentDrawable)
    }

    private fun finishInputEnterKey() {
        _inputString.update { "" }
        lastQwertyRomajiRawInput = null
        candidateCoordinator.resetConversionSession()
        finishComposingText()
        clearSuggestionStateAfterCommit()
        resetFlagsEnterKeyNotHenkan()
    }

    /**
     * Deletes the last grapheme cluster before the cursor or deletes the current selection.
     * This correctly handles complex emojis and user text selections.
     */
    private fun deleteLastGraphemeOrSelection() {
        lastCommittedCandidate = null
        sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
    }

    private fun handleLeftKeyPress(gestureType: GestureType, insertString: String) {
        Timber.d("called handleLeftKeyPress $insertString ${stringInTail.get()} $gestureType")
        if (gestureType == GestureType.FlickTop || gestureType == GestureType.FlickBottom ||
            gestureType == GestureType.FlickLeft || gestureType == GestureType.FlickRight) {
            when (gestureType) {
                GestureType.FlickRight -> sendDpadRightIfPossible()
                GestureType.FlickTop -> sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_UP)
                GestureType.FlickLeft -> sendDpadLeftIfPossible()
                GestureType.FlickBottom -> sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_DOWN)
                else -> {}
            }
            return
        }

        if (insertString.isEmpty() && stringInTail.get().isEmpty()) {
            if (gestureType == GestureType.Tap) {
                sendDpadLeftIfPossible()
            }
        } else if (!isHenkan.get()) {
            lastFlickConvertedNextHiragana.set(true)
            isContinuousTapInputEnabled.set(true)
            englishSpaceKeyPressed.set(false)
            suggestionClickNum = 0
            if (insertString.isNotEmpty()) {
                beginZenzRerankRequest()
                lastCandidate = null
                val tail = stringInTail.get()
                val stringBuilder = StringBuilder(tail)
                suppressSelectionCleanupForInternalPreEditMove()
                if (insertString.length == 1) {
                    stringInTail.set(stringBuilder.insert(0, insertString.last()).toString())
                    _inputString.update { "" }
                    suggestionAdapter?.suggestions = emptyList()
                    if (isKeyboardFloatingMode == true) {
                        floatingKeyboardBinding?.let { mainView ->
                            animateSuggestionImageViewVisibility(
                                mainView.suggestionVisibility, false
                            )
                        }
                    } else {
                        mainLayoutBinding?.let { mainView ->
                            animateSuggestionImageViewVisibility(
                                mainView.suggestionVisibility, false
                            )
                        }
                    }
                } else {
                    stringInTail.set(stringBuilder.insert(0, insertString.last()).toString())
                    _inputString.update { it.dropLast(1) }
                }
                invalidateLiveConversionAfterInternalCursorMove()
                refreshCandidateForCurrentPreedit()
            }
        }
    }

    private fun handleLeftLongPress() {
        if (!isHenkan.get()) {
            lastFlickConvertedNextHiragana.set(true)
            isContinuousTapInputEnabled.set(true)
            onLeftKeyLongPressUp.set(false)
            suggestionClickNum = 0
            asyncLeftLongPress()
        }
    }

    private fun handleRightLongPress() {
        if (!isHenkan.get()) {
            onRightKeyLongPressUp.set(false)
            suggestionClickNum = 0
            lastFlickConvertedNextHiragana.set(true)
            isContinuousTapInputEnabled.set(true)
            asyncRightLongPress()
        }
    }

    private fun asyncLeftLongPress() {
        Timber.d("asyncLeftLongPress called")
        if (leftLongPressJob?.isActive == true) return
        leftLongPressJob = scope.launch {
            var finalSuggestionFlag: CandidateShowFlag? = null

            while (isActive && leftCursorKeyLongKeyPressed.get() && !onLeftKeyLongPressUp.get()) {

                val insertString = inputString.value

                Timber.d("asyncLeftLongPress called while loop")

                // tail があり composing が空 → Idle で抜ける
                if (stringInTail.get().isNotEmpty() && insertString.isEmpty()) {
                    finalSuggestionFlag = CandidateShowFlag.Idle
                    handleLeftCursorMoveAction()
                    break
                }

                if (insertString.isNotEmpty()) {
                    updateLeftInputString(insertString)
                } else if (stringInTail.get().isEmpty() && !isCursorAtBeginning()) {
                    if (selectMode.value) {
                        extendOrShrinkLeftOneChar()
                    } else {
                        handleLeftCursorMoveAction()
                    }
                } else {
                    handleLeftCursorMoveAction()
                }

                delay(LONG_DELAY_TIME)
            }
            _suggestionFlag.emit(
                finalSuggestionFlag
                    ?: if (inputString.value.isEmpty()) CandidateShowFlag.Idle else CandidateShowFlag.Updating
            )
        }
    }

    private fun asyncRightLongPress() {
        if (rightLongPressJob?.isActive == true) return
        rightLongPressJob = scope.launch {
            var finalSuggestionFlag: CandidateShowFlag? = null
            while (isActive && rightCursorKeyLongKeyPressed.get() && !onRightKeyLongPressUp.get()) {
                val insertString = inputString.value
                if (stringInTail.get().isEmpty() && insertString.isNotEmpty()) {
                    finalSuggestionFlag = CandidateShowFlag.Updating
                    break
                }
                actionInRightKeyPressed(insertString)
                delay(LONG_DELAY_TIME)
            }
            _suggestionFlag.emit(
                finalSuggestionFlag
                    ?: if (inputString.value.isNotEmpty()) CandidateShowFlag.Updating else CandidateShowFlag.Idle
            )

        }
    }

    private fun updateLeftInputString(insertString: String) {
        if (insertString.isNotEmpty()) {
            beginZenzRerankRequest()
            lastCandidate = null
            suppressSelectionCleanupForInternalPreEditMove()
            if (insertString.length == 1) {
                stringInTail.set(insertString + stringInTail.get())
                _inputString.update { "" }
                suggestionAdapter?.suggestions = emptyList()
                if (isKeyboardFloatingMode == true) {
                    floatingKeyboardBinding?.let { mainView ->
                        animateSuggestionImageViewVisibility(
                            mainView.suggestionVisibility, false
                        )
                    }
                } else {
                    mainLayoutBinding?.let { mainView ->
                        animateSuggestionImageViewVisibility(
                            mainView.suggestionVisibility, false
                        )
                    }
                }
            } else {
                stringInTail.set(insertString.last() + stringInTail.get())
                _inputString.update { it.dropLast(1) }
            }
            invalidateLiveConversionAfterInternalCursorMove()
            refreshCandidateForCurrentPreedit()
        }
    }

    private fun actionInRightKeyPressed(gestureType: GestureType, insertString: String) {
        if (selectMode.value) {
            extendOrShrinkSelectionRight()
        } else {
            if (gestureType == GestureType.Tap &&
                insertString.isNotEmpty() &&
                stringInTail.get().isNotEmpty()
            ) {
                handleNonHenkan(insertString)
            } else {
                handleEmptyInputString(gestureType)
            }
        }
    }

    private fun actionInRightKeyPressed(insertString: String) {
        when {
            insertString.isEmpty() -> handleEmptyInputString()
            !isHenkan.get() -> handleNonHenkan(insertString)
        }
    }

    private fun handleEmptyInputString(gestureType: GestureType) {
        if (gestureType == GestureType.FlickTop || gestureType == GestureType.FlickBottom ||
            gestureType == GestureType.FlickLeft || gestureType == GestureType.FlickRight) {
            when (gestureType) {
                GestureType.FlickRight -> sendDpadRightIfPossible()
                GestureType.FlickTop -> sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_UP)
                GestureType.FlickLeft -> sendDpadLeftIfPossible()
                GestureType.FlickBottom -> sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_DOWN)
                else -> {}
            }
            return
        }

        if (stringInTail.get().isEmpty()) {
            if (gestureType == GestureType.Tap) {
                sendDpadRightIfPossible()
            }
        } else {
            beginZenzRerankRequest()
            lastCandidate = null
            suppressSelectionCleanupForInternalPreEditMove()
            val dropString = stringInTail.get().first()
            stringInTail.set(stringInTail.get().drop(1))
            _inputString.update { dropString.toString() }
            invalidateLiveConversionAfterInternalCursorMove()
            refreshCandidateForCurrentPreedit()
        }
    }

    private fun isCursorAtBeginning(): Boolean {
        val extractedText = runCatching {
            editorGateway.getExtractedText(ExtractedTextRequest(), 0)
        }.getOrNull()
        extractedText?.selectionStart?.let { return it <= 0 }
        val textBeforeCursor = runCatching {
            editorGateway.getTextBeforeCursor(1, 0)
        }.getOrNull()
        return textBeforeCursor.isNullOrEmpty()
    }

    private fun isCursorAtEnd(): Boolean {
        val extractedText = runCatching {
            editorGateway.getExtractedText(ExtractedTextRequest(), 0)
        }.getOrNull()
        extractedText?.let {
            val textLength = it.text?.length ?: 0
            val cursorPosition = it.selectionEnd
            return cursorPosition >= textLength
        }
        val textAfterCursor = runCatching {
            editorGateway.getTextAfterCursor(1, 0)
        }.getOrNull()
        return textAfterCursor.isNullOrEmpty()
    }

    private fun isSelectionActive(): Boolean {
        val extractedText = runCatching {
            editorGateway.getExtractedText(ExtractedTextRequest(), 0)
        }.getOrNull()
        extractedText?.let {
            return it.selectionStart != it.selectionEnd
        }
        val selectedText = runCatching {
            editorGateway.getSelectedText(0)
        }.getOrNull()
        return !selectedText.isNullOrEmpty()
    }

    private fun sendDpadLeftIfPossible() {
        if (isSelectionActive() || !isCursorAtBeginning()) {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_LEFT)
        }
    }

    private fun sendDpadRightIfPossible() {
        if (isSelectionActive() || !isCursorAtEnd()) {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_RIGHT)
        }
    }

    private fun handleEmptyInputString() {
        if (stringInTail.get().isEmpty()) {
            if (selectMode.value) {
                extendOrShrinkSelectionRight()
            } else {
                handleRightCursorMoveAction()
            }
        } else {
            beginZenzRerankRequest()
            lastCandidate = null
            suppressSelectionCleanupForInternalPreEditMove()
            val dropString = stringInTail.get().first()
            stringInTail.set(stringInTail.get().drop(1))
            _inputString.update { dropString.toString() }
            invalidateLiveConversionAfterInternalCursorMove()
            refreshCandidateForCurrentPreedit()
        }
    }

    private fun handleNonHenkanTap(insertString: String) {
        englishSpaceKeyPressed.set(false)
        lastFlickConvertedNextHiragana.set(true)
        isContinuousTapInputEnabled.set(true)
        suggestionClickNum = 0
        if (stringInTail.get().isNotEmpty()) {
            beginZenzRerankRequest()
            lastCandidate = null
            suppressSelectionCleanupForInternalPreEditMove()
            _inputString.update { insertString + stringInTail.get().first() }
            stringInTail.set(stringInTail.get().drop(1))
            invalidateLiveConversionAfterInternalCursorMove()
            refreshCandidateForCurrentPreedit()
        }
    }

    private fun handleNonHenkan(insertString: String) {
        Timber.d("handleNonHenkan: $insertString ${stringInTail.get()}")
        englishSpaceKeyPressed.set(false)
        lastFlickConvertedNextHiragana.set(true)
        isContinuousTapInputEnabled.set(true)
        suggestionClickNum = 0
        if (stringInTail.get().isNotEmpty()) {
            beginZenzRerankRequest()
            lastCandidate = null
            suppressSelectionCleanupForInternalPreEditMove()
            _inputString.update { insertString + stringInTail.get()[0] }
            stringInTail.set(stringInTail.get().substring(1))
            invalidateLiveConversionAfterInternalCursorMove()
            refreshCandidateForCurrentPreedit()
        }
    }

    private fun appendCharToStringBuilder(
        char: Char, insertString: String, stringBuilder: StringBuilder
    ) {
        if (insertString.length == 1) {
            stringBuilder.append(char)
            _inputString.update { stringBuilder.toString() }
        } else {
            try {
                stringBuilder.append(insertString).deleteCharAt(insertString.lastIndex).append(char)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            }
            _inputString.update {
                stringBuilder.toString()
            }
        }
    }

    private fun deleteStringCommon(insertString: String) {
        clearFunctionKeyConversionSource()
        val length = insertString.length
        when {
            length > 1 -> {
                _inputString.update {
                    it.dropLast(1)
                }
            }

            else -> {
                _inputString.update { "" }
                if (stringInTail.get().isEmpty()) setComposingText("", 0)
            }
        }
    }

    private fun setCurrentInputCharacterContinuous(
        char: Char, insertString: String, sb: StringBuilder
    ) {
        if (shouldDirectInsertCharacter(char)) {
            insertDirectText(char.toString())
            return
        }
        suggestionClickNum = 0
        _dakutenPressed.value = false
        englishSpaceKeyPressed.set(false)
        onDeleteLongPressUp.set(false)
        if (insertString.isNotEmpty()) {
            sb.append(insertString).append(char)
            _inputString.update {
                sb.toString()
            }
        } else {
            _inputString.update {
                char.toString()
            }
        }
    }

    private fun setCurrentInputCharacter(
        char: Char, inputForInsert: String, sb: StringBuilder,
    ) {
        if (shouldDirectInsertCharacter(char)) {
            insertDirectText(char.toString())
            return
        }
        if (inputForInsert.isNotEmpty()) {
            val hiraganaAtInsertPosition = inputForInsert.last()
            val nextChar = hiraganaAtInsertPosition.getNextInputChar(char)
            if (nextChar == null) {
                _inputString.update {
                    sb.append(inputForInsert).append(char).toString()
                }
            } else {
                appendCharToStringBuilder(nextChar, inputForInsert, sb)
            }
        } else {
            _inputString.update {
                char.toString()
            }
        }
    }

    private fun sendCharTap(
        charToSend: Char, insertString: String, sb: StringBuilder
    ) {
        if (routeSymbolPanelSearchText(charToSend.toString())) return
        when (currentInputType) {
            InputTypeForIME.None,
            InputTypeForIME.Number,
            InputTypeForIME.NumberDecimal,
            InputTypeForIME.NumberPassword,
            InputTypeForIME.NumberSigned,
            InputTypeForIME.Phone,
            InputTypeForIME.Date,
            InputTypeForIME.Datetime,
            InputTypeForIME.Time,
                -> {
                insertDirectCharacterFromKeyboard(charToSend)
            }

            in passwordTypes -> {
                if (showCandidateInPasswordPreference == true) {
                    insertDirectCharacterFromKeyboard(charToSend)
                } else {
                    setCurrentInputCharacterContinuous(
                        charToSend, insertString, sb
                    )
                }
            }

            else -> {
                if (isFlickOnlyMode == true) {
                    sendCharFlick(charToSend, insertString, sb)
                    isContinuousTapInputEnabled.set(true)
                    lastFlickConvertedNextHiragana.set(true)
                } else {
                    if (isContinuousTapInputEnabled.get() && lastFlickConvertedNextHiragana.get()) {
                        setCurrentInputCharacterContinuous(
                            charToSend, insertString, sb
                        )
                        lastFlickConvertedNextHiragana.set(false)
                    } else {
                        setKeyTouch(
                            charToSend, insertString, sb
                        )
                    }
                }
            }
        }
    }

    private fun sendCharFlick(
        charToSend: Char, insertString: String, sb: StringBuilder
    ) {
        if (routeSymbolPanelSearchText(charToSend.toString())) return
        when (currentInputType) {
            InputTypeForIME.None,
            InputTypeForIME.Number,
            InputTypeForIME.NumberDecimal,
            InputTypeForIME.NumberPassword,
            InputTypeForIME.NumberSigned,
            InputTypeForIME.Phone,
            InputTypeForIME.Date,
            InputTypeForIME.Datetime,
            InputTypeForIME.Time,
                -> {
                insertDirectCharacterFromKeyboard(charToSend)
            }

            in passwordTypes -> {
                if (showCandidateInPasswordPreference == true) {
                    insertDirectCharacterFromKeyboard(charToSend)
                } else {
                    setCurrentInputCharacterContinuous(
                        charToSend, insertString, sb
                    )
                }
            }

            else -> {
                setCurrentInputCharacterContinuous(
                    charToSend, insertString, sb
                )
            }
        }
    }

    private fun setStringBuilderForConvertStringInHiragana(
        inputChar: Char, sb: StringBuilder, insertString: String
    ) {
        if (insertString.isEmpty()) return
        val updated = if (insertString.length == 1) {
            inputChar.toString()
        } else {
            insertString.dropLast(1) + inputChar
        }
        if (routeSymbolPanelSearchFullText(updated)) {
            lastQwertyRomajiRawInput = null
            return
        }
        if (insertString.length == 1) {
            sb.append(inputChar)
        } else {
            sb.append(insertString).deleteAt(insertString.length - 1).append(inputChar)
        }
        _inputString.update { updated }
    }

    private fun toggleDakutenOnlyForCustomKeyboard() {
        val insertString = keyboardCompositionTextForEditing()
        if (insertString.isEmpty()) return

        insertString.last().toggleDakutenWithSeion()?.let { toggled ->
            setStringBuilderForConvertStringInHiragana(
                toggled,
                StringBuilder(),
                insertString
            )
        }
    }

    private fun toggleHandakutenOnlyForCustomKeyboard() {
        val insertString = keyboardCompositionTextForEditing()
        if (insertString.isEmpty()) return

        insertString.last().toggleHandakutenWithSeion()?.let { toggled ->
            setStringBuilderForConvertStringInHiragana(
                toggled,
                StringBuilder(),
                insertString
            )
        }
    }

    private fun dakutenSmallLetter(
        sb: StringBuilder, insertString: String, gestureType: GestureType
    ) {
        _dakutenPressed.value = true
        englishSpaceKeyPressed.set(false)
        val compositionText = keyboardCompositionTextForEditing().ifEmpty { insertString }
        if (compositionText.isNotEmpty()) {
            val insertPosition = compositionText.last()
            insertPosition.let { c ->
                if (c.isHiragana()) {
                    when (gestureType) {
                        GestureType.Tap, GestureType.FlickBottom -> {
                            c.getDakutenSmallChar()?.let { dakutenChar ->
                                setStringBuilderForConvertStringInHiragana(
                                    dakutenChar, sb, compositionText
                                )
                            }
                        }

                        GestureType.FlickLeft -> {
                            c.getDakutenFlickLeft()?.let { dakutenChar ->
                                setStringBuilderForConvertStringInHiragana(
                                    dakutenChar, sb, compositionText
                                )
                            }
                        }

                        GestureType.FlickRight -> {
                            c.getDakutenFlickRight()?.let { dakutenChar ->
                                setStringBuilderForConvertStringInHiragana(
                                    dakutenChar, sb, compositionText
                                )
                            }
                        }

                        GestureType.FlickTop -> {
                            c.getDakutenFlickTop()?.let { dakutenChar ->
                                setStringBuilderForConvertStringInHiragana(
                                    dakutenChar, sb, compositionText
                                )
                            }
                        }

                        else -> {}
                    }
                }
            }
        } else {
            if (!onKeyboardSwitchLongPressUp && qwertyMode.value != TenKeyQWERTYMode.Custom && tenkeyShowIMEButtonPreference == true) {
                switchNextKeyboard()
            }
        }
    }

    private fun dakutenSmallLetterFloating(
        sb: StringBuilder,
        insertString: String,
        gestureType: GestureType
    ) {
        _dakutenPressed.value = true
        englishSpaceKeyPressed.set(false)
        val compositionText = keyboardCompositionTextForEditing().ifEmpty { insertString }

        if (compositionText.isNotEmpty()) {
            val insertPosition = compositionText.last()
            insertPosition.let { c ->
                if (c.isHiragana()) {
                    when (gestureType) {
                        GestureType.Tap, GestureType.FlickBottom -> {
                            c.getDakutenSmallChar()?.let { dakutenChar ->
                                setStringBuilderForConvertStringInHiragana(
                                    dakutenChar,
                                    sb,
                                    compositionText
                                )
                            }
                        }

                        GestureType.FlickLeft -> {
                            c.getDakutenFlickLeft()?.let { dakutenChar ->
                                setStringBuilderForConvertStringInHiragana(
                                    dakutenChar,
                                    sb,
                                    compositionText
                                )
                            }
                        }

                        GestureType.FlickRight -> {
                            c.getDakutenFlickRight()?.let { dakutenChar ->
                                setStringBuilderForConvertStringInHiragana(
                                    dakutenChar,
                                    sb,
                                    compositionText
                                )
                            }
                        }

                        GestureType.FlickTop -> {
                            c.getDakutenFlickTop()?.let { dakutenChar ->
                                setStringBuilderForConvertStringInHiragana(
                                    dakutenChar,
                                    sb,
                                    compositionText
                                )
                            }
                        }

                        else -> {}
                    }
                }
            }
        } else {
            if (!onKeyboardSwitchLongPressUp &&
                qwertyMode.value != TenKeyQWERTYMode.Custom &&
                tenkeyShowIMEButtonPreference == true
            ) {
                switchNextKeyboard()
            }
        }
    }

    // 2) 次のモードに切り替える関数
    fun switchNextKeyboard() {
        if (keyboardOrder.isEmpty()) return

        // モジュール演算で自動的に 0 に戻る
        val nextIndex = (currentKeyboardOrder + 1) % keyboardOrder.size
        val nextType = keyboardOrder[nextIndex]

        when (nextType) {
            KeyboardType.TENKEY -> {
                setCurrentInputModeForSession(InputMode.ModeJapanese)
            }

            KeyboardType.SUMIRE -> {
                setCurrentInputModeForSession(InputMode.ModeJapanese)
            }

            KeyboardType.ROMAJI -> {
                setCurrentInputModeForSession(InputMode.ModeJapanese)
            }

            KeyboardType.QWERTY -> {
                setCurrentInputModeForSession(InputMode.ModeEnglish)
            }

            KeyboardType.CUSTOM -> {}
        }

        // 統一された showKeyboard 関数を呼び出す
        showKeyboard(nextType)

        currentKeyboardOrder = nextIndex
        if (enableShowLastShownKeyboardInRestart == true) {
            appPreference.save_last_used_keyboard_position_preference = nextIndex
        }

        if (qwertyMode.value == TenKeyQWERTYMode.Number) {
            val type = when (nextType) {
                KeyboardType.TENKEY -> TenKeyQWERTYMode.Default
                KeyboardType.SUMIRE -> TenKeyQWERTYMode.Sumire
                KeyboardType.QWERTY -> TenKeyQWERTYMode.TenKeyQWERTY
                KeyboardType.ROMAJI -> TenKeyQWERTYMode.TenKeyQWERTYRomaji
                KeyboardType.CUSTOM -> TenKeyQWERTYMode.Custom
            }
            _tenKeyQWERTYMode.update { type }
        }

        mainLayoutBinding?.let { mainView ->
            setKeyboardSizeSwitchKeyboard(mainView)
        }
    }

    private fun smallBigLetterConversionEnglish(
        sb: StringBuilder, insertString: String,
    ) {
        _dakutenPressed.value = true
        englishSpaceKeyPressed.set(false)

        if (insertString.isNotEmpty()) {
            val insertPosition = insertString.last()
            insertPosition.let { c ->
                if (!c.isHiragana()) {
                    c.getDakutenSmallChar()?.let { dakutenChar ->
                        setStringBuilderForConvertStringInHiragana(dakutenChar, sb, insertString)
                    }
                }
            }
        } else {
            if (!onKeyboardSwitchLongPressUp && tenkeyShowIMEButtonPreference == true) {
                switchNextKeyboard()
            }
        }
    }

    private fun smallBigLetterConversionEnglishFloating(
        sb: StringBuilder, insertString: String,
    ) {
        _dakutenPressed.value = true
        englishSpaceKeyPressed.set(false)

        if (insertString.isNotEmpty()) {
            val insertPosition = insertString.last()
            insertPosition.let { c ->
                if (!c.isHiragana()) {
                    c.getDakutenSmallChar()?.let { dakutenChar ->
                        setStringBuilderForConvertStringInHiragana(dakutenChar, sb, insertString)
                    }
                }
            }
        }
    }

    private fun handleDakutenSmallLetterKey(
        sb: StringBuilder,
        isFlick: Boolean,
        char: Char?,
        insertString: String,
        mainView: MainLayoutBinding,
        gestureType: GestureType
    ) {
        if (isTabletGojuonSurface()) {
            mainView.tabletView.let {
                when (it.currentInputMode.get()) {
                    InputMode.ModeJapanese -> {
                        dakutenSmallLetter(
                            sb, insertString, gestureType
                        )
                    }

                    InputMode.ModeEnglish -> {
                        smallBigLetterConversionEnglish(sb, insertString)
                    }

                    InputMode.ModeNumber -> {
                        _tenKeyQWERTYMode.update {
                            TenKeyQWERTYMode.TenKeyQWERTY
                        }
                    }
                }
            }
        } else {
            mainView.keyboardView.let {
                when (it.currentInputMode.value) {
                    InputMode.ModeJapanese -> {
                        dakutenSmallLetter(
                            sb, insertString, gestureType
                        )
                    }

                    InputMode.ModeEnglish -> {
                        smallBigLetterConversionEnglish(sb, insertString)
                    }

                    InputMode.ModeNumber -> {
                        if (isFlick) {
                            char?.let { c ->
                                sendCharFlick(
                                    charToSend = c, insertString = insertString, sb = sb
                                )
                            }
                            isContinuousTapInputEnabled.set(true)
                            lastFlickConvertedNextHiragana.set(true)
                        } else {
                            char?.let { c ->
                                sendCharTap(
                                    charToSend = c, insertString = insertString, sb = sb
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun handleDakutenSmallLetterKeyFloating(
        sb: StringBuilder,
        isFlick: Boolean,
        char: Char?,
        insertString: String,
        floatingKeyboardLayoutBinding: FloatingKeyboardLayoutBinding,
        gestureType: GestureType
    ) {
        floatingKeyboardLayoutBinding.keyboardViewFloating.let {
            when (it.currentInputMode.value) {
                InputMode.ModeJapanese -> {
                    dakutenSmallLetterFloating(
                        sb, insertString, gestureType
                    )
                }

                InputMode.ModeEnglish -> {
                    smallBigLetterConversionEnglishFloating(sb, insertString)
                }

                InputMode.ModeNumber -> {
                    if (isFlick) {
                        char?.let { c ->
                            sendCharFlick(
                                charToSend = c, insertString = insertString, sb = sb
                            )
                        }
                        isContinuousTapInputEnabled.set(true)
                        lastFlickConvertedNextHiragana.set(true)
                    } else {
                        char?.let { c ->
                            sendCharTap(
                                charToSend = c, insertString = insertString, sb = sb
                            )
                        }
                    }
                }
            }
        }
    }

    private fun setKeyTouch(
        key: Char, insertString: String, sb: StringBuilder,
    ) {
        suggestionClickNum = 0
        _dakutenPressed.value = false
        englishSpaceKeyPressed.set(false)
        lastFlickConvertedNextHiragana.set(false)
        onDeleteLongPressUp.set(false)
        isContinuousTapInputEnabled.set(false)
        if (isHenkan.get()) {
            clearBunsetsuConversionSession()
            finishComposingText()
            setComposingText("", 0)
            _inputString.update {
                key.toString()
            }
            isHenkan.set(false)
            henkanPressedWithBunsetsuDetect = false
            suggestionAdapter?.updateHighlightPosition(RecyclerView.NO_POSITION)
            isFirstClickHasStringTail = false
        } else {
            if (shouldDirectInsertCharacter(key)) {
                insertDirectText(key.toString())
                return
            }
            setCurrentInputCharacter(
                key, insertString, sb
            )
        }
    }

    private fun setConvertLetterInJapaneseFromButton(
        suggestions: List<Candidate>,
        isSpaceKey: Boolean,
        mainView: MainLayoutBinding,
        insertString: String
    ) {
        if (suggestionClickNum > suggestions.size) suggestionClickNum = 0
        val listIterator = suggestions.listIterator((suggestionClickNum - 1).coerceAtLeast(0))
        Timber.d("setConvertLetterInJapaneseFromButton ${listIterator.hasPrevious()} ${listIterator.hasNext()} $isSpaceKey")
        when {
            !listIterator.hasPrevious() && isSpaceKey -> {
                setSuggestionComposingText(suggestions, insertString)
                mainView.suggestionRecyclerView.smoothScrollToPosition(0)
                suggestionAdapter?.updateHighlightPosition(0)
            }

            !listIterator.hasPrevious() && !isSpaceKey -> {
                setSuggestionComposingText(suggestions, insertString)
                mainView.suggestionRecyclerView.smoothScrollToPosition(0)
                suggestionAdapter?.updateHighlightPosition(0)
            }

            listIterator.hasNext() && isSpaceKey -> {
                if (suggestionClickNum > suggestions.size) suggestionClickNum = 0
                setSuggestionComposingText(suggestions, insertString)
            }

            listIterator.hasNext() && !isSpaceKey -> {
                if (suggestionClickNum > suggestions.size) suggestionClickNum = 0
                setSuggestionComposingText(suggestions, insertString)
            }
        }
    }

    private fun setConvertLetterInJapaneseFromButtonFloating(
        suggestions: List<Candidate>,
        isSpaceKey: Boolean,
        floatingKeyboardLayoutBinding: FloatingKeyboardLayoutBinding,
        insertString: String
    ) {
        if (suggestionClickNum > suggestions.size) suggestionClickNum = 0
        val listIterator = suggestions.listIterator((suggestionClickNum - 1).coerceAtLeast(0))
        when {
            !listIterator.hasPrevious() && isSpaceKey -> {
                setSuggestionComposingText(suggestions, insertString)
                floatingKeyboardLayoutBinding.suggestionRecyclerView.smoothScrollToPosition(0)
                suggestionAdapter?.updateHighlightPosition(0)
            }

            !listIterator.hasPrevious() && !isSpaceKey -> {
                setSuggestionComposingText(suggestions, insertString)
                floatingKeyboardLayoutBinding.suggestionRecyclerView.smoothScrollToPosition(0)
                suggestionAdapter?.updateHighlightPosition(0)
            }

            listIterator.hasNext() && isSpaceKey -> {
                if (suggestionClickNum > suggestions.size) suggestionClickNum = 0
                setSuggestionComposingText(suggestions, insertString)
            }

            listIterator.hasNext() && !isSpaceKey -> {
                if (suggestionClickNum > suggestions.size) suggestionClickNum = 0
                setSuggestionComposingText(suggestions, insertString)
            }
        }
    }

    private fun setSpaceKeyActionEnglishAndNumberNotEmpty(insertString: String) {
        Timber.d("setSpaceKeyActionEnglishAndNumberNotEmpty: $insertString ${stringInTail.get()}")
        if (stringInTail.get().isNotEmpty()) {
            val extractedText = getExtractedText(ExtractedTextRequest(), 0)
            val currentCursorPosition = extractedText?.selectionEnd ?: 0
            commitText("$insertString $stringInTail", 1)
            val newCursorPosition =
                (currentCursorPosition - stringInTail.get().length + 1).coerceAtLeast(0)
            stringInTail.set("")
            setSelection(newCursorPosition, newCursorPosition)
            Timber.d("setSpaceKeyActionEnglishAndNumberNotEmpty: $currentCursorPosition ${extractedText?.text}")
        } else {
            commitText("$insertString ", 1)
        }
        _inputString.update {
            ""
        }
        if (isHenkan.get()) {
            suggestionAdapter?.suggestions = emptyList()
            isHenkan.set(false)
            henkanPressedWithBunsetsuDetect = false
            suggestionClickNum = 0
            suggestionAdapter?.updateHighlightPosition(-1)
        }
    }

    private fun setSpaceKeyActionEnglishAndNumberEmpty(isFlick: Boolean) {
        Timber.d("setSpaceKeyActionEnglishAndNumberEmpty: $isFlick ${stringInTail.get()}")
        if (stringInTail.get().isNotEmpty()) {
            commitText(" $stringInTail", 1)
            stringInTail.set("")
        } else {
            mainLayoutBinding?.let { mainView ->
                commitText(
                    resolveEmptySpaceForCurrentMode(
                        isFlick = isFlick,
                        currentInputMode = currentTenkeyInputMode(mainView)
                    ),
                    1
                )
            }
        }
        _inputString.update { "" }
        if (isHenkan.get()) {
            suggestionAdapter?.suggestions = emptyList()
            isHenkan.set(false)
            henkanPressedWithBunsetsuDetect = false
            suggestionClickNum = 0
            suggestionAdapter?.updateHighlightPosition(-1)
        }
    }

    private fun resolveEmptySpaceForCurrentMode(
        isFlick: Boolean,
        currentInputMode: InputMode
    ): String {
        return resolveEmptySpaceForCurrentMode(
            isCustomLayoutDirectMode = isCustomLayoutDirectMode,
            customDirectModeSpaceHankakuPreference = customDirectModeSpaceHankakuPreference,
            isFlick = isFlick,
            currentInputMode = currentInputMode
        )
    }

    private var isFirstClickHasStringTail = false
    private var promotedTailAfterCandidateCommit = false

    private fun prepareHenkanCandidateCycle() {
        if (!isHenkan.get()) {
            suggestionClickNum = 0
            isFirstClickHasStringTail = false
            suggestionAdapter?.updateHighlightPosition(RecyclerView.NO_POSITION)
            suggestionAdapterFull?.updateHighlightPosition(RecyclerView.NO_POSITION)
        }
    }

    private fun setSuggestionComposingText(suggestions: List<Candidate>, insertString: String) {
        if (suggestionClickNum == 1 && stringInTail.get().isNotEmpty()) {
            isFirstClickHasStringTail = true
        }

        Timber.d("setSuggestionComposingText: $isFirstClickHasStringTail $suggestionClickNum ${stringInTail.get()}")

        val index = (suggestionClickNum - 1).coerceAtLeast(0)
        if (suggestionClickNum <= 0) suggestionClickNum = 1

        val nextSuggestion = suggestions[index]
        val candidateType = nextSuggestion.type.toInt()
        val suggestionText = nextSuggestion.string
        val suggestionLength = nextSuggestion.length.toInt()
        if (candidateType == 5 || candidateType == 7 || candidateType == 8) {
            val tail = insertString.substring(suggestionLength)
            if (!isFirstClickHasStringTail) stringInTail.set(tail)
        } else if (candidateType == 15) {
            val (correctedReading) = nextSuggestion.string.correctReading()
            val fullText = correctedReading + stringInTail
            applyComposingText(
                text = fullText,
                highlightLength = correctedReading.length,
                backgroundColor = if (customComposingTextPreference == true) {
                    inputConversionBackgroundColor
                        ?: getColor(com.kazumaproject.core.R.color.orange)
                } else {
                    getColor(com.kazumaproject.core.R.color.orange)
                },
                textColor = if (customComposingTextPreference == true) {
                    inputConversionTextColor
                } else {
                    null
                }
            )
            return
        }
        val fullText = suggestionText + stringInTail
        applyComposingText(
            text = fullText,
            highlightLength = suggestionText.length,
            backgroundColor = if (customComposingTextPreference == true) {
                inputConversionBackgroundColor
                    ?: getColor(com.kazumaproject.core.R.color.orange)
            } else {
                getColor(com.kazumaproject.core.R.color.orange)
            },
            textColor = if (customComposingTextPreference == true) {
                inputConversionTextColor
            } else {
                null
            }
        )
    }

    /**
     * ComposingTextを適用する（ハイライト指定あり）
     */
    private fun applyComposingText(
        text: String,
        highlightLength: Int,
        @ColorInt backgroundColor: Int,
        @ColorInt textColor: Int? = null
    ) {
        applyComposingTextRange(
            text = text,
            highlightStart = 0,
            highlightEnd = highlightLength,
            backgroundColor = backgroundColor,
            textColor = textColor
        )
    }

    private fun applyComposingTextRange(
        text: String,
        highlightStart: Int,
        highlightEnd: Int,
        @ColorInt backgroundColor: Int,
        @ColorInt textColor: Int? = null
    ) {
        val spannableString = SpannableString(text)
        val safeStart = highlightStart.coerceIn(0, text.length)
        val safeEnd = highlightEnd.coerceIn(safeStart, text.length)
        val spanFlag = Spannable.SPAN_EXCLUSIVE_EXCLUSIVE or Spannable.SPAN_COMPOSING

        spannableString.apply {
            // 背景色
            setSpan(
                BackgroundColorSpan(backgroundColor),
                safeStart,
                safeEnd,
                spanFlag
            )

            // テキスト色
            textColor?.let { color ->
                setSpan(
                    ForegroundColorSpan(color),
                    safeStart,
                    safeEnd,
                    spanFlag
                )
            }

            if (text.isNotEmpty()) {
                setSpan(
                    UnderlineSpan(),
                    0,
                    text.length,
                    spanFlag
                )
            }
        }

        setComposingText(spannableString, 1)
    }

    private fun setNextReturnInputCharacter(insertString: String) {
        _dakutenPressed.value = true
        englishSpaceKeyPressed.set(false)
        val sb = StringBuilder()
        if (insertString.isNotEmpty()) {
            val insertPosition = insertString.last()
            insertPosition.let { c ->
                c.getNextReturnInputChar()?.let { charForReturn ->
                    appendCharToStringBuilder(
                        charForReturn, insertString, sb
                    )
                }
            }
        }
    }

    private val vibratorManager by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        } else null
    }
    private val vibrator by lazy {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        } else null
    }
    private val audioManager by lazy {
        getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    private enum class KeySoundType {
        STANDARD,
        DELETE,
        ENTER
    }

    private fun vibrate() {
        if (isVibration == false) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibrationEffect = VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
            val combinedVibration = CombinedVibration.createParallel(vibrationEffect)
            vibratorManager?.vibrate(combinedVibration)
        } else {
            vibrator?.vibrate(2)
        }
    }

    private fun handleKeyPressFeedback(keySoundType: KeySoundType) {
        when (vibrationTimingStr) {
            "both", "press" -> vibrate()
            "release", null -> Unit
        }
        playKeySound(keySoundType)
    }

    private fun playKeySound(keySoundType: KeySoundType) {
        if (isKeySoundEnabled != true) return
        if (audioManager.ringerMode != AudioManager.RINGER_MODE_NORMAL) return

        val effectType = when (keySoundType) {
            KeySoundType.STANDARD -> AudioManager.FX_KEYPRESS_STANDARD
            KeySoundType.DELETE -> AudioManager.FX_KEYPRESS_DELETE
            KeySoundType.ENTER -> AudioManager.FX_KEYPRESS_RETURN
        }
        val volumePercent = (keySoundVolumePercent ?: 0).coerceIn(0, 100)

        if (volumePercent == 0) {
            audioManager.playSoundEffect(effectType)
        } else {
            audioManager.playSoundEffect(effectType, volumePercent / 100f)
        }
    }

    private fun getKeySoundType(key: Key): KeySoundType {
        return when (key) {
            Key.SideKeyDelete -> KeySoundType.DELETE
            Key.SideKeyEnter -> KeySoundType.ENTER
            else -> KeySoundType.STANDARD
        }
    }

    private fun getKeySoundType(qwertyKey: QWERTYKey): KeySoundType {
        return when (qwertyKey) {
            QWERTYKey.QWERTYKeyDelete -> KeySoundType.DELETE
            QWERTYKey.QWERTYKeyReturn -> KeySoundType.ENTER
            else -> KeySoundType.STANDARD
        }
    }

    private fun toggleEmojiKeyboard() {
        _keyboardSymbolViewState.value = SymbolKeyboardState(
            isShown = !_keyboardSymbolViewState.value.isShown
        )
        stringInTail.set("")
        finishComposingText()
        setComposingText("", 0)
        _inputString.update { "" }
    }

    private fun getKeySoundType(action: KeyAction): KeySoundType {
        return when (action) {
            KeyAction.Delete,
            KeyAction.Backspace,
            KeyAction.DeleteUntilSymbol,
            KeyAction.DeleteAfterCursorUntilSymbol,
            KeyAction.UndoLastDelete -> KeySoundType.DELETE

            KeyAction.Enter,
            KeyAction.NewLine,
            KeyAction.Confirm -> KeySoundType.ENTER

            else -> KeySoundType.STANDARD
        }
    }

    private fun isDevicePhysicalKeyboard(device: InputDevice?): Boolean {
        return hardwareKeyboardCoordinator.isPhysicalKeyboard(device)
    }

    /**
     * Handles moving to the next page and looping back to the start.
     */
    private fun goToNextPageForFloatingCandidate() {
        if (fullSuggestionsList.isEmpty()) return
        val maxPage = (fullSuggestionsList.size - 1) / PAGE_SIZE
        currentPage = if (currentPage >= maxPage) 0 else currentPage + 1
        currentHighlightIndex = 0
        displayCurrentPage()
    }

    private fun goToPreviousPageForFloatingCandidate() {
        // Only proceed if we are not on the first page
        if (currentPage > 0) {
            currentPage--
            currentHighlightIndex = 0
            displayCurrentPage()
        }
    }

    private fun checkForPhysicalKeyboard(hasPhysicalKeyboard: Boolean) {
        if (hasPhysicalKeyboard) {
            Timber.d("A physical keyboard is connected.")
        } else {
            Timber.d("No physical keyboard is connected.")
        }
        val previousConnected = hasHardwareKeyboardConnected == true
        hardwareKeyboardCoordinator.applyPresenceEffect(
            effect = hardwareKeyboardCoordinator.buildPresenceEffect(
                hasPhysicalKeyboard = hasPhysicalKeyboard,
                readFloatingModePreference = { appPreference.is_floating_mode ?: false },
            ),
            host = physicalKeyboardPresenceHost,
            previousHasPhysicalKeyboard = previousConnected,
        )
    }

    private fun providesZenzEngine(context: Context): ZenzEngine {
        val defaultAssetFileName = "ggml-model-Q5_K_M.gguf"
        val defaultDestFile = File(context.filesDir, defaultAssetFileName)

        fun ensureDefaultModelCopied(): File {
            if (!defaultDestFile.exists()) {
                context.assets.open(defaultAssetFileName).use { input ->
                    FileOutputStream(defaultDestFile).use { output ->
                        input.copyTo(output)
                    }
                }
            }
            return defaultDestFile
        }

        fun copyUriToInternalFile(uriString: String): File {
            val uri = uriString.toUri()
            val dest = File(context.filesDir, "zenz_custom_model.gguf")

            context.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "openInputStream returned null for uri=$uri" }
                FileOutputStream(dest).use { output ->
                    input.copyTo(output)
                }
            }
            return dest
        }

        val customUri = AppPreference.zenz_model_uri_preference

        // 1) まずユーザー指定があれば試す
        if (customUri.isNotBlank()) {
            try {
                val customFile = copyUriToInternalFile(customUri)
                ZenzEngine.initModel(customFile.absolutePath)
                com.kazumaproject.markdownhelperkeyboard.converter.zenz.ZenzModelIdentity.update(customFile.absolutePath)
                Timber.d("Zenz model initialized with custom file: ${customFile.absolutePath}")
                return ZenzEngine
            } catch (e: Exception) {
                Timber.e(e, "Zenz Failed to init Zenz with custom model. Fallback to default.")
                // フォールバック継続
            }
        }

        // 2) デフォルトで初期化（ここも失敗し得るので try/catch）
        try {
            val defaultFile = ensureDefaultModelCopied()
            ZenzEngine.initModel(defaultFile.absolutePath)
            com.kazumaproject.markdownhelperkeyboard.converter.zenz.ZenzModelIdentity.update(defaultFile.absolutePath)
            Timber.d("Zenz model initialized with default asset file: ${defaultFile.absolutePath}")
        } catch (e: Exception) {
            Timber.e(e, "Zenz Failed to init Zenz with default model as well.")
            // ここまで失敗する場合は致命的。ZenzEngine が内部で未初期化でもアプリが落ちない設計なら return は可能。
            // 必要ならここで例外を投げる/機能OFF扱いにするなど方針を決める。
        }

        return ZenzEngine
    }

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    override fun getTextBeforeCursor(p0: Int, p1: Int): CharSequence? {
        return editorGateway.getTextBeforeCursor(p0, p1)
    }

    override fun getTextAfterCursor(p0: Int, p1: Int): CharSequence? {
        return editorGateway.getTextAfterCursor(p0, p1)
    }

    override fun getSelectedText(p0: Int): CharSequence? {
        return editorGateway.getSelectedText(p0)
    }

    override fun getCursorCapsMode(p0: Int): Int {
        return editorGateway.getCursorCapsMode(p0)
    }

    override fun getExtractedText(p0: ExtractedTextRequest?, p1: Int): ExtractedText? {
        return editorGateway.getExtractedText(p0, p1)
    }

    override fun deleteSurroundingText(p0: Int, p1: Int): Boolean {
        return editorGateway.deleteSurroundingText(p0, p1)
    }

    override fun deleteSurroundingTextInCodePoints(p0: Int, p1: Int): Boolean {
        return editorGateway.deleteSurroundingTextInCodePoints(p0, p1)
    }

    override fun setComposingText(p0: CharSequence?, p1: Int): Boolean {
        return editorGateway.setComposingText(p0, p1)
    }

    override fun setComposingRegion(p0: Int, p1: Int): Boolean {
        return editorGateway.setComposingRegion(p0, p1)
    }

    override fun finishComposingText(): Boolean {
        liveConversionManager.stopComposition()
        return editorGateway.finishComposingText()
    }

    override fun commitText(p0: CharSequence?, p1: Int): Boolean {
        return editorGateway.commitText(p0, p1)
    }

    override fun commitCompletion(p0: CompletionInfo?): Boolean {
        return editorGateway.commitCompletion(p0)
    }

    override fun commitCorrection(p0: CorrectionInfo?): Boolean {
        return editorGateway.commitCorrection(p0)
    }

    override fun setSelection(p0: Int, p1: Int): Boolean {
        return editorGateway.setSelection(p0, p1)
    }

    override fun performEditorAction(p0: Int): Boolean {
        return editorGateway.performEditorAction(p0)
    }

    override fun performContextMenuAction(p0: Int): Boolean {
        return editorGateway.performContextMenuAction(p0)
    }

    override fun beginBatchEdit(): Boolean {
        return editorGateway.beginBatchEdit()
    }

    override fun endBatchEdit(): Boolean {
        return editorGateway.endBatchEdit()
    }

    override fun sendKeyEvent(p0: KeyEvent?): Boolean {
        return editorGateway.sendKeyEvent(p0)
    }

    override fun clearMetaKeyStates(p0: Int): Boolean {
        return editorGateway.clearMetaKeyStates(p0)
    }

    override fun reportFullscreenMode(p0: Boolean): Boolean {
        return editorGateway.reportFullscreenMode(p0)
    }

    override fun performPrivateCommand(p0: String?, p1: Bundle?): Boolean {
        return editorGateway.performPrivateCommand(p0, p1)
    }

    override fun requestCursorUpdates(p0: Int): Boolean {
        if (isSystemUiRemoteInputSession) return false
        return editorGateway.requestCursorUpdates(p0)
    }

    override fun getHandler(): Handler? {
        return editorGateway.handler()
    }

    override fun closeConnection() {
        editorGateway.closeConnection()
    }

    override fun commitContent(
        inputContent: InputContentInfo, flags: Int, opts: Bundle?
    ): Boolean {
        return editorGateway.commitContent(inputContent, flags, opts)
    }

    override fun onToggled(isEnabled: Boolean) {
        isClipboardHistoryFeatureEnabled = isEnabled
        appPreference.clipboard_history_enable = isEnabled
    }

    override fun onInputDeviceAdded(p0: Int) {
        val device = hardwareKeyboardCoordinator.getDevice(p0)
        if (hardwareKeyboardCoordinator.isPhysicalDeviceId(p0)) {
            Timber.d("Physical keyboard connected: ${device?.name}")
        }
        hardwareKeyboardCoordinator.onDeviceAdded(p0, physicalKeyboardPresenceListener)
    }

    override fun onInputDeviceChanged(p0: Int) {
        Timber.d("Input device changed: ID $p0")
        hardwareKeyboardCoordinator.onDeviceChanged(physicalKeyboardPresenceListener)
    }

    override fun onInputDeviceRemoved(p0: Int) {
        val device = hardwareKeyboardCoordinator.getDevice(p0)
        Timber.d("Input device removed: ${device?.name} ID $p0")
        hardwareKeyboardCoordinator.onDeviceChanged(physicalKeyboardPresenceListener)
    }

    private val physicalKeyboardPresenceListener =
        HardwareKeyboardCoordinator.PhysicalKeyboardPresenceListener { hasPhysical ->
            checkForPhysicalKeyboard(hasPhysical)
        }

    override fun onCreateInlineSuggestionsRequest(uiExtras: android.os.Bundle): android.view.inputmethod.InlineSuggestionsRequest? {
        Timber.d("onCreateInlineSuggestionsRequest called")
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.R) {
            return null
        }
        if (isSystemUiRemoteInputSession) {
            Timber.d("Skip inline suggestions for SystemUI remote input")
            return null
        }
        return try {
            val minInlineSuggestionSize = android.util.Size(
                applicationContext.dpToPx(100),
                applicationContext.dpToPx(36),
            )
            val maxInlineSuggestionSize = android.util.Size(
                applicationContext.dpToPx(740),
                applicationContext.dpToPx(36),
            )
            val specBuilder = android.widget.inline.InlinePresentationSpec.Builder(
                minInlineSuggestionSize,
                maxInlineSuggestionSize,
            )

            val isDark = keyboardThemeMode == "dark" ||
                    (keyboardThemeMode == "default" &&
                            (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                            android.content.res.Configuration.UI_MODE_NIGHT_YES)
            val isCustomDark = !(customThemeBgColor ?: Color.WHITE).isLightColor()
            val chipTextColor: Int
            when (keyboardThemeMode) {
                "custom" -> {
                    chipTextColor = customThemeSpecialKeyTextColor
                        ?: customThemeCandidateTextColor
                        ?: (if (isCustomDark) android.graphics.Color.WHITE else android.graphics.Color.BLACK)
                }
                "dark" -> {
                    chipTextColor = android.graphics.Color.WHITE
                }
                "light" -> {
                    chipTextColor = android.graphics.Color.BLACK
                }
                else -> {
                    val isDynamic = com.google.android.material.color.DynamicColors.isDynamicColorAvailable()
                    if (isDynamic) {
                        val fallbackTextColor = androidx.core.content.ContextCompat.getColor(this, com.kazumaproject.core.R.color.keyboard_icon_color)
                        chipTextColor = getThemeColorOrFallback(
                            attrRes = com.google.android.material.R.attr.colorOnSurface,
                            fallbackColor = fallbackTextColor
                        )
                    } else {
                        chipTextColor = if (isDark) android.graphics.Color.WHITE else android.graphics.Color.BLACK
                    }
                }
            }
            val chipTextHintColor: Int = android.graphics.Color.argb(
                153,
                android.graphics.Color.red(chipTextColor),
                android.graphics.Color.green(chipTextColor),
                android.graphics.Color.blue(chipTextColor)
            )

            val stylesBuilder = androidx.autofill.inline.UiVersions.newStylesBuilder()
            val chipBgDrawableId = androidx.autofill.R.drawable.autofill_inline_suggestion_chip_background
            val chipBgColor: Int
            when (keyboardThemeMode) {
                "custom" -> {
                    val baseColor = customThemeCandidateTextColor
                        ?: customThemeSpecialKeyTextColor
                        ?: (if (isCustomDark) android.graphics.Color.WHITE else android.graphics.Color.BLACK)
                    chipBgColor = android.graphics.Color.argb(
                        40,
                        android.graphics.Color.red(baseColor),
                        android.graphics.Color.green(baseColor),
                        android.graphics.Color.blue(baseColor)
                    )
                }
                "dark" -> {
                    chipBgColor = android.graphics.Color.argb(40, 255, 255, 255)
                }
                "light" -> {
                    chipBgColor = android.graphics.Color.argb(40, 0, 0, 0)
                }
                else -> {
                    val isDynamic = com.google.android.material.color.DynamicColors.isDynamicColorAvailable()
                    if (isDynamic) {
                        val fallbackBgColor = androidx.core.content.ContextCompat.getColor(
                            this,
                            com.kazumaproject.core.R.color.keyboard_icon_color
                        )
                        chipBgColor = getThemeColorOrFallback(
                            attrRes = com.google.android.material.R.attr.colorSurfaceContainerHigh,
                            fallbackColor = fallbackBgColor
                        )
                    } else {
                        chipBgColor = if (isDark) android.graphics.Color.argb(40, 255, 255, 255)
                        else android.graphics.Color.argb(40, 0, 0, 0)
                    }
                }
            }

            val style = androidx.autofill.inline.v1.InlineSuggestionUi.newStyleBuilder()
                .setSingleIconChipStyle(
                    androidx.autofill.inline.common.ViewStyle.Builder()
                        .setBackground(
                            android.graphics.drawable.Icon.createWithResource(applicationContext, chipBgDrawableId)
                                .setTint(chipBgColor)
                        )
                        .setPadding(0, 0, 0, 0)
                        .build()
                )
                .setChipStyle(
                    androidx.autofill.inline.common.ViewStyle.Builder()
                        .setBackground(
                            android.graphics.drawable.Icon.createWithResource(applicationContext, chipBgDrawableId)
                                .setTint(chipBgColor)
                        )
                        .setPadding(
                            applicationContext.dpToPx(12),
                            0,
                            applicationContext.dpToPx(12),
                            0
                        )
                        .build()
                )
                .setStartIconStyle(
                    androidx.autofill.inline.common.ImageViewStyle.Builder()
                        .setLayoutMargin(0, 0, 0, 0)
                        .build()
                )
                .setTitleStyle(
                    androidx.autofill.inline.common.TextViewStyle.Builder()
                        .setTextColor(chipTextColor)
                        .setTextSize(12f)
                        .build()
                )
                .setSubtitleStyle(
                    androidx.autofill.inline.common.TextViewStyle.Builder()
                        .setTextColor(chipTextHintColor)
                        .setTextSize(10f)
                        .build()
                )
                .setEndIconStyle(
                    androidx.autofill.inline.common.ImageViewStyle.Builder()
                        .setLayoutMargin(0, 0, 0, 0)
                        .build()
                )
                .build()
            stylesBuilder.addStyle(style)
            specBuilder.setStyle(stylesBuilder.build())

            val spec = specBuilder.build()
            // Some password managers only respond when multiple presentation specs
            // are available, matching the AOSP/HeliBoard inline autofill pattern.
            android.view.inputmethod.InlineSuggestionsRequest.Builder(listOf(spec, spec, spec))
                .setMaxSuggestionCount(6)
                .setExtras(android.os.Bundle())
                .build()
        } catch (e: Exception) {
            Timber.e(e, "Error during onCreateInlineSuggestionsRequest")
            null
        }
    }

    override fun onInlineSuggestionsResponse(response: android.view.inputmethod.InlineSuggestionsResponse): Boolean {
        Timber.d("onInlineSuggestionsResponse called with ${response.inlineSuggestions.size} suggestions")
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            if (isSystemUiRemoteInputSession) {
                Timber.d("Drop inline suggestions response for SystemUI remote input")
                suggestionAdapter?.setInlineSuggestions(emptyList())
                suggestionAdapterFull?.setInlineSuggestions(emptyList())
                return true
            }
            val suggestions = response.inlineSuggestions
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                suggestionAdapter?.setInlineSuggestions(suggestions)
                suggestionAdapterFull?.setInlineSuggestions(suggestions)
                if (suggestions.isNotEmpty()) {
                    mainLayoutBinding?.let { mainView ->
                        animateSuggestionImageViewVisibility(mainView.suggestionVisibility, true)
                        updateUpperAreaVisibility(mainView)
                    }
                }
                mainLayoutBinding?.let { updateUpperAreaVisibility(it) }
            }
        }
        return true
    }
}
