package com.example.llmapp

import android.app.Application
import android.util.Log
import androidx.work.Configuration
import androidx.work.WorkManager

class LLMApplication : Application(), Configuration.Provider {
    
    override fun onCreate() {
        super.onCreate()
        // No need to manually initialize WorkManager anymore
        // It will automatically use our configuration
        
        Log.d("LLMApplication", "Application initialized")
    }
    
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(Log.INFO)
            .build()
}
