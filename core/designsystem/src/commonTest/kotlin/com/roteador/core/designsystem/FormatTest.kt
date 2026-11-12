package com.roteador.core.designsystem

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

class FormatTest {
    @Test
    fun formatsDecimalsWithComma() {
        assertEquals("-25,9", Format.decimal(-25.850267, 1))
        assertEquals("0,05", Format.decimal(0.049, 2))
        assertEquals("3", Format.decimal(2.6, 0))
    }

    @Test
    fun formatsBitrates() {
        assertEquals("312 Mbit/s", Format.bitrate(312_400_000.0))
        assertEquals("1,3 Mbit/s", Format.bitrate(1_250_000.0))
        assertEquals("0,9 kbit/s", Format.bitrate(900.0))
    }

    @Test
    fun formatsDurations() {
        assertEquals("18 d 18 h", Format.duration(1_621_408.seconds))
        assertEquals("1 h 1 min", Format.duration(3_700.seconds))
        assertEquals("59 s", Format.duration(59.seconds))
    }
}
