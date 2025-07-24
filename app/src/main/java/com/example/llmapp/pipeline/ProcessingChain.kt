package com.example.llmapp.pipeline

import android.util.Log
import com.example.llmapp.interfaces.MessageSourceService

/**
 * Chain of Responsibility Pattern for flexible message processing
 * Allows adding/removing processing steps without modifying existing code
 */

interface ProcessingCallback {
    fun onComplete(message: MessageSourceService.SourceMessage)
    fun onError(error: String)
}

/**
 * Base interface for processing handlers in the chain
 */
interface ProcessingHandler {
    fun setNext(handler: ProcessingHandler): ProcessingHandler
    fun handle(message: MessageSourceService.SourceMessage, callback: ProcessingCallback)
}

/**
 * Abstract base class implementing common chain functionality
 */
abstract class BaseProcessingHandler : ProcessingHandler {
    private var nextHandler: ProcessingHandler? = null
    
    override fun setNext(handler: ProcessingHandler): ProcessingHandler {
        nextHandler = handler
        return handler
    }
    
    protected fun passToNext(message: MessageSourceService.SourceMessage, callback: ProcessingCallback) {
        nextHandler?.handle(message, callback) ?: callback.onComplete(message)
    }
    
    protected fun handleError(error: String, callback: ProcessingCallback) {
        Log.e(this::class.simpleName, error)
        callback.onError(error)
    }
}

/**
 * Content processing handler - processes message content using LLM or other processors
 */
class ContentProcessingHandler(
    private val processor: ContentProcessor,
    private val tag: String = "ContentProcessingHandler"
) : BaseProcessingHandler() {
    
    override fun handle(message: MessageSourceService.SourceMessage, callback: ProcessingCallback) {
        Log.d(tag, "Processing content for message: ${message.subject}")
        
        if (!processor.isReady()) {
            handleError("Content processor is not ready", callback)
            return
        }
        
        processor.processContent(
            message = message,
            onComplete = { processedContent ->
                Log.d(tag, "Content processing completed for: ${message.subject}")
                val processedMessage = message.copy(content = processedContent)
                passToNext(processedMessage, callback)
            },
            onError = { error ->
                handleError("Content processing failed: $error", callback)
            }
        )
    }
}

/**
 * Validation handler - validates message before processing
 */
class ValidationHandler(
    private val tag: String = "ValidationHandler"
) : BaseProcessingHandler() {
    
    override fun handle(message: MessageSourceService.SourceMessage, callback: ProcessingCallback) {
        Log.d(tag, "Validating message: ${message.subject}")
        
        when {
            message.subject.isBlank() -> {
                handleError("Message has empty subject", callback)
            }
            message.content.isBlank() -> {
                handleError("Message has empty content", callback)
            }
            message.content.length > 10000 -> {
                // Truncate long content
                Log.w(tag, "Message content too long, truncating")
                val truncatedMessage = message.copy(
                    content = message.content.take(10000) + "... [Content truncated]"
                )
                passToNext(truncatedMessage, callback)
            }
            else -> {
                Log.d(tag, "Message validation passed")
                passToNext(message, callback)
            }
        }
    }
}

/**
 * Delivery handler - delivers processed message to destination
 */
class DeliveryHandler(
    private val deliveryService: MessageDeliveryService,
    private val tag: String = "DeliveryHandler"
) : BaseProcessingHandler() {
    
    override fun handle(message: MessageSourceService.SourceMessage, callback: ProcessingCallback) {
        Log.d(tag, "Delivering message: ${message.subject}")
        
        if (!deliveryService.isConfigured()) {
            handleError("Delivery service is not configured", callback)
            return
        }
        
        deliveryService.sendMessage(
            content = message.content,
            metadata = message.metadata,
            onSuccess = {
                Log.d(tag, "Message delivered successfully")
                passToNext(message, callback)
            },
            onError = { error ->
                handleError("Delivery failed: $error", callback)
            }
        )
    }
}

/**
 * Logging handler - logs message processing steps
 */
class LoggingHandler(
    private val logLevel: LogLevel = LogLevel.INFO,
    private val tag: String = "LoggingHandler"
) : BaseProcessingHandler() {
    
    enum class LogLevel { DEBUG, INFO, WARN, ERROR }
    
    override fun handle(message: MessageSourceService.SourceMessage, callback: ProcessingCallback) {
        when (logLevel) {
            LogLevel.DEBUG -> Log.d(tag, "Processing: ${message.subject} (${message.content.length} chars)")
            LogLevel.INFO -> Log.i(tag, "Processing: ${message.subject}")
            LogLevel.WARN -> Log.w(tag, "Processing: ${message.subject}")
            LogLevel.ERROR -> Log.e(tag, "Processing: ${message.subject}")
        }
        
        passToNext(message, callback)
    }
}

/**
 * Filter handler - filters messages based on criteria
 */
class FilterHandler(
    private val filter: (MessageSourceService.SourceMessage) -> Boolean,
    private val tag: String = "FilterHandler"
) : BaseProcessingHandler() {
    
    override fun handle(message: MessageSourceService.SourceMessage, callback: ProcessingCallback) {
        if (filter(message)) {
            Log.d(tag, "Message passed filter: ${message.subject}")
            passToNext(message, callback)
        } else {
            Log.d(tag, "Message filtered out: ${message.subject}")
            callback.onComplete(message) // Complete without processing
        }
    }
}

/**
 * Processing chain builder for easy configuration
 */
class ProcessingChainBuilder {
    private var firstHandler: ProcessingHandler? = null
    private var lastHandler: ProcessingHandler? = null
    
    fun addValidation(): ProcessingChainBuilder {
        return addHandler(ValidationHandler())
    }
    
    fun addLogging(level: LoggingHandler.LogLevel = LoggingHandler.LogLevel.INFO): ProcessingChainBuilder {
        return addHandler(LoggingHandler(level))
    }
    
    fun addFilter(filter: (MessageSourceService.SourceMessage) -> Boolean): ProcessingChainBuilder {
        return addHandler(FilterHandler(filter))
    }
    
    fun addContentProcessing(processor: ContentProcessor): ProcessingChainBuilder {
        return addHandler(ContentProcessingHandler(processor))
    }
    
    fun addDelivery(deliveryService: MessageDeliveryService): ProcessingChainBuilder {
        return addHandler(DeliveryHandler(deliveryService))
    }
    
    fun addHandler(handler: ProcessingHandler): ProcessingChainBuilder {
        if (firstHandler == null) {
            firstHandler = handler
            lastHandler = handler
        } else {
            lastHandler?.setNext(handler)
            lastHandler = handler
        }
        return this
    }
    
    fun build(): ProcessingHandler {
        return firstHandler ?: throw IllegalStateException("No handlers added to chain")
    }
    
    /**
     * Build a default email processing chain
     */
    fun buildDefaultEmailChain(
        contentProcessor: ContentProcessor,
        deliveryService: MessageDeliveryService
    ): ProcessingHandler {
        return addLogging()
            .addValidation()
            .addContentProcessing(contentProcessor)
            .addDelivery(deliveryService)
            .build()
    }
}
