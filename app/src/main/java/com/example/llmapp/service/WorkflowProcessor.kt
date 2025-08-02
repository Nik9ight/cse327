package com.example.llmapp.service

import android.content.Context
import android.util.Log
import com.example.llmapp.utils.NetworkConnectivityManager
import kotlinx.coroutines.*
import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random

class WorkflowProcessor(private val context: Context) {
    
    private val TAG = "WorkflowProcessor"
    private val maxRetryAttempts = 5
    private val baseDelayMs = 1000L // 1 second
    private val maxDelayMs = 32000L // 32 seconds
    
    // Network connectivity manager for robust network handling
    private val networkManager = NetworkConnectivityManager(context)
    
    /**
     * Main workflow processing loop with robust error handling
     * Implements the continuous loop mentioned in the reference
     */
    suspend fun startContinuousProcessing() {
        var consecutiveFailures = 0
        
        while (currentCoroutineContext().isActive) { // Check if coroutine is still active
            try {
                Log.d(TAG, "Starting workflow processing cycle")
                
                // Check cancellation before starting processing
                currentCoroutineContext().ensureActive()
                
                // Main workflow steps as mentioned in reference:
                // 1. Fetch Gmail messages
                val emails = fetchGmailMessages()
                
                if (emails.isNotEmpty()) {
                    for (email in emails) {
                        try {
                            // Check cancellation before processing each email
                            currentCoroutineContext().ensureActive()
                            
                            // 2. Process with local LLM
                            val llmResult = processWithLLM(email)
                            
                            // 3. Send response via Telegram or Gmail
                            sendResponse(llmResult, email)
                            
                            Log.d(TAG, "Successfully processed email: ${email.subject}")
                        } catch (e: CancellationException) {
                            Log.d(TAG, "Workflow processing cancelled")
                            throw e // Re-throw cancellation to stop the loop
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to process individual email", e)
                            // Continue with next email instead of stopping entirely
                        }
                    }
                }
                
                // Reset failure counter on success
                consecutiveFailures = 0
                
                // Wait before next cycle (15 minutes as suggested in reference)
                // Use delay that respects cancellation
                delay(15 * 60 * 1000L) // 15 minutes
                
            } catch (e: CancellationException) {
                Log.d(TAG, "Workflow processing cancelled gracefully")
                throw e // Re-throw to properly handle cancellation
            } catch (e: Exception) {
                consecutiveFailures++
                Log.e(TAG, "Workflow processing failed (attempt $consecutiveFailures)", e)
                
                // Use exponential backoff for retries
                val delayMs = calculateBackoffDelay(consecutiveFailures)
                Log.d(TAG, "Waiting ${delayMs}ms before retry")
                
                try {
                    delay(delayMs) // This will throw CancellationException if cancelled
                } catch (ce: CancellationException) {
                    Log.d(TAG, "Cancelled during retry delay")
                    throw ce
                }
                
                // If too many failures, wait longer before trying again
                if (consecutiveFailures >= maxRetryAttempts) {
                    Log.w(TAG, "Too many consecutive failures, waiting 5 minutes")
                    try {
                        delay(5 * 60 * 1000L) // 5 minutes
                    } catch (ce: CancellationException) {
                        Log.d(TAG, "Cancelled during failure recovery delay")
                        throw ce
                    }
                    consecutiveFailures = 0 // Reset counter
                }
            }
        }
        
        Log.d(TAG, "Workflow processing loop ended")
    }
    
    /**
     * Fetch Gmail messages with retry logic and network error handling
     */
    private suspend fun fetchGmailMessages(): List<EmailMessage> {
        return retryWithBackoff("fetchGmailMessages") {
            // Check network connectivity first
            if (!isNetworkAvailable()) {
                throw NetworkUnavailableException("No network connection")
            }
            
            // TODO: Implement actual Gmail API call
            // For now, return empty list
            Log.d(TAG, "Fetching Gmail messages...")
            
            // Simulate Gmail API call
            delay(1000) // Simulate network delay
            
            // Return mock data for now - replace with actual Gmail API
            emptyList<EmailMessage>()
        }
    }
    
    /**
     * Process email content with local LLM
     */
    private suspend fun processWithLLM(email: EmailMessage): LLMResult {
        return retryWithBackoff("processWithLLM") {
            Log.d(TAG, "Processing email with LLM: ${email.subject}")
            
            // TODO: Implement actual LLM processing
            // This should call your local LLM (Ollama, llama.cpp, etc.)
            delay(2000) // Simulate LLM processing time
            
            // Return mock result - replace with actual LLM processing
            LLMResult(
                originalEmail = email,
                processedContent = "LLM processed response for: ${email.content}",
                responseType = "telegram" // or "gmail"
            )
        }
    }
    
    /**
     * Send response via Telegram or Gmail with retry logic
     */
    private suspend fun sendResponse(llmResult: LLMResult, originalEmail: EmailMessage) {
        retryWithBackoff("sendResponse") {
            if (!isNetworkAvailable()) {
                throw NetworkUnavailableException("No network connection for sending response")
            }
            
            when (llmResult.responseType) {
                "telegram" -> {
                    Log.d(TAG, "Sending response via Telegram")
                    // TODO: Implement Telegram Bot API call
                    delay(500) // Simulate API call
                }
                "gmail" -> {
                    Log.d(TAG, "Sending response via Gmail")
                    // TODO: Implement Gmail API reply
                    delay(500) // Simulate API call
                }
                else -> {
                    Log.w(TAG, "Unknown response type: ${llmResult.responseType}")
                }
            }
        }
    }
    
    /**
     * Generic retry function with exponential backoff
     * Implements the retry logic mentioned in the reference
     */
    private suspend fun <T> retryWithBackoff(
        operation: String,
        action: suspend () -> T
    ): T {
        repeat(maxRetryAttempts) { attempt ->
            try {
                // Check cancellation before each retry attempt
                currentCoroutineContext().ensureActive()
                return action()
            } catch (e: CancellationException) {
                Log.d(TAG, "$operation cancelled")
                throw e // Re-throw cancellation immediately
            } catch (e: Exception) {
                Log.w(TAG, "$operation failed (attempt ${attempt + 1}/$maxRetryAttempts)", e)
                
                if (attempt == maxRetryAttempts - 1) {
                    throw e // Last attempt, throw the exception
                }
                
                // Calculate delay with exponential backoff + jitter
                val delayMs = calculateBackoffDelay(attempt + 1)
                Log.d(TAG, "Retrying $operation in ${delayMs}ms")
                
                try {
                    delay(delayMs)
                } catch (ce: CancellationException) {
                    Log.d(TAG, "$operation retry cancelled during delay")
                    throw ce
                }
            }
        }
        
        // This shouldn't be reached, but just in case
        throw RuntimeException("Max retry attempts exceeded for $operation")
    }
    
    /**
     * Calculate exponential backoff delay with jitter
     * Prevents thundering herd problem
     */
    private fun calculateBackoffDelay(attempt: Int): Long {
        val exponentialDelay = min(
            baseDelayMs * (2.0.pow(attempt.toDouble())).toLong(),
            maxDelayMs
        )
        
        // Add jitter (random ±25% variation)
        val jitter = (exponentialDelay * 0.25 * (Random.nextDouble() - 0.5)).toLong()
        
        return exponentialDelay + jitter
    }
    
    /**
     * Check network connectivity
     * Implements graceful network handling mentioned in reference
     */
    private suspend fun isNetworkAvailable(): Boolean {
        val hasNetwork = networkManager.hasInternetCapability()
        if (!hasNetwork) {
            Log.w(TAG, "No internet connection available")
            
            // Wait for network to become available (up to 30 seconds)
            Log.d(TAG, "Waiting for network to become available...")
            return networkManager.waitForNetwork(30000)
        }
        return true
    }
}

// Data classes for workflow processing
data class EmailMessage(
    val id: String,
    val subject: String,
    val content: String,
    val sender: String,
    val timestamp: Long
)

data class LLMResult(
    val originalEmail: EmailMessage,
    val processedContent: String,
    val responseType: String // "telegram" or "gmail"
)

// Custom exceptions
class NetworkUnavailableException(message: String) : Exception(message)
