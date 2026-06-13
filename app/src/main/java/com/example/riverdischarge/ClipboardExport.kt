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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.nativeCanvas
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
import kotlin.math.roundToInt

internal fun copyTextToClipboard(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
    Toast.makeText(context, "«$label» скопировано", Toast.LENGTH_SHORT).show()
}

internal fun buildSummaryClipboardText(survey: ParsedSurvey, result: CalculationOutput): String = buildString {
    appendLine("Показатель	Значение")
    appendLine("Замер	${survey.name}")
    appendLine("Река	${survey.river}")
    appendLine("Дата	${survey.dateText}")
    appendLine("Начальный берег	${survey.section.startSide.title}")
    appendLine("Коэффициент левого берега	${formatNumber(survey.banks.leftCoefficient)}")
    appendLine("Коэффициент правого берега	${formatNumber(survey.banks.rightCoefficient)}")
    appendLine("Суммарный расход Q, м³/с	${formatNumber(result.totalDischarge)}")
    appendLine("Площадь живого сечения F, м²	${formatNumber(result.wettedArea)}")
    appendLine("Средняя скорость Vср, м/с	${formatNumber(result.meanVelocity)}")
    appendLine("Максимальная глубина, м	${formatNumber(result.maxDepth)}")
}

internal fun buildProfileTableClipboardText(section: SectionData): String = buildString {
    appendLine("№	Расстояние x, м	Глубина h, м")
    section.points.forEachIndexed { index, point ->
        appendLine("${index + 1}	${formatNumber(point.distance)}	${formatNumber(point.depth)}")
    }
}

internal fun buildVelocityTableClipboardText(verticals: List<VelocityVertical>): String = buildString {
    appendLine("Вертикаль	x, м	h, м	Схема	Vпов, м/с	V0.2, м/с	V0.4, м/с	V0.6, м/с	V0.8, м/с	Vдно, м/с	Vср, м/с")
    verticals.forEachIndexed { index, vertical ->
        fun cell(point: VelocityPoint) = vertical.velocities[point]?.let(::formatNumber).orEmpty()
        appendLine(
            listOf(
                "V${index + 1}",
                formatNumber(vertical.distance),
                formatNumber(vertical.localDepth),
                vertical.measurementMethod.schemeLabel,
                cell(VelocityPoint.SURFACE),
                cell(VelocityPoint.P02),
                cell(VelocityPoint.P04),
                cell(VelocityPoint.P06),
                cell(VelocityPoint.P08),
                cell(VelocityPoint.BOTTOM),
                formatNumber(vertical.meanVelocity)
            ).joinToString("	")
        )
    }
}

internal fun buildSegmentsClipboardText(result: CalculationOutput): String = buildString {
    appendLine("Участок	x от, м	x до, м	Ширина, м	Площадь, м²	Скорость, м/с	Частный расход, м³/с")
    result.segments.forEach { segment ->
        appendLine(
            listOf(
                segment.label,
                formatNumber(segment.xFrom),
                formatNumber(segment.xTo),
                formatNumber(segment.width),
                formatNumber(segment.area),
                formatNumber(segment.velocity),
                formatNumber(segment.discharge)
            ).joinToString("	")
        )
    }
}

internal fun buildFullClipboardExport(survey: ParsedSurvey, result: CalculationOutput): String = buildString {
    appendLine("СВОДКА")
    append(buildSummaryClipboardText(survey, result))
    appendLine()
    appendLine("ПРОФИЛЬ РУСЛА")
    append(buildProfileTableClipboardText(survey.section))
    appendLine()
    appendLine("ВЕРТИКАЛИ СКОРОСТИ")
    append(buildVelocityTableClipboardText(survey.velocityVerticals))
    appendLine()
    appendLine("УЧАСТКИ РАСХОДА")
    append(buildSegmentsClipboardText(result))
}
