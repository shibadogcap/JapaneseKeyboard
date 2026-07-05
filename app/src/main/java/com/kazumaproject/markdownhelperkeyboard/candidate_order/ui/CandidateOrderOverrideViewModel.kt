package com.kazumaproject.markdownhelperkeyboard.candidate_order.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.candidate_order.database.CandidateOrderOverrideEntity
import com.kazumaproject.markdownhelperkeyboard.candidate_order.model.CandidateOrderItem
import com.kazumaproject.markdownhelperkeyboard.candidate_order.model.SavedCandidateOrderGroup
import com.kazumaproject.markdownhelperkeyboard.converter.api.KanaKanjiConverter
import com.kazumaproject.markdownhelperkeyboard.converter.api.ComposingText
import com.kazumaproject.markdownhelperkeyboard.converter.api.ConvertRequestOptions
import com.kazumaproject.markdownhelperkeyboard.converter.api.ConvertRuntimeContext
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyStylePredictionMode
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyStyleLearningType
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyStyleZenzaiMode
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyStyleTypoCorrectionMode
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CandidateRequestPrivacy
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyLiveConversionMode
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.ImeCandidateEnvironment
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AuxiliaryCandidateSourceConfig
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.repository.CandidateOrderOverrideRepository
import com.kazumaproject.markdownhelperkeyboard.repository.LearnRepository
import com.kazumaproject.markdownhelperkeyboard.repository.UserDictionaryRepository
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class CandidateOrderOverrideUiState(
    val reading: String = "",
    val candidates: List<CandidateOrderItem> = emptyList(),
    val savedOrders: List<SavedCandidateOrderGroup> = emptyList(),
    val isLoading: Boolean = false,
    val message: String? = null
)

internal data class CandidateOrderEditingState(
    val reading: String,
    val candidates: List<CandidateOrderItem>
)

internal fun filterCandidateOrderEditableCandidates(
    reading: String,
    candidates: List<Candidate>
): List<Candidate> {
    return candidates
        .filter { candidate ->
            candidate.string.isNotBlank() &&
                    candidate.length.toInt() == reading.length
        }
        .distinctBy { it.string }
}

internal fun List<CandidateOrderOverrideEntity>.toSavedCandidateOrderGroups(): List<SavedCandidateOrderGroup> {
    return groupBy { it.input }
        .map { (input, rows) ->
            val sortedRows = rows.sortedBy { it.rank }
            SavedCandidateOrderGroup(
                input = input,
                candidates = sortedRows.map { it.candidate },
                updatedAt = sortedRows.maxOfOrNull { it.updatedAt } ?: 0L
            )
        }
        .sortedWith(
            compareByDescending<SavedCandidateOrderGroup> { it.updatedAt }
                .thenBy { it.input }
        )
}

internal fun SavedCandidateOrderGroup.toCandidateOrderEditingState(): CandidateOrderEditingState? {
    val normalizedInput = input.trim()
    if (normalizedInput.isEmpty() || candidates.isEmpty()) return null

    return CandidateOrderEditingState(
        reading = normalizedInput,
        candidates = candidates.mapIndexed { index, candidate ->
            CandidateOrderItem(
                candidate = candidate,
                originalIndex = index
            )
        }
    )
}

@HiltViewModel
class CandidateOrderOverrideViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val kanaKanjiConverter: KanaKanjiConverter,
    private val appPreference: AppPreference,
    private val userDictionaryRepository: UserDictionaryRepository,
    private val learnRepository: LearnRepository,
    private val candidateOrderOverrideRepository: CandidateOrderOverrideRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CandidateOrderOverrideUiState())
    val uiState: StateFlow<CandidateOrderOverrideUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            candidateOrderOverrideRepository.observeAll()
                .collect { entities ->
                    _uiState.update {
                        it.copy(savedOrders = entities.toSavedCandidateOrderGroups())
                    }
                }
        }
    }

    fun updateReading(reading: String) {
        if (uiState.value.reading == reading) return
        _uiState.update { it.copy(reading = reading) }
    }

    fun editSavedOrder(savedOrder: SavedCandidateOrderGroup) {
        val editingState = savedOrder.toCandidateOrderEditingState() ?: return

        _uiState.update {
            it.copy(
                reading = editingState.reading,
                candidates = editingState.candidates,
                message = null
            )
        }
    }

    fun fetchCandidates() {
        val reading = uiState.value.reading.trim()
        if (reading.isEmpty()) {
            _uiState.update { it.copy(message = "読みを入力してください") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, message = null) }
            val candidates = withContext(Dispatchers.Default) {
                val composingText = ComposingText(reading)
                 val options = ConvertRequestOptions(
                    nBest = appPreference.n_best_preference ?: 8,
                    requireJapanesePrediction = AzooKeyStylePredictionMode.AutoMix,
                    requireEnglishPrediction = AzooKeyStylePredictionMode.Disabled,
                    learningType = AzooKeyStyleLearningType.InputAndOutput,
                    zenzaiMode = AzooKeyStyleZenzaiMode.Off,
                    experimentalZenzaiPredictiveInput = false,
                    typoCorrectionMode = AzooKeyStyleTypoCorrectionMode.Automatic,
                    metadata = null,
                    useUserDictionary = true,
                    useUserTemplate = false,
                    useRomajiCandidates = false,
                    useBunsetsu = false,
                    useOmissionSearch = false,
                )
                val runtime = ConvertRuntimeContext(
                    privacy = CandidateRequestPrivacy(),
                    isCandidateSelectionActive = false,
                    isConverting = false,
                    isDirectInputMode = false,
                    liveConversionMode = AzooKeyLiveConversionMode.Disabled,
                )
                val environment = ImeCandidateEnvironment(
                    auxiliaryConfig = AuxiliaryCandidateSourceConfig(
                        learnedPrefixMatchThreshold = 0,
                        userDictionaryPrefixMatchThreshold = 0,
                    ),
                    isLearnDictionaryMode = false,
                    romanize = { null },
                    toHankakuAlphabet = { it },
                    onNormalBunsetsuResult = { _, _ -> },
                    latticeIncrementalState = null,
                )
                val response = kanaKanjiConverter.requestCandidates(
                    input = composingText,
                    options = options,
                    runtime = runtime,
                    environment = environment,
                )
                response.result.mainResults
            }.let { filterCandidateOrderEditableCandidates(reading, it) }

            val orderedCandidates = withContext(Dispatchers.IO) {
                candidateOrderOverrideRepository.applyOrder(reading, candidates)
            }

            _uiState.update {
                it.copy(
                    candidates = orderedCandidates.mapIndexed { index, candidate ->
                        CandidateOrderItem(
                            candidate = candidate.string,
                            originalIndex = index
                        )
                    },
                    isLoading = false,
                    message = if (orderedCandidates.isEmpty()) "候補が見つかりません" else null
                )
            }
        }
    }

    fun moveCandidate(from: Int, to: Int) {
        val current = uiState.value.candidates
        if (from !in current.indices || to !in current.indices) return

        val reordered = current.toMutableList()
        val item = reordered.removeAt(from)
        reordered.add(to, item)
        _uiState.update { it.copy(candidates = reordered) }
    }

    fun save() {
        val reading = uiState.value.reading.trim()
        val candidates = uiState.value.candidates
        if (reading.isEmpty() || candidates.isEmpty()) return

        viewModelScope.launch(Dispatchers.IO) {
            candidateOrderOverrideRepository.saveOrder(
                input = reading,
                candidates = candidates.map { it.candidate }
            )
            _uiState.update { it.copy(candidates = emptyList(), message = context.getString(R.string.candidate_order_override_saved)) }
        }
    }

    fun deleteSavedOrder(input: String) {
        val normalizedInput = input.trim()
        if (normalizedInput.isEmpty()) return

        viewModelScope.launch(Dispatchers.IO) {
            candidateOrderOverrideRepository.deleteByInput(normalizedInput)
            _uiState.update {
                it.copy(
                    message = context.getString(
                        R.string.candidate_order_override_saved_order_deleted
                    )
                )
            }
        }
    }

    fun deleteAllSavedOrders() {
        viewModelScope.launch(Dispatchers.IO) {
            candidateOrderOverrideRepository.deleteAll()
            _uiState.update {
                it.copy(message = context.getString(R.string.candidate_order_override_delete_all_done))
            }
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }
}
