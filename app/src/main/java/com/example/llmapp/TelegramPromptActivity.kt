package com.example.llmapp

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.TextView
import androidx.activity.ComponentActivity

class TelegramPromptActivity : ComponentActivity() {
    
    private lateinit var promptTypeGroup: RadioGroup
    private lateinit var customPromptInput: EditText
    private lateinit var promptPreview: TextView
    private lateinit var saveButton: Button
    private lateinit var cancelButton: Button
    private lateinit var resetButton: Button
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_telegram_prompt)
        
        initializeViews()
        loadCurrentConfiguration()
        setupClickListeners()
    }
    
    private fun initializeViews() {
        promptTypeGroup = findViewById(R.id.promptTypeGroup)
        customPromptInput = findViewById(R.id.customPromptInput)
        promptPreview = findViewById(R.id.promptPreview)
        saveButton = findViewById(R.id.saveButton)
        cancelButton = findViewById(R.id.cancelButton)
        resetButton = findViewById(R.id.resetButton)
        
        updatePromptPreview()
    }
    
    private fun loadCurrentConfiguration() {
        val currentPrompt = intent.getStringExtra("current_prompt")
        if (!currentPrompt.isNullOrBlank()) {
            customPromptInput.setText(currentPrompt)
            promptTypeGroup.check(R.id.customPromptRadio)
        }
    }
    
    private fun setupClickListeners() {
        promptTypeGroup.setOnCheckedChangeListener { _, _ ->
            updatePromptPreview()
        }
        
        saveButton.setOnClickListener {
            val prompt = getSelectedPrompt()
            if (validatePrompt(prompt)) {
                val resultIntent = Intent().apply {
                    putExtra("custom_prompt", prompt)
                }
                setResult(Activity.RESULT_OK, resultIntent)
                finish()
            }
        }
        
        cancelButton.setOnClickListener {
            setResult(Activity.RESULT_CANCELED)
            finish()
        }
        
        resetButton.setOnClickListener {
            promptTypeGroup.check(R.id.defaultPromptRadio)
            customPromptInput.text.clear()
            updatePromptPreview()
        }
    }
    
    private fun updatePromptPreview() {
        val prompt = getSelectedPrompt()
        promptPreview.text = "Preview:\n\n$prompt"
    }
    
    private fun getSelectedPrompt(): String {
        return when (promptTypeGroup.checkedRadioButtonId) {
            R.id.defaultPromptRadio -> getDefaultPrompt()
            R.id.meetingPromptRadio -> getMeetingPrompt()
            R.id.taskPromptRadio -> getTaskPrompt()
            R.id.customPromptRadio -> customPromptInput.text.toString().trim()
            else -> getDefaultPrompt()
        }
    }
    
    private fun getDefaultPrompt(): String {
        return """
            Please analyze this Telegram message and provide a comprehensive summary suitable for an email.
            
            Include:
            1. Main topics or themes discussed
            2. Important information or decisions
            3. Action items or next steps (if any)
            4. Key participants and their roles
            5. Overall context and relevance
            
            Please provide a professional summary that would be appropriate for business email communication.
        """.trimIndent()
    }
    
    private fun getMeetingPrompt(): String {
        return """
            Analyze this Telegram conversation with focus on meeting-related information:
            
            Extract and summarize:
            1. Meeting requests or scheduling discussions
            2. Proposed dates, times, and locations
            3. Agenda items or topics to be discussed
            4. Attendees mentioned
            5. Action items or preparations needed
            6. Follow-up requirements
            
            Provide a structured summary suitable for meeting coordination.
        """.trimIndent()
    }
    
    private fun getTaskPrompt(): String {
        return """
            Analyze this Telegram message focusing on task and project management:
            
            Identify and extract:
            1. Tasks assigned or discussed
            2. Deadlines and timelines mentioned
            3. Project updates or status reports
            4. Blockers or issues raised
            5. Resource requirements
            6. Dependencies on other tasks or people
            7. Priority levels (if mentioned)
            
            Provide a task-oriented summary suitable for project management tracking.
        """.trimIndent()
    }
    
    private fun validatePrompt(prompt: String): Boolean {
        if (prompt.isBlank()) {
            customPromptInput.error = "Prompt cannot be empty"
            return false
        }
        
        if (prompt.length < 10) {
            customPromptInput.error = "Prompt too short (minimum 10 characters)"
            return false
        }
        
        if (prompt.length > 2000) {
            customPromptInput.error = "Prompt too long (maximum 2000 characters)"
            return false
        }
        
        return true
    }
}
