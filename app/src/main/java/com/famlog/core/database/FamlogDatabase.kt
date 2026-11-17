package com.famlog.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 앱 로컬 캐시. 서버가 원본이라 스키마가 바뀌면 지우고 다시 받는다 (마이그레이션 없음).
 * 로그아웃하거나 다른 계정으로 로그인하면 비운다.
 */
@Database(entities = [MessageEntity::class], version = 1, exportSchema = false)
abstract class FamlogDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): FamlogDatabase =
        Room.databaseBuilder(context, FamlogDatabase::class.java, "famlog.db")
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides
    fun provideMessageDao(database: FamlogDatabase): MessageDao = database.messageDao()
}
