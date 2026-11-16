package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.model.MemoryFact
import com.example.data.model.PendingConfirmation
import com.example.data.model.RiyaCatalog
import com.example.ui.UiState
import com.example.ui.components.ConfirmationBanner
import com.example.ui.components.ErrorBanner
import com.example.ui.components.InfoNoticeBanner
import com.example.ui.components.StatusPill
import com.example.ui.components.StatusToastBanner
import com.example.ui.theme.LocalRiyaColors

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MemoriesScreen(
    memoriesState: UiState<List<MemoryFact>>,
    pendingConfirmation: PendingConfirmation?,
    errorMessage: String?,
    statusMessage: String?,
    onSaveMemory: (category: String, key: String, value: String) -> Unit,
    onDeleteMemory: (MemoryFact) -> Unit,
    onClearAllMemories: () -> Unit,
    onDismissError: () -> Unit,
    onDismissStatus: () -> Unit
) {
    val riyaColors = LocalRiyaColors.current
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("all") }
    var showAddDialog by remember { mutableStateOf(false) }
    var memoryToForget by remember { mutableStateOf<MemoryFact?>(null) }

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
                    text = "Personal Memories",
                    style = MaterialTheme.typography.titleLarge,
                    color = riyaColors.textPrimary
                )
                Text(
                    text = "Structured facts recalled by RIYA during conversations & planning",
                    style = MaterialTheme.typography.bodyMedium,
                    color = riyaColors.textSecondary
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onClearAllMemories,
                    modifier = Modifier
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("clear_memories_button")
                ) {
                    Icon(Icons.Filled.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Clear")
                }
                Button(
                    onClick = { showAddDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = riyaColors.accent, contentColor = Color.White),
                    modifier = Modifier
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("add_memory_button")
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Remember")
                }
            }
        }

        InfoNoticeBanner(
            message = "Memories are isolated to your Google account and only shared with Gemini when Auto-Memory Recall is enabled."
        )

        if (pendingConfirmation != null) ConfirmationBanner(pendingConfirmation)
        if (errorMessage != null) ErrorBanner(errorMessage, onDismissError)
        if (statusMessage != null) StatusToastBanner(statusMessage, onDismissStatus)

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search memories by key or value...") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("memory_search_input")
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            RiyaCatalog.MEMORY_CATEGORIES.forEach { cat ->
                FilterChip(
                    selected = selectedCategory == cat,
                    onClick = { selectedCategory = cat },
                    label = { Text(cat.replace('_', ' ')) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = riyaColors.statusInfoBg,
                        selectedLabelColor = riyaColors.statusInfoFg
                    )
                )
            }
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (memoriesState) {
                UiState.Loading -> CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = riyaColors.accent
                )
                is UiState.Error -> ErrorBanner(memoriesState.message, {})
                is UiState.Success -> {
                    val filtered = remember(memoriesState.data, searchQuery, selectedCategory) {
                        memoriesState.data.filter { fact ->
                            val matchesCat = selectedCategory == "all" || fact.category == selectedCategory
                            val q = searchQuery.trim().lowercase()
                            val matchesSearch = q.isEmpty() ||
                                fact.key.lowercase().contains(q) ||
                                fact.value.lowercase().contains(q)
                            matchesCat && matchesSearch
                        }
                    }

                    if (filtered.isEmpty()) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = riyaColors.bgCard),
                            border = BorderStroke(1.dp, riyaColors.border),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Psychology,
                                    contentDescription = null,
                                    tint = riyaColors.accent,
                                    modifier = Modifier.size(36.dp)
                                )
                                Text(
                                    text = "No matching memories yet",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = riyaColors.textPrimary
                                )
                                Text(
                                    text = "Tap Remember to save facts, work preferences, or instructions.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = riyaColors.textSecondary
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(filtered, key = { it.id }) { fact ->
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = riyaColors.bgCard),
                                    border = BorderStroke(1.dp, riyaColors.border),
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(14.dp),
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Column(
                                            modifier = Modifier.weight(1f),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            StatusPill(
                                                text = fact.category.replace('_', ' '),
                                                bgColor = riyaColors.statusInfoBg,
                                                fgColor = riyaColors.statusInfoFg
                                            )
                                            Text(
                                                text = fact.key,
                                                style = MaterialTheme.typography.titleMedium,
                                                color = riyaColors.textPrimary
                                            )
                                            Text(
                                                text = fact.value,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = riyaColors.textSecondary
                                            )
                                        }
                                        IconButton(
                                            onClick = { memoryToForget = fact },
                                            modifier = Modifier.size(48.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Delete,
                                                contentDescription = "Forget ${fact.key}",
                                                tint = riyaColors.statusErrorFg
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        var newKey by remember { mutableStateOf("") }
        var newValue by remember { mutableStateOf("") }
        var newCategory by remember { mutableStateOf("preferences") }

        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Remember Fact") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        RiyaCatalog.MEMORY_CATEGORIES.filter { it != "all" }.forEach { cat ->
                            FilterChip(
                                selected = newCategory == cat,
                                onClick = { newCategory = cat },
                                label = { Text(cat.replace('_', ' ')) }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = newKey,
                        onValueChange = { newKey = it },
                        label = { Text("Fact Key (e.g., preferred_language)") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("memory_key_input")
                    )
                    OutlinedTextField(
                        value = newValue,
                        onValueChange = { newValue = it },
                        label = { Text("Fact Value") },
                        minLines = 2,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("memory_value_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onSaveMemory(newCategory, newKey, newValue)
                        showAddDialog = false
                    },
                    enabled = newKey.isNotBlank() && newValue.isNotBlank(),
                    modifier = Modifier.testTag("save_memory_confirm_button")
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) { Text("Cancel") }
            }
        )
    }

    val target = memoryToForget
    if (target != null) {
        AlertDialog(
            onDismissRequest = { memoryToForget = null },
            title = { Text("Forget Memory?") },
            text = { Text("Delete '${target.key}' from your persistent RIYA memories?") },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteMemory(target)
                        memoryToForget = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = riyaColors.statusErrorFg)
                ) {
                    Text("Forget")
                }
            },
            dismissButton = {
                TextButton(onClick = { memoryToForget = null }) { Text("Cancel") }
            }
        )
    }
}
