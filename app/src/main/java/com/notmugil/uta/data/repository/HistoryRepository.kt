package com.notmugil.uta.data.repository

import com.notmugil.uta.data.SubsonicRepository
import com.notmugil.uta.data.db.HistoryDao
import com.notmugil.uta.data.db.toDomain
import com.notmugil.uta.data.db.toHistoryEntity
import com.notmugil.uta.domain.model.HistoryItem
import com.notmugil.uta.domain.model.TrackItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HistoryRepository @Inject constructor(
    private val historyDao: HistoryDao,
    private val subsonicRepository: SubsonicRepository
) {

    fun getHistory(): Flow<List<HistoryItem>> {
        val serverId = subsonicRepository.currentServerId
        return historyDao.getHistory(serverId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    suspend fun recordPlay(track: TrackItem) {
        val serverId = subsonicRepository.currentServerId
        val recent = historyDao.getMostRecent(serverId)
        if (recent != null &&
            recent.trackId == track.id &&
            (System.currentTimeMillis() - recent.playedAt) < 5_000
        ) {
            return
        }
        historyDao.insert(track.toHistoryEntity(serverId))
        historyDao.trimOldEntries(serverId, keepCount = 500)
    }

    suspend fun removeEntry(id: Long) {
        historyDao.deleteById(id)
    }

    suspend fun clearHistory() {
        val serverId = subsonicRepository.currentServerId
        historyDao.deleteAll(serverId)
    }
}
