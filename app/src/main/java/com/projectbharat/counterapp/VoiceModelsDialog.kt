package com.projectbharat.counterapp

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

private data class VoiceModelStatus(
    val key: String,
    val title: String,
    val purpose: String,
    val installed: Boolean,
    val state: WorkInfo.State?,
    val progress: Int,
    val message: String
)

@Composable
fun VoiceModelsDialog(english: Boolean, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var models by remember { mutableStateOf(emptyList<VoiceModelStatus>()) }
    var refreshing by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        while (isActive) {
            val snapshot = withContext(Dispatchers.IO) {
                val manager = OfflineSpeechModelManager(context.applicationContext)
                val workManager = WorkManager.getInstance(context)
                val definitions = listOf(
                    Triple("sherpa_mr", "Sherpa-ONNX · Marathi", "Baby movement voice counting"),
                    Triple("sherpa_en", "Sherpa-ONNX · English", "Baby movement voice counting"),
                )
                definitions.map { (key, title, purpose) ->
                    val installed = when (key) {
                        "sherpa_mr" -> manager.isInstalled(false)
                        "sherpa_en" -> manager.isInstalled(true)
                        else -> false
                    }
                    val info = runCatching { workManager.getWorkInfosByTag(key).get().lastOrNull() }.getOrNull()
                    VoiceModelStatus(
                        key, title, purpose, installed, info?.state,
                        info?.progress?.getInt("progress", 0) ?: 0,
                        info?.progress?.getString("message").orEmpty()
                    )
                }
            }
            models = snapshot
            refreshing = false
            delay(900)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (english) "Offline voice models" else "ऑफलाइन आवाज मॉडेल") },
        text = {
            Column(
                Modifier.fillMaxWidth().height(390.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                models.forEach { model ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(model.title, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                                Text(
                                    when {
                                        model.installed -> if (english) "Ready" else "तयार"
                                        model.state == WorkInfo.State.RUNNING -> if (english) "Working" else "सुरू"
                                        model.state == WorkInfo.State.ENQUEUED || model.state == WorkInfo.State.BLOCKED -> if (english) "Queued" else "रांगेत"
                                        model.state == WorkInfo.State.FAILED -> if (english) "Failed" else "अयशस्वी"
                                        model.state == WorkInfo.State.SUCCEEDED -> if (english) "Verifying" else "तपासत आहे"
                                        else -> if (english) "Not downloaded" else "डाउनलोड नाही"
                                    },
                                    color = if (model.installed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(model.purpose, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (!model.installed && model.state == WorkInfo.State.RUNNING) {
                                LinearProgressIndicator(
                                    progress = { (model.progress / 100f).coerceIn(0f, 1f) },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Text(
                                    model.message.ifBlank { if (english) "Preparing model…" else "मॉडेल तयार होत आहे…" },
                                    style = MaterialTheme.typography.labelSmall
                                )
                            } else if (!model.installed && (model.state == WorkInfo.State.ENQUEUED || model.state == WorkInfo.State.BLOCKED)) {
                                Text(if (english) "Waiting for the previous model to finish" else "मागील मॉडेल पूर्ण होण्याची प्रतीक्षा", style = MaterialTheme.typography.labelSmall)
                            } else if (!model.installed && model.state == WorkInfo.State.FAILED) {
                                Text(if (english) "Download failed. Use Download all to retry." else "डाउनलोड अयशस्वी. पुन्हा प्रयत्न करण्यासाठी सर्व डाउनलोड करा.", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
                if (refreshing && models.isEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator()
                        Text(if (english) "Checking installed models…" else "डाउनलोड तपासत आहे…")
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { SpeechModelDownloadScheduler.enqueueAll(context, wifiOnly = false) }) {
                Text(if (english) "Download all models" else "सर्व मॉडेल डाउनलोड करा")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text(if (english) "Close" else "बंद करा") }
        }
    )
}
