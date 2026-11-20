package com.projectbharat.counterapp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Button
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.background
import kotlinx.coroutines.delay
import kotlin.math.sqrt
import androidx.core.content.ContextCompat
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.Switch
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.togetherWith
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.AnimatedContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.border
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.Lifecycle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import android.hardware.SensorManager
import android.hardware.SensorEventListener
import android.hardware.SensorEvent
import android.hardware.Sensor
import android.content.pm.PackageManager
import android.Manifest
import androidx.compose.foundation.Canvas
import android.content.IntentFilter
import android.os.Build
import java.text.NumberFormat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.material3.Divider
import androidx.compose.material3.IconButton
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.projectbharat.counterapp.data.DayTotal
import com.projectbharat.counterapp.data.CounterLog
import java.time.LocalDate

private fun registerTrackingReceiver(context: Context, receiver: BroadcastReceiver) {
    val filter = IntentFilter(TRACKING_STATE_CHANGED)
    if (Build.VERSION.SDK_INT >= 33) {
        context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
    } else {
        context.registerReceiver(receiver, filter)
    }
}

@Composable
private fun StepMetricCard(label: String, value: String, note: String, ink: Color, modifier: Modifier = Modifier) {
    Card(modifier, shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(13.dp)) {
            Text(label, color = ink.copy(alpha = .58f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(value, color = ink, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(note, color = ink.copy(alpha = .55f), style = MaterialTheme.typography.labelSmall)
        }
    }
}

private data class ChantPreset(val title: String, val devanagari: String, val latin: String, val deity: String = "Other")

private val chantPresets = listOf(
    ChantPreset("Ram", "राम", "Ram", "Ram"),
    ChantPreset("Sri Rama Ram Jai Jai Ram", "श्रीराम राम जय जय राम श्रीराम जय राम जय जय राम", "Sri Rama Ram Jai Jai Ram Sri Rama Jai Ram Jai Jai Ram", "Ram"),
    ChantPreset("Jai Jai Pandurang Hari", "जय जय पांडुरंग हरी", "Jai Jai Pandurang Hari", "Vishnu"),
    ChantPreset("Jai Shri Ram", "जय श्रीराम", "Jai Shri Ram", "Ram"),
    ChantPreset("Om Namah Shivaya", "ॐ नमः शिवाय", "Om Namah Shivaya", "Shiva"),
    ChantPreset("Maha Mrityunjaya Mantra", "ॐ त्र्यम्बकं यजामहे सुगन्धिं पुष्टिवर्धनम्। उर्वारुकमिव बन्धनान्मृत्योर्मुक्षीय मामृतात्॥", "Om Tryambakam Yajamahe Sugandhim Pushtivardhanam Urvarukamiva Bandhanan Mrityor Mukshiya Maamritat", "Shiva"),
    ChantPreset("Om Namo Bhagavate Vasudevaya", "ॐ नमो भगवते वासुदेवाय", "Om Namo Bhagavate Vasudevaya", "Krishna"),
    ChantPreset("Hare Krishna Mahamantra", "हरे कृष्ण हरे कृष्ण, कृष्ण कृष्ण हरे हरे। हरे राम हरे राम, राम राम हरे हरे॥", "Hare Krishna Hare Krishna Krishna Krishna Hare Hare Hare Rama Hare Rama Rama Rama Hare Hare", "Krishna"),
    ChantPreset("Om Gam Ganapataye Namaha", "ॐ गं गणपतये नमः", "Om Gam Ganapataye Namaha", "Ganesh"),
    ChantPreset("Gayatri Mantra", "ॐ भूर्भुवः स्वः तत्सवितुर्वरेण्यं भर्गो देवस्य धीमहि धियो यो नः प्रचोदयात्॥", "Om Bhur Bhuvah Svah Tat Savitur Varenyam Bhargo Devasya Dhimahi Dhiyo Yo Nah Prachodayat", "Devi"),
    ChantPreset("Shri Ram Jai Ram", "श्री राम जय राम जय जय राम", "Shri Ram Jai Ram Jai Jai Ram", "Ram"),
    ChantPreset("Om Hanumate Namah", "ॐ हनुमते नमः", "Om Hanumate Namah", "Hanuman"),
    ChantPreset("Guru Mantra", "गुरुर्ब्रह्मा गुरुर्विष्णुः गुरुर्देवो महेश्वरः। गुरुः साक्षात् परब्रह्म तस्मै श्रीगुरवे नमः॥", "Gurur Brahma Gurur Vishnuh Gurur Devo Maheshwarah Guruh Sakshat Parabrahma Tasmai Shri Gurave Namah", "Guru"),
    ChantPreset("Om (Pranava)", "ॐ", "Om", "Other"),
    ChantPreset("Ram Krishna Hari", "राम कृष्ण हरी ॥", "Ram Krishna Hari", "Vishnu"),
    ChantPreset("Om Namo Narayanaya", "ॐ नमो नारायणाय ॥", "Om Namo Narayanaya", "Vishnu"),
    ChantPreset("Digambara Digambara", "दिगंबरा दिगंबरा श्रीपाद वल्लभ दिगंबरा ॥", "Digambara Digambara Shripada Vallabha Digambara", "Dattatreya"),
    ChantPreset("Om Aim Saraswatyai Namah", "ॐ ऐं सरस्वत्यै नमः ॥", "Om Aim Saraswatyai Namah", "Devi"),
    ChantPreset("Om Mahalakshmyai Namah", "ॐ महालक्ष्म्यै नमः ॥", "Om Mahalakshmyai Namah", "Devi"),
    ChantPreset("Vakratunda Mahakaya", "वक्रतुण्ड महाकाय सूर्यकोटि समप्रभ । निर्विघ्नं कुरु मे देव सर्वकार्येषु सर्वदा ॥", "Vakratunda Mahakaya Suryakoti Samaprabha Nirvighnam Kuru Me Deva Sarvakaryeshu Sarvada", "Ganesh"),
    ChantPreset("Pandurang Hari", "पांडुरंग हरी", "Pandurang Hari", "Vishnu"),
    ChantPreset("Pandurang", "पांडुरंग", "Pandurang", "Vishnu")
)

@Composable
fun AutoChantCounterScreen(categoryId: Long, count: Int, totalCount: Int, english: Boolean, ink: Color, onMantraChange: (String) -> Unit, onManualCount: (String) -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("chant_settings", Context.MODE_PRIVATE) }
    var target by remember(categoryId) { mutableStateOf(prefs.getString("target_$categoryId", if (english) "Ram" else "राम") ?: "राम") }
    var language by remember(categoryId) { mutableStateOf(prefs.getString("language_$categoryId", if (english) "en" else "hi") ?: "hi") }
    var automatic by remember { mutableStateOf(context.getSharedPreferences("tracking_state", Context.MODE_PRIVATE).getBoolean("chant_enabled", false)) }
    var running by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var transcript by remember { mutableStateOf("") }
    var matched by remember { mutableStateOf(false) }
    var matchedCount by remember { mutableIntStateOf(0) }
    var presetMenu by remember { mutableStateOf(false) }
    var selectedDeity by remember(categoryId) { mutableStateOf("All") }
    var addMantraDialog by remember { mutableStateOf(false) }
    var newMantraMr by remember { mutableStateOf("") }
    var newMantraEn by remember { mutableStateOf("") }
    var goalsDialog by remember(categoryId) { mutableStateOf(false) }
    var dailyMalaTarget by remember(categoryId) { mutableIntStateOf(prefs.getInt("daily_mala_target_$categoryId", 3).coerceAtLeast(1)) }
    var lifetimeJapaTarget by remember(categoryId) { mutableIntStateOf(prefs.getInt("lifetime_japa_target_$categoryId", 10000).coerceAtLeast(108)) }
    var customMantras by remember(categoryId) {
        mutableStateOf(prefs.getStringSet("custom_mantras_$categoryId", emptySet()).orEmpty().map { saved ->
            val parts = saved.split("\u001F", limit = 2)
            if (parts.size == 2) ChantPreset(parts[1], parts[0], parts[1])
            else ChantPreset(saved, saved, saved)
        })
    }

    val stopRecognition = {
        ChantRecognitionService.stop(context)
        running = false
        automatic = false
        status = if (english) "Stopped" else "थांबवले"
    }
    val startService = {
        if (target.isNotBlank()) {
            prefs.edit().putString("target_$categoryId", target.trim()).putString("language_$categoryId", language).apply()
            try {
                ChantRecognitionService.start(context, categoryId, target.trim(), language)
                automatic = true
                running = context.getSharedPreferences("tracking_state", Context.MODE_PRIVATE).getBoolean("chant_audio_active", false)
                status = if (english) "Preparing offline model…" else "ऑफलाइन मॉडेल तयार होत आहे…"
            } catch (error: Exception) {
                running = false
                automatic = false
                status = when {
                    error is SecurityException -> if (english) "Microphone permission required" else "मायक्रोफोन परवानगी आवश्यक"
                    error.message?.contains("busy", true) == true -> if (english) "Microphone busy — another app or call is using it" else "मायक्रोफोन व्यस्त — दुसरे अॅप किंवा कॉल वापरत आहे"
                    else -> error.message ?: if (english) "Could not start" else "सुरू झाले नाही"
                }
            }
        }
    }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startService() else { automatic = false; status = if (english) "Notification permission denied" else "सूचना परवानगी नाकारली" }
    }
    val microphonePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            automatic = false
            if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            else startService()
        } else { automatic = false; running = false; status = if (english) "Microphone permission denied" else "मायक्रोफोन परवानगी नाकारली" }
    }
    DisposableEffect(context, categoryId) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                if (intent?.getStringExtra(EXTRA_TRACKING_KIND) != "chant") return
                if (intent.hasExtra("active")) running = intent.getBooleanExtra("active", false)
                intent.getStringExtra("message")?.let { status = it
                    if (it.contains("Microphone busy", true) || it.contains("Microphone unavailable", true) ||
                        it.contains("microphone permission", true) || it.contains("मायक्रोफोन व्यस्त") ||
                        it.contains("मायक्रोफोन उपलब्ध नाही") || it.contains("परवानगी आवश्यक")) {
                        automatic = false
                    }
                }
                intent.getStringExtra("transcript")?.let { transcript = it }
                if (intent.hasExtra("matched")) matched = intent.getBooleanExtra("matched", false)
                if (intent.hasExtra("delta")) {
                    val delta = intent.getIntExtra("delta", 0)
                    matched = true
                    matchedCount = delta
                }
            }
        }
        registerTrackingReceiver(context, receiver)
        onDispose { context.unregisterReceiver(receiver) }
    }
    LaunchedEffect(categoryId, english, automatic) {
        var lastRetryAt = 0L
        while (true) {
            val active = context.getSharedPreferences("tracking_state", Context.MODE_PRIVATE).getBoolean("chant_audio_active", false)
            running = active
            val recoverable = status.contains("reconnecting", true) || status.contains("stopped", true) || status.contains("error", true)
            if (automatic && !active && recoverable && System.currentTimeMillis() - lastRetryAt >= 15000L) {
                lastRetryAt = System.currentTimeMillis()
                status = if (english) "Recognition reconnecting…" else "आवाज ओळख पुन्हा जोडत आहे…"
                startService()
            }
            delay(1500)
        }
    }

    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(if (english) "TODAY" else "आज", color = ink.copy(alpha=.55f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Text(if (english) "Chants" else "जप", color = ink, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Row(Modifier.padding(start = 8.dp, end = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (english) "Voice count" else "आवाज मोजणी", color = ink, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(end = 8.dp))
                Switch(checked = automatic, onCheckedChange = { enabled ->
                    if (!enabled) stopRecognition()
                    else {
                        automatic = false; matched = false; matchedCount = 0; transcript = ""
                        status = if (english) "Starting voice recognition…" else "आवाज ओळख सुरू होत आहे…"
                        running = false
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
                            microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
                        else if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        else startService()
                    }
                })
            }
        }
        Card(Modifier.fillMaxWidth().heightIn(min = 760.dp), shape = RoundedCornerShape(26.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.fillMaxWidth().heightIn(min = 760.dp).padding(horizontal = 18.dp, vertical = 22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                val malaProgress = if (totalCount > 0 && totalCount % 108 == 0) 108 else totalCount % 108
                val malaFraction = malaProgress / 108f
                val beadTint by animateColorAsState(if (malaProgress == 108) Color(0xFFB87928) else Color(0xFF7650A1), label = "beadCountTint")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    Surface(Modifier.weight(1f), shape = RoundedCornerShape(14.dp), color = Color(0xFFEAF3F9)) {
                        Column(Modifier.padding(vertical = 10.dp, horizontal = 3.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(totalCount.toString(), color = Color(0xFF285D87), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                            Text(if (english) "TOTAL JAPA" else "एकूण जप", color = Color(0xFF285D87), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, maxLines = 1)
                        }
                    }
                    Surface(Modifier.weight(1f), shape = RoundedCornerShape(14.dp), color = Color(0xFFF4EFF9)) {
                        Column(Modifier.padding(vertical = 10.dp, horizontal = 3.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("$malaProgress/108", color = beadTint, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                            Text(if (english) "MALA PROGRESS" else "माळ प्रगती", color = Color(0xFF76528D), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, maxLines = 1)
                        }
                    }
                    Surface(Modifier.weight(1f), shape = RoundedCornerShape(14.dp), color = Color(0xFFFFF4E4)) {
                        Column(Modifier.padding(vertical = 10.dp, horizontal = 3.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text((totalCount / 108).toString(), color = Color(0xFFB87928), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                            Text(if (english) "MALAS" else "पूर्ण माळा", color = Color(0xFF9A6828), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, maxLines = 1)
                        }
                    }
                }
                // Per-mantra daily and lifetime goals with motivational milestones.
                val dailyMalasDone = count / 108
                val dailyGoalCount = dailyMalaTarget * 108
                val dailyGoalProgress = (count.toFloat() / dailyGoalCount).coerceIn(0f, 1f)
                val lifetimeProgress = (totalCount.toFloat() / lifetimeJapaTarget).coerceIn(0f, 1f)
                val milestones = listOf(108, 1188, 2268, 5508, 11664, 54108, 108864, 1080864)
                val milestoneNames = if (english) listOf("Japa Begin", "Regular Japa", "Japa Devotion", "Japa Sankalp", "Sadhana Path", "Sadhana Dedication", "Maha Sadhana", "Maha Sankalp") else listOf("जप आरंभ", "नियमित जप", "जप निष्ठा", "जप संकल्प", "साधना पथ", "साधना समर्पण", "महासाधना", "महासंकल्प")
                val earned = milestones.indices.filter { totalCount >= milestones[it] }
                val nextIndex = milestones.indexOfFirst { totalCount < it }
                val rankName = if (earned.isEmpty()) (if (english) "Start your first mala" else "पहिली माळ सुरू करा") else milestoneNames[earned.last()]
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F4FC))) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(if (english) "MY SANKALP" else "माझा संकल्प", color = Color(0xFF68468D), fontWeight = FontWeight.Bold)
                                Text(if (english) "Daily and lifetime goals for this mantra" else "या मंत्रासाठी दैनिक व आजीवन लक्ष्य", color = ink.copy(alpha = .6f), style = MaterialTheme.typography.labelSmall)
                            }
                            TextButton(onClick = { goalsDialog = true }) { Text(if (english) "Edit goals" else "लक्ष्य बदला") }
                        }
                        Text((if (english) "Daily malas: " else "दैनिक माळा: ") + "$dailyMalasDone / $dailyMalaTarget", color = ink, fontWeight = FontWeight.SemiBold)
                        LinearProgressIndicator(progress = { dailyGoalProgress }, modifier = Modifier.fillMaxWidth().height(7.dp), color = Color(0xFF7D59A7), trackColor = Color(0xFFE5DCEB))
                        Text((if (english) "Lifetime: " else "आजीवन: ") + NumberFormat.getIntegerInstance().format(totalCount) + " / " + NumberFormat.getIntegerInstance().format(lifetimeJapaTarget), color = ink, fontWeight = FontWeight.SemiBold)
                        LinearProgressIndicator(progress = { lifetimeProgress }, modifier = Modifier.fillMaxWidth().height(7.dp), color = Color(0xFFB68A43), trackColor = Color(0xFFEDE3D3))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(shape = RoundedCornerShape(50), color = Color(0xFFE8DDF2)) {
                                Text("✦ $rankName", modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp), color = Color(0xFF68468D), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.weight(1f))
                            Text((if (english) "Badges " else "बॅज ") + earned.size.toString() + "/" + milestones.size, color = ink.copy(alpha = .65f), style = MaterialTheme.typography.labelSmall)
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            milestones.indices.forEach { i ->
                                Surface(Modifier.weight(1f).height(35.dp), shape = RoundedCornerShape(9.dp), color = if (i in earned) Color(0xFFE9D5A8) else Color(0xFFEDE8F0)) {
                                    Box(contentAlignment = Alignment.Center) { Text(if (i in earned) "★" else "·", color = if (i in earned) Color(0xFF8A6325) else Color(0xFFAAA0B2), style = MaterialTheme.typography.titleMedium) }
                                }
                            }
                        }
                        if (nextIndex >= 0) {
                            val previous = if (nextIndex == 0) 0 else milestones[nextIndex - 1]
                            val towardNext = ((totalCount - previous).toFloat() / (milestones[nextIndex] - previous)).coerceIn(0f, 1f)
                            Text((if (english) "Next: " else "पुढील: ") + milestoneNames[nextIndex] + " · " + NumberFormat.getIntegerInstance().format(milestones[nextIndex] - totalCount) + (if (english) " japa to go" else " जप बाकी"), color = ink.copy(alpha = .65f), style = MaterialTheme.typography.labelSmall)
                            LinearProgressIndicator(progress = { towardNext }, modifier = Modifier.fillMaxWidth().height(5.dp), color = Color(0xFFB68A43), trackColor = Color(0xFFEDE3D3))
                        } else Text(if (english) "All milestones earned for this mantra." else "या मंत्रासाठी सर्व टप्पे पूर्ण.", color = Color(0xFF8A6325), style = MaterialTheme.typography.labelSmall)
                    }
                }
                if (goalsDialog) {
                    AlertDialog(
                        onDismissRequest = { goalsDialog = false },
                        title = { Text(if (english) "Mantra goals" else "मंत्र लक्ष्य") },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(value = dailyMalaTarget.toString(), onValueChange = { it.toIntOrNull()?.let { v -> dailyMalaTarget = v.coerceIn(1, 999) } }, label = { Text(if (english) "Daily malas (108 each)" else "दैनिक माळा (प्रत्येकी १०८)") }, singleLine = true)
                                OutlinedTextField(value = lifetimeJapaTarget.toString(), onValueChange = { it.toIntOrNull()?.let { v -> lifetimeJapaTarget = v.coerceAtLeast(108) } }, label = { Text(if (english) "Lifetime japa target" else "आजीवन जप लक्ष्य") }, singleLine = true)
                            }
                        },
                        confirmButton = { TextButton(onClick = { prefs.edit().putInt("daily_mala_target_$categoryId", dailyMalaTarget).putInt("lifetime_japa_target_$categoryId", lifetimeJapaTarget).apply(); goalsDialog = false }) { Text(if (english) "Save" else "जतन") } },
                        dismissButton = { TextButton(onClick = { goalsDialog = false }) { Text(if (english) "Cancel" else "रद्द") } }
                    )
                }
                Text(target, color = if (MaterialTheme.colorScheme.background == Color.White) Color(0xFF38234F) else ink, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 26.dp))
                val manualInteraction = remember { MutableInteractionSource() }
                val manualPressed by manualInteraction.collectIsPressedAsState()
                val manualScale by animateFloatAsState(if (manualPressed) 0.94f else 1f, label = "japaTapScale")
                val circleAccent by animateColorAsState(if (count > 0 && count % 108 == 0) Color(0xFFD5A24B) else Color(0xFFB895D7), label = "japaCircleAccent")
                Box(Modifier.padding(top = 16.dp).size(244.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        progress = { malaFraction },
                        modifier = Modifier.size(244.dp),
                        strokeWidth = 9.dp,
                        color = beadTint,
                        trackColor = Color(0xFFE8E1D7)
                    )
                    Box(
                        Modifier.size(218.dp)
                            .graphicsLayer(scaleX = manualScale, scaleY = manualScale)
                            .shadow(12.dp, CircleShape)
                            .background(Brush.linearGradient(listOf(Color(0xFFEFE4FA), Color(0xFFD2B9EA))), CircleShape)
                            .border(3.dp, circleAccent, CircleShape)
                            .clickable(interactionSource = manualInteraction, indication = null, onClick = { onManualCount(target) }),
                        contentAlignment = Alignment.Center
                    ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.SelfImprovement, null, tint = Color(0xFF7855A6), modifier = Modifier.size(42.dp))
                        Text("+1", color = ink, style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.ExtraBold)
                        Text(if (english) "TAP TO COUNT" else "स्पर्श करून मोजा", color = ink, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    }
                }
                }
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth().padding(top = 8.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("All", "Ram", "Krishna", "Shiva", "Ganesh", "Hanuman", "Devi", "Vishnu", "Dattatreya", "Guru", "Other").forEach { deity ->
                        FilterChip(selected = selectedDeity == deity, onClick = { selectedDeity = deity }, label = { Text(if (english) deity else when(deity) { "All" -> "सर्व"; "Ram" -> "राम"; "Krishna" -> "कृष्ण"; "Shiva" -> "शिव"; "Ganesh" -> "गणेश"; "Hanuman" -> "हनुमान"; "Devi" -> "देवी"; "Vishnu" -> "विष्णू"; "Dattatreya" -> "दत्तात्रेय"; "Guru" -> "गुरु"; else -> "इतर" }) })
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.weight(1f)) {
                        OutlinedButton(
                            onClick = { presetMenu = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text(if (english) "Choose mantra" else "मंत्र निवडा", maxLines = 1)
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                        }
                        DropdownMenu(expanded = presetMenu, onDismissRequest = { presetMenu = false }) {
                            val visiblePresets = (chantPresets + customMantras).distinctBy { it.devanagari }
                                .filter { selectedDeity == "All" || it.deity == selectedDeity }
                            visiblePresets.forEach { item ->
                                val mantra = if (language == "hi") item.devanagari else item.latin
                                DropdownMenuItem(text = { Text(mantra, maxLines = 2) }, onClick = {
                                    target = mantra
                                    prefs.edit().putString("target_$categoryId", target).apply()
                                    onMantraChange(target)
                                    if (running) {
                                        transcript = ""
                                        matched = false
                                        matchedCount = 0
                                        status = if (english) "Preparing offline model…" else "ऑफलाइन मॉडेल तयार होत आहे…"
                                        ChantRecognitionService.start(context, categoryId, target.trim(), language)
                                    }
                                    presetMenu = false
                                })
                            }
                        }
                    }
                    Button(
                        onClick = { newMantraMr = ""; newMantraEn = ""; addMantraDialog = true },
                        shape = RoundedCornerShape(14.dp)
                    ) { Text(if (english) "+ Add" else "+ जोडा", maxLines = 1) }
                }
                if (automatic) {
                    val listeningOffline = running && (status.contains("Listening offline", true) || status.contains("Mantra matched", true) || status.contains("ऐकत आहे", true) || status.contains("मंत्र जुळला", true))
                    val micPulse = rememberInfiniteTransition(label = "offlineMicPulse")
                    val micScale by micPulse.animateFloat(
                        initialValue = 1f, targetValue = 1.18f,
                        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                            animation = androidx.compose.animation.core.tween(650),
                            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
                        ), label = "micScale"
                    )
                    Surface(
                        Modifier.fillMaxWidth().padding(top = 10.dp),
                        shape = RoundedCornerShape(14.dp),
                        color = if (listeningOffline) Color(0xFFE4F5EA) else Color(0xFFF3EFF8)
                    ) {
                        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 18.dp)) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                                if (listeningOffline) {
                                    Icon(Icons.Default.Mic, contentDescription = null, tint = Color(0xFF16834F), modifier = Modifier.size(22.dp).graphicsLayer(scaleX = micScale, scaleY = micScale))
                                    Text(if (english) "LISTENING OFFLINE" else "ऑफलाइन ऐकत आहे", modifier = Modifier.weight(1f), color = Color(0xFF16834F), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                } else {
                                    Icon(Icons.Default.Mic, contentDescription = null, tint = Color(0xFF76528D), modifier = Modifier.size(20.dp))
                                    Text(if (english) "LIVE RECOGNITION" else "थेट आवाज ओळख", modifier = Modifier.weight(1f), color = Color(0xFF76528D), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                }
                            }
                            if (status.contains("Starting", true) || status.contains("Preparing", true) || status.contains("Reconnecting", true) || status.contains("Checking", true) || status.contains("तयार होत", true) || status.contains("Downloading", true) || status.contains("डाउनलोड", true) || status.contains("Extracting", true) || status.contains("Loading", true) || status.contains("लोड होत", true)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                    Text(if (english) when { status.contains("मॉडेल तयार", true) -> "Offline model ready…"; status.contains("ऐकत आहे", true) -> "Listening offline"; status.contains("माइक पुन्हा", true) -> "Reconnecting microphone…"; status.contains("ओळख यंत्रणा", true) -> "Preparing recognizer…"; else -> status.ifBlank { "Preparing offline model…" } } else status.ifBlank { "ऑफलाइन मॉडेल तयार होत आहे…" }, color = ink, style = MaterialTheme.typography.bodySmall)
                                }
                            } else {
                                Box(Modifier.fillMaxWidth().clipToBounds().padding(top = 6.dp)) {
                                    AnimatedContent(
                                        targetState = transcript.ifBlank {
                                            if (listeningOffline) (if (english) "Listening offline" else status.ifBlank { "ऑफलाइन ऐकत आहे" })
                                            else if (english) when {
                                                status.contains("मॉडेल तयार", true) -> "Offline model ready"
                                                status.contains("माइक पुन्हा", true) -> "Reconnecting microphone…"
                                                status.contains("ओळख यंत्रणा", true) -> "Preparing recognizer…"
                                                else -> status.ifBlank { if (running) "Listening…" else "Starting…" }
                                            } else status.ifBlank { if (running) "ऐकत आहे…" else "सुरू होत आहे…" }
                                        },
                                        transitionSpec = { (slideInHorizontally { it } + fadeIn()) togetherWith (slideOutHorizontally { -it } + fadeOut()) },
                                        label = "recognizedPhraseTicker"
                                    ) { visibleText ->
                                        Text(visibleText,
                                            color = if (matched) (if (listeningOffline) Color(0xFF16834F) else ink).copy(alpha = 0.58f) else if (listeningOffline) Color(0xFF16834F) else ink,
                                            fontWeight = if (matched) FontWeight.Medium else FontWeight.Normal,
                                            style = MaterialTheme.typography.bodyMedium, maxLines = 2)
                                    }
                                }
                                if (matched) Text(
                                    (if (english) "Mantra matched" else "मंत्र जुळला") + if (matchedCount > 0) " · +$matchedCount" else "",
                                    color = Color(0xFF16834F), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
    }
    if (addMantraDialog) {
        AlertDialog(
            onDismissRequest = { addMantraDialog = false },
            title = { Text(if (english) "Add mantra" else "नवीन मंत्र") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(value = newMantraMr, onValueChange = { newMantraMr = it },
                        label = { Text("मंत्र (देवनागरी)") }, singleLine = false)
                    OutlinedTextField(value = newMantraEn, onValueChange = { newMantraEn = it },
                        label = { Text("Mantra (English)") }, singleLine = false)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val devanagari = newMantraMr.trim()
                    val latin = newMantraEn.trim()
                    if (devanagari.isNotBlank() && latin.isNotBlank()) {
                        val added = ChantPreset(latin, devanagari, latin)
                        customMantras = (customMantras + added).distinctBy { it.devanagari }
                        val saved = customMantras.map { it.devanagari + "\u001F" + it.latin }.toSet()
                        val selectedText = if (language == "hi") devanagari else latin
                        prefs.edit().putStringSet("custom_mantras_$categoryId", saved)
                            .putString("target_$categoryId", selectedText).apply()
                        target = selectedText
                        onMantraChange(selectedText)
                        if (running) {
                            transcript = ""
                            matched = false
                            matchedCount = 0
                            status = if (english) "Preparing offline model…" else "ऑफलाइन मॉडेल तयार होत आहे…"
                            ChantRecognitionService.start(context, categoryId, selectedText, language)
                        }
                    }
                    addMantraDialog = false
                }, enabled = newMantraMr.isNotBlank() && newMantraEn.isNotBlank()) { Text(if (english) "Add" else "जोडा") }
            },
            dismissButton = { TextButton(onClick = { addMantraDialog = false }) { Text(if (english) "Cancel" else "रद्द") } }
        )
    }
}


@Composable
fun JapaReportScreen(logs: List<CounterLog>, english: Boolean, ink: Color, onBack: () -> Unit) {
    var month by remember { mutableStateOf(LocalDate.now().withDayOfMonth(1)) }
    var period by remember { mutableStateOf("WEEKLY") }
    var anchor by remember { mutableStateOf(LocalDate.now()) }
    var chartMode by remember { mutableStateOf("BAR") }
    val totalsByDate = remember(logs) { logs.groupBy { it.date }.mapValues { (_, records) -> records.sumOf { it.count } } }
    val activeDates = remember(totalsByDate) { totalsByDate.filterValues { it > 0 }.keys.mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }.toSet() }
    val total = logs.sumOf { it.count }
    val today = LocalDate.now()
    val todayCount = totalsByDate[today.toString()] ?: 0
    val average = if (activeDates.isNotEmpty()) total / activeDates.size else 0
    val best = totalsByDate.maxByOrNull { it.value }
    val currentStreak = run {
        var cursor = if (today in activeDates) today else today.minusDays(1)
        var streak = 0
        while (cursor in activeDates) { streak++; cursor = cursor.minusDays(1) }
        streak
    }
    val longestStreak = run {
        var run = 0
        var bestRun = 0
        var previous: LocalDate? = null
        activeDates.sorted().forEach { date ->
            run = if (previous?.plusDays(1) == date) run + 1 else 1
            bestRun = maxOf(bestRun, run)
            previous = date
        }
        bestRun
    }
    val periodStart = when(period) { "DAILY" -> anchor; "MONTHLY" -> anchor.withDayOfMonth(1); else -> anchor.minusDays(((anchor.dayOfWeek.value + 6) % 7).toLong()) }
    val periodEnd = when(period) { "DAILY" -> anchor; "MONTHLY" -> periodStart.plusMonths(1).minusDays(1); else -> periodStart.plusDays(6) }
    val chartDays = when(period) {
        "DAILY" -> listOf(DayTotal(anchor.toString(), totalsByDate[anchor.toString()] ?: 0))
        "MONTHLY" -> (1..periodStart.lengthOfMonth()).map { d -> val date=periodStart.withDayOfMonth(d); DayTotal(date.toString(), totalsByDate[date.toString()] ?: 0) }
        else -> (0..6).map { i -> val date=periodStart.plusDays(i.toLong()); DayTotal(date.toString(), totalsByDate[date.toString()] ?: 0) }
    }
    val periodSum = chartDays.sumOf { it.total }
    val monthDays = month.lengthOfMonth()
    val offset = (month.dayOfWeek.value + 6) % 7
    val monthCells: List<Int?> = (0 until 42).map { cell -> (cell - offset + 1).takeIf { it in 1..monthDays } }
    val mantraBreakdown = logs.groupBy { it.note.orEmpty().removePrefix("MANTRA:") }.mapValues { (_, rows) -> rows.sumOf { it.count } }.entries.sortedByDescending { it.value }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Icon(Icons.Default.ArrowDropDown, null); Text(if (english) "Back to Japa" else "जपाकडे परत") }
            Spacer(Modifier.weight(1f))
            Text(if (english) "MY JAPA REPORT" else "माझा जप अहवाल", color = ink, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        }
        Text(today.format(java.time.format.DateTimeFormatter.ofPattern("d MMMM yyyy")), color = ink.copy(alpha = .6f), style = MaterialTheme.typography.bodySmall)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StepMetricCard(if (english) "TOTAL JAPA" else "एकूण जप", NumberFormat.getIntegerInstance().format(total), if (english) "${total / 108} malas" else "${total / 108} माळा", ink, Modifier.weight(1f))
            StepMetricCard(if (english) "TODAY" else "आज", NumberFormat.getIntegerInstance().format(todayCount), if (english) "Japa count" else "आजचा जप", ink, Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StepMetricCard(if (english) "CURRENT STREAK" else "सध्याची सलगता", "$currentStreak", if (english) "days" else "दिवस", ink, Modifier.weight(1f))
            StepMetricCard(if (english) "LONGEST STREAK" else "सर्वाधिक सलगता", "$longestStreak", if (english) "days" else "दिवस", ink, Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StepMetricCard(if (english) "ACTIVE DAYS" else "सक्रिय दिवस", "${activeDates.size}", if (english) "All time" else "एकूण", ink, Modifier.weight(1f))
            StepMetricCard(if (english) "AVERAGE / DAY" else "दररोज सरासरी", NumberFormat.getIntegerInstance().format(average), if (english) "Per active day" else "सक्रिय दिवस", ink, Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StepMetricCard(if (english) "BEST DAY" else "सर्वोत्तम दिवस", NumberFormat.getIntegerInstance().format(best?.value ?: 0), best?.key?.let { runCatching { LocalDate.parse(it).format(java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy")) }.getOrDefault(it) } ?: "—", ink, Modifier.weight(1f))
            StepMetricCard(if (english) "TOTAL MALAS" else "पूर्ण माळा", NumberFormat.getIntegerInstance().format(total / 108), if (english) "108 japa each" else "प्रत्येक माळ १०८", ink, Modifier.weight(1f))
        }
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(if (english) month.format(java.time.format.DateTimeFormatter.ofPattern("MMMM yyyy")) else month.format(java.time.format.DateTimeFormatter.ofPattern("MMMM yyyy")), color = ink, fontWeight = FontWeight.Bold)
                    Row {
                        TextButton(onClick = { month = month.minusMonths(1) }) { Text("‹") }
                        TextButton(onClick = { month = month.plusMonths(1).coerceAtMost(today.withDayOfMonth(1)) }) { Text("›") }
                    }
                }
                Row(Modifier.fillMaxWidth()) {
                    (if (english) listOf("Mon","Tue","Wed","Thu","Fri","Sat","Sun") else listOf("सोम","मंगळ","बुध","गुरु","शुक्र","शनि","रवि")).forEach { label ->
                        Text(label, Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall, color = ink.copy(alpha=.6f))
                    }
                }
                monthCells.chunked(7).forEach { weekRow ->
                    Row(Modifier.fillMaxWidth()) {
                        weekRow.forEach { day ->
                            Box(Modifier.weight(1f).height(38.dp).padding(2.dp), contentAlignment = Alignment.Center) {
                                if (day != null) {
                                    val date = month.withDayOfMonth(day)
                                    val value = totalsByDate[date.toString()] ?: 0
                                    Surface(shape = CircleShape, color = when { value >= 108 -> Color(0xFFDDEEE4); date < today && value < 108 -> Color(0xFFF5D9D9); value > 0 -> Color(0xFFE7D8F6); else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha=.35f) }) {
                                        Text(if (value >= 108) "$day ✓" else if (date < today) "$day ×" else day.toString(), modifier = Modifier.padding(6.dp), color = ink, style = MaterialTheme.typography.labelSmall, fontWeight = if (value > 0) FontWeight.Bold else FontWeight.Normal)
                                    }
                                }
                            }
                        }
                    }
                }
                Text(if (english) "Green ✓ = 108+ japa · Red × = missed/under goal" else "हिरवे ✓ = १०८+ जप · लाल × = लक्ष्य अपूर्ण", color = ink.copy(alpha=.55f), style = MaterialTheme.typography.labelSmall)
            }
        }
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (english) "PROGRESS ANALYTICS" else "जप प्रगती विश्लेषण", color = ink, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    SegmentedButtonRowCompat(chartMode, listOf("BAR","LINE"), { chartMode=it }, english)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    listOf("DAILY","WEEKLY","MONTHLY").forEach { p ->
                        FilterChip(selected=period==p,onClick={period=p;anchor=today},label={Text(if(english)p else when(p){"DAILY"->"दैनिक";"WEEKLY"->"साप्ताहिक";else->"मासिक"})})
                    }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.SpaceBetween) {
                    IconButton(onClick={anchor=when(period){"DAILY"->anchor.minusDays(1);"WEEKLY"->anchor.minusWeeks(1);else->anchor.minusMonths(1)}}){Text("‹")}
                    Text(if(period=="DAILY") periodStart.format(java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy")) else periodStart.format(java.time.format.DateTimeFormatter.ofPattern("dd MMM"))+" – "+periodEnd.format(java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy")),color=ink,fontWeight=FontWeight.Medium)
                    IconButton(onClick={anchor=when(period){"DAILY"->anchor.plusDays(1);"WEEKLY"->anchor.plusWeeks(1);else->anchor.plusMonths(1)}.coerceAtMost(today)}){Text("›")}
                }
                JapaActivityChart(chartDays, chartMode, Modifier.fillMaxWidth().height(190.dp), ink)
                Surface(color=Color(0xFFE8DDF4),shape=RoundedCornerShape(18.dp),modifier=Modifier.fillMaxWidth()) {
                    Text((if(english)"Total: " else "एकूण: ")+NumberFormat.getIntegerInstance().format(periodSum)+(if(english)" Japa" else " जप"),modifier=Modifier.padding(12.dp),color=Color(0xFF5C3B85),fontWeight=FontWeight.Bold,textAlign=TextAlign.Center)
                }
            }
        }
        Card(Modifier.fillMaxWidth(), shape=RoundedCornerShape(20.dp), colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(14.dp), verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text(if(english)"YOUR JAPA NOTEBOOK" else "तुमची जप नोंदवही",color=ink,fontWeight=FontWeight.SemiBold)
                Text(if(english)"Last 5 days of japa" else "मागील ५ दिवसांचा जप",color=ink.copy(alpha=.55f),style=MaterialTheme.typography.bodySmall)
                (0..4).map{today.minusDays(it.toLong())}.forEach { date ->
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                        Text(date.format(java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy")),modifier=Modifier.weight(1f),color=ink)
                        Text(NumberFormat.getIntegerInstance().format(totalsByDate[date.toString()]?:0),color=Color(0xFF7855A6),fontWeight=FontWeight.Bold)
                    }
                    if(date!=today.minusDays(4)) Divider(color=ink.copy(alpha=.08f))
                }
            }
        }
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (english) "JAPA BY MANTRA" else "मंत्रानुसार जप", color = ink, fontWeight = FontWeight.SemiBold)
                if (mantraBreakdown.isEmpty()) Text(if (english) "No japa records yet" else "अजून जप नोंद नाही", color = ink.copy(alpha=.6f))
                mantraBreakdown.forEach { (mantra, value) ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(mantra, color = ink, maxLines = 2)
                            Text("${value / 108} malas", color = ink.copy(alpha=.55f), style = MaterialTheme.typography.labelSmall)
                        }
                        Text(NumberFormat.getIntegerInstance().format(value), color = Color(0xFF7855A6), fontWeight = FontWeight.Bold)
                    }
                    LinearProgressIndicator(progress = { if (total > 0) value.toFloat() / total else 0f }, modifier = Modifier.fillMaxWidth().height(5.dp), color = Color(0xFF8B69B5), trackColor = MaterialTheme.colorScheme.surfaceVariant)
                }
            }
        }
    }
}



@Composable
private fun SegmentedButtonRowCompat(selected:String, options:List<String>, onSelect:(String)->Unit, english:Boolean) {
    Row(horizontalArrangement=Arrangement.spacedBy(3.dp)) { options.forEach { o -> FilterChip(selected==o,onClick={onSelect(o)},label={Text(if(english)o else if(o=="BAR")"स्तंभ" else "रेषा",style=MaterialTheme.typography.labelSmall)}) } }
}
@Composable
private fun JapaActivityChart(days:List<DayTotal>, mode:String, modifier:Modifier, ink:Color) {
    Canvas(modifier) {
        val max=days.maxOfOrNull{it.total}?.coerceAtLeast(1)?.toFloat()?:1f
        val bottom=size.height-24.dp.toPx()
        val top=12.dp.toPx()
        val slot=size.width/days.size.coerceAtLeast(1)
        if(mode=="BAR") days.forEachIndexed { i,d ->
            val h=(bottom-top)*d.total/max
            drawRoundRect(brush=Brush.verticalGradient(listOf(Color(0xFF9B7BC4),Color(0xFF6C4B9B)),startY=bottom-h,endY=bottom),topLeft=Offset(i*slot+slot*.2f,bottom-h),size=Size(slot*.6f,h),cornerRadius=androidx.compose.ui.geometry.CornerRadius(8.dp.toPx()))
        } else {
            val points=days.mapIndexed { i,d -> Offset(i*slot+slot/2,bottom-(bottom-top)*d.total/max) }
            points.zipWithNext().forEach { (a,b)->drawLine(Color(0xFF7655A5),a,b,strokeWidth=3.dp.toPx()) }
            points.forEach { drawCircle(Color(0xFF7655A5),4.dp.toPx(),it) }
        }
        days.forEachIndexed { i,d -> drawContext.canvas.nativeCanvas.drawText(if(days.size<=7) runCatching{LocalDate.parse(d.date).format(java.time.format.DateTimeFormatter.ofPattern("EEE"))}.getOrDefault("") else runCatching{LocalDate.parse(d.date).dayOfMonth.toString()}.getOrDefault(""),i*slot+slot/2,bottom+17.dp.toPx(),android.graphics.Paint().apply{color=ink.toArgb();textSize=10.dp.toPx();textAlign=android.graphics.Paint.Align.CENTER}) }
    }
}


@Composable
fun ExperimentalBabyMotionControl(activeSessionId: Long?, english: Boolean, ink: Color, onDetected: (Long, Float) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val manager = remember { context.getSystemService(Context.SENSOR_SERVICE) as SensorManager }
    val linear = remember { manager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION) }
    val gyro = remember { manager.getDefaultSensor(Sensor.TYPE_GYROSCOPE) }
    var enabled by remember(activeSessionId) { mutableStateOf(false) }
    var calibrated by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }

    DisposableEffect(enabled, activeSessionId, linear, gyro, lifecycleOwner) {
        if (enabled && activeSessionId != null && linear != null) {
            val startedAt = System.currentTimeMillis()
            val samples = ArrayList<Float>()
            var baseline = 0f
            var threshold = 0.55f
            var gyroMagnitude = 0f
            var lastDetection = 0L
            var calibratedLocal = false
            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    if (event.sensor.type == Sensor.TYPE_GYROSCOPE) {
                        val v = event.values
                        gyroMagnitude = sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2])
                        return
                    }
                    if (event.sensor.type != Sensor.TYPE_LINEAR_ACCELERATION) return
                    val v = event.values
                    val magnitude = sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2])
                    if (!calibratedLocal) {
                        samples.add(magnitude)
                        if (System.currentTimeMillis() - startedAt >= 8000L && samples.size >= 40) {
                            val sorted = samples.sorted()
                            baseline = sorted[sorted.size / 2]
                            val deviations = sorted.map { kotlin.math.abs(it - baseline) }.sorted()
                            val mad = deviations[deviations.size / 2]
                            threshold = maxOf(0.55f, baseline + 5f * mad)
                            calibratedLocal = true
                            calibrated = true
                            status = if (english) "Baseline set · experimental only" else "मूलभूत कंपन नोंदले · प्रायोगिक"
                        }
                        return
                    }
                    val spike = magnitude > maxOf(threshold, baseline + 0.65f)
                    val quietRotation = gyro == null || gyroMagnitude < 1.2f
                    val now = System.currentTimeMillis()
                    if (spike && quietRotation && now - lastDetection > 2200L) {
                        lastDetection = now
                        onDetected(now, magnitude)
                        status = if (english) "Moment saved for review" else "संकेत तपासणीसाठी जतन केला"
                    } else if (!spike) {
                        baseline = baseline * 0.995f + magnitude * 0.005f
                    }
                }
                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
            }
            var registered = false
            val registerSensors = {
                if (!registered) {
                    manager.registerListener(listener, linear, SensorManager.SENSOR_DELAY_GAME)
                    gyro?.let { manager.registerListener(listener, it, SensorManager.SENSOR_DELAY_GAME) }
                    registered = true
                }
            }
            val lifecycleObserver = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_START -> registerSensors()
                    Lifecycle.Event.ON_STOP -> { manager.unregisterListener(listener); registered = false }
                    else -> Unit
                }
            }
            lifecycleOwner.lifecycle.addObserver(lifecycleObserver)
            if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) registerSensors()
            calibrated = false
            status = if (english) "Keep phone still for 8-second baseline…" else "८ सेकंद फोन स्थिर ठेवा…"
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
                manager.unregisterListener(listener)
            }
        } else {
            calibrated = false
            onDispose { }
        }
    }

    if (activeSessionId != null) {
        Card(Modifier.fillMaxWidth().padding(top = 10.dp), shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.fillMaxWidth().padding(12.dp)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f).padding(end = 10.dp)) {
                        Text(if (english) "Auto detect" else "स्वयंचलित ओळख", color = ink, fontWeight = FontWeight.SemiBold)
                    }
                    androidx.compose.material3.Switch(checked = enabled, onCheckedChange = { enabled = it }, enabled = linear != null)
                }
                if (linear == null) {
                    Text(if (english) "This phone does not expose a linear-acceleration sensor." else "या फोनमध्ये linear-acceleration सेन्सर उपलब्ध नाही.", color = Color(0xFFB45353), style = MaterialTheme.typography.labelSmall)
                }
                if (enabled && linear != null) {
                    Text(status.ifBlank { if (calibrated) "Ready" else if (english) "Calibrating…" else "कॅलिब्रेट होत आहे…" },
                        color = ink.copy(alpha = .6f), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}


@Composable
fun BabyMotionSensorEngine(
    activeSessionId: Long,
    enabled: Boolean,
    onDetected: (Long, Float) -> Unit,
    onStatus: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val manager = remember { context.getSystemService(Context.SENSOR_SERVICE) as SensorManager }
    val linear = remember { manager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION) }
    val gyro = remember { manager.getDefaultSensor(Sensor.TYPE_GYROSCOPE) }
    val detectedCallback by rememberUpdatedState(onDetected)
    val statusCallback by rememberUpdatedState(onStatus)

    DisposableEffect(enabled, activeSessionId, linear, gyro, lifecycleOwner) {
        if (enabled && linear != null) {
            val startedAt = System.currentTimeMillis()
            val samples = ArrayList<Float>()
            var baseline = 0f
            var threshold = 0.55f
            var gyroMagnitude = 0f
            var lastDetection = 0L
            var calibrated = false
            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    if (event.sensor.type == Sensor.TYPE_GYROSCOPE) {
                        val v = event.values
                        gyroMagnitude = sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2])
                        return
                    }
                    if (event.sensor.type != Sensor.TYPE_LINEAR_ACCELERATION) return
                    val v = event.values
                    val magnitude = sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2])
                    if (!calibrated) {
                        samples.add(magnitude)
                        if (System.currentTimeMillis() - startedAt >= 8000L && samples.size >= 40) {
                            val sorted = samples.sorted()
                            baseline = sorted[sorted.size / 2]
                            val deviations = sorted.map { kotlin.math.abs(it - baseline) }.sorted()
                            threshold = maxOf(0.55f, baseline + 5f * deviations[deviations.size / 2])
                            calibrated = true
                            statusCallback(if (true) "Ready" else "Ready")
                        }
                        return
                    }
                    val spike = magnitude > maxOf(threshold, baseline + 0.65f)
                    val quietRotation = gyro == null || gyroMagnitude < 1.2f
                    val now = System.currentTimeMillis()
                    if (spike && quietRotation && now - lastDetection > 2200L) {
                        lastDetection = now
                        detectedCallback(now, magnitude)
                        statusCallback("Ready")
                    } else if (!spike) baseline = baseline * 0.995f + magnitude * 0.005f
                }
                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
            }
            var registered = false
            val register = {
                if (!registered) {
                    manager.registerListener(listener, linear, SensorManager.SENSOR_DELAY_GAME)
                    gyro?.let { manager.registerListener(listener, it, SensorManager.SENSOR_DELAY_GAME) }
                    registered = true
                }
            }
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_START -> register()
                    Lifecycle.Event.ON_STOP -> { manager.unregisterListener(listener); registered = false }
                    else -> Unit
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) register()
            statusCallback(if (linear == null) "Sensor unavailable" else "Calibrating")
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
                manager.unregisterListener(listener)
            }
        } else {
            onDispose { }
        }
    }
}

