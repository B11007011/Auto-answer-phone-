package com.example.auto_answer_app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import android.util.Log
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityManager
import android.content.ComponentName
import android.provider.Settings

class CallReceiver : BroadcastReceiver() {
    private val handler = Handler(Looper.getMainLooper())

    override fun onReceive(context: Context?, intent: Intent?) {
        try {
            if (context == null || intent == null) {
                Log.e("CallReceiver", "Null context or intent received")
                return
            }
            
            if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) {
                Log.d("CallReceiver", "Ignoring non-phone state intent: ${intent.action}")
                return
            }

            // Check for READ_PHONE_STATE permission
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) 
                != PackageManager.PERMISSION_GRANTED) {
                Log.e("CallReceiver", "Missing READ_PHONE_STATE permission")
                return
            }

            val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
            Log.d("CallReceiver", "Phone state changed: $state")

            when (state) {
                TelephonyManager.EXTRA_STATE_RINGING -> {
                    try {
                        handleIncomingCall(context, intent)
                    } catch (e: Exception) {
                        Log.e("CallReceiver", "Error handling incoming call: ${e.message}")
                    }
                }
                TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                    Log.d("CallReceiver", "Call answered")
                }
                TelephonyManager.EXTRA_STATE_IDLE -> {
                    Log.d("CallReceiver", "Call ended")
                }
            }
        } catch (e: Exception) {
            Log.e("CallReceiver", "Error in onReceive: ${e.message}")
        }
    }
    
    private fun handleIncomingCall(context: Context, intent: Intent) {
        // Check if we have permission to read the phone number
        val hasReadCallLogPermission = ContextCompat.checkSelfPermission(context, 
            Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED
        
        val incomingNumber = if (hasReadCallLogPermission) {
            intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER) ?: "Unknown"
        } else {
            "Unknown (No permission)"
        }
        Log.d("CallReceiver", "Incoming call from: $incomingNumber")

        // Check if auto-answer is enabled
        val prefs = context.getSharedPreferences("auto_answer_settings", Context.MODE_PRIVATE)
        val autoAnswerEnabled = prefs.getBoolean("auto_answer_enabled", false)
        Log.d("CallReceiver", "Auto answer setting: $autoAnswerEnabled")

        if (autoAnswerEnabled) {
            // Check all required permissions
            val hasPhonePermission = ContextCompat.checkSelfPermission(context, 
                Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
            val hasAnswerCallsPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.checkSelfPermission(context, 
                    Manifest.permission.ANSWER_PHONE_CALLS) == PackageManager.PERMISSION_GRANTED
            } else true

            Log.d("CallReceiver", "Permissions: READ_PHONE_STATE=$hasPhonePermission, " +
                "ANSWER_PHONE_CALLS=$hasAnswerCallsPermission")

            // Try to answer using accessibility service first (more reliable)
            if (isAccessibilityServiceEnabled(context)) {
                Log.d("CallReceiver", "Accessibility service is enabled, attempting to use it")
                startServiceWithRetry(context)
            } else {
                Log.d("CallReceiver", "Accessibility service is not enabled")
                
                // As a fallback, try direct answer if API 26+ and we have permission
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && hasAnswerCallsPermission) {
                    try {
                        val telecomManager = context.getSystemService(Context.TELECOM_SERVICE)
                        telecomManager?.javaClass?.getMethod("acceptRingingCall")?.invoke(telecomManager)
                        Log.d("CallReceiver", "Direct call answer attempt")
                    } catch (e: Exception) {
                        Log.e("CallReceiver", "Failed to answer call directly: ${e.message}")
                    }
                }
            }
        } else {
            Log.d("CallReceiver", "Auto answer is disabled, skipping call handling")
        }
    }

    private fun startServiceWithRetry(context: Context, retryCount: Int = 2, delay: Long = 500) {
        var currentRetry = 0
        
        fun attemptStart() {
            try {
                val serviceIntent = Intent(context, CallControlService::class.java)
                // Don't use FLAG_ACTIVITY_NEW_TASK as it's not appropriate for services
                context.startService(serviceIntent)
                Log.d("CallReceiver", "Started CallControlService, attempt ${currentRetry + 1}")
                
                // Schedule next retry if needed
                if (currentRetry < retryCount - 1) {
                    currentRetry++
                    handler.postDelayed({ attemptStart() }, delay)
                }
            } catch (e: Exception) {
                Log.e("CallReceiver", "Failed to start service on attempt ${currentRetry + 1}: ${e.message}")
            }
        }

        attemptStart()
    }

    private fun isAccessibilityServiceEnabled(context: Context): Boolean {
        try {
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: ""
            
            val serviceName = ComponentName(context.packageName, CallControlService::class.java.name).flattenToString()
            val isEnabled = enabledServices.contains(serviceName)
            
            Log.d("CallReceiver", "Accessibility service ($serviceName) enabled: $isEnabled")
            return isEnabled
        } catch (e: Exception) {
            Log.e("CallReceiver", "Error checking accessibility service: ${e.message}")
            return false
        }
    }
}