package com.example.llmapp

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

/**
 * Activity for configuring the LLM prompt
 */
class LlmPromptActivity : AppCompatActivity() {
    
    private lateinit var promptInputLayout: TextInputLayout
    private lateinit var promptInput: TextInputEditText
    private lateinit var applyButton: Button
    private lateinit var cancelButton: Button
    private lateinit var resetButton: Button
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_llm_prompt)
        
        // Enable back button in action bar
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Set LLM Prompt"
        
        // Initialize UI components
        promptInputLayout = findViewById(R.id.promptInputLayout)
        promptInput = findViewById(R.id.promptInput)
        applyButton = findViewById(R.id.applyButton)
        cancelButton = findViewById(R.id.cancelButton)
        resetButton = findViewById(R.id.resetButton)
        
        // Set current prompt if provided
        val currentPrompt = intent.getStringExtra("current_prompt")
        if (!currentPrompt.isNullOrEmpty()) {
            promptInput.setText(currentPrompt)
        } else {
            promptInput.setText(DEFAULT_PROMPT)
        }
        
        // Set up button listeners
        applyButton.setOnClickListener {
            val prompt = promptInput.text.toString()
            val resultIntent = Intent()
            resultIntent.putExtra("llm_prompt", prompt)
            setResult(Activity.RESULT_OK, resultIntent)
            finish()
        }
        
        cancelButton.setOnClickListener {
            setResult(Activity.RESULT_CANCELED)
            finish()
        }
        
        resetButton.setOnClickListener {
            promptInput.setText(DEFAULT_PROMPT)
            Toast.makeText(this, "Reset to default prompt", Toast.LENGTH_SHORT).show()
        }
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                setResult(Activity.RESULT_CANCELED)
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    companion object {
        private const val DEFAULT_PROMPT = """Summarize the following email and make it as brief as possible while maintaining key information."""
    }
}
