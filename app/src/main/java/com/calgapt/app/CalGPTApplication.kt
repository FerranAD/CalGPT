package com.calgapt.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

@HiltAndroidApp
class CalGPTApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Initialize Timber or other libraries here
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
    }
}
