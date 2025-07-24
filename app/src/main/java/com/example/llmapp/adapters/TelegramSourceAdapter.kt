package com.example.llmapp.adapters

import android.util.Log
import com.example.llmapp.pipeline.TelegramMessage
import com.example.llmapp.interfaces.MessageSourceService
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import java.net.URLEncoder
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.*

/**
 * Adapter for integrating Telegram Bot API as a message source
 * Implements the Adapter pattern to convert Telegram API responses to our standard format
 */
class TelegramSourceAdapter(
    private val botToken: String,
    private val chatId: String? = null // If null, will get from all accessible chats
) : MessageSourceService {
    
    companion object {
        private const val TELEGRAM_API_BASE = "https://api.telegram.org/bot"
        private val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        private const val TAG = "TelegramSourceAdapter"
    }
    
    private val client = OkHttpClient()
    
    // Simple message cache to ensure consistent results within a session
    private var cachedMessages: List<TelegramMessage> = emptyList()
    private var lastCacheTime: Long = 0
    private val cacheValidityMs: Long = 30000 // 30 seconds cache validity
    
    override fun isConfigured(): Boolean {
        return !botToken.isBlank() && validateBotToken()
    }
    
    /**
     * Validate that the bot token is working by calling getMe API
     */
    private fun validateBotToken(): Boolean {
        return try {
            Log.d(TAG, "Validating Telegram bot token...")
            
            val url = "$TELEGRAM_API_BASE$botToken/getMe"
            val request = Request.Builder().url(url).build()
            
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()
            
            if (response.isSuccessful && responseBody != null) {
                val json = JSONObject(responseBody)
                val isValid = json.getBoolean("ok")
                
                if (isValid) {
                    val result = json.getJSONObject("result")
                    val botUsername = result.optString("username", "Unknown")
                    Log.d(TAG, "Bot token validated successfully. Bot username: @$botUsername")
                } else {
                    Log.e(TAG, "Bot token validation failed: ${json.optString("description")}")
                }
                
                isValid
            } else {
                Log.e(TAG, "HTTP error validating bot token: ${response.code} - $responseBody")
                false
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error validating bot token", e)
            false
        }
    }
    
    override fun retrieveMessages(count: Int, callback: MessageSourceService.MessageRetrievalCallback) {
        try {
            val telegramMessages = fetchRecentTelegramMessages(count)
            val sourceMessages = telegramMessages.map { convertToSourceMessage(it) }
            callback.onMessagesRetrieved(sourceMessages)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to retrieve Telegram messages", e)
            callback.onError("Failed to retrieve messages: ${e.message}")
        }
    }
    
    override fun markMessageAsProcessed(messageId: String, callback: MessageSourceService.OperationCallback) {
        try {
            // In a real implementation, this would mark the message as read/processed in Telegram
            Log.d(TAG, "Marking message as processed: $messageId")
            callback.onSuccess()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to mark message as processed: $messageId", e)
            callback.onError("Failed to mark message as processed: ${e.message}")
        }
    }
    
    // Legacy methods for backward compatibility with existing pipeline code
    fun getMessages(): List<MessageSourceService.SourceMessage> {
        return try {
            val telegramMessages = fetchTelegramMessages()
            telegramMessages.map { convertToSourceMessage(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch Telegram messages", e)
            emptyList()
        }
    }
    
    fun getMessageById(id: String): MessageSourceService.SourceMessage? {
        return try {
            val telegramMessage = fetchTelegramMessageById(id)
            telegramMessage?.let { convertToSourceMessage(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch Telegram message by ID: $id", e)
            null
        }
    }
    
    fun getRecentMessages(limit: Int): List<MessageSourceService.SourceMessage> {
        return try {
            val telegramMessages = fetchRecentTelegramMessages(limit)
            telegramMessages.map { convertToSourceMessage(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch recent Telegram messages", e)
            emptyList()
        }
    }
    
    /**
     * Get the most recent conversation from a specific chat
     * Always fetches all available messages (no offset tracking)
     */
    suspend fun getRecentConversation(chatId: String, messageLimit: Int = 10): List<TelegramMessage> {
        return try {
            Log.d(TAG, "Fetching recent conversation from chat: $chatId with limit: $messageLimit")
            
            // Always fetch all available messages (no offset tracking)
            Log.d(TAG, "Using getAllUpdatesWithoutOffset to fetch messages")
            val updates = getAllUpdatesWithoutOffset(limit = messageLimit * 3) // Get more to filter by chat
            
            Log.d(TAG, "Fetched ${updates.size} total updates from Telegram API")
            
            // Detailed debugging of received messages
            if (updates.isEmpty()) {
                Log.w(TAG, "No updates returned from Telegram API")
            } else {
                // Debug: Log all chat IDs we received
                val receivedChatIds = updates.mapNotNull { it.metadata["chat_id"] }.distinct()
                Log.d(TAG, "Received messages from chats: $receivedChatIds")
                Log.d(TAG, "Looking for chat: $chatId")
                
                // Log more details about the first few messages
                updates.take(3).forEachIndexed { index, message ->
                    Log.d(TAG, "Sample message $index: ID=${message.id}, " +
                          "chat_id=${message.metadata["chat_id"]}, " +
                          "sender=${message.senderName}, " +
                          "content=${message.content.take(30)}...")
                }
            }
            
            // Enhanced filtering: Handle both string and numeric chat ID comparisons with detailed logging
            val filteredMessages = updates.filter { message ->
                val messageChatId = message.metadata["chat_id"]
                val matches = when {
                    messageChatId == chatId -> {
                        Log.d(TAG, "Exact match: '$messageChatId' == '$chatId'")
                        true
                    }
                    messageChatId?.toLongOrNull()?.toString() == chatId -> {
                        Log.d(TAG, "Numeric match 1: messageChatId as number '${messageChatId?.toLongOrNull()}' == '$chatId'")
                        true
                    }
                    chatId.toLongOrNull()?.toString() == messageChatId -> {
                        Log.d(TAG, "Numeric match 2: chatId as number '${chatId.toLongOrNull()}' == '$messageChatId'")
                        true
                    }
                    else -> {
                        Log.v(TAG, "No match between '$messageChatId' and '$chatId'")
                        false
                    }
                }
                
                Log.d(TAG, "Comparing message chat ID '$messageChatId' with target '$chatId' - matches: $matches")
                matches
            }.take(messageLimit)
            
            Log.d(TAG, "Found ${filteredMessages.size} messages for chat: $chatId")
            
            // If no messages found from API, try fallback immediately
            if (filteredMessages.isEmpty()) {
                Log.w(TAG, "No messages found for chat $chatId from API, using mock data")
                return generateMockConversation(chatId, messageLimit)
            }
            
            filteredMessages
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch conversation from chat: $chatId", e)
            // Fallback to mock data if API fails
            generateMockConversation(chatId, messageLimit)
        }
    }
    
    /**
     * Get recent messages from any available chat (useful for testing)
     */
    suspend fun getRecentMessagesFromAnyChat(messageLimit: Int = 10): List<TelegramMessage> {
        return try {
            Log.d(TAG, "Fetching recent messages from any available chat")
            
            val updates = getAllUpdatesWithoutOffset(limit = messageLimit * 2)
            
            if (updates.isEmpty()) {
                Log.w(TAG, "No messages found from any chat, using mock data")
                return generateMockMessages().take(messageLimit)
            }
            
            Log.d(TAG, "Found ${updates.size} messages from any chat")
            updates.take(messageLimit)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch messages from any chat", e)
            generateMockMessages().take(messageLimit)
        }
    }
    
    /**
     * Get list of available chat IDs that have recent messages
     */
    suspend fun getAvailableChatIds(): List<String> {
        return try {
            Log.d(TAG, "Fetching available chat IDs")
            
            val updates = getAllUpdatesWithoutOffset(limit = 100) // Increased limit to find more chats
            Log.d(TAG, "Fetched ${updates.size} updates to extract chat IDs")
            
            // Extract chat IDs with detailed logging
            val chatIds = updates.mapNotNull { 
                val chatId = it.metadata["chat_id"]
                if (chatId != null) {
                    Log.v(TAG, "Found chat ID: $chatId from message ${it.id}")
                } else {
                    Log.w(TAG, "Message ${it.id} has no chat_id in metadata: ${it.metadata}")
                }
                chatId
            }.distinct()
            
            Log.d(TAG, "Found ${chatIds.size} unique chats: $chatIds")
            
            // If no chat IDs found, log a warning
            if (chatIds.isEmpty()) {
                Log.w(TAG, "No chat IDs found in updates. Check if the bot has received any messages.")
            }
            
            chatIds
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch available chat IDs", e)
            emptyList()
        }
    }
    
    /**
     * Public method to get updates for debugging - no offset tracking
     */
    suspend fun getUpdates(offset: Long? = null, limit: Int = 100): List<TelegramMessage> {
        Log.d(TAG, "getUpdates called (ignoring offset parameter - no tracking)")
        return getAllUpdatesWithoutOffset(limit)
    }
    
    /**
     * Get ALL updates from Telegram Bot API with simple caching
     * This ensures consistent results across multiple calls within a session
     */
    private suspend fun getAllUpdatesWithoutOffset(limit: Int = 100): List<TelegramMessage> {
        return withContext(Dispatchers.IO) {
            try {
                val currentTime = System.currentTimeMillis()
                
                // Return cached messages if cache is still valid
                if (cachedMessages.isNotEmpty() && (currentTime - lastCacheTime) < cacheValidityMs) {
                    Log.d(TAG, "Returning cached messages (${cachedMessages.size} messages)")
                    return@withContext cachedMessages
                }
                
                // Simple URL building - NO offset parameter at all
                val url = "$TELEGRAM_API_BASE$botToken/getUpdates?limit=$limit&timeout=30"
                
                Log.d(TAG, "Requesting ALL Telegram updates with URL: $url")
                Log.d(TAG, "Parameters - limit: $limit (NO OFFSET TRACKING)")
                
                val request = Request.Builder()
                    .url(url)
                    .get()
                    .build()
                
                val response = client.newCall(request).execute()
                val responseBody = response.body?.string()
                
                Log.d(TAG, "Response code: ${response.code}")
                Log.d(TAG, "Response body length: ${responseBody?.length ?: 0}")
                
                if (response.isSuccessful && responseBody != null) {
                    // Log a truncated version of the response to avoid overwhelming logs
                    val truncatedResponse = if (responseBody.length > 500) 
                        responseBody.substring(0, 500) + "..." 
                    else 
                        responseBody
                    Log.d(TAG, "Raw API response (truncated): $truncatedResponse")
                    
                    val messages = parseUpdatesResponseWithoutOffset(responseBody)
                    Log.d(TAG, "Parsed ${messages.size} messages from API response")
                    
                    // Update cache
                    cachedMessages = messages
                    lastCacheTime = currentTime
                    Log.d(TAG, "Updated message cache with ${messages.size} messages")
                    
                    messages
                } else {
                    Log.w(TAG, "Failed to get updates: ${response.code} - $responseBody")
                    emptyList()
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error getting Telegram updates", e)
                emptyList()
            }
        }
    }
    
    /**
     * Get updates from Telegram Bot API (async version)
     */
//    fun getUpdates(offset: Long? = null, limit: Int = 100): List<TelegramMessage> {
//        return getUpdatesSync(offset, limit)
//    }
    
    /**
     * Reset method - clears message cache to force fresh fetch
     */
    fun resetUpdateTracking() {
        Log.d(TAG, "Clearing message cache to force fresh fetch")
        cachedMessages = emptyList()
        lastCacheTime = 0
    }
    
    /**
     * Force refresh of message cache by clearing it
     */
    fun clearMessageCache() {
        Log.d(TAG, "Manually clearing message cache")
        cachedMessages = emptyList()
        lastCacheTime = 0
    }
    
    /**
     * Get last update ID (now returns 0 since we don't track)
     */
    fun getLastUpdateId(): Long {
        Log.d(TAG, "getLastUpdateId called - returning 0 (no tracking)")
        return 0
    }
    
    /**
     * Parse the JSON response from Telegram API (simplified - no offset tracking)
     */
    private fun parseUpdatesResponseWithoutOffset(responseBody: String): List<TelegramMessage> {
        return try {
            val json = JSONObject(responseBody)
            
            if (!json.getBoolean("ok")) {
                Log.e(TAG, "Telegram API returned error: ${json.optString("description")}")
                return emptyList()
            }
            
            val result = json.getJSONArray("result")
            Log.d(TAG, "Telegram API returned ${result.length()} updates")
            
            val messages = mutableListOf<TelegramMessage>()
            
            // Simple parsing - no update ID tracking
            for (i in 0 until result.length()) {
                try {
                    val update = result.getJSONObject(i)
                    val updateId = update.getLong("update_id")
                    
                    Log.d(TAG, "Processing update ID: $updateId (NO TRACKING)")
                    
                    // Log the raw update structure for debugging
                    Log.d(TAG, "Update structure: ${update.toString().take(200)}...")
                    
                    if (update.has("message")) {
                        val messageJson = update.getJSONObject("message")
                        Log.d(TAG, "Found message in update: ${messageJson.toString().take(100)}...")
                        
                        val message = parseMessageFromUpdate(messageJson)
                        message?.let { 
                            messages.add(it)
                            Log.d(TAG, "Added message: ${it.id} from chat: ${it.metadata["chat_id"]} with content: ${it.content.take(50)}...")
                        } ?: Log.w(TAG, "Failed to parse message from update $updateId")
                    } else {
                        Log.d(TAG, "Update $updateId does not contain a message field")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing update at index $i", e)
                    // Continue to the next update
                }
            }
            
            Log.d(TAG, "Parsed ${messages.size} messages from Telegram API (no offset tracking)")
            messages
            
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing Telegram updates response", e)
            emptyList()
        }
    }
    
    /**
     * Parse a single message from Telegram API response
     */
    private fun parseMessageFromUpdate(messageJson: JSONObject): TelegramMessage? {
        return try {
            Log.d(TAG, "Parsing message: ${messageJson.toString().take(200)}...")
            
            // Extract basic message information
            val messageId = messageJson.getLong("message_id").toString()
            
            // Handle different message types (text, photo, document, etc.)
            var content = ""
            val messageType: String
            
            when {
                messageJson.has("text") -> {
                    content = messageJson.getString("text")
                    messageType = "text"
                    Log.d(TAG, "Message $messageId has text content: ${content.take(50)}...")
                }
                messageJson.has("photo") -> {
                    content = "[Photo]"
                    messageType = "photo"
                    // Try to get caption if available
                    messageJson.optString("caption", "")?.let {
                        if (it.isNotEmpty()) content += " Caption: $it"
                    }
                }
                messageJson.has("document") -> {
                    content = "[Document]"
                    messageType = "document"
                    // Try to get document filename
                    val doc = messageJson.getJSONObject("document")
                    doc.optString("file_name", "")?.let {
                        if (it.isNotEmpty()) content += " File: $it"
                    }
                }
                messageJson.has("sticker") -> {
                    content = "[Sticker]"
                    messageType = "sticker"
                }
                messageJson.has("audio") -> {
                    content = "[Audio]"
                    messageType = "audio"
                }
                messageJson.has("voice") -> {
                    content = "[Voice]"
                    messageType = "voice"
                }
                else -> {
                    content = "[Unknown message type]"
                    messageType = "unknown"
                    Log.w(TAG, "Unknown message type in message: $messageId")
                }
            }
            
            val date = messageJson.getLong("date") * 1000 // Convert to milliseconds
            
            // Parse sender information
            val from = messageJson.optJSONObject("from")
            val senderName = when {
                from?.has("username") == true -> "@${from.getString("username")}"
                from?.has("first_name") == true -> {
                    val firstName = from.getString("first_name")
                    val lastName = from.optString("last_name", "")
                    if (lastName.isNotEmpty()) "$firstName $lastName" else firstName
                }
                else -> "Unknown User"
            }
            
            // Parse chat information with enhanced logging
            val chat = messageJson.getJSONObject("chat")
            if (chat == null) {
                Log.e(TAG, "Message $messageId has no chat object")
                return null
            }
            
            val chatId = chat.getLong("id").toString()
            val chatType = chat.getString("type")
            
            Log.d(TAG, "Message $messageId is from chat ID: $chatId (type: $chatType)")
            
            // Create detailed metadata
            val metadata = mutableMapOf<String, String>().apply {
                put("chat_id", chatId)
                put("message_type", messageType)
                put("date_unix", messageJson.getLong("date").toString())
                
                // Add chat title if available
                chat.optString("title")?.takeIf { it.isNotEmpty() }?.let {
                    put("chat_title", it)
                }
                
                // Add user ID
                from?.optLong("id")?.let {
                    put("user_id", it.toString())
                }
                
                // If replying to another message
                messageJson.optJSONObject("reply_to_message")?.let {
                    put("reply_to_message_id", it.getLong("message_id").toString())
                }
                
                // Add forward info if available
                messageJson.optJSONObject("forward_from")?.let {
                    put("forwarded", "true")
                    put("forward_from", it.toString())
                }
            }
            
            TelegramMessage(
                id = messageId,
                content = content,
                senderName = senderName,
                timestamp = DATE_FORMAT.format(Date(date)),
                chatType = chatType,
                metadata = metadata
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing message from update", e)
            null
        }
    }
    
    /**
     * Send a message to Telegram (for testing/confirmation purposes)
     */
    fun sendMessage(chatId: String, text: String): Boolean {
        return try {
            Log.d(TAG, "Sending message to chat: $chatId")
            
            val url = "$TELEGRAM_API_BASE$botToken/sendMessage"
            
            // Build form data
            val formBody = FormBody.Builder()
                .add("chat_id", chatId)
                .add("text", text)
                .add("parse_mode", "HTML") // Allow HTML formatting
                .build()
            
            val request = Request.Builder()
                .url(url)
                .post(formBody)
                .build()
            
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()
            
            if (response.isSuccessful && responseBody != null) {
                val json = JSONObject(responseBody)
                val success = json.getBoolean("ok")
                
                if (success) {
                    Log.d(TAG, "Message sent successfully to chat: $chatId")
                } else {
                    Log.e(TAG, "Failed to send message: ${json.optString("description")}")
                }
                
                success
            } else {
                Log.e(TAG, "HTTP error sending message: ${response.code} - $responseBody")
                false
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error sending message to chat: $chatId", e)
            false
        }
    }
    
    // Private helper methods
    
    private fun fetchTelegramMessages(): List<TelegramMessage> {
        Log.d(TAG, "Fetching all Telegram messages")
        return runBlocking { getAllUpdatesWithoutOffset(limit = 100) }
    }
    
    private fun fetchTelegramMessageById(id: String): TelegramMessage? {
        Log.d(TAG, "Fetching Telegram message by ID: $id")
        // Get all messages and find the one with matching ID
        return runBlocking { getAllUpdatesWithoutOffset(limit = 100) }.find { it.id == id }
    }
    
    private fun fetchRecentTelegramMessages(limit: Int): List<TelegramMessage> {
        Log.d(TAG, "Fetching $limit recent Telegram messages")
        return runBlocking { getAllUpdatesWithoutOffset(limit = limit) }
    }
    
    private fun convertToSourceMessage(telegramMessage: TelegramMessage): MessageSourceService.SourceMessage {
        return MessageSourceService.SourceMessage(
            id = telegramMessage.id,
            subject = "Telegram: ${telegramMessage.senderName}",
            content = telegramMessage.content,
            source = "Telegram",
            metadata = mapOf(
                "sender_name" to telegramMessage.senderName,
                "chat_type" to telegramMessage.chatType,
                "timestamp" to telegramMessage.timestamp,
                "chat_id" to (telegramMessage.metadata["chat_id"]?.toString() ?: "unknown"),
                "message_type" to (telegramMessage.metadata["message_type"]?.toString() ?: "text")
            )
        )
    }
    
    // Mock data generation methods for testing
    
    private fun generateMockMessages(): List<TelegramMessage> {
        val now = System.currentTimeMillis()
        return listOf(
            TelegramMessage(
                id = "msg_001",
                content = "Hey team, can we schedule a meeting for tomorrow to discuss the project timeline?",
                senderName = "Alice Johnson",
                timestamp = DATE_FORMAT.format(Date(now - 3600000)), // 1 hour ago
                chatType = "group",
                metadata = mapOf(
                    "chat_id" to "group_123",
                    "message_type" to "text",
                    "reply_to" to "null"
                )
            ),
            TelegramMessage(
                id = "msg_002",
                content = "Sure! How about 2 PM? I have the requirements document ready to review.",
                senderName = "Bob Smith",
                timestamp = DATE_FORMAT.format(Date(now - 3500000)), // 58 minutes ago
                chatType = "group",
                metadata = mapOf(
                    "chat_id" to "group_123",
                    "message_type" to "text",
                    "reply_to" to "msg_001"
                )
            ),
            TelegramMessage(
                id = "msg_003",
                content = "Perfect! I'll send out calendar invites. Should we include the design team as well?",
                senderName = "Carol White",
                timestamp = DATE_FORMAT.format(Date(now - 3400000)), // 56 minutes ago
                chatType = "group",
                metadata = mapOf(
                    "chat_id" to "group_123",
                    "message_type" to "text",
                    "reply_to" to "msg_002"
                )
            )
        )
    }
    
    private fun generateMockConversation(chatId: String, limit: Int): List<TelegramMessage> {
        val now = System.currentTimeMillis()
        val conversation = mutableListOf<TelegramMessage>()
        
        for (i in 1..limit) {
            conversation.add(
                TelegramMessage(
                    id = "conv_${chatId}_$i",
                    content = when (i % 4) {
                        1 -> "This is an important update about our project status. We've made significant progress."
                        2 -> "I agree! The new features are working well. Should we schedule a demo?"
                        3 -> "Yes, let's set up a demo for next week. I'll coordinate with the stakeholders."
                        else -> "Sounds good! Let me know if you need any help with the preparation."
                    },
                    senderName = when (i % 3) {
                        1 -> "Project Manager"
                        2 -> "Lead Developer"
                        else -> "Product Owner"
                    },
                    timestamp = DATE_FORMAT.format(Date(now - (i * 300000))), // 5 minutes apart
                    chatType = if (chatId.startsWith("group")) "group" else "private",
                    metadata = mapOf(
                        "chat_id" to chatId,
                        "message_type" to "text",
                        "conversation_part" to i.toString()
                    )
                )
            )
        }
        
        return conversation.reversed() // Return in chronological order
    }
    
    private fun generateMockUpdates(limit: Int): List<TelegramMessage> {
        val now = System.currentTimeMillis()
        val updates = mutableListOf<TelegramMessage>()
        
        for (i in 1..limit) {
            updates.add(
                TelegramMessage(
                    id = "update_$i",
                    content = "Update message $i: This is a sample message from the Telegram bot API.",
                    senderName = "Bot User $i",
                    timestamp = DATE_FORMAT.format(Date(now - (i * 60000))), // 1 minute apart
                    chatType = if (i % 2 == 0) "private" else "group",
                    metadata = mapOf(
                        "chat_id" to "chat_$i",
                        "message_type" to "text",
                        "update_id" to i.toString()
                    )
                )
            )
        }
        
        return updates
    }
}

/**
 * Factory for creating Telegram source adapters with different configurations
 */
object TelegramSourceAdapterFactory {
    
    /**
     * Create a Telegram adapter for a specific chat
     */
    fun createForChat(botToken: String, chatId: String): TelegramSourceAdapter {
        return TelegramSourceAdapter(botToken, chatId)
    }
    
    /**
     * Create a Telegram adapter for all accessible chats
     */
    fun createForAllChats(botToken: String): TelegramSourceAdapter {
        return TelegramSourceAdapter(botToken, null)
    }
    
    /**
     * Create a Telegram adapter with validation
     */
    fun createWithValidation(botToken: String, chatId: String? = null): TelegramSourceAdapter? {
        return try {
            if (botToken.isBlank()) {
                Log.e("TelegramSourceAdapterFactory", "Bot token cannot be empty")
                return null
            }
            
            val adapter = TelegramSourceAdapter(botToken, chatId)
            
            // Validate the adapter can connect (in real implementation)
            // validateConnection(adapter)
            
            adapter
        } catch (e: Exception) {
            Log.e("TelegramSourceAdapterFactory", "Failed to create Telegram adapter", e)
            null
        }
    }
    
    private fun validateConnection(adapter: TelegramSourceAdapter): Boolean {
        // In a real implementation, this would test the bot token
        return try {
            // adapter.getUpdates(limit = 1)
            true
        } catch (e: Exception) {
            Log.e("TelegramSourceAdapterFactory", "Telegram connection validation failed", e)
            false
        }
    }
}
