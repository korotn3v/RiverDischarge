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
internal fun HomeScreen(
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
internal fun SurveyCard(
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

internal data class EditorTab(val title: String, val emoji: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EditorScreen(
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
