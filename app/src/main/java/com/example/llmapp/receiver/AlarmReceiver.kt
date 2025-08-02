package com.example.llmapp.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.llmapp.service.WorkflowBackgroundService

class AlarmReceiver : BroadcastReceiver() {
    
    private val TAG = "AlarmReceiver"
    
    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Alarm received for workflow execution")
        
        try {
            val workflowId = intent.getStringExtra("workflow_id")
            Log.d(TAG, "Executing workflow: $workflowId")
            
            // Ensure background service is running
            WorkflowBackgroundService.start(context)
            
            // TODO: Trigger specific workflow execution
            // You can integrate this with your workflow execution logic
            
        } catch (e: Exception) {
            Log.e(TAG, "Error handling alarm", e)
        }
    }
}
