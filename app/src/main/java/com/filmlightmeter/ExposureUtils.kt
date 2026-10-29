package com.filmlightmeter

import java.util.Locale
import kotlin.math.abs
import kotlin.math.log2
import kotlin.math.roundToInt

object ExposureUtils {
    // Механические выдержки до 1 секунды; свыше 1 с в режиме Bulb важнее точный секундомер
    val MECHANICAL_SHUTTERS = listOf(
        1.0 / 8000, 1.0 / 4000, 1.0 / 2000, 1.0 / 1000, 1.0 / 500,
        1.0 / 250, 1.0 / 125, 1.0 / 60, 1.0 / 30, 1.0 / 15,
        1.0 / 8, 1.0 / 4, 1.0 / 2, 1.0
    )

    val STANDARD_APERTURES = listOf(
        1.2, 1.4, 1.8, 2.0, 2.8, 3.5, 4.0, 4.5, 5.6, 8.0, 11.0, 16.0, 22.0, 32.0
    )

    val STANDARD_ISOS = listOf(25, 50, 100, 125, 160, 200, 400, 800, 1600, 3200, 6400)

    val FILTER_COMPENSATIONS = listOf(0f, 1f, 1.5f, 2f, 3f, 6f, 10f)

    fun quantizeShutter(rawSeconds: Double): Double {
        if (rawSeconds <= 0.0) return 0.0
        // Если выдержка длиннее 1 секунды (Bulb), не обрезаем её и не теряем точность
        if (rawSeconds > 1.0) return rawSeconds
        return MECHANICAL_SHUTTERS.minByOrNull { abs(log2(it) - log2(rawSeconds)) } ?: rawSeconds
    }

    fun formatShutter(seconds: Double): String {
        if (seconds <= 0.0) return "--"
        if (seconds < 1.0 / 8000.0) return "<1/8000"
        return when {
            seconds < 0.75 -> "1/${(1.0 / seconds).roundToInt()}"
            seconds < 10.0 -> {
                val r = (seconds * 10.0).roundToInt() / 10.0
                if (r % 1.0 == 0.0) "${r.toInt()}s" else String.format(Locale.US, "%.1fs", r)
            }
            seconds < 60.0 -> "${seconds.roundToInt()}s"
            seconds < 3600.0 -> {
                val totalSec = seconds.roundToInt()
                val m = totalSec / 60
                val s = totalSec % 60
                if (s == 0) "${m}m" else "${m}m ${s}s"
            }
            else -> {
                val totalMin = (seconds / 60.0).roundToInt()
                val h = totalMin / 60
                val m = totalMin % 60
                if (m == 0) "${h}h" else "${h}h ${m}m"
            }
        }
    }
}
