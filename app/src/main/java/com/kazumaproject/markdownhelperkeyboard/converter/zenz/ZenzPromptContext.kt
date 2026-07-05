package com.kazumaproject.markdownhelperkeyboard.converter.zenz

/** AzooKey [ZenzaiV3DependentMode](https://github.com/azooKey/AzooKeyKanaKanjiConverter) 相当のプロンプト条件。 */
data class ZenzPromptContext(
    val profile: String = "",
    val topic: String = "",
    val style: String = "",
    val preference: String = "",
    val leftContext: String = "",
    val rightContext: String = "",
)
