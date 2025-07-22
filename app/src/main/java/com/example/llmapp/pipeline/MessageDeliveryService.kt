package com.example.llmapp.pipeline

/**
 * Interface for message delivery services
 */
interface MessageDeliveryService : CancellableService {
    /**
     * Send a message
     */
    fun sendMessage(
        content: String,
        metadata: Map<String, String> = emptyMap(),
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    )
    
    /**
     * Check if the service is properly configured
     */
    fun isConfigured(): Boolean
}
