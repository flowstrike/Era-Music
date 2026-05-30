package com.spyou.eramusic

import android.app.Application
import android.content.Context

class EraApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/** Convenience accessor for the app-wide [AppContainer]. */
val Context.appContainer: AppContainer
    get() = (applicationContext as EraApp).container
