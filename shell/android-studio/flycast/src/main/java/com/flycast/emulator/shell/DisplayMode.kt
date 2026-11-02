package com.flycast.emulator.shell

/** Base display choices; Extended screen is a separate toggle while a widescreen mode is active. */
enum class DisplayMode(val label: String, val wide: Boolean, val fullscreen: Boolean) {
    Original("4:3", false, false),
    Wide("16:9", true, false),
    Fullscreen("Fullscreen", false, true);

    val supportsSuperWidescreen: Boolean get() = wide || fullscreen

    fun apply(setBool: (String, String, Boolean) -> Unit, superWidescreen: Boolean = false) {
        val superWideEnabled = supportsSuperWidescreen && superWidescreen
        // Flycast requires Widescreen for SuperWideScreen to expand the rendered scene.
        setBool("config", "rend.WideScreen", wide || (fullscreen && superWideEnabled))
        setBool("config", "rend.SuperWideScreen", superWideEnabled)
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
