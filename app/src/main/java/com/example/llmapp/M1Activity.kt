package com.example.llmapp

import android.os.Bundle
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.example.llmapp.llmChatView
import com.example.llmapp.llmchatmodel
import com.example.llmapp.Model

class M1Activity : ComponentActivity() {
    private lateinit var chatView: llmChatView
    private var outputAccumulator: StringBuilder = StringBuilder()
    private lateinit var model: Model

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_m1)

        model = MODEL_TEXT_CLASSIFICATION_MOBILEBERT.copy(llmSupportImage = false)
        chatView = llmChatView(model)
        chatView.initialize(this) {}

        val inputText = findViewById<EditText>(R.id.m1InputEditText)
        val sendButton = findViewById<Button>(R.id.m1SendButton)
        val outputText = findViewById<TextView>(R.id.m1OutputText)

        sendButton.setOnClickListener {
            val input = inputText.text.toString()
            outputAccumulator = StringBuilder()
            chatView.generateResponse(
                context = this,
                input = input,
                images = listOf(),
                onResult = { result ->
                    runOnUiThread {
                        outputAccumulator.append(result)
                        outputText.text = outputAccumulator.toString()
                    }
                },
                onError = { errorMsg ->
                    runOnUiThread { outputText.text = "Error occurred: $errorMsg" }
                }
            )
            inputText.setText("")
            // Hide keyboard after sending
            val imm = getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.hideSoftInputFromWindow(inputText.windowToken, 0)
        }
    }
}
