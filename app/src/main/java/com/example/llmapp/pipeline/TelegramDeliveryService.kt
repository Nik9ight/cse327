package com.example.llmapp.pipeline

import android.content.Context
import android.util.Log
import com.example.llmapp.TelegramService

/**
 * Implementation of MessageDeliveryService using Telegram
 */
class TelegramDeliveryService(
    private val context: Context,
    private val telegramService: TelegramService? = null
) : MessageDeliveryService {
    private val TAG = "TelegramDeliverySvc"
    private val service: TelegramService
    
    init {
        service = telegramService ?: TelegramService(context)
        Log.d(TAG, "TelegramDeliveryService initialized, configured: ${isConfigured()}")
    }
    
    override fun sendMessage(
        content: String,
        metadata: Map<String, String>,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (!isConfigured()) {
            onError("Telegram service is not configured")
            return
        }
        
        Log.d(TAG, "Sending message to Telegram")
        
        service.sendMessage(
            content,
            onSuccess = {
                Log.d(TAG, "Message sent successfully")
                onSuccess()
            },
            onError = { error ->
                Log.e(TAG, "Failed to send message: $error")
                onError(error)
            }
        )
    }
    
    override fun isConfigured(): Boolean {
        return service.isLoggedIn()
    }
    
    override fun cancelPendingOperations() {
        Log.d(TAG, "Cancelling pending Telegram operations")
        // Currently the Telegram service operations are synchronous
        // No pending operations to cancel, but implemented for interface compliance
    }
    
    // Helper method to escape HTML special characters for Telegram message formatting
    fun escapeHtml(text: String): String {
        // Escape characters that have special meaning in HTML
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
    }
    
    // Helper method to build a formatted Telegram message
    fun buildFormattedMessage(
        subject: String,
        content: String,
        from: String = "",
        date: String = ""
    ): String {
        // Sanitize all inputs to ensure they're safe for display
        val sanitizedSubject = sanitizeText(subject, 100)
        val sanitizedFrom = sanitizeText(from, 100)
        val sanitizedDate = sanitizeText(date, 100)
        val sanitizedContent = sanitizeText(content, 3000)
        
        val fromSection = if (sanitizedFrom.isNotEmpty()) "\n\n<b>From:</b> ${escapeHtml(sanitizedFrom)}" else ""
        val dateSection = if (sanitizedDate.isNotEmpty()) "\n<b>Date:</b> ${escapeHtml(sanitizedDate)}" else ""
        
        return """
            📧 <b>Email Processed</b>
            
            <b>Subject:</b> ${escapeHtml(sanitizedSubject)}$fromSection$dateSection
            
            <b>Summary:</b>
            ${escapeHtml(sanitizedContent)}
        """.trimIndent()
    }
    
    // Helper method to sanitize text
    private fun sanitizeText(text: String, maxLength: Int = 8000): String {
        if (text.isEmpty()) return ""
        
        // Truncate if longer than max length
        val truncatedText = if (text.length > maxLength) {
            text.substring(0, maxLength) + "..."
        } else {
            text
        }
        
        // Remove control characters that might cause issues
        return truncatedText.replace(Regex("[\\p{Cntrl}&&[^\n\t\r]]"), "")
            .replace(Regex("[\u0000-\u001F]"), "") // Remove ASCII control characters
            .trim()
    }
}
