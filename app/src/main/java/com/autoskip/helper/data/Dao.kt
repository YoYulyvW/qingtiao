package com.autoskip.helper.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface RuleDao {
    @Query("SELECT * FROM rules ORDER BY enabled DESC, createdAt DESC")
    fun observeAll(): Flow<List<RuleEntity>>

    @Query("SELECT * FROM rules WHERE enabled = 1")
    suspend fun enabledRules(): List<RuleEntity>

    @Query("SELECT * FROM rules ORDER BY createdAt DESC")
    suspend fun all(): List<RuleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(rule: RuleEntity): Long

    @Update
    suspend fun update(rule: RuleEntity)

    @Delete
    suspend fun delete(rule: RuleEntity)

    @Query("UPDATE rules SET hitCount = hitCount + 1 WHERE id = :id")
    suspend fun bumpHit(id: Long)

    @Query("DELETE FROM rules WHERE learned = 1 AND enabled = 0")
    suspend fun clearDisabledLearned()
}

@Dao
interface CondRuleDao {
    @Query("SELECT * FROM cond_rules ORDER BY enabled DESC, createdAt DESC")
    fun observeAll(): Flow<List<CondRuleEntity>>

    @Query("SELECT * FROM cond_rules WHERE enabled = 1")
    suspend fun enabledRules(): List<CondRuleEntity>

    @Query("SELECT * FROM cond_rules")
    suspend fun all(): List<CondRuleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(rule: CondRuleEntity): Long

    @Update
    suspend fun update(rule: CondRuleEntity)

    @Delete
    suspend fun delete(rule: CondRuleEntity)

    @Query("UPDATE cond_rules SET hitCount = hitCount + 1 WHERE id = :id")
    suspend fun bumpHit(id: Long)
}

@Dao
interface LogDao {
    @Query("SELECT * FROM logs ORDER BY timestamp DESC LIMIT 500")
    fun observeRecent(): Flow<List<LogEntity>>

    @Insert
    suspend fun insert(log: LogEntity)

    @Query("SELECT COUNT(*) FROM logs")
    fun observeTotal(): Flow<Int>

    @Query("SELECT COUNT(*) FROM logs WHERE timestamp >= :since")
    fun observeSince(since: Long): Flow<Int>

    @Query("DELETE FROM logs")
    suspend fun clearAll()
}
