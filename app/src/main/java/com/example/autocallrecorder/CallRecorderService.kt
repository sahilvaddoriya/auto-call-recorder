package com.example.autocallrecorder

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.*

class CallRecorderService : AccessibilityService() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var isOffHook = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d("CallRecorderService", "Service Connected")
        
        try {
            // Listen for phone state changes
            val telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            telephonyManager.listen(object : PhoneStateListener() {
                override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                    when (state) {
                        TelephonyManager.CALL_STATE_OFFHOOK -> {
                            Log.d("CallRecorderService", "Phone OFFHOOK")
                            isOffHook = true
                            performRecordingFlow()
                        }
                        TelephonyManager.CALL_STATE_IDLE -> {
                            Log.d("CallRecorderService", "Phone IDLE")
                            isOffHook = false
                        }
                    }
                }
            }, PhoneStateListener.LISTEN_CALL_STATE)
        } catch (e: SecurityException) {
            Log.e("CallRecorderService", "Permission missing for phone state", e)
        } catch (e: Exception) {
             Log.e("CallRecorderService", "Error in onServiceConnected", e)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // We primarily use PhoneStateListener for the trigger, 
        // but we might need events to know when the window content has updated after clicks.
    }

    override fun onInterrupt() {
        Log.d("CallRecorderService", "Service Interrupted")
        serviceScope.cancel()
    }


    private fun performRecordingFlow() {
        serviceScope.launch {
            // Check if automation is enabled
            val prefs = getSharedPreferences("CallRecorderPrefs", Context.MODE_PRIVATE)
            val isEnabled = prefs.getBoolean("automation_enabled", true)
            
            if (!isEnabled) {
               Log.d("CallRecorderService", "Automation disabled via toggle. Skipping.")
               return@launch 
            }

            Log.d("CallRecorderService", "Waiting for call to become ACTIVE (finding timer)...")
            val callStarted = waitForCallActive()
            
            if (!callStarted) {
                Log.d("CallRecorderService", "Call never became active or timed out.")
                return@launch
            }

            Log.d("CallRecorderService", "Call is ACTIVE. Proceeding immediately (0.5s delay)...")
            delay(500L) // Minimal wait to ensure UI stability

            if (!isOffHook) {
                Log.d("CallRecorderService", "Call ended before flow started.")
                return@launch
            }

            Log.d("CallRecorderService", "Searching for Call Assist/Record buttons...")
            // Log.d("CallRecorderService", "--- FULL WINDOW DUMP START ---")
            // Iterate over ALL windows to find the button
            val allWindows = windows
            // Log.d("CallRecorderService", "Total Windows: ${allWindows.size}")
            
            // Search in this window
            var targetNode: AccessibilityNodeInfo? = null

            allWindows.forEachIndexed { index, window ->
                // Log.d("CallRecorderService", "Window $index: Type=${window.type}, Title=${window.title}")
                val root = window.root
                if (root != null) {
                    // explicit text dump for user (Commented out for production cleanup)
                    // Log.d("CallRecorderService", "--- TEXT DUMP WIN $index START ---")
                    // logAllText(root)
                    // Log.d("CallRecorderService", "--- TEXT DUMP WIN $index END ---")

                    // dumpNodeTree(root, 0, "Win $index")
                    
                    // Search in this window if not found yet
                    if (targetNode == null) {
                        var found = findNode(root, "Call Assist")
                        if (found == null) found = findNode(root, "Assist")
                        if (found == null) found = findNode(root, "Record")
                        if (found == null) found = findNode(root, "Call Recording")
                        
                        // New Logic: Find "More" and look above it
                        if (found == null) {
                             val moreNode = findNode(root, "More")
                             if (moreNode != null) {
                                 Log.d("CallRecorderService", "Found 'More' button at ${moreNode.viewIdResourceName}, searching above it...")
                                 found = findNodeAbove(root, moreNode)
                                 if (found != null) {
                                     Log.d("CallRecorderService", "Found candidate above 'More': ${found.text ?: found.contentDescription}")
                                 }
                             }
                        }

                        if (found != null) {
                            targetNode = found
                        }
                    }
                }
            }
            // Log.d("CallRecorderService", "--- FULL WINDOW DUMP END ---")

            targetNode?.let { node ->
                // Log.d("CallRecorderService", "Found target node: '${node.text ?: node.contentDescription}' in window, clicking...")
                val clicked = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                if (!clicked) {
                     node.parent?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                }
                
                // Wait for state change - reduced to 200ms for speed
                delay(200L)
                
                // Re-scan active window for Record confirmation
                 val root = rootInActiveWindow
                 if (root != null) {
                     val recordNode = findRecordButton(root)
                     if (recordNode != null) {
                        // Log.d("CallRecorderService", "Found 'Record' confirmation button: '${recordNode.text ?: recordNode.contentDescription}', clicking...")
                        val recordClicked = recordNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        if (!recordClicked) {
                            Log.d("CallRecorderService", "Click on Record node failed, trying parent...")
                            recordNode.parent?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        }
                     }
                 }
            } ?: run {
                 Log.d("CallRecorderService", "Could not find 'Call Assist', 'Assist', or 'Record' button in any window.")
                 Log.d("CallRecorderService", "Please check the 'Bounds' in the log to identify the button 'above' the More button.")
            }
        }
    }

    private suspend fun waitForCallActive(): Boolean {
        // Try for up to 60 seconds (ringing can take a while)
        val maxRetries = 60
        for (i in 0 until maxRetries) {
            if (!isOffHook) return false
            
            val root = rootInActiveWindow
            if (root != null) {
                if (hasCallTimer(root)) {
                    Log.d("CallRecorderService", "Found Call Timer! Call is Active.")
                    return true
                }
            }
            delay(1000L)
        }
        return false
    }

    private fun hasCallTimer(root: AccessibilityNodeInfo?): Boolean {
        if (root == null) return false
        
        // Regex for 00:00, 12:34, 1:23:45
        val timerRegex = Regex("^\\d{2}:\\d{2}$|^\\d{1,2}:\\d{2}:\\d{2}$")
        
        val text = root.text?.toString()
        if (text != null && timerRegex.matches(text)) {
            Log.d("CallRecorderService", "Detected Timer Text: $text")
            return true
        }
        
        for (i in 0 until root.childCount) {
             if (hasCallTimer(root.getChild(i))) return true
        }
        return false
    }

    private fun findRecordButton(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var node = findNode(root, "Record")
        if (node == null) node = findNode(root, "Call Recording")
        if (node == null) node = findNode(root, "Start recording")
        return node
    }

    private fun findNodeAbove(root: AccessibilityNodeInfo?, referenceNode: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (root == null) return null
        
        val refBounds = android.graphics.Rect()
        referenceNode.getBoundsInScreen(refBounds)
        
        var bestCandidate: AccessibilityNodeInfo? = null
        var minDistance = Int.MAX_VALUE

        // Scan all nodes to find one directly above
        fun scan(node: AccessibilityNodeInfo?) {
            if (node == null) return
            
            val bounds = android.graphics.Rect()
            node.getBoundsInScreen(bounds)
            
            // Criteria: 
            // 1. Is clickable
            // 2. Is ABOVE the reference (bottom < ref.top)
            // 3. Is horizontally aligned (roughly)
            
            if (node.isClickable && bounds.bottom <= refBounds.top) {
                // Check horizontal overlap
                 val horizontalOverlap = kotlin.math.max(0, kotlin.math.min(bounds.right, refBounds.right) - kotlin.math.max(bounds.left, refBounds.left))
                 if (horizontalOverlap > 0) {
                     val distance = refBounds.top - bounds.bottom
                     if (distance < minDistance) {
                         minDistance = distance
                         bestCandidate = node
                     }
                 }
            }
            
            for (i in 0 until node.childCount) {
                scan(node.getChild(i))
            }
        }
        
        scan(root)
        return bestCandidate
    }

    // Improved finder that checks Text AND ContentDescription
    private fun findNode(root: AccessibilityNodeInfo?, text: String): AccessibilityNodeInfo? {
        if (root == null) return null
        
        // Check current node
        val nodeText = root.text?.toString()
        val nodeDesc = root.contentDescription?.toString()
        
        if (nodeText?.contains(text, ignoreCase = true) == true || 
            nodeDesc?.contains(text, ignoreCase = true) == true) {
            return root
        }
        
        // Check children
        for (i in 0 until root.childCount) {
             val found = findNode(root.getChild(i), text)
             if (found != null) return found
        }
        
        return null
    }

    private fun logAllText(node: AccessibilityNodeInfo?) {
        if (node == null) return
        
        val text = node.text?.toString()?.trim()
        val desc = node.contentDescription?.toString()?.trim()
        
        if (!text.isNullOrEmpty()) {
            Log.d("CallRecorderService", "Visible Text: '$text'")
        }
        if (!desc.isNullOrEmpty()) {
             Log.d("CallRecorderService", "Visible Desc: '$desc'")
        }
        
        for (i in 0 until node.childCount) {
            logAllText(node.getChild(i))
        }
    }

    private fun dumpNodeTree(node: AccessibilityNodeInfo?, depth: Int = 0, prefix: String = "") {
        if (node == null) return
        val indent = "  ".repeat(depth)
        val desc = node.contentDescription
        val bounds = android.graphics.Rect()
        node.getBoundsInScreen(bounds)
        
        Log.d("CallRecorderDebug", "$prefix $indent Class: ${node.className}, Text: ${node.text}, Desc: $desc, Bounds: $bounds, Clickable: ${node.isClickable}")
        
        for (i in 0 until node.childCount) {
            dumpNodeTree(node.getChild(i), depth + 1, prefix)
        }
    }
}
