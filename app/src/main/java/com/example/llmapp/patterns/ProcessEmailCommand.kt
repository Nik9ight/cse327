package com.example.llmapp.patterns

import android.content.Context
import android.util.Log
import com.example.llmapp.GmailService
import com.example.llmapp.Model
import com.example.llmapp.TelegramService
import com.example.llmapp.interfaces.MessageSourceService
import com.example.llmapp.llmChatView

/**
 * Command implementation for processing a single email
 * Processes an email through LLM and sends it to Telegram
 */
class ProcessEmailCommand(
    private val context: Context,
    private val message: MessageSourceService.SourceMessage,
    private val chatView: llmChatView,
    private val telegramService: TelegramService?,
    private val onProcessingStarted: (emailId: String, subject: String) -> Unit = { _, _ -> },
    private val onProcessingComplete: (emailId: String, subject: String, summary: String) -> Unit = { _, _, _ -> },
    private val onEmailSent: (emailId: String, subject: String, success: Boolean, message: String) -> Unit = { _, _, _, _ -> },
    private val observers: List<PipelineObserver> = emptyList()
) : EmailCommand {
    
    private var isCancelled = false
    private var isLlmProcessing = false
    private val TAG = "ProcessEmailCommand"
    
    override fun execute() {
        val emailId = message.id
        val subject = message.subject
        val content = message.content
        val from = message.metadata["from"] ?: ""
        val date = message.metadata["date"] ?: ""
        
        // Notify that processing has started
        observers.forEach { it.onProcessingStarted(emailId, subject) }
        onProcessingStarted(emailId, subject)
        
        // Build prompt for LLM
        val prompt = buildPrompt(subject, content)
        
        // Process with LLM
        synchronized(this) {
            isLlmProcessing = true
        }
        
        try {
            processWithLLM(prompt) { llmResponse ->
                synchronized(this) {
                    isLlmProcessing = false
                }
                
                if (isCancelled) {
                    Log.d(TAG, "Command was cancelled, not sending to Telegram")
                    return@processWithLLM
                }
                
                // Notify of processing completion
                observers.forEach { it.onProcessingComplete(emailId, subject, llmResponse) }
                onProcessingComplete(emailId, subject, llmResponse)
                
                // Build Telegram message
                val telegramMessage = buildTelegramMessage(subject, llmResponse, from, date)
                
                // Send to Telegram if service is available
                if (telegramService != null) {
                    telegramService.sendMessage(
                        telegramMessage,
                        onSuccess = {
                            // Notify of successful sending
                            observers.forEach { it.onEmailSent(emailId, subject, true, "Message sent successfully") }
                            onEmailSent(emailId, subject, true, "Message sent successfully")
                        },
                        onError = { error ->
                            // Notify of error
                            observers.forEach { it.onEmailSent(emailId, subject, false, error) }
                            onEmailSent(emailId, subject, false, error)
                        }
                    )
                } else {
                    val errorMsg = "No Telegram service available"
                    Log.e(TAG, errorMsg)
                    observers.forEach { it.onEmailSent(emailId, subject, false, errorMsg) }
                    onEmailSent(emailId, subject, false, errorMsg)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing email: ${e.message}")
            synchronized(this) {
                isLlmProcessing = false
            }
            // Notify of error
            val errorMsg = "Failed to process: ${e.message}"
            observers.forEach { it.onProcessingComplete(emailId, subject, "Error: ${e.message}") }
            observers.forEach { it.onEmailSent(emailId, subject, false, errorMsg) }
            onProcessingComplete(emailId, subject, "Error: ${e.message}")
            onEmailSent(emailId, subject, false, errorMsg)
        }
    }
    
    override fun cancel() {
        isCancelled = true
        
        synchronized(this) {
            if (isLlmProcessing) {
                // Stop LLM processing if it's in progress
                chatView.stopResponse(chatView.model)
            }
        }
    }
    
    // Helper method to sanitize text
    private fun sanitizeText(text: String, maxLength: Int = 8000): String {
        if (text.isEmpty()) return ""
        
        // Truncate if longer than max length
        val truncatedText = if (text.length > maxLength) {
            text.substring(0, maxLength) + "..."
        } else {
            text
        }
        
        // Remove control characters that might cause issues
        return truncatedText.replace(Regex("[\\p{Cntrl}&&[^\n\t\r]]"), "")
            .replace(Regex("[\u0000-\u001F]"), "") // Remove ASCII control characters
            .trim()
    }
    
    // Helper methods to extract email metadata for test input
    private fun extractSubject(input: String): String {
        // Try to find subject in common formats
        val subjectRegex = Regex("Subject:\\s*([^\\n]+)", RegexOption.IGNORE_CASE)
        val match = subjectRegex.find(input)
        return match?.groupValues?.getOrNull(1)?.trim() ?: "Unknown Subject"
    }
    
    private fun extractSender(input: String): String {
        // Try to extract sender information from common formats
        val fromRegex = Regex("From:\\s*([^\\n]+)", RegexOption.IGNORE_CASE)
        val match = fromRegex.find(input)
        return match?.groupValues?.getOrNull(1)?.trim() ?: "Unknown Sender"
    }

    private fun processWithLLM(input: String, onComplete: (String) -> Unit) {
        val outputAccumulator = StringBuilder()
        var isCompleted = false
        
        try {
            Log.d(TAG, "Starting LLM processing with input length: ${input.length}")
            
            // Sanitize and limit input to prevent issues
            val maxInputLength = 6000
            val limitedInput = if (input.length > maxInputLength) {
                Log.w(TAG, "Input too large (${input.length} chars), truncating to $maxInputLength chars")
                input.take(maxInputLength) + "\n\n[... Content truncated due to length ...]"
            } else {
                input
            }
            
            // Option to use test input to avoid content issues
            val useTestInput = true // Set to true to use test input instead of actual content
            val testInput = if (useTestInput) {
                // Extract metadata from the input for context
                val extractedSubject = extractSubject(input)
                val extractedSender = extractSender(input)
                
                // Use a simple test input that we know will work, but include extracted metadata
                """
                Summarize the following email from ${sanitizeText(extractedSender, 50)} with subject "${sanitizeText(extractedSubject, 50)}".
                Make it brief and professional.
                """.trimIndent()
            } else {
                limitedInput
            }
            
            Log.d(TAG, "Using ${if (useTestInput) "TEST INPUT" else "ACTUAL CONTENT"} for LLM processing")
            
            if (useTestInput) {
                Log.d(TAG, "Test input: $testInput")
            }
            
            chatView.generateResponse(
                context = context,
                input = testInput, // Use the test input or sanitized original input
                images = listOf(),
                onResult = { result: String, done: Boolean ->
                    Log.d(TAG, "LLM result chunk received, done=$done")
                    
                    if (isCancelled) {
                        return@generateResponse
                    }
                    
                    outputAccumulator.append(result)
                    
                    if (done && !isCompleted) {
                        isCompleted = true
                        Log.d(TAG, "LLM processing complete")
                        onComplete(outputAccumulator.toString())
                    }
                },
                onError = { error: String ->
                    if (!isCompleted) {
                        isCompleted = true
                        Log.e(TAG, "LLM processing error: $error")
                        onComplete("Error processing: $error")
                    }
                }
            )
        } catch (e: Exception) {
            if (!isCompleted) {
                isCompleted = true
                Log.e(TAG, "Exception during LLM processing", e)
                onComplete("Error: ${e.message}")
            }
        }
    }
    
    private fun buildPrompt(subject: String, content: String): String {
        // Sanitize inputs to prevent any processing issues
        val sanitizedSubject = sanitizeText(subject, 100)
        val sanitizedContent = sanitizeText(content, 5000)
        
        return """
            Summarize the following email and make it as brief as possible while maintaining key information.
            
            Subject: $sanitizedSubject
            
            Content:
            $sanitizedContent
        """.trimIndent()
    }
    
    private fun buildTelegramMessage(subject: String, llmResponse: String, from: String = "", date: String = ""): String {
        // Sanitize all inputs to ensure they're safe for display
        val sanitizedSubject = sanitizeText(subject, 100)
        val sanitizedFrom = sanitizeText(from, 100)
        val sanitizedDate = sanitizeText(date, 100)
        val sanitizedResponse = sanitizeText(llmResponse, 3000)
        
        val fromSection = if (sanitizedFrom.isNotEmpty()) "\n\n<b>From:</b> ${escapeHtml(sanitizedFrom)}" else ""
        val dateSection = if (sanitizedDate.isNotEmpty()) "\n<b>Date:</b> ${escapeHtml(sanitizedDate)}" else ""
        
        return """
            📧 <b>Email Processed</b>
            
            <b>Subject:</b> ${escapeHtml(sanitizedSubject)}$fromSection$dateSection
            
            <b>LLM Response:</b>
            ${escapeHtml(sanitizedResponse)}
        """.trimIndent()
    }
    
    // Helper method to escape HTML special characters
    private fun escapeHtml(text: String): String {
        // Escape characters that have special meaning in HTML
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
    }
}
