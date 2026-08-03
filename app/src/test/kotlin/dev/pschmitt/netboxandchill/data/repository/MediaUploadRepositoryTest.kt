package dev.pschmitt.netboxandchill.data.repository

import org.junit.Assert.assertEquals
import org.junit.Test

class MediaUploadRepositoryTest {
    @Test
    fun `derives core content type from endpoint`() {
        assertEquals(
            "dcim.device",
            MediaUploadRepository.contentTypeForEndpoint("api/dcim/devices/"),
        )
        assertEquals(
            "dcim.devicetype",
            MediaUploadRepository.contentTypeForEndpoint("api/dcim/device-types/"),
        )
    }

    @Test
    fun `derives plugin content type without losing plugin namespace`() {
        assertEquals(
            "netbox_documents.document",
            MediaUploadRepository.contentTypeForEndpoint(
                "api/plugins/netbox_documents/documents/"
            ),
        )
    }

    @Test
    fun `adds MIME extension only when the provider name has none`() {
        assertEquals(
            "shared-file.pdf",
            MediaUploadRepository.filenameWithMimeExtension("shared-file", "application/pdf"),
        )
        assertEquals(
            "photo.jpg",
            MediaUploadRepository.filenameWithMimeExtension("photo", "image/jpeg"),
        )
        assertEquals(
            "already.png",
            MediaUploadRepository.filenameWithMimeExtension("already.png", "image/jpeg"),
        )
        assertEquals(
            "unknown-type",
            MediaUploadRepository.filenameWithMimeExtension(
                "unknown-type",
                "application/octet-stream",
            ),
        )
    }
}
