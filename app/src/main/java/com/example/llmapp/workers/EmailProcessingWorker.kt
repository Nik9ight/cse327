package com.example.llmapp.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.llmapp.GmailService
import com.example.llmapp.MODEL_TEXT_CLASSIFICATION_MOBILEBERT
import com.example.llmapp.TelegramService
import com.example.llmapp.GmailToTelegramPipeline
import com.example.llmapp.patterns.PipelineObserver
import com.example.llmapp.pipeline.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * Worker class for background processing of emails
 */
class EmailProcessingWorker(
    private val appContext: Context, 
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    private val TAG = "EmailProcessingWorker"
    
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting email processing job")
            
            // Create services
            val gmailService = GmailService(appContext)
            val telegramService = TelegramService(appContext)
            val model = MODEL_TEXT_CLASSIFICATION_MOBILEBERT.copy(llmSupportImage = false)
            
            // Check if services are configured properly
            if (!gmailService.isSignedIn() || !telegramService.isLoggedIn()) {
                Log.w(TAG, "Services not configured properly")
                return@withContext Result.retry() // Retry later, user might sign in
            }
            
            // Create pipeline components
            val fetchService = GmailFetchService(appContext, gmailService)
            val contentProcessor = LlmContentProcessor(appContext, model)
            val deliveryService = TelegramDeliveryService(appContext, telegramService)
            
            // Create the new pipeline with cancellation support
            val pipeline = EmailProcessingPipeline(
                context = appContext,
                fetchService = fetchService,
                contentProcessor = contentProcessor,
                deliveryService = deliveryService,
                onCancellation = {
                    Log.d(TAG, "Pipeline cancelled via worker cancellation")
                }
            )
            
            // Verify that the pipeline is properly configured
            if (!pipeline.isConfigured()) {
                Log.w(TAG, "Pipeline not properly configured")
                return@withContext Result.retry()
            }
            
            // Use suspendCancellableCoroutine to convert callback-style to coroutine-style
            return@withContext try {
                val result = suspendCancellableCoroutine<Result> { continuation ->
                    // Set up a timeout job in the same coroutine scope
                    val timeoutJob = GlobalScope.launch {
                        delay(60000) // 60 seconds timeout
                        if (continuation.isActive) {
                            Log.w(TAG, "Operation timed out")
                            continuation.resume(Result.retry())
                        }
                    }
                    
                    // Set up a completion callback
                    pipeline.setCompletionCallback(object : EmailProcessingPipeline.CompletionCallback {
                        override fun onComplete(success: Boolean, message: String) {
                            Log.d(TAG, "Pipeline completed: $message")
                            
                            // Cancel the timeout job since processing is complete
                            timeoutJob.cancel()
                            
                            // Resume with appropriate result
                            if (success) {
                                continuation.resume(Result.success())
                            } else {
                                continuation.resume(Result.retry())
                            }
                        }
                    })
                    
                    // Process emails
                    pipeline.processEmails(3)
                    
                    // Register cancellation handler
                    continuation.invokeOnCancellation { cause ->
                        timeoutJob.cancel()
                        pipeline.onCancellation() // This will handle all cleanup
                        Log.d(TAG, "Worker cancelled: ${cause?.message ?: "No cause specified"}")
                    }
                }
                
                result
            } catch (e: Exception) {
                Log.e(TAG, "Error in processing: ${e.message}")
                Result.failure()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Worker execution failed", e)
            return@withContext Result.failure()
        }
    }
}
