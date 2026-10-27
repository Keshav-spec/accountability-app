package com.steady.accountability

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.steady.accountability.data.*
import com.steady.accountability.blocking.*
import com.steady.accountability.blocking.ui.AppBlockingScreen
import com.steady.accountability.reminders.ReminderScheduler
import com.steady.accountability.usage.UsageAppRow
import com.steady.accountability.usage.UsagePermissionState
import com.steady.accountability.usage.UsageRules
import com.steady.accountability.usage.UsageStatsRepository
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.handleDeeplinks
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val Sage = Color(0xFF244237)
private val Eucalyptus = Color(0xFF43664D)
private val Mint = Color(0xFFC2E9C9)
private val Cream = Color(0xFFFAF9F5)
private val SoftStone = Color(0xFFF4F4F0)
private val Stone = Color(0xFFEFEEEA)
private val Ink = Color(0xFF1B1C1A)
private val MutedInk = Color(0xFF414845)
private val Tabs = listOf("Today", "Tasks", "Check-in", "Reminders", "Usage", "Limits")
private val TabSymbols = mapOf("Today" to "☼", "Tasks" to "☷", "Check-in" to "◉", "Reminders" to "♧", "Usage" to "◌", "Limits" to "⏸")

private fun formatStorageBytes(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> "${"%.1f".format(bytes / (1024f * 1024f))} MB"
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (SupabaseSync.configured) runCatching { SupabaseSync.client.handleDeeplinks(intent) }
        val db = DatabaseProvider.get(applicationContext)
        lifecycleScope.launch { ReminderScheduler.refresh(applicationContext) }
        setContent { SteadyApp(TaskRepository(db.taskDao()), CheckInRepository(db.checkInDao()), ReminderSettingsRepository(db.reminderSettingsDao()), db.taskDao(), db.checkInDao(), db.usageDao(), db.reminderHistoryDao()) }
    }
}

@Composable
private fun SteadyApp(repository: TaskRepository, checkIns: CheckInRepository, settingsRepository: ReminderSettingsRepository, taskDao: TaskDao, checkInDao: CheckInDao, usageDao: UsageDao, reminderHistoryDao: ReminderHistoryDao) {
    var selected by remember { mutableStateOf("Today") }
    var editing by remember { mutableStateOf<TaskEntity?>(null) }
    var showNew by remember { mutableStateOf(false) }
    var permissionExplanation by remember { mutableStateOf(false) }
    val tasks by repository.allTasks().collectAsState(initial = emptyList())
    val visibleTasks = tasks.filter { it.deletedAt == null }
    val checkIn by checkIns.forDate().collectAsState(initial = null)
    val settings by settingsRepository.observe().collectAsState(initial = null)
    val reminderHistory by reminderHistoryDao.observeRecent().collectAsState(initial = emptyList())
    val usageTracking by usageDao.observeTracking().collectAsState(initial = null)
    val usageApps by usageDao.observeApps().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("steady-sync", android.content.Context.MODE_PRIVATE) }
    var syncState by remember { mutableStateOf(if (SupabaseSync.configured) "Offline" else "Local only") }
    var accountMessage by remember { mutableStateOf("") }
    var accountEmail by remember { mutableStateOf("") }
    var emailInput by remember { mutableStateOf("") }
    var passwordInput by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf(prefs.getString("display_name", "").orEmpty()) }
    var newAccountName by remember { mutableStateOf("") }
    var wifiOnly by remember { mutableStateOf(prefs.getBoolean("wifi_only", false)) }
    var syncOnOpen by remember { mutableStateOf(prefs.getBoolean("sync_on_open", true)) }
    var usagePermission by remember { mutableStateOf(UsagePermissionState.DENIED) }
    var usageRows by remember { mutableStateOf(emptyList<UsageAppRow>()) }
    var usageRefresh by remember { mutableIntStateOf(0) }
    var blockingConfig by remember { mutableStateOf(BlockingPreferences.read(context)) }
    var accessibilityGranted by remember { mutableStateOf(BlockingAccessibilityService.accessibilityEnabled(context)) }
    var blockingServiceConnected by remember { mutableStateOf(BlockingAccessibilityService.connected) }
    val usageRepository = remember(context, usageDao) { UsageStatsRepository(context.applicationContext, usageDao) }
    val lifecycleOwner = LocalLifecycleOwner.current
    val syncNow: suspend () -> Unit = {
        if (SupabaseSync.configured && SupabaseSync.userId() != null) {
            val connectivity = context.getSystemService(ConnectivityManager::class.java)
            val network = connectivity.activeNetwork
            if (network == null) syncState = "Offline"
            else if (prefs.getBoolean("wifi_only", false) && connectivity.getNetworkCapabilities(network)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) != true) syncState = "Waiting for Wi-Fi"
            else {
                syncState = "Syncing"
                try {
                    if (prefs.getBoolean("profile_pending", false) && prefs.getString("profile_user_id", SupabaseSync.userId()) == SupabaseSync.userId()) {
                        prefs.getString("display_name", null)?.takeIf(String::isNotBlank)?.let { SupabaseSync.saveProfileName(it) }
                        prefs.edit().putBoolean("profile_pending", false).apply()
                    }
                    syncState = SupabaseSync.sync(taskDao, checkInDao, { prefs.edit().putString("owner", it).apply() }, prefs.getString("owner", null))
                    if (syncState == "Synced") prefs.edit().putString("last_sync", java.time.Instant.now().toString()).apply()
                } catch (_: Exception) { syncState = if (connectivity.activeNetwork == null) "Offline" else "Needs attention" }
            }
        }
    }
    LaunchedEffect(Unit) {
        if (SupabaseSync.configured) runCatching {
            SupabaseSync.client.auth.awaitInitialization()
            val currentUserId = SupabaseSync.userId()
            if (currentUserId != null) {
                accountEmail = SupabaseSync.currentEmail().orEmpty()
                val savedName = runCatching { SupabaseSync.profileName() }.getOrNull()
                    ?: prefs.getString("display_name", null)?.takeIf { prefs.getString("profile_user_id", null) == currentUserId }
                    ?: accountEmail.substringBefore('@').replace('.', ' ').replaceFirstChar(Char::uppercase)
                displayName = savedName
                prefs.edit().putString("display_name", savedName).putString("profile_user_id", currentUserId).apply()
                if (prefs.getBoolean("sync_on_open", true)) syncNow()
            }
        }
    }
    DisposableEffect(Unit) {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { if (prefs.getBoolean("sync_on_open", true)) scope.launch { syncNow() } }
            override fun onLost(network: Network) { syncState = "Offline" }
        }
        manager.registerDefaultNetworkCallback(callback)
        onDispose { runCatching { manager.unregisterNetworkCallback(callback) } }
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) {
            usageRefresh++
            accessibilityGranted = BlockingAccessibilityService.accessibilityEnabled(context)
            blockingServiceConnected = BlockingAccessibilityService.connected
            blockingConfig = BlockingPreferences.read(context)
            BlockingAccessibilityService.dismissOverlay(context)
        } }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(selected, usageTracking?.enabled, usageRefresh) {
        if (selected == "Usage" && usageTracking?.enabled == true) {
            val access = withContext(Dispatchers.IO) { usageRepository.permissionState() }
            usagePermission = access
            if (access == UsagePermissionState.GRANTED) {
                val result = withContext(Dispatchers.IO) { usageRepository.refreshToday() }
                usagePermission = result.permission
                usageRows = result.rows
            } else usageRows = emptyList()
        } else if (selected == "Usage") {
            usagePermission = UsagePermissionState.DENIED
            usageRows = emptyList()
        }
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) scope.launch { settingsRepository.save((settings ?: ReminderSettingsEntity()).copy(enabled = true)); ReminderScheduler.refresh(context) }
    }
    val csvExportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val bytes = AccountArchive.exportCsv(taskDao.allForSync(), checkInDao.all())
                    context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) } ?: error("The selected file could not be opened.")
                }
            }.onSuccess { accountMessage = "Your CSV export was saved." }
                .onFailure { accountMessage = "Export could not be saved. Please try another location." }
        }
    }
    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val bytes = AccountArchive.encryptedSnapshot(taskDao.allForSync(), checkInDao.all())
                    context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) } ?: error("The selected file could not be opened.")
                }
            }.onSuccess { accountMessage = "Your encrypted backup snapshot was saved." }
                .onFailure { accountMessage = "Backup could not be saved. Please try another location." }
        }
    }
    MaterialTheme(colorScheme = lightColorScheme(
        primary = Sage, onPrimary = Cream, primaryContainer = Color(0xFFE4ECE7), onPrimaryContainer = Sage,
        secondary = Eucalyptus, onSecondary = Color.White, secondaryContainer = Mint, onSecondaryContainer = Color(0xFF183A27),
        background = Cream, surface = Color.White, surfaceVariant = Color(0xFFE3E2DF), onSurface = Ink,
        onSurfaceVariant = MutedInk, outline = Color(0xFF727974), outlineVariant = Color(0xFFC1C8C3),
    )) {
        Scaffold(containerColor = Cream, bottomBar = {
            NavigationBar(containerColor = Cream, tonalElevation = 0.dp) {
                Tabs.forEach { tab -> NavigationBarItem(
                    selected = selected == tab,
                    onClick = { selected = tab },
                    icon = { Text(TabSymbols.getValue(tab), style = MaterialTheme.typography.titleLarge) },
                    label = { Text(tab, style = MaterialTheme.typography.labelSmall) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Sage, selectedTextColor = Sage, indicatorColor = Mint,
                        unselectedIconColor = MutedInk, unselectedTextColor = MutedInk,
                    ),
                ) }
            }
        }, floatingActionButton = {
            if (selected == "Today" || selected == "Tasks") FloatingActionButton(onClick = { editing = null; showNew = true }, containerColor = Sage, contentColor = Color.White) { Text("+") }
        }) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp).heightIn(min = 52.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(shape = RoundedCornerShape(50), color = Stone, modifier = Modifier.size(36.dp)) {
                        Box(contentAlignment = Alignment.Center) { Text("◒", color = Eucalyptus, style = MaterialTheme.typography.titleMedium) }
                    }
                    Column(Modifier.weight(1f).padding(start = 10.dp)) {
                        Text("STEADY", style = MaterialTheme.typography.titleMedium, color = Ink, fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp)
                        Text(if (selected == "Account") "Your account" else selected, style = MaterialTheme.typography.bodySmall, color = MutedInk)
                    }
                    TextButton(onClick = { selected = "Check-in" }) { Text("♧", style = MaterialTheme.typography.titleLarge, color = Sage) }
                    Surface(shape = RoundedCornerShape(50), color = Sage, modifier = Modifier.size(38.dp).clickable { selected = "Account" }) {
                        Box(contentAlignment = Alignment.Center) { Text("♙", color = Cream, style = MaterialTheme.typography.titleMedium) }
                    }
                }
                Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
                when (selected) {
                    "Account" -> {
                        val dbFile = remember { context.getDatabasePath("steady.db") }
                        val storageBytes = dbFile.length() + java.io.File(dbFile.path + "-wal").length()
                        AccountScreen(
                            configured = SupabaseSync.configured,
                            syncState = syncState,
                            account = accountEmail,
                            displayName = displayName,
                            message = accountMessage,
                            email = emailInput,
                            onEmail = { emailInput = it },
                            password = passwordInput,
                            onPassword = { passwordInput = it },
                            newName = newAccountName,
                            onNewName = { newAccountName = it },
                            onSignIn = { scope.launch {
                                try {
                                    SupabaseSync.signIn(emailInput.trim(), passwordInput)
                                    accountEmail = SupabaseSync.currentEmail().orEmpty().ifBlank { emailInput.trim() }
                                    passwordInput = ""
                                    val remoteName = runCatching { SupabaseSync.profileName() }.getOrNull()
                                    val userId = SupabaseSync.userId()
                                    val resolvedName = remoteName ?: accountEmail.substringBefore('@').replace('.', ' ').replaceFirstChar(Char::uppercase)
                                    displayName = resolvedName
                                    prefs.edit().putString("display_name", resolvedName).putString("profile_user_id", userId).putBoolean("profile_pending", remoteName == null && prefs.getBoolean("profile_pending", false)).apply()
                                    accountMessage = "Signed in successfully."
                                    syncNow()
                                } catch (error: Exception) {
                                    accountMessage = error.message?.takeIf(String::isNotBlank)?.let { "Sign in failed: ${it.take(180)}" } ?: "Sign in could not be completed. Check your details and connection."
                                }
                            } },
                            onSignUp = { name -> scope.launch {
                                try {
                                    displayName = name
                                    prefs.edit().putString("display_name", name).putBoolean("profile_pending", true).apply()
                                    SupabaseSync.signUp(emailInput.trim(), passwordInput, name)
                                    if (SupabaseSync.userId() != null) {
                                        accountEmail = SupabaseSync.currentEmail().orEmpty().ifBlank { emailInput.trim() }
                                        passwordInput = ""
                                        prefs.edit().putString("profile_user_id", SupabaseSync.userId()).apply()
                                        SupabaseSync.saveProfileName(name)
                                        prefs.edit().putBoolean("profile_pending", false).apply()
                                        accountMessage = "Account created and your name is saved."
                                        syncNow()
                                    } else accountMessage = "Account created. Check your email to confirm it, then sign in. Your name is saved with the account."
                                } catch (error: Exception) {
                                    accountMessage = error.message?.takeIf(String::isNotBlank)?.let { "Account creation failed: ${it.take(180)}" } ?: "Account creation could not be completed. Check your details and connection."
                                }
                            } },
                            onSaveName = { name ->
                                displayName = name
                                prefs.edit().putString("display_name", name).putString("profile_user_id", SupabaseSync.userId()).putBoolean("profile_pending", true).apply()
                                if (accountEmail.isBlank() || !SupabaseSync.configured) {
                                    prefs.edit().putBoolean("profile_pending", false).apply()
                                    accountMessage = "Your name was saved on this device."
                                } else scope.launch {
                                    try {
                                        SupabaseSync.saveProfileName(name)
                                        prefs.edit().putBoolean("profile_pending", false).apply()
                                        accountMessage = "Your name was updated."
                                    } catch (_: Exception) { accountMessage = "Your name is saved on this device and will sync when you’re online." }
                                }
                            },
                            onSignOut = { scope.launch { runCatching { SupabaseSync.signOut() }; accountEmail = ""; passwordInput = ""; syncState = "Offline"; accountMessage = "Signed out. Your local plan remains available." } },
                            onSync = { scope.launch { syncNow() } },
                            wifiOnly = wifiOnly,
                            onWifiOnlyChange = { value -> wifiOnly = value; prefs.edit().putBoolean("wifi_only", value).apply(); scope.launch { syncNow() } },
                            syncOnOpen = syncOnOpen,
                            onSyncOnOpenChange = { value -> syncOnOpen = value; prefs.edit().putBoolean("sync_on_open", value).apply() },
                            localStorageSummary = "${formatStorageBytes(storageBytes)} stored on this device · ${visibleTasks.size} tasks",
                            onExport = { csvExportLauncher.launch("steady-reflections.csv") },
                            onBackup = { backupLauncher.launch("steady-offline-backup.steadybackup") },
                            onPasswordUpdate = { newPassword -> scope.launch {
                                try { SupabaseSync.updatePassword(newPassword); accountMessage = "Your password was updated." }
                                catch (_: Exception) { accountMessage = "Password could not be updated. Check your connection and try again." }
                            } },
                        )
                    }
                    "Tasks" -> TaskListScreen(visibleTasks, onEdit = { editing = it; showNew = true }, onStatus = { task, status -> scope.launch { repository.setStatus(task, status); ReminderScheduler.refresh(context); syncNow() } }, onDelete = { task -> scope.launch { repository.delete(task); syncNow() } })
                    "Check-in" -> CheckInScreen(checkIn) { priorities, intention, progress, reflection, outcome -> scope.launch { checkIns.save(java.time.LocalDate.now().toString(), intention, priorities, progress, reflection, outcome, checkIn); syncNow() } }
                    "Reminders" -> ReminderSettingsScreen(settings ?: ReminderSettingsEntity(), reminderHistory, onSave = { value -> scope.launch { settingsRepository.save(value); ReminderScheduler.refresh(context) } }, onEnable = { permissionExplanation = true }, onStopToday = { scope.launch { settingsRepository.save((settings ?: ReminderSettingsEntity()).copy(stoppedDate = java.time.LocalDate.now().toString())); ReminderScheduler.refresh(context) } })
                    "Usage" -> UsageDashboardScreen(
                        enabled = usageTracking?.enabled == true,
                        permission = usagePermission,
                        apps = usageApps,
                        rows = usageRows,
                        onEnabledChange = { enabled -> scope.launch {
                            usageDao.saveTracking(UsageTrackingEntity(enabled = enabled))
                            if (UsageRules.shouldDeleteAggregatesWhenDisabled(enabled)) {
                                withContext(Dispatchers.IO) { usageDao.deleteAllAggregates() }
                                usageRows = emptyList()
                                usagePermission = UsagePermissionState.DENIED
                            } else {
                                val opened = try {
                                    context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                                    true
                                } catch (_: ActivityNotFoundException) {
                                    runCatching { context.startActivity(Intent(Settings.ACTION_SETTINGS)); false }.getOrDefault(false)
                                }
                                if (!opened) usagePermission = UsagePermissionState.UNAVAILABLE
                                usageRefresh++
                            }
                        } },
                        onOpenSettings = {
                            val opened = try { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)); true }
                            catch (_: ActivityNotFoundException) { runCatching { context.startActivity(Intent(Settings.ACTION_SETTINGS)); false }.getOrDefault(false) }
                            if (!opened) usagePermission = UsagePermissionState.UNAVAILABLE
                        },
                        onRefresh = { usageRefresh++ },
                        onAppChange = { app -> scope.launch { usageDao.saveApp(app); usageRefresh++ } },
                    )
                    "Limits" -> AppBlockingScreen(
                        config = blockingConfig,
                        tasks = visibleTasks,
                        priorityTitles = checkIn?.prioritiesText?.lineSequence()?.map(String::trim)?.filter(String::isNotBlank)?.toSet().orEmpty(),
                        accessGranted = accessibilityGranted,
                        serviceConnected = blockingServiceConnected,
                        onSave = { value ->
                            val safeValue = value.copy(essentialPackages = value.essentialPackages + BlockingPreferences.essentialPackages(context))
                            blockingConfig = safeValue
                            BlockingPreferences.save(context, safeValue)
                            if (!safeValue.enabled) BlockingAccessibilityService.disable(context)
                        },
                        onRequestEnable = { value ->
                            val safeValue = value.copy(essentialPackages = value.essentialPackages + BlockingPreferences.essentialPackages(context))
                            blockingConfig = safeValue
                            BlockingPreferences.save(context, safeValue)
                            val opened = runCatching { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)); true }.getOrDefault(false)
                            if (!opened) accountMessage = "Open Android Settings → Accessibility → Steady to enable the optional service."
                        },
                        onRefreshAccess = {
                            accessibilityGranted = BlockingAccessibilityService.accessibilityEnabled(context)
                            blockingServiceConnected = BlockingAccessibilityService.connected
                            blockingConfig = BlockingPreferences.read(context)
                        },
                    )
                    else -> TodayScreen(visibleTasks, onEdit = { editing = it; showNew = true }, onStatus = { task, status -> scope.launch { repository.setStatus(task, status); ReminderScheduler.refresh(context); syncNow() } }, onDelete = { task -> scope.launch { repository.delete(task); syncNow() } })
                }
                }
            }
        }
        if (showNew) TaskEditorDialog(editing, onDismiss = { showNew = false }, onSave = { title, notes, date, time ->
            scope.launch {
                val now = java.time.Instant.now().toString()
                val original = editing
                val item = TaskEntity(original?.id ?: java.util.UUID.randomUUID().toString(), title, notes.ifBlank { null }, date, time.ifBlank { null }, original?.status ?: TaskStatus.PLANNED.name, original?.createdAt ?: now, now, syncPending = true)
                if (repository.save(item)) { showNew = false; ReminderScheduler.refresh(context); syncNow() }
            }
        })
        if (permissionExplanation) AlertDialog(
            onDismissRequest = { permissionExplanation = false },
            title = { Text("Allow local reminders?") },
            text = { Text("Steady can show notifications at the times you choose. Reminders stay on this device and are never sent to a service. You can turn them off any time.") },
            confirmButton = { TextButton(onClick = {
                permissionExplanation = false
                if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                else scope.launch { settingsRepository.save((settings ?: ReminderSettingsEntity()).copy(enabled = true)); ReminderScheduler.refresh(context) }
            }) { Text("Continue") } },
            dismissButton = { TextButton(onClick = { permissionExplanation = false }) { Text("Not now") } },
        )
    }
}

@Composable
private fun TodayScreen(tasks: List<TaskEntity>, onEdit: (TaskEntity) -> Unit, onStatus: (TaskEntity, TaskStatus) -> Unit, onDelete: (TaskEntity) -> Unit) {
    val today = java.time.LocalDate.now().toString()
    val todays = tasks.filter { it.scheduledDate == today }
    val active = todays.filter { it.status == TaskStatus.PLANNED.name }
    val finished = todays.filter { it.status != TaskStatus.PLANNED.name }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp)) {
        item {
            Column(Modifier.padding(top = 8.dp, bottom = 4.dp)) {
                Text("● YOUR SPACE · MINDFUL RHYTHM", style = MaterialTheme.typography.labelSmall, color = MutedInk, letterSpacing = 1.1.sp, fontWeight = FontWeight.Bold)
                Text("A little structure,\nwith room to breathe.", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold, color = Ink, modifier = Modifier.padding(top = 8.dp))
                Text("Every kind of day belongs here.", color = MutedInk, modifier = Modifier.padding(top = 4.dp))
            }
        }
        item {
            Surface(color = SoftStone, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("❀  TODAY · ${todays.count { it.status == TaskStatus.COMPLETED.name }} OF ${todays.size} GENTLE TASKS", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = Eucalyptus, fontWeight = FontWeight.Bold)
                        Text(if (active.isEmpty()) "Unrushed pace" else "Room to breathe", style = MaterialTheme.typography.labelSmall, color = MutedInk)
                    }
                    val progress = if (todays.isEmpty()) 0f else todays.count { it.status == TaskStatus.COMPLETED.name }.toFloat() / todays.size
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(7.dp), color = Eucalyptus, trackColor = Color(0xFFE3E2DF))
                }
            }
        }
        item {
            Surface(color = Stone, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(18.dp), verticalAlignment = Alignment.Top) {
                    Surface(color = Color.White, shape = RoundedCornerShape(50), modifier = Modifier.size(38.dp)) {
                        Box(contentAlignment = Alignment.Center) { Text("♧", color = Eucalyptus, style = MaterialTheme.typography.titleLarge) }
                    }
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text("SOFT NOTE", style = MaterialTheme.typography.labelSmall, color = MutedInk, letterSpacing = 1.sp)
                        Text("Nothing is rushed. A quiet day counts, too.", style = MaterialTheme.typography.titleMedium, color = Ink, modifier = Modifier.padding(top = 3.dp))
                        Text("No overdue flags or alarms. Just step forward when ready.", style = MaterialTheme.typography.bodySmall, color = MutedInk, modifier = Modifier.padding(top = 5.dp))
                    }
                }
            }
        }
        item {
            Surface(color = SoftStone, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("〰", color = Eucalyptus, style = MaterialTheme.typography.headlineSmall)
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text("Take a breath  ·  Inhale", color = Ink, fontWeight = FontWeight.Medium)
                        Text("4s inhale · 4s exhale, at your pace", style = MaterialTheme.typography.bodySmall, color = MutedInk)
                    }
                    Text("◉", color = Eucalyptus, style = MaterialTheme.typography.titleLarge)
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("TODAY’S FLOW", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = MutedInk, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Text("Restorative tasks", style = MaterialTheme.typography.bodySmall, color = MutedInk)
            }
        }
        if (active.isEmpty() && todays.isEmpty()) item { Text("Nothing planned right now. A quiet day counts, too.", color = MutedInk, modifier = Modifier.padding(vertical = 12.dp)) }
        items(active, key = { it.id }) { task -> TaskCard(task, onEdit, { status -> onStatus(task, status) }, { onDelete(task) }) }
        if (finished.isNotEmpty()) item { Text("ALREADY DONE OR SKIPPED", style = MaterialTheme.typography.labelMedium, color = MutedInk, modifier = Modifier.padding(top = 12.dp)) }
        items(finished, key = { it.id }) { task -> TaskCard(task, onEdit, { status -> onStatus(task, status) }, { onDelete(task) }) }
        val upcoming = tasks.filter { it.scheduledDate > today && it.status == TaskStatus.PLANNED.name }.sortedBy { it.scheduledDate }
        if (upcoming.isNotEmpty()) {
            item { Text("COMING UP", style = MaterialTheme.typography.labelMedium, color = Sage, modifier = Modifier.padding(top = 14.dp)) }
            items(upcoming, key = { it.id }) { task -> TaskCard(task, onEdit, { status -> onStatus(task, status) }, { onDelete(task) }) }
        }
    }
}

@Composable
private fun TaskListScreen(tasks: List<TaskEntity>, onEdit: (TaskEntity) -> Unit, onStatus: (TaskEntity, TaskStatus) -> Unit, onDelete: (TaskEntity) -> Unit) {
    var filter by remember { mutableStateOf("All") }
    val today = java.time.LocalDate.now().toString()
    val visible = when (filter) {
        "Today" -> tasks.filter { it.scheduledDate == today }
        "Upcoming" -> tasks.filter { it.scheduledDate > today && it.status == TaskStatus.PLANNED.name }
        else -> tasks
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp)) {
        item {
            Text("YOUR SPACE · TASKS", style = MaterialTheme.typography.labelSmall, color = MutedInk, letterSpacing = 1.sp, fontWeight = FontWeight.Bold)
            Text("Your tasks", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold, color = Ink, modifier = Modifier.padding(top = 4.dp))
            Text("Change the plan whenever you need.", color = MutedInk, modifier = Modifier.padding(top = 4.dp))
            Surface(color = Stone, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 12.dp)) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("♧", color = Eucalyptus, style = MaterialTheme.typography.titleLarge)
                    Text("${visible.size} tasks for a steady rhythm", modifier = Modifier.padding(start = 10.dp), color = Ink, fontWeight = FontWeight.Medium)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                listOf("All", "Today", "Upcoming").forEach { label ->
                    FilterChip(selected = filter == label, onClick = { filter = label }, label = { Text(label) }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Sage, selectedLabelColor = Cream))
                }
            }
        }
        if (visible.isEmpty()) item { Text(if (filter == "Upcoming") "No upcoming tasks yet. You can leave space in the plan." else "Your list is ready when you are.", color = MutedInk, modifier = Modifier.padding(vertical = 12.dp)) }
        items(visible.sortedWith(compareBy<TaskEntity> { it.scheduledDate }.thenBy { it.createdAt }), key = { it.id }) { task -> TaskCard(task, onEdit, { status -> onStatus(task, status) }, { onDelete(task) }) }
    }
}

@Composable
private fun TaskCard(task: TaskEntity, onEdit: (TaskEntity) -> Unit, onStatus: (TaskStatus) -> Unit, onDelete: () -> Unit = {}) {
    Surface(color = Color.White, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(color = if (task.status == TaskStatus.COMPLETED.name) Mint else SoftStone, shape = RoundedCornerShape(9.dp), modifier = Modifier.size(30.dp)) {
                    TextButton(onClick = { onStatus(if (task.status == TaskStatus.PLANNED.name) TaskStatus.COMPLETED else TaskStatus.PLANNED) }, contentPadding = PaddingValues(0.dp)) {
                        Text(if (task.status == TaskStatus.COMPLETED.name) "✓" else "", color = Sage)
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text(task.title, modifier = Modifier.padding(start = 10.dp), fontWeight = FontWeight.SemiBold, color = Ink)
                    Text(buildString { append(if (task.status == TaskStatus.PLANNED.name) "${task.scheduledDate} · Upcoming" else task.status.lowercase().replaceFirstChar(Char::uppercase)); task.targetTime?.let { append(" · ").append(it) }; if (task.status == TaskStatus.SKIPPED.name) append(" · You can revisit this later") }, modifier = Modifier.padding(start = 10.dp), style = MaterialTheme.typography.bodySmall, color = Eucalyptus)
                }
                TextButton(onClick = { onEdit(task) }) { Text("Edit") }
            }
            task.notes?.takeIf(String::isNotBlank)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Color(0xFF68736F), modifier = Modifier.padding(top = 4.dp)) }
            Row {
                if (task.status == TaskStatus.PLANNED.name) {
                    TextButton(onClick = { onStatus(TaskStatus.COMPLETED) }) { Text("Complete", color = Sage) }
                    TextButton(onClick = { onStatus(TaskStatus.SKIPPED) }) { Text("Skip", color = MutedInk) }
                } else TextButton(onClick = { onStatus(TaskStatus.PLANNED) }) { Text("Undo") }
                TextButton(onClick = onDelete) { Text("Delete") }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun TaskEditorDialog(task: TaskEntity?, onDismiss: () -> Unit, onSave: (String, String, String, String) -> Unit) {
    var title by remember(task?.id) { mutableStateOf(task?.title.orEmpty()) }
    var notes by remember(task?.id) { mutableStateOf(task?.notes.orEmpty()) }
    var date by remember(task?.id) { mutableStateOf(task?.scheduledDate ?: java.time.LocalDate.now().toString()) }
    var time by remember(task?.id) { mutableStateOf(task?.targetTime.orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Cream,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = { Surface(color = Color(0xFFD5D2C7), shape = RoundedCornerShape(50), modifier = Modifier.padding(top = 10.dp).size(width = 36.dp, height = 4.dp)) {} },
    ) {
        Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(if (task == null) "Add a task" else "Edit task", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold, color = Sage)
            Text("Carve out an intention for your day", color = MutedInk)
            OutlinedTextField(title, { title = it; error = null }, label = { Text("Task title") }, placeholder = { Text("e.g. Read two pages") }, singleLine = true, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth())
            OutlinedTextField(notes, { notes = it }, label = { Text("Notes (optional)") }, placeholder = { Text("Keep it brief, keep it kind…") }, minLines = 2, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(date, { date = it }, label = { Text("Date · YYYY-MM-DD") }, singleLine = true, shape = RoundedCornerShape(14.dp), modifier = Modifier.weight(1f))
                OutlinedTextField(time, { time = it }, label = { Text("Time · optional") }, singleLine = true, shape = RoundedCornerShape(14.dp), modifier = Modifier.weight(1f))
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("Cancel", color = MutedInk) }
                Button(onClick = {
                    val dateValid = runCatching { java.time.LocalDate.parse(date) }.isSuccess
                    val timeValid = time.isBlank() || runCatching { java.time.LocalTime.parse(time) }.isSuccess
                    error = TaskValidation.titleError(title) ?: if (!dateValid) "Use a date like 2026-10-25." else if (!timeValid) "Use a time like 09:30 or leave it blank." else null
                    if (error == null) onSave(title.trim(), notes, date, time)
                }, shape = RoundedCornerShape(50.dp), colors = ButtonDefaults.buttonColors(containerColor = Sage)) { Text("✓  Save gentle task") }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun CheckInScreen(checkIn: DailyCheckInEntity?, onSave: (List<String>, String, String, String, String?) -> Unit) {
    var priorities by remember(checkIn?.updatedAt) { mutableStateOf(checkIn?.prioritiesText?.split('\n')?.take(3) ?: emptyList()) }
    var intention by remember(checkIn?.updatedAt) { mutableStateOf(checkIn?.morningPlan.orEmpty()) }
    var progress by remember(checkIn?.updatedAt) { mutableStateOf(checkIn?.progressNote.orEmpty()) }
    var reflection by remember(checkIn?.updatedAt) { mutableStateOf(checkIn?.eveningReflection.orEmpty()) }
    var outcome by remember(checkIn?.updatedAt) { mutableStateOf(checkIn?.dayOutcome.orEmpty()) }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp)) {
        item {
            Text("MORNING RITUAL · A MOMENT FOR YOU", style = MaterialTheme.typography.labelSmall, color = MutedInk, letterSpacing = 1.sp, fontWeight = FontWeight.Bold)
            Text("A check-in, at your pace", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold, color = Sage, modifier = Modifier.padding(top = 5.dp))
            Text("Take a quiet breath. No rush, no pressure.", style = MaterialTheme.typography.bodyMedium, color = MutedInk, modifier = Modifier.padding(top = 4.dp, bottom = 8.dp))
        }
        item { Surface(color = Stone, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) { Text("☼  Gentle momentum over rigid discipline.", modifier = Modifier.padding(18.dp), color = Ink, fontWeight = FontWeight.Medium) } }
        item { Surface(color = Color.White, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text("♧  Morning plan", fontWeight = FontWeight.SemiBold, color = Sage, style = MaterialTheme.typography.titleMedium)
            Text("Choose up to three gentle priorities.", style = MaterialTheme.typography.bodySmall, color = MutedInk)
            repeat(3) { index ->
                OutlinedTextField(
                    value = priorities.getOrNull(index).orEmpty(),
                    onValueChange = { value -> priorities = priorities.toMutableList().also { while (it.size <= index) it.add(""); it[index] = value } },
                    label = { Text("PRIORITY 0${index + 1}") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Text("✧  A short intention (optional)", color = MutedInk, style = MaterialTheme.typography.labelMedium)
            OutlinedTextField(intention, { intention = it }, placeholder = { Text("Moving gently, with ease and patience…") }, minLines = 2, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth())
        } } }
        item { Surface(color = Color.White, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("◉  Progress check-in", fontWeight = FontWeight.SemiBold, color = Sage, style = MaterialTheme.typography.titleMedium)
            Text("How is it going right now?", style = MaterialTheme.typography.bodyMedium, color = MutedInk)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = progress == "I started", onClick = { progress = "I started" }, label = { Text("🌿 I started") }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Mint))
                FilterChip(selected = progress == "I need a smaller next step", onClick = { progress = "I need a smaller next step" }, label = { Text("☕ Pausing a bit") }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Mint))
            }
            FilterChip(selected = progress.startsWith("A smaller next step:"), onClick = { progress = "A smaller next step: " }, label = { Text("🌱 Help me start smaller") }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Mint))
            OutlinedTextField(progress, { progress = it }, label = { Text("A smaller next step (optional)") }, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth())
            Text("♡  Even one sentence or one breath is progress.", style = MaterialTheme.typography.bodySmall, color = MutedInk)
        } } }
        item { Surface(color = Color.White, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("☾  Evening reflection", fontWeight = FontWeight.SemiBold, color = Sage, style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(reflection, { reflection = it }, label = { Text("What would you like to remember?") }, minLines = 2, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { listOf("COMPLETE" to "Complete", "MIXED" to "Mixed", "REST" to "Rest").forEach { (key, label) -> FilterChip(selected = outcome == key, onClick = { outcome = key }, label = { Text(label) }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Mint)) } }
            Text("Rest is a valid way to spend a day.", style = MaterialTheme.typography.bodySmall, color = MutedInk)
        } } }
        item { Button(onClick = { onSave(priorities.map(String::trim).filter(String::isNotBlank).take(3), intention, progress, reflection, outcome.ifBlank { null }) }, shape = RoundedCornerShape(50), modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Sage)) { Text("Complete this check-in  →") } }
        item { Surface(color = SoftStone, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) { Text("You do not need to finish everything today to deserve rest.", modifier = Modifier.padding(15.dp), color = MutedInk) } }
    }
}

@Composable
private fun ReminderSettingsScreen(settings: ReminderSettingsEntity, history: List<ReminderHistoryEntity>, onSave: (ReminderSettingsEntity) -> Unit, onEnable: () -> Unit, onStopToday: () -> Unit) {
    var times by remember(settings.timesText) { mutableStateOf(settings.timesText) }
    var quietStart by remember(settings.quietStart) { mutableStateOf(settings.quietStart) }
    var quietEnd by remember(settings.quietEnd) { mutableStateOf(settings.quietEnd) }
    var limit by remember(settings.dailyLimit) { mutableStateOf(settings.dailyLimit.toString()) }
    var snooze by remember(settings.snoozeMinutes) { mutableStateOf(settings.snoozeMinutes.toString()) }
    var message by remember { mutableStateOf<String?>(null) }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp)) {
        item {
            Text("YOUR SPACE · RITUALS", style = MaterialTheme.typography.labelSmall, color = MutedInk, letterSpacing = 1.sp, fontWeight = FontWeight.Bold)
            Text("Reminder settings &\nbalance", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold, color = Ink, modifier = Modifier.padding(top = 5.dp))
            Text("Mindful nudges crafted around your natural rhythm. No urgent alarms, no pressure.", style = MaterialTheme.typography.bodyMedium, color = MutedInk, modifier = Modifier.padding(top = 4.dp))
        }
        item { Surface(color = Color(0xFFE4F2E5), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("◇", color = Eucalyptus, style = MaterialTheme.typography.titleLarge)
                Column(Modifier.padding(start = 10.dp)) {
                    Text("Device-first privacy ·", color = Sage, fontWeight = FontWeight.SemiBold)
                    Text("Reminders stay on your device. Zero cloud tracking, battery-aware and offline-friendly.", style = MaterialTheme.typography.bodySmall, color = MutedInk)
                }
            }
        } }
        item { Surface(color = Stone, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("PEACEFUL RHYTHM", style = MaterialTheme.typography.labelSmall, color = Eucalyptus, letterSpacing = 1.sp, fontWeight = FontWeight.Bold)
                    Text("Unhurried time,\nunhurried mind.", style = MaterialTheme.typography.titleMedium, color = Ink, modifier = Modifier.padding(top = 3.dp))
                }
                Text("◔", style = MaterialTheme.typography.headlineLarge, color = Eucalyptus)
            }
        } }
        item { Surface(color = Color.White, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { Text("Enable local reminders", modifier = Modifier.weight(1f)); Switch(checked = settings.enabled, onCheckedChange = { if (it) onEnable() else onSave(settings.copy(enabled = false)) }) }
            Text("Gentle schedule", style = MaterialTheme.typography.titleSmall, color = Ink, fontWeight = FontWeight.SemiBold)
            Text("Soft reminders spaced around your day", style = MaterialTheme.typography.bodySmall, color = MutedInk)
            OutlinedTextField(times, { times = it }, label = { Text("Reminder times · 24-hour") }, supportingText = { Text("For example: 09:00, 13:00, 18:00") }, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth())
            Surface(color = SoftStone, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("◉  Quiet sanctuary", color = Ink, fontWeight = FontWeight.Medium)
                Text("No reminders during your rest window.", style = MaterialTheme.typography.bodySmall, color = MutedInk)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(quietStart, { quietStart = it }, label = { Text("Quiet starts") }, shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f))
                    OutlinedTextField(quietEnd, { quietEnd = it }, label = { Text("Quiet ends") }, shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f))
                }
            } }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(limit, { limit = it.filter(Char::isDigit) }, label = { Text("Daily nudge limit") }, shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f))
                OutlinedTextField(snooze, { snooze = it.filter(Char::isDigit) }, label = { Text("Snooze minutes") }, shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f))
            }
            Button(onClick = {
                val parsedTimes = times.split(',').map(String::trim).filter(String::isNotBlank)
                val timesValid = parsedTimes.isNotEmpty() && parsedTimes.all { runCatching { java.time.LocalTime.parse(it) }.isSuccess }
                val quietValid = runCatching { java.time.LocalTime.parse(quietStart); java.time.LocalTime.parse(quietEnd) }.isSuccess
                val max = limit.toIntOrNull(); val snoozeValue = snooze.toIntOrNull()
                message = when { !timesValid -> "Enter one or more times such as 09:00."; !quietValid -> "Enter quiet hours as HH:MM."; max == null || max !in 1..12 -> "Daily limit must be between 1 and 12."; snoozeValue == null || snoozeValue !in 5..120 -> "Snooze must be between 5 and 120 minutes."; else -> null }
                if (message == null) onSave(settings.copy(timesText = parsedTimes.distinct().joinToString(","), quietStart = quietStart, quietEnd = quietEnd, dailyLimit = max!!, snoozeMinutes = snoozeValue!!))
            }, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Sage)) { Text("✓  Save schedule") }
            message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            TextButton(onClick = onStopToday) { Text("Stop reminders for today") }
            Text("Notifications require your permission. You can snooze an individual reminder when it appears.", style = MaterialTheme.typography.bodySmall, color = MutedInk)
        } } }
        item {
            Surface(color = Color.White, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Reminder history", color = Sage, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    if (history.isEmpty()) Text("Reminders that are delivered will appear here with the reason they were sent.", color = MutedInk, style = MaterialTheme.typography.bodySmall)
                    history.take(10).forEach { entry ->
                        val time = java.time.Instant.ofEpochMilli(entry.occurredAtMillis).atZone(java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("MMM d · h:mm a"))
                        Text("$time\n${entry.reason}", color = MutedInk, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
