package com.example.llmapp.workflows.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.llmapp.R
import com.example.llmapp.workflows.services.ImageWorkflowConfig
import com.example.llmapp.workflows.services.WorkflowType
import com.example.llmapp.workflows.services.DestinationType

/**
 * Adapter for displaying workflow list with star, run, and delete options
 */
class WorkflowListAdapter(
    private val onStarClick: (ImageWorkflowConfig) -> Unit,
    private val onRunClick: (ImageWorkflowConfig) -> Unit,
    private val onDeleteClick: (ImageWorkflowConfig) -> Unit,
    private val onWorkflowClick: (ImageWorkflowConfig) -> Unit
) : RecyclerView.Adapter<WorkflowListAdapter.WorkflowViewHolder>() {
    
    private var workflows = listOf<ImageWorkflowConfig>()
    
    fun updateWorkflows(newWorkflows: List<ImageWorkflowConfig>) {
        workflows = newWorkflows
        notifyDataSetChanged()
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WorkflowViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_image_workflow, parent, false)
        return WorkflowViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: WorkflowViewHolder, position: Int) {
        holder.bind(workflows[position])
    }
    
    override fun getItemCount(): Int = workflows.size
    
    inner class WorkflowViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvWorkflowName: TextView = itemView.findViewById(R.id.tvWorkflowName)
        private val tvWorkflowType: TextView = itemView.findViewById(R.id.tvWorkflowType)
        private val tvDestination: TextView = itemView.findViewById(R.id.tvDestination)
        private val tvDetails: TextView = itemView.findViewById(R.id.tvDetails)
        private val ivStar: ImageView = itemView.findViewById(R.id.ivStar)
        private val ivRun: ImageView = itemView.findViewById(R.id.ivRun)
        private val ivDelete: ImageView = itemView.findViewById(R.id.ivDelete)
        
        fun bind(workflow: ImageWorkflowConfig) {
            tvWorkflowName.text = workflow.workflowName
            
            // Set workflow type
            tvWorkflowType.text = when (workflow.workflowType) {
                WorkflowType.IMAGE_FORWARD -> "Image Forward"
                WorkflowType.DOCUMENT_ANALYSIS -> "Document Analysis"
            }
            
            // Set destination
            tvDestination.text = when (workflow.destinationType) {
                DestinationType.GMAIL -> "Gmail: ${workflow.gmailRecipient}"
                DestinationType.TELEGRAM -> "Telegram: ${workflow.telegramChatId}"
            }
            
            // Set details based on workflow type
            tvDetails.text = when (workflow.workflowType) {
                WorkflowType.IMAGE_FORWARD -> {
                    val referenceFile = workflow.referenceImagePath.substringAfterLast("/")
                    "Reference: $referenceFile"
                }
                WorkflowType.DOCUMENT_ANALYSIS -> {
                    "Type: ${workflow.documentType} | Time: ${workflow.scheduledTime}"
                }
            }
            
            // Set star icon
            ivStar.setImageResource(
                if (workflow.isStarred) {
                    android.R.drawable.btn_star_big_on
                } else {
                    android.R.drawable.btn_star_big_off
                }
            )
            
            // Set click listeners
            ivStar.setOnClickListener {
                onStarClick(workflow)
            }
            
            ivRun.setOnClickListener {
                onRunClick(workflow)
            }
            
            ivDelete.setOnClickListener {
                onDeleteClick(workflow)
            }
            
            itemView.setOnClickListener {
                onWorkflowClick(workflow)
            }
        }
    }
}
