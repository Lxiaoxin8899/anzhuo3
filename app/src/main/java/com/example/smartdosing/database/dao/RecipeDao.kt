package com.example.smartdosing.database.dao

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import com.example.smartdosing.database.entities.*

/**
 * 配方数据访问对象 (DAO)
 * 提供配方的CRUD操作、复杂查询和统计功能
 */
@Dao
interface RecipeDao {

    // =================================
    // 基础查询操作
    // =================================

    @Query("SELECT * FROM recipes ORDER BY create_time DESC")
    fun getAllRecipesFlow(): Flow<List<RecipeEntity>>

    @Query("SELECT * FROM recipes WHERE id = :id")
    suspend fun getRecipeById(id: String): RecipeEntity?

    @Query("SELECT * FROM recipes WHERE code = :code")
    suspend fun getRecipeByCode(code: String): RecipeEntity?

    @Query("SELECT * FROM recipes ORDER BY create_time DESC")
    suspend fun getAllRecipes(): List<RecipeEntity>

    // =================================
    // 分页查询（核心功能）
    // =================================

    @Query("""
        SELECT * FROM recipes
        WHERE (:category IS NULL OR category = :category)
        AND (:customer IS NULL OR customer = :customer)
        AND (:status IS NULL OR status = :status)
        AND (:searchText IS NULL OR
             name LIKE '%' || :searchText || '%' OR
             code LIKE '%' || :searchText || '%' OR
             description LIKE '%' || :searchText || '%')
        ORDER BY
        CASE WHEN :sortBy = 'CREATE_TIME' AND :sortOrder = 'DESC' THEN create_time END DESC,
        CASE WHEN :sortBy = 'CREATE_TIME' AND :sortOrder = 'ASC' THEN create_time END ASC,
        CASE WHEN :sortBy = 'NAME' AND :sortOrder = 'ASC' THEN name END ASC,
        CASE WHEN :sortBy = 'NAME' AND :sortOrder = 'DESC' THEN name END DESC,
        CASE WHEN :sortBy = 'USAGE_COUNT' AND :sortOrder = 'DESC' THEN usage_count END DESC,
        CASE WHEN :sortBy = 'LAST_USED' AND :sortOrder = 'DESC' THEN last_used END DESC
        LIMIT :limit OFFSET :offset
    """)
    suspend fun getRecipesPaged(
        category: String?,
        customer: String?,
        status: String?,
        searchText: String?,
        sortBy: String,
        sortOrder: String,
        limit: Int,
        offset: Int
    ): List<RecipeEntity>

    // =================================
    // 统计查询
    // =================================

    @Query("SELECT COUNT(*) FROM recipes")
    suspend fun getRecipeCount(): Int

    @Query("""
        SELECT COUNT(*) FROM recipes
        WHERE (:category IS NULL OR category = :category)
        AND (:customer IS NULL OR customer = :customer)
        AND (:status IS NULL OR status = :status)
        AND (:searchText IS NULL OR
             name LIKE '%' || :searchText || '%' OR
             code LIKE '%' || :searchText || '%' OR
             description LIKE '%' || :searchText || '%')
    """)
    suspend fun getFilteredRecipeCount(
        category: String?,
        customer: String?,
        status: String?,
        searchText: String?
    ): Int

    @Query("SELECT COUNT(*) FROM recipes WHERE category = :category")
    suspend fun getRecipeCountByCategory(category: String): Int

    @Query("SELECT DISTINCT category FROM recipes ORDER BY category")
    suspend fun getAllCategories(): List<String>

    @Query("SELECT DISTINCT customer FROM recipes WHERE customer != '' ORDER BY customer")
    suspend fun getAllCustomers(): List<String>

    @Query("SELECT DISTINCT status FROM recipes ORDER BY status")
    suspend fun getAllStatuses(): List<String>

    // =================================
    // 增删改操作
    // =================================

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertRecipe(recipe: RecipeEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertRecipes(recipes: List<RecipeEntity>)

    @Update
    suspend fun updateRecipe(recipe: RecipeEntity)

    @Delete
    suspend fun deleteRecipe(recipe: RecipeEntity)

    @Query("DELETE FROM recipes WHERE id = :id")
    suspend fun deleteRecipeById(id: String)

    @Query("DELETE FROM recipes WHERE code = :code")
    suspend fun deleteRecipeByCode(code: String)

    // =================================
    // 业务操作
    // =================================

    @Query("""
        UPDATE recipes
        SET usage_count = usage_count + 1, last_used = :currentTime
        WHERE id = :id
    """)
    suspend fun markRecipeUsed(id: String, currentTime: String)

    @Query("""
        UPDATE recipes
        SET status = :status, update_time = :updateTime
        WHERE id = :id
    """)
    suspend fun updateRecipeStatus(id: String, status: String, updateTime: String)

    // =================================
    // 关系查询
    // =================================

    @Transaction
    @Query("SELECT * FROM recipes WHERE id = :recipeId")
    suspend fun getRecipeWithMaterials(recipeId: String): RecipeWithMaterials?

    @Transaction
    @Query("SELECT * FROM recipes ORDER BY create_time DESC LIMIT :limit")
    suspend fun getRecipesWithMaterials(limit: Int = 50): List<RecipeWithMaterials>

    @Transaction
    @Query("SELECT * FROM recipes ORDER BY create_time DESC")
    fun getAllRecipesWithMaterialsFlow(): Flow<List<RecipeWithMaterials>>

    // =================================
    // 批量操作（支持事务）
    // =================================

    @Transaction
    suspend fun insertRecipeWithMaterials(
        recipe: RecipeEntity,
        materials: List<MaterialEntity>,
        tags: List<String>
    ) {
        insertRecipe(recipe)
        if (materials.isNotEmpty()) {
            insertMaterials(materials)
        }
        if (tags.isNotEmpty()) {
            insertRecipeTags(tags.map { RecipeTagEntity(recipe.id, it) })
        }
    }

    @Insert
    suspend fun insertMaterials(materials: List<MaterialEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRecipeTags(tags: List<RecipeTagEntity>)

    /**
     * 事务更新配方及其关联的材料和标签
     */
    @Transaction
    suspend fun updateRecipeWithMaterials(
        recipe: RecipeEntity,
        materials: List<MaterialEntity>,
        tags: List<String>
    ) {
        updateRecipe(recipe)
        // 先删除旧的材料和标签，再插入新的
        deleteMaterialsByRecipeId(recipe.id)
        deleteRecipeTagsByRecipeId(recipe.id)
        if (materials.isNotEmpty()) {
            insertMaterials(materials)
        }
        if (tags.isNotEmpty()) {
            insertRecipeTags(tags.map { RecipeTagEntity(recipe.id, it) })
        }
    }

    @Query("DELETE FROM materials WHERE recipe_id = :recipeId")
    suspend fun deleteMaterialsByRecipeId(recipeId: String)

    @Transaction
    suspend fun insertRecipesBatch(recipesWithMaterials: List<RecipeWithMaterials>) {
        val recipes = recipesWithMaterials.map { it.recipe }
        val materials = recipesWithMaterials.flatMap { rwm ->
            rwm.materials
        }
        val tags = recipesWithMaterials.flatMap { rwm ->
            rwm.tags.map { RecipeTagEntity(rwm.recipe.id, it) }
        }

        insertRecipes(recipes)
        if (materials.isNotEmpty()) {
            insertMaterials(materials)
        }
        if (tags.isNotEmpty()) {
            insertRecipeTags(tags)
        }
    }

    // =================================
    // 高级查询（用于统计和分析）
    // =================================

    @Query("""
        SELECT * FROM recipes
        WHERE last_used IS NOT NULL
        ORDER BY last_used DESC
        LIMIT :limit
    """)
    suspend fun getRecentlyUsedRecipes(limit: Int = 10): List<RecipeEntity>

    @Query("""
        SELECT * FROM recipes
        ORDER BY usage_count DESC
        LIMIT :limit
    """)
    suspend fun getMostUsedRecipes(limit: Int = 10): List<RecipeEntity>

    @Query("""
        SELECT * FROM recipes
        WHERE create_time >= :sinceDate
        ORDER BY create_time DESC
        LIMIT :limit
    """)
    suspend fun getRecentlyCreatedRecipes(sinceDate: String, limit: Int = 10): List<RecipeEntity>

    @Query("""
        SELECT category, COUNT(*) as count
        FROM recipes
        GROUP BY category
        ORDER BY count DESC
    """)
    suspend fun getCategoryStats(): List<CategoryCount>

    @Query("""
        SELECT customer, COUNT(*) as count
        FROM recipes
        WHERE customer != ''
        GROUP BY customer
        ORDER BY count DESC
        LIMIT :limit
    """)
    suspend fun getCustomerStats(limit: Int = 10): List<CustomerCount>

    // =================================
    // 搜索和筛选
    // =================================

    @Query("""
        SELECT * FROM recipes
        WHERE name LIKE '%' || :query || '%'
        OR code LIKE '%' || :query || '%'
        OR description LIKE '%' || :query || '%'
        ORDER BY
        CASE
            WHEN name LIKE :query || '%' THEN 1
            WHEN code LIKE :query || '%' THEN 2
            WHEN name LIKE '%' || :query || '%' THEN 3
            ELSE 4
        END,
        create_time DESC
        LIMIT :limit
    """)
    suspend fun searchRecipes(query: String, limit: Int = 50): List<RecipeEntity>

    @Query("SELECT tag FROM recipe_tags WHERE recipe_id = :recipeId")
    suspend fun getTagsByRecipeId(recipeId: String): List<String>

    @Query("""
        DELETE FROM recipe_tags WHERE recipe_id = :recipeId
    """)
    suspend fun deleteRecipeTagsByRecipeId(recipeId: String)

    @Query("SELECT DISTINCT tag FROM recipe_tags ORDER BY tag")
    suspend fun getAllTags(): List<String>
}

/**
 * 统计结果数据类
 */
data class CategoryCount(
    val category: String,
    val count: Int
)

data class CustomerCount(
    val customer: String,
    val count: Int
)