package com.steady.accountability.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import org.json.JSONArray
import org.json.JSONObject
import java.nio.ByteBuffer
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

object AccountArchive {
    fun exportCsv(tasks: List<TaskEntity>, checkIns: List<DailyCheckInEntity>): ByteArray {
        val output = StringBuilder("type,date,title,notes,priorities,progress,reflection,outcome\r\n")
        tasks.filter { it.deletedAt == null }.forEach { task ->
            output.append(listOf("task", task.scheduledDate, task.title, task.notes.orEmpty(), "", "", "", task.status).joinToString(",") { csv(it) }).append("\r\n")
        }
        checkIns.filter { it.deletedAt == null }.forEach { checkIn ->
            output.append(listOf("check-in", checkIn.date, "", "", checkIn.prioritiesText, checkIn.progressNote.orEmpty(), checkIn.eveningReflection.orEmpty(), checkIn.dayOutcome.orEmpty()).joinToString(",") { csv(it) }).append("\r\n")
        }
        return output.toString().toByteArray(Charsets.UTF_8)
    }

    fun encryptedSnapshot(tasks: List<TaskEntity>, checkIns: List<DailyCheckInEntity>): ByteArray {
        val payload = JSONObject().put("format", "steady-local-snapshot-v1")
            .put("tasks", JSONArray().also { array -> tasks.filter { it.deletedAt == null }.forEach { task ->
                array.put(JSONObject().put("id", task.id).put("title", task.title).put("notes", task.notes)
                    .put("scheduledDate", task.scheduledDate).put("targetTime", task.targetTime).put("status", task.status)
                    .put("createdAt", task.createdAt).put("updatedAt", task.updatedAt))
            } })
            .put("checkIns", JSONArray().also { array -> checkIns.filter { it.deletedAt == null }.forEach { checkIn ->
                array.put(JSONObject().put("date", checkIn.date).put("morningPlan", checkIn.morningPlan)
                    .put("eveningReflection", checkIn.eveningReflection).put("priorities", checkIn.prioritiesText)
                    .put("progressNote", checkIn.progressNote).put("dayOutcome", checkIn.dayOutcome)
                    .put("createdAt", checkIn.createdAt).put("updatedAt", checkIn.updatedAt))
            } }).toString().toByteArray(Charsets.UTF_8)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, encryptionKey())
        val encrypted = cipher.doFinal(payload)
        return ByteBuffer.allocate(1 + cipher.iv.size + encrypted.size)
            .put(cipher.iv.size.toByte()).put(cipher.iv).put(encrypted).array()
    }

    private fun encryptionKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build())
            generateKey()
        }
    }

    private fun csv(value: String): String = "\"${value.replace("\"", "\"\"").replace("\r", " ").replace("\n", " ")}\""
    private const val KEY_ALIAS = "steady-local-backup-v1"
}
