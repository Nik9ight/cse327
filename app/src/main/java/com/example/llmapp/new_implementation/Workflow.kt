package com.example.llmapp.new_implementation

import com.example.llmapp.new_implementation.interfaces.MessageSource
import com.example.llmapp.new_implementation.interfaces.MessageDestination
import com.example.llmapp.new_implementation.interfaces.Processor

/**
 * Main Workflow class implementing the design from ChatGPT conversation
 * Connects Source -> Processor -> Destination
 */
class Workflow(
    private val source: MessageSource,
    private val processor: Processor,
    private val destination: MessageDestination
) {
    
    /**
     * Run single message workflow
     */
    fun run(): Boolean {
        return try {
            val message = source.fetchMessage()
            val processedMessage = processor.process(message)
            destination.sendMessage(processedMessage)
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Run batch workflow (3 messages at a time as discussed)
     */
    fun runBatch(batchSize: Int = 3): Boolean {
        return try {
            val messages = source.fetchMessages(batchSize)
            val processedMessage = processor.processBatch(messages)
            destination.sendMessage(processedMessage)
        } catch (e: Exception) {
            false
        }
    }
}
