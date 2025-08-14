package com.example.llmapp.workflows.navigation

import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.llmapp.workflows.ui.EnhancedWorkflowCreateActivity
import com.example.llmapp.workflows.ui.EnhancedWorkflowListActivity
import com.example.llmapp.workflows.activities.WorkflowCreationActivity
import com.example.llmapp.workflows.activities.WorkflowListActivity

/**
 * Central navigation utility for workflow activities
 * Handles routing between Enhanced and Simple workflow interfaces
 */
object WorkflowNavigator {
    
    private const val TAG = "WorkflowNavigator"
    private const val PREF_KEY_INTERFACE_TYPE = "workflow_interface_type"
    
    enum class InterfaceType {
        ENHANCED,    // Full-featured interface (default)
        SIMPLE       // Simplified interface for quick workflows
    }
    
    /**
     * Get user's preferred interface type
     */
    fun getPreferredInterface(context: Context): InterfaceType {
        val prefs = context.getSharedPreferences("workflow_settings", Context.MODE_PRIVATE)
        val savedType = prefs.getString(PREF_KEY_INTERFACE_TYPE, InterfaceType.ENHANCED.name)
        return try {
            InterfaceType.valueOf(savedType ?: InterfaceType.ENHANCED.name)
        } catch (e: IllegalArgumentException) {
            InterfaceType.ENHANCED
        }
    }
    
    /**
     * Set user's preferred interface type
     */
    fun setPreferredInterface(context: Context, interfaceType: InterfaceType) {
        val prefs = context.getSharedPreferences("workflow_settings", Context.MODE_PRIVATE)
        prefs.edit()
            .putString(PREF_KEY_INTERFACE_TYPE, interfaceType.name)
            .apply()
        Log.d(TAG, "Interface type set to: $interfaceType")
    }
    
    /**
     * Navigate to workflow list using user's preferred interface
     */
    fun openWorkflowList(context: Context, forceInterface: InterfaceType? = null) {
        val interfaceType = forceInterface ?: getPreferredInterface(context)
        
        val intent = when (interfaceType) {
            InterfaceType.ENHANCED -> {
                Log.d(TAG, "Opening Enhanced Workflow List")
                Intent(context, EnhancedWorkflowListActivity::class.java)
            }
            InterfaceType.SIMPLE -> {
                Log.d(TAG, "Opening Simple Workflow List")
                Intent(context, WorkflowListActivity::class.java)
            }
        }
        
        context.startActivity(intent)
    }
    
    /**
     * Navigate to workflow creation using user's preferred interface
     */
    fun createWorkflow(context: Context, forceInterface: InterfaceType? = null) {
        val interfaceType = forceInterface ?: getPreferredInterface(context)
        
        val intent = when (interfaceType) {
            InterfaceType.ENHANCED -> {
                Log.d(TAG, "Opening Enhanced Workflow Creation")
                Intent(context, EnhancedWorkflowCreateActivity::class.java)
            }
            InterfaceType.SIMPLE -> {
                Log.d(TAG, "Opening Simple Workflow Creation")
                Intent(context, WorkflowCreationActivity::class.java)
            }
        }
        
        context.startActivity(intent)
    }
    
    /**
     * Navigate to workflow editing (Enhanced only for now)
     */
    fun editWorkflow(context: Context, workflowId: String) {
        Log.d(TAG, "Opening workflow edit: $workflowId")
        val intent = Intent(context, EnhancedWorkflowCreateActivity::class.java).apply {
            putExtra(EnhancedWorkflowCreateActivity.EXTRA_WORKFLOW_ID, workflowId)
            putExtra(EnhancedWorkflowCreateActivity.EXTRA_IS_EDIT, true)
        }
        context.startActivity(intent)
    }
    
    /**
     * Show interface selection dialog
     */
    fun showInterfaceSelector(context: Context, onSelected: (InterfaceType) -> Unit) {
        androidx.appcompat.app.AlertDialog.Builder(context)
            .setTitle("Choose Workflow Interface")
            .setMessage("Select your preferred workflow management interface:")
            .setPositiveButton("Enhanced (Recommended)") { _, _ ->
                setPreferredInterface(context, InterfaceType.ENHANCED)
                onSelected(InterfaceType.ENHANCED)
            }
            .setNegativeButton("Simple") { _, _ ->
                setPreferredInterface(context, InterfaceType.SIMPLE)
                onSelected(InterfaceType.SIMPLE)
            }
            .setNeutralButton("Cancel", null)
            .show()
    }
    
    /**
     * Get interface description for user display
     */
    fun getInterfaceDescription(interfaceType: InterfaceType): String {
        return when (interfaceType) {
            InterfaceType.ENHANCED -> "Full-featured interface with advanced options, testing, and service management"
            InterfaceType.SIMPLE -> "Simplified interface for quick workflow creation and basic management"
        }
    }
}
