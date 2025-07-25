package com.example.llmapp.workflow

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Data classes for workflow management system
 * Using data classes for immutability and easy serialization
 */

/**
 * Enumeration of supported workflow types
 */
enum class WorkflowType {
    GMAIL_TO_TELEGRAM,
    TELEGRAM_TO_GMAIL;
    
    fun getDisplayName(): String {
        return when (this) {
            GMAIL_TO_TELEGRAM -> "Gmail to Telegram"
            TELEGRAM_TO_GMAIL -> "Telegram to Gmail"
        }
    }
    
    fun getDescription(): String {
        return when (this) {
            GMAIL_TO_TELEGRAM -> "Process emails from Gmail and send summaries to Telegram"
            TELEGRAM_TO_GMAIL -> "Process messages from Telegram and send summaries via Gmail"
        }
    }
}

/**
 * Workflow configuration using Strategy Pattern
 * Different configurations for different workflow types
 */
@Parcelize
sealed class WorkflowConfiguration : Parcelable {
    abstract fun validate(): ValidationResult
}

/**
 * Configuration for Gmail to Telegram workflows
 */
@Parcelize
data class GmailToTelegramConfig(
    val gmailSearchQuery: String = "",
    val telegramBotToken: String = "",
    val telegramChatId: String = "",
    val llmPrompt: String = "",
    val emailLimit: Int = 5,
    val autoProcessInterval: Long = 0 // 0 means manual only
) : WorkflowConfiguration() {
    
    override fun validate(): ValidationResult {
        val errors = mutableListOf<String>()
        
        if (telegramBotToken.isBlank()) {
            errors.add("Telegram bot token is required")
        }
        
        if (telegramChatId.isBlank()) {
            errors.add("Telegram chat ID is required")
        }
        
        if (emailLimit <= 0) {
            errors.add("Email limit must be positive")
        }
        
        return if (errors.isEmpty()) {
            ValidationResult.Success
        } else {
            ValidationResult.Failure(errors)
        }
    }
}

/**
 * Configuration for Telegram to Gmail workflows
 */
@Parcelize
data class TelegramToGmailConfig(
    val telegramBotToken: String = "",
    val telegramChatId: String = "",
    val gmailRecipients: List<String> = emptyList(),
    val gmailSender: String = "",
    val llmPrompt: String = "",
    val messageLimit: Int = 30,
    val emailTemplate: String = "STANDARD" // STANDARD, COMPACT, DETAILED
) : WorkflowConfiguration() {
    
    override fun validate(): ValidationResult {
        val errors = mutableListOf<String>()
        
        if (telegramBotToken.isBlank()) {
            errors.add("Telegram bot token is required")
        }
        
        if (telegramChatId.isBlank()) {
            errors.add("Telegram chat ID is required")
        }
        
        if (gmailRecipients.isEmpty()) {
            errors.add("At least one Gmail recipient is required")
        }
        
        if (gmailSender.isBlank()) {
            errors.add("Gmail sender is required")
        }
        
        if (messageLimit <= 0) {
            errors.add("Message limit must be positive")
        }
        
        return if (errors.isEmpty()) {
            ValidationResult.Success
        } else {
            ValidationResult.Failure(errors)
        }
    }
}

/**
 * Saved workflow entity
 */
@Parcelize
data class SavedWorkflow(
    val id: String,
    val name: String,
    val type: WorkflowType,
    val configuration: WorkflowConfiguration,
    val createdAt: Long,
    val lastModified: Long,
    val isEnabled: Boolean = true,
    val description: String = ""
) : Parcelable {
    
    fun getFormattedCreatedDate(): String {
        return java.text.SimpleDateFormat("MMM dd, yyyy HH:mm", java.util.Locale.getDefault())
            .format(java.util.Date(createdAt))
    }
    
    fun getFormattedLastModified(): String {
        return java.text.SimpleDateFormat("MMM dd, yyyy HH:mm", java.util.Locale.getDefault())
            .format(java.util.Date(lastModified))
    }
}

/**
 * Workflow execution result
 */
data class WorkflowExecutionResult(
    val success: Boolean,
    val message: String,
    val executionTime: Long,
    val details: Map<String, Any> = emptyMap(),
    val timestamp: Long = System.currentTimeMillis()
) {
    
    fun getFormattedExecutionTime(): String {
        return if (executionTime < 1000) {
            "${executionTime}ms"
        } else {
            "${executionTime / 1000}s"
        }
    }
    
    fun getFormattedTimestamp(): String {
        return java.text.SimpleDateFormat("MMM dd, yyyy HH:mm:ss", java.util.Locale.getDefault())
            .format(java.util.Date(timestamp))
    }
}

/**
 * Validation result using sealed classes
 */
sealed class ValidationResult {
    object Success : ValidationResult()
    data class Failure(val errors: List<String>) : ValidationResult()
    
    fun isValid(): Boolean = this is Success
    
    fun getErrorMessage(): String {
        return when (this) {
            is Success -> ""
            is Failure -> errors.joinToString("\n• ", "• ")
        }
    }
}

/**
 * Workflow execution status
 */
enum class WorkflowStatus {
    IDLE,
    RUNNING,
    COMPLETED,
    FAILED,
    STOPPED
}

/**
 * Workflow template for quick creation
 */
data class WorkflowTemplate(
    val name: String,
    val type: WorkflowType,
    val description: String,
    val defaultConfiguration: WorkflowConfiguration
) {
    companion object {
        fun getDefaultTemplates(): List<WorkflowTemplate> {
            return listOf(
                WorkflowTemplate(
                    name = "Email Notifications",
                    type = WorkflowType.GMAIL_TO_TELEGRAM,
                    description = "Send important email summaries to Telegram",
                    defaultConfiguration = GmailToTelegramConfig(
                        gmailSearchQuery = "is:unread",
                        llmPrompt = "Summarize this email briefly and highlight important points",
                        emailLimit = 3
                    )
                ),
                WorkflowTemplate(
                    name = "Chat Summary",
                    type = WorkflowType.TELEGRAM_TO_GMAIL,
                    description = "Send Telegram conversation summaries via email",
                    defaultConfiguration = TelegramToGmailConfig(
                        llmPrompt = "Summarize this conversation and highlight key decisions or action items",
                        messageLimit = 20,
                        emailTemplate = "STANDARD"
                    )
                ),
                WorkflowTemplate(
                    name = "Daily Digest",
                    type = WorkflowType.GMAIL_TO_TELEGRAM,
                    description = "Daily summary of important emails",
                    defaultConfiguration = GmailToTelegramConfig(
                        gmailSearchQuery = "newer_than:1d",
                        llmPrompt = "Create a daily digest of these emails with key highlights",
                        emailLimit = 10,
                        autoProcessInterval = 24 * 60 * 60 * 1000 // 24 hours
                    )
                )
            )
        }
    }
}
