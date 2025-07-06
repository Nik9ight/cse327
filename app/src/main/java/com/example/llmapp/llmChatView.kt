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
        onResult: (String) -> Unit,
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
                    resultListener = { partialResult, done ->
                        lastResult = partialResult
                        onResult(partialResult)
                        if (done) inProgress = false
                    },
                    cleanUpListener = {
                        inProgress = false
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error occurred while running inference", e)
                inProgress = false
                onError(e.message ?: "Unknown error")
            }
        }
    }
    fun stopResponse(model: Model) {
        Log.d(TAG, "Stopping response for model ${model.name}...")
        val instance = model.instance as? LlmModelInstance ?: return
        instance.session.cancelGenerateResponseAsync()
    }
}