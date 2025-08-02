package com.example.llmapp.new_implementation

import com.example.llmapp.new_implementation.interfaces.MessageSource
import com.example.llmapp.adapters.TelegramSourceAdapter
import com.example.llmapp.pipeline.TelegramMessage
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
            val telegramMessages = telegramAdapter.getRecentMessages(limit = 1)
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
            val telegramMessages = telegramAdapter.getRecentMessages(limit = count)
            telegramMessages.map { convertToStandardMessage(it) }
        } catch (e: Exception) {
            Log.e("TelegramSource", "Failed to fetch messages", e)
            emptyList()
        }
    }
    
    private fun convertToStandardMessage(sourceMessage: com.example.llmapp.interfaces.MessageSourceService.SourceMessage): Message {
        return Message(
            id = sourceMessage.id,
            sender = sourceMessage.metadata["sender_name"] ?: "unknown",
            recipient = sourceMessage.metadata["chat_id"] ?: "unknown",
            content = sourceMessage.content,
            timestamp = parseTimestamp(sourceMessage.metadata["original_timestamp"] ?: ""),
            metadata = mapOf(
                "platform" to "telegram",
                "chat_type" to (sourceMessage.metadata["chat_type"] ?: "unknown"),
                "original_timestamp" to (sourceMessage.metadata["original_timestamp"] ?: "")
            ) + sourceMessage.metadata
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