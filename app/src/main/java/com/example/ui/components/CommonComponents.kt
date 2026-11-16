package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.example.data.model.PendingConfirmation
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalRiyaColors
import com.example.voice.VoiceState

@Composable
fun WaveformVisualizer(
    bars: List<Float>,
    voiceState: VoiceState,
    modifier: Modifier = Modifier
) {
    val riyaColors = LocalRiyaColors.current
    val activeColor = when (voiceState) {
        VoiceState.LISTENING -> riyaColors.waveformMicLive
        VoiceState.SPEAKING -> riyaColors.waveformActive
        VoiceState.TRANSCRIBING, VoiceState.THINKING -> riyaColors.statusInfoFg
        VoiceState.ERROR -> riyaColors.statusErrorFg
        else -> riyaColors.waveformIdle
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(28.dp)
    ) {
        val count = bars.size.coerceAtLeast(1)
        val slotWidth = size.width / count
        val barWidth = (slotWidth * 0.56f).coerceAtLeast(3f)
        val centerY = size.height / 2f

        bars.forEachIndexed { index, rawAmp ->
            val amp = rawAmp.coerceIn(0.06f, 1f)
            val barHeight = (size.height * amp).coerceAtLeast(4f)
            val x = index * slotWidth + (slotWidth - barWidth) / 2f
            val y = centerY - barHeight / 2f
            drawRoundRect(
                color = activeColor,
                topLeft = Offset(x, y),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }
    }
}

@Composable
fun StatusPill(
    text: String,
    bgColor: Color,
    fgColor: Color,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        color = bgColor,
        shape = RoundedCornerShape(999.dp),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = fgColor,
                    modifier = Modifier.size(13.dp)
                )
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = fgColor
            )
        }
    }
}

@Composable
fun ConfirmationBanner(
    confirmation: PendingConfirmation,
    modifier: Modifier = Modifier
) {
    val riyaColors = LocalRiyaColors.current
    Card(
        colors = CardDefaults.cardColors(containerColor = riyaColors.statusWarningBg),
        border = BorderStroke(1.dp, riyaColors.statusWarningFg.copy(alpha = 0.65f)),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Security,
                    contentDescription = "Permission confirmation required",
                    tint = riyaColors.statusWarningFg,
                    modifier = Modifier.size(20.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Approval Required: ${confirmation.toolName}",
                        style = MaterialTheme.typography.titleMedium,
                        color = riyaColors.textPrimary
                    )
                    Text(
                        text = "Capability: ${confirmation.permissionKey}",
                        style = MaterialTheme.typography.labelSmall,
                        color = riyaColors.statusWarningFg
                    )
                }
            }
            Text(
                text = confirmation.summary,
                style = MaterialTheme.typography.bodyMedium,
                color = riyaColors.textPrimary
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)
            ) {
                OutlinedButton(
                    onClick = confirmation.onReject,
                    border = BorderStroke(1.dp, riyaColors.statusErrorFg),
                    modifier = Modifier
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("confirm_reject_button")
                ) {
                    Text("Deny", color = riyaColors.statusErrorFg)
                }
                Button(
                    onClick = confirmation.onApprove,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = riyaColors.statusSuccessFg,
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("confirm_approve_button")
                ) {
                    Text("Approve")
                }
            }
        }
    }
}

@Composable
fun TaskProgressBanner(
    message: String,
    modifier: Modifier = Modifier
) {
    val riyaColors = LocalRiyaColors.current
    Card(
        colors = CardDefaults.cardColors(containerColor = riyaColors.statusInfoBg),
        border = BorderStroke(1.dp, riyaColors.statusInfoFg.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                color = riyaColors.statusInfoFg,
                strokeWidth = 2.dp
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = riyaColors.statusInfoFg
            )
        }
    }
}

@Composable
fun ErrorBanner(
    message: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val riyaColors = LocalRiyaColors.current
    Card(
        colors = CardDefaults.cardColors(containerColor = riyaColors.statusErrorBg),
        border = BorderStroke(1.dp, riyaColors.statusErrorFg.copy(alpha = 0.6f)),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.ErrorOutline,
                contentDescription = "Error",
                tint = riyaColors.statusErrorFg,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = riyaColors.statusErrorFg,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Dismiss error",
                    tint = riyaColors.statusErrorFg
                )
            }
        }
    }
}

@Composable
fun StatusToastBanner(
    message: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val riyaColors = LocalRiyaColors.current
    Card(
        colors = CardDefaults.cardColors(containerColor = riyaColors.statusSuccessBg),
        border = BorderStroke(1.dp, riyaColors.statusSuccessFg.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = "Status",
                tint = riyaColors.statusSuccessFg,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = riyaColors.statusSuccessFg,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Dismiss status",
                    tint = riyaColors.statusSuccessFg
                )
            }
        }
    }
}

@Composable
fun InfoNoticeBanner(
    message: String,
    modifier: Modifier = Modifier
) {
    val riyaColors = LocalRiyaColors.current
    Card(
        colors = CardDefaults.cardColors(containerColor = riyaColors.bgSurfaceElevated),
        border = BorderStroke(1.dp, riyaColors.border),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Info,
                contentDescription = null,
                tint = riyaColors.accent,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = riyaColors.textSecondary
            )
        }
    }
}

@Composable
fun MarkdownText(
    markdown: String,
    textColor: Color,
    modifier: Modifier = Modifier
) {
    val riyaColors = LocalRiyaColors.current
    val lines = markdown.lines()
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            if (line.trimStart().startsWith("```")) {
                val codeLines = mutableListOf<String>()
                i++
                while (i < lines.size && !lines[i].trimStart().startsWith("```")) {
                    codeLines += lines[i]
                    i++
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(riyaColors.codeBg, RoundedCornerShape(8.dp))
                        .padding(10.dp)
                ) {
                    Text(
                        text = codeLines.joinToString("\n"),
                        fontFamily = JetBrainsMonoFontFamily,
                        style = MaterialTheme.typography.bodyMedium,
                        color = riyaColors.codeFg
                    )
                }
            } else if (line.startsWith("### ") || line.startsWith("## ") || line.startsWith("# ")) {
                val heading = line.trimStart('#', ' ')
                Text(
                    text = parseInlineMarkdown(heading, riyaColors.codeBg),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = textColor
                )
            } else if (line.trimStart().startsWith("- ") || line.trimStart().startsWith("* ")) {
                val itemText = line.trimStart().drop(2)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(text = "•", style = MaterialTheme.typography.bodyLarge, color = riyaColors.accent)
                    Text(
                        text = parseInlineMarkdown(itemText, riyaColors.codeBg),
                        style = MaterialTheme.typography.bodyLarge,
                        color = textColor
                    )
                }
            } else if (line.isNotBlank()) {
                Text(
                    text = parseInlineMarkdown(line, riyaColors.codeBg),
                    style = MaterialTheme.typography.bodyLarge,
                    color = textColor
                )
            }
            i++
        }
    }
}

private fun parseInlineMarkdown(text: String, codeBg: Color): AnnotatedString = buildAnnotatedString {
    var idx = 0
    while (idx < text.length) {
        if (text.startsWith("**", idx)) {
            val end = text.indexOf("**", idx + 2)
            if (end != -1) {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(text.substring(idx + 2, end))
                }
                idx = end + 2
                continue
            }
        }
        if (text[idx] == '`') {
            val end = text.indexOf('`', idx + 1)
            if (end != -1) {
                withStyle(SpanStyle(fontFamily = JetBrainsMonoFontFamily, background = codeBg)) {
                    append(text.substring(idx + 1, end))
                }
                idx = end + 1
                continue
            }
        }
        append(text[idx])
        idx++
    }
}
