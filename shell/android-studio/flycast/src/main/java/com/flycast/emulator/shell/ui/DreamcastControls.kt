package com.flycast.emulator.shell.ui

import android.content.Context
import android.content.SharedPreferences
import android.preference.PreferenceManager
import android.view.KeyEvent
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.flycast.emulator.Emulator
import com.flycast.emulator.emu.JNIdc
import com.flycast.emulator.shell.GbcastGameUi
import gb.frame.ControlButton
import gb.frame.ControlsHost
import gb.frame.HotkeyAction
import gb.frame.Hotkeys
import gb.frame.PadButton
import gb.frame.PadLayout
import gb.frame.TouchControlsMode
import gb.frame.padRect
import gb.frame.rememberAppIconPainter
import app.gbcast.emulator.R

/** Dreamcast adapter for GBFrame's shared Controls page. */
object DreamcastControls : ControlsHost {
    private const val PREF_KEYS = "gbcast.controls.keyMap"
    private const val PREF_HOTKEYS = "gbcast.controls.hotkeys"
    private const val PREF_FF_TOGGLE = "gbcast.controls.fastForwardToggle"
    private const val PREF_TOUCH_MODE = "gbcast.controls.touchMode"

    override val console = "Dreamcast"
    override val pad = DreamcastPad

    override val buttons: List<ControlButton> = listOf(
        ControlButton("Up", "D-pad up", icon = Icons.Default.KeyboardArrowUp, color = Color(0xFF343B50)),
        ControlButton("Down", "D-pad down", icon = Icons.Default.KeyboardArrowDown, color = Color(0xFF343B50)),
        ControlButton("Left", "D-pad left", icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft, color = Color(0xFF343B50)),
        ControlButton("Right", "D-pad right", icon = Icons.AutoMirrored.Filled.KeyboardArrowRight, color = Color(0xFF343B50)),
        ControlButton("A", "A button", badge = "A", color = Color(0xFF3C8D83)),
        ControlButton("B", "B button", badge = "B", color = Color(0xFFBD625A)),
        ControlButton("X", "X button", badge = "X", color = Color(0xFF6385C4)),
        ControlButton("Y", "Y button", badge = "Y", color = Color(0xFFCEB45E)),
        ControlButton("L", "Left trigger", badge = "L", color = Color(0xFF47435F)),
        ControlButton("R", "Right trigger", badge = "R", color = Color(0xFF47435F)),
        ControlButton("Start", "Start button", badge = "START", color = Color(0xFF6E58D8)),
    )

    private val defaultMap = linkedMapOf(
        KeyEvent.KEYCODE_DPAD_UP to "Up",
        KeyEvent.KEYCODE_DPAD_DOWN to "Down",
        KeyEvent.KEYCODE_DPAD_LEFT to "Left",
        KeyEvent.KEYCODE_DPAD_RIGHT to "Right",
        KeyEvent.KEYCODE_BUTTON_A to "A",
        KeyEvent.KEYCODE_BUTTON_B to "B",
        KeyEvent.KEYCODE_BUTTON_X to "X",
        KeyEvent.KEYCODE_BUTTON_Y to "Y",
        KeyEvent.KEYCODE_BUTTON_L1 to "L",
        KeyEvent.KEYCODE_BUTTON_R1 to "R",
        KeyEvent.KEYCODE_BUTTON_START to "Start",
    )

    private lateinit var prefs: SharedPreferences
    private var keyMap by androidx.compose.runtime.mutableStateOf<Map<Int, String>>(defaultMap)
    private var storedHotkeys by androidx.compose.runtime.mutableStateOf<Map<HotkeyAction, Set<Int>>>(emptyMap())
    override var remapTarget by androidx.compose.runtime.mutableStateOf<String?>(null)
    override var hotkeyCapture by androidx.compose.runtime.mutableStateOf<HotkeyAction?>(null)
    override var hotkeyCaptureKeys by androidx.compose.runtime.mutableStateOf<Set<Int>>(emptySet())
    private var ffActive = false
    var fastForwardActive by androidx.compose.runtime.mutableStateOf(false)
        private set
    private var ffToggleState by androidx.compose.runtime.mutableStateOf(false)
    private var touchState by androidx.compose.runtime.mutableStateOf(TouchControlsMode.AUTO)

    override val hotkeys: Map<HotkeyAction, Set<Int>> get() = storedHotkeys

    override var fastForwardToggle: Boolean
        get() = ffToggleState
        set(value) {
            ffToggleState = value
            preferences().edit().putBoolean(PREF_FF_TOGGLE, value).apply()
        }

    override var touchControlsMode: TouchControlsMode
        get() = touchState
        set(value) {
            touchState = value
            preferences().edit().putString(PREF_TOUCH_MODE, value.name).remove("gbcast.controls.showTouch").apply()
        }

    private fun preferences(): SharedPreferences =
        if (::prefs.isInitialized) prefs else error("DreamcastControls has not been initialized")

    fun initialize(context: Context) {
        if (::prefs.isInitialized) return
        prefs = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
        keyMap = readKeyMap()
        storedHotkeys = readHotkeys()
        ffToggleState = prefs.getBoolean(PREF_FF_TOGGLE, false)
        touchState = runCatching {
            TouchControlsMode.valueOf(prefs.getString(PREF_TOUCH_MODE, TouchControlsMode.AUTO.name) ?: TouchControlsMode.AUTO.name)
        }.getOrDefault(TouchControlsMode.AUTO)
    }

    private fun readKeyMap(): Map<Int, String> {
        val saved = preferences().getString(PREF_KEYS, null) ?: return defaultMap
        val validIds = buttons.map { it.id }.toSet()
        val parsed = saved.split(";").mapNotNull { entry ->
            val p = entry.split("=", limit = 2)
            val code = p.getOrNull(0)?.toIntOrNull()
            val id = p.getOrNull(1)
            if (code != null && id != null && id in validIds) code to id else null
        }.toMap()
        return if (parsed.isEmpty()) defaultMap else parsed
    }

    private fun saveKeyMap() {
        preferences().edit().putString(PREF_KEYS, keyMap.entries.joinToString(";") { "${it.key}=${it.value}" }).apply()
    }

    private fun readHotkeys(): Map<HotkeyAction, Set<Int>> {
        val saved = preferences().getString(PREF_HOTKEYS, null) ?: return emptyMap()
        return saved.split(";").mapNotNull { entry ->
            val p = entry.split("=", limit = 2)
            val action = p.getOrNull(0)?.let { name -> HotkeyAction.entries.firstOrNull { it.name == name } }
            val keys = p.getOrNull(1)?.split("+")?.mapNotNull { it.toIntOrNull() }?.toSet()
            if (action != null && !keys.isNullOrEmpty()) action to keys else null
        }.toMap()
    }

    private fun saveHotkeys() {
        preferences().edit().putString(PREF_HOTKEYS, storedHotkeys.entries.joinToString(";") {
            "${it.key.name}=${it.value.joinToString("+")}"
        }).apply()
    }

    override fun keysFor(id: String): List<Int> = keyMap.filterValues { it == id }.keys.toList()

    override fun bindKey(id: String, keyCode: Int) {
        if (buttons.none { it.id == id }) return
        keyMap = keyMap.toMutableMap().also { map ->
            map.entries.removeAll { it.value == id || it.key == keyCode }
            map[keyCode] = id
        }
        saveKeyMap()
    }

    override fun resetKeyMap() {
        keyMap = defaultMap
        saveKeyMap()
    }

    override fun bindHotkey(action: HotkeyAction, keys: Set<Int>) {
        storedHotkeys = storedHotkeys.toMutableMap().also { map ->
            if (keys.isEmpty()) map.remove(action) else map[action] = keys
        }
        saveHotkeys()
    }

    @JvmStatic
    fun mapKeyCode(keyCode: Int): Int {
        val id = keyMap[keyCode] ?: return keyCode
        return when (id) {
            "Up" -> KeyEvent.KEYCODE_DPAD_UP
            "Down" -> KeyEvent.KEYCODE_DPAD_DOWN
            "Left" -> KeyEvent.KEYCODE_DPAD_LEFT
            "Right" -> KeyEvent.KEYCODE_DPAD_RIGHT
            "A" -> KeyEvent.KEYCODE_BUTTON_A
            "B" -> KeyEvent.KEYCODE_BUTTON_B
            "X" -> KeyEvent.KEYCODE_BUTTON_X
            "Y" -> KeyEvent.KEYCODE_BUTTON_Y
            "L" -> KeyEvent.KEYCODE_BUTTON_L2
            "R" -> KeyEvent.KEYCODE_BUTTON_R2
            "Start" -> KeyEvent.KEYCODE_BUTTON_START
            else -> keyCode
        }
    }

    @JvmStatic
    fun configureHotkeys() {
        Hotkeys.configure(bindings = { hotkeys }, run = { action, down ->
            when (action) {
                HotkeyAction.QuickSave -> if (down) JNIdc.saveCurrentSlot()
                HotkeyAction.QuickLoad -> if (down) JNIdc.loadCurrentSlot()
                HotkeyAction.Menu -> if (down) Emulator.getCurrentActivity()?.let(GbcastGameUi::onAndroidBack)
                HotkeyAction.FastForward -> {
                    val enabled = if (fastForwardToggle) {
                        if (down) ffActive = !ffActive
                        ffActive
                    } else down
                    ffActive = enabled
                    fastForwardActive = enabled
                    JNIdc.setFastForwardMode(enabled)
                }
            }
        })
    }

    fun setFastForward(enabled: Boolean) {
        ffActive = enabled
        fastForwardActive = enabled
        JNIdc.setFastForwardMode(enabled)
    }
}

// Positions are fractions of the alpha-trimmed controller artwork (267x298), measured on it; the diagram has the
// artwork's own ratio, so the artwork fills it and the buttons sit on the drawn ones
private val DreamcastPad = PadLayout(listOf(
    PadButton("L", "L", x = .19f, y = .10f, w = .15f, h = .07f, wOfWidth = true, shape = padRect(8), color = Color(0xFF47435F)),
    PadButton("R", "R", x = .81f, y = .10f, w = .15f, h = .07f, wOfWidth = true, shape = padRect(8), color = Color(0xFF47435F)),
    PadButton("Up", icon = Icons.Default.KeyboardArrowUp, x = .190f, y = .478f, w = .058f, h = .058f, shape = padRect(2), color = Color(0xFF343B50)),
    PadButton("Down", icon = Icons.Default.KeyboardArrowDown, x = .190f, y = .582f, w = .058f, h = .058f, shape = padRect(2), color = Color(0xFF343B50)),
    PadButton("Left", icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft, x = .132f, y = .530f, w = .058f, h = .058f, shape = padRect(2), color = Color(0xFF343B50)),
    PadButton("Right", icon = Icons.AutoMirrored.Filled.KeyboardArrowRight, x = .248f, y = .530f, w = .058f, h = .058f, shape = padRect(2), color = Color(0xFF343B50)),
    PadButton("Start", "▶", x = .496f, y = .680f, w = .08f, h = .07f, shape = padRect(5), color = Color(0xFF6E58D8)),
    PadButton("X", "X", x = .729f, y = .405f, w = .08f, h = .08f, color = Color(0xFFCEB45E)),
    PadButton("Y", "Y", x = .815f, y = .334f, w = .08f, h = .08f, color = Color(0xFF3C8D83)),
    PadButton("A", "A", x = .812f, y = .478f, w = .08f, h = .08f, color = Color(0xFFBD625A)),
    PadButton("B", "B", x = .901f, y = .406f, w = .08f, h = .08f, color = Color(0xFF6385C4)),
), aspectRatio = 267f / 298f, artwork = { rememberAppIconPainter(R.drawable.ic_launcher_foreground) })

