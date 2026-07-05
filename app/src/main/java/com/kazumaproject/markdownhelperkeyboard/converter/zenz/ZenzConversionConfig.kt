package com.kazumaproject.markdownhelperkeyboard.converter.zenz

data class ZenzConversionConfig(
    val profile: String = "",
    val topic: String = "",
    val style: String = "",
    val preference: String = "",
    val maxTokens: Int = 32,
    val rerankEnabled: Boolean = false,
    val rerankTopK: Int = 4,
    val rerankBaseWeight: Float = 0.7f,
    val rerankZenzWeight: Float = 0.3f,
    val useRightContextFallback: Boolean = false,
    val hasHardwareKeyboard: Boolean = false,
)

data class ZenzContextRequest(
    val insertReading: String,
    val lastCandidateLength: Int = 0,
    val liveConversionEnabled: Boolean = false,
)

data class ZenzRerankRequest(
    val insertReading: String,
    val candidates: List<com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate>,
    val leftContext: String,
    val config: ZenzConversionConfig,
    val cursorPosition: Int? = null,
)

data class ZenzPredictiveRequest(
    val insertReading: String,
    val dictionaryCandidates: List<com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate>,
    val leftContext: String,
    val nBest: Int,
    val config: ZenzConversionConfig,
    val cursorPosition: Int? = null,
) {
    val topDictionaryCandidate: String
        get() = dictionaryCandidates.firstOrNull()?.string.orEmpty()
}

data class ZenzGenerationRequest(
    val insertReading: String,
    val leftContext: String,
    val config: ZenzConversionConfig,
    val cursorPosition: Int? = null,
)