package com.example.simplepinball

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.consume
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF0B1220)) {
                    PinballGame()
                }
            }
        }
    }
}

private data class Ball(var position: Offset, var velocity: Offset)

@Composable
private fun PinballGame() {
    var viewSize by remember { mutableStateOf(Size.Zero) }
    var ball by remember { mutableStateOf(Ball(Offset.Zero, Offset.Zero)) }
    var leftPressed by remember { mutableStateOf(false) }
    var rightPressed by remember { mutableStateOf(false) }
    var plungerPull by remember { mutableFloatStateOf(0f) }
    var plungerPointerId by remember { mutableStateOf<PointerId?>(null) }
    var plungerReleased by remember { mutableStateOf(false) }

    val paddleLength = 170f
    val basePaddleAngle = 22f
    val activatedAngle = -25f
    val paddleYOffset = 220f
    val paddleThickness = 20f
    val ballRadius = 22f
    val gravity = 1600f

    LaunchedEffect(viewSize) {
        if (viewSize == Size.Zero) return@LaunchedEffect
        // Reset ball whenever layout is known
        ball = Ball(
            position = Offset(viewSize.width * 0.75f, viewSize.height - paddleYOffset - ballRadius * 2),
            velocity = Offset(0f, 0f)
        )

        var lastFrameNanos = 0L
        while (isActive) {
            val frameTimeNanos = androidx.compose.ui.platform.withFrameNanos { it }
            if (lastFrameNanos != 0L) {
                val deltaSeconds = (frameTimeNanos - lastFrameNanos) / 1_000_000_000f
                val gravityVelocity = gravity * deltaSeconds

                val vx = ball.velocity.x
                val vy = ball.velocity.y + gravityVelocity
                var updatedVelocity = Offset(vx, vy)
                var updatedPosition = ball.position + updatedVelocity * deltaSeconds

                // Wall collisions
                if (updatedPosition.x < ballRadius) {
                    updatedPosition = updatedPosition.copy(x = ballRadius)
                    updatedVelocity = updatedVelocity.copy(x = abs(updatedVelocity.x) * 0.9f)
                }
                if (updatedPosition.x > viewSize.width - ballRadius) {
                    updatedPosition = updatedPosition.copy(x = viewSize.width - ballRadius)
                    updatedVelocity = updatedVelocity.copy(x = -abs(updatedVelocity.x) * 0.9f)
                }
                if (updatedPosition.y < ballRadius) {
                    updatedPosition = updatedPosition.copy(y = ballRadius)
                    updatedVelocity = updatedVelocity.copy(y = abs(updatedVelocity.y) * 0.9f)
                }

                // Floor - keep ball within screen
                if (updatedPosition.y > viewSize.height - ballRadius) {
                    updatedPosition = Offset(viewSize.width * 0.75f, viewSize.height - paddleYOffset - ballRadius * 2)
                    updatedVelocity = Offset(0f, -600f)
                }

                // Paddle collisions
                val leftAnchor = Offset(viewSize.width * 0.35f, viewSize.height - paddleYOffset)
                val rightAnchor = Offset(viewSize.width * 0.65f, viewSize.height - paddleYOffset)

                fun paddleNormal(isLeft: Boolean, pressed: Boolean): Offset {
                    val angle = (if (pressed) activatedAngle else basePaddleAngle) * if (isLeft) 1 else -1
                    val rad = angle * (PI / 180f).toFloat()
                    return Offset(cos(rad), sin(rad))
                }

                fun paddleCollision(anchor: Offset, isLeft: Boolean, pressed: Boolean) {
                    val angle = (if (pressed) activatedAngle else basePaddleAngle) * if (isLeft) 1 else -1
                    val rad = angle * (PI / 180f).toFloat()
                    val end = Offset(
                        x = anchor.x + paddleLength * cos(rad),
                        y = anchor.y + paddleLength * sin(rad)
                    )
                    val minX = min(anchor.x, end.x) - ballRadius
                    val maxX = max(anchor.x, end.x) + ballRadius
                    val minY = min(anchor.y, end.y) - ballRadius
                    val maxY = max(anchor.y, end.y) + ballRadius

                    if (updatedPosition.x in minX..maxX && updatedPosition.y in minY..maxY && updatedVelocity.y > 0f) {
                        val normal = paddleNormal(isLeft, pressed)
                        val speed = updatedVelocity.getDistance()
                        val bounceStrength = if (pressed) 1.3f else 0.9f
                        val horizontalPush = if (isLeft) -220f else 220f
                        updatedVelocity = Offset(
                            x = normal.x * speed * bounceStrength + horizontalPush,
                            y = normal.y * -speed * bounceStrength - 150f
                        )
                        updatedPosition = updatedPosition.copy(y = anchor.y - ballRadius)
                    }
                }

                paddleCollision(leftAnchor, true, leftPressed)
                paddleCollision(rightAnchor, false, rightPressed)

                // Plunger release
                if (plungerReleased) {
                    val launchPower = 1400f * (0.35f + plungerPull)
                    updatedVelocity = updatedVelocity.copy(y = updatedVelocity.y - launchPower)
                    plungerReleased = false
                }

                ball = Ball(updatedPosition, updatedVelocity)
            }
            lastFrameNanos = frameTimeNanos
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B1220))
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val size = this.size
                        val height = size.height
                        val width = size.width
                        val plungerWidth = 120f
                        val plungerHeight = 220f
                        val plungerRectLeft = width * 0.85f - plungerWidth / 2
                        val plungerRectRight = plungerRectLeft + plungerWidth
                        val plungerRectTop = height - plungerHeight

                        leftPressed = event.changes.any { it.pressed && it.position.x < width * 0.35f }
                        rightPressed = event.changes.any { it.pressed && it.position.x > width * 0.65f }

                        event.changes.forEach { change ->
                            val pos = change.position
                            val inPlunger = pos.x in plungerRectLeft..plungerRectRight && pos.y > plungerRectTop

                            if (plungerPointerId == null && change.pressed && inPlunger) {
                                plungerPointerId = change.id
                            }

                            if (plungerPointerId == change.id) {
                                if (change.pressed) {
                                    val pull = ((pos.y - plungerRectTop) / plungerHeight).coerceIn(0f, 1f)
                                    plungerPull = pull
                                }
                                if (change.changedToUp()) {
                                    plungerReleased = true
                                    plungerPointerId = null
                                    plungerPull = 0f
                                }
                            }
                            if (change.changedToUp() && change.id == plungerPointerId) {
                                plungerPointerId = null
                            }
                        }

                        event.changes.forEach { it.consume() }
                    }
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            viewSize = Size(size.width, size.height)

            // Background playfield
            drawRect(
                color = Color(0xFF101A33),
                size = size
            )

            // Ball
            drawCircle(
                color = Color(0xFF57D6FF),
                radius = ballRadius,
                center = ball.position
            )

            // Paddles
            fun drawPaddle(anchor: Offset, isLeft: Boolean, pressed: Boolean) {
                val angle = (if (pressed) activatedAngle else basePaddleAngle) * if (isLeft) 1 else -1
                val rad = angle * (PI / 180f).toFloat()
                val end = Offset(
                    x = anchor.x + paddleLength * cos(rad),
                    y = anchor.y + paddleLength * sin(rad)
                )
                drawLine(
                    color = Color(0xFFFFC857),
                    start = anchor,
                    end = end,
                    strokeWidth = paddleThickness,
                    cap = StrokeCap.Round
                )
            }

            val leftAnchor = Offset(size.width * 0.35f, size.height - paddleYOffset)
            val rightAnchor = Offset(size.width * 0.65f, size.height - paddleYOffset)
            drawPaddle(leftAnchor, true, leftPressed)
            drawPaddle(rightAnchor, false, rightPressed)

            // Plunger
            val plungerWidth = 120f
            val plungerHeight = 220f
            val plungerLeft = size.width * 0.85f - plungerWidth / 2
            val plungerTop = size.height - plungerHeight
            drawRect(
                color = Color(0xFF1F2A44),
                topLeft = Offset(plungerLeft, plungerTop),
                size = Size(plungerWidth, plungerHeight)
            )
            val pullDistance = plungerPull * (plungerHeight - 40f)
            drawRoundRect(
                color = Color(0xFF7AE582),
                topLeft = Offset(plungerLeft + 20f, plungerTop + 20f + pullDistance),
                size = Size(plungerWidth - 40f, 60f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(12f, 12f)
            )

            // Simple lanes and bumpers
            drawLine(
                color = Color(0xFF2F3B52),
                start = Offset(size.width * 0.1f, size.height * 0.25f),
                end = Offset(size.width * 0.9f, size.height * 0.25f),
                strokeWidth = 6f
            )
            drawCircle(
                color = Color(0xFFFF6B6B),
                radius = 26f,
                center = Offset(size.width * 0.3f, size.height * 0.45f)
            )
            drawCircle(
                color = Color(0xFFFF6B6B),
                radius = 26f,
                center = Offset(size.width * 0.7f, size.height * 0.5f)
            )
        }

        Text(
            text = "左タップ: 左パドル / 右タップ: 右パドル / 右下ドラッグ: プランジャー",
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .background(Color(0x66000000))
                .padding(horizontal = 12.dp, vertical = 8.dp)
        )
    }
}

private fun Offset.getDistance(): Float = kotlin.math.sqrt(x * x + y * y)
private operator fun Offset.plus(other: Offset): Offset = Offset(x + other.x, y + other.y)
private operator fun Offset.times(value: Float): Offset = Offset(x * value, y * value)
