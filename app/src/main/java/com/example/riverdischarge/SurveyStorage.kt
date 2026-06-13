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

internal object SurveyStorage {
    private const val PREFS_NAME = "river_discharge_surveys"
    private const val KEY_SURVEYS = "saved_surveys"

    fun load(context: Context): List<SavedSurvey> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_SURVEYS, "[]") ?: "[]"
        val array = JSONArray(raw)
        val result = mutableListOf<SavedSurvey>()
        for (i in 0 until array.length()) {
            val item = array.getJSONObject(i)
            result += SavedSurvey(
                id = item.getString("id"),
                createdAt = item.optLong("createdAt", 0L),
                updatedAt = item.optLong("updatedAt", 0L),
                draft = item.getJSONObject("draft").toSurveyDraft()
            )
        }
        return result.sortedByDescending { it.updatedAt }
    }

    fun save(context: Context, survey: SavedSurvey) {
        val surveys = load(context).toMutableList()
        surveys.removeAll { it.id == survey.id }
        surveys.add(0, survey)
        persist(context, surveys)
    }

    fun delete(context: Context, surveyId: String) {
        persist(context, load(context).filterNot { it.id == surveyId })
    }

    private fun persist(context: Context, surveys: List<SavedSurvey>) {
        val array = JSONArray()
        surveys.forEach { survey ->
            array.put(
                JSONObject().apply {
                    put("id", survey.id)
                    put("createdAt", survey.createdAt)
                    put("updatedAt", survey.updatedAt)
                    put("draft", survey.draft.toJson())
                }
            )
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SURVEYS, array.toString())
            .apply()
    }
}

internal fun SurveyDraft.toJson(): JSONObject = JSONObject().apply {
    put("id", id)
    put("name", name)
    put("river", river)
    put("dateText", dateText)
    put("startSide", startSide.name)
    put("startEdgeText", startEdgeText)
    put("endEdgeText", endEdgeText)
    put("leftBankType", leftBankType.name)
    put("rightBankType", rightBankType.name)
    put("leftBankCoefficientText", leftBankCoefficientText)
    put("rightBankCoefficientText", rightBankCoefficientText)
    put("sectionPoints", JSONArray().apply {
        sectionPoints.forEach { point ->
            put(
                JSONObject().apply {
                    put("id", point.id)
                    put("distanceText", point.distanceText)
                    put("depthText", point.depthText)
                }
            )
        }
    })
    put("velocityVerticals", JSONArray().apply {
        velocityVerticals.forEach { vertical ->
            put(
                JSONObject().apply {
                    put("id", vertical.id)
                    put("distanceText", vertical.distanceText)
                    put("measurementMethod", vertical.measurementMethod.name)
                    put("vSurfaceText", vertical.vSurfaceText)
                    put("v02Text", vertical.v02Text)
                    put("v04Text", vertical.v04Text)
                    put("v06Text", vertical.v06Text)
                    put("v08Text", vertical.v08Text)
                    put("vBottomText", vertical.vBottomText)
                }
            )
        }
    })
}

internal fun JSONObject.toSurveyDraft(): SurveyDraft {
    val sectionPointsJson = optJSONArray("sectionPoints") ?: JSONArray()
    val sectionPoints = buildList {
        for (i in 0 until sectionPointsJson.length()) {
            val item = sectionPointsJson.getJSONObject(i)
            add(
                SectionPointInput(
                    id = item.optString("id", UUID.randomUUID().toString()),
                    distanceText = item.optString("distanceText", ""),
                    depthText = item.optString("depthText", "")
                )
            )
        }
    }.ifEmpty { listOf(SectionPointInput()) }

    val velocityJson = optJSONArray("velocityVerticals") ?: JSONArray()
    val velocityVerticals = buildList {
        for (i in 0 until velocityJson.length()) {
            val item = velocityJson.getJSONObject(i)
            add(
                VelocityVerticalInput(
                    id = item.optString("id", UUID.randomUUID().toString()),
                    distanceText = item.optString("distanceText", ""),
                    measurementMethod = runCatching {
                        MeasurementMethod.valueOf(item.optString("measurementMethod", MeasurementMethod.SINGLE_06.name))
                    }.getOrDefault(MeasurementMethod.SINGLE_06),
                    vSurfaceText = item.optString("vSurfaceText", ""),
                    v02Text = item.optString("v02Text", ""),
                    v04Text = item.optString("v04Text", ""),
                    v06Text = item.optString("v06Text", ""),
                    v08Text = item.optString("v08Text", ""),
                    vBottomText = item.optString("vBottomText", "")
                )
            )
        }
    }

    return SurveyDraft(
        id = optString("id", UUID.randomUUID().toString()),
        name = optString("name", ""),
        river = optString("river", ""),
        dateText = optString("dateText", todayText()),
        startSide = runCatching { BankSide.valueOf(optString("startSide", BankSide.LEFT.name)) }.getOrDefault(BankSide.LEFT),
        startEdgeText = optString("startEdgeText", ""),
        endEdgeText = optString("endEdgeText", ""),
        sectionPoints = sectionPoints,
        velocityVerticals = velocityVerticals,
        leftBankType = runCatching { BankType.valueOf(optString("leftBankType", BankType.STEEP_OR_ROUGH.name)) }.getOrDefault(BankType.STEEP_OR_ROUGH),
        rightBankType = runCatching { BankType.valueOf(optString("rightBankType", BankType.STEEP_OR_ROUGH.name)) }.getOrDefault(BankType.STEEP_OR_ROUGH),
        leftBankCoefficientText = optString("leftBankCoefficientText", formatNumber(BankType.STEEP_OR_ROUGH.defaultCoefficient)),
        rightBankCoefficientText = optString("rightBankCoefficientText", formatNumber(BankType.STEEP_OR_ROUGH.defaultCoefficient))
    )
}
