package com.scascan.app.di

import android.content.Context
import androidx.room.Room
import com.google.gson.Gson
import com.scascan.app.data.local.AppDatabase
import com.scascan.app.data.local.LogEntryDao
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
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    @Singleton
    fun provideLogEntryDao(db: AppDatabase): LogEntryDao = db.logEntryDao()

    @Provides
    @Singleton
    fun provideWaterLogDao(db: AppDatabase): com.scascan.app.data.local.WaterLogDao = db.waterLogDao()
}
