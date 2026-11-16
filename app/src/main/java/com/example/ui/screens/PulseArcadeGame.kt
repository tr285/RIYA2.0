package com.example.ui.screens

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.ui.theme.LocalRiyaColors
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

private enum class PulseGameState { TITLE, PLAYING, PAUSED, GAME_OVER }
private enum class OrbKind { ENERGY_CYAN, OVERDRIVE_GOLD, HAZARD_RED }

private data class PulseOrb(
    val id: Int,
    var angleRad: Float,
    var distFraction: Float,
    val speedPerSec: Float,
    val kind: OrbKind
)

private data class SparkParticle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var life: Float,
    val maxLife: Float,
    val color: Color
)

@Composable
fun PulseArcadeGame(currentUserId: String) {
    val riyaColors = LocalRiyaColors.current
    val context = LocalContext.current
    val prefs = remember(currentUserId) {
        context.getSharedPreferences("riya_arcade_$currentUserId", Context.MODE_PRIVATE)
    }

    var gameState by remember { mutableStateOf(PulseGameState.TITLE) }
    var score by remember { mutableIntStateOf(0) }
    var bestScore by remember(currentUserId) { mutableIntStateOf(prefs.getInt("best_score", 0)) }
    var lives by remember { mutableIntStateOf(3) }
    var combo by remember { mutableIntStateOf(1) }
    var wave by remember { mutableIntStateOf(1) }
    var isMuted by remember { mutableStateOf(false) }

    var shieldAngleRad by remember { mutableFloatStateOf(-PI.toFloat() / 2f) }
    var hitFlash by remember { mutableFloatStateOf(0f) }
    var shockwaveRadius by remember { mutableFloatStateOf(0f) }
    var shockwaveAlpha by remember { mutableFloatStateOf(0f) }
    var corePulsePhase by remember { mutableFloatStateOf(0f) }
    var canvasSize by remember { mutableStateOf(IntSize(1, 1)) }

    val orbs = remember { mutableStateListOf<PulseOrb>() }
    val particles = remember { mutableStateListOf<SparkParticle>() }

    val toneGen = remember {
        runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 55) }.getOrNull()
    }
    DisposableEffect(toneGen) {
        onDispose { runCatching { toneGen?.release() } }
    }

    fun playCue(toneType: Int, durationMs: Int) {
        if (!isMuted) {
            runCatching { toneGen?.startTone(toneType, durationMs) }
        }
    }

    fun saveScoreIfBest(newScore: Int) {
        if (newScore > bestScore) {
            bestScore = newScore
            prefs.edit().putInt("best_score", newScore).apply()
        }
    }

    fun startNewGame() {
        score = 0
        lives = 3
        combo = 1
        wave = 1
        hitFlash = 0f
        shockwaveAlpha = 0f
        orbs.clear()
        particles.clear()
        gameState = PulseGameState.PLAYING
    }

    fun spawnBurst(cx: Float, cy: Float, color: Color, count: Int = 10) {
        repeat(count) {
            val a = Random.nextFloat() * 2f * PI.toFloat()
            val spd = 90f + Random.nextFloat() * 190f
            particles.add(
                SparkParticle(
                    x = cx,
                    y = cy,
                    vx = cos(a) * spd,
                    vy = sin(a) * spd,
                    life = 0.42f,
                    maxLife = 0.42f,
                    color = color
                )
            )
        }
    }

    LaunchedEffect(gameState) {
        if (gameState != PulseGameState.PLAYING) return@LaunchedEffect
        var lastNanos = 0L
        var spawnAccumulator = 0f
        var nextOrbId = 1

        while (isActive) {
            withFrameNanos { frameNanos ->
                if (lastNanos == 0L) {
                    lastNanos = frameNanos
                    return@withFrameNanos
                }
                val dt = ((frameNanos - lastNanos) / 1_000_000_000f).coerceIn(0f, 0.05f)
                lastNanos = frameNanos

                corePulsePhase += dt * 3.5f
                if (hitFlash > 0f) hitFlash = (hitFlash - dt * 2.8f).coerceAtLeast(0f)
                if (shockwaveAlpha > 0f) {
                    shockwaveRadius += dt * 780f
                    shockwaveAlpha = (shockwaveAlpha - dt * 1.6f).coerceAtLeast(0f)
                }

                // Wave progression
                val targetWave = 1 + (score / 120)
                if (targetWave > wave) wave = targetWave

                // Spawn orbs
                val spawnInterval = (1.15f - (wave - 1) * 0.08f).coerceAtLeast(0.42f)
                spawnAccumulator += dt
                if (spawnAccumulator >= spawnInterval) {
                    spawnAccumulator = 0f
                    val roll = Random.nextFloat()
                    val kind = when {
                        roll < 0.09f -> OrbKind.OVERDRIVE_GOLD
                        roll < 0.56f -> OrbKind.HAZARD_RED
                        else -> OrbKind.ENERGY_CYAN
                    }
                    val speed = (0.36f + (wave - 1) * 0.045f + Random.nextFloat() * 0.14f)
                        .coerceAtMost(0.95f)
                    orbs.add(
                        PulseOrb(
                            id = nextOrbId++,
                            angleRad = Random.nextFloat() * 2f * PI.toFloat() - PI.toFloat(),
                            distFraction = 1.02f,
                            speedPerSec = speed,
                            kind = kind
                        )
                    )
                }

                val centerX = canvasSize.width / 2f
                val centerY = canvasSize.height / 2f
                val maxR = min(centerX, centerY) * 0.92f
                val shieldFrac = 0.28f
                val coreFrac = 0.14f
                val shieldHalfSpanRad = (38f * PI / 180.0).toFloat()

                val toRemove = mutableListOf<Int>()
                var triggerShockwaveClear = false

                for (i in orbs.indices) {
                    val orb = orbs[i]
                    val prevFrac = orb.distFraction
                    orb.distFraction -= orb.speedPerSec * dt

                    // Shield arc intersection
                    if (prevFrac >= shieldFrac && orb.distFraction <= shieldFrac + 0.02f) {
                        val delta = normalizeAngleDelta(orb.angleRad - shieldAngleRad)
                        if (abs(delta) <= shieldHalfSpanRad) {
                            val impactR = shieldFrac * maxR
                            val ix = centerX + cos(orb.angleRad) * impactR
                            val iy = centerY + sin(orb.angleRad) * impactR
                            when (orb.kind) {
                                OrbKind.ENERGY_CYAN -> {
                                    score += 10 * combo
                                    combo = (combo + 1).coerceAtMost(8)
                                    saveScoreIfBest(score)
                                    spawnBurst(ix, iy, Color(0xFF06B6D4), 10)
                                    playCue(ToneGenerator.TONE_PROP_BEEP, 45)
                                }
                                OrbKind.HAZARD_RED -> {
                                    score += 15 * combo
                                    saveScoreIfBest(score)
                                    spawnBurst(ix, iy, Color(0xFFF59E0B), 12)
                                    playCue(ToneGenerator.TONE_PROP_ACK, 50)
                                }
                                OrbKind.OVERDRIVE_GOLD -> {
                                    score += 50 * combo
                                    if (lives < 3) lives += 1
                                    saveScoreIfBest(score)
                                    shockwaveRadius = impactR
                                    shockwaveAlpha = 1f
                                    triggerShockwaveClear = true
                                    spawnBurst(ix, iy, Color(0xFFFBBF24), 18)
                                    playCue(ToneGenerator.TONE_PROP_BEEP2, 95)
                                }
                            }
                            toRemove.add(i)
                            continue
                        }
                    }

                    // Core impact
                    if (orb.distFraction <= coreFrac) {
                        val impactR = coreFrac * maxR
                        val ix = centerX + cos(orb.angleRad) * impactR
                        val iy = centerY + sin(orb.angleRad) * impactR
                        when (orb.kind) {
                            OrbKind.HAZARD_RED -> {
                                lives -= 1
                                combo = 1
                                hitFlash = 1f
                                spawnBurst(ix, iy, Color(0xFFEF4444), 16)
                                playCue(ToneGenerator.TONE_SUP_ERROR, 140)
                                if (lives <= 0) {
                                    saveScoreIfBest(score)
                                    gameState = PulseGameState.GAME_OVER
                                }
                            }
                            OrbKind.ENERGY_CYAN, OrbKind.OVERDRIVE_GOLD -> {
                                combo = 1
                                spawnBurst(ix, iy, Color(0xFF94A3B8), 6)
                            }
                        }
                        toRemove.add(i)
                    }
                }

                for (idx in toRemove.asReversed()) {
                    if (idx in orbs.indices) orbs.removeAt(idx)
                }

                if (triggerShockwaveClear) {
                    val hazardIndices = orbs.indices.filter { orbs[it].kind == OrbKind.HAZARD_RED }
                    for (idx in hazardIndices.asReversed()) {
                        val h = orbs[idx]
                        val hx = centerX + cos(h.angleRad) * (h.distFraction * maxR)
                        val hy = centerY + sin(h.angleRad) * (h.distFraction * maxR)
                        spawnBurst(hx, hy, Color(0xFFFBBF24), 8)
                        score += 10
                        orbs.removeAt(idx)
                    }
                    saveScoreIfBest(score)
                }

                // Update particles
                val deadParticles = mutableListOf<Int>()
                for (i in particles.indices) {
                    val p = particles[i]
                    p.x += p.vx * dt
                    p.y += p.vy * dt
                    p.life -= dt
                    if (p.life <= 0f) deadParticles.add(i)
                }
                for (idx in deadParticles.asReversed()) {
                    if (idx in particles.indices) particles.removeAt(idx)
                }
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // HUD Row
        Card(
            colors = CardDefaults.cardColors(containerColor = riyaColors.bgCard),
            border = BorderStroke(1.dp, riyaColors.border),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("SCORE $score  (x$combo)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = riyaColors.accent)
                    Text("WAVE $wave  •  HP ${"♥".repeat(lives.coerceAtLeast(0))}${"♡".repeat((3 - lives).coerceIn(0, 3))}  •  BEST $bestScore", style = MaterialTheme.typography.labelSmall, color = riyaColors.textSecondary)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { isMuted = !isMuted },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("arcade_mute_button")
                    ) {
                        Icon(
                            imageVector = if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = if (isMuted) "Unmute arcade sound" else "Mute arcade sound",
                            tint = riyaColors.textSecondary
                        )
                    }
                    if (gameState == PulseGameState.PLAYING || gameState == PulseGameState.PAUSED) {
                        IconButton(
                            onClick = {
                                gameState = if (gameState == PulseGameState.PLAYING) PulseGameState.PAUSED else PulseGameState.PLAYING
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("arcade_pause_button")
                        ) {
                            Icon(
                                imageVector = if (gameState == PulseGameState.PLAYING) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = if (gameState == PulseGameState.PLAYING) "Pause game" else "Resume game",
                                tint = riyaColors.accent
                            )
                        }
                    }
                }
            }
        }

        // Playfield Canvas + State Overlays
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color(0xFF0F172A), Color(0xFF060913))
                    )
                )
                .onSizeChanged { canvasSize = it }
                .pointerInput(gameState) {
                    detectTapGestures { pos ->
                        if (gameState == PulseGameState.PLAYING) {
                            val cx = size.width / 2f
                            val cy = size.height / 2f
                            shieldAngleRad = atan2(pos.y - cy, pos.x - cx)
                        }
                    }
                }
                .pointerInput(gameState) {
                    detectDragGestures { change, _ ->
                        change.consume()
                        if (gameState == PulseGameState.PLAYING) {
                            val cx = size.width / 2f
                            val cy = size.height / 2f
                            shieldAngleRad = atan2(change.position.y - cy, change.position.x - cx)
                        }
                    }
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cx = size.width / 2f
                val cy = size.height / 2f
                val center = Offset(cx, cy)
                val maxR = min(cx, cy) * 0.92f

                // Concentric radar rings
                listOf(0.28f, 0.52f, 0.76f, 0.98f).forEach { ringFrac ->
                    drawCircle(
                        color = Color(0xFF1E293B),
                        radius = maxR * ringFrac,
                        center = center,
                        style = Stroke(width = 1.5f)
                    )
                }

                // Shockwave ring on Gold Overdrive
                if (shockwaveAlpha > 0f) {
                    drawCircle(
                        color = Color(0xFFFBBF24).copy(alpha = shockwaveAlpha * 0.7f),
                        radius = shockwaveRadius,
                        center = center,
                        style = Stroke(width = 6f)
                    )
                }

                // Core glow & sphere
                val pulseR = maxR * (0.12f + 0.015f * sin(corePulsePhase))
                val coreColor = if (hitFlash > 0f) Color(0xFFEF4444) else Color(0xFF06B6D4)
                drawCircle(
                    color = coreColor.copy(alpha = 0.22f + hitFlash * 0.35f),
                    radius = pulseR * 1.45f,
                    center = center
                )
                drawCircle(
                    color = coreColor,
                    radius = pulseR,
                    center = center
                )

                // Orbital Shield Arc
                val shieldR = maxR * 0.28f
                val startDeg = Math.toDegrees(shieldAngleRad.toDouble()).toFloat() - 38f
                drawArc(
                    color = Color(0xFF22D3EE),
                    startAngle = startDeg,
                    sweepAngle = 76f,
                    useCenter = false,
                    topLeft = Offset(cx - shieldR, cy - shieldR),
                    size = Size(shieldR * 2f, shieldR * 2f),
                    style = Stroke(width = 14f, cap = StrokeCap.Round)
                )

                // Projectiles
                orbs.forEach { orb ->
                    val r = orb.distFraction * maxR
                    val ox = cx + cos(orb.angleRad) * r
                    val oy = cy + sin(orb.angleRad) * r
                    val orbColor = when (orb.kind) {
                        OrbKind.ENERGY_CYAN -> Color(0xFF22D3EE)
                        OrbKind.OVERDRIVE_GOLD -> Color(0xFFFBBF24)
                        OrbKind.HAZARD_RED -> Color(0xFFF43F5E)
                    }
                    val radius = if (orb.kind == OrbKind.OVERDRIVE_GOLD) 14f else 11f
                    drawCircle(color = orbColor.copy(alpha = 0.28f), radius = radius * 1.8f, center = Offset(ox, oy))
                    drawCircle(color = orbColor, radius = radius, center = Offset(ox, oy))
                }

                // Particles
                particles.forEach { p ->
                    val alpha = (p.life / p.maxLife).coerceIn(0f, 1f)
                    drawCircle(
                        color = p.color.copy(alpha = alpha),
                        radius = 4.5f * alpha + 1.5f,
                        center = Offset(p.x, p.y)
                    )
                }
            }

            if (gameState != PulseGameState.PLAYING) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xB3050814)),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = riyaColors.bgCard),
                        border = BorderStroke(1.dp, riyaColors.accent),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .padding(24.dp)
                            .fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            when (gameState) {
                                PulseGameState.TITLE -> {
                                    Text("PULSE CORE", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = riyaColors.accent)
                                    Text(
                                        "Drag or tap to aim your orbital shield.\nCatch Cyan orbs for combo points, grab Gold orbs for shockwave overdrive, and deflect Red hazards!",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = riyaColors.textSecondary
                                    )
                                    Button(
                                        onClick = { startNewGame() },
                                        colors = ButtonDefaults.buttonColors(containerColor = riyaColors.accent, contentColor = Color.White),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .defaultMinSize(minHeight = 48.dp)
                                            .testTag("arcade_start_button")
                                    ) {
                                        Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Start Mission")
                                    }
                                }

                                PulseGameState.PAUSED -> {
                                    Text("PAUSED", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = riyaColors.accent)
                                    Button(
                                        onClick = { gameState = PulseGameState.PLAYING },
                                        colors = ButtonDefaults.buttonColors(containerColor = riyaColors.accent, contentColor = Color.White),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .defaultMinSize(minHeight = 48.dp)
                                            .testTag("arcade_resume_button")
                                    ) {
                                        Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Resume")
                                    }
                                    Button(
                                        onClick = { startNewGame() },
                                        colors = ButtonDefaults.buttonColors(containerColor = riyaColors.bgSurfaceElevated, contentColor = riyaColors.textPrimary),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .defaultMinSize(minHeight = 48.dp)
                                    ) {
                                        Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Restart")
                                    }
                                }

                                PulseGameState.GAME_OVER -> {
                                    Text("CORE BREACHED", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = riyaColors.statusErrorFg)
                                    Text(
                                        "Final Score: $score  •  Best: $bestScore",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = riyaColors.textPrimary
                                    )
                                    Button(
                                        onClick = { startNewGame() },
                                        colors = ButtonDefaults.buttonColors(containerColor = riyaColors.accent, contentColor = Color.White),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .defaultMinSize(minHeight = 48.dp)
                                            .testTag("arcade_restart_button")
                                    ) {
                                        Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Play Again")
                                    }
                                }

                                PulseGameState.PLAYING -> Unit
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun normalizeAngleDelta(angleRad: Float): Float {
    var a = angleRad
    while (a > PI.toFloat()) a -= 2f * PI.toFloat()
    while (a < -PI.toFloat()) a += 2f * PI.toFloat()
    return a
}
