package com.example.llmapp.pipeline

import android.content.Context
import android.util.Log
import androidx.work.*
import com.example.llmapp.GmailService
import com.example.llmapp.MODEL_TEXT_CLASSIFICATION_MOBILEBERT
import com.example.llmapp.Model
import com.example.llmapp.TelegramService
import com.example.llmapp.interfaces.MessageSourceService
import com.example.llmapp.patterns.BroadcastPipelineObserver
import com.example.llmapp.patterns.PipelineObserver
import kotlinx.coroutines.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import java.util.concurrent.TimeUnit

/**
 * Coordinator class for the email processing pipeline
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class EmailProcessingPipeline(
    private val context: Context,
    fetchService: EmailFetchService? = null,
    contentProcessor: ContentProcessor? = null,
    deliveryService: MessageDeliveryService? = null,
    private val onCancellation: () -> Unit = {}
) {
    private val TAG = "EmailProcessingPipeline"
    
    // Services
    private val emailFetchService: EmailFetchService
    private val contentProcessor: ContentProcessor
    private val deliveryService: MessageDeliveryService
    
    /**
     * Get the email fetch service instance
     * This is needed for configuring search parameters
     */
    fun getEmailFetchService(): EmailFetchService = emailFetchService
    
    /**
     * Get the content processor instance
     * This is needed for configuring LLM prompt
     */
    fun getContentProcessor(): ContentProcessor = contentProcessor
    
    // Event management
    private val eventManager = PipelineEventManager()
    
    // Tracking
    private var successCount = 0
    private var failureCount = 0
    
    /**
     * Callback interface for pipeline completion
     */
    interface CompletionCallback {
        fun onComplete(success: Boolean, message: String)
    }
    
    private var completionCallback: CompletionCallback? = null
    
    init {
        // Create services if not provided
        this.emailFetchService = fetchService ?: GmailFetchService(context)
        this.contentProcessor = contentProcessor ?: LlmContentProcessor(context)
        this.deliveryService = deliveryService ?: TelegramDeliveryService(context)
        
        // Add default broadcast observer to bridge to the existing observer system
        addEventListenerAdapter(BroadcastPipelineObserver(context))
        
        Log.d(TAG, "EmailProcessingPipeline initialized")
    }
    
    /**
     * Set a completion callback to be notified when processing is complete
     */
    fun setCompletionCallback(callback: CompletionCallback) {
        this.completionCallback = callback
        
        // Bridge the completion callback to our event system
        addEventListener(object : PipelineEventListener {
            override fun onEmailFetched(emailId: String, subject: String) {}
            override fun onProcessingStarted(emailId: String, subject: String) {}
            override fun onProcessingComplete(emailId: String, subject: String, summary: String) {}
            override fun onEmailSent(emailId: String, subject: String, success: Boolean, message: String) {}
            
            override fun onBatchComplete(successCount: Int, failureCount: Int) {
                // Use success if at least one email was processed successfully
                val success = successCount > 0
                val message = if (successCount + failureCount == 0) {
                    "No emails to process"
                } else {
                    "Processed ${successCount + failureCount} emails: $successCount succeeded, $failureCount failed"
                }
                completionCallback?.onComplete(success, message)
            }
        })
    }
    
    /**
     * Add event listener
     */
    fun addEventListener(listener: PipelineEventListener) {
        eventManager.addListener(listener)
    }
    
    /**
     * Remove event listener
     */
    fun removeEventListener(listener: PipelineEventListener) {
        eventManager.removeListener(listener)
    }
    
    /**
     * Add legacy PipelineObserver by adapting it to the new event system
     */
    fun addEventListenerAdapter(observer: PipelineObserver) {
        // Create adapter to convert PipelineObserver to PipelineEventListener
        val adapter = object : PipelineEventListener {
            override fun onEmailFetched(emailId: String, subject: String) {
                observer.onEmailFetched(emailId, subject)
            }
            
            override fun onProcessingStarted(emailId: String, subject: String) {
                observer.onProcessingStarted(emailId, subject)
            }
            
            override fun onProcessingComplete(emailId: String, subject: String, summary: String) {
                observer.onProcessingComplete(emailId, subject, summary)
            }
            
            override fun onEmailSent(emailId: String, subject: String, success: Boolean, message: String) {
                observer.onEmailSent(emailId, subject, success, message)
            }
            
            override fun onBatchComplete(successCount: Int, failureCount: Int) {
                observer.onBatchComplete(successCount, failureCount)
            }
        }
        
        addEventListener(adapter)
    }
    
    /**
     * Check if all services are configured
     */
    fun isConfigured(): Boolean {
        return emailFetchService.isConfigured() && 
               contentProcessor.isReady() && 
               deliveryService.isConfigured()
    }
    
    /**
     * Process emails - main pipeline method
     */
    fun processEmails(count: Int = 3) {
        // Reset counters
        successCount = 0
        failureCount = 0
        
        if (!isConfigured()) {
            Log.e(TAG, "Pipeline is not properly configured")
            eventManager.notifyBatchComplete(0, 0)
            return
        }
        
        Log.d(TAG, "Starting email processing pipeline for $count emails")
        
        // Step 1: Fetch emails
        emailFetchService.fetchUnreadMessages(
            count,
            onSuccess = { messages ->
                Log.d(TAG, "Fetched ${messages.size} messages")
                
                if (messages.isEmpty()) {
                    Log.d(TAG, "No emails to process")
                    eventManager.notifyBatchComplete(0, 0)
                    return@fetchUnreadMessages
                }
                
                // Process messages sequentially
                processMessagesSequentially(messages)
            },
            onError = { error ->
                Log.e(TAG, "Error fetching messages: $error")
                eventManager.notifyBatchComplete(0, 0)
            }
        )
    }
    /**
 * Process emails with search query
 */
fun processEmailsWithSearch(searchQuery: String, count: Int = 3) {
    // Reset counters
    successCount = 0
    failureCount = 0
    
    if (!isConfigured()) {
        Log.e(TAG, "Pipeline is not properly configured")
        eventManager.notifyBatchComplete(0, 0)
        return
    }
    
    Log.d(TAG, "Starting email processing pipeline with query: $searchQuery for $count emails")
    
    // Check if fetchService is GmailFetchService
    val gmailFetchService = emailFetchService as? GmailFetchService
    if (gmailFetchService == null) {
        Log.e(TAG, "Email fetch service is not GmailFetchService")
        eventManager.notifyBatchComplete(0, 0)
        return
    }
    
    // Set the search query
    gmailFetchService.setSearchQuery(searchQuery)
    
    // Step 1: Fetch emails with query
    emailFetchService.fetchMessagesByQuery(
        searchQuery,
        count,
        onSuccess = { messages ->
            Log.d(TAG, "Fetched ${messages.size} messages matching query: $searchQuery")
            
            if (messages.isEmpty()) {
                Log.d(TAG, "No emails matching query to process")
                eventManager.notifyBatchComplete(0, 0)
                return@fetchMessagesByQuery
            }
            
            // Process messages sequentially
            processMessagesSequentially(messages)
        },
        onError = { error ->
            Log.e(TAG, "Error fetching messages: $error")
            eventManager.notifyBatchComplete(0, 0)
        }
    )
}
    /**
     * Process a list of messages sequentially
     */
    private fun processMessagesSequentially(messages: List<MessageSourceService.SourceMessage>) {
        // Create a copy of the list to work with
        val remainingMessages = messages.toMutableList()
        
        // Process the first message
        processNextMessage(remainingMessages)
    }
    
    /**
     * Process the next message in the queue
     */
    private fun processNextMessage(remainingMessages: MutableList<MessageSourceService.SourceMessage>) {
        if (remainingMessages.isEmpty()) {
            Log.d(TAG, "All messages processed, success: $successCount, failure: $failureCount")
            eventManager.notifyBatchComplete(successCount, failureCount)
            return
        }
        
        // Get the next message
        val message = remainingMessages.removeAt(0)
        Log.d(TAG, "Processing message: ${message.subject}, remaining: ${remainingMessages.size}")
        
        // Notify that we've fetched the email
        eventManager.notifyEmailFetched(message.id, message.subject)
        
        // Process the message through the pipeline
        processMessage(
            message,
            onComplete = {
                // Process the next message after a brief delay using coroutines
                CoroutineScope(Dispatchers.Default).launch {
                    delay(1000) // 1 second delay between processing
                    processNextMessage(remainingMessages)
                }
            }
        )
    }
    
    /**
     * Process a single message through the pipeline
     */
    private fun processMessage(
        message: MessageSourceService.SourceMessage,
        onComplete: () -> Unit
    ) {
        Log.d(TAG, "Processing message through pipeline: ${message.id}")
        
        // Step 2: Notify processing started
        eventManager.notifyProcessingStarted(message.id, message.subject)
        
        // Step 3: Process content with LLM
        contentProcessor.processContent(
            message = message,
            onComplete = { processedContent ->
                // Step 4: Notify content processing complete
                eventManager.notifyProcessingComplete(message.id, message.subject, processedContent)
                
                // Step 5: Prepare metadata for delivery
                val metadata = mutableMapOf<String, String>()
                message.metadata["from"]?.let { metadata["from"] = it }
                message.metadata["date"]?.let { metadata["date"] = it }
                
                // Step 6: Format message for delivery
                val telegramService = deliveryService as TelegramDeliveryService
                val formattedMessage = telegramService.buildFormattedMessage(
                    subject = message.subject,
                    content = processedContent,
                    from = metadata["from"] ?: "",
                    date = metadata["date"] ?: ""
                )
                
                // Step 7: Send message to delivery service
                deliveryService.sendMessage(
                    content = formattedMessage,
                    metadata = metadata,
                    onSuccess = {
                        // Success - mark email as processed
                        successCount++
                        
                        markMessageAsProcessed(message.id)
                        
                        // Notify email sent
                        eventManager.notifyEmailSent(
                            message.id, 
                            message.subject, 
                            true, 
                            "Message processed successfully"
                        )
                        
                        // Continue with the next message
                        onComplete()
                    },
                    onError = { error ->
                        // Failure
                        failureCount++
                        Log.e(TAG, "Error sending message: $error")
                        
                        // Notify email sent with failure
                        eventManager.notifyEmailSent(
                            message.id, 
                            message.subject, 
                            false, 
                            "Failed to send message: $error"
                        )
                        
                        // Continue with the next message
                        onComplete()
                    }
                )
            },
            onError = { error ->
                // Content processing failed
                failureCount++
                Log.e(TAG, "Error processing content: $error")
                
                // Notify email sent with failure
                eventManager.notifyEmailSent(
                    message.id, 
                    message.subject, 
                    false, 
                    "Failed to process content: $error"
                )
                
                // Continue with the next message
                onComplete()
            }
        )
    }
    
    /**
     * Mark a message as processed
     */
    private fun markMessageAsProcessed(messageId: String) {
        emailFetchService.markMessageAsProcessed(
            messageId,
            object : MessageSourceService.OperationCallback {
                override fun onSuccess() {
                    Log.d(TAG, "Marked message as processed: $messageId")
                }
                
                override fun onError(errorMessage: String) {
                    Log.e(TAG, "Failed to mark message as processed: $errorMessage")
                }
            }
        )
    }
    
    /**
     * Process a manual email (for testing or UI input)
     */
    fun processManualEmail(
        subject: String, 
        content: String,
        from: String = "",
        date: String = ""
    ) {
        if (!deliveryService.isConfigured()) {
            Log.e(TAG, "Delivery service not configured")
            completionCallback?.onComplete(false, "Delivery service not configured")
            return
        }
        
        // Create a synthetic message
        val message = MessageSourceService.SourceMessage(
            id = "manual-${System.currentTimeMillis()}",
            subject = subject,
            content = content,
            source = "Manual",
            metadata = mapOf(
                "from" to from,
                "date" to date
            )
        )
        
        // Process through the pipeline
        processMessage(
            message,
            onComplete = {
                Log.d(TAG, "Manual email processing complete")
            }
        )
    }
    
    /**
     * Process a sample email (for demonstration)
     */
    fun processSampleEmail() {
        if (!deliveryService.isConfigured()) {
            Log.e(TAG, "Delivery service not configured")
            completionCallback?.onComplete(false, "Delivery service not configured")
            return
        }
        
        // Create a sample message
        val sampleMessage = MessageSourceService.SourceMessage(
            id = "sample-${System.currentTimeMillis()}",
            subject = "Meeting tomorrow at 2 PM",
            content = """
                Hello team,
                
                Just a reminder that we have a project status meeting tomorrow at 2 PM in Conference Room A.
                Please bring your weekly progress reports and be prepared to discuss any blockers.
                
                Best regards,
                Project Manager
            """.trimIndent(),
            source = "Sample",
            metadata = mapOf(
                "from" to "project.manager@example.com",
                "date" to "Mon, 09 Jul 2025 10:30:45 +0000"
            )
        )
        
        // Process through the pipeline
        processMessage(
            sampleMessage,
            onComplete = {
                Log.d(TAG, "Sample email processing complete")
            }
        )
    }
    
    /**
     * Schedule periodic email processing
     */
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
            
        Log.d(TAG, "Scheduled periodic processing every $intervalMinutes minutes")
    }
    
    /**
     * Cancel periodic processing
     */
    fun cancelPeriodicProcessing() {
        WorkManager.getInstance(context).cancelUniqueWork("gmail_processing")
        Log.d(TAG, "Cancelled periodic processing")
    }
    
    /**
     * Helper method to safely invoke the cancellation callback
     */
    private fun notifyCancellation() {
        try {
            onCancellation.invoke()
        } catch (e: Exception) {
            Log.e(TAG, "Error invoking cancellation callback", e)
        }
    }
    
    /**
     * Handle pipeline cancellation
     */
    fun onCancellation() {
        Log.d(TAG, "Pipeline cancelled")
        
        // Clean up resources in order
        runCatching {
            // 1. Stop any new processing
            eventManager.notifyProcessingCancelled()
            
            // 2. Cancel ongoing email fetching
            emailFetchService.cancelPendingOperations()
            
            // 3. Clean up content processor
            contentProcessor.cleanup()
            
            // 4. Cancel any pending message deliveries
            deliveryService.cancelPendingOperations()
            
            // 5. Notify about cancellation
            notifyCancellation()
            
            // 6. Finally notify batch complete with current counts
            eventManager.notifyBatchComplete(successCount, failureCount)
        }.onFailure { e ->
            Log.e(TAG, "Error during pipeline cancellation", e)
            // Still try to notify batch complete even if cleanup failed
            notifyCancellation()
            eventManager.notifyBatchComplete(successCount, failureCount)
        }
        
        Log.d(TAG, "Pipeline cleanup complete")
    }
    
    /**
     * Worker class for background processing
     */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    class EmailProcessingWorker(
        private val appContext: Context, 
        params: WorkerParameters
    ) : CoroutineWorker(appContext, params) {
        private val TAG = "EmailProcessingWorker"
        
        override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Starting email processing job")
                
                // Create services
                val gmailService = GmailService(appContext)
                val telegramService = TelegramService(appContext)
                val model = MODEL_TEXT_CLASSIFICATION_MOBILEBERT.copy(llmSupportImage = false)
                
                // Check if services are configured properly
                if (!gmailService.isSignedIn() || !telegramService.isLoggedIn()) {
                    Log.w(TAG, "Services not configured properly")
                    return@withContext ListenableWorker.Result.retry() // Retry later
                }
                
                // Create pipeline components
                val fetchService = GmailFetchService(appContext, gmailService)
                val contentProcessor = LlmContentProcessor(appContext, model)
                val deliveryService = TelegramDeliveryService(appContext, telegramService)
                
                // Create a pipeline with cancellation support
                val pipeline = EmailProcessingPipeline(
                    context = appContext,
                    fetchService = fetchService,
                    contentProcessor = contentProcessor,
                    deliveryService = deliveryService,
                    onCancellation = {
                        Log.d(TAG, "Pipeline cancelled via worker cancellation")
                    }
                )
                
                // Use suspendCancellableCoroutine to convert callback-style to coroutine-style
                val result = suspendCancellableCoroutine<ListenableWorker.Result> { continuation ->
                    // Set completion callback
                    pipeline.setCompletionCallback(object : CompletionCallback {
                        override fun onComplete(success: Boolean, message: String) {
                            Log.d(TAG, "Pipeline completed: $message")
                            
                            if (continuation.isActive) {
                                val workerResult = if (success) {
                                    ListenableWorker.Result.success()
                                } else {
                                    ListenableWorker.Result.retry()
                                }
                                continuation.resume(workerResult) {
                                    // Handle cancellation during resume
                                    Log.d(TAG, "Worker resumption was cancelled")
                                    pipeline.onCancellation()
                                }
                            }
                        }
                    })
                    
                    // Process emails
                    pipeline.processEmails(3)
                    
                    // Set a timeout
                    val timeoutJob = GlobalScope.launch {
                        delay(60000) // 60 seconds timeout
                        if (continuation.isActive) {
                            Log.w(TAG, "Operation timed out")
                            continuation.resume(ListenableWorker.Result.retry()) {
                                // Handle cancellation during timeout resume
                                Log.d(TAG, "Timeout resumption was cancelled")
                                pipeline.onCancellation()
                            }
                        }
                    }

                    // Handle cancellation
                    continuation.invokeOnCancellation { cause ->
                        runCatching {
                            timeoutJob.cancel()
                            pipeline.onCancellation() // This will handle all cleanup including contentProcessor
                            Log.d(TAG, "Pipeline worker cancelled: ${cause?.message ?: "No cause specified"}")
                        }.onFailure { e ->
                            Log.e(TAG, "Error during worker cancellation", e)
                        }
                    }
                }
                
                return@withContext result
            } catch (e: Exception) {
                Log.e(TAG, "Worker execution failed", e)
                return@withContext ListenableWorker.Result.failure()
            }
        }
    }
}

//newly added
/**
 * Process emails with search query
 */
