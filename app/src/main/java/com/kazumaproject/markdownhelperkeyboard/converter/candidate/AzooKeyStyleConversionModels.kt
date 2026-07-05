package com.kazumaproject.markdownhelperkeyboard.converter.candidate

data class AzooKeyStyleConversionResult(
    val mainResults: List<Candidate>,
    val predictionResults: List<Candidate> = emptyList(),
    val englishPredictionResults: List<Candidate> = emptyList(),
    val firstClauseResults: List<Candidate> = emptyList(),
    /** AzooKey supplementaryCandidates（絵文字・記号などライブ変換対象外） */
    val supplementaryCandidates: List<Candidate> = emptyList(),
)

data class AzooKeyStyleConvertRequestOptions(
    val nBest: Int = 10,
    val japanesePredictionMode: AzooKeyStylePredictionMode = AzooKeyStylePredictionMode.AutoMix,
    val englishPredictionMode: AzooKeyStylePredictionMode = AzooKeyStylePredictionMode.Disabled,
    val learningType: AzooKeyStyleLearningType = AzooKeyStyleLearningType.InputAndOutput,
    val zenzaiMode: AzooKeyStyleZenzaiMode = AzooKeyStyleZenzaiMode.Off,
    val typoCorrectionMode: AzooKeyStyleTypoCorrectionMode = AzooKeyStyleTypoCorrectionMode.Automatic,
    val fullWidthRomanCandidate: Boolean = false,
    val halfWidthKanaCandidate: Boolean = false,
    val specialCandidateProviders: List<SpecialCandidateProvider> = DefaultSpecialCandidateProviders.providers,
    val versionString: String? = null,
)

enum class AzooKeyStylePredictionMode {
    AutoMix,
    ManualMix,
    Disabled;

    val isEnabled: Boolean
        get() = this != Disabled

    val shouldMix: Boolean
        get() = this == AutoMix
}

enum class AzooKeyStyleLearningType {
    InputAndOutput,
    OnlyOutput,
    Nothing;

    val shouldUpdate: Boolean
        get() = this == InputAndOutput

    val shouldUse: Boolean
        get() = this != Nothing
}

enum class AzooKeyStyleZenzaiMode {
    Off,
    On;

    val isEnabled: Boolean
        get() = this == On
}

enum class AzooKeyStyleTypoCorrectionMode {
    Automatic,
    Enabled,
    Disabled
}
