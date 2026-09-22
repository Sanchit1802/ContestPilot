package com.sanchit.contestpilot.domain

import com.sanchit.contestpilot.domain.logic.ContestClassifier
import com.sanchit.contestpilot.domain.model.ContestDivision
import org.junit.Assert.assertEquals
import org.junit.Test

class ContestClassifierTest {

    @Test
    fun `combined division is matched before the individual divisions`() {
        assertEquals(
            ContestDivision.DIV_1_2,
            ContestClassifier.classify("Codeforces Round 999 (Div. 1 + Div. 2)")
        )
    }

    @Test
    fun `combined division is recognised with alternative separators`() {
        listOf(
            "Codeforces Round 999 (Div. 1 + Div. 2)",
            "Codeforces Round 999 (Div. 1 & Div. 2)",
            "Codeforces Round 999 (Div 1 and Div 2)",
            "Codeforces Round 999 (Div.1/Div.2)"
        ).forEach { name ->
            assertEquals(name, ContestDivision.DIV_1_2, ContestClassifier.classify(name))
        }
    }

    @Test
    fun `individual divisions are recognised`() {
        assertEquals(
            ContestDivision.DIV_1,
            ContestClassifier.classify("Codeforces Round 999 (Div. 1)")
        )
        assertEquals(
            ContestDivision.DIV_2,
            ContestClassifier.classify("Codeforces Round 999 (Div. 2)")
        )
        assertEquals(
            ContestDivision.DIV_3,
            ContestClassifier.classify("Codeforces Round 999 (Div. 3)")
        )
        assertEquals(
            ContestDivision.DIV_4,
            ContestClassifier.classify("Codeforces Round 999 (Div. 4)")
        )
    }

    @Test
    fun `classification ignores case and optional punctuation`() {
        assertEquals(ContestDivision.DIV_3, ContestClassifier.classify("Some round (div 3)"))
        assertEquals(ContestDivision.DIV_2, ContestClassifier.classify("SOME ROUND (DIV.2)"))
    }

    @Test
    fun `a contest without a division is classified as other`() {
        assertEquals(
            ContestDivision.OTHER,
            ContestClassifier.classify("Educational Codeforces Round 999")
        )
    }

    @Test
    fun `a round number containing a division digit is not mistaken for a division`() {
        assertEquals(
            ContestDivision.OTHER,
            ContestClassifier.classify("Codeforces Round 1234")
        )
    }
}
