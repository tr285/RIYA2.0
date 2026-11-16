package com.example.ui.screens

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.StatFs
import android.speech.SpeechRecognizer
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.model.DiagnosticCheck
import com.example.data.model.PendingConfirmation
import com.example.ui.DeviceHealthInput
import com.example.ui.components.ConfirmationBanner
import com.example.ui.components.ErrorBanner
import com.example.ui.components.StatusPill
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalRiyaColors
import com.example.voice.VoiceSnapshot
import com.google.firebase.Firebase
import com.google.firebase.auth.auth

@Composable
fun DiagnosticsScreen(
    checks: List<DiagnosticCheck>,
    summaryText: String,
    voiceSnapshot: VoiceSnapshot,
    pendingConfirmation: PendingConfirmation?,
    errorMessage: String?,
    onRunDiagnostics: (DeviceHealthInput) -> Unit,
    onDismissError: () -> Unit
) {
    val riyaColors = LocalRiyaColors.current
    val context = LocalContext.current

    fun collectAndRun() {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am?.getMemoryInfo(memInfo)

        val statFs = StatFs(context.filesDir.absolutePath)
        val totalStorageMb = (statFs.blockCountLong * statFs.blockSizeLong) / (1024L * 1024L)
        val freeStorageMb = (statFs.availableBlocksLong * statFs.blockSizeLong) / (1024L * 1024L)
        val user = Firebase.auth.currentUser

        onRunDiagnostics(
            DeviceHealthInput(
                manufacturerModel = "${Build.MANUFACTURER} ${Build.MODEL}",
                androidVersion = Build.VERSION.RELEASE ?: "Unknown",
                sdkInt = Build.VERSION.SDK_INT,
                totalRamMb = memInfo.totalMem / (1024L * 1024L),
                availRamMb = memInfo.availMem / (1024L * 1024L),
                freeStorageMb = freeStorageMb,
                totalStorageMb = totalStorageMb,
                isSignedIn = user != null,
                userEmail = user?.email,
                appCheckConfigured = true,
                sttAvailable = SpeechRecognizer.isRecognitionAvailable(context),
                ttsReady = voiceSnapshot.ttsReady
            )
        )
    }

    LaunchedEffect(Unit) {
        if (checks.isEmpty()) {
            collectAndRun()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(riyaColors.bgBase)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "System Status & Diagnostics",
                    style = MaterialTheme.typography.titleLarge,
                    color = riyaColors.textPrimary
                )
                Text(
                    text = "Live Android hardware, voice engine, App Check, and AI telemetry",
                    style = MaterialTheme.typography.bodyMedium,
                    color = riyaColors.textSecondary
                )
            }
            Button(
                onClick = { collectAndRun() },
                colors = ButtonDefaults.buttonColors(containerColor = riyaColors.accent, contentColor = Color.White),
                modifier = Modifier
                    .defaultMinSize(minHeight = 48.dp)
                    .testTag("rerun_diagnostics_button")
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Re-run")
            }
        }

        if (pendingConfirmation != null) ConfirmationBanner(pendingConfirmation)
        if (errorMessage != null) ErrorBanner(errorMessage, onDismissError)

        LazyColumn(
            contentPadding = PaddingValues(vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(checks, key = { it.name }) { check ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = riyaColors.bgCard),
                    border = BorderStroke(1.dp, riyaColors.border),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
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
                                    imageVector = Icons.Filled.HealthAndSafety,
                                    contentDescription = null,
                                    tint = riyaColors.accent,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(check.name, style = MaterialTheme.typography.titleMedium, color = riyaColors.textPrimary)
                            }
                            StatusPill(
                                text = check.status,
                                bgColor = when (check.status) {
                                    "PASSED" -> riyaColors.statusSuccessBg
                                    "WARNING" -> riyaColors.statusWarningBg
                                    else -> riyaColors.statusErrorBg
                                },
                                fgColor = when (check.status) {
                                    "PASSED" -> riyaColors.statusSuccessFg
                                    "WARNING" -> riyaColors.statusWarningFg
                                    else -> riyaColors.statusErrorFg
                                }
                            )
                        }
                        Text(check.summary, style = MaterialTheme.typography.bodyLarge, color = riyaColors.textPrimary)
                        Text(check.detail, style = MaterialTheme.typography.bodyMedium, color = riyaColors.textSecondary)
                    }
                }
            }

            if (summaryText.isNotBlank()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = riyaColors.codeBg),
                        border = BorderStroke(1.dp, riyaColors.border),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = summaryText,
                            fontFamily = JetBrainsMonoFontFamily,
                            style = MaterialTheme.typography.bodyMedium,
                            color = riyaColors.codeFg,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }
            }
        }
    }
}
