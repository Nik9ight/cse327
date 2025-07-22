package com.example.llmapp.patterns

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log

/**
 * Broadcast receiver for email processing events
 * Implements the Observer pattern using Android's BroadcastReceiver
 */
class EmailProcessingReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_EMAIL_FETCHED = "com.example.llmapp.EMAIL_FETCHED"
        const val ACTION_PROCESSING_STARTED = "com.example.llmapp.PROCESSING_STARTED"
        const val ACTION_PROCESSING_COMPLETE = "com.example.llmapp.PROCESSING_COMPLETE"
        const val ACTION_EMAIL_SENT = "com.example.llmapp.EMAIL_SENT"
        const val ACTION_BATCH_COMPLETE = "com.example.llmapp.BATCH_COMPLETE"
        
        const val EXTRA_EMAIL_ID = "email_id"
        const val EXTRA_SUBJECT = "subject"
        const val EXTRA_SUMMARY = "summary"
        const val EXTRA_SUCCESS = "success"
        const val EXTRA_MESSAGE = "message"
        const val EXTRA_SUCCESS_COUNT = "success_count"
        const val EXTRA_FAILURE_COUNT = "failure_count"
        
        /**
         * Create intent filter for all email processing actions
         */
        fun createIntentFilter(): IntentFilter {
            val filter = IntentFilter()
            filter.addAction(ACTION_EMAIL_FETCHED)
            filter.addAction(ACTION_PROCESSING_STARTED)
            filter.addAction(ACTION_PROCESSING_COMPLETE)
            filter.addAction(ACTION_EMAIL_SENT)
            filter.addAction(ACTION_BATCH_COMPLETE)
            return filter
        }
    }
    
    // Listeners for different events
    private var onEmailFetchedListener: ((String, String) -> Unit)? = null
    private var onProcessingStartedListener: ((String, String) -> Unit)? = null
    private var onProcessingCompleteListener: ((String, String, String) -> Unit)? = null
    private var onEmailSentListener: ((String, String, Boolean, String) -> Unit)? = null
    private var onBatchCompleteListener: ((Int, Int) -> Unit)? = null
    
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_EMAIL_FETCHED -> {
                val emailId = intent.getStringExtra(EXTRA_EMAIL_ID) ?: ""
                val subject = intent.getStringExtra(EXTRA_SUBJECT) ?: ""
                Log.d("EmailReceiver", "Email fetched: $subject")
                onEmailFetchedListener?.invoke(emailId, subject)
            }
            ACTION_PROCESSING_STARTED -> {
                val emailId = intent.getStringExtra(EXTRA_EMAIL_ID) ?: ""
                val subject = intent.getStringExtra(EXTRA_SUBJECT) ?: ""
                Log.d("EmailReceiver", "Processing started: $subject")
                onProcessingStartedListener?.invoke(emailId, subject)
            }
            ACTION_PROCESSING_COMPLETE -> {
                val emailId = intent.getStringExtra(EXTRA_EMAIL_ID) ?: ""
                val subject = intent.getStringExtra(EXTRA_SUBJECT) ?: ""
                val summary = intent.getStringExtra(EXTRA_SUMMARY) ?: ""
                Log.d("EmailReceiver", "Processing complete: $subject")
                onProcessingCompleteListener?.invoke(emailId, subject, summary)
            }
            ACTION_EMAIL_SENT -> {
                val emailId = intent.getStringExtra(EXTRA_EMAIL_ID) ?: ""
                val subject = intent.getStringExtra(EXTRA_SUBJECT) ?: ""
                val success = intent.getBooleanExtra(EXTRA_SUCCESS, false)
                val message = intent.getStringExtra(EXTRA_MESSAGE) ?: ""
                Log.d("EmailReceiver", "Email sent: $subject, success=$success")
                onEmailSentListener?.invoke(emailId, subject, success, message)
            }
            ACTION_BATCH_COMPLETE -> {
                val successCount = intent.getIntExtra(EXTRA_SUCCESS_COUNT, 0)
                val failureCount = intent.getIntExtra(EXTRA_FAILURE_COUNT, 0)
                Log.d("EmailReceiver", "Batch complete: success=$successCount, failures=$failureCount")
                onBatchCompleteListener?.invoke(successCount, failureCount)
            }
        }
    }
    
    // Listener setters
    fun setOnEmailFetchedListener(listener: (String, String) -> Unit) {
        onEmailFetchedListener = listener
    }
    
    fun setOnProcessingStartedListener(listener: (String, String) -> Unit) {
        onProcessingStartedListener = listener
    }
    
    fun setOnProcessingCompleteListener(listener: (String, String, String) -> Unit) {
        onProcessingCompleteListener = listener
    }
    
    fun setOnEmailSentListener(listener: (String, String, Boolean, String) -> Unit) {
        onEmailSentListener = listener
    }
    
    fun setOnBatchCompleteListener(listener: (Int, Int) -> Unit) {
        onBatchCompleteListener = listener
    }
}
