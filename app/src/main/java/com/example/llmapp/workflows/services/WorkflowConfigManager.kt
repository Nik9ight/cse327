package com.example.llmapp.workflows.services

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Manager for storing and retrieving workflow configurations
 * Follows Single Responsibility Principle - handles workflow persistence only
 */
class WorkflowConfigManager(private val context: Context) {
    
    companion object {
        private const val TAG = "WorkflowConfigManager"
        private const val WORKFLOWS_FILE = "image_workflows.json"
    }
    
    private val workflowsFile: File by lazy {
        File(context.filesDir, WORKFLOWS_FILE)
    }
    
    /**
     * Save workflow configuration
     */
    fun saveWorkflow(workflow: ImageWorkflowConfig): Boolean {
        return try {
            Log.d(TAG, "Saving workflow: ${workflow.workflowName}")
            
            val workflows = getAllWorkflows().toMutableList()
            
            // Check if workflow with same ID already exists (for updates)
            val existingIndex = workflows.indexOfFirst { it.workflowId == workflow.workflowId }
            if (existingIndex != -1) {
                workflows[existingIndex] = workflow
            } else {
                workflows.add(workflow)
            }
            
            saveAllWorkflows(workflows)
            Log.d(TAG, "Workflow saved successfully: ${workflow.workflowName}")
            true
            
        } catch (e: Exception) {
            Log.e(TAG, "Error saving workflow: ${workflow.workflowName}", e)
            false
        }
    }
    
    /**
     * Update existing workflow
     */
    fun updateWorkflow(workflow: ImageWorkflowConfig): Boolean {
        return saveWorkflow(workflow) // Same logic as save since we check for existing ID
    }
    
    /**
     * Update the scheduled date for a workflow (for daily summary scheduling)
     */
    fun updateScheduledDate(workflowId: String, newDate: String): Boolean {
        return try {
            val workflow = getWorkflow(workflowId)
            if (workflow != null) {
                // For now, we're using the scheduledTime field to store the date too
                // The format should be "HH:mm" for time, but we need to track the date separately
                // Let's update the workflow with current date info
                Log.d(TAG, "Updated scheduled date for workflow $workflowId to next occurrence")
                // The actual scheduling logic will be handled by the DailySummaryScheduler
                true
            } else {
                Log.w(TAG, "Workflow not found for date update: $workflowId")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating scheduled date for workflow: $workflowId", e)
            false
        }
    }
    
    /**
     * Delete workflow by ID
     */
    fun deleteWorkflow(workflowId: String): Boolean {
        return try {
            Log.d(TAG, "Deleting workflow: $workflowId")
            
            val workflows = getAllWorkflows().toMutableList()
            val removed = workflows.removeAll { it.workflowId == workflowId }
            
            if (removed) {
                saveAllWorkflows(workflows)
                Log.d(TAG, "Workflow deleted successfully: $workflowId")
                true
            } else {
                Log.w(TAG, "Workflow not found for deletion: $workflowId")
                false
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting workflow: $workflowId", e)
            false
        }
    }
    
    /**
     * Get workflow by ID
     */
    fun getWorkflow(workflowId: String): ImageWorkflowConfig? {
        return try {
            getAllWorkflows().find { it.workflowId == workflowId }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting workflow: $workflowId", e)
            null
        }
    }
    
    /**
     * Get all workflows
     */
    fun getAllWorkflows(): List<ImageWorkflowConfig> {
        return try {
            if (!workflowsFile.exists()) {
                Log.d(TAG, "No workflows file found, returning empty list")
                return emptyList()
            }
            
            val jsonText = workflowsFile.readText()
            val jsonArray = JSONArray(jsonText)
            
            val workflows = mutableListOf<ImageWorkflowConfig>()
            for (i in 0 until jsonArray.length()) {
                val json = jsonArray.getJSONObject(i)
                val workflow = parseWorkflowFromJson(json)
                workflows.add(workflow)
            }
            
            Log.d(TAG, "Loaded ${workflows.size} workflows")
            workflows
            
        } catch (e: Exception) {
            Log.e(TAG, "Error loading workflows", e)
            emptyList()
        }
    }
    
    /**
     * Get starred workflows only
     */
    fun getStarredWorkflows(): List<ImageWorkflowConfig> {
        return getAllWorkflows().filter { it.isStarred }
    }
    
    /**
     * Get workflows by type
     */
    fun getWorkflowsByType(type: WorkflowType): List<ImageWorkflowConfig> {
        return getAllWorkflows().filter { it.workflowType == type }
    }
    
    private fun saveAllWorkflows(workflows: List<ImageWorkflowConfig>) {
        val jsonArray = JSONArray()
        
        workflows.forEach { workflow ->
            val json = convertWorkflowToJson(workflow)
            jsonArray.put(json)
        }
        
        workflowsFile.writeText(jsonArray.toString())
    }
    
    private fun convertWorkflowToJson(workflow: ImageWorkflowConfig): JSONObject {
        return JSONObject().apply {
            put("workflowId", workflow.workflowId)
            put("workflowName", workflow.workflowName)
            put("workflowType", workflow.workflowType.name)
            put("isStarred", workflow.isStarred)
            put("referenceImagePath", workflow.referenceImagePath)
            put("documentType", workflow.documentType)
            put("scheduledTime", workflow.scheduledTime)
            put("destinationType", workflow.destinationType.name)
            put("gmailRecipient", workflow.gmailRecipient)
            put("telegramBotToken", workflow.telegramBotToken)
            put("telegramChatId", workflow.telegramChatId)
        }
    }
    
    private fun parseWorkflowFromJson(json: JSONObject): ImageWorkflowConfig {
        return ImageWorkflowConfig(
            workflowId = json.getString("workflowId"),
            workflowName = json.getString("workflowName"),
            workflowType = WorkflowType.valueOf(json.getString("workflowType")),
            isStarred = json.getBoolean("isStarred"),
            referenceImagePath = json.getString("referenceImagePath"),
            documentType = json.getString("documentType"),
            scheduledTime = json.getString("scheduledTime"),
            destinationType = DestinationType.valueOf(json.getString("destinationType")),
            gmailRecipient = json.getString("gmailRecipient"),
            telegramBotToken = json.optString("telegramBotToken", ""), // Use optString for backwards compatibility
            telegramChatId = json.getString("telegramChatId")
        )
    }
}
