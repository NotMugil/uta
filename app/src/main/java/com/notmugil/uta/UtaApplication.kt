package com.notmugil.uta

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import coil3.request.crossfade
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.android.internal.managers.ApplicationComponentManager
import dagger.hilt.android.internal.modules.ApplicationContextModule
import dagger.hilt.internal.GeneratedComponentManagerHolder
import timber.log.Timber

@HiltAndroidApp
class UtaApplication : Application(), GeneratedComponentManagerHolder, SingletonImageLoader.Factory {

    private val hiltComponentManager = ApplicationComponentManager {
        DaggerUtaApplication_HiltComponents_SingletonC.builder()
            .applicationContextModule(ApplicationContextModule(this))
            .build()
    }

    override fun componentManager(): ApplicationComponentManager = hiltComponentManager

    override fun generatedComponent(): Any = hiltComponentManager.generatedComponent()

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        return ImageLoader.Builder(context)
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, 0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("image_cache"))
                    .maxSizeBytes(250L * 1024 * 1024) // 250MB
                    .build()
            }
            .crossfade(true)
            .build()
    }

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
    }
}
