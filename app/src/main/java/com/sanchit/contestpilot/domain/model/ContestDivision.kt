package com.sanchit.contestpilot.domain.model

/**
 * Division of a Codeforces round, derived from the contest name.
 *
 * A division is *not* evidence that a contest is rated: the Codeforces API exposes no
 * rated flag on its `Contest` object, so ContestPilot never claims to know it.
 */
enum class ContestDivision(val displayName: String) {
    DIV_1("Div. 1"),
    DIV_2("Div. 2"),
    DIV_3("Div. 3"),
    DIV_4("Div. 4"),
    DIV_1_2("Div. 1 + Div. 2"),
    OTHER("Other");

    companion object {
        /** Divisions the user can individually enable for auto-registration. */
        val selectable: List<ContestDivision> =
            listOf(DIV_1, DIV_2, DIV_3, DIV_4, DIV_1_2)

        fun fromStorageValue(value: String): ContestDivision? =
            entries.firstOrNull { it.name == value }
    }
}
