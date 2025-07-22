package com.example.llmapp.pipeline

import com.example.llmapp.interfaces.MessageSourceService

/**
 * Interface for content processing services
 */
interface ContentProcessor {
    /**
     * Process the content of a message
     */
    fun processContent(
        message: MessageSourceService.SourceMessage,
        onComplete: (String) -> Unit,
        onError: (String) -> Unit
    )
    
    /**
     * Check if processor is ready
     */
    fun isReady(): Boolean
    
    /**
     * Clean up resources when cancelled or done
     */
    fun cleanup()
}
