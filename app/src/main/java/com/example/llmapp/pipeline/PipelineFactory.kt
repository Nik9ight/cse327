package com.example.llmapp.pipeline

import android.content.Context
import com.example.llmapp.GmailService
import com.example.llmapp.MODEL_TEXT_CLASSIFICATION_MOBILEBERT
import com.example.llmapp.Model
import com.example.llmapp.TelegramService
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
 * Configuration data classes for enhanced factory pattern
 */
data class ServiceConfig(
    val type: String,
    val parameters: Map<String, Any> = emptyMap()
)

data class PipelineConfiguration(
    val fetchService: ServiceConfig,
    val contentProcessor: ServiceConfig,
    val deliveryService: ServiceConfig,
    val processingChain: List<String> = listOf("content", "delivery")
)

/**
 * Enhanced factory interface for creating pipeline components
 * Follows the Abstract Factory pattern for extensibility
 */
interface PipelineComponentFactory {
    fun createEmailFetchService(config: ServiceConfig, context: Context): EmailFetchService
    fun createContentProcessor(config: ServiceConfig, context: Context): ContentProcessor
    fun createDeliveryService(config: ServiceConfig, context: Context): MessageDeliveryService
}

/**
 * Enhanced factory for creating pipeline components
 * Implements Factory pattern to create different types of services with better extensibility
 */
object PipelineFactory : PipelineComponentFactory {
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
    
    // Enhanced factory methods for new pipeline components
    override fun createEmailFetchService(config: ServiceConfig, context: Context): EmailFetchService {
        return when (config.type.lowercase()) {
            "gmail" -> {
                val gmailService = config.parameters["service"] as? GmailService
                GmailFetchService(context, gmailService)
            }
            "mock" -> {
                MockEmailFetchService(context)
            }
            else -> throw IllegalArgumentException("Unknown email fetch service type: ${config.type}")
        }
    }
    
    override fun createContentProcessor(config: ServiceConfig, context: Context): ContentProcessor {
        return when (config.type.lowercase()) {
            "llm" -> {
                val model = config.parameters["model"] as? Model
                val strategy = config.parameters["strategy"] as? PromptStrategy ?: DefaultPromptStrategy()
                LlmContentProcessor(context, model ?: getDefaultModel(), strategy)
            }
            "mock" -> {
                MockContentProcessor()
            }
            "passthrough" -> {
                PassthroughContentProcessor()
            }
            else -> throw IllegalArgumentException("Unknown content processor type: ${config.type}")
        }
    }
    
    override fun createDeliveryService(config: ServiceConfig, context: Context): MessageDeliveryService {
        return when (config.type.lowercase()) {
            "telegram" -> {
                val telegramService = config.parameters["service"] as? TelegramService
                TelegramDeliveryService(context, telegramService)
            }
            "mock" -> {
                MockDeliveryService(context)
            }
            else -> throw IllegalArgumentException("Unknown delivery service type: ${config.type}")
        }
    }
    
    private fun getDefaultModel(): Model {
        return MODEL_TEXT_CLASSIFICATION_MOBILEBERT.copy(llmSupportImage = false)
    }
    
    /**
     * Enhanced pipeline builder method
     */
    fun buildPipeline(config: PipelineConfiguration, context: Context): EmailProcessingPipeline {
        val fetchService = createEmailFetchService(config.fetchService, context)
        val contentProcessor = createContentProcessor(config.contentProcessor, context)
        val deliveryService = createDeliveryService(config.deliveryService, context)
        
        return EmailProcessingPipeline(
            context = context,
            fetchService = fetchService,
            contentProcessor = contentProcessor,
            deliveryService = deliveryService
        )
    }
    
    /**
     * Create a pipeline with default Gmail to Telegram configuration
     */
    fun buildGmailToTelegramPipeline(
        context: Context,
        gmailService: GmailService? = null,
        telegramService: TelegramService? = null,
        model: Model? = null,
        promptStrategy: PromptStrategy = DefaultPromptStrategy()
    ): EmailProcessingPipeline {
        
        val config = PipelineConfiguration(
            fetchService = ServiceConfig(
                type = "gmail",
                parameters = buildMap {
                    gmailService?.let { put("service", it) }
                }
            ),
            contentProcessor = ServiceConfig(
                type = "llm",
                parameters = buildMap {
                    model?.let { put("model", it) }
                    put("strategy", promptStrategy)
                }
            ),
            deliveryService = ServiceConfig(
                type = "telegram",
                parameters = buildMap {
                    telegramService?.let { put("service", it) }
                }
            )
        )
        
        return buildPipeline(config, context)
    }
    
    // Legacy methods for backward compatibility
    
    /**
     * Create a processing service by name
     */
    private fun createProcessingService(name: String, context: Context): ProcessingService? {
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

/**
 * Mock implementations for testing and development
 */
class MockEmailFetchService(private val context: Context) : EmailFetchService {
    
    override fun fetchUnreadMessages(
        count: Int,
        onSuccess: (List<com.example.llmapp.interfaces.MessageSourceService.SourceMessage>) -> Unit,
        onError: (String) -> Unit
    ) {
        // Mock implementation for testing
        onSuccess(emptyList())
    }
    
    override fun fetchMessagesByQuery(
        query: String,
        count: Int,
        onSuccess: (List<com.example.llmapp.interfaces.MessageSourceService.SourceMessage>) -> Unit,
        onError: (String) -> Unit
    ) {
        // Mock implementation for testing
        onSuccess(emptyList())
    }
    
    override fun setSearchQuery(query: String?) {
        // Mock implementation
    }
    
    override fun getSearchQuery(): String? = null
    
    override fun markMessageAsProcessed(
        messageId: String,
        callback: com.example.llmapp.interfaces.MessageSourceService.OperationCallback
    ) {
        callback.onSuccess()
    }
    
    override fun isConfigured(): Boolean = true
    
    override fun cancelPendingOperations() {
        // Mock implementation
    }
}

class MockContentProcessor : ContentProcessor {
    override fun processContent(
        message: com.example.llmapp.interfaces.MessageSourceService.SourceMessage,
        onComplete: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        onComplete("Mock processed: ${message.subject}")
    }
    
    override fun isReady(): Boolean = true
    override fun cleanup() {}
}

class PassthroughContentProcessor : ContentProcessor {
    override fun processContent(
        message: com.example.llmapp.interfaces.MessageSourceService.SourceMessage,
        onComplete: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        onComplete(message.content)
    }
    
    override fun isReady(): Boolean = true
    override fun cleanup() {}
}

class MockDeliveryService(private val context: Context) : MessageDeliveryService {
    override fun sendMessage(
        content: String,
        metadata: Map<String, String>,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        onSuccess()
    }
    
    override fun isConfigured(): Boolean = true
    
    override fun cancelPendingOperations() {
        // Mock implementation
    }
}
