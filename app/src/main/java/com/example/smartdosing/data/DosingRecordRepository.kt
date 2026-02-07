package com.example.smartdosing.data

import android.content.Context
import com.example.smartdosing.database.SmartDosingDatabase
import com.example.smartdosing.database.DataMapper.toDomainRecords
import com.example.smartdosing.database.entities.DosingRecordDetailEntity
import com.example.smartdosing.database.entities.DosingRecordEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.math.abs
import java.util.UUID

/**
 * 投料记录存储仓库
 */
class DosingRecordRepository private constructor(context: Context) {

    private val database = SmartDosingDatabase.getDatabase(context)
    private val recordDao = database.dosingRecordDao()
    suspend fun saveRecord(request: DosingRecordSaveRequest) {
        val recordId = "dosing_record_${UUID.randomUUID()}"
        val totalActualWeight = request.details.sumOf { it.actualWeight }
        val overLimitCount = request.details.count { it.isOverLimit }
        val avgDeviationPercent = if (request.details.isEmpty()) {
            0.0
        } else {
            request.details.sumOf { detail ->
                val expected = detail.targetWeight
                val actual = detail.actualWeight
                if (expected == 0.0) 0.0 else abs((actual - expected) / expected) * 100.0
            } / request.details.size
        }

        val recordEntity = DosingRecordEntity(
            id = recordId,
            recipeId = request.recipeId,
            recipeCode = request.recipeCode,
            recipeName = request.recipeName,
            operatorName = request.operatorName,
            checklistItems = request.checklistItems,
            startTime = request.startTime,
            endTime = request.endTime,
            totalMaterials = request.totalMaterials,
            completedMaterials = request.details.size,
            totalActualWeight = totalActualWeight,
            tolerancePercent = request.tolerancePercent,
            overLimitCount = overLimitCount,
            avgDeviationPercent = avgDeviationPercent,
            status = DosingRecordStatus.COMPLETED.name,
            createdAt = request.endTime
        )

        val detailEntities = request.details.mapIndexed { index, detail ->
            DosingRecordDetailEntity(
                id = "dosing_detail_${recordId}_${index + 1}",
                recordId = recordId,
                sequence = detail.sequence,
                materialCode = detail.materialCode,
                materialName = detail.materialName,
                targetWeight = detail.targetWeight,
                actualWeight = detail.actualWeight,
                unit = detail.unit,
                isOverLimit = detail.isOverLimit,
                overLimitPercent = detail.overLimitPercent
            )
        }

        recordDao.insertRecordWithDetails(recordEntity, detailEntities)
    }

    suspend fun getRecentRecords(limit: Int = 50): List<DosingRecord> {
        return recordDao.getRecentRecords(limit).toDomainRecords()
    }

    fun observeRecords(): Flow<List<DosingRecord>> {
        return recordDao.observeRecords().map { it.toDomainRecords() }
    }

    companion object {
        @Volatile
        private var INSTANCE: DosingRecordRepository? = null

        fun getInstance(context: Context): DosingRecordRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: DosingRecordRepository(context.applicationContext).also { INSTANCE = it }
            }
        }

        fun clearInstance() {
            INSTANCE = null
        }
    }
}

/**
 * 投料记录保存请求
 */
data class DosingRecordSaveRequest(
    val recipeId: String?,
    val recipeCode: String?,
    val recipeName: String,
    val operatorName: String,
    val checklistItems: List<String>,
    val startTime: String,
    val endTime: String,
    val totalMaterials: Int,
    val tolerancePercent: Float,
    val details: List<DosingRecordDetailInput>
)

/**
 * 投料记录明细输入
 */
data class DosingRecordDetailInput(
    val sequence: Int,
    val materialCode: String,
    val materialName: String,
    val targetWeight: Double,
    val actualWeight: Double,
    val unit: String,
    val isOverLimit: Boolean,
    val overLimitPercent: Double
)
