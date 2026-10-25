package com.steady.accountability

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.steady.accountability.data.*
import com.steady.accountability.reminders.ReminderScheduler
import kotlinx.coroutines.launch

private val Sage = Color(0xFF375A50)
private val Cream = Color(0xFFF7F5EF)
private val Ink = Color(0xFF24312E)
private val Tabs = listOf("Today", "Tasks", "Check-in", "Reminders")

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val db = DatabaseProvider.get(applicationContext)
        lifecycleScope.launch { ReminderScheduler.refresh(applicationContext) }
        setContent { SteadyApp(TaskRepository(db.taskDao()), CheckInRepository(db.checkInDao()), ReminderSettingsRepository(db.reminderSettingsDao())) }
    }
}

@Composable
private fun SteadyApp(repository: TaskRepository, checkIns: CheckInRepository, settingsRepository: ReminderSettingsRepository) {
    var selected by remember { mutableStateOf("Today") }
    var editing by remember { mutableStateOf<TaskEntity?>(null) }
    var showNew by remember { mutableStateOf(false) }
    var permissionExplanation by remember { mutableStateOf(false) }
    val tasks by repository.allTasks().collectAsState(initial = emptyList())
    val checkIn by checkIns.forDate().collectAsState(initial = null)
    val settings by settingsRepository.observe().collectAsState(initial = null)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) scope.launch { settingsRepository.save((settings ?: ReminderSettingsEntity()).copy(enabled = true)); ReminderScheduler.refresh(context) }
    }
    MaterialTheme(colorScheme = lightColorScheme(primary = Sage, background = Cream, surface = Color.White, onSurface = Ink)) {
        Scaffold(containerColor = Cream, bottomBar = {
            NavigationBar(containerColor = Color.White) {
                Tabs.forEach { tab -> NavigationBarItem(selected = selected == tab, onClick = { selected = tab }, icon = {}, label = { Text(tab) }) }
            }
        }, floatingActionButton = {
            if (selected == "Today" || selected == "Tasks") FloatingActionButton(onClick = { editing = null; showNew = true }, containerColor = Sage, contentColor = Color.White) { Text("+") }
        }) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 22.dp, vertical = 18.dp)) {
                Text("STEADY  ·  YOUR SPACE", style = MaterialTheme.typography.labelMedium, color = Sage, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(16.dp))
                when (selected) {
                    "Tasks" -> TaskListScreen(tasks, onEdit = { editing = it; showNew = true }, onStatus = { task, status -> scope.launch { repository.setStatus(task, status); ReminderScheduler.refresh(context) } })
                    "Check-in" -> CheckInScreen(checkIn) { priorities, intention, progress, reflection, outcome -> scope.launch { checkIns.save(java.time.LocalDate.now().toString(), intention, priorities, progress, reflection, outcome, checkIn) } }
                    "Reminders" -> ReminderSettingsScreen(settings ?: ReminderSettingsEntity(), onSave = { value -> scope.launch { settingsRepository.save(value); ReminderScheduler.refresh(context) } }, onEnable = { permissionExplanation = true }, onStopToday = { scope.launch { settingsRepository.save((settings ?: ReminderSettingsEntity()).copy(stoppedDate = java.time.LocalDate.now().toString())); ReminderScheduler.refresh(context) } })
                    else -> TodayScreen(tasks, onEdit = { editing = it; showNew = true }, onStatus = { task, status -> scope.launch { repository.setStatus(task, status); ReminderScheduler.refresh(context) } })
                }
            }
        }
        if (showNew) TaskEditorDialog(editing, onDismiss = { showNew = false }, onSave = { title, notes, date, time ->
            scope.launch {
                val now = java.time.Instant.now().toString()
                val original = editing
                val item = TaskEntity(original?.id ?: java.util.UUID.randomUUID().toString(), title, notes.ifBlank { null }, date, time.ifBlank { null }, original?.status ?: TaskStatus.PLANNED.name, original?.createdAt ?: now, now)
                if (repository.save(item)) { showNew = false; ReminderScheduler.refresh(context) }
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
private fun TodayScreen(tasks: List<TaskEntity>, onEdit: (TaskEntity) -> Unit, onStatus: (TaskEntity, TaskStatus) -> Unit) {
    val today = java.time.LocalDate.now().toString()
    val todays = tasks.filter { it.scheduledDate == today }
    val active = todays.filter { it.status == TaskStatus.PLANNED.name }
    val finished = todays.filter { it.status != TaskStatus.PLANNED.name }
    Text("A little structure,\nwith room to breathe.", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold, color = Ink)
    Text("Every kind of day belongs here.", color = Color(0xFF68736F), modifier = Modifier.padding(top = 6.dp, bottom = 18.dp))
    Text("TODAY · ${active.count { it.status == TaskStatus.COMPLETED.name }} / ${todays.size}", style = MaterialTheme.typography.labelMedium, color = Sage, fontWeight = FontWeight.Bold)
    if (active.isEmpty()) Text("Nothing planned right now. A quiet day counts, too.", color = Color(0xFF68736F), modifier = Modifier.padding(vertical = 12.dp))
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp)) {
        items(active, key = { it.id }) { task -> TaskCard(task, onEdit) { status -> onStatus(task, status) } }
        if (finished.isNotEmpty()) item { Text("ALREADY DONE OR SKIPPED", style = MaterialTheme.typography.labelMedium, color = Color(0xFF68736F), modifier = Modifier.padding(top = 12.dp)) }
        items(finished, key = { it.id }) { task -> TaskCard(task, onEdit) { status -> onStatus(task, status) } }
        val upcoming = tasks.filter { it.scheduledDate > today && it.status == TaskStatus.PLANNED.name }.sortedBy { it.scheduledDate }
        if (upcoming.isNotEmpty()) {
            item { Text("COMING UP", style = MaterialTheme.typography.labelMedium, color = Sage, modifier = Modifier.padding(top = 14.dp)) }
            items(upcoming, key = { it.id }) { task -> TaskCard(task, onEdit) { status -> onStatus(task, status) } }
        }
    }
}

@Composable
private fun TaskListScreen(tasks: List<TaskEntity>, onEdit: (TaskEntity) -> Unit, onStatus: (TaskEntity, TaskStatus) -> Unit) {
    Text("Your tasks", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold, color = Ink)
    Text("Change the plan whenever you need.", color = Color(0xFF68736F), modifier = Modifier.padding(top = 6.dp, bottom = 16.dp))
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp)) {
        if (tasks.isEmpty()) item { Text("Your list is ready when you are.", color = Color(0xFF68736F)) }
        items(tasks.sortedWith(compareBy<TaskEntity> { it.scheduledDate }.thenBy { it.createdAt }), key = { it.id }) { task -> TaskCard(task, onEdit) { status -> onStatus(task, status) } }
    }
}

@Composable
private fun TaskCard(task: TaskEntity, onEdit: (TaskEntity) -> Unit, onStatus: (TaskStatus) -> Unit) {
    Surface(color = Color.White, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 15.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(task.title, fontWeight = FontWeight.SemiBold, color = Ink)
                    Text(buildString { append(task.scheduledDate); task.targetTime?.let { append(" · ").append(it) }; if (task.status != TaskStatus.PLANNED.name) append(" · ").append(task.status.lowercase()) }, style = MaterialTheme.typography.bodySmall, color = Color(0xFF68736F))
                }
                TextButton(onClick = { onEdit(task) }) { Text("Edit") }
            }
            task.notes?.takeIf(String::isNotBlank)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Color(0xFF68736F), modifier = Modifier.padding(top = 4.dp)) }
            Row {
                if (task.status == TaskStatus.PLANNED.name) {
                    TextButton(onClick = { onStatus(TaskStatus.COMPLETED) }) { Text("Complete") }
                    TextButton(onClick = { onStatus(TaskStatus.SKIPPED) }) { Text("Skip") }
                } else TextButton(onClick = { onStatus(TaskStatus.PLANNED) }) { Text("Undo") }
            }
        }
    }
}

@Composable
private fun TaskEditorDialog(task: TaskEntity?, onDismiss: () -> Unit, onSave: (String, String, String, String) -> Unit) {
    var title by remember(task?.id) { mutableStateOf(task?.title.orEmpty()) }
    var notes by remember(task?.id) { mutableStateOf(task?.notes.orEmpty()) }
    var date by remember(task?.id) { mutableStateOf(task?.scheduledDate ?: java.time.LocalDate.now().toString()) }
    var time by remember(task?.id) { mutableStateOf(task?.targetTime.orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (task == null) "Add a task" else "Edit task") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(title, { title = it; error = null }, label = { Text("Title") }, singleLine = true)
            OutlinedTextField(notes, { notes = it }, label = { Text("Notes (optional)") }, minLines = 2)
            OutlinedTextField(date, { date = it }, label = { Text("Date (YYYY-MM-DD)") }, singleLine = true)
            OutlinedTextField(time, { time = it }, label = { Text("Target time (HH:MM, optional)") }, singleLine = true)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        }
    }, confirmButton = { TextButton(onClick = {
        val dateValid = runCatching { java.time.LocalDate.parse(date) }.isSuccess
        val timeValid = time.isBlank() || runCatching { java.time.LocalTime.parse(time) }.isSuccess
        error = TaskValidation.titleError(title) ?: if (!dateValid) "Use a date like 2026-10-25." else if (!timeValid) "Use a time like 09:30 or leave it blank." else null
        if (error == null) onSave(title.trim(), notes, date, time)
    }) { Text("Save") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
private fun CheckInScreen(checkIn: DailyCheckInEntity?, onSave: (List<String>, String, String, String, String?) -> Unit) {
    var priorities by remember(checkIn?.updatedAt) { mutableStateOf(checkIn?.prioritiesText?.split('\n')?.take(3) ?: emptyList()) }
    var intention by remember(checkIn?.updatedAt) { mutableStateOf(checkIn?.morningPlan.orEmpty()) }
    var progress by remember(checkIn?.updatedAt) { mutableStateOf(checkIn?.progressNote.orEmpty()) }
    var reflection by remember(checkIn?.updatedAt) { mutableStateOf(checkIn?.eveningReflection.orEmpty()) }
    var outcome by remember(checkIn?.updatedAt) { mutableStateOf(checkIn?.dayOutcome.orEmpty()) }
    Text("A check-in, at your pace", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold, color = Ink)
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp)) {
        item { Surface(color = Color.White, shape = RoundedCornerShape(18.dp)) { Column(Modifier.padding(16.dp)) {
            Text("Morning plan", fontWeight = FontWeight.SemiBold, color = Sage)
            Text("Choose up to three gentle priorities.", style = MaterialTheme.typography.bodySmall, color = Color(0xFF68736F))
            repeat(3) { index -> OutlinedTextField(value = priorities.getOrNull(index).orEmpty(), onValueChange = { value -> priorities = priorities.toMutableList().also { while (it.size <= index) it.add(""); it[index] = value } }, label = { Text("Priority ${index + 1}") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
            OutlinedTextField(intention, { intention = it }, label = { Text("A short intention (optional)") }, minLines = 2, modifier = Modifier.fillMaxWidth())
        } } }
        item { Surface(color = Color.White, shape = RoundedCornerShape(18.dp)) { Column(Modifier.padding(16.dp)) {
            Text("Progress check-in", fontWeight = FontWeight.SemiBold, color = Sage)
            Text("How is it going?", style = MaterialTheme.typography.bodySmall, color = Color(0xFF68736F))
            Row { TextButton(onClick = { progress = "I started" }) { Text("I started") }; TextButton(onClick = { progress = "I need a smaller next step" }) { Text("Help me start smaller") } }
            OutlinedTextField(progress, { progress = it }, label = { Text("A smaller next step (optional)") }, modifier = Modifier.fillMaxWidth())
        } } }
        item { Surface(color = Color.White, shape = RoundedCornerShape(18.dp)) { Column(Modifier.padding(16.dp)) {
            Text("Evening review", fontWeight = FontWeight.SemiBold, color = Sage)
            OutlinedTextField(reflection, { reflection = it }, label = { Text("What would you like to remember?") }, minLines = 2, modifier = Modifier.fillMaxWidth())
            Row { listOf("COMPLETE" to "Complete", "MIXED" to "Mixed", "REST" to "Rest").forEach { (key, label) -> FilterChip(selected = outcome == key, onClick = { outcome = key }, label = { Text(label) }, modifier = Modifier.padding(end = 4.dp)) } }
            Text("Rest is a valid way to spend a day.", style = MaterialTheme.typography.bodySmall, color = Color(0xFF68736F))
        } } }
        item { Button(onClick = { onSave(priorities.map(String::trim).filter(String::isNotBlank).take(3), intention, progress, reflection, outcome.ifBlank { null }) }, colors = ButtonDefaults.buttonColors(containerColor = Sage)) { Text("Save check-in") } }
    }
}

@Composable
private fun ReminderSettingsScreen(settings: ReminderSettingsEntity, onSave: (ReminderSettingsEntity) -> Unit, onEnable: () -> Unit, onStopToday: () -> Unit) {
    var times by remember(settings.timesText) { mutableStateOf(settings.timesText) }
    var quietStart by remember(settings.quietStart) { mutableStateOf(settings.quietStart) }
    var quietEnd by remember(settings.quietEnd) { mutableStateOf(settings.quietEnd) }
    var limit by remember(settings.dailyLimit) { mutableStateOf(settings.dailyLimit.toString()) }
    var snooze by remember(settings.snoozeMinutes) { mutableStateOf(settings.snoozeMinutes.toString()) }
    var message by remember { mutableStateOf<String?>(null) }
    Text("Reminder settings", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold, color = Ink)
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp)) {
        item { Surface(color = Color.White, shape = RoundedCornerShape(18.dp)) { Column(Modifier.padding(16.dp)) {
            Text("Reminders stay on this device. Android may deliver them a little later to save battery.", style = MaterialTheme.typography.bodyMedium, color = Color(0xFF68736F))
            Row(verticalAlignment = Alignment.CenterVertically) { Text("Enable local reminders", modifier = Modifier.weight(1f)); Switch(checked = settings.enabled, onCheckedChange = { if (it) onEnable() else onSave(settings.copy(enabled = false)) }) }
            OutlinedTextField(times, { times = it }, label = { Text("Times (comma-separated, 24-hour)") }, supportingText = { Text("For example: 09:00, 13:00, 18:00") }, modifier = Modifier.fillMaxWidth())
            Row { OutlinedTextField(quietStart, { quietStart = it }, label = { Text("Quiet starts") }, modifier = Modifier.weight(1f).padding(end = 5.dp)); OutlinedTextField(quietEnd, { quietEnd = it }, label = { Text("Quiet ends") }, modifier = Modifier.weight(1f).padding(start = 5.dp)) }
            Row { OutlinedTextField(limit, { limit = it.filter(Char::isDigit) }, label = { Text("Daily max") }, modifier = Modifier.weight(1f).padding(end = 5.dp)); OutlinedTextField(snooze, { snooze = it.filter(Char::isDigit) }, label = { Text("Snooze minutes") }, modifier = Modifier.weight(1f).padding(start = 5.dp)) }
            Button(onClick = {
                val parsedTimes = times.split(',').map(String::trim).filter(String::isNotBlank)
                val timesValid = parsedTimes.isNotEmpty() && parsedTimes.all { runCatching { java.time.LocalTime.parse(it) }.isSuccess }
                val quietValid = runCatching { java.time.LocalTime.parse(quietStart); java.time.LocalTime.parse(quietEnd) }.isSuccess
                val max = limit.toIntOrNull(); val snoozeValue = snooze.toIntOrNull()
                message = when { !timesValid -> "Enter one or more times such as 09:00."; !quietValid -> "Enter quiet hours as HH:MM."; max == null || max !in 1..12 -> "Daily limit must be between 1 and 12."; snoozeValue == null || snoozeValue !in 5..120 -> "Snooze must be between 5 and 120 minutes."; else -> null }
                if (message == null) onSave(settings.copy(timesText = parsedTimes.distinct().joinToString(","), quietStart = quietStart, quietEnd = quietEnd, dailyLimit = max!!, snoozeMinutes = snoozeValue!!))
            }, colors = ButtonDefaults.buttonColors(containerColor = Sage)) { Text("Save schedule") }
            message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            TextButton(onClick = onStopToday) { Text("Stop reminders for today") }
            Text("Notifications require your permission. You can also snooze an individual reminder when it appears.", style = MaterialTheme.typography.bodySmall, color = Color(0xFF68736F))
        } } }
    }
}
