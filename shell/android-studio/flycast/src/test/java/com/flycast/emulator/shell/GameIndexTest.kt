package com.flycast.emulator.shell

import org.junit.Assert.assertEquals
import org.junit.Test

class GameIndexTest {
    @Test
    fun hidesTracksNextToADescriptor() {
        val paths = listOf(
            "saf:/Sonic/Sonic.gdi",
            "saf:/Sonic/track01.bin",
            "saf:/Sonic/track02.raw",
            "saf:/Naomi/mvsc2.zip",
            "saf:/Loose/game.bin",
            "saf:/Jet Set Radio/Jet Set Radio.chd",
        )
        assertEquals(
            listOf("saf:/Sonic/Sonic.gdi", "saf:/Naomi/mvsc2.zip", "saf:/Loose/game.bin", "saf:/Jet Set Radio/Jet Set Radio.chd"),
            GameIndex.filterTracks(paths),
        )
    }
}
