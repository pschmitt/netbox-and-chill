package dev.pschmitt.nyetbox.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.pschmitt.nyetbox.data.db.BookmarkDao
import dev.pschmitt.nyetbox.data.db.CacheDatabaseManager
import dev.pschmitt.nyetbox.data.db.CustomFieldDao
import dev.pschmitt.nyetbox.data.db.DashboardStatDao
import dev.pschmitt.nyetbox.data.db.DeviceDao
import dev.pschmitt.nyetbox.data.db.DeviceTypeDao
import dev.pschmitt.nyetbox.data.db.DynamicDaoProxy
import dev.pschmitt.nyetbox.data.db.ImageAttachmentDao
import dev.pschmitt.nyetbox.data.db.NetBoxModelDao
import dev.pschmitt.nyetbox.data.db.NetBoxObjectDao
import dev.pschmitt.nyetbox.data.db.NewsDao
import dev.pschmitt.nyetbox.data.db.ObjectChangeDao
import dev.pschmitt.nyetbox.data.db.PendingEditDao
import dev.pschmitt.nyetbox.data.db.RackElevationDao
import dev.pschmitt.nyetbox.data.db.RecentVisitDao
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDeviceDao(manager: CacheDatabaseManager): DeviceDao =
        DynamicDaoProxy.create(DeviceDao::class.java, manager) { it.deviceDao() }

    @Provides
    fun provideNetBoxModelDao(manager: CacheDatabaseManager): NetBoxModelDao =
        DynamicDaoProxy.create(NetBoxModelDao::class.java, manager) { it.netBoxModelDao() }

    @Provides
    fun provideNetBoxObjectDao(manager: CacheDatabaseManager): NetBoxObjectDao =
        DynamicDaoProxy.create(NetBoxObjectDao::class.java, manager) { it.netBoxObjectDao() }

    @Provides
    fun provideDeviceTypeDao(manager: CacheDatabaseManager): DeviceTypeDao =
        DynamicDaoProxy.create(DeviceTypeDao::class.java, manager) { it.deviceTypeDao() }

    @Provides
    fun provideImageAttachmentDao(manager: CacheDatabaseManager): ImageAttachmentDao =
        DynamicDaoProxy.create(ImageAttachmentDao::class.java, manager) { it.imageAttachmentDao() }

    @Provides
    fun provideBookmarkDao(manager: CacheDatabaseManager): BookmarkDao =
        DynamicDaoProxy.create(BookmarkDao::class.java, manager) { it.bookmarkDao() }

    @Provides
    fun provideObjectChangeDao(manager: CacheDatabaseManager): ObjectChangeDao =
        DynamicDaoProxy.create(ObjectChangeDao::class.java, manager) { it.objectChangeDao() }

    @Provides
    fun provideDashboardStatDao(manager: CacheDatabaseManager): DashboardStatDao =
        DynamicDaoProxy.create(DashboardStatDao::class.java, manager) { it.dashboardStatDao() }

    @Provides
    fun provideCustomFieldDao(manager: CacheDatabaseManager): CustomFieldDao =
        DynamicDaoProxy.create(CustomFieldDao::class.java, manager) { it.customFieldDao() }

    @Provides
    fun providePendingEditDao(manager: CacheDatabaseManager): PendingEditDao =
        DynamicDaoProxy.create(PendingEditDao::class.java, manager) { it.pendingEditDao() }

    @Provides
    fun provideRecentVisitDao(manager: CacheDatabaseManager): RecentVisitDao =
        DynamicDaoProxy.create(RecentVisitDao::class.java, manager) { it.recentVisitDao() }

    @Provides
    fun provideRackElevationDao(manager: CacheDatabaseManager): RackElevationDao =
        DynamicDaoProxy.create(RackElevationDao::class.java, manager) { it.rackElevationDao() }

    @Provides
    fun provideNewsDao(manager: CacheDatabaseManager): NewsDao =
        DynamicDaoProxy.create(NewsDao::class.java, manager) { it.newsDao() }
}
