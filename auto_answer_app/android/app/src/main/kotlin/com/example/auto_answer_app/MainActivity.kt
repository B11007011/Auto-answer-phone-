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
import android.media.AudioManager

class MainActivity: FlutterActivity() {
    private val CHANNEL = "call_control"
    private val PERMISSION_REQUEST_CODE = 123
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var audioManager: AudioManager

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        handler.postDelayed({
            requestRequiredPermissions()
            // Check and restore settings on startup
            loadAndApplySettings()
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
                            // Immediately try to apply speaker settings - this is important for Xiaomi devices
                            if (speaker) {
                                trySpeakerSettings()
                            }
                            result.success(null)
                        }
                        "forceSpeakerOn" -> {
                            // New method to force speaker on for Xiaomi devices
                            trySpeakerSettings()
                            result.success(true)
                        }
                        "getDeviceInfo" -> {
                            // Return device manufacturer and model information
                            val deviceInfo = getDeviceInfoString()
                            Log.d("MainActivity", "Device info requested: $deviceInfo")
                            result.success(deviceInfo)
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

            // For Xiaomi devices, we need this extra permission
            if (isXiaomiDevice()) {
                Log.d("MainActivity", "Detected Xiaomi device, requesting additional permissions")
                try {
                    permissions.add("android.permission.READ_PRIVILEGED_PHONE_STATE")
                } catch (e: Exception) {
                    // This permission might not be available on all Xiaomi devices
                    Log.d("MainActivity", "Additional Xiaomi permission not available: ${e.message}")
                }
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
            
            val autoAnswerServiceName = AutoAnswerService::class.java.name
            val callControlServiceName = CallControlService::class.java.name
            
            val isEnabled = enabledServices.any { 
                val serviceName = it.resolveInfo.serviceInfo.name
                serviceName == autoAnswerServiceName || serviceName == callControlServiceName
            }
            
            Log.d("MainActivity", "Checking accessibility service: $isEnabled")
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
                    restartAccessibilityServices()
                } catch (e: Exception) {
                    Log.e("MainActivity", "Error refreshing service: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error updating settings: ${e.message}")
        }
    }
    
    private fun restartAccessibilityServices() {
        // For Xiaomi devices, we need a special approach to restart services
        if (isXiaomiDevice()) {
            Log.d("MainActivity", "Using Xiaomi-specific service restart method")
            // For Xiaomi, we don't actually restart the service as it can be unreliable
            // Instead, we'll rely on settings being read directly from SharedPreferences
        } else {
            // Standard service restart for other devices
            try {
                val intent = Intent().apply {
                    component = ComponentName(packageName, AutoAnswerService::class.java.name)
                }
                stopService(intent)
                startService(intent)
            } catch (e: Exception) {
                Log.e("MainActivity", "Error restarting services: ${e.message}")
            }
        }
    }
    
    private fun loadAndApplySettings() {
        try {
            val prefs = getSharedPreferences("auto_answer_settings", Context.MODE_PRIVATE)
            val speakerEnabled = prefs.getBoolean("speaker_enabled", false)
            
            Log.d("MainActivity", "Loading settings on startup: speaker=$speakerEnabled")
            
            // If speaker is enabled in settings, try to prepare it
            if (speakerEnabled) {
                trySpeakerSettings()
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error loading settings: ${e.message}")
        }
    }
    
    private fun trySpeakerSettings() {
        try {
            // Set audio mode to normal first
            audioManager.mode = AudioManager.MODE_NORMAL
            
            // Then set speaker settings
            Log.d("MainActivity", "Forcing speaker mode to prepare for calls")
            audioManager.isSpeakerphoneOn = true
            
            // Try different audio modes
            val modes = listOf(
                AudioManager.MODE_IN_CALL,
                AudioManager.MODE_IN_COMMUNICATION,
                AudioManager.MODE_NORMAL
            )
            
            // Try each mode with speaker on
            for (mode in modes) {
                try {
                    audioManager.mode = mode
                    audioManager.isSpeakerphoneOn = true
                    Log.d("MainActivity", "Set audio mode $mode with speaker ON")
                } catch (e: Exception) {
                    Log.e("MainActivity", "Failed to set audio mode $mode: ${e.message}")
                }
            }
            
            // If this is a Xiaomi device, try reflection method as well
            if (isXiaomiDevice()) {
                try {
                    Log.d("MainActivity", "Trying Xiaomi-specific audio methods")
                    val audioSystem = Class.forName("android.media.AudioSystem")
                    val setForceUse = audioSystem.getMethod("setForceUse", Int::class.java, Int::class.java)
                    
                    // Constants from AudioSystem
                    val FOR_COMMUNICATION = 0
                    val FOR_MEDIA = 1
                    val FORCE_SPEAKER = 1
                    
                    setForceUse.invoke(null, FOR_COMMUNICATION, FORCE_SPEAKER)
                    setForceUse.invoke(null, FOR_MEDIA, FORCE_SPEAKER)
                    Log.d("MainActivity", "Applied Xiaomi-specific speaker methods")
                } catch (e: Exception) {
                    Log.e("MainActivity", "Xiaomi-specific methods failed: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error setting speaker mode: ${e.message}")
        }
    }
    
    private fun isXiaomiDevice(): Boolean {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val model = Build.MODEL.lowercase()
        val isXiaomi = manufacturer.contains("xiaomi") || 
                      manufacturer.contains("redmi") || 
                      model.contains("xiaomi") || 
                      model.contains("redmi") ||
                      model.contains("poco")
                      
        Log.d("MainActivity", "Device check - Manufacturer: $manufacturer, Model: $model, Is Xiaomi: $isXiaomi")
        return isXiaomi
    }

    private fun getDeviceInfoString(): String {
        return "${Build.MANUFACTURER} ${Build.MODEL}"
    }
}
