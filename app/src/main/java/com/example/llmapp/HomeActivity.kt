package com.example.llmapp

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.activity.ComponentActivity
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
    private lateinit var m1Button: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        login = Login(this)
        login.initGoogleSignIn()
        permissions = Permissions(this)

        val signInButton = findViewById<Button>(R.id.signInButton)
        val enablePermissionsButton = findViewById<Button>(R.id.enablePermissionsButton)
        m1Button = findViewById(R.id.m1Button)
        val textImageButton = findViewById<Button>(R.id.textImageButton)

        signInButton.setOnClickListener {
            login.signIn()
        }

        enablePermissionsButton.setOnClickListener {
            if (permissions.hasPermissions(requiredPermissions)) {
                Toast.makeText(this, "All permissions already granted", Toast.LENGTH_SHORT).show()
            } else {
                permissions.requestPermissions(requiredPermissions, RC_PERMISSIONS)
            }
        }

        m1Button.setOnClickListener {
            val intent = Intent(this, M1Activity::class.java)
            startActivity(intent)
        }

        textImageButton.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 9001) { // Google Sign-In
            login.handleSignInResult(data,
                onSuccess = { account: GoogleSignInAccount ->
                    Toast.makeText(this, "Signed in as: ${account.displayName}", Toast.LENGTH_SHORT).show()
                },
                onError = { errorMsg ->
                    Toast.makeText(this, errorMsg, Toast.LENGTH_SHORT).show()
                }
            )
        }
    }
}
