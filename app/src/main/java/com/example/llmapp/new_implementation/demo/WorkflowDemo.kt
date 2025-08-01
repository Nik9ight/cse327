package com.example.llmapp.new_implementation.demo

import android.content.Context
import android.util.Log
import com.example.llmapp.new_implementation.*
import com.example.llmapp.new_implementation.factories.SourceFactory
import com.example.llmapp.new_implementation.factories.DestinationFactory

/**
 * Demo and Test class for the new implementation
 * Tests the complete workflow: Source → LLM → Destination
 */
class WorkflowDemo(private val context: Context) {
    
    private val TAG = "WorkflowDemo"
    
    /**
     * Test Gmail to Telegram workflow
     */
    fun testGmailToTelegram(
        telegramBotToken: String,
        telegramChatId: String,
        onResult: (Boolean, String) -> Unit
    ) {
        try {
            Log.d(TAG, "Starting Gmail to Telegram workflow test")
            
            // Create source with configuration
            val gmailConfig = mapOf(
                "context" to context,
                "searchQuery" to "is:unread"
            )
            val source = SourceFactory.create("gmail", gmailConfig)
            
            // Create processor
            val processor = LLMProcessor(context)
            
            // Create destination with configuration
            val telegramConfig = mapOf(
                "context" to context,
                "chatId" to telegramChatId,
                "botToken" to telegramBotToken
            )
            val destination = DestinationFactory.create("telegram", telegramConfig)
            
            // Create and run workflow
            val workflow = Workflow(source, processor, destination)
            val success = workflow.run()
            
            val message = if (success) {
                "✅ Gmail to Telegram workflow completed successfully!"
            } else {
                "❌ Gmail to Telegram workflow failed"
            }
            
            Log.d(TAG, message)
            onResult(success, message)
            
        } catch (e: Exception) {
            val errorMessage = "❌ Gmail to Telegram workflow error: ${e.message}"
            Log.e(TAG, errorMessage, e)
            onResult(false, errorMessage)
        }
    }
    
    /**
     * Test Telegram to Gmail workflow
     */
    fun testTelegramToGmail(
        telegramBotToken: String,
        gmailRecipients: List<String>,
        onResult: (Boolean, String) -> Unit
    ) {
        try {
            Log.d(TAG, "Starting Telegram to Gmail workflow test")
            
            // Create source with configuration
            val telegramConfig = mapOf(
                "botToken" to telegramBotToken
            )
            val source = SourceFactory.create("telegram", telegramConfig)
            
            // Create processor
            val processor = LLMProcessor(context)
            
            // Create destination with configuration
            val gmailConfig = mapOf(
                "context" to context,
                "recipients" to gmailRecipients
            )
            val destination = DestinationFactory.create("gmail", gmailConfig)
            
            // Create and run workflow
            val workflow = Workflow(source, processor, destination)
            val success = workflow.run()
            
            val message = if (success) {
                "✅ Telegram to Gmail workflow completed successfully!"
            } else {
                "❌ Telegram to Gmail workflow failed"
            }
            
            Log.d(TAG, message)
            onResult(success, message)
            
        } catch (e: Exception) {
            val errorMessage = "❌ Telegram to Gmail workflow error: ${e.message}"
            Log.e(TAG, errorMessage, e)
            onResult(false, errorMessage)
        }
    }
    
    /**
     * Test batch processing workflow
     */
    fun testBatchProcessing(
        sourceType: String,
        destinationType: String,
        sourceConfig: Map<String, Any>,
        destinationConfig: Map<String, Any>,
        batchSize: Int = 3,
        onResult: (Boolean, String) -> Unit
    ) {
        try {
            Log.d(TAG, "Starting batch processing test: $sourceType -> $destinationType")
            
            // Create source
            val source = SourceFactory.create(sourceType, sourceConfig)
            
            // Create processor
            val processor = LLMProcessor(context)
            
            // Create destination
            val destination = DestinationFactory.create(destinationType, destinationConfig)
            
            // Create and run batch workflow
            val workflow = Workflow(source, processor, destination)
            val success = workflow.runBatch(batchSize)
            
            val message = if (success) {
                "✅ Batch processing ($sourceType -> $destinationType) completed successfully!"
            } else {
                "❌ Batch processing ($sourceType -> $destinationType) failed"
            }
            
            Log.d(TAG, message)
            onResult(success, message)
            
        } catch (e: Exception) {
            val errorMessage = "❌ Batch processing error: ${e.message}"
            Log.e(TAG, errorMessage, e)
            onResult(false, errorMessage)
        }
    }
    
    /**
     * Test individual components
     */
    fun testComponents(onResult: (Boolean, String) -> Unit) {
        val results = mutableListOf<String>()
        var allPassed = true
        
        try {
            // Test Message creation
            val testMessage = Message(
                id = "test_001",
                sender = "test@example.com",
                recipient = "recipient@example.com",
                content = "This is a test message for the new implementation",
                timestamp = System.currentTimeMillis(),
                metadata = mapOf("platform" to "test", "type" to "unit_test")
            )
            results.add("✅ Message creation: PASSED")
            
            // Test SourceFactory
            try {
                val supportedSources = SourceFactory.getSupportedTypes()
                results.add("✅ SourceFactory supported types: $supportedSources")
                
                // Test creation without config
                SourceFactory.create("gmail")
                SourceFactory.create("telegram")
                results.add("✅ SourceFactory creation: PASSED")
            } catch (e: Exception) {
                results.add("❌ SourceFactory test: FAILED - ${e.message}")
                allPassed = false
            }
            
            // Test DestinationFactory
            try {
                val supportedDestinations = DestinationFactory.getSupportedTypes()
                results.add("✅ DestinationFactory supported types: $supportedDestinations")
                
                // Test creation without config
                DestinationFactory.create("gmail")
                DestinationFactory.create("telegram")
                results.add("✅ DestinationFactory creation: PASSED")
            } catch (e: Exception) {
                results.add("❌ DestinationFactory test: FAILED - ${e.message}")
                allPassed = false
            }
            
            // Test LLMProcessor
            try {
                val processor = LLMProcessor(context)
                val processedMessage = processor.process(testMessage)
                results.add("✅ LLMProcessor single message: PASSED")
                results.add("   Processed content length: ${processedMessage.content.length}")
            } catch (e: Exception) {
                results.add("❌ LLMProcessor test: FAILED - ${e.message}")
                allPassed = false
            }
            
            // Test Formatters
            try {
                val telegramFormatter = TelegramFormatter()
                val gmailFormatter = GmailFormatter()
                
                val telegramOutput = telegramFormatter.formatOutput(testMessage)
                val gmailOutput = gmailFormatter.formatOutput(testMessage)
                
                results.add("✅ TelegramFormatter: PASSED (${telegramOutput.length} chars)")
                results.add("✅ GmailFormatter: PASSED (${gmailOutput.length} chars)")
            } catch (e: Exception) {
                results.add("❌ Formatter tests: FAILED - ${e.message}")
                allPassed = false
            }
            
        } catch (e: Exception) {
            results.add("❌ Component testing error: ${e.message}")
            allPassed = false
        }
        
        val finalResult = results.joinToString("\n")
        Log.d(TAG, "Component test results:\n$finalResult")
        onResult(allPassed, finalResult)
    }
    
    /**
     * Run comprehensive tests
     */
    fun runAllTests(
        telegramBotToken: String?,
        telegramChatId: String?,
        gmailRecipients: List<String>?,
        onResult: (Boolean, String) -> Unit
    ) {
        val results = mutableListOf<String>()
        var overallSuccess = true
        
        // Test components first
        testComponents { success, message ->
            if (!success) overallSuccess = false
            results.add("COMPONENT TESTS:\n$message")
        }
        
        // Test workflows if credentials provided
        if (!telegramBotToken.isNullOrBlank() && !telegramChatId.isNullOrBlank()) {
            testGmailToTelegram(telegramBotToken, telegramChatId) { success, message ->
                if (!success) overallSuccess = false
                results.add("\nGMAIL TO TELEGRAM:\n$message")
            }
        } else {
            results.add("\nGMAIL TO TELEGRAM: SKIPPED (missing credentials)")
        }
        
        if (!telegramBotToken.isNullOrBlank() && !gmailRecipients.isNullOrEmpty()) {
            testTelegramToGmail(telegramBotToken, gmailRecipients) { success, message ->
                if (!success) overallSuccess = false
                results.add("\nTELEGRAM TO GMAIL:\n$message")
            }
        } else {
            results.add("\nTELEGRAM TO GMAIL: SKIPPED (missing credentials)")
        }
        
        val finalReport = """
        🧪 NEW IMPLEMENTATION TEST REPORT
        ================================
        
        ${results.joinToString("\n")}
        
        ================================
        Overall Result: ${if (overallSuccess) "✅ PASSED" else "❌ FAILED"}
        """.trimIndent()
        
        Log.i(TAG, finalReport)
        onResult(overallSuccess, finalReport)
    }
}
