package com.example.auto_answer_app

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import android.content.SharedPreferences
import android.media.AudioManager
import android.telecom.TelecomManager
import android.content.Context
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import android.os.Handler
import android.os.Looper
import android.graphics.Path
import android.graphics.Rect
import android.view.accessibility.AccessibilityWindowInfo

class CallControlService : AccessibilityService() {
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var audioManager: AudioManager
    private val handler = Handler(Looper.getMainLooper())
    private var isHandlingCall = false

    override fun onCreate() {
        try {
            super.onCreate()
            sharedPreferences = getSharedPreferences("auto_answer_settings", Context.MODE_PRIVATE)
            audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            Log.d("CallControlService", "Service created with settings: autoAnswer=${sharedPreferences.getBoolean("auto_answer_enabled", false)}, speaker=${sharedPreferences.getBoolean("speaker_enabled", false)}")
        } catch (e: Exception) {
            Log.e("CallControlService", "Error in onCreate: ${e.message}")
        }
    }

    override fun onServiceConnected() {
        try {
            super.onServiceConnected()
            Log.d("CallControlService", "Accessibility service connected")
            
            // Configure accessibility service if needed
            val info = serviceInfo
            info.packageNames = arrayOf(
                "com.android.dialer",
                "com.android.incallui",
                "com.google.android.dialer",
                "com.samsung.android.dialer"
            )
            serviceInfo = info
        } catch (e: Exception) {
            Log.e("CallControlService", "Error in onServiceConnected: ${e.message}")
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        try {
            if (event == null) return

            val prefs = getSharedPreferences("auto_answer_settings", Context.MODE_PRIVATE)
            val autoAnswerEnabled = prefs.getBoolean("auto_answer_enabled", false)
            val speakerEnabled = prefs.getBoolean("speaker_enabled", false)

            Log.d("CallControlService", "Received event: type=${event.eventType}, package=${event.packageName}")
            Log.d("CallControlService", "Settings: autoAnswer=$autoAnswerEnabled, speaker=$speakerEnabled")

            // Only process events if auto-answer is enabled
            if (!autoAnswerEnabled) {
                Log.d("CallControlService", "Auto answer is disabled, ignoring event")
                return
            }

            // Check if this is a relevant dialer package
            val dialerPackages = listOf(
                "com.android.dialer",
                "com.android.incallui",
                "com.google.android.dialer",
                "com.samsung.android.dialer"
            )
            
            if (event.packageName?.toString() !in dialerPackages) {
                Log.d("CallControlService", "Event from non-dialer package, ignoring")
                return
            }

            // Process window state changes
            when (event.eventType) {
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                    Log.d("CallControlService", "Window state changed: ${event.className}")
                    handleIncomingCall(event, speakerEnabled)
                }
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                    Log.d("CallControlService", "Window content changed: ${event.className}")
                    if (event.contentChangeTypes == AccessibilityEvent.CONTENT_CHANGE_TYPE_PANE_APPEARED) {
                        handleIncomingCall(event, speakerEnabled)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("CallControlService", "Error in onAccessibilityEvent: ${e.message}")
        }
    }

    private fun handleIncomingCall(event: AccessibilityEvent, speakerEnabled: Boolean) {
        try {
            val rootNode = rootInActiveWindow
            if (rootNode == null) {
                Log.e("CallControlService", "Root node is null")
                return
            }

            // Look for answer button with different possible resource IDs
            val answerButtonIds = listOf(
                "android:id/answer_button",
                "com.android.dialer:id/answer_button",
                "com.google.android.dialer:id/answer_button",
                "com.samsung.android.dialer:id/answer_button",
                "answer_button",
                // Xiaomi specific IDs
                "com.android.incallui:id/answer_button",
                "com.xiaomi.incallui:id/answer_button",
                "com.miui.incallui:id/answer_button",
                "com.android.phone:id/answer_button"
            )

            var answerButton: AccessibilityNodeInfo? = null
            try {
                answerButton = answerButtonIds.firstNotNullOfOrNull { id ->
                    try {
                        rootNode.findAccessibilityNodeInfosByViewId(id).firstOrNull()
                    } catch (e: Exception) {
                        Log.e("CallControlService", "Error finding button by ID $id: ${e.message}")
                        null
                    }
                }
            } catch (e: Exception) {
                Log.e("CallControlService", "Error searching for answer buttons: ${e.message}")
            }

            // If no button found by ID, try to find by text
            if (answerButton == null) {
                Log.d("CallControlService", "Answer button not found by ID, trying text search")
                try {
                    answerButton = findNodeByText(rootNode, listOf(
                        "Answer", "ANSWER", "Accept", "ACCEPT", 
                        // Add Chinese text for Xiaomi devices 
                        "接听", "接聽", "接通", "應答"
                    ))
                } catch (e: Exception) {
                    Log.e("CallControlService", "Error searching for answer button by text: ${e.message}")
                }
            }

            if (answerButton != null && answerButton.isClickable) {
                Log.d("CallControlService", "Found answer button, attempting to click")
                val clicked = answerButton.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                Log.d("CallControlService", "Answer button click result: $clicked")

                if (clicked && speakerEnabled) {
                    enableSpeakerphone()
                }
            } else {
                Log.d("CallControlService", "Answer button not found or not clickable")
            }

            rootNode.recycle()
        } catch (e: Exception) {
            Log.e("CallControlService", "Error handling incoming call: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun findNodeByText(root: AccessibilityNodeInfo, texts: List<String>): AccessibilityNodeInfo? {
        for (text in texts) {
            try {
                root.findAccessibilityNodeInfosByText(text).firstOrNull()?.let { return it }
            } catch (e: Exception) {
                Log.e("CallControlService", "Error finding node by text '$text': ${e.message}")
            }
        }
        return null
    }

    private fun enableSpeakerphone() {
        try {
            if (sharedPreferences.getBoolean("speaker_enabled", false)) {
                // Try multiple times with increasing delays to ensure speaker activation works
                val delayTimes = listOf(500L, 1000L, 2000L)
                
                for ((index, delay) in delayTimes.withIndex()) {
                    handler.postDelayed({
                        try {
                            audioManager.mode = AudioManager.MODE_IN_CALL
                            audioManager.isSpeakerphoneOn = true
                            Log.d("CallControlService", "Speaker enabled (attempt ${index + 1})")
                        } catch (e: Exception) {
                            Log.e("CallControlService", "Failed to enable speaker on attempt ${index + 1}: ${e.message}")
                        }
                    }, delay)
                }
            }
        } catch (e: Exception) {
            Log.e("CallControlService", "Error in enableSpeakerphone: ${e.message}")
        }
    }

    override fun onInterrupt() {
        Log.d("CallControlService", "Service interrupted")
    }

    override fun onDestroy() {
        try {
            super.onDestroy()
            Log.d("CallControlService", "Service destroyed")
        } catch (e: Exception) {
            Log.e("CallControlService", "Error in onDestroy: ${e.message}")
        }
    }
} 