package com.example.smartdosing.data

import android.content.Context
import androidx.room.withTransaction
import kotlin.text.Charsets

import com.example.smartdosing.database.SmartDosingDatabase
import com.example.smartdosing.database.DataMapper.toLogEntity

/**
 * 基于数据库的配方导入管理器
 * 支持事务操作、导入日志记录和性能优化；文件解析委托给 [RecipeImportParser]
 */
class DatabaseRecipeImportManager(
    private val context: Context,
    private val databaseRepository: DatabaseRecipeRepository,
    private val templateRepository: TemplateRepository = TemplateRepository.getInstance()
) {

    private val database = SmartDosingDatabase.getDatabase(context)
    private val importLogDao = database.importLogDao()

    companion object {
        private const val TEMPLATE_ID = "standard_recipe_template"

        @Volatile
        private var INSTANCE: DatabaseRecipeImportManager? = null

        fun getInstance(
            context: Context,
            databaseRepository: DatabaseRecipeRepository,
            templateRepository: TemplateRepository = TemplateRepository.getInstance()
        ): DatabaseRecipeImportManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: DatabaseRecipeImportManager(
                    context,
                    databaseRepository,
                    templateRepository
                ).also { INSTANCE = it }
            }
        }

        fun clearInstance() {
            INSTANCE = null
        }
    }

    /**
     * 导入CSV文件 - 带事务支持
     */
    suspend fun importCsvFile(
        csvBytes: ByteArray,
        fileName: String = "import.csv"
    ): ImportSummary {
        val startTime = System.currentTimeMillis()

        try {
            val parsed = RecipeImportParser(currentTemplate()).parseCsv(csvBytes.toString(Charsets.UTF_8))
            val summary = parsed.rejected ?: persistRequestsWithTransaction(parsed.requests, parsed.errors)

            val importDuration = System.currentTimeMillis() - startTime
            recordImportLog(fileName, csvBytes.size.toLong(), "CSV", importDuration, summary)

            return summary
        } catch (e: Exception) {
            val importDuration = System.currentTimeMillis() - startTime
            val errorSummary = ImportSummary(0, 0, 1, listOf("导入失败: ${e.message}"))
            recordImportLog(fileName, csvBytes.size.toLong(), "CSV", importDuration, errorSummary, e.message)

            return errorSummary
        }
    }

    /**
     * 导入Excel文件 - 带事务支持
     */
    suspend fun importExcel(
        bytes: ByteArray,
        fileName: String = "import.xlsx"
    ): ImportSummary {
        val startTime = System.currentTimeMillis()

        try {
            val parsed = RecipeImportParser(currentTemplate()).parseExcel(bytes)
            val summary = parsed.rejected ?: persistRequestsWithTransaction(parsed.requests, parsed.errors)

            val importDuration = System.currentTimeMillis() - startTime
            recordImportLog(fileName, bytes.size.toLong(), "EXCEL", importDuration, summary)

            return summary
        } catch (e: Exception) {
            val importDuration = System.currentTimeMillis() - startTime
            val errorSummary = ImportSummary(0, 0, 1, listOf("导入失败: ${e.message}"))
            recordImportLog(fileName, bytes.size.toLong(), "EXCEL", importDuration, errorSummary, e.message)

            return errorSummary
        }
    }

    /**
     * 批量持久化配方请求 - 使用数据库事务
     */
    private suspend fun persistRequestsWithTransaction(
        requests: List<RecipeImportRequest>,
        parseErrors: List<String>
    ): ImportSummary {
        android.util.Log.i("DatabaseImportManager", "[Persist] 开始持久化，配方数: ${requests.size}, 解析错误数: ${parseErrors.size}")
        val errors = parseErrors.toMutableList()
        if (requests.isEmpty()) {
            if (errors.isEmpty()) {
                errors += "未解析到任何有效配方，请检查模板内容"
            }
            return ImportSummary(
                total = parseErrors.size,
                success = 0,
                failed = errors.size,
                errors = errors
            )
        }

        var processedCount = 0
        return try {
            database.withTransaction {
                requests.forEachIndexed { index, request ->
                    databaseRepository.addRecipe(request)
                    processedCount++
                    android.util.Log.i("DatabaseImportManager", "[Persist] 第${index + 1}个配方成功: ${request.name}")
                }
            }
            val total = requests.size + parseErrors.size
            android.util.Log.i("DatabaseImportManager", "[Persist] 持久化完成，成功: $processedCount, 失败: ${errors.size}")
            ImportSummary(
                total = total,
                success = processedCount,
                failed = errors.size,
                errors = errors
            )
        } catch (e: Exception) {
            android.util.Log.e("DatabaseImportManager", "[Persist] 执行事务失败: ${e.message}", e)
            val failedIndex = processedCount + 1
            errors += "第${failedIndex}条配方导入失败：${e.message ?: "未知错误"}"
            ImportSummary(
                total = requests.size + parseErrors.size,
                success = 0,
                failed = errors.size,
                errors = errors
            )
        }
    }

    /**
     * 记录导入日志
     */
    private suspend fun recordImportLog(
        fileName: String,
        fileSize: Long,
        fileType: String,
        importDuration: Long,
        summary: ImportSummary,
        errorDetails: String? = null
    ) {
        try {
            val importLogEntity = summary.toLogEntity(
                fileName = fileName,
                fileSize = fileSize,
                fileType = fileType,
                importDuration = importDuration,
                importedBy = "DATABASE_IMPORT",
                errorDetails = errorDetails
            )

            importLogDao.insertImportLog(importLogEntity)
        } catch (e: Exception) {
            // 日志记录失败不应该影响导入结果
            android.util.Log.e("DatabaseImportManager", "Failed to record import log", e)
        }
    }

    private fun currentTemplate(): TemplateDefinition {
        return templateRepository.getTemplateById(TEMPLATE_ID)
            ?: throw IllegalStateException("未找到标准模板，请先初始化模板数据")
    }
}
