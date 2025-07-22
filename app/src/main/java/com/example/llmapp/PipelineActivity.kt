package com.example.llmapp

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import androidx.work.WorkManager
import com.example.llmapp.Model
import com.example.llmapp.MODEL_TEXT_CLASSIFICATION_MOBILEBERT
import com.example.llmapp.patterns.PipelineObserver
import com.example.llmapp.pipeline.*
import kotlinx.coroutines.launch

/**
 * Activity for Gmail to Telegram Pipeline - rebuilt with modular design patterns
 * Fetches unread emails from Gmail, processes them with local LLM,
 * and sends the processed output to Telegram.
 * 
 * Uses:
 * - Strategy Pattern: For different processing strategies
 * - Command Pattern: For email processing commands 
 * - Observer Pattern: For notifications
 * - Chain of Responsibility: For email processing pipeline
 * - Factory Pattern: For creating service components
 */
class PipelineActivity : ComponentActivity(), GmailToTelegramPipeline.PipelineCompletionListener, PipelineObserver {
    private lateinit var gmailService: GmailService
    private lateinit var telegramService: TelegramService
    private lateinit var pipeline: GmailToTelegramPipeline
    private lateinit var model: Model
    
    // UI elements - Workflow Steps
    private lateinit var workflowInstructionText: TextView
    private lateinit var statusTextView: TextView
    private lateinit var progressBar: ProgressBar
    
    // Step 1
    private lateinit var step1Card: View
    private lateinit var gmailSignInButton: Button
    private lateinit var step1StatusText: TextView
    
    // Step 2
    private lateinit var step2Card: View
    private lateinit var searchQueryButton: Button
    private lateinit var step2StatusText: TextView
    
    // Step 3
    private lateinit var step3Card: View
    private lateinit var telegramSetupButton: Button
    private lateinit var step3StatusText: TextView
    
    // Step 4
    private lateinit var step4Card: View
    private lateinit var runWorkflowButton: Button
    private lateinit var step4StatusText: TextView
    private lateinit var autoProcessSwitch: Switch
    
    // Advanced options
    private lateinit var toggleAdvancedOptionsButton: Button
    private lateinit var advancedOptionsLayout: View
    private lateinit var processSampleEmailButton: Button
    
    // Workflow state tracking
    private var step1Completed = false
    private var step2Completed = false
    private var step3Completed = false
    private var currentSearchQuery: String? = null
    
    // Constants
    private val REQUEST_GMAIL_SIGN_IN = 1001
    private val REQUEST_TELEGRAM_SETUP = 1002
    private val REQUEST_SEARCH_QUERY = 1003
    private val TAG = "PipelineActivity"
    
    /**
     * Initialize the workflow UI elements
     */
    private fun initializeWorkflowUI() {
        // Basic UI elements
        workflowInstructionText = findViewById(R.id.workflowInstructionText)
        statusTextView = findViewById(R.id.statusTextView)
        progressBar = findViewById(R.id.progressBar)
        
        // Step 1: Gmail Sign In
        step1Card = findViewById(R.id.step1Card)
        gmailSignInButton = findViewById(R.id.gmailSignInButton)
        step1StatusText = findViewById(R.id.step1StatusText)
        
        // Step 2: Search Query
        step2Card = findViewById(R.id.step2Card)
        searchQueryButton = findViewById(R.id.searchQueryButton)
        step2StatusText = findViewById(R.id.step2StatusText)
        
        // Step 3: Telegram Setup
        step3Card = findViewById(R.id.step3Card)
        telegramSetupButton = findViewById(R.id.telegramSetupButton)
        step3StatusText = findViewById(R.id.step3StatusText)
        
        // Step 4: Run Workflow
        step4Card = findViewById(R.id.step4Card)
        runWorkflowButton = findViewById(R.id.runWorkflowButton)
        step4StatusText = findViewById(R.id.step4StatusText)
        autoProcessSwitch = findViewById(R.id.autoProcessSwitch)
        
        // Advanced options
        toggleAdvancedOptionsButton = findViewById(R.id.toggleAdvancedOptionsButton)
        advancedOptionsLayout = findViewById(R.id.advancedOptionsLayout)
        processSampleEmailButton = findViewById(R.id.processSampleEmailButton)
        
        // Set up click listeners
        setupClickListeners()
    }
    
    /**
     * Set up click listeners for workflow steps
     */
    private fun setupClickListeners() {
        // Step 1: Gmail Sign In
        gmailSignInButton.setOnClickListener {
            startGmailSignIn()
        }
        
        // Step 2: Search Query
        searchQueryButton.setOnClickListener {
            startSearchQueryActivity()
        }
        
        // Step 3: Telegram Setup
        telegramSetupButton.setOnClickListener {
            startTelegramSetup()
        }
        
        // Step 4: Run Workflow
        runWorkflowButton.setOnClickListener {
            runWorkflow()
        }
        
        // Toggle advanced options
        toggleAdvancedOptionsButton.setOnClickListener {
            toggleAdvancedOptions()
        }
        
        // Process sample email
        processSampleEmailButton.setOnClickListener {
            processSampleEmail()
        }
        
        // Auto process switch
        autoProcessSwitch.setOnCheckedChangeListener { _, isChecked ->
            toggleAutomaticProcessing(isChecked)
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pipeline)
        
        // Initialize direct service instances
        gmailService = GmailService(this)
        telegramService = TelegramService(this)
        model = MODEL_TEXT_CLASSIFICATION_MOBILEBERT.copy(llmSupportImage = false)
        
        // Initialize UI components
        initializeWorkflowUI()
        
        // Initialize pipeline
        initializePipeline()
        
        // Check existing configurations
        checkExistingConfigurations()
    }
    
    /**
     * Initialize the pipeline with required components
     */
    private fun initializePipeline() {
        // Initialize Gmail sign-in
        gmailService.initGoogleSignIn(this)
        
        // Create the pipeline
        pipeline = GmailToTelegramPipeline(
            context = this,
            telegramService = telegramService,
            model = model,
            gmailService = gmailService
        )
        
        // Set this activity as the completion listener
        pipeline.setCompletionListener(this)
        
        // Register this activity as a pipeline observer
        pipeline.addObserver(this)
    }
    
    /**
     * Check for existing configurations on startup
     */
    private fun checkExistingConfigurations() {
        lifecycleScope.launch {
            statusTextView.text = "Checking configurations..."
            progressBar.visibility = View.VISIBLE

            // Check Gmail sign-in
            if (gmailService.isSignedIn()) {
                completeStep1()
            }

            // Check Telegram setup
            if (telegramService.isLoggedIn()) {
                completeStep3()
            }

            // Update UI based on current state
            updateWorkflowUI()
            
            progressBar.visibility = View.GONE
            statusTextView.text = "Ready to start workflow"
        }
    }
    
    /**
     * Start Gmail Sign In process (Step 1)
     */
    private fun startGmailSignIn() {
        step1StatusText.text = "Signing in..."
        gmailService.signIn(this) { success ->
            runOnUiThread {
                if (success) {
                    completeStep1()
                } else {
                    step1StatusText.text = "Sign-in failed. Try again."
                    Toast.makeText(this, "Gmail sign-in failed", Toast.LENGTH_SHORT).show()
                }
                updateWorkflowUI()
            }
        }
    }
    
    /**
     * Start Search Query activity (Step 2)
     */
    private fun startSearchQueryActivity() {
        val intent = Intent(this, EmailSearchActivity::class.java)
        startActivityForResult(intent, REQUEST_SEARCH_QUERY)
    }
    
    /**
     * Start Telegram Setup (Step 3)
     */
    private fun startTelegramSetup() {
        val intent = Intent(this, TelegramLoginActivity::class.java)
        startActivityForResult(intent, REQUEST_TELEGRAM_SETUP)
    }
    
    /**
     * Run the workflow with current configuration (Step 4)
     */
    private fun runWorkflow() {
        if (!validateWorkflowReadiness()) {
            return
        }
        
        statusTextView.text = "Running workflow..."
        progressBar.visibility = View.VISIBLE
        runWorkflowButton.isEnabled = false
        
        // Apply search query if exists
        val fetchService = (pipeline.getEmailFetchService() as? GmailFetchService)
        fetchService?.getSearchQuery()?.let {
            statusTextView.text = "Running workflow with query: $it"
        }
        
        // Run the pipeline
        pipeline.process()
    }
        /**
     * Run the workflow with current configuration (Step 4)
     */
    // private fun runWorkflow() {
    //     if (!validateWorkflowReadiness()) {
    //         return
    //     }
        
    //     statusTextView.text = "Running workflow..."
    //     progressBar.visibility = View.VISIBLE
    //     runWorkflowButton.isEnabled = false
        
    //     // Check if we have a search query to use
    //     if (currentSearchQuery != null && currentSearchQuery!!.isNotBlank()) {
    //         statusTextView.text = "Running workflow with query: $currentSearchQuery"
            
    //         // Process emails with search
    //         if (pipeline is EmailProcessingPipeline) {
    //             (pipeline as EmailProcessingPipeline).processEmailsWithSearch(currentSearchQuery!!, 5)
    //         } else if (pipeline is GmailToTelegramPipeline) {
    //             val fetchService = pipeline.getEmailFetchService() as? GmailFetchService
    //             fetchService?.setSearchQuery(currentSearchQuery!!)
    //             pipeline.process()
    //         }
    //     } else {
    //         // Use regular processing
    //         pipeline.process()
    //     }
    // }
    

    /**
     * Mark step 1 as completed
     */
    private fun completeStep1() {
        step1Completed = true
        step1StatusText.text = "Signed in to Gmail"
        gmailSignInButton.text = "Re-authenticate Gmail"
    }
    
    /**
     * Mark step 2 as completed
     */
    private fun completeStep2(query: String?) {
        step2Completed = true
        currentSearchQuery = query
        val displayQuery = if (query.isNullOrBlank()) "Default (unread emails)" else query
        step2StatusText.text = "Search query set: $displayQuery"
        searchQueryButton.text = "Change Search Query"
    }
    
    /**
     * Mark step 3 as completed
     */
    private fun completeStep3() {
        step3Completed = true
        step3StatusText.text = "Telegram bot configured"
        telegramSetupButton.text = "Re-configure Telegram"
    }
    
    /**
     * Update the UI based on the current workflow state
     */
    private fun updateWorkflowUI() {
        // Step 2 availability
        step2Card.alpha = if (step1Completed) 1.0f else 0.5f
        searchQueryButton.isEnabled = step1Completed
        step2StatusText.text = if (step1Completed && !step2Completed) 
            "Ready to set search query" else if (!step1Completed) 
            "Complete Step 1 first" else 
            step2StatusText.text
            
        // Step 3 availability
        step3Card.alpha = if (step1Completed) 1.0f else 0.5f
        telegramSetupButton.isEnabled = step1Completed
        step3StatusText.text = if (step1Completed && !step3Completed) 
            "Ready to configure Telegram" else if (!step1Completed) 
            "Complete Step 1 first" else 
            step3StatusText.text
            
        // Step 4 availability
        val step4Ready = step1Completed && step3Completed // Step 2 is optional
        step4Card.alpha = if (step4Ready) 1.0f else 0.5f
        runWorkflowButton.isEnabled = step4Ready
        autoProcessSwitch.isEnabled = step4Ready
        step4StatusText.text = if (step4Ready) 
            "Ready to run workflow" else 
            "Complete previous steps first"
    }
    
    /**
     * Validate if the workflow is ready to run
     */
    private fun validateWorkflowReadiness(): Boolean {
        if (!step1Completed) {
            Toast.makeText(this, "Please sign in to Gmail first", Toast.LENGTH_SHORT).show()
            return false
        }
        
        if (!step3Completed) {
            Toast.makeText(this, "Please configure Telegram first", Toast.LENGTH_SHORT).show()
            return false
        }
        
        return true
    }
    
    /**
     * Toggle advanced options visibility
     */
    private fun toggleAdvancedOptions() {
        val isVisible = advancedOptionsLayout.visibility == View.VISIBLE
        advancedOptionsLayout.visibility = if (isVisible) View.GONE else View.VISIBLE
        toggleAdvancedOptionsButton.text = if (isVisible) 
            "Show Advanced Options" else 
            "Hide Advanced Options"
    }
    
    /**
     * Process a sample email for testing
     */
    private fun processSampleEmail() {
        if (!validateWorkflowReadiness()) {
            return
        }
        
        statusTextView.text = "Processing sample email..."
        progressBar.visibility = View.VISIBLE
        
        pipeline.processSampleEmail { success, message ->
            runOnUiThread {
                statusTextView.text = message
                progressBar.visibility = View.GONE
            }
        }
    }
    
    /**
     * Toggle automatic processing via WorkManager
     */
    private fun toggleAutomaticProcessing(isChecked: Boolean) {
        if (isChecked) {
            if (!validateWorkflowReadiness()) {
                autoProcessSwitch.isChecked = false
                return
            }
            
            // Start periodic processing
            pipeline.schedulePeriodicProcessing(15) // Every 15 minutes
            Toast.makeText(this, "Automatic processing enabled (every 15 minutes)", Toast.LENGTH_LONG).show()
        } else {
            // Cancel periodic processing
            WorkManager.getInstance(this).cancelAllWorkByTag("email_processing")
            Toast.makeText(this, "Automatic processing disabled", Toast.LENGTH_SHORT).show()
        }
    }
    
    // Implement PipelineCompletionListener interface
    override fun onComplete(success: Boolean, message: String) {
        runOnUiThread {
            progressBar.visibility = View.GONE
            statusTextView.text = if (success) "Operation completed successfully" else "Error: $message"
            runWorkflowButton.isEnabled = true
            
            if (!success) {
                Toast.makeText(this, "Error: $message", Toast.LENGTH_LONG).show()
            }
        }
    }
    
    // Implement PipelineObserver interface
    override fun onEmailFetched(emailId: String, subject: String) {
        runOnUiThread {
            statusTextView.text = "Status: Email fetched - $subject"
        }
    }
    
    override fun onProcessingStarted(emailId: String, subject: String) {
        runOnUiThread {
            statusTextView.text = "Status: Processing - $subject"
        }
    }
    
    override fun onProcessingComplete(emailId: String, subject: String, summary: String) {
        runOnUiThread {
            statusTextView.text = "Status: Processing complete - $subject"
        }
    }
    
    override fun onEmailSent(emailId: String, subject: String, success: Boolean, message: String) {
        runOnUiThread {
            val status = if (success) "sent" else "failed"
            statusTextView.text = "Status: Email $status - $subject"
        }
    }
    
    override fun onBatchComplete(successCount: Int, failureCount: Int) {
        runOnUiThread {
            val total = successCount + failureCount
            statusTextView.text = "Status: Completed batch - $successCount/$total successful"
        }
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        Log.d(TAG, "onActivityResult: requestCode=$requestCode, resultCode=$resultCode, data=${data != null}")
        
        when (requestCode) {
            REQUEST_GMAIL_SIGN_IN -> {
                // Forward the result to the GmailService
                try {
                    gmailService.handleActivityResult(requestCode, resultCode, data)
                    
                    if (gmailService.isSignedIn()) {
                        completeStep1()
                        updateWorkflowUI()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error handling Gmail sign-in result", e)
                    Toast.makeText(this, "Sign-in process failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
            // REQUEST_SEARCH_QUERY -> {
            //     if (resultCode == RESULT_OK) {
            //         val query = data?.getStringExtra("search_query")
            //         completeStep2(query)
            //         updateWorkflowUI()
            //     }
            // }
            REQUEST_SEARCH_QUERY -> {
                if (resultCode == Activity.RESULT_OK && data != null) {
                    val query = data.getStringExtra("search_query")
                    currentSearchQuery = query

                    // Display the query in the UI
                    val displayQuery = if (query.isNullOrBlank()) "Default (unread emails)" else query
                    step2StatusText.text = "Search query set: $displayQuery"
                    searchQueryButton.text = "Change Search Query"

                    // Store the query in the fetch service
                    val fetchService = pipeline.getEmailFetchService() as? GmailFetchService
                    fetchService?.setSearchQuery(query)

                    // Mark step 2 as completed
                    completeStep2(query)
                    updateWorkflowUI()

                    Toast.makeText(this, "Search query updated", Toast.LENGTH_SHORT).show()
                } else if (resultCode == Activity.RESULT_CANCELED) {
                    // User cancelled - no changes needed
                    Log.d(TAG, "Search query selection cancelled")
                }
            }
            REQUEST_TELEGRAM_SETUP -> {
                if (resultCode == RESULT_OK) {
                    completeStep3()
                    updateWorkflowUI()
                }
            }
        }
    }
    
    override fun onResume() {
        super.onResume()
        
        // Check configurations when resuming
        if (::gmailService.isInitialized && ::telegramService.isInitialized) {
            lifecycleScope.launch {
                // Check Gmail sign-in
                if (gmailService.isSignedIn() && !step1Completed) {
                    completeStep1()
                }

                // Check Telegram setup
                if (telegramService.isLoggedIn() && !step3Completed) {
                    completeStep3()
                }

                // Update UI based on current state
                updateWorkflowUI()
            }
        }
    }
}
