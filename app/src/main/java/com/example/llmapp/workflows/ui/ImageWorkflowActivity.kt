package com.example.llmapp.workflows.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.TextUtils
import android.util.Patterns
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.llmapp.R
import com.example.llmapp.workflows.services.ImageWorkflowBackgroundService
import com.example.llmapp.workflows.camera.PersonDetectionWorkflow
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.regex.Pattern
import java.io.File

/**
 * Configuration activity for image processing workflows
 */
class ImageWorkflowActivity : AppCompatActivity() {
    
    companion object {
        private const val PERMISSIONS_REQUEST_CODE = 1001
    }
    
    // Bot token input
    private lateinit var etBotToken: EditText
    private lateinit var btnLoadChats: Button
    
    // Person Detection Views
    private lateinit var switchPersonDetection: Switch
    private lateinit var spinnerPersonChat: Spinner
    private lateinit var etTargetSubject: EditText
    private lateinit var btnSelectReferenceImage: Button
    private lateinit var btnRemoveReferenceImage: Button
    private lateinit var ivReferenceImagePreview: ImageView
    private lateinit var tvReferenceImageStatus: TextView
    
    // Receipt Detection Views
    private lateinit var switchReceiptDetection: Switch
    private lateinit var spinnerReceiptChat: Spinner
    private lateinit var etReceiptSummaryTime: EditText
    
    // Control Views
    private lateinit var btnSave: Button
    private lateinit var btnStartService: Button
    private lateinit var tvServiceStatus: TextView
    private lateinit var progressBar: ProgressBar
    
    // Chat data
    private var chatMap = mutableMapOf<Long, Map<String, String?>>()
    private var personSelectedChatId: Long = 0
    private var receiptSelectedChatId: Long = 0
    private var selectedReferenceImagePath: String? = null
    
    private val activityScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    // Image selection launcher
    private val imageSelectionLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { handleSelectedImage(it) }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_image_workflow)
        
        initializeViews()
        setupListeners()
        loadSavedConfiguration()
        loadExistingReferenceImage()
        
        // Check and request permissions
        checkAndRequestPermissions()
    }
    
    private fun initializeViews() {
        // Bot token views
        etBotToken = findViewById(R.id.etBotToken)
        btnLoadChats = findViewById(R.id.btnLoadChats)
        progressBar = findViewById(R.id.progressBar)
        
        // Person Detection Views
        switchPersonDetection = findViewById(R.id.switchPersonDetection)
        spinnerPersonChat = findViewById(R.id.spinnerPersonChat)
        etTargetSubject = findViewById(R.id.etTargetSubject)
        btnSelectReferenceImage = findViewById(R.id.btnSelectReferenceImage)
        btnRemoveReferenceImage = findViewById(R.id.btnRemoveReferenceImage)
        ivReferenceImagePreview = findViewById(R.id.ivReferenceImagePreview)
        tvReferenceImageStatus = findViewById(R.id.tvReferenceImageStatus)
        
        // Receipt Detection Views
        switchReceiptDetection = findViewById(R.id.switchReceiptDetection)
        spinnerReceiptChat = findViewById(R.id.spinnerReceiptChat)
        etReceiptSummaryTime = findViewById(R.id.etReceiptSummaryTime)
        
        // Control Views
        btnSave = findViewById(R.id.btnSave)
        btnStartService = findViewById(R.id.btnStartService)
        tvServiceStatus = findViewById(R.id.tvServiceStatus)
        
        // Set default values
        etReceiptSummaryTime.setText("20:00")
        progressBar.visibility = View.GONE
        
        // Set up action bar
        supportActionBar?.apply {
            title = "Image Workflow Setup"
            setDisplayHomeAsUpEnabled(true)
        }
    }
    
    private fun setupListeners() {
        btnLoadChats.setOnClickListener {
            val botToken = etBotToken.text.toString().trim()
            if (botToken.isNotEmpty()) {
                loadTelegramChats(botToken)
            } else {
                Toast.makeText(this, "Please enter bot token first", Toast.LENGTH_SHORT).show()
            }
        }
        
        btnSave.setOnClickListener {
            if (validateInput()) {
                saveConfiguration()
            }
        }
        
        btnStartService.setOnClickListener {
            if (validateInput()) {
                startImageWorkflowService()
            }
        }
        
        // Enable/disable inputs based on switches
        switchPersonDetection.setOnCheckedChangeListener { _, isChecked ->
            spinnerPersonChat.isEnabled = isChecked
            etTargetSubject.isEnabled = isChecked
            btnSelectReferenceImage.isEnabled = isChecked
            updateServiceStatus()
        }
        
        switchReceiptDetection.setOnCheckedChangeListener { _, isChecked ->
            spinnerReceiptChat.isEnabled = isChecked
            etReceiptSummaryTime.isEnabled = isChecked
            updateServiceStatus()
        }
        
        // Reference image listeners
        btnSelectReferenceImage.setOnClickListener {
            imageSelectionLauncher.launch("image/*")
        }
        
        btnRemoveReferenceImage.setOnClickListener {
            removeReferenceImage()
        }
        
        // Chat selection listeners
        spinnerPersonChat.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (position > 0) { // Skip the first "Select chat" item
                    val chatIds = chatMap.keys.toList()
                    if (position - 1 < chatIds.size) {
                        personSelectedChatId = chatIds[position - 1]
                    }
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        
        spinnerReceiptChat.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (position > 0) { // Skip the first "Select chat" item
                    val chatIds = chatMap.keys.toList()
                    if (position - 1 < chatIds.size) {
                        receiptSelectedChatId = chatIds[position - 1]
                    }
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }
    
    private fun loadTelegramChats(botToken: String) {
        progressBar.visibility = View.VISIBLE
        btnLoadChats.isEnabled = false
        
        activityScope.launch {
            try {
                val chats = withContext(Dispatchers.IO) {
                    getTelegramChats(botToken)
                }
                
                chatMap.clear()
                chatMap.putAll(chats)
                
                setupChatSpinners()
                
                Toast.makeText(this@ImageWorkflowActivity, "Loaded ${chats.size} chats", Toast.LENGTH_SHORT).show()
                
            } catch (e: Exception) {
                Toast.makeText(this@ImageWorkflowActivity, "Error loading chats: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                progressBar.visibility = View.GONE
                btnLoadChats.isEnabled = true
            }
        }
    }
    
    private fun setupChatSpinners() {
        val chatOptions = mutableListOf<String>()
        chatOptions.add("Select a chat")
        
        chatMap.forEach { (chatId, chatInfo) ->
            val displayName = when {
                !chatInfo["title"].isNullOrEmpty() -> chatInfo["title"]!! // Group/Channel title
                !chatInfo["chat_username"].isNullOrEmpty() -> "@${chatInfo["chat_username"]}" // Chat username
                !chatInfo["from_username"].isNullOrEmpty() -> "@${chatInfo["from_username"]}" // User username
                else -> "Chat $chatId" // Fallback
            }
            chatOptions.add(displayName)
        }
        
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, chatOptions)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        
        spinnerPersonChat.adapter = adapter
        spinnerReceiptChat.adapter = adapter
    }
    
    private suspend fun getTelegramChats(botToken: String): Map<Long, Map<String, String?>> = withContext(Dispatchers.IO) {
        val client = OkHttpClient()

        val request = Request.Builder()
            .url("https://api.telegram.org/bot$botToken/getUpdates")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("Failed to fetch chats: ${response.code}")
            }

            val json = JSONObject(response.body?.string() ?: "")
            val resultArray = json.optJSONArray("result") ?: return@withContext emptyMap()

            val chatMap = mutableMapOf<Long, Map<String, String?>>()

            for (i in 0 until resultArray.length()) {
                val updateObj = resultArray.optJSONObject(i)
                val messageObj = updateObj?.optJSONObject("message")
                    ?: updateObj?.optJSONObject("edited_message")
                    ?: updateObj?.optJSONObject("channel_post")
                    ?: continue

                val chatObj = messageObj.optJSONObject("chat") ?: continue
                val fromObj = messageObj.optJSONObject("from")

                val chatId = chatObj.optLong("id")
                val chatUsername = chatObj.optString("username", null)?.takeIf { it.isNotEmpty() }
                val chatTitle = chatObj.optString("title", null)?.takeIf { it.isNotEmpty() }
                val fromUsername = fromObj?.optString("username", null)?.takeIf { it.isNotEmpty() }

                chatMap[chatId] = mapOf(
                    "chat_username" to chatUsername,
                    "title" to chatTitle,
                    "from_username" to fromUsername
                )
            }

            return@withContext chatMap
        }
    }
    private fun validateInput(): Boolean {
        var isValid = true
        
        // Validate Person Detection
        if (switchPersonDetection.isChecked) {
            if (personSelectedChatId == 0L) {
                Toast.makeText(this, "Please select a chat for Person Detection", Toast.LENGTH_SHORT).show()
                return false
            }
        }
        
        // Validate Receipt Detection
        if (switchReceiptDetection.isChecked) {
            if (receiptSelectedChatId == 0L) {
                Toast.makeText(this, "Please select a chat for Receipt Detection", Toast.LENGTH_SHORT).show()
                return false
            }
            
            val summaryTime = etReceiptSummaryTime.text.toString().trim()
            if (!isValidTimeFormat(summaryTime)) {
                Toast.makeText(this, "Please enter valid time format (HH:MM)", Toast.LENGTH_SHORT).show()
                etReceiptSummaryTime.requestFocus()
                return false
            }
        }
        
        // At least one workflow must be enabled
        if (!switchPersonDetection.isChecked && !switchReceiptDetection.isChecked) {
            Toast.makeText(this, "Please enable at least one workflow", Toast.LENGTH_SHORT).show()
            return false
        }
        
        return isValid
    }

    
    private fun isValidTimeFormat(time: String): Boolean {
        val timePattern = Pattern.compile("^([01]?[0-9]|2[0-3]):[0-5][0-9]$")
        return timePattern.matcher(time).matches()
    }
    
    private fun saveConfiguration() {
        val sharedPrefs = getSharedPreferences("image_workflow_prefs", MODE_PRIVATE)
        val editor = sharedPrefs.edit()
        
        // Save bot token
        editor.putString("bot_token", etBotToken.text.toString().trim())
        
        // Save Person Detection config
        editor.putBoolean("person_workflow_enabled", switchPersonDetection.isChecked)
        editor.putLong("person_workflow_chat_id", personSelectedChatId)
        editor.putString("target_subject", etTargetSubject.text.toString().trim())
        
        // Save Receipt Detection config
        editor.putBoolean("receipt_workflow_enabled", switchReceiptDetection.isChecked)
        editor.putLong("receipt_workflow_chat_id", receiptSelectedChatId)
        editor.putString("receipt_summary_time", etReceiptSummaryTime.text.toString().trim())
        
        editor.apply()
        
        Toast.makeText(this, "Configuration saved!", Toast.LENGTH_SHORT).show()
        updateServiceStatus()
    }
    
    private fun loadSavedConfiguration() {
        val sharedPrefs = getSharedPreferences("image_workflow_prefs", MODE_PRIVATE)
        
        // Load bot token
        etBotToken.setText(sharedPrefs.getString("bot_token", ""))
        
        // Load Person Detection config
        switchPersonDetection.isChecked = sharedPrefs.getBoolean("person_workflow_enabled", false)
        
        // Safe loading of chat IDs (handle both String and Long for backwards compatibility)
        personSelectedChatId = try {
            // Try to get as Long first
            sharedPrefs.getLong("person_workflow_chat_id", 0L)
        } catch (e: ClassCastException) {
            // If it fails, try to get as String and convert
            val chatIdString = sharedPrefs.getString("person_workflow_chat_id", "0") ?: "0"
            try {
                chatIdString.toLong()
            } catch (e: NumberFormatException) {
                0L
            }
        }
        
        etTargetSubject.setText(sharedPrefs.getString("target_subject", ""))
        
        // Load Receipt Detection config
        switchReceiptDetection.isChecked = sharedPrefs.getBoolean("receipt_workflow_enabled", false)
        
        receiptSelectedChatId = try {
            // Try to get as Long first
            sharedPrefs.getLong("receipt_workflow_chat_id", 0L)
        } catch (e: ClassCastException) {
            // If it fails, try to get as String and convert
            val chatIdString = sharedPrefs.getString("receipt_workflow_chat_id", "0") ?: "0"
            try {
                chatIdString.toLong()
            } catch (e: NumberFormatException) {
                0L
            }
        }
        
        etReceiptSummaryTime.setText(sharedPrefs.getString("receipt_summary_time", "20:00"))
        
        // Update input states
        spinnerPersonChat.isEnabled = switchPersonDetection.isChecked
        etTargetSubject.isEnabled = switchPersonDetection.isChecked
        spinnerReceiptChat.isEnabled = switchReceiptDetection.isChecked
        etReceiptSummaryTime.isEnabled = switchReceiptDetection.isChecked
        
        updateServiceStatus()
    }
    
    private fun startImageWorkflowService() {
        val intent = Intent(this, ImageWorkflowBackgroundService::class.java).apply {
            // Person Detection config
            putExtra("person_workflow_enabled", switchPersonDetection.isChecked)
            putExtra("person_workflow_chat_id", personSelectedChatId.toString())
            putExtra("target_subject", etTargetSubject.text.toString().trim())
            
            // Receipt Detection config
            putExtra("receipt_workflow_enabled", switchReceiptDetection.isChecked)
            putExtra("receipt_workflow_chat_id", receiptSelectedChatId.toString())
            putExtra("receipt_summary_time", etReceiptSummaryTime.text.toString().trim())
        }
        
        startForegroundService(intent)
        
        Toast.makeText(this, "Image workflow service started!", Toast.LENGTH_SHORT).show()
        updateServiceStatus()
    }
    
    private fun updateServiceStatus() {
        val activeWorkflows = mutableListOf<String>()
        
        if (switchPersonDetection.isChecked) {
            activeWorkflows.add("Person Detection")
        }
        
        if (switchReceiptDetection.isChecked) {
            activeWorkflows.add("Receipt Detection")
        }
        
        val statusText = if (activeWorkflows.isEmpty()) {
            "No workflows enabled"
        } else {
            "Active workflows: ${activeWorkflows.joinToString(", ")}"
        }
        
        tvServiceStatus.text = statusText
    }
    
    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf<String>()
        
        // Storage permissions for Android 12 and below
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        } else {
            // Media permissions for Android 13+
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
            }
        }
        
        // Camera permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.CAMERA)
        }
        
        // Notification permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        
        if (permissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissions.toTypedArray(), PERMISSIONS_REQUEST_CODE)
        }
    }
    
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        when (requestCode) {
            PERMISSIONS_REQUEST_CODE -> {
                val deniedPermissions = mutableListOf<String>()
                
                for (i in permissions.indices) {
                    if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                        deniedPermissions.add(permissions[i])
                    }
                }
                
                if (deniedPermissions.isNotEmpty()) {
                    Toast.makeText(
                        this,
                        "Some permissions were denied. The app may not work properly. Please grant permissions in Settings.",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    Toast.makeText(this, "All permissions granted! You can now start the workflow service.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    /**
     * Handle selected reference image
     */
    private fun handleSelectedImage(uri: Uri) {
        try {
            // Copy image to app's internal storage
            val inputStream = contentResolver.openInputStream(uri)
            val fileName = "reference_image_${System.currentTimeMillis()}.jpg"
            val file = File(filesDir, fileName)
            
            inputStream?.use { input ->
                file.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            
            selectedReferenceImagePath = file.absolutePath
            
            // Set reference image in workflow
            activityScope.launch {
                try {
                    val workflow = PersonDetectionWorkflow(
                        this@ImageWorkflowActivity,
                        "",
                        workflowId = "person_detection"
                    )
                    
                    val success = workflow.setReferenceImage(file.absolutePath)
                    
                    if (success) {
                        withContext(Dispatchers.Main) {
                            updateReferenceImageUI(file.absolutePath)
                            Toast.makeText(this@ImageWorkflowActivity, "Reference image set successfully", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@ImageWorkflowActivity, "Failed to set reference image. No face detected.", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@ImageWorkflowActivity, "Error setting reference image: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            
        } catch (e: Exception) {
            Toast.makeText(this, "Error selecting image: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * Update reference image UI
     */
    private fun updateReferenceImageUI(imagePath: String) {
        try {
            val bitmap = BitmapFactory.decodeFile(imagePath)
            ivReferenceImagePreview.setImageBitmap(bitmap)
            ivReferenceImagePreview.visibility = View.VISIBLE
            btnRemoveReferenceImage.visibility = View.VISIBLE
            tvReferenceImageStatus.text = "Reference image set ✓"
            tvReferenceImageStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_light))
        } catch (e: Exception) {
            tvReferenceImageStatus.text = "Error loading image preview"
            tvReferenceImageStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_light))
        }
    }
    
    /**
     * Remove reference image
     */
    private fun removeReferenceImage() {
        try {
            val workflow = PersonDetectionWorkflow(
                this,
                "",
                workflowId = "person_detection"
            )
            workflow.removeReferenceImage()
            
            // Clean up UI
            ivReferenceImagePreview.setImageBitmap(null)
            ivReferenceImagePreview.visibility = View.GONE
            btnRemoveReferenceImage.visibility = View.GONE
            tvReferenceImageStatus.text = "No reference image selected"
            tvReferenceImageStatus.setTextColor(ContextCompat.getColor(this, android.R.color.secondary_text_dark))
            
            // Delete file if exists
            selectedReferenceImagePath?.let { path ->
                File(path).delete()
            }
            selectedReferenceImagePath = null
            
            Toast.makeText(this, "Reference image removed", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Error removing reference image: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * Load existing reference image on startup
     */
    private fun loadExistingReferenceImage() {
        try {
            val workflow = PersonDetectionWorkflow(
                this,
                "",
                workflowId = "person_detection"
            )
            
            if (workflow.hasReferenceImage()) {
                val imagePath = workflow.getReferenceImagePath()
                imagePath?.let { path ->
                    if (File(path).exists()) {
                        selectedReferenceImagePath = path
                        updateReferenceImageUI(path)
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore errors on startup
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        activityScope.cancel()
    }
    
    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}
