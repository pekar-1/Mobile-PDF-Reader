package com.example.pdf_reader.di

import com.example.pdf_reader.data.repository.FileRepository
import com.example.pdf_reader.domain.repository.IFileRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindFileRepository(impl: FileRepository): IFileRepository
}
