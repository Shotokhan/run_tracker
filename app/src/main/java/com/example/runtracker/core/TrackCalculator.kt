package com.example.runtracker.core

import android.location.Location

data class Split(
    val index: Int,
    val distanceMeters: Double,
    val durationMillis: Long,
    val paceSecPerKm: Double
)

data class TrackSnapshot(
    val totalDistanceMeters: Double = 0.0,
    val elapsedMillis: Long = 0L,
    val currentPaceSecPerKm: Double = 0.0,
    val avgPaceSecPerKm: Double = 0.0,
    val splits: List<Split> = emptyList(),
    val goalMeters: Double? = null,
    val goalReached: Boolean = false,
    val gpsLocked: Boolean = false
)

/**
 * Turns a stream of raw GPS fixes into distance / pace / split statistics.
 *
 * Design notes:
 * - Fixes with poor accuracy (weak signal) are ignored entirely rather than
 *   used, since a single bad fix can otherwise inject a large fake jump in distance.
 * - Fixes closer together than [minDisplacementMeters] are ignored too, since GPS jitter
 *   while standing still or moving slowly otherwise accumulates as fake distance.
 * - Instantaneous pace is smoothed over a rolling time window rather than computed
 *   from only the last two fixes, since GPS noise makes point-to-point pace unstable.
 */
class TrackCalculator(
    private val splitDistanceMeters: Double = 1000.0,
    private val minAccuracyMeters: Float = 20f,
    private val minDisplacementMeters: Float = 3f,
    private val paceWindowMillis: Long = 15_000L
) {
    private var lastAccepted: Location? = null
    private var startTimeMillis: Long = 0L
    private var totalDistance = 0.0
    private var nextSplitAt = splitDistanceMeters
    private var splitStartTime = 0L
    private var splitStartDistance = 0.0
    private val splitsList = mutableListOf<Split>()

    // rolling window of (timestamp, cumulativeDistance) used for instantaneous pace
    private val recentWindow = ArrayDeque<Pair<Long, Double>>()

    var goalMeters: Double? = null
        private set
    private var goalReachedFlag = false
    private var goalEventPending = false

    fun start(nowMillis: Long, goalMeters: Double? = null) {
        startTimeMillis = nowMillis
        splitStartTime = nowMillis
        splitStartDistance = 0.0
        nextSplitAt = splitDistanceMeters
        totalDistance = 0.0
        lastAccepted = null
        splitsList.clear()
        recentWindow.clear()
        this.goalMeters = goalMeters
        goalReachedFlag = false
        goalEventPending = false
    }

    /** Extends the goal and clears the "reached" flag so tracking continues. */
    fun snoozeGoal(extraMeters: Double) {
        goalMeters = (goalMeters ?: totalDistance) + extraMeters
        goalReachedFlag = false
        goalEventPending = false
    }

    fun onLocation(location: Location, nowMillis: Long): TrackSnapshot {
        if (location.accuracy <= minAccuracyMeters) {
            val prev = lastAccepted
            if (prev == null) {
                lastAccepted = location
                recentWindow.addLast(nowMillis to totalDistance)
            } else {
                val delta = prev.distanceTo(location)
                if (delta >= minDisplacementMeters) {
                    totalDistance += delta
                    lastAccepted = location
                    recentWindow.addLast(nowMillis to totalDistance)
                    while (recentWindow.isNotEmpty() && nowMillis - recentWindow.first().first > paceWindowMillis) {
                        recentWindow.removeFirst()
                    }
                    checkSplit(nowMillis)
                    checkGoal()
                }
            }
        }
        return snapshot(nowMillis, gpsLocked = true)
    }

    /** Call periodically (e.g. every second) even without a new fix, to keep the elapsed timer live. */
    fun heartbeat(nowMillis: Long): TrackSnapshot = snapshot(nowMillis, gpsLocked = lastAccepted != null)

    private fun checkSplit(nowMillis: Long) {
        while (totalDistance >= nextSplitAt) {
            val duration = nowMillis - splitStartTime
            val dist = nextSplitAt - splitStartDistance
            val pace = paceSecPerKm(duration, dist)
            splitsList.add(Split(splitsList.size + 1, dist, duration, pace))
            splitStartTime = nowMillis
            splitStartDistance = nextSplitAt
            nextSplitAt += splitDistanceMeters
        }
    }

    private fun checkGoal() {
        val g = goalMeters
        if (g != null && !goalReachedFlag && totalDistance >= g) {
            goalReachedFlag = true
            goalEventPending = true
        }
    }

    /** Returns true once, the first time this call observes a newly-reached goal. */
    fun consumeGoalReachedEvent(): Boolean {
        if (goalEventPending) {
            goalEventPending = false
            return true
        }
        return false
    }

    /** The last, incomplete split when a session is stopped mid-split. Null if no partial distance. */
    fun finalPartialSplit(nowMillis: Long): Split? {
        val dist = totalDistance - splitStartDistance
        if (dist <= 0.5) return null
        val duration = nowMillis - splitStartTime
        return Split(splitsList.size + 1, dist, duration, paceSecPerKm(duration, dist))
    }

    fun allSplits(): List<Split> = splitsList.toList()

    fun totalDistanceMeters(): Double = totalDistance

    private fun paceSecPerKm(durationMillis: Long, distanceMeters: Double): Double =
        if (distanceMeters > 0) (durationMillis / 1000.0) / (distanceMeters / 1000.0) else 0.0

    private fun instantPace(): Double {
        if (recentWindow.size < 2) return 0.0
        val (t0, d0) = recentWindow.first()
        val (t1, d1) = recentWindow.last()
        val dtSec = (t1 - t0) / 1000.0
        val dKm = (d1 - d0) / 1000.0
        return if (dKm > 0 && dtSec > 0) dtSec / dKm else 0.0
    }

    private fun snapshot(nowMillis: Long, gpsLocked: Boolean): TrackSnapshot {
        val elapsed = if (startTimeMillis == 0L) 0L else nowMillis - startTimeMillis
        val avgPace = if (totalDistance > 0) (elapsed / 1000.0) / (totalDistance / 1000.0) else 0.0
        return TrackSnapshot(
            totalDistanceMeters = totalDistance,
            elapsedMillis = elapsed,
            currentPaceSecPerKm = instantPace(),
            avgPaceSecPerKm = avgPace,
            splits = splitsList.toList(),
            goalMeters = goalMeters,
            goalReached = goalReachedFlag,
            gpsLocked = gpsLocked
        )
    }
}
