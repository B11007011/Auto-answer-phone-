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
import android.accessibilityservice.AccessibilityService

class CallReceiver : BroadcastReceiver() {
    private val handler = Handler(Looper.getMainLooper())

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null || intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) {
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

                    // Try to answer immediately if we have the permission
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && hasAnswerCallsPermission) {
                        val telecomManager = context.getSystemService(Context.TELECOM_SERVICE)
                        try {
                            telecomManager?.javaClass?.getMethod("acceptRingingCall")?.invoke(telecomManager)
                            Log.d("CallReceiver", "Direct call answer attempt successful")
                        } catch (e: Exception) {
                            Log.e("CallReceiver", "Failed to answer call directly: ${e.message}")
                            e.printStackTrace()
                        }
                    }

                    // Start the accessibility service with retry mechanism
                    if (isAccessibilityServiceEnabled(context)) {
                        Log.d("CallReceiver", "Starting service with retry mechanism")
                        startServiceWithRetry(context)
                    } else {
                        Log.e("CallReceiver", "Accessibility service is not enabled")
                    }
                } else {
                    Log.d("CallReceiver", "Auto answer is disabled, skipping call handling")
                }
            }
            TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                Log.d("CallReceiver", "Call answered")
            }
            TelephonyManager.EXTRA_STATE_IDLE -> {
                Log.d("CallReceiver", "Call ended")
            }
        }
    }

    private fun startServiceWithRetry(context: Context, retryCount: Int = 3, delay: Long = 500) {
        var currentRetry = 0
        
        fun attemptStart() {
            try {
                val serviceIntent = Intent(context, CallControlService::class.java)
                serviceIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
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
            val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )
            val componentName = ComponentName(context.packageName, CallControlService::class.java.name).flattenToString()
            return enabledServices?.contains(componentName) == true
        } catch (e: Exception) {
            Log.e("CallReceiver", "Error checking accessibility service: ${e.message}")
            return false
        }
    }
}