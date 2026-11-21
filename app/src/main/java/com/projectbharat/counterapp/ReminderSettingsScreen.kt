package com.projectbharat.counterapp

import android.Manifest
import android.app.TimePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import java.util.Calendar

@Composable
internal fun ReminderSettingsScreen(english: Boolean, ink: Color) {
    val context = LocalContext.current
    var reminders by remember { mutableStateOf(DailyReminderScheduler.load(context)) }
    var notificationsEnabled by remember { mutableStateOf(DailyReminderScheduler.notificationsEnabled(context)) }
    val requestNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        notificationsEnabled = DailyReminderScheduler.notificationsEnabled(context)
    }
    LifecycleResumeEffect(context) {
        notificationsEnabled = DailyReminderScheduler.notificationsEnabled(context)
        onPauseOrDispose { }
    }

    fun save(updated: List<DailyReminder>) {
        reminders = updated
        DailyReminderScheduler.saveAndSchedule(context, updated)
    }

    fun pickTime(hour: Int, minute: Int, onSet: (Int, Int) -> Unit) {
        TimePickerDialog(context, { _, h, m -> onSet(h, m) }, hour, minute,
            DateFormat.is24HourFormat(context)).show()
    }

    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text(if (english) "Reminders" else "स्मरणपत्रे", color = ink, style = MaterialTheme.typography.headlineSmall)
            Text(if (english) "Pick a suggested time or add your own daily reminder."
                else "सुचवलेली वेळ निवडा किंवा स्वतःचे दैनिक स्मरणपत्र जोडा.")
        }
        if (!notificationsEnabled) item {
            Text(if (english) "Notifications are off. Enable them to receive reminders."
                else "सूचना बंद आहेत. स्मरणपत्रांसाठी त्या सुरू करा.")
            TextButton(onClick = {
                if (Build.VERSION.SDK_INT >= 33 &&
                    context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    val intent = if (Build.VERSION.SDK_INT >= 26) {
                        Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            .putExtra(Settings.EXTRA_CHANNEL_ID, DailyReminderScheduler.CHANNEL)
                    } else Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                    context.startActivity(intent)
                }
            }) { Text(if (english) "Enable notifications" else "सूचना सुरू करा") }
            if (Build.VERSION.SDK_INT >= 33) TextButton(onClick = {
                context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
            }) { Text(if (english) "Notification settings" else "सूचना सेटिंग्ज") }
        }
        items(reminders, key = { it.id }) { reminder ->
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(if (english) reminder.titleEn else reminder.titleMr, color = ink)
                        val time = Calendar.getInstance().apply {
                            set(Calendar.HOUR_OF_DAY, reminder.hour)
                            set(Calendar.MINUTE, reminder.minute)
                        }.time
                        TextButton(onClick = {
                            pickTime(reminder.hour, reminder.minute) { hour, minute ->
                                save(reminders.map { if (it.id == reminder.id) it.copy(hour = hour, minute = minute) else it })
                            }
                        }) { Text(DateFormat.getTimeFormat(context).format(time)) }
                        if (reminder.id >= 100) TextButton(onClick = {
                            save(reminders.filterNot { it.id == reminder.id })
                        }) { Text(if (english) "Remove" else "काढा") }
                    }
                    Switch(checked = reminder.enabled, onCheckedChange = { enabled ->
                        save(reminders.map { if (it.id == reminder.id) it.copy(enabled = enabled) else it })
                    }, modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
        item {
            Button(onClick = {
                pickTime(9, 0) { hour, minute ->
                    val id = (reminders.maxOfOrNull { it.id } ?: 0).coerceAtLeast(99) + 1
                    save(reminders + DailyReminder(id, "माझे स्मरणपत्र", "Custom reminder", hour, minute, true))
                }
            }) { Text(if (english) "Add custom reminder" else "स्मरणपत्र जोडा") }
            Text(
                if (english) "Android may deliver reminders after the selected time. If movements slow down or change, call your maternity care team promptly."
                else "Android वर स्मरणपत्र थोडे उशिरा येऊ शकते. बाळाच्या हालचाली कमी झाल्या किंवा बदलल्या तर त्वरित प्रसूती-सेवा केंद्राशी संपर्क साधा.",
                style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp, bottom = 16.dp)
            )
        }
    }
}
