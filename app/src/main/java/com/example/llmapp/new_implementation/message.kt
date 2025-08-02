package com.example.llmapp.new_implementation

data class Message(
    val id: String,
    val sender: String,
    val recipient: String,
    val content: String,
    val timestamp: Long,
    val metadata: Map<String, String> = emptyMap()
)
