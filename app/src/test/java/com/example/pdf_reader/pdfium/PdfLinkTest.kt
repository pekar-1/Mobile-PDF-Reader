package com.example.pdf_reader.pdfium

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PdfLinkTest {
    @Test
    fun parsesPageAndUri() {
        assertEquals(PdfLink.Page(4), PdfLink.parse("P:4"))
        assertEquals(PdfLink.Uri("https://a.b/c?d=1:2"), PdfLink.parse("U:https://a.b/c?d=1:2"))
    }

    @Test
    fun rejectsGarbage() {
        assertNull(PdfLink.parse(null))
        assertNull(PdfLink.parse("P:x"))
        assertNull(PdfLink.parse("U:  "))
        assertNull(PdfLink.parse("X:1"))
    }
}
