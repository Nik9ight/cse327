package com.example.llmapp

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.example.llmapp.workflow.*
import com.example.llmapp.utils.ServiceManager
import kotlinx.coroutines.launch

class WorkflowCreateActivity : ComponentActivity() {
    
    companion object {
        private const val TAG = "WorkflowCreateActivity"
    }
    
    // UI Components
    private lateinit var titleText: TextView
    private lateinit var nameInput: EditText
    private lateinit var descriptionInput: EditText
    private lateinit var typeSpinner: Spinner
    private lateinit var templateSpinner: Spinner
    private lateinit var configurationLayout: LinearLayout
    private lateinit var saveButton: Button
    private lateinit var cancelButton: Button
    private lateinit var testButton: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var statusText: TextView
    
    // Gmail to Telegram configuration views
    private lateinit var gmailToTelegramLayout: LinearLayout
    private lateinit var gmailSearchQueryInput: EditText
    private lateinit var telegramBotTokenInput: EditText
    private lateinit var telegramChatIdInput: EditText
    private lateinit var llmPromptInput: EditText
    private lateinit var emailLimitInput: EditText
    
    // Telegram to Gmail configuration views
    private lateinit var telegramToGmailLayout: LinearLayout
    private lateinit var telegramBotTokenInput2: EditText
    private lateinit var telegramChatIdInput2: EditText
    private lateinit var gmailRecipientsInput: EditText
    private lateinit var gmailSenderInput: EditText
    private lateinit var llmPromptInput2: EditText
    private lateinit var messageLimitInput: EditText
    private lateinit var emailTemplateSpinner: Spinner
    
    // State
    private var editMode = false
    private var editingWorkflow: SavedWorkflow? = null
    private lateinit var workflowManager: WorkflowManager
    private var selectedWorkflowType = WorkflowType.GMAIL_TO_TELEGRAM
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_workflow_create)
        
        initializeViews()
        initializeWorkflowManager()
        setupSpinners()
        setupListeners()
        handleIntentData()
        updateUI()
        
        Log.d(TAG, "WorkflowCreateActivity created")
    }
    
    private fun initializeViews() {
        titleText = findViewById(R.id.titleText)
        nameInput = findViewById(R.id.workflowNameInput)
        descriptionInput = findViewById(R.id.workflowDescriptionInput)
        typeSpinner = findViewById(R.id.workflowTypeSpinner)
        templateSpinner = findViewById(R.id.workflowTemplateSpinner)
        configurationLayout = findViewById(R.id.configurationLayout)
        saveButton = findViewById(R.id.saveWorkflowButton)
        cancelButton = findViewById(R.id.cancelButton)
        testButton = findViewById(R.id.testWorkflowButton)
        progressBar = findViewById(R.id.progressBar)
        statusText = findViewById(R.id.statusText)
        
        // Gmail to Telegram configuration
        gmailToTelegramLayout = findViewById(R.id.gmailToTelegramLayout)
        gmailSearchQueryInput = findViewById(R.id.gmailSearchQueryInput)
        telegramBotTokenInput = findViewById(R.id.telegramBotTokenInput)
        telegramChatIdInput = findViewById(R.id.telegramChatIdInput)
        llmPromptInput = findViewById(R.id.llmPromptInput)
        emailLimitInput = findViewById(R.id.emailLimitInput)
        
        // Telegram to Gmail configuration
        telegramToGmailLayout = findViewById(R.id.telegramToGmailLayout)
        telegramBotTokenInput2 = findViewById(R.id.telegramBotTokenInput2)
        telegramChatIdInput2 = findViewById(R.id.telegramChatIdInput2)
        gmailRecipientsInput = findViewById(R.id.gmailRecipientsInput)
        gmailSenderInput = findViewById(R.id.gmailSenderInput)
        llmPromptInput2 = findViewById(R.id.llmPromptInput2)
        messageLimitInput = findViewById(R.id.messageLimitInput)
        emailTemplateSpinner = findViewById(R.id.emailTemplateSpinner)
    }
    
    private fun initializeWorkflowManager() {
        workflowManager = WorkflowManager(this)
    }
    
    private fun setupSpinners() {
        // Workflow type spinner
        val typeAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            WorkflowType.values().map { it.getDisplayName() }
        )
        typeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        typeSpinner.adapter = typeAdapter
        
        // Template spinner
        val templates = WorkflowTemplate.getDefaultTemplates()
        val templateAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            listOf("Custom") + templates.map { it.name }
        )
        templateAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        templateSpinner.adapter = templateAdapter
        
        // Email template spinner
        val emailTemplateAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            listOf("Standard", "Compact", "Detailed")
        )
        emailTemplateAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        emailTemplateSpinner.adapter = emailTemplateAdapter
    }
    
    private fun setupListeners() {
        typeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedWorkflowType = WorkflowType.values()[position]
                updateConfigurationLayout()
                updateTemplateSpinner()
            }
            
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        
        templateSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (position > 0) { // Not "Custom"
                    val templates = WorkflowTemplate.getDefaultTemplates()
                    val template = templates[position - 1]
                    applyTemplate(template)
                }
            }
            
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        
        saveButton.setOnClickListener {
            saveWorkflow()
        }
        
        cancelButton.setOnClickListener {
            finish()
        }
        
        testButton.setOnClickListener {
            testWorkflow()
        }
        
        // Add text watchers for validation
        setupValidation()
    }
    
    private fun isValidGmailSearchQuery(query: String): Boolean {
        if (query.isBlank()) return true // Allow empty for default behavior
        
        // Common Gmail search operators
        val validOperators = listOf(
            "from:", "to:", "subject:", "has:", "is:", "in:", "cc:", "bcc:",
            "filename:", "label:", "category:", "before:", "after:", "older:",
            "newer:", "larger:", "smaller:", "size:", "deliveredto:"
        )
        
        // Check for basic Gmail search syntax
        val cleanQuery = query.trim().lowercase()
        
        // Allow simple text searches
        if (!cleanQuery.contains(":")) return true
        
        // Check if it contains valid operators
        val hasValidOperator = validOperators.any { operator ->
            cleanQuery.contains(operator)
        }
        
        // Check for common invalid patterns
        val invalidPatterns = listOf(
            ":::", // Multiple colons
            "::", // Double colons
            "from:from:", // Duplicate operators
            "to:to:", // Duplicate operators
        )
        
        val hasInvalidPattern = invalidPatterns.any { pattern ->
            cleanQuery.contains(pattern)
        }
        
        // Additional validation for specific operators
        val isValidSpecificFormat = when {
            cleanQuery.contains("is:") -> {
                val validIsValues = listOf("unread", "read", "starred", "important", "spam", "trash", "sent")
                val isValues = extractOperatorValues(cleanQuery, "is:")
                isValues.all { it in validIsValues }
            }
            cleanQuery.contains("has:") -> {
                val validHasValues = listOf("attachment", "drive", "document", "spreadsheet", "presentation", "pdf", "youtube")
                val hasValues = extractOperatorValues(cleanQuery, "has:")
                hasValues.all { it in validHasValues }
            }
            cleanQuery.contains("in:") -> {
                val validInValues = listOf("inbox", "sent", "spam", "trash", "drafts", "important", "starred", "chats")
                val inValues = extractOperatorValues(cleanQuery, "in:")
                inValues.all { it in validInValues }
            }
            else -> true
        }
        
        return hasValidOperator && !hasInvalidPattern && isValidSpecificFormat
    }
    
    private fun extractOperatorValues(query: String, operator: String): List<String> {
        val pattern = "$operator(\\S+)".toRegex()
        return pattern.findAll(query).map { it.groupValues[1] }.toList()
    }
    
    private fun getGmailSearchQueryError(query: String): String? {
        if (query.isBlank()) return null
        
        val cleanQuery = query.trim().lowercase()
        
        // Check for invalid patterns
        when {
            cleanQuery.contains(":::") -> return "Invalid syntax: Too many colons (:::)"
            cleanQuery.contains("::") -> return "Invalid syntax: Double colons (::)"
            cleanQuery.contains("from:from:") -> return "Invalid syntax: Duplicate 'from:' operator"
            cleanQuery.contains("to:to:") -> return "Invalid syntax: Duplicate 'to:' operator"
        }
        
        // Check for valid operators if colons are present
        if (cleanQuery.contains(":")) {
            val validOperators = listOf(
                "from:", "to:", "subject:", "has:", "is:", "in:", "cc:", "bcc:",
                "filename:", "label:", "category:", "before:", "after:", "older:",
                "newer:", "larger:", "smaller:", "size:", "deliveredto:"
            )
            
            val hasValidOperator = validOperators.any { operator ->
                cleanQuery.contains(operator)
            }
            
            if (!hasValidOperator) {
                return "Invalid Gmail search operator. Use operators like: from:, to:, subject:, is:, has:, in:, etc."
            }
        }
        
        // Validate specific operator values
        when {
            cleanQuery.contains("is:") -> {
                val validIsValues = listOf("unread", "read", "starred", "important", "spam", "trash", "sent")
                val isValues = extractOperatorValues(cleanQuery, "is:")
                val invalidValues = isValues.filter { it !in validIsValues }
                if (invalidValues.isNotEmpty()) {
                    return "Invalid 'is:' values: ${invalidValues.joinToString(", ")}. Valid values: ${validIsValues.joinToString(", ")}"
                }
            }
            cleanQuery.contains("has:") -> {
                val validHasValues = listOf("attachment", "drive", "document", "spreadsheet", "presentation", "pdf", "youtube")
                val hasValues = extractOperatorValues(cleanQuery, "has:")
                val invalidValues = hasValues.filter { it !in validHasValues }
                if (invalidValues.isNotEmpty()) {
                    return "Invalid 'has:' values: ${invalidValues.joinToString(", ")}. Valid values: ${validHasValues.joinToString(", ")}"
                }
            }
            cleanQuery.contains("in:") -> {
                val validInValues = listOf("inbox", "sent", "spam", "trash", "drafts", "important", "starred", "chats")
                val inValues = extractOperatorValues(cleanQuery, "in:")
                val invalidValues = inValues.filter { it !in validInValues }
                if (invalidValues.isNotEmpty()) {
                    return "Invalid 'in:' values: ${invalidValues.joinToString(", ")}. Valid values: ${validInValues.joinToString(", ")}"
                }
            }
        }
        
        return null
    }
    
    private fun setupValidation() {
        val textWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                validateForm()
            }
        }
        
        nameInput.addTextChangedListener(textWatcher)
        gmailSearchQueryInput.addTextChangedListener(textWatcher)
        telegramBotTokenInput.addTextChangedListener(textWatcher)
        telegramChatIdInput.addTextChangedListener(textWatcher)
        telegramBotTokenInput2.addTextChangedListener(textWatcher)
        telegramChatIdInput2.addTextChangedListener(textWatcher)
        gmailRecipientsInput.addTextChangedListener(textWatcher)
        gmailSenderInput.addTextChangedListener(textWatcher)
    }
    
    private fun handleIntentData() {
        editMode = intent.getBooleanExtra("edit_mode", false)
        editingWorkflow = intent.getParcelableExtra("workflow")
        
        if (editMode && editingWorkflow != null) {
            populateFromWorkflow(editingWorkflow!!)
        }
    }
    
    private fun updateUI() {
        titleText.text = if (editMode) "Edit Workflow" else "Create New Workflow"
        saveButton.text = if (editMode) "Update Workflow" else "Create Workflow"
    }
    
    private fun updateConfigurationLayout() {
        when (selectedWorkflowType) {
            WorkflowType.GMAIL_TO_TELEGRAM -> {
                gmailToTelegramLayout.visibility = View.VISIBLE
                telegramToGmailLayout.visibility = View.GONE
            }
            WorkflowType.TELEGRAM_TO_GMAIL -> {
                gmailToTelegramLayout.visibility = View.GONE
                telegramToGmailLayout.visibility = View.VISIBLE
            }
        }
        validateForm()
    }
    
    private fun updateTemplateSpinner() {
        val templates = WorkflowTemplate.getDefaultTemplates()
        val filteredTemplates = templates.filter { it.type == selectedWorkflowType }
        
        val templateAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            listOf("Custom") + filteredTemplates.map { it.name }
        )
        templateAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        templateSpinner.adapter = templateAdapter
    }
    
    private fun applyTemplate(template: WorkflowTemplate) {
        nameInput.setText(template.name)
        
        when (val config = template.defaultConfiguration) {
            is GmailToTelegramConfig -> {
                gmailSearchQueryInput.setText(config.gmailSearchQuery)
                llmPromptInput.setText(config.llmPrompt)
                emailLimitInput.setText(config.emailLimit.toString())
            }
            is TelegramToGmailConfig -> {
                llmPromptInput2.setText(config.llmPrompt)
                messageLimitInput.setText(config.messageLimit.toString())
                
                val templateIndex = when (config.emailTemplate.uppercase()) {
                    "COMPACT" -> 1
                    "DETAILED" -> 2
                    else -> 0
                }
                emailTemplateSpinner.setSelection(templateIndex)
            }
        }
    }
    
    private fun populateFromWorkflow(workflow: SavedWorkflow) {
        nameInput.setText(workflow.name)
        descriptionInput.setText(workflow.description)
        
        // Set workflow type
        val typeIndex = WorkflowType.values().indexOf(workflow.type)
        typeSpinner.setSelection(typeIndex)
        selectedWorkflowType = workflow.type
        
        // Populate configuration
        when (val config = workflow.configuration) {
            is GmailToTelegramConfig -> {
                gmailSearchQueryInput.setText(config.gmailSearchQuery)
                telegramBotTokenInput.setText(config.telegramBotToken)
                telegramChatIdInput.setText(config.telegramChatId)
                llmPromptInput.setText(config.llmPrompt)
                emailLimitInput.setText(config.emailLimit.toString())
            }
            is TelegramToGmailConfig -> {
                telegramBotTokenInput2.setText(config.telegramBotToken)
                telegramChatIdInput2.setText(config.telegramChatId)
                gmailRecipientsInput.setText(config.gmailRecipients.joinToString(", "))
                gmailSenderInput.setText(config.gmailSender)
                llmPromptInput2.setText(config.llmPrompt)
                messageLimitInput.setText(config.messageLimit.toString())
                
                val templateIndex = when (config.emailTemplate.uppercase()) {
                    "COMPACT" -> 1
                    "DETAILED" -> 2
                    else -> 0
                }
                emailTemplateSpinner.setSelection(templateIndex)
            }
        }
        
        updateConfigurationLayout()
    }
    
    private fun validateForm(): Boolean {
        val isValid = when {
            nameInput.text.isBlank() -> false
            selectedWorkflowType == WorkflowType.GMAIL_TO_TELEGRAM -> {
                val searchQueryValid = isValidGmailSearchQuery(gmailSearchQueryInput.text.toString())
                searchQueryValid && 
                telegramBotTokenInput.text.isNotBlank() && 
                telegramChatIdInput.text.isNotBlank()
            }
            selectedWorkflowType == WorkflowType.TELEGRAM_TO_GMAIL -> {
                telegramBotTokenInput2.text.isNotBlank() && 
                telegramChatIdInput2.text.isNotBlank() &&
                gmailRecipientsInput.text.isNotBlank() &&
                gmailSenderInput.text.isNotBlank()
            }
            else -> false
        }
        
        saveButton.isEnabled = isValid
        testButton.isEnabled = isValid
        
        // Show Gmail search query error if present
        if (selectedWorkflowType == WorkflowType.GMAIL_TO_TELEGRAM) {
            val searchQueryError = getGmailSearchQueryError(gmailSearchQueryInput.text.toString())
            if (searchQueryError != null) {
                // You can show this error in a TextView or as a tooltip
                Log.w(TAG, "Gmail search query validation error: $searchQueryError")
            }
        }
        
        return isValid
    }
    
    private fun createConfiguration(): WorkflowConfiguration {
        return when (selectedWorkflowType) {
            WorkflowType.GMAIL_TO_TELEGRAM -> {
                GmailToTelegramConfig(
                    gmailSearchQuery = gmailSearchQueryInput.text.toString().trim(),
                    telegramBotToken = telegramBotTokenInput.text.toString().trim(),
                    telegramChatId = telegramChatIdInput.text.toString().trim(),
                    llmPrompt = llmPromptInput.text.toString().trim(),
                    emailLimit = emailLimitInput.text.toString().toIntOrNull() ?: 5
                )
            }
            WorkflowType.TELEGRAM_TO_GMAIL -> {
                TelegramToGmailConfig(
                    telegramBotToken = telegramBotTokenInput2.text.toString().trim(),
                    telegramChatId = telegramChatIdInput2.text.toString().trim(),
                    gmailRecipients = gmailRecipientsInput.text.toString()
                        .split(",")
                        .map { it.trim() }
                        .filter { it.isNotBlank() },
                    gmailSender = gmailSenderInput.text.toString().trim(),
                    llmPrompt = llmPromptInput2.text.toString().trim(),
                    messageLimit = messageLimitInput.text.toString().toIntOrNull() ?: 30,
                    emailTemplate = when (emailTemplateSpinner.selectedItemPosition) {
                        1 -> "COMPACT"
                        2 -> "DETAILED"
                        else -> "STANDARD"
                    }
                )
            }
        }
    }
    
    private fun saveWorkflow() {
        if (!validateForm()) {
            showError("Please fill in all required fields")
            return
        }
        
        // Additional Gmail search query validation for detailed error reporting
        if (selectedWorkflowType == WorkflowType.GMAIL_TO_TELEGRAM) {
            val searchQueryError = getGmailSearchQueryError(gmailSearchQueryInput.text.toString())
            if (searchQueryError != null) {
                showError("Gmail Search Query Error:\n$searchQueryError\n\nExamples of valid queries:\n• is:unread\n• from:example@email.com\n• subject:important\n• has:attachment")
                return
            }
        }
        
        val configuration = createConfiguration()
        val validationResult = configuration.validate()
        
        if (!validationResult.isValid()) {
            showError("Configuration validation failed:\n${validationResult.getErrorMessage()}")
            return
        }
        
        showProgress("Saving workflow...")
        
        lifecycleScope.launch {
            try {
                val result = if (editMode && editingWorkflow != null) {
                    workflowManager.updateWorkflow(
                        id = editingWorkflow!!.id,
                        name = nameInput.text.toString().trim(),
                        configuration = configuration
                    )
                } else {
                    workflowManager.createWorkflow(
                        name = nameInput.text.toString().trim(),
                        type = selectedWorkflowType,
                        configuration = configuration
                    )
                }
                
                runOnUiThread {
                    hideProgress()
                    
                    result.fold(
                        onSuccess = { workflow ->
                            showSuccess(if (editMode) "Workflow updated successfully!" else "Workflow created successfully!")
                            setResult(RESULT_OK)
                            finish()
                        },
                        onFailure = { exception ->
                            showError("Failed to save workflow: ${exception.message}")
                        }
                    )
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save workflow", e)
                runOnUiThread {
                    hideProgress()
                    showError("Failed to save workflow: ${e.message}")
                }
            }
        }
    }
    
    private fun testWorkflow() {
        if (!validateForm()) {
            showError("Please fill in all required fields before testing")
            return
        }
        
        // Additional Gmail search query validation for testing
        if (selectedWorkflowType == WorkflowType.GMAIL_TO_TELEGRAM) {
            val searchQueryError = getGmailSearchQueryError(gmailSearchQueryInput.text.toString())
            if (searchQueryError != null) {
                showError("Gmail Search Query Error:\n$searchQueryError\n\nExamples of valid queries:\n• is:unread\n• from:example@email.com\n• subject:important\n• has:attachment")
                return
            }
        }
        
        showProgress("Testing workflow configuration...")
        
        // For now, just validate the configuration
        val configuration = createConfiguration()
        val validationResult = configuration.validate()
        
        hideProgress()
        
        if (validationResult.isValid()) {
            showSuccess("Workflow configuration is valid! You can save and run this workflow.")
        } else {
            showError("Configuration validation failed:\n${validationResult.getErrorMessage()}")
        }
    }
    
    private fun showProgress(message: String) {
        progressBar.visibility = View.VISIBLE
        statusText.visibility = View.VISIBLE
        statusText.text = message
        saveButton.isEnabled = false
        testButton.isEnabled = false
    }
    
    private fun hideProgress() {
        progressBar.visibility = View.GONE
        statusText.visibility = View.GONE
        validateForm() // Re-enable buttons based on form state
    }
    
    private fun showSuccess(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
    
    private fun showError(message: String) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Error")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }
}
