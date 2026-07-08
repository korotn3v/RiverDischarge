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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.runtime.rememberUpdatedState
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

@Composable
internal fun PassportStep(draft: SurveyDraft, onDraftChange: (SurveyDraft) -> Unit) {
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
internal fun SectionStep(
    draft: SurveyDraft,
    sectionState: ParseState<SectionData>,
    onDraftChange: (SurveyDraft) -> Unit,
    modifier: Modifier = Modifier
) {
    // Stable per-row callbacks: they capture only the updated-state holder, never the draft itself,
    // so unchanged point editors can skip recomposition while typing in another row.
    val currentDraft by rememberUpdatedState(draft)
    val onPointChange: (SectionPointInput) -> Unit = remember(onDraftChange) {
        { updated ->
            onDraftChange(currentDraft.copy(sectionPoints = currentDraft.sectionPoints.replaceSectionPoint(updated)))
        }
    }
    val onPointDelete: (String) -> Unit = remember(onDraftChange) {
        { id ->
            onDraftChange(currentDraft.copy(sectionPoints = currentDraft.sectionPoints.filterNot { it.id == id }))
        }
    }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "start-bank") {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Стартовый берег", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        BankSide.entries.forEach { side ->
                            FilterChip(
                                selected = draft.startSide == side,
                                onClick = { onDraftChange(currentDraft.copy(startSide = side)) },
                                label = { Text(side.title) }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = draft.startEdgeText,
                        onValueChange = { onDraftChange(currentDraft.copy(startEdgeText = it)) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Урез стартового берега, м") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                }
            }
        }

        item(key = "points-header") {
            Text("Точки глубины", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        }
        if (draft.sectionPoints.isEmpty()) {
            item(key = "points-empty") {
                EmptyCard("Нет точек", "Добавь точки промера внутри русла.")
            }
        } else {
            itemsIndexed(draft.sectionPoints, key = { _, point -> point.id }) { index, point ->
                SectionPointEditor(
                    index = index,
                    point = point,
                    canDelete = draft.sectionPoints.size > 1,
                    onChange = onPointChange,
                    onDelete = onPointDelete
                )
            }
        }
        item(key = "points-add") {
            Button(
                onClick = {
                    onDraftChange(currentDraft.copy(sectionPoints = currentDraft.sectionPoints + SectionPointInput()))
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("+ Добавить точку") }
        }

        item(key = "end-bank") {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Дальний берег", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = draft.endEdgeText,
                        onValueChange = { onDraftChange(currentDraft.copy(endEdgeText = it)) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Урез противоположного берега, м") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                }
            }
        }

        when (sectionState) {
            is ParseState.Error -> item(key = "section-error") { ErrorCard(sectionState.message) }
            is ParseState.Ok -> {
                item(key = "section-width") {
                    InfoCard(
                        title = "Ширина русла",
                        body = "${formatNumber(sectionState.value.wettedWidth)} м между урезами."
                    )
                }
                item(key = "section-chart") {
                    SectionProfileCard(section = sectionState.value, velocityVerticals = emptyList())
                }
            }
        }
    }
}

@Composable
internal fun VelocityStep(
    draft: SurveyDraft,
    sectionState: ParseState<SectionData>,
    velocityState: ParseState<List<VelocityVertical>>,
    onDraftChange: (SurveyDraft) -> Unit,
    modifier: Modifier = Modifier
) {
    // Same stable-callback setup as SectionStep: rows skip recomposition unless their own data changed.
    val currentDraft by rememberUpdatedState(draft)
    val onVerticalChange: (VelocityVerticalInput) -> Unit = remember(onDraftChange) {
        { updated ->
            onDraftChange(currentDraft.copy(velocityVerticals = currentDraft.velocityVerticals.replaceVelocityVertical(updated)))
        }
    }
    val onVerticalDelete: (String) -> Unit = remember(onDraftChange) {
        { id ->
            onDraftChange(currentDraft.copy(velocityVerticals = currentDraft.velocityVerticals.filterNot { it.id == id }))
        }
    }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        when (sectionState) {
            is ParseState.Error -> item(key = "section-error") {
                ErrorCard("Сначала заполни профиль русла: ${sectionState.message}")
            }
            is ParseState.Ok -> {
                item(key = "velocity-header") {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Скорости на вертикалях", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(
                                "Только вертикали, где мерилась скорость. Свободное русло: 1 точка (0.6h), 2 точки (0.2h/0.8h) или 5 точек. Ледостав / зарастание: 3 или 6 точек.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                if (draft.velocityVerticals.isEmpty()) {
                    item(key = "verticals-empty") {
                        EmptyCard(
                            title = "Нет вертикалей",
                            subtitle = "Добавь вертикаль скорости — не обязательно во всех точках профиля."
                        )
                    }
                } else {
                    itemsIndexed(draft.velocityVerticals, key = { _, vertical -> vertical.id }) { index, vertical ->
                        val preview = previewLocalDepth(sectionState.value, vertical.distanceText)
                        VelocityEditorCard(
                            index = index,
                            input = vertical,
                            preview = preview,
                            canDelete = draft.velocityVerticals.size > 1,
                            onChange = onVerticalChange,
                            onDelete = onVerticalDelete
                        )
                    }
                }

                item(key = "verticals-add") {
                    Button(
                        onClick = {
                            onDraftChange(currentDraft.copy(velocityVerticals = currentDraft.velocityVerticals + VelocityVerticalInput()))
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("+ Добавить вертикаль") }
                }

                when (velocityState) {
                    is ParseState.Error -> item(key = "velocity-error") { ErrorCard(velocityState.message) }
                    is ParseState.Ok -> {
                        item(key = "velocity-means") {
                            InfoCard(
                                title = "Средние скорости",
                                body = velocityState.value.joinToString("\n") {
                                    "x=${formatNumber(it.distance)} · h=${formatNumber(it.localDepth)} · Vср=${formatNumber(it.meanVelocity)}"
                                }
                            )
                        }
                        item(key = "velocity-chart") {
                            SectionProfileCard(section = sectionState.value, velocityVerticals = velocityState.value)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BanksStep(
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
internal fun ResultStep(
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
            val sectionMaxDepth = (sectionState as? ParseState.Ok)
                ?.value?.profilePoints?.maxOfOrNull { it.depth } ?: 0.0
            VelocityProfilesCard(velocityState.value, sectionMaxDepth = sectionMaxDepth)
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
internal fun BankSelector(
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
internal fun SectionPointEditor(
    index: Int,
    point: SectionPointInput,
    canDelete: Boolean,
    onChange: (SectionPointInput) -> Unit,
    onDelete: (String) -> Unit
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
                    TextButton(onClick = { onDelete(point.id) }) { Text("Удалить") }
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
internal fun VelocityEditorCard(
    index: Int,
    input: VelocityVerticalInput,
    preview: LocalDepthPreview?,
    canDelete: Boolean,
    onChange: (VelocityVerticalInput) -> Unit,
    onDelete: (String) -> Unit
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
                    TextButton(onClick = { onDelete(input.id) }) { Text("Удалить") }
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
