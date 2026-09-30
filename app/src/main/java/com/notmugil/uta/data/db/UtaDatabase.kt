package com.notmugil.uta.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        AlbumEntity::class,
        TrackEntity::class,
        ArtistEntity::class,
        PlaylistEntity::class,
        PlaylistTrackCrossRef::class,
        GenreEntity::class,
        LocalMediaEntity::class,
        DownloadScopeEntity::class,
        DownloadQueueEntity::class,
        SyncMetadataEntity::class,
        SyncActionEntity::class,
        HistoryEntryEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class UtaDatabase : RoomDatabase() {
    abstract fun albumDao(): AlbumDao
    abstract fun trackDao(): TrackDao
    abstract fun artistDao(): ArtistDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun genreDao(): GenreDao
    abstract fun localMediaDao(): LocalMediaDao
    abstract fun syncMetadataDao(): SyncMetadataDao
    abstract fun syncActionDao(): SyncActionDao
    abstract fun historyDao(): HistoryDao

    companion object {
        const val DB_NAME = "uta_library.db"

        fun buildDatabase(context: Context): UtaDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                UtaDatabase::class.java,
                DB_NAME
            )
                .fallbackToDestructiveMigration(dropAllTables = true)
                .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
                .build()
        }
    }
}
