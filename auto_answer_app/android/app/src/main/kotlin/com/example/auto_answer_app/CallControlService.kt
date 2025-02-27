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
        super.onCreate()
        sharedPreferences = getSharedPreferences("auto_answer_settings", Context.MODE_PRIVATE)
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        Log.d("CallControlService", "Service created with settings: autoAnswer=${sharedPreferences.getBoolean("auto_answer_enabled", false)}, speaker=${sharedPreferences.getBoolean("speaker_enabled", false)}")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
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
                "answer_button"
            )

            var answerButton = answerButtonIds.firstNotNullOfOrNull { id ->
                rootNode.findAccessibilityNodeInfosByViewId(id).firstOrNull()
            }

            // If no button found by ID, try to find by text
            if (answerButton == null) {
                Log.d("CallControlService", "Answer button not found by ID, trying text search")
                answerButton = findNodeByText(rootNode, listOf("Answer", "ANSWER", "Accept", "ACCEPT"))
            }

            if (answerButton != null && answerButton.isClickable) {
                Log.d("CallControlService", "Found answer button, attempting to click")
                val clicked = answerButton.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                Log.d("CallControlService", "Answer button click result: $clicked")

                if (clicked && speakerEnabled) {
                    // Wait a moment for the call to be answered before enabling speaker
                    Handler(Looper.getMainLooper()).postDelayed({
                        enableSpeakerphone()
                    }, 1000)
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
            root.findAccessibilityNodeInfosByText(text).firstOrNull()?.let { return it }
        }
        return null
    }

    private fun enableSpeakerphone() {
        if (sharedPreferences.getBoolean("speaker_enabled", false)) {
            handler.postDelayed({
                try {
                    audioManager.mode = AudioManager.MODE_IN_CALL
                    audioManager.isSpeakerphoneOn = true
                    Log.d("CallControlService", "Speaker enabled")
                } catch (e: Exception) {
                    Log.e("CallControlService", "Failed to enable speaker: ${e.message}")
                }
            }, 1000)
        }
    }

    private fun performAnswerGesture() {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.N) return

        val display = windows.firstOrNull { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
        val rect = Rect()
        display?.getBoundsInScreen(rect)

        if (rect.isEmpty) {
            Log.e("CallControlService", "Could not get screen bounds")
            return
        }

        // Create a swipe up gesture from bottom to middle
        val path = Path()
        path.moveTo(rect.centerX().toFloat(), rect.bottom.toFloat() - 100)
        path.lineTo(rect.centerX().toFloat(), rect.centerY().toFloat())

        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 400))
            .build()

        dispatchGesture(gesture, object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription) {
                Log.d("CallControlService", "Answer gesture completed")
                enableSpeakerphone()
            }

            override fun onCancelled(gestureDescription: GestureDescription) {
                Log.e("CallControlService", "Answer gesture cancelled")
            }
        }, null)
    }

    private fun findAnswerButton(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val answerKeywords = listOf(
            "answer",
            "accept",
            "swipe to answer",
            "slide to answer",
            "answer call",
            "接聽",  // Chinese
            "応答",  // Japanese
            "수락",  // Korean
            "contestar", // Spanish
            "répondre"  // French
        )

        // First try to find by viewId
        val answerButtonIds = listOf(
            "com.android.dialer:id/incoming_call_answer_button",
            "com.android.incallui:id/answer_button",
            "com.google.android.dialer:id/answer_button"
        )

        for (id in answerButtonIds) {
            val nodes = node.findAccessibilityNodeInfosByViewId(id)
            if (nodes.isNotEmpty()) {
                return nodes[0]
            }
        }

        // Then try to find by text or content description
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(node)

        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            
            // Check text
            current.text?.toString()?.lowercase()?.let { text ->
                if (answerKeywords.any { text.contains(it.lowercase()) }) {
                    return current
                }
            }

            // Check content description
            current.contentDescription?.toString()?.lowercase()?.let { desc ->
                if (answerKeywords.any { desc.contains(it.lowercase()) }) {
                    return current
                }
            }

            // Add children to queue
            for (i in 0 until current.childCount) {
                current.getChild(i)?.let { queue.add(it) }
            }
        }

        return null
    }

    override fun onInterrupt() {
        Log.d("CallControlService", "Service interrupted")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d("CallControlService", "Service connected")
        
        try {
            val info = serviceInfo
            info.apply {
                eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or 
                            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                            AccessibilityEvent.TYPE_VIEW_CLICKED or
                            AccessibilityEvent.TYPE_VIEW_FOCUSED
                feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
                notificationTimeout = 100
                flags = AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS or
                        AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                        AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE or
                        AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
                packageNames = arrayOf(
                    "com.android.dialer",
                    "com.android.incallui",
                    "com.google.android.dialer",
                    "com.samsung.android.dialer"
                )
            }
            serviceInfo = info
            Log.d("CallControlService", "Service configuration updated successfully")
        } catch (e: Exception) {
            Log.e("CallControlService", "Error configuring service: ${e.message}")
            e.printStackTrace()
        }
    }
} 