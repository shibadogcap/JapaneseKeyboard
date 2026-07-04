package com.kazumaproject.markdownhelperkeyboard.converter.candidate

/** AzooKey [CompleteAction](https://github.com/azooKey/AzooKeyKanaKanjiConverter) 相当。 */
sealed interface CandidateCompleteAction {
    data class MoveCursor(val offset: Int) : CandidateCompleteAction
}

fun Candidate.withActions(actions: List<CandidateCompleteAction>): Candidate = copy(actions = actions)

fun Candidate.applyAppropriateActions(): Candidate = withActions(AzooKeyAppropriateActions.forCandidate(this))

object AzooKeyAppropriateActions {
    private val moveCursorMinusOne = setOf(
        "[]", "()", "｛｝", "〈〉", "〔〕", "（）", "「」", "『』", "【】",
        "{}", "<>", "《》", "\"\"", "''", "””",
    )
    private val moveCursorMinusTwo = setOf("{{}}")

    fun forCandidate(candidate: Candidate): List<CandidateCompleteAction> {
        return when (candidate.string) {
            in moveCursorMinusOne -> listOf(CandidateCompleteAction.MoveCursor(-1))
            in moveCursorMinusTwo -> listOf(CandidateCompleteAction.MoveCursor(-2))
            else -> emptyList()
        }
    }
}
