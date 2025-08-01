package com.example.llmapp.new_implementation

import com.example.llmapp.new_implementation.interfaces.MessageSource
import com.example.llmapp.adapters.TelegramSourceAdapter
import android.util.Log
import java.text.SimpleDateFormat
import java.util.*

/**
 * TelegramSource implementation using existing TelegramSourceAdapter
 * Converts TelegramMessage to standardized Message format
 */
class TelegramSource(private val config: Map<String, Any> = emptyMap()) : MessageSource {
    
    private val botToken: String = config["botToken"] as? String ?: ""
    private val telegramAdapter: TelegramSourceAdapter by lazy { 
        TelegramSourceAdapter(botToken) 
    }
    
    constructor() : this(emptyMap())
    
    override fun fetchMessage(): Message {
        return try {
            val telegramMessages = telegramAdapter.getUpdates(limit = 1)
            if (telegramMessages.isNotEmpty()) {
                convertToStandardMessage(telegramMessages.first())
            } else {
                createEmptyMessage()
            }
        } catch (e: Exception) {
            Log.e("TelegramSource", "Failed to fetch message", e)
            createEmptyMessage()
        }
    }
    
    override fun fetchMessages(count: Int): List<Message> {
        return try {
            val telegramMessages = telegramAdapter.getUpdates(limit = count)
            telegramMessages.map { convertToStandardMessage(it) }
        } catch (e: Exception) {
            Log.e("TelegramSource", "Failed to fetch messages", e)
            emptyList()
        }
    }
    
    private fun convertToStandardMessage(telegramMessage: com.example.llmapp.adapters.TelegramMessage): Message {
        return Message(
            id = telegramMessage.id,
            sender = telegramMessage.senderName,
            recipient = telegramMessage.metadata["chat_id"] ?: "unknown",
            content = telegramMessage.content,
            timestamp = parseTimestamp(telegramMessage.timestamp),
            metadata = mapOf(
                "platform" to "telegram",
                "chat_type" to telegramMessage.chatType,
                "original_timestamp" to telegramMessage.timestamp
            ) + telegramMessage.metadata
        )
    }
    
    private fun parseTimestamp(timestampStr: String): Long {
        return try {
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).parse(timestampStr)?.time ?: System.currentTimeMillis()
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
    }
    
    private fun createEmptyMessage(): Message {
        return Message(
            id = "empty_${System.currentTimeMillis()}",
            sender = "system",
            recipient = "system",
            content = "No messages available",
            timestamp = System.currentTimeMillis(),
            metadata = mapOf("platform" to "telegram", "status" to "empty")
        )
    }
}