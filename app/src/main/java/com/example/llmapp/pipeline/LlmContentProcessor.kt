package com.example.llmapp.pipeline

import android.content.Context
import android.util.Log
import com.example.llmapp.Model
import com.example.llmapp.MODEL_TEXT_CLASSIFICATION_MOBILEBERT
import com.example.llmapp.interfaces.MessageSourceService
import com.example.llmapp.llmChatView
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Implementation of ContentProcessor using LLM with Strategy Pattern support
 * 
 * DESIGN PATTERNS IMPLEMENTED:
 * - Strategy Pattern: For flexible prompt generation (PromptStrategy)
 * - Template Method: Base processing flow with customizable prompt building
 * 
 * EXTENSIBILITY FEATURES:
 * - Can switch prompt strategies without modifying processor code
 * - Backward compatible with existing setCustomPrompt method
 * - Easy to add new prompt types (analysis, classification, etc.)
 */
class LlmContentProcessor(
    private val context: Context,
    private val model: Model = MODEL_TEXT_CLASSIFICATION_MOBILEBERT.copy(llmSupportImage = false),
    private var promptStrategy: PromptStrategy = DefaultPromptStrategy()
) : ContentProcessor {
    private val TAG = "LlmContentProcessor"
    private val chatView: llmChatView
    private val isProcessing = AtomicBoolean(false)
    private var isInitialized = false
    
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
        // Log essential information for debugging
        Log.d(TAG, "Processing request - Subject: ${message.subject.take(30)}... Content size: ${message.content.length} chars")
        
        if (!isReady()) {
            Log.w(TAG, "LLM processor not ready - initialized: $isInitialized, processing: ${isProcessing.get()}")
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
            
            // Build prompt using strategy pattern
            val prompt = promptStrategy.buildPrompt(message)
            
            // Log the actual prompt being used
            Log.d(TAG, "Using prompt for LLM processing: ${prompt.take(100)}${if (prompt.length > 100) "..." else ""}")
            Log.d(TAG, "Prompt strategy: ${promptStrategy::class.simpleName}")
            
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
                        
                        // Enhanced error logging
                        Log.e(TAG, "LLM processing error: $error")
                        Log.e(TAG, "Input size was: ${prompt.length} characters")
                        
                        // Handle %s placeholder error specially
                        val cleanedError = if (error.contains("%s")) {
                            "Input too large or format error. Try with a shorter email."
                        } else {
                            "Error processing: $error"
                        }
                        
                        onError(cleanedError)
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
     * Set a new prompt strategy for content processing
     * This allows changing the prompt generation behavior without modifying the processor
     */
    fun setPromptStrategy(strategy: PromptStrategy) {
        this.promptStrategy = strategy
        Log.d(TAG, "Prompt strategy changed to: ${strategy::class.simpleName}")
    }
    
    /**
     * Set a custom prompt template (backward compatibility)
     * This creates a CustomPromptStrategy internally
     */
    fun setCustomPrompt(prompt: String?) {
        if (prompt.isNullOrBlank()) {
            Log.w(TAG, "Empty prompt provided, using default strategy")
            promptStrategy = DefaultPromptStrategy()
        } else {
            Log.d(TAG, "Custom prompt set: ${prompt.take(50)}${if (prompt.length > 50) "..." else ""}")
            promptStrategy = CustomPromptStrategy(prompt)
        }
    }
    
    /**
     * Get the current prompt strategy
     */
    fun getPromptStrategy(): PromptStrategy = promptStrategy
    
    // Removed old buildPrompt method - now handled by strategy pattern
    
    // Helper method to sanitize text (kept for backward compatibility)
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
