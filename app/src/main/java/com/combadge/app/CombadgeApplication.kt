package com.combadge.app

import android.app.Application
import android.util.Log

/**
 * Application class.
 *
 * Lightweight — the main initialization (NSD, audio) is deferred to
 * [viewmodel.CombadgeViewModel] so it happens after permissions are granted.
 */
class CombadgeApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        Log.d("CombadgeApp", "Application created")
    }
}
