package com.example.llmapp

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import androidx.work.WorkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class PipelineActivity : ComponentActivity() {
    private lateinit var telegramService: TelegramService
    private lateinit var gmailService: GmailService
    private lateinit var pipeline: GmailToTelegramPipeline
    private lateinit var model: Model
    
    // UI elements
    private lateinit var emailSubjectEditText: EditText
    private lateinit var emailContentEditText: EditText
    private lateinit var processEmailButton: Button
    private lateinit var processSampleEmailButton: Button
    private lateinit var gmailSignInButton: Button
    private lateinit var fetchEmailsButton: Button
    private lateinit var statusTextView: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var autoProcessSwitch: Switch
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pipeline)
        
        // Initialize services
        telegramService = TelegramService(this)
        gmailService = GmailService(this)
        model = MODEL_TEXT_CLASSIFICATION_MOBILEBERT.copy(llmSupportImage = false)
        
        // Initialize UI components
        initializeUI()
        
        // Initialize Gmail sign-in
        gmailService.initGoogleSignIn(this)
        
        // Check configurations
        checkConfigurationStatus()
        
        // Initialize pipeline if services are ready
        if (telegramService.isLoggedIn()) {
            initializePipeline()
        }
    }
    
    private fun initializeUI() {
        emailSubjectEditText = findViewById(R.id.emailSubjectEditText)
        emailContentEditText = findViewById(R.id.emailContentEditText)
        processEmailButton = findViewById(R.id.processEmailButton)
        processSampleEmailButton = findViewById(R.id.processSampleEmailButton)
        gmailSignInButton = findViewById(R.id.gmailSignInButton)
        fetchEmailsButton = findViewById(R.id.fetchEmailsButton)
        statusTextView = findViewById(R.id.statusTextView)
        progressBar = findViewById(R.id.progressBar)
        autoProcessSwitch = findViewById(R.id.autoProcessSwitch)
        
        // Setup click listeners
        setupListeners()
    }
    
    private fun setupListeners() {
        processEmailButton.setOnClickListener {
            processManualEmail()
        }
        
        processSampleEmailButton.setOnClickListener {
            processSampleEmail()
        }
        
        gmailSignInButton.setOnClickListener {
            signInToGmail()
        }
        
        fetchEmailsButton.setOnClickListener {
            fetchAndProcessEmails()
        }
        
        autoProcessSwitch.setOnCheckedChangeListener { _, isChecked ->
            toggleAutomaticProcessing(isChecked)
        }
    }
    
    private fun checkConfigurationStatus() {
        val gmailStatus = if (gmailService.isSignedIn()) "Connected" else "Not connected"
        val telegramStatus = if (telegramService.isLoggedIn()) "Connected" else "Not configured"
        
        statusTextView.text = "Gmail: $gmailStatus | Telegram: $telegramStatus"
        
        // Update UI based on configuration status
        updateUIBasedOnStatus()
    }
    
    private fun updateUIBasedOnStatus() {
        val isGmailSignedIn = gmailService.isSignedIn()
        val isTelegramConfigured = telegramService.isLoggedIn()
        
        // Enable/disable buttons based on status
        gmailSignInButton.text = if (isGmailSignedIn) "Sign Out" else "Sign In to Gmail"
        fetchEmailsButton.isEnabled = isGmailSignedIn && isTelegramConfigured
        autoProcessSwitch.isEnabled = isGmailSignedIn && isTelegramConfigured
        processEmailButton.isEnabled = isTelegramConfigured
        processSampleEmailButton.isEnabled = isTelegramConfigured
    }
    
    private fun processManualEmail() {
        val subject = emailSubjectEditText.text.toString().trim()
        val content = emailContentEditText.text.toString().trim()
        
        if (subject.isEmpty() || content.isEmpty()) {
            Toast.makeText(this, "Please enter both subject and content", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (!telegramService.isLoggedIn()) {
            Toast.makeText(this, "Please set up Telegram bot first", Toast.LENGTH_LONG).show()
            return
        }
        
        showProgress(true)
        statusTextView.text = "Status: Processing..."
        
        // Process in background
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Use our sequential processing function
                processEmailSequentially(subject, content)
                
                withContext(Dispatchers.Main) {
                    showProgress(false)
                    statusTextView.text = "Status: Email processed and sent to Telegram"
                    
                    // Clear fields
                    emailSubjectEditText.text.clear()
                    emailContentEditText.text.clear()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showProgress(false)
                    statusTextView.text = "Status: Error - ${e.message}"
                    Toast.makeText(this@PipelineActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    
    private fun processSampleEmail() {
        if (!telegramService.isLoggedIn()) {
            Toast.makeText(this, "Please set up Telegram bot first", Toast.LENGTH_LONG).show()
            return
        }
        
        showProgress(true)
        statusTextView.text = "Status: Processing sample email..."
        
        // Process in background
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Create a pipeline with completion callback
                val tempPipeline = GmailToTelegramPipeline(this@PipelineActivity)
                
                tempPipeline.processSampleEmail { success, message ->
                    lifecycleScope.launch(Dispatchers.Main) {
                        showProgress(false)
                        
                        if (success) {
                            statusTextView.text = "Status: Sample email processed and sent to Telegram"
                        } else {
                            statusTextView.text = "Status: Error - $message"
                            Toast.makeText(this@PipelineActivity, "Error: $message", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showProgress(false)
                    statusTextView.text = "Status: Error - ${e.message}"
                    Toast.makeText(this@PipelineActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    
    private fun signInToGmail() {
        if (gmailService.isSignedIn()) {
            // Sign out if already signed in
            gmailService.signOut()
            checkConfigurationStatus()
            Toast.makeText(this, "Signed out from Gmail", Toast.LENGTH_SHORT).show()
        } else {
            // Sign in to Gmail
            showProgress(true)
            try {
                // Log pre-sign-in state
                GmailLoginHelper.logAuthState(this)
                
                gmailService.signIn(this) { success ->
                    showProgress(false)
                    if (success) {
                        Toast.makeText(this, "Gmail sign-in successful", Toast.LENGTH_SHORT).show()
                        // Display detailed status
                        GmailLoginHelper.checkAndDisplayStatus(this)
                    } else {
                        Toast.makeText(this, "Gmail sign-in failed", Toast.LENGTH_SHORT).show()
                    }
                    
                    // Log post-sign-in state
                    GmailLoginHelper.logAuthState(this)
                    
                    checkConfigurationStatus()
                }
            } catch (e: Exception) {
                showProgress(false)
                Log.e("PipelineActivity", "Gmail sign-in error", e)
                Toast.makeText(this, "Gmail sign-in error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
    
    private fun fetchAndProcessEmails() {
        if (!gmailService.isSignedIn() || !telegramService.isLoggedIn()) {
            Toast.makeText(this, "Both Gmail and Telegram must be configured", Toast.LENGTH_LONG).show()
            return
        }
        
        showProgress(true)
        statusTextView.text = "Status: Fetching most recent unread emails..."
        
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val emails = gmailService.getUnreadEmails(3) // Limit to 5 emails
                
                if (emails.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        showProgress(false)
                        statusTextView.text = "Status: No unread emails found"
                    }
                    return@launch
                }
                
                withContext(Dispatchers.Main) {
                    statusTextView.text = "Status: Processing ${emails.size} emails sequentially (newest first)..."
                }
                
                var processedCount = 0
                
                // Process emails one at a time
                for (email in emails) {
                    // Update status on main thread
                    withContext(Dispatchers.Main) {
                        val shortDate = email.date.split(" ").take(4).joinToString(" ") // Extract first part of date for display
                        statusTextView.text = "Status: Processing email ${processedCount + 1}/${emails.size}...\nFrom: ${email.from}\nDate: $shortDate"
                    }
                    
                    // Process email and wait for completion
                    processEmailSequentially(email.subject, email.content, email.from, email.date)
                    
                    // Mark as read after successful processing
                    gmailService.markAsRead(email.id)
                    processedCount++
                    
                    // Update progress on main thread
                    withContext(Dispatchers.Main) {
                        statusTextView.text = "Status: Processed ${processedCount}/${emails.size} emails..."
                    }
                }
                
                withContext(Dispatchers.Main) {
                    showProgress(false)
                    statusTextView.text = "Status: Successfully processed $processedCount emails"
                }
                
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showProgress(false)
                    statusTextView.text = "Status: Error - ${e.message}"
                    Toast.makeText(this@PipelineActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    
    private fun toggleAutomaticProcessing(enabled: Boolean) {
        if (enabled) {
            // Start periodic processing
            pipeline.schedulePeriodicProcessing(15) // Every 15 minutes
            Toast.makeText(this, "Automatic processing enabled (every 15 minutes)", Toast.LENGTH_LONG).show()
        } else {
            // Cancel periodic processing
            WorkManager.getInstance(this).cancelUniqueWork("gmail_processing")
            Toast.makeText(this, "Automatic processing disabled", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun showProgress(show: Boolean) {
        progressBar.visibility = if (show) View.VISIBLE else View.GONE
    }
    
    private fun initializePipeline() {
        pipeline = GmailToTelegramPipeline(
            context = this,
            telegramService = telegramService,
            model = model,
            gmailService = gmailService
        )
    }
    
    /**
     * Process a single email and wait for the LLM processing to complete before returning
     */
    private suspend fun processEmailSequentially(subject: String, content: String, from: String = "", date: String = "") = suspendCancellableCoroutine<Unit> { continuation ->
        val pipeline = GmailToTelegramPipeline(this)
        
        // Set up a completion callback
        val listener = object : GmailToTelegramPipeline.PipelineCompletionListener {
            override fun onComplete(success: Boolean, message: String) {
                if (success) {
                    continuation.resume(Unit) {}
                } else {
                    continuation.resumeWithException(Exception(message))
                }
            }
        }
        
        pipeline.setCompletionListener(listener)
        pipeline.processEmail(subject, content, telegramService, from, date)
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        Log.d("PipelineActivity", "onActivityResult: requestCode=$requestCode, resultCode=$resultCode, data=${data != null}")
        
        // Forward the result to the GmailService
        try {
            gmailService.handleActivityResult(requestCode, resultCode, data)
        } catch (e: Exception) {
            Log.e("PipelineActivity", "Error handling activity result", e)
            Toast.makeText(this, "Sign-in process failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
        
        // Always refresh status after activity results
        checkConfigurationStatus()
    }
    
    override fun onResume() {
        super.onResume()
        checkConfigurationStatus()
    }
}
