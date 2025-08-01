package com.example.llmapp.new_implementation.factories

import com.example.llmapp.new_implementation.interfaces.MessageDestination
import com.example.llmapp.new_implementation.GmailDestination
import com.example.llmapp.new_implementation.TelegramDestination

/**
 * Factory Pattern for creating MessageDestination instances
 * As discussed in the ChatGPT conversation
 */
object DestinationFactory {
    fun create(type: String): MessageDestination = when(type.lowercase()) {
        "gmail" -> GmailDestination()
        "telegram" -> TelegramDestination()
        else -> throw IllegalArgumentException("Invalid destination type: $type")
    }
    
    fun create(type: String, config: Map<String, Any>): MessageDestination = when(type.lowercase()) {
        "gmail" -> GmailDestination(config)
        "telegram" -> TelegramDestination(config)
        else -> throw IllegalArgumentException("Invalid destination type: $type")
    }
    
    fun getSupportedTypes(): List<String> = listOf("gmail", "telegram")
}
