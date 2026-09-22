package com.sanchit.contestpilot.domain.model

/**
 * Lifecycle phase of a contest.
 *
 * The Codeforces values map one-to-one onto the `phase` enum documented at
 * https://codeforces.com/apiHelp/objects. CodeChef exposes no phase field; its contests
 * are bucketed by the source endpoint into future/present/past lists, which map onto
 * [BEFORE], [CODING] and [FINISHED] respectively.
 */
enum class ContestPhase {
    BEFORE,
    CODING,
    PENDING_SYSTEM_TEST,
    SYSTEM_TEST,
    FINISHED,
    UNKNOWN;

    companion object {
        fun fromCodeforces(value: String?): ContestPhase =
            entries.firstOrNull { it.name == value } ?: UNKNOWN
    }
}
