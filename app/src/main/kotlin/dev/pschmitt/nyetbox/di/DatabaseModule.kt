package dev.pschmitt.nyetbox.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.pschmitt.nyetbox.data.db.AppDatabase
import dev.pschmitt.nyetbox.data.db.BookmarkDao
import dev.pschmitt.nyetbox.data.db.CustomFieldDao
import dev.pschmitt.nyetbox.data.db.DashboardStatDao
import dev.pschmitt.nyetbox.data.db.DeviceDao
import dev.pschmitt.nyetbox.data.db.DeviceTypeDao
import dev.pschmitt.nyetbox.data.db.ImageAttachmentDao
import dev.pschmitt.nyetbox.data.db.NetBoxModelDao
import dev.pschmitt.nyetbox.data.db.NetBoxObjectDao
import dev.pschmitt.nyetbox.data.db.NewsDao
import dev.pschmitt.nyetbox.data.db.ObjectChangeDao
import dev.pschmitt.nyetbox.data.db.PendingEditDao
import dev.pschmitt.nyetbox.data.db.RackElevationDao
import dev.pschmitt.nyetbox.data.db.RecentVisitDao
import dev.pschmitt.nyetbox.data.db.MIGRATION_1_2
import dev.pschmitt.nyetbox.data.db.MIGRATION_10_11
import dev.pschmitt.nyetbox.data.db.MIGRATION_11_12
import dev.pschmitt.nyetbox.data.db.MIGRATION_12_13
import dev.pschmitt.nyetbox.data.db.MIGRATION_13_14
import dev.pschmitt.nyetbox.data.db.MIGRATION_14_15
import dev.pschmitt.nyetbox.data.db.MIGRATION_2_3
import dev.pschmitt.nyetbox.data.db.MIGRATION_3_4
import dev.pschmitt.nyetbox.data.db.MIGRATION_4_5
import dev.pschmitt.nyetbox.data.db.MIGRATION_5_6
import dev.pschmitt.nyetbox.data.db.MIGRATION_6_7
import dev.pschmitt.nyetbox.data.db.MIGRATION_7_8
import dev.pschmitt.nyetbox.data.db.MIGRATION_8_9
import dev.pschmitt.nyetbox.data.db.MIGRATION_9_10
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "nyetbox.db")
            .addMigrations(
                MIGRATION_1_2,
                MIGRATION_2_3,
                MIGRATION_3_4,
                MIGRATION_4_5,
                MIGRATION_5_6,
                MIGRATION_6_7,
                MIGRATION_7_8,
                MIGRATION_8_9,
                MIGRATION_9_10,
                MIGRATION_10_11,
                MIGRATION_11_12,
                MIGRATION_12_13,
                MIGRATION_13_14,
                MIGRATION_14_15,
            )
            .build()

    @Provides fun provideDeviceDao(database: AppDatabase): DeviceDao = database.deviceDao()

    @Provides
    fun provideNetBoxModelDao(database: AppDatabase): NetBoxModelDao = database.netBoxModelDao()

    @Provides
    fun provideNetBoxObjectDao(database: AppDatabase): NetBoxObjectDao = database.netBoxObjectDao()

    @Provides
    fun provideDeviceTypeDao(database: AppDatabase): DeviceTypeDao = database.deviceTypeDao()

    @Provides
    fun provideImageAttachmentDao(database: AppDatabase): ImageAttachmentDao =
        database.imageAttachmentDao()

    @Provides fun provideBookmarkDao(database: AppDatabase): BookmarkDao = database.bookmarkDao()

    @Provides
    fun provideObjectChangeDao(database: AppDatabase): ObjectChangeDao = database.objectChangeDao()

    @Provides
    fun provideDashboardStatDao(database: AppDatabase): DashboardStatDao =
        database.dashboardStatDao()

    @Provides
    fun provideCustomFieldDao(database: AppDatabase): CustomFieldDao = database.customFieldDao()

    @Provides
    fun providePendingEditDao(database: AppDatabase): PendingEditDao = database.pendingEditDao()

    @Provides
    fun provideRecentVisitDao(database: AppDatabase): RecentVisitDao = database.recentVisitDao()

    @Provides
    fun provideRackElevationDao(database: AppDatabase): RackElevationDao =
        database.rackElevationDao()

    @Provides fun provideNewsDao(database: AppDatabase): NewsDao = database.newsDao()
}
