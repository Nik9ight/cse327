package com.example.llmapp

import android.content.Intent
import android.os.Bundle
import android.os.PowerManager
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.llmapp.workflow.*
import com.example.llmapp.util.WorkflowValidationUtil
import kotlinx.coroutines.launch

class WorkflowListActivity : ComponentActivity(), WorkflowObserver {
    
    companion object {
        private const val TAG = "WorkflowListActivity"
        private const val REQUEST_CREATE_WORKFLOW = 1001
        private const val REQUEST_EDIT_WORKFLOW = 1002
    }
    
    // UI Components
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyStateLayout: LinearLayout
    private lateinit var progressBar: ProgressBar
    private lateinit var statusText: TextView
    private lateinit var createWorkflowFab: com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
    private lateinit var refreshButton: Button
    private lateinit var retryButton: Button
    
    // Workflow management
    private lateinit var workflowManager: WorkflowManager
    private lateinit var adapter: WorkflowAdapter
    private var selectedWorkflow: SavedWorkflow? = null
    private var wakeLock: PowerManager.WakeLock? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_workflow_list)
        
        initializeViews()
        initializeWorkflowManager()
        setupRecyclerView()
        setupClickListeners()
        loadWorkflows()
        
        Log.d(TAG, "WorkflowListActivity created")
    }
    
    override fun onDestroy() {
        super.onDestroy()
        workflowManager.removeObserver(this)
        releaseWakeLock()
    }
    
    private fun initializeViews() {
        recyclerView = findViewById(R.id.workflowsRecyclerView)
        emptyStateLayout = findViewById(R.id.emptyStateLayout)
        progressBar = findViewById(R.id.progressBar)
        statusText = findViewById(R.id.statusText)
        createWorkflowFab = findViewById(R.id.createWorkflowFab)
        refreshButton = findViewById(R.id.refreshButton)
        retryButton = findViewById(R.id.retryButton)
    }
    
    private fun initializeWorkflowManager() {
        workflowManager = WorkflowManager(this)
        workflowManager.addObserver(this)
        
        // Set up authentication callback for Gmail sign-in during workflow execution
        val authCallback = WorkflowValidationUtil.createGmailAuthenticationCallback(this)
        workflowManager.setAuthenticationCallback(authCallback)
        Log.d(TAG, "Authentication callback set up for workflow manager")
    }
    
    private fun setupRecyclerView() {
        adapter = WorkflowAdapter(
            onWorkflowClick = { workflow ->
                onWorkflowSelected(workflow)
            },
            onRunClick = { workflow ->
                runWorkflow(workflow)
            },
            onEditClick = { workflow ->
                editWorkflow(workflow)
            },
            onDeleteClick = { workflow ->
                deleteWorkflow(workflow)
            },
            onDuplicateClick = { workflow ->
                duplicateWorkflow(workflow)
            }
        )
        
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }
    
    private fun setupClickListeners() {
        createWorkflowFab.setOnClickListener {
            lifecycleScope.launch {
                try {
                    val intent = Intent(this@WorkflowListActivity, WorkflowCreateActivity::class.java)
                    startActivity(intent)
                } catch (e: Exception) {
                    Log.e(TAG, "Error launching workflow creation", e)
                    showError("Failed to open workflow creator")
                }
            }
        }
        
        refreshButton.setOnClickListener {
            loadWorkflows()
        }
        
        retryButton.setOnClickListener {
            loadWorkflows()
        }
    }
    
    private fun loadWorkflows() {
        showProgress("Loading workflows...")
        
        lifecycleScope.launch {
            try {
                Log.d(TAG, "Loading workflows from manager...")
                val workflows = workflowManager.getAllWorkflows()
                Log.d(TAG, "Loaded ${workflows.size} workflows from manager")
                
                workflows.forEach { workflow ->
                    Log.d(TAG, "Workflow: ${workflow.name} (${workflow.id}) - Type: ${workflow.type}, Enabled: ${workflow.isEnabled}")
                }
                
                runOnUiThread {
                    hideProgress()
                    adapter.updateWorkflows(workflows)
                    updateWorkflowRunningStatus() // Update running status when loading
                    
                    if (workflows.isEmpty()) {
                        showEmptyState()
                    } else {
                        hideEmptyState()
                    }
                }
                
                Log.d(TAG, "Loaded ${workflows.size} workflows")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load workflows", e)
                runOnUiThread {
                    hideProgress()
                    showError("Failed to load workflows: ${e.message}")
                }
            }
        }
    }
    
    private fun createNewWorkflow() {
        val intent = Intent(this, WorkflowCreateActivity::class.java)
        startActivityForResult(intent, REQUEST_CREATE_WORKFLOW)
    }
    
    private fun editWorkflow(workflow: SavedWorkflow) {
        val intent = Intent(this, WorkflowCreateActivity::class.java).apply {
            putExtra("workflow", workflow)
            putExtra("edit_mode", true)
        }
        startActivityForResult(intent, REQUEST_EDIT_WORKFLOW)
    }
    
    private fun runWorkflow(workflow: SavedWorkflow) {
        Log.d(TAG, "runWorkflow called for: ${workflow.name} (ID: ${workflow.id})")
        
        if (workflowManager.isWorkflowRunning()) {
            val runningId = workflowManager.getRunningWorkflowId()
            Log.w(TAG, "Another workflow is already running: $runningId")
            showError("Another workflow is already running. Please wait for it to complete.")
            return
        }
        
        // Validate workflow prerequisites before execution
        Log.d(TAG, "Validating workflow prerequisites...")
        val validationMessage = when (workflow.type) {
            WorkflowType.GMAIL_TO_TELEGRAM -> {
                val config = workflow.configuration as GmailToTelegramConfig
                WorkflowValidationUtil.getGmailToTelegramValidationMessage(this, config)
            }
            WorkflowType.TELEGRAM_TO_GMAIL -> {
                val config = workflow.configuration as TelegramToGmailConfig
                WorkflowValidationUtil.getTelegramToGmailValidationMessage(this, config)
            }
        }
        
        if (!validationMessage.canExecute) {
            Log.w(TAG, "Workflow validation failed: ${validationMessage.getSummary()}")
            showValidationError(validationMessage)
            return
        }
        
        Log.d(TAG, "Workflow validation passed, proceeding with execution")
        
        // Keep screen on during workflow execution
        acquireWakeLock()
        
        showProgress("Running workflow: ${workflow.name}...")
        updateWorkflowRunningStatus()
        
        Log.d(TAG, "Starting workflow execution in coroutine")
        lifecycleScope.launch {
            try {
                Log.d(TAG, "Calling workflowManager.executeWorkflow for: ${workflow.id}")
                val result = workflowManager.executeWorkflow(workflow.id)
                
                Log.d(TAG, "Workflow execution completed, processing result")
                runOnUiThread {
                    hideProgress()
                    releaseWakeLock()
                    updateWorkflowRunningStatus()
                    
                    result.fold(
                        onSuccess = { executionResult ->
                            Log.d(TAG, "Workflow execution result - success: ${executionResult.success}, message: ${executionResult.message}")
                            if (executionResult.success) {
                                showSuccess("Workflow completed successfully!\n${executionResult.message}")
                            } else {
                                showError("Workflow failed:\n${executionResult.message}")
                            }
                        },
                        onFailure = { exception ->
                            Log.e(TAG, "Workflow execution failed with exception", exception)
                            showError("Workflow execution failed:\n${exception.message}")
                        }
                    )
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Exception during workflow execution for: ${workflow.name}", e)
                runOnUiThread {
                    hideProgress()
                    releaseWakeLock()
                    updateWorkflowRunningStatus()
                    showError("Failed to run workflow: ${e.message}")
                }
            }
        }
    }
    
    private fun deleteWorkflow(workflow: SavedWorkflow) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Delete Workflow")
            .setMessage("Are you sure you want to delete '${workflow.name}'? This action cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                lifecycleScope.launch {
                    try {
                        workflowManager.deleteWorkflow(workflow.id)
                        Log.d(TAG, "Deleted workflow: ${workflow.name}")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to delete workflow: ${workflow.name}", e)
                        runOnUiThread {
                            showError("Failed to delete workflow: ${e.message}")
                        }
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun duplicateWorkflow(workflow: SavedWorkflow) {
        lifecycleScope.launch {
            try {
                val result = workflowManager.createWorkflow(
                    name = "${workflow.name} (Copy)",
                    type = workflow.type,
                    configuration = workflow.configuration
                )
                
                result.fold(
                    onSuccess = { duplicatedWorkflow ->
                        Log.d(TAG, "Duplicated workflow: ${workflow.name}")
                        showSuccess("Workflow duplicated successfully!")
                    },
                    onFailure = { exception ->
                        Log.e(TAG, "Failed to duplicate workflow: ${workflow.name}", exception)
                        showError("Failed to duplicate workflow: ${exception.message}")
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to duplicate workflow: ${workflow.name}", e)
                showError("Failed to duplicate workflow: ${e.message}")
            }
        }
    }
    
    private fun onWorkflowSelected(workflow: SavedWorkflow) {
        selectedWorkflow = workflow
        adapter.setSelectedWorkflow(workflow.id)
        
        Log.d(TAG, "Selected workflow: ${workflow.name} (${workflow.type.getDisplayName()})")
    }
    
    private fun showProgress(message: String) {
        progressBar.visibility = View.VISIBLE
        statusText.visibility = View.VISIBLE
        statusText.text = message
    }
    private fun hideProgress() {
        progressBar.visibility = View.GONE
        statusText.visibility = View.GONE
    }
    
    private fun showEmptyState() {
        emptyStateLayout.visibility = View.VISIBLE
        recyclerView.visibility = View.GONE
    }
    
    private fun hideEmptyState() {
        emptyStateLayout.visibility = View.GONE
        recyclerView.visibility = View.VISIBLE
    }
    
    private fun showSuccess(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
    
    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
    
    private fun showValidationError(validationMessage: WorkflowValidationUtil.ValidationMessage) {
        // Show a more detailed error message with action guidance
        val fullMessage = StringBuilder()
        fullMessage.append("⚠️ Cannot Execute Workflow\n\n")
        fullMessage.append(validationMessage.getSummary())
        
        if (validationMessage.criticalIssues.isNotEmpty()) {
            fullMessage.append("\n\n📋 Issues to resolve:\n")
            validationMessage.criticalIssues.forEachIndexed { index, issue ->
                fullMessage.append("${index + 1}. $issue\n")
            }
        }
        
        // For authentication issues, provide a direct action button
        if (validationMessage.hasGmailAuthIssue()) {
            fullMessage.append("\n💡 Tap 'OK' then go to Home > Sign In to authenticate Gmail")
        }
        
        if (validationMessage.hasTelegramConfigIssue()) {
            fullMessage.append("\n💡 Check your Telegram bot token and chat ID in workflow settings")
        }
        
        // Use an AlertDialog for better UX with longer messages
        android.app.AlertDialog.Builder(this)
            .setTitle("Workflow Validation Failed")
            .setMessage(fullMessage.toString())
            .setIcon(android.R.drawable.ic_dialog_alert)
            .setPositiveButton("OK") { _, _ ->
                // If it's a Gmail auth issue, we could potentially navigate to sign in
                if (validationMessage.hasGmailAuthIssue()) {
                    Log.d(TAG, "User acknowledged Gmail authentication requirement")
                }
            }
            .setNegativeButton("View Details") { _, _ ->
                // Show the full action guidance
                showValidationDetails(validationMessage.actionGuidance)
            }
            .show()
    }
    
    private fun showValidationDetails(actionGuidance: String) {
        android.app.AlertDialog.Builder(this)
            .setTitle("Setup Instructions")
            .setMessage(actionGuidance)
            .setIcon(android.R.drawable.ic_dialog_info)
            .setPositiveButton("Got it") { _, _ -> }
            .show()
    }
    
    private fun acquireWakeLock() {
        try {
            val powerManager = getSystemService(POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.SCREEN_DIM_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "LLMApp:WorkflowExecution"
            )
            wakeLock?.acquire(30 * 60 * 1000L) // 30 minutes max
            
            // Also keep screen on using window flag as backup
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            
            Log.d(TAG, "Wake lock acquired - screen will stay on during workflow execution")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to acquire wake lock", e)
        }
    }
    
    private fun releaseWakeLock() {
        try {
            wakeLock?.let { wl ->
                if (wl.isHeld) {
                    wl.release()
                    Log.d(TAG, "Wake lock released")
                }
            }
            wakeLock = null
            
            // Remove screen on flag
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to release wake lock", e)
        }
    }
    
    private fun updateWorkflowRunningStatus() {
        // Get running workflow ID from manager
        val runningWorkflowId = workflowManager.getRunningWorkflowId()
        
        // Update the adapter to reflect running status
        adapter.setRunningWorkflowId(runningWorkflowId)
        
        // Update UI elements based on running status
        val isRunning = runningWorkflowId != null
        createWorkflowFab.isEnabled = !isRunning
        refreshButton.isEnabled = !isRunning
        
        if (isRunning) {
            createWorkflowFab.alpha = 0.5f
            refreshButton.alpha = 0.5f
        } else {
            createWorkflowFab.alpha = 1.0f
            refreshButton.alpha = 1.0f
        }
    }
    // WorkflowObserver implementation
    override fun onWorkflowCreated(workflow: SavedWorkflow) {
        runOnUiThread {
            loadWorkflows()
            showSuccess("Workflow '${workflow.name}' created successfully!")
        }
    }
    
    override fun onWorkflowUpdated(workflow: SavedWorkflow) {
        runOnUiThread {
            loadWorkflows()
            showSuccess("Workflow '${workflow.name}' updated successfully!")
        }
    }
    
    override fun onWorkflowDeleted(workflow: SavedWorkflow) {
        runOnUiThread {
            selectedWorkflow = null
            loadWorkflows()
            showSuccess("Workflow '${workflow.name}' deleted successfully!")
        }
    }
    
    override fun onWorkflowStarted(workflow: SavedWorkflow) {
        runOnUiThread {
            updateWorkflowRunningStatus()
            Log.d(TAG, "Workflow started: ${workflow.name}")
        }
    }
    
    override fun onWorkflowCompleted(workflow: SavedWorkflow, result: WorkflowExecutionResult) {
        runOnUiThread {
            updateWorkflowRunningStatus()
            Log.d(TAG, "Workflow completed: ${workflow.name}, success: ${result.success}")
        }
    }
    
    override fun onWorkflowStopped(workflow: SavedWorkflow) {
        runOnUiThread {
            updateWorkflowRunningStatus()
            Log.d(TAG, "Workflow '${workflow.name}' was stopped")
        }
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        // Handle Gmail sign-in results
        // Check if this might be a Gmail sign-in request (RC_SIGN_IN = 9001)
        if (requestCode == 9001) {
            Log.d(TAG, "Received Gmail sign-in activity result: requestCode=$requestCode, resultCode=$resultCode")
            // Create a temporary GmailService to handle the activity result
            val gmailService = GmailService(this)
            gmailService.handleActivityResult(requestCode, resultCode, data)
        }
        
        if (resultCode == RESULT_OK) {
            when (requestCode) {
                REQUEST_CREATE_WORKFLOW, REQUEST_EDIT_WORKFLOW -> {
                    // Workflow was created or edited, reload the list
                    loadWorkflows()
                }
                9001 -> {
                    // Gmail sign-in completed successfully
                    Log.d(TAG, "Gmail sign-in completed successfully")
                    Toast.makeText(this, "Gmail authentication completed", Toast.LENGTH_SHORT).show()
                }
            }
        } else if (requestCode == 9001) {
            // Gmail sign-in was cancelled or failed
            Log.d(TAG, "Gmail sign-in was cancelled or failed")
            Toast.makeText(this, "Gmail sign-in cancelled", Toast.LENGTH_SHORT).show()
        }
    }
}
