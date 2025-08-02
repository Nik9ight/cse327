package com.example.llmapp.utils

import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.llmapp.utils.ServiceManager
import com.example.llmapp.service.WorkflowBackgroundService
import com.example.llmapp.service.WorkflowSchedulerService
import com.example.llmapp.service.WorkflowWatchdogService
import kotlin.system.exitProcess

/**
 * Utility class for gracefully exiting the app and stopping all background services
 */
object AppExitManager {
    
    private const val TAG = "AppExitManager"
    
    /**
     * Gracefully exit the app by stopping all background services and finishing the activity
     */
    fun exitApp(context: Context, onComplete: () -> Unit = {}) {
        Log.d(TAG, "Starting graceful app exit...")
        
        // Run the exit process on a background thread to avoid blocking UI
        Thread {
            try {
                // Stop all background services with force if needed
                stopAllBackgroundServicesWithForce(context)
                
                // Clear any persistent data if needed
                clearAppState(context)
                
                Log.d(TAG, "App exit completed successfully")
                
                // Call the completion callback on the main thread
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    onComplete()
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error during app exit", e)
                // Still proceed with exit even if there's an error
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    onComplete()
                }
            }
        }.start()
    }
    
    /**
     * Stop all background services related to workflows with force if graceful stop fails
     */
    private fun stopAllBackgroundServicesWithForce(context: Context) {
        Log.d(TAG, "Stopping all background services with force if needed...")
        
        try {
            // First try graceful stop
            Log.d(TAG, "Attempting graceful service stop...")
            ServiceManager.stopBackgroundService(context)
            
            // Wait a short time for graceful shutdown
            Thread.sleep(2000) // Wait 2 seconds
            
            // Check if services are still running and force stop if needed
            if (ServiceManager.isServiceRunning(context)) {
                Log.w(TAG, "Services still running, forcing stop...")
                forceStopServices(context)
            } else {
                Log.d(TAG, "Services stopped gracefully")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping background services, attempting force stop", e)
            forceStopServices(context)
        }
    }
    
    /**
     * Force stop all services
     */
    private fun forceStopServices(context: Context) {
        Log.d(TAG, "Force stopping all services...")
        
        // Stop individual services directly as backup
        val servicesToStop = listOf(
            WorkflowBackgroundService::class.java,
            WorkflowSchedulerService::class.java,
            WorkflowWatchdogService::class.java
        )
        
        servicesToStop.forEach { serviceClass ->
            try {
                val intent = Intent(context, serviceClass)
                context.stopService(intent)
                Log.d(TAG, "Force stopped service: ${serviceClass.simpleName}")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to force stop service ${serviceClass.simpleName}", e)
            }
        }
        
        // Additional force measures
        try {
            // Send broadcast to receivers to stop any scheduled tasks
            val stopIntent = Intent("com.example.llmapp.FORCE_STOP")
            context.sendBroadcast(stopIntent)
            
            Log.d(TAG, "Sent force stop broadcast")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to send force stop broadcast", e)
        }
    }
    
    /**
     * Clear app state and preferences if needed
     */
    private fun clearAppState(context: Context) {
        try {
            // Clear any temporary workflow state
            // You can add specific cleanup logic here if needed
            Log.d(TAG, "App state cleared")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing app state", e)
        }
    }
    
    /**
     * Show exit confirmation dialog
     */
    fun showExitConfirmation(context: Context, onConfirm: () -> Unit) {
        androidx.appcompat.app.AlertDialog.Builder(context)
            .setTitle("Exit App")
            .setMessage("Are you sure you want to exit?\n\nThis will:\n• Stop all background workflows\n• Close the application completely\n• Clear all active sessions")
            .setIcon(android.R.drawable.ic_dialog_alert)
            .setPositiveButton("Exit") { _, _ ->
                // Show starting exit process
                android.widget.Toast.makeText(context, "Stopping background services...", android.widget.Toast.LENGTH_SHORT).show()
                onConfirm()
            }
            .setNegativeButton("Cancel", null)
            .setCancelable(true)
            .show()
    }
    
    /**
     * Force exit the entire app process (use as last resort)
     */
    fun forceExit() {
        Log.w(TAG, "Force exiting app process")
        exitProcess(0)
    }
}
