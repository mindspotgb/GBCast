package com.flycast.emulator.shell

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.view.InputDevice
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.flycast.emulator.shell.ui.GBCastRoot
import com.flycast.emulator.shell.ui.DreamcastControls
import gb.frame.ControlsCapture
import gb.frame.HomeKeys
import gb.frame.HomeTheme
import gb.frame.PlayTime
import gb.frame.setControllerContent
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.fillMaxSize
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LibraryActivity : ComponentActivity() {
    private var ready by mutableStateOf(false)
    private var startupError by mutableStateOf<Throwable?>(null)
    private var lastGamepadBAt = 0L
    private var lastGamepadBDevice = -1
    private var pairedBackDevice = -1
    private var pairedBackDownTime = -1L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        lifecycleScope.launch {
            val error = withContext(Dispatchers.IO) {
                runCatching {
                    DreamcastControls.initialize(this@LibraryActivity)
                    GBCastBridge.start(this@LibraryActivity)
                }.exceptionOrNull()
            }
            startupError = error
            ready = error == null
            if (error != null) Log.e("GBCastStartup", "Failed to initialize GBCast launcher", error)
        }
        setControllerContent {
            val error = startupError
            if (error != null) {
                androidx.compose.foundation.layout.Column {
                    androidx.compose.material3.Text(error.message ?: "Could not open data folder")
                    gb.frame.DataFolderControls(GBCastBridge.dataFolder)
                }
            } else if (ready) {
                GBCastRoot(onQuit = ::quit)
            } else {
                com.flycast.emulator.shell.ui.GBCastTheme { gb.frame.AppStartScreen(app.gbcast.emulator.R.drawable.ic_launcher_foreground) }
            }
        }
        hideSystemBars()
        // A game requested by a frontend or `am start` (gb.frame.LaunchIntent); started by GBCastRoot once the app is ready
        if (savedInstanceState == null) gb.frame.LaunchIntent.handle(this, intent)
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        gb.frame.LaunchIntent.handle(this, intent)
    }

    override fun onResume() {
        super.onResume()
        PlayTime.update(null, false)
        hideSystemBars()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    private fun quit() {
        finishAndRemoveTask()
    }

    @SuppressLint("RestrictedApi") // ComponentActivity's public override contract requires delegating unhandled keys to super.
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (!ready) return super.dispatchKeyEvent(event)
        if (event.keyCode == KeyEvent.KEYCODE_BUTTON_B && event.action == KeyEvent.ACTION_DOWN) {
            lastGamepadBAt = event.eventTime
            lastGamepadBDevice = event.deviceId
        }
        val captureActive = ControlsCapture.active(DreamcastControls)
        if (event.keyCode == KeyEvent.KEYCODE_BACK && !captureActive) {
            val fromController = event.source and (InputDevice.SOURCE_GAMEPAD or InputDevice.SOURCE_DPAD) != 0
            if (fromController && event.action == KeyEvent.ACTION_DOWN &&
                event.deviceId == lastGamepadBDevice &&
                event.eventTime >= lastGamepadBAt &&
                event.eventTime - lastGamepadBAt <= 150L
            ) {
                pairedBackDevice = event.deviceId
                pairedBackDownTime = event.downTime
                return true
            }
            if (event.action == KeyEvent.ACTION_UP && event.deviceId == pairedBackDevice && event.downTime == pairedBackDownTime) {
                pairedBackDevice = -1
                pairedBackDownTime = -1L
                return true
            }
            return backKey(event)
        }
        if (event.keyCode == KeyEvent.KEYCODE_BUTTON_B && !captureActive) return backKey(event)
        // Keep the same dispatch order as GBsat: library shortcuts run only outside remapping, then
        // the shared capture helper gets the event before any app-specific handling.
        if (HomeKeys.dispatch(event, enabled = !captureActive)) return true
        if (ControlsCapture.dispatch(event, DreamcastControls)) return true
        val mapped = when (event.keyCode) {
            KeyEvent.KEYCODE_BUTTON_A -> KeyEvent.KEYCODE_DPAD_CENTER
            else -> null
        }
        if (mapped != null) {
            return super.dispatchKeyEvent(
                KeyEvent(event.downTime, event.eventTime, event.action, mapped, event.repeatCount, event.metaState, event.deviceId, event.scanCode, event.flags, event.source),
            )
        }
        return super.dispatchKeyEvent(event)
    }

    private fun backKey(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) onBackPressedDispatcher.onBackPressed()
        return true
    }

    private val dpad = setOf(
        KeyEvent.KEYCODE_DPAD_UP,
        KeyEvent.KEYCODE_DPAD_DOWN,
        KeyEvent.KEYCODE_DPAD_LEFT,
        KeyEvent.KEYCODE_DPAD_RIGHT,
        KeyEvent.KEYCODE_DPAD_CENTER,
    )
}

