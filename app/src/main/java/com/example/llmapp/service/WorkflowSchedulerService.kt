package com.example.llmapp.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.example.llmapp.receiver.AlarmReceiver

class WorkflowSchedulerService : Service() {
    
    private val TAG = "WorkflowSchedulerService"
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Scheduler service started")
        
        val action = intent?.getStringExtra("action")
        when (action) {
            "schedule_workflow" -> {
                val workflowId = intent.getStringExtra("workflow_id")
                val intervalSeconds = intent.getLongExtra("interval_seconds", 300)
                scheduleWorkflow(workflowId, intervalSeconds)
            }
            "cancel_workflow" -> {
                val workflowId = intent.getStringExtra("workflow_id")
                cancelWorkflow(workflowId)
            }
        }
        
        return START_NOT_STICKY
    }
    
    private fun scheduleWorkflow(workflowId: String?, intervalSeconds: Long) {
        if (workflowId == null) return
        
        try {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(this, AlarmReceiver::class.java).apply {
                putExtra("workflow_id", workflowId)
            }
            
            val pendingIntent = PendingIntent.getBroadcast(
                this,
                workflowId.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
            val triggerTime = System.currentTimeMillis() + (intervalSeconds * 1000)
            val intervalMillis = intervalSeconds * 1000
            
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
                    // Use setExactAndAllowWhileIdle for precise timing
                    alarmManager.setRepeating(
                        AlarmManager.RTC_WAKEUP,
                        triggerTime,
                        intervalMillis,
                        pendingIntent
                    )
                }
                else -> {
                    alarmManager.setRepeating(
                        AlarmManager.RTC_WAKEUP,
                        triggerTime,
                        intervalMillis,
                        pendingIntent
                    )
                }
            }
            
            Log.d(TAG, "Scheduled workflow $workflowId with interval ${intervalSeconds}s")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule workflow $workflowId", e)
        }
    }
    
    private fun cancelWorkflow(workflowId: String?) {
        if (workflowId == null) return
        
        try {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(this, AlarmReceiver::class.java).apply {
                putExtra("workflow_id", workflowId)
            }
            
            val pendingIntent = PendingIntent.getBroadcast(
                this,
                workflowId.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
            alarmManager.cancel(pendingIntent)
            Log.d(TAG, "Cancelled workflow $workflowId")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cancel workflow $workflowId", e)
        }
    }
    
    companion object {
        fun scheduleWorkflow(context: Context, workflowId: String, intervalSeconds: Long) {
            val intent = Intent(context, WorkflowSchedulerService::class.java).apply {
                putExtra("action", "schedule_workflow")
                putExtra("workflow_id", workflowId)
                putExtra("interval_seconds", intervalSeconds)
            }
            context.startService(intent)
        }
        
        fun cancelWorkflow(context: Context, workflowId: String) {
            val intent = Intent(context, WorkflowSchedulerService::class.java).apply {
                putExtra("action", "cancel_workflow")
                putExtra("workflow_id", workflowId)
            }
            context.startService(intent)
        }
    }
}
