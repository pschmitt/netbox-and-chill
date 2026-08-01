package dev.pschmitt.netboxandchill.di

import android.content.Context
import androidx.room.migration.Migration
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.pschmitt.netboxandchill.data.db.AppDatabase
import dev.pschmitt.netboxandchill.data.db.BookmarkDao
import dev.pschmitt.netboxandchill.data.db.CustomFieldDao
import dev.pschmitt.netboxandchill.data.db.DashboardStatDao
import dev.pschmitt.netboxandchill.data.db.DeviceDao
import dev.pschmitt.netboxandchill.data.db.DeviceTypeDao
import dev.pschmitt.netboxandchill.data.db.ImageAttachmentDao
import dev.pschmitt.netboxandchill.data.db.NetBoxModelDao
import dev.pschmitt.netboxandchill.data.db.NetBoxObjectDao
import dev.pschmitt.netboxandchill.data.db.NewsDao
import dev.pschmitt.netboxandchill.data.db.ObjectChangeDao
import dev.pschmitt.netboxandchill.data.db.PendingEditDao
import dev.pschmitt.netboxandchill.data.db.RackElevationDao
import dev.pschmitt.netboxandchill.data.db.RecentVisitDao
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    private val MIGRATION_14_15 =
        object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `news_items` (
                        `guid` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `link` TEXT NOT NULL,
                        `summary` TEXT,
                        `publishedAt` INTEGER NOT NULL,
                        `syncedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`guid`)
                    )
                    """.trimIndent()
                )
            }
        }

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "netbox-and-chill.db")
            // Keep the cache across known schema changes. The destructive fallback remains only
            // for older/unrecognized pre-1.0 schemas that have no migration path yet.
            .addMigrations(MIGRATION_14_15)
            .fallbackToDestructiveMigration(dropAllTables = true)
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
