package com.example.llmapp.new_implementation

import com.example.llmapp.new_implementation.interfaces.MessageDestination
import com.example.llmapp.new_implementation.interfaces.OutputFormatter
import com.example.llmapp.GmailService
import android.content.Context
import android.util.Log
import kotlinx.coroutines.runBlocking

/**
 * GmailDestination implementation using existing GmailService
 * Pure delivery mechanism - formatting handled by strategy
 */
class GmailDestination(private val config: Map<String, Any> = emptyMap()) : MessageDestination {
    
    private val context: Context? = config["context"] as? Context
    private val recipients: List<String> = config["recipients"] as? List<String> ?: emptyList()
    private val senderEmail: String = config["senderEmail"] as? String ?: ""
    private val formatter: OutputFormatter = config["formatter"] as? OutputFormatter 
        ?: throw IllegalArgumentException("OutputFormatter is required for GmailDestination")
    private val gmailService: GmailService? by lazy {
        context?.let { GmailService(it) }
    }
    
    constructor() : this(emptyMap())
    
    override fun sendMessage(message: Message): Boolean {
        return try {
            val emailRecipients = recipients.ifEmpty { listOf(message.recipient) }
            val subject = generateSubject(message)
            // Use the injected formatter strategy
            val htmlContent = formatter.formatOutput(message)
            
            runBlocking {
                gmailService?.sendEmail(
                    to = emailRecipients,
                    subject = subject,
                    body = htmlContent,
                    isHtml = true
                )
            }
            
            Log.d("GmailDestination", "Email sent successfully: ${message.id}")
            true
        } catch (e: Exception) {
            Log.e("GmailDestination", "Error sending email", e)
            false
        }
    }
    
    override fun sendMessages(messages: List<Message>): List<Boolean> {
        return if (messages.size == 1) {
            listOf(sendMessage(messages.first()))
        } else {
            // For batch messages, send as single consolidated email
            sendBatchEmail(messages)
        }
    }
    
    private fun sendBatchEmail(messages: List<Message>): List<Boolean> {
        return try {
            val emailRecipients = recipients.ifEmpty { 
                messages.map { it.recipient }.distinct()
            }
            val subject = generateBatchSubject(messages)
            // Use formatter for batch formatting
            val htmlContent = formatter.formatBatchOutput(messages)
            
            runBlocking {
                gmailService?.sendEmail(
                    to = emailRecipients,
                    subject = subject,
                    body = htmlContent,
                    isHtml = true
                )
            }
            
            Log.d("GmailDestination", "Batch email sent successfully for ${messages.size} messages")
            List(messages.size) { true }
        } catch (e: Exception) {
            Log.e("GmailDestination", "Error sending batch email", e)
            List(messages.size) { false }
        }
    }
    
    private fun generateSubject(message: Message): String {
        val platform = message.metadata["platform"] ?: "unknown"
        
        return when (platform) {
            "telegram" -> "Telegram Message Summary from ${message.sender}"
            "gmail" -> "Processed Email: ${message.metadata["subject"] ?: "No Subject"}"
            else -> "Automated Message from ${message.sender}"
        }
    }
    
    private fun generateBatchSubject(messages: List<Message>): String {
        val platform = messages.firstOrNull()?.metadata?.get("platform") ?: "unknown"
        val count = messages.size
        
        return when (platform) {
            "telegram" -> "Telegram Batch Summary - $count Messages"
            "gmail" -> "Gmail Batch Analysis - $count Emails"
            else -> "Batch Processing Report - $count Messages"
        }
    }
}