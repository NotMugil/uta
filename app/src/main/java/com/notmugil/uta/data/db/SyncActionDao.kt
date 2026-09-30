package com.notmugil.uta.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface SyncActionDao {
    @Upsert
    suspend fun upsertAction(action: SyncActionEntity): Long

    @Query("SELECT * FROM sync_actions WHERE serverId = :serverId AND status = 'PENDING' ORDER BY createdAt ASC")
    suspend fun getPendingActions(serverId: String): List<SyncActionEntity>

    @Query("DELETE FROM sync_actions WHERE id = :id")
    suspend fun deleteAction(id: Long)

    @Query("DELETE FROM sync_actions WHERE id IN (:ids)")
    suspend fun deleteActions(ids: List<Long>)

    @Query(
        "DELETE FROM sync_actions WHERE serverId = :serverId AND targetId = :targetId AND actionType IN (:actionTypes)"
    )
    suspend fun deleteConflictingActions(serverId: String, targetId: String, actionTypes: List<String>)
}
