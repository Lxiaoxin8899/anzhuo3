package com.example.smartdosing.database

import com.example.smartdosing.data.*
import com.example.smartdosing.database.entities.*
import com.example.smartdosing.database.dao.CategoryCount
import com.example.smartdosing.database.dao.CustomerCount
import java.text.SimpleDateFormat
import java.util.*

/**
 * 数据映射器 - 处理Entity与业务模型之间的转换
 */
object DataMapper {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    // =================================
    // Recipe 转换
    // =================================

    /**
     * RecipeEntity + 关联数据 -> Recipe
     */
    fun RecipeEntity.toDomainModel(materials: List<MaterialEntity>, tags: List<String>): Recipe {
        return Recipe(
            id = id,
            code = code,
            name = name,
            category = category,
            subCategory = subCategory,
            customer = customer,
            batchNo = batchNo,
            version = version,
            description = description,
            materials = materials.map { it.toDomainModel() },
            totalWeight = totalWeight,
            createTime = createTime,
            updateTime = updateTime,
            lastUsed = lastUsed,
            usageCount = usageCount,
            status = try { RecipeStatus.valueOf(status) } catch (e: Exception) { RecipeStatus.ACTIVE },
            priority = try { RecipePriority.valueOf(priority) } catch (e: Exception) { RecipePriority.NORMAL },
            tags = tags,
            creator = creator,
            reviewer = reviewer
        )
    }

    /**
     * RecipeWithMaterials -> Recipe
     */
    fun RecipeWithMaterials.toDomainModel(): Recipe {
        return recipe.toDomainModel(materials, tags)
    }

    /**
     * Recipe -> RecipeEntity
     */
    fun Recipe.toEntity(): RecipeEntity {
        return RecipeEntity(
            id = id,
            code = code,
            name = name,
            category = category,
            subCategory = subCategory,
            customer = customer,
            batchNo = batchNo,
            version = version,
            description = description,
            totalWeight = totalWeight,
            createTime = createTime,
            updateTime = updateTime,
            lastUsed = lastUsed,
            usageCount = usageCount,
            status = status.name,
            priority = priority.name,
            creator = creator,
            reviewer = reviewer
        )
    }

    /**
     * RecipeImportRequest -> Recipe (用于导入)
     */
    fun RecipeImportRequest.toDomainModel(
        id: String = "recipe_${System.currentTimeMillis()}",
        code: String = this.code.ifEmpty { generateRecipeCode(category, materials.sumOf { it.weight }) },
        currentTime: String = dateFormat.format(Date())
    ): Recipe {
        return Recipe(
            id = id,
            code = code,
            name = name,
            category = category,
            subCategory = subCategory,
            customer = customer,
            batchNo = batchNo,
            version = version,
            description = description,
            // 生成材料列表时写入导入模板的编码，确保数据链路一致
            materials = materials.mapIndexed { index, materialImport ->
                Material(
                    id = "material_${System.currentTimeMillis()}_$index",
                    name = materialImport.name,
                    weight = materialImport.weight,
                    unit = materialImport.unit,
                    sequence = materialImport.sequence,
                    notes = materialImport.notes,
                    code = materialImport.code
                )
            },
            totalWeight = materials.sumOf { it.weight },
            createTime = currentTime,
            updateTime = currentTime,
            lastUsed = null,
            usageCount = 0,
            status = status,
            priority = priority,
            tags = tags,
            creator = creator,
            reviewer = reviewer
        )
    }

    // =================================
    // Material 转换
    // =================================

    /**
     * MaterialEntity -> Material
     */
    fun MaterialEntity.toDomainModel(): Material {
        return Material(
            id = id,
            name = name,
            weight = weight,
            unit = unit,
            sequence = sequence,
            notes = notes,
            code = code
        )
    }

    /**
     * Material -> MaterialEntity
     */
    fun Material.toEntity(recipeId: String): MaterialEntity {
        return MaterialEntity(
            id = id,
            recipeId = recipeId,
            name = name,
            weight = weight,
            unit = unit,
            sequence = sequence,
            notes = notes,
            code = code
        )
    }

    /**
     * MaterialImport -> MaterialEntity
     */
    fun MaterialImport.toEntity(
        recipeId: String,
        materialId: String = "material_${System.currentTimeMillis()}_${sequence}"
    ): MaterialEntity {
        return MaterialEntity(
            id = materialId,
            recipeId = recipeId,
            name = name,
            weight = weight,
            unit = unit,
            sequence = sequence,
            notes = notes,
            code = code
        )
    }

    // =================================
    // Template 转换
    // =================================

    /**
     * TemplateEntity -> TemplateDefinition
     */
    fun TemplateEntity.toDomainModel(fields: List<TemplateFieldEntity>): TemplateDefinition {
        return TemplateDefinition(
            id = id,
            name = name,
            description = description,
            version = version,
            updatedAt = updatedAt,
            supportedFormats = listOf(TemplateFormat.CSV, TemplateFormat.EXCEL),
            fields = fields.map { it.toDomainModel() }
        )
    }

    /**
     * TemplateFieldEntity -> TemplateField
     */
    fun TemplateFieldEntity.toDomainModel(): TemplateField {
        return TemplateField(
            id = id,
            key = fieldKey,
            label = label,
            description = description,
            required = required,
            example = example,
            order = fieldOrder
        )
    }

    /**
     * TemplateDefinition -> TemplateEntity
     */
    fun TemplateDefinition.toEntity(
        isDefault: Boolean = false,
        createdBy: String = "USER"
    ): TemplateEntity {
        return TemplateEntity(
            id = id,
            name = name,
            description = description,
            version = version,
            updatedAt = updatedAt,
            isDefault = isDefault,
            createdBy = createdBy
        )
    }

    /**
     * TemplateField -> TemplateFieldEntity
     */
    fun TemplateField.toEntity(templateId: String): TemplateFieldEntity {
        return TemplateFieldEntity(
            id = id,
            templateId = templateId,
            fieldKey = key,
            label = label,
            description = description,
            required = required,
            example = example,
            fieldOrder = order
        )
    }

    // =================================
    // ImportLog 转换
    // =================================

    /**
     * ImportSummary -> ImportLogEntity
     */
    fun ImportSummary.toLogEntity(
        fileName: String,
        fileSize: Long,
        fileType: String,
        importDuration: Long,
        importedBy: String = "WEB",
        errorDetails: String? = null
    ): ImportLogEntity {
        val currentTime = dateFormat.format(Date())
        return ImportLogEntity(
            id = "import_${System.currentTimeMillis()}",
            fileName = fileName,
            fileSize = fileSize,
            fileType = fileType,
            successCount = success,
            failedCount = failed,
            errorDetails = errorDetails ?: errors.joinToString("\n"),
            importTime = currentTime,
            importDuration = importDuration,
            importedBy = importedBy
        )
    }

    /**
     * ImportLogEntity -> ImportSummary
     */
    fun ImportLogEntity.toImportSummary(): ImportSummary {
        return ImportSummary(
            total = successCount + failedCount,
            success = successCount,
            failed = failedCount,
            errors = if (errorDetails.isNullOrEmpty()) {
                emptyList()
            } else {
                errorDetails.split("\n").filter { it.isNotBlank() }
            }
        )
    }

    // =================================
    // 统计数据转换
    // =================================

    /**
     * 转换分类统计
     */
    fun List<CategoryCount>.toCategoryCountsMap(): Map<String, Int> {
        return associate { it.category to it.count }
    }

    /**
     * 转换客户统计
     */
    fun List<CustomerCount>.toCustomerCountsMap(): Map<String, Int> {
        return associate { it.customer to it.count }
    }

    // =================================
    // 批量转换辅助方法
    // =================================

    /**
     * 批量转换 RecipeWithMaterials -> Recipe
     */
    fun List<RecipeWithMaterials>.toDomainModels(): List<Recipe> {
        return map { it.toDomainModel() }
    }

    /**
     * 批量转换 Recipe -> RecipeEntity
     */
    fun List<Recipe>.toEntities(): List<RecipeEntity> {
        return map { it.toEntity() }
    }

    /**
     * Recipe -> RecipeWithMaterials (用于批量插入)
     */
    fun Recipe.toRecipeWithMaterials(): RecipeWithMaterials {
        return RecipeWithMaterials(
            recipe = toEntity(),
            materials = materials.map { it.toEntity(id) },
            tags = tags
        )
    }

    // =================================
    // 辅助方法
    // =================================

    /**
     * 生成配方编码
     */
    private fun generateRecipeCode(category: String, totalWeight: Double): String {
        val prefix = when (category) {
            "香精" -> "XJ"
            "酸类" -> "SL"
            "甜味剂" -> "TWJ"
            "色素" -> "SS"
            "防腐剂" -> "FFS"
            "增稠剂" -> "ZCJ"
            else -> "QT"
        }

        val dateFormat = SimpleDateFormat("yyMMdd", Locale.getDefault())
        val dateStr = dateFormat.format(Date())

        val weightCode = if (totalWeight > 0) {
            "${totalWeight.toInt().toString().padStart(3, '0')}"
        } else {
            "000"
        }

        val timeCode = (System.currentTimeMillis() % 100).toString().padStart(2, '0')

        return "${prefix}${dateStr}${weightCode}${timeCode}"
    }

    /**
     * 验证数据完整性
     */
    fun validateRecipeEntity(entity: RecipeEntity): List<String> {
        val errors = mutableListOf<String>()

        if (entity.id.isBlank()) errors.add("配方ID不能为空")
        if (entity.code.isBlank()) errors.add("配方编码不能为空")
        if (entity.name.isBlank()) errors.add("配方名称不能为空")
        if (entity.category.isBlank()) errors.add("配方分类不能为空")
        if (entity.totalWeight <= 0) errors.add("总重量必须大于0")

        return errors
    }

    /**
     * 验证材料实体
     */
    fun validateMaterialEntity(entity: MaterialEntity): List<String> {
        val errors = mutableListOf<String>()

        if (entity.id.isBlank()) errors.add("材料ID不能为空")
        if (entity.recipeId.isBlank()) errors.add("配方ID不能为空")
        if (entity.name.isBlank()) errors.add("材料名称不能为空")
        if (entity.weight <= 0) errors.add("材料重量必须大于0")
        if (entity.sequence <= 0) errors.add("投料序号必须大于0")

        return errors
    }
}
