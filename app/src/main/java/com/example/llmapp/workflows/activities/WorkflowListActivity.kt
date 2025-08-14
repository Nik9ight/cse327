package com.example.llmapp.workflows.activities

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.llmapp.R
import com.example.llmapp.workflows.services.ImageWorkflowConfig
import com.example.llmapp.workflows.services.WorkflowType
import com.example.llmapp.workflows.services.DestinationType
import com.example.llmapp.workflows.services.WorkflowConfigManager
import com.example.llmapp.workflows.adapters.WorkflowListAdapter
import com.example.llmapp.utils.ServiceManager
import com.google.android.material.floatingactionbutton.FloatingActionButton

/**
 * Activity for listing and managing image workflows
 * User can view, star, delete, and create workflows
 * Workflows run automatically when photos are taken
 */
class WorkflowListActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "WorkflowListActivity"
    }
    
    private lateinit var recyclerView: RecyclerView
    private lateinit var workflowAdapter: WorkflowListAdapter
    private lateinit var fabCreate: FloatingActionButton
    private lateinit var workflowConfigManager: WorkflowConfigManager

    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_image_workflow_list)
        
        // Set up toolbar
        supportActionBar?.title = "Workflows"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        
        workflowConfigManager = WorkflowConfigManager(this)
        
        initViews()
        setupRecyclerView()
        loadWorkflows()
    }
    
    private fun initViews() {
        recyclerView = findViewById(R.id.recyclerViewWorkflows)
        fabCreate = findViewById(R.id.fabCreateWorkflow)
        
        fabCreate.setOnClickListener {
            startWorkflowCreation()
        }
    }
    
    private fun setupRecyclerView() {
        workflowAdapter = WorkflowListAdapter(
            onStarClick = { workflow ->
                toggleWorkflowStar(workflow)
            },
            onRunClick = { workflow ->
                showWorkflowInfo(workflow)
            },
            onDeleteClick = { workflow ->
                showDeleteConfirmation(workflow)
            },
            onWorkflowClick = { workflow ->
                showWorkflowInfo(workflow)
            }
        )
        
        recyclerView.apply {
            layoutManager = LinearLayoutManager(this@WorkflowListActivity)
            adapter = workflowAdapter
        }
    }
    
    private fun loadWorkflows() {
        try {
            val workflows = workflowConfigManager.getAllWorkflows()
            
            // Sort workflows: starred first, then by creation order
            val sortedWorkflows = workflows.sortedWith(
                compareByDescending<ImageWorkflowConfig> { it.isStarred }
                    .thenBy { it.workflowName }
            )
            
            workflowAdapter.updateWorkflows(sortedWorkflows)
            
            Log.d(TAG, "Loaded ${workflows.size} workflows")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error loading workflows", e)
        }
    }
    
    private fun startWorkflowCreation() {
        val intent = Intent(this, WorkflowCreationActivity::class.java)
        startActivity(intent)
    }
    
    private fun toggleWorkflowStar(workflow: ImageWorkflowConfig) {
        try {
            val updatedWorkflow = workflow.copy(isStarred = !workflow.isStarred)
            val success = workflowConfigManager.updateWorkflow(updatedWorkflow)
            
            if (success) {
                Log.d(TAG, "Workflow ${workflow.workflowName} star toggled to ${updatedWorkflow.isStarred}")
                loadWorkflows() // Refresh the list
                
                // Start image workflow service if needed (workflow may have become active)
                ServiceManager.startImageWorkflowServiceIfNeeded(this)
            } else {
                Log.e(TAG, "Failed to update workflow star status")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error toggling workflow star", e)
        }
    }
    
    private fun showDeleteConfirmation(workflow: ImageWorkflowConfig) {
        AlertDialog.Builder(this)
            .setTitle("Delete Workflow")
            .setMessage("Are you sure you want to delete '${workflow.workflowName}'?\n\nThis action cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                deleteWorkflow(workflow)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun deleteWorkflow(workflow: ImageWorkflowConfig) {
        try {
            val success = workflowConfigManager.deleteWorkflow(workflow.workflowId)
            
            if (success) {
                Log.d(TAG, "Workflow ${workflow.workflowName} deleted successfully")
                loadWorkflows() // Refresh the list
            } else {
                Log.e(TAG, "Failed to delete workflow")
                // Could show error dialog to user
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting workflow", e)
        }
    }
    
    /**
     * Show workflow information and status
     */
    private fun showWorkflowInfo(workflow: ImageWorkflowConfig) {
        val info = when (workflow.workflowType) {
            WorkflowType.IMAGE_FORWARD -> {
                val referenceFileName = workflow.referenceImagePath.substringAfterLast("/")
                """
                📷 Image Forward Workflow
                
                Reference Image: $referenceFileName
                Destination: ${getDestinationText(workflow)}
                
                This workflow automatically:
                • Detects persons in new photos
                • Compares them with your reference image
                • Forwards matches to your destination
                """.trimIndent()
            }
            WorkflowType.DOCUMENT_ANALYSIS -> {
                """
                📄 Document Analysis Workflow
                
                Document Type: ${workflow.documentType}
                Daily Summary Time: ${workflow.scheduledTime}
                Destination: ${getDestinationText(workflow)}
                
                This workflow automatically:
                • Detects documents in new photos
                • Analyzes ${workflow.documentType} documents
                • Sends daily summary reports at ${workflow.scheduledTime}
                """.trimIndent()
            }
        }
        
        AlertDialog.Builder(this)
            .setTitle("${workflow.workflowName}")
            .setMessage(info)
            .setPositiveButton("OK", null)
            .show()
    }
    
    private fun getDestinationText(workflow: ImageWorkflowConfig): String {
        return when (workflow.destinationType) {
            DestinationType.GMAIL -> "Gmail: ${workflow.gmailRecipient}"
            DestinationType.TELEGRAM -> "Telegram: ${workflow.telegramChatId}"
        }
    }
    
    override fun onResume() {
        super.onResume()
        // Refresh the list when returning from workflow creation
        loadWorkflows()
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
