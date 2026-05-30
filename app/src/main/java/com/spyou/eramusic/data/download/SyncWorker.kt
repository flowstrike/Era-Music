package com.spyou.eramusic.data.download

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class SyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "SyncWorker"
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "doWork() started")
        val container = (applicationContext as com.spyou.eramusic.EraApp).container
        return try {
            container.syncOrchestrator.syncAll()
            Log.d(TAG, "doWork() completed successfully")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "doWork() failed", e)
            Result.retry()
        }
    }
}
