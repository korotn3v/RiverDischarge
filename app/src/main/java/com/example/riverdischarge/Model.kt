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
import kotlinx.serialization.Serializable

enum class BankSide(val title: String) {
    LEFT("Левый берег"),
    RIGHT("Правый берег")
}

enum class BankType(val title: String, val defaultCoefficient: Double) {
    GENTLE_ZERO_DEPTH("Пологий берег, h=0 у уреза", 0.70),
    STEEP_OR_ROUGH("Обрывистый берег / неровная стенка", 0.80),
    VERTICAL_SMOOTH("Вертикальная гладкая стенка", 0.90),
    DEAD_SPACE("Заросший берег / застой / мёртвое пространство", 0.50)
}

/** A depth at which the current meter is lowered, expressed as a fraction of the local depth. */
enum class VelocityPoint(val fraction: Double, val label: String, val short: String) {
    SURFACE(0.0, "поверхности", "пов"),
    P02(0.2, "0.2h", "0.2h"),
    P04(0.4, "0.4h", "0.4h"),
    P06(0.6, "0.6h", "0.6h"),
    P08(0.8, "0.8h", "0.8h"),
    BOTTOM(1.0, "дне", "дно")
}

enum class MeasurementMethod(val title: String, val points: List<VelocityPoint>) {
    SINGLE_06("Одноточечный (0.6h)", listOf(VelocityPoint.P06)),
    TWO_POINT("Двухточечный (0.2h, 0.8h)", listOf(VelocityPoint.P02, VelocityPoint.P08)),
    THREE_POINT("Трёхточечный (0.2h, 0.6h, 0.8h)", listOf(VelocityPoint.P02, VelocityPoint.P06, VelocityPoint.P08)),
    FIVE_POINT(
        "Пятиточечный (пов, 0.2h, 0.6h, 0.8h, дно)",
        listOf(VelocityPoint.SURFACE, VelocityPoint.P02, VelocityPoint.P06, VelocityPoint.P08, VelocityPoint.BOTTOM)
    ),
    SIX_POINT(
        "Шеститочечный (пов, 0.2h, 0.4h, 0.6h, 0.8h, дно)",
        listOf(VelocityPoint.SURFACE, VelocityPoint.P02, VelocityPoint.P04, VelocityPoint.P06, VelocityPoint.P08, VelocityPoint.BOTTOM)
    );

    val schemeLabel: String get() = points.joinToString("/") { it.short }

    /** Mean vertical velocity from the velocities measured at this method's points. */
    fun meanVelocity(v: Map<VelocityPoint, Double>): Double = when (this) {
        SINGLE_06 -> v.getValue(VelocityPoint.P06)
        TWO_POINT -> 0.5 * (v.getValue(VelocityPoint.P02) + v.getValue(VelocityPoint.P08))
        THREE_POINT -> 0.25 * (v.getValue(VelocityPoint.P02) + 2.0 * v.getValue(VelocityPoint.P06) + v.getValue(VelocityPoint.P08))
        FIVE_POINT -> 0.1 * (
            v.getValue(VelocityPoint.SURFACE) + 3.0 * v.getValue(VelocityPoint.P02) +
                3.0 * v.getValue(VelocityPoint.P06) + 2.0 * v.getValue(VelocityPoint.P08) + v.getValue(VelocityPoint.BOTTOM)
            )
        SIX_POINT -> 0.1 * (
            v.getValue(VelocityPoint.SURFACE) + 2.0 * v.getValue(VelocityPoint.P02) + 2.0 * v.getValue(VelocityPoint.P04) +
                2.0 * v.getValue(VelocityPoint.P06) + 2.0 * v.getValue(VelocityPoint.P08) + v.getValue(VelocityPoint.BOTTOM)
            )
    }
}

@Serializable
data class SectionPointInput(
    val id: String = UUID.randomUUID().toString(),
    val distanceText: String = "",
    val depthText: String = ""
)

@Serializable
data class VelocityVerticalInput(
    val id: String = UUID.randomUUID().toString(),
    val distanceText: String = "",
    val measurementMethod: MeasurementMethod = MeasurementMethod.SINGLE_06,
    val vSurfaceText: String = "",
    val v02Text: String = "",
    val v04Text: String = "",
    val v06Text: String = "",
    val v08Text: String = "",
    val vBottomText: String = ""
) {
    fun text(point: VelocityPoint): String = when (point) {
        VelocityPoint.SURFACE -> vSurfaceText
        VelocityPoint.P02 -> v02Text
        VelocityPoint.P04 -> v04Text
        VelocityPoint.P06 -> v06Text
        VelocityPoint.P08 -> v08Text
        VelocityPoint.BOTTOM -> vBottomText
    }

    fun withText(point: VelocityPoint, value: String): VelocityVerticalInput = when (point) {
        VelocityPoint.SURFACE -> copy(vSurfaceText = value)
        VelocityPoint.P02 -> copy(v02Text = value)
        VelocityPoint.P04 -> copy(v04Text = value)
        VelocityPoint.P06 -> copy(v06Text = value)
        VelocityPoint.P08 -> copy(v08Text = value)
        VelocityPoint.BOTTOM -> copy(vBottomText = value)
    }

    /** Switches the scheme and clears any point it doesn't use, so no stale values linger. */
    fun switchMethod(method: MeasurementMethod): VelocityVerticalInput {
        var result = copy(measurementMethod = method)
        VelocityPoint.entries.forEach { point ->
            if (point !in method.points) result = result.withText(point, "")
        }
        return result
    }
}

@Serializable
data class SurveyDraft(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val river: String = "",
    val dateText: String = todayText(),
    val startSide: BankSide = BankSide.LEFT,
    val startEdgeText: String = "",
    val endEdgeText: String = "",
    val sectionPoints: List<SectionPointInput> = listOf(SectionPointInput()),
    val velocityVerticals: List<VelocityVerticalInput> = emptyList(),
    val leftBankType: BankType = BankType.STEEP_OR_ROUGH,
    val rightBankType: BankType = BankType.STEEP_OR_ROUGH,
    val leftBankCoefficientText: String = formatNumber(BankType.STEEP_OR_ROUGH.defaultCoefficient),
    val rightBankCoefficientText: String = formatNumber(BankType.STEEP_OR_ROUGH.defaultCoefficient)
)

@Serializable
data class SavedSurvey(
    val id: String,
    val createdAt: Long,
    val updatedAt: Long,
    val draft: SurveyDraft
)

data class SectionPoint(
    val distance: Double,
    val depth: Double
)

data class SectionData(
    val startSide: BankSide,
    val startEdgeDistance: Double,
    val endEdgeDistance: Double,
    val points: List<SectionPoint>
) {
    val wettedWidth: Double get() = endEdgeDistance - startEdgeDistance
    val profilePoints: List<SectionPoint>
        get() = buildList {
            add(SectionPoint(startEdgeDistance, 0.0))
            addAll(points)
            add(SectionPoint(endEdgeDistance, 0.0))
        }
}

data class VelocityVertical(
    val distance: Double,
    val localDepth: Double,
    val measurementMethod: MeasurementMethod,
    val velocities: Map<VelocityPoint, Double>,
    val meanVelocity: Double,
    val measuredDepthOffsets: List<Double>
)

data class BankData(
    val leftType: BankType,
    val rightType: BankType,
    val leftCoefficient: Double,
    val rightCoefficient: Double
)

data class ParsedSurvey(
    val id: String,
    val name: String,
    val river: String,
    val dateText: String,
    val section: SectionData,
    val velocityVerticals: List<VelocityVertical>,
    val banks: BankData
)

data class SegmentDischarge(
    val label: String,
    val xFrom: Double,
    val xTo: Double,
    val width: Double,
    val area: Double,
    val velocity: Double,
    val discharge: Double
)

data class CalculationOutput(
    val totalDischarge: Double,
    val wettedArea: Double,
    val meanVelocity: Double,
    val maxDepth: Double,
    val segments: List<SegmentDischarge>
)

sealed interface ParseState<out T> {
    data class Ok<T>(val value: T) : ParseState<T>
    data class Error(val message: String) : ParseState<Nothing>
}

data class LocalDepthPreview(
    val localDepth: Double,
    val d02: Double,
    val d06: Double,
    val d08: Double,
    val recommendedThreePoint: Boolean
)
