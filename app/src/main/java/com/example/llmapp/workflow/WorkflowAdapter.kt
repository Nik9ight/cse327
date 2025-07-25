package com.example.llmapp.workflow

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.llmapp.R

/**
 * RecyclerView adapter for displaying workflows using ViewHolder pattern
 */
class WorkflowAdapter(
    private val onWorkflowClick: (SavedWorkflow) -> Unit,
    private val onRunClick: (SavedWorkflow) -> Unit = {},
    private val onEditClick: (SavedWorkflow) -> Unit = {},
    private val onDeleteClick: (SavedWorkflow) -> Unit = {},
    private val onDuplicateClick: (SavedWorkflow) -> Unit = {}
) : RecyclerView.Adapter<WorkflowAdapter.WorkflowViewHolder>() {
    
    private var workflows = listOf<SavedWorkflow>()
    private var selectedWorkflowId: String? = null
    private var runningWorkflowId: String? = null
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WorkflowViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_workflow, parent, false)
        return WorkflowViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: WorkflowViewHolder, position: Int) {
        holder.bind(workflows[position])
    }
    
    override fun getItemCount(): Int = workflows.size
    
    fun updateWorkflows(newWorkflows: List<SavedWorkflow>) {
        workflows = newWorkflows
        notifyDataSetChanged()
    }
    
    fun setSelectedWorkflow(workflowId: String) {
        val previousSelected = selectedWorkflowId
        selectedWorkflowId = workflowId
        
        // Refresh the previously selected item
        workflows.indexOfFirst { it.id == previousSelected }.takeIf { it >= 0 }?.let {
            notifyItemChanged(it)
        }
        
        // Refresh the newly selected item
        workflows.indexOfFirst { it.id == workflowId }.takeIf { it >= 0 }?.let {
            notifyItemChanged(it)
        }
    }
    
    fun setRunningWorkflowId(workflowId: String?) {
        val previousRunning = runningWorkflowId
        runningWorkflowId = workflowId
        
        Log.d("WorkflowAdapter", "setRunningWorkflowId called - previous: $previousRunning, new: $workflowId")
        
        // Refresh the previously running item
        previousRunning?.let { prevId ->
            workflows.indexOfFirst { it.id == prevId }.takeIf { it >= 0 }?.let {
                Log.d("WorkflowAdapter", "Refreshing previously running item at position: $it")
                notifyItemChanged(it)
            }
        }
        
        // Refresh the newly running item
        workflowId?.let { newId ->
            workflows.indexOfFirst { it.id == newId }.takeIf { it >= 0 }?.let {
                Log.d("WorkflowAdapter", "Refreshing newly running item at position: $it")
                notifyItemChanged(it)
            }
        }
        
        // If no specific workflow is running/stopping, refresh all items
        if (previousRunning == null && workflowId != null) {
            Log.d("WorkflowAdapter", "Starting workflow execution, refreshing all items")
            notifyDataSetChanged()
        } else if (previousRunning != null && workflowId == null) {
            Log.d("WorkflowAdapter", "Workflow execution completed, refreshing all items")
            notifyDataSetChanged()
        }
    }
    
    inner class WorkflowViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val nameText: TextView = itemView.findViewById(R.id.workflowName)
        private val typeText: TextView = itemView.findViewById(R.id.workflowType)
        private val descriptionText: TextView = itemView.findViewById(R.id.workflowDescription)
        private val statusBadge: TextView = itemView.findViewById(R.id.statusBadge)
        private val workflowIcon: ImageView = itemView.findViewById(R.id.workflowIcon)
        private val headerClickableArea: View = itemView.findViewById(R.id.headerClickableArea)
        
        // Action buttons
        private val runButton: Button = itemView.findViewById(R.id.runButton)
        private val editButton: Button = itemView.findViewById(R.id.editButton)
        private val deleteButton: Button = itemView.findViewById(R.id.deleteButton)
        private val duplicateButton: Button = itemView.findViewById(R.id.duplicateButton)
        
        fun bind(workflow: SavedWorkflow) {
            nameText.text = workflow.name
            typeText.text = workflow.type.getDisplayName()
            descriptionText.text = workflow.description.ifBlank { workflow.type.getDescription() }
            
            Log.d("WorkflowAdapter", "Binding workflow: ${workflow.name}, enabled: ${workflow.isEnabled}")
            
            // Set workflow icon and type icon
            val iconRes = when (workflow.type) {
                WorkflowType.GMAIL_TO_TELEGRAM -> R.drawable.ic_email_to_telegram
                WorkflowType.TELEGRAM_TO_GMAIL -> R.drawable.ic_telegram_to_email
            }
            workflowIcon.setImageResource(iconRes)
            
            // Set status badge
            val isRunning = workflow.id == runningWorkflowId
            Log.d("WorkflowAdapter", "Binding ${workflow.name}: runningWorkflowId=$runningWorkflowId, isRunning=$isRunning")
            
            if (isRunning) {
                statusBadge.text = "Running..."
                statusBadge.setBackgroundColor(itemView.context.getColor(R.color.running))
                Log.d("WorkflowAdapter", "Set status badge to 'Running...' for ${workflow.name}")
            } else {
                statusBadge.text = if (workflow.isEnabled) "Active" else "Disabled"
                statusBadge.setBackgroundColor(
                    if (workflow.isEnabled) {
                        itemView.context.getColor(R.color.status_enabled)
                    } else {
                        itemView.context.getColor(R.color.status_disabled)
                    }
                )
                Log.d("WorkflowAdapter", "Set status badge to '${statusBadge.text}' for ${workflow.name}")
            }
            
            // Highlight selected item
            itemView.isSelected = workflow.id == selectedWorkflowId
            itemView.setBackgroundResource(
                if (workflow.id == selectedWorkflowId) {
                    R.drawable.selected_workflow_background
                } else {
                    R.drawable.workflow_item_background
                }
            )
            
            // Set click listener on header area only
            headerClickableArea.setOnClickListener {
                Log.d("WorkflowAdapter", "Header area clicked for workflow: ${workflow.name}")
                onWorkflowClick(workflow)
            }
            
            // Set up action button click listeners
            runButton.setOnClickListener {
                Log.d("WorkflowAdapter", "Run button clicked for workflow: ${workflow.name} (ID: ${workflow.id})")
                onRunClick(workflow)
            }
            
            editButton.setOnClickListener {
                Log.d("WorkflowAdapter", "Edit button clicked for workflow: ${workflow.name}")
                onEditClick(workflow)
            }
            
            deleteButton.setOnClickListener {
                Log.d("WorkflowAdapter", "Delete button clicked for workflow: ${workflow.name}")
                onDeleteClick(workflow)
            }
            
            duplicateButton.setOnClickListener {
                Log.d("WorkflowAdapter", "Duplicate button clicked for workflow: ${workflow.name}")
                onDuplicateClick(workflow)
            }
            
            // Enable/disable run button based on workflow status and running state
            val isAnotherWorkflowRunning = runningWorkflowId != null && !isRunning
            
            runButton.isEnabled = workflow.isEnabled && !isAnotherWorkflowRunning
            runButton.text = if (isRunning) "Running..." else "Run"
            runButton.alpha = if (runButton.isEnabled) 1.0f else 0.5f
            
            Log.d("WorkflowAdapter", "Button states for ${workflow.name}: runEnabled=${runButton.isEnabled}, text='${runButton.text}', isAnotherRunning=$isAnotherWorkflowRunning")
            
            // Disable all buttons when any workflow is running
            editButton.isEnabled = !isAnotherWorkflowRunning && !isRunning
            deleteButton.isEnabled = !isAnotherWorkflowRunning && !isRunning
            duplicateButton.isEnabled = !isAnotherWorkflowRunning && !isRunning
            
            editButton.alpha = if (editButton.isEnabled) 1.0f else 0.5f
            deleteButton.alpha = if (deleteButton.isEnabled) 1.0f else 0.5f
            duplicateButton.alpha = if (duplicateButton.isEnabled) 1.0f else 0.5f
            
            Log.d("WorkflowAdapter", "Run button enabled: ${runButton.isEnabled}, visible: ${runButton.visibility == View.VISIBLE}, running: $isRunning")
            
            // Ensure buttons are visible
            runButton.visibility = View.VISIBLE
            editButton.visibility = View.VISIBLE
            deleteButton.visibility = View.VISIBLE
            duplicateButton.visibility = View.VISIBLE
            
            // Show configuration summary
            val configSummary = when (val config = workflow.configuration) {
                is GmailToTelegramConfig -> {
                    "Emails: ${config.emailLimit} • Query: ${config.gmailSearchQuery.ifBlank { "Default" }}"
                }
                is TelegramToGmailConfig -> {
                    "Messages: ${config.messageLimit} • Recipients: ${config.gmailRecipients.size}"
                }
            }
            descriptionText.text = if (workflow.description.isNotBlank()) {
                "${workflow.description}\n$configSummary"
            } else {
                configSummary
            }
            
            // Make description visible if it has content
            descriptionText.visibility = View.VISIBLE
        }
    }
}
