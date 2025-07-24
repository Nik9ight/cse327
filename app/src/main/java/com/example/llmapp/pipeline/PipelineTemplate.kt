package com.example.llmapp.pipeline

import android.util.Log
import com.example.llmapp.pipeline.Email
import com.example.llmapp.TelegramService
import com.example.llmapp.interfaces.MessageSourceService
import kotlinx.coroutines.runBlocking

/**
 * Template Method Pattern for email processing pipeline
 * Defines the skeleton of the email processing algorithm with customizable steps
 */
abstract class EmailProcessingTemplate {
    private val tag = "EmailProcessingTemplate"
    
    /**
     * Template method defining the overall algorithm
     * This is the main method that follows the template pattern
     */
    fun processEmail(email: Email): Boolean {
        Log.d(tag, "Starting email processing template")
        
        try {
            // Step 1: Validate input
            if (!validateEmail(email)) {
                Log.e(tag, "Email validation failed")
                return false
            }
            
            // Step 2: Pre-process email
            val preprocessedEmail = preprocessEmail(email)
            if (preprocessedEmail == null) {
                Log.e(tag, "Email preprocessing failed")
                return false
            }
            
            // Step 3: Apply LLM processing
            val processedContent = applyLlmProcessing(preprocessedEmail)
            if (processedContent.isNullOrBlank()) {
                Log.e(tag, "LLM processing failed")
                return false
            }
            
            // Step 4: Format output
            val formattedOutput = formatOutput(processedContent, preprocessedEmail)
            if (formattedOutput.isNullOrBlank()) {
                Log.e(tag, "Output formatting failed")
                return false
            }
            
            // Step 5: Send to destination
            val success = sendToDestination(formattedOutput, preprocessedEmail)
            if (!success) {
                Log.e(tag, "Failed to send to destination")
                return false
            }
            
            // Step 6: Post-process (optional hook)
            postProcess(preprocessedEmail, formattedOutput)
            
            Log.d(tag, "Email processing completed successfully")
            return true
            
        } catch (e: Exception) {
            Log.e(tag, "Error in email processing template", e)
            handleError(e, email)
            return false
        }
    }
    
    /**
     * Hook methods that subclasses can override
     * These define the customizable parts of the algorithm
     */
    
    // Abstract methods - must be implemented by subclasses
    protected abstract fun applyLlmProcessing(email: Email): String?
    protected abstract fun sendToDestination(content: String, email: Email): Boolean
    
    // Concrete methods with default implementation - can be overridden
    protected open fun validateEmail(email: Email): Boolean {
        return !email.subject.isBlank() || !email.content.isBlank()
    }
    
    protected open fun preprocessEmail(email: Email): Email? {
        // Default preprocessing - just return the original email
        return email
    }
    
    protected open fun formatOutput(processedContent: String, originalEmail: Email): String {
        // Default formatting
        return """
            Subject: ${originalEmail.subject}
            From: ${originalEmail.from}
            Date: ${originalEmail.date}
            
            Processed Content:
            $processedContent
        """.trimIndent()
    }
    
    protected open fun postProcess(email: Email, output: String) {
        // Default post-processing - do nothing
        Log.d(tag, "Post-processing completed for email: ${email.subject}")
    }
    
    protected open fun handleError(error: Exception, email: Email) {
        // Default error handling
        Log.e(tag, "Error processing email '${email.subject}': ${error.message}")
    }
}

/**
 * Concrete implementation for Telegram pipeline
 */
class TelegramPipelineTemplate(
    private val promptStrategy: PromptStrategy,
    private val telegramService: TelegramService
) : EmailProcessingTemplate() {
    
    override fun applyLlmProcessing(email: Email): String? {
        return try {
            // Convert Email to SourceMessage for the strategy
            val sourceMessage = MessageSourceService.SourceMessage(
                id = email.id,
                subject = email.subject,
                content = email.content,
                source = "Template",
                metadata = mapOf(
                    "from" to email.from,
                    "date" to email.date
                )
            )
            val prompt = promptStrategy.buildPrompt(sourceMessage)
            // Here you would call your LLM service
            // For now, returning processed content
            "LLM processed: ${email.content}"
        } catch (e: Exception) {
            Log.e("TelegramPipelineTemplate", "LLM processing failed", e)
            null
        }
    }
    
    override fun sendToDestination(content: String, email: Email): Boolean {
        return try {
            // For template pattern, we'll use a simplified approach
            // In a real implementation, you might want to use coroutines or callbacks
            var result = false
            val completed = java.util.concurrent.CountDownLatch(1)
            
            telegramService.sendMessage(
                text = content,
                onSuccess = {
                    result = true
                    completed.countDown()
                },
                onError = { error ->
                    Log.e("TelegramPipelineTemplate", "Failed to send to Telegram: $error")
                    result = false
                    completed.countDown()
                }
            )
            
            // Wait for completion (with timeout)
            completed.await(30, java.util.concurrent.TimeUnit.SECONDS)
            result
        } catch (e: Exception) {
            Log.e("TelegramPipelineTemplate", "Failed to send to Telegram", e)
            false
        }
    }
    
    override fun preprocessEmail(email: Email): Email? {
        // Telegram-specific preprocessing
        return email.copy(
            subject = email.subject.take(100), // Limit subject length
            content = email.content.take(4000) // Limit content length for Telegram
        )
    }
    
    override fun formatOutput(processedContent: String, originalEmail: Email): String {
        // Telegram-specific formatting
        return """
            📧 *Email Alert*
            
            *Subject:* ${originalEmail.subject}
            *From:* ${originalEmail.from}
            
            *Analysis:*
            $processedContent
        """.trimIndent()
    }
}

/**
 * Concrete implementation for testing/debugging
 */
class DebugPipelineTemplate : EmailProcessingTemplate() {
    
    override fun applyLlmProcessing(email: Email): String? {
        // Debug implementation - just return debug info
        return "DEBUG: Processing email with ${email.content.length} characters"
    }
    
    override fun sendToDestination(content: String, email: Email): Boolean {
        // Debug implementation - just log
        Log.d("DebugPipeline", "Would send: $content")
        return true
    }
    
    override fun validateEmail(email: Email): Boolean {
        // More lenient validation for debugging
        return true
    }
    
    override fun postProcess(email: Email, output: String) {
        Log.d("DebugPipeline", "Debug post-processing: Email '${email.subject}' -> Output length: ${output.length}")
    }
}

/**
 * Concrete implementation for batch processing
 */
class BatchProcessingTemplate(
    private val promptStrategy: PromptStrategy,
    private val outputHandler: (String, Email) -> Boolean
) : EmailProcessingTemplate() {
    
    private val processedEmails = mutableListOf<Pair<Email, String>>()
    
    override fun applyLlmProcessing(email: Email): String? {
        return try {
            // Convert Email to SourceMessage for the strategy
            val sourceMessage = MessageSourceService.SourceMessage(
                id = email.id,
                subject = email.subject,
                content = email.content,
                source = "Template",
                metadata = mapOf(
                    "from" to email.from,
                    "date" to email.date
                )
            )
            val prompt = promptStrategy.buildPrompt(sourceMessage)
            // Batch-optimized LLM processing
            "Batch processed: ${email.content.substring(0, minOf(100, email.content.length))}"
        } catch (e: Exception) {
            Log.e("BatchProcessingTemplate", "Batch LLM processing failed", e)
            null
        }
    }
    
    override fun sendToDestination(content: String, email: Email): Boolean {
        // Store for batch sending
        processedEmails.add(email to content)
        return outputHandler(content, email)
    }
    
    override fun formatOutput(processedContent: String, originalEmail: Email): String {
        // Compact format for batch processing
        return "${originalEmail.subject}: $processedContent"
    }
    
    fun getBatchResults(): List<Pair<Email, String>> {
        return processedEmails.toList()
    }
    
    fun clearBatch() {
        processedEmails.clear()
    }
}

/**
 * Concrete implementation for Gmail delivery from Telegram messages
 */
class GmailDeliveryTemplate(
    private val promptStrategy: PromptStrategy,
    private val gmailService: com.example.llmapp.GmailService,
    private val emailRecipients: List<String>,
    private val subjectTemplate: String = "Telegram Message Summary",
    private val llmProcessor: LlmContentProcessor? = null,
    private val processOnlyMode: Boolean = false // Flag to control email sending behavior
) : TelegramMessageProcessingTemplate() {
    
    override fun applyLlmProcessing(message: TelegramMessage): String? {
        return try {
            // Validate input message
            if (message.content.isBlank()) {
                Log.w("GmailDeliveryTemplate", "Message content is blank, skipping LLM processing")
                return "No content to process"
            }
            
            // Convert TelegramMessage to SourceMessage for the strategy
            val sourceMessage = MessageSourceService.SourceMessage(
                id = message.id,
                subject = "Telegram message from ${message.senderName}",
                content = message.content,
                source = "Telegram",
                metadata = mapOf(
                    "from" to message.senderName,
                    "date" to message.timestamp,
                    "chat_type" to message.chatType
                )
            )
            
            // Check if we have a real LLM processor available
            if (llmProcessor != null && llmProcessor.isReady()) {
                Log.d("GmailDeliveryTemplate", "Using real LLM processing for message from ${message.senderName}")
                
                // Use real LLM processing with synchronous approach
                var result: String? = null
                var error: String? = null
                val processingLatch = java.util.concurrent.CountDownLatch(1)
                
                // Note: Do not reset prompt strategy here - preserve user's custom prompt
                
                llmProcessor.processContent(
                    message = sourceMessage,
                    onComplete = { processedContent ->
                        result = processedContent
                        processingLatch.countDown()
                    },
                    onError = { errorMessage ->
                        error = errorMessage
                        processingLatch.countDown()
                    }
                )
                
                // Wait for LLM processing to complete (with timeout)
                val completed = processingLatch.await(60, java.util.concurrent.TimeUnit.SECONDS)
                
                if (!completed) {
                    Log.e("GmailDeliveryTemplate", "LLM processing timed out")
                    return "Error: LLM processing timed out"
                }
                
                if (error != null) {
                    Log.e("GmailDeliveryTemplate", "LLM processing failed: $error")
                    return "Error: $error"
                }
                
                if (result != null) {
                    Log.d("GmailDeliveryTemplate", "Successfully processed message using real LLM")
                    return result
                }
            }
            
            // Fallback to simulated processing if LLM processor is not available
            Log.w("GmailDeliveryTemplate", "LLM processor not available, using simulated processing")
            
            // Build prompt using strategy with error handling (for logging purposes)
            val prompt = try {
                promptStrategy.buildPrompt(sourceMessage)
            } catch (e: Exception) {
                Log.e("GmailDeliveryTemplate", "Failed to build prompt", e)
                return "Error: Failed to build prompt - ${e.message}"
            }
            
            // Return simulated processed summary as fallback
            val processedContent = """
                📝 AI Analysis Summary (Simulated):
                
                From: ${message.senderName}
                Time: ${message.timestamp}
                Chat: ${message.chatType}
                
                Message Summary: ${message.content.take(100)}${if (message.content.length > 100) "..." else ""}
                
                Key Points:
                • Message contains ${message.content.split(" ").size} words
                • Sender: ${message.senderName}
                • Chat type: ${message.chatType}
                
                Note: This is a simulated AI analysis. LLM processor was not available.
            """.trimIndent()
            
            Log.d("GmailDeliveryTemplate", "Used fallback simulated processing for message from ${message.senderName}")
            processedContent
            
        } catch (e: Exception) {
            Log.e("GmailDeliveryTemplate", "LLM processing failed", e)
            "Error processing message: ${e.message}"
        }
    }
    
    override fun sendEmail(content: String, message: TelegramMessage): Boolean {
        return try {
            // If in process-only mode, skip email sending but return success
            if (processOnlyMode) {
                Log.d("GmailDeliveryTemplate", "Process-only mode: Skipping email sending for consolidated processing")
                return true
            }
            
            // Validate inputs
            if (content.isBlank()) {
                Log.w("GmailDeliveryTemplate", "Email content is blank")
                return false
            }
            
            if (emailRecipients.isEmpty()) {
                Log.w("GmailDeliveryTemplate", "No email recipients configured")
                return false
            }
            
            // Log the email sending attempt
            Log.d("GmailDeliveryTemplate", "Sending email to: ${emailRecipients.joinToString(", ")}")
            Log.d("GmailDeliveryTemplate", "Subject: $subjectTemplate")
            Log.d("GmailDeliveryTemplate", "Content preview: ${content.take(100)}...")
            
            // Call the actual Gmail service to send email using runBlocking for suspend function
            try {
                val success = kotlinx.coroutines.runBlocking {
                    gmailService.sendEmail(
                        to = emailRecipients, // Pass the list directly
                        subject = subjectTemplate,
                        body = content,
                        isHtml = true // Enable HTML formatting
                    )
                }
                
                if (success) {
                    Log.d("GmailDeliveryTemplate", "Email sent successfully")
                    return true
                } else {
                    Log.e("GmailDeliveryTemplate", "Gmail service returned false - email was not sent")
                    return false
                }
            } catch (e: Exception) {
                Log.e("GmailDeliveryTemplate", "Failed to send email via Gmail service", e)
                return false
            }
            
        } catch (e: Exception) {
            Log.e("GmailDeliveryTemplate", "Failed to send email", e)
            false
        }
    }
    
    /**
     * Public method to get LLM processing result without sending email
     * Used by consolidated commands to extract LLM results
     */
    fun getLlmProcessingResult(message: TelegramMessage): String? {
        return applyLlmProcessing(message)
    }
    
    override fun formatEmailContent(processedContent: String, originalMessage: TelegramMessage): String {
        // Gmail-specific formatting with HTML
        return """
            <html>
            <body>
            <h2>📱 Telegram Message Summary</h2>
            
            <div style="background-color: #e3f2fd; padding: 15px; border-radius: 8px; margin: 10px 0;">
                <h3>Message Details</h3>
                <p><strong>From:</strong> ${originalMessage.senderName}</p>
                <p><strong>Time:</strong> ${originalMessage.timestamp}</p>
                <p><strong>Chat Type:</strong> ${originalMessage.chatType}</p>
            </div>
            
            <div style="background-color: #f3e5f5; padding: 15px; border-radius: 8px; margin: 10px 0;">
                <h3>🤖 AI Analysis</h3>
                <pre style="white-space: pre-wrap; font-family: Arial, sans-serif;">$processedContent</pre>
            </div>
            
            <div style="background-color: #e8f5e8; padding: 15px; border-radius: 8px; margin: 10px 0;">
                <h3>💬 Original Message</h3>
                <pre style="white-space: pre-wrap; font-family: Arial, sans-serif;">${originalMessage.content}</pre>
            </div>
            
            <p><small><em>This email was automatically generated from a Telegram message on ${java.util.Date()}</em></small></p>
            </body>
            </html>
        """.trimIndent()
    }
    
    override fun preprocessMessage(message: TelegramMessage): TelegramMessage? {
        // Gmail-specific preprocessing
        return message.copy(
            content = message.content.take(5000), // Limit content length for email
            senderName = message.senderName.take(100) // Limit sender name length
        )
    }
}

/**
 * Template Method Pattern for Telegram message processing pipeline
 * Defines the skeleton for processing Telegram messages and sending emails
 */
abstract class TelegramMessageProcessingTemplate {
    private val tag = "TelegramMessageTemplate"
    
    /**
     * Template method defining the overall algorithm for Telegram to Gmail processing
     */
    fun processMessage(message: TelegramMessage): Boolean {
        Log.d(tag, "Starting Telegram message processing template")
        
        try {
            // Step 1: Validate input
            if (!validateMessage(message)) {
                Log.e(tag, "Message validation failed")
                return false
            }
            
            // Step 2: Pre-process message
            val preprocessedMessage = preprocessMessage(message)
            if (preprocessedMessage == null) {
                Log.e(tag, "Message preprocessing failed")
                return false
            }
            
            // Step 3: Apply LLM processing
            val processedContent = applyLlmProcessing(preprocessedMessage)
            if (processedContent.isNullOrBlank()) {
                Log.e(tag, "LLM processing failed")
                return false
            }
            
            // Step 4: Format email content
            val emailContent = formatEmailContent(processedContent, preprocessedMessage)
            if (emailContent.isNullOrBlank()) {
                Log.e(tag, "Email formatting failed")
                return false
            }
            
            // Step 5: Send email
            val success = sendEmail(emailContent, preprocessedMessage)
            if (!success) {
                Log.e(tag, "Failed to send email")
                return false
            }
            
            // Step 6: Post-process (optional hook)
            postProcess(preprocessedMessage, emailContent)
            
            Log.d(tag, "Telegram message processing completed successfully")
            return true
            
        } catch (e: Exception) {
            Log.e(tag, "Error in Telegram message processing template", e)
            handleError(e, message)
            return false
        }
    }
    
    /**
     * Abstract methods - must be implemented by subclasses
     */
    protected abstract fun applyLlmProcessing(message: TelegramMessage): String?
    protected abstract fun sendEmail(content: String, message: TelegramMessage): Boolean
    
    /**
     * Hook methods with default implementations - can be overridden
     */
    protected open fun validateMessage(message: TelegramMessage): Boolean {
        return message.content.isNotBlank()
    }
    
    protected open fun preprocessMessage(message: TelegramMessage): TelegramMessage? {
        // Default preprocessing - just return the original message
        return message
    }
    
    protected open fun formatEmailContent(processedContent: String, originalMessage: TelegramMessage): String {
        // Default formatting
        return """
            Telegram Message Summary
            
            From: ${originalMessage.senderName}
            Time: ${originalMessage.timestamp}
            
            Processed Analysis:
            $processedContent
            
            Original Message:
            ${originalMessage.content}
        """.trimIndent()
    }
    
    protected open fun postProcess(message: TelegramMessage, emailContent: String) {
        // Default post-processing - do nothing
        Log.d(tag, "Post-processing completed for message from: ${message.senderName}")
    }
    
    protected open fun handleError(error: Exception, message: TelegramMessage) {
        // Default error handling
        Log.e(tag, "Error processing Telegram message from '${message.senderName}': ${error.message}")
    }
}

/**
 * Data class for Telegram messages
 */
data class TelegramMessage(
    val id: String,
    val content: String,
    val senderName: String,
    val timestamp: String,
    val chatType: String = "private",
    val metadata: Map<String, String> = emptyMap()
)

/**
 * Factory for creating pipeline templates
 */
object PipelineTemplateFactory {
    
    fun createTelegramTemplate(
        promptStrategy: PromptStrategy,
        telegramService: TelegramService
    ): EmailProcessingTemplate {
        return TelegramPipelineTemplate(promptStrategy, telegramService)
    }
    
    fun createDebugTemplate(): EmailProcessingTemplate {
        return DebugPipelineTemplate()
    }
    
    fun createBatchTemplate(
        promptStrategy: PromptStrategy,
        outputHandler: (String, Email) -> Boolean
    ): EmailProcessingTemplate {
        return BatchProcessingTemplate(promptStrategy, outputHandler)
    }
    
    fun createTelegramToGmailTemplate(
        promptStrategy: PromptStrategy,
        gmailService: com.example.llmapp.GmailService,
        emailRecipients: List<String>,
        subjectTemplate: String = "Telegram Message Summary"
    ): TelegramMessageProcessingTemplate {
        return GmailDeliveryTemplate(promptStrategy, gmailService, emailRecipients, subjectTemplate)
    }
    
    /**
     * Create a Telegram to Gmail template with real LLM processing
     */
    fun createTelegramToGmailTemplateWithLlm(
        promptStrategy: PromptStrategy,
        gmailService: com.example.llmapp.GmailService,
        emailRecipients: List<String>,
        llmProcessor: LlmContentProcessor,
        subjectTemplate: String = "Telegram Message Summary"
    ): TelegramMessageProcessingTemplate {
        return GmailDeliveryTemplate(promptStrategy, gmailService, emailRecipients, subjectTemplate, llmProcessor)
    }
}
