package dev.pschmitt.nyetbox.ui.generic

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.pschmitt.nyetbox.data.repository.choiceSearchHint

@Composable
internal fun EditDiffDialog(
    fields: List<EditableField>,
    edits: Map<String, Pair<EditFieldKind, String>>,
    referenceOptions: Map<String, List<EditOption>>,
    choiceOptions: Map<String, List<EditOption>>,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val fieldsByKey = fields.associateBy { it.key }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Edit, contentDescription = null) },
        title = { Text("Review changes") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                edits.forEach { (key, valueWithKind) ->
                    val field = fieldsByKey[key]
                    val before =
                        displayEditValue(
                            field,
                            field?.value,
                            referenceOptions,
                            choiceOptions,
                        )
                    val after =
                        displayEditValue(
                            field,
                            valueWithKind.second,
                            referenceOptions,
                            choiceOptions,
                        )
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(12.dp),
                        tonalElevation = 1.dp,
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                field?.label ?: key,
                                style = MaterialTheme.typography.titleSmall,
                            )
                            DiffValueRow(
                                prefix = "− Before",
                                value = before,
                                background = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                            )
                            DiffValueRow(
                                prefix = "+ After",
                                value = after,
                                background = MaterialTheme.colorScheme.tertiaryContainer,
                                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                            )
                        }
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Revert")
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Icon(Icons.Default.Check, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Confirm changes")
            }
        },
    )
}

@Composable
internal fun DiffValueRow(
    prefix: String,
    value: String,
    background: Color,
    contentColor: Color,
) {
    Surface(
        color = background,
        contentColor = contentColor,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Text(
                prefix,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.width(72.dp),
            )
            Text(value, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
internal fun EditForm(
    fields: List<EditableField>,
    values: Map<String, String>,
    referenceOptions: Map<String, List<EditOption>>,
    choiceOptions: Map<String, List<EditOption>>,
    onValueChange: (key: String, value: String) -> Unit,
    errorMessage: String?,
    onCreateLinkedItem: (EditableField) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // A bold, persistent banner rather than a Snackbar - a toast is easy to miss entirely
        // while the keyboard is open (it can render behind it), and a save failure here is
        // exactly the moment the user most needs to notice something went wrong.
        errorMessage?.let { message ->
            item {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
                        Icon(Icons.Default.Error, contentDescription = null)
                        Spacer(Modifier.width(12.dp))
                        Text(message, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
        items(fields, key = { it.key }) { field ->
            val value = values[field.key] ?: field.value
            val changed = value != field.value
            Box(
                modifier =
                    Modifier.fillMaxWidth()
                        .then(
                            if (changed) {
                                Modifier.border(
                                        2.dp,
                                        MaterialTheme.colorScheme.primary,
                                        RoundedCornerShape(12.dp),
                                    )
                                    .padding(6.dp)
                            } else {
                                Modifier
                            }
                        )
            ) {
                EditFieldControl(
                    field = field,
                    value = value,
                    referenceOptions = referenceOptions,
                    choiceOptions = choiceOptions,
                    onCreateLinkedItem = {
                        if (field.referenceEndpointPath != null) onCreateLinkedItem(field)
                    },
                    onValueChange = { onValueChange(field.key, it) },
                )
            }
        }
    }
}

@Composable
internal fun EditFieldControl(
    field: EditableField,
    value: String,
    referenceOptions: Map<String, List<EditOption>>,
    choiceOptions: Map<String, List<EditOption>>,
    onCreateLinkedItem: () -> Unit,
    onValueChange: (String) -> Unit,
) {
    when (field.kind) {
        EditFieldKind.BOOLEAN ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(field.label, style = MaterialTheme.typography.bodyLarge)
                Switch(
                    checked = value.toBooleanStrictOrNull() ?: false,
                    onCheckedChange = { onValueChange(it.toString()) },
                )
            }
        EditFieldKind.NUMBER,
        EditFieldKind.INTEGER ->
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                label = { Text(field.label) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
        EditFieldKind.LONG_TEXT ->
            if (field.markdown) {
                MarkdownEditor(
                    value = value,
                    label = field.label,
                    onValueChange = onValueChange,
                )
            } else {
                OutlinedTextField(
                    value = value,
                    onValueChange = onValueChange,
                    label = { Text(field.label) },
                    minLines = 3,
                    maxLines = 8,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        EditFieldKind.JSON ->
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                label = { Text(field.label) },
                minLines = 3,
                maxLines = 8,
                supportingText = { Text("Enter a valid JSON value") },
                modifier = Modifier.fillMaxWidth(),
            )
        EditFieldKind.STRING ->
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                label = { Text(field.label) },
                modifier = Modifier.fillMaxWidth(),
            )
        EditFieldKind.REFERENCE ->
            EditPickerField(
                field = field,
                value = value,
                options = referenceOptions[field.key].orEmpty(),
                onValueChange = { _, next -> onValueChange(next) },
                allowClear = true,
                onCreateLinkedItem = onCreateLinkedItem,
            )
        EditFieldKind.CHOICE ->
            EditPickerField(
                field = field,
                value = value,
                options = choiceOptions[field.key].orEmpty(),
                onValueChange = { _, next -> onValueChange(next) },
                allowClear = field.customFieldName != null,
            )
        EditFieldKind.MULTI_REFERENCE,
        EditFieldKind.MULTI_CHOICE ->
            if (
                (if (field.kind == EditFieldKind.MULTI_REFERENCE) {
                        referenceOptions[field.key].orEmpty()
                    } else {
                        choiceOptions[field.key].orEmpty()
                    })
                    .isEmpty()
            ) {
                OutlinedTextField(
                    value = value,
                    onValueChange = onValueChange,
                    label = { Text(field.label) },
                    minLines = 2,
                    supportingText = { Text("Enter comma-separated values or a JSON array") },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                EditMultiPickerField(
                    field = field,
                    value = value,
                    options =
                        if (field.kind == EditFieldKind.MULTI_REFERENCE) {
                            referenceOptions[field.key].orEmpty()
                        } else {
                            choiceOptions[field.key].orEmpty()
                        },
                    onValueChange = { _, next -> onValueChange(next) },
                    onCreateLinkedItem =
                        if (field.kind == EditFieldKind.MULTI_REFERENCE) onCreateLinkedItem
                        else null,
                )
            }
    }
}

@Composable
internal fun FocusedEditFieldDialog(
    field: EditableField,
    value: String,
    referenceOptions: Map<String, List<EditOption>>,
    choiceOptions: Map<String, List<EditOption>>,
    onCreateLinkedItem: (EditableField) -> Unit,
    onValueChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onReview: (String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Edit, contentDescription = null) },
        title = { Text("Edit ${field.label}") },
        text = {
            EditFieldControl(
                field = field,
                value = value,
                referenceOptions = referenceOptions,
                choiceOptions = choiceOptions,
                onCreateLinkedItem = {
                    if (field.referenceEndpointPath != null) onCreateLinkedItem(field)
                },
                onValueChange = onValueChange,
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Cancel")
            }
        },
        confirmButton = {
            TextButton(onClick = { onReview(value) }) {
                Icon(Icons.Default.Check, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Review")
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EditMultiPickerField(
    field: EditableField,
    value: String,
    options: List<EditOption>,
    onValueChange: (key: String, value: String) -> Unit,
    onCreateLinkedItem: (() -> Unit)? = null,
) {
    var expanded by remember(field.key) { mutableStateOf(false) }
    var query by remember(field.key) { mutableStateOf("") }
    val selected = selectedValuesFromJson(value).toSet()
    val filteredOptions = filterEditOptions(options, query)
    val selectedLabel =
        options
            .filter { it.value in selected }
            .joinToString(", ") { it.label }
            .takeIf { it.isNotBlank() }
            ?: field.currentDisplay?.takeIf { it.isNotBlank() }
            ?: "None"
    Column {
        Box(Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = selectedLabel,
                onValueChange = {},
                readOnly = true,
                label = { Text(field.label) },
                trailingIcon = {
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Box(
                modifier =
                    Modifier.matchParentSize().clickable(
                        onClickLabel = "Choose ${field.label}",
                        role = Role.Button,
                    ) {
                        query = ""
                        expanded = true
                    }
            )
        }
        if (expanded) {
            ModalBottomSheet(
                onDismissRequest = {
                    expanded = false
                    query = ""
                }
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    Text(field.label, style = MaterialTheme.typography.headlineSmall)
                    EditOptionSearchField(
                        query = query,
                        onQueryChange = { query = it },
                        label = "Search ${field.label}",
                    )
                    onCreateLinkedItem?.let { createLinkedItem ->
                        ListItem(
                            headlineContent = { Text("Create new ${field.label}") },
                            leadingContent = {
                                Icon(Icons.Default.Add, contentDescription = null)
                            },
                            modifier =
                                Modifier.clickable {
                                    expanded = false
                                    query = ""
                                    createLinkedItem()
                                },
                        )
                    }
                    ListItem(
                        headlineContent = { Text("Clear all") },
                        leadingContent = { Icon(Icons.Default.Clear, contentDescription = null) },
                        modifier =
                            Modifier.clickable {
                                onValueChange(field.key, selectedValuesToJson(emptyList()))
                            },
                    )
                    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp)) {
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
                                leadingContent = { EditOptionPreview(option) },
                                trailingContent = {
                                    Checkbox(
                                        checked = option.value in selected,
                                        onCheckedChange = null,
                                    )
                                },
                                modifier =
                                    Modifier.clickable {
                                        val next =
                                            if (option.value in selected) selected - option.value
                                            else selected + option.value
                                        onValueChange(field.key, selectedValuesToJson(next))
                                    },
                            )
                        }
                        if (filteredOptions.isEmpty()) {
                            item {
                                Text(
                                    if (options.isEmpty()) "No choices available" else "No matches",
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EditPickerField(
    field: EditableField,
    value: String,
    options: List<EditOption>,
    onValueChange: (key: String, value: String) -> Unit,
    allowClear: Boolean,
    onCreateLinkedItem: (() -> Unit)? = null,
) {
    var expanded by remember(field.key) { mutableStateOf(false) }
    var query by remember(field.key) { mutableStateOf("") }
    val selectedLabel =
        options.firstOrNull { it.value == value }?.label
            ?: field.currentDisplay
            ?: value.takeIf { it.isNotBlank() }
            ?: "None"
    Column {
        Box(Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = selectedLabel,
                onValueChange = {},
                readOnly = true,
                label = { Text(field.label) },
                trailingIcon = {
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Box(
                modifier =
                    Modifier.matchParentSize().clickable(
                        onClickLabel = "Choose ${field.label}",
                        role = Role.Button,
                    ) {
                        query = ""
                        expanded = true
                    }
            )
        }
        if (expanded) {
            val filteredOptions = filterEditOptions(options, query)
            ModalBottomSheet(
                onDismissRequest = {
                    expanded = false
                    query = ""
                }
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    Text(field.label, style = MaterialTheme.typography.headlineSmall)
                    EditOptionSearchField(
                        query = query,
                        onQueryChange = { query = it },
                        label = "Search ${field.label}",
                    )
                    onCreateLinkedItem?.let { createLinkedItem ->
                        ListItem(
                            headlineContent = { Text("Create new ${field.label}") },
                            leadingContent = {
                                Icon(Icons.Default.Add, contentDescription = null)
                            },
                            modifier =
                                Modifier.clickable {
                                    expanded = false
                                    query = ""
                                    createLinkedItem()
                                },
                        )
                    }
                    if (allowClear) {
                        ListItem(
                            headlineContent = { Text("None") },
                            leadingContent = {
                                Icon(Icons.Default.Clear, contentDescription = null)
                            },
                            modifier =
                                Modifier.clickable {
                                    onValueChange(field.key, "")
                                    expanded = false
                                },
                        )
                    }
                    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp)) {
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
                                leadingContent = { EditOptionPreview(option) },
                                modifier =
                                    Modifier.clickable {
                                        onValueChange(field.key, option.value)
                                        expanded = false
                                    },
                            )
                        }
                        if (filteredOptions.isEmpty()) {
                            item {
                                Text(
                                    if (options.isEmpty()) "No choices available" else "No matches",
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
