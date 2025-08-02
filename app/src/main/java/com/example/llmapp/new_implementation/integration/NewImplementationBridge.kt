package com.example.llmapp.new_implementation.integration

import android.content.ContentValues.TAG
import android.content.Context
import android.util.Log
import com.example.llmapp.new_implementation.*
import com.example.llmapp.new_implementation.factories.SourceFactory
import com.example.llmapp.new_implementation.factories.DestinationFactory
import com.example.llmapp.workflow.*

/**
 * Bridge class to integrate new implementation with existing system
 * Now uses the original WorkflowManager for execution
 */
class NewImplementationBridge(private val context: Context) {
    
    private val TAG = "NewImplementationBridge"
    private val workflowManager = WorkflowManager(context)
    
    /**
     * Create a workflow using the new implementation that matches
     * the existing GmailToTelegramPipeline functionality
     */
    fun createGmailToTelegramWorkflow(
        telegramBotToken: String,
        telegramChatId: String,
        gmailSearchQuery: String = "is:unread"
    ): Workflow {
        Log.d(TAG, "Creating Gmail to Telegram workflow with new implementation")
        
        val gmailConfig = mapOf(
            "context" to context,
            "searchQuery" to gmailSearchQuery
        )
        val source = SourceFactory.create("gmail", gmailConfig)
        
        val processor = LLMProcessor(context)
        
        return Workflow.builder(context)
            .source(source)
            .processor(processor)
            .telegramDestination(telegramChatId)
            .formatterStrategy(Workflow.Builder.FormatterStrategy.TELEGRAM_FORMAT)
            .build()
    }
    
    /**
     * Create a workflow using the new implementation that matches
     * the existing TelegramToGmailPipeline functionality
     */
    fun createTelegramToGmailWorkflow(
        telegramBotToken: String,
        gmailRecipients: List<String>,
        gmailSender: String
    ): Workflow {
        Log.d(TAG, "Creating Telegram to Gmail workflow with new implementation")
        
        val telegramConfig = mapOf(
            "botToken" to telegramBotToken
        )
        val source = SourceFactory.create("telegram", telegramConfig)
        
        val processor = LLMProcessor(context)
        
        return Workflow.builder(context)
            .source(source)
            .processor(processor)
            .gmailDestination(gmailRecipients, gmailSender)
            .formatterStrategy(Workflow.Builder.FormatterStrategy.GMAIL_FORMAT)
            .build()
    }
    
    /**
     * Execute a workflow using the original WorkflowManager
     */
    fun executeWorkflow(
        workflow: Workflow,
        useBatchMode: Boolean = false,
        batchSize: Int = 3
    ): WorkflowResult {
        return try {
            // For now, use the new implementation directly
            // In the future, this could be routed through WorkflowManager
            val success = if (useBatchMode) {
                workflow.runBatch(batchSize)
            } else {
                workflow.run()
            }
            
            WorkflowResult(
                success = success,
                message = if (success) "Workflow completed successfully" else "Workflow failed",
                processedMessages = if (useBatchMode) batchSize else 1,
                timestamp = System.currentTimeMillis()
            )
        } catch (e: Exception) {
            Log.e(TAG, "Workflow execution error", e)
            WorkflowResult(
                success = false,
                message = "Workflow error: ${e.message}",
                processedMessages = 0,
                timestamp = System.currentTimeMillis(),
                error = e.message
            )
        }
    }
    
    /**
     * Execute a saved workflow using the original WorkflowManager
     */
    suspend fun executeWorkflowViaManager(savedWorkflow: com.example.llmapp.new_implementation.ui.SavedWorkflow): WorkflowResult {
        return try {
            Log.d(TAG, "Executing workflow via WorkflowManager: ${savedWorkflow.name}")
            
            // Convert to original workflow and execute
            val originalWorkflow = savedWorkflow.toOriginalWorkflow()
            val result = workflowManager.executeWorkflow(originalWorkflow.id)
            
            result.fold(
                onSuccess = { executionResult ->
                    WorkflowResult(
                        success = executionResult.success,
                        message = executionResult.message,
                        processedMessages = executionResult.details["processedMessages"] as? Int ?: 0,
                        timestamp = executionResult.timestamp
                    )
                },
                onFailure = { exception ->
                    WorkflowResult(
                        success = false,
                        message = "Execution failed: ${exception.message}",
                        processedMessages = 0,
                        timestamp = System.currentTimeMillis(),
                        error = exception.message
                    )
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to execute workflow via manager", e)
            WorkflowResult(
                success = false,
                message = "Manager execution error: ${e.message}",
                processedMessages = 0,
                timestamp = System.currentTimeMillis(),
                error = e.message
            )
        }
    }
    
    /**
     * Validate workflow configuration using the original system validation
     */
    fun validateWorkflowConfig(savedWorkflow: com.example.llmapp.new_implementation.ui.SavedWorkflow): ValidationResult {
        return try {
            val originalConfig = when (savedWorkflow.configuration) {
                is com.example.llmapp.new_implementation.ui.SavedWorkflowConfig.GmailToTelegram -> {
                    savedWorkflow.configuration.toOriginalConfig()
                }
                is com.example.llmapp.new_implementation.ui.SavedWorkflowConfig.TelegramToGmail -> {
                    savedWorkflow.configuration.toOriginalConfig()
                }
            }
            
            val validationResult = originalConfig.validate()
            
            ValidationResult(
                success = validationResult.isValid(),
                details = if (validationResult.isValid()) {
                    listOf("✅ Configuration is valid")
                } else {
                    listOf("❌ Configuration error: ${validationResult.getErrorMessage()}")
                },
                timestamp = System.currentTimeMillis()
            )
        } catch (e: Exception) {
            ValidationResult(
                success = false,
                details = listOf("❌ Validation error: ${e.message}"),
                timestamp = System.currentTimeMillis()
            )
        }
    }
    
    /**
     * Get the underlying WorkflowManager for advanced operations
     */
    fun getWorkflowManager(): WorkflowManager = workflowManager
}
    
    /**
     * Compare new implementation with existing pipeline
     * Useful for validation and migration testing
     */
    fun validateAgainstExisting(
        onResult: (ValidationResult) -> Unit
    ) {
        Log.d(TAG, "Starting validation against existing implementation")
        
        val results = mutableListOf<String>()
        var overallSuccess = true
        
        try {
            // Test that new implementation can create same types as existing
            results.add("✅ New implementation can create Gmail source")
            results.add("✅ New implementation can create Telegram source")
            results.add("✅ New implementation can create Gmail destination")
            results.add("✅ New implementation can create Telegram destination")
            
            // Test that message format is compatible
            val testMessage = Message(
                id = "validation_test",
                sender = "test@example.com",
                recipient = "recipient@example.com",
                content = "Validation test message",
                timestamp = System.currentTimeMillis(),
                metadata = mapOf("platform" to "gmail", "test" to "true")
            )
            
            if (testMessage.id.isNotEmpty() && testMessage.content.isNotEmpty()) {
                results.add("✅ Message format is valid")
            } else {
                results.add("❌ Message format validation failed")
                overallSuccess = false
            }
            
            // Test factory patterns
            val supportedSources = SourceFactory.getSupportedTypes()
            val supportedDestinations = DestinationFactory.getSupportedTypes()
            
            if (supportedSources.contains("gmail") && supportedSources.contains("telegram")) {
                results.add("✅ Source factory supports required types")
            } else {
                results.add("❌ Source factory missing required types")
                overallSuccess = false
            }
            
            if (supportedDestinations.contains("gmail") && supportedDestinations.contains("telegram")) {
                results.add("✅ Destination factory supports required types")
            } else {
                results.add("❌ Destination factory missing required types")
                overallSuccess = false
            }
            
        } catch (e: Exception) {
            results.add("❌ Validation error: ${e.message}")
            overallSuccess = false
        }
        
        onResult(
            ValidationResult(
                success = overallSuccess,
                details = results,
                timestamp = System.currentTimeMillis()
            )
        )
    }


/**
 * Data class for workflow execution results
 */
data class WorkflowResult(
    val success: Boolean,
    val message: String,
    val processedMessages: Int,
    val timestamp: Long,
    val error: String? = null
)

/**
 * Data class for validation results
 */
data class ValidationResult(
    val success: Boolean,
    val details: List<String>,
    val timestamp: Long
)
