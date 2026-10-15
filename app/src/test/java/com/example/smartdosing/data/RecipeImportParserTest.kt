package com.example.smartdosing.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class RecipeImportParserTest {

    private val template = TemplateRepository.getInstance().getTemplateById("standard_recipe_template")!!
    private val fields = template.fields.sortedBy { it.order }
    private val parser = RecipeImportParser(template)

    private fun csvHeader(): String = fields.joinToString(",") { it.label }

    /** 按字段 key 生成一行，未给出的列留空 */
    private fun row(values: Map<String, String>): List<String> = fields.map { values[it.key].orEmpty() }

    private fun csvLine(values: Map<String, String>): String =
        row(values).joinToString(",") { value ->
            if (value.contains(',') || value.contains('"')) "\"" + value.replace("\"", "\"\"") + "\"" else value
        }

    private fun csv(vararg rows: Map<String, String>): String =
        (listOf(csvHeader()) + rows.map { csvLine(it) }).joinToString("\r\n")

    @Test
    fun `CSV 按配方编码把多行材料合并成一个配方`() {
        val text = csv(
            mapOf("recipe_name" to "薄荷", "recipe_code" to "R-1", "material_name" to "薄荷醇", "material_weight" to "1.5"),
            mapOf("recipe_name" to "薄荷", "recipe_code" to "r-1", "material_name" to "丙二醇", "material_weight" to "20"),
            mapOf("recipe_name" to "草莓", "recipe_code" to "R-2", "material_name" to "草莓香精", "material_weight" to "3")
        )

        val result = parser.parseCsv(text)

        assertNull(result.rejected)
        assertTrue(result.errors.isEmpty())
        assertEquals(2, result.requests.size)
        val mint = result.requests.first { it.code == "R-1" }
        assertEquals(listOf("薄荷醇", "丙二醇"), mint.materials.map { it.name })
        assertEquals(listOf(1, 2), mint.materials.map { it.sequence })
        assertEquals("g", mint.materials.first().unit)
        assertEquals("IMPORT", mint.creator)
    }

    @Test
    fun `CSV 带 BOM 和引号逗号仍能正确解析`() {
        val text = "\uFEFF" + csv(
            mapOf("recipe_name" to "混合, 测试", "material_name" to "A \"特级\"", "material_weight" to "2")
        )

        val result = parser.parseCsv(text)

        assertNull(result.rejected)
        assertEquals("混合, 测试", result.requests.single().name)
        assertEquals("A \"特级\"", result.requests.single().materials.single().name)
    }

    @Test
    fun `CSV 缺少必填表头时整体拒绝`() {
        val header = fields.filterNot { it.key == "material_weight" }.joinToString(",") { it.label }
        val result = parser.parseCsv(header + "\nx,y")

        val rejected = result.rejected
        assertNotNull(rejected)
        assertTrue(rejected!!.errors.single().contains("重量"))
        assertTrue(result.requests.isEmpty())
    }

    @Test
    fun `CSV 只有表头时提示内容为空`() {
        val result = parser.parseCsv(csvHeader())
        assertEquals("CSV内容为空，请使用模板填写数据", result.rejected!!.errors.single())
    }

    @Test
    fun `无效重量的行报错但不影响同配方其它行`() {
        val text = csv(
            mapOf("recipe_name" to "薄荷", "material_name" to "薄荷醇", "material_weight" to "abc"),
            mapOf("recipe_name" to "薄荷", "material_name" to "丙二醇", "material_weight" to "10")
        )

        val result = parser.parseCsv(text)

        assertEquals(listOf("第2 行材料名称或重量无效"), result.errors)
        assertEquals(listOf("丙二醇"), result.requests.single().materials.map { it.name })
    }

    @Test
    fun `配方所有材料都无效时报配方级错误`() {
        val text = csv(mapOf("recipe_name" to "空配方", "material_name" to "", "material_weight" to "1"))

        val result = parser.parseCsv(text)

        assertTrue(result.requests.isEmpty())
        assertTrue(result.errors.contains("配方\"空配方\"没有有效的材料信息"))
    }

    @Test
    fun `优先级 标签 备注 分类按约定映射`() {
        val text = csv(
            mapOf(
                "recipe_name" to "加急单",
                "task_priority" to "加急",
                "task_tags" to "夏季，新品; 试产",
                "task_note" to "先做小样",
                "recipe_category" to "辅料类",
                "material_name" to "A",
                "material_weight" to "1",
                "material_notes" to "低温"
            )
        )

        val request = parser.parseCsv(text).requests.single()

        assertEquals(RecipePriority.URGENT, request.priority)
        assertEquals(listOf("夏季", "新品", "试产"), request.tags)
        assertEquals("先做小样；低温", request.description)
        assertEquals("辅料", request.category)
    }

    @Test
    fun `Excel 与 CSV 走同一套规则（含优先级和标签）`() {
        val rows = listOf(
            fields.map { it.label },
            row(
                mapOf(
                    "recipe_name" to "薄荷",
                    "recipe_code" to "X-1",
                    "task_priority" to "HIGH",
                    "task_tags" to "a,b",
                    "material_name" to "薄荷醇",
                    "material_weight" to "1.25"
                )
            ),
            row(mapOf("recipe_name" to "薄荷", "recipe_code" to "X-1", "material_name" to "丙二醇", "material_weight" to "5"))
        )

        val result = parser.parseExcel(buildXlsx(rows))

        assertNull(result.rejected)
        val request = result.requests.single()
        assertEquals("X-1", request.code)
        assertEquals(RecipePriority.HIGH, request.priority)
        assertEquals(listOf("a", "b"), request.tags)
        assertEquals(listOf(1.25, 5.0), request.materials.map { it.weight })
    }

    @Test
    fun `Excel 缺少工作表时拒绝`() {
        val bytes = ByteArrayOutputStream().also { out ->
            ZipOutputStream(out).use { zip ->
                zip.putNextEntry(ZipEntry("xl/workbook.xml"))
                zip.write("<workbook/>".toByteArray())
                zip.closeEntry()
            }
        }.toByteArray()

        val result = parser.parseExcel(bytes)

        assertEquals("Excel模板缺少工作表，请使用最新模板", result.rejected!!.errors.single())
    }

    /** 构造一个最小 xlsx：所有单元格写成 sharedStrings 引用 */
    private fun buildXlsx(rows: List<List<String>>): ByteArray {
        val shared = mutableListOf<String>()
        fun indexOf(text: String): Int = shared.indexOf(text).takeIf { it >= 0 } ?: shared.size.also { shared += text }

        val sheet = buildString {
            append("<worksheet><sheetData>")
            rows.forEachIndexed { r, cells ->
                append("<row r=\"${r + 1}\">")
                cells.forEachIndexed { c, text ->
                    if (text.isNotEmpty()) {
                        val ref = columnName(c) + (r + 1)
                        append("<c r=\"$ref\" t=\"s\"><v>${indexOf(text)}</v></c>")
                    }
                }
                append("</row>")
            }
            append("</sheetData></worksheet>")
        }
        val sharedXml = shared.joinToString("", "<sst>", "</sst>") { "<si><t>${escape(it)}</t></si>" }

        return ByteArrayOutputStream().also { out ->
            ZipOutputStream(out).use { zip ->
                zip.putNextEntry(ZipEntry("xl/worksheets/sheet1.xml"))
                zip.write(sheet.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
                zip.putNextEntry(ZipEntry("xl/sharedStrings.xml"))
                zip.write(sharedXml.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }.toByteArray()
    }

    private fun columnName(index: Int): String {
        var n = index + 1
        val sb = StringBuilder()
        while (n > 0) {
            val rem = (n - 1) % 26
            sb.insert(0, ('A' + rem))
            n = (n - 1) / 26
        }
        return sb.toString()
    }

    private fun escape(text: String) = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}
