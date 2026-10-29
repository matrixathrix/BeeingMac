package com.example.beeing

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent

/**
 * Performs GLOBAL_ACTION_LOCK_SCREEN — the same action the power button
 * triggers. Unlike DevicePolicyManager.lockNow() (device admin), this never
 * touches keyguard/biometric policy, so Face/Fingerprint unlock still works
 * on the very next unlock attempt.
 */
class LockAccessibilityService : AccessibilityService() {

    private val lockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        val filter = IntentFilter(ACTION_LOCK_PHONE)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(lockReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(lockReceiver, filter)
        }
    }

    override fun onUnbind(intent: Intent?): Boolean {
        runCatching { unregisterReceiver(lockReceiver) }
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}
}

/** True once the user has enabled this service under Settings > Accessibility. */
fun isLockAccessibilityServiceEnabled(context: Context): Boolean {
    val expected = "${context.packageName}/${LockAccessibilityService::class.java.name}"
    val enabled = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ) ?: return false
    return enabled.split(':').any { it.equals(expected, ignoreCase = true) }
}
