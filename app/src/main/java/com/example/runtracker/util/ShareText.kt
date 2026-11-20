package com.example.runtracker.util

import com.example.runtracker.data.SessionEntity
import com.example.runtracker.data.SplitEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ShareText {

    fun build(session: SessionEntity, splits: List<SplitEntity>): String {
        val date = SimpleDateFormat("EEEE, MMM d yyyy \u00b7 HH:mm", Locale.getDefault())
            .format(Date(session.startEpochMillis))

        return buildString {
            appendLine("\uD83C\uDFC3 Run \u2013 $date")
            appendLine()
            appendLine("Distance: ${Formatters.distance(session.distanceMeters)}")
            appendLine("Time: ${Formatters.duration(session.durationMillis)}")
            appendLine("Avg pace: ${Formatters.pace(session.avgPaceSecPerKm)}")
            if (session.goalMeters != null) {
                val status = if (session.goalWasReached) "reached \u2713" else "not reached"
                appendLine("Goal: ${Formatters.distance(session.goalMeters)} ($status)")
            }
            if (splits.isNotEmpty()) {
                appendLine()
                appendLine("Splits:")
                splits.forEach { s ->
                    appendLine(
                        "${s.splitIndex}. ${Formatters.distance(s.distanceMeters)} \u2013 " +
                            "${Formatters.duration(s.durationMillis)} \u2013 ${Formatters.pace(s.paceSecPerKm)}"
                    )
                }
            }
        }.trimEnd()
    }
}
