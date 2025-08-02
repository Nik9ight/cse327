package com.example.llmapp.utils

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.appcompat.app.AlertDialog

class BackgroundPermissionManager {
    
    companion object {
        private const val TAG = "BackgroundPermissionManager"
        private const val REQUEST_IGNORE_BATTERY_OPTIMIZATIONS = 1002
        private const val REQUEST_SYSTEM_ALERT_WINDOW = 1003
        
        /**
         * Check if the app is whitelisted from battery optimizations
         */
        fun isIgnoringBatteryOptimizations(context: Context): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                powerManager.isIgnoringBatteryOptimizations(context.packageName)
            } else {
                true // No battery optimization on older versions
            }
        }
        
        /**
         * Request to ignore battery optimizations
         */
        fun requestIgnoreBatteryOptimizations(activity: Activity) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (!isIgnoringBatteryOptimizations(activity)) {
                    showBatteryOptimizationDialog(activity)
                }
            }
        }
        
        /**
         * Check if the app has system alert window permission
         */
        fun canDrawOverlays(context: Context): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Settings.canDrawOverlays(context)
            } else {
                true // No restriction on older versions
            }
        }
        
        /**
         * Request system alert window permission
         */
        fun requestSystemAlertWindowPermission(activity: Activity) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (!canDrawOverlays(activity)) {
                    showOverlayPermissionDialog(activity)
                }
            }
        }
        
        /**
         * Request all necessary background permissions
         */
        fun requestAllBackgroundPermissions(activity: Activity) {
            requestIgnoreBatteryOptimizations(activity)
            requestSystemAlertWindowPermission(activity)
        }
        
        /**
         * Open app settings page
         */
        fun openAppSettings(context: Context) {
            try {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${context.packageName}")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to open app settings", e)
            }
        }
        
        private fun showBatteryOptimizationDialog(activity: Activity) {
            AlertDialog.Builder(activity)
                .setTitle("Battery Optimization")
                .setMessage("To ensure workflows run reliably in the background, please disable battery optimization for this app.")
                .setPositiveButton("Settings") { _, _ ->
                    openBatteryOptimizationSettings(activity)
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
        
        private fun showOverlayPermissionDialog(activity: Activity) {
            AlertDialog.Builder(activity)
                .setTitle("Overlay Permission")
                .setMessage("This app needs permission to display over other apps for background notifications.")
                .setPositiveButton("Grant") { _, _ ->
                    openOverlaySettings(activity)
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
        
        private fun openBatteryOptimizationSettings(activity: Activity) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:${activity.packageName}")
                    }
                    activity.startActivityForResult(intent, REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to open battery optimization settings", e)
                // Fallback to general settings
                openAppSettings(activity)
            }
        }
        
        private fun openOverlaySettings(activity: Activity) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                        data = Uri.parse("package:${activity.packageName}")
                    }
                    activity.startActivityForResult(intent, REQUEST_SYSTEM_ALERT_WINDOW)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to open overlay settings", e)
                // Fallback to general settings
                openAppSettings(activity)
            }
        }
        
        /**
         * Get a summary of current background permissions status
         */
        fun getPermissionStatus(context: Context): String {
            val batteryOptimized = if (isIgnoringBatteryOptimizations(context)) "✅ Disabled" else "❌ Enabled"
            val overlayPermission = if (canDrawOverlays(context)) "✅ Granted" else "❌ Not granted"
            
            return """
                Background Permissions Status:
                
                Battery Optimization: $batteryOptimized
                Overlay Permission: $overlayPermission
                
                ${if (!isIgnoringBatteryOptimizations(context) || !canDrawOverlays(context)) 
                    "⚠️ Some permissions are missing. Tap 'Grant Permissions' to fix this." 
                else 
                    "✅ All permissions granted for unrestricted background execution."}
            """.trimIndent()
        }
    }
}
