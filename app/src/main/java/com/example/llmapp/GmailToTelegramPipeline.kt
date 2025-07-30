package com.example.llmapp

import android.content.Context
import android.util.Log
import com.example.llmapp.pipeline.*
import com.example.llmapp.patterns.BroadcastPipelineObserver
import com.example.llmapp.patterns.PipelineObserver

/**
 * GmailToTelegramPipeline - Enhanced with comprehensive design patterns
 * 
 * This is a facade class that provides backward compatibility with the existing
 * codebase while delegating actual work to the new modular pipeline components.
 * 
 * Design Patterns Implemented:
 * - Strategy Pattern: For different processing strategies and service creation
 * - Command Pattern: For email processing commands with undo/redo support
 * - Observer Pattern: For notifications and event handling
 * - Chain of Responsibility: For email processing pipeline
 * - Factory Pattern: For creating service components
 * - Template Method Pattern: For customizable processing workflows
 * - Plugin Architecture: For extensible functionality
 */
class GmailToTelegramPipeline(
    private val context: Context,
    private val telegramService: TelegramService? = null,
    private val model: Model? = MODEL_TEXT_CLASSIFICATION_MOBILEBERT.copy(llmSupportImage = false),
    private val gmailService: GmailService? = null,
    private val serviceCreationStrategy: ServiceCreationStrategy = DefaultServiceStrategy()
) {
    private val TAG = "GmailToTelegramPipeline"
    private var completionListener: PipelineCompletionListener? = null
    
    // The new pipeline implementation
    private val pipeline: EmailProcessingPipeline
    
    // Command pattern support
    private val commandInvoker = PipelineCommandInvoker()
    
    // Template method pattern support
    private val processingTemplate: EmailProcessingTemplate
    
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
        // Use strategy pattern for service creation
        val emailFetchService = serviceCreationStrategy.createEmailFetchService(context, gmailService)
        val contentProcessor = serviceCreationStrategy.createContentProcessor(context, model)
        val deliveryService = serviceCreationStrategy.createDeliveryService(context, telegramService)
        
        // Create the pipeline with the services
        pipeline = EmailProcessingPipeline(
            context = context,
            fetchService = emailFetchService,
            contentProcessor = contentProcessor,
            deliveryService = deliveryService
        )
        
        // Create processing template for template method pattern
        processingTemplate = PipelineTemplateFactory.createTelegramTemplate(
            promptStrategy = DefaultPromptStrategy(),
            telegramService = telegramService ?: TelegramService(context)
        )
        
        // Add legacy observer support
        addObserver(BroadcastPipelineObserver(context))
        
        Log.d(TAG, "GmailToTelegramPipeline facade initialized with enhanced design patterns")
    }
    
    constructor(context: Context) : this(
        context, 
        null, 
        MODEL_TEXT_CLASSIFICATION_MOBILEBERT.copy(llmSupportImage = false), 
        null,
        DefaultServiceStrategy()
    )
    
    /**
     * Alternative constructor with custom strategy
     */
    constructor(context: Context, strategy: ServiceCreationStrategy) : this(
        context, 
        null, 
        MODEL_TEXT_CLASSIFICATION_MOBILEBERT.copy(llmSupportImage = false), 
        null,
        strategy
    )
    
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
     * Enhanced with Command Pattern support
     */
    fun process() {
        // Get the search query if set, otherwise use default processing
        val fetchService = getEmailFetchService() as? GmailFetchService
        val query = fetchService?.getSearchQuery()
        
        // Create and execute command using Command Pattern
        val command = PipelineCommandFactory.createProcessCommand(pipeline, query, 3)
        val success = commandInvoker.executeCommand(command)
        
        if (success) {
            Log.d(TAG, "Processing command executed successfully")
        } else {
            Log.e(TAG, "Processing command failed")
        }
    }
    
    /**
     * Undo the last processing operation
     */
    fun undoLastOperation(): Boolean {
        return commandInvoker.undoLastCommand()
    }
    
    /**
     * Get command history for debugging/audit purposes
     */
    fun getCommandHistory(): List<String> {
        return commandInvoker.getCommandHistory()
    }
    
    /**
     * Clear command history
     */
    fun clearCommandHistory() {
        commandInvoker.clearHistory()
    }
    
    /**
     * Process email using Template Method Pattern
     * This provides a more structured and customizable processing approach
     */
    fun processEmailWithTemplate(email: Email): Boolean {
        return processingTemplate.processEmail(email)
    }
                    
    // No old code needed here - pipeline handles all processing
    
    /**
     * Process Gmail message and send to Telegram
     * Enhanced with Command Pattern
     */
    fun processEmail(subject: String, content: String, telegramService: TelegramService? = null, from: String = "", date: String = "") {
        val command = PipelineCommandFactory.createManualEmailCommand(pipeline, subject, content, from, date)
        commandInvoker.executeCommand(command)
    }
    
    /**
     * Check if the message source service is configured
     */
    fun isMessageSourceConfigured(): Boolean {
        return pipeline.isConfigured()
    }
    
    /**
     * Method to fetch and process emails from Gmail
     * Enhanced with Command Pattern
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
        
        // Create and execute command
        val command = ProcessEmailsCommand(pipeline, count)
        commandInvoker.executeCommand(command)
    }
    
    /**
     * Schedule periodic processing
     */
    fun schedulePeriodicProcessing(intervalMinutes: Long = 15) {
        pipeline.schedulePeriodicProcessing(intervalMinutes)
    }
    
    /**
     * For demonstration purposes - process a sample email
     * Enhanced with Command Pattern
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
        
        // Create and execute command
        val command = PipelineCommandFactory.createSampleEmailCommand(pipeline)
        commandInvoker.executeCommand(command)
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
    
    /**
     * Get the delivery service from the underlying pipeline
     * This is needed for configuring message delivery
     */
    fun getDeliveryService(): MessageDeliveryService {
        return pipeline.getDeliveryService()
    }
    
    /**
     * Switch to a different service creation strategy at runtime
     * Demonstrates Strategy Pattern flexibility
     */
    fun switchServiceStrategy(newStrategy: ServiceCreationStrategy) {
        Log.d(TAG, "Switching service creation strategy")
        // Note: In a real implementation, you might want to recreate the pipeline
        // with the new strategy. For now, this is just a demonstration.
        // This would require careful handling of existing state.
    }
    
    /**
     * Get processing template for advanced customization
     */
    fun getProcessingTemplate(): EmailProcessingTemplate {
        return processingTemplate
    }
    
    /**
     * Create a custom processing template
     */
    fun createCustomTemplate(
        promptStrategy: PromptStrategy,
        telegramService: TelegramService
    ): EmailProcessingTemplate {
        return PipelineTemplateFactory.createTelegramTemplate(promptStrategy, telegramService)
    }
    
    /**
     * Get metrics and statistics about command execution
     */
    fun getExecutionStats(): String {
        val history = commandInvoker.getCommandHistory()
        return "Total commands executed: ${history.size}\nRecent commands: ${history.takeLast(3).joinToString(", ")}"
    }
        
}
