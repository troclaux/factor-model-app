package com.buddy.budget.portfolio.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.buddy.budget.portfolio.PortfolioViewModel
import com.buddy.budget.portfolio.model.Frequency
import com.buddy.budget.portfolio.model.InputMode
import com.buddy.budget.portfolio.model.LookbackUnit
import com.buddy.budget.portfolio.model.PeriodMode
import com.buddy.budget.portfolio.model.PortfolioInput
import com.buddy.budget.portfolio.model.parseDate
import com.buddy.budget.portfolio.model.parseNumber
import java.time.Instant
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PortfolioScreen(viewModel: PortfolioViewModel) {
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(viewModel.error) {
        viewModel.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissError()
        }
    }
    LaunchedEffect(viewModel.message) {
        viewModel.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissMessage()
        }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Portfolio") },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
                PrimaryTabRow(selectedTabIndex = viewModel.selectedTab) {
                    Tab(selected = viewModel.selectedTab == 0, onClick = { viewModel.selectedTab = 0 }, text = { Text("Setup") })
                    Tab(selected = viewModel.selectedTab == 1, onClick = { viewModel.selectedTab = 1 }, text = { Text("Results") })
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (viewModel.selectedTab == 0) {
                Surface(tonalElevation = 3.dp) {
                    Button(
                        onClick = viewModel::analyze,
                        enabled = !viewModel.loading,
                        modifier = Modifier.fillMaxWidth().padding(16.dp).height(52.dp),
                    ) {
                        if (viewModel.loading) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(12.dp))
                            Text("Fetching prices…")
                        } else {
                            Icon(Icons.Default.QueryStats, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Analyze")
                        }
                    }
                }
            }
        },
    ) { padding ->
        if (viewModel.selectedTab == 0) {
            SetupContent(viewModel, Modifier.padding(padding))
        } else {
            ResultsContent(viewModel, Modifier.padding(padding))
        }
    }
}

@Composable
fun SectionCard(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SetupContent(viewModel: PortfolioViewModel, modifier: Modifier) {
    val settings = viewModel.settings

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SectionCard("Assets") {
                var tickerText by rememberSaveable { mutableStateOf("") }
                val addTicker = {
                    viewModel.addTickers(tickerText)
                    tickerText = ""
                }
                OutlinedTextField(
                    value = tickerText,
                    onValueChange = { tickerText = it },
                    label = { Text("Add tickers") },
                    placeholder = { Text("PETR4.SA, AAPL…") },
                    supportingText = { Text("B3 tickers need the .SA suffix") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { addTicker() }),
                    trailingIcon = {
                        IconButton(onClick = addTicker, enabled = tickerText.isNotBlank()) {
                            Icon(Icons.Default.Add, contentDescription = "Add")
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    settings.tickers.forEach { ticker ->
                        InputChip(
                            selected = false,
                            onClick = { viewModel.removeTicker(ticker) },
                            label = { Text(ticker) },
                            trailingIcon = { Icon(Icons.Default.Close, contentDescription = "Remove $ticker", Modifier.size(16.dp)) },
                        )
                    }
                }
                OutlinedTextField(
                    value = settings.marketProxy,
                    onValueChange = { value -> viewModel.update { it.copy(marketProxy = value) } },
                    label = { Text("Market benchmark") },
                    supportingText = { Text("Used for market betas, e.g. ^BVSP or SPY. Leave blank to skip.") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        item {
            SectionCard("Parameters") {
                OutlinedTextField(
                    value = settings.riskFreePercent,
                    onValueChange = { value -> viewModel.update { it.copy(riskFreePercent = value) } },
                    label = { Text("Risk-free rate (annual)") },
                    suffix = { Text("%") },
                    isError = settings.riskFreeProxy.isBlank() && parseNumber(settings.riskFreePercent) == null,
                    enabled = settings.riskFreeProxy.isBlank(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = settings.riskFreeProxy,
                    onValueChange = { value -> viewModel.update { it.copy(riskFreeProxy = value) } },
                    label = { Text("Risk-free proxy") },
                    supportingText = { Text("Yield ticker, e.g. ^IRX (13-week T-bill) or ^TNX (10-year). Overrides the rate above.") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Period", style = MaterialTheme.typography.labelLarge)
                ChoiceRow(PeriodMode.entries, settings.periodMode, { it.label }) { mode ->
                    viewModel.update { it.copy(periodMode = mode) }
                }
                if (settings.periodMode == PeriodMode.LOOKBACK) {
                    OutlinedTextField(
                        value = settings.lookbackAmount,
                        onValueChange = { value -> viewModel.update { it.copy(lookbackAmount = value) } },
                        label = { Text("Lookback") },
                        supportingText = { Text("Ends on the last trading day of the previous month") },
                        isError = (parseNumber(settings.lookbackAmount) ?: 0.0) <= 0.0,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    ChoiceRow(LookbackUnit.entries, settings.lookbackUnit, { it.label }) { unit ->
                        viewModel.update { it.copy(lookbackUnit = unit) }
                    }
                } else {
                    DateField("Start date", settings.startDate) { date -> viewModel.update { it.copy(startDate = date) } }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("End at latest available", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "Use the most recent close",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = settings.endLatest, onCheckedChange = { value -> viewModel.update { it.copy(endLatest = value) } })
                    }
                    if (!settings.endLatest) {
                        DateField("End date", settings.endDate) { date -> viewModel.update { it.copy(endDate = date) } }
                    }
                }
                Text("Return frequency", style = MaterialTheme.typography.labelLarge)
                ChoiceRow(Frequency.entries, settings.frequency, { it.label }) { frequency ->
                    viewModel.update { it.copy(frequency = frequency) }
                }
                if (settings.frequency == Frequency.MONTHLY && settings.periodMode == PeriodMode.LOOKBACK && settings.lookbackUnit != LookbackUnit.YEARS) {
                    Text(
                        "Short windows give few monthly returns. Daily is usually better here.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Allow short selling", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Off constrains every weight to be ≥ 0",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = settings.allowShort, onCheckedChange = { value -> viewModel.update { it.copy(allowShort = value) } })
                }
            }
        }

        item { OpenFinanceCard(viewModel) }

        item {
            PortfolioInputCard(
                title = "My portfolio",
                tickers = settings.tickers,
                input = settings.current,
                onChange = { input -> viewModel.update { it.copy(current = input) } },
                onAddTickers = viewModel::addTickers,
            )
        }

        item {
            PortfolioInputCard(
                title = "Hypothetical portfolio",
                tickers = settings.tickers,
                input = settings.hypothetical,
                onChange = { input -> viewModel.update { it.copy(hypothetical = input) } },
                onAddTickers = viewModel::addTickers,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> ChoiceRow(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                selected = option == selected,
                onClick = { onSelect(option) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
            ) { Text(label(option), maxLines = 1) }
        }
    }
}

/** A read-only date field (yyyy-MM-dd) that opens a date picker when tapped. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateField(label: String, value: String, onChange: (String) -> Unit) {
    var picking by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { if (it is PressInteraction.Release) picking = true }
    }
    OutlinedTextField(
        value = value,
        onValueChange = {},
        readOnly = true,
        label = { Text(label) },
        placeholder = { Text("Tap to pick a date") },
        isError = parseDate(value) == null,
        trailingIcon = { Icon(Icons.Default.CalendarMonth, contentDescription = null) },
        singleLine = true,
        interactionSource = interactionSource,
        modifier = Modifier.fillMaxWidth(),
    )
    if (picking) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = parseDate(value)?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { onChange(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString()) }
                    picking = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text("Cancel") } },
        ) {
            DatePicker(state)
        }
    }
}

/** Pluggy credentials and the import button that fills "My portfolio" from Open Finance. */
@Composable
private fun OpenFinanceCard(viewModel: PortfolioViewModel) {
    val settings = viewModel.settings
    var expanded by rememberSaveable { mutableStateOf(false) }
    val configured = settings.pluggyClientId.isNotBlank() && settings.pluggyClientSecret.isNotBlank() && settings.pluggyItemIds.isNotBlank()

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth().clickable { expanded = !expanded }, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Open Finance (Pluggy)", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (configured) "Ready to import your holdings" else "Not set up (optional)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null)
            }
            AnimatedVisibility(expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Connect your broker at meu.pluggy.ai, then link MeuPluggy in the demo app at dashboard.pluggy.ai. " +
                            "Paste that app's client ID and secret and the connection's item ID. Stored only on this phone.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = settings.pluggyClientId,
                        onValueChange = { value -> viewModel.update { it.copy(pluggyClientId = value) } },
                        label = { Text("Client ID") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = settings.pluggyClientSecret,
                        onValueChange = { value -> viewModel.update { it.copy(pluggyClientSecret = value) } },
                        label = { Text("Client secret") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = settings.pluggyItemIds,
                        onValueChange = { value -> viewModel.update { it.copy(pluggyItemIds = value) } },
                        label = { Text("Item IDs") },
                        supportingText = { Text("One per broker connection, separated by commas") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            Button(
                onClick = viewModel::importHoldings,
                enabled = configured && !viewModel.importing,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (viewModel.importing) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text("Importing…")
                } else {
                    Text("Import my holdings")
                }
            }
            Text(
                "Replaces My portfolio's share counts with your stocks, FIIs, BDRs and ETFs and adds missing tickers.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Collapsible editor for a portfolio given as share counts or weights, one field per ticker. */
@Composable
private fun PortfolioInputCard(
    title: String,
    tickers: List<String>,
    input: PortfolioInput,
    onChange: (PortfolioInput) -> Unit,
    onAddTickers: (String) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var pasting by remember { mutableStateOf(false) }
    val filled = tickers.count { (parseNumber(input.values[it] ?: "") ?: 0.0) != 0.0 }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth().clickable { expanded = !expanded }, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (filled == 0) "Not set (optional)" else "$filled positions · ${input.mode.label.lowercase()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null)
            }
            AnimatedVisibility(expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChoiceRow(InputMode.entries, input.mode, { it.label }) { onChange(input.copy(mode = it)) }
                    Row {
                        TextButton(onClick = { pasting = true }) { Text("Paste list") }
                        TextButton(onClick = { onChange(input.copy(values = emptyMap())) }) { Text("Clear") }
                    }
                    tickers.forEach { ticker ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(ticker, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                            OutlinedTextField(
                                value = input.values[ticker] ?: "",
                                onValueChange = { value -> onChange(input.copy(values = input.values + (ticker to value))) },
                                placeholder = { Text("0") },
                                suffix = if (input.mode == InputMode.WEIGHTS) ({ Text("%") }) else null,
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.width(140.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    if (pasting) {
        PasteDialog(
            mode = input.mode,
            onDismiss = { pasting = false },
            onConfirm = { parsed ->
                pasting = false
                onAddTickers(parsed.keys.joinToString(" "))
                onChange(input.copy(values = input.values + parsed))
            },
        )
    }
}

/** Accepts the CLI's file format: one "TICKER VALUE" per line, # for comments. */
@Composable
private fun PasteDialog(mode: InputMode, onDismiss: () -> Unit, onConfirm: (Map<String, String>) -> Unit) {
    var text by remember { mutableStateOf("") }
    val parsed = text.lines()
        .map { it.substringBefore("#").trim() }
        .filter { it.isNotEmpty() }
        .mapNotNull { line ->
            val parts = line.split(Regex("\\s+"))
            if (parts.size == 2 && parseNumber(parts[1]) != null) parts[0].uppercase() to parts[1] else null
        }
        .toMap()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Paste ${mode.label.lowercase()}") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text(if (mode == InputMode.SHARES) "EGIE3.SA 157\nITUB3.SA 310" else "EGIE3.SA 25\nITUB3.SA 75") },
                supportingText = { Text("One TICKER VALUE per line · ${parsed.size} recognized") },
                minLines = 6,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(parsed) }, enabled = parsed.isNotEmpty()) { Text("Apply") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
