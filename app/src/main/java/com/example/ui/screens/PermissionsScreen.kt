package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.model.PermissionRule
import com.example.data.model.RiyaCatalog
import com.example.data.model.SecurityAuditEntry
import com.example.ui.UiState
import com.example.ui.components.ErrorBanner
import com.example.ui.components.StatusPill
import com.example.ui.components.StatusToastBanner
import com.example.ui.theme.LocalRiyaColors

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PermissionsScreen(
    rulesState: UiState<List<PermissionRule>>,
    auditLogsState: UiState<List<SecurityAuditEntry>>,
    errorMessage: String?,
    statusMessage: String?,
    onSaveRule: (permissionKey: String, decision: String, lifetime: String, reason: String) -> Unit,
    onDeleteRule: (PermissionRule) -> Unit,
    onDismissError: () -> Unit,
    onDismissStatus: () -> Unit
) {
    val riyaColors = LocalRiyaColors.current
    var selectedTab by remember { mutableIntStateOf(0) }
    var selectedKey by remember { mutableStateOf(RiyaCatalog.PERMISSION_KEYS.first().first) }
    var selectedDecision by remember { mutableStateOf("confirm") }
    var selectedLifetime by remember { mutableStateOf("session") }
    var persistentAck by remember { mutableStateOf(false) }
    var reasonText by remember { mutableStateOf("") }
    var showKeyDropdown by remember { mutableStateOf(false) }

    val rules = (rulesState as? UiState.Success)?.data.orEmpty()
    val auditLogs = (auditLogsState as? UiState.Success)?.data.orEmpty()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(riyaColors.bgBase)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Trust, Permissions & Audit",
            style = MaterialTheme.typography.titleLarge,
            color = riyaColors.textPrimary
        )

        if (errorMessage != null) ErrorBanner(errorMessage, onDismissError)
        if (statusMessage != null) StatusToastBanner(statusMessage, onDismissStatus)

        // Capability Rule Configurator Card
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
                    text = "Configure Capability Policy",
                    style = MaterialTheme.typography.titleMedium,
                    color = riyaColors.textPrimary
                )

                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { showKeyDropdown = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("permission_key_dropdown")
                    ) {
                        Text(
                            text = selectedKey,
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(Icons.Filled.ArrowDropDown, contentDescription = "Select capability key")
                    }
                    DropdownMenu(
                        expanded = showKeyDropdown,
                        onDismissRequest = { showKeyDropdown = false }
                    ) {
                        RiyaCatalog.PERMISSION_KEYS.forEach { (key, desc) ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(key, style = MaterialTheme.typography.labelLarge)
                                        Text(desc, style = MaterialTheme.typography.bodyMedium, color = riyaColors.textSecondary)
                                    }
                                },
                                onClick = {
                                    selectedKey = key
                                    showKeyDropdown = false
                                }
                            )
                        }
                    }
                }

                Text("Decision", style = MaterialTheme.typography.labelLarge, color = riyaColors.textSecondary)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RiyaCatalog.PERMISSION_DECISIONS.forEach { dec ->
                        FilterChip(
                            selected = selectedDecision == dec,
                            onClick = { selectedDecision = dec },
                            label = { Text(dec.uppercase()) }
                        )
                    }
                }

                Text("Lifetime", style = MaterialTheme.typography.labelLarge, color = riyaColors.textSecondary)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RiyaCatalog.PERMISSION_LIFETIMES.forEach { life ->
                        FilterChip(
                            selected = selectedLifetime == life,
                            onClick = {
                                selectedLifetime = life
                                if (life != "persistent") persistentAck = false
                            },
                            label = { Text(life.replace('_', ' ')) }
                        )
                    }
                }

                if (selectedLifetime == "persistent") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { persistentAck = !persistentAck }
                    ) {
                        Checkbox(
                            checked = persistentAck,
                            onCheckedChange = { persistentAck = it },
                            modifier = Modifier.testTag("persistent_ack_checkbox")
                        )
                        Text(
                            text = "I understand persistent rules stay active across sessions until revoked.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = riyaColors.statusWarningFg
                        )
                    }
                }

                OutlinedTextField(
                    value = reasonText,
                    onValueChange = { reasonText = it },
                    label = { Text("Reason (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                val canSave = selectedLifetime != "persistent" || persistentAck
                Button(
                    onClick = {
                        onSaveRule(selectedKey, selectedDecision, selectedLifetime, reasonText)
                        reasonText = ""
                    },
                    enabled = canSave,
                    colors = ButtonDefaults.buttonColors(containerColor = riyaColors.accent, contentColor = Color.White),
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("save_permission_rule_button")
                ) {
                    Icon(Icons.Filled.Security, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Save Permission Rule")
                }
            }
        }

        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = riyaColors.bgSurface,
            contentColor = riyaColors.accent
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("Active Rules (${rules.size})") }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("Audit Log (${auditLogs.size})") }
            )
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (selectedTab == 0) {
                if (rules.isEmpty()) {
                    Text(
                        text = "No custom rules saved yet. High-risk actions ask for your explicit approval.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = riyaColors.textSecondary,
                        modifier = Modifier.padding(12.dp)
                    )
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(rules, key = { it.id }) { rule ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = riyaColors.bgCard),
                                border = BorderStroke(1.dp, riyaColors.border),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(rule.permissionKey, style = MaterialTheme.typography.titleMedium, color = riyaColors.textPrimary)
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            StatusPill(
                                                text = rule.decision.uppercase(),
                                                bgColor = when (rule.decision) {
                                                    "allow" -> riyaColors.statusSuccessBg
                                                    "deny" -> riyaColors.statusErrorBg
                                                    else -> riyaColors.statusWarningBg
                                                },
                                                fgColor = when (rule.decision) {
                                                    "allow" -> riyaColors.statusSuccessFg
                                                    "deny" -> riyaColors.statusErrorFg
                                                    else -> riyaColors.statusWarningFg
                                                }
                                            )
                                            StatusPill(rule.lifetime.replace('_', ' '), riyaColors.bgSurfaceElevated, riyaColors.textSecondary)
                                        }
                                        if (rule.reason.isNotBlank()) {
                                            Text(rule.reason, style = MaterialTheme.typography.bodyMedium, color = riyaColors.textSecondary)
                                        }
                                    }
                                    IconButton(onClick = { onDeleteRule(rule) }, modifier = Modifier.size(48.dp)) {
                                        Icon(Icons.Filled.Delete, contentDescription = "Delete rule", tint = riyaColors.statusErrorFg)
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(auditLogs, key = { it.id }) { entry ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = riyaColors.bgCard),
                            border = BorderStroke(1.dp, riyaColors.border),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.History,
                                    contentDescription = null,
                                    tint = riyaColors.accent,
                                    modifier = Modifier.size(18.dp)
                                )
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text(entry.permissionKey, style = MaterialTheme.typography.titleMedium, color = riyaColors.textPrimary)
                                        StatusPill(
                                            text = entry.decision.uppercase(),
                                            bgColor = if (entry.decision == "allow") riyaColors.statusSuccessBg else if (entry.decision == "deny") riyaColors.statusErrorBg else riyaColors.statusWarningBg,
                                            fgColor = if (entry.decision == "allow") riyaColors.statusSuccessFg else if (entry.decision == "deny") riyaColors.statusErrorFg else riyaColors.statusWarningFg
                                        )
                                    }
                                    Text(entry.reason, style = MaterialTheme.typography.bodyMedium, color = riyaColors.textSecondary)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
