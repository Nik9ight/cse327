package com.example.llmapp

import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.api.services.gmail.GmailScopes

object GmailDebug {
    private const val TAG = "GmailDebug"

    /**
     * Check if a Google account is currently signed in
     * @return true if an account is signed in, false otherwise
     */
    fun checkSignInStatus(context: Context): Boolean {
        val account = GoogleSignIn.getLastSignedInAccount(context)
        val isSignedIn = account != null
        
        Log.d(TAG, "Check sign-in status: $isSignedIn")
        
        if (isSignedIn) {
            Log.d(TAG, "Account name: ${account!!.displayName}")
            Log.d(TAG, "Account email: ${account.email}")
            
            // Check if the required scopes are granted
            val hasReadScope = GoogleSignIn.hasPermissions(
                account, 
                Scope(GmailScopes.GMAIL_READONLY)
            )
            
            val hasModifyScope = GoogleSignIn.hasPermissions(
                account,
                Scope(GmailScopes.GMAIL_MODIFY)
            )
            
            Log.d(TAG, "Has GMAIL_READONLY scope: $hasReadScope")
            Log.d(TAG, "Has GMAIL_MODIFY scope: $hasModifyScope")
            
            if (!hasReadScope || !hasModifyScope) {
                Log.w(TAG, "Account is signed in but missing required scopes")
                return false
            }
        }
        
        return isSignedIn
    }
    
    /**
     * Create a diagnostic toast message about Google Sign-in status
     */
    fun showSignInDiagnostics(context: Context) {
        val account = GoogleSignIn.getLastSignedInAccount(context)
        
        if (account == null) {
            Toast.makeText(context, "No Google account signed in", Toast.LENGTH_LONG).show()
            return
        }
        
        val hasReadScope = GoogleSignIn.hasPermissions(
            account, 
            Scope(GmailScopes.GMAIL_READONLY)
        )
        
        val hasModifyScope = GoogleSignIn.hasPermissions(
            account,
            Scope(GmailScopes.GMAIL_MODIFY)
        )
        
        val hasSendScope = GoogleSignIn.hasPermissions(
            account,
            Scope(GmailScopes.GMAIL_SEND)
        )
        
        val message = "Signed in as: ${account.email}\n" +
                      "Read scope: ${if (hasReadScope) "✅" else "❌"}\n" +
                      "Modify scope: ${if (hasModifyScope) "✅" else "❌"}\n" +
                      "Send scope: ${if (hasSendScope) "✅" else "❌"}"
                      
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }
    
    /**
     * Handle sign-in result from activity result
     */
    fun handleSignInResult(data: Intent?, onSuccess: (GoogleSignInAccount) -> Unit, onFailure: (Exception) -> Unit) {
        try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            val account = task.getResult(ApiException::class.java)
            
            Log.d(TAG, "Sign-in successful: ${account.displayName}")
            onSuccess(account)
        } catch (e: ApiException) {
            Log.e(TAG, "Sign-in failed with status code: ${e.statusCode}")
            Log.e(TAG, "Error message: ${e.message}")
            onFailure(e)
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error in sign-in result", e)
            onFailure(e)
        }
    }
}
