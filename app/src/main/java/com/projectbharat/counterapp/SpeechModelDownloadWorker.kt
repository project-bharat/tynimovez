package com.projectbharat.counterapp

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

class SpeechModelDownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val key = inputData.getString(KEY_MODEL) ?: return Result.failure()
        if (key != "sherpa_en" && key != "sherpa_mr") return Result.failure()
        val english = applicationContext.getSharedPreferences("app_preferences", Context.MODE_PRIVATE)
            .getBoolean("english_language", false)
        val title = if (english) "AI voice setup" else "AI आवाज सेटअप"
        return try {
            val taskContext = currentCoroutineContext()
            setForeground(foregroundInfo(title, if (english) "Preparing download…" else "डाउनलोड तयार होत आहे…", 0))
            when (key) {
                "sherpa_en", "sherpa_mr" -> {
                    val manager = OfflineSpeechModelManager(applicationContext)
                    manager.download(key == "sherpa_en") { done, total ->
                        val percent = if (total > 0L) ((done * 100L) / total).toInt().coerceIn(0, 100) else 0
                        val message = if (english) "Downloading · $percent%" else "डाउनलोड · $percent%"
                        publishProgress(title, message, percent)
                    }
                }
                else -> return Result.failure()
            }
            setProgress(workDataOf("complete" to true))
            Result.success()
        } catch (error: kotlinx.coroutines.CancellationException) {
            throw error
        } catch (error: Throwable) {
            if (runAttemptCount < 4) Result.retry() else Result.failure(workDataOf("error" to (error.message ?: "Download failed")))
        }
    }

    private fun publishProgress(title: String, message: String, percent: Int) {
        runBlocking {
            setProgress(workDataOf("progress" to percent, "message" to message))
            setForeground(foregroundInfo(title, message, percent))
        }
    }

    private fun foregroundInfo(title: String, message: String, percent: Int): ForegroundInfo {
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "AI voice setup", NotificationManager.IMPORTANCE_LOW))
        }
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(message)
            .setProgress(100, percent, percent <= 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .build()
        return if (Build.VERSION.SDK_INT >= 29) {
            ForegroundInfo(NOTIFICATION_ID + keyOffset(title), notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID + keyOffset(title), notification)
        }
    }

    private fun keyOffset(title: String) = title.hashCode().and(0x7fff)

    companion object {
        const val KEY_MODEL = "model_key"
        private const val CHANNEL_ID = "offline_voice_model_downloads"
        private const val NOTIFICATION_ID = 7400
    }
}

/** Queues each voice model once, one after another, so large model downloads do not compete. */
object SpeechModelDownloadScheduler {
    private val modelKeys = listOf("sherpa_mr", "sherpa_en")

    fun enqueueAll(context: Context, wifiOnly: Boolean) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .build()
        val requests = modelKeys.map { key ->
            OneTimeWorkRequestBuilder<SpeechModelDownloadWorker>()
                .setInputData(workDataOf(SpeechModelDownloadWorker.KEY_MODEL to key))
                .setConstraints(constraints)
                .addTag(key)
                .build()
        }
        var chain = WorkManager.getInstance(context).beginUniqueWork(
            "voice-model-prefetch",
            if (wifiOnly) ExistingWorkPolicy.KEEP else ExistingWorkPolicy.REPLACE,
            requests.first()
        )
        requests.drop(1).forEach { request -> chain = chain.then(request) }
        chain.enqueue()
    }

    fun enqueueFirstInstall(context: Context) {
        val prefs = context.getSharedPreferences("speech_model_setup", Context.MODE_PRIVATE)
        // v2 migrates earlier Wi-Fi-only queues to a connected-network queue so setup
        // does not silently wait for Wi-Fi when the user needs offline voice features.
        if (!prefs.getBoolean("prefetch_connected_network_v2", false)) {
            enqueueAll(context, wifiOnly = false)
            prefs.edit()
                .putBoolean("prefetch_scheduled", true)
                .putBoolean("prefetch_connected_network_v2", true)
                .apply()
        }
    }
}
