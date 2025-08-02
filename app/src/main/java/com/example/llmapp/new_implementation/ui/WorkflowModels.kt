package com.example.llmapp.new_implementation.ui

import android.content.Context
import com.example.llmapp.workflow.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Data models for the new workflow system - now using original workflow classes
 */

// Use the original WorkflowType from the main system
typealias WorkflowType = com.example.llmapp.workflow.WorkflowType

// Convert new implementation configs to original system configs
sealed class SavedWorkflowConfig {
    data class GmailToTelegram(
        val gmailSearchQuery: String,
        val telegramBotToken: String,
        val telegramChatId: String,
        val llmPrompt: String = "Summarize this email briefly"
    ) : SavedWorkflowConfig() {
        fun toOriginalConfig(): GmailToTelegramConfig {
            return GmailToTelegramConfig(
                gmailSearchQuery = gmailSearchQuery,
                telegramBotToken = telegramBotToken,
                telegramChatId = telegramChatId,
                llmPrompt = llmPrompt,
                emailLimit = 5
            )
        }
    }
    
    data class TelegramToGmail(
        val telegramBotToken: String,
        val gmailRecipients: List<String>,
        val gmailSender: String,
        val llmPrompt: String = "Summarize this conversation"
    ) : SavedWorkflowConfig() {
        fun toOriginalConfig(): TelegramToGmailConfig {
            return TelegramToGmailConfig(
                telegramBotToken = telegramBotToken,
                telegramChatId = "", // Not needed for this direction
                gmailRecipients = gmailRecipients,
                gmailSender = gmailSender,
                llmPrompt = llmPrompt,
                messageLimit = 30,
                emailTemplate = "STANDARD"
            )
        }
    }
}

data class SavedWorkflow(
    val id: String,
    val name: String,
    val type: WorkflowType,
    val intervalSeconds: Int,
    val configuration: SavedWorkflowConfig,
    val isRunning: Boolean = false,
    val created: Long,
    val lastRun: Long? = null,
    val description: String = ""
) {
    fun toOriginalWorkflow(): com.example.llmapp.workflow.SavedWorkflow {
        val originalConfig = when (configuration) {
            is SavedWorkflowConfig.GmailToTelegram -> configuration.toOriginalConfig()
            is SavedWorkflowConfig.TelegramToGmail -> configuration.toOriginalConfig()
        }
        
        return com.example.llmapp.workflow.SavedWorkflow(
            id = id,
            name = name,
            type = type,
            configuration = originalConfig,
            createdAt = created,
            lastModified = created,
            isEnabled = true,
            description = description
        )
    }
    
    companion object {
        fun fromOriginalWorkflow(
            originalWorkflow: com.example.llmapp.workflow.SavedWorkflow,
            intervalSeconds: Int = 300,
            isRunning: Boolean = false,
            lastRun: Long? = null
        ): SavedWorkflow {
            val config = when (val originalConfig = originalWorkflow.configuration) {
                is GmailToTelegramConfig -> SavedWorkflowConfig.GmailToTelegram(
                    gmailSearchQuery = originalConfig.gmailSearchQuery,
                    telegramBotToken = originalConfig.telegramBotToken,
                    telegramChatId = originalConfig.telegramChatId,
                    llmPrompt = originalConfig.llmPrompt
                )
                is TelegramToGmailConfig -> SavedWorkflowConfig.TelegramToGmail(
                    telegramBotToken = originalConfig.telegramBotToken,
                    gmailRecipients = originalConfig.gmailRecipients,
                    gmailSender = originalConfig.gmailSender,
                    llmPrompt = originalConfig.llmPrompt
                )
            }
            
            return SavedWorkflow(
                id = originalWorkflow.id,
                name = originalWorkflow.name,
                type = originalWorkflow.type,
                intervalSeconds = intervalSeconds,
                configuration = config,
                isRunning = isRunning,
                created = originalWorkflow.createdAt,
                lastRun = lastRun,
                description = originalWorkflow.description
            )
        }
    }
}

/**
 * Storage for new implementation workflows - now using the original WorkflowManager
 */
class NewWorkflowStorage(private val context: Context) {
    
    private val workflowManager = WorkflowManager(context)
    private val prefs = context.getSharedPreferences("new_workflow_metadata", Context.MODE_PRIVATE)
    
    suspend fun saveWorkflow(workflow: SavedWorkflow) = withContext(Dispatchers.IO) {
        // Save to the original workflow system
        val originalWorkflow = workflow.toOriginalWorkflow()
        val result = workflowManager.createWorkflow(
            name = originalWorkflow.name,
            type = originalWorkflow.type,
            configuration = originalWorkflow.configuration
        )
        
        result.fold(
            onSuccess = { savedOriginalWorkflow ->
                // Save additional metadata for new implementation features
                saveMetadata(savedOriginalWorkflow.id, workflow.intervalSeconds, workflow.isRunning, workflow.lastRun)
                android.util.Log.d("NewWorkflowStorage", "Saved workflow: ${workflow.name} (ID: ${savedOriginalWorkflow.id})")
            },
            onFailure = { exception ->
                android.util.Log.e("NewWorkflowStorage", "Failed to save workflow: ${workflow.name}", exception)
                throw exception
            }
        )
    }
    
    suspend fun getWorkflow(id: String): SavedWorkflow? = withContext(Dispatchers.IO) {
        val originalWorkflow = workflowManager.getWorkflow(id) ?: return@withContext null
        val metadata = getMetadata(id)
        
        return@withContext SavedWorkflow.fromOriginalWorkflow(
            originalWorkflow = originalWorkflow,
            intervalSeconds = metadata.intervalSeconds,
            isRunning = metadata.isRunning,
            lastRun = metadata.lastRun
        )
    }
    
    suspend fun getAllWorkflows(): List<SavedWorkflow> = withContext(Dispatchers.IO) {
        val originalWorkflows = workflowManager.getAllWorkflows()
        android.util.Log.d("NewWorkflowStorage", "Retrieved ${originalWorkflows.size} workflows from original system")
        
        return@withContext originalWorkflows.map { originalWorkflow ->
            val metadata = getMetadata(originalWorkflow.id)
            SavedWorkflow.fromOriginalWorkflow(
                originalWorkflow = originalWorkflow,
                intervalSeconds = metadata.intervalSeconds,
                isRunning = metadata.isRunning,
                lastRun = metadata.lastRun
            )
        }
    }
    
    suspend fun updateWorkflow(workflow: SavedWorkflow) = withContext(Dispatchers.IO) {
        // Update in the original system
        val originalConfig = when (workflow.configuration) {
            is SavedWorkflowConfig.GmailToTelegram -> workflow.configuration.toOriginalConfig()
            is SavedWorkflowConfig.TelegramToGmail -> workflow.configuration.toOriginalConfig()
        }
        
        val result = workflowManager.updateWorkflow(
            id = workflow.id,
            name = workflow.name,
            configuration = originalConfig
        )
        
        result.fold(
            onSuccess = {
                // Update metadata
                saveMetadata(workflow.id, workflow.intervalSeconds, workflow.isRunning, workflow.lastRun)
                android.util.Log.d("NewWorkflowStorage", "Updated workflow: ${workflow.name}")
            },
            onFailure = { exception ->
                android.util.Log.e("NewWorkflowStorage", "Failed to update workflow: ${workflow.name}", exception)
                throw exception
            }
        )
    }
    
    suspend fun deleteWorkflow(id: String) = withContext(Dispatchers.IO) {
        val result = workflowManager.deleteWorkflow(id)
        result.fold(
            onSuccess = {
                // Remove metadata
                removeMetadata(id)
                android.util.Log.d("NewWorkflowStorage", "Deleted workflow: $id")
            },
            onFailure = { exception ->
                android.util.Log.e("NewWorkflowStorage", "Failed to delete workflow: $id", exception)
                throw exception
            }
        )
    }
    
    suspend fun clearAll() = withContext(Dispatchers.IO) {
        // Note: This would delete ALL workflows in the system, use with caution
        prefs.edit().clear().commit()
        android.util.Log.d("NewWorkflowStorage", "Cleared all workflow metadata")
    }
    
    suspend fun getStorageInfo(): String = withContext(Dispatchers.IO) {
        val workflows = getAllWorkflows()
        return@withContext "Storage Info: ${workflows.size} workflows in unified system"
    }
    
    // Metadata management for new implementation features
    private data class WorkflowMetadata(
        val intervalSeconds: Int = 300,
        val isRunning: Boolean = false,
        val lastRun: Long? = null
    )
    
    private fun saveMetadata(workflowId: String, intervalSeconds: Int, isRunning: Boolean, lastRun: Long?) {
        val key = "metadata_$workflowId"
        val metadataString = "$intervalSeconds|$isRunning|${lastRun ?: ""}"
        prefs.edit().putString(key, metadataString).apply()
    }
    
    private fun getMetadata(workflowId: String): WorkflowMetadata {
        val key = "metadata_$workflowId"
        val metadataString = prefs.getString(key, null) ?: return WorkflowMetadata()
        
        return try {
            val parts = metadataString.split("|")
            WorkflowMetadata(
                intervalSeconds = parts[0].toInt(),
                isRunning = parts[1].toBoolean(),
                lastRun = if (parts[2].isBlank()) null else parts[2].toLong()
            )
        } catch (e: Exception) {
            WorkflowMetadata()
        }
    }
    
    private fun removeMetadata(workflowId: String) {
        val key = "metadata_$workflowId"
        prefs.edit().remove(key).apply()
    }
}
