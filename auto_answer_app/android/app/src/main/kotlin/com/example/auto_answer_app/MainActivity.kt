package com.example.auto_answer_app

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugins.GeneratedPluginRegistrant
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.util.Log
import android.content.ComponentName
import android.os.Handler
import android.os.Looper

class MainActivity: FlutterActivity() {
    private val CHANNEL = "call_control"
    private val PERMISSION_REQUEST_CODE = 123
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        handler.postDelayed({
            requestRequiredPermissions()
        }, 2000)
    }
    
    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        setupMethodChannel(flutterEngine)
    }
    
    private fun setupMethodChannel(flutterEngine: FlutterEngine) {
        try {
            MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL).setMethodCallHandler { call, result ->
                try {
                    when (call.method) {
                        "isAccessibilityServiceEnabled" -> {
                            val isEnabled = isAccessibilityServiceEnabled()
                            Log.d("MainActivity", "Accessibility service enabled: $isEnabled")
                            result.success(isEnabled)
                        }
                        "openAccessibility" -> {
                            openAccessibilitySettings()
                            result.success(null)
                        }
                        "updateSettings" -> {
                            val autoAnswer = call.argument<Boolean>("autoAnswer") ?: false
                            val speaker = call.argument<Boolean>("speaker") ?: false
                            Log.d("MainActivity", "Updating settings: autoAnswer=$autoAnswer, speaker=$speaker")
                            updateServiceSettings(autoAnswer, speaker)
                            result.success(null)
                        }
                        else -> {
                            result.notImplemented()
                        }
                    }
                } catch (e: Exception) {
                    Log.e("MainActivity", "Error handling method call: ${e.message}")
                    result.error("ERROR", e.message, null)
                }
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error setting up method channel: ${e.message}")
        }
    }

    private fun requestRequiredPermissions() {
        try {
            val permissions = mutableListOf(
                Manifest.permission.READ_PHONE_STATE,
                Manifest.permission.MODIFY_AUDIO_SETTINGS,
                Manifest.permission.READ_CALL_LOG
            )

            // Add Android 8.0+ specific permissions
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                permissions.add(Manifest.permission.ANSWER_PHONE_CALLS)
            }

            val permissionsToRequest = permissions.filter {
                ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
            }.toTypedArray()

            if (permissionsToRequest.isNotEmpty()) {
                Log.d("MainActivity", "Requesting permissions: ${permissionsToRequest.joinToString()}")
                ActivityCompat.requestPermissions(this, permissionsToRequest, PERMISSION_REQUEST_CODE)
            } else {
                Log.d("MainActivity", "All permissions already granted")
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error requesting permissions: ${e.message}")
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        try {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults)
            
            if (requestCode == PERMISSION_REQUEST_CODE) {
                for (i in permissions.indices) {
                    val permission = permissions[i]
                    val granted = grantResults[i] == PackageManager.PERMISSION_GRANTED
                    Log.d("MainActivity", "Permission $permission granted: $granted")
                }
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error in onRequestPermissionsResult: ${e.message}")
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        try {
            val am = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
            val enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            
            val componentName = ComponentName(packageName, CallControlService::class.java.name)
            val isEnabled = enabledServices.any { 
                ComponentName(it.resolveInfo.serviceInfo.packageName, it.resolveInfo.serviceInfo.name) == componentName
            }
            
            Log.d("MainActivity", "Checking accessibility service: $isEnabled for component: $componentName")
            return isEnabled
        } catch (e: Exception) {
            Log.e("MainActivity", "Error checking accessibility service: ${e.message}")
            return false
        }
    }

    private fun openAccessibilitySettings() {
        try {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(intent)
        } catch (e: Exception) {
            Log.e("MainActivity", "Error opening accessibility settings: ${e.message}")
        }
    }

    private fun updateServiceSettings(autoAnswer: Boolean, speaker: Boolean) {
        try {
            val prefs = getSharedPreferences("auto_answer_settings", Context.MODE_PRIVATE)
            prefs.edit().apply {
                putBoolean("auto_answer_enabled", autoAnswer)
                putBoolean("speaker_enabled", speaker)
                apply()
            }
            Log.d("MainActivity", "Settings updated: autoAnswer=$autoAnswer, speaker=$speaker")
            
            // Only attempt service refresh if accessibility service is enabled
            if (isAccessibilityServiceEnabled()) {
                try {
                    val intent = Intent().apply {
                        component = ComponentName(packageName, CallControlService::class.java.name)
                    }
                    stopService(intent)
                    startService(intent)
                } catch (e: Exception) {
                    Log.e("MainActivity", "Error refreshing service: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error updating settings: ${e.message}")
        }
    }
}
