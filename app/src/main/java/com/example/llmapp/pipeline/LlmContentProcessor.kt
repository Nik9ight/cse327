package com.example.llmapp.pipeline

import android.content.Context
import android.util.Log
import com.example.llmapp.Model
import com.example.llmapp.MODEL_TEXT_CLASSIFICATION_MOBILEBERT
import com.example.llmapp.interfaces.MessageSourceService
import com.example.llmapp.llmChatView
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Implementation of ContentProcessor using LLM
 */
class LlmContentProcessor(
    private val context: Context,
    private val model: Model = MODEL_TEXT_CLASSIFICATION_MOBILEBERT.copy(llmSupportImage = false)
) : ContentProcessor {
    private val TAG = "LlmContentProcessor"
    private val chatView: llmChatView
    private val isProcessing = AtomicBoolean(false)
    private var isInitialized = false
    private var customPrompt: String? = null
    
    init {
        chatView = llmChatView(model)
        // Initialize the chat view
        chatView.initialize(context) { error ->
            if (error.isNotEmpty()) {
                Log.e(TAG, "LLM initialization error: $error")
            } else {
                isInitialized = true
                Log.d(TAG, "LLM initialized successfully")
            }
        }
    }
    
    override fun processContent(
        message: MessageSourceService.SourceMessage,
        onComplete: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        if (!isReady()) {
            onError("LLM processor is not ready")
            return
        }
        
        // Check if already processing
        if (!isProcessing.compareAndSet(false, true)) {
            onError("LLM is already processing another request")
            return
        }
        
        try {
            Log.d(TAG, "Processing message: ${message.subject}")
            
            // Extract message details
            val subject = sanitizeText(message.subject, 100)
            val content = sanitizeText(message.content, 5000)
            val from = sanitizeText(message.metadata["from"] ?: "", 100)
            
            // Build a prompt for LLM
            val prompt = buildPrompt(subject, content)
            
            // Log the actual prompt being used
            Log.d(TAG, "Using prompt for LLM processing: ${prompt.take(100)}${if (prompt.length > 100) "..." else ""}")
            
            // Accumulate output
            val outputAccumulator = StringBuilder()
            var isCompleted = false
            
            chatView.generateResponse(
                context = context,
                input = prompt,
                images = listOf(),
                onResult = { result: String, done: Boolean ->
                    Log.d(TAG, "LLM result chunk received, done=$done")
                    outputAccumulator.append(result)
                    
                    if (done && !isCompleted) {
                        isCompleted = true
                        isProcessing.set(false)
                        
                        val finalOutput = outputAccumulator.toString()
                        if (finalOutput.trim().isEmpty()) {
                            onError("LLM produced no output")
                        } else {
                            onComplete(finalOutput)
                        }
                    }
                },
                onError = { error: String ->
                    if (!isCompleted) {
                        isCompleted = true
                        isProcessing.set(false)
                        Log.e(TAG, "LLM processing error: $error")
                        onError("Error processing: $error")
                    }
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Exception during LLM processing", e)
            isProcessing.set(false)
            onError("Exception during processing: ${e.message ?: "Unknown error"}")
        }
    }
    
    override fun isReady(): Boolean {
        return isInitialized && !isProcessing.get()
    }
    
    override fun cleanup() {
        Log.d(TAG, "Cleaning up LLM processor")
        if (isProcessing.get()) {
            // Stop any ongoing processing
            chatView.stopResponse(model)
        }
        // Clean up resources
        model.instance = null
        isInitialized = false
        isProcessing.set(false)
    }
    
    /**
     * Set a custom prompt template
     * This template will be used as the instruction part, and email details
     * (subject, content, from, date) will automatically be appended
     */
    fun setCustomPrompt(prompt: String?) {
        customPrompt = prompt
        Log.d(TAG, "Custom prompt set: ${prompt?.take(50)}${if (prompt?.length ?: 0 > 50) "..." else ""}")
        
        // Basic validation
        if (prompt.isNullOrBlank()) {
            Log.w(TAG, "Warning: Empty or null custom prompt provided")
        } else {
            Log.d(TAG, "Custom prompt accepted - email details will be automatically appended")
        }
    }
    
    // Helper method to build a prompt
    private fun buildPrompt(subject: String, content: String): String {
        // Extract message details
        val from = "" // In the actual method we would get this from the message
        val date = "" // In the actual method we would get this from the message
        
        // Use custom prompt if available, otherwise use default
        if (customPrompt != null && customPrompt!!.isNotBlank()) {
            // Use the custom prompt as the instruction part, then always append the email details
            return """
                ${customPrompt!!.trim()}
                
                Subject: $subject
                
                Content:
                $content
                
                From: $from
                Date: $date
            """.trimIndent()
        }
        
        // Default prompt
        return """
            Summarize the following email and make it as brief as possible while maintaining key information.
            
            Subject: $subject
            
            Content:
            $content
            
            From: $from
            Date: $date
        """.trimIndent()
    }
    
    // Helper method to build a test input
    private fun buildTestInput(subject: String, sender: String): String {
        return """
            Summarize the following email from ${sanitizeText(sender, 50)} with subject "${sanitizeText(subject, 50)}".
            Make it brief and professional.
        """.trimIndent()
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
}
