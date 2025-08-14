package com.example.llmapp.workflows.activities

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.MenuItem
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.example.llmapp.R
import com.example.llmapp.workflows.services.ImageWorkflowConfig
import com.example.llmapp.workflows.services.WorkflowType
import com.example.llmapp.workflows.services.DestinationType
import com.example.llmapp.workflows.services.WorkflowConfigManager
import com.example.llmapp.utils.ServiceManager

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
    private lateinit var etTelegramChatId: EditText
    
    private lateinit var btnCreate: Button
    private lateinit var workflowConfigManager: WorkflowConfigManager
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_image_workflow_creation)
        
        // Set up toolbar
        supportActionBar?.title = "Create Workflow"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        
        workflowConfigManager = WorkflowConfigManager(this)
        
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
        etTelegramChatId = findViewById(R.id.etTelegramChatId)
        
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
    }
    
    private fun showTelegramOptions() {
        layoutGmailOptions.visibility = LinearLayout.GONE
        layoutTelegramOptions.visibility = LinearLayout.VISIBLE
        
        // Clear gmail fields
        etGmailRecipient.text.clear()
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
                    val chatId = etTelegramChatId.text.toString().trim()
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
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressed()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
