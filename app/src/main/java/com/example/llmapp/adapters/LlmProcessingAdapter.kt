package com.example.llmapp.adapters

import android.content.Context
import com.example.llmapp.Model
import com.example.llmapp.interfaces.ProcessingService
import com.example.llmapp.llmChatView
import com.google.mediapipe.framework.image.MPImage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Adapter for llmChatView to implement ProcessingService interface
 * Using Adapter pattern to make existing class work with new interfaces
 */
class LlmProcessingAdapter(private val model: Model) : ProcessingService {
    private lateinit var chatView: llmChatView
    private var isInitialized = false
    
    override fun isReady(): Boolean {
        return isInitialized
    }
    
    suspend fun initialize(context: Context): Boolean {
        return withContext(Dispatchers.Default) {
            val deferred = CompletableDeferred<Boolean>()
            
            chatView = llmChatView(model)
            chatView.initialize(context) { error ->
                if (error.isEmpty()) {
                    isInitialized = true
                    deferred.complete(true)
                } else {
                    deferred.complete(false)
                }
            }
            
            deferred.await()
        }
    }
    
    override fun processText(input: String, callback: ProcessingService.ProcessingCallback) {
        if (!isReady()) {
            callback.onError("LLM processor is not initialized")
            return
        }
        
        val outputAccumulator = StringBuilder()
        
        try {
            chatView.generateResponse(
                context = chatView.model.instance as Context,
                input = input,
                images = listOf(),
                onResult = { result, isDone ->
                    outputAccumulator.append(result)
                    if (isDone) {
                        callback.onProcessingComplete(outputAccumulator.toString())
                    }
                },
                onError = { errorMsg ->
                    callback.onError(errorMsg)
                }
            )
        } catch (e: Exception) {
            callback.onError("Processing error: ${e.message}")
        }
    }
    
    // Clean up resources
    fun cleanup() {
        if (isInitialized && this::chatView.isInitialized) {
            // Any cleanup if needed
        }
    }
}
