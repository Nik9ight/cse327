package com.example.llmapp.new_implementation.demo

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.example.llmapp.R
import kotlinx.coroutines.launch

/**
 * Test Activity for the new implementation
 * Provides a simple UI to test the workflows
 */
class NewImplementationTestActivity : ComponentActivity() {
    
    private lateinit var workflowDemo: WorkflowDemo
    private lateinit var resultTextView: TextView
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Create a simple layout programmatically since we don't have XML
        setContentView(createTestLayout())
        
        workflowDemo = WorkflowDemo(this)
        
        // Find the results TextView
        val layout = findViewById<android.view.ViewGroup>(android.R.id.content).getChildAt(0) as android.widget.LinearLayout
        for (i in 0 until layout.childCount) {
            val view = layout.getChildAt(i)
            if (view is TextView && view.text.toString().contains("Results will appear here")) {
                resultTextView = view
                break
            }
        }
        
        setupClickListeners()
    }
    
    private fun createTestLayout(): android.view.View {
        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }
        
        // Title
        val title = TextView(this).apply {
            text = "New Implementation Test"
            textSize = 24f
            setPadding(0, 0, 0, 32)
        }
        layout.addView(title)
        
        // Test Component Button
        val testComponentsBtn = Button(this).apply {
            text = "Test Components"
        }
        layout.addView(testComponentsBtn)
        
        // Test Gmail to Telegram Button
        val testGmailToTelegramBtn = Button(this).apply {
            text = "Test Gmail → Telegram"
        }
        layout.addView(testGmailToTelegramBtn)
        
        // Test Telegram to Gmail Button
        val testTelegramToGmailBtn = Button(this).apply {
            text = "Test Telegram → Gmail"
        }
        layout.addView(testTelegramToGmailBtn)
        
        // Run All Tests Button
        val runAllTestsBtn = Button(this).apply {
            text = "Run All Tests"
        }
        layout.addView(runAllTestsBtn)
        
        // Results TextView
        val resultsText = TextView(this).apply {
            text = "Results will appear here..."
            textSize = 12f
            setPadding(0, 32, 0, 0)
        }
        layout.addView(resultsText)
        
        return layout
    }
    
    private fun setupClickListeners() {
        val buttons = listOf(
            "Test Components" to ::testComponents,
            "Test Gmail → Telegram" to ::testGmailToTelegram,
            "Test Telegram → Gmail" to ::testTelegramToGmail,
            "Run All Tests" to ::runAllTests
        )
        
        // Find buttons by text and set click listeners
        for (i in 0 until (findViewById<android.view.ViewGroup>(android.R.id.content).getChildAt(0) as android.widget.LinearLayout).childCount) {
            val view = (findViewById<android.view.ViewGroup>(android.R.id.content).getChildAt(0) as android.widget.LinearLayout).getChildAt(i)
            if (view is Button) {
                val buttonText = view.text.toString()
                buttons.find { it.first == buttonText }?.let { (_, action) ->
                    view.setOnClickListener { action() }
                }
            }
        }
    }
    
    private fun testComponents() {
        lifecycleScope.launch {
            resultTextView.text = "Testing components..."
            
            workflowDemo.testComponents { success, result ->
                runOnUiThread {
                    resultTextView.text = result
                    Toast.makeText(
                        this@NewImplementationTestActivity,
                        if (success) "Components test passed!" else "Components test failed!",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }
    
    private fun testGmailToTelegram() {
        lifecycleScope.launch {
            resultTextView.text = "Testing Gmail to Telegram workflow..."
            
            // You would need to provide actual credentials here
            val botToken = "YOUR_BOT_TOKEN" // Replace with actual token
            val chatId = "YOUR_CHAT_ID" // Replace with actual chat ID
            
            workflowDemo.testGmailToTelegram(botToken, chatId) { success, result ->
                runOnUiThread {
                    resultTextView.text = result
                    Toast.makeText(
                        this@NewImplementationTestActivity,
                        if (success) "Gmail→Telegram test passed!" else "Gmail→Telegram test failed!",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }
    
    private fun testTelegramToGmail() {
        lifecycleScope.launch {
            resultTextView.text = "Testing Telegram to Gmail workflow..."
            
            // You would need to provide actual credentials here
            val botToken = "YOUR_BOT_TOKEN" // Replace with actual token
            val recipients = listOf("your-email@example.com") // Replace with actual email
            
            workflowDemo.testTelegramToGmail(botToken, recipients) { success, result ->
                runOnUiThread {
                    resultTextView.text = result
                    Toast.makeText(
                        this@NewImplementationTestActivity,
                        if (success) "Telegram→Gmail test passed!" else "Telegram→Gmail test failed!",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }
    
    private fun runAllTests() {
        lifecycleScope.launch {
            resultTextView.text = "Running comprehensive tests..."
            
            // You would need to provide actual credentials here
            val botToken = "YOUR_BOT_TOKEN" // Replace with actual token  
            val chatId = "YOUR_CHAT_ID" // Replace with actual chat ID
            val recipients = listOf("your-email@example.com") // Replace with actual email
            
            workflowDemo.runAllTests(botToken, chatId, recipients) { success, result ->
                runOnUiThread {
                    resultTextView.text = result
                    Toast.makeText(
                        this@NewImplementationTestActivity,
                        if (success) "All tests passed!" else "Some tests failed!",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }
}
