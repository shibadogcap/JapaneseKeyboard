package com.kazumaproject.markdownhelperkeyboard.converter.candidate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AzooKeyRuntimeConversionPolicyTest {
    @Test
    fun privateSessionKeepsExistingLearningButStopsNewWrites() {
        val policy = AzooKeyRuntimeConversionPolicyResolver.resolve(
            AzooKeyRuntimeConversionPolicyInput(
                learningType = AzooKeyStyleLearningType.InputAndOutput,
                zenzaiMode = AzooKeyStyleZenzaiMode.On,
                experimentalZenzaiPredictiveInput = true,
                liveConversionMode = AzooKeyLiveConversionMode.Enabled,
                privacy = CandidateRequestPrivacy(isPrivateMode = true),
                isComposing = true,
            )
        )

        assertEquals(AzooKeyStyleLearningType.OnlyOutput, policy.learningType)
        assertEquals(AzooKeyStyleZenzaiMode.Off, policy.zenzaiMode)
        assertFalse(policy.allowsPersonalizedConversion)
        assertTrue(policy.shouldReadLearningMemory)
        assertFalse(policy.shouldWriteLearningMemory)
        assertFalse(policy.shouldUseZenzaiPredictiveInput)
        assertTrue(policy.shouldUseLiveConversion)
    }

    @Test
    fun privateSessionWithLearningDisabledStaysDisabled() {
        val policy = AzooKeyRuntimeConversionPolicyResolver.resolve(
            AzooKeyRuntimeConversionPolicyInput(
                learningType = AzooKeyStyleLearningType.Nothing,
                privacy = CandidateRequestPrivacy(isPrivateMode = true),
                isComposing = true,
            )
        )

        assertEquals(AzooKeyStyleLearningType.Nothing, policy.learningType)
        assertFalse(policy.shouldReadLearningMemory)
        assertFalse(policy.shouldWriteLearningMemory)
    }

    @Test
    fun publicComposingSessionCanUseRequestedRuntimeFeatures() {
        val policy = AzooKeyRuntimeConversionPolicyResolver.resolve(
            AzooKeyRuntimeConversionPolicyInput(
                learningType = AzooKeyStyleLearningType.InputAndOutput,
                zenzaiMode = AzooKeyStyleZenzaiMode.On,
                experimentalZenzaiPredictiveInput = true,
                liveConversionMode = AzooKeyLiveConversionMode.Enabled,
                isComposing = true,
            )
        )

        assertTrue(policy.shouldReadLearningMemory)
        assertTrue(policy.allowsPersonalizedConversion)
        assertTrue(policy.shouldWriteLearningMemory)
        assertTrue(policy.shouldUseZenzai)
        assertTrue(policy.shouldUseZenzaiPredictiveInput)
        assertTrue(policy.shouldUseLiveConversion)
    }

    @Test
    fun liveConversionIsDisabledWhileSelectingCandidate() {
        val policy = AzooKeyRuntimeConversionPolicyResolver.resolve(
            AzooKeyRuntimeConversionPolicyInput(
                learningType = AzooKeyStyleLearningType.OnlyOutput,
                liveConversionMode = AzooKeyLiveConversionMode.Enabled,
                isComposing = true,
                isCandidateSelectionActive = true,
            )
        )

        assertFalse(policy.shouldUseLiveConversion)
        assertTrue(policy.shouldReadLearningMemory)
    }

    @Test
    fun suppressedSuggestionsDisableZenzaiPredictiveInput() {
        val policy = AzooKeyRuntimeConversionPolicyResolver.resolve(
            AzooKeyRuntimeConversionPolicyInput(
                learningType = AzooKeyStyleLearningType.OnlyOutput,
                zenzaiMode = AzooKeyStyleZenzaiMode.On,
                experimentalZenzaiPredictiveInput = true,
                liveConversionMode = AzooKeyLiveConversionMode.Enabled,
                privacy = CandidateRequestPrivacy(suppressSuggestions = true),
                isComposing = true,
            )
        )

        assertEquals(AzooKeyStyleLearningType.Nothing, policy.learningType)
        assertEquals(AzooKeyStyleZenzaiMode.Off, policy.zenzaiMode)
        assertFalse(policy.allowsPersonalizedConversion)
        assertFalse(policy.shouldUseZenzaiPredictiveInput)
        assertFalse(policy.shouldUseLiveConversion)
    }
}
