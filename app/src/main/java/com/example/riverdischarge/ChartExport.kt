package com.example.riverdischarge

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import android.graphics.Typeface
import android.widget.Toast
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

/** Upscale factor for the exported PNG so vector lines/text stay crisp when zoomed. */
private const val EXPORT_SCALE = 3f

/**
 * Renders a chart [draw] block (the same `DrawScope` code the on-screen `Canvas` uses) into a
 * standalone PNG-ready [Bitmap], with an optional [titleLines] header drawn above the chart.
 *
 * The chart is laid out at [chartWidthDp]×[chartHeightDp] (device-independent, via [density]); the
 * whole bitmap is then drawn through a ×[EXPORT_SCALE] canvas so everything — including the raw-px
 * `nativeCanvas` text inside the chart — scales up uniformly instead of being blurry-upsampled.
 */
internal fun renderChartToBitmap(
    density: Density,
    titleLines: List<String>,
    chartWidthDp: Float,
    chartHeightDp: Float,
    background: Color,
    titleColor: Color,
    draw: DrawScope.() -> Unit
): Bitmap {
    val widthPx = chartWidthDp * density.density
    val chartHeightPx = chartHeightDp * density.density

    val titlePaint = Paint().apply {
        color = titleColor.toArgb()
        textSize = 30f
        isAntiAlias = true
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    val sidePad = 16f
    val lineH = titlePaint.fontSpacing
    val titleH = if (titleLines.isEmpty()) 0f else lineH * titleLines.size + 16f
    val totalHPx = titleH + chartHeightPx

    val bitmap = Bitmap.createBitmap(
        (widthPx * EXPORT_SCALE).toInt().coerceAtLeast(1),
        (totalHPx * EXPORT_SCALE).toInt().coerceAtLeast(1),
        Bitmap.Config.ARGB_8888
    )
    val androidCanvas = AndroidCanvas(bitmap)
    androidCanvas.scale(EXPORT_SCALE, EXPORT_SCALE)
    androidCanvas.drawColor(background.toArgb())

    titleLines.forEachIndexed { i, line ->
        androidCanvas.drawText(line, sidePad, lineH * (i + 1) - 4f, titlePaint)
    }

    // Draw the chart below the title band, in its own coordinate space.
    androidCanvas.save()
    androidCanvas.translate(0f, titleH)
    val composeCanvas = Canvas(androidCanvas)
    CanvasDrawScope().draw(
        density = density,
        layoutDirection = LayoutDirection.Ltr,
        canvas = composeCanvas,
        size = Size(widthPx, chartHeightPx),
        block = draw
    )
    androidCanvas.restore()
    return bitmap
}

/**
 * Writes [bitmap] as a PNG into the app cache and fires the system share sheet (no storage
 * permission required). The user can save it to the gallery, send it to a messenger, etc.
 */
internal fun shareBitmap(context: Context, bitmap: Bitmap, baseName: String) {
    val result = runCatching {
        val dir = File(context.cacheDir, "shared_charts").apply { mkdirs() }
        val safe = baseName.replace(Regex("[^A-Za-z0-9_-]"), "_")
        val file = File(dir, "${safe}_${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val share = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(
            Intent.createChooser(share, "Поделиться графиком")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
    if (result.isFailure) {
        Toast.makeText(context, "Не удалось подготовить картинку", Toast.LENGTH_SHORT).show()
    }
}

/** Small outlined button used under each chart to share it as a PNG. */
@Composable
internal fun ShareChartButton(text: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick) { Text(text) }
}
