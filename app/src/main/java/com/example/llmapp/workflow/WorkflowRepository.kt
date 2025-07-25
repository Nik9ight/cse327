package com.example.llmapp.workflow

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.gson.*
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.lang.reflect.Type

/**
 * Repository Pattern implementation for workflow persistence
 * This abstracts the data storage mechanism and provides a clean interface
 */
interface WorkflowRepository {
    suspend fun saveWorkflow(workflow: SavedWorkflow): Result<Unit>
    suspend fun getWorkflow(id: String): SavedWorkflow?
    suspend fun getAllWorkflows(): List<SavedWorkflow>
    suspend fun deleteWorkflow(id: String): Result<Unit>
    suspend fun updateWorkflow(workflow: SavedWorkflow): Result<Unit>
}

/**
 * SharedPreferences-based implementation of WorkflowRepository
 * Uses JSON serialization for complex objects
 */
class WorkflowRepositoryImpl(private val context: Context) : WorkflowRepository {
    
    companion object {
        private const val TAG = "WorkflowRepository"
        private const val PREFS_NAME = "workflow_preferences"
        private const val KEY_WORKFLOWS = "saved_workflows"
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = GsonBuilder()
        .registerTypeAdapter(WorkflowConfiguration::class.java, WorkflowConfigurationAdapter())
        .create()
    
    /**
     * Custom type adapter for WorkflowConfiguration sealed class
     */
    private class WorkflowConfigurationAdapter : JsonSerializer<WorkflowConfiguration>, JsonDeserializer<WorkflowConfiguration> {
        companion object {
            private const val TYPE_FIELD = "configType"
        }
        
        override fun serialize(src: WorkflowConfiguration?, typeOfSrc: Type?, context: JsonSerializationContext?): JsonElement {
            if (src == null) return JsonNull.INSTANCE
            
            val jsonObject = JsonObject()
            
            when (src) {
                is GmailToTelegramConfig -> {
                    jsonObject.addProperty(TYPE_FIELD, "GmailToTelegram")
                    jsonObject.addProperty("gmailSearchQuery", src.gmailSearchQuery)
                    jsonObject.addProperty("telegramBotToken", src.telegramBotToken)
                    jsonObject.addProperty("telegramChatId", src.telegramChatId)
                    jsonObject.addProperty("llmPrompt", src.llmPrompt)
                    jsonObject.addProperty("emailLimit", src.emailLimit)
                    jsonObject.addProperty("autoProcessInterval", src.autoProcessInterval)
                }
                is TelegramToGmailConfig -> {
                    jsonObject.addProperty(TYPE_FIELD, "TelegramToGmail")
                    jsonObject.addProperty("telegramBotToken", src.telegramBotToken)
                    jsonObject.addProperty("telegramChatId", src.telegramChatId)
                    jsonObject.add("gmailRecipients", context?.serialize(src.gmailRecipients))
                    jsonObject.addProperty("gmailSender", src.gmailSender)
                    jsonObject.addProperty("llmPrompt", src.llmPrompt)
                    jsonObject.addProperty("messageLimit", src.messageLimit)
                    jsonObject.addProperty("emailTemplate", src.emailTemplate)
                }
            }
            
            return jsonObject
        }
        
        override fun deserialize(json: JsonElement?, typeOfT: Type?, context: JsonDeserializationContext?): WorkflowConfiguration {
            if (json == null || !json.isJsonObject) {
                throw JsonParseException("Invalid WorkflowConfiguration JSON")
            }
            
            val jsonObject = json.asJsonObject
            val type = jsonObject.get(TYPE_FIELD)?.asString
                ?: throw JsonParseException("Missing configuration type")
            
            return when (type) {
                "GmailToTelegram" -> GmailToTelegramConfig(
                    gmailSearchQuery = jsonObject.get("gmailSearchQuery")?.asString ?: "",
                    telegramBotToken = jsonObject.get("telegramBotToken")?.asString ?: "",
                    telegramChatId = jsonObject.get("telegramChatId")?.asString ?: "",
                    llmPrompt = jsonObject.get("llmPrompt")?.asString ?: "",
                    emailLimit = jsonObject.get("emailLimit")?.asInt ?: 5,
                    autoProcessInterval = jsonObject.get("autoProcessInterval")?.asLong ?: 0
                )
                "TelegramToGmail" -> TelegramToGmailConfig(
                    telegramBotToken = jsonObject.get("telegramBotToken")?.asString ?: "",
                    telegramChatId = jsonObject.get("telegramChatId")?.asString ?: "",
                    gmailRecipients = context?.deserialize(jsonObject.get("gmailRecipients"), 
                        object : TypeToken<List<String>>() {}.type) ?: emptyList(),
                    gmailSender = jsonObject.get("gmailSender")?.asString ?: "",
                    llmPrompt = jsonObject.get("llmPrompt")?.asString ?: "",
                    messageLimit = jsonObject.get("messageLimit")?.asInt ?: 30,
                    emailTemplate = jsonObject.get("emailTemplate")?.asString ?: "STANDARD"
                )
                else -> throw JsonParseException("Unknown configuration type: $type")
            }
        }
    }
    
    override suspend fun saveWorkflow(workflow: SavedWorkflow): Result<Unit> = withContext(Dispatchers.IO) {
        return@withContext try {
            val workflows = getAllWorkflowsInternal().toMutableList()
            
            // Remove existing workflow with same ID if it exists
            workflows.removeAll { it.id == workflow.id }
            
            // Add the new/updated workflow
            workflows.add(workflow)
            
            // Save to preferences
            val json = gson.toJson(workflows)
            prefs.edit().putString(KEY_WORKFLOWS, json).apply()
            
            Log.d(TAG, "Saved workflow: ${workflow.name} (ID: ${workflow.id})")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save workflow: ${workflow.name}", e)
            Result.failure(e)
        }
    }
    
    override suspend fun getWorkflow(id: String): SavedWorkflow? = withContext(Dispatchers.IO) {
        return@withContext try {
            getAllWorkflowsInternal().find { it.id == id }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get workflow: $id", e)
            null
        }
    }
    
    override suspend fun getAllWorkflows(): List<SavedWorkflow> = withContext(Dispatchers.IO) {
        return@withContext getAllWorkflowsInternal()
    }
    
    override suspend fun deleteWorkflow(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        return@withContext try {
            val workflows = getAllWorkflowsInternal().toMutableList()
            val removed = workflows.removeAll { it.id == id }
            
            if (removed) {
                val json = gson.toJson(workflows)
                prefs.edit().putString(KEY_WORKFLOWS, json).apply()
                Log.d(TAG, "Deleted workflow: $id")
            }
            
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete workflow: $id", e)
            Result.failure(e)
        }
    }
    
    override suspend fun updateWorkflow(workflow: SavedWorkflow): Result<Unit> {
        // Same as save for this implementation
        return saveWorkflow(workflow)
    }
    
    private fun getAllWorkflowsInternal(): List<SavedWorkflow> {
        return try {
            val json = prefs.getString(KEY_WORKFLOWS, null)
            if (json != null) {
                val type = object : TypeToken<List<SavedWorkflow>>() {}.type
                gson.fromJson(json, type) ?: emptyList()
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse workflows from preferences", e)
            emptyList()
        }
    }
}

/**
 * Strategy Pattern for workflow execution
 */
interface WorkflowExecutionStrategy {
    suspend fun execute(pipeline: WorkflowPipeline, workflow: SavedWorkflow): WorkflowExecutionResult
}

/**
 * Default execution strategy
 */
class DefaultExecutionStrategy : WorkflowExecutionStrategy {
    override suspend fun execute(pipeline: WorkflowPipeline, workflow: SavedWorkflow): WorkflowExecutionResult {
        return pipeline.execute()
    }
}

/**
 * Execution strategy with retry logic
 */
class RetryExecutionStrategy(
    private val maxRetries: Int = 3,
    private val retryDelayMs: Long = 1000
) : WorkflowExecutionStrategy {
    
    override suspend fun execute(pipeline: WorkflowPipeline, workflow: SavedWorkflow): WorkflowExecutionResult {
        var lastResult: WorkflowExecutionResult? = null
        
        repeat(maxRetries) { attempt ->
            try {
                val result = pipeline.execute()
                if (result.success) {
                    return result
                }
                lastResult = result
                
                if (attempt < maxRetries - 1) {
                    kotlinx.coroutines.delay(retryDelayMs)
                }
            } catch (e: Exception) {
                lastResult = WorkflowExecutionResult(
                    success = false,
                    message = "Execution failed: ${e.message}",
                    executionTime = 0
                )
                
                if (attempt < maxRetries - 1) {
                    kotlinx.coroutines.delay(retryDelayMs)
                }
            }
        }
        
        return lastResult ?: WorkflowExecutionResult(
            success = false,
            message = "All retry attempts failed",
            executionTime = 0
        )
    }
}
