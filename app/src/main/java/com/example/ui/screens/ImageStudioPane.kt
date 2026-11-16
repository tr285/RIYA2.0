package com.example.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.ui.ImageGenUiState
import com.example.ui.components.ErrorBanner
import com.example.ui.theme.LocalRiyaColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ImageStudioPane(
    imageState: ImageGenUiState,
    onGenerateOrEdit: (prompt: String, inputBitmap: Bitmap?) -> Unit,
    onClearResult: () -> Unit,
    onSendPromptToChat: (String) -> Unit,
    onError: (String) -> Unit
) {
    val riyaColors = LocalRiyaColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var prompt by remember { mutableStateOf("") }
    var aspectHint by remember { mutableStateOf("1:1") }
    var selectedInputBitmap by remember { mutableStateOf<Bitmap?>(null) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val decoded = decodeSampledBitmapFromUri(context, uri, maxDim = 1024)
                if (decoded != null) {
                    selectedInputBitmap = decoded
                } else {
                    onError("Unable to load the selected photo.")
                }
            }
        }
    }

    val quickIdeas = listOf(
        "Minimalist futuristic desk setup with warm cyan ambient lighting",
        "Watercolor botanical illustration of a lotus in morning light",
        "Isometric 3D icon of a glowing neural core on dark slate"
    )

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
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = if (selectedInputBitmap != null) "Edit Selected Photo" else "Create Image with Gemini",
                    style = MaterialTheme.typography.titleMedium,
                    color = riyaColors.accent
                )
                Text(
                    text = "Powered by gemini-nano-banana-2.1 via Firebase AI Logic. Pick a photo to edit it, or describe a new scene from scratch.",
                    style = MaterialTheme.typography.bodySmall,
                    color = riyaColors.textSecondary
                )

                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    label = {
                        Text(
                            if (selectedInputBitmap != null) "Describe the edits to apply"
                            else "Describe the image to create"
                        )
                    },
                    placeholder = {
                        Text(
                            if (selectedInputBitmap != null) "e.g., Turn the sky into a golden sunset"
                            else "e.g., Neon glass bonsai tree on a dark oak table",
                            color = riyaColors.textMuted
                        )
                    },
                    minLines = 2,
                    maxLines = 4,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = riyaColors.bgInput,
                        unfocusedContainerColor = riyaColors.bgInput,
                        focusedBorderColor = riyaColors.accent,
                        unfocusedBorderColor = riyaColors.border,
                        focusedTextColor = riyaColors.textPrimary,
                        unfocusedTextColor = riyaColors.textPrimary
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("image_prompt_input")
                )

                if (selectedInputBitmap == null) {
                    Text(
                        text = "Framing Hint",
                        style = MaterialTheme.typography.labelMedium,
                        color = riyaColors.textSecondary
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("1:1" to "Square 1:1", "16:9" to "Wide 16:9", "9:16" to "Portrait 9:16").forEach { (ratio, label) ->
                            FilterChip(
                                selected = aspectHint == ratio,
                                onClick = { aspectHint = ratio },
                                label = { Text(label) }
                            )
                        }
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        quickIdeas.forEachIndexed { idx, idea ->
                            FilterChip(
                                selected = false,
                                onClick = { prompt = idea },
                                label = { Text("Idea ${idx + 1}") }
                            )
                        }
                    }
                }

                // Selected reference photo preview for editing
                selectedInputBitmap?.let { bmp ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = riyaColors.bgSurface),
                        border = BorderStroke(1.dp, riyaColors.accent.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Edit Input (${bmp.width}x${bmp.height})",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = riyaColors.accent
                                )
                                IconButton(
                                    onClick = { selectedInputBitmap = null },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Close,
                                        contentDescription = "Remove selected photo",
                                        tint = riyaColors.textSecondary
                                    )
                                }
                            }
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = "Selected photo to edit",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 180.dp)
                                    .clip(RoundedCornerShape(10.dp))
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("image_pick_photo_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PhotoLibrary,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (selectedInputBitmap == null) "Pick Photo" else "Change Photo")
                    }

                    Button(
                        onClick = {
                            val effectivePrompt = if (selectedInputBitmap == null && prompt.isNotBlank()) {
                                "${prompt.trim()} (Aspect framing: $aspectHint)"
                            } else {
                                prompt.trim()
                            }
                            onGenerateOrEdit(effectivePrompt, selectedInputBitmap)
                        },
                        enabled = prompt.isNotBlank() && imageState !is ImageGenUiState.Loading,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = riyaColors.accent,
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("image_generate_button")
                    ) {
                        Icon(
                            imageVector = if (selectedInputBitmap != null) Icons.Filled.Edit else Icons.Filled.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (selectedInputBitmap != null) "Apply Edit" else "Generate")
                    }
                }
            }
        }

        // Output Section
        when (imageState) {
            ImageGenUiState.Idle -> {
                Card(
                    colors = CardDefaults.cardColors(containerColor = riyaColors.bgSurface),
                    border = BorderStroke(1.dp, riyaColors.border),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Image,
                            contentDescription = null,
                            tint = riyaColors.textMuted,
                            modifier = Modifier.size(36.dp)
                        )
                        Text(
                            text = "Generated artwork will appear here",
                            style = MaterialTheme.typography.bodyMedium,
                            color = riyaColors.textSecondary
                        )
                    }
                }
            }

            ImageGenUiState.Loading -> {
                Card(
                    colors = CardDefaults.cardColors(containerColor = riyaColors.bgSurface),
                    border = BorderStroke(1.dp, riyaColors.accent.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(color = riyaColors.accent)
                        Text(
                            text = "Generating image with Gemini (may take 20-60s)...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = riyaColors.textPrimary
                        )
                    }
                }
            }

            is ImageGenUiState.Error -> {
                ErrorBanner(
                    message = imageState.message,
                    onDismiss = onClearResult
                )
            }

            is ImageGenUiState.Success -> {
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
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (imageState.isEdit) "Edited Result" else "Generated Image",
                                style = MaterialTheme.typography.titleMedium,
                                color = riyaColors.accent
                            )
                            IconButton(
                                onClick = onClearResult,
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "Dismiss image result",
                                    tint = riyaColors.textSecondary
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(riyaColors.bgSurface),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                bitmap = imageState.bitmap.asImageBitmap(),
                                contentDescription = "Generated image for ${imageState.prompt}",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 360.dp)
                            )
                        }
                        Text(
                            text = imageState.prompt,
                            style = MaterialTheme.typography.bodySmall,
                            color = riyaColors.textSecondary
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { selectedInputBitmap = imageState.bitmap },
                                modifier = Modifier
                                    .weight(1f)
                                    .defaultMinSize(minHeight = 48.dp)
                            ) {
                                Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Edit This")
                            }
                            Button(
                                onClick = {
                                    onSendPromptToChat("Let's discuss this image concept: ${imageState.prompt}")
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = riyaColors.accent,
                                    contentColor = Color.White
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .defaultMinSize(minHeight = 48.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Send to Chat")
                            }
                        }
                    }
                }
            }
        }
    }
}

private suspend fun decodeSampledBitmapFromUri(
    context: Context,
    uri: Uri,
    maxDim: Int
): Bitmap? = withContext(Dispatchers.IO) {
    runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, bounds)
        }
        val maxSide = max(bounds.outWidth, bounds.outHeight)
        var sampleSize = 1
        while (maxSide / sampleSize > maxDim * 2) {
            sampleSize *= 2
        }
        val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, opts)
        }
    }.getOrNull()
}
