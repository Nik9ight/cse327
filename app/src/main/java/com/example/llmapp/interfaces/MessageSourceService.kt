package com.example.llmapp.interfaces

/**
 * Interface for any service that can retrieve messages
 * Following Single Responsibility and Interface Segregation principles
 */
interface MessageSourceService {
    /**
     * Represents a message from any source
     */
    data class SourceMessage(
        val id: String,
        val subject: String,
        val content: String,
        val source: String = "",
        val metadata: Map<String, String> = mapOf()
    )
    
    /**
     * Check if the service is properly configured and ready to use
     */
    fun isConfigured(): Boolean
    
    /**
     * Retrieve messages from the source
     * @param count Maximum number of messages to retrieve
     * @param callback Callback to handle retrieved messages or error
     */
    fun retrieveMessages(count: Int, callback: MessageRetrievalCallback)
    
    /**
     * Mark a message as processed
     * @param messageId ID of the message to mark
     * @param callback Callback to handle success or error
     */
    fun markMessageAsProcessed(messageId: String, callback: OperationCallback)
    
    /**
     * Callback interface for message retrieval operations
     */
    interface MessageRetrievalCallback {
        fun onMessagesRetrieved(messages: List<SourceMessage>)
        fun onError(errorMessage: String)
    }
    
    /**
     * Callback interface for general operations
     */
    interface OperationCallback {
        fun onSuccess()
        fun onError(errorMessage: String)
    }
}
