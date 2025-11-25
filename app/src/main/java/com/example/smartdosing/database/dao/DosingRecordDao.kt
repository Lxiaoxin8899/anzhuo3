package com.example.smartdosing.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.example.smartdosing.database.entities.DosingRecordDetailEntity
import com.example.smartdosing.database.entities.DosingRecordEntity
import com.example.smartdosing.database.entities.DosingRecordWithDetails
import kotlinx.coroutines.flow.Flow

/**
 * 投料记录 DAO，负责记录的增删查
 */
@Dao
interface DosingRecordDao {

    @Transaction
    @Query("SELECT * FROM dosing_records ORDER BY start_time DESC LIMIT :limit")
    suspend fun getRecentRecords(limit: Int = 50): List<DosingRecordWithDetails>

    @Transaction
    @Query("SELECT * FROM dosing_records WHERE id = :recordId")
    suspend fun getRecordById(recordId: String): DosingRecordWithDetails?

    @Transaction
    @Query("SELECT * FROM dosing_records ORDER BY start_time DESC")
    fun observeRecords(): Flow<List<DosingRecordWithDetails>>

    @Insert
    suspend fun insertRecord(record: DosingRecordEntity)

    @Insert
    suspend fun insertDetails(details: List<DosingRecordDetailEntity>)

    @Query("DELETE FROM dosing_records")
    suspend fun clearAll()

    @Transaction
    suspend fun insertRecordWithDetails(
        record: DosingRecordEntity,
        details: List<DosingRecordDetailEntity>
    ) {
        insertRecord(record)
        if (details.isNotEmpty()) {
            insertDetails(details)
        }
    }
}
