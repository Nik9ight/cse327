package com.example.llmapp.interfaces

/**
 * Interface for any text processing service that transforms content
 * Following Single Responsibility and Interface Segregation principles
 */
interface ProcessingService {
    /**
     * Process the input text and produce output
     * @param input Text to be processed
     * @param callback Callback to receive processed text or error
     */
    fun processText(input: String, callback: ProcessingCallback)
    
    /**
     * Check if the processing service is ready to use
     */
    fun isReady(): Boolean
    
    /**
     * Callback interface for processing operations
     */
    interface ProcessingCallback {
        fun onProcessingComplete(output: String)
        fun onError(errorMessage: String)
    }
}
