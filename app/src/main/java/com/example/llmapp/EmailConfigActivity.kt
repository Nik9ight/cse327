package com.example.llmapp

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.activity.ComponentActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn

class EmailConfigActivity : ComponentActivity() {
    
    private lateinit var recipientsInput: EditText
    private lateinit var senderInput: EditText
    private lateinit var addRecipientButton: Button
    private lateinit var recipientsList: ListView
    private lateinit var saveButton: Button
    private lateinit var cancelButton: Button
    private lateinit var login: Login
    
    private val recipients = mutableListOf<String>()
    private lateinit var recipientsAdapter: ArrayAdapter<String>
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_email_config)
        
        // Initialize Login to check for signed-in user
        login = Login(this)
        login.initGoogleSignIn()
        
        initializeViews()
        loadCurrentConfiguration()
        setupClickListeners()
    }
    
    override fun onResume() {
        super.onResume()
        // Check if user signed in while this activity was paused
        checkAndUpdateSignInStatus()
    }
    
    private fun checkAndUpdateSignInStatus() {
        val signedInAccount = GoogleSignIn.getLastSignedInAccount(this)
        if (signedInAccount != null && signedInAccount.email != null) {
            // Only update if field is empty or different
            if (senderInput.text.toString().trim() != signedInAccount.email) {
                senderInput.setText(signedInAccount.email)
                senderInput.isEnabled = false
                Toast.makeText(this, "Sender email updated from signed-in account", Toast.LENGTH_SHORT).show()
            }
        } else {
            // Re-enable field if user signed out
            senderInput.isEnabled = true
        }
    }
    
    private fun initializeViews() {
        recipientsInput = findViewById(R.id.recipientsInput)
        senderInput = findViewById(R.id.senderInput)
        addRecipientButton = findViewById(R.id.addRecipientButton)
        recipientsList = findViewById(R.id.recipientsList)
        saveButton = findViewById(R.id.saveButton)
        cancelButton = findViewById(R.id.cancelButton)
        
        recipientsAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, recipients)
        recipientsList.adapter = recipientsAdapter
    }
    
    private fun loadCurrentConfiguration() {
        val currentRecipients = intent.getStringArrayListExtra("current_recipients")
        val currentSender = intent.getStringExtra("current_sender")
        
        currentRecipients?.let {
            recipients.addAll(it)
            recipientsAdapter.notifyDataSetChanged()
        }
        
        // Check if user is signed in and automatically set sender email
        val signedInAccount = GoogleSignIn.getLastSignedInAccount(this)
        if (signedInAccount != null && signedInAccount.email != null) {
            senderInput.setText(signedInAccount.email)
            senderInput.isEnabled = false // Disable editing since it's auto-filled
            Toast.makeText(this, "Using signed-in email: ${signedInAccount.email}", Toast.LENGTH_SHORT).show()
        } else if (!currentSender.isNullOrBlank()) {
            senderInput.setText(currentSender)
        } else {
            // If not signed in, show message encouraging sign-in
            Toast.makeText(this, "Sign in to Gmail to auto-fill sender email", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun setupClickListeners() {
        addRecipientButton.setOnClickListener {
            val email = recipientsInput.text.toString().trim()
            if (validateEmail(email) && !recipients.contains(email)) {
                recipients.add(email)
                recipientsAdapter.notifyDataSetChanged()
                recipientsInput.text.clear()
            }
        }
        
        recipientsList.setOnItemLongClickListener { _, _, position, _ ->
            recipients.removeAt(position)
            recipientsAdapter.notifyDataSetChanged()
            Toast.makeText(this, "Recipient removed", Toast.LENGTH_SHORT).show()
            true
        }
        
        saveButton.setOnClickListener {
            val sender = senderInput.text.toString().trim()
            
            if (validateConfiguration(sender)) {
                val resultIntent = Intent().apply {
                    putStringArrayListExtra("email_recipients", ArrayList(recipients))
                    putExtra("default_sender", sender)
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
    
    private fun validateEmail(email: String): Boolean {
        if (email.isBlank()) {
            recipientsInput.error = "Email cannot be empty"
            return false
        }
        
        val emailPattern = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
        if (!emailPattern.matches(email)) {
            recipientsInput.error = "Invalid email format"
            return false
        }
        
        return true
    }
    
    private fun validateConfiguration(sender: String): Boolean {
        if (recipients.isEmpty()) {
            Toast.makeText(this, "Please add at least one recipient", Toast.LENGTH_SHORT).show()
            return false
        }
        
        // If sender field is disabled (auto-filled), it's already validated
        if (!senderInput.isEnabled) {
            return true
        }
        
        if (!validateEmailSender(sender)) {
            senderInput.error = "Invalid sender email format"
            return false
        }
        
        return true
    }
    
    private fun validateEmailSender(email: String): Boolean {
        if (email.isBlank()) {
            return false
        }
        
        val emailPattern = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
        return emailPattern.matches(email)
    }
}
