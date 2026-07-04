package com.kazumaproject.markdownhelperkeyboard.ime_service.di

import com.kazumaproject.markdownhelperkeyboard.converter.api.DefaultKanaKanjiConverter
import com.kazumaproject.markdownhelperkeyboard.converter.api.KanaKanjiConverter
import com.kazumaproject.markdownhelperkeyboard.converter.zenz.AndroidZenzEngineAdapter
import com.kazumaproject.markdownhelperkeyboard.converter.zenz.ZenzEnginePort
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class CandidateModule {
    @Binds
    abstract fun bindKanaKanjiConverter(
        impl: DefaultKanaKanjiConverter,
    ): KanaKanjiConverter

    @Binds
    abstract fun bindZenzEnginePort(
        impl: AndroidZenzEngineAdapter,
    ): ZenzEnginePort
}