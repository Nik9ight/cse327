package com.example.llmapp.new_implementation.factories

import com.example.llmapp.new_implementation.interfaces.OutputFormatter
import com.example.llmapp.new_implementation.GmailFormatter
import com.example.llmapp.new_implementation.TelegramFormatter
import com.example.llmapp.new_implementation.Message

/**
 * Factory for creating OutputFormatter instances
 * Supports the Strategy Pattern implementation
 */
object OutputFormatterFactory {
    
    fun createFormatter(type: String): OutputFormatter {
        return when (type.lowercase()) {
            "gmail" -> GmailFormatter()
            "telegram" -> TelegramFormatter()
            "compact" -> createCompactFormatter()
            "debug" -> createDebugFormatter()
            else -> throw IllegalArgumentException("Unknown formatter type: $type")
        }
    }
    
    fun createCustomFormatter(
        singleTemplate: String,
        batchTemplate: String? = null
    ): OutputFormatter {
        return CustomTemplateFormatter(singleTemplate, batchTemplate)
    }
    
    private fun createCompactFormatter(): OutputFormatter {
        return object : OutputFormatter {
            override fun formatOutput(message: Message): String {
                val platform = message.metadata["platform"] ?: "unknown"
                val timeStr = formatTimestamp(message.timestamp)
                return "[$platform] ${message.sender} ($timeStr): ${message.content}"
            }
            
            override fun formatBatchOutput(messages: List<Message>): String {
                return "Batch (${messages.size} items): ${messages.firstOrNull()?.content ?: "Empty"}"
            }
        }
    }
    
    private fun createDebugFormatter(): OutputFormatter {
        return object : OutputFormatter {
            override fun formatOutput(message: Message): String {
                return """
                DEBUG FORMAT:
                ID: ${message.id}
                Sender: ${message.sender}
                Recipient: ${message.recipient}
                Timestamp: ${message.timestamp}
                Platform: ${message.metadata["platform"] ?: "N/A"}
                Content Length: ${message.content.length}
                Metadata Keys: ${message.metadata.keys.joinToString(", ")}
                
                Content:
                ${message.content}
                """.trimIndent()
            }
            
            override fun formatBatchOutput(messages: List<Message>): String {
                return """
                DEBUG BATCH FORMAT:
                Message Count: ${messages.size}
                Message IDs: ${messages.map { it.id }.joinToString(", ")}
                Total Content Length: ${messages.sumOf { it.content.length }}
                
                First Message Content:
                ${messages.firstOrNull()?.content ?: "No messages"}
                """.trimIndent()
            }
        }
    }
    
    private fun formatTimestamp(timestamp: Long): String {
        return java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
            .format(java.util.Date(timestamp))
    }
    
    fun getSupportedTypes(): List<String> = listOf("gmail", "telegram", "compact", "debug")
}

/**
 * Custom template-based formatter
 */
private class CustomTemplateFormatter(
    private val singleTemplate: String,
    private val batchTemplate: String?
) : OutputFormatter {
    
    override fun formatOutput(message: Message): String {
        return singleTemplate
            .replace("{id}", message.id)
            .replace("{sender}", message.sender)
            .replace("{recipient}", message.recipient)
            .replace("{content}", message.content)
            .replace("{timestamp}", formatTimestamp(message.timestamp))
            .replace("{platform}", message.metadata["platform"] ?: "unknown")
            .replace("{subject}", message.metadata["subject"] ?: "")
    }
    
    override fun formatBatchOutput(messages: List<Message>): String {
        val template = batchTemplate ?: singleTemplate
        val firstMessage = messages.firstOrNull()
        
        return template
            .replace("{count}", messages.size.toString())
            .replace("{id}", firstMessage?.id ?: "")
            .replace("{sender}", firstMessage?.sender ?: "")
            .replace("{content}", firstMessage?.content ?: "")
            .replace("{timestamp}", formatTimestamp(System.currentTimeMillis()))
            .replace("{platform}", firstMessage?.metadata?.get("platform") ?: "unknown")
    }
    
    private fun formatTimestamp(timestamp: Long): String {
        return java.text.SimpleDateFormat("MMM dd, yyyy HH:mm", java.util.Locale.getDefault())
            .format(java.util.Date(timestamp))
    }
}
