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

internal fun calculateDischarge(survey: ParsedSurvey): CalculationOutput {
    val section = survey.section
    val verticals = survey.velocityVerticals.sortedBy { it.distance }
    val boundaries = buildList {
        add(section.startEdgeDistance)
        addAll(verticals.map { it.distance })
        add(section.endEdgeDistance)
    }

    val startBankTitle = section.startSide.title.lowercase(Locale.getDefault())
    val endBankTitle = if (section.startSide == BankSide.LEFT) BankSide.RIGHT.title.lowercase(Locale.getDefault()) else BankSide.LEFT.title.lowercase(Locale.getDefault())
    val startBankCoefficient = if (section.startSide == BankSide.LEFT) survey.banks.leftCoefficient else survey.banks.rightCoefficient
    val endBankCoefficient = if (section.startSide == BankSide.LEFT) survey.banks.rightCoefficient else survey.banks.leftCoefficient

    val segments = mutableListOf<SegmentDischarge>()
    for (i in 0 until boundaries.lastIndex) {
        val xFrom = boundaries[i]
        val xTo = boundaries[i + 1]
        val width = xTo - xFrom
        val area = integrateArea(section.profilePoints, xFrom, xTo)
        val velocity = when {
            i == 0 -> startBankCoefficient * verticals.first().meanVelocity
            i == boundaries.lastIndex - 1 -> endBankCoefficient * verticals.last().meanVelocity
            else -> (verticals[i - 1].meanVelocity + verticals[i].meanVelocity) / 2.0
        }
        val label = when {
            i == 0 -> "Прибрежный участок у $startBankTitle"
            i == boundaries.lastIndex - 1 -> "Прибрежный участок у $endBankTitle"
            else -> "Участок между вертикалями ${i} и ${i + 1}"
        }
        segments += SegmentDischarge(
            label = label,
            xFrom = xFrom,
            xTo = xTo,
            width = width,
            area = area,
            velocity = velocity,
            discharge = area * velocity
        )
    }

    val totalDischarge = segments.sumOf { it.discharge }
    val wettedArea = integrateArea(section.profilePoints, section.startEdgeDistance, section.endEdgeDistance)
    val meanVelocity = if (wettedArea > 0.0) totalDischarge / wettedArea else 0.0
    val maxDepth = section.profilePoints.maxOfOrNull { it.depth } ?: 0.0

    return CalculationOutput(
        totalDischarge = totalDischarge,
        wettedArea = wettedArea,
        meanVelocity = meanVelocity,
        maxDepth = maxDepth,
        segments = segments
    )
}

internal fun depthAt(profilePoints: List<SectionPoint>, x: Double): Double {
    val sorted = profilePoints.sortedBy { it.distance }
    if (x <= sorted.first().distance) return sorted.first().depth
    if (x >= sorted.last().distance) return sorted.last().depth
    for (i in 0 until sorted.lastIndex) {
        val left = sorted[i]
        val right = sorted[i + 1]
        if (x in left.distance..right.distance) {
            val dx = right.distance - left.distance
            if (dx == 0.0) return left.depth
            val ratio = (x - left.distance) / dx
            return left.depth + (right.depth - left.depth) * ratio
        }
    }
    return 0.0
}

internal fun integrateArea(profilePoints: List<SectionPoint>, xFrom: Double, xTo: Double): Double {
    val left = minOf(xFrom, xTo)
    val right = maxOf(xFrom, xTo)
    val inner = profilePoints
        .filter { it.distance in (left + 1e-9)..< (right - 1e-9) }
        .sortedBy { it.distance }

    val nodes = buildList {
        add(SectionPoint(left, depthAt(profilePoints, left)))
        addAll(inner)
        add(SectionPoint(right, depthAt(profilePoints, right)))
    }

    var area = 0.0
    for (i in 0 until nodes.lastIndex) {
        val a = nodes[i]
        val b = nodes[i + 1]
        area += (a.depth + b.depth) * (b.distance - a.distance) / 2.0
    }
    return area
}
