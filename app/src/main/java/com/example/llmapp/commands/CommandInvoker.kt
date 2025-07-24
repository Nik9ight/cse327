package com.example.llmapp.commands

import android.util.Log
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Base Command Invoker for executing and managing commands
 * Implements the Command pattern to execute, undo, and track commands
 */
open class CommandInvoker {
    
    private val executedCommands = CopyOnWriteArrayList<Command>()
    private val tag = "CommandInvoker"
    
    /**
     * Execute a command and add it to the history
     */
    fun execute(command: Command): Boolean {
        return try {
            val success = command.execute()
            if (success) {
                executedCommands.add(command)
                Log.d(tag, "Command executed successfully: ${command.javaClass.simpleName}")
                
                // Keep only last 100 commands to prevent memory issues
                if (executedCommands.size > 100) {
                    executedCommands.removeAt(0)
                }
            } else {
                Log.w(tag, "Command execution failed: ${command.javaClass.simpleName}")
            }
            success
        } catch (e: Exception) {
            Log.e(tag, "Exception during command execution: ${command.javaClass.simpleName}", e)
            false
        }
    }
    
    /**
     * Undo the last executed command
     */
    fun undoLast(): Boolean {
        return try {
            if (executedCommands.isEmpty()) {
                Log.w(tag, "No commands to undo")
                return false
            }
            
            val lastCommand = executedCommands.removeAt(executedCommands.size - 1)
            val success = lastCommand.undo()
            
            if (success) {
                Log.d(tag, "Command undone successfully: ${lastCommand.javaClass.simpleName}")
            } else {
                Log.w(tag, "Command undo failed: ${lastCommand.javaClass.simpleName}")
                // Re-add to list if undo failed
                executedCommands.add(lastCommand)
            }
            
            success
        } catch (e: Exception) {
            Log.e(tag, "Exception during command undo", e)
            false
        }
    }
    
    /**
     * Undo all executed commands in reverse order
     */
    fun undoAll(): Boolean {
        var allSuccessful = true
        val commandsToUndo = executedCommands.toList().reversed()
        
        for (command in commandsToUndo) {
            try {
                if (!command.undo()) {
                    allSuccessful = false
                    Log.w(tag, "Failed to undo command: ${command.javaClass.simpleName}")
                }
            } catch (e: Exception) {
                Log.e(tag, "Exception undoing command: ${command.javaClass.simpleName}", e)
                allSuccessful = false
            }
        }
        
        if (allSuccessful) {
            executedCommands.clear()
            Log.d(tag, "All commands undone successfully")
        }
        
        return allSuccessful
    }
    
    /**
     * Clear command history without undoing
     */
    fun clearHistory() {
        executedCommands.clear()
        Log.d(tag, "Command history cleared")
    }
    
    /**
     * Get the number of executed commands in history
     */
    fun getCommandCount(): Int = executedCommands.size
    
    /**
     * Check if there are commands that can be undone
     */
    fun canUndo(): Boolean = executedCommands.isNotEmpty()
    
    /**
     * Get command execution statistics
     */
    fun getStats(): CommandStats {
        return CommandStats(
            totalCommands = executedCommands.size,
            commandTypes = executedCommands.groupBy { it.javaClass.simpleName }
                .mapValues { it.value.size }
        )
    }
}

/**
 * Statistics for command execution
 */
data class CommandStats(
    val totalCommands: Int,
    val commandTypes: Map<String, Int>
)
