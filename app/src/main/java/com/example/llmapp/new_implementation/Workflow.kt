package com.example.llmapp.new_implementation

import com.example.llmapp.new_implementation.interfaces.MessageSource
import com.example.llmapp.new_implementation.interfaces.MessageDestination
import com.example.llmapp.new_implementation.interfaces.Processor
import com.example.llmapp.new_implementation.interfaces.OutputFormatter
import android.content.Context

/**
 * Enhanced Workflow class with Strategy Pattern for formatting
 * Connects Source -> Processor -> Destination with configurable formatting
 */
class Workflow(
    private val source: MessageSource,
    private val processor: Processor,
    private val destination: MessageDestination
) {
    
    /**
     * Run single message workflow
     */
    fun run(): Boolean {
        return try {
            val message = source.fetchMessage()
            val processedMessage = processor.process(message)
            destination.sendMessage(processedMessage)
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Run batch workflow
     */
    fun runBatch(batchSize: Int = 3): Boolean {
        return try {
            val messages = source.fetchMessages(batchSize)
            val processedMessage = processor.processBatch(messages)
            destination.sendMessage(processedMessage)
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Builder class implementing Strategy Pattern for Workflow creation
     */
    class Builder(private val context: Context) {
        private var source: MessageSource? = null
        private var processor: Processor? = null
        private var destinationType: DestinationType? = null
        private var formatterStrategy: FormatterStrategy = FormatterStrategy.AUTO
        private var customFormatter: OutputFormatter? = null
        private val config = mutableMapOf<String, Any>()
        
        enum class DestinationType {
            GMAIL, TELEGRAM
        }
        
        enum class FormatterStrategy {
            AUTO,           // Automatically choose based on destination
            GMAIL_FORMAT,   // Force Gmail HTML formatting
            TELEGRAM_FORMAT, // Force Telegram markdown formatting
            CUSTOM          // Use provided custom formatter
        }
        
        fun source(source: MessageSource) = apply { this.source = source }
        
        fun processor(processor: Processor) = apply { this.processor = processor }
        
        fun gmailDestination(recipients: List<String>, senderEmail: String = "") = apply {
            this.destinationType = DestinationType.GMAIL
            config["recipients"] = recipients
            config["senderEmail"] = senderEmail
        }
        
        fun telegramDestination(chatId: String) = apply {
            this.destinationType = DestinationType.TELEGRAM
            config["chatId"] = chatId
        }
        
        fun formatterStrategy(strategy: FormatterStrategy) = apply {
            this.formatterStrategy = strategy
        }
        
        fun customFormatter(formatter: OutputFormatter) = apply {
            this.customFormatter = formatter
            this.formatterStrategy = FormatterStrategy.CUSTOM
        }
        
        fun build(): Workflow {
            val source = this.source ?: throw IllegalStateException("Source is required")
            val processor = this.processor ?: throw IllegalStateException("Processor is required")
            val destinationType = this.destinationType ?: throw IllegalStateException("Destination is required")
            
            // Create formatter based on strategy
            val formatter = createFormatter(destinationType, formatterStrategy)
            
            // Add formatter and context to config
            config["formatter"] = formatter
            config["context"] = context
            
            // Create destination with formatter
            val destination = createDestination(destinationType, config)
            
            return Workflow(source, processor, destination)
        }
        
        private fun createFormatter(destinationType: DestinationType, strategy: FormatterStrategy): OutputFormatter {
            return when (strategy) {
                FormatterStrategy.CUSTOM -> customFormatter 
                    ?: throw IllegalStateException("Custom formatter not provided")
                
                FormatterStrategy.GMAIL_FORMAT -> GmailFormatter()
                
                FormatterStrategy.TELEGRAM_FORMAT -> TelegramFormatter()
                
                FormatterStrategy.AUTO -> when (destinationType) {
                    DestinationType.GMAIL -> GmailFormatter()
                    DestinationType.TELEGRAM -> TelegramFormatter()
                }
            }
        }
        
        private fun createDestination(type: DestinationType, config: Map<String, Any>): MessageDestination {
            return when (type) {
                DestinationType.GMAIL -> GmailDestination(config)
                DestinationType.TELEGRAM -> TelegramDestination(config)
            }
        }
    }
    
    companion object {
        fun builder(context: Context) = Builder(context)
    }
}
