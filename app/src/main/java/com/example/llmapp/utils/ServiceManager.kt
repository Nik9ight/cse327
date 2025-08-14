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
         * Check if the image workflow background service is running
         */
        fun isImageWorkflowServiceRunning(context: Context): Boolean {
            return try {
                val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                val services = activityManager.getRunningServices(Integer.MAX_VALUE)
                
                services.any { service ->
                    service.service.className == "com.example.llmapp.workflows.services.ImageWorkflowBackgroundService"
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking image workflow service status", e)
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
         * Start image workflow service if there are active workflows
         */
        fun startImageWorkflowServiceIfNeeded(context: Context): Boolean {
            return try {
                val workflowManager =
                    com.example.llmapp.workflows.services.WorkflowConfigManager(context)
                val workflows = workflowManager.getAllWorkflows()

                if (workflows.isNotEmpty() && !isImageWorkflowServiceRunning(context)) {
                    Log.d(TAG, "Starting image workflow service for ${workflows.size} workflows")

                    val serviceIntent = Intent(
                        context,
                        com.example.llmapp.workflows.services.ImageWorkflowBackgroundService::class.java
                    )

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        try {
                            context.startForegroundService(serviceIntent)
                        } catch (e: Exception) {
                            Log.w(
                                TAG,
                                "Cannot start image workflow foreground service, trying regular service",
                                e
                            )
                            context.startService(serviceIntent)
                        }
                    } else {
                        context.startService(serviceIntent)
                    }

                    Log.d(TAG, "Image workflow service started")
                    true
                } else if (workflows.isEmpty()) {
                    Log.d(TAG, "No image workflows configured, skipping image workflow service")
                    true
                } else {
                    Log.d(TAG, "Image workflow service already running")
                    true
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start image workflow service", e)
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
                
                // Also stop image workflow service
                stopImageWorkflowService(context)
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to stop background service", e)
                false
            }
        }
        
        /**
         * Stop image workflow service
         */
        fun stopImageWorkflowService(context: Context): Boolean {
            return try {
                if (isImageWorkflowServiceRunning(context)) {
                    Log.d(TAG, "Stopping image workflow service...")
                    val serviceIntent = Intent(context, com.example.llmapp.workflows.services.ImageWorkflowBackgroundService::class.java)
                    val stopped = context.stopService(serviceIntent)
                    Log.d(TAG, "Image workflow service stop result: $stopped")
                    true
                } else {
                    Log.d(TAG, "Image workflow service not running")
                    true
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to stop image workflow service", e)
                false
            }
        }
        
        /**
         * Get the current status of background execution
         */
        fun getBackgroundStatus(context: Context): String {
            val serviceRunning = isServiceRunning(context)
            val imageServiceRunning = isImageWorkflowServiceRunning(context)
            val batteryOptimized = BackgroundPermissionManager.isIgnoringBatteryOptimizations(context)
            val overlayPermission = BackgroundPermissionManager.canDrawOverlays(context)
            
            // Check image workflows
            val workflowManager = com.example.llmapp.workflows.services.WorkflowConfigManager(context)
            val imageWorkflows = workflowManager.getAllWorkflows()
            
            return buildString {
                appendLine("Background Execution Status:")
                appendLine("========================")
                appendLine("Main Service Running: ${if (serviceRunning) "✅ Yes" else "❌ No"}")
                appendLine("Image Service Running: ${if (imageServiceRunning) "✅ Yes" else "❌ No"}")
                appendLine("Image Workflows: ${imageWorkflows.size} configured")
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
                    imageWorkflows.isNotEmpty() && !imageServiceRunning -> {
                        appendLine("⚠️ Image workflows configured but service not running.")
                        appendLine("Image monitoring may not be working.")
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
