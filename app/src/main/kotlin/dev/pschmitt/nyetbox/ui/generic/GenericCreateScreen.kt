package dev.pschmitt.nyetbox.ui.generic

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.pschmitt.nyetbox.data.repository.CreateChoice
import dev.pschmitt.nyetbox.data.repository.CreateFieldDefinition
import dev.pschmitt.nyetbox.data.repository.choiceSearchFieldLabel
import dev.pschmitt.nyetbox.data.repository.choiceSearchHint
import dev.pschmitt.nyetbox.data.repository.choiceSearchMatches
import dev.pschmitt.nyetbox.ui.common.RemoteThumbnail

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GenericCreateScreen(
    onBack: () -> Unit,
    onCreated: (endpointPath: String, id: Int, display: String?) -> Unit,
    viewModel: GenericCreateViewModel = hiltViewModel(),
) {
    val fields by viewModel.fields.collectAsStateWithLifecycle()
    val referenceOptions by viewModel.referenceOptions.collectAsStateWithLifecycle()
    val values by viewModel.values.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val isSaving by viewModel.isSaving.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val createdId by viewModel.createdId.collectAsStateWithLifecycle()
    val createdDisplay by viewModel.createdDisplay.collectAsStateWithLifecycle()

    LaunchedEffect(createdId) {
        createdId?.let { onCreated(viewModel.route.endpointPath, it, createdDisplay) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Create ${viewModel.route.label}") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        }
    ) { padding ->
        when {
            isLoading ->
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            fields.isEmpty() ->
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text(errorMessage ?: "This model has no writable fields")
                }
            else ->
                LazyColumn(
                    modifier = Modifier.padding(padding).fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    errorMessage?.let { message ->
                        item {
                            Row(
                                verticalAlignment = Alignment.Top,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Icon(
                                    Icons.Default.Error,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                )
                                Text(
                                    message,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(start = 8.dp),
                                )
                            }
                        }
                    }
                    items(fields, key = { it.key }) { field ->
                        val options =
                            field.choices.ifEmpty { referenceOptions[field.key].orEmpty() }
                        Column {
                            CreateFieldInput(
                                field = field,
                                value = values[field.key].orEmpty(),
                                options = options,
                                localImageFile = viewModel::localImageFile,
                                onValueChange = viewModel::setValue,
                            )
                            field.helpText
                                ?.takeIf { it.isNotBlank() }
                                ?.let {
                                    Text(
                                        it,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                        }
                    }
                    item {
                        Button(
                            onClick = viewModel::create,
                            enabled = !isSaving,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            if (isSaving) CircularProgressIndicator(strokeWidth = 2.dp)
                            else Icon(Icons.Default.Add, contentDescription = null)
                            Text(if (isSaving) " Creating…" else " Create")
                        }
                    }
                }
        }
    }
}

@Composable
private fun CreateFieldInput(
    field: CreateFieldDefinition,
    value: String,
    options: List<CreateChoice>,
    localImageFile: (String, String) -> java.io.File?,
    onValueChange: (String, String) -> Unit,
) {
    val label = if (field.required) "${field.label} *" else field.label
    if (field.type == "boolean") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Switch(
                checked = value.toBooleanStrictOrNull() ?: false,
                onCheckedChange = { onValueChange(field.key, it.toString()) },
            )
        }
        return
    }
    if (field.markdown) {
        MarkdownEditor(
            value = value,
            label = label,
            onValueChange = { onValueChange(field.key, it) },
        )
        return
    }
    if (field.multiple) {
        if (options.isEmpty()) {
            OutlinedTextField(
                value = value,
                onValueChange = { onValueChange(field.key, it) },
                label = { Text(label) },
                supportingText = { Text("Enter comma-separated values or a JSON array") },
                modifier = Modifier.fillMaxWidth(),
            )
        } else if (field.type in setOf("multiple-object", "multiple_object", "multiobject")) {
            OutlinedTextField(
                value = value,
                onValueChange = { onValueChange(field.key, it) },
                label = { Text(label) },
                supportingText = { Text("Enter related object IDs separated by commas") },
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            CreateMultiChoiceInput(field, value, options, onValueChange)
        }
        return
    }
    if (options.isNotEmpty() || field.referenceEndpointPath != null) {
        CreateChoiceInput(field, value, options, localImageFile, onValueChange)
        return
    }
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(field.key, it) },
        label = { Text(label) },
        minLines = if (field.type == "json") 3 else 1,
        supportingText =
            when {
                field.type == "json" -> ({ Text("Enter a valid JSON value") })
                field.type == "nested object" -> ({ Text("Enter the related object ID") })
                else -> null
            },
        keyboardOptions =
            KeyboardOptions(
                keyboardType =
                    when (field.type) {
                        "integer",
                        "decimal",
                        "float",
                        "nested object",
                        "object" -> KeyboardType.Number
                        else -> KeyboardType.Text
                    }
            ),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
internal fun CreateMultiChoiceInput(
    field: CreateFieldDefinition,
    value: String,
    options: List<CreateChoice>,
    onValueChange: (String, String) -> Unit,
) {
    var expanded by remember(field.key) { mutableStateOf(false) }
    val selected = selectedValuesFromJson(value).toSet()
    val selectedLabel =
        options
            .filter { it.value in selected }
            .joinToString(", ") { it.label }
            .takeIf { it.isNotBlank() } ?: "None"
    Column {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text(if (field.required) "${field.label} *" else field.label) },
            trailingIcon = {
                IconButton(onClick = { expanded = true }) {
                    Icon(Icons.Default.ArrowDropDown, contentDescription = "Choose ${field.label}")
                }
            },
            modifier =
                Modifier.fillMaxWidth()
                    .testTag("create-multi-choice-field-${field.key}")
                    .clickable { expanded = true },
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.fillMaxWidth(0.9f),
        ) {
            DropdownMenuItem(
                text = { Text("Clear all") },
                leadingIcon = { Icon(Icons.Default.Clear, contentDescription = null) },
                onClick = {
                    onValueChange(field.key, selectedValuesToJson(emptyList()))
                    expanded = false
                },
            )
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    leadingIcon = {
                        Checkbox(checked = option.value in selected, onCheckedChange = null)
                    },
                    onClick = {
                        val next =
                            if (option.value in selected) selected - option.value
                            else selected + option.value
                        onValueChange(field.key, selectedValuesToJson(next))
                    },
                )
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun CreateChoiceInput(
    field: CreateFieldDefinition,
    value: String,
    options: List<CreateChoice>,
    localImageFile: (String, String) -> java.io.File?,
    onValueChange: (String, String) -> Unit,
) {
    var expanded by remember(field.key) { mutableStateOf(false) }
    var queryValue by remember(field.key) { mutableStateOf(TextFieldValue()) }
    val label = if (field.required) "${field.label} *" else field.label
    val query = queryValue.text
    val filteredOptions = filterCreateChoices(options, query)
    Box {
        OutlinedTextField(
            value = options.firstOrNull { it.value == value }?.label ?: value,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = {
                IconButton(
                    onClick = {
                        queryValue = TextFieldValue()
                        expanded = true
                    }
                ) {
                    Icon(Icons.Default.ArrowDropDown, contentDescription = "Choose ${field.label}")
                }
            },
            modifier =
                Modifier.fillMaxWidth().testTag("create-choice-field-${field.key}").clickable {
                    queryValue = TextFieldValue()
                    expanded = true
                },
        )
        if (expanded) {
            ModalBottomSheet(
                onDismissRequest = {
                    expanded = false
                    queryValue = TextFieldValue()
                }
            ) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    Text(field.label, style = MaterialTheme.typography.headlineSmall)
                    CreateChoiceSearchField(
                        value = queryValue,
                        onValueChange = { queryValue = it },
                        label = "Search ${field.label}",
                    )
                    val fieldSuggestions = createChoiceFieldSuggestions(options, query)
                    if (fieldSuggestions.isNotEmpty()) {
                        Column(Modifier.fillMaxWidth()) {
                            Text(
                                "Filter by related field",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            fieldSuggestions.forEach { suggestion ->
                                ListItem(
                                    leadingContent = {
                                        Icon(Icons.Default.FilterAlt, contentDescription = null)
                                    },
                                    headlineContent = { Text(suggestion.label) },
                                    supportingContent = {
                                        Text("Type ${suggestion.key} followed by a value")
                                    },
                                    modifier =
                                        Modifier.clickable {
                                            val nextQuery = "${suggestion.key} "
                                            queryValue =
                                                TextFieldValue(
                                                    text = nextQuery,
                                                    selection = TextRange(nextQuery.length),
                                                )
                                        },
                                )
                            }
                        }
                    }
                    if (!field.required) {
                        DropdownMenuItem(
                            text = { Text("Clear") },
                            leadingIcon = {
                                Icon(Icons.Default.Clear, contentDescription = null)
                            },
                            onClick = {
                                onValueChange(field.key, "")
                                expanded = false
                                queryValue = TextFieldValue()
                            },
                        )
                    }
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 560.dp)) {
                        items(filteredOptions, key = { it.value }) { option ->
                            val matchHint =
                                choiceSearchHint(
                                    label = option.label,
                                    value = option.value,
                                    searchFields = option.searchFields,
                                    query = query,
                                )
                            ListItem(
                                headlineContent = { Text(option.label) },
                                supportingContent =
                                    matchHint?.let { hint ->
                                        {
                                            Text(
                                                "Matched $hint",
                                                color = MaterialTheme.colorScheme.primary,
                                            )
                                        }
                                    },
                                leadingContent = {
                                    CreateChoicePreview(option, localImageFile)
                                },
                                modifier =
                                    Modifier.clickable {
                                        onValueChange(field.key, option.value)
                                        expanded = false
                                        queryValue = TextFieldValue()
                                    },
                            )
                        }
                        if (filteredOptions.isEmpty()) {
                            item {
                                Text(
                                    if (options.isEmpty()) {
                                        "No cached choices available"
                                    } else {
                                        "No matches"
                                    },
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(24.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CreateChoiceSearchField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    label: String,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = {
            if (value.text.isNotEmpty()) {
                IconButton(onClick = { onValueChange(TextFieldValue()) }) {
                    Icon(Icons.Default.Clear, contentDescription = "Clear search")
                }
            }
        },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
    )
}

internal data class CreateChoiceFieldSuggestion(val key: String, val label: String)

internal fun createChoiceFieldSuggestions(
    options: List<CreateChoice>,
    query: String,
    limit: Int = 5,
): List<CreateChoiceFieldSuggestion> {
    val prefix = query.trimStart().takeWhile { !it.isWhitespace() && it != ':' }.lowercase()
    if (prefix.length < 2 || query.trimStart().drop(prefix.length).isNotBlank()) return emptyList()
    return options
        .asSequence()
        .flatMap { it.searchFields.keys.asSequence() }
        .distinct()
        .filter { key ->
            key.lowercase().startsWith(prefix) ||
                choiceSearchFieldLabel(key).lowercase().startsWith(prefix)
        }
        .sorted()
        .map { key -> CreateChoiceFieldSuggestion(key, choiceSearchFieldLabel(key)) }
        .take(limit)
        .toList()
}

internal fun filterCreateChoices(
    options: List<CreateChoice>,
    query: String,
): List<CreateChoice> {
    if (query.isBlank()) return options
    return options.filter { option ->
        choiceSearchMatches(option.label, option.value, option.searchFields, query).isNotEmpty()
    }
}

@Composable
private fun CreateChoicePreview(
    option: CreateChoice,
    localImageFile: (String, String) -> java.io.File?,
) {
    val hasImages = !option.frontImageUrl.isNullOrBlank() || !option.rearImageUrl.isNullOrBlank()
    if (!hasImages) {
        Icon(Icons.Default.Link, contentDescription = null)
        return
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.width(76.dp),
    ) {
        option.frontImageUrl?.let { url ->
            RemoteThumbnail(
                imageUrl = url,
                contentDescription = "Front image",
                localFile = localImageFile(url, "device-type-${option.value}-front"),
                modifier = Modifier.size(34.dp),
                contentScale = ContentScale.Crop,
            )
        }
        option.rearImageUrl?.let { url ->
            RemoteThumbnail(
                imageUrl = url,
                contentDescription = "Rear image",
                localFile = localImageFile(url, "device-type-${option.value}-rear"),
                modifier = Modifier.size(34.dp),
                contentScale = ContentScale.Crop,
            )
        }
    }
}
