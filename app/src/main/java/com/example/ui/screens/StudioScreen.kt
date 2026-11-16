package com.example.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.ui.AudioTranscribeUiState
import com.example.ui.ImageGenUiState
import com.example.ui.components.ErrorBanner
import com.example.ui.components.StatusToastBanner
import com.example.ui.theme.LocalRiyaColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudioScreen(
    currentUserId: String,
    modelName: String,
    imageState: ImageGenUiState,
    audioState: AudioTranscribeUiState,
    errorMessage: String?,
    statusMessage: String?,
    onGenerateOrEditImage: (prompt: String, inputBitmap: Bitmap?) -> Unit,
    onClearImageResult: () -> Unit,
    onStartAudioRecording: () -> Unit,
    onStopAndTranscribeAudio: () -> Unit,
    onCancelAudioRecording: () -> Unit,
    onClearAudioResult: () -> Unit,
    onSendToChat: (String) -> Unit,
    onSaveTranscriptAsNote: (String) -> Unit,
    onNotifyCopied: () -> Unit,
    onReportError: (String) -> Unit,
    onDismissError: () -> Unit,
    onDismissStatus: () -> Unit
) {
    val riyaColors = LocalRiyaColors.current
    var selectedSection by rememberSaveable { mutableIntStateOf(0) }
    val sections = listOf("Images", "Audio", "Arcade")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(riyaColors.bgBase)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "RIYA Studio",
                style = MaterialTheme.typography.titleLarge,
                color = riyaColors.textPrimary
            )
            Text(
                text = modelName,
                style = MaterialTheme.typography.labelMedium,
                color = riyaColors.accent
            )
        }

        if (errorMessage != null) ErrorBanner(errorMessage, onDismissError)
        if (statusMessage != null) StatusToastBanner(statusMessage, onDismissStatus)

        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            sections.forEachIndexed { index, label ->
                SegmentedButton(
                    selected = selectedSection == index,
                    onClick = { selectedSection = index },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = sections.size),
                    modifier = Modifier.testTag("studio_tab_${label.lowercase()}")
                ) {
                    Text(label, maxLines = 1)
                }
            }
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (selectedSection) {
                0 -> ImageStudioPane(
                    imageState = imageState,
                    onGenerateOrEdit = onGenerateOrEditImage,
                    onClearResult = onClearImageResult,
                    onSendPromptToChat = onSendToChat,
                    onError = onReportError
                )

                1 -> AudioTranscribePane(
                    audioState = audioState,
                    modelName = modelName,
                    onStartRecording = onStartAudioRecording,
                    onStopAndTranscribe = onStopAndTranscribeAudio,
                    onCancelRecording = onCancelAudioRecording,
                    onClearResult = onClearAudioResult,
                    onSendToChat = onSendToChat,
                    onSaveAsNote = onSaveTranscriptAsNote,
                    onNotifyCopied = onNotifyCopied,
                    onPermissionDenied = {
                        onReportError("Microphone permission is required to record audio clips.")
                    }
                )

                2 -> PulseArcadeGame(currentUserId = currentUserId)
            }
        }
    }
}
