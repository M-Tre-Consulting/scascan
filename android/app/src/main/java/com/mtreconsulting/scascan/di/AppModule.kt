package com.mtreconsulting.scascan.di

import android.content.Context
import androidx.room.Room
import com.google.gson.Gson
import com.mtreconsulting.scascan.data.local.AppDatabase
import com.mtreconsulting.scascan.data.local.LogEntryDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideGson(): Gson = Gson()

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "scascan.db")
            .fallbackToDestructiveMigration(false)
            .build()

    @Provides
    @Singleton
    fun provideLogEntryDao(db: AppDatabase): LogEntryDao = db.logEntryDao()

    @Provides
    @Singleton
    fun provideWaterLogDao(db: AppDatabase): com.mtreconsulting.scascan.data.local.WaterLogDao = db.waterLogDao()
}
