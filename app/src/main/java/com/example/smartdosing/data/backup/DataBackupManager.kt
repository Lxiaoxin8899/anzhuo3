package com.example.smartdosing.data.backup

import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.util.Log
import com.example.smartdosing.BuildConfig
import com.example.smartdosing.database.SmartDosingDatabase
import com.example.smartdosing.web.CONFIGURATION_RECORDS_FILE_NAME
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * 本地数据备份与恢复
 *
 * 备份内容为 Room 数据库文件（配方、模板、任务、已配对发送端）和档案库使用的配置记录文件，打包为 ZIP。
 * 设备 UID、本机 API Key 等设备身份信息存放在 SharedPreferences 中，不随备份迁移。
 */
class DataBackupManager(context: Context) {

    private val appContext = context.applicationContext

    /**
     * 导出数据库到用户选择的位置
     */
    suspend fun exportTo(uri: Uri): BackupResult = withContext(Dispatchers.IO) {
        runCatching {
            val database = SmartDosingDatabase.getDatabase(appContext)
            // 把 WAL 中尚未落盘的事务写回主库文件，保证单文件备份完整
            database.openHelper.writableDatabase
                .query("PRAGMA wal_checkpoint(TRUNCATE)")
                .use { it.moveToFirst() }

            val dbFile = appContext.getDatabasePath(SmartDosingDatabase.DATABASE_NAME)
            val recordsFile = recordsFile()
            val output = appContext.contentResolver.openOutputStream(uri)
                ?: throw IOException("无法写入目标文件")
            ZipOutputStream(output.buffered()).use { zip ->
                zip.putNextEntry(ZipEntry(BACKUP_INFO_ENTRY))
                zip.write(buildBackupInfo().toByteArray(Charsets.UTF_8))
                zip.closeEntry()

                zip.putNextEntry(ZipEntry(DATABASE_ENTRY))
                dbFile.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()

                if (recordsFile.isFile) {
                    zip.putNextEntry(ZipEntry(RECORDS_ENTRY))
                    recordsFile.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
            val totalSize = dbFile.length() + if (recordsFile.isFile) recordsFile.length() else 0L
            BackupResult.Success("备份完成（${formatSize(totalSize)}）")
        }.getOrElse { error ->
            Log.e(TAG, "导出备份失败", error)
            BackupResult.Failure("备份失败：${error.message ?: "未知错误"}")
        }
    }

    /**
     * 从备份文件恢复数据库。成功后调用方需要调用 [restartApp]，
     * 因为各仓库单例仍持有旧数据库实例。
     */
    suspend fun restoreFrom(uri: Uri): BackupResult = withContext(Dispatchers.IO) {
        val stagingFile = File(appContext.cacheDir, "restore_staging.db")
        val recordsStagingFile = File(appContext.cacheDir, "restore_staging_records.json")
        try {
            val hasRecords = extractBackup(uri, stagingFile, recordsStagingFile)
            validateBackupDatabase(stagingFile)?.let { reason ->
                return@withContext BackupResult.Failure(reason)
            }

            SmartDosingDatabase.closeAndClearInstance()

            val dbFile = appContext.getDatabasePath(SmartDosingDatabase.DATABASE_NAME)
            File(dbFile.path + "-wal").delete()
            File(dbFile.path + "-shm").delete()
            stagingFile.copyTo(dbFile, overwrite = true)
            // 旧版备份不含配置记录文件，此时保留本机现有记录
            if (hasRecords) {
                recordsStagingFile.copyTo(recordsFile(), overwrite = true)
            }

            BackupResult.Success("恢复完成，应用即将重启")
        } catch (error: Exception) {
            Log.e(TAG, "恢复备份失败", error)
            BackupResult.Failure("恢复失败：${error.message ?: "未知错误"}")
        } finally {
            stagingFile.delete()
            recordsStagingFile.delete()
        }
    }

    /**
     * 清除应用缓存目录，返回释放的字节数
     */
    suspend fun clearCache(): Long = withContext(Dispatchers.IO) {
        val children = appContext.cacheDir.listFiles().orEmpty()
        val freed = children.sumOf { it.walkBottomUp().filter(File::isFile).sumOf(File::length) }
        children.forEach { it.deleteRecursively() }
        freed
    }

    /**
     * 重启应用进程，让所有单例重新绑定恢复后的数据库
     */
    fun restartApp() {
        val launchIntent = appContext.packageManager.getLaunchIntentForPackage(appContext.packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        if (launchIntent != null) {
            appContext.startActivity(launchIntent)
        }
        Runtime.getRuntime().exit(0)
    }

    private fun recordsFile(): File = File(appContext.filesDir, CONFIGURATION_RECORDS_FILE_NAME)

    /**
     * 解压备份到临时文件，返回备份里是否带有配置记录文件
     */
    private fun extractBackup(uri: Uri, databaseTarget: File, recordsTarget: File): Boolean {
        val input = appContext.contentResolver.openInputStream(uri)
            ?: throw IOException("无法读取备份文件")
        var hasDatabase = false
        var hasRecords = false
        ZipInputStream(input.buffered()).use { zip ->
            generateSequence { zip.nextEntry }.forEach { entry ->
                when (entry.name) {
                    DATABASE_ENTRY -> {
                        databaseTarget.outputStream().use { zip.copyTo(it) }
                        hasDatabase = true
                    }
                    RECORDS_ENTRY -> {
                        recordsTarget.outputStream().use { zip.copyTo(it) }
                        hasRecords = true
                    }
                }
            }
        }
        if (!hasDatabase) {
            throw IOException("备份文件中缺少数据库内容，请确认选择的是本应用导出的备份")
        }
        return hasRecords
    }

    /**
     * 校验备份数据库，返回 null 表示可恢复，否则返回拒绝原因
     */
    private fun validateBackupDatabase(file: File): String? {
        val header = ByteArray(SQLITE_HEADER.size)
        val read = file.inputStream().use { it.read(header) }
        if (read != header.size || !header.contentEquals(SQLITE_HEADER)) {
            return "备份文件已损坏或不是有效的数据库"
        }

        val version = SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { it.version }
        return when {
            version > SmartDosingDatabase.SCHEMA_VERSION ->
                "备份来自更新版本的应用（数据库 v$version），请先升级应用"
            // v5 之前没有迁移脚本，打开时会被破坏性重建，等于清空数据
            version < MIN_RESTORABLE_VERSION ->
                "备份版本过旧（数据库 v$version），无法安全恢复"
            else -> null
        }
    }

    private fun buildBackupInfo(): String = """
        {"app":"${appContext.packageName}","versionName":"${BuildConfig.VERSION_NAME}","schemaVersion":${SmartDosingDatabase.SCHEMA_VERSION},"createdAt":${System.currentTimeMillis()}}
    """.trimIndent()

    private fun formatSize(bytes: Long): String = when {
        bytes >= 1024 * 1024 -> String.format(java.util.Locale.US, "%.1f MB", bytes / 1024.0 / 1024.0)
        bytes >= 1024 -> String.format(java.util.Locale.US, "%.1f KB", bytes / 1024.0)
        else -> "$bytes B"
    }

    companion object {
        private const val TAG = "DataBackupManager"
        private const val DATABASE_ENTRY = "smartdosing.db"
        private const val BACKUP_INFO_ENTRY = "backup-info.json"
        private const val RECORDS_ENTRY = CONFIGURATION_RECORDS_FILE_NAME
        private const val MIN_RESTORABLE_VERSION = 5
        private val SQLITE_HEADER = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)

        /** 备份文件的推荐文件名 */
        fun suggestedFileName(): String {
            val stamp = java.text.SimpleDateFormat("yyyyMMdd_HHmm", java.util.Locale.US).format(java.util.Date())
            return "SmartDosing_backup_$stamp.zip"
        }
    }
}

sealed class BackupResult {
    data class Success(val message: String) : BackupResult()
    data class Failure(val message: String) : BackupResult()
}
