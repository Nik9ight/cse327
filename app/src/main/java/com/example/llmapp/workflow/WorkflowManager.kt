package com.example.llmapp.workflow

import android.content.Context
import android.util.Log
import com.example.llmapp.pipeline.TelegramToGmailPipeline
import com.example.llmapp.GmailToTelegramPipeline
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.*

/**
 * Central workflow management system using multiple design patterns
 * 
 * Design Patterns:
 * - Repository Pattern: For workflow persistence
 * - Factory Pattern: For creating different pipeline types
 * - Strategy Pattern: For different execution strategies
 * - Observer Pattern: For workflow state notifications
 */
class WorkflowManager(
    private val context: Context,
    private val repository: WorkflowRepository = WorkflowRepositoryImpl(context),
    private val factory: WorkflowFactory = WorkflowFactoryImpl(context)
) {
    companion object {
        private const val TAG = "WorkflowManager"
    }
    
    private val observers = mutableListOf<WorkflowObserver>()
    private var runningWorkflow: String? = null
    
    /**
     * Create a new workflow
     */
    suspend fun createWorkflow(
        name: String,
        type: WorkflowType,
        configuration: WorkflowConfiguration
    ): Result<SavedWorkflow> = withContext(Dispatchers.IO) {
        return@withContext try {
            val workflow = SavedWorkflow(
                id = UUID.randomUUID().toString(),
                name = name,
                type = type,
                configuration = configuration,
                createdAt = System.currentTimeMillis(),
                lastModified = System.currentTimeMillis()
            )
            
            repository.saveWorkflow(workflow)
            notifyObservers { onWorkflowCreated(workflow) }
            
            Log.d(TAG, "Created workflow: $name (${type.name})")
            Result.success(workflow)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create workflow: $name", e)
            Result.failure(e)
        }
    }
    
    /**
     * Update an existing workflow
     */
    suspend fun updateWorkflow(
        id: String,
        name: String,
        configuration: WorkflowConfiguration
    ): Result<SavedWorkflow> = withContext(Dispatchers.IO) {
        return@withContext try {
            val existingWorkflow = repository.getWorkflow(id)
                ?: return@withContext Result.failure(Exception("Workflow not found: $id"))
            
            val updatedWorkflow = existingWorkflow.copy(
                name = name,
                configuration = configuration,
                lastModified = System.currentTimeMillis()
            )
            
            repository.saveWorkflow(updatedWorkflow)
            notifyObservers { onWorkflowUpdated(updatedWorkflow) }
            
            Log.d(TAG, "Updated workflow: $name")
            Result.success(updatedWorkflow)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update workflow: $id", e)
            Result.failure(e)
        }
    }
    
    /**
     * Get all saved workflows
     */
    suspend fun getAllWorkflows(): List<SavedWorkflow> = withContext(Dispatchers.IO) {
        return@withContext try {
            repository.getAllWorkflows()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get all workflows", e)
            emptyList()
        }
    }
    
    /**
     * Get workflow by ID
     */
    suspend fun getWorkflow(id: String): SavedWorkflow? = withContext(Dispatchers.IO) {
        return@withContext try {
            repository.getWorkflow(id)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get workflow: $id", e)
            null
        }
    }
    
    /**
     * Delete a workflow
     */
    suspend fun deleteWorkflow(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        return@withContext try {
            val workflow = repository.getWorkflow(id)
            if (workflow != null) {
                repository.deleteWorkflow(id)
                notifyObservers { onWorkflowDeleted(workflow) }
                Log.d(TAG, "Deleted workflow: ${workflow.name}")
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete workflow: $id", e)
            Result.failure(e)
        }
    }
    
    /**
     * Execute a workflow using Strategy Pattern
     */
    suspend fun executeWorkflow(
        workflowId: String,
        executionStrategy: WorkflowExecutionStrategy = DefaultExecutionStrategy()
    ): Result<WorkflowExecutionResult> = withContext(Dispatchers.IO) {
        return@withContext try {
            if (runningWorkflow != null) {
                return@withContext Result.failure(Exception("Another workflow is already running: $runningWorkflow"))
            }
            
            val workflow = repository.getWorkflow(workflowId)
                ?: return@withContext Result.failure(Exception("Workflow not found: $workflowId"))
            
            runningWorkflow = workflowId
            notifyObservers { onWorkflowStarted(workflow) }
            
            Log.d(TAG, "Executing workflow: ${workflow.name} (${workflow.type.name})")
            Log.d(TAG, "Workflow configuration: ${workflow.configuration}")
            
            // Create and execute the pipeline using Factory Pattern
            Log.d(TAG, "Creating pipeline with factory...")
            val pipeline = factory.createPipeline(workflow.type, workflow.configuration)
            Log.d(TAG, "Pipeline created successfully, executing with strategy...")
            
            val result = executionStrategy.execute(pipeline, workflow)
            Log.d(TAG, "Strategy execution completed, result: ${result.success}, message: ${result.message}")
            
            runningWorkflow = null
            notifyObservers { onWorkflowCompleted(workflow, result) }
            
            Log.d(TAG, "Workflow execution completed: ${workflow.name}")
            Result.success(result)
            
        } catch (e: Exception) {
            runningWorkflow = null
            Log.e(TAG, "Failed to execute workflow: $workflowId", e)
            val errorResult = WorkflowExecutionResult(
                success = false,
                message = "Execution failed: ${e.message}",
                executionTime = 0,
                details = emptyMap()
            )
            Result.failure(e)
        }
    }
    
    /**
     * Check if a workflow is currently running
     */
    fun isWorkflowRunning(): Boolean = runningWorkflow != null
    
    /**
     * Get the currently running workflow ID
     */
    fun getRunningWorkflowId(): String? = runningWorkflow
    
    /**
     * Stop the currently running workflow
     */
    suspend fun stopCurrentWorkflow(): Result<Unit> = withContext(Dispatchers.IO) {
        return@withContext try {
            if (runningWorkflow != null) {
                val workflowId = runningWorkflow!!
                runningWorkflow = null
                
                val workflow = repository.getWorkflow(workflowId)
                if (workflow != null) {
                    notifyObservers { onWorkflowStopped(workflow) }
                }
                
                Log.d(TAG, "Stopped workflow: $workflowId")
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop workflow", e)
            Result.failure(e)
        }
    }
    
    // Observer Pattern implementation
    fun addObserver(observer: WorkflowObserver) {
        if (!observers.contains(observer)) {
            observers.add(observer)
        }
    }
    
    fun removeObserver(observer: WorkflowObserver) {
        observers.remove(observer)
    }
    
    private fun notifyObservers(action: WorkflowObserver.() -> Unit) {
        observers.forEach { observer ->
            try {
                observer.action()
            } catch (e: Exception) {
                Log.e(TAG, "Observer notification failed", e)
            }
        }
    }
}

/**
 * Observer interface for workflow events
 */
interface WorkflowObserver {
    fun onWorkflowCreated(workflow: SavedWorkflow)
    fun onWorkflowUpdated(workflow: SavedWorkflow)
    fun onWorkflowDeleted(workflow: SavedWorkflow)
    fun onWorkflowStarted(workflow: SavedWorkflow)
    fun onWorkflowCompleted(workflow: SavedWorkflow, result: WorkflowExecutionResult)
    fun onWorkflowStopped(workflow: SavedWorkflow)
}

/**
 * Default implementation of WorkflowObserver
 */
abstract class BaseWorkflowObserver : WorkflowObserver {
    override fun onWorkflowCreated(workflow: SavedWorkflow) {}
    override fun onWorkflowUpdated(workflow: SavedWorkflow) {}
    override fun onWorkflowDeleted(workflow: SavedWorkflow) {}
    override fun onWorkflowStarted(workflow: SavedWorkflow) {}
    override fun onWorkflowCompleted(workflow: SavedWorkflow, result: WorkflowExecutionResult) {}
    override fun onWorkflowStopped(workflow: SavedWorkflow) {}
}
