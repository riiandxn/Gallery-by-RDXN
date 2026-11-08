package com.rdxn.gallery

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/** Jalan di latar belakang saat ada foto baru (walau aplikasi tertutup), lalu mendaftar ulang dirinya. */
class IndexWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        try {
            Indexer(applicationContext, Faces.get(applicationContext)).run()
        } catch (e: Exception) {
            // diabaikan; akan dicoba lagi pada foto baru berikutnya
        }
        IndexScheduler.schedule(applicationContext)
        return Result.success()
    }
}

object IndexScheduler {
    fun schedule(ctx: Context) {
        val constraints = Constraints.Builder()
            .addContentUriTrigger(MediaRepo.IMAGES, true)
            .setTriggerContentUpdateDelay(5, TimeUnit.SECONDS)
            .setTriggerContentMaxDelay(30, TimeUnit.SECONDS)
            .build()
        val req = OneTimeWorkRequestBuilder<IndexWorker>().setConstraints(constraints).build()
        WorkManager.getInstance(ctx).enqueueUniqueWork("index-photos", ExistingWorkPolicy.REPLACE, req)
    }
}
