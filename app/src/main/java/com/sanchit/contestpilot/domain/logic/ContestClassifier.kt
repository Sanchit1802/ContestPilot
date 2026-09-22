package com.sanchit.contestpilot.domain.logic

import com.sanchit.contestpilot.domain.model.ContestDivision

/**
 * Derives a [ContestDivision] from a Codeforces contest name.
 *
 * The combined "Div. 1 + Div. 2" form is matched first so it is never mistaken for a
 * plain Div. 1 round. Classification says nothing about whether a contest is rated.
 */
object ContestClassifier {

    private val combinedDivisionPattern =
        Regex("""\bdiv\.?\s*1\s*(?:\+|&|and|/)\s*div\.?\s*2\b""", RegexOption.IGNORE_CASE)

    private val divisionPatterns = listOf(
        ContestDivision.DIV_1 to Regex("""\bdiv\.?\s*1\b""", RegexOption.IGNORE_CASE),
        ContestDivision.DIV_2 to Regex("""\bdiv\.?\s*2\b""", RegexOption.IGNORE_CASE),
        ContestDivision.DIV_3 to Regex("""\bdiv\.?\s*3\b""", RegexOption.IGNORE_CASE),
        ContestDivision.DIV_4 to Regex("""\bdiv\.?\s*4\b""", RegexOption.IGNORE_CASE)
    )

    fun classify(name: String): ContestDivision {
        if (combinedDivisionPattern.containsMatchIn(name)) {
            return ContestDivision.DIV_1_2
        }

        return divisionPatterns
            .firstOrNull { (_, pattern) -> pattern.containsMatchIn(name) }
            ?.first
            ?: ContestDivision.OTHER
    }
}
