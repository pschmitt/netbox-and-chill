package dev.pschmitt.netboxandchill.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.pschmitt.netboxandchill.data.db.AppDatabase
import dev.pschmitt.netboxandchill.data.db.DeviceDao
import dev.pschmitt.netboxandchill.data.db.NetBoxModelDao
import dev.pschmitt.netboxandchill.data.db.NetBoxObjectDao
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "netbox-and-chill.db")
            // Pre-1.0, only ever shipped via the rolling "latest" Obtainium channel - a clean
            // re-sync on schema changes is fine, not worth hand-written Migrations yet.
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides fun provideDeviceDao(database: AppDatabase): DeviceDao = database.deviceDao()

    @Provides fun provideNetBoxModelDao(database: AppDatabase): NetBoxModelDao = database.netBoxModelDao()

    @Provides
    fun provideNetBoxObjectDao(database: AppDatabase): NetBoxObjectDao = database.netBoxObjectDao()
}
