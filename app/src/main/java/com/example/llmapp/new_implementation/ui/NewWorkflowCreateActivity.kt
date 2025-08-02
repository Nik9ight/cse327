package com.example.llmapp.new_implementation.ui

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.example.llmapp.R
import com.example.llmapp.new_implementation.integration.NewImplementationBridge
import com.example.llmapp.new_implementation.Workflow
import com.example.llmapp.workflow.WorkflowManager
import kotlinx.coroutines.launch

class NewWorkflowCreateActivity : ComponentActivity() {
    
    private val TAG = "NewWorkflowCreateActivity"
    private lateinit var bridge: NewImplementationBridge
    private lateinit var workflowStorage: NewWorkflowStorage
    
    // UI Components
    private lateinit var workflowNameInput: EditText
    private lateinit var workflowTypeSpinner: Spinner
    private lateinit var intervalInput: EditText
    
    // Gmail to Telegram fields
    private lateinit var gmailSearchQueryInput: EditText
    private lateinit var telegramBotTokenInput: EditText
    private lateinit var telegramChatIdInput: EditText
    private lateinit var llmPromptInputGmail: EditText
    
    // Telegram to Gmail fields
    private lateinit var telegramBotTokenInput2: EditText
    private lateinit var gmailRecipientsInput: EditText
    private lateinit var telegramChatIdInput2: EditText
    private lateinit var llmPromptInputTelegram: EditText
    
    // Layout containers
    private lateinit var gmailToTelegramLayout: LinearLayout
    private lateinit var telegramToGmailLayout: LinearLayout
    
    // Buttons
    private lateinit var saveButton: Button
    private lateinit var testButton: Button
    
    // Edit mode
    private var editMode = false
    private var editingWorkflowId: String? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_new_workflow_create)
        
        bridge = NewImplementationBridge(this)
        workflowStorage = NewWorkflowStorage(this)
        
        // Check for edit mode
        editingWorkflowId = intent.getStringExtra("workflow_id")
        editMode = editingWorkflowId != null
        
        initializeViews()
        setupSpinner()
        setupListeners()
        
        // Load workflow for editing if in edit mode
        if (editMode) {
            loadWorkflowForEditing()
        }
    }
    
    private fun initializeViews() {
        workflowNameInput = findViewById(R.id.workflowNameInput)
        workflowTypeSpinner = findViewById(R.id.workflowTypeSpinner)
        intervalInput = findViewById(R.id.intervalInput)
        
        // Gmail to Telegram
        gmailSearchQueryInput = findViewById(R.id.gmailSearchQueryInput)
        telegramBotTokenInput = findViewById(R.id.telegramBotTokenInput)
        telegramChatIdInput = findViewById(R.id.telegramChatIdInput)
        llmPromptInputGmail = findViewById(R.id.llmPromptInputGmail)
        
        // Telegram to Gmail
        telegramBotTokenInput2 = findViewById(R.id.telegramBotTokenInput2)
        gmailRecipientsInput = findViewById(R.id.gmailRecipientsInput)
        telegramChatIdInput2 = findViewById(R.id.telegramChatIdInput2)
        llmPromptInputTelegram = findViewById(R.id.llmPromptInputTelegram)
        
        // Layouts
        gmailToTelegramLayout = findViewById(R.id.gmailToTelegramLayout)
        telegramToGmailLayout = findViewById(R.id.telegramToGmailLayout)
        
        // Buttons
        saveButton = findViewById(R.id.saveButton)
        testButton = findViewById(R.id.testButton)
        
        // Set default values
        intervalInput.setText("300") // 5 minutes
        gmailSearchQueryInput.setText("is:unread")
        llmPromptInputGmail.setText("Summarize this email briefly")
        llmPromptInputTelegram.setText("Summarize this conversation")
        
        // Update button text based on mode
        saveButton.text = if (editMode) "Update Workflow" else "Save Workflow"
    }
    
    private fun setupSpinner() {
        val workflowTypes = arrayOf("Gmail to Telegram", "Telegram to Gmail")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, workflowTypes)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        workflowTypeSpinner.adapter = adapter
    }
    
    private fun setupListeners() {
        workflowTypeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                updateLayoutVisibility(position)
            }
            
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        
        saveButton.setOnClickListener {
            saveWorkflow()
        }
        
        testButton.setOnClickListener {
            testWorkflow()
        }
    }
    
    private fun updateLayoutVisibility(workflowType: Int) {
        when (workflowType) {
            0 -> { // Gmail to Telegram
                gmailToTelegramLayout.visibility = android.view.View.VISIBLE
                telegramToGmailLayout.visibility = android.view.View.GONE
            }
            1 -> { // Telegram to Gmail
                gmailToTelegramLayout.visibility = android.view.View.GONE
                telegramToGmailLayout.visibility = android.view.View.VISIBLE
            }
        }
    }
    
    private fun validateForm(): Boolean {
        if (workflowNameInput.text.toString().trim().isEmpty()) {
            showError("Workflow name is required")
            return false
        }
        
        val interval = intervalInput.text.toString().toIntOrNull()
        if (interval == null || interval < 60) {
            showError("Interval must be at least 60 seconds")
            return false
        }
        
        when (workflowTypeSpinner.selectedItemPosition) {
            0 -> { // Gmail to Telegram
                if (telegramBotTokenInput.text.toString().trim().isEmpty()) {
                    showError("Telegram bot token is required")
                    return false
                }
                if (telegramChatIdInput.text.toString().trim().isEmpty()) {
                    showError("Telegram chat ID is required")
                    return false
                }
            }
            1 -> { // Telegram to Gmail
                if (telegramBotTokenInput2.text.toString().trim().isEmpty()) {
                    showError("Telegram bot token is required")
                    return false
                }
                if (gmailRecipientsInput.text.toString().trim().isEmpty()) {
                    showError("Gmail recipients are required")
                    return false
                }
                if (telegramChatIdInput2.text.toString().trim().isEmpty()) {
                    showError("Telegram chat ID is required")
                    return false
                }
            }
        }
        
        return true
    }
    
    private fun saveWorkflow() {
        if (!validateForm()) return
        
        lifecycleScope.launch {
            try {
                val savedWorkflow = if (editMode) {
                    // For edit mode, preserve original created time
                    val existing = workflowStorage.getWorkflow(editingWorkflowId!!)
                    createSavedWorkflow().copy(created = existing?.created ?: System.currentTimeMillis())
                } else {
                    createSavedWorkflow()
                }
                
                workflowStorage.saveWorkflow(savedWorkflow)
                
                Log.d(TAG, "Workflow saved: ${savedWorkflow.name} (ID: ${savedWorkflow.id})")
                
                runOnUiThread {
                    val message = if (editMode) "Workflow updated successfully!" else "Workflow saved successfully!"
                    Toast.makeText(this@NewWorkflowCreateActivity, message, Toast.LENGTH_SHORT).show()
                    setResult(RESULT_OK)
                    finish()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save workflow", e)
                runOnUiThread {
                    showError("Failed to save workflow: ${e.message}")
                }
            }
        }
    }
    
    private fun testWorkflow() {
        if (!validateForm()) return
        
        lifecycleScope.launch {
            try {
                val workflow = createWorkflow()
                
                runOnUiThread {
                    Toast.makeText(this@NewWorkflowCreateActivity, "Testing workflow...", Toast.LENGTH_SHORT).show()
                }
                
                val result = bridge.executeWorkflow(workflow)
                
                runOnUiThread {
                    if (result.success) {
                        Toast.makeText(this@NewWorkflowCreateActivity, "Test successful! ✅", Toast.LENGTH_LONG).show()
                    } else {
                        showError("Test failed: ${result.message}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Test workflow failed", e)
                runOnUiThread {
                    showError("Test failed: ${e.message}")
                }
            }
        }
    }
    
    private fun createWorkflow(): Workflow {
        return when (workflowTypeSpinner.selectedItemPosition) {
            0 -> { // Gmail to Telegram
                bridge.createGmailToTelegramWorkflow(
                    telegramBotToken = telegramBotTokenInput.text.toString().trim(),
                    telegramChatId = telegramChatIdInput.text.toString().trim(),
                    gmailSearchQuery = gmailSearchQueryInput.text.toString().trim()
                )
            }
            1 -> { // Telegram to Gmail
                bridge.createTelegramToGmailWorkflow(
                    telegramBotToken = telegramBotTokenInput2.text.toString().trim(),
                    gmailRecipients = gmailRecipientsInput.text.toString()
                        .split(",").map { it.trim() }.filter { it.isNotEmpty() },
                    gmailSender = telegramChatIdInput2.text.toString().trim()
                )
            }
            else -> throw IllegalStateException("Invalid workflow type")
        }
    }
    
    private fun createSavedWorkflow(): SavedWorkflow {
        return SavedWorkflow(
            id = editingWorkflowId ?: System.currentTimeMillis().toString(),
            name = workflowNameInput.text.toString().trim(),
            type = when (workflowTypeSpinner.selectedItemPosition) {
                0 -> WorkflowType.GMAIL_TO_TELEGRAM
                1 -> WorkflowType.TELEGRAM_TO_GMAIL
                else -> WorkflowType.GMAIL_TO_TELEGRAM
            },
            intervalSeconds = intervalInput.text.toString().toInt(),
            configuration = when (workflowTypeSpinner.selectedItemPosition) {
                0 -> SavedWorkflowConfig.GmailToTelegram(
                    gmailSearchQuery = gmailSearchQueryInput.text.toString().trim(),
                    telegramBotToken = telegramBotTokenInput.text.toString().trim(),
                    telegramChatId = telegramChatIdInput.text.toString().trim(),
                    llmPrompt = llmPromptInputGmail.text.toString().trim()
                )
                1 -> SavedWorkflowConfig.TelegramToGmail(
                    telegramBotToken = telegramBotTokenInput2.text.toString().trim(),
                    gmailRecipients = gmailRecipientsInput.text.toString()
                        .split(",").map { it.trim() }.filter { it.isNotEmpty() },
                    gmailSender = telegramChatIdInput2.text.toString().trim(),
                    llmPrompt = llmPromptInputTelegram.text.toString().trim()
                )
                else -> throw IllegalStateException("Invalid workflow type")
            },
            isRunning = false,
            created = System.currentTimeMillis(), // Will be overridden in saveWorkflow() for edit mode
            lastRun = null
        )
    }
    
    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
    
    private fun loadWorkflowForEditing() {
        editingWorkflowId?.let { workflowId ->
            lifecycleScope.launch {
                try {
                    val workflow = workflowStorage.getWorkflow(workflowId)
                    if (workflow != null) {
                        runOnUiThread {
                            populateFormWithWorkflow(workflow)
                        }
                    } else {
                        runOnUiThread {
                            showError("Workflow not found")
                            finish()
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to load workflow for editing", e)
                    runOnUiThread {
                        showError("Failed to load workflow: ${e.message}")
                        finish()
                    }
                }
            }
        }
    }
    
    private fun populateFormWithWorkflow(workflow: SavedWorkflow) {
        workflowNameInput.setText(workflow.name)
        intervalInput.setText(workflow.intervalSeconds.toString())
        
        when (workflow.type) {
            WorkflowType.GMAIL_TO_TELEGRAM -> {
                workflowTypeSpinner.setSelection(0)
                val config = workflow.configuration as SavedWorkflowConfig.GmailToTelegram
                gmailSearchQueryInput.setText(config.gmailSearchQuery)
                telegramBotTokenInput.setText(config.telegramBotToken)
                telegramChatIdInput.setText(config.telegramChatId)
                llmPromptInputGmail.setText(config.llmPrompt)
            }
            WorkflowType.TELEGRAM_TO_GMAIL -> {
                workflowTypeSpinner.setSelection(1)
                val config = workflow.configuration as SavedWorkflowConfig.TelegramToGmail
                telegramBotTokenInput2.setText(config.telegramBotToken)
                gmailRecipientsInput.setText(config.gmailRecipients.joinToString(", "))
                telegramChatIdInput2.setText(config.gmailSender)
                llmPromptInputTelegram.setText(config.llmPrompt)
            }
        }
    }
}
