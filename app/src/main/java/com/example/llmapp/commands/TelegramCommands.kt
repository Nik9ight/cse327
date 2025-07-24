package com.example.llmapp.commands

import android.util.Log
import com.example.llmapp.commands.Command
import com.example.llmapp.commands.CommandInvoker
import com.example.llmapp.pipeline.TelegramMessage
import com.example.llmapp.pipeline.TelegramMessageProcessingTemplate

/**
 * Command interface for Telegram message processing operations
 * Extends the base Command interface with Telegram-specific operations
 */
interface TelegramCommand : Command {
    /**
     * Get the Telegram message associated with this command
     */
    fun getMessage(): TelegramMessage?
    
    /**
     * Get the processing result if available
     */
    fun getResult(): String?
}

/**
 * Command for processing a single Telegram message
 */
class ProcessTelegramMessageCommand(
    private val message: TelegramMessage,
    private val processor: TelegramMessageProcessingTemplate
) : TelegramCommand {
    
    private var result: String? = null
    private var wasExecuted = false
    private var originalState: TelegramMessage? = null
    
    override fun execute(): Boolean {
        return try {
            if (wasExecuted) {
                Log.w("ProcessTelegramMessageCommand", "Command already executed")
                return false
            }
            
            originalState = message.copy() // Save original state for undo
            val success = processor.processMessage(message)
            wasExecuted = true
            
            if (success) {
                // Set a success result message
                result = "Successfully processed message from ${message.senderName}"
            } else {
                result = null
            }
            
            // Return the success status
            success
        } catch (e: Exception) {
            Log.e("ProcessTelegramMessageCommand", "Failed to process Telegram message", e)
            false
        }
    }
    
    override fun undo(): Boolean {
        return try {
            if (!wasExecuted) {
                Log.w("ProcessTelegramMessageCommand", "Cannot undo: command not executed")
                return false
            }
            
            // Reset processing state
            result = null
            wasExecuted = false
            
            Log.d("ProcessTelegramMessageCommand", "Successfully undid Telegram message processing")
            true
        } catch (e: Exception) {
            Log.e("ProcessTelegramMessageCommand", "Failed to undo Telegram message processing", e)
            false
        }
    }
    
    override fun getMessage(): TelegramMessage = message
    
    override fun getResult(): String? = result
    
    override fun toString(): String {
        return "ProcessTelegramMessageCommand(message=${message.id}, executed=$wasExecuted)"
    }
}

/**
 * Consolidated command for processing multiple Telegram messages as a single unit
 * This follows SOLID principles by having a single responsibility: consolidate and process messages once
 */
class ConsolidatedTelegramProcessCommand(
    private val messages: List<TelegramMessage>,
    private val processor: TelegramMessageProcessingTemplate
) : TelegramCommand {
    
    private var result: String? = null
    private var wasExecuted = false
    private var consolidatedMessage: String? = null
    private var llmProcessedContent: String? = null // Store actual LLM result
    
    override fun execute(): Boolean {
        return try {
            if (wasExecuted) {
                Log.w("ConsolidatedTelegramProcessCommand", "Command already executed")
                return true // Already executed successfully
            }
            
            if (messages.isEmpty()) {
                Log.w("ConsolidatedTelegramProcessCommand", "No messages to process")
                return false
            }
            
            // Step 1: Consolidate all messages into a single formatted message
            consolidatedMessage = consolidateMessages(messages)
            
            // Step 2: Create a single consolidated TelegramMessage for processing
            val consolidatedTelegramMessage = TelegramMessage(
                id = "consolidated_${System.currentTimeMillis()}",
                content = consolidatedMessage!!,
                senderName = "Multiple Participants: ${messages.map { it.senderName }.distinct().joinToString(", ")}",
                timestamp = messages.maxOfOrNull { it.timestamp } ?: getCurrentTimestamp(),
                chatType = messages.firstOrNull()?.chatType ?: "group",
                metadata = mapOf(
                    "message_count" to messages.size.toString(),
                    "participants" to messages.map { it.senderName }.distinct().joinToString(","),
                    "time_range" to "${messages.minOfOrNull { it.timestamp }} to ${messages.maxOfOrNull { it.timestamp }}"
                )
            )
            
            // Step 3: Process the consolidated message to get LLM result without sending email
            // Check if processor is GmailDeliveryTemplate to access LLM processing directly
            if (processor is com.example.llmapp.pipeline.GmailDeliveryTemplate) {
                // Get LLM processing result directly without triggering email sending
                val llmResult = processor.getLlmProcessingResult(consolidatedTelegramMessage)
                if (llmResult != null) {
                    llmProcessedContent = llmResult
                    result = llmResult // Return the actual LLM processed content
                    wasExecuted = true
                    Log.d("ConsolidatedTelegramProcessCommand", "Successfully captured LLM result for consolidated message with ${messages.size} original messages")
                    return true
                } else {
                    Log.e("ConsolidatedTelegramProcessCommand", "LLM processing returned null")
                    return false
                }
            } else {
                // Fallback: Process the consolidated message once through the template
                val success = processor.processMessage(consolidatedTelegramMessage)
                
                if (success) {
                    // Set a generic result if we can't get LLM content directly
                    result = "Successfully processed ${messages.size} messages as consolidated conversation"
                    wasExecuted = true
                    Log.d("ConsolidatedTelegramProcessCommand", "Successfully processed consolidated message with ${messages.size} original messages")
                } else {
                    Log.e("ConsolidatedTelegramProcessCommand", "Failed to process consolidated message")
                }
                
                return success
            }
        } catch (e: Exception) {
            Log.e("ConsolidatedTelegramProcessCommand", "Failed to execute consolidated processing", e)
            false
        }
    }
    
    override fun undo(): Boolean {
        return try {
            if (!wasExecuted) {
                Log.w("ConsolidatedTelegramProcessCommand", "Nothing to undo - command not executed")
                return true
            }
            
            // Reset processing state
            result = null
            consolidatedMessage = null
            llmProcessedContent = null
            wasExecuted = false
            
            Log.d("ConsolidatedTelegramProcessCommand", "Successfully undid consolidated processing")
            true
        } catch (e: Exception) {
            Log.e("ConsolidatedTelegramProcessCommand", "Failed to undo consolidated processing", e)
            false
        }
    }
    
    override fun getMessage(): TelegramMessage? = messages.firstOrNull()
    
    override fun getResult(): String? = result
    
    /**
     * Get the consolidated message text that was created from all input messages
     */
    fun getConsolidatedMessage(): String? = consolidatedMessage
    
    /**
     * Get the count of original messages that were consolidated
     */
    fun getOriginalMessageCount(): Int = messages.size
    
    /**
     * Get the actual LLM processed content (the AI summary)
     */
    fun getLlmProcessedContent(): String? = llmProcessedContent
    
    /**
     * Consolidate multiple Telegram messages into a single formatted message
     * This follows the Single Responsibility Principle by having one method handle message consolidation
     */
    private fun consolidateMessages(messages: List<TelegramMessage>): String {
        val participants = messages.map { it.senderName }.distinct()
        val timeRange = "${messages.minOfOrNull { it.timestamp }} to ${messages.maxOfOrNull { it.timestamp }}"
        
        val consolidatedText = buildString {
            appendLine("=== TELEGRAM CONVERSATION SUMMARY ===")
            appendLine("Participants: ${participants.joinToString(", ")}")
            appendLine("Time Range: $timeRange")
            appendLine("Total Messages: ${messages.size}")
            appendLine()
            appendLine("=== CONVERSATION CONTENT ===")
            
            messages.forEachIndexed { index, message ->
                appendLine("${index + 1}. [${message.timestamp}] ${message.senderName}:")
                appendLine("   ${message.content}")
                if (index < messages.size - 1) appendLine()
            }
            
            appendLine()
            appendLine("=== END OF CONVERSATION ===")
        }
        
        return consolidatedText
    }
    
    private fun getCurrentTimestamp(): String {
        val formatter = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
        return formatter.format(java.util.Date())
    }
    
    override fun toString(): String {
        return "ConsolidatedTelegramProcessCommand(messages=${messages.size}, executed=$wasExecuted)"
    }
}

/**
 * Batch command for processing multiple Telegram messages
 */
class BatchProcessTelegramCommand(
    private val messages: List<TelegramMessage>,
    private val processor: TelegramMessageProcessingTemplate
) : TelegramCommand {
    
    private val results = mutableMapOf<String, String>()
    private val processedCommands = mutableListOf<ProcessTelegramMessageCommand>()
    private var wasExecuted = false
    
    override fun execute(): Boolean {
        return try {
            if (wasExecuted) {
                Log.w("BatchProcessTelegramCommand", "Batch command already executed")
                return false
            }
            
            var successCount = 0
            
            for (message in messages) {
                val command = ProcessTelegramMessageCommand(message, processor)
                if (command.execute()) {
                    command.getResult()?.let { result ->
                        results[message.id] = result
                    }
                    processedCommands.add(command)
                    successCount++
                } else {
                    Log.w("BatchProcessTelegramCommand", "Failed to process message: ${message.id}")
                }
            }
            
            wasExecuted = true
            Log.d("BatchProcessTelegramCommand", "Processed $successCount/${messages.size} messages")
            
            successCount > 0
        } catch (e: Exception) {
            Log.e("BatchProcessTelegramCommand", "Failed to execute batch processing", e)
            false
        }
    }
    
    override fun undo(): Boolean {
        return try {
            if (!wasExecuted) {
                Log.w("BatchProcessTelegramCommand", "Cannot undo: batch command not executed")
                return false
            }
            
            var undoCount = 0
            
            // Undo all processed commands in reverse order
            for (command in processedCommands.reversed()) {
                if (command.undo()) {
                    undoCount++
                }
            }
            
            results.clear()
            processedCommands.clear()
            wasExecuted = false
            
            Log.d("BatchProcessTelegramCommand", "Undid $undoCount operations")
            true
        } catch (e: Exception) {
            Log.e("BatchProcessTelegramCommand", "Failed to undo batch processing", e)
            false
        }
    }
    
    override fun getMessage(): TelegramMessage? = messages.firstOrNull()
    
    override fun getResult(): String? = results.values.joinToString("\n---\n")
    
    fun getResults(): Map<String, String> = results.toMap()
    
    fun getProcessedCount(): Int = processedCommands.size
    
    override fun toString(): String {
        return "BatchProcessTelegramCommand(messages=${messages.size}, processed=${processedCommands.size})"
    }
}

/**
 * Command for configuring Telegram processing settings
 */
class ConfigureTelegramProcessingCommand(
    private val processor: TelegramMessageProcessingTemplate,
    private val newSettings: TelegramProcessingSettings
) : TelegramCommand {
    
    private var previousSettings: TelegramProcessingSettings? = null
    private var wasExecuted = false
    
    override fun execute(): Boolean {
        return try {
            if (wasExecuted) {
                Log.w("ConfigureTelegramProcessingCommand", "Configuration already applied")
                return false
            }
            
            // In a real implementation, you would get current settings from processor
            previousSettings = getCurrentSettings(processor)
            
            // Apply new settings
            applySettings(processor, newSettings)
            wasExecuted = true
            
            Log.d("ConfigureTelegramProcessingCommand", "Applied new Telegram processing settings")
            true
        } catch (e: Exception) {
            Log.e("ConfigureTelegramProcessingCommand", "Failed to configure Telegram processing", e)
            false
        }
    }
    
    override fun undo(): Boolean {
        return try {
            if (!wasExecuted || previousSettings == null) {
                Log.w("ConfigureTelegramProcessingCommand", "Cannot undo: no previous settings")
                return false
            }
            
            applySettings(processor, previousSettings!!)
            wasExecuted = false
            
            Log.d("ConfigureTelegramProcessingCommand", "Restored previous Telegram processing settings")
            true
        } catch (e: Exception) {
            Log.e("ConfigureTelegramProcessingCommand", "Failed to restore previous settings", e)
            false
        }
    }
    
    override fun getMessage(): TelegramMessage? = null
    
    override fun getResult(): String? = "Settings applied: $newSettings"
    
    private fun getCurrentSettings(processor: TelegramMessageProcessingTemplate): TelegramProcessingSettings {
        // In a real implementation, extract current settings from processor
        return TelegramProcessingSettings()
    }
    
    private fun applySettings(processor: TelegramMessageProcessingTemplate, settings: TelegramProcessingSettings) {
        // In a real implementation, apply settings to processor
        Log.d("ConfigureTelegramProcessingCommand", "Applying settings: $settings")
    }
    
    override fun toString(): String {
        return "ConfigureTelegramProcessingCommand(executed=$wasExecuted)"
    }
}

/**
 * Settings class for Telegram processing configuration
 */
data class TelegramProcessingSettings(
    val maxMessageLength: Int = 5000,
    val includeMetadata: Boolean = true,
    val emailSubjectTemplate: String = "Telegram Message Summary",
    val retryAttempts: Int = 3,
    val timeoutSeconds: Int = 30
)

/**
 * Specialized command invoker for Telegram operations
 */
class TelegramCommandInvoker : CommandInvoker() {
    
    private val telegramHistory = mutableListOf<TelegramCommand>()
    
    /**
     * Execute a Telegram-specific command and track it separately
     */
    fun executeTelegramCommand(command: TelegramCommand): Boolean {
        val success = execute(command)
        if (success) {
            telegramHistory.add(command)
            
            // Keep only last 50 Telegram commands to prevent memory issues
            if (telegramHistory.size > 50) {
                telegramHistory.removeAt(0)
            }
        }
        return success
    }
    
    /**
     * Get the last Telegram command result
     */
    fun getLastTelegramResult(): String? {
        return telegramHistory.lastOrNull()?.getResult()
    }
    
    /**
     * Get all Telegram processing results
     */
    fun getAllTelegramResults(): List<String> {
        return telegramHistory.mapNotNull { it.getResult() }
    }
    
    /**
     * Clear Telegram command history
     */
    fun clearTelegramHistory() {
        telegramHistory.clear()
    }
    
    /**
     * Get Telegram processing statistics
     */
    fun getTelegramStats(): TelegramStats {
        val batchCommands = telegramHistory.count { it is BatchProcessTelegramCommand }
        val consolidatedCommands = telegramHistory.count { it is ConsolidatedTelegramProcessCommand }
        val singleCommands = telegramHistory.count { it is ProcessTelegramMessageCommand }
        
        return TelegramStats(
            totalCommands = telegramHistory.size,
            successfulCommands = telegramHistory.count { it.getResult() != null },
            batchCommands = batchCommands,
            singleCommands = singleCommands,
            consolidatedCommands = consolidatedCommands
        )
    }
}

/**
 * Statistics for Telegram command processing
 */
data class TelegramStats(
    val totalCommands: Int,
    val successfulCommands: Int,
    val batchCommands: Int,
    val singleCommands: Int,
    val consolidatedCommands: Int = 0
) {
    val successRate: Double
        get() = if (totalCommands > 0) successfulCommands.toDouble() / totalCommands else 0.0
}
