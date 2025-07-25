package com.example.llmapp.workflow

import android.content.Context
import android.util.Log
import com.example.llmapp.workflow.*

/**
 * Demo class to show workflow management system functionality
 * This demonstrates how to create, save, and manage workflows programmatically
 */
class WorkflowDemo(private val context: Context) {
    
    companion object {
        private const val TAG = "WorkflowDemo"
    }
    
    private val workflowManager = WorkflowManager(context)
    
    /**
     * Creates sample workflows to demonstrate the system
     */
    suspend fun createSampleWorkflows(): Result<List<SavedWorkflow>> {
        return try {
            val workflows = mutableListOf<SavedWorkflow>()
            
            // Sample Gmail to Telegram workflow
            val gmailToTelegramConfig = GmailToTelegramConfig(
                gmailSearchQuery = "is:unread from:important@company.com",
                telegramBotToken = "sample_bot_token",
                telegramChatId = "sample_chat_id",
                llmPrompt = "Summarize this important email for quick review",
                emailLimit = 3
            )
            
            val gmailWorkflowResult = workflowManager.createWorkflow(
                name = "Important Email Alerts",
                type = WorkflowType.GMAIL_TO_TELEGRAM,
                configuration = gmailToTelegramConfig
            )
            
            gmailWorkflowResult.fold(
                onSuccess = { gmailWorkflow ->
                    workflows.add(gmailWorkflow)
                    Log.d(TAG, "Created Gmail to Telegram workflow: ${gmailWorkflow.name}")
                },
                onFailure = { exception ->
                    Log.e(TAG, "Failed to create Gmail workflow: ${exception.message}")
                }
            )
            
            // Sample Telegram to Gmail workflow
            val telegramToGmailConfig = TelegramToGmailConfig(
                telegramBotToken = "sample_bot_token_2",
                telegramChatId = "sample_chat_id_2",
                gmailRecipients = listOf("team@company.com", "manager@company.com"),
                gmailSender = "bot@company.com",
                llmPrompt = "Format these messages for professional team communication",
                messageLimit = 20,
                emailTemplate = "STANDARD"
            )
            
            val telegramWorkflowResult = workflowManager.createWorkflow(
                name = "Team Chat Summary",
                type = WorkflowType.TELEGRAM_TO_GMAIL,
                configuration = telegramToGmailConfig
            )
            
            telegramWorkflowResult.fold(
                onSuccess = { telegramWorkflow ->
                    workflows.add(telegramWorkflow)
                    Log.d(TAG, "Created Telegram to Gmail workflow: ${telegramWorkflow.name}")
                },
                onFailure = { exception ->
                    Log.e(TAG, "Failed to create Telegram workflow: ${exception.message}")
                }
            )
            
            // Create workflow from template
            val templates = WorkflowTemplate.getDefaultTemplates()
            if (templates.isNotEmpty()) {
                val template = templates.first()
                
                val templateWorkflowResult = workflowManager.createWorkflow(
                    name = "${template.name} (From Template)",
                    type = template.type,
                    configuration = template.defaultConfiguration
                )
                
                templateWorkflowResult.fold(
                    onSuccess = { templateWorkflow ->
                        workflows.add(templateWorkflow)
                        Log.d(TAG, "Created workflow from template: ${templateWorkflow.name}")
                    },
                    onFailure = { exception ->
                        Log.e(TAG, "Failed to create workflow from template: ${exception.message}")
                    }
                )
            }
            
            Result.success(workflows)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create sample workflows", e)
            Result.failure(e)
        }
    }
    
    /**
     * Demonstrates workflow execution
     */
    suspend fun demonstrateWorkflowExecution(workflowId: String): Result<String> {
        return try {
            val result = workflowManager.executeWorkflow(workflowId)
            
            result.fold(
                onSuccess = { executionResult ->
                    val message = "Workflow executed successfully: ${executionResult.message}"
                    Log.d(TAG, message)
                    Result.success(message)
                },
                onFailure = { exception ->
                    val message = "Workflow execution failed: ${exception.message}"
                    Log.w(TAG, message, exception)
                    Result.failure(exception)
                }
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to execute workflow", e)
            Result.failure(e)
        }
    }
    
    /**
     * Lists all available workflows
     */
    suspend fun listAllWorkflows(): Result<List<SavedWorkflow>> {
        return try {
            val workflows = workflowManager.getAllWorkflows()
            
            Log.d(TAG, "Found ${workflows.size} workflows:")
            workflows.forEach { workflow ->
                Log.d(TAG, "- ${workflow.name} (${workflow.type.getDisplayName()})")
            }
            
            Result.success(workflows)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to list workflows", e)
            Result.failure(e)
        }
    }
    
    /**
     * Demonstrates workflow validation
     */
    fun demonstrateValidation(): Result<String> {
        return try {
            // Valid configuration
            val validConfig = GmailToTelegramConfig(
                gmailSearchQuery = "is:unread",
                telegramBotToken = "1234567890:ABCDEF1234567890abcdef1234567890ABC",
                telegramChatId = "-1001234567890",
                llmPrompt = "Summarize this email",
                emailLimit = 5
            )
            
            val validResult = validConfig.validate()
            Log.d(TAG, "Valid config validation: ${validResult.isValid()}")
            
            // Invalid configuration
            val invalidConfig = GmailToTelegramConfig(
                gmailSearchQuery = "",
                telegramBotToken = "invalid_token",
                telegramChatId = "",
                llmPrompt = "",
                emailLimit = -1
            )
            
            val invalidResult = invalidConfig.validate()
            Log.d(TAG, "Invalid config validation: ${invalidResult.isValid()}")
            Log.d(TAG, "Validation errors: ${invalidResult.getErrorMessage()}")
            
            Result.success("Validation demonstration completed")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed validation demonstration", e)
            Result.failure(e)
        }
    }
    
    /**
     * Cleans up demo workflows
     */
    suspend fun cleanupDemoWorkflows(): Result<Unit> {
        return try {
            val workflows = workflowManager.getAllWorkflows()
            val demoWorkflows = workflows.filter { workflow -> 
                workflow.name.contains("(From Template)") || 
                workflow.name == "Important Email Alerts" || 
                workflow.name == "Team Chat Summary" 
            }
            
            for (workflow in demoWorkflows) {
                val deleteResult = workflowManager.deleteWorkflow(workflow.id)
                deleteResult.fold(
                    onSuccess = {
                        Log.d(TAG, "Deleted demo workflow: ${workflow.name}")
                    },
                    onFailure = { exception ->
                        Log.e(TAG, "Failed to delete workflow ${workflow.name}: ${exception.message}")
                    }
                )
            }
            
            Result.success(Unit)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cleanup demo workflows", e)
            Result.failure(e)
        }
    }
}
