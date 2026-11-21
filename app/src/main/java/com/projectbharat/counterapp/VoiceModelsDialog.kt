package com.projectbharat.counterapp

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.work.WorkManager
import androidx.concurrent.futures.await
import kotlinx.coroutines.delay

@Composable
fun VoiceModelsDialog(english: Boolean, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var ready by remember { mutableStateOf(false) }
    var preparing by remember { mutableStateOf(false) }
    LaunchedEffect(context) {
        val manager = OfflineSpeechModelManager(context.applicationContext)
        val work = WorkManager.getInstance(context)
        while (true) {
            ready = manager.isInstalled(false) && manager.isInstalled(true)
            preparing = work.getWorkInfosForUniqueWork("voice-model-prefetch").await().any { !it.state.isFinished }
            if (ready) break
            delay(1000)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (english) "AI voice counting" else "AI आवाज मोजणी") },
        text = {
            Text(when {
                ready -> if (english) "Ready for offline use." else "ऑफलाइन वापरासाठी तयार."
                preparing -> if (english) "Preparing offline voice counting…" else "ऑफलाइन आवाज मोजणी तयार होत आहे…"
                else -> if (english) "Set up once to count movements by voice offline." else "ऑफलाइन आवाजाने हालचाली मोजण्यासाठी एकदा सेटअप करा."
            })
        },
        confirmButton = {
            if (!ready) Button(
                onClick = {
                    SpeechModelDownloadScheduler.enqueueAll(context, wifiOnly = false)
                    preparing = true
                },
                enabled = !preparing
            ) { Text(if (english) "Set up" else "सेटअप करा") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(if (english) "Close" else "बंद करा") } }
    )
}
