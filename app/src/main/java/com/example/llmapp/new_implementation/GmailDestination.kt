package com.example.llmapp.new_implementation

import com.example.llmapp.new_implementation.interfaces.MessageDestination
import com.example.llmapp.GmailService
import android.content.Context
import android.util.Log
import kotlinx.coroutines.runBlocking

/**
 * GmailDestination implementation using existing GmailService
 * Sends standardized Messages via Gmail
 */
class GmailDestination(private val config: Map<String, Any> = emptyMap()) : MessageDestination {
    
    private val context: Context? = config["context"] as? Context
    private val recipients: List<String> = config["recipients"] as? List<String> ?: emptyList()
    private val senderEmail: String = config["senderEmail"] as? String ?: ""
    private val gmailService: GmailService? by lazy {
        context?.let { GmailService(it) }
    }
    
    constructor() : this(emptyMap())
    
    override fun sendMessage(message: Message): Boolean {
        return try {
            val emailRecipients = recipients.ifEmpty { listOf(message.recipient) }
            val subject = generateSubject(message)
            val htmlContent = formatMessageForGmail(message)
            
            runBlocking {
                gmailService?.sendEmail(
                    recipients = emailRecipients,
                    subject = subject,
                    content = htmlContent,
                    isHtml = true
                )
            }
            
            Log.d("GmailDestination", "Email sent successfully: ${message.id}")
            true
        } catch (e: Exception) {
            Log.e("GmailDestination", "Error sending email", e)
            false
        }
    }
    
    override fun sendMessages(messages: List<Message>): List<Boolean> {
        return messages.map { sendMessage(it) }
    }
    
    private fun generateSubject(message: Message): String {
        val platform = message.metadata["platform"] ?: "unknown"
        
        return when (platform) {
            "telegram" -> "Telegram Message Summary from ${message.sender}"
            "gmail" -> "Processed Email: ${message.metadata["subject"] ?: "No Subject"}"
            else -> "Automated Message from ${message.sender}"
        }
    }
    
    private fun formatMessageForGmail(message: Message): String {
        val platform = message.metadata["platform"] ?: "unknown"
        val timestamp = formatTimestamp(message.timestamp)
        
        return when (platform) {
            "telegram" -> {
                """
                <!DOCTYPE html>
                <html>
                <head>
                    <title>Telegram Message Summary</title>
                    <style>
                        body { font-family: Arial, sans-serif; margin: 20px; line-height: 1.6; }
                        .header { background: #0088cc; color: white; padding: 15px; border-radius: 5px; }
                        .content { margin: 20px 0; padding: 15px; background: #f9f9f9; border-radius: 5px; }
                        .metadata { color: #666; font-size: 0.9em; margin-top: 15px; }
                    </style>
                </head>
                <body>
                    <div class="header">
                        <h2>💬 Telegram Message Summary</h2>
                    </div>
                    <div class="content">
                        <p><strong>From:</strong> ${message.sender}</p>
                        <p><strong>Chat Type:</strong> ${message.metadata["chat_type"] ?: "Unknown"}</p>
                        <p><strong>Time:</strong> $timestamp</p>
                        <hr>
                        <h3>Processed Content:</h3>
                        <p>${message.content.replace("\n", "<br>")}</p>
                    </div>
                    <div class="metadata">
                        <p>This email was automatically generated from a Telegram message.</p>
                        <p>Message ID: ${message.id}</p>
                    </div>
                </body>
                </html>
                """.trimIndent()
            }
            "gmail" -> {
                """
                <!DOCTYPE html>
                <html>
                <head>
                    <title>Processed Email</title>
                    <style>
                        body { font-family: Arial, sans-serif; margin: 20px; line-height: 1.6; }
                        .header { background: #d73502; color: white; padding: 15px; border-radius: 5px; }
                        .content { margin: 20px 0; padding: 15px; background: #f9f9f9; border-radius: 5px; }
                        .metadata { color: #666; font-size: 0.9em; margin-top: 15px; }
                    </style>
                </head>
                <body>
                    <div class="header">
                        <h2>📧 Processed Email</h2>
                    </div>
                    <div class="content">
                        <p><strong>Original From:</strong> ${message.sender}</p>
                        <p><strong>Original Subject:</strong> ${message.metadata["subject"] ?: "No Subject"}</p>
                        <p><strong>Processed At:</strong> $timestamp</p>
                        <hr>
                        <h3>Processed Analysis:</h3>
                        <p>${message.content.replace("\n", "<br>")}</p>
                    </div>
                    <div class="metadata">
                        <p>This email was automatically processed and forwarded.</p>
                        <p>Message ID: ${message.id}</p>
                    </div>
                </body>
                </html>
                """.trimIndent()
            }
            else -> {
                """
                <html>
                <body>
                    <h2>Automated Message</h2>
                    <p><strong>From:</strong> ${message.sender}</p>
                    <p><strong>Time:</strong> $timestamp</p>
                    <hr>
                    <p>${message.content.replace("\n", "<br>")}</p>
                </body>
                </html>
                """.trimIndent()
            }
        }
    }
    
    private fun formatTimestamp(timestamp: Long): String {
        return java.text.SimpleDateFormat("MMM dd, yyyy HH:mm", java.util.Locale.getDefault())
            .format(java.util.Date(timestamp))
    }
}