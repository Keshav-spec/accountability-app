package com.steady.accountability

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.room.Room
import com.steady.accountability.data.*
import kotlinx.coroutines.launch

private val Sage = Color(0xFF375A50)
private val Cream = Color(0xFFF7F5EF)
private val Ink = Color(0xFF24312E)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val db = Room.databaseBuilder(applicationContext, AppDatabase::class.java, "steady.db").build()
        setContent { SteadyApp(TaskRepository(db.taskDao()), CheckInRepository(db.checkInDao())) }
    }
}

@Composable
private fun SteadyApp(repository: TaskRepository, checkIns: CheckInRepository) {
    var selected by remember { mutableStateOf("Today") }
    var showAdd by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val tasks by repository.tasksForToday().collectAsState(initial = emptyList())
    val checkIn by checkIns.forToday().collectAsState(initial = null)
    val scope = rememberCoroutineScope()
    MaterialTheme(colorScheme = lightColorScheme(primary = Sage, background = Cream, surface = Color.White, onSurface = Ink)) {
        Scaffold(containerColor = Cream, bottomBar = {
            NavigationBar(containerColor = Color.White) {
                listOf("Today", "Tasks", "Check-in").forEach { tab ->
                    NavigationBarItem(selected = selected == tab, onClick = { selected = tab }, icon = {}, label = { Text(tab) })
                }
            }
        }, floatingActionButton = {
            if (selected != "Check-in") FloatingActionButton(onClick = { showAdd = true }, containerColor = Sage, contentColor = Color.White) { Text("+") }
        }) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 24.dp, vertical = 20.dp)) {
                Text("STEADY  ·  YOUR SPACE", style = MaterialTheme.typography.labelMedium, color = Sage, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(20.dp))
                when (selected) {
                    "Check-in" -> CheckInScreen(checkIn) { morning, evening -> scope.launch { checkIns.save(morning, evening, checkIn) } }
                    else -> TodayScreen(tasks, onToggle = { task -> scope.launch { repository.toggle(task) } })
                }
            }
        }
        if (showAdd) AlertDialog(
            onDismissRequest = { showAdd = false; error = null },
            title = { Text("Add a small task") },
            text = { Column { OutlinedTextField(value = title, onValueChange = { title = it; error = null }, label = { Text("Task title") }, singleLine = true); error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) } } },
            confirmButton = { TextButton(onClick = { scope.launch { if (repository.add(title)) { title = ""; showAdd = false } else error = TaskValidation.titleError(title) } }) { Text("Add task") } },
            dismissButton = { TextButton(onClick = { showAdd = false; error = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun TodayScreen(tasks: List<TaskEntity>, onToggle: (TaskEntity) -> Unit) {
    Text("A little structure,\nwith room to breathe.", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold, color = Ink)
    Spacer(Modifier.height(8.dp))
    Text("One step at a time is enough.", color = Color(0xFF68736F))
    Spacer(Modifier.height(28.dp))
    Surface(color = Color.White, shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { Text("Today's gentle plan", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold); Spacer(Modifier.weight(1f)); Text("${tasks.count { it.status == "COMPLETED" }} / ${tasks.size}", color = Sage) }
            Spacer(Modifier.height(12.dp))
            if (tasks.isEmpty()) Text("Nothing on your list yet. Add one small thing when you're ready.", color = Color(0xFF68736F), modifier = Modifier.padding(vertical = 12.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(tasks, key = { it.id }) { task ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Checkbox(checked = task.status == "COMPLETED", onCheckedChange = { onToggle(task) }, colors = CheckboxDefaults.colors(checkedColor = Sage))
                        Text(task.title, color = Ink, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
            if (tasks.isNotEmpty()) Text("You can adjust your plan as the day unfolds.", style = MaterialTheme.typography.bodySmall, color = Color(0xFF68736F), modifier = Modifier.padding(top = 12.dp))
        }
    }
}

@Composable
private fun CheckInScreen(checkIn: DailyCheckInEntity?, onSave: (String, String) -> Unit) {
    var morning by remember(checkIn?.createdAt) { mutableStateOf(checkIn?.morningPlan.orEmpty()) }
    var evening by remember(checkIn?.createdAt) { mutableStateOf(checkIn?.eveningReflection.orEmpty()) }
    Text("Check in with yourself", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold, color = Ink)
    Spacer(Modifier.height(8.dp))
    Text("A quiet moment to notice what you need.", color = Color(0xFF68736F))
    Spacer(Modifier.height(24.dp))
    listOf("Morning plan" to "What would make today feel steady?", "Evening reflection" to "What went well, even a little?").forEach { (label, hint) ->
        Surface(color = Color.White, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp)) {
            Column(Modifier.padding(18.dp)) { Text(label, fontWeight = FontWeight.SemiBold, color = Sage); Spacer(Modifier.height(10.dp)); OutlinedTextField(value = if (label == "Morning plan") morning else evening, onValueChange = { value -> if (label == "Morning plan") morning = value else evening = value; onSave(morning, evening) }, placeholder = { Text(hint) }, modifier = Modifier.fillMaxWidth(), minLines = 3) }
        }
    }
    Text("Your check-in is saved on this device.", style = MaterialTheme.typography.bodySmall, color = Color(0xFF68736F))
}
