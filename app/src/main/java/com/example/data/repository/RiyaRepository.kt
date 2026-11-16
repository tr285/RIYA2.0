package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.firestore.OperationType
import com.example.data.firestore.handleFirestoreError
import com.example.data.model.AutonomousTaskRun
import com.example.data.model.ChatMessage
import com.example.data.model.MemoryFact
import com.example.data.model.PermissionRule
import com.example.data.model.PersonalNote
import com.example.data.model.PersonalTask
import com.example.data.model.ReminderItem
import com.example.data.model.SecurityAuditEntry
import com.example.data.model.UserProfile
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

class RiyaRepository(private val db: FirebaseFirestore) {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val auth get() = Firebase.auth

    private fun userDoc(userId: String) = db.collection("users").document(userId)

    private fun <T> Flow<T>.logAndRethrow(
        userId: String,
        operationType: OperationType,
        path: String
    ): Flow<T> = catch { error ->
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val exceptionToLog = firestoreError ?: (error as? Exception)
        if (exceptionToLog != null && auth.currentUser?.uid == userId) {
            handleFirestoreError(exceptionToLog, operationType, path)
        }
        throw error
    }

    // -------------------------------------------------------------------------
    // USER PROFILE & PREFERENCES
    // -------------------------------------------------------------------------

    fun observeUserProfile(userId: String): Flow<UserProfile?> {
        val docRef = userDoc(userId)
        return docRef.snapshots()
            .map { snap ->
                snap.toObject(
                    UserProfile::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }
            .logAndRethrow(userId, OperationType.GET, docRef.path)
    }

    suspend fun ensureUserProfile(
        userId: String,
        displayName: String,
        email: String?
    ): Result<UserProfile> {
        val docRef = userDoc(userId)
        return try {
            val existing = docRef.get().await()
            if (existing.exists()) {
                val profile = existing.toObject(
                    UserProfile::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ) ?: UserProfile(id = userId, userId = userId, displayName = displayName, email = email)
                Result.success(profile)
            } else {
                val safeName = displayName.ifBlank { "RIYA User" }.take(120)
                val payload = mutableMapOf<String, Any>(
                    "userId" to userId,
                    "displayName" to safeName,
                    "themeMode" to "dark",
                    "defaultStartupMode" to "text",
                    "voiceLanguage" to "en",
                    "responseStyle" to "friendly",
                    "waveformAnimationsEnabled" to true,
                    "autoMemoryRecallEnabled" to true,
                    "voiceResponsesEnabled" to true,
                    "preferredModel" to "gemini-3.8-flash",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (!email.isNullOrBlank()) {
                    payload["email"] = email.take(200)
                }
                docRef.set(payload).await()
                val created = docRef.get().await().toObject(
                    UserProfile::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ) ?: UserProfile(id = userId, userId = userId, displayName = safeName, email = email)
                Result.success(created)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun updatePreferences(
        userId: String,
        themeMode: String? = null,
        defaultStartupMode: String? = null,
        voiceLanguage: String? = null,
        responseStyle: String? = null,
        waveformAnimationsEnabled: Boolean? = null,
        autoMemoryRecallEnabled: Boolean? = null,
        voiceResponsesEnabled: Boolean? = null,
        preferredModel: String? = null
    ): Result<Unit> {
        val docRef = userDoc(userId)
        val updates = mutableMapOf<String, Any>("updatedAt" to FieldValue.serverTimestamp())
        if (themeMode != null) updates["themeMode"] = themeMode
        if (defaultStartupMode != null) updates["defaultStartupMode"] = defaultStartupMode
        if (voiceLanguage != null) updates["voiceLanguage"] = voiceLanguage
        if (responseStyle != null) updates["responseStyle"] = responseStyle
        if (waveformAnimationsEnabled != null) updates["waveformAnimationsEnabled"] = waveformAnimationsEnabled
        if (autoMemoryRecallEnabled != null) updates["autoMemoryRecallEnabled"] = autoMemoryRecallEnabled
        if (voiceResponsesEnabled != null) updates["voiceResponsesEnabled"] = voiceResponsesEnabled
        if (preferredModel != null) updates["preferredModel"] = preferredModel

        return try {
            docRef.update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    // -------------------------------------------------------------------------
    // CONVERSATION MESSAGES
    // -------------------------------------------------------------------------

    fun observeMessages(userId: String): Flow<List<ChatMessage>> {
        val col = userDoc(userId).collection("messages")
        return col.orderBy("createdAt", Query.Direction.ASCENDING)
            .limitToLast(100)
            .snapshots()
            .map { snap ->
                snap.toObjects(
                    ChatMessage::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }
            .logAndRethrow(userId, OperationType.LIST, col.path)
    }

    suspend fun addMessage(
        userId: String,
        sessionId: String,
        role: String,
        text: String,
        latencyMs: Long = 0L
    ): Result<String> {
        val id = "msg_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val docRef = userDoc(userId).collection("messages").document(id)
        val payload = mapOf(
            "userId" to userId,
            "sessionId" to sessionId.take(128),
            "role" to role,
            "text" to text.take(20000),
            "latencyMs" to latencyMs.coerceIn(0L, 600_000L),
            "createdAt" to FieldValue.serverTimestamp()
        )
        return try {
            docRef.set(payload).await()
            Result.success(id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun clearMessages(userId: String): Result<Unit> {
        val col = userDoc(userId).collection("messages")
        return try {
            val snap = col.get().await()
            val batch = db.batch()
            snap.documents.forEach { batch.delete(it.reference) }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, col.path)
            Result.failure(e)
        }
    }

    // -------------------------------------------------------------------------
    // PERSISTENT MEMORIES
    // -------------------------------------------------------------------------

    fun observeMemories(userId: String): Flow<List<MemoryFact>> {
        val col = userDoc(userId).collection("memories")
        return col.orderBy("updatedAt", Query.Direction.DESCENDING)
            .snapshots()
            .map { snap ->
                snap.toObjects(
                    MemoryFact::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }
            .logAndRethrow(userId, OperationType.LIST, col.path)
    }

    suspend fun saveMemory(
        userId: String,
        category: String,
        key: String,
        value: String
    ): Result<String> {
        val id = "mem_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val docRef = userDoc(userId).collection("memories").document(id)
        val payload = mapOf(
            "userId" to userId,
            "category" to category,
            "key" to key.trim().take(160),
            "value" to value.trim().take(4000),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        return try {
            docRef.set(payload).await()
            Result.success(id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteMemory(userId: String, memoryId: String): Result<Unit> {
        val docRef = userDoc(userId).collection("memories").document(memoryId)
        return try {
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun clearAllMemories(userId: String): Result<Unit> {
        val col = userDoc(userId).collection("memories")
        return try {
            val snap = col.get().await()
            val batch = db.batch()
            snap.documents.forEach { batch.delete(it.reference) }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, col.path)
            Result.failure(e)
        }
    }

    // -------------------------------------------------------------------------
    // PERSONAL TASKS, REMINDERS, NOTES
    // -------------------------------------------------------------------------

    fun observeTasks(userId: String): Flow<List<PersonalTask>> {
        val col = userDoc(userId).collection("tasks")
        return col.orderBy("createdAt", Query.Direction.DESCENDING)
            .snapshots()
            .map { snap ->
                snap.toObjects(
                    PersonalTask::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }
            .logAndRethrow(userId, OperationType.LIST, col.path)
    }

    suspend fun createTask(
        userId: String,
        title: String,
        description: String = "",
        priority: String = "medium",
        category: String = "general",
        dueDateLabel: String = "Today"
    ): Result<String> {
        val id = "tsk_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val docRef = userDoc(userId).collection("tasks").document(id)
        val payload = mapOf(
            "userId" to userId,
            "title" to title.trim().take(200),
            "description" to description.trim().take(4000),
            "priority" to priority,
            "status" to "pending",
            "category" to category.ifBlank { "general" }.take(60),
            "dueDateLabel" to dueDateLabel.take(80),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        return try {
            docRef.set(payload).await()
            Result.success(id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun updateTaskStatus(userId: String, taskId: String, status: String): Result<Unit> {
        val docRef = userDoc(userId).collection("tasks").document(taskId)
        return try {
            docRef.update(
                mapOf(
                    "status" to status,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteTask(userId: String, taskId: String): Result<Unit> {
        val docRef = userDoc(userId).collection("tasks").document(taskId)
        return try {
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            Result.failure(e)
        }
    }

    fun observeReminders(userId: String): Flow<List<ReminderItem>> {
        val col = userDoc(userId).collection("reminders")
        return col.orderBy("createdAt", Query.Direction.DESCENDING)
            .snapshots()
            .map { snap ->
                snap.toObjects(
                    ReminderItem::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }
            .logAndRethrow(userId, OperationType.LIST, col.path)
    }

    suspend fun createReminder(
        userId: String,
        title: String,
        triggerLabel: String,
        recurringRule: String = "none"
    ): Result<String> {
        val id = "rem_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val docRef = userDoc(userId).collection("reminders").document(id)
        val payload = mapOf(
            "userId" to userId,
            "title" to title.trim().take(200),
            "triggerLabel" to triggerLabel.ifBlank { "Today" }.take(100),
            "recurringRule" to recurringRule,
            "status" to "pending",
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        return try {
            docRef.set(payload).await()
            Result.success(id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun updateReminderStatus(userId: String, reminderId: String, status: String): Result<Unit> {
        val docRef = userDoc(userId).collection("reminders").document(reminderId)
        return try {
            docRef.update(
                mapOf(
                    "status" to status,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteReminder(userId: String, reminderId: String): Result<Unit> {
        val docRef = userDoc(userId).collection("reminders").document(reminderId)
        return try {
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            Result.failure(e)
        }
    }

    fun observeNotes(userId: String): Flow<List<PersonalNote>> {
        val col = userDoc(userId).collection("notes")
        return col.orderBy("updatedAt", Query.Direction.DESCENDING)
            .snapshots()
            .map { snap ->
                snap.toObjects(
                    PersonalNote::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }
            .logAndRethrow(userId, OperationType.LIST, col.path)
    }

    suspend fun createNote(
        userId: String,
        title: String,
        content: String,
        category: String = "general"
    ): Result<String> {
        val id = "note_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val docRef = userDoc(userId).collection("notes").document(id)
        val payload = mapOf(
            "userId" to userId,
            "title" to title.trim().take(200),
            "content" to content.trim().take(12000),
            "category" to category.ifBlank { "general" }.take(60),
            "pinned" to false,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        return try {
            docRef.set(payload).await()
            Result.success(id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun toggleNotePin(userId: String, noteId: String, pinned: Boolean): Result<Unit> {
        val docRef = userDoc(userId).collection("notes").document(noteId)
        return try {
            docRef.update(
                mapOf(
                    "pinned" to pinned,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteNote(userId: String, noteId: String): Result<Unit> {
        val docRef = userDoc(userId).collection("notes").document(noteId)
        return try {
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            Result.failure(e)
        }
    }

    // -------------------------------------------------------------------------
    // AUTONOMOUS TASK RUNS
    // -------------------------------------------------------------------------

    fun observeAutonomousRuns(userId: String): Flow<List<AutonomousTaskRun>> {
        val col = userDoc(userId).collection("autonomous_runs")
        return col.orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(30)
            .snapshots()
            .map { snap ->
                snap.toObjects(
                    AutonomousTaskRun::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }
            .logAndRethrow(userId, OperationType.LIST, col.path)
    }

    suspend fun createAutonomousRun(
        userId: String,
        goal: String,
        status: String,
        stepsSummary: String,
        stepsCompleted: Long,
        stepsTotal: Long
    ): Result<String> {
        val id = "run_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val docRef = userDoc(userId).collection("autonomous_runs").document(id)
        val payload = mapOf(
            "userId" to userId,
            "goal" to goal.trim().take(1000),
            "status" to status,
            "stepsSummary" to stepsSummary.take(8000),
            "stepsCompleted" to stepsCompleted.coerceIn(0L, 50L),
            "stepsTotal" to stepsTotal.coerceIn(0L, 50L),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        return try {
            docRef.set(payload).await()
            Result.success(id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun updateAutonomousRun(
        userId: String,
        runId: String,
        status: String,
        stepsSummary: String,
        stepsCompleted: Long,
        stepsTotal: Long
    ): Result<Unit> {
        val docRef = userDoc(userId).collection("autonomous_runs").document(runId)
        return try {
            docRef.update(
                mapOf(
                    "status" to status,
                    "stepsSummary" to stepsSummary.take(8000),
                    "stepsCompleted" to stepsCompleted.coerceIn(0L, 50L),
                    "stepsTotal" to stepsTotal.coerceIn(0L, 50L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    // -------------------------------------------------------------------------
    // PERMISSIONS & SECURITY AUDIT LOG
    // -------------------------------------------------------------------------

    fun observePermissionRules(userId: String): Flow<List<PermissionRule>> {
        val col = userDoc(userId).collection("permission_rules")
        return col.orderBy("updatedAt", Query.Direction.DESCENDING)
            .snapshots()
            .map { snap ->
                snap.toObjects(
                    PermissionRule::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }
            .logAndRethrow(userId, OperationType.LIST, col.path)
    }

    suspend fun savePermissionRule(
        userId: String,
        permissionKey: String,
        decision: String,
        lifetime: String,
        reason: String
    ): Result<String> {
        val safeId = "rule_" + permissionKey.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
        val docRef = userDoc(userId).collection("permission_rules").document(safeId)
        return try {
            val snap = docRef.get().await()
            if (snap.exists()) {
                docRef.update(
                    mapOf(
                        "permissionKey" to permissionKey.take(100),
                        "decision" to decision,
                        "lifetime" to lifetime,
                        "reason" to reason.take(500),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                docRef.set(
                    mapOf(
                        "userId" to userId,
                        "permissionKey" to permissionKey.take(100),
                        "decision" to decision,
                        "lifetime" to lifetime,
                        "reason" to reason.take(500),
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            recordAuditEntry(
                userId = userId,
                permissionKey = permissionKey,
                decision = decision,
                reason = "Rule updated ($lifetime): $reason"
            )
            Result.success(safeId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun deletePermissionRule(userId: String, ruleId: String): Result<Unit> {
        val docRef = userDoc(userId).collection("permission_rules").document(ruleId)
        return try {
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            Result.failure(e)
        }
    }

    fun observeAuditLogs(userId: String): Flow<List<SecurityAuditEntry>> {
        val col = userDoc(userId).collection("audit_logs")
        return col.orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(50)
            .snapshots()
            .map { snap ->
                snap.toObjects(
                    SecurityAuditEntry::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }
            .logAndRethrow(userId, OperationType.LIST, col.path)
    }

    suspend fun recordAuditEntry(
        userId: String,
        permissionKey: String,
        decision: String,
        reason: String
    ): Result<String> {
        val id = "aud_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val docRef = userDoc(userId).collection("audit_logs").document(id)
        val payload = mapOf(
            "userId" to userId,
            "permissionKey" to permissionKey.take(100),
            "decision" to decision,
            "reason" to reason.take(500),
            "createdAt" to FieldValue.serverTimestamp()
        )
        return try {
            docRef.set(payload).await()
            Result.success(id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }
}
