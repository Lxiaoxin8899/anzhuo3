package com.example.smartdosing.database.dao

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import com.example.smartdosing.database.entities.TemplateEntity
import com.example.smartdosing.database.entities.TemplateFieldEntity

/**
 * 模板数据访问对象 (DAO)
 * 提供模板和模板字段的CRUD操作
 */
@Dao
interface TemplateDao {

    // =================================
    // 模板基础操作
    // =================================

    @Query("SELECT * FROM templates ORDER BY is_default DESC, name ASC")
    suspend fun getAllTemplates(): List<TemplateEntity>

    @Query("SELECT * FROM templates ORDER BY is_default DESC, name ASC")
    fun getAllTemplatesFlow(): Flow<List<TemplateEntity>>

    @Query("SELECT * FROM templates WHERE id = :id")
    suspend fun getTemplateById(id: String): TemplateEntity?

    @Query("SELECT * FROM templates WHERE name = :name LIMIT 1")
    suspend fun getTemplateByName(name: String): TemplateEntity?

    @Query("SELECT * FROM templates WHERE is_default = 1 ORDER BY name ASC")
    suspend fun getDefaultTemplates(): List<TemplateEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTemplate(template: TemplateEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTemplates(templates: List<TemplateEntity>)

    @Update
    suspend fun updateTemplate(template: TemplateEntity)

    @Delete
    suspend fun deleteTemplate(template: TemplateEntity)

    @Query("DELETE FROM templates WHERE id = :id")
    suspend fun deleteTemplateById(id: String)

    // =================================
    // 模板字段操作
    // =================================

    @Query("SELECT * FROM template_fields WHERE template_id = :templateId ORDER BY field_order ASC")
    suspend fun getTemplateFields(templateId: String): List<TemplateFieldEntity>

    @Query("SELECT * FROM template_fields WHERE template_id = :templateId ORDER BY field_order ASC")
    fun getTemplateFieldsFlow(templateId: String): Flow<List<TemplateFieldEntity>>

    @Query("SELECT * FROM template_fields WHERE id = :id")
    suspend fun getTemplateFieldById(id: String): TemplateFieldEntity?

    @Query("""
        SELECT * FROM template_fields
        WHERE template_id = :templateId AND field_key = :fieldKey
        LIMIT 1
    """)
    suspend fun getTemplateFieldByKey(templateId: String, fieldKey: String): TemplateFieldEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTemplateField(field: TemplateFieldEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTemplateFields(fields: List<TemplateFieldEntity>)

    @Update
    suspend fun updateTemplateField(field: TemplateFieldEntity)

    @Delete
    suspend fun deleteTemplateField(field: TemplateFieldEntity)

    @Query("DELETE FROM template_fields WHERE id = :id")
    suspend fun deleteTemplateFieldById(id: String)

    @Query("DELETE FROM template_fields WHERE template_id = :templateId")
    suspend fun deleteTemplateFieldsByTemplateId(templateId: String)

    // =================================
    // 复合操作（模板 + 字段）
    // =================================

    @Transaction
    suspend fun insertTemplateWithFields(
        template: TemplateEntity,
        fields: List<TemplateFieldEntity>
    ) {
        insertTemplate(template)
        if (fields.isNotEmpty()) {
            insertTemplateFields(fields)
        }
    }

    @Transaction
    suspend fun updateTemplateWithFields(
        template: TemplateEntity,
        fields: List<TemplateFieldEntity>
    ) {
        updateTemplate(template)
        deleteTemplateFieldsByTemplateId(template.id)
        if (fields.isNotEmpty()) {
            insertTemplateFields(fields)
        }
    }

    @Transaction
    suspend fun deleteTemplateWithFields(templateId: String) {
        deleteTemplateFieldsByTemplateId(templateId)
        deleteTemplateById(templateId)
    }

    // =================================
    // 字段顺序管理
    // =================================

    @Query("""
        SELECT MAX(field_order) FROM template_fields
        WHERE template_id = :templateId
    """)
    suspend fun getMaxFieldOrder(templateId: String): Int?

    @Query("""
        UPDATE template_fields
        SET field_order = field_order + 1
        WHERE template_id = :templateId AND field_order >= :fromOrder
    """)
    suspend fun incrementFieldOrderFrom(templateId: String, fromOrder: Int)

    @Query("""
        UPDATE template_fields
        SET field_order = field_order - 1
        WHERE template_id = :templateId AND field_order > :deletedOrder
    """)
    suspend fun decrementFieldOrderAfter(templateId: String, deletedOrder: Int)

    @Query("""
        SELECT COUNT(*) FROM template_fields
        WHERE template_id = :templateId AND field_order = :order
    """)
    suspend fun countFieldsWithOrder(templateId: String, order: Int): Int

    // =================================
    // 模板统计和查询
    // =================================

    @Query("SELECT COUNT(*) FROM templates")
    suspend fun getTemplateCount(): Int

    @Query("SELECT COUNT(*) FROM templates WHERE is_default = 1")
    suspend fun getDefaultTemplateCount(): Int

    @Query("SELECT COUNT(*) FROM template_fields WHERE template_id = :templateId")
    suspend fun getFieldCountForTemplate(templateId: String): Int

    @Query("""
        SELECT t.*, COUNT(tf.id) as field_count
        FROM templates t
        LEFT JOIN template_fields tf ON t.id = tf.template_id
        GROUP BY t.id
        ORDER BY t.is_default DESC, t.name ASC
    """)
    suspend fun getTemplatesWithFieldCount(): List<TemplateWithFieldCount>

    @Query("""
        SELECT * FROM templates
        WHERE name LIKE '%' || :query || '%'
        OR description LIKE '%' || :query || '%'
        ORDER BY is_default DESC, name ASC
        LIMIT :limit
    """)
    suspend fun searchTemplates(query: String, limit: Int = 20): List<TemplateEntity>

    // =================================
    // 模板验证
    // =================================

    @Query("""
        SELECT EXISTS(
            SELECT 1 FROM templates
            WHERE name = :name AND id != :excludeId
        )
    """)
    suspend fun isTemplateNameDuplicate(name: String, excludeId: String): Boolean

    @Query("""
        SELECT EXISTS(
            SELECT 1 FROM template_fields
            WHERE template_id = :templateId AND field_key = :fieldKey AND id != :excludeId
        )
    """)
    suspend fun isFieldKeyDuplicate(templateId: String, fieldKey: String, excludeId: String): Boolean

    @Query("""
        SELECT COUNT(*) FROM template_fields
        WHERE template_id = :templateId AND required = 1
    """)
    suspend fun getRequiredFieldCount(templateId: String): Int

    // =================================
    // 模板备份和恢复
    // =================================

    @Query("SELECT * FROM templates WHERE is_default = 0")
    suspend fun getUserTemplates(): List<TemplateEntity>

    @Query("""
        SELECT tf.* FROM template_fields tf
        INNER JOIN templates t ON tf.template_id = t.id
        WHERE t.is_default = 0
        ORDER BY tf.template_id, tf.field_order
    """)
    suspend fun getUserTemplateFields(): List<TemplateFieldEntity>

    @Transaction
    suspend fun resetToDefaults(defaultTemplates: List<TemplateEntity>, defaultFields: List<TemplateFieldEntity>) {
        // 删除所有用户模板
        deleteUserTemplates()
        deleteUserTemplateFields()

        // 重置默认模板
        insertTemplates(defaultTemplates)
        insertTemplateFields(defaultFields)
    }

    @Query("DELETE FROM templates WHERE is_default = 0")
    suspend fun deleteUserTemplates()

    @Query("""
        DELETE FROM template_fields
        WHERE template_id IN (SELECT id FROM templates WHERE is_default = 0)
    """)
    suspend fun deleteUserTemplateFields()
}

/**
 * 模板与字段数量统计数据类
 */
data class TemplateWithFieldCount(
    @Embedded val template: TemplateEntity,
    val field_count: Int
)

/**
 * 模板完整信息（包含字段）
 */
data class TemplateWithFields(
    @Embedded val template: TemplateEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "template_id"
    )
    val fields: List<TemplateFieldEntity>
)