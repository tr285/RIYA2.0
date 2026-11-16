package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class RiyaRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun ensureProfileAndMemoryCrud_authenticatedOwner_succeedsAndEmitsFlow() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repo = RiyaRepository(firestore)

        val profileRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.ensureUserProfile(uid, "Alice", ALICE_EMAIL)
        }
        assertTrue(profileRes.isSuccess)

        val uniqueKey = "editor_${UUID.randomUUID().toString().take(8)}"
        val memRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.saveMemory(uid, "preferences", uniqueKey, "Jetpack Compose")
        }
        assertTrue(memRes.isSuccess)
        val memId = memRes.getOrThrow()

        val emittedMemories = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeMemories(uid).first { list -> list.any { it.id == memId } }
        }
        assertTrue(emittedMemories.any { it.id == memId && it.key == uniqueKey })
    }

    @Test
    fun taskReminderNoteAndAutonomousRun_authenticatedOwner_succeeds() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repo = RiyaRepository(firestore)

        val taskRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.createTask(uid, "Ship RIYA Android", "Verify all rules", "high", "work", "Today")
        }
        assertTrue(taskRes.isSuccess)

        val remRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.createReminder(uid, "Standup", "9:30 AM", "weekdays")
        }
        assertTrue(remRes.isSuccess)

        val noteRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.createNote(uid, "Design Notes", "Material 3 dark & light theme", "general")
        }
        assertTrue(noteRes.isSuccess)

        val runId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.createAutonomousRun(uid, "Audit security", "running", "Step 1", 0, 2).getOrThrow()
        }
        val completeRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.updateAutonomousRun(uid, runId, "completed", "Done", 2, 2)
        }
        assertTrue(completeRes.isSuccess)

        val retryAfterTerminal = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.updateAutonomousRun(uid, runId, "running", "Reopen", 1, 2)
        }
        assertTrue(retryAfterTerminal.isFailure)
        val firestoreErr = retryAfterTerminal.exceptionOrNull()?.unwrapFirestoreException()
        assertNotNull(firestoreErr)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreErr?.code)
    }

    @Test
    fun savePermissionRuleAndAuditEntry_auditLogImmutable() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repo = RiyaRepository(firestore)

        val ruleRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.savePermissionRule(uid, "tasks.autonomous", "allow", "session", "Trusted workflow")
        }
        assertTrue(ruleRes.isSuccess)

        val auditId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.recordAuditEntry(uid, "memory.write", "allow", "Test audit ${UUID.randomUUID()}").getOrThrow()
        }

        var deleteFailed = false
        try {
            withTimeout(DEFAULT_TIMEOUT_MS) {
                firestore.collection("users").document(uid)
                    .collection("audit_logs").document(auditId)
                    .delete().await()
            }
        } catch (e: Exception) {
            val fsErr = e.unwrapFirestoreException()
            assertNotNull(fsErr)
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsErr?.code)
            deleteFailed = true
        }
        assertTrue(deleteFailed)
    }

    @Test
    fun observeMemories_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        val repo = RiyaRepository(firestore)

        var caught: Exception? = null
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repo.observeMemories("some_user_id").first()
            }
        } catch (e: Exception) {
            caught = e
        }
        val fsErr = caught?.unwrapFirestoreException()
        assertNotNull("Expected FirebaseFirestoreException, got $caught", fsErr)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsErr?.code)
    }

    @Test
    fun crossUserAccess_otherUsersTasks_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = RiyaRepository(firestore)
        val aliceTaskId = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.createTask(aliceUid, "Alice Secret Task").getOrThrow()
        }

        signInTestUser(BOB_EMAIL)
        var readFailed = false
        try {
            withTimeout(DEFAULT_TIMEOUT_MS) {
                firestore.collection("users").document(aliceUid)
                    .collection("tasks").document(aliceTaskId)
                    .get().await()
            }
        } catch (e: Exception) {
            val fsErr = e.unwrapFirestoreException()
            assertNotNull(fsErr)
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsErr?.code)
            readFailed = true
        }
        assertTrue(readFailed)
    }

    @Test
    fun updatePreferences_preferredModel_validAllowedInvalidDenied() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repo = RiyaRepository(firestore)

        val profileRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.ensureUserProfile(uid, "Alice", ALICE_EMAIL)
        }
        assertTrue("ensureUserProfile failed: ${profileRes.exceptionOrNull()}", profileRes.isSuccess)

        val validUpdate = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.updatePreferences(userId = uid, preferredModel = "gemini-4-argon")
        }
        assertTrue("validUpdate failed: ${validUpdate.exceptionOrNull()}", validUpdate.isSuccess)

        val invalidUpdate = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.updatePreferences(userId = uid, preferredModel = "gemini-1.5-pro")
        }
        assertTrue("Expected invalidUpdate to fail", invalidUpdate.isFailure)
        val fsErr = invalidUpdate.exceptionOrNull()?.unwrapFirestoreException()
        assertNotNull("Expected FirebaseFirestoreException", fsErr)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsErr?.code)
    }

    private companion object {
        const val ALICE_EMAIL = "alice.riya@test.com"
        const val BOB_EMAIL = "bob.riya@test.com"
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 4000L
    }
}
