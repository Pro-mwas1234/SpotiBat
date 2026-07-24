package com.project.lol

import android.app.Application
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.perf.FirebasePerformance

class SpotilolApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this

        val analytics = FirebaseAnalytics.getInstance(this)
        FirebaseCrashlytics.getInstance()
        FirebasePerformance.getInstance()

        val params = Bundle().apply {
            putString(FirebaseAnalytics.Param.SCREEN_NAME, "Spotilol")
            putString(FirebaseAnalytics.Param.SCREEN_CLASS, "SpotilolApplication")
        }
        analytics.logEvent(FirebaseAnalytics.Event.APP_OPEN, params)
    }

    companion object {
        lateinit var instance: SpotilolApplication
            private set
    }
}
