package com.example.llmapp.utils

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.llmapp.service.WorkflowBackgroundService
import com.example.llmapp.service.WorkflowWatchdogService

class ServiceManager {
    
    companion object {
        private const val TAG = "ServiceManager"
        
        /**
         * Check if the background service is currently running
         */
        fun isServiceRunning(context: Context): Boolean {
            return try {
                val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                val services = activityManager.getRunningServices(Integer.MAX_VALUE)
                
                services.any { service ->
                    service.service.className == WorkflowBackgroundService::class.java.name
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking service status", e)
                false
            }
        }
        
        /**
         * Start the background service with proper error handling
         */
        fun startBackgroundService(context: Context): Boolean {
            return try {
                if (!isServiceRunning(context)) {
                    Log.d(TAG, "Starting background service...")
                    WorkflowBackgroundService.start(context)
                    
                    // Also start watchdog service to monitor main service
                    WorkflowWatchdogService.start(context)
                    
                    true
                } else {
                    Log.d(TAG, "Background service already running")
                    
                    // Ensure watchdog is also running
                    WorkflowWatchdogService.start(context)
                    
                    true
                }
                
                // Also start image workflow service if needed
                startImageWorkflowServiceIfNeeded(context)
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start background service", e)
                false
            }
        }
        
        /**
         * Stop the background service
         */
        fun stopBackgroundService(context: Context): Boolean {
            return try {
                if (isServiceRunning(context)) {
                    Log.d(TAG, "Stopping background service...")
                    WorkflowBackgroundService.stop(context)
                    WorkflowWatchdogService.stop(context)
                    true
                } else {
                    Log.d(TAG, "Background service not running")
                    WorkflowWatchdogService.stop(context) // Stop watchdog anyway
                    true
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to stop background service", e)
                false
            }
        }
        
        /**
         * Get the current status of background execution
         */
        fun getBackgroundStatus(context: Context): String {
            val serviceRunning = isServiceRunning(context)
            val batteryOptimized = BackgroundPermissionManager.isIgnoringBatteryOptimizations(context)
            val overlayPermission = BackgroundPermissionManager.canDrawOverlays(context)
            
            return buildString {
                appendLine("Background Execution Status:")
                appendLine("========================")
                appendLine("Service Running: ${if (serviceRunning) "✅ Yes" else "❌ No"}")
                appendLine("Battery Optimization: ${if (batteryOptimized) "✅ Disabled" else "❌ Enabled"}")
                appendLine("Overlay Permission: ${if (overlayPermission) "✅ Granted" else "❌ Not granted"}")
                appendLine()
                
                when {
                    !serviceRunning && !batteryOptimized -> {
                        appendLine("⚠️ Service stopped. Battery optimization may be interfering.")
                        appendLine("Please disable battery optimization for this app.")
                    }
                    !serviceRunning -> {
                        appendLine("⚠️ Service stopped. Tap refresh to restart.")
                    }
                    serviceRunning && batteryOptimized && overlayPermission -> {
                        appendLine("✅ All systems operational for background execution!")
                    }
                    else -> {
                        appendLine("⚠️ Some features may not work optimally.")
                    }
                }
            }
        }
    }
}
