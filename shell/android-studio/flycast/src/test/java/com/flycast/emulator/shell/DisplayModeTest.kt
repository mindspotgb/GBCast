package com.flycast.emulator.shell

import org.junit.Assert.*
import org.junit.Test

class DisplayModeTest {
    @Test fun switchingEveryPairClearsIncompatibleFlagsAndRoundTrips() {
        val values = mutableMapOf<String, Boolean>()
        val setter: (String, String, Boolean) -> Unit = { _, key, value -> values[key] = value }
        val getter: (String, String) -> Boolean = { _, key -> values[key] == true }
        for (from in DisplayMode.entries) for (to in DisplayMode.entries) {
            from.apply(setter)
            to.apply(setter)
            assertEquals(to, DisplayMode.current(getter))
            assertEquals(to.wide, values["rend.WideScreen"])
            assertEquals(false, values["rend.SuperWideScreen"])
            assertEquals(to.fullscreen, values["rend.StretchToWindow"])
        }
    }

    @Test fun superWidescreenIsAvailableInSixteenNineAndFullscreenOnly() {
        val values = mutableMapOf<String, Boolean>()
        val setter: (String, String, Boolean) -> Unit = { _, key, value -> values[key] = value }
        assertFalse(DisplayMode.Original.supportsSuperWidescreen)
        DisplayMode.Original.apply(setter, superWidescreen = true)
        assertFalse(values["rend.WideScreen"] == true)
        assertFalse(values["rend.SuperWideScreen"] == true)

        for (mode in listOf(DisplayMode.Wide, DisplayMode.Fullscreen)) {
            assertTrue(mode.supportsSuperWidescreen)
            mode.apply(setter, superWidescreen = true)
            assertTrue(values["rend.WideScreen"] == true)
            assertTrue(values["rend.SuperWideScreen"] == true)
            assertEquals(mode.fullscreen, values["rend.StretchToWindow"])
        }
    }

    @Test fun legacyOrphanSuperWideFlagKeepsOriginalAspect() {
        assertEquals(DisplayMode.Original, DisplayMode.current { _, key -> key == "rend.SuperWideScreen" })
    }

}
