package com.example.llmapp.new_implementation.interfaces
import com.example.llmapp.new_implementation.Message

interface MessageDestination {
    fun sendMessage(message: Message): Boolean
    fun sendMessages(messages: List<Message>): List<Boolean>
}
