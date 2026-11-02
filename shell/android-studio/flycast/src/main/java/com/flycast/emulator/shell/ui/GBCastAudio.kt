package com.flycast.emulator.shell.ui

import androidx.compose.runtime.mutableIntStateOf
import com.flycast.emulator.emu.JNIdc

/**
 * The kit's standard audio options (Settings → Audio, quick menu → Audio) over Flycast's config: `aica.Volume` and
 * GBCast's `aica.FastForwardAudio`. The values are mirrored in Compose state so both pages update at once; the config
 * file is written by each set (JNIdc.configSetInt saves).
 */
object GBCastAudio : gb.frame.AudioSettings {
    private val vol = mutableIntStateOf(-1)
    private val ff = mutableIntStateOf(-1)

    override var volume: Int
        get() = vol.intValue.takeIf { it >= 0 } ?: JNIdc.configGetInt("config", "aica.Volume").also { vol.intValue = it }
        set(v) { vol.intValue = v; JNIdc.configSetInt("config", "aica.Volume", v) }
    override var fastForwardAudio: Int
        get() = ff.intValue.takeIf { it >= 0 } ?: JNIdc.configGetInt("config", "aica.FastForwardAudio").also { ff.intValue = it }
        set(v) { ff.intValue = v; JNIdc.configSetInt("config", "aica.FastForwardAudio", v) }
}
