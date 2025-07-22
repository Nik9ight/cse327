package com.example.llmapp.adapters

import android.content.Context
import com.example.llmapp.TelegramService
import com.example.llmapp.interfaces.MessageService

/**
 * Adapter for TelegramService to implement MessageService interface
 * Using Adapter pattern to make existing class work with new interfaces
 */
class TelegramServiceAdapter(private val context: Context) : MessageService {
    private val telegramService = TelegramService(context)
    
    override fun isConfigured(): Boolean {
        return telegramService.isLoggedIn()
    }
    
    override fun sendMessage(content: String, callback: MessageService.MessageCallback) {
        if (!isConfigured()) {
            callback.onError("Telegram service is not configured")
            return
        }
        
        telegramService.sendMessage(
            text = content,
            onSuccess = {
                callback.onSuccess()
            },
            onError = { error ->
                callback.onError(error)
            }
        )
    }
    
    // Expose the underlying service for configuration
    fun getUnderlyingService(): TelegramService {
        return telegramService
    }
}
