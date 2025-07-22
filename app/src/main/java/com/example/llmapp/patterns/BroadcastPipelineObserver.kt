package com.example.llmapp.patterns

import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Bridge between PipelineObserver and BroadcastReceiver
 * Implements PipelineObserver and broadcasts events using Android's Intent system
 */
class BroadcastPipelineObserver(private val context: Context) : PipelineObserver {
    
    override fun onEmailFetched(emailId: String, subject: String) {
        val intent = Intent(EmailProcessingReceiver.ACTION_EMAIL_FETCHED).apply {
            putExtra(EmailProcessingReceiver.EXTRA_EMAIL_ID, emailId)
            putExtra(EmailProcessingReceiver.EXTRA_SUBJECT, subject)
        }
        context.sendBroadcast(intent)
        Log.d("BroadcastObserver", "Email fetched broadcast sent: $subject")
    }
    
    override fun onProcessingStarted(emailId: String, subject: String) {
        val intent = Intent(EmailProcessingReceiver.ACTION_PROCESSING_STARTED).apply {
            putExtra(EmailProcessingReceiver.EXTRA_EMAIL_ID, emailId)
            putExtra(EmailProcessingReceiver.EXTRA_SUBJECT, subject)
        }
        context.sendBroadcast(intent)
        Log.d("BroadcastObserver", "Processing started broadcast sent: $subject")
    }
    
    override fun onProcessingComplete(emailId: String, subject: String, summary: String) {
        val intent = Intent(EmailProcessingReceiver.ACTION_PROCESSING_COMPLETE).apply {
            putExtra(EmailProcessingReceiver.EXTRA_EMAIL_ID, emailId)
            putExtra(EmailProcessingReceiver.EXTRA_SUBJECT, subject)
            putExtra(EmailProcessingReceiver.EXTRA_SUMMARY, summary)
        }
        context.sendBroadcast(intent)
        Log.d("BroadcastObserver", "Processing complete broadcast sent: $subject")
    }
    
    override fun onEmailSent(emailId: String, subject: String, success: Boolean, message: String) {
        val intent = Intent(EmailProcessingReceiver.ACTION_EMAIL_SENT).apply {
            putExtra(EmailProcessingReceiver.EXTRA_EMAIL_ID, emailId)
            putExtra(EmailProcessingReceiver.EXTRA_SUBJECT, subject)
            putExtra(EmailProcessingReceiver.EXTRA_SUCCESS, success)
            putExtra(EmailProcessingReceiver.EXTRA_MESSAGE, message)
        }
        context.sendBroadcast(intent)
        Log.d("BroadcastObserver", "Email sent broadcast sent: $subject, success=$success")
    }
    
    override fun onBatchComplete(successCount: Int, failureCount: Int) {
        val intent = Intent(EmailProcessingReceiver.ACTION_BATCH_COMPLETE).apply {
            putExtra(EmailProcessingReceiver.EXTRA_SUCCESS_COUNT, successCount)
            putExtra(EmailProcessingReceiver.EXTRA_FAILURE_COUNT, failureCount)
        }
        context.sendBroadcast(intent)
        Log.d("BroadcastObserver", "Batch complete broadcast sent: success=$successCount, failures=$failureCount")
    }
}
