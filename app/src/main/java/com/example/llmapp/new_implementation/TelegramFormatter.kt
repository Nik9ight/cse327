package com.example.llmapp.new_implementation

import com.example.llmapp.new_implementation.interfaces.OutputFormatter

/**
 * TelegramFormatter implementation for formatting messages for Telegram
 */
class TelegramFormatter : OutputFormatter {
    
    override fun formatOutput(message: Message): String {
        val platform = message.metadata["platform"] ?: "unknown"
        val timestamp = formatTimestamp(message.timestamp)
        
        return when (platform) {
            "gmail" -> {
                """
                📧 *Email Summary*
                
                *From:* ${message.sender}
                *Subject:* ${message.metadata["subject"] ?: "No Subject"}
                *Time:* $timestamp
                
                *Summary:*
                ${message.content}
                
                _Automatically processed from Gmail_
                """.trimIndent()
            }
            "telegram" -> {
                """
                🤖 *Message Analysis*
                
                *Original From:* ${message.sender}
                *Processed At:* $timestamp
                
                *Analysis:*
                ${message.content}
                
                _Processed by LLM_
                """.trimIndent()
            }
            else -> {
                """
                📝 *Automated Message*
                
                *From:* ${message.sender}
                *Time:* $timestamp
                
                ${message.content}
                """.trimIndent()
            }
        }
    }
    
    override fun formatBatchOutput(messages: List<Message>): String {
        if (messages.isEmpty()) return "No messages to format"
        
        val firstMessage = messages.first()
        val platform = firstMessage.metadata["platform"] ?: "unknown"
        val timestamp = formatTimestamp(System.currentTimeMillis())
        
        return """
        📊 *Batch Summary Report*
        
        *Source:* ${platform.uppercase()}
        *Messages Processed:* ${messages.size}
        *Generated At:* $timestamp
        
        *Consolidated Analysis:*
        ${firstMessage.content}
        
        _Batch processed by LLM from ${messages.size} ${if (platform == "gmail") "emails" else "messages"}_
        """.trimIndent()
    }
    
    private fun formatTimestamp(timestamp: Long): String {
        return java.text.SimpleDateFormat("MMM dd, HH:mm", java.util.Locale.getDefault())
            .format(java.util.Date(timestamp))
    }
}