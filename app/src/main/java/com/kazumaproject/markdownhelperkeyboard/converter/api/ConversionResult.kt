package com.kazumaproject.markdownhelperkeyboard.converter.api

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyStyleConversionResult
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CandidateLanePresentation

/**
 * AzooKey [ConversionResult](https://github.com/azooKey/AzooKeyKanaKanjiConverter) 相当。
 */
typealias ConversionResult = AzooKeyStyleConversionResult

fun ConversionResult.visibleMainCandidates(): List<Candidate> = mainResults

fun ConversionResult.displayCandidates(): List<Candidate> =
    CandidateLanePresentation.mergeForDisplay(mainResults, supplementaryCandidates)

fun ConversionResult.liveConversionCandidates(): List<Candidate> =
    CandidateLanePresentation.forLiveConversion(mainResults)