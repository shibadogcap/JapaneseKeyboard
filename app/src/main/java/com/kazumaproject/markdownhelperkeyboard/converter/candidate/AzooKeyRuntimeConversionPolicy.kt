package com.kazumaproject.markdownhelperkeyboard.converter.candidate

data class AzooKeyRuntimeConversionPolicyInput(
    val learningType: AzooKeyStyleLearningType,
    val zenzaiMode: AzooKeyStyleZenzaiMode = AzooKeyStyleZenzaiMode.Off,
    val experimentalZenzaiPredictiveInput: Boolean = false,
    val liveConversionMode: AzooKeyLiveConversionMode = AzooKeyLiveConversionMode.Disabled,
    val privacy: CandidateRequestPrivacy = CandidateRequestPrivacy(),
    val isComposing: Boolean = false,
    val isCandidateSelectionActive: Boolean = false,
    val isDirectInputMode: Boolean = false,
    val isConverting: Boolean = false,
)

data class AzooKeyRuntimeConversionPolicy(
    val allowsPersonalizedConversion: Boolean,
    val learningType: AzooKeyStyleLearningType,
    val zenzaiMode: AzooKeyStyleZenzaiMode,
    val experimentalZenzaiPredictiveInput: Boolean,
    val liveConversionMode: AzooKeyLiveConversionMode,
) {
    val shouldReadLearningMemory: Boolean
        get() = learningType.shouldUse

    val shouldWriteLearningMemory: Boolean
        get() = learningType.shouldUpdate

    val shouldUseZenzai: Boolean
        get() = zenzaiMode.isEnabled

    val shouldUseZenzaiPredictiveInput: Boolean
        get() = shouldUseZenzai && experimentalZenzaiPredictiveInput

    val shouldUseLiveConversion: Boolean
        get() = liveConversionMode.isEnabled
}

object AzooKeyRuntimeConversionPolicyResolver {
    fun resolve(input: AzooKeyRuntimeConversionPolicyInput): AzooKeyRuntimeConversionPolicy {
        val blocksPersonalizedPaths = input.privacy.blocksPersonalizedCandidates
        val blocksSuggestions = input.privacy.suppressSuggestions
        val zenzaiMode = if (blocksPersonalizedPaths) {
            AzooKeyStyleZenzaiMode.Off
        } else {
            input.zenzaiMode
        }
        val liveConversionMode = if (
            blocksSuggestions ||
            input.isDirectInputMode ||
            input.isCandidateSelectionActive ||
            input.isConverting ||
            !input.isComposing
        ) {
            AzooKeyLiveConversionMode.Disabled
        } else {
            input.liveConversionMode
        }
        val learningType = when {
            blocksSuggestions || input.learningType == AzooKeyStyleLearningType.Nothing ->
                AzooKeyStyleLearningType.Nothing
            blocksPersonalizedPaths ->
                AzooKeyStyleLearningType.OnlyOutput
            else -> input.learningType
        }
        return AzooKeyRuntimeConversionPolicy(
            allowsPersonalizedConversion = !blocksPersonalizedPaths && !blocksSuggestions,
            learningType = learningType,
            zenzaiMode = zenzaiMode,
            experimentalZenzaiPredictiveInput = input.experimentalZenzaiPredictiveInput &&
                zenzaiMode.isEnabled &&
                !blocksPersonalizedPaths &&
                !blocksSuggestions,
            liveConversionMode = liveConversionMode,
        )
    }
}

enum class AzooKeyLiveConversionMode {
    Disabled,
    Enabled;

    val isEnabled: Boolean
        get() = this == Enabled
}
