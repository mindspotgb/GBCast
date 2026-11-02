package com.flycast.emulator.shell

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.flycast.emulator.Emulator
import com.flycast.emulator.emu.JNIdc
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AutoSkipSettingsTest {
    @Test fun skipOptionsBindToNativeSettingsIndependentlyOfFastDiscAndManualFastForward() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Emulator
        assertNull(app.ensureCore())
        JNIdc.initAutoSkip()
        val keys = listOf("GBFrame.SkipLoadingScreens", "GBFrame.SkipTransitions", "GBFrame.SkipSaveWaits")
        val previous = keys.map { JNIdc.configGetBool("config", it) }
        val fastDisc = JNIdc.configGetBool("config", "FastGDRomLoad")
        try {
            JNIdc.setFastForwardMode(true)
            keys.forEach { key ->
                assertTrue(JNIdc.configSetBool("config", key, true))
                assertTrue(JNIdc.configGetBool("config", key))
                assertEquals(fastDisc, JNIdc.configGetBool("config", "FastGDRomLoad"))
                assertEquals(1, JNIdc.overlayStats()[5])
            }
            assertEquals(0L, JNIdc.autoSkipStats()[0]) // a library/settings screen must never auto-fast-forward
            keys.forEach { assertTrue(JNIdc.configSetBool("config", it, false)) }
            assertEquals(1, JNIdc.overlayStats()[5]) // automatic controls never clear the user's manual fast-forward
        } finally {
            keys.zip(previous).forEach { (key, value) -> JNIdc.configSetBool("config", key, value) }
            JNIdc.setFastForwardMode(false)
        }
    }
}
