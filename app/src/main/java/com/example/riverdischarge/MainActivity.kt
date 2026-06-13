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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RiverDischargeTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    RiverDischargeApp()
                }
            }
        }
    }
}

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

data class SectionPointInput(
    val id: String = UUID.randomUUID().toString(),
    val distanceText: String = "",
    val depthText: String = ""
)

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

private fun <T> ParseState<T>.getOrNull(): T? = (this as? ParseState.Ok<T>)?.value
private fun <T> ParseState<T>.errorOrNull(): String? = (this as? ParseState.Error)?.message

private fun todayText(): String = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date())

@Composable
fun RiverDischargeApp() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var surveys by remember { mutableStateOf(SurveyStorage.load(context)) }
    var currentDraft by remember { mutableStateOf<SurveyDraft?>(null) }
    var currentTab by remember { mutableStateOf(0) }

    if (currentDraft == null) {
        HomeScreen(
            surveys = surveys,
            snackbarHostState = snackbarHostState,
            onNewSurvey = {
                currentDraft = SurveyDraft()
                currentTab = 0
            },
            onOpenSurvey = {
                currentDraft = it.draft
                currentTab = 0
            },
            onDeleteSurvey = { survey ->
                SurveyStorage.delete(context, survey.id)
                surveys = SurveyStorage.load(context)
            }
        )
    } else {
        val draft = currentDraft!!
        val sectionState = remember(draft) { parseSection(draft) }
        val velocityState = remember(draft) { parseVelocityStage(draft) }
        val bankState = remember(draft) { parseBanks(draft) }
        val surveyState = remember(draft) { parseSurvey(draft) }
        val calculationState = remember(draft) {
            when (val survey = parseSurvey(draft)) {
                is ParseState.Ok -> ParseState.Ok(calculateDischarge(survey.value))
                is ParseState.Error -> survey
            }
        }

        BackHandler {
            currentDraft = null
            currentTab = 0
        }

        EditorScreen(
            draft = draft,
            currentTab = currentTab,
            onTabSelected = { currentTab = it },
            snackbarHostState = snackbarHostState,
            sectionState = sectionState,
            velocityState = velocityState,
            bankState = bankState,
            calculationState = calculationState,
            surveyState = surveyState,
            onDraftChange = { currentDraft = it },
            onClose = {
                currentDraft = null
                currentTab = 0
            },
            onSave = {
                val parsed = parseSurvey(draft)
                if (parsed is ParseState.Error) {
                    scope.launch { snackbarHostState.showSnackbar(parsed.message) }
                } else {
                    val stamp = System.currentTimeMillis()
                    val toSave = SavedSurvey(
                        id = draft.id,
                        createdAt = surveys.firstOrNull { it.id == draft.id }?.createdAt ?: stamp,
                        updatedAt = stamp,
                        draft = draft
                    )
                    SurveyStorage.save(context, toSave)
                    surveys = SurveyStorage.load(context)
                    currentDraft = null
                    currentTab = 0
                    scope.launch { snackbarHostState.showSnackbar("Замер сохранён") }
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(
    surveys: List<SavedSurvey>,
    snackbarHostState: SnackbarHostState,
    onNewSurvey: () -> Unit,
    onOpenSurvey: (SavedSurvey) -> Unit,
    onDeleteSurvey: (SavedSurvey) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Расход реки", fontWeight = FontWeight.Bold)
                        Text(
                            "Замеры по створам",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onNewSurvey) {
                Text("+  Новый замер", fontWeight = FontWeight.SemiBold)
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "Мои замеры",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        "Створы и расчёт расхода",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            if (surveys.isEmpty()) {
                EmptyCard(
                    title = "Пока нет замеров",
                    subtitle = "Нажми «Новый замер», чтобы ввести данные по створу и посчитать расход."
                )
            } else {
                surveys.forEach { survey ->
                    val parsed = parseSurvey(survey.draft)
                    val calc = (parsed as? ParseState.Ok)?.let { calculateDischarge(it.value) }
                    SurveyCard(
                        survey = survey,
                        calculation = calc,
                        onOpen = { onOpenSurvey(survey) },
                        onDelete = { onDeleteSurvey(survey) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(96.dp))
        }
    }
}

@Composable
private fun SurveyCard(
    survey: SavedSurvey,
    calculation: CalculationOutput?,
    onOpen: () -> Unit,
    onDelete: () -> Unit
) {
    val timeText = remember(survey.updatedAt) {
        SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(survey.updatedAt))
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpen() },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                survey.draft.name.ifBlank { "Без названия" },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                "${survey.draft.river.ifBlank { "Река не указана" }} · ${survey.draft.dateText}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (calculation != null) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                "Расход Q",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Text(
                                "${formatNumber(calculation.totalDischarge)} м³/с",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                "F = ${formatNumber(calculation.wettedArea)} м²",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Text(
                                "Vср = ${formatNumber(calculation.meanVelocity)} м/с",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    timeText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = onDelete) { Text("Удалить") }
                    Button(onClick = onOpen) { Text("Открыть") }
                }
            }
        }
    }
}

private data class EditorTab(val title: String, val emoji: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditorScreen(
    draft: SurveyDraft,
    currentTab: Int,
    onTabSelected: (Int) -> Unit,
    snackbarHostState: SnackbarHostState,
    sectionState: ParseState<SectionData>,
    velocityState: ParseState<List<VelocityVertical>>,
    bankState: ParseState<BankData>,
    calculationState: ParseState<CalculationOutput>,
    surveyState: ParseState<ParsedSurvey>,
    onDraftChange: (SurveyDraft) -> Unit,
    onClose: () -> Unit,
    onSave: () -> Unit
) {
    val tabs = remember {
        listOf(
            EditorTab("Паспорт", "👤"),
            EditorTab("Профиль", "📐"),
            EditorTab("Скорости", "🌊"),
            EditorTab("Берега", "🏞"),
            EditorTab("Расход", "Σ")
        )
    }
    val safeTab = currentTab.coerceIn(0, tabs.lastIndex)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            draft.name.ifBlank { "Новый замер" },
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                        Text(
                            tabs[safeTab].title,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Text("✕", fontSize = 20.sp)
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = safeTab == index,
                        onClick = { onTabSelected(index) },
                        icon = { Text(tab.emoji, fontSize = 18.sp) },
                        label = { Text(tab.title, maxLines = 1) }
                    )
                }
            }
        },
        floatingActionButton = {
            if (safeTab < tabs.lastIndex) {
                ExtendedFloatingActionButton(onClick = { onTabSelected(safeTab + 1) }) {
                    Text("Далее →", fontWeight = FontWeight.SemiBold)
                }
            } else {
                ExtendedFloatingActionButton(onClick = onSave) {
                    Text("Сохранить", fontWeight = FontWeight.SemiBold)
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))
            when (safeTab) {
                0 -> PassportStep(draft, onDraftChange)
                1 -> SectionStep(draft, sectionState, onDraftChange)
                2 -> VelocityStep(draft, sectionState, velocityState, onDraftChange)
                3 -> BanksStep(draft, bankState, onDraftChange)
                4 -> ResultStep(sectionState, velocityState, bankState, surveyState, calculationState)
            }
            Spacer(modifier = Modifier.height(96.dp))
        }
    }
}

@Composable
private fun PassportStep(draft: SurveyDraft, onDraftChange: (SurveyDraft) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Паспорт замера", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = draft.name,
                onValueChange = { onDraftChange(draft.copy(name = it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Имя замера") },
                placeholder = { Text("Например: Створ у моста") },
                singleLine = true
            )
            OutlinedTextField(
                value = draft.river,
                onValueChange = { onDraftChange(draft.copy(river = it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Река") },
                placeholder = { Text("Например: Северная Двина") },
                singleLine = true
            )
            OutlinedTextField(
                value = draft.dateText,
                onValueChange = { onDraftChange(draft.copy(dateText = it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Дата") },
                placeholder = { Text("dd.MM.yyyy") },
                singleLine = true
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SectionStep(
    draft: SurveyDraft,
    sectionState: ParseState<SectionData>,
    onDraftChange: (SurveyDraft) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Стартовый берег", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BankSide.entries.forEach { side ->
                        FilterChip(
                            selected = draft.startSide == side,
                            onClick = { onDraftChange(draft.copy(startSide = side)) },
                            label = { Text(side.title) }
                        )
                    }
                }
                OutlinedTextField(
                    value = draft.startEdgeText,
                    onValueChange = { onDraftChange(draft.copy(startEdgeText = it)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Урез стартового берега, м") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Точки глубины", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                if (draft.sectionPoints.isEmpty()) {
                    EmptyCard("Нет точек", "Добавь точки промера внутри русла.")
                } else {
                    draft.sectionPoints.forEachIndexed { index, point ->
                        SectionPointEditor(
                            index = index,
                            point = point,
                            canDelete = draft.sectionPoints.size > 1,
                            onChange = { updated ->
                                onDraftChange(draft.copy(sectionPoints = draft.sectionPoints.replaceSectionPoint(updated)))
                            },
                            onDelete = {
                                onDraftChange(draft.copy(sectionPoints = draft.sectionPoints.filterNot { it.id == point.id }))
                            }
                        )
                    }
                }
                Button(
                    onClick = {
                        onDraftChange(draft.copy(sectionPoints = draft.sectionPoints + SectionPointInput()))
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("+ Добавить точку") }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Дальний берег", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = draft.endEdgeText,
                    onValueChange = { onDraftChange(draft.copy(endEdgeText = it)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Урез противоположного берега, м") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
            }
        }

        when (sectionState) {
            is ParseState.Error -> ErrorCard(sectionState.message)
            is ParseState.Ok -> {
                InfoCard(
                    title = "Ширина русла",
                    body = "${formatNumber(sectionState.value.wettedWidth)} м между урезами."
                )
                SectionProfileCard(section = sectionState.value, velocityVerticals = emptyList())
            }
        }
    }
}

@Composable
private fun VelocityStep(
    draft: SurveyDraft,
    sectionState: ParseState<SectionData>,
    velocityState: ParseState<List<VelocityVertical>>,
    onDraftChange: (SurveyDraft) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        when (sectionState) {
            is ParseState.Error -> ErrorCard("Сначала заполни профиль русла: ${sectionState.message}")
            is ParseState.Ok -> {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Скорости на вертикалях", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            "Только вертикали, где мерилась скорость. Свободное русло: 1 точка (0.6h), 2 точки (0.2h/0.8h) или 5 точек. Ледостав / зарастание: 3 или 6 точек.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (draft.velocityVerticals.isEmpty()) {
                    EmptyCard(
                        title = "Нет вертикалей",
                        subtitle = "Добавь вертикаль скорости — не обязательно во всех точках профиля."
                    )
                } else {
                    draft.velocityVerticals.forEachIndexed { index, vertical ->
                        val preview = previewLocalDepth(sectionState.value, vertical.distanceText)
                        VelocityEditorCard(
                            index = index,
                            input = vertical,
                            preview = preview,
                            canDelete = draft.velocityVerticals.size > 1,
                            onChange = { updated ->
                                onDraftChange(draft.copy(velocityVerticals = draft.velocityVerticals.replaceVelocityVertical(updated)))
                            },
                            onDelete = {
                                onDraftChange(draft.copy(velocityVerticals = draft.velocityVerticals.filterNot { it.id == vertical.id }))
                            }
                        )
                    }
                }

                Button(
                    onClick = {
                        onDraftChange(draft.copy(velocityVerticals = draft.velocityVerticals + VelocityVerticalInput()))
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("+ Добавить вертикаль") }

                when (velocityState) {
                    is ParseState.Error -> ErrorCard(velocityState.message)
                    is ParseState.Ok -> {
                        InfoCard(
                            title = "Средние скорости",
                            body = velocityState.value.joinToString("\n") {
                                "x=${formatNumber(it.distance)} · h=${formatNumber(it.localDepth)} · Vср=${formatNumber(it.meanVelocity)}"
                            }
                        )
                        SectionProfileCard(section = sectionState.value, velocityVerticals = velocityState.value)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BanksStep(
    draft: SurveyDraft,
    bankState: ParseState<BankData>,
    onDraftChange: (SurveyDraft) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Тип берегов", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "Коэффициент берегового отсека: 0.7 пологий (h=0), 0.8 обрыв/неровность, 0.9 гладкая стенка, 0.5 заросли/застой. Можно править вручную.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                BankSelector(
                    title = "Левый берег",
                    selectedType = draft.leftBankType,
                    coefficientText = draft.leftBankCoefficientText,
                    onTypeSelected = { type ->
                        onDraftChange(
                            draft.copy(
                                leftBankType = type,
                                leftBankCoefficientText = formatNumber(type.defaultCoefficient)
                            )
                        )
                    },
                    onCoefficientChange = {
                        onDraftChange(draft.copy(leftBankCoefficientText = it))
                    }
                )
                HorizontalDivider()
                BankSelector(
                    title = "Правый берег",
                    selectedType = draft.rightBankType,
                    coefficientText = draft.rightBankCoefficientText,
                    onTypeSelected = { type ->
                        onDraftChange(
                            draft.copy(
                                rightBankType = type,
                                rightBankCoefficientText = formatNumber(type.defaultCoefficient)
                            )
                        )
                    },
                    onCoefficientChange = {
                        onDraftChange(draft.copy(rightBankCoefficientText = it))
                    }
                )
            }
        }
        when (bankState) {
            is ParseState.Error -> ErrorCard(bankState.message)
            is ParseState.Ok -> InfoCard(
                title = "Коэффициенты для расчёта",
                body = "Левый берег: ${bankState.value.leftType.title} → ${formatNumber(bankState.value.leftCoefficient)}\n" +
                    "Правый берег: ${bankState.value.rightType.title} → ${formatNumber(bankState.value.rightCoefficient)}"
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ResultStep(
    sectionState: ParseState<SectionData>,
    velocityState: ParseState<List<VelocityVertical>>,
    bankState: ParseState<BankData>,
    surveyState: ParseState<ParsedSurvey>,
    calculationState: ParseState<CalculationOutput>
) {
    val context = androidx.compose.ui.platform.LocalContext.current

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (sectionState is ParseState.Ok) {
            SectionProfileCard(
                section = sectionState.value,
                velocityVerticals = velocityState.getOrNull().orEmpty()
            )
        }
        if (velocityState is ParseState.Ok && velocityState.value.isNotEmpty()) {
            VelocityProfilesCard(velocityState.value)
        }
        if (velocityState is ParseState.Error) {
            ErrorCard(velocityState.message)
        }
        if (bankState is ParseState.Error) {
            ErrorCard(bankState.message)
        }
        if (surveyState is ParseState.Error) {
            ErrorCard(surveyState.message)
        }
        when (calculationState) {
            is ParseState.Error -> ErrorCard(calculationState.message)
            is ParseState.Ok -> {
                val result = calculationState.value
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Расход", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        MetricRow("Суммарный расход Q", "${formatNumber(result.totalDischarge)} м³/с")
                        MetricRow("Площадь живого сечения F", "${formatNumber(result.wettedArea)} м²")
                        MetricRow("Средняя скорость Vср", "${formatNumber(result.meanVelocity)} м/с")
                        MetricRow("Максимальная глубина", "${formatNumber(result.maxDepth)} м")
                    }
                }

                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Частные расходы по участкам", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        result.segments.forEach { segment ->
                            SegmentCard(segment)
                        }
                    }
                }

                if (surveyState is ParseState.Ok) {
                    val survey = surveyState.value
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Копировать в буфер", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(
                                "Таблицы с табуляцией — вставляй в Excel или Google Sheets.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(onClick = {
                                    copyTextToClipboard(
                                        context = context,
                                        label = "Сводка расчёта",
                                        text = buildSummaryClipboardText(survey, result)
                                    )
                                }) { Text("Сводка") }
                                OutlinedButton(onClick = {
                                    copyTextToClipboard(
                                        context = context,
                                        label = "Профиль русла",
                                        text = buildProfileTableClipboardText(survey.section)
                                    )
                                }) { Text("Профиль") }
                                OutlinedButton(onClick = {
                                    copyTextToClipboard(
                                        context = context,
                                        label = "Вертикали скорости",
                                        text = buildVelocityTableClipboardText(survey.velocityVerticals)
                                    )
                                }) { Text("Скорости") }
                                OutlinedButton(onClick = {
                                    copyTextToClipboard(
                                        context = context,
                                        label = "Участки расхода",
                                        text = buildSegmentsClipboardText(result)
                                    )
                                }) { Text("Участки") }
                                Button(onClick = {
                                    copyTextToClipboard(
                                        context = context,
                                        label = "Все таблицы",
                                        text = buildFullClipboardExport(survey, result)
                                    )
                                }) { Text("Копировать всё") }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BankSelector(
    title: String,
    selectedType: BankType,
    coefficientText: String,
    onTypeSelected: (BankType) -> Unit,
    onCoefficientChange: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, fontWeight = FontWeight.Bold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            BankType.entries.forEach { type ->
                FilterChip(
                    selected = type == selectedType,
                    onClick = { onTypeSelected(type) },
                    label = { Text(type.title) }
                )
            }
        }
        OutlinedTextField(
            value = coefficientText,
            onValueChange = onCoefficientChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Коэффициент") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
        )
    }
}

@Composable
private fun SectionPointEditor(
    index: Int,
    point: SectionPointInput,
    canDelete: Boolean,
    onChange: (SectionPointInput) -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Точка глубины ${index + 1}", fontWeight = FontWeight.Bold)
                if (canDelete) {
                    TextButton(onClick = onDelete) { Text("Удалить") }
                }
            }
            OutlinedTextField(
                value = point.distanceText,
                onValueChange = { onChange(point.copy(distanceText = it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Расстояние по линейке, м") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )
            OutlinedTextField(
                value = point.depthText,
                onValueChange = { onChange(point.copy(depthText = it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Глубина, м") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VelocityEditorCard(
    index: Int,
    input: VelocityVerticalInput,
    preview: LocalDepthPreview?,
    canDelete: Boolean,
    onChange: (VelocityVerticalInput) -> Unit,
    onDelete: () -> Unit
) {
    val recommendation = when {
        preview == null -> "Сначала введи расстояние вертикали внутри русла."
        preview.recommendedThreePoint -> "Глубина больше 0.5 м — лучше взять 3 замера: 0.2h, 0.6h и 0.8h."
        else -> "Глубина 0.5 м и меньше — обычно достаточно одного замера на 0.6h."
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Вертикаль скорости ${index + 1}", fontWeight = FontWeight.Bold)
                if (canDelete) {
                    TextButton(onClick = onDelete) { Text("Удалить") }
                }
            }
            OutlinedTextField(
                value = input.distanceText,
                onValueChange = { onChange(input.copy(distanceText = it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Расстояние вертикали, м") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )

            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(recommendation, fontWeight = FontWeight.Medium)
                    if (preview != null) {
                        Text(
                            "h = ${formatNumber(preview.localDepth)} м; 0.2h = ${formatNumber(preview.d02)} м; 0.6h = ${formatNumber(preview.d06)} м; 0.8h = ${formatNumber(preview.d08)} м от поверхности."
                        )
                    }
                }
            }

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MeasurementMethod.entries.forEach { method ->
                    FilterChip(
                        selected = input.measurementMethod == method,
                        onClick = { onChange(input.switchMethod(method)) },
                        label = { Text(method.title) }
                    )
                }
            }

            input.measurementMethod.points.forEach { point ->
                OutlinedTextField(
                    value = input.text(point),
                    onValueChange = { onChange(input.withText(point, it)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Скорость на ${point.label}, м/с") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
            }
        }
    }
}

@Composable
private fun SectionProfileCard(section: SectionData, velocityVerticals: List<VelocityVertical>) {
    val profile = section.profilePoints
    val maxDepthValue = (profile.maxOfOrNull { it.depth } ?: 1.0).coerceAtLeast(0.1)
    val xMin = section.startEdgeDistance
    val xMax = section.endEdgeDistance
    val depthTicks = (0..4).map { maxDepthValue * it / 4.0 }
    val pointLabels = profile.drop(1).dropLast(1)

    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.tertiary
    val outline = MaterialTheme.colorScheme.outline
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    val waterLineColor = MaterialTheme.colorScheme.onSurfaceVariant

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
                    val leftPad = 72.dp.toPx()
                    val rightPad = 18.dp.toPx()
                    val topPad = 30.dp.toPx()
                    val bottomPad = 64.dp.toPx()
                    val width = size.width - leftPad - rightPad
                    val height = size.height - topPad - bottomPad
                    val left = leftPad
                    val top = topPad
                    val bottom = top + height
                    val right = left + width
                    val axisPaint = Paint().apply {
                        color = android.graphics.Color.argb(255, 90, 90, 90)
                        textSize = 30f
                        textAlign = Paint.Align.RIGHT
                        isAntiAlias = true
                    }
                    val labelPaint = Paint().apply {
                        color = android.graphics.Color.argb(255, 70, 70, 70)
                        textSize = 26f
                        textAlign = Paint.Align.CENTER
                        isAntiAlias = true
                    }
                    val smallPaint = Paint().apply {
                        color = android.graphics.Color.argb(255, 70, 70, 70)
                        textSize = 24f
                        textAlign = Paint.Align.CENTER
                        isAntiAlias = true
                    }

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
                            strokeWidth = 1.5f
                        )
                        drawContext.canvas.nativeCanvas.drawText(formatNumber(tick), left - 10f, y + 8f, axisPaint)
                    }

                    val xTicks = buildList {
                        add(section.startEdgeDistance)
                        addAll(pointLabels.map { it.distance })
                        add(section.endEdgeDistance)
                    }
                    xTicks.forEach { tick ->
                        val x = mapX(tick)
                        drawLine(
                            color = outline.copy(alpha = 0.35f),
                            start = Offset(x, top),
                            end = Offset(x, bottom),
                            strokeWidth = 1f
                        )
                        drawContext.canvas.nativeCanvas.drawText(formatNumber(tick), x, bottom + 28f, labelPaint)
                    }

                    drawLine(color = outline, start = Offset(left, top), end = Offset(left, bottom), strokeWidth = 2.5f)
                    drawLine(color = waterLineColor, start = Offset(left, top), end = Offset(right, top), strokeWidth = 2.5f)
                    drawLine(color = outline, start = Offset(left, bottom), end = Offset(right, bottom), strokeWidth = 2.5f)

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
                    drawPath(path = linePath, color = primary, style = Stroke(width = 5f, cap = StrokeCap.Round))

                    pointLabels.forEachIndexed { index, point ->
                        val x = mapX(point.distance)
                        val y = mapY(point.depth)
                        drawLine(
                            color = outline.copy(alpha = 0.55f),
                            start = Offset(x, top),
                            end = Offset(x, y),
                            strokeWidth = 2f
                        )
                        drawCircle(color = primary, radius = 6f, center = Offset(x, y))
                        drawContext.canvas.nativeCanvas.drawText((index + 1).toString(), x, bottom + 56f, smallPaint)
                    }

                    velocityVerticals.forEachIndexed { index, vertical ->
                        val x = mapX(vertical.distance)
                        val bottomY = mapY(vertical.localDepth)
                        drawLine(
                            color = secondary,
                            start = Offset(x, top),
                            end = Offset(x, bottomY),
                            strokeWidth = 3f
                        )
                        vertical.measuredDepthOffsets.forEach { offset ->
                            drawCircle(color = secondary, radius = 7f, center = Offset(x, mapY(offset)))
                        }
                        drawContext.canvas.nativeCanvas.drawText("V${index + 1}", x, top - 8f, smallPaint)
                    }

                    drawContext.canvas.nativeCanvas.drawText("Глубина, м", left - 8f, top - 8f, axisPaint)
                    drawContext.canvas.nativeCanvas.drawText("Расстояния по линейке, м", (left + right) / 2f, size.height - 8f, labelPaint)
                    drawContext.canvas.nativeCanvas.drawText("Урез", mapX(section.startEdgeDistance), bottom + 56f, smallPaint)
                    drawContext.canvas.nativeCanvas.drawText("Урез", mapX(section.endEdgeDistance), bottom + 56f, smallPaint)
                }
            }

            Text(
                "Схема похожа на полевой поперечник: сверху урез воды, снизу расстояния по линейке, на профиле — точки глубины, а V1, V2… показывают только те вертикали, где реально мерилась скорость.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

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
private data class EpureProfile(
    val measured: List<Pair<Double, Double>>,
    val surfacePoint: Pair<Double, Double>?,
    val bedPoint: Pair<Double, Double>?
)

private fun buildEpureProfile(vertical: VelocityVertical): EpureProfile {
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
private fun smoothPathThrough(points: List<Offset>): Path {
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

@Composable
private fun VelocityProfilesCard(verticals: List<VelocityVertical>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Эпюры скорости", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(
                "Распределение скорости по глубине вертикали: вправо — скорость v, вниз — глубина (сверху поверхность, снизу дно). Сплошная линия идёт по замерам, пунктир — физичная достройка к поверхности (выполаживание) и ко дну (спад из-за трения о ложе).",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            verticals.forEachIndexed { index, vertical ->
                VelocityProfileChart(index = index, vertical = vertical)
            }
        }
    }
}

@Composable
private fun VelocityProfileChart(index: Int, vertical: VelocityVertical) {
    val profile = buildEpureProfile(vertical)
    val h = vertical.localDepth
    val vMax = ((profile.measured.maxOfOrNull { it.second } ?: 0.0) * 1.15).coerceAtLeast(0.05)
    val depthTicks = (0..4).map { h * it / 4.0 }
    val vTicks = (0..4).map { vMax * it / 4.0 }

    val primary = MaterialTheme.colorScheme.primary
    val pointColor = MaterialTheme.colorScheme.tertiary
    val outline = MaterialTheme.colorScheme.outline
    val waterLineColor = MaterialTheme.colorScheme.onSurfaceVariant

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            "Вертикаль ${index + 1} · x=${formatNumber(vertical.distance)} м · h=${formatNumber(h)} м · Vср=${formatNumber(vertical.meanVelocity)} м/с",
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
                val leftPad = 56.dp.toPx()
                val rightPad = 16.dp.toPx()
                val topPad = 26.dp.toPx()
                val bottomPad = 50.dp.toPx()
                val width = size.width - leftPad - rightPad
                val height = size.height - topPad - bottomPad
                val left = leftPad
                val top = topPad
                val bottom = top + height
                val right = left + width

                val axisPaint = Paint().apply {
                    color = android.graphics.Color.argb(255, 90, 90, 90)
                    textSize = 28f
                    textAlign = Paint.Align.RIGHT
                    isAntiAlias = true
                }
                val labelPaint = Paint().apply {
                    color = android.graphics.Color.argb(255, 70, 70, 70)
                    textSize = 26f
                    textAlign = Paint.Align.CENTER
                    isAntiAlias = true
                }

                fun mapX(v: Double): Float = left + (v / vMax).toFloat().coerceIn(0f, 1f) * width
                fun mapY(depth: Double): Float = top + (depth / h).toFloat().coerceIn(0f, 1f) * height

                // Depth grid + labels (0 at surface on top, h at the bed on bottom).
                depthTicks.forEach { tick ->
                    val y = mapY(tick)
                    drawLine(
                        color = outline.copy(alpha = 0.30f),
                        start = Offset(left, y),
                        end = Offset(right, y),
                        strokeWidth = 1.5f
                    )
                    drawContext.canvas.nativeCanvas.drawText(formatNumber(tick), left - 10f, y + 8f, axisPaint)
                }
                // Velocity grid + labels along the bottom.
                vTicks.forEach { tick ->
                    val x = mapX(tick)
                    drawLine(
                        color = outline.copy(alpha = 0.30f),
                        start = Offset(x, top),
                        end = Offset(x, bottom),
                        strokeWidth = 1f
                    )
                    drawContext.canvas.nativeCanvas.drawText(formatNumber(tick), x, bottom + 26f, labelPaint)
                }

                // Frame: velocity axis (left), surface line (top), bed line (bottom).
                drawLine(color = outline, start = Offset(left, top), end = Offset(left, bottom), strokeWidth = 2.5f)
                drawLine(color = waterLineColor, start = Offset(left, top), end = Offset(right, top), strokeWidth = 2.5f)
                drawLine(color = outline, start = Offset(left, bottom), end = Offset(right, bottom), strokeWidth = 2.5f)

                val measuredOffsets = profile.measured.map { (depth, v) -> Offset(mapX(v), mapY(depth)) }

                val dashed = PathEffect.dashPathEffect(floatArrayOf(12f, 10f))
                profile.surfacePoint?.let { (depth, v) ->
                    drawLine(
                        color = primary.copy(alpha = 0.7f),
                        start = Offset(mapX(v), mapY(depth)),
                        end = measuredOffsets.first(),
                        strokeWidth = 4f,
                        pathEffect = dashed
                    )
                }
                profile.bedPoint?.let { (depth, v) ->
                    drawLine(
                        color = primary.copy(alpha = 0.7f),
                        start = measuredOffsets.last(),
                        end = Offset(mapX(v), mapY(depth)),
                        strokeWidth = 4f,
                        pathEffect = dashed
                    )
                }

                // Solid smooth curve through the actual readings.
                if (measuredOffsets.size >= 2) {
                    drawPath(
                        path = smoothPathThrough(measuredOffsets),
                        color = primary,
                        style = Stroke(width = 5f, cap = StrokeCap.Round)
                    )
                }

                // Each reading: a thin horizontal "velocity stick" from the axis plus the point itself.
                profile.measured.forEachIndexed { i, (depth, v) ->
                    val y = mapY(depth)
                    drawLine(
                        color = pointColor.copy(alpha = 0.45f),
                        start = Offset(left, y),
                        end = Offset(mapX(v), y),
                        strokeWidth = 2f
                    )
                    drawCircle(color = pointColor, radius = 7f, center = measuredOffsets[i])
                }

                drawContext.canvas.nativeCanvas.drawText("h, м", left - 8f, top - 8f, axisPaint)
                drawContext.canvas.nativeCanvas.drawText("v, м/с", (left + right) / 2f, size.height - 6f, labelPaint)
                drawContext.canvas.nativeCanvas.drawText("дно", right - 4f, bottom - 8f, labelPaint)
            }
        }
    }
}

@Composable
private fun SimpleDataTable(
    title: String,
    header: List<String>,
    rows: List<List<String>>
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, fontWeight = FontWeight.Medium)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
        ) {
            Column {
                TableRow(cells = header, header = true)
                rows.forEach { TableRow(cells = it, header = false) }
            }
        }
    }
}

@Composable
private fun TableRow(cells: List<String>, header: Boolean) {
    Row {
        cells.forEach { cell ->
            Box(
                modifier = Modifier
                    .width(92.dp)
                    .wrapContentHeight()
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                    .background(
                        if (header) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                        else MaterialTheme.colorScheme.surface
                    )
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = cell,
                    textAlign = TextAlign.Center,
                    fontWeight = if (header) FontWeight.SemiBold else FontWeight.Normal
                )
            }
        }
    }
}

@Composable
private fun SegmentCard(segment: SegmentDischarge) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(segment.label, fontWeight = FontWeight.Bold)
            Text("x: ${formatNumber(segment.xFrom)}–${formatNumber(segment.xTo)} м · b = ${formatNumber(segment.width)} м")
            Text("F = ${formatNumber(segment.area)} м² · V = ${formatNumber(segment.velocity)} м/с")
            Text("q = ${formatNumber(segment.discharge)} м³/с")
        }
    }
}

@Composable
private fun MetricRow(title: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(title)
        Text(value, fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
    }
}

@Composable
private fun EmptyCard(title: String, subtitle: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun InfoCard(title: String, body: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, fontWeight = FontWeight.Bold)
            Text(body)
        }
    }
}

@Composable
private fun ErrorCard(message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(16.dp),
            color = MaterialTheme.colorScheme.onErrorContainer
        )
    }
}

private fun validatePassport(draft: SurveyDraft): String? {
    if (draft.name.isBlank()) return "Укажи имя замера."
    if (draft.river.isBlank()) return "Укажи реку."
    if (draft.dateText.isBlank()) return "Укажи дату."
    return null
}

private fun parseSection(draft: SurveyDraft): ParseState<SectionData> {
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

private fun previewLocalDepth(section: SectionData, distanceText: String): LocalDepthPreview? {
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

private fun parseVelocityStage(draft: SurveyDraft): ParseState<List<VelocityVertical>> {
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

private fun parseBanks(draft: SurveyDraft): ParseState<BankData> {
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

private fun parseSurvey(draft: SurveyDraft): ParseState<ParsedSurvey> {
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

private fun calculateDischarge(survey: ParsedSurvey): CalculationOutput {
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

private fun copyTextToClipboard(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
    Toast.makeText(context, "«$label» скопировано", Toast.LENGTH_SHORT).show()
}

private fun buildSummaryClipboardText(survey: ParsedSurvey, result: CalculationOutput): String = buildString {
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

private fun buildProfileTableClipboardText(section: SectionData): String = buildString {
    appendLine("№	Расстояние x, м	Глубина h, м")
    section.points.forEachIndexed { index, point ->
        appendLine("${index + 1}	${formatNumber(point.distance)}	${formatNumber(point.depth)}")
    }
}

private fun buildVelocityTableClipboardText(verticals: List<VelocityVertical>): String = buildString {
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

private fun buildSegmentsClipboardText(result: CalculationOutput): String = buildString {
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

private fun buildFullClipboardExport(survey: ParsedSurvey, result: CalculationOutput): String = buildString {
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

private fun depthAt(profilePoints: List<SectionPoint>, x: Double): Double {
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

private fun integrateArea(profilePoints: List<SectionPoint>, xFrom: Double, xTo: Double): Double {
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

private object SurveyStorage {
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

private fun SurveyDraft.toJson(): JSONObject = JSONObject().apply {
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

private fun JSONObject.toSurveyDraft(): SurveyDraft {
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

private fun String.parseFlexibleDouble(): Double? = trim().replace(',', '.').toDoubleOrNull()
private fun String.parseNullableDouble(): Double? = if (isBlank()) null else parseFlexibleDouble()

private fun List<SectionPointInput>.replaceSectionPoint(newValue: SectionPointInput): List<SectionPointInput> =
    map { if (it.id == newValue.id) newValue else it }

private fun List<VelocityVerticalInput>.replaceVelocityVertical(newValue: VelocityVerticalInput): List<VelocityVerticalInput> =
    map { if (it.id == newValue.id) newValue else it }

private fun formatNumber(value: Double): String {
    val rounded = (value * 1000.0).roundToInt() / 1000.0
    return if (rounded % 1.0 == 0.0) {
        rounded.toInt().toString()
    } else {
        String.format(Locale.US, "%.3f", rounded).trimEnd('0').trimEnd('.')
    }
}
