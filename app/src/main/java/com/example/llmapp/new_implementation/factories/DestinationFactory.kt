package com.example.llmapp.new_implementation.factories

import com.example.llmapp.new_implementation.interfaces.MessageDestination
import com.example.llmapp.new_implementation.interfaces.OutputFormatter
import com.example.llmapp.new_implementation.GmailDestination
import com.example.llmapp.new_implementation.TelegramDestination
import com.example.llmapp.new_implementation.GmailFormatter
import com.example.llmapp.new_implementation.TelegramFormatter

/**
 * Factory Pattern for creating com.example.llmapp.new_implementation.interfaces.MessageDestination instances with formatters
 * Enhanced to support Strategy Pattern for formatting
 */
object DestinationFactory {
    
    fun create(type: String): MessageDestination {
        val defaultFormatter = when(type.lowercase()) {
            "gmail" -> GmailFormatter()
            "telegram" -> TelegramFormatter()
            else -> throw IllegalArgumentException("Invalid destination type: $type")
        }
        
        return create(type, mapOf("formatter" to defaultFormatter))
    }
    
    fun create(type: String, config: Map<String, Any>): MessageDestination {
        // Ensure formatter is provided, add default if missing
        val configWithFormatter = if (config.containsKey("formatter")) {
            config
        } else {
            val defaultFormatter = when(type.lowercase()) {
                "gmail" -> GmailFormatter()
                "telegram" -> TelegramFormatter()
                else -> throw IllegalArgumentException("Invalid destination type: $type")
            }
            config + ("formatter" to defaultFormatter)
        }
        
        return when(type.lowercase()) {
            "gmail" -> GmailDestination(configWithFormatter)
            "telegram" -> TelegramDestination(configWithFormatter)
            else -> throw IllegalArgumentException("Invalid destination type: $type")
        }
    }
    
    fun createWithFormatter(type: String, formatter: OutputFormatter, config: Map<String, Any> = emptyMap()): MessageDestination {
        val configWithFormatter = config + ("formatter" to formatter)
        return create(type, configWithFormatter)
    }
    
    fun getSupportedTypes(): List<String> = listOf("gmail", "telegram")
}
