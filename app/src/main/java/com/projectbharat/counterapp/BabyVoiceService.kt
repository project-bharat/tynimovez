package com.projectbharat.counterapp

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.projectbharat.counterapp.data.CounterDatabase
import com.projectbharat.counterapp.data.CounterRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext

internal data class BabyVoiceState(
    val sessionId: Long? = null,
    val english: Boolean = false,
    val active: Boolean = false,
    val listening: Boolean = false,
    val status: String = "",
    val total: Int = 0,
    val categoryId: Long? = null,
    val transcript: String = "",
    val latestCatch: Int? = null
)

class BabyVoiceService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val dao by lazy { CounterDatabase.get(this).dao() }
    private var sessionJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action != ACTION_START) {
            sessionJob?.cancel()
            stopSelf()
            return START_NOT_STICKY
        }
        val id = intent.getLongExtra(EXTRA_SESSION, -1)
        val english = intent.getBooleanExtra(EXTRA_ENGLISH, false)
        val current = state.value
        if (current.active && current.sessionId == id && current.english == english) return START_NOT_STICKY

        starts.value += 1
        val previous = sessionJob
        previous?.cancel()
        mutableState.value = BabyVoiceState(id, english, active = true, status = startingText(english),
            total = if (current.sessionId == id) current.total else 0)
        try {
            ensureChannel()
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(state.value),
                if (Build.VERSION.SDK_INT >= 30) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0)
            BabySessionNotification.cancel(this)
            QuickActionsNotification.cancel(this)
        } catch (error: Exception) {
            Log.w(TAG, "Could not start foreground microphone service", error)
            fail(voiceFailure(english, error))
            return START_NOT_STICKY
        }

        sessionJob = scope.launch {
            try {
                previous?.join()
                runSession(id, english)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.w(TAG, "Voice counting stopped", error)
                fail(voiceFailure(english, error))
            }
        }
        return START_NOT_STICKY
    }

    private suspend fun runSession(id: Long, english: Boolean) = coroutineScope {
        val session = dao.babySession(id)
        val remaining = session?.let { it.startedAt + CounterRepository.TWO_HOURS_MS - System.currentTimeMillis() }
        if (session == null || session.endedAt != null || remaining == null || remaining <= 0) {
            stopSelf()
            return@coroutineScope
        }

        val wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "tynimovez:voice-count")
        wakeLock.setReferenceCounted(false)
        wakeLock.acquire(remaining.coerceAtMost(CounterRepository.TWO_HOURS_MS))
        val counts = Channel<Int>(Channel.UNLIMITED)
        try {
            update { it.copy(total = session.movementCount, categoryId = session.categoryId) }
            launch {
                delay(remaining)
                CounterRepository(dao).completeExpiredBabySession(id)
                QuickActionsNotification.showIfIdle(this@BabyVoiceService)
                stopSelf()
            }
            launch {
                dao.observeBabySession(id).collect { record ->
                    if (record == null || record.endedAt != null || record.startedAt != session.startedAt) {
                        QuickActionsNotification.showIfIdle(this@BabyVoiceService)
                        stopSelf()
                    } else {
                        update { it.copy(total = record.movementCount) }
                    }
                }
            }
            launch {
                for (amount in counts) {
                    val saved = dao.addBabyMovementsAtomically(id, System.currentTimeMillis(),
                        CounterRepository.TWO_HOURS_MS, amount, null, expectedStart = session.startedAt)
                    if (!saved) {
                        stopSelf()
                        break
                    }
                    update { it.copy(latestCatch = amount) }
                }
            }
            listen(english) { counts.trySend(it) }
        } finally {
            counts.cancel()
            if (wakeLock.isHeld) wakeLock.release()
        }
    }

    private suspend fun listen(english: Boolean, onCount: (Int) -> Unit): Unit = coroutineScope {
        val manager = OfflineSpeechModelManager(applicationContext)
        val files = withContext(Dispatchers.IO) {
            manager.installedFiles(english) ?: manager.download(english) { _, _ -> }
        }
        check(files.language == if (english) "en" else "mr")
        var retries = 0
        while (true) {
            val stopped = CompletableDeferred<Throwable>()
            val engine = SherpaOfflineSpeechEngine(
                context = applicationContext,
                modelFiles = files,
                english = english,
                previewRequested = { mutableState.subscriptionCount.value > 0 },
                onTranscript = { phrase, _ ->
                    if (isActive && state.value.active) mutableState.value = state.value.copy(transcript = phrase)
                },
                onCount = { if (isActive) onCount(it) },
                onStatus = { status ->
                    if (isActive) update {
                        val listening = status.startsWith("Listening") || status.startsWith("ऑफलाइन ऐकत")
                        it.copy(listening = listening, status = status)
                    }
                },
                onStopped = { stopped.complete(it) }
            )
            var startedAt: Long? = null
            try {
                runInterruptible(Dispatchers.IO) { engine.start() }
                startedAt = SystemClock.elapsedRealtime()
                throw stopped.await()
            } catch (error: MicrophoneUnavailable) {
                val start = startedAt
                if (start != null && SystemClock.elapsedRealtime() - start >= 30_000) retries = 0
                if (retries == retryDelays.size) throw error
                update { it.copy(listening = false,
                    status = if (english) "Reconnecting microphone…" else "मायक्रोफोन पुन्हा जोडत आहे…") }
            } finally {
                withContext(NonCancellable + Dispatchers.IO) { engine.stop() }
            }
            delay(retryDelays[retries++])
        }
    }

    private fun fail(message: String) {
        mutableState.value = state.value.copy(active = false, listening = false, status = message)
        stopSelf()
    }

    private fun update(transform: (BabyVoiceState) -> BabyVoiceState) {
        val previous = state.value
        val next = transform(previous)
        if (!previous.active || previous == next) return
        mutableState.value = next
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(next))
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL, "AI voice counting", NotificationManager.IMPORTANCE_LOW))
        }
    }

    private fun notification(state: BabyVoiceState): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java).setAction(MainActivity.ACTION_OPEN_COUNTER)
            .putExtra(CounterQuickActions.EXTRA_COUNTER_KIND, "BABY")
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stop = PendingIntent.getService(this, 0, Intent(this, BabyVoiceService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE)
        val total = if (state.english) "Current count: ${state.total}" else "सध्याची मोजणी: ${state.total}"
        val last = state.latestCatch?.let { if (state.english) " · Last +$it" else " · शेवटचे +$it" }.orEmpty()
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle(if (state.english) "AI voice counting" else "AI आवाज मोजणी")
            .setContentText(total + last)
            .setSubText(state.status)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(open)
            .apply {
                val sessionId = state.sessionId
                val categoryId = state.categoryId
                if (sessionId != null && categoryId != null) {
                    addAction(android.R.drawable.ic_input_add, if (state.english) "+1 Add" else "+१ जोडा",
                        BabySessionNotification.actionPending(this@BabyVoiceService, sessionId, categoryId,
                            CounterQuickActions.ACTION_BABY_ADD, 7111))
                }
            }
            .addAction(0, if (state.english) "Stop" else "बंद", stop)
            .build()
    }

    override fun onDestroy() {
        val ended = state.value
        scope.cancel()
        if (state.value.active) mutableState.value = BabyVoiceState()
        stopForeground(STOP_FOREGROUND_REMOVE)
        val app = applicationContext
        ended.sessionId?.let { id ->
            CoroutineScope(Dispatchers.Main).launch {
                val record = CounterDatabase.get(app).dao().babySession(id)
                if (record != null && record.endedAt == null &&
                    System.currentTimeMillis() < record.startedAt + CounterRepository.TWO_HOURS_MS) {
                    BabySessionNotification.show(app, id, record.categoryId, record.movementCount, ended.english)
                } else QuickActionsNotification.showIfIdle(app)
            }
        }
        super.onDestroy()
    }

    companion object {
        private const val TAG = "BabyVoiceService"
        private const val CHANNEL = "baby_voice_count"
        private const val NOTIFICATION_ID = 7106
        private const val ACTION_START = "com.projectbharat.counterapp.START_VOICE"
        private const val ACTION_STOP = "com.projectbharat.counterapp.STOP_VOICE"
        private const val EXTRA_SESSION = "session"
        private const val EXTRA_ENGLISH = "english"
        private val retryDelays = longArrayOf(500, 1_000, 2_000, 4_000, 8_000)
        private val mutableState = MutableStateFlow(BabyVoiceState())
        private val starts = MutableStateFlow(0L)
        internal val voiceStarts = starts.asStateFlow()
        internal val state = mutableState.asStateFlow()

        internal fun start(context: Context, sessionId: Long, english: Boolean) {
            ContextCompat.startForegroundService(context, startIntent(context, sessionId, english))
        }

        internal fun notificationStart(context: Context, sessionId: Long, english: Boolean): PendingIntent? {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) !=
                PackageManager.PERMISSION_GRANTED) return null
            val intent = startIntent(context, sessionId, english)
            val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            return if (Build.VERSION.SDK_INT >= 26) PendingIntent.getForegroundService(context, 7114, intent, flags)
            else PendingIntent.getService(context, 7114, intent, flags)
        }

        private fun startIntent(context: Context, sessionId: Long, english: Boolean) =
            Intent(context, BabyVoiceService::class.java).setAction(ACTION_START)
                .putExtra(EXTRA_SESSION, sessionId).putExtra(EXTRA_ENGLISH, english)

        internal fun stop(context: Context) {
            context.stopService(Intent(context, BabyVoiceService::class.java))
        }

        internal fun startingText(english: Boolean) =
            if (english) "Preparing voice counting…" else "आवाज मोजणी तयार होत आहे…"

        internal fun voiceFailure(english: Boolean, error: Exception): String = when (error) {
            is SecurityException -> if (english) "Allow microphone access to start." else "सुरू करण्यासाठी मायक्रोफोनची परवानगी द्या."
            is MicrophoneUnavailable -> if (english) "Microphone unavailable. Tap AI voice to retry." else "मायक्रोफोन उपलब्ध नाही. पुन्हा AI आवाज दाबा."
            else -> if (english) "Voice counting couldn't start or stopped. Tap AI voice to retry." else "आवाज मोजणी थांबली. पुन्हा AI आवाज दाबा."
        }
    }
}
