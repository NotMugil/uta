package com.notmugil.uta.di

import android.content.Context
import com.notmugil.uta.data.db.AlbumDao
import com.notmugil.uta.data.db.ArtistDao
import com.notmugil.uta.data.db.GenreDao
import com.notmugil.uta.data.db.HistoryDao
import com.notmugil.uta.data.db.LocalMediaDao
import com.notmugil.uta.data.db.PlaylistDao
import com.notmugil.uta.data.db.SyncActionDao
import com.notmugil.uta.data.db.SyncMetadataDao
import com.notmugil.uta.data.db.TrackDao
import com.notmugil.uta.data.db.UtaDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): UtaDatabase {
        return UtaDatabase.buildDatabase(context)
    }

    @Provides
    fun provideAlbumDao(database: UtaDatabase): AlbumDao = database.albumDao()

    @Provides
    fun provideTrackDao(database: UtaDatabase): TrackDao = database.trackDao()

    @Provides
    fun provideArtistDao(database: UtaDatabase): ArtistDao = database.artistDao()

    @Provides
    fun providePlaylistDao(database: UtaDatabase): PlaylistDao = database.playlistDao()

    @Provides
    fun provideGenreDao(database: UtaDatabase): GenreDao = database.genreDao()

    @Provides
    fun provideLocalMediaDao(database: UtaDatabase): LocalMediaDao = database.localMediaDao()

    @Provides
    fun provideSyncMetadataDao(database: UtaDatabase): SyncMetadataDao = database.syncMetadataDao()

    @Provides
    fun provideSyncActionDao(database: UtaDatabase): SyncActionDao = database.syncActionDao()

    @Provides
    fun provideHistoryDao(database: UtaDatabase): HistoryDao = database.historyDao()
}
