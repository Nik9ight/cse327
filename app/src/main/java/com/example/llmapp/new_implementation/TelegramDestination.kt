package com.example.llmapp.new_implementation

import com.example.llmapp.new_implementation.interfaces.MessageDestination
import com.example.llmapp.TelegramService
import android.content.Context
import android.util.Log

/**
 * TelegramDestination implementation using existing TelegramService
 * Sends standardized Messages via Telegram
 */
class TelegramDestination(private val config: Map<String, Any> = emptyMap()) : MessageDestination {
    
    private val context: Context? = config["context"] as? Context
    private val chatId: String = config["chatId"] as? String ?: ""
    private val telegramService: TelegramService? by lazy {
        context?.let { TelegramService(it) }
    }
    
    constructor() : this(emptyMap())
    
    override fun sendMessage(message: Message): Boolean {
        return try {
            val formattedContent = formatMessageForTelegram(message)
            
            telegramService?.sendMessage(
                chatId = chatId.ifEmpty { message.recipient },
                message = formattedContent,
                onSuccess = {
                    Log.d("TelegramDestination", "Message sent successfully: ${message.id}")
                },
                onError = { error ->
                    Log.e("TelegramDestination", "Failed to send message: $error")
                }
            )
            
            true
        } catch (e: Exception) {
            Log.e("TelegramDestination", "Error sending message", e)
            false
        }
    }
    
    override fun sendMessages(messages: List<Message>): List<Boolean> {
        return messages.map { sendMessage(it) }
    }
    
    private fun formatMessageForTelegram(message: Message): String {
        val platform = message.metadata["platform"] ?: "unknown"
        val subject = message.metadata["subject"]
        
        return when (platform) {
            "gmail" -> {
                buildString {
                    appendLine("📧 *Email Summary*")
                    appendLine()
                    appendLine("*From:* ${message.sender}")
                    if (subject != null) {
                        appendLine("*Subject:* $subject")
                    }
                    appendLine("*Time:* ${formatTimestamp(message.timestamp)}")
                    appendLine()
                    appendLine("*Content:*")
                    appendLine(message.content)
                }
            }
            "telegram" -> {
                // Telegram to Telegram (processed content)
                buildString {
                    appendLine("🤖 *Processed Message*")
                    appendLine()
                    appendLine("*Original From:* ${message.sender}")
                    appendLine("*Processed At:* ${formatTimestamp(message.timestamp)}")
                    appendLine()
                    appendLine("*Analysis:*")
                    appendLine(message.content)
                }
            }
            else -> message.content
        }
    }
    
    private fun formatTimestamp(timestamp: Long): String {
        return java.text.SimpleDateFormat("MMM dd, yyyy HH:mm", java.util.Locale.getDefault())
            .format(java.util.Date(timestamp))
    }
}