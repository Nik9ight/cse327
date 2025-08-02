package com.example.llmapp.new_implementation.interfaces

import com.example.llmapp.new_implementation.Message

interface OutputFormatter {
    fun formatOutput(message: Message): String
    fun formatBatchOutput(messages: List<Message>): String
}