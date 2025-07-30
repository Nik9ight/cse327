package com.example.llmapp

import android.content.Intent
import android.graphics.drawable.AnimationDrawable
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.appcompat.widget.PopupMenu
import com.google.android.gms.auth.api.signin.GoogleSignInAccount

class HomeActivity : ComponentActivity() {
    private lateinit var login: Login
    private lateinit var permissions: Permissions
    private val RC_PERMISSIONS = 1002
    private val requiredPermissions = arrayOf(
        android.Manifest.permission.READ_EXTERNAL_STORAGE,
        android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
        android.Manifest.permission.INTERNET,
        android.Manifest.permission.ACCESS_NETWORK_STATE,
        android.Manifest.permission.ACCESS_WIFI_STATE,
        android.Manifest.permission.ACCESS_COARSE_LOCATION,
        android.Manifest.permission.ACCESS_FINE_LOCATION,
        android.Manifest.permission.RECORD_AUDIO,
        android.Manifest.permission.CAMERA,
        android.Manifest.permission.READ_PHONE_STATE,
        android.Manifest.permission.READ_PHONE_NUMBERS
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)
        
        // Start the blinking dot animation
        findViewById<ImageView>(R.id.blinkingDot)?.let { blinkingDot ->
            (blinkingDot.drawable as? AnimationDrawable)?.start()
        }

        login = Login(this)
        login.initGoogleSignIn()
        permissions = Permissions(this)

        val signInButton = findViewById<Button>(R.id.signInButton)
        val enablePermissionsButton = findViewById<Button>(R.id.enablePermissionsButton)
        val pipelineButton = findViewById<Button>(R.id.pipelineButton)
        val workflowsButton = findViewById<Button>(R.id.workflowsButton)
        val llmButton = findViewById<Button>(R.id.llmButton)

        signInButton.setOnClickListener {
            if (login.isSignedIn()) {
                // Sign out
                login.signOut {
                    Toast.makeText(this, "Signed out successfully", Toast.LENGTH_SHORT).show()
                    updateSignInButtonState()
                }
            } else {
                // Sign in
                login.signIn()
            }
        }
        
        // Initial button state update
        updateSignInButtonState()
        
        enablePermissionsButton.setOnClickListener {
            if (!permissions.hasPermissions(requiredPermissions)) {
                permissions.requestPermissions(requiredPermissions, RC_PERMISSIONS)
            } else {
                Toast.makeText(this, "All permissions already granted", Toast.LENGTH_SHORT).show()
            }
        }
        
        pipelineButton.setOnClickListener { view ->
            showPipelineMenu(view)
        }
        
        workflowsButton.setOnClickListener {
            startActivity(Intent(this, WorkflowListActivity::class.java))
        }
        
        llmButton.setOnClickListener { view ->
            showLLMMenu(view)
        }
    }

    override fun onResume() {
        super.onResume()
        // Update button state when returning to the activity
        updateSignInButtonState()
    }

    private fun updateSignInButtonState() {
        val signInButton = findViewById<Button>(R.id.signInButton)
        
        if (login.isSignedIn()) {
            signInButton.text = "SIGN OUT"
        } else {
            signInButton.text = "SIGN IN WITH GOOGLE"
        }
    }

    private fun showPipelineMenu(view: android.view.View) {
        val popup = PopupMenu(this, view)
        popup.menuInflater.inflate(R.menu.pipeline_menu, popup.menu)
        
        popup.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.menu_email_to_telegram -> {
                    startActivity(Intent(this, PipelineActivity::class.java))
                    true
                }
                R.id.menu_telegram_to_gmail -> {
                    startActivity(Intent(this, TelegramToGmailActivity::class.java))
                    true
                }
                else -> false
            }
        }
        
        popup.show()
    }

    private fun showLLMMenu(view: android.view.View) {
        val popup = PopupMenu(this, view)
        popup.menuInflater.inflate(R.menu.llm_menu, popup.menu)
        
        popup.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.menu_m1 -> {
                    startActivity(Intent(this, M1Activity::class.java))
                    true
                }
                R.id.menu_m2_multimodal -> {
                    startActivity(Intent(this, MainActivity::class.java))
                    true
                }
                else -> false
            }
        }
        
        popup.show()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 9001) { // Google Sign-In
            login.handleSignInResult(data,
                onSuccess = { account: GoogleSignInAccount ->
                    Toast.makeText(this, "Signed in as: ${account.displayName}", Toast.LENGTH_SHORT).show()
                    updateSignInButtonState()
                },
                onError = { errorMsg ->
                    Toast.makeText(this, errorMsg, Toast.LENGTH_SHORT).show()
                    updateSignInButtonState()
                }
            )
        }
    }
}
