package com.example.llmapp.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.example.llmapp.R
import com.example.llmapp.utils.BackgroundPermissionManager

class BatteryOptimizationActivity : ComponentActivity() {
    
    private val REQUEST_IGNORE_BATTERY_OPTIMIZATIONS = 1001
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_battery_optimization)
        
        val titleText = findViewById<TextView>(R.id.titleText)
        val descriptionText = findViewById<TextView>(R.id.descriptionText)
        val statusText = findViewById<TextView>(R.id.statusText)
        val grantButton = findViewById<Button>(R.id.grantButton)
        val skipButton = findViewById<Button>(R.id.skipButton)
        
        titleText.text = "Battery Optimization Settings"
        descriptionText.text = """
            To ensure your workflows run reliably in the background, please disable battery optimization for this app.
            
            This will prevent Android from killing the app when the screen is off or when the device is idle.
            
            Your workflows need to run continuously to monitor emails and send messages as configured.
        """.trimIndent()
        
        updateStatus()
        
        grantButton.setOnClickListener {
            requestBatteryOptimizationWhitelist()
        }
        
        skipButton.setOnClickListener {
            Toast.makeText(this, "Background execution may be unreliable", Toast.LENGTH_LONG).show()
            finish()
        }
    }
    
    override fun onResume() {
        super.onResume()
        updateStatus()
    }
    
    private fun updateStatus() {
        val statusText = findViewById<TextView>(R.id.statusText)
        val grantButton = findViewById<Button>(R.id.grantButton)
        
        if (BackgroundPermissionManager.isIgnoringBatteryOptimizations(this)) {
            statusText.text = "✅ Battery optimization is disabled - workflows can run in background"
            statusText.setTextColor(getColor(android.R.color.holo_green_dark))
            grantButton.text = "Settings Updated ✓"
            grantButton.isEnabled = false
            
            // Auto-close after success
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                Toast.makeText(this, "Battery optimization disabled successfully!", Toast.LENGTH_SHORT).show()
                finish()
            }, 2000)
        } else {
            statusText.text = "❌ Battery optimization is enabled - workflows may be killed in background"
            statusText.setTextColor(getColor(android.R.color.holo_red_dark))
            grantButton.text = "Disable Battery Optimization"
            grantButton.isEnabled = true
        }
    }
    
    private fun requestBatteryOptimizationWhitelist() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivityForResult(intent, REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            } catch (e: Exception) {
                // Fallback to general battery settings
                try {
                    val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    startActivity(intent)
                    Toast.makeText(this, "Please find and disable battery optimization for LLM App", Toast.LENGTH_LONG).show()
                } catch (e2: Exception) {
                    // Last fallback to app settings
                    BackgroundPermissionManager.openAppSettings(this)
                    Toast.makeText(this, "Please disable battery optimization in app settings", Toast.LENGTH_LONG).show()
                }
            }
        } else {
            Toast.makeText(this, "Battery optimization not needed on this Android version", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        if (requestCode == REQUEST_IGNORE_BATTERY_OPTIMIZATIONS) {
            updateStatus()
        }
    }
}
