package dev.local.jarvis

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.View
import android.view.accessibility.AccessibilityManager
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {
    private val preferences by lazy { getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE) }
    private lateinit var voice: VoiceInput
    private lateinit var command: EditText
    private lateinit var status: TextView
    private lateinit var speak: Button
    private lateinit var helperStatus: TextView
    private lateinit var helperButton: Button
    private lateinit var agentStatus: TextView
    private lateinit var agentButton: Button
    private lateinit var body: LinearLayout
    private var foreground = false
    private var voiceAfterPermission = false
    private val ink = Color.rgb(24, 45, 41)
    private val accent = Color.rgb(0, 107, 87)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        voice = VoiceInput(this, ::report) { phrase ->
            command.setText(phrase)
            runCommand()
        }
        body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(28))
            isFocusableInTouchMode = true
        }
        setContentView(ScrollView(this).apply { addView(body) })
        label(body, "Jarvis", 36, true)
        label(body, "On-device phone assistant", 16)
        val agentCard = card()
        label(agentCard, "Phone agent", 22, true)
        label(agentCard, "Qwen2.5 · Try “mute the media, then open YouTube.”", 16)
        agentStatus = label(agentCard, "", 15)
        agentButton = button(agentCard, "Enable agent mode") { changeAgent() }
        button(agentCard, "Manage phone access") { openAccessibility() }
        val voiceCard = card()
        label(voiceCard, "Speak a command", 22, true)
        label(voiceCard, "Tap Speak, then say “Jarvis, open YouTube and search for Python tutorials.”", 16)
        label(voiceCard, "The microphone listens for one command. English speech is processed on this device.", 14)
        speak = button(voiceCard, "Speak", true) { toggleVoice() }
        status = label(voiceCard, "Ready", 15).apply {
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
            setTextColor(accent)
        }

        val typeCard = card()
        label(typeCard, "Type a command", 20, true)
        label(typeCard, "Open YouTube, Chrome, Maps, WhatsApp, Camera or Settings.", 15)
        command = EditText(this).apply {
            hint = "Open Settings"
            setSingleLine()
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            imeOptions = EditorInfo.IME_ACTION_GO
            setOnEditorActionListener { _, action, _ ->
                if (action == EditorInfo.IME_ACTION_GO) { runCommand(); true } else false
            }
            typeCard.addView(this)
        }
        button(typeCard, "Run command") { runCommand() }

        val helperCard = card()
        label(helperCard, "YouTube ad helper", 20, true)
        helperStatus = label(helperCard, "", 15)
        helperButton = button(helperCard, "Enable helper") { changeHelper() }
        button(helperCard, "Accessibility settings") { openAccessibility() }
        refreshHelper()
        refreshAgent()
    }

    private fun dp(amount: Int) = (amount * resources.displayMetrics.density + 0.5f).toInt()

    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(12), dp(16), dp(14))
        background = GradientDrawable().apply {
            setColor(Color.WHITE)
            setStroke(dp(1), Color.rgb(211, 222, 215))
            cornerRadius = dp(18).toFloat()
        }
        body.addView(this, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(16) })
    }

    private fun label(parent: LinearLayout, value: String, size: Int, bold: Boolean = false) =
        TextView(this).apply {
            text = value
            textSize = size.toFloat()
            setTextColor(ink)
            if (bold) setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            setPadding(0, dp(4), 0, dp(5))
            parent.addView(this)
        }

    private fun button(parent: LinearLayout, value: String, primary: Boolean = false, click: () -> Unit) =
        Button(this).apply {
            text = value
            isAllCaps = false
            minHeight = dp(48)
            setTextColor(if (primary) Color.WHITE else ink)
            backgroundTintList = android.content.res.ColorStateList.valueOf(
                if (primary) accent else Color.rgb(228, 238, 232)
            )
            setOnClickListener { click() }
            parent.addView(this)
        }

    private fun report(message: String) {
        status.text = message
        speak.text = if (voice.active) "Cancel listening" else "Speak"
    }

    private fun toggleVoice() {
        if (voice.active) {
            voice.cancel()
            report("Listening cancelled.")
        } else if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            voice.start()
        } else {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 101)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != 101) return
        if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            if (foreground) voice.start() else voiceAfterPermission = true
        } else {
            report("Microphone access is off. Type below or allow it in Settings → Apps → Jarvis → Permissions.")
        }
    }

    private fun runCommand() {
        voice.cancel()
        if (preferences.getBoolean(PhoneAgent.ENABLED, false)) {
            val agent = PhoneAgent.connected
            if (agent == null) {
                report("Enable Jarvis phone agent in Android Accessibility settings, then return.")
            } else {
                val fromTile = intent.getBooleanExtra("from_tile", false)
                val result = agent.begin(command.text.toString(), fromTile)
                report(result)
                if (fromTile && result.startsWith("Agent started")) moveTaskToBack(true)
            }
            return
        }
        val parsed = Commands.parse(command.text.toString())
        if (parsed == null) {
            report("For a free-form goal, enable Phone agent. Quick commands include “open Settings”.")
            return
        }
        try {
            val target = Commands.intent(this, parsed) ?: throw ActivityNotFoundException()
            startActivity(target)
            report(if (parsed is Command.Search) "Opened YouTube search." else "Opened the requested app.")
        } catch (failure: ActivityNotFoundException) {
            report("This app or action is unavailable. Check that the target app is installed and enabled.")
        } catch (failure: SecurityException) {
            report("Android blocked this launch. Check the target app in Settings.")
        }
    }

    private fun changeHelper() {
        if (preferences.getBoolean(HELPER_ALLOWED, false)) {
            preferences.edit().putBoolean(HELPER_ALLOWED, false).apply()
            refreshHelper()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Allow the YouTube helper?")
            .setMessage(getString(R.string.helper_description) +
                "\n\nAndroid grants access in Accessibility settings. Other Jarvis commands work with this off.")
            .setNegativeButton("Keep off", null)
            .setPositiveButton("Allow helper") { _, _ ->
                preferences.edit().putBoolean(HELPER_ALLOWED, true).apply()
                refreshHelper()
                if (!systemAccess()) openAccessibility()
            }.show()
    }

    private fun refreshAgent() {
        val enabled = preferences.getBoolean(PhoneAgent.ENABLED, false)
        agentButton.text = if (enabled) "Turn off agent mode" else "Enable agent mode"
        agentStatus.text = when {
            !enabled -> "Off · Quick commands available."
            PhoneAgent.connected == null -> "Waiting for Android access. Enable Jarvis phone agent under Accessibility."
            else -> PhoneAgent.lastResult.ifBlank { "Ready · Speak or type a goal below." }
        }
    }

    private fun changeAgent() {
        if (preferences.getBoolean(PhoneAgent.ENABLED, false)) {
            preferences.edit().putBoolean(PhoneAgent.ENABLED, false).apply()
            PhoneAgent.connected?.finish("Agent mode turned off.")
            refreshAgent()
            return
        }
        AlertDialog.Builder(this).setTitle("Enable phone agent?")
            .setMessage(getString(R.string.agent_disclosure))
            .setNegativeButton("Keep off", null)
            .setPositiveButton("Enable agent") { _, _ ->
                preferences.edit().putBoolean(PhoneAgent.ENABLED, true).apply()
                refreshAgent()
                if (PhoneAgent.connected == null) openAccessibility()
            }.show()
    }

    override fun onNewIntent(newIntent: Intent) {
        super.onNewIntent(newIntent)
        setIntent(newIntent)
    }

    private fun systemAccess(): Boolean {
        val manager = getSystemService(AccessibilityManager::class.java)
        val component = ComponentName(this, SkipService::class.java)
        return manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { ComponentName.unflattenFromString(it.id) == component }
    }

    private fun refreshHelper() {
        val allowed = preferences.getBoolean(HELPER_ALLOWED, false)
        val access = systemAccess()
        helperStatus.text = when {
            allowed && access -> "On · Looking for a visible YouTube ad-skip button."
            allowed -> "Waiting for system access. Open Accessibility settings and enable Jarvis YouTube helper."
            access -> "Paused · System access is still enabled. Revoke it in Accessibility settings at any time."
            else -> "Off · Optionally tap a visible Skip Ad button in YouTube."
        }
        helperButton.text = if (allowed) "Pause helper" else "Enable helper"
    }

    private fun openAccessibility() {
        try {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        } catch (failure: ActivityNotFoundException) {
            helperStatus.text = "Open phone Settings and search for Accessibility."
        }
    }

    override fun onResume() {
        super.onResume()
        foreground = true
        refreshHelper()
        refreshAgent()
        PhoneAgent.onFinish = { message -> report(message); refreshAgent() }
        if (voiceAfterPermission) {
            voiceAfterPermission = false
            voice.start()
        }
    }

    override fun onPause() {
        foreground = false
        PhoneAgent.onFinish = null
        voiceAfterPermission = false
        if (voice.active) {
            voice.cancel()
            report("Listening stopped when Jarvis left the foreground.")
        }
        super.onPause()
    }

    override fun onDestroy() {
        voice.cancel()
        super.onDestroy()
    }
}
