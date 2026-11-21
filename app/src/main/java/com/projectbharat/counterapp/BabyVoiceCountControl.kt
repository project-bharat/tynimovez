package com.projectbharat.counterapp

import android.Manifest
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.RepeatMode
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicLong

// A process-wide lock prevents overlapping microphone/model engines when the active session changes.
private val voiceEngineMutex = Mutex()
private val voiceRunGeneration = AtomicLong(0L)

private val microphoneRetryDelays = longArrayOf(500, 1_000, 2_000, 4_000, 8_000)

@Composable
fun BabyVoiceCountControl(
    autoStart: Boolean = false,
    onAutoStartConsumed: () -> Unit = {},
    activeSessionId: Long,
    english: Boolean,
    onCount: (Int) -> Unit,
    onRecognitionState: (Boolean, String, String?, Int?) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val manager = remember(context) { OfflineSpeechModelManager(context.applicationContext) }
    val callback = rememberUpdatedState(onCount)
    val stateCallback = rememberUpdatedState(onRecognitionState)
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val runGeneration = voiceRunGeneration
    val engineMutex = voiceEngineMutex
    var listening by remember(activeSessionId) { mutableStateOf(false) }
    var startRequest by remember(activeSessionId) { mutableStateOf(0L) }
    var permissionRequestPending by remember(activeSessionId) { mutableStateOf(false) }
    LaunchedEffect(activeSessionId, autoStart) {
        if (autoStart) {
            listening = true
            onAutoStartConsumed()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permissionRequestPending = false
        if (granted) {
            listening = true
        } else {
            listening = false
            stateCallback.value(false, if (english) "Microphone permission denied" else "मायक्रोफोन परवानगी नाकारली", "", null)
        }
    }

    val currentEnglish by rememberUpdatedState(english)
    DisposableEffect(lifecycleOwner, activeSessionId) {
        val observer = LifecycleEventObserver { _, event ->
            // Android's runtime permission sheet can emit ON_STOP. Do not cancel the
            // pending start while that system dialog is visible.
            if (event == Lifecycle.Event.ON_STOP && listening && !permissionRequestPending) {
                runGeneration.incrementAndGet()
                listening = false
                stateCallback.value(false, if (currentEnglish) "Voice count is off" else "आवाज मोजणी बंद", "", null)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(listening, startRequest, activeSessionId, english) {
        if (!listening) return@LaunchedEffect
        val runId = runGeneration.incrementAndGet()
        val runContext = currentCoroutineContext()
        fun isCurrentRun() = runContext.isActive && runGeneration.get() == runId
        stateCallback.value(false, if (english) "Starting voice recognition…" else "आवाज ओळख सुरू होत आहे…", "", 0)
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            listening = false
            permissionRequestPending = true
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return@LaunchedEffect
        }

        try {
            engineMutex.withLock {
                stateCallback.value(false, if (english) "Checking offline model…" else "ऑफलाइन मॉडेल तपासत आहे…", "", 0)
                val files = withContext(Dispatchers.IO) {
                    manager.installedFiles(english) ?: manager.download(english) { done, total ->
                        val percent = if (total > 0) ((done * 100) / total).toInt().coerceIn(0, 100) else 0
                        mainHandler.post {
                            if (isCurrentRun()) stateCallback.value(false,
                                if (english) "Downloading offline model… $percent%" else "ऑफलाइन मॉडेल डाउनलोड होत आहे… $percent%",
                                "", null)
                        }
                    }
                }
                check(files.language == if (english) "en" else "mr") { "The downloaded speech model does not match the selected app language" }
                stateCallback.value(false, if (english) "Initializing voice engine…" else "आवाज इंजिन सुरू करत आहे…", "", 0)

                var retries = 0
                while (true) {
                    val stopped = CompletableDeferred<Throwable>()
                    val engine = SherpaOfflineSpeechEngine(
                        context = context,
                        modelFiles = files,
                        english = english,
                        onTranscript = { phrase, detectedCount ->
                            if (isCurrentRun()) stateCallback.value(true, if (english) "Listening offline…" else "ऑफलाइन ऐकत आहे…", phrase, detectedCount)
                        },
                        onCount = { amount -> if (isCurrentRun()) callback.value(amount) },
                        onStatus = { status -> if (isCurrentRun()) stateCallback.value(status.contains("Listening", true) || status.contains("ऐकत आहे", true), status, null, null) },
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
                        if (retries == microphoneRetryDelays.size) throw error
                        if (isCurrentRun()) {
                            stateCallback.value(false,
                                if (english) "Reconnecting microphone…" else "मायक्रोफोन पुन्हा जोडत आहे…",
                                "", 0)
                        }
                    } finally {
                        // The worker retains microphone ownership if this bounded wait expires.
                        withContext(NonCancellable + Dispatchers.IO) { engine.stop() }
                    }
                    delay(microphoneRetryDelays[retries++])
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            if (isCurrentRun()) {
                val message = when (error) {
                    is SecurityException -> if (english) "Microphone permission required" else "मायक्रोफोन परवानगी आवश्यक"
                    is MicrophoneUnavailable -> if (english) "Microphone unavailable. Tap Voice count to retry." else "मायक्रोफोन उपलब्ध नाही. पुन्हा आवाज मोजा दाबा."
                    else -> if (english) "Offline voice failed. Tap Voice count to retry." else "ऑफलाइन आवाज ओळख अयशस्वी. पुन्हा आवाज मोजा दाबा."
                }
                android.util.Log.w("BabyVoiceControl", "Voice counting stopped", error)
                stateCallback.value(false, message, null, null)
                listening = false
            }
        } finally {
            runGeneration.compareAndSet(runId, runId + 1)
        }
    }

    Button(
        onClick = {
            // Invalidate any queued transcript/progress callback at the exact tap,
            // not only when Compose later restarts the effect.
            runGeneration.incrementAndGet()
            if (listening) {
                // Close the panel synchronously on an explicit stop. The next tap
                // opens it synchronously again, without waiting for LaunchedEffect.
                listening = false
                stateCallback.value(false, if (english) "Voice count is off" else "आवाज मोजणी बंद", "", null)
            } else {
                stateCallback.value(true, if (english) "Starting voice recognition…" else "आवाज ओळख सुरू होत आहे…", "", 0)
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                    startRequest++
                    listening = true
                } else {
                    stateCallback.value(true, if (english) "Microphone permission needed" else "मायक्रोफोन परवानगी आवश्यक", "", 0)
                    permissionRequestPending = true
                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            }
        },
        modifier = Modifier.height(40.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 5.dp),
        colors = ButtonDefaults.buttonColors(containerColor = if (listening) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary),
        shape = RoundedCornerShape(12.dp)
    ) {
        Icon(if (listening) Icons.Default.MicOff else Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.size(5.dp))
        Text(if (listening) (if (english) "Stop" else "बंद") else (if (english) "Voice count" else "आवाज मोजा"), style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}

@Composable
fun BabyVoiceRecognitionPanel(listening: Boolean, english: Boolean, status: String, transcript: String, detectedCount: Int) {
    val pulse = rememberInfiniteTransition(label = "babyVoicePulse")
    val scale by pulse.animateFloat(1f, 1.15f, infiniteRepeatable(tween(650), RepeatMode.Reverse), label = "babyVoiceMic")
    val loading = status.contains("Reconnecting", true) || status.contains("पुन्हा जोडत", true) || status.contains("Starting", true) || status.contains("Checking", true) || status.contains("Preparing", true) || status.contains("Loading", true) || status.contains("Downloading", true) ||
        status.contains("तपास", true) || status.contains("डाउनलोड", true) || status.contains("लोड होत", true)
    val shortStatus = when {
        status.contains("busy", true) -> if (english) "Microphone busy" else "मायक्रोफोन व्यस्त"
        status.equals("Microphone unavailable", true) -> if (english) "Microphone unavailable" else "मायक्रोफोन उपलब्ध नाही"
        status.contains("Listening offline", true) -> if (english) "Listening" else "ऐकत आहे"
        status.contains("ऑफलाइन ऐकत") -> "ऐकत आहे"
        else -> status
    }
    Surface(
        Modifier.fillMaxWidth().padding(top = 8.dp),
        shape = RoundedCornerShape(14.dp),
        color = if (listening) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        Icons.Default.Mic,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp).graphicsLayer(
                            scaleX = if (listening) scale else 1f,
                            scaleY = if (listening) scale else 1f
                        ),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        if (listening) "LIVE RECOGNITION" else if (english) "VOICE RECOGNITION" else "आवाज ओळख",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (loading) CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                    Text(
                        shortStatus.ifBlank { if (listening) (if (english) "Listening" else "ऐकत आहे") else (if (english) "Voice count is off" else "आवाज मोजणी बंद") },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (listening && !loading) Color(0xFF16A34A) else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2
                    )
                }
            }
            Spacer(Modifier.height(9.dp))
            Text(
                transcript.ifBlank { if (english) "Say a number from one to ten" else "एक ते दहा अंक बोला" },
                style = MaterialTheme.typography.bodyMedium,
                color = if (transcript.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .75f) else MaterialTheme.colorScheme.onSurface,
                maxLines = 2
            )
            if (detectedCount > 0) {
                Spacer(Modifier.height(7.dp))
                Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = .12f)) {
                    Text(
                        "+$detectedCount",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
