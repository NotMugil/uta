package com.notmugil.uta.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sync_actions",
    indices = [
        Index(value = ["serverId", "status"]),
        Index(value = ["serverId", "createdAt"]),
        Index(value = ["serverId", "targetId", "actionType"])
    ]
)
data class SyncActionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val serverId: String,
    val actionType: String,
    val targetId: String,
    val targetType: String? = null,
    val payloadJson: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val status: String = "PENDING",
    val retryCount: Int = 0,
    val lastError: String? = null
)
