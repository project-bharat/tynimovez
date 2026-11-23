package com.projectbharat.counterapp

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.RepeatMode
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.withResumed
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun BabyVoiceCountControl(
    autoStart: Boolean = false,
    onAutoStartConsumed: () -> Unit = {},
    activeSessionId: Long,
    english: Boolean,
    onRecognitionState: (Boolean, String, String?, Int?) -> Unit
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val state by BabyVoiceService.state.collectAsStateWithLifecycle()
    val active = state.active && state.sessionId == activeSessionId
    var requestStatus by remember(activeSessionId) { mutableStateOf("") }

    fun start() {
        if (!lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) return
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            requestStatus = if (english) "Enable notifications to use AI voice counting."
                else "AI आवाज मोजणीसाठी सूचना सुरू करा."
            return
        }
        requestStatus = BabyVoiceService.startingText(english)
        try {
            BabyVoiceService.start(context, activeSessionId, english)
        } catch (error: Exception) {
            requestStatus = BabyVoiceService.voiceFailure(english, error)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            start()
        } else {
            requestStatus = if (english) "Allow microphone access to start." else "सुरू करण्यासाठी मायक्रोफोनची परवानगी द्या."
        }
    }

    fun requestStart() {
        val permissions = buildList {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                add(Manifest.permission.RECORD_AUDIO)
            }
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (permissions.isEmpty()) start() else permissionLauncher.launch(permissions.toTypedArray())
    }

    LaunchedEffect(autoStart, activeSessionId) {
        if (autoStart) {
            if (!active) requestStart()
            onAutoStartConsumed()
        }
    }

    LaunchedEffect(active, english, state.english) {
        if (active && state.english != english) lifecycle.withResumed { start() }
    }

    LaunchedEffect(state, requestStatus, activeSessionId, english) {
        if (state.sessionId == activeSessionId && state.status.isNotEmpty()) {
            onRecognitionState(state.listening, state.status, "", state.latestCatch ?: 0)
            if (requestStatus.isNotEmpty() && state.active) requestStatus = ""
        } else {
            onRecognitionState(false, requestStatus.ifBlank { if (english) "Voice count is off" else "आवाज मोजणी बंद" }, "", 0)
        }
    }

    Button(
        onClick = {
            if (active) {
                requestStatus = ""
                BabyVoiceService.stop(context)
            } else {
                requestStart()
            }
        },
        modifier = Modifier.height(40.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 5.dp),
        colors = ButtonDefaults.buttonColors(containerColor = if (active) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary),
        shape = RoundedCornerShape(12.dp)
    ) {
        Icon(if (active) Icons.Default.MicOff else Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.size(5.dp))
        Text(if (active) (if (english) "Stop" else "बंद") else (if (english) "AI voice" else "AI आवाज"),
            style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}

@Composable
fun BabyVoiceRecognitionPanel(listening: Boolean, english: Boolean, status: String, transcript: String, detectedCount: Int) {
    val pulse = rememberInfiniteTransition(label = "babyVoicePulse")
    val scale by pulse.animateFloat(1f, 1.15f, infiniteRepeatable(tween(650), RepeatMode.Reverse), label = "babyVoiceMic")
    val loading = status.contains("Reconnecting", true) || status.contains("पुन्हा जोडत", true) || status.contains("Starting", true) || status.contains("Checking", true) || status.contains("Preparing", true) || status.contains("Loading", true) || status.contains("Downloading", true) ||
        status.contains("तपास", true) || status.contains("डाउनलोड", true) || status.contains("तयार होत", true)
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
                        if (english) "AI voice counting" else "AI आवाज मोजणी",
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
