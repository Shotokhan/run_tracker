package com.example.runtracker.util

import kotlin.math.roundToInt

object Formatters {

    /** e.g. "1.34 km" or "480 m" for sub-kilometer distances */
    fun distance(meters: Double): String =
        if (meters >= 1000) String.format("%.2f km", meters / 1000.0)
        else "${meters.roundToInt()} m"

    /** e.g. "23:07" for under an hour, "1:03:12" for an hour or more */
    fun duration(millis: Long): String {
        val totalSeconds = millis / 1000
        val h = totalSeconds / 3600
        val m = (totalSeconds % 3600) / 60
        val s = totalSeconds % 60
        return if (h > 0) String.format("%d:%02d:%02d", h, m, s)
        else String.format("%02d:%02d", m, s)
    }

    /** e.g. "5:42 /km". Returns "--:-- /km" when pace is not yet available. */
    fun pace(secPerKm: Double): String {
        if (secPerKm <= 0 || secPerKm.isNaN() || secPerKm.isInfinite()) return "--:-- /km"
        val totalSeconds = secPerKm.roundToInt()
        val m = totalSeconds / 60
        val s = totalSeconds % 60
        return String.format("%d:%02d /km", m, s)
    }
}
