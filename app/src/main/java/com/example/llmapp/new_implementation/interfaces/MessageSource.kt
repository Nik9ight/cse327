package com.example.llmapp.new_implementation.interfaces

import com.example.llmapp.new_implementation.Message

interface MessageSource {
    fun fetchMessage(): Message
    fun fetchMessages(count: Int = 3): List<Message>
}