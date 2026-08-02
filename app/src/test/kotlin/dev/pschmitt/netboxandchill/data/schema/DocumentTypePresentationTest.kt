package dev.pschmitt.netboxandchill.data.schema

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DocumentTypePresentationTest {
    @Test
    fun knownPurchaseOrderVariantsUseTheReadableLabel() {
        val expected = DocumentTypePresentation("purchaseorder", "Purchase order")

        assertEquals(expected, documentTypePresentation("purchaseorder"))
        assertEquals(expected, documentTypePresentation("Purchaseorder"))
        assertEquals(expected, documentTypePresentation("purchase-order"))
        assertEquals(expected, documentTypePresentation("Purchase Order"))
    }

    @Test
    fun camelCaseAndUnknownTypesRemainReadable() {
        assertEquals(
            DocumentTypePresentation("warrantycard", "Warranty card"),
            documentTypePresentation("warrantyCard"),
        )
        assertEquals(DocumentTypePresentation("other", "Other"), documentTypePresentation("other"))
    }

    @Test
    fun blankTypesAreIgnored() {
        assertNull(documentTypePresentation(null))
        assertNull(documentTypePresentation("  "))
    }
}
