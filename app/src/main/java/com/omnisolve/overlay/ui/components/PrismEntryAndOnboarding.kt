package com.omnisolve.overlay.ui.components

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnisolve.overlay.service.PrismEngagementNotificationManager
import com.omnisolve.overlay.ui.physics.LocalReducedMotion
import com.omnisolve.overlay.ui.theme.LocalWhiteGlassPalette
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val EntryCrimson = Color(0xFFD71921)
private val EntryCrimsonBright = Color(0xFFFF4650)

/**
 * Reusable geometric Red Prism emblem drawn cleanly on Canvas so the top header,
 * entry splash reveal, and onboarding guide share one unmistakable visual identity.
 */
fun DrawScope.drawRedPrismGem(
    center: Offset,
    radius: Float,
    highlightShift: Float = 0f,
    alpha: Float = 1f
) {
    val top = Offset(center.x, center.y - radius)
    val left = Offset(center.x - radius * 0.86f, center.y + radius * 0.42f)
    val right = Offset(center.x + radius * 0.86f, center.y + radius * 0.42f)
    val bottom = Offset(center.x, center.y + radius * 0.94f)
    val core = Offset(center.x + radius * 0.08f * highlightShift, center.y + radius * 0.08f)

    // Left dark ruby facet
    val leftFacet = Path().apply {
        moveTo(top.x, top.y)
        lineTo(left.x, left.y)
        lineTo(core.x, core.y)
        close()
    }
    drawPath(leftFacet, color = Color(0xFF9F1017).copy(alpha = alpha))

    // Right bright ruby facet
    val rightFacet = Path().apply {
        moveTo(top.x, top.y)
        lineTo(core.x, core.y)
        lineTo(right.x, right.y)
        close()
    }
    drawPath(
        rightFacet,
        brush = Brush.linearGradient(
            listOf(
                EntryCrimsonBright.copy(alpha = alpha),
                EntryCrimson.copy(alpha = alpha)
            ),
            start = top,
            end = right
        )
    )

    // Bottom refracted facet
    val bottomLeftFacet = Path().apply {
        moveTo(left.x, left.y)
        lineTo(bottom.x, bottom.y)
        lineTo(core.x, core.y)
        close()
    }
    drawPath(bottomLeftFacet, color = Color(0xFF6B0A10).copy(alpha = alpha))

    val bottomRightFacet = Path().apply {
        moveTo(core.x, core.y)
        lineTo(bottom.x, bottom.y)
        lineTo(right.x, right.y)
        close()
    }
    drawPath(bottomRightFacet, color = EntryCrimson.copy(alpha = alpha * 0.92f))

    // Specular edge lines
    val outline = Path().apply {
        moveTo(top.x, top.y)
        lineTo(right.x, right.y)
        lineTo(bottom.x, bottom.y)
        lineTo(left.x, left.y)
        close()
    }
    drawPath(
        outline,
        color = Color(0xFFFFD6D9).copy(alpha = 0.72f * alpha),
        style = Stroke(width = radius * 0.07f, cap = StrokeCap.Round)
    )
    drawLine(
        color = Color.White.copy(alpha = 0.65f * alpha),
        start = top,
        end = core,
        strokeWidth = radius * 0.055f,
        cap = StrokeCap.Round
    )
}

@Composable
fun RedPrismLogoMark(
    modifier: Modifier = Modifier,
    animated: Boolean = false
) {
    val shift = if (animated) {
        val inf = rememberInfiniteTransition(label = "prismGlow")
        val s by inf.animateFloat(
            initialValue = -1f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(2200, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "shift"
        )
        s
    } else 0f

    Canvas(modifier = modifier) {
        val r = size.minDimension * 0.44f
        drawRedPrismGem(center = center, radius = r, highlightShift = shift, alpha = 1f)
    }
}

private data class Vec3(val x: Float, val y: Float, val z: Float)

private data class PrismFace(
    val p0: Offset,
    val p1: Offset,
    val p2: Offset,
    val depth: Float,
    val fillColor: Color,
    val rimAlpha: Float
)

private data class HudEmberParticle(
    val angleRad: Float,
    val baseRadiusFactor: Float,
    val driftSpeed: Float,
    val sizeDp: Float,
    val baseAlpha: Float,
    val phaseOffset: Float
)

/**
 * Full-screen OLED Crimson Prism + 360° Dynamic HUD entrance animation on app launch:
 * - Faceted 3D-shaded rotating ruby octahedron prism
 * - Multi-ring calibrated HUD radar reticle & rotating arc ticks
 * - Depth-sorted twin glowing crimson-white orbital energy ribbons + ignition starburst
 * - Drifting atmospheric embers and staggered "Solve Anything" reveal
 * - Respects [LocalReducedMotion] and supports instant tap-to-enter.
 */
@Composable
fun PrismAppEntryAnimationOverlay(
    visible: Boolean,
    onAnimationFinished: () -> Unit
) {
    val palette = LocalWhiteGlassPalette.current
    val reducedMotion = LocalReducedMotion.current

    val accentCore = remember(palette.accentSky) {
        if (palette.isDarkObsidian) Color(0xFFD71921) else palette.accentSky
    }
    val accentHot = remember(palette.accentSkyBright) {
        if (palette.isDarkObsidian) Color(0xFFFF4652) else palette.accentSkyBright
    }

    val masterProgress = remember { Animatable(0f) }
    val spinRadians = remember { Animatable(0f) }
    val titleReveal = remember { Animatable(0f) }

    val embers = remember {
        List(36) { idx ->
            val golden = idx * 2.3999632f
            HudEmberParticle(
                angleRad = golden,
                baseRadiusFactor = 0.22f + ((idx * 37) % 100) / 145f,
                driftSpeed = 0.35f + ((idx * 19) % 10) * 0.08f,
                sizeDp = 1.1f + ((idx * 13) % 7) * 0.48f,
                baseAlpha = 0.26f + ((idx * 23) % 10) * 0.065f,
                phaseOffset = (idx * 0.41f) % (2f * PI.toFloat())
            )
        }
    }

    val reusableFacetPath = remember { Path() }
    val projectedVerts = remember { Array(6) { Offset.Zero } }
    val worldVerts = remember { Array(6) { Vec3(0f, 0f, 0f) } }
    val faceList = remember { ArrayList<PrismFace>(8) }

    LaunchedEffect(visible, reducedMotion) {
        if (!visible) return@LaunchedEffect
        masterProgress.snapTo(0f)
        spinRadians.snapTo(0f)
        titleReveal.snapTo(0f)

        if (reducedMotion) {
            launch { masterProgress.animateTo(1f, tween(260, easing = FastOutSlowInEasing)) }
            launch { titleReveal.animateTo(1f, tween(220, easing = FastOutSlowInEasing)) }
            delay(360L)
            onAnimationFinished()
        } else {
            launch {
                masterProgress.animateTo(1f, tween(850, easing = FastOutSlowInEasing))
            }
            launch {
                spinRadians.animateTo(1.25f * PI.toFloat(), tween(900, easing = FastOutSlowInEasing))
            }
            launch {
                delay(120L)
                titleReveal.animateTo(1f, tween(420, easing = FastOutSlowInEasing))
            }
            delay(920L)
            onAnimationFinished()
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(140)),
        exit = fadeOut(tween(340))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF040203))
                .clickable(
                    role = Role.Button,
                    onClickLabel = "Skip entrance animation",
                    onClick = onAnimationFinished
                )
                .testTag("prism_entry_animation_overlay"),
            contentAlignment = Alignment.Center
        ) {
            val t = masterProgress.value
            val yaw = spinRadians.value
            val hudAlpha = ((t - 0.10f) / 0.28f).coerceIn(0f, 1f)
            val burstPhase = ((t - 0.18f) / 0.26f).coerceIn(0f, 1f)
            val ribbonAlpha = ((t - 0.10f) / 0.24f).coerceIn(0f, 1f)
            val prismScale = if (t < 0.24f) {
                0.58f + 0.42f * FastOutSlowInEasing.transform((t / 0.24f).coerceIn(0f, 1f))
            } else {
                1f + 0.03f * sin((t - 0.24f) * 7f)
            }
            val glitchBurst = 0f
            val glitchCyan = accentHot
            val glitchShiftSign = 1f

            // Full-bleed atmospheric HUD backdrop + embers + 3D ruby prism + orbital ribbons
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cx = size.width * 0.5f
                val cy = size.height * 0.44f
                val hudRadius = size.minDimension * 0.38f
                val prismRadius = hudRadius * 0.44f * prismScale

                // 1. Deep crimson radial ambient haze behind HUD
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            accentCore.copy(alpha = 0.24f * (0.4f + 0.6f * hudAlpha)),
                            accentCore.copy(alpha = 0.07f),
                            Color.Transparent
                        ),
                        center = Offset(cx, cy),
                        radius = hudRadius * 1.45f
                    ),
                    center = Offset(cx, cy),
                    radius = hudRadius * 1.45f
                )

                // 2. Drifting atmospheric embers
                embers.forEach { p ->
                    val drift = p.angleRad + t * p.driftSpeed
                    val radial = hudRadius * (p.baseRadiusFactor + 0.08f * sin(t * 4f + p.phaseOffset))
                    val px = cx + cos(drift) * radial * 1.35f
                    val py = cy + sin(drift) * radial * 1.35f - t * 24.dp.toPx() * p.driftSpeed
                    val pulse = 0.5f + 0.5f * sin(t * 9f + p.phaseOffset)
                    drawCircle(
                        color = accentHot.copy(alpha = p.baseAlpha * pulse),
                        radius = p.sizeDp.dp.toPx(),
                        center = Offset(px, py)
                    )
                }

                // 3. Concentric 360° HUD radar rings & precision ticks
                if (hudAlpha > 0.01f) {
                    val outerR = hudRadius * (0.94f + 0.06f * hudAlpha)
                    val midR = hudRadius * 0.78f
                    val innerR = hudRadius * 0.62f

                    val reticleJitterX = if (glitchBurst > 0.05f) {
                        glitchShiftSign * glitchBurst * 7.dp.toPx()
                    } else 0f
                    val hudCenter = Offset(cx + reticleJitterX, cy)

                    // Outer crisp ring
                    drawCircle(
                        color = accentCore.copy(alpha = 0.36f * hudAlpha),
                        radius = outerR,
                        center = hudCenter,
                        style = Stroke(width = 1.3.dp.toPx())
                    )

                    // Outer rotating segmented HUD arcs
                    val rotDeg = t * 88f
                    val outerArcRectSize = Size(outerR * 2f, outerR * 2f)
                    val outerTopLeft = Offset(hudCenter.x - outerR, hudCenter.y - outerR)
                    for (seg in 0..3) {
                        drawArc(
                            color = (if (glitchBurst > 0.45f && seg % 2 == 0) glitchCyan else accentHot)
                                .copy(alpha = 0.72f * hudAlpha),
                            startAngle = rotDeg + seg * 90f - 18f,
                            sweepAngle = 36f,
                            useCenter = false,
                            topLeft = outerTopLeft,
                            size = outerArcRectSize,
                            style = Stroke(width = 2.1.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }

                    // Middle radar precision ticks (72 notches counter-rotating)
                    val tickBaseRad = (-t * 54f) * (PI.toFloat() / 180f)
                    for (i in 0 until 72) {
                        val a = tickBaseRad + i * (2f * PI.toFloat() / 72f)
                        val isMajor = i % 6 == 0
                        val r0 = midR - if (isMajor) 7.dp.toPx() else 3.5.dp.toPx()
                        val r1 = midR + if (isMajor) 2.dp.toPx() else 0f
                        val c = cos(a)
                        val s = sin(a)
                        drawLine(
                            color = accentHot.copy(alpha = (if (isMajor) 0.55f else 0.22f) * hudAlpha),
                            start = Offset(hudCenter.x + c * r0, hudCenter.y + s * r0),
                            end = Offset(hudCenter.x + c * r1, hudCenter.y + s * r1),
                            strokeWidth = (if (isMajor) 1.5f else 0.9f).dp.toPx()
                        )
                    }

                    // Inner fine calibration circle & sweeping brackets
                    drawCircle(
                        color = Color.White.copy(alpha = 0.14f * hudAlpha),
                        radius = innerR,
                        center = hudCenter,
                        style = Stroke(width = 0.9.dp.toPx())
                    )
                    val innerArcSize = Size(innerR * 2f, innerR * 2f)
                    val innerTopLeft = Offset(hudCenter.x - innerR, hudCenter.y - innerR)
                    for (seg in 0..2) {
                        drawArc(
                            color = accentCore.copy(alpha = 0.48f * hudAlpha),
                            startAngle = -rotDeg * 1.4f + seg * 120f,
                            sweepAngle = 52f,
                            useCenter = false,
                            topLeft = innerTopLeft,
                            size = innerArcSize,
                            style = Stroke(width = 1.4.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }
                }

                // Helper to draw back/front segments of the two tilted 3D orbital energy ribbons
                fun drawOrbitalRibbonHalf(drawFrontHalf: Boolean) {
                    if (ribbonAlpha <= 0.01f) return
                    val orbitSpecs = listOf(
                        Triple(hudRadius * 0.72f, 0.24f, 0.34f), // ring 1 tilt & squash
                        Triple(hudRadius * 0.64f, 0.22f, -0.28f) // ring 2 counter-tilt
                    )
                    orbitSpecs.forEachIndexed { ringIdx, (rx, squash, rollRad) ->
                        val ry = rx * squash
                        val cosRoll = cos(rollRad)
                        val sinRoll = sin(rollRad)
                        val headAngle = if (ringIdx == 0) {
                            t * 7.8f + 0.5f
                        } else {
                            -t * 6.4f + 3.4f
                        }
                        val arcSpan = 3.25f
                        val steps = 28

                        fun projectOrbitPoint(theta: Float): Pair<Offset, Float> {
                            val lx = cos(theta) * rx
                            val ly = sin(theta) * ry
                            val px = cx + lx * cosRoll - ly * sinRoll
                            val py = cy + lx * sinRoll + ly * cosRoll
                            return Offset(px, py) to sin(theta)
                        }

                        var prevPt = projectOrbitPoint(headAngle - arcSpan)
                        for (step in 1..steps) {
                            val frac = step.toFloat() / steps.toFloat()
                            val theta = headAngle - arcSpan * (1f - frac)
                            val curPt = projectOrbitPoint(theta)
                            val isFront = (prevPt.second + curPt.second) * 0.5f >= 0f
                            if (isFront == drawFrontHalf) {
                                val segAlpha = (frac * frac) * ribbonAlpha
                                val depthBoost = if (isFront) 1f else 0.34f
                                // Outer crimson glow stroke
                                drawLine(
                                    color = accentHot.copy(alpha = 0.42f * segAlpha * depthBoost),
                                    start = prevPt.first,
                                    end = curPt.first,
                                    strokeWidth = (1.5f + 5.0f * frac).dp.toPx(),
                                    cap = StrokeCap.Round
                                )
                                // Incandescent white-crimson core filament
                                drawLine(
                                    color = Color(0xFFFFE5E7).copy(alpha = 0.92f * segAlpha * depthBoost),
                                    start = prevPt.first,
                                    end = curPt.first,
                                    strokeWidth = (0.7f + 1.8f * frac).dp.toPx(),
                                    cap = StrokeCap.Round
                                )
                            }
                            prevPt = curPt
                        }

                        // Blazing leading orb + spark dust near the ribbon head
                        val head = projectOrbitPoint(headAngle)
                        if ((head.second >= 0f) == drawFrontHalf) {
                            val depthBoost = if (head.second >= 0f) 1f else 0.4f
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        Color.White.copy(alpha = 0.95f * ribbonAlpha * depthBoost),
                                        accentHot.copy(alpha = 0.72f * ribbonAlpha * depthBoost),
                                        Color.Transparent
                                    ),
                                    center = head.first,
                                    radius = 12.dp.toPx()
                                ),
                                radius = 12.dp.toPx(),
                                center = head.first
                            )
                        }
                    }
                }

                // 4. BACK halves of orbital energy ribbons (behind the 3D crystal)
                drawOrbitalRibbonHalf(drawFrontHalf = false)

                // 5. Real 3D-shaded rotating faceted ruby octahedron prism
                val pitch = 0.16f
                val cosP = cos(pitch)
                val sinP = sin(pitch)
                val focal = 3.5f

                // Define vertices in model space: 0=top, 1=bottom, 2..5=equatorial diamond corners
                val rawModel = arrayOf(
                    Vec3(0f, -1.16f, 0f),
                    Vec3(0f, 1.08f, 0f),
                    Vec3(0.76f, 0f, 0f),
                    Vec3(0f, 0f, 0.76f),
                    Vec3(-0.76f, 0f, 0f),
                    Vec3(0f, 0f, -0.76f)
                )
                val cosY = cos(yaw)
                val sinY = sin(yaw)
                for (i in 0 until 6) {
                    val m = rawModel[i]
                    val rx = m.x * cosY + m.z * sinY
                    val rz = -m.x * sinY + m.z * cosY
                    val py = m.y * cosP - rz * sinP
                    val pz = m.y * sinP + rz * cosP
                    worldVerts[i] = Vec3(rx, py, pz)
                    val persp = focal / (focal - pz)
                    projectedVerts[i] = Offset(
                        cx + rx * prismRadius * persp,
                        cy + py * prismRadius * persp
                    )
                }

                // Key directional light from top-front-right
                val lx = 0.42f
                val ly = -0.56f
                val lz = 0.71f

                fun addFacet(i0: Int, i1: Int, i2: Int) {
                    val v0 = worldVerts[i0]
                    val v1 = worldVerts[i1]
                    val v2 = worldVerts[i2]
                    val ux = v1.x - v0.x
                    val uy = v1.y - v0.y
                    val uz = v1.z - v0.z
                    val vx = v2.x - v0.x
                    val vy = v2.y - v0.y
                    val vz = v2.z - v0.z

                    var nx = uy * vz - uz * vy
                    var ny = uz * vx - ux * vz
                    var nz = ux * vy - uy * vx

                    val centX = (v0.x + v1.x + v2.x) / 3f
                    val centY = (v0.y + v1.y + v2.y) / 3f
                    val centZ = (v0.z + v1.z + v2.z) / 3f
                    if (nx * centX + ny * centY + nz * centZ < 0f) {
                        nx = -nx
                        ny = -ny
                        nz = -nz
                    }
                    val len = sqrt(nx * nx + ny * ny + nz * nz).coerceAtLeast(1e-4f)
                    nx /= len
                    ny /= len
                    nz /= len

                    if (nz <= -0.02f) return // Back-face culled

                    val diffuse = max(0f, nx * lx + ny * ly + nz * lz)
                    // Specular highlight against half-vector of L and view(0,0,1)
                    val hx = lx * 0.56f
                    val hy = ly * 0.56f
                    val hz = (lz + 1f) * 0.56f
                    val spec = max(0f, nx * hx + ny * hy + nz * hz).pow(4.5f)
                    val isUpper = i0 == 0

                    val r = (88f + diffuse * 135f + spec * 45f + if (isUpper) 22f else 0f).coerceIn(0f, 255f) / 255f
                    val g = (8f + diffuse * 26f + spec * 125f).coerceIn(0f, 255f) / 255f
                    val b = (14f + diffuse * 32f + spec * 132f).coerceIn(0f, 255f) / 255f

                    faceList.add(
                        PrismFace(
                            p0 = projectedVerts[i0],
                            p1 = projectedVerts[i1],
                            p2 = projectedVerts[i2],
                            depth = centZ,
                            fillColor = Color(r, g, b, 0.96f),
                            rimAlpha = (0.35f + diffuse * 0.45f + spec * 0.3f).coerceIn(0.25f, 0.95f)
                        )
                    )
                }

                faceList.clear()
                addFacet(0, 2, 3)
                addFacet(0, 3, 4)
                addFacet(0, 4, 5)
                addFacet(0, 5, 2)
                addFacet(1, 3, 2)
                addFacet(1, 4, 3)
                addFacet(1, 5, 4)
                addFacet(1, 2, 5)
                faceList.sortBy { it.depth }

                // Soft under-crystal glow halo
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            accentHot.copy(alpha = 0.34f),
                            Color.Transparent
                        ),
                        center = Offset(cx, cy),
                        radius = prismRadius * 1.45f
                    ),
                    radius = prismRadius * 1.45f,
                    center = Offset(cx, cy)
                )

                // Chromatic RGB split prism echoes during glitch bursts
                if (glitchBurst > 0.06f) {
                    val splitDx = glitchShiftSign * glitchBurst * 11.dp.toPx()
                    val splitDy = glitchBurst * 2.5.dp.toPx()
                    faceList.forEach { face ->
                        reusableFacetPath.reset()
                        reusableFacetPath.moveTo(face.p0.x - splitDx, face.p0.y + splitDy)
                        reusableFacetPath.lineTo(face.p1.x - splitDx, face.p1.y + splitDy)
                        reusableFacetPath.lineTo(face.p2.x - splitDx, face.p2.y + splitDy)
                        reusableFacetPath.close()
                        drawPath(
                            reusableFacetPath,
                            color = glitchCyan.copy(alpha = 0.48f * glitchBurst),
                            style = Stroke(width = 1.7.dp.toPx(), cap = StrokeCap.Round)
                        )

                        reusableFacetPath.reset()
                        reusableFacetPath.moveTo(face.p0.x + splitDx, face.p0.y - splitDy)
                        reusableFacetPath.lineTo(face.p1.x + splitDx, face.p1.y - splitDy)
                        reusableFacetPath.lineTo(face.p2.x + splitDx, face.p2.y - splitDy)
                        reusableFacetPath.close()
                        drawPath(
                            reusableFacetPath,
                            color = accentHot.copy(alpha = 0.52f * glitchBurst),
                            style = Stroke(width = 1.7.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }
                }

                faceList.forEach { face ->
                    reusableFacetPath.reset()
                    reusableFacetPath.moveTo(face.p0.x, face.p0.y)
                    reusableFacetPath.lineTo(face.p1.x, face.p1.y)
                    reusableFacetPath.lineTo(face.p2.x, face.p2.y)
                    reusableFacetPath.close()
                    drawPath(reusableFacetPath, color = face.fillColor)
                    drawPath(
                        reusableFacetPath,
                        color = Color(0xFFFFD8DC).copy(alpha = face.rimAlpha * 0.65f),
                        style = Stroke(width = 1.25.dp.toPx(), cap = StrokeCap.Round)
                    )
                }

                // 6. Ignition spark starburst near the bottom prism apex (matching frame 00:02)
                if (burstPhase in 0.01f..0.99f) {
                    val flareAlpha = sin(burstPhase * PI.toFloat()).coerceIn(0f, 1f)
                    val flareCenter = Offset(cx, cy + prismRadius * 0.96f)
                    val flareRadius = prismRadius * (0.28f + 0.68f * burstPhase)
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.95f * flareAlpha),
                                accentHot.copy(alpha = 0.72f * flareAlpha),
                                Color.Transparent
                            ),
                            center = flareCenter,
                            radius = flareRadius
                        ),
                        radius = flareRadius,
                        center = flareCenter
                    )
                    for (rayIdx in 0 until 18) {
                        val ra = rayIdx * (2f * PI.toFloat() / 18f) + burstPhase * 0.3f
                        val innerLen = flareRadius * 0.18f
                        val outerLen = flareRadius * (if (rayIdx % 2 == 0) 1.05f else 0.72f)
                        drawLine(
                            color = Color(0xFFFFE5E7).copy(alpha = 0.85f * flareAlpha),
                            start = Offset(flareCenter.x + cos(ra) * innerLen, flareCenter.y + sin(ra) * innerLen),
                            end = Offset(flareCenter.x + cos(ra) * outerLen, flareCenter.y + sin(ra) * outerLen),
                            strokeWidth = (if (rayIdx % 2 == 0) 1.6f else 1.0f).dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    }
                }

                // 7. FRONT halves of orbital energy ribbons (sweeping over the 3D crystal)
                drawOrbitalRibbonHalf(drawFrontHalf = true)

                // 8. Clean subtle specular bottom fade
            }

            // Bottom centered brand reveal (clean consumer presentation, no cyberpunk telemetry)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 28.dp, vertical = 68.dp)
                    .alpha(titleReveal.value)
                    .scale(0.94f + 0.06f * titleReveal.value)
            ) {
                Text(
                    text = "Prism Solver",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontSize = 30.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        letterSpacing = (-0.4).sp
                    )
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Instant Quiz & Screen Helper",
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = accentHot,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.5.sp
                    )
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "Tap anywhere to continue",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 10.5.sp
                    )
                )
            }
        }
    }
}

private data class GuideStep(
    val badge: String,
    val title: String,
    val headline: String,
    val description: String,
    val featureTags: List<String>,
    val icon: ImageVector
)

private val GUIDE_STEPS = listOf(
    GuideStep(
        badge = "STEP 01 / 03",
        title = "Floating Pill",
        headline = "Solve MCQs, Math (½, Σ) & Match Over Any App",
        description = "Tap 'Start Solving' to float a small pill above your quiz. Whenever you tap the pill, Prism reads your screen and shows the right answer + explanation — without leaving your app.",
        featureTags = listOf("MCQs & Match", "Math ½ · Σ · ∫", "Only Reads on Tap"),
        icon = Icons.Filled.AutoAwesome
    ),
    GuideStep(
        badge = "STEP 02 / 03",
        title = "Auto-Math Vision & Offline Cache",
        headline = "Reads Math Symbols & Saves Answers Offline",
        description = "Fractions (1/2), Sigma (Σ), roots (√), and diagrams automatically use Math Vision so OCR mistakes never trip you up. Every solved answer is saved in your local Room cache for instant 0ms repeat hits.",
        featureTags = listOf("Auto-Math Vision", "0ms Offline Cache", "100% Free Fast AI"),
        icon = Icons.Filled.Layers
    ),
    GuideStep(
        badge = "STEP 03 / 03",
        title = "Google Forms & Themes",
        headline = "Paste Any Quiz Link or Transform Your UI",
        description = "Paste a Google Form link to solve all questions together and pin answers to your notification bar — or switch themes in Settings to rearrange the entire app layout.",
        featureTags = listOf("Google Forms", "Notification Pin", "5 Layout Themes"),
        icon = Icons.Filled.Description
    )
)

/**
 * Interactive 4-page feature showcase shown automatically to new users (and replayable in Settings).
 */
@Composable
fun NewUserFeatureGuideOverlay(
    visible: Boolean,
    onFinishGuide: (grantNotificationsTriggered: Boolean) -> Unit
) {
    if (!visible) return

    val context = LocalContext.current
    val palette = LocalWhiteGlassPalette.current
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { GUIDE_STEPS.size })

    val notifLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        onFinishGuide(true)
    }

    fun finishAndDeliverWelcome() {
        if (PrismEngagementNotificationManager.hasNotificationPermission(context)) {
            onFinishGuide(true)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            onFinishGuide(true)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.canvasTop.copy(alpha = 0.96f))
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 18.dp)
            .testTag("new_user_feature_guide_overlay"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 600.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Header + Skip
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    RedPrismLogoMark(modifier = Modifier.size(30.dp), animated = true)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Welcome to Prism Solver",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = palette.inkPrimary
                            ),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "10-Second Quick Tour",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = palette.inkSecondary,
                                fontSize = 11.5.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(Modifier.width(8.dp))

                Box(
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .clip(RoundedCornerShape(10.dp))
                        .background(palette.glassMedium)
                        .border(1.dp, palette.cardHairlineBorder, RoundedCornerShape(10.dp))
                        .clickable(role = Role.Button) { onFinishGuide(false) }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .testTag("btn_skip_feature_guide"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Skip",
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = palette.inkSecondary,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                }
            }

            // Interactive Pager Content
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(vertical = 14.dp)
            ) { page ->
                val step = GUIDE_STEPS[page]
                GuideFeatureCard(step = step, pageIndex = page)
            }

            // Bottom Step Dots + Next / Finish CTA
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(GUIDE_STEPS.size) { index ->
                        val active = pagerState.currentPage == index
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 4.dp)
                                .height(7.dp)
                                .width(if (active) 28.dp else 8.dp)
                                .clip(CircleShape)
                                .background(if (active) palette.accentSky else palette.cardHairlineBorder)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (pagerState.currentPage > 0) {
                        LivingSecondaryButton(
                            text = "Back",
                            onClick = {
                                scope.launch {
                                    pagerState.animateScrollToPage(pagerState.currentPage - 1)
                                }
                            },
                            testTag = "btn_guide_prev"
                        )
                    }
                    val isLast = pagerState.currentPage == GUIDE_STEPS.lastIndex
                    LivingPrimaryButton(
                        text = if (isLast) "Done — Open App" else "Next",
                        icon = null,
                        onClick = {
                            if (isLast) {
                                finishAndDeliverWelcome()
                            } else {
                                scope.launch {
                                    pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                }
                            }
                        },
                        testTag = if (isLast) "btn_guide_finish" else "btn_guide_next",
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GuideFeatureCard(
    step: GuideStep,
    pageIndex: Int
) {
    val palette = LocalWhiteGlassPalette.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(26.dp))
            .background(palette.glassStrong)
            .border(1.2.dp, palette.cardHairlineBorder, RoundedCornerShape(26.dp))
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Interactive Live Demo on Page 0 so new users immediately feel how it works
        if (pageIndex == 0) {
            LiveInteractiveDemoCard(compact = true)
        } else {
            // Animated Feature Preview Stage
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 132.dp, max = 176.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(palette.canvasTop)
                    .border(1.dp, palette.accentSky.copy(alpha = 0.35f), RoundedCornerShape(20.dp)),
                contentAlignment = Alignment.Center
            ) {
                InteractiveStepIllustration(pageIndex = pageIndex, icon = step.icon)
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(palette.glassTintedSky)
                        .border(1.dp, palette.accentSky.copy(alpha = 0.55f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = step.badge,
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = palette.accentSky,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 10.sp
                        ),
                        maxLines = 1
                    )
                }
                Text(
                    text = step.title,
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = palette.inkSecondary,
                        fontWeight = FontWeight.SemiBold
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }

            Text(
                text = step.headline,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontSize = 21.sp,
                    lineHeight = 26.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = palette.inkPrimary
                )
            )

            Text(
                text = step.description,
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = palette.inkSecondary,
                    lineHeight = 20.sp,
                    fontSize = 13.5.sp
                )
            )
        }

        // Feature Capability Pills (wraps cleanly on compact screens & large font scales)
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            step.featureTags.take(3).forEach { tag ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(9.dp))
                        .background(palette.glassMedium)
                        .border(1.dp, palette.cardHairlineBorder, RoundedCornerShape(9.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = tag,
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = palette.inkPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 10.5.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun InteractiveStepIllustration(
    pageIndex: Int,
    icon: ImageVector
) {
    val palette = LocalWhiteGlassPalette.current
    val inf = rememberInfiniteTransition(label = "guideStepAnim")
    val pulse by inf.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    when (pageIndex) {
        0 -> {
            // Dynamic Island capsule preview floating smoothly
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(50.dp))
                        .background(Color.Black)
                        .border(
                            width = 1.6.dp,
                            color = palette.accentSky.copy(alpha = 0.55f + 0.45f * pulse),
                            shape = RoundedCornerShape(50.dp)
                        )
                        .padding(horizontal = 20.dp, vertical = 10.dp)
                ) {
                    RedPrismLogoMark(modifier = Modifier.size(18.dp))
                    Text(
                        text = "B • Mitochondrion",
                        style = MaterialTheme.typography.labelLarge.copy(
                            color = Color(0xFFEEEEEE),
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF261215))
                            .padding(horizontal = 7.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "3.4s",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = palette.accentSkyBright,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
                Text(
                    text = "Tap = Solve  •  Drag anywhere  •  Swipe down = Switch style",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = palette.inkSecondary,
                        fontSize = 11.sp
                    ),
                    textAlign = TextAlign.Center
                )
            }
        }

        1 -> {
            // 3 Everyday Solving Styles
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                listOf(
                    Triple("Fast Answer", "~2s", 0.65f + 0.15f * pulse),
                    Triple("Photo & Diagrams", "Math", 0.78f + 0.14f * pulse),
                    Triple("Best Accuracy", "99%", 0.94f)
                ).forEach { (name, badge, fill) ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = name,
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = palette.inkPrimary,
                                fontWeight = FontWeight.SemiBold
                            ),
                            modifier = Modifier.width(126.dp)
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(palette.glassMedium)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(fill.coerceIn(0.2f, 1f))
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(palette.accentSky)
                            )
                        }
                        Text(
                            text = badge,
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = palette.accentSky,
                                fontWeight = FontWeight.ExtraBold
                            )
                        )
                    }
                }
            }
        }

        2 -> {
            // Google Forms & Pinned Notification Card Mock
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(palette.glassMedium)
                    .border(1.dp, palette.cardHairlineBorder, RoundedCornerShape(14.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "Pinned Status Bar • Google Form Key",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = palette.accentSky,
                        fontWeight = FontWeight.Bold
                    )
                )
                Text(
                    text = "(1-A|2-B|3-D|4-A)",
                    style = MaterialTheme.typography.titleLarge.copy(
                        color = palette.inkPrimary,
                        fontWeight = FontWeight.ExtraBold
                    )
                )
                Text(
                    text = "Copy   •   Solve Now   •   Stealth   •   Unpin",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = palette.inkSecondary,
                        fontWeight = FontWeight.SemiBold
                    )
                )
            }
        }

        else -> {
            // Stealth & Welcome Badge
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(palette.glassTintedSky)
                        .border(1.5.dp, palette.accentSky.copy(alpha = 0.6f + 0.4f * pulse), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = palette.inkPrimary,
                        modifier = Modifier.size(30.dp)
                    )
                }
                Text(
                    text = "Charging 92%  •  Pocket Buzz  •  Clean Pill",
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = palette.inkPrimary,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }
    }
}
