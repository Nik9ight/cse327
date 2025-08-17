package com.example.llmapp.workflows.activities

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.llmapp.R
import com.example.llmapp.workflows.services.ImageWorkflowConfig
import com.example.llmapp.workflows.services.WorkflowType
import com.example.llmapp.workflows.services.DestinationType
import com.example.llmapp.workflows.services.WorkflowConfigManager
import com.example.llmapp.utils.ServiceManager
import com.example.llmapp.utils.TelegramChatFetcher
import com.example.llmapp.utils.TelegramConfigManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Activity for creating new image workflows
 * User selects between Image Forward or Document Analysis
 */
class WorkflowCreationActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "WorkflowCreationActivity"
        private const val REQUEST_IMAGE_PICK = 1001
    }
    
    // Common views
    private lateinit var etWorkflowName: EditText
    private lateinit var rgWorkflowType: RadioGroup
    private lateinit var rbImageForward: RadioButton
    private lateinit var rbDocumentAnalysis: RadioButton
    
    // Image Forward views
    private lateinit var layoutImageForward: LinearLayout
    private lateinit var btnSelectReference: Button
    private lateinit var tvSelectedReference: TextView
    private var selectedReferenceImagePath: String = ""
    
    // Document Analysis views
    private lateinit var layoutDocumentAnalysis: LinearLayout
    private lateinit var etDocumentType: EditText
    private lateinit var tpScheduledTime: TimePicker
    
    // Destination views
    private lateinit var layoutDestination: LinearLayout
    private lateinit var rgDestination: RadioGroup
    private lateinit var rbGmail: RadioButton
    private lateinit var rbTelegram: RadioButton
    private lateinit var layoutGmailOptions: LinearLayout
    private lateinit var layoutTelegramOptions: LinearLayout
    private lateinit var etGmailRecipient: EditText
    
    // Enhanced Telegram options
    private lateinit var etTelegramBotToken: EditText
    private lateinit var btnFetchChats: Button
    private lateinit var spinnerTelegramChats: Spinner
    private lateinit var etTelegramChatId: EditText
    private lateinit var tvChatSelectionHelp: TextView
    
    private val telegramChatFetcher = TelegramChatFetcher()
    private var availableChats: List<TelegramChatFetcher.TelegramChat> = emptyList()
    private var selectedChatId: String = ""
    
    private lateinit var btnCreate: Button
    private lateinit var workflowConfigManager: WorkflowConfigManager
    private lateinit var telegramConfigManager: TelegramConfigManager
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_image_workflow_creation)
        
        // Set up toolbar
        supportActionBar?.title = "Create Workflow"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        
        workflowConfigManager = WorkflowConfigManager(this)
        telegramConfigManager = TelegramConfigManager(this)
        
        initViews()
        setupListeners()
    }
    
    private fun initViews() {
        // Common views
        etWorkflowName = findViewById(R.id.etWorkflowName)
        rgWorkflowType = findViewById(R.id.rgWorkflowType)
        rbImageForward = findViewById(R.id.rbImageForward)
        rbDocumentAnalysis = findViewById(R.id.rbDocumentAnalysis)
        
        // Image Forward views
        layoutImageForward = findViewById(R.id.layoutImageForward)
        btnSelectReference = findViewById(R.id.btnSelectReference)
        tvSelectedReference = findViewById(R.id.tvSelectedReference)
        
        // Document Analysis views
        layoutDocumentAnalysis = findViewById(R.id.layoutDocumentAnalysis)
        etDocumentType = findViewById(R.id.etDocumentType)
        tpScheduledTime = findViewById(R.id.tpScheduledTime)
        
        // Destination views
        layoutDestination = findViewById(R.id.layoutDestination)
        rgDestination = findViewById(R.id.rgDestination)
        rbGmail = findViewById(R.id.rbGmail)
        rbTelegram = findViewById(R.id.rbTelegram)
        layoutGmailOptions = findViewById(R.id.layoutGmailOptions)
        layoutTelegramOptions = findViewById(R.id.layoutTelegramOptions)
        etGmailRecipient = findViewById(R.id.etGmailRecipient)
        
        // Enhanced Telegram options (bot token now managed separately)
        btnFetchChats = findViewById(R.id.btnFetchChats)
        spinnerTelegramChats = findViewById(R.id.spinnerTelegramChats)
        etTelegramChatId = findViewById(R.id.etTelegramChatId)
        tvChatSelectionHelp = findViewById(R.id.tvChatSelectionHelp)
        
        btnCreate = findViewById(R.id.btnCreate)
        
        // Set default time to 6 PM (18:00)
        tpScheduledTime.hour = 18
        tpScheduledTime.minute = 0
    }
    
    private fun setupListeners() {
        rgWorkflowType.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.rbImageForward -> {
                    showImageForwardOptions()
                }
                R.id.rbDocumentAnalysis -> {
                    showDocumentAnalysisOptions()
                }
            }
        }
        
        rgDestination.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.rbGmail -> {
                    showGmailOptions()
                }
                R.id.rbTelegram -> {
                    showTelegramOptions()
                }
            }
        }
        
        btnSelectReference.setOnClickListener {
            selectReferenceImage()
        }
        
        btnFetchChats.setOnClickListener {
            fetchTelegramChats()
        }
        
        spinnerTelegramChats.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (position > 0 && availableChats.isNotEmpty()) { // Skip the first "Select chat" item
                    val selectedChat = availableChats[position - 1]
                    selectedChatId = selectedChat.chatId
                    etTelegramChatId.setText(selectedChatId)
                    Log.d(TAG, "Selected chat: ${selectedChat.getDisplayText()}")
                }
            }
            
            override fun onNothingSelected(parent: AdapterView<*>?) {
                selectedChatId = ""
                etTelegramChatId.setText("")
            }
        }
        
        btnCreate.setOnClickListener {
            createWorkflow()
        }
        
        // Initially hide all options
        layoutImageForward.visibility = LinearLayout.GONE
        layoutDocumentAnalysis.visibility = LinearLayout.GONE
        layoutDestination.visibility = LinearLayout.GONE
        layoutGmailOptions.visibility = LinearLayout.GONE
        layoutTelegramOptions.visibility = LinearLayout.GONE
    }
    
    private fun showImageForwardOptions() {
        layoutImageForward.visibility = LinearLayout.VISIBLE
        layoutDocumentAnalysis.visibility = LinearLayout.GONE
        layoutDestination.visibility = LinearLayout.VISIBLE
        
        // Clear document analysis fields
        etDocumentType.text.clear()
    }
    
    private fun showDocumentAnalysisOptions() {
        layoutImageForward.visibility = LinearLayout.GONE
        layoutDocumentAnalysis.visibility = LinearLayout.VISIBLE
        layoutDestination.visibility = LinearLayout.VISIBLE
        
        // Clear reference image
        selectedReferenceImagePath = ""
        tvSelectedReference.text = "No reference image selected"
    }
    
    private fun showGmailOptions() {
        layoutGmailOptions.visibility = LinearLayout.VISIBLE
        layoutTelegramOptions.visibility = LinearLayout.GONE
        
        // Clear telegram fields
        etTelegramChatId.text.clear()
        selectedChatId = ""
        availableChats = emptyList()
        updateTelegramChatSpinner()
    }
    
    private fun showTelegramOptions() {
        layoutGmailOptions.visibility = LinearLayout.GONE
        layoutTelegramOptions.visibility = LinearLayout.VISIBLE
        
        // Clear gmail fields
        etGmailRecipient.text.clear()
        
        // Initialize chat spinner and auto-fetch chats if token is configured
        updateTelegramChatSpinner()
        autoFetchChatsIfConfigured()
    }
    
    private fun selectReferenceImage() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        startActivityForResult(intent, REQUEST_IMAGE_PICK)
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        if (requestCode == REQUEST_IMAGE_PICK && resultCode == Activity.RESULT_OK) {
            data?.data?.let { uri ->
                selectedReferenceImagePath = getRealPathFromURI(uri)
                tvSelectedReference.text = "Reference image selected: ${selectedReferenceImagePath.substringAfterLast("/")}"
                Log.d(TAG, "Reference image selected: $selectedReferenceImagePath")
            }
        }
    }
    
    private fun getRealPathFromURI(uri: Uri): String {
        var path = ""
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val columnIndex = cursor.getColumnIndex(MediaStore.Images.Media.DATA)
                if (columnIndex != -1) {
                    path = cursor.getString(columnIndex)
                }
            }
        }
        return if (path.isNotEmpty()) path else uri.path ?: ""
    }
    
    /**
     * Fetch available Telegram chats from the saved bot token
     */
    private fun fetchTelegramChats() {
        val botToken = telegramConfigManager.getBotToken()
        
        if (botToken.isNullOrEmpty()) {
            showTelegramSettingsDialog("Please configure your Telegram bot token first.")
            return
        }
        
        fetchChatsWithToken(botToken)
    }
    
    /**
     * Auto-fetch chats if bot token is configured
     */
    private fun autoFetchChatsIfConfigured() {
        val botToken = telegramConfigManager.getBotToken()
        
        if (botToken.isNullOrEmpty()) {
            tvChatSelectionHelp.text = "Configure your Telegram bot token using the settings menu (gear icon) to automatically fetch available chats."
            tvChatSelectionHelp.visibility = View.VISIBLE
            btnFetchChats.text = "Configure Bot Token"
            btnFetchChats.setOnClickListener { showTelegramSettingsDialog() }
            return
        }
        
        // Auto-fetch chats
        tvChatSelectionHelp.text = "Fetching available chats..."
        tvChatSelectionHelp.visibility = View.VISIBLE
        btnFetchChats.text = "Refresh Chats"
        btnFetchChats.setOnClickListener { fetchTelegramChats() }
        
        fetchChatsWithToken(botToken)
    }
    
    /**
     * Fetch chats with a specific bot token
     */
    private fun fetchChatsWithToken(botToken: String) {
        if (!telegramChatFetcher.isValidBotTokenFormat(botToken)) {
            tvChatSelectionHelp.text = "Invalid bot token format. Please update your settings."
            tvChatSelectionHelp.visibility = View.VISIBLE
            return
        }
        
        // Show progress
        btnFetchChats.isEnabled = false
        btnFetchChats.text = "Fetching..."
        
        CoroutineScope(Dispatchers.Main).launch {
            try {
                Log.d(TAG, "Fetching chats with saved bot token")
                
                // Fetch chats directly (token already validated when saved)
                val chatsResult = telegramChatFetcher.findChatIdsAndUsernames(botToken)
                chatsResult.fold(
                    onSuccess = { chats ->
                        availableChats = chats
                        updateTelegramChatSpinner()
                        
                        if (chats.isEmpty()) {
                            tvChatSelectionHelp.text = "No conversations found. Send a message to your bot first to create a conversation."
                            tvChatSelectionHelp.visibility = View.VISIBLE
                        } else {
                            tvChatSelectionHelp.text = "Found ${chats.size} conversation(s). Select one or enter chat ID manually."
                            tvChatSelectionHelp.visibility = View.VISIBLE
                        }
                        
                        Log.d(TAG, "Found ${chats.size} chats")
                    },
                    onFailure = { error ->
                        Log.e(TAG, "Error fetching chats", error)
                        tvChatSelectionHelp.text = "Error fetching conversations: ${error.message}. Check your bot token in settings."
                        tvChatSelectionHelp.visibility = View.VISIBLE
                    }
                )
            } finally {
                btnFetchChats.isEnabled = true
                btnFetchChats.text = "Refresh Chats"
            }
        }
    }
    
    /**
     * Update the Telegram chat spinner with available chats
     */
    private fun updateTelegramChatSpinner() {
        val spinnerItems = mutableListOf<String>()
        spinnerItems.add("Select a chat") // Default item
        
        availableChats.forEach { chat ->
            spinnerItems.add(chat.getDisplayText())
        }
        
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, spinnerItems)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerTelegramChats.adapter = adapter
    }
    
    /**
     * Show Telegram settings dialog
     */
    private fun showTelegramSettingsDialog(message: String? = null) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_telegram_settings, null)
        val etDialogBotToken = dialogView.findViewById<EditText>(R.id.etDialogBotToken)
        val btnDialogCancel = dialogView.findViewById<Button>(R.id.btnDialogCancel)
        val btnDialogTest = dialogView.findViewById<Button>(R.id.btnDialogTest)
        val btnDialogSave = dialogView.findViewById<Button>(R.id.btnDialogSave)
        
        // Pre-fill with existing token
        val existingToken = telegramConfigManager.getBotToken()
        if (!existingToken.isNullOrEmpty()) {
            etDialogBotToken.setText(existingToken)
        }
        
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()
        
        // Show custom message if provided
        if (message != null) {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        }
        
        btnDialogCancel.setOnClickListener {
            dialog.dismiss()
        }
        
        btnDialogTest.setOnClickListener {
            val token = etDialogBotToken.text.toString().trim()
            if (token.isEmpty()) {
                etDialogBotToken.error = "Bot token is required"
                return@setOnClickListener
            }
            
            if (!telegramChatFetcher.isValidBotTokenFormat(token)) {
                etDialogBotToken.error = "Invalid bot token format"
                return@setOnClickListener
            }
            
            btnDialogTest.isEnabled = false
            btnDialogTest.text = "Testing..."
            
            CoroutineScope(Dispatchers.Main).launch {
                try {
                    val result = telegramChatFetcher.testBotToken(token)
                    result.fold(
                        onSuccess = { botInfo ->
                            Toast.makeText(this@WorkflowCreationActivity, "✓ $botInfo", Toast.LENGTH_SHORT).show()
                            btnDialogSave.isEnabled = true
                        },
                        onFailure = { error ->
                            Toast.makeText(this@WorkflowCreationActivity, "❌ ${error.message}", Toast.LENGTH_LONG).show()
                            etDialogBotToken.error = "Token validation failed"
                        }
                    )
                } finally {
                    btnDialogTest.isEnabled = true
                    btnDialogTest.text = "Test Token"
                }
            }
        }
        
        btnDialogSave.setOnClickListener {
            val token = etDialogBotToken.text.toString().trim()
            if (token.isEmpty()) {
                etDialogBotToken.error = "Bot token is required"
                return@setOnClickListener
            }
            
            if (!telegramChatFetcher.isValidBotTokenFormat(token)) {
                etDialogBotToken.error = "Invalid bot token format"
                return@setOnClickListener
            }
            
            // Save token
            telegramConfigManager.saveBotToken(token)
            Toast.makeText(this@WorkflowCreationActivity, "Bot token saved successfully!", Toast.LENGTH_SHORT).show()
            
            // Auto-fetch chats if Telegram is currently selected
            if (rbTelegram.isChecked) {
                autoFetchChatsIfConfigured()
            }
            
            dialog.dismiss()
        }
        
        dialog.show()
    }
    
    private fun createWorkflow() {
        try {
            // Validate common fields
            val workflowName = etWorkflowName.text.toString().trim()
            if (workflowName.isEmpty()) {
                etWorkflowName.error = "Workflow name is required"
                return
            }
            
            // Validate workflow type selection
            val selectedTypeId = rgWorkflowType.checkedRadioButtonId
            if (selectedTypeId == -1) {
                Toast.makeText(this, "Please select a workflow type", Toast.LENGTH_SHORT).show()
                return
            }
            
            // Validate destination selection
            val selectedDestinationId = rgDestination.checkedRadioButtonId
            if (selectedDestinationId == -1) {
                Toast.makeText(this, "Please select a destination", Toast.LENGTH_SHORT).show()
                return
            }
            
            // Determine workflow type
            val workflowType = when (selectedTypeId) {
                R.id.rbImageForward -> WorkflowType.IMAGE_FORWARD
                R.id.rbDocumentAnalysis -> WorkflowType.DOCUMENT_ANALYSIS
                else -> {
                    Toast.makeText(this, "Invalid workflow type", Toast.LENGTH_SHORT).show()
                    return
                }
            }
            
            // Validate type-specific fields
            when (workflowType) {
                WorkflowType.IMAGE_FORWARD -> {
                    if (selectedReferenceImagePath.isEmpty()) {
                        Toast.makeText(this, "Please select a reference image", Toast.LENGTH_SHORT).show()
                        return
                    }
                }
                WorkflowType.DOCUMENT_ANALYSIS -> {
                    val documentType = etDocumentType.text.toString().trim()
                    if (documentType.isEmpty()) {
                        etDocumentType.error = "Document type is required"
                        return
                    }
                }
            }
            
            // Determine destination type and validate
            val destinationType = when (selectedDestinationId) {
                R.id.rbGmail -> {
                    val recipient = etGmailRecipient.text.toString().trim()
                    if (recipient.isEmpty()) {
                        etGmailRecipient.error = "Gmail recipient is required"
                        return
                    }
                    DestinationType.GMAIL
                }
                R.id.rbTelegram -> {
                    val botToken = telegramConfigManager.getBotToken()
                    val chatId = etTelegramChatId.text.toString().trim()
                    
                    if (botToken.isNullOrEmpty()) {
                        Toast.makeText(this, "Please configure your Telegram bot token first (use settings menu)", Toast.LENGTH_LONG).show()
                        return
                    }
                    
                    if (!telegramChatFetcher.isValidBotTokenFormat(botToken)) {
                        Toast.makeText(this, "Invalid bot token. Please update your settings.", Toast.LENGTH_LONG).show()
                        return
                    }
                    
                    if (chatId.isEmpty()) {
                        etTelegramChatId.error = "Telegram chat ID is required"
                        return
                    }
                    
                    DestinationType.TELEGRAM
                }
                else -> {
                    Toast.makeText(this, "Invalid destination type", Toast.LENGTH_SHORT).show()
                    return
                }
            }
            
            // Create workflow configuration
            val workflowConfig = ImageWorkflowConfig(
                workflowName = workflowName,
                workflowType = workflowType,
                referenceImagePath = if (workflowType == WorkflowType.IMAGE_FORWARD) selectedReferenceImagePath else "",
                documentType = if (workflowType == WorkflowType.DOCUMENT_ANALYSIS) etDocumentType.text.toString().trim() else "",
                scheduledTime = if (workflowType == WorkflowType.DOCUMENT_ANALYSIS) "${tpScheduledTime.hour}:${String.format("%02d", tpScheduledTime.minute)}" else "",
                destinationType = destinationType,
                gmailRecipient = if (destinationType == DestinationType.GMAIL) etGmailRecipient.text.toString().trim() else "",
                telegramBotToken = if (destinationType == DestinationType.TELEGRAM) telegramConfigManager.getBotToken() ?: "" else "",
                telegramChatId = if (destinationType == DestinationType.TELEGRAM) etTelegramChatId.text.toString().trim() else ""
            )
            
            // Save workflow
            val success = workflowConfigManager.saveWorkflow(workflowConfig)
            
            if (success) {
                Log.d(TAG, "Workflow created successfully: ${workflowConfig.workflowName}")
                Toast.makeText(this, "Workflow '${workflowConfig.workflowName}' created successfully", Toast.LENGTH_SHORT).show()
                
                // Start image workflow service if needed now that workflows exist
                ServiceManager.startImageWorkflowServiceIfNeeded(this)
                
                finish() // Return to workflow list
            } else {
                Log.e(TAG, "Failed to save workflow")
                Toast.makeText(this, "Failed to create workflow", Toast.LENGTH_SHORT).show()
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error creating workflow", e)
            Toast.makeText(this, "Error creating workflow: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.workflow_creation_menu, menu)
        return true
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressed()
                true
            }
            R.id.action_settings -> {
                showTelegramSettingsDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
