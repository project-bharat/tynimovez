package com.projectbharat.counterapp

import android.Manifest
import android.media.AudioManager
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
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
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import java.util.concurrent.atomic.AtomicLong

private fun microphoneBusy(context: android.content.Context): Boolean {
    val audio = context.getSystemService(AudioManager::class.java) ?: return false
    if (audio.mode == AudioManager.MODE_IN_CALL) return true
    if (android.os.Build.VERSION.SDK_INT >= 24) {
        return runCatching { audio.activeRecordingConfigurations.isNotEmpty() }.getOrDefault(false)
    }
    return false
}

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
    val runGeneration = remember(activeSessionId) { AtomicLong(0L) }
    val engineMutex = remember(activeSessionId) { Mutex() }
    var listening by remember(activeSessionId) { mutableStateOf(false) }
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
            if (microphoneBusy(context)) {
                stateCallback.value(false, if (english) "Microphone busy — a call or another app is using it" else "मायक्रोफोन व्यस्त — कॉल किंवा दुसरे अॅप वापरत आहे", "", null)
                listening = false
            } else {
                stateCallback.value(false, if (english) "Starting voice recognition…" else "आवाज ओळख सुरू होत आहे…", "", 0)
                listening = true
            }
        } else {
            listening = false
            stateCallback.value(false, if (english) "Microphone permission denied" else "मायक्रोफोन परवानगी नाकारली", "", null)
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            // Android's runtime permission sheet can emit ON_STOP. Do not cancel the
            // pending start while that system dialog is visible.
            if (event == Lifecycle.Event.ON_STOP && !permissionRequestPending) listening = false
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(listening, activeSessionId, english) {
        // Every language/start-stop transition invalidates callbacks from the previous engine.
        val runId = runGeneration.incrementAndGet()
        if (!listening) {
            stateCallback.value(false, if (english) "Voice count is off" else "आवाज मोजणी बंद", "", null)
            return@LaunchedEffect
        }

        // Publish the opening state before waiting for cleanup, model download, or
        // microphone acquisition. This makes the recognition panel appear immediately,
        // including during rapid start/stop and language changes.
        stateCallback.value(false, if (english) "Starting voice recognition…" else "आवाज ओळख सुरू होत आहे…", "", 0)
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            listening = false
            permissionRequestPending = true
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return@LaunchedEffect
        }

        // Serialize engines: the next language cannot claim the microphone until the old
        // engine has released AudioRecord and its native recognizer.
        engineMutex.lock()
        var engine: SherpaOfflineSpeechEngine? = null
        try {
            // Check for external microphone use only after the prior engine has fully
            // stopped and released AudioRecord. This avoids false "busy" on rapid restart.
            if (microphoneBusy(context)) {
                stateCallback.value(false, if (english) "Microphone busy — a call or another app is using it" else "मायक्रोफोन व्यस्त — कॉल किंवा दुसरे अॅप वापरत आहे", "", null)
                listening = false
                return@LaunchedEffect
            }
            stateCallback.value(false, if (english) "Checking offline model…" else "ऑफलाइन मॉडेल तपासत आहे…", "", 0)
            val files = withContext(Dispatchers.IO) {
                manager.installedFiles(english) ?: manager.download(english) { done, total ->
                    val percent = if (total > 0) ((done * 100) / total).toInt().coerceIn(0, 100) else 0
                    mainHandler.post {
                        if (runGeneration.get() == runId) stateCallback.value(false,
                            if (english) "Downloading offline model… $percent%" else "ऑफलाइन मॉडेल डाउनलोड होत आहे… $percent%",
                            "", null)
                    }
                }
            }
            check(files.language == if (english) "en" else "mr") { "The downloaded speech model does not match the selected app language" }
            stateCallback.value(false, if (english) "Initializing voice engine…" else "आवाज इंजिन सुरू करत आहे…", "", 0)
            engine = SherpaOfflineSpeechEngine(
                modelFiles = files,
                english = english,
                onTranscript = { phrase, detectedCount ->
                    if (runGeneration.get() == runId) stateCallback.value(true, if (english) "Listening offline…" else "ऑफलाइन ऐकत आहे…", phrase, detectedCount)
                },
                onCount = { amount -> if (runGeneration.get() == runId) callback.value(amount) },
                onStatus = { status -> if (runGeneration.get() == runId) stateCallback.value(status.contains("Listening", true) || status.contains("ऐकत आहे", true), status, null, null) },
                onStopped = { error ->
                    if (runGeneration.get() == runId) {
                        stateCallback.value(false, error ?: if (english) "Voice count stopped" else "आवाज मोजणी थांबली", null, null)
                        listening = false
                    }
                }
            )
            // start() now waits for actual native initialization and mic acquisition,
            // or reports a bounded timeout/error instead of leaving the UI stuck.
            withContext(Dispatchers.IO) { engine.start() }
            awaitCancellation()
        } catch (error: kotlinx.coroutines.CancellationException) {
            throw error
        } catch (error: Throwable) {
            val detail=error.message.orEmpty()
            val message=if(detail.contains("busy",true)) (if(english) "Microphone busy — a call or another app is using it" else "मायक्रोफोन व्यस्त — कॉल किंवा दुसरे अॅप वापरत आहे") else if(detail.contains("microphone unavailable",true)) (if(english) "Microphone unavailable" else "मायक्रोफोन उपलब्ध नाही") else "Offline voice setup failed: " + (detail.ifBlank { "try again" })
            stateCallback.value(false, message, null, null)
            listening = false
        } finally {
            // LaunchedEffect is cancelled on language changes. Cleanup must outlive that
            // cancellation so the previous AudioRecord/model is fully stopped before restart.
            try {
                withContext(NonCancellable + Dispatchers.IO) { engine?.stop() }
            } finally {
                // Never strand the mutex if native shutdown throws; subsequent toggles
                // must be able to attempt a fresh engine instance.
                if (engineMutex.isLocked) engineMutex.unlock()
            }
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
    val loading = status.contains("Starting", true) || status.contains("Checking", true) || status.contains("Preparing", true) || status.contains("Loading", true) || status.contains("Downloading", true) ||
        status.contains("तपास", true) || status.contains("डाउनलोड", true) || status.contains("लोड होत", true)
    val shortStatus = when {
        status.contains("busy", true) -> if (english) "Microphone busy" else "मायक्रोफोन व्यस्त"
        status.contains("unavailable", true) -> if (english) "Microphone unavailable" else "मायक्रोफोन उपलब्ध नाही"
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
                        maxLines = 1
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
