package dev.pschmitt.netboxandchill.ui.common

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.io.File

@Composable
fun MediaUploadDialog(
    endpointPath: String,
    objectId: Int,
    onDismiss: () -> Unit,
    onUploaded: () -> Unit,
    viewModel: MediaUploadViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val documentEndpointPath by viewModel.documentEndpointPath.collectAsStateWithLifecycle()
    val documentTypeOptions by viewModel.documentTypeOptions.collectAsStateWithLifecycle()
    val initialKind =
        if (endpointPath == "api/dcim/device-types/") MediaUploadKind.DeviceTypeFront
        else MediaUploadKind.ImageAttachment
    var kind by remember(endpointPath) { mutableStateOf(initialKind) }
    var kindMenuExpanded by remember { mutableStateOf(false) }
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var selectedFilename by remember { mutableStateOf<String?>(null) }
    var captureUri by remember { mutableStateOf<Uri?>(null) }
    var documentTypeId by remember { mutableStateOf<Int?>(null) }
    var documentTypeMenuExpanded by remember { mutableStateOf(false) }

    fun setSelected(uri: Uri) {
        selectedUri = uri
        selectedFilename = displayName(context, uri)
        viewModel.clearMessage()
    }

    val filePicker =
        rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let(::setSelected)
        }
    val cameraLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { captured ->
            val uri = captureUri
            if (captured && uri != null) setSelected(uri)
            else if (!captured) captureUri = null
        }
    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                val uri = createCaptureUri(context)
                captureUri = uri
                cameraLauncher.launch(uri)
            }
        }

    fun takePhoto() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            val uri = createCaptureUri(context)
            captureUri = uri
            cameraLauncher.launch(uri)
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    LaunchedEffect(state.message) {
        if (state.message != null) selectedUri = null
    }

    val availableKinds =
        buildList {
            add(MediaUploadKind.ImageAttachment)
            if (endpointPath == "api/dcim/device-types/") {
                add(MediaUploadKind.DeviceTypeFront)
                add(MediaUploadKind.DeviceTypeRear)
            }
            if (documentEndpointPath != null && documentTypeOptions.isNotEmpty()) {
                add(MediaUploadKind.Document)
            }
        }
    if (kind !in availableKinds) kind = availableKinds.first()
    val canTakePhoto = kind != MediaUploadKind.Document
    val canUpload =
        selectedUri != null &&
            !state.isUploading &&
            (kind != MediaUploadKind.Document || documentTypeId != null)

    AlertDialog(
        onDismissRequest = { if (!state.isUploading) onDismiss() },
        icon = { Icon(Icons.Default.UploadFile, contentDescription = null) },
        title = { Text("Upload media") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    onClick = { kindMenuExpanded = true },
                    enabled = !state.isUploading,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        if (kind == MediaUploadKind.Document) Icons.Default.Description
                        else Icons.Default.UploadFile,
                        contentDescription = null,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(kind.label)
                }
                DropdownMenu(
                    expanded = kindMenuExpanded,
                    onDismissRequest = { kindMenuExpanded = false },
                ) {
                    availableKinds.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.label) },
                            onClick = {
                                kind = option
                                kindMenuExpanded = false
                                selectedUri = null
                            },
                            leadingIcon = {
                                Icon(
                                    if (option == MediaUploadKind.Document) Icons.Default.Description
                                    else Icons.Default.UploadFile,
                                    contentDescription = null,
                                )
                            },
                        )
                    }
                }
                if (kind == MediaUploadKind.Document) {
                    OutlinedButton(
                        onClick = { documentTypeMenuExpanded = true },
                        enabled = !state.isUploading,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.Description, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            documentTypeOptions.firstOrNull { it.id == documentTypeId }?.label
                                ?: "Choose document type"
                        )
                    }
                    DropdownMenu(
                        expanded = documentTypeMenuExpanded,
                        onDismissRequest = { documentTypeMenuExpanded = false },
                    ) {
                        documentTypeOptions.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.label) },
                                onClick = {
                                    documentTypeId = option.id
                                    documentTypeMenuExpanded = false
                                },
                            )
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            filePicker.launch(if (kind == MediaUploadKind.Document) "*/*" else "image/*")
                        },
                        enabled = !state.isUploading,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Default.UploadFile, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Choose file")
                    }
                    if (canTakePhoto) {
                        OutlinedButton(
                            onClick = ::takePhoto,
                            enabled = !state.isUploading,
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Default.CameraAlt, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("Take photo")
                        }
                    }
                }
                selectedFilename?.let { Text("Selected: $it") }
                state.error?.let { Text(it) }
                state.message?.let { Text(it) }
                if (state.isUploading) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.padding(4.dp).width(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Uploading…")
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !state.isUploading) { Text("Close") }
        },
        confirmButton = {
            Button(
                onClick = {
                    val uri = selectedUri ?: return@Button
                    viewModel.upload(
                        kind = kind,
                        endpointPath = endpointPath,
                        objectId = objectId,
                        uri = uri,
                        filename = selectedFilename ?: "upload",
                        documentEndpointPath = documentEndpointPath,
                        documentTypeId = documentTypeId,
                        onUploaded = onUploaded,
                    )
                },
                enabled = canUpload,
            ) {
                Icon(Icons.Default.UploadFile, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Upload")
            }
        },
    )
}

private fun createCaptureUri(context: Context): Uri {
    val directory = File(context.cacheDir, "uploads").apply { mkdirs() }
    val file = File(directory, "capture-${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}

private fun displayName(context: Context, uri: Uri): String =
    context.contentResolver
        .query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { cursor ->
            val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
        }
        ?.takeIf(String::isNotBlank)
        ?: uri.lastPathSegment?.substringAfterLast('/')?.takeIf(String::isNotBlank)
        ?: "upload"
