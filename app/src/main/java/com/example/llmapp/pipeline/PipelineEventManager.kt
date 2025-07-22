package com.example.llmapp.pipeline

import android.content.ContentValues.TAG
import android.util.Log
import com.example.llmapp.interfaces.MessageSourceService

/**
 * Interface for pipeline event listeners
 */
interface PipelineEventListener {
    fun onEmailFetched(emailId: String, subject: String)
    fun onProcessingStarted(emailId: String, subject: String)
    fun onProcessingComplete(emailId: String, subject: String, summary: String)
    fun onEmailSent(emailId: String, subject: String, success: Boolean, message: String)
    fun onBatchComplete(successCount: Int, failureCount: Int)
}

/**
 * Pipeline event manager to handle event notifications
 */
class PipelineEventManager {
    private val listeners = mutableListOf<PipelineEventListener>()
    
    fun addListener(listener: PipelineEventListener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener)
        }
    }
    
    fun removeListener(listener: PipelineEventListener) {
        listeners.remove(listener)
    }
    
    fun notifyEmailFetched(emailId: String, subject: String) {
        listeners.forEach { it.onEmailFetched(emailId, subject) }
    }
    
    fun notifyProcessingStarted(emailId: String, subject: String) {
        listeners.forEach { it.onProcessingStarted(emailId, subject) }
    }
    
    fun notifyProcessingComplete(emailId: String, subject: String, summary: String) {
        listeners.forEach { it.onProcessingComplete(emailId, subject, summary) }
    }
    
    fun notifyEmailSent(emailId: String, subject: String, success: Boolean, message: String) {
        listeners.forEach { it.onEmailSent(emailId, subject, success, message) }
    }
    
    fun notifyBatchComplete(successCount: Int, failureCount: Int) {
        listeners.forEach { it.onBatchComplete(successCount, failureCount) }
    }
    
    /**
     * Notify that processing was cancelled
     */
    fun notifyProcessingCancelled() {
        Log.d(TAG, "Processing cancelled")
        // Optional: Add onProcessingCancelled to the listener interface if needed
    }
}
