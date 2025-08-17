package com.example.llmapp.utils

import android.content.Context
import android.content.SharedPreferences

/**
 * Manager for storing and retrieving Telegram configuration
 */
class TelegramConfigManager(private val context: Context) {
    
    companion object {
        private const val PREFS_NAME = "telegram_workflow_config"
        private const val KEY_BOT_TOKEN = "bot_token"
        private const val KEY_LAST_VALIDATED = "last_validated"
    }
    
    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    
    /**
     * Save bot token
     */
    fun saveBotToken(token: String) {
        prefs.edit()
            .putString(KEY_BOT_TOKEN, token)
            .putLong(KEY_LAST_VALIDATED, System.currentTimeMillis())
            .apply()
    }
    
    /**
     * Get saved bot token
     */
    fun getBotToken(): String? {
        return prefs.getString(KEY_BOT_TOKEN, null)
    }
    
    /**
     * Check if bot token is configured
     */
    fun isBotTokenConfigured(): Boolean {
        return !getBotToken().isNullOrBlank()
    }
    
    /**
     * Clear bot token
     */
    fun clearBotToken() {
        prefs.edit()
            .remove(KEY_BOT_TOKEN)
            .remove(KEY_LAST_VALIDATED)
            .apply()
    }
    
    /**
     * Get last validation timestamp
     */
    fun getLastValidated(): Long {
        return prefs.getLong(KEY_LAST_VALIDATED, 0)
    }
    
    /**
     * Check if token was validated recently (within 24 hours)
     */
    fun isRecentlyValidated(): Boolean {
        val lastValidated = getLastValidated()
        val dayAgo = System.currentTimeMillis() - (24 * 60 * 60 * 1000)
        return lastValidated > dayAgo
    }
}
