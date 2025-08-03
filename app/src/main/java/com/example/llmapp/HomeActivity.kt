package com.example.llmapp

import android.content.Intent
import android.graphics.drawable.AnimationDrawable
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.appcompat.widget.PopupMenu
import com.example.llmapp.geofence.GeofenceActivity
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.example.llmapp.new_implementation.ui.NewWorkflowListActivity
import com.example.llmapp.new_implementation.ui.NewWorkflowCreateActivity
import com.example.llmapp.service.WorkflowBackgroundService
import com.example.llmapp.utils.BackgroundPermissionManager
import com.example.llmapp.utils.ServiceManager
import com.example.llmapp.ui.BatteryOptimizationActivity
import com.example.llmapp.utils.AppExitManager

class HomeActivity : ComponentActivity() {
    private lateinit var login: Login
    private lateinit var permissions: Permissions
    private val RC_PERMISSIONS = 1002
    private val requiredPermissions = arrayOf(
        android.Manifest.permission.READ_EXTERNAL_STORAGE,
        android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
        android.Manifest.permission.INTERNET,
        android.Manifest.permission.ACCESS_NETWORK_STATE,
        android.Manifest.permission.ACCESS_WIFI_STATE,
        android.Manifest.permission.ACCESS_COARSE_LOCATION,
        android.Manifest.permission.ACCESS_FINE_LOCATION,
        android.Manifest.permission.RECORD_AUDIO,
        android.Manifest.permission.CAMERA,
        android.Manifest.permission.READ_PHONE_STATE,
        android.Manifest.permission.READ_PHONE_NUMBERS
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)
        
        // Start the blinking dot animation
        findViewById<ImageView>(R.id.blinkingDot)?.let { blinkingDot ->
            (blinkingDot.drawable as? AnimationDrawable)?.start()
        }

        login = Login(this)
        login.initGoogleSignIn()
        permissions = Permissions(this)

        val signInButton = findViewById<Button>(R.id.signInButton)
        val enablePermissionsButton = findViewById<Button>(R.id.enablePermissionsButton)
        val pipelineButton = findViewById<Button>(R.id.pipelineButton)
        val workflowsButton = findViewById<Button>(R.id.workflowsButton)
        val newWorkflowsButton = findViewById<Button>(R.id.newWorkflowsButton)
        val llmButton = findViewById<Button>(R.id.llmButton)
        val geofenceButton = findViewById<Button>(R.id.geofenceButton)
        val exitIcon = findViewById<ImageView>(R.id.exitIcon)

        signInButton.setOnClickListener {
            if (login.isSignedIn()) {
                // Sign out
                login.signOut {
                    Toast.makeText(this, "Signed out successfully", Toast.LENGTH_SHORT).show()
                    updateSignInButtonState()
                }
            } else {
                // Sign in
                login.signIn()
            }
        }
        
        // Initial button state update
        updateSignInButtonState()
        
        enablePermissionsButton.setOnClickListener {
            if (!permissions.hasPermissions(requiredPermissions)) {
                permissions.requestPermissions(requiredPermissions, RC_PERMISSIONS)
            } else {
                // Check battery optimization status
                if (!BackgroundPermissionManager.isIgnoringBatteryOptimizations(this)) {
                    // Launch dedicated battery optimization activity
                    val intent = Intent(this, BatteryOptimizationActivity::class.java)
                    startActivity(intent)
                } else {
                    Toast.makeText(this, "All permissions granted for background execution", Toast.LENGTH_SHORT).show()
                }
            }
        }
        
        pipelineButton.setOnClickListener { view ->
            showPipelineMenu(view)
        }
        
        workflowsButton.setOnClickListener {
            startActivity(Intent(this, WorkflowListActivity::class.java))
        }
        
        newWorkflowsButton.setOnClickListener { view ->
            showNewWorkflowMenu(view)
        }
        
        llmButton.setOnClickListener { view ->
            showLLMMenu(view)
        }
        
        geofenceButton.setOnClickListener {
            val intent = Intent(this, GeofenceActivity::class.java)
            startActivity(intent)
        }
        
        // Exit icon click handler
        exitIcon.setOnClickListener {
            AppExitManager.showExitConfirmation(this) {
                // Show progress while exiting
                val progressToast = Toast.makeText(this, "Stopping background services...", Toast.LENGTH_LONG)
                progressToast.show()
                
                AppExitManager.exitApp(this) {
                    // Cancel the progress toast
                    progressToast.cancel()
                    
                    // Show completion message
                    Toast.makeText(this, "App stopped successfully", Toast.LENGTH_SHORT).show()
                    
                    // Close the activity after services are stopped
                    finishAffinity() // Closes all activities in the task
                }
            }
        }
        
        // Long press for debug info
        exitIcon.setOnLongClickListener {
            showServiceDebugInfo()
            true
        }
        
        // Initialize background service for workflows
        initializeBackgroundServices()
    }
    
    private fun initializeBackgroundServices() {
        try {
            // Start the background service for workflow execution
            val started = ServiceManager.startBackgroundService(this)
            if (started) {
                Log.d("HomeActivity", "Background service initialized successfully")
            } else {
                Log.w("HomeActivity", "Failed to start background service")
            }
        } catch (e: Exception) {
            Log.e("HomeActivity", "Error initializing background services", e)
        }
    }

    override fun onResume() {
        super.onResume()
        // Update button state when returning to the activity
        updateSignInButtonState()
        
        // Check battery optimization status and prompt if needed
        checkBatteryOptimization()
    }
    
    private fun checkBatteryOptimization() {
        // Only check after basic permissions are granted
        if (permissions.hasPermissions(requiredPermissions)) {
            // Check if user disabled the reminder
            val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
            val reminderDisabled = prefs.getBoolean("battery_reminder_disabled", false)
            
            if (!reminderDisabled && !BackgroundPermissionManager.isIgnoringBatteryOptimizations(this)) {
                // Show a subtle reminder about battery optimization
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    showBatteryOptimizationReminder()
                }, 2000) // Show after 2 seconds
            }
        }
    }
    
    private fun showBatteryOptimizationReminder() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Background Execution")
            .setMessage("For reliable workflow execution, please disable battery optimization for this app.")
            .setPositiveButton("Configure") { _, _ ->
                val intent = Intent(this, BatteryOptimizationActivity::class.java)
                startActivity(intent)
            }
            .setNegativeButton("Later", null)
            .setNeutralButton("Don't ask again") { _, _ ->
                // Store preference to not show again
                getSharedPreferences("app_prefs", MODE_PRIVATE)
                    .edit()
                    .putBoolean("battery_reminder_disabled", true)
                    .apply()
            }
            .show()
    }

    private fun updateSignInButtonState() {
        val signInButton = findViewById<Button>(R.id.signInButton)
        
        if (login.isSignedIn()) {
            signInButton.text = "SIGN OUT"
        } else {
            signInButton.text = "SIGN IN WITH GOOGLE"
        }
    }

    private fun showPipelineMenu(view: android.view.View) {
        val popup = PopupMenu(this, view)
        popup.menuInflater.inflate(R.menu.pipeline_menu, popup.menu)
        
        popup.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.menu_email_to_telegram -> {
                    startActivity(Intent(this, PipelineActivity::class.java))
                    true
                }
                R.id.menu_telegram_to_gmail -> {
                    startActivity(Intent(this, TelegramToGmailActivity::class.java))
                    true
                }
                else -> false
            }
        }
        
        popup.show()
    }

    private fun showNewWorkflowMenu(view: android.view.View) {
        val popup = PopupMenu(this, view)
        popup.menuInflater.inflate(R.menu.new_workflow_menu, popup.menu)
        
        popup.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.menu_create_new_workflow -> {
                    startActivity(Intent(this, NewWorkflowCreateActivity::class.java))
                    true
                }
                R.id.menu_manage_workflows -> {
                    startActivity(Intent(this, NewWorkflowListActivity::class.java))
                    true
                }
                R.id.menu_workflow_help -> {
                    showWorkflowHelp()
                    true
                }
                else -> false
            }
        }
        
        popup.show()
    }
    
    private fun showWorkflowHelp() {
        Toast.makeText(this, """
            New Workflows Features:
            • Create automated workflows
            • Run continuously with intervals
            • Gmail ↔ Telegram integration
            • Enhanced formatting strategies
            • Better error handling
        """.trimIndent(), Toast.LENGTH_LONG).show()
    }

    private fun showLLMMenu(view: android.view.View) {
        val popup = PopupMenu(this, view)
        popup.menuInflater.inflate(R.menu.llm_menu, popup.menu)
        
        popup.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.menu_m1 -> {
                    startActivity(Intent(this, M1Activity::class.java))
                    true
                }
                R.id.menu_m2_multimodal -> {
                    startActivity(Intent(this, MainActivity::class.java))
                    true
                }
                else -> false
            }
        }
        
        popup.show()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 9001) { // Google Sign-In
            login.handleSignInResult(data,
                onSuccess = { account: GoogleSignInAccount ->
                    Toast.makeText(this, "Signed in as: ${account.displayName}", Toast.LENGTH_SHORT).show()
                    updateSignInButtonState()
                },
                onError = { errorMsg ->
                    Toast.makeText(this, errorMsg, Toast.LENGTH_SHORT).show()
                    updateSignInButtonState()
                }
            )
        }
    }
    
    /**
     * Show debug info about background services (triggered by long-press on exit icon)
     */
    private fun showServiceDebugInfo() {
        val isRunning = ServiceManager.isServiceRunning(this)
        val batteryOptimized = !BackgroundPermissionManager.isIgnoringBatteryOptimizations(this)
        
        val debugInfo = buildString {
            appendLine("🔧 Service Debug Info")
            appendLine("━━━━━━━━━━━━━━━━━━━━")
            appendLine("Background Service: ${if (isRunning) "✅ Running" else "❌ Stopped"}")
            appendLine("Battery Optimization: ${if (batteryOptimized) "⚠️ Enabled (May kill service)" else "✅ Disabled"}")
            appendLine("Permissions: ${if (permissions.hasPermissions(requiredPermissions)) "✅ Granted" else "❌ Missing"}")
            appendLine("")
            appendLine("💡 Long press to restart service")
        }
        
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Debug Information")
            .setMessage(debugInfo)
            .setPositiveButton("Restart Service") { _, _ ->
                ServiceManager.startBackgroundService(this)
                Toast.makeText(this, "Service restart requested", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Close", null)
            .setNeutralButton("Open Battery Settings") { _, _ ->
                val intent = Intent(this, BatteryOptimizationActivity::class.java)
                startActivity(intent)
            }
            .show()
    }
}
