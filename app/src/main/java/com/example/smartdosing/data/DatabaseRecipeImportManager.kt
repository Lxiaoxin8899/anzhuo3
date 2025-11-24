package com.example.smartdosing.data

import android.content.Context
import java.io.ByteArrayInputStream
import java.util.Locale
import java.util.zip.ZipInputStream
import kotlin.text.Charsets

import com.example.smartdosing.database.SmartDosingDatabase
import com.example.smartdosing.database.DataMapper
import com.example.smartdosing.database.DataMapper.toLogEntity

/**
 * 基于数据库的配方导入管理器
 * 支持事务操作、导入日志记录和性能优化
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
        private const val MATERIAL_PREFIX = "material_line"

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
            val text = csvBytes.toString(Charsets.UTF_8)
            val summary = importCsvTextWithTransaction(text)

            // 记录导入日志
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
     * 导入CSV文本 - 带事务支持（支持垂直展开格式）
     */
    private suspend fun importCsvTextWithTransaction(csvText: String): ImportSummary {
        val template = currentTemplate()
        val lines = csvText.split(Regex("\\r?\\n"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        if (lines.size <= 1) {
            return ImportSummary(0, 0, 0, listOf("CSV内容为空，请使用模板填写数据"))
        }

        val dataLines = lines.drop(1)
        if (dataLines.isEmpty()) {
            return ImportSummary(0, 0, 0, listOf("CSV中未找到数据行，请保留表头并填写内容"))
        }

        val errors = mutableListOf<String>()

        // 解析所有行，生成row列表
        android.util.Log.i("DatabaseImportManager", "[CSV] 开始解析所有行")
        val allRows = mutableListOf<Pair<Int, Map<String, String>>>() // Pair<行号, 字段值Map>
        dataLines.forEachIndexed { index, line ->
            val rowNumber = index + 2
            val columns = parseCsvLine(line)
            if (columns.all { it.isBlank() }) {
                return@forEachIndexed
            }

            val valueMap = mutableMapOf<String, String>()
            template.fields.sortedBy { it.order }.forEachIndexed { columnIndex, field ->
                val value = columns.getOrNull(columnIndex)?.trim() ?: ""
                valueMap[field.key] = value
            }

            allRows += rowNumber to valueMap
        }

        // 按配方编码或配方名称分组（垂直展开格式）
        val recipeGroups = allRows.groupBy { (_, valueMap) ->
            val code = valueMap["recipe_code"].orEmpty().trim()
            val name = valueMap["recipe_name"].orEmpty().trim()
            normalizeKey(code, name) ?: name // 使用规范化的key或配方名称
        }

        android.util.Log.i("DatabaseImportManager", "[CSV] 分组完成，共${recipeGroups.size}个配方组")

        val requests = mutableListOf<RecipeImportRequest>()

        recipeGroups.forEach { (groupKey, rows) ->
            android.util.Log.i("DatabaseImportManager", "[CSV] 处理配方组: $groupKey, 行数: ${rows.size}")
            if (groupKey.isBlank()) {
                rows.forEach { (rowNumber, _) ->
                    errors += "第$rowNumber 行缺少配方编码或配方名称"
                }
                return@forEach
            }

            // 获取第一行作为配方基本信息
            val (firstRowNumber, firstRow) = rows.first()
            val name = firstRow["recipe_name"].orEmpty()
            if (name.isBlank()) {
                errors += "第$firstRowNumber 行缺少配方名称"
                return@forEach
            }

            // 从所有行中提取材料信息
            val materials = mutableListOf<MaterialImport>()
            rows.forEachIndexed { index, (rowNumber, valueMap) ->
                val materialName = valueMap["material_name"].orEmpty()
                val materialWeight = valueMap["material_weight"]?.toDoubleOrNull()
                val materialUnit = valueMap["material_unit"].orEmpty().ifBlank { "g" }
                val materialSeq = valueMap["material_sequence"]?.toIntOrNull() ?: (index + 1)
                val materialNotes = valueMap["material_notes"].orEmpty()

                if (materialName.isBlank() || materialWeight == null) {
                    errors += "第$rowNumber 行材料名称或重量无效"
                    return@forEachIndexed
                }

                materials += MaterialImport(
                    name = materialName,
                    weight = materialWeight,
                    unit = materialUnit,
                    sequence = materialSeq,
                    notes = materialNotes
                )
            }

            if (materials.isEmpty()) {
                errors += "配方\"$name\"没有有效的材料信息"
                return@forEach
            }

            // 将所有材料的备注合并为配方描述
            val allNotes = materials.map { it.notes }.filter { it.isNotBlank() }.joinToString("; ")

            requests += RecipeImportRequest(
                code = firstRow["recipe_code"].orEmpty(),
                name = name,
                category = firstRow["recipe_category"].orEmpty().ifBlank { "未分类" },
                subCategory = "",
                customer = "",
                batchNo = firstRow["batch_no"].orEmpty(),
                version = "1.0",
                description = allNotes,
                materials = materials,
                status = RecipeStatus.ACTIVE,
                priority = RecipePriority.NORMAL,
                tags = emptyList(),
                creator = firstRow["designer"].orEmpty().ifBlank { "IMPORT" },
                reviewer = ""
            )
        }

        return persistRequestsWithTransaction(requests, errors)
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
            val template = currentTemplate()
            val entries = unzipEntries(bytes)
            // 使用单工作表
            val sheetXml = entries["xl/worksheets/sheet1.xml"]

            if (sheetXml == null) {
                return ImportSummary(0, 0, 0, listOf("Excel模板缺少工作表，请使用最新模板"))
            }

            val summary = importExcelWithTransaction(template, sheetXml)

            // 记录导入日志
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
     * 处理Excel导入 - 带事务支持（垂直展开格式）
     */
    private suspend fun importExcelWithTransaction(
        template: TemplateDefinition,
        sheetXml: String
    ): ImportSummary {
        val rows = parseSheetXml(sheetXml)
        val dataRows = rows.drop(1).filter { row -> row.any { it.isNotBlank() } }

        if (dataRows.isEmpty()) {
            return ImportSummary(0, 0, 0, listOf("Excel表格没有数据"))
        }

        val errors = mutableListOf<String>()

        // 解析所有行，生成row列表
        val allRows = mutableListOf<Pair<Int, Map<String, String>>>()
        dataRows.forEachIndexed { index, row ->
            val rowNumber = index + 2
            val valueMap = mutableMapOf<String, String>()
            template.fields.sortedBy { it.order }.forEachIndexed { columnIndex, field ->
                valueMap[field.key] = row.getOrNull(columnIndex)?.trim() ?: ""
            }
            allRows += rowNumber to valueMap
        }

        // 按配方编码或配方名称分组（垂直展开格式）
        val recipeGroups = allRows.groupBy { (_, valueMap) ->
            val code = valueMap["recipe_code"].orEmpty().trim()
            val name = valueMap["recipe_name"].orEmpty().trim()
            normalizeKey(code, name) ?: name
        }

        val requests = mutableListOf<RecipeImportRequest>()

        recipeGroups.forEach { (groupKey, rows) ->
            if (groupKey.isBlank()) {
                rows.forEach { (rowNumber, _) ->
                    errors += "第$rowNumber 行缺少配方编码或配方名称"
                }
                return@forEach
            }

            // 获取第一行作为配方基本信息
            val (firstRowNumber, firstRow) = rows.first()
            val name = firstRow["recipe_name"].orEmpty()
            if (name.isBlank()) {
                errors += "第$firstRowNumber 行缺少配方名称"
                return@forEach
            }

            // 从所有行中提取材料信息
            val materials = mutableListOf<MaterialImport>()
            rows.forEachIndexed { index, (rowNumber, valueMap) ->
                val materialName = valueMap["material_name"].orEmpty()
                val materialWeight = valueMap["material_weight"]?.toDoubleOrNull()
                val materialUnit = valueMap["material_unit"].orEmpty().ifBlank { "g" }
                val materialSeq = valueMap["material_sequence"]?.toIntOrNull() ?: (index + 1)
                val materialNotes = valueMap["material_notes"].orEmpty()

                if (materialName.isBlank() || materialWeight == null) {
                    errors += "第$rowNumber 行材料名称或重量无效"
                    return@forEachIndexed
                }

                materials += MaterialImport(
                    name = materialName,
                    weight = materialWeight,
                    unit = materialUnit,
                    sequence = materialSeq,
                    notes = materialNotes
                )
            }

            if (materials.isEmpty()) {
                errors += "配方\"$name\"没有有效的材料信息"
                return@forEach
            }

            // 将所有材料的备注合并为配方描述
            val allNotes = materials.map { it.notes }.filter { it.isNotBlank() }.joinToString("; ")

            requests += RecipeImportRequest(
                code = firstRow["recipe_code"].orEmpty(),
                name = name,
                category = firstRow["recipe_category"].orEmpty().ifBlank { "未分类" },
                subCategory = "",
                customer = "",
                batchNo = firstRow["batch_no"].orEmpty(),
                version = "1.0",
                description = allNotes,
                materials = materials,
                status = RecipeStatus.ACTIVE,
                priority = RecipePriority.NORMAL,
                tags = emptyList(),
                creator = firstRow["designer"].orEmpty().ifBlank { "IMPORT" },
                reviewer = ""
            )
        }

        return persistRequestsWithTransaction(requests, errors)
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
        var successCount = 0

        // 直接逐个添加配方，不使用 runInTransaction（避免死锁）
        requests.forEachIndexed { index, request ->
            try {
                databaseRepository.addRecipe(request)
                successCount++
                android.util.Log.i("DatabaseImportManager", "[Persist] 第${index + 1}个配方成功: ${request.name}")
            } catch (e: Exception) {
                android.util.Log.e("DatabaseImportManager", "[Persist] 第${index + 1}个配方失败: ${e.message}", e)
                errors += "第${index + 1}条配方导入失败：${e.message ?: "未知错误"}"
            }
        }

        val total = requests.size + parseErrors.size
        val failed = errors.size

        android.util.Log.i("DatabaseImportManager", "[Persist] 持久化完成，成功: $successCount, 失败: $failed")

        return ImportSummary(
            total = total,
            success = successCount,
            failed = failed,
            errors = errors
        )
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

    // =================================
    // 解析辅助方法
    // =================================

    private fun unzipEntries(bytes: ByteArray): Map<String, String> {
        val map = mutableMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val content = zip.readBytes().toString(Charsets.UTF_8)
                map[entry.name] = content
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        return map
    }

    private fun parseSheetXml(xml: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        val rowRegex = Regex("<row[^>]*>(.*?)</row>", RegexOption.DOT_MATCHES_ALL)
        val cellRegex = Regex("<t[^>]*>(.*?)</t>", RegexOption.DOT_MATCHES_ALL)
        rowRegex.findAll(xml).forEach { rowMatch ->
            val cells = cellRegex.findAll(rowMatch.groupValues[1])
                .map { unescapeXml(it.groupValues[1]) }
                .toList()
            rows += cells
        }
        return rows
    }

    private fun unescapeXml(text: String): String {
        return text
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&amp;", "&")
    }

    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val ch = line[i]
            when {
                ch == '"' -> {
                    if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                        current.append('"')
                        i++
                    } else {
                        inQuotes = !inQuotes
                    }
                }
                ch == ',' && !inQuotes -> {
                    result += current.toString()
                    current.clear()
                }
                else -> current.append(ch)
            }
            i++
        }
        result += current.toString()
        return result
    }

    private fun normalizeKey(primary: String?, fallback: String?): String? {
        val base = when {
            !primary.isNullOrBlank() -> primary
            !fallback.isNullOrBlank() -> fallback
            else -> null
        }
        return base?.trim()?.uppercase(Locale.getDefault())
    }

    private fun buildDescription(description: String, notes: String): String {
        return listOf(description, notes.takeIf { it.isNotBlank() }?.let { "备注：$it" })
            .filterNotNull()
            .filter { it.isNotBlank() }
            .joinToString("\n")
    }

    private fun parseTagsFromValue(tagsString: String): List<String> {
        return if (tagsString.isBlank()) {
            emptyList()
        } else {
            tagsString.split(",", ";", "，", "；")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
        }
    }

    private fun currentTemplate(): TemplateDefinition {
        return templateRepository.getTemplateById(TEMPLATE_ID)
            ?: throw IllegalStateException("未找到标准模板，请先初始化模板数据")
    }
}