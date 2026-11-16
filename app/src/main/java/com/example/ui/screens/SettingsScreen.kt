package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import com.example.ai.AiTelemetry
import com.example.auth.signOutUser
import com.example.data.model.PendingConfirmation
import com.example.data.model.RiyaCatalog
import com.example.data.model.UserProfile
import com.example.ui.components.ConfirmationBanner
import com.example.ui.components.ErrorBanner
import com.example.ui.components.StatusToastBanner
import com.example.ui.theme.LocalRiyaColors

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    profile: UserProfile,
    telemetry: AiTelemetry,
    pendingConfirmation: PendingConfirmation?,
    errorMessage: String?,
    statusMessage: String?,
    onUpdateTheme: (String) -> Unit,
    onUpdatePreferences: (
        defaultStartupMode: String?,
        voiceLanguage: String?,
        responseStyle: String?,
        waveformAnimationsEnabled: Boolean?,
        autoMemoryRecallEnabled: Boolean?,
        voiceResponsesEnabled: Boolean?
    ) -> Unit,
    onSelectModel: (String) -> Unit,
    onTestSpeaker: () -> Unit,
    onClearAllMemories: () -> Unit,
    onDismissError: () -> Unit,
    onDismissStatus: () -> Unit
) {
    val riyaColors = LocalRiyaColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(riyaColors.bgBase)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Preferences & Account",
            style = MaterialTheme.typography.titleLarge,
            color = riyaColors.textPrimary
        )

        if (pendingConfirmation != null) ConfirmationBanner(pendingConfirmation)
        if (errorMessage != null) ErrorBanner(errorMessage, onDismissError)
        if (statusMessage != null) StatusToastBanner(statusMessage, onDismissStatus)

        // Account & Isolation
        SettingsSectionCard(title = "Google Account & Cloud Isolation") {
            Text(profile.displayName, style = MaterialTheme.typography.titleMedium, color = riyaColors.textPrimary)
            if (!profile.email.isNullOrBlank()) {
                Text(profile.email, style = MaterialTheme.typography.bodyMedium, color = riyaColors.textSecondary)
            }
            Text(
                text = "All memories, chats, tasks, and audit entries are isolated to UID: ${profile.userId}",
                style = MaterialTheme.typography.labelSmall,
                color = riyaColors.textMuted
            )
            OutlinedButton(
                onClick = { signOutUser(credentialManager, scope) },
                border = BorderStroke(1.dp, riyaColors.statusErrorFg),
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 48.dp)
                    .testTag("sign_out_button")
            ) {
                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, tint = riyaColors.statusErrorFg, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Sign Out", color = riyaColors.statusErrorFg)
            }
        }

        // Appearance & Interface
        SettingsSectionCard(title = "Appearance & Interface") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Dark Mode", style = MaterialTheme.typography.titleMedium, color = riyaColors.textPrimary)
                    Text("Switch between Dark and Light M3 themes", style = MaterialTheme.typography.bodyMedium, color = riyaColors.textSecondary)
                }
                Switch(
                    checked = profile.themeMode == "dark",
                    onCheckedChange = { checked -> onUpdateTheme(if (checked) "dark" else "light") },
                    modifier = Modifier.testTag("theme_switch")
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("24-Bar Audio Waveform", style = MaterialTheme.typography.titleMedium, color = riyaColors.textPrimary)
                    Text("Render animated voice bars in Chat", style = MaterialTheme.typography.bodyMedium, color = riyaColors.textSecondary)
                }
                Switch(
                    checked = profile.waveformAnimationsEnabled,
                    onCheckedChange = { checked -> onUpdatePreferences(null, null, null, checked, null, null) }
                )
            }

            Text("Default Startup Mode", style = MaterialTheme.typography.labelLarge, color = riyaColors.textSecondary)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("text", "push_to_talk", "continuous").forEach { mode ->
                    FilterChip(
                        selected = profile.defaultStartupMode == mode,
                        onClick = { onUpdatePreferences(mode, null, null, null, null, null) },
                        label = { Text(mode.replace('_', ' ')) }
                    )
                }
            }
        }

        // Voice & Response Style
        SettingsSectionCard(title = "Voice & AI Personality") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Voice Responses (TTS)", style = MaterialTheme.typography.titleMedium, color = riyaColors.textPrimary)
                    Text("Speak assistant replies when using voice", style = MaterialTheme.typography.bodyMedium, color = riyaColors.textSecondary)
                }
                Switch(
                    checked = profile.voiceResponsesEnabled,
                    onCheckedChange = { checked -> onUpdatePreferences(null, null, null, null, null, checked) }
                )
            }

            Text("Speech Language", style = MaterialTheme.typography.labelLarge, color = riyaColors.textSecondary)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RiyaCatalog.VOICE_LANGUAGES.forEach { (code, label) ->
                    FilterChip(
                        selected = profile.voiceLanguage == code,
                        onClick = { onUpdatePreferences(null, code, null, null, null, null) },
                        label = { Text(label) }
                    )
                }
            }

            Text("Response Style", style = MaterialTheme.typography.labelLarge, color = riyaColors.textSecondary)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RiyaCatalog.RESPONSE_STYLES.forEach { style ->
                    FilterChip(
                        selected = profile.responseStyle == style,
                        onClick = { onUpdatePreferences(null, null, style, null, null, null) },
                        label = { Text(style) }
                    )
                }
            }

            OutlinedButton(
                onClick = onTestSpeaker,
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 48.dp)
                    .testTag("test_speaker_button")
            ) {
                Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Test Speaker Output")
            }
        }

        // Gemini Model Tier
        SettingsSectionCard(title = "Gemini Model Tier") {
            Text(
                text = "Select which Gemini model powers RIYA Chat, Built-in Skills, and Audio Transcription.",
                style = MaterialTheme.typography.bodySmall,
                color = riyaColors.textSecondary
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RiyaCatalog.MODEL_TIERS.forEach { (modelId, label, _) ->
                    FilterChip(
                        selected = profile.preferredModel == modelId,
                        onClick = { onSelectModel(modelId) },
                        label = { Text(label) },
                        modifier = Modifier.testTag("settings_model_$modelId")
                    )
                }
            }
        }

        // Memory & AI Telemetry
        SettingsSectionCard(title = "Memory Recall & AI Telemetry") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Auto-Memory Recall", style = MaterialTheme.typography.titleMedium, color = riyaColors.textPrimary)
                    Text("Include saved personal facts in Gemini context", style = MaterialTheme.typography.bodyMedium, color = riyaColors.textSecondary)
                }
                Switch(
                    checked = profile.autoMemoryRecallEnabled,
                    onCheckedChange = { checked -> onUpdatePreferences(null, null, null, null, checked, null) }
                )
            }

            Text(
                text = "Model: ${telemetry.modelName} • Calls: ${telemetry.totalGenerations} • Last: ${telemetry.lastLatencyMs}ms • Avg: ${telemetry.avgLatencyMs.toInt()}ms",
                style = MaterialTheme.typography.labelSmall,
                color = riyaColors.textSecondary
            )

            Button(
                onClick = onClearAllMemories,
                colors = ButtonDefaults.buttonColors(containerColor = riyaColors.statusErrorBg, contentColor = riyaColors.statusErrorFg),
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 48.dp)
            ) {
                Icon(Icons.Filled.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Clear All Stored Memories")
            }
        }

        // About RIYA
        SettingsSectionCard(title = "About RIYA for Android") {
            Text(
                text = "RIYA Personal AI Assistant v1.0.0 — Powered by Firebase AI Logic, Cloud Firestore Enterprise, App Check, and Android Speech/TTS.",
                style = MaterialTheme.typography.bodyMedium,
                color = riyaColors.textSecondary
            )
        }
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    val riyaColors = LocalRiyaColors.current
    Card(
        colors = CardDefaults.cardColors(containerColor = riyaColors.bgCard),
        border = BorderStroke(1.dp, riyaColors.border),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = riyaColors.accent)
            content()
        }
    }
}
