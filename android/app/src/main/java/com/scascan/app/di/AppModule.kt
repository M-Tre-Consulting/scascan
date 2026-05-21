package com.scascan.app.di

import com.google.ai.client.generativeai.GenerativeModel
import com.google.gson.Gson
import com.scascan.app.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideGenerativeModel(): GenerativeModel = GenerativeModel(
        modelName = "gemini-1.5-flash",
        apiKey = BuildConfig.GEMINI_API_KEY
    )

    @Provides
    @Singleton
    fun provideGson(): Gson = Gson()
}
