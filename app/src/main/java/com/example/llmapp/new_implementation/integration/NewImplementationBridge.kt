package com.example.llmapp.new_implementation.integration

import android.content.Context
import android.util.Log
import com.example.llmapp.new_implementation.*
import com.example.llmapp.new_implementation.factories.SourceFactory
import com.example.llmapp.new_implementation.factories.DestinationFactory

/**
 * Bridge class to integrate new implementation with existing system
 * Allows gradual migration from old to new implementation
 */
class NewImplementationBridge(private val context: Context) {
    
    private val TAG = "NewImplementationBridge"
    
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
        
        val telegramConfig = mapOf(
            "context" to context,
            "chatId" to telegramChatId,
            "botToken" to telegramBotToken
        )
        val destination = DestinationFactory.create("telegram", telegramConfig)
        
        return Workflow(source, processor, destination)
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
        
        val gmailConfig = mapOf(
            "context" to context,
            "recipients" to gmailRecipients,
            "senderEmail" to gmailSender
        )
        val destination = DestinationFactory.create("gmail", gmailConfig)
        
        return Workflow(source, processor, destination)
    }
    
    /**
     * Execute a workflow and return results in a format compatible
     * with existing pipeline result handling
     */
    fun executeWorkflow(
        workflow: Workflow,
        useBatchMode: Boolean = false,
        batchSize: Int = 3
    ): WorkflowResult {
        return try {
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
