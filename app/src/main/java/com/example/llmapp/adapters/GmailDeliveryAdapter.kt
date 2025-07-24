package com.example.llmapp.adapters

import android.util.Log
import com.example.llmapp.GmailService
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * Adapter for Gmail delivery functionality
 * Implements the Adapter pattern to provide a consistent interface for email delivery
 */
class GmailDeliveryAdapter(
    private val gmailService: GmailService,
    private val defaultSender: String
) {
    
    companion object {
        private val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    }
    
    /**
     * Send a single email through Gmail
     */
    suspend fun sendEmail(
        recipients: List<String>,
        subject: String,
        content: String,
        isHtml: Boolean = true,
        attachments: List<EmailAttachment> = emptyList()
    ): EmailDeliveryResult {
        return try {
            Log.d("GmailDeliveryAdapter", "Sending email to: ${recipients.joinToString(", ")}")
            
            val emailRequest = GmailEmailRequest(
                to = recipients,
                subject = subject,
                body = content,
                isHtml = isHtml,
                from = defaultSender,
                attachments = attachments
            )
            
            // In a real implementation, this would call the actual Gmail service
            val success = sendEmailThroughGmail(emailRequest)
            
            if (success) {
                EmailDeliveryResult.Success(
                    messageId = generateMessageId(),
                    timestamp = DATE_FORMAT.format(Date()),
                    recipients = recipients
                )
            } else {
                EmailDeliveryResult.Failure("Failed to send email through Gmail service")
            }
            
        } catch (e: Exception) {
            Log.e("GmailDeliveryAdapter", "Failed to send email", e)
            EmailDeliveryResult.Failure("Exception occurred: ${e.message}")
        }
    }
    
    /**
     * Send batch emails with rate limiting
     */
    suspend fun sendBatchEmails(
        emailRequests: List<BatchEmailRequest>,
        delayBetweenEmails: Long = 1000 // 1 second delay
    ): BatchEmailResult {
        val results = mutableListOf<EmailDeliveryResult>()
        var successCount = 0
        
        try {
            Log.d("GmailDeliveryAdapter", "Sending batch of ${emailRequests.size} emails")
            
            for ((index, request) in emailRequests.withIndex()) {
                val result = sendEmail(
                    recipients = request.recipients,
                    subject = request.subject,
                    content = request.content,
                    isHtml = request.isHtml,
                    attachments = request.attachments
                )
                
                results.add(result)
                
                if (result is EmailDeliveryResult.Success) {
                    successCount++
                }
                
                // Add delay between emails to respect rate limits
                if (index < emailRequests.size - 1 && delayBetweenEmails > 0) {
                    kotlinx.coroutines.delay(delayBetweenEmails)
                }
            }
            
            return BatchEmailResult(
                totalEmails = emailRequests.size,
                successfulEmails = successCount,
                failedEmails = emailRequests.size - successCount,
                results = results,
                timestamp = DATE_FORMAT.format(Date())
            )
            
        } catch (e: Exception) {
            Log.e("GmailDeliveryAdapter", "Failed to send batch emails", e)
            return BatchEmailResult(
                totalEmails = emailRequests.size,
                successfulEmails = successCount,
                failedEmails = emailRequests.size - successCount,
                results = results,
                timestamp = DATE_FORMAT.format(Date()),
                error = e.message
            )
        }
    }
    
    /**
     * Send Telegram-to-Gmail formatted email
     */
    suspend fun sendTelegramSummary(
        recipients: List<String>,
        telegramContent: TelegramEmailContent,
        templateType: EmailTemplateType = EmailTemplateType.STANDARD
    ): EmailDeliveryResult {
        return try {
            val formattedContent = formatTelegramEmail(telegramContent, templateType)
            val subject = generateTelegramEmailSubject(telegramContent)
            
            sendEmail(
                recipients = recipients,
                subject = subject,
                content = formattedContent,
                isHtml = true
            )
            
        } catch (e: Exception) {
            Log.e("GmailDeliveryAdapter", "Failed to send Telegram summary email", e)
            EmailDeliveryResult.Failure("Failed to format or send Telegram summary: ${e.message}")
        }
    }
    
    /**
     * Test Gmail connection and permissions
     */
    fun testConnection(): ConnectionTestResult {
        return try {
            Log.d("GmailDeliveryAdapter", "Testing Gmail connection")
            
            // Test the Gmail service connection
            val isConnected = testGmailServiceConnection()
            
            if (isConnected) {
                ConnectionTestResult.Success("Gmail connection successful")
            } else {
                ConnectionTestResult.Failure("Gmail connection failed")
            }
            
        } catch (e: Exception) {
            Log.e("GmailDeliveryAdapter", "Gmail connection test failed", e)
            ConnectionTestResult.Failure("Connection test error: ${e.message}")
        }
    }
    
    /**
     * Send a test email to verify the email sending functionality
     */
    suspend fun sendTestEmail(
        to: String,
        from: String? = null
    ): EmailDeliveryResult {
        return try {
            Log.d("GmailDeliveryAdapter", "Sending test email to: $to")
            
            val testSubject = "Test Email from TelegramToGmail Pipeline"
            val testContent = """
                <html>
                <body>
                    <h2>📧 Test Email</h2>
                    <p>This is a test email to verify that the Gmail email sending functionality is working correctly.</p>
                    <p><strong>Time:</strong> ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}</p>
                    <p><strong>From:</strong> TelegramToGmail Pipeline</p>
                    <hr>
                    <p><em>If you received this email, the pipeline is successfully configured to send emails through Gmail.</em></p>
                </body>
                </html>
            """.trimIndent()
            
            sendEmail(
                recipients = listOf(to),
                subject = testSubject,
                content = testContent,
                isHtml = true
            )
            
        } catch (e: Exception) {
            Log.e("GmailDeliveryAdapter", "Failed to send test email", e)
            EmailDeliveryResult.Failure("Failed to send test email: ${e.message}")
        }
    }
    
    // Private helper methods
    
    private suspend fun sendEmailThroughGmail(request: GmailEmailRequest): Boolean {
        return try {
            gmailService.sendEmail(
                to = request.to,
                subject = request.subject,
                body = request.body,
                isHtml = request.isHtml,
                from = request.from
            )
        } catch (e: Exception) {
            Log.e("GmailDeliveryAdapter", "Gmail service call failed", e)
            false
        }
    }
    
    private fun formatTelegramEmail(content: TelegramEmailContent, templateType: EmailTemplateType): String {
        return when (templateType) {
            EmailTemplateType.STANDARD -> formatStandardTemplate(content)
            EmailTemplateType.COMPACT -> formatCompactTemplate(content)
            EmailTemplateType.DETAILED -> formatDetailedTemplate(content)
        }
    }
    
    private fun formatStandardTemplate(content: TelegramEmailContent): String {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <title>Telegram Message Summary</title>
                <style>
                    body { font-family: Arial, sans-serif; margin: 20px; }
                    .header { background-color: #0088cc; color: white; padding: 15px; border-radius: 8px; }
                    .content { background-color: #f8f9fa; padding: 15px; margin: 10px 0; border-radius: 8px; }
                    .ai-analysis { background-color: #e3f2fd; padding: 15px; margin: 10px 0; border-radius: 8px; }
                    .metadata { background-color: #f3e5f5; padding: 10px; margin: 10px 0; border-radius: 8px; font-size: 0.9em; }
                    .footer { color: #666; font-size: 0.8em; margin-top: 20px; }
                </style>
            </head>
            <body>
                <div class="header">
                    <h2>📱 Telegram Message Summary</h2>
                    <p>Processed on ${content.timestamp}</p>
                </div>
                
                <div class="metadata">
                    <h3>Message Details</h3>
                    <p><strong>From:</strong> ${content.senderName}</p>
                    <p><strong>Chat:</strong> ${content.chatType}</p>
                    <p><strong>Time:</strong> ${content.originalTimestamp}</p>
                </div>
                
                ${content.processedContent?.let { """
                <div class="ai-analysis">
                    <h3>🤖 AI Analysis</h3>
                    <p>$it</p>
                </div>
                """ } ?: ""}
                
                <div class="content">
                    <h3>💬 Original Message</h3>
                    <p>${content.originalMessage}</p>
                </div>
                
                <div class="footer">
                    <p><em>This email was automatically generated from a Telegram message.</em></p>
                </div>
            </body>
            </html>
        """.trimIndent()
    }
    
    private fun formatCompactTemplate(content: TelegramEmailContent): String {
        return """
            <h3>📱 Telegram Message</h3>
            <p><strong>From:</strong> ${content.senderName} | <strong>Time:</strong> ${content.originalTimestamp}</p>
            
            ${content.processedContent?.let { "<p><strong>AI Summary:</strong> $it</p>" } ?: ""}
            
            <p><strong>Message:</strong> ${content.originalMessage}</p>
            
            <p><small><em>Auto-generated on ${content.timestamp}</em></small></p>
        """.trimIndent()
    }
    
    private fun formatDetailedTemplate(content: TelegramEmailContent): String {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <title>Detailed Telegram Analysis</title>
                <style>
                    body { font-family: 'Segoe UI', Arial, sans-serif; margin: 20px; line-height: 1.6; }
                    .container { max-width: 800px; margin: 0 auto; }
                    .header { background: linear-gradient(135deg, #0088cc, #00bcd4); color: white; padding: 20px; border-radius: 10px; text-align: center; }
                    .section { margin: 20px 0; padding: 20px; border-radius: 10px; border-left: 4px solid #0088cc; }
                    .metadata { background-color: #f8f9fa; }
                    .analysis { background-color: #e8f5e8; }
                    .original { background-color: #fff3cd; }
                    .timestamp { color: #666; font-size: 0.9em; }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <h1>📱 Detailed Telegram Message Analysis</h1>
                        <p class="timestamp">Generated on ${content.timestamp}</p>
                    </div>
                    
                    <div class="section metadata">
                        <h3>📋 Message Metadata</h3>
                        <ul>
                            <li><strong>Sender:</strong> ${content.senderName}</li>
                            <li><strong>Chat Type:</strong> ${content.chatType}</li>
                            <li><strong>Original Timestamp:</strong> ${content.originalTimestamp}</li>
                            <li><strong>Message Length:</strong> ${content.originalMessage.length} characters</li>
                        </ul>
                    </div>
                    
                    ${content.processedContent?.let { """
                    <div class="section analysis">
                        <h3>🤖 AI-Powered Analysis</h3>
                        <p>$it</p>
                    </div>
                    """ } ?: ""}
                    
                    <div class="section original">
                        <h3>💬 Original Message Content</h3>
                        <blockquote style="border-left: 3px solid #ccc; margin: 0; padding-left: 20px;">
                            ${content.originalMessage}
                        </blockquote>
                    </div>
                    
                    <div style="text-align: center; margin-top: 30px; color: #666; font-size: 0.9em;">
                        <p><em>This detailed analysis was automatically generated from a Telegram message using AI processing.</em></p>
                    </div>
                </div>
            </body>
            </html>
        """.trimIndent()
    }
    
    private fun generateTelegramEmailSubject(content: TelegramEmailContent): String {
        return if (!content.chatTitle.isNullOrBlank()) {
            "Telegram [${content.chatTitle}] - ${content.originalTimestamp}"
        } else {
            "Telegram - ${content.originalTimestamp}"
        }
    }
    
    private fun generateMessageId(): String {
        return "gmail_${System.currentTimeMillis()}_${(1000..9999).random()}"
    }
    
    private fun testGmailServiceConnection(): Boolean {
        // Test the Gmail service connection
        return try {
            gmailService.isSignedIn()
        } catch (e: Exception) {
            Log.e("GmailDeliveryAdapter", "Gmail service connection test failed", e)
            false
        }
    }
}

// Data classes for email operations

data class GmailEmailRequest(
    val to: List<String>,
    val subject: String,
    val body: String,
    val isHtml: Boolean = true,
    val from: String,
    val attachments: List<EmailAttachment> = emptyList()
)

data class BatchEmailRequest(
    val recipients: List<String>,
    val subject: String,
    val content: String,
    val isHtml: Boolean = true,
    val attachments: List<EmailAttachment> = emptyList()
)

data class EmailAttachment(
    val filename: String,
    val mimeType: String,
    val data: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as EmailAttachment
        if (filename != other.filename) return false
        if (mimeType != other.mimeType) return false
        if (!data.contentEquals(other.data)) return false
        return true
    }
    
    override fun hashCode(): Int {
        var result = filename.hashCode()
        result = 31 * result + mimeType.hashCode()
        result = 31 * result + data.contentHashCode()
        return result
    }
}

data class TelegramEmailContent(
    val senderName: String,
    val originalMessage: String,
    val processedContent: String? = null,
    val chatType: String,
    val originalTimestamp: String,
    val chatTitle: String? = null, // Chat title for group chats
    val timestamp: String = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
)

enum class EmailTemplateType {
    STANDARD,
    COMPACT,
    DETAILED
}

sealed class EmailDeliveryResult {
    data class Success(
        val messageId: String,
        val timestamp: String,
        val recipients: List<String>
    ) : EmailDeliveryResult()
    
    data class Failure(
        val error: String
    ) : EmailDeliveryResult()
}

data class BatchEmailResult(
    val totalEmails: Int,
    val successfulEmails: Int,
    val failedEmails: Int,
    val results: List<EmailDeliveryResult>,
    val timestamp: String,
    val error: String? = null
) {
    val successRate: Double
        get() = if (totalEmails > 0) successfulEmails.toDouble() / totalEmails else 0.0
}

sealed class ConnectionTestResult {
    data class Success(val message: String) : ConnectionTestResult()
    data class Failure(val error: String) : ConnectionTestResult()
}

/**
 * Factory for creating Gmail delivery adapters
 */
object GmailDeliveryAdapterFactory {
    
    fun create(gmailService: GmailService, defaultSender: String): GmailDeliveryAdapter {
        return GmailDeliveryAdapter(gmailService, defaultSender)
    }
    
    fun createWithValidation(gmailService: GmailService, defaultSender: String): GmailDeliveryAdapter? {
        return try {
            val adapter = GmailDeliveryAdapter(gmailService, defaultSender)
            
            // Test connection before returning
            when (adapter.testConnection()) {
                is ConnectionTestResult.Success -> adapter
                is ConnectionTestResult.Failure -> {
                    Log.e("GmailDeliveryAdapterFactory", "Gmail connection validation failed")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e("GmailDeliveryAdapterFactory", "Failed to create Gmail adapter", e)
            null
        }
    }
}
