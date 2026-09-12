package com.upptagen.app.data.dao

import androidx.room.*
import com.upptagen.app.data.model.ActiveTimerEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ActiveTimerDao {

    @Query("SELECT * FROM active_timer WHERE id = 1 LIMIT 1")
    fun getActiveTimerFlow(): Flow<ActiveTimerEntity?>

    @Query("SELECT * FROM active_timer WHERE id = 1 LIMIT 1")
    suspend fun getActiveTimerSync(): ActiveTimerEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setActiveTimer(timer: ActiveTimerEntity)

    @Query("DELETE FROM active_timer WHERE id = 1")
    suspend fun clearActiveTimer()
}
