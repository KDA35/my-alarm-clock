package com.myalarm.clock.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AlarmGroupDao {
    @Query("SELECT * FROM alarm_groups ORDER BY createdAt")
    fun observeAll(): Flow<List<AlarmGroup>>

    @Query("SELECT * FROM alarm_groups WHERE id = :id")
    suspend fun getById(id: Long): AlarmGroup?

    @Query("SELECT * FROM alarm_groups WHERE name = :name LIMIT 1")
    suspend fun getByName(name: String): AlarmGroup?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(group: AlarmGroup): Long

    @Update
    suspend fun update(group: AlarmGroup)

    @Delete
    suspend fun delete(group: AlarmGroup)

    @Query("UPDATE alarm_groups SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)
}
