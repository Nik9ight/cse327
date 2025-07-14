package com.example.llmapp

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.llmapp.Model
import com.google.mediapipe.framework.image.MPImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TAG = "AGLlmChatView"
//llmChatView
class llmChatView(val model: Model) : ViewModel() {
    var lastResult: String = ""
    var inProgress: Boolean = false

    fun initialize(context: Context, onDone: (String) -> Unit) {
        llmchatmodel.initialize(context, model) { error ->
            onDone(error)
        }
    }

    fun generateResponse(
        context: Context,
        input: String,
        images: List<MPImage> = listOf(),
        onResult: (String, Boolean) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.Default) {
            inProgress = true
            // Wait for instance to be initialized.
            while (model.instance == null) {
                delay(100)
            }
            try {
                llmchatmodel.runInference(
                    model = model,
                    input = input,
                    images = images,
                    resultListener = onResult,
                    cleanUpListener = {
                        inProgress = false
                    }
                )
            } catch (e: Exception) {
                onError("Error: ${e.message}")
                inProgress = false
            }
        }
    }
    
    // Keep backward compatibility with old method signature
    fun generateResponse(
        context: Context,
        input: String,
        images: List<MPImage> = listOf(),
        onResult: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        generateResponse(
            context = context,
            input = input,
            images = images,
            onResult = { result, _ -> onResult(result) },
            onError = onError
        )
    }
    
    fun stopResponse(model: Model) {
        if (inProgress) {
            llmchatmodel.resetSession(model)
            inProgress = false
        }
    }
}