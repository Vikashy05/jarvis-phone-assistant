package dev.local.jarvis

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.PixelFormat
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.util.concurrent.Executors

class PhoneAgent : AccessibilityService() {
    companion object {
        internal var connected: PhoneAgent? = null
            private set
        internal const val ENABLED = "agent_enabled"
        internal var lastResult = ""
            private set
        internal var onFinish: ((String) -> Unit)? = null
        private val inference = Executors.newSingleThreadExecutor()
    }

    private val handler = Handler(Looper.getMainLooper())
    private val planner by lazy { LocalPlanner(applicationContext) }
    private val wm by lazy { getSystemService(WindowManager::class.java) }
    private val audio by lazy { getSystemService(AudioManager::class.java) }
    private val history = mutableListOf<String>()
    private var apps = emptyMap<String, List<ComponentName>>()
    private var generation = 0L
    private var goal = ""
    private var panel: LinearLayout? = null
    private lateinit var message: TextView
    private lateinit var approve: Button
    private var previous: Pair<String, AgentAction>? = null

    override fun onServiceConnected() { connected = this }

    internal fun begin(input: String, fromTile: Boolean): String {
        if (!getSharedPreferences(PREFERENCES, MODE_PRIVATE).getBoolean(ENABLED, false)) return "Enable agent mode first."
        if (goal.isNotEmpty()) return "A goal is already running. Use the floating Stop button."
        if (input.isBlank() || input.length > 220) return "Use a goal between 1 and 220 characters."
        if (!unlocked()) return "Unlock your phone first."
        return try {
            goal = input.trim()
            generation++
            previous = null
            history.clear()
            apps = packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
                PackageManager.ResolveInfoFlags.of(0))
                .filter { it.activityInfo.packageName != packageName }
                .groupBy({ it.loadLabel(packageManager).toString().trim() },
                    { ComponentName(it.activityInfo.packageName, it.activityInfo.name) })
            openPanel()
            val token = generation
            handler.postDelayed({ if (token == generation) finish("Time limit reached. Review the phone before retrying.") }, 180_000)
            handler.postDelayed({ step(token) }, if (fromTile) 1000 else 150)
            "Agent started. Use the floating panel to follow or stop it."
        } catch (failure: Exception) {
            finish("Unable to start the agent panel.")
            lastResult
        }
    }

    private fun unlocked() = !getSystemService(KeyguardManager::class.java).isKeyguardLocked &&
        getSystemService(PowerManager::class.java).isInteractive

    private fun screen(): AgentScreen {
        check(unlocked()) { "Phone locked. Goal stopped." }
        val root = rootInActiveWindow ?: error("Android has no accessible foreground window. Retry from the target app.")
        return AgentScreen.read(root, packageName)
    }

    private fun step(token: Long) {
        if (token != generation) return
        if (history.size >= 8) { finish("Eight-step limit reached. Review the result."); return }
        try {
            val view = screen()
            val title = apps.entries.firstOrNull { entry -> entry.value.any { it.packageName == view.pkg } }?.key
                ?: if (view.pkg == packageName) "Jarvis" else view.pkg
            val state = buildString {
                append("Current app $title (${view.pkg}); media muted=${audio.isStreamMute(AudioManager.STREAM_MUSIC)}\n")
                view.controls.forEachIndexed { index, control -> appendLine(control.summary(index + 1)) }
            }
            val request = goal
            val names = apps.keys.sortedWith(compareByDescending<String> { request.contains(it, true) }.thenBy { it })
                .take(35).joinToString(", ").take(450)
            val steps = history.takeLast(4).joinToString(" | ").takeLast(350)
            message.text = "Step ${history.size + 1} · Planning on phone…"
            inference.execute {
                val result = runCatching { planner.next(request, state, names, steps) }
                handler.post {
                    if (token == generation) result.fold(
                        { output -> propose(token, view, output) },
                        { failure -> finish("Local model stopped. " + (failure.message ?: "Check available memory.").take(140)) }
                    )
                }
            }
        } catch (failure: Exception) { finish(failure.message ?: "Could not inspect this screen.") }
    }

    private fun propose(token: Long, seen: AgentScreen, output: String) {
        val action = AgentAction.parse(output)
        if (action == null) { finish("Model returned an unsupported step. Try a shorter goal."); return }
        if (action.verb == "DONE") {
            finish(if (history.isEmpty()) "Model reported no action needed." else "Model marked the goal complete after ${history.size} step(s).")
            return
        }
        if (action.verb == "STOP") { finish("Model could not find a supported next action."); return }
        val control = action.index?.let { seen.controls.getOrNull(it - 1) }
        if (action.index != null && control == null) { finish("Model selected a missing control."); return }
        if (control != null && action.verb in setOf("TAP", "TYPE")) {
            val effect = if (action.verb == "TAP") "Tap “${control.label}”" else "Replace “${control.label}” with “${action.argument}”"
            message.text = "$effect\n${seen.pkg}"
            approve.visibility = Button.VISIBLE
            approve.setOnClickListener {
                approve.visibility = Button.GONE
                approve.setOnClickListener(null)
                execute(token, seen, action, control)
            }
        } else execute(token, seen, action, control)
    }

    private fun execute(token: Long, seen: AgentScreen, action: AgentAction, original: ScreenControl?) {
        if (token != generation) return
        try {
            val fresh = screen()
            check(fresh.pkg == seen.pkg && fresh.window == seen.window) { "The foreground app changed. Goal stopped." }
            val target = original?.let { old ->
                fresh.controls.singleOrNull { it.sameTarget(old) } ?: error("The control changed. Goal stopped.")
            }
            val signature = fresh.pkg + fresh.controls.joinToString { it.label + it.bounds.toShortString() + it.checked }
            check(previous != (signature to action)) { "Repeated step on the same screen. Goal stopped." }
            previous = signature to action
            val ok = when (action.verb) {
                "MUTE", "UNMUTE" -> {
                    val mute = action.verb == "MUTE"
                    audio.adjustStreamVolume(AudioManager.STREAM_MUSIC,
                        if (mute) AudioManager.ADJUST_MUTE else AudioManager.ADJUST_UNMUTE, 0)
                    audio.isStreamMute(AudioManager.STREAM_MUSIC) == mute
                }
                "OPEN" -> {
                    val matches = apps.entries.filter { it.key.equals(action.argument, true) }.flatMap { it.value }.distinct()
                    check(matches.size == 1) { "App name is missing or ambiguous: ${action.argument}" }
                    startActivity(Intent.makeMainActivity(matches.single()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    true
                }
                "SEARCH" -> {
                    val intent = Commands.intent(this, Command.Search(action.argument)) ?: error("YouTube is unavailable.")
                    startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    true
                }
                "BACK" -> performGlobalAction(GLOBAL_ACTION_BACK)
                "HOME" -> performGlobalAction(GLOBAL_ACTION_HOME)
                "SCROLL" -> target?.scroll == true && target.node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                "TAP" -> target?.click == true && target.node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                "TYPE" -> target?.edit == true && target.node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,
                    Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, action.argument) })
                else -> false
            }
            check(ok) { "Android declined ${action.verb.lowercase()}. Goal stopped." }
            val description = listOf(action.verb, target?.label.orEmpty(), action.argument).filter { it.isNotEmpty() }.joinToString(" ")
            history.add(description)
            message.text = "$description · Checking result…"
            handler.postDelayed({ step(token) }, 900)
        } catch (failure: Exception) { finish(failure.message ?: "Action unavailable.") }
    }

    private fun openPanel() {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(12, 8, 12, 8)
            setBackgroundColor(Color.rgb(25, 50, 44))
        }
        message = TextView(this).apply { setTextColor(Color.WHITE); text = "Preparing local agent…" }
        row.addView(message)
        val buttons = LinearLayout(this)
        approve = Button(this).apply { text = "Allow step"; visibility = Button.GONE }
        buttons.addView(approve)
        buttons.addView(Button(this).apply { text = "Stop"; setOnClickListener { finish("Stopped by you.") } })
        row.addView(buttons)
        val params = WindowManager.LayoutParams((260 * resources.displayMetrics.density).toInt(), -2,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT).apply { gravity = Gravity.TOP or Gravity.END }
        wm.addView(row, params)
        panel = row
    }

    internal fun finish(reason: String) {
        if (goal.isEmpty()) return
        generation++
        handler.removeCallbacksAndMessages(null)
        goal = ""
        previous = null
        history.clear()
        apps = emptyMap()
        panel?.let { wm.removeViewImmediate(it) }
        panel = null
        if (::approve.isInitialized) approve.setOnClickListener(null)
        inference.execute { planner.close() }
        lastResult = reason
        onFinish?.invoke(reason)
        Toast.makeText(this, reason, Toast.LENGTH_LONG).show()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (goal.isNotEmpty() && !unlocked()) finish("Phone locked. Goal stopped.")
    }
    override fun onInterrupt() { finish("Android interrupted the agent.") }
    override fun onDestroy() {
        finish("Agent access disconnected.")
        if (connected === this) connected = null
        super.onDestroy()
    }
}
