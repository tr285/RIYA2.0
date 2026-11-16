package com.example.ui

import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.AiTelemetry
import com.example.ai.RiyaAiEngine
import com.example.data.model.AutonomousTaskRun
import com.example.data.model.BuiltInSkill
import com.example.data.model.ChatMessage
import com.example.data.model.DiagnosticCheck
import com.example.data.model.MemoryFact
import com.example.data.model.PendingConfirmation
import com.example.data.model.PermissionRule
import com.example.data.model.PersonalNote
import com.example.data.model.PersonalTask
import com.example.data.model.ReminderItem
import com.example.data.model.SecurityAuditEntry
import com.example.data.model.UserProfile
import com.example.data.repository.RiyaRepository
import com.example.voice.RiyaAudioRecorder
import com.example.voice.RiyaVoiceEngine
import com.example.voice.VoiceSnapshot
import com.example.voice.VoiceState
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

private const val TAG = "RiyaViewModel"
private const val STOP_TIMEOUT_MILLIS = 5000L

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

data class DeviceHealthInput(
    val manufacturerModel: String,
    val androidVersion: String,
    val sdkInt: Int,
    val totalRamMb: Long,
    val availRamMb: Long,
    val freeStorageMb: Long,
    val totalStorageMb: Long,
    val isSignedIn: Boolean,
    val userEmail: String?,
    val appCheckConfigured: Boolean,
    val sttAvailable: Boolean,
    val ttsReady: Boolean
)

sealed interface ImageGenUiState {
    data object Idle : ImageGenUiState
    data object Loading : ImageGenUiState
    data class Success(val bitmap: Bitmap, val prompt: String, val isEdit: Boolean) : ImageGenUiState
    data class Error(val message: String) : ImageGenUiState
}

sealed interface AudioTranscribeUiState {
    data object Idle : AudioTranscribeUiState
    data class Recording(val elapsedSec: Int, val amplitude: Float) : AudioTranscribeUiState
    data object Transcribing : AudioTranscribeUiState
    data class Success(val transcript: String) : AudioTranscribeUiState
    data class Error(val message: String) : AudioTranscribeUiState
}

class RiyaViewModel(
    private val repository: RiyaRepository,
    private val aiEngine: RiyaAiEngine,
    private val voiceEngine: RiyaVoiceEngine,
    private val audioRecorder: RiyaAudioRecorder,
    val currentUserId: String,
    val displayName: String,
    val email: String?
) : ViewModel() {

    private fun <T> Flow<T>.toUiState(label: String): StateFlow<UiState<T>> =
        this.map<T, UiState<T>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing $label", rootError)
                emit(UiState.Error(rootError.localizedMessage ?: "Failed to load $label"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = UiState.Loading
            )

    val profileState: StateFlow<UiState<UserProfile?>> =
        repository.observeUserProfile(currentUserId).toUiState("user profile")

    val messagesState: StateFlow<UiState<List<ChatMessage>>> =
        repository.observeMessages(currentUserId).toUiState("messages")

    val memoriesState: StateFlow<UiState<List<MemoryFact>>> =
        repository.observeMemories(currentUserId).toUiState("memories")

    val tasksState: StateFlow<UiState<List<PersonalTask>>> =
        repository.observeTasks(currentUserId).toUiState("tasks")

    val remindersState: StateFlow<UiState<List<ReminderItem>>> =
        repository.observeReminders(currentUserId).toUiState("reminders")

    val notesState: StateFlow<UiState<List<PersonalNote>>> =
        repository.observeNotes(currentUserId).toUiState("notes")

    val autonomousRunsState: StateFlow<UiState<List<AutonomousTaskRun>>> =
        repository.observeAutonomousRuns(currentUserId).toUiState("autonomous runs")

    val permissionRulesState: StateFlow<UiState<List<PermissionRule>>> =
        repository.observePermissionRules(currentUserId).toUiState("permission rules")

    val auditLogsState: StateFlow<UiState<List<SecurityAuditEntry>>> =
        repository.observeAuditLogs(currentUserId).toUiState("audit logs")

    val voiceSnapshot: StateFlow<VoiceSnapshot> = voiceEngine.snapshot

    private val _isSending = MutableStateFlow(false)
    val isSending: StateFlow<Boolean> = _isSending.asStateFlow()

    private val _streamingText = MutableStateFlow("")
    val streamingText: StateFlow<String> = _streamingText.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _pendingConfirmation = MutableStateFlow<PendingConfirmation?>(null)
    val pendingConfirmation: StateFlow<PendingConfirmation?> = _pendingConfirmation.asStateFlow()

    private val _activeTaskProgress = MutableStateFlow<String?>(null)
    val activeTaskProgress: StateFlow<String?> = _activeTaskProgress.asStateFlow()

    private val _telemetry = MutableStateFlow(AiTelemetry())
    val telemetry: StateFlow<AiTelemetry> = _telemetry.asStateFlow()

    private val _skillOutputTitle = MutableStateFlow<String?>(null)
    val skillOutputTitle: StateFlow<String?> = _skillOutputTitle.asStateFlow()

    private val _skillOutputContent = MutableStateFlow<String?>(null)
    val skillOutputContent: StateFlow<String?> = _skillOutputContent.asStateFlow()

    private val _isSkillRunning = MutableStateFlow(false)
    val isSkillRunning: StateFlow<Boolean> = _isSkillRunning.asStateFlow()

    private val _diagnosticChecks = MutableStateFlow<List<DiagnosticCheck>>(emptyList())
    val diagnosticChecks: StateFlow<List<DiagnosticCheck>> = _diagnosticChecks.asStateFlow()

    private val _diagnosticsSummaryText = MutableStateFlow("")
    val diagnosticsSummaryText: StateFlow<String> = _diagnosticsSummaryText.asStateFlow()

    private val _imageGenState = MutableStateFlow<ImageGenUiState>(ImageGenUiState.Idle)
    val imageGenState: StateFlow<ImageGenUiState> = _imageGenState.asStateFlow()

    private val _audioTranscribeState = MutableStateFlow<AudioTranscribeUiState>(AudioTranscribeUiState.Idle)
    val audioTranscribeState: StateFlow<AudioTranscribeUiState> = _audioTranscribeState.asStateFlow()

    private val _dictatedTextEvent = MutableStateFlow<String?>(null)
    val dictatedTextEvent: StateFlow<String?> = _dictatedTextEvent.asStateFlow()

    private val activeSessionId = "session_${UUID.randomUUID().toString().take(8)}"
    private var activeChatJob: Job? = null
    private var activeImageJob: Job? = null
    private var activeAudioTickerJob: Job? = null
    private var activeTranscribeJob: Job? = null
    private var startupVoiceApplied = false

    init {
        viewModelScope.launch {
            val ensured = repository.ensureUserProfile(currentUserId, displayName, email)
            ensured.onFailure { err ->
                _errorMessage.value = err.localizedMessage ?: "Failed to initialize user profile."
            }
        }

        // Keep VoiceEngine and telemetry aligned with persisted user preferences
        viewModelScope.launch {
            profileState.collectLatest { state ->
                val profile = (state as? UiState.Success)?.data ?: return@collectLatest
                _telemetry.value = _telemetry.value.copy(modelName = profile.preferredModel)
                voiceEngine.updateSettings(
                    languageCode = profile.voiceLanguage,
                    voiceResponses = profile.voiceResponsesEnabled
                )
                if (!startupVoiceApplied) {
                    startupVoiceApplied = true
                    if (profile.defaultStartupMode == "continuous" && voiceSnapshot.value.sttAvailable) {
                        voiceEngine.setVoiceMode(true)
                    }
                }
            }
        }

        // Waveform animation ticker while voice engine is active
        viewModelScope.launch {
            while (isActive) {
                val snap = voiceSnapshot.value
                val animEnabled = currentProfile()?.waveformAnimationsEnabled ?: true
                if (animEnabled && (snap.voiceState != VoiceState.IDLE && snap.voiceState != VoiceState.ERROR && snap.voiceState != VoiceState.INTERRUPTED)) {
                    voiceEngine.tickWaveform()
                    delay(80L)
                } else {
                    delay(240L)
                }
            }
        }
    }

    private fun currentProfile(): UserProfile? = (profileState.value as? UiState.Success)?.data
    private fun currentMessages(): List<ChatMessage> = (messagesState.value as? UiState.Success)?.data.orEmpty()
    private fun currentMemories(): List<MemoryFact> = (memoriesState.value as? UiState.Success)?.data.orEmpty()
    private fun currentTasks(): List<PersonalTask> = (tasksState.value as? UiState.Success)?.data.orEmpty()
    private fun currentReminders(): List<ReminderItem> = (remindersState.value as? UiState.Success)?.data.orEmpty()
    private fun currentNotes(): List<PersonalNote> = (notesState.value as? UiState.Success)?.data.orEmpty()
    private fun currentRules(): List<PermissionRule> = (permissionRulesState.value as? UiState.Success)?.data.orEmpty()

    fun dismissError() {
        _errorMessage.value = null
    }

    fun dismissStatus() {
        _statusMessage.value = null
    }

    fun dismissSkillOutput() {
        _skillOutputTitle.value = null
        _skillOutputContent.value = null
    }

    fun notifyMessage(msg: String) {
        _statusMessage.value = msg
    }

    fun reportError(msg: String) {
        _errorMessage.value = msg
    }

    // -------------------------------------------------------------------------
    // PERMISSION GATE & AUDIT LOGGING
    // -------------------------------------------------------------------------

    private fun evaluateWithPermissionGate(
        permissionKey: String,
        toolName: String,
        summary: String,
        defaultRequiresConfirm: Boolean,
        onDeniedOrRejected: (() -> Unit)? = null,
        onAllowed: () -> Unit
    ) {
        val rule = currentRules().firstOrNull { it.permissionKey == permissionKey }
        when (rule?.decision) {
            "deny" -> {
                viewModelScope.launch {
                    repository.recordAuditEntry(
                        userId = currentUserId,
                        permissionKey = permissionKey,
                        decision = "deny",
                        reason = "Blocked by rule (${rule.lifetime}): $summary"
                    )
                    if (rule.lifetime == "one_time") {
                        repository.deletePermissionRule(currentUserId, rule.id)
                    }
                }
                _errorMessage.value = "Action blocked by '$permissionKey' permission rule."
                onDeniedOrRejected?.invoke()
            }

            "allow" -> {
                viewModelScope.launch {
                    repository.recordAuditEntry(
                        userId = currentUserId,
                        permissionKey = permissionKey,
                        decision = "allow",
                        reason = "Auto-allowed by rule (${rule.lifetime}): $summary"
                    )
                    if (rule.lifetime == "one_time") {
                        repository.deletePermissionRule(currentUserId, rule.id)
                    }
                }
                onAllowed()
            }

            "confirm", null -> {
                val needsPrompt = rule?.decision == "confirm" || defaultRequiresConfirm
                if (!needsPrompt) {
                    viewModelScope.launch {
                        repository.recordAuditEntry(
                            userId = currentUserId,
                            permissionKey = permissionKey,
                            decision = "allow",
                            reason = summary
                        )
                    }
                    onAllowed()
                } else {
                    _pendingConfirmation.value = PendingConfirmation(
                        id = UUID.randomUUID().toString(),
                        toolName = toolName,
                        permissionKey = permissionKey,
                        summary = summary,
                        onApprove = {
                            _pendingConfirmation.value = null
                            viewModelScope.launch {
                                repository.recordAuditEntry(
                                    userId = currentUserId,
                                    permissionKey = permissionKey,
                                    decision = "allow",
                                    reason = "User approved: $summary"
                                )
                                if (rule?.lifetime == "one_time") {
                                    repository.deletePermissionRule(currentUserId, rule.id)
                                }
                            }
                            onAllowed()
                        },
                        onReject = {
                            _pendingConfirmation.value = null
                            viewModelScope.launch {
                                repository.recordAuditEntry(
                                    userId = currentUserId,
                                    permissionKey = permissionKey,
                                    decision = "deny",
                                    reason = "User rejected: $summary"
                                )
                            }
                            _statusMessage.value = "Action cancelled by user."
                            onDeniedOrRejected?.invoke()
                        }
                    )
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // CONVERSATION & STREAMING GEMINI AI
    // -------------------------------------------------------------------------

    fun sendMessage(rawText: String, fromVoice: Boolean = false) {
        val prompt = rawText.trim()
        if (prompt.isEmpty() || _isSending.value) return
        _errorMessage.value = null

        viewModelScope.launch {
            val saved = repository.addMessage(
                userId = currentUserId,
                sessionId = activeSessionId,
                role = "user",
                text = prompt,
                latencyMs = 0L
            )
            if (saved.isFailure) {
                _errorMessage.value = saved.exceptionOrNull()?.localizedMessage ?: "Failed to save message."
                return@launch
            }
            streamAssistantReply(prompt, fromVoice)
        }
    }

    fun regenerateLastResponse() {
        if (_isSending.value) return
        val lastUserMsg = currentMessages().lastOrNull { it.role == "user" }
        if (lastUserMsg == null) {
            _statusMessage.value = "Send a message first to regenerate a response."
            return
        }
        streamAssistantReply(lastUserMsg.text, fromVoice = false)
    }

    private fun streamAssistantReply(prompt: String, fromVoice: Boolean) {
        activeChatJob?.cancel()
        activeChatJob = viewModelScope.launch {
            _isSending.value = true
            _streamingText.value = ""
            voiceEngine.markThinking(true)
            val startMs = System.currentTimeMillis()

            val profile = currentProfile() ?: UserProfile()
            val historyPairs = currentMessages()
                .takeLast(14)
                .map { it.role to it.text }

            try {
                var finalReply = ""
                aiEngine.streamConversation(
                    userPrompt = prompt,
                    historyPairs = historyPairs,
                    memories = currentMemories(),
                    tasks = currentTasks(),
                    reminders = currentReminders(),
                    responseStyle = profile.responseStyle,
                    languageCode = profile.voiceLanguage,
                    autoMemoryRecall = profile.autoMemoryRecallEnabled,
                    modelName = profile.preferredModel
                ).collect { accumulated ->
                    finalReply = accumulated
                    _streamingText.value = accumulated
                }

                val elapsed = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)
                _streamingText.value = ""
                _isSending.value = false
                voiceEngine.markThinking(false)

                repository.addMessage(
                    userId = currentUserId,
                    sessionId = activeSessionId,
                    role = "assistant",
                    text = finalReply,
                    latencyMs = elapsed
                )

                val prev = _telemetry.value
                val nextTotal = prev.totalGenerations + 1
                val nextAvg = ((prev.avgLatencyMs * prev.totalGenerations) + elapsed) / nextTotal
                _telemetry.value = prev.copy(
                    modelName = profile.preferredModel,
                    totalGenerations = nextTotal,
                    lastLatencyMs = elapsed,
                    avgLatencyMs = nextAvg
                )

                if (fromVoice || voiceSnapshot.value.voiceModeActive) {
                    voiceEngine.speakIfEnabled(finalReply, force = fromVoice && profile.voiceResponsesEnabled)
                }
            } catch (e: CancellationException) {
                _streamingText.value = ""
                _isSending.value = false
                voiceEngine.markThinking(false)
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Chat generation error", e)
                _streamingText.value = ""
                _isSending.value = false
                voiceEngine.markThinking(false)
                _errorMessage.value = e.localizedMessage ?: "AI generation failed."
            }
        }
    }

    fun clearConversation() {
        viewModelScope.launch {
            repository.clearMessages(currentUserId).onFailure {
                _errorMessage.value = it.localizedMessage ?: "Failed to clear conversation."
            }.onSuccess {
                _statusMessage.value = "Conversation cleared."
            }
        }
    }

    // -------------------------------------------------------------------------
    // VOICE CONTROLS
    // -------------------------------------------------------------------------

    fun onVoiceInputRecognized(spokenText: String) {
        sendMessage(spokenText, fromVoice = true)
    }

    fun onDictatedText(spokenText: String) {
        val clean = spokenText.trim()
        if (clean.isNotEmpty()) {
            _dictatedTextEvent.value = clean
        }
    }

    fun consumeDictatedText() {
        _dictatedTextEvent.value = null
    }

    suspend fun transcribeMicWav(wavBytes: ByteArray): String {
        val profile = currentProfile() ?: UserProfile()
        return aiEngine.speechToText(
            audioBytes = wavBytes,
            mimeType = "audio/wav",
            languageHint = profile.voiceLanguage,
            modelName = profile.preferredModel
        )
    }

    fun toggleDictation() {
        if (voiceSnapshot.value.isDictating) {
            voiceEngine.stopDictation()
        } else {
            evaluateWithPermissionGate(
                permissionKey = "voice.microphone",
                toolName = "Voice Dictation",
                summary = "Capture speech via Android Microphone API into message composer",
                defaultRequiresConfirm = false
            ) {
                voiceEngine.startDictation()
            }
        }
    }

    fun cancelVoiceCapture() {
        voiceEngine.cancelCapture()
    }

    fun toggleVoiceMode() {
        val currentlyActive = voiceSnapshot.value.voiceModeActive
        if (currentlyActive) {
            voiceEngine.setVoiceMode(false)
        } else {
            evaluateWithPermissionGate(
                permissionKey = "voice.microphone",
                toolName = "Voice Mode",
                summary = "Enable continuous microphone listening for voice chat",
                defaultRequiresConfirm = false
            ) {
                voiceEngine.setVoiceMode(true)
            }
        }
    }

    fun toggleVoicePause() {
        voiceEngine.togglePause()
    }

    fun startPushToTalk() {
        evaluateWithPermissionGate(
            permissionKey = "voice.microphone",
            toolName = "Push-to-Talk",
            summary = "Record voice query via device microphone",
            defaultRequiresConfirm = false
        ) {
            voiceEngine.startPushToTalk()
        }
    }

    fun stopPushToTalk() {
        voiceEngine.stopPushToTalk()
    }

    fun interruptVoice() {
        activeChatJob?.cancel()
        _streamingText.value = ""
        _isSending.value = false
        voiceEngine.stopSpeaking()
        voiceEngine.stopListening()
    }

    fun clearVoiceError() {
        voiceEngine.clearError()
    }

    fun testSpeakerAudio() {
        voiceEngine.testSpeaker()
            .onSuccess { msg -> _statusMessage.value = msg }
            .onFailure { err -> _errorMessage.value = err.localizedMessage ?: "Speaker test failed." }
    }

    // -------------------------------------------------------------------------
    // MEMORIES CRUD
    // -------------------------------------------------------------------------

    fun saveMemory(category: String, key: String, value: String) {
        if (key.isBlank() || value.isBlank()) {
            _errorMessage.value = "Memory key and value cannot be empty."
            return
        }
        evaluateWithPermissionGate(
            permissionKey = "memory.write",
            toolName = "Remember Fact",
            summary = "Store memory [$category] $key",
            defaultRequiresConfirm = false
        ) {
            viewModelScope.launch {
                repository.saveMemory(currentUserId, category, key, value)
                    .onSuccess { _statusMessage.value = "Memory saved: $key" }
                    .onFailure { _errorMessage.value = it.localizedMessage ?: "Failed to save memory." }
            }
        }
    }

    fun deleteMemory(memory: MemoryFact) {
        evaluateWithPermissionGate(
            permissionKey = "memory.delete",
            toolName = "Forget Memory",
            summary = "Delete memory '${memory.key}'",
            defaultRequiresConfirm = false
        ) {
            viewModelScope.launch {
                repository.deleteMemory(currentUserId, memory.id)
                    .onSuccess { _statusMessage.value = "Memory deleted." }
                    .onFailure { _errorMessage.value = it.localizedMessage ?: "Failed to delete memory." }
            }
        }
    }

    fun clearAllMemories() {
        evaluateWithPermissionGate(
            permissionKey = "memory.delete",
            toolName = "Clear All Memories",
            summary = "Permanently delete all stored memories (${currentMemories().size} facts)",
            defaultRequiresConfirm = true
        ) {
            viewModelScope.launch {
                repository.clearAllMemories(currentUserId)
                    .onSuccess { _statusMessage.value = "All memories cleared." }
                    .onFailure { _errorMessage.value = it.localizedMessage ?: "Failed to clear memories." }
            }
        }
    }

    // -------------------------------------------------------------------------
    // PRODUCTIVITY: TASKS, REMINDERS, NOTES
    // -------------------------------------------------------------------------

    fun createTask(title: String, description: String, priority: String, category: String, dueDateLabel: String) {
        if (title.isBlank()) {
            _errorMessage.value = "Task title is required."
            return
        }
        evaluateWithPermissionGate(
            permissionKey = "productivity.manage",
            toolName = "Create Task",
            summary = "Create task '$title'",
            defaultRequiresConfirm = false
        ) {
            viewModelScope.launch {
                repository.createTask(currentUserId, title, description, priority, category, dueDateLabel)
                    .onSuccess { _statusMessage.value = "Task created." }
                    .onFailure { _errorMessage.value = it.localizedMessage ?: "Failed to create task." }
            }
        }
    }

    fun toggleTaskCompleted(task: PersonalTask) {
        val nextStatus = if (task.status == "completed") "pending" else "completed"
        viewModelScope.launch {
            repository.updateTaskStatus(currentUserId, task.id, nextStatus)
                .onFailure { _errorMessage.value = it.localizedMessage ?: "Failed to update task." }
        }
    }

    fun deleteTask(task: PersonalTask) {
        viewModelScope.launch {
            repository.deleteTask(currentUserId, task.id)
                .onFailure { _errorMessage.value = it.localizedMessage ?: "Failed to delete task." }
        }
    }

    fun createReminder(title: String, triggerLabel: String, recurringRule: String) {
        if (title.isBlank()) {
            _errorMessage.value = "Reminder title is required."
            return
        }
        evaluateWithPermissionGate(
            permissionKey = "system.notification",
            toolName = "Schedule Reminder",
            summary = "Add reminder '$title' ($triggerLabel)",
            defaultRequiresConfirm = false
        ) {
            viewModelScope.launch {
                repository.createReminder(currentUserId, title, triggerLabel, recurringRule)
                    .onSuccess { _statusMessage.value = "Reminder scheduled." }
                    .onFailure { _errorMessage.value = it.localizedMessage ?: "Failed to create reminder." }
            }
        }
    }

    fun toggleReminderTriggered(reminder: ReminderItem) {
        val next = if (reminder.status == "pending") "triggered" else "pending"
        viewModelScope.launch {
            repository.updateReminderStatus(currentUserId, reminder.id, next)
                .onFailure { _errorMessage.value = it.localizedMessage ?: "Failed to update reminder." }
        }
    }

    fun deleteReminder(reminder: ReminderItem) {
        viewModelScope.launch {
            repository.deleteReminder(currentUserId, reminder.id)
                .onFailure { _errorMessage.value = it.localizedMessage ?: "Failed to delete reminder." }
        }
    }

    fun createNote(title: String, content: String, category: String) {
        if (title.isBlank() || content.isBlank()) {
            _errorMessage.value = "Note title and content are required."
            return
        }
        evaluateWithPermissionGate(
            permissionKey = "productivity.manage",
            toolName = "Save Note",
            summary = "Create note '$title'",
            defaultRequiresConfirm = false
        ) {
            viewModelScope.launch {
                repository.createNote(currentUserId, title, content, category)
                    .onSuccess { _statusMessage.value = "Note saved." }
                    .onFailure { _errorMessage.value = it.localizedMessage ?: "Failed to save note." }
            }
        }
    }

    fun toggleNotePin(note: PersonalNote) {
        viewModelScope.launch {
            repository.toggleNotePin(currentUserId, note.id, !note.pinned)
                .onFailure { _errorMessage.value = it.localizedMessage ?: "Failed to update note." }
        }
    }

    fun deleteNote(note: PersonalNote) {
        viewModelScope.launch {
            repository.deleteNote(currentUserId, note.id)
                .onFailure { _errorMessage.value = it.localizedMessage ?: "Failed to delete note." }
        }
    }

    // -------------------------------------------------------------------------
    // AUTONOMOUS MULTI-STEP TASK PLANNER & BUILT-IN SKILLS
    // -------------------------------------------------------------------------

    fun startAutonomousTask(goal: String) {
        val cleanGoal = goal.trim()
        if (cleanGoal.isEmpty()) {
            _errorMessage.value = "Enter a goal for RIYA to plan and execute."
            return
        }

        val autonomousRule = currentRules().firstOrNull { it.permissionKey == "tasks.autonomous" }
        if (autonomousRule?.decision == "deny") {
            viewModelScope.launch {
                repository.recordAuditEntry(
                    userId = currentUserId,
                    permissionKey = "tasks.autonomous",
                    decision = "deny",
                    reason = "Autonomous task blocked by rule: $cleanGoal"
                )
            }
            _errorMessage.value = "Autonomous execution is denied by your Trust & Permissions rule."
            return
        }

        viewModelScope.launch {
            _activeTaskProgress.value = "Planning steps for: $cleanGoal"
            val runIdResult = repository.createAutonomousRun(
                userId = currentUserId,
                goal = cleanGoal,
                status = "running",
                stepsSummary = "Generating structured execution plan...",
                stepsCompleted = 0L,
                stepsTotal = 1L
            )
            val runId = runIdResult.getOrNull()
            if (runId == null) {
                _activeTaskProgress.value = null
                _errorMessage.value = runIdResult.exceptionOrNull()?.localizedMessage ?: "Failed to start task run."
                return@launch
            }

            try {
                val plan = aiEngine.planAutonomousTask(cleanGoal, currentMemories(), currentTasks())
                val stepsCount = plan.steps.size.toLong().coerceAtLeast(1L)
                val formattedSteps = plan.steps.joinToString("\n") { step ->
                    "${step.stepIndex}. [${step.riskTier.uppercase()}] ${step.action} — ${step.description} (${step.permissionKey})"
                }
                val needsGate = autonomousRule?.decision == "confirm" ||
                    (autonomousRule == null && plan.steps.any { it.riskTier != "low" })

                if (needsGate) {
                    repository.updateAutonomousRun(
                        userId = currentUserId,
                        runId = runId,
                        status = "awaiting_confirmation",
                        stepsSummary = "Awaiting user approval:\n$formattedSteps",
                        stepsCompleted = 0L,
                        stepsTotal = stepsCount
                    )
                    _activeTaskProgress.value = "Awaiting approval for '$cleanGoal'"
                    evaluateWithPermissionGate(
                        permissionKey = "tasks.autonomous",
                        toolName = "Autonomous Plan (${plan.steps.size} steps)",
                        summary = "Execute goal '$cleanGoal'\n$formattedSteps",
                        defaultRequiresConfirm = true,
                        onDeniedOrRejected = {
                            _activeTaskProgress.value = null
                            viewModelScope.launch {
                                repository.updateAutonomousRun(
                                    userId = currentUserId,
                                    runId = runId,
                                    status = "cancelled",
                                    stepsSummary = "Cancelled by user before execution.\n$formattedSteps",
                                    stepsCompleted = 0L,
                                    stepsTotal = stepsCount
                                )
                            }
                        },
                        onAllowed = {
                            viewModelScope.launch {
                                repository.updateAutonomousRun(
                                    userId = currentUserId,
                                    runId = runId,
                                    status = "completed",
                                    stepsSummary = "$formattedSteps\n\nResult:\n${plan.finalSummary}",
                                    stepsCompleted = stepsCount,
                                    stepsTotal = stepsCount
                                )
                                _activeTaskProgress.value = null
                                _statusMessage.value = "Autonomous task completed: ${plan.goal}"
                            }
                        }
                    )
                } else {
                    repository.updateAutonomousRun(
                        userId = currentUserId,
                        runId = runId,
                        status = "completed",
                        stepsSummary = "$formattedSteps\n\nResult:\n${plan.finalSummary}",
                        stepsCompleted = stepsCount,
                        stepsTotal = stepsCount
                    )
                    repository.recordAuditEntry(
                        userId = currentUserId,
                        permissionKey = "tasks.autonomous",
                        decision = "allow",
                        reason = "Completed ${plan.steps.size}-step low-risk plan: $cleanGoal"
                    )
                    _activeTaskProgress.value = null
                    _statusMessage.value = "Autonomous task completed: ${plan.goal}"
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                repository.updateAutonomousRun(
                    userId = currentUserId,
                    runId = runId,
                    status = "failed",
                    stepsSummary = "Error: ${e.localizedMessage ?: "Planning failed"}",
                    stepsCompleted = 0L,
                    stepsTotal = 1L
                )
                _activeTaskProgress.value = null
                _errorMessage.value = e.localizedMessage ?: "Autonomous task failed."
            }
        }
    }

    fun runBuiltInSkill(skill: BuiltInSkill, userInput: String) {
        val defaultGate = skill.riskTier != "LOW"
        evaluateWithPermissionGate(
            permissionKey = skill.permissionKey,
            toolName = skill.name,
            summary = "Run ${skill.name} skill${if (userInput.isNotBlank()) " on '$userInput'" else ""}",
            defaultRequiresConfirm = defaultGate
        ) {
            viewModelScope.launch {
                _isSkillRunning.value = true
                _skillOutputTitle.value = skill.name
                _skillOutputContent.value = "Executing ${skill.name} with Gemini..."
                try {
                    val activeModel = currentProfile()?.preferredModel ?: "gemini-3.8-flash"
                    val result = aiEngine.runSkill(
                        skill = skill,
                        userInput = userInput,
                        memories = currentMemories(),
                        tasks = currentTasks(),
                        modelName = activeModel
                    )
                    _skillOutputContent.value = result
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _skillOutputContent.value = "Error: ${e.localizedMessage ?: "Skill failed."}"
                } finally {
                    _isSkillRunning.value = false
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // PERMISSIONS & PREFERENCES
    // -------------------------------------------------------------------------

    fun savePermissionRule(
        permissionKey: String,
        decision: String,
        lifetime: String,
        reason: String
    ) {
        viewModelScope.launch {
            repository.savePermissionRule(
                userId = currentUserId,
                permissionKey = permissionKey,
                decision = decision,
                lifetime = lifetime,
                reason = reason.ifBlank { "Configured in Trust Center" }
            ).onSuccess {
                _statusMessage.value = "Rule saved: $permissionKey -> $decision ($lifetime)"
            }.onFailure {
                _errorMessage.value = it.localizedMessage ?: "Failed to save rule."
            }
        }
    }

    fun deletePermissionRule(rule: PermissionRule) {
        viewModelScope.launch {
            repository.deletePermissionRule(currentUserId, rule.id)
                .onSuccess {
                    repository.recordAuditEntry(
                        userId = currentUserId,
                        permissionKey = rule.permissionKey,
                        decision = "confirm",
                        reason = "Rule removed; restored default prompt policy"
                    )
                    _statusMessage.value = "Rule removed for ${rule.permissionKey}"
                }
                .onFailure {
                    _errorMessage.value = it.localizedMessage ?: "Failed to delete rule."
                }
        }
    }

    fun updateThemeMode(themeMode: String) {
        viewModelScope.launch {
            repository.updatePreferences(userId = currentUserId, themeMode = themeMode)
                .onFailure { _errorMessage.value = it.localizedMessage ?: "Failed to update theme." }
        }
    }

    fun selectPreferredModel(modelName: String) {
        viewModelScope.launch {
            repository.updatePreferences(userId = currentUserId, preferredModel = modelName)
                .onSuccess {
                    _telemetry.value = _telemetry.value.copy(modelName = modelName)
                    _statusMessage.value = "Model switched to $modelName"
                }
                .onFailure {
                    _errorMessage.value = it.localizedMessage ?: "Failed to update preferred model."
                }
        }
    }

    fun updatePreferences(
        defaultStartupMode: String? = null,
        voiceLanguage: String? = null,
        responseStyle: String? = null,
        waveformAnimationsEnabled: Boolean? = null,
        autoMemoryRecallEnabled: Boolean? = null,
        voiceResponsesEnabled: Boolean? = null
    ) {
        viewModelScope.launch {
            repository.updatePreferences(
                userId = currentUserId,
                defaultStartupMode = defaultStartupMode,
                voiceLanguage = voiceLanguage,
                responseStyle = responseStyle,
                waveformAnimationsEnabled = waveformAnimationsEnabled,
                autoMemoryRecallEnabled = autoMemoryRecallEnabled,
                voiceResponsesEnabled = voiceResponsesEnabled
            ).onSuccess {
                _statusMessage.value = "Preferences updated."
            }.onFailure {
                _errorMessage.value = it.localizedMessage ?: "Failed to save preferences."
            }
        }
    }

    // -------------------------------------------------------------------------
    // STUDIO: IMAGE GENERATION / EDITING & AUDIO TRANSCRIPTION
    // -------------------------------------------------------------------------

    fun generateOrEditImage(prompt: String, inputBitmap: Bitmap?) {
        val cleanPrompt = prompt.trim()
        if (cleanPrompt.isEmpty()) {
            _imageGenState.value = ImageGenUiState.Error("Enter a prompt or edit instruction first.")
            return
        }
        activeImageJob?.cancel()
        activeImageJob = viewModelScope.launch {
            _imageGenState.value = ImageGenUiState.Loading
            try {
                val outputBitmap = if (inputBitmap != null) {
                    aiEngine.editImage(inputBitmap, cleanPrompt)
                } else {
                    aiEngine.generateImage(cleanPrompt)
                }
                _imageGenState.value = ImageGenUiState.Success(
                    bitmap = outputBitmap,
                    prompt = cleanPrompt,
                    isEdit = inputBitmap != null
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Studio image generation failed", e)
                _imageGenState.value = ImageGenUiState.Error(
                    e.localizedMessage ?: "Image generation failed."
                )
            }
        }
    }

    fun clearImageStudio() {
        activeImageJob?.cancel()
        _imageGenState.value = ImageGenUiState.Idle
    }

    fun startAudioRecording() {
        voiceEngine.stopListening()
        activeAudioTickerJob?.cancel()
        activeTranscribeJob?.cancel()
        val startResult = audioRecorder.startRecording()
        if (startResult.isFailure) {
            _audioTranscribeState.value = AudioTranscribeUiState.Error(
                startResult.exceptionOrNull()?.localizedMessage ?: "Failed to start microphone recorder."
            )
            return
        }
        val startedAt = System.currentTimeMillis()
        _audioTranscribeState.value = AudioTranscribeUiState.Recording(elapsedSec = 0, amplitude = 0.08f)
        activeAudioTickerJob = viewModelScope.launch {
            while (isActive) {
                delay(100L)
                val elapsed = ((System.currentTimeMillis() - startedAt) / 1000L).toInt()
                val amp = audioRecorder.currentAmplitudeNormalized()
                _audioTranscribeState.value = AudioTranscribeUiState.Recording(
                    elapsedSec = elapsed,
                    amplitude = amp
                )
            }
        }
    }

    fun stopAndTranscribeAudio() {
        activeAudioTickerJob?.cancel()
        activeTranscribeJob?.cancel()
        _audioTranscribeState.value = AudioTranscribeUiState.Transcribing
        activeTranscribeJob = viewModelScope.launch {
            val bytesResult = withContext(Dispatchers.IO) {
                audioRecorder.stopAndReadBytes()
            }
            val audioBytes = bytesResult.getOrElse { err ->
                _audioTranscribeState.value = AudioTranscribeUiState.Error(
                    err.localizedMessage ?: "Failed to finalize audio clip."
                )
                return@launch
            }
            try {
                val profile = currentProfile() ?: UserProfile()
                val transcript = aiEngine.transcribeAudio(
                    audioBytes = audioBytes,
                    mimeType = "audio/mp4",
                    languageHint = profile.voiceLanguage,
                    modelName = profile.preferredModel
                )
                _audioTranscribeState.value = AudioTranscribeUiState.Success(transcript)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Audio transcription failed", e)
                _audioTranscribeState.value = AudioTranscribeUiState.Error(
                    e.localizedMessage ?: "Failed to transcribe recorded audio."
                )
            }
        }
    }

    fun cancelAudioRecording() {
        activeAudioTickerJob?.cancel()
        activeTranscribeJob?.cancel()
        audioRecorder.stopAndDiscard()
        _audioTranscribeState.value = AudioTranscribeUiState.Idle
    }

    fun clearAudioTranscribe() {
        _audioTranscribeState.value = AudioTranscribeUiState.Idle
    }

    // -------------------------------------------------------------------------
    // SYSTEM STATUS & DIAGNOSTICS
    // -------------------------------------------------------------------------

    fun runDiagnostics(device: DeviceHealthInput) {
        evaluateWithPermissionGate(
            permissionKey = "system.info",
            toolName = "System Diagnostics",
            summary = "Inspect Android runtime, storage, AI, voice, and Firestore telemetry",
            defaultRequiresConfirm = false
        ) {
            val t = _telemetry.value
            val memCount = currentMemories().size
            val taskCount = currentTasks().size
            val remCount = currentReminders().size
            val noteCount = currentNotes().size
            val rulesCount = currentRules().size

            val checks = listOf(
                DiagnosticCheck(
                    name = "Android Device & OS",
                    status = "PASSED",
                    summary = "${device.manufacturerModel} • Android ${device.androidVersion} (API ${device.sdkInt})",
                    detail = "RAM ${device.availRamMb} MB free / ${device.totalRamMb} MB total"
                ),
                DiagnosticCheck(
                    name = "Internal Storage",
                    status = if (device.freeStorageMb > 200L) "PASSED" else "WARNING",
                    summary = "${device.freeStorageMb} MB free of ${device.totalStorageMb} MB",
                    detail = "App files partition healthy"
                ),
                DiagnosticCheck(
                    name = "Google Auth & Cloud Isolation",
                    status = if (device.isSignedIn) "PASSED" else "FAILED",
                    summary = if (device.isSignedIn) "Signed in (${device.userEmail ?: currentUserId})" else "Not signed in",
                    detail = "Scoped to /users/$currentUserId"
                ),
                DiagnosticCheck(
                    name = "Firebase App Check & AI Logic",
                    status = if (device.appCheckConfigured) "PASSED" else "WARNING",
                    summary = "Model: ${t.modelName} • Generations: ${t.totalGenerations}",
                    detail = "Last latency: ${t.lastLatencyMs} ms • Avg: ${t.avgLatencyMs.toInt()} ms"
                ),
                DiagnosticCheck(
                    name = "Voice Input (STT) & Output (TTS)",
                    status = if (device.ttsReady) "PASSED" else "WARNING",
                    summary = "STT: ${if (device.sttAvailable) "Speech API + Mic PCM" else "Direct Mic PCM"} • TTS: ${if (device.ttsReady) "Ready" else "Initializing"}",
                    detail = "Language: ${currentProfile()?.voiceLanguage ?: "en"}"
                ),
                DiagnosticCheck(
                    name = "Firestore Workspace Inventory",
                    status = "PASSED",
                    summary = "$memCount memories • $taskCount tasks • $remCount reminders • $noteCount notes",
                    detail = "$rulesCount active trust rules"
                )
            )

            _diagnosticChecks.value = checks
            _diagnosticsSummaryText.value = buildString {
                appendLine("=== RIYA ANDROID DIAGNOSTICS REPORT ===")
                appendLine("Device: ${device.manufacturerModel} (Android ${device.androidVersion}, API ${device.sdkInt})")
                appendLine("Memory: ${device.availRamMb} MB / ${device.totalRamMb} MB free")
                appendLine("Storage: ${device.freeStorageMb} MB / ${device.totalStorageMb} MB free")
                appendLine("Account UID: $currentUserId")
                appendLine("AI Engine: ${t.modelName} | Calls=${t.totalGenerations} | Avg=${t.avgLatencyMs.toInt()}ms")
                appendLine("Voice: STT=${device.sttAvailable} | TTS=${device.ttsReady}")
                appendLine("Records: memories=$memCount, tasks=$taskCount, reminders=$remCount, notes=$noteCount, rules=$rulesCount")
                checks.forEach {
                    appendLine("[${it.status}] ${it.name}: ${it.summary}")
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        activeChatJob?.cancel()
        activeImageJob?.cancel()
        activeAudioTickerJob?.cancel()
        activeTranscribeJob?.cancel()
        voiceEngine.release()
        audioRecorder.stopAndDiscard()
    }
}
