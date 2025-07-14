package com.example.llmapp

import android.app.Activity
import android.util.Log
import android.widget.Toast
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.Scope
import com.google.api.services.gmail.GmailScopes

/**
 * Helper class for diagnosing Google Sign-In and Gmail API issues
 */
object GmailLoginHelper {
    private const val TAG = "GmailLoginHelper"
    
    /**
     * Check and display current Gmail authentication status
     */
    fun checkAndDisplayStatus(activity: Activity) {
        val account = GoogleSignIn.getLastSignedInAccount(activity)
        
        if (account == null) {
            showToast(activity, "No Google account signed in")
            Log.d(TAG, "No Google account signed in")
            return
        }
        
        // Check for Gmail permissions
        val hasReadScope = GoogleSignIn.hasPermissions(
            account, 
            Scope(GmailScopes.GMAIL_READONLY)
        )
        
        val hasModifyScope = GoogleSignIn.hasPermissions(
            account,
            Scope(GmailScopes.GMAIL_MODIFY)
        )
        
        val message = buildString {
            append("Account: ${account.email}\n")
            append("Display Name: ${account.displayName}\n")
            append("Read Permission: ${if (hasReadScope) "✓" else "✗"}\n")
            append("Modify Permission: ${if (hasModifyScope) "✓" else "✗"}")
        }
        
        Log.d(TAG, message)
        showToast(activity, message)
    }
    
    /**
     * Display toast message
     */
    private fun showToast(activity: Activity, message: String) {
        Toast.makeText(activity, message, Toast.LENGTH_LONG).show()
    }
    
    /**
     * Log details about the current authentication state
     */
    fun logAuthState(activity: Activity) {
        val account = GoogleSignIn.getLastSignedInAccount(activity)
        
        Log.d(TAG, "============= Gmail Auth Status =============")
        if (account == null) {
            Log.d(TAG, "Status: No Google account signed in")
        } else {
            Log.d(TAG, "Status: Signed in")
            Log.d(TAG, "Email: ${account.email}")
            Log.d(TAG, "Display Name: ${account.displayName}")
            Log.d(TAG, "ID: ${account.id}")
            Log.d(TAG, "Has Gmail Read Permission: ${GoogleSignIn.hasPermissions(account, Scope(GmailScopes.GMAIL_READONLY))}")
            Log.d(TAG, "Has Gmail Modify Permission: ${GoogleSignIn.hasPermissions(account, Scope(GmailScopes.GMAIL_MODIFY))}")
        }
        Log.d(TAG, "===========================================")
    }
}
