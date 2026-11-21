package com.example.pdf_reader

import java.io.ByteArrayOutputStream

/** Generates small text PDFs for instrumented tests (no binary assets needed). */
object TestPdfs {
    val PAGES = listOf(
        "Hello PDFium page one",
        "Second page with search target",
        "Third page with search target again",
    )

    /**
     * Minimal multi-page Helvetica PDF, 612x792 pt pages, correct xref table.
     * Text starts at (72, 700). [rotate] sets /Rotate on every page (0, 90, 180, 270).
     * [annots] maps a page index to raw annotation dictionaries; each one becomes an
     * indirect object listed in that page's /Annots. Page i is object `4 + 2 * i`.
     */
    fun build(
        pages: List<String> = PAGES,
        rotate: Int = 0,
        annots: Map<Int, List<String>> = emptyMap(),
    ): ByteArray {
        val objects = mutableListOf<String>()
        val kids = pages.indices.joinToString(" ") { "${4 + it * 2} 0 R" }
        objects += "<< /Type /Catalog /Pages 2 0 R >>"
        objects += "<< /Type /Pages /Kids [$kids] /Count ${pages.size} >>"
        objects += "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>"
        var nextAnnot = 4 + pages.size * 2
        val annotObjects = mutableListOf<String>()
        pages.forEachIndexed { i, text ->
            val pageAnnots = annots[i].orEmpty()
            val annotRefs = pageAnnots.joinToString(" ") { "${nextAnnot++} 0 R" }
            annotObjects += pageAnnots
            val annotEntry = if (pageAnnots.isEmpty()) "" else "/Annots [$annotRefs] "
            objects += "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Rotate $rotate " +
                "$annotEntry/Resources << /Font << /F1 3 0 R >> >> /Contents ${5 + i * 2} 0 R >>"
            val stream = "BT /F1 24 Tf 72 700 Td ($text) Tj ET"
            objects += "<< /Length ${stream.length} >>\nstream\n$stream\nendstream"
        }
        objects += annotObjects

        val out = ByteArrayOutputStream()
        fun write(s: String) = out.write(s.toByteArray(Charsets.US_ASCII))
        write("%PDF-1.4\n")
        val offsets = objects.mapIndexed { i, body ->
            out.size().also { write("${i + 1} 0 obj\n$body\nendobj\n") }
        }
        val xref = out.size()
        write("xref\n0 ${objects.size + 1}\n0000000000 65535 f \n")
        offsets.forEach { write("%010d 00000 n \n".format(it)) }
        write("trailer\n<< /Size ${objects.size + 1} /Root 1 0 R >>\nstartxref\n$xref\n%%EOF\n")
        return out.toByteArray()
    }
}
