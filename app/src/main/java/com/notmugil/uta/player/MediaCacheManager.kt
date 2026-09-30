package com.notmugil.uta.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import timber.log.Timber
import java.io.File

@OptIn(UnstableApi::class)
object MediaCacheManager {
    private var simpleCache: SimpleCache? = null
    private const val MAX_CACHE_BYTES = 512 * 1024 * 1024L // 512MB LRU audio cache

    @Synchronized
    fun getCache(context: Context): SimpleCache? {
        return try {
            simpleCache ?: run {
                val cacheDir = File(context.cacheDir, "media3_audio_cache")
                val databaseProvider = StandaloneDatabaseProvider(context.applicationContext)
                val evictor = LeastRecentlyUsedCacheEvictor(MAX_CACHE_BYTES)
                SimpleCache(cacheDir, evictor, databaseProvider).also {
                    simpleCache = it
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "[MediaCache] Failed to initialize SimpleCache")
            null
        }
    }

    fun createCacheDataSourceFactory(context: Context): androidx.media3.datasource.DataSource.Factory {
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("Uta/1.0 (Android; Subsonic Client)")
            .setConnectTimeoutMs(20_000)
            .setReadTimeoutMs(30_000)
            .setAllowCrossProtocolRedirects(true)
        val defaultDataSourceFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)

        val cache = getCache(context) ?: return defaultDataSourceFactory

        return CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(defaultDataSourceFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    @Synchronized
    fun clearCache() {
        try {
            simpleCache?.keys?.forEach { key ->
                simpleCache?.removeResource(key)
            }
        } catch (e: Exception) {
            Timber.e(e, "[MediaCache] Failed to clear audio cache")
        }
    }
}
