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

internal fun validatePassport(draft: SurveyDraft): String? {
    if (draft.name.isBlank()) return "Укажи имя замера."
    if (draft.river.isBlank()) return "Укажи реку."
    if (draft.dateText.isBlank()) return "Укажи дату."
    return null
}

internal fun parseSection(draft: SurveyDraft): ParseState<SectionData> {
    validatePassport(draft)?.let { return ParseState.Error(it) }

    val startEdge = draft.startEdgeText.parseFlexibleDouble()
        ?: return ParseState.Error("Не удалось распознать расстояние уреза стартового берега.")
    val endEdge = draft.endEdgeText.parseFlexibleDouble()
        ?: return ParseState.Error("Не удалось распознать расстояние уреза противоположного берега.")

    if (endEdge <= startEdge) {
        return ParseState.Error("Расстояние противоположного берега должно быть больше стартового уреза.")
    }
    if (draft.sectionPoints.isEmpty()) {
        return ParseState.Error("Добавь хотя бы одну точку глубины.")
    }

    val parsedPoints = mutableListOf<SectionPoint>()
    draft.sectionPoints.forEachIndexed { index, input ->
        val distance = input.distanceText.parseFlexibleDouble()
            ?: return ParseState.Error("В точке глубины ${index + 1} не распознано расстояние.")
        val depth = input.depthText.parseFlexibleDouble()
            ?: return ParseState.Error("В точке глубины ${index + 1} не распознана глубина.")

        if (distance <= startEdge || distance >= endEdge) {
            return ParseState.Error("Точка глубины ${index + 1} должна быть строго внутри русла: $startEdge < x < $endEdge.")
        }
        if (depth < 0.0) {
            return ParseState.Error("Глубина в точке ${index + 1} не может быть отрицательной.")
        }
        parsedPoints += SectionPoint(distance, depth)
    }

    val sorted = parsedPoints.sortedBy { it.distance }
    for (i in 1 until sorted.size) {
        if (abs(sorted[i].distance - sorted[i - 1].distance) < 1e-9) {
            return ParseState.Error("Есть две точки глубины с одинаковым расстоянием. Расстояния должны различаться.")
        }
    }
    return ParseState.Ok(
        SectionData(
            startSide = draft.startSide,
            startEdgeDistance = startEdge,
            endEdgeDistance = endEdge,
            points = sorted
        )
    )
}

internal fun previewLocalDepth(section: SectionData, distanceText: String): LocalDepthPreview? {
    val x = distanceText.parseFlexibleDouble() ?: return null
    if (x <= section.startEdgeDistance || x >= section.endEdgeDistance) return null
    val depth = depthAt(section.profilePoints, x)
    return LocalDepthPreview(
        localDepth = depth,
        d02 = depth * 0.2,
        d06 = depth * 0.6,
        d08 = depth * 0.8,
        recommendedThreePoint = depth > 0.5
    )
}

internal fun parseVelocityStage(draft: SurveyDraft): ParseState<List<VelocityVertical>> {
    val section = when (val parsed = parseSection(draft)) {
        is ParseState.Ok -> parsed.value
        is ParseState.Error -> return parsed
    }
    if (draft.velocityVerticals.isEmpty()) {
        return ParseState.Error("Добавь хотя бы одну вертикаль скорости.")
    }

    val verticals = mutableListOf<VelocityVertical>()
    draft.velocityVerticals.forEachIndexed { index, input ->
        val distance = input.distanceText.parseFlexibleDouble()
            ?: return ParseState.Error("В вертикали скорости ${index + 1} не распознано расстояние.")
        if (distance <= section.startEdgeDistance || distance >= section.endEdgeDistance) {
            return ParseState.Error("Вертикаль скорости ${index + 1} должна лежать строго между урезами.")
        }
        val localDepth = depthAt(section.profilePoints, distance)
        if (localDepth <= 0.0) {
            return ParseState.Error("В вертикали скорости ${index + 1} получилась нулевая глубина. Выбери точку внутри русла.")
        }

        val method = input.measurementMethod
        val velocities = mutableMapOf<VelocityPoint, Double>()
        for (point in method.points) {
            val value = input.text(point).parseNullableDouble()
                ?: return ParseState.Error("Для вертикали ${index + 1} (${method.title}) введи скорость на ${point.label}.")
            if (value < 0.0) {
                return ParseState.Error("Скорости в вертикали ${index + 1} не могут быть отрицательными.")
            }
            velocities[point] = value
        }

        verticals += VelocityVertical(
            distance = distance,
            localDepth = localDepth,
            measurementMethod = method,
            velocities = velocities,
            meanVelocity = method.meanVelocity(velocities),
            measuredDepthOffsets = method.points.map { localDepth * it.fraction }
        )
    }

    val sorted = verticals.sortedBy { it.distance }
    for (i in 1 until sorted.size) {
        if (abs(sorted[i].distance - sorted[i - 1].distance) < 1e-9) {
            return ParseState.Error("Есть две вертикали скорости с одинаковым расстоянием. Они должны различаться.")
        }
    }
    return ParseState.Ok(sorted)
}

internal fun parseBanks(draft: SurveyDraft): ParseState<BankData> {
    val left = draft.leftBankCoefficientText.parseFlexibleDouble()
        ?: return ParseState.Error("Не удалось распознать коэффициент левого берега.")
    val right = draft.rightBankCoefficientText.parseFlexibleDouble()
        ?: return ParseState.Error("Не удалось распознать коэффициент правого берега.")

    if (left <= 0.0 || left > 1.0) {
        return ParseState.Error("Коэффициент левого берега должен быть в диапазоне 0–1.")
    }
    if (right <= 0.0 || right > 1.0) {
        return ParseState.Error("Коэффициент правого берега должен быть в диапазоне 0–1.")
    }
    return ParseState.Ok(
        BankData(
            leftType = draft.leftBankType,
            rightType = draft.rightBankType,
            leftCoefficient = left,
            rightCoefficient = right
        )
    )
}

internal fun parseSurvey(draft: SurveyDraft): ParseState<ParsedSurvey> {
    val section = when (val parsed = parseSection(draft)) {
        is ParseState.Ok -> parsed.value
        is ParseState.Error -> return parsed
    }
    val velocity = when (val parsed = parseVelocityStage(draft)) {
        is ParseState.Ok -> parsed.value
        is ParseState.Error -> return parsed
    }
    val banks = when (val parsed = parseBanks(draft)) {
        is ParseState.Ok -> parsed.value
        is ParseState.Error -> return parsed
    }
    return ParseState.Ok(
        ParsedSurvey(
            id = draft.id,
            name = draft.name.trim(),
            river = draft.river.trim(),
            dateText = draft.dateText.trim(),
            section = section,
            velocityVerticals = velocity,
            banks = banks
        )
    )
}
