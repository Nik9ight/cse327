package com.example.llmapp.pipeline

import android.util.Log
import com.example.llmapp.GmailService
import com.example.llmapp.adapters.GmailDeliveryAdapter
import com.example.llmapp.adapters.TelegramSourceAdapter
import com.example.llmapp.adapters.EmailDeliveryResult
import com.example.llmapp.adapters.TelegramEmailContent
import com.example.llmapp.adapters.EmailTemplateType
import com.example.llmapp.commands.*
import com.example.llmapp.observers.PipelineObserver
import com.example.llmapp.pipeline.PromptStrategy
import com.example.llmapp.pipeline.DefaultPromptStrategy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

/**
 * Main facade for the Telegram to Gmail pipeline
 * Implements the Facade pattern to provide a simple interface for complex operations
 * Uses Strategy, Command, Observer, and Template Method patterns
 */
class TelegramToGmailPipeline(
    private val botToken: String,
    private val gmailService: GmailService,
    private val emailRecipients: List<String>,
    private val defaultSender: String,
    private val llmProcessor: LlmContentProcessor? = null
) {
    
    companion object {
        private val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    }
    
    // Core components
    private val telegramAdapter: TelegramSourceAdapter = TelegramSourceAdapter(botToken)
    private val gmailAdapter: GmailDeliveryAdapter = GmailDeliveryAdapter(gmailService, defaultSender)
    private val commandInvoker: TelegramCommandInvoker = TelegramCommandInvoker()
    private val observers = mutableListOf<PipelineObserver>()
    
    // Strategy pattern for different prompt types
    private var promptStrategy: PromptStrategy = DefaultPromptStrategy()
    
    // Template for processing
    private lateinit var processingTemplate: GmailDeliveryTemplate
    private lateinit var processOnlyTemplate: GmailDeliveryTemplate // For consolidated processing without email sending
    
    // Configuration
    private var emailTemplateType: EmailTemplateType = EmailTemplateType.STANDARD
    private var batchSize: Int = 5
    private var delayBetweenEmails: Long = 1000 // 1 second
    
    init {
        initializeTemplate()
    }
    
    /**
     * Process the most recent conversation from a specific chat and send as email
     */
    suspend fun processRecentConversation(
        chatId: String, 
        messageLimit: Int = 30,
        customSubject: String? = null
    ): PipelineResult = withContext(Dispatchers.IO) {
        return@withContext try {
            notifyObservers { onPipelineStarted("Processing recent conversation from chat: $chatId") }
            
            Log.d("TelegramToGmailPipeline", "Processing recent conversation from chat: $chatId")
            
            // Step 1: Validate inputs
            if (chatId.isBlank()) {
                val error = "Chat ID cannot be empty"
                notifyObservers { onPipelineError(error) }
                return@withContext PipelineResult.Failure(error)
            }
            
            if (messageLimit <= 0) {
                val error = "Message limit must be positive"
                notifyObservers { onPipelineError(error) }
                return@withContext PipelineResult.Failure(error)
            }
            
            // Step 2: Fetch recent conversation using the specific method
            notifyObservers { onProgressUpdate("Fetching recent $messageLimit messages from chat $chatId...") }
            
            val messages = try {
                Log.d("TelegramToGmailPipeline", "Fetching recent conversation for chat: $chatId with limit: $messageLimit")
                
                // Clear cache to ensure we get the most recent messages
                telegramAdapter.clearMessageCache()
                
                // Fetch messages from specific chat
                val conversationMessages = telegramAdapter.getRecentConversation(chatId, messageLimit)
                
                // If no messages found for specific chat, try any available chat as fallback
                if (conversationMessages.isEmpty()) {
                    Log.w("TelegramToGmailPipeline", "No messages found for chat $chatId, trying any available chat")
                    notifyObservers { onProgressUpdate("No messages found for specified chat, checking any available chats...") }
                    
                    telegramAdapter.getRecentMessagesFromAnyChat(messageLimit)
                } else {
                    conversationMessages
                }
            } catch (e: Exception) {
                val error = "Failed to fetch messages from Telegram: ${e.message}"
                Log.e("TelegramToGmailPipeline", error, e)
                notifyObservers { onPipelineError(error) }
                return@withContext PipelineResult.Failure(error)
            }
            
            if (messages.isEmpty()) {
                val error = "No messages found in chat: $chatId. The bot might not have received any messages yet, or you may need to send a message to the bot first."
                notifyObservers { onPipelineError(error) }
                return@withContext PipelineResult.Failure(error)
            }

            // Process messages
            notifyObservers { onProgressUpdate("Fetched ${messages.size} messages from chat") }
            Log.d("TelegramToGmailPipeline", "Processing ${messages.size} messages from chat")
            
            // Step 3: Process consolidated messages using Command pattern with error handling
            // Use process-only template to avoid duplicate emails - this will only do LLM processing
            val consolidatedCommand = try {
                ConsolidatedTelegramProcessCommand(messages, processOnlyTemplate)
            } catch (e: Exception) {
                val error = "Failed to create consolidated command: ${e.message}"
                Log.e("TelegramToGmailPipeline", error, e)
                notifyObservers { onPipelineError(error) }
                return@withContext PipelineResult.Failure(error)
            }
            
            val success = try {
                commandInvoker.executeTelegramCommand(consolidatedCommand)
            } catch (e: Exception) {
                val error = "Failed to execute consolidated command: ${e.message}"
                Log.e("TelegramToGmailPipeline", error, e)
                notifyObservers { onPipelineError(error) }
                return@withContext PipelineResult.Failure(error)
            }
            
            if (!success) {
                val error = "Failed to process consolidated Telegram messages - command execution returned false"
                notifyObservers { onPipelineError(error) }
                return@withContext PipelineResult.Failure(error)
            }
            
            Log.d("TelegramToGmailPipeline", "Successfully processed consolidated messages with LLM")
            
            // Step 4: Create content from consolidated result - use LLM processed content if available
            val llmProcessedSummary = consolidatedCommand.getLlmProcessedContent() ?: consolidatedCommand.getResult() ?: ""
            val combinedContent = try {
                createConsolidatedConversationContent(messages, llmProcessedSummary)
            } catch (e: Exception) {
                val error = "Failed to create consolidated conversation content: ${e.message}"
                Log.e("TelegramToGmailPipeline", error, e)
                notifyObservers { onPipelineError(error) }
                return@withContext PipelineResult.Failure(error)
            }
            
            val emailContent = try {
                createTelegramEmailContent(combinedContent, messages)
            } catch (e: Exception) {
                val error = "Failed to create email content: ${e.message}"
                Log.e("TelegramToGmailPipeline", error, e)
                notifyObservers { onPipelineError(error) }
                return@withContext PipelineResult.Failure(error)
            }
            
            val emailResult = try {
                gmailAdapter.sendTelegramSummary(
                    recipients = emailRecipients,
                    telegramContent = emailContent,
                    templateType = emailTemplateType
                )
            } catch (e: Exception) {
                val error = "Failed to send email: ${e.message}"
                Log.e("TelegramToGmailPipeline", error, e)
                notifyObservers { onPipelineError(error) }
                return@withContext PipelineResult.Failure(error)
            }
            
            return@withContext when (emailResult) {
                is EmailDeliveryResult.Success -> {
                    notifyObservers { onPipelineCompleted("Email sent successfully: ${emailResult.messageId}") }
                    PipelineResult.Success(
                        messageId = emailResult.messageId,
                        processedMessages = messages.size,
                        emailSent = true
                    )
                }
                is EmailDeliveryResult.Failure -> {
                    notifyObservers { onPipelineError("Email delivery failed: ${emailResult.error}") }
                    PipelineResult.Failure("Email delivery failed: ${emailResult.error}")
                }
            }
            
        } catch (e: Exception) {
            val error = "Pipeline execution failed: ${e.message}"
            Log.e("TelegramToGmailPipeline", error, e)
            notifyObservers { onPipelineError(error) }
            PipelineResult.Failure(error)
        }
    }
    
    /**
     * Process a single message and send as email
     */
    suspend fun processSingleMessage(
        chatId: String,
        messageId: String
    ): PipelineResult = withContext(Dispatchers.IO) {
        return@withContext try {
            notifyObservers { onPipelineStarted("Processing single message: $messageId") }
            
            // Fetch specific message
            val sourceMessage = telegramAdapter.getMessageById(messageId)
            if (sourceMessage == null) {
                val error = "Message not found: $messageId"
                notifyObservers { onPipelineError(error) }
                return@withContext PipelineResult.Failure(error)
            }
            
            // Convert to TelegramMessage
            val telegramMessage = TelegramMessage(
                id = sourceMessage.id,
                content = sourceMessage.content,
                senderName = sourceMessage.metadata["sender_name"] ?: "Unknown",
                timestamp = sourceMessage.metadata["timestamp"] ?: DATE_FORMAT.format(Date()),
                chatType = sourceMessage.metadata["chat_type"] ?: "unknown",
                metadata = sourceMessage.metadata
            )
            
            // Process using Command pattern
            val command = ProcessTelegramMessageCommand(telegramMessage, processingTemplate)
            val success = commandInvoker.executeTelegramCommand(command)
            
            if (!success) {
                val error = "Failed to process message: $messageId"
                notifyObservers { onPipelineError(error) }
                return@withContext PipelineResult.Failure(error)
            }
            
            // Send email
            val processedContent = command.getResult()
            val emailContent = TelegramEmailContent(
                senderName = telegramMessage.senderName,
                originalMessage = telegramMessage.content,
                processedContent = processedContent,
                chatType = telegramMessage.chatType,
                originalTimestamp = telegramMessage.timestamp
            )
            
            val emailResult = gmailAdapter.sendTelegramSummary(
                recipients = emailRecipients,
                telegramContent = emailContent,
                templateType = emailTemplateType
            )
            
            return@withContext when (emailResult) {
                is EmailDeliveryResult.Success -> {
                    notifyObservers { onPipelineCompleted("Single message processed and emailed: ${emailResult.messageId}") }
                    PipelineResult.Success(
                        messageId = emailResult.messageId,
                        processedMessages = 1,
                        emailSent = true
                    )
                }
                is EmailDeliveryResult.Failure -> {
                    notifyObservers { onPipelineError("Email delivery failed: ${emailResult.error}") }
                    PipelineResult.Failure("Email delivery failed: ${emailResult.error}")
                }
            }
            
        } catch (e: Exception) {
            val error = "Single message processing failed: ${e.message}"
            Log.e("TelegramToGmailPipeline", error, e)
            notifyObservers { onPipelineError(error) }
            PipelineResult.Failure(error)
        }
    }
    
    /**
     * Process all recent updates from Telegram
     */
    suspend fun processAllRecentUpdates(limit: Int = 20): PipelineResult = withContext(Dispatchers.IO) {
        return@withContext try {
            notifyObservers { onPipelineStarted("Processing all recent updates (limit: $limit)") }
            
            val messages = telegramAdapter.getUpdates(limit = limit)
            if (messages.isEmpty()) {
                val error = "No recent updates found"
                notifyObservers { onPipelineError(error) }
                return@withContext PipelineResult.Failure(error)
            }
            
            // Process in batches to avoid overwhelming email
            val batches = messages.chunked(batchSize)
            var totalProcessed = 0
            val emailResults = mutableListOf<EmailDeliveryResult>()
            
            for ((batchIndex, batch) in batches.withIndex()) {
                notifyObservers { onProgressUpdate("Processing batch ${batchIndex + 1}/${batches.size}") }
                
                val consolidatedCommand = ConsolidatedTelegramProcessCommand(batch, processOnlyTemplate)
                val success = commandInvoker.executeTelegramCommand(consolidatedCommand)
                
                if (success) {
                    val llmProcessedSummary = consolidatedCommand.getLlmProcessedContent() ?: consolidatedCommand.getResult() ?: ""
                    val combinedContent = createConsolidatedConversationContent(batch, llmProcessedSummary)
                    val emailContent = createTelegramEmailContent(combinedContent, batch, "Batch ${batchIndex + 1}")
                    
                    val emailResult = gmailAdapter.sendTelegramSummary(
                        recipients = emailRecipients,
                        telegramContent = emailContent,
                        templateType = emailTemplateType
                    )
                    
                    emailResults.add(emailResult)
                    totalProcessed += batch.size
                    
                    // Delay between batches
                    if (batchIndex < batches.size - 1) {
                        Thread.sleep(delayBetweenEmails * 2)
                    }
                }
            }
            
            val successfulEmails = emailResults.count { it is EmailDeliveryResult.Success }
            
            notifyObservers { 
                onPipelineCompleted("Processed $totalProcessed messages in ${batches.size} batches, sent $successfulEmails emails") 
            }
            
            PipelineResult.Success(
                messageId = "batch_${System.currentTimeMillis()}",
                processedMessages = totalProcessed,
                emailSent = successfulEmails > 0,
                batchInfo = "Sent $successfulEmails/${batches.size} batch emails"
            )
            
        } catch (e: Exception) {
            val error = "Batch processing failed: ${e.message}"
            Log.e("TelegramToGmailPipeline", error, e)
            notifyObservers { onPipelineError(error) }
            PipelineResult.Failure(error)
        }
    }
    
    // Configuration methods
    
    fun setPromptStrategy(strategy: PromptStrategy) {
        this.promptStrategy = strategy
        // Update existing templates rather than recreating them
        updateTemplatePromptStrategy()
    }
    
    fun setEmailTemplate(templateType: EmailTemplateType) {
        this.emailTemplateType = templateType
    }
    
    fun setBatchSize(size: Int) {
        this.batchSize = size.coerceIn(1, 20)
    }
    
    fun setDelayBetweenEmails(delayMs: Long) {
        this.delayBetweenEmails = delayMs.coerceAtLeast(0)
    }
    
    // Observer pattern implementation
    
    fun addObserver(observer: PipelineObserver) {
        observers.add(observer)
    }
    
    fun removeObserver(observer: PipelineObserver) {
        observers.remove(observer)
    }
    
    // Utility methods
    
    suspend fun testConnections(): ConnectionTestResults {
        return ConnectionTestResults(
            telegramConnection = testTelegramConnection(),
            gmailConnection = testGmailConnection()
        )
    }
    
    /**
     * Test email sending functionality
     * This method can be used to verify that the email sending fix is working
     */
    suspend fun testEmailSending(): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            Log.d("TelegramToGmailPipeline", "Testing email sending functionality...")
            val testResult = gmailAdapter.sendTestEmail(emailRecipients.first())
            Log.d("TelegramToGmailPipeline", "Email test result: $testResult")
            testResult is EmailDeliveryResult.Success
        } catch (e: Exception) {
            Log.e("TelegramToGmailPipeline", "Email test failed: ${e.message}", e)
            false
        }
    }
    
    /**
     * Get available chat IDs for debugging
     */
    suspend fun getAvailableChatIds(): List<String> = withContext(Dispatchers.IO) {
        return@withContext try {
            // Clear cache to get fresh chat IDs
            telegramAdapter.clearMessageCache()
            
            // Try multiple methods to get chat IDs
            val chatIds = telegramAdapter.getAvailableChatIds()
            val directUpdates = telegramAdapter.getUpdates(limit = 50)
            val directChatIds = directUpdates.mapNotNull { it.metadata["chat_id"] }.distinct()
            
            val allChatIds = (chatIds + directChatIds).distinct()
            
            if (allChatIds.isEmpty()) {
                Log.w("TelegramToGmailPipeline", "No chat IDs found. Try sending a message to the bot first.")
                // Return mock chat ID for testing
                listOf("mock_chat_${System.currentTimeMillis()}")
            } else {
                allChatIds
            }
        } catch (e: Exception) {
            Log.e("TelegramToGmailPipeline", "Failed to get available chat IDs: ${e.message}")
            // Return mock chat ID as fallback
            listOf("mock_chat_${System.currentTimeMillis()}")
        }
    }
    
    fun getStatistics(): PipelineStatistics {
        val telegramStats = commandInvoker.getTelegramStats()
        return PipelineStatistics(
            totalProcessedMessages = telegramStats.totalCommands,
            successfulOperations = telegramStats.successfulCommands,
            batchOperations = telegramStats.batchCommands,
            singleOperations = telegramStats.singleCommands,
            consolidatedOperations = telegramStats.consolidatedCommands,
            successRate = telegramStats.successRate
        )
    }
    
    fun clearHistory() {
        commandInvoker.clearTelegramHistory()
        commandInvoker.clearHistory()
    }
    
    // Private helper methods
    
    private fun initializeTemplate() {
        processingTemplate = if (llmProcessor != null) {
            Log.d("TelegramToGmailPipeline", "Initializing template with real LLM processor")
            GmailDeliveryTemplate(
                promptStrategy = promptStrategy,
                gmailService = gmailService,
                emailRecipients = emailRecipients,
                llmProcessor = llmProcessor
            )
        } else {
            Log.d("TelegramToGmailPipeline", "Initializing template without LLM processor (simulated mode)")
            GmailDeliveryTemplate(
                promptStrategy = promptStrategy,
                gmailService = gmailService,
                emailRecipients = emailRecipients
            )
        }
        
        // Create a process-only template for consolidated processing (no email sending)
        processOnlyTemplate = if (llmProcessor != null) {
            GmailDeliveryTemplate(
                promptStrategy = promptStrategy,
                gmailService = gmailService,
                emailRecipients = emailRecipients,
                llmProcessor = llmProcessor,
                processOnlyMode = true // This will skip email sending
            )
        } else {
            GmailDeliveryTemplate(
                promptStrategy = promptStrategy,
                gmailService = gmailService,
                emailRecipients = emailRecipients,
                processOnlyMode = true
            )
        }
    }
    
    /**
     * Update prompt strategy on existing templates without recreating them
     * This avoids duplicate initialization and preserves LLM processor state
     */
    private fun updateTemplatePromptStrategy() {
        // Only update the LLM processor's prompt strategy if it exists
        // This preserves any custom prompt that was set directly on the processor
        llmProcessor?.setPromptStrategy(promptStrategy)
        Log.d("TelegramToGmailPipeline", "Updated prompt strategy on LLM processor: ${promptStrategy::class.simpleName}")
    }
    
    private fun combineConversationResults(
        messages: List<TelegramMessage>,
        results: Map<String, String>
    ): CombinedConversationContent {
        val conversationSummary = results.values.joinToString("\n\n---\n\n")
        val participants = messages.map { it.senderName }.distinct()
        val timeRange = "${messages.minOfOrNull { it.timestamp }} to ${messages.maxOfOrNull { it.timestamp }}"
        
        return CombinedConversationContent(
            summary = conversationSummary,
            participants = participants,
            messageCount = messages.size,
            timeRange = timeRange,
            originalMessages = messages
        )
    }
    
    /**
     * Create consolidated conversation content from LLM-processed summary
     * This follows Single Responsibility Principle by having one method handle consolidated content creation
     */
    private fun createConsolidatedConversationContent(
        messages: List<TelegramMessage>,
        llmProcessedSummary: String
    ): CombinedConversationContent {
        val participants = messages.map { it.senderName }.distinct()
        val timeRange = "${messages.minOfOrNull { it.timestamp }} to ${messages.maxOfOrNull { it.timestamp }}"
        
        return CombinedConversationContent(
            summary = llmProcessedSummary,
            participants = participants,
            messageCount = messages.size,
            timeRange = timeRange,
            originalMessages = messages
        )
    }
    
    private fun createTelegramEmailContent(
        content: CombinedConversationContent,
        messages: List<TelegramMessage>,
        batchLabel: String? = null
    ): TelegramEmailContent {
        val subject = batchLabel?.let { "Telegram $it Summary" } ?: "Telegram Conversation Summary"
        val senderSummary = content.participants.joinToString(", ")
        
        return TelegramEmailContent(
            senderName = senderSummary,
            originalMessage = messages.joinToString("\n\n") { "${it.senderName}: ${it.content}" },
            processedContent = content.summary,
            chatType = messages.firstOrNull()?.chatType ?: "unknown",
            originalTimestamp = content.timeRange
        )
    }
    
    private suspend fun testTelegramConnection(): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            telegramAdapter.getUpdates(limit = 1)
            true
        } catch (e: Exception) {
            Log.e("TelegramToGmailPipeline", "Telegram connection test failed", e)
            false
        }
    }
    
    private fun testGmailConnection(): Boolean {
        return when (gmailAdapter.testConnection()) {
            is com.example.llmapp.adapters.ConnectionTestResult.Success -> true
            is com.example.llmapp.adapters.ConnectionTestResult.Failure -> false
        }
    }
    
    private fun notifyObservers(action: PipelineObserver.() -> Unit) {
        observers.forEach { observer ->
            try {
                observer.action()
            } catch (e: Exception) {
                Log.e("TelegramToGmailPipeline", "Observer notification failed", e)
            }
        }
    }
    
    /**
     * Reset Telegram update tracking (now does nothing since no tracking)
     */
    fun resetTelegramTracking() {
        try {
            telegramAdapter.resetUpdateTracking()
            Log.d("TelegramToGmailPipeline", "Telegram update tracking reset (no-op)")
        } catch (e: Exception) {
            Log.e("TelegramToGmailPipeline", "Failed to reset Telegram tracking", e)
        }
    }
    
    /**
     * Get debug information about the Telegram API integration
     */
    suspend fun getDebugInfo(): String = withContext(Dispatchers.IO) {
        return@withContext try {
            val rawUpdates = telegramAdapter.getUpdates(offset = null, limit = 10)
            
            if (rawUpdates.isNotEmpty()) {
                val uniqueChats = rawUpdates.mapNotNull { it.metadata["chat_id"] }.distinct()
                buildString {
                    appendLine("� Telegram API Debug:")
                    appendLine("📬 Updates found: ${rawUpdates.size}")
                    appendLine("💬 Active chats: $uniqueChats")
                    appendLine("📋 Recent messages:")
                    rawUpdates.take(3).forEach { msg ->
                        appendLine("  - Chat: ${msg.metadata["chat_id"]}, Content: ${msg.content.take(30)}...")
                    }
                }
            } else {
                """
                ❌ No updates found
                🔧 Possible issues:
                  - Bot token might be incorrect
                  - No messages sent to bot yet
                  - Network connectivity issues
                """.trimIndent()
            }
        } catch (e: Exception) {
            "❌ Debug info collection failed: ${e.message}"
        }
    }
}

// Data classes for pipeline results and configuration

sealed class PipelineResult {
    data class Success(
        val messageId: String,
        val processedMessages: Int,
        val emailSent: Boolean,
        val batchInfo: String? = null
    ) : PipelineResult()
    
    data class Failure(
        val error: String
    ) : PipelineResult()
}

data class CombinedConversationContent(
    val summary: String,
    val participants: List<String>,
    val messageCount: Int,
    val timeRange: String,
    val originalMessages: List<TelegramMessage>
)

data class ConnectionTestResults(
    val telegramConnection: Boolean,
    val gmailConnection: Boolean
) {
    val allConnectionsWorking: Boolean
        get() = telegramConnection && gmailConnection
}

data class PipelineStatistics(
    val totalProcessedMessages: Int,
    val successfulOperations: Int,
    val batchOperations: Int,
    val singleOperations: Int,
    val consolidatedOperations: Int = 0,
    val successRate: Double
)

/**
 * Builder for creating configured TelegramToGmailPipeline instances
 */
class TelegramToGmailPipelineBuilder {
    private var botToken: String? = null
    private var gmailService: GmailService? = null
    private var emailRecipients: List<String> = emptyList()
    private var defaultSender: String? = null
    private var promptStrategy: PromptStrategy? = null
    private var emailTemplateType: EmailTemplateType = EmailTemplateType.STANDARD
    private var batchSize: Int = 5
    private var delayBetweenEmails: Long = 1000
    private var llmProcessor: LlmContentProcessor? = null
    
    fun botToken(token: String) = apply { this.botToken = token }
    fun gmailService(service: GmailService) = apply { this.gmailService = service }
    fun emailRecipients(recipients: List<String>) = apply { this.emailRecipients = recipients }
    fun defaultSender(sender: String) = apply { this.defaultSender = sender }
    fun promptStrategy(strategy: PromptStrategy) = apply { this.promptStrategy = strategy }
    fun emailTemplate(template: EmailTemplateType) = apply { this.emailTemplateType = template }
    fun batchSize(size: Int) = apply { this.batchSize = size }
    fun delayBetweenEmails(delay: Long) = apply { this.delayBetweenEmails = delay }
    fun llmProcessor(processor: LlmContentProcessor) = apply { this.llmProcessor = processor }
    
    fun build(): TelegramToGmailPipeline {
        require(!botToken.isNullOrBlank()) { "Bot token is required" }
        require(gmailService != null) { "Gmail service is required" }
        require(emailRecipients.isNotEmpty()) { "Email recipients are required" }
        require(!defaultSender.isNullOrBlank()) { "Default sender is required" }
        
        val pipeline = TelegramToGmailPipeline(
            botToken = botToken!!,
            gmailService = gmailService!!,
            emailRecipients = emailRecipients,
            defaultSender = defaultSender!!,
            llmProcessor = llmProcessor
        )
        
        promptStrategy?.let { pipeline.setPromptStrategy(it) }
        pipeline.setEmailTemplate(emailTemplateType)
        pipeline.setBatchSize(batchSize)
        pipeline.setDelayBetweenEmails(delayBetweenEmails)
        
        return pipeline
    }
}
