package com.kazumaproject.markdownhelperkeyboard.converter.api

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyStyleConversionResult
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate

/**
 * AzooKey [ConversionResult](https://github.com/azooKey/AzooKeyKanaKanjiConverter) 相当。
 */
typealias ConversionResult = AzooKeyStyleConversionResult

fun ConversionResult.visibleMainCandidates(): List<Candidate> = mainResults