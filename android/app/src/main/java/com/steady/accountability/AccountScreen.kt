package com.steady.accountability

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val AccountForest = Color(0xFF0D2C22)
private val AccountSage = Color(0xFF43664D)
private val AccountMint = Color(0xFFC2E9C9)
private val AccountStone = Color(0xFFF4F4F0)
private val AccountText = Color(0xFF1B1C1A)
private val AccountMuted = Color(0xFF414845)

@Composable
fun AccountScreen(
    configured: Boolean,
    syncState: String,
    lastSync: String,
    recoveryPending: Boolean,
    account: String,
    displayName: String,
    message: String,
    email: String,
    onEmail: (String) -> Unit,
    password: String,
    onPassword: (String) -> Unit,
    newName: String,
    onNewName: (String) -> Unit,
    onSignIn: () -> Unit,
    onGoogleSignIn: () -> Unit,
    onLinkGoogle: () -> Unit,
    onPasswordReset: () -> Unit,
    onRecoveryPassword: (String) -> Unit,
    onSignUp: (String) -> Unit,
    onSaveName: (String) -> Unit,
    onSignOut: () -> Unit,
    onSync: () -> Unit,
    wifiOnly: Boolean,
    onWifiOnlyChange: (Boolean) -> Unit,
    syncOnOpen: Boolean,
    onSyncOnOpenChange: (Boolean) -> Unit,
    localStorageSummary: String,
    onExport: () -> Unit,
    onBackup: () -> Unit,
    onPasswordUpdate: (String) -> Unit,
) {
    var creatingAccount by remember { mutableStateOf(false) }
    var showNameEditor by remember { mutableStateOf(false) }
    var showPasswordDialog by remember { mutableStateOf(false) }
    var showDevices by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }
    var showRecoveryPassword by remember(recoveryPending) { mutableStateOf(recoveryPending) }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth().heightIn(max = 620.dp)) {
        item {
            Text("● YOUR SPACE · SANCTUARY PREFERENCES", style = MaterialTheme.typography.labelSmall, color = AccountSage, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
            Text("Account & sync", style = MaterialTheme.typography.headlineMedium, color = AccountForest, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
            Text("Your rhythm stays grounded on this device when you are offline. Cloud sync is calm, intentional, and private.", style = MaterialTheme.typography.bodyMedium, color = AccountMuted, modifier = Modifier.padding(top = 4.dp))
        }
        item {
            AccountCard {
                if (account.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val initials = displayName.trim().split(Regex("\\s+")).filter(String::isNotBlank).take(2).mapNotNull { it.firstOrNull()?.uppercaseChar() }.joinToString("").ifBlank { "S" }
                        Surface(color = AccountForest, shape = RoundedCornerShape(50), modifier = Modifier.size(46.dp)) {
                            androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) { Text(initials, color = Color.White, fontWeight = FontWeight.SemiBold) }
                        }
                        Column(Modifier.weight(1f).padding(start = 10.dp)) {
                            Text(displayName.ifBlank { "Steady member" }, color = AccountText, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                            Text(account, color = AccountMuted, style = MaterialTheme.typography.bodySmall)
                        }
                        Surface(color = AccountStone, shape = RoundedCornerShape(50)) {
                            Text("◇  Steady Care", modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp), color = AccountForest, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    Surface(color = AccountStone, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().padding(top = 14.dp)) {
                        Row(Modifier.padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("◉", color = AccountSage, style = MaterialTheme.typography.titleMedium)
                            Column(Modifier.weight(1f).padding(start = 8.dp)) {
                                Text(if (syncState == "Synced") "Synced & calm" else syncState, color = AccountForest, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
                                Text(if (syncState == "Synced") "Your local plan is up to date" else "Your local plan stays safe on this device", color = AccountMuted, style = MaterialTheme.typography.labelSmall)
                                if (lastSync.isNotBlank()) Text("Last successful sync · $lastSync", color = AccountMuted, style = MaterialTheme.typography.labelSmall)
                            }
                            Button(onClick = onSync, enabled = configured, shape = RoundedCornerShape(50), colors = ButtonDefaults.buttonColors(containerColor = AccountForest), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 5.dp)) { Text("↻  Sync now", style = MaterialTheme.typography.labelMedium) }
                        }
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("♙  Your profile", color = AccountMuted, style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { showNameEditor = true }) { Text("Edit name", color = AccountForest) }
                    }
                } else {
                    Text(if (creatingAccount) "Create your Steady account" else "Welcome back", style = MaterialTheme.typography.titleLarge, color = AccountForest, fontWeight = FontWeight.SemiBold)
                    Text(if (creatingAccount) "Choose the name you’d like to see in Steady." else "Sign in to sync your tasks and check-ins across devices.", style = MaterialTheme.typography.bodySmall, color = AccountMuted)
                    if (!configured) Text("Supabase is not configured. Your local planner is ready; add the Android Supabase settings to enable accounts.", color = AccountMuted, style = MaterialTheme.typography.bodySmall)
                    if (creatingAccount) {
                        OutlinedTextField(newName, onNewName, label = { Text("Your name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    }
                    OutlinedTextField(email, onEmail, label = { Text("Email") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(password, onPassword, label = { Text("Password") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                    Button(
                        onClick = { if (creatingAccount) onSignUp(newName.trim()) else onSignIn() },
                        enabled = configured && (!creatingAccount || newName.isNotBlank()),
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = AccountForest),
                    ) { Text(if (creatingAccount) "Create account" else "Sign in") }
                    TextButton(onClick = { creatingAccount = !creatingAccount }) {
                        Text(if (creatingAccount) "Already have an account? Sign in" else "New to Steady? Create an account", color = AccountForest)
                    }
                    if (!creatingAccount) {
                        TextButton(onClick = onPasswordReset, enabled = configured) { Text("Forgot password?", color = AccountForest) }
                        Button(onClick = onGoogleSignIn, enabled = configured, shape = RoundedCornerShape(50), modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = AccountStone, contentColor = AccountForest)) { Text("Continue with Google") }
                    }
                }
                if (message.isNotBlank()) Text(message, color = AccountMuted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
            }
        }
        item {
            AccountCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("▤", color = AccountForest, style = MaterialTheme.typography.titleLarge)
                    Column(Modifier.weight(1f).padding(start = 10.dp)) {
                        Text("Local-first sovereignty", color = AccountText, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                        Text(localStorageSummary, color = AccountMuted, style = MaterialTheme.typography.bodySmall)
                    }
                    Surface(color = AccountMint, shape = RoundedCornerShape(50)) { Text(if (syncState == "Offline") "OFFLINE READY" else "LOCAL FIRST", modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), color = AccountForest, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold) }
                }
                Text("Your tasks, check-ins, and reflections are stored on this device first. You can use Steady offline; sync only runs when you choose or reconnect.", color = AccountMuted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 10.dp, bottom = 8.dp))
                PreferenceRow("⌁", "Sync over Wi-Fi only", "Avoids mobile data and battery use", wifiOnly, onWifiOnlyChange)
                PreferenceRow("☾", "Sync when opening Steady", "Sync on app open or network reconnect", syncOnOpen, onSyncOnOpenChange)
            }
        }
        item {
            AccountCard {
                Text("♧  Mindful data ownership", color = AccountText, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                Text("Your journey belongs solely to you.", color = AccountMuted, style = MaterialTheme.typography.bodySmall)
                ActionRow("▧", "Export reflections & rhythms", "Save a CSV copy of tasks and check-ins", "→", onExport)
                ActionRow("♧", "Create offline backup snapshot", "Encrypted local snapshot file", "↓", onBackup)
                Text("The snapshot uses this phone’s Android Keystore key and is readable only by this app installation.", color = AccountMuted, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
            }
        }
        item {
            Surface(color = AccountStone, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("ACCOUNT HISTORY REFERENCE", color = AccountMuted, style = MaterialTheme.typography.labelSmall, letterSpacing = 0.8.sp)
                        Text("Current", color = AccountSage, style = MaterialTheme.typography.labelSmall)
                    }
                    ActionRow("▤", "Legacy sync profile", "Current local-first account history", "→", { showHistory = true })
                }
            }
        }
        item {
            AccountCard {
                Text("Security & Sanctuary", color = AccountText, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                ActionRow("⚿", "Update account password", "Choose a new sign-in password", "›", { showPasswordDialog = true }, enabled = account.isNotBlank() && configured)
                ActionRow("G", "Link Google account", "Add Google as a sign-in method for this account", "›", onLinkGoogle, enabled = account.isNotBlank() && configured)
                ActionRow("▣", "Active connected devices", "This device · other sessions are private", "›", { showDevices = true })
                Button(onClick = onSignOut, enabled = account.isNotBlank(), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().padding(top = 8.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFE7E2), contentColor = AccountText)) { Text("⇥  Sign out of Steady") }
                Text("Signing out keeps all local device data intact. Take all the time you need.", color = AccountMuted, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
            }
        }
        item { Spacer(Modifier.height(6.dp)) }
    }

    if (showNameEditor) {
        var editedName by remember(displayName) { mutableStateOf(displayName) }
        AlertDialog(
            onDismissRequest = { showNameEditor = false },
            title = { Text("Edit your name") },
            text = { OutlinedTextField(editedName, { editedName = it }, label = { Text("Your name") }, singleLine = true) },
            confirmButton = { TextButton(onClick = { if (editedName.isNotBlank()) { onSaveName(editedName.trim()); showNameEditor = false } }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { showNameEditor = false }) { Text("Cancel") } },
        )
    }
    if (showPasswordDialog) {
        var firstPassword by remember { mutableStateOf("") }
        var secondPassword by remember { mutableStateOf("") }
        var passwordError by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showPasswordDialog = false },
            title = { Text("Update password") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(firstPassword, { firstPassword = it; passwordError = "" }, label = { Text("New password") }, singleLine = true, visualTransformation = PasswordVisualTransformation())
                    OutlinedTextField(secondPassword, { secondPassword = it; passwordError = "" }, label = { Text("Confirm password") }, singleLine = true, visualTransformation = PasswordVisualTransformation())
                    if (passwordError.isNotBlank()) Text(passwordError, color = MaterialTheme.colorScheme.error)
                }
            },
            confirmButton = { TextButton(onClick = {
                passwordError = when { firstPassword.length < 8 -> "Use at least 8 characters."; firstPassword != secondPassword -> "The passwords do not match."; else -> "" }
                if (passwordError.isBlank()) { onPasswordUpdate(firstPassword); showPasswordDialog = false }
            }) { Text("Save password") } },
            dismissButton = { TextButton(onClick = { showPasswordDialog = false }) { Text("Cancel") } },
        )
    }
    if (showRecoveryPassword) {
        var firstPassword by remember { mutableStateOf("") }
        var secondPassword by remember { mutableStateOf("") }
        var error by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { if (!recoveryPending) showRecoveryPassword = false },
            title = { Text("Choose a new password") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(firstPassword, { firstPassword = it; error = "" }, label = { Text("New password") }, singleLine = true, visualTransformation = PasswordVisualTransformation())
                OutlinedTextField(secondPassword, { secondPassword = it; error = "" }, label = { Text("Confirm password") }, singleLine = true, visualTransformation = PasswordVisualTransformation())
                if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
            } },
            confirmButton = { TextButton(onClick = { error = when { firstPassword.length < 8 -> "Use at least 8 characters."; firstPassword != secondPassword -> "The passwords do not match."; else -> "" }; if (error.isBlank()) { onRecoveryPassword(firstPassword); showRecoveryPassword = false } }) { Text("Save password") } },
            dismissButton = { if (!recoveryPending) TextButton(onClick = { showRecoveryPassword = false }) { Text("Cancel") } },
        )
    }
    if (showDevices) AlertDialog(
        onDismissRequest = { showDevices = false },
        title = { Text("Connected devices") },
        text = { Text("This device is signed in as $account. Supabase does not expose a full device-session list to this app, so other active sessions cannot be shown here. Signing out here ends this device’s session.") },
        confirmButton = { TextButton(onClick = { showDevices = false }) { Text("Done") } },
    )
    if (showHistory) AlertDialog(
        onDismissRequest = { showHistory = false },
        title = { Text("Account history") },
        text = { Text("Steady uses your current local-first profile and the configured Supabase account. No separate legacy sync profile was found in this app installation.") },
        confirmButton = { TextButton(onClick = { showHistory = false }) { Text("Close") } },
    )
}

@Composable
private fun AccountCard(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Surface(color = Color.White, shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

@Composable
private fun PreferenceRow(icon: String, title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Surface(color = AccountStone, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(icon, color = AccountSage, style = MaterialTheme.typography.titleMedium)
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                Text(title, color = AccountText, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Text(subtitle, color = AccountMuted, style = MaterialTheme.typography.labelSmall)
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

@Composable
private fun ActionRow(icon: String, title: String, subtitle: String, trailing: String, onClick: () -> Unit, enabled: Boolean = true) {
    Surface(onClick = onClick, enabled = enabled, color = AccountStone, shape = RoundedCornerShape(15.dp), modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(color = Color.White, shape = RoundedCornerShape(10.dp), modifier = Modifier.size(32.dp)) {
                androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) { Text(icon, color = AccountForest, style = MaterialTheme.typography.titleSmall) }
            }
            Column(Modifier.weight(1f).padding(start = 9.dp)) {
                Text(title, color = AccountText, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Text(subtitle, color = AccountMuted, style = MaterialTheme.typography.labelSmall)
            }
            Text(trailing, color = AccountMuted, style = MaterialTheme.typography.titleMedium)
        }
    }
}
