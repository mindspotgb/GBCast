package com.flycast.emulator.shell

/** Base display choices; Super widescreen is a separate toggle while a widescreen mode is active. */
enum class DisplayMode(val label: String, val wide: Boolean, val fullscreen: Boolean) {
    Original("4:3", false, false),
    Wide("16:9", true, false),
    Fullscreen("Fullscreen", false, true);

    fun apply(setBool: (String, String, Boolean) -> Unit, superWidescreen: Boolean = false) {
        setBool("config", "rend.WideScreen", wide)
        setBool("config", "rend.SuperWideScreen", wide && superWidescreen)
        setBool("config", "rend.StretchToWindow", fullscreen)
    }

    companion object {
        fun current(getBool: (String, String) -> Boolean): DisplayMode = when {
            getBool("config", "rend.StretchToWindow") -> Fullscreen
            !getBool("config", "rend.WideScreen") -> Original
            else -> Wide
        }
    }
}
