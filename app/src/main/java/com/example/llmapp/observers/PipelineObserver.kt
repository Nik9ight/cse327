package com.example.llmapp.observers

interface PipelineObserver {
    fun onPipelineStarted(message: String)
    fun onProgressUpdate(message: String)
    fun onPipelineCompleted(message: String)
    fun onPipelineError(message: String)
}
