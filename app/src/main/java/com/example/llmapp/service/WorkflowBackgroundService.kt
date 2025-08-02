package com.example.llmapp.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentValues.TAG
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.llmapp.R
import com.example.llmapp.HomeActivity
import kotlinx.coroutines.*

class WorkflowBackgroundService : Service() {
    
    private val TAG = "WorkflowBackgroundService"
    private val NOTIFICATION_ID = 1001
    private val CHANNEL_ID = "workflow_background_channel"
    
    private var wakeLock: PowerManager.WakeLock? = null
    private var serviceJob = SupervisorJob()
    private var serviceScope = CoroutineScope(Dispatchers.Default + serviceJob)
    
    // Enhanced workflow processor with retry logic
    private lateinit var workflowProcessor: WorkflowProcessor
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Background service created")
        createNotificationChannel()
        acquireWakeLock()
        
        // Initialize the enhanced workflow processor
        workflowProcessor = WorkflowProcessor(this)
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Background service started")
        
        // Check if this is a stop request
        if (intent?.action == "STOP_SERVICE") {
            Log.d(TAG, "Received stop request, shutting down...")
            stopSelf()
            return START_NOT_STICKY
        }
        
        try {
            val notification = createForegroundNotification()
            startForeground(NOTIFICATION_ID, notification)
            
            // Start workflow monitoring
            startWorkflowMonitoring()
            
            Log.d(TAG, "Foreground service started successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start foreground service", e)
            // If we can't start as foreground, stop the service
            stopSelf()
            return START_NOT_STICKY
        }
        
        // Return START_STICKY to restart service if killed
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Background service destroyed - cancelling all operations")
        
        try {
            // Cancel all coroutines immediately
            serviceJob.cancel()
            
            // Wait briefly for cancellation to complete
            Thread.sleep(500)
            
            // Release wake lock
            releaseWakeLock()
            
            Log.d(TAG, "Background service cleanup completed")
        } catch (e: Exception) {
            Log.e(TAG, "Error during service cleanup", e)
        }
        
        // Don't restart service in onDestroy to avoid ForegroundServiceStartNotAllowedException
        // The BootCompletedReceiver and KeepAliveReceiver will handle restarts when appropriate
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Workflow Background Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps workflows running in the background"
                setShowBadge(false)
                enableLights(false)
                enableVibration(false)
                // Make notification harder to dismiss
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setBypassDnd(true)
            }
            
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun createForegroundNotification(): Notification {
        val intent = Intent(this, HomeActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("LLM App - Workflows Active")
            .setContentText("Monitoring workflows in background - DO NOT DISMISS")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setAutoCancel(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            // Make it stick around
            .setDeleteIntent(createRestartIntent())
            .build()
    }
    
    private fun createRestartIntent(): PendingIntent {
        val restartIntent = Intent(this, WorkflowBackgroundService::class.java)
        return PendingIntent.getService(
            this, 0, restartIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
    
    private fun acquireWakeLock() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "LLMApp:WorkflowWakeLock"
            ).apply {
                // Keep wake lock for much longer and auto-renew
                acquire(60 * 60 * 1000L) // 1 hour, will be renewed
            }
            Log.d(TAG, "Wake lock acquired for 1 hour")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to acquire wake lock", e)
        }
    }
    
    private fun releaseWakeLock() {
        try {
            wakeLock?.let { wl ->
                if (wl.isHeld) {
                    wl.release()
                    Log.d(TAG, "Wake lock released")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to release wake lock", e)
        }
    }
    
    private fun startWorkflowMonitoring() {
        serviceScope.launch {
            Log.d(TAG, "Starting enhanced workflow monitoring with robust processing")
            
            try {
                // Start the continuous processing loop from the reference
                // This implements the Gmail -> LLM -> Telegram/Gmail pipeline
                workflowProcessor.startContinuousProcessing()
            } catch (e: Exception) {
                Log.e(TAG, "Critical error in workflow processing", e)
                
                // Try to restart after a delay
                delay(60000) // Wait 1 minute
                Log.d(TAG, "Attempting to restart workflow processing")
                startWorkflowMonitoring() // Recursive restart
            }
        }
        
        // Keep the wake lock renewal in a separate coroutine
        serviceScope.launch {
            var cycleCount = 0
            while (isActive) {
                try {
                    cycleCount++
                    if (cycleCount % 20 == 0) { // Every 10 minutes (20 * 30 seconds)
                        renewWakeLock()
                        updateNotification(cycleCount)
                    }
                    
                    delay(30000) // Check every 30 seconds
                } catch (e: Exception) {
                    Log.e(TAG, "Error in wake lock maintenance", e)
                    delay(60000)
                }
            }
        }
    }
    
    private fun renewWakeLock() {
        try {
            wakeLock?.let { wl ->
                if (wl.isHeld) {
                    wl.acquire(60 * 60 * 1000L) // Renew for another hour
                    Log.d(TAG, "Wake lock renewed")
                } else {
                    acquireWakeLock() // Re-acquire if lost
                }
            } ?: run {
                acquireWakeLock() // Acquire if null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to renew wake lock", e)
        }
    }
    
    private fun updateNotification(cycleCount: Int) {
        try {
            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("LLM App - Workflows Active")
                .setContentText("Running cycle #$cycleCount - DO NOT DISMISS")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setOngoing(true)
                .setAutoCancel(false)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .build()
            
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update notification", e)
        }
    }
    
    private suspend fun checkAndExecuteWorkflows() {
        try {
            // This would integrate with your workflow system
            Log.d(TAG, "Checking for workflows to execute...")
            
            // TODO: Add actual workflow execution logic here
            // You can integrate with your NewWorkflowStorage and WorkflowRunner
            
        } catch (e: Exception) {
            Log.e(TAG, "Error checking workflows", e)
        }
    }
    
    companion object {
        fun start(context: Context) {
            try {
                val intent = Intent(context, WorkflowBackgroundService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    // Check if we can start foreground service
                    try {
                        context.startForegroundService(intent)
                    } catch (e: Exception) {
                        Log.w(TAG, "Cannot start foreground service, trying regular service", e)
                        // Fallback to regular service if foreground service is not allowed
                        context.startService(intent)
                    }
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start background service", e)
            }
        }
        
        fun stop(context: Context) {
            try {
                Log.d(TAG, "Sending stop request to background service")
                val intent = Intent(context, WorkflowBackgroundService::class.java)
                intent.action = "STOP_SERVICE"
                
                // Try to send stop command first
                context.startService(intent)
                
                // Also try direct stop
                Thread.sleep(1000) // Wait 1 second for graceful shutdown
                context.stopService(intent)
                
                Log.d(TAG, "Stop request sent to background service")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to stop background service", e)
            }
        }
    }
}
