package com.flycast.emulator.shell.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import com.flycast.emulator.periph.InputDeviceManager
import gb.frame.TouchControlsLayoutMetrics
import gb.frame.TouchControlsOverlay
import gb.frame.TouchControlsPlacement
import kotlin.math.hypot
import kotlin.math.min

/** Flycast vgamepad::ControlId values from core/ui/vgamepad.h. Buttons are sent only on press/release edges. */
private object FlycastTouchId {
    const val LEFT = 0
    const val UP = 1
    const val RIGHT = 2
    const val DOWN = 3
    const val X = 4
    const val Y = 5
    const val B = 6
    const val A = 7
    const val START = 8
    const val LEFT_TRIGGER = 9
    const val RIGHT_TRIGGER = 10
}

/** Keeps each simultaneous finger independent and emits only state transitions to Flycast's virtual gamepad. */
private object DreamcastTouchInput {
    private val lock = Any()
    private val held = mutableMapOf<Any, Set<Int>>()

    fun set(owner: Any, controls: Set<Int>) {
        val transitions = synchronized(lock) {
            val before = held.values.flatten().toSet()
            if (controls.isEmpty()) held.remove(owner) else held[owner] = controls
            val after = held.values.flatten().toSet()
            (before - after).map { it to false } + (after - before).map { it to true }
        }
        val input = InputDeviceManager.getInstance()
        transitions.forEach { (id, pressed) -> input.virtualButtonInput(id, pressed) }
    }

    fun joystick(x: Float, y: Float) = InputDeviceManager.getInstance().virtualJoystick(x, y)

    fun releaseAll() {
        val releases = synchronized(lock) {
            val active = held.values.flatten().toSet()
            held.clear()
            active
        }
        val input = InputDeviceManager.getInstance()
        releases.forEach { input.virtualButtonInput(it, false) }
        input.virtualJoystick(0f, 0f)
    }
}

/** Compose touch skin for GBCast. GBFrame chooses the video/control arrangement for the device shape. */
@Composable
fun DreamcastTouchControls(
    videoAspectRatio: Float,
    onMenu: () -> Unit,
    hapticsEnabled: Boolean,
) {
    DisposableEffect(Unit) {
        onDispose { DreamcastTouchInput.releaseAll() }
    }
    TouchControlsOverlay(
        modifier = Modifier.fillMaxSize(),
        referenceWidth = 760.dp,
        referenceHeight = 900.dp,
        videoAspectRatio = videoAspectRatio,
        controlsEnabled = true,
    ) { metrics ->
        when (metrics.plan.placement) {
            TouchControlsPlacement.FULLSCREEN_OVERLAY -> TransparentPad(metrics, onMenu, hapticsEnabled)
            TouchControlsPlacement.PORTRAIT_BOTTOM -> PortraitPad(metrics, onMenu, hapticsEnabled)
            TouchControlsPlacement.LANDSCAPE_SIDES -> LandscapePad(metrics, onMenu, hapticsEnabled)
            TouchControlsPlacement.FULLSCREEN -> Unit
        }
    }
}

@Composable
private fun PortraitPad(metrics: TouchControlsLayoutMetrics, onMenu: () -> Unit, haptics: Boolean) {
    val width = metrics.screenWidth.value
    val height = metrics.screenHeight.value
    val panelTop = metrics.plan.video.top + metrics.plan.video.height
    val panelHeight = (height - panelTop).coerceAtLeast(0f)
    val scale = min(1f, min(width / 390f, panelHeight / 410f)).coerceAtLeast(.60f)
    val buttonSize = 48f * scale
    val stickSize = 104f * scale
    val dpadSize = 104f * scale

    Box(Modifier.fillMaxSize()) {
        ControllerPanel(
            Modifier.offset(y = panelTop.dp).fillMaxWidth().height(panelHeight.dp),
            topRounded = true,
        )
        ShoulderButton("L", FlycastTouchId.LEFT_TRIGGER,
            Modifier.offset(x = (width * .07f).dp, y = (panelTop + 16f * scale).dp).size(width = (104f * scale).dp, height = (34f * scale).dp), haptics)
        ShoulderButton("R", FlycastTouchId.RIGHT_TRIGGER,
            Modifier.offset(x = (width * .93f - 104f * scale).dp, y = (panelTop + 16f * scale).dp).size(width = (104f * scale).dp, height = (34f * scale).dp), haptics)

        AnalogStick(
            Modifier.offset(x = (width * .25f - stickSize / 2).dp, y = (panelTop + panelHeight * .30f - stickSize / 2).dp).size(stickSize.dp),
        )
        DirectionPad(
            Modifier.offset(x = (width * .25f - dpadSize / 2).dp, y = (panelTop + panelHeight * .73f - dpadSize / 2).dp).size(dpadSize.dp), haptics,
        )

        FaceButton("X", FlycastTouchId.X,
            Modifier.offset(x = (width * .68f - buttonSize / 2).dp, y = (panelTop + panelHeight * .50f - buttonSize / 2).dp).size(buttonSize.dp), FaceYellow, haptics)
        FaceButton("Y", FlycastTouchId.Y,
            Modifier.offset(x = (width * .79f - buttonSize / 2).dp, y = (panelTop + panelHeight * .36f - buttonSize / 2).dp).size(buttonSize.dp), FaceGreen, haptics)
        FaceButton("B", FlycastTouchId.B,
            Modifier.offset(x = (width * .88f - buttonSize / 2).dp, y = (panelTop + panelHeight * .51f - buttonSize / 2).dp).size(buttonSize.dp), FaceBlue, haptics)
        FaceButton("A", FlycastTouchId.A,
            Modifier.offset(x = (width * .77f - buttonSize / 2).dp, y = (panelTop + panelHeight * .66f - buttonSize / 2).dp).size(buttonSize.dp), FaceRed, haptics)

        StartButton(
            Modifier.offset(x = (width * .50f - 42f * scale).dp, y = (panelTop + panelHeight - 58f * scale).dp).size(width = (84f * scale).dp, height = (38f * scale).dp), haptics,
        )
        MenuButton(
            Modifier.offset(x = (width * .07f).dp, y = (panelTop + panelHeight - 54f * scale).dp).size((38f * scale).dp), onMenu, haptics,
        )
    }
}

@Composable
private fun LandscapePad(metrics: TouchControlsLayoutMetrics, onMenu: () -> Unit, haptics: Boolean) {
    val width = metrics.screenWidth.value
    val height = metrics.screenHeight.value
    val video = metrics.plan.video
    val leftWidth = video.left
    val rightLeft = video.left + video.width
    val rightWidth = (width - rightLeft).coerceAtLeast(0f)
    val panelWidth = min(leftWidth, rightWidth).coerceAtLeast(0f)
    val scale = min(1f, min(panelWidth / 154f, height / 380f)).coerceAtLeast(.52f)
    val stickSize = 70f * scale
    val dpadSize = 68f * scale
    val buttonSize = 40f * scale

    Box(Modifier.fillMaxSize()) {
        ControllerPanel(Modifier.offset(x = 0.dp, y = 0.dp).width(leftWidth.dp).fillMaxHeight(), topRounded = false)
        ControllerPanel(Modifier.offset(x = rightLeft.dp, y = 0.dp).width(rightWidth.dp).fillMaxHeight(), topRounded = false)

        ShoulderButton("L", FlycastTouchId.LEFT_TRIGGER,
            Modifier.offset(x = (leftWidth * .16f).dp, y = (height * .08f).dp).size(width = (leftWidth * .68f).dp, height = (30f * scale).dp), haptics)
        ShoulderButton("R", FlycastTouchId.RIGHT_TRIGGER,
            Modifier.offset(x = (rightLeft + rightWidth * .16f).dp, y = (height * .08f).dp).size(width = (rightWidth * .68f).dp, height = (30f * scale).dp), haptics)

        AnalogStick(
            Modifier.offset(x = (leftWidth * .50f - stickSize / 2).dp, y = (height * .38f - stickSize / 2).dp).size(stickSize.dp),
        )
        DirectionPad(
            Modifier.offset(x = (leftWidth * .50f - dpadSize / 2).dp, y = (height * .73f - dpadSize / 2).dp).size(dpadSize.dp), haptics,
        )

        FaceButton("X", FlycastTouchId.X,
            Modifier.offset(x = (rightLeft + rightWidth * .28f - buttonSize / 2).dp, y = (height * .48f - buttonSize / 2).dp).size(buttonSize.dp), FaceYellow, haptics)
        FaceButton("Y", FlycastTouchId.Y,
            Modifier.offset(x = (rightLeft + rightWidth * .53f - buttonSize / 2).dp, y = (height * .36f - buttonSize / 2).dp).size(buttonSize.dp), FaceGreen, haptics)
        FaceButton("B", FlycastTouchId.B,
            Modifier.offset(x = (rightLeft + rightWidth * .74f - buttonSize / 2).dp, y = (height * .49f - buttonSize / 2).dp).size(buttonSize.dp), FaceBlue, haptics)
        FaceButton("A", FlycastTouchId.A,
            Modifier.offset(x = (rightLeft + rightWidth * .50f - buttonSize / 2).dp, y = (height * .63f - buttonSize / 2).dp).size(buttonSize.dp), FaceRed, haptics)

        StartButton(
            Modifier.offset(x = (rightLeft + rightWidth * .50f - 34f * scale).dp, y = (height * .86f - 14f * scale).dp).size(width = (68f * scale).dp, height = (28f * scale).dp), haptics,
        )
        MenuButton(
            Modifier.offset(x = (leftWidth * .50f - 16f * scale).dp, y = (height * .90f - 16f * scale).dp).size((32f * scale).dp), onMenu, haptics,
        )
    }
}

@Composable
private fun TransparentPad(metrics: TouchControlsLayoutMetrics, onMenu: () -> Unit, haptics: Boolean) {
    val width = metrics.screenWidth.value
    val height = metrics.screenHeight.value
    val scale = min(1f, min(width / 620f, height / 500f)).coerceAtLeast(.58f)
    val buttonSize = 50f * scale
    val stickSize = 116f * scale
    val dpadSize = 100f * scale
    val panelBottom = height - 14f * scale

    Box(Modifier.fillMaxSize()) {
        AnalogStick(
            Modifier.offset(x = (width * .16f - stickSize / 2).dp, y = (panelBottom - 2.25f * stickSize).dp).size(stickSize.dp),
            transparent = true,
        )
        DirectionPad(
            Modifier.offset(x = (width * .16f - dpadSize / 2).dp, y = (panelBottom - dpadSize - 12f * scale).dp).size(dpadSize.dp), haptics, transparent = true,
        )
        ShoulderButton("L", FlycastTouchId.LEFT_TRIGGER,
            Modifier.offset(x = (width * .03f).dp, y = (height * .16f).dp).size(width = (86f * scale).dp, height = (32f * scale).dp), haptics, transparent = true)
        ShoulderButton("R", FlycastTouchId.RIGHT_TRIGGER,
            Modifier.offset(x = (width * .97f - 86f * scale).dp, y = (height * .16f).dp).size(width = (86f * scale).dp, height = (32f * scale).dp), haptics, transparent = true)
        FaceButton("X", FlycastTouchId.X,
            Modifier.offset(x = (width * .77f - buttonSize / 2).dp, y = (panelBottom - 1.70f * buttonSize).dp).size(buttonSize.dp), FaceYellow, haptics, transparent = true)
        FaceButton("Y", FlycastTouchId.Y,
            Modifier.offset(x = (width * .85f - buttonSize / 2).dp, y = (panelBottom - 2.45f * buttonSize).dp).size(buttonSize.dp), FaceGreen, haptics, transparent = true)
        FaceButton("B", FlycastTouchId.B,
            Modifier.offset(x = (width * .90f - buttonSize / 2).dp, y = (panelBottom - 1.82f * buttonSize).dp).size(buttonSize.dp), FaceBlue, haptics, transparent = true)
        FaceButton("A", FlycastTouchId.A,
            Modifier.offset(x = (width * .83f - buttonSize / 2).dp, y = (panelBottom - .98f * buttonSize).dp).size(buttonSize.dp), FaceRed, haptics, transparent = true)
        StartButton(
            Modifier.offset(x = (width * .50f - 40f * scale).dp, y = (panelBottom - 34f * scale).dp).size(width = (80f * scale).dp, height = (30f * scale).dp), haptics, transparent = true,
        )
        MenuButton(
            Modifier.offset(x = (width * .05f).dp, y = (panelBottom - 34f * scale).dp).size((32f * scale).dp), onMenu, haptics, transparent = true,
        )
    }
}

@Composable
private fun ControllerPanel(modifier: Modifier, topRounded: Boolean) {
    val shape = if (topRounded) RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp) else RoundedCornerShape(12.dp)
    Box(modifier.clip(shape).background(
        Brush.verticalGradient(listOf(Color(0xFFF7F8F9), Color(0xFFE2E5E8), Color(0xFFC8CDD2))),
    ).border(1.dp, Color(0xFF8D949B).copy(alpha = .65f), shape)) {
        Canvas(Modifier.fillMaxSize()) {
            val centerY = size.height * .47f
            drawLine(Color.White.copy(alpha = .62f), Offset(0f, centerY), Offset(size.width, centerY), 1.dp.toPx())
            drawLine(Color(0xFF9CA3AA).copy(alpha = .35f), Offset(0f, centerY + 2.dp.toPx()), Offset(size.width, centerY + 2.dp.toPx()), 1.dp.toPx())
            val groove = Color(0xFF8E969F).copy(alpha = .25f)
            drawCircle(groove, size.minDimension * .16f, Offset(size.width * .25f, size.height * .34f), style = Stroke(2.dp.toPx()))
            drawCircle(Color.White.copy(alpha = .42f), size.minDimension * .16f, Offset(size.width * .25f, size.height * .34f), style = Stroke(1.dp.toPx()))
        }
    }
}

@Composable
private fun FaceButton(
    label: String,
    id: Int,
    modifier: Modifier,
    tint: Color,
    haptics: Boolean,
    transparent: Boolean = false,
) {
    val pressed = remember { mutableStateOf(false) }
    val owner = remember { Any() }
    val feedback = LocalHapticFeedback.current
    PressRegion(modifier, owner, id, haptics, pressed, feedback) {
        val alpha = if (transparent) .56f else 1f
        val top = tint.copy(alpha = alpha).lerp(Color.White, if (pressed.value) .05f else .27f)
        val bottom = tint.copy(alpha = alpha).lerp(Color.Black, if (pressed.value) .24f else .06f)
        Box(
            Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(top, bottom)), CircleShape)
                .border(1.5.dp, Color.White.copy(alpha = if (transparent) .68f else .92f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(label, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
        }
    }
}

@Composable
private fun ShoulderButton(label: String, id: Int, modifier: Modifier, haptics: Boolean, transparent: Boolean = false) {
    val pressed = remember { mutableStateOf(false) }
    val owner = remember { Any() }
    val feedback = LocalHapticFeedback.current
    PressRegion(modifier, owner, id, haptics, pressed, feedback) {
        val base = if (transparent) Color(0xA6E6E9ED) else Color(0xFFE2E5E8)
        val shape = RoundedCornerShape(50)
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(base.lerp(Color.White, .36f), base.lerp(Color(0xFF7E858C), if (pressed.value) .35f else .12f))), shape,
            ).border(1.dp, Color(0xFF7F878F).copy(alpha = .65f), shape),
            contentAlignment = Alignment.Center,
        ) { Text(label, color = Color(0xFF646D76), fontSize = 15.sp, fontWeight = FontWeight.Bold) }
    }
}

@Composable
private fun StartButton(modifier: Modifier, haptics: Boolean, transparent: Boolean = false) {
    val pressed = remember { mutableStateOf(false) }
    val owner = remember { Any() }
    val feedback = LocalHapticFeedback.current
    PressRegion(modifier, owner, FlycastTouchId.START, haptics, pressed, feedback) {
        val fill = if (transparent) Color(0x996B7580) else Color(0xFFBBC1C7)
        val shape = RoundedCornerShape(12.dp)
        Box(Modifier.fillMaxSize().background(fill, shape).border(1.dp, Color.White.copy(alpha = .62f), shape), contentAlignment = Alignment.Center) {
            Text("START", color = if (transparent) Color.White else Color(0xFF4D555D), fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun MenuButton(modifier: Modifier, onMenu: () -> Unit, haptics: Boolean, transparent: Boolean = false) {
    val feedback = LocalHapticFeedback.current
    var pressed by remember { mutableStateOf(false) }
    val shape = CircleShape
    Box(
        modifier.clip(shape).background(if (transparent) Color(0x77515B66) else Color(0xFFD5D9DD), shape)
            .border(1.dp, Color.White.copy(alpha = .68f), shape)
            .pointerInput(haptics) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    pressed = true
                    if (haptics) feedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onMenu()
                    try {
                        while (true) {
                            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) { change.consume(); break }
                            change.consume()
                        }
                    } finally { pressed = false }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize().padding(9.dp)) {
            val center = Offset(size.width / 2f, size.height / 2f)
            drawCircle(if (pressed) Color.White else Color(0xFF69727B), radius = size.minDimension * .12f, center = center)
            for (i in 0 until 8) {
                val angle = i * (Math.PI / 4.0)
                val p = Offset(center.x + kotlin.math.cos(angle).toFloat() * size.minDimension * .32f,
                    center.y + kotlin.math.sin(angle).toFloat() * size.minDimension * .32f)
                drawCircle(if (pressed) Color.White else Color(0xFF69727B), radius = size.minDimension * .065f, center = p)
            }
        }
    }
}

@Composable
private fun DirectionPad(modifier: Modifier, haptics: Boolean, transparent: Boolean = false) {
    val owner = remember { Any() }
    var active by remember { mutableStateOf(emptySet<Int>()) }
    val feedback = LocalHapticFeedback.current
    DisposableEffect(owner) { onDispose { DreamcastTouchInput.set(owner, emptySet()) } }
    Box(
        modifier.pointerInput(owner, haptics) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                down.consume()
                var previous = emptySet<Int>()
                fun update(position: Offset) {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val dx = (position.x - center.x) / center.x
                    val dy = (position.y - center.y) / center.y
                    val next = buildSet {
                        if (dx < -.24f) add(FlycastTouchId.LEFT)
                        if (dx > .24f) add(FlycastTouchId.RIGHT)
                        if (dy < -.24f) add(FlycastTouchId.UP)
                        if (dy > .24f) add(FlycastTouchId.DOWN)
                    }
                    if (next != previous) {
                        if (haptics && next.isNotEmpty()) feedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        previous = next
                        active = next
                        DreamcastTouchInput.set(owner, next)
                    }
                }
                try {
                    update(down.position)
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) { change.consume(); break }
                        change.consume()
                        update(change.position)
                    }
                } finally {
                    DreamcastTouchInput.set(owner, emptySet())
                    active = emptySet()
                }
            }
        },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val fill = if (transparent) Color(0x77434E5A) else Color(0xFFD7DBDF)
            val edge = if (transparent) Color.White.copy(alpha = .7f) else Color(0xFF7C858E)
            val arm = size.minDimension * .32f
            val thickness = size.minDimension * .31f
            val cx = size.width / 2f
            val cy = size.height / 2f
            val armRectH = androidx.compose.ui.geometry.CornerRadius(size.minDimension * .07f)
            drawRoundRect(fill, Offset(cx - thickness / 2f, cy - arm), Size(thickness, arm * 2f), armRectH)
            drawRoundRect(fill, Offset(cx - arm, cy - thickness / 2f), Size(arm * 2f, thickness), armRectH)
            drawRoundRect(edge, Offset(cx - thickness / 2f, cy - arm), Size(thickness, arm * 2f), armRectH, style = Stroke(size.minDimension * .018f))
            drawRoundRect(edge, Offset(cx - arm, cy - thickness / 2f), Size(arm * 2f, thickness), armRectH, style = Stroke(size.minDimension * .018f))
            val arrows = listOf(
                Triple(FlycastTouchId.UP, Offset(cx, cy - arm * .66f), Offset(0f, -1f)),
                Triple(FlycastTouchId.DOWN, Offset(cx, cy + arm * .66f), Offset(0f, 1f)),
                Triple(FlycastTouchId.LEFT, Offset(cx - arm * .66f, cy), Offset(-1f, 0f)),
                Triple(FlycastTouchId.RIGHT, Offset(cx + arm * .66f, cy), Offset(1f, 0f)),
            )
            arrows.forEach { (id, point, direction) ->
                val tint = if (id in active) Color.White else Color(0xFF6D7680)
                val length = size.minDimension * .08f
                val wing = length * .68f
                val path = Path().apply {
                    when (id) {
                        FlycastTouchId.UP -> { moveTo(point.x, point.y - length); lineTo(point.x - wing, point.y + length * .4f); lineTo(point.x + wing, point.y + length * .4f) }
                        FlycastTouchId.DOWN -> { moveTo(point.x, point.y + length); lineTo(point.x - wing, point.y - length * .4f); lineTo(point.x + wing, point.y - length * .4f) }
                        FlycastTouchId.LEFT -> { moveTo(point.x - length, point.y); lineTo(point.x + length * .4f, point.y - wing); lineTo(point.x + length * .4f, point.y + wing) }
                        else -> { moveTo(point.x + length, point.y); lineTo(point.x - length * .4f, point.y - wing); lineTo(point.x - length * .4f, point.y + wing) }
                    }
                    close()
                }
                drawPath(path, tint)
            }
            drawCircle(Color(0xFF89929B), size.minDimension * .075f, Offset(cx, cy))
        }
    }
}

@Composable
private fun AnalogStick(modifier: Modifier, transparent: Boolean = false) {
    var axis by remember { mutableStateOf(Offset.Zero) }
    Box(
        modifier.clip(CircleShape).pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                down.consume()
                fun update(position: Offset) {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val radius = min(size.width, size.height) * .36f
                    val dx = position.x - center.x
                    val dy = position.y - center.y
                    val length = hypot(dx, dy).coerceAtLeast(1f)
                    val scale = min(1f, radius / length)
                    val normalized = Offset(dx * scale / radius, dy * scale / radius)
                    axis = normalized
                    DreamcastTouchInput.joystick(normalized.x, normalized.y)
                }
                try {
                    update(down.position)
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) { change.consume(); break }
                        change.consume()
                        update(change.position)
                    }
                } finally {
                    axis = Offset.Zero
                    DreamcastTouchInput.joystick(0f, 0f)
                }
            }
        },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val outer = if (transparent) Color(0x88404B56) else Color(0xFFD6DADF)
            val edge = if (transparent) Color.White.copy(alpha = .78f) else Color(0xFF777F88)
            drawCircle(outer, radius = size.minDimension * .49f)
            drawCircle(edge, radius = size.minDimension * .49f, style = Stroke(size.minDimension * .035f))
            drawCircle(if (transparent) Color(0x99596570) else Color(0xFFB8BEC4), radius = size.minDimension * .36f)
            val knob = Offset(size.width / 2f + axis.x * size.minDimension * .17f, size.height / 2f + axis.y * size.minDimension * .17f)
            drawCircle(if (transparent) Color(0xD0AEB5BC) else Color(0xFFE9ECEF), size.minDimension * .25f,
                Offset(knob.x, knob.y + size.minDimension * .015f))
            drawCircle(Color.White.copy(alpha = .78f), size.minDimension * .25f, knob, style = Stroke(size.minDimension * .025f))
            drawCircle(Color(0xFF8D969F).copy(alpha = .35f), size.minDimension * .16f, knob)
        }
    }
}

@Composable
private fun PressRegion(
    modifier: Modifier,
    owner: Any,
    controlId: Int,
    haptics: Boolean,
    pressed: MutableState<Boolean>,
    feedback: androidx.compose.ui.hapticfeedback.HapticFeedback,
    content: @Composable () -> Unit,
) {
    Box(
        modifier.pointerInput(owner, haptics) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                down.consume()
                pressed.value = true
                DreamcastTouchInput.set(owner, setOf(controlId))
                if (haptics) feedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                try {
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) { change.consume(); break }
                        change.consume()
                    }
                } finally {
                    DreamcastTouchInput.set(owner, emptySet())
                    pressed.value = false
                }
            }
        },
        contentAlignment = Alignment.Center,
    ) { content() }
}

private val FaceRed = Color(0xFFE84F64)
private val FaceBlue = Color(0xFF42A5E8)
private val FaceGreen = Color(0xFF53C79A)
private val FaceYellow = Color(0xFFF2D447)

private fun Color.lerp(other: Color, amount: Float): Color = androidx.compose.ui.graphics.lerp(this, other, amount.coerceIn(0f, 1f))
