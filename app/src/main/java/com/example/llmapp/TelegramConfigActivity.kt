package com.example.llmapp

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.activity.ComponentActivity

class TelegramConfigActivity : ComponentActivity() {
    
    private lateinit var botTokenInput: EditText
    private lateinit var saveButton: Button
    private lateinit var cancelButton: Button
    private lateinit var helpText: TextView
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_telegram_config)
        
        initializeViews()
        loadCurrentConfiguration()
        setupClickListeners()
    }
    
    private fun initializeViews() {
        botTokenInput = findViewById(R.id.botTokenInput)
        saveButton = findViewById(R.id.saveButton)
        cancelButton = findViewById(R.id.cancelButton)
        helpText = findViewById(R.id.helpText)
        
        helpText.text = """
            To configure Telegram bot:
            1. Message @BotFather on Telegram
            2. Send /newbot command
            3. Follow instructions to create bot
            4. Copy the bot token and paste it here
            
            Example: 123456789:ABCdefGHIjklMNOpqrsTUVwxyz
        """.trimIndent()
    }
    
    private fun loadCurrentConfiguration() {
        val currentToken = intent.getStringExtra("current_bot_token")
        if (!currentToken.isNullOrBlank()) {
            botTokenInput.setText(currentToken)
        }
    }
    
    private fun setupClickListeners() {
        saveButton.setOnClickListener {
            val token = botTokenInput.text.toString().trim()
            
            if (validateBotToken(token)) {
                val resultIntent = Intent().apply {
                    putExtra("bot_token", token)
                }
                setResult(Activity.RESULT_OK, resultIntent)
                finish()
            }
        }
        
        cancelButton.setOnClickListener {
            setResult(Activity.RESULT_CANCELED)
            finish()
        }
    }
    
    private fun validateBotToken(token: String): Boolean {
        if (token.isBlank()) {
            botTokenInput.error = "Bot token cannot be empty"
            return false
        }
        
        // Basic validation for Telegram bot token format
        val tokenPattern = Regex("^\\d+:[A-Za-z0-9_-]+$")
        if (!tokenPattern.matches(token)) {
            botTokenInput.error = "Invalid bot token format"
            return false
        }
        
        return true
    }
}
