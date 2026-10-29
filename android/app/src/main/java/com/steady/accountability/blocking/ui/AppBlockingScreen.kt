package com.steady.accountability.blocking.ui

import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.steady.accountability.blocking.*
import com.steady.accountability.data.TaskEntity
import java.time.LocalDate

data class BlockingApp(val packageName: String, val name: String)

@Composable
fun AppBlockingScreen(
    config: BlockingConfig,
    tasks: List<TaskEntity>,
    priorityTitles: Set<String>,
    accessGranted: Boolean,
    serviceConnected: Boolean,
    onSave: (BlockingConfig) -> Unit,
    onRequestEnable: (BlockingConfig) -> Unit,
    onRefreshAccess: () -> Unit,
) {
    val context = LocalContext.current
    var draft by remember(config) { mutableStateOf(config) }
    var search by remember { mutableStateOf("") }
    var validation by remember { mutableStateOf<String?>(null) }
    var showApps by remember { mutableStateOf(false) }
    var showDisclosure by remember { mutableStateOf(false) }
    val apps = remember(context) { launcherApps(context.packageManager, context.packageName) }
    val available = apps.filter { it.name.contains(search, true) || it.packageName.contains(search, true) }
    val today = LocalDate.now().toString()
    val priorities = tasks.filter { task -> task.scheduledDate == today && task.deletedAt == null && task.status == "PLANNED" && priorityTitles.any { it.equals(task.title.trim(), ignoreCase = true) } }
    val recovery = BlockingRules.recoveryState(draft.enabled, accessGranted, serviceConnected)

    if (showDisclosure) AlertDialog(
        onDismissRequest = { showDisclosure = false },
        title = { Text("How app limits work") },
        text = { Text("To show a pause screen, Android requires Steady’s Accessibility access. While blocking is on, Steady checks the foreground app’s package name against the apps you select. It does not read screen text or content, take screenshots, click controls, or upload app activity. This is an optional accountability aid and can be bypassed, paused, disabled in Settings, or uninstalled at any time. Android may restrict the service in the background; if it stops, turn access off and on again here.") },
        confirmButton = { TextButton(onClick = {
            validation = when {
                draft.blockedPackages.isEmpty() -> "Choose at least one app before enabling blocking."
                draft.ruleType == BlockingRuleType.PRIORITY_TASKS && draft.selectedTaskIds.isEmpty() -> "Choose at least one task, or switch to a break-time rule."
                draft.ruleType == BlockingRuleType.BREAK_TIME && runCatching { java.time.LocalTime.parse(draft.breakTime) }.isFailure -> "Enter the break time as HH:MM, for example 18:00."
                else -> null
            }
            showDisclosure = false
            if (validation == null) onRequestEnable(draft.copy(enabled = true, ruleDate = today, essentialPackages = draft.essentialPackages + apps.filter { it.packageName in setOf("com.android.settings", "com.android.systemui", "com.android.phone", "com.google.android.permissioncontroller", "com.android.permissioncontroller") }.map { it.packageName }))
        }) { Text("Continue to Settings") } },
        dismissButton = { TextButton(onClick = { showDisclosure = false }) { Text("Cancel") } },
    )
    if (showApps) AlertDialog(
        onDismissRequest = { showApps = false },
        title = { Text("Choose apps") },
        text = { Column(Modifier.heightIn(max = 480.dp)) {
            OutlinedTextField(search, { search = it }, label = { Text("Search apps") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            LazyColumn(Modifier.weight(1f, fill = false)) {
                items(available, key = { it.packageName }) { app ->
                    val selected = app.packageName in draft.blockedPackages
                    Row(Modifier.fillMaxWidth().clickable {
                        draft = if (selected) draft.copy(blockedPackages = draft.blockedPackages - app.packageName, exceptionPackages = draft.exceptionPackages - app.packageName)
                        else draft.copy(blockedPackages = draft.blockedPackages + app.packageName)
                    }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(selected, onCheckedChange = { checked -> draft = if (checked) draft.copy(blockedPackages = draft.blockedPackages + app.packageName) else draft.copy(blockedPackages = draft.blockedPackages - app.packageName) })
                        Text(app.name, Modifier.weight(1f))
                        if (selected) Text("Exception", Modifier.clickable {
                            draft = if (app.packageName in draft.exceptionPackages) draft.copy(exceptionPackages = draft.exceptionPackages - app.packageName) else draft.copy(exceptionPackages = draft.exceptionPackages + app.packageName)
                        }, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            Text("Tap ‘Exception’ for an app you want to leave available.", style = MaterialTheme.typography.bodySmall)
        } },
        confirmButton = { TextButton(onClick = { showApps = false; search = "" }) { Text("Done") } },
    )

    Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
        Text("App blocking & gentle pauses", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Text("A gentle pause for the apps you choose. Blocking is off until you opt in.", style = MaterialTheme.typography.bodyMedium)
            Card(shape = RoundedCornerShape(20.dp)) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text("Blocking", fontWeight = FontWeight.SemiBold); Text("Off by default · always bypassable", style = MaterialTheme.typography.bodySmall) }
                    Switch(checked = draft.enabled, onCheckedChange = { if (it) showDisclosure = true else { draft = draft.copy(enabled = false); onSave(draft.copy(enabled = false)) } })
                }
                when (recovery) {
                    BlockingRecoveryState.NEEDS_ACCESS -> Text("Blocking is enabled, but Android access is off. Continue to Accessibility settings to restore it.", color = MaterialTheme.colorScheme.error)
                    BlockingRecoveryState.SERVICE_NOT_CONNECTED -> Text("The service is not connected. Open Accessibility settings, turn Steady access off and on, then return and refresh.")
                    BlockingRecoveryState.READY -> Text("Ready · Steady checks only which selected app is in the foreground.")
                    BlockingRecoveryState.OFF -> Text("Your chosen apps remain available while blocking is off.")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { runCatching { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) } }) { Text("Accessibility settings") }
                    TextButton(onClick = onRefreshAccess) { Text("Refresh") }
                }
            } }
            Card(shape = RoundedCornerShape(20.dp)) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Your rule", fontWeight = FontWeight.SemiBold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(draft.ruleType == BlockingRuleType.PRIORITY_TASKS, onClick = { draft = draft.copy(ruleType = BlockingRuleType.PRIORITY_TASKS) })
                    Text("Until today’s selected tasks are complete or skipped")
                }
                if (draft.ruleType == BlockingRuleType.PRIORITY_TASKS && priorities.isEmpty()) Text("Add today's priorities in Check-in and make sure each has a matching planned task before using this rule.", style = MaterialTheme.typography.bodySmall)
                if (draft.ruleType == BlockingRuleType.PRIORITY_TASKS) priorities.forEach { task ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(task.id in draft.selectedTaskIds, onCheckedChange = { checked ->
                            if (checked && draft.selectedTaskIds.size >= 3) validation = "Choose up to three priority tasks."
                            else {
                                validation = null
                                draft = if (checked) draft.copy(selectedTaskIds = draft.selectedTaskIds + task.id) else draft.copy(selectedTaskIds = draft.selectedTaskIds - task.id)
                            }
                        })
                        Text(task.title)
                    }
                }
                if (draft.ruleType == BlockingRuleType.PRIORITY_TASKS) Text("Choose up to three tasks. Blocking releases after each selected task is complete or skipped.", style = MaterialTheme.typography.bodySmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(draft.ruleType == BlockingRuleType.BREAK_TIME, onClick = { draft = draft.copy(ruleType = BlockingRuleType.BREAK_TIME) })
                    Text("Until a break time")
                }
                if (draft.ruleType == BlockingRuleType.BREAK_TIME) OutlinedTextField(draft.breakTime, { draft = draft.copy(breakTime = it) }, label = { Text("Break time (HH:MM)") }, singleLine = true)
                OutlinedTextField(draft.breakDurationMinutes.toString(), { value -> value.toIntOrNull()?.takeIf { it in 5..120 }?.let { draft = draft.copy(breakDurationMinutes = it) } }, label = { Text("Take a break (5–120 minutes)") }, singleLine = true)
            } }
            Card(shape = RoundedCornerShape(20.dp)) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Selected apps", fontWeight = FontWeight.SemiBold)
                if (draft.blockedPackages.isEmpty()) Text("No apps selected yet.")
                draft.blockedPackages.forEach { packageName ->
                    val label = apps.firstOrNull { it.packageName == packageName }?.name ?: packageName
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(label, Modifier.weight(1f))
                        TextButton(onClick = { draft = draft.copy(exceptionPackages = if (packageName in draft.exceptionPackages) draft.exceptionPackages - packageName else draft.exceptionPackages + packageName) }) { Text(if (packageName in draft.exceptionPackages) "Allow" else "Exception") }
                        TextButton(onClick = { draft = draft.copy(blockedPackages = draft.blockedPackages - packageName, exceptionPackages = draft.exceptionPackages - packageName) }) { Text("Remove") }
                    }
                }
                OutlinedButton(onClick = { showApps = true }, modifier = Modifier.fillMaxWidth()) { Text("Add or choose apps") }
            } }
            Card(shape = RoundedCornerShape(20.dp)) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Safety controls", fontWeight = FontWeight.SemiBold)
                Text("System settings, the phone app, your launcher, Steady, and the apps you mark as exceptions remain available. Android may delay or stop background services; we do not bypass battery settings.", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { draft = draft.copy(pauseUntilMillis = BlockingRules.pauseFor(System.currentTimeMillis(), 15)); onSave(draft) }) { Text("Pause 15 min") }
                    OutlinedButton(onClick = { draft = draft.copy(pauseUntilMillis = BlockingRules.pauseFor(System.currentTimeMillis(), draft.breakDurationMinutes)); onSave(draft) }) { Text("Take a break") }
                }
                OutlinedButton(onClick = { draft = draft.copy(bypassUntilMillis = BlockingRules.bypassUntilTomorrow(java.time.LocalDateTime.now(), java.time.ZoneId.systemDefault())); onSave(draft) }) { Text("Emergency bypass until tomorrow") }
                Button(onClick = {
                    validation = when {
                        draft.enabled && draft.blockedPackages.isEmpty() -> "Choose at least one app before enabling blocking."
                        draft.enabled && draft.ruleType == BlockingRuleType.PRIORITY_TASKS && draft.selectedTaskIds.isEmpty() -> "Choose at least one task, or switch to a break-time rule."
                        draft.ruleType == BlockingRuleType.BREAK_TIME && runCatching { java.time.LocalTime.parse(draft.breakTime) }.isFailure -> "Enter the break time as HH:MM, for example 18:00."
                        else -> null
                    }
                    if (validation == null) {
                        if (draft.enabled && !accessGranted) showDisclosure = true
                        else { draft = draft.copy(ruleDate = today); onSave(draft) }
                    }
                }, modifier = Modifier.fillMaxWidth()) { Text("Save blocking settings") }
                validation?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            } }
    }
}

private fun launcherApps(pm: PackageManager, ownPackage: String): List<BlockingApp> {
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    @Suppress("DEPRECATION")
    return pm.queryIntentActivities(intent, PackageManager.MATCH_ALL).mapNotNull { info ->
        val pkg = info.activityInfo?.packageName ?: return@mapNotNull null
        if (pkg == ownPackage || pkg in setOf("com.android.settings", "com.android.systemui", "com.android.phone", "com.google.android.permissioncontroller", "com.android.permissioncontroller")) return@mapNotNull null
        BlockingApp(pkg, info.loadLabel(pm).toString())
    }.distinctBy { it.packageName }.sortedBy { it.name.lowercase() }
}
