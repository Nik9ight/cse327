package com.example.llmapp.utils

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Utility class to fetch available chat IDs and usernames from Telegram bot
 * Based on the provided Python function for finding chat IDs and usernames
 */
class TelegramChatFetcher {
    
    companion object {
        private const val TAG = "TelegramChatFetcher"
        private const val TELEGRAM_API_BASE = "https://api.telegram.org/bot"
    }
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
    
    /**
     * Data class representing a Telegram chat
     */
    data class TelegramChat(
        val chatId: String,
        val chatType: String,
        val chatUsername: String?,
        val title: String?,
        val fromUsername: String?,
        val displayName: String
    ) {
        /**
         * Get a user-friendly display text for the chat
         */
        fun getDisplayText(): String {
            return when {
                !title.isNullOrBlank() -> "$title ($chatId)"
                !chatUsername.isNullOrBlank() -> "@$chatUsername ($chatId)"
                !fromUsername.isNullOrBlank() -> "@$fromUsername ($chatId)"
                else -> "Chat $chatId"
            }
        }
        
        /**
         * Get a short description of the chat type
         */
        fun getTypeDescription(): String {
            return when (chatType) {
                "private" -> "Private chat"
                "group" -> "Group"
                "supergroup" -> "Supergroup"
                "channel" -> "Channel"
                else -> chatType.capitalize()
            }
        }
    }
    
    /**
     * Fetch available chat IDs and usernames from Telegram bot updates
     * This is equivalent to the Python find_chat_ids_and_usernames function
     */
    suspend fun findChatIdsAndUsernames(
        botToken: String,
        offset: Int? = null
    ): Result<List<TelegramChat>> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Fetching chat IDs for bot token: ${botToken.take(10)}...")
            
            val url = buildString {
                append("$TELEGRAM_API_BASE$botToken/getUpdates")
                append("?timeout=10")
                if (offset != null) {
                    append("&offset=$offset")
                }
            }
            
            val request = Request.Builder()
                .url(url)
                .build()
            
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()
            
            if (!response.isSuccessful || responseBody == null) {
                val errorMsg = "Failed to fetch updates: ${response.code} - ${response.message}"
                Log.e(TAG, errorMsg)
                return@withContext Result.failure(Exception(errorMsg))
            }
            
            val json = JSONObject(responseBody)
            
            if (!json.getBoolean("ok")) {
                val description = json.optString("description", "Unknown error")
                Log.e(TAG, "Telegram API error: $description")
                return@withContext Result.failure(Exception("Telegram API error: $description"))
            }
            
            val chatsMap = mutableMapOf<String, TelegramChat>()
            val updates = json.optJSONArray("result") ?: return@withContext Result.success(emptyList())
            
            for (i in 0 until updates.length()) {
                val update = updates.getJSONObject(i)
                
                // Get message from different possible fields
                val message = when {
                    update.has("message") -> update.getJSONObject("message")
                    update.has("edited_message") -> update.getJSONObject("edited_message")
                    update.has("channel_post") -> update.getJSONObject("channel_post")
                    else -> continue
                }
                
                val chat = message.optJSONObject("chat") ?: continue
                val fromUser = message.optJSONObject("from")
                
                val chatId = chat.optString("id")
                if (chatId.isBlank()) continue
                
                val chatType = chat.optString("type", "unknown")
                val chatUsername = chat.optString("username").takeIf { it.isNotBlank() }
                val title = chat.optString("title").takeIf { it.isNotBlank() }
                val fromUsername = fromUser?.optString("username")?.takeIf { it.isNotBlank() }
                
                // Create display name based on available information
                val displayName = when {
                    !title.isNullOrBlank() -> title
                    !chatUsername.isNullOrBlank() -> "@$chatUsername"
                    !fromUsername.isNullOrBlank() -> "@$fromUsername"
                    else -> "Chat $chatId"
                }
                
                chatsMap[chatId] = TelegramChat(
                    chatId = chatId,
                    chatType = chatType,
                    chatUsername = chatUsername,
                    title = title,
                    fromUsername = fromUsername,
                    displayName = displayName
                )
            }
            
            val chatsList = chatsMap.values.sortedBy { it.displayName }
            Log.d(TAG, "Found ${chatsList.size} unique chats")
            
            return@withContext Result.success(chatsList)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching chat IDs", e)
            return@withContext Result.failure(e)
        }
    }
    
    /**
     * Get a simple list of chat IDs for quick access
     */
    suspend fun getChatIds(botToken: String): Result<List<String>> {
        return findChatIdsAndUsernames(botToken).map { chats ->
            chats.map { it.chatId }
        }
    }
    
    /**
     * Validate if a bot token is properly formatted
     */
    fun isValidBotTokenFormat(token: String): Boolean {
        // Telegram bot tokens have format: <bot_id>:<auth_token>
        // Example: 123456789:ABCdefGHIjklMNOpqrsTUVwxyz
        val tokenPattern = Regex("^\\d+:[A-Za-z0-9_-]{35,}$")
        return tokenPattern.matches(token)
    }
    
    /**
     * Test if a bot token is valid by making a getMe API call
     */
    suspend fun testBotToken(botToken: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (!isValidBotTokenFormat(botToken)) {
                return@withContext Result.failure(Exception("Invalid bot token format"))
            }
            
            val url = "$TELEGRAM_API_BASE$botToken/getMe"
            val request = Request.Builder().url(url).build()
            
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()
            
            if (!response.isSuccessful || responseBody == null) {
                return@withContext Result.failure(Exception("Failed to validate token: ${response.code}"))
            }
            
            val json = JSONObject(responseBody)
            if (!json.getBoolean("ok")) {
                val description = json.optString("description", "Invalid token")
                return@withContext Result.failure(Exception(description))
            }
            
            val botInfo = json.getJSONObject("result")
            val botName = botInfo.optString("first_name", "Unknown Bot")
            val username = botInfo.optString("username")
            
            Log.d(TAG, "Bot token validated successfully: $botName (@$username)")
            return@withContext Result.success("Bot: $botName (@$username)")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error validating bot token", e)
            return@withContext Result.failure(e)
        }
    }
}
