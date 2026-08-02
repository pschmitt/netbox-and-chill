package dev.pschmitt.netboxandchill.data.repository

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.pschmitt.netboxandchill.data.api.MediaNetBoxApi
import dev.pschmitt.netboxandchill.data.schema.NetBoxRef
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody

data class MediaUploadReceipt(val id: Int?)

/** Explicit, online-only multipart uploads. All reads after a successful upload refresh Room. */
@Singleton
class MediaUploadRepository
@Inject
constructor(
    private val api: MediaNetBoxApi,
    private val imageAttachmentRepository: ImageAttachmentRepository,
    private val genericObjectRepository: GenericObjectRepository,
    private val settingsRepository: SettingsRepository,
    @ApplicationContext private val context: Context,
) {

    suspend fun uploadImageAttachment(
        endpointPath: String,
        objectId: Int,
        uri: Uri,
        filename: String,
    ): Result<MediaUploadReceipt> =
        upload(
            operation = {
                val objectType = contentTypeForEndpoint(endpointPath)
                api.uploadFile(
                    "api/extras/image-attachments/",
                    filePart("image", uri, filename),
                    fields(
                        "object_type" to objectType,
                        "object_id" to objectId.toString(),
                        "name" to filename,
                    ),
                )
            },
            afterUpload = {
                imageAttachmentRepository.refresh(contentTypeForEndpoint(endpointPath), objectId)
            },
        )

    suspend fun uploadDeviceTypePhoto(
        deviceTypeId: Int,
        face: DeviceTypePhotoFace,
        uri: Uri,
        filename: String,
    ): Result<MediaUploadReceipt> =
        upload(
            operation = {
                api.patchFile(
                    "${NetBoxRef.DEVICE_TYPES_ENDPOINT_PATH}$deviceTypeId/",
                    filePart(face.fieldName, uri, filename),
                )
            },
            afterUpload = {},
        )

    suspend fun uploadDocument(
        documentEndpointPath: String,
        objectEndpointPath: String,
        objectId: Int,
        uri: Uri,
        filename: String,
        documentTypeValue: String,
    ): Result<MediaUploadReceipt> =
        upload(
            operation = {
                api.uploadFile(
                    documentEndpointPath,
                    filePart("file", uri, filename),
                    fields(
                        "name" to filename,
                        "document_type" to documentTypeValue,
                        "object_type" to contentTypeForEndpoint(objectEndpointPath),
                        "object_id" to objectId.toString(),
                    ),
                )
            },
            afterUpload = { genericObjectRepository.syncAll(documentEndpointPath) },
        )

    private suspend fun upload(
        operation: suspend () -> kotlinx.serialization.json.JsonObject,
        afterUpload: suspend () -> Unit,
    ): Result<MediaUploadReceipt> =
        withContext(Dispatchers.IO) {
            if (settingsRepository.offlineMode.value) {
                return@withContext Result.failure(IllegalStateException("Uploads are unavailable in offline mode"))
            }
            runCatching {
                val response = operation()
                afterUpload()
                MediaUploadReceipt(response["id"]?.toString()?.toIntOrNull())
            }
        }

    private fun fields(vararg values: Pair<String, String>): Map<String, RequestBody> =
        values.associate { (key, value) -> key to value.toRequestBody("text/plain".toMediaTypeOrNull()) }

    private fun filePart(field: String, uri: Uri, filename: String): MultipartBody.Part {
        val resolver = context.contentResolver
        val mediaType = resolver.getType(uri)?.toMediaTypeOrNull()
        val body =
            object : RequestBody() {
                override fun contentType() = mediaType

                override fun contentLength(): Long =
                    resolver.openAssetFileDescriptor(uri, "r")?.use { it.length }
                        ?: super.contentLength()

                override fun writeTo(sink: okio.BufferedSink) {
                    resolver.openInputStream(uri).use { input ->
                        requireNotNull(input) { "Couldn't open selected file" }
                        input.copyTo(sink.outputStream())
                    }
                }
            }
        return MultipartBody.Part.createFormData(field, filename.safeFilename(), body)
    }

    private fun String.safeFilename(): String =
        substringAfterLast('/').substringAfterLast('\\').ifBlank { "upload" }

    companion object {
        fun contentTypeForEndpoint(endpointPath: String): String {
            val segments = endpointPath.trim('/').split('/')
            require(segments.size >= 3) { "Unsupported NetBox endpoint: $endpointPath" }
            val app = if (segments[1] == "plugins" && segments.size >= 4) segments[2] else segments[1]
            val model = segments.last().removeSuffix("s").replace("-", "").replace("_", "")
            return "$app.$model"
        }
    }
}

enum class DeviceTypePhotoFace(val fieldName: String, val label: String) {
    Front("front_image", "Front photo"),
    Rear("rear_image", "Rear photo"),
}
