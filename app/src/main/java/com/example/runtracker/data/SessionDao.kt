package com.example.runtracker.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

data class SessionWithSplits(
    val session: SessionEntity,
    val splits: List<SplitEntity>
)

@Dao
interface SessionDao {

    @Insert
    suspend fun insertSession(session: SessionEntity): Long

    @Insert
    suspend fun insertSplits(splits: List<SplitEntity>)

    @Transaction
    suspend fun insertSessionWithSplits(session: SessionEntity, splits: List<SplitEntity>): Long {
        val sessionId = insertSession(session)
        if (splits.isNotEmpty()) {
            insertSplits(splits.map { it.copy(sessionId = sessionId) })
        }
        return sessionId
    }

    @Query("SELECT * FROM sessions ORDER BY startEpochMillis DESC")
    fun observeAllSessions(): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions WHERE id = :sessionId")
    suspend fun getSession(sessionId: Long): SessionEntity?

    @Query("SELECT * FROM splits WHERE sessionId = :sessionId ORDER BY splitIndex ASC")
    suspend fun getSplitsForSession(sessionId: Long): List<SplitEntity>

    @Query("DELETE FROM sessions WHERE id = :sessionId")
    suspend fun deleteSession(sessionId: Long)
}
