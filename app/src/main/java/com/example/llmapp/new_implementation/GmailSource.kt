package com.example.llmapp.new_implementation

import com.example.llmapp.new_implementation.interfaces.MessageSource
import com.example.llmapp.interfaces.MessageSourceService
import com.example.llmapp.adapters.GmailServiceAdapter
import android.content.Context
import android.util.Log
import java.text.SimpleDateFormat
import java.util.*

/**
 * GmailSource implementation using existing GmailServiceAdapter
 * Converts Gmail messages to standardized Message format
 */
class GmailSource(private val config: Map<String, Any> = emptyMap()) : MessageSource {
    
    private val context: Context? = config["context"] as? Context
    private val searchQuery: String = config["searchQuery"] as? String ?: "is:unread"
    private val gmailAdapter: GmailServiceAdapter? by lazy { 
        context?.let { GmailServiceAdapter(it) }
    }
    
    constructor() : this(emptyMap())
    
    override fun fetchMessage(): Message {
        return try {
            val messages = fetchGmailMessages(1)
            if (messages.isNotEmpty()) {
                messages.first()
            } else {
                createEmptyMessage()
            }
        } catch (e: Exception) {
            Log.e("GmailSource", "Failed to fetch message", e)
            createEmptyMessage()
        }
    }
    
    override fun fetchMessages(count: Int): List<Message> {
        return try {
            fetchGmailMessages(count)
        } catch (e: Exception) {
            Log.e("GmailSource", "Failed to fetch messages", e)
            emptyList()
        }
    }
    
    private fun fetchGmailMessages(count: Int): List<Message> {
        val messages = mutableListOf<Message>()
        
        gmailAdapter?.fetchMessagesByQuery(
            query = searchQuery,
            count = count,
            onSuccess = { sourceMessages ->
                messages.addAll(sourceMessages.map { convertToStandardMessage(it) })
            },
            onError = { error ->
                Log.e("GmailSource", "Error fetching Gmail messages: $error")
            }
        )
        
        return messages
    }
    
    private fun convertToStandardMessage(sourceMessage: MessageSourceService.SourceMessage): Message {
        return Message(
            id = sourceMessage.id,
            sender = sourceMessage.metadata["from"] ?: "unknown",
            recipient = sourceMessage.metadata["to"] ?: "me",
            content = sourceMessage.content,
            timestamp = parseTimestamp(sourceMessage.metadata["date"] ?: ""),
            metadata = mapOf(
                "platform" to "gmail",
                "subject" to sourceMessage.subject,
                "source" to sourceMessage.source
            ) + sourceMessage.metadata
        )
    }
    
    private fun parseTimestamp(dateStr: String): Long {
        return try {
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).parse(dateStr)?.time ?: System.currentTimeMillis()
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
    }
    
    private fun createEmptyMessage(): Message {
        return Message(
            id = "empty_${System.currentTimeMillis()}",
            sender = "system",
            recipient = "system", 
            content = "No emails available",
            timestamp = System.currentTimeMillis(),
            metadata = mapOf("platform" to "gmail", "status" to "empty")
        )
    }
}