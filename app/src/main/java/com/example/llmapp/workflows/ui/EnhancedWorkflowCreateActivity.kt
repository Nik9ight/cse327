package com.example.llmapp.workflows.ui

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.example.llmapp.GmailService
import com.example.llmapp.Login
import com.example.llmapp.R
import com.example.llmapp.TelegramService
import com.example.llmapp.workflows.services.ImageWorkflowConfig
import com.example.llmapp.workflows.services.WorkflowType
import com.example.llmapp.workflows.services.DestinationType
import com.example.llmapp.workflows.services.WorkflowConfigManager
import kotlinx.coroutines.launch

/**
 * Enhanced activity for creating and managing image workflows
 * Follows SOLID principles with clear separation of concerns
 */
class EnhancedWorkflowCreateActivity : ComponentActivity() {
    
    companion object {
        private const val TAG = "EnhancedWorkflowCreate"
        const val EXTRA_WORKFLOW_ID = "workflow_id"
        const val EXTRA_IS_EDIT = "is_edit"
    }
    
    private lateinit var workflowConfigManager: WorkflowConfigManager
    private lateinit var telegramService: TelegramService
    private lateinit var gmailService: GmailService
    private lateinit var login: Login
    
    // UI Components
    private lateinit var workflowNameEditText: EditText
    
    // Person forwarding section
    private lateinit var personForwardingCheckBox: CheckBox
    private lateinit var personForwardingSection: LinearLayout
    private lateinit var notifyNonMatchingCheckBox: CheckBox
    
    // Document analysis section
    private lateinit var documentAnalysisCheckBox: CheckBox
    private lateinit var documentAnalysisSection: LinearLayout
    private lateinit var documentTypeSpinner: Spinner
    private lateinit var immediateAnalysisCheckBox: CheckBox
    
    // Daily summary section
    private lateinit var dailySummaryCheckBox: CheckBox
    private lateinit var dailySummarySection: LinearLayout
    private lateinit var summaryTimeEditText: EditText
    private lateinit var clearAfterSummaryCheckBox: CheckBox
    
    // Communication settings
    private lateinit var telegramCheckBox: CheckBox
    private lateinit var gmailCheckBox: CheckBox
    private lateinit var gmailRecipientEditText: EditText
    private lateinit var gmailSection: LinearLayout
    
    // Control buttons
    private lateinit var saveButton: Button
    private lateinit var testButton: Button
    private lateinit var cancelButton: Button
    
    // State
    private var isEditMode = false
    private var editingWorkflowId: String? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_enhanced_workflow_create)
        
        workflowConfigManager = WorkflowConfigManager(this)
        telegramService = TelegramService(this)
        gmailService = GmailService(this)
        login = Login(this)
        
        // Check if we're in edit mode
        isEditMode = intent.getBooleanExtra(EXTRA_IS_EDIT, false)
        editingWorkflowId = intent.getStringExtra(EXTRA_WORKFLOW_ID)
        
        initializeViews()
        setupEventListeners()
        setupSpinners()
        
        if (isEditMode && editingWorkflowId != null) {
            loadExistingWorkflow(editingWorkflowId!!)
            title = "Edit Workflow"
        } else {
            title = "Create New Workflow"
        }
        
        updateUIState()
    }
    
    private fun initializeViews() {
        // Basic workflow info
        workflowNameEditText = findViewById(R.id.workflowNameEditText)
        
        // Person forwarding section
        personForwardingCheckBox = findViewById(R.id.personForwardingCheckBox)
        personForwardingSection = findViewById(R.id.personForwardingSection)
        notifyNonMatchingCheckBox = findViewById(R.id.notifyNonMatchingCheckBox)
        
        // Document analysis section
        documentAnalysisCheckBox = findViewById(R.id.documentAnalysisCheckBox)
        documentAnalysisSection = findViewById(R.id.documentAnalysisSection)
        documentTypeSpinner = findViewById(R.id.documentTypeSpinner)
        immediateAnalysisCheckBox = findViewById(R.id.immediateAnalysisCheckBox)
        
        // Daily summary section
        dailySummaryCheckBox = findViewById(R.id.dailySummaryCheckBox)
        dailySummarySection = findViewById(R.id.dailySummarySection)
        summaryTimeEditText = findViewById(R.id.summaryTimeEditText)
        clearAfterSummaryCheckBox = findViewById(R.id.clearAfterSummaryCheckBox)
        
        // Communication settings
        telegramCheckBox = findViewById(R.id.telegramCheckBox)
        gmailCheckBox = findViewById(R.id.gmailCheckBox)
        gmailRecipientEditText = findViewById(R.id.gmailRecipientEditText)
        gmailSection = findViewById(R.id.gmailSection)
        
        // Control buttons
        saveButton = findViewById(R.id.saveButton)
        testButton = findViewById(R.id.testButton)
        cancelButton = findViewById(R.id.cancelButton)
    }
    
    private fun setupEventListeners() {
        personForwardingCheckBox.setOnCheckedChangeListener { _, isChecked ->
            personForwardingSection.visibility = if (isChecked) View.VISIBLE else View.GONE
            updateUIState()
        }
        
        documentAnalysisCheckBox.setOnCheckedChangeListener { _, isChecked ->
            documentAnalysisSection.visibility = if (isChecked) View.VISIBLE else View.GONE
            dailySummaryCheckBox.isEnabled = isChecked
            if (!isChecked) {
                dailySummaryCheckBox.isChecked = false
                dailySummarySection.visibility = View.GONE
            }
            updateUIState()
        }
        
        dailySummaryCheckBox.setOnCheckedChangeListener { _, isChecked ->
            dailySummarySection.visibility = if (isChecked) View.VISIBLE else View.GONE
        }
        
        gmailCheckBox.setOnCheckedChangeListener { _, isChecked ->
            gmailSection.visibility = if (isChecked) View.VISIBLE else View.GONE
            if (isChecked && !login.isSignedIn()) {
                showGmailSignInPrompt()
            }
        }
        
        saveButton.setOnClickListener { saveWorkflow() }
        testButton.setOnClickListener { testWorkflow() }
        cancelButton.setOnClickListener { finish() }
    }
    
    private fun setupSpinners() {
        // Document type spinner
        val documentTypes = arrayOf(
            "receipts",
            "prescriptions", 
            "id documents",
            "contracts",
            "invoices",
            "forms",
            "other documents"
        )
        
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, documentTypes)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        documentTypeSpinner.adapter = adapter
    }
    
    private fun updateUIState() {
        val hasPersonOrDocument = personForwardingCheckBox.isChecked || documentAnalysisCheckBox.isChecked
        saveButton.isEnabled = hasPersonOrDocument && workflowNameEditText.text.toString().isNotBlank()
        testButton.isEnabled = hasPersonOrDocument
        
        // Update communication requirements
        val needsCommunication = personForwardingCheckBox.isChecked || 
                                documentAnalysisCheckBox.isChecked ||
                                dailySummaryCheckBox.isChecked
        
        if (needsCommunication) {
            findViewById<TextView>(R.id.communicationLabel).visibility = View.VISIBLE
            findViewById<LinearLayout>(R.id.communicationSection).visibility = View.VISIBLE
        } else {
            findViewById<TextView>(R.id.communicationLabel).visibility = View.GONE
            findViewById<LinearLayout>(R.id.communicationSection).visibility = View.GONE
        }
    }
    
    private fun loadExistingWorkflow(workflowId: String) {
        val config = workflowConfigManager.getWorkflow(workflowId)
        if (config == null) {
            Toast.makeText(this, "Workflow not found", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        
        // Load basic info
        workflowNameEditText.setText(config.workflowName)
        
        // Load settings based on new workflow structure
        when (config.workflowType) {
            WorkflowType.IMAGE_FORWARD -> {
                personForwardingCheckBox.isChecked = true
                // For backwards compatibility, assume reference image is set if path exists
                notifyNonMatchingCheckBox.isChecked = false // Default for new structure
            }
            WorkflowType.DOCUMENT_ANALYSIS -> {
                documentAnalysisCheckBox.isChecked = true
                
                // Set document type in spinner
                val documentTypes = (documentTypeSpinner.adapter as ArrayAdapter<String>)
                val typeIndex = (0 until documentTypes.count).find { 
                    documentTypes.getItem(it) == config.documentType 
                } ?: 0
                documentTypeSpinner.setSelection(typeIndex)
                
                // Load daily summary settings (always enabled for document analysis)
                dailySummaryCheckBox.isChecked = true
                summaryTimeEditText.setText(config.scheduledTime)
                clearAfterSummaryCheckBox.isChecked = true // Default
            }
        }
        
        // Load communication settings based on destination type
        when (config.destinationType) {
            DestinationType.TELEGRAM -> {
                telegramCheckBox.isChecked = true
                gmailCheckBox.isChecked = false
            }
            DestinationType.GMAIL -> {
                telegramCheckBox.isChecked = false
                gmailCheckBox.isChecked = true
                gmailRecipientEditText.setText(config.gmailRecipient)
            }
        }
        
        updateUIState()
    }
    
    private fun saveWorkflow() {
        try {
            Log.d(TAG, "Saving workflow configuration")
            
            val workflowName = workflowNameEditText.text.toString().trim()
            if (workflowName.isBlank()) {
                Toast.makeText(this, "Please enter a workflow name", Toast.LENGTH_SHORT).show()
                return
            }
            
            // Determine workflow type based on checkboxes
            val workflowType = when {
                personForwardingCheckBox.isChecked -> WorkflowType.IMAGE_FORWARD
                documentAnalysisCheckBox.isChecked -> WorkflowType.DOCUMENT_ANALYSIS
                else -> {
                    Toast.makeText(this, "Please select at least one workflow type", Toast.LENGTH_SHORT).show()
                    return
                }
            }
            
            // Get document type or default
            val documentType = if (workflowType == WorkflowType.DOCUMENT_ANALYSIS) {
                documentTypeSpinner.selectedItem.toString()
            } else {
                "document"
            }
            
            // Get scheduled time or default
            val scheduledTime = if (workflowType == WorkflowType.DOCUMENT_ANALYSIS) {
                val time = summaryTimeEditText.text.toString().trim().ifBlank { "18:00" }
                if (!isValidTimeFormat(time)) {
                    Toast.makeText(this, "Please enter time in HH:MM format (e.g., 18:00)", Toast.LENGTH_SHORT).show()
                    return
                }
                time
            } else {
                "18:00"
            }
            
            // Determine destination type
            val destinationType = when {
                telegramCheckBox.isChecked -> DestinationType.TELEGRAM
                gmailCheckBox.isChecked -> DestinationType.GMAIL
                else -> {
                    Toast.makeText(this, "Please select a communication method", Toast.LENGTH_SHORT).show()
                    return
                }
            }
            
            val config = ImageWorkflowConfig(
                workflowId = editingWorkflowId ?: java.util.UUID.randomUUID().toString(),
                workflowName = workflowName,
                workflowType = workflowType,
                referenceImagePath = "", // Will be set later in proper implementation
                documentType = documentType,
                scheduledTime = scheduledTime,
                destinationType = destinationType,
                gmailRecipient = if (destinationType == DestinationType.GMAIL) {
                    gmailRecipientEditText.text.toString().trim()
                } else "",
                telegramChatId = if (destinationType == DestinationType.TELEGRAM) {
                    "default_chat_id" // Will be set properly in full implementation
                } else ""
            )
            
            // Validate communication settings
            if (!validateCommunicationSettings(config)) {
                return
            }
            
            val saved = workflowConfigManager.saveWorkflow(config)
            if (saved) {
                Log.d(TAG, "Workflow saved successfully: ${config.workflowName}")
                Toast.makeText(this, "Workflow saved successfully!", Toast.LENGTH_SHORT).show()
                
                val resultIntent = Intent().apply {
                    putExtra("workflow_saved", true)
                    putExtra("workflow_id", config.workflowId)
                }
                setResult(RESULT_OK, resultIntent)
                finish()
            } else {
                Toast.makeText(this, "Failed to save workflow", Toast.LENGTH_SHORT).show()
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error saving workflow", e)
            Toast.makeText(this, "Error saving workflow: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun validateCommunicationSettings(config: ImageWorkflowConfig): Boolean {
        if (config.destinationType == DestinationType.TELEGRAM && !telegramService.isLoggedIn()) {
            Toast.makeText(this, "Please configure Telegram first", Toast.LENGTH_LONG).show()
            return false
        }
        
        if (config.destinationType == DestinationType.GMAIL) {
            if (!login.isSignedIn()) {
                Toast.makeText(this, "Please sign in to Gmail first", Toast.LENGTH_LONG).show()
                return false
            }
            
            if (config.gmailRecipient.isBlank()) {
                Toast.makeText(this, "Please enter Gmail recipient", Toast.LENGTH_SHORT).show()
                return false
            }
            
            if (!android.util.Patterns.EMAIL_ADDRESS.matcher(config.gmailRecipient).matches()) {
                Toast.makeText(this, "Please enter valid email address", Toast.LENGTH_SHORT).show()
                return false
            }
        }
        
        return true
    }
    
    private fun testWorkflow() {
        lifecycleScope.launch {
            try {
                Toast.makeText(this@EnhancedWorkflowCreateActivity, "Testing workflow configuration...", Toast.LENGTH_SHORT).show()
                
                // Test Telegram if enabled
                if (telegramCheckBox.isChecked && telegramService.isLoggedIn()) {
                    telegramService.testConnection(
                        onSuccess = {
                            runOnUiThread {
                                Toast.makeText(this@EnhancedWorkflowCreateActivity, "✅ Telegram connection successful", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onError = { error ->
                            runOnUiThread {
                                Toast.makeText(this@EnhancedWorkflowCreateActivity, "❌ Telegram test failed: $error", Toast.LENGTH_LONG).show()
                            }
                        }
                    )
                }
                
                // Test Gmail if enabled
                if (gmailCheckBox.isChecked && login.isSignedIn()) {
                    val recipient = gmailRecipientEditText.text.toString().trim()
                    if (recipient.isNotBlank()) {
                        val testSent = gmailService.sendEmail(
                            to = listOf(recipient),
                            subject = "LLMAPP Workflow Test",
                            body = "<h3>Test Email</h3><p>This is a test email from your LLMAPP workflow configuration.</p><p>Time: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}</p>",
                            isHtml = true
                        )
                        
                        runOnUiThread {
                            if (testSent) {
                                Toast.makeText(this@EnhancedWorkflowCreateActivity, "✅ Gmail test email sent", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(this@EnhancedWorkflowCreateActivity, "❌ Gmail test failed", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error testing workflow", e)
                runOnUiThread {
                    Toast.makeText(this@EnhancedWorkflowCreateActivity, "Test error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    
    private fun showGmailSignInPrompt() {
        if (!login.isSignedIn()) {
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Gmail Sign In Required")
                .setMessage("To use Gmail for notifications, you need to sign in first. Would you like to sign in now?")
                .setPositiveButton("Sign In") { _, _ ->
                    // User needs to go to home activity to sign in
                    Toast.makeText(this, "Please use the 'Sign In' button on the home screen first", Toast.LENGTH_LONG).show()
                }
                .setNegativeButton("Later") { _, _ ->
                    gmailCheckBox.isChecked = false
                    gmailSection.visibility = View.GONE
                }
                .show()
        }
    }
    
    private fun isValidTimeFormat(time: String): Boolean {
        return try {
            val parts = time.split(":")
            if (parts.size != 2) return false
            
            val hours = parts[0].toInt()
            val minutes = parts[1].toInt()
            
            hours in 0..23 && minutes in 0..59
        } catch (e: Exception) {
            false
        }
    }
}
