package com.example.llmapp.strategies

/**
 * Strategy interface for different LLM prompt configurations
 */
interface PromptStrategy {
    fun buildPrompt(sourceMessage: com.example.llmapp.interfaces.MessageSourceService.SourceMessage): String
    fun getStrategyName(): String
    fun getDescription(): String
}
