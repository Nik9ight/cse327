package com.example.llmapp.pipeline

/**
 * Interface for services that support cancellation
 */
interface CancellableService {
    /**
     * Cancel any pending operations
     */
    fun cancelPendingOperations()
}
