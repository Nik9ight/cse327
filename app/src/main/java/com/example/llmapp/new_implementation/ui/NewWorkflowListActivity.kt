package com.example.llmapp.new_implementation.ui
import com.example.llmapp.new_implementation.integration.NewImplementationBridge
import com.example.llmapp.service.WorkflowBackgroundService
import com.example.llmapp.service.WorkflowSchedulerService
import com.example.llmapp.utils.ServiceManager

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
import kotlinx.coroutines.launch

class NewWorkflowListActivity : ComponentActivity() {
    
    private val TAG = "com.example.llmapp.new_implementation.ui.NewWorkflowListActivity"
    private lateinit var bridge: NewImplementationBridge
    private lateinit var workflowStorage: NewWorkflowStorage
    private lateinit var workflowRunner: WorkflowRunner
    
    private lateinit var recyclerView: RecyclerView
    private lateinit var addButton: Button
    private lateinit var refreshButton: Button
    private lateinit var emptyView: TextView
    
    private lateinit var adapter: WorkflowAdapter
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_new_workflow_list)
        
        bridge = NewImplementationBridge(this)
        workflowStorage = NewWorkflowStorage(this)
        workflowRunner = WorkflowRunner(this, bridge)
        
        initializeViews()
        setupRecyclerView()
        loadWorkflows()
    }
    
    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume called - refreshing workflows")
        loadWorkflows()
    }
    
    private fun initializeViews() {
        recyclerView = findViewById(R.id.workflowRecyclerView)
        addButton = findViewById(R.id.addWorkflowButton)
        refreshButton = findViewById(R.id.refreshButton)
        emptyView = findViewById(R.id.emptyView)
        
        addButton.setOnClickListener {
            val intent = Intent(this, NewWorkflowCreateActivity::class.java)
            startActivityForResult(intent, REQUEST_CREATE_WORKFLOW)
        }
        
        refreshButton.setOnClickListener {
            Log.d(TAG, "Manual refresh button clicked")
            loadWorkflows()
        }
        
        // Long click to show debug info
        refreshButton.setOnLongClickListener {
            showDebugInfo()
            true
        }
    }
    
    private fun showDebugInfo() {
        lifecycleScope.launch {
            try {
                val storageInfo = workflowStorage.getStorageInfo()
                val allWorkflows = workflowStorage.getAllWorkflows()
                val serviceStatus = ServiceManager.getBackgroundStatus(this@NewWorkflowListActivity)
                
                val debugMessage = buildString {
                    appendLine("🔍 Debug Information")
                    appendLine("==================")
                    appendLine(storageInfo)
                    appendLine()
                    appendLine("Workflows found:")
                    if (allWorkflows.isEmpty()) {
                        appendLine("  - No workflows found")
                    } else {
                        allWorkflows.forEach { workflow ->
                            appendLine("  - ${workflow.name} (ID: ${workflow.id})")
                        }
                    }
                    appendLine()
                    appendLine(serviceStatus)
                }
                
                runOnUiThread {
                    android.app.AlertDialog.Builder(this@NewWorkflowListActivity)
                        .setTitle("Debug Information")
                        .setMessage(debugMessage)
                        .setPositiveButton("OK", null)
                        .setNeutralButton("Restart Service") { _, _ ->
                            restartBackgroundService()
                        }
                        .show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get debug info", e)
            }
        }
    }
    
    private fun restartBackgroundService() {
        lifecycleScope.launch {
            try {
                ServiceManager.stopBackgroundService(this@NewWorkflowListActivity)
                kotlinx.coroutines.delay(1000) // Wait 1 second
                val started = ServiceManager.startBackgroundService(this@NewWorkflowListActivity)
                
                runOnUiThread {
                    val message = if (started) {
                        "Background service restarted successfully!"
                    } else {
                        "Failed to restart background service. Check permissions."
                    }
                    Toast.makeText(this@NewWorkflowListActivity, message, Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to restart service", e)
                runOnUiThread {
                    Toast.makeText(this@NewWorkflowListActivity, 
                        "Error restarting service: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    
    companion object {
        private const val REQUEST_CREATE_WORKFLOW = 1001
        private const val REQUEST_EDIT_WORKFLOW = 1002
    }
    
    private fun setupRecyclerView() {
        adapter = WorkflowAdapter(mutableListOf()) { workflow, action ->
            handleWorkflowAction(workflow, action)
        }
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }
    
    private fun loadWorkflows() {
        Log.d(TAG, "Loading workflows...")
        lifecycleScope.launch {
            try {
                val allWorkflows = workflowStorage.getAllWorkflows()
                Log.d(TAG, "Retrieved ${allWorkflows.size} workflows from storage")
                
                runOnUiThread {
                    adapter.updateWorkflows(allWorkflows)
                    updateEmptyView()
                    Log.d(TAG, "Updated UI with ${allWorkflows.size} workflows")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load workflows", e)
                runOnUiThread {
                    Toast.makeText(this@NewWorkflowListActivity, "Failed to load workflows", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    private fun updateEmptyView() {
        if (adapter.itemCount == 0) {
            recyclerView.visibility = View.GONE
            emptyView.visibility = View.VISIBLE
        } else {
            recyclerView.visibility = View.VISIBLE
            emptyView.visibility = View.GONE
        }
    }
    
    private fun handleWorkflowAction(workflow: SavedWorkflow, action: WorkflowAction) {
        when (action) {
            WorkflowAction.START -> startWorkflow(workflow)
            WorkflowAction.STOP -> stopWorkflow(workflow)
            WorkflowAction.RUN_ONCE -> runWorkflowOnce(workflow)
            WorkflowAction.DELETE -> deleteWorkflow(workflow)
            WorkflowAction.EDIT -> editWorkflow(workflow)
        }
    }
    
    private fun startWorkflow(workflow: SavedWorkflow) {
        lifecycleScope.launch {
            try {
                workflowRunner.startWorkflow(workflow)
                
                // Schedule workflow with background service
                WorkflowSchedulerService.scheduleWorkflow(
                    this@NewWorkflowListActivity,
                    workflow.id,
                    workflow.intervalSeconds.toLong()
                )
                
                // Ensure background service is running
                ServiceManager.startBackgroundService(this@NewWorkflowListActivity)
                
                // Update workflow status
                val updatedWorkflow = workflow.copy(isRunning = true)
                workflowStorage.updateWorkflow(updatedWorkflow)
                
                runOnUiThread {
                    loadWorkflows()
                    Toast.makeText(this@NewWorkflowListActivity, 
                        "Workflow '${workflow.name}' started with background execution", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start workflow", e)
                runOnUiThread {
                    Toast.makeText(this@NewWorkflowListActivity, 
                        "Failed to start workflow: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    
    private fun stopWorkflow(workflow: SavedWorkflow) {
        lifecycleScope.launch {
            try {
                workflowRunner.stopWorkflow(workflow.id)
                
                // Cancel scheduled workflow
                WorkflowSchedulerService.cancelWorkflow(this@NewWorkflowListActivity, workflow.id)
                
                // Update workflow status
                val updatedWorkflow = workflow.copy(isRunning = false)
                workflowStorage.updateWorkflow(updatedWorkflow)
                
                runOnUiThread {
                    loadWorkflows()
                    Toast.makeText(this@NewWorkflowListActivity, 
                        "Workflow '${workflow.name}' stopped", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to stop workflow", e)
                runOnUiThread {
                    Toast.makeText(this@NewWorkflowListActivity, 
                        "Failed to stop workflow: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    
    private fun runWorkflowOnce(workflow: SavedWorkflow) {
        lifecycleScope.launch {
            try {
                runOnUiThread {
                    Toast.makeText(this@NewWorkflowListActivity, "Running workflow once...", Toast.LENGTH_SHORT).show()
                }
                
                val result = workflowRunner.runWorkflowOnce(workflow)
                
                // Update last run time
                val updatedWorkflow = workflow.copy(lastRun = System.currentTimeMillis())
                workflowStorage.updateWorkflow(updatedWorkflow)
                
                runOnUiThread {
                    loadWorkflows()
                    if (result.success) {
                        Toast.makeText(this@NewWorkflowListActivity, 
                            "Workflow completed successfully! ✅", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(this@NewWorkflowListActivity, 
                            "Workflow failed: ${result.message}", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to run workflow", e)
                runOnUiThread {
                    Toast.makeText(this@NewWorkflowListActivity, 
                        "Failed to run workflow: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    
    private fun deleteWorkflow(workflow: SavedWorkflow) {
        // Stop workflow if running
        if (workflow.isRunning) {
            lifecycleScope.launch {
                workflowRunner.stopWorkflow(workflow.id)
                // Cancel scheduled workflow
                WorkflowSchedulerService.cancelWorkflow(this@NewWorkflowListActivity, workflow.id)
            }
        }
        
        lifecycleScope.launch {
            try {
                // Ensure workflow is cancelled from scheduler
                WorkflowSchedulerService.cancelWorkflow(this@NewWorkflowListActivity, workflow.id)
                
                workflowStorage.deleteWorkflow(workflow.id)
                
                runOnUiThread {
                    loadWorkflows()
                    Toast.makeText(this@NewWorkflowListActivity, 
                        "Workflow '${workflow.name}' deleted", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete workflow", e)
                runOnUiThread {
                    Toast.makeText(this@NewWorkflowListActivity, 
                        "Failed to delete workflow: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    
    private fun editWorkflow(workflow: SavedWorkflow) {
        val intent = Intent(this, NewWorkflowCreateActivity::class.java)
        intent.putExtra("workflow_id", workflow.id)
        startActivityForResult(intent, REQUEST_EDIT_WORKFLOW)
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        when (requestCode) {
            REQUEST_CREATE_WORKFLOW, REQUEST_EDIT_WORKFLOW -> {
                // Always refresh the list when returning from create/edit activity
                Log.d(TAG, "Returned from workflow activity (resultCode: $resultCode), refreshing list")
                loadWorkflows()
            }
        }
    }
}

enum class WorkflowAction {
    START, STOP, RUN_ONCE, DELETE, EDIT
}

class WorkflowAdapter(
    private var workflows: MutableList<SavedWorkflow>,
    private val onActionClicked: (SavedWorkflow, WorkflowAction) -> Unit
) : RecyclerView.Adapter<WorkflowAdapter.WorkflowViewHolder>() {
    
    fun updateWorkflows(newWorkflows: List<SavedWorkflow>) {
        android.util.Log.d("com.example.llmapp.new_implementation.ui.WorkflowAdapter", "updateWorkflows called with ${newWorkflows.size} workflows")
        for (workflow in newWorkflows) {
            android.util.Log.d("com.example.llmapp.new_implementation.ui.WorkflowAdapter", "- Workflow: ${workflow.name} (${workflow.id})")
        }
        workflows.clear()
        workflows.addAll(newWorkflows)
        notifyDataSetChanged()
        android.util.Log.d("com.example.llmapp.new_implementation.ui.WorkflowAdapter", "Adapter now has ${workflows.size} workflows")
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WorkflowViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_new_workflow, parent, false)
        return WorkflowViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: WorkflowViewHolder, position: Int) {
        holder.bind(workflows[position], onActionClicked)
    }
    
    override fun getItemCount() = workflows.size
    
    class WorkflowViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val nameText: TextView = itemView.findViewById(R.id.workflowName)
        private val typeText: TextView = itemView.findViewById(R.id.workflowType)
        private val statusText: TextView = itemView.findViewById(R.id.workflowStatus)
        private val intervalText: TextView = itemView.findViewById(R.id.workflowInterval)
        private val lastRunText: TextView = itemView.findViewById(R.id.workflowLastRun)
        
        private val startButton: Button = itemView.findViewById(R.id.startButton)
        private val stopButton: Button = itemView.findViewById(R.id.stopButton)
        private val runOnceButton: Button = itemView.findViewById(R.id.runOnceButton)
        private val deleteButton: Button = itemView.findViewById(R.id.deleteButton)
        
        fun bind(workflow: SavedWorkflow, onActionClicked: (SavedWorkflow, WorkflowAction) -> Unit) {
            nameText.text = workflow.name
            typeText.text = workflow.type.name.replace("_", " to ")
            intervalText.text = "Every ${workflow.intervalSeconds}s"
            
            if (workflow.isRunning) {
                statusText.text = "🟢 Running"
                startButton.visibility = View.GONE
                stopButton.visibility = View.VISIBLE
            } else {
                statusText.text = "⚪ Stopped"
                startButton.visibility = View.VISIBLE
                stopButton.visibility = View.GONE
            }
            
            lastRunText.text = if (workflow.lastRun != null) {
                "Last run: ${java.text.SimpleDateFormat("MMM dd, HH:mm", java.util.Locale.getDefault())
                    .format(java.util.Date(workflow.lastRun))}"
            } else {
                "Never run"
            }
            
            startButton.setOnClickListener { onActionClicked(workflow, WorkflowAction.START) }
            stopButton.setOnClickListener { onActionClicked(workflow, WorkflowAction.STOP) }
            runOnceButton.setOnClickListener { onActionClicked(workflow, WorkflowAction.RUN_ONCE) }
            deleteButton.setOnClickListener { onActionClicked(workflow, WorkflowAction.DELETE) }
        }
    }
}
