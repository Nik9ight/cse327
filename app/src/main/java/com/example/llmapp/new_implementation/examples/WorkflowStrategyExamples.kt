package com.example.llmapp.new_implementation.examples

import android.content.Context
import com.example.llmapp.new_implementation.*
import com.example.llmapp.new_implementation.interfaces.OutputFormatter
import com.example.llmapp.new_implementation.factories.SourceFactory
import com.example.llmapp.new_implementation.factories.DestinationFactory

/**
 * Examples of using the Strategy Pattern for formatting in workflows
 */
class WorkflowStrategyExamples(private val context: Context) {
    
    /**
     * Example 1: Auto formatting (default behavior)
     */
    fun createStandardGmailToTelegramWorkflow(chatId: String): Workflow {
        val gmailSource = SourceFactory.create("gmail", mapOf(
            "context" to context,
            "searchQuery" to "is:unread"
        ))
        
        val processor = LLMProcessor(context)
        
        return Workflow.builder(context)
            .source(gmailSource)
            .processor(processor)
            .telegramDestination(chatId)
            // Uses TelegramFormatter automatically
            .build()
    }
    
    /**
     * Example 2: Cross-platform formatting
     * Send to Gmail but use Telegram-style formatting (plain text)
     */
    fun createTelegramStyleGmailWorkflow(recipients: List<String>): Workflow {
        val telegramSource = SourceFactory.create("telegram", mapOf(
            "botToken" to "your_bot_token"
        ))
        
        val processor = LLMProcessor(context)
        
        return Workflow.builder(context)
            .source(telegramSource)
            .processor(processor)
            .gmailDestination(recipients)
            .formatterStrategy(Workflow.Builder.FormatterStrategy.TELEGRAM_FORMAT)
            .build()
    }
    
    /**
     * Example 3: Custom formatter
     */
    fun createCustomFormattedWorkflow(chatId: String): Workflow {
        val customFormatter = object : OutputFormatter {
            override fun formatOutput(message: Message): String {
                return """
                🎯 **CUSTOM ALERT**
                
                **From:** ${message.sender}
                **Time:** ${formatTime(message.timestamp)}
                **Platform:** ${message.metadata["platform"]?.uppercase() ?: "UNKNOWN"}
                
                **Content:**
                ${message.content}
                
                _Processed with custom formatting strategy_
                """.trimIndent()
            }
            
            override fun formatBatchOutput(messages: List<Message>): String {
                return """
                📊 **BATCH REPORT**
                
                **Messages Processed:** ${messages.size}
                **Batch ID:** ${System.currentTimeMillis()}
                
                **Summary:**
                ${messages.firstOrNull()?.content ?: "No content"}
                
                _Custom batch processing completed_
                """.trimIndent()
            }
            
            private fun formatTime(timestamp: Long): String {
                return java.text.SimpleDateFormat("MMM dd, HH:mm", java.util.Locale.getDefault())
                    .format(java.util.Date(timestamp))
            }
        }
        
        val gmailSource = SourceFactory.create("gmail", mapOf(
            "context" to context,
            "searchQuery" to "is:unread"
        ))
        
        val processor = LLMProcessor(context)
        
        return Workflow.builder(context)
            .source(gmailSource)
            .processor(processor)
            .telegramDestination(chatId)
            .customFormatter(customFormatter)
            .build()
    }
    
    /**
     * Example 4: Using DestinationFactory with custom formatter
     */
    fun createFactoryBasedWorkflow(): Workflow {
        val compactFormatter = object : OutputFormatter {
            override fun formatOutput(message: Message): String {
                return "${message.sender}: ${message.content}"
            }
            
            override fun formatBatchOutput(messages: List<Message>): String {
                return "Batch (${messages.size}): ${messages.firstOrNull()?.content ?: ""}"
            }
        }
        
        val gmailSource = SourceFactory.create("gmail", mapOf("context" to context))
        val processor = LLMProcessor(context)
        
        // Create destination with custom formatter using factory
        val destination = DestinationFactory.createWithFormatter(
            "telegram", 
            compactFormatter,
            mapOf(
                "context" to context,
                "chatId" to "compact_chat"
            )
        )
        
        return Workflow(gmailSource, processor, destination)
    }
    
    /**
     * Example 5: Mixed strategies - Gmail HTML for important, Telegram for casual
     */
    fun createAdaptiveWorkflow(isImportant: Boolean, recipients: List<String>, chatId: String): Workflow {
        val gmailSource = SourceFactory.create("gmail", mapOf("context" to context))
        val processor = LLMProcessor(context)
        
        return if (isImportant) {
            // Important messages get rich HTML formatting
            Workflow.builder(context)
                .source(gmailSource)
                .processor(processor)
                .gmailDestination(recipients)
                .formatterStrategy(Workflow.Builder.FormatterStrategy.GMAIL_FORMAT)
                .build()
        } else {
            // Casual messages get simple Telegram formatting
            Workflow.builder(context)
                .source(gmailSource)
                .processor(processor)
                .telegramDestination(chatId)
                .formatterStrategy(Workflow.Builder.FormatterStrategy.TELEGRAM_FORMAT)
                .build()
        }
    }
}
