package com.example.llmapp

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage

class MainActivity : ComponentActivity() {
    private lateinit var chatView: llmChatView
    private var selectedImage: Bitmap? = null
    private val IMAGE_PICK_CODE = 1001
    private var outputAccumulator: StringBuilder = StringBuilder()
    private lateinit var model: Model

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        model = MODEL_TEXT_CLASSIFICATION_MOBILEBERT.copy(llmSupportImage = true)
        chatView = llmChatView(model)
        chatView.initialize(this) {}

        val inputText = findViewById<EditText>(R.id.inputText)
        val sendButton = findViewById<Button>(R.id.sendButton)
        val outputText = findViewById<TextView>(R.id.outputText)
        val attachImageButton = findViewById<Button>(R.id.attachImageButton)
        val stopResponseButton = findViewById<Button>(R.id.stopResponseButton)
        val selectedImageView = findViewById<ImageView>(R.id.selectedImageView)

        attachImageButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK)
            intent.type = "image/*"
            startActivityForResult(intent, IMAGE_PICK_CODE)
        }

        sendButton.setOnClickListener {
            val input = inputText.text.toString()
            // Ensure selectedImage is ARGB_8888
            val safeBitmap = selectedImage?.let {
                if (it.config != Bitmap.Config.ARGB_8888) {
                    it.copy(Bitmap.Config.ARGB_8888, false)
                } else {
                    it
                }
            }
            val images = safeBitmap?.let { listOf(BitmapImageBuilder(it).build()) } ?: listOf()
            outputAccumulator = StringBuilder() // Reset accumulator for new message
            chatView.generateResponse(
                context = this,
                input = input,
                images = images,
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
            // Clear the input text after sending
            inputText.setText("")
            // Hide keyboard after sending
            val imm = getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.hideSoftInputFromWindow(inputText.windowToken, 0)
        }

        stopResponseButton.setOnClickListener {
            if (chatView.inProgress) {
                // No need to pass the model since chatView already has it
                chatView.stopResponse(model)
                Toast.makeText(this, "Response generation stopped", Toast.LENGTH_SHORT).show()
                
                // Add a visual indicator that response was stopped
                outputAccumulator.append("\n\n[Response generation stopped by user]")
                outputText.text = outputAccumulator.toString()
            } else {
                Toast.makeText(this, "No response generation in progress", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == IMAGE_PICK_CODE && resultCode == Activity.RESULT_OK) {
            val imageUri: Uri? = data?.data
            imageUri?.let {
                selectedImage = if (Build.VERSION.SDK_INT < 28) {
                    MediaStore.Images.Media.getBitmap(this.contentResolver, it)
                } else {
                    val source = ImageDecoder.createSource(this.contentResolver, it)
                    ImageDecoder.decodeBitmap(source)
                }
                val selectedImageView = findViewById<ImageView>(R.id.selectedImageView)
                selectedImageView.setImageBitmap(selectedImage)
                selectedImageView.visibility = ImageView.VISIBLE
            }
        }
    }
}