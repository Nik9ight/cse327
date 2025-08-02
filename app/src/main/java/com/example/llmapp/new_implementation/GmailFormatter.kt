package com.example.llmapp.new_implementation

import com.example.llmapp.new_implementation.interfaces.OutputFormatter

/**
 * GmailFormatter implementation for formatting messages for Gmail
 */
class GmailFormatter : OutputFormatter {
    
    override fun formatOutput(message: Message): String {
        val platform = message.metadata["platform"] ?: "unknown"
        val timestamp = formatTimestamp(message.timestamp)
        
        return when (platform) {
            "telegram" -> {
                formatTelegramForGmail(message, timestamp)
            }
            "gmail" -> {
                formatGmailForGmail(message, timestamp)
            }
            else -> {
                formatGenericForGmail(message, timestamp)
            }
        }
    }
    
    override fun formatBatchOutput(messages: List<Message>): String {
        if (messages.isEmpty()) return "<p>No messages to format</p>"
        
        val firstMessage = messages.first()
        val platform = firstMessage.metadata["platform"] ?: "unknown"
        val timestamp = formatTimestamp(System.currentTimeMillis())
        
        return """
        <!DOCTYPE html>
        <html>
        <head>
            <title>Batch Analysis Report</title>
            <style>
                body { font-family: Arial, sans-serif; margin: 20px; line-height: 1.6; }
                .header { background: #2c3e50; color: white; padding: 20px; border-radius: 5px; text-align: center; }
                .content { margin: 20px 0; padding: 20px; background: #ecf0f1; border-radius: 5px; }
                .stats { background: #3498db; color: white; padding: 10px; border-radius: 5px; margin: 10px 0; }
                .footer { color: #7f8c8d; font-size: 0.9em; margin-top: 20px; }
            </style>
        </head>
        <body>
            <div class="header">
                <h1>📊 Batch Analysis Report</h1>
            </div>
            <div class="stats">
                <strong>Source Platform:</strong> ${platform.uppercase()} | 
                <strong>Messages Processed:</strong> ${messages.size} | 
                <strong>Generated:</strong> $timestamp
            </div>
            <div class="content">
                <h2>Consolidated Analysis</h2>
                ${firstMessage.content.replace("\n", "<br>")}
            </div>
            <div class="footer">
                <p>This report was automatically generated from ${messages.size} ${if (platform == "gmail") "emails" else "messages"} using LLM processing.</p>
                <p>Report ID: ${firstMessage.id}</p>
            </div>
        </body>
        </html>
        """.trimIndent()
    }
    
    private fun formatTelegramForGmail(message: Message, timestamp: String): String {
        return """
        <!DOCTYPE html>
        <html>
        <head>
            <title>Telegram Message Summary</title>
            <style>
                body { font-family: Arial, sans-serif; margin: 20px; line-height: 1.6; }
                .header { background: #0088cc; color: white; padding: 15px; border-radius: 5px; }
                .content { margin: 20px 0; padding: 15px; background: #f8f9fa; border-radius: 5px; }
                .metadata { background: #e3f2fd; padding: 10px; border-radius: 5px; margin: 10px 0; }
                .footer { color: #666; font-size: 0.9em; margin-top: 15px; }
            </style>
        </head>
        <body>
            <div class="header">
                <h2>💬 Telegram Message Analysis</h2>
            </div>
            <div class="metadata">
                <strong>From:</strong> ${message.sender}<br>
                <strong>Chat Type:</strong> ${message.metadata["chat_type"] ?: "Unknown"}<br>
                <strong>Processed At:</strong> $timestamp
            </div>
            <div class="content">
                <h3>LLM Analysis:</h3>
                <p>${message.content.replace("\n", "<br>")}</p>
            </div>
            <div class="footer">
                <p>This email was automatically generated from a Telegram message and processed with LLM.</p>
                <p>Message ID: ${message.id}</p>
            </div>
        </body>
        </html>
        """.trimIndent()
    }
    
    private fun formatGmailForGmail(message: Message, timestamp: String): String {
        return """
        <!DOCTYPE html>
        <html>
        <head>
            <title>Processed Email</title>
            <style>
                body { font-family: Arial, sans-serif; margin: 20px; line-height: 1.6; }
                .header { background: #d32f2f; color: white; padding: 15px; border-radius: 5px; }
                .content { margin: 20px 0; padding: 15px; background: #fff3e0; border-radius: 5px; }
                .metadata { background: #ffebee; padding: 10px; border-radius: 5px; margin: 10px 0; }
                .footer { color: #666; font-size: 0.9em; margin-top: 15px; }
            </style>
        </head>
        <body>
            <div class="header">
                <h2>📧 Email Analysis Report</h2>
            </div>
            <div class="metadata">
                <strong>Original From:</strong> ${message.sender}<br>
                <strong>Original Subject:</strong> ${message.metadata["subject"] ?: "No Subject"}<br>
                <strong>Processed At:</strong> $timestamp
            </div>
            <div class="content">
                <h3>LLM Analysis:</h3>
                <p>${message.content.replace("\n", "<br>")}</p>
            </div>
            <div class="footer">
                <p>This email was automatically processed and analyzed using LLM.</p>
                <p>Message ID: ${message.id}</p>
            </div>
        </body>
        </html>
        """.trimIndent()
    }
    
    private fun formatGenericForGmail(message: Message, timestamp: String): String {
        return """
        <!DOCTYPE html>
        <html>
        <head>
            <title>Automated Message</title>
            <style>
                body { font-family: Arial, sans-serif; margin: 20px; line-height: 1.6; }
                .header { background: #424242; color: white; padding: 15px; border-radius: 5px; }
                .content { margin: 20px 0; padding: 15px; background: #f5f5f5; border-radius: 5px; }
            </style>
        </head>
        <body>
            <div class="header">
                <h2>📝 Automated Message</h2>
            </div>
            <div class="content">
                <p><strong>From:</strong> ${message.sender}</p>
                <p><strong>Time:</strong> $timestamp</p>
                <hr>
                <p>${message.content.replace("\n", "<br>")}</p>
            </div>
        </body>
        </html>
        """.trimIndent()
    }
    
    private fun formatTimestamp(timestamp: Long): String {
        return java.text.SimpleDateFormat("MMM dd, yyyy HH:mm", java.util.Locale.getDefault())
            .format(java.util.Date(timestamp))
    }
}