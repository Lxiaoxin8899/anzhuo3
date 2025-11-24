package com.example.smartdosing.database

import android.content.Context
import androidx.room.*
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

import com.example.smartdosing.database.entities.*
import com.example.smartdosing.database.dao.*
import com.example.smartdosing.database.converters.DatabaseConverters

/**
 * SmartDosing应用的Room数据库主类
 *
 * 版本: 1
 * 包含表: recipes, materials, recipe_tags, templates, template_fields, import_logs
 */
@Database(
    entities = [
        RecipeEntity::class,
        MaterialEntity::class,
        RecipeTagEntity::class,
        TemplateEntity::class,
        TemplateFieldEntity::class,
        ImportLogEntity::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(DatabaseConverters::class)
abstract class SmartDosingDatabase : RoomDatabase() {

    // DAO 接口
    abstract fun recipeDao(): RecipeDao
    abstract fun materialDao(): MaterialDao
    abstract fun templateDao(): TemplateDao
    abstract fun importLogDao(): ImportLogDao

    companion object {
        const val DATABASE_NAME = "smartdosing.db"

        @Volatile
        private var INSTANCE: SmartDosingDatabase? = null

        /**
         * 获取数据库单例实例
         */
        fun getDatabase(context: Context): SmartDosingDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SmartDosingDatabase::class.java,
                    DATABASE_NAME
                )
                .addCallback(DatabaseCallback(context))
                .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING) // 启用WAL模式
                .fallbackToDestructiveMigration() // 开发阶段允许破坏性迁移
                .build()

                INSTANCE = instance
                instance
            }
        }

        /**
         * 清除数据库实例（用于测试）
         */
        fun clearInstance() {
            INSTANCE = null
        }
    }

    /**
     * 数据库回调 - 处理数据库创建和升级
     */
    private class DatabaseCallback(private val context: Context) : Callback() {

        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)

            // 在后台线程初始化数据
            CoroutineScope(Dispatchers.IO).launch {
                INSTANCE?.let { database ->
                    try {
                        populateInitialData(database)
                    } catch (e: Exception) {
                        android.util.Log.e("SmartDosingDB", "初始化数据失败", e)
                    }
                }
            }
        }

        override fun onOpen(db: SupportSQLiteDatabase) {
            super.onOpen(db)

            // 启用外键约束
            db.execSQL("PRAGMA foreign_keys = ON")
        }

        /**
         * 初始化默认数据
         */
        private suspend fun populateInitialData(database: SmartDosingDatabase) {
            val recipeDao = database.recipeDao()
            val templateDao = database.templateDao()

            // 检查是否已有数据
            if (recipeDao.getRecipeCount() > 0) {
                android.util.Log.i("SmartDosingDB", "数据库已有数据，跳过初始化")
                return
            }

            android.util.Log.i("SmartDosingDB", "开始初始化默认数据...")

            // 插入默认模板
            insertDefaultTemplates(templateDao)

            // 插入示例配方 - 已注释，不再自动添加演示数据
            // insertSampleRecipes(database)

            android.util.Log.i("SmartDosingDB", "默认数据初始化完成（不包含演示配方）")
        }

        /**
         * 插入默认模板数据
         */
        private suspend fun insertDefaultTemplates(templateDao: TemplateDao) {
            val now = getCurrentTimeString()

            val standardTemplate = TemplateEntity(
                id = "standard_recipe_template",
                name = "标准配方导入模板",
                description = "用于Excel/CSV批量导入配方的标准模板，可自定义列标题和示例值。",
                version = 1,
                updatedAt = now,
                isDefault = true,
                createdBy = "SYSTEM"
            )

            val standardFields = listOf(
                TemplateFieldEntity(
                    id = "field_recipe_name",
                    templateId = standardTemplate.id,
                    fieldKey = "recipe_name",
                    label = "配方名称",
                    description = "整条记录的配方名称，示例：草莓烟油",
                    required = true,
                    example = "草莓烟油",
                    fieldOrder = 1
                ),
                TemplateFieldEntity(
                    id = "field_recipe_code",
                    templateId = standardTemplate.id,
                    fieldKey = "recipe_code",
                    label = "配方编码",
                    description = "可选字段，配方唯一编号或物料号",
                    required = false,
                    example = "S000001",
                    fieldOrder = 2
                ),
                TemplateFieldEntity(
                    id = "field_designer",
                    templateId = standardTemplate.id,
                    fieldKey = "designer",
                    label = "设计师",
                    description = "负责配方设计/审核的人员",
                    required = false,
                    example = "张工",
                    fieldOrder = 3
                ),
                TemplateFieldEntity(
                    id = "field_batch_no",
                    templateId = standardTemplate.id,
                    fieldKey = "batch_no",
                    label = "配方批次",
                    description = "可选，配方批次或版本信息",
                    required = false,
                    example = "2025-Q1",
                    fieldOrder = 4
                ),
                TemplateFieldEntity(
                    id = "field_recipe_category",
                    templateId = standardTemplate.id,
                    fieldKey = "recipe_category",
                    label = "配方分类",
                    description = "用于统计的分类，例如香精/溶剂/调味剂",
                    required = false,
                    example = "香精",
                    fieldOrder = 5
                ),
                TemplateFieldEntity(
                    id = "field_material_line_1",
                    templateId = standardTemplate.id,
                    fieldKey = "material_line_1",
                    label = "材料1名称:重量:单位:序号",
                    description = "请按\"名称:重量:单位:序号\"格式填写，例如 草莓香精:50:g:1",
                    required = true,
                    example = "草莓香精:50:g:1",
                    fieldOrder = 6
                ),
                TemplateFieldEntity(
                    id = "field_material_line_2",
                    templateId = standardTemplate.id,
                    fieldKey = "material_line_2",
                    label = "材料2名称:重量:单位:序号",
                    description = "示例：草莓香精(溶剂):150:ml:2",
                    required = false,
                    example = "草莓香精(溶剂):150:ml:2",
                    fieldOrder = 7
                ),
                TemplateFieldEntity(
                    id = "field_material_line_3",
                    templateId = standardTemplate.id,
                    fieldKey = "material_line_3",
                    label = "材料3名称:重量:单位:序号",
                    description = "可继续追加更多材料列，按照同样格式填写",
                    required = false,
                    example = "柠檬酸:10:g:3",
                    fieldOrder = 8
                )
            )

            templateDao.insertTemplateWithFields(standardTemplate, standardFields)
            android.util.Log.i("SmartDosingDB", "插入默认模板完成")
        }

        /**
         * 插入示例配方数据
         */
        private suspend fun insertSampleRecipes(database: SmartDosingDatabase) {
            val recipeDao = database.recipeDao()
            val materialDao = database.materialDao()

            val currentTime = getCurrentTimeString()

            // 示例配方1：苹果香精配方
            val recipe1 = RecipeEntity(
                id = "sample_recipe_1",
                code = "XJ241124001",
                name = "苹果香精配方",
                category = "香精",
                subCategory = "水果类",
                customer = "康师傅",
                batchNo = "KSF2024001",
                version = "2.1",
                description = "经典苹果香味配方，适用于饮料和糖果制作",
                totalWeight = 200.0,
                createTime = currentTime,
                updateTime = currentTime,
                lastUsed = null,
                usageCount = 0,
                status = "ACTIVE",
                priority = "HIGH",
                creator = "张工程师",
                reviewer = "李主管"
            )

            val materials1 = listOf(
                MaterialEntity("mat1_1", recipe1.id, "苹果香精", 50.0, "g", 1, "主香料"),
                MaterialEntity("mat1_2", recipe1.id, "乙基麦芽酚", 10.0, "g", 2, "增香剂"),
                MaterialEntity("mat1_3", recipe1.id, "柠檬酸", 5.0, "g", 3, "调酸"),
                MaterialEntity("mat1_4", recipe1.id, "山梨醇", 100.0, "g", 4, "甜味剂"),
                MaterialEntity("mat1_5", recipe1.id, "食用酒精", 35.0, "ml", 5, "溶剂")
            )

            val tags1 = listOf("水果", "饮料", "糖果")

            // 示例配方2：柠檬酸配方
            val recipe2 = RecipeEntity(
                id = "sample_recipe_2",
                code = "SL241124001",
                name = "柠檬酸配方",
                category = "酸类",
                subCategory = "有机酸",
                customer = "统一",
                batchNo = "TY2024002",
                version = "1.5",
                description = "标准柠檬酸调味配方",
                totalWeight = 215.0,
                createTime = currentTime,
                updateTime = currentTime,
                lastUsed = null,
                usageCount = 0,
                status = "ACTIVE",
                priority = "NORMAL",
                creator = "王技师",
                reviewer = "张主管"
            )

            val materials2 = listOf(
                MaterialEntity("mat2_1", recipe2.id, "柠檬酸", 80.0, "g", 1),
                MaterialEntity("mat2_2", recipe2.id, "柠檬香精", 15.0, "g", 2),
                MaterialEntity("mat2_3", recipe2.id, "蔗糖", 120.0, "g", 3)
            )

            val tags2 = listOf("酸味", "调料")

            // 插入配方和相关数据
            recipeDao.insertRecipeWithMaterials(recipe1, materials1, tags1)
            recipeDao.insertRecipeWithMaterials(recipe2, materials2, tags2)

            android.util.Log.i("SmartDosingDB", "插入示例配方完成")
        }

        /**
         * 获取当前时间字符串
         */
        private fun getCurrentTimeString(): String {
            val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            return format.format(Date())
        }
    }
}