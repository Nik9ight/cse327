package com.example.llmapp

import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity

class TelegramLoginActivity : ComponentActivity() {
    private lateinit var telegramService: TelegramService
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_telegram_login)
        
        telegramService = TelegramService(this)
        
        val botTokenEditText = findViewById<EditText>(R.id.botTokenEditText)
        val connectBotButton = findViewById<Button>(R.id.connectBotButton)
        val chatIdEditText = findViewById<EditText>(R.id.chatIdEditText)
        val findChatIdButton = findViewById<Button>(R.id.findChatIdButton)
        val saveChatIdButton = findViewById<Button>(R.id.saveChatIdButton)
        val testMessageButton = findViewById<Button>(R.id.testMessageButton)
        val botTokenHelpText = findViewById<TextView>(R.id.botTokenHelpText)
        val chatIdHelpText = findViewById<TextView>(R.id.chatIdHelpText)
        val troubleshootButton = findViewById<Button>(R.id.troubleshootButton)
        
        // Make help text links clickable
        botTokenHelpText.movementMethod = LinkMovementMethod.getInstance()
        chatIdHelpText.movementMethod = LinkMovementMethod.getInstance()
        
        // Check if already logged in
        if (telegramService.isLoggedIn()) {
            testMessageButton.isEnabled = true
        }
        
        connectBotButton.setOnClickListener {
            val token = botTokenEditText.text.toString().trim()
            if (token.isEmpty()) {
                Toast.makeText(this, "Please enter a bot token", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            // Validate token format (should be like 123456789:ABCDefGhIJKlmNoPQRsTUVwxyZ)
            if (!token.contains(":")) {
                Toast.makeText(this, "Invalid token format. It should look like: 123456789:ABCDefGhIJKlmNoPQRsTUVwxyZ", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            
            telegramService.login(
                token = token,
                onSuccess = {
                    Toast.makeText(this, "Bot connected successfully", Toast.LENGTH_SHORT).show()
                    updateUI()
                },
                onError = { error ->
                    Toast.makeText(this, "Error: $error", Toast.LENGTH_LONG).show()
                }
            )
        }
        
        findChatIdButton.setOnClickListener {
            telegramService.openTelegramToFindChatId()
        }
        
        saveChatIdButton.setOnClickListener {
            val chatId = chatIdEditText.text.toString().trim()
            if (chatId.isEmpty()) {
                Toast.makeText(this, "Please enter a chat ID", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            // First verify the chat ID
            Toast.makeText(this, "Verifying chat ID...", Toast.LENGTH_SHORT).show()
            telegramService.verifyChatId(chatId,
                onSuccess = {
                    // Save the chat ID only if verification succeeds
                    telegramService.setChatId(chatId)
                    Toast.makeText(this, "Chat ID verified and saved", Toast.LENGTH_SHORT).show()
                    updateUI()
                },
                onError = { error ->
                    Toast.makeText(this, error, Toast.LENGTH_LONG).show()
                    // Show help dialog with instructions
                    showChatIdHelpDialog()
                }
            )
        }
        
        testMessageButton.setOnClickListener {
            telegramService.sendMessage(
                text = "Test message from LLMAPP",
                onSuccess = {
                    Toast.makeText(this, "Message sent successfully", Toast.LENGTH_SHORT).show()
                },
                onError = { error ->
                    Toast.makeText(this, "Error: $error", Toast.LENGTH_LONG).show()
                }
            )
        }
        
        troubleshootButton.setOnClickListener {
            showChatIdHelpDialog()
        }
    }
    
    private fun updateUI() {
        val testMessageButton = findViewById<Button>(R.id.testMessageButton)
        testMessageButton.isEnabled = telegramService.isLoggedIn()
    }
    
    private fun showChatIdHelpDialog() {
        val dialogBuilder = androidx.appcompat.app.AlertDialog.Builder(this)
        dialogBuilder.setTitle("Chat ID Help")
        dialogBuilder.setMessage("""
            Error "chat not found" usually means one of these issues:
            
            1. The chat ID format is incorrect
               - For groups: Must start with "-100" (e.g., -1001234567890)
               - For personal chats: Just numbers (e.g., 123456789)
            
            2. Your bot is not a member of the group
               - Add your bot to the group first
               - Make sure the bot has permission to send messages
            
            3. The bot hasn't started a conversation with you
               - For personal chats, send a message to the bot first
            
            To find your chat ID:
            1. Add @getidsbot to your group
            2. Or use @getidsbot in a personal chat
            3. It will show you the correct ID to use
        """.trimIndent())
        
        dialogBuilder.setPositiveButton("Open Telegram") { dialog, _ ->
            telegramService.openTelegramToFindChatId()
            dialog.dismiss()
        }
        
        dialogBuilder.setNegativeButton("Close") { dialog, _ ->
            dialog.dismiss()
        }
        
        val dialog = dialogBuilder.create()
        dialog.show()
    }
}
