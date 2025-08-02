package com.example.llmapp.new_implementation

import com.example.llmapp.new_implementation.interfaces.Processor
import com.example.llmapp.pipeline.LlmContentProcessor
import android.content.Context
import android.util.Log
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * LLM Processor implementation integrating with existing LlmContentProcessor
 * Processes messages using local LLM as discussed in ChatGPT conversation
 */
class LLMProcessor(private val context: Context) : Processor {
    
    private val llmProcessor: LlmContentProcessor by lazy {
        LlmContentProcessor(context)
    }
    
    override fun process(message: Message): Message {
        return try {
            val platform = message.metadata["platform"] as? String ?: "unknown"
            val processedContent = processWithLLM(message.content, platform)
            
            message.copy(
                content = processedContent,
                timestamp = System.currentTimeMillis(), // Update timestamp to processing time
                metadata = message.metadata + mapOf(
                    "processed" to "true",
                    "processor" to "llm",
                    "original_content_length" to message.content.length.toString()
                )
            )
        } catch (e: Exception) {
            Log.e("LLMProcessor", "Failed to process message", e)
            message.copy(
                content = "Error: Failed to process message - ${e.message}",
                metadata = message.metadata + mapOf("processed" to "false", "error" to e.message.toString())
            )
        }
    }
    
    override fun processBatch(messages: List<Message>): Message {
        return try {
            if (messages.isEmpty()) {
                throw IllegalArgumentException("No messages to process")
            }
            
            // Combine messages as discussed in ChatGPT conversation
            val combinedContent = combineMessages(messages)
            val platform = messages.first().metadata["platform"] as? String ?: "unknown"
            val batchPrompt = createBatchPrompt(combinedContent, platform)
            val processedContent = processWithLLM(batchPrompt, "batch")
            
            // Create a new consolidated message
            Message(
                id = "batch_${System.currentTimeMillis()}",
                sender = "LLM_Processor",
                recipient = messages.first().recipient,
                content = processedContent,
                timestamp = System.currentTimeMillis(),
                metadata = mapOf(
                    "processed" to "true",
                    "processor" to "llm_batch",
                    "original_message_count" to messages.size.toString(),
                    "platform" to platform,
                    "batch_processed" to "true"
                )
            )
        } catch (e: Exception) {
            Log.e("LLMProcessor", "Failed to process batch", e)
            Message(
                id = "batch_error_${System.currentTimeMillis()}",
                sender = "LLM_Processor",
                recipient = "system",
                content = "Error: Failed to process batch - ${e.message}",
                timestamp = System.currentTimeMillis(),
                metadata = mapOf("processed" to "false", "error" to e.message.toString())
            )
        }
    }
    
    private fun processWithLLM(content: String, platform: String): String {
        val prompt = createPrompt(content, platform)
        
        var result = ""
        var error: String? = null
        val latch = CountDownLatch(1)
        
        // Convert to SourceMessage format for existing LLM processor
        val sourceMessage = com.example.llmapp.interfaces.MessageSourceService.SourceMessage(
            id = "llm_${System.currentTimeMillis()}",
            subject = "Content for processing",
            content = prompt,
            source = platform,
            metadata = mapOf("platform" to platform)
        )
        
        llmProcessor.processContent(
            message = sourceMessage,
            onComplete = { processedResult ->
                result = processedResult
                latch.countDown()
            },
            onError = { errorMessage ->
                error = errorMessage
                latch.countDown()
            }
        )
        
        // Wait for processing with timeout
        val completed = latch.await(60, TimeUnit.SECONDS)
        
        return when {
            !completed -> "Error: LLM processing timed out"
            error != null -> "Error: $error"
            result.isNotBlank() -> result
            else -> "Error: LLM produced empty result"
        }
    }
    
    private fun createPrompt(content: String, platform: String): String {
        return when (platform) {
            "gmail" -> {
                """
                Please analyze and summarize this email content:
                
                $content
                
                Provide a concise summary focusing on:
                1. Main topic or purpose
                2. Key points or action items
                3. Urgency level
                4. Brief recommendation
                """.trimIndent()
            }
            "telegram" -> {
                """
                Please analyze this Telegram conversation and provide insights:
                
                $content
                
                Focus on:
                1. Main topics discussed
                2. Important decisions or conclusions
                3. Action items or next steps
                4. Overall sentiment
                """.trimIndent()
            }
            "batch" -> {
                """
                Please analyze this collection of messages and provide a comprehensive summary:
                
                $content
                
                Provide:
                1. Overall theme and key topics
                2. Important patterns or trends
                3. Critical action items
                4. Summary of key decisions
                """.trimIndent()
            }
            else -> {
                """
                Please analyze and summarize this content:
                
                $content
                
                Provide a clear, concise summary of the main points.
                """.trimIndent()
            }
        }
    }
    
    private fun combineMessages(messages: List<Message>): String {
        return buildString {
            appendLine("=== BATCH MESSAGE ANALYSIS ===")
            appendLine("Total Messages: ${messages.size}")
            appendLine()
            
            messages.forEachIndexed { index, message ->
                appendLine("Message ${index + 1}:")
                appendLine("From: ${message.sender}")
                appendLine("Time: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(message.timestamp))}")
                appendLine("Content: ${message.content}")
                appendLine("---")
            }
        }
    }
    
    private fun createBatchPrompt(combinedContent: String, platform: String): String {
        return """
        You are analyzing a batch of ${if (platform == "gmail") "emails" else "messages"} from $platform.
        
        $combinedContent
        
        Please provide a comprehensive analysis including:
        1. Summary of all messages
        2. Key themes and topics
        3. Important action items across all messages
        4. Overall assessment and recommendations
        
        Format your response clearly with headings.
        """.trimIndent()
    }
}
