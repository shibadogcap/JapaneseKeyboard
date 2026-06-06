package com.kazumaproject.markdownhelperkeyboard.converter.candidate

data class CandidateRequest(
    val input: String,
    val mode: CandidateRequestMode,
    val nBest: Int,
    val useUserDictionary: Boolean,
    val useUserTemplate: Boolean,
    val useRomajiCandidates: Boolean,
    val useBunsetsu: Boolean,
    val useOmissionSearch: Boolean,
    val japanesePredictionMode: AzooKeyStylePredictionMode,
    val englishPredictionMode: AzooKeyStylePredictionMode,
    val learningType: AzooKeyStyleLearningType,
    val zenzaiMode: AzooKeyStyleZenzaiMode = AzooKeyStyleZenzaiMode.Off,
    val experimentalZenzaiPredictiveInput: Boolean = false,
    val liveConversionMode: AzooKeyLiveConversionMode = AzooKeyLiveConversionMode.Disabled,
    val typoCorrectionMode: AzooKeyStyleTypoCorrectionMode,
    val privacy: CandidateRequestPrivacy = CandidateRequestPrivacy(),
    val specialCandidateProviders: List<SpecialCandidateProvider> = DefaultSpecialCandidateProviders.providers,
    val versionString: String? = null,
    val isCandidateSelectionActive: Boolean = false,
    val isConverting: Boolean = false,
    val isDirectInputMode: Boolean = false,
) {
    private val runtimePolicy: AzooKeyRuntimeConversionPolicy
        get() = AzooKeyRuntimeConversionPolicyResolver.resolve(
            AzooKeyRuntimeConversionPolicyInput(
                learningType = learningType,
                zenzaiMode = zenzaiMode,
                experimentalZenzaiPredictiveInput = experimentalZenzaiPredictiveInput,
                liveConversionMode = liveConversionMode,
                privacy = privacy,
                isComposing = input.isNotEmpty(),
                isCandidateSelectionActive = isCandidateSelectionActive,
                isConverting = isConverting,
                isDirectInputMode = isDirectInputMode,
            )
        )

    val effectiveJapanesePredictionMode: AzooKeyStylePredictionMode
        get() = if (privacy.blocksPersonalizedCandidates) {
            AzooKeyStylePredictionMode.Disabled
        } else {
            japanesePredictionMode
        }

    val effectiveEnglishPredictionMode: AzooKeyStylePredictionMode
        get() = if (privacy.suppressSuggestions) {
            AzooKeyStylePredictionMode.Disabled
        } else {
            englishPredictionMode
        }

    val effectiveLearningType: AzooKeyStyleLearningType
        get() = runtimePolicy.learningType

    val effectiveZenzaiMode: AzooKeyStyleZenzaiMode
        get() = runtimePolicy.zenzaiMode

    val shouldUseZenzaiPredictiveInput: Boolean
        get() = runtimePolicy.shouldUseZenzaiPredictiveInput

    val shouldUseLiveConversion: Boolean
        get() = runtimePolicy.shouldUseLiveConversion

    /** AzooKey [LearningType.needUsingMemory] — 予測モードとは独立 */
    val shouldReadMemoryDictionary: Boolean
        get() = runtimePolicy.shouldReadLearningMemory

    val shouldReadLearnedCandidates: Boolean
        get() = shouldReadMemoryDictionary && effectiveJapanesePredictionMode.isEnabled

    val runtimeConversionPolicy: AzooKeyRuntimeConversionPolicy
        get() = runtimePolicy

    val allowsPersonalizedConversion: Boolean
        get() = runtimePolicy.allowsPersonalizedConversion

    /**
     * AzooKey's N_best is a lattice search width, not a visible candidate cap.
     * Long composing text needs a wider lattice to keep clause and word
     * candidates from disappearing.
     */
    val effectiveSearchNBest: Int
        get() {
            val minimum = when {
                input.length >= 16 -> 64
                input.length >= 8 -> 48
                else -> 24
            }
            return nBest.coerceAtLeast(minimum).coerceAtMost(80)
        }

    fun toAzooKeyStyleOptions(): AzooKeyStyleConvertRequestOptions {
        return AzooKeyStyleConvertRequestOptions(
            nBest = effectiveSearchNBest,
            japanesePredictionMode = effectiveJapanesePredictionMode,
            englishPredictionMode = effectiveEnglishPredictionMode,
            learningType = effectiveLearningType,
            zenzaiMode = effectiveZenzaiMode,
            typoCorrectionMode = typoCorrectionMode,
            specialCandidateProviders = specialCandidateProviders,
            versionString = versionString,
        )
    }
}

enum class CandidateRequestMode {
    Normal,
    Original,
    WithoutPrediction,
    EnglishKana,
}

data class CandidateRequestPrivacy(
    val isPrivateMode: Boolean = false,
    val suppressSuggestions: Boolean = false,
) {
    val blocksPersonalizedCandidates: Boolean
        get() = isPrivateMode || suppressSuggestions
}
