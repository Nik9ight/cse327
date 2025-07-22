package com.example.llmapp.pipeline

import android.content.Context
import com.example.llmapp.MODEL_TEXT_CLASSIFICATION_MOBILEBERT
import com.example.llmapp.Model
import com.example.llmapp.adapters.GmailServiceAdapter
import com.example.llmapp.adapters.LlmProcessingAdapter
import com.example.llmapp.adapters.TelegramServiceAdapter
import com.example.llmapp.interfaces.MessageService
import com.example.llmapp.interfaces.MessageSourceService
import com.example.llmapp.interfaces.ProcessingService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Factory for creating pipeline components
 * Implements Factory pattern to create different types of services
 */
object PipelineFactory {
    // Service registry - could be expanded to support custom implementations
    private val processingServices = mutableMapOf<String, (Context) -> ProcessingService>()
    private val messageSourceServices = mutableMapOf<String, (Context) -> MessageSourceService>()
    private val messageDestinationServices = mutableMapOf<String, (Context) -> MessageService>()
    
    init {
        // Register default implementations
        registerProcessingService("llm") { context ->
            val model = MODEL_TEXT_CLASSIFICATION_MOBILEBERT.copy(llmSupportImage = false)
            LlmProcessingAdapter(model)
        }
        
        registerMessageSourceService("gmail") { context ->
            GmailServiceAdapter(context)
        }
        
        registerMessageDestinationService("telegram") { context ->
            TelegramServiceAdapter(context)
        }
    }
    
    /**
     * Register a new processing service
     */
    fun registerProcessingService(name: String, factory: (Context) -> ProcessingService) {
        processingServices[name] = factory
    }
    
    /**
     * Register a new message source service
     */
    fun registerMessageSourceService(name: String, factory: (Context) -> MessageSourceService) {
        messageSourceServices[name] = factory
    }
    
    /**
     * Register a new message destination service
     */
    fun registerMessageDestinationService(name: String, factory: (Context) -> MessageService) {
        messageDestinationServices[name] = factory
    }
    
    /**
     * Create a processing service by name
     */
    fun createProcessingService(name: String, context: Context): ProcessingService? {
        return processingServices[name]?.invoke(context)
    }
    
    /**
     * Create a message source service by name
     */
    fun createMessageSourceService(name: String, context: Context): MessageSourceService? {
        return messageSourceServices[name]?.invoke(context)
    }
    
    /**
     * Create a message destination service by name
     */
    fun createMessageDestinationService(name: String, context: Context): MessageService? {
        return messageDestinationServices[name]?.invoke(context)
    }
    
    /**
     * Create a pipeline with specified services
     */
    fun createPipeline(
        context: Context,
        sourceServiceName: String? = null,
        processingServiceName: String? = null,
        destinationServiceName: String? = null
    ): MessageProcessingPipeline {
        val sourceService = sourceServiceName?.let { createMessageSourceService(it, context) }
        val processingService = processingServiceName?.let { createProcessingService(it, context) }
        val destinationService = destinationServiceName?.let { createMessageDestinationService(it, context) }
        
        return MessageProcessingPipeline(
            context = context,
            messageSourceService = sourceService,
            processingService = processingService,
            messageDestinationService = destinationService
        )
    }
    
    /**
     * Create a Gmail to Telegram pipeline (for backward compatibility)
     */
    fun createGmailToTelegramPipeline(context: Context): MessageProcessingPipeline {
        // Initialize LLM model asynchronously
        val llmProcessor = LlmProcessingAdapter(MODEL_TEXT_CLASSIFICATION_MOBILEBERT.copy(llmSupportImage = false))
        CoroutineScope(Dispatchers.Main).launch {
            llmProcessor.initialize(context)
        }
        
        return MessageProcessingPipeline(
            context = context,
            messageSourceService = GmailServiceAdapter(context),
            processingService = llmProcessor,
            messageDestinationService = TelegramServiceAdapter(context)
        )
    }
}
