package com.example.auto_answer_app

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.util.Log
import android.view.accessibility.AccessibilityWindowInfo
import android.content.Context
import android.media.AudioManager

class AutoAnswerService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private var isProcessingCall = false
    private val TAG = "AutoAnswerService"
    private var lastEventTime = 0L
    private val COOLDOWN_TIME = 500L // 500ms cooldown
    private lateinit var audioManager: AudioManager

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        Log.d(TAG, "AutoAnswerService created")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val currentTime = System.currentTimeMillis()
        
        val eventType = when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> "TYPE_WINDOW_STATE_CHANGED"
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> "TYPE_WINDOW_CONTENT_CHANGED"
            AccessibilityEvent.TYPE_VIEW_CLICKED -> "TYPE_VIEW_CLICKED"
            AccessibilityEvent.TYPE_VIEW_FOCUSED -> "TYPE_VIEW_FOCUSED"
            AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED -> "TYPE_NOTIFICATION_STATE_CHANGED"
            else -> "TYPE_${event.eventType}"
        }
        
        Log.d(TAG, "=== Received Accessibility Event ===")
        Log.d(TAG, "Event Type: $eventType")
        Log.d(TAG, "Package: ${event.packageName}")
        Log.d(TAG, "Event Text: ${event.text}")
        Log.d(TAG, "Class Name: ${event.className}")
        Log.d(TAG, "Content Description: ${event.contentDescription}")
        Log.d(TAG, "Source: ${event.source?.className}")
        Log.d(TAG, "Window ID: ${event.windowId}")
        Log.d(TAG, "================================")

        // Check if this is a call-related event
        val packageName = event.packageName?.toString()
        val isCallEvent = isCallRelatedPackage(packageName) || 
                         event.text?.any { it?.toString()?.contains("answer", ignoreCase = true) == true } == true ||
                         event.contentDescription?.toString()?.contains("answer", ignoreCase = true) == true

        if (isCallEvent) {
            // If we're already processing but it's been a while, reset the flag
            if (isProcessingCall && (currentTime - lastEventTime) > COOLDOWN_TIME) {
                isProcessingCall = false
            }

            if (!isProcessingCall) {
                isProcessingCall = true
                lastEventTime = currentTime
                handleCallScreen()
            } else {
                Log.d(TAG, "Skipping event, still in cooldown (${currentTime - lastEventTime}ms)")
            }
        }
        
        // Also check for in-call UI to enable speaker
        if (isCallRelatedPackage(packageName) && 
            (eventType == "TYPE_WINDOW_STATE_CHANGED" || 
             eventType == "TYPE_WINDOW_CONTENT_CHANGED")) {
            // Get the speaker setting
            val prefs = applicationContext.getSharedPreferences("auto_answer_settings", Context.MODE_PRIVATE)
            val speakerEnabled = prefs.getBoolean("speaker_enabled", false)
            
            if (speakerEnabled) {
                Log.d(TAG, "Call UI detected, checking for speaker button")
                checkAndEnableSpeaker()
            }
        }
    }

    private fun isCallRelatedPackage(packageName: String?): Boolean {
        if (packageName == null) {
            Log.d(TAG, "Package name is null")
            return false
        }
        
        val dialerPackages = listOf(
            "com.google.android.dialer",
            "com.android.dialer",
            "com.android.incallui",
            "com.samsung.android.dialer",
            "com.android.phone",
            "com.android.server.telecom",
            "com.android.systemui", // For notification shade
            // Xiaomi specific packages - enhanced for F22 Pro
            "com.xiaomi.incallui",
            "com.miui.incallui",
            "com.miui.phone",
            "com.miui.voiceassist",
            "com.xiaomi.phone",
            "com.xiaomi.voip"
        )
        
        val isCallRelated = dialerPackages.any { packageName.contains(it) }
        Log.d(TAG, "Package $packageName is call related: $isCallRelated")
        return isCallRelated
    }

    private fun handleCallScreen() {
        Log.d(TAG, "=== Starting Call Screen Handling ===")

        try {
            // First try the active window
            var answered = false
            val activeWindow = rootInActiveWindow
            if (activeWindow != null) {
                Log.d(TAG, "Trying active window first")
                dumpWindowHierarchy(activeWindow)
                answered = tryAnswerMethods(activeWindow)
            }

            // If not answered, try all windows
            if (!answered) {
                val windowList = windows?.toList() ?: emptyList()
                Log.d(TAG, "Number of windows: ${windowList.size}")
                
                windowList.forEachIndexed { index, window ->
                    if (answered) return@forEachIndexed
                    
                    Log.d(TAG, "Checking window $index: ${window.title}")
                    Log.d(TAG, "Window type: ${window.type}")
                    Log.d(TAG, "Window layer: ${window.layer}")
                    
                    val root = window.root
                    if (root != null) {
                        Log.d(TAG, "Window has root node")
                        dumpWindowHierarchy(root)
                        answered = tryAnswerMethods(root)
                    }
                }
            }

            // If still not answered, try gesture
            if (!answered) {
                Log.d(TAG, "No button found, trying gesture")
                performAnswerGesture()
                // Try a second gesture after a short delay
                handler.postDelayed({
                    performAnswerGesture()
                }, 300)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling call screen", e)
        } finally {
            // Reset processing flag after a delay
            handler.postDelayed({
                Log.d(TAG, "Resetting processing flag")
                isProcessingCall = false
            }, COOLDOWN_TIME)
        }
    }

    private fun tryAnswerMethods(root: AccessibilityNodeInfo): Boolean {
        // Try all methods multiple times with different approaches
        for (attempt in 1..2) {
            Log.d(TAG, "Attempt $attempt to find answer button")

            // Method 1: Try to find by specific IDs
            findAnswerButtonById(root)?.let {
                Log.d(TAG, "Found answer button by ID")
                if (clickNode(it)) return true
            }

            // Method 2: Try to find by text
            findAnswerButtonByText(root)?.let {
                Log.d(TAG, "Found answer button by text")
                if (clickNode(it)) return true
            }

            // Method 3: Try to find any clickable button
            findAnyClickableButton(root)?.let {
                Log.d(TAG, "Found clickable button")
                if (clickNode(it)) return true
            }

            // Short delay between attempts
            Thread.sleep(100)
        }

        return false
    }

    private fun findAnswerButtonById(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val buttonIds = listOf(
            "com.google.android.dialer:id/incoming_call_answer",
            "com.google.android.dialer:id/incoming_call_answer_button",
            "com.google.android.dialer:id/answerButton",
            "com.android.dialer:id/incoming_call_answer",
            "com.android.dialer:id/incoming_call_answer_button",
            "com.android.dialer:id/answerButton",
            "android:id/action0",
            "android:id/answer_button",
            "com.google.android.dialer:id/incall_answer_button",
            "com.google.android.dialer:id/primary_action_button",
            "com.google.android.dialer:id/incoming_answer_button",
            // Additional generic IDs
            "answer_button",
            "acceptButton",
            "accept_button",
            "button_answer",
            "button_accept"
        )

        for (id in buttonIds) {
            try {
                Log.d(TAG, "Searching for button ID: $id")
                val nodes = root.findAccessibilityNodeInfosByViewId(id)
                Log.d(TAG, "Found ${nodes.size} nodes with ID: $id")
                
                for (node in nodes) {
                    val isClickable = node.isClickable
                    val className = node.className
                    val text = node.text
                    Log.d(TAG, "Node details - Clickable: $isClickable, Class: $className, Text: $text")
                    
                    if (isClickable || (node.parent?.isClickable == true)) {
                        Log.d(TAG, "Found clickable button with ID: $id")
                        return node
                    }
                    node.recycle()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error finding button by ID: $id", e)
            }
        }
        return null
    }

    private fun findAnswerButtonByText(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val answerKeywords = listOf(
            "answer",
            "accept",
            "Answer call",
            "Accept call",
            "ANSWER",
            "ACCEPT",
            "Swipe up to answer",
            "Slide to answer",
            "Swipe to answer"
        )

        try {
            val queue = mutableListOf<AccessibilityNodeInfo>()
            queue.add(root)

            while (queue.isNotEmpty()) {
                val node = queue.removeAt(0)
                
                val nodeText = node.text?.toString()?.lowercase() ?: ""
                val nodeDesc = node.contentDescription?.toString()?.lowercase() ?: ""
                val className = node.className?.toString()?.lowercase() ?: ""
                
                Log.d(TAG, "Checking node - Text: '$nodeText', Desc: '$nodeDesc', Class: '$className'")
                
                if ((node.isClickable || node.parent?.isClickable == true) && 
                    (answerKeywords.any { nodeText.contains(it.lowercase()) || nodeDesc.contains(it.lowercase()) } ||
                     className.contains("button"))) {
                    Log.d(TAG, "Found potential answer button")
                    return node
                }

                for (i in 0 until node.childCount) {
                    node.getChild(i)?.let { queue.add(it) }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error finding button by text", e)
        }
        return null
    }

    private fun findAnyClickableButton(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        try {
            val queue = mutableListOf<AccessibilityNodeInfo>()
            queue.add(root)

            while (queue.isNotEmpty()) {
                val node = queue.removeAt(0)
                
                if (node.isClickable || node.parent?.isClickable == true) {
                    val className = node.className?.toString() ?: ""
                    if (className.contains("Button", ignoreCase = true) ||
                        className.contains("ImageButton", ignoreCase = true) ||
                        className.contains("ImageView", ignoreCase = true)) {
                        Log.d(TAG, "Found clickable element: $className")
                        return node
                    }
                }

                for (i in 0 until node.childCount) {
                    node.getChild(i)?.let { queue.add(it) }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error finding clickable button", e)
        }
        return null
    }

    private fun clickNode(node: AccessibilityNodeInfo): Boolean {
        try {
            // Try clicking the node itself
            if (node.isClickable) {
                val clicked = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                Log.d(TAG, "Direct click performed: $clicked")
                if (clicked) return true
            }

            // Try clicking the parent
            node.parent?.let { parent ->
                if (parent.isClickable) {
                    val parentClicked = parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    Log.d(TAG, "Parent click performed: $parentClicked")
                    if (parentClicked) return true
                }
            }

            // If normal click didn't work, try focus and click
            if (node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)) {
                Thread.sleep(100)
                return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error clicking node", e)
        } finally {
            try {
                node.recycle()
            } catch (e: Exception) {
                Log.e(TAG, "Error recycling node", e)
            }
        }
        return false
    }

    private fun performAnswerGesture() {
        try {
            val displayMetrics = resources.displayMetrics
            val screenHeight = displayMetrics.heightPixels
            val screenWidth = displayMetrics.widthPixels

            // Try both vertical and horizontal swipes
            val gestures = listOf(
                // Vertical swipe up
                createGesture(
                    screenWidth / 2f, screenHeight * 0.8f,
                    screenWidth / 2f, screenHeight * 0.2f
                ),
                // Horizontal swipe right
                createGesture(
                    screenWidth * 0.2f, screenHeight / 2f,
                    screenWidth * 0.8f, screenHeight / 2f
                )
            )

            for (gesture in gestures) {
                dispatchGesture(gesture, object : AccessibilityService.GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription) {
                        Log.d(TAG, "Gesture completed")
                    }
                    override fun onCancelled(gestureDescription: GestureDescription) {
                        Log.d(TAG, "Gesture cancelled")
                    }
                }, null)
                Thread.sleep(200)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error performing gesture", e)
        }
    }

    private fun createGesture(startX: Float, startY: Float, endX: Float, endY: Float): GestureDescription {
        val swipePath = Path()
        swipePath.moveTo(startX, startY)
        swipePath.lineTo(endX, endY)
        return GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(swipePath, 0, 300))
            .build()
    }

    private fun dumpWindowHierarchy(node: AccessibilityNodeInfo, level: Int = 0) {
        try {
            val indent = "  ".repeat(level)
            val className = node.className?.toString() ?: "null"
            val text = node.text?.toString() ?: "null"
            val desc = node.contentDescription?.toString() ?: "null"
            val id = node.viewIdResourceName ?: "null"
            val clickable = node.isClickable
            val enabled = node.isEnabled
            val focused = node.isFocused
            
            Log.d(TAG, "$indent=== Node Info ===")
            Log.d(TAG, "$indent Class: $className")
            Log.d(TAG, "$indent Text: $text")
            Log.d(TAG, "$indent Description: $desc")
            Log.d(TAG, "$indent ID: $id")
            Log.d(TAG, "$indent Clickable: $clickable")
            Log.d(TAG, "$indent Enabled: $enabled")
            Log.d(TAG, "$indent Focused: $focused")
            Log.d(TAG, "$indent===============")
            
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { child ->
                    dumpWindowHierarchy(child, level + 1)
                    child.recycle()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error dumping hierarchy at level $level", e)
        }
    }

    private fun findNodeByText(root: AccessibilityNodeInfo, texts: List<String>): AccessibilityNodeInfo? {
        for (text in texts) {
            root.findAccessibilityNodeInfosByText(text).firstOrNull()?.let { return it }
        }
        return null
    }

    private fun checkAndEnableSpeaker() {
        try {
            Log.d(TAG, "Running checkAndEnableSpeaker()")
            
            // 1. Try enabling speaker using AudioManager - with multiple attempts
            enableSpeakerphone()
            
            // 2. Try to find and click the speaker button in UI
            val root = rootInActiveWindow
            if (root != null) {
                // Look for speaker button by ID - added more Xiaomi/MIUI specific IDs
                val speakerButtonIds = listOf(
                    "com.android.dialer:id/speaker_button",
                    "com.android.incallui:id/speaker_button",
                    "com.google.android.dialer:id/speaker_button",
                    "com.xiaomi.incallui:id/speaker_button",
                    "com.miui.incallui:id/speaker_button",
                    "com.miui.phone:id/speaker_button",
                    "com.xiaomi.phone:id/speaker_button", 
                    "com.xiaomi.incallui:id/ivSpeaker",
                    "com.miui.incallui:id/ivSpeaker",
                    "speaker_button",
                    "btnSpeaker",
                    "button_speakerphone",
                    "ivSpeaker",    // Common Xiaomi ID
                    "speakerphone", // Possible ID on some Xiaomi phones
                    "audio_button", // Some MIUI versions use this
                    "audioButton"
                )
                
                var speakerButton = speakerButtonIds.firstNotNullOfOrNull { id ->
                    try {
                        val nodes = root.findAccessibilityNodeInfosByViewId(id)
                        Log.d(TAG, "Searching for $id: found ${nodes.size} nodes")
                        nodes.firstOrNull()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error searching for ID $id: ${e.message}")
                        null
                    }
                }
                
                // If no button found by ID, try to find by text or content description
                if (speakerButton == null) {
                    Log.d(TAG, "No speaker button found by ID, searching by text")
                    speakerButton = findNodeByText(root, listOf(
                        "Speaker", "SPEAKER", "Speakerphone", "SPEAKERPHONE", 
                        "Speaker on", "Turn on speaker", "Enable speaker",
                        "擴音", "扬声器", "扬聲器", "喇叭", "免提", "开启扬声器" // Chinese for Xiaomi/MIUI
                    ))
                }
                
                // Try to find by content description
                if (speakerButton == null) {
                    Log.d(TAG, "Searching for speaker button by accessibility tree")
                    speakerButton = findSpeakerButtonByAccessibilityTree(root)
                }
                
                if (speakerButton != null) {
                    if (speakerButton.isClickable) {
                        Log.d(TAG, "Found speaker button, clicking it")
                        speakerButton.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    } else if (speakerButton.parent?.isClickable == true) {
                        Log.d(TAG, "Speaker button not clickable, trying parent")
                        speakerButton.parent?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    } else {
                        Log.d(TAG, "Speaker button and parent not clickable")
                    }
                } else {
                    Log.d(TAG, "Speaker button not found in UI")
                }
                
                root.recycle()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error enabling speaker: ${e.message}", e)
        }
    }
    
    private fun findSpeakerButtonByAccessibilityTree(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        try {
            val queue = mutableListOf<AccessibilityNodeInfo>()
            queue.add(root)
            
            while (queue.isNotEmpty()) {
                val node = queue.removeAt(0)
                
                // Check if this could be a speaker button based on various clues
                val className = node.className?.toString() ?: ""
                val contentDesc = node.contentDescription?.toString()?.lowercase() ?: ""
                val text = node.text?.toString()?.lowercase() ?: ""
                
                // These keywords might indicate a speaker button
                val speakerKeywords = listOf("speaker", "audio", "sound", "扬声器", "擴音", "喇叭", "免提")
                
                // Check if this might be a speaker button
                val isSpeakerRelated = speakerKeywords.any { keyword -> 
                    contentDesc.contains(keyword) || text.contains(keyword) 
                }
                
                // If it's likely a speaker button and is clickable
                if (isSpeakerRelated && (node.isClickable || node.parent?.isClickable == true)) {
                    Log.d(TAG, "Found potential speaker button by tree search: $className, text: $text, desc: $contentDesc")
                    return node
                }
                
                // Check children
                for (i in 0 until node.childCount) {
                    node.getChild(i)?.let { queue.add(it) }
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in findSpeakerButtonByAccessibilityTree: ${e.message}")
        }
        return null
    }
    
    private fun enableSpeakerphone() {
        try {
            // Try multiple approaches with different delays - more aggressive for Xiaomi devices
            val delayTimes = listOf(0L, 500L, 1000L, 1500L, 2000L, 3000L)
            
            for ((index, delay) in delayTimes.withIndex()) {
                handler.postDelayed({
                    try {
                        Log.d(TAG, "Attempting to enable speaker (attempt ${index + 1})")
                        
                        // Standard approach
                        audioManager.mode = AudioManager.MODE_IN_CALL
                        audioManager.isSpeakerphoneOn = true
                        
                        // Alternative approach for some devices
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                            try {
                                audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION)
                                audioManager.isSpeakerphoneOn = true
                            } catch (e: Exception) {
                                Log.e(TAG, "Error setting MODE_IN_COMMUNICATION: ${e.message}")
                            }
                        }
                        
                        // Xiaomi specific approach using reflection - enhanced for F22 Pro
                        try {
                            val audioSystem = Class.forName("android.media.AudioSystem")
                            val setForceUse = audioSystem.getMethod("setForceUse", Int::class.java, Int::class.java)
                            
                            // Constants from AudioSystem
                            val FOR_COMMUNICATION = 0
                            val FORCE_SPEAKER = 1
                            
                            setForceUse.invoke(null, FOR_COMMUNICATION, FORCE_SPEAKER)
                            Log.d(TAG, "Applied Xiaomi-specific speaker method")
                            
                            // Try another method specific to MIUI
                            try {
                                // Another set of constants that might work on some Xiaomi devices
                                val FOR_MEDIA = 1
                                setForceUse.invoke(null, FOR_MEDIA, FORCE_SPEAKER)
                                Log.d(TAG, "Applied secondary Xiaomi-specific speaker method")
                            } catch (e: Exception) {
                                Log.d(TAG, "Secondary method failed: ${e.message}")
                            }
                        } catch (e: Exception) {
                            Log.d(TAG, "Alternative speaker method not available: ${e.message}")
                        }
                        
                        Log.d(TAG, "Speaker enabled (attempt ${index + 1})")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to enable speaker on attempt ${index + 1}: ${e.message}")
                    }
                }, delay)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in enableSpeakerphone: ${e.message}")
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "Service interrupted")
    }

    override fun onServiceConnected() {
        Log.d(TAG, "Service connected")
    }
} 