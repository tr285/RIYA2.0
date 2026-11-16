package com.example.ai

import android.graphics.Bitmap
import android.util.Log
import com.example.data.model.BuiltInSkill
import com.example.data.model.MemoryFact
import com.example.data.model.PersonalTask
import com.example.data.model.PlannedStep
import com.example.data.model.ReminderItem
import com.google.firebase.Firebase
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.ImagePart
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

private const val TAG = "RiyaAiEngine"
private const val DEFAULT_MODEL = "gemini-3.8-flash"
private const val IMAGE_MODEL = "gemini-nano-banana-2.1"
private const val AI_TIMEOUT_MS = 60_000L
private const val MAX_INLINE_AUDIO_BYTES = 18 * 1024 * 1024

data class AiTelemetry(
    val modelName: String = DEFAULT_MODEL,
    val totalGenerations: Int = 0,
    val lastLatencyMs: Long = 0L,
    val avgLatencyMs: Double = 0.0
)

data class StructuredPlanResult(
    val goal: String,
    val steps: List<PlannedStep>,
    val finalSummary: String
)

class RiyaAiEngine {

    private val chatModels = ConcurrentHashMap<String, GenerativeModel>()

    private fun getChatModel(modelName: String): GenerativeModel {
        val safeName = when (modelName) {
            "gemini-3.5-flash-lite", "gemini-4-argon", "gemini-3.8-flash" -> modelName
            else -> DEFAULT_MODEL
        }
        return chatModels.getOrPut(safeName) {
            Firebase.ai.generativeModel(
                modelName = safeName,
                generationConfig = generationConfig {
                    temperature = 0.7f
                    maxOutputTokens = 2048
                }
            )
        }
    }

    private val plannerSchema = Schema.obj(
        properties = mapOf(
            "goalSummary" to Schema.string(description = "Brief summary of the user's goal"),
            "steps" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "action" to Schema.string(description = "Short action name in snake_case"),
                        "description" to Schema.string(description = "Clear step description"),
                        "permissionKey" to Schema.enumeration(
                            values = listOf(
                                "memory.read",
                                "memory.write",
                                "productivity.manage",
                                "tasks.autonomous",
                                "system.info"
                            ),
                            description = "Required RIYA capability permission"
                        ),
                        "riskTier" to Schema.enumeration(
                            values = listOf("low", "medium", "high"),
                            description = "Risk tier of this step"
                        )
                    )
                ),
                description = "2 to 5 sequential executable steps"
            ),
            "executionResult" to Schema.string(
                description = "Detailed actionable output completing or preparing the goal"
            )
        )
    )

    private val plannerModel by lazy {
        Firebase.ai.generativeModel(
            modelName = DEFAULT_MODEL,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = plannerSchema
                temperature = 0.4f
            }
        )
    }

    private val imageModel by lazy {
        Firebase.ai.generativeModel(
            modelName = IMAGE_MODEL,
            generationConfig = generationConfig {
                responseModalities = listOf(ResponseModality.IMAGE)
            }
        )
    }

    fun streamConversation(
        userPrompt: String,
        historyPairs: List<Pair<String, String>>,
        memories: List<MemoryFact>,
        tasks: List<PersonalTask>,
        reminders: List<ReminderItem>,
        responseStyle: String,
        languageCode: String,
        autoMemoryRecall: Boolean,
        modelName: String = DEFAULT_MODEL
    ): Flow<String> = flow {
        val systemDirective = buildString {
            appendLine("You are RIYA, a helpful, intelligent, security-conscious Personal AI Assistant.")
            appendLine("Tone & Style: $responseStyle. Preferred language code: $languageCode.")
            appendLine("Format responses clearly using concise markdown when helpful (bullet points, code blocks, short headers).")
            if (autoMemoryRecall && memories.isNotEmpty()) {
                appendLine("\nRelevant Personal Memories (user-scoped):")
                memories.take(12).forEach { m ->
                    appendLine("- [${m.category}] ${m.key}: ${m.value}")
                }
            }
            val pendingTasks = tasks.filter { it.status == "pending" || it.status == "in_progress" }
            if (pendingTasks.isNotEmpty()) {
                appendLine("\nActive User Tasks (${pendingTasks.size}):")
                pendingTasks.take(6).forEach { t ->
                    appendLine("- [${t.priority.uppercase()}] ${t.title} (due: ${t.dueDateLabel})")
                }
            }
            val activeReminders = reminders.filter { it.status == "pending" }
            if (activeReminders.isNotEmpty()) {
                appendLine("\nPending Reminders (${activeReminders.size}):")
                activeReminders.take(5).forEach { r ->
                    appendLine("- ${r.title} (${r.triggerLabel})")
                }
            }
        }

        val chatHistory = mutableListOf<Content>()
        chatHistory += content("user") { text("System Context:\n$systemDirective") }
        chatHistory += content("model") { text("Understood. I am RIYA and will assist using your preferences and context.") }
        historyPairs.takeLast(12).forEach { (role, msg) ->
            val mappedRole = if (role == "user") "user" else "model"
            chatHistory += content(mappedRole) { text(msg) }
        }

        val chat = getChatModel(modelName).startChat(history = chatHistory)
        val accumulated = StringBuilder()
        try {
            withTimeout(AI_TIMEOUT_MS) {
                chat.sendMessageStream(userPrompt).collect { chunk ->
                    val piece = chunk.text.orEmpty()
                    if (piece.isNotEmpty()) {
                        accumulated.append(piece)
                        emit(accumulated.toString())
                    }
                }
            }
            if (accumulated.isBlank()) {
                throw IllegalStateException("AI model returned an empty response.")
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Streaming timed out", e)
            throw IllegalStateException("RIYA AI request timed out after 60 seconds. Please retry.", e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Streaming generation failed", e)
            throw e
        }
    }

    suspend fun planAutonomousTask(
        goal: String,
        memories: List<MemoryFact>,
        tasks: List<PersonalTask>
    ): StructuredPlanResult {
        val contextBlock = buildString {
            appendLine("Create a structured autonomous execution plan for this user goal:")
            appendLine("Goal: $goal")
            if (memories.isNotEmpty()) {
                appendLine("User memories: " + memories.take(6).joinToString("; ") { "${it.key}=${it.value}" })
            }
            if (tasks.isNotEmpty()) {
                appendLine("Existing tasks: " + tasks.take(6).joinToString("; ") { "${it.title}(${it.status})" })
            }
        }

        return try {
            val response = withTimeout(AI_TIMEOUT_MS) {
                plannerModel.generateContent(contextBlock)
            }
            val rawJson = response.text ?: throw IllegalStateException("Planner returned empty JSON.")
            val json = JSONObject(rawJson)
            val goalSummary = json.optString("goalSummary", goal)
            val stepsArr = json.optJSONArray("steps")
            val steps = buildList {
                if (stepsArr != null) {
                    for (i in 0 until stepsArr.length()) {
                        val obj = stepsArr.getJSONObject(i)
                        add(
                            PlannedStep(
                                stepIndex = i + 1,
                                action = obj.optString("action", "execute_step_${i + 1}"),
                                description = obj.optString("description", "Execute step ${i + 1}"),
                                permissionKey = obj.optString("permissionKey", "tasks.autonomous"),
                                riskTier = obj.optString("riskTier", "low")
                            )
                        )
                    }
                }
            }.ifEmpty {
                listOf(
                    PlannedStep(
                        stepIndex = 1,
                        action = "analyze_and_execute",
                        description = "Analyze goal and generate deliverable",
                        permissionKey = "tasks.autonomous",
                        riskTier = "low"
                    )
                )
            }
            val executionResult = json.optString("executionResult", "Plan prepared successfully.")
            StructuredPlanResult(
                goal = goalSummary,
                steps = steps,
                finalSummary = executionResult
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Autonomous planner timed out", e)
            throw IllegalStateException("Task planning timed out after 60 seconds.", e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Autonomous planner failed", e)
            throw e
        }
    }

    suspend fun runSkill(
        skill: BuiltInSkill,
        userInput: String,
        memories: List<MemoryFact>,
        tasks: List<PersonalTask>,
        modelName: String = DEFAULT_MODEL
    ): String {
        val prompt = buildString {
            appendLine("Execute RIYA built-in skill: ${skill.name} (${skill.id})")
            appendLine("Skill purpose: ${skill.description}")
            appendLine("User input: ${userInput.ifBlank { "Run general analysis with current workspace context" }}")
            if (memories.isNotEmpty()) {
                appendLine("Known user memories: " + memories.take(8).joinToString("; ") { "${it.key}: ${it.value}" })
            }
            if (tasks.isNotEmpty()) {
                appendLine("Known tasks: " + tasks.take(8).joinToString("; ") { "${it.title} [${it.priority}/${it.status}]" })
            }
            appendLine("Provide a crisp, structured, actionable output.")
        }
        return try {
            val response = withTimeout(AI_TIMEOUT_MS) {
                getChatModel(modelName).generateContent(prompt)
            }
            response.text?.takeIf { it.isNotBlank() }
                ?: throw IllegalStateException("Skill execution returned an empty response.")
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Skill execution timed out", e)
            throw IllegalStateException("Skill execution timed out after 60 seconds.", e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Skill execution failed", e)
            throw e
        }
    }

    suspend fun generateImage(prompt: String): Bitmap {
        return try {
            val response = withTimeout(AI_TIMEOUT_MS) {
                imageModel.generateContent(prompt)
            }
            val imagePart = response.candidates.firstOrNull()
                ?.content?.parts?.filterIsInstance<ImagePart>()
                ?.firstOrNull()
            imagePart?.image ?: throw IllegalStateException("No image was returned by $IMAGE_MODEL.")
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Image generation timed out", e)
            throw IllegalStateException("Image generation timed out after 60 seconds.", e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Image generation failed", e)
            throw e
        }
    }

    suspend fun editImage(inputBitmap: Bitmap, instruction: String): Bitmap {
        return try {
            val response = withTimeout(AI_TIMEOUT_MS) {
                imageModel.generateContent(
                    content {
                        image(inputBitmap)
                        text(instruction)
                    }
                )
            }
            val imagePart = response.candidates.firstOrNull()
                ?.content?.parts?.filterIsInstance<ImagePart>()
                ?.firstOrNull()
            imagePart?.image ?: throw IllegalStateException("No edited image was returned by $IMAGE_MODEL.")
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Image editing timed out", e)
            throw IllegalStateException("Image editing timed out after 60 seconds.", e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Image editing failed", e)
            throw e
        }
    }

    suspend fun transcribeAudio(
        audioBytes: ByteArray,
        mimeType: String = "audio/mp4",
        languageHint: String = "en",
        modelName: String = DEFAULT_MODEL
    ): String {
        if (audioBytes.isEmpty()) {
            throw IllegalArgumentException("Recorded audio clip is empty.")
        }
        if (audioBytes.size > MAX_INLINE_AUDIO_BYTES) {
            throw IllegalArgumentException("Audio clip exceeds 18 MB limit.")
        }
        return try {
            val response = withTimeout(AI_TIMEOUT_MS) {
                getChatModel(modelName).generateContent(
                    content {
                        inlineData(audioBytes, mimeType)
                        text(
                            "Transcribe this spoken audio accurately verbatim (preferred language hint: $languageHint). " +
                                "Output the verbatim transcript cleanly, followed by a concise bullet summary if multiple points are mentioned."
                        )
                    }
                )
            }
            response.text?.takeIf { it.isNotBlank() }
                ?: throw IllegalStateException("Audio transcription returned an empty response.")
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Audio transcription timed out", e)
            throw IllegalStateException("Audio transcription timed out after 60 seconds.", e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Audio transcription failed", e)
            throw e
        }
    }

    suspend fun speechToText(
        audioBytes: ByteArray,
        mimeType: String = "audio/wav",
        languageHint: String = "en",
        modelName: String = DEFAULT_MODEL
    ): String {
        if (audioBytes.isEmpty()) {
            throw IllegalArgumentException("Captured microphone audio is empty.")
        }
        if (audioBytes.size > MAX_INLINE_AUDIO_BYTES) {
            throw IllegalArgumentException("Captured audio exceeds 18 MB limit.")
        }
        return try {
            val response = withTimeout(AI_TIMEOUT_MS) {
                getChatModel(modelName).generateContent(
                    content {
                        inlineData(audioBytes, mimeType)
                        text(
                            "Transcribe the spoken words in this microphone recording verbatim " +
                                "(preferred language hint: $languageHint). " +
                                "Return ONLY the transcribed text with standard punctuation, no surrounding quotes, no labels, and no commentary. " +
                                "If no speech is audible, return an empty string."
                        )
                    }
                )
            }
            response.text?.trim().orEmpty()
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Speech-to-text timed out", e)
            throw IllegalStateException("Speech-to-text timed out after 60 seconds.", e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Speech-to-text failed", e)
            throw e
        }
    }
}
