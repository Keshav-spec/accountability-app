package com.steady.accountability.data

import com.steady.accountability.BuildConfig
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.time.Instant

object SupabaseSync {
    val configured get() = BuildConfig.SUPABASE_URL.isNotBlank() && BuildConfig.SUPABASE_PUBLISHABLE_KEY.isNotBlank()
    val client by lazy {
        check(configured)
        createSupabaseClient(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_PUBLISHABLE_KEY) {
            install(Auth) { host = "login-callback"; scheme = "steady" }
            install(Postgrest)
        }
    }
    suspend fun signIn(email: String, password: String) = client.auth.signInWith(Email) { this.email = email; this.password = password }
    suspend fun signUp(email: String, password: String, displayName: String) = client.auth.signUpWith(Email) {
        this.email = email
        this.password = password
        data = buildJsonObject { put("display_name", displayName) }
    }
    suspend fun profileName(): String? {
        val id = userId() ?: return null
        val profile = client.from("user_profiles").select { filter { eq("user_id", id) } }.decodeList<RemoteUserProfile>().firstOrNull()?.displayName
        return profile?.takeIf(String::isNotBlank)
            ?: client.auth.currentUserOrNull()?.userMetadata?.get("display_name")?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotBlank)
    }
    suspend fun saveProfileName(displayName: String) {
        val id = userId() ?: error("Sign in to save your account name.")
        client.auth.updateUser { data { put("display_name", displayName) } }
        client.from("user_profiles").upsert(RemoteUserProfile(id, displayName)) { onConflict = "user_id" }
    }
    suspend fun updatePassword(password: String) { client.auth.updateUser { this.password = password } }
    suspend fun signOut() = client.auth.signOut()
    fun userId(): String? = client.auth.currentUserOrNull()?.id
    fun currentEmail(): String? = client.auth.currentUserOrNull()?.email

    suspend fun sync(tasks: TaskDao, checkIns: CheckInDao, saveOwner: (String) -> Unit, currentOwner: String?): String {
        val userId = userId() ?: return "Sign in to sync"
        if (currentOwner != null && currentOwner != userId) return "Local data belongs to another account"
        saveOwner(userId)
        val remoteTasks = client.from("tasks").select().decodeList<RemoteTask>()
        val remoteCheckIns = client.from("daily_check_ins").select().decodeList<RemoteCheckIn>()
        val taskById = remoteTasks.associateBy { it.id }
        val checkInByDate = remoteCheckIns.associateBy { it.date }
        val localTasks = tasks.allForSync()
        for (local in localTasks) {
            val remote = taskById[local.id]
            if (SyncRules.localWins(local.updatedAt, remote?.updatedAt)) {
                client.from("tasks").upsert(local.toRemote(userId)) { onConflict = "id" }
                tasks.markSynced(local.id)
            } else if (remote != null) tasks.upsert(remote.toLocal())
        }
        val localCheckIns = checkIns.allForSync()
        for (local in localCheckIns) {
            val remote = checkInByDate[local.date]
            if (SyncRules.localWins(local.updatedAt, remote?.updatedAt)) {
                client.from("daily_check_ins").upsert(local.toRemote(userId)) { onConflict = "user_id,date" }
                checkIns.markSynced(local.date)
            } else if (remote != null) checkIns.saveCheckIn(remote.toLocal())
        }
        for (remote in remoteTasks) if (localTasks.none { it.id == remote.id }) tasks.upsert(remote.toLocal())
        for (remote in remoteCheckIns) if (localCheckIns.none { it.date == remote.date }) checkIns.saveCheckIn(remote.toLocal())
        return "Synced"
    }
}

@Serializable
data class RemoteUserProfile(
    @SerialName("user_id") val userId: String,
    @SerialName("display_name") val displayName: String,
)

object SyncRules {
    // ISO-8601 timestamps are lexically sortable. Remote wins exact ties for deterministic convergence.
    fun localWins(localUpdatedAt: String, remoteUpdatedAt: String?): Boolean = remoteUpdatedAt == null || Instant.parse(localUpdatedAt).isAfter(Instant.parse(remoteUpdatedAt))
    fun shouldQueueUpload(localUpdatedAt: String, remoteUpdatedAt: String?): Boolean = localWins(localUpdatedAt, remoteUpdatedAt)
    fun deletedAtForSync(deletedAt: String?): String? = deletedAt
}

@Serializable
data class RemoteTask(
    val id: String, val title: String, val notes: String? = null,
    @SerialName("scheduled_date") val scheduledDate: String, @SerialName("target_time") val targetTime: String? = null,
    val status: String, @SerialName("created_at") val createdAt: String, @SerialName("updated_at") val updatedAt: String,
    @SerialName("deleted_at") val deletedAt: String? = null, @SerialName("user_id") val userId: String,
)

@Serializable
data class RemoteCheckIn(
    @SerialName("user_id") val userId: String, val date: String, @SerialName("morning_plan") val morningPlan: String? = null,
    val priorities: List<String> = emptyList(), @SerialName("progress_note") val progressNote: String? = null,
    @SerialName("evening_reflection") val eveningReflection: String? = null, @SerialName("day_outcome") val dayOutcome: String? = null,
    @SerialName("created_at") val createdAt: String, @SerialName("updated_at") val updatedAt: String,
    @SerialName("deleted_at") val deletedAt: String? = null,
)

private fun TaskEntity.toRemote(userId: String) = RemoteTask(id, title, notes, scheduledDate, targetTime, status.lowercase(), createdAt, updatedAt, deletedAt, userId)
private fun RemoteTask.toLocal() = TaskEntity(id, title, notes, scheduledDate, targetTime?.take(5), status.uppercase(), createdAt, updatedAt, deletedAt, false)
private fun DailyCheckInEntity.toRemote(userId: String) = RemoteCheckIn(userId, date, morningPlan, prioritiesText.lines().filter(String::isNotBlank), progressNote, eveningReflection, dayOutcome, createdAt, updatedAt, deletedAt)
private fun RemoteCheckIn.toLocal() = DailyCheckInEntity(date, morningPlan, eveningReflection, priorities.joinToString("\n"), progressNote, dayOutcome, createdAt, updatedAt, deletedAt, false)
