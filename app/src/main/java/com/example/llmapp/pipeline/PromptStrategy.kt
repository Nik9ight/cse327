package com.example.llmapp.pipeline

import com.example.llmapp.interfaces.MessageSourceService

/**
 * Strategy Pattern for prompt building
 * Allows different prompt generation strategies without modifying existing code
 */
interface PromptStrategy {
    fun buildPrompt(message: MessageSourceService.SourceMessage): String
    fun getStrategyName(): String
    fun getDescription(): String
}

/**
 * Default prompt strategy that creates a summarization prompt
 */
class DefaultPromptStrategy : PromptStrategy {
    override fun buildPrompt(message: MessageSourceService.SourceMessage): String {
        val subject = sanitizeText(message.subject, 100)
        val content = sanitizeText(message.content, 2000)
        val from = sanitizeText(message.metadata["from"] ?: "", 100)
        val date = sanitizeText(message.metadata["date"] ?: "", 100)
        
        return """
            Summarize the following email or messages and make it as brief as possible while maintaining key information.
            
            Subject: $subject
            
            Content:
            $content
            
            From: $from
            Date: $date
        """.trimIndent()
    }

    override fun getStrategyName(): String {
        return "Default Summarization"
    }

    override fun getDescription(): String {
        return "Creates a brief summary of the email content while maintaining key information"
    }

    private fun sanitizeText(text: String, maxLength: Int = 8000): String {
        if (text.isEmpty()) return ""
        
        val truncatedText = if (text.length > maxLength) {
            text.substring(0, maxLength) + "..."
        } else {
            text
        }
        
        return truncatedText.replace(Regex("[\\p{Cntrl}&&[^\n\t\r]]"), "")
            .replace(Regex("[\u0000-\u001F]"), "")
            .trim()
    }
}

/**
 * Custom prompt strategy that uses a user-defined template
 */
class CustomPromptStrategy(private val template: String) : PromptStrategy {
    override fun buildPrompt(message: MessageSourceService.SourceMessage): String {
        val subject = sanitizeText(message.subject, 100)
        val content = sanitizeText(message.content, 2000)
        val from = sanitizeText(message.metadata["from"] ?: "", 100)
        val date = sanitizeText(message.metadata["date"] ?: "", 100)
        
        return """
            ${template.trim()}
            
            Subject: $subject
            
            Content:
            $content
            
            From: $from
            Date: $date
        """.trimIndent()
    }

    override fun getStrategyName(): String {
        return "Custom Template"
    }

    override fun getDescription(): String {
        return "Uses a user-defined template for processing emails: ${template.take(50)}${if (template.length > 50) "..." else ""}"
    }

    private fun sanitizeText(text: String, maxLength: Int = 8000): String {
        if (text.isEmpty()) return ""
        
        val truncatedText = if (text.length > maxLength) {
            text.substring(0, maxLength) + "..."
        } else {
            text
        }
        
        return truncatedText.replace(Regex("[\\p{Cntrl}&&[^\n\t\r]]"), "")
            .replace(Regex("[\u0000-\u001F]"), "")
            .trim()
    }
}

/**
 * Analysis prompt strategy for detailed email analysis
 */
class AnalysisPromptStrategy : PromptStrategy {
    override fun buildPrompt(message: MessageSourceService.SourceMessage): String {
        val subject = sanitizeText(message.subject, 100)
        val content = sanitizeText(message.content, 2000)
        val from = sanitizeText(message.metadata["from"] ?: "", 100)
        val date = sanitizeText(message.metadata["date"] ?: "", 100)
        
        return """
            Analyze the following email and provide:
            1. Main purpose/intent
            2. Key action items
            3. Important dates or deadlines
            4. Priority level (High/Medium/Low)
            5. Brief summary
            
            Subject: $subject
            
            Content:
            $content
            
            From: $from
            Date: $date
        """.trimIndent()
    }

    override fun getStrategyName(): String {
        return "Email Analysis"
    }

    override fun getDescription(): String {
        return "Provides detailed analysis including purpose, action items, deadlines, and priority level"
    }

    private fun sanitizeText(text: String, maxLength: Int = 8000): String {
        if (text.isEmpty()) return ""
        
        val truncatedText = if (text.length > maxLength) {
            text.substring(0, maxLength) + "..."
        } else {
            text
        }
        
        return truncatedText.replace(Regex("[\\p{Cntrl}&&[^\n\t\r]]"), "")
            .replace(Regex("[\u0000-\u001F]"), "")
            .trim()
    }
}
