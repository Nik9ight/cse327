package com.example.llmapp.pipeline

import android.content.Context
import android.util.Log
import androidx.work.*
import com.example.llmapp.interfaces.MessageService
import com.example.llmapp.interfaces.MessageSourceService
import com.example.llmapp.interfaces.ProcessingService
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * A generic pipeline that processes messages from a source and sends them to a destination
 * Implements Strategy pattern by accepting different services as strategies
 */
class MessageProcessingPipeline(
    private val context: Context,
    private val messageSourceService: MessageSourceService? = null,
    private val processingService: ProcessingService? = null,
    private val messageDestinationService: MessageService? = null
) {
    private val TAG = "MessagePipeline"
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
    
    /**
     * Process a single message with given content
     */
    fun processMessage(subject: String, content: String, metadata: Map<String, String> = mapOf()) {
        if (processingService == null || !processingService.isReady()) {
            completionListener?.onComplete(false, "Processing service is not configured")
            return
        }
        
        if (messageDestinationService == null || !messageDestinationService.isConfigured()) {
            completionListener?.onComplete(false, "Message destination service is not configured")
            return
        }
        
        val prompt = buildPrompt(subject, content)
        
        processingService.processText(prompt, object : ProcessingService.ProcessingCallback {
            override fun onProcessingComplete(output: String) {
                val formattedMessage = buildOutputMessage(subject, output, metadata)
                
                messageDestinationService.sendMessage(formattedMessage, object : MessageService.MessageCallback {
                    override fun onSuccess() {
                        completionListener?.onComplete(true, "Message processed and sent successfully")
                    }
                    
                    override fun onError(errorMessage: String) {
                        completionListener?.onComplete(false, "Failed to send processed message: $errorMessage")
                    }
                })
            }
            
            override fun onError(errorMessage: String) {
                completionListener?.onComplete(false, "Processing failed: $errorMessage")
            }
        })
    }
    
    /**
     * Fetch messages from source and process them
     */
    fun fetchAndProcessMessages(maxMessages: Int = 10) {
        if (messageSourceService == null || !messageSourceService.isConfigured()) {
            completionListener?.onComplete(false, "Message source service is not configured")
            return
        }
        
        messageSourceService.retrieveMessages(maxMessages, object : MessageSourceService.MessageRetrievalCallback {
            override fun onMessagesRetrieved(messages: List<MessageSourceService.SourceMessage>) {
                if (messages.isEmpty()) {
                    completionListener?.onComplete(true, "No new messages to process")
                    return
                }
                
                // Process first message for now
                val message = messages.first()
                processMessage(
                    message.subject,
                    message.content,
                    message.metadata
                )
                
                // Mark as processed
                messageSourceService.markMessageAsProcessed(message.id, object : MessageSourceService.OperationCallback {
                    override fun onSuccess() {
                        Log.d(TAG, "Message marked as processed: ${message.id}")
                    }
                    
                    override fun onError(errorMessage: String) {
                        Log.e(TAG, "Failed to mark message as processed: $errorMessage")
                    }
                })
            }
            
            override fun onError(errorMessage: String) {
                completionListener?.onComplete(false, "Failed to retrieve messages: $errorMessage")
            }
        })
    }
    
    /**
     * Schedule periodic processing using WorkManager
     */
    fun schedulePeriodicProcessing(intervalMinutes: Long = 15) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
            
        val workRequest = PeriodicWorkRequestBuilder<MessageProcessingWorker>(
            intervalMinutes, TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .build()
            
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(
                "message_processing_pipeline",
                ExistingPeriodicWorkPolicy.REPLACE,
                workRequest
            )
    }
    
    /**
     * Cancel scheduled periodic processing
     */
    fun cancelPeriodicProcessing() {
        WorkManager.getInstance(context)
            .cancelUniqueWork("message_processing_pipeline")
    }
    
    /**
     * Build a prompt for the processing service
     */
    private fun buildPrompt(subject: String, content: String): String {
        return """
            $content
        """.trimIndent()
    }
    
    /**
     * Format the output message with metadata
     */
    private fun buildOutputMessage(subject: String, processedContent: String, metadata: Map<String, String>): String {
        val from = metadata["from"] ?: ""
        val date = metadata["date"] ?: ""
        
        return buildString {
            append("<b>")
            append(escapeHtml(subject))
            append("</b>\n\n")
            
            if (from.isNotEmpty()) {
                append("<i>From: ")
                append(escapeHtml(from))
                append("</i>\n")
            }
            
            if (date.isNotEmpty()) {
                append("<i>Date: ")
                append(escapeHtml(date))
                append("</i>\n")
            }
            
            append("\n")
            append(processedContent)
        }
    }
    
    /**
     * Escape HTML special characters
     */
    private fun escapeHtml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#039;")
    }
    
    /**
     * Worker class for background processing
     */
    class MessageProcessingWorker(
        private val context: Context,
        params: WorkerParameters
    ) : Worker(context, params) {
        override fun doWork(): Result {
            // This is a placeholder. In a real implementation, we would need to
            // create a pipeline with dependencies injected by a factory/DI container
            return Result.success()
        }
    }
}
