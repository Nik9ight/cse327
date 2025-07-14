package com.example.llmapp

import android.content.Context
import android.util.Log
import androidx.work.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.util.concurrent.TimeUnit

class GmailToTelegramPipeline(
    private val context: Context,
    private val telegramService: TelegramService? = null,
    private val model: Model? = MODEL_TEXT_CLASSIFICATION_MOBILEBERT.copy(llmSupportImage = false),
    private val gmailService: GmailService? = null
) {
    private lateinit var chatView: llmChatView
    private var completionListener: PipelineCompletionListener? = null
    
    /**
     * Interface for pipeline completion callbacks
     */
    interface PipelineCompletionListener {
        fun onComplete(success: Boolean, message: String)
    }
    
    /**
     * Set a completion listener to be notified when processing is complete
     */
    fun setCompletionListener(listener: PipelineCompletionListener) {
        completionListener = listener
    }
    
    constructor(context: Context) : this(context, null, MODEL_TEXT_CLASSIFICATION_MOBILEBERT.copy(llmSupportImage = false), null)
    
    init {
        val modelToUse = model ?: MODEL_TEXT_CLASSIFICATION_MOBILEBERT.copy(llmSupportImage = false)
        chatView = llmChatView(modelToUse)
        chatView.initialize(context) { error ->
            if (error.isNotEmpty()) {
                telegramService?.sendMessage(
                    "Error initializing LLM: $error",
                    onSuccess = {},
                    onError = { errMsg -> 
                        // Log error
                        Log.e("GmailToTelegramPipeline", "LLM init error: $errMsg")
                    }
                )
                completionListener?.onComplete(false, "LLM initialization error: $error")
            }
        }
    }
    
    // Process Gmail message and send to Telegram
    fun processEmail(subject: String, content: String, telegramService: TelegramService? = null, from: String = "", date: String = "") {
        val telegramSvc = telegramService ?: this.telegramService
        if (telegramSvc == null) {
            Log.e("GmailToTelegramPipeline", "No TelegramService provided")
            completionListener?.onComplete(false, "No TelegramService provided")
            return
        }
        
        val prompt = buildPrompt(subject, content)
        
        // Process with LLM and send to Telegram
        processWithLLM(prompt) { llmResponse ->
            val message = buildTelegramMessage(subject, llmResponse, from, date)
            telegramSvc.sendMessage(
                message,
                onSuccess = {
                    // Message sent successfully
                    Log.d("GmailToTelegramPipeline", "Message sent successfully")
                    completionListener?.onComplete(true, "Email processed and sent to Telegram")
                },
                onError = { error ->
                    // Handle error
                    Log.e("GmailToTelegramPipeline", "Failed to send message: $error")
                    completionListener?.onComplete(false, "Failed to send message: $error")
                }
            )
        }
    }
    
    private fun buildPrompt(subject: String, content: String): String {
        return """
            Process this email and provide a concise summary and make it as brief as possible.
            
            Subject: $subject
            
            Content:
            $content
        """.trimIndent()
    }
    
    private fun processWithLLM(input: String, onComplete: (String) -> Unit) {
        val outputAccumulator = StringBuilder()
        var isCompleted = false
        
        try {
            Log.d("GmailToTelegramPipeline", "Starting LLM processing with input length: ${input.length}")
            
            chatView.generateResponse(
                context = context,
                input = input,
                images = listOf(),
                onResult = { result, done ->
                    Log.d("GmailToTelegramPipeline", "LLM result chunk received, done=$done")
                    outputAccumulator.append(result)
                    
                    if (done && !isCompleted) {
                        isCompleted = true
                        Log.d("GmailToTelegramPipeline", "LLM processing complete")
                        onComplete(outputAccumulator.toString())
                    }
                },
                onError = { error ->
                    if (!isCompleted) {
                        isCompleted = true
                        Log.e("GmailToTelegramPipeline", "LLM processing error: $error")
                        onComplete("Error processing: $error")
                    }
                }
            )
        } catch (e: Exception) {
            if (!isCompleted) {
                isCompleted = true
                Log.e("GmailToTelegramPipeline", "Exception during LLM processing", e)
                onComplete("Error: ${e.message}")
            }
        }
    }
    
    private fun buildTelegramMessage(subject: String, llmResponse: String, from: String = "", date: String = ""): String {
        val fromSection = if (from.isNotEmpty()) "\n\n<b>From:</b> ${escapeHtml(from)}" else ""
        val dateSection = if (date.isNotEmpty()) "\n<b>Date:</b> ${escapeHtml(date)}" else ""
        
        // Sanitize the LLM response to avoid HTML parsing errors
        val sanitizedResponse = escapeHtml(llmResponse)
        
        return """
            📧 <b>Email Processed</b>
            
            <b>Subject:</b> ${escapeHtml(subject)}$fromSection$dateSection
            
            <b>LLM Response:</b>
            $sanitizedResponse
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
    
    // Schedule periodic processing (if needed)
    fun schedulePeriodicProcessing(intervalMinutes: Long = 15) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
            
        val workRequest = PeriodicWorkRequestBuilder<EmailProcessingWorker>(
            intervalMinutes, TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .build()
            
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(
                "gmail_processing",
                ExistingPeriodicWorkPolicy.REPLACE,
                workRequest
            )
    }
    
    // For demonstration purposes - process a sample email
    fun processSampleEmail(onComplete: ((Boolean, String) -> Unit)? = null) {
        val sampleSubject = "Meeting tomorrow at 2 PM"
        val sampleContent = """
            Hello team,
            
            Just a reminder that we have a project status meeting tomorrow at 2 PM in Conference Room A.
            Please bring your weekly progress reports and be prepared to discuss any blockers.
            
            Best regards,
            Project Manager
        """.trimIndent()
        
        val sampleFrom = "project.manager@example.com"
        val sampleDate = "Mon, 09 Jul 2025 10:30:45 +0000"
        
        // Set a temporary completion listener if one was provided
        if (onComplete != null) {
            val tempListener = object : PipelineCompletionListener {
                override fun onComplete(success: Boolean, message: String) {
                    onComplete(success, message)
                }
            }
            setCompletionListener(tempListener)
        }
        
        processEmail(sampleSubject, sampleContent, null, sampleFrom, sampleDate)
    }
    
    // Worker class for background processing
    class EmailProcessingWorker(
        context: Context, 
        params: WorkerParameters
    ) : Worker(context, params) {
        
        override fun doWork(): Result {
            try {
                Log.d("EmailProcessingWorker", "Starting email processing job")
                
                val gmailService = GmailService(applicationContext)
                val telegramService = TelegramService(applicationContext)
                val model = MODEL_TEXT_CLASSIFICATION_MOBILEBERT.copy(llmSupportImage = false)
                
                // Check if services are configured properly
                if (!gmailService.isSignedIn() || !telegramService.isLoggedIn()) {
                    Log.w("EmailProcessingWorker", "Services not configured properly")
                    return Result.retry() // Retry later, user might sign in
                }
                
                val pipeline = GmailToTelegramPipeline(
                    context = applicationContext,
                    telegramService = telegramService,
                    model = model,
                    gmailService = gmailService
                )
                
                // Use coroutine scope to handle async operations
                val processed = runBlocking {
                    try {
                        // Get unread emails (limit to 5 to avoid processing too many at once)
                        val emails = gmailService.getUnreadEmails(3)
                        var processedCount = 0
                        
                        // Process each email sequentially
                        for (email in emails) {
                            Log.d("EmailProcessingWorker", "Processing email: '${email.subject}' from '${email.from}' dated '${email.date}'")
                            
                            // Process email and wait for completion using suspending approach
                            var processingComplete = false
                            var processingSuccess = false
                            var processingMessage = ""
                            
                            pipeline.setCompletionListener(object : GmailToTelegramPipeline.PipelineCompletionListener {
                                override fun onComplete(success: Boolean, message: String) {
                                    processingComplete = true
                                    processingSuccess = success
                                    processingMessage = message
                                }
                            })
                            
                            pipeline.processEmail(email.subject, email.content, telegramService, email.from, email.date)
                            
                            // Wait for completion with timeout
                            var timeoutCounter = 0
                            while (!processingComplete && timeoutCounter < 60) { // 60 second timeout
                                delay(1000)
                                timeoutCounter++
                            }
                            
                            if (!processingComplete) {
                                Log.w("EmailProcessingWorker", "Processing timed out for email: ${email.subject}")
                                continue
                            }
                            
                            if (processingSuccess) {
                                // Mark as read after successful processing
                                gmailService.markAsRead(email.id)
                                processedCount++
                                Log.d("EmailProcessingWorker", "Successfully processed email: ${email.subject}")
                            } else {
                                Log.e("EmailProcessingWorker", "Failed to process email: $processingMessage")
                            }
                            
                            // Add delay to avoid rate limiting
                            delay(2000)
                        }
                        
                        if (processedCount > 0) {
                            Log.i("EmailProcessingWorker", "Processed $processedCount emails")
                        } else {
                            Log.i("EmailProcessingWorker", "No emails processed")
                        }
                        
                        processedCount > 0
                    } catch (e: Exception) {
                        Log.e("EmailProcessingWorker", "Error processing emails", e)
                        false
                    }
                }
                
                return if (processed) Result.success() else Result.retry()
            } catch (e: Exception) {
                Log.e("EmailProcessingWorker", "Worker execution failed", e)
                return Result.failure()
            }
        }
    }
}
