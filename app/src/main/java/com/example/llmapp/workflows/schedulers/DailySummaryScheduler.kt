package com.example.llmapp.workflows.schedulers

import android.content.Context
import android.util.Log
import com.example.llmapp.workflows.services.ImageWorkflowConfig
import com.example.llmapp.workflows.services.WorkflowType
import com.example.llmapp.workflows.services.EnhancedImageProcessingService
import com.example.llmapp.workflows.services.WorkflowConfigManager
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * Scheduler for daily summary reports
 * Follows Single Responsibility Principle - handles scheduling only
 */
class DailySummaryScheduler(
    private val context: Context,
    private val imageProcessingService: EnhancedImageProcessingService,
    private val workflowConfigManager: WorkflowConfigManager
) {
    companion object {
        private const val TAG = "DailySummaryScheduler"
        private const val CHECK_INTERVAL_MS = 60000L // Check every minute
    }
    
    private var schedulerJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    /**
     * Start the daily summary scheduler
     */
    fun startScheduler() {
        if (schedulerJob?.isActive == true) {
            Log.d(TAG, "Scheduler already running")
            return
        }
        
        Log.d(TAG, "Starting daily summary scheduler")
        
        schedulerJob = scope.launch {
            while (isActive) {
                try {
                    checkAndSendDailySummaries()
                    delay(CHECK_INTERVAL_MS)
                } catch (e: Exception) {
                    Log.e(TAG, "Error in scheduler loop", e)
                    // Continue running despite errors
                    delay(CHECK_INTERVAL_MS)
                }
            }
        }
        
        Log.d(TAG, "Daily summary scheduler started")
    }
    
    /**
     * Stop the scheduler
     */
    fun stopScheduler() {
        Log.d(TAG, "Stopping daily summary scheduler")
        schedulerJob?.cancel()
        schedulerJob = null
        Log.d(TAG, "Daily summary scheduler stopped")
    }
    
    /**
     * Check if it's time to send daily summaries and send them
     */
    private suspend fun checkAndSendDailySummaries() {
        try {
            val currentDateTime = getCurrentDateTime()
            val configs = workflowConfigManager.getWorkflowsByType(WorkflowType.DOCUMENT_ANALYSIS)
            
            if (configs.isEmpty()) {
                return
            }
            
            Log.d(TAG, "Checking ${configs.size} configs for daily summary at ${currentDateTime.first} ${currentDateTime.second}")
            
            configs.forEach { config ->
                if (shouldSendSummaryNow(config, currentDateTime)) {
                    Log.d(TAG, "Sending daily summary for workflow: ${config.workflowName}")
                    
                    try {
                        imageProcessingService.generateDailySummaryForWorkflow(config)
                        
                        // Update the scheduled date to next day after successful sending
                        updateScheduledDateToNextDay(config.workflowId)
                        
                        Log.d(TAG, "Daily summary sent successfully and scheduled date updated for workflow: ${config.workflowName}")
                        
                    } catch (e: Exception) {
                        Log.e(TAG, "Error sending daily summary for workflow: ${config.workflowName}", e)
                    }
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error checking daily summaries", e)
        }
    }
    
    /**
     * Check if we should send summary now based on scheduled date and time from workflow config
     */
    private fun shouldSendSummaryNow(config: ImageWorkflowConfig, currentDateTime: Pair<String, String>): Boolean {
        try {
            val currentDate = currentDateTime.first
            val currentTime = currentDateTime.second
            
            // Get the scheduled date for this workflow (stored in SharedPreferences)
            val scheduledDate = getScheduledDate(config.workflowId)
            
            // If no scheduled date exists, initialize it to today (first run)
            if (scheduledDate == null) {
                setScheduledDate(config.workflowId, currentDate)
                Log.d(TAG, "Initialized scheduled date for workflow ${config.workflowId} to $currentDate")
                return false // Don't send on initialization
            }
            
            // Check if today matches the scheduled date
            if (currentDate != scheduledDate) {
                Log.d(TAG, "Not scheduled for today - current: $currentDate, scheduled: $scheduledDate")
                return false
            }
            
            // Check if we're within 1 minute of the scheduled time
            val scheduledTimeMinutes = parseTimeString(config.scheduledTime)
            val currentTimeMinutes = parseTimeString(currentTime)
            
            if (scheduledTimeMinutes == -1 || currentTimeMinutes == -1) {
                Log.w(TAG, "Invalid time format - scheduled: ${config.scheduledTime}, current: $currentTime")
                return false
            }
            
            val diffMinutes = Math.abs(scheduledTimeMinutes - currentTimeMinutes)
            val shouldSend = diffMinutes <= 1
            
            if (shouldSend) {
                Log.d(TAG, "Should send summary: scheduledDate=$scheduledDate, scheduledTime=${config.scheduledTime}, currentDate=$currentDate, currentTime=$currentTime, diffMinutes=$diffMinutes")
            }
            
            return shouldSend
            
        } catch (e: Exception) {
            Log.e(TAG, "Error checking if should send summary for workflow ${config.workflowId}", e)
            return false
        }
    }
    
    /**
     * Parse time string (HH:MM) to minutes since midnight
     */
    private fun parseTimeString(timeStr: String): Int {
        val parts = timeStr.split(":")
        if (parts.size != 2) return -1
        
        val hours = parts[0].toIntOrNull() ?: return -1
        val minutes = parts[1].toIntOrNull() ?: return -1
        
        return hours * 60 + minutes
    }
    
    /**
     * Get current date and time as separate strings
     */
    private fun getCurrentDateTime(): Pair<String, String> {
        val calendar = Calendar.getInstance()
        val dateFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val timeFormatter = SimpleDateFormat("HH:mm", Locale.getDefault())
        val currentDate = dateFormatter.format(calendar.time)
        val currentTime = timeFormatter.format(calendar.time)
        return Pair(currentDate, currentTime)
    }
    
    /**
     * Update the scheduled date to the next day for a workflow
     */
    private fun updateScheduledDateToNextDay(workflowId: String) {
        try {
            val calendar = Calendar.getInstance()
            calendar.add(Calendar.DAY_OF_YEAR, 1) // Move to next day
            val nextDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(calendar.time)
            setScheduledDate(workflowId, nextDate)
            Log.d(TAG, "Updated scheduled date for workflow $workflowId to next day: $nextDate")
        } catch (e: Exception) {
            Log.e(TAG, "Error updating scheduled date for workflow $workflowId", e)
        }
    }
    
    /**
     * Get the scheduled date for a workflow
     */
    private fun getScheduledDate(workflowId: String): String? {
        return try {
            val prefs = context.getSharedPreferences("daily_summaries", Context.MODE_PRIVATE)
            val scheduledDate = prefs.getString("${workflowId}_scheduled_date", null)
            Log.d(TAG, "Scheduled date for $workflowId: $scheduledDate")
            scheduledDate
        } catch (e: Exception) {
            Log.e(TAG, "Error getting scheduled date", e)
            null
        }
    }
    
    /**
     * Set the scheduled date for a workflow
     */
    private fun setScheduledDate(workflowId: String, date: String) {
        try {
            val prefs = context.getSharedPreferences("daily_summaries", Context.MODE_PRIVATE)
            prefs.edit().putString("${workflowId}_scheduled_date", date).apply()
            Log.d(TAG, "Set scheduled date for $workflowId to $date")
        } catch (e: Exception) {
            Log.e(TAG, "Error setting scheduled date", e)
        }
    }
    
    /**
     * Force send daily summary for all enabled workflows (for testing)
     */
    
    /**
     * Force send daily summary for all enabled workflows (for testing)
     */
    suspend fun forceSendAllDailySummaries() {
        Log.d(TAG, "Force sending all daily summaries")
        
        try {
            val configs = workflowConfigManager.getWorkflowsByType(WorkflowType.DOCUMENT_ANALYSIS)
            
            configs.forEach { config ->
                try {
                    Log.d(TAG, "Force sending daily summary for: ${config.workflowName}")
                    imageProcessingService.generateDailySummaryForWorkflow(config)
                    
                    // Update the scheduled date to next day after successful sending
                    updateScheduledDateToNextDay(config.workflowId)
                    
                    Log.d(TAG, "Force send completed and scheduled date updated for: ${config.workflowName}")
                } catch (e: Exception) {
                    Log.e(TAG, "Error force sending summary for: ${config.workflowName}", e)
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error force sending daily summaries", e)
        }
    }
}
