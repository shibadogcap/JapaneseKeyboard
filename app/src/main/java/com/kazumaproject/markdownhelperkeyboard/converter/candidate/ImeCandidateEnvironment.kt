package com.kazumaproject.markdownhelperkeyboard.converter.candidate

import com.kazumaproject.markdownhelperkeyboard.converter.api.ConversionSession
import com.kazumaproject.markdownhelperkeyboard.converter.lattice.AzooKeyLatticeIncrementalState

data class AuxiliaryCandidateSourceConfig(
    val learnedPrefixMatchThreshold: Int,
    val userDictionaryPrefixMatchThreshold: Int,
    val learnedLimit: Int = 12,
    val userDictionaryLimit: Int = 12,
    val systemUserDictionaryLimit: Int = 12,
    val loudsDictionaryLimit: Int = 32,
    val loudsDictionaryMaxPrefixDepth: Int = 4,
    val emojiDictionaryLimit: Int = 16,
    val symbolDictionaryLimit: Int = 16,
    val userTemplateLimit: Int = 16,
)

/**
 * Per-request/session snapshot passed from [com.kazumaproject.markdownhelperkeyboard.ime_service.IMEService]
 * into [CandidateService]. Keeps Android UI state out of the converter domain while allowing
 * preference-driven thresholds and engine flags to vary per IME session.
 */
data class ImeCandidateEnvironment(
    val auxiliaryConfig: AuxiliaryCandidateSourceConfig,
    val isLearnDictionaryMode: Boolean,
    val romanize: (String) -> String?,
    val toHankakuAlphabet: (String) -> String,
    val onNormalBunsetsuResult: (request: CandidateRequest, result: BunsetsuCandidateResult) -> Unit = { _, _ -> },
    /** 読み prefix 拡張時の lattice node 再利用 */
    val latticeIncrementalState: AzooKeyLatticeIncrementalState? = null,
    val conversionSession: ConversionSession? = null,
)

data class CandidatePostProcessEnvironment(
    val isNgWordEnabled: Boolean,
    val ngWordPattern: Regex,
    val isOrderOverrideEnabled: Boolean,
)