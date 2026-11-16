package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.data.model.ChatMessage
import com.example.data.model.PendingConfirmation
import com.example.data.model.RiyaCatalog
import com.example.data.model.UserProfile
import com.example.ui.UiState
import com.example.ui.components.ConfirmationBanner
import com.example.ui.components.ErrorBanner
import com.example.ui.components.MarkdownText
import com.example.ui.components.StatusPill
import com.example.ui.components.StatusToastBanner
import com.example.ui.components.TaskProgressBanner
import com.example.ui.components.WaveformVisualizer
import com.example.ui.theme.LocalRiyaColors
import com.example.voice.VoiceSnapshot
import com.example.voice.VoiceState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChatScreen(
    messagesState: UiState<List<ChatMessage>>,
    profile: UserProfile,
    voiceSnapshot: VoiceSnapshot,
    dictatedTextEvent: String?,
    isSending: Boolean,
    streamingText: String,
    errorMessage: String?,
    statusMessage: String?,
    pendingConfirmation: PendingConfirmation?,
    activeTaskProgress: String?,
    onSendMessage: (String) -> Unit,
    onConsumeDictatedText: () -> Unit,
    onToggleDictation: () -> Unit,
    onCancelVoiceCapture: () -> Unit,
    onRegenerate: () -> Unit,
    onClearChat: () -> Unit,
    onToggleVoiceMode: () -> Unit,
    onTogglePause: () -> Unit,
    onStartPushToTalk: () -> Unit,
    onStopPushToTalk: () -> Unit,
    onInterruptVoice: () -> Unit,
    onDismissError: () -> Unit,
    onDismissStatus: () -> Unit,
    onSelectModel: (String) -> Unit,
    onReportVoicePermissionDenied: () -> Unit,
    onNotifyCopied: () -> Unit
) {
    val riyaColors = LocalRiyaColors.current
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val listState = rememberLazyListState()

    var inputText by remember { mutableStateOf("") }
    var modelMenuExpanded by remember { mutableStateOf(false) }
    var pendingMicAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        val action = pendingMicAction
        pendingMicAction = null
        if (granted) {
            action?.invoke()
        } else {
            onReportVoicePermissionDenied()
        }
    }

    fun runWithMicPermission(action: () -> Unit) {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            action()
        } else {
            pendingMicAction = action
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    val messages = (messagesState as? UiState.Success)?.data.orEmpty()

    LaunchedEffect(dictatedTextEvent) {
        val spoken = dictatedTextEvent?.trim()
        if (!spoken.isNullOrEmpty()) {
            inputText = if (inputText.isBlank()) {
                spoken
            } else {
                val sep = if (inputText.last().isWhitespace()) "" else " "
                "$inputText$sep$spoken"
            }
            onConsumeDictatedText()
        }
    }

    LaunchedEffect(messages.size, streamingText.length) {
        if (messages.isNotEmpty() || streamingText.isNotEmpty()) {
            val targetIndex = (messages.size + if (streamingText.isNotEmpty()) 1 else 0 - 1).coerceAtLeast(0)
            listState.animateScrollToItem(targetIndex)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(riyaColors.bgBase)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Header Status Strip + 24-Bar Waveform
        Card(
            colors = CardDefaults.cardColors(containerColor = riyaColors.bgSurface),
            border = BorderStroke(1.dp, riyaColors.border),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        StatusPill(
                            text = if (isSending) "AI: Streaming" else "AI: Ready",
                            bgColor = if (isSending) riyaColors.statusInfoBg else riyaColors.statusSuccessBg,
                            fgColor = if (isSending) riyaColors.statusInfoFg else riyaColors.statusSuccessFg,
                            icon = Icons.Filled.AutoAwesome
                        )
                        StatusPill(
                            text = "Voice: ${voiceSnapshot.voiceState.label}",
                            bgColor = riyaColors.bgSurfaceElevated,
                            fgColor = riyaColors.textSecondary,
                            icon = Icons.Outlined.RecordVoiceOver
                        )
                        StatusPill(
                            text = if (voiceSnapshot.micActive) "Mic: Live" else if (voiceSnapshot.isPaused) "Mic: Paused" else "Mic: Off",
                            bgColor = if (voiceSnapshot.micActive) riyaColors.statusSuccessBg else riyaColors.bgSurfaceElevated,
                            fgColor = if (voiceSnapshot.micActive) riyaColors.waveformMicLive else riyaColors.textMuted,
                            icon = if (voiceSnapshot.micActive) Icons.Filled.Mic else Icons.Filled.MicOff
                        )
                        Box {
                            val activeLabel = when (profile.preferredModel) {
                                "gemini-3.5-flash-lite" -> "3.5 Lite"
                                "gemini-4-argon" -> "4 Argon"
                                else -> "3.8 Flash"
                            }
                            AssistChip(
                                onClick = { modelMenuExpanded = true },
                                label = { Text(activeLabel, style = MaterialTheme.typography.labelMedium) },
                                trailingIcon = {
                                    Icon(
                                        imageVector = Icons.Filled.ArrowDropDown,
                                        contentDescription = "Select Gemini model",
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = riyaColors.bgSurfaceElevated,
                                    labelColor = riyaColors.accent,
                                    trailingIconContentColor = riyaColors.accent
                                ),
                                border = BorderStroke(1.dp, riyaColors.border),
                                modifier = Modifier
                                    .defaultMinSize(minHeight = 48.dp)
                                    .testTag("chat_model_selector")
                            )
                            DropdownMenu(
                                expanded = modelMenuExpanded,
                                onDismissRequest = { modelMenuExpanded = false }
                            ) {
                                RiyaCatalog.MODEL_TIERS.forEach { (modelId, label, subtitle) ->
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(
                                                    text = label,
                                                    style = MaterialTheme.typography.labelLarge,
                                                    color = if (modelId == profile.preferredModel) riyaColors.accent else riyaColors.textPrimary
                                                )
                                                Text(
                                                    text = subtitle,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = riyaColors.textSecondary
                                                )
                                            }
                                        },
                                        onClick = {
                                            modelMenuExpanded = false
                                            if (modelId != profile.preferredModel) {
                                                onSelectModel(modelId)
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                    if (messages.isNotEmpty()) {
                        IconButton(
                            onClick = onClearChat,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("clear_chat_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.DeleteSweep,
                                contentDescription = "Clear conversation",
                                tint = riyaColors.textSecondary
                            )
                        }
                    }
                }
                if (profile.waveformAnimationsEnabled) {
                    WaveformVisualizer(
                        bars = voiceSnapshot.waveformBars,
                        voiceState = voiceSnapshot.voiceState
                    )
                }
            }
        }

        // Dynamic Banners
        if (pendingConfirmation != null) {
            ConfirmationBanner(confirmation = pendingConfirmation)
        }
        if (activeTaskProgress != null) {
            TaskProgressBanner(message = activeTaskProgress)
        }
        if (errorMessage != null) {
            ErrorBanner(message = errorMessage, onDismiss = onDismissError)
        }
        if (voiceSnapshot.lastVoiceError != null) {
            ErrorBanner(message = voiceSnapshot.lastVoiceError, onDismiss = onDismissError)
        }
        if (statusMessage != null) {
            StatusToastBanner(message = statusMessage, onDismiss = onDismissStatus)
        }

        // Conversation Transcript
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (messagesState) {
                UiState.Loading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = riyaColors.accent
                    )
                }
                is UiState.Error -> {
                    ErrorBanner(
                        message = messagesState.message,
                        onDismiss = {},
                        modifier = Modifier.align(Alignment.TopCenter)
                    )
                }
                is UiState.Success -> {
                    if (messages.isEmpty() && streamingText.isEmpty()) {
                        EmptyConversationWelcome(
                            displayName = profile.displayName,
                            onSelectPrompt = { prompt -> onSendMessage(prompt) }
                        )
                    } else {
                        val lastAssistantId = messages.lastOrNull { it.role == "assistant" }?.id
                        LazyColumn(
                            state = listState,
                            contentPadding = PaddingValues(vertical = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            itemsIndexed(messages, key = { _, msg -> msg.id }) { _, msg ->
                                MessageBubble(
                                    message = msg,
                                    isLatestAssistant = msg.id == lastAssistantId && !isSending,
                                    onCopy = {
                                        clipboard.setText(AnnotatedString(msg.text))
                                        onNotifyCopied()
                                    },
                                    onRegenerate = onRegenerate
                                )
                            }
                            if (isSending) {
                                item(key = "streaming_assistant") {
                                    StreamingBubble(
                                        text = streamingText.ifBlank { "Thinking..." }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Voice Controls Strip
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilledTonalButton(
                onClick = {
                    if (voiceSnapshot.voiceModeActive) {
                        onToggleVoiceMode()
                    } else {
                        runWithMicPermission { onToggleVoiceMode() }
                    }
                },
                modifier = Modifier
                    .defaultMinSize(minHeight = 48.dp)
                    .weight(1f)
                    .testTag("voice_mode_button")
            ) {
                Icon(
                    imageVector = if (voiceSnapshot.voiceModeActive) Icons.Filled.MicOff else Icons.Filled.Mic,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (voiceSnapshot.voiceModeActive) "Stop Voice" else "Start Voice")
            }

            OutlinedButton(
                onClick = {
                    if (voiceSnapshot.isPushToTalkActive) {
                        onStopPushToTalk()
                    } else {
                        runWithMicPermission { onStartPushToTalk() }
                    }
                },
                modifier = Modifier
                    .defaultMinSize(minHeight = 48.dp)
                    .weight(1f)
                    .testTag("push_to_talk_button")
            ) {
                Icon(
                    imageVector = Icons.Outlined.RecordVoiceOver,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (voiceSnapshot.isPushToTalkActive) "Finish PTT" else "Push to Talk")
            }

            if (voiceSnapshot.voiceModeActive) {
                IconButton(
                    onClick = onTogglePause,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("voice_pause_button")
                ) {
                    Icon(
                        imageVector = if (voiceSnapshot.isPaused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                        contentDescription = if (voiceSnapshot.isPaused) "Resume voice" else "Pause voice",
                        tint = riyaColors.accent
                    )
                }
            }

            if (isSending || voiceSnapshot.assistantSpeaking || voiceSnapshot.micActive) {
                IconButton(
                    onClick = onInterruptVoice,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("voice_interrupt_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Stop,
                        contentDescription = "Interrupt response or voice",
                        tint = riyaColors.statusErrorFg
                    )
                }
            }
        }

        // Live Voice-to-Text Capture Card
        val isCapturingVoice = voiceSnapshot.micActive ||
            voiceSnapshot.isDictating ||
            voiceSnapshot.isPushToTalkActive ||
            voiceSnapshot.voiceState == VoiceState.TRANSCRIBING
        if (isCapturingVoice) {
            Card(
                colors = CardDefaults.cardColors(containerColor = riyaColors.bgSurface),
                border = BorderStroke(1.dp, riyaColors.accent),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("voice_capture_card")
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.GraphicEq,
                                contentDescription = "Microphone active",
                                tint = riyaColors.waveformMicLive,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = when {
                                    voiceSnapshot.voiceState == VoiceState.TRANSCRIBING -> "Transcribing speech..."
                                    voiceSnapshot.isDictating -> "Voice-to-Text Dictation"
                                    voiceSnapshot.isPushToTalkActive -> "Speak to Send"
                                    else -> "Hands-Free Listening"
                                },
                                style = MaterialTheme.typography.titleSmall,
                                color = riyaColors.accent
                            )
                        }
                        StatusPill(
                            text = if (voiceSnapshot.usingDirectMicFallback) "Mic PCM" else "Speech API",
                            bgColor = riyaColors.bgSurfaceElevated,
                            fgColor = riyaColors.textSecondary
                        )
                    }

                    Text(
                        text = voiceSnapshot.partialTranscript.ifBlank { "Speak clearly into your Android microphone..." },
                        style = MaterialTheme.typography.bodyMedium,
                        color = riyaColors.textPrimary
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onCancelVoiceCapture,
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = 48.dp)
                                .testTag("voice_capture_cancel_button")
                        ) {
                            Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Cancel")
                        }
                        if (voiceSnapshot.isDictating || voiceSnapshot.isPushToTalkActive) {
                            FilledTonalButton(
                                onClick = {
                                    if (voiceSnapshot.isDictating) onToggleDictation() else onStopPushToTalk()
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .defaultMinSize(minHeight = 48.dp)
                                    .testTag("voice_capture_done_button")
                            ) {
                                Icon(Icons.Filled.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (voiceSnapshot.isDictating) "Insert Text" else "Send Voice")
                            }
                        }
                    }
                }
            }
        }

        // Input Composer with Microphone Dictation Button
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                placeholder = { Text("Speak or type a message...", color = riyaColors.textMuted) },
                maxLines = 4,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(
                    onSend = {
                        if (inputText.isNotBlank() && !isSending) {
                            val textToSend = inputText
                            inputText = ""
                            onSendMessage(textToSend)
                        }
                    }
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = riyaColors.bgInput,
                    unfocusedContainerColor = riyaColors.bgInput,
                    focusedBorderColor = riyaColors.accent,
                    unfocusedBorderColor = riyaColors.border,
                    focusedTextColor = riyaColors.textPrimary,
                    unfocusedTextColor = riyaColors.textPrimary
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .weight(1f)
                    .testTag("chat_input")
            )
            FilledTonalIconButton(
                onClick = {
                    if (voiceSnapshot.isDictating) {
                        onToggleDictation()
                    } else {
                        runWithMicPermission { onToggleDictation() }
                    }
                },
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = if (voiceSnapshot.isDictating) riyaColors.statusErrorBg else riyaColors.bgSurfaceElevated,
                    contentColor = if (voiceSnapshot.isDictating) riyaColors.statusErrorFg else riyaColors.accent
                ),
                modifier = Modifier
                    .size(52.dp)
                    .testTag("chat_mic_dictate_button")
            ) {
                Icon(
                    imageVector = if (voiceSnapshot.isDictating) Icons.Filled.Stop else Icons.Filled.Mic,
                    contentDescription = if (voiceSnapshot.isDictating) {
                        "Stop voice dictation"
                    } else {
                        "Dictate message with microphone"
                    }
                )
            }
            FilledIconButton(
                onClick = {
                    if (inputText.isNotBlank() && !isSending) {
                        val textToSend = inputText
                        inputText = ""
                        onSendMessage(textToSend)
                    }
                },
                enabled = inputText.isNotBlank() && !isSending,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = riyaColors.accent,
                    contentColor = Color.White
                ),
                modifier = Modifier
                    .size(52.dp)
                    .testTag("send_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send message"
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EmptyConversationWelcome(
    displayName: String,
    onSelectPrompt: (String) -> Unit
) {
    val riyaColors = LocalRiyaColors.current
    val starterPrompts = listOf(
        "Prioritize my active tasks for today",
        "Help me brainstorm a Kotlin architecture",
        "Summarize what you know about me from memory",
        "Draft a concise study plan for this week"
    )
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 12.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            color = riyaColors.statusInfoBg,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.size(56.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    tint = riyaColors.statusInfoFg,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Hello, $displayName",
            style = MaterialTheme.typography.headlineMedium,
            color = riyaColors.textPrimary
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "RIYA is ready with your cloud-isolated memories, tasks, and voice controls.",
            style = MaterialTheme.typography.bodyMedium,
            color = riyaColors.textSecondary
        )
        Spacer(modifier = Modifier.height(18.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            starterPrompts.forEach { prompt ->
                AssistChip(
                    onClick = { onSelectPrompt(prompt) },
                    label = { Text(prompt, style = MaterialTheme.typography.labelLarge) },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = riyaColors.bgCard,
                        labelColor = riyaColors.textPrimary
                    ),
                    border = BorderStroke(1.dp, riyaColors.border)
                )
            }
        }
    }
}

@Composable
private fun MessageBubble(
    message: ChatMessage,
    isLatestAssistant: Boolean,
    onCopy: () -> Unit,
    onRegenerate: () -> Unit
) {
    val riyaColors = LocalRiyaColors.current
    val isUser = message.role == "user"
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (isUser) riyaColors.userBubbleBg else riyaColors.assistantBubbleBg
            ),
            border = if (isUser) null else BorderStroke(1.dp, riyaColors.border),
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp
            ),
            modifier = Modifier.widthIn(max = 520.dp)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = if (isUser) "You" else "RIYA",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isUser) Color.White.copy(alpha = 0.85f) else riyaColors.accent
                    )
                    if (!isUser && message.latencyMs > 0L) {
                        Text(
                            text = "${message.latencyMs} ms",
                            style = MaterialTheme.typography.labelSmall,
                            color = riyaColors.textMuted
                        )
                    }
                }
                if (isUser) {
                    Text(
                        text = message.text,
                        style = MaterialTheme.typography.bodyLarge,
                        color = riyaColors.userBubbleFg
                    )
                } else {
                    MarkdownText(
                        markdown = message.text,
                        textColor = riyaColors.assistantBubbleFg
                    )
                }
                if (!isUser) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = onCopy,
                            modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.ContentCopy,
                                contentDescription = "Copy response",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Copy")
                        }
                        if (isLatestAssistant) {
                            TextButton(
                                onClick = onRegenerate,
                                modifier = Modifier
                                    .defaultMinSize(minHeight = 48.dp)
                                    .testTag("regenerate_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Refresh,
                                    contentDescription = "Regenerate response",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Regenerate")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StreamingBubble(text: String) {
    val riyaColors = LocalRiyaColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = riyaColors.assistantBubbleBg),
            border = BorderStroke(1.dp, riyaColors.accent.copy(alpha = 0.6f)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.widthIn(max = 520.dp)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(12.dp),
                        color = riyaColors.accent,
                        strokeWidth = 2.dp
                    )
                    Text(
                        text = "RIYA (streaming)",
                        style = MaterialTheme.typography.labelSmall,
                        color = riyaColors.accent
                    )
                }
                MarkdownText(
                    markdown = text,
                    textColor = riyaColors.assistantBubbleFg
                )
            }
        }
    }
}
