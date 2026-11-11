package com.focusforce.app.di

import android.content.Context
import androidx.room.Room
import com.focusforce.app.data.db.AppDatabase
import com.focusforce.app.data.db.CategoryDao
import com.focusforce.app.data.db.UsageDao
import com.focusforce.app.data.usage.UsageRepositoryImpl
import com.focusforce.app.domain.repo.UsageRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME).build()

    @Provides fun usageDao(db: AppDatabase): UsageDao = db.usageDao()
    @Provides fun categoryDao(db: AppDatabase): CategoryDao = db.categoryDao()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindUsageRepository(impl: UsageRepositoryImpl): UsageRepository
}
