package com.example.llmapp.workflows.demo

import android.app.Activity
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.example.llmapp.workflows.camera.PersonDetectionWorkflow
import kotlinx.coroutines.*

/**
 * Demo activity to test face matching functionality
 * This can be removed once testing is complete
 */
class FaceMatchingDemoActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "FaceMatchingDemo"
    }
    
    private val demoScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Simple demo without UI
        runFaceMatchingDemo()
    }
    
    private fun runFaceMatchingDemo() {
        Log.d(TAG, "Starting face matching demo")
        
        demoScope.launch {
            try {
                val workflow = PersonDetectionWorkflow(
                    this@FaceMatchingDemoActivity,
                    "dummy_chat_id",
                    workflowId = "demo_workflow"
                )
                
                Log.d(TAG, "PersonDetectionWorkflow initialized successfully")
                Log.d(TAG, "Face matching service is ready to use")
                Log.d(TAG, "Reference image status: ${workflow.hasReferenceImage()}")
                
                // Demo complete
                Log.d(TAG, "Face matching demo completed successfully")
                
            } catch (e: Exception) {
                Log.e(TAG, "Error in face matching demo", e)
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        demoScope.cancel()
    }
}
