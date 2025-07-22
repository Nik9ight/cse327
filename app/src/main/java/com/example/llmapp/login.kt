package com.example.llmapp

import android.app.Activity
import android.content.Intent
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.api.services.gmail.GmailScopes

class Login(private val activity: Activity) {
    private lateinit var googleSignInClient: GoogleSignInClient
    private val RC_SIGN_IN = 9001
    private val TAG = "Login"

    fun initGoogleSignIn(requestGmailAccess: Boolean = false) {
        val gsoBuilder = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
        
        // Add Gmail scopes if needed
        if (requestGmailAccess) {
            gsoBuilder
                .requestScopes(Scope(GmailScopes.GMAIL_READONLY))
                .requestScopes(Scope(GmailScopes.GMAIL_MODIFY))
        }
        
        val gso = gsoBuilder.build()
        googleSignInClient = GoogleSignIn.getClient(activity, gso)
    }

    fun signIn() {
        val signInIntent = googleSignInClient.signInIntent
        activity.startActivityForResult(signInIntent, RC_SIGN_IN)
    }

    fun handleSignInResult(data: Intent?, onSuccess: (GoogleSignInAccount) -> Unit, onError: (String) -> Unit) {
        if (data == null) {
            Log.e(TAG, "Google Sign-In failed: Intent data is null")
            onError("Google Sign-In failed: Sign-in was cancelled or failed")
            return
        }
        
        try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            val account = task.getResult(ApiException::class.java)
            
            if (account != null) {
                Log.d(TAG, "Google Sign-In successful: ${account.email}")
                onSuccess(account)
            } else {
                Log.e(TAG, "Google Sign-In failed: account is null")
                onError("Google Sign-In failed: account is null")
            }
        } catch (e: ApiException) {
            val statusCode = e.statusCode
            val statusMessage = when (statusCode) {
                10 -> "DEVELOPER_ERROR - Check your Google Cloud Console configuration, SHA-1 fingerprint, and package name"
                12500 -> "SIGN_IN_CANCELLED - User canceled the sign-in flow"
                12501 -> "SIGN_IN_FAILED - Sign-in failed for some reason"
                12502 -> "SIGN_IN_REQUIRED - Sign-in required but not attempted"
                else -> "Unknown error code: $statusCode"
            }
            
            Log.e(TAG, "Google Sign-In failed with status code: $statusCode ($statusMessage)", e)
            onError("Google Sign-In failed: $statusMessage")
        } catch (e: Exception) {
            Log.e(TAG, "Google Sign-In unexpected error", e)
            onError("Google Sign-In failed: ${e.message}")
        }
    }

    fun signOut(onComplete: () -> Unit) {
        googleSignInClient.signOut().addOnCompleteListener { onComplete() }
    }
    
    // Check if the user has granted the requested Gmail scopes
    fun hasGmailScopes(): Boolean {
        val account = GoogleSignIn.getLastSignedInAccount(activity)
        return account != null && 
               GoogleSignIn.hasPermissions(account, Scope(GmailScopes.GMAIL_READONLY)) &&
               GoogleSignIn.hasPermissions(account, Scope(GmailScopes.GMAIL_MODIFY))
    }
    
    // Get the constant to identify the sign-in request
    fun getRequestCode(): Int {
        return RC_SIGN_IN
    }
    
    // Check if any Google account is signed in
    fun isSignedIn(): Boolean {
        return GoogleSignIn.getLastSignedInAccount(activity) != null
    }
}
