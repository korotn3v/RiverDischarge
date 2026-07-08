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
private const val EXPORT_SCALE = 6f

/**
 * Nominal print resolution of the export coordinate space. 1 picture-cm equals [EXPORT_PX_PER_CM]
 * base px (×[EXPORT_SCALE] in the final bitmap, i.e. 600 dpi when printed at actual size).
 */
private const val EXPORT_DPI = 100f
internal const val EXPORT_PX_PER_CM = EXPORT_DPI / 2.54f

/** Density used for all exported charts, so dp-based paddings are device-independent. */
internal val ExportChartDensity = Density(EXPORT_DPI / 160f)

/**
 * Fixed physical scale of an exported chart: metres of river per centimetre of picture.
 * Width and depth scales differ (classic ~10× vertical exaggeration) for readability.
 */
internal data class ExportScale(val metersPerCmWidth: Double, val metersPerCmDepth: Double)

/** Picks one of four scale gradations from the wetted width, so narrow and wide rivers both fit. */
internal fun exportScaleFor(wettedWidth: Double): ExportScale = when {
    wettedWidth <= 20.0 -> ExportScale(2.0, 0.2)
    wettedWidth <= 60.0 -> ExportScale(5.0, 0.5)
    wettedWidth <= 150.0 -> ExportScale(10.0, 1.0)
    else -> ExportScale(20.0, 2.0)
}

/** Velocity-axis scale (m/s per picture-cm) for the эпюры, graded by the survey's max velocity. */
internal fun velocityMetersPerCm(vMax: Double): Double = when {
    vMax <= 0.5 -> 0.1
    vMax <= 1.5 -> 0.25
    else -> 0.5
}

/** Metres → export px along a fixed scale axis. */
internal fun metersToExportPx(meters: Double, metersPerCm: Double): Float =
    (meters / metersPerCm).toFloat() * EXPORT_PX_PER_CM

/**
 * Renders a chart [draw] block (the same `DrawScope` code the on-screen `Canvas` uses) into a
 * standalone PNG-ready [Bitmap] — just the chart, no caption band.
 *
 * The chart is laid out at [chartWidthPx]×[chartHeightPx] in the [ExportChartDensity] coordinate
 * space (callers size it from the fixed metres-per-cm scale); the whole bitmap is then drawn
 * through a ×[EXPORT_SCALE] canvas so everything — including the `nativeCanvas` text inside the
 * chart — scales up uniformly instead of being blurry-upsampled.
 */
internal fun renderChartToBitmap(
    chartWidthPx: Float,
    chartHeightPx: Float,
    background: Color,
    draw: DrawScope.() -> Unit
): Bitmap {
    val bitmap = Bitmap.createBitmap(
        (chartWidthPx * EXPORT_SCALE).toInt().coerceAtLeast(1),
        (chartHeightPx * EXPORT_SCALE).toInt().coerceAtLeast(1),
        Bitmap.Config.ARGB_8888
    )
    val androidCanvas = AndroidCanvas(bitmap)
    androidCanvas.scale(EXPORT_SCALE, EXPORT_SCALE)
    androidCanvas.drawColor(background.toArgb())

    val composeCanvas = Canvas(androidCanvas)
    CanvasDrawScope().draw(
        density = ExportChartDensity,
        layoutDirection = LayoutDirection.Ltr,
        canvas = composeCanvas,
        size = Size(chartWidthPx, chartHeightPx),
        block = draw
    )
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
