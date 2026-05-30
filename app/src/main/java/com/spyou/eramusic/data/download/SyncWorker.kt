package com.spyou.eramusic.data.download

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class SyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as com.spyou.eramusic.EraApp).container
        return try {
            container.syncOrchestrator.syncAll()
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}
