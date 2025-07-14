package com.example.llmapp

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import okhttp3.*
import org.json.JSONObject
import java.io.IOException

private const val TAG = "TelegramService"

class TelegramService(private val context: Context) {
    private var botToken: String? = null
    private var chatId: String? = null
    private var isLoggedIn = false
    private val client = OkHttpClient()

    // Initialize with stored credentials if available
    init {
        val sharedPrefs = context.getSharedPreferences("telegram_prefs", Context.MODE_PRIVATE)
        botToken = sharedPrefs.getString("bot_token", null)
        chatId = sharedPrefs.getString("chat_id", null)
        isLoggedIn = botToken != null && chatId != null
        
        Log.d(TAG, "TelegramService initialized. Token exists: ${botToken != null}, Chat ID: $chatId")
    }

    // Login with bot token
    fun login(token: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        // Validate token by making a getMe call
        val request = Request.Builder()
            .url("https://api.telegram.org/bot$token/getMe")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "Failed to validate token", e)
                (context as? Activity)?.runOnUiThread {
                    onError("Network error: ${e.message}")
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()
                Log.d(TAG, "Response code: ${response.code}, body: $responseBody")
                
                if (response.isSuccessful && responseBody != null) {
                    try {
                        val json = JSONObject(responseBody)
                        if (json.getBoolean("ok")) {
                            // Save the token
                            botToken = token
                            saveCredentials()
                            (context as? Activity)?.runOnUiThread {
                                Toast.makeText(context, "Bot connected successfully", Toast.LENGTH_SHORT).show()
                                onSuccess()
                            }
                        } else {
                            val description = if (json.has("description")) json.getString("description") else "Unknown error"
                            (context as? Activity)?.runOnUiThread {
                                onError("API Error: $description")
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing response", e)
                        (context as? Activity)?.runOnUiThread {
                            onError("Error parsing response: ${e.message}")
                        }
                    }
                } else {
                    val errorMessage = try {
                        if (responseBody != null) {
                            val json = JSONObject(responseBody)
                            if (json.has("description")) json.getString("description") else response.message
                        } else {
                            response.message
                        }
                    } catch (e: Exception) {
                        "Error ${response.code}: ${response.message}"
                    }
                    
                    (context as? Activity)?.runOnUiThread {
                        onError("Telegram API Error: $errorMessage\n\nPlease check that your bot token is correct and properly formatted.")
                    }
                }
            }
        })
    }

    // Set up chat ID
    fun setChatId(id: String) {
        chatId = id
        saveCredentials()
        isLoggedIn = botToken != null && chatId != null
    }

    // Save credentials
    private fun saveCredentials() {
        val sharedPrefs = context.getSharedPreferences("telegram_prefs", Context.MODE_PRIVATE)
        with(sharedPrefs.edit()) {
            botToken?.let { putString("bot_token", it) }
            chatId?.let { putString("chat_id", it) }
            apply()
        }
    }

    // Logout
    fun logout() {
        botToken = null
        chatId = null
        isLoggedIn = false
        val sharedPrefs = context.getSharedPreferences("telegram_prefs", Context.MODE_PRIVATE)
        with(sharedPrefs.edit()) {
            clear()
            apply()
        }
    }

    // Check if logged in
    fun isLoggedIn(): Boolean {
        return isLoggedIn
    }

    // Send message to chat
    fun sendMessage(text: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        if (!isLoggedIn) {
            onError("Not logged in")
            return
        }
        
        Log.d(TAG, "Sending message to chat ID: $chatId")

        val url = "https://api.telegram.org/bot$botToken/sendMessage"
        
        val formBody = FormBody.Builder()
            .add("chat_id", chatId!!)
            .add("text", text)
            .add("parse_mode", "HTML")
            .build()
            
        val request = Request.Builder()
            .url(url)
            .post(formBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "Failed to send message", e)
                (context as? Activity)?.runOnUiThread {
                    onError("Network error: ${e.message}")
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()
                Log.d(TAG, "Send message response code: ${response.code}, body: $responseBody")
                
                if (response.isSuccessful) {
                    (context as? Activity)?.runOnUiThread {
                        onSuccess()
                    }
                } else {
                    val errorMessage = try {
                        if (responseBody != null) {
                            val json = JSONObject(responseBody)
                            if (json.has("description")) json.getString("description") else response.message
                        } else {
                            response.message
                        }
                    } catch (e: Exception) {
                        "Error ${response.code}: ${response.message}"
                    }
                    
                    (context as? Activity)?.runOnUiThread {
                        onError("Telegram API Error: $errorMessage")
                    }
                }
            }
        })
    }

    // Open Telegram app to find chat ID
    fun openTelegramToFindChatId() {
        val telegramIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://telegram.me/getidsbot"))
        if (telegramIntent.resolveActivity(context.packageManager) != null) {
            context.startActivity(telegramIntent)
        } else {
            Toast.makeText(context, "Telegram app not installed", Toast.LENGTH_SHORT).show()
        }
    }
    
    // Verify chat ID works by checking if bot can access it
    fun verifyChatId(chatId: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        if (botToken == null) {
            onError("Bot token not set. Please connect your bot first.")
            return
        }
        
        Log.d(TAG, "Verifying chat ID: $chatId")
        
        // Try to send a test message to verify the chat ID
        val url = "https://api.telegram.org/bot$botToken/getChat"
        
        val formBody = FormBody.Builder()
            .add("chat_id", chatId)
            .build()
            
        val request = Request.Builder()
            .url(url)
            .post(formBody)
            .build()
            
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "Failed to verify chat ID", e)
                (context as? Activity)?.runOnUiThread {
                    onError("Network error: ${e.message}")
                }
            }
            
            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()
                Log.d(TAG, "Verify chat ID response: ${response.code}, body: $responseBody")
                
                if (response.isSuccessful && responseBody != null) {
                    try {
                        val json = JSONObject(responseBody)
                        if (json.getBoolean("ok")) {
                            // Chat ID is valid and bot has access
                            (context as? Activity)?.runOnUiThread {
                                onSuccess()
                            }
                        } else {
                            val description = if (json.has("description")) json.getString("description") else "Unknown error"
                            (context as? Activity)?.runOnUiThread {
                                onError("Chat ID verification failed: $description")
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing response", e)
                        (context as? Activity)?.runOnUiThread {
                            onError("Error parsing response: ${e.message}")
                        }
                    }
                } else {                            val errorMessage = try {
                        if (responseBody != null) {
                            val json = JSONObject(responseBody)
                            if (json.has("description")) json.getString("description") else response.message
                        } else {
                            response.message
                        }
                    } catch (e: Exception) {
                        "Error ${response.code}: ${response.message}"
                    }
                    
                    val errorInfo = if (errorMessage.contains("chat not found") || errorMessage.contains("Bad Request")) {
                        """
                        $errorMessage
                        
                        This usually means:
                        1. Your bot is not a member of the chat/group
                        2. The chat ID format is incorrect
                        3. The bot doesn't have permission to send messages
                        
                        Please add your bot to the chat/group first!
                        """
                    } else {
                        errorMessage
                    }
                    
                    (context as? Activity)?.runOnUiThread {
                        onError("Chat ID verification failed: $errorInfo")
                    }
                }
            }
        })
    }
}
