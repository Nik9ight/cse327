package com.example.llmapp.new_implementation.ui

import android.content.Context
import android.util.Log
import com.example.llmapp.new_implementation.integration.NewImplementationBridge
import com.example.llmapp.new_implementation.integration.WorkflowResult
import com.example.llmapp.new_implementation.Workflow
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages continuous workflow execution with intervals
 * Now integrates with the original WorkflowManager
 */
class WorkflowRunner(
    private val context: Context,
    private val bridge: NewImplementationBridge
) {
    private val TAG = "WorkflowRunner"
    private val runningWorkflows = ConcurrentHashMap<String, Job>()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    suspend fun startWorkflow(savedWorkflow: SavedWorkflow) {
        stopWorkflow(savedWorkflow.id) // Stop if already running

        Log.d(TAG, "Starting workflow: ${savedWorkflow.name}")

        val job = scope.launch {
            try {
                while (isActive) {
                    Log.d(TAG, "Executing workflow: ${savedWorkflow.name}")

                    // Use the bridge to execute via WorkflowManager
                    val result = bridge.executeWorkflowViaManager(savedWorkflow)

                    Log.d(TAG, "Workflow ${savedWorkflow.name} result: ${result.success}")

                    // Wait for the specified interval
                    delay(savedWorkflow.intervalSeconds * 1000L)
                }
            } catch (e: CancellationException) {
                Log.d(TAG, "Workflow ${savedWorkflow.name} was cancelled")
            } catch (e: Exception) {
                Log.e(TAG, "Error in workflow ${savedWorkflow.name}", e)
            }
        }

        runningWorkflows[savedWorkflow.id] = job
    }

    fun stopWorkflow(workflowId: String) {
        runningWorkflows[workflowId]?.let { job ->
            Log.d(TAG, "Stopping workflow: $workflowId")
            job.cancel()
            runningWorkflows.remove(workflowId)
        }
    }

    fun stopAllWorkflows() {
        Log.d(TAG, "Stopping all workflows")
        runningWorkflows.values.forEach { it.cancel() }
        runningWorkflows.clear()
    }

    fun isWorkflowRunning(workflowId: String): Boolean {
        return runningWorkflows[workflowId]?.isActive == true
    }

    suspend fun runWorkflowOnce(savedWorkflow: SavedWorkflow): WorkflowResult {
        return try {
            Log.d(TAG, "Running workflow once: ${savedWorkflow.name}")

            // Use the bridge to execute via WorkflowManager
            bridge.executeWorkflowViaManager(savedWorkflow)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to run workflow once", e)
            WorkflowResult(
                success = false,
                message = "Failed to execute: ${e.message}",
                processedMessages = 0,
                timestamp = System.currentTimeMillis(),
                error = e.message
            )
        }
    }

    private fun createWorkflowFromSaved(savedWorkflow: SavedWorkflow): Workflow {
        return when (savedWorkflow.configuration) {
            is SavedWorkflowConfig.GmailToTelegram -> {
                bridge.createGmailToTelegramWorkflow(
                    telegramBotToken = savedWorkflow.configuration.telegramBotToken,
                    telegramChatId = savedWorkflow.configuration.telegramChatId,
                    gmailSearchQuery = savedWorkflow.configuration.gmailSearchQuery
                )
            }

            is SavedWorkflowConfig.TelegramToGmail -> {
                bridge.createTelegramToGmailWorkflow(
                    telegramBotToken = savedWorkflow.configuration.telegramBotToken,
                    gmailRecipients = savedWorkflow.configuration.gmailRecipients,
                    gmailSender = savedWorkflow.configuration.gmailSender
                )
            }
        }
    }

    fun cleanup() {
        stopAllWorkflows()
        scope.cancel()
    }


    fun getRunningWorkflowCount(): Int {
        return runningWorkflows.size
    }


}