package com.example.pdf_reader.pdfium

// Created from C++ with (String, Int), don't change the constructor.
class PdfiumException(message: String, val errorCode: Int) :
    Exception("$message (code $errorCode)") {

    companion object {
        const val ERR_SUCCESS = 0
        const val ERR_UNKNOWN = 1
        const val ERR_FILE = 2
        const val ERR_FORMAT = 3
        const val ERR_PASSWORD = 4
        const val ERR_SECURITY = 5
        const val ERR_PAGE = 6
    }
}
