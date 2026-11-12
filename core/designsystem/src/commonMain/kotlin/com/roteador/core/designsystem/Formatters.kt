package com.roteador.core.designsystem

import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToLong
import kotlin.time.Duration

/** pt-BR number and unit formatting, without platform locale APIs (common code). */
object Format {
    fun decimal(value: Double, decimals: Int = 1): String {
        val factor = 10.0.pow(decimals)
        val rounded = (value * factor).roundToLong()
        val sign = if (rounded < 0) "-" else ""
        val absolute = abs(rounded)
        val whole = absolute / factor.toLong()
        if (decimals == 0) return sign + whole
        val fraction = (absolute % factor.toLong()).toString().padStart(decimals, '0')
        return "$sign$whole,$fraction"
    }

    /** 312_400_000.0 -> "312 Mbit/s", 1_250_000.0 -> "1,3 Mbit/s", 900.0 -> "0,9 kbit/s". */
    fun bitrate(bitsPerSecond: Double): String = when {
        bitsPerSecond >= 100e6 -> "${decimal(bitsPerSecond / 1e6, 0)} Mbit/s"
        bitsPerSecond >= 1e6 -> "${decimal(bitsPerSecond / 1e6, 1)} Mbit/s"
        else -> "${decimal(bitsPerSecond / 1e3, 1)} kbit/s"
    }

    /** 1621408 s -> "18 d 18 h", 3700 s -> "1 h 1 min", 59 s -> "59 s". */
    fun duration(duration: Duration): String {
        val totalSeconds = duration.inWholeSeconds
        val days = totalSeconds / 86_400
        val hours = (totalSeconds % 86_400) / 3_600
        val minutes = (totalSeconds % 3_600) / 60
        return when {
            days > 0 -> "$days d $hours h"
            hours > 0 -> "$hours h $minutes min"
            minutes > 0 -> "$minutes min"
            else -> "$totalSeconds s"
        }
    }

    fun dbm(value: Double): String = "${decimal(value, 1)} dBm"

    fun dbm(value: Int): String = "$value dBm"
}
