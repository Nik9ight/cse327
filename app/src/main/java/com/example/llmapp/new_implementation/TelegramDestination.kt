package com.example.llmapp.new_implementation

import com.example.llmapp.new_implementation.interfaces.MessageDestination
import com.example.llmapp.new_implementation.interfaces.OutputFormatter
import com.example.llmapp.TelegramService
import android.content.Context
import android.util.Log

/**
 * TelegramDestination implementation using existing TelegramService
 * Pure delivery mechanism - formatting handled by strategy
 */
class TelegramDestination(private val config: Map<String, Any> = emptyMap()) : MessageDestination {
    
    private val context: Context? = config["context"] as? Context
    private val chatId: String = config["chatId"] as? String ?: ""
    private val formatter: OutputFormatter = config["formatter"] as? OutputFormatter 
        ?: throw IllegalArgumentException("OutputFormatter is required for TelegramDestination")
    private val telegramService: TelegramService? by lazy {
        context?.let { TelegramService(it) }
    }
    
    constructor() : this(emptyMap())
    
    override fun sendMessage(message: Message): Boolean {
        return try {
            // Use the injected formatter strategy
            val formattedContent = formatter.formatOutput(message)
            
            // Set chat ID if not already set
            val chatIdToUse = chatId.ifEmpty { message.recipient }
            if (chatIdToUse.isNotEmpty()) {
                telegramService?.setChatId(chatIdToUse)
            }
            
            telegramService?.sendMessage(
                text = formattedContent,
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
        return if (messages.size == 1) {
            listOf(sendMessage(messages.first()))
        } else {
            sendBatchMessage(messages)
        }
    }
    
    private fun sendBatchMessage(messages: List<Message>): List<Boolean> {
        return try {
            // Use formatter for batch formatting
            val batchContent = formatter.formatBatchOutput(messages)
            val chatIdToUse = chatId.ifEmpty { messages.firstOrNull()?.recipient ?: "" }
            
            // Set chat ID if not already set
            if (chatIdToUse.isNotEmpty()) {
                telegramService?.setChatId(chatIdToUse)
            }
            
            telegramService?.sendMessage(
                text = batchContent,
                onSuccess = {
                    Log.d("TelegramDestination", "Batch message sent successfully")
                },
                onError = { error ->
                    Log.e("TelegramDestination", "Failed to send batch message: $error")
                }
            )
            
            List(messages.size) { true }
        } catch (e: Exception) {
            Log.e("TelegramDestination", "Error sending batch message", e)
            List(messages.size) { false }
        }
    }
}