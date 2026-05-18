package com.example.stockwidget

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object AlertScheduler {

    private const val NAME = "stock_alerts"

    fun schedule(context: Context) {
        val req = PeriodicWorkRequestBuilder<AlertWorker>(30, TimeUnit.MINUTES).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            req
        )
    }
}
