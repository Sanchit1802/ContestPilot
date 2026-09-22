package com.sanchit.contestpilot.domain.logic

import java.time.Instant

/**
 * Indirection over the system clock so every time-dependent behaviour can be driven from
 * a fixed [Instant] in tests.
 */
fun interface TimeProvider {
    fun now(): Instant

    companion object {
        val system = TimeProvider { Instant.now() }

        fun fixed(instant: Instant) = TimeProvider { instant }
    }
}
