package com.notmugil.uta

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.android.internal.managers.ApplicationComponentManager
import dagger.hilt.android.internal.modules.ApplicationContextModule
import dagger.hilt.internal.GeneratedComponentManagerHolder
import timber.log.Timber

@HiltAndroidApp
class UtaApplication : Application(), GeneratedComponentManagerHolder {

    private val hiltComponentManager = ApplicationComponentManager {
        DaggerUtaApplication_HiltComponents_SingletonC.builder()
            .applicationContextModule(ApplicationContextModule(this))
            .build()
    }

    override fun componentManager(): ApplicationComponentManager = hiltComponentManager

    override fun generatedComponent(): Any = hiltComponentManager.generatedComponent()

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
    }
}
