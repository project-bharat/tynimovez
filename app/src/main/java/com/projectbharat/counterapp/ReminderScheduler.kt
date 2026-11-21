package com.projectbharat.counterapp

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import java.util.Calendar

data class DailyReminder(
    val id: Int,
    val titleMr: String,
    val titleEn: String,
    val hour: Int,
    val minute: Int,
    val enabled: Boolean
)

object DailyReminderScheduler {
    private const val PREFS = "daily_reminder_preferences"
    const val CHANNEL = "daily_counter_reminders"

    fun defaults() = listOf(
        DailyReminder(1, "बाळाची हालचाल · सकाळ", "Baby movements · Morning", 9, 0, false),
        DailyReminder(2, "बाळाची हालचाल · दुपार", "Baby movements · Afternoon", 13, 0, false),
        DailyReminder(3, "बाळाची हालचाल · संध्याकाळ", "Baby movements · Evening", 19, 0, false),
    )

    fun load(context: Context): List<DailyReminder> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val custom = prefs.getStringSet("custom_ids", emptySet()).orEmpty()
            .mapNotNull { it.toIntOrNull() }.sorted()
            .map { id -> DailyReminder(id, "माझे स्मरणपत्र", "Custom reminder", 9, 0, false) }
        return (defaults() + custom).map { item ->
            item.copy(
                hour = prefs.getInt("hour_${item.id}", item.hour),
                minute = prefs.getInt("minute_${item.id}", item.minute),
                enabled = prefs.getBoolean("enabled_${item.id}", item.enabled)
            )
        }
    }

    fun saveAndSchedule(context: Context, reminders: List<DailyReminder>) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val previousIds = load(context).map { it.id }
        val activeIds = reminders.map { it.id }.toSet()
        val removed = previousIds.filterNot(activeIds::contains)
        prefs.edit().apply {
            putStringSet("custom_ids", activeIds.filter { it >= 100 }.map { it.toString() }.toSet())
            removed.forEach { id ->
                remove("hour_$id")
                remove("minute_$id")
                remove("enabled_$id")
            }
            reminders.forEach { item ->
                putInt("hour_${item.id}", item.hour)
                putInt("minute_${item.id}", item.minute)
                putBoolean("enabled_${item.id}", item.enabled)
            }
        }.apply()
        removed.forEach { cancel(context, it) }
        reminders.forEach { item ->
            if (item.enabled) scheduleNext(context, item) else cancel(context, item.id)
        }
    }

    fun rescheduleAll(context: Context) {
        // Cancel reminders from the previous multi-counter version.
        cancel(context, 4)
        cancel(context, 5)
        load(context).filter { it.enabled }.forEach { scheduleNext(context, it) }
    }

    private fun scheduleNext(context: Context, item: DailyReminder) {
        val now = Calendar.getInstance()
        val next = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, item.hour)
            set(Calendar.MINUTE, item.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= now.timeInMillis) add(Calendar.DAY_OF_YEAR, 1)
        }
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pending = pendingIntent(context, item.id)
        alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.timeInMillis, pending)
    }

    private fun cancel(context: Context, id: Int) {
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarm.cancel(pendingIntent(context, id))
    }

    private fun pendingIntent(context: Context, id: Int): PendingIntent {
        val intent = Intent(context, DailyReminderReceiver::class.java).putExtra("reminder_id", id)
        return PendingIntent.getBroadcast(context, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    fun notificationsEnabled(context: Context): Boolean {
        ensureChannel(context)
        val manager = context.getSystemService(NotificationManager::class.java)
        return NotificationManagerCompat.from(context).areNotificationsEnabled() &&
            (Build.VERSION.SDK_INT < 26 ||
                manager.getNotificationChannel(CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE)
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL, "Daily reminders", NotificationManager.IMPORTANCE_DEFAULT))
        }
    }

    fun showNotification(context: Context, reminderId: Int) {
        val item = load(context).firstOrNull { it.id == reminderId } ?: return
        if (!item.enabled) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        ensureChannel(context)
        val languageEnglish = context.getSharedPreferences("app_preferences", Context.MODE_PRIVATE)
            .getBoolean("english_language", false)
        val open = PendingIntent.getActivity(context, 7200,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE)
        val title = if (languageEnglish) item.titleEn else item.titleMr
        val body = if (languageEnglish) "A gentle reminder for your daily routine." else "तुमच्या दैनंदिन नोंदीसाठी एक छोटी आठवण."
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(title)
            .setContentText(body)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        try { manager.notify(7000 + reminderId, notification) } catch (_: SecurityException) { }
        scheduleNext(context, item)
    }
}

class DailyReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra("reminder_id", -1)
        if (id >= 0) DailyReminderScheduler.showNotification(context, id)
    }
}

class ReminderBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action in listOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_TIME_CHANGED,
                Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_MY_PACKAGE_REPLACED)) {
            DailyReminderScheduler.rescheduleAll(context)
        }
    }
}
