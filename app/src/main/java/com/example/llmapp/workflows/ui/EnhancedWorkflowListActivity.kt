package com.example.llmapp.workflows.ui

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.llmapp.R
import com.example.llmapp.workflows.services.ImageWorkflowConfig
import com.example.llmapp.workflows.services.WorkflowType
import com.example.llmapp.workflows.services.DestinationType
import com.example.llmapp.workflows.services.WorkflowConfigManager
import com.example.llmapp.workflows.services.ImageWorkflowBackgroundService
import kotlinx.coroutines.launch

/**
 * Enhanced activity for viewing and managing image workflows
 * Follows SOLID principles with clear separation of concerns
 */
class EnhancedWorkflowListActivity : ComponentActivity() {
    
    companion object {
        private const val TAG = "EnhancedWorkflowList"
        private const val REQUEST_CREATE_WORKFLOW = 1001
        private const val REQUEST_EDIT_WORKFLOW = 1002
    }
    
    private lateinit var workflowConfigManager: WorkflowConfigManager
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: WorkflowAdapter
    private lateinit var emptyView: LinearLayout
    private lateinit var createWorkflowButton: Button
    private lateinit var startServiceButton: Button
    private lateinit var stopServiceButton: Button
    
    private var workflows = mutableListOf<ImageWorkflowConfig>()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_enhanced_workflow_list)
        
        title = "Image Workflows"
        
        workflowConfigManager = WorkflowConfigManager(this)
        
        initializeViews()
        setupRecyclerView()
        setupEventListeners()
        
        loadWorkflows()
        updateServiceButtons()
    }
    
    private fun initializeViews() {
        recyclerView = findViewById(R.id.workflowRecyclerView)
        emptyView = findViewById(R.id.emptyView)
        createWorkflowButton = findViewById(R.id.createWorkflowButton)
        startServiceButton = findViewById(R.id.startServiceButton)
        stopServiceButton = findViewById(R.id.stopServiceButton)
    }
    
    private fun setupRecyclerView() {
        adapter = WorkflowAdapter(workflows) { workflow, action ->
            when (action) {
                WorkflowAction.EDIT -> editWorkflow(workflow)
                WorkflowAction.DELETE -> deleteWorkflow(workflow)
                WorkflowAction.TOGGLE -> toggleWorkflow(workflow)
            }
        }
        
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }
    
    private fun setupEventListeners() {
        createWorkflowButton.setOnClickListener {
            val intent = Intent(this, EnhancedWorkflowCreateActivity::class.java)
            startActivityForResult(intent, REQUEST_CREATE_WORKFLOW)
        }
        
        findViewById<Button>(R.id.createWorkflowButtonEmpty).setOnClickListener {
            val intent = Intent(this, EnhancedWorkflowCreateActivity::class.java)
            startActivityForResult(intent, REQUEST_CREATE_WORKFLOW)
        }
        
        startServiceButton.setOnClickListener {
            startImageWorkflowService()
        }
        
        stopServiceButton.setOnClickListener {
            stopImageWorkflowService()
        }
    }
    
    private fun loadWorkflows() {
        lifecycleScope.launch {
            try {
                workflows.clear()
                workflows.addAll(workflowConfigManager.getAllWorkflows())
                
                runOnUiThread {
                    adapter.notifyDataSetChanged()
                    updateEmptyView()
                }
                
                Log.d(TAG, "Loaded ${workflows.size} workflows")
                
            } catch (e: Exception) {
                Log.e(TAG, "Error loading workflows", e)
                runOnUiThread {
                    Toast.makeText(this@EnhancedWorkflowListActivity, "Error loading workflows", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    private fun updateEmptyView() {
        if (workflows.isEmpty()) {
            recyclerView.visibility = View.GONE
            emptyView.visibility = View.VISIBLE
        } else {
            recyclerView.visibility = View.VISIBLE
            emptyView.visibility = View.GONE
        }
    }
    
    private fun editWorkflow(workflow: ImageWorkflowConfig) {
        val intent = Intent(this, EnhancedWorkflowCreateActivity::class.java).apply {
            putExtra(EnhancedWorkflowCreateActivity.EXTRA_WORKFLOW_ID, workflow.workflowId)
            putExtra(EnhancedWorkflowCreateActivity.EXTRA_IS_EDIT, true)
        }
        startActivityForResult(intent, REQUEST_EDIT_WORKFLOW)
    }
    
    private fun deleteWorkflow(workflow: ImageWorkflowConfig) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Delete Workflow")
            .setMessage("Are you sure you want to delete '${workflow.workflowName}'?")
            .setPositiveButton("Delete") { _, _ ->
                lifecycleScope.launch {
                    try {
                        val deleted = workflowConfigManager.deleteWorkflow(workflow.workflowId)
                        if (deleted) {
                            runOnUiThread {
                                workflows.remove(workflow)
                                adapter.notifyDataSetChanged()
                                updateEmptyView()
                                Toast.makeText(this@EnhancedWorkflowListActivity, "Workflow deleted", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            runOnUiThread {
                                Toast.makeText(this@EnhancedWorkflowListActivity, "Failed to delete workflow", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error deleting workflow", e)
                        runOnUiThread {
                            Toast.makeText(this@EnhancedWorkflowListActivity, "Error deleting workflow", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun toggleWorkflow(workflow: ImageWorkflowConfig) {
        // For now, this doesn't change the workflow config itself
        // In a full implementation, you might have an "active" flag in the config
        Toast.makeText(this, "Edit the workflow to enable/disable features", Toast.LENGTH_SHORT).show()
    }
    
    private fun startImageWorkflowService() {
        try {
            val serviceIntent = Intent(this, ImageWorkflowBackgroundService::class.java)
            startForegroundService(serviceIntent)
            
            Toast.makeText(this, "Image workflow service started", Toast.LENGTH_SHORT).show()
            updateServiceButtons()
            
            Log.d(TAG, "Image workflow service started")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error starting image workflow service", e)
            Toast.makeText(this, "Failed to start service", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun stopImageWorkflowService() {
        try {
            val serviceIntent = Intent(this, ImageWorkflowBackgroundService::class.java)
            stopService(serviceIntent)
            
            Toast.makeText(this, "Image workflow service stopped", Toast.LENGTH_SHORT).show()
            updateServiceButtons()
            
            Log.d(TAG, "Image workflow service stopped")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping image workflow service", e)
            Toast.makeText(this, "Failed to stop service", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun updateServiceButtons() {
        val isServiceRunning = isServiceRunning()
        startServiceButton.isEnabled = !isServiceRunning
        stopServiceButton.isEnabled = isServiceRunning
        
        val statusText = findViewById<TextView>(R.id.serviceStatusText)
        statusText.text = if (isServiceRunning) "Service Status: Running" else "Service Status: Stopped"
        statusText.setTextColor(if (isServiceRunning) getColor(android.R.color.holo_green_dark) else getColor(android.R.color.holo_red_dark))
    }
    
    private fun isServiceRunning(): Boolean {
        return try {
            val activityManager = getSystemService(android.app.ActivityManager::class.java)
            @Suppress("DEPRECATION")
            val runningServices = activityManager.getRunningServices(Integer.MAX_VALUE)
            
            runningServices.any { service ->
                service.service.className == ImageWorkflowBackgroundService::class.java.name
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking service status", e)
            false
        }
    }
    
    override fun onResume() {
        super.onResume()
        loadWorkflows()
        updateServiceButtons()
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        when (requestCode) {
            REQUEST_CREATE_WORKFLOW, REQUEST_EDIT_WORKFLOW -> {
                if (resultCode == RESULT_OK) {
                    val workflowSaved = data?.getBooleanExtra("workflow_saved", false) ?: false
                    if (workflowSaved) {
                        loadWorkflows()
                        Toast.makeText(this, "Workflow saved successfully", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
}

/**
 * RecyclerView adapter for workflow list
 */
class WorkflowAdapter(
    private val workflows: MutableList<ImageWorkflowConfig>,
    private val onWorkflowAction: (ImageWorkflowConfig, WorkflowAction) -> Unit
) : RecyclerView.Adapter<WorkflowViewHolder>() {
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WorkflowViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_workflow, parent, false)
        return WorkflowViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: WorkflowViewHolder, position: Int) {
        holder.bind(workflows[position], onWorkflowAction)
    }
    
    override fun getItemCount(): Int = workflows.size
}

/**
 * ViewHolder for workflow items
 */
class WorkflowViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    private val workflowName: TextView = itemView.findViewById(R.id.workflowName)
    private val workflowDescription: TextView = itemView.findViewById(R.id.workflowDescription)
    private val workflowStatus: TextView = itemView.findViewById(R.id.workflowStatus)
    private val editButton: Button = itemView.findViewById(R.id.editButton)
    private val deleteButton: Button = itemView.findViewById(R.id.deleteButton)
    
    fun bind(workflow: ImageWorkflowConfig, onAction: (ImageWorkflowConfig, WorkflowAction) -> Unit) {
        workflowName.text = workflow.workflowName
        
        // Create description based on new workflow structure
        val features = mutableListOf<String>()
        when (workflow.workflowType) {
            WorkflowType.IMAGE_FORWARD -> {
                features.add("Image Forward")
                if (workflow.referenceImagePath.isNotEmpty()) {
                    features.add("Reference Set")
                }
            }
            WorkflowType.DOCUMENT_ANALYSIS -> {
                features.add("Document Analysis")
                features.add("Type: ${workflow.documentType}")
                features.add("Schedule: ${workflow.scheduledTime}")
            }
        }
        
        workflowDescription.text = if (features.isNotEmpty()) {
            features.joinToString(" • ")
        } else {
            "No features configured"
        }
        
        // Create status text based on destination
        val destination = when (workflow.destinationType) {
            DestinationType.GMAIL -> "Gmail: ${workflow.gmailRecipient}"
            DestinationType.TELEGRAM -> "Telegram: ${workflow.telegramChatId}"
        }
        
        workflowStatus.text = "Via: $destination"
        
        // Set status color - active if workflow is properly configured
        val isActive = when (workflow.workflowType) {
            WorkflowType.IMAGE_FORWARD -> workflow.referenceImagePath.isNotEmpty()
            WorkflowType.DOCUMENT_ANALYSIS -> workflow.documentType.isNotEmpty()
        }
        
        workflowStatus.setTextColor(
            if (isActive) itemView.context.getColor(android.R.color.holo_green_dark)
            else itemView.context.getColor(android.R.color.holo_orange_dark)
        )
        
        editButton.setOnClickListener {
            onAction(workflow, WorkflowAction.EDIT)
        }
        
        deleteButton.setOnClickListener {
            onAction(workflow, WorkflowAction.DELETE)
        }
        
        itemView.setOnClickListener {
            onAction(workflow, WorkflowAction.TOGGLE)
        }
    }
}

enum class WorkflowAction {
    EDIT, DELETE, TOGGLE
}
