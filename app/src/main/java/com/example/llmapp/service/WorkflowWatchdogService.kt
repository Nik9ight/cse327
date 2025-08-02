package com.example.llmapp.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import com.example.llmapp.utils.ServiceManager

class WorkflowWatchdogService : Service() {
    
    private val TAG = "WorkflowWatchdogService"
    private val handler = Handler(Looper.getMainLooper())
    private var checkRunnable: Runnable? = null
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Watchdog service created")
        startWatchdog()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Watchdog service started")
        startWatchdog()
        return START_STICKY
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Watchdog service destroyed")
        stopWatchdog()
        
        // Don't restart service in onDestroy to avoid BackgroundServiceStartNotAllowedException
        // The receivers will handle restarts when appropriate
    }
    
    private fun startWatchdog() {
        stopWatchdog() // Stop any existing watchdog
        
        checkRunnable = object : Runnable {
            override fun run() {
                try {
                    // Check if main service is running
                    if (!ServiceManager.isServiceRunning(this@WorkflowWatchdogService)) {
                        Log.w(TAG, "Main service not running - restarting...")
                        ServiceManager.startBackgroundService(this@WorkflowWatchdogService)
                    } else {
                        Log.d(TAG, "Main service is running - watchdog check passed")
                    }
                    
                    // Schedule next check
                    handler.postDelayed(this, 60000) // Check every minute
                } catch (e: Exception) {
                    Log.e(TAG, "Error in watchdog check", e)
                    // Try again in 30 seconds on error
                    handler.postDelayed(this, 30000)
                }
            }
        }
        
        // Start checking after 30 seconds
        handler.postDelayed(checkRunnable!!, 30000)
        Log.d(TAG, "Watchdog started - will check every minute")
    }
    
    private fun stopWatchdog() {
        checkRunnable?.let { runnable ->
            handler.removeCallbacks(runnable)
            checkRunnable = null
        }
    }
    
    companion object {
        fun start(context: Context) {
            try {
                val intent = Intent(context, WorkflowWatchdogService::class.java)
                context.startService(intent)
                Log.d("WorkflowWatchdogService", "Watchdog service start requested")
            } catch (e: Exception) {
                Log.e("WorkflowWatchdogService", "Failed to start watchdog service", e)
            }
        }
        
        fun stop(context: Context) {
            try {
                val intent = Intent(context, WorkflowWatchdogService::class.java)
                context.stopService(intent)
            } catch (e: Exception) {
                Log.e("WorkflowWatchdogService", "Failed to stop watchdog service", e)
            }
        }
    }
}
