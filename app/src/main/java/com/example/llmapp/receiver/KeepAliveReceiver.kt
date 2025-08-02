package com.example.llmapp.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.llmapp.service.WorkflowBackgroundService

class KeepAliveReceiver : BroadcastReceiver() {
    
    private val TAG = "KeepAliveReceiver"
    
    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Keep-alive trigger: ${intent.action}")
        
        when (intent.action) {
            Intent.ACTION_SCREEN_ON,
            Intent.ACTION_USER_PRESENT -> {
                try {
                    // Restart service on user interaction
                    WorkflowBackgroundService.start(context)
                    Log.d(TAG, "Background service keep-alive triggered on screen on/user present")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to keep service alive", e)
                }
            }
            Intent.ACTION_SCREEN_OFF -> {
                try {
                    // Also restart service when screen turns off to counter system killing
                    Log.d(TAG, "Screen off - reinforcing service to prevent system kill")
                    
                    // Use a short delay to let system settle
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        WorkflowBackgroundService.start(context)
                        Log.d(TAG, "Service reinforced after screen off")
                    }, 2000) // 2 second delay
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to reinforce service on screen off", e)
                }
            }
        }
    }
}
