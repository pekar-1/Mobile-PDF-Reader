package com.example.pdf_reader.ui.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExternalLinksTest {
    @Test
    fun keepsAllowedSchemes() {
        assertEquals("https://a.b/c", ExternalLinks.normalize("https://a.b/c"))
        assertEquals("HTTP://a.b", ExternalLinks.normalize("HTTP://a.b"))
        assertEquals("mailto:x@y.z", ExternalLinks.normalize("mailto:x@y.z"))
        assertEquals("tel:+48123", ExternalLinks.normalize(" tel:+48123 "))
    }

    @Test
    fun addsHttpsWithoutScheme() {
        assertEquals("https://www.example.com", ExternalLinks.normalize("www.example.com"))
        assertEquals("https://example.com:8080/x", ExternalLinks.normalize("example.com:8080/x"))
        assertEquals("https://localhost:3000", ExternalLinks.normalize("localhost:3000"))
    }

    @Test
    fun rejectsOtherSchemes() {
        assertNull(ExternalLinks.normalize("javascript:alert(1)"))
        assertNull(ExternalLinks.normalize("JavaScript:alert(1)"))
        assertNull(ExternalLinks.normalize("file:///sdcard/x.pdf"))
        assertNull(ExternalLinks.normalize("intent://scan#Intent;end"))
        assertNull(ExternalLinks.normalize("content://media/1"))
        assertNull(ExternalLinks.normalize("ftp://a.b"))
    }

    @Test
    fun rejectsBlank() {
        assertNull(ExternalLinks.normalize(""))
        assertNull(ExternalLinks.normalize("   "))
    }
}
