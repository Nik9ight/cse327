package com.example.llmapp.adapters

import android.app.Activity
import android.content.ContentValues.TAG
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.llmapp.GmailService
import com.example.llmapp.interfaces.MessageSourceService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Adapter for GmailService to implement MessageSourceService interface
 * Using Adapter pattern to make existing class work with new interfaces
 */
class GmailServiceAdapter : MessageSourceService {
    private val context: Context
    private val gmailService: GmailService
    private val scope = CoroutineScope(Dispatchers.IO)
    
    // Constructor that creates a new GmailService
    constructor(context: Context) {
        this.context = context
        this.gmailService = GmailService(context)
    }
    
    // Constructor that accepts an existing GmailService
    constructor(context: Context, gmailService: GmailService) {
        this.context = context
        this.gmailService = gmailService
    }
    
    override fun isConfigured(): Boolean {
        return gmailService.isSignedIn()
    }
    
    override fun retrieveMessages(count: Int, callback: MessageSourceService.MessageRetrievalCallback) {
        if (!isConfigured()) {
            callback.onError("Gmail service is not configured")
            return
        }
        
        scope.launch {
            try {
                val emails = gmailService.getUnreadEmails(maxResults = count)
                val sourceMessages = emails.map {
                    MessageSourceService.SourceMessage(
                        id = it.id,
                        subject = it.subject,
                        content = it.content,
                        source = "Gmail",
                        metadata = mapOf(
                            "from" to it.from,
                            "date" to it.date
                        )
                    )
                }
                callback.onMessagesRetrieved(sourceMessages)
            } catch (e: Exception) {
                callback.onError("Error retrieving emails: ${e.message}")
            }
        }
    }
    
    
    /**
     * Get unread messages with a simpler callback interface
     * This method exists for compatibility with code that doesn't use the MessageSourceService interface
     * 
     * @param count Number of messages to retrieve
     * @param onSuccess Callback for successful retrieval
     * @param onError Callback for errors
     */
    fun getUnreadMessages(
        count: Int,
        onSuccess: (List<MessageSourceService.SourceMessage>) -> Unit,
        onError: (String) -> Unit
    )
    {
        Log.d("GmailServiceAdapter", "Getting up to $count unread messages")
        retrieveMessages(count, object : MessageSourceService.MessageRetrievalCallback
        {
            override fun onMessagesRetrieved(messages: List<MessageSourceService.SourceMessage>) {
                // Log message content for debugging
                messages.forEach { msg ->
                    Log.d("GmailServiceAdapter", "Message ID: ${msg.id}, Subject: ${msg.subject}")
                    Log.d("GmailServiceAdapter", "Content length: ${msg.content.length}")
                    if (msg.content.isBlank()) {
                        Log.w("GmailServiceAdapter", "Warning: Empty content for message ${msg.id}")
                    }
                }
                onSuccess(messages)
            }
            
            override fun onError(errorMessage: String) {
                Log.e("GmailServiceAdapter", "Error retrieving messages: $errorMessage")
                onError(errorMessage)
            }
        })
    }

    
    override fun markMessageAsProcessed(messageId: String, callback: MessageSourceService.OperationCallback) {
        if (!isConfigured()) {
            callback.onError("Gmail service is not configured")
            return
        }
        
        scope.launch {
            try {
                gmailService.markAsRead(messageId)
                callback.onSuccess()
            } catch (e: Exception) {
                Log.e(TAG, "Error marking message as processed: ${e.message}")
                callback.onError("Failed to mark message as processed: ${e.message}")
            }
        }
    }

    /**
     * Search for messages with a specific query
     */
    /**
 * Search for messages with a specific query
 */
fun searchMessages(
    query: String,
    count: Int,
    onSuccess: (List<MessageSourceService.SourceMessage>) -> Unit,
    onError: (String) -> Unit
) {
    if (!isConfigured()) {
        onError("Gmail service is not configured")
        return
    }

    scope.launch {
        try {
            val emails = gmailService.searchEmails(query = query, maxResults = count)
            val sourceMessages = emails.map { message ->
                // Extract message details from Gmail API Message format
                val headers = message.payload.headers
                val from = headers.find { it.name == "From" }?.value ?: ""
                val subject = headers.find { it.name == "Subject" }?.value ?: ""
                val content = extractEmailContent(message)
                val date = headers.find { it.name == "Date" }?.value ?: ""
                
                MessageSourceService.SourceMessage(
                    id = message.id,
                    subject = subject,
                    content = content,
                    source = "Gmail",
                    metadata = mapOf(
                        "from" to from,
                        "date" to date
                    )
                )
            }
            onSuccess(sourceMessages)
        } catch (e: Exception) {
            Log.e(TAG, "Error searching messages: ${e.message}")
            onError("Failed to search messages: ${e.message}")
        }
    }
}

/**
 * Helper method to extract content from Gmail API Message
 * Extracts content from various parts of the message
 */
private fun extractEmailContent(message: com.google.api.services.gmail.model.Message): String {
    val payload = message.payload
    
    // First try to get content from snippet if it's substantial
    if (message.snippet != null && message.snippet.length > 50) {
        return message.snippet
    }
    
    // Then try to get content from body
    if (payload?.body?.data != null) {
        try {
            val decodedBytes = java.util.Base64.getUrlDecoder().decode(payload.body.data)
            return String(decodedBytes)
        } catch (e: Exception) {
            Log.e(TAG, "Error decoding message body: ${e.message}")
        }
    }
    
    // Try to get content from parts
    val parts = payload?.parts
    if (parts != null) {
        // First try to find plain text content
        for (part in parts) {
            if (part.mimeType == "text/plain" && part.body?.data != null) {
                try {
                    val decodedBytes = java.util.Base64.getUrlDecoder().decode(part.body.data)
                    return String(decodedBytes)
                } catch (e: Exception) {
                    Log.e(TAG, "Error decoding part: ${e.message}")
                }
            }
        }
        
        // If no plain text, try HTML
        for (part in parts) {
            if (part.mimeType == "text/html" && part.body?.data != null) {
                try {
                    val decodedBytes = java.util.Base64.getUrlDecoder().decode(part.body.data)
                    val htmlContent = String(decodedBytes)
                    // Return stripped HTML - could add HTML parsing here
                    return htmlContent.replace(Regex("<[^>]*>"), " ")
                } catch (e: Exception) {
                    Log.e(TAG, "Error decoding HTML part: ${e.message}")
                }
            }
        }
        
        // Check for nested parts (multipart messages)
        for (part in parts) {
            if (part.parts != null) {
                for (nestedPart in part.parts) {
                    if (nestedPart.mimeType == "text/plain" && nestedPart.body?.data != null) {
                        try {
                            val decodedBytes = java.util.Base64.getUrlDecoder().decode(nestedPart.body.data)
                            return String(decodedBytes)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error decoding nested part: ${e.message}")
                        }
                    }
                }
            }
        }
    }
    
    // If all else fails, return the snippet or a placeholder
    return message.snippet ?: "[No content available]"
}

    // new implementation of searchMessages
    // fun searchMessages(count: Int, callback: MessageSourceService.MessageRetrievalCallback) {
    // if (!isConfigured()) {
    //     callback.onError("Gmail service is not configured")
    //     return
    // }
    
    // scope.launch {
    //     try {
    //         // Use searchEmails with "is:unread" as default query instead of getUnreadEmails
    //         val emails = gmailService.searchEmails(query = "is:unread", maxResults = count)
    //         val sourceMessages = emails.map { message ->
    //             // Extract message details from Gmail API Message format
    //             val headers = message.payload.headers
    //             val from = headers.find { it.name == "From" }?.value ?: ""
    //             val subject = headers.find { it.name == "Subject" }?.value ?: ""
    //             val content = gmailService.extractEmailContent(message)
    //             val date = headers.find { it.name == "Date" }?.value ?: ""
                
    //             MessageSourceService.SourceMessage(
    //                 id = message.id,
    //                 subject = subject,
    //                 content = content,
    //                 source = "Gmail",
    //                 metadata = mapOf(
    //                     "from" to from,
    //                     "date" to date
    //                 )
    //             )
    //         }
    //         callback.onMessagesRetrieved(sourceMessages)
    //     } catch (e: Exception) {
    //         callback.onError("Error retrieving emails: ${e.message}")
    //     }
    //     }
    // }

    // Helper method to extract content from Gmail API Message
    //    fun extractEmailContent(message: com.google.api.services.gmail.model.Message): String {
    //        val payload = message.payload
    //
    //        // First try to get content from snippet
    //        if (message.snippet != null && message.snippet.isNotBlank()) {
    //            return message.snippet
    //        }
    //
    //        // Then try to get content from body
    //        if (payload?.body?.data != null) {
    //            try {
    //                val decodedBytes = java.util.Base64.getUrlDecoder().decode(payload.body.data)
    //                return String(decodedBytes)
    //            } catch (e: Exception) {
    //                Log.e("GmailServiceAdapter", "Error decoding message body: ${e.message}")
    //            }
    //        }
    //
    //        // Try to get content from parts
    //        val parts = payload?.parts
    //        if (parts != null) {
    //            for (part in parts) {
    //                if (part.mimeType == "text/plain" && part.body?.data != null) {
    //                    try {
    //                        val decodedBytes = java.util.Base64.getUrlDecoder().decode(part.body.data)
    //                        return String(decodedBytes)
    //                    } catch (e: Exception) {
    //                        Log.e("GmailServiceAdapter", "Error decoding part: ${e.message}")
    //                    }
    //                }
    //            }
    //        }
    //
    //        return "[No content available]"
    //    }


    // fun getQueryMessages(
    // count: Int,
    // onSuccess: (List<MessageSourceService.SourceMessage>) -> Unit,
    // onError: (String) -> Unit
    // ) {
    //     Log.d("GmailServiceAdapter", "Getting up to $count unread messages")
    //     searchMessages(count, object : MessageSourceService.MessageRetrievalCallback {
    //         override fun onMessagesRetrieved(messages: List<MessageSourceService.SourceMessage>) {
    //             // Log message content for debugging
    //             messages.forEach { msg ->
    //                 Log.d("GmailServiceAdapter", "Message ID: ${msg.id}, Subject: ${msg.subject}")
    //                 Log.d("GmailServiceAdapter", "Content length: ${msg.content.length}")
    //                 if (msg.content.isBlank()) {
    //                     Log.w("GmailServiceAdapter", "Warning: Empty content for message ${msg.id}")
    //                 }
    //             }
    //             onSuccess(messages)
    //         }
            
    //         override fun onError(errorMessage: String) {
    //             Log.e("GmailServiceAdapter", "Error retrieving messages: $errorMessage")
    //             onError(errorMessage)
    //         }
    //     })
    // }
    // Methods for handling sign-in
    fun initGoogleSignIn(activity: Activity) {
        gmailService.initGoogleSignIn(activity)
    }
    
    fun signIn(activity: Activity, onComplete: (Boolean) -> Unit) {
        gmailService.signIn(activity, onComplete)
    }
    
    fun handleActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        gmailService.handleActivityResult(requestCode, resultCode, data)
    }
    
    // Expose the underlying service for direct access if needed
    fun getUnderlyingService(): GmailService {
        return gmailService
    }
}
