package com.notmugil.uta.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {

    @Query("SELECT * FROM history_entries WHERE serverId = :serverId ORDER BY playedAt DESC LIMIT 500")
    fun getHistory(serverId: String): Flow<List<HistoryEntryEntity>>

    @Query("SELECT * FROM history_entries WHERE serverId = :serverId ORDER BY playedAt DESC LIMIT 1")
    suspend fun getMostRecent(serverId: String): HistoryEntryEntity?

    @Insert
    suspend fun insert(entry: HistoryEntryEntity)

    @Query("DELETE FROM history_entries WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM history_entries WHERE serverId = :serverId")
    suspend fun deleteAll(serverId: String)

    @Query(
        """
        DELETE FROM history_entries WHERE serverId = :serverId AND id NOT IN (
            SELECT id FROM history_entries WHERE serverId = :serverId ORDER BY playedAt DESC LIMIT :keepCount
        )
        """
    )
    suspend fun trimOldEntries(serverId: String, keepCount: Int = 500)
}
