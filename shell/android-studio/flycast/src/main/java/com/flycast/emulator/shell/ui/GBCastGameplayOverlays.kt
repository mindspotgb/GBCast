package com.flycast.emulator.shell.ui

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.flycast.emulator.emu.JNIdc
import gb.frame.PerfOptions
import gb.frame.PerfOverlay
import gb.frame.GameplayOverlayChrome
import kotlinx.coroutines.delay

object GBCastOverlayOptions : gb.frame.MonitoringSettings {
    private lateinit var preferences: android.content.SharedPreferences
    override var options by mutableStateOf(PerfOptions())
        private set
    override var showFfButton by mutableStateOf(false)
        private set
    fun initialize(context: Context) {
        preferences = context.getSharedPreferences("gbcast-overlays", Context.MODE_PRIVATE)
        showFfButton = preferences.getBoolean("fastForwardButton", false)
        options = PerfOptions(JNIdc.configGetBool("config", "rend.ShowFPS"),
            preferences.getBoolean("speed", false), preferences.getBoolean("cpu", false), preferences.getBoolean("gpu", false),
            preferences.getBoolean("renderer", false), preferences.getBoolean("resolution", false), preferences.getBoolean("frameTime", false))
    }
    override fun update(options: PerfOptions, showFfButton: Boolean) {
        JNIdc.configSetBool("config", "rend.ShowFPS", options.fps)
        this.options = options
        this.showFfButton = showFfButton
        preferences.edit().putBoolean("speed", options.speed).putBoolean("cpu", options.cpu)
            .putBoolean("gpu", options.gpu).putBoolean("renderer", options.renderer)
            .putBoolean("resolution", options.resolution).putBoolean("frameTime", options.frameTime)
            .putBoolean("fastForwardButton", showFfButton).apply()
    }
}

@Composable
fun GBCastGameplayOverlays(onMenu: () -> Unit) {
    var stats by remember { mutableStateOf(intArrayOf(0, 640, 480, 60, 0, 0)) }
    var fps by remember { mutableFloatStateOf(0f) }
    var notice by remember { mutableStateOf<String?>(null) }
    var noticeAt by remember { mutableLongStateOf(0) }
    var lastCount by remember { mutableIntStateOf(-1) }
    var lastTime by remember { mutableLongStateOf(System.nanoTime()) }
    LaunchedEffect(Unit) {
        while (true) {
            val now = System.nanoTime()
            val next = JNIdc.overlayStats()
            if (next.size >= 6 && JNIdc.guiState() != 0) {
                // Paused: keep the last reading on screen and leave the paused time out of the next one
                lastCount = next[0]; lastTime = now; stats = next
            } else if (next.size >= 6 && now - lastTime >= 900_000_000) {
                val count = next[0]
                if (lastCount >= 0 && count >= lastCount) fps = (count - lastCount) * 1_000_000_000f / (now - lastTime)
                lastCount = count; lastTime = now; stats = next
            }
            JNIdc.pollOverlayMessage()?.let { notice = it; noticeAt = now }
            if (notice != null && now - noticeAt > 3_000_000_000) notice = null
            delay(250)
        }
    }
    Box(Modifier.fillMaxSize()) {
        // The kit's in-game chrome, placed the same way in every app (gb.frame.GameplayOverlayChrome)
        GameplayOverlayChrome(
            onMenu = onMenu,
            showFastForwardButton = GBCastOverlayOptions.showFfButton, fastForward = DreamcastControls.fastForwardActive,
            onFastForward = { DreamcastControls.setFastForward(!DreamcastControls.fastForwardActive) },
            message = notice,
            perf = { m ->
                PerfOverlay(GBCastOverlayOptions.options, "GBCast", fps, stats.getOrElse(3) { 60 }.toFloat().coerceAtLeast(1f),
                    when (stats.getOrElse(4) { 0 }) { 4 -> "Vulkan"; 5 -> "Vulkan OIT"; 3 -> "OpenGL OIT"; else -> "OpenGL ES" },
                    stats.getOrElse(1) { 640 }, stats.getOrElse(2) { 480 }, if (fps > 0f) 1000f / fps else 0f,
                    gpuName = { JNIdc.overlayGpuName().orEmpty() }, modifier = m)
            },
        )
    }
}
