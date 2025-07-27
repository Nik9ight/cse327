package com.example.llmapp

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.example.llmapp.adapters.EmailTemplateType
import com.example.llmapp.observers.PipelineObserver
import com.example.llmapp.pipeline.TelegramToGmailPipeline
import com.example.llmapp.pipeline.TelegramToGmailPipelineBuilder
import com.example.llmapp.pipeline.PipelineResult
import com.example.llmapp.pipeline.DefaultPromptStrategy
import com.example.llmapp.pipeline.AnalysisPromptStrategy
import com.example.llmapp.pipeline.CustomPromptStrategy
import com.example.llmapp.pipeline.LlmContentProcessor
import kotlinx.coroutines.launch

class TelegramToGmailActivity : ComponentActivity(), PipelineObserver {
    
    companion object {
        private const val REQUEST_TELEGRAM_CONFIG = 1001
        private const val REQUEST_EMAIL_CONFIG = 1002
        private const val REQUEST_PROMPT_CONFIG = 1003
        private const val TAG = "TelegramToGmailActivity"
    }
    
    // UI Components
    private lateinit var step1Button: Button
    private lateinit var step2Button: Button
    private lateinit var step3Button: Button
    private lateinit var step4Button: Button
    private lateinit var runPipelineButton: Button
    private lateinit var viewLogsButton: Button
    private lateinit var testConnectionsButton: Button
    private lateinit var clearHistoryButton: Button
    
    // Info buttons
    private lateinit var step1InfoButton: Button
    private lateinit var step2InfoButton: Button
    private lateinit var step3InfoButton: Button
    private lateinit var step4InfoButton: Button
    private lateinit var actionsInfoButton: Button
    private lateinit var logsInfoButton: Button
    
    private lateinit var step1StatusText: TextView
    private lateinit var step2StatusText: TextView
    private lateinit var step3StatusText: TextView
    private lateinit var step4StatusText: TextView
    private lateinit var statisticsText: TextView
    private lateinit var logOutput: TextView
    
    private lateinit var progressBar: ProgressBar
    private lateinit var statusText: TextView
    
    // Advanced options
    private lateinit var advancedOptionsLayout: LinearLayout
    
    // Content layouts for each section
    private lateinit var step1Content: LinearLayout
    private lateinit var step2Content: LinearLayout
    private lateinit var step3Content: LinearLayout
    private lateinit var step4Content: LinearLayout
    private lateinit var actionsContent: LinearLayout
    private lateinit var logsContent: LinearLayout
    private lateinit var templateSpinner: Spinner
    private lateinit var batchSizeSeekBar: SeekBar
    private lateinit var batchSizeText: TextView
    private lateinit var delaySeekBar: SeekBar
    private lateinit var delayText: TextView
    
    // Step cards for sequential workflow
    private lateinit var step1Card: LinearLayout
    private lateinit var step2Card: LinearLayout
    private lateinit var step3Card: LinearLayout
    private lateinit var step4Card: LinearLayout
    
    // Pipeline components
    private var pipeline: TelegramToGmailPipeline? = null
    private var gmailService: GmailService? = null
    private var llmProcessor: LlmContentProcessor? = null
    
    // Configuration state
    private var step1Completed = false // Telegram bot configuration
    private var step2Completed = false // Email recipients configuration
    private var step3Completed = false // LLM prompt configuration
    private var step4Completed = false // Gmail service configuration
    
    private var botToken: String? = null
    private var emailRecipients: List<String> = emptyList()
    private var defaultSender: String? = null
    private var customPrompt: String? = null
    private var currentTemplateType = EmailTemplateType.STANDARD
    private var currentBatchSize = 5
    private var currentDelay = 1000L
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_telegram_to_gmail)
        
        initializeViews()
        setupClickListeners()
        initializeServices()
        updateUI()
        
        // Show workflow information
        appendToLog("=== TELEGRAM TO GMAIL PIPELINE ===")
        appendToLog("Automated Workflow Steps:")
        appendToLog("1. Fetch recent conversation from Telegram")
        appendToLog("2. Preprocess messages and apply prompt strategy")
        appendToLog("3. Process content with LLM for analysis")
        appendToLog("4. Send processed response as email via Gmail")
        appendToLog("")
        appendToLog("🚀 SETUP GUIDE:")
        appendToLog("• Complete all 4 configuration steps below")
        appendToLog("• Send a message to your Telegram bot first")
        appendToLog("• Use 'Test Connections' to verify setup")
        appendToLog("• Then click 'Run Automated Pipeline'")
        appendToLog("")
        
        Log.d(TAG, "TelegramToGmailActivity created")
    }
    
    override fun onDestroy() {
        super.onDestroy()
        
        // Clean up LLM processor resources
        llmProcessor?.cleanup()
        
        Log.d(TAG, "TelegramToGmailActivity destroyed")
    }
    
    private fun initializeViews() {
        // Step cards
        step1Card = findViewById(R.id.step1Card)
        step2Card = findViewById(R.id.step2Card)
        step3Card = findViewById(R.id.step3Card)
        step4Card = findViewById(R.id.step4Card)
        
        // Step buttons
        step1Button = findViewById(R.id.step1Button)
        step2Button = findViewById(R.id.step2Button)
        step3Button = findViewById(R.id.step3Button)
        step4Button = findViewById(R.id.step4Button)
        
        // Info buttons
        step1InfoButton = findViewById(R.id.step1InfoButton)
        step2InfoButton = findViewById(R.id.step2InfoButton)
        step3InfoButton = findViewById(R.id.step3InfoButton)
        step4InfoButton = findViewById(R.id.step4InfoButton)
        actionsInfoButton = findViewById(R.id.actionsInfoButton)
        logsInfoButton = findViewById(R.id.logsInfoButton)
        
        // Action buttons
        runPipelineButton = findViewById(R.id.runPipelineButton)
        viewLogsButton = findViewById(R.id.viewLogsButton)
        testConnectionsButton = findViewById(R.id.testConnectionsButton)
        clearHistoryButton = findViewById(R.id.clearHistoryButton)
        
        // Status texts
        step1StatusText = findViewById(R.id.step1StatusText)
        step2StatusText = findViewById(R.id.step2StatusText)
        step3StatusText = findViewById(R.id.step3StatusText)
        step4StatusText = findViewById(R.id.step4StatusText)
        statisticsText = findViewById(R.id.statisticsText)
        logOutput = findViewById(R.id.logOutput)
        
        // Progress components
        
        progressBar = findViewById(R.id.progressBar)
        statusText = findViewById(R.id.statusText)
        
        // Advanced options
        advancedOptionsLayout = findViewById(R.id.advancedOptionsLayout)
        
        // Initialize all content layouts
        step1Content = findViewById(R.id.step1Content)
        step2Content = findViewById(R.id.step2Content)
        step3Content = findViewById(R.id.step3Content)
        step4Content = findViewById(R.id.step4Content)
        actionsContent = findViewById(R.id.actionsContent)
        logsContent = findViewById(R.id.logsContent)
        
        templateSpinner = findViewById(R.id.templateSpinner)
        batchSizeSeekBar = findViewById(R.id.batchSizeSeekBar)
        batchSizeText = findViewById(R.id.batchSizeText)
        delaySeekBar = findViewById(R.id.delaySeekBar)
        delayText = findViewById(R.id.delayText)
        
        setupAdvancedOptions()
    }    private fun setupAdvancedOptions() {
        // Template spinner
        val templateAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            EmailTemplateType.values().map { it.name }
        )
        templateAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        templateSpinner.adapter = templateAdapter
        templateSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                currentTemplateType = EmailTemplateType.values()[position]
                pipeline?.setEmailTemplate(currentTemplateType)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        
        // Batch size seekbar
        batchSizeSeekBar.max = 19 // 1-20
        batchSizeSeekBar.progress = currentBatchSize - 1
        updateBatchSizeText()
        batchSizeSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                currentBatchSize = progress + 1
                updateBatchSizeText()
                pipeline?.setBatchSize(currentBatchSize)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        
        // Delay seekbar
        delaySeekBar.max = 50 // 0-5 seconds
        delaySeekBar.progress = (currentDelay / 100).toInt()
        updateDelayText()
        delaySeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                currentDelay = progress * 100L
                updateDelayText()
                pipeline?.setDelayBetweenEmails(currentDelay)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }
    
    private fun updateBatchSizeText() {
        batchSizeText.text = "Batch Size: $currentBatchSize"
    }
    
    private fun updateDelayText() {
        delayText.text = "Delay: ${currentDelay}ms"
    }
    
    private fun setupClickListeners() {
        step1Button.setOnClickListener { startTelegramConfiguration() }
        step2Button.setOnClickListener { startEmailConfiguration() }
        step3Button.setOnClickListener { startPromptConfiguration() }
        step4Button.setOnClickListener { startGmailConfiguration() }
        
        // Info button click listeners
        step1InfoButton.setOnClickListener { toggleInfoContent(step1Content) }
        step2InfoButton.setOnClickListener { toggleInfoContent(step2Content) }
        step3InfoButton.setOnClickListener { toggleInfoContent(step3Content) }
        step4InfoButton.setOnClickListener { toggleInfoContent(step4Content) }
        actionsInfoButton.setOnClickListener { toggleInfoContent(actionsContent) }
        logsInfoButton.setOnClickListener { toggleInfoContent(logsContent) }
        
        // Primary action: run automated workflow
        runPipelineButton.setOnClickListener { runCompleteWorkflow() }
        
        // Long click: show processing options for manual control
        runPipelineButton.setOnLongClickListener { 
            showProcessingOptions()
            true
        }
        
        viewLogsButton.setOnClickListener { 
            // This will be handled by the info button
            toggleInfoContent(logsContent)
        }
        
        testConnectionsButton.setOnClickListener { testConnections() }
        clearHistoryButton.setOnClickListener { clearHistory() }
    }
    
    private fun initializeServices() {
        gmailService = GmailService(this)
        
        // Initialize LLM processor for real AI processing
        try {
            llmProcessor = LlmContentProcessor(this)
            Log.d(TAG, "LLM processor initialized successfully")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to initialize LLM processor, will use simulated processing", e)
            llmProcessor = null
        }
        
        // Check if Telegram is already configured
        val telegramService = TelegramService(this)
        step1Completed = telegramService.isLoggedIn()
        if (step1Completed) {
            botToken = telegramService.getBotToken() ?: "configured"
            updateStepStatus(1, "✓ Telegram bot already configured")
            
            // Log the saved chat ID for debugging
            val savedChatId = telegramService.getChatId()
            if (!savedChatId.isNullOrEmpty()) {
                Log.d(TAG, "Found saved chat ID in preferences: $savedChatId")
            } else {
                Log.w(TAG, "No chat ID found in saved preferences - user may need to complete Telegram setup")
            }
        } else {
            updateStepStatus(1, "Configure Telegram Bot")
        }
        
        // Check if Gmail is already signed in
        step4Completed = gmailService?.isSignedIn() == true
        if (step4Completed) {
            updateStepStatus(4, "✓ Gmail already configured")
        } else {
            updateStepStatus(4, "Configure Gmail Service")
        }
    }
    
    private fun startTelegramConfiguration() {
        val intent = Intent(this, TelegramLoginActivity::class.java)
        // TelegramLoginActivity doesn't need current_bot_token as it handles its own state
        startActivityForResult(intent, REQUEST_TELEGRAM_CONFIG)
    }
    
    private fun startEmailConfiguration() {
        val intent = Intent(this, EmailConfigActivity::class.java)
        intent.putStringArrayListExtra("current_recipients", ArrayList(emailRecipients))
        intent.putExtra("current_sender", defaultSender)
        startActivityForResult(intent, REQUEST_EMAIL_CONFIG)
    }
    
    private fun startPromptConfiguration() {
        val intent = Intent(this, LlmPromptActivity::class.java)
        intent.putExtra("current_prompt", customPrompt)
        startActivityForResult(intent, REQUEST_PROMPT_CONFIG)
    }
    
    private fun startGmailConfiguration() {
        if (gmailService?.isSignedIn() == true) {
            step4Completed = true
            updateUI()
            Toast.makeText(this, "Gmail already configured", Toast.LENGTH_SHORT).show()
            return
        }
        
        gmailService?.signIn(this) { success ->
            if (success) {
                step4Completed = true
                updateUI()
                Toast.makeText(this, "Gmail configuration successful", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Gmail configuration failed", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun showRecentConversationDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_recent_conversation, null)
        val chatIdInput = dialogView.findViewById<EditText>(R.id.chatIdInput)
        val messageLimitInput = dialogView.findViewById<EditText>(R.id.messageLimitInput)
        
        messageLimitInput.setText("10")
        
        // Auto-populate chat ID from saved TelegramService preferences
        val telegramService = TelegramService(this)
        val savedChatId = telegramService.getChatId()
        if (!savedChatId.isNullOrEmpty()) {
            chatIdInput.setText(savedChatId)
            Log.d(TAG, "Auto-populated chat ID from saved preferences: $savedChatId")
        } else {
            Log.w(TAG, "No saved chat ID found in TelegramService preferences")
        }
        
        android.app.AlertDialog.Builder(this)
            .setTitle("Process Recent Conversation")
            .setView(dialogView)
            .setPositiveButton("Process") { _, _ ->
                val chatId = chatIdInput.text.toString().trim()
                val messageLimit = messageLimitInput.text.toString().toIntOrNull() ?: 10
                
                if (chatId.isNotEmpty()) {
                    Log.d(TAG, "Processing recent conversation with chat ID: $chatId")
                    processRecentConversation(chatId, messageLimit)
                } else {
                    Toast.makeText(this, "Please enter a chat ID", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun showSingleMessageDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_single_message, null)
        val chatIdInput = dialogView.findViewById<EditText>(R.id.chatIdInput)
        val messageIdInput = dialogView.findViewById<EditText>(R.id.messageIdInput)
        
        // Auto-populate chat ID from saved TelegramService preferences
        val telegramService = TelegramService(this)
        val savedChatId = telegramService.getChatId()
        if (!savedChatId.isNullOrEmpty()) {
            chatIdInput.setText(savedChatId)
            Log.d(TAG, "Auto-populated chat ID from saved preferences: $savedChatId")
        } else {
            Log.w(TAG, "No saved chat ID found in TelegramService preferences")
        }
        
        android.app.AlertDialog.Builder(this)
            .setTitle("Process Single Message")
            .setView(dialogView)
            .setPositiveButton("Process") { _, _ ->
                val chatId = chatIdInput.text.toString().trim()
                val messageId = messageIdInput.text.toString().trim()
                
                if (chatId.isNotEmpty() && messageId.isNotEmpty()) {
                    Log.d(TAG, "Processing single message with chat ID: $chatId, message ID: $messageId")
                    processSingleMessage(chatId, messageId)
                } else {
                    Toast.makeText(this, "Please enter both chat ID and message ID", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun showBatchProcessingDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_batch_processing, null)
        val limitInput = dialogView.findViewById<EditText>(R.id.limitInput)
        
        limitInput.setText("20")
        
        android.app.AlertDialog.Builder(this)
            .setTitle("Process All Recent Updates")
            .setView(dialogView)
            .setPositiveButton("Process") { _, _ ->
                val limit = limitInput.text.toString().toIntOrNull() ?: 20
                processBatchUpdates(limit)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun processRecentConversation(chatId: String, messageLimit: Int) {
        lifecycleScope.launch {
            try {
                ensurePipelineInitialized()
                showProgress("Processing recent conversation...")
                
                val result = pipeline!!.processRecentConversation(chatId, messageLimit)
                
                hideProgress()
                handlePipelineResult(result)
                
            } catch (e: Exception) {
                hideProgress()
                showError("Failed to process conversation: ${e.message}")
            }
        }
    }
    
    private fun processSingleMessage(chatId: String, messageId: String) {
        lifecycleScope.launch {
            try {
                ensurePipelineInitialized()
                showProgress("Processing single message...")
                
                val result = pipeline!!.processSingleMessage(chatId, messageId)
                
                hideProgress()
                handlePipelineResult(result)
                
            } catch (e: Exception) {
                hideProgress()
                showError("Failed to process message: ${e.message}")
            }
        }
    }
    
    private fun processBatchUpdates(limit: Int) {
        lifecycleScope.launch {
            try {
                ensurePipelineInitialized()
                showProgress("Processing batch updates...")
                
                val result = pipeline!!.processAllRecentUpdates(limit)
                
                hideProgress()
                handlePipelineResult(result)
                
            } catch (e: Exception) {
                hideProgress()
                showError("Failed to process batch updates: ${e.message}")
            }
        }
    }
    
    /**
     * Complete automated workflow: Fetch → Preprocess → LLM Processing → Email Delivery
     * Following the step-wise execution pattern from PipelineActivity
     */
    private fun runCompleteWorkflow() {
        if (!validateConfiguration()) {
            return
        }
        
        lifecycleScope.launch {
            try {
                ensurePipelineInitialized()
                
                // Step 1: Initialize workflow
                appendToLog("=== STARTING AUTOMATED WORKFLOW ===")
                showProgress("Initializing workflow...")
                
                // Apply custom prompt strategy if configured
                applyLlmPromptConfiguration()
                
                // Step 2: Fetch recent conversation from any available chat
                showProgress("Step 1/4: Fetching recent conversations from Telegram...")
                appendToLog("STEP 1: Fetching conversations from Telegram")
                
                // Try to get available chat IDs first
                val availableChatIds = pipeline!!.getAvailableChatIds()
                if (availableChatIds.isEmpty()) {
                    // No chats found - provide helpful guidance to user
                    hideProgress()
                    
                    val helpMessage = buildString {
                        appendLine("🤖 NO CONVERSATIONS FOUND")
                        appendLine()
                        appendLine("Your Telegram bot hasn't received any messages yet!")
                        appendLine()
                        appendLine("📱 To get started:")
                        appendLine("1. Open Telegram and search for your bot")
                        appendLine("2. Send any message to your bot (e.g., 'Hello')")
                        appendLine("3. Wait a moment for the bot to register the conversation")
                        appendLine("4. Return here and run the pipeline again")
                        appendLine()
                        appendLine("💡 Quick steps:")
                        appendLine("• Bot username: Check your Telegram configuration")
                        appendLine("• Send message: Any text will create a conversation")
                        appendLine("• Test connection: Use 'Test Connections' button first")
                        appendLine()
                        appendLine("Once you send a message, this workflow will be able to process it!")
                    }
                    
                    // Show actionable dialog instead of just error
                    android.app.AlertDialog.Builder(this@TelegramToGmailActivity)
                        .setTitle("🤖 Bot Setup Required")
                        .setMessage(helpMessage)
                        .setPositiveButton("Test Connections") { _, _ ->
                            testConnections()
                        }
                        .setNegativeButton("OK") { _, _ ->
                            appendToLog("User needs to send a message to the bot first")
                        }
                        .setNeutralButton("Retry Workflow") { _, _ ->
                            runCompleteWorkflow()
                        }
                        .show()
                    
                    appendToLog("=== NO CONVERSATIONS AVAILABLE ===")
                    appendToLog("User needs to send a message to the bot first")
                    appendToLog("Guidance provided for bot interaction")
                    return@launch
                }
                
                appendToLog("Found ${availableChatIds.size} available chats: ${availableChatIds.take(3).joinToString(", ")}${if (availableChatIds.size > 3) "..." else ""}")
                
                // Prioritize the saved chat ID from TelegramService
                val savedChatId = getSavedChatId()
                val targetChatId = when {
                    // If we have a saved chat ID and it's in the available chats, use it
                    !savedChatId.isNullOrEmpty() && availableChatIds.contains(savedChatId) -> {
                        appendToLog("Using saved chat ID from TelegramService: $savedChatId")
                        savedChatId
                    }
                    // If saved chat ID doesn't match exactly, try numeric comparison
                    !savedChatId.isNullOrEmpty() && availableChatIds.any { 
                        it.toLongOrNull()?.toString() == savedChatId || 
                        savedChatId.toLongOrNull()?.toString() == it 
                    } -> {
                        val matchingChatId = availableChatIds.find { 
                            it.toLongOrNull()?.toString() == savedChatId || 
                            savedChatId.toLongOrNull()?.toString() == it 
                        }!!
                        appendToLog("Using saved chat ID (numeric match) from TelegramService: $savedChatId -> $matchingChatId")
                        matchingChatId
                    }
                    // If no saved chat ID or it's not available, use the first available
                    availableChatIds.size == 1 -> {
                        appendToLog("Using the only available chat: ${availableChatIds.first()}")
                        availableChatIds.first()
                    }
                    // For multiple chats, use the first one
                    else -> {
                        appendToLog("Using first available chat (no saved preference): ${availableChatIds.first()}")
                        if (!savedChatId.isNullOrEmpty()) {
                            appendToLog("Note: Saved chat ID '$savedChatId' not found in available chats")
                        }
                        availableChatIds.first()
                    }
                }
                
                appendToLog("Using chat ID: $targetChatId for automated processing")
                
                // Step 3: Process with LLM
                showProgress("Step 2/4: Processing messages with LLM...")
                appendToLog("STEP 2: Processing conversation with LLM")
                
                // Step 4: Send via Gmail
                showProgress("Step 3/4: Preparing email delivery...")
                appendToLog("STEP 3: Preparing email content for delivery")
                
                // Execute the complete pipeline
                showProgress("Step 4/4: Executing complete pipeline...")
                appendToLog("STEP 4: Executing Telegram → LLM → Gmail pipeline")
                
                val result = pipeline!!.processRecentConversation(
                    chatId = targetChatId,
                    messageLimit = 10, // Default limit for automated workflow
                    customSubject = "Automated Telegram Conversation Summary"
                )
                
                hideProgress()
                
                // Handle results
                when (result) {
                    is PipelineResult.Success -> {
                        val successMessage = buildString {
                            appendLine("🎉 WORKFLOW COMPLETED SUCCESSFULLY!")
                            appendLine()
                            appendLine("✓ Messages fetched from Telegram")
                            appendLine("✓ Content processed with LLM")
                            appendLine("✓ Email sent via Gmail")
                            appendLine()
                            appendLine("Details:")
                            appendLine("• Processed ${result.processedMessages} messages")
                            appendLine("• Chat ID: $targetChatId")
                            appendLine("• Email delivered: ${if (result.emailSent) "Yes" else "No"}")
                            result.batchInfo?.let { appendLine("• Batch info: $it") }
                        }
                        
                        showSuccess(successMessage)
                        appendToLog("=== WORKFLOW COMPLETED SUCCESSFULLY ===")
                    }
                    
                    is PipelineResult.Failure -> {
                        val errorMessage = buildString {
                            appendLine("❌ WORKFLOW FAILED")
                            appendLine()
                            appendLine("Error: ${result.error}")
                            appendLine()
                            appendLine("Available chats: ${availableChatIds.joinToString(", ")}")
                            appendLine()
                            appendLine("Troubleshooting:")
                            appendLine("• Ensure your Telegram bot token is valid")
                            appendLine("• Send a message to your bot to create a conversation")
                            appendLine("• Check your Gmail configuration")
                            appendLine("• Try the manual processing options")
                        }
                        
                        showError(errorMessage)
                        appendToLog("=== WORKFLOW FAILED ===")
                        appendToLog("Error: ${result.error}")
                    }
                }
                
                updateStatistics()
                
            } catch (e: Exception) {
                hideProgress()
                val errorMessage = buildString {
                    appendLine("❌ WORKFLOW ERROR")
                    appendLine()
                    appendLine("Exception: ${e.message}")
                    appendLine()
                    appendLine("Check the logs for more details and ensure all configuration steps are completed.")
                }
                
                showError(errorMessage)
                appendToLog("=== WORKFLOW ERROR ===")
                appendToLog("Exception: ${e.message}")
                Log.e(TAG, "Complete workflow failed", e)
            }
        }
    }
    
    /**
     * Apply LLM prompt configuration to the pipeline
     */
    private fun applyLlmPromptConfiguration() {
        if (!customPrompt.isNullOrBlank()) {
            llmProcessor?.setCustomPrompt(customPrompt)
            appendToLog("Applied custom LLM prompt: ${customPrompt!!.take(50)}${if (customPrompt!!.length > 50) "..." else ""}")
        } else {
            // Use default prompt strategy
            llmProcessor?.setPromptStrategy(DefaultPromptStrategy())
            appendToLog("Using default LLM prompt strategy")
        }
    }
    
    /**
     * Show processing options (kept for manual control)
     */
    private fun showProcessingOptions() {
        if (!validateConfiguration()) {
            return
        }
        
        val options = arrayOf(
            "🚀 Run Complete Automated Workflow",
            "📱 Process Recent Conversation (Manual)",
            "📝 Process Single Message", 
            "📦 Process All Recent Updates"
        )
        
        android.app.AlertDialog.Builder(this)
            .setTitle("Select Processing Mode")
            .setMessage("Choose how you want to process Telegram messages:\n\n" +
                       "• Automated: Full workflow with smart chat selection\n" +
                       "• Manual: Specify exact chat ID and parameters\n" +
                       "• Single: Process one specific message\n" +
                       "• Batch: Process multiple recent updates")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> runCompleteWorkflow()
                    1 -> showRecentConversationDialog()
                    2 -> showSingleMessageDialog()
                    3 -> showBatchProcessingDialog()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun testConnections() {
        lifecycleScope.launch {
            try {
                // First, debug the chat ID configuration before testing connections
                debugChatIdConfiguration()
                
                ensurePipelineInitialized()
                showProgress("Testing connections...")
                
                // First, reset the update tracking to ensure we get all messages
                pipeline!!.resetTelegramTracking()
                
                val results = pipeline!!.testConnections()
                
                hideProgress()
                
                // Get available chat IDs for additional info with enhanced debugging
                val availableChatIds = try {
                    Log.d("TelegramToGmailActivity", "Getting available chat IDs for debugging...")
                    pipeline!!.getAvailableChatIds()
                } catch (e: Exception) {
                    Log.e("TelegramToGmailActivity", "Failed to get available chat IDs", e)
                    emptyList<String>()
                }
                
                // Get some debugging info about raw updates
                val debugInfo = try {
                    Log.d("TelegramToGmailActivity", "Getting debug information...")
                    pipeline!!.getDebugInfo()
                } catch (e: Exception) {
                    Log.e("TelegramToGmailActivity", "Failed to get debug info", e)
                    "Debug info unavailable: ${e.message}"
                }
                
                // Get saved chat ID for comparison
                val savedChatId = getSavedChatId()
                
                val message = buildString {
                    appendLine("📊 CONNECTION TEST RESULTS:")
                    appendLine()
                    appendLine("Telegram: ${if (results.telegramConnection) "✓ Connected" else "✗ Failed"}")
                    appendLine("Gmail: ${if (results.gmailConnection) "✓ Connected" else "✗ Failed"}")
                    appendLine()
                    appendLine("💾 SAVED CONFIGURATION:")
                    appendLine("Saved Chat ID: ${savedChatId ?: "Not set"}")
                    appendLine()
                    
                    if (availableChatIds.isNotEmpty()) {
                        appendLine("🎉 Available Conversations: ${availableChatIds.size}")
                        appendLine("Chat IDs: ${availableChatIds.take(3).joinToString(", ")}${if (availableChatIds.size > 3) "..." else ""}")
                        
                        // Check if saved chat ID matches any available chat IDs
                        if (!savedChatId.isNullOrEmpty()) {
                            val exactMatch = availableChatIds.contains(savedChatId)
                            val numericMatch = availableChatIds.any { 
                                it.toLongOrNull()?.toString() == savedChatId || 
                                savedChatId.toLongOrNull()?.toString() == it 
                            }
                            appendLine()
                            appendLine("🔍 CHAT ID VALIDATION:")
                            appendLine("Exact match: ${if (exactMatch) "✓ Yes" else "✗ No"}")
                            appendLine("Numeric match: ${if (numericMatch) "✓ Yes" else "✗ No"}")
                            
                            if (!exactMatch && !numericMatch) {
                                appendLine("⚠️ WARNING: Saved chat ID not found in available chats!")
                            }
                        }else {
                            appendLine()
                            appendLine("✅ Ready to run automated pipeline!")
                        }
                    } else {
                        appendLine("⚠️ No conversations found")
                        appendLine()
                        appendLine("� Debug Information:")
                        appendLine(debugInfo)
                        appendLine()
                        appendLine("�📱 Next steps:")
                        appendLine("1. Open Telegram")
                        appendLine("2. Search for your bot: @YourBotName")
                        appendLine("3. Send any message (e.g., 'Hello')")
                        appendLine("4. Wait 30 seconds for API sync")
                        appendLine("5. Test connections again")
                        appendLine()
                        appendLine("💡 If messages exist but not detected:")
                        appendLine("- Check bot token is correct")
                        appendLine("- Ensure bot has message access")
                        appendLine("- Try sending a new message")
                    }
                    
                    appendLine()
                    appendLine("Overall Status: ${if (results.allConnectionsWorking && availableChatIds.isNotEmpty()) "✅ Ready for pipeline" else "⚠️ Setup needed"}")
                }
                
                showConnectionResults(message, results.allConnectionsWorking)
                
            } catch (e: Exception) {
                hideProgress()
                showError("Connection test failed: ${e.message}")
            }
        }
    }
    
    private fun clearHistory() {
        pipeline?.clearHistory()
        updateStatistics()
        logOutput.text = ""
        Toast.makeText(this, "History cleared", Toast.LENGTH_SHORT).show()
    }
    
    private fun toggleInfoContent(contentLayout: LinearLayout) {
        val isVisible = contentLayout.visibility == View.VISIBLE
        contentLayout.visibility = if (isVisible) View.GONE else View.VISIBLE
    }
    
    private fun validateConfiguration(): Boolean {
        if (!step1Completed) {
            Toast.makeText(this, "Please configure Telegram bot first", Toast.LENGTH_SHORT).show()
            return false
        }
        if (!step2Completed) {
            Toast.makeText(this, "Please configure email recipients", Toast.LENGTH_SHORT).show()
            return false
        }
        if (!step3Completed) {
            Toast.makeText(this, "Please configure LLM prompt", Toast.LENGTH_SHORT).show()
            return false
        }
        if (!step4Completed) {
            Toast.makeText(this, "Please configure Gmail service", Toast.LENGTH_SHORT).show()
            return false
        }
        return true
    }
    
    private fun ensurePipelineInitialized() {
        if (pipeline == null) {
            // Validate all required fields before attempting to create pipeline
            if (botToken.isNullOrBlank()) {
                throw IllegalStateException("Bot token is not configured")
            }
            if (gmailService == null) {
                throw IllegalStateException("Gmail service is not initialized")
            }
            if (defaultSender.isNullOrBlank()) {
                throw IllegalStateException("Default sender email is not configured")
            }
            if (emailRecipients.isEmpty()) {
                throw IllegalStateException("Email recipients are not configured")
            }
            
            try {
                pipeline = TelegramToGmailPipelineBuilder()
                    .botToken(botToken!!)
                    .gmailService(gmailService!!)
                    .emailRecipients(emailRecipients)
                    .defaultSender(defaultSender!!)
                    .emailTemplate(currentTemplateType)
                    .batchSize(currentBatchSize)
                    .delayBetweenEmails(currentDelay)
                    .apply { 
                        // Add LLM processor if available
                        llmProcessor?.let { llmProcessor(it) }
                    }
                    .build()
                
                pipeline!!.addObserver(this)
                
                // Set custom prompt if available
                customPrompt?.let { prompt ->
                    val strategy = when {
                        prompt.contains("meeting", ignoreCase = true) -> AnalysisPromptStrategy()
                        prompt.contains("task", ignoreCase = true) -> AnalysisPromptStrategy()
                        else -> DefaultPromptStrategy()
                    }
                    pipeline!!.setPromptStrategy(strategy)
                }
                
                Log.d(TAG, "Pipeline initialized successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize pipeline", e)
                throw IllegalStateException("Failed to initialize pipeline: ${e.message}", e)
            }
        }
    }
    
    private fun handlePipelineResult(result: PipelineResult) {
        when (result) {
            is PipelineResult.Success -> {
                val message = buildString {
                    appendLine("✓ Pipeline completed successfully!")
                    appendLine("Message ID: ${result.messageId}")
                    appendLine("Processed messages: ${result.processedMessages}")
                    appendLine("Email sent: ${if (result.emailSent) "Yes" else "No"}")
                    result.batchInfo?.let { appendLine("Batch info: $it") }
                }
                showSuccess(message)
                updateStatistics()
            }
            is PipelineResult.Failure -> {
                showError("Pipeline failed: ${result.error}")
            }
        }
    }
    
    private fun updateUI() {
        // Update step statuses
        step1StatusText.text = if (step1Completed) "✓ Telegram Bot Configured" else "Configure Telegram Bot"
        step2StatusText.text = if (step2Completed) "✓ Email Recipients Set" else if (!step1Completed) "Complete Step 1 first" else "Set Email Recipients"
        step3StatusText.text = if (step3Completed) "✓ LLM Prompt Configured" else if (!step2Completed) "Complete Step 2 first" else "Configure LLM Prompt"
        step4StatusText.text = if (step4Completed) "✓ Gmail Service Ready" else if (!step3Completed) "Complete Step 3 first" else "Configure Gmail Service"
        
        // Update button states with step numbers
        step1Button.text = if (step1Completed) "Step 1: Change Bot Token" else "Step 1: Configure Telegram"
        step2Button.text = if (step2Completed) "Step 2: Change Recipients" else "Step 2: Set Email Recipients"
        step3Button.text = if (step3Completed) "Step 3: Change Prompt" else "Step 3: Configure Prompt"
        step4Button.text = if (step4Completed) "Step 4: Reconfigure Gmail" else "Step 4: Configure Gmail"
        
        // Sequential step availability
        // Step 1 is always available
        step1Card.alpha = 1.0f
        step1Button.isEnabled = true
        
        // Step 2 - only available after step 1
        step2Card.alpha = if (step1Completed) 1.0f else 0.5f
        step2Button.isEnabled = step1Completed
        
        // Step 3 - only available after step 2
        step3Card.alpha = if (step2Completed) 1.0f else 0.5f
        step3Button.isEnabled = step2Completed
        
        // Step 4 - only available after step 3
        step4Card.alpha = if (step3Completed) 1.0f else 0.5f
        step4Button.isEnabled = step3Completed
        
        // Update pipeline button
        val allConfigured = step1Completed && step2Completed && step3Completed && step4Completed
        runPipelineButton.isEnabled = allConfigured
        if (allConfigured) {
            runPipelineButton.text = "🚀 Run Automated Pipeline"
            // Add hint about long press for manual options
            runPipelineButton.contentDescription = "Tap to run automated workflow, long press for manual options"
        } else {
            runPipelineButton.text = "Complete Configuration First"
        }
        
        testConnectionsButton.isEnabled = allConfigured
        
        updateStatistics()
    }
    
    private fun updateStatistics() {
        pipeline?.let {
            val stats = it.getStatistics()
            statisticsText.text = buildString {
                appendLine("Pipeline Statistics:")
                appendLine("Total processed: ${stats.totalProcessedMessages}")
                appendLine("Successful operations: ${stats.successfulOperations}")
                appendLine("Batch operations: ${stats.batchOperations}")
                appendLine("Single operations: ${stats.singleOperations}")
                appendLine("Success rate: ${"%.1f".format(stats.successRate * 100)}%")
            }
        } ?: run {
            statisticsText.text = "No statistics available"
        }
    }
    
    private fun showProgress(message: String) {
        progressBar.visibility = View.VISIBLE
        statusText.text = message
        statusText.visibility = View.VISIBLE
    }
    
    private fun hideProgress() {
        progressBar.visibility = View.GONE
        statusText.visibility = View.GONE
    }
    
    private fun showSuccess(message: String) {
        android.app.AlertDialog.Builder(this)
            .setTitle("Success")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
        
        appendToLog("SUCCESS: $message")
    }
    
    private fun showError(message: String) {
        android.app.AlertDialog.Builder(this)
            .setTitle("Error")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
        
        appendToLog("ERROR: $message")
    }
    
    private fun showConnectionResults(message: String, success: Boolean) {
        android.app.AlertDialog.Builder(this)
            .setTitle(if (success) "Connection Test Passed" else "Connection Test Failed")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
        
        appendToLog("CONNECTION TEST: $message")
    }
    
    private fun appendToLog(message: String) {
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
        val logEntry = "[$timestamp] $message\n"
        logOutput.append(logEntry)
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        if (resultCode == Activity.RESULT_OK) {
            when (requestCode) {
                REQUEST_TELEGRAM_CONFIG -> {
                    // TelegramLoginActivity manages telegram state internally
                    // Get the bot token from the TelegramService
                    val telegramService = TelegramService(this)
                    step1Completed = telegramService.isLoggedIn()
                    if (step1Completed) {
                        // Get the actual bot token for the pipeline
                        botToken = telegramService.getBotToken() ?: "configured"
                        updateStepStatus(1, "✓ Telegram bot configured")
                    } else {
                        updateStepStatus(1, "⚠ Telegram configuration incomplete")
                    }
                    updateUI()
                    if (step1Completed) {
                        Toast.makeText(this, "Telegram bot configured successfully", Toast.LENGTH_SHORT).show()
                    }
                }
                REQUEST_EMAIL_CONFIG -> {
                    if (data != null) {
                        emailRecipients = data.getStringArrayListExtra("email_recipients") ?: emptyList()
                        defaultSender = data.getStringExtra("default_sender")
                        step2Completed = emailRecipients.isNotEmpty() && !defaultSender.isNullOrBlank()
                        if (step2Completed) {
                            updateStepStatus(2, "✓ Email configured: ${emailRecipients.size} recipient(s)")
                        } else {
                            updateStepStatus(2, "⚠ Email configuration incomplete")
                        }
                        updateUI()
                        if (step2Completed) {
                            Toast.makeText(this, "Email configuration updated", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                REQUEST_PROMPT_CONFIG -> {
                    if (data != null) {
                        // LlmPromptActivity returns "llm_prompt" instead of "custom_prompt"
                        customPrompt = data.getStringExtra("llm_prompt")
                        step3Completed = !customPrompt.isNullOrBlank()
                        if (step3Completed) {
                            updateStepStatus(3, "✓ LLM prompt configured")
                        } else {
                            updateStepStatus(3, "⚠ LLM prompt configuration incomplete")
                        }
                        updateUI()
                        if (step3Completed) {
                            Toast.makeText(this, "LLM prompt configured", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            
            // Recreate pipeline if all steps are completed
            if (step1Completed && step2Completed && step3Completed && step4Completed) {
                pipeline = null // Force recreation on next use
                appendToLog("All configuration steps completed - pipeline ready")
            }
        }
    }
    
    private fun updateStepStatus(step: Int, status: String) {
        when (step) {
            1 -> step1StatusText.text = status
            2 -> step2StatusText.text = status
            3 -> step3StatusText.text = status
            4 -> step4StatusText.text = status
        }
    }
    
    // PipelineObserver implementation
    override fun onPipelineStarted(message: String) {
        runOnUiThread {
            appendToLog("STARTED: $message")
        }
    }
    
    override fun onProgressUpdate(message: String) {
        runOnUiThread {
            statusText.text = message
            appendToLog("PROGRESS: $message")
        }
    }
    
    override fun onPipelineCompleted(message: String) {
        runOnUiThread {
            appendToLog("COMPLETED: $message")
        }
    }
    
    override fun onPipelineError(message: String) {
        runOnUiThread {
            appendToLog("ERROR: $message")
        }
    }
    
    /**
     * Helper method to get the saved chat ID from TelegramService
     */
    private fun getSavedChatId(): String? {
        val telegramService = TelegramService(this)
        val chatId = telegramService.getChatId()
        Log.d(TAG, "Retrieved saved chat ID: $chatId")
        return chatId
    }
    
    /**
     * Helper method to check if we have a valid saved chat ID
     */
    private fun hasSavedChatId(): Boolean {
        val chatId = getSavedChatId()
        val hasValidChatId = !chatId.isNullOrEmpty()
        Log.d(TAG, "Has valid saved chat ID: $hasValidChatId")
        return hasValidChatId
    }
    
    /**
     * Debug method to verify chat ID configuration
     */
    private fun debugChatIdConfiguration() {
        val telegramService = TelegramService(this)
        val isLoggedIn = telegramService.isLoggedIn()
        val botToken = telegramService.getBotToken()
        val chatId = telegramService.getChatId()
        
        val debugInfo = buildString {
            appendLine("🔍 Telegram Configuration Debug:")
            appendLine("Is logged in: $isLoggedIn")
            appendLine("Bot token exists: ${!botToken.isNullOrEmpty()}")
            appendLine("Bot token: ${botToken?.let { "${it.take(10)}..." } ?: "null"}")
            appendLine("Chat ID: $chatId")
            appendLine("Chat ID is valid: ${!chatId.isNullOrEmpty()}")
        }
        
        Log.d(TAG, debugInfo)
        appendToLog(debugInfo)
    }
}
