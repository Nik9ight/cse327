package com.example.llmapp

import android.os.Bundle
import android.view.View
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
    private lateinit var stopButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_m1)

        model = MODEL_TEXT_CLASSIFICATION_MOBILEBERT.copy(llmSupportImage = false)
        chatView = llmChatView(model)
        chatView.initialize(this) {}

        val inputText = findViewById<EditText>(R.id.m1InputEditText)
        val sendButton = findViewById<Button>(R.id.m1SendButton)
        stopButton = findViewById<Button>(R.id.m1StopButton)
        val outputText = findViewById<TextView>(R.id.m1OutputText)

        sendButton.setOnClickListener {
            val input = inputText.text.toString()
            
            if (input.isEmpty()) {
                Toast.makeText(this, "Please enter some text", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            // Show the stop button
            stopButton.visibility = View.VISIBLE
            
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
                    runOnUiThread { 
                        outputText.text = "Error occurred: $errorMsg"
                        // Hide stop button on error
                        stopButton.visibility = View.GONE
                    }
                }
            )
            
            // Create a background thread to check if processing is still ongoing
            Thread {
                while (chatView.inProgress) {
                    Thread.sleep(500)
                }
                runOnUiThread {
                    // Hide stop button when processing is done
                    stopButton.visibility = View.GONE
                }
            }.start()
            inputText.setText("")
            // Hide keyboard after sending
            val imm = getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.hideSoftInputFromWindow(inputText.windowToken, 0)
        }
        
        // Set up the stop button click listener
        stopButton.setOnClickListener {
            if (chatView.inProgress) {
                chatView.stopResponse(model)
                stopButton.visibility = View.GONE
                Toast.makeText(this, "Response generation stopped", Toast.LENGTH_SHORT).show()
                
                // Add a visual indicator that response was stopped
                outputAccumulator.append("\n\n[Response generation stopped by user]")
                outputText.text = outputAccumulator.toString()
            } else {
                // If not in progress, just hide the button
                stopButton.visibility = View.GONE
            }
        }
    }
}
