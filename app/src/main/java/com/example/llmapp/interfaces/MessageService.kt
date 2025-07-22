package com.example.llmapp.interfaces

/**
 * Interface for any service that can send messages
 * Following Single Responsibility and Interface Segregation principles
 */
interface MessageService {
    /**
     * Check if the service is properly configured and ready to use
     */
    fun isConfigured(): Boolean
    
    /**
     * Send a message through the service
     * @param content Message content to send
     * @param callback Callback to handle success or error
     */
    fun sendMessage(content: String, callback: MessageCallback)
    
    /**
     * Callback interface for message operations
     */
    interface MessageCallback {
        fun onSuccess()
        fun onError(errorMessage: String)
    }
}
