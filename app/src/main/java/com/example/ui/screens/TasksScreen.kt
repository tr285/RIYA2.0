package com.example.ui.screens

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.example.data.model.AutonomousTaskRun
import com.example.data.model.BuiltInSkill
import com.example.data.model.PendingConfirmation
import com.example.data.model.PersonalNote
import com.example.data.model.PersonalTask
import com.example.data.model.ReminderItem
import com.example.data.model.RiyaCatalog
import com.example.ui.UiState
import com.example.ui.components.ConfirmationBanner
import com.example.ui.components.ErrorBanner
import com.example.ui.components.MarkdownText
import com.example.ui.components.StatusPill
import com.example.ui.components.StatusToastBanner
import com.example.ui.components.TaskProgressBanner
import com.example.ui.theme.LocalRiyaColors

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TasksScreen(
    tasksState: UiState<List<PersonalTask>>,
    remindersState: UiState<List<ReminderItem>>,
    notesState: UiState<List<PersonalNote>>,
    autonomousRunsState: UiState<List<AutonomousTaskRun>>,
    pendingConfirmation: PendingConfirmation?,
    activeTaskProgress: String?,
    errorMessage: String?,
    statusMessage: String?,
    skillOutputTitle: String?,
    skillOutputContent: String?,
    isSkillRunning: Boolean,
    onCreateTask: (title: String, description: String, priority: String, category: String, dueDateLabel: String) -> Unit,
    onToggleTask: (PersonalTask) -> Unit,
    onDeleteTask: (PersonalTask) -> Unit,
    onCreateReminder: (title: String, triggerLabel: String, recurringRule: String) -> Unit,
    onToggleReminder: (ReminderItem) -> Unit,
    onDeleteReminder: (ReminderItem) -> Unit,
    onCreateNote: (title: String, content: String, category: String) -> Unit,
    onToggleNotePin: (PersonalNote) -> Unit,
    onDeleteNote: (PersonalNote) -> Unit,
    onStartAutonomousTask: (String) -> Unit,
    onRunSkill: (BuiltInSkill, String) -> Unit,
    onDismissSkillOutput: () -> Unit,
    onDismissError: () -> Unit,
    onDismissStatus: () -> Unit
) {
    val riyaColors = LocalRiyaColors.current
    var subTab by remember { mutableIntStateOf(0) }
    var autonomousGoal by remember { mutableStateOf("") }
    var showTaskDialog by remember { mutableStateOf(false) }
    var showReminderDialog by remember { mutableStateOf(false) }
    var showNoteDialog by remember { mutableStateOf(false) }
    var activeSkillDialog by remember { mutableStateOf<BuiltInSkill?>(null) }

    val tasks = (tasksState as? UiState.Success)?.data.orEmpty()
    val reminders = (remindersState as? UiState.Success)?.data.orEmpty()
    val notes = (notesState as? UiState.Success)?.data.orEmpty()
    val runs = (autonomousRunsState as? UiState.Success)?.data.orEmpty()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(riyaColors.bgBase)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Daily Productivity Overview
        Card(
            colors = CardDefaults.cardColors(containerColor = riyaColors.bgCard),
            border = BorderStroke(1.dp, riyaColors.border),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Tasks, Skills & Productivity",
                    style = MaterialTheme.typography.titleLarge,
                    color = riyaColors.textPrimary
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val pendingTasks = tasks.count { it.status == "pending" || it.status == "in_progress" }
                    val completedTasks = tasks.count { it.status == "completed" }
                    val pendingReminders = reminders.count { it.status == "pending" }
                    StatusPill("$pendingTasks Open Tasks", riyaColors.statusInfoBg, riyaColors.statusInfoFg)
                    StatusPill("$completedTasks Done", riyaColors.statusSuccessBg, riyaColors.statusSuccessFg)
                    StatusPill("$pendingReminders Reminders", riyaColors.statusWarningBg, riyaColors.statusWarningFg)
                    StatusPill("${notes.size} Notes", riyaColors.bgSurfaceElevated, riyaColors.textSecondary)
                }
            }
        }

        // Autonomous Task Planner Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = autonomousGoal,
                onValueChange = { autonomousGoal = it },
                placeholder = { Text("Describe a multi-step goal for RIYA...") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .testTag("autonomous_goal_input")
            )
            Button(
                onClick = {
                    val goal = autonomousGoal
                    autonomousGoal = ""
                    onStartAutonomousTask(goal)
                },
                enabled = autonomousGoal.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = riyaColors.accent, contentColor = Color.White),
                modifier = Modifier
                    .defaultMinSize(minHeight = 52.dp)
                    .testTag("run_autonomous_task_button")
            ) {
                Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Plan & Run")
            }
        }

        if (pendingConfirmation != null) ConfirmationBanner(pendingConfirmation)
        if (activeTaskProgress != null) TaskProgressBanner(activeTaskProgress)
        if (errorMessage != null) ErrorBanner(errorMessage, onDismissError)
        if (statusMessage != null) StatusToastBanner(statusMessage, onDismissStatus)

        val tabs = listOf("Tasks", "Reminders & Notes", "Skills (8)", "Runs (${runs.size})")
        ScrollableTabRow(
            selectedTabIndex = subTab,
            containerColor = riyaColors.bgSurface,
            contentColor = riyaColors.accent,
            edgePadding = 4.dp
        ) {
            tabs.forEachIndexed { idx, label ->
                Tab(
                    selected = subTab == idx,
                    onClick = { subTab = idx },
                    text = { Text(label) }
                )
            }
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (subTab) {
                0 -> {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Button(
                                onClick = { showTaskDialog = true },
                                modifier = Modifier
                                    .defaultMinSize(minHeight = 48.dp)
                                    .testTag("add_task_button")
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("New Task")
                            }
                        }
                        LazyColumn(
                            contentPadding = PaddingValues(vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(tasks, key = { it.id }) { task ->
                                val isDone = task.status == "completed"
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = riyaColors.bgCard),
                                    border = BorderStroke(1.dp, riyaColors.border),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        IconButton(
                                            onClick = { onToggleTask(task) },
                                            modifier = Modifier.size(48.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (isDone) Icons.Filled.CheckBox else Icons.Filled.CheckBoxOutlineBlank,
                                                contentDescription = "Toggle task completion",
                                                tint = if (isDone) riyaColors.statusSuccessFg else riyaColors.accent
                                            )
                                        }
                                        Column(
                                            modifier = Modifier.weight(1f),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(
                                                text = task.title,
                                                style = MaterialTheme.typography.titleMedium,
                                                textDecoration = if (isDone) TextDecoration.LineThrough else null,
                                                color = if (isDone) riyaColors.textMuted else riyaColors.textPrimary
                                            )
                                            if (task.description.isNotBlank()) {
                                                Text(
                                                    text = task.description,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = riyaColors.textSecondary
                                                )
                                            }
                                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                StatusPill(
                                                    text = task.priority.uppercase(),
                                                    bgColor = if (task.priority == "urgent" || task.priority == "high") riyaColors.statusErrorBg else riyaColors.statusInfoBg,
                                                    fgColor = if (task.priority == "urgent" || task.priority == "high") riyaColors.statusErrorFg else riyaColors.statusInfoFg
                                                )
                                                if (task.dueDateLabel.isNotBlank()) {
                                                    StatusPill(task.dueDateLabel, riyaColors.bgSurfaceElevated, riyaColors.textSecondary)
                                                }
                                            }
                                        }
                                        IconButton(
                                            onClick = { onDeleteTask(task) },
                                            modifier = Modifier.size(48.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Delete,
                                                contentDescription = "Delete task",
                                                tint = riyaColors.statusErrorFg
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                1 -> {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                        ) {
                            OutlinedButton(
                                onClick = { showReminderDialog = true },
                                modifier = Modifier
                                    .defaultMinSize(minHeight = 48.dp)
                                    .testTag("add_reminder_button")
                            ) {
                                Icon(Icons.Filled.NotificationsActive, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Reminder")
                            }
                            Button(
                                onClick = { showNoteDialog = true },
                                modifier = Modifier
                                    .defaultMinSize(minHeight = 48.dp)
                                    .testTag("add_note_button")
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Note")
                            }
                        }
                        LazyColumn(
                            contentPadding = PaddingValues(vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            item {
                                Text("Reminders", style = MaterialTheme.typography.titleMedium, color = riyaColors.textPrimary)
                            }
                            items(reminders, key = { it.id }) { rem ->
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = riyaColors.bgCard),
                                    border = BorderStroke(1.dp, riyaColors.border),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        IconButton(
                                            onClick = { onToggleReminder(rem) },
                                            modifier = Modifier.size(48.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (rem.status == "triggered") Icons.Filled.CheckBox else Icons.Filled.NotificationsActive,
                                                contentDescription = "Toggle reminder",
                                                tint = riyaColors.statusWarningFg
                                            )
                                        }
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(rem.title, style = MaterialTheme.typography.titleMedium, color = riyaColors.textPrimary)
                                            Text(
                                                "${rem.triggerLabel} • ${rem.recurringRule} • ${rem.status}",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = riyaColors.textSecondary
                                            )
                                        }
                                        IconButton(onClick = { onDeleteReminder(rem) }, modifier = Modifier.size(48.dp)) {
                                            Icon(Icons.Filled.Delete, contentDescription = "Delete reminder", tint = riyaColors.statusErrorFg)
                                        }
                                    }
                                }
                            }
                            item {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text("Personal Notes", style = MaterialTheme.typography.titleMedium, color = riyaColors.textPrimary)
                            }
                            val sortedNotes = notes.sortedByDescending { it.pinned }
                            items(sortedNotes, key = { it.id }) { note ->
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = riyaColors.bgCard),
                                    border = BorderStroke(1.dp, if (note.pinned) riyaColors.accent else riyaColors.border),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text(note.title, style = MaterialTheme.typography.titleMedium, color = riyaColors.textPrimary)
                                            Text(note.content, style = MaterialTheme.typography.bodyMedium, color = riyaColors.textSecondary)
                                        }
                                        IconButton(onClick = { onToggleNotePin(note) }, modifier = Modifier.size(48.dp)) {
                                            Icon(
                                                imageVector = if (note.pinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                                                contentDescription = "Pin note",
                                                tint = if (note.pinned) riyaColors.accent else riyaColors.textMuted
                                            )
                                        }
                                        IconButton(onClick = { onDeleteNote(note) }, modifier = Modifier.size(48.dp)) {
                                            Icon(Icons.Filled.Delete, contentDescription = "Delete note", tint = riyaColors.statusErrorFg)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                2 -> {
                    LazyColumn(
                        contentPadding = PaddingValues(vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(RiyaCatalog.BUILTIN_SKILLS, key = { it.id }) { skill ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = riyaColors.bgCard),
                                border = BorderStroke(1.dp, riyaColors.border),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = skill.name,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = riyaColors.textPrimary
                                        )
                                        StatusPill(
                                            text = "RISK: ${skill.riskTier}",
                                            bgColor = if (skill.riskTier == "LOW") riyaColors.statusSuccessBg else riyaColors.statusWarningBg,
                                            fgColor = if (skill.riskTier == "LOW") riyaColors.statusSuccessFg else riyaColors.statusWarningFg
                                        )
                                    }
                                    Text(
                                        text = skill.description,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = riyaColors.textSecondary
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Capability: ${skill.permissionKey}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = riyaColors.textMuted
                                        )
                                        Button(
                                            onClick = { activeSkillDialog = skill },
                                            modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                                        ) {
                                            Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Run Skill")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                3 -> {
                    LazyColumn(
                        contentPadding = PaddingValues(vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(runs, key = { it.id }) { run ->
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
                                        Text(
                                            text = run.goal,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = riyaColors.textPrimary,
                                            modifier = Modifier.weight(1f)
                                        )
                                        StatusPill(
                                            text = "${run.status.uppercase()} (${run.stepsCompleted}/${run.stepsTotal})",
                                            bgColor = when (run.status) {
                                                "completed" -> riyaColors.statusSuccessBg
                                                "failed", "cancelled" -> riyaColors.statusErrorBg
                                                else -> riyaColors.statusInfoBg
                                            },
                                            fgColor = when (run.status) {
                                                "completed" -> riyaColors.statusSuccessFg
                                                "failed", "cancelled" -> riyaColors.statusErrorFg
                                                else -> riyaColors.statusInfoFg
                                            }
                                        )
                                    }
                                    Text(
                                        text = run.stepsSummary,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = riyaColors.textSecondary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showTaskDialog) {
        var title by remember { mutableStateOf("") }
        var desc by remember { mutableStateOf("") }
        var priority by remember { mutableStateOf("medium") }
        var due by remember { mutableStateOf("Today") }
        AlertDialog(
            onDismissRequest = { showTaskDialog = false },
            title = { Text("New Personal Task") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title") }, singleLine = true)
                    OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text("Description") })
                    OutlinedTextField(value = due, onValueChange = { due = it }, label = { Text("Due label") }, singleLine = true)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        RiyaCatalog.TASK_PRIORITIES.forEach { p ->
                            FilterChip(selected = priority == p, onClick = { priority = p }, label = { Text(p) })
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onCreateTask(title, desc, priority, "general", due)
                        showTaskDialog = false
                    },
                    enabled = title.isNotBlank()
                ) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { showTaskDialog = false }) { Text("Cancel") } }
        )
    }

    if (showReminderDialog) {
        var title by remember { mutableStateOf("") }
        var trigger by remember { mutableStateOf("Today 6:00 PM") }
        var rule by remember { mutableStateOf("none") }
        AlertDialog(
            onDismissRequest = { showReminderDialog = false },
            title = { Text("New Reminder") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Reminder Title") }, singleLine = true)
                    OutlinedTextField(value = trigger, onValueChange = { trigger = it }, label = { Text("When") }, singleLine = true)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        RiyaCatalog.REMINDER_RECURRENCES.forEach { r ->
                            FilterChip(selected = rule == r, onClick = { rule = r }, label = { Text(r) })
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onCreateReminder(title, trigger, rule)
                        showReminderDialog = false
                    },
                    enabled = title.isNotBlank()
                ) { Text("Schedule") }
            },
            dismissButton = { TextButton(onClick = { showReminderDialog = false }) { Text("Cancel") } }
        )
    }

    if (showNoteDialog) {
        var title by remember { mutableStateOf("") }
        var content by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showNoteDialog = false },
            title = { Text("New Personal Note") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title") }, singleLine = true)
                    OutlinedTextField(value = content, onValueChange = { content = it }, label = { Text("Note Content") }, minLines = 3)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onCreateNote(title, content, "general")
                        showNoteDialog = false
                    },
                    enabled = title.isNotBlank() && content.isNotBlank()
                ) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showNoteDialog = false }) { Text("Cancel") } }
        )
    }

    val chosenSkill = activeSkillDialog
    if (chosenSkill != null) {
        var skillInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { activeSkillDialog = null },
            title = { Text("Run ${chosenSkill.name}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(chosenSkill.description, style = MaterialTheme.typography.bodyMedium)
                    OutlinedTextField(
                        value = skillInput,
                        onValueChange = { skillInput = it },
                        label = { Text(chosenSkill.inputHint) },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    val skillToRun = chosenSkill
                    val inputCopy = skillInput
                    activeSkillDialog = null
                    onRunSkill(skillToRun, inputCopy)
                }) { Text("Execute") }
            },
            dismissButton = { TextButton(onClick = { activeSkillDialog = null }) { Text("Cancel") } }
        )
    }

    if (skillOutputTitle != null && skillOutputContent != null) {
        AlertDialog(
            onDismissRequest = onDismissSkillOutput,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (isSkillRunning) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text(skillOutputTitle)
                }
            },
            text = {
                Box(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    MarkdownText(markdown = skillOutputContent, textColor = riyaColors.textPrimary)
                }
            },
            confirmButton = {
                Button(onClick = onDismissSkillOutput) { Text("Close") }
            }
        )
    }
}
