package com.example.auto_answer_app

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.util.Log
import android.telecom.TelecomManager
import android.content.Context
import android.os.Build
import android.app.KeyguardManager

class AutoAnswerAccessibilityService : AccessibilityService() {
    private var isProcessingCall = false

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (isProcessingCall) return

        try {
            if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
                event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
                
                val packageName = event.packageName?.toString() ?: ""
                if (!isRelevantPackage(packageName)) return

                Log.d("AutoAnswer", "Processing event from package: $packageName")
                
                // Wake up device if needed
                wakeUpDevice()

                val rootNode = rootInActiveWindow
                if (rootNode == null) {
                    Log.d("AutoAnswer", "Root node is null")
                    return
                }

                isProcessingCall = true
                
                // Try direct telecom answer first for Android 8.0+
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val telecomManager = getSystemService(Context.TELECOM_SERVICE) as TelecomManager
                    try {
                        telecomManager.acceptRingingCall()
                        Log.d("AutoAnswer", "Call answered via TelecomManager")
                        return
                    } catch (e: Exception) {
                        Log.e("AutoAnswer", "TelecomManager failed: ${e.message}")
                        // Continue with accessibility method if telecom fails
                    }
                }

                // Fallback to accessibility click method
                findAndClickAnswerButton(rootNode)
            }
        } finally {
            isProcessingCall = false
        }
    }

    private fun isRelevantPackage(packageName: String): Boolean {
        return packageName.contains("dialer") || 
               packageName.contains("incallui") || 
               packageName.contains("phone") ||
               packageName.contains("huawei") ||
               packageName.contains("xiaomi") ||
               packageName.contains("samsung") ||
               packageName.contains("oneplus") ||
               packageName.contains("oppo") ||
               packageName.contains("vivo")
    }

    private fun wakeUpDevice() {
        val km = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        val keyguardLock = km.newKeyguardLock("AutoAnswer")
        keyguardLock.disableKeyguard()
    }

    private fun findAndClickAnswerButton(root: AccessibilityNodeInfo) {
        val answerTexts = listOf("answer", "accept", "接聽", "接听", "応答", "수락")
        
        try {
            // First try finding by text
            for (text in answerTexts) {
                val nodes = root.findAccessibilityNodeInfosByText(text)
                for (node in nodes) {
                    if (isClickableAnswerButton(node)) {
                        Log.d("AutoAnswer", "Found answer button with text: $text")
                        node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        return
                    }
                }
            }

            // If not found by text, try traversing all nodes
            traverseNodesForAnswerButton(root)
        } catch (e: Exception) {
            Log.e("AutoAnswer", "Error finding answer button: ${e.message}")
        }
    }

    private fun isClickableAnswerButton(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false
        
        return (node.isClickable && 
                (node.className?.contains("Button") == true || 
                 node.className?.contains("ImageView") == true ||
                 node.className?.contains("View") == true))
    }

    private fun traverseNodesForAnswerButton(root: AccessibilityNodeInfo?) {
        if (root == null) return

        if (isClickableAnswerButton(root)) {
            val text = root.text?.toString()?.lowercase() ?: ""
            val desc = root.contentDescription?.toString()?.lowercase() ?: ""
            
            if (text.contains("answer") || desc.contains("answer") ||
                text.contains("accept") || desc.contains("accept")) {
                root.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                return
            }
        }

        for (i in 0 until root.childCount) {
            traverseNodesForAnswerButton(root.getChild(i))
        }
    }

    override fun onInterrupt() {
        isProcessingCall = false
    }

    override fun onServiceConnected() {
        Log.d("AutoAnswer", "Accessibility Service Connected")
        isProcessingCall = false
    }
} 