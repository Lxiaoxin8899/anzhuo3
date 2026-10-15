package com.example.smartdosing.database

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 校验 v5 → v6 → v7 迁移：结构与导出的 schema 一致，且旧数据不丢失
 */
@RunWith(AndroidJUnit4::class)
class SmartDosingDatabaseMigrationTest {

    private val dbName = "migration-test.db"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        SmartDosingDatabase::class.java
    )

    @Test
    fun `v5 升级到 v7 保留发送端和已接收任务`() {
        helper.createDatabase(dbName, 5).use { db ->
            db.execSQL(
                "INSERT INTO authorized_senders (uid, name, ip_address, authorized_at, task_count, is_active) " +
                    "VALUES ('S1', '后台', '192.168.1.2', 100, 3, 1)"
            )
            db.execSQL(
                "INSERT INTO received_tasks (id, transfer_id, sender_uid, sender_name, schema_version, title, " +
                    "recipe_code, recipe_name, quantity, unit, priority, received_at, status) " +
                    "VALUES ('RT1', 'T1', 'S1', '后台', '1.0', '任务', 'R1', '配方', 10.0, 'g', 'NORMAL', 200, 'ACCEPTED')"
            )
        }

        val db = helper.runMigrationsAndValidate(
            dbName,
            SmartDosingDatabase.SCHEMA_VERSION,
            true,
            SmartDosingDatabase.MIGRATION_5_6,
            SmartDosingDatabase.MIGRATION_6_7
        )

        db.query("SELECT task_count, sender_api_key, callback_base_url FROM authorized_senders WHERE uid = 'S1'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(3, c.getInt(0))
            assertTrue(c.isNull(1))
            assertTrue(c.isNull(2))
        }
        db.query("SELECT status, exec_status, progress, result_record_id FROM received_tasks WHERE id = 'RT1'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("ACCEPTED", c.getString(0))
            assertEquals("PENDING", c.getString(1))
            assertEquals(0, c.getInt(2))
            assertTrue(c.isNull(3))
        }
        db.query("SELECT COUNT(*) FROM pending_task_result_syncs").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(0, c.getInt(0))
        }
    }

    @Test
    fun `v6 单步升级到 v7`() {
        helper.createDatabase(dbName, 6).close()

        helper.runMigrationsAndValidate(dbName, 7, true, SmartDosingDatabase.MIGRATION_6_7).close()
    }

    @Test
    fun `迁移后的库能被 Room 正常打开并读写`() = runTest {
        helper.createDatabase(dbName, 5).close()

        val database = Room.databaseBuilder(
            ApplicationProvider.getApplicationContext(),
            SmartDosingDatabase::class.java,
            dbName
        )
            .addMigrations(SmartDosingDatabase.MIGRATION_5_6, SmartDosingDatabase.MIGRATION_6_7)
            .allowMainThreadQueries()
            .setQueryExecutor { it.run() }
            .setTransactionExecutor { it.run() }
            .build()
        try {
            assertEquals(0, database.deviceDao().getActiveSenderCount())
        } finally {
            database.close()
        }
    }
}
