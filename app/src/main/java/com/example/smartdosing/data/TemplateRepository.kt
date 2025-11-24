package com.example.smartdosing.data

import kotlinx.coroutines.flow.MutableStateFlow
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * 标准模板字段定义
 */
data class TemplateField(
    val id: String,
    val key: String,
    val label: String,
    val description: String,
    val required: Boolean,
    val example: String,
    val order: Int
)

/**
 * 模板定义
 */
data class TemplateDefinition(
    val id: String,
    val name: String,
    val description: String,
    val version: Int,
    val updatedAt: String,
    val supportedFormats: List<TemplateFormat>,
    val fields: List<TemplateField>
)

enum class TemplateFormat {
    CSV,
    EXCEL
}

/**
 * 模板字段更新载荷
 */
data class TemplateFieldPayload(
    val id: String? = null,
    val key: String,
    val label: String,
    val description: String = "",
    val required: Boolean = true,
    val example: String = "",
    val order: Int = 0
)

/**
 * 模板更新请求体
 */
data class TemplateUpdateRequest(
    val name: String? = null,
    val description: String? = null,
    val fields: List<TemplateFieldPayload>
)

/**
 * 模板仓库（内存）
 */
class TemplateRepository private constructor() {

    private val defaultTemplates = buildDefaultTemplates().associateBy { it.id }
    private val templatesFlow = MutableStateFlow(defaultTemplates.values.map { it.deepCopy() })

    companion object {
        @Volatile
        private var INSTANCE: TemplateRepository? = null

        fun getInstance(): TemplateRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: TemplateRepository().also { INSTANCE = it }
            }
        }
    }

    fun getTemplates(): List<TemplateDefinition> = templatesFlow.value

    fun getTemplateById(id: String): TemplateDefinition? = templatesFlow.value.find { it.id == id }

    fun updateTemplate(id: String, request: TemplateUpdateRequest): TemplateDefinition? {
        val current = getTemplateById(id) ?: return null
        if (request.fields.isEmpty()) {
            throw IllegalArgumentException("模板字段不能为空")
        }

        val normalizedFields = normalizeFields(request.fields)
        val updated = current.copy(
            name = request.name?.takeIf { it.isNotBlank() } ?: current.name,
            description = request.description ?: current.description,
            version = current.version + 1,
            updatedAt = now(),
            fields = normalizedFields
        )

        templatesFlow.value = templatesFlow.value.map { if (it.id == id) updated else it }
        return updated
    }

    fun resetTemplate(id: String): TemplateDefinition? {
        val default = defaultTemplates[id] ?: return null
        templatesFlow.value = templatesFlow.value.map { if (it.id == id) default.deepCopy() else it }
        return getTemplateById(id)
    }

    fun generateCsvTemplate(templateId: String): Pair<String, ByteArray>? {
        val template = getTemplateById(templateId) ?: return null
        val builder = StringBuilder()
        builder.appendLine(template.fields.joinToString(",") { escapeCsv(it.label) })
        buildSampleRows(template).forEach { row ->
            builder.appendLine(row.joinToString(",") { escapeCsv(it) })
        }
        val fileName = "${template.name}_模板.csv"
        return fileName to builder.toString().toByteArray(Charsets.UTF_8)
    }

    fun generateExcelTemplate(templateId: String): Pair<String, ByteArray>? {
        val template = getTemplateById(templateId) ?: return null
        val fileName = "${template.name}_模板.xlsx"
        return fileName to buildExcelFile(template)
    }

    private fun buildExcelFile(template: TemplateDefinition): ByteArray {
        val summaryFields = template.fields.filterNot { it.key.startsWith("material_line") }
        val materialFields = template.fields.filter { it.key.startsWith("material_line") }
        val summaryHeaders = summaryFields.map { it.label }
        val summaryRows = listOf(summaryFields.map { it.example.orEmpty() })
        val detailHeaders = listOf("配方编码", "序号", "材料名称", "重量", "单位", "备注")
        val detailRows = buildMaterialDetailSampleRows(template, materialFields)
        val summarySheetXml = buildSheetXml(summaryHeaders, summaryRows)
        val detailSheetXml = buildSheetXml(detailHeaders, detailRows)
        val nowIso = isoNow()
        val workbookXml = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"
                      xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
                <sheets>
                    <sheet name="配方信息" sheetId="1" r:id="rId1"/>
                    <sheet name="材料明细" sheetId="2" r:id="rId2"/>
                </sheets>
            </workbook>
        """.trimIndent()

        val contentTypes = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                <Default Extension="xml" ContentType="application/xml"/>
                <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
                <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
                <Override PartName="/xl/worksheets/sheet2.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
                <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
                <Override PartName="/docProps/app.xml" ContentType="application/vnd.openxmlformats-officedocument.extended-properties+xml"/>
                <Override PartName="/docProps/core.xml" ContentType="application/vnd.openxmlformats-package.core-properties+xml"/>
            </Types>
        """.trimIndent()

        val rootRels = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
                <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties" Target="docProps/core.xml"/>
                <Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/extended-properties" Target="docProps/app.xml"/>
            </Relationships>
        """.trimIndent()

        val workbookRels = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
                <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet2.xml"/>
                <Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
            </Relationships>
        """.trimIndent()

        val stylesXml = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
                <fonts count="1"><font/></fonts>
                <fills count="1"><fill/></fills>
                <borders count="1"><border/></borders>
                <cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
                <cellXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/></cellXfs>
            </styleSheet>
        """.trimIndent()

        val coreProps = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <cp:coreProperties xmlns:cp="http://schemas.openxmlformats.org/package/2006/metadata/core-properties"
                               xmlns:dc="http://purl.org/dc/elements/1.1/"
                               xmlns:dcterms="http://purl.org/dc/terms/"
                               xmlns:dcmitype="http://purl.org/dc/dcmitype/"
                               xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                <dc:title>${escapeXml(template.name)} 模板</dc:title>
                <dc:creator>SmartDosing</dc:creator>
                <cp:lastModifiedBy>SmartDosing</cp:lastModifiedBy>
                <dcterms:created xsi:type="dcterms:W3CDTF">$nowIso</dcterms:created>
                <dcterms:modified xsi:type="dcterms:W3CDTF">$nowIso</dcterms:modified>
            </cp:coreProperties>
        """.trimIndent()

        val appProps = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Properties xmlns="http://schemas.openxmlformats.org/officeDocument/2006/extended-properties"
                        xmlns:vt="http://schemas.openxmlformats.org/officeDocument/2006/docPropsVTypes">
                <Application>SmartDosing</Application>
                <DocSecurity>0</DocSecurity>
                <ScaleCrop>false</ScaleCrop>
            </Properties>
        """.trimIndent()

        val outputStream = ByteArrayOutputStream()
        ZipOutputStream(outputStream).use { zip ->
            addZipEntry(zip, "[Content_Types].xml", contentTypes)
            addZipEntry(zip, "_rels/.rels", rootRels)
            addZipEntry(zip, "xl/workbook.xml", workbookXml)
            addZipEntry(zip, "xl/_rels/workbook.xml.rels", workbookRels)
            addZipEntry(zip, "xl/worksheets/sheet1.xml", summarySheetXml)
            addZipEntry(zip, "xl/worksheets/sheet2.xml", detailSheetXml)
            addZipEntry(zip, "xl/styles.xml", stylesXml)
            addZipEntry(zip, "docProps/core.xml", coreProps)
            addZipEntry(zip, "docProps/app.xml", appProps)
        }
        return outputStream.toByteArray()
    }

    private fun buildSheetXml(headers: List<String>, sampleRows: List<List<String>>): String {
        val rows = listOf(headers) + sampleRows
        val rowXml = rows.mapIndexed { rowIndex, row ->
            val cells = row.mapIndexed { colIndex, value ->
                val cellRef = "${columnName(colIndex)}${rowIndex + 1}"
                """<c r="$cellRef" t="inlineStr"><is><t>${escapeXml(value)}</t></is></c>"""
            }.joinToString("")
            """<row r="${rowIndex + 1}">$cells</row>"""
        }.joinToString("")

        return """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
                <sheetData>$rowXml</sheetData>
            </worksheet>
        """.trimIndent()
    }

    private fun normalizeFields(fields: List<TemplateFieldPayload>): List<TemplateField> {
        val sanitized = fields.mapIndexed { index, payload ->
            val id = payload.id ?: UUID.randomUUID().toString()
            val normalizedKey = sanitizeKey(payload.key.ifBlank { "column_${index + 1}" })
            val label = payload.label.ifBlank { payload.key.ifBlank { "字段${index + 1}" } }
            TemplateField(
                id = id,
                key = normalizedKey,
                label = label,
                description = payload.description,
                required = payload.required,
                example = payload.example,
                order = payload.order.takeIf { it > 0 } ?: (index + 1)
            )
        }

        val finalFields = mutableListOf<TemplateField>()
        val usedKeys = mutableSetOf<String>()
        sanitized.sortedBy { it.order }.forEachIndexed { idx, field ->
            var key = field.key
            var suffix = 1
            while (usedKeys.contains(key)) {
                key = "${field.key}_${suffix++}"
            }
            usedKeys.add(key)
            finalFields += field.copy(key = key, order = idx + 1)
        }
        return finalFields
    }

    private fun addZipEntry(zip: ZipOutputStream, path: String, content: String) {
        val entry = ZipEntry(path)
        zip.putNextEntry(entry)
        zip.write(content.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    private fun TemplateDefinition.deepCopy(): TemplateDefinition {
        return copy(fields = fields.map { it.copy() })
    }

    private fun buildDefaultTemplates(): List<TemplateDefinition> {
        val now = now()
        val standardFields = listOf(
            TemplateField(
                id = UUID.randomUUID().toString(),
                key = "recipe_name",
                label = "配方名称",
                description = "整条记录的配方名称，示例：草莓烟油",
                required = true,
                example = "草莓烟油",
                order = 1
            ),
            TemplateField(
                id = UUID.randomUUID().toString(),
                key = "recipe_code",
                label = "配方编码",
                description = "可选字段，配方唯一编号或物料号",
                required = false,
                example = "S000001",
                order = 2
            ),
            TemplateField(
                id = UUID.randomUUID().toString(),
                key = "designer",
                label = "设计师",
                description = "负责配方设计/审核的人员",
                required = false,
                example = "张工",
                order = 3
            ),
            TemplateField(
                id = UUID.randomUUID().toString(),
                key = "batch_no",
                label = "配方批次",
                description = "可选，配方批次或版本信息",
                required = false,
                example = "2025-Q1",
                order = 4
            ),
            TemplateField(
                id = UUID.randomUUID().toString(),
                key = "recipe_category",
                label = "配方分类",
                description = "用于统计的分类，例如香精/溶剂/调味剂",
                required = false,
                example = "香精",
                order = 5
            ),
            TemplateField(
                id = UUID.randomUUID().toString(),
                key = "recipe_description",
                label = "配方描述",
                description = "对整个配方的说明，整条记录只填写一次",
                required = false,
                example = "经典草莓烟油配方",
                order = 6
            ),
            TemplateField(
                id = UUID.randomUUID().toString(),
                key = "material_line_1",
                label = "材料1名称:重量:单位:序号",
                description = "请按“名称:重量:单位:序号”格式填写，例如 草莓香精:50:g:1",
                required = true,
                example = "草莓香精:50:g:1",
                order = 7
            ),
            TemplateField(
                id = UUID.randomUUID().toString(),
                key = "material_line_2",
                label = "材料2名称:重量:单位:序号",
                description = "示例：草莓香精(溶剂):150:ml:2",
                required = false,
                example = "草莓香精(溶剂):150:ml:2",
                order = 8
            ),
            TemplateField(
                id = UUID.randomUUID().toString(),
                key = "material_line_3",
                label = "材料3名称:重量:单位:序号",
                description = "可继续追加更多材料列，按照同样格式填写",
                required = false,
                example = "柠檬酸:10:g:3",
                order = 9
            ),
            TemplateField(
                id = UUID.randomUUID().toString(),
                key = "material_notes",
                label = "材料/工艺备注",
                description = "整体备注或注意事项，例如“需低温保存”",
                required = false,
                example = "需低温保存",
                order = 10
            )
        )

        val standardTemplate = TemplateDefinition(
            id = "standard_recipe_template",
            name = "标准配方导入模板",
            description = "用于Excel/CSV批量导入配方的标准模板，可自定义列标题和示例值。",
            version = 1,
            updatedAt = now,
            supportedFormats = listOf(TemplateFormat.CSV, TemplateFormat.EXCEL),
            fields = standardFields
        )

        return listOf(standardTemplate)
    }

    private fun buildSampleRows(template: TemplateDefinition): List<List<String>> {
        // 仅生成一条示例记录，避免示例行重复
        val baseRow = template.fields.map { it.example.orEmpty() }
        return listOf(baseRow)
    }

    private fun buildMaterialDetailSampleRows(
        template: TemplateDefinition,
        materialFields: List<TemplateField>
    ): List<List<String>> {
        val recipeCode = template.fields.find { it.key == "recipe_code" }?.example?.takeIf { it.isNotBlank() }
            ?: "SAMPLE_CODE"
        if (materialFields.isEmpty()) {
            return listOf(listOf(recipeCode, "1", "示例材料", "50", "g", ""))
        }
        return materialFields.mapIndexed { index, field ->
            val parts = field.example.split(":")
            val name = parts.getOrNull(0).orEmpty()
            val weight = parts.getOrNull(1).orEmpty()
            val unit = parts.getOrNull(2).orEmpty()
            val seq = parts.getOrNull(3).orEmpty().ifBlank { (index + 1).toString() }
            listOf(recipeCode, seq, name, weight, unit, "")
        }
    }

    private fun now(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return sdf.format(Date())
    }

    private fun isoNow(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault())
        return sdf.format(Date())
    }

    private fun sanitizeKey(key: String): String {
        val sanitized = key.lowercase(Locale.getDefault()).replace("[^a-z0-9_]+".toRegex(), "_")
        return sanitized.trim('_').ifBlank { "column_${System.currentTimeMillis()}" }
    }

    private fun escapeCsv(text: String): String {
        if (text.isEmpty()) return ""
        val needsQuotes = text.contains(",") || text.contains("\"") || text.contains("\n") || text.contains("\r")
        val escaped = text.replace("\"", "\"\"")
        return if (needsQuotes) "\"$escaped\"" else escaped
    }

    private fun escapeXml(text: String): String {
        if (text.isEmpty()) return ""
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    private fun columnName(index: Int): String {
        var i = index
        var name = ""
        do {
            val remainder = i % 26
            name = ('A'.code + remainder).toChar() + name
            i = i / 26 - 1
        } while (i >= 0)
        return name
    }
}
