package com.example.llmapp.strategies

import com.example.llmapp.interfaces.MessageSourceService
import com.example.llmapp.pipeline.PromptStrategy


/**
 * Default strategy for Telegram message analysis
 */
class TelegramPromptStrategy : PromptStrategy {
    
    override fun buildPrompt(sourceMessage: MessageSourceService.SourceMessage): String {
        return """
            Please analyze this Telegram message and provide a comprehensive summary suitable for an email.
            
            Sender: ${sourceMessage.metadata["sender_name"] ?: "Unknown"}
            Chat Type: ${sourceMessage.metadata["chat_type"] ?: "Unknown"}
            
            Message Content:
            ${sourceMessage.content}
            
            Please include:
            1. Main topics or themes discussed
            2. Important information or decisions
            3. Action items or next steps (if any)
            4. Key participants and their roles
            5. Overall context and relevance
            
            Provide a professional summary that would be appropriate for business email communication.
        """.trimIndent()
    }
    
    override fun getStrategyName(): String = "Telegram Default Analysis"
    
    override fun getDescription(): String = "General purpose analysis for Telegram messages"
}

/**
 * Strategy focused on meeting-related content analysis
 */
class MeetingAnalysisPromptStrategy : PromptStrategy {
    
    override fun buildPrompt(sourceMessage: MessageSourceService.SourceMessage): String {
        return """
            Analyze this Telegram conversation with focus on meeting-related information:
            
            Sender: ${sourceMessage.metadata["sender_name"] ?: "Unknown"}
            Time: ${sourceMessage.metadata["timestamp"] ?: "Unknown"}
            
            Message Content:
            ${sourceMessage.content}
            
            Extract and summarize:
            1. Meeting requests or scheduling discussions
            2. Proposed dates, times, and locations
            3. Agenda items or topics to be discussed
            4. Attendees mentioned
            5. Action items or preparations needed
            6. Follow-up requirements
            
            Provide a structured summary suitable for meeting coordination.
        """.trimIndent()
    }
    
    override fun getStrategyName(): String = "Meeting Analysis"
    
    override fun getDescription(): String = "Specialized analysis for meeting coordination and scheduling"
}

/**
 * Strategy focused on task and project management
 */
class TaskAnalysisPromptStrategy : PromptStrategy {
    
    override fun buildPrompt(sourceMessage: MessageSourceService.SourceMessage): String {
        return """
            Analyze this Telegram message focusing on task and project management:
            
            From: ${sourceMessage.metadata["sender_name"] ?: "Unknown"}
            Time: ${sourceMessage.metadata["timestamp"] ?: "Unknown"}
            
            Content:
            ${sourceMessage.content}
            
            Identify and extract:
            1. Tasks assigned or discussed
            2. Deadlines and timelines mentioned
            3. Project updates or status reports
            4. Blockers or issues raised
            5. Resource requirements
            6. Dependencies on other tasks or people
            7. Priority levels (if mentioned)
            
            Provide a task-oriented summary suitable for project management tracking.
        """.trimIndent()
    }
    
    override fun getStrategyName(): String = "Task Management Analysis"
    
    override fun getDescription(): String = "Focused analysis for task tracking and project management"
}
