package com.example.llmapp.pipeline

import android.util.Log
import com.example.llmapp.interfaces.MessageSourceService

/**
 * Command Pattern for pipeline operations
 * Allows encapsulating operations as objects, enabling undo, queuing, and logging
 */
interface PipelineCommand {
    fun execute(): Boolean
    fun undo()
    fun getDescription(): String
}

/**
 * Command to process emails with default settings
 */
class ProcessEmailsCommand(
    private val pipeline: EmailProcessingPipeline,
    private val count: Int = 3
) : PipelineCommand {
    private val tag = "ProcessEmailsCommand"
    private var wasExecuted = false
    
    override fun execute(): Boolean {
        return try {
            Log.d(tag, "Executing: Process $count emails")
            pipeline.processEmails(count)
            wasExecuted = true
            true
        } catch (e: Exception) {
            Log.e(tag, "Failed to execute ProcessEmailsCommand", e)
            false
        }
    }
    
    override fun undo() {
        if (wasExecuted) {
            Log.d(tag, "Undoing: ProcessEmailsCommand")
            // In a real implementation, this might cancel processing or revert changes
            wasExecuted = false
        }
    }
    
    override fun getDescription(): String = "Process $count emails"
}

/**
 * Command to process emails with search query
 */
class ProcessWithQueryCommand(
    private val pipeline: EmailProcessingPipeline,
    private val query: String,
    private val count: Int = 3
) : PipelineCommand {
    private val tag = "ProcessWithQueryCommand"
    private var wasExecuted = false
    
    override fun execute(): Boolean {
        return try {
            Log.d(tag, "Executing: Process emails with query '$query'")
            pipeline.processEmailsWithSearch(query, count)
            wasExecuted = true
            true
        } catch (e: Exception) {
            Log.e(tag, "Failed to execute ProcessWithQueryCommand", e)
            false
        }
    }
    
    override fun undo() {
        if (wasExecuted) {
            Log.d(tag, "Undoing: ProcessWithQueryCommand")
            wasExecuted = false
        }
    }
    
    override fun getDescription(): String = "Process $count emails with query: $query"
}

/**
 * Command to process a sample email
 */
class ProcessSampleEmailCommand(
    private val pipeline: EmailProcessingPipeline
) : PipelineCommand {
    private val tag = "ProcessSampleEmailCommand"
    private var wasExecuted = false
    
    override fun execute(): Boolean {
        return try {
            Log.d(tag, "Executing: Process sample email")
            pipeline.processSampleEmail()
            wasExecuted = true
            true
        } catch (e: Exception) {
            Log.e(tag, "Failed to execute ProcessSampleEmailCommand", e)
            false
        }
    }
    
    override fun undo() {
        if (wasExecuted) {
            Log.d(tag, "Undoing: ProcessSampleEmailCommand")
            wasExecuted = false
        }
    }
    
    override fun getDescription(): String = "Process sample email"
}

/**
 * Command to process a manual email
 */
class ProcessManualEmailCommand(
    private val pipeline: EmailProcessingPipeline,
    private val subject: String,
    private val content: String,
    private val from: String = "",
    private val date: String = ""
) : PipelineCommand {
    private val tag = "ProcessManualEmailCommand"
    private var wasExecuted = false
    
    override fun execute(): Boolean {
        return try {
            Log.d(tag, "Executing: Process manual email '$subject'")
            pipeline.processManualEmail(subject, content, from, date)
            wasExecuted = true
            true
        } catch (e: Exception) {
            Log.e(tag, "Failed to execute ProcessManualEmailCommand", e)
            false
        }
    }
    
    override fun undo() {
        if (wasExecuted) {
            Log.d(tag, "Undoing: ProcessManualEmailCommand")
            wasExecuted = false
        }
    }
    
    override fun getDescription(): String = "Process manual email: $subject"
}

/**
 * Factory for creating pipeline commands
 */
object PipelineCommandFactory {
    
    fun createProcessCommand(
        pipeline: EmailProcessingPipeline,
        query: String? = null,
        defaultCount: Int = 3
    ): PipelineCommand {
        return if (!query.isNullOrBlank()) {
            ProcessWithQueryCommand(pipeline, query, defaultCount)
        } else {
            ProcessEmailsCommand(pipeline, defaultCount)
        }
    }
    
    fun createSampleEmailCommand(pipeline: EmailProcessingPipeline): PipelineCommand {
        return ProcessSampleEmailCommand(pipeline)
    }
    
    fun createManualEmailCommand(
        pipeline: EmailProcessingPipeline,
        subject: String,
        content: String,
        from: String = "",
        date: String = ""
    ): PipelineCommand {
        return ProcessManualEmailCommand(pipeline, subject, content, from, date)
    }
}

/**
 * Command invoker with history and undo support
 */
class PipelineCommandInvoker {
    private val commandHistory = mutableListOf<PipelineCommand>()
    private val tag = "PipelineCommandInvoker"
    
    fun executeCommand(command: PipelineCommand): Boolean {
        Log.d(tag, "Executing command: ${command.getDescription()}")
        
        val success = command.execute()
        if (success) {
            commandHistory.add(command)
            Log.d(tag, "Command executed successfully: ${command.getDescription()}")
        } else {
            Log.e(tag, "Command failed: ${command.getDescription()}")
        }
        
        return success
    }
    
    fun undoLastCommand(): Boolean {
        if (commandHistory.isNotEmpty()) {
            val lastCommand = commandHistory.removeLastOrNull()
            if (lastCommand != null) {
                Log.d(tag, "Undoing command: ${lastCommand.getDescription()}")
                lastCommand.undo()
                return true
            }
        }
        Log.w(tag, "No commands to undo")
        return false
    }
    
    fun getCommandHistory(): List<String> {
        return commandHistory.map { it.getDescription() }
    }
    
    fun clearHistory() {
        commandHistory.clear()
        Log.d(tag, "Command history cleared")
    }
}
