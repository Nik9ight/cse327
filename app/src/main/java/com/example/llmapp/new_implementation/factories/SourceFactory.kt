package com.example.llmapp.new_implementation.factories

import com.example.llmapp.new_implementation.interfaces.MessageSource
import com.example.llmapp.new_implementation.GmailSource
import com.example.llmapp.new_implementation.TelegramSource

/**
 * Factory Pattern for creating MessageSource instances
 * As discussed in the ChatGPT conversation
 */
object SourceFactory {
    fun create(type: String): MessageSource = when(type.lowercase()) {
        "gmail" -> GmailSource()
        "telegram" -> TelegramSource()
        else -> throw IllegalArgumentException("Invalid source type: $type")
    }
    
    fun create(type: String, config: Map<String, Any>): MessageSource = when(type.lowercase()) {
        "gmail" -> GmailSource(config)
        "telegram" -> TelegramSource(config)
        else -> throw IllegalArgumentException("Invalid source type: $type")
    }
    
    fun getSupportedTypes(): List<String> = listOf("gmail", "telegram")
}
