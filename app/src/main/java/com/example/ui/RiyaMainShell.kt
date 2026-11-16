package com.example.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.R
import com.example.ai.RiyaAiEngine
import com.example.data.model.UserProfile
import com.example.data.repository.RiyaRepository
import com.example.ui.screens.ChatScreen
import com.example.ui.screens.DiagnosticsScreen
import com.example.ui.screens.MemoriesScreen
import com.example.ui.screens.PermissionsScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.StudioScreen
import com.example.ui.screens.TasksScreen
import com.example.ui.theme.LocalRiyaColors
import com.example.ui.theme.RiyaTheme
import com.example.voice.RiyaAudioRecorder
import com.example.voice.RiyaVoiceEngine
import com.google.firebase.firestore.FirebaseFirestore

enum class RiyaTab(
    val routeId: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
) {
    CHAT("chat", R.string.nav_chat, Icons.AutoMirrored.Filled.Chat, Icons.AutoMirrored.Outlined.Chat, "tab_chat"),
    MEMORIES("memories", R.string.nav_memories, Icons.Filled.Psychology, Icons.Outlined.Psychology, "tab_memories"),
    TASKS("tasks", R.string.nav_tasks, Icons.Filled.Checklist, Icons.Outlined.Checklist, "tab_tasks"),
    STUDIO("studio", R.string.nav_studio, Icons.Filled.AutoAwesome, Icons.Outlined.AutoAwesome, "tab_studio"),
    PERMISSIONS("permissions", R.string.nav_permissions, Icons.Filled.Shield, Icons.Outlined.Shield, "tab_permissions"),
    DIAGNOSTICS("diagnostics", R.string.nav_diagnostics, Icons.Filled.MonitorHeart, Icons.Outlined.MonitorHeart, "tab_diagnostics"),
    SETTINGS("settings", R.string.nav_settings, Icons.Filled.Settings, Icons.Outlined.Settings, "tab_settings")
}

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class, ExperimentalMaterial3Api::class)
@Composable
fun RiyaMainShell(
    currentUserId: String,
    displayName: String,
    email: String?
) {
    val viewModel: RiyaViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                val voiceEngine = RiyaVoiceEngine(app)
                val audioRecorder = RiyaAudioRecorder(app)
                val vm = RiyaViewModel(
                    repository = RiyaRepository(db),
                    aiEngine = RiyaAiEngine(),
                    voiceEngine = voiceEngine,
                    audioRecorder = audioRecorder,
                    currentUserId = currentUserId,
                    displayName = displayName,
                    email = email
                )
                voiceEngine.onRecognizedText = { spoken -> vm.onVoiceInputRecognized(spoken) }
                voiceEngine.onDictationResult = { spoken -> vm.onDictatedText(spoken) }
                voiceEngine.onSpeechToTextAudio = { wavBytes -> vm.transcribeMicWav(wavBytes) }
                vm
            }
        }
    )

    val profileState by viewModel.profileState.collectAsStateWithLifecycle()
    val profile = (profileState as? UiState.Success)?.data ?: UserProfile(
        userId = currentUserId,
        displayName = displayName,
        email = email
    )

    val messagesState by viewModel.messagesState.collectAsStateWithLifecycle()
    val memoriesState by viewModel.memoriesState.collectAsStateWithLifecycle()
    val tasksState by viewModel.tasksState.collectAsStateWithLifecycle()
    val remindersState by viewModel.remindersState.collectAsStateWithLifecycle()
    val notesState by viewModel.notesState.collectAsStateWithLifecycle()
    val runsState by viewModel.autonomousRunsState.collectAsStateWithLifecycle()
    val rulesState by viewModel.permissionRulesState.collectAsStateWithLifecycle()
    val auditLogsState by viewModel.auditLogsState.collectAsStateWithLifecycle()

    val voiceSnapshot by viewModel.voiceSnapshot.collectAsStateWithLifecycle()
    val isSending by viewModel.isSending.collectAsStateWithLifecycle()
    val streamingText by viewModel.streamingText.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()
    val pendingConfirmation by viewModel.pendingConfirmation.collectAsStateWithLifecycle()
    val activeTaskProgress by viewModel.activeTaskProgress.collectAsStateWithLifecycle()
    val telemetry by viewModel.telemetry.collectAsStateWithLifecycle()
    val skillTitle by viewModel.skillOutputTitle.collectAsStateWithLifecycle()
    val skillContent by viewModel.skillOutputContent.collectAsStateWithLifecycle()
    val isSkillRunning by viewModel.isSkillRunning.collectAsStateWithLifecycle()
    val diagChecks by viewModel.diagnosticChecks.collectAsStateWithLifecycle()
    val diagSummary by viewModel.diagnosticsSummaryText.collectAsStateWithLifecycle()
    val imageGenState by viewModel.imageGenState.collectAsStateWithLifecycle()
    val audioTranscribeState by viewModel.audioTranscribeState.collectAsStateWithLifecycle()
    val dictatedTextEvent by viewModel.dictatedTextEvent.collectAsStateWithLifecycle()

    var currentTab by rememberSaveable { mutableStateOf(RiyaTab.CHAT) }

    BackHandler(enabled = currentTab != RiyaTab.CHAT) {
        currentTab = RiyaTab.CHAT
    }

    val context = LocalContext.current
    val activity = context as? Activity
    val widthSizeClass = if (activity != null) {
        calculateWindowSizeClass(activity).widthSizeClass
    } else {
        WindowWidthSizeClass.Compact
    }
    val useNavRail = widthSizeClass != WindowWidthSizeClass.Compact

    val bottomTabs = listOf(
        RiyaTab.CHAT,
        RiyaTab.MEMORIES,
        RiyaTab.TASKS,
        RiyaTab.STUDIO,
        RiyaTab.PERMISSIONS
    )

    RiyaTheme(darkTheme = profile.themeMode == "dark") {
        val riyaColors = LocalRiyaColors.current
        Scaffold(
            containerColor = riyaColors.bgBase,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                TopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "RIYA",
                                style = MaterialTheme.typography.titleLarge,
                                color = riyaColors.textPrimary
                            )
                            Text(
                                text = "• ${profile.displayName.split(' ').firstOrNull().orEmpty()}",
                                style = MaterialTheme.typography.labelLarge,
                                color = riyaColors.textSecondary
                            )
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { currentTab = RiyaTab.DIAGNOSTICS },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("nav_diagnostics_button")
                        ) {
                            Icon(
                                imageVector = if (currentTab == RiyaTab.DIAGNOSTICS) Icons.Filled.MonitorHeart else Icons.Outlined.MonitorHeart,
                                contentDescription = stringResource(R.string.nav_diagnostics),
                                tint = if (currentTab == RiyaTab.DIAGNOSTICS) riyaColors.accent else riyaColors.textSecondary
                            )
                        }
                        IconButton(
                            onClick = { currentTab = RiyaTab.SETTINGS },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("nav_settings_button")
                        ) {
                            Icon(
                                imageVector = if (currentTab == RiyaTab.SETTINGS) Icons.Filled.Settings else Icons.Outlined.Settings,
                                contentDescription = stringResource(R.string.nav_settings),
                                tint = if (currentTab == RiyaTab.SETTINGS) riyaColors.accent else riyaColors.textSecondary
                            )
                        }
                        IconButton(
                            onClick = {
                                val nextTheme = if (profile.themeMode == "dark") "light" else "dark"
                                viewModel.updateThemeMode(nextTheme)
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("quick_theme_toggle")
                        ) {
                            Icon(
                                imageVector = if (profile.themeMode == "dark") Icons.Filled.LightMode else Icons.Filled.DarkMode,
                                contentDescription = "Toggle theme",
                                tint = riyaColors.textSecondary
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = riyaColors.bgSurface
                    )
                )
            },
            bottomBar = {
                if (!useNavRail) {
                    NavigationBar(
                        containerColor = riyaColors.bgSurface
                    ) {
                        bottomTabs.forEach { tab ->
                            val selected = currentTab == tab
                            val label = stringResource(tab.labelRes)
                            NavigationBarItem(
                                selected = selected,
                                onClick = { currentTab = tab },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = label
                                    )
                                },
                                label = { Text(label, maxLines = 1) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = riyaColors.accent,
                                    selectedTextColor = riyaColors.accent,
                                    unselectedIconColor = riyaColors.textSecondary,
                                    unselectedTextColor = riyaColors.textSecondary,
                                    indicatorColor = riyaColors.statusInfoBg
                                ),
                                modifier = Modifier.testTag(tab.testTag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(riyaColors.bgBase)
            ) {
                if (useNavRail) {
                    NavigationRail(
                        containerColor = riyaColors.bgSurface,
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        RiyaTab.entries.forEach { tab ->
                            val selected = currentTab == tab
                            val label = stringResource(tab.labelRes)
                            NavigationRailItem(
                                selected = selected,
                                onClick = { currentTab = tab },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = label
                                    )
                                },
                                label = { Text(label) },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = riyaColors.accent,
                                    selectedTextColor = riyaColors.accent,
                                    unselectedIconColor = riyaColors.textSecondary,
                                    unselectedTextColor = riyaColors.textSecondary,
                                    indicatorColor = riyaColors.statusInfoBg
                                ),
                                modifier = Modifier.testTag(tab.testTag)
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Column(
                        modifier = Modifier
                            .widthIn(max = 840.dp)
                            .fillMaxSize()
                    ) {
                        when (currentTab) {
                            RiyaTab.CHAT -> ChatScreen(
                                messagesState = messagesState,
                                profile = profile,
                                voiceSnapshot = voiceSnapshot,
                                dictatedTextEvent = dictatedTextEvent,
                                isSending = isSending,
                                streamingText = streamingText,
                                errorMessage = errorMessage,
                                statusMessage = statusMessage,
                                pendingConfirmation = pendingConfirmation,
                                activeTaskProgress = activeTaskProgress,
                                onSendMessage = { viewModel.sendMessage(it) },
                                onConsumeDictatedText = { viewModel.consumeDictatedText() },
                                onToggleDictation = { viewModel.toggleDictation() },
                                onCancelVoiceCapture = { viewModel.cancelVoiceCapture() },
                                onRegenerate = { viewModel.regenerateLastResponse() },
                                onClearChat = { viewModel.clearConversation() },
                                onToggleVoiceMode = { viewModel.toggleVoiceMode() },
                                onTogglePause = { viewModel.toggleVoicePause() },
                                onStartPushToTalk = { viewModel.startPushToTalk() },
                                onStopPushToTalk = { viewModel.stopPushToTalk() },
                                onInterruptVoice = { viewModel.interruptVoice() },
                                onDismissError = {
                                    viewModel.dismissError()
                                    viewModel.clearVoiceError()
                                },
                                onDismissStatus = { viewModel.dismissStatus() },
                                onSelectModel = { viewModel.selectPreferredModel(it) },
                                onReportVoicePermissionDenied = {
                                    viewModel.reportError("Microphone permission is required for Voice-to-Text and Voice Mode.")
                                },
                                onNotifyCopied = { viewModel.notifyMessage("Copied to clipboard.") }
                            )

                            RiyaTab.MEMORIES -> MemoriesScreen(
                                memoriesState = memoriesState,
                                pendingConfirmation = pendingConfirmation,
                                errorMessage = errorMessage,
                                statusMessage = statusMessage,
                                onSaveMemory = { cat, k, v -> viewModel.saveMemory(cat, k, v) },
                                onDeleteMemory = { viewModel.deleteMemory(it) },
                                onClearAllMemories = { viewModel.clearAllMemories() },
                                onDismissError = { viewModel.dismissError() },
                                onDismissStatus = { viewModel.dismissStatus() }
                            )

                            RiyaTab.TASKS -> TasksScreen(
                                tasksState = tasksState,
                                remindersState = remindersState,
                                notesState = notesState,
                                autonomousRunsState = runsState,
                                pendingConfirmation = pendingConfirmation,
                                activeTaskProgress = activeTaskProgress,
                                errorMessage = errorMessage,
                                statusMessage = statusMessage,
                                skillOutputTitle = skillTitle,
                                skillOutputContent = skillContent,
                                isSkillRunning = isSkillRunning,
                                onCreateTask = { t, d, p, c, due -> viewModel.createTask(t, d, p, c, due) },
                                onToggleTask = { viewModel.toggleTaskCompleted(it) },
                                onDeleteTask = { viewModel.deleteTask(it) },
                                onCreateReminder = { t, tr, rec -> viewModel.createReminder(t, tr, rec) },
                                onToggleReminder = { viewModel.toggleReminderTriggered(it) },
                                onDeleteReminder = { viewModel.deleteReminder(it) },
                                onCreateNote = { t, c, cat -> viewModel.createNote(t, c, cat) },
                                onToggleNotePin = { viewModel.toggleNotePin(it) },
                                onDeleteNote = { viewModel.deleteNote(it) },
                                onStartAutonomousTask = { viewModel.startAutonomousTask(it) },
                                onRunSkill = { skill, input -> viewModel.runBuiltInSkill(skill, input) },
                                onDismissSkillOutput = { viewModel.dismissSkillOutput() },
                                onDismissError = { viewModel.dismissError() },
                                onDismissStatus = { viewModel.dismissStatus() }
                            )

                            RiyaTab.STUDIO -> StudioScreen(
                                currentUserId = currentUserId,
                                modelName = profile.preferredModel,
                                imageState = imageGenState,
                                audioState = audioTranscribeState,
                                errorMessage = errorMessage,
                                statusMessage = statusMessage,
                                onGenerateOrEditImage = { prompt, bmp -> viewModel.generateOrEditImage(prompt, bmp) },
                                onClearImageResult = { viewModel.clearImageStudio() },
                                onStartAudioRecording = { viewModel.startAudioRecording() },
                                onStopAndTranscribeAudio = { viewModel.stopAndTranscribeAudio() },
                                onCancelAudioRecording = { viewModel.cancelAudioRecording() },
                                onClearAudioResult = { viewModel.clearAudioTranscribe() },
                                onSendToChat = { text ->
                                    currentTab = RiyaTab.CHAT
                                    viewModel.sendMessage(text)
                                },
                                onSaveTranscriptAsNote = { transcript ->
                                    viewModel.createNote("Voice Transcript", transcript, "voice")
                                },
                                onNotifyCopied = { viewModel.notifyMessage("Copied to clipboard.") },
                                onReportError = { viewModel.reportError(it) },
                                onDismissError = { viewModel.dismissError() },
                                onDismissStatus = { viewModel.dismissStatus() }
                            )

                            RiyaTab.PERMISSIONS -> PermissionsScreen(
                                rulesState = rulesState,
                                auditLogsState = auditLogsState,
                                errorMessage = errorMessage,
                                statusMessage = statusMessage,
                                onSaveRule = { k, d, l, r -> viewModel.savePermissionRule(k, d, l, r) },
                                onDeleteRule = { viewModel.deletePermissionRule(it) },
                                onDismissError = { viewModel.dismissError() },
                                onDismissStatus = { viewModel.dismissStatus() }
                            )

                            RiyaTab.DIAGNOSTICS -> DiagnosticsScreen(
                                checks = diagChecks,
                                summaryText = diagSummary,
                                voiceSnapshot = voiceSnapshot,
                                pendingConfirmation = pendingConfirmation,
                                errorMessage = errorMessage,
                                onRunDiagnostics = { viewModel.runDiagnostics(it) },
                                onDismissError = { viewModel.dismissError() }
                            )

                            RiyaTab.SETTINGS -> SettingsScreen(
                                profile = profile,
                                telemetry = telemetry,
                                pendingConfirmation = pendingConfirmation,
                                errorMessage = errorMessage,
                                statusMessage = statusMessage,
                                onUpdateTheme = { viewModel.updateThemeMode(it) },
                                onUpdatePreferences = { m, l, s, w, mem, vr ->
                                    viewModel.updatePreferences(m, l, s, w, mem, vr)
                                },
                                onSelectModel = { viewModel.selectPreferredModel(it) },
                                onTestSpeaker = { viewModel.testSpeakerAudio() },
                                onClearAllMemories = { viewModel.clearAllMemories() },
                                onDismissError = { viewModel.dismissError() },
                                onDismissStatus = { viewModel.dismissStatus() }
                            )
                        }
                    }
                }
            }
        }
    }
}
