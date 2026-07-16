package com.example.runtracker.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startEpochMillis: Long,
    val endEpochMillis: Long,
    val distanceMeters: Double,
    val durationMillis: Long,
    val avgPaceSecPerKm: Double,
    val goalMeters: Double?,
    val goalWasReached: Boolean
)
