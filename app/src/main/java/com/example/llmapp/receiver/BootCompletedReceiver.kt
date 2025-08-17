package com.example.llmapp.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.llmapp.service.WorkflowBackgroundService
import com.example.llmapp.service.WorkflowWatchdogService

class BootCompletedReceiver : BroadcastReceiver() {
    
    private val TAG = "BootCompletedReceiver"
    
    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Received broadcast: ${intent.action}")
        
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_PACKAGE_REPLACED -> {
                Log.d(TAG, "Starting background services after boot/update")
                
                try {
                    // Use a delay to ensure system is ready
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        WorkflowBackgroundService.start(context)
                        WorkflowWatchdogService.start(context)
                        Log.d(TAG, "Background services started successfully after boot")
                    }, 5000) // 5 second delay
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to start background services", e)
                }
            }
        }
    }
    
    private fun startImageWorkflowServiceIfNeeded(context: Context) {
        try {
            // Check if there are saved image workflows
            val workflowManager = com.example.llmapp.workflows.services.WorkflowConfigManager(context)
            val workflows = workflowManager.getAllWorkflows()
            
            if (workflows.isNotEmpty()) {
                Log.d(TAG, "Found ${workflows.size} image workflows, starting ImageWorkflowBackgroundService")
                val serviceIntent = Intent(context, com.example.llmapp.workflows.services.ImageWorkflowBackgroundService::class.java)
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    try {
                        context.startForegroundService(serviceIntent)
                    } catch (e: Exception) {
                        Log.w(TAG, "Cannot start image workflow foreground service, trying regular service", e)
                        context.startService(serviceIntent)
                    }
                } else {
                    context.startService(serviceIntent)
                }
                
                Log.d(TAG, "Image workflow background service start requested")
            } else {
                Log.d(TAG, "No image workflows configured, skipping ImageWorkflowBackgroundService")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking for image workflows", e)
        }
    }
}
