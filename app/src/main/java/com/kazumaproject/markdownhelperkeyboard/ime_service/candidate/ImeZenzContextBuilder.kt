package com.kazumaproject.markdownhelperkeyboard.ime_service.candidate

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyRuntimeConversionPolicy
import com.kazumaproject.markdownhelperkeyboard.converter.zenz.ZenzConversionConfig
import com.kazumaproject.markdownhelperkeyboard.ime_service.ImePreferencesSnapshot

/**
 * [ImeCandidateZenzContext] を snapshot + runtime policy から組み立てる。
 */
object ImeZenzContextBuilder {
    fun build(
        snapshot: ImePreferencesSnapshot?,
        policy: AzooKeyRuntimeConversionPolicy,
        config: ZenzConversionConfig,
        leftContext: String,
        hasHardwareKeyboard: Boolean,
        fallbackZenzEnabled: Boolean,
        fallbackZenzRerankEnabled: Boolean,
        fallbackNBest: Int,
    ): ImeCandidateZenzContext {
        val zenzOn = snapshot?.zenzEnableStatePreference ?: fallbackZenzEnabled
        val zenzRerankOn = snapshot?.zenzRerankPreference ?: fallbackZenzRerankEnabled
        val nBestCount = snapshot?.nBest ?: fallbackNBest
        return ImeCandidateZenzContext(
            config = config,
            leftContext = leftContext,
            zenzEnabled = zenzOn && !hasHardwareKeyboard,
            zenzaiEvaluationEnabled = zenzOn && zenzRerankOn && policy.shouldUseZenzai,
            asyncGenerationEnabled = zenzOn &&
                !hasHardwareKeyboard &&
                policy.allowsPersonalizedConversion,
            rerankEnabled = zenzRerankOn,
            nBest = nBestCount,
        )
    }
}