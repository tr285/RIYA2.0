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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.ui.AudioTranscribeUiState
import com.example.ui.components.ErrorBanner
import com.example.ui.components.MarkdownText
import com.example.ui.theme.LocalRiyaColors

@Composable
fun AudioTranscribePane(
    audioState: AudioTranscribeUiState,
    modelName: String,
    onStartRecording: () -> Unit,
    onStopAndTranscribe: () -> Unit,
    onCancelRecording: () -> Unit,
    onClearResult: () -> Unit,
    onSendToChat: (String) -> Unit,
    onSaveAsNote: (String) -> Unit,
    onNotifyCopied: () -> Unit,
    onPermissionDenied: () -> Unit
) {
    val riyaColors = LocalRiyaColors.current
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    val micLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            onStartRecording()
        } else {
            onPermissionDenied()
        }
    }

    fun requestOrStart() {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            onStartRecording()
        } else {
            micLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = riyaColors.bgCard),
            border = BorderStroke(1.dp, riyaColors.border),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Voice Memo & Multimodal Transcription",
                    style = MaterialTheme.typography.titleMedium,
                    color = riyaColors.accent
                )
                Text(
                    text = "Record an AAC voice clip directly from your microphone and transcribe + summarize it using $modelName.",
                    style = MaterialTheme.typography.bodySmall,
                    color = riyaColors.textSecondary
                )

                when (audioState) {
                    is AudioTranscribeUiState.Recording -> {
                        val mins = audioState.elapsedSec / 60
                        val secs = audioState.elapsedSec % 60
                        val timerText = "%02d:%02d".format(mins, secs)

                        Card(
                            colors = CardDefaults.cardColors(containerColor = riyaColors.bgSurface),
                            border = BorderStroke(1.dp, riyaColors.statusErrorFg.copy(alpha = 0.6f)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.GraphicEq,
                                        contentDescription = "Recording active",
                                        tint = riyaColors.statusErrorFg
                                    )
                                    Text(
                                        text = "Recording $timerText",
                                        style = MaterialTheme.typography.titleLarge,
                                        color = riyaColors.textPrimary
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(10.dp)
                                        .clip(RoundedCornerShape(5.dp))
                                        .background(riyaColors.bgSurfaceElevated)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(audioState.amplitude.coerceIn(0.05f, 1f))
                                            .fillMaxHeight()
                                            .background(riyaColors.accent)
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = onCancelRecording,
                                modifier = Modifier
                                    .weight(1f)
                                    .defaultMinSize(minHeight = 48.dp)
                                    .testTag("audio_cancel_button")
                            ) {
                                Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Discard")
                            }
                            Button(
                                onClick = onStopAndTranscribe,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = riyaColors.statusErrorFg,
                                    contentColor = Color.White
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .defaultMinSize(minHeight = 48.dp)
                                    .testTag("audio_record_button")
                            ) {
                                Icon(Icons.Filled.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Stop & Transcribe")
                            }
                        }
                    }

                    AudioTranscribeUiState.Transcribing -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = riyaColors.accent,
                                strokeWidth = 2.5.dp
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Transcribing audio with Gemini...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = riyaColors.textPrimary
                            )
                        }
                    }

                    else -> {
                        Button(
                            onClick = { requestOrStart() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = riyaColors.accent,
                                contentColor = Color.White
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 48.dp)
                                .testTag("audio_record_button")
                        ) {
                            Icon(Icons.Filled.Mic, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Record Audio Clip")
                        }
                    }
                }
            }
        }

        if (audioState is AudioTranscribeUiState.Error) {
            ErrorBanner(
                message = audioState.message,
                onDismiss = onClearResult
            )
        }

        if (audioState is AudioTranscribeUiState.Success) {
            Card(
                colors = CardDefaults.cardColors(containerColor = riyaColors.bgCard),
                border = BorderStroke(1.dp, riyaColors.accent),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Transcript & Summary",
                        style = MaterialTheme.typography.titleMedium,
                        color = riyaColors.accent
                    )
                    MarkdownText(
                        markdown = audioState.transcript,
                        textColor = riyaColors.textPrimary
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                clipboard.setText(AnnotatedString(audioState.transcript))
                                onNotifyCopied()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = 48.dp)
                        ) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Copy")
                        }
                        OutlinedButton(
                            onClick = { onSaveAsNote(audioState.transcript) },
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = 48.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.NoteAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Save Note")
                        }
                    }
                    Button(
                        onClick = {
                            onSendToChat("Here is an audio transcript to analyze or act on:\n\n${audioState.transcript}")
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = riyaColors.accent,
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Send Transcript to RIYA Chat")
                    }
                }
            }
        }
    }
}
