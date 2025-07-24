package com.example.llmapp.pipeline

/**
 * Simple Email data class for template method pattern
 */
data class Email(
    val id: String,
    val subject: String,
    val content: String,
    val from: String = "",
    val date: String = "",
    val metadata: Map<String, String> = emptyMap()
) {
    companion object {
        /**
         * Convert from MessageSourceService.SourceMessage to Email
         */
        fun fromSourceMessage(message: com.example.llmapp.interfaces.MessageSourceService.SourceMessage): Email {
            return Email(
                id = message.id,
                subject = message.subject,
                content = message.content,
                from = message.metadata["from"] ?: "",
                date = message.metadata["date"] ?: "",
                metadata = message.metadata
            )
        }
        
        /**
         * Convert from GmailService.EmailMessage to Email
         */
        fun fromEmailMessage(message: com.example.llmapp.GmailService.EmailMessage): Email {
            return Email(
                id = message.id,
                subject = message.subject,
                content = message.content,
                from = message.from,
                date = message.date,
                metadata = mapOf(
                    "from" to message.from,
                    "date" to message.date,
                    "timestamp" to message.timestamp.toString()
                )
            )
        }
    }
}
