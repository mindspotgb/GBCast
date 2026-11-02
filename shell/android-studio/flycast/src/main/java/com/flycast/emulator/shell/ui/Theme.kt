package com.flycast.emulator.shell.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import gb.frame.EmuPalette
import gb.frame.EmuTheme

/**
 * Dreamcast Graphite identity. Use deep warm graphite surfaces with a restrained orange undertone, a saturated,
 * lower-luminance orange for focus, a subdued gray for outlines, and light text on focused controls.
 *
 * Shared GBFrame components consume this palette through EmuTheme.
 */
private val GraphiteOrange = Color(0xFFD0581E)
private val CursorGray = Color(0xFF656D77)
private val GraphitePalette = EmuPalette(
    accent = GraphiteOrange,
    onAccent = Color(0xFFE8E9EC),
    highlight = CursorGray,
    background = listOf(Color(0xFF211B18), Color(0xFF2A211D), Color(0xFF1F1916)),
    panel = Color(0xFF27201C).copy(alpha = 0.98f),
    card = Color(0xFF2B231F).copy(alpha = 0.98f),
    border = Color(0xFF666D77).copy(alpha = 0.42f),
    dim = Color(0xFFB7BDC6),
    warn = Color(0xFFFF9270),
    surface = Color(0xFF261F1B),
)

@Composable
fun GBCastTheme(content: @Composable () -> Unit) =
    EmuTheme(GraphitePalette, content)
