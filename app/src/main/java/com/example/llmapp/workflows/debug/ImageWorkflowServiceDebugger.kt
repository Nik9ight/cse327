package com.example.llmapp.workflows.debug

import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.llmapp.workflows.services.ImageWorkflowBackgroundService

/**
 * Debug utility for testing image workflow service lifecycle
 */
class ImageWorkflowServiceDebugger(private val context: Context) {
    
    companion object {
        private const val TAG = "ImageWorkflowDebugger"
    }
    
    /**
     * Test starting the image workflow service with debug configuration
     */
    fun startServiceWithDebugConfig() {
        Log.d(TAG, "Starting image workflow service with debug configuration")
        
        try {
            val intent = Intent(context, ImageWorkflowBackgroundService::class.java).apply {
                putExtra("person_workflow_enabled", true)
                putExtra("receipt_workflow_enabled", true)
                putExtra("person_workflow_chat_id", "debug_person_chat")
                putExtra("receipt_workflow_chat_id", "debug_receipt_chat")
                putExtra("target_subject", "Debug Person")
                putExtra("receipt_summary_time", "20:00")
            }
            
            context.startForegroundService(intent)
            Log.d(TAG, "✅ Image workflow service started successfully")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to start image workflow service", e)
        }
    }
    
    /**
     * Test stopping the image workflow service
     */
    fun stopService() {
        Log.d(TAG, "Stopping image workflow service")
        
        try {
            val intent = Intent(context, ImageWorkflowBackgroundService::class.java)
            val stopped = context.stopService(intent)
            
            Log.d(TAG, if (stopped) "✅ Service stopped successfully" else "⚠️ Service was not running")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to stop image workflow service", e)
        }
    }
    
    /**
     * Test the complete service lifecycle
     */
    fun testServiceLifecycle() {
        Log.d(TAG, "🧪 Testing image workflow service lifecycle")
        
        // Start service
        startServiceWithDebugConfig()
        
        // Wait a bit
        Thread.sleep(2000)
        
        // Stop service
        stopService()
        
        Log.d(TAG, "📊 Service lifecycle test completed")
    }
}
