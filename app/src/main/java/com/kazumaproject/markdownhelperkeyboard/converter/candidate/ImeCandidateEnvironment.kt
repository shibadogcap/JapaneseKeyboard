package com.kazumaproject.markdownhelperkeyboard.converter.candidate

import com.kazumaproject.markdownhelperkeyboard.converter.lattice.AzooKeyLatticeIncrementalState

/**
 * Per-request/session snapshot passed from [com.kazumaproject.markdownhelperkeyboard.ime_service.IMEService]
 * into [CandidateService]. Keeps Android UI state out of the converter domain while allowing
 * preference-driven thresholds and engine flags to vary per IME session.
 */
data class ImeCandidateEnvironment(
    val auxiliaryConfig: AuxiliaryCandidateSourceConfig,
    val systemEngineConfig: SystemKanaKanjiEngineSourceConfig,
    val systemDictionarySourcePolicy: SystemDictionarySourcePolicy? = null,
    val isLearnDictionaryMode: Boolean,
    val romanize: (String) -> String?,
    val toHankakuAlphabet: (String) -> String,
    val onNormalBunsetsuResult: (request: CandidateRequest, result: BunsetsuCandidateResult) -> Unit = { _, _ -> },
    /** 読み prefix 拡張時の lattice node 再利用（[com.kazumaproject.markdownhelperkeyboard.converter.api.ConversionSession] と共有可） */
    val latticeIncrementalState: AzooKeyLatticeIncrementalState? = null,
)

data class CandidatePostProcessEnvironment(
    val isNgWordEnabled: Boolean,
    val ngWordPattern: Regex,
    val isOrderOverrideEnabled: Boolean,
)