package com.calmapps.calmtunes

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class CalmTunesAccessibilityService : AccessibilityService() {

    private val TAG = "CalmTunesAccess"
    private var lastClickTime: Long = 0
    private val CLICK_COOLDOWN_MS = 3000L

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.packageName == null) return

        val packageName = event.packageName.toString()
        if (!packageName.contains("fmradio", ignoreCase = true) &&
            !packageName.contains("radio", ignoreCase = true)) {
            return
        }

        if (System.currentTimeMillis() - lastClickTime < CLICK_COOLDOWN_MS) {
            return
        }

        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {

            val rootNode = rootInActiveWindow ?: return
            checkForPlayButton(rootNode)
        }
    }

    private fun checkForPlayButton(root: AccessibilityNodeInfo) {
        // New Check: Look for Next/Previous buttons.
        // If these exist and are active, we assume the radio is in a playback state
        // (or at least doesn't need 'Play' pressed), so we bail out.
        if (areNavigationButtonsActive(root)) {
            Log.d(TAG, "Navigation buttons (Next/Previous) found and active. Skipping play click.")

            // Reset cooldown so we don't hammer the check immediately
            lastClickTime = System.currentTimeMillis()

            // Return to main app since our job is effectively done
            Handler(Looper.getMainLooper()).postDelayed({
                returnToApp()
            }, 500)
            return
        }

        val keywords = listOf("Play", "Start", "Power", "Turn On", "FM Radio")

        for (keyword in keywords) {
            val nodes = root.findAccessibilityNodeInfosByText(keyword)
            if (nodes.isNullOrEmpty()) continue

            for (node in nodes) {
                if (node.isClickable && node.isEnabled) {
                    val text = (node.text ?: node.contentDescription ?: "").toString()

                    Log.d(TAG, "Found target button: '$text'. Clicking!")

                    val clicked = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    if (clicked) {
                        lastClickTime = System.currentTimeMillis()

                        Handler(Looper.getMainLooper()).postDelayed({
                            returnToApp()
                        }, 500)

                        return
                    }
                }
            }
        }
    }

    private fun areNavigationButtonsActive(root: AccessibilityNodeInfo): Boolean {
        // Common keywords for radio navigation
        val navKeywords = listOf("Next", "Previous", "Forward", "Back")

        for (keyword in navKeywords) {
            val nodes = root.findAccessibilityNodeInfosByText(keyword)
            if (!nodes.isNullOrEmpty()) {
                for (node in nodes) {
                    // We only count them if they are actually enabled/clickable
                    // which suggests they are active controls, not just disabled UI elements.
                    if (node.isEnabled) {
                        Log.d(TAG, "Found active navigation button: '${node.text ?: node.contentDescription}'")
                        return true
                    }
                }
            }
        }
        return false
    }

    private fun returnToApp() {
        try {
            val intent = packageManager.getLaunchIntentForPackage("com.calmapps.calmtunes")
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                intent.putExtra("FROM_RADIO_TUNER", true)
                startActivity(intent)
                Log.d(TAG, "Returning to CalmTunes...")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to return to app", e)
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "Service Interrupted")
    }
}