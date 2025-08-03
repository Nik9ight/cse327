package com.example.llmapp.geofence

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import com.example.llmapp.TelegramService
import kotlinx.coroutines.*
import kotlin.coroutines.suspendCoroutine
import kotlin.coroutines.resume

class GeofenceBroadcastReceiver : BroadcastReceiver() {
    
    private val TAG = "GeofenceBroadcastReceiver"
    
    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Geofence broadcast received")
        
        if (intent.action == "com.example.llmapp.ACTION_GEOFENCE_EVENT") {
            val geofencingEvent = GeofencingEvent.fromIntent(intent)
            
            if (geofencingEvent?.hasError() == true) {
                Log.e(TAG, "Geofencing error: ${geofencingEvent.errorCode}")
                return
            }
            
            val geofenceTransition = geofencingEvent?.geofenceTransition
            val triggeringGeofences = geofencingEvent?.triggeringGeofences
            
            Log.d(TAG, "Geofence transition: $geofenceTransition")
            
            when (geofenceTransition) {
                Geofence.GEOFENCE_TRANSITION_ENTER -> {
                    triggeringGeofences?.forEach { geofence ->
                        handleGeofenceEnter(context, geofence.requestId)
                    }
                }
                Geofence.GEOFENCE_TRANSITION_EXIT -> {
                    triggeringGeofences?.forEach { geofence ->
                        handleGeofenceExit(context, geofence.requestId)
                    }
                }
                else -> {
                    Log.w(TAG, "Unknown geofence transition: $geofenceTransition")
                }
            }
        }
    }
    
    private fun handleGeofenceEnter(context: Context, geofenceId: String) {
        Log.d(TAG, "Entered geofence: $geofenceId")
        
        // Get stored message and chat ID for this geofence
        val prefs = context.getSharedPreferences("geofence_prefs", Context.MODE_PRIVATE)
        val message = prefs.getString("${geofenceId}_enter_message", "🎯 Entered location zone: $geofenceId")
        val isEnabled = prefs.getBoolean("${geofenceId}_enabled", true)
        val geofenceChatId = prefs.getString("${geofenceId}_chat_id", null)
        
        if (isEnabled && message != null) {
            sendTelegramMessage(context, message, geofenceChatId)
            
            // Show notification to user
            val chatInfo = if (geofenceChatId != null) " to chat $geofenceChatId" else ""
            GeofenceNotificationManager.showGeofenceNotification(
                context,
                "Geofence Triggered",
                "Entered: $geofenceId - Message sent to Telegram$chatInfo"
            )
        }
    }
    
    private fun handleGeofenceExit(context: Context, geofenceId: String) {
        Log.d(TAG, "Exited geofence: $geofenceId")
        
        // Get stored exit message and chat ID for this geofence
        val prefs = context.getSharedPreferences("geofence_prefs", Context.MODE_PRIVATE)
        val message = prefs.getString("${geofenceId}_exit_message", null)
        val isEnabled = prefs.getBoolean("${geofenceId}_enabled", true)
        val exitEnabled = prefs.getBoolean("${geofenceId}_exit_enabled", false)
        val geofenceChatId = prefs.getString("${geofenceId}_chat_id", null)
        
        if (isEnabled && exitEnabled && message != null) {
            sendTelegramMessage(context, message, geofenceChatId)
            
            // Show notification to user
            val chatInfo = if (geofenceChatId != null) " to chat $geofenceChatId" else ""
            GeofenceNotificationManager.showGeofenceNotification(
                context,
                "Geofence Triggered",
                "Exited: $geofenceId - Message sent to Telegram$chatInfo"
            )
        }
    }
    
    private fun sendTelegramMessage(context: Context, message: String, geofenceChatId: String? = null) {
        // Use coroutine to handle async operation
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val telegramService = TelegramService(context)
                
                if (telegramService.isLoggedIn()) {
                    // Add timestamp and location info to message
                    val timestamp = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
                    val fullMessage = "🕒 $timestamp\n$message"
                    
                    // Temporarily override chat ID if geofence has specific one
                    val originalChatId = telegramService.getChatId()
                    val targetChatId = geofenceChatId ?: originalChatId
                    
                    if (targetChatId != null) {
                        // Temporarily set the target chat ID
                        if (geofenceChatId != null && geofenceChatId != originalChatId) {
                            telegramService.setChatId(geofenceChatId)
                        }
                        
                        // Send message synchronously in background thread
                        sendMessageSync(telegramService, fullMessage)
                        
                        // Restore original chat ID if we changed it
                        if (geofenceChatId != null && geofenceChatId != originalChatId && originalChatId != null) {
                            telegramService.setChatId(originalChatId)
                        }
                    } else {
                        Log.w(TAG, "No chat ID available for geofence message")
                        withContext(Dispatchers.Main) {
                            GeofenceNotificationManager.showGeofenceNotification(
                                context,
                                "Geofence Triggered",
                                "Configure Telegram chat ID to receive location alerts"
                            )
                        }
                    }
                } else {
                    Log.w(TAG, "Telegram not logged in, cannot send geofence message")
                    
                    // Show notification that telegram is not configured
                    withContext(Dispatchers.Main) {
                        GeofenceNotificationManager.showGeofenceNotification(
                            context,
                            "Geofence Triggered",
                            "Configure Telegram to receive location alerts"
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send geofence message", e)
            }
        }
    }
    
    private suspend fun sendMessageSync(telegramService: TelegramService, message: String) {
        return suspendCoroutine<Unit> { continuation ->
            telegramService.sendMessage(
                text = message,
                onSuccess = {
                    Log.d(TAG, "Geofence message sent successfully")
                    continuation.resume(Unit)
                },
                onError = { error ->
                    Log.e(TAG, "Failed to send geofence message: $error")
                    continuation.resume(Unit) // Don't throw error, just log it
                }
            )
        }
    }
}
