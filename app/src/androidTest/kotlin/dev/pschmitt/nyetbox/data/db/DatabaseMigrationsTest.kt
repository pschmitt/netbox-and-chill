package dev.pschmitt.nyetbox.data.db

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseMigrationsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun deviceAndPendingEditDataSurviveHistoricalMigrations() {
        val database = openDatabase()
        database.execSQL(
            """
            CREATE TABLE devices (
                id INTEGER NOT NULL PRIMARY KEY,
                name TEXT NOT NULL
            )
            """
                .trimIndent()
        )
        database.execSQL("INSERT INTO devices(id, name) VALUES (1, 'cached-device')")
        database.execSQL(
            """
            CREATE TABLE custom_fields (
                name TEXT NOT NULL PRIMARY KEY,
                type TEXT NOT NULL,
                syncedAt INTEGER NOT NULL
            )
            """
                .trimIndent()
        )
        database.execSQL(
            "INSERT INTO custom_fields(name, type, syncedAt) VALUES ('purchase_info', 'text', 1)"
        )

        MIGRATION_6_7.migrate(database)
        database.execSQL(
            """
            INSERT INTO pending_edits(endpointPath, id, baseJson, localJson, patchJson, state, createdAt)
            VALUES ('api/dcim/devices/', 1, '{}', '{"name":"local"}', '{}', 'queued', 1)
            """
                .trimIndent()
        )
        MIGRATION_7_8.migrate(database)
        MIGRATION_8_9.migrate(database)
        MIGRATION_9_10.migrate(database)
        MIGRATION_10_11.migrate(database)
        MIGRATION_12_13.migrate(database)

        database.query("SELECT name, customFieldsJson, primaryIpId FROM devices").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("cached-device", cursor.getString(0))
            assertTrue(cursor.isNull(1))
            assertTrue(cursor.isNull(2))
        }
        database
            .query("SELECT label, groupName, weight, objectTypes, choiceSetUrl FROM custom_fields")
            .use {
                assertTrue(it.moveToFirst())
                assertTrue(it.isNull(0))
                assertTrue(it.isNull(1))
                assertEquals(0, it.getInt(2))
                assertTrue(it.isNull(3))
                assertTrue(it.isNull(4))
            }
        database.query("SELECT localJson FROM pending_edits").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("{\"name\":\"local\"}", cursor.getString(0))
        }
        database.close()
    }

    @Test
    fun newsMigrationCreatesCacheWithoutAffectingExistingTables() {
        val database = openDatabase()
        database.execSQL(
            "CREATE TABLE devices (id INTEGER NOT NULL PRIMARY KEY, name TEXT NOT NULL)"
        )
        database.execSQL("INSERT INTO devices(id, name) VALUES (7, 'still-here')")

        MIGRATION_14_15.migrate(database)

        database.query("SELECT name FROM devices").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("still-here", cursor.getString(0))
        }
        database.query("SELECT COUNT(*) FROM news_items").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }
        database.close()
    }

    private fun openDatabase(): SupportSQLiteDatabase {
        val name = "migration-${UUID.randomUUID()}.db"
        val configuration =
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(name)
                .callback(
                    object : SupportSQLiteOpenHelper.Callback(15) {
                        override fun onCreate(db: SupportSQLiteDatabase) = Unit

                        override fun onUpgrade(
                            db: SupportSQLiteDatabase,
                            oldVersion: Int,
                            newVersion: Int,
                        ) = Unit
                    }
                )
                .build()
        return FrameworkSQLiteOpenHelperFactory().create(configuration).writableDatabase
    }
}
