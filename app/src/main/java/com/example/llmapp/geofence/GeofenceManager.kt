package com.example.llmapp.geofence

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.util.Log
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.*
import com.google.android.gms.tasks.Task
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.ConnectionResult

data class GeofenceData(
    val id: String,
    val latitude: Double,
    val longitude: Double,
    val radius: Float,
    val enterMessage: String,
    val exitMessage: String? = null,
    val isEnabled: Boolean = true,
    val enableExit: Boolean = false,
    val name: String = id,
    val chatId: String? = null  // Optional chat ID for this geofence
)

class GeofenceManager(private val context: Context) {
    
    private val TAG = "GeofenceManager"
    private val geofencingClient: GeofencingClient = LocationServices.getGeofencingClient(context)
    private val prefs = context.getSharedPreferences("geofence_prefs", Context.MODE_PRIVATE)
    
    companion object {
        const val GEOFENCE_ACTION = "com.example.llmapp.ACTION_GEOFENCE_EVENT"
    }
    
    /**
     * Add a new geofence
     */
    fun addGeofence(geofenceData: GeofenceData, onSuccess: () -> Unit, onError: (String) -> Unit) {
        if (!hasLocationPermissions()) {
            onError("Location permissions not granted")
            return
        }
        
        // Check if Google Play Services is available
        if (!isGooglePlayServicesAvailable()) {
            onError("Google Play Services not available - required for geofencing")
            return
        }
        
        Log.d(TAG, "Adding geofence: ${geofenceData.id}")
        
        // Create geofence
        val geofence = Geofence.Builder()
            .setRequestId(geofenceData.id)
            .setCircularRegion(geofenceData.latitude, geofenceData.longitude, geofenceData.radius)
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .setTransitionTypes(
                if (geofenceData.enableExit) {
                    Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT
                } else {
                    Geofence.GEOFENCE_TRANSITION_ENTER
                }
            )
            .build()
        
        // Create geofencing request
        val geofenceRequest = GeofencingRequest.Builder()
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
            .addGeofence(geofence)
            .build()
        
        // Create pending intent
        val pendingIntent = getGeofencePendingIntent()
        
        // Add geofence
        try {
            geofencingClient.addGeofences(geofenceRequest, pendingIntent)
                .addOnSuccessListener {
                    Log.d(TAG, "Geofence added successfully: ${geofenceData.id}")
                    saveGeofenceData(geofenceData)
                    onSuccess()
                }
                .addOnFailureListener { exception ->
                    Log.e(TAG, "Failed to add geofence: ${geofenceData.id}", exception)
                    val errorMessage = getGeofenceErrorMessage(exception)
                    onError(errorMessage)
                }
        } catch (securityException: SecurityException) {
            onError("Location permissions not granted")
        }
    }
    
    /**
     * Remove a geofence
     */
    fun removeGeofence(geofenceId: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        Log.d(TAG, "Removing geofence: $geofenceId")
        
        geofencingClient.removeGeofences(listOf(geofenceId))
            .addOnSuccessListener {
                Log.d(TAG, "Geofence removed successfully: $geofenceId")
                removeGeofenceData(geofenceId)
                onSuccess()
            }
            .addOnFailureListener { exception ->
                Log.e(TAG, "Failed to remove geofence: $geofenceId", exception)
                onError("Failed to remove geofence: ${exception.message}")
            }
    }
    
    /**
     * Remove all geofences
     */
    fun removeAllGeofences(onSuccess: () -> Unit, onError: (String) -> Unit) {
        Log.d(TAG, "Removing all geofences")
        
        val pendingIntent = getGeofencePendingIntent()
        geofencingClient.removeGeofences(pendingIntent)
            .addOnSuccessListener {
                Log.d(TAG, "All geofences removed successfully")
                clearAllGeofenceData()
                onSuccess()
            }
            .addOnFailureListener { exception ->
                Log.e(TAG, "Failed to remove all geofences", exception)
                onError("Failed to remove all geofences: ${exception.message}")
            }
    }
    
    /**
     * Get all saved geofences
     */
    fun getSavedGeofences(): List<GeofenceData> {
        val geofences = mutableListOf<GeofenceData>()
        val allPrefs = prefs.all
        
        val geofenceIds = allPrefs.keys.filter { it.endsWith("_latitude") }
            .map { it.replace("_latitude", "") }
        
        for (id in geofenceIds) {
            try {
                val latitude = prefs.getFloat("${id}_latitude", 0f).toDouble()
                val longitude = prefs.getFloat("${id}_longitude", 0f).toDouble()
                val radius = prefs.getFloat("${id}_radius", 100f)
                val enterMessage = prefs.getString("${id}_enter_message", "Entered $id") ?: "Entered $id"
                val exitMessage = prefs.getString("${id}_exit_message", null)
                val isEnabled = prefs.getBoolean("${id}_enabled", true)
                val enableExit = prefs.getBoolean("${id}_exit_enabled", false)
                val name = prefs.getString("${id}_name", id) ?: id
                val chatId = prefs.getString("${id}_chat_id", null)
                
                if (latitude != 0.0 && longitude != 0.0) {
                    geofences.add(
                        GeofenceData(
                            id = id,
                            latitude = latitude,
                            longitude = longitude,
                            radius = radius,
                            enterMessage = enterMessage,
                            exitMessage = exitMessage,
                            isEnabled = isEnabled,
                            enableExit = enableExit,
                            name = name,
                            chatId = chatId
                        )
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading geofence data for $id", e)
            }
        }
        
        return geofences
    }
    
    /**
     * Update geofence enabled/disabled status
     */
    fun updateGeofenceStatus(geofenceId: String, isEnabled: Boolean) {
        prefs.edit().putBoolean("${geofenceId}_enabled", isEnabled).apply()
        Log.d(TAG, "Updated geofence $geofenceId enabled status: $isEnabled")
    }
    
    /**
     * Check if location permissions are granted
     */
    fun hasLocationPermissions(): Boolean {
        return ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }
    
    /**
     * Check if background location permission is granted (Android 10+)
     */
    fun hasBackgroundLocationPermission(): Boolean {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
        } else {
            true // Not required for Android < 10
        }
    }
    
    /**
     * Calculate distance between two locations
     */
    fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val results = FloatArray(1)
        Location.distanceBetween(lat1, lon1, lat2, lon2, results)
        return results[0]
    }
    
    private fun getGeofencePendingIntent(): PendingIntent {
        val intent = Intent(context, GeofenceBroadcastReceiver::class.java)
        intent.action = GEOFENCE_ACTION
        return PendingIntent.getBroadcast(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
    }
    
    private fun saveGeofenceData(geofenceData: GeofenceData) {
        with(prefs.edit()) {
            putFloat("${geofenceData.id}_latitude", geofenceData.latitude.toFloat())
            putFloat("${geofenceData.id}_longitude", geofenceData.longitude.toFloat())
            putFloat("${geofenceData.id}_radius", geofenceData.radius)
            putString("${geofenceData.id}_enter_message", geofenceData.enterMessage)
            putString("${geofenceData.id}_exit_message", geofenceData.exitMessage)
            putBoolean("${geofenceData.id}_enabled", geofenceData.isEnabled)
            putBoolean("${geofenceData.id}_exit_enabled", geofenceData.enableExit)
            putString("${geofenceData.id}_name", geofenceData.name)
            putString("${geofenceData.id}_chat_id", geofenceData.chatId)
            apply()
        }
        Log.d(TAG, "Saved geofence data: ${geofenceData.id}")
    }
    
    private fun removeGeofenceData(geofenceId: String) {
        with(prefs.edit()) {
            remove("${geofenceId}_latitude")
            remove("${geofenceId}_longitude")
            remove("${geofenceId}_radius")
            remove("${geofenceId}_enter_message")
            remove("${geofenceId}_exit_message")
            remove("${geofenceId}_enabled")
            remove("${geofenceId}_exit_enabled")
            remove("${geofenceId}_name")
            remove("${geofenceId}_chat_id")
            apply()
        }
        Log.d(TAG, "Removed geofence data: $geofenceId")
    }
    
    private fun clearAllGeofenceData() {
        prefs.edit().clear().apply()
        Log.d(TAG, "Cleared all geofence data")
    }
    
    /**
     * Check if Google Play Services is available for geofencing
     */
    private fun isGooglePlayServicesAvailable(): Boolean {
        return try {
            val googleApiAvailability = com.google.android.gms.common.GoogleApiAvailability.getInstance()
            val result = googleApiAvailability.isGooglePlayServicesAvailable(context)
            result == com.google.android.gms.common.ConnectionResult.SUCCESS
        } catch (e: Exception) {
            Log.e(TAG, "Error checking Google Play Services availability", e)
            false
        }
    }
    
    /**
     * Get user-friendly error message for geofencing failures
     */
    private fun getGeofenceErrorMessage(exception: Exception): String {
        return when {
            exception.message?.contains("1000") == true -> {
                "Geofencing not available. Please check:\n" +
                "• Location services are enabled\n" +
                "• Google Play Services is updated\n" +
                "• Device supports geofencing"
            }
            exception.message?.contains("1001") == true -> {
                "Too many geofences. Remove some existing geofences first."
            }
            exception.message?.contains("1002") == true -> {
                "Invalid geofence parameters. Check location and radius."
            }
            exception.message?.contains("1003") == true -> {
                "Location permission denied. Enable location access."
            }
            exception.message?.contains("1004") == true -> {
                "Network connection required for geofencing setup."
            }
            else -> {
                "Failed to add geofence: ${exception.message}\n\n" +
                "Try:\n" +
                "• Enable location services\n" +
                "• Update Google Play Services\n" +
                "• Restart the app"
            }
        }
    }
}
