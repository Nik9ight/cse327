package com.example.llmapp

import android.content.Context
import android.util.Log
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.genai.llminference.GraphOptions
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession

typealias ResultListener = (partialResult: String, done: Boolean) -> Unit

typealias CleanUpListener = () -> Unit

data class LlmModelInstance(val engine: LlmInference, var session: LlmInferenceSession)
private const val TAG = "AGLlmChatModelHelper"

object llmchatmodel {
    private val cleanUpListeners: MutableMap<String, CleanUpListener> = mutableMapOf()

    fun initialize(context: Context,model: Model, onDone: (String) -> Unit) {    //removed model: Model
        // Prepare options.
        val maxTokens =4096
        val topK = 64
        val topP = 0.95f
        val temperature = 1.00f
        val accelerator = "CPU"
        //Log.d(TAG, "Initializing...")
        val preferredBackend = LlmInference.Backend.CPU

        val optionsBuilder =
            LlmInference.LlmInferenceOptions.builder()
                .setModelPath("/data/local/tmp/llm/gemma-3n-E2B-it-int4.task")
                .setMaxTokens(maxTokens)
                .setPreferredBackend(preferredBackend)  //Accelerator.CPU.label
                .setMaxNumImages(1)
        val options = optionsBuilder.build()

        // Create an instance of the LLM Inference task and session.
        try {
            val llmInference = LlmInference.createFromOptions(context, options)

            val session =
                LlmInferenceSession.createFromOptions(
                    llmInference,
                    LlmInferenceSession.LlmInferenceSessionOptions.builder()
                        .setTopK(topK)
                        .setTopP(topP)
                        .setTemperature(temperature)
                        .setGraphOptions(
                            GraphOptions.builder()
                                .setEnableVisionModality(true)
                                .build()
                        )
                        .build(),
                )
            model.instance = LlmModelInstance(engine = llmInference, session = session)
        } catch (e: Exception) {
            onDone("Failed to initialize: ${e.message}")
            return
        }
        onDone("")
    }

    fun resetSession(model: Model) {
        try {
            Log.d(TAG, "Resetting session for model '${model.name}'")

            val instance = model.instance as LlmModelInstance? ?: return
            val session = instance.session
            session.close()

            val inference = instance.engine
            val topK = 64
            val topP = 0.95f
            val temperature = 1.00f
            val newSession =
                LlmInferenceSession.createFromOptions(
                    inference,
                    LlmInferenceSession.LlmInferenceSessionOptions.builder()
                        .setTopK(topK)
                        .setTopP(topP)
                        .setTemperature(temperature)
                        .setGraphOptions(
                            GraphOptions.builder()
                                .setEnableVisionModality(model.llmSupportImage)
                                .build()
                        )
                        .build(),
                )
            instance.session = newSession
            Log.d(TAG, "Resetting done")
        } catch (e: Exception) {
            Log.d(TAG, "Failed to reset session", e)
        }
    }

    fun cleanUp(model: Model) {
        if (model.instance == null) {
            return
        }

        val instance = model.instance as LlmModelInstance

        try {
            instance.session.close()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to close the LLM Inference session: ${e.message}")
        }

        try {
            instance.engine.close()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to close the LLM Inference engine: ${e.message}")
        }

        val onCleanUp = cleanUpListeners.remove(model.name)
        if (onCleanUp != null) {
            onCleanUp()
        }
        model.instance = null
        Log.d(TAG, "Clean up done.")
    }

    fun runInference(
        model: Model,
        input: String,
        resultListener: ResultListener,
        cleanUpListener: CleanUpListener,
        images: List<MPImage> = listOf(),
    ) {
        val instance = model.instance as LlmModelInstance

        // Set listener.
        if (!cleanUpListeners.containsKey(model.name)) {
            cleanUpListeners[model.name] = cleanUpListener
        }

        // Start async inference.
        val session = instance.session
        if (input.trim().isNotEmpty()) {
            session.addQueryChunk(input)
        }
        for (image in images) {
            session.addImage(image)
        }
        val unused = session.generateResponseAsync(resultListener)
    }

}