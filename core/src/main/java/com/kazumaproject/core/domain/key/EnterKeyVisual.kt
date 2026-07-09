package com.kazumaproject.core.domain.key

import androidx.annotation.DrawableRes
import com.kazumaproject.core.R

/**
 * Enter/Return キーの見た目を表す共通定義。
 * TenKey / QWERTY / Sumire で InputType に応じた表示を揃えるために利用する。
 */
enum class EnterKeyVisual(@DrawableRes val drawableResId: Int) {
    ARROW(R.drawable.baseline_arrow_right_alt_24),
    RETURN(R.drawable.baseline_keyboard_return_24),
    TAB(R.drawable.keyboard_tab_24px),
    CHECK(R.drawable.baseline_check_24),
    SEARCH(R.drawable.baseline_search_24),
}

fun EnterKeyVisual.japaneseLabel(): String = when (this) {
    EnterKeyVisual.RETURN -> "改行"
    EnterKeyVisual.TAB -> "次"
    EnterKeyVisual.CHECK -> "確定"
    EnterKeyVisual.SEARCH -> "検索"
    EnterKeyVisual.ARROW -> "確定"
}

fun EnterKeyVisual.englishLabel(): String = when (this) {
    EnterKeyVisual.RETURN -> "return"
    EnterKeyVisual.TAB -> "next"
    EnterKeyVisual.CHECK -> "done"
    EnterKeyVisual.SEARCH -> "search"
    EnterKeyVisual.ARROW -> "return"
}

fun EnterKeyVisual.toSumireEnterKeyIndex(): Int = when (this) {
    EnterKeyVisual.RETURN -> 0
    EnterKeyVisual.ARROW -> 1
    EnterKeyVisual.SEARCH -> 3
    EnterKeyVisual.TAB -> 4
    EnterKeyVisual.CHECK -> 5
}
