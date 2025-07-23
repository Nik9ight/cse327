package com.example.llmapp

import android.content.Context
import android.util.Log
import com.example.llmapp.pipeline.*
import com.example.llmapp.patterns.BroadcastPipelineObserver
import com.example.llmapp.patterns.PipelineObserver

/**
 * GmailToTelegramPipeline - Refactored to use design patterns with modular architecture
 * 
 * This is a facade class that provides backward compatibility with the existing
 * codebase while delegating actual work to the new modular pipeline components.
 * 
 * Uses:
 * - Strategy Pattern: For different processing strategies
 * - Command Pattern: For email processing commands 
 * - Observer Pattern: For notifications
 * - Chain of Responsibility: For email processing pipeline
 * - Factory Pattern: For creating service components
 */
class GmailToTelegramPipeline(
    private val context: Context,
    private val telegramService: TelegramService? = null,
    private val model: Model? = MODEL_TEXT_CLASSIFICATION_MOBILEBERT.copy(llmSupportImage = false),
    private val gmailService: GmailService? = null
) {
    private val TAG = "GmailToTelegramPipeline"
    private var completionListener: PipelineCompletionListener? = null
    
    // The new pipeline implementation
    private val pipeline: EmailProcessingPipeline
    
    // Observer pattern support for backward compatibility
    private val observers = mutableListOf<PipelineObserver>()
    
    /**
     * Interface for pipeline completion callbacks
     * Will be bridged to the new pipeline
     */
    interface PipelineCompletionListener {
        fun onComplete(success: Boolean, message: String)
    }
    
    init {
        // Create the services
        val emailFetchService = GmailFetchService(context, gmailService)
        val contentProcessor = LlmContentProcessor(context, model ?: MODEL_TEXT_CLASSIFICATION_MOBILEBERT.copy(llmSupportImage = false))
        val deliveryService = TelegramDeliveryService(context, telegramService)
        
        // Create the pipeline with the services
        pipeline = EmailProcessingPipeline(
            context = context,
            fetchService = emailFetchService,
            contentProcessor = contentProcessor,
            deliveryService = deliveryService
        )
        
        // Add legacy observer support
        addObserver(BroadcastPipelineObserver(context))
        
        Log.d(TAG, "GmailToTelegramPipeline facade initialized")
    }
    
    constructor(context: Context) : this(context, null, MODEL_TEXT_CLASSIFICATION_MOBILEBERT.copy(llmSupportImage = false), null)
    
    /**
     * Set a completion listener to be notified when processing is complete
     */
    fun setCompletionListener(listener: PipelineCompletionListener) {
        completionListener = listener
        
        // Bridge to the new pipeline
        pipeline.setCompletionCallback(object : EmailProcessingPipeline.CompletionCallback {
            override fun onComplete(success: Boolean, message: String) {
                completionListener?.onComplete(success, message)
            }
        })
    }
    
    /**
     * Add an observer to the pipeline
     */
    fun addObserver(observer: PipelineObserver) {
        if (!observers.contains(observer)) {
            observers.add(observer)
            pipeline.addEventListenerAdapter(observer)
        }
    }
    
    /**
     * Remove an observer from the pipeline
     */
    fun removeObserver(observer: PipelineObserver) {
        observers.remove(observer)
    }
    
    /**
     * Process unread messages from Gmail and send them to Telegram
     * Legacy method that delegates to the new pipeline
     */
    // fun process() {
    //     pipeline.processEmailsWithSearch(query: String, 3)
    // }
    /**
 * Process unread messages from Gmail and send them to Telegram
 * Legacy method that delegates to the new pipeline
 */
fun process() {
    // Get the search query if set, otherwise use default processing
    val fetchService = getEmailFetchService() as? GmailFetchService
    val query = fetchService?.getSearchQuery()
    
    if (query != null && query.isNotBlank()) {
        Log.d(TAG, "Processing with search query: $query")
        pipeline.processEmailsWithSearch(query, 3)
    } else {
        Log.d(TAG, "Processing with default settings (unread emails)")
        pipeline.processEmails(3)
    }
}
                    
    // No old code needed here - pipeline handles all processing
    
    /**
     * Process Gmail message and send to Telegram
     */
    fun processEmail(subject: String, content: String, telegramService: TelegramService? = null, from: String = "", date: String = "") {
        pipeline.processManualEmail(subject, content, from, date)
    }
    
    /**
     * Check if the message source service is configured
     */
    fun isMessageSourceConfigured(): Boolean {
        return pipeline.isConfigured()
    }
    
    /**
     * Method to fetch and process emails from Gmail
     */
    fun fetchAndProcessEmails(count: Int = 3, onComplete: ((Boolean, String) -> Unit)? = null) {
        // Set a temporary completion callback if provided
        if (onComplete != null) {
            pipeline.setCompletionCallback(object : EmailProcessingPipeline.CompletionCallback {
                override fun onComplete(success: Boolean, message: String) {
                    onComplete(success, message)
                }
            })
        }
        
        // Process emails
        pipeline.processEmails(count)
    }
    
    /**
     * Schedule periodic processing
     */
    fun schedulePeriodicProcessing(intervalMinutes: Long = 15) {
        pipeline.schedulePeriodicProcessing(intervalMinutes)
    }
    
    /**
     * For demonstration purposes - process a sample email
     */
    fun processSampleEmail(onComplete: ((Boolean, String) -> Unit)? = null) {
        // Set a temporary completion callback if provided
        if (onComplete != null) {
            pipeline.setCompletionCallback(object : EmailProcessingPipeline.CompletionCallback {
                override fun onComplete(success: Boolean, message: String) {
                    onComplete(success, message)
                }
            })
        }
        
        // Process the sample email
        pipeline.processSampleEmail()
    }
    
    /**
     * Get the email fetch service from the underlying pipeline
     * This is needed for configuring search parameters
     */
    fun getEmailFetchService(): EmailFetchService {
        return pipeline.getEmailFetchService()
    }
    
    /**
     * Get the content processor from the underlying pipeline
     * This is needed for configuring LLM prompts
     */
    fun getContentProcessor(): ContentProcessor {
        return pipeline.getContentProcessor()
    }
        
}
