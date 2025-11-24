package com.example.smartdosing.database.dao

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import com.example.smartdosing.database.entities.ImportLogEntity

/**
 * 导入日志数据访问对象 (DAO)
 * 提供导入日志的记录和查询功能
 */
@Dao
interface ImportLogDao {

    // =================================
    // 基础查询操作
    // =================================

    @Query("SELECT * FROM import_logs ORDER BY import_time DESC")
    suspend fun getAllImportLogs(): List<ImportLogEntity>

    @Query("SELECT * FROM import_logs ORDER BY import_time DESC")
    fun getAllImportLogsFlow(): Flow<List<ImportLogEntity>>

    @Query("SELECT * FROM import_logs WHERE id = :id")
    suspend fun getImportLogById(id: String): ImportLogEntity?

    @Query("""
        SELECT * FROM import_logs
        ORDER BY import_time DESC
        LIMIT :limit OFFSET :offset
    """)
    suspend fun getImportLogsPaged(limit: Int, offset: Int): List<ImportLogEntity>

    // =================================
    // 按类型和时间查询
    // =================================

    @Query("""
        SELECT * FROM import_logs
        WHERE file_type = :fileType
        ORDER BY import_time DESC
        LIMIT :limit
    """)
    suspend fun getImportLogsByType(fileType: String, limit: Int = 50): List<ImportLogEntity>

    @Query("""
        SELECT * FROM import_logs
        WHERE import_time >= :sinceTime
        ORDER BY import_time DESC
        LIMIT :limit
    """)
    suspend fun getRecentImportLogs(sinceTime: String, limit: Int = 50): List<ImportLogEntity>

    @Query("""
        SELECT * FROM import_logs
        WHERE import_time BETWEEN :startTime AND :endTime
        ORDER BY import_time DESC
    """)
    suspend fun getImportLogsByTimeRange(startTime: String, endTime: String): List<ImportLogEntity>

    // =================================
    // 增删改操作
    // =================================

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertImportLog(log: ImportLogEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertImportLogs(logs: List<ImportLogEntity>)

    @Update
    suspend fun updateImportLog(log: ImportLogEntity)

    @Delete
    suspend fun deleteImportLog(log: ImportLogEntity)

    @Query("DELETE FROM import_logs WHERE id = :id")
    suspend fun deleteImportLogById(id: String)

    @Query("DELETE FROM import_logs WHERE import_time < :beforeTime")
    suspend fun deleteImportLogsBefore(beforeTime: String)

    // =================================
    // 统计查询
    // =================================

    @Query("SELECT COUNT(*) FROM import_logs")
    suspend fun getImportLogCount(): Int

    @Query("SELECT COUNT(*) FROM import_logs WHERE file_type = :fileType")
    suspend fun getImportLogCountByType(fileType: String): Int

    @Query("SELECT SUM(success_count) FROM import_logs")
    suspend fun getTotalSuccessCount(): Int?

    @Query("SELECT SUM(failed_count) FROM import_logs")
    suspend fun getTotalFailedCount(): Int?

    @Query("SELECT SUM(file_size) FROM import_logs")
    suspend fun getTotalFileSize(): Long?

    @Query("SELECT AVG(import_duration) FROM import_logs WHERE import_duration > 0")
    suspend fun getAverageImportDuration(): Double?

    // =================================
    // 成功率统计
    // =================================

    @Query("""
        SELECT
            SUM(success_count) as total_success,
            SUM(failed_count) as total_failed,
            COUNT(*) as total_imports
        FROM import_logs
    """)
    suspend fun getImportSummaryStats(): ImportSummaryStats?

    @Query("""
        SELECT
            file_type,
            COUNT(*) as import_count,
            SUM(success_count) as total_success,
            SUM(failed_count) as total_failed,
            AVG(import_duration) as avg_duration
        FROM import_logs
        GROUP BY file_type
        ORDER BY import_count DESC
    """)
    suspend fun getImportStatsByType(): List<ImportTypeStats>

    @Query("""
        SELECT
            DATE(import_time) as import_date,
            COUNT(*) as import_count,
            SUM(success_count) as total_success,
            SUM(failed_count) as total_failed
        FROM import_logs
        WHERE import_time >= :sinceDate
        GROUP BY DATE(import_time)
        ORDER BY import_date DESC
        LIMIT :days
    """)
    suspend fun getDailyImportStats(sinceDate: String, days: Int = 30): List<DailyImportStats>

    // =================================
    // 错误分析
    // =================================

    @Query("""
        SELECT * FROM import_logs
        WHERE failed_count > 0
        ORDER BY import_time DESC
        LIMIT :limit
    """)
    suspend fun getFailedImports(limit: Int = 20): List<ImportLogEntity>

    @Query("""
        SELECT error_details
        FROM import_logs
        WHERE error_details IS NOT NULL AND error_details != ''
        ORDER BY import_time DESC
        LIMIT :limit
    """)
    suspend fun getRecentErrorDetails(limit: Int = 10): List<String>

    @Query("""
        SELECT COUNT(*) FROM import_logs
        WHERE failed_count > 0
    """)
    suspend fun getFailedImportCount(): Int

    @Query("""
        SELECT COUNT(*) FROM import_logs
        WHERE success_count > 0 AND failed_count = 0
    """)
    suspend fun getSuccessfulImportCount(): Int

    // =================================
    // 性能分析
    // =================================

    @Query("""
        SELECT * FROM import_logs
        WHERE import_duration > :threshold
        ORDER BY import_duration DESC
        LIMIT :limit
    """)
    suspend fun getSlowImports(threshold: Long, limit: Int = 10): List<ImportLogEntity>

    @Query("""
        SELECT
            file_size,
            import_duration,
            (CAST(file_size AS REAL) / import_duration) * 1000 as throughput_bytes_per_second
        FROM import_logs
        WHERE import_duration > 0 AND file_size > 0
        ORDER BY throughput_bytes_per_second DESC
        LIMIT :limit
    """)
    suspend fun getFastestImports(limit: Int = 10): List<ImportPerformance>

    // =================================
    // 清理和维护
    // =================================

    @Query("""
        DELETE FROM import_logs
        WHERE id NOT IN (
            SELECT id FROM import_logs
            ORDER BY import_time DESC
            LIMIT :keepCount
        )
    """)
    suspend fun keepOnlyRecentLogs(keepCount: Int = 1000)

    @Query("""
        SELECT COUNT(*) FROM import_logs
        WHERE import_time < :cutoffTime
    """)
    suspend fun getOldLogCount(cutoffTime: String): Int

    @Query("DELETE FROM import_logs")
    suspend fun deleteAllImportLogs()
}

/**
 * 导入汇总统计数据类
 */
data class ImportSummaryStats(
    val total_success: Int,
    val total_failed: Int,
    val total_imports: Int
)

/**
 * 按文件类型统计数据类
 */
data class ImportTypeStats(
    val file_type: String,
    val import_count: Int,
    val total_success: Int,
    val total_failed: Int,
    val avg_duration: Double?
)

/**
 * 每日导入统计数据类
 */
data class DailyImportStats(
    val import_date: String,
    val import_count: Int,
    val total_success: Int,
    val total_failed: Int
)

/**
 * 导入性能数据类
 */
data class ImportPerformance(
    val file_size: Long,
    val import_duration: Long,
    val throughput_bytes_per_second: Double
)