package com.example.riverdischarge

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Paint
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.riverdischarge.ui.theme.RiverDischargeTheme
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/** Depth axis max shared between the cross-section and the velocity эпюры in the exported images. */
internal fun sharedDepthMax(section: SectionData, velocityVerticals: List<VelocityVertical>): Double {
    val sectionMax = section.profilePoints.maxOfOrNull { it.depth } ?: 0.0
    val verticalMax = velocityVerticals.maxOfOrNull { it.localDepth } ?: 0.0
    return max(sectionMax, verticalMax).coerceAtLeast(0.1)
}

// Chart insets shared between on-screen drawing and export-bitmap sizing (dp, density-resolved).
internal val SectionPadLeft = 72.dp
internal val SectionPadRight = 18.dp
internal val SectionPadTop = 30.dp
internal val SectionPadBottom = 64.dp
internal val EpurePadLeft = 56.dp
internal val EpurePadRight = 16.dp
internal val EpurePadTop = 26.dp
internal val EpurePadBottom = 50.dp

/** Axis tick positions: round multiples of [stepM] when a fixed scale is used, quarters otherwise. */
private fun axisTicks(maxValue: Double, stepM: Double?): List<Double> =
    if (stepM != null && stepM > 0.0) {
        generateSequence(0.0) { it + stepM }.takeWhile { it <= maxValue + 1e-9 }.toList()
    } else {
        (0..4).map { maxValue * it / 4.0 }
    }

internal fun DrawScope.drawSectionProfileChart(
    section: SectionData,
    velocityVerticals: List<VelocityVertical>,
    maxDepthOverride: Double?,
    primary: Color,
    secondary: Color,
    outline: Color,
    surfaceVariant: Color,
    waterLineColor: Color,
    depthTickStepM: Double? = null
) {
    val profile = section.profilePoints
    val maxDepthValue = maxDepthOverride ?: (profile.maxOfOrNull { it.depth } ?: 1.0).coerceAtLeast(0.1)
    val xMin = section.startEdgeDistance
    val xMax = section.endEdgeDistance
    val depthTicks = axisTicks(maxDepthValue, depthTickStepM)
    val pointLabels = profile.drop(1).dropLast(1)

    val leftPad = SectionPadLeft.toPx()
    val rightPad = SectionPadRight.toPx()
    val topPad = SectionPadTop.toPx()
    val bottomPad = SectionPadBottom.toPx()
    val width = size.width - leftPad - rightPad
    val height = size.height - topPad - bottomPad
    val left = leftPad
    val top = topPad
    val bottom = top + height
    val right = left + width
    // All text/marker sizes are dp-based so the chart keeps its proportions at any density —
    // both on screen and in the fixed-scale export (whose density models print centimetres).
    val axisPaint = Paint().apply {
        color = android.graphics.Color.argb(255, 90, 90, 90)
        textSize = 10.dp.toPx()
        textAlign = Paint.Align.RIGHT
        isAntiAlias = true
    }
    val labelPaint = Paint().apply {
        color = android.graphics.Color.argb(255, 70, 70, 70)
        textSize = 9.dp.toPx()
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
    }
    val smallPaint = Paint().apply {
        color = android.graphics.Color.argb(255, 70, 70, 70)
        textSize = 8.dp.toPx()
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
    }
    val labelGap = 4.dp.toPx()

    fun mapX(x: Double): Float {
        val ratio = ((x - xMin) / (xMax - xMin)).toFloat().coerceIn(0f, 1f)
        return left + ratio * width
    }

    fun mapY(depth: Double): Float {
        val ratio = (depth / maxDepthValue).toFloat().coerceIn(0f, 1f)
        return top + ratio * height
    }

    depthTicks.forEach { tick ->
        val y = mapY(tick)
        drawLine(
            color = outline.copy(alpha = 0.35f),
            start = Offset(left, y),
            end = Offset(right, y),
            strokeWidth = 0.5.dp.toPx()
        )
        drawContext.canvas.nativeCanvas.drawText(formatNumber(tick), left - 3.dp.toPx(), y + 3.dp.toPx(), axisPaint)
    }

    // Dense profiles make neighbouring labels collide: draw a label only when it clears the
    // previous one (grid lines are still drawn for every tick).
    val xTicks = buildList {
        add(section.startEdgeDistance)
        addAll(pointLabels.map { it.distance })
        add(section.endEdgeDistance)
    }
    var lastLabelEnd = Float.NEGATIVE_INFINITY
    xTicks.forEach { tick ->
        val x = mapX(tick)
        drawLine(
            color = outline.copy(alpha = 0.35f),
            start = Offset(x, top),
            end = Offset(x, bottom),
            strokeWidth = 0.4.dp.toPx()
        )
        val text = formatNumber(tick)
        val halfW = labelPaint.measureText(text) / 2f
        if (x - halfW >= lastLabelEnd + labelGap) {
            drawContext.canvas.nativeCanvas.drawText(text, x, bottom + 9.dp.toPx(), labelPaint)
            lastLabelEnd = x + halfW
        }
    }

    drawLine(color = outline, start = Offset(left, top), end = Offset(left, bottom), strokeWidth = 0.8.dp.toPx())
    drawLine(color = waterLineColor, start = Offset(left, top), end = Offset(right, top), strokeWidth = 0.8.dp.toPx())
    drawLine(color = outline, start = Offset(left, bottom), end = Offset(right, bottom), strokeWidth = 0.8.dp.toPx())

    val fillPath = Path().apply {
        moveTo(mapX(profile.first().distance), top)
        profile.forEach { point ->
            lineTo(mapX(point.distance), mapY(point.depth))
        }
        lineTo(mapX(profile.last().distance), top)
        close()
    }
    drawPath(path = fillPath, color = surfaceVariant.copy(alpha = 0.55f), style = Fill)

    val linePath = Path().apply {
        profile.forEachIndexed { index, point ->
            val x = mapX(point.distance)
            val y = mapY(point.depth)
            if (index == 0) moveTo(x, y) else lineTo(x, y)
        }
    }
    drawPath(path = linePath, color = primary, style = Stroke(width = 1.7.dp.toPx(), cap = StrokeCap.Round))

    pointLabels.forEachIndexed { index, point ->
        val x = mapX(point.distance)
        val y = mapY(point.depth)
        drawLine(
            color = outline.copy(alpha = 0.55f),
            start = Offset(x, top),
            end = Offset(x, y),
            strokeWidth = 0.7.dp.toPx()
        )
        drawCircle(color = primary, radius = 2.dp.toPx(), center = Offset(x, y))
    }

    // Second bottom row — "Урез" at both edges plus point numbers, thinned the same way.
    val indexRowY = bottom + 18.dp.toPx()
    lastLabelEnd = Float.NEGATIVE_INFINITY
    val indexRow = buildList {
        add(section.startEdgeDistance to "Урез")
        pointLabels.forEachIndexed { index, point -> add(point.distance to (index + 1).toString()) }
        add(section.endEdgeDistance to "Урез")
    }
    indexRow.forEach { (distance, text) ->
        val x = mapX(distance)
        val halfW = smallPaint.measureText(text) / 2f
        if (x - halfW >= lastLabelEnd + labelGap) {
            drawContext.canvas.nativeCanvas.drawText(text, x, indexRowY, smallPaint)
            lastLabelEnd = x + halfW
        }
    }

    velocityVerticals.forEachIndexed { index, vertical ->
        val x = mapX(vertical.distance)
        val bottomY = mapY(vertical.localDepth)
        drawLine(
            color = secondary,
            start = Offset(x, top),
            end = Offset(x, bottomY),
            strokeWidth = 1.dp.toPx()
        )
        vertical.measuredDepthOffsets.forEach { offset ->
            drawCircle(color = secondary, radius = 2.3.dp.toPx(), center = Offset(x, mapY(offset)))
        }
        drawContext.canvas.nativeCanvas.drawText("V${index + 1}", x, top - 3.dp.toPx(), smallPaint)
    }

    val axisTitlePaint = Paint(axisPaint).apply { textAlign = Paint.Align.LEFT }
    drawContext.canvas.nativeCanvas.drawText("Глубина, м", 2.dp.toPx(), top - 3.dp.toPx(), axisTitlePaint)
    drawContext.canvas.nativeCanvas.drawText("Расстояния по линейке, м", (left + right) / 2f, size.height - 3.dp.toPx(), labelPaint)
}

@Composable
internal fun SectionProfileCard(section: SectionData, velocityVerticals: List<VelocityVertical>) {
    val profile = section.profilePoints
    val pointLabels = profile.drop(1).dropLast(1)

    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.tertiary
    val outline = MaterialTheme.colorScheme.outline
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    val waterLineColor = MaterialTheme.colorScheme.onSurfaceVariant
    val surface = MaterialTheme.colorScheme.surface
    val context = LocalContext.current

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Поперечное сечение русла", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(section.startSide.title)
                Text(
                    if (section.startSide == BankSide.LEFT) BankSide.RIGHT.title else BankSide.LEFT.title,
                    textAlign = TextAlign.End
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(320.dp)
                    .border(1.dp, outline, RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(8.dp)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawSectionProfileChart(
                        section = section,
                        velocityVerticals = velocityVerticals,
                        maxDepthOverride = null,
                        primary = primary,
                        secondary = secondary,
                        outline = outline,
                        surfaceVariant = surfaceVariant,
                        waterLineColor = waterLineColor
                    )
                }
            }

            Text(
                "Схема похожа на полевой поперечник: сверху урез воды, снизу расстояния по линейке, на профиле — точки глубины, а V1, V2… показывают только те вертикали, где реально мерилась скорость.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            ShareChartButton(text = "Поделиться поперечником") {
                // Fixed physical scale: the plot area is sized from the river itself, so every
                // exported поперечник of a given width class shares the same метров-в-сантиметре.
                val exportScale = exportScaleFor(section.wettedWidth)
                val depthMax = sharedDepthMax(section, velocityVerticals)
                val plotW = metersToExportPx(section.wettedWidth, exportScale.metersPerCmWidth)
                val plotH = metersToExportPx(depthMax, exportScale.metersPerCmDepth)
                val (padW, padH) = with(ExportChartDensity) {
                    (SectionPadLeft.toPx() + SectionPadRight.toPx()) to (SectionPadTop.toPx() + SectionPadBottom.toPx())
                }
                renderChartToBitmap(
                    chartWidthPx = plotW + padW,
                    chartHeightPx = plotH + padH,
                    background = surface
                ) {
                    drawSectionProfileChart(
                        section = section,
                        velocityVerticals = velocityVerticals,
                        maxDepthOverride = depthMax,
                        primary = primary,
                        secondary = secondary,
                        outline = outline,
                        surfaceVariant = surfaceVariant,
                        waterLineColor = waterLineColor,
                        depthTickStepM = exportScale.metersPerCmDepth
                    )
                }.let { bitmap -> shareBitmap(context, bitmap, "poperechnik") }
            }

            SimpleDataTable(
                title = "Точки профиля",
                header = listOf("№", "x, м", "h, м"),
                rows = pointLabels.mapIndexed { index, point ->
                    listOf((index + 1).toString(), formatNumber(point.distance), formatNumber(point.depth))
                }
            )

            if (velocityVerticals.isNotEmpty()) {
                SimpleDataTable(
                    title = "Вертикали скорости",
                    header = listOf("Вертикаль", "x, м", "h, м", "Схема"),
                    rows = velocityVerticals.mapIndexed { index, vertical ->
                        listOf(
                            "V${index + 1}",
                            formatNumber(vertical.distance),
                            formatNumber(vertical.localDepth),
                            vertical.measurementMethod.schemeLabel
                        )
                    }
                )
            }
        }
    }
}

/**
 * (depth, v) profile of one vertical ready to draw as an эпюра.
 * [measured] are the real readings (sorted surface→bottom); [surfacePoint]/[bedPoint] are the
 * physical dashed extensions toward the free surface (flattens to the topmost reading) and toward
 * the bed (velocity decays because of friction). Either is null when that end was actually measured.
 */
internal data class EpureProfile(
    val measured: List<Pair<Double, Double>>,
    val surfacePoint: Pair<Double, Double>?,
    val bedPoint: Pair<Double, Double>?
)

internal fun buildEpureProfile(vertical: VelocityVertical): EpureProfile {
    val h = vertical.localDepth
    val measured = vertical.velocities.entries
        .map { (point, v) -> point.fraction * h to v }
        .sortedBy { it.first }
    val eps = h * 1e-3
    val top = measured.first()
    val bottom = measured.last()

    // Near the surface the profile flattens out, so hold the topmost reading up to depth 0.
    val surfacePoint = if (top.first > eps) 0.0 to top.second else null

    // Near the bed friction drags the flow to a small value: continue the lowest measured gradient
    // down to the bed, clamped so it only decreases (and never below zero).
    val bedPoint = if (bottom.first < h - eps) {
        val bedV = if (measured.size >= 2) {
            val a = measured[measured.size - 2]
            val b = measured[measured.size - 1]
            val slope = if (b.first != a.first) (b.second - a.second) / (b.first - a.first) else 0.0
            (b.second + slope * (h - b.first)).coerceIn(0.0, b.second)
        } else {
            bottom.second * 0.6
        }
        h to bedV
    } else null

    return EpureProfile(measured, surfacePoint, bedPoint)
}

/** Smooth (Catmull-Rom) path through pixel-space points; straight line for fewer than 3 points. */
internal fun smoothPathThrough(points: List<Offset>): Path {
    val path = Path()
    if (points.isEmpty()) return path
    path.moveTo(points[0].x, points[0].y)
    if (points.size < 3) {
        for (i in 1 until points.size) path.lineTo(points[i].x, points[i].y)
        return path
    }
    for (i in 0 until points.size - 1) {
        val p0 = points[if (i == 0) 0 else i - 1]
        val p1 = points[i]
        val p2 = points[i + 1]
        val p3 = points[if (i + 2 < points.size) i + 2 else i + 1]
        val c1 = Offset(p1.x + (p2.x - p0.x) / 6f, p1.y + (p2.y - p0.y) / 6f)
        val c2 = Offset(p2.x - (p3.x - p1.x) / 6f, p2.y - (p3.y - p1.y) / 6f)
        path.cubicTo(c1.x, c1.y, c2.x, c2.y, p2.x, p2.y)
    }
    return path
}

internal fun DrawScope.drawVelocityProfileChart(
    vertical: VelocityVertical,
    axisDepthMax: Double,
    vMax: Double,
    primary: Color,
    pointColor: Color,
    outline: Color,
    waterLineColor: Color,
    depthTickStepM: Double? = null,
    vTickStep: Double? = null
) {
    val profile = buildEpureProfile(vertical)
    val depthTicks = axisTicks(axisDepthMax, depthTickStepM)
    val vTicks = axisTicks(vMax, vTickStep)

    val leftPad = EpurePadLeft.toPx()
    val rightPad = EpurePadRight.toPx()
    val topPad = EpurePadTop.toPx()
    val bottomPad = EpurePadBottom.toPx()
    val width = size.width - leftPad - rightPad
    val height = size.height - topPad - bottomPad
    val left = leftPad
    val top = topPad
    val bottom = top + height
    val right = left + width

    // dp-based sizing — see drawSectionProfileChart.
    val axisPaint = Paint().apply {
        color = android.graphics.Color.argb(255, 90, 90, 90)
        textSize = 9.dp.toPx()
        textAlign = Paint.Align.RIGHT
        isAntiAlias = true
    }
    val labelPaint = Paint().apply {
        color = android.graphics.Color.argb(255, 70, 70, 70)
        textSize = 9.dp.toPx()
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
    }
    val labelGap = 4.dp.toPx()

    fun mapX(v: Double): Float = left + (v / vMax).toFloat().coerceIn(0f, 1f) * width
    fun mapY(depth: Double): Float = top + (depth / axisDepthMax).toFloat().coerceIn(0f, 1f) * height

    // Depth grid + labels (0 at surface on top, axisDepthMax at the bed on bottom).
    depthTicks.forEach { tick ->
        val y = mapY(tick)
        drawLine(
            color = outline.copy(alpha = 0.30f),
            start = Offset(left, y),
            end = Offset(right, y),
            strokeWidth = 0.5.dp.toPx()
        )
        drawContext.canvas.nativeCanvas.drawText(formatNumber(tick), left - 3.dp.toPx(), y + 3.dp.toPx(), axisPaint)
    }
    // Velocity grid + labels along the bottom, thinned when they would collide.
    var lastLabelEnd = Float.NEGATIVE_INFINITY
    vTicks.forEach { tick ->
        val x = mapX(tick)
        drawLine(
            color = outline.copy(alpha = 0.30f),
            start = Offset(x, top),
            end = Offset(x, bottom),
            strokeWidth = 0.4.dp.toPx()
        )
        val text = formatNumber(tick)
        val halfW = labelPaint.measureText(text) / 2f
        if (x - halfW >= lastLabelEnd + labelGap) {
            drawContext.canvas.nativeCanvas.drawText(text, x, bottom + 9.dp.toPx(), labelPaint)
            lastLabelEnd = x + halfW
        }
    }

    // Frame: velocity axis (left), surface line (top), bed line (bottom).
    drawLine(color = outline, start = Offset(left, top), end = Offset(left, bottom), strokeWidth = 0.8.dp.toPx())
    drawLine(color = waterLineColor, start = Offset(left, top), end = Offset(right, top), strokeWidth = 0.8.dp.toPx())
    drawLine(color = outline, start = Offset(left, bottom), end = Offset(right, bottom), strokeWidth = 0.8.dp.toPx())

    val measuredOffsets = profile.measured.map { (depth, v) -> Offset(mapX(v), mapY(depth)) }

    val dashed = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))
    profile.surfacePoint?.let { (depth, v) ->
        drawLine(
            color = primary.copy(alpha = 0.7f),
            start = Offset(mapX(v), mapY(depth)),
            end = measuredOffsets.first(),
            strokeWidth = 1.3.dp.toPx(),
            pathEffect = dashed
        )
    }
    profile.bedPoint?.let { (depth, v) ->
        drawLine(
            color = primary.copy(alpha = 0.7f),
            start = measuredOffsets.last(),
            end = Offset(mapX(v), mapY(depth)),
            strokeWidth = 1.3.dp.toPx(),
            pathEffect = dashed
        )
    }

    // Solid smooth curve through the actual readings.
    if (measuredOffsets.size >= 2) {
        drawPath(
            path = smoothPathThrough(measuredOffsets),
            color = primary,
            style = Stroke(width = 1.7.dp.toPx(), cap = StrokeCap.Round)
        )
    }

    // Each reading: a thin horizontal "velocity stick" from the axis plus the point itself.
    profile.measured.forEachIndexed { i, (depth, v) ->
        val y = mapY(depth)
        drawLine(
            color = pointColor.copy(alpha = 0.45f),
            start = Offset(left, y),
            end = Offset(mapX(v), y),
            strokeWidth = 0.7.dp.toPx()
        )
        drawCircle(color = pointColor, radius = 2.3.dp.toPx(), center = measuredOffsets[i])
    }

    val axisTitlePaint = Paint(axisPaint).apply { textAlign = Paint.Align.LEFT }
    drawContext.canvas.nativeCanvas.drawText("h, м", 2.dp.toPx(), top - 3.dp.toPx(), axisTitlePaint)
    drawContext.canvas.nativeCanvas.drawText("v, м/с", (left + right) / 2f, size.height - 2.dp.toPx(), labelPaint)
    drawContext.canvas.nativeCanvas.drawText("дно", right - 2.dp.toPx(), bottom - 3.dp.toPx(), labelPaint)
}

@Composable
internal fun VelocityProfilesCard(
    verticals: List<VelocityVertical>,
    sectionMaxDepth: Double = 0.0,
    exportScale: ExportScale = exportScaleFor(0.0)
) {
    // Shared export scale so every saved эпюра is directly comparable (same depth & velocity axes).
    val exportDepthMax = max(
        sectionMaxDepth,
        verticals.maxOfOrNull { it.localDepth } ?: 0.0
    ).coerceAtLeast(0.1)
    val exportVMax = ((verticals.flatMap { it.velocities.values }.maxOrNull() ?: 0.0) * 1.15)
        .coerceAtLeast(0.05)

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Эпюры скорости", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(
                "Распределение скорости по глубине вертикали: вправо — скорость v, вниз — глубина (сверху поверхность, снизу дно). Сплошная линия идёт по замерам, пунктир — физичная достройка к поверхности (выполаживание) и ко дну (спад из-за трения о ложе).",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            verticals.forEachIndexed { index, vertical ->
                VelocityProfileChart(
                    index = index,
                    vertical = vertical,
                    exportDepthMax = exportDepthMax,
                    exportVMax = exportVMax,
                    exportScale = exportScale
                )
            }
        }
    }
}

@Composable
internal fun VelocityProfileChart(
    index: Int,
    vertical: VelocityVertical,
    exportDepthMax: Double,
    exportVMax: Double,
    exportScale: ExportScale
) {
    val profile = buildEpureProfile(vertical)
    val h = vertical.localDepth
    // On-screen each эпюра auto-scales to itself; the export uses the shared scale passed in.
    val vMax = ((profile.measured.maxOfOrNull { it.second } ?: 0.0) * 1.15).coerceAtLeast(0.05)

    val primary = MaterialTheme.colorScheme.primary
    val pointColor = MaterialTheme.colorScheme.tertiary
    val outline = MaterialTheme.colorScheme.outline
    val waterLineColor = MaterialTheme.colorScheme.onSurfaceVariant
    val surface = MaterialTheme.colorScheme.surface
    val context = LocalContext.current
    val titleLine = "Вертикаль ${index + 1} · x=${formatNumber(vertical.distance)} м · h=${formatNumber(h)} м · Vср=${formatNumber(vertical.meanVelocity)} м/с"

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            titleLine,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp)
                .border(1.dp, outline, RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(8.dp)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawVelocityProfileChart(
                    vertical = vertical,
                    axisDepthMax = h,
                    vMax = vMax,
                    primary = primary,
                    pointColor = pointColor,
                    outline = outline,
                    waterLineColor = waterLineColor
                )
            }
        }
        ShareChartButton(text = "Поделиться эпюрой") {
            // Fixed physical scale shared by all эпюры of the survey: depth uses the same
            // metres-per-cm as the exported поперечник, velocity gets its own graded scale.
            val vPerCm = velocityMetersPerCm(exportVMax)
            val plotW = metersToExportPx(exportVMax, vPerCm)
            val plotH = metersToExportPx(exportDepthMax, exportScale.metersPerCmDepth)
            val (padW, padH) = with(ExportChartDensity) {
                (EpurePadLeft.toPx() + EpurePadRight.toPx()) to (EpurePadTop.toPx() + EpurePadBottom.toPx())
            }
            renderChartToBitmap(
                chartWidthPx = plotW + padW,
                chartHeightPx = plotH + padH,
                background = surface
            ) {
                drawVelocityProfileChart(
                    vertical = vertical,
                    axisDepthMax = exportDepthMax,
                    vMax = exportVMax,
                    primary = primary,
                    pointColor = pointColor,
                    outline = outline,
                    waterLineColor = waterLineColor,
                    depthTickStepM = exportScale.metersPerCmDepth,
                    vTickStep = vPerCm
                )
            }.let { bitmap -> shareBitmap(context, bitmap, "epura_v${index + 1}") }
        }
    }
}
