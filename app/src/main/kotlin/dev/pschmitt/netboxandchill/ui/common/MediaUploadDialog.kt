package dev.pschmitt.netboxandchill.ui.common

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Image
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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.pschmitt.netboxandchill.data.repository.MediaUploadRepository
import java.io.File

@Composable
fun MediaUploadDialog(
    endpointPath: String,
    objectId: Int,
    onDismiss: () -> Unit,
    onUploaded: () -> Unit,
    initialKind: MediaUploadKind? = null,
    imageAttachmentId: Int? = null,
    initialUri: Uri? = null,
    initialFilename: String? = null,
    initialMimeType: String? = null,
    viewModel: MediaUploadViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val documentEndpointPath by viewModel.documentEndpointPath.collectAsStateWithLifecycle()
    val documentTypeOptions by viewModel.documentTypeOptions.collectAsStateWithLifecycle()
    val defaultKind =
        if (endpointPath == "api/dcim/device-types/") MediaUploadKind.DeviceTypeFront
        else MediaUploadKind.ImageAttachment
    var kind by remember(endpointPath, initialKind) {
        mutableStateOf(initialKind ?: defaultKind)
    }
    var selectedUri by remember(initialUri) { mutableStateOf(initialUri) }
    var selectedFilename by remember(initialUri, initialFilename) {
        mutableStateOf(
            (initialFilename ?: initialUri?.let { displayName(context, it) })?.let {
                filenameForUpload(
                    it,
                    initialMimeType ?: initialUri?.let(context.contentResolver::getType),
                    kind,
                )
            }
        )
    }
    val selectedMimeType =
        remember(selectedUri, initialUri, initialMimeType) {
            selectedUri?.let { uri ->
                if (uri == initialUri) initialMimeType ?: context.contentResolver.getType(uri)
                else context.contentResolver.getType(uri)
            }
        }
    var captureUri by remember { mutableStateOf<Uri?>(null) }
    var documentTypeValue by remember { mutableStateOf<String?>(null) }
    var documentTypeMenuExpanded by remember { mutableStateOf(false) }
    var deviceTypeKindMenuExpanded by remember { mutableStateOf(false) }

    fun setSelected(uri: Uri) {
        selectedUri = uri
        selectedFilename =
            filenameForUpload(displayName(context, uri), context.contentResolver.getType(uri), kind)
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

    val isDocument = kind == MediaUploadKind.Document
    val isDeviceTypePhoto =
        kind == MediaUploadKind.DeviceTypeFront || kind == MediaUploadKind.DeviceTypeRear
    val isReplacingImage = kind == MediaUploadKind.ImageAttachment && imageAttachmentId != null
    val canTakePhoto = !isDocument
    val canUpload =
        selectedUri != null &&
            !state.isUploading &&
            (!isDocument || documentTypeValue != null)
    val dialogTitle =
        when {
            isReplacingImage -> "Replace image attachment"
            isDocument -> "Upload document"
            isDeviceTypePhoto -> "Upload device-type photo"
            else -> "Upload image attachment"
        }
    val dialogDescription =
        when {
            isReplacingImage -> "Choose a replacement image or take a new photo."
            isDocument -> "Choose a document file and its NetBox document type."
            isDeviceTypePhoto -> "Choose or capture the device type's front or rear photo."
            else -> "Choose an image file or take a photo to attach to this item."
        }

    AlertDialog(
        onDismissRequest = { if (!state.isUploading) onDismiss() },
        icon = {
            Icon(
                if (isDocument) Icons.Default.Description else Icons.Default.Image,
                contentDescription = null,
            )
        },
        title = { Text(dialogTitle) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(dialogDescription, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
                if (!isDocument && endpointPath == "api/dcim/device-types/") {
                    // Anchor the popup to the face button rather than to the dialog's scrolling
                    // column. This keeps it directly below the trigger on phones and tablets.
                    Box(Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { deviceTypeKindMenuExpanded = true },
                            enabled = !state.isUploading,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Default.Image, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(kind.label)
                        }
                        DropdownMenu(
                            expanded = deviceTypeKindMenuExpanded,
                            onDismissRequest = { deviceTypeKindMenuExpanded = false },
                        ) {
                            listOf(
                                    MediaUploadKind.ImageAttachment,
                                    MediaUploadKind.DeviceTypeFront,
                                    MediaUploadKind.DeviceTypeRear,
                                )
                                .forEach { option ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(option.label)
                                        },
                                        onClick = {
                                            kind = option
                                            deviceTypeKindMenuExpanded = false
                                        },
                                        leadingIcon = {
                                            Icon(Icons.Default.Image, contentDescription = null)
                                        },
                                    )
                                }
                        }
                    }
                }
                if (isDocument) {
                    OutlinedButton(
                        onClick = { documentTypeMenuExpanded = true },
                        enabled = !state.isUploading,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.Description, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            documentTypeOptions.firstOrNull { it.value == documentTypeValue }?.label
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
                                    documentTypeValue = option.value
                                    documentTypeMenuExpanded = false
                                },
                            )
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            filePicker.launch(if (isDocument) "*/*" else "image/*")
                        },
                        enabled = !state.isUploading,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Default.UploadFile, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text(if (isDocument) "Choose document" else "Choose image")
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
                selectedUri?.let { uri ->
                    SharedMediaPreview(
                        uri = uri,
                        mimeType = selectedMimeType,
                        filename = selectedFilename,
                    )
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
                        filename =
                            filenameForUpload(
                                selectedFilename ?: "upload",
                                selectedMimeType,
                                kind,
                            ),
                        documentEndpointPath = documentEndpointPath,
                        documentTypeValue = documentTypeValue,
                        imageAttachmentId = imageAttachmentId,
                        onUploaded = onUploaded,
                    )
                },
                enabled = canUpload,
            ) {
                Icon(Icons.Default.UploadFile, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(
                    when {
                        isDocument -> "Upload document"
                        isReplacingImage -> "Replace image"
                        else -> "Upload image"
                    }
                )
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

private fun filenameForUpload(
    filename: String,
    mimeType: String?,
    kind: MediaUploadKind,
): String {
    val withMime = MediaUploadRepository.filenameWithMimeExtension(filename, mimeType)
    if (withMime.substringAfterLast('.', "").isNotBlank()) return withMime
    return "$withMime.${if (kind == MediaUploadKind.Document) "bin" else "jpg"}"
}
