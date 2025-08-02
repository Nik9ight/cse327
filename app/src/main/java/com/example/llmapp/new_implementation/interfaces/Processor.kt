package com.example.llmapp.new_implementation.interfaces

import com.example.llmapp.new_implementation.Message

/**
 * Interface for processing messages with LLM
 * As discussed in the ChatGPT conversation
 */
interface Processor {
    fun process(message: Message): Message
    fun processBatch(messages: List<Message>): Message
}
