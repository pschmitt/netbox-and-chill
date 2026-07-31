package dev.pschmitt.netboxandchill.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities =
        [
            DeviceEntity::class,
            NetBoxModelEntity::class,
            NetBoxObjectEntity::class,
            DeviceTypeEntity::class,
            ImageAttachmentEntity::class,
        ],
    version = 3,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun deviceDao(): DeviceDao

    abstract fun netBoxModelDao(): NetBoxModelDao

    abstract fun netBoxObjectDao(): NetBoxObjectDao

    abstract fun deviceTypeDao(): DeviceTypeDao

    abstract fun imageAttachmentDao(): ImageAttachmentDao
}
