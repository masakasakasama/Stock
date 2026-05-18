package com.example.stockwidget

import android.app.Application

class App : Application() {

    override fun onCreate() {
        super.onCreate()

        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            CrashLog.append(this, "uncaught on ${thread.name}", throwable)
            previous?.uncaughtException(thread, throwable)
        }

        AlertScheduler.schedule(this)
    }
}
