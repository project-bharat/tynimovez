package com.projectbharat.counterapp

import android.content.Intent
import android.os.Build
import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PregnantWoman
import androidx.compose.foundation.interaction.MutableInteractionSource
import android.os.Bundle
import android.media.ToneGenerator
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import java.io.File
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.*
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.projectbharat.counterapp.data.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
private fun BilanLaunchSplash() {
    val dark = isSystemInDarkTheme()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (dark) Color(0xFF31413A) else Color(0xFFF3EEE3)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.splash_logo),
                contentDescription = "Bilan Counter logo",
                modifier = Modifier.size(164.dp),
                contentScale = ContentScale.Fit
            )
            Spacer(modifier = Modifier.height(18.dp))
            Text(
                text = "Bilan Counter",
                color = if (dark) Color(0xFFF3EEE3) else Color(0xFF31413A),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

class MainActivity : ComponentActivity() {
    var launchCounterKind by mutableStateOf<String?>(null)
        private set
    var launchCounterQuickStart by mutableStateOf(false)
        private set
    var launchBabyVoice by mutableStateOf(false)
        private set
    fun consumeLaunchCounterKind() { launchCounterKind = null; launchCounterQuickStart = false; launchBabyVoice = false }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == ACTION_OPEN_COUNTER) {
            launchCounterKind = intent.getStringExtra(CounterQuickActions.EXTRA_COUNTER_KIND)
            launchCounterQuickStart = intent.getBooleanExtra("quick_start", false)
            launchBabyVoice = intent.getBooleanExtra("baby_voice", false)
        }
    }

    override fun onResume() {
        super.onResume()
        QuickActionsNotification.show(this)
    }

    override fun onStop() {
        // Baby movement tracking continues while the UI is backgrounded.
        super.onStop()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        launchCounterKind = intent.getStringExtra(CounterQuickActions.EXTRA_COUNTER_KIND)
        launchCounterQuickStart = intent.getBooleanExtra("quick_start", false)
        launchBabyVoice = intent.getBooleanExtra("baby_voice", false)
        // Use the Android SplashScreen API defaults. Android removes the splash
        // automatically as soon as the first app frame is ready.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        SpeechModelDownloadScheduler.enqueueFirstInstall(this)
        DailyReminderScheduler.rescheduleAll(this)
        SeedData.initialize(this)
        val repo = CounterRepository(CounterDatabase.get(this).dao())
        setContent {
            var showBrandSplash by remember { mutableStateOf(true) }
            LaunchedEffect(Unit) {
                delay(900)
                showBrandSplash = false
            }
            if (showBrandSplash) {
                BilanLaunchSplash()
            } else {
                CounterApp(repo)
            }
        }
    }

    companion object {
        const val ACTION_OPEN_COUNTER = "com.projectbharat.counterapp.action.OPEN_COUNTER"
    }
}

class CounterViewModel(private val repo: CounterRepository) : ViewModel() {
    val categories = repo.categories.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    fun count(id: Long) = repo.today(id)
    fun logsForDay(id: Long, date: LocalDate) = repo.logsForDay(id, date)
    suspend fun updateLog(log: CounterLog) = repo.updateLog(log)
    suspend fun deleteLog(id: Long) = repo.deleteLog(id)
    fun week(id: Long) = repo.week(id)
    fun averageBabyTimeToTen(id: Long) = repo.averageBabyTimeToTen(id)
    fun sessions(id: Long, date: String) = repo.babySessionsForDay(id, date)
    fun monthSessions(id: Long, month: LocalDate) = repo.babySessionsForMonth(id, month)
    fun latestSession(id: Long) = repo.latestBabySession(id)
    fun detections(sessionId: Long) = repo.babyDetections(sessionId)
    fun detectionsForDay(categoryId: Long, date: String) = repo.babyDetectionsForDay(categoryId, date)
    suspend fun recordDetection(sessionId: Long, timestamp: Long, magnitude: Float) = repo.recordBabyDetection(sessionId, timestamp, magnitude)
    suspend fun confirmDetection(id: Long) = repo.confirmBabyDetection(id)
    suspend fun dismissDetection(id: Long) = repo.dismissBabyDetection(id)
    suspend fun startSession(id: Long) = repo.startBabySession(id)
    suspend fun addMovement(sessionId: Long) = repo.addBabyMovement(sessionId, null)
    suspend fun undoMovement(sessionId: Long) = repo.undoBabyMovement(sessionId)
    suspend fun finishSession(sessionId: Long, strength: String?) = repo.finishBabySession(sessionId, strength)
    suspend fun updateBabySessionRecord(session: BabySession) = repo.updateBabySessionRecord(session)
    suspend fun resumeSession(sessionId: Long) = repo.resumeBabySession(sessionId)
    suspend fun completeSession(sessionId: Long) = repo.completeExpiredBabySession(sessionId)
    class Factory(private val repo: CounterRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = CounterViewModel(repo) as T
    }
}

private val Navy = Color(0xFF102E55)
private val Gold = Color(0xFFE7A33C)
private val Cream = Color(0xFFF5F0E6)
private val SoftBlue = Color(0xFFDCE9F7)
private val zone = ZoneId.systemDefault()
private val dateFormat = DateTimeFormatter.ofPattern("dd MMM yyyy")

private fun sessionTime(timestamp: Long): String =
    Instant.ofEpochMilli(timestamp).atZone(zone).format(DateTimeFormatter.ofPattern("hh:mm a"))

private fun periodLabel(period: String, english: Boolean): String = when (period) {
    "MORNING" -> if (english) "Morning" else "सकाळ"
    "AFTERNOON" -> if (english) "Afternoon" else "दुपार"
    "EVENING" -> if (english) "Evening" else "संध्याकाळ"
    else -> if (english) "Night" else "रात्र"
}

@Composable
fun CounterApp(repo: CounterRepository) {
    val vm: CounterViewModel = viewModel(factory = CounterViewModel.Factory(repo))
    val categories by vm.categories.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var selectedId by remember { mutableLongStateOf(1L) }
    var english by remember(context) {
        mutableStateOf(
            context.getSharedPreferences("app_preferences", android.content.Context.MODE_PRIVATE)
                .getBoolean("english_language", false)
        )
    }
    LaunchedEffect(english) {
        context.getSharedPreferences("app_preferences", android.content.Context.MODE_PRIVATE)
            .edit().putBoolean("english_language", english).apply()
    }
    var weekly by remember { mutableStateOf(false) }
    var monthlyHistory by remember { mutableStateOf(false) }
    var historyMonth by remember { mutableStateOf(LocalDate.now().withDayOfMonth(1)) }
    var remindersScreen by remember { mutableStateOf(false) }
    var pregnancyScreen by remember { mutableStateOf(false) }
    androidx.activity.compose.BackHandler(enabled = pregnancyScreen) { pregnancyScreen = false }
    var backupScreen by remember { mutableStateOf(false) }
    var voiceModelsDialog by remember { mutableStateOf(false) }
    var backupMessage by remember { mutableStateOf<String?>(null) }
    var pendingRestoreUri by remember { mutableStateOf<android.net.Uri?>(null) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    var finishDialog by remember { mutableStateOf(false) }
    var finalStrength by remember { mutableStateOf("MEDIUM") }
    var historyDate by remember { mutableStateOf(LocalDate.now()) }
    var clock by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val scope = rememberCoroutineScope()
    val activity = context as? MainActivity
    val shortcutKind = activity?.launchCounterKind
    val shortcutQuickStart = activity?.launchCounterQuickStart == true
    val shortcutBabyVoice = activity?.launchBabyVoice == true
    var autoStartBabyVoice by remember { mutableStateOf(false) }
    LaunchedEffect(shortcutBabyVoice) { if (shortcutBabyVoice) autoStartBabyVoice = true }
    var showPermissionIntro by remember {
        mutableStateOf(!context.getSharedPreferences("app_setup", android.content.Context.MODE_PRIVATE).getBoolean("permission_intro_seen", false))
    }
    val firstRunPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
        context.getSharedPreferences("app_setup", android.content.Context.MODE_PRIVATE).edit().putBoolean("permission_intro_seen", true).apply()
        showPermissionIntro = false
        QuickActionsNotification.show(context)
    }
    val firstRunPermissions = if (Build.VERSION.SDK_INT >= 33) arrayOf(Manifest.permission.POST_NOTIFICATIONS) else emptyArray()
    val jsonExportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            backupMessage = null
            runCatching { withContext(Dispatchers.IO) { CounterBackupManager.exportJson(context, uri) } }
                .onSuccess { backupMessage = if (english) "Backup saved successfully." else "बॅकअप यशस्वीपणे जतन झाला." }
                .onFailure { backupMessage = (if (english) "Backup failed: " else "बॅकअप अयशस्वी: ") + (it.message ?: "Unknown error") }
        }
    }
    val csvExportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) scope.launch {
            backupMessage = null
            runCatching { withContext(Dispatchers.IO) { CounterBackupManager.exportCsv(context, uri) } }
                .onSuccess { backupMessage = if (english) "CSV exported successfully." else "CSV निर्यात यशस्वी." }
                .onFailure { backupMessage = (if (english) "CSV export failed: " else "CSV निर्यात अयशस्वी: ") + (it.message ?: "Unknown error") }
        }
    }
    val jsonImportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        pendingRestoreUri = uri
    }
    val haptic = LocalHapticFeedback.current
    val clickTone = remember { ToneGenerator(android.media.AudioManager.STREAM_SYSTEM, 35) }
    DisposableEffect(clickTone) { onDispose { clickTone.release() } }
    LaunchedEffect(categories) {
        CounterQuickActions.publishShortcuts(context, categories)
        CounterHomeWidget.refresh(context)
    }
    LaunchedEffect(categories, shortcutKind, shortcutQuickStart, shortcutBabyVoice) {
        if (shortcutKind == "BABY") {
            categories.firstOrNull { it.kind == "BABY" }?.let { baby ->
                selectedId = baby.id
                remindersScreen = false
                pregnancyScreen = false
                backupScreen = false
                weekly = false
                monthlyHistory = false
                if (shortcutQuickStart) scope.launch { vm.startSession(baby.id) }
                if (shortcutBabyVoice) autoStartBabyVoice = true
            }
        }
        if (shortcutKind != null) activity?.consumeLaunchCounterKind()
    }
    val selected = categories.firstOrNull { it.id == selectedId } ?: categories.firstOrNull()
    LaunchedEffect(categories) { if (categories.isNotEmpty() && categories.none { it.id == selectedId }) selectedId = categories.first().id }

    val countFlow = remember(selected?.id) { selected?.let { vm.count(it.id) } ?: flowOf(0) }
    val weekFlow = remember(selected?.id) { selected?.let { vm.week(it.id) } ?: flowOf(emptyList()) }
    val averageFlow = remember(selected?.id) { selected?.let { vm.averageBabyTimeToTen(it.id) } ?: flowOf(null) }
    val sessionFlow = remember(selected?.id, historyDate) { selected?.let { vm.sessions(it.id, historyDate.toString()) } ?: flowOf(emptyList()) }
    val historyLogsFlow = remember(selected?.id, historyDate) { selected?.let { vm.logsForDay(it.id, historyDate) } ?: flowOf(emptyList()) }
    val latestFlow = remember(selected?.id) { selected?.let { vm.latestSession(it.id) } ?: flowOf(null) }
    val monthFlow = remember(selected?.id, historyMonth) { selected?.let { vm.monthSessions(it.id, historyMonth) } ?: flowOf(emptyList()) }
    val count by countFlow.collectAsStateWithLifecycle(0)
    LaunchedEffect(count, selected?.id) { CounterHomeWidget.refresh(context) }
    val days by weekFlow.collectAsStateWithLifecycle(emptyList())
    val average by averageFlow.collectAsStateWithLifecycle(null)
    val sessions by sessionFlow.collectAsStateWithLifecycle(emptyList())
    val historyLogs by historyLogsFlow.collectAsStateWithLifecycle(emptyList())
    val latestSession by latestFlow.collectAsStateWithLifecycle(null)
    LaunchedEffect(latestSession?.id, clock) { latestSession?.let { val limit = CounterRepository.TWO_HOURS_MS; if (it.endedAt == null && clock >= it.startedAt + limit) vm.completeSession(it.id) } }
    val monthSessions by monthFlow.collectAsStateWithLifecycle(emptyList())
    val isBaby = selected?.kind == "BABY"
    val activeSession = latestSession?.takeIf { val limit = CounterRepository.TWO_HOURS_MS; it.endedAt == null && clock < it.startedAt + limit }
    val resumableSession = latestSession?.takeIf { val limit = CounterRepository.TWO_HOURS_MS; it.endedAt != null && clock < it.startedAt + limit }
    val lockedSession = latestSession?.takeIf { val limit = CounterRepository.TWO_HOURS_MS; clock < it.startedAt + limit }
    LaunchedEffect(activeSession?.id, activeSession?.movementCount, english) {
        if (activeSession != null && selected != null) {
            BabySessionNotification.show(context, activeSession.id, selected.id, activeSession.movementCount, english)
        } else BabySessionNotification.cancel(context)
    }
    LaunchedEffect(activeSession?.id) { if (activeSession == null) autoStartBabyVoice = false }
    val remainingMs = activeSession?.let { val limit = CounterRepository.TWO_HOURS_MS; (it.startedAt + limit - clock).coerceAtLeast(0L) } ?: 0L
    val detectionFlow = remember(selected?.id, historyDate) { selected?.let { vm.detectionsForDay(it.id, historyDate.toString()) } ?: flowOf(emptyList()) }
    val detections by detectionFlow.collectAsStateWithLifecycle(emptyList())
    val dark = isSystemInDarkTheme()
    val page = if (dark) Color(0xFF0B1420) else Cream
    val ink = if (dark) Color(0xFFF2F5F8) else Navy
    val tapSurface = if (dark) Color(0xFF263D52) else SoftBlue
    val scheme = if (dark) darkColorScheme(primary = Color(0xFFB8D5F2), onPrimary = Color(0xFF102238), secondary = Color(0xFFE3BD73), onSecondary = Color(0xFF30230C), background = Color(0xFF0B1420), onBackground = Color(0xFFF2F5F8), surface = Color(0xFF172638), onSurface = Color(0xFFF2F5F8), surfaceVariant = Color(0xFF22384D), onSurfaceVariant = Color(0xFFC4D0DC), outline = Color(0xFF52677B))
                 else lightColorScheme(primary = Navy, secondary = Gold, background = page, surface = Color.White)

    MaterialTheme(colorScheme = scheme) {
        if (showPermissionIntro) {
            AlertDialog(
                onDismissRequest = {
                    context.getSharedPreferences("app_setup", android.content.Context.MODE_PRIVATE).edit().putBoolean("permission_intro_seen", true).apply()
                    showPermissionIntro = false
                },
                title = { Text(if (english) "Set up device access" else "फोन परवानग्या सेट करा") },
                text = { Text(if (english) "Baby movement tracking uses notifications and microphone access for voice counting." else "बाळाच्या हालचालींची मोजणी व आवाजाद्वारे मोजणीसाठी सूचना आणि मायक्रोफोन परवानगी आवश्यक आहे.") },
                confirmButton = {
                    TextButton(onClick = { firstRunPermissionLauncher.launch(firstRunPermissions) }) {
                        Text(if (english) "Continue" else "पुढे")
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        context.getSharedPreferences("app_setup", android.content.Context.MODE_PRIVATE).edit().putBoolean("permission_intro_seen", true).apply()
                        showPermissionIntro = false
                    }) { Text(if (english) "Not now" else "आत्ता नको") }
                }
            )
        }
        if (voiceModelsDialog) VoiceModelsDialog(english = english, onDismiss = { voiceModelsDialog = false })
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet {
                    Column(Modifier.fillMaxWidth().padding(18.dp)) {
                        Text(if (english) "BABY MOVEMENT COUNTER" else "बाळाच्या हालचाली", style = MaterialTheme.typography.titleLarge, color = ink, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(14.dp))
                        categories.forEach { cat ->
                            val glyph = Icons.Default.PregnantWoman
                            NavigationDrawerItem(
                                icon = { Icon(glyph, null) },
                                label = { Text(if (english) "Baby Movements" else "बाळाची हालचाल") },
                                selected = selected?.id == cat.id && !remindersScreen && !pregnancyScreen,
                                onClick = { pregnancyScreen = false; selectedId = cat.id; remindersScreen = false; weekly = false; monthlyHistory = false; backupScreen = false; scope.launch { drawerState.close() } }
                            )
                        }
                        
                        Divider(Modifier.padding(vertical = 8.dp))
                        NavigationDrawerItem(
                            icon = { Icon(Icons.Default.PregnantWoman, null) },
                            label = { Text(if (english) "Pregnancy timeline" else "गर्भावस्थेची कालरेषा") },
                            selected = pregnancyScreen,
                            onClick = {
                                pregnancyScreen = true
                                remindersScreen = false
                                backupScreen = false
                                weekly = false
                                monthlyHistory = false
                                scope.launch { drawerState.close() }
                            }
                        )
                        NavigationDrawerItem(icon = { Icon(Icons.Default.NotificationsActive, null) }, label = { Text(if (english) "Reminders" else "स्मरणपत्रे") }, selected = remindersScreen, onClick = { pregnancyScreen = false; remindersScreen = true; weekly = false; monthlyHistory = false; backupScreen = false; scope.launch { drawerState.close() } })
                        
                        NavigationDrawerItem(icon = { Icon(Icons.Default.Save, null) }, label = { Text(if (english) "Backup & export" else "बॅकअप व निर्यात") }, selected = backupScreen, onClick = { pregnancyScreen = false; backupScreen = true; remindersScreen = false; weekly = false; monthlyHistory = false; scope.launch { drawerState.close() } })
                        NavigationDrawerItem(icon = { Icon(Icons.Default.Download, null) }, label = { Text(if (english) "AI voice setup" else "AI आवाज सेटअप") }, selected = voiceModelsDialog, onClick = { voiceModelsDialog = true; scope.launch { drawerState.close() } })
                        NavigationDrawerItem(icon = { Icon(Icons.Default.BarChart, null) }, label = { Text(if (english) "Weekly report" else "साप्ताहिक अहवाल") }, selected = weekly, onClick = { pregnancyScreen = false; weekly = true; remindersScreen = false; monthlyHistory = false; backupScreen = false; scope.launch { drawerState.close() } })
                        NavigationDrawerItem(icon = { Icon(Icons.Default.History, null) }, label = { Text(if (english) "History" else "इतिहास") }, selected = monthlyHistory, onClick = { pregnancyScreen = false; monthlyHistory = true; remindersScreen = false; weekly = false; backupScreen = false; scope.launch { drawerState.close() } })
                        NavigationDrawerItem(
                            icon = { Icon(Icons.Default.Language, null) },
                            label = { Text(if (english) "मराठी" else "English") },
                            selected = false,
                            onClick = {
                                val nextLanguage = !english
                                english = nextLanguage
                                context.getSharedPreferences("app_preferences", android.content.Context.MODE_PRIVATE)
                                    .edit().putBoolean("english_language", nextLanguage).apply()
                                scope.launch { drawerState.close() }
                            }
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            if (english) "Designed & developed by Bharat Rasave" else "निर्मिती व संकल्पना · Bharat Rasave",
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = .55f),
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 18.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        ) {
        Surface(Modifier.fillMaxSize(), color = page) {
            Column(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 12.dp)) {
                Row(
                    Modifier.fillMaxWidth()
                        .background(Color(0xFF31413A), RoundedCornerShape(22.dp))
                        .padding(horizontal = 10.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { scope.launch { drawerState.open() } }) { Icon(Icons.Default.Menu, null, tint = Color(0xFFF3EEE3)) }
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.bilan_header_logo_light),
                            contentDescription = "Bilan",
                            modifier = Modifier.width(94.dp).height(32.dp),
                            contentScale = ContentScale.Fit
                        )
                        Text(
                            if (english) "Baby Movement Counter" else "बाळाची हालचाल",
                            color = Color(0xFFF3EEE3),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = {
                        pregnancyScreen = false
                        remindersScreen = true; weekly = false; monthlyHistory = false
                        backupScreen = false
                    }) {
                        Icon(Icons.Default.NotificationsActive, contentDescription = if (english) "Reminders" else "स्मरणपत्रे", tint = Color(0xFFF3EEE3))
                    }
                    IconButton(onClick = {
                        historyDate = LocalDate.now()
                        historyMonth = LocalDate.now().withDayOfMonth(1)
                        pregnancyScreen = false
                        monthlyHistory = true; remindersScreen = false; weekly = false
                        backupScreen = false
                    }) {
                        Icon(Icons.Default.History, contentDescription = if (english) "History" else "इतिहास", tint = Color(0xFFF3EEE3))
                    }
                }
                Spacer(Modifier.height(12.dp))

                Column(Modifier.weight(1f)) {
                Box(Modifier.weight(1f)) {
                if (pregnancyScreen) {
                    PregnancyCalculatorScreen(english)
                } else if (backupScreen) {
                    BackupSettingsScreen(english = english, ink = ink, message = backupMessage,
                        onExportBackup = { jsonExportLauncher.launch("daily-counter-backup.json") },
                        onImportBackup = { jsonImportLauncher.launch(arrayOf("application/json", "text/*")) },
                        onExportCsv = { csvExportLauncher.launch("daily-counter-history.csv") })
                } else if (remindersScreen) {
                    ReminderSettingsScreen(english = english, ink = ink)
                } else
                if (monthlyHistory && isBaby && selected != null) {
                    MonthlyHistoryScreen(
                        english = english, ink = ink, month = historyMonth, sessions = monthSessions,
                        onPrevious = { historyMonth = historyMonth.minusMonths(1) },
                        onNext = { if (historyMonth.isBefore(LocalDate.now().withDayOfMonth(1))) historyMonth = historyMonth.plusMonths(1) },
                        onExportPdf = { exportMonthlyPdf(context, selected.titleMr, selected.titleEn, historyMonth, monthSessions, english) },
                        onEditSession = { session -> scope.launch { vm.updateBabySessionRecord(session) } }
                    )
                } else if (monthlyHistory && selected != null) {
                    CounterHistoryScreen(
                        english = english, ink = ink, date = historyDate, logs = historyLogs, days = days,
                        onPrevious = { historyDate = historyDate.minusDays(1) },
                        onNext = { if (historyDate.isBefore(LocalDate.now())) historyDate = historyDate.plusDays(1) },
                        onUpdate = { log -> scope.launch { vm.updateLog(log) } },
                        onDelete = { log -> scope.launch { vm.deleteLog(log.id) } }
                    )
                } else if (weekly) {
                } else if (weekly) {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            StatTile(if (english) "TOTAL" else "एकूण", days.sumOf { it.total }.toString(), ink, Modifier.weight(1f))
                            StatTile(if (english) "DAILY AVG" else "दररोज सरासरी", "%.1f".format(days.sumOf { it.total }.toDouble() / 7.0), ink, Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(14.dp))
                        WeeklyChart(days, Modifier.fillMaxWidth().height(210.dp))
                        if (isBaby) Text((if (english) "Average time to 10 movements: " else "१० हालचालींसाठी सरासरी वेळ: ") + (average?.let { "%.1f min".format(it / 60000.0) } ?: "—"))
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = {
                            val lines = buildString {
                                append(if (english) "Last 7 days\n" else "मागील ७ दिवसांची नोंद\n")
                                days.forEach { append("${it.date}: ${it.total}\n") }
                                append(if (english) "Total: " else "एकूण: "); append(days.sumOf { it.total })
                            }
                            val send = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, lines) }
                            context.startActivity(Intent.createChooser(send, if (english) "Share summary" else "सारांश शेअर करा"))
                        }) { Text(if (english) "Share summary" else "सारांश शेअर करा") }
                    }
                } else if (isBaby && selected != null) {
                    BabyMovementScreen(
                        autoStartVoice = autoStartBabyVoice,
                        onAutoStartVoiceConsumed = { autoStartBabyVoice = false },
                        english = english,
                        ink = ink,
                        tapSurface = tapSurface,
                        latestSession = latestSession,
                        activeSession = activeSession,
                        resumableSession = resumableSession,
                        lockedSession = lockedSession,
                        remainingMs = remainingMs,
                        sessions = sessions,
                        detections = detections,
                        recentLogs = historyLogs,
                        onViewHistory = { historyDate = LocalDate.now(); historyMonth = LocalDate.now().withDayOfMonth(1); monthlyHistory = true },
                        historyDate = historyDate,
                        onPreviousDay = { historyDate = historyDate.minusDays(1) },
                        onNextDay = { if (historyDate.isBefore(LocalDate.now())) historyDate = historyDate.plusDays(1) },
                        onStart = { scope.launch { vm.startSession(selected.id) } },
                        onResume = { sessionId -> scope.launch { vm.resumeSession(sessionId) } },
                        onTap = { sessionId ->
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            clickTone.startTone(ToneGenerator.TONE_PROP_BEEP, 65)
                            scope.launch { vm.addMovement(sessionId) }
                        },
                        onUndo = { sessionId -> scope.launch { vm.undoMovement(sessionId) } },
                        onFinish = { finishDialog = true },
                        onAutoMovement = { sessionId, timestamp, magnitude -> scope.launch { vm.recordDetection(sessionId, timestamp, magnitude) } },
                        onConfirmDetections = { ids -> scope.launch { ids.forEach { vm.confirmDetection(it) } } },
                        onDismissDetections = { ids -> scope.launch { ids.forEach { vm.dismissDetection(it) } } }
                    )
                }
                }
            }
        }
        }
    }

    }
    pendingRestoreUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingRestoreUri = null },
            title = { Text(if (english) "Replace app data?" else "सध्याचा डेटा बदलायचा?") },
            text = { Text(if (english) "Restoring replaces all counters, history and baby movement sessions on this device. Export a backup first if you need to keep the current data." else "बॅकअप पुनर्संचयित केल्याने या उपकरणावरील सर्व मोजण्या, इतिहास आणि बाळाच्या हालचालींची सत्रे बदलली जातील. सध्याचा डेटा हवा असल्यास आधी बॅकअप घ्या.") },
            confirmButton = {
                TextButton(onClick = {
                    pendingRestoreUri = null
                    scope.launch {
                        backupMessage = null
                        runCatching { withContext(Dispatchers.IO) { CounterBackupManager.restoreJson(context, uri) } }
                            .onSuccess { backupMessage = if (english) "Backup restored successfully." else "बॅकअप यशस्वीपणे पुनर्संचयित झाला." }
                            .onFailure { backupMessage = (if (english) "Restore failed: " else "पुनर्संचयित करणे अयशस्वी: ") + (it.message ?: "Unknown error") }
                    }
                }) { Text(if (english) "Replace data" else "डेटा बदला") }
            },
            dismissButton = { TextButton(onClick = { pendingRestoreUri = null }) { Text(if (english) "Cancel" else "रद्द") } }
        )
    }
    if (finishDialog && activeSession != null) {
        AlertDialog(
            onDismissRequest = { finishDialog = false },
            title = { Text(if (english) "Rate movement strength" else "बाळाच्या हालचालींची ताकद नोंदवा") },
            text = {
                Column {
                    Text(if (english) "How strong did the movements feel during this session?" else "या सत्रातील हालचालींची ताकद कशी जाणवली?")
                    listOf("LIGHT", "MEDIUM", "STRONG").forEach { value ->
                        val label = when (value) { "LIGHT" -> if (english) "Light" else "हलकी"; "MEDIUM" -> if (english) "Medium" else "मध्यम"; else -> if (english) "Strong" else "जोरात" }
                        Row(Modifier.fillMaxWidth().clickable { finalStrength = value }.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = finalStrength == value, onClick = { finalStrength = value })
                            Text(label)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { scope.launch { vm.finishSession(activeSession.id, finalStrength) }; finishDialog = false }) { Text(if (english) "Save session" else "सत्र जतन करा") } },
            dismissButton = { TextButton(onClick = { finishDialog = false }) { Text(if (english) "Cancel" else "रद्द") } }
        )
    }
}

@Composable
private fun BabyMovementScreen(
    autoStartVoice: Boolean, onAutoStartVoiceConsumed: () -> Unit,
    english: Boolean, ink: Color, tapSurface: Color, latestSession: BabySession?, activeSession: BabySession?,
    resumableSession: BabySession?, lockedSession: BabySession?, remainingMs: Long, sessions: List<BabySession>, detections: List<BabyDetection>, recentLogs: List<CounterLog>, onViewHistory: () -> Unit, historyDate: LocalDate,
    onPreviousDay: () -> Unit, onNextDay: () -> Unit, onStart: () -> Unit, onResume: (Long) -> Unit, onTap: (Long) -> Unit,
    onUndo: (Long) -> Unit, onFinish: () -> Unit, onAutoMovement: (Long, Long, Float) -> Unit,
    onConfirmDetections: (List<Long>) -> Unit, onDismissDetections: (List<Long>) -> Unit
) {
    val context = LocalContext.current
    var guidanceExpanded by remember { mutableStateOf(false) }
    val elapsed = activeSession?.let { (System.currentTimeMillis() - it.startedAt).coerceAtLeast(0L) } ?: 0L
    val sessionLimit = CounterRepository.TWO_HOURS_MS
    val progress = (elapsed.toFloat() / sessionLimit).coerceIn(0f, 1f)
    val tapScale = remember { Animatable(1f) }
    val tapScope = rememberCoroutineScope()
    var autoDetect by remember(activeSession?.id) { mutableStateOf(false) }
    var autoStatus by remember(activeSession?.id) { mutableStateOf("") }
    var voiceListening by remember(activeSession?.id) { mutableStateOf(false) }
    var voicePanelVisible by remember(activeSession?.id) { mutableStateOf(false) }
    var voiceStatus by remember(activeSession?.id) { mutableStateOf("") }
    var voiceTranscript by remember(activeSession?.id) { mutableStateOf("") }
    var voiceDetectedCount by remember(activeSession?.id) { mutableStateOf(0) }
    val pending = detections.count { it.status == "PENDING" }
    val added = detections.count { it.status == "CONFIRMED" }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (activeSession != null) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                    Text(if (english) "BABY MOVEMENTS" else "बाळाची हालचाल", color = ink.copy(alpha=.55f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, maxLines = 1)
                    Text(periodLabel(activeSession.dayPeriod, english) + " · " + sessionTime(activeSession.startedAt),
                        color = ink, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, maxLines = 1)
                }
                Button(
                    onClick = { autoDetect = !autoDetect },
                    modifier = Modifier.height(40.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 5.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (autoDetect) Color(0xFF26734D) else Color(0xFFE6EEF6),
                        contentColor = if (autoDetect) Color.White else ink
                    )
                ) {
                    Icon(Icons.Default.DirectionsWalk, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(5.dp))
                    Text(if (autoDetect) (if (english) "Motion on" else "हालचाल सुरू") else (if (english) "Phone motion" else "फोन हालचाल"), style = MaterialTheme.typography.labelSmall, maxLines = 1)
                }
                BabyVoiceCountControl(
                    autoStart = autoStartVoice,
                    onAutoStartConsumed = onAutoStartVoiceConsumed,
                    activeSessionId = activeSession.id,
                    english = english,
                    onRecognitionState = { active, status, transcript, detectedCount ->
                        voiceListening = active
                        voiceStatus = status
                        // Keep the panel visible for setup/reload and actionable errors.
                        // Hide it only for an explicit user stop / inactive state.
                        voicePanelVisible = active ||
                            (status != "Voice count is off" && status != "आवाज मोजणी बंद")
                        if (transcript != null) voiceTranscript = transcript
                        if (detectedCount != null) voiceDetectedCount = detectedCount
                        if (!active) {
                            voiceTranscript = ""
                            voiceDetectedCount = 0
                        }
                    }
                )
            }
            if (voicePanelVisible) {
                BabyVoiceRecognitionPanel(voiceListening, english, voiceStatus, voiceTranscript, voiceDetectedCount)
            }
            Spacer(Modifier.height(6.dp))
            BabyMotionSensorEngine(
                activeSessionId = activeSession.id, enabled = autoDetect,
                onDetected = { timestamp, magnitude -> onAutoMovement(activeSession.id, timestamp, magnitude) },
                onStatus = { autoStatus = it }
            )
            Spacer(Modifier.height(8.dp))
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(26.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.fillMaxWidth().padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (english) "MOVEMENTS" else "हालचाली", color = ink.copy(alpha=.55f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Text(activeSession.movementCount.toString(), color = ink, style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
                            Surface(Modifier.align(Alignment.CenterEnd).padding(end = 4.dp), shape = RoundedCornerShape(9.dp), color = tapSurface.copy(alpha = .65f)) {
                                TextButton(onClick = { if (activeSession.movementCount > 0) onUndo(activeSession.id) }, enabled = activeSession.movementCount > 0,
                                    contentPadding = PaddingValues(horizontal = 9.dp, vertical = 2.dp), modifier = Modifier.height(34.dp)) {
                                    Icon(Icons.Default.Undo, null, modifier = Modifier.size(15.dp)); Spacer(Modifier.width(3.dp))
                                    Text(if (english) "Undo" else "मागे", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                    Box(Modifier.size(232.dp), contentAlignment = Alignment.Center) {
                        Canvas(Modifier.fillMaxSize()) {
                            drawCircle(tapSurface, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 14.dp.toPx()))
                            drawArc(Gold, -90f, 360f * progress, false, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 14.dp.toPx(), cap = StrokeCap.Round))
                        }
                        val tapInteraction = remember { MutableInteractionSource() }
                        Box(
                            Modifier.size(193.dp)
                                .graphicsLayer(scaleX = tapScale.value, scaleY = tapScale.value)
                                .background(Brush.linearGradient(if (activeSession.movementCount >= 10) listOf(Color(0xFFE2F5E8), Color(0xFFC6EBD2)) else listOf(Color(0xFFFFE6DD), Color(0xFFFFC9B9))), CircleShape)
                                .clickable(interactionSource = tapInteraction, indication = null) {
                                    tapScope.launch { tapScale.animateTo(.92f, tween(90)); tapScale.animateTo(1f, tween(180)) }
                                    onTap(activeSession.id)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.PregnantWoman, null, tint = Color(0xFFC65F68), modifier = Modifier.size(44.dp))
                                Text("+1", color = Color(0xFF7F3546), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.ExtraBold)
                                Text(if (english) "TAP TO COUNT" else "स्पर्श करून मोजा", color = Color(0xFF7F3546), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("%02d:%02d".format(elapsed / 60000, (elapsed / 1000) % 60), color = ink, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("%02d:%02d".format(remainingMs / 3600000, (remainingMs / 60000) % 60), color = Gold, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().padding(top = 7.dp).height(6.dp).clip(CircleShape), color = Gold, trackColor = tapSurface)
                    if (activeSession.timeToTenMs != null) {
                        Surface(Modifier.fillMaxWidth().padding(top = 12.dp), shape = RoundedCornerShape(14.dp), color = Color(0xFFE8F4EC)) {
                            Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(if (english) "10 MOVEMENTS REACHED" else "१० हालचाली पूर्ण", color = Color(0xFF246B48), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                Text(if (english) "Time: " + "%.1f min".format(activeSession.timeToTenMs / 60000.0) else "वेळ: " + "%.1f मिनिटे".format(activeSession.timeToTenMs / 60000.0), color = Color(0xFF246B48), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                            }
                        }
                    }
                }
            }
        } else {
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(26.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.fillMaxWidth().padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.PregnantWoman, null, tint = Color(0xFFC65F68), modifier = Modifier.size(42.dp))
                    Text(if (english) "Baby movement" else "बाळाची हालचाल", color = ink, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    if (latestSession != null && latestSession.timeToTenMs != null) {
                        Surface(Modifier.fillMaxWidth().padding(top = 14.dp), shape = RoundedCornerShape(16.dp), color = Color(0xFFE8F4EC)) {
                            Column(Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(if (english) "10 MOVEMENTS COMPLETED" else "१० हालचाली पूर्ण", color = Color(0xFF246B48), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                Text("%.1f".format((latestSession.timeToTenMs ?: 0L) / 60000.0) + (if (english) " min" else " मिनिटे"), color = Color(0xFF246B48), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.ExtraBold)
                                Text(if (english) "Time taken to feel 10 movements" else "१० हालचाली जाणवण्यासाठी लागलेला वेळ", color = Color(0xFF246B48), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    if (resumableSession != null) {
                        Spacer(Modifier.height(28.dp))
                        Button(onClick = { onResume(resumableSession.id) }, modifier = Modifier.height(46.dp)) {
                            Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(7.dp)); Text(if (english) "Resume session" else "सत्र पुन्हा सुरू करा")
                        }
                    } else if (lockedSession == null) {
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = onStart, modifier = Modifier.height(46.dp)) {
                            Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(7.dp)); Text(if (english) "Start session" else "सत्र सुरू करा")
                        }
                    }
                }
            }
        }
        if (detections.isNotEmpty() || autoDetect) {
            Card(Modifier.fillMaxWidth().padding(top = 10.dp), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.fillMaxWidth().padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Navy).padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(if (english) "PHONE MOTION" else "फोनची हालचाल", color = Color.White, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        if (autoDetect) {
                            val ready = autoStatus.contains("Ready", true) || autoStatus.contains("तयार", true)
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (ready) {
                                    Box(Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF45D483)))
                                    Text(if (english) "READY" else "तयार", color = Color(0xFF72E3A0), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                } else {
                                    CircularProgressIndicator(Modifier.size(13.dp), color = Color(0xFFFF7777), strokeWidth = 2.dp)
                                    Text(if (english) "CALIBRATING…" else "कॅलिब्रेट होत आहे…", color = Color(0xFFFF7777), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, maxLines = 1)
                                }
                            }
                        } else Text(if (english) "OFF" else "बंद", color = Color.White.copy(alpha = .8f), style = MaterialTheme.typography.labelSmall)
                    }
                    Text(if (english) "This detects movement of your phone—not your baby. Phone movement can create false detections. Review and add only movements you actually felt." else "हा फोनची हालचाल ओळखतो, बाळाची नाही. फोन हलल्यामुळे चुकीच्या नोंदी होऊ शकतात. प्रत्यक्ष जाणवलेल्या हालचालीच जोडा.", modifier = Modifier.fillMaxWidth().padding(top = 8.dp), color = ink.copy(alpha = .75f), style = MaterialTheme.typography.bodySmall)
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(if (english) "Detected" else "ओळखले", color = ink.copy(alpha = .65f), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
                            Text(pending.toString(), color = ink, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
                        }
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(if (english) "Added" else "जोडले", color = ink.copy(alpha = .65f), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
                            Text(added.toString(), color = ink, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
                        }
                    }
                    if (pending > 0) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            modifier = Modifier.height(36.dp).widthIn(min = 92.dp).clickable { onConfirmDetections(detections.filter { it.status == "PENDING" }.map { it.id }) },
                            shape = CircleShape, color = Color(0xFFE4EFFB)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(if (english) "Add all" else "सर्व जोडा", color = Color(0xFF174A7E), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                            }
                        }
                        Surface(
                            modifier = Modifier.height(36.dp).widthIn(min = 92.dp).clickable { onDismissDetections(detections.filter { it.status == "PENDING" }.map { it.id }) },
                            shape = CircleShape, color = Color(0xFFFCE8E7)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(if (english) "Dismiss" else "वगळा", color = Color(0xFFB3261E), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(if (english) "HISTORY" else "इतिहास", color = ink, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onPreviousDay, modifier = Modifier.size(32.dp)) { Text("‹", color = ink, style = MaterialTheme.typography.titleLarge) }
                Text(historyDate.format(dateFormat), color = ink, style = MaterialTheme.typography.labelMedium)
                IconButton(onClick = onNextDay, enabled = historyDate.isBefore(LocalDate.now()), modifier = Modifier.size(32.dp)) { Text("›", color = ink, style = MaterialTheme.typography.titleLarge) }
            }
        }
        if (sessions.isEmpty()) {
            Text(if (english) "No sessions" else "नोंद उपलब्ध नाही", modifier = Modifier.fillMaxWidth().padding(14.dp), color = ink.copy(alpha=.55f))
        } else {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                sessions.forEach { session ->
                    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(15.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(periodLabel(session.dayPeriod, english) + " · " + sessionTime(session.startedAt), color = ink, fontWeight = FontWeight.Bold)
                                val duration = ((session.endedAt ?: minOf(session.startedAt + CounterRepository.TWO_HOURS_MS, System.currentTimeMillis())) - session.startedAt).coerceAtLeast(0) / 60000
                                if (session.timeToTenMs != null) Text((if (english) "10 in " else "१० साठी ") + "%.1f min".format(session.timeToTenMs / 60000.0), color = Color(0xFF238451), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            }
                            Text(session.movementCount.toString(), color = Gold, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                        }
                    }
                }
            }
        }
        if (recentLogs.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(if (english) "RECENT" else "अलीकडील", color = ink.copy(alpha = .55f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                recentLogs.take(2).forEach { item ->
                    Text(sessionTime(item.timestamp) + " · +" + item.count, color = ink.copy(alpha = .75f), style = MaterialTheme.typography.labelSmall, maxLines = 1)
                }
            }
        }
        TextButton(onClick = onViewHistory, modifier = Modifier.align(Alignment.End)) {
            Icon(Icons.Default.History, null); Spacer(Modifier.width(5.dp))
            Text(if (english) "View history & analytics" else "संपूर्ण इतिहास व विश्लेषण")
        }
        Card(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .65f))) {
            Column(Modifier.padding(13.dp)) {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Navy).clickable { guidanceExpanded = !guidanceExpanded }.padding(horizontal = 12.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(if (english) "MOVEMENT GUIDANCE" else "हालचालींबाबत सूचना", color = Color.White, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Text(if (guidanceExpanded) (if (english) "Less −" else "कमी −") else (if (english) "More +" else "अधिक +"), color = Color.White, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                }
                Text(
                    if (english) "If movements become less frequent or feel different from your baby's usual pattern, contact your maternity-care professional promptly. Do not wait for the timer."
                    else "हालचाली नेहमीपेक्षा कमी किंवा वेगळ्या वाटल्यास वेळ संपण्याची वाट न पाहता त्वरित डॉक्टरांशी संपर्क साधा.",
                    color = Color(0xFF9A5B36), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp)
                )
                if (guidanceExpanded) {
                    Text(
                        if (english) "Choose a quiet time when you usually notice movement. Sit comfortably and count kicks, flutters, rolls or swishes. Guidance differs; follow your maternity-care professional's recommended method."
                        else "बाळाची हालचाल सहसा जाणवते अशी शांत वेळ निवडा. आरामात बसा आणि लाथा, फडफड, वळणे किंवा सरकणे मोजा. डॉक्टरांनी सांगितलेली मोजणी पद्धत पाळा.",
                        color = ink.copy(alpha=.75f), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp)
                    )
                    TextButton(onClick = { val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://my.clevelandclinic.org/health/articles/23497-kick-counts")); runCatching { context.startActivity(intent) } }) {
                        Text(if (english) "Know how · Cleveland Clinic" else "अधिक जाणून घ्या · Cleveland Clinic")
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun StatTile(label: String, value: String, ink: Color, modifier: Modifier = Modifier) {
    Card(modifier, shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 13.dp)) {
            Text(label, color = ink.copy(alpha = .55f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            Text(value, color = ink, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
        }
    }
}

@Composable
fun WeeklyChart(days: List<DayTotal>, modifier: Modifier = Modifier) {
    val maxValue=(days.maxOfOrNull{it.total} ?: 0).coerceAtLeast(1)
    Row(modifier, horizontalArrangement=Arrangement.spacedBy(6.dp), verticalAlignment=Alignment.Bottom) {
        days.forEach { day ->
            Column(Modifier.weight(1f),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Bottom) {
                Text(day.total.toString(),color=Navy,style=MaterialTheme.typography.labelSmall,fontWeight=FontWeight.Bold)
                Spacer(Modifier.height(5.dp))
                Box(Modifier.fillMaxWidth(.72f).weight(1f,false).height((145f*day.total/maxValue).coerceAtLeast(if(day.total>0) 8f else 3f).dp).clip(RoundedCornerShape(topStart=8.dp,topEnd=8.dp)).background(if(day.total==maxValue) Gold else Color(0xFF9BB6D3)))
                Spacer(Modifier.height(7.dp))
                Text(day.date.takeLast(2),color=Navy.copy(alpha=.65f),style=MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun MonthlyHistoryScreen(
    english: Boolean,
    ink: Color,
    month: LocalDate,
    sessions: List<BabySession>,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onExportPdf: () -> Unit,
    onEditSession: (BabySession) -> Unit
) {
    val context = LocalContext.current
    val totals = sessions.groupBy { it.date }.mapValues { entry -> entry.value.sumOf { it.movementCount } }
    val daysInMonth = month.lengthOfMonth()
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = onPrevious) { Text("‹") }
            Text(month.format(DateTimeFormatter.ofPattern("MMMM yyyy")), color = ink, style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = onNext, enabled = month.isBefore(LocalDate.now().withDayOfMonth(1))) { Text("›") }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            HistoryMetric(if (english) "Sessions" else "सत्रे", sessions.size.toString(), ink)
            HistoryMetric(if (english) "Movements" else "हालचाली", sessions.sumOf { it.movementCount }.toString(), ink)
            HistoryMetric(if (english) "Avg/session" else "प्रति सत्र सरासरी", if (sessions.isEmpty()) "—" else "%.1f".format(sessions.map { it.movementCount }.average()), ink)
            val milestoneTimes = sessions.mapNotNull { it.timeToTenMs }
            HistoryMetric(if (english) "Avg time to 10" else "१० साठी सरासरी वेळ", if (milestoneTimes.isEmpty()) "—" else "%.1f m".format(milestoneTimes.average() / 60000.0), ink)
        }
        Spacer(Modifier.height(8.dp))
        Text(if (english) "Daily movement pattern" else "दैनिक हालचालींचा आलेख", color = ink, style = MaterialTheme.typography.titleSmall)
        MonthlyChart((1..daysInMonth).map { day -> totals[month.withDayOfMonth(day).toString()] ?: 0 }, Modifier.fillMaxWidth().height(190.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Button(onClick = onExportPdf, enabled = sessions.isNotEmpty()) { Text(if (english) "Share PDF" else "PDF शेअर करा") }
        }
        Text(if (english) "Individual sessions" else "स्वतंत्र सत्रांची नोंद", color = ink, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(vertical = 6.dp))
        if (sessions.isEmpty()) {
            Text(if (english) "No sessions recorded this month" else "या महिन्यात सत्रे नोंदलेली नाहीत", color = ink.copy(alpha = .65f))
        } else {
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(sessions, key = { it.id }) { session ->
                    var editSession by remember(session.id) { mutableStateOf(false) }
                    var editCount by remember(session.id) { mutableStateOf(session.movementCount.toString()) }
                    val endValue = session.endedAt ?: (session.startedAt + CounterRepository.TWO_HOURS_MS)
                    var editDate by remember(session.id) { mutableStateOf(session.date) }
                    var editTime by remember(session.id) { mutableStateOf(Instant.ofEpochMilli(session.startedAt).atZone(zone).toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm"))) }
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("${session.date}  •  ${periodLabel(session.dayPeriod, english)}  •  ${sessionTime(session.startedAt)}", color = ink, fontWeight = FontWeight.Medium)
                                session.strength?.let { Text(strengthLabel(it, english), color = ink.copy(alpha = .65f), style = MaterialTheme.typography.bodySmall) }
                                session.timeToTenMs?.let { Text((if (english) "Time to 10: " else "१० हालचालींसाठी वेळ: ") + "%.1f".format(it / 60000.0) + (if (english) " min" else " मिनिटे"), color = ink.copy(alpha = .65f), style = MaterialTheme.typography.bodySmall) }
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(session.movementCount.toString(), color = Gold, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                IconButton(onClick = { editCount = session.movementCount.toString(); editDate = session.date; editTime = Instant.ofEpochMilli(session.startedAt).atZone(zone).toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm")); editSession = true }) { Icon(Icons.Default.Edit, contentDescription = if (english) "Edit session" else "सत्र संपादित करा", tint = ink) }
                            }
                        }
                    }
                    if (editSession) {
                        AlertDialog(
                            onDismissRequest = { editSession = false },
                            title = { Text(if (english) "Edit session" else "सत्र संपादित करा") },
                            text = {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(editCount, { editCount = it.filter(Char::isDigit).take(4) }, label = { Text(if (english) "Movement count" else "हालचालींची संख्या") }, singleLine = true)
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedButton(onClick = {
                                            val parts = editDate.split("-")
                                            if (parts.size == 3) android.app.DatePickerDialog(context, { _, year, month, day ->
                                                editDate = "%04d-%02d-%02d".format(year, month+1, day)
                                            }, parts[0].toInt(), parts[1].toInt()-1, parts[2].toInt()).show()
                                        }) { Text(editDate) }
                                        OutlinedButton(onClick = {
                                            val parts = editTime.split(":")
                                            android.app.TimePickerDialog(context, { _, hour, minute -> editTime = "%02d:%02d".format(hour, minute) },
                                                parts.getOrNull(0)?.toIntOrNull() ?: 16, parts.getOrNull(1)?.toIntOrNull() ?: 12, true).show()
                                        }) { Text(editTime) }
                                    }
                                }
                            },
                            confirmButton = {
                                TextButton(onClick = {
                                    val count = editCount.toIntOrNull()?.coerceIn(0, 9999)
                                    runCatching {
                                        val date = LocalDate.parse(editDate)
                                        val time = java.time.LocalTime.parse(editTime)
                                        if (count != null) {
                                            val start = date.atTime(time).atZone(zone).toInstant().toEpochMilli()
                                            val end = start + CounterRepository.TWO_HOURS_MS
                                            val period = when (time.hour) { in 5..11 -> "MORNING"; in 12..16 -> "AFTERNOON"; in 17..20 -> "EVENING"; else -> "NIGHT" }
                                            onEditSession(session.copy(date=editDate, startedAt=start, endedAt=end, movementCount=count, dayPeriod=period, timeToTenMs=if(count>=10) session.timeToTenMs else null))
                                            editSession = false
                                        }
                                    }
                                }) { Text(if (english) "Save" else "जतन करा") }
                            },
                            dismissButton = { TextButton(onClick = { editSession = false }) { Text(if (english) "Cancel" else "रद्द") } }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryMetric(label: String, value: String, ink: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = ink, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(label, color = ink.copy(alpha = .65f), style = MaterialTheme.typography.labelSmall)
    }
}

private fun strengthLabel(value: String, english: Boolean) = when (value) {
    "LIGHT" -> if (english) "Light" else "हलकी"
    "MEDIUM" -> if (english) "Medium" else "मध्यम"
    "STRONG" -> if (english) "Strong" else "जोरात"
    else -> value
}

@Composable
private fun MonthlyChart(values: List<Int>, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val max = (values.maxOrNull() ?: 0).coerceAtLeast(1).toFloat()
        val gap = size.width / (values.size * 1.45f)
        val width = gap * .62f
        values.forEachIndexed { index, value ->
            val height = (size.height * .88f * value / max).coerceAtLeast(if (value > 0) 2f else 0f)
            drawRoundRect(Gold, androidx.compose.ui.geometry.Offset(index * gap + gap * .18f, size.height - height), androidx.compose.ui.geometry.Size(width, height), androidx.compose.ui.geometry.CornerRadius(4f))
        }
        drawLine(Navy.copy(alpha = .25f), androidx.compose.ui.geometry.Offset(0f, size.height), androidx.compose.ui.geometry.Offset(size.width, size.height), strokeWidth = 1.5f)
    }
}

private fun exportMonthlyPdf(context: android.content.Context, titleMr: String, titleEn: String, month: LocalDate, sessions: List<BabySession>, english: Boolean) {
    val pdf = PdfDocument()
    val width = 595
    val height = 842
    val navy = android.graphics.Color.rgb(16, 46, 85)
    val blue = android.graphics.Color.rgb(58, 112, 163)
    val gold = android.graphics.Color.rgb(231, 163, 60)
    val pale = android.graphics.Color.rgb(239, 245, 251)
    val ink = android.graphics.Color.rgb(45, 57, 72)
    val muted = android.graphics.Color.rgb(105, 119, 136)
    val monthLabel = month.format(DateTimeFormatter.ofPattern("MMMM yyyy"))
    val sorted = sessions.sortedBy { it.startedAt }
    val total = sessions.sumOf { it.movementCount }
    val average = if (sessions.isEmpty()) 0.0 else sessions.map { it.movementCount }.average()
    val daily = (1..month.lengthOfMonth()).map { day ->
        sessions.filter { it.date == month.withDayOfMonth(day).toString() }.sumOf { it.movementCount }
    }
    val maxDaily = (daily.maxOrNull() ?: 0).coerceAtLeast(1)
    fun paint(color: Int, size: Float, bold: Boolean = false) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        textSize = size
        typeface = Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
    }
    val titlePaint = paint(android.graphics.Color.WHITE, 21f, true)
    val subPaint = paint(android.graphics.Color.rgb(224, 234, 245), 10f)
    val bodyPaint = paint(ink, 9.5f)
    val smallPaint = paint(muted, 8.5f)
    val headPaint = paint(android.graphics.Color.WHITE, 9f, true)
    val numberPaint = paint(navy, 17f, true)
    val labelPaint = paint(muted, 8.5f, true)
    val footerPaint = paint(muted, 8f)
    val xs = floatArrayOf(36f, 160f, 250f, 355f, 465f)
    var pageNumber = 0
    var currentPage: PdfDocument.Page? = null
    var cv: android.graphics.Canvas? = null
    var y = 0f

    fun tableHeader(canvas: android.graphics.Canvas, top: Float): Float {
        canvas.drawRoundRect(android.graphics.RectF(36f, top, 559f, top + 28f), 7f, 7f, paint(navy, 9f, true))
        val labels = if (english) listOf("DATE & TIME", "PERIOD", "10 MOVES IN", "FIRST HOUR", "TOTAL MOVES")
                     else listOf("दिनांक व वेळ", "कालावधी", "१० हालचाली", "पहिला तास", "एकूण")
        labels.forEachIndexed { i, label -> canvas.drawText(label, xs[i] + 7f, top + 18f, headPaint) }
        return top + 28f
    }
    fun startPage(first: Boolean) {
        pageNumber++
        currentPage = pdf.startPage(PdfDocument.PageInfo.Builder(width, height, pageNumber).create())
        cv = currentPage!!.canvas
        val canvas = cv!!
        canvas.drawColor(android.graphics.Color.WHITE)
        canvas.drawRoundRect(android.graphics.RectF(24f, 22f, 571f, 88f), 14f, 14f, paint(navy, 12f, true))
        canvas.drawRoundRect(android.graphics.RectF(24f, 22f, 31f, 88f), 4f, 4f, paint(gold, 10f))
        canvas.drawText(if (english) "BABY MOVEMENT REPORT" else "बाळाच्या हालचालींचा अहवाल", 45f, 52f, titlePaint)
        val headerSub = if (first) (if (english) "Monthly summary" else "मासिक सारांश") else (if (english) "Session details · continued" else "सत्र तपशील · पुढे")
        val rightSubPaint = paint(android.graphics.Color.rgb(224, 234, 245), 9f).apply { textAlign = Paint.Align.RIGHT }
        canvas.drawText(if (english) titleEn + " · " + monthLabel else titleMr + " · " + monthLabel, 550f, 52f, rightSubPaint)
        canvas.drawText(headerSub, 550f, 72f, rightSubPaint)
        var tableTop = 120f
        if (first) {
            val metrics = listOf(
                Triple(if (english) "SESSIONS" else "सत्रे", sessions.size.toString(), blue),
                Triple(if (english) "MOVEMENTS" else "हालचाली", total.toString(), navy),
                Triple(if (english) "AVG / SESSION" else "सरासरी", "%.1f".format(average), gold)
            )
            metrics.forEachIndexed { i, metric ->
                val left = 36f + i * 177f
                canvas.drawRoundRect(android.graphics.RectF(left, 100f, left + 164f, 165f), 11f, 11f, paint(pale, 10f))
                canvas.drawRoundRect(android.graphics.RectF(left, 100f, left + 5f, 165f), 3f, 3f, paint(metric.third, 10f))
                canvas.drawText(metric.first, left + 15f, 121f, labelPaint)
                canvas.drawText(metric.second, left + 15f, 150f, numberPaint)
            }
            canvas.drawText(if (english) "DAILY MOVEMENT PATTERN" else "दैनिक हालचालींचा आलेख", 36f, 190f, paint(navy, 10f, true))
            canvas.drawRoundRect(android.graphics.RectF(36f, 202f, 559f, 356f), 8f, 8f, paint(pale, 9f))
            val slot = 505f / daily.size.coerceAtLeast(1)
            daily.forEachIndexed { i, value ->
                val barHeight = if (value == 0) 1.5f else (value.toFloat() / maxDaily * 118f).coerceAtLeast(3f)
                val left = 45f + i * slot + slot * .2f
                canvas.drawRoundRect(android.graphics.RectF(left, 338f - barHeight, left + slot * .58f, 338f), 2f, 2f,
                    paint(if (value > 0) blue else android.graphics.Color.rgb(205, 216, 228), 8f))
            }
            canvas.drawText("1", 45f, 348f, smallPaint)
            canvas.drawText(month.lengthOfMonth().toString(), 538f, 348f, smallPaint)
            tableTop = 390f
        } else {
            canvas.drawText(if (english) "Session records" else "सत्र नोंदी", 36f, 115f, paint(navy, 11f, true))
        }
        canvas.drawText(if (english) "SESSION DETAILS" else "सत्रांचा तपशील", 36f, tableTop - 12f, paint(navy, 10f, true))
        y = tableHeader(canvas, tableTop)
    }
    fun finishPage() {
        val canvas = cv ?: return
        canvas.drawLine(36f, 805f, 559f, 805f, paint(android.graphics.Color.rgb(220, 228, 237), 1f))
        canvas.drawText(if (english) "Daily Counter · Personal movement log · Not a medical assessment" else "दैनिक मोजणी · वैयक्तिक नोंद · वैद्यकीय मूल्यांकन नाही", 36f, 823f, footerPaint)
        canvas.drawText(pageNumber.toString(), 544f, 823f, paint(navy, 9f, true))
        pdf.finishPage(currentPage!!)
        currentPage = null
        cv = null
    }
    startPage(true)
    sorted.forEachIndexed { index, session ->
        if (y > 775f) {
            finishPage()
            startPage(false)
        }
        val canvas = cv!!
        if (index % 2 == 0) canvas.drawRoundRect(android.graphics.RectF(36f, y, 559f, y + 25f), 4f, 4f, paint(pale, 8f))
        val dateLabel = runCatching { LocalDate.parse(session.date).format(DateTimeFormatter.ofPattern("dd MMM")) }.getOrDefault(session.date)
        val end = session.endedAt ?: minOf(session.startedAt + CounterRepository.TWO_HOURS_MS, System.currentTimeMillis())
        val duration = (end - session.startedAt).coerceAtLeast(0L) / 60000L
        val dateTimeLabel = dateLabel + " " + sessionTime(session.startedAt)
        val tenLabel = session.timeToTenMs?.let { "%.1f min".format(it / 60000.0) } ?: "—"
        val firstHourLabel = session.firstHourCount.toString()
        val values = listOf(dateTimeLabel, periodLabel(session.dayPeriod, english), tenLabel, firstHourLabel, session.movementCount.toString())
        values.forEachIndexed { col, value ->
            if (col == 4) {
                canvas.drawRoundRect(android.graphics.RectF(xs[col] + 5f, y + 4f, xs[col] + 57f, y + 21f), 6f, 6f, paint(android.graphics.Color.rgb(222, 237, 249), 8f))
                canvas.drawText(value, xs[col] + 22f, y + 17f, paint(navy, 10f, true))
            } else {
                canvas.drawText(value.take(if (col == 0) 18 else 12), xs[col] + 7f, y + 16f, bodyPaint)
            }
        }
        y += 27f
    }
    finishPage()
    val file = File(context.cacheDir, "baby-movement-" + month.year + "-" + month.monthValue + ".pdf")
    file.outputStream().use { pdf.writeTo(it) }
    pdf.close()
    val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
    val share = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(share, if (english) "Share PDF" else "PDF शेअर करा"))
}
