package com.steady.accountability

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.size
import androidx.compose.ui.viewinterop.AndroidView
import android.widget.ImageView
import com.steady.accountability.data.UsageAppSettingEntity
import com.steady.accountability.usage.UsageAppRow
import com.steady.accountability.usage.UsageDashboardState
import com.steady.accountability.usage.UsagePermissionState
import com.steady.accountability.usage.UsageRules

private val UsageSage = Color(0xFF244237)
private val UsageInk = Color(0xFF1B1C1A)

@Composable
fun UsageDashboardScreen(
    enabled: Boolean,
    permission: UsagePermissionState,
    apps: List<UsageAppSettingEntity>,
    rows: List<UsageAppRow>,
    onEnabledChange: (Boolean) -> Unit,
    onOpenSettings: () -> Unit,
    onRefresh: () -> Unit,
    onAppChange: (UsageAppSettingEntity) -> Unit,
) {
    var showAppPicker by remember { mutableStateOf(false) }
    val selected = apps.count { it.included }
    val state = UsageRules.dashboardState(enabled, permission, selected, rows.any { it.totalMillis > 0 })
    Text("YOUR SPACE · DIGITAL BALANCE", style = MaterialTheme.typography.labelSmall, color = Color(0xFF414845), fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
    Text("Mindful app limits", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold, color = UsageInk, modifier = Modifier.padding(top = 5.dp))
    Text("Optional, on-device totals for apps you choose.", color = Color(0xFF414845), modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().heightIn(max = 620.dp)) {
        item {
            Surface(color = Color.White, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Your privacy", fontWeight = FontWeight.SemiBold, color = UsageSage)
                    Text("Android requires Usage Access in system Settings. Steady checks daily totals only while this screen is open and only for apps you select. Totals and limits stay on this phone and are never synced. No background monitoring, content reading, or app blocking.", style = MaterialTheme.typography.bodySmall, color = Color(0xFF68736F))
                    Text("Totals can be delayed or approximate depending on Android and your device.", style = MaterialTheme.typography.bodySmall, color = Color(0xFF68736F))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Enable usage totals", modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
                        Switch(checked = enabled, onCheckedChange = onEnabledChange)
                    }
                    if (enabled && permission != UsagePermissionState.GRANTED) {
                        Text(permissionMessage(permission), style = MaterialTheme.typography.bodySmall, color = Color(0xFF68736F))
                        Button(onClick = onOpenSettings) { Text("Open Usage Access settings") }
                    }
                    if (enabled && permission == UsagePermissionState.GRANTED) {
                        Button(onClick = onRefresh) { Text("Refresh today’s totals") }
                    }
                }
            }
        }
        if (!enabled) {
            item { Text("Usage totals are off. Your saved app choices and limits stay on this device; saved totals are erased when you turn tracking off.", color = Color(0xFF68736F)) }
        } else {
            when (state) {
                UsageDashboardState.NEEDS_ACCESS, UsageDashboardState.RESTRICTED, UsageDashboardState.UNAVAILABLE -> Unit
                UsageDashboardState.EMPTY_SELECTION -> item { Text("Add an app to see its daily total or set a warning threshold.", color = Color(0xFF68736F)) }
                UsageDashboardState.EMPTY -> item { Text("No usage totals are available for your selected apps yet today. Android may update them later.", color = Color(0xFF68736F)) }
                UsageDashboardState.READY -> {
                    item { Text("TODAY’S TOTALS", style = MaterialTheme.typography.labelMedium, color = UsageSage, fontWeight = FontWeight.Bold) }
                    items(rows.filter { it.setting.included }, key = { "usage-${it.setting.packageName}" }) { row ->
                        Surface(color = Color.White, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    AppIcon(row.setting.packageName, row.setting.displayName)
                                    Text(row.setting.displayName, modifier = Modifier.padding(start = 10.dp), fontWeight = FontWeight.SemiBold, color = UsageInk)
                                }
                                Text(formatDuration(row.totalMillis), color = Color(0xFF68736F))
                                if (UsageRules.thresholdReached(row.totalMillis, row.setting.dailyLimitMinutes)) {
                                    Text("You’ve reached the daily limit you set. Steady won’t block this app.", color = UsageSage, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }
                }
                UsageDashboardState.DISABLED -> Unit
            }
            if (permission == UsagePermissionState.GRANTED) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text("YOUR APPS", style = MaterialTheme.typography.labelMedium, color = UsageSage, fontWeight = FontWeight.Bold)
                            Text("$selected selected", style = MaterialTheme.typography.bodySmall, color = Color(0xFF68736F))
                        }
                        Button(onClick = { showAppPicker = true }) { Text("Add app") }
                    }
                }
                items(apps.filter { it.included }, key = { "settings-${it.packageName}" }) { app ->
                    Surface(color = Color.White, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                AppIcon(app.packageName, app.displayName)
                                Text(app.displayName, modifier = Modifier.weight(1f).padding(start = 10.dp), color = UsageInk, fontWeight = FontWeight.Medium)
                                Checkbox(checked = true, onCheckedChange = { onAppChange(app.copy(included = it)) })
                            }
                            OutlinedTextField(
                                value = app.dailyLimitMinutes?.toString().orEmpty(),
                                onValueChange = { value ->
                                    val minutes = value.filter(Char::isDigit).take(4).toIntOrNull()?.takeIf { it in 1..1440 }
                                    onAppChange(app.copy(dailyLimitMinutes = minutes, included = app.included || minutes != null))
                                },
                                label = { Text("Daily warning limit in minutes") },
                                supportingText = { Text("Leave blank for no limit") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(4.dp)) }
    }
    if (showAppPicker) {
        AppPickerDialog(
            apps = apps,
            onDismiss = { showAppPicker = false },
            onAppChange = onAppChange,
        )
    }
}

@Composable
private fun AppPickerDialog(
    apps: List<UsageAppSettingEntity>,
    onDismiss: () -> Unit,
    onAppChange: (UsageAppSettingEntity) -> Unit,
) {
    var search by remember { mutableStateOf("") }
    val filtered = apps.filter { it.displayName.contains(search.trim(), ignoreCase = true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add apps") },
        text = {
            Column {
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    label = { Text("Search apps") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (filtered.isEmpty()) {
                    Text("No apps match that search.", modifier = Modifier.padding(top = 16.dp), color = Color(0xFF68736F))
                } else {
                    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp).padding(top = 8.dp)) {
                        items(filtered, key = { it.packageName }) { app ->
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                Checkbox(
                                    checked = app.included,
                                    onCheckedChange = { checked -> onAppChange(app.copy(included = checked)) },
                                )
                                AppIcon(app.packageName, app.displayName)
                                Text(app.displayName, modifier = Modifier.weight(1f).padding(start = 10.dp), color = UsageInk)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}

@Composable
private fun AppIcon(packageName: String, displayName: String) {
    val context = LocalContext.current
    val appIcon = remember(packageName, context) {
        runCatching { context.packageManager.getApplicationIcon(packageName) }
            .getOrElse { context.getDrawable(android.R.drawable.sym_def_app_icon) }
    }
    AndroidView(
        factory = { viewContext ->
            ImageView(viewContext).apply {
                contentDescription = "$displayName icon"
                scaleType = ImageView.ScaleType.FIT_CENTER
            }
        },
        update = { imageView -> imageView.setImageDrawable(appIcon) },
        modifier = Modifier.size(36.dp),
    )
}

private fun permissionMessage(state: UsagePermissionState): String = when (state) {
    UsagePermissionState.GRANTED -> "Usage Access is on."
    UsagePermissionState.DENIED -> "Usage Access is not enabled yet. You can enable it in Android Settings, or leave usage totals off."
    UsagePermissionState.RESTRICTED -> "Android reports this access is restricted. Check system Settings or your device administrator."
    UsagePermissionState.UNAVAILABLE -> "Usage statistics are unavailable on this device right now."
}

private fun formatDuration(millis: Long): String {
    val minutes = millis.coerceAtLeast(0L) / 60_000L
    val hours = minutes / 60
    val remaining = minutes % 60
    return if (hours > 0) "${hours}h ${remaining}m today" else "${remaining}m today"
}
