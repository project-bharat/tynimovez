package com.projectbharat.counterapp

import android.app.Activity
import android.net.Uri
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.app.core.backup.PlaySubscriptionChecker
import com.app.core.backup.backupResult
import com.projectbharat.counterapp.data.CounterBackupManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Date

@Composable
fun BackupAndSyncScreen(english: Boolean) {
    val context = LocalContext.current
    val activity = context as Activity
    val app = activity.application as CounterApplication
    val scope = rememberCoroutineScope()
    val drive = app.driveSync
    val state by drive.state.collectAsStateWithLifecycle()
    val pro by app.subscription.isProUser.collectAsStateWithLifecycle()
    var busy by remember { mutableStateOf(false) }
    var message by rememberSaveable { mutableStateOf<String?>(null) }
    var restoreUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var restoreCloud by rememberSaveable { mutableStateOf(false) }
    var consentEmail by rememberSaveable { mutableStateOf<String?>(null) }
    var paywall by rememberSaveable { mutableStateOf(false) }
    fun label(en: String, mr: String) = if (english) en else mr

    fun run(success: String? = null, block: suspend () -> Unit) {
        scope.launch {
            if (busy) return@launch
            busy = true
            message = null
            try {
                backupResult { block() }.fold(
                    onSuccess = { message = success },
                    onFailure = { message = it.message ?: label("Backup operation failed.", "बॅकअप प्रक्रिया अयशस्वी झाली.") })
            } finally {
                busy = false
            }
        }
    }

    LifecycleResumeEffect(app) {
        scope.launch { backupResult { app.subscription.refresh() } }
        onPauseOrDispose {}
    }

    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) run(label("Backup file saved.", "बॅकअप फाइल जतन झाली.")) { app.files.export(uri).getOrThrow() }
    }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> restoreUri = uri }
    val csv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) run(label("CSV saved.", "CSV जतन झाली.")) {
            withContext(Dispatchers.IO) { CounterBackupManager.exportCsv(context, uri) }
        }
    }
    val authorize = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        val email = consentEmail
        consentEmail = null
        if (result.resultCode == Activity.RESULT_OK && email != null) {
            run(label("Google Drive connected.", "Google Drive जोडले.")) {
                drive.authorization.complete(result.data)
                drive.link(email)
            }
        } else message = label("Google Drive access was cancelled.", "Google Drive प्रवेश रद्द केला.")
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label("Backup & sync", "बॅकअप आणि सिंक"), style = MaterialTheme.typography.headlineSmall)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(label("Device backup · Free", "उपकरण बॅकअप · मोफत"), style = MaterialTheme.typography.titleMedium)
                Text(label("Save a copy of your counter records. Backup files can contain sensitive information.",
                    "मोजणीच्या नोंदींची प्रत जतन करा. बॅकअप फाइलमध्ये संवेदनशील माहिती असू शकते."))
                Button(onClick = { export.launch(app.files.defaultFileName()) }, enabled = !busy) {
                    Text(label("Export backup file", "बॅकअप फाइल निर्यात करा"))
                }
                OutlinedButton(onClick = { import.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
                    enabled = !busy) { Text(label("Restore from file", "फाइलमधून पुनर्संचयित करा")) }
                TextButton(onClick = { csv.launch("tynimovez-history.csv") }, enabled = !busy) { Text(label("Export CSV", "CSV निर्यात करा")) }
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(label("Google Drive backup · Pro", "Google Drive बॅकअप · प्रो"), style = MaterialTheme.typography.titleMedium)
                if (!pro) {
                    Text(label("Automatically save backups to your Google account’s private app folder.",
                        "तुमच्या Google खात्यातील अॅपच्या खाजगी फोल्डरमध्ये बॅकअप आपोआप जतन करा."))
                    if (state.email != null) TextButton(onClick = { run { drive.disconnect() } }, enabled = !busy) {
                        Text(label("Disconnect Google Drive", "Google Drive डिस्कनेक्ट करा"))
                    }
                    Button(onClick = { paywall = true }) { Text(label("Upgrade to Pro", "प्रो घ्या")) }
                    TextButton(onClick = { run { check(app.subscription.refresh()) { label("No active Pro subscription found.", "सक्रिय प्रो सदस्यता सापडली नाही.") } } },
                        enabled = !busy) { Text(label("Check subscription", "सदस्यता तपासा")) }
                } else {
                    val email = state.email
                    if (email != null) {
                        Text(email)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically) {
                            Text(label("Auto-sync", "स्वयंचलित सिंक"))
                            Switch(checked = state.automatic, onCheckedChange = { enabled -> run { drive.setAutomatic(enabled) } },
                                enabled = !busy && !state.syncing)
                        }
                        val synced = state.lastSynced?.let {
                            val date = Date(it)
                            DateFormat.getDateFormat(context).format(date) + " " + DateFormat.getTimeFormat(context).format(date)
                        } ?: label("Never", "अद्याप नाही")
                        Text(label("Last synced: $synced", "शेवटचे सिंक: $synced"))
                        Button(onClick = { message = null; drive.triggerManualDriveSync() }, enabled = !busy && !state.syncing) {
                            Text(label("Sync now", "आता सिंक करा"))
                        }
                        OutlinedButton(onClick = { restoreCloud = true }, enabled = !busy && !state.syncing) {
                            Text(label("Restore from cloud", "क्लाउडमधून पुनर्संचयित करा"))
                        }
                        TextButton(onClick = { run { drive.disconnect() } }, enabled = !busy) { Text(label("Disconnect", "खाते डिस्कनेक्ट करा")) }
                    }
                    if (email == null || state.message != null) {
                        OutlinedButton(onClick = {
                            run {
                                val selected = email ?: drive.authorization.selectAccount(activity, context.getString(R.string.default_web_client_id))
                                val grant = drive.authorization.authorize(selected)
                                if (grant.hasResolution()) {
                                    consentEmail = selected
                                    authorize.launch(IntentSenderRequest.Builder(requireNotNull(grant.pendingIntent)).build())
                                } else {
                                    drive.link(selected)
                                }
                            }
                        }, enabled = !busy && !state.syncing) {
                            Text(if (email == null) label("Connect Google account", "Google खाते जोडा") else label("Reconnect", "पुन्हा जोडा"))
                        }
                    }
                }
                if (state.syncing) Text(label("Syncing…", "सिंक होत आहे…"))
                if (pro) state.message?.let { Text(it) }
            }
        }
        message?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
    }

    if (restoreUri != null || restoreCloud) AlertDialog(
        onDismissRequest = { restoreUri = null; restoreCloud = false },
        title = { Text(label("Replace counter records?", "मोजणीच्या नोंदी बदलाव्यात?")) },
        text = { Text(label("Restoring replaces the counter records on this device with the selected backup.",
            "पुनर्संचयित केल्यावर या उपकरणावरील मोजणीच्या नोंदी निवडलेल्या बॅकअपने बदलल्या जातील.")) },
        confirmButton = {
            TextButton(onClick = {
                val uri = restoreUri
                restoreUri = null
                restoreCloud = false
                run(label("Backup restored.", "बॅकअप पुनर्संचयित झाला.")) {
                    if (uri != null) app.files.restore(uri).getOrThrow() else drive.restoreFromCloud().getOrThrow()
                }
            }) { Text(label("Restore", "पुनर्संचयित करा")) }
        },
        dismissButton = {
            TextButton(onClick = { restoreUri = null; restoreCloud = false }) { Text(label("Cancel", "रद्द करा")) }
        })

    if (paywall) {
        var offer by remember { mutableStateOf<PlaySubscriptionChecker.Offer?>(null) }
        var failed by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            backupResult { app.subscription.offer() }.onSuccess { offer = it }.onFailure { failed = true }
        }
        LaunchedEffect(pro) { if (pro) paywall = false }
        AlertDialog(onDismissRequest = { paywall = false },
            title = { Text("Tynimovez Pro") },
            text = { Text(when {
                failed -> label("The subscription is unavailable in Google Play.", "Google Play वर सदस्यता उपलब्ध नाही.")
                offer == null -> label("Checking Google Play…", "Google Play तपासत आहे…")
                else -> label("Get automatic Google Drive backup.", "स्वयंचलित Google Drive बॅकअप मिळवा.")
            }) },
            confirmButton = {
                offer?.let { plan ->
                    Button(onClick = { run { app.subscription.purchase(activity, plan) } }, enabled = !busy) {
                        Text(label("Subscribe · ${plan.price}", "सदस्यता · ${plan.price}"))
                    }
                }
            },
            dismissButton = { TextButton(onClick = { paywall = false }) { Text(label("Close", "बंद करा")) } })
    }
}
