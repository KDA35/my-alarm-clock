package com.myalarm.clock.di

import android.content.Context
import androidx.room.Room
import com.myalarm.clock.data.AlarmDao
import com.myalarm.clock.data.AlarmDatabase
import com.myalarm.clock.data.AlarmGroupDao
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
    fun provideDatabase(@ApplicationContext context: Context): AlarmDatabase =
        Room.databaseBuilder(context, AlarmDatabase::class.java, "alarms.db")
            .addMigrations(AlarmDatabase.MIGRATION_1_2)
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideAlarmDao(db: AlarmDatabase): AlarmDao = db.alarmDao()

    @Provides
    fun provideAlarmGroupDao(db: AlarmDatabase): AlarmGroupDao = db.alarmGroupDao()
}
