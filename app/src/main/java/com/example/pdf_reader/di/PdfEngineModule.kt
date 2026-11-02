package com.example.pdf_reader.di

import com.example.pdf_reader.data.pdf.PdfEngine
import com.example.pdf_reader.domain.repository.IPdfEngine
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class PdfEngineModule {

    @Binds
    @Singleton
    abstract fun bindPdfEngine(engine: PdfEngine): IPdfEngine
}
