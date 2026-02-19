package com.balsdon.managemom

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Worker that syncs device + package list to Firebase. Used for daily periodic sync.
 */
class SyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val latch = CountDownLatch(1)
            var success = false
            FirebaseSync.syncToFirebase(
                applicationContext,
                onSuccess = { success = true; latch.countDown() },
                onError = { latch.countDown() }
            )
            latch.await(30, TimeUnit.SECONDS)
            FirebaseSync.processPendingUninstalls(applicationContext)
            if (success) Result.success() else Result.retry()
        } catch (e: SecurityException) {
            Result.success()
        } catch (e: Exception) {
            Result.success()
        }
    }
}
