package com.example.smartdosing.database.dao

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import com.example.smartdosing.database.entities.MaterialEntity

/**
 * 材料数据访问对象 (DAO)
 * 提供材料的CRUD操作和查询功能
 */
@Dao
interface MaterialDao {

    // =================================
    // 基础查询操作
    // =================================

    @Query("SELECT * FROM materials WHERE id = :id")
    suspend fun getMaterialById(id: String): MaterialEntity?

    @Query("SELECT * FROM materials WHERE recipe_id = :recipeId ORDER BY sequence ASC")
    suspend fun getMaterialsByRecipeId(recipeId: String): List<MaterialEntity>

    @Query("SELECT * FROM materials WHERE recipe_id = :recipeId ORDER BY sequence ASC")
    fun getMaterialsByRecipeIdFlow(recipeId: String): Flow<List<MaterialEntity>>

    @Query("SELECT * FROM materials WHERE recipe_id IN (:recipeIds) ORDER BY recipe_id, sequence ASC")
    suspend fun getMaterialsByRecipeIds(recipeIds: List<String>): List<MaterialEntity>

    // =================================
    // 增删改操作
    // =================================

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMaterial(material: MaterialEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMaterials(materials: List<MaterialEntity>)

    @Update
    suspend fun updateMaterial(material: MaterialEntity)

    @Update
    suspend fun updateMaterials(materials: List<MaterialEntity>)

    @Delete
    suspend fun deleteMaterial(material: MaterialEntity)

    @Query("DELETE FROM materials WHERE id = :id")
    suspend fun deleteMaterialById(id: String)

    @Query("DELETE FROM materials WHERE recipe_id = :recipeId")
    suspend fun deleteMaterialsByRecipeId(recipeId: String)

    @Query("DELETE FROM materials WHERE recipe_id IN (:recipeIds)")
    suspend fun deleteMaterialsByRecipeIds(recipeIds: List<String>)

    // =================================
    // 批量操作
    // =================================

    @Transaction
    suspend fun replaceMaterialsForRecipe(recipeId: String, materials: List<MaterialEntity>) {
        deleteMaterialsByRecipeId(recipeId)
        if (materials.isNotEmpty()) {
            insertMaterials(materials)
        }
    }

    @Transaction
    suspend fun updateRecipeMaterials(
        recipeId: String,
        toDelete: List<String>,
        toInsert: List<MaterialEntity>,
        toUpdate: List<MaterialEntity>
    ) {
        if (toDelete.isNotEmpty()) {
            deleteMultipleMaterials(toDelete)
        }
        if (toUpdate.isNotEmpty()) {
            updateMaterials(toUpdate)
        }
        if (toInsert.isNotEmpty()) {
            insertMaterials(toInsert)
        }
    }

    @Query("DELETE FROM materials WHERE id IN (:materialIds)")
    suspend fun deleteMultipleMaterials(materialIds: List<String>)

    // =================================
    // 统计和查询
    // =================================

    @Query("SELECT COUNT(*) FROM materials WHERE recipe_id = :recipeId")
    suspend fun getMaterialCountByRecipeId(recipeId: String): Int

    @Query("""
        SELECT SUM(weight) FROM materials
        WHERE recipe_id = :recipeId
    """)
    suspend fun getTotalWeightByRecipeId(recipeId: String): Double?

    @Query("""
        SELECT DISTINCT name FROM materials
        WHERE name LIKE '%' || :query || '%'
        ORDER BY name
        LIMIT :limit
    """)
    suspend fun searchMaterialNames(query: String, limit: Int = 20): List<String>

    @Query("""
        SELECT DISTINCT unit FROM materials
        ORDER BY unit
    """)
    suspend fun getAllUnits(): List<String>

    @Query("""
        SELECT name, COUNT(*) as usage_count
        FROM materials
        GROUP BY name
        ORDER BY usage_count DESC
        LIMIT :limit
    """)
    suspend fun getMostUsedMaterials(limit: Int = 20): List<MaterialUsage>

    @Query("""
        SELECT * FROM materials
        WHERE name = :materialName
        ORDER BY recipe_id, sequence
    """)
    suspend fun getMaterialsByName(materialName: String): List<MaterialEntity>

    // =================================
    // 序号管理
    // =================================

    @Query("""
        SELECT MAX(sequence) FROM materials
        WHERE recipe_id = :recipeId
    """)
    suspend fun getMaxSequenceForRecipe(recipeId: String): Int?

    @Query("""
        UPDATE materials
        SET sequence = sequence + 1
        WHERE recipe_id = :recipeId AND sequence >= :fromSequence
    """)
    suspend fun incrementSequenceFrom(recipeId: String, fromSequence: Int)

    @Query("""
        UPDATE materials
        SET sequence = sequence - 1
        WHERE recipe_id = :recipeId AND sequence > :deletedSequence
    """)
    suspend fun decrementSequenceAfter(recipeId: String, deletedSequence: Int)

    // =================================
    // 验证和约束检查
    // =================================

    @Query("""
        SELECT COUNT(*) FROM materials
        WHERE recipe_id = :recipeId AND sequence = :sequence
    """)
    suspend fun countMaterialsWithSequence(recipeId: String, sequence: Int): Int

    @Query("""
        SELECT EXISTS(
            SELECT 1 FROM materials
            WHERE recipe_id = :recipeId AND sequence = :sequence AND id != :excludeId
        )
    """)
    suspend fun isSequenceDuplicate(recipeId: String, sequence: Int, excludeId: String): Boolean

    @Query("""
        SELECT * FROM materials
        WHERE recipe_id = :recipeId AND sequence BETWEEN :startSeq AND :endSeq
        ORDER BY sequence
    """)
    suspend fun getMaterialsInSequenceRange(
        recipeId: String,
        startSeq: Int,
        endSeq: Int
    ): List<MaterialEntity>
}

/**
 * 材料使用统计数据类
 */
data class MaterialUsage(
    val name: String,
    val usage_count: Int
)