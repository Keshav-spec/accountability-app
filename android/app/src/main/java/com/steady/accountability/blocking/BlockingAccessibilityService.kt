package com.steady.accountability.blocking

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.steady.accountability.data.DatabaseProvider
import com.steady.accountability.data.LOCAL_OWNER_ID
import com.steady.accountability.data.TaskEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.ZoneId

class BlockingAccessibilityService : AccessibilityService() {
    private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var overlay: FrameLayout? = null
    private var overlayVisible = false
    private var lastPackageSeen: String? = null
    private lateinit var windowManager: WindowManager

    private val controlReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_DISMISS_OVERLAY -> removeOverlay()
                ACTION_DISABLE_BLOCKING -> {
                    removeOverlay()
                    disableSelf()
                }
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        connected = true
        windowManager = getSystemService(WindowManager::class.java)
        ContextCompat.registerReceiver(this, controlReceiver, IntentFilter().apply {
            addAction(ACTION_DISMISS_OVERLAY)
            addAction(ACTION_DISABLE_BLOCKING)
        }, ContextCompat.RECEIVER_NOT_EXPORTED)
        serviceInfo = serviceInfo.apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            feedbackType = android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 150
            flags = 0
        }
        if (!BlockingPreferences.read(this).enabled) {
            disableSelf()
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        // Read only the transient foreground package name. Never inspect source, text, nodes, or content descriptions.
        val foregroundPackage = event.packageName?.toString() ?: return
        val config = BlockingPreferences.read(this)
        if (!config.enabled) {
            removeOverlay()
            disableSelf()
            return
        }
        if (foregroundPackage == packageName) {
            // Always clear the overlay when Steady itself comes forward, so recovery controls stay reachable.
            removeOverlay()
            return
        }
        if (foregroundPackage == lastPackageSeen) return
        lastPackageSeen = foregroundPackage

        if (foregroundPackage !in config.blockedPackages || foregroundPackage in config.exceptionPackages || foregroundPackage in config.essentialPackages) {
            removeOverlay()
            return
        }
        mainScope.launch(Dispatchers.IO) {
            val current = BlockingPreferences.read(this@BlockingAccessibilityService)
            val tasks: List<TaskEntity> = if (current.ruleType == BlockingRuleType.PRIORITY_TASKS) {
                val activeOwner = getSharedPreferences("steady-sync", MODE_PRIVATE).getString("active_owner", LOCAL_OWNER_ID) ?: LOCAL_OWNER_ID
                runCatching { DatabaseProvider.get(this@BlockingAccessibilityService).taskDao().allForSync(activeOwner) }.getOrDefault(emptyList())
            } else emptyList()
            val decision = BlockingRules.evaluate(current, foregroundPackage, tasks, LocalDateTime.now(), packageName)
            launch(Dispatchers.Main.immediate) {
                if (decision == BlockingDecision.BLOCK && BlockingPreferences.read(this@BlockingAccessibilityService).enabled) {
                    showOverlay(current)
                } else removeOverlay()
            }
        }
    }

    override fun onInterrupt() {
        removeOverlay()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        connected = false
        removeOverlay()
        runCatching { unregisterReceiver(controlReceiver) }
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        connected = false
        removeOverlay()
        mainScope.cancel()
        runCatching { unregisterReceiver(controlReceiver) }
        super.onDestroy()
    }

    private fun showOverlay(config: BlockingConfig) {
        if (overlayVisible) return
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(28), dp(34), dp(28), dp(34))
            background = GradientDrawable().apply {
                setColor(Color.rgb(250, 249, 245))
                cornerRadius = dp(30).toFloat()
            }
        }
        panel.addView(TextView(this).apply {
            text = "A gentle pause"
            textSize = 28f
            setTextColor(Color.rgb(13, 44, 34))
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }, matchWidth())
        val reasonText = when (config.ruleType) {
            BlockingRuleType.PRIORITY_TASKS -> "This app will be available when your chosen tasks are completed or skipped."
            BlockingRuleType.BREAK_TIME -> "This app is paused until ${config.breakTime}. You can take a break or use an emergency bypass."
        }
        panel.addView(TextView(this).apply {
            text = "$reasonText\n\nThis is an accountability aid. You can pause it, bypass it, or turn it off at any time."
            textSize = 16f
            setTextColor(Color.rgb(65, 72, 69))
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, dp(18))
        }, matchWidth())
        addAction(panel, "Pause for 15 minutes") {
            BlockingPreferences.save(this, config.copy(pauseUntilMillis = BlockingRules.pauseFor(System.currentTimeMillis(), 15)))
            removeOverlay()
        }
        addAction(panel, "Take a break · ${config.breakDurationMinutes} minutes") {
            BlockingPreferences.save(this, config.copy(pauseUntilMillis = BlockingRules.pauseFor(System.currentTimeMillis(), config.breakDurationMinutes)))
            removeOverlay()
        }
        addAction(panel, "Emergency bypass · until tomorrow") {
            BlockingPreferences.save(this, config.copy(bypassUntilMillis = BlockingRules.bypassUntilTomorrow(LocalDateTime.now(), ZoneId.systemDefault())))
            removeOverlay()
        }
        addAction(panel, "Turn blocking off") {
            BlockingPreferences.save(this, config.copy(enabled = false))
            removeOverlay()
            disableSelf()
        }
        val scrim = FrameLayout(this).apply {
            setBackgroundColor(Color.argb(220, 18, 31, 26))
            addView(panel, FrameLayout.LayoutParams(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT, Gravity.CENTER).apply {
                leftMargin = dp(20)
                rightMargin = dp(20)
            })
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.TOP or Gravity.START }
        try {
            windowManager.addView(scrim, params)
            overlay = scrim
            overlayVisible = true
        } catch (_: RuntimeException) {
            overlay = null
            overlayVisible = false
        }
    }

    private fun addAction(parent: LinearLayout, label: String, action: () -> Unit) {
        parent.addView(Button(this).apply {
            text = label
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor(Color.rgb(36, 66, 55))
                cornerRadius = dp(18).toFloat()
            }
            setOnClickListener { action() }
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(52)).apply { topMargin = dp(5) })
    }

    private fun removeOverlay() {
        overlay?.let { view -> runCatching { windowManager.removeView(view) } }
        overlay = null
        overlayVisible = false
        lastPackageSeen = null
    }

    private fun matchWidth() = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        const val ACTION_DISMISS_OVERLAY = "com.steady.accountability.blocking.DISMISS_OVERLAY"
        const val ACTION_DISABLE_BLOCKING = "com.steady.accountability.blocking.DISABLE"
        @Volatile var connected: Boolean = false
            private set

        fun accessibilityEnabled(context: Context): Boolean {
            val expected = ComponentName(context, BlockingAccessibilityService::class.java).flattenToString()
            val enabled = android.provider.Settings.Secure.getString(context.contentResolver, android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
            return android.provider.Settings.Secure.getInt(context.contentResolver, android.provider.Settings.Secure.ACCESSIBILITY_ENABLED, 0) == 1 &&
                enabled.split(':').any { it.equals(expected, ignoreCase = true) }
        }

        fun dismissOverlay(context: Context) {
            context.sendBroadcast(Intent(context, BlockingAccessibilityService::class.java).setAction(ACTION_DISMISS_OVERLAY))
        }

        fun disable(context: Context) {
            context.sendBroadcast(Intent(context, BlockingAccessibilityService::class.java).setAction(ACTION_DISABLE_BLOCKING))
        }
    }
}
